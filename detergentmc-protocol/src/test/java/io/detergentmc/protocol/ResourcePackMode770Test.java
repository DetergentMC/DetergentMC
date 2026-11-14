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
 * 770（1.21.5）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-770} / {@code resource-pack.sha1-770}。
 */
class ResourcePackMode770Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_770 =
            "https://example.invalid/packs/detergentmc-pack-1.21.5.zip";
    private static final String VALID_SHA1_770 =
            "ffffffffffffffffffffffffffffffffffffffff";

    @Test
    void valid770UrlAndSha1YieldPack770() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack763());
        assertNull(decision.pack764());
        assertNull(decision.pack766());
        assertNull(decision.pack767());
        assertNull(decision.pack768());
        assertNull(decision.pack769());
        assertNotNull(decision.pack770());
        assertEquals(URL_770, decision.pack770().url());
        assertEquals(VALID_SHA1_770, decision.pack770().sha1());
        assertFalse(decision.pack770().required());
        // packForProtocol picks the 770 pack for 770 clients, and the
        // 765 pack for 765 clients.
        assertEquals(URL_770, decision.packForProtocol(770).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty770UrlMeansNoPack770AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack770());
        // Without a 770 pack, 770 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(770).url());
    }

    @Test
    void invalid770Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, "not-a-sha1", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 770 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack770());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-770"), decision.problem());
    }

    @Test
    void nonHttps770UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                "http://example.invalid/packs/detergentmc-pack-1.21.5.zip",
                VALID_SHA1_770, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack770());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-770"), decision.problem());
    }

    @Test
    void sixteenArgDecideWithout770StillWorks() {
        // The older 16-argument decide (without url-770 / sha1-770)
        // keeps working and yields no 770 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack770());
        assertEquals(URL, decision.packForProtocol(770).url());
    }
}
