package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の759（1.19）⇔760（1.19.1／1.19.2）向け変換テスト。
 * 759と760のレジストリは全カテゴリ同一のため、ブロック状態・アイテム・
 * エンティティ・音・バイオームなどの番号はそのまま通る。唯一のペイロード差は
 * Trade List の件数の型（759 が u8、760 が varint）。件数の後ろ
 * （3スロット×件数＋villagerLevel／experience／isRegularVillager／
 * canRestock）はバイト同一のためそのまま写す。Multi Block Change の bool は
 * 759 が notTrustEdges、760 が suppressLightUpdates で同じ位置の 1バイトの
 * ため変換子は要らない（そのまま写す。意味の違いは未検証のため制限として
 * 文書に残す）。Respawn の gamemode も u8（759）と i8（760）で
 * ワイヤ上は同一のため変換子は要らない。
 */
class ChunkTranslator759Test {

    /** スロット1個（759／760形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0); // NBT 無し
    }

    /** スロットの先頭（個数＋アイテムID）を読む。 */
    private static int[] readSlotHead(Reader reader) {
        assertTrue(reader.readBoolean());
        int itemId = reader.readVarInt();
        int count = reader.readByte();
        assertEquals(0, reader.readByte()); // NBT 無し
        return new int[]{itemId, count};
    }

    /** Trade List ペイロード（759形式：件数は u8）。 */
    private static byte[] tradeList759() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // windowId
        payload.writeUnsignedByte(1); // count（u8）
        writeSlot(payload, 804, 1); // oak_sign（759）
        writeSlot(payload, 1, 2); // stone
        writeSlot(payload, 804, 1); // oak_sign（759）
        payload.writeBoolean(false); // tradeDisabled
        payload.writeInt(1); // numToolUses
        payload.writeInt(2); // maxUses
        payload.writeInt(3); // xp
        payload.writeInt(4); // specialPrice
        payload.writeFloat(0.5f); // priceMultiplier
        payload.writeInt(5); // demand
        payload.writeVarInt(0); // villagerLevel
        payload.writeVarInt(0); // experience
        payload.writeBoolean(false); // isRegularVillager
        payload.writeBoolean(true); // canRestock
        return payload.bytes();
    }

    /** Trade List ペイロード（760形式：件数は varint）。 */
    private static byte[] tradeList760() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // windowId
        payload.writeVarInt(1); // count（varint）
        writeSlot(payload, 804, 1); // oak_sign（760）
        writeSlot(payload, 1, 2); // stone
        writeSlot(payload, 804, 1); // oak_sign（760）
        payload.writeBoolean(false); // tradeDisabled
        payload.writeInt(1); // numToolUses
        payload.writeInt(2); // maxUses
        payload.writeInt(3); // xp
        payload.writeInt(4); // specialPrice
        payload.writeFloat(0.5f); // priceMultiplier
        payload.writeInt(5); // demand
        payload.writeVarInt(0); // villagerLevel
        payload.writeVarInt(0); // experience
        payload.writeBoolean(false); // isRegularVillager
        payload.writeBoolean(true); // canRestock
        return payload.bytes();
    }

    @Test
    void tradeList759To760ConvertsCountToVarInt() {
        byte[] translated = ChunkTranslator.translateTradeList759To760(tradeList759());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt()); // windowId
        assertEquals(1, reader.readVarInt()); // count（varint に直る）
        assertArrayEquals(new int[]{804, 1}, readSlotHead(reader)); // oak_sign（同一）
        assertArrayEquals(new int[]{1, 2}, readSlotHead(reader)); // stone
        assertArrayEquals(new int[]{804, 1}, readSlotHead(reader)); // oak_sign
        assertEquals(false, reader.readBoolean()); // tradeDisabled
        assertEquals(1, reader.readInt());
        assertEquals(2, reader.readInt());
        assertEquals(3, reader.readInt());
        assertEquals(4, reader.readInt());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readVarInt()); // villagerLevel
        assertEquals(0, reader.readVarInt()); // experience
        assertEquals(false, reader.readBoolean()); // isRegularVillager
        assertEquals(true, reader.readBoolean()); // canRestock
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeList760To759ConvertsCountToUnsignedByte() {
        byte[] translated = ChunkTranslator.translateTradeList760To759(tradeList760());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt()); // windowId
        assertEquals(1, reader.readUnsignedByte()); // count（u8 に直る）
        assertArrayEquals(new int[]{804, 1}, readSlotHead(reader)); // oak_sign（同一）
        assertArrayEquals(new int[]{1, 2}, readSlotHead(reader)); // stone
        assertArrayEquals(new int[]{804, 1}, readSlotHead(reader)); // oak_sign
        assertEquals(false, reader.readBoolean()); // tradeDisabled
        assertEquals(1, reader.readInt());
        assertEquals(2, reader.readInt());
        assertEquals(3, reader.readInt());
        assertEquals(4, reader.readInt());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readVarInt()); // villagerLevel
        assertEquals(0, reader.readVarInt()); // experience
        assertEquals(false, reader.readBoolean()); // isRegularVillager
        assertEquals(true, reader.readBoolean()); // canRestock
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeListRoundTripIsIdentity() {
        assertArrayEquals(tradeList759(),
                ChunkTranslator.translateTradeList760To759(
                        ChunkTranslator.translateTradeList759To760(tradeList759())));
        assertArrayEquals(tradeList760(),
                ChunkTranslator.translateTradeList759To760(
                        ChunkTranslator.translateTradeList760To759(tradeList760())));
    }

    @Test
    void tradeListCountOver255PassesThrough() {
        // 760 の件数が 255 を超えると 759 の u8 には入らないため素通しする。
        Writer payload = new Writer();
        payload.writeVarInt(7); // windowId
        payload.writeVarInt(256); // count
        payload.writeBytes(new byte[]{0x01, 0x02});
        byte[] broken = payload.bytes();
        assertArrayEquals(broken, ChunkTranslator.translateTradeList760To759(broken));
    }

    @Test
    void tradeListBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02}; // 短すぎて読めない
        assertArrayEquals(broken, ChunkTranslator.translateTradeList759To760(broken));
        assertArrayEquals(broken, ChunkTranslator.translateTradeList760To759(broken));
    }
}
