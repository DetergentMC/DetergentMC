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

/** 767（1.21／1.21.1）用のリソースパック（pack_format 34）。 */
class ResourcePackBuilder767Test {

    @TempDir
    Path tempDir;

    @Test
    void builds767PackWithFormat34AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built767 =
                new ResourcePackBuilder().build767(tempDir);
        assertTrue(Files.exists(built767.file()));
        assertEquals(40, built767.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built767.file()), built767.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_767,
                built767.file().getFileName().toString());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_21,
                ResourcePackBuilder.packFormatForProtocol(767));
        assertEquals(34, ResourcePackBuilder.PACK_FORMAT_1_21);
        assertEquals(34, ResourcePackBuilder.packFormatForProtocol(767));

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built767.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.21-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.21 / 1.21.1 format 34 (not 15, not 18, not 22,
        // not 32). 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4,
        // 32 is 1.20.5–1.20.6 and 34 is 1.21–1.21.1 (Minecraft Wiki
        // pack_format).
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":34"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":18"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":22"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":32"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);
    }

    @Test
    void pack767DiffersFromPack765OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack765 = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack pack767 = builder.build767(tempDir.resolve("767"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack765.sha1(), pack767.sha1());
        // Rebuilding the 767 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build767(tempDir.resolve("767b"));
        assertEquals(pack767.sha1(), again.sha1());
    }

    @Test
    void writePack767WritesFormat34() throws Exception {
        Path file = tempDir.resolve("pack767.zip");
        new ResourcePackBuilder().writePack767(file);
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
        assertTrue(mcmeta.contains("\"pack_format\":34"), mcmeta);
    }

    @Test
    void packFormatForProtocolRejectsUnknownProtocol() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackBuilder.packFormatForProtocol(777));
    }
}
