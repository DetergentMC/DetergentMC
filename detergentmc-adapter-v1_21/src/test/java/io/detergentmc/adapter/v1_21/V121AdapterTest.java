package io.detergentmc.adapter.v1_21;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V121AdapterTest {

    private final V121Adapter adapter = new V121Adapter();

    @Test
    void identifiesItselfAs121() {
        assertEquals("1.21", adapter.minecraftVersion());
        assertEquals(767, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn121() {
        // Every unified block known today was introduced in 1.20 or
        // earlier, and 1.21 added no new blocks over 1.20.6, so all
        // of them exist in 1.21 - including the 1.20 additions
        // (cherry wood, bamboo, suspicious sand, calibrated sculk
        // sensor) and the 1.20.3 additions (crafter, trial spawner).
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.21 should support " + block);
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
        // "1.20" (no patch part) counts as 1.20.0 and is older than 1.21.
        assertTrue(V121Adapter.compareVersions("1.20", "1.21") < 0);
        assertEquals(0, V121Adapter.compareVersions("1.21", "1.21.0"));
        assertTrue(V121Adapter.compareVersions("1.20.6", "1.21") < 0);
        assertEquals(0, V121Adapter.compareVersions("1.21", "1.21"));
        assertTrue(V121Adapter.compareVersions("1.21.1", "1.21") > 0);
        // The rule behind the mapping table: a block introduced after
        // 1.21 (for example in 1.21.1 or later) would be reported as
        // unsupported. No such unified block exists yet, so the table
        // itself is verified by mapsEveryBlockThatExistsIn121 plus
        // this comparison.
        assertTrue(V121Adapter.availableIn121(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.21");
    }

    @Test
    void registeredAsVersionAdapterService() {
        // The adapter must be discoverable via ServiceLoader from its
        // own META-INF/services registration.
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.21")
                        && candidate.protocolVersion() == 767
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_21.V121Adapter"));
        assertTrue(found, "Expected V121Adapter via ServiceLoader");
    }
}
