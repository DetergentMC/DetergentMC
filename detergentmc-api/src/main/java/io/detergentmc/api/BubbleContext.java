package io.detergentmc.api;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * What a Bubble gets when it is enabled: everything it needs to talk to
 * the server, without touching version-specific internals.
 */
public interface BubbleContext {

    /** The metadata from this Bubble's {@code bubble.yml}. */
    BubbleInfo info();

    /** A logger named after this Bubble. */
    Logger logger();

    /** This Bubble's own configuration (its {@code config.yml}). */
    Config config();

    /** Multilingual messages: translate keys for any supported locale. */
    Translator translator();

    /** The server event bus. */
    EventBus events();

    /** Register commands provided by this Bubble. */
    CommandRegistry commands();

    /** The running server (version-independent view). */
    Server server();

    /** Writable folder for this Bubble's data, e.g. {@code bubbles/<id>/}. */
    Path dataFolder();
}
