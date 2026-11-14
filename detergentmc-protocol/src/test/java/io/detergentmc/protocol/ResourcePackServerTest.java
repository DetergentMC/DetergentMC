package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackServerTest {

    @TempDir
    Path tempDir;

    @Test
    void servesPackOverLocalHttpWithMatchingSha1() throws Exception {
        ResourcePackBuilder.BuiltPack built = new ResourcePackBuilder().build(tempDir);
        // Port 0: any free loopback port (keeps the test hermetic).
        ResourcePackServer server = new ResourcePackServer(built, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertTrue(pack.url().startsWith("http://127.0.0.1:"),
                    pack.url());
            assertTrue(pack.url().endsWith(ResourcePackServer.PACK_PATH), pack.url());
            assertEquals(built.sha1(), pack.sha1());
            assertFalse(pack.required());
            assertEquals(ResourcePackServer.DEFAULT_PROMPT_KEY, pack.promptKey());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built.file()).length, body.length);
            } finally {
                connection.disconnect();
            }

            // Unknown paths are 404.
            HttpURLConnection missing = (HttpURLConnection)
                    URI.create("http://127.0.0.1:" + server.port() + "/nope.zip")
                            .toURL().openConnection();
            try {
                assertEquals(404, missing.getResponseCode());
            } finally {
                missing.disconnect();
            }
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning());
    }

    @Test
    void servesBothPacksWithMatchingSha1() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackServer server = new ResourcePackServer(built, built763, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            ResourcePack pack763 = server.pack763();
            assertNotNull(pack763);
            assertEquals(built763.sha1(), pack763.sha1());
            assertTrue(pack763.url().endsWith(ResourcePackServer.PACK_PATH_763),
                    pack763.url());
            assertFalse(pack763.url().equals(pack.url()));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack763.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built763.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built763.file()).length,
                        body.length);
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning());
    }

    @Test
    void singlePackServerHasNoPack763() throws Exception {
        ResourcePackBuilder.BuiltPack built = new ResourcePackBuilder().build(tempDir);
        ResourcePackServer server = new ResourcePackServer(built, 0, false);
        try {
            server.start();
            assertNull(server.pack763());
            assertNull(server.url763());
        } finally {
            server.stop();
        }
    }

    @Test
    void servesAllThreePacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackServer server =
                new ResourcePackServer(built, built763, built764, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            ResourcePack pack763 = server.pack763();
            assertNotNull(pack763);
            assertEquals(built763.sha1(), pack763.sha1());

            ResourcePack pack764 = server.pack764();
            assertNotNull(pack764);
            assertEquals(built764.sha1(), pack764.sha1());
            assertTrue(pack764.url().endsWith(ResourcePackServer.PACK_PATH_764),
                    pack764.url());
            // All three share one port, but have different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack764.url()).getPort());
            assertEquals(server.port(), URI.create(pack763.url()).getPort());
            assertFalse(pack764.url().equals(pack.url()));
            assertFalse(pack764.url().equals(pack763.url()));
            assertFalse(pack764.sha1().equals(pack.sha1()));
            assertFalse(pack764.sha1().equals(pack763.sha1()));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack764.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built764.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built764.file()).length,
                        body.length);
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning());
    }

    @Test
    void twoPackServerHasNoPack764() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackServer server = new ResourcePackServer(built, built763, 0, false);
        try {
            server.start();
            assertNull(server.pack764());
            assertNull(server.url764());
        } finally {
            server.stop();
        }
    }

    @Test
    void servesAllFourPacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackServer server =
                new ResourcePackServer(built, built763, built764, built766, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            ResourcePack pack763 = server.pack763();
            assertNotNull(pack763);
            assertEquals(built763.sha1(), pack763.sha1());

            ResourcePack pack764 = server.pack764();
            assertNotNull(pack764);
            assertEquals(built764.sha1(), pack764.sha1());

            // The 1.20.5 / 1.20.6 pack (pack_format 32) serves both
            // versions from one file on the same port.
            ResourcePack pack766 = server.pack766();
            assertNotNull(pack766);
            assertEquals(built766.sha1(), pack766.sha1());
            assertTrue(pack766.url().endsWith(ResourcePackServer.PACK_PATH_766),
                    pack766.url());
            // All four share one port, but have different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack766.url()).getPort());
            assertEquals(server.port(), URI.create(pack764.url()).getPort());
            assertEquals(server.port(), URI.create(pack763.url()).getPort());
            assertFalse(pack766.url().equals(pack.url()));
            assertFalse(pack766.url().equals(pack763.url()));
            assertFalse(pack766.url().equals(pack764.url()));
            assertFalse(pack766.sha1().equals(pack.sha1()));
            assertFalse(pack766.sha1().equals(pack763.sha1()));
            assertFalse(pack766.sha1().equals(pack764.sha1()));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack766.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built766.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built766.file()).length,
                        body.length);
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning());
    }

    @Test
    void threePackServerHasNoPack766() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackServer server =
                new ResourcePackServer(built, built763, built764, 0, false);
        try {
            server.start();
            assertNull(server.pack766());
            assertNull(server.url766());
            // Without a 766 pack, a 766 client gets no pack (null) —
            // the same as a 763 client on a single-pack server.
            assertNull(server.packForProtocol(766));
        } finally {
            server.stop();
        }
    }

    @Test
    void packForProtocolSelects766Pack() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackServer server =
                new ResourcePackServer(built, built763, built764, built766, 0, false);
        try {
            server.start();
            assertEquals(built763.sha1(), server.packForProtocol(763).sha1());
            assertEquals(built764.sha1(), server.packForProtocol(764).sha1());
            assertEquals(built.sha1(), server.packForProtocol(765).sha1());
            assertEquals(built766.sha1(), server.packForProtocol(766).sha1());
        } finally {
            server.stop();
        }
    }
}
