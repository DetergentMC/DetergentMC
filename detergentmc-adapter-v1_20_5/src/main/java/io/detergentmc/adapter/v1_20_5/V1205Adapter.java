package io.detergentmc.adapter.v1_20_5;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.20.5 (protocol 766).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.20.4, 1.20.1 and 1.20.2 adapters (see
 * docs/VERSIONS.md). The vanilla 1.20.5 server jar (SHA-1
 * {@code 79493072f65e17243fd36a699c9a96b4381feb91}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 1.20.5: every {@link BlockType} whose
 * {@code since} version is 1.20.5 or older. 1.20.5 added no new blocks
 * over 1.20.4 (its changes were to items and data components, such as
 * the armadillo and wolf armour, and to the pack formats), so every
 * unified block known today is present here, including the 1.20
 * additions (cherry wood, bamboo blocks, suspicious sand, calibrated
 * sculk sensor) and the 1.20.3 additions (crafter, trial spawner).
 * Blocks added in 1.20.6 or later do not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so a
 * future unified block introduced after 1.20.5 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 *
 * <p>1.20.6 shares protocol 766 with 1.20.5, so it has its own adapter
 * class ({@link V1206Adapter}) in this same module, the same way
 * 1.20.3 and 1.20.4 share protocol 765.
 */
public final class V1205Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.20.5";
    public static final int PROTOCOL_VERSION = 766;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1205(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.20.5, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1205(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.20" < "1.20.1" < "1.20.5"}). Missing segments
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
