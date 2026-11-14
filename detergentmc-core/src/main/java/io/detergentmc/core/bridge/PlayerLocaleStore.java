package io.detergentmc.core.bridge;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Persists each player's language choice in
 * {@code <workdir>/data/player-locales.properties} (one
 * {@code <playerName>=<languageTag>} line per player), so the choice
 * survives restarts. Only {@code ja} and {@code en} are supported; anything
 * else falls back to the server default locale.
 */
public final class PlayerLocaleStore {

    private static final Logger LOG = Logger.getLogger(PlayerLocaleStore.class.getName());

    /** Language tags a player may pick with {@code /lang}. */
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of("ja", "en");

    private final Path file;
    private final Locale defaultLocale;
    private final Properties properties = new Properties();

    public PlayerLocaleStore(Path file, Locale defaultLocale) {
        if (file == null) {
            throw new IllegalArgumentException("locale file is required");
        }
        this.file = file;
        this.defaultLocale = defaultLocale == null ? Locale.JAPANESE : defaultLocale;
        load();
    }

    /** The properties file backing this store. */
    public Path file() {
        return file;
    }

    public Locale defaultLocale() {
        return defaultLocale;
    }

    /** Returns {@code true} when {@code language} is a supported {@code /lang} value. */
    public static boolean isSupported(String language) {
        return language != null && SUPPORTED_LANGUAGES.contains(language.toLowerCase(Locale.ROOT));
    }

    /** Parses a {@code /lang} argument into a locale, or {@code null} when unsupported. */
    public static Locale parseSupported(String language) {
        if (!isSupported(language)) {
            return null;
        }
        return Locale.forLanguageTag(language.toLowerCase(Locale.ROOT));
    }

    /** The stored locale for a player, or the server default when none is stored. */
    public synchronized Locale localeFor(String playerName) {
        String tag = properties.getProperty(playerName);
        if (tag == null || tag.isBlank()) {
            return defaultLocale;
        }
        Locale locale = Locale.forLanguageTag(tag.trim());
        return isSupported(locale.getLanguage()) ? locale : defaultLocale;
    }

    /** Stores a player's locale and writes the file immediately. */
    public synchronized void setLocale(String playerName, Locale locale) {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("player name is required");
        }
        if (locale == null) {
            throw new IllegalArgumentException("locale is required");
        }
        properties.setProperty(playerName, locale.getLanguage());
        save();
    }

    /** Reloads the file from disk (used after an external edit). */
    public synchronized void load() {
        properties.clear();
        if (!Files.exists(file)) {
            return;
        }
        try (InputStreamReader in = new InputStreamReader(
                Files.newInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(in);
        } catch (IOException e) {
            LOG.warning("Failed to load player locales from " + file + ": " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStreamWriter out = new OutputStreamWriter(
                    Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                properties.store(out, "DetergentMC player locales (set with /lang)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to save player locales to " + file, e);
        }
    }
}
