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
 * Bedrock → 内部 761（Java 1.19.3）サーバーのテスト。
 * 偽 Java サーバー（761 役）とのログイン〜プレイの流れを確認する。
 * 761 は 762 と同じく設定フェーズなし・Join Game codec・NBT アイテムで、
 * Login Start は名前＋bool＋UUID、Join Game は portalCooldown なし。
 */
class JavaBackendConnection761Test {

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
            }, "fake-java-server-reader-761");
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
        }, "fake-java-server-accept-761");
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

    /** 761 の Login Success ペイロード（UUID・名前・空の属性）。 */
    private static byte[] loginSuccess761(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeUuid(uuid);
        writer.writeString(name);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /** 761 の Join Game ペイロード（762 と同形式。codec は空）。 */
    private static byte[] joinGame761(int entityId) {
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
    void loginStart761SendsNameBoolAndUuid() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            UUID uuid = UUID.randomUUID();
            ListenerEvents listener = new ListenerEvents();
            JavaBackendConnection connection = new JavaBackendConnection(
                    "127.0.0.1", port, 761, "Steve", uuid, listener);
            connection.connect();
            FakeJavaServer server = waitForServer(holder, error);
            try {
                // ハンドシェイク：プロトコル 761。
                MinecraftPacket handshake = server.waitForId(0x00, 5_000);
                assertNotNull(handshake, "ハンドシェイクが届きませんでした");
                PacketIO.Reader handshakeReader = new PacketIO.Reader(handshake.payload());
                assertEquals(761, handshakeReader.readVarInt());
                // ログイン開始：名前＋bool(true)＋UUID。
                MinecraftPacket loginStart = server.waitForId(0x00, 5_000);
                assertNotNull(loginStart, "ログイン開始が届きませんでした");
                PacketIO.Reader reader = new PacketIO.Reader(loginStart.payload());
                assertEquals("Steve", reader.readString());
                assertTrue(reader.readBoolean(), "761 の Login Start には UUID の option フラグが必要");
                assertEquals(uuid, reader.readUuid());
            } finally {
                connection.close();
                server.close();
            }
        }
    }

    @Test
    void loginSuccess761GoesStraightToPlayAndJoinGameWorks() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            UUID uuid = UUID.randomUUID();
            ListenerEvents listener = new ListenerEvents();
            JavaBackendConnection connection = new JavaBackendConnection(
                    "127.0.0.1", port, 761, "Steve", uuid, listener);
            connection.connect();
            FakeJavaServer server = waitForServer(holder, error);
            try {
                // ハンドシェイクとログイン開始を読み飛ばす。
                assertNotNull(server.waitForId(0x00, 5_000));
                assertNotNull(server.waitForId(0x00, 5_000));
                // Login Success（0x02）を送る。
                int loginSuccessId = PacketIds.id(PacketIds.PacketName.LOGIN_SUCCESS, 761)
                        .orElseThrow();
                assertEquals(0x02, loginSuccessId);
                server.send(new MinecraftPacket(loginSuccessId, loginSuccess761("Steve", uuid)));
                // 761 には設定フェーズが無い：Login Acknowledged（0x03）は送られない。
                assertFalse(server.sawId(0x03, 500),
                        "761 で Login Acknowledged が送られてはいけない");
                // Join Game（0x24）を送るとプレイに入る。
                int joinGameId = PacketIds.id(PacketIds.PacketName.PLAY_JOIN_GAME, 761)
                        .orElseThrow();
                assertEquals(0x24, joinGameId);
                server.send(new MinecraftPacket(joinGameId, joinGame761(42)));
                assertTrue(listener.joined.await(5, TimeUnit.SECONDS), "Join Game で参加しませんでした");
                assertEquals(42, listener.entityId[0]);
            } finally {
                connection.close();
                server.close();
            }
        }
    }

    @Test
    void playerInfo761DisplayNameIsJsonString() throws Exception {
        UUID uuid = UUID.randomUUID();
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = new JavaBackendConnection(
                "127.0.0.1", 0, 761, "Steve", UUID.randomUUID(), listener);
        // Player Info Update（0x36）：add player 1 件、display name は JSON 文字列。
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(0x01); // actions: add player
        writer.writeVarInt(1); // count
        writer.writeUuid(uuid);
        writer.writeString("Alex");
        writer.writeVarInt(0); // properties（空）
        writer.writeVarInt(0); // gamemode
        writer.writeVarInt(0); // ping
        writer.writeBoolean(true); // has display name
        writer.writeString("{\"text\":\"Alex\"}"); // display name（JSON）
        int id = PacketIds.id(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE, 761).orElseThrow();
        assertEquals(0x36, id);
        int state = connection.handleServerPacket(new MinecraftPacket(id, writer.bytes()), 2);
        assertEquals(2, state);
        assertEquals(1, listener.playerAdds.size());
        assertEquals(uuid, listener.playerAdds.get(0)[0]);
        assertEquals("Alex", listener.playerAdds.get(0)[1]);
        assertEquals(0, listener.playerAdds.get(0)[2]);
    }
}
