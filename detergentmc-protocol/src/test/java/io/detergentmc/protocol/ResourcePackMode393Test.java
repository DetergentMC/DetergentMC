package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 393（1.13）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode393Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_393 =
            "https://example.invalid/packs/detergentmc-pack-1.13.zip";
    private static final String VALID_SHA1_393 =
            "efefefefefefefefefefefefefefefefefefefef";
    private static final String URL_401 =
            "https://example.invalid/packs/detergentmc-pack-1.13.1.zip";
    private static final String VALID_SHA1_401 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";

    /** 最大オーバーロード：393 ペアまで渡す（401 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url393, String sha1393) {
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
                // 401（フォールバック先として設定）
                URL_401, VALID_SHA1_401,
                url393, sha1393,
                // 340（未設定）
                "", "",
                false, "");
    }

    @Test
    void valid393UrlAndSha1YieldPack393() {
        ResourcePackMode.Decision decision = decideFull(URL_393, VALID_SHA1_393);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack393());
        assertEquals(URL_393, decision.pack393().url());
        assertEquals(VALID_SHA1_393, decision.pack393().sha1());
        // packForProtocol は 393 クライアントに 393 パックを返す。
        assertEquals(URL_393, decision.packForProtocol(393).url());
        assertEquals(URL_401, decision.packForProtocol(401).url());
    }

    @Test
    void empty393UrlFallsBackTo401Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack393());
        assertEquals(URL_401, decision.packForProtocol(393).url());
    }

    @Test
    void invalid393Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_393, "not-a-sha1");
        assertNull(decision.pack393());
        assertTrue(decision.problem() != null && decision.problem().contains("393"),
                String.valueOf(decision.problem()));
        assertEquals(URL_401, decision.packForProtocol(393).url());
    }

    @Test
    void nonHttps393UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_393);
        assertNull(decision.pack393());
        assertTrue(decision.problem() != null && decision.problem().contains("393"),
                String.valueOf(decision.problem()));
    }
}
