package io.detergentmc.adapter.v1_14;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.14 (protocol 477).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the other version adapters (see
 * docs/VERSIONS.md). The vanilla 1.14 server jar (SHA-1
 * {@code f1a0073671057f01aa843443fef34330281333ce}, verified against the
 * official piston-meta manifest; the jar itself also records protocol
 * 477, pack_format resource 4 / data 4 and Java 8 (jre-legacy) in its
 * version.json) is downloaded at runtime and never committed.
 *
 * <p>Which blocks exist in 1.14: every {@link BlockType} whose
 * {@code since} version is 1.14 or older. The 1.14 and 1.14.1
 * data-generator registry dumps match on the wire (blocks.json and
 * commands.json are byte-identical, and registries.json differs only in
 * memory_module_type, which is not a wire registry), and 1.15
 * (Buzzy Bees) added bee_nest, beehive, honey_block and honeycomb_block,
 * so those do not exist here, and {@link #toMinecraftId(BlockType)}
 * answers {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced after
 * 1.14 is automatically reported as unsupported instead of silently
 * mapping to a wrong id.
 */
public final class V114Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.14";
    public static final int PROTOCOL_VERSION = 477;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn114(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.14, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn114(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.14" < "1.14" < "1.15"}). Missing segments
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
