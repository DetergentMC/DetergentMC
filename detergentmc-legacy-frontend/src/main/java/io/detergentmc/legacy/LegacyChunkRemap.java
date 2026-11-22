package io.detergentmc.legacy;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Block-ID remapping between the protocol-5 registry space (what the
 * core sends a protocol-5 client) and the legacy protocol-78 space
 * (what a 1.6.4 client understands), for the blocks whose numeric IDs
 * differ or that did not exist in 1.6.4.
 *
 * <p>Both spaces are pre-Flattening "(id &lt;&lt; 4) | meta" numbers
 * and 1.7 mostly only ADDED blocks, so the map is small. Blocks added
 * in 1.7 are approximated by their closest 1.6.4 counterpart; the meta
 * value is preserved.
 *
 * <p>Chunk payloads (single and bulk) carry zlib-compressed section
 * data laid out grouped by array kind: all sections' block-ID arrays,
 * then all metadata nibble arrays, then block light, then sky light,
 * then the "add" nibble arrays (high ID bits) of the sections present
 * in the add bitmap, then (for ground-up chunks) 256 biome bytes.
 * The remapper inflates, rewrites IDs in place, and re-deflates only
 * when something actually changed; otherwise the original payload is
 * returned untouched.
 */
public final class LegacyChunkRemap {

    /** Protocol-5 block ID -> legacy-78 block ID approximations. */
    public static final Map<Integer, Integer> BLOCK_ID_5_TO_78 = Map.ofEntries(
            Map.entry(95, 20),    // stained glass -> glass
            Map.entry(160, 102),  // stained glass pane -> glass pane
            Map.entry(161, 18),   // acacia/dark-oak leaves -> leaves
            Map.entry(162, 17),   // acacia/dark-oak log -> log
            Map.entry(163, 53),   // acacia stairs -> oak stairs
            Map.entry(164, 53),   // dark-oak stairs -> oak stairs
            Map.entry(174, 79),   // packed ice -> ice
            Map.entry(175, 38));  // double plant -> red flower

    /** Item ID remap for slots (block items share the block map). */
    public static int itemId5To78(int itemId) {
        Integer mapped = BLOCK_ID_5_TO_78.get(itemId);
        if (mapped != null) {
            return mapped;
        }
        // Item 2267 (the "wait" record) passes through: it is NOT
        // a 1.7 addition — the real 1.6.4 jar's item registry
        // contains record_wait (measured in the legacy-47 Stage A,
        // docs/PROXY-1.4.2.md). The disc arrived in 1.4.4, so every
        // legacy generation except 1.4.2 has it; the one missing
        // generation is handled by the legacy-47 layer below. (An
        // earlier revision of this method mapped 2267 -> 2256 for
        // all targets on the mistaken "(1.7)" premise.)
        return itemId;
    }

    // ------------------------------------------------------------------
    // Protocol 61 (1.5.2) targets: the blocks and items added in 1.6
    // (docs/PROXY-1.5.2.md). Applied AFTER the 78 map above; the meta
    // (color) is preserved where the approximation has one.
    // ------------------------------------------------------------------

    /** Legacy-78 block ID -> legacy-61 block ID (1.6 additions). */
    public static final Map<Integer, Integer> BLOCK_ID_78_TO_61 = Map.ofEntries(
            Map.entry(159, 35),   // stained clay -> wool (meta = color)
            Map.entry(170, 35),   // hay bale -> wool (meta forced yellow)
            Map.entry(171, 35),   // carpet -> wool (meta = color)
            Map.entry(172, 82),   // hardened clay -> clay
            Map.entry(173, 35));  // coal block -> wool (meta forced black)

    /** Metas forced by the 61 approximation, by protocol-5 block ID. */
    private static final Map<Integer, Integer> FORCED_META_61 = Map.of(
            170, 4,    // hay bale -> yellow wool
            173, 15);  // coal block -> black wool

    /** Item IDs added in 1.6, approximated for 61 (after the 78 map). */
    private static final Map<Integer, Integer> ITEM_ID_78_TO_61 = Map.of(
            417, 306,  // iron horse armor -> iron chestplate
            418, 315,  // gold horse armor -> gold chestplate
            419, 310,  // diamond horse armor -> diamond chestplate
            420, 287,  // lead -> string
            421, 339); // name tag -> paper

    /** Full (id, meta) approximation of a protocol-5 block for 61. */
    public static int[] blockIdAndMeta5To61(int id, int meta) {
        int out = BLOCK_ID_5_TO_78.getOrDefault(id, id);
        Integer mapped = BLOCK_ID_78_TO_61.get(out);
        if (mapped != null) {
            out = mapped;
        }
        Integer forced = FORCED_META_61.get(id);
        return new int[]{out, forced != null ? forced : meta};
    }

