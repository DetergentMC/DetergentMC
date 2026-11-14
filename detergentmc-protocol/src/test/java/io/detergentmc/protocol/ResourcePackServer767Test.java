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

/** 767（1.21／1.21.1）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer767Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllFivePacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.21 / 1.21.1 pack (pack_format 34) serves both
            // versions from one file on the same port.
            ResourcePack pack767 = server.pack767();
            assertNotNull(pack767);
            assertEquals(built767.sha1(), pack767.sha1());
            assertTrue(pack767.url().endsWith(ResourcePackServer.PACK_PATH_767),
                    pack767.url());
            // All five share one port, but have different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack767.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack766().url()).getPort());
            assertFalse(pack767.url().equals(pack.url()));
            assertFalse(pack767.url().equals(server.pack763().url()));
            assertFalse(pack767.url().equals(server.pack764().url()));
            assertFalse(pack767.url().equals(server.pack766().url()));
            assertFalse(pack767.sha1().equals(pack.sha1()));
            assertFalse(pack767.sha1().equals(server.pack763().sha1()));
            assertFalse(pack767.sha1().equals(server.pack764().sha1()));
            assertFalse(pack767.sha1().equals(server.pack766().sha1()));

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack767.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built767.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built767.file()).length,
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
    void fourPackServerHasNoPack767() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackServer server =
                new ResourcePackServer(built, built763, built764, built766, 0, false);
        try {
            server.start();
            assertNull(server.pack767());
            assertNull(server.url767());
            // Without a 767 pack, a 767 client gets no pack (null) —
            // the same as a 763 client on a single-pack server.
            assertNull(server.packForProtocol(767));
        } finally {
            server.stop();
        }
    }

    @Test
    void packForProtocolSelects767Pack() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, 0, false);
        try {
            server.start();
            assertEquals(built763.sha1(), server.packForProtocol(763).sha1());
            assertEquals(built764.sha1(), server.packForProtocol(764).sha1());
            assertEquals(built.sha1(), server.packForProtocol(765).sha1());
            assertEquals(built766.sha1(), server.packForProtocol(766).sha1());
            assertEquals(built767.sha1(), server.packForProtocol(767).sha1());
        } finally {
            server.stop();
        }
    }
}
