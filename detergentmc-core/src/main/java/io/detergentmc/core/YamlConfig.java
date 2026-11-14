package io.detergentmc.core;

import io.detergentmc.api.Config;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * A {@link Config} backed by a YAML map. Keys use dot notation, e.g.
 * {@code "language.default"}. Missing keys return the caller's default.
 */
public final class YamlConfig implements Config {

    private final Map<String, Object> data;

    public YamlConfig(Map<String, Object> data) {
        this.data = data == null ? Map.of() : new LinkedHashMap<>(data);
    }

    public static YamlConfig empty() {
        return new YamlConfig(Map.of());
    }

    /** Loads a YAML file. Returns an empty config when the file is missing. */
    public static YamlConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            return fromStream(in);
        }
    }

    /** Loads YAML from a stream (e.g. a file inside a Bubble jar). */
    @SuppressWarnings("unchecked")
    public static YamlConfig fromStream(InputStream in) {
        Object loaded = new Yaml().load(in);
        if (loaded instanceof Map<?, ?> map) {
            return new YamlConfig((Map<String, Object>) map);
        }
        return empty();
    }

    private Object lookup(String key) {
        String[] parts = key.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @Override
    public String getString(String key, String defaultValue) {
        Object value = lookup(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        Object value = lookup(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = lookup(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return defaultValue;
    }

    @Override
    public List<String> getStringList(String key) {
        Object value = lookup(key);
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(item -> result.add(String.valueOf(item)));
        } else if (value != null) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    @Override
    public boolean has(String key) {
        return lookup(key) != null;
    }
}
