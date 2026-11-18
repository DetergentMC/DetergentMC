package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 573（1.15）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode573Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_573 =
            "https://example.invalid/packs/detergentmc-pack-1.15.zip";
    private static final String VALID_SHA1_573 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";
    private static final String URL_578 =
            "https://example.invalid/packs/detergentmc-pack-1.15.2.zip";
    private static final String VALID_SHA1_578 =
            "abababababababababababababababababababab";

    /** 最大オーバーロード：573 ペアまで渡す（758・575 は未設定）。 */
    private static ResourcePackMode.Decision decideFull(
            String url578, String sha1578, String url573, String sha1573) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1,
                // 761・762・763・764・766〜776（15ペア＝30個は未設定）
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                // 760・759・758（未設定）
                "", "", "", "", "", "",
                url578, sha1578,
                // 575（未設定）
                "", "",
                url573, sha1573,
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
    void valid573UrlAndSha1YieldPack573() {
        ResourcePackMode.Decision decision = decideFull(
                URL_578, VALID_SHA1_578, URL_573, VALID_SHA1_573);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack573());
        assertEquals(URL_573, decision.pack573().url());
        assertEquals(VALID_SHA1_573, decision.pack573().sha1());
        // packForProtocol は 573 クライアントに 573 パックを返す。
        assertEquals(URL_573, decision.packForProtocol(573).url());
        assertEquals(URL_578, decision.packForProtocol(578).url());
    }

    @Test
    void empty573UrlFallsBackTo578Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_578, VALID_SHA1_578, "", "");
        assertNull(decision.pack573());
        assertEquals(URL_578, decision.packForProtocol(573).url());
    }

    @Test
    void invalid573Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_578, VALID_SHA1_578, URL_573, "not-a-sha1");
        assertNull(decision.pack573());
        assertTrue(decision.problem() != null && decision.problem().contains("573"),
                String.valueOf(decision.problem()));
        assertEquals(URL_578, decision.packForProtocol(573).url());
    }

    @Test
    void nonHttps573UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_578, VALID_SHA1_578,
                "http://example.invalid/packs/detergentmc-pack-1.15.zip",
                VALID_SHA1_573);
        assertNull(decision.pack573());
        assertTrue(decision.problem() != null && decision.problem().contains("url-573"),
                String.valueOf(decision.problem()));
    }
}
