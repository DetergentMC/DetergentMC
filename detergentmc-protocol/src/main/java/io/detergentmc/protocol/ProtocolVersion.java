package io.detergentmc.protocol;

import java.util.logging.Logger;

/**
 * Minecraft protocol version numbers.
 *
 * <p>These numbers are public knowledge (wiki.vg / the Minecraft Wiki).
 * The protocol version is what actually decides whether a client can talk
 * to a server - the display version ("1.20.4") is only a label.
 */
public final class ProtocolVersion {

    private static final Logger LOG = Logger.getLogger(ProtocolVersion.class.getName());

    private ProtocolVersion() {
    }

    /** Minecraft 1.8 - 1.8.9 */
    public static final int V1_8 = 47;
    /** Minecraft 1.12 - 1.12.2 */
    public static final int V1_12_2 = 340;
    /** Minecraft 1.16.5 */
    public static final int V1_16_5 = 754;
    /** Minecraft 1.17 */
    public static final int V1_17 = 755;
    /** Minecraft 1.17.1 */
    public static final int V1_17_1 = 756;
    /** Minecraft 1.18 - 1.18.1 */
    public static final int V1_18 = 757;
    /** Minecraft 1.19 */
    public static final int V1_19 = 759;
    /** Minecraft 1.19.1 - 1.19.2 */
    public static final int V1_19_1 = 760;
    /** Minecraft 1.19.3 */
    public static final int V1_19_3 = 761;
    /** Minecraft 1.19.4 */
    public static final int V1_19_4 = 762;
    /** Minecraft 1.20 - 1.20.1 */
    public static final int V1_20_1 = 763;
    /** Minecraft 1.20.2 */
    public static final int V1_20_2 = 764;
    /** Minecraft 1.20.3 - 1.20.4 */
    public static final int V1_20_4 = 765;
    /** Minecraft 1.20.5 - 1.20.6 */
    public static final int V1_20_5 = 766;
    /** Minecraft 1.21 - 1.21.1 */
    public static final int V1_21 = 767;
    /** Minecraft 1.21.2 - 1.21.3 */
    public static final int V1_21_2 = 768;
    /** Minecraft 1.21.4 */
    public static final int V1_21_4 = 769;
    /** Minecraft 1.21.5 */
    public static final int V1_21_5 = 770;
    /** Minecraft 1.21.6 */
    public static final int V1_21_6 = 771;
    /** Minecraft 1.21.7 - 1.21.8 */
    public static final int V1_21_7 = 772;
    /** Minecraft 1.21.9 - 1.21.10 */
    public static final int V1_21_9 = 773;
    /** Minecraft 1.21.11 */
    public static final int V1_21_11 = 774;
    /** Minecraft 26.1 - 26.1.2 ("Tiny Takeover") */
    public static final int V26_1 = 775;

    /** The newest protocol this skeleton knows about. */
    public static final int LATEST_KNOWN = V26_1;

    /**
     * The Minecraft version DetergentMC runs when {@code detergentmc.yml}
     * has no {@code minecraft-version} entry.
     */
    public static final String DEFAULT_MINECRAFT_VERSION = "1.20.4";

    /**
     * The internal server protocol used when {@code detergentmc.yml} has
     * no {@code minecraft-version} entry: 765 (1.20.4).
     */
    public static final int DEFAULT_SERVER_PROTOCOL = V1_20_4;

