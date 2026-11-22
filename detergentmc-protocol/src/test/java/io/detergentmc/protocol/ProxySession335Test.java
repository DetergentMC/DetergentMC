package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PacketIds.PacketName;
import io.detergentmc.protocol.PlayTranslator.JoinGame340;
import io.detergentmc.protocol.ProxySession.Outcome;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 335（1.12）クライアント・サーバーのプロキシ統合テスト。
 * 335 はワイヤ形式が 338／340 と完全同一で変換経路は 340 と共用するが、
 * パケットIDが 338 と 53 件ずれる（レシピブック再編）。ID の解釈・生成が
 * 起きる境界で名前経由の付け替えが挟まること、Keep Alive（335 も
 * varint）の付け替えと切り捨て復元が 335 専用の復元表で効くこと、
 * 335 のみに存在する Prepare Crafting Grid が落とされることを固定する。
 */
class ProxySession335Test {

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

    /** 335／338／340／393／401／404 の Join Game ペイロード（形式は同一）。 */
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

    /** 旧式サーバーの Login Success（{UUID文字列, 名前}）。335 もこの形。 */
    private static byte[] loginSuccessOld() {
        Writer writer = new Writer();
        writer.writeString(PLAYER_UUID.toString());
        writer.writeString("Steve");
        return writer.bytes();
    }

