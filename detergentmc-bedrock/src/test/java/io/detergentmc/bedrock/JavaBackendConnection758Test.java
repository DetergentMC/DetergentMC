package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bedrock → 内部 758（Java 1.18.2）サーバーのテスト。
 * ソケットを使わない：{@link JavaBackendConnection} を作るだけで
 * {@code connect()} は呼ばず、パッケージ・プライベートの
 * {@code handleServerPacket} にパケットを直接渡す。
 * 758 は設定フェーズなし・Join Game の dimension が NBT・Chat は 0x0f・
 * Player Info は旧式（action 付き、crypto 無し）・Respawn の次元は NBT。
 * 送信側（Login Start／Chat／Command のペイロード）はソケット無しでは
 * 検証できないため Stage E の実バニラ統合テストに回す。
 */
class JavaBackendConnection758Test {

    private static final UUID SENDER =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    /** 出来事を記録する Listener（抽象メソッドだけ実装し、残りは既定）。 */
    private static final class ListenerEvents implements JavaBackendConnection.Listener {
        final int[] joinedEntityId = {-1};
        final List<String[]> chats = new ArrayList<>();
        final List<Object[]> playerAdds = new ArrayList<>();
        final List<BedrockEntityTranslator.SpawnEntity> spawns = new ArrayList<>();
        final List<int[]> dimensionInfos = new ArrayList<>();

        @Override
        public void onJoined(int javaEntityId) {
            joinedEntityId[0] = javaEntityId;
        }

        @Override
        public void onSpawnPosition(double x, double y, double z) {
        }

        @Override
        public void onChat(String sender, String message) {
            chats.add(new String[]{sender, message});
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
        }

        @Override
        public void onDisconnected(String reason) {
        }

        @Override
        public void onPlayerInfoAdd(UUID uuid, String name, int gamemode) {
            playerAdds.add(new Object[]{uuid, name, gamemode});
        }

        @Override
        public void onSpawnEntity(BedrockEntityTranslator.SpawnEntity spawn) {
            spawns.add(spawn);
        }

        @Override
        public void onDimensionInfo(int minY, int height, List<String> biomeNames) {
            dimensionInfos.add(new int[]{minY, height});
        }
    }

    private static JavaBackendConnection connection(ListenerEvents listener) {
        return new JavaBackendConnection("127.0.0.1", 0, 758,
                "Steve", UUID.randomUUID(), listener);
    }

    private static int packetId(PacketIds.PacketName name) {
        return PacketIds.id(name, 758).orElseThrow();
    }

    /** dimension_type レジストリ（overworld だけ。element は空の compound）。 */
    private static Nbt.Tag.CompoundTag codec() {
        return new Nbt.Tag.CompoundTag(Map.of(
                "minecraft:dimension_type", new Nbt.Tag.CompoundTag(Map.of(
                        "type", new Nbt.Tag.StringTag("minecraft:dimension_type"),
                        "value", new Nbt.Tag.ListTag(Nbt.COMPOUND, List.of(
                                new Nbt.Tag.CompoundTag(Map.of(
                                        "name", new Nbt.Tag.StringTag("minecraft:overworld"),
                                        "id", new Nbt.Tag.IntTag(0),
                                        "element", new Nbt.Tag.CompoundTag(Map.of()))))))),
                "minecraft:worldgen/biome", new Nbt.Tag.CompoundTag(Map.of(
                        "type", new Nbt.Tag.StringTag("minecraft:worldgen/biome"),
                        "value", new Nbt.Tag.ListTag(Nbt.COMPOUND, List.of())))));
    }

