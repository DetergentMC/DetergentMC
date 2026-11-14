package io.detergentmc.adapter.v1_21_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class V1213AdapterTest {

    private final V1213Adapter adapter = new V1213Adapter();

    @Test
    void identifiesItselfAs1213() {
        assertEquals("1.21.3", adapter.minecraftVersion());
        assertEquals(768, adapter.protocolVersion());
    }

    @Test
    void mapsEveryBlockThatExistsIn1213() {
        // 1.21.3 added no new blocks over 1.21.2, so every unified
        // block known today exists in 1.21.3.
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.21.3 should support " + block);
            assertEquals(block.minecraftId(),
                    adapter.toMinecraftId(block).orElseThrow());
        }
        assertEquals("minecraft:cherry_log",
                adapter.toMinecraftId(BlockType.CHERRY_LOG).orElseThrow());
    }

    @Test
    void versionComparisonDecidesAvailability() {
        assertTrue(V1213Adapter.availableIn1213(BlockType.CHERRY_LOG),
                "a 1.20 block exists in 1.21.3");
    }

    @Test
    void registeredAsVersionAdapterService() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(candidate -> candidate.minecraftVersion().equals("1.21.3")
                        && candidate.protocolVersion() == 768
                        && candidate.getClass().getName()
                                .equals("io.detergentmc.adapter.v1_21_2.V1213Adapter"));
        assertTrue(found, "Expected V1213Adapter via ServiceLoader");
    }
}
