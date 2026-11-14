package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranslatorTest {

    @Test
    void translatesJapaneseAndEnglish(@TempDir Path tmp) {
        LocaleManager translator = new LocaleManager(Locale.JAPANESE, tmp);
        translator.loadBaseLanguages();

        assertEquals("こんにちは、Steveさん！ DetergentMCへようこそ。",
                translator.translate("command.hello", Locale.JAPANESE, "Steve"));
        assertEquals("Hello, Steve! Welcome to DetergentMC.",
                translator.translate("command.hello", Locale.ENGLISH, "Steve"));
    }

    @Test
    void missingKeyReturnsTheKeyItself(@TempDir Path tmp) {
        LocaleManager translator = new LocaleManager(Locale.JAPANESE, tmp);
        translator.loadBaseLanguages();

        assertEquals("no.such.key", translator.translate("no.such.key", Locale.JAPANESE));
        assertEquals("no.such.key", translator.translate("no.such.key", Locale.ENGLISH));
    }

    @Test
    void fallsBackToDefaultLocale(@TempDir Path tmp) throws IOException {
        // Only English has this key; the default locale is English, so a
        // Japanese request falls back to the English text.
        Files.writeString(tmp.resolve("en.yml"), "custom:\n  only-en: Only English\n");
        Files.writeString(tmp.resolve("ja.yml"), "custom:\n  dummy: x\n");
        LocaleManager translator = new LocaleManager(Locale.ENGLISH, tmp);
        translator.load(Locale.ENGLISH);
        translator.load(Locale.JAPANESE);

        assertEquals("Only English", translator.translate("custom.only-en", Locale.JAPANESE));
    }

    @Test
    void staleDiskFileStillGetsNewBundledKeys(@TempDir Path tmp) throws IOException {
        // A workdir created by an older version has disk locale files that
        // lack newer keys (vanilla.*, command.lang.*). Bundled keys must
        // still resolve, while a disk override for an old key keeps winning.
        Files.writeString(tmp.resolve("ja.yml"),
                "custom:\n  only-disk: ディスクだけ\ncommand:\n  hello: \"上書き、{0}さん\"\n");
        LocaleManager translator = new LocaleManager(Locale.JAPANESE, tmp);
        translator.load(Locale.JAPANESE);

        assertEquals("ディスクだけ", translator.translate("custom.only-disk", Locale.JAPANESE));
        assertEquals("上書き、Steveさん",
                translator.translate("command.hello", Locale.JAPANESE, "Steve"));
        assertEquals("バニラサーバーが起動しました (ポート 25,565)。",
                translator.translate("vanilla.ready", Locale.JAPANESE, 25565));
        assertEquals("使い方: /lang <ja|en>",
                translator.translate("command.lang.usage", Locale.JAPANESE));
    }

    @Test
    void availableLocalesIncludeJapaneseAndEnglish(@TempDir Path tmp) {
        LocaleManager translator = new LocaleManager(Locale.JAPANESE, tmp);
        translator.loadBaseLanguages();

        assertTrue(translator.availableLocales().contains(Locale.JAPANESE));
        assertTrue(translator.availableLocales().contains(Locale.ENGLISH));
        assertEquals(Locale.JAPANESE, translator.defaultLocale());
    }
}
