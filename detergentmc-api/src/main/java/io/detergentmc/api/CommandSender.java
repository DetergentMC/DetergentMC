package io.detergentmc.api;

import java.util.Locale;

/** Someone who can run a command: a player or the server console. */
public interface CommandSender {

    /** Display name of the sender. */
    String name();

    /** The sender's preferred locale (for translated replies). */
    Locale locale();

    /** Sends a message to this sender. */
    void sendMessage(String message);

    /** Sends a translated message (key from the locale files). */
    default void sendMessage(String key, Object... args) {
        sendMessage(key);
    }
}
