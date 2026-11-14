package io.detergentmc.api;

/** Fired after the server has finished starting and Bubbles are enabled. */
public record ServerStartEvent(Server server) implements Event {
}
