// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** 480（1.14.1）用のリソースパック（pack_format 4）の生成。 */
class ResourcePackBuilder480Test {

    @TempDir
    Path tempDir;

    @Test
    void packFormatForProtocol480Is4() {
        assertEquals(4, ResourcePackBuilder.packFormatForProtocol(480));
        assertEquals(4, ResourcePackBuilder.packFormatForProtocol(490));
        assertEquals(5, ResourcePackBuilder.packFormatForProtocol(573));
    }

    @Test
    void build480WritesPackFormat4AndValidZip() throws Exception {
        ResourcePackBuilder.BuiltPack built =
                new ResourcePackBuilder().build480(tempDir.resolve("480"));
        assertNotNull(built);
        assertEquals("detergentmc-pack-1.14.1.zip", built.file().getFileName().toString());
        assertTrue(Files.exists(built.file()));
        assertEquals(40, built.sha1().length());
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
        assertNotNull(packMcmeta);
        // 実バニラ 1.14.1 jar の version.json：pack_version = 4。
        assertTrue(packMcmeta.contains("\"pack_format\":4"), packMcmeta);
    }
}
