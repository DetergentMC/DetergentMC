package io.detergentmc.legacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyTextTest {

    @Test
    void plainTextComponent() {
        assertEquals("Hello", LegacyText.jsonToPlain("{\"text\":\"Hello\"}"));
    }

    @Test
    void nestedExtraIsConcatenated() {
        assertEquals("<Steve> hi there", LegacyText.jsonToPlain(
                "{\"text\":\"<Steve> \",\"extra\":[{\"text\":\"hi\"},{\"text\":\" there\"}]}"));
    }

    @Test
    void bareJsonString() {
        assertEquals("just text", LegacyText.jsonToPlain("\"just text\""));
    }

    @Test
    void plainStringPassesThrough() {
        assertEquals("not json at all", LegacyText.jsonToPlain("not json at all"));
    }

    @Test
    void chatStyleComponentWithColor() {
        assertEquals("§aWelcome", LegacyText.jsonToPlain(
                "{\"text\":\"§aWelcome\",\"color\":\"green\"}"));
    }

    @Test
    void translateKeyFallback() {
        assertEquals("multiplayer.disconnect.kicked",
                LegacyText.jsonToPlain("{\"translate\":\"multiplayer.disconnect.kicked\"}"));
    }

    @Test
    void escapesAreDecoded() {
        assertEquals("a\nb\"c", LegacyText.jsonToPlain("{\"text\":\"a\\nb\\\"c\"}"));
    }

    @Test
    void nullAndEmpty() {
        assertEquals("", LegacyText.jsonToPlain(null));
        assertEquals("", LegacyText.jsonToPlain(""));
    }
}
