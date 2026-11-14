package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.BubbleException;
import io.detergentmc.api.BubbleInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BubbleInfoTest {

    @Test
    void parsesValidBubbleYml() {
        BubbleInfo info = BubbleInfo.parse(Map.of(
                "id", "hello-bubble",
                "name", "HelloBubble",
                "version", "1.0.0",
                "description", "Says hello.",
                "authors", List.of("DetergentMC"),
                "main", "io.detergentmc.bubble.hello.HelloBubble",
                "depends", List.of()));
        assertEquals("hello-bubble", info.id());
        assertEquals("HelloBubble", info.name());
        assertEquals("1.0.0", info.version());
        assertEquals(List.of("DetergentMC"), info.authors());
        assertTrue(info.depends().isEmpty());
    }

    @Test
    void appliesDefaults() {
        BubbleInfo info = BubbleInfo.parse(Map.of(
                "id", "minimal",
                "main", "example.Minimal"));
        assertEquals("minimal", info.name());
        assertEquals("0.1.0", info.version());
    }

    @Test
    void missingIdIsRejected() {
        assertThrows(BubbleException.class, () -> BubbleInfo.parse(Map.of(
                "main", "example.Minimal")));
    }

    @Test
    void uppercaseIdIsRejected() {
        assertThrows(BubbleException.class, () -> BubbleInfo.parse(Map.of(
                "id", "HelloBubble",
                "main", "example.HelloBubble")));
    }
}
