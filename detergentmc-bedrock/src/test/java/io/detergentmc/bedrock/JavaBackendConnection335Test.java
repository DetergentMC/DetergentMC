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
 * Bedrock → 内部 335（Java 1.12）サーバーのテスト。
 * 335 は Flattening 前の世代（Join Game の形式は 393 と同一、Spawn
 * Entity Living の種別はモブ ID（pig=90）、アイテムは全面振り直し）
 * のため、335 専用分岐を通ることを固定する：設定フェーズなし・
 * Join Game の次元は数値 ID・Chat は送信者 UUID 無し・Player Info は
 * 旧式（crypto 無し）・モブは Spawn Entity Living（種別の正規化は
 * 335 の番号で行う parseSpawnEntityLiving335（340 のパースへ委譲）。498 には流さない）。
 */
class JavaBackendConnection335Test {

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
        return new JavaBackendConnection("127.0.0.1", 0, 335,
                "Steve", UUID.randomUUID(), listener);
    }

    private static int packetId(PacketIds.PacketName name) {
        return PacketIds.id(name, 335).orElseThrow();
    }

    /** 335 の Join Game ペイロード（形式は 393 と同一）。 */
    private static byte[] joinGame335Payload() {
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
    void loginSuccess335GoesStraightToPlay() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        int next = connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.LOGIN_SUCCESS), new byte[0]), 0);
        assertEquals(2, next, "335 には設定フェーズが無い");
    }

    @Test
    void joinGame335FiresJoinedWithHeight256() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_JOIN_GAME),
                        joinGame335Payload()), 2);
        assertEquals(42, listener.joinedEntityId[0]);
        assertEquals(1, listener.dimensionInfos.size());
        assertEquals(0, listener.dimensionInfos.get(0)[0]); // minY
        assertEquals(256, listener.dimensionInfos.get(0)[1]); // height
    }

    @Test
    void chat335HasNoSenderUuid() throws Exception {
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
    void playerInfo335AddPlayerHasNoCrypto() throws Exception {
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
    void spawnLiving335UsesMobId90ForPig() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(9);
        writer.writeUuid(SENDER);
        writer.writeVarInt(90); // pig（335 のモブ ID。393 の 51 ではない）
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
    void spawnEntity335UsesObjectTable() throws Exception {
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
    void normalize335EntityAndItem() {
        // pig：335 のモブ ID 90 → 765 の pig（73）。
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(90, 335));
        // ダイヤ剣：335 の 276 は 758 の 719 と同じアイテムに正規化される。
        assertEquals(BedrockEntityTranslator.normalizeItem(719, 758),
                BedrockEntityTranslator.normalizeItem(276, 335));
        assertTrue(BedrockEntityTranslator.normalizeItem(276, 335) > 0);
    }
}
