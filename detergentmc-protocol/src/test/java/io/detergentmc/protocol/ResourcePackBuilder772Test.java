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

/** 772（1.21.7）用のリソースパック（pack_format 64）。 */
class ResourcePackBuilder772Test {

    @TempDir
    Path tempDir;

    @Test
    void builds772PackWithFormat64AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built772 =
                new ResourcePackBuilder().build772(tempDir);
        assertTrue(Files.exists(built772.file()));
        assertEquals(40, built772.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built772.file()), built772.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_772,
                built772.file().getFileName().toString());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_21_7,
                ResourcePackBuilder.packFormatForProtocol(772));
        assertEquals(64, ResourcePackBuilder.PACK_FORMAT_1_21_7);
        assertEquals(64, ResourcePackBuilder.packFormatForProtocol(772));

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built772.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.21.7-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.21.7 format 64 (not 15, not 18, not 22,
        // not 32, not 34, not 42, not 46). 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4,
        // 32 is 1.20.5–1.20.6, 34 is 1.21–1.21.1, 42 is 1.21.2–1.21.3, 46 is 1.21.4
        // and 64 is 1.21.7 (Minecraft Wiki pack_format).
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":64"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":18"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":22"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":32"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":34"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":42"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":46"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":55"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":63"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);
    }

    @Test
    void pack772DiffersFromPack771OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack771 = builder.build771(tempDir.resolve("771"));
        ResourcePackBuilder.BuiltPack pack772 = builder.build772(tempDir.resolve("772"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack771.sha1(), pack772.sha1());
        // Rebuilding the 772 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build772(tempDir.resolve("772b"));
        assertEquals(pack772.sha1(), again.sha1());
    }

    @Test
    void writePack772WritesFormat64() throws Exception {
        Path file = tempDir.resolve("pack772.zip");
        new ResourcePackBuilder().writePack772(file);
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
        assertTrue(mcmeta.contains("\"pack_format\":64"), mcmeta);
    }

    @Test
    void packFormatForProtocolRejectsUnknownProtocol() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackBuilder.packFormatForProtocol(777));
    }
}
