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

/** 768（1.21.2／1.21.3）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer768Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllSixPacksOnTheSamePort() throws Exception {
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
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.21.2 / 1.21.3 pack (pack_format 42) serves both
            // versions from one file on the same port.
            ResourcePack pack768 = server.pack768();
            assertNotNull(pack768);
            assertEquals(built768.sha1(), pack768.sha1());
            assertTrue(pack768.url().endsWith(ResourcePackServer.PACK_PATH_768),
                    pack768.url());
            // All six share one port, but have different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack768.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack767().url()).getPort());
            assertFalse(pack768.url().equals(pack.url()));
            assertFalse(pack768.url().equals(server.pack763().url()));
            assertFalse(pack768.url().equals(server.pack764().url()));
            assertFalse(pack768.url().equals(server.pack766().url()));
            assertFalse(pack768.url().equals(server.pack767().url()));
            assertFalse(pack768.sha1().equals(pack.sha1()));
            assertFalse(pack768.sha1().equals(server.pack763().sha1()));
            assertFalse(pack768.sha1().equals(server.pack764().sha1()));
            assertFalse(pack768.sha1().equals(server.pack766().sha1()));
            assertFalse(pack768.sha1().equals(server.pack767().sha1()));

            // packForProtocol picks the 768 pack for 768 clients.
            assertEquals(pack768.sha1(), server.packForProtocol(768).sha1());
            assertEquals(built767.sha1(), server.packForProtocol(767).sha1());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack768.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built768.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built768.file()).length,
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
    void fivePackServerHasNoPack768() throws Exception {
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
            assertNull(server.pack768());
            assertNull(server.url768());
            // 768パックが無ければ packForProtocol(768) は null になる
            // （4パックのときの packForProtocol(767) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。
            assertNull(server.packForProtocol(768));
        } finally {
            server.stop();
        }
    }
}
