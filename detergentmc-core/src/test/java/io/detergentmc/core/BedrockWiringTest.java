package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.adapter.v1_20_4.V1204Adapter;
import io.detergentmc.bedrock.BedrockConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link DetergentServer#bedrockConfig(int)} の配線テスト。 */
class BedrockWiringTest {

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
    void bedrockConfigDefaults() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "server", Map.of("motd", "DetergentMC 1.20.4"),
                "max-players", 20,
                "server-name", "DetergentMC"));
        BedrockConfig config = server.bedrockConfig(25566);
        assertTrue(config.enabled(), "既定で Bedrock リスナーは有効");
        assertEquals(19132, config.port());
        assertEquals("DetergentMC 1.20.4", config.motd());
        assertEquals(".", config.floodgatePrefix());
        assertEquals(20, config.maxPlayers());
        assertEquals("DetergentMC", config.serverName());
        assertEquals("127.0.0.1", config.internalHost());
        assertEquals(25566, config.internalPort());
        assertEquals(765, config.serverProtocol());
    }

    @Test
    void bedrockConfigFromYaml() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.2",
                "bedrock", Map.of("enabled", false, "port", 19133, "motd", "Test Bedrock"),
                "floodgate", Map.of("prefix", "_"),
                "max-players", 10));
        BedrockConfig config = server.bedrockConfig(25566);
        assertFalse(config.enabled());
        assertEquals(19133, config.port());
        assertEquals("Test Bedrock", config.motd());
        assertEquals("_", config.floodgatePrefix());
        assertEquals(10, config.maxPlayers());
        assertEquals(764, config.serverProtocol(), "内部 1.20.2 サーバーは 764");
    }

    @Test
    void bedrockMotdFallsBackToJavaMotd() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "server", Map.of("motd", "Java MOTD")));
        assertEquals("Java MOTD", server.bedrockConfig(25566).motd());
    }
}
