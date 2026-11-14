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

/** 761（1.19.3）用のリソースパック（pack_format 12）。 */
class ResourcePackBuilder761Test {

    @TempDir
    Path tempDir;

    @Test
    void builds761PackWithFormat12AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built761 =
                new ResourcePackBuilder().build761(tempDir);
        assertTrue(Files.exists(built761.file()));
        assertEquals(40, built761.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built761.file()), built761.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_761,
                built761.file().getFileName().toString());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_19_3,
                ResourcePackBuilder.packFormatForProtocol(761));
        assertEquals(12, ResourcePackBuilder.PACK_FORMAT_1_19_3);
        assertEquals(12, ResourcePackBuilder.packFormatForProtocol(761));

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built761.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.19.3-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.19.3 format 12 (not 9, not 13, not 15).
        // 9 is 1.19–1.19.2, 12 is 1.19.3, 13 is 1.19.4,
        // 15 is 1.20–1.20.1 (Minecraft Wiki pack_format).
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":12"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":9"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":13"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);
    }

    @Test
    void pack761DiffersFromPack762OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack761 = builder.build761(tempDir.resolve("761"));
        ResourcePackBuilder.BuiltPack pack762 = builder.build762(tempDir.resolve("762"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack761.sha1(), pack762.sha1());
        // Rebuilding the 761 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build761(tempDir.resolve("761b"));
        assertEquals(pack761.sha1(), again.sha1());
    }

    @Test
    void writePack761WritesFormat12() throws Exception {
        Path file = tempDir.resolve("pack761.zip");
        new ResourcePackBuilder().writePack761(file);
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
        assertTrue(mcmeta.contains("\"pack_format\":12"), mcmeta);
    }

    @Test
    void packFormatForProtocolReturns12For761() {
        assertEquals(12, ResourcePackBuilder.packFormatForProtocol(761));
    }

    @Test
    void packFormatForProtocolRejectsUnknownProtocol() {
        // 999 はどの対応バージョンでもないため拒否される（759は対応済み）。
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackBuilder.packFormatForProtocol(999));
    }
}
