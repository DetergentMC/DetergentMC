package io.detergentmc.adapter.v1_14_1;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.14.1 (protocol 480).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the other version adapters (see
 * docs/VERSIONS.md). The vanilla 1.14.1 server jar (SHA-1
 * {@code ed76d597a44c5266be2a7fcd77a8270f1f0bc118}, verified against the
 * official piston-meta manifest; the jar itself also records protocol
 * 480, pack_format resource 4 / data 4 and Java 8 (jre-legacy) in its
 * version.json) is downloaded at runtime and never committed.
 *
 * <p>Which blocks exist in 1.14.1: every {@link BlockType} whose
 * {@code since} version is 1.14.1 or older. The 1.14.1 and 1.14.2
 * data-generator registry dumps are identical in every category
 * (blocks.json and commands.json are byte-identical, and registries.json
 * matches in all 36 categories including entry order), and 1.15
 * (Buzzy Bees) added bee_nest, beehive, honey_block and honeycomb_block,
 * so those do not exist here, and {@link #toMinecraftId(BlockType)}
 * answers {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced after
 * 1.14.1 is automatically reported as unsupported instead of silently
 * mapping to a wrong id.
 */
public final class V1141Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.14.1";
    public static final int PROTOCOL_VERSION = 480;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1141(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.14.1, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1141(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.14" < "1.14.1" < "1.15"}). Missing segments
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
