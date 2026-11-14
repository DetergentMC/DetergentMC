package io.detergentmc.content;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Registry of custom UIs, keyed by id. */
public final class UIRegistry {

    private final Map<String, CustomUI> uis = new LinkedHashMap<>();

    public void register(CustomUI ui) {
        if (uis.containsKey(ui.id())) {
            throw new IllegalArgumentException("Custom UI already registered: " + ui.id());
        }
        uis.put(ui.id(), ui);
    }

    public Optional<CustomUI> get(String id) {
        return Optional.ofNullable(uis.get(id));
    }

    public Collection<CustomUI> all() {
        return java.util.List.copyOf(uis.values());
    }
}
