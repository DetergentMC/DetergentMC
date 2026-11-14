package io.detergentmc.adapter.v1_20_5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1205AdapterTest {

    private final V1205Adapter adapter = new V1205Adapter();

    @Test
    void identifiesItselfAs1205() {
        assertEquals("1.20.5", adapter.minecraftVersion());
        assertEquals(766, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1205() {
        // Every unified block known today was introduced in 1.20 or
        // earlier, and 1.20.5 added no new blocks over 1.20.4, so all
        // of them exist in 1.20.5 - including the 1.20 additions
        // (cherry wood, bamboo, suspicious sand, calibrated sculk
        // sensor).
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.20.5 should support " + block);
            assertEquals(block.minecraftId(),
                    adapter.toMinecraftId(block).orElseThrow());
        }
        assertEquals("minecraft:cherry_log",
                adapter.toMinecraftId(BlockType.CHERRY_LOG).orElseThrow());
        assertEquals("minecraft:calibrated_sculk_sensor",
                adapter.toMinecraftId(BlockType.CALIBRATED_SCULK_SENSOR).orElseThrow());
    }

    @Test
    void versionComparisonDecidesAvailability() {
        // "1.20" (no patch part) counts as 1.20.0 and is older than 1.20.5.
        assertTrue(V1205Adapter.compareVersions("1.20", "1.20.5") < 0);
        assertEquals(0, V1205Adapter.compareVersions("1.20", "1.20.0"));
        assertTrue(V1205Adapter.compareVersions("1.20.4", "1.20.5") < 0);
        assertEquals(0, V1205Adapter.compareVersions("1.20.5", "1.20.5"));
        assertTrue(V1205Adapter.compareVersions("1.20.6", "1.20.5") > 0);
        assertTrue(V1205Adapter.compareVersions("1.21", "1.20.5") > 0);
        // The rule behind the mapping table: a block introduced after
        // 1.20.5 (for example in 1.20.6 or later) would be reported as
        // unsupported. No such unified block exists yet, so the table
        // itself is verified by mapsEveryBlockThatExistsIn1205 plus
        // this comparison.
        assertTrue(V1205Adapter.availableIn1205(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.20.5");
    }

    @Test
    void registeredAsVersionAdapterService() {
        // The adapter must be discoverable via ServiceLoader from its
        // own META-INF/services registration.
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.20.5")
                        && candidate.protocolVersion() == 766
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_20_5.V1205Adapter"));
        assertTrue(found, "Expected V1205Adapter via ServiceLoader");
    }
}
