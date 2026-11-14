package io.detergentmc.api;

import java.util.List;

/**
 * A simple hierarchical configuration, backed by a YAML file.
 * Keys use dot notation, e.g. {@code "language.default"}.
 */
public interface Config {

    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    List<String> getStringList(String key);

    boolean has(String key);
}
