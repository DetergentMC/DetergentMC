package io.detergentmc.adapter.v1_9_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.9.2 (protocol 109).
 *
 * <p>Protocol 109 belongs to 1.9.2 alone (verified against the real
 * vanilla server's Status answer; 1.9.3 and 1.9.4 answer 110). The
 * vanilla 1.9.2 server jar (SHA-1
 * {@code 2b95cc7b136017e064c46d04a5825fe4cfa1be30}, 8,848,002 bytes,
 * verified against the official piston-meta manifest; the jar
 * predates the version.json convention and ships no data generator;
 * the booted server's level.dat reports data version 176) is
 * downloaded at runtime and never committed.
 *
 * <p>The registries are exactly those of 1.9.4 (protocol 110): the
 * minecraft-data dataPaths for 1.9.2 and 1.9.4 both resolve to the
 * same pc/1.9 registry set, and the two real jars' en_US.lang files
 * differ only in the five {@code /stopsound} command description
 * lines added in 1.9.3 - 214 blocks, 365 items, 444 sounds. On the
 * wire, 1.9.2 differs from 110 in exactly two ways, both from the
 * 1.9.3 changes: the clientbound Update Sign packet still exists
 * (0x46, shifting the six following packet ids by one), and Chunk
 * Data has no blockEntities field yet. The protocol module therefore
 * delegates every 109 registry mapping to the 110 tables and only
 * re-implements the packet-id column and the chunk tail; see
 * docs/PROXY-1.9.2.md. Which blocks exist in 1.9.2: every
 * {@link BlockType} whose {@code since} version is 1.9.2 or older.
 * Everything 1.10 and later added does not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so
 * a future unified block introduced after 1.9.2 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 *
 * <p>1.9.x also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.9.x backend (see docs/PROXY-1.9.2.md). The
 * resource pack format is 2 with the old layout ({@code .lang}
 * files, {@code textures/blocks/}).
 */
public final class V1_9_2Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.9.2";
    public static final int PROTOCOL_VERSION = 109;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn192(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.9.2, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn192(BlockType block) {
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
