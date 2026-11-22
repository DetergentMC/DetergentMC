package io.detergentmc.adapter.v1_9_4;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.9.4 (protocol 110).
 *
 * <p>Protocol 110 is shared by two releases - 1.9.3 and 1.9.4 both
 * answer Status with protocol 110 (verified against the real vanilla
 * servers). This adapter therefore represents the whole 110 line,
 * with 1.9.4 as the reference build. The vanilla 1.9.4 server jar
 * (SHA-1 {@code edbb7b1758af33d365bf835eb9d13de005b1e274},
 * 9,399,053 bytes, verified against the official piston-meta
 * manifest; the jar predates the version.json convention and ships
 * no data generator, so the registries were extracted from the real
 * jar's bytecode - 444 sounds, 60 entity registrations, 214 blocks -
 * and cross-checked with a wire capture of a real 1.9.4 login; the
 * booted server's level.dat reports data version 184) is downloaded
 * at runtime and never committed.
 *
 * <p>On the wire 1.9.4 is almost identical to 1.10.x (protocol 210):
 * the packet ids are exactly the same and only three formats differ
 * (Sound Effect and Named Sound Effect carry their pitch as a u8
 * instead of an f32, with a x63.5 scale, and Resource Pack Response
 * still has a leading hash string). The registries predate the 1.10
 * additions (magma, nether wart block, red nether brick, bone block,
 * structure void and the polar bear), so the protocol module
 * converts through the 210 paths with 110-specific tables and
 * approximations; see docs/PROXY-1.9.4.md. Which blocks exist in
 * 1.9.4: every {@link BlockType} whose {@code since} version is
 * 1.9.4 or older. Everything 1.10 and later added does not exist
 * here, and {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced
 * after 1.9.4 is automatically reported as unsupported instead of
 * silently mapping to a wrong id.
 *
 * <p>1.9.x also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.9.x backend (see docs/PROXY-1.9.4.md). The
 * resource pack format is 2 with the old layout ({@code .lang}
 * files, {@code textures/blocks/}).
 */
public final class V1_9_4Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.9.4";
    public static final int PROTOCOL_VERSION = 110;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn194(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.9.4, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn194(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.9" < "1.10"}). Missing segments count as
     * zero, so {@code "1.20"} equals {@code "1.20.0"}.
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
