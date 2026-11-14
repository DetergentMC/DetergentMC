package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.adapter.v1_20_4.V1204Adapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Wiring tests for the datapack in {@link DetergentServer}: generating it must place the zip where vanilla will load it from, even before the world exists. */
class DatapackWiringTest {

    @TempDir
    Path tempDir;

    private DetergentServer serverWith(Map<String, Object> configData) throws Exception {
        Files.createDirectories(tempDir.resolve("bubbles"));
        Files.createDirectories(tempDir.resolve("locales"));
        YamlConfig config = new YamlConfig(configData);
        LocaleManager translator = new LocaleManager(Locale.JAPANESE, tempDir.resolve("locales"));
        translator.loadBaseLanguages();
        SimpleEventBus events = new SimpleEventBus();
        SimpleCommandRegistry commands = new SimpleCommandRegistry();
        CoreServer server = new CoreServer(new V1204Adapter());
        BubbleLoader bubbleLoader = new BubbleLoader(tempDir.resolve("bubbles"),
                tempDir.resolve("data"), translator, events, commands, server);
        return new DetergentServer(tempDir, config, translator, events,
                commands, server, bubbleLoader);
    }

    @Test
    void generateDatapackPlacesZipInWorldDatapacks() throws Exception {
        DetergentServer server = serverWith(Map.of());
        Path file = server.generateDatapack();

        assertNotNull(file);
        assertEquals(tempDir.resolve("vanilla").resolve("world").resolve("datapacks")
                .resolve("detergentmc.zip"), file);
        assertTrue(Files.exists(file));
        try (ZipFile zip = new ZipFile(file.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("data/minecraft/tags/functions/tick.json"));
            assertNotNull(zip.getEntry("data/minecraft/tags/functions/load.json"));
            assertNotNull(zip.getEntry("data/detergentmc/functions/tick.mcfunction"));
            assertNotNull(zip.getEntry("data/detergentmc/functions/load.mcfunction"));
        }
    }

    @Test
    void generateDatapackFor1193UsesFormat10() throws Exception {
        // Minecraft Wiki (Template:Data pack format): 10 = 1.19–1.19.3.
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.19.3"));
        Path file = server.generateDatapack();
        assertNotNull(file);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entry = zip.getEntry("pack.mcmeta");
            assertNotNull(entry);
            String mcmeta = new String(
                    zip.getInputStream(entry).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("{\"pack\":{\"pack_format\":10,\"description\":\"DetergentMC\"}}",
                    mcmeta, "datapack format for 1.19.3");
        }
    }

    @Test
    void generateDatapackFor1191UsesFormat10() throws Exception {
        // Minecraft Wiki (Template:Data pack format): 10 = 1.19–1.19.3.
        for (String version : new String[]{"1.19.1", "1.19.2"}) {
            DetergentServer server = serverWith(Map.of("minecraft-version", version));
            Path file = server.generateDatapack();
            assertNotNull(file);
            try (ZipFile zip = new ZipFile(file.toFile())) {
                var entry = zip.getEntry("pack.mcmeta");
                assertNotNull(entry);
                String mcmeta = new String(
                        zip.getInputStream(entry).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                assertEquals("{\"pack\":{\"pack_format\":10,\"description\":\"DetergentMC\"}}",
                        mcmeta, "datapack format for " + version);
            }
        }
    }

    @Test
    void generateDatapackFor1205UsesFormat41() throws Exception {
        // Minecraft Wiki (Template:Data pack format): 41 = 1.20.5–1.20.6.
        for (String version : new String[]{"1.20.5", "1.20.6"}) {
            DetergentServer server = serverWith(Map.of("minecraft-version", version));
            Path file = server.generateDatapack();
            assertNotNull(file);
            try (ZipFile zip = new ZipFile(file.toFile())) {
                var entry = zip.getEntry("pack.mcmeta");
                assertNotNull(entry);
                String mcmeta = new String(
                        zip.getInputStream(entry).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                assertEquals("{\"pack\":{\"pack_format\":41,\"description\":\"DetergentMC\"}}",
                        mcmeta, "datapack format for " + version);
            }
        }
    }
}
