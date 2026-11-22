package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.6.1 (legacy protocol 73).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.6.1 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 73 ({@link LegacyProtocol#V1_6_1}), which lives in the
 * legacy numbering space only.
 *
 * <p>Blocks: the 1.6.1 and 1.6.2 registries are identical (measured
 * via the client jars' lang keys, docs/PROXY-1.6.1.md), so the block
 * set is every {@link BlockType} whose {@code since} version is
 * 1.6.1 or older — the same set as 1.6.2/1.6.4 in practice. The
 * representative vanilla 1.6.1 server jar (SHA-1
 * {@code 0252918a5f9d47e3c6eb1dfec02134d1374a89b4}, verified against
 * piston-meta) is downloaded at runtime, never committed. This
 * generation has no datapacks, no packet compression and no
 * DataVersion concept; pack_format is 1 and the internal server
 * runs on Java 8.
 */
public final class V1_6_1Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.6.1";

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

    /** The LEGACY protocol number (73) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_6_1;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
