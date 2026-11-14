package io.detergentmc.adapter.v1_20_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1202AdapterTest {

    private final V1202Adapter adapter = new V1202Adapter();

    @Test
    void identifiesItselfAs1202() {
        assertEquals("1.20.2", adapter.minecraftVersion());
        assertEquals(764, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1202() {
        // Every unified block known today was introduced in 1.20 or
        // earlier, and 1.20.2 added no new blocks over 1.20.1, so all
        // of them exist in 1.20.2 - including the 1.20 additions
        // (cherry wood, bamboo, suspicious sand, calibrated sculk
        // sensor).
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.20.2 should support " + block);
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
        // "1.20" (no patch part) counts as 1.20.0 and is older than 1.20.2.
        assertTrue(V1202Adapter.compareVersions("1.20", "1.20.2") < 0);
        assertEquals(0, V1202Adapter.compareVersions("1.20", "1.20.0"));
        assertTrue(V1202Adapter.compareVersions("1.20.1", "1.20.2") < 0);
        assertEquals(0, V1202Adapter.compareVersions("1.20.2", "1.20.2"));
        assertTrue(V1202Adapter.compareVersions("1.20.3", "1.20.2") > 0);
        assertTrue(V1202Adapter.compareVersions("1.21", "1.20.2") > 0);
        // The rule behind the mapping table: a block introduced after
        // 1.20.2 (for example in 1.20.3, like the crafter) would be
        // reported as unsupported. No such unified block exists yet, so
        // the table itself is verified by mapsEveryBlockThatExistsIn1202
        // plus this comparison.
        assertTrue(V1202Adapter.availableIn1202(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.20.2");
    }

    @Test
    void registeredAsVersionAdapterService() {
        // The adapter must be discoverable via ServiceLoader from its
        // own META-INF/services registration.
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.20.2")
                        && candidate.protocolVersion() == 764
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_20_2.V1202Adapter"));
        assertTrue(found, "Expected V1202Adapter via ServiceLoader");
    }
}
