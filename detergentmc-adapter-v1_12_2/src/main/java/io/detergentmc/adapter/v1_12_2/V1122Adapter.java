package io.detergentmc.adapter.v1_12_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.12.2 (protocol 340).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the other version adapters (see
 * docs/VERSIONS.md). The vanilla 1.12.2 server jar (SHA-1
 * {@code 886945bfb2b978778c3a0288fd7fab09d315b25f}, 30,222,121 bytes,
 * verified against the official piston-meta manifest; the jar predates
 * the version.json convention and ships no data generator, so the
 * registries were taken from the minecraft-data 1.12 Burger extract
 * cross-checked against the real jar's own registry classes) is
 * downloaded at runtime and never committed.
 *
 * <p>1.12.2 is the last pre-Flattening version: on the wire a block is
 * a numeric id plus a 4-bit metadata value ({@code (id << 4) | meta},
 * 254 block types, about 1,328 valid combinations), not a block state.
 * The protocol module's 340 tables carry that semantic mapping; this
 * adapter only answers availability by version. Which blocks exist in
 * 1.12.2: every {@link BlockType} whose {@code since} version is
 * 1.12.2 or older. Everything the Flattening and later updates added -
 * the split wool/sign/coral families as separate blocks, kelp, seagrass,
 * conduit and the rest - does not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()} for
 * them. The check is done against {@code BlockType.since()}, so a future
 * unified block introduced after 1.12.2 is automatically reported as
 * unsupported instead of silently mapping to a wrong id.
 *
 * <p>1.12.2 also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.12.2 backend (see docs/PROXY-1.12.2.md). The
 * resource pack format is 3 with the old layout ({@code .lang} files,
 * {@code textures/blocks/}).
 */
public final class V1122Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.12.2";
    public static final int PROTOCOL_VERSION = 340;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1122(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.12.2, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1122(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.12.2" < "1.13"}). Missing segments count as
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
