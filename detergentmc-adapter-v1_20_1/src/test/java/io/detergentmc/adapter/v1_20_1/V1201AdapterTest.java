package io.detergentmc.adapter.v1_20_1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import org.junit.jupiter.api.Test;

class V1201AdapterTest {

    private final V1201Adapter adapter = new V1201Adapter();

    @Test
    void identifiesItselfAs1201() {
        assertEquals("1.20.1", adapter.minecraftVersion());
        assertEquals(763, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1201() {
        // Every unified block known today was introduced in 1.20 or
        // earlier, so all of them exist in 1.20.1 - including the 1.20
        // additions (cherry wood, bamboo, suspicious sand, calibrated
        // sculk sensor).
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.20.1 should support " + block);
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
        // "1.20" (no patch part) counts as 1.20.0 and is older than 1.20.1.
        assertTrue(V1201Adapter.compareVersions("1.20", "1.20.1") < 0);
        assertEquals(0, V1201Adapter.compareVersions("1.20", "1.20.0"));
        assertEquals(0, V1201Adapter.compareVersions("1.20.1", "1.20.1"));
        assertTrue(V1201Adapter.compareVersions("1.20.2", "1.20.1") > 0);
        assertTrue(V1201Adapter.compareVersions("1.21", "1.20.1") > 0);
        // The rule behind the mapping table: a block introduced after
        // 1.20.1 would be reported as unsupported. (No such unified
        // block exists yet, so the table itself is verified by
        // mapsEveryBlockThatExistsIn1201 plus this comparison.)
    }
}
