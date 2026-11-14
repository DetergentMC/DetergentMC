package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bedrock 正規ブロックパレット（canonical_block_states.nbt）のテスト。 */
class BedrockBlockPaletteTest {

    @Test
    void paletteLoadsAndHas14127Entries() throws Exception {
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        assertEquals(14127, palette.count(), "正規パレットは 14,127 エントリ");
        assertEquals(BedrockBlockRuntimeIds.PALETTE_VERSION,
                BedrockBlockPalette.EXPECTED_VERSION);
    }

    @Test
    void knownRuntimeIdsPointAtRightNames() throws Exception {
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        assertEquals("minecraft:cyan_terracotta", palette.nameAt(0), "先頭のエントリ");
        assertEquals("minecraft:air", palette.nameAt(11881), "空気");
        assertEquals("minecraft:stone", palette.nameAt(1774), "石");
        assertEquals("minecraft:grass", palette.nameAt(12183), "Bedrock の草ブロックは grass");
        assertEquals("minecraft:water", palette.nameAt(9304), "水");
        assertEquals("minecraft:oak_log", palette.nameAt(725), "オークの丸太");
    }

    @Test
    void entryNamesAreWellFormed() throws Exception {
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        // 名前は状態ごとに重複する（例：blue_candle は個数違いで複数エントリ）。
        for (int i = 0; i < palette.count(); i++) {
            String name = palette.nameAt(i);
            assertTrue(name.startsWith("minecraft:"), "エントリ " + i + ": " + name);
            assertTrue(palette.entryAt(i).length() > 0, "エントリの NBT が空です");
        }
    }

    @Test
    void rawPaletteBytesAreTheWholeFile() throws Exception {
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        byte[] raw = palette.rawPaletteBytes();
        // 先頭は 0x0A（compound）の1エントリ、末尾は TAG_End（0x00）で終わる。
        assertEquals(0x0A, raw[0] & 0xFF);
        assertEquals(0x00, raw[raw.length - 1] & 0xFF);
        assertTrue(raw.length > 1_900_000, "パレット全体は約 2MB");
    }
}
