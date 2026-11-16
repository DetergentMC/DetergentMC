package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.adapter.v1_20_4.V1204Adapter;
import io.detergentmc.protocol.ClientProxy;
import io.detergentmc.protocol.ProxyAuthConfig;
import io.detergentmc.protocol.ResourcePack;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Wiring tests for the internal server protocol in {@link DetergentServer}:
 * {@code minecraft-version} in the config must decide the protocol the
 * proxy translates toward (764 for 1.20.2, 765 otherwise).
 */
class ServerProtocolWiringTest {

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
    void minecraftVersion1194SelectsInternalProtocol762() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.19.4"));
        assertEquals(762, server.serverProtocol());
    }

    @Test
    void minecraftVersion1191SelectsInternalProtocol760() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.19.1"));
        assertEquals(760, server.serverProtocol());
    }

    @Test
    void minecraftVersion1192SelectsInternalProtocol760() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.19.2"));
        assertEquals(760, server.serverProtocol());
    }

    @Test
    void minecraftVersion1201SelectsInternalProtocol763() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.20.1"));
        assertEquals(763, server.serverProtocol());
        DetergentServer server120 = serverWith(Map.of("minecraft-version", "1.20"));
        assertEquals(763, server120.serverProtocol());
    }

    @Test
    void minecraftVersion1202SelectsInternalProtocol764() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.20.2"));
        assertEquals(764, server.serverProtocol());
    }

    @Test
    void minecraftVersion1204SelectsInternalProtocol765() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.20.4"));
        assertEquals(765, server.serverProtocol());
    }

    @Test
    void minecraftVersion121SelectsInternalProtocol767() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21"));
        assertEquals(767, server.serverProtocol());
        DetergentServer server1211 = serverWith(Map.of("minecraft-version", "1.21.1"));
        assertEquals(767, server1211.serverProtocol());
    }

    @Test
    void minecraftVersion1212SelectsInternalProtocol768() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.2"));
        assertEquals(768, server.serverProtocol());
        DetergentServer server1213 = serverWith(Map.of("minecraft-version", "1.21.3"));
        assertEquals(768, server1213.serverProtocol());
    }

    @Test
    void minecraftVersion1214SelectsInternalProtocol769() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.4"));
        assertEquals(769, server.serverProtocol());
    }

    @Test
    void minecraftVersion1215SelectsInternalProtocol770() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.5"));
        assertEquals(770, server.serverProtocol());
    }

    @Test
    void minecraftVersion1217SelectsInternalProtocol772() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.7"));
        assertEquals(772, server.serverProtocol());
    }

    @Test
    void minecraftVersion1218SelectsInternalProtocol772() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.8"));
        assertEquals(772, server.serverProtocol());
    }

    @Test
    void minecraftVersion1219SelectsInternalProtocol773() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.9"));
        assertEquals(773, server.serverProtocol());
    }

    @Test
    void minecraftVersion12110SelectsInternalProtocol773() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.10"));
        assertEquals(773, server.serverProtocol());
    }

    @Test
    void minecraftVersion12111SelectsInternalProtocol774() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.11"));
        assertEquals(774, server.serverProtocol());
    }

    @Test
    void minecraftVersion1216SelectsInternalProtocol771() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.21.6"));
        assertEquals(771, server.serverProtocol());
    }

    @Test
    void missingMinecraftVersionDefaultsToInternalProtocol765() throws Exception {
        DetergentServer server = serverWith(Map.of());
        assertEquals(765, server.serverProtocol());
    }

    @Test
    void unknownMinecraftVersionFallsBackToInternalProtocol765() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.99.9"));
        assertEquals(765, server.serverProtocol());
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * minecraft-version 1.20.1 のとき、プロキシは内部763で作られ、
     * 765クライアント用の差し替えパックを渡す（内部763のバニラが出すのは
     * pack_format 15 のパックで、765クライアントには使えないため）。
     * バニラ（内部763）に渡すパックは763用（pack_format 15）。
     */
    @Test
    void clientProxyFor1201UsesInternalProtocol763And765Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.1",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(763, proxy.internalProtocol());
            assertNotNull(proxy.pack763());
            assertNotNull(proxy.pack764());
            // 内部763のときも、765クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack(), proxy.pack765());
            // 内部が766でないため、766クライアント用の差し替えパックも渡す。
            assertSame(server.resourcePack766(), proxy.pack766());
            // 内部が767でないため、767クライアント用の差し替えパックも渡す。
            assertSame(server.resourcePack767(), proxy.pack767());
            // バニラ（内部763）に渡すパックは763用。
            assertSame(server.resourcePack763(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.20.2 のとき、プロキシは内部764・765用パック付きで
     * 作られる（DetergentServer から ClientProxy への配線）。
     */
    @Test
    void clientProxyFor1202UsesInternalProtocol764And765Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.2",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(764, proxy.internalProtocol());
            assertNotNull(proxy.pack763());
            assertNotNull(proxy.pack764());
            // 内部764のときだけ、765クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack(), proxy.pack765());
            // 内部が766でないため、766クライアント用の差し替えパックも渡す。
            assertSame(server.resourcePack766(), proxy.pack766());
            // 内部が767でないため、767クライアント用の差し替えパックも渡す。
            assertSame(server.resourcePack767(), proxy.pack767());
            // バニラ（内部764）に渡すパックは764用。
            assertSame(server.resourcePack764(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.20.4 のとき、プロキシは内部765・pack765 なしで
     * 作られる（従来通りの経路で、バニラのパックがそのまま通る）。
     */
    @Test
    void clientProxyFor1204UsesInternalProtocol765AndNoPack765() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(765, proxy.internalProtocol());
            assertNotNull(proxy.pack763());
            assertNotNull(proxy.pack764());
            assertNull(proxy.pack765());
            // 内部が766でないため、766クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack766(), proxy.pack766());
            // 内部が767でないため、767クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack767(), proxy.pack767());
            // バニラ（内部765）に渡すパックは765用。
            assertSame(server.resourcePack(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.20.5 のとき、プロキシは内部766・pack766 なしで
     * 作られる（ネイティブの766クライアントはリレーでバニラのパックが
     * そのまま通る）。
     */
    @Test
    void clientProxyFor1205UsesInternalProtocol766AndNoPack766() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.5",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(766, proxy.internalProtocol());
            assertNotNull(proxy.pack763());
            assertNotNull(proxy.pack764());
            assertNull(proxy.pack765());
            assertNull(proxy.pack766());
            // 内部が767でないため、767クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack767(), proxy.pack767());
            // バニラ（内部766）に渡すパックは766用。
            assertSame(server.resourcePack766(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.21 のとき、プロキシは内部767・pack767 なしで
     * 作られる（ネイティブの767クライアントはリレーでバニラのパックが
     * そのまま通る）。1.21.1 も同じ内部767である。
     */
    @Test
    void clientProxyFor121UsesInternalProtocol767AndNoPack767() throws Exception {
        for (String version : new String[]{"1.21", "1.21.1"}) {
            DetergentServer server = serverWith(Map.of(
                    "minecraft-version", version,
                    "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
            try {
                assertNotNull(server.startResourcePack());
                ClientProxy proxy = server.createClientProxy(
                        freePort(), freePort(), false, ProxyAuthConfig.defaults());
                assertEquals(767, proxy.internalProtocol());
                assertNotNull(proxy.pack763());
                assertNotNull(proxy.pack764());
                assertNull(proxy.pack765());
                // 内部が766でないため、766クライアント用の差し替えパックを渡す。
                assertSame(server.resourcePack766(), proxy.pack766());
                assertNull(proxy.pack767());
                // バニラ（内部767）に渡すパックは767用。
                assertSame(server.resourcePack767(), server.hostResourcePack());
            } finally {
                server.stopResourcePack();
            }
        }
    }

    /**
     * minecraft-version 1.21.2 のとき、プロキシは内部768・pack768 なしで
     * 作られる（ネイティブの768クライアントはリレーでバニラのパックが
     * そのまま通る）。
     */
    @Test
    void clientProxyFor1212UsesInternalProtocol768AndNoPack768() throws Exception {
        for (String version : new String[]{"1.21.2", "1.21.3"}) {
            DetergentServer server = serverWith(Map.of(
                    "minecraft-version", version,
                    "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
            try {
                assertNotNull(server.startResourcePack());
                ClientProxy proxy = server.createClientProxy(
                        freePort(), freePort(), false, ProxyAuthConfig.defaults());
                assertEquals(768, proxy.internalProtocol());
                assertNotNull(proxy.pack763());
                assertNotNull(proxy.pack764());
                assertNull(proxy.pack765());
                // 内部が766でないため、766クライアント用の差し替えパックを渡す。
                assertSame(server.resourcePack766(), proxy.pack766());
                // 内部が767でないため、767クライアント用の差し替えパックを渡す。
                assertSame(server.resourcePack767(), proxy.pack767());
                assertNull(proxy.pack768());
                // 内部が769でないため、769クライアント用の差し替えパックを渡す。
                assertSame(server.resourcePack769(), proxy.pack769());
                // 内部が770でないため、770クライアント用の差し替えパックを渡す。
                assertSame(server.resourcePack770(), proxy.pack770());
                // バニラ（内部768）に渡すパックは768用。
                assertSame(server.resourcePack768(), server.hostResourcePack());
            } finally {
                server.stopResourcePack();
            }
        }
    }

    /**
     * minecraft-version 1.21.4 のとき、プロキシは内部769・pack769 なしで
     * 作られる（ネイティブの769クライアントはリレーでバニラのパックが
     * そのまま通る）。
     */
    @Test
    void clientProxyFor1214UsesInternalProtocol769AndNoPack769() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.21.4",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(769, proxy.internalProtocol());
            assertNotNull(proxy.pack763());
            assertNotNull(proxy.pack764());
            assertNull(proxy.pack765());
            // 内部が766でないため、766クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack766(), proxy.pack766());
            // 内部が767でないため、767クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack767(), proxy.pack767());
            // 内部が768でないため、768クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack768(), proxy.pack768());
            assertNull(proxy.pack769());
            // バニラ（内部769）に渡すパックは769用。
            assertSame(server.resourcePack769(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.21.5 のとき、プロキシは内部770・pack770 なしで
     * 作られる（ネイティブの770クライアントはリレーでバニラのパックが
     * そのまま通る）。
     */
    @Test
    void clientProxyFor1215UsesInternalProtocol770AndNoPack770() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.21.5",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(770, proxy.internalProtocol());
            assertNotNull(proxy.pack763());
            assertNotNull(proxy.pack764());
            assertNull(proxy.pack765());
            // 内部が766でないため、766クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack766(), proxy.pack766());
            // 内部が767でないため、767クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack767(), proxy.pack767());
            // 内部が768でないため、768クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack768(), proxy.pack768());
            // 内部が769でないため、769クライアント用の差し替えパックを渡す。
            assertSame(server.resourcePack769(), proxy.pack769());
            assertNull(proxy.pack770());
            // バニラ（内部770）に渡すパックは770用。
            assertSame(server.resourcePack770(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.20.4（内部765）のとき、プロキシには本物の756パック
     * （ループバックでビルドしたもの）が渡される。以前は packFor756Clients
     * が null で、ProxySession が756クライアントに pack757 を落として出していた。
     */
    @Test
    void clientProxyFor1204PassesReal756Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(765, proxy.internalProtocol());
            // The real 756 pack (built by startResourcePack), not null and
            // not the placeholder from ClientProxy756Test.
            // (ResourcePackServer.pack756() builds a fresh instance per
            // call, so compare by value, not identity.)
            assertNotNull(proxy.pack756());
            assertEquals(server.resourcePackServer().pack756(), proxy.pack756());
            assertTrue(proxy.pack756().url().endsWith("/detergentmc-pack-1.17.1.zip"),
                    proxy.pack756().url());
            // The 757 pack is passed the same way.
            assertNotNull(proxy.pack757());
            assertEquals(server.resourcePackServer().pack757(), proxy.pack757());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.17.1（内部756）のとき、プロキシには756パックを渡さない
     * （ネイティブの756クライアントはリレーでバニラのパックがそのまま通る）。
     */
    @Test
    void clientProxyFor1171UsesInternalProtocol756AndNoPack756() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.17.1",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(756, proxy.internalProtocol());
            assertNull(proxy.pack756());
            // 内部が757でないため、757クライアント用の差し替えパックは渡す。
            assertNotNull(proxy.pack757());
            assertEquals(server.resourcePackServer().pack757(), proxy.pack757());
            // バニラ（内部756）に渡すパックは756用。
            assertEquals(server.resourcePackServer().pack756(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.20.4（内部765）のとき、プロキシには本物の755パック
     * （ループバックでビルドしたもの）が渡される。以前は packFor755Clients
     * が null で、ProxySession が755クライアントに pack756 を落として出していた。
     */
    @Test
    void clientProxyFor1204PassesReal755Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(765, proxy.internalProtocol());
            // The real 755 pack (built by startResourcePack), not null and
            // not the placeholder from ClientProxy755Test.
            // (ResourcePackServer.pack755() builds a fresh instance per
            // call, so compare by value, not identity.)
            assertNotNull(proxy.pack755());
            assertEquals(server.resourcePackServer().pack755(), proxy.pack755());
            assertTrue(proxy.pack755().url().endsWith("/detergentmc-pack-1.17.zip"),
                    proxy.pack755().url());
            // The 756 pack is passed the same way.
            assertNotNull(proxy.pack756());
            assertEquals(server.resourcePackServer().pack756(), proxy.pack756());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.17（内部755）のとき、プロキシには755パックを渡さない
     * （ネイティブの755クライアントはリレーでバニラのパックがそのまま通る）。
     */
    @Test
    void clientProxyFor117UsesInternalProtocol755AndNoPack755() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.17",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(755, proxy.internalProtocol());
            assertNull(proxy.pack755());
            // 内部が756でないため、756クライアント用の差し替えパックは渡す
            // （755クライアントに755パックが無い場合のフォールバック先でもある）。
            assertNotNull(proxy.pack756());
            assertEquals(server.resourcePackServer().pack756(), proxy.pack756());
            // バニラ（内部755）に渡すパックは755用。
            assertEquals(server.resourcePackServer().pack755(), server.hostResourcePack());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.20.4（内部765）のとき、プロキシには本物の757パック
     * （ループバックでビルドしたもの）が渡される。以前は packFor757Clients
     * が null で、ProxySession が757クライアントに pack759 を落として出していた。
     */
    @Test
    void clientProxyFor1204PassesReal757Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.4",
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            assertNotNull(server.startResourcePack());
            ClientProxy proxy = server.createClientProxy(
                    freePort(), freePort(), false, ProxyAuthConfig.defaults());
            assertEquals(765, proxy.internalProtocol());
            // The real 757 pack (built by startResourcePack), not null and
            // not the placeholder from ClientProxy757Test.
            // (ResourcePackServer.pack757() builds a fresh instance per
            // call, so compare by value, not identity.)
            assertNotNull(proxy.pack757());
            assertEquals(server.resourcePackServer().pack757(), proxy.pack757());
            assertTrue(proxy.pack757().url().endsWith("/detergentmc-pack-1.18.zip"),
                    proxy.pack757().url());
            // The 775 pack is passed the same way.
            assertNotNull(proxy.pack775());
            assertEquals(server.resourcePackServer().pack775(), proxy.pack775());
        } finally {
            server.stopResourcePack();
        }
    }

    /**
     * minecraft-version 1.18／1.18.1（内部757）のとき、プロキシには757パックを渡さない
     * （ネイティブの757クライアントはリレーでバニラのパックがそのまま通る）。
     */
    @Test
    void clientProxyFor118UsesInternalProtocol757AndNoPack757() throws Exception {
        for (String version : new String[]{"1.18", "1.18.1"}) {
            DetergentServer server = serverWith(Map.of(
                    "minecraft-version", version,
                    "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
            try {
                assertNotNull(server.startResourcePack());
                ClientProxy proxy = server.createClientProxy(
                        freePort(), freePort(), false, ProxyAuthConfig.defaults());
                assertEquals(757, proxy.internalProtocol());
                assertNull(proxy.pack757());
                // 内部が775でないため、775クライアント用の差し替えパックは渡す。
                assertNotNull(proxy.pack775());
                assertEquals(server.resourcePackServer().pack775(), proxy.pack775());
                // バニラ（内部757）に渡すパックは757用。
                assertEquals(server.resourcePackServer().pack757(), server.hostResourcePack());
            } finally {
                server.stopResourcePack();
            }
        }
    }

    /**
     * 764用パックが用意できなかったとき、バニラに渡すパックは765用に
     * フォールバックする（外部モードで url-764 が無い場合など）。
     */
    @Test
    void hostResourcePackFallsBackTo765PackWhenNo764Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "minecraft-version", "1.20.2",
                "resource-pack", Map.of(
                        "enabled", true,
                        "url", "https://example.invalid/packs/detergentmc-pack.zip",
                        "sha1", "0123456789abcdef0123456789abcdef01234567",
                        "required", false)));
        ResourcePack pack = server.startResourcePack();
        assertNotNull(pack);
        assertNull(server.resourcePack764());
        assertSame(pack, server.hostResourcePack());
    }
}
