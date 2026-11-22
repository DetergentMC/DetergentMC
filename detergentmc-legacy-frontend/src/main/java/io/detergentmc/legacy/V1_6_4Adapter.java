package io.detergentmc.legacy;

import io.detergentmc.api.BlockType;
import io.detergentmc.api.VersionAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Version adapter for Minecraft 1.6.4 (legacy protocol 78).
 *
 * <p>This adapter lives in the legacy frontend module, not in an
 * adapter module, because a 1.6.4 backend is not served by the Netty
 * proxy at all: the frontend's direct mode terminates legacy clients
 * and speaks to the internal vanilla 1.6.4 as a legacy client (see
 * {@link LegacyFrontendServer}). Registering the adapter lets
 * {@code DetergentServer} accept {@code minecraft-version: 1.6.4}
 * (adapter discovery, Bubble-facing version info) while the Netty
 * protocol machinery stays untouched.
 *
 * <p><b>Numbering discipline:</b> {@link #protocolVersion()} returns
 * the <em>legacy</em> protocol number 78 ({@link LegacyProtocol#V1_6_4}).
 * Legacy numbers live in their own numbering space and must never be
 * compared against the Netty generation's {@code ProtocolVersion}
 * constants; nothing in the Netty path reads this value for a 1.6.4
 * backend because the proxy is not started in direct mode.
 *
 * <p>Blocks: every {@link BlockType} whose {@code since} version is
 * 1.6.4 or older exists here (the 1.7 additions — stained glass,
 * stained glass pane, acacia/dark oak wood and leaves, packed ice,
 * double plants — do not, matching the approximations the frontend
 * applies on the wire). The representative vanilla 1.6.4 server jar
 * (SHA-1 {@code 050f93c1f3fe9e2052398f7bd6aca10c63d64a87}, verified
 * against piston-meta) is downloaded at runtime, never committed.
 * This generation has no datapacks (no /trigger bridge), no packet
 * compression and no DataVersion concept.
 */
public final class V1_6_4Adapter implements VersionAdapter {

    public static final String MINECRAFT_VERSION = "1.6.4";

    private static final Map<BlockType, String> BLOCKS = new EnumMap<>(BlockType.class);

    static {
        for (BlockType block : BlockType.values()) {
            if (compareVersions(block.since(), MINECRAFT_VERSION) <= 0) {
                BLOCKS.put(block, block.minecraftId());
            }
        }
    }

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

    /** The LEGACY protocol number (78) — see the class javadoc. */
    @Override
    public int protocolVersion() {
        return LegacyProtocol.V1_6_4;
    }

    @Override
    public Optional<String> toMinecraftId(BlockType block) {
        return Optional.ofNullable(BLOCKS.get(block));
    }
}
