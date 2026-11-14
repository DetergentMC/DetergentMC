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

/** 760（1.19.1／1.19.2）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer760Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllFifteenPacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built760 = builder.build760(tempDir.resolve("760"));
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
                built, built760, built761, built762, built763, built764, built766, built767, built768,
                built769, built770, built771, built772, built773, built774, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.19.1 / 1.19.2 pack (pack_format 9) is served too.
            ResourcePack pack760 = server.pack760();
            assertNotNull(pack760);
            assertEquals(built760.sha1(), pack760.sha1());
            assertTrue(pack760.url().endsWith(ResourcePackServer.PACK_PATH_760),
                    pack760.url());
            // The 1.19.3 pack (pack_format 12) serves 761 clients
            // from its own file on the same port.
            ResourcePack pack761 = server.pack761();
            assertNotNull(pack761);
            assertEquals(built761.sha1(), pack761.sha1());
            assertTrue(pack761.url().endsWith(ResourcePackServer.PACK_PATH_761),
                    pack761.url());
            // All fifteen share one port, but have different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack760.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack774().url()).getPort());
            assertFalse(pack760.url().equals(pack.url()));
            assertFalse(pack760.url().equals(pack761.url()));
            assertFalse(pack760.url().equals(server.pack763().url()));
            assertFalse(pack760.url().equals(server.pack774().url()));
            assertFalse(pack760.sha1().equals(pack.sha1()));
            assertFalse(pack760.sha1().equals(pack761.sha1()));
            assertFalse(pack760.sha1().equals(server.pack763().sha1()));
            assertFalse(pack760.sha1().equals(server.pack774().sha1()));

            // packForProtocol picks the 760 pack for 760 clients.
            assertEquals(pack760.url(), server.packForProtocol(760).url());
            assertEquals(pack761.url(), server.packForProtocol(761).url());
            assertEquals(pack.url(), server.packForProtocol(765).url());

            // The 760 pack is actually downloadable over HTTP.
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack760.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack760.sha1(),
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
    void oldFourteenPackConstructorHasNo760Pack() throws Exception {
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
            server.start();
            // The old constructor leaves the 760 pack unset; 760 clients
            // fall back to the 761 pack.
            assertNull(server.pack760());
            assertEquals(server.pack761().url(), server.packForProtocol(760).url());
        } finally {
            server.stop();
        }
    }
}
