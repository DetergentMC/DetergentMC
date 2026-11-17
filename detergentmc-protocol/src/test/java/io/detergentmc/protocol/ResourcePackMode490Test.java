package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 490（1.14.3）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode490Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_490 =
            "https://example.invalid/packs/detergentmc-pack-1.14.3.zip";
    private static final String VALID_SHA1_490 =
            "abababababababababababababababababababab";
    private static final String URL_498 =
            "https://example.invalid/packs/detergentmc-pack-1.14.4.zip";
    private static final String VALID_SHA1_498 =
            "efefefefefefefefefefefefefefefefefefefef";
    private static final String URL_573 =
            "https://example.invalid/packs/detergentmc-pack-1.15.zip";
    private static final String VALID_SHA1_573 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";

    /** 最大オーバーロード：490 ペアまで渡す（498 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(
            String url498, String sha1498, String url490, String sha1490) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1,
                // 761・762・763・764・766〜776（15ペア＝30個は未設定）
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                // 760・759・758（未設定）
                "", "", "", "", "", "",
                // 578・575・573（未設定）
                "", "", "", "",
                "", "",
                url498, sha1498,
                url490, sha1490,
                // 485（未設定）
                "", "",
                // 480（未設定）
                "", "",
                false, "");
    }

    @Test
    void valid490UrlAndSha1YieldPack490() {
        ResourcePackMode.Decision decision = decideFull(
                URL_498, VALID_SHA1_498, URL_490, VALID_SHA1_490);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack490());
        assertEquals(URL_490, decision.pack490().url());
        assertEquals(VALID_SHA1_490, decision.pack490().sha1());
        // packForProtocol は 490 クライアントに 490 パックを返す。
        assertEquals(URL_490, decision.packForProtocol(490).url());
        assertEquals(URL_498, decision.packForProtocol(498).url());
    }

    @Test
    void empty490UrlFallsBackTo498Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_498, VALID_SHA1_498, "", "");
        assertNull(decision.pack490());
        assertEquals(URL_498, decision.packForProtocol(490).url());
    }

    @Test
    void invalid490Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_498, VALID_SHA1_498, URL_490, "not-a-sha1");
        assertNull(decision.pack490());
        assertTrue(decision.problem() != null && decision.problem().contains("490"),
                String.valueOf(decision.problem()));
        assertEquals(URL_498, decision.packForProtocol(490).url());
    }

    @Test
    void nonHttps490UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_498, VALID_SHA1_498,
                "http://example.invalid/packs/detergentmc-pack-1.14.3.zip",
                VALID_SHA1_490);
        assertNull(decision.pack490());
        assertTrue(decision.problem() != null && decision.problem().contains("url-490"),
                String.valueOf(decision.problem()));
    }

    @Test
    void loopbackWhenNoExternalUrl() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide("", "", false, "");
        assertEquals(ResourcePackMode.Kind.LOOPBACK, decision.kind());
        assertNull(decision.pack490());
    }
}
