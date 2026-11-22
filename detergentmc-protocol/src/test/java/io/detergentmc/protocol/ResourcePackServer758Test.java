// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 758（1.18.2）用のリソースパックの配信。HTTP サーバーは起動しない
 * （ソケットを使うテストはこのサンドボックスで動かないため、開始前の
 * 出し分けだけを確認する。実際の配信は Stage E の統合テストで確認する）。
 */
class ResourcePackServer758Test {

    @TempDir
    Path tempDir;

    private ResourcePackServer nineteenPackServer() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built758 = builder.build758(tempDir.resolve("758"));
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
        ResourcePackBuilder.BuiltPack built776 = builder.build776(tempDir.resolve("776"));
        return new ResourcePackServer(
                built, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,
                built770, built771, built772, built773, built774, built775, built776, 0, false);
    }

    @Test
    void pack758IsServedBeforeStart() throws Exception {
        ResourcePackServer server = nineteenPackServer();
        ResourcePack pack758 = server.pack758();
        assertNotNull(pack758);
        assertTrue(pack758.url().endsWith(ResourcePackServer.PACK_PATH_758), pack758.url());
        // The 758 pack (pack_format 8) differs from the 759 pack
        // (pack_format 9) and from the main pack (pack_format 22).
        assertFalse(pack758.sha1().equals(server.pack759().sha1()));
        assertFalse(pack758.url().equals(server.pack759().url()));
        assertFalse(pack758.url().equals(server.pack760().url()));
        assertFalse(pack758.url().equals(server.pack774().url()));
        assertFalse(pack758.url().equals(server.pack775().url()));
    }

    @Test
    void packForProtocol758ReturnsPack758() throws Exception {
        ResourcePackServer server = nineteenPackServer();
        assertEquals(server.pack758().url(), server.packForProtocol(758).url());
        assertEquals(server.pack759().url(), server.packForProtocol(759).url());
        assertEquals(server.pack760().url(), server.packForProtocol(760).url());
    }

    @Test
    void packForProtocol758FallsBackTo759Without758Pack() throws Exception {
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
        // The old 17-pack constructor serves no 758 pack.
        ResourcePackServer server = new ResourcePackServer(
                built, built759, built760, built761, built762, built763, built764, built766, built767, built768,
                built769, built770, built771, built772, built773, built774, built775, 0, false);
        assertNull(server.pack758());
        assertNull(server.url758());
        assertEquals(server.pack759().url(), server.packForProtocol(758).url());
    }
}
