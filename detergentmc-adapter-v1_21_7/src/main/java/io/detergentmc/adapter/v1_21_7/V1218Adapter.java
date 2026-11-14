package io.detergentmc.adapter.v1_21_7;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.21.8 (protocol 772).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.20.4, 1.20.1, 1.20.2, 1.20.5, 1.21,
 * 1.21.2, 1.21.4, 1.21.5 and 1.21.6 adapters (see docs/VERSIONS.md). The vanilla 1.21.8 server jar (SHA-1
 * {@code 6bce4ef400e4efaa63a13d5e6f6b500be969ef81}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 1.21.8: every {@link BlockType} whose
 * {@code since} version is 1.21.8 or older. 1.21.8 added no blocks over
 * 1.21.7 (a security-fix release), and the 1.21.6 addition
 * (the dried ghast - the Chase the Skies content) is not in the
 * unified {@link BlockType} enum yet, so every
 * unified block known today is present here, including the 1.20
 * additions (cherry wood, bamboo blocks, suspicious sand, calibrated
 * sculk sensor), the 1.20.3 additions (crafter, trial spawner), the
 * 1.21.2 additions (the pale oak woodset, the creaking heart, pale
 * moss), the 1.21.4 additions (resin blocks, the eyeblossom) and the
 * 1.21.5 additions (the bush, short and tall dry grass, the cactus
 * flower, wildflowers, leaf litter, the firefly bush, the test block
 * and the test instance block). Blocks added in 1.21.8 or later do
 * not exist here until they are added to {@link BlockType}, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so
 * a future unified block introduced after 1.21.8 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 *
 * <p>1.21.7 and 1.21.8 share protocol 772, so this module has two
 * adapter classes (like 1.21.2 and 1.21.3, which share protocol 768
 * in the v1_21_2 module).
 */
public final class V1218Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.21.8";
    public static final int PROTOCOL_VERSION = 772;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1218(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.21.8, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1218(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.20" < "1.20.1" < "1.21"}). Missing segments
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
