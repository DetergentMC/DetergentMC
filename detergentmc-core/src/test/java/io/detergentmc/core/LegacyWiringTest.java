package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.detergentmc.adapter.v1_20_4.V1204Adapter;
import io.detergentmc.api.VersionAdapter;
import io.detergentmc.legacy.LegacyFrontendConfig;
import io.detergentmc.legacy.LegacyProtocol;
import io.detergentmc.legacy.V1_0_0Adapter;
import io.detergentmc.legacy.V1_1Adapter;
import io.detergentmc.legacy.V1_2_1Adapter;
import io.detergentmc.legacy.V1_2_2Adapter;
import io.detergentmc.legacy.V1_2_3Adapter;
import io.detergentmc.legacy.V1_2_4Adapter;
import io.detergentmc.legacy.V1_6_1Adapter;
import io.detergentmc.legacy.V1_2_4Adapter;
import io.detergentmc.legacy.V1_2_5Adapter;
import io.detergentmc.legacy.V1_3_1Adapter;
import io.detergentmc.legacy.V1_3_2Adapter;
import io.detergentmc.legacy.V1_4_2Adapter;
import io.detergentmc.legacy.V1_4_4Adapter;
import io.detergentmc.legacy.V1_4_5Adapter;
import io.detergentmc.legacy.V1_4_6Adapter;
import io.detergentmc.legacy.V1_4_7Adapter;
import io.detergentmc.legacy.V1_5_1Adapter;
import io.detergentmc.legacy.V1_5_2Adapter;
import io.detergentmc.legacy.V1_5Adapter;
import io.detergentmc.legacy.V1_6_2Adapter;
import io.detergentmc.legacy.V1_6_4Adapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link DetergentServer#legacyConfig(int, int)} の配線テスト。 */
class LegacyWiringTest {

    @TempDir
    Path tempDir;

    private DetergentServer serverWith(Map<String, Object> configData,
                                       VersionAdapter adapter) throws Exception {
        Files.createDirectories(tempDir.resolve("bubbles"));
        Files.createDirectories(tempDir.resolve("locales"));
        YamlConfig config = new YamlConfig(configData);
        LocaleManager translator = new LocaleManager(Locale.JAPANESE, tempDir.resolve("locales"));
        translator.loadBaseLanguages();
        SimpleEventBus events = new SimpleEventBus();
        SimpleCommandRegistry commands = new SimpleCommandRegistry();
        CoreServer server = new CoreServer(adapter);
        BubbleLoader bubbleLoader = new BubbleLoader(tempDir.resolve("bubbles"),
                tempDir.resolve("data"), translator, events, commands, server);
        return new DetergentServer(tempDir, config, translator, events,
                commands, server, bubbleLoader);
    }

