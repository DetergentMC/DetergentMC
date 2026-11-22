package io.detergentmc.adapter.v1_13;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.13 (protocol 393).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the other version adapters (see
 * docs/VERSIONS.md). The vanilla 1.13 server jar (SHA-1
 * {@code d0caafb8438ebd206f99930cfaecfa6c9a13dca0}, verified against the
 * official piston-meta manifest; the jar has no version.json at its root
 * because it predates that convention, and its data generator records
 * 593 blocks / 8,582 block states and pack_format 4) is downloaded at
 * runtime and never committed.
 *
 * <p>Which blocks exist in 1.13: every {@link BlockType} whose
 * {@code since} version is 1.13 or older. The 1.14 (Village &amp;
 * Pillage) blocks - barrel, bell, blast furnace, campfire, cartography
 * table, composter, fletching table, grindstone, jigsaw, lectern, loom,
 * scaffolding, smoker, stonecutter and the rest - do not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()} for
 * them. The check is done against {@code BlockType.since()}, so a future
 * unified block introduced after 1.13 is automatically reported as
 * unsupported instead of silently mapping to a wrong id. Note the wire
 * conversion itself (protocol module) uses the 1.13 names from the
 * real registry dumps (for example {@code sign} instead of
 * {@code oak_sign}); this adapter only answers availability by version.
 * The 1.13 registry also predates the dead coral blocks and the
 * waterlogged/unstable property additions of 1.13.1/1.13.2, which the
 * protocol module's 393 tables (not this adapter) take care of.
 */
public final class V113Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.13";
    public static final int PROTOCOL_VERSION = 393;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn113(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.13, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn113(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.13" < "1.14"}). Missing segments count as
     * zero, so {@code "1.20"} equals {@code "1.20.0"}.
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
