package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.5 (legacy protocol 60).
 *
 * <p>Minecraft 1.5 shares legacy protocol 60 with 1.5.1 and is
 * wire-identical to it (measured, docs/PROXY-1.5.1.md), so this
 * adapter differs from {@link V1_5_1Adapter} only in the version
 * name: a {@code minecraft-version: 1.5} backend is served by the
 * frontend's direct mode exactly like a 1.5.1 one. piston-meta
 * mislabels 1.5 as a snapshot, but it is a full release with an
 * official server jar (SHA-1
 * {@code aedad5159ef56d69c5bcf77ed141f53430af43c3}, verified
 * against piston-meta), downloaded at runtime and never committed.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 60 ({@link LegacyProtocol#V1_5_1}).
 */
public final class V1_5Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.5";

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

    /** The LEGACY protocol number (61) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_5_1;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
