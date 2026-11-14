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
 * Bedrock → 内部 760（Java 1.19.1／1.19.2）サーバーのテスト。
 * 偽 Java サーバー（760 役）とのログイン〜プレイの流れを確認する。
 * 760 は 761 と同じく設定フェーズなし・Join Game codec・NBT アイテムで、
 * Login Start は名前＋署名データ option＋任意UUID、Join Game は
 * portalCooldown なし。Player Info は旧式（action varint）で、
 * add_player の crypto は {timestamp, 公開鍵, 署名}（UUID 無し）。
 * Player Chat は Message Header 分離＋previousSignature＋formattedMessage＋
 * UUID 付き previousMessages の760形式。
 */
class JavaBackendConnection760Test {

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
            }, "fake-java-server-reader-760");
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
        }, "fake-java-server-accept-760");
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

    /** 760 の Login Success ペイロード（UUID・名前・空の属性）。 */
    private static byte[] loginSuccess760(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeUuid(uuid);
        writer.writeString(name);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /** 760 の Join Game ペイロード（761 と同形式。codec は空）。 */
    private static byte[] joinGame760(int entityId) {
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
    void loginStart760SendsNameNoSignatureAndUuid() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            UUID uuid = UUID.randomUUID();
            ListenerEvents listener = new ListenerEvents();
            JavaBackendConnection connection = new JavaBackendConnection(
                    "127.0.0.1", port, 760, "Steve", uuid, listener);
            connection.connect();
            FakeJavaServer server = waitForServer(holder, error);
            try {
                // ハンドシェイク：プロトコル 760。
                MinecraftPacket handshake = server.waitForId(0x00, 5_000);
                assertNotNull(handshake, "ハンドシェイクが届きませんでした");
                PacketIO.Reader handshakeReader = new PacketIO.Reader(handshake.payload());
                assertEquals(760, handshakeReader.readVarInt());
                // ログイン開始：名前＋署名データ無し（false）＋UUID あり（true）＋UUID。
                MinecraftPacket loginStart = server.waitForId(0x00, 5_000);
                assertNotNull(loginStart, "ログイン開始が届きませんでした");
                PacketIO.Reader reader = new PacketIO.Reader(loginStart.payload());
                assertEquals("Steve", reader.readString());
                assertFalse(reader.readBoolean(), "760 の Login Start は署名データ無し");
                assertTrue(reader.readBoolean(), "760 の Login Start には UUID の option フラグが必要");
                assertEquals(uuid, reader.readUuid());
            } finally {
                connection.close();
                server.close();
            }
        }
    }

    @Test
    void loginSuccess760GoesStraightToPlayAndJoinGameWorks() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            UUID uuid = UUID.randomUUID();
            ListenerEvents listener = new ListenerEvents();
            JavaBackendConnection connection = new JavaBackendConnection(
                    "127.0.0.1", port, 760, "Steve", uuid, listener);
            connection.connect();
            FakeJavaServer server = waitForServer(holder, error);
            try {
                // ハンドシェイクとログイン開始を読み飛ばす。
                assertNotNull(server.waitForId(0x00, 5_000));
                assertNotNull(server.waitForId(0x00, 5_000));
                // Login Success（0x02）を送る。
                int loginSuccessId = PacketIds.id(PacketIds.PacketName.LOGIN_SUCCESS, 760)
                        .orElseThrow();
                assertEquals(0x02, loginSuccessId);
                server.send(new MinecraftPacket(loginSuccessId, loginSuccess760("Steve", uuid)));
                // 760 には設定フェーズが無い：Login Acknowledged（0x03）は送られない。
                assertFalse(server.sawId(0x03, 500),
                        "760 で Login Acknowledged が送られてはいけない");
                // Join Game（0x25）を送るとプレイに入る。
                int joinGameId = PacketIds.id(PacketIds.PacketName.PLAY_JOIN_GAME, 760)
                        .orElseThrow();
                assertEquals(0x25, joinGameId);
                server.send(new MinecraftPacket(joinGameId, joinGame760(42)));
                assertTrue(listener.joined.await(5, TimeUnit.SECONDS), "Join Game で参加しませんでした");
                assertEquals(42, listener.entityId[0]);
            } finally {
                connection.close();
                server.close();
            }
        }
    }

    @Test
    void playerInfo760AddPlayerWithoutCrypto() throws Exception {
        UUID uuid = UUID.randomUUID();
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = new JavaBackendConnection(
                "127.0.0.1", 0, 760, "Steve", UUID.randomUUID(), listener);
        // Player Info（0x37）：action 0 add_player、crypto 無し。
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
        int id = PacketIds.id(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE, 760).orElseThrow();
        assertEquals(0x37, id);
        int state = connection.handleServerPacket(new MinecraftPacket(id, writer.bytes()), 2);
        assertEquals(2, state);
        assertEquals(1, listener.playerAdds.size());
        assertEquals(uuid, listener.playerAdds.get(0)[0]);
        assertEquals("Alex", listener.playerAdds.get(0)[1]);
        assertEquals(1, listener.playerAdds.get(0)[2]);
    }

    @Test
    void playerInfo760AddPlayerWithCrypto() throws Exception {
        UUID uuid = UUID.randomUUID();
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = new JavaBackendConnection(
                "127.0.0.1", 0, 760, "Steve", UUID.randomUUID(), listener);
        // Player Info（0x37）：action 0 add_player、crypto あり。
        // 760 の crypto は {timestamp, 公開鍵, 署名}（UUID 無し）。
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
        int id = PacketIds.id(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE, 760).orElseThrow();
        int state = connection.handleServerPacket(new MinecraftPacket(id, writer.bytes()), 2);
        assertEquals(2, state);
        assertEquals(1, listener.playerAdds.size());
        assertEquals(uuid, listener.playerAdds.get(0)[0]);
        assertEquals("Alex", listener.playerAdds.get(0)[1]);
        assertEquals(1, listener.playerAdds.get(0)[2]);
    }

    @Test
    void extractPlayerChat760ReadsSenderAndMessage() {
        UUID sender = UUID.randomUUID();
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(false); // previousSignature 無し
        writer.writeUuid(sender);
        writer.writeByteArray(new byte[]{0x01, 0x02}); // 署名
        writer.writeString("hello");
        JavaBackendConnection.PlayerChat chat =
                JavaBackendConnection.extractPlayerChat760(writer.bytes());
        assertNotNull(chat);
        assertEquals(sender, chat.senderUuid());
        assertEquals("hello", chat.message());
    }
}
