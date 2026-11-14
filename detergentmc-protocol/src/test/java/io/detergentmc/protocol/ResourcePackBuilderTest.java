package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsZipWithExpectedEntries() throws Exception {
        ResourcePackBuilder.BuiltPack built = new ResourcePackBuilder().build(tempDir);
        assertTrue(Files.exists(built.file()));
        assertEquals(40, built.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built.file()), built.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME, built.file().getFileName().toString());

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        String bubbleModel = null;
        String slimeballModel = null;
        byte[] pngBytes = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                byte[] bytes = zip.readAllBytes();
                switch (entry.getName()) {
                    case "pack.mcmeta" -> mcmeta = new String(bytes, StandardCharsets.UTF_8);
                    case ResourcePackBuilder.BUBBLE_MODEL_PATH ->
                            bubbleModel = new String(bytes, StandardCharsets.UTF_8);
                    case ResourcePackBuilder.SLIMEBALL_MODEL_PATH ->
                            slimeballModel = new String(bytes, StandardCharsets.UTF_8);
                    case ResourcePackBuilder.BUBBLE_TEXTURE_PATH -> pngBytes = bytes;
                    default -> throw new AssertionError("unexpected entry: " + entry.getName());
                }
            }
        }
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.20.4 format 22, exact description.
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":22"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);

        // Bubble texture: a readable 16x16 PNG with some non-transparent pixels.
        assertNotNull(pngBytes);
        BufferedImage image;
        try (InputStream in = new ByteArrayInputStream(pngBytes)) {
            image = ImageIO.read(in);
        }
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        int opaque = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    opaque++;
                }
            }
        }
        assertTrue(opaque > 50, "bubble should cover a good part of the 16x16 canvas");

        // Bubble model: generated item model pointing at the bubble texture.
        assertNotNull(bubbleModel);
        assertTrue(bubbleModel.contains("\"parent\":\"item/generated\""), bubbleModel);
        assertTrue(bubbleModel.contains("detergentmc:item/bubble"), bubbleModel);

        // Slimeball override: CustomModelData 1 -> the bubble model.
        assertNotNull(slimeballModel);
        assertTrue(slimeballModel.contains("\"custom_model_data\":1"), slimeballModel);
        assertTrue(slimeballModel.contains("\"model\":\"detergentmc:item/bubble\""),
                slimeballModel);
    }

    @Test
    void rebuildIsDeterministic() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack first = builder.build(tempDir.resolve("a"));
        ResourcePackBuilder.BuiltPack second = builder.build(tempDir.resolve("b"));
        assertEquals(first.sha1(), second.sha1());
        assertEquals(Files.readAllBytes(first.file()).length,
                Files.readAllBytes(second.file()).length);
    }

    @Test
    void builds763PackWithFormat15AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built763 =
                new ResourcePackBuilder().build763(tempDir);
        assertTrue(Files.exists(built763.file()));
        assertEquals(40, built763.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built763.file()), built763.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_763,
                built763.file().getFileName().toString());

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        String bubbleModel = null;
        String slimeballModel = null;
        byte[] pngBytes = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built763.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                byte[] bytes = zip.readAllBytes();
                switch (entry.getName()) {
                    case "pack.mcmeta" -> mcmeta = new String(bytes, StandardCharsets.UTF_8);
                    case ResourcePackBuilder.BUBBLE_MODEL_PATH ->
                            bubbleModel = new String(bytes, StandardCharsets.UTF_8);
                    case ResourcePackBuilder.SLIMEBALL_MODEL_PATH ->
                            slimeballModel = new String(bytes, StandardCharsets.UTF_8);
                    case ResourcePackBuilder.BUBBLE_TEXTURE_PATH -> pngBytes = bytes;
                    default -> throw new AssertionError("unexpected entry: " + entry.getName());
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.20.3-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.20.1 format 15 (not 22), same description.
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":22"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);

        // Bubble texture: the same readable 16x16 PNG as the 1.20.4 pack.
        assertNotNull(pngBytes);
        BufferedImage image;
        try (InputStream in = new ByteArrayInputStream(pngBytes)) {
            image = ImageIO.read(in);
        }
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());

        // Bubble model and slimeball override: unchanged — both are
        // valid in 1.20.1 (item/generated + custom_model_data predicate).
        assertNotNull(bubbleModel);
        assertTrue(bubbleModel.contains("detergentmc:item/bubble"), bubbleModel);
        assertNotNull(slimeballModel);
        assertTrue(slimeballModel.contains("\"custom_model_data\":1"), slimeballModel);
        assertTrue(slimeballModel.contains("\"model\":\"detergentmc:item/bubble\""),
                slimeballModel);
    }

    @Test
    void pack763DiffersFromPack765OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack765 = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack pack763 = builder.build763(tempDir.resolve("763"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack765.sha1(), pack763.sha1());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_20_1,
                ResourcePackBuilder.packFormatForProtocol(763));
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_20_4,
                ResourcePackBuilder.packFormatForProtocol(765));
        // Rebuilding the 763 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build763(tempDir.resolve("763b"));
        assertEquals(pack763.sha1(), again.sha1());
    }

    @Test
    void packFormatForUnknownProtocolThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ResourcePackBuilder.packFormatForProtocol(999));
    }

    @Test
    void builds764PackWithFormat18AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built764 =
                new ResourcePackBuilder().build764(tempDir);
        assertTrue(Files.exists(built764.file()));
        assertEquals(40, built764.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built764.file()), built764.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_764,
                built764.file().getFileName().toString());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_20_2,
                ResourcePackBuilder.packFormatForProtocol(764));

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built764.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.20.3-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.20.2 format 18 (not 15, not 22), same description.
        // 15 is 1.20–1.20.1 and 22 is 1.20.3–1.20.4 (Minecraft Wiki pack_format).
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":18"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":22"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);
    }

    @Test
    void pack764DiffersFromPack765OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack765 = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack pack764 = builder.build764(tempDir.resolve("764"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack765.sha1(), pack764.sha1());
        // Rebuilding the 764 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build764(tempDir.resolve("764b"));
        assertEquals(pack764.sha1(), again.sha1());
    }

    @Test
    void builds766PackWithFormat32AndSameDemoAssets() throws Exception {
        ResourcePackBuilder.BuiltPack built766 =
                new ResourcePackBuilder().build766(tempDir);
        assertTrue(Files.exists(built766.file()));
        assertEquals(40, built766.sha1().length());
        assertEquals(ResourcePackBuilder.sha1Hex(built766.file()), built766.sha1());
        assertEquals(ResourcePackBuilder.PACK_FILE_NAME_766,
                built766.file().getFileName().toString());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_20_5,
                ResourcePackBuilder.packFormatForProtocol(766));

        Set<String> names = new HashSet<>();
        String mcmeta = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(built766.file()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().equals("pack.mcmeta")) {
                    mcmeta = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        // Same four entries as the 1.20.4 pack — nothing 1.20.5-only in here.
        assertEquals(Set.of("pack.mcmeta",
                ResourcePackBuilder.BUBBLE_TEXTURE_PATH,
                ResourcePackBuilder.BUBBLE_MODEL_PATH,
                ResourcePackBuilder.SLIMEBALL_MODEL_PATH), names);

        // pack.mcmeta: 1.20.5 / 1.20.6 format 32 (not 15, not 18, not 22).
        // 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4 and
        // 32 is 1.20.5–1.20.6 (Minecraft Wiki pack_format).
        assertNotNull(mcmeta);
        assertTrue(mcmeta.contains("\"pack_format\":32"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":15"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":18"), mcmeta);
        assertTrue(!mcmeta.contains("\"pack_format\":22"), mcmeta);
        assertTrue(mcmeta.contains("DetergentMC resource pack"), mcmeta);
    }

    @Test
    void pack766DiffersFromPack765OnlyInMcmeta() throws Exception {
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackBuilder.BuiltPack pack765 = builder.build(tempDir.resolve("765"));
        ResourcePackBuilder.BuiltPack pack766 = builder.build766(tempDir.resolve("766"));
        // Different files and different SHA-1s (the mcmeta differs).
        assertNotEquals(pack765.sha1(), pack766.sha1());
        assertEquals(ResourcePackBuilder.PACK_FORMAT_1_20_5,
                ResourcePackBuilder.packFormatForProtocol(766));
        // Rebuilding the 766 pack is deterministic, too.
        ResourcePackBuilder.BuiltPack again = builder.build766(tempDir.resolve("766b"));
        assertEquals(pack766.sha1(), again.sha1());
    }

    @Test
    void packFormatForProtocolCoversAllKnownProtocols() {
        // Re-check of every known value (Minecraft Wiki pack_format):
        // 15 = 1.20–1.20.1, 18 = 1.20.2, 22 = 1.20.3–1.20.4,
        // 32 = 1.20.5–1.20.6.
        assertEquals(15, ResourcePackBuilder.packFormatForProtocol(763));
        assertEquals(18, ResourcePackBuilder.packFormatForProtocol(764));
        assertEquals(22, ResourcePackBuilder.packFormatForProtocol(765));
        assertEquals(32, ResourcePackBuilder.packFormatForProtocol(766));
    }
}
