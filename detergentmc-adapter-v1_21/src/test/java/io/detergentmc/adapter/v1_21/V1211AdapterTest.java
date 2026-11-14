package io.detergentmc.adapter.v1_21;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1211AdapterTest {

    private final V1211Adapter adapter = new V1211Adapter();

    @Test
    void identifiesItselfAs1211() {
        assertEquals("1.21.1", adapter.minecraftVersion());
        assertEquals(767, adapter.protocolVersion());
    }

    @Test
    void sharesProtocol767With121() {
        // 1.21 and 1.21.1 share protocol 767, the same way 1.20.5
        // and 1.20.6 share 766.
        assertEquals(new V121Adapter().protocolVersion(), adapter.protocolVersion());
        assertEquals(767, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1211() {
        // Every unified block known today was introduced in 1.20 or
        // earlier, and 1.21.1 added no new blocks over 1.21, so all
        // of them exist in 1.21.1.
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.21.1 should support " + block);
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
        assertTrue(V1211Adapter.availableIn1211(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.21.1");
        // A block introduced after 1.21.1 (for example in 1.21.2) would
        // be reported as unsupported; no such unified block exists
        // yet, so the rule is checked through the shared comparison.
        assertTrue(V121Adapter.compareVersions("1.21.2", "1.21.1") > 0);
        assertEquals(0, V121Adapter.compareVersions("1.21.1", "1.21.1"));
    }

    @Test
    void registeredAsVersionAdapterService() {
        // The adapter must be discoverable via ServiceLoader from its
        // own META-INF/services registration (both 767 adapters are
        // registered from this one module).
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.21.1")
                        && candidate.protocolVersion() == 767
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_21.V1211Adapter"));
        assertTrue(found, "Expected V1211Adapter via ServiceLoader");
    }
}
