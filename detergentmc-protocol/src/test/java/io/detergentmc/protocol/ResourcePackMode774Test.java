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
 * 774（1.21.11）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-774} / {@code resource-pack.sha1-774}。
 */
class ResourcePackMode774Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_774 =
            "https://example.invalid/packs/detergentmc-pack-1.21.11.zip";
    private static final String VALID_SHA1_774 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String URL_773 =
            "https://example.invalid/packs/detergentmc-pack-1.21.9.zip";
    private static final String VALID_SHA1_773 =
            "ffffffffffffffffffffffffffffffffffffffff";
    private static final String URL_772 =
            "https://example.invalid/packs/detergentmc-pack-1.21.7.zip";
    private static final String VALID_SHA1_772 =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String URL_771 =
            "https://example.invalid/packs/detergentmc-pack-1.21.6.zip";
    private static final String VALID_SHA1_771 =
            "dddddddddddddddddddddddddddddddddddddddd";
    private static final String URL_770 =
            "https://example.invalid/packs/detergentmc-pack-1.21.5.zip";
    private static final String VALID_SHA1_770 =
            "cccccccccccccccccccccccccccccccccccccccc";

    @Test
    void valid774UrlAndSha1YieldPack774() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, URL_774, VALID_SHA1_774, false, "");
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
        assertNotNull(decision.pack771());
        assertEquals(URL_771, decision.pack771().url());
        assertEquals(VALID_SHA1_771, decision.pack771().sha1());
        assertNotNull(decision.pack772());
        assertEquals(URL_772, decision.pack772().url());
        assertEquals(VALID_SHA1_772, decision.pack772().sha1());
        assertNotNull(decision.pack773());
        assertEquals(URL_773, decision.pack773().url());
        assertEquals(VALID_SHA1_773, decision.pack773().sha1());
        assertNotNull(decision.pack774());
        assertEquals(URL_774, decision.pack774().url());
        assertEquals(VALID_SHA1_774, decision.pack774().sha1());
        assertFalse(decision.pack774().required());
        // packForProtocol picks the 774 pack for 774 clients, the
        // 773 pack for 773 clients, and the 765 pack for 765 clients.
        assertEquals(URL_774, decision.packForProtocol(774).url());
        assertEquals(URL_773, decision.packForProtocol(773).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty774UrlMeansNoPack774AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack771());
        assertNotNull(decision.pack772());
        assertNotNull(decision.pack773());
        assertNull(decision.pack774());
        // Without a 774 pack, 774 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(774).url());
    }

    @Test
    void invalid774Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, URL_774, "not-a-sha1", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 774 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack774());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-774"), decision.problem());
    }

    @Test
    void nonHttps774UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773,
                "http://example.invalid/packs/detergentmc-pack-1.21.11.zip",
                VALID_SHA1_774, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack774());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-774"), decision.problem());
    }

    @Test
    void twentyFourArgDecideWithout774StillWorks() {
        // The older 24-argument decide (without url-774 / sha1-774)
        // keeps working and yields no 774 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack770());
        assertNotNull(decision.pack771());
        assertNotNull(decision.pack772());
        assertNotNull(decision.pack773());
        assertNull(decision.pack774());
        assertEquals(URL, decision.packForProtocol(774).url());
    }
}
