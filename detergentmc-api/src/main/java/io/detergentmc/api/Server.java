package io.detergentmc.api;

/** A version-independent view of the running DetergentMC server. */
public interface Server {

    /** DetergentMC version, e.g. {@code "0.1.0"}. */
    String detergentVersion();

    /** Minecraft version this server build runs, e.g. {@code "1.20.4"}. */
    String minecraftVersion();

    /** Minecraft protocol version, e.g. {@code 765} for 1.20.4. */
    int protocolVersion();

    /** The version adapter that maps the unified API onto this version. */
    VersionAdapter adapter();
}
