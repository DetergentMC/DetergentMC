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

/** 735（1.16）用のリソースパック（pack_format 5）の生成。 */
class ResourcePackBuilder735Test {

    @TempDir
    Path tempDir;

    @Test
    void packFormatForProtocol735Is5() {
        assertEquals(5, ResourcePackBuilder.packFormatForProtocol(735));
        assertEquals(5, ResourcePackBuilder.packFormatForProtocol(736));
        assertEquals(6, ResourcePackBuilder.packFormatForProtocol(751));
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
    void build735WritesPackFormat5AndValidZip() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build735(tempDir.resolve("735"));
        assertNotNull(built);
        assertEquals("detergentmc-pack-1.16.zip", built.file().getFileName().toString());
        assertTrue(Files.exists(built.file()));
        assertNotNull(built.sha1());
        assertEquals(40, built.sha1().length());
        assertTrue(built.sha1().matches("[0-9a-f]{40}"));

        // The zip contains a pack.mcmeta whose pack_format is 5,
        // in the plain integer form: 1.16 has no min_format /
        // max_format (see PACK_MCMETA_735).
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
        assertTrue(packMcmeta.contains("\"pack_format\":5"), packMcmeta);
        assertFalse(packMcmeta.contains("min_format"), packMcmeta);
        assertFalse(packMcmeta.contains("max_format"), packMcmeta);
        // The 735 pack names 1.16 in its description, so it differs
        // from the 736 pack even though both use format 5 and the demo
        // assets and fixed zip timestamps are the same.
        assertTrue(packMcmeta.contains("1.16"), packMcmeta);
        // The 751 / 753 / 754 packs (different pack_format for 751+,
        // different description for each) differ too.
        ResourcePackBuilder.BuiltPack built736 = builder.build736(tempDir.resolve("736"));
        ResourcePackBuilder.BuiltPack built751 = builder.build751(tempDir.resolve("751"));
        ResourcePackBuilder.BuiltPack built753 = builder.build753(tempDir.resolve("753"));
        ResourcePackBuilder.BuiltPack built754 = builder.build754(tempDir.resolve("754"));
        ResourcePackBuilder.BuiltPack built755 = builder.build755(tempDir.resolve("755"));
        assertNotEquals(built.sha1(), built736.sha1());
        assertNotEquals(built.sha1(), built751.sha1());
        assertNotEquals(built.sha1(), built753.sha1());
        assertNotEquals(built.sha1(), built754.sha1());
        assertNotEquals(built.sha1(), built755.sha1());
        assertNotEquals(built.file(), built736.file());
        assertNotEquals(built.file(), built751.file());
        assertNotEquals(built.file(), built753.file());
        assertNotEquals(built.file(), built754.file());
        assertNotEquals(built.file(), built755.file());
    }

    @Test
    void writePack735WritesPackFormat5() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        Path file = tempDir.resolve("detergentmc-pack-1.16.zip");
        builder.writePack735(file);
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
        assertTrue(packMcmeta.contains("\"pack_format\":5"), packMcmeta);
        assertTrue(packMcmeta.contains("1.16"), packMcmeta);
    }
}
