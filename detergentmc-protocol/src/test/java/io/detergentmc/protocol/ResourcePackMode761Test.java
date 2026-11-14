// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 761（1.19.3）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-761} / {@code resource-pack.sha1-761}。
 */
class ResourcePackMode761Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
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

    @Test
    void valid761UrlAndSha1YieldPack761() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNotNull(decision.pack761());
        assertEquals(URL_761, decision.pack761().url());
        assertEquals(VALID_SHA1_761, decision.pack761().sha1());
        assertFalse(decision.pack761().required());
        assertNotNull(decision.pack762());
        assertEquals(URL_762, decision.pack762().url());
        assertEquals(VALID_SHA1_762, decision.pack762().sha1());
        assertNotNull(decision.pack763());
        assertEquals(URL_763, decision.pack763().url());
        assertEquals(VALID_SHA1_763, decision.pack763().sha1());
        // packForProtocol picks the 761 pack for 761 clients, the
        // 762 pack for 762 clients, and the 765 pack for 765 clients.
        assertEquals(URL_761, decision.packForProtocol(761).url());
        assertEquals(URL_762, decision.packForProtocol(762).url());
        assertEquals(URL_763, decision.packForProtocol(763).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty761UrlMeansNoPack761AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack761());
        assertNotNull(decision.pack762());
        // Without a 761 pack, 761 clients fall back to the 762 pack.
        assertEquals(URL_762, decision.packForProtocol(761).url());
    }

    @Test
    void invalid761Sha1WarnsAndFallsBackTo762Pack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, URL_761, "not-a-sha1", URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 761 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack761());
        assertNotNull(decision.pack762());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-761"), decision.problem());
        assertEquals(URL_762, decision.packForProtocol(761).url());
    }

    @Test
    void nonHttps761UrlWarnsAndFallsBackTo762Pack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1,
                "http://example.invalid/packs/detergentmc-pack-1.19.3.zip",
                VALID_SHA1_761, URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack761());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-761"), decision.problem());
        assertEquals(URL_762, decision.packForProtocol(761).url());
    }

    @Test
    void shortDecideWithout761StillWorks() {
        // The older 4-argument decide (without url-761 / sha1-761)
        // keeps working and yields no 761 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack761());
        // Without a 761 pack, 761 clients fall back to the main pack
        // (no 762 / 763 pack is configured either).
        assertEquals(URL, decision.packForProtocol(761).url());
    }
}
