package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.4.4 (legacy protocol 49).
 *
 * <p>Minecraft 1.4.4 shares legacy protocol 49 with 1.4.5 and is
 * identical to it in registrations, packet layouts and registries
 * (measured by mechanical extraction from both jars,
 * docs/PROXY-1.4.5.md), so this adapter differs from
 * {@link V1_4_5Adapter} only in the version name: a
 * {@code minecraft-version: 1.4.4} backend is served by the
 * frontend's direct mode exactly like a 1.4.5 one. The vanilla
 * 1.4.4 server jar (SHA-1
 * {@code 4215dcadb706508bf9d6d64209a0080b9cee9e71}, verified against
 * piston-meta) is downloaded at runtime and never committed.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 49 ({@link LegacyProtocol#V1_4_5}).
 */
public final class V1_4_4Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.4.4";

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (V1_6_4Adapter.compareVersions(block.since(), MINECRAFT_VERSION) <= 0) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    @Override
    public String minecraftVersion() {
        return MINECRAFT_VERSION;
    }

    /** The LEGACY protocol number (49) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_4_5;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
