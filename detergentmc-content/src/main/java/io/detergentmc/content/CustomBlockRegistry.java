package io.detergentmc.content;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Registry of custom blocks, keyed by id. */
public final class CustomBlockRegistry {

    private final Map<String, CustomBlock> blocks = new LinkedHashMap<>();

    public void register(CustomBlock block) {
        if (blocks.containsKey(block.id())) {
            throw new IllegalArgumentException("Custom block already registered: " + block.id());
        }
        blocks.put(block.id(), block);
    }

    public Optional<CustomBlock> get(String id) {
        return Optional.ofNullable(blocks.get(id));
    }

    public Collection<CustomBlock> all() {
        return java.util.List.copyOf(blocks.values());
    }

    /**
     * The demo custom block: {@code detergentmc:bubble_block}.
     *
     * <p><b>Honest scope — this is NOT a real new block.</b> At this stage
     * it is only a definition in a mapping table: the id, a display-name
     * key, the vanilla block clients actually see
     * ({@code minecraft:slime_block}), and the texture the resource pack
     * would give it. Vanilla has no {@code detergentmc:bubble_block} block
     * id, so DetergentMC cannot (yet) send that id to a client or place
     * such a block in a world — a client would only ever receive a slime
     * block, optionally retextured by the pack. Sending a genuinely new
     * block id needs the protocol/networking milestone.
     */
    public static CustomBlock demoBubbleBlock() {
        return new CustomBlock(
                "detergentmc:bubble_block",
                io.detergentmc.api.BlockType.SLIME_BLOCK,
                "detergentmc:block/bubble_block",
                "block.detergentmc.bubble_block",
                "1.20.4");
    }

    /** Registers the demo bubble block (see {@link #demoBubbleBlock()}). */
    public void registerDemoBlocks() {
        register(demoBubbleBlock());
    }
}
