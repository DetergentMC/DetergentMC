package io.detergentmc.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BlockType;
import org.junit.jupiter.api.Test;

class RegistriesTest {

    @Test
    void customBlocksRegisterAndResolve() {
        CustomBlockRegistry registry = new CustomBlockRegistry();
        registry.register(new CustomBlock(
                "detergentmc:clean_brick", BlockType.STONE,
                "detergentmc:block/clean_brick", "block.detergentmc.clean_brick", "1.20.4"));
        assertTrue(registry.get("detergentmc:clean_brick").isPresent());
        assertEquals(1, registry.all().size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(new CustomBlock(
                "detergentmc:clean_brick", BlockType.STONE,
                "detergentmc:block/clean_brick", "block.detergentmc.clean_brick", "1.20.4")));
    }

    @Test
    void demoBubbleBlockIsOnlyAMappingNotARealBlock() {
        // Honest scope: detergentmc:bubble_block does not exist in vanilla.
        // The registry entry only maps it to a vanilla slime block plus a
        // resource-pack texture; no new block id is ever sent to a client.
        CustomBlockRegistry registry = new CustomBlockRegistry();
        registry.registerDemoBlocks();
        CustomBlock bubble = registry.get("detergentmc:bubble_block").orElseThrow();
        assertEquals(BlockType.SLIME_BLOCK, bubble.baseBlock());
        assertEquals("minecraft:slime_block", bubble.baseBlock().minecraftId());
        assertEquals("detergentmc:block/bubble_block", bubble.texture());
        assertEquals("block.detergentmc.bubble_block", bubble.displayNameKey());
    }

    @Test
    void customUisValidateSlots() {
        UIRegistry registry = new UIRegistry();
        registry.register(new CustomUI("detergentmc:shop", "ui.detergentmc.shop", 27));
        assertTrue(registry.get("detergentmc:shop").isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> new CustomUI("detergentmc:bad", "ui.bad", 10));
    }
}
