package io.detergentmc.adapter.v1_16;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V116AdapterTest {

    private final V116Adapter adapter = new V116Adapter();

    @Test
    void identifiesItselfAs116() {
        assertEquals("1.16", adapter.minecraftVersion());
        assertEquals(735, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn116() {
        // 19 unified blocks known today. Amethyst (1.17) and the 5
        // introduced in 1.20 (cherry log/planks, bamboo block,
        // suspicious sand, calibrated sculk sensor) do not exist in
        // 1.16, so 13 of them map. The count is 13, the same as
        // 1.16.1 - no unified block has since 1.16, 1.16.1, 1.16.2,
        // 1.16.3, 1.16.4 or 1.16.5. Counted from BlockType.since(), not guessed.
        assertEquals(19, BlockType.values().length);
        int supported = 0;
        Set<BlockType> unsupported = EnumSet.noneOf(BlockType.class);
        for (BlockType block : BlockType.values()) {
            if (adapter.supports(block)) {
                supported++;
                assertEquals(block.minecraftId(),
                        adapter.toMinecraftId(block).orElseThrow());
            } else {
                unsupported.add(block);
            }
        }
        assertEquals(13, supported);
        assertEquals(Set.of(BlockType.AMETHYST_BLOCK, BlockType.CHERRY_LOG,
                BlockType.CHERRY_PLANKS, BlockType.BAMBOO_BLOCK,
                BlockType.SUSPICIOUS_SAND, BlockType.CALIBRATED_SCULK_SENSOR),
                unsupported);
        assertFalse(adapter.toMinecraftId(BlockType.AMETHYST_BLOCK).isPresent());
        assertEquals("minecraft:stone",
                adapter.toMinecraftId(BlockType.STONE).orElseThrow());
    }

    @Test
    void blocksIntroducedAfter116AreUnsupported() {
        // Amethyst (1.17) and the 1.20 additions are in the unified enum
        // but must not map in 1.16 - toMinecraftId has to answer empty
        // for them, and the new ids must not be silently mapped to a
        // wrong block.
        for (BlockType block : new BlockType[]{BlockType.AMETHYST_BLOCK,
                BlockType.CHERRY_LOG, BlockType.CHERRY_PLANKS,
                BlockType.BAMBOO_BLOCK, BlockType.SUSPICIOUS_SAND,
                BlockType.CALIBRATED_SCULK_SENSOR}) {
            assertFalse(adapter.supports(block), "1.16 should not support " + block);
            assertFalse(adapter.toMinecraftId(block).isPresent());
        }
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.minecraftId().equals("minecraft:amethyst_block")
                        && adapter.supports(block)),
                "amethyst_block must not map in 1.16");
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.minecraftId().equals("minecraft:cherry_log")
                        && adapter.supports(block)),
                "cherry_log must not map in 1.16");
    }

    @Test
    void versionComparisonDecidesAvailability() {
        assertTrue(V116Adapter.compareVersions("1.16", "1.17") < 0);
        assertEquals(0, V116Adapter.compareVersions("1.16", "1.16"));
        assertTrue(V116Adapter.compareVersions("1.17", "1.16") > 0);
        assertTrue(V116Adapter.compareVersions("1.20", "1.16") > 0);
        assertTrue(V116Adapter.availableIn116(BlockType.STONE),
                "a 1.0 block exists in 1.16");
        assertTrue(V116Adapter.availableIn116(BlockType.SLIME_BLOCK),
                "a 1.8 block exists in 1.16");
        assertFalse(V116Adapter.availableIn116(BlockType.AMETHYST_BLOCK),
                "a 1.17 block does not exist in 1.16");
        assertFalse(V116Adapter.availableIn116(BlockType.CHERRY_LOG),
                "a 1.20 block does not exist in 1.16");
    }

    @Test
    void noUnifiedBlockIntroducedIn116To1165() {
        // Why 1.16 maps exactly like 1.16.1 for blocks: no unified
        // block has since 1.16, 1.16.1, 1.16.2, 1.16.3, 1.16.4 or 1.16.5, so the
        // 1.16 availability check already decides 1.16.1. If one is added
        // later, the 13-block count in mapsEveryBlockThatExistsIn116
        // must be revisited. Counted from BlockType.since(), not guessed.
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.since().equals("1.16")
                        || block.since().equals("1.16.1")
                        || block.since().equals("1.16.2")
                        || block.since().equals("1.16.3")
                        || block.since().equals("1.16.4")
                        || block.since().equals("1.16.5")),
                "no unified block has since 1.16, 1.16.1, 1.16.2, 1.16.3, 1.16.4 or 1.16.5");
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found116 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.16")
                        && candidate.protocolVersion() == 735
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_16.V116Adapter"));
        assertTrue(found116, "Expected V116Adapter via ServiceLoader");
    }
}