    /** 758 サーバーへ入り、Join Game まで進めた 335 セッション。 */
    private static ProxySession joined335Against758() {
        ProxySession session = loginTo(335, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        return session;
    }

    @Test
    void handshake335IsAccepted() {
        ProxySession.HandshakeResult result = handshake(335, 2, 763);
        assertFalse(result.disconnect());
        assertNotNull(result.session());
        assertEquals(335, result.session().clientProtocol());
        assertEquals(763, result.session().serverProtocol());
    }

    @Test
    void handshake334And336AreRejected() {
        assertTrue(handshake(334, 2, 763).disconnect());
        assertTrue(handshake(336, 2, 763).disconnect());
        assertTrue(handshake(337, 2, 763).disconnect());
        assertTrue(handshake(392, 2, 763).disconnect());
    }

    @Test
    void join335Against758LoginSuccessUsesStringUuid() {
        ProxySession session = loginTo(335, 758);
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
    void join335Against758JoinGameIs340Form() {
        ProxySession session = loginTo(335, 758);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        Outcome join = session.handleServerPacket(
                new MinecraftPacket(0x26, joinGame758Payload()));
        assertEquals(1, join.toClient().size());
        assertEquals(0x23, join.toClient().get(0).id()); // 335／338／340 の Join Game
        JoinGame340 parsed = PlayTranslator.parseJoinGame340(join.toClient().get(0).payload());
        assertEquals(42, parsed.entityId());
        assertEquals(0, parsed.dimension());
        assertEquals(2, parsed.difficulty());
    }

    @Test
    void join335Against335JoinGameSurvives() {
        ProxySession session = loginTo(335, 335);
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
    void setSlotSalmonFrom758ServerBecomesFishWithDamageFor335Client() {
        // 340 と経路を共用するため、758 の Set Slot（salmon 802）が
        // 335 クライアント向けにも fish(349)＋itemDamage 1 になること。
        ProxySession session = joined335Against758();
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeVarInt(0); // stateId（758 にはあるが 335 には無い）
        writer.writeShort(5); // slot
        writer.writeBoolean(true);
        writer.writeVarInt(802); // salmon（758）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT 無し
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x16, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x16, out.toClient().get(0).id()); // 335／338／340 の Set Slot
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(0, reader.readByte());
        assertEquals(5, reader.readShort());
        assertEquals(349, reader.readShort()); // fish（335 ワイヤ形式 i16）
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(1, reader.readShort()); // itemDamage = 1（salmon）
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateTimeFrom758ServerUses335Id() {
        // クライアント行き 0x2b 以上の ID は 335 で 1 小さいこと
        // （Update Time：340 では 0x47、335 では 0x46）。
        ProxySession session = joined335Against758();
        Writer writer = new Writer();
        writer.writeLong(123456L); // world age
        writer.writeLong(-6000L); // time of day
        int id758 = PacketIds.id(PacketName.PLAY_UPDATE_TIME, 758).orElseThrow();
        Outcome out = session.handleServerPacket(new MinecraftPacket(id758, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x46, out.toClient().get(0).id()); // 335 の Update Time
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(123456L, reader.readLong());
        assertEquals(-6000L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void keepAliveFrom758ServerBecomesVarIntFor335Client() {
        // 758 サーバーの Keep Alive（i64）が、335 クライアント向けに
        // varint へ付け替わること。
        ProxySession session = joined335Against758();
        Writer writer = new Writer();
        writer.writeLong(123456789L);
        int id758 = PacketIds.id(PacketName.PLAY_KEEP_ALIVE, 758).orElseThrow();
        Outcome out = session.handleServerPacket(new MinecraftPacket(id758, writer.bytes()));
        assertEquals(1, out.toClient().size());
        assertEquals(0x1F, out.toClient().get(0).id()); // 335 の Keep Alive
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(123456789, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void keepAliveResponseFrom335ClientBecomesI64For758Server() {
        // 335 クライアントの Keep Alive 応答（varint、ワイヤID 0x0C＝
        // 338 の 0x0B から +1）が、758 サーバー向けに i64 へ付け替わること。
        ProxySession session = joined335Against758();
        Writer writer = new Writer();
        writer.writeVarInt(123456789);
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x0C, writer.bytes()));
        assertEquals(1, out.toServer().size());
        int id758 = PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, 758).orElseThrow();
        assertEquals(id758, out.toServer().get(0).id());
        Reader reader = new Reader(out.toServer().get(0).payload());
        assertEquals(123456789L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void keepAliveTruncatedIdIsRestoredOnResponse() {
        // int 範囲を超える i64 の Keep Alive ID は、335 向けに下位
        // 32 ビットへ切り捨てて送り、応答では 335 専用の復元表で
        // 元の i64 へ復元されること。
        ProxySession session = joined335Against758();
        long original = 0x1_2345_6789L;
        Writer writer = new Writer();
        writer.writeLong(original);
        int id758 = PacketIds.id(PacketName.PLAY_KEEP_ALIVE, 758).orElseThrow();
        Outcome out = session.handleServerPacket(new MinecraftPacket(id758, writer.bytes()));
        assertEquals(1, out.toClient().size());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals((int) original, reader.readVarInt());
        assertEquals(0, reader.remaining());

        Writer response = new Writer();
        response.writeVarInt((int) original);
        Outcome back = session.handleClientPacket(new MinecraftPacket(0x0C, response.bytes()));
        assertEquals(1, back.toServer().size());
        Reader backReader = new Reader(back.toServer().get(0).payload());
        assertEquals(original, backReader.readLong());
        assertEquals(0, backReader.remaining());
    }

    @Test
    void prepareCraftingGridFrom335ClientIsDropped() {
        // Prepare Crafting Grid（335 のみ、サーバー行き 0x01）は
        // 338 以降に届け先が無いため落とされること。
        ProxySession session = joined335Against758();
        Writer writer = new Writer();
        writer.writeByte(0); // windowId（中身は解釈されない）
        writer.writeShort(0);
        writer.writeByte(0);
        Outcome out = session.handleClientPacket(new MinecraftPacket(0x01, writer.bytes()));
        assertTrue(out.toServer().isEmpty());
        assertTrue(out.toClient().isEmpty());
    }

    @Test
    void craftRecipeResponseNeverReaches335Client() {
        // Craft Recipe Response は 335 に存在しない。340 サーバーから
        // 来ても 335 クライアントには届かないこと（レシピ系は
        // 758→340 段でも落とす方針で、335 の最終段でも落とす）。
        ProxySession session = loginTo(335, 340);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x23, joinGameOldPayload()));
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeVarInt(0); // recipe
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x2B, writer.bytes()));
        assertTrue(out.toClient().isEmpty());
    }

    @Test
    void keepAliveFrom335ServerBecomesI64For763Client() {
        // 内部335サーバーの Keep Alive（varint）が、763 クライアント
        // 向けに i64 のまま届くこと。応答（i64）も 335 サーバー向けに
        // varint へ戻り、ワイヤID は 335 の 0x0C になること。
        ProxySession session = loginTo(763, 335);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x23, joinGameOldPayload()));
        Writer writer = new Writer();
        writer.writeVarInt(987654321);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x1F, writer.bytes()));
        assertEquals(1, out.toClient().size());
        int id763 = PacketIds.id(PacketName.PLAY_KEEP_ALIVE, 763).orElseThrow();
        assertEquals(id763, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(987654321L, reader.readLong());
        assertEquals(0, reader.remaining());

        Writer response = new Writer();
        response.writeLong(987654321L);
        int id763ToServer = PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, 763).orElseThrow();
        Outcome back = session.handleClientPacket(new MinecraftPacket(id763ToServer, response.bytes()));
        assertEquals(1, back.toServer().size());
        assertEquals(0x0C, back.toServer().get(0).id()); // 335 の Keep Alive（サーバー行き）
        Reader backReader = new Reader(back.toServer().get(0).payload());
        assertEquals(987654321, backReader.readVarInt());
        assertEquals(0, backReader.remaining());
    }

    @Test
    void updateTimeFrom335ServerUses763Id() {
        // 内部335サーバーの Update Time（ワイヤID 0x46）が、入口で
        // 340 の番号（0x47）へ付け替わってから上位変換され、
        // 763 クライアントには 763 の ID で届くこと。
        ProxySession session = loginTo(763, 335);
        session.handleServerPacket(new MinecraftPacket(0x02, loginSuccessOld()));
        session.handleServerPacket(new MinecraftPacket(0x23, joinGameOldPayload()));
        Writer writer = new Writer();
        writer.writeLong(2222L);
        writer.writeLong(6000L);
        Outcome out = session.handleServerPacket(new MinecraftPacket(0x46, writer.bytes()));
        assertEquals(1, out.toClient().size());
        int id763 = PacketIds.id(PacketName.PLAY_UPDATE_TIME, 763).orElseThrow();
        assertEquals(id763, out.toClient().get(0).id());
        Reader reader = new Reader(out.toClient().get(0).payload());
        assertEquals(2222L, reader.readLong());
        assertEquals(6000L, reader.readLong());
        assertEquals(0, reader.remaining());
    }
}