    /** Remaps one (id << 4) | meta block value for 61. */
    public static int blockValue5To61(int value) {
        int[] r = blockIdAndMeta5To61(value >> 4, value & 15);
        return (r[0] << 4) | r[1];
    }

    /** Item ID remap for slots toward 61. */
    public static int itemId5To61(int itemId) {
        int out = itemId5To78(itemId);
        return ITEM_ID_78_TO_61.getOrDefault(out, out);
    }

    /** ID-only step used inside chunk payloads (meta handled apart). */
    private static int chunkBlockId5To61(int id) {
        int out = BLOCK_ID_5_TO_78.getOrDefault(id, id);
        return BLOCK_ID_78_TO_61.getOrDefault(out, out);
    }

    // ------------------------------------------------------------------
    // Protocol 51 (1.4.7 / 1.4.6) targets: the blocks and items added
    // in 1.5 (docs/PROXY-1.4.7.md). Applied AFTER the 61 layer above;
    // the meta (color / powered / slab variant) is preserved where
    // the approximation has one.
    // ------------------------------------------------------------------

    /** Legacy-61 block ID -> legacy-51 block ID (1.5 additions). */
    public static final Map<Integer, Integer> BLOCK_ID_61_TO_51 = Map.ofEntries(
            Map.entry(146, 54),   // trapped chest -> chest
            Map.entry(147, 72),   // light weighted pressure plate -> wooden plate
            Map.entry(148, 70),   // heavy weighted pressure plate -> stone plate
            Map.entry(149, 93),   // comparator (unpowered) -> repeater (unpowered)
            Map.entry(150, 94),   // comparator (powered) -> repeater (powered)
            Map.entry(151, 44),   // daylight sensor -> stone slab (meta forced 0)
            Map.entry(152, 35),   // redstone block -> wool (meta forced red)
            Map.entry(153, 87),   // nether quartz ore -> netherrack
            Map.entry(154, 23),   // hopper -> dispenser
            Map.entry(155, 35),   // quartz block -> wool (meta forced white)
            Map.entry(156, 53),   // quartz stairs -> oak stairs
            Map.entry(157, 27),   // activator rail -> powered rail
            Map.entry(158, 23));  // dropper -> dispenser

    /** Metas forced by the 51 approximation, by protocol-5 block ID. */
    private static final Map<Integer, Integer> FORCED_META_51 = Map.of(
            151, 0,    // daylight sensor -> plain stone slab
            152, 14,   // redstone block -> red wool
            155, 0);   // quartz block -> white wool

    /** Item IDs added in 1.5, approximated for 51 (after the 61 map). */
    private static final Map<Integer, Integer> ITEM_ID_61_TO_51 = Map.of(
            404, 356,  // comparator -> repeater
            405, 336,  // nether brick -> brick
            406, 264,  // nether quartz -> diamond
            407, 328,  // TNT minecart -> minecart
            408, 328); // hopper minecart -> minecart

    /**
     * The meta a protocol-5 block keeps toward 51: the quartz slab
     * variant (stone slab / double slab, meta 7 — a 1.5 variant that
     * 1.4.7 does not have) becomes the pale sandstone variant, then
     * the 61 and 51 forced metas apply.
     */
    private static int meta5To51(int id, int meta) {
        int m = meta;
        if ((id == 44 || id == 43) && meta == 7) {
            m = 1; // quartz slab -> sandstone slab
        }
        Integer forced = FORCED_META_61.get(id);
        if (forced != null) {
            m = forced;
        }
        forced = FORCED_META_51.get(id);
        if (forced != null) {
            m = forced;
        }
        return m;
    }

    /** Full (id, meta) approximation of a protocol-5 block for 51. */
    public static int[] blockIdAndMeta5To51(int id, int meta) {
        int[] r = blockIdAndMeta5To61(id, meta);
        int out = BLOCK_ID_61_TO_51.getOrDefault(r[0], r[0]);
        return new int[]{out, meta5To51(id, meta)};
    }

    /** Remaps one (id << 4) | meta block value for 51. */
    public static int blockValue5To51(int value) {
        int[] r = blockIdAndMeta5To51(value >> 4, value & 15);
        return (r[0] << 4) | r[1];
    }

    /** Item ID remap for slots toward 51. */
    public static int itemId5To51(int itemId) {
        int out = itemId5To61(itemId);
        if (out < 256) {
            // Block items ride the block map.
            return BLOCK_ID_61_TO_51.getOrDefault(out, out);
        }
        return ITEM_ID_61_TO_51.getOrDefault(out, out);
    }

    /** ID-only step used inside chunk payloads (meta handled apart). */
    private static int chunkBlockId5To51(int id) {
        int out = chunkBlockId5To61(id);
        return BLOCK_ID_61_TO_51.getOrDefault(out, out);
    }

    // ------------------------------------------------------------------
    // Protocol 49 (1.4.5 / 1.4.4) targets: the blocks are identical
    // to 51 (measured), so the block approximations delegate to the
    // 51 layer outright; only the three items added in 1.4.6 need a
    // further step (docs/PROXY-1.4.5.md).
    // ------------------------------------------------------------------

