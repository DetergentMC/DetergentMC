package io.detergentmc.core;

import io.detergentmc.api.Translator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Multilingual messages for DetergentMC.
 *
 * <p>Language files are YAML files named after the locale, e.g.
 * {@code ja.yml} and {@code en.yml}. They are looked up in this order:
 * <ol>
 *   <li>The language files bundled in the core jar ({@code /locales/}).</li>
 *   <li>The server's {@code locales/} directory (operator overrides,
 *       layered on top of the bundled file).</li>
 * </ol>
 *
 * <p>Fallback chain for a key: requested locale -&gt; default locale -&gt;
 * the key itself. A missing translation never crashes anything.
 */
public final class LocaleManager implements Translator {

    private static final Logger LOG = Logger.getLogger(LocaleManager.class.getName());

    private final Locale defaultLocale;
    private final Path localesDir;
    private final Map<Locale, Map<String, String>> messages = new HashMap<>();

    public LocaleManager(Locale defaultLocale, Path localesDir) {
        this.defaultLocale = defaultLocale;
        this.localesDir = localesDir;
    }

    /** Loads the bundled base languages (ja, en) plus any overrides. */
    public void loadBaseLanguages() {
        load(Locale.JAPANESE);
        load(Locale.ENGLISH);
    }

    /**
     * Loads (or reloads) one locale from disk or from the bundled resources.
     *
     * <p>The bundled language file is loaded first and the operator's file
     * in {@code locales/} is layered on top of it. That way a workdir
     * created by an older version (whose disk files lack newer keys such as
     * {@code vanilla.*} or {@code command.lang.*}) still gets the new
     * messages, while any operator override on disk keeps winning.
     */
    public synchronized void load(Locale locale) {
        String fileName = locale.getLanguage() + ".yml";
        Map<String, String> flat = new LinkedHashMap<>();
        Path diskFile = localesDir.resolve(fileName);
        try {
            try (InputStream in = getClass().getResourceAsStream("/locales/" + fileName)) {
                if (in != null) {
                    flat.putAll(flatten(new Yaml().load(in), ""));
                }
            }
            if (Files.exists(diskFile)) {
                try (InputStream in = Files.newInputStream(diskFile)) {
                    flat.putAll(flatten(new Yaml().load(in), ""));
                }
            } else if (flat.isEmpty()) {
                LOG.warning("No language file found for locale: " + locale);
            }
        } catch (IOException e) {
            LOG.warning("Failed to load language file " + fileName + ": " + e.getMessage());
        }
        messages.put(locale, flat);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> flatten(Object node, String prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                out.putAll(flatten(entry.getValue(), key));
            }
        } else if (node != null) {
            out.put(prefix, String.valueOf(node));
        }
        return out;
    }

    @Override
    public String translate(String key, Locale locale, Object... args) {
        String pattern = lookup(key, locale);
        if (pattern == null) {
            pattern = lookup(key, defaultLocale);
        }
        if (pattern == null) {
            return key;
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, locale).format(args);
    }

    private String lookup(String key, Locale locale) {
        Map<String, String> table = messages.get(locale);
        if (table == null) {
            load(locale);
            table = messages.get(locale);
        }
        return table == null ? null : table.get(key);
    }

    @Override
    public String translate(String key, Object... args) {
        return translate(key, defaultLocale, args);
    }

    @Override
    public Locale defaultLocale() {
        return defaultLocale;
    }

    @Override
    public Set<Locale> availableLocales() {
        return new HashSet<>(messages.keySet());
    }
}
