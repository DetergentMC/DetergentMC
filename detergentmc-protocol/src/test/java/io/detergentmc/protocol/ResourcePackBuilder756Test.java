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

/** 756（1.17.1）用のリソースパック（pack_format 7）の生成。 */
class ResourcePackBuilder756Test {

    @TempDir
    Path tempDir;

    @Test
    void packFormatForProtocol756Is7() {
        assertEquals(7, ResourcePackBuilder.packFormatForProtocol(756));
        assertEquals(8, ResourcePackBuilder.packFormatForProtocol(757));
        assertEquals(9, ResourcePackBuilder.packFormatForProtocol(759));
        assertEquals(9, ResourcePackBuilder.packFormatForProtocol(760));
        assertEquals(12, ResourcePackBuilder.packFormatForProtocol(761));
    }

    @Test
    void build756WritesPackFormat7AndValidZip() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build756(tempDir.resolve("756"));
        assertNotNull(built);
        assertEquals("detergentmc-pack-1.17.1.zip", built.file().getFileName().toString());
        assertTrue(Files.exists(built.file()));
        assertNotNull(built.sha1());
        assertEquals(40, built.sha1().length());
        assertTrue(built.sha1().matches("[0-9a-f]{40}"));

        // The zip contains a pack.mcmeta whose pack_format is 7,
        // in the plain integer form: 1.17.1 has no min_format /
        // max_format (see PACK_MCMETA_756).
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
        assertTrue(packMcmeta.contains("\"pack_format\":7"), packMcmeta);
        assertFalse(packMcmeta.contains("min_format"), packMcmeta);
        assertFalse(packMcmeta.contains("max_format"), packMcmeta);
        // The 756 pack is a different file from the 757 / 759 packs
        // (different pack_format).
        ResourcePackBuilder.BuiltPack built757 = builder.build757(tempDir.resolve("757"));
        ResourcePackBuilder.BuiltPack built759 = builder.build759(tempDir.resolve("759"));
        assertNotEquals(built.sha1(), built757.sha1());
        assertNotEquals(built.sha1(), built759.sha1());
        assertNotEquals(built.file(), built757.file());
        assertNotEquals(built.file(), built759.file());
    }

    @Test
    void writePack756WritesPackFormat7() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        Path file = tempDir.resolve("detergentmc-pack-1.17.1.zip");
        builder.writePack756(file);
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
        assertTrue(packMcmeta.contains("\"pack_format\":7"), packMcmeta);
    }
}
