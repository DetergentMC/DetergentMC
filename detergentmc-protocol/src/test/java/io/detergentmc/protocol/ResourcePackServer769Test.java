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

/** 769（1.21.4）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer769Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllSevenPacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.21.4 pack (pack_format 46) serves 769 clients
            // from its own file on the same port.
            ResourcePack pack769 = server.pack769();
            assertNotNull(pack769);
            assertEquals(built769.sha1(), pack769.sha1());
            assertTrue(pack769.url().endsWith(ResourcePackServer.PACK_PATH_769),
                    pack769.url());
            // All seven share one port, but have different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack769.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack768().url()).getPort());
            assertFalse(pack769.url().equals(pack.url()));
            assertFalse(pack769.url().equals(server.pack763().url()));
            assertFalse(pack769.url().equals(server.pack764().url()));
            assertFalse(pack769.url().equals(server.pack766().url()));
            assertFalse(pack769.url().equals(server.pack767().url()));
            assertFalse(pack769.url().equals(server.pack768().url()));
            assertFalse(pack769.sha1().equals(pack.sha1()));
            assertFalse(pack769.sha1().equals(server.pack763().sha1()));
            assertFalse(pack769.sha1().equals(server.pack764().sha1()));
            assertFalse(pack769.sha1().equals(server.pack766().sha1()));
            assertFalse(pack769.sha1().equals(server.pack767().sha1()));
            assertFalse(pack769.sha1().equals(server.pack768().sha1()));

            // packForProtocol picks the 769 pack for 769 clients.
            assertEquals(pack769.sha1(), server.packForProtocol(769).sha1());
            assertEquals(built768.sha1(), server.packForProtocol(768).sha1());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack769.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built769.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built769.file()).length,
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
    void sixPackServerHasNoPack769() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, 0, false);
        try {
            server.start();
            assertNull(server.pack769());
            assertNull(server.url769());
            // 769パックが無ければ packForProtocol(769) は null になる
            // （5パックのときの packForProtocol(768) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。
            assertNull(server.packForProtocol(769));
        } finally {
            server.stop();
        }
    }
}
