package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.5.2 (legacy protocol 61).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.5.2 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 61 ({@link LegacyProtocol#V1_5_2}), which lives in the
 * legacy numbering space only.
 *
 * <p>Blocks: the block set is every {@link BlockType} whose
 * {@code since} version is 1.5.2 or older, which excludes the 1.6
 * additions (carpet, hardened and stained clay, coal block, hay
 * bale) measured via the client jars' lang keys
 * (docs/PROXY-1.5.2.md). The representative vanilla 1.5.2 server jar
 * (SHA-1 {@code f9ae3f651319151ce99a0bfad6b34fa16eb6775f}, verified
 * against piston-meta) is downloaded at runtime, never committed.
 * This generation has no datapacks, no packet compression and no
 * DataVersion concept; texture packs travel as MC|TPack plugin
 * messages and the internal server runs on Java 8.
 */
public final class V1_5_2Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.5.2";

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
        return LegacyProtocol.V1_5_2;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
