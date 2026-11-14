package io.detergentmc.api;

/** Fired when a player sends a chat message. The message itself is delivered by vanilla. */
public record PlayerChatEvent(Player player, String message) implements Event {
}
