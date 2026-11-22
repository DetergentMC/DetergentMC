package io.detergentmc.adapter.v1_11_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.11.2 (protocol 316).
 *
 * <p>This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO, the same as for the other version adapters (see
 * docs/VERSIONS.md). The vanilla 1.11.2 server jar (SHA-1
 * {@code f00c294a1576e03fddcac777c3cf4c7d404c4ba4}, 9,720,835 bytes,
 * verified against the official piston-meta manifest; the jar predates
 * the version.json convention and ships no data generator, so the
 * registries were taken from the minecraft-data pc/1.11 Burger extract -
 * which minecraft-data itself resolves as 1.11.2's own data - and
 * cross-checked against the real jar: 493 sounds and 81 entity
 * registrations extracted from its bytecode, and a wire capture of a
 * real 1.11.2 login) is downloaded at runtime and never committed.
 *
 * <p>On the wire 1.11.2 sits one generation below 1.12 (protocol 335):
 * same numeric-id-plus-metadata block encoding
 * ({@code (id << 4) | meta}) and a VarInt Keep Alive, but 1.12's
 * additions do not exist here - no recipe book, no Advancements (the
 * 1.11 generation still uses the old achievements), no concrete,
 * concrete powder or glazed terracotta blocks, no iron nugget or
 * knowledge book items, and no parrot or illusioner entities. The
 * protocol module approximates those towards 316 (concrete family to
 * wool/stained clay, parrot and illusioner to pig) and drops the six
 * recipe-book/advancement packets; see docs/PROXY-1.11.2.md. Which
 * blocks exist in 1.11.2: every {@link BlockType} whose {@code since}
 * version is 1.11.2 or older. Everything 1.12 and later added does not
 * exist here, and {@link #toMinecraftId(BlockType)} answers
 * {@link Optional#empty()} for them. The check is done against
 * {@code BlockType.since()}, so a future unified block introduced
 * after 1.11.2 is automatically reported as unsupported instead of
 * silently mapping to a wrong id.
 *
 * <p>1.11.2 also predates datapacks entirely: the {@code /trigger}
 * bridge datapack the core generates for newer backends cannot exist
 * on this version, so DetergentMC commands that rely on it stay
 * unavailable on a 1.11.2 backend (see docs/PROXY-1.11.2.md). The
 * resource pack format is 3 with the old layout ({@code .lang} files,
 * {@code textures/blocks/}).
 */
public final class V1112Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.11.2";
    public static final int PROTOCOL_VERSION = 316;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1112(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.11.2, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1112(BlockType block) {
        return compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
    }

    /**
     * Compares two dotted version strings numerically, segment by
     * segment ({@code "1.11.2" < "1.12"}). Missing segments count as
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
