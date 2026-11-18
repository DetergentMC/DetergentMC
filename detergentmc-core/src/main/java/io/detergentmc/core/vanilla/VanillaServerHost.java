package io.detergentmc.core.vanilla;

import io.detergentmc.protocol.ResourcePack;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Hosts the real vanilla server as a child process.
 *
 * <p>On {@link #start()} it downloads the vanilla jar for the configured
 * Minecraft version (first start only; a jar whose SHA-1 already matches is
 * reused), writes {@code server.properties} and {@code eula.txt} into the
 * vanilla directory, then launches
 * {@code java -Xmx<memoryMb>M -jar server-<version>.jar nogui} there.
 * The world, chunks, blocks and player movement are all vanilla's job;
 * DetergentMC is only the host and the bridge.
 *
 * <p>The child's output is read on a daemon thread, re-logged with a
 * {@code [vanilla] } prefix, parsed by {@link VanillaLogParser}, and
 * forwarded to every registered {@link VanillaLogListener}. The gameplay
 * bridge (a later milestone) will listen here to turn vanilla log events
 * into DetergentMC API events.
 *
 * <p>The vanilla jar is downloaded from Mojang and executed as-is. It is
 * never unpacked or modified, and it is never committed to the repository.
 */
public final class VanillaServerHost {

    private static final Logger LOG = Logger.getLogger(VanillaServerHost.class.getName());
    private static final int DEFAULT_MEMORY_MB = 1024;
    private static final int STOP_TIMEOUT_SECONDS = 20;

    private final Path vanillaDir;
    private final String minecraftVersion;
    private final int port;
    private final boolean onlineMode;
    private final int maxPlayers;
    private final String motd;
    private final ResourcePack resourcePack;
    private final int memoryMb;
    private final VanillaDownloader downloader;

    private final List<VanillaLogListener> listeners = new CopyOnWriteArrayList<>();
    private final VanillaLogParser parser = new VanillaLogParser();
    private final CountDownLatch readyLatch = new CountDownLatch(1);

    private volatile Process process;
    private volatile BufferedWriter processInput;
    private volatile Integer exitCode;

    /**
     * Creates a host with the default 1024 MB heap for the vanilla server.
     *
     * @param vanillaDir       directory holding the vanilla jar, world and
     *                         properties (for example {@code <workdir>/vanilla})
     * @param minecraftVersion e.g. {@code "1.20.4"}
     * @param port             port the vanilla server listens on
     * @param onlineMode       public online-mode (the proxy enforces Mojang
     *                         authentication when true). The internal vanilla
     *                         server itself always runs offline-mode=false
     *                         as a trusted backend (see
     *                         {@link #writeServerProperties})
     * @param maxPlayers       vanilla {@code max-players}
     * @param motd             vanilla {@code motd}
     * @param resourcePack     resource pack to offer, or {@code null} for none
     */
    public VanillaServerHost(Path vanillaDir, String minecraftVersion, int port,
            boolean onlineMode, int maxPlayers, String motd, ResourcePack resourcePack) {
        this(vanillaDir, minecraftVersion, port, onlineMode, maxPlayers, motd,
                resourcePack, DEFAULT_MEMORY_MB, new VanillaDownloader());
    }

    /** Same as the main constructor, but with an explicit heap size in MB. */
    public VanillaServerHost(Path vanillaDir, String minecraftVersion, int port,
            boolean onlineMode, int maxPlayers, String motd, ResourcePack resourcePack,
            int memoryMb) {
        this(vanillaDir, minecraftVersion, port, onlineMode, maxPlayers, motd,
                resourcePack, memoryMb, new VanillaDownloader());
    }

    /** Test-only constructor with an explicit downloader (e.g. a local HTTP server). */
    VanillaServerHost(Path vanillaDir, String minecraftVersion, int port,
            boolean onlineMode, int maxPlayers, String motd, ResourcePack resourcePack,
            int memoryMb, VanillaDownloader downloader) {
        if (vanillaDir == null) {
            throw new IllegalArgumentException("vanillaDir is required");
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("minecraftVersion is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        if (memoryMb < 256) {
            throw new IllegalArgumentException("memoryMb must be at least 256, got " + memoryMb);
        }
        this.vanillaDir = vanillaDir;
        this.minecraftVersion = minecraftVersion;
        this.port = port;
        this.onlineMode = onlineMode;
        this.maxPlayers = maxPlayers;
        this.motd = motd == null ? "" : motd;
        this.resourcePack = resourcePack;
        this.memoryMb = memoryMb;
        this.downloader = downloader;
    }

    /**
     * Downloads the vanilla jar if needed, writes {@code server.properties}
     * and {@code eula.txt}, and starts the vanilla server process.
     *
     * @throws IOException if the jar cannot be downloaded or verified, or
     *         the process cannot be launched
     */
    public void start() throws IOException {
        if (process != null && process.isAlive()) {
            throw new IllegalStateException("Vanilla server is already running");
        }
        Files.createDirectories(vanillaDir);
        Path jar = downloader.download(vanillaDir, minecraftVersion);
        writeServerProperties(vanillaDir, port, onlineMode, maxPlayers, motd, resourcePack);
        writeEula(vanillaDir);

        String javaBin = javaBinaryFor(minecraftVersion);
        List<String> command = List.of(javaBin,
                "-Xmx" + memoryMb + "M",
                "-jar", jar.getFileName().toString(), "nogui");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(vanillaDir.toFile())
                .redirectErrorStream(true);
        Process started;
        try {
            started = builder.start();
        } catch (IOException e) {
            throw new IOException("Failed to launch the vanilla server ("
                    + String.join(" ", command) + " in " + vanillaDir + "): " + e.getMessage(), e);
        }
        process = started;
        processInput = new BufferedWriter(new OutputStreamWriter(
                started.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(this::readLoop, "vanilla-log-reader");
        reader.setDaemon(true);
        reader.start();
        LOG.info(() -> "Vanilla server process started (Minecraft " + minecraftVersion
                + ", port " + port + ")");
    }

    /**
     * The {@code java} binary used to launch the vanilla server for the
     * given Minecraft version.
     *
     * <p>Minecraft 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3,
     * 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10 and 1.21.11 require Java 21, while DetergentMC itself (and 1.18.2 and 1.19.1–1.20.4)
     * runs on Java 17. When the Gradle process runs on Java 17,
     * launching a 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3,
     * 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10 or 1.21.11 vanilla server with the current {@code java.home} would fail at
     * once ("requires Java 21"). For those versions this method
     * therefore prefers a Java 21 installation, in this order:
     * <ol>
     *   <li>the {@code detergentmc.java21.home} system property,</li>
     *   <li>the {@code DETERGENTMC_JAVA21_HOME} environment variable,</li>
     *   <li>the current {@code java.home} when it is already Java 21 or
     *       newer (e.g. the whole server runs on Java 21),</li>
     *   <li>otherwise the current {@code java.home} as a last resort —
     *       the vanilla server will refuse to start, and the log will
     *       say Java 21 is required (see the warning below).</li>
     * </ol>
     * Minecraft 26.1 / 26.1.1 / 26.1.2 / 26.2 require Java 25. For those versions
     * this method prefers a Java 25 installation, in the same order
     * ({@code detergentmc.java25.home} → {@code DETERGENTMC_JAVA25_HOME}
     * → current {@code java.home} when it is already Java 25 or newer).
     * For 1.18.2 and 1.19.1–1.20.4 the current {@code java.home} is always used.
     *
     * <p>Package-private for tests.
     */
    static String javaBinaryFor(String minecraftVersion) {
        String javaHome = System.getProperty("java.home");
        if ("1.15.2".equals(minecraftVersion) || "1.15.1".equals(minecraftVersion) || "1.15".equals(minecraftVersion) || "1.14.4".equals(minecraftVersion) || "1.14.3".equals(minecraftVersion) || "1.14.2".equals(minecraftVersion) || "1.14.1".equals(minecraftVersion) || "1.14".equals(minecraftVersion) || "1.13.2".equals(minecraftVersion) || "1.13.1".equals(minecraftVersion) || "1.13".equals(minecraftVersion) || "1.12.2".equals(minecraftVersion) || "1.12.1".equals(minecraftVersion) || "1.12".equals(minecraftVersion)) {
            // Minecraft 1.15.x は Java 8 世代（version.json の javaVersion は
            // jre-legacy）。Java 17 以降では起動できないため、Java 8 を探す。
            String java8Home = System.getProperty("detergentmc.java8.home");
            if (java8Home == null || java8Home.isBlank()) {
                java8Home = System.getenv("DETERGENTMC_JAVA8_HOME");
            }
            if (java8Home != null && !java8Home.isBlank()) {
                return Path.of(java8Home.strip(), "bin", "java").toString();
            }
            if (Runtime.version().feature() == 8) {
                return Path.of(javaHome, "bin", "java").toString();
            }
            LOG.warning(() -> "Minecraft " + minecraftVersion + " needs Java 8, but no "
                    + "Java 8 installation was configured (set the "
                    + "detergentmc.java8.home system property or the "
                    + "DETERGENTMC_JAVA8_HOME environment variable). Falling back "
                    + "to the current Java (" + javaHome + "); the vanilla server "
                    + "will refuse to start.");
            return Path.of(javaHome, "bin", "java").toString();
        }
        if ("26.1".equals(minecraftVersion) || "26.1.1".equals(minecraftVersion)
                || "26.1.2".equals(minecraftVersion) || "26.2".equals(minecraftVersion)) {
            String java25Home = System.getProperty("detergentmc.java25.home");
            if (java25Home == null || java25Home.isBlank()) {
                java25Home = System.getenv("DETERGENTMC_JAVA25_HOME");
            }
            if (java25Home != null && !java25Home.isBlank()) {
                return Path.of(java25Home.strip(), "bin", "java").toString();
            }
            if (Runtime.version().feature() >= 25) {
                return Path.of(javaHome, "bin", "java").toString();
            }
            LOG.warning(() -> "Minecraft " + minecraftVersion + " needs Java 25, but no "
                    + "Java 25 installation was configured (set the "
                    + "detergentmc.java25.home system property or the "
                    + "DETERGENTMC_JAVA25_HOME environment variable). Falling back "
                    + "to the current Java (" + javaHome + "); the vanilla server "
                    + "will refuse to start.");
            return Path.of(javaHome, "bin", "java").toString();
        }
        if (!"1.20.5".equals(minecraftVersion) && !"1.20.6".equals(minecraftVersion)
                && !"1.21".equals(minecraftVersion) && !"1.21.1".equals(minecraftVersion)
                && !"1.21.2".equals(minecraftVersion) && !"1.21.3".equals(minecraftVersion)
                && !"1.21.4".equals(minecraftVersion) && !"1.21.5".equals(minecraftVersion)
                && !"1.21.6".equals(minecraftVersion) && !"1.21.7".equals(minecraftVersion)
                && !"1.21.8".equals(minecraftVersion) && !"1.21.9".equals(minecraftVersion)
                && !"1.21.10".equals(minecraftVersion)
                && !"1.21.11".equals(minecraftVersion)) {
            return Path.of(javaHome, "bin", "java").toString();
        }
        String java21Home = System.getProperty("detergentmc.java21.home");
        if (java21Home == null || java21Home.isBlank()) {
            java21Home = System.getenv("DETERGENTMC_JAVA21_HOME");
        }
        if (java21Home != null && !java21Home.isBlank()) {
            return Path.of(java21Home.strip(), "bin", "java").toString();
        }
        if (Runtime.version().feature() >= 21) {
            return Path.of(javaHome, "bin", "java").toString();
        }
        LOG.warning(() -> "Minecraft " + minecraftVersion + " needs Java 21, but no "
                + "Java 21 installation was configured (set the "
                + "detergentmc.java21.home system property or the "
                + "DETERGENTMC_JAVA21_HOME environment variable). Falling back "
                + "to the current Java (" + javaHome + "); the vanilla server "
                + "will refuse to start.");
        return Path.of(javaHome, "bin", "java").toString();
    }

    /** Sends one line to the vanilla server console (for example {@code "stop"} or {@code "list"}). */
    public void sendCommand(String vanillaCommand) {
        Process current = process;
        BufferedWriter input = processInput;
        if (current == null || !current.isAlive() || input == null) {
            throw new IllegalStateException("Vanilla server is not running");
        }
        String line = vanillaCommand == null ? "" : vanillaCommand;
        try {
            synchronized (input) {
                input.write(line);
                input.newLine();
                input.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to send a command to the vanilla server: " + line, e);
        }
    }

    /**
     * Stops the vanilla server: sends {@code "stop"}, waits up to 20 seconds
     * for a clean exit, then destroys the process if it is still alive.
     */
    public void stop() {
        Process current = process;
        if (current == null || !current.isAlive()) {
            return;
        }
        try {
            sendCommand("stop");
        } catch (RuntimeException e) {
            LOG.warning("Could not send \"stop\" to the vanilla server: " + e.getMessage());
        }
        try {
            if (!current.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warning("Vanilla server did not stop in " + STOP_TIMEOUT_SECONDS
                        + "s; destroying it");
                current.destroy();
                if (!current.waitFor(5, TimeUnit.SECONDS)) {
                    current.destroyForcibly();
                    // destroyForcibly() は即座に返るため、exitValue() を呼ぶ前に
                    // プロセスの終了を待つ。待たないと負荷時にプロセスがまだ生きていて
                    // IllegalThreadStateException になり、テストが失敗する
                    // （776の再実行で発覚。stop() 自体は775以前から同じ形）。
                    current.waitFor(10, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
            try {
                current.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
            }
        } finally {
            closeQuietly(processInput);
            process = null;
            processInput = null;
        }
        // プロセスがまだ生きている場合は exitValue() を呼ばない
        // （IllegalThreadStateException になるため）。
        LOG.info(() -> "Vanilla server stopped (exit code "
                + (current.isAlive() ? "still running" : current.exitValue()) + ")");
    }

    public boolean isRunning() {
        Process current = process;
        return current != null && current.isAlive();
    }

    /**
     * Waits until the vanilla server prints {@code Done (...)} or the
     * timeout elapses. Returns {@code true} when the server became ready.
     */
    public boolean waitForReady(long timeoutMillis) {
        try {
            return readyLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void addListener(VanillaLogListener listener) {
        listeners.add(listener);
    }

    public Path vanillaDir() {
        return vanillaDir;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public int port() {
        return port;
    }

    /** Exit code of the vanilla process once it has finished, or {@code null} while it runs. */
    public Integer exitCode() {
        return exitCode;
    }

    private void readLoop() {
        Process current = process;
        if (current == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                current.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOG.info("[vanilla] " + line);
                parser.parse(line).ifPresent(event -> {
                    if (event instanceof VanillaLogEvent.ServerReady) {
                        readyLatch.countDown();
                    }
                    for (VanillaLogListener listener : listeners) {
                        try {
                            listener.onLogEvent(event);
                        } catch (RuntimeException e) {
                            LOG.warning("Vanilla log listener failed: " + e.getMessage());
                        }
                    }
                });
            }
        } catch (IOException e) {
            LOG.warning("Failed to read the vanilla server output: " + e.getMessage());
        } finally {
            try {
                exitCode = current.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            LOG.info("[vanilla] process exited with code " + exitCode);
        }
    }

    /**
     * Writes {@code server.properties} into the vanilla directory.
     * Keys DetergentMC manages are (over)written; any other keys already in
     * the file (for example an operator's own tweaks) are kept.
     *
     * <p>The vanilla server always binds to loopback only
     * ({@code server-ip=127.0.0.1}): players connect through the
     * DetergentMC proxy on the public port, never directly to vanilla.
     * Compression is disabled ({@code network-compression-threshold=-1})
     * so the proxy never has to inflate / re-deflate packets while
     * translating them; the proxy↔vanilla hop is loopback, so this
     * costs nothing in practice.
     *
     * <p>【担当1】内部バニラの {@code online-mode} は常に {@code false}
     * （信頼済み接続）。Mojang 認証はプロキシがクライアントとの間で
     * 完結させ、認証済みの接続だけをここへ繋ぐ（Velocity／BungeeCord の
     * 信頼プロキシと同じ考え方）。引数の {@code onlineMode} は公開側の
     * online-mode（プロキシが認証を強制するかどうか）で、内部バニラの
     * 値としては使わない。内部バニラが online-mode のままだと、プロキシ
     * 認証済みの 763 クライアントはバニラの暗号化要求に応じられず
     * 入れない（プロキシがクライアントとの暗号しか知らないため）。
     * 内部バニラへの直接接続は loopback 束縛で防ぐ（公開ポートは
     * プロキシだけ）。
     */
    public static void writeServerProperties(Path vanillaDir, int port, boolean onlineMode,
            int maxPlayers, String motd, ResourcePack resourcePack) throws IOException {
        Files.createDirectories(vanillaDir);
        Path file = vanillaDir.resolve("server.properties");
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStreamReader in = new InputStreamReader(
                    Files.newInputStream(file), StandardCharsets.UTF_8)) {
                properties.load(in);
            }
        }
        properties.setProperty("server-port", String.valueOf(port));
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("network-compression-threshold", "-1");
        // 【担当1】内部バニラは常に offline-mode（信頼済み接続）。
        // 公開側の online-mode（引数）はプロキシが認証で実現する。
        if (onlineMode) {
            LOG.info("Internal vanilla server runs with online-mode=false "
                    + "(trusted backend): Mojang authentication is done by the proxy.");
        }
        properties.setProperty("online-mode", "false");
        properties.setProperty("max-players", String.valueOf(maxPlayers));
        properties.setProperty("motd", motd == null ? "" : motd);
        properties.setProperty("spawn-protection", "0");
        properties.setProperty("enable-command-block", "false");
        if (resourcePack != null) {
            properties.setProperty("resource-pack", resourcePack.url());
            properties.setProperty("resource-pack-sha1", resourcePack.sha1());
        } else {
            // The pack was disabled: remove stale keys so an old URL is
            // not left behind in server.properties.
            properties.remove("resource-pack");
            properties.remove("resource-pack-sha1");
        }
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(out,
                    "DetergentMC-managed server.properties (generated at startup)");
        }
    }

    /**
     * Writes {@code eula=true} into the vanilla directory. DetergentMC's own
     * {@code eula.txt} in the workdir was already checked by {@code Main}
     * before the server started, so the operator has agreed to the EULA.
     */
    public static void writeEula(Path vanillaDir) throws IOException {
        Files.createDirectories(vanillaDir);
        Files.writeString(vanillaDir.resolve("eula.txt"),
                "# By changing the setting below to TRUE you are indicating your agreement to\n"
                        + "# the Minecraft EULA (https://www.minecraft.net/en-us/eula).\n"
                        + "eula=true\n",
                StandardCharsets.UTF_8);
    }

    private static void closeQuietly(BufferedWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
            // The process is going away anyway.
        }
    }
}
