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
 * 769（1.21.4）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-769} / {@code resource-pack.sha1-769}。
 */
class ResourcePackMode769Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_769 =
            "https://example.invalid/packs/detergentmc-pack-1.21.4.zip";
    private static final String VALID_SHA1_769 =
            "ffffffffffffffffffffffffffffffffffffffff";

    @Test
    void valid769UrlAndSha1YieldPack769() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", URL_769, VALID_SHA1_769,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack763());
        assertNull(decision.pack764());
        assertNull(decision.pack766());
        assertNull(decision.pack767());
        assertNull(decision.pack768());
        assertNotNull(decision.pack769());
        assertEquals(URL_769, decision.pack769().url());
        assertEquals(VALID_SHA1_769, decision.pack769().sha1());
        assertFalse(decision.pack769().required());
        // packForProtocol picks the 769 pack for 769 clients, and the
        // 765 pack for 765 clients.
        assertEquals(URL_769, decision.packForProtocol(769).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty769UrlMeansNoPack769AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack769());
        // Without a 769 pack, 769 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(769).url());
    }

    @Test
    void invalid769Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", URL_769, "not-a-sha1",
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 769 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack769());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-769"), decision.problem());
    }

    @Test
    void nonHttps769UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                "http://example.invalid/packs/detergentmc-pack-1.21.4.zip",
                VALID_SHA1_769, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack769());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-769"), decision.problem());
    }

    @Test
    void fourteenArgDecideWithout769StillWorks() {
        // The older 14-argument decide (without url-769 / sha1-769)
        // keeps working and yields no 769 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack769());
        assertEquals(URL, decision.packForProtocol(769).url());
    }
}
