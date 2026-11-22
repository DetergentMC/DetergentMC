package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketIO.LegacyPacket;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * One legacy-wire connection (either face): a socket plus the
 * table-driven stream parser, with optional AES/CFB8 ciphers that are
 * enabled after the login encryption exchange.
 */
public final class LegacyConnection implements Closeable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final LegacyPacketIO.StreamParser parser;
    private LegacyCrypto.StreamCipher encryptor;
    private LegacyCrypto.StreamCipher decryptor;
    private final byte[] readBuffer = new byte[65536];

    public LegacyConnection(Socket socket) throws IOException {
        this(socket, LegacyProtocol.V1_6_4);
    }

    public LegacyConnection(Socket socket, int legacyProtocol) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.parser = new LegacyPacketIO.StreamParser(legacyProtocol);
    }

    public Socket socket() {
        return socket;
    }

    /** The legacy protocol this connection parses and writes with. */
    public int legacyProtocol() {
        return parser.legacyProtocol();
    }

    /** Adopts a legacy protocol (after the handshake reveals it). */
    public void setLegacyProtocol(int legacyProtocol) {
        parser.setLegacyProtocol(legacyProtocol);
    }

    /** Enables stream encryption (called once, after the 0xFC exchange). */
    public synchronized void enableEncryption(byte[] sharedSecret) {
        this.encryptor = LegacyCrypto.encryptor(sharedSecret);
        this.decryptor = LegacyCrypto.decryptor(sharedSecret);
    }

    public boolean encrypted() {
        return encryptor != null;
    }

    /** Writes raw (already packet-shaped) bytes, encrypting if enabled. */
    public synchronized void sendRaw(byte[] plain) throws IOException {
        byte[] data = encryptor != null ? encryptor.process(plain) : plain;
        out.write(data);
        out.flush();
    }

    public void sendPacket(LegacyPacket packet) throws IOException {
        sendRaw(LegacyPacketIO.write(legacyProtocol(), packet));
    }

    /**
     * Reads the next complete packet.
     *
     * @return the packet, or null on socket timeout (caller retries)
     * @throws EOFException when the peer closed the connection
     */
    public LegacyPacket readPacket() throws IOException {
        while (true) {
            LegacyPacket packet = parser.next();
            if (packet != null) {
                return packet;
            }
            int n;
            try {
                n = in.read(readBuffer);
            } catch (SocketTimeoutException e) {
                return null;
            }
            if (n < 0) {
                throw new EOFException("legacy connection closed");
            }
            byte[] plain = decryptor != null
                    ? decryptor.process(readBuffer, 0, n)
                    : java.util.Arrays.copyOf(readBuffer, n);
            parser.feed(plain, 0, plain.length);
        }
    }

    /**
     * Reads raw bytes (used only for the very first byte(s) of a
     * connection, before the packet parser can take over: ping vs
     * handshake discrimination).
     */
    public int readRawByte() throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("legacy connection closed");
        }
        return decryptor != null ? (decryptor.process(new byte[]{(byte) b})[0] & 0xFF) : b;
    }

    /** Feeds bytes obtained via {@link #readRawByte()} into the parser. */
    public void feedRaw(byte[] data) {
        parser.feed(data, 0, data.length);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
