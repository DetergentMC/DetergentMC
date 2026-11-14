package io.detergentmc.core.vanilla;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the real vanilla server jar from Mojang at first start.
 *
 * <p>It follows the public piston-meta chain:
 * {@code version_manifest_v2.json} -&gt; the version JSON for the requested
 * Minecraft version -&gt; {@code downloads.server} (URL + SHA-1).
 * The jar is saved as {@code <vanillaDir>/server-<version>.jar} and its
 * SHA-1 is verified before it is used. When a jar with a matching SHA-1 is
 * already present, it is reused and not downloaded again.
 *
 * <p>Downloads prefer {@code curl} (in this sandbox Java's HTTPS goes
 * through a proxy that fails, while curl works) and fall back to
 * {@link HttpURLConnection} when curl is unavailable or fails. The jar is
 * never part of the repository: it is only ever downloaded at runtime.
 *
 * <p>No Mojang or Paper code is copied here; the jar is downloaded and
 * executed as-is, never unpacked or modified.
 */
public final class VanillaDownloader {

    public static final String DEFAULT_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final Logger LOG = Logger.getLogger(VanillaDownloader.class.getName());
    private static final boolean CURL_AVAILABLE = checkCurl();
    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int READ_TIMEOUT_MILLIS = 120_000;

    /** Where to get the server jar from, and the SHA-1 it must have. */
    public record ServerDownload(String url, String sha1) {
    }

    private final String manifestUrl;

    public VanillaDownloader() {
        this(DEFAULT_MANIFEST_URL);
    }

    /** Creates a downloader reading the manifest from the given URL (used by tests with a local server). */
    public VanillaDownloader(String manifestUrl) {
        if (manifestUrl == null || manifestUrl.isBlank()) {
            throw new IllegalArgumentException("manifestUrl is required");
        }
        this.manifestUrl = manifestUrl;
    }

    /**
     * Resolves the server jar download for a Minecraft version by walking
     * the manifest -&gt; version JSON chain. No file is written.
     */
    public ServerDownload resolve(String minecraftVersion) throws IOException {
        String manifest = fetchText(manifestUrl);
        Matcher id = Pattern.compile("\"id\"\\s*:\\s*\""
                + Pattern.quote(minecraftVersion) + "\"").matcher(manifest);
        if (!id.find()) {
            throw new IOException("Minecraft " + minecraftVersion
                    + " was not found in the Mojang version manifest (" + manifestUrl + ")");
        }
        String versionJsonUrl = extractString(manifest, "url", id.end());
        if (versionJsonUrl == null) {
            throw new IOException("No version JSON URL for Minecraft "
                    + minecraftVersion + " in the manifest");
        }
        String versionJson = fetchText(versionJsonUrl);
        int downloadsIndex = versionJson.indexOf("\"downloads\"");
        if (downloadsIndex < 0) {
            throw new IOException("No \"downloads\" in the version JSON for Minecraft "
                    + minecraftVersion);
        }
        int serverIndex = versionJson.indexOf("\"server\"", downloadsIndex);
        if (serverIndex < 0) {
            throw new IOException("No server download in the version JSON for Minecraft "
                    + minecraftVersion);
        }
        int open = versionJson.indexOf('{', serverIndex);
        int close = versionJson.indexOf('}', open);
        if (open < 0 || close < 0) {
            throw new IOException("Malformed server download in the version JSON for Minecraft "
                    + minecraftVersion);
        }
        String serverObject = versionJson.substring(open, close);
        String sha1 = extractString(serverObject, "sha1", 0);
        String url = extractString(serverObject, "url", 0);
        if (sha1 == null || url == null) {
            throw new IOException("Server download for Minecraft " + minecraftVersion
                    + " has no URL or SHA-1");
        }
        return new ServerDownload(url, sha1);
    }

