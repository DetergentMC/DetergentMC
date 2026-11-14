package io.detergentmc.adapter.v1_20_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.20.2 (protocol 764).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.20.4 and 1.20.1 adapters (see
 * docs/VERSIONS.md). The vanilla 1.20.2 server jar (SHA-1
 * {@code 5b868151bd02b41319f54c8d4061b8cae84e665c}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 1.20.2: every {@link BlockType} whose
 * {@code since} version is 1.20.2 or older. 1.20.2 itself added no new
 * blocks over 1.20.1 (its changes were to networking - the configuration
 * phase - and to gameplay such as villager trades), so every unified
 * block known today is present here, including the 1.20 additions
 * (cherry wood, bamboo blocks, suspicious sand, calibrated sculk
 * sensor). Blocks added in 1.20.3 or later - for example the crafter
 * and the trial spawner - do not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so a
 * future unified block introduced after 1.20.2 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 */
public final class V1202Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.20.2";
    public static final int PROTOCOL_VERSION = 764;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1202(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.20.2, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1202(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.20" < "1.20.1" < "1.20.2"}). Missing segments
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
