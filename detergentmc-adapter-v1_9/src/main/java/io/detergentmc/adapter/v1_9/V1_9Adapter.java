package io.detergentmc.adapter.v1_9;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.9 (protocol 107).
 *
 * <p>Protocol 107 belongs to 1.9 alone (verified against the real
 * vanilla server's Status answer; 1.9.1 answers 108, 1.9.2 answers
 * 109). The vanilla 1.9 server jar (SHA-1
 * {@code b4d449cf2918e0f3bd8aa18954b916a4d1880f0d}, 8,848,015 bytes,
 * verified against the official piston-meta manifest; the jar
 * predates the version.json convention and ships no data generator;
 * the booted server's level.dat reports data version 169) is
 * downloaded at runtime and never committed.
 *
 * <p>1.9 differs from 1.9.1 (protocol 108) in exactly one wire
 * detail: the Join Game dimension is a signed byte in 107 and an
 * int from 108 on (verified by capturing the real 1.9 server's
 * Join Game packet and by a full protocol.json comparison, which
 * shows this single type difference; registries are identical, and
 * the attribute {@code generic.armorToughness} added in 108 travels
 * as a name string this generation does not map, so it has no
 * effect on the mapping tables). The protocol module therefore
 * delegates every 107 registry mapping to the 108 tables and only
 * re-encodes the Join Game dimension. A 107 backend's Chunk Data
 * additionally carries large zero padding inside its data length
 * (measured: 153,600 zero bytes in one captured chunk); the chunk
 * reader reads sections sequentially and discards the remainder,
 * so the padding needs no special handling. Which blocks exist in
 * 1.9: every {@link BlockType} whose {@code since} version is 1.9
 * or older. Everything 1.9.1 and later added does not exist here,
 * and {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced
 * after 1.9 is automatically reported as unsupported instead of
 * silently mapping to a wrong id.
 *
 * <p>1.9.x also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.9.x backend (see docs/PROXY-1.9.md). The
 * resource pack format is 2 with the old layout ({@code .lang}
 * files, {@code textures/blocks/}).
 */
public final class V1_9Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.9";
    public static final int PROTOCOL_VERSION = 107;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn19(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.9, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn19(BlockType block) {
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
