package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 477（1.14）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode477Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_477 =
            "https://example.invalid/packs/detergentmc-pack-1.14.zip";
    private static final String VALID_SHA1_477 =
            "efefefefefefefefefefefefefefefefefefefef";
    private static final String URL_480 =
            "https://example.invalid/packs/detergentmc-pack-1.14.1.zip";
    private static final String VALID_SHA1_480 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";

    /** 最大オーバーロード：477 ペアまで渡す（480 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url477, String sha1477) {
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
                // 480（フォールバック先として設定）
                URL_480, VALID_SHA1_480,
                url477, sha1477,
                false, "");
    }

    @Test
    void valid477UrlAndSha1YieldPack477() {
        ResourcePackMode.Decision decision = decideFull(URL_477, VALID_SHA1_477);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack477());
        assertEquals(URL_477, decision.pack477().url());
        assertEquals(VALID_SHA1_477, decision.pack477().sha1());
        // packForProtocol は 477 クライアントに 477 パックを返す。
        assertEquals(URL_477, decision.packForProtocol(477).url());
        assertEquals(URL_480, decision.packForProtocol(480).url());
    }

    @Test
    void empty477UrlFallsBackTo480Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack477());
        assertEquals(URL_480, decision.packForProtocol(477).url());
    }

    @Test
    void invalid477Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_477, "not-a-sha1");
        assertNull(decision.pack477());
        assertTrue(decision.problem() != null && decision.problem().contains("477"),
                String.valueOf(decision.problem()));
        assertEquals(URL_480, decision.packForProtocol(477).url());
    }

    @Test
    void nonHttps477UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_477);
        assertNull(decision.pack477());
        assertTrue(decision.problem() != null && decision.problem().contains("477"),
                String.valueOf(decision.problem()));
    }
}
