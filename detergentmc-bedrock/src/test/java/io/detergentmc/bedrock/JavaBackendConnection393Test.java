package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bedrock → 内部 393（Java 1.13）サーバーのテスト。
 * 393 は 477 と形式が違う（Join Game に difficulty があり viewDistance が
 * 無い、Spawn Entity はオブジェクト表の i8 種別）ため、393 専用分岐を
 * 通ることを固定する：設定フェーズなし・Join Game の次元は数値 ID・
 * Chat は送信者 UUID 無し・Player Info は旧式（crypto 無し）・
 * モブは Spawn Entity Living（0x03、末尾メタデータ読み飛ばし、
 * 種別の正規化は 393 の番号で行う parseSpawnEntityLiving393）。
 */
class JavaBackendConnection393Test {

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
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
        }

        @Override
        public void onDisconnected(String reason) {
        }

        @Override
        public void onChat(String sender, String message) {
            chats.add(new String[]{sender, message});
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
        return new JavaBackendConnection("127.0.0.1", 0, 393,
                "Steve", UUID.randomUUID(), listener);
    }

    private static int packetId(PacketIds.PacketName name) {
        return PacketIds.id(name, 393).orElseThrow();
    }

    /** 393 の Join Game ペイロード（difficulty あり・viewDistance 無し）。 */
    private static byte[] joinGame393Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(42);
        writer.writeByte(0); // gamemode
        writer.writeInt(0); // dimension overworld
        writer.writeByte(1); // difficulty
        writer.writeByte(20); // maxPlayers（u8）
        writer.writeString("default");
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void loginSuccess393GoesStraightToPlay() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        int next = connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.LOGIN_SUCCESS), new byte[0]), 0);
        assertEquals(2, next, "393 には設定フェーズが無い");
    }

    @Test
    void joinGame393FiresJoinedWithHeight256() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_JOIN_GAME),
                        joinGame393Payload()), 2);
        assertEquals(42, listener.joinedEntityId[0]);
        assertEquals(1, listener.dimensionInfos.size());
        assertEquals(0, listener.dimensionInfos.get(0)[0]); // minY
        assertEquals(256, listener.dimensionInfos.get(0)[1]); // height
    }

    @Test
    void chat393HasNoSenderUuid() throws Exception {
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
    void playerInfo393AddPlayerHasNoCrypto() throws Exception {
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
    void spawnLiving393SkipsTrailingMetadata() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(9);
        writer.writeUuid(SENDER);
        writer.writeVarInt(51); // pig（393。pig の種別 ID は 51）
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
    void spawnEntity393UsesObjectTable() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(12);
        writer.writeUuid(SENDER);
        writer.writeByte(1); // オブジェクト表の boat
        writer.writeDouble(1.0);
        writer.writeDouble(64.0);
        writer.writeDouble(3.0);
        writer.writeByte(0); // pitch
        writer.writeByte(0); // yaw
        writer.writeInt(0); // data
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_SPAWN_ENTITY),
                        writer.bytes()), 2);
        assertEquals(1, listener.spawns.size());
        assertEquals(12, listener.spawns.get(0).entityId());
    }

    @Test
    void normalize393EntityAndItem() {
        // pig：393 の 51 → 765 の pig（73）。
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(51, 393));
        assertTrue(BedrockEntityTranslator.normalizeItem(1, 393) > 0);
        // アイテム 447 は 393 と 404 で別物（404 は dead_tube_coral、
        // 393 では dead coral 自体が無い）。393 専用表で引くため、
        // 404 と同じ番号でも正規化結果が異なる。
        assertTrue(BedrockEntityTranslator.normalizeItem(447, 393)
                != BedrockEntityTranslator.normalizeItem(447, 404));
        // tube_coral は両方とも 438 で同じアイテム。
        assertEquals(BedrockEntityTranslator.normalizeItem(438, 404),
                BedrockEntityTranslator.normalizeItem(438, 393));
    }
}
