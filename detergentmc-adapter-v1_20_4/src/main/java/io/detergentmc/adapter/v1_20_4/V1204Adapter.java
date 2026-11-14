package io.detergentmc.adapter.v1_20_4;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.20.4 (protocol 765).
 *
 * <p>This module contains mapping tables only. Every {@link BlockType} in
 * the unified API exists in 1.20.4, so every mapping is present here.
 * Blocks added in later versions are simply absent from this adapter's
 * table in older adapters - that is how the unified API stays honest
 * about what a version can do.
 *
 * <p>TODO (next milestone): bind this adapter to vanilla internals via
 * the official Mojang mappings. The vanilla server jar is downloaded
 * from Mojang at build/run time and is never committed - see
 * docs/VERSIONS.md. Until then this adapter is a pure mapping layer
 * with no NMS calls.
 */
public final class V1204Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.20.4";
    public static final int PROTOCOL_VERSION = 765;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            BLOCKS.put(block, block.minecraftId());
        }
    }

    @Override
    public String minecraftVersion() {
        return MINECRAFT_VERSION;
    }

    @Override
    public int protocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
