package io.detergentmc.adapter.v1_20_4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import org.junit.jupiter.api.Test;

class V1204AdapterTest {

    private final V1204Adapter adapter = new V1204Adapter();

    @Test
    void identifiesItselfAs1204() {
        assertEquals("1.20.4", adapter.minecraftVersion());
        assertEquals(765, adapter.protocolVersion());
    }

    @Test
    void mapsEveryUnifiedBlock() {
        for (BlockType block : BlockType.values()) {
            assertTrue(adapter.supports(block), "1.20.4 should support " + block);
            assertEquals(block.minecraftId(),
                    adapter.toMinecraftId(block).orElseThrow());
        }
        assertEquals("minecraft:cherry_log",
                adapter.toMinecraftId(BlockType.CHERRY_LOG).orElseThrow());
    }
}
