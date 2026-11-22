package io.detergentmc.core;

import io.detergentmc.api.CommandSender;
import io.detergentmc.api.ServerStartEvent;
import io.detergentmc.api.ServerStopEvent;
import io.detergentmc.api.VersionAdapter;
import io.detergentmc.core.bridge.GameBridge;
import io.detergentmc.core.bridge.PlayerLocaleStore;
import io.detergentmc.core.bridge.PlayerRegistry;
import io.detergentmc.core.vanilla.DatapackGenerator;
import io.detergentmc.core.vanilla.VanillaServerHost;
import io.detergentmc.bedrock.BedrockConfig;
import io.detergentmc.bedrock.BedrockProtocol;
import io.detergentmc.bedrock.BedrockServer;
import io.detergentmc.legacy.LegacyFrontendConfig;
import io.detergentmc.legacy.LegacyFrontendServer;
import io.detergentmc.legacy.LegacyProtocol;
import io.detergentmc.protocol.ClientProxy;
import io.detergentmc.protocol.ProtocolVersion;
import io.detergentmc.protocol.ProxyAuthConfig;
import io.detergentmc.protocol.ResourcePack;
import io.detergentmc.protocol.ResourcePackBuilder;
import io.detergentmc.protocol.ResourcePackServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * The DetergentMC server skeleton (MVP).
 *
 * <p>What this MVP does: it loads the configuration, the language files,
 * the version adapter for the configured Minecraft version, and every
 * Bubble in {@code bubbles/}. It then waits for console commands
 * ({@code help}, {@code bubbles}, {@code stop}).
 *
 * <p>What it does NOT do yet (honest TODO): it does not start a real
 * Minecraft world, it does not accept player connections, and it does not
 * bind to vanilla internals. The vanilla binding is the next milestone
 * (see docs/VERSIONS.md). This skeleton proves the shape: core + API +
 * adapter + Bubble loading + multilingual messages all compile and run.
 */
public final class DetergentServer {

    private static final Logger LOG = Logger.getLogger(DetergentServer.class.getName());

    private final Path workingDir;
    private final YamlConfig config;
    private final LocaleManager translator;
    private final SimpleEventBus events;
    private final SimpleCommandRegistry commands;
    private final CoreServer server;
    private final BubbleLoader bubbleLoader;
    /**
     * The protocol number the internal (vanilla) server speaks, decided
     * from {@code minecraft-version} via
     * {@link ProtocolVersion#serverProtocolFor(String)}: 763 for 1.20 /
     * 1.20.1, 764 for 1.20.2, 765 for 1.20.3 / 1.20.4, 766 for 1.20.5 /
     * 1.20.6, 767 for 1.21 / 1.21.1 (765 for unknown versions).
     * The proxy translates <em>toward</em> this protocol; every component
     * that needs the translation direction must read this value (or call
     * {@code serverProtocolFor} itself) instead of assuming 765.
     */
    private final int serverProtocol;
    private volatile boolean running;
    private VanillaServerHost vanillaHost;
    private ClientProxy clientProxy;
    private BedrockServer bedrockServer;
    private LegacyFrontendServer legacyServer;
    private ResourcePackServer resourcePackServer;
    private ResourcePack resourcePack;
    /** 47クライアントに送るリソースパック（pack_format 1）。無ければ null。 */
    private ResourcePack resourcePack47;
    private ResourcePack resourcePack5;
    private ResourcePack resourcePack4;

    /** 107クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack107;
    /** 108クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack108;
    /** 109クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack109;
    /** 110クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack110;
    /** 210クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack210;
    /** 315クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack315;
    /** 316クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack316;
    /** 335クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack335;
    /** 338クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack338;
    /** 340クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack340;
    /** 393クライアントに送るリソースパック（pack_format 4）。無ければ null。 */
    private ResourcePack resourcePack393;
    /** 578クライアントに送るリソースパック（pack_format 5）。無ければ null。 */
    private ResourcePack resourcePack401;
    private ResourcePack resourcePack404;
    private ResourcePack resourcePack477;
    private ResourcePack resourcePack480;
    private ResourcePack resourcePack485;
    private ResourcePack resourcePack490;
    private ResourcePack resourcePack498;
    private ResourcePack resourcePack573;
    private ResourcePack resourcePack575;
    private ResourcePack resourcePack578;
    /** 758クライアントに送るリソースパック（pack_format 8）。無ければ null。 */
    private ResourcePack resourcePack758;
    /** 735クライアントに送るリソースパック（pack_format 5）。無ければ null。 */
    private ResourcePack resourcePack735;
    /** 736クライアントに送るリソースパック（pack_format 5）。無ければ null。 */
    private ResourcePack resourcePack736;
    /** 751クライアントに送るリソースパック（pack_format 6）。無ければ null。 */
    private ResourcePack resourcePack751;
    /** 753クライアントに送るリソースパック（pack_format 6）。無ければ null。 */
    private ResourcePack resourcePack753;
    /** 754クライアントに送るリソースパック（pack_format 6）。無ければ null。1.16.4と1.16.5で共用。 */
    private ResourcePack resourcePack754;
    /** 755クライアントに送るリソースパック（pack_format 7）。無ければ null。 */
    private ResourcePack resourcePack755;
    /** 756クライアントに送るリソースパック（pack_format 7）。無ければ null。 */
    private ResourcePack resourcePack756;
    /** 757クライアントに送るリソースパック（pack_format 8）。無ければ null。1.18と1.18.1で共用。 */
    private ResourcePack resourcePack757;
    /** 759クライアントに送るリソースパック（pack_format 9）。無ければ null。 */
    private ResourcePack resourcePack759;
    /** 760クライアントに送るリソースパック（pack_format 9）。無ければ null。 */
    private ResourcePack resourcePack760;
    /** 761クライアントに送るリソースパック（pack_format 12）。無ければ null。 */
    private ResourcePack resourcePack761;
    /** 762クライアントに送るリソースパック（pack_format 13）。無ければ null。 */
    private ResourcePack resourcePack762;
    /** 763クライアントに送るリソースパック（pack_format 15）。無ければ null。 */
    private ResourcePack resourcePack763;
    /** 764クライアントに送るリソースパック（pack_format 18）。無ければ null。 */
    private ResourcePack resourcePack764;
    /** 766クライアントに送るリソースパック（pack_format 32）。無ければ null。1.20.5と1.20.6で共用。 */
    private ResourcePack resourcePack766;
    /** 767クライアントに送るリソースパック（pack_format 34）。無ければ null。1.21と1.21.1で共用。 */
    private ResourcePack resourcePack767;
    /** 768クライアントに送るリソースパック（pack_format 42）。無ければ null。1.21.2と1.21.3で共用。 */
    private ResourcePack resourcePack768;
    /** 769クライアントに送るリソースパック（pack_format 46）。無ければ null。 */
    private ResourcePack resourcePack769;
    /** 770クライアントに送るリソースパック（pack_format 55）。無ければ null。 */
    private ResourcePack resourcePack770;
    /** 771クライアントに送るリソースパック（pack_format 63）。無ければ null。 */
    private ResourcePack resourcePack771;
    /** 772クライアントに送るリソースパック（pack_format 64）。無ければ null。 */
    private ResourcePack resourcePack772;
    /** 773クライアントに送るリソースパック（pack_format 69）。無ければ null。 */
    private ResourcePack resourcePack773;
    /** 774クライアントに送るリソースパック（pack_format 75）。無ければ null。 */
    private ResourcePack resourcePack774;
    /** 775クライアントに送るリソースパック（pack_format 84）。無ければ null。26.1／26.1.1／26.1.2で共用。 */
    private ResourcePack resourcePack775;
    /** 776クライアントに送るリソースパック（pack_format 88）。無ければ null。26.2にホットフィックスは無い。 */
    private ResourcePack resourcePack776;
    private GameBridge gameBridge;
    private PlayerRegistry playerRegistry;

    public DetergentServer(Path workingDir, YamlConfig config,
            LocaleManager translator, SimpleEventBus events,
            SimpleCommandRegistry commands, CoreServer server,
            BubbleLoader bubbleLoader) {
        this.workingDir = workingDir;
        this.config = config;
        this.translator = translator;
        this.events = events;
        this.commands = commands;
        this.server = server;
        this.bubbleLoader = bubbleLoader;
        // Legacy backends (pre-Netty, e.g. 1.6.4) have no Netty protocol
        // number: the legacy frontend serves them in direct mode and the
        // Netty proxy is not started, so serverProtocol is 0 ("none").
        // The legacy number itself (78 for 1.6.4) lives only in the
        // legacy module and is never used as a Netty protocol here.
        String minecraftVersion = config.getString("minecraft-version", "1.20.4");
        this.serverProtocol = LegacyProtocol.isLegacyBackendVersion(minecraftVersion)
                ? 0
                : ProtocolVersion.serverProtocolFor(minecraftVersion);
    }

    /**
     * Creates a server in the given working directory, copying the default
     * {@code detergentmc.yml} and language files on first start.
     */
    public static DetergentServer create(Path workingDir) throws IOException {
        Files.createDirectories(workingDir.resolve("bubbles"));
        Files.createDirectories(workingDir.resolve("locales"));
        Files.createDirectories(workingDir.resolve("data"));

        copyDefaultResource("detergentmc.yml", workingDir.resolve("detergentmc.yml"));
        copyDefaultResource("locales/ja.yml", workingDir.resolve("locales/ja.yml"));
        copyDefaultResource("locales/en.yml", workingDir.resolve("locales/en.yml"));

        YamlConfig config = YamlConfig.load(workingDir.resolve("detergentmc.yml"));
        Locale defaultLocale = Locale.forLanguageTag(
                config.getString("language.default", "ja"));
        LocaleManager translator = new LocaleManager(defaultLocale, workingDir.resolve("locales"));
        translator.loadBaseLanguages();

        String minecraftVersion = config.getString("minecraft-version", "1.20.4");
        VersionAdapter adapter = selectAdapter(minecraftVersion);
        CoreServer server = new CoreServer(adapter);

        // One event bus and one command registry, shared by the server and
        // every Bubble, so Bubbles and the server see the same events.
        SimpleEventBus events = new SimpleEventBus();
        SimpleCommandRegistry commands = new SimpleCommandRegistry();
        BubbleLoader bubbleLoader = new BubbleLoader(
                workingDir.resolve("bubbles"),
                workingDir.resolve("data"),
                translator, events, commands, server);

        return new DetergentServer(
                workingDir, config, translator, events, commands, server, bubbleLoader);
    }

