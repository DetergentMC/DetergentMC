package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimpleBlockTranslatorTest {

    private final SimpleBlockTranslator translator = new SimpleBlockTranslator();

    @Test
    void newBlocksPassThroughOnNewClients() {
        assertEquals("minecraft:cherry_log",
                translator.translateBlock("minecraft:cherry_log", ProtocolVersion.V1_20_4));
        assertTrue(translator.supports("minecraft:cherry_log", ProtocolVersion.V1_20_4));
    }

    @Test
    void newBlocksFallBackOnOldClients() {
        // A 1.12.2 client (protocol 340) has no cherry wood.
        assertEquals("minecraft:oak_log",
                translator.translateBlock("minecraft:cherry_log", ProtocolVersion.V1_12_2));
        assertFalse(translator.supports("minecraft:cherry_log", ProtocolVersion.V1_12_2));
        // Amethyst (1.17) is unknown to a 1.16.5 client.
        assertEquals("minecraft:quartz_block",
                translator.translateBlock("minecraft:amethyst_block", ProtocolVersion.V1_16_5));
        assertEquals("minecraft:amethyst_block",
                translator.translateBlock("minecraft:amethyst_block", ProtocolVersion.V1_17));
    }

    @Test
    void blocksAddedIn1203FallBackFor1201Clients() {
        // 1.20.3で追加されたブロックは1.20.1クライアント（763）には無い。
        assertEquals("minecraft:crafting_table",
                translator.translateBlock("minecraft:crafter", ProtocolVersion.V1_20_1));
        assertFalse(translator.supports("minecraft:crafter", ProtocolVersion.V1_20_1));
        assertEquals("minecraft:spawner",
                translator.translateBlock("minecraft:trial_spawner", ProtocolVersion.V1_20_1));
        assertFalse(translator.supports("minecraft:trial_spawner", ProtocolVersion.V1_20_1));
        // 1.20.3で改名された short_grass は、763向けには旧名 grass で送る。
        assertEquals("minecraft:grass",
                translator.translateBlock("minecraft:short_grass", ProtocolVersion.V1_20_1));
        assertFalse(translator.supports("minecraft:short_grass", ProtocolVersion.V1_20_1));
    }

    @Test
    void blocksAddedIn1203PassThroughOn1204Clients() {
        assertEquals("minecraft:crafter",
                translator.translateBlock("minecraft:crafter", ProtocolVersion.V1_20_4));
        assertTrue(translator.supports("minecraft:crafter", ProtocolVersion.V1_20_4));
        assertEquals("minecraft:trial_spawner",
                translator.translateBlock("minecraft:trial_spawner", ProtocolVersion.V1_20_4));
        assertEquals("minecraft:short_grass",
                translator.translateBlock("minecraft:short_grass", ProtocolVersion.V1_20_4));
    }

    @Test
    void blocksAddedIn1203FallBackFor1202Clients() {
        // 1.20.3で追加されたブロックは1.20.2クライアント（764）にも無い。
        // ProtocolVersion に V1_20_2 が無いため番号 764 を直接使う。
        int v1202 = 764;
        assertEquals("minecraft:crafting_table",
                translator.translateBlock("minecraft:crafter", v1202));
        assertFalse(translator.supports("minecraft:crafter", v1202));
        assertEquals("minecraft:spawner",
                translator.translateBlock("minecraft:trial_spawner", v1202));
        assertEquals("minecraft:grass",
                translator.translateBlock("minecraft:short_grass", v1202));
        assertEquals("minecraft:stone_slab",
                translator.translateBlock("minecraft:tuff_slab", v1202));
        assertEquals("minecraft:stone_stairs",
                translator.translateBlock("minecraft:tuff_stairs", v1202));
        assertEquals("minecraft:stone_brick_wall",
                translator.translateBlock("minecraft:tuff_wall", v1202));
        assertEquals("minecraft:tuff",
                translator.translateBlock("minecraft:polished_tuff", v1202));
        assertEquals("minecraft:stone_bricks",
                translator.translateBlock("minecraft:tuff_bricks", v1202));
        assertEquals("minecraft:chiseled_stone_bricks",
                translator.translateBlock("minecraft:chiseled_tuff", v1202));
        assertEquals("minecraft:copper_block",
                translator.translateBlock("minecraft:chiseled_copper", v1202));
        assertEquals("minecraft:iron_door",
                translator.translateBlock("minecraft:copper_door", v1202));
        assertEquals("minecraft:iron_trapdoor",
                translator.translateBlock("minecraft:waxed_oxidized_copper_trapdoor", v1202));
        assertEquals("minecraft:iron_bars",
                translator.translateBlock("minecraft:copper_grate", v1202));
        assertEquals("minecraft:glowstone",
                translator.translateBlock("minecraft:weathered_copper_bulb", v1202));
        // 1.20.2 からあるブロックはそのまま通す。
        assertEquals("minecraft:tuff",
                translator.translateBlock("minecraft:tuff", v1202));
        assertTrue(translator.supports("minecraft:tuff", v1202));
    }

    @Test
    void oldBlocksPassThroughEverywhere() {
        assertEquals("minecraft:stone",
                translator.translateBlock("minecraft:stone", ProtocolVersion.V1_8));
        assertTrue(translator.supports("minecraft:stone", ProtocolVersion.V1_8));
    }

    @Test
    void protocolTranslatorKnowsWhenAPackIsNeeded() {
        ProtocolTranslator protocol = new ProtocolTranslator();
        assertTrue(protocol.needsResourcePack(ProtocolVersion.V1_12_2));
        assertFalse(protocol.needsResourcePack(ProtocolVersion.V1_20_4));
    }
}
