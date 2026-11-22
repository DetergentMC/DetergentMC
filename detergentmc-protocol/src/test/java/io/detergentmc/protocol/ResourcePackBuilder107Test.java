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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 107（1.9）用のリソースパック（pack_format 2、旧レイアウト）の生成。レイアウトは 108／109／210／315／335／340 と同一で、形式番号だけが 2。 */
class ResourcePackBuilder107Test {

    @TempDir
    Path tempDir;

    @Test
    void packFormatForProtocol107Is2() {
        assertEquals(2, ResourcePackBuilder.packFormatForProtocol(107));
        assertEquals(2, ResourcePackBuilder.packFormatForProtocol(108));
        assertEquals(3, ResourcePackBuilder.packFormatForProtocol(335));
        assertEquals(3, ResourcePackBuilder.packFormatForProtocol(340));
        assertEquals(4, ResourcePackBuilder.packFormatForProtocol(393));
    }

    @Test
    void build107WritesPackFormat2AndValidZip() throws Exception {
        ResourcePackBuilder.BuiltPack built =
                new ResourcePackBuilder().build107(tempDir.resolve("107"));
        assertNotNull(built);
        assertEquals("detergentmc-pack-1.9.zip", built.file().getFileName().toString());
        assertTrue(Files.exists(built.file()));
        assertEquals(40, built.sha1().length());
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(Files.readAllBytes(built.file())))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(),
                        new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertTrue(entries.containsKey("pack.mcmeta"));
        assertTrue(entries.get("pack.mcmeta").contains("\"pack_format\":2"),
                entries.get("pack.mcmeta"));
        // 説明文は 1.9 用（108／109／210／335／340 パックと中身を分ける）。
        assertTrue(entries.get("pack.mcmeta").contains("(1.9)"),
                entries.get("pack.mcmeta"));
        // 旧レイアウト：テクスチャは textures/items/、モデルの layer0 も旧形式。
        assertTrue(entries.containsKey("assets/detergentmc/textures/items/bubble.png"),
                entries.keySet().toString());
        assertTrue(entries.getOrDefault("assets/detergentmc/models/item/bubble.json", "")
                .contains("detergentmc:items/bubble"));
        assertTrue(!entries.containsKey("assets/detergentmc/textures/item/bubble.png"));
    }
}