    /** Item IDs added in 1.4.6, approximated for 49 (after the 51 map). */
    private static final Map<Integer, Integer> ITEM_ID_51_TO_49 = Map.of(
            401, 339,  // firework rocket -> paper (its main ingredient)
            402, 348,  // firework star -> glowstone dust (its sparkle)
            403, 340); // enchanted book -> book

    /** Full (id, meta) approximation of a protocol-5 block for 49. */
    public static int[] blockIdAndMeta5To49(int id, int meta) {
        return blockIdAndMeta5To51(id, meta);
    }

    /** Remaps one (id << 4) | meta block value for 49. */
    public static int blockValue5To49(int value) {
        return blockValue5To51(value);
    }

    /** Item ID remap for slots toward 49. */
    public static int itemId5To49(int itemId) {
        int out = itemId5To51(itemId);
        return ITEM_ID_51_TO_49.getOrDefault(out, out);
    }

    // ------------------------------------------------------------------
    // Legacy protocol 47 (1.4.2) targets: the wire layouts and the
    // block/entity registries are identical to 49 (measured,
    // docs/PROXY-1.4.2.md), so the block approximations delegate to
    // the 49 layer outright; only the one item added in 1.4.4 needs
    // a further step.
    // ------------------------------------------------------------------

    /** The one item added in 1.4.4, approximated for legacy 47 (after the 49 map). */
    private static final Map<Integer, Integer> ITEM_ID_49_TO_47OLD = Map.of(
            2267, 2266); // "wait" record -> "11" record (the disc it repairs)

    /** Full (id, meta) approximation of a protocol-5 block for legacy 47 (1.4.2). */
    public static int[] blockIdAndMeta5To47Old(int id, int meta) {
        return blockIdAndMeta5To49(id, meta);
    }

    /** Remaps one (id << 4) | meta block value for legacy 47 (1.4.2). */
    public static int blockValue5To47Old(int value) {
        return blockValue5To49(value);
    }

    /** Item ID remap for slots toward legacy 47 (1.4.2). */
    public static int itemId5To47Old(int itemId) {
        int out = itemId5To49(itemId);
        return ITEM_ID_49_TO_47OLD.getOrDefault(out, out);
    }

    // ------------------------------------------------------------------
    // Protocol 39 (1.3.2 / 1.3.1) targets: the blocks and items
    // added in 1.4 (docs/PROXY-1.3.2.md). Applied AFTER the
    // legacy-47 layer above (the 1.4.2 registries minus the 1.4
    // additions are exactly the 1.3.2 registries, measured in the
    // real jars); the meta is preserved where the approximation
    // has one.
    // ------------------------------------------------------------------

    /** Legacy-51 block ID -> protocol-39 block ID (1.4 additions). */
    public static final Map<Integer, Integer> BLOCK_ID_51_TO_39 = Map.ofEntries(
            Map.entry(137, 25),   // command block -> note block
            Map.entry(138, 89),   // beacon -> glowstone (the other light-15 block)
            Map.entry(139, 85),   // cobblestone wall -> fence
            Map.entry(140, 38),   // flower pot -> red flower
            Map.entry(141, 59),   // carrots -> crops (growth meta is shared)
            Map.entry(142, 59),   // potatoes -> crops (growth meta is shared)
            Map.entry(143, 77),   // wooden button -> stone button (facing meta is shared)
            Map.entry(144, 63),   // skull -> sign post
            Map.entry(145, 42));  // anvil -> iron block

    /** Metas forced by the 39 approximation, by protocol-5 block ID. */
    private static final Map<Integer, Integer> FORCED_META_39 = Map.of(
            140, 0,    // flower pot (meta = plant) -> plain red flower
            145, 0);   // anvil (meta = facing + damage) -> plain iron block

    /** Item IDs added in 1.4, approximated for 39 (after the legacy-47 map). */
    private static final Map<Integer, Integer> ITEM_ID_47OLD_TO_39 = Map.ofEntries(
            Map.entry(389, 321),  // item frame -> painting (the other wall decoration)
            Map.entry(390, 281),  // flower pot -> bowl (the other small container)
            Map.entry(391, 260),  // carrot -> apple
            Map.entry(392, 297),  // potato -> bread
            Map.entry(393, 297),  // baked potato -> bread
            Map.entry(394, 367),  // poisonous potato -> rotten flesh (the other bad food)
            Map.entry(395, 358),  // empty map -> map
            Map.entry(396, 322),  // golden carrot -> golden apple
            Map.entry(397, 298),  // skull -> leather helmet (the wearable slot it fills)
            Map.entry(398, 346),  // carrot on a stick -> fishing rod (its base tool)
            Map.entry(399, 388),  // nether star -> emerald (the other rare gem)
            Map.entry(400, 354)); // pumpkin pie -> cake

