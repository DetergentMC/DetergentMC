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

/** 736（1.16.1）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer736Test {

    @TempDir
    Path tempDir;

    @Test
    void servesAllTwentyFourPacksOnTheSamePort() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built736 = builder.build736(tempDir.resolve("736"));
        ResourcePackBuilder.BuiltPack built751 = builder.build751(tempDir.resolve("751"));
        ResourcePackBuilder.BuiltPack built753 = builder.build753(tempDir.resolve("753"));
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
                built, built736, built751, built753, built754, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768,
                built769, built770, built771, built772, built773, built774, built775, 0, false);
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 1.16.1 pack (pack_format 5) is served too.
            ResourcePack pack736 = server.pack736();
            assertNotNull(pack736);
            assertEquals(built736.sha1(), pack736.sha1());
            assertTrue(pack736.url().endsWith(ResourcePackServer.PACK_PATH_736),
                    pack736.url());
            assertEquals(ResourcePackServer.PACK_PATH_736, "/detergentmc-pack-1.16.1.zip");
            // All twenty-four share one port, but have different paths.
            assertEquals(server.port(), URI.create(pack736.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack774().url()).getPort());
            assertEquals(server.port(), URI.create(server.pack775().url()).getPort());
            assertFalse(pack736.url().equals(pack.url()));
            assertFalse(pack736.url().equals(server.pack751().url()));
            assertFalse(pack736.url().equals(server.pack753().url()));
            assertFalse(pack736.url().equals(server.pack754().url()));
            assertFalse(pack736.url().equals(server.pack755().url()));
            assertFalse(pack736.url().equals(server.pack757().url()));
            assertFalse(pack736.url().equals(server.pack774().url()));
            assertFalse(pack736.url().equals(server.pack775().url()));
            assertFalse(pack736.sha1().equals(pack.sha1()));
            assertFalse(pack736.sha1().equals(server.pack751().sha1()));
            assertFalse(pack736.sha1().equals(server.pack753().sha1()));
            assertFalse(pack736.sha1().equals(server.pack754().sha1()));
            assertFalse(pack736.sha1().equals(server.pack755().sha1()));
            assertFalse(pack736.sha1().equals(server.pack757().sha1()));
            assertFalse(pack736.sha1().equals(server.pack774().sha1()));
            assertFalse(pack736.sha1().equals(server.pack775().sha1()));
            // The 736 pack has pack_format 5 while the 751 pack has 6,
            // so their SHA-1 values differ.
            assertFalse(pack736.sha1().equals(server.pack751().sha1()));

            // packForProtocol picks the 736 pack for 736 clients.
            assertEquals(pack736.url(), server.packForProtocol(736).url());
            assertEquals(server.pack751().url(), server.packForProtocol(751).url());
            assertEquals(server.pack753().url(), server.packForProtocol(753).url());
            assertEquals(server.pack754().url(), server.packForProtocol(754).url());
            assertEquals(server.pack755().url(), server.packForProtocol(755).url());
            assertEquals(server.pack756().url(), server.packForProtocol(756).url());
            assertEquals(server.pack757().url(), server.packForProtocol(757).url());
            assertEquals(pack.url(), server.packForProtocol(765).url());
            assertEquals(server.pack775().url(), server.packForProtocol(775).url());

            // The 736 pack is actually downloadable over HTTP.
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack736.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack736.sha1(),
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
    void oldTwentyThreePackConstructorHasNo736Pack() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built751 = builder.build751(tempDir.resolve("751"));
        ResourcePackBuilder.BuiltPack built753 = builder.build753(tempDir.resolve("753"));
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
                built, built751, built753, built754, built755, built756, built757, built759, built760, built761, built762, built763, built764, built766, built767, built768,
                built769, built770, built771, built772, built773, built774, built775, 0, false);
        try {
            server.start();
            // The old constructor leaves the 736 pack unset; 736 clients
            // fall back to the 751 pack (the adjacent newer pack).
            assertNull(server.pack736());
            assertEquals(server.pack751().url(), server.packForProtocol(736).url());
        } finally {
            server.stop();
        }
    }
}
