package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketIO.LegacyPacket;
import io.detergentmc.legacy.LegacyWire.Writer;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.List;

/**
 * The direct mode's backend face: logs into an internal legacy
 * vanilla server as a legacy client (handshake, RSA + AES/CFB8
 * encryption — required even in offline mode — and the login packet),
 * mirroring exactly what a real legacy client sends. The plaintext
 * generations (protocols 29, 28, 23, 22 — 1.2.5 down to 1.0.0) are
 * the exception: they predate the encryption boundary, so their
 * login is the plaintext handshake-string / hash-string / login
 * exchange a real client of the generation performs, with the
 * login packet laid out per generation (measured against the real
 * servers, docs/PROXY-1.2.5.md, docs/PROXY-1.2.3.md,
 * docs/PROXY-1.1.md, docs/PROXY-1.0.md).
 */
public final class LegacyBackendLogin {

    /** Result of a successful backend login. */
    public record Result(LegacyConnection connection, LegacyPacket loginResponse) {
    }

    private LegacyBackendLogin() {
    }

    public static Result connectAndLogin(String host, int port, String username)
            throws IOException {
        return connectAndLogin(host, port, username, LegacyProtocol.V1_6_4);
    }

    /** As {@link #connectAndLogin(String, int, String)} for a specific legacy protocol. */
    public static Result connectAndLogin(String host, int port, String username,
                                         int legacyProtocol)
            throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 10_000);
        socket.setSoTimeout(15_000);
        LegacyConnection connection = new LegacyConnection(socket, legacyProtocol);
        try {
            if (LegacyProtocol.isPlaintextGeneration(legacyProtocol)) {
                // The plaintext generations (29, 28, 23, 22):
                // the handshake is the username as a single
                // string; the server answers with its
                // connection-hash string; the login packet
                // carries the protocol number (checked by the
                // server), the username and the generation's
                // trailing fields. A real client's trailing
                // values are the world settings it asks for; the
                // servers ignore them beyond the protocol and the
                // username (the probing logins of the Stage A
                // measurements succeeded against all four real
                // servers with exactly these bytes).
                connection.sendRaw(new Writer(0x02)
                        .string(username)
                        .bytes());
                requirePacket(connection, 0x02);
                Writer login = new Writer(0x01)
                        .i32(legacyProtocol)
                        .string(username);
                if (legacyProtocol == LegacyProtocol.V1_1) {
                    // 1.1: seed, level type, gamemode, then
                    // dimension / difficulty / height / players
                    // as four bytes.
                    login.i64(0).string("default").i32(0)
                            .u8(0).u8(0).u8(0).u8(0);
                } else if (legacyProtocol == LegacyProtocol.V1_0) {
                    // 1.0.0: seed, gamemode, then the four bytes
                    // (no level type).
                    login.i64(0).i32(0)
                            .u8(0).u8(0).u8(0).u8(0);
                } else {
                    // 29 / 28: level type, gamemode i32,
                    // dimension i32, difficulty / height /
                    // players as three bytes.
                    login.string("default").i32(0).i32(0)
                            .u8(1).u8(0).u8(20);
                }
                connection.sendRaw(login.bytes());
                LegacyPacket loginResponse = requirePacket(connection, 0x01);
                socket.setSoTimeout(60_000);
                return new Result(connection, loginResponse);
            }

            // 0x02 handshake: protocol, username, host, port.
            connection.sendRaw(new Writer(0x02)
                    .u8(legacyProtocol)
                    .string(username)
                    .string(host)
                    .i32(port)
                    .bytes());

            // 0xFD encryption request: serverId, public key, verify token.
            LegacyPacket request = requirePacket(connection, 0xFD);
            byte[] keyBytes = (byte[]) request.field(1);
            byte[] token = (byte[]) request.field(2);
            PublicKey serverKey;
            byte[] secret = LegacyCrypto.randomBytes(16);
            byte[] encryptedSecret;
            byte[] encryptedToken;
            try {
                serverKey = LegacyCrypto.parsePublicKey(keyBytes);
                encryptedSecret = LegacyCrypto.rsaEncrypt(serverKey, secret);
                encryptedToken = LegacyCrypto.rsaEncrypt(serverKey, token);
            } catch (GeneralSecurityException e) {
                throw new IOException("backend encryption setup failed", e);
            }

            // 0xFC encryption response (still plaintext). The server's
            // own 0xFC also arrives in plaintext: encryption starts
            // only after both 0xFC packets have been exchanged.
            // (Measured against the real 1.6.4 server in Stage E: the
            // earlier order — enabling the ciphers before reading the
            // server's 0xFC — decrypted that packet into garbage and
            // no real login ever completed.)
            connection.sendRaw(new Writer(0xFC)
                    .bytes16(encryptedSecret)
                    .bytes16(encryptedToken)
                    .bytes());
            requirePacket(connection, 0xFC);
            connection.enableEncryption(secret);

            // 0x01 login: protocol, level type, five zero bytes (the
            // shared packet layout's server-side fields, unused by the
            // server in this direction).
            connection.sendRaw(new Writer(0x01)
                    .i32(legacyProtocol)
                    .string("default")
                    .u8(0).u8(0).u8(0).u8(0).u8(0)
                    .bytes());

            // 0xCD client status, payload 0 (initial spawn): the
            // server completes the login only when this arrives — a
            // real client sends it unprompted right after the login
            // packet. Measured against the real 1.6.4 server in
            // Stage E: without it, the server waits for the status
            // and this side waits for the login response, forever.
            connection.sendRaw(new Writer(0xCD).u8(0).bytes());

            LegacyPacket loginResponse = requirePacket(connection, 0x01);
            socket.setSoTimeout(60_000);
            return new Result(connection, loginResponse);
        } catch (IOException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    private static LegacyPacket requirePacket(LegacyConnection connection, int expectedId)
            throws IOException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            LegacyPacket packet = connection.readPacket();
            if (packet == null) {
                continue;
            }
            if (packet.id() == 0xFF) {
                throw new IOException("backend disconnected during login: "
                        + packet.field(0));
            }
            if (packet.id() != expectedId) {
                throw new IOException("expected packet 0x"
                        + Integer.toHexString(expectedId) + " from backend, got 0x"
                        + Integer.toHexString(packet.id()));
            }
            return packet;
        }
        throw new EOFException("timed out waiting for backend packet 0x"
                + Integer.toHexString(expectedId));
    }

    /** Convenience for building the shared-secret fields list. */
    static List<Object> fields(Object... values) {
        return List.of(values);
    }
}
