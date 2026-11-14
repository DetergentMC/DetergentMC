package io.detergentmc.adapter.v1_21_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.21.2 (protocol 768).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.20.4, 1.20.1, 1.20.2, 1.20.5 and 1.21
 * adapters (see docs/VERSIONS.md). The vanilla 1.21.2 server jar (SHA-1
 * {@code 7bf95409b0d9b5388bfea3704ec92012d273c14c}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 1.21.2: every {@link BlockType} whose
 * {@code since} version is 1.21.2 or older. 1.21.2 added blocks over
 * 1.21.1 (the pale oak woodset, the creaking heart, pale moss - the
 * Winter Drop content shipped in 1.21.2), but the unified
 * {@link BlockType} enum does not list them yet, so every unified block
 * known today is present here, including the 1.20 additions (cherry
 * wood, bamboo blocks, suspicious sand, calibrated sculk sensor) and
 * the 1.20.3 additions (crafter, trial spawner). Blocks added in
 * 1.21.2 or later do not exist here until they are added to
 * {@link BlockType}, and {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced
 * after 1.21.2 is automatically reported as unsupported instead of
 * silently mapping to a wrong id.
 *
 * <p>1.21.3 shares protocol 768 with 1.21.2, so it has its own adapter
 * class ({@link V1213Adapter}) in this same module, the same way
 * 1.20.5 and 1.20.6 share protocol 766 and 1.21 and 1.21.1 share
 * protocol 767.
 */
public final class V1212Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.21.2";
    public static final int PROTOCOL_VERSION = 768;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1212(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.21.2, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1212(BlockType block) {
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
