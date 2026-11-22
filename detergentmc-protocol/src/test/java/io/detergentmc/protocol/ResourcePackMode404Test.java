package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 404（1.13.2）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode404Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_404 =
            "https://example.invalid/packs/detergentmc-pack-1.13.2.zip";
    private static final String VALID_SHA1_404 =
            "abababababababababababababababababababab";
    private static final String URL_477 =
            "https://example.invalid/packs/detergentmc-pack-1.14.zip";
    private static final String VALID_SHA1_477 =
            "efefefefefefefefefefefefefefefefefefefef";

    /** 最大オーバーロード：404 ペアまで渡す（477 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url404, String sha1404) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1,
                // 761・762・763・764・766〜776（15ペア＝30個は未設定）
                "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "",
                "", "", "", "", "", "",
                // 760・759・758（未設定）
                "", "", "", "", "", "",
                // 578・575・573（未設定）
                "", "", "", "",
                "", "",
                // 498・490（未設定）
                "", "",
                "", "",
                // 485（未設定）
                "", "",
                // 480（未設定）
                "", "",
                // 477（フォールバック先として設定）
                URL_477, VALID_SHA1_477,
                url404, sha1404,
                // 401（未設定）
                "", "",
                // 393（未設定）
                "", "",
                // 340（未設定）
                "", "",
                false, "");
    }

    @Test
    void valid404UrlAndSha1YieldPack404() {
        ResourcePackMode.Decision decision = decideFull(URL_404, VALID_SHA1_404);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack404());
        assertEquals(URL_404, decision.pack404().url());
        assertEquals(VALID_SHA1_404, decision.pack404().sha1());
        // packForProtocol は 404 クライアントに 404 パックを返す。
        assertEquals(URL_404, decision.packForProtocol(404).url());
        assertEquals(URL_477, decision.packForProtocol(477).url());
    }

    @Test
    void empty404UrlFallsBackTo477Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack404());
        assertEquals(URL_477, decision.packForProtocol(404).url());
    }

    @Test
    void invalid404Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_404, "not-a-sha1");
        assertNull(decision.pack404());
        assertTrue(decision.problem() != null && decision.problem().contains("404"),
                String.valueOf(decision.problem()));
        assertEquals(URL_477, decision.packForProtocol(404).url());
    }

    @Test
    void nonHttps404UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_404);
        assertNull(decision.pack404());
        assertTrue(decision.problem() != null && decision.problem().contains("404"),
                String.valueOf(decision.problem()));
    }
}
