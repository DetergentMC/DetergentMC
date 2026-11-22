package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.5.1 (legacy protocol 60).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.5.1 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 60 ({@link LegacyProtocol#V1_5_1}), which lives in the
 * legacy numbering space only. Protocol 60 also serves Minecraft
 * 1.5 (see {@link V1_5Adapter}); the two releases are wire-identical
 * (measured, docs/PROXY-1.5.1.md).
 *
 * <p>Blocks: the block set is every {@link BlockType} whose
 * {@code since} version is 1.5.1 or older — the same set as 1.5.2,
 * whose registry is identical to 1.5.1's (measured via the client
 * jars' lang keys). The representative vanilla 1.5.1 server jar
 * (SHA-1 {@code d07c71ee2767dabb79fb32dad8162e1b854d5324}, verified
 * against piston-meta) is downloaded at runtime, never committed.
 * This generation has no datapacks, no packet compression and no
 * DataVersion concept; texture packs travel as MC|TPack plugin
 * messages and the internal server runs on Java 8.
 */
public final class V1_5_1Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.5.1";

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
