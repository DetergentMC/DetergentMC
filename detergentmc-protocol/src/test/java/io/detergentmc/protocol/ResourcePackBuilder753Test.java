// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 753（1.16.3）用のリソースパック（pack_format 6）の生成。 */
class ResourcePackBuilder753Test {

    @TempDir
    Path tempDir;

    @Test
    void packFormatForProtocol753Is6() {
        assertEquals(6, ResourcePackBuilder.packFormatForProtocol(753));
        assertEquals(6, ResourcePackBuilder.packFormatForProtocol(754));
        assertEquals(7, ResourcePackBuilder.packFormatForProtocol(755));
        assertEquals(7, ResourcePackBuilder.packFormatForProtocol(756));
        assertEquals(8, ResourcePackBuilder.packFormatForProtocol(757));
        assertEquals(9, ResourcePackBuilder.packFormatForProtocol(759));
        assertEquals(9, ResourcePackBuilder.packFormatForProtocol(760));
        assertEquals(12, ResourcePackBuilder.packFormatForProtocol(761));
    }

    @Test
    void build753WritesPackFormat6AndValidZip() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build753(tempDir.resolve("753"));
        assertNotNull(built);
        assertEquals("detergentmc-pack-1.16.3.zip", built.file().getFileName().toString());
        assertTrue(Files.exists(built.file()));
        assertNotNull(built.sha1());
        assertEquals(40, built.sha1().length());
        assertTrue(built.sha1().matches("[0-9a-f]{40}"));

        // The zip contains a pack.mcmeta whose pack_format is 6,
        // in the plain integer form: 1.16.3 has no min_format /
        // max_format (see PACK_MCMETA_753).
        String packMcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(Files.readAllBytes(built.file())))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("pack.mcmeta")) {
                    packMcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        assertNotNull(packMcmeta, "pack.mcmeta が zip に入っていること");
        assertTrue(packMcmeta.contains("\"pack_format\":6"), packMcmeta);
        assertFalse(packMcmeta.contains("min_format"), packMcmeta);
        assertFalse(packMcmeta.contains("max_format"), packMcmeta);
        // The 753 pack names 1.16.3 in its description, so it differs
        // from the 754 pack even though both use pack_format 6.
        assertTrue(packMcmeta.contains("1.16.3"), packMcmeta);
        // The 754 pack has the same pack_format (6) but a different
        // description, so its SHA-1 differs from the 753 pack even
        // though the demo assets and fixed zip timestamps are the same.
        // The 755 / 757 packs (different pack_format) differ too.
        ResourcePackBuilder.BuiltPack built754 = builder.build754(tempDir.resolve("754"));
        ResourcePackBuilder.BuiltPack built755 = builder.build755(tempDir.resolve("755"));
        ResourcePackBuilder.BuiltPack built757 = builder.build757(tempDir.resolve("757"));
        assertNotEquals(built.sha1(), built754.sha1());
        assertNotEquals(built.sha1(), built755.sha1());
        assertNotEquals(built.sha1(), built757.sha1());
        assertNotEquals(built.file(), built754.file());
        assertNotEquals(built.file(), built755.file());
        assertNotEquals(built.file(), built757.file());
    }

    @Test
    void writePack753WritesPackFormat6() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        Path file = tempDir.resolve("detergentmc-pack-1.16.3.zip");
        builder.writePack753(file);
        assertTrue(Files.exists(file));
        String packMcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(Files.readAllBytes(file)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("pack.mcmeta")) {
                    packMcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        assertNotNull(packMcmeta, "pack.mcmeta が zip に入っていること");
        assertTrue(packMcmeta.contains("\"pack_format\":6"), packMcmeta);
        assertTrue(packMcmeta.contains("1.16.3"), packMcmeta);
    }
}
