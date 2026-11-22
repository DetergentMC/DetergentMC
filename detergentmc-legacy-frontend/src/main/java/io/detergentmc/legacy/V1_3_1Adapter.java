package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.3.1 — the same legacy protocol
 * 39 as 1.3.2 (measured on the real 1.3.1 jar: identical packet
 * registrations, layouts and registries; the widespread
 * "1.3.1 = protocol 38" table entry is wrong). The adapter lives
 * in the legacy frontend module because a 1.3.1 backend is served
 * by the frontend's direct mode, never by the Netty proxy.
 *
 * <p>Blocks: the block set is every {@link BlockType} whose
 * {@code since} version is 1.3.1 or older. The representative
 * vanilla 1.3.1 server jar (SHA-1
 * {@code 82563ce498bfc1fc8a2cb5bf236f7da86a390646}, verified against
 * piston-meta) is downloaded at runtime, never committed.
 */
public final class V1_3_1Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.3.1";

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

    /** The LEGACY protocol number (39, shared with 1.3.2) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_3_2;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
