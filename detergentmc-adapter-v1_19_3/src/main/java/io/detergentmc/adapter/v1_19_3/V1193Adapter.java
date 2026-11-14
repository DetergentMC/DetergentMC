package io.detergentmc.adapter.v1_19_3;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.19.3 (protocol 761).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.20.1, 1.20.2, 1.20.4, 1.20.5 and 1.21
 * adapters (see docs/VERSIONS.md). The vanilla 1.19.3 server jar (SHA-1
 * {@code c9df48efed58511cdd0213c56b9013a7b5c9ac1f}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 1.19.3: every {@link BlockType} whose
 * {@code since} version is 1.19.3 or older. The 1.19.4 additions
 * (cherry wood, sniffer, decorated pot, suspicious sand, smithing
 * templates, armor trims) and the 1.20 additions (bamboo blocks,
 * calibrated sculk sensor) do not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so a
 * future unified block introduced after 1.19.3 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 */
public final class V1193Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.19.3";
    public static final int PROTOCOL_VERSION = 761;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1193(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.19.3, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1193(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.19.3" < "1.20" < "1.20.1"}). Missing segments
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
