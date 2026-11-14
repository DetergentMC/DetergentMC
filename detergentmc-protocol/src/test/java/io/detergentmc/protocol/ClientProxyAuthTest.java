package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * プロキシ認証（online-mode）の Netty 結合テスト（担当1）。
 * 相手は偽バニラ（765側・平文）と偽セッションサーバー（ローカルHTTP）。
 * 本物の Mojang には触らない。
 */
@Timeout(60)
class ClientProxyAuthTest {

    private static final UUID AUTH_UUID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private HttpServer fakeSessionServer;
    private String sessionServerUrl;
    private final AtomicReference<String> lastUsername = new AtomicReference<>();
    private final AtomicReference<String> lastServerId = new AtomicReference<>();

    @BeforeEach
    void startFakeSessionServer() throws IOException {
        fakeSessionServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeSessionServer.createContext("/session/minecraft/hasJoined", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String username = queryValue(query, "username");
            String serverId = queryValue(query, "serverId");
            lastUsername.set(username);
            lastServerId.set(serverId);
            byte[] body;
            int status;
            if ("Steve".equals(username) && serverId != null && !serverId.isBlank()) {
                status = 200;
                body = ("{\"id\":\"123e4567e89b12d3a456426614174000\","
                        + "\"name\":\"Steve\","
                        + "\"properties\":[{\"name\":\"textures\","
                        + "\"value\":\"dGV4dHVyZXNWYWx1ZQ==\","
                        + "\"signature\":\"c2lnbmF0dXJl\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
            } else {
                status = 404;
                body = new byte[0];
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        fakeSessionServer.start();
        sessionServerUrl = "http://127.0.0.1:" + fakeSessionServer.getAddress().getPort();
    }

    @AfterEach
    void stopFakeSessionServer() {
        if (fakeSessionServer != null) {
            fakeSessionServer.stop(0);
        }
    }

    private static String queryValue(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(
                        pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 偽バニラ（ClientProxyTest と同じ最小サーバー＋タイムアウト読み取り）
    // ------------------------------------------------------------------

    static final class FakeVanilla implements AutoCloseable {
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

        /** タイムアウト付きで1フレーム読む。届かない・接続が閉じられた場合は null。 */
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

        byte[] readRaw(int length) throws IOException {
            return readExact(in, length);
        }

        void writeRaw(byte[] bytes) throws IOException {
            out.write(bytes);
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
    // 偽クライアント（Encryption Response の後に AES/CFB8 を有効化できる）
    // ------------------------------------------------------------------

    static final class FakeClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream plainIn;
        private final OutputStream plainOut;
        private InputStream in;
        private OutputStream out;
        private boolean encrypted;

        FakeClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(10_000);
            plainIn = socket.getInputStream();
            plainOut = socket.getOutputStream();
            in = plainIn;
            out = plainOut;
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        void sendRaw(byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            return MinecraftPacket.decodeOne(concatVarInt(length, frame), 0).packet();
        }

        byte[] readRaw(int length) throws IOException {
            return readExact(in, length);
        }

        /** Encryption Response を送った後に呼ぶ。以後の送受信は暗号化される。 */
        void enableEncryption(byte[] sharedSecret) {
            out = new CipherOutputStream(plainOut,
                    MojangAuth.createEncryptCipher(sharedSecret));
            in = new CipherInputStream(plainIn,
                    MojangAuth.createDecryptCipher(sharedSecret));
            encrypted = true;
        }

        boolean isClosedByPeer() throws IOException {
            socket.setSoTimeout(2_000);
            try {
                return in.read() < 0;
            } catch (SocketTimeoutException e) {
                return false;
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

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

    private static byte[] loginStart763(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    private static byte[] loginStart765(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(AUTH_UUID);
        writer.writeString("Steve");
        writer.writeVarInt(0);
        return writer.bytes();
    }

    private static byte[] registryDataPayload() {
        Map<String, Tag> root = new java.util.LinkedHashMap<>();
        root.put("minecraft:dimension_type", new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:dimension_type"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Tag.CompoundTag(Map.of(
                                "name", new Tag.StringTag("minecraft:overworld"),
                                "id", new Tag.IntTag(0),
                                "element", new Tag.CompoundTag(Map.of()))))))));
        root.put("minecraft:worldgen/biome", new Tag.CompoundTag(Map.of(
                "type", new Tag.StringTag("minecraft:worldgen/biome"),
                "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                        new Tag.CompoundTag(Map.of(
                                "name", new Tag.StringTag("minecraft:plains"),
                                "id", new Tag.IntTag(0),
                                "element", new Tag.CompoundTag(Map.of()))))))));
        return Nbt.writeAnonymous(new Tag.CompoundTag(root));
    }

