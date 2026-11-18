package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 490（1.14.3）パックの配信（ResourcePackServer の24パック構成）。 */
class ResourcePackServer490Test {

    @TempDir
    Path tempDir;

    private ResourcePackServer twentyFourPackServer() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        return new ResourcePackServer(
                builder.build(tempDir.resolve("765")),
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
    void pack490IsAvailable() throws Exception {
        ResourcePackServer server = twentyFourPackServer();
        ResourcePack pack490 = server.pack490();
        assertNotNull(pack490);
        assertTrue(pack490.url().endsWith(ResourcePackServer.PACK_PATH_490), pack490.url());
        assertEquals(pack490.url(), server.url490());
        assertNotNull(server.pack498());
    }

    @Test
    void packForProtocol490ReturnsPack490() throws Exception {
        ResourcePackServer server = twentyFourPackServer();
        assertEquals(server.pack490().url(), server.packForProtocol(490).url());
        assertEquals(server.pack498().url(), server.packForProtocol(498).url());
        assertEquals(server.pack573().url(), server.packForProtocol(573).url());
    }
}
