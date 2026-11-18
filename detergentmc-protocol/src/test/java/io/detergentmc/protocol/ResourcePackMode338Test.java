package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 338（1.12.1）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode338Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_338 =
            "https://example.invalid/packs/detergentmc-pack-1.12.1.zip";
    private static final String VALID_SHA1_338 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";
    private static final String URL_340 =
            "https://example.invalid/packs/detergentmc-pack-1.12.2.zip";
    private static final String VALID_SHA1_340 =
            "abababababababababababababababababababab";

    /** 最大オーバーロード：338 ペアまで渡す（340 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url338, String sha1338) {
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
                // 404（未設定）
                "", "",
                // 401（未設定）
                "", "",
                // 393（未設定）
                "", "",
                // 340（フォールバック先として設定）
                URL_340, VALID_SHA1_340,
                url338, sha1338,
                false, "");
    }

    @Test
    void valid338UrlAndSha1YieldPack338() {
        ResourcePackMode.Decision decision = decideFull(URL_338, VALID_SHA1_338);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack338());
        assertEquals(URL_338, decision.pack338().url());
        assertEquals(VALID_SHA1_338, decision.pack338().sha1());
        // packForProtocol は 338 クライアントに 338 パックを返す。
        assertEquals(URL_338, decision.packForProtocol(338).url());
        assertEquals(URL_340, decision.packForProtocol(340).url());
    }

    @Test
    void empty338UrlFallsBackTo340Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack338());
        assertEquals(URL_340, decision.packForProtocol(338).url());
    }

    @Test
    void invalid338Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_338, "not-a-sha1");
        assertNull(decision.pack338());
        assertTrue(decision.problem() != null && decision.problem().contains("338"),
                String.valueOf(decision.problem()));
        assertEquals(URL_340, decision.packForProtocol(338).url());
    }

    @Test
    void nonHttps338UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_338);
        assertNull(decision.pack338());
        assertTrue(decision.problem() != null && decision.problem().contains("338"),
                String.valueOf(decision.problem()));
    }
}