    /**
     * Returns the protocol number the DetergentMC server speaks
     * <em>internally</em> for the given Minecraft version — that is, the
     * protocol of the vanilla server started from {@code detergentmc.yml}
     * ({@code minecraft-version}) and the protocol the proxy translates
     * <em>toward</em>.
     *
     * <p>This is the single place that decides the internal protocol, so
     * every component that needs to know the translation direction (the
     * proxy, the client adapters) must read it from here instead of
     * assuming 765:
     *
     * <ul>
     *   <li>{@code "1.16.4"} or {@code "1.16.5"} → {@link #V1_16_5} (754);
     *       1.16.4 and 1.16.5 share protocol 754</li>
     *   <li>{@code "1.17"} → {@link #V1_17} (755)</li>
     *   <li>{@code "1.17.1"} → {@link #V1_17_1} (756)</li>
     *   <li>{@code "1.18"} or {@code "1.18.1"} → {@link #V1_18} (757);
     *       1.18 and 1.18.1 share protocol 757</li>
     *   <li>{@code "1.19"} → {@link #V1_19} (759)</li>
     *   <li>{@code "1.19.1"} or {@code "1.19.2"} → {@link #V1_19_1} (760);
     *       1.19.1 and 1.19.2 share protocol 760</li>
     *   <li>{@code "1.19.3"} → {@link #V1_19_3} (761)</li>
     *   <li>{@code "1.19.4"} → {@link #V1_19_4} (762)</li>
     *   <li>{@code "1.20"} or {@code "1.20.1"} → {@link #V1_20_1} (763).
     *       The 1.20.1 server adapter ({@code V1201Adapter}) runs a real
     *       1.20.1 vanilla server, so the internal protocol must be 763
     *       here — falling back to 765 would make the proxy translate
     *       toward the wrong version.</li>
     *   <li>{@code "1.20.2"} → {@link #V1_20_2} (764)</li>
     *   <li>{@code "1.20.3"} or {@code "1.20.4"} → {@link #V1_20_4} (765);
     *       1.20.3 and 1.20.4 share protocol 765</li>
     *   <li>{@code "1.20.5"} or {@code "1.20.6"} → {@link #V1_20_5} (766);
     *       1.20.5 and 1.20.6 share protocol 766</li>
     *   <li>{@code "1.21"} or {@code "1.21.1"} → {@link #V1_21} (767);
     *       1.21 and 1.21.1 share protocol 767</li>
     *   <li>{@code "1.21.2"} or {@code "1.21.3"} → {@link #V1_21_2} (768);
     *       1.21.2 and 1.21.3 share protocol 768</li>
     *   <li>{@code "1.21.4"} → {@link #V1_21_4} (769)</li>
     *   <li>{@code "1.21.5"} → {@link #V1_21_5} (770)</li>
     *   <li>{@code "1.21.6"} → {@link #V1_21_6} (771)</li>
     *   <li>{@code "1.21.7"} or {@code "1.21.8"} → {@link #V1_21_7} (772);
     *       1.21.7 and 1.21.8 share protocol 772</li>
     *   <li>{@code "1.21.9"} or {@code "1.21.10"} → {@link #V1_21_9} (773);
     *       1.21.9 and 1.21.10 share protocol 773</li>
     *   <li>{@code "1.21.11"} → {@link #V1_21_11} (774)</li>
     *   <li>{@code "26.1"} or {@code "26.1.1"} or {@code "26.1.2"} → {@link #V26_1} (775);
     *       26.1, 26.1.1 and 26.1.2 share protocol 775</li>
     *   <li>anything else (including {@code null} or blank) → 765, with a
     *       warning logged. The server never refuses to start over an
     *       unknown version string here; the version adapter lookup in
     *       {@code DetergentServer} still decides whether the version is
     *       actually supported.</li>
     * </ul>
     *
     * @param minecraftVersion the {@code minecraft-version} value from the
     *                         config, e.g. {@code "1.20.2"} or
     *                         {@code "1.20.4"}
     * @return the internal server protocol number (754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774 or 775)
     */
    public static int serverProtocolFor(String minecraftVersion) {
        if ("1.16.4".equals(minecraftVersion) || "1.16.5".equals(minecraftVersion)) {
            return V1_16_5;
        }
        if ("1.17".equals(minecraftVersion)) {
            return V1_17;
        }
        if ("1.17.1".equals(minecraftVersion)) {
            return V1_17_1;
        }
        if ("1.18".equals(minecraftVersion) || "1.18.1".equals(minecraftVersion)) {
            return V1_18;
        }
        if ("1.19".equals(minecraftVersion)) {
            return V1_19;
        }
        if ("1.19.1".equals(minecraftVersion) || "1.19.2".equals(minecraftVersion)) {
            return V1_19_1;
        }
        if ("1.19.3".equals(minecraftVersion)) {
            return V1_19_3;
        }
        if ("1.19.4".equals(minecraftVersion)) {
            return V1_19_4;
        }
        if ("1.20".equals(minecraftVersion) || "1.20.1".equals(minecraftVersion)) {
            return V1_20_1;
        }
        if ("1.20.2".equals(minecraftVersion)) {
            return V1_20_2;
        }
        if ("1.20.3".equals(minecraftVersion) || "1.20.4".equals(minecraftVersion)) {
            return V1_20_4;
        }
        if ("1.20.5".equals(minecraftVersion) || "1.20.6".equals(minecraftVersion)) {
            return V1_20_5;
        }
        if ("1.21".equals(minecraftVersion) || "1.21.1".equals(minecraftVersion)) {
            return V1_21;
        }
        if ("1.21.2".equals(minecraftVersion) || "1.21.3".equals(minecraftVersion)) {
            return V1_21_2;
        }
        if ("1.21.4".equals(minecraftVersion)) {
            return V1_21_4;
        }
        if ("1.21.5".equals(minecraftVersion)) {
            return V1_21_5;
        }
        if ("1.21.6".equals(minecraftVersion)) {
            return V1_21_6;
        }
        if ("1.21.7".equals(minecraftVersion) || "1.21.8".equals(minecraftVersion)) {
            return V1_21_7;
        }
        if ("1.21.9".equals(minecraftVersion) || "1.21.10".equals(minecraftVersion)) {
            return V1_21_9;
        }
        if ("1.21.11".equals(minecraftVersion)) {
            return V1_21_11;
        }
        if ("26.1".equals(minecraftVersion) || "26.1.1".equals(minecraftVersion) || "26.1.2".equals(minecraftVersion)) {
            return V26_1;
        }
        LOG.warning(() -> "Unknown minecraft-version \"" + minecraftVersion
                + "\"; falling back to the 1.20.4 server protocol (765). "
                + "Supported server versions: 1.16.4, 1.16.5, 1.17, 1.17.1, 1.18, 1.18.1, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, "
                + "1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, "
                + "1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2.");
        return V1_20_4;
    }

    /** True when the client protocol is older than the given version. */
    public static boolean isOlderThan(int clientProtocol, int version) {
        return clientProtocol < version;
    }
}
