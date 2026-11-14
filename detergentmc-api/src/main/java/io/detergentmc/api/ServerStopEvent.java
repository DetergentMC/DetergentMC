package io.detergentmc.api;

/** Fired when the server begins to stop, before Bubbles are disabled. */
public record ServerStopEvent(Server server) implements Event {
}
