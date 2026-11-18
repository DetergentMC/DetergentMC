package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.adapter.v1_20_4.V1204Adapter;
import io.detergentmc.protocol.ResourcePack;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Wiring tests for the resource pack in {@link DetergentServer}: the
 * disabled case must hand {@code null} to the vanilla host (i.e. no pack
 * is built or served), and the enabled case must build, serve and return
 * a pack whose URL answers over loopback.
 */
class ResourcePackWiringTest {

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
    void disabledConfigReturnsNullAndServesNothing() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", false, "port", 0)));
        assertNull(server.startResourcePack());
        assertNull(server.resourcePack());
        assertNull(server.resourcePack763());
        assertNull(server.resourcePack764());
        assertNull(server.resourcePack766());
        assertNull(server.resourcePack767());
        assertNull(server.resourcePackServer());
        // No pack was built on disk either.
        assertFalse(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                .resolve("detergentmc-pack.zip")));
    }

    @Test
    void enabledConfigBuildsAndServesPack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            assertEquals(server.resourcePack(), pack);
            assertNotNull(server.resourcePackServer());
            assertTrue(server.resourcePackServer().isRunning());
            assertTrue(pack.url().startsWith("http://127.0.0.1:"), pack.url());
            assertFalse(pack.required());
            assertEquals("resource-pack.prompt", pack.promptKey());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServesBothPacks() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.20.1 variant is built, served and offered alongside.
            ResourcePack pack763 = server.resourcePack763();
            assertNotNull(pack763);
            assertNotEquals(pack.sha1(), pack763.sha1());
            assertNotEquals(pack.url(), pack763.url());
            assertTrue(pack763.url().endsWith("/detergentmc-pack-1.20.1.zip"), pack763.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.20.1.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack763.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack763.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack762());
        assertNull(server.resourcePack763());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServes762Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.19.4 variant is built, served and offered alongside.
            ResourcePack pack762 = server.resourcePack762();
            assertNotNull(pack762);
            assertNotEquals(pack.sha1(), pack762.sha1());
            assertNotEquals(pack.url(), pack762.url());
            assertTrue(pack762.url().endsWith("/detergentmc-pack-1.19.4.zip"), pack762.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.19.4.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack762.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack762.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack762());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServes761Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.19.3 variant is built, served and offered alongside.
            ResourcePack pack761 = server.resourcePack761();
            assertNotNull(pack761);
            assertNotEquals(pack.sha1(), pack761.sha1());
            assertNotEquals(pack.url(), pack761.url());
            assertTrue(pack761.url().endsWith("/detergentmc-pack-1.19.3.zip"), pack761.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.19.3.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack761.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack761.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack761());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServes760Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.19.1 variant is built, served and offered alongside.
            ResourcePack pack760 = server.resourcePack760();
            assertNotNull(pack760);
            assertNotEquals(pack.sha1(), pack760.sha1());
            assertNotEquals(pack.url(), pack760.url());
            assertTrue(pack760.url().endsWith("/detergentmc-pack-1.19.1.zip"), pack760.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.19.1.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack760.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack760.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack760());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServesAllThreePacks() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.20.2 variant (pack_format 18) is built, served and
            // offered alongside the 1.20.4 and 1.20.1 packs, on the same
            // loopback port.
            ResourcePack pack764 = server.resourcePack764();
            assertNotNull(pack764);
            assertNotEquals(pack.sha1(), pack764.sha1());
            assertNotEquals(server.resourcePack763().sha1(), pack764.sha1());
            assertNotEquals(pack.url(), pack764.url());
            assertTrue(pack764.url().endsWith("/detergentmc-pack-1.20.2.zip"), pack764.url());
            assertEquals(URI.create(pack.url()).getPort(),
                    URI.create(pack764.url()).getPort());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.20.2.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack764.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack764.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack763());
        assertNull(server.resourcePack764());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServesAllFourPacks() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.20.5 / 1.20.6 variant (pack_format 32, one pack for
            // both versions) is built, served and offered alongside the
            // 1.20.4, 1.20.1 and 1.20.2 packs, on the same loopback port.
            ResourcePack pack766 = server.resourcePack766();
            assertNotNull(pack766);
            assertNotEquals(pack.sha1(), pack766.sha1());
            assertNotEquals(server.resourcePack763().sha1(), pack766.sha1());
            assertNotEquals(server.resourcePack764().sha1(), pack766.sha1());
            assertNotEquals(pack.url(), pack766.url());
            assertTrue(pack766.url().endsWith("/detergentmc-pack-1.20.5.zip"), pack766.url());
            assertEquals(URI.create(pack.url()).getPort(),
                    URI.create(pack766.url()).getPort());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.20.5.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack766.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack766.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack763());
        assertNull(server.resourcePack764());
        assertNull(server.resourcePack766());
        assertNull(server.resourcePack767());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServesAllFivePacks() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.21 / 1.21.1 variant (pack_format 34, one pack for
            // both versions) is built, served and offered alongside the
            // 1.20.4, 1.20.1, 1.20.2 and 1.20.5 / 1.20.6 packs, on the
            // same loopback port.
            ResourcePack pack767 = server.resourcePack767();
            assertNotNull(pack767);
            assertNotEquals(pack.sha1(), pack767.sha1());
            assertNotEquals(server.resourcePack763().sha1(), pack767.sha1());
            assertNotEquals(server.resourcePack764().sha1(), pack767.sha1());
            assertNotEquals(server.resourcePack766().sha1(), pack767.sha1());
            assertNotEquals(pack.url(), pack767.url());
            assertTrue(pack767.url().endsWith("/detergentmc-pack-1.21.zip"), pack767.url());
            assertEquals(URI.create(pack.url()).getPort(),
                    URI.create(pack767.url()).getPort());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.21.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack767.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack767.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack763());
        assertNull(server.resourcePack764());
        assertNull(server.resourcePack766());
        assertNull(server.resourcePack767());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServes757Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.18 / 1.18.1 variant is built, served and offered alongside.
            ResourcePack pack757 = server.resourcePackServer().pack757();
            assertNotNull(pack757);
            assertNotEquals(pack.sha1(), pack757.sha1());
            assertNotEquals(pack.url(), pack757.url());
            assertTrue(pack757.url().endsWith("/detergentmc-pack-1.18.zip"), pack757.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.18.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack757.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack757.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs757PackFor118Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 757 (pack_format 8) for a
        // 1.18 / 1.18.1 server, 765 (pack_format 22) otherwise.
        for (String version : new String[]{"1.18", "1.18.1"}) {
            DetergentServer server = serverWith(Map.of(
                    "minecraft-version", version,
                    "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
            try {
                assertNotNull(server.startResourcePack());
                assertNotNull(server.resourcePackServer().pack757());
                assertEquals(server.resourcePackServer().pack757(), server.hostResourcePack());
            } finally {
                server.stopResourcePack();
            }
        }
    }

    @Test
    void enabledConfigBuildsAndServes756Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.17.1 variant is built, served and offered alongside.
            ResourcePack pack756 = server.resourcePackServer().pack756();
            assertNotNull(pack756);
            assertNotEquals(pack.sha1(), pack756.sha1());
            assertNotEquals(pack.url(), pack756.url());
            assertTrue(pack756.url().endsWith("/detergentmc-pack-1.17.1.zip"), pack756.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.17.1.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack756.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack756.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs756PackFor1171Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 756 (pack_format 7) for a
        // 1.17.1 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.17.1",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePackServer().pack756());
            assertEquals(server.resourcePackServer().pack756(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void enabledConfigBuildsAndServes754Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.16.4 / 1.16.5 variant is built, served and offered alongside.
            ResourcePack pack754 = server.resourcePackServer().pack754();
            assertNotNull(pack754);
            assertNotEquals(pack.sha1(), pack754.sha1());
            assertNotEquals(pack.url(), pack754.url());
            assertTrue(pack754.url().endsWith("/detergentmc-pack-1.16.5.zip"), pack754.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.16.5.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack754.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack754.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs754PackFor1165Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 754 (pack_format 6) for a
        // 1.16.4 / 1.16.5 server, 765 (pack_format 22) otherwise.
        for (String version : new String[]{"1.16.4", "1.16.5"}) {
            DetergentServer server = serverWith(Map.of(
                    "minecraft-version", version,
                    "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
            try {
                assertNotNull(server.startResourcePack());
                assertNotNull(server.resourcePackServer().pack754());
                assertEquals(server.resourcePackServer().pack754(), server.hostResourcePack());
            } finally {
                server.stopResourcePack();
            }
        }
    }

    @Test
    void enabledConfigBuildsAndServes736Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.16.1 variant is built, served and offered alongside.
            ResourcePack pack736 = server.resourcePackServer().pack736();
            assertNotNull(pack736);
            assertNotEquals(pack.sha1(), pack736.sha1());
            assertNotEquals(pack.url(), pack736.url());
            assertTrue(pack736.url().endsWith("/detergentmc-pack-1.16.1.zip"), pack736.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.16.1.zip")));
            // The 736 and 751 packs differ (pack_format 5 vs 6).
            assertNotEquals(server.resourcePackServer().pack751().sha1(), pack736.sha1());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack736.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack736.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs736PackFor1161Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 736 (pack_format 5) for a
        // 1.16.1 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.16.1",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePackServer().pack736());
            assertEquals(server.resourcePackServer().pack736(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void enabledConfigBuildsAndServes735Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.16 variant is built, served and offered alongside.
            ResourcePack pack735 = server.resourcePackServer().pack735();
            assertNotNull(pack735);
            assertNotEquals(pack.sha1(), pack735.sha1());
            assertNotEquals(pack.url(), pack735.url());
            assertTrue(pack735.url().endsWith("/detergentmc-pack-1.16.zip"), pack735.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.16.zip")));
            // The 735 and 736 packs differ (same pack_format 5, different
            // description).
            assertNotEquals(server.resourcePackServer().pack736().sha1(), pack735.sha1());
            // The 735 and 751 packs differ (pack_format 5 vs 6).
            assertNotEquals(server.resourcePackServer().pack751().sha1(), pack735.sha1());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack735.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack735.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs735PackFor116Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 735 (pack_format 5) for a
        // 1.16 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.16",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePackServer().pack735());
            assertEquals(server.resourcePackServer().pack735(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void enabledConfigBuildsAndServes751Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.16.2 variant is built, served and offered alongside.
            ResourcePack pack751 = server.resourcePackServer().pack751();
            assertNotNull(pack751);
            assertNotEquals(pack.sha1(), pack751.sha1());
            assertNotEquals(pack.url(), pack751.url());
            assertTrue(pack751.url().endsWith("/detergentmc-pack-1.16.2.zip"), pack751.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.16.2.zip")));
            // The 751 and 753 packs differ (the 751 description names
            // 1.16.2), even though both use pack_format 6.
            assertNotEquals(server.resourcePackServer().pack753().sha1(), pack751.sha1());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack751.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack751.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs751PackFor1162Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 751 (pack_format 6) for a
        // 1.16.2 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.16.2",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePackServer().pack751());
            assertEquals(server.resourcePackServer().pack751(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void enabledConfigBuildsAndServes753Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.16.3 variant is built, served and offered alongside.
            ResourcePack pack753 = server.resourcePackServer().pack753();
            assertNotNull(pack753);
            assertNotEquals(pack.sha1(), pack753.sha1());
            assertNotEquals(pack.url(), pack753.url());
            assertTrue(pack753.url().endsWith("/detergentmc-pack-1.16.3.zip"), pack753.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.16.3.zip")));
            // The 753 and 754 packs differ (the 753 description names
            // 1.16.3), even though both use pack_format 6.
            assertNotEquals(server.resourcePackServer().pack754().sha1(), pack753.sha1());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack753.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack753.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs753PackFor1163Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 753 (pack_format 6) for a
        // 1.16.3 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.16.3",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePackServer().pack753());
            assertEquals(server.resourcePackServer().pack753(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void enabledConfigBuildsAndServes755Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.17 variant is built, served and offered alongside.
            ResourcePack pack755 = server.resourcePackServer().pack755();
            assertNotNull(pack755);
            assertNotEquals(pack.sha1(), pack755.sha1());
            assertNotEquals(pack.url(), pack755.url());
            assertTrue(pack755.url().endsWith("/detergentmc-pack-1.17.zip"), pack755.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.17.zip")));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack755.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack755.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs755PackFor117Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 755 (pack_format 7) for a
        // 1.17 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.17",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePackServer().pack755());
            assertEquals(server.resourcePackServer().pack755(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void hostResourcePackIs767PackFor121And1211Servers() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 767 (pack_format 34) for a
        // 1.21 / 1.21.1 server, 765 (pack_format 22) otherwise.
        for (String version : new String[]{"1.21", "1.21.1"}) {
            DetergentServer server = serverWith(Map.of(
                    "minecraft-version", version,
                    "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
            try {
                assertNotNull(server.startResourcePack());
                assertNotNull(server.resourcePack767());
                assertEquals(server.resourcePack767(), server.hostResourcePack());
            } finally {
                server.stopResourcePack();
            }
        }
    }

    @Test
    void hostResourcePackIs766PackFor1205And1206Servers() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 766 (pack_format 32) for a
        // 1.20.5 / 1.20.6 server, 765 (pack_format 22) otherwise.
        for (String version : new String[]{"1.20.5", "1.20.6"}) {
            DetergentServer server = serverWith(Map.of(
                    "minecraft-version", version,
                    "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
            try {
                assertNotNull(server.startResourcePack());
                assertNotNull(server.resourcePack766());
                assertEquals(server.resourcePack766(), server.hostResourcePack());
            } finally {
                server.stopResourcePack();
            }
        }
    }

    @Test
    void enabledConfigBuildsAndServesAllSevenPacks() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.21.2 / 1.21.3 variant (pack_format 42, one pack for
            // both versions) and the 1.21.4 variant (pack_format 46) are
            // built, served and offered alongside the 1.20.4, 1.20.1,
            // 1.20.2, 1.20.5 / 1.20.6 and 1.21 / 1.21.1 packs, on the
            // same loopback port.
            ResourcePack pack768 = server.resourcePack768();
            assertNotNull(pack768);
            assertNotEquals(pack.sha1(), pack768.sha1());
            assertNotEquals(server.resourcePack763().sha1(), pack768.sha1());
            assertNotEquals(server.resourcePack764().sha1(), pack768.sha1());
            assertNotEquals(server.resourcePack766().sha1(), pack768.sha1());
            assertNotEquals(server.resourcePack767().sha1(), pack768.sha1());
            assertNotEquals(pack.url(), pack768.url());
            assertTrue(pack768.url().endsWith("/detergentmc-pack-1.21.2.zip"), pack768.url());
            assertEquals(URI.create(pack.url()).getPort(),
                    URI.create(pack768.url()).getPort());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.21.2.zip")));

            HttpURLConnection connection768 = (HttpURLConnection)
                    URI.create(pack768.url()).toURL().openConnection();
            try {
                assertEquals(200, connection768.getResponseCode());
                byte[] body = connection768.getInputStream().readAllBytes();
                assertEquals(pack768.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection768.disconnect();
            }

            ResourcePack pack769 = server.resourcePack769();
            assertNotNull(pack769);
            assertNotEquals(pack.sha1(), pack769.sha1());
            assertNotEquals(server.resourcePack763().sha1(), pack769.sha1());
            assertNotEquals(server.resourcePack764().sha1(), pack769.sha1());
            assertNotEquals(server.resourcePack766().sha1(), pack769.sha1());
            assertNotEquals(server.resourcePack767().sha1(), pack769.sha1());
            assertNotEquals(pack768.sha1(), pack769.sha1());
            assertNotEquals(pack.url(), pack769.url());
            assertTrue(pack769.url().endsWith("/detergentmc-pack-1.21.4.zip"), pack769.url());
            assertEquals(URI.create(pack.url()).getPort(),
                    URI.create(pack769.url()).getPort());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.21.4.zip")));

            HttpURLConnection connection769 = (HttpURLConnection)
                    URI.create(pack769.url()).toURL().openConnection();
            try {
                assertEquals(200, connection769.getResponseCode());
                byte[] body = connection769.getInputStream().readAllBytes();
                assertEquals(pack769.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection769.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack763());
        assertNull(server.resourcePack764());
        assertNull(server.resourcePack766());
        assertNull(server.resourcePack767());
        assertNull(server.resourcePack768());
        assertNull(server.resourcePack769());
        assertNull(server.resourcePackServer());
    }

    @Test
    void enabledConfigBuildsAndServesAllTenPacks() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            // The 1.21.5 variant (pack_format 55), the 1.21.6 variant
            // (pack_format 63) and the 1.21.7 / 1.21.8 variant
            // (pack_format 64) are built, served and offered alongside
            // the 1.20.4, 1.20.1, 1.20.2, 1.20.5 / 1.20.6, 1.21 / 1.21.1,
            // 1.21.2 / 1.21.3 and 1.21.4 packs, on the same loopback port.
            ResourcePack pack770 = server.resourcePack770();
            assertNotNull(pack770);
            assertNotEquals(pack.sha1(), pack770.sha1());
            assertNotEquals(server.resourcePack769().sha1(), pack770.sha1());
            assertNotEquals(pack.url(), pack770.url());
            assertTrue(pack770.url().endsWith("/detergentmc-pack-1.21.5.zip"), pack770.url());
            assertEquals(URI.create(pack.url()).getPort(),
                    URI.create(pack770.url()).getPort());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.21.5.zip")));

            HttpURLConnection connection770 = (HttpURLConnection)
                    URI.create(pack770.url()).toURL().openConnection();
            try {
                assertEquals(200, connection770.getResponseCode());
                byte[] body = connection770.getInputStream().readAllBytes();
                assertEquals(pack770.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection770.disconnect();
            }

            ResourcePack pack771 = server.resourcePack771();
            assertNotNull(pack771);
            assertNotEquals(pack.sha1(), pack771.sha1());
            assertNotEquals(server.resourcePack769().sha1(), pack771.sha1());
            assertNotEquals(pack770.sha1(), pack771.sha1());
            assertNotEquals(pack.url(), pack771.url());
            assertTrue(pack771.url().endsWith("/detergentmc-pack-1.21.6.zip"), pack771.url());
            assertEquals(URI.create(pack.url()).getPort(),
                    URI.create(pack771.url()).getPort());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.21.6.zip")));

            HttpURLConnection connection771 = (HttpURLConnection)
                    URI.create(pack771.url()).toURL().openConnection();
            try {
                assertEquals(200, connection771.getResponseCode());
                byte[] body = connection771.getInputStream().readAllBytes();
                assertEquals(pack771.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection771.disconnect();
            }

            ResourcePack pack772 = server.resourcePack772();
            assertNotNull(pack772);
            assertNotEquals(pack.sha1(), pack772.sha1());
            assertNotEquals(server.resourcePack769().sha1(), pack772.sha1());
            assertNotEquals(pack770.sha1(), pack772.sha1());
            assertNotEquals(pack771.sha1(), pack772.sha1());
            assertNotEquals(pack.url(), pack772.url());
            assertTrue(pack772.url().endsWith("/detergentmc-pack-1.21.7.zip"), pack772.url());
            assertEquals(URI.create(pack.url()).getPort(),
                    URI.create(pack772.url()).getPort());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.21.7.zip")));

            HttpURLConnection connection772 = (HttpURLConnection)
                    URI.create(pack772.url()).toURL().openConnection();
            try {
                assertEquals(200, connection772.getResponseCode());
                byte[] body = connection772.getInputStream().readAllBytes();
                assertEquals(pack772.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection772.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack());
        assertNull(server.resourcePack763());
        assertNull(server.resourcePack764());
        assertNull(server.resourcePack766());
        assertNull(server.resourcePack767());
        assertNull(server.resourcePack768());
        assertNull(server.resourcePack769());
        assertNull(server.resourcePack770());
        assertNull(server.resourcePack771());
        assertNull(server.resourcePack772());
        assertNull(server.resourcePackServer());
    }

    @Test
    void hostResourcePackIs771PackFor1216Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 771 (pack_format 63) for a
        // 1.21.6 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.21.6",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePack771());
            assertEquals(server.resourcePack771(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void hostResourcePackIs772PackFor1217Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 772 (pack_format 64) for a
        // 1.21.7 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.21.7",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePack772());
            assertEquals(server.resourcePack772(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void hostResourcePackIs772PackFor1218Server() throws Exception {
        // 1.21.8 も内部772のため、1.21.7 と同じ 772 パック
        // （pack_format 64）を提供する。
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.21.8",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePack772());
            assertEquals(server.resourcePack772(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void hostResourcePackIs769PackFor1214Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 769 (pack_format 46) for a
        // 1.21.4 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.21.4",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePack769());
            assertEquals(server.resourcePack769(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void hostResourcePackIs761PackFor1193Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 761 (pack_format 12) for a
        // 1.19.3 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.19.3",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePack761());
            assertEquals(server.resourcePack761(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void hostResourcePackIs760PackFor1191Server() throws Exception {
        // The internal vanilla server offers the pack matching its own
        // version in server.properties: 760 (pack_format 9) for a
        // 1.19.1 server, 765 (pack_format 22) otherwise.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.19.1",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePack760());
            assertEquals(server.resourcePack760(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void hostResourcePackIs765PackFor1204Server() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            assertNotNull(server.resourcePack766());
            assertEquals(server.resourcePack(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }
}
