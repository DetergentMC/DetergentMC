// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

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

/** 772（1.21.7）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer772Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllTenPacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770, built771,
                built772, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.21.6 pack (pack_format 63) is served too.
            ResourcePack pack771 = server.pack771();
            assertNotNull(pack771);
            assertEquals(built771.sha1(), pack771.sha1());
            assertTrue(pack771.url().endsWith(ResourcePackServer.PACK_PATH_771),
                    pack771.url());
            // The 1.21.7 pack (pack_format 64) serves 772 clients
            // from its own file on the same port.
            ResourcePack pack772 = server.pack772();
            assertNotNull(pack772);
            assertEquals(built772.sha1(), pack772.sha1());
            assertTrue(pack772.url().endsWith(ResourcePackServer.PACK_PATH_772),
                    pack772.url());
            // All ten share one port, but have different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack772.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack769().url()).getPort());
            assertFalse(pack772.url().equals(pack.url()));
            assertFalse(pack772.url().equals(pack771.url()));
            assertFalse(pack772.url().equals(server.pack763().url()));
            assertFalse(pack772.url().equals(server.pack764().url()));
            assertFalse(pack772.url().equals(server.pack766().url()));
            assertFalse(pack772.url().equals(server.pack767().url()));
            assertFalse(pack772.url().equals(server.pack768().url()));
            assertFalse(pack772.url().equals(server.pack769().url()));
            assertFalse(pack772.sha1().equals(pack.sha1()));
            assertFalse(pack772.sha1().equals(pack771.sha1()));
            assertFalse(pack772.sha1().equals(server.pack763().sha1()));
            assertFalse(pack772.sha1().equals(server.pack764().sha1()));
            assertFalse(pack772.sha1().equals(server.pack766().sha1()));
            assertFalse(pack772.sha1().equals(server.pack767().sha1()));
            assertFalse(pack772.sha1().equals(server.pack768().sha1()));
            assertFalse(pack772.sha1().equals(server.pack769().sha1()));

            // packForProtocol picks the 772 pack for 772 clients.
            assertEquals(pack772.sha1(), server.packForProtocol(772).sha1());
            assertEquals(built771.sha1(), server.packForProtocol(771).sha1());
            assertEquals(built769.sha1(), server.packForProtocol(769).sha1());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack772.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built772.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built772.file()).length,
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
    void ninePackServerHasNoPack772() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770,
                built771, 0, false);
        try {
            server.start();
            assertNull(server.pack772());
            assertNull(server.url772());
            // 772パックが無ければ packForProtocol(772) は null になる
            // （8パックのときの packForProtocol(771) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。
            assertNull(server.packForProtocol(772));
        } finally {
            server.stop();
        }
    }
}
