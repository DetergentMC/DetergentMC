package io.detergentmc.api;

/** Thrown when a Bubble is invalid, fails to load, or fails at runtime. */
public class BubbleException extends RuntimeException {
    public BubbleException(String message) {
        super(message);
    }

    public BubbleException(String message, Throwable cause) {
        super(message, cause);
    }
}
