package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 401（1.13.1）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode401Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_401 =
            "https://example.invalid/packs/detergentmc-pack-1.13.1.zip";
    private static final String VALID_SHA1_401 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";
    private static final String URL_404 =
            "https://example.invalid/packs/detergentmc-pack-1.13.2.zip";
    private static final String VALID_SHA1_404 =
            "abababababababababababababababababababab";

    /** 最大オーバーロード：401 ペアまで渡す（404 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url401, String sha1401) {
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
                // 477（未設定）
                "", "",
                // 404（フォールバック先として設定）
                URL_404, VALID_SHA1_404,
                url401, sha1401,
                // 393（未設定）
                "", "",
                // 340（未設定）
                "", "",
                false, "");
    }

    @Test
    void valid401UrlAndSha1YieldPack401() {
        ResourcePackMode.Decision decision = decideFull(URL_401, VALID_SHA1_401);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack401());
        assertEquals(URL_401, decision.pack401().url());
        assertEquals(VALID_SHA1_401, decision.pack401().sha1());
        // packForProtocol は 401 クライアントに 401 パックを返す。
        assertEquals(URL_401, decision.packForProtocol(401).url());
        assertEquals(URL_404, decision.packForProtocol(404).url());
    }

    @Test
    void empty401UrlFallsBackTo404Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack401());
        assertEquals(URL_404, decision.packForProtocol(401).url());
    }

    @Test
    void invalid401Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_401, "not-a-sha1");
        assertNull(decision.pack401());
        assertTrue(decision.problem() != null && decision.problem().contains("401"),
                String.valueOf(decision.problem()));
        assertEquals(URL_404, decision.packForProtocol(401).url());
    }

    @Test
    void nonHttps401UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_401);
        assertNull(decision.pack401());
        assertTrue(decision.problem() != null && decision.problem().contains("401"),
                String.valueOf(decision.problem()));
    }
}
