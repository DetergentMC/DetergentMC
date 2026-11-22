package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame340;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 340（1.12.2）クライアント・サーバーのプロキシ統合テスト。
 * 340 はブロックに状態が無く、スロットに itemDamage を持ち、
 * パケットIDも 393 と95件違うため、変換経路が専用であることを
 * 固定する（参加フロー、Join Game、魚の Damage 付け替え）。
 */
class ProxySession340Test {

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

    /** 340／393／401／404 の Join Game ペイロード（形式は同一）。 */
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

    /** 旧式サーバーの Login Success（{UUID文字列, 名前}）。340 もこの形。 */
    private static byte[] loginSuccessOld() {
        Writer writer = new Writer();
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        return writer.bytes();
    }

    @Test
    void handshake340IsAccepted() {
        ProxySession.HandshakeResult result = handshake(340, 2, 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(340, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake339And341AreRejected() {
        assertTrue(handshake(339, 2, 763).disconnect());
        assertTrue(handshake(341, 2, 763).disconnect());
        assertTrue(handshake(392, 2, 763).disconnect());
    }

    @Test
    void join340Against758LoginSuccessUsesStringUuid() {
        ProxySession session = loginTo(340, 758);
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
    void join340Against758JoinGameIs340Form() {
        ProxySession session = loginTo(340, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame758Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id()); // 340 の Join Game
        JoinGame340 parsed = PlayTranslator.parseJoinGame340(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(2, parsed.difficulty());
    }

    @Test
    void join340Against340JoinGameSurvives() {
        ProxySession session = loginTo(340, 340);
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessOld()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGameOldPayload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id());
        JoinGame340 parsed = PlayTranslator.parseJoinGame340(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(20, parsed.maxPlayers());
    }

    @Test
    void join404ClientAgainst340Server() {
        // 内部 340 の Join Game が 404 クライアントにも届くこと
        // （サーバー側 340 の配線確認：routeServerPlay340 経由）。
        ProxySession session = loginTo(404, 340);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x23, joinGameOldPayload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id()); // 404 の Join Game
        JoinGame340 parsed = PlayTranslator.parseJoinGame340(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
    }

    @Test
    void setSlotSalmonFrom758ServerBecomesFishWithDamageFor340Client() {
        // サーバー 758 の Set Slot（salmon 802）が、340 クライアント向けに
        // fish(349)＋itemDamage 1 の 340 ワイヤ形式へ付け替わること。
        // 340 専用経路（意味対応＋Damage 規約＋SlotTranslator）でなければ
        // 起きない変換。
        ProxySession session = loginTo(340, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeVarInt(0); // stateId（758 にはあるが 340 には無い）
        writer.writeShort(5); // slot
        writer.writeBoolean(true);
        writer.writeVarInt(802); // salmon（758）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT 無し
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x16, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x16, out.toClient().get(0).id()); // 340 の Set Slot
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(0, reader.readByte());
        assertEquals(5, reader.readShort());
        assertEquals(349, reader.readShort()); // fish（340 ワイヤ形式 i16）
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(1, reader.readShort()); // itemDamage = 1（salmon）
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void creativeSlotFrom340ClientBecomesNewSlotFor404Server() {
        // 340 クライアントの Set Creative Slot（340 ワイヤ形式、ダイヤ剣
        // 276）が、404 サーバー向けに現行形式＋404 の番号（493）で届くこと。
        ProxySession session = loginTo(340, 404);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x25, joinGameOldPayload()));
        Writer writer = new Writer();
        writer.writeShort(13); // slot
        writer.writeShort(276); // ダイヤ剣（340 ワイヤ形式）
        writer.writeByte(1);
        writer.writeShort(0); // damage
        writer.writeByte(0); // NBT 無し
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x1B, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x24, out.toServer().get(0).id()); // 404 の Set Creative Slot
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(13, reader.readShort());
        assertTrue(reader.readBoolean()); // 現行形式（present）
        assertEquals(493, reader.readVarInt()); // 404 のダイヤ剣
        assertEquals(1, reader.readUnsignedByte());
    }
}
