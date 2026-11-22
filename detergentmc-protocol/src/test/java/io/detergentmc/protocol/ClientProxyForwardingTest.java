package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Surfactant転送の受け口（バッチ4）の ClientProxy レベルテスト。
 * 偽バニラ765＋生ソケットの偽クライアント765で、
 * {@code forwarding.secret} を設定したプロキシが
 * ログインの途中で転送確認（Login Plugin Request）を送り、
 * 署名の検証が通れば転送された素性を採用し、
 * 通らなければ切断することを確かめる。
 *
 * <p>転送ペイロードはテスト側で {@link Mac} を使って独立に組み立てる
 * （Surfactant側の ForwardingSigner と同じ v1 仕様）。
 */
class ClientProxyForwardingTest {

    private static final String SECRET = "shared-secret";
    private static final UUID REAL_UUID =
            UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    /** 内部バニラが割り当てる想定の offline UUID（名前由来）。 */
    private static final UUID VANILLA_UUID =
            UUID.fromString("22222222-3333-4444-5555-666666666666");

    /** テスト側で独立に署名して転送ワイヤを作る（v1形式）。 */
    private static byte[] forwardingWire(String secret, String json) throws Exception {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(jsonBytes);
        byte[] wire = new byte[1 + signature.length + jsonBytes.length];
        wire[0] = 1;
        System.arraycopy(signature, 0, wire, 1, signature.length);
        System.arraycopy(jsonBytes, 0, wire, 1 + signature.length, jsonBytes.length);
        return wire;
    }

    private static String forwardingJson(String name, UUID uuid, String address,
            boolean withProperties) {
        String json = "{\"address\":\"" + address + "\",\"uuid\":\"" + uuid
                + "\",\"name\":\"" + name + "\"";
        if (withProperties) {
            json += ",\"properties\":[{\"name\":\"textures\","
                    + "\"value\":\"fwd-texture-value\",\"signature\":\"fwd-sig\"}]";
        }
        return json + "}";
    }

    private ClientProxy forwardingProxy(int port, int vanillaPort) {
        ClientProxy proxy = new ClientProxy(port, "127.0.0.1", vanillaPort, false,
                null, null, ProxyAuthConfig.defaults());
        proxy.setForwardingSecret(SECRET);
        return proxy;
    }

    /** ハンドシェイク＋Login Start を送り、転送確認を受け取って取引IDを返す。 */
    private int beginForwardingLogin(FakeClient client, FakeVanilla vanilla,
            String name) throws Exception {
        client.sendPacket(new MinecraftPacket(0x00, handshakePayload(765, 2)));
        vanilla.accept();
        client.sendPacket(new MinecraftPacket(0x00, loginStart765(name)));
        MinecraftPacket request = client.readPacket();
        assertEquals(0x04, request.id(), "Login Plugin Request（0x04）が来るはず");
        Reader reader = new Reader(request.payload());
        int transactionId = reader.readVarInt();
        assertEquals(ForwardingData.CHANNEL, reader.readString(),
                "チャンネルは surfactant:forwarding のはず");
        return transactionId;
    }

    private void respondForwarding(FakeClient client, int transactionId,
            boolean successful, byte[] wire) throws IOException {
        Writer writer = new Writer();
        writer.writeVarInt(transactionId);
        writer.writeBoolean(successful);
        if (wire != null) {
            writer.writeBytes(wire);
        }
        client.sendPacket(new MinecraftPacket(0x02, writer.bytes()));
    }

