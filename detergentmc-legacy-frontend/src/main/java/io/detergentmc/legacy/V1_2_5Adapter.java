package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.2.5 (legacy protocol 29).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.2.5 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 29 ({@link LegacyProtocol#V1_2_5}), which lives in the
 * legacy numbering space only.
 *
 * <p>Protocol 29 covers 1.2.5 and 1.2.4 (measured by login
 * probing on the real jars: 28 is kicked, 29 succeeds, 30 is
 * kicked; the 1.2.4 and 1.2.5 packet tables and registries are
 * completely identical). 1.2.5 is the oldest server jar
 * piston-meta distributes; the 1.2.4 jar and everything below it
 * comes from the Omniarchive Vault route instead (see
 * {@link V1_2_4Adapter}).
 *
 * <p>Blocks: the block set is every {@link BlockType} whose
 * {@code since} version is 1.2.5 or older. The representative
 * vanilla 1.2.5 server jar (SHA-1
 * {@code d8321edc9470e56b8ad5c67bbd16beba25843336}, verified against
 * piston-meta) is downloaded at runtime, never committed. This
 * generation sits below the login-encryption boundary (the login
 * is plaintext), has no chunk-bulk packet (single chunks are
 * announced by a pre-chunk packet), no datapacks, no packet
 * compression, no scoreboard and no DataVersion or pack-format
 * concept; the 1.2.5 client has no texture-pack offer mechanism at
 * all, and the internal server runs on Java 8.
 */
public final class V1_2_5Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.2.5";

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

    /** The LEGACY protocol number (29 = 1.2.5) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_2_5;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
