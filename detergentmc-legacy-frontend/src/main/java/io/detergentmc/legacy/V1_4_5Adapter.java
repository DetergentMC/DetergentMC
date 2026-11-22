package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.4.5 (legacy protocol 49).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.4.5 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 49 ({@link LegacyProtocol#V1_4_5}), which lives in the
 * legacy numbering space only. Protocol 49 also serves Minecraft
 * 1.4.4 (see {@link V1_4_4Adapter}); the two releases are identical
 * in registrations, packet layouts and registries (measured,
 * docs/PROXY-1.4.5.md). Note that the widespread protocol tables
 * swapping 49 and 51 are wrong: 49 is 1.4.4/1.4.5, 51 is 1.4.6/1.4.7.
 *
 * <p>Blocks: the block set is every {@link BlockType} whose
 * {@code since} version is 1.4.5 or older — the same set as 1.4.7,
 * whose block registry is identical to 1.4.5's (measured). The
 * representative vanilla 1.4.5 server jar (SHA-1
 * {@code c12fd88a8233d2c517dbc8196ba2ae855f4d36ea}, verified against
 * piston-meta) is downloaded at runtime, never committed. This
 * generation has no datapacks, no packet compression, no scoreboard
 * and no DataVersion concept; texture packs travel as MC|TPack
 * plugin messages and the internal server runs on Java 8.
 */
public final class V1_4_5Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.4.5";

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
