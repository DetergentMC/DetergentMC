package io.detergentmc.adapter.v1_21_7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1217AdapterTest {

    private final V1217Adapter adapter = new V1217Adapter();

    @Test
    void identifiesItselfAs1217() {
        assertEquals("1.21.7", adapter.minecraftVersion());
        assertEquals(772, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1217() {
        // Every unified block known today was introduced in 1.21.5 or
        // earlier, and the 1.21.6 addition (dried ghast) is not in the
        // unified BlockType enum yet, so all of them exist in 1.21.7 -
        // including the 1.20 additions (cherry wood, bamboo, suspicious
        // sand, calibrated sculk sensor), the 1.20.3 additions (crafter,
        // trial spawner), the 1.21.2 additions (pale oak woodset,
        // creaking heart, pale moss), the 1.21.4 additions (resin
        // blocks, eyeblossom) and the 1.21.5 additions (bush, dry
        // grass, cactus flower, wildflowers, leaf litter, firefly
        // bush, test block, test instance block).
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.21.7 should support " + block);
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
        assertTrue(V1217Adapter.compareVersions("1.20", "1.21.7") < 0);
        assertEquals(0, V1217Adapter.compareVersions("1.21.7", "1.21.7"));
        assertTrue(V1217Adapter.compareVersions("1.21.4", "1.21.7") < 0);
        assertTrue(V1217Adapter.compareVersions("1.21.8", "1.21.7") > 0);
        assertTrue(V1217Adapter.availableIn1217(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.21.7");
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.21.7")
                        && candidate.protocolVersion() == 772
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_21_7.V1217Adapter"));
        assertTrue(found, "Expected V1217Adapter via ServiceLoader");
    }
}
