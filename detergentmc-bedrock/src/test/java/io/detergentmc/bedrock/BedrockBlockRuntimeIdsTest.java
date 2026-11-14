package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Bedrock ブロック対応表（Java 765 → Bedrock ランタイムID）のテスト。 */
class BedrockBlockRuntimeIdsTest {

    @Test
    void tableCoversAllJavaStates() {
        assertEquals(26644, BedrockBlockRuntimeIds.tableSize(),
                "Java 765 の全 26,644 状態を網羅すること");
        for (int state = 0; state < BedrockBlockRuntimeIds.tableSize(); state++) {
            int runtimeId = BedrockBlockRuntimeIds.runtimeId(state);
            assertTrue(runtimeId >= 0 && runtimeId < BedrockBlockRuntimeIds.PALETTE_COUNT,
                    "状態 " + state + " のランタイムIDが範囲外: " + runtimeId);
        }
    }

    @Test
    void knownMappings() {
        assertEquals(11881, BedrockBlockRuntimeIds.runtimeId(0), "空気");
        assertEquals(1774, BedrockBlockRuntimeIds.runtimeId(1), "石");
        assertEquals(12183, BedrockBlockRuntimeIds.runtimeId(8), "草ブロック");
        assertEquals(9550, BedrockBlockRuntimeIds.runtimeId(10), "土");
        assertEquals(9304, BedrockBlockRuntimeIds.runtimeId(80), "水（深さ 0）");
        assertEquals(726, BedrockBlockRuntimeIds.runtimeId(130), "オークの丸太（axis=x）");
        assertEquals(6981, BedrockBlockRuntimeIds.runtimeId(112), "砂");
        assertEquals(12229, BedrockBlockRuntimeIds.runtimeId(79), "岩盤");
        assertEquals(5995, BedrockBlockRuntimeIds.runtimeId(14), "丸石");
    }

    @Test
    void outOfRangeFallsBackToStone() {
        assertEquals(BedrockBlockRuntimeIds.STONE_RUNTIME_ID,
                BedrockBlockRuntimeIds.runtimeId(-1));
        assertEquals(BedrockBlockRuntimeIds.STONE_RUNTIME_ID,
                BedrockBlockRuntimeIds.runtimeId(99999));
        assertFalse(BedrockBlockRuntimeIds.isWaterlogged(-1));
        assertFalse(BedrockBlockRuntimeIds.isWaterlogged(99999));
    }

    @Test
    void waterloggedStates() {
        // 39 は水没したマングローブの芽。石・草・水そのものは水没ではない。
        assertTrue(BedrockBlockRuntimeIds.isWaterlogged(39));
        assertFalse(BedrockBlockRuntimeIds.isWaterlogged(1));
        assertFalse(BedrockBlockRuntimeIds.isWaterlogged(8));
        assertFalse(BedrockBlockRuntimeIds.isWaterlogged(80));
        int waterloggedCount = 0;
        for (int state = 0; state < BedrockBlockRuntimeIds.tableSize(); state++) {
            if (BedrockBlockRuntimeIds.isWaterlogged(state)) {
                waterloggedCount++;
            }
        }
        assertEquals(9181, waterloggedCount, "水没する状態は 9,181 件");
    }

    @Test
    void biomeIds() {
        assertEquals(1, BedrockBiomeIds.idForName("minecraft:plains"), "平原");
        assertEquals(1, BedrockBiomeIds.idForName("plains"), "minecraft: 無しでも引ける");
        assertEquals(2, BedrockBiomeIds.idForName("minecraft:desert"), "砂漠");
        assertEquals(0, BedrockBiomeIds.idForName("minecraft:ocean"), "海洋");
        assertEquals(37, BedrockBiomeIds.idForName("minecraft:badlands"), "荒地");
        assertEquals(1, BedrockBiomeIds.idForName("minecraft:not_a_biome"),
                "未知のバイオームは平原に代替");
        assertEquals(1, BedrockBiomeIds.idForName(null), "null は平原に代替");
        assertEquals(1, BedrockBiomeIds.idForJavaId(39), "番号 39 は平原");
        assertEquals(2, BedrockBiomeIds.idForJavaId(14), "番号 14 は砂漠");
        assertEquals(1, BedrockBiomeIds.idForJavaId(999), "範囲外の番号は平原");
        assertEquals(64, BedrockBiomeIds.BY_JAVA_ID.length, "Java のバイオームは 64 件");
        // 全てのバイオームIDが Bedrock の範囲内（0〜192）。
        for (int id : BedrockBiomeIds.BY_JAVA_ID) {
            assertTrue(id >= 0 && id <= 192, "バイオームIDが範囲外: " + id);
        }
    }
}
