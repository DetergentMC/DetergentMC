package io.detergentmc.adapter.v1_26_1;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.Optional;

/**
 * Version adapter for Minecraft 26.1.1 (protocol 775).
 *
 * <p>26.1.1 shares protocol 775 with 26.1, so this adapter lives in
 * the same module as {@link V1261Adapter}, the same way 1.21.1 shares
 * protocol 767 with 1.21. This module contains mapping tables only.
 * It contains no Mojang code and no NMS calls - the real binding via
 * the official Mojang mappings is TODO (see docs/VERSIONS.md). The
 * vanilla 26.1.1 server jar (SHA-1
 * {@code 49c8195703ad0ba4f0a4efbccfd85a4a8ca57431}, verified against the
 * official piston-meta manifest) is downloaded at runtime and never
 * committed.
 *
 * <p>26.1.1 is a bugfix release over 26.1 (data version 4788 vs 4786,
 * same protocol 775 and same pack formats - resource 84.0, data 101.1,
 * verified from the version.json inside each jar). minecraft-data has
 * no separate 26.1.1 registry data, so this adapter treats 26.1.1 as
 * identical to 26.1 for block availability.
 */
public final class V12611Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "26.1.1";
    public static final int PROTOCOL_VERSION = 775;

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
        if (V1261Adapter.availableIn261(block)) {
            return Optional.of(block.minecraftId());
        }
        return Optional.empty();
    }
}