    @Test
    void validForwardingAdoptsIdentity() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = forwardingProxy(proxyPort, vanilla.port());
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                int transactionId = beginForwardingLogin(client, vanilla, "FwdSteve");
                // 検証が終わるまで、バニラには何も届いていないこと。
                assertNull(vanilla.tryReadPacket(700),
                        "転送の検証前にバニラへパケットが流れてはいけない");
                respondForwarding(client, transactionId, true,
                        forwardingWire(SECRET, forwardingJson("FwdSteve", REAL_UUID,
                                "203.0.113.7:51234", true)));
                // バニラにはハンドシェイクと、本物のUUID入りの Login Start。
                MinecraftPacket handshake = vanilla.readPacket();
                assertEquals(0x00, handshake.id());
                assertEquals(765, new Reader(handshake.payload()).readVarInt());
                MinecraftPacket loginStart = vanilla.readPacket();
                assertEquals(0x00, loginStart.id());
                Reader loginReader = new Reader(loginStart.payload());
                assertEquals("FwdSteve", loginReader.readString());
                assertEquals(REAL_UUID, loginReader.readUuid(),
                        "内部バニラへの Login Start は転送された本物のUUIDのはず");
                // バニラは offline UUID で Login Success を返す（実バニラの挙動）。
                Writer success = new Writer();
                success.writeUuid(VANILLA_UUID);
                success.writeString("FwdSteve");
                success.writeVarInt(0);
                vanilla.sendPacket(new MinecraftPacket(0x02, success.bytes()));
                // クライアント側：UUIDはバニラのもの、プロパティは転送のものに
                // 差し替わっている（DetergentMC層の素性採用の範囲）。
                MinecraftPacket loginSuccess = client.readPacket();
                assertEquals(0x02, loginSuccess.id());
                Reader successReader = new Reader(loginSuccess.payload());
                assertEquals(VANILLA_UUID, successReader.readUuid());
                assertEquals("FwdSteve", successReader.readString());
                assertEquals(1, successReader.readVarInt());
                assertEquals("textures", successReader.readString());
                assertEquals("fwd-texture-value", successReader.readString());
                assertTrue(successReader.readBoolean());
                assertEquals("fwd-sig", successReader.readString());
            } finally {
                proxy.stop();
            }
        }
    }

    @Test
    void tamperedSignatureDisconnects() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = forwardingProxy(proxyPort, vanilla.port());
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                int transactionId = beginForwardingLogin(client, vanilla, "FwdSteve");
                respondForwarding(client, transactionId, true,
                        forwardingWire("wrong-secret", forwardingJson("FwdSteve",
                                REAL_UUID, "203.0.113.7:51234", true)));
                MinecraftPacket disconnect = client.readPacket();
                assertEquals(0x00, disconnect.id(), "Login Disconnect（0x00）が来るはず");
                String text = new String(disconnect.payload(), StandardCharsets.UTF_8);
                assertTrue(text.contains("Surfactant"),
                        "切断理由に Surfactant の案内が入るはず: " + text);
                assertNull(vanilla.tryReadPacket(700),
                        "検証に失敗した接続はバニラへ何も送らないはず");
            } finally {
                proxy.stop();
            }
        }
    }

    @Test
    void notUnderstoodDisconnects() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = forwardingProxy(proxyPort, vanilla.port());
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                int transactionId = beginForwardingLogin(client, vanilla, "FwdSteve");
                respondForwarding(client, transactionId, false, null);
                MinecraftPacket disconnect = client.readPacket();
                assertEquals(0x00, disconnect.id());
                String text = new String(disconnect.payload(), StandardCharsets.UTF_8);
                assertTrue(text.contains("Surfactant"),
                        "切断理由に Surfactant の案内が入るはず: " + text);
            } finally {
                proxy.stop();
            }
        }
    }

    @Test
    void nameMismatchDisconnects() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = forwardingProxy(proxyPort, vanilla.port());
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                int transactionId = beginForwardingLogin(client, vanilla, "FwdSteve");
                respondForwarding(client, transactionId, true,
                        forwardingWire(SECRET, forwardingJson("SomeoneElse",
                                REAL_UUID, "203.0.113.7:51234", false)));
                MinecraftPacket disconnect = client.readPacket();
                assertEquals(0x00, disconnect.id());
                assertNull(vanilla.tryReadPacket(700),
                        "名前が違う転送はバニラへ何も送らないはず");
            } finally {
                proxy.stop();
            }
        }
    }

    // ------------------------------------------------------------------
    // 偽バニラ（765・平文）
    // ------------------------------------------------------------------

    private static final class FakeVanilla implements AutoCloseable {
        private final ServerSocket serverSocket;
        private Socket socket;
        private InputStream in;
        private OutputStream out;

        FakeVanilla() throws IOException {
            serverSocket = new ServerSocket(0);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void accept() throws IOException {
            serverSocket.setSoTimeout(10_000);
            socket = serverSocket.accept();
            socket.setSoTimeout(10_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
        }

        MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            return MinecraftPacket.decodeOne(concatVarInt(length, frame), 0).packet();
        }

        MinecraftPacket tryReadPacket(long timeoutMillis) throws IOException {
            socket.setSoTimeout((int) timeoutMillis);
            try {
                return readPacket();
            } catch (SocketTimeoutException | EOFException e) {
                return null;
            } finally {
                socket.setSoTimeout(10_000);
            }
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        @Override
        public void close() throws IOException {
            if (socket != null) {
                socket.close();
            }
            serverSocket.close();
        }
    }

    // ------------------------------------------------------------------
    // 偽クライアント（765・平文）
    // ------------------------------------------------------------------

    private static final class FakeClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        FakeClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(10_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            return MinecraftPacket.decodeOne(concatVarInt(length, frame), 0).packet();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    // ------------------------------------------------------------------
    // ヘルパー
    // ------------------------------------------------------------------

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int current = in.read();
            if (current < 0) {
                throw new EOFException("VarIntの読み取り中にストリームが終わりました。");
            }
            value |= (current & 0x7F) << (7 * position);
            position++;
            if (position > 5) {
                throw new IOException("VarIntが長すぎます。");
            }
            if ((current & 0x80) == 0) {
                return value;
            }
        }
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(bytes, read, length - read);
            if (n < 0) {
                throw new EOFException("ストリームが途中で終わりました（"
                        + read + "/" + length + "バイト）。");
            }
            read += n;
        }
        return bytes;
    }

    private static byte[] concatVarInt(int length, byte[] frame) {
        byte[] lengthBytes = VarInt.encode(length);
        byte[] all = new byte[lengthBytes.length + frame.length];
        System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
        System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
        return all;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static byte[] handshakePayload(int protocol, int nextState) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    private static byte[] loginStart765(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }
}
