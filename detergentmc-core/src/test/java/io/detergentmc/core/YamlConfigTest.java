package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class YamlConfigTest {

    private static YamlConfig config(String yaml) {
        return YamlConfig.fromStream(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsNestedKeysWithDefaults() {
        YamlConfig config = config("""
                language:
                  default: ja
                max-players: 20
                features:
                  - a
                  - b
                """);

        assertEquals("ja", config.getString("language.default", "en"));
        assertEquals("en", config.getString("language.missing", "en"));
        assertEquals(20, config.getInt("max-players", 10));
        assertEquals(10, config.getInt("missing", 10));
        assertEquals(List.of("a", "b"), config.getStringList("features"));
        assertTrue(config.has("language.default"));
        assertFalse(config.has("nope"));
    }

    @Test
    void readsBooleans() {
        YamlConfig config = config("enabled: true\n");
        assertTrue(config.getBoolean("enabled", false));
        assertFalse(config.getBoolean("missing", false));
    }
}