    /**
     * The meta a protocol-5 block keeps toward 39: the 51 layer's
     * meta rules, then the 39 forced metas.
     */
    private static int meta5To39(int id, int meta) {
        int m = meta5To51(id, meta);
        Integer forced = FORCED_META_39.get(id);
        return forced != null ? forced : m;
    }

    /** Full (id, meta) approximation of a protocol-5 block for 39. */
    public static int[] blockIdAndMeta5To39(int id, int meta) {
        int[] r = blockIdAndMeta5To51(id, meta);
        int out = BLOCK_ID_51_TO_39.getOrDefault(r[0], r[0]);
        Integer forced = FORCED_META_39.get(id);
        return new int[]{out, forced != null ? forced : r[1]};
    }

    /** Remaps one (id << 4) | meta block value for 39. */
    public static int blockValue5To39(int value) {
        int[] r = blockIdAndMeta5To39(value >> 4, value & 15);
        return (r[0] << 4) | r[1];
    }

    /** Item ID remap for slots toward 39. */
    public static int itemId5To39(int itemId) {
        int out = itemId5To47Old(itemId);
        if (out < 256) {
            // Block items ride the block map.
            return BLOCK_ID_51_TO_39.getOrDefault(out, out);
        }
        return ITEM_ID_47OLD_TO_39.getOrDefault(out, out);
    }

    /** ID-only step used inside chunk payloads (meta handled apart). */
    private static int chunkBlockId5To39(int id) {
        int out = chunkBlockId5To51(id);
        return BLOCK_ID_51_TO_39.getOrDefault(out, out);
    }

    // ------------------------------------------------------------------
    // Protocol 29 (1.2.5) targets: the blocks and items added in
    // 1.3 (docs/PROXY-1.2.5.md). Applied AFTER the 39 layer above
    // (the 1.3.2 registries minus the 1.3 additions are exactly
    // the 1.2.5 registries, measured in the real jars — the
    // entity registries are identical, so no entity step is
    // needed); the meta is preserved where the approximation
    // has one.
    // ------------------------------------------------------------------

    /** Legacy-39 block ID -> protocol-29 block ID (1.3 additions). */
    public static final Map<Integer, Integer> BLOCK_ID_39_TO_29 = Map.ofEntries(
            Map.entry(125, 43),   // double wooden slab -> double stone slab (wood variant)
            Map.entry(126, 44),   // wooden slab -> stone slab (wood variant)
            Map.entry(127, 106),  // cocoa -> vines (the other surface plant)
            Map.entry(128, 53),   // sandstone stairs -> oak stairs
            Map.entry(129, 56),   // emerald ore -> diamond ore (the other gem ore)
            Map.entry(130, 54),   // ender chest -> chest
            Map.entry(131, 69),   // tripwire hook -> lever (the other wall switch)
            Map.entry(132, 0),    // tripwire -> air (a strung line is near-invisible)
            Map.entry(133, 57),   // emerald block -> diamond block (the other gem block)
            Map.entry(134, 53),   // spruce wood stairs -> oak stairs
            Map.entry(135, 53),   // birch wood stairs -> oak stairs
            Map.entry(136, 53));  // jungle wood stairs -> oak stairs

    /** Item IDs added in 1.3, approximated for 29 (after the 39 map). */
    private static final Map<Integer, Integer> ITEM_ID_39_TO_29 = Map.of(
            386, 340,  // book and quill -> book (the text has no 1.2.5 home)
            387, 340,  // written book -> book
            388, 264); // emerald -> diamond (1.2.5's gem item; its own
                       // internal name in the 1.2.5 jar is "emerald")

    /**
     * The meta a protocol-5 block keeps toward 29: the 39 layer's
     * meta rules, then the wooden-slab rule — protocol 29's stone
     * slabs have a wood-textured variant at meta 2, so the wooden
     * slabs land on it (a top-half wooden slab keeps its top bit).
     */
    private static int meta5To29(int id, int meta) {
        int m = meta5To39(id, meta);
        if (id == 125) {
            m = 2; // double wooden slab -> wood-variant double stone slab
        } else if (id == 126) {
            m = (m & 8) | 2; // wooden slab -> wood-variant stone slab
        }
        return m;
    }

    /** Full (id, meta) approximation of a protocol-5 block for 29. */
    public static int[] blockIdAndMeta5To29(int id, int meta) {
        int[] r = blockIdAndMeta5To39(id, meta);
        int out = BLOCK_ID_39_TO_29.getOrDefault(r[0], r[0]);
        return new int[]{out, meta5To29(id, meta)};
    }

    /** Remaps one (id << 4) | meta block value for 29. */
    public static int blockValue5To29(int value) {
        int[] r = blockIdAndMeta5To29(value >> 4, value & 15);
        return (r[0] << 4) | r[1];
    }

