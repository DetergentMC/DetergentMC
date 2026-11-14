package io.detergentmc.api;

import java.util.Locale;
import java.util.Set;

/**
 * Multilingual messages.
 *
 * <p>Messages live in locale files ({@code locales/ja.yml},
 * {@code locales/en.yml}, ...). A message key is looked up in the
 * requested locale first, then in the default locale, and if it is missing
 * there too, the key itself is returned - never an exception, never blank.
 *
 * <p>Placeholders use {@link java.text.MessageFormat} style: {@code {0}},
 * {@code {1}}, ...
 */
public interface Translator {

    /** Translates a key for a locale, with the default locale as fallback. */
    String translate(String key, Locale locale, Object... args);

    /** Translates a key for the server's default locale. */
    String translate(String key, Object... args);

    /** The server's default locale (the fallback language). */
    Locale defaultLocale();

    /** Locales that have a language file loaded. */
    Set<Locale> availableLocales();
}
