package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 338（1.12.1）パックの配信（ResourcePackServer の32パック構成と338欠落時の代替）。 */
class ResourcePackServer338Test {

    @TempDir
    Path tempDir;

    private ResourcePackServer thirtyTwoPackServer() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        return new ResourcePackServer(
                builder.build(tempDir.resolve("765")),
                builder.build338(tempDir.resolve("338")),
                builder.build340(tempDir.resolve("340")),
                builder.build393(tempDir.resolve("393")),
                builder.build401(tempDir.resolve("401")),
                builder.build404(tempDir.resolve("404")),
                builder.build477(tempDir.resolve("477")),
                builder.build480(tempDir.resolve("480")),
                builder.build485(tempDir.resolve("485")),
                builder.build490(tempDir.resolve("490")),
                builder.build498(tempDir.resolve("498")),
                builder.build573(tempDir.resolve("573")),
                builder.build575(tempDir.resolve("575")),
                builder.build578(tempDir.resolve("578")),
                builder.build758(tempDir.resolve("758")),
                builder.build759(tempDir.resolve("759")),
                builder.build760(tempDir.resolve("760")),
                builder.build761(tempDir.resolve("761")),
                builder.build762(tempDir.resolve("762")),
                builder.build763(tempDir.resolve("763")),
                builder.build764(tempDir.resolve("764")),
                builder.build766(tempDir.resolve("766")),
                builder.build767(tempDir.resolve("767")),
                builder.build768(tempDir.resolve("768")),
                builder.build769(tempDir.resolve("769")),
                builder.build770(tempDir.resolve("770")),
                builder.build771(tempDir.resolve("771")),
                builder.build772(tempDir.resolve("772")),
                builder.build773(tempDir.resolve("773")),
                builder.build774(tempDir.resolve("774")),
                builder.build775(tempDir.resolve("775")),
                builder.build776(tempDir.resolve("776")),
                0, false, "");
    }

    @Test
    void pack338IsAvailable() throws Exception {
        ResourcePackServer server = thirtyTwoPackServer();
        ResourcePack pack338 = server.pack338();
        assertNotNull(pack338);
        assertTrue(pack338.url().endsWith(ResourcePackServer.PACK_PATH_338), pack338.url());
        assertEquals(pack338.url(), server.url338());
        assertNotNull(server.pack340());
    }

    @Test
    void packForProtocol338ReturnsPack338() throws Exception {
        ResourcePackServer server = thirtyTwoPackServer();
        assertEquals(server.pack338().url(), server.packForProtocol(338).url());
        assertEquals(server.pack340().url(), server.packForProtocol(340).url());
    }

    @Test
    void packForProtocol338FallsBackToPack340When338Missing() throws Exception {
        // 338 パックを持たない構成（31パック構成）では 340 パックで
        // 代替する（pack_format 3・旧レイアウトは同一で、説明文だけが
        // 1.12.2 用になる）。
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackServer server = new ResourcePackServer(
                builder.build(tempDir.resolve("765")),
                builder.build340(tempDir.resolve("340")),
                builder.build393(tempDir.resolve("393")),
                builder.build401(tempDir.resolve("401")),
                builder.build404(tempDir.resolve("404")),
                builder.build477(tempDir.resolve("477")),
                builder.build480(tempDir.resolve("480")),
                builder.build485(tempDir.resolve("485")),
                builder.build490(tempDir.resolve("490")),
                builder.build498(tempDir.resolve("498")),
                builder.build573(tempDir.resolve("573")),
                builder.build575(tempDir.resolve("575")),
                builder.build578(tempDir.resolve("578")),
                builder.build758(tempDir.resolve("758")),
                builder.build759(tempDir.resolve("759")),
                builder.build760(tempDir.resolve("760")),
                builder.build761(tempDir.resolve("761")),
                builder.build762(tempDir.resolve("762")),
                builder.build763(tempDir.resolve("763")),
                builder.build764(tempDir.resolve("764")),
                builder.build766(tempDir.resolve("766")),
                builder.build767(tempDir.resolve("767")),
                builder.build768(tempDir.resolve("768")),
                builder.build769(tempDir.resolve("769")),
                builder.build770(tempDir.resolve("770")),
                builder.build771(tempDir.resolve("771")),
                builder.build772(tempDir.resolve("772")),
                builder.build773(tempDir.resolve("773")),
                builder.build774(tempDir.resolve("774")),
                builder.build775(tempDir.resolve("775")),
                builder.build776(tempDir.resolve("776")),
                0, false, "");
        assertEquals(server.pack340().url(), server.packForProtocol(338).url());
    }
}