    /** Item ID remap for slots toward 29. */
    public static int itemId5To29(int itemId) {
        int out = itemId5To39(itemId);
        if (out < 256) {
            // Block items ride the block map.
            return BLOCK_ID_39_TO_29.getOrDefault(out, out);
        }
        return ITEM_ID_39_TO_29.getOrDefault(out, out);
    }

    /** ID-only step used inside chunk payloads (meta handled apart). */
    private static int chunkBlockId5To29(int id) {
        int out = chunkBlockId5To39(id);
        return BLOCK_ID_39_TO_29.getOrDefault(out, out);
    }

    // ------------------------------------------------------------------
    // Protocol 28 (1.2.3 / 1.2.2 / 1.2.1) targets: the block, item
    // and entity registries are IDENTICAL to 29 (measured in the
    // real jars, docs/PROXY-1.2.3.md) — the only 29-only trait is
    // the abilities packet, which lives in the packet table, not
    // here. The 28 layer therefore delegates to the 29 layer
    // outright.
    // ------------------------------------------------------------------

    /** Full (id, meta) approximation of a protocol-5 block for 28. */
    public static int[] blockIdAndMeta5To28(int id, int meta) {
        return blockIdAndMeta5To29(id, meta);
    }

    /** Remaps one (id << 4) | meta block value for 28. */
    public static int blockValue5To28(int value) {
        return blockValue5To29(value);
    }

    /** Item ID remap for slots toward 28. */
    public static int itemId5To28(int itemId) {
        return itemId5To29(itemId);
    }

    /** ID-only step used inside chunk payloads (meta handled apart). */
    private static int chunkBlockId5To28(int id) {
        return chunkBlockId5To29(id);
    }

    // ------------------------------------------------------------------
    // Protocol 23 (1.1) targets: the blocks and items added in 1.2
    // (docs/PROXY-1.1.md). Applied AFTER the 28 layer above (the
    // 1.2.3 registries minus the 1.2 additions are exactly the 1.1
    // registries, measured in the real jars); the meta is
    // preserved (the glowstone approximation ignores it).
    // ------------------------------------------------------------------

    /** Legacy-28 block ID -> protocol-23 block ID (1.2 additions). */
    public static final Map<Integer, Integer> BLOCK_ID_28_TO_23 = Map.of(
            123, 89,   // redstone lamp (off) -> glowstone (the other light-15 block)
            124, 89);  // redstone lamp (on) -> glowstone

    /** Item IDs added in 1.2, approximated for 23 (after the 28 map). */
    private static final Map<Integer, Integer> ITEM_ID_28_TO_23 = Map.of(
            384, 373,  // bottle o' enchanting -> potion (the other throw-able bottle)
            385, 259); // fire charge -> flint and steel (the other fire starter)

    /** Full (id, meta) approximation of a protocol-5 block for 23. */
    public static int[] blockIdAndMeta5To23(int id, int meta) {
        int[] r = blockIdAndMeta5To28(id, meta);
        int out = BLOCK_ID_28_TO_23.getOrDefault(r[0], r[0]);
        return new int[]{out, r[1]};
    }

    /** Remaps one (id << 4) | meta block value for 23. */
    public static int blockValue5To23(int value) {
        int[] r = blockIdAndMeta5To23(value >> 4, value & 15);
        return (r[0] << 4) | r[1];
    }

    /** Item ID remap for slots toward 23. */
    public static int itemId5To23(int itemId) {
        int out = itemId5To28(itemId);
        if (out < 256) {
            // Block items ride the block map.
            return BLOCK_ID_28_TO_23.getOrDefault(out, out);
        }
        return ITEM_ID_28_TO_23.getOrDefault(out, out);
    }

    /** ID-only step used inside chunk payloads (meta handled apart). */
    private static int chunkBlockId5To23(int id) {
        int out = chunkBlockId5To28(id);
        return BLOCK_ID_28_TO_23.getOrDefault(out, out);
    }

    // ------------------------------------------------------------------
    // Protocol 22 (1.0.0) targets: blocks and entities are
    // identical to 23 (measured, docs/PROXY-1.0.md), so the block
    // approximations delegate to the 23 layer outright; only the
    // one item added in 1.1 needs a further step.
    // ------------------------------------------------------------------

    /** The one item added in 1.1, approximated for 22 (after the 23 map). */
    private static final Map<Integer, Integer> ITEM_ID_23_TO_22 = Map.of(
            383, 339); // spawn egg -> paper

    /** Full (id, meta) approximation of a protocol-5 block for 22. */
    public static int[] blockIdAndMeta5To22(int id, int meta) {
        return blockIdAndMeta5To23(id, meta);
    }

    /** Remaps one (id << 4) | meta block value for 22. */
    public static int blockValue5To22(int value) {
        return blockValue5To23(value);
    }

