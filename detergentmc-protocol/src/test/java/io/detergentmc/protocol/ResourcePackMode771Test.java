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
 * 771（1.21.6）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-771} / {@code resource-pack.sha1-771}。
 */
class ResourcePackMode771Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_771 =
            "https://example.invalid/packs/detergentmc-pack-1.21.6.zip";
    private static final String VALID_SHA1_771 =
            "ffffffffffffffffffffffffffffffffffffffff";
    private static final String URL_770 =
            "https://example.invalid/packs/detergentmc-pack-1.21.5.zip";
    private static final String VALID_SHA1_770 =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

    @Test
    void valid771UrlAndSha1YieldPack771() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, false, "");
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
        assertNotNull(decision.pack771());
        assertEquals(URL_771, decision.pack771().url());
        assertEquals(VALID_SHA1_771, decision.pack771().sha1());
        assertFalse(decision.pack771().required());
        // packForProtocol picks the 771 pack for 771 clients, the
        // 770 pack for 770 clients, and the 765 pack for 765 clients.
        assertEquals(URL_771, decision.packForProtocol(771).url());
        assertEquals(URL_770, decision.packForProtocol(770).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty771UrlMeansNoPack771AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack770());
        assertNull(decision.pack771());
        // Without a 771 pack, 771 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(771).url());
    }

    @Test
    void invalid771Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, "not-a-sha1", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 771 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack771());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-771"), decision.problem());
    }

    @Test
    void nonHttps771UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770,
                "http://example.invalid/packs/detergentmc-pack-1.21.6.zip",
                VALID_SHA1_771, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack771());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-771"), decision.problem());
    }

    @Test
    void eighteenArgDecideWithout771StillWorks() {
        // The older 18-argument decide (without url-771 / sha1-771)
        // keeps working and yields no 771 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack770());
        assertNull(decision.pack771());
        assertEquals(URL, decision.packForProtocol(771).url());
    }
}
