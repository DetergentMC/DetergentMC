package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 480（1.14.1）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode480Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_480 =
            "https://example.invalid/packs/detergentmc-pack-1.14.1.zip";
    private static final String VALID_SHA1_480 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";
    private static final String URL_485 =
            "https://example.invalid/packs/detergentmc-pack-1.14.2.zip";
    private static final String VALID_SHA1_485 =
            "abababababababababababababababababababab";

    /** 最大オーバーロード：480 ペアまで渡す（485 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url480, String sha1480) {
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
                // 498・490（未設定）
                "", "",
                "", "",
                // 485（フォールバック先として設定）
                URL_485, VALID_SHA1_485,
                url480, sha1480,
                // 477（未設定）
                "", "",
                false, "");
    }

    @Test
    void valid480UrlAndSha1YieldPack480() {
        ResourcePackMode.Decision decision = decideFull(URL_480, VALID_SHA1_480);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack480());
        assertEquals(URL_480, decision.pack480().url());
        assertEquals(VALID_SHA1_480, decision.pack480().sha1());
        // packForProtocol は 480 クライアントに 480 パックを返す。
        assertEquals(URL_480, decision.packForProtocol(480).url());
        assertEquals(URL_485, decision.packForProtocol(485).url());
    }

    @Test
    void empty480UrlFallsBackTo485Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack480());
        assertEquals(URL_485, decision.packForProtocol(480).url());
    }

    @Test
    void invalid480Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_480, "not-a-sha1");
        assertNull(decision.pack480());
        assertTrue(decision.problem() != null && decision.problem().contains("480"),
                String.valueOf(decision.problem()));
        assertEquals(URL_485, decision.packForProtocol(480).url());
    }

    @Test
    void nonHttps480UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                "http://example.invalid/packs/detergentmc-pack-1.14.1.zip",
                VALID_SHA1_480);
        assertNull(decision.pack480());
        assertTrue(decision.problem() != null && decision.problem().contains("url-480"),
                String.valueOf(decision.problem()));
    }

    @Test
    void loopbackWhenNoExternalUrl() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide("", "", false, "");
        assertEquals(ResourcePackMode.Kind.LOOPBACK, decision.kind());
        assertNull(decision.pack480());
    }
}
