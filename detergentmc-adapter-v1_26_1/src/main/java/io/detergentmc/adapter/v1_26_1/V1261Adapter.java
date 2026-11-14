package io.detergentmc.adapter.v1_26_1;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 26.1 (protocol 775).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.20.4, 1.20.1, 1.20.2, 1.20.5, 1.21,
 * 1.21.2, 1.21.4, 1.21.5, 1.21.6, 1.21.9 and 1.21.11 adapters (see docs/VERSIONS.md). The vanilla 26.1 server jar (SHA-1
 * {@code 3872a7f07a1a595e651aef8b058dfc2bb3772f46}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 26.1: every {@link BlockType} whose
 * {@code since} version is 26.1 or older. 26.1 ("Tiny Takeover") added
 * the golden dandelion (and the potted golden dandelion) and the
 * trumpet note block instrument; the golden dandelion is not in the
 * unified {@link BlockType} enum yet, so every unified block known
 * today is present here, including the 1.20 additions (cherry wood,
 * bamboo blocks, suspicious sand, calibrated sculk sensor), the 1.20.3
 * additions (crafter, trial spawner), the 1.21.2 additions (the pale
 * oak woodset, the creaking heart, pale moss), the 1.21.4 additions
 * (resin blocks, the eyeblossom) and the 1.21.5 additions (the bush,
 * short and tall dry grass, the cactus flower, wildflowers, leaf
 * litter, the firefly bush, the test block and the test instance
 * block). Blocks added in 26.1 or later do not exist here until they
 * are added to {@link BlockType}, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so
 * a future unified block introduced after 26.1 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 *
 * <p>26.1 is the first version on protocol 775, and 26.1.1 and 26.1.2
 * share protocol 775, so this module has one adapter class per version
 * string (see {@link V12611Adapter} and {@link V12612Adapter}), the
 * same way 1.21 and 1.21.1 share protocol 767. The version comparison
 * is numeric and segment by segment, so {@code "26.1"} compares
 * greater than every {@code "1.x"} version ({@code 26 > 1} in the
 * first segment) - the year-based versioning needs no special case.
 */
public final class V1261Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "26.1";
    public static final int PROTOCOL_VERSION = 775;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn261(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 26.1, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn261(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.20" < "1.20.1" < "1.21" < "26.1"}). Missing segments
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
