package io.detergentmc.adapter.v1_18;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.18 (protocol 757).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.19, 1.19.1, 1.19.3, 1.20.1, 1.20.2, 1.20.4,
 * 1.20.5, 1.21, 1.21.2, 1.21.4, 1.21.5, 1.21.6, 1.21.9, 1.21.11 and 26.1
 * adapters (see docs/VERSIONS.md). The vanilla 1.18 server jar (SHA-1
 * {@code 3cf24a8694aca6267883b17d934efacc5e44440d}, 46,323,386 bytes,
 * verified against the official piston-meta manifest and by measuring
 * the downloaded jar) is downloaded at runtime and never
 * committed. Data version 2860, pack format resource 8 / data 8
 * (both measured from the jar's {@code version.json}). The server
 * requires Java 17 (piston-meta {@code majorVersion: 17}, also in the
 * jar's {@code version.json}).
 *
 * <p>Which blocks exist in 1.18: every {@link BlockType} whose
 * {@code since} version is 1.18 or older - 14 of the 19 unified blocks.
 * The 1.20 additions (cherry log, cherry planks, bamboo block,
 * suspicious sand, calibrated sculk sensor) do not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so a
 * future unified block introduced after 1.18 is automatically reported
 * as unsupported instead of silently mapping to a wrong id. No unified
 * block has {@code since} 1.18 or 1.19, so the 1.18 additions (deep
 * slate variants and the like) are not in the unified {@link BlockType}
 * enum yet either.
 *
 * <p>1.18 is the first version on protocol 757. 1.18.1 shares protocol
 * 757 (see {@link io.detergentmc.adapter.v1_18.V1181Adapter}), the same
 * way 1.21.1 shares protocol 767 with 1.21.
 */
public final class V118Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.18";
    public static final int PROTOCOL_VERSION = 757;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn118(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.18, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn118(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.17" < "1.18" < "1.19" < "1.20"}). Missing segments
     * count as zero, so {@code "1.20"} equals {@code "1.20.0"}.
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
