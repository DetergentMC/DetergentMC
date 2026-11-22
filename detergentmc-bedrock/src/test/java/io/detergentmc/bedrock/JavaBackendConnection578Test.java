package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bedrock → 内部 578（Java 1.15.2）サーバーのテスト。
 * {@link JavaBackendConnection758Test} と同じ段取り（ソケット無しで
 * {@code handleServerPacket} に直接渡す）。578 は設定フェーズなし・
 * Join Game の次元は数値 ID・Chat は送信者 UUID 無し・Player Info は
 * 旧式（crypto 無し）・モブは Spawn Entity Living（0x03）。
 */
class JavaBackendConnection578Test {

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
        return new JavaBackendConnection("127.0.0.1", 0, 578,
                "Steve", UUID.randomUUID(), listener);
    }

    private static int packetId(PacketIds.PacketName name) {
        return PacketIds.id(name, 578).orElseThrow();
    }

    /** 578 の Join Game ペイロード。 */
    private static byte[] joinGame578Payload() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(42);
        writer.writeByte(0); // gamemode
        writer.writeInt(0); // dimension overworld
        writer.writeLong(12345L);
        writer.writeByte(20); // maxPlayers（u8）
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    @Test
    void loginSuccess578GoesStraightToPlay() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        int next = connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.LOGIN_SUCCESS), new byte[0]), 0);
        assertEquals(2, next, "578 には設定フェーズが無い");
    }

    @Test
    void joinGame578FiresJoinedWithHeight256() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_JOIN_GAME),
                        joinGame578Payload()), 2);
        assertEquals(42, listener.joinedEntityId[0]);
        assertEquals(1, listener.dimensionInfos.size());
        assertEquals(0, listener.dimensionInfos.get(0)[0]); // minY
        assertEquals(256, listener.dimensionInfos.get(0)[1]); // height
    }

    @Test
    void chat578HasNoSenderUuid() throws Exception {
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
    void playerInfo578AddPlayerHasNoCrypto() throws Exception {
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
    void spawnLiving578ZombiePigmanBecomesZombifiedPiglin() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(99);
        writer.writeUuid(UUID.randomUUID());
        writer.writeVarInt(57); // zombie_pigman（578）
        writer.writeDouble(0.5);
        writer.writeDouble(64.0);
        writer.writeDouble(0.5);
        writer.writeByte(0); // yaw
        writer.writeByte(0); // pitch
        writer.writeByte(0); // headPitch
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_SPAWN_ENTITY_LIVING),
                        writer.bytes()), 2);
        assertEquals(1, listener.spawns.size());
        // 765 の zombified_piglin は 758 経路の正規化と一致するはず。
        assertEquals(BedrockEntityTranslator.normalizeEntityType(110, 758),
                listener.spawns.get(0).type765());
        assertNotNull(listener.spawns.get(0));
    }

    @Test
    void normalize578EntityAndItem() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(55, 578)); // pig → 765 pig
        assertEquals(BedrockEntityTranslator.normalizeEntityType(110, 758),
                BedrockEntityTranslator.normalizeEntityType(57, 578));
        assertEquals(BedrockEntityTranslator.normalizeItem(939, 758),
                BedrockEntityTranslator.normalizeItem(754, 578)); // spawn egg
        assertTrue(BedrockEntityTranslator.normalizeItem(1, 578) > 0);
    }

    @Test
    void extractOldChat578Directly() {
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
