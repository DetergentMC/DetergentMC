// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 758（1.18.2）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-758} / {@code resource-pack.sha1-758}。
 */
class ResourcePackMode758Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_758 =
            "https://example.invalid/packs/detergentmc-pack-1.18.2.zip";
    private static final String VALID_SHA1_758 =
            "ffffffffffffffffffffffffffffffffffffffff";
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

    /** The full decide() overload: 761–776 pairs, then 760, 759, then 758. */
    private static ResourcePackMode.Decision decideFull(
            String url760, String sha1760, String url759, String sha1759,
            String url758, String sha1758) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                url760, sha1760, url759, sha1759, url758, sha1758,
                false, "");
    }

    @Test
    void valid758UrlAndSha1YieldPack758() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759, URL_758, VALID_SHA1_758);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNotNull(decision.pack758());
        assertEquals(URL_758, decision.pack758().url());
        assertEquals(VALID_SHA1_758, decision.pack758().sha1());
        assertNotNull(decision.pack759());
        assertEquals(URL_759, decision.pack759().url());
        // packForProtocol picks the 758 pack for 758 clients, the
        // 759 pack for 759 clients, and the 765 pack for 765 clients.
        assertEquals(URL_758, decision.packForProtocol(758).url());
        assertEquals(URL_759, decision.packForProtocol(759).url());
        assertEquals(URL_760, decision.packForProtocol(760).url());
        assertEquals(URL_761, decision.packForProtocol(761).url());
        assertEquals(URL_762, decision.packForProtocol(762).url());
        assertEquals(URL_763, decision.packForProtocol(763).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty758UrlMeansNoPack758AndNoProblem() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759, "", "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack758());
        assertNotNull(decision.pack759());
        // Without a 758 pack, 758 clients fall back to the 759 pack.
        assertEquals(URL_759, decision.packForProtocol(758).url());
    }

    @Test
    void invalid758Sha1WarnsAndFallsBackTo759Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759, URL_758, "not-a-sha1");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 758 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack758());
        assertNotNull(decision.pack759());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-758"), decision.problem());
        assertEquals(URL_759, decision.packForProtocol(758).url());
    }

    @Test
    void nonHttps758UrlWarnsAndFallsBackTo759Pack() {
        ResourcePackMode.Decision decision = decideFull(
                URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759,
                "http://example.invalid/packs/detergentmc-pack-1.18.2.zip",
                VALID_SHA1_758);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack758());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-758"), decision.problem());
        assertEquals(URL_759, decision.packForProtocol(758).url());
    }

    @Test
    void shortDecideWithout758StillWorks() {
        // The older 4-argument decide (without url-758 / sha1-758)
        // keeps working and yields no 758 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack758());
        // Without a 758 pack, 758 clients fall back to the main pack
        // (no 759 / 760 / 761 / 762 / 763 pack is configured either).
        assertEquals(URL, decision.packForProtocol(758).url());
    }

    @Test
    void middleDecideWithout758FallsBackTo759Pack() {
        // The 17-pair overload (with url-759 / sha1-759, without url-758 /
        // sha1-758) keeps working: 758 clients fall back to the 759 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", URL_760, VALID_SHA1_760, URL_759, VALID_SHA1_759,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNull(decision.pack758());
        assertNotNull(decision.pack759());
        assertEquals(URL_759, decision.packForProtocol(758).url());
    }
}
