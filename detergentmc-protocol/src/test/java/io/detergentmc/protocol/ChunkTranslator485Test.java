package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * 485（1.14.2）の Trade List 変換。490／758 との唯一の形式差：
 * 485 の各取引には末尾の demand (i32) が無く（1.14.4 で追加）、
 * 末尾の canRestock (bool) も無い（1.14.3 で追加）。
 * 758 形式との間で demand と canRestock を落とす／補うことを
 * 固定する。canRestock の補完値は ViaVersion の 1.14.2→1.14.3
 * 変換と同じく isRegularVillager の値を使う。
 * チャンク等の他の形式は 490 と同一のため 490 用変換を共用する
 * （ChunkTranslator490Test が担う）。
 */
class ChunkTranslator485Test {

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
        writer.writeInt(demand); // demand（485 には無い）
        writer.writeVarInt(3); // villagerLevel
        writer.writeVarInt(11); // experience
        writer.writeBoolean(true); // isRegularVillager
        writer.writeBoolean(true); // canRestock（485 には無い）
        return writer.bytes();
    }

    /** 485 形式の Trade List（demand も canRestock も無し）。 */
    private static byte[] tradeList485() {
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
        writer.writeBoolean(true); // isRegularVillager のみ
        return writer.bytes();
    }

    @Test
    void tradeList758To485DropsDemandAndCanRestock() {
        byte[] out = ChunkTranslator.translateTradeList758To485(tradeList758(7));
        assertArrayEquals(tradeList485(), out);
    }

    @Test
    void tradeList485To758AddsZeroDemandAndCanRestockFromRegularVillager() {
        byte[] out = ChunkTranslator.translateTradeList485To758(tradeList485());
        // demand=0 が補われ、canRestock は isRegularVillager（true）で補われる。
        assertArrayEquals(tradeList758(0), out);
    }

    @Test
    void tradeListRoundTrip485To758To485() {
        byte[] as758 = ChunkTranslator.translateTradeList485To758(tradeList485());
        byte[] back = ChunkTranslator.translateTradeList758To485(as758);
        assertArrayEquals(tradeList485(), back);
    }

    /** 490 形式の Trade List（demand 無し・canRestock 有り）。 */
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
        writer.writeBoolean(true); // isRegularVillager
        writer.writeBoolean(true); // canRestock
        return writer.bytes();
    }

    @Test
    void tradeList485DiffersFrom490FormByDemandAndCanRestock() {
        // 490 形式は demand を落とすが canRestock は保持する。
        // 485 形式は canRestock も落とす（490 形式より1バイト短い）。
        byte[] as490 = ChunkTranslator.translateTradeList758To490(tradeList758(7));
        assertArrayEquals(tradeList490(), as490);
        byte[] as485 = ChunkTranslator.translateTradeList758To485(tradeList758(7));
        assertEquals(as490.length - 1, as485.length);
        Reader reader = new Reader(as485);
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
