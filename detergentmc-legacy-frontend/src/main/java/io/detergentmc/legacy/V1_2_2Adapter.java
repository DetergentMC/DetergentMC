package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.2.2 (legacy protocol 28 — the
 * same protocol number as 1.2.3).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.2.2 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 28 ({@link LegacyProtocol#V1_2_3}), which lives in the
 * legacy numbering space only.
 *
 * <p>The 1.2.2 packet table and registries are completely
 * identical to 1.2.3's (measured on the real jars,
 * docs/PROXY-1.2.3.md), so this adapter is a secondary for
 * protocol 28. Blocks: the block set is every {@link BlockType}
 * whose {@code since} version is 1.2.2 or older.
 *
 * <p>Jar provenance: piston-meta distributes no server jar for
 * 1.2.2. The measured jar comes from the Omniarchive Vault linked
 * from the Minecraft Wiki version page and is byte-identical to
 * the jar inside the official .exe distribution (SHA-1
 * {@code 673f50223b363c4c474d520c99104bd8481f0ce6}). The runtime
 * downloader therefore cannot fetch it automatically; the jar
 * must be placed manually. The internal server runs on Java 8.
 */
public final class V1_2_2Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.2.2";

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

    /** The LEGACY protocol number (28 = 1.2.3) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_2_3;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
