package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame498;
import io.detergentmc.protocol.PlayTranslator.JoinGame578;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 498（1.14.4）クライアント・サーバーのプロキシ統合テスト。
 * 498 は 573 と形式が違う（Join Game に seed／respawn screen が無い等）
 * ため、専用変換チェーンを通る。ここでは 498 がプロトコル番号として
 * 一級に扱われること（受け入れ・ネイティブ判定・Login Success の
 * 文字列 UUID 形・Join Game・チャット）を固定する。
 */
class ProxySession498Test {

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

    /** 498 の Join Game ペイロード（seed／respawn screen 無し）。 */
    private static byte[] joinGame498Payload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeByte(20);
        writer.writeString("default");
        writer.writeVarInt(10);
        writer.writeBoolean(false);
        return writer.bytes();
    }

    @Test
    void handshake498IsAccepted() {
        ProxySession.HandshakeResult result = handshake(498, 2, 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(498, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake497IsRejected() {
        ProxySession.HandshakeResult result = handshake(497, 2, 763);
        assertTrue(result.disconnect());
    }

    @Test
    void join498Against758LoginSuccessUsesStringUuid() {
        ProxySession session = loginTo(498, 758);
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
    void join498Against758JoinGameIs498Form() {
        ProxySession session = loginTo(498, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame758Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
    }

    @Test
    void join498Against498JoinGameSurvivesRoundTrip() {
        ProxySession session = loginTo(498, 498);
        // 498 サーバーの Login Success は {UUID文字列, 名前}。
        Writer success = new Writer();
        success.writeString(PLAYER_UUID.toString());
        success.writeString("Steve");
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, success.bytes()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGame498Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
    }

    @Test
    void join573ClientAgainst498Server() {
        // 573 クライアント → 内部 498：498→758 で seed=0 を合成してから
        // 573 形式へ落ちる。
        ProxySession session = loginTo(573, 498);
        Writer success = new Writer();
        success.writeString(PLAYER_UUID.toString());
        success.writeString("Steve");
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, success.bytes()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGame498Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x26, join.toClient().get(0).id());
        JoinGame578 parsed = PlayTranslator.parseJoinGame578(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(0L, parsed.hashedSeed());
    }

    @Test
    void chat498To758ServerStaysLegacyChat() {
        ProxySession session = loginTo(498, 758);
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
    void chat758To498ClientDropsSenderUuid() {
        ProxySession session = loginTo(498, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        Writer writer = new Writer();
        writer.writeString("{\"text\":\"hi\"}");
        writer.writeByte(0);
        writer.writeUuid(PLAYER_UUID);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x0f, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x0e, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals("{\"text\":\"hi\"}", reader.readString());
        assertEquals(1, reader.readByte());
        assertEquals(0, reader.remaining());
    }
}
