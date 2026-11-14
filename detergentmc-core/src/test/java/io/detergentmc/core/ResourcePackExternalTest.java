package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.adapter.v1_20_4.V1204Adapter;
import io.detergentmc.protocol.ResourcePack;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the external resource-pack mode in {@link DetergentServer}:
 * an {@code https://} URL is used as-is with no local HTTP server, an
 * {@code http://} URL is refused and falls back to loopback, a missing
 * or malformed SHA-1 offers no pack, and a required pack that cannot
 * be prepared aborts the startup (throws) instead of silently
 * continuing without one.
 */
class ResourcePackExternalTest {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String EXTERNAL_URL =
            "https://example.invalid/packs/detergentmc-pack.zip";

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

    private Path packZip() {
        return tempDir.resolve("data").resolve("resource-pack")
                .resolve("detergentmc-pack.zip");
    }

    @Test
    void externalModeUsesConfiguredUrlAndSha1WithoutLocalServer() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "required", false,
                        "prompt-key", "my.pack.prompt")));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        assertEquals(VALID_SHA1, pack.sha1());
        assertFalse(pack.required());
        assertEquals("my.pack.prompt", pack.promptKey());
        assertEquals(pack, server.resourcePack());
        // No local HTTP server was started and no pack was built on disk.
        assertNull(server.resourcePackServer());
        assertFalse(Files.exists(packZip()));
    }

    @Test
    void externalModeKeepsRequiredFlag() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "required", true)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertTrue(pack.required());
        assertEquals(io.detergentmc.protocol.ResourcePackServer.DEFAULT_PROMPT_KEY,
                pack.promptKey());
        assertNull(server.resourcePackServer());
    }

    @Test
    void emptyUrlKeepsLoopbackDefault() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "url", "")));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            assertTrue(pack.url().startsWith("http://127.0.0.1:"), pack.url());
            assertNotNull(server.resourcePackServer());
            assertTrue(server.resourcePackServer().isRunning());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void httpUrlIsRefusedAndFallsBackToLoopback() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "port", 0,
                        "url", "http://example.invalid/packs/detergentmc-pack.zip",
                        "sha1", VALID_SHA1)));
        ResourcePack pack = null;
        try {
            pack = server.startResourcePack();
            assertNotNull(pack);
            assertTrue(pack.url().startsWith("http://127.0.0.1:"), pack.url());
            assertNotNull(server.resourcePackServer());
            assertTrue(server.resourcePackServer().isRunning());
        } finally {
            server.stopResourcePack();
        }
    }

    @Test
    void externalInvalidSha1WithoutRequiredContinuesWithoutPack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", "not-a-sha1",
                        "required", false)));
        assertNull(server.startResourcePack());
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
        assertFalse(Files.exists(packZip()));
    }

    @Test
    void externalMissingSha1WithRequiredAbortsStartup() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", "",
                        "required", true)));
        assertThrows(IOException.class, server::startResourcePack);
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void externalInvalidSha1WithRequiredAbortsStartup() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", "abc123",
                        "required", true)));
        assertThrows(IOException.class, server::startResourcePack);
        assertNull(server.resourcePack());
        assertNull(server.resourcePackServer());
    }

    @Test
    void externalModeWithValid763PackOffersItTo763Clients() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "url-763", "https://example.invalid/packs/detergentmc-pack-1.20.1.zip",
                        "sha1-763", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        assertNotNull(server.resourcePack763());
        assertEquals("https://example.invalid/packs/detergentmc-pack-1.20.1.zip",
                server.resourcePack763().url());
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                server.resourcePack763().sha1());
        // External mode still serves nothing locally.
        assertNull(server.resourcePackServer());
        assertFalse(Files.exists(packZip()));
    }

    @Test
    void externalModeWithInvalid763Sha1KeepsMainPackOnly() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "url-763", "https://example.invalid/packs/detergentmc-pack-1.20.1.zip",
                        "sha1-763", "not-a-sha1",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        // The bad 763 settings only warn; the main pack still works.
        assertNull(server.resourcePack763());
        assertNull(server.resourcePackServer());
    }

    @Test
    void externalModeWithValid764PackOffersItTo764Clients() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "url-764", "https://example.invalid/packs/detergentmc-pack-1.20.2.zip",
                        "sha1-764", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        assertNotNull(server.resourcePack764());
        assertEquals("https://example.invalid/packs/detergentmc-pack-1.20.2.zip",
                server.resourcePack764().url());
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                server.resourcePack764().sha1());
        // No 763 pack was configured, so 763 clients get the main pack.
        assertNull(server.resourcePack763());
        // External mode still serves nothing locally.
        assertNull(server.resourcePackServer());
        assertFalse(Files.exists(packZip()));
    }

    @Test
    void externalModeWithValid766PackOffersItTo766Clients() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "url-766", "https://example.invalid/packs/detergentmc-pack-1.20.5.zip",
                        "sha1-766", "cccccccccccccccccccccccccccccccccccccccc",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        assertNotNull(server.resourcePack766());
        assertEquals("https://example.invalid/packs/detergentmc-pack-1.20.5.zip",
                server.resourcePack766().url());
        assertEquals("cccccccccccccccccccccccccccccccccccccccc",
                server.resourcePack766().sha1());
        // One pack serves both 1.20.5 and 1.20.6 (protocol 766).
        // No 763 / 764 pack was configured, so those clients get the main pack.
        assertNull(server.resourcePack763());
        assertNull(server.resourcePack764());
        // External mode still serves nothing locally.
        assertNull(server.resourcePackServer());
        assertFalse(Files.exists(packZip()));
    }

    @Test
    void externalModeWithValid767PackOffersItTo767Clients() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "url-767", "https://example.invalid/packs/detergentmc-pack-1.21.zip",
                        "sha1-767", "dddddddddddddddddddddddddddddddddddddddd",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        assertNotNull(server.resourcePack767());
        assertEquals("https://example.invalid/packs/detergentmc-pack-1.21.zip",
                server.resourcePack767().url());
        assertEquals("dddddddddddddddddddddddddddddddddddddddd",
                server.resourcePack767().sha1());
        // One pack serves both 1.21 and 1.21.1 (protocol 767).
        // No 763 / 764 / 766 pack was configured, so those clients get the main pack.
        assertNull(server.resourcePack763());
        assertNull(server.resourcePack764());
        assertNull(server.resourcePack766());
        // External mode still serves nothing locally.
        assertNull(server.resourcePackServer());
        assertFalse(Files.exists(packZip()));
    }

    @Test
    void externalModeWithInvalid767Sha1KeepsMainPackOnly() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "url-767", "https://example.invalid/packs/detergentmc-pack-1.21.zip",
                        "sha1-767", "not-a-sha1",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        // The bad 767 settings only warn; the main pack still works.
        // Startup is not aborted: a missing 767 pack never fails a
        // required pack on its own (the primary url/sha1 decide that).
        assertNull(server.resourcePack767());
        assertNull(server.resourcePackServer());
    }

    @Test
    void externalModeWithInvalid766Sha1KeepsMainPackOnly() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "url-766", "https://example.invalid/packs/detergentmc-pack-1.20.5.zip",
                        "sha1-766", "not-a-sha1",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        // The bad 766 settings only warn; the main pack still works.
        // Startup is not aborted: a missing 766 pack never fails a
        // required pack on its own (the primary url/sha1 decide that).
        assertNull(server.resourcePack766());
        assertNull(server.resourcePackServer());
    }

    @Test
    void externalModeWithInvalid764Sha1KeepsMainPackOnly() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", EXTERNAL_URL,
                        "sha1", VALID_SHA1,
                        "url-764", "https://example.invalid/packs/detergentmc-pack-1.20.2.zip",
                        "sha1-764", "not-a-sha1",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertEquals(EXTERNAL_URL, pack.url());
        // The bad 764 settings only warn; the main pack still works.
        assertNull(server.resourcePack764());
        assertNull(server.resourcePackServer());
    }

    @Test
    void localPortConflictWithoutRequiredContinuesWithoutPack() throws Exception {
        try (ServerSocket blocker = new ServerSocket(
                0, 50, InetAddress.getByName("127.0.0.1"))) {
            DetergentServer server = serverWith(Map.of(
                    "resource-pack", Map.of(
                            "enabled", true,
                            "port", blocker.getLocalPort(),
                            "required", false)));
            assertNull(server.startResourcePack());
            assertNull(server.resourcePack());
            assertNull(server.resourcePackServer());
        }
    }

    @Test
    void localPortConflictWithRequiredAbortsStartup() throws Exception {
        try (ServerSocket blocker = new ServerSocket(
                0, 50, InetAddress.getByName("127.0.0.1"))) {
            DetergentServer server = serverWith(Map.of(
                    "resource-pack", Map.of(
                            "enabled", true,
                            "port", blocker.getLocalPort(),
                            "required", true)));
            assertThrows(IOException.class, server::startResourcePack);
            assertNull(server.resourcePack());
            assertNull(server.resourcePackServer());
        }
    }
}
