package io.detergentmc.api;

/** Fired after a Bubble has been enabled. */
public record BubbleEnableEvent(BubbleInfo bubble) implements Event {
}
