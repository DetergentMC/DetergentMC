package io.detergentmc.adapter.v1_17;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V117AdapterTest {

    private final V117Adapter adapter = new V117Adapter();

    @Test
    void identifiesItselfAs117() {
        assertEquals("1.17", adapter.minecraftVersion());
        assertEquals(755, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn117() {
        // 19 unified blocks known today. The 5 introduced in 1.20
        // (cherry log/planks, bamboo block, suspicious sand, calibrated
        // sculk sensor) do not exist in 1.17, so 14 of them map.
        // Amethyst (1.17) exists; nothing has since 1.18 or 1.19, so the
        // count is the same 14 as for 1.17.1 and 1.18 - counted from
        // BlockType.since(), not guessed.
        assertEquals(19, BlockType.values().length);
        int supported = 0;
        for (BlockType block : BlockType.values()) {
            if (adapter.supports(block)) {
                supported++;
                assertEquals(block.minecraftId(),
                        adapter.toMinecraftId(block).orElseThrow());
            }
        }
        assertEquals(14, supported);
        assertEquals("minecraft:amethyst_block",
                adapter.toMinecraftId(BlockType.AMETHYST_BLOCK).orElseThrow());
        assertEquals("minecraft:stone",
                adapter.toMinecraftId(BlockType.STONE).orElseThrow());
    }

    @Test
    void blocksIntroducedAfter117AreUnsupported() {
        // The 1.20 additions are in the unified enum but must not map
        // in 1.17 - toMinecraftId has to answer empty for them, and the
        // new ids must not be silently mapped to a wrong block.
        for (BlockType block : new BlockType[]{BlockType.CHERRY_LOG,
                BlockType.CHERRY_PLANKS, BlockType.BAMBOO_BLOCK,
                BlockType.SUSPICIOUS_SAND, BlockType.CALIBRATED_SCULK_SENSOR}) {
            assertFalse(adapter.supports(block), "1.17 should not support " + block);
            assertFalse(adapter.toMinecraftId(block).isPresent());
        }
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.minecraftId().equals("minecraft:cherry_log")
                        && adapter.supports(block)),
                "cherry_log must not map in 1.17");
    }

    @Test
    void versionComparisonDecidesAvailability() {
        assertTrue(V117Adapter.compareVersions("1.17", "1.17.1") < 0);
        assertEquals(0, V117Adapter.compareVersions("1.17", "1.17"));
        assertTrue(V117Adapter.compareVersions("1.18", "1.17") > 0);
        assertTrue(V117Adapter.compareVersions("1.20", "1.17") > 0);
        assertTrue(V117Adapter.availableIn117(BlockType.AMETHYST_BLOCK),
                "a 1.17 block exists in 1.17");
        assertFalse(V117Adapter.availableIn117(BlockType.CHERRY_LOG),
                "a 1.20 block does not exist in 1.17");
    }

    @Test
    void noUnifiedBlockWasIntroducedIn118Or119() {
        // Why 1.17 maps the same 14 blocks as 1.17.1 and 1.18: the unified enum
        // has no entry introduced in 1.18 or 1.19 (the 1.18 additions
        // such as deep slate variants are not unified blocks yet).
        // If one is added later, the 14 in mapsEveryBlockThatExistsIn117
        // must be revisited.
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.since().equals("1.18")
                        || block.since().equals("1.19")),
                "no unified block has since 1.18 or 1.19");
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found117 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.17")
                        && candidate.protocolVersion() == 755
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_17.V117Adapter"));
        assertTrue(found117, "Expected V117Adapter via ServiceLoader");
    }
}
