// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 762（1.19.4）用のリソースパック（pack_format 13）。 */
class ResourcePackBuilder762Test {

    @TempDir
    Path tempDir;

    @Test
    void builds762PackWithFormat13AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built762 =
                new ResourcePackBuilder().build762(tempDir);
        assertTrue(Files.exists(built762.file()));
        assertEquals(40, built762.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built762.file()), built762.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_762,
                built762.file().getFileName().toString());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_19_4,
                ResourcePackBuilder.packFormatForProtocol(762));
        assertEquals(13, ResourcePackBuilder.PACK_FORMAT_1_19_4);
        assertEquals(13, ResourcePackBuilder.packFormatForProtocol(762));

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built762.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.19.4-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.19.4 format 13 (not 15, not 9, not 12).
        // 9 is 1.19–1.19.2, 12 is 1.19.3, 13 is 1.19.4,
        // 15 is 1.20–1.20.1 (Minecraft Wiki pack_format).
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":13"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":9"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":12"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);
    }

    @Test
    void pack762DiffersFromPack763OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack762 = builder.build762(tempDir.resolve("762"));
        ResourcePackBuilder.BuiltPack pack763 = builder.build763(tempDir.resolve("763"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack762.sha1(), pack763.sha1());
        // Rebuilding the 762 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build762(tempDir.resolve("762b"));
        assertEquals(pack762.sha1(), again.sha1());
    }

    @Test
    void writePack762WritesFormat13() throws Exception {
        Path file = tempDir.resolve("pack762.zip");
        new ResourcePackBuilder().writePack762(file);
        assertTrue(Files.exists(file));
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":13"), mcmeta);
    }

    @Test
    void packFormatForProtocolRejectsUnknownProtocol() {
        // 999 はどの対応バージョンでもないため拒否される（759は対応済み）。
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackBuilder.packFormatForProtocol(999));
    }
}
