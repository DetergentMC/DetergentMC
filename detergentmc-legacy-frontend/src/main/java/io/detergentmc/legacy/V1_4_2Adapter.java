package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.4.2 (legacy protocol 47).
 *
 * <p>Same shape and discipline as {@link V1_6_4Adapter}: the adapter
 * lives in the legacy frontend module because a 1.4.2 backend is
 * served by the frontend's direct mode, never by the Netty proxy.
 * {@link #protocolVersion()} returns the <em>legacy</em> protocol
 * number 47 ({@link LegacyProtocol#V1_4_2}), which lives in the
 * legacy numbering space only. <b>It is NOT the Netty protocol 47
 * (Minecraft 1.8)</b> — the two 47s are kept apart by the collision
 * discipline in {@link LegacyProtocol}, and this class is named
 * after the version (V1_4_2), never as a bare "47".
 *
 * <p>Blocks: the block set is every {@link BlockType} whose
 * {@code since} version is 1.4.2 or older — the same set as 1.4.5,
 * whose block registry is identical to 1.4.2's (measured,
 * docs/PROXY-1.4.2.md). The wire layouts are also identical to
 * protocol 49's; the only registry difference is one item (the
 * "wait" record, 2267, arrived in 1.4.4). The representative
 * vanilla 1.4.2 server jar (SHA-1
 * {@code 5be700523a729bb78ef99206fb480a63dcd09825}, verified against
 * piston-meta) is downloaded at runtime, never committed. This
 * generation has no datapacks, no packet compression, no scoreboard
 * and no DataVersion concept; texture packs travel as MC|TPack
 * plugin messages and the internal server runs on Java 8.
 */
public final class V1_4_2Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.4.2";

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

    /** The LEGACY protocol number (47 = 1.4.2) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_4_2;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
