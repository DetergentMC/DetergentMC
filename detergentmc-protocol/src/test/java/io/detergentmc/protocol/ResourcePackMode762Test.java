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
 * 762（1.19.4）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-762} / {@code resource-pack.sha1-762}。
 */
class ResourcePackMode762Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_762 =
            "https://example.invalid/packs/detergentmc-pack-1.19.4.zip";
    private static final String VALID_SHA1_762 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String URL_763 =
            "https://example.invalid/packs/detergentmc-pack-1.20.1.zip";
    private static final String VALID_SHA1_763 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void valid762UrlAndSha1YieldPack762() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", URL_762, VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNotNull(decision.pack762());
        assertEquals(URL_762, decision.pack762().url());
        assertEquals(VALID_SHA1_762, decision.pack762().sha1());
        assertFalse(decision.pack762().required());
        assertNotNull(decision.pack763());
        assertEquals(URL_763, decision.pack763().url());
        assertEquals(VALID_SHA1_763, decision.pack763().sha1());
        // packForProtocol picks the 762 pack for 762 clients, the
        // 763 pack for 763 clients, and the 765 pack for 765 clients.
        assertEquals(URL_762, decision.packForProtocol(762).url());
        assertEquals(URL_763, decision.packForProtocol(763).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty762UrlMeansNoPack762AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack762());
        assertNotNull(decision.pack763());
        // Without a 762 pack, 762 clients fall back to the 763 pack.
        assertEquals(URL_763, decision.packForProtocol(762).url());
    }

    @Test
    void invalid762Sha1WarnsAndFallsBackTo763Pack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", URL_762, "not-a-sha1", URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 762 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack762());
        assertNotNull(decision.pack763());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-762"), decision.problem());
        assertEquals(URL_763, decision.packForProtocol(762).url());
    }

    @Test
    void nonHttps762UrlWarnsAndFallsBackTo763Pack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "",
                "http://example.invalid/packs/detergentmc-pack-1.19.4.zip",
                VALID_SHA1_762, URL_763, VALID_SHA1_763,
                "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack762());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-762"), decision.problem());
        assertEquals(URL_763, decision.packForProtocol(762).url());
    }

    @Test
    void shortDecideWithout762StillWorks() {
        // The older 4-argument decide (without url-762 / sha1-762)
        // keeps working and yields no 762 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack762());
        // Without a 762 pack and without a 763 pack, 762 clients fall
        // back to the main pack.
        assertEquals(URL, decision.packForProtocol(762).url());
    }
}
