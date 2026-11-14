package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Mode-selection tests for {@link ResourcePackMode}: loopback is the
 * default, only {@code https://} URLs select external mode, plain
 * {@code http://} is refused with a loopback fallback, and a missing
 * or malformed SHA-1 leaves external mode without a pack to offer.
 */
class ResourcePackModeTest {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";

    @Test
    void emptyUrlSelectsLoopbackByDefault() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide("", "", false, "");
        assertEquals(ResourcePackMode.Kind.LOOPBACK, decision.kind());
        assertNull(decision.pack());
        assertNull(decision.problem());
    }

    @Test
    void nullUrlSelectsLoopbackByDefault() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(null, null, false, null);
        assertEquals(ResourcePackMode.Kind.LOOPBACK, decision.kind());
        assertNull(decision.pack());
        assertNull(decision.problem());
    }

    @Test
    void blankUrlSelectsLoopbackByDefault() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide("   ", "", false, "");
        assertEquals(ResourcePackMode.Kind.LOOPBACK, decision.kind());
        assertNull(decision.pack());
        assertNull(decision.problem());
    }

    @Test
    void httpsUrlWithValidSha1SelectsExternal() {
        ResourcePackMode.Decision decision =
                ResourcePackMode.decide(URL, VALID_SHA1, true, "resource-pack.prompt");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertEquals(VALID_SHA1, decision.pack().sha1());
        assertTrue(decision.pack().required());
        assertEquals("resource-pack.prompt", decision.pack().promptKey());
    }

    @Test
    void httpsUrlNormalizesUpperCaseSha1ToLowerCase() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, "0123456789ABCDEF0123456789ABCDEF01234567", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertEquals(VALID_SHA1, decision.pack().sha1());
    }

    @Test
    void httpsUrlWithBlankPromptKeyUsesDefaultPrompt() {
        ResourcePackMode.Decision decision =
                ResourcePackMode.decide(URL, VALID_SHA1, false, "  ");
        assertNotNull(decision.pack());
        assertEquals(ResourcePackServer.DEFAULT_PROMPT_KEY, decision.pack().promptKey());
    }

    @Test
    void httpsUrlWithCustomPromptKeyKeepsIt() {
        ResourcePackMode.Decision decision =
                ResourcePackMode.decide(URL, VALID_SHA1, false, "my.pack.prompt");
        assertNotNull(decision.pack());
        assertEquals("my.pack.prompt", decision.pack().promptKey());
    }

    @Test
    void httpsUrlWithMissingSha1HasNoPackToOffer() {
        ResourcePackMode.Decision decision =
                ResourcePackMode.decide(URL, "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.pack());
        assertNotNull(decision.problem());
    }

    @Test
    void httpsUrlWithNullSha1HasNoPackToOffer() {
        ResourcePackMode.Decision decision =
                ResourcePackMode.decide(URL, null, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.pack());
        assertNotNull(decision.problem());
    }

    @Test
    void httpsUrlWithShortSha1HasNoPackToOffer() {
        ResourcePackMode.Decision decision =
                ResourcePackMode.decide(URL, "abc123", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.pack());
        assertNotNull(decision.problem());
    }

    @Test
    void httpsUrlWithNonHexSha1HasNoPackToOffer() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, "0123456789abcdef0123456789abcdef0123456g", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.pack());
        assertNotNull(decision.problem());
    }

    @Test
    void httpUrlIsRefusedAndFallsBackToLoopback() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                "http://example.invalid/packs/detergentmc-pack.zip", VALID_SHA1, false, "");
        assertEquals(ResourcePackMode.Kind.LOOPBACK, decision.kind());
        assertNull(decision.pack());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("https://"), decision.problem());
    }

    @Test
    void otherSchemeIsRefusedAndFallsBackToLoopback() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                "ftp://example.invalid/detergentmc-pack.zip", VALID_SHA1, false, "");
        assertEquals(ResourcePackMode.Kind.LOOPBACK, decision.kind());
        assertNull(decision.pack());
        assertNotNull(decision.problem());
    }

    @Test
    void isValidSha1AcceptsFortyHexDigits() {
        assertTrue(ResourcePackMode.isValidSha1(VALID_SHA1));
        assertTrue(ResourcePackMode.isValidSha1("0123456789ABCDEF0123456789ABCDEF01234567"));
        assertFalse(ResourcePackMode.isValidSha1(null));
        assertFalse(ResourcePackMode.isValidSha1(""));
        assertFalse(ResourcePackMode.isValidSha1("abc"));
        assertFalse(ResourcePackMode.isValidSha1(VALID_SHA1 + "0"));
        assertFalse(ResourcePackMode.isValidSha1(
                "0123456789abcdef0123456789abcdef0123456g"));
    }

    // ------------------------------------------------------------------
    // 1.20.1 variant (url-763 / sha1-763, external mode only)
    // ------------------------------------------------------------------

    private static final String URL_763 =
            "https://example.invalid/packs/detergentmc-pack-1.20.1.zip";
    private static final String VALID_SHA1_763 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void valid763UrlAndSha1YieldPack763() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, URL_763, VALID_SHA1_763, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNotNull(decision.pack763());
        assertEquals(URL_763, decision.pack763().url());
        assertEquals(VALID_SHA1_763, decision.pack763().sha1());
        assertFalse(decision.pack763().required());
    }

    @Test
    void empty763UrlMeansNoPack763AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack763());
    }

    @Test
    void invalid763Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, URL_763, "not-a-sha1", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 763 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack763());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-763"), decision.problem());
    }

    @Test
    void http763UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null,
                "http://example.invalid/packs/detergentmc-pack-1.20.1.zip",
                VALID_SHA1_763, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack763());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-763"), decision.problem());
    }

    @Test
    void badMainSha1LeavesNoPackAtAllEvenWithValid763() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, "bad", "", "", null, null, URL_763, VALID_SHA1_763, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.pack());
        assertNull(decision.pack763());
        assertNotNull(decision.problem());
    }

    // ------------------------------------------------------------------
    // 1.20.2 variant (url-764 / sha1-764, external mode only)
    // The 1.20.2 pack is pack_format 18, not 15: the 763 pack is never
    // shared with 764 clients.
    // ------------------------------------------------------------------

    private static final String URL_764 =
            "https://example.invalid/packs/detergentmc-pack-1.20.2.zip";
    private static final String VALID_SHA1_764 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void valid764UrlAndSha1YieldPack764() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, URL_763, VALID_SHA1_763,
                URL_764, VALID_SHA1_764, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNotNull(decision.pack763());
        assertEquals(URL_763, decision.pack763().url());
        assertNotNull(decision.pack764());
        assertEquals(URL_764, decision.pack764().url());
        assertEquals(VALID_SHA1_764, decision.pack764().sha1());
        assertFalse(decision.pack764().required());
    }

    @Test
    void empty764UrlMeansNoPack764AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, URL_763, VALID_SHA1_763, "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack763());
        assertNull(decision.pack764());
    }

    @Test
    void invalid764Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, "", "", URL_764, "not-a-sha1", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 764 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack764());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-764"), decision.problem());
    }

    @Test
    void http764UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, "", "",
                "http://example.invalid/packs/detergentmc-pack-1.20.2.zip",
                VALID_SHA1_764, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack764());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-764"), decision.problem());
    }

    @Test
    void both763And764ProblemsAreReportedTogether() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, URL_763, "bad", URL_764, "also-bad", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack763());
        assertNull(decision.pack764());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-763"), decision.problem());
        assertTrue(decision.problem().contains("sha1-764"), decision.problem());
    }

    // ------------------------------------------------------------------
    // 1.20.5 / 1.20.6 variant (url-766 / sha1-766, external mode only)
    // One pack serves both versions: protocol 766, pack_format 32.
    // The 766 pack is never shared with other protocols, and the 765
    // pack is never shared with 766 clients.
    // ------------------------------------------------------------------

    private static final String URL_766 =
            "https://example.invalid/packs/detergentmc-pack-1.20.5.zip";
    private static final String VALID_SHA1_766 =
            "cccccccccccccccccccccccccccccccccccccccc";

    @Test
    void valid766UrlAndSha1YieldPack766() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, URL_763, VALID_SHA1_763,
                URL_764, VALID_SHA1_764, URL_766, VALID_SHA1_766, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNotNull(decision.pack763());
        assertEquals(URL_763, decision.pack763().url());
        assertNotNull(decision.pack764());
        assertEquals(URL_764, decision.pack764().url());
        assertNotNull(decision.pack766());
        assertEquals(URL_766, decision.pack766().url());
        assertEquals(VALID_SHA1_766, decision.pack766().sha1());
        assertFalse(decision.pack766().required());
        // packForProtocol picks the 766 pack for 766 clients, and the
        // 765 pack for 765 clients.
        assertEquals(URL_766, decision.packForProtocol(766).url());
        assertEquals(URL, decision.packForProtocol(765).url());
        assertEquals(URL_763, decision.packForProtocol(763).url());
        assertEquals(URL_764, decision.packForProtocol(764).url());
    }

    @Test
    void empty766UrlMeansNoPack766AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, URL_763, VALID_SHA1_763,
                URL_764, VALID_SHA1_764, "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack763());
        assertNotNull(decision.pack764());
        assertNull(decision.pack766());
        // Without a 766 pack, 766 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(766).url());
    }

    @Test
    void invalid766Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, "", "", "", "", URL_766, "not-a-sha1", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 766 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack766());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-766"), decision.problem());
        assertEquals(URL, decision.packForProtocol(766).url());
    }

    @Test
    void http766UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, "", "", "", "",
                "http://example.invalid/packs/detergentmc-pack-1.20.5.zip",
                VALID_SHA1_766, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack766());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-766"), decision.problem());
    }

    @Test
    void allThreeVariantProblemsAreReportedTogether() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", null, null, URL_763, "bad", URL_764, "also-bad",
                URL_766, "bad-too", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack763());
        assertNull(decision.pack764());
        assertNull(decision.pack766());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-763"), decision.problem());
        assertTrue(decision.problem().contains("sha1-764"), decision.problem());
        assertTrue(decision.problem().contains("sha1-766"), decision.problem());
    }
}
