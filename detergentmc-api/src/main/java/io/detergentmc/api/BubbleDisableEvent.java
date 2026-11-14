package io.detergentmc.api;

/** Fired before a Bubble is disabled. */
public record BubbleDisableEvent(BubbleInfo bubble) implements Event {
}