    private static void copyDefaultResource(String resource, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = DetergentServer.class.getResourceAsStream("/" + resource)) {
            if (in == null) {
                return;
            }
            Files.copy(in, target);
        }
    }

    /** Picks the version adapter whose Minecraft version matches the config. */
    static VersionAdapter selectAdapter(String minecraftVersion) {
        for (VersionAdapter adapter : ServiceLoader.load(VersionAdapter.class)) {
            if (adapter.minecraftVersion().equals(minecraftVersion)) {
                return adapter;
            }
        }
        throw new IllegalStateException(
                "No version adapter found for Minecraft " + minecraftVersion
                        + ". Add the adapter module for that version (see docs/VERSIONS.md).");
    }

    public void start() throws IOException {
        running = true;
        LOG.info(() -> translator.translate("server.starting",
                server.detergentVersion(), server.minecraftVersion()));
        bubbleLoader.loadAll();
        try {
            startVanilla();
        } catch (IOException e) {
            running = false;
            bubbleLoader.disableAll();
            throw new IOException(translator.translate("vanilla.failed", e.getMessage()), e);
        }
        events.fire(new ServerStartEvent(server));
        LOG.info(() -> translator.translate("server.started",
                bubbleLoader.loaded().size()));
        LOG.info(() -> translator.translate("server.console-help"));
        consoleLoop();
    }

    /**
     * Starts the vanilla server as a child process, after the Bubbles are
     * loaded. The vanilla server owns the world and the players; DetergentMC
     * only hosts it. Settings come from {@code detergentmc.yml}.
     *
     * <p>Port layout: the DetergentMC proxy listens on the public port
     * ({@code server.port}, default 25565) and the vanilla server listens
     * on the internal port ({@code server.internal-port}, default 25566)
     * bound to 127.0.0.1 only. Players never connect to vanilla directly,
     * so a 1.20.1 client can join through the proxy's translation.
     * If the proxy fails to start, the vanilla server is stopped again
     * and startup aborts — a running vanilla server must never be left
     * behind without its proxy.
     *
     * <p>The internal protocol is decided from {@code minecraft-version}
     * (see {@link #serverProtocol()}): 763 when the server itself runs
     * 1.20 / 1.20.1, 764 when it runs 1.20.2, 765 when it runs 1.20.4,
     * 766 when it runs 1.20.5 / 1.20.6, 767 when it runs 1.21 / 1.21.1.
     * The vanilla host receives the
     * version string (it downloads and starts that vanilla jar) and the
     * resource pack matching that version (see {@link #hostResourcePack()}),
     * and the proxy receives the same protocol via
     * {@link #createClientProxy(int, int, boolean, ProxyAuthConfig)},
     * so the proxy translates toward the running vanilla version.
     */
    private void startVanilla() throws IOException {
        String minecraftVersion = config.getString("minecraft-version", "1.20.4");
        LOG.info(() -> "Internal server protocol: " + serverProtocol
                + " (Minecraft " + minecraftVersion + ")");
        int port = config.getInt("server.port", 25565);
        int internalPort = config.getInt("server.internal-port", 25566);
        boolean onlineMode = config.getBoolean("server.online-mode", true);
        int maxPlayers = config.getInt("max-players", 20);
        String motd = config.getString("server.motd", "DetergentMC 1.20.4");
        int memoryMb = config.getInt("vanilla.memory-mb", 1024);
        // Offer the DetergentMC resource pack when enabled. In loopback
        // mode (the default) the pack is built and served over HTTP on
        // 127.0.0.1; in external mode (resource-pack.url is an https://
        // URL) no local server is started and the configured URL/SHA-1
        // are handed to the vanilla host as-is. When the pack cannot be
        // prepared and it is required, startResourcePack() throws and
        // the server does not start; otherwise the host takes null,
        // exactly as before.
        // The prepared packs land in resourcePack / resourcePack763 /
        // resourcePack764 / resourcePack766 / resourcePack767; the vanilla host and the
        // proxy each pick the one matching their side (see
        // hostResourcePack() and createClientProxy()).
        startResourcePack();
        // Build the datapack that bridges in-game /trigger commands into
        // the vanilla log (see DatapackGenerator). It is placed before
        // the vanilla server starts, so even a first start — where the
        // world folder does not exist yet — picks it up when the world
        // is created. A failure only disables the /trigger bridge.
        generateDatapack();
        vanillaHost = new VanillaServerHost(workingDir.resolve("vanilla"),
                minecraftVersion, internalPort, onlineMode, maxPlayers, motd,
                hostResourcePack(), memoryMb);
        // Bridge the vanilla log into DetergentMC API events, commands and
        // players. The bridge also registers the built-in /lang command and
        // logs join / leave / ready (replacing the old log-only listener).
        Path dataDir = workingDir.resolve("data");
        PlayerLocaleStore localeStore = new PlayerLocaleStore(
                dataDir.resolve("player-locales.properties"), translator.defaultLocale());
        playerRegistry = new PlayerRegistry(
                localeStore, translator.defaultLocale(), vanillaHost::sendCommand);
        gameBridge = new GameBridge(
                vanillaHost, events, commands, translator, playerRegistry, dataDir, port);
        vanillaHost.addListener(gameBridge);
        LOG.info(() -> translator.translate("vanilla.starting", minecraftVersion));
        vanillaHost.start();
        // Legacy backends (pre-Netty, e.g. 1.6.4): the Netty proxy cannot
        // speak to the internal vanilla at all, so it is not started.
        // The legacy frontend serves the server in direct mode instead
        // (legacy clients only; modern clients cannot join this backend).
        // Bedrock is not started either: it translates into the Netty
        // proxy path, which does not exist for a legacy backend.
        if (LegacyProtocol.isLegacyBackendVersion(minecraftVersion)) {
            LOG.info(() -> "内部サーバーはレガシー世代（" + minecraftVersion
                    + "）のため、Netty プロキシと Bedrock は起動せず、"
                    + "レガシーフロントエンドのダイレクトモードで提供します。");
            startLegacyFrontend(legacyConfig(port, internalPort));
            return;
        }
        // The proxy is the only public entry point: it listens on the
        // public port and forwards to the vanilla server on the internal
        // (loopback-only) port. If it cannot start, stop vanilla again
        // instead of leaving it running without a proxy.
        // 【担当1】online-mode のとき、Mojang 認証はプロキシが完結させる。
        // 内部バニラは offline-mode（信頼済み接続）で動く。照会先は
        // server.session-server-url（空なら本物の Mojang）。
        ProxyAuthConfig authConfig = ProxyAuthConfig.of(
                config.getString("server.session-server-url", ""));
        clientProxy = createClientProxy(port, internalPort, onlineMode, authConfig);
        try {
            clientProxy.start();
        } catch (IOException e) {
            vanillaHost.stop();
            vanillaHost = null;
            clientProxy = null;
            throw new IOException(translator.translate("vanilla.failed", e.getMessage()), e);
        }
        // Bedrock リスナー（Geyser 風＋Floodgate 風）は Java プロキシと並行して
        // 起動する。バインドに失敗してもサーバー全体は落とさない（Bedrock は
        // 任意機能のため警告ログだけにして続ける）。
        startBedrock(internalPort);
        // レガシーフロントエンド（Netty 以前世代のクライアント受け入れ）は
        // 既定で無効。legacy.enabled: true のときだけ、ブリッジモードで
        // レガシー用ポートに待ち受ける（旧世代は offline 前提のため、
        // 有効化は運用者の明示的な判断に委ねる）。
        if (config.getBoolean("legacy.enabled", false)) {
            startLegacyFrontend(legacyConfig(port, internalPort));
        }
    }

    /**
     * The resource pack the internal vanilla server offers in its own
     * {@code server.properties}: the 735 pack (pack_format 5) when the
     * internal server is 1.16, the 736 pack (pack_format 5) when the
     * internal server is 1.16.1, the 751 pack (pack_format 6) when the
     * internal server is 1.16.2, the 753 pack (pack_format 6) when the
     * internal server is 1.16.3, the 754 pack (pack_format 6) when the
     * internal server is 1.16.4 / 1.16.5, the 755 pack (pack_format 7) when the
     * internal server is 1.17, the 756 pack (pack_format 7) when the
     * internal server is 1.17.1, the 757 pack (pack_format 8) when the
     * internal server is 1.18 / 1.18.1, the 763 pack (pack_format 15) when the
     * internal server is 1.20/1.20.1, the 764 pack (pack_format 18) when
     * the internal server is 1.20.2, the 766 pack (pack_format 32) when it
     * is 1.20.5 or 1.20.6, the 767 pack (pack_format 34) when it is
     * 1.21 or 1.21.1, the 768 pack (pack_format 42) when it is
     * 1.21.2 or 1.21.3, the 769 pack (pack_format 46) when it is
     * 1.21.4, the 770 pack (pack_format 55) when it is
     * 1.21.5, the 771 pack (pack_format 63) when it is
     * 1.21.6, the 772 pack (pack_format 64) when it is
     * 1.21.7／1.21.8, the 773 pack (pack_format 69) when it is
     * 1.21.9／1.21.10, the 774 pack (pack_format 75) when it is
     * 1.21.11, the 775 pack (pack_format 84) when it is
     * 26.1 / 26.1.1 / 26.1.2, the 765 pack (pack_format 22) otherwise.
     * Native clients reach the vanilla pack untouched (relay mode), so it
     * must match the internal version; translated clients get the proxy's
     * per-client substitution instead. Falls back to the 765 pack when no
     * 735 / 736 / 751 / 753 / 754 / 755 / 756 / 757 / 763 / 764 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775 pack was prepared.
     *
     * <p>Package-private for tests.
     */
    ResourcePack hostResourcePack() {
        if (serverProtocol == ProtocolVersion.V1_7_2 && resourcePack4 != null) {
            return resourcePack4;
        }
        if (serverProtocol == ProtocolVersion.V1_7_2 && resourcePack5 != null) {
            return resourcePack5;
        }
        if (serverProtocol == ProtocolVersion.V1_7_2 && resourcePack47 != null) {
            return resourcePack47;
        }
        if (serverProtocol == ProtocolVersion.V1_7_10 && resourcePack5 != null) {
            return resourcePack5;
        }
        if (serverProtocol == ProtocolVersion.V1_7_10 && resourcePack47 != null) {
            return resourcePack47;
        }
        if (serverProtocol == ProtocolVersion.V1_8 && resourcePack47 != null) {
            return resourcePack47;
        }
        if (serverProtocol == ProtocolVersion.V1_8 && resourcePack107 != null) {
            return resourcePack107;
        }
        if (serverProtocol == ProtocolVersion.V1_9 && resourcePack107 != null) {
            return resourcePack107;
        }
        if (serverProtocol == ProtocolVersion.V1_9 && resourcePack108 != null) {
            return resourcePack108;
        }
        if (serverProtocol == ProtocolVersion.V1_9 && resourcePack109 != null) {
            return resourcePack109;
        }
        if (serverProtocol == ProtocolVersion.V1_9 && resourcePack110 != null) {
            return resourcePack110;
        }
        if (serverProtocol == ProtocolVersion.V1_9_1 && resourcePack108 != null) {
            return resourcePack108;
        }
        if (serverProtocol == ProtocolVersion.V1_9_1 && resourcePack109 != null) {
            return resourcePack109;
        }
        if (serverProtocol == ProtocolVersion.V1_9_1 && resourcePack110 != null) {
            return resourcePack110;
        }
        if (serverProtocol == ProtocolVersion.V1_9_2 && resourcePack109 != null) {
            return resourcePack109;
        }
        if (serverProtocol == ProtocolVersion.V1_9_2 && resourcePack110 != null) {
            return resourcePack110;
        }
        if (serverProtocol == ProtocolVersion.V1_9_4 && resourcePack110 != null) {
            return resourcePack110;
        }
        if (serverProtocol == ProtocolVersion.V1_9_4 && resourcePack210 != null) {
            return resourcePack210;
        }
        if (serverProtocol == ProtocolVersion.V1_10_2 && resourcePack210 != null) {
            return resourcePack210;
        }
        if (serverProtocol == ProtocolVersion.V1_10_2 && resourcePack315 != null) {
            return resourcePack315;
        }
        if (serverProtocol == ProtocolVersion.V1_11_2 && resourcePack316 != null) {
            return resourcePack316;
        }
        if (serverProtocol == ProtocolVersion.V1_12 && resourcePack335 != null) {
            return resourcePack335;
        }
        if (serverProtocol == ProtocolVersion.V1_12_1 && resourcePack338 != null) {
            return resourcePack338;
        }
        if (serverProtocol == ProtocolVersion.V1_12_2 && resourcePack340 != null) {
            return resourcePack340;
        }
        if (serverProtocol == ProtocolVersion.V1_13 && resourcePack393 != null) {
            return resourcePack393;
        }
        if (serverProtocol == ProtocolVersion.V1_13_1 && resourcePack401 != null) {
            return resourcePack401;
        }
        if (serverProtocol == ProtocolVersion.V1_13_2 && resourcePack404 != null) {
            return resourcePack404;
        }
        if (serverProtocol == ProtocolVersion.V1_14 && resourcePack477 != null) {
            return resourcePack477;
        }
        if (serverProtocol == ProtocolVersion.V1_14_1 && resourcePack480 != null) {
            return resourcePack480;
        }
        if (serverProtocol == ProtocolVersion.V1_14_2 && resourcePack485 != null) {
            return resourcePack485;
        }
        if (serverProtocol == ProtocolVersion.V1_14_3 && resourcePack490 != null) {
            return resourcePack490;
        }
        if (serverProtocol == ProtocolVersion.V1_14_4 && resourcePack498 != null) {
            return resourcePack498;
        }
        if (serverProtocol == ProtocolVersion.V1_15 && resourcePack573 != null) {
            return resourcePack573;
        }
        if (serverProtocol == ProtocolVersion.V1_15_1 && resourcePack575 != null) {
            return resourcePack575;
        }
        if (serverProtocol == ProtocolVersion.V1_15_2 && resourcePack578 != null) {
            return resourcePack578;
        }
        if (serverProtocol == ProtocolVersion.V1_18_2 && resourcePack758 != null) {
            return resourcePack758;
        }
        if (serverProtocol == ProtocolVersion.V1_16 && resourcePack735 != null) {
            return resourcePack735;
        }
        if (serverProtocol == ProtocolVersion.V1_16_1 && resourcePack736 != null) {
            return resourcePack736;
        }
        if (serverProtocol == ProtocolVersion.V1_16_2 && resourcePack751 != null) {
            return resourcePack751;
        }
        if (serverProtocol == ProtocolVersion.V1_16_3 && resourcePack753 != null) {
            return resourcePack753;
        }
        if (serverProtocol == ProtocolVersion.V1_16_5 && resourcePack754 != null) {
            return resourcePack754;
        }
        if (serverProtocol == ProtocolVersion.V1_17 && resourcePack755 != null) {
            return resourcePack755;
        }
        if (serverProtocol == ProtocolVersion.V1_17_1 && resourcePack756 != null) {
            return resourcePack756;
        }
        if (serverProtocol == ProtocolVersion.V1_18 && resourcePack757 != null) {
            return resourcePack757;
        }
        if (serverProtocol == ProtocolVersion.V1_19 && resourcePack759 != null) {
            return resourcePack759;
        }
        if (serverProtocol == ProtocolVersion.V1_19_1 && resourcePack760 != null) {
            return resourcePack760;
        }
        if (serverProtocol == ProtocolVersion.V1_19_3 && resourcePack761 != null) {
            return resourcePack761;
        }
        if (serverProtocol == ProtocolVersion.V1_19_4 && resourcePack762 != null) {
            return resourcePack762;
        }
        if (serverProtocol == ProtocolVersion.V1_20_1 && resourcePack763 != null) {
            return resourcePack763;
        }
        if (serverProtocol == ProtocolVersion.V1_20_2 && resourcePack764 != null) {
            return resourcePack764;
        }
        if (serverProtocol == ProtocolVersion.V1_20_5 && resourcePack766 != null) {
            return resourcePack766;
        }
        if (serverProtocol == ProtocolVersion.V1_21 && resourcePack767 != null) {
            return resourcePack767;
        }
        if (serverProtocol == ProtocolVersion.V1_21_2 && resourcePack768 != null) {
            return resourcePack768;
        }
        if (serverProtocol == ProtocolVersion.V1_21_4 && resourcePack769 != null) {
            return resourcePack769;
        }
        if (serverProtocol == ProtocolVersion.V1_21_5 && resourcePack770 != null) {
            return resourcePack770;
        }
        if (serverProtocol == ProtocolVersion.V1_21_6 && resourcePack771 != null) {
            return resourcePack771;
        }
        if (serverProtocol == ProtocolVersion.V1_21_7 && resourcePack772 != null) {
            return resourcePack772;
        }
        if (serverProtocol == ProtocolVersion.V1_21_9 && resourcePack773 != null) {
            return resourcePack773;
        }
        if (serverProtocol == ProtocolVersion.V1_21_11 && resourcePack774 != null) {
            return resourcePack774;
        }
        if (serverProtocol == ProtocolVersion.V26_1 && resourcePack775 != null) {
            return resourcePack775;
        }
        if (serverProtocol == ProtocolVersion.V26_2 && resourcePack776 != null) {
            return resourcePack776;
        }
        return resourcePack;
    }

    /**
     * Builds the client proxy for this server: the internal protocol
     * (756 for 1.17.1, 757 for 1.18 / 1.18.1, 763 for 1.20/1.20.1, 764 for 1.20.2, 765 for 1.20.3/1.20.4,
     * 766 for 1.20.5/1.20.6, 767 for 1.21/1.21.1, 768 for 1.21.2/1.21.3,
     *     769 for 1.21.4, 770 for 1.21.5, 771 for 1.21.6, 772 for 1.21.7／1.21.8,
     *     773 for 1.21.9／1.21.10, 774 for 1.21.11, 775 for 26.1 / 26.1.1 / 26.1.2)
     * and the per-client packs are passed through, so the proxy
     * translates toward the running vanilla version instead of
     * assuming 765. The 765 pack is only passed when the internal
     * server is 1.20/1.20.1 or 1.20.2 — with an internal 765 server the vanilla pack
     * goes through untouched, so {@code null} keeps that path exactly
     * as before. The 766 pack is passed unless the internal server is
     * 1.20.5/1.20.6, for the same reason (native 766 clients relay
     * the vanilla pack untouched). The 767 pack is passed unless the
     * internal server is 1.21/1.21.1, for the same reason (native 767
     * clients relay the vanilla pack untouched). The 768 pack is passed
     * unless the internal server is 1.21.2/1.21.3, for the same reason
     * (native 768 clients relay the vanilla pack untouched). The 769 pack is passed
     * unless the internal server is 1.21.4, for the same reason
     * (native 769 clients relay the vanilla pack untouched). The 770 pack is passed
     * unless the internal server is 1.21.5, for the same reason
     * (native 770 clients relay the vanilla pack untouched). The 771 pack is passed
     * unless the internal server is 1.21.6, for the same reason
     * (native 771 clients relay the vanilla pack untouched). The 772 pack is passed
     * unless the internal server is 1.21.7, for the same reason
     * (native 772 clients relay the vanilla pack untouched). The 773 pack is passed
     * unless the internal server is 1.21.9, for the same reason
     * (native 773 clients relay the vanilla pack untouched). The 774 pack is passed
     * unless the internal server is 1.21.11, for the same reason
     * (native 774 clients relay the vanilla pack untouched). The 775 pack is passed
     * unless the internal server is 26.1, for the same reason
     * (native 775 clients relay the vanilla pack untouched). The 758 pack is passed
     * unless the internal server is 1.18.2, for the same reason
     * (native 758 clients relay the vanilla pack untouched).
     * (native 775 clients relay the vanilla pack untouched). The 757 pack is passed
     * unless the internal server is 1.18 / 1.18.1, for the same reason
     * (native 757 clients relay the vanilla pack untouched). The 756 pack is passed
     * unless the internal server is 1.17.1, for the same reason
     * (native 756 clients relay the vanilla pack untouched). The 755 pack is passed
     * unless the internal server is 1.17, for the same reason
     * (native 755 clients relay the vanilla pack untouched). The 754 pack is passed
     * unless the internal server is 1.16.4 / 1.16.5, for the same reason
     * (native 754 clients relay the vanilla pack untouched). The 753 pack is passed
     * unless the internal server is 1.16.3, for the same reason
     * (native 753 clients relay the vanilla pack untouched). The 751 pack is passed
     * unless the internal server is 1.16.2, for the same reason
     * (native 751 clients relay the vanilla pack untouched). The 736 pack is passed
     * unless the internal server is 1.16.1, for the same reason
     * (native 736 clients relay the vanilla pack untouched). The 735 pack is passed
     * unless the internal server is 1.16, for the same reason
     * (native 735 clients relay the vanilla pack untouched).
     *
     * <p>Package-private for tests (the wiring tests call this directly
     * without starting a vanilla server).
     */
    ClientProxy createClientProxy(int port, int internalPort, boolean onlineMode,
            ProxyAuthConfig authConfig) {
        ResourcePack packFor765Clients =
                (serverProtocol == ProtocolVersion.V1_20_1
                        || serverProtocol == ProtocolVersion.V1_20_2)
                        ? resourcePack : null;
        ResourcePack packFor758Clients =
                serverProtocol == ProtocolVersion.V1_18_2 ? null : resourcePack758;
        ResourcePack packFor759Clients =
                serverProtocol == ProtocolVersion.V1_19 ? null : resourcePack759;
        ResourcePack packFor760Clients =
                serverProtocol == ProtocolVersion.V1_19_1 ? null : resourcePack760;
        ResourcePack packFor761Clients =
                serverProtocol == ProtocolVersion.V1_19_3 ? null : resourcePack761;
        ResourcePack packFor762Clients =
                serverProtocol == ProtocolVersion.V1_19_4 ? null : resourcePack762;
        ResourcePack packFor766Clients =
                serverProtocol == ProtocolVersion.V1_20_5 ? null : resourcePack766;
        ResourcePack packFor767Clients =
                serverProtocol == ProtocolVersion.V1_21 ? null : resourcePack767;
        ResourcePack packFor768Clients =
                serverProtocol == ProtocolVersion.V1_21_2 ? null : resourcePack768;
        ResourcePack packFor769Clients =
                serverProtocol == ProtocolVersion.V1_21_4 ? null : resourcePack769;
        ResourcePack packFor770Clients =
                serverProtocol == ProtocolVersion.V1_21_5 ? null : resourcePack770;
        ResourcePack packFor771Clients =
                serverProtocol == ProtocolVersion.V1_21_6 ? null : resourcePack771;
        ResourcePack packFor772Clients =
                serverProtocol == ProtocolVersion.V1_21_7 ? null : resourcePack772;
        ResourcePack packFor773Clients =
                serverProtocol == ProtocolVersion.V1_21_9 ? null : resourcePack773;
        ResourcePack packFor774Clients =
                serverProtocol == ProtocolVersion.V1_21_11 ? null : resourcePack774;
        ResourcePack packFor775Clients =
                serverProtocol == ProtocolVersion.V26_1 ? null : resourcePack775;
        ResourcePack packFor776Clients =
                serverProtocol == ProtocolVersion.V26_2 ? null : resourcePack776;
        ResourcePack packFor757Clients =
                serverProtocol == ProtocolVersion.V1_18 ? null : resourcePack757;
        ResourcePack packFor756Clients =
                serverProtocol == ProtocolVersion.V1_17_1 ? null : resourcePack756;
        ResourcePack packFor755Clients =
                serverProtocol == ProtocolVersion.V1_17 ? null : resourcePack755;
        ResourcePack packFor754Clients =
                serverProtocol == ProtocolVersion.V1_16_5 ? null : resourcePack754;
        ResourcePack packFor753Clients =
                serverProtocol == ProtocolVersion.V1_16_3 ? null : resourcePack753;
        ResourcePack packFor751Clients =
                serverProtocol == ProtocolVersion.V1_16_2 ? null : resourcePack751;
        ResourcePack packFor736Clients =
                serverProtocol == ProtocolVersion.V1_16_1 ? null : resourcePack736;
        ResourcePack packFor735Clients =
                serverProtocol == ProtocolVersion.V1_16 ? null : resourcePack735;
        ClientProxy proxy = new ClientProxy(port, "127.0.0.1", internalPort, onlineMode,
                packFor735Clients, packFor736Clients, packFor751Clients, packFor753Clients, packFor754Clients, packFor755Clients, packFor756Clients, packFor757Clients,
                packFor758Clients, packFor759Clients, packFor760Clients, packFor761Clients, packFor762Clients, resourcePack763, resourcePack764, authConfig,
                packFor765Clients, packFor766Clients, packFor767Clients,
                packFor768Clients, packFor769Clients, packFor770Clients, packFor771Clients,
                packFor772Clients, packFor773Clients, packFor774Clients, packFor775Clients, packFor776Clients, serverProtocol);
        // 578 pack is passed unless the internal server is 1.15.2
        // (native 578 clients relay the vanilla pack untouched).
        proxy.setPack47(serverProtocol == ProtocolVersion.V1_8
                ? null : resourcePack47);
        proxy.setPack5(serverProtocol == ProtocolVersion.V1_7_10
                ? null : resourcePack5);
        proxy.setPack4(serverProtocol == ProtocolVersion.V1_7_2
                ? null : resourcePack4);
        proxy.setPack107(serverProtocol == ProtocolVersion.V1_9
                ? null : resourcePack107);
        proxy.setPack108(serverProtocol == ProtocolVersion.V1_9
                ? null : resourcePack108);
        proxy.setPack109(serverProtocol == ProtocolVersion.V1_9
                ? null : resourcePack109);
        proxy.setPack110(serverProtocol == ProtocolVersion.V1_9
                ? null : resourcePack110);
        proxy.setPack210(serverProtocol == ProtocolVersion.V1_9_4
                ? null : resourcePack210);
        proxy.setPack315(serverProtocol == ProtocolVersion.V1_10_2
                ? null : resourcePack315);
        proxy.setPack316(serverProtocol == ProtocolVersion.V1_11_2
                ? null : resourcePack316);
        proxy.setPack335(serverProtocol == ProtocolVersion.V1_12
                ? null : resourcePack335);
        proxy.setPack338(serverProtocol == ProtocolVersion.V1_12_1
                ? null : resourcePack338);
        proxy.setPack340(serverProtocol == ProtocolVersion.V1_12_2
                ? null : resourcePack340);
        proxy.setPack393(serverProtocol == ProtocolVersion.V1_13
                ? null : resourcePack393);
        proxy.setPack401(serverProtocol == ProtocolVersion.V1_13_1
                ? null : resourcePack401);
        proxy.setPack404(serverProtocol == ProtocolVersion.V1_13_2
                ? null : resourcePack404);
        proxy.setPack477(serverProtocol == ProtocolVersion.V1_14
                ? null : resourcePack477);
        proxy.setPack480(serverProtocol == ProtocolVersion.V1_14_1
                ? null : resourcePack480);
        proxy.setPack485(serverProtocol == ProtocolVersion.V1_14_2
                ? null : resourcePack485);
        proxy.setPack490(serverProtocol == ProtocolVersion.V1_14_3
                ? null : resourcePack490);
        proxy.setPack498(serverProtocol == ProtocolVersion.V1_14_4
                ? null : resourcePack498);
        proxy.setPack573(serverProtocol == ProtocolVersion.V1_15
                ? null : resourcePack573);
        proxy.setPack575(serverProtocol == ProtocolVersion.V1_15_1
                ? null : resourcePack575);
        proxy.setPack578(serverProtocol == ProtocolVersion.V1_15_2
                ? null : resourcePack578);
        // Surfactant forwarding (batch 4): when forwarding.secret is set,
        // logins must arrive through Surfactant with a verified forwarding
        // payload; the forwarded identity (UUID, name, IP, properties)
        // becomes the identity DetergentMC's layer uses for the player.
        String forwardingSecret = config.getString("forwarding.secret", "");
        if (forwardingSecret != null && !forwardingSecret.isBlank()) {
            proxy.setForwardingSecret(forwardingSecret);
            LOG.info(() -> "Surfactant forwarding is enabled (forwarding.secret): "
                    + "only connections verified through Surfactant can log in.");
        }
        return proxy;
    }

    /**
     * Prepares the resource pack to offer to clients, from the
     * {@code resource-pack} section of {@code detergentmc.yml}, and
     * returns it — or {@code null} when no pack is offered.
     *
     * <p>Two modes (see {@link io.detergentmc.protocol.ResourcePackMode}):
     *
     * <ul>
     *   <li><b>Loopback (default, {@code resource-pack.url} empty):</b>
     *       builds the demo pack and starts its loopback HTTP server.
     *       A build or bind failure is logged and yields {@code null},
     *       so the server keeps running without a pack — unless the
     *       pack is required, in which case this method throws and the
     *       server does not start.</li>
     *   <li><b>External ({@code resource-pack.url} is an
     *       {@code https://} URL):</b> no local HTTP server is started;
     *       the configured URL and SHA-1 are returned as-is, to be
     *       written into {@code server.properties} by the vanilla host.
     *       The configured SHA-1 is only checked for shape (40 hex
     *       digits): DetergentMC never downloads the hosted file and
     *       never compares it against the locally generated pack (the
     *       hosted file may legitimately differ). If the SHA-1 is
     *       missing or malformed, a warning is logged and no pack is
     *       offered — and when the pack is required, this method throws
     *       instead, so the server does not start with a pack it
     *       promised to require.</li>
     * </ul>
     *
     * <p>A non-HTTPS URL (including {@code http://}) is refused with a
     * warning and falls back to loopback delivery.
     *
     * <p>Both modes also prepare a 1.17 variant of the pack
     * ({@code pack_format} 7), a 1.17.1 variant of the pack
     * ({@code pack_format} 7), a 1.18 / 1.18.1 variant of the pack
     * ({@code pack_format} 8, one pack for both versions), a 1.19.4 variant of the pack
     * ({@code pack_format} 13), a 1.20.1 variant ({@code pack_format} 15),
     * a 1.20.2 variant ({@code pack_format}
     * 18), a 1.20.5 / 1.20.6 variant ({@code pack_format} 32, one
     * pack for both versions), a 1.21 / 1.21.1 variant
     * ({@code pack_format} 34, one pack for both versions), a
     * 1.21.2 / 1.21.3 variant ({@code pack_format} 42, one pack for
     * both versions), a 1.21.4 variant ({@code pack_format} 46), a
     * 1.21.5 variant ({@code pack_format} 55), a 1.21.6 variant
     * ({@code pack_format} 63), a 1.21.7／1.21.8 variant
     * ({@code pack_format} 64), a 1.21.9／1.21.10 variant
     * ({@code pack_format} 69), a 1.21.11 variant
     * ({@code pack_format} 75) and a 26.1 variant
     * ({@code pack_format} 84, one pack for 26.1 / 26.1.1 / 26.1.2):
     * in loopback mode they are generated and served
     * alongside the 1.20.4 pack by the same HTTP server (see
     * {@link #resourcePack761()}, {@link #resourcePack762()}, {@link #resourcePack763()},
     * {@link #resourcePack764()},
     * {@link #resourcePack766()}, {@link #resourcePack767()},
     * {@link #resourcePack768()}, {@link #resourcePack769()},
     * {@link #resourcePack770()}, {@link #resourcePack771()},
     * {@link #resourcePack772()} and {@link #resourcePack773()});
     * in external mode they come from
     * {@code resource-pack.url-757} / {@code resource-pack.sha1-757},
     * {@code resource-pack.url-761} / {@code resource-pack.sha1-761},
     * {@code resource-pack.url-762} / {@code resource-pack.sha1-762},
     * {@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767},
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768},
     * {@code resource-pack.url-769} / {@code resource-pack.sha1-769},
     * {@code resource-pack.url-770} / {@code resource-pack.sha1-770},
     * {@code resource-pack.url-771} / {@code resource-pack.sha1-771},
     * {@code resource-pack.url-772} / {@code resource-pack.sha1-772},
     * {@code resource-pack.url-773} / {@code resource-pack.sha1-773},
     * {@code resource-pack.url-774} / {@code resource-pack.sha1-774},
     * {@code resource-pack.url-775} / {@code resource-pack.sha1-775},
     * {@code resource-pack.url-776} / {@code resource-pack.sha1-776},
     * {@code resource-pack.url-756} / {@code resource-pack.sha1-756} and
     * {@code resource-pack.url-755} / {@code resource-pack.sha1-755}
     * when both are valid. The proxy offers the 1.17 pack to 755
     * clients, the 1.17.1 pack to 756
     * clients, the 1.18 / 1.18.1 pack to 757
     * clients, the 1.19.3 pack to 761
     * clients, the 1.19.4 pack to 762
     * clients, the 1.20.1 pack to 763
     * clients, the 1.20.2 pack to 764 clients, the 1.20.5 / 1.20.6 pack
     * to 766 clients, the 1.21 / 1.21.1 pack to 767 clients, the
     * 1.21.2 / 1.21.3 pack to 768 clients, the 1.21.4 pack to 769
     * clients, the 1.21.5 pack to 770 clients, the 1.21.6 pack to 771
     * clients, the 1.21.7／1.21.8 pack to 772 clients, the 1.21.9／1.21.10
     * pack to 773 clients, the 1.21.11 pack to 774 clients, the 26.1 pack
     * to 775 clients, the 26.2 pack to 776 clients, and the 1.20.4 pack to 765
     * clients. The pack written into {@code server.properties} is chosen by
     * {@link #hostResourcePack()}: the 755 pack when the vanilla server
     * itself is 1.17, the 756 pack when the vanilla server
     * itself is 1.17.1, the 757 pack when the vanilla server
     * itself is 1.18 / 1.18.1, the 761 pack when the vanilla server
     * itself is 1.19.3, the 762 pack when the vanilla server
     * itself is 1.19.4, the 764 pack when the vanilla server
     * itself is 1.20.2, the 766 pack when it is 1.20.5 or 1.20.6, the
     * 767 pack when it is 1.21 or 1.21.1, the 768 pack when it is
     * 1.21.2 or 1.21.3, the 769 pack when it is 1.21.4, the 770 pack
     * when it is 1.21.5, the 771 pack when it is 1.21.6, the 772 pack
     * when it is 1.21.7／1.21.8, the 773 pack when it is 1.21.9／1.21.10,
     * the 774 pack when it is 1.21.11, the 775 pack when it is 26.1,
     * the 765 pack
     * otherwise.
     *
     * <p>Package-private for tests (the wiring tests call this directly
     * without starting a vanilla server).
     *
     * @throws IOException when the pack is required
     *         ({@code resource-pack.required: true}) but cannot be
     *         prepared, so the caller must abort the startup
     */
    ResourcePack startResourcePack() throws IOException {
        if (!config.getBoolean("resource-pack.enabled", true)) {
            LOG.info(() -> translator.translate("resource-pack.disabled"));
            return null;
        }
        boolean required = config.getBoolean("resource-pack.required", false);
        String promptKey = config.getString("resource-pack.prompt-key", "");
        io.detergentmc.protocol.ResourcePackMode.Decision decision =
                io.detergentmc.protocol.ResourcePackMode.decide(
                        config.getString("resource-pack.url", ""),
                        config.getString("resource-pack.sha1", ""),
                        config.getString("resource-pack.url-761", ""),
                        config.getString("resource-pack.sha1-761", ""),
                        config.getString("resource-pack.url-762", ""),
                        config.getString("resource-pack.sha1-762", ""),
                        config.getString("resource-pack.url-763", ""),
                        config.getString("resource-pack.sha1-763", ""),
                        config.getString("resource-pack.url-764", ""),
                        config.getString("resource-pack.sha1-764", ""),
                        config.getString("resource-pack.url-766", ""),
                        config.getString("resource-pack.sha1-766", ""),
                        config.getString("resource-pack.url-767", ""),
                        config.getString("resource-pack.sha1-767", ""),
                        config.getString("resource-pack.url-768", ""),
                        config.getString("resource-pack.sha1-768", ""),
                        config.getString("resource-pack.url-769", ""),
                        config.getString("resource-pack.sha1-769", ""),
                        config.getString("resource-pack.url-770", ""),
                        config.getString("resource-pack.sha1-770", ""),
                        config.getString("resource-pack.url-771", ""),
                        config.getString("resource-pack.sha1-771", ""),
                        config.getString("resource-pack.url-772", ""),
                        config.getString("resource-pack.sha1-772", ""),
                        config.getString("resource-pack.url-773", ""),
                        config.getString("resource-pack.sha1-773", ""),
                        config.getString("resource-pack.url-774", ""),
                        config.getString("resource-pack.sha1-774", ""),
                        config.getString("resource-pack.url-775", ""),
                        config.getString("resource-pack.sha1-775", ""),
                        config.getString("resource-pack.url-776", ""),
                        config.getString("resource-pack.sha1-776", ""),
                        config.getString("resource-pack.url-760", ""),
                        config.getString("resource-pack.sha1-760", ""),
                        config.getString("resource-pack.url-759", ""),
                        config.getString("resource-pack.sha1-759", ""),
                        config.getString("resource-pack.url-758", ""),
                        config.getString("resource-pack.sha1-758", ""),
                        config.getString("resource-pack.url-578", ""),
                        config.getString("resource-pack.sha1-578", ""),
                        config.getString("resource-pack.url-575", ""),
                        config.getString("resource-pack.sha1-575", ""),
                        config.getString("resource-pack.url-573", ""),
                        config.getString("resource-pack.sha1-573", ""),
                        config.getString("resource-pack.url-498", ""),
                        config.getString("resource-pack.sha1-498", ""),
                        config.getString("resource-pack.url-490", ""),
                        config.getString("resource-pack.sha1-490", ""),
                        config.getString("resource-pack.url-485", ""),
                        config.getString("resource-pack.sha1-485", ""),
                        config.getString("resource-pack.url-480", ""),
                        config.getString("resource-pack.sha1-480", ""),
                        config.getString("resource-pack.url-477", ""),
                        config.getString("resource-pack.sha1-477", ""),
                        config.getString("resource-pack.url-404", ""),
                        config.getString("resource-pack.sha1-404", ""),
                        config.getString("resource-pack.url-401", ""),
                        config.getString("resource-pack.sha1-401", ""),
                        config.getString("resource-pack.url-393", ""),
                        config.getString("resource-pack.sha1-393", ""),
                        config.getString("resource-pack.url-340", ""),
                        config.getString("resource-pack.sha1-340", ""),
                        config.getString("resource-pack.url-338", ""),
                        config.getString("resource-pack.sha1-338", ""),
                        config.getString("resource-pack.url-335", ""),
                        config.getString("resource-pack.sha1-335", ""),
                        config.getString("resource-pack.url-316", ""),
                        config.getString("resource-pack.sha1-316", ""),
                        config.getString("resource-pack.url-315", ""),
                        config.getString("resource-pack.sha1-315", ""),
                        config.getString("resource-pack.url-210", ""),
                        config.getString("resource-pack.sha1-210", ""),
                        config.getString("resource-pack.url-110", ""),
                        config.getString("resource-pack.sha1-110", ""),
                        config.getString("resource-pack.url-109", ""),
                        config.getString("resource-pack.sha1-109", ""),
                        config.getString("resource-pack.url-108", ""),
                        config.getString("resource-pack.sha1-108", ""),
                        config.getString("resource-pack.url-107", ""),
                        config.getString("resource-pack.sha1-107", ""),
                        config.getString("resource-pack.url-47", ""),
                        config.getString("resource-pack.sha1-47", ""),
                        config.getString("resource-pack.url-5", ""),
                        config.getString("resource-pack.sha1-5", ""),
                        config.getString("resource-pack.url-4", ""),
                        config.getString("resource-pack.sha1-4", ""),
                        config.getString("resource-pack.url-757", ""),
                        config.getString("resource-pack.sha1-757", ""),
                        config.getString("resource-pack.url-756", ""),
                        config.getString("resource-pack.sha1-756", ""),
                        config.getString("resource-pack.url-755", ""),
                        config.getString("resource-pack.sha1-755", ""),
                        config.getString("resource-pack.url-754", ""),
                        config.getString("resource-pack.sha1-754", ""),
                        config.getString("resource-pack.url-753", ""),
                        config.getString("resource-pack.sha1-753", ""),
                        config.getString("resource-pack.url-751", ""),
                        config.getString("resource-pack.sha1-751", ""),
                        config.getString("resource-pack.url-736", ""),
                        config.getString("resource-pack.sha1-736", ""),
                        config.getString("resource-pack.url-735", ""),
                        config.getString("resource-pack.sha1-735", ""),
                        required, promptKey);
        if (decision.problem() != null) {
            LOG.warning(() -> translator.translate(
                    "resource-pack.config-warning", decision.problem()));
        }
        switch (decision.kind()) {
            case EXTERNAL -> {
                if (decision.pack() == null) {
                    // https:// URL but the SHA-1 is missing or malformed:
                    // no pack can be offered.
                    if (required) {
                        throw new IOException(translator.translate(
                                "resource-pack.required-failed", decision.problem()));
                    }
                    resourcePack = null;
                    resourcePack47 = null;
                    resourcePack5 = null;
                    resourcePack4 = null;
                    resourcePack107 = null;
                    resourcePack108 = null;
                    resourcePack109 = null;
                    resourcePack110 = null;
                    resourcePack210 = null;
                    resourcePack315 = null;
                    resourcePack316 = null;
                    resourcePack335 = null;
                    resourcePack338 = null;
                    resourcePack340 = null;
                    resourcePack393 = null;
                    resourcePack401 = null;
                    resourcePack404 = null;
                    resourcePack477 = null;
                    resourcePack480 = null;
                    resourcePack485 = null;
                    resourcePack490 = null;
                    resourcePack498 = null;
                    resourcePack573 = null;
                    resourcePack575 = null;
                    resourcePack578 = null;
                    resourcePack758 = null;
                    resourcePack735 = null;
                    resourcePack736 = null;
                    resourcePack751 = null;
                    resourcePack753 = null;
                    resourcePack754 = null;
                    resourcePack755 = null;
                    resourcePack756 = null;
                    resourcePack757 = null;
                    resourcePack759 = null;
                    resourcePack760 = null;
                    resourcePack761 = null;
                    resourcePack762 = null;
                    resourcePack763 = null;
                    resourcePack764 = null;
                    resourcePack766 = null;
                    resourcePack767 = null;
                    resourcePack768 = null;
                    resourcePack769 = null;
                    resourcePack770 = null;
                    resourcePack771 = null;
                    resourcePack772 = null;
                    resourcePack773 = null;
                    resourcePack774 = null;
                    resourcePack775 = null;
                    resourcePack776 = null;
                    return null;
                }
                LOG.info(() -> translator.translate(
                        "resource-pack.external-enabled", decision.pack().url()));
                resourcePack = decision.pack();
                resourcePack477 = decision.pack477();
                if (resourcePack477 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack477.url()));
                }
                resourcePack393 = decision.pack393();
                if (resourcePack393 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack393.url()));
                }
                resourcePack47 = decision.pack47();
                if (resourcePack47 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack47.url()));
                }
                resourcePack5 = decision.pack5();
                if (resourcePack5 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack5.url()));
                }
                resourcePack4 = decision.pack4();
                if (resourcePack4 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack4.url()));
                }
                resourcePack107 = decision.pack107();
                if (resourcePack107 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack107.url()));
                }
                resourcePack108 = decision.pack108();
                if (resourcePack108 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack108.url()));
                }
                resourcePack109 = decision.pack109();
                if (resourcePack109 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack109.url()));
                }
                resourcePack110 = decision.pack110();
                if (resourcePack110 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack110.url()));
                }
                resourcePack210 = decision.pack210();
                if (resourcePack210 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack210.url()));
                }
                resourcePack315 = decision.pack315();
                if (resourcePack315 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack315.url()));
                }
                resourcePack316 = decision.pack316();
                if (resourcePack316 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack316.url()));
                }
                resourcePack335 = decision.pack335();
                if (resourcePack335 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack335.url()));
                }
                resourcePack338 = decision.pack338();
                if (resourcePack338 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack338.url()));
                }
                resourcePack340 = decision.pack340();
                if (resourcePack340 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack340.url()));
                }
                resourcePack401 = decision.pack401();
                if (resourcePack401 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack401.url()));
                }
                resourcePack404 = decision.pack404();
                if (resourcePack404 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack404.url()));
                }
                resourcePack480 = decision.pack480();
                if (resourcePack480 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack480.url()));
                }
                resourcePack485 = decision.pack485();
                if (resourcePack485 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack485.url()));
                }
                resourcePack490 = decision.pack490();
                if (resourcePack490 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack490.url()));
                }
                resourcePack498 = decision.pack498();
                if (resourcePack498 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack498.url()));
                }
                resourcePack573 = decision.pack573();
                if (resourcePack573 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack573.url()));
                }
                resourcePack575 = decision.pack575();
                if (resourcePack575 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack575.url()));
                }
                resourcePack578 = decision.pack578();
                if (resourcePack578 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack578.url()));
                }
                resourcePack758 = decision.pack758();
                if (resourcePack758 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack758.url()));
                }
                resourcePack735 = decision.pack735();
                if (resourcePack735 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack735.url()));
                }
                resourcePack736 = decision.pack736();
                if (resourcePack736 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack736.url()));
                }
                resourcePack751 = decision.pack751();
                if (resourcePack751 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack751.url()));
                }
                resourcePack753 = decision.pack753();
                if (resourcePack753 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack753.url()));
                }
                resourcePack754 = decision.pack754();
                if (resourcePack754 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack754.url()));
                }
                resourcePack755 = decision.pack755();
                if (resourcePack755 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack755.url()));
                }
                resourcePack756 = decision.pack756();
                if (resourcePack756 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack756.url()));
                }
                resourcePack757 = decision.pack757();
                if (resourcePack757 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack757.url()));
                }
                resourcePack759 = decision.pack759();
                if (resourcePack759 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack759.url()));
                }
                resourcePack760 = decision.pack760();
                if (resourcePack760 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack760.url()));
                }
                resourcePack761 = decision.pack761();
                if (resourcePack761 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack761.url()));
                }
                resourcePack762 = decision.pack762();
                if (resourcePack762 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack762.url()));
                }
                resourcePack763 = decision.pack763();
                if (resourcePack763 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack763.url()));
                }
                resourcePack764 = decision.pack764();
                if (resourcePack764 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack764.url()));
                }
                resourcePack766 = decision.pack766();
                if (resourcePack766 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack766.url()));
                }
                resourcePack767 = decision.pack767();
                if (resourcePack767 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack767.url()));
                }
                resourcePack768 = decision.pack768();
                if (resourcePack768 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack768.url()));
                }
                resourcePack769 = decision.pack769();
                if (resourcePack769 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack769.url()));
                }
                resourcePack770 = decision.pack770();
                if (resourcePack770 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack770.url()));
                }
                resourcePack771 = decision.pack771();
                if (resourcePack771 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack771.url()));
                }
                resourcePack772 = decision.pack772();
                if (resourcePack772 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack772.url()));
                }
                resourcePack773 = decision.pack773();
                if (resourcePack773 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack773.url()));
                }
                resourcePack774 = decision.pack774();
                if (resourcePack774 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack774.url()));
                }
                resourcePack775 = decision.pack775();
                if (resourcePack775 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack775.url()));
                }
                resourcePack776 = decision.pack776();
                if (resourcePack776 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack776.url()));
                }
                return resourcePack;
            }
            case LOOPBACK -> {
                // Fall through to the local build below.
            }
        }
        int port = config.getInt("resource-pack.port", 25567);
        try {
            // 1.16用（pack_format 5）と1.16.1用（pack_format 5）と1.16.2用（pack_format 6）と1.16.3用（pack_format 6）と1.16.4／1.16.5用（pack_format 6、1パックで両バージョン共用）と1.20.4用（pack_format 22）と1.17用（pack_format 7）、1.17.1用（pack_format 7）、1.18／1.18.1用（pack_format 8、1パックで両バージョン共用）と1.18.2用（pack_format 8）、
            // 1.19用（pack_format 9）、
            // 1.19.1／1.19.2用（pack_format 9）、
            // 1.19.3用（pack_format 12）、1.19.4用（pack_format 13）、
            // 1.20.1用（pack_format 15）、1.20.2用（pack_format 18）、
            // 1.20.5／1.20.6用（pack_format 32、1パックで両バージョン共用）、
            // 1.21／1.21.1用（pack_format 34、1パックで両バージョン共用）、
            // 1.21.2／1.21.3用（pack_format 42、1パックで両バージョン共用）、
            // 1.21.4用（pack_format 46）、1.21.5用（pack_format 55）、
            // 1.21.6用（pack_format 63）、1.21.7／1.21.8用（pack_format 64）、
            // 1.21.9／1.21.10用（pack_format 69）、1.21.11用（pack_format 75）、
            // 765用（pack_format 22）を本命として、735〜776と
            // 降下側（578〜4）の各クライアント用パックを生成し、
            // 同じHTTPサーバー（同じ25567番ポート）で配信する。
            // 出し分けは ResourcePackServer.packForProtocol が行う。
            ResourcePackBuilder.BuiltPack built = new ResourcePackBuilder()
                    .build(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built107 = new ResourcePackBuilder()
                    .build107(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built47 = new ResourcePackBuilder()
                    .build47(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built5 = new ResourcePackBuilder()
                    .build5(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built4 = new ResourcePackBuilder()
                    .build4(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built108 = new ResourcePackBuilder()
                    .build108(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built109 = new ResourcePackBuilder()
                    .build109(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built110 = new ResourcePackBuilder()
                    .build110(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built210 = new ResourcePackBuilder()
                    .build210(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built315 = new ResourcePackBuilder()
                    .build315(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built316 = new ResourcePackBuilder()
                    .build316(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built335 = new ResourcePackBuilder()
                    .build335(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built338 = new ResourcePackBuilder()
                    .build338(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built340 = new ResourcePackBuilder()
                    .build340(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built393 = new ResourcePackBuilder()
                    .build393(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built401 = new ResourcePackBuilder()
                    .build401(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built404 = new ResourcePackBuilder()
                    .build404(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built477 = new ResourcePackBuilder()
                    .build477(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built480 = new ResourcePackBuilder()
                    .build480(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built485 = new ResourcePackBuilder()
                    .build485(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built490 = new ResourcePackBuilder()
                    .build490(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built498 = new ResourcePackBuilder()
                    .build498(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built573 = new ResourcePackBuilder()
                    .build573(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built575 = new ResourcePackBuilder()
                    .build575(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built578 = new ResourcePackBuilder()
                    .build578(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built758 = new ResourcePackBuilder()
                    .build758(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built735 = new ResourcePackBuilder()
                    .build735(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built736 = new ResourcePackBuilder()
                    .build736(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built751 = new ResourcePackBuilder()
                    .build751(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built753 = new ResourcePackBuilder()
                    .build753(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built754 = new ResourcePackBuilder()
                    .build754(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built755 = new ResourcePackBuilder()
                    .build755(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built756 = new ResourcePackBuilder()
                    .build756(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built757 = new ResourcePackBuilder()
                    .build757(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built759 = new ResourcePackBuilder()
                    .build759(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built760 = new ResourcePackBuilder()
                    .build760(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built761 = new ResourcePackBuilder()
                    .build761(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built762 = new ResourcePackBuilder()
                    .build762(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built763 = new ResourcePackBuilder()
                    .build763(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built764 = new ResourcePackBuilder()
                    .build764(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built766 = new ResourcePackBuilder()
                    .build766(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built767 = new ResourcePackBuilder()
                    .build767(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built768 = new ResourcePackBuilder()
                    .build768(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built769 = new ResourcePackBuilder()
                    .build769(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built770 = new ResourcePackBuilder()
                    .build770(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built771 = new ResourcePackBuilder()
                    .build771(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built772 = new ResourcePackBuilder()
                    .build772(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built773 = new ResourcePackBuilder()
                    .build773(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built774 = new ResourcePackBuilder()
                    .build774(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built775 = new ResourcePackBuilder()
                    .build775(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built776 = new ResourcePackBuilder()
                    .build776(workingDir.resolve("data").resolve("resource-pack"));
            resourcePackServer = new ResourcePackServer(
                    built, built735, built736, built751, built753, built754, built755, built756, built757, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769, built770, built771, built772, built773, built774, built775, built776, built578, built575, built573, built498, built490, built485, built480, built477, built404, built401, built393, built340, built338, built335, built316, built315, built210, built110, built109, built108, built107, built47, built5, built4, port, required,
                    promptKey.isBlank()
                            ? ResourcePackServer.DEFAULT_PROMPT_KEY : promptKey.trim());
            resourcePack = resourcePackServer.start();
            resourcePack47 = resourcePackServer.pack47();
            resourcePack5 = resourcePackServer.pack5();
            resourcePack4 = resourcePackServer.pack4();
            resourcePack107 = resourcePackServer.pack107();
            resourcePack108 = resourcePackServer.pack108();
            resourcePack109 = resourcePackServer.pack109();
            resourcePack110 = resourcePackServer.pack110();
            resourcePack210 = resourcePackServer.pack210();
            resourcePack315 = resourcePackServer.pack315();
            resourcePack316 = resourcePackServer.pack316();
            resourcePack335 = resourcePackServer.pack335();
            resourcePack338 = resourcePackServer.pack338();
            resourcePack340 = resourcePackServer.pack340();
            resourcePack393 = resourcePackServer.pack393();
            resourcePack401 = resourcePackServer.pack401();
            resourcePack404 = resourcePackServer.pack404();
            resourcePack477 = resourcePackServer.pack477();
            resourcePack480 = resourcePackServer.pack480();
            resourcePack485 = resourcePackServer.pack485();
            resourcePack490 = resourcePackServer.pack490();
            resourcePack498 = resourcePackServer.pack498();
            resourcePack573 = resourcePackServer.pack573();
            resourcePack575 = resourcePackServer.pack575();
            resourcePack578 = resourcePackServer.pack578();
            resourcePack758 = resourcePackServer.pack758();
            resourcePack735 = resourcePackServer.pack735();
            resourcePack736 = resourcePackServer.pack736();
            resourcePack751 = resourcePackServer.pack751();
            resourcePack753 = resourcePackServer.pack753();
            resourcePack754 = resourcePackServer.pack754();
            resourcePack755 = resourcePackServer.pack755();
            resourcePack756 = resourcePackServer.pack756();
            resourcePack757 = resourcePackServer.pack757();
            resourcePack759 = resourcePackServer.pack759();
            resourcePack760 = resourcePackServer.pack760();
            resourcePack761 = resourcePackServer.pack761();
            resourcePack762 = resourcePackServer.pack762();
            resourcePack763 = resourcePackServer.pack763();
            resourcePack764 = resourcePackServer.pack764();
            resourcePack766 = resourcePackServer.pack766();
            resourcePack767 = resourcePackServer.pack767();
            resourcePack768 = resourcePackServer.pack768();
            resourcePack769 = resourcePackServer.pack769();
            resourcePack770 = resourcePackServer.pack770();
            resourcePack771 = resourcePackServer.pack771();
            resourcePack772 = resourcePackServer.pack772();
            resourcePack773 = resourcePackServer.pack773();
            resourcePack774 = resourcePackServer.pack774();
            resourcePack775 = resourcePackServer.pack775();
            resourcePack776 = resourcePackServer.pack776();
            LOG.info(() -> translator.translate("resource-pack.enabled", resourcePack.url()));
            return resourcePack;
        } catch (IOException e) {
            LOG.warning(() -> translator.translate("resource-pack.failed", e.getMessage()));
            stopResourcePack();
            resourcePack = null;
            if (required) {
                throw new IOException(translator.translate(
                        "resource-pack.required-failed", e.getMessage()), e);
            }
            return null;
        }
    }

    /**
     * Builds the DetergentMC datapack into
     * {@code <workdir>/vanilla/world/datapacks/detergentmc.zip} and
     * returns the file, or {@code null} when generation failed. A
     * failure is logged and the server keeps running without the
     * {@code /trigger} bridge, the same way a resource-pack failure is
     * handled.
     *
     * <p>Package-private for tests (the wiring test calls this directly
     * without starting a vanilla server).
     */
    Path generateDatapack() {
        try {
            // The datapack format follows the running vanilla version:
            // 9 for 1.18.2, 10 for 1.19 / 1.19.1 / 1.19.2 / 1.19.3, 12 for 1.19.4,
            // 15 for 1.20.1, 18 for 1.20.2, 26 for 1.20.4,
            // 41 for 1.20.5 / 1.20.6, 48 for 1.21 / 1.21.1,
            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4, 71 for 1.21.5, 80 for 1.21.6, 81 for 1.21.7／1.21.8, 88 for 1.21.9／1.21.10, 94 for 1.21.11, 101 for 26.1 / 26.1.1 / 26.1.2, 107 for 26.2.
            int packFormat = DatapackGenerator.packFormatFor(
                    config.getString("minecraft-version", "1.20.4"));
            Path file = new DatapackGenerator()
                    .build(workingDir.resolve("vanilla"), packFormat);
            LOG.info(() -> translator.translate("datapack.generated", file.toString()));
            return file;
        } catch (IOException e) {
            LOG.warning(() -> translator.translate("datapack.failed", e.getMessage()));
            return null;
        }
    }

    /** Stops the resource-pack HTTP server, if one is running. */
    void stopResourcePack() {
        if (resourcePackServer != null) {
            resourcePackServer.stop();
            resourcePackServer = null;
        }
        resourcePack = null;
        resourcePack47 = null;
        resourcePack5 = null;
        resourcePack4 = null;
        resourcePack107 = null;
        resourcePack108 = null;
        resourcePack109 = null;
        resourcePack110 = null;
        resourcePack210 = null;
        resourcePack315 = null;
        resourcePack316 = null;
        resourcePack335 = null;
        resourcePack338 = null;
        resourcePack340 = null;
        resourcePack393 = null;
        resourcePack401 = null;
        resourcePack404 = null;
        resourcePack477 = null;
        resourcePack480 = null;
        resourcePack485 = null;
        resourcePack490 = null;
        resourcePack498 = null;
        resourcePack573 = null;
        resourcePack575 = null;
        resourcePack578 = null;
        resourcePack758 = null;
        resourcePack735 = null;
        resourcePack736 = null;
        resourcePack751 = null;
        resourcePack753 = null;
        resourcePack754 = null;
        resourcePack755 = null;
        resourcePack756 = null;
        resourcePack757 = null;
        resourcePack759 = null;
        resourcePack760 = null;
        resourcePack761 = null;
        resourcePack762 = null;
        resourcePack763 = null;
        resourcePack764 = null;
        resourcePack766 = null;
        resourcePack767 = null;
        resourcePack768 = null;
        resourcePack769 = null;
        resourcePack770 = null;
        resourcePack771 = null;
        resourcePack772 = null;
        resourcePack773 = null;
        resourcePack774 = null;
        resourcePack775 = null;
        resourcePack776 = null;
    }

    /**
     * Bedrock リスナー（Geyser 風＋Floodgate 風）の設定を
     * {@code detergentmc.yml} から作る。
     *
     * <p>Package-private for tests.
     */
    BedrockConfig bedrockConfig(int internalPort) {
        return new BedrockConfig(
                config.getBoolean("bedrock.enabled", true),
                config.getInt("bedrock.port", BedrockProtocol.DEFAULT_PORT),
                config.getString("bedrock.motd",
                        config.getString("server.motd", "DetergentMC 1.20.4")),
                config.getString("floodgate.prefix", "."),
                config.getInt("max-players", 20),
                config.getString("server-name", "DetergentMC"),
                "127.0.0.1", internalPort, serverProtocol,
                config.getInt("bedrock.chunk-radius", BedrockConfig.DEFAULT_CHUNK_RADIUS));
    }

    /**
     * Bedrock リスナーを Java プロキシと並行して起動する。バインド失敗時は
     * 警告ログにしてサーバー全体は落とさない（Bedrock は任意機能のため）。
     */
    private void startBedrock(int internalPort) {
        BedrockConfig bedrock = bedrockConfig(internalPort);
        if (!bedrock.enabled()) {
            LOG.info("Bedrock リスナーは無効です（bedrock.enabled: false）。");
            return;
        }
        try {
            bedrockServer = new BedrockServer(bedrock);
            bedrockServer.start();
        } catch (IOException e) {
            LOG.warning("Bedrock リスナーの起動に失敗しました（Java 版は動いています）: "
                    + e.getMessage());
            bedrockServer = null;
        }
    }

    /** The Bedrock (RakNet UDP) server, or {@code null} when not running. */
    public BedrockServer bedrockServer() {
        return bedrockServer;
    }

    /**
     * レガシーフロントエンド（Netty 以前世代）の設定を
     * {@code detergentmc.yml} から作る。内部サーバー自体がレガシー世代
     * （1.6.4）の場合はダイレクトモード（フロントエンドが内部バニラへ
     * レガシークライアントとして直接接続）、それ以外はブリッジモード
     * （フロントエンドがコアプロキシへプロトコル5の合成クライアント
     * として接続）。
     *
     * <p>Package-private for tests.
     */
    LegacyFrontendConfig legacyConfig(int publicPort, int internalPort) {
        String minecraftVersion = config.getString("minecraft-version", "1.20.4");
        String motd = config.getString("legacy.motd", "");
        if (motd.isBlank()) {
            motd = config.getString("server.motd", "DetergentMC 1.20.4");
        }
        int maxPlayers = config.getInt("max-players", 20);
        int legacyPort = config.getInt("legacy.port", LegacyFrontendConfig.DEFAULT_PORT);
        if (LegacyProtocol.isLegacyBackendVersion(minecraftVersion)) {
            return LegacyFrontendConfig.direct(
                    legacyPort, "127.0.0.1", internalPort, motd, maxPlayers,
                    minecraftVersion);
        }
        return LegacyFrontendConfig.bridge(
                legacyPort, "127.0.0.1", publicPort, motd, maxPlayers);
    }

    /**
     * レガシーフロントエンドを起動する。バインド失敗時は警告ログにして
     * サーバー全体は落とさない（Bedrock と同じ扱い）。
     */
    private void startLegacyFrontend(LegacyFrontendConfig legacy) {
        try {
            legacyServer = new LegacyFrontendServer(legacy);
            legacyServer.start();
        } catch (IOException e) {
            LOG.warning("レガシーフロントエンドの起動に失敗しました: " + e.getMessage());
            legacyServer = null;
        }
    }

    /** The legacy (pre-Netty) frontend server, or {@code null} when not running. */
    public LegacyFrontendServer legacyServer() {
        return legacyServer;
    }

    /** The resource pack currently offered to clients, or {@code null}. */
    public ResourcePack resourcePack() {
        return resourcePack;
    }

    /** The 1.8.9 resource pack offered to 47 clients, or {@code null}. */
    public ResourcePack resourcePack47() {
        return resourcePack47;
    }

    /** The 1.7.10 resource pack offered to 5 clients, or {@code null}. */
    public ResourcePack resourcePack5() {
        return resourcePack5;
    }

    /** The 1.7.2 resource pack offered to 4 clients, or {@code null}. */
    public ResourcePack resourcePack4() {
        return resourcePack4;
    }

    /** The 1.9 resource pack offered to 107 clients, or {@code null}. */
    public ResourcePack resourcePack107() {
        return resourcePack107;
    }

    /** The 1.9.1 resource pack offered to 108 clients, or {@code null}. */
    public ResourcePack resourcePack108() {
        return resourcePack108;
    }

    /** The 1.9.2 resource pack offered to 109 clients, or {@code null}. */
    public ResourcePack resourcePack109() {
        return resourcePack109;
    }

    /** The 1.9.4 resource pack offered to 110 clients, or {@code null}. */
    public ResourcePack resourcePack110() {
        return resourcePack110;
    }

    /** The 1.10.2 resource pack offered to 210 clients, or {@code null}. */
    public ResourcePack resourcePack210() {
        return resourcePack210;
    }

    /** The 1.11 resource pack offered to 315 clients, or {@code null}. */
    public ResourcePack resourcePack315() {
        return resourcePack315;
    }

    /** The 1.11.2 resource pack offered to 316 clients, or {@code null}. */
    public ResourcePack resourcePack316() {
        return resourcePack316;
    }

    /** The 1.12 resource pack offered to 335 clients, or {@code null}. */
    public ResourcePack resourcePack335() {
        return resourcePack335;
    }

    /** The 1.12.1 resource pack offered to 338 clients, or {@code null}. */
    public ResourcePack resourcePack338() {
        return resourcePack338;
    }

    /** The 1.12.2 resource pack offered to 340 clients, or {@code null}. */
    public ResourcePack resourcePack340() {
        return resourcePack340;
    }

    /** The 1.13 resource pack offered to 393 clients, or {@code null}. */
    public ResourcePack resourcePack393() {
        return resourcePack393;
    }

    /** The 1.13.1 resource pack offered to 401 clients, or {@code null}. */
    public ResourcePack resourcePack401() {
        return resourcePack401;
    }

    /** The 1.13.2 resource pack offered to 404 clients, or {@code null}. */
    public ResourcePack resourcePack404() {
        return resourcePack404;
    }

    /** The 1.14 resource pack offered to 477 clients, or {@code null}. */
    public ResourcePack resourcePack477() {
        return resourcePack477;
    }

    /** The 1.14.1 resource pack offered to 480 clients, or {@code null}. */
    public ResourcePack resourcePack480() {
        return resourcePack480;
    }

    /** The 1.14.2 resource pack offered to 485 clients, or {@code null}. */
    public ResourcePack resourcePack485() {
        return resourcePack485;
    }

    /** The 1.14.3 resource pack offered to 490 clients, or {@code null}. */
    public ResourcePack resourcePack490() {
        return resourcePack490;
    }

    /** The 1.14.4 resource pack offered to 498 clients, or {@code null}. */
    public ResourcePack resourcePack498() {
        return resourcePack498;
    }

    /** The 1.15 resource pack offered to 573 clients, or {@code null}. */
    public ResourcePack resourcePack573() {
        return resourcePack573;
    }

    /** The 1.15.1 resource pack offered to 575 clients, or {@code null}. */
    public ResourcePack resourcePack575() {
        return resourcePack575;
    }

    /** The 1.15.2 resource pack offered to 578 clients, or {@code null}. */
    public ResourcePack resourcePack578() {
        return resourcePack578;
    }

    /** The 1.18.2 resource pack offered to 758 clients, or {@code null}. */
    public ResourcePack resourcePack758() {
        return resourcePack758;
    }

    /** The 1.19 resource pack offered to 759 clients, or {@code null}. */
    public ResourcePack resourcePack759() {
        return resourcePack759;
    }

    /** The 1.19.1 / 1.19.2 resource pack offered to 760 clients, or {@code null}. */
    public ResourcePack resourcePack760() {
        return resourcePack760;
    }

    /** The 1.19.3 resource pack offered to 761 clients, or {@code null}. */
    public ResourcePack resourcePack761() {
        return resourcePack761;
    }

    /** The 1.19.4 resource pack offered to 762 clients, or {@code null}. */
    public ResourcePack resourcePack762() {
        return resourcePack762;
    }

    /** The 1.20.1 resource pack offered to 763 clients, or {@code null}. */
    public ResourcePack resourcePack763() {
        return resourcePack763;
    }

    /** The 1.20.2 resource pack offered to 764 clients, or {@code null}. */
    public ResourcePack resourcePack764() {
        return resourcePack764;
    }

    /**
     * The 1.20.5 / 1.20.6 resource pack offered to 766 clients, or
     * {@code null}. One pack serves both versions (protocol 766,
     * pack_format 32).
     */
    public ResourcePack resourcePack766() {
        return resourcePack766;
    }

    /**
     * The 1.21 / 1.21.1 resource pack offered to 767 clients, or
     * {@code null}. One pack serves both versions (protocol 767,
     * pack_format 34).
     */
    public ResourcePack resourcePack767() {
        return resourcePack767;
    }

    /**
     * The 1.21.2 / 1.21.3 resource pack offered to 768 clients, or
     * {@code null}. One pack serves both versions (protocol 768,
     * pack_format 42).
     */
    public ResourcePack resourcePack768() {
        return resourcePack768;
    }

    /**
     * The 1.21.4 resource pack offered to 769 clients, or
     * {@code null} (protocol 769, pack_format 46).
     */
    public ResourcePack resourcePack769() {
        return resourcePack769;
    }

    /**
     * The 1.21.5 resource pack offered to 770 clients, or
     * {@code null} (protocol 770, pack_format 55).
     */
    public ResourcePack resourcePack770() {
        return resourcePack770;
    }

    /**
     * The 1.21.6 resource pack offered to 771 clients, or
     * {@code null} (protocol 771, pack_format 63).
     */
    public ResourcePack resourcePack771() {
        return resourcePack771;
    }

    /**
     * The 1.21.7 resource pack offered to 772 clients, or
     * {@code null} (protocol 772, pack_format 64).
     */
    public ResourcePack resourcePack772() {
        return resourcePack772;
    }

    /**
     * The 1.21.9 resource pack offered to 773 clients, or
     * {@code null} (protocol 773, pack_format 69).
     */
    public ResourcePack resourcePack773() {
        return resourcePack773;
    }

    /** The resource-pack HTTP server, or {@code null} when not running. */
    public ResourcePackServer resourcePackServer() {
        return resourcePackServer;
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        events.fire(new ServerStopEvent(server));
        // Stop the proxy first (no new players), then the vanilla server.
        if (clientProxy != null) {
            try {
                clientProxy.stop();
            } catch (RuntimeException e) {
                LOG.warning("Failed to stop the client proxy: " + e.getMessage());
            }
            clientProxy = null;
        }
        // Stop the Bedrock listener too.
        if (bedrockServer != null) {
            try {
                bedrockServer.stop();
            } catch (RuntimeException e) {
                LOG.warning("Failed to stop the Bedrock server: " + e.getMessage());
            }
            bedrockServer = null;
        }
        // Stop the legacy frontend too.
        if (legacyServer != null) {
            try {
                legacyServer.stop();
            } catch (RuntimeException e) {
                LOG.warning("Failed to stop the legacy frontend: " + e.getMessage());
            }
            legacyServer = null;
        }
        // Stop the vanilla server first, then disable the Bubbles.
        if (vanillaHost != null) {
            try {
                vanillaHost.stop();
            } catch (RuntimeException e) {
                LOG.warning("Failed to stop the vanilla server: " + e.getMessage());
            }
            vanillaHost = null;
        }
        // The pack is only useful while the vanilla server runs.
        stopResourcePack();
        bubbleLoader.disableAll();
        LOG.info(() -> translator.translate("server.stopped"));
    }

    public boolean isRunning() {
        return running;
    }

    public YamlConfig config() {
        return config;
    }

    /**
     * The protocol number the internal (vanilla) server speaks, decided
     * from {@code minecraft-version} at construction time (763 for
     * 1.20 / 1.20.1, 764 for 1.20.2, 765 for 1.20.3 / 1.20.4, 766 for
     * 1.20.5 / 1.20.6, 767 for 1.21 / 1.21.1, 765 for unknown — see
     * {@link ProtocolVersion#serverProtocolFor(String)}). The proxy
     * translates <em>toward</em> this protocol, so the translation
     * direction follows this value.
     */
    public int serverProtocol() {
        return serverProtocol;
    }

    public LocaleManager translator() {
        return translator;
    }

    /** The event bus shared by the server and all Bubbles. */
    public SimpleEventBus events() {
        return events;
    }

    /** The command registry shared by the server and all Bubbles. */
    public SimpleCommandRegistry commands() {
        return commands;
    }

    public CoreServer server() {
        return server;
    }

    public BubbleLoader bubbleLoader() {
        return bubbleLoader;
    }

    /** The vanilla server host, or {@code null} before it is started. */
    public VanillaServerHost vanillaHost() {
        return vanillaHost;
    }

    /** The client proxy, or {@code null} before it is started. */
    public ClientProxy clientProxy() {
        return clientProxy;
    }

    /** The gameplay bridge, or {@code null} before the vanilla server is started. */
    public GameBridge gameBridge() {
        return gameBridge;
    }

    /** The online-player registry, or {@code null} before the vanilla server is started. */
    public PlayerRegistry playerRegistry() {
        return playerRegistry;
    }

    private void consoleLoop() {
        CommandSender console = new CommandSender() {
            @Override
            public String name() {
                return "Console";
            }

            @Override
            public Locale locale() {
                return translator.defaultLocale();
            }

            @Override
            public void sendMessage(String message) {
                System.out.println(message);
            }

            @Override
            public void sendMessage(String key, Object... args) {
                System.out.println(translator.translate(key, locale(), args));
            }
        };
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                String input = line.trim();
                if (input.equalsIgnoreCase("stop")) {
                    stop();
                } else if (input.equalsIgnoreCase("bubbles")) {
                    if (bubbleLoader.loaded().isEmpty()) {
                        System.out.println(translator.translate("command.bubbles-empty"));
                    } else {
                        bubbleLoader.loaded().forEach(handle -> System.out.println(
                                translator.translate("command.bubbles-line",
                                        handle.info().id(), handle.info().version())));
                    }
                } else if (input.equalsIgnoreCase("help") || input.isEmpty()) {
                    System.out.println(translator.translate("server.console-help"));
                } else if (input.equalsIgnoreCase("vanilla")) {
                    System.out.println(translator.translate("vanilla.usage"));
                } else if (input.toLowerCase(Locale.ROOT).startsWith("vanilla ")) {
                    String vanillaCommand = input.substring("vanilla ".length()).trim();
                    if (vanillaCommand.isEmpty()) {
                        System.out.println(translator.translate("vanilla.usage"));
                    } else if (vanillaHost == null || !vanillaHost.isRunning()) {
                        System.out.println(translator.translate("vanilla.not-running"));
                    } else {
                        try {
                            vanillaHost.sendCommand(vanillaCommand);
                        } catch (RuntimeException e) {
                            System.out.println(translator.translate("vanilla.failed", e.getMessage()));
                        }
                    }
                } else if (!commands.dispatch(console, input)) {
                    System.out.println(translator.translate("command.unknown", input));
                }
            }
        } catch (IOException e) {
            LOG.warning("Console input failed: " + e.getMessage());
        }
    }
}
