package io.detergentmc.legacy;

/**
 * The legacy server-list ping (the "section 1" protocol). A pre-Netty
 * client sends 0xFE (optionally followed by 0x01 and a plugin-message
 * host probe); the server answers with a 0xFF kick packet whose string
 * is {@code §1\0<protocol>\0<version>\0<motd>\0<online>\0<max>}.
 *
 * <p>Measured against the real vanilla 1.6.4 server during the
 * pre-Netty survey: its response was
 * {@code §1\0 78\0 1.6.4\0 <motd>\0 0\0 20}.
 */
public final class LegacyPing {

    private LegacyPing() {
    }

    /** Builds the 0xFF response packet bytes for a section-1 ping. */
    public static byte[] section1Response(int legacyProtocol, String versionName,
                                          String motd, int online, int max) {
        String payload = "§1\0" + legacyProtocol + "\0" + versionName + "\0"
                + (motd != null ? motd : "") + "\0" + online + "\0" + max;
        return new LegacyWire.Writer(0xFF).string(payload).bytes();
    }

    /**
     * The older (pre-1.4) ping response form: a kick packet whose
     * string is {@code <motd>§<online>§<max>}. Kept for completeness;
     * protocol-78 clients use the section-1 form.
     */
    public static byte[] oldResponse(String motd, int online, int max) {
        String payload = (motd != null ? motd : "") + "§" + online + "§" + max;
        return new LegacyWire.Writer(0xFF).string(payload).bytes();
    }
}
