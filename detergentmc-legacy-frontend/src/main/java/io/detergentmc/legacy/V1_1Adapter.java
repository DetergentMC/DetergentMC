package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.1 (legacy protocol 23).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.1 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 23 ({@link LegacyProtocol#V1_1}), which lives in the
 * legacy numbering space only.
 *
 * <p>Protocol 23 is 1.1 only (measured by login probing and full
 * packet-table extraction on the real jar, docs/PROXY-1.1.md).
 * The login and respawn packets carry the world seed, mob spawns
 * have two rotation bytes, and chunks travel as arbitrary cuboids
 * (0x33) rather than 16-block sections — the last generation with
 * that chunk form. Blocks: the block set is every {@link BlockType}
 * whose {@code since} version is 1.1 or older.
 *
 * <p>Jar provenance: piston-meta distributes no server jar for
 * 1.1. The measured jar comes from the Omniarchive Vault linked
 * from the Minecraft Wiki version page and is byte-identical to
 * the jar inside the official .exe distribution (SHA-1
 * {@code 3dbadea4e8a0923ac5fde9f4b9e98a244ea2589a}). The runtime
 * downloader therefore cannot fetch it automatically; the jar
 * must be placed manually. This generation's login is plaintext
 * (no encryption), and the internal server runs on Java 8.
 */
public final class V1_1Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.1";

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

    /** The LEGACY protocol number (23 = 1.1) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_1;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
