package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Netty プロキシの結合テスト。相手は本物のバニラではなく、765側を話す
 * 偽バニラ（プレーンなソケットサーバー）である。本物のバニラに対する
 * 統合テストは detergentmc-core の ProxyIntegrationTest にある。
 */
@Timeout(60)
class ClientProxyTest {

    // ------------------------------------------------------------------
    // 偽バニラ（765側）
    // ------------------------------------------------------------------

    /** 765側を話す最小のサーバー。フレーム単位で読み書きする。 */
    static final class FakeVanilla implements AutoCloseable {
        private final ServerSocket serverSocket;
        private Socket socket;
        private InputStream in;
        private OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();

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

        /** 1フレーム読む。 */
        MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            return MinecraftPacket.decodeOne(concatVarInt(length, frame), 0).packet();
        }

        private static byte[] concatVarInt(int length, byte[] frame) {
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return all;
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 届いたパケットをキューにためる読み取りループを開始する。 */
        void startReading() {
            Thread reader = new Thread(() -> {
                try {
                    while (true) {
                        received.add(readPacket());
                    }
                } catch (IOException ignored) {
                    // 接続が切れたら終わり。
                }
            }, "fake-vanilla-reader");
            reader.setDaemon(true);
            reader.start();
        }

        MinecraftPacket pollPacket(long timeoutMillis) throws InterruptedException {
            return received.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        /** 生バイトをちょうど {@code length} バイト読む。 */
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

    // ------------------------------------------------------------------
    // 偽クライアント（テスト側のソケット）
    // ------------------------------------------------------------------

    static final class FakeClient implements AutoCloseable {
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

        void sendRaw(byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            return MinecraftPacket.decodeOne(FakeVanilla.concatVarInt(length, frame), 0)
                    .packet();
        }

        byte[] readRaw(int length) throws IOException {
            return readExact(in, length);
        }

        /** ストリームが閉じられた（EOF）か。 */
        boolean isClosedByPeer() throws IOException {
            socket.setSoTimeout(2_000);
            try {
                return in.read() < 0;
            } catch (java.net.SocketTimeoutException e) {
                return false;
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
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

    private static byte[] loginSuccessPayload() {
        Writer writer = new Writer();
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
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

    // ------------------------------------------------------------------
    // テスト
    // ------------------------------------------------------------------

    @Test
    void join763ThroughProxy() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanilla.port(), false);
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                vanilla.accept();
                // ハンドシェイク（763→765 に書き換えられる）。
                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(763, 2)));
                MinecraftPacket handshake = vanilla.readPacket();
                assertEquals(0x00, handshake.id());
                assertEquals(765, PlayTranslator.parseHandshake(handshake.payload()).protocol());
                // ログイン開始（UUID の bool が外れて765形式になる）。
                client.sendPacket(new MinecraftPacket(0x00, loginStart763("Steve")));
                MinecraftPacket loginStart = vanilla.readPacket();
                assertEquals(0x00, loginStart.id());
                Reader loginReader = new Reader(loginStart.payload());
                assertEquals("Steve", loginReader.readString());
                assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                        loginReader.readUuid());
                assertEquals(0, loginReader.remaining());
                // ログイン成功 → プロキシが Login Acknowledged＋Client Information を送る。
                vanilla.sendPacket(new MinecraftPacket(0x02, loginSuccessPayload()));
                MinecraftPacket success = client.readPacket();
                assertEquals(0x02, success.id());
                assertEquals(0x03, vanilla.readPacket().id()); // Login Acknowledged
                assertEquals(0x00, vanilla.readPacket().id()); // Client Information
                // 設定フェーズ：レジストリ＋リソースパック＋Finish Configuration。
                vanilla.sendPacket(new MinecraftPacket(0x05, registryDataPayload()));
                UUID packUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
                Writer pack = new Writer();
                pack.writeUuid(packUuid);
                pack.writeString("http://127.0.0.1:25567/detergentmc-pack.zip");
                pack.writeString("0123456789abcdef0123456789abcdef01234567");
                pack.writeBoolean(false);
                pack.writeBoolean(false);
                vanilla.sendPacket(new MinecraftPacket(0x07, pack.bytes()));
                vanilla.sendPacket(new MinecraftPacket(0x02, new byte[0]));
                assertEquals(0x02, vanilla.readPacket().id()); // Acknowledge Finish
                // Join Game → 763形式（0x28）に変換され、codec が入る。
                vanilla.sendPacket(new MinecraftPacket(0x29, joinGame765Payload()));
                MinecraftPacket joinGame = client.readPacket();
                assertEquals(0x28, joinGame.id());
                Reader joinReader = new Reader(joinGame.payload());
                assertEquals(42, joinReader.readInt());
                // リソースパックのオファー（0x40）が Join Game の後に来る。
                MinecraftPacket offer = client.readPacket();
                assertEquals(0x40, offer.id());
                assertEquals("http://127.0.0.1:25567/detergentmc-pack.zip",
                        new Reader(offer.payload()).readString());
                // スポーン位置（0x54→0x50）と Keep Alive（0x24→0x23）。
                vanilla.sendPacket(new MinecraftPacket(0x54, new byte[8]));
                assertEquals(0x50, client.readPacket().id());
                Writer alive = new Writer();
                alive.writeLong(77L);
                vanilla.sendPacket(new MinecraftPacket(0x24, alive.bytes()));
                MinecraftPacket keepAlive = client.readPacket();
                assertEquals(0x23, keepAlive.id());
                assertEquals(77L, new Reader(keepAlive.payload()).readLong());
                // System Chat（0x69→0x64、NBT→JSON文字列）。
                Writer chat = new Writer();
                chat.writeAnonymousNbt(new Tag.CompoundTag(
                        Map.of("text", new Tag.StringTag("Welcome!"))));
                chat.writeBoolean(false);
                vanilla.sendPacket(new MinecraftPacket(0x69, chat.bytes()));
                MinecraftPacket systemChat = client.readPacket();
                assertEquals(0x64, systemChat.id());
                assertEquals("{\"text\":\"Welcome!\"}",
                        new Reader(systemChat.payload()).readString());
                // クライアント→サーバー：チャット（文字列だけ→765形式に合成）。
                vanilla.startReading();
                Writer message = new Writer();
                message.writeString("hello proxy");
                client.sendPacket(new MinecraftPacket(0x05, message.bytes()));
                MinecraftPacket chatToServer = vanilla.pollPacket(5_000);
                assertNotNull(chatToServer, "チャットがバニラに届きませんでした。");
                assertEquals(0x05, chatToServer.id());
                assertEquals("hello proxy",
                        new Reader(chatToServer.payload()).readString());
                // 移動（0x14→0x17）。
                Writer move = new Writer();
                move.writeDouble(1.0);
                move.writeDouble(64.0);
                move.writeDouble(2.0);
                move.writeBoolean(true);
                client.sendPacket(new MinecraftPacket(0x14, move.bytes()));
                MinecraftPacket moved = vanilla.pollPacket(5_000);
                assertNotNull(moved, "移動がバニラに届きませんでした。");
                assertEquals(0x17, moved.id());
                // Keep Alive 応答（0x12→0x15）とリソースパック応答（0x24→0x28＋UUID）。
                Writer aliveResponse = new Writer();
                aliveResponse.writeLong(77L);
                client.sendPacket(new MinecraftPacket(0x12, aliveResponse.bytes()));
                MinecraftPacket aliveToServer = vanilla.pollPacket(5_000);
                assertNotNull(aliveToServer);
                assertEquals(0x15, aliveToServer.id());
                Writer packResponse = new Writer();
                packResponse.writeVarInt(0);
                client.sendPacket(new MinecraftPacket(0x24, packResponse.bytes()));
                MinecraftPacket packToServer = vanilla.pollPacket(5_000);
                assertNotNull(packToServer);
                assertEquals(0x28, packToServer.id());
                assertEquals(packUuid, new Reader(packToServer.payload()).readUuid());
            } finally {
                proxy.stop();
            }
            assertFalse(proxy.isRunning());
        }
    }

    @Test
    void status763IsRewritten() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanilla.port(), false);
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                vanilla.accept();
                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(763, 1)));
                assertEquals(765, PlayTranslator.parseHandshake(
                        vanilla.readPacket().payload()).protocol());
                client.sendPacket(new MinecraftPacket(0x00, new byte[0])); // Status Request
                assertEquals(0x00, vanilla.readPacket().id());
                Writer response = new Writer();
                response.writeString("{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                        + "\"players\":{\"max\":20,\"online\":0},"
                        + "\"description\":{\"text\":\"hi\"}}");
                vanilla.sendPacket(new MinecraftPacket(0x00, response.bytes()));
                MinecraftPacket status = client.readPacket();
                assertEquals(0x00, status.id());
                String json = new Reader(status.payload()).readString();
                assertTrue(json.contains("\"protocol\":763"), json);
                assertTrue(json.contains("\"name\":\"1.20.1\""), json);
                // Ping / Pong はそのまま。
                Writer ping = new Writer();
                ping.writeLong(12345L);
                client.sendPacket(new MinecraftPacket(0x01, ping.bytes()));
                assertEquals(0x01, vanilla.readPacket().id());
                vanilla.sendPacket(new MinecraftPacket(0x01, ping.bytes()));
                MinecraftPacket pong = client.readPacket();
                assertEquals(0x01, pong.id());
                assertEquals(12345L, new Reader(pong.payload()).readLong());
            } finally {
                proxy.stop();
            }
        }
    }

    @Test
    void relay765IsByteIdentical() throws Exception {
        // 【担当1】素通し（リレー）は offline-mode の765だけ。
        // online-mode の765はプロキシが認証してからリレーに切り替わる
        // （ClientProxyAuthTest 参照）。
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanilla.port(), false);
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                vanilla.accept();
                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(765, 2)));
                assertEquals(765, PlayTranslator.parseHandshake(
                        vanilla.readPacket().payload()).protocol());
                // リレーに切り替わった後は、生バイトがそのまま通る。
                byte[] loginStart = new MinecraftPacket(0x00, loginStart763("Steve")).encode();
                byte[] extra = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0x00};
                byte[] sent = new byte[loginStart.length + extra.length];
                System.arraycopy(loginStart, 0, sent, 0, loginStart.length);
                System.arraycopy(extra, 0, sent, loginStart.length, extra.length);
                client.sendRaw(sent);
                assertArrayEquals(sent, vanilla.readRaw(sent.length));
                // 逆方向も生バイトのまま。
                byte[] reply = new byte[]{0x05, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
                vanilla.writeRaw(reply);
                assertArrayEquals(reply, client.readRaw(reply.length));
            } finally {
                proxy.stop();
            }
        }
    }

    @Test
    void onlineMode763StartsProxyAuth() throws Exception {
        // 【担当1】763＋online-mode はもう切断しない。プロキシが
        // Encryption Request を送り、認証が終わるまでバニラへは何も送らない。
        // 完全な参加フローは ClientProxyAuthTest で検証する。
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanilla.port(), true);
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                vanilla.accept();
                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(763, 2)));
                client.sendPacket(new MinecraftPacket(0x00, loginStart763("Steve")));
                MinecraftPacket request = client.readPacket();
                assertEquals(0x01, request.id()); // Encryption Request
                assertFalse(client.isClosedByPeer(), "認証の開始で接続が閉じてはいけない");
            } finally {
                proxy.stop();
            }
        }
    }

    @Test
    void unsupportedProtocolLoginIsRejected() throws Exception {
        try (FakeVanilla vanilla = new FakeVanilla()) {
            int proxyPort = freePort();
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanilla.port(), false);
            proxy.start();
            try (FakeClient client = new FakeClient(proxyPort)) {
                vanilla.accept();
                // 776は対応済みのため、拒否されるのは777（26.3以降の未対応番号）。
                client.sendPacket(new MinecraftPacket(0x00, handshakePayload(777, 2)));
                MinecraftPacket disconnect = client.readPacket();
                assertEquals(0x00, disconnect.id());
                String reason = new Reader(disconnect.payload()).readString();
                assertTrue(reason.contains("777"), reason);
            } finally {
                proxy.stop();
            }
        }
    }

    /**
     * 内部763が許可されること。1.20系の相互連携の見出し事例（766クライアントが
     * 1.20／1.20.1サーバーに入る）は、プロキシが内部763で構築できて初めて
     * 成立する。ProxySession 側は以前から内部763（設定フェーズの合成）を
     * 扱えたが、ClientProxy のコンストラクタだけが763を拒否していた。
     * 他の内部プロトコル（764・765・766）と同様に、763も拒否されないこと。
     * 759も760も758も拒否されないこと（1.18.2／1.19／1.19.1／1.19.2対応）。758より古いもの（757など）は今も拒否される。
     */
    @Test
    void internalProtocol763IsAccepted() throws Exception {
        ClientProxy proxy761 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, 761);
        assertEquals(761, proxy761.internalProtocol());
        ClientProxy proxy762 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, 762);
        assertEquals(762, proxy762.internalProtocol());
        ClientProxy proxy763 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, 763);
        assertEquals(763, proxy763.internalProtocol());
        ClientProxy proxy764 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, 764);
        assertEquals(764, proxy764.internalProtocol());
        ClientProxy proxy759 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, 759);
        assertEquals(759, proxy759.internalProtocol());
        ClientProxy proxy758 = new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                null, null, null, ProxyAuthConfig.defaults(), null, null, 758);
        assertEquals(758, proxy758.internalProtocol());
        try {
            new ClientProxy(freePort(), "127.0.0.1", freePort(), false,
                    null, null, null, ProxyAuthConfig.defaults(), null, null, 757);
            throw new AssertionError("expected IllegalArgumentException for 757");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("758"), expected.getMessage());
        }
    }

}