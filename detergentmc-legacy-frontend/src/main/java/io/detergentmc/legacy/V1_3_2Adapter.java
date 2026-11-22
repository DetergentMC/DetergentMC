package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.3.2 (legacy protocol 39).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.3.2 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 39 ({@link LegacyProtocol#V1_3_2}), which lives in the
 * legacy numbering space only.
 *
 * <p>Protocol 39 also covers 1.3.1 (measured on the real jars;
 * the widespread "1.3.1 = protocol 38" table entry is wrong) —
 * see {@link V1_3_1Adapter}, which reports the same protocol 39.
 *
 * <p>Blocks: the block set is every {@link BlockType} whose
 * {@code since} version is 1.3.2 or older. The representative
 * vanilla 1.3.2 server jar (SHA-1
 * {@code 3de2ae6c488135596e073a9589842800c9f53bfe}, verified against
 * piston-meta) is downloaded at runtime, never committed. This
 * generation has no datapacks, no packet compression, no scoreboard
 * and no DataVersion concept; texture packs travel as MC|TPack
 * plugin messages and the internal server runs on Java 8.
 */
public final class V1_3_2Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.3.2";

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

    /** The LEGACY protocol number (39 = 1.3.2 and 1.3.1) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_3_2;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
