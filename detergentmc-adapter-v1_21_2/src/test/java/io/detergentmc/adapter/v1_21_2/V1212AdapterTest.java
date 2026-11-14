package io.detergentmc.adapter.v1_21_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1212AdapterTest {

    private final V1212Adapter adapter = new V1212Adapter();

    @Test
    void identifiesItselfAs1212() {
        assertEquals("1.21.2", adapter.minecraftVersion());
        assertEquals(768, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1212() {
        // Every unified block known today was introduced in 1.20 or
        // earlier, and the 1.21.2 additions (pale oak woodset,
        // creaking heart, pale moss) are not in the unified
        // BlockType enum yet, so all of them exist in 1.21.2 -
        // including the 1.20 additions (cherry wood, bamboo,
        // suspicious sand, calibrated sculk sensor) and the 1.20.3
        // additions (crafter, trial spawner).
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.21.2 should support " + block);
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
        assertTrue(V1212Adapter.compareVersions("1.20", "1.21.2") < 0);
        assertEquals(0, V1212Adapter.compareVersions("1.21.2", "1.21.2"));
        assertTrue(V1212Adapter.compareVersions("1.21.1", "1.21.2") < 0);
        assertTrue(V1212Adapter.compareVersions("1.21.3", "1.21.2") > 0);
        assertTrue(V1212Adapter.availableIn1212(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.21.2");
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.21.2")
                        && candidate.protocolVersion() == 768
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_21_2.V1212Adapter"));
        assertTrue(found, "Expected V1212Adapter via ServiceLoader");
    }
}
