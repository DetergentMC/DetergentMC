package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Bedrock アイテムID表のテスト。負の network_id は有効な番号であること、
 * バージョン別の bin 表が正しく読めることを検証する。
 * Java のアイテム番号は ViaVersion Mappings の 1.20.3（items 順）で、
 * 石=1、花崗岩=2、凝灰岩=12、ダイヤの剣=834、盾=1155、リンゴ=796、弓=797、
 * オークの丸太=131、トウヒの苗木=49、白いベッド=961、束=927。
 */
class BedrockItemIdsVersionTest {

    @Test
    void negativeNetworkIdsAreValid() {
        // 花崗岩（Java 2）は Bedrock では -590。石（1）に置き換えてはいけない。
        assertEquals(-590, BedrockItemIds.networkId(2), "花崗岩は -590");
        assertTrue(BedrockItemIds.isMapped(2), "花崗岩は対応あり");
        // 全ての対応アイテムについて、networkId が -1 でなければ isMapped が真。
        int mapped = 0;
        for (int item765 = 0; item765 < BedrockItemIds.COUNT; item765++) {
            if (BedrockItemIds.isMapped(item765)) {
                mapped++;
                assertTrue(BedrockItemIds.networkId(item765) != -1);
                int id = BedrockItemIds.networkId(item765);
                // -1 以外の負の値も有効（フォールバックの石 1 ではない）。
                if (id < 0) {
                    assertTrue(id < -1, "負の network_id は -1 以外の値");
                }
            }
        }
        assertEquals(BedrockItemIds.MAPPED_COUNT, mapped,
                "対応数は MAPPED_COUNT（1272）と一致すること");
    }

    @Test
    void fallbackIsStoneOnlyForUnmapped() {
        // bundle（Java 927）は未対応 → 石。
        assertFalse(BedrockItemIds.isMapped(927), "bundle は未対応");
        assertEquals(1, BedrockItemIds.networkId(927), "bundle は石に置き換える");
        assertEquals(1, BedrockItemIds.networkId(-1), "範囲外は石");
        assertEquals(1, BedrockItemIds.networkId(BedrockItemIds.COUNT), "範囲外は石");
        // 石（Java 1）は 1 のまま。
        assertEquals(1, BedrockItemIds.networkId(1), "石は 1");
    }

    @Test
    void oldTableDiamondSwordIs318() {
        // 旧 1 引数版は従来の表のまま（互換性のため変えない）。
        assertEquals(318, BedrockItemIds.networkId(834), "旧表のダイヤの剣は 318");
    }

    @Test
    void versionedTableFor1_20_50() {
        int protocol = 630;
        // トウヒの苗木は 1.20.50 では sapling（6）の共有 ID・metadata 1。
        assertEquals(6, BedrockItemIds.networkId(49, protocol), "トウヒの苗木の ID");
        assertEquals(1, BedrockItemIds.metadata(49, protocol), "トウヒの苗木の metadata");
        // ダイヤの剣は 1.20.50 では 319。
        assertEquals(319, BedrockItemIds.networkId(834, protocol), "ダイヤの剣は 319");
        assertEquals(0, BedrockItemIds.metadata(834, protocol));
        // 花崗岩は -590 のまま。
        assertEquals(-590, BedrockItemIds.networkId(2, protocol));
        assertEquals(0, BedrockItemIds.metadata(2, protocol));
        // 盾は 358。
        assertEquals(358, BedrockItemIds.networkId(1155, protocol));
        // 白いベッドは 421。
        assertEquals(421, BedrockItemIds.networkId(961, protocol));
        // オークの丸太は 17。
        assertEquals(17, BedrockItemIds.networkId(131, protocol));
        // リンゴは 257。
        assertEquals(257, BedrockItemIds.networkId(796, protocol));
        // 弓は 303。
        assertEquals(303, BedrockItemIds.networkId(797, protocol));
        assertTrue(BedrockItemIds.isMapped(49, protocol));
    }

    @Test
    void versionedTableFor1_20_0HasFewerEntries() {
        // 1.20.0 では凝灰岩のハーフブロック（tuff_slab）は Bedrock にまだ無い
        // → 石に置き換える。tuff_slab は Java 13。
        // なお土台の凝灰岩（tuff, Java 12）は 1.17 からあるので 1.20.0 でも対応あり。
        assertTrue(BedrockItemIds.isMapped(12, 589), "1.20.0 では tuff あり");
        assertEquals(-333, BedrockItemIds.networkId(12, 589), "1.20.0 の tuff は -333");
        assertTrue(BedrockItemIds.isMapped(13, 630), "1.20.50 では tuff_slab あり");
        assertEquals(-744, BedrockItemIds.networkId(13, 630));
        assertFalse(BedrockItemIds.isMapped(13, 589), "1.20.0 では tuff_slab なし");
        assertEquals(1, BedrockItemIds.networkId(13, 589), "1.20.0 の tuff_slab は石");
        // 石はどのバージョンでも 1。
        assertEquals(1, BedrockItemIds.networkId(1, 589));
        assertEquals(1, BedrockItemIds.networkId(1, 2193));
    }

    @Test
    void versionedTableFor1_26_50HasDistinctVariantIds() {
        int protocol = 2193;
        // 26.50 ではトウヒの苗木は -825 の独自 ID・metadata 0。
        assertEquals(-825, BedrockItemIds.networkId(49, protocol), "トウヒの苗木は -825");
        assertEquals(0, BedrockItemIds.metadata(49, protocol));
        // ダイヤの剣は PMMP の 1.26.30 版 required_item_list では 347。
        // minecraft-data の items.json（1.26.51）は 318 を載せており両者は
        // 1.26 系で食い違うが、この表は PMMP の version 2 レジストリに従う。
        // どちらが実機の Item パケットで正しいかは実機未確認のため、docs 参照。
        assertEquals(347, BedrockItemIds.networkId(834, protocol), "ダイヤの剣は 347");
        // 花崗岩は -590 のまま。
        assertEquals(-590, BedrockItemIds.networkId(2, protocol));
    }

    @Test
    void unknownProtocolFallsBackToOldTable() {
        // 対象外のプロトコルは旧表（metadata 0）にフォールバックする。
        assertEquals(318, BedrockItemIds.networkId(834, 9999));
        assertEquals(0, BedrockItemIds.metadata(834, 9999));
        assertEquals(BedrockItemIds.isMapped(834), BedrockItemIds.isMapped(834, 9999));
    }
}
