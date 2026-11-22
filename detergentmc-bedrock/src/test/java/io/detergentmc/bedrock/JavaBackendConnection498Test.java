package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIds;
import io.detergentmc.protocol.RegistryRemapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bedrock → 内部 498（Java 1.14.4）サーバーのテスト。
 * 498 は 578 と形式が違う箇所がある（Join Game に hashedSeed が無い、
 * Spawn Entity Living に末尾メタデータ）ため、{@link JavaBackendConnection}
 * の 498 分岐を固定する：設定フェーズなし・Join Game の次元は数値 ID・
 * Chat は送信者 UUID 無し・Player Info は旧式（crypto 無し）・
 * モブは Spawn Entity Living（0x03、末尾メタデータ読み飛ばし）。
 */
class JavaBackendConnection498Test {

    private static final UUID SENDER =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

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
        return new JavaBackendConnection("127.0.0.1", 0, 498,
                "Steve", UUID.randomUUID(), listener);
    }

    private static int packetId(PacketIds.PacketName name) {
        return PacketIds.id(name, 498).orElseThrow();
    }

    /** 498 の Join Game ペイロード（hashedSeed が無い）。 */
    private static byte[] joinGame498Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(42);
        writer.writeByte(0); // gamemode
        writer.writeInt(0); // dimension overworld
        writer.writeByte(20); // maxPlayers（u8）
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void loginSuccess498GoesStraightToPlay() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        int next = connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.LOGIN_SUCCESS), new byte[0]), 0);
        assertEquals(2, next, "498 には設定フェーズが無い");
    }

    @Test
    void joinGame498FiresJoinedWithHeight256() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_JOIN_GAME),
                        joinGame498Payload()), 2);
        assertEquals(42, listener.joinedEntityId[0]);
        assertEquals(1, listener.dimensionInfos.size());
        assertEquals(0, listener.dimensionInfos.get(0)[0]); // minY
        assertEquals(256, listener.dimensionInfos.get(0)[1]); // height
    }

    @Test
    void chat498HasNoSenderUuid() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("{\"text\":\"hello\"}");
        writer.writeByte(0); // position（送信者 UUID は無い）
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_CHAT),
                        writer.bytes()), 2);
        assertEquals(1, listener.chats.size());
        assertEquals("hello", listener.chats.get(0)[1]);
    }

    @Test
    void playerInfo498AddPlayerHasNoCrypto() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(0); // add_player
        writer.writeVarInt(1);
        writer.writeUuid(SENDER);
        writer.writeString("Steve");
        writer.writeVarInt(0); // properties
        writer.writeVarInt(1); // gamemode
        writer.writeVarInt(50); // ping
        writer.writeBoolean(false); // displayName 無し
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE),
                        writer.bytes()), 2);
        assertEquals(1, listener.playerAdds.size());
        assertEquals("Steve", listener.playerAdds.get(0)[1]);
    }

    @Test
    void spawnLiving498SkipsTrailingMetadata() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(9);
        writer.writeUuid(SENDER);
        writer.writeVarInt(54); // pig（498）
        writer.writeDouble(1.0);
        writer.writeDouble(64.0);
        writer.writeDouble(3.0);
        writer.writeByte(10); // yaw
        writer.writeByte(20); // pitch
        writer.writeByte(30); // headPitch
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeByte(0xFF); // 末尾メタデータ（空）
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_SPAWN_ENTITY_LIVING),
                        writer.bytes()), 2);
        assertEquals(1, listener.spawns.size());
        assertEquals(9, listener.spawns.get(0).entityId());
    }

    @Test
    void normalize498EntityAndItem() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(54, 498)); // pig → 765 pig
        assertEquals(BedrockEntityTranslator.normalizeEntityType(
                        RegistryRemapper.mapEntityType498To758(54), 758),
                BedrockEntityTranslator.normalizeEntityType(54, 498));
        assertEquals(BedrockEntityTranslator.normalizeItem(
                        RegistryRemapper.mapItem498To758(753), 758),
                BedrockEntityTranslator.normalizeItem(753, 498)); // zombie_pigman_spawn_egg
        assertTrue(BedrockEntityTranslator.normalizeItem(1, 498) > 0);
    }

    @Test
    void extractOldChat498Directly() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("{\"text\":\"abc\"}");
        writer.writeByte(2);
        JavaBackendConnection.PlayerChat chat =
                JavaBackendConnection.extractOldChat578(writer.bytes());
        assertNotNull(chat);
        assertEquals("abc", chat.message());
        assertEquals(new UUID(0L, 0L), chat.senderUuid());
    }
}
