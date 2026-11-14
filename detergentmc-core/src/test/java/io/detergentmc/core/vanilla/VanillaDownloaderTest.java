package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the downloader against a local HTTP server only: the build must
 * never reach the network (no piston-meta, no real jar).
 */
class VanillaDownloaderTest {

    @TempDir
    Path tempDir;

    private static String sha1Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }

    /** A tiny fake Mojang: manifest -> version JSON -> jar bytes. */
    private static final class FakeMojang implements AutoCloseable {
        final HttpServer server;
        final byte[] jarBytes;
        final String jarSha1;
        final AtomicInteger jarRequests = new AtomicInteger();

        FakeMojang(byte[] jarBytes) throws Exception {
            this(jarBytes, "1.20.4");
        }

        FakeMojang(byte[] jarBytes, String minecraftVersion) throws Exception {
            this.jarBytes = jarBytes;
            this.jarSha1 = sha1Hex(jarBytes);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;
            server.createContext("/manifest.json", exchange -> {
                byte[] body = ("{\"versions\":[{\"id\": \"" + minecraftVersion + "\", \"type\": \"release\", "
                        + "\"url\": \"" + base + "/version.json\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                send(exchange, body, "application/json");
            });
            server.createContext("/version.json", exchange -> {
                byte[] body = ("{\"downloads\": {\"server\": {\"sha1\": \"" + jarSha1
                        + "\", \"size\": " + jarBytes.length
                        + ", \"url\": \"" + base + "/server.jar\"}}}")
                        .getBytes(StandardCharsets.UTF_8);
                send(exchange, body, "application/json");
            });
            server.createContext("/server.jar", exchange -> {
                jarRequests.incrementAndGet();
                send(exchange, jarBytes, "application/java-archive");
            });
            server.start();
        }

        String manifestUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/manifest.json";
        }

        private static void send(com.sun.net.httpserver.HttpExchange exchange,
                byte[] body, String contentType) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    @Test
    void downloadsJarAndVerifiesSha1() throws Exception {
        byte[] jarBytes = "not a real jar, just bytes".getBytes(StandardCharsets.UTF_8);
        try (FakeMojang mojang = new FakeMojang(jarBytes)) {
            VanillaDownloader downloader = new VanillaDownloader(mojang.manifestUrl());
            Path jar = downloader.download(tempDir, "1.20.4");
            assertEquals(tempDir.resolve("server-1.20.4.jar"), jar);
            assertEquals(jarBytes.length, Files.size(jar));
            assertTrue(VanillaDownloader.verifySha1(jar, mojang.jarSha1));
            assertEquals(1, mojang.jarRequests.get());
        }
    }

    @Test
    void downloads1201JarAndVerifiesSha1() throws Exception {
        // The downloader is not 1.20.4-fixed: the version is an argument,
        // so 1.20.1 follows the same manifest -> version JSON -> jar
        // chain (and the same SHA-1 check) as 1.20.4.
        byte[] jarBytes = "not a real 1.20.1 jar, just bytes".getBytes(StandardCharsets.UTF_8);
        try (FakeMojang mojang = new FakeMojang(jarBytes, "1.20.1")) {
            VanillaDownloader downloader = new VanillaDownloader(mojang.manifestUrl());
            Path jar = downloader.download(tempDir, "1.20.1");
            assertEquals(tempDir.resolve("server-1.20.1.jar"), jar);
            assertEquals(jarBytes.length, Files.size(jar));
            assertTrue(VanillaDownloader.verifySha1(jar, mojang.jarSha1));
            assertEquals(1, mojang.jarRequests.get());
        }
    }

    @Test
    void reusesJarWhenSha1Matches() throws Exception {
        byte[] jarBytes = "same bytes again".getBytes(StandardCharsets.UTF_8);
        try (FakeMojang mojang = new FakeMojang(jarBytes)) {
            VanillaDownloader downloader = new VanillaDownloader(mojang.manifestUrl());
            downloader.download(tempDir, "1.20.4");
            downloader.download(tempDir, "1.20.4");
            // The jar itself was fetched only once; the manifest was re-read.
            assertEquals(1, mojang.jarRequests.get());
        }
    }

    @Test
    void sha1VerificationRejectsWrongHash() throws Exception {
        Path file = tempDir.resolve("small.txt");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        assertTrue(VanillaDownloader.verifySha1(file,
                sha1Hex("hello".getBytes(StandardCharsets.UTF_8))));
        assertFalse(VanillaDownloader.verifySha1(file,
                "0000000000000000000000000000000000000000"));
        assertFalse(VanillaDownloader.verifySha1(file, null));
    }

    @Test
    void unknownVersionFailsClearly() throws Exception {
        byte[] jarBytes = "x".getBytes(StandardCharsets.UTF_8);
        try (FakeMojang mojang = new FakeMojang(jarBytes)) {
            VanillaDownloader downloader = new VanillaDownloader(mojang.manifestUrl());
            IOException error = assertThrows(IOException.class,
                    () -> downloader.download(tempDir, "9.9.9"));
            assertTrue(error.getMessage().contains("9.9.9"));
        }
    }
}
