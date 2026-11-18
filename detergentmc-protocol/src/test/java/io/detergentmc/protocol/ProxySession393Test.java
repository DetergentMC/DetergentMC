package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame393;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 393（1.13）クライアント・サーバーのプロキシ統合テスト。
 * 393 は 401 とパケットIDが同一でスロットも旧形式だが、レジストリが
 * 404 と違う（dead coral 5種が無い）ため変換経路は専用であることを
 * 固定する（参加フロー、Join Game、サンゴのアイテム番号の付け替え）。
 */
class ProxySession393Test {

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

    /** 393／401／404 の Join Game ペイロード（形式は同一）。 */
    private static byte[] joinGameOldPayload() {
        Writer writer = new Writer();
        writer.writeInt(42);
        writer.writeByte(0);
        writer.writeInt(0);
        writer.writeByte(1);
        writer.writeByte(20);
        writer.writeString("flat");
        writer.writeBoolean(false);
        return writer.bytes();
    }

    /** 旧式サーバーの Login Success（{UUID文字列, 名前}）。393 もこの形。 */
    private static byte[] loginSuccessOld() {
        Writer writer = new Writer();
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        return writer.bytes();
    }

    @Test
    void handshake393IsAccepted() {
        ProxySession.HandshakeResult result = handshake(393, 2, 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(393, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake392And394AreRejected() {
        assertTrue(handshake(392, 2, 763).disconnect());
        assertTrue(handshake(394, 2, 763).disconnect());
        assertTrue(handshake(400, 2, 763).disconnect());
    }

    @Test
    void join393Against758LoginSuccessUsesStringUuid() {
        ProxySession session = loginTo(393, 758);
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
    void join393Against758JoinGameIs393Form() {
        ProxySession session = loginTo(393, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame758Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame393 parsed = PlayTranslator.parseJoinGame393(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(2, parsed.difficulty());
    }

    @Test
    void join393Against393JoinGameSurvives() {
        ProxySession session = loginTo(393, 393);
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessOld()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGameOldPayload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame393 parsed = PlayTranslator.parseJoinGame393(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(20, parsed.maxPlayers());
    }

    @Test
    void join404ClientAgainst393Server() {
        // 内部 393 の Join Game が 404 クライアントにも届くこと
        // （サーバー側 393 の配線確認：routeServerPlay393 経由）。
        ProxySession session = loginTo(404, 393);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGameOldPayload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame393 parsed = PlayTranslator.parseJoinGame393(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
    }

    @Test
    void setSlotDeadCoralFrom404ServerBecomesTubeCoralFor393Client() {
        // サーバー 404 の Set Slot（現行スロット形式、アイテム 447 =
        // dead_tube_coral）が、393 クライアント向けに旧形式スロット＋
        // 生きた tube_coral の 393 番号（438）へ付け替わること。
        // 393 専用表でなければ起きない変換（401 経路では 447 のまま）。
        ProxySession session = loginTo(393, 404);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x25, joinGameOldPayload()));
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeShort(5);
        writer.writeBoolean(true);
        writer.writeVarInt(447); // 404 の dead_tube_coral
        writer.writeByte(1);
        writer.writeByte(0);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x17, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x17, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(0, reader.readByte());
        assertEquals(5, reader.readShort());
        assertEquals(438, reader.readShort()); // 393 の tube_coral（旧形式 i16）
        assertEquals(1, reader.readUnsignedByte());
    }

    @Test
    void setSlotFrom393ServerReaches404ClientWith404ItemNumber() {
        // 内部 393 の Set Slot（旧形式スロット、アイテム 447）が
        // 404 クライアントへ現行形式＋404 の番号（452）で届くこと。
        // 393 サーバー側の経路（スロット事前付け替え→routeServerPlay393→
        // 758→404 チェーン）を通るため、393 専用表の番号付け替えが
        // ここで効く（447 は 393 と 404 で別のアイテムを指す）。
        ProxySession session = loginTo(404, 393);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x25, joinGameOldPayload()));
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeShort(5);
        writer.writeShort(447); // 旧形式スロット（393 サーバー発、393 の番号）
        writer.writeByte(1);
        writer.writeByte(0);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x17, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x17, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(0, reader.readByte());
        assertEquals(5, reader.readShort());
        assertTrue(reader.readBoolean()); // 現行形式（present）
        assertEquals(452, reader.readVarInt()); // 404 の番号
        assertEquals(1, reader.readUnsignedByte());
    }

    @Test
    void creativeSlotFrom393ClientBecomesNewSlotFor404Server() {
        // 393 クライアントの Set Creative Slot（旧形式）が、404 サーバー
        // 向けに現行形式（present bool＋varint）へ付け替わること。
        // アイテム 276 はサンゴの挿入位置より前で 393・404 同一番号。
        ProxySession session = loginTo(393, 404);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x25, joinGameOldPayload()));
        Writer writer = new Writer();
        writer.writeShort(13);
        writer.writeShort(276); // 旧形式スロット
        writer.writeByte(1);
        writer.writeByte(0);
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x24, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x24, out.toServer().get(0).id());
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(13, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(276, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
    }
}
