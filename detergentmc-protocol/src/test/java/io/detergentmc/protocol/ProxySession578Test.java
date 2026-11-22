package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame578;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 578（1.15.2）クライアント・サーバーのプロキシ統合テスト。
 * 578 は 758 の1段下として配線される：クライアントのパケットは
 * 578→758→759→…→763 形式へ登り、サーバーからは段階を下って最後に
 * 758→578 変換を受ける。内部サーバーが 578 の場合はサーバーの
 * パケットも 578→758 と登ってから処理される。
 */
class ProxySession578Test {

    private static final UUID PLAYER_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static ProxySession.HandshakeResult handshake(
            int clientProtocol, int nextState, int serverProtocol) {
        Writer writer = new Writer();
        writer.writeVarInt(clientProtocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(25565);
        writer.writeVarInt(nextState);
        return ProxySession.resolveHandshake(writer.bytes(), false,
                null, null, ProxyAuthConfig.defaults(), null, serverProtocol);
    }

    private static ProxySession loginTo(int clientProtocol, int serverProtocol) {
        ProxySession.HandshakeResult result = handshake(clientProtocol, 2, serverProtocol);
        assertFalse(result.disconnect());
        ProxySession session = result.session();
        Writer writer = new Writer();
        writer.writeString("Steve");
        session.handleClientPacket(new MinecraftPacket(0x00, writer.bytes()));
        return session;
    }

    /** 758 形式の Login Success（UUID＋名前だけ。実バニラ 1.18.2 には properties 自体が無い）。 */
    private static byte[] loginSuccess758() {
        Writer writer = new Writer();
        writer.writeUuid(PLAYER_UUID);
        writer.writeString("Steve");
        return writer.bytes();
    }

    private static Tag.CompoundTag codec() {
        return new Tag.CompoundTag(Map.of(
                "minecraft:dimension_type", new Tag.CompoundTag(Map.of(
                        "type", new Tag.StringTag("minecraft:dimension_type"),
                        "value", new Tag.ListTag(Nbt.COMPOUND, List.of(
                                new Tag.CompoundTag(Map.of(
                                        "name", new Tag.StringTag("minecraft:overworld"),
                                        "id", new Tag.IntTag(0),
                                        "element", new Tag.CompoundTag(Map.of()))))))),
                "minecraft:worldgen/biome", new Tag.CompoundTag(Map.of(
                        "type", new Tag.StringTag("minecraft:worldgen/biome"),
                        "value", new Tag.ListTag(Nbt.COMPOUND, List.of())))));
    }

    /** 758 の Join Game ペイロード。 */
    private static byte[] joinGame758Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeBoolean(false);
        writer.writeByte(0);
        writer.writeByte(-1);
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeNamedNbt("", codec());
        writer.writeNamedNbt("", new Tag.CompoundTag(Map.of()));
        writer.writeString("minecraft:overworld");
        writer.writeLong(12345L);
        writer.writeVarInt(20);
        writer.writeVarInt(10);
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 578 の Join Game ペイロード。 */
    private static byte[] joinGame578Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeLong(12345L);
        writer.writeByte(20);
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    @Test
    void handshake578IsAccepted() {
        ProxySession.HandshakeResult result = handshake(578, 2, 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(578, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake577IsRejected() {
        ProxySession.HandshakeResult result = handshake(577, 2, 763);
        assertTrue(result.disconnect());
    }

    @Test
    void join578Against758LoginSuccessUsesStringUuid() {
        ProxySession session = loginTo(578, 758);
        Outcome success = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccess758()));
        assertEquals(1, success.toClient().size());
        assertEquals(0x02, success.toClient().get(0).id());
        Reader reader = new Reader(success.toClient().get(0).payload());
        assertEquals(PLAYER_UUID.toString(), reader.readString());
        assertEquals("Steve", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void join578Against758JoinGameIs578Form() {
        ProxySession session = loginTo(578, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame758Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        JoinGame578 parsed = PlayTranslator.parseJoinGame578(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(12345L, parsed.hashedSeed());
    }

    @Test
    void join578Against578JoinGameSurvivesRoundTrip() {
        ProxySession session = loginTo(578, 578);
        // 578 サーバーの Login Success は {UUID文字列, 名前}。
        Writer success = new Writer();
        success.writeString(PLAYER_UUID.toString());
        success.writeString("Steve");
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, success.bytes()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame578Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        JoinGame578 parsed = PlayTranslator.parseJoinGame578(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
    }

    @Test
    void chat578To758ServerStaysLegacyChat() {
        ProxySession session = loginTo(578, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        Writer writer = new Writer();
        writer.writeString("hello");
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x03, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x03, out.toServer().get(0).id()); // 758 の Chat Message
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals("hello", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void chat758To578ClientDropsSenderUuid() {
        ProxySession session = loginTo(578, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"hi\"}");
        writer.writeByte(0);
        writer.writeUuid(PLAYER_UUID);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x0f, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x0f, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}", reader.readString());
        // 位置は旧式チャットが System Chat 経由で中継されるため 1（system）に
        // なる（758 クライアントでも同じ既存チェーンの挙動）。578 固有の
        // 変換は末尾の送信者 UUID を落とすこと。
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.remaining());
    }
}
