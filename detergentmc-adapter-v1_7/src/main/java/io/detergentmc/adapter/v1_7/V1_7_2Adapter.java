package io.detergentmc.adapter.v1_7;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.7.2 - 1.7.5 (protocol 4).
 *
 * <p>Protocol 4 covers the four oldest 1.7 releases (1.7.2, 1.7.3,
 * 1.7.4 and 1.7.5; verified against each real vanilla server's Status
 * answer). It lives in the same module as protocol 5's adapter
 * ({@link V1_7Adapter}) because the two protocols share everything
 * except two wire details. The representative vanilla 1.7.2 server jar
 * (SHA-1 {@code 3716cac82982e7c2eb09f83028b555e9ea606002}, verified
 * against the official piston-meta manifest) is downloaded at runtime
 * and never committed. This generation predates the data version
 * concept entirely, ships no data generator, and has no packet
 * compression mechanism at all.
 *
 * <p>Against protocol 5 (1.7.6 - 1.7.10) the packet ids and the
 * registries are identical (verified by wire capture and by a full
 * client-jar language-key comparison). The only two differences are
 * in the login/spawn surface: UUID strings are 32 hex characters
 * without dashes (Login Success and Named Entity Spawn; protocol 5
 * uses the dashed 36-character form), and Named Entity Spawn carries
 * no property list (the name is followed directly by the coordinates;
 * protocol 5 has a property array). The protocol module implements
 * protocol 4 as the protocol 5 path plus those two swaps.
 *
 * <p>Which blocks exist in 1.7.2: every {@link BlockType} whose
 * {@code since} version is 1.7.2 or older, decided from
 * {@code BlockType.since()} exactly as in {@link V1_7Adapter}. No
 * blocks were added by the 1.7.3 - 1.7.10 point releases, so the
 * resulting set matches protocol 5's; the check is still done against
 * this adapter's own version so a future unified block introduced
 * after 1.7.2 is reported as unsupported instead of silently mapping
 * to a wrong id.
 *
 * <p>1.7 also predates datapacks entirely: the {@code /trigger}
 * bridge datapack cannot exist on this version, and 1.7 clients are
 * offered a resource pack through the {@code MC|RPack} plugin message
 * rather than a dedicated packet (see docs/PROXY-1.7.2.md).
 */
public final class V1_7_2Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.7.2";
    public static final int PROTOCOL_VERSION = 4;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (V1_7Adapter.compareVersions(block.since(), MINECRAFT_VERSION) <= 0) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    @Override
    public String minecraftVersion() {
        return MINECRAFT_VERSION;
    }

    @Override
    public int protocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
