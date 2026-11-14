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
 * 768（1.21.2／1.21.3）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-768} / {@code resource-pack.sha1-768}。
 */
class ResourcePackMode768Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_768 =
            "https://example.invalid/packs/detergentmc-pack-1.21.2.zip";
    private static final String VALID_SHA1_768 =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

    @Test
    void valid768UrlAndSha1YieldPack768() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", URL_768, VALID_SHA1_768,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack763());
        assertNull(decision.pack764());
        assertNull(decision.pack766());
        assertNull(decision.pack767());
        assertNotNull(decision.pack768());
        assertEquals(URL_768, decision.pack768().url());
        assertEquals(VALID_SHA1_768, decision.pack768().sha1());
        assertFalse(decision.pack768().required());
        // packForProtocol picks the 768 pack for 768 clients, and the
        // 765 pack for 765 clients. One pack serves both 1.21.2 and 1.21.3.
        assertEquals(URL_768, decision.packForProtocol(768).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty768UrlMeansNoPack768AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack768());
        // Without a 768 pack, 768 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(768).url());
    }

    @Test
    void invalid768Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", URL_768, "not-a-sha1",
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 768 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack768());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-768"), decision.problem());
    }

    @Test
    void nonHttps768UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "",
                "http://example.invalid/packs/detergentmc-pack-1.21.2.zip",
                VALID_SHA1_768, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack768());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-768"), decision.problem());
    }

    @Test
    void twelveArgDecideWithout768StillWorks() {
        // The older 12-argument decide (without url-768 / sha1-768)
        // keeps working and yields no 768 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNull(decision.pack768());
        assertEquals(URL, decision.packForProtocol(768).url());
    }
}
