package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 477（1.14）パックの配信（ResourcePackServer の28パック構成と477欠落時の代替）。 */
class ResourcePackServer477Test {

    @TempDir
    Path tempDir;

    private ResourcePackServer twentyEightPackServer() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        return new ResourcePackServer(
                builder.build(tempDir.resolve("765")),
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
    void pack480IsAvailable() throws Exception {
        ResourcePackServer server = twentyEightPackServer();
        ResourcePack pack480 = server.pack480();
        assertNotNull(pack480);
        assertTrue(pack480.url().endsWith(ResourcePackServer.PACK_PATH_480), pack480.url());
        assertEquals(pack480.url(), server.url480());
        assertNotNull(server.pack485());
    }

    @Test
    void packForProtocol480ReturnsPack480() throws Exception {
        ResourcePackServer server = twentyEightPackServer();
        assertEquals(server.pack480().url(), server.packForProtocol(480).url());
        assertEquals(server.pack485().url(), server.packForProtocol(485).url());
        assertEquals(server.pack573().url(), server.packForProtocol(573).url());
    }

    @Test
    void pack477IsAvailable() throws Exception {
        ResourcePackServer server = twentyEightPackServer();
        ResourcePack pack477 = server.pack477();
        assertNotNull(pack477);
        assertTrue(pack477.url().endsWith(ResourcePackServer.PACK_PATH_477), pack477.url());
        assertEquals(pack477.url(), server.url477());
        assertNotNull(server.pack480());
    }

    @Test
    void packForProtocol477ReturnsPack477() throws Exception {
        ResourcePackServer server = twentyEightPackServer();
        assertEquals(server.pack477().url(), server.packForProtocol(477).url());
        assertEquals(server.pack480().url(), server.packForProtocol(480).url());
    }

    @Test
    void packForProtocol477FallsBackToPack480When477Missing() throws Exception {
        // 477 パックを持たない26パック構成では 480 パックで代替する。
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackServer server = new ResourcePackServer(
                builder.build(tempDir.resolve("765")),
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
        assertEquals(server.pack480().url(), server.packForProtocol(477).url());
    }

    @Test
    void packForProtocol480FallsBackToPack485When480Missing() throws Exception {
        // 480 パックを持たない25パック構成では 485 パックで代替する。
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackServer server = new ResourcePackServer(
                builder.build(tempDir.resolve("765")),
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
        assertEquals(server.pack485().url(), server.packForProtocol(480).url());
    }
}
