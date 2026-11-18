package io.detergentmc.adapter.v1_16_5;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.16.5 (protocol 754).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.17, 1.17.1, 1.18, 1.19, 1.19.1, 1.19.3, 1.20.1, 1.20.2, 1.20.4,
 * 1.20.5, 1.21, 1.21.2, 1.21.4, 1.21.5, 1.21.6, 1.21.9, 1.21.11 and 26.1
 * adapters (see docs/VERSIONS.md). The vanilla 1.16.5 server jar (SHA-1
 * {@code 1b557e7b033b583cd9f66746b7a9ab1ec1673ced}, 37,962,360 bytes,
 * verified against the official piston-meta manifest and by measuring
 * the downloaded jar) is downloaded at runtime and never
 * committed. Data version 2586, pack format 6 (a single
 * {@code pack_version} number in the jar's {@code version.json}, not a
 * resource/data split). The server requires Java 8 (piston-meta
 * {@code majorVersion: 8}).
 *
 * <p>Which blocks exist in 1.16.5: every {@link BlockType} whose
 * {@code since} version is 1.16.5 or older - 13 of the 19 unified blocks,
 * counted from {@code BlockType.since()} (see
 * {@link io.detergentmc.adapter.v1_16_5.V1165AdapterTest#mapsEveryBlockThatExistsIn1165()}).
 * Amethyst (since 1.17) does not exist here, and neither do the 1.20
 * additions (cherry log, cherry planks, bamboo block, suspicious sand,
 * calibrated sculk sensor), so {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for all six of them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced after
 * 1.16.5 is automatically reported as unsupported instead of silently
 * mapping to a wrong id. The count is 13, not the 14 of 1.17 - the one
 * difference is the amethyst block, the only unified block introduced
 * in 1.17.
 *
 * <p>1.16.5 is the newest version on protocol 754. 1.16.4 shares protocol
 * 754 (see {@link io.detergentmc.adapter.v1_16_5.V1164Adapter}), the same
 * way 1.18.1 shares protocol 757 with 1.18.
 */
public final class V1165Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.16.5";
    public static final int PROTOCOL_VERSION = 754;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1165(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.16.5, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1165(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.16.5" < "1.17" < "1.17.1" < "1.18" < "1.19" < "1.20"}). Missing segments
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
