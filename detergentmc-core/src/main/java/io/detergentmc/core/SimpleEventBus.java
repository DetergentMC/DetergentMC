package io.detergentmc.core;

import io.detergentmc.api.Event;
import io.detergentmc.api.EventBus;
import io.detergentmc.api.Subscribe;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reflection-based {@link EventBus} implementation. */
public final class SimpleEventBus implements EventBus {

    private static final Logger LOG = Logger.getLogger(SimpleEventBus.class.getName());

    private record Handler(Object listener, Method method, Class<? extends Event> type) {
    }

    private final List<Handler> handlers = new CopyOnWriteArrayList<>();

    @Override
    public void register(Object listener) {
        for (Method method : listener.getClass().getMethods()) {
            if (method.getAnnotation(Subscribe.class) == null) {
                continue;
            }
            if (method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException(
                        "@Subscribe method must take exactly one Event parameter: " + method);
            }
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Class<? extends Event> type = (Class<? extends Event>) method.getParameterTypes()[0];
            handlers.add(new Handler(listener, method, type));
        }
    }

    @Override
    public void unregister(Object listener) {
        handlers.removeIf(handler -> handler.listener() == listener);
    }

    @Override
    public void fire(Event event) {
        for (Handler handler : new ArrayList<>(handlers)) {
            if (!handler.type().isInstance(event)) {
                continue;
            }
            try {
                handler.method().invoke(handler.listener(), event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOG.log(Level.WARNING, "Event handler failed: " + handler.method(), e);
            }
        }
    }
}
