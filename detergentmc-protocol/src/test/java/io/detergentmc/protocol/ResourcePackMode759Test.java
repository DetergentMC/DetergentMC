// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 759（1.19）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-759} / {@code resource-pack.sha1-759}。
 */
class ResourcePackMode759Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_759 =
            "https://example.invalid/packs/detergentmc-pack-1.19.zip";
    private static final String VALID_SHA1_759 =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String URL_760 =
            "https://example.invalid/packs/detergentmc-pack-1.19.1.zip";
    private static final String VALID_SHA1_760 =
            "dddddddddddddddddddddddddddddddddddddddd";
    private static final String URL_761 =
            "https://example.invalid/packs/detergentmc-pack-1.19.3.zip";
    private static final String VALID_SHA1_761 =
            "cccccccccccccccccccccccccccccccccccccccc";
    private static final String URL_762 =
            "https://example.invalid/packs/detergentmc-pack-1.19.4.zip";
    private static final String VALID_SHA1_762 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String URL_763 =
            "https://example.invalid/packs/detergentmc-pack-1.20.1.zip";
    private static final String VALID_SHA1_763 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    /** The full decide() overload: 761–776 pairs, then 760, then 759. */
    private static ResourcePackMode.Decision decideFull(
            String url760, String sha1760, String url759, String sha1759) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                url760, sha1760, url759, sha1759,
                false, "");
    }

    @Test
    void valid759UrlAndSha1YieldPack759() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNotNull(decision.pack759());
        assertEquals(URL_759, decision.pack759().url());
        assertEquals(VALID_SHA1_759, decision.pack759().sha1());
        assertNotNull(decision.pack760());
        assertEquals(URL_760, decision.pack760().url());
        assertNotNull(decision.pack761());
        assertEquals(URL_761, decision.pack761().url());
        assertNotNull(decision.pack762());
        assertEquals(URL_762, decision.pack762().url());
        assertNotNull(decision.pack763());
        assertEquals(URL_763, decision.pack763().url());
        // packForProtocol picks the 759 pack for 759 clients, the
        // 760 pack for 760 clients, and the 765 pack for 765 clients.
        assertEquals(URL_759, decision.packForProtocol(759).url());
        assertEquals(URL_760, decision.packForProtocol(760).url());
        assertEquals(URL_761, decision.packForProtocol(761).url());
        assertEquals(URL_762, decision.packForProtocol(762).url());
        assertEquals(URL_763, decision.packForProtocol(763).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty759UrlMeansNoPack759AndNoProblem() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, "", "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack759());
        assertNotNull(decision.pack760());
        // Without a 759 pack, 759 clients fall back to the 760 pack.
        assertEquals(URL_760, decision.packForProtocol(759).url());
    }

    @Test
    void invalid759Sha1WarnsAndFallsBackTo760Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, "not-a-sha1");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 759 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack759());
        assertNotNull(decision.pack760());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-759"), decision.problem());
        assertEquals(URL_760, decision.packForProtocol(759).url());
    }

    @Test
    void nonHttps759UrlWarnsAndFallsBackTo760Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760,
                "http://example.invalid/packs/detergentmc-pack-1.19.zip",
                VALID_SHA1_759);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack759());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-759"), decision.problem());
        assertEquals(URL_760, decision.packForProtocol(759).url());
    }

    @Test
    void shortDecideWithout759StillWorks() {
        // The older 4-argument decide (without url-759 / sha1-759)
        // keeps working and yields no 759 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack759());
        // Without a 759 pack, 759 clients fall back to the main pack
        // (no 760 / 761 / 762 / 763 pack is configured either).
        assertEquals(URL, decision.packForProtocol(759).url());
    }

    @Test
    void middleDecideWithout759FallsBackTo760Pack() {
        // The middle overload (with url-760 / sha1-760, without url-759 /
        // sha1-759) keeps working: 759 clients fall back to the 760 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", URL_760, VALID_SHA1_760,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNull(decision.pack759());
        assertNotNull(decision.pack760());
        assertEquals(URL_760, decision.packForProtocol(759).url());
    }
}
