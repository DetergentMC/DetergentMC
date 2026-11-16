package io.detergentmc.adapter.v1_16_5;

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

class V1165AdapterTest {

    private final V1165Adapter adapter = new V1165Adapter();
    private final V1164Adapter adapter1164 = new V1164Adapter();

    @Test
    void identifiesItselfAs1165() {
        assertEquals("1.16.5", adapter.minecraftVersion());
        assertEquals(754, adapter.protocolVersion());
        assertEquals("1.16.4", adapter1164.minecraftVersion());
        assertEquals(754, adapter1164.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1165() {
        // 19 unified blocks known today. Amethyst (1.17) and the 5
        // introduced in 1.20 (cherry log/planks, bamboo block,
        // suspicious sand, calibrated sculk sensor) do not exist in
        // 1.16.5, so 13 of them map. The count is 13, not the 14 of
        // 1.17 - the one difference is the amethyst block, the only
        // unified block introduced in 1.17. Counted from
        // BlockType.since(), not guessed.
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
    void blocksIntroducedAfter1165AreUnsupported() {
        // Amethyst (1.17) and the 1.20 additions are in the unified enum
        // but must not map in 1.16.5 - toMinecraftId has to answer empty
        // for them, and the new ids must not be silently mapped to a
        // wrong block.
        for (BlockType block : new BlockType[]{BlockType.AMETHYST_BLOCK,
                BlockType.CHERRY_LOG, BlockType.CHERRY_PLANKS,
                BlockType.BAMBOO_BLOCK, BlockType.SUSPICIOUS_SAND,
                BlockType.CALIBRATED_SCULK_SENSOR}) {
            assertFalse(adapter.supports(block), "1.16.5 should not support " + block);
            assertFalse(adapter.toMinecraftId(block).isPresent());
            assertFalse(adapter1164.supports(block),
                    "1.16.4 should not support " + block);
        }
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.minecraftId().equals("minecraft:amethyst_block")
                        && adapter.supports(block)),
                "amethyst_block must not map in 1.16.5");
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.minecraftId().equals("minecraft:cherry_log")
                        && adapter.supports(block)),
                "cherry_log must not map in 1.16.5");
    }

    @Test
    void versionComparisonDecidesAvailability() {
        assertTrue(V1165Adapter.compareVersions("1.16.5", "1.17") < 0);
        assertEquals(0, V1165Adapter.compareVersions("1.16.5", "1.16.5"));
        assertTrue(V1165Adapter.compareVersions("1.17", "1.16.5") > 0);
        assertTrue(V1165Adapter.compareVersions("1.20", "1.16.5") > 0);
        assertTrue(V1165Adapter.availableIn1165(BlockType.STONE),
                "a 1.0 block exists in 1.16.5");
        assertTrue(V1165Adapter.availableIn1165(BlockType.SLIME_BLOCK),
                "a 1.8 block exists in 1.16.5");
        assertFalse(V1165Adapter.availableIn1165(BlockType.AMETHYST_BLOCK),
                "a 1.17 block does not exist in 1.16.5");
        assertFalse(V1165Adapter.availableIn1165(BlockType.CHERRY_LOG),
                "a 1.20 block does not exist in 1.16.5");
    }

    @Test
    void patch1164SharesProtocol754With1165() {
        // 1.16.4 answers exactly like 1.16.5 for blocks (same protocol
        // 754, byte-identical registries - the data-generator reports
        // from both jars are identical, Stage A note §7.4).
        for (BlockType block : BlockType.values()) {
            assertEquals(adapter.toMinecraftId(block),
                    adapter1164.toMinecraftId(block),
                    "1.16.4 should answer like 1.16.5 for " + block);
        }
        // Why the sharing is exact: no unified block has since 1.16.4
        // or 1.16.5, so the 1.16.5 availability check already decides
        // 1.16.4. If one is added later, V1164Adapter must be revisited.
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.since().equals("1.16.4")
                        || block.since().equals("1.16.5")),
                "no unified block has since 1.16.4 or 1.16.5");
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found1165 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.16.5")
                        && candidate.protocolVersion() == 754
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_16_5.V1165Adapter"));
        assertTrue(found1165, "Expected V1165Adapter via ServiceLoader");
        boolean found1164 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.16.4")
                        && candidate.protocolVersion() == 754
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_16_5.V1164Adapter"));
        assertTrue(found1164, "Expected V1164Adapter via ServiceLoader");
    }
}
