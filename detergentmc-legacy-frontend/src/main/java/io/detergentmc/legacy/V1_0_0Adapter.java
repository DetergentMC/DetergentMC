package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.0.0 (legacy protocol 22).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.0.0 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 22 ({@link LegacyProtocol#V1_0}), which lives in the
 * legacy numbering space only.
 *
 * <p>Protocol 22 is 1.0.0, the first full release (measured by
 * login probing, the kick strings, and the protocol constant in
 * the jar, docs/PROXY-1.0.md). Against 1.1 the login and respawn
 * packets lose the level-type string, there is no plugin-message
 * packet at all, and the client still sends the raw movement
 * input packet 0x1B that 1.1 removed. Blocks: the block set is
 * every {@link BlockType} whose {@code since} version is 1.0.0
 * or older.
 *
 * <p>Jar provenance: piston-meta distributes no server jar for
 * 1.0.0. The measured jar comes from the Omniarchive Vault linked
 * from the Minecraft Wiki version page and is byte-identical to
 * the jar inside the official .exe distribution (SHA-1
 * {@code 38cafa1bf1e927c5cdb4cfb8cc6a34dfdcfab2b8}). The runtime
 * downloader therefore cannot fetch it automatically; the jar
 * must be placed manually. This generation's login is plaintext
 * (no encryption), and the internal server runs on Java 8.
 */
public final class V1_0_0Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.0.0";

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

    /** The LEGACY protocol number (22 = 1.0.0) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_0;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
