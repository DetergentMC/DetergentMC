package io.detergentmc.api;

/**
 * Version-independent block identifiers.
 *
 * <p>A Bubble talks about {@code BlockType.CHERRY_LOG} and the version
 * adapter decides what that means on the running version - on 1.20.4 it is
 * {@code minecraft:cherry_log}, on an older version without cherry wood
 * the adapter reports it as unsupported and the protocol layer can fall
 * back to another block plus a texture. The {@code since} value records
 * the Minecraft version that introduced the block, so adapters and the
 * protocol layer can reason about availability.
 */
public enum BlockType {
    STONE("stone", "1.0"),
    GRASS_BLOCK("grass_block", "1.0"),
    DIRT("dirt", "1.0"),
    COBBLESTONE("cobblestone", "1.0"),
    OAK_LOG("oak_log", "1.0"),
    OAK_PLANKS("oak_planks", "1.0"),
    SAND("sand", "1.0"),
    SLIME_BLOCK("slime_block", "1.8"),
    GLASS("glass", "1.0"),
    IRON_BLOCK("iron_block", "1.0"),
    GOLD_BLOCK("gold_block", "1.0"),
    DIAMOND_BLOCK("diamond_block", "1.0"),
    OBSIDIAN("obsidian", "1.0"),
    AMETHYST_BLOCK("amethyst_block", "1.17"),
    CHERRY_LOG("cherry_log", "1.20"),
    CHERRY_PLANKS("cherry_planks", "1.20"),
    BAMBOO_BLOCK("bamboo_block", "1.20"),
    SUSPICIOUS_SAND("suspicious_sand", "1.20"),
    CALIBRATED_SCULK_SENSOR("calibrated_sculk_sensor", "1.20");

    private final String id;
    private final String since;

    BlockType(String id, String since) {
        this.id = id;
        this.since = since;
    }

    /** Unified id, e.g. {@code "cherry_log"}. */
    public String id() {
        return id;
    }

    /** Minecraft version that introduced this block, e.g. {@code "1.20"}. */
    public String since() {
        return since;
    }

    /** Full Minecraft id, e.g. {@code "minecraft:cherry_log"}. */
    public String minecraftId() {
        return "minecraft:" + id;
    }
}
