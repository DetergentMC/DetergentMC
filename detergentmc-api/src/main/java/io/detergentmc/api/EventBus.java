package io.detergentmc.api;

/**
 * The server event bus. Bubbles register a listener object; every method
 * annotated with {@link Subscribe} that takes a single {@link Event}
 * parameter is called when a matching event is fired.
 */
public interface EventBus {

    /** Registers all {@link Subscribe} methods of the listener object. */
    void register(Object listener);

    /** Unregisters a previously registered listener object. */
    void unregister(Object listener);

    /** Fires an event to all matching handlers, in registration order. */
    void fire(Event event);
}
