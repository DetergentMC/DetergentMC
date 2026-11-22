package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Bedrock → 内部 107（Java 1.9）サーバーのテスト。
 * 107 は 108 とレジストリ同一だが、Join Game の dimension だけが
 * i8（108 以降は i32）という唯一の形式差分がある。340 系の i32 読み
 * （parseJoinGame340）に流すと 17 バイトの Join Game が壊れて次元が
 * 既定のオーバーワールドに落ちるため、専用の parseJoinGame107 で
 * 読むことを固定する。あわせて、設定フェーズなし・Chat は送信者
 * UUID 無しの 107 列挙入りも固定する。
 */
class JavaBackendConnection107Test {

    private static final class ListenerEvents implements JavaBackendConnection.Listener {
        final int[] joinedEntityId = {-1};
        final List<String[]> chats = new ArrayList<>();
        final List<int[]> dimensionInfos = new ArrayList<>();
        final List<List<String>> biomeNameSets = new ArrayList<>();

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
        public void onDimensionInfo(int minY, int height, List<String> biomeNames) {
            dimensionInfos.add(new int[]{minY, height});
            biomeNameSets.add(biomeNames);
        }
    }

    private static JavaBackendConnection connection(ListenerEvents listener) {
        return new JavaBackendConnection("127.0.0.1", 0, 107,
                "Steve", UUID.randomUUID(), listener);
    }

    private static int packetId(PacketIds.PacketName name) {
        return PacketIds.id(name, 107).orElseThrow();
    }

    /** 107 の Join Game ペイロード（dimension は符号付きバイト）。 */
    private static byte[] joinGame107Payload(int dimension) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(42);
        writer.writeByte(0); // gamemode
        writer.writeByte(dimension); // dimension（107 は i8）
        writer.writeByte(1); // difficulty
        writer.writeByte(20); // maxPlayers（u8）
        writer.writeString("default");
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void loginSuccess107GoesStraightToPlay() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        int next = connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.LOGIN_SUCCESS), new byte[0]), 0);
        assertEquals(2, next, "107 には設定フェーズが無い");
    }

    @Test
    void joinGame107ReadsDimensionAsSignedByte() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        byte[] payload = joinGame107Payload(0);
        assertEquals(17, payload.length, "107 の Join Game は 17 バイト（dimension i8）");
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_JOIN_GAME),
                        payload), 2);
        assertEquals(42, listener.joinedEntityId[0]);
        assertEquals(1, listener.dimensionInfos.size(),
                "i8 読みで次元が確定し、次元情報が届く（i32 読みでは例外で落ちる）");
        assertEquals(0, listener.dimensionInfos.get(0)[0]); // minY
        assertEquals(256, listener.dimensionInfos.get(0)[1]); // height
        assertEquals(RegistryRemapper.biomeNames340(), listener.biomeNameSets.get(0));
    }

    @Test
    void joinGame107ReadsNetherDimensionByte() throws Exception {
        ListenerEvents listener = new ListenerEvents();
        JavaBackendConnection connection = connection(listener);
        connection.handleServerPacket(
                new MinecraftPacket(packetId(PacketIds.PacketName.PLAY_JOIN_GAME),
                        joinGame107Payload(-1)), 2);
        assertEquals(42, listener.joinedEntityId[0]);
        assertEquals(1, listener.dimensionInfos.size(),
                "dimension -1（ネザー）の i8 も読み切れる");
        assertTrue(listener.dimensionInfos.get(0)[1] == 256);
    }

    @Test
    void chat107HasNoSenderUuid() throws Exception {
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
}
