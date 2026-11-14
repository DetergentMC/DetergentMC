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

/** 768（1.21.2／1.21.3）用のリソースパック（pack_format 42）。 */
class ResourcePackBuilder768Test {

    @TempDir
    Path tempDir;

    @Test
    void builds768PackWithFormat42AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built768 =
                new ResourcePackBuilder().build768(tempDir);
        assertTrue(Files.exists(built768.file()));
        assertEquals(40, built768.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built768.file()), built768.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_768,
                built768.file().getFileName().toString());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_21_2,
                ResourcePackBuilder.packFormatForProtocol(768));
        assertEquals(42, ResourcePackBuilder.PACK_FORMAT_1_21_2);
        assertEquals(42, ResourcePackBuilder.packFormatForProtocol(768));

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built768.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.21.2-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.21.2 / 1.21.3 format 42 (not 15, not 18, not 22,
        // not 32, not 34). 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4,
        // 32 is 1.20.5–1.20.6, 34 is 1.21–1.21.1 and 42 is 1.21.2–1.21.3
        // (Minecraft Wiki pack_format).
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":42"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":18"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":22"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":32"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":34"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);
    }

    @Test
    void pack768DiffersFromPack767OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack767 = builder.build767(tempDir.resolve("767"));
        ResourcePackBuilder.BuiltPack pack768 = builder.build768(tempDir.resolve("768"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack767.sha1(), pack768.sha1());
        // Rebuilding the 768 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build768(tempDir.resolve("768b"));
        assertEquals(pack768.sha1(), again.sha1());
    }

    @Test
    void writePack768WritesFormat42() throws Exception {
        Path file = tempDir.resolve("pack768.zip");
        new ResourcePackBuilder().writePack768(file);
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
        assertTrue(mcmeta.contains("\"pack_format\":42"), mcmeta);
    }

    @Test
    void packFormatForProtocolRejectsUnknownProtocol() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackBuilder.packFormatForProtocol(776));
    }
}
