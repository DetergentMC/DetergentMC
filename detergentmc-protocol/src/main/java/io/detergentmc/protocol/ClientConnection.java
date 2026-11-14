package io.detergentmc.protocol;

import java.util.Locale;

/**
 * One connected client, seen by the protocol layer.
 *
 * <p>TODO: the real implementation (Netty pipeline + vanilla packets)
 * arrives with the networking milestone. This interface already fixes
 * the shape: the server only ever needs the client's protocol version
 * and locale to decide how to present content to it.
 */
public interface ClientConnection {

    /** The client's protocol version, e.g. 765 for a 1.20.4 client. */
    int protocolVersion();

    /** The client's preferred locale, e.g. {@code ja} or {@code en}. */
    Locale locale();

    /** Sends (offers) a resource pack to this client. */
    void sendResourcePack(ResourcePack pack);
}
