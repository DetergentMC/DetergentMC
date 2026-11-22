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

/** 774（1.21.11）用のリソースパック（pack_format 75）。 */
class ResourcePackBuilder774Test {

    @TempDir
    Path tempDir;

    @Test
    void builds774PackWithFormat75AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built774 =
                new ResourcePackBuilder().build774(tempDir);
        assertTrue(Files.exists(built774.file()));
        assertEquals(40, built774.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built774.file()), built774.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_774,
                built774.file().getFileName().toString());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_21_11,
                ResourcePackBuilder.packFormatForProtocol(774));
        assertEquals(75, ResourcePackBuilder.PACK_FORMAT_1_21_11);
        assertEquals(75, ResourcePackBuilder.packFormatForProtocol(774));

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built774.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.21.11-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.21.11 format 75 (not 15, not 18, not 22,
        // not 32, not 34, not 42, not 46, not 64, not 69). 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4,
        // 32 is 1.20.5–1.20.6, 34 is 1.21–1.21.1, 42 is 1.21.2–1.21.3, 46 is 1.21.4
        // and 64 is 1.21.7, 69 is 1.21.9, 75 is 1.21.11 (Minecraft Wiki pack_format).
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":75"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":18"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":22"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":32"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":34"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":42"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":46"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":55"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":63"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":64"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":69"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);
    }

    @Test
    void pack774DiffersFromPack773OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack773 = builder.build773(tempDir.resolve("773"));
        ResourcePackBuilder.BuiltPack pack774 = builder.build774(tempDir.resolve("774"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack773.sha1(), pack774.sha1());
        // Rebuilding the 774 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build774(tempDir.resolve("774b"));
        assertEquals(pack774.sha1(), again.sha1());
    }

    @Test
    void writePack774WritesFormat75() throws Exception {
        Path file = tempDir.resolve("pack774.zip");
        new ResourcePackBuilder().writePack774(file);
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
        assertTrue(mcmeta.contains("\"pack_format\":75"), mcmeta);
    }

    @Test
    void packFormatForProtocolRejectsUnknownProtocol() {
        // 777（26.3以降の未対応番号）は拒否される。
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackBuilder.packFormatForProtocol(777));
    }
}
