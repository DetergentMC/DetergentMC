package io.detergentmc.legacy;

/**
 * Configuration of the legacy (pre-Netty) frontend.
 *
 * @param port         TCP port the frontend listens on (separate from
 *                     the core's modern port; never the same socket)
 * @param mode         BRIDGE (legacy clients join the core as
 *                     synthetic protocol-5 clients) or DIRECT (the
 *                     backend itself is a legacy vanilla)
 * @param coreHost     bridge mode: host of the core proxy
 * @param corePort     bridge mode: port of the core proxy
 * @param backendHost  direct mode: host of the internal vanilla
 * @param backendPort  direct mode: port of the internal vanilla
 * @param motd         MOTD used in legacy ping responses
 * @param versionName  version string used in legacy ping responses
 * @param maxPlayers   max-players figure used in legacy ping responses
 */
public record LegacyFrontendConfig(
        int port,
        Mode mode,
        String coreHost,
        int corePort,
        String backendHost,
        int backendPort,
        String motd,
        String versionName,
        int maxPlayers) {

    /** Default listen port (25565 proxy, 25566 internal, 25567 packs). */
    public static final int DEFAULT_PORT = 25568;

    public enum Mode {
        BRIDGE,
        DIRECT
    }

    public static LegacyFrontendConfig bridge(int port, String coreHost, int corePort,
                                                String motd, int maxPlayers) {
        return new LegacyFrontendConfig(port, Mode.BRIDGE, coreHost, corePort,
                null, 0, motd, "1.6.4", maxPlayers);
    }

    public static LegacyFrontendConfig direct(int port, String backendHost, int backendPort,
                                              String motd, int maxPlayers) {
        return direct(port, backendHost, backendPort, motd, maxPlayers, "1.6.4");
    }

    /**
     * Direct mode against a specific legacy backend version. In
     * direct mode the version name doubles as the backend's identity:
     * it selects the legacy protocol spoken to the backend and the
     * version advertised in ping responses.
     */
    public static LegacyFrontendConfig direct(int port, String backendHost, int backendPort,
                                              String motd, int maxPlayers,
                                              String backendVersionName) {
        return new LegacyFrontendConfig(port, Mode.DIRECT, null, 0,
                backendHost, backendPort, motd, backendVersionName, maxPlayers);
    }

    /**
     * The legacy protocol of the configured version name (the backend
     * version in direct mode, the advertised version in bridge mode),
     * falling back to the newest supported legacy protocol.
     */
    public int legacyProtocol() {
        int protocol = LegacyProtocol.protocolForVersionName(versionName);
        return protocol > 0 ? protocol : LegacyProtocol.V1_6_4;
    }
}
