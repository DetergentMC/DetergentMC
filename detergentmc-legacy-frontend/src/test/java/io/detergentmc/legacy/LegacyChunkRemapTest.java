package io.detergentmc.legacy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegacyChunkRemapTest {

    /** Builds one section's grouped-layout data with the given block ids. */
    private static byte[] sectionData(int[] ids4096, boolean sky) {
        int sections = 1;
        byte[] raw = new byte[sections * (4096 + 2048 + 2048) + (sky ? 2048 : 0) + 256];
        for (int j = 0; j < 4096; j++) {
            raw[j] = (byte) ids4096[j];
        }
        return raw;
    }

    @Test
    void stainedGlassBecomesGlassInChunk() {
        int[] ids = new int[4096];
        ids[0] = 95;   // stained glass (1.7)
        ids[1] = 20;   // glass (both)
        ids[2] = 1;    // stone
        byte[] compressed = LegacyChunkRemap.deflate(sectionData(ids, true));
        byte[] out = LegacyChunkRemap.remapSingleChunkData(compressed, 1, 0, true);
        byte[] raw = LegacyChunkRemap.inflate(out);
        assertEquals(20, raw[0] & 0xFF);
        assertEquals(20, raw[1] & 0xFF);
        assertEquals(1, raw[2] & 0xFF);
    }

    @Test
    void unchangedChunkPassesThroughIdentically() {
        int[] ids = new int[4096];
        ids[0] = 1;
        byte[] compressed = LegacyChunkRemap.deflate(sectionData(ids, true));
        byte[] out = LegacyChunkRemap.remapSingleChunkData(compressed, 1, 0, true);
        assertSame(compressed, out);
    }

    @Test
    void bulkRemap() {
        int[] ids = new int[4096];
        ids[5] = 174; // packed ice -> ice (79)
        byte[] chunkRaw = sectionData(ids, true);
        byte[] compressed = LegacyChunkRemap.deflate(chunkRaw);
        byte[] out = LegacyChunkRemap.remapBulkData(compressed, true,
                List.of(new int[]{0, 0, 1, 0}));
        byte[] raw = LegacyChunkRemap.inflate(out);
        assertEquals(79, raw[5] & 0xFF);
    }

    @Test
    void blockValueRemapKeepsMeta() {
        assertEquals((20 << 4) | 7, LegacyChunkRemap.blockValue5To78((95 << 4) | 7));
        assertEquals((1 << 4) | 3, LegacyChunkRemap.blockValue5To78((1 << 4) | 3));
    }

    @Test
    void itemRemap() {
        assertEquals(20, LegacyChunkRemap.itemId5To78(95));
        assertEquals(276, LegacyChunkRemap.itemId5To78(276)); // diamond sword unchanged
        // The "wait" disc (2267) exists in 1.6.4 (measured in the
        // real jar's item registry, docs/PROXY-1.4.2.md) — it is a
        // 1.4.4 addition, not a 1.7 one — so it passes through here
        // and is approximated only by the legacy-47 (1.4.2) layer.
        assertEquals(2267, LegacyChunkRemap.itemId5To78(2267));
    }

    @Test
    void garbageDataPassesThrough() {
        byte[] junk = new byte[]{1, 2, 3};
        assertSame(junk, LegacyChunkRemap.remapSingleChunkData(junk, 1, 0, true));
    }

    // ------------------------------------------------------------------
    // Protocol 61 (1.5.2): the 1.6 additions (docs/PROXY-1.5.2.md)
    // ------------------------------------------------------------------

    @Test
    void block61Approximations() {
        // Stained clay keeps its color as wool; hardened clay -> clay.
        assertEquals((35 << 4) | 14, LegacyChunkRemap.blockValue5To61((159 << 4) | 14));
        assertEquals((82 << 4) | 0, LegacyChunkRemap.blockValue5To61((172 << 4) | 0));
        // Carpet keeps its color as wool.
        assertEquals((35 << 4) | 11, LegacyChunkRemap.blockValue5To61((171 << 4) | 11));
        // Coal block -> black wool, hay bale -> yellow wool (forced metas).
        assertEquals((35 << 4) | 15, LegacyChunkRemap.blockValue5To61((173 << 4) | 0));
        assertEquals((35 << 4) | 4, LegacyChunkRemap.blockValue5To61((170 << 4) | 9));
        // The 78 step still applies first (stained glass -> glass).
        assertEquals((20 << 4) | 7, LegacyChunkRemap.blockValue5To61((95 << 4) | 7));
        // Unmapped blocks pass through.
        assertEquals((1 << 4) | 3, LegacyChunkRemap.blockValue5To61((1 << 4) | 3));
    }

    @Test
    void item61Approximations() {
        assertEquals(306, LegacyChunkRemap.itemId5To61(417)); // iron horse armor
        assertEquals(315, LegacyChunkRemap.itemId5To61(418)); // gold horse armor
        assertEquals(310, LegacyChunkRemap.itemId5To61(419)); // diamond horse armor
        assertEquals(287, LegacyChunkRemap.itemId5To61(420)); // lead -> string
        assertEquals(339, LegacyChunkRemap.itemId5To61(421)); // name tag -> paper
        assertEquals(276, LegacyChunkRemap.itemId5To61(276)); // diamond sword unchanged
        assertEquals(20, LegacyChunkRemap.itemId5To61(95));   // via the 78 map
    }

    @Test
    void coalBlockBecomesBlackWoolInChunk61() {
        int[] ids = new int[4096];
        ids[0] = 173;  // coal block
        ids[1] = 159;  // stained clay (meta nibble 0 by default)
        ids[2] = 1;    // stone
        byte[] compressed = LegacyChunkRemap.deflate(sectionData(ids, true));
        byte[] out = LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_5_2);
        byte[] raw = LegacyChunkRemap.inflate(out);
        assertEquals(35, raw[0] & 0xFF);
        assertEquals(35, raw[1] & 0xFF);
        assertEquals(1, raw[2] & 0xFF);
        // The meta array starts after the block-ID arrays (4096 for
        // one section); index 0 is the low nibble: forced black (15).
        int meta0 = raw[4096] & 0xFF;
        assertEquals(15, meta0 & 0xF, "coal block meta forced to black wool");
        // The same payload toward 78 keeps the coal block untouched.
        assertSame(compressed, LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_6_4));
    }

    // ------------------------------------------------------------------
    // Protocol 51 (1.4.7 / 1.4.6): the 1.5 additions (docs/PROXY-1.4.7.md)
    // ------------------------------------------------------------------

    @Test
    void block51Approximations() {
        assertEquals((54 << 4) | 0, LegacyChunkRemap.blockValue5To51((146 << 4) | 0)); // trapped chest -> chest
        assertEquals((72 << 4) | 0, LegacyChunkRemap.blockValue5To51((147 << 4) | 0)); // light plate -> wooden plate
        assertEquals((70 << 4) | 0, LegacyChunkRemap.blockValue5To51((148 << 4) | 0)); // heavy plate -> stone plate
        assertEquals((93 << 4) | 2, LegacyChunkRemap.blockValue5To51((149 << 4) | 2)); // comparator off -> repeater off
        assertEquals((94 << 4) | 2, LegacyChunkRemap.blockValue5To51((150 << 4) | 2)); // comparator on -> repeater on
        assertEquals((44 << 4) | 0, LegacyChunkRemap.blockValue5To51((151 << 4) | 5)); // daylight sensor -> stone slab, meta forced 0
        assertEquals((35 << 4) | 14, LegacyChunkRemap.blockValue5To51((152 << 4) | 0)); // redstone block -> red wool
        assertEquals((87 << 4) | 0, LegacyChunkRemap.blockValue5To51((153 << 4) | 0)); // quartz ore -> netherrack
        assertEquals((23 << 4) | 3, LegacyChunkRemap.blockValue5To51((154 << 4) | 3)); // hopper -> dispenser
        assertEquals((35 << 4) | 0, LegacyChunkRemap.blockValue5To51((155 << 4) | 9)); // quartz block -> white wool
        assertEquals((53 << 4) | 6, LegacyChunkRemap.blockValue5To51((156 << 4) | 6)); // quartz stairs -> oak stairs
        assertEquals((27 << 4) | 8, LegacyChunkRemap.blockValue5To51((157 << 4) | 8)); // activator rail -> powered rail
        assertEquals((23 << 4) | 1, LegacyChunkRemap.blockValue5To51((158 << 4) | 1)); // dropper -> dispenser
        // The quartz slab is a meta variant of the stone slab, not a
        // new ID: meta 7 becomes the sandstone variant (meta 1).
        assertEquals((44 << 4) | 1, LegacyChunkRemap.blockValue5To51((44 << 4) | 7));
        assertEquals((43 << 4) | 1, LegacyChunkRemap.blockValue5To51((43 << 4) | 7));
        assertEquals((44 << 4) | 3, LegacyChunkRemap.blockValue5To51((44 << 4) | 3)); // other slab metas kept
        // Older layers still apply underneath.
        assertEquals((35 << 4) | 15, LegacyChunkRemap.blockValue5To51((173 << 4) | 0)); // coal block via the 61 layer
        assertEquals((20 << 4) | 7, LegacyChunkRemap.blockValue5To51((95 << 4) | 7));  // via the 78 layer
        assertEquals((1 << 4) | 3, LegacyChunkRemap.blockValue5To51((1 << 4) | 3));    // stone unchanged
    }

    @Test
    void item51Approximations() {
        assertEquals(356, LegacyChunkRemap.itemId5To51(404)); // comparator -> repeater
        assertEquals(336, LegacyChunkRemap.itemId5To51(405)); // nether brick -> brick
        assertEquals(264, LegacyChunkRemap.itemId5To51(406)); // nether quartz -> diamond
        assertEquals(328, LegacyChunkRemap.itemId5To51(407)); // TNT minecart -> minecart
        assertEquals(328, LegacyChunkRemap.itemId5To51(408)); // hopper minecart -> minecart
        assertEquals(54, LegacyChunkRemap.itemId5To51(146));  // block items ride the block map
        assertEquals(23, LegacyChunkRemap.itemId5To51(154));  // hopper item -> dispenser
        assertEquals(306, LegacyChunkRemap.itemId5To51(417)); // via the 61 layer
        assertEquals(276, LegacyChunkRemap.itemId5To51(276)); // diamond sword unchanged
    }

    @Test
    void quartzAndRedstoneBlocksRemapInChunk51() {
        int[] ids = new int[4096];
        ids[0] = 44;   // stone slab, meta 7 = quartz slab variant
        ids[1] = 152;  // redstone block
        ids[2] = 154;  // hopper
        ids[3] = 1;    // stone
        byte[] raw0 = sectionData(ids, true);
        raw0[4096] = (byte) 0x07; // meta nibbles: index0 = 7 (low), index1 = 0 (high)
        byte[] compressed = LegacyChunkRemap.deflate(raw0);
        byte[] out = LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_4_7);
        byte[] raw = LegacyChunkRemap.inflate(out);
        assertEquals(44, raw[0] & 0xFF);
        assertEquals(35, raw[1] & 0xFF);
        assertEquals(23, raw[2] & 0xFF);
        assertEquals(1, raw[3] & 0xFF);
        // Meta byte at 4096: index0 (low) forced 7 -> 1 (sandstone),
        // index1 (high) forced to 14 (red wool).
        assertEquals(0xE1, raw[4096] & 0xFF, "slab meta 7->1 and redstone meta forced to 14");
        // The same payload toward 61 keeps all three blocks.
        assertSame(compressed, LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_5_2));
    }

    // ------------------------------------------------------------------
    // Protocol 49 (1.4.5 / 1.4.4): blocks delegate to 51, items add
    // the 1.4.6 step (docs/PROXY-1.4.5.md)
    // ------------------------------------------------------------------

    @Test
    void item49Approximations() {
        assertEquals(339, LegacyChunkRemap.itemId5To49(401)); // firework rocket -> paper
        assertEquals(348, LegacyChunkRemap.itemId5To49(402)); // firework star -> glowstone dust
        assertEquals(340, LegacyChunkRemap.itemId5To49(403)); // enchanted book -> book
        assertEquals(264, LegacyChunkRemap.itemId5To49(406)); // via the 51 layer
        assertEquals(23, LegacyChunkRemap.itemId5To49(154));  // block items via the 51 layer
        assertEquals(276, LegacyChunkRemap.itemId5To49(276)); // diamond sword unchanged
    }

    @Test
    void block49DelegatesTo51() {
        // The 1.4.5 and 1.4.7 block registries are identical
        // (measured), so the 49 block approximation IS the 51 one.
        assertEquals(LegacyChunkRemap.blockValue5To51((154 << 4) | 3),
                LegacyChunkRemap.blockValue5To49((154 << 4) | 3));
        assertEquals(LegacyChunkRemap.blockValue5To51((44 << 4) | 7),
                LegacyChunkRemap.blockValue5To49((44 << 4) | 7));
        assertEquals(LegacyChunkRemap.blockValue5To51((152 << 4) | 0),
                LegacyChunkRemap.blockValue5To49((152 << 4) | 0));
        // And a chunk payload remaps the same toward 49 as toward 51.
        int[] ids = new int[4096];
        ids[0] = 154;  // hopper
        ids[1] = 152;  // redstone block
        byte[] compressed = LegacyChunkRemap.deflate(sectionData(ids, true));
        byte[] out49 = LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_4_5);
        byte[] raw = LegacyChunkRemap.inflate(out49);
        assertEquals(23, raw[0] & 0xFF);
        assertEquals(35, raw[1] & 0xFF);
        assertEquals(14, (raw[4096] >> 4) & 0xF, "redstone block meta forced to red wool for 49");
    }

    // ------------------------------------------------------------------
    // Legacy protocol 47 (1.4.2): blocks delegate to 49, items add
    // the 1.4.4 step (docs/PROXY-1.4.2.md)
    // ------------------------------------------------------------------

    @Test
    void item47OldApproximations() {
        assertEquals(2266, LegacyChunkRemap.itemId5To47Old(2267)); // "wait" record -> "11" record
        assertEquals(2266, LegacyChunkRemap.itemId5To47Old(2266)); // "11" record unchanged
        assertEquals(2256, LegacyChunkRemap.itemId5To47Old(2256)); // "13" record unchanged
        assertEquals(339, LegacyChunkRemap.itemId5To47Old(401));  // via the 49 layer
        assertEquals(340, LegacyChunkRemap.itemId5To47Old(403));  // via the 49 layer
        assertEquals(276, LegacyChunkRemap.itemId5To47Old(276));  // diamond sword unchanged
        // And 49 keeps the "wait" record as it is (it exists in 1.4.4+).
        assertEquals(2267, LegacyChunkRemap.itemId5To49(2267));
    }

    @Test
    void block47OldDelegatesTo49() {
        // The 1.4.2 and 1.4.5 block registries are identical
        // (measured), so the legacy-47 block approximation IS the
        // 49 one — and a chunk payload remaps the same toward
        // legacy 47 as toward 49.
        assertEquals(LegacyChunkRemap.blockValue5To49((154 << 4) | 3),
                LegacyChunkRemap.blockValue5To47Old((154 << 4) | 3));
        assertEquals(LegacyChunkRemap.blockValue5To49((152 << 4) | 0),
                LegacyChunkRemap.blockValue5To47Old((152 << 4) | 0));
        int[] ids = new int[4096];
        ids[0] = 154;  // hopper
        ids[1] = 152;  // redstone block
        byte[] compressed = LegacyChunkRemap.deflate(sectionData(ids, true));
        byte[] out47 = LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_4_2);
        byte[] raw = LegacyChunkRemap.inflate(out47);
        assertEquals(23, raw[0] & 0xFF);
        assertEquals(35, raw[1] & 0xFF);
        assertEquals(14, (raw[4096] >> 4) & 0xF, "redstone block meta forced to red wool for legacy 47");
    }

    // ------------------------------------------------------------------
    // Protocol 39 (1.3.2 / 1.3.1): the 1.4 additions (blocks 137-145,
    // items 389-400) are approximated (docs/PROXY-1.3.2.md)
    // ------------------------------------------------------------------

    @Test
    void item39Approximations() {
        assertEquals(321, LegacyChunkRemap.itemId5To39(389)); // item frame -> painting
        assertEquals(281, LegacyChunkRemap.itemId5To39(390)); // flower pot -> bowl
        assertEquals(260, LegacyChunkRemap.itemId5To39(391)); // carrot -> apple
        assertEquals(297, LegacyChunkRemap.itemId5To39(392)); // potato -> bread
        assertEquals(297, LegacyChunkRemap.itemId5To39(393)); // baked potato -> bread
        assertEquals(367, LegacyChunkRemap.itemId5To39(394)); // poisonous potato -> rotten flesh
        assertEquals(358, LegacyChunkRemap.itemId5To39(395)); // empty map -> map
        assertEquals(322, LegacyChunkRemap.itemId5To39(396)); // golden carrot -> golden apple
        assertEquals(298, LegacyChunkRemap.itemId5To39(397)); // skull -> leather helmet
        assertEquals(346, LegacyChunkRemap.itemId5To39(398)); // carrot on a stick -> fishing rod
        assertEquals(388, LegacyChunkRemap.itemId5To39(399)); // nether star -> emerald
        assertEquals(354, LegacyChunkRemap.itemId5To39(400)); // pumpkin pie -> cake
        // The steps below still apply first.
        assertEquals(2266, LegacyChunkRemap.itemId5To39(2267)); // via legacy-47 layer
        assertEquals(339, LegacyChunkRemap.itemId5To39(401));  // via the 49 layer
        assertEquals(276, LegacyChunkRemap.itemId5To39(276));  // diamond sword unchanged
        // Block items ride the block map.
        assertEquals(25, LegacyChunkRemap.itemId5To39(137));  // command block item
        assertEquals(42, LegacyChunkRemap.itemId5To39(145));  // anvil item
        // And legacy 47 keeps the 1.4 items as they are.
        assertEquals(389, LegacyChunkRemap.itemId5To47Old(389));
        assertEquals(137, LegacyChunkRemap.itemId5To47Old(137));
    }

    @Test
    void block39Approximations() {
        assertEquals((25 << 4) | 3, LegacyChunkRemap.blockValue5To39((137 << 4) | 3)); // command block -> note block
        assertEquals((89 << 4), LegacyChunkRemap.blockValue5To39(138 << 4));           // beacon -> glowstone
        assertEquals((85 << 4) | 1, LegacyChunkRemap.blockValue5To39((139 << 4) | 1)); // cobble wall -> fence
        assertEquals((38 << 4), LegacyChunkRemap.blockValue5To39((140 << 4) | 9),
                "flower pot -> red flower, plant meta forced to 0");
        assertEquals((59 << 4) | 7, LegacyChunkRemap.blockValue5To39((141 << 4) | 7)); // carrots -> crops, growth kept
        assertEquals((59 << 4) | 7, LegacyChunkRemap.blockValue5To39((142 << 4) | 7)); // potatoes -> crops
        assertEquals((77 << 4) | 5, LegacyChunkRemap.blockValue5To39((143 << 4) | 5)); // wooden button -> stone button
        assertEquals((63 << 4) | 9, LegacyChunkRemap.blockValue5To39((144 << 4) | 9)); // skull -> sign post
        assertEquals((42 << 4), LegacyChunkRemap.blockValue5To39((145 << 4) | 12),
                "anvil -> iron block, facing/damage meta forced to 0");
        // Older layers still apply underneath.
        assertEquals((23 << 4) | 3, LegacyChunkRemap.blockValue5To39((154 << 4) | 3)); // hopper via 51
        assertEquals((35 << 4) | 14, LegacyChunkRemap.blockValue5To39((152 << 4) | 0)); // redstone block via 51
        // And legacy 47 keeps the 1.4 blocks as they are.
        assertEquals(137, LegacyChunkRemap.blockValue5To47Old(137 << 4) >> 4);
    }

    @Test
    void chunkPayloadRemapsFor39() {
        int[] ids = new int[4096];
        ids[0] = 137;  // command block
        ids[1] = 145;  // anvil
        ids[2] = 141;  // carrots
        byte[] compressed = LegacyChunkRemap.deflate(sectionData(ids, true));
        byte[] out = LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_3_2);
        byte[] raw = LegacyChunkRemap.inflate(out);
        assertEquals(25, raw[0] & 0xFF);
        assertEquals(42, raw[1] & 0xFF);
        assertEquals(59, raw[2] & 0xFF);
    }

    // ------------------------------------------------------------------
    // Protocol 29 (1.2.5): the 1.3 additions (blocks 125-136,
    // items 386-388) are approximated (docs/PROXY-1.2.5.md)
    // ------------------------------------------------------------------

    @Test
    void item29Approximations() {
        assertEquals(340, LegacyChunkRemap.itemId5To29(386)); // book and quill -> book
        assertEquals(340, LegacyChunkRemap.itemId5To29(387)); // written book -> book
        assertEquals(264, LegacyChunkRemap.itemId5To29(388)); // emerald -> diamond
        // The nether star rides the 39 layer to emerald, then to diamond.
        assertEquals(264, LegacyChunkRemap.itemId5To29(399));
        // The steps below still apply first.
        assertEquals(321, LegacyChunkRemap.itemId5To29(389)); // item frame via 39
        assertEquals(2266, LegacyChunkRemap.itemId5To29(2267)); // via legacy-47 layer
        assertEquals(276, LegacyChunkRemap.itemId5To29(276));  // diamond sword unchanged
        assertEquals(264, LegacyChunkRemap.itemId5To29(264));  // diamond itself unchanged
        // Block items ride the block map.
        assertEquals(56, LegacyChunkRemap.itemId5To29(129));  // emerald ore item
        assertEquals(44, LegacyChunkRemap.itemId5To29(126));  // wooden slab item
        // And 39 keeps the 1.3 items as they are.
        assertEquals(388, LegacyChunkRemap.itemId5To39(388));
        assertEquals(129, LegacyChunkRemap.itemId5To39(129));
    }

    @Test
    void block29Approximations() {
        assertEquals((56 << 4) | 0, LegacyChunkRemap.blockValue5To29((129 << 4) | 0)); // emerald ore -> diamond ore
        assertEquals((57 << 4) | 0, LegacyChunkRemap.blockValue5To29((133 << 4) | 0)); // emerald block -> diamond block
        assertEquals((54 << 4) | 5, LegacyChunkRemap.blockValue5To29((130 << 4) | 5)); // ender chest -> chest, facing kept
        assertEquals((106 << 4) | 0, LegacyChunkRemap.blockValue5To29((127 << 4) | 0)); // cocoa -> vines
        assertEquals((53 << 4) | 3, LegacyChunkRemap.blockValue5To29((128 << 4) | 3)); // sandstone stairs -> oak stairs
        assertEquals((53 << 4) | 3, LegacyChunkRemap.blockValue5To29((134 << 4) | 3)); // spruce stairs
        assertEquals((53 << 4) | 3, LegacyChunkRemap.blockValue5To29((135 << 4) | 3)); // birch stairs
        assertEquals((53 << 4) | 3, LegacyChunkRemap.blockValue5To29((136 << 4) | 3)); // jungle stairs
        assertEquals((69 << 4) | 2, LegacyChunkRemap.blockValue5To29((131 << 4) | 2)); // tripwire hook -> lever
        assertEquals(0, LegacyChunkRemap.blockValue5To29((132 << 4) | 0));             // tripwire -> air
        // Wooden slabs land on the wood-textured stone-slab variant
        // (meta 2 exists in 1.2.5); a top-half slab keeps its bit.
        assertEquals((43 << 4) | 2, LegacyChunkRemap.blockValue5To29((125 << 4) | 1));
        assertEquals((44 << 4) | 2, LegacyChunkRemap.blockValue5To29((126 << 4) | 1));
        assertEquals((44 << 4) | 10, LegacyChunkRemap.blockValue5To29((126 << 4) | 9));
        // Older layers still apply underneath.
        assertEquals((25 << 4) | 3, LegacyChunkRemap.blockValue5To29((137 << 4) | 3)); // command block via 39
        assertEquals((1 << 4) | 3, LegacyChunkRemap.blockValue5To29((1 << 4) | 3));    // stone untouched
    }

    @Test
    void chunkPayloadRemapsFor29() {
        int[] ids = new int[4096];
        ids[0] = 129;  // emerald ore
        ids[1] = 133;  // emerald block
        ids[2] = 132;  // tripwire
        ids[3] = 137;  // command block (via the 39 layer)
        byte[] compressed = LegacyChunkRemap.deflate(sectionData(ids, true));
        byte[] out = LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_2_5);
        byte[] raw = LegacyChunkRemap.inflate(out);
        assertEquals(56, raw[0] & 0xFF);
        assertEquals(57, raw[1] & 0xFF);
        assertEquals(0, raw[2] & 0xFF);
        assertEquals(25, raw[3] & 0xFF);
        // Toward 39 the 1.3 blocks survive (only the 39 layer runs).
        byte[] raw39 = LegacyChunkRemap.inflate(LegacyChunkRemap.remapSingleChunkData(
                compressed, 1, 0, true, LegacyProtocol.V1_3_2));
        assertEquals(129, raw39[0] & 0xFF);
        assertEquals(133, raw39[1] & 0xFF);
    }

    // ------------------------------------------------------------------
    // Protocols 28 (1.2.3), 23 (1.1) and 22 (1.0.0): the 1.2
    // additions (lamp blocks, items 384-385) and the 1.1 addition
    // (item 383) are approximated (docs/PROXY-1.2.3.md,
    // docs/PROXY-1.1.md, docs/PROXY-1.0.md)
    // ------------------------------------------------------------------

    @Test
    void layer28DelegatesTo29() {
        // The 28 registries are identical to 29's (measured), so
        // every 28 approximation equals the 29 one.
        assertEquals(LegacyChunkRemap.blockValue5To29((123 << 4) | 0),
                LegacyChunkRemap.blockValue5To28((123 << 4) | 0));
        assertEquals(384, LegacyChunkRemap.itemId5To28(384));
        assertEquals(385, LegacyChunkRemap.itemId5To28(385));
        assertEquals(264, LegacyChunkRemap.itemId5To28(388)); // via the 29 layer
    }

    @Test
    void item23Approximations() {
        assertEquals(373, LegacyChunkRemap.itemId5To23(384)); // bottle o' enchanting -> potion
        assertEquals(259, LegacyChunkRemap.itemId5To23(385)); // fire charge -> flint and steel
        assertEquals(383, LegacyChunkRemap.itemId5To23(383)); // spawn egg exists in 1.1
        // Block items ride the block map.
        assertEquals(89, LegacyChunkRemap.itemId5To23(123));  // lamp item -> glowstone
        // Older layers still apply underneath.
        assertEquals(264, LegacyChunkRemap.itemId5To23(399)); // nether star via 39/29
        assertEquals(276, LegacyChunkRemap.itemId5To23(276)); // diamond sword unchanged
    }

    @Test
    void block23Approximations() {
        assertEquals((89 << 4) | 0, LegacyChunkRemap.blockValue5To23((123 << 4) | 0)); // lamp off -> glowstone
        assertEquals((89 << 4) | 0, LegacyChunkRemap.blockValue5To23((124 << 4) | 0)); // lamp on -> glowstone
        assertEquals((56 << 4) | 0, LegacyChunkRemap.blockValue5To23((129 << 4) | 0)); // via the 29 layer
        assertEquals((1 << 4) | 3, LegacyChunkRemap.blockValue5To23((1 << 4) | 3));    // stone untouched
    }

    @Test
    void item22Approximations() {
        assertEquals(339, LegacyChunkRemap.itemId5To22(383)); // spawn egg -> paper
        assertEquals(373, LegacyChunkRemap.itemId5To22(384)); // via the 23 layer
        assertEquals(259, LegacyChunkRemap.itemId5To22(385)); // via the 23 layer
        assertEquals(89, LegacyChunkRemap.itemId5To22(124));  // lamp item via the 23 layer
    }

    @Test
    void cuboidConversionRelaysAndRemaps() {
        // Two sections (0 and 1) with sky light and a biome tail.
        int sections = 2;
        byte[] raw = new byte[sections * (4096 + 2048 + 2048 + 2048) + 256];
        // Section 0, local (lx=1, ly=2, lz=3): index 2*256+3*16+1.
        raw[2 * 256 + 3 * 16 + 1] = (byte) 123; // redstone lamp
        // Section 1, local (0,0,0): first entry of the second id array.
        raw[4096] = 1; // stone
        // Meta 5 for the lamp: meta arrays start after both id
        // arrays. The section index (561) is odd, so the value
        // sits in the byte's HIGH nibble.
        int metaOffset = sections * 4096;
        int lampIndex = 2 * 256 + 3 * 16 + 1;
        raw[metaOffset + lampIndex / 2] = (byte) (raw[metaOffset + lampIndex / 2] | (5 << 4));
        byte[] compressed = LegacyChunkRemap.deflate(raw);
        LegacyChunkRemap.CuboidData cuboid = LegacyChunkRemap.remapChunkToCuboid(
                compressed, 0b11, 0, true, LegacyProtocol.V1_1);
        assertNotNull(cuboid);
        assertEquals(0, cuboid.yBase());
        assertEquals(32, cuboid.sizeY());
        byte[] out = LegacyChunkRemap.inflate(cuboid.compressedData());
        int total = 16 * 32 * 16;
        assertEquals(total + 3 * ((total + 1) / 2), out.length,
                "cuboid payload is ids + three nibble arrays");
        // Cuboid index: (lx * 16 + lz) * sizeY + ly = (16+3)*32 + 2.
        int lampCuboid = (1 * 16 + 3) * 32 + 2;
        assertEquals(89, out[lampCuboid] & 0xFF, "the lamp became glowstone");
        // The stone of section 1 lands at cuboid y 16.
        assertEquals(1, out[(0 * 16 + 0) * 32 + 16] & 0xFF);
        // The meta nibble follows the id array. The cuboid index
        // (610) is even — the low nibble — although the section
        // index was odd: the re-laying handles the parity flip.
        int metaByte = out[total + lampCuboid / 2] & 0xFF;
        assertEquals(5, metaByte & 0xF, "the lamp's meta survives the re-laying");
    }

    @Test
    void cuboidDropsSectionsAbove127() {
        // Sections 7 and 8 present: only section 7 has a cuboid home.
        int sections = 2;
        byte[] raw = new byte[sections * (4096 + 2048 + 2048 + 2048) + 256];
        raw[0] = 1; // a block in section 7
        byte[] compressed = LegacyChunkRemap.deflate(raw);
        LegacyChunkRemap.CuboidData cuboid = LegacyChunkRemap.remapChunkToCuboid(
                compressed, (1 << 7) | (1 << 8), 0, true, LegacyProtocol.V1_0);
        assertNotNull(cuboid);
        assertEquals(112, cuboid.yBase());
        assertEquals(16, cuboid.sizeY());
        byte[] out = LegacyChunkRemap.inflate(cuboid.compressedData());
        assertEquals(1, out[0] & 0xFF, "section 7's block lands at the cuboid base");
    }

    @Test
    void cuboidIsNullWhenNothingSurvivesBelow128() {
        byte[] raw = new byte[4096 + 2048 + 2048 + 2048 + 256];
        byte[] compressed = LegacyChunkRemap.deflate(raw);
        assertNull(
                LegacyChunkRemap.remapChunkToCuboid(
                        compressed, 1 << 12, 0, true, LegacyProtocol.V1_1));
        assertNull(
                LegacyChunkRemap.remapChunkToCuboid(
                        new byte[]{1, 2, 3}, 1, 0, true, LegacyProtocol.V1_1),
                "an unparsable payload converts to null, not an exception");
    }
}
