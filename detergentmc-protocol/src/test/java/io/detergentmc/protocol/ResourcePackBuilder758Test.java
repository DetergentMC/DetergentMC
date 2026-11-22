// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** 758（1.18.2）用のリソースパック（pack_format 8）の生成。 */
class ResourcePackBuilder758Test {

    @TempDir
    Path tempDir;

    @Test
    void packFormatForProtocol758Is8() {
        assertEquals(8, ResourcePackBuilder.packFormatForProtocol(758));
        assertEquals(9, ResourcePackBuilder.packFormatForProtocol(759));
        assertEquals(9, ResourcePackBuilder.packFormatForProtocol(760));
        assertEquals(12, ResourcePackBuilder.packFormatForProtocol(761));
    }

    @Test
    void build758WritesPackFormat8AndValidZip() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack built = builder.build758(tempDir.resolve("758"));
        assertNotNull(built);
        assertEquals("detergentmc-pack-1.18.2.zip", built.file().getFileName().toString());
        assertTrue(Files.exists(built.file()));
        assertNotNull(built.sha1());
        assertEquals(40, built.sha1().length());
        assertTrue(built.sha1().matches("[0-9a-f]{40}"));

        // The zip contains a pack.mcmeta whose pack_format is 8
        // (real vanilla 1.18.2 server jar version.json:
        // pack_version.resource 8).
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
        assertTrue(packMcmeta.contains("\"pack_format\":8"), packMcmeta);
        // The 758 pack is a different file from the 759 / 761 packs
        // (different pack_format).
        ResourcePackBuilder.BuiltPack built759 = builder.build759(tempDir.resolve("759"));
        ResourcePackBuilder.BuiltPack built761 = builder.build761(tempDir.resolve("761"));
        assertNotEquals(built.sha1(), built759.sha1());
        assertNotEquals(built.sha1(), built761.sha1());
        assertNotEquals(built.file(), built759.file());
        assertNotEquals(built.file(), built761.file());
    }

    @Test
    void writePack758WritesPackFormat8() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        Path file = tempDir.resolve("pack758.zip");
        builder.writePack758(file);
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
        assertTrue(packMcmeta.contains("\"pack_format\":8"), packMcmeta);
    }
}
