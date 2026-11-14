package io.detergentmc.content;

import io.detergentmc.api.BlockType;

/**
 * A custom block DetergentMC adds on top of a Minecraft version.
 *
 * <p>A custom block is displayed to clients using a base block the client
 * already has, plus a texture from the DetergentMC resource pack. That is
 * how a new block can appear even on an old client: the client never
 * needs the block to exist in its own version.
 */
public record CustomBlock(
        /** Unique id, e.g. {@code "detergentmc:clean_brick"}. */
        String id,
        /** The vanilla block the client sees underneath. */
        BlockType baseBlock,
        /** Texture path inside the resource pack, e.g. {@code "detergentmc:block/clean_brick"}. */
        String texture,
        /** Translation key for the display name, e.g. {@code "block.detergentmc.clean_brick"}. */
        String displayNameKey,
        /** Minecraft version this block was introduced in DetergentMC for. */
        String introducedIn
) {
    public CustomBlock {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CustomBlock id is required");
        }
        if (baseBlock == null) {
            throw new IllegalArgumentException("CustomBlock baseBlock is required");
        }
        if (texture == null) {
            texture = "";
        }
        if (displayNameKey == null) {
            displayNameKey = "";
        }
        if (introducedIn == null) {
            introducedIn = "";
        }
    }
}
