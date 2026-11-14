package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 767（1.21／1.21.1）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-767} / {@code resource-pack.sha1-767}。
 */
class ResourcePackMode767Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_767 =
            "https://example.invalid/packs/detergentmc-pack-1.21.zip";
    private static final String VALID_SHA1_767 =
            "dddddddddddddddddddddddddddddddddddddddd";

    @Test
    void valid767UrlAndSha1YieldPack767() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", URL_767, VALID_SHA1_767,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack763());
        assertNull(decision.pack764());
        assertNull(decision.pack766());
        assertNotNull(decision.pack767());
        assertEquals(URL_767, decision.pack767().url());
        assertEquals(VALID_SHA1_767, decision.pack767().sha1());
        assertFalse(decision.pack767().required());
        // packForProtocol picks the 767 pack for 767 clients, and the
        // 765 pack for 765 clients. One pack serves both 1.21 and 1.21.1.
        assertEquals(URL_767, decision.packForProtocol(767).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty767UrlMeansNoPack767AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack767());
        // Without a 767 pack, 767 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(767).url());
    }

    @Test
    void invalid767Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", URL_767, "not-a-sha1", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 767 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack767());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-767"), decision.problem());
    }

    @Test
    void nonHttps767UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "",
                "http://example.invalid/packs/detergentmc-pack-1.21.zip",
                VALID_SHA1_767, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack767());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-767"), decision.problem());
    }

    @Test
    void tenArgDecideWithout767StillWorks() {
        // The older 10-argument decide (without url-767 / sha1-767)
        // keeps working and yields no 767 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack767());
        assertEquals(URL, decision.packForProtocol(767).url());
    }
}
