package io.detergentmc.adapter.v1_10_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.10.2 (protocol 210).
 *
 * <p>Protocol 210 is shared by three releases - 1.10, 1.10.1 and
 * 1.10.2 all answer Status with protocol 210 (verified against the
 * real vanilla servers; there is no release protocol 201). This
 * adapter therefore represents the whole 1.10 line, with 1.10.2 as
 * the reference build. The vanilla 1.10.2 server jar (SHA-1
 * {@code 3d501b23df53c548254f5e3f66492d178a48db63}, 9,459,897 bytes,
 * verified against the official piston-meta manifest; the jar
 * predates the version.json convention and ships no data generator,
 * so the registries were extracted from the real jar's bytecode -
 * 463 sounds, 61 entity registrations, 219 blocks including the
 * five blocks the stale minecraft-data pc/1.10 copy misses - and
 * cross-checked with a wire capture of a real 1.10.2 login; the
 * booted server's level.dat reports data version 512) is downloaded
 * at runtime and never committed.
 *
 * <p>On the wire 1.10.x is almost identical to 1.11 (protocol 315):
 * the packet ids are exactly the same and only four formats differ
 * (Spawn Entity Living carries its type as a u8 instead of a
 * VarInt, the Title action numbers predate the action bar action,
 * Collect has no pickup count, and Block Placement cursors are
 * i8 0-15 instead of f32). The registries predate the 1.11
 * additions (observer, shulker boxes, totem, shulker shell, iron
 * nugget) and the 1.11 entity variant split, so the protocol module
 * converts through the 315 paths with 210-specific tables and
 * approximations; see docs/PROXY-1.10.2.md. Which blocks exist in
 * 1.10.2: every {@link BlockType} whose {@code since} version is
 * 1.10.2 or older. Everything 1.11 and later added does not exist
 * here, and {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced
 * after 1.10.2 is automatically reported as unsupported instead of
 * silently mapping to a wrong id.
 *
 * <p>1.10.x also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.10.x backend (see docs/PROXY-1.10.2.md). The
 * resource pack format is 2 with the old layout ({@code .lang}
 * files, {@code textures/blocks/}).
 */
public final class V1_10_2Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.10.2";
    public static final int PROTOCOL_VERSION = 210;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1102(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.10.2, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1102(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.10" < "1.11"}). Missing segments count as
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
