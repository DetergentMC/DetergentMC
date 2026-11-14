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

/** 759（1.19）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer759Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllSeventeenPacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built759 = builder.build759(tempDir.resolve("759"));
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
        ResourcePackBuilder.BuiltPack built775 = builder.build775(tempDir.resolve("775"));
        ResourcePackServer server = new ResourcePackServer(
                built, built759, built760, built761, built762, built763, built764, built766, built767, built768,
                built769, built770, built771, built772, built773, built774, built775, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.19 pack (pack_format 9) is served too.
            ResourcePack pack759 = server.pack759();
            assertNotNull(pack759);
            assertEquals(built759.sha1(), pack759.sha1());
            assertTrue(pack759.url().endsWith(ResourcePackServer.PACK_PATH_759),
                    pack759.url());
            // The 1.19.1 / 1.19.2 pack (pack_format 9) serves 760 clients
            // from its own file on the same port.
            ResourcePack pack760 = server.pack760();
            assertNotNull(pack760);
            assertEquals(built760.sha1(), pack760.sha1());
            assertTrue(pack760.url().endsWith(ResourcePackServer.PACK_PATH_760),
                    pack760.url());
            // All seventeen share one port, but have different paths.
            assertEquals(server.port(), URI.create(pack759.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack774().url()).getPort());
            assertEquals(server.port(), URI.create(server.pack775().url()).getPort());
            assertFalse(pack759.url().equals(pack.url()));
            assertFalse(pack759.url().equals(pack760.url()));
            assertFalse(pack759.url().equals(server.pack761().url()));
            assertFalse(pack759.url().equals(server.pack774().url()));
            assertFalse(pack759.url().equals(server.pack775().url()));
            assertFalse(pack759.sha1().equals(pack.sha1()));
            assertFalse(pack759.sha1().equals(server.pack761().sha1()));
            assertFalse(pack759.sha1().equals(server.pack774().sha1()));
            assertFalse(pack759.sha1().equals(server.pack775().sha1()));

            // The 26.1 pack (pack_format 84) serves 775 clients
            // from its own file on the same port.
            ResourcePack pack775 = server.pack775();
            assertNotNull(pack775);
            assertEquals(built775.sha1(), pack775.sha1());
            assertTrue(pack775.url().endsWith(ResourcePackServer.PACK_PATH_775),
                    pack775.url());
            assertFalse(pack775.url().equals(server.pack774().url()));
            assertFalse(pack775.sha1().equals(server.pack774().sha1()));

            // packForProtocol picks the 759 pack for 759 clients.
            assertEquals(pack759.url(), server.packForProtocol(759).url());
            assertEquals(pack760.url(), server.packForProtocol(760).url());
            assertEquals(pack.url(), server.packForProtocol(765).url());
            assertEquals(pack775.url(), server.packForProtocol(775).url());

            // The 759 pack is actually downloadable over HTTP.
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack759.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack759.sha1(),
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
    void oldFifteenPackConstructorHasNo759Pack() throws Exception {
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
            server.start();
            // The old constructor leaves the 759 pack unset; 759 clients
            // fall back to the 760 pack.
            assertNull(server.pack759());
            assertEquals(server.pack760().url(), server.packForProtocol(759).url());
        } finally {
            server.stop();
        }
    }
}
