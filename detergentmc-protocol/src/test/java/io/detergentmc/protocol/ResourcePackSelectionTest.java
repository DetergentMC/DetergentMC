package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * リソースパックの出し分け：基準はクライアントのプロトコルであって、
 * 内部サーバーのバージョンではない。内部が764でも765クライアントには
 * pack_format 22のパックを出す。
 */
class ResourcePackSelectionTest {

    @TempDir
    Path tempDir;

    private static ResourcePack pack(String url) {
        return new ResourcePack(url,
                "0123456789abcdef0123456789abcdef01234567", false, "");
    }

    @Test
    void decisionPackForProtocolIsKeyedByClientProtocol() {
        ResourcePack main = pack("https://example.invalid/pack.zip");
        ResourcePack pack763 = pack("https://example.invalid/pack-1.20.1.zip");
        ResourcePack pack764 = pack("https://example.invalid/pack-1.20.2.zip");
        ResourcePackMode.Decision decision = new ResourcePackMode.Decision(
                ResourcePackMode.Kind.EXTERNAL, main, null, pack763, pack764);
        assertEquals(pack763, decision.packForProtocol(763));
        assertEquals(pack764, decision.packForProtocol(764));
        assertEquals(main, decision.packForProtocol(765));
        // server.properties に書くパックも同じ基準で選ぶ。
        // 内部764なら764用のパック（18）が入る。
        assertEquals(pack764, decision.packForProtocol(764));
        assertEquals(main, decision.packForProtocol(765));
    }

    @Test
    void decisionFallsBackToMainPackWhenVariantMissing() {
        ResourcePack main = pack("https://example.invalid/pack.zip");
        ResourcePackMode.Decision decision = new ResourcePackMode.Decision(
                ResourcePackMode.Kind.EXTERNAL, main, null, null, null);
        assertEquals(main, decision.packForProtocol(763));
        assertEquals(main, decision.packForProtocol(764));
        assertEquals(main, decision.packForProtocol(765));
    }

    @Test
    void serverPackForProtocolIsKeyedByClientProtocol() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackServer server =
                new ResourcePackServer(built, built763, built764, 0, false);
        try {
            ResourcePack main = server.start();
            assertEquals(built.sha1(), server.packForProtocol(765).sha1());
            assertEquals(built763.sha1(), server.packForProtocol(763).sha1());
            assertEquals(built764.sha1(), server.packForProtocol(764).sha1());
            assertEquals(main.sha1(), server.packForProtocol(765).sha1());
            // 内部が764でも765クライアントには本体（22）を出す。
            // packForProtocol の引数はクライアントのプロトコルである。
            assertEquals(built.sha1(), server.packForProtocol(765).sha1());
        } finally {
            server.stop();
        }
    }

    @Test
    void serverPackForProtocolReturnsNullFor765BeforeStart() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack built763 = builder.build763(tempDir.resolve("763"));
        ResourcePackBuilder.BuiltPack built764 = builder.build764(tempDir.resolve("764"));
        ResourcePackServer server =
                new ResourcePackServer(built, built763, built764, 0, false);
        // 開始前は本体（765用）が無いため null。763／764用はファイルから作れる。
        assertNull(server.packForProtocol(765));
        assertEquals(built763.sha1(), server.packForProtocol(763).sha1());
        assertEquals(built764.sha1(), server.packForProtocol(764).sha1());
    }
}
