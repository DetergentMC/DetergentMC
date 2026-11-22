package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * 480（1.14.1）の Trade List 変換。Stage A の実データ照合で、480 の
 * Trade List 形式は 485 と完全同一（各取引に demand も canRestock も
 * 無い）と証明済み。そのため 480 専用の変換メソッドは作らず、485 用
 * 変換（{@link ChunkTranslator} の *485 メソッド）がそのまま 480 形式を
 * 出し入れできることを固定する。チャンク等の他の形式も 485 と同一。
 */
class ChunkTranslator480Test {

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
        writer.writeInt(demand); // demand（480 には無い）
        writer.writeVarInt(3); // villagerLevel
        writer.writeVarInt(11); // experience
        writer.writeBoolean(true); // isRegularVillager
        writer.writeBoolean(true); // canRestock（480 には無い）
        return writer.bytes();
    }

    /** 480 形式の Trade List（demand も canRestock も無し。485 形式と同一）。 */
    private static byte[] tradeList480() {
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
    void tradeList758To480FormDropsDemandAndCanRestock() {
        byte[] out = ChunkTranslator.translateTradeList758To485(tradeList758(7));
        assertArrayEquals(tradeList480(), out);
    }

    @Test
    void tradeList480FormTo758AddsZeroDemandAndCanRestockFromRegularVillager() {
        byte[] out = ChunkTranslator.translateTradeList485To758(tradeList480());
        // demand=0 が補われ、canRestock は isRegularVillager（true）で補われる。
        assertArrayEquals(tradeList758(0), out);
    }

    @Test
    void tradeListRoundTrip480FormThrough758() {
        byte[] as758 = ChunkTranslator.translateTradeList485To758(tradeList480());
        byte[] back = ChunkTranslator.translateTradeList758To485(as758);
        assertArrayEquals(tradeList480(), back);
    }
}
