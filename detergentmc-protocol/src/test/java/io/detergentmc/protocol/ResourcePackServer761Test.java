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

/** 761（1.19.3）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer761Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllFourteenPacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built761 = builder.build761(tempDir.resolve("761"));
        ResourcePackBuilder.BuiltPack built762 = builder.build762(tempDir.resolve("762"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackBuilder.BuiltPack built773 = builder.build773(tempDir.resolve("773"));
        ResourcePackBuilder.BuiltPack built774 = builder.build774(tempDir.resolve("774"));
        ResourcePackServer server = new ResourcePackServer(
                built, built761, built762, built763, built764, built766, built767, built768,
                built769, built770, built771, built772, built773, built774, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.19.3 pack (pack_format 12) is served too.
            ResourcePack pack761 = server.pack761();
            assertNotNull(pack761);
            assertEquals(built761.sha1(), pack761.sha1());
            assertTrue(pack761.url().endsWith(ResourcePackServer.PACK_PATH_761),
                    pack761.url());
            // The 1.19.4 pack (pack_format 13) serves 762 clients
            // from its own file on the same port.
            ResourcePack pack762 = server.pack762();
            assertNotNull(pack762);
            assertEquals(built762.sha1(), pack762.sha1());
            assertTrue(pack762.url().endsWith(ResourcePackServer.PACK_PATH_762),
                    pack762.url());
            // All fourteen share one port, but have different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack761.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack774().url()).getPort());
            assertFalse(pack761.url().equals(pack.url()));
            assertFalse(pack761.url().equals(pack762.url()));
            assertFalse(pack761.url().equals(server.pack763().url()));
            assertFalse(pack761.url().equals(server.pack774().url()));
            assertFalse(pack761.sha1().equals(pack.sha1()));
            assertFalse(pack761.sha1().equals(pack762.sha1()));
            assertFalse(pack761.sha1().equals(server.pack763().sha1()));
            assertFalse(pack761.sha1().equals(server.pack774().sha1()));

            // packForProtocol picks the 761 pack for 761 clients.
            assertEquals(pack761.url(), server.packForProtocol(761).url());
            assertEquals(pack762.url(), server.packForProtocol(762).url());
            assertEquals(pack.url(), server.packForProtocol(765).url());

            // The 761 pack is actually downloadable over HTTP.
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack761.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack761.sha1(),
                        ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning());
    }

    @Test
    void oldThirteenPackConstructorHasNo761Pack() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built762 = builder.build762(tempDir.resolve("762"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackBuilder.BuiltPack built766 = builder.build766(tempDir.resolve("766"));
        ResourcePackBuilder.BuiltPack built767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack built768 = builder.build768(tempDir.resolve("768"));
        ResourcePackBuilder.BuiltPack built769 = builder.build769(tempDir.resolve("769"));
        ResourcePackBuilder.BuiltPack built770 = builder.build770(tempDir.resolve("770"));
        ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve("772"));
        ResourcePackBuilder.BuiltPack built773 = builder.build773(tempDir.resolve("773"));
        ResourcePackBuilder.BuiltPack built774 = builder.build774(tempDir.resolve("774"));
        ResourcePackServer server = new ResourcePackServer(
                built, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, 0, false);
        try {
            server.start();
            // The old constructor leaves the 761 pack unset; 761 clients
            // fall back to the 762 pack.
            assertNull(server.pack761());
            assertEquals(server.pack762().url(), server.packForProtocol(761).url());
        } finally {
            server.stop();
        }
    }
}
