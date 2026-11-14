package io.detergentmc.api;

/** Fired when a player leaves the server. */
public record PlayerLeaveEvent(Player player) implements Event {
}
