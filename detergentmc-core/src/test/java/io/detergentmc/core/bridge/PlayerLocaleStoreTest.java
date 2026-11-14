package io.detergentmc.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerLocaleStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsWhenNoFileExists() {
        PlayerLocaleStore store = new PlayerLocaleStore(
                tempDir.resolve("player-locales.properties"), Locale.JAPANESE);
        assertEquals(Locale.JAPANESE, store.localeFor("Steve"));
        assertFalse(Files.exists(tempDir.resolve("player-locales.properties")));
    }

    @Test
    void persistsAcrossReloads() {
        Path file = tempDir.resolve("data/player-locales.properties");
        PlayerLocaleStore store = new PlayerLocaleStore(file, Locale.JAPANESE);

        store.setLocale("Steve", Locale.ENGLISH);
        store.setLocale("Alex", Locale.JAPANESE);
        assertTrue(Files.exists(file));

        // A new instance on the same file (as after a restart) reads it back.
        PlayerLocaleStore reloaded = new PlayerLocaleStore(file, Locale.JAPANESE);
        assertEquals(Locale.ENGLISH, reloaded.localeFor("Steve"));
        assertEquals(Locale.JAPANESE, reloaded.localeFor("Alex"));
        assertEquals(Locale.JAPANESE, reloaded.localeFor("Nobody"));
    }

    @Test
    void supportedLanguagesAreOnlyJaAndEn() {
        assertTrue(PlayerLocaleStore.isSupported("ja"));
        assertTrue(PlayerLocaleStore.isSupported("en"));
        assertTrue(PlayerLocaleStore.isSupported("EN"));
        assertFalse(PlayerLocaleStore.isSupported("fr"));
        assertFalse(PlayerLocaleStore.isSupported(null));
        assertEquals(Locale.ENGLISH, PlayerLocaleStore.parseSupported("en"));
        assertEquals(null, PlayerLocaleStore.parseSupported("zh"));
    }
}
