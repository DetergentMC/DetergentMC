package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame498;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 477（1.14）クライアント・サーバーのプロキシ統合テスト。
 * 477 のワイヤ形式は 480 と完全同一（Stage A でパケットID・
 * レジストリ・形式の差分0を証明済み）のため 480／485 の経路を共用する。
 * ここでは 477 がプロトコル番号として一級に扱われることと、
 * 480 との相互運用（477⇔480 は付け替えのみ）が通ることを固定する。
 */
class ProxySession477Test {

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

    /** 477／480／485 共通の Join Game ペイロード（seed／respawn screen 無し）。 */
    private static byte[] joinGameOldPayload() {
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

    /** 旧式サーバーの Login Success（{UUID文字列, 名前}）。 */
    private static byte[] loginSuccessOld() {
        Writer writer = new Writer();
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        return writer.bytes();
    }

    @Test
    void handshake477IsAccepted() {
        ProxySession.HandshakeResult result = handshake(477, 2, 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(477, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake476IsRejected() {
        ProxySession.HandshakeResult result = handshake(476, 2, 763);
        assertTrue(result.disconnect());
    }

    @Test
    void join477Against758LoginSuccessUsesStringUuid() {
        ProxySession session = loginTo(477, 758);
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
    void join477Against758JoinGameIsOldForm() {
        ProxySession session = loginTo(477, 758);
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
    void join477Against477JoinGameSurvivesRoundTrip() {
        ProxySession session = loginTo(477, 477);
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessOld()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGameOldPayload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
    }

    @Test
    void join477ClientAgainst480Server() {
        // 477 クライアント → 内部 480。形式は同一のため素通しに近い往復。
        ProxySession session = loginTo(477, 480);
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessOld()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGameOldPayload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
    }

    @Test
    void join480ClientAgainst477Server() {
        // 480 クライアント → 内部 477。形式は同一のため素通しに近い往復。
        ProxySession session = loginTo(480, 477);
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessOld()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGameOldPayload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame498 parsed = PlayTranslator.parseJoinGame498(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
    }

    /** クライアントの掘削（旧式 Block Dig：status・position・face）。ack の照合用に記録される。 */
    private static void dig(ProxySession session) {
        Writer dig = new Writer();
        dig.writeVarInt(0); // status（started digging）
        dig.writePositionRaw(0L); // location
        dig.writeByte(0); // face
        session.handleClientPacket(new MinecraftPacket(0x1a, dig.bytes()));
    }

    @Test
    void acknowledgePlayerDiggingIsDroppedFor477Client() {
        // 758 サーバー発の掘削 ack（758 では 0x08）は、先にクライアントが
        // 掘削して記録がある場合にだけ旧式クライアントへ合成される。
        // その合成先は 477 にも 480 にも存在しないため両方に届かず、
        // 498 クライアントにだけ 0x5c で届く.
        Writer ack = new Writer();
        ack.writePositionRaw(0L); // location
        ack.writeVarInt(1); // block state（stone）
        ack.writeVarInt(0); // status
        ack.writeBoolean(true); // successful

        ProxySession session477 = loginTo(477, 758);
        session477.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session477.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        dig(session477);
        Outcome dropped = session477.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(0, dropped.toClient().size(),
                "477 に無いパケットはクライアントへ送らない");

        ProxySession session480 = loginTo(480, 758);
        session480.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session480.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        dig(session480);
        Outcome dropped480 = session480.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(0, dropped480.toClient().size(),
                "480 にも無いパケットはクライアントへ送らない");

        ProxySession session498 = loginTo(498, 758);
        session498.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session498.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        dig(session498);
        Outcome delivered = session498.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(1, delivered.toClient().size());
        assertEquals(0x5c, delivered.toClient().get(0).id());
    }

    @Test
    void chat477To758ServerStaysLegacyChat() {
        ProxySession session = loginTo(477, 758);
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
    void chat758To477ClientDropsSenderUuid() {
        ProxySession session = loginTo(477, 758);
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
