package io.detergentmc.adapter.v1_20_5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1206AdapterTest {

    private final V1206Adapter adapter = new V1206Adapter();

    @Test
    void identifiesItselfAs1206() {
        assertEquals("1.20.6", adapter.minecraftVersion());
        assertEquals(766, adapter.protocolVersion());
    }

    @Test
    void sharesProtocol766With1205() {
        // 1.20.5 and 1.20.6 share protocol 766, the same way 1.20.3
        // and 1.20.4 share 765.
        assertEquals(new V1205Adapter().protocolVersion(), adapter.protocolVersion());
        assertEquals(766, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1206() {
        // Every unified block known today was introduced in 1.20 or
        // earlier, and 1.20.6 added no new blocks over 1.20.5, so all
        // of them exist in 1.20.6.
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.20.6 should support " + block);
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
        assertTrue(V1206Adapter.availableIn1206(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.20.6");
        // A block introduced after 1.20.6 (for example in 1.21) would
        // be reported as unsupported; no such unified block exists
        // yet, so the rule is checked through the shared comparison.
        assertTrue(V1205Adapter.compareVersions("1.21", "1.20.6") > 0);
        assertEquals(0, V1205Adapter.compareVersions("1.20.6", "1.20.6"));
    }

    @Test
    void registeredAsVersionAdapterService() {
        // The adapter must be discoverable via ServiceLoader from its
        // own META-INF/services registration (both 766 adapters are
        // registered from this one module).
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.20.6")
                        && candidate.protocolVersion() == 766
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_20_5.V1206Adapter"));
        assertTrue(found, "Expected V1206Adapter via ServiceLoader");
    }
}
