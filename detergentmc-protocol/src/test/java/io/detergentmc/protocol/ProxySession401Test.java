package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator.JoinGame404;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 401（1.13.1）クライアント・サーバーのプロキシ統合テスト。
 * 401 は 404 とパケットID・レジストリが同一で、違うのはスロットの
 * 符号化だけ。404 経路を共用しつつ境界でスロットだけ付け替えることを
 * 固定する（参加フロー、Join Game、スロットを含むパケットの往復）。
 */
class ProxySession401Test {

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

    /** 404／401 の Join Game ペイロード（形式は同一）。 */
    private static byte[] joinGame404Payload() {
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

    /** 旧式サーバーの Login Success（{UUID文字列, 名前}）。401 もこの形。 */
    private static byte[] loginSuccessOld() {
        Writer writer = new Writer();
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        return writer.bytes();
    }

    @Test
    void handshake401IsAccepted() {
        ProxySession.HandshakeResult result = handshake(401, 2, 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(401, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake400And402AreRejected() {
        assertTrue(handshake(400, 2, 763).disconnect());
        assertTrue(handshake(402, 2, 763).disconnect());
        assertTrue(handshake(403, 2, 763).disconnect());
    }

    @Test
    void join401Against758LoginSuccessUsesStringUuid() {
        ProxySession session = loginTo(401, 758);
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
    void join401Against758JoinGameIs404Form() {
        ProxySession session = loginTo(401, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame758Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame404 parsed = PlayTranslator.parseJoinGame404(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(2, parsed.difficulty());
    }

    @Test
    void join401Against401JoinGameSurvives() {
        ProxySession session = loginTo(401, 401);
        Outcome login = session.handleServerPacket(
                new MinecraftPacket(0x02, loginSuccessOld()));
        assertEquals(1, login.toClient().size());
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGame404Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame404 parsed = PlayTranslator.parseJoinGame404(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(20, parsed.maxPlayers());
    }

    @Test
    void join404ClientAgainst401Server() {
        // 内部 401 の Join Game が 404 クライアントにも届くこと
        // （サーバー側 401 の配線確認）。
        ProxySession session = loginTo(404, 401);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x25, joinGame404Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x25, join.toClient().get(0).id());
        JoinGame404 parsed = PlayTranslator.parseJoinGame404(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
    }

    @Test
    void setSlotFrom404ServerBecomesOldSlotFor401Client() {
        // サーバー 404 の Set Slot（現行スロット形式）が、401 クライアント
        // 向けに旧形式（itemId: i16）へ付け替わること。
        ProxySession session = loginTo(401, 404);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x25, joinGame404Payload()));
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeShort(5);
        writer.writeBoolean(true);
        writer.writeVarInt(276);
        writer.writeByte(1);
        writer.writeByte(0);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x17, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x17, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(0, reader.readByte());
        assertEquals(5, reader.readShort());
        assertEquals(276, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(0, reader.remaining() - 1); // NBT 無しマーカー 0x00
    }

    @Test
    void windowItemsFrom401ServerBecomesOldSlotFor401Client() {
        // 内部 401 の Window Items（旧形式）は 401 クライアントへ素通し相当
        // （404形式へ直してから旧形式へ戻すため内容が保存されること）。
        ProxySession session = loginTo(401, 401);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x25, joinGame404Payload()));
        Writer writer = new Writer();
        writer.writeUnsignedByte(0);
        writer.writeShort(2);
        writer.writeShort(276); // 旧形式スロット
        writer.writeByte(3);
        writer.writeByte(0);
        writer.writeShort(-1); // 空
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x15, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x15, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(2, reader.readShort());
        assertEquals(276, reader.readShort());
        assertEquals(3, reader.readUnsignedByte());
        reader.readNamedNbt();
        assertEquals(-1, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void creativeSlotFrom401ClientBecomesNewSlotFor404Server() {
        // 401 クライアントの Set Creative Slot（旧形式）が、404 サーバー
        // 向けに現行形式（present bool＋varint）へ付け替わること。
        ProxySession session = loginTo(401, 404);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x25, joinGame404Payload()));
        Writer writer = new Writer();
        writer.writeShort(13);
        writer.writeShort(513); // 旧形式スロット
        writer.writeByte(1);
        writer.writeByte(0);
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x24, writer.bytes()));
        assertEquals(1, out.toServer().size());
        assertEquals(0x24, out.toServer().get(0).id());
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(13, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(513, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
    }
}
