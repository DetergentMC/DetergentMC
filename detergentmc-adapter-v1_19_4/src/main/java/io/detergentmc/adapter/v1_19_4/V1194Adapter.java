package io.detergentmc.adapter.v1_19_4;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.19.4 (protocol 762).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the 1.20.1, 1.20.2, 1.20.4, 1.20.5 and 1.21
 * adapters (see docs/VERSIONS.md). The vanilla 1.19.4 server jar (SHA-1
 * {@code 8f3112a1049751cc472ec13e397eade5336ca7ae}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 1.19.4: every {@link BlockType} whose
 * {@code since} version is 1.19.4 or older. The 1.20 additions (cherry
 * wood, bamboo blocks, suspicious sand, calibrated sculk sensor) do
 * not exist here, and {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced
 * after 1.19.4 is automatically reported as unsupported instead of
 * silently mapping to a wrong id.
 *
 * <p>Note: ViaVersion's mapping-1.19.4.json lists cherry blocks and
 * other 1.20 content. That content was behind the 1.20 experimental
 * datapack in 1.19.4 and is not in a plain 1.19.4 server, so it is
 * correctly excluded here by the {@code since} check.
 */
public final class V1194Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.19.4";
    public static final int PROTOCOL_VERSION = 762;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1194(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.19.4, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1194(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.19.4" < "1.20" < "1.20.1"}). Missing segments
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
