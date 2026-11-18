package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 578（1.15.2）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode578Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_578 =
            "https://example.invalid/packs/detergentmc-pack-1.15.2.zip";
    private static final String VALID_SHA1_578 =
            "abababababababababababababababababababab";
    private static final String URL_758 =
            "https://example.invalid/packs/detergentmc-pack-1.18.2.zip";
    private static final String VALID_SHA1_758 =
            "ffffffffffffffffffffffffffffffffffffffff";

    /** 最大オーバーロード：578 ペアまで渡す。 */
    private static ResourcePackMode.Decision decideFull(
            String url758, String sha1758, String url578, String sha1578) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1,
                // 761・762・763・764・766〜776（15ペア＝30個は未設定）
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "",
                // 760・759（未設定）
                "", "", "", "",
                url758, sha1758, url578, sha1578,
                // 575（未設定）
                "", "",
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
    void valid578UrlAndSha1YieldPack578() {
        ResourcePackMode.Decision decision = decideFull(
                URL_758, VALID_SHA1_758, URL_578, VALID_SHA1_578);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack578());
        assertEquals(URL_578, decision.pack578().url());
        assertEquals(VALID_SHA1_578, decision.pack578().sha1());
        // packForProtocol は 578 クライアントに 578 パックを返す。
        assertEquals(URL_578, decision.packForProtocol(578).url());
        assertEquals(URL_758, decision.packForProtocol(758).url());
    }

    @Test
    void empty578UrlFallsBackTo758Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_758, VALID_SHA1_758, "", "");
        assertNull(decision.pack578());
        assertEquals(URL_758, decision.packForProtocol(578).url());
    }

    @Test
    void invalid578Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_758, VALID_SHA1_758, URL_578, "not-a-sha1");
        assertNull(decision.pack578());
        assertTrue(decision.problem() != null && decision.problem().contains("578"),
                String.valueOf(decision.problem()));
        assertEquals(URL_758, decision.packForProtocol(578).url());
    }
}
