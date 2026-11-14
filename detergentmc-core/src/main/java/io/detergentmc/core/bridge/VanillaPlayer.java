package io.detergentmc.core.bridge;

import io.detergentmc.api.Player;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * A connected player, backed by the vanilla server.
 *
 * <p>Messages are delivered through the vanilla console as
 * {@code /tellraw <name> {"text":"..."}}. The JSON is built here with
 * correct escaping, and the text is sent as UTF-8 so Japanese is not
 * garbled. The player name is never broadcast: {@code tellraw} targets
 * only this player.
 */
public final class VanillaPlayer implements Player {

    private final String name;
    private final UUID uuid;
    private final VanillaCommandSink sink;
    private volatile Locale locale;

    public VanillaPlayer(String name, UUID uuid, Locale locale, VanillaCommandSink sink) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("player name is required");
        }
        if (uuid == null) {
            throw new IllegalArgumentException("player uuid is required");
        }
        if (locale == null) {
            throw new IllegalArgumentException("player locale is required");
        }
        if (sink == null) {
            throw new IllegalArgumentException("command sink is required");
        }
        this.name = name;
        this.uuid = uuid;
        this.locale = locale;
        this.sink = sink;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public Locale locale() {
        return locale;
    }

    /** Changes this player's preferred locale (for example via {@code /lang}). */
    public void setLocale(Locale locale) {
        if (locale == null) {
            throw new IllegalArgumentException("locale is required");
        }
        this.locale = locale;
    }

    @Override
    public void sendMessage(String message) {
        if (message == null) {
            return;
        }
        sink.sendCommand(tellrawCommand(name, message));
    }

    /**
     * Builds the vanilla console line that sends {@code message} to
     * {@code playerName} only: {@code tellraw <name> {"text":"..."}}.
     */
    public static String tellrawCommand(String playerName, String message) {
        return "tellraw " + playerName + " {\"text\":\"" + escapeJson(message) + "\"}";
    }

    /**
     * Escapes a string for use inside a JSON string literal. Non-ASCII
     * characters (Japanese included) are left as-is so they travel as
     * UTF-8; only {@code "}, {@code \} and control characters are escaped.
     */
    public static String escapeJson(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * Resolves a player's UUID from the vanilla log. When the log line
     * carried a real (Mojang) UUID it is used as-is; when it is missing or
     * unparsable (for example in offline mode) a deterministic UUID is
     * derived from the name, the same way vanilla does for offline players:
     * {@code UUID.nameUUIDFromBytes("OfflinePlayer:" + name)}.
     */
    public static UUID resolveUuid(String name, String uuidText) {
        if (uuidText != null && !uuidText.isBlank()) {
            try {
                return UUID.fromString(uuidText.trim());
            } catch (IllegalArgumentException ignored) {
                // fall through to the deterministic offline UUID
            }
        }
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
