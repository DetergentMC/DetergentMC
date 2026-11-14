package io.detergentmc.adapter.v1_21_2;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.21.3 (protocol 768).
 *
 * <p>1.21.3 shares protocol 768 with 1.21.2, so this adapter lives in
 * the same module as {@link V1212Adapter}, the same way 1.20.5 and
 * 1.20.6 share protocol 766 and 1.21 and 1.21.1 share protocol 767.
 * This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO (see docs/VERSIONS.md). The vanilla 1.21.3 server jar (SHA-1
 * {@code 45810d238246d90e811d896f87b14695b7fb6839}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>Which blocks exist in 1.21.3: every {@link BlockType} whose
 * {@code since} version is 1.21.3 or older. 1.21.3 added no new blocks
 * over 1.21.2, so every unified block known today is present here.
 * Blocks added after 1.21.3 do not exist here, and
 * {@link #toMinecraftId(BlockType)} answers {@link Optional#empty()}
 * for them. The check is done against {@code BlockType.since()}, so a
 * future unified block introduced after 1.21.3 is automatically
 * reported as unsupported instead of silently mapping to a wrong id.
 */
public final class V1213Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.21.3";
    public static final int PROTOCOL_VERSION = 768;

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (availableIn1213(block)) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

    /**
     * Whether this unified block exists in Minecraft 1.21.3, decided
     * from the version that introduced it ({@link BlockType#since()}).
     */
    static boolean availableIn1213(BlockType block) {
        return V1212Adapter.compareVersions(block.since(), MINECRAFT_VERSION) <= 0;
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
