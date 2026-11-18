package io.detergentmc.adapter.v1_18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V118AdapterTest {

    private final V118Adapter adapter = new V118Adapter();
    private final V1181Adapter adapter1181 = new V1181Adapter();

    @Test
    void identifiesItselfAs118() {
        assertEquals("1.18", adapter.minecraftVersion());
        assertEquals(757, adapter.protocolVersion());
        assertEquals("1.18.1", adapter1181.minecraftVersion());
        assertEquals(757, adapter1181.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn118() {
        // 19 unified blocks known today. The 5 introduced in 1.20
        // (cherry log/planks, bamboo block, suspicious sand, calibrated
        // sculk sensor) do not exist in 1.18, so 14 of them map.
        // Amethyst (1.17) exists; nothing has since 1.18 or 1.19.
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
    void blocksIntroducedAfter118AreUnsupported() {
        // The 1.20 additions are in the unified enum but must not map
        // in 1.18 - toMinecraftId has to answer empty for them, and the
        // new ids must not be silently mapped to a wrong block.
        for (BlockType block : new BlockType[]{BlockType.CHERRY_LOG,
                BlockType.CHERRY_PLANKS, BlockType.BAMBOO_BLOCK,
                BlockType.SUSPICIOUS_SAND, BlockType.CALIBRATED_SCULK_SENSOR}) {
            assertFalse(adapter.supports(block), "1.18 should not support " + block);
            assertFalse(adapter.toMinecraftId(block).isPresent());
            assertFalse(adapter1181.supports(block),
                    "1.18.1 should not support " + block);
        }
        assertFalse(Stream.of(BlockType.values())
                .anyMatch(block -> block.minecraftId().equals("minecraft:cherry_log")
                        && adapter.supports(block)),
                "cherry_log must not map in 1.18");
    }

    @Test
    void versionComparisonDecidesAvailability() {
        assertTrue(V118Adapter.compareVersions("1.17", "1.18") < 0);
        assertEquals(0, V118Adapter.compareVersions("1.18", "1.18"));
        assertTrue(V118Adapter.compareVersions("1.19", "1.18") > 0);
        assertTrue(V118Adapter.compareVersions("1.20", "1.18") > 0);
        assertTrue(V118Adapter.availableIn118(BlockType.AMETHYST_BLOCK),
                "a 1.17 block exists in 1.18");
        assertFalse(V118Adapter.availableIn118(BlockType.CHERRY_LOG),
                "a 1.20 block does not exist in 1.18");
    }

    @Test
    void patch1181SharesProtocol757With118() {
        // 1.18.1 answers exactly like 1.18 for blocks (same protocol
        // 757, same registries - minecraft-data maps 1.18.1 to pc/1.18).
        for (BlockType block : BlockType.values()) {
            assertEquals(adapter.toMinecraftId(block),
                    adapter1181.toMinecraftId(block),
                    "1.18.1 should answer like 1.18 for " + block);
        }
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found118 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.18")
                        && candidate.protocolVersion() == 757
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_18.V118Adapter"));
        assertTrue(found118, "Expected V118Adapter via ServiceLoader");
        boolean found1181 = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.18.1")
                        && candidate.protocolVersion() == 757
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_18.V1181Adapter"));
        assertTrue(found1181, "Expected V1181Adapter via ServiceLoader");
    }
}
