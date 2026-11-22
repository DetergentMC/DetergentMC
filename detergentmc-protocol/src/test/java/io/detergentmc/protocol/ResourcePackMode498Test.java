package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 498（1.14.4）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode498Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_498 =
            "https://example.invalid/packs/detergentmc-pack-1.14.4.zip";
    private static final String VALID_SHA1_498 =
            "efefefefefefefefefefefefefefefefefefefef";
    private static final String URL_573 =
            "https://example.invalid/packs/detergentmc-pack-1.15.zip";
    private static final String VALID_SHA1_573 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";

    /** 最大オーバーロード：498 ペアまで渡す（573 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(
            String url573, String sha1573, String url498, String sha1498) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1,
                // 761・762・763・764・766〜776（15ペア＝30個は未設定）
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                // 760・759・758（未設定）
                "", "", "", "", "", "",
                // 578・575（未設定）
                "", "", "", "",
                url573, sha1573,
                url498, sha1498,
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
    void valid498UrlAndSha1YieldPack498() {
        ResourcePackMode.Decision decision = decideFull(
                URL_573, VALID_SHA1_573, URL_498, VALID_SHA1_498);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack498());
        assertEquals(URL_498, decision.pack498().url());
        assertEquals(VALID_SHA1_498, decision.pack498().sha1());
        // packForProtocol は 498 クライアントに 498 パックを返す。
        assertEquals(URL_498, decision.packForProtocol(498).url());
        assertEquals(URL_573, decision.packForProtocol(573).url());
    }

    @Test
    void empty498UrlFallsBackTo573Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_573, VALID_SHA1_573, "", "");
        assertNull(decision.pack498());
        assertEquals(URL_573, decision.packForProtocol(498).url());
    }

    @Test
    void invalid498Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_573, VALID_SHA1_573, URL_498, "not-a-sha1");
        assertNull(decision.pack498());
        assertTrue(decision.problem() != null && decision.problem().contains("498"),
                String.valueOf(decision.problem()));
        assertEquals(URL_573, decision.packForProtocol(498).url());
    }

    @Test
    void nonHttps498UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_573, VALID_SHA1_573,
                "http://example.invalid/packs/detergentmc-pack-1.14.4.zip",
                VALID_SHA1_498);
        assertNull(decision.pack498());
        assertTrue(decision.problem() != null && decision.problem().contains("url-498"),
                String.valueOf(decision.problem()));
    }
}
