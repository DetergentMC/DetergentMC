package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 340（1.12.2）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode340Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_340 =
            "https://example.invalid/packs/detergentmc-pack-1.12.2.zip";
    private static final String VALID_SHA1_340 =
            "abababababababababababababababababababab";
    private static final String URL_393 =
            "https://example.invalid/packs/detergentmc-pack-1.13.zip";
    private static final String VALID_SHA1_393 =
            "efefefefefefefefefefefefefefefefefefefef";

    /** 最大オーバーロード：340 ペアまで渡す（393 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url340, String sha1340) {
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
                // 393（フォールバック先として設定）
                URL_393, VALID_SHA1_393,
                url340, sha1340,
                false, "");
    }

    @Test
    void valid340UrlAndSha1YieldPack340() {
        ResourcePackMode.Decision decision = decideFull(URL_340, VALID_SHA1_340);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack340());
        assertEquals(URL_340, decision.pack340().url());
        assertEquals(VALID_SHA1_340, decision.pack340().sha1());
        // packForProtocol は 340 クライアントに 340 パックを返す。
        assertEquals(URL_340, decision.packForProtocol(340).url());
        assertEquals(URL_393, decision.packForProtocol(393).url());
    }

    @Test
    void empty340UrlFallsBackTo393Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack340());
        assertEquals(URL_393, decision.packForProtocol(340).url());
    }

    @Test
    void invalid340Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_340, "not-a-sha1");
        assertNull(decision.pack340());
        assertTrue(decision.problem() != null && decision.problem().contains("340"),
                String.valueOf(decision.problem()));
        assertEquals(URL_393, decision.packForProtocol(340).url());
    }

    @Test
    void nonHttps340UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_340);
        assertNull(decision.pack340());
        assertTrue(decision.problem() != null && decision.problem().contains("340"),
                String.valueOf(decision.problem()));
    }
}