    /** 758 の Join Game ペイロード（parseJoinGame758 のレイアウト）。 */
    private static byte[] joinGame758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(42);
        writer.writeBoolean(false); // hardcore
        writer.writeByte(0); // gamemode
        writer.writeByte(-1); // previousGamemode
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", codec());
        writer.writeNamedNbt("", new Nbt.Tag.CompoundTag(Map.of())); // dimension（NBT）
        writer.writeString("minecraft:overworld"); // worldName
        writer.writeLong(12345L); // hashedSeed
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebug
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        return writer.bytes();
    }

    @Test
    void loginSuccess758GoesStraightToPlay() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        int next = connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.LOGIN_SUCCESS), new byte[0]), 0);
        assertEquals(2, next, "758 には設定フェーズが無い");
    }

    @Test
    void joinGame758FiresJoined() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_JOIN_GAME),
                        joinGame758Payload()), 2);
        assertEquals(42, listener.joinedEntityId[0]);
        assertEquals(1, listener.dimensionInfos.size());
        assertEquals(-64, listener.dimensionInfos.get(0)[0]);
        assertEquals(384, listener.dimensionInfos.get(0)[1]);
    }

    /** 758 の Player Info add_player ペイロード（crypto フィールド無し）。 */
    private static byte[] playerInfo758AddPayload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(0); // action：add_player
        writer.writeVarInt(1); // count
        writer.writeUuid(SENDER);
        writer.writeString("Steve");
        writer.writeVarInt(0); // properties（空）
        writer.writeVarInt(1); // gamemode
        writer.writeVarInt(50); // ping
        writer.writeBoolean(false); // displayName 無し
        // 758 にはこの後の crypto（任意）が無い。
        return writer.bytes();
    }

    @Test
    void playerInfo758AddPlayerHasNoCrypto() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE),
                        playerInfo758AddPayload()), 2);
        assertEquals(1, listener.playerAdds.size());
        assertEquals(SENDER, listener.playerAdds.get(0)[0]);
        assertEquals("Steve", listener.playerAdds.get(0)[1]);
        assertEquals(1, listener.playerAdds.get(0)[2]);
    }

    /** 758 の Chat ペイロード（0x0f）{message JSON, position i8, sender UUID}。 */
    private static byte[] chat758Payload(UUID sender) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("{\"text\":\"hi\"}");
        writer.writeByte(0); // position：chat
        writer.writeUuid(sender);
        return writer.bytes();
    }

    @Test
    void chat758ResolvesSenderName() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE),
                        playerInfo758AddPayload()), 2);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_CHAT),
                        chat758Payload(SENDER)), 2);
        assertEquals(1, listener.chats.size());
        assertEquals("Steve", listener.chats.get(0)[0]);
        assertEquals("hi", listener.chats.get(0)[1]);
    }

    @Test
    void chat758UnknownSenderHasEmptyName() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_CHAT),
                        chat758Payload(UUID.randomUUID())), 2);
        assertEquals(1, listener.chats.size());
        assertEquals("", listener.chats.get(0)[0]);
        assertEquals("hi", listener.chats.get(0)[1]);
    }

    @Test
    void extractOldChat758ReadsMessagePositionAndSender() {
        JavaBackendConnection.PlayerChat chat =
                JavaBackendConnection.extractOldChat758(chat758Payload(SENDER));
        assertNotNull(chat);
        assertEquals(SENDER, chat.senderUuid());
        assertEquals("hi", chat.message());
        assertNull(JavaBackendConnection.extractOldChat758(new byte[]{0x05}));
        assertNull(JavaBackendConnection.extractOldChat758(new byte[0]));
    }

    /** 758 の Respawn ペイロード（先頭が次元 NBT）。 */
    private static byte[] respawn758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeNamedNbt("", new Nbt.Tag.CompoundTag(Map.of())); // dimension（NBT）
        writer.writeString("minecraft:the_nether"); // worldName
        writer.writeLong(12345L); // hashedSeed
        writer.writeByte(0); // gamemode
        writer.writeByte(-1); // previousGamemode
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // copyMetadata
        return writer.bytes();
    }

    @Test
    void respawn758ReadsNbtDimension() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_RESPAWN),
                        respawn758Payload()), 2);
        assertEquals(1, listener.dimensionInfos.size());
        assertEquals(-64, listener.dimensionInfos.get(0)[0]);
        assertEquals(384, listener.dimensionInfos.get(0)[1]);
    }

    /** 758 の Spawn Entity ペイロード（headPitch 無し、objectData は i32）。 */
    private static byte[] spawnEntity758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUuid(SENDER);
        writer.writeVarInt(64); // type：758 のブタ
        writer.writeDouble(1.5);
        writer.writeDouble(2.5);
        writer.writeDouble(3.5);
        writer.writeByte(64); // pitch 90°
        writer.writeByte(0); // yaw 0°
        writer.writeInt(5); // objectData（i32）
        writer.writeShort(8000);
        writer.writeShort(0);
        writer.writeShort(-8000);
        return writer.bytes();
    }

    @Test
    void spawnEntity758HasNoHeadPitch() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_SPAWN_ENTITY),
                        spawnEntity758Payload()), 2);
        assertEquals(1, listener.spawns.size());
        BedrockEntityTranslator.SpawnEntity spawn = listener.spawns.get(0);
        assertEquals(7, spawn.entityId());
        assertEquals(73, spawn.type765());
        assertEquals(90.0f, spawn.pitch());
        assertEquals(0.0f, spawn.yaw());
        assertEquals(0.0f, spawn.headPitch());
        assertEquals(5, spawn.objectData());
    }

    /** 758 の Spawn Entity Living ペイロード（yaw が pitch より先）。 */
    private static byte[] spawnEntityLiving758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(8); // entityId
        writer.writeUuid(SENDER);
        writer.writeVarInt(64); // type：758 のブタ
        writer.writeDouble(1.5);
        writer.writeDouble(2.5);
        writer.writeDouble(3.5);
        writer.writeByte(64); // yaw 90°
        writer.writeByte(0); // pitch 0°
        writer.writeByte(32); // headPitch 45°
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    @Test
    void spawnEntityLiving758YawBeforePitch() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_SPAWN_ENTITY_LIVING),
                        spawnEntityLiving758Payload()), 2);
        assertEquals(1, listener.spawns.size());
        BedrockEntityTranslator.SpawnEntity spawn = listener.spawns.get(0);
        assertEquals(8, spawn.entityId());
        assertEquals(73, spawn.type765());
        assertEquals(90.0f, spawn.yaw());
        assertEquals(0.0f, spawn.pitch());
        assertEquals(45.0f, spawn.headPitch());
    }

    /** 758 の Named Entity Spawn ペイロード。 */
    private static byte[] namedEntitySpawn758Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(9); // entityId
        writer.writeUuid(SENDER);
        writer.writeDouble(1.5);
        writer.writeDouble(2.5);
        writer.writeDouble(3.5);
        writer.writeByte(0); // yaw
        writer.writeByte(0); // pitch
        return writer.bytes();
    }

    @Test
    void namedEntitySpawn758IsPlayer() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_NAMED_ENTITY_SPAWN),
                        namedEntitySpawn758Payload()), 2);
        assertEquals(1, listener.spawns.size());
        BedrockEntityTranslator.SpawnEntity spawn = listener.spawns.get(0);
        assertEquals(9, spawn.entityId());
        assertEquals(124, spawn.type765());
    }
}
