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

/** 754（1.16.4／1.16.5）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer754Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllTwentyOnePacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built754 = builder.build754(tempDir.resolve("754"));
        ResourcePackBuilder.BuiltPack built755 = builder.build755(tempDir.resolve("755"));
        ResourcePackBuilder.BuiltPack built756 = builder.build756(tempDir.resolve("756"));
        ResourcePackBuilder.BuiltPack built757 = builder.build757(tempDir.resolve("757"));
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
                built, built754, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768,
                built769, built770, built771, built772, built773, built774, built775, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.16.4 / 1.16.5 pack (pack_format 6) is served too.
            ResourcePack pack754 = server.pack754();
            assertNotNull(pack754);
            assertEquals(built754.sha1(), pack754.sha1());
            assertTrue(pack754.url().endsWith(ResourcePackServer.PACK_PATH_754),
                    pack754.url());
            assertEquals(ResourcePackServer.PACK_PATH_754, "/detergentmc-pack-1.16.5.zip");
            // All twenty-one share one port, but have different paths.
            assertEquals(server.port(), URI.create(pack754.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack774().url()).getPort());
            assertEquals(server.port(), URI.create(server.pack775().url()).getPort());
            assertFalse(pack754.url().equals(pack.url()));
            assertFalse(pack754.url().equals(server.pack755().url()));
            assertFalse(pack754.url().equals(server.pack757().url()));
            assertFalse(pack754.url().equals(server.pack774().url()));
            assertFalse(pack754.url().equals(server.pack775().url()));
            assertFalse(pack754.sha1().equals(pack.sha1()));
            assertFalse(pack754.sha1().equals(server.pack755().sha1()));
            assertFalse(pack754.sha1().equals(server.pack757().sha1()));
            assertFalse(pack754.sha1().equals(server.pack774().sha1()));
            assertFalse(pack754.sha1().equals(server.pack775().sha1()));
            // The 754 pack has a different pack_format (6) from the 755
            // pack (7), so their SHA-1 values differ.
            assertFalse(pack754.sha1().equals(server.pack755().sha1()));

            // packForProtocol picks the 754 pack for 754 clients.
            assertEquals(pack754.url(), server.packForProtocol(754).url());
            assertEquals(server.pack755().url(), server.packForProtocol(755).url());
            assertEquals(server.pack756().url(), server.packForProtocol(756).url());
            assertEquals(server.pack757().url(), server.packForProtocol(757).url());
            assertEquals(pack.url(), server.packForProtocol(765).url());
            assertEquals(server.pack775().url(), server.packForProtocol(775).url());

            // The 754 pack is actually downloadable over HTTP.
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack754.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack754.sha1(),
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
    void oldTwentyPackConstructorHasNo754Pack() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built755 = builder.build755(tempDir.resolve("755"));
        ResourcePackBuilder.BuiltPack built756 = builder.build756(tempDir.resolve("756"));
        ResourcePackBuilder.BuiltPack built757 = builder.build757(tempDir.resolve("757"));
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
                built, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768,
                built769, built770, built771, built772, built773, built774, built775, 0, false);
        try {
            server.start();
            // The old constructor leaves the 754 pack unset; 754 clients
            // fall back to the 755 pack (the adjacent newer pack).
            assertNull(server.pack754());
            assertEquals(server.pack755().url(), server.packForProtocol(754).url());
        } finally {
            server.stop();
        }
    }
}