    /**
     * Downloads (or reuses) the server jar for the given version into
     * {@code <vanillaDir>/server-<version>.jar} and verifies its SHA-1.
     *
     * @return the path of the verified jar
     */
    public Path download(Path vanillaDir, String minecraftVersion) throws IOException {
        Files.createDirectories(vanillaDir);
        ServerDownload serverDownload = resolve(minecraftVersion);
        Path target = vanillaDir.resolve("server-" + minecraftVersion + ".jar");
        if (Files.exists(target) && verifySha1(target, serverDownload.sha1())) {
            LOG.info(() -> "Reusing vanilla server jar (SHA-1 matches): " + target);
            return target;
        }
        Path tmp = vanillaDir.resolve("server-" + minecraftVersion + ".jar.tmp");
        try {
            downloadFile(serverDownload.url(), tmp);
            if (!verifySha1(tmp, serverDownload.sha1())) {
                throw new IOException("SHA-1 mismatch for the vanilla server jar downloaded from "
                        + serverDownload.url() + " (expected " + serverDownload.sha1()
                        + "). The download may be corrupt; delete " + tmp + " and try again.");
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        LOG.info(() -> "Downloaded vanilla server jar: " + target);
        return target;
    }

    /** SHA-1 (lower-case hex) of a file. */
    public static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is guaranteed to exist on every Java platform.
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }

    /** Returns true when the file's SHA-1 matches the expected value (case-insensitive). */
    public static boolean verifySha1(Path file, String expectedSha1) throws IOException {
        if (expectedSha1 == null) {
            return false;
        }
        return sha1(file).equalsIgnoreCase(expectedSha1.trim());
    }

    private static String extractString(String json, String key, int fromIndex) {
        Matcher matcher = Pattern.compile("\""
                + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find(fromIndex) ? matcher.group(1) : null;
    }

    private String fetchText(String url) throws IOException {
        if (CURL_AVAILABLE) {
            try {
                return runCurlText(url);
            } catch (IOException e) {
                LOG.warning("curl failed for " + url + " (" + e.getMessage()
                        + "); falling back to Java HTTPS");
            }
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "DetergentMC/0.1.0");
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private void downloadFile(String url, Path target) throws IOException {
        if (CURL_AVAILABLE) {
            try {
                runCurlDownload(url, target);
                return;
            } catch (IOException e) {
                LOG.warning("curl download failed for " + url + " (" + e.getMessage()
                        + "); falling back to Java HTTPS");
                Files.deleteIfExists(target);
            }
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "DetergentMC/0.1.0");
        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        } finally {
            connection.disconnect();
        }
    }

    private static boolean checkCurl() {
        try {
            return new ProcessBuilder("curl", "--version")
                    .redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String runCurlText(String url) throws IOException {
        Process process = new ProcessBuilder("curl", "-fsSL", url)
                .redirectErrorStream(false)
                .start();
        byte[] stdout;
        byte[] stderr;
        try (InputStream out = process.getInputStream();
             InputStream err = process.getErrorStream()) {
            stdout = out.readAllBytes();
            stderr = err.readAllBytes();
        }
        int exit = waitFor(process);
        if (exit != 0) {
            throw new IOException("curl exited with code " + exit + " for " + url + ": "
                    + new String(stderr, StandardCharsets.UTF_8).trim());
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    private static void runCurlDownload(String url, Path target) throws IOException {
        Process process = new ProcessBuilder(
                "curl", "-fsSL", "--retry", "2", "-o", target.toString(), url)
                .redirectErrorStream(false)
                .start();
        byte[] stderr;
        try (InputStream err = process.getErrorStream()) {
            stderr = err.readAllBytes();
        }
        int exit = waitFor(process);
        if (exit != 0) {
            throw new IOException("curl exited with code " + exit + " for " + url + ": "
                    + new String(stderr, StandardCharsets.UTF_8).trim());
        }
    }

    private static int waitFor(Process process) throws IOException {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for curl", e);
        }
    }
}
