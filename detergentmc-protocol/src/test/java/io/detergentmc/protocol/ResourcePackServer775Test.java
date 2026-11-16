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

/** 775（26.1）用のリソースパックの配信（同じ25567番ポートで出し分け）。 */
class ResourcePackServer775Test {

    @TempDir
    Path tempDir;

    @Test
    void serves775PackOnTheSamePort() throws Exception {
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
        ResourcePackBuilder.BuiltPack built773 = builder.build773(tempDir.resolve("773"));
        ResourcePackBuilder.BuiltPack built774 = builder.build774(tempDir.resolve("774"));
        ResourcePackBuilder.BuiltPack built775 = builder.build775(tempDir.resolve("775"));
        // 763〜775だけを渡す BuiltPack コンストラクタは無いため、
        // フル Path コンストラクタを直接使う。
        ResourcePackServer server = new ResourcePackServer(
                built.file(), built.sha1(), null, null, null, null, null, null, null, null,
                built763.file(), built763.sha1(), built764.file(), built764.sha1(),
                built766.file(), built766.sha1(), built767.file(), built767.sha1(),
                built768.file(), built768.sha1(), built769.file(), built769.sha1(),
                built770.file(), built770.sha1(), built771.file(), built771.sha1(),
                built772.file(), built772.sha1(), built773.file(), built773.sha1(),
                built774.file(), built774.sha1(), built775.file(), built775.sha1(),
                0, false, "");
        try {
            ResourcePack pack = server.start();
            assertTrue(server.isRunning());
            assertEquals(built.sha1(), pack.sha1());

            // The 26.1 pack (pack_format 84) serves 775 clients
            // from its own file on the same port.
            ResourcePack pack775 = server.pack775();
            assertNotNull(pack775);
            assertEquals(built775.sha1(), pack775.sha1());
            assertTrue(pack775.url().endsWith(ResourcePackServer.PACK_PATH_775),
                    pack775.url());
            assertEquals(ResourcePackServer.PACK_PATH_775, "/detergentmc-pack-26.1.zip");
            // Same port, but different paths and SHA-1s.
            assertEquals(server.port(), URI.create(pack775.url()).getPort());
            assertEquals(server.port(), URI.create(server.pack774().url()).getPort());
            assertFalse(pack775.url().equals(pack.url()));
            assertFalse(pack775.url().equals(server.pack774().url()));
            assertFalse(pack775.url().equals(server.pack773().url()));
            assertFalse(pack775.sha1().equals(pack.sha1()));
            assertFalse(pack775.sha1().equals(server.pack774().sha1()));
            assertFalse(pack775.sha1().equals(server.pack773().sha1()));

            // packForProtocol picks the 775 pack for 775 clients.
            assertEquals(pack775.sha1(), server.packForProtocol(775).sha1());
            assertEquals(built774.sha1(), server.packForProtocol(774).sha1());
            assertEquals(built773.sha1(), server.packForProtocol(773).sha1());

            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack775.url()).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try {
                assertEquals(200, connection.getResponseCode());
                assertEquals("application/zip", connection.getHeaderField("Content-Type"));
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(ResourcePackBuilder.sha1Hex(body), built775.sha1());
                assertEquals(java.nio.file.Files.readAllBytes(built775.file()).length,
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
    void twelvePackServerHasNoPack775() throws Exception {
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
        ResourcePackBuilder.BuiltPack built773 = builder.build773(tempDir.resolve("773"));
        ResourcePackBuilder.BuiltPack built774 = builder.build774(tempDir.resolve("774"));
        ResourcePackServer server = new ResourcePackServer(
                built, built763, built764, built766, built767, built768, built769, built770, built771,
                built772, built773, built774, 0, false);
        try {
            server.start();
            assertNull(server.pack775());
            assertNull(server.url775());
            // 775パックが無ければ packForProtocol(775) は null になる
            // （10パックのときの packForProtocol(774) と同じ契約。呼び出し側が
            // 本体パックへフォールバックする）。
            assertNull(server.packForProtocol(775));
            // 774パックはある。
            assertNotNull(server.pack774());
        } finally {
            server.stop();
        }
    }
}
