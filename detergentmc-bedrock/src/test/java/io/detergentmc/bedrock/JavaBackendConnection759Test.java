package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIds;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Bedrock → 内部 759（Java 1.19）サーバーのテスト。
 * 偽 Java サーバー（759 役）とのログイン〜プレイの流れを確認する。
 * 759 は 760 と同じく設定フェーズなし・Join Game codec・NBT アイテムで、
 * Login Start は名前＋署名データ option のみ（playerUUID は無い。
 * 760で追加された）。Join Game は portalCooldown なし。
 * Player Info は旧式（action varint）で、add_player の crypto は
 * {timestamp, 公開鍵, 署名}（UUID 無し）。Player Chat は初代の
 * インライン署名形式（signedChatContent・unsigned・type・senderUuid・
 * senderName・senderTeam・timestamp・salt・signature）。
 * 署名付きチャットは759（1.19）で導入された。760は連鎖ハッシュ＋
 * Message Header 付きの別形式。
 */
class JavaBackendConnection759Test {

    /** 偽 Java サーバー（サーバー役）。送られてきたパケットを記録する。 */
    private static final class FakeJavaServer implements AutoCloseable {
        final ServerSocket serverSocket;
        final Socket socket;
        final InputStream in;
        final OutputStream out;
        final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        final MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
        volatile boolean closed;

