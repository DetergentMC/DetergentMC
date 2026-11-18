// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 755（1.17）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-755} / {@code resource-pack.sha1-755}。
 */
class ResourcePackMode755Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_755 =
            "https://example.invalid/packs/detergentmc-pack-1.17.zip";
    private static final String VALID_SHA1_755 =
            "8888888888888888888888888888888888888888";
    private static final String URL_756 =
            "https://example.invalid/packs/detergentmc-pack-1.17.1.zip";
    private static final String VALID_SHA1_756 =
            "ffffffffffffffffffffffffffffffffffffffff";
    private static final String URL_757 =
            "https://example.invalid/packs/detergentmc-pack-1.18.zip";
    private static final String VALID_SHA1_757 =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String URL_759 =
            "https://example.invalid/packs/detergentmc-pack-1.19.zip";
    private static final String VALID_SHA1_759 =
            "dddddddddddddddddddddddddddddddddddddddd";
    private static final String URL_760 =
            "https://example.invalid/packs/detergentmc-pack-1.19.1.zip";
    private static final String VALID_SHA1_760 =
            "cccccccccccccccccccccccccccccccccccccccc";
    private static final String URL_761 =
            "https://example.invalid/packs/detergentmc-pack-1.19.3.zip";
    private static final String VALID_SHA1_761 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String URL_762 =
            "https://example.invalid/packs/detergentmc-pack-1.19.4.zip";
    private static final String VALID_SHA1_762 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String URL_763 =
            "https://example.invalid/packs/detergentmc-pack-1.20.1.zip";
    private static final String VALID_SHA1_763 =
            "9999999999999999999999999999999999999999";

    /** The full decide() overload: 761–775 pairs, then 760, 759, 757, 756, 755. */
    private static ResourcePackMode.Decision decideFull(
            String url760, String sha1760, String url759, String sha1759,
            String url757, String sha1757, String url756, String sha1756,
            String url755, String sha1755) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                url760, sha1760, url759, sha1759, url757, sha1757, url756, sha1756, url755, sha1755,
                false, "");
    }

    @Test
    void valid755UrlAndSha1YieldPack755() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759, URL_757, VALID_SHA1_757,
                URL_756, VALID_SHA1_756, URL_755, VALID_SHA1_755);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNotNull(decision.pack755());
        assertEquals(URL_755, decision.pack755().url());
        assertEquals(VALID_SHA1_755, decision.pack755().sha1());
        assertNotNull(decision.pack756());
        assertEquals(URL_756, decision.pack756().url());
        assertNotNull(decision.pack757());
        assertEquals(URL_757, decision.pack757().url());
        assertNotNull(decision.pack759());
        assertEquals(URL_759, decision.pack759().url());
        assertNotNull(decision.pack760());
        assertEquals(URL_760, decision.pack760().url());
        assertNotNull(decision.pack761());
        assertEquals(URL_761, decision.pack761().url());
        assertNotNull(decision.pack762());
        assertEquals(URL_762, decision.pack762().url());
        assertNotNull(decision.pack763());
        assertEquals(URL_763, decision.pack763().url());
        // packForProtocol picks the 755 pack for 755 clients, the
        // 756 pack for 756 clients, and the 765 pack for 765 clients.
        assertEquals(URL_755, decision.packForProtocol(755).url());
        assertEquals(URL_756, decision.packForProtocol(756).url());
        assertEquals(URL_757, decision.packForProtocol(757).url());
        assertEquals(URL_759, decision.packForProtocol(759).url());
        assertEquals(URL_760, decision.packForProtocol(760).url());
        assertEquals(URL_761, decision.packForProtocol(761).url());
        assertEquals(URL_762, decision.packForProtocol(762).url());
        assertEquals(URL_763, decision.packForProtocol(763).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty755UrlMeansNoPack755AndNoProblem() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759, URL_757, VALID_SHA1_757,
                URL_756, VALID_SHA1_756, "", "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack755());
        assertNotNull(decision.pack756());
        // Without a 755 pack, 755 clients fall back to the 756 pack.
        assertEquals(URL_756, decision.packForProtocol(755).url());
    }

    @Test
    void invalid755Sha1WarnsAndFallsBackTo756Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759, URL_757, VALID_SHA1_757,
                URL_756, VALID_SHA1_756, URL_755, "not-a-sha1");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 755 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack755());
        assertNotNull(decision.pack756());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-755"), decision.problem());
        assertEquals(URL_756, decision.packForProtocol(755).url());
    }

    @Test
    void nonHttps755UrlWarnsAndFallsBackTo756Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759, URL_757, VALID_SHA1_757,
                URL_756, VALID_SHA1_756,
                "http://example.invalid/packs/detergentmc-pack-1.17.zip",
                VALID_SHA1_755);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack755());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-755"), decision.problem());
        assertEquals(URL_756, decision.packForProtocol(755).url());
    }

    @Test
    void decideWithout755StillWorks() {
        // The 40-argument decide (without url-755 / sha1-755) keeps
        // working and yields no 755 pack; 755 clients fall back to
        // the 756 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759, URL_757, VALID_SHA1_757,
                URL_756, VALID_SHA1_756,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack755());
        assertNotNull(decision.pack756());
        assertEquals(URL_756, decision.packForProtocol(755).url());
    }

    @Test
    void middleDecideWithout755FallsBackTo756Pack() {
        // The middle overload (with url-756 / sha1-756, without url-755 /
        // sha1-755, and without url-759 / sha1-759 and url-760 / sha1-760)
        // keeps working: 755 clients fall back to the 756 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", URL_757, VALID_SHA1_757, URL_756, VALID_SHA1_756,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNull(decision.pack755());
        assertNotNull(decision.pack756());
        assertNotNull(decision.pack757());
        assertNull(decision.pack759());
        assertNull(decision.pack760());
        assertEquals(URL_756, decision.packForProtocol(755).url());
    }
}
