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
 * 776（26.2）用の外部パックの判定（{@link ResourcePackMode}）：
 * {@code resource-pack.url-776} / {@code resource-pack.sha1-776}。
 * 26.2にホットフィックスは無い（プロトコル776、pack_format 88）。
 */
class ResourcePackMode776Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_776 =
            "https://example.invalid/packs/detergentmc-pack-26.2.zip";
    private static final String VALID_SHA1_776 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String URL_775 =
            "https://example.invalid/packs/detergentmc-pack-26.1.zip";
    private static final String VALID_SHA1_775 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String URL_774 =
            "https://example.invalid/packs/detergentmc-pack-1.21.11.zip";
    private static final String VALID_SHA1_774 =
            "9999999999999999999999999999999999999999";
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
    void valid776UrlAndSha1YieldPack776() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, URL_774, VALID_SHA1_774, URL_775, VALID_SHA1_775,
                URL_776, VALID_SHA1_776,
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
        assertNull(decision.pack769());
        assertNotNull(decision.pack771());
        assertEquals(URL_771, decision.pack771().url());
        assertEquals(VALID_SHA1_771, decision.pack771().sha1());
        assertNotNull(decision.pack775());
        assertEquals(URL_775, decision.pack775().url());
        assertEquals(VALID_SHA1_775, decision.pack775().sha1());
        assertNotNull(decision.pack776());
        assertEquals(URL_776, decision.pack776().url());
        assertEquals(VALID_SHA1_776, decision.pack776().sha1());
        assertFalse(decision.pack776().required());
        // packForProtocol picks the 776 pack for 776 clients, the
        // 775 pack for 775 clients, and the 765 pack for 765 clients.
        assertEquals(URL_776, decision.packForProtocol(776).url());
        assertEquals(URL_775, decision.packForProtocol(775).url());
        assertEquals(URL, decision.packForProtocol(765).url());
    }

    @Test
    void empty776UrlMeansNoPack776AndNoProblem() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, URL_774, VALID_SHA1_774, URL_775, VALID_SHA1_775, "", "",
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack771());
        assertNotNull(decision.pack772());
        assertNotNull(decision.pack773());
        assertNotNull(decision.pack774());
        assertNotNull(decision.pack775());
        assertNull(decision.pack776());
        // Without a 776 pack, 776 clients fall back to the main pack.
        assertEquals(URL, decision.packForProtocol(776).url());
    }

    @Test
    void invalid776Sha1WarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, URL_774, VALID_SHA1_774, URL_775, VALID_SHA1_775,
                URL_776, "not-a-sha1",
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        // The primary pack is unaffected; only the 776 pack is dropped.
        assertNotNull(decision.pack());
        assertEquals(URL, decision.pack().url());
        assertNull(decision.pack776());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("sha1-776"), decision.problem());
    }

    @Test
    void nonHttps776UrlWarnsAndFallsBackToMainPack() {
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, URL_774, VALID_SHA1_774, URL_775, VALID_SHA1_775,
                "http://example.invalid/packs/detergentmc-pack-26.2.zip",
                VALID_SHA1_776, false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack());
        assertNull(decision.pack776());
        assertNotNull(decision.problem());
        assertTrue(decision.problem().contains("url-776"), decision.problem());
    }

    @Test
    void decideWithout776StillWorks() {
        // The older decide (without url-776 / sha1-776) keeps working
        // and yields no 776 pack.
        ResourcePackMode.Decision decision = ResourcePackMode.decide(
                URL, VALID_SHA1, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                URL_770, VALID_SHA1_770, URL_771, VALID_SHA1_771, URL_772, VALID_SHA1_772,
                URL_773, VALID_SHA1_773, URL_774, VALID_SHA1_774, URL_775, VALID_SHA1_775,
                false, "");
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNull(decision.problem());
        assertNotNull(decision.pack());
        assertNotNull(decision.pack770());
        assertNotNull(decision.pack771());
        assertNotNull(decision.pack772());
        assertNotNull(decision.pack773());
        assertNotNull(decision.pack774());
        assertNotNull(decision.pack775());
        assertNull(decision.pack776());
        assertEquals(URL, decision.packForProtocol(776).url());
    }
}
