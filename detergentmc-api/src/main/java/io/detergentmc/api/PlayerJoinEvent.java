package io.detergentmc.api;

/** Fired when a player joins the server. */
public record PlayerJoinEvent(Player player) implements Event {
}