    /** Item ID remap for slots toward 22. */
    public static int itemId5To22(int itemId) {
        int out = itemId5To23(itemId);
        return ITEM_ID_23_TO_22.getOrDefault(out, out);
    }

    /** ID-only step used inside chunk payloads (meta handled apart). */
    private static int chunkBlockId5To22(int id) {
        return chunkBlockId5To23(id);
    }

    /** Remaps one (id << 4) | meta block value; identity when unmapped. */
    public static int blockValue5To78(int value) {
        int id = value >> 4;
        Integer mapped = BLOCK_ID_5_TO_78.get(id);
        if (mapped == null) {
            return value;
        }
        return (mapped << 4) | (value & 15);
    }

    private LegacyChunkRemap() {
    }

    // ------------------------------------------------------------------
    // Chunk payloads
    // ------------------------------------------------------------------

    /**
     * Remaps the compressed section data of a single chunk (the
     * byte[] field of a chunk_data packet). Returns the original array
     * when nothing needed remapping or the layout does not parse.
     */
    public static byte[] remapSingleChunkData(byte[] compressed, int primaryBitmap,
                                                int addBitmap, boolean groundUp) {
        return remapSingleChunkData(compressed, primaryBitmap, addBitmap, groundUp,
                LegacyProtocol.V1_6_4);
    }

    /** As above, remapping toward the given legacy protocol. */
    public static byte[] remapSingleChunkData(byte[] compressed, int primaryBitmap,
                                                int addBitmap, boolean groundUp,
                                                int legacyProtocol) {
        primaryBitmap &= 0xFFFF;
        addBitmap &= 0xFFFF;
        try {
            byte[] raw = inflate(compressed);
            int sections = Integer.bitCount(primaryBitmap);
            int adds = Integer.bitCount(addBitmap);
            // Sky-light presence is not flagged in a single-chunk
            // packet; decide from the exact inflated length.
            int base = sections * (4096 + 2048 + 2048) + adds * 2048 + (groundUp ? 256 : 0);
            boolean sky;
            if (raw.length == base + sections * 2048) {
                sky = true;
            } else if (raw.length == base) {
                sky = false;
            } else {
                return compressed;
            }
            if (!remapSections(raw, sections, primaryBitmap, addBitmap, sky,
                    legacyProtocol)) {
                return compressed;
            }
            return deflate(raw);
        } catch (IllegalArgumentException e) {
            return compressed;
        }
    }

    /**
     * Remaps a whole bulk payload's compressed data. Returns the new
     * compressed bytes, or null when nothing changed / unparsable.
     */
    public static byte[] remapBulkData(byte[] compressed, boolean skyLight,
                                       List<int[]> meta) {
        return remapBulkData(compressed, skyLight, meta, LegacyProtocol.V1_6_4);
    }

