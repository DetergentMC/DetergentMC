package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * 490（1.14.3）の Trade List 変換。498／758 との唯一の形式差：
 * 490 の各取引には末尾の demand (i32) が無い（1.14.4 で追加された）。
 * 758 形式との間で demand を落とす／0 で補うことを固定する。
 * チャンク等の他の形式は 498 と同一のため 498 用変換を共用する
 * （ChunkTranslator498Test が担う）。
 */
class ChunkTranslator490Test {

    /** 758 形式の Trade List（取引1件・空スロットのみ・demand=7）。 */
    private static byte[] tradeList758(int demand) {
        Writer writer = new Writer();
        writer.writeVarInt(3); // windowId
        writer.writeUnsignedByte(1); // count
        writer.writeBoolean(false); // inputItem1（空）
        writer.writeBoolean(false); // outputItem（空）
        writer.writeBoolean(false); // inputItem2 無し
        writer.writeBoolean(false); // tradeDisabled
        writer.writeInt(2); // nbTradeUses
        writer.writeInt(10); // maximumNbTradeUses
        writer.writeInt(5); // xp
        writer.writeInt(0); // specialPrice
        writer.writeFloat(0.05f); // priceMultiplier
        writer.writeInt(demand); // demand（490 には無い）
        writer.writeVarInt(3); // villagerLevel
        writer.writeVarInt(11); // experience
        writer.writeBoolean(true); // isRegularVillager
        writer.writeBoolean(true); // canRestock
        return writer.bytes();
    }

    /** 490 形式の Trade List（demand 無し）。 */
    private static byte[] tradeList490() {
        Writer writer = new Writer();
        writer.writeVarInt(3);
        writer.writeUnsignedByte(1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeInt(2);
        writer.writeInt(10);
        writer.writeInt(5);
        writer.writeInt(0);
        writer.writeFloat(0.05f);
        writer.writeVarInt(3);
        writer.writeVarInt(11);
        writer.writeBoolean(true);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    @Test
    void tradeList758To490DropsDemand() {
        byte[] out = ChunkTranslator.translateTradeList758To490(tradeList758(7));
        assertArrayEquals(tradeList490(), out);
    }

    @Test
    void tradeList490To758AddsZeroDemand() {
        byte[] out = ChunkTranslator.translateTradeList490To758(tradeList490());
        assertArrayEquals(tradeList758(0), out);
    }

    @Test
    void tradeListRoundTrip490To758To490() {
        byte[] as758 = ChunkTranslator.translateTradeList490To758(tradeList490());
        byte[] back = ChunkTranslator.translateTradeList758To490(as758);
        assertArrayEquals(tradeList490(), back);
    }

    @Test
    void tradeList490DiffersFrom498FormByDemandOnly() {
        // 498 形式は demand を保持する。同じ入力を 498 変換すると
        // demand=7 のまま、490 変換すると落ちる。
        byte[] as498 = ChunkTranslator.translateTradeList758To498(tradeList758(7));
        assertArrayEquals(tradeList758(7), as498);
        byte[] as490 = ChunkTranslator.translateTradeList758To490(tradeList758(7));
        assertEquals(as498.length - 4, as490.length);
        Reader reader = new Reader(as490);
        reader.readVarInt();
        reader.readUnsignedByte();
        reader.readBoolean();
        reader.readBoolean();
        reader.readBoolean();
        reader.readBoolean();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readFloat();
        // demand の位置には次の villagerLevel が来る。
        assertEquals(3, reader.readVarInt());
    }
}
