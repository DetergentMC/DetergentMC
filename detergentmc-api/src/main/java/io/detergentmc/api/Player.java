package io.detergentmc.api;

import java.util.Locale;
import java.util.UUID;

/** A connected player, seen through the version-independent API. */
public interface Player {

    String name();

    UUID uuid();

    /** The player's preferred locale (from the client settings). */
    Locale locale();

    /** Sends a chat message to this player. */
    void sendMessage(String message);
}
