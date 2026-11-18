package io.detergentmc.adapter.v1_16_5;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.16.4 (protocol 754).
 *
 * <p>1.16.4 shares protocol 754 with 1.16.5, so this adapter lives in
 * the same module as {@link V1165Adapter}, the same way 1.18.1 shares
 * protocol 757 with 1.18 (see {@code V1181Adapter}). This module contains
 * mapping tables only. It contains no Mojang code and no NMS calls - the
 * real binding via the official Mojang mappings is TODO (see
 * docs/VERSIONS.md). The vanilla 1.16.4 server jar (SHA-1
 * {@code 35139deedbd5182953cf1caa23835da59ca3d7cd}, 37,961,464 bytes,
 * verified against the official piston-meta manifest and by measuring
 * the downloaded jar) is downloaded at runtime and never committed.
 *
 * <p>1.16.4 is a bugfix release under 1.16.5 (data version 2584 vs 2586,
 * same protocol 754 and same pack format 6, verified from the
 * version.json inside each jar). The data-generator reports
 * (blocks and registries) generated from the two jars are byte-identical
 * (Stage A note §7.4), and minecraft-data maps both versions to
 * pc/1.16.2 for protocol, blocks, items and biomes (enchantments
 * pc/1.16.4), so this adapter treats 1.16.4 as identical to 1.16.5 for
 * block availability. No unified block has {@code since} 1.16.4 or
 * 1.16.5, so answering like 1.16.5 is exact, not an approximation.
 */
public final class V1164Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.16.4";
    public static final int PROTOCOL_VERSION = 754;

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
        if (V1165Adapter.availableIn1165(block)) {
            return Optional.of(block.minecraftId());
        }
        return Optional.empty();
    }
}