    private static byte[] joinGame765Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeString("minecraft:overworld");
        writer.writeString("minecraft:overworld");
        writer.writeLong(99L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        return writer.bytes();
    }

    private ProxyAuthConfig fakeAuthConfig() {
        return ProxyAuthConfig.of(sessionServerUrl);
    }

    // ------------------------------------------------------------------
    // テスト
    // ------------------------------------------------------------------

    @Test
    void join763OnlineThroughProxy() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanilla.port(),
                    true, null, null, fakeAuthConfig());
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                vanilla.accept();
                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(763, 2)));
                client.sendPacket(new MinecraftPacket(0x00, loginStart763("Steve")));
                // 認証が終わるまで、バニラには何も届かない。
                assertNull(vanilla.tryReadPacket(500),
                        "認証前にハンドシェイクがバニラへ届いてはいけない");
                // Encryption Request がクライアントへ来る。
                MinecraftPacket request = client.readPacket();
                assertEquals(0x01, request.id());
                MojangAuth.EncryptionRequest parsed =
                        MojangAuth.parseEncryptionRequest(request.payload());
                byte[] secret = new byte[16];
                Arrays.fill(secret, (byte) 0x5A);
                client.sendPacket(new MinecraftPacket(0x01,
                        MojangAuth.buildEncryptionResponsePayload(secret,
                                parsed.verifyToken(),
                                MojangAuth.decodePublicKey(parsed.publicKey()))));
                client.enableEncryption(secret);
                // 認証成功後、バニラにハンドシェイク（765）と認証済み Login Start が届く。
                MinecraftPacket handshake = vanilla.readPacket();
                assertEquals(0x00, handshake.id());
                assertEquals(765, PlayTranslator.parseHandshake(handshake.payload()).protocol());
                MinecraftPacket loginStart = vanilla.readPacket();
                assertEquals(0x00, loginStart.id());
                Reader loginReader = new Reader(loginStart.payload());
                assertEquals("Steve", loginReader.readString());
                assertEquals(AUTH_UUID, loginReader.readUuid());
                // セッションサーバーには正しい serverId ハッシュで照会された。
                assertEquals("Steve", lastUsername.get());
                assertEquals(MojangAuth.serverIdHash("", secret, parsed.publicKey()),
                        lastServerId.get());
                // ログイン成功（プロパティ付き）が暗号化されてクライアントへ届く。
                vanilla.sendPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
                MinecraftPacket success = client.readPacket();
                assertEquals(0x02, success.id());
                Reader successReader = new Reader(success.payload());
                assertEquals(AUTH_UUID, successReader.readUuid());
                assertEquals("Steve", successReader.readString());
                assertEquals(1, successReader.readVarInt());
                assertEquals("textures", successReader.readString());
                // Login Acknowledged＋Client Information がバニラへ。
                assertEquals(0x03, vanilla.readPacket().id());
                assertEquals(0x00, vanilla.readPacket().id());
                // 設定フェーズを消費して Join Game まで進める。
                vanilla.sendPacket(new MinecraftPacket(0x05, registryDataPayload()));
                vanilla.sendPacket(new MinecraftPacket(0x02, new byte[0]));
                assertEquals(0x02, vanilla.readPacket().id());
                vanilla.sendPacket(new MinecraftPacket(0x29, joinGame765Payload()));
                MinecraftPacket joinGame = client.readPacket();
                assertEquals(0x28, joinGame.id());
                // 暗号化されたままチャットがバニラへ届く。
                Writer message = new Writer();
                message.writeString("hello auth");
                client.sendPacket(new MinecraftPacket(0x05, message.bytes()));
                MinecraftPacket chat = vanilla.readPacket();
                assertEquals(0x05, chat.id());
                assertEquals("hello auth", new Reader(chat.payload()).readString());
            } finally {
                proxy.stop();
            }
            assertFalse(proxy.isRunning());
        }
    }

    @Test
    void join765OnlineThroughProxyThenRelaysEncrypted() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanilla.port(),
                    true, null, null, fakeAuthConfig());
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                vanilla.accept();
                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(765, 2)));
                client.sendPacket(new MinecraftPacket(0x00, loginStart765("Steve")));
                assertNull(vanilla.tryReadPacket(500),
                        "認証前にハンドシェイクがバニラへ届いてはいけない");
                MinecraftPacket request = client.readPacket();
                assertEquals(0x01, request.id());
                MojangAuth.EncryptionRequest parsed =
                        MojangAuth.parseEncryptionRequest(request.payload());
                byte[] secret = MojangAuth.generateSharedSecret();
                client.sendPacket(new MinecraftPacket(0x01,
                        MojangAuth.buildEncryptionResponsePayload(secret,
                                parsed.verifyToken(),
                                MojangAuth.decodePublicKey(parsed.publicKey()))));
                client.enableEncryption(secret);
                assertEquals(0x00, vanilla.readPacket().id());
                MinecraftPacket loginStart = vanilla.readPacket();
                Reader loginReader = new Reader(loginStart.payload());
                assertEquals("Steve", loginReader.readString());
                assertEquals(AUTH_UUID, loginReader.readUuid());
                vanilla.sendPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
                MinecraftPacket success = client.readPacket();
                assertEquals(0x02, success.id());
                // Login Success の後はリレー：生バイトが暗号化されたまま通る。
                byte[] acknowledged = new MinecraftPacket(0x03, new byte[0]).encode();
                client.sendRaw(acknowledged);
                assertArrayEquals(acknowledged, vanilla.readRaw(acknowledged.length));
                byte[] reply = new byte[]{0x05, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
                vanilla.writeRaw(reply);
                assertArrayEquals(reply, client.readRaw(reply.length));
            } finally {
                proxy.stop();
            }
        }
    }

    @Test
    void authFailureDisconnects763Client() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanilla.port(),
                    true, null, null, fakeAuthConfig());
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                vanilla.accept();
                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(763, 2)));
                client.sendPacket(new MinecraftPacket(0x00, loginStart763("Mallory")));
                MinecraftPacket request = client.readPacket();
                assertEquals(0x01, request.id());
                MojangAuth.EncryptionRequest parsed =
                        MojangAuth.parseEncryptionRequest(request.payload());
                byte[] secret = MojangAuth.generateSharedSecret();
                client.sendPacket(new MinecraftPacket(0x01,
                        MojangAuth.buildEncryptionResponsePayload(secret,
                                parsed.verifyToken(),
                                MojangAuth.decodePublicKey(parsed.publicKey()))));
                // 認証失敗：Login Disconnect が返り、接続が閉じられる。
                // クライアントは応答を送った時点で暗号化を始めているので、
                // 切断も暗号化されて届く（本物のクライアントと同じ）。
                client.enableEncryption(secret);
                MinecraftPacket disconnect = client.readPacket();
                assertEquals(0x00, disconnect.id());
                String reason = new Reader(disconnect.payload()).readString();
                assertTrue(reason.contains("Mojang認証"), reason);
                assertTrue(client.isClosedByPeer(), "切断後に接続が閉じられること");
                // バニラには何も届かない。
                assertNull(vanilla.tryReadPacket(500));
            } finally {
                proxy.stop();
            }
        }
    }

    @Test
    void nonLoopbackVanillaHostIsRejected() throws Exception {
        int proxyPort = freePort();
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                proxyPort, "192.168.1.1", 25566, true));
        assertThrows(IllegalArgumentException.class, () -> new ClientProxy(
                proxyPort, "example.invalid", 25566, true));
        // loopback は受け付ける。
        ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", 25566, true);
        assertEquals("127.0.0.1", proxy.vanillaHost());
    }
}