        FakeJavaServer(ServerSocket serverSocket) throws IOException {
            this.serverSocket = serverSocket;
            socket = serverSocket.accept();
            socket.setSoTimeout(10_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        received.addAll(decoder.feed(buffer, 0, read));
                    }
                } catch (IOException e) {
                    closed = true;
                }
            }, "fake-java-server-reader-759");
            reader.setDaemon(true);
            reader.start();
        }

        synchronized void send(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean sawId(int id, long timeoutMillis) throws InterruptedException {
            return waitForId(id, timeoutMillis) != null;
        }

        @Override
        public void close() throws IOException {
            socket.close();
            serverSocket.close();
        }
    }

    private static final class ListenerEvents implements JavaBackendConnection.Listener {
        final CountDownLatch joined = new CountDownLatch(1);
        final CountDownLatch disconnected = new CountDownLatch(1);
        final int[] entityId = {-1};
        final List<String> disconnectReasons = new ArrayList<>();
        final List<Object[]> playerAdds = new ArrayList<>();

        @Override
        public void onJoined(int javaEntityId) {
            entityId[0] = javaEntityId;
            joined.countDown();
        }

        @Override
        public void onSpawnPosition(double x, double y, double z) {
        }

        @Override
        public void onChat(String sender, String message) {
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
        }

        @Override
        public void onDisconnected(String reason) {
            disconnectReasons.add(reason);
            disconnected.countDown();
        }

        @Override
        public void onPlayerInfoAdd(UUID uuid, String name, int gamemode) {
            playerAdds.add(new Object[]{uuid, name, gamemode});
        }
    }

    /** 偽サーバーを別スレッドで受け入れる。 */
    private static void acceptInBackground(ServerSocket serverSocket,
            AtomicReference<FakeJavaServer> holder, AtomicReference<Exception> error) {
        Thread thread = new Thread(() -> {
            try {
                holder.set(new FakeJavaServer(serverSocket));
            } catch (IOException e) {
                error.set(e);
            }
        }, "fake-java-server-accept-759");
        thread.setDaemon(true);
        thread.start();
    }

    private static FakeJavaServer waitForServer(
            AtomicReference<FakeJavaServer> holder, AtomicReference<Exception> error)
            throws Exception {
        FakeJavaServer server = null;
        for (int i = 0; i < 100 && server == null; i++) {
            Thread.sleep(50);
            server = holder.get();
        }
        assertNotNull(server, "偽サーバーが受け入れませんでした: " + error.get());
        return server;
    }

    /** 759 の Login Success ペイロード（UUID・名前・空の属性）。 */
    private static byte[] loginSuccess759(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeUuid(uuid);
        writer.writeString(name);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /** 759 の Join Game ペイロード（760 と同形式。codec は空）。 */
    private static byte[] joinGame759(int entityId) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(entityId);
        writer.writeBoolean(false); // hardcore
        writer.writeByte(0); // gameMode
        writer.writeByte(-1); // previousGameMode
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of())));
        writer.writeString("minecraft:overworld"); // worldType
        writer.writeString("minecraft:overworld"); // worldName
        writer.writeLong(123L); // hashedSeed
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebug
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // hasDeath
        return writer.bytes();
    }

    @Test
    void loginStart759SendsNameNoSignatureAndNoUuid() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            UUID uuid = UUID.randomUUID();
            ListenerEvents listener = new ListenerEvents();
            JavaBackendConnection connection = new JavaBackendConnection(
                    "127.0.0.1", port, 759, "Steve", uuid, listener);
            connection.connect();
            FakeJavaServer server = waitForServer(holder, error);
            try {
                // ハンドシェイク：プロトコル 759。
                MinecraftPacket handshake = server.waitForId(0x00, 5_000);
                assertNotNull(handshake, "ハンドシェイクが届きませんでした");
                PacketIO.Reader handshakeReader = new PacketIO.Reader(handshake.payload());
                assertEquals(759, handshakeReader.readVarInt());
                // ログイン開始：名前＋署名データ無し（false）のみ。
                // 759 には playerUUID の option が無い（760で追加された）。
                MinecraftPacket loginStart = server.waitForId(0x00, 5_000);
                assertNotNull(loginStart, "ログイン開始が届きませんでした");
                PacketIO.Reader reader = new PacketIO.Reader(loginStart.payload());
                assertEquals("Steve", reader.readString());
                assertFalse(reader.readBoolean(), "759 の Login Start は署名データ無し");
                assertEquals(0, reader.remaining(), "759 の Login Start には UUID が無いはず");
            } finally {
                connection.close();
                server.close();
            }
        }
    }

    @Test
    void loginSuccess759GoesStraightToPlayAndJoinGameWorks() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            UUID uuid = UUID.randomUUID();
            ListenerEvents listener = new ListenerEvents();
            JavaBackendConnection connection = new JavaBackendConnection(
                    "127.0.0.1", port, 759, "Steve", uuid, listener);
            connection.connect();
            FakeJavaServer server = waitForServer(holder, error);
            try {
                // ハンドシェイクとログイン開始を読み飛ばす。
                assertNotNull(server.waitForId(0x00, 5_000));
                assertNotNull(server.waitForId(0x00, 5_000));
                // Login Success（0x02）を送る。
                int loginSuccessId = PacketIds.id(PacketIds.PacketName.LOGIN_SUCCESS, 759)
                        .orElseThrow();
                assertEquals(0x02, loginSuccessId);
                server.send(new MinecraftPacket(loginSuccessId, loginSuccess759("Steve", uuid)));
                // 759 には設定フェーズが無い：Login Acknowledged（0x03）は送られない。
                assertFalse(server.sawId(0x03, 500),
                        "759 で Login Acknowledged が送られてはいけない");
                // Join Game（0x23）を送るとプレイに入る。
                int joinGameId = PacketIds.id(PacketIds.PacketName.PLAY_JOIN_GAME, 759)
                        .orElseThrow();
                assertEquals(0x23, joinGameId);
                server.send(new MinecraftPacket(joinGameId, joinGame759(42)));
                assertTrue(listener.joined.await(5, TimeUnit.SECONDS), "Join Game で参加しませんでした");
                assertEquals(42, listener.entityId[0]);
            } finally {
                connection.close();
                server.close();
            }
        }
    }

    @Test
    void sendChat759SendsOldStyleChatMessage() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            UUID uuid = UUID.randomUUID();
            ListenerEvents listener = new ListenerEvents();
            JavaBackendConnection connection = new JavaBackendConnection(
                    "127.0.0.1", port, 759, "Steve", uuid, listener);
            connection.connect();
            FakeJavaServer server = waitForServer(holder, error);
            try {
                // ハンドシェイクとログイン開始を読み飛ばす。
                assertNotNull(server.waitForId(0x00, 5_000));
                assertNotNull(server.waitForId(0x00, 5_000));
                // Login Success と Join Game でプレイに入る（sendChat は joined の後だけ送る）。
                int loginSuccessId = PacketIds.id(PacketIds.PacketName.LOGIN_SUCCESS, 759)
                        .orElseThrow();
                server.send(new MinecraftPacket(loginSuccessId, loginSuccess759("Steve", uuid)));
                int joinGameId = PacketIds.id(PacketIds.PacketName.PLAY_JOIN_GAME, 759)
                        .orElseThrow();
                server.send(new MinecraftPacket(joinGameId, joinGame759(42)));
                assertTrue(listener.joined.await(5, TimeUnit.SECONDS), "Join Game で参加しませんでした");
                connection.sendChat("hello");
                // 759 の Chat Message（0x04）は {message, timestamp, salt,
                // 署名バッファ, signedPreview}（previousMessages は760で追加）。
                MinecraftPacket chat = server.waitForId(0x04, 5_000);
                assertNotNull(chat, "Chat Message が届きませんでした");
                PacketIO.Reader reader = new PacketIO.Reader(chat.payload());
                assertEquals("hello", reader.readString());
                reader.readLong(); // timestamp
                reader.readLong(); // salt
                assertEquals(0, reader.readByteArray().length); // 署名なし
                assertFalse(reader.readBoolean()); // signedPreview
                assertEquals(0, reader.remaining());
            } finally {
                connection.close();
                server.close();
            }
        }
    }

    @Test
    void playerInfo759AddPlayerWithoutCrypto() throws Exception {
        UUID uuid = UUID.randomUUID();
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = new JavaBackendConnection(
                "127.0.0.1", 0, 759, "Steve", UUID.randomUUID(), listener);
        // Player Info（0x34）：action 0 add_player、crypto 無し。
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(0); // add_player
        writer.writeVarInt(1); // count
        writer.writeUuid(uuid);
        writer.writeString("Alex");
        writer.writeVarInt(0); // properties（空）
        writer.writeVarInt(1); // gamemode
        writer.writeVarInt(50); // ping
        writer.writeBoolean(true); // display name あり
        writer.writeString("{\"text\":\"Alex\"}"); // display name（JSON）
        writer.writeBoolean(false); // crypto 無し
        int id = PacketIds.id(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE, 759).orElseThrow();
        assertEquals(0x34, id);
        int state = connection.handleServerPacket(new MinecraftPacket(id, writer.bytes()), 2);
        assertEquals(2, state);
        assertEquals(1, listener.playerAdds.size());
        assertEquals(uuid, listener.playerAdds.get(0)[0]);
        assertEquals("Alex", listener.playerAdds.get(0)[1]);
        assertEquals(1, listener.playerAdds.get(0)[2]);
    }

    @Test
    void playerInfo759AddPlayerWithCrypto() throws Exception {
        UUID uuid = UUID.randomUUID();
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = new JavaBackendConnection(
                "127.0.0.1", 0, 759, "Steve", UUID.randomUUID(), listener);
        // Player Info（0x34）：action 0 add_player、crypto あり。
        // 759 の crypto は {timestamp, 公開鍵, 署名}（UUID 無し。760と同じ）。
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(0); // add_player
        writer.writeVarInt(1); // count
        writer.writeUuid(uuid);
        writer.writeString("Alex");
        writer.writeVarInt(0); // properties（空）
        writer.writeVarInt(1); // gamemode
        writer.writeVarInt(50); // ping
        writer.writeBoolean(false); // display name 無し
        writer.writeBoolean(true); // crypto あり
        writer.writeLong(999L); // timestamp
        writer.writeByteArray(new byte[]{0x01, 0x02}); // 公開鍵
        writer.writeByteArray(new byte[]{0x03, 0x04}); // 署名
        int id = PacketIds.id(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE, 759).orElseThrow();
        int state = connection.handleServerPacket(new MinecraftPacket(id, writer.bytes()), 2);
        assertEquals(2, state);
        assertEquals(1, listener.playerAdds.size());
        assertEquals(uuid, listener.playerAdds.get(0)[0]);
        assertEquals("Alex", listener.playerAdds.get(0)[1]);
        assertEquals(1, listener.playerAdds.get(0)[2]);
    }

    @Test
    void extractPlayerChat759ReadsSenderAndMessage() {
        UUID sender = UUID.randomUUID();
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("hello"); // signedChatContent
        writer.writeBoolean(false); // unsigned 無し
        writer.writeVarInt(0); // type（chat）
        writer.writeUuid(sender);
        writer.writeString("Steve"); // senderName
        writer.writeBoolean(false); // senderTeam 無し
        writer.writeLong(100L); // timestamp
        writer.writeLong(200L); // salt
        writer.writeByteArray(new byte[]{0x01, 0x02}); // 署名
        JavaBackendConnection.PlayerChat chat =
                JavaBackendConnection.extractPlayerChat759(writer.bytes());
        assertNotNull(chat);
        assertEquals(sender, chat.senderUuid());
        assertEquals("hello", chat.message());
    }
}
