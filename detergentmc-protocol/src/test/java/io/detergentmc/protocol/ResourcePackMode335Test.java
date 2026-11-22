package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 335（1.12）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode335Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_335 =
            "https://example.invalid/packs/detergentmc-pack-1.12.zip";
    private static final String VALID_SHA1_335 =
            "efefefefefefefefefefefefefefefefefefefef";
    private static final String URL_338 =
            "https://example.invalid/packs/detergentmc-pack-1.12.1.zip";
    private static final String VALID_SHA1_338 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";

    /** 最大オーバーロード：335 ペアまで渡す（338 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url335, String sha1335) {
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
                // 340（未設定）
                "", "",
                // 338（フォールバック先として設定）
                URL_338, VALID_SHA1_338,
                url335, sha1335,
                false, "");
    }

    @Test
    void valid335UrlAndSha1YieldPack335() {
        ResourcePackMode.Decision decision = decideFull(URL_335, VALID_SHA1_335);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack335());
        assertEquals(URL_335, decision.pack335().url());
        assertEquals(VALID_SHA1_335, decision.pack335().sha1());
        // packForProtocol は 335 クライアントに 335 パックを返す。
        assertEquals(URL_335, decision.packForProtocol(335).url());
        assertEquals(URL_338, decision.packForProtocol(338).url());
    }

    @Test
    void empty335UrlFallsBackTo338Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack335());
        assertEquals(URL_338, decision.packForProtocol(335).url());
    }

    @Test
    void invalid335Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_335, "not-a-sha1");
        assertNull(decision.pack335());
        assertTrue(decision.problem() != null && decision.problem().contains("335"),
                String.valueOf(decision.problem()));
        assertEquals(URL_338, decision.packForProtocol(335).url());
    }

    @Test
    void nonHttps335UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_335);
        assertNull(decision.pack335());
        assertTrue(decision.problem() != null && decision.problem().contains("335"),
                String.valueOf(decision.problem()));
    }
}
