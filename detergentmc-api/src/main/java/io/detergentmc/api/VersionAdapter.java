package io.detergentmc.api;

import java.util.Optional;

/**
 * Maps the version-independent DetergentMC API onto one concrete Minecraft
 * version. Every supported version gets its own adapter (one module per
 * version), so the API and the Bubbles never need to know which Minecraft
 * version is running underneath.
 *
 * <p>Adapters are discovered at runtime via {@link java.util.ServiceLoader}.
 */
public interface VersionAdapter {

    /** Minecraft version this adapter is for, e.g. {@code "1.20.4"}. */
    String minecraftVersion();

    /** Minecraft protocol version, e.g. {@code 765} for 1.20.4. */
    int protocolVersion();

    /**
     * The Minecraft block id for a unified block, e.g. {@code "minecraft:stone"}.
     * Empty when this version has no such block (it was added later).
     */
    Optional<String> toMinecraftId(BlockType block);

    /** Whether this version has the given unified block. */
    default boolean supports(BlockType block) {
        return toMinecraftId(block).isPresent();
    }

    /** Translation key for this block's display name. */
    default String displayNameKey(BlockType block) {
        return "block." + block.id();
    }
}
