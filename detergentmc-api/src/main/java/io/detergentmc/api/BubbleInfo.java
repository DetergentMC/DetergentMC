package io.detergentmc.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Metadata of one Bubble, parsed from its {@code bubble.yml}.
 *
 * <pre>
 * id: hello-bubble
 * name: HelloBubble
 * version: 1.0.0
 * description: Says hello in the player's language.
 * authors: [DetergentMC]
 * main: io.detergentmc.bubble.hello.HelloBubble
 * depends: []
 * </pre>
 */
public record BubbleInfo(
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        String mainClass,
        List<String> depends
) {
    public BubbleInfo {
        if (id == null || id.isBlank()) {
            throw new BubbleException("bubble.yml: 'id' is required");
        }
        if (!id.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new BubbleException("bubble.yml: 'id' must be lowercase letters, digits, '.', '_' or '-': " + id);
        }
        if (mainClass == null || mainClass.isBlank()) {
            throw new BubbleException("bubble.yml: 'main' is required");
        }
        authors = authors == null ? List.of() : List.copyOf(authors);
        depends = depends == null ? List.of() : List.copyOf(depends);
        if (name == null) {
            name = id;
        }
        if (version == null) {
            version = "0.1.0";
        }
        if (description == null) {
            description = "";
        }
    }

    /** Parses a {@code bubble.yml} already loaded into a map. */
    @SuppressWarnings("unchecked")
    public static BubbleInfo parse(Map<String, Object> yaml) {
        if (yaml == null) {
            throw new BubbleException("bubble.yml is empty or unreadable");
        }
        List<String> authors = new ArrayList<>();
        Object a = yaml.get("authors");
        if (a instanceof List<?> list) {
            list.forEach(item -> authors.add(String.valueOf(item)));
        } else if (a != null) {
            authors.add(String.valueOf(a));
        }
        List<String> depends = new ArrayList<>();
        Object d = yaml.get("depends");
        if (d instanceof List<?> list) {
            list.forEach(item -> depends.add(String.valueOf(item)));
        } else if (d != null) {
            depends.add(String.valueOf(d));
        }
        return new BubbleInfo(
                str(yaml, "id"),
                str(yaml, "name"),
                str(yaml, "version"),
                str(yaml, "description"),
                authors,
                str(yaml, "main"),
                depends
        );
    }

    private static String str(Map<String, Object> yaml, String key) {
        Object value = yaml.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public List<String> authors() {
        return Collections.unmodifiableList(authors);
    }

    public List<String> depends() {
        return Collections.unmodifiableList(depends);
    }
}
