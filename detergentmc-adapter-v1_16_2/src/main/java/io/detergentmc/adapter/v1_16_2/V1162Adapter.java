package io.detergentmc.adapter.v1_16_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.16.2 (protocol 751).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.16.3, 1.16.5, 1.17, 1.17.1, 1.18, 1.19, 1.19.1, 1.19.3, 1.20.1, 1.20.2, 1.20.4,
 * 1.20.5, 1.21, 1.21.2, 1.21.4, 1.21.5, 1.21.6, 1.21.9, 1.21.11 and 26.1
 * adapters (see docs/VERSIONS.md). The vanilla 1.16.2 server jar (SHA-1
 * {@code c5f6fb23c3876461d46ec380421e42b289789530}, 37,942,337 bytes,
 * verified against the official piston-meta manifest and by measuring
 * the downloaded jar) is downloaded at runtime and never
 * committed. Data version 2578, pack format 6 (a single
 * {@code pack_version} number in the jar's {@code version.json}, not a
 * resource/data split). The server requires Java 8 (piston-meta
 * {@code majorVersion: 8}).
 *
 * <p>Which blocks exist in 1.16.2: every {@link BlockType} whose
 * {@code since} version is 1.16.2 or older - 13 of the 19 unified blocks,
 * counted from {@code BlockType.since()} (see
 * {@link io.detergentmc.adapter.v1_16_2.V1162AdapterTest#mapsEveryBlockThatExistsIn1162()}).
 * Amethyst (since 1.17) does not exist here, and neither do the 1.20
 * additions (cherry log, cherry planks, bamboo block, suspicious sand,
 * calibrated sculk sensor), so {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for all six of them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced after
 * 1.16.2 is automatically reported as unsupported instead of silently
 * mapping to a wrong id. The count is 13, the same as 1.16.3 - no unified
 * block has {@code since} 1.16.2, 1.16.3, 1.16.4 or 1.16.5, so the 1.16.2 and
 * 1.16.3 availability sets are identical.
 *
 * <p>1.16.2 is the only version on protocol 751. The 1.16.2 and 1.16.3
 * data-generator reports (blocks and registries) are byte-identical
 * (Stage A note §7.3), and both map to pc/1.16.2 in minecraft-data.
 */
public final class V1162Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.16.2";
    public static final int PROTOCOL_VERSION = 751;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1162(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.16.2, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1162(BlockType block) {
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
