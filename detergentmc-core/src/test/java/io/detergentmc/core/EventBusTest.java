package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.detergentmc.adapter.v1_20_4.V1204Adapter;
import io.detergentmc.api.Player;
import io.detergentmc.api.PlayerJoinEvent;
import io.detergentmc.api.ServerStartEvent;
import io.detergentmc.api.Subscribe;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventBusTest {

    record TestPlayer(String name, UUID uuid, Locale locale) implements Player {
        @Override
        public void sendMessage(String message) {
        }
    }

    static class Listener {
        int joins;

        @Subscribe
        public void onJoin(PlayerJoinEvent event) {
            joins++;
        }
    }

    @Test
    void callsMatchingHandlersOnly() {
        SimpleEventBus bus = new SimpleEventBus();
        Listener listener = new Listener();
        bus.register(listener);

        bus.fire(new PlayerJoinEvent(new TestPlayer("Steve", UUID.randomUUID(), Locale.JAPANESE)));
        assertEquals(1, listener.joins);

        bus.fire(new ServerStartEvent(new CoreServer(new V1204Adapter())));
        assertEquals(1, listener.joins);

        bus.unregister(listener);
        bus.fire(new PlayerJoinEvent(new TestPlayer("Alex", UUID.randomUUID(), Locale.ENGLISH)));
        assertEquals(1, listener.joins);
    }

    @Test
    void rejectsBadHandlerMethods() {
        SimpleEventBus bus = new SimpleEventBus();
        Object bad = new Object() {
            @Subscribe
            public void broken(String notAnEvent) {
            }
        };
        assertThrows(IllegalArgumentException.class, () -> bus.register(bad));
    }
}
