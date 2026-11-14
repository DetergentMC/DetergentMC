package io.detergentmc.protocol;

import java.util.Map;

/**
 * Table-driven {@link BlockTranslator}.
 *
 * <p>{@code INTRODUCED_IN} records the protocol version that added each
 * newer block; {@code FALLBACKS} records what an older client should see
 * instead. Blocks not listed here are passed through unchanged.
 */
public final class SimpleBlockTranslator implements BlockTranslator {

    /** Protocol version that introduced the block (by server block id). */
    private static final Map<String, Integer> INTRODUCED_IN = Map.ofEntries(
            Map.entry("minecraft:cherry_log", ProtocolVersion.V1_20_1),
            Map.entry("minecraft:cherry_planks", ProtocolVersion.V1_20_1),
            Map.entry("minecraft:bamboo_block", ProtocolVersion.V1_20_1),
            Map.entry("minecraft:suspicious_sand", ProtocolVersion.V1_20_1),
            Map.entry("minecraft:calibrated_sculk_sensor", ProtocolVersion.V1_20_1),
            Map.entry("minecraft:amethyst_block", ProtocolVersion.V1_17), // 1.17
            // 1.20.3 (protocol 765) additions and renames. The crafter and
            // the trial spawner arrived as experimental 1.21 content in
            // 1.20.3, but they are in the 1.20.4 registry, so a 1.20.1
            // client (763) cannot display them. short_grass is the
            // 1.20.3 rename of the 1.20.1 block "grass".
            Map.entry("minecraft:crafter", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:trial_spawner", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:short_grass", ProtocolVersion.V1_20_4),
            // 1.20.3 のその他の新ブロック（tuff 系・copper 系）。
            // 764（1.20.2）・763（1.20.1）のクライアントには無い。
            Map.entry("minecraft:tuff_slab", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:tuff_stairs", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:tuff_wall", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:polished_tuff", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:polished_tuff_slab", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:polished_tuff_stairs", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:polished_tuff_wall", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:tuff_bricks", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:tuff_brick_slab", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:tuff_brick_stairs", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:tuff_brick_wall", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:chiseled_tuff", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:chiseled_tuff_bricks", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:chiseled_copper", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:exposed_chiseled_copper", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:weathered_chiseled_copper", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:oxidized_chiseled_copper", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_chiseled_copper", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_exposed_chiseled_copper", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_weathered_chiseled_copper", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_oxidized_chiseled_copper", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:copper_door", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:exposed_copper_door", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:weathered_copper_door", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:oxidized_copper_door", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_copper_door", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_exposed_copper_door", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_weathered_copper_door", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_oxidized_copper_door", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:copper_trapdoor", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:exposed_copper_trapdoor", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:weathered_copper_trapdoor", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:oxidized_copper_trapdoor", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_copper_trapdoor", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_exposed_copper_trapdoor", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_weathered_copper_trapdoor", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_oxidized_copper_trapdoor", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:copper_grate", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:exposed_copper_grate", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:weathered_copper_grate", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:oxidized_copper_grate", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_copper_grate", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_exposed_copper_grate", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_weathered_copper_grate", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_oxidized_copper_grate", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:copper_bulb", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:exposed_copper_bulb", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:weathered_copper_bulb", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:oxidized_copper_bulb", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_copper_bulb", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_exposed_copper_bulb", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_weathered_copper_bulb", ProtocolVersion.V1_20_4),
            Map.entry("minecraft:waxed_oxidized_copper_bulb", ProtocolVersion.V1_20_4)
    );

    /** What an older client sees instead of a block it does not have. */
    private static final Map<String, String> FALLBACKS = Map.ofEntries(
            Map.entry("minecraft:cherry_log", "minecraft:oak_log"),
            Map.entry("minecraft:cherry_planks", "minecraft:oak_planks"),
            Map.entry("minecraft:bamboo_block", "minecraft:oak_log"),
            Map.entry("minecraft:suspicious_sand", "minecraft:sand"),
            Map.entry("minecraft:calibrated_sculk_sensor", "minecraft:sculk_sensor"),
            Map.entry("minecraft:amethyst_block", "minecraft:quartz_block"),
            Map.entry("minecraft:crafter", "minecraft:crafting_table"),
            Map.entry("minecraft:trial_spawner", "minecraft:spawner"),
            Map.entry("minecraft:short_grass", "minecraft:grass"),
            Map.entry("minecraft:tuff_slab", "minecraft:stone_slab"),
            Map.entry("minecraft:tuff_stairs", "minecraft:stone_stairs"),
            Map.entry("minecraft:tuff_wall", "minecraft:stone_brick_wall"),
            Map.entry("minecraft:polished_tuff", "minecraft:tuff"),
            Map.entry("minecraft:polished_tuff_slab", "minecraft:stone_slab"),
            Map.entry("minecraft:polished_tuff_stairs", "minecraft:stone_stairs"),
            Map.entry("minecraft:polished_tuff_wall", "minecraft:stone_brick_wall"),
            Map.entry("minecraft:tuff_bricks", "minecraft:stone_bricks"),
            Map.entry("minecraft:tuff_brick_slab", "minecraft:stone_brick_slab"),
            Map.entry("minecraft:tuff_brick_stairs", "minecraft:stone_brick_stairs"),
            Map.entry("minecraft:tuff_brick_wall", "minecraft:stone_brick_wall"),
            Map.entry("minecraft:chiseled_tuff", "minecraft:chiseled_stone_bricks"),
            Map.entry("minecraft:chiseled_tuff_bricks", "minecraft:chiseled_stone_bricks"),
            Map.entry("minecraft:chiseled_copper", "minecraft:copper_block"),
            Map.entry("minecraft:exposed_chiseled_copper", "minecraft:copper_block"),
            Map.entry("minecraft:weathered_chiseled_copper", "minecraft:copper_block"),
            Map.entry("minecraft:oxidized_chiseled_copper", "minecraft:copper_block"),
            Map.entry("minecraft:waxed_chiseled_copper", "minecraft:copper_block"),
            Map.entry("minecraft:waxed_exposed_chiseled_copper", "minecraft:copper_block"),
            Map.entry("minecraft:waxed_weathered_chiseled_copper", "minecraft:copper_block"),
            Map.entry("minecraft:waxed_oxidized_chiseled_copper", "minecraft:copper_block"),
            Map.entry("minecraft:copper_door", "minecraft:iron_door"),
            Map.entry("minecraft:exposed_copper_door", "minecraft:iron_door"),
            Map.entry("minecraft:weathered_copper_door", "minecraft:iron_door"),
            Map.entry("minecraft:oxidized_copper_door", "minecraft:iron_door"),
            Map.entry("minecraft:waxed_copper_door", "minecraft:iron_door"),
            Map.entry("minecraft:waxed_exposed_copper_door", "minecraft:iron_door"),
            Map.entry("minecraft:waxed_weathered_copper_door", "minecraft:iron_door"),
            Map.entry("minecraft:waxed_oxidized_copper_door", "minecraft:iron_door"),
            Map.entry("minecraft:copper_trapdoor", "minecraft:iron_trapdoor"),
            Map.entry("minecraft:exposed_copper_trapdoor", "minecraft:iron_trapdoor"),
            Map.entry("minecraft:weathered_copper_trapdoor", "minecraft:iron_trapdoor"),
            Map.entry("minecraft:oxidized_copper_trapdoor", "minecraft:iron_trapdoor"),
            Map.entry("minecraft:waxed_copper_trapdoor", "minecraft:iron_trapdoor"),
            Map.entry("minecraft:waxed_exposed_copper_trapdoor", "minecraft:iron_trapdoor"),
            Map.entry("minecraft:waxed_weathered_copper_trapdoor", "minecraft:iron_trapdoor"),
            Map.entry("minecraft:waxed_oxidized_copper_trapdoor", "minecraft:iron_trapdoor"),
            Map.entry("minecraft:copper_grate", "minecraft:iron_bars"),
            Map.entry("minecraft:exposed_copper_grate", "minecraft:iron_bars"),
            Map.entry("minecraft:weathered_copper_grate", "minecraft:iron_bars"),
            Map.entry("minecraft:oxidized_copper_grate", "minecraft:iron_bars"),
            Map.entry("minecraft:waxed_copper_grate", "minecraft:iron_bars"),
            Map.entry("minecraft:waxed_exposed_copper_grate", "minecraft:iron_bars"),
            Map.entry("minecraft:waxed_weathered_copper_grate", "minecraft:iron_bars"),
            Map.entry("minecraft:waxed_oxidized_copper_grate", "minecraft:iron_bars"),
            Map.entry("minecraft:copper_bulb", "minecraft:glowstone"),
            Map.entry("minecraft:exposed_copper_bulb", "minecraft:glowstone"),
            Map.entry("minecraft:weathered_copper_bulb", "minecraft:glowstone"),
            Map.entry("minecraft:oxidized_copper_bulb", "minecraft:glowstone"),
            Map.entry("minecraft:waxed_copper_bulb", "minecraft:glowstone"),
            Map.entry("minecraft:waxed_exposed_copper_bulb", "minecraft:glowstone"),
            Map.entry("minecraft:waxed_weathered_copper_bulb", "minecraft:glowstone"),
            Map.entry("minecraft:waxed_oxidized_copper_bulb", "minecraft:glowstone")
    );

    @Override
    public String translateBlock(String serverBlockId, int clientProtocolVersion) {
        if (supports(serverBlockId, clientProtocolVersion)) {
            return serverBlockId;
        }
        return FALLBACKS.getOrDefault(serverBlockId, "minecraft:stone");
    }

    @Override
    public boolean supports(String serverBlockId, int clientProtocolVersion) {
        Integer introduced = INTRODUCED_IN.get(serverBlockId);
        return introduced == null || clientProtocolVersion >= introduced;
    }
}
