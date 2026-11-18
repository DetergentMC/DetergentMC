package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 575（1.15.1）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode575Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_575 =
            "https://example.invalid/packs/detergentmc-pack-1.15.1.zip";
    private static final String VALID_SHA1_575 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";
    private static final String URL_578 =
            "https://example.invalid/packs/detergentmc-pack-1.15.2.zip";
    private static final String VALID_SHA1_578 =
            "abababababababababababababababababababab";

    /** 最大オーバーロード：575 ペアまで渡す（758 は未設定）。 */
    private static ResourcePackMode.Decision decideFull(
            String url578, String sha1578, String url575, String sha1575) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1,
                // 761・762・763・764・766〜776（15ペア＝30個は未設定）
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                // 760・759・758（未設定）
                "", "", "", "", "", "",
                url578, sha1578, url575, sha1575,
                // 573（未設定）
                "", "",
                // 498（未設定）
                "", "",
                // 490（未設定）
                "", "",
                // 485（未設定）
                "", "",
                // 480（未設定）
                "", "",
                // 477（未設定）
                "", "",
                false, "");
    }

    @Test
    void valid575UrlAndSha1YieldPack575() {
        ResourcePackMode.Decision decision = decideFull(
                URL_578, VALID_SHA1_578, URL_575, VALID_SHA1_575);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack575());
        assertEquals(URL_575, decision.pack575().url());
        assertEquals(VALID_SHA1_575, decision.pack575().sha1());
        // packForProtocol は 575 クライアントに 575 パックを返す。
        assertEquals(URL_575, decision.packForProtocol(575).url());
        assertEquals(URL_578, decision.packForProtocol(578).url());
    }

    @Test
    void empty575UrlFallsBackTo578Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_578, VALID_SHA1_578, "", "");
        assertNull(decision.pack575());
        assertEquals(URL_578, decision.packForProtocol(575).url());
    }

    @Test
    void invalid575Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_578, VALID_SHA1_578, URL_575, "not-a-sha1");
        assertNull(decision.pack575());
        assertTrue(decision.problem() != null && decision.problem().contains("575"),
                String.valueOf(decision.problem()));
        assertEquals(URL_578, decision.packForProtocol(575).url());
    }

    @Test
    void nonHttps575UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_578, VALID_SHA1_578,
                "http://example.invalid/packs/detergentmc-pack-1.15.1.zip",
                VALID_SHA1_575);
        assertNull(decision.pack575());
        assertTrue(decision.problem() != null && decision.problem().contains("url-575"),
                String.valueOf(decision.problem()));
    }
}
