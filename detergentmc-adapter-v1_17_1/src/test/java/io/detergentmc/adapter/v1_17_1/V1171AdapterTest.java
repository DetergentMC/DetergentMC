package io.detergentmc.adapter.v1_17_1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1171AdapterTest {

    private final V1171Adapter adapter = new V1171Adapter();

    @Test
    void identifiesItselfAs1171() {
        assertEquals("1.17.1", adapter.minecraftVersion());
        assertEquals(756, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1171() {
        // 19 unified blocks known today. The 5 introduced in 1.20
        // (cherry log/planks, bamboo block, suspicious sand, calibrated
        // sculk sensor) do not exist in 1.17.1, so 14 of them map.
        // Amethyst (1.17) exists; nothing has since 1.18 or 1.19, so the
        // count is the same 14 as for 1.18 - counted from
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
    void blocksIntroducedAfter1171AreUnsupported() {
        // The 1.20 additions are in the unified enum but must not map
        // in 1.17.1 - toMinecraftId has to answer empty for them, and the
        // new ids must not be silently mapped to a wrong block.
        for (BlockType block : new BlockType[]{BlockType.CHERRY_LOG,
                BlockType.CHERRY_PLANKS, BlockType.BAMBOO_BLOCK,
                BlockType.SUSPICIOUS_SAND, BlockType.CALIBRATED_SCULK_SENSOR}) {
            assertFalse(adapter.supports(block), "1.17.1 should not support " + block);
            assertFalse(adapter.toMinecraftId(block).isPresent());
        }
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.minecraftId().equals("minecraft:cherry_log")
                        && adapter.supports(block)),
                "cherry_log must not map in 1.17.1");
    }

    @Test
    void versionComparisonDecidesAvailability() {
        assertTrue(V1171Adapter.compareVersions("1.17", "1.17.1") < 0);
        assertEquals(0, V1171Adapter.compareVersions("1.17.1", "1.17.1"));
        assertTrue(V1171Adapter.compareVersions("1.18", "1.17.1") > 0);
        assertTrue(V1171Adapter.compareVersions("1.20", "1.17.1") > 0);
        assertTrue(V1171Adapter.availableIn1171(BlockType.AMETHYST_BLOCK),
                "a 1.17 block exists in 1.17.1");
        assertFalse(V1171Adapter.availableIn1171(BlockType.CHERRY_LOG),
                "a 1.20 block does not exist in 1.17.1");
    }

    @Test
    void noUnifiedBlockWasIntroducedIn118Or119() {
        // Why 1.17.1 maps the same 14 blocks as 1.18: the unified enum
        // has no entry introduced in 1.18 or 1.19 (the 1.18 additions
        // such as deep slate variants are not unified blocks yet).
        // If one is added later, the 14 in mapsEveryBlockThatExistsIn1171
        // must be revisited.
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.since().equals("1.18")
                        || block.since().equals("1.19")),
                "no unified block has since 1.18 or 1.19");
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found1171 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.17.1")
                        && candidate.protocolVersion() == 756
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_17_1.V1171Adapter"));
        assertTrue(found1171, "Expected V1171Adapter via ServiceLoader");
    }
}
