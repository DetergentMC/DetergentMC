package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.2.4 (legacy protocol 29 — the
 * same protocol number as 1.2.5).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.2.4 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 29 ({@link LegacyProtocol#V1_2_5}), which lives in the
 * legacy numbering space only.
 *
 * <p>1.2.4 is protocol 29, not 28 (measured by login probing on the
 * real jar, and the packet tables and registries of 1.2.4 and
 * 1.2.5 are completely identical — docs/PROXY-1.2.5.md), so this
 * adapter is a secondary for protocol 29, exactly like 1.3.1 for
 * 39. Blocks: the block set is every {@link BlockType} whose
 * {@code since} version is 1.2.4 or older.
 *
 * <p>Jar provenance: piston-meta distributes no server jar for
 * 1.2.4 (its oldest is 1.2.5). The measured 1.2.4 server jar comes
 * from the Omniarchive Vault linked from the Minecraft Wiki
 * version page and is byte-identical to the jar inside the
 * official .exe distribution (SHA-1
 * {@code ab5203296605bdb3a020b8b4ea6c8d03fd3a3e66}). The runtime
 * downloader therefore cannot fetch it automatically; the jar
 * must be placed manually. The internal server runs on Java 8.
 */
public final class V1_2_4Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.2.4";

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

    /** The LEGACY protocol number (29 = 1.2.5 / 1.2.4) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_2_5;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
