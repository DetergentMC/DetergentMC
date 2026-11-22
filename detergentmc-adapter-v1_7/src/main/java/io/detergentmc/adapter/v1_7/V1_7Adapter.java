package io.detergentmc.adapter.v1_7;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.7.6 - 1.7.10 (protocol 5).
 *
 * <p>Protocol 5 covers the five 1.7.6+ releases (1.7.6, 1.7.7, 1.7.8,
 * 1.7.9 and 1.7.10; verified against each real vanilla server's Status
 * answer. The older 1.7.2 - 1.7.5 releases speak protocol 4 and are not
 * covered here). The representative vanilla 1.7.10 server jar (SHA-1
 * {@code 952438ac4e01b4d115c5fc38f891710c4941df29}, 9,605,030 bytes,
 * verified against the official piston-meta manifest) is downloaded
 * at runtime and never committed. This generation predates the data
 * version concept entirely (DataVersion arrived in 1.9), ships no data
 * generator, and has no packet compression mechanism at all.
 *
 * <p>Against 1.8 (protocol 47) every shared packet keeps the same id
 * (95 of 95 common packets; the 12 packets 1.8 added simply do not
 * exist here). The differences are all in field layouts: entity ids
 * are i32 instead of varint, block positions are three separate
 * numbers instead of a packed long, chunk data is grouped by kind
 * (a u8 block id array followed by nibble arrays for metadata, block
 * light, sky light and the add array) instead of 1.8's fixed 2-byte
 * packing, slot NBT is gzip-compressed, Keep Alive is an i32, player
 * info is the old name-based form and named entity spawns carry a
 * UUID string, a name and a property list. Entity metadata headers
 * (bitfield, terminator 0x7f, 8 types) and i32 fixed-point entity
 * coordinates are shared with 1.8. Registries share every numeric id
 * with 1.8 for names present in both (the 1.8 additions - coloured
 * doors, fences and banners, red sandstone, the iron trapdoor and
 * friends - are approximated by the protocol module's mapping
 * tables). Which blocks exist in 1.7.10: every {@link BlockType}
 * whose {@code since} version is 1.7.10 or older. Everything 1.8 and
 * later added does not exist here, and {@link #toMinecraftId(BlockType)}
 * answers {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced
 * after 1.7.10 is automatically reported as unsupported instead of
 * silently mapping to a wrong id.
 *
 * <p>1.7 also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.7.x backend (see docs/PROXY-1.7.10.md). The
 * resource pack format is 1, and 1.7 clients are offered a pack
 * through the {@code MC|RPack} plugin message rather than a dedicated
 * packet (that packet only arrived in 1.8).
 */
public final class V1_7Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.7.10";
    public static final int PROTOCOL_VERSION = 5;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn17(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.7.10, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn17(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.7.10" < "1.8"}). Missing segments count as
     * zero, so {@code "1.7"} equals {@code "1.7.0"}.
     */
    static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
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
