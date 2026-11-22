package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 575（1.15.1）パックの配信（ResourcePackServer の21パック構成）。 */
class ResourcePackServer575Test {

    @TempDir
    Path tempDir;

    private ResourcePackServer twentyOnePackServer() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        return new ResourcePackServer(
                builder.build(tempDir.resolve("765")),
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
    void pack575IsAvailable() throws Exception {
        ResourcePackServer server = twentyOnePackServer();
        ResourcePack pack575 = server.pack575();
        assertNotNull(pack575);
        assertTrue(pack575.url().endsWith(ResourcePackServer.PACK_PATH_575), pack575.url());
        assertEquals(pack575.url(), server.url575());
        assertNotNull(server.pack578());
    }

    @Test
    void packForProtocol575ReturnsPack575() throws Exception {
        ResourcePackServer server = twentyOnePackServer();
        assertEquals(server.pack575().url(), server.packForProtocol(575).url());
        assertEquals(server.pack578().url(), server.packForProtocol(578).url());
        assertEquals(server.pack758().url(), server.packForProtocol(758).url());
    }
}
