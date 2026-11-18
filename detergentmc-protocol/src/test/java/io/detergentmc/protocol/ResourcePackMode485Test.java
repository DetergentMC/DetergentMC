package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 485（1.14.2）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode485Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_485 =
            "https://example.invalid/packs/detergentmc-pack-1.14.2.zip";
    private static final String VALID_SHA1_485 =
            "abababababababababababababababababababab";
    private static final String URL_490 =
            "https://example.invalid/packs/detergentmc-pack-1.14.3.zip";
    private static final String VALID_SHA1_490 =
            "efefefefefefefefefefefefefefefefefefefef";
    private static final String URL_573 =
            "https://example.invalid/packs/detergentmc-pack-1.15.zip";
    private static final String VALID_SHA1_573 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";

    /** 最大オーバーロード：485 ペアまで渡す（490 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(
            String url490, String sha1490, String url485, String sha1485) {
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
                // 498（未設定）
                "", "",
                url490, sha1490,
                url485, sha1485,
                // 480（未設定）
                "", "",
                // 477（未設定）
                "", "",
                false, "");
    }

    @Test
    void valid485UrlAndSha1YieldPack485() {
        ResourcePackMode.Decision decision = decideFull(
                URL_490, VALID_SHA1_490, URL_485, VALID_SHA1_485);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack485());
        assertEquals(URL_485, decision.pack485().url());
        assertEquals(VALID_SHA1_485, decision.pack485().sha1());
        // packForProtocol は 485 クライアントに 485 パックを返す。
        assertEquals(URL_485, decision.packForProtocol(485).url());
        assertEquals(URL_490, decision.packForProtocol(490).url());
    }

    @Test
    void empty485UrlFallsBackTo490Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_490, VALID_SHA1_490, "", "");
        assertNull(decision.pack485());
        assertEquals(URL_490, decision.packForProtocol(485).url());
    }

    @Test
    void invalid485Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_490, VALID_SHA1_490, URL_485, "not-a-sha1");
        assertNull(decision.pack485());
        assertTrue(decision.problem() != null && decision.problem().contains("485"),
                String.valueOf(decision.problem()));
        assertEquals(URL_490, decision.packForProtocol(485).url());
    }

    @Test
    void nonHttps485UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_490, VALID_SHA1_490,
                "http://example.invalid/packs/detergentmc-pack-1.14.2.zip",
                VALID_SHA1_485);
        assertNull(decision.pack485());
        assertTrue(decision.problem() != null && decision.problem().contains("url-485"),
                String.valueOf(decision.problem()));
    }

    @Test
    void loopbackWhenNoExternalUrl() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide("", "", false, "");
        assertEquals(ResourcePackMode.Kind.LOOPBACK, decision.kind());
        assertNull(decision.pack485());
    }
}
