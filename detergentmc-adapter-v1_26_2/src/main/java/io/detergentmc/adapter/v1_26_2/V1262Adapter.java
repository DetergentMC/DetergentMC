package io.detergentmc.adapter.v1_26_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 26.2 (protocol 776).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.20.4, 1.20.1, 1.20.2, 1.20.5, 1.21,
 * 1.21.2, 1.21.4, 1.21.5, 1.21.6, 1.21.9, 1.21.11 and 26.1 adapters (see docs/VERSIONS.md). The vanilla 26.2 server jar (SHA-1
 * {@code 823e2250d24b3ddac457a60c92a6a941943fcd6a}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 26.2: every {@link BlockType} whose
 * {@code since} version is 26.2 or older. 26.2 ("Chaos Cubed") added
 * the sulfur and cinnabar block family, potent sulfur and the sulfur
 * caves biome; none of those are in the unified {@link BlockType} enum
 * yet, so every unified block known today is present here, including
 * the 1.20 additions (cherry wood, bamboo blocks, suspicious sand,
 * calibrated sculk sensor), the 1.20.3 additions (crafter, trial
 * spawner), the 1.21.2 additions (the pale oak woodset, the creaking
 * heart, pale moss), the 1.21.4 additions (resin blocks, the
 * eyeblossom), the 1.21.5 additions (the bush, short and tall dry
 * grass, the cactus flower, wildflowers, leaf litter, the firefly
 * bush, the test block and the test instance block) and the 26.1
 * additions (the golden dandelion and the trumpet note block
 * instrument, also not in the unified enum yet). Blocks added in 26.2
 * or later do not exist here until they are added to {@link BlockType},
 * and {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so
 * a future unified block introduced after 26.2 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 *
 * <p>26.2 is the only version on protocol 776 (piston-meta lists no
 * hotfix releases for 26.2), so this module has a single adapter class,
 * unlike 26.1 which has one class per version string. The version
 * comparison is numeric and segment by segment, so {@code "26.2"}
 * compares greater than every {@code "1.x"} version ({@code 26 > 1} in
 * the first segment) - the year-based versioning needs no special case.
 */
public final class V1262Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "26.2";
    public static final int PROTOCOL_VERSION = 776;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn262(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 26.2, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn262(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.20" < "1.20.1" < "1.21" < "26.1" < "26.2"}).
     * Missing segments count as zero, so {@code "1.20"} equals
     * {@code "1.20.0"}.
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
