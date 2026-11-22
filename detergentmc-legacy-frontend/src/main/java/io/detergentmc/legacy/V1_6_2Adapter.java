package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.6.2 (legacy protocol 74).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.6.2 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 74 ({@link LegacyProtocol#V1_6_2}), which lives in the
 * legacy numbering space only.
 *
 * <p>Blocks: the 1.6.2 and 1.6.4 registries are identical (measured
 * via the client jars' lang keys, docs/PROXY-1.6.2.md), so the block
 * set is every {@link BlockType} whose {@code since} version is
 * 1.6.2 or older — the same set as 1.6.4 in practice. The
 * representative vanilla 1.6.2 server jar (SHA-1
 * {@code 01b6ea555c6978e6713e2a2dfd7fe19b1449ca54}, verified against
 * piston-meta) is downloaded at runtime, never committed. This
 * generation has no datapacks, no packet compression and no
 * DataVersion concept; pack_format is 1 and the internal server
 * runs on Java 8.
 */
public final class V1_6_2Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.6.2";

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

    /** The LEGACY protocol number (74) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_6_2;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
