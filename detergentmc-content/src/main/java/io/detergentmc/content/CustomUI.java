package io.detergentmc.content;

/**
 * A custom UI DetergentMC can show to players.
 *
 * <p>UIs are described in a version-independent way (a title translation
 * key and a slot count). How they are rendered depends on the client:
 * modern clients get the full UI, old clients get the same content
 * through inventory windows they already understand. The rendering
 * itself is TODO (protocol layer + networking milestone).
 */
public record CustomUI(
        /** Unique id, e.g. {@code "detergentmc:shop"}. */
        String id,
        /** Translation key for the window title. */
        String titleKey,
        /** Number of slots (must be a multiple of 9 for chest-style UIs). */
        int slots
) {
    public CustomUI {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CustomUI id is required");
        }
        if (titleKey == null) {
            titleKey = "";
        }
        if (slots <= 0 || slots % 9 != 0) {
            throw new IllegalArgumentException("CustomUI slots must be a positive multiple of 9: " + slots);
        }
    }
}
