package io.detergentmc.adapter.v1_8;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.8 - 1.8.9 (protocol 47).
 *
 * <p>Protocol 47 covers all ten 1.8.x releases (1.8 through 1.8.9;
 * verified against each real vanilla server's Status answer, which
 * reports protocol 47 for every one of them). The representative
 * vanilla 1.8.9 server jar (SHA-1
 * {@code b58b2ceb36e01bcd8dbf49c8fb66c55a9f0676cd}, 8,320,755 bytes,
 * verified against the official piston-meta manifest) is downloaded
 * at runtime and never committed. This generation predates the
 * data version concept entirely (DataVersion arrived in 1.9) and
 * ships no data generator.
 *
 * <p>1.8 is the largest generational gap the protocol module bridges:
 * chunks have no palette (block values are packed as fixed 2-byte
 * little-endian {@code (numericId << 4) | meta} entries under a u16
 * bit mask, and initial chunks arrive in Map Chunk Bulk packets),
 * entity metadata uses a bitfield header byte (3 bits of type plus
 * 5 bits of key, terminator 0x7f, 8 types), and entity coordinates
 * are i32 fixed point instead of f64. Registries, however, share
 * every numeric id with 1.9 for names present in both (the 1.9
 * additions - end / purpur blocks, shield, elytra and friends - are
 * approximated by the protocol module's mapping tables). Which
 * blocks exist in 1.8.9: every {@link BlockType} whose {@code since}
 * version is 1.8.9 or older. Everything 1.9 and later added does not
 * exist here, and {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced
 * after 1.8.9 is automatically reported as unsupported instead of
 * silently mapping to a wrong id.
 *
 * <p>1.8 also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.8.x backend (see docs/PROXY-1.8.9.md). The
 * resource pack format is 1 with the old layout ({@code .lang}
 * files, {@code textures/blocks/}).
 */
public final class V1_8Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.8.9";
    public static final int PROTOCOL_VERSION = 47;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn18(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.8.9, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn18(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.8.9" < "1.9"}). Missing segments count as
     * zero, so {@code "1.8"} equals {@code "1.8.0"}.
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
