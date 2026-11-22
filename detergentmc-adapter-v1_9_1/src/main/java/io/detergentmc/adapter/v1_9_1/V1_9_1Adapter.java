package io.detergentmc.adapter.v1_9_1;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.9.1 (protocol 108).
 *
 * <p>Protocol 108 belongs to 1.9.1 alone (verified against the real
 * vanilla server's Status answer; 1.9.2 answers 109, 1.9.3 and 1.9.4
 * answer 110). The vanilla 1.9.1 server jar (SHA-1
 * {@code bf95d9118d9b4b827f524c878efd275125b56181}, 8,848,010 bytes,
 * verified against the official piston-meta manifest; the jar
 * predates the version.json convention and ships no data generator;
 * the booted server's level.dat reports data version 175) is
 * downloaded at runtime and never committed.
 *
 * <p>1.9.1 is wire-identical to 1.9.2 (protocol 109): the two real
 * jars' entry lists match exactly and every non-class file is
 * CRC-identical; the eleven differing classes differ only in
 * version strings and one error-message wording, and no registry
 * name string differs (Stage A measurement; see
 * docs/PROXY-1.9.1.md). The protocol module therefore delegates
 * every 108 registry mapping and packet handling to the 109 tables.
 * Which blocks exist in 1.9.1: every {@link BlockType} whose
 * {@code since} version is 1.9.1 or older. Everything 1.9.2 and
 * later added does not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so
 * a future unified block introduced after 1.9.1 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 *
 * <p>1.9.x also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.9.x backend (see docs/PROXY-1.9.1.md). The
 * resource pack format is 2 with the old layout ({@code .lang}
 * files, {@code textures/blocks/}).
 */
public final class V1_9_1Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.9.1";
    public static final int PROTOCOL_VERSION = 108;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn191(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.9.1, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn191(BlockType block) {
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
