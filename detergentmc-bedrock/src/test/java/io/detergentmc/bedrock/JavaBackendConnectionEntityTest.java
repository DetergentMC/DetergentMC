package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Java バックエンドのエンティティ配線テスト。偽 Java サーバーが 765 の
 * エンティティパケットを送り、Listener へ正しく届くことを確認する。
 * 偽サーバーは本物のバニラと同じ順番でパケットを送る。
 */
class JavaBackendConnectionEntityTest {

    /** 偽 Java サーバー（サーバー役）。送られてきたパケットを記録する。 */
    private static final class FakeJavaServer implements AutoCloseable {
        final ServerSocket serverSocket;
        final Socket socket;
        final InputStream in;
        final OutputStream out;
        final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        final MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();

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
                } catch (IOException ignored) {
                    // 閉じるときは無視する。
                }
            }, "fake-java-server-reader");
            reader.setDaemon(true);
            reader.start();
        }

        void send(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                MinecraftPacket packet = received.poll(50, TimeUnit.MILLISECONDS);
                if (packet != null && packet.id() == id) {
                    return packet;
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            socket.close();
            serverSocket.close();
        }
    }

    /** エンティティの出来事をためる Listener。 */
    private static final class EntityEvents implements JavaBackendConnection.Listener {
        final CountDownLatch joined = new CountDownLatch(1);
        final int[] entityId = new int[1];
        final List<BedrockEntityTranslator.SpawnEntity> spawns = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.SpawnExperienceOrb> orbs = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.JavaMetadata> metadatas = new CopyOnWriteArrayList<>();
        final List<List<Integer>> removes = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.EntityVelocity> velocities = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.EntityTeleport> teleports = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.EntityMove> moves = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.EntityMoveLook> moveLooks = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.EntityLook> looks = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.EntityHeadRotation> heads = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.EntityEquipment> equipments = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.EntityEffect> effects = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.RemoveEntityEffect> removeEffects =
                new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.UpdateAttributes> attributes = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.SetPassengers> passengers = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.AttachEntity> attaches = new CopyOnWriteArrayList<>();
        final List<Object[]> playerInfoAdds = new CopyOnWriteArrayList<>();
        final List<Object[]> playerInfoGamemodes = new CopyOnWriteArrayList<>();
        final List<List<UUID>> playerInfoRemoves = new CopyOnWriteArrayList<>();

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
        }

        @Override
        public void onSpawnEntity(BedrockEntityTranslator.SpawnEntity spawn) {
            spawns.add(spawn);
        }

        @Override
        public void onSpawnExperienceOrb(BedrockEntityTranslator.SpawnExperienceOrb orb) {
            orbs.add(orb);
        }

        @Override
        public void onEntityMetadata(BedrockEntityTranslator.JavaMetadata metadata) {
            metadatas.add(metadata);
        }

        @Override
        public void onRemoveEntities(List<Integer> entityIds) {
            removes.add(entityIds);
        }

        @Override
        public void onEntityVelocity(BedrockEntityTranslator.EntityVelocity velocity) {
            velocities.add(velocity);
        }

        @Override
        public void onEntityTeleport(BedrockEntityTranslator.EntityTeleport teleport) {
            teleports.add(teleport);
        }

        @Override
        public void onEntityMove(BedrockEntityTranslator.EntityMove move) {
            moves.add(move);
        }

        @Override
        public void onEntityMoveLook(BedrockEntityTranslator.EntityMoveLook move) {
            moveLooks.add(move);
        }

        @Override
        public void onEntityLook(BedrockEntityTranslator.EntityLook look) {
            looks.add(look);
        }

        @Override
        public void onEntityHeadRotation(BedrockEntityTranslator.EntityHeadRotation rotation) {
            heads.add(rotation);
        }

        @Override
        public void onEntityEquipment(BedrockEntityTranslator.EntityEquipment equipment) {
            equipments.add(equipment);
        }

        @Override
        public void onEntityEffect(BedrockEntityTranslator.EntityEffect effect) {
            effects.add(effect);
        }

        @Override
        public void onRemoveEntityEffect(BedrockEntityTranslator.RemoveEntityEffect effect) {
            removeEffects.add(effect);
        }

        @Override
        public void onUpdateAttributes(BedrockEntityTranslator.UpdateAttributes attrs) {
            attributes.add(attrs);
        }

        @Override
        public void onSetPassengers(BedrockEntityTranslator.SetPassengers p) {
            passengers.add(p);
        }

        @Override
        public void onAttachEntity(BedrockEntityTranslator.AttachEntity attach) {
            attaches.add(attach);
        }

        @Override
        public void onPlayerInfoAdd(UUID uuid, String name, int gamemode) {
            playerInfoAdds.add(new Object[]{uuid, name, gamemode});
        }

        @Override
        public void onPlayerInfoGamemode(UUID uuid, int gamemode) {
            playerInfoGamemodes.add(new Object[]{uuid, gamemode});
        }

        @Override
        public void onPlayerInfoRemove(List<UUID> uuids) {
            playerInfoRemoves.add(uuids);
        }
    }

    private static void acceptInBackground(ServerSocket serverSocket,
            AtomicReference<FakeJavaServer> holder, AtomicReference<Exception> error) {
        Thread thread = new Thread(() -> {
            try {
                holder.set(new FakeJavaServer(serverSocket));
            } catch (IOException e) {
                error.set(e);
            }
        }, "fake-java-server-accept");
        thread.setDaemon(true);
        thread.start();
    }

    private static byte[] spawnEntityPayload(int entityId, UUID uuid, int type,
            double x, double y, double z) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(entityId);
        writer.writeUuid(uuid);
        writer.writeVarInt(type);
        writer.writeDouble(x);
        writer.writeDouble(y);
        writer.writeDouble(z);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    @Test
    void entityPacketsReachListener765() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            EntityEvents events = new EntityEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 765, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = null;
                for (int i = 0; i < 100 && server == null; i++) {
                    Thread.sleep(50);
                    server = holder.get();
                }
                assertNotNull(server, "偽サーバーが受け入れませんでした: " + error.get());
                try (FakeJavaServer fake = server) {
                    fake.waitForId(0x00, 5_000); // ハンドシェイク
                    fake.send(new MinecraftPacket(0x02,
                            new PacketIO.Writer().writeString(".Steve")
                                    .writeUuid(uuid).bytes()));
                    assertNotNull(fake.waitForId(0x03, 5_000), "Login Acknowledged が届かない");
                    assertNotNull(fake.waitForId(0x00, 5_000), "Client Information が届かない");
                    fake.send(new MinecraftPacket(0x02, new byte[0])); // Finish Configuration
                    assertNotNull(fake.waitForId(0x02, 5_000), "Finish Configuration の承認が届かない");

                    // Join Game（0x29）：エンティティ ID 7（自分）。
                    PacketIO.Writer joinGame = new PacketIO.Writer();
                    joinGame.writeInt(7);
                    fake.send(new MinecraftPacket(0x29, joinGame.bytes()));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS), "Join Game が届かない");
                    assertEquals(7, events.entityId[0]);

                    UUID alex = UUID.fromString("33333333-4444-5555-6666-777777777777");
                    // Player Info Update（0x3c）：add player（Alex、生存）。
                    PacketIO.Writer infoUpdate = new PacketIO.Writer();
                    infoUpdate.writeVarInt(1);
                    infoUpdate.writeVarInt(1);
                    infoUpdate.writeUuid(alex);
                    infoUpdate.writeString("Alex");
                    infoUpdate.writeVarInt(0);
                    infoUpdate.writeVarInt(0);
                    infoUpdate.writeVarInt(10);
                    infoUpdate.writeBoolean(false);
                    fake.send(new MinecraftPacket(0x3c, infoUpdate.bytes()));
                    Thread.sleep(200);
                    assertEquals(1, events.playerInfoAdds.size());
                    assertEquals(alex, events.playerInfoAdds.get(0)[0]);
                    assertEquals("Alex", events.playerInfoAdds.get(0)[1]);
                    assertEquals(0, events.playerInfoAdds.get(0)[2]);

                    // Player Info Update（0x3c）：gamemode 更新（0x04、クリエイティブ）。
                    PacketIO.Writer gamemode = new PacketIO.Writer();
                    gamemode.writeVarInt(0x04);
                    gamemode.writeVarInt(1);
                    gamemode.writeUuid(alex);
                    gamemode.writeVarInt(1);
                    fake.send(new MinecraftPacket(0x3c, gamemode.bytes()));
                    Thread.sleep(200);
                    assertEquals(1, events.playerInfoGamemodes.size());
                    assertEquals(alex, events.playerInfoGamemodes.get(0)[0]);
                    assertEquals(1, events.playerInfoGamemodes.get(0)[1]);

                    // Spawn Entity（0x01）：ゾンビ（120）と他プレイヤー（124）。
                    fake.send(new MinecraftPacket(0x01,
                            spawnEntityPayload(101, UUID.randomUUID(), 120, 10.5, 64.0, -3.25)));
                    fake.send(new MinecraftPacket(0x01,
                            spawnEntityPayload(42, alex, 124, 8.5, 64.0, 8.5)));
                    Thread.sleep(300);
                    assertEquals(2, events.spawns.size(), "Spawn Entity が 2 件届くこと");
                    assertEquals(120, events.spawns.get(0).type765());
                    assertEquals(10.5, events.spawns.get(0).x(), 0.001);
                    assertEquals(124, events.spawns.get(1).type765());
                    assertEquals(alex, events.spawns.get(1).uuid());

                    // Spawn Experience Orb（0x02）。
                    PacketIO.Writer orb = new PacketIO.Writer();
                    orb.writeVarInt(106);
                    orb.writeDouble(5.0);
                    orb.writeDouble(64.0);
                    orb.writeDouble(5.0);
                    orb.writeShort(7);
                    fake.send(new MinecraftPacket(0x02, orb.bytes()));

                    // Entity Metadata（0x56）：ゾンビの体力 20・赤ちゃん。
                    PacketIO.Writer metadata = new PacketIO.Writer();
                    metadata.writeVarInt(101);
                    metadata.writeByte(9);
                    metadata.writeVarInt(3);
                    metadata.writeFloat(20.0f);
                    metadata.writeByte(16);
                    metadata.writeVarInt(8);
                    metadata.writeBoolean(true);
                    metadata.writeByte(0xFF);
                    fake.send(new MinecraftPacket(0x56, metadata.bytes()));

                    // Entity Velocity（0x58）。
                    PacketIO.Writer velocity = new PacketIO.Writer();
                    velocity.writeVarInt(101);
                    velocity.writeShort(8000);
                    velocity.writeShort(0);
                    velocity.writeShort(0);
                    fake.send(new MinecraftPacket(0x58, velocity.bytes()));

                    // Relative Entity Move（0x2c）。
                    PacketIO.Writer move = new PacketIO.Writer();
                    move.writeVarInt(101);
                    move.writeShort(4096);
                    move.writeShort(0);
                    move.writeShort(0);
                    move.writeBoolean(true);
                    fake.send(new MinecraftPacket(0x2c, move.bytes()));

                    // Entity Move Look（0x2d）。
                    PacketIO.Writer moveLook = new PacketIO.Writer();
                    moveLook.writeVarInt(101);
                    moveLook.writeShort(0);
                    moveLook.writeShort(0);
                    moveLook.writeShort(0);
                    moveLook.writeByte(64);
                    moveLook.writeByte(0);
                    moveLook.writeBoolean(true);
                    fake.send(new MinecraftPacket(0x2d, moveLook.bytes()));

                    // Entity Look（0x2e）。
                    PacketIO.Writer look = new PacketIO.Writer();
                    look.writeVarInt(101);
                    look.writeByte(64);
                    look.writeByte(0);
                    look.writeBoolean(true);
                    fake.send(new MinecraftPacket(0x2e, look.bytes()));

                    // Entity Head Rotation（0x46）。
                    PacketIO.Writer head = new PacketIO.Writer();
                    head.writeVarInt(101);
                    head.writeByte(128);
                    fake.send(new MinecraftPacket(0x46, head.bytes()));

                    // Entity Teleport（0x6d）。
                    PacketIO.Writer teleport = new PacketIO.Writer();
                    teleport.writeVarInt(101);
                    teleport.writeDouble(20.0);
                    teleport.writeDouble(65.0);
                    teleport.writeDouble(-5.0);
                    teleport.writeByte(0);
                    teleport.writeByte(0);
                    teleport.writeBoolean(false);
                    fake.send(new MinecraftPacket(0x6d, teleport.bytes()));

                    // Entity Equipment（0x59）：メインハンドにダイヤの剣。
                    PacketIO.Writer equipment = new PacketIO.Writer();
                    equipment.writeVarInt(101);
                    equipment.writeByte(0);
                    equipment.writeBoolean(true);
                    equipment.writeVarInt(834);
                    equipment.writeByte(1);
                    equipment.writeBytes(
                            Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of())));
                    fake.send(new MinecraftPacket(0x59, equipment.bytes()));

                    // Entity Effect（0x72）：速度。
                    PacketIO.Writer effect = new PacketIO.Writer();
                    effect.writeVarInt(101);
                    effect.writeVarInt(0);
                    effect.writeByte(1);
                    effect.writeVarInt(200);
                    effect.writeByte(0x02);
                    effect.writeBoolean(false);
                    fake.send(new MinecraftPacket(0x72, effect.bytes()));

                    // Remove Entity Effect（0x41）。
                    PacketIO.Writer removeEffect = new PacketIO.Writer();
                    removeEffect.writeVarInt(101);
                    removeEffect.writeVarInt(0);
                    fake.send(new MinecraftPacket(0x41, removeEffect.bytes()));

                    // Entity Update Attributes（0x71）：最大体力 30。
                    PacketIO.Writer attributes = new PacketIO.Writer();
                    attributes.writeVarInt(101);
                    attributes.writeVarInt(1);
                    attributes.writeString("minecraft:generic.max_health");
                    attributes.writeDouble(30.0);
                    attributes.writeVarInt(0);
                    fake.send(new MinecraftPacket(0x71, attributes.bytes()));

                    // Set Passengers（0x5d）：200 に 201 が乗る。
                    PacketIO.Writer passengers = new PacketIO.Writer();
                    passengers.writeVarInt(200);
                    passengers.writeVarInt(1);
                    passengers.writeVarInt(201);
                    fake.send(new MinecraftPacket(0x5d, passengers.bytes()));

                    // Attach Entity（0x57）：201 が 200 にリードで繋がる。
                    PacketIO.Writer attach = new PacketIO.Writer();
                    attach.writeInt(201);
                    attach.writeInt(200);
                    fake.send(new MinecraftPacket(0x57, attach.bytes()));

                    // Remove Entities（0x40）：101 と 42 を消す。
                    PacketIO.Writer remove = new PacketIO.Writer();
                    remove.writeVarInt(2);
                    remove.writeVarInt(101);
                    remove.writeVarInt(42);
                    fake.send(new MinecraftPacket(0x40, remove.bytes()));

                    // Player Info Remove（0x3b）：Alex を消す。
                    PacketIO.Writer infoRemove = new PacketIO.Writer();
                    infoRemove.writeVarInt(1);
                    infoRemove.writeUuid(alex);
                    fake.send(new MinecraftPacket(0x3b, infoRemove.bytes()));

                    Thread.sleep(500);
                    assertEquals(1, events.orbs.size());
                    assertEquals(7, events.orbs.get(0).count());
                    assertEquals(1, events.metadatas.size());
                    assertEquals(101, events.metadatas.get(0).entityId());
                    assertEquals(20.0f, events.metadatas.get(0).health(), 0.001f);
                    assertTrue(events.metadatas.get(0).baby());
                    assertEquals(1, events.velocities.size());
                    assertEquals(1.0f, events.velocities.get(0).velX(), 0.001f);
                    assertEquals(1, events.moves.size());
                    assertEquals(1.0, events.moves.get(0).dx(), 0.001);
                    assertEquals(1, events.moveLooks.size());
                    assertEquals(90.0f, events.moveLooks.get(0).yaw(), 0.001f);
                    assertEquals(1, events.looks.size());
                    assertEquals(1, events.heads.size());
                    assertEquals(180.0f, events.heads.get(0).headYaw(), 0.001f);
                    assertEquals(1, events.teleports.size());
                    assertEquals(20.0, events.teleports.get(0).x(), 0.001);
                    assertEquals(1, events.equipments.size());
                    assertEquals(834, events.equipments.get(0).entries().get(0).item().item765());
                    assertEquals(1, events.effects.size());
                    assertEquals(0, events.effects.get(0).effect765());
                    assertEquals(1, events.removeEffects.size());
                    assertEquals(0, events.removeEffects.get(0).effect765());
                    assertEquals(1, events.attributes.size());
                    assertEquals(30.0, events.attributes.get(0).maxHealth(), 0.001);
                    assertEquals(1, events.passengers.size());
                    assertEquals(200, events.passengers.get(0).vehicleId());
                    assertEquals(List.of(201), events.passengers.get(0).passengerIds());
                    assertEquals(1, events.attaches.size());
                    assertEquals(201, events.attaches.get(0).entityId());
                    assertEquals(200, events.attaches.get(0).vehicleId());
                    assertEquals(1, events.removes.size());
                    assertEquals(List.of(101, 42), events.removes.get(0));
                    assertEquals(1, events.playerInfoRemoves.size());
                    assertEquals(List.of(alex), events.playerInfoRemoves.get(0));

                    // Bedrock→Java：Use Entity（攻撃）と Entity Action（しゃがみ）。
                    backend.sendUseEntity(101, 1, 0);
                    MinecraftPacket useEntity = fake.waitForId(0x13, 5_000);
                    assertNotNull(useEntity, "Use Entity（0x13）が届きませんでした。");
                    PacketIO.Reader useReader = new PacketIO.Reader(useEntity.payload());
                    assertEquals(101, useReader.readVarInt());
                    assertEquals(1, useReader.readVarInt());

                    backend.sendEntityAction(0);
                    MinecraftPacket entityAction = fake.waitForId(0x22, 5_000);
                    assertNotNull(entityAction, "Entity Action（0x22）が届きませんでした。");
                    PacketIO.Reader actionReader = new PacketIO.Reader(entityAction.payload());
                    assertEquals(7, actionReader.readVarInt()); // 自分のエンティティ ID
                    assertEquals(0, actionReader.readVarInt()); // しゃがみ開始
                    assertEquals(0, actionReader.readVarInt()); // jump boost
                }
            } finally {
                backend.close();
            }
        }
    }
}