    @Test
    void modernBackendUsesBridgeMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "server", Map.of("motd", "DetergentMC 1.20.4"),
                "max-players", 20), new V1204Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.BRIDGE, config.mode());
        assertEquals(LegacyFrontendConfig.DEFAULT_PORT, config.port());
        assertEquals(25565, config.corePort());
        assertEquals("DetergentMC 1.20.4", config.motd());
        assertEquals(20, config.maxPlayers());
        assertNull(server.legacyServer());
    }

    @Test
    void legacyBackendUsesDirectMode() throws Exception {
        // Constructing a DetergentServer for 1.6.4 must not throw: the
        // legacy backend has no Netty protocol number.
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.6.4",
                "server", Map.of("motd", "DetergentMC 1.6.4"),
                "max-players", 10,
                "legacy", Map.of("port", 26000)), new V1_6_4Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26000, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("DetergentMC 1.6.4", config.motd());
        assertEquals(10, config.maxPlayers());
    }

    @Test
    void legacy162BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.6.2",
                "server", Map.of("motd", "DetergentMC 1.6.2"),
                "max-players", 10,
                "legacy", Map.of("port", 26001)), new V1_6_2Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26001, config.port());
        assertEquals(25566, config.backendPort());
        // The backend version travels into the config: it selects the
        // legacy protocol spoken to the backend (74) and the version
        // advertised in ping responses.
        assertEquals("1.6.2", config.versionName());
        assertEquals(LegacyProtocol.V1_6_2, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds162() {
        VersionAdapter adapter = DetergentServer.selectAdapter("1.6.2");
        assertEquals("1.6.2", adapter.minecraftVersion());
    }

    @Test
    void legacy161BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.6.1",
                "server", Map.of("motd", "DetergentMC 1.6.1"),
                "max-players", 10,
                "legacy", Map.of("port", 26002)), new V1_6_1Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26002, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.6.1", config.versionName());
        assertEquals(LegacyProtocol.V1_6_1, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds161() {
        VersionAdapter adapter = DetergentServer.selectAdapter("1.6.1");
        assertEquals("1.6.1", adapter.minecraftVersion());
    }

    @Test
    void legacy152BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.5.2",
                "server", Map.of("motd", "DetergentMC 1.5.2"),
                "max-players", 10,
                "legacy", Map.of("port", 26003)), new V1_5_2Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26003, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.5.2", config.versionName());
        assertEquals(LegacyProtocol.V1_5_2, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds152() {
        VersionAdapter adapter = DetergentServer.selectAdapter("1.5.2");
        assertEquals("1.5.2", adapter.minecraftVersion());
    }

    @Test
    void legacy151BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.5.1",
                "server", Map.of("motd", "DetergentMC 1.5.1"),
                "max-players", 10,
                "legacy", Map.of("port", 26004)), new V1_5_1Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26004, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.5.1", config.versionName());
        assertEquals(LegacyProtocol.V1_5_1, config.legacyProtocol());
    }

    @Test
    void legacy15BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.5",
                "server", Map.of("motd", "DetergentMC 1.5"),
                "max-players", 10,
                "legacy", Map.of("port", 26005)), new V1_5Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26005, config.port());
        assertEquals("1.5", config.versionName());
        assertEquals(LegacyProtocol.V1_5_1, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds151And15() {
        VersionAdapter adapter151 = DetergentServer.selectAdapter("1.5.1");
        assertEquals("1.5.1", adapter151.minecraftVersion());
        assertEquals(LegacyProtocol.V1_5_1, adapter151.protocolVersion());
        VersionAdapter adapter15 = DetergentServer.selectAdapter("1.5");
        assertEquals("1.5", adapter15.minecraftVersion());
        assertEquals(LegacyProtocol.V1_5_1, adapter15.protocolVersion());
    }

    @Test
    void legacy147BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.4.7",
                "server", Map.of("motd", "DetergentMC 1.4.7"),
                "max-players", 10,
                "legacy", Map.of("port", 26006)), new V1_4_7Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26006, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.4.7", config.versionName());
        assertEquals(LegacyProtocol.V1_4_7, config.legacyProtocol());
    }

    @Test
    void legacy146BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.4.6",
                "server", Map.of("motd", "DetergentMC 1.4.6"),
                "max-players", 10,
                "legacy", Map.of("port", 26007)), new V1_4_6Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26007, config.port());
        assertEquals("1.4.6", config.versionName());
        assertEquals(LegacyProtocol.V1_4_7, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds147And146() {
        VersionAdapter adapter147 = DetergentServer.selectAdapter("1.4.7");
        assertEquals("1.4.7", adapter147.minecraftVersion());
        assertEquals(LegacyProtocol.V1_4_7, adapter147.protocolVersion());
        VersionAdapter adapter146 = DetergentServer.selectAdapter("1.4.6");
        assertEquals("1.4.6", adapter146.minecraftVersion());
        assertEquals(LegacyProtocol.V1_4_7, adapter146.protocolVersion());
    }

    @Test
    void legacy145BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.4.5",
                "server", Map.of("motd", "DetergentMC 1.4.5"),
                "max-players", 10,
                "legacy", Map.of("port", 26008)), new V1_4_5Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26008, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.4.5", config.versionName());
        assertEquals(LegacyProtocol.V1_4_5, config.legacyProtocol());
    }

    @Test
    void legacy144BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.4.4",
                "server", Map.of("motd", "DetergentMC 1.4.4"),
                "max-players", 10,
                "legacy", Map.of("port", 26009)), new V1_4_4Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26009, config.port());
        assertEquals("1.4.4", config.versionName());
        assertEquals(LegacyProtocol.V1_4_5, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds145And144() {
        VersionAdapter adapter145 = DetergentServer.selectAdapter("1.4.5");
        assertEquals("1.4.5", adapter145.minecraftVersion());
        assertEquals(LegacyProtocol.V1_4_5, adapter145.protocolVersion());
        VersionAdapter adapter144 = DetergentServer.selectAdapter("1.4.4");
        assertEquals("1.4.4", adapter144.minecraftVersion());
        assertEquals(LegacyProtocol.V1_4_5, adapter144.protocolVersion());
    }

    @Test
    void legacy142BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.4.2",
                "server", Map.of("motd", "DetergentMC 1.4.2"),
                "max-players", 10,
                "legacy", Map.of("port", 26010)), new V1_4_2Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26010, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.4.2", config.versionName());
        assertEquals(LegacyProtocol.V1_4_2, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds142() {
        VersionAdapter adapter142 = DetergentServer.selectAdapter("1.4.2");
        assertEquals("1.4.2", adapter142.minecraftVersion());
        // Legacy protocol 47 (1.4.2) — the legacy numbering space,
        // not the Netty protocol 47 (1.8).
        assertEquals(LegacyProtocol.V1_4_2, adapter142.protocolVersion());
    }

    @Test
    void legacy132BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.3.2",
                "server", Map.of("motd", "DetergentMC 1.3.2"),
                "max-players", 10,
                "legacy", Map.of("port", 26011)), new V1_3_2Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26011, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.3.2", config.versionName());
        assertEquals(LegacyProtocol.V1_3_2, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds132And131() {
        VersionAdapter adapter132 = DetergentServer.selectAdapter("1.3.2");
        assertEquals("1.3.2", adapter132.minecraftVersion());
        assertEquals(LegacyProtocol.V1_3_2, adapter132.protocolVersion());
        VersionAdapter adapter131 = DetergentServer.selectAdapter("1.3.1");
        assertEquals("1.3.1", adapter131.minecraftVersion());
        // 1.3.1 shares protocol 39 with 1.3.2 (measured on the real
        // jars; the widespread "1.3.1 = 38" entry is wrong).
        assertEquals(LegacyProtocol.V1_3_2, adapter131.protocolVersion());
    }

    @Test
    void legacy125BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.2.5",
                "server", Map.of("motd", "DetergentMC 1.2.5"),
                "max-players", 10,
                "legacy", Map.of("port", 26012)), new V1_2_5Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26012, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.2.5", config.versionName());
        assertEquals(LegacyProtocol.V1_2_5, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds125() {
        VersionAdapter adapter125 = DetergentServer.selectAdapter("1.2.5");
        assertEquals("1.2.5", adapter125.minecraftVersion());
        // Legacy protocol 29 (1.2.5) — the legacy numbering space,
        // not a Netty protocol number.
        assertEquals(LegacyProtocol.V1_2_5, adapter125.protocolVersion());
    }

    @Test
    void legacy124BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.2.4",
                "server", Map.of("motd", "DetergentMC 1.2.4"),
                "max-players", 10,
                "legacy", Map.of("port", 26013)), new V1_2_4Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26013, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.2.4", config.versionName());
        // 1.2.4 is protocol 29, the same number as 1.2.5 (measured).
        assertEquals(LegacyProtocol.V1_2_5, config.legacyProtocol());
    }

    @Test
    void legacy123BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.2.3",
                "server", Map.of("motd", "DetergentMC 1.2.3"),
                "max-players", 10,
                "legacy", Map.of("port", 26014)), new V1_2_3Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26014, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.2.3", config.versionName());
        assertEquals(LegacyProtocol.V1_2_3, config.legacyProtocol());
    }

    @Test
    void legacy122BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.2.2",
                "server", Map.of("motd", "DetergentMC 1.2.2"),
                "max-players", 10,
                "legacy", Map.of("port", 26015)), new V1_2_2Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26015, config.port());
        assertEquals("1.2.2", config.versionName());
        // 1.2.2 shares protocol 28 with 1.2.3 (measured).
        assertEquals(LegacyProtocol.V1_2_3, config.legacyProtocol());
    }

    @Test
    void legacy121BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.2.1",
                "server", Map.of("motd", "DetergentMC 1.2.1"),
                "max-players", 10,
                "legacy", Map.of("port", 26016)), new V1_2_1Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26016, config.port());
        assertEquals("1.2.1", config.versionName());
        // 1.2.1 shares protocol 28 with 1.2.3 (measured).
        assertEquals(LegacyProtocol.V1_2_3, config.legacyProtocol());
    }

    @Test
    void legacy11BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.1",
                "server", Map.of("motd", "DetergentMC 1.1"),
                "max-players", 10,
                "legacy", Map.of("port", 26017)), new V1_1Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26017, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.1", config.versionName());
        assertEquals(LegacyProtocol.V1_1, config.legacyProtocol());
    }

    @Test
    void legacy100BackendUsesDirectMode() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.0.0",
                "server", Map.of("motd", "DetergentMC 1.0.0"),
                "max-players", 10,
                "legacy", Map.of("port", 26018)), new V1_0_0Adapter());
        LegacyFrontendConfig config = server.legacyConfig(25565, 25566);
        assertEquals(LegacyFrontendConfig.Mode.DIRECT, config.mode());
        assertEquals(26018, config.port());
        assertEquals(25566, config.backendPort());
        assertEquals("1.0.0", config.versionName());
        assertEquals(LegacyProtocol.V1_0, config.legacyProtocol());
    }

    @Test
    void adapterDiscoveryFinds124And123And11And100() {
        VersionAdapter adapter124 = DetergentServer.selectAdapter("1.2.4");
        assertEquals("1.2.4", adapter124.minecraftVersion());
        // 1.2.4 shares protocol 29 with 1.2.5 (measured on the real
        // jars; the widespread "1.2.4 = 28" entry is wrong).
        assertEquals(LegacyProtocol.V1_2_5, adapter124.protocolVersion());
        VersionAdapter adapter123 = DetergentServer.selectAdapter("1.2.3");
        assertEquals("1.2.3", adapter123.minecraftVersion());
        assertEquals(LegacyProtocol.V1_2_3, adapter123.protocolVersion());
        VersionAdapter adapter122 = DetergentServer.selectAdapter("1.2.2");
        assertEquals("1.2.2", adapter122.minecraftVersion());
        assertEquals(LegacyProtocol.V1_2_3, adapter122.protocolVersion());
        VersionAdapter adapter121 = DetergentServer.selectAdapter("1.2.1");
        assertEquals("1.2.1", adapter121.minecraftVersion());
        assertEquals(LegacyProtocol.V1_2_3, adapter121.protocolVersion());
        VersionAdapter adapter11 = DetergentServer.selectAdapter("1.1");
        assertEquals("1.1", adapter11.minecraftVersion());
        assertEquals(LegacyProtocol.V1_1, adapter11.protocolVersion());
        VersionAdapter adapter100 = DetergentServer.selectAdapter("1.0.0");
        assertEquals("1.0.0", adapter100.minecraftVersion());
        assertEquals(LegacyProtocol.V1_0, adapter100.protocolVersion());
    }

    @Test
    void legacyMotdOverridesServerMotd() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "server", Map.of("motd", "Java MOTD"),
                "legacy", Map.of("motd", "Legacy MOTD")), new V1204Adapter());
        assertEquals("Legacy MOTD", server.legacyConfig(25565, 25566).motd());
    }

    @Test
    void adapterDiscoveryFinds164() {
        // The frontend module's adapter must be selectable for 1.6.4,
        // or DetergentServer.create() would refuse the version.
        VersionAdapter adapter = DetergentServer.selectAdapter("1.6.4");
        assertEquals("1.6.4", adapter.minecraftVersion());
    }
}
