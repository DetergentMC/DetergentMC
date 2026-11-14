package io.detergentmc.adapter.v1_21_4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1214AdapterTest {

    private final V1214Adapter adapter = new V1214Adapter();

    @Test
    void identifiesItselfAs1214() {
        assertEquals("1.21.4", adapter.minecraftVersion());
        assertEquals(769, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1214() {
        // Every unified block known today was introduced in 1.21.3 or
        // earlier, and the 1.21.4 additions (resin blocks, eyeblossom)
        // are not in the unified BlockType enum yet, so all of them
        // exist in 1.21.4 - including the 1.20 additions (cherry wood,
        // bamboo, suspicious sand, calibrated sculk sensor), the 1.20.3
        // additions (crafter, trial spawner) and the 1.21.2 additions
        // (pale oak woodset, creaking heart, pale moss).
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.21.4 should support " + block);
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
        assertTrue(V1214Adapter.compareVersions("1.20", "1.21.4") < 0);
        assertEquals(0, V1214Adapter.compareVersions("1.21.4", "1.21.4"));
        assertTrue(V1214Adapter.compareVersions("1.21.3", "1.21.4") < 0);
        assertTrue(V1214Adapter.compareVersions("1.21.5", "1.21.4") > 0);
        assertTrue(V1214Adapter.availableIn1214(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.21.4");
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.21.4")
                        && candidate.protocolVersion() == 769
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_21_4.V1214Adapter"));
        assertTrue(found, "Expected V1214Adapter via ServiceLoader");
    }
}