    /** As above, remapping toward the given legacy protocol. */
    public static byte[] remapBulkData(byte[] compressed, boolean skyLight,
                                       List<int[]> meta, int legacyProtocol) {
        try {
            byte[] raw = inflate(compressed);
            boolean changed = false;
            int offset = 0;
            for (int[] m : meta) {
                int primary = m[2] & 0xFFFF;
                int add = m[3] & 0xFFFF;
                int sections = Integer.bitCount(primary);
                int adds = Integer.bitCount(add);
                int chunkLength = sections * (4096 + 2048 + 2048)
                        + (skyLight ? sections * 2048 : 0) + adds * 2048 + 256;
                if (offset + chunkLength > raw.length) {
                    return null;
                }
                byte[] chunkRaw = new byte[chunkLength];
                System.arraycopy(raw, offset, chunkRaw, 0, chunkLength);
                if (remapSections(chunkRaw, sections, primary, add, skyLight,
                        legacyProtocol)) {
                    System.arraycopy(chunkRaw, 0, raw, offset, chunkLength);
                    changed = true;
                }
                offset += chunkLength;
            }
            if (!changed || offset != raw.length) {
                return null;
            }
            return deflate(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * A chunk converted to the cuboid form protocols 23 (1.1) and
     * 22 (1.0.0) use: the y coordinate of the cuboid's base, its
     * height in blocks (a multiple of 16) and the compressed
     * payload. The x / z come from the chunk packet itself; the
     * footprint is always the full 16x16 column.
     */
    public record CuboidData(int yBase, int sizeY, byte[] compressedData) {
    }

    /**
     * Converts one chunk's compressed section data (the protocol-5
     * grouped-by-kind layout) into the cuboid payload of protocols
     * 23 / 22: block ids for the whole cuboid, then metadata,
     * block-light and sky-light nibble arrays, each indexed
     * {@code (lx * 16 + lz) * sizeY + ly} (y fastest — measured in
     * the real 1.1 / 1.0.0 jars and captures, docs/PROXY-1.1.md).
     * Block IDs and metas are remapped toward the target protocol
     * on the way. The cuboid world is 128 high, so sections 8..15
     * (y 128..255) are dropped; the biome array has no cuboid home
     * either (23 / 22 clients compute biomes from the world seed).
     * A gap section inside the kept range becomes air with zero
     * light (real 1.1 servers send full columns, so this is a
     * corner case). Returns null when the payload does not parse
     * or no section survives below y 128.
     */
    public static CuboidData remapChunkToCuboid(byte[] compressed, int primaryBitmap,
                                                int addBitmap, boolean groundUp,
                                                int legacyProtocol) {
        primaryBitmap &= 0xFFFF;
        addBitmap &= 0xFFFF;
        try {
            byte[] raw = inflate(compressed);
            int sections = Integer.bitCount(primaryBitmap);
            int adds = Integer.bitCount(addBitmap);
            // Sky-light presence is not flagged in a single-chunk
            // packet; decide from the exact inflated length.
            int base = sections * (4096 + 2048 + 2048) + adds * 2048 + (groundUp ? 256 : 0);
            boolean sky;
            if (raw.length == base + sections * 2048) {
                sky = true;
            } else if (raw.length == base) {
                sky = false;
            } else {
                return null;
            }
            byte[] work = raw.clone();
            remapSections(work, sections, primaryBitmap, addBitmap, sky, legacyProtocol);
            int present = primaryBitmap & 0xFF;
            if (present == 0) {
                return null;
            }
            int first = Integer.numberOfTrailingZeros(present);
            int last = 31 - Integer.numberOfLeadingZeros(present);
            int sizeY = (last - first + 1) * 16;
            int total = 16 * sizeY * 16;
            byte[] ids = new byte[total];
            byte[] meta = new byte[total];
            byte[] blockLight = new byte[total];
            byte[] skyLight = new byte[total];
            int metaOff = sections * 4096;
            int blockLightOff = metaOff + sections * 2048;
            int skyOff = blockLightOff + sections * 2048;
            for (int s = first; s <= last; s++) {
                if ((primaryBitmap & (1 << s)) == 0) {
                    continue;
                }
                int si = Integer.bitCount(primaryBitmap & ((1 << s) - 1));
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            int src = ly * 256 + lz * 16 + lx;
                            int dst = (lx * 16 + lz) * sizeY + (s - first) * 16 + ly;
                            ids[dst] = work[si * 4096 + src];
                            meta[dst] = (byte) nibble(work, metaOff + si * 2048, src);
                            blockLight[dst] = (byte) nibble(work,
                                    blockLightOff + si * 2048, src);
                            skyLight[dst] = sky
                                    ? (byte) nibble(work, skyOff + si * 2048, src) : 0;
                        }
                    }
                }
            }
            int nibbleBytes = (total + 1) / 2;
            byte[] out = new byte[total + 3 * nibbleBytes];
            System.arraycopy(ids, 0, out, 0, total);
            packNibbles(meta, out, total);
            packNibbles(blockLight, out, total + nibbleBytes);
            packNibbles(skyLight, out, total + 2 * nibbleBytes);
            return new CuboidData(first * 16, sizeY, deflate(out));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Packs per-block nibble values (one per byte) into nibble pairs. */
    private static void packNibbles(byte[] values, byte[] out, int outOffset) {
        for (int i = 0; i < values.length; i++) {
            int v = values[i] & 0xF;
            if ((i & 1) == 0) {
                out[outOffset + i / 2] |= (byte) v;
            } else {
                out[outOffset + i / 2] |= (byte) (v << 4);
            }
        }
    }

    /**
     * Rewrites block IDs inside one chunk's inflated section data
     * (grouped-by-kind layout). Returns true when any ID changed.
     */
    private static boolean remapSections(byte[] raw, int sections,
                                         int primaryBitmap, int addBitmap,
                                         boolean skyLight, int legacyProtocol) {
        boolean to61 = LegacyProtocol.is1_5Generation(legacyProtocol);
        boolean to51 = LegacyProtocol.is1_4Generation(legacyProtocol);
        boolean to39 = LegacyProtocol.is1_3Generation(legacyProtocol);
        boolean to29 = LegacyProtocol.is1_2Generation(legacyProtocol);
        boolean to23 = legacyProtocol == LegacyProtocol.V1_1;
        boolean to22 = legacyProtocol == LegacyProtocol.V1_0;
        boolean changed = false;
        int addOffset = sections * (4096 + 2048 + 2048) + (skyLight ? sections * 2048 : 0);
        int metaOffset = sections * 4096;
        int addIndex = 0;
        for (int i = 0; i < sections; i++) {
            int section = nthSection(primaryBitmap, i);
            boolean hasAdd = (addBitmap & (1 << section)) != 0;
            for (int j = 0; j < 4096; j++) {
                int id = raw[i * 4096 + j] & 0xFF;
                if (hasAdd) {
                    id |= nibble(raw, addOffset + addIndex * 2048, j) << 8;
                }
                Integer mapped = BLOCK_ID_5_TO_78.get(id);
                if (to22) {
                    int target = chunkBlockId5To22(id);
                    if (target != id) {
                        raw[i * 4096 + j] = (byte) target;
                        if (hasAdd) {
                            setNibble(raw, addOffset + addIndex * 2048, j, 0);
                        }
                        changed = true;
                    }
                    int curMeta = nibble(raw, metaOffset + i * 2048, j);
                    int newMeta = meta5To29(id, curMeta);
                    if (newMeta != curMeta) {
                        setNibble(raw, metaOffset + i * 2048, j, newMeta);
                        changed = true;
                    }
                } else if (to23) {
                    int target = chunkBlockId5To23(id);
                    if (target != id) {
                        raw[i * 4096 + j] = (byte) target;
                        if (hasAdd) {
                            setNibble(raw, addOffset + addIndex * 2048, j, 0);
                        }
                        changed = true;
                    }
                    int curMeta = nibble(raw, metaOffset + i * 2048, j);
                    int newMeta = meta5To29(id, curMeta);
                    if (newMeta != curMeta) {
                        setNibble(raw, metaOffset + i * 2048, j, newMeta);
                        changed = true;
                    }
                } else if (to29) {
                    int target = chunkBlockId5To29(id);
                    if (target != id) {
                        raw[i * 4096 + j] = (byte) target;
                        if (hasAdd) {
                            setNibble(raw, addOffset + addIndex * 2048, j, 0);
                        }
                        changed = true;
                    }
                    int curMeta = nibble(raw, metaOffset + i * 2048, j);
                    int newMeta = meta5To29(id, curMeta);
                    if (newMeta != curMeta) {
                        setNibble(raw, metaOffset + i * 2048, j, newMeta);
                        changed = true;
                    }
                } else if (to39) {
                    int target = chunkBlockId5To39(id);
                    if (target != id) {
                        raw[i * 4096 + j] = (byte) target;
                        if (hasAdd) {
                            setNibble(raw, addOffset + addIndex * 2048, j, 0);
                        }
                        changed = true;
                    }
                    int curMeta = nibble(raw, metaOffset + i * 2048, j);
                    int newMeta = meta5To39(id, curMeta);
                    if (newMeta != curMeta) {
                        setNibble(raw, metaOffset + i * 2048, j, newMeta);
                        changed = true;
                    }
                } else if (to51) {
                    int target = chunkBlockId5To51(id);
                    if (target != id) {
                        raw[i * 4096 + j] = (byte) target;
                        if (hasAdd) {
                            setNibble(raw, addOffset + addIndex * 2048, j, 0);
                        }
                        changed = true;
                    }
                    int curMeta = nibble(raw, metaOffset + i * 2048, j);
                    int newMeta = meta5To51(id, curMeta);
                    if (newMeta != curMeta) {
                        setNibble(raw, metaOffset + i * 2048, j, newMeta);
                        changed = true;
                    }
                } else if (to61) {
                    int target = chunkBlockId5To61(id);
                    Integer forced = FORCED_META_61.get(id);
                    if (target != id) {
                        raw[i * 4096 + j] = (byte) target;
                        if (hasAdd) {
                            setNibble(raw, addOffset + addIndex * 2048, j, 0);
                        }
                        changed = true;
                    }
                    if (forced != null) {
                        setNibble(raw, metaOffset + i * 2048, j, forced);
                        changed = true;
                    }
                } else if (mapped != null) {
                    raw[i * 4096 + j] = (byte) (int) mapped;
                    if (hasAdd) {
                        setNibble(raw, addOffset + addIndex * 2048, j, 0);
                    }
                    changed = true;
                }
            }
            if (hasAdd) {
                addIndex++;
            }
        }
        return changed;
    }

    private static int nthSection(int bitmap, int n) {
        for (int s = 0; s < 16; s++) {
            if ((bitmap & (1 << s)) != 0 && n-- == 0) {
                return s;
            }
        }
        return -1;
    }

    private static int nibble(byte[] data, int arrayOffset, int index) {
        int b = data[arrayOffset + index / 2] & 0xFF;
        return (index & 1) == 0 ? (b & 0xF) : ((b >> 4) & 0xF);
    }

    private static void setNibble(byte[] data, int arrayOffset, int index, int value) {
        int at = arrayOffset + index / 2;
        int b = data[at] & 0xFF;
        if ((index & 1) == 0) {
            b = (b & 0xF0) | (value & 0xF);
        } else {
            b = (b & 0x0F) | ((value & 0xF) << 4);
        }
        data[at] = (byte) b;
    }

    public static byte[] inflate(byte[] compressed) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(tmp);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("truncated zlib stream");
                }
                out.write(tmp, 0, n);
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new IllegalArgumentException("bad zlib stream", e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    public static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(tmp);
            out.write(tmp, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }
}
