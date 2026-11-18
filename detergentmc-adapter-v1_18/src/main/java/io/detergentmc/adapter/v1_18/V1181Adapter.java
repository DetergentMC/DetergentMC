package io.detergentmc.adapter.v1_18;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.18.1 (protocol 757).
 *
 * <p>1.18.1 shares protocol 757 with 1.18, so this adapter lives in
 * the same module as {@link V118Adapter}, the same way 1.21.1 shares
 * protocol 767 with 1.21 and 26.1.1 shares protocol 775 with 26.1.
 * This module contains mapping tables only. It contains no Mojang code
 * and no NMS calls - the real binding via the official Mojang mappings
 * is TODO (see docs/VERSIONS.md). The vanilla 1.18.1 server jar (SHA-1
 * {@code 125e5adf40c659fd3bce3e66e67a16bb49ecc1b9}, 46,324,407 bytes,
 * verified against the official piston-meta manifest and by measuring
 * the downloaded jar) is downloaded at runtime and never committed.
 *
 * <p>1.18.1 is a bugfix release over 1.18 (data version 2865 vs 2860,
 * same protocol 757 and same pack formats - resource 8, data 8,
 * verified from the version.json inside each jar). minecraft-data has
 * no separate 1.18.1 registry data (dataPaths.json maps 1.18.1 to
 * pc/1.18), so this adapter treats 1.18.1 as identical to 1.18 for
 * block availability.
 */
public final class V1181Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.18.1";
    public static final int PROTOCOL_VERSION = 757;

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
        if (V118Adapter.availableIn118(block)) {
            return Optional.of(block.minecraftId());
        }
        return Optional.empty();
    }
}
