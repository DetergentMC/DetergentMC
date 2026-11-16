package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatapackGeneratorTest {

    @TempDir
    Path tempDir;

    private static Map<String, String> readZip(Path zipFile) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                var entry = enumeration.nextElement();
                assertEquals(FileTime.from(Instant.EPOCH), entry.getLastModifiedTime(),
                        "entry timestamp must be fixed for reproducibility: " + entry.getName());
                entries.put(entry.getName(),
                        new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    void buildCreatesZipInWorldDatapacks() throws Exception {
        Path vanillaDir = tempDir.resolve("vanilla");
        Path file = new DatapackGenerator().build(vanillaDir);

        assertEquals(vanillaDir.resolve("world").resolve("datapacks")
                .resolve("detergentmc.zip"), file);
        assertTrue(Files.exists(file));

        Map<String, String> entries = readZip(file);
        assertEquals(5, entries.size(), "entries: " + entries.keySet());
        assertEquals("{\"pack\":{\"pack_format\":26,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        assertEquals("{\"values\":[\"detergentmc:tick\"]}",
                entries.get("data/minecraft/tags/functions/tick.json"));
        assertEquals("{\"values\":[\"detergentmc:load\"]}",
                entries.get("data/minecraft/tags/functions/load.json"));
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
        assertEquals(DatapackGenerator.LOAD_FUNCTION,
                entries.get("data/detergentmc/functions/load.mcfunction"));
    }

    @Test
    void tickFunctionMatchesVerifiedContent() {
        String tick = DatapackGenerator.TICK_FUNCTION;
        // The exact lines proven against a real 1.20.4 server (2026-11-08).
        assertTrue(tick.contains("scoreboard players enable @a d_hello"));
        assertTrue(tick.contains("scoreboard players enable @a d_lang_ja"));
        assertTrue(tick.contains("scoreboard players enable @a d_lang_en"));
        assertTrue(tick.contains(
                "execute as @a[scores={d_hello=1..}] run say [DetergentMC] hello"));
        assertTrue(tick.contains(
                "execute as @a[scores={d_lang_ja=1..}] run say [DetergentMC] lang ja"));
        assertTrue(tick.contains(
                "execute as @a[scores={d_lang_en=1..}] run say [DetergentMC] lang en"));
        assertEquals(26, DatapackGenerator.PACK_FORMAT_1_20_4, "1.20.4 datapack format");
    }

    @Test
    void loadFunctionCreatesTriggerObjectives() {
        String load = DatapackGenerator.LOAD_FUNCTION;
        assertTrue(load.contains("scoreboard objectives add d_hello trigger"));
        assertTrue(load.contains("scoreboard objectives add d_lang_ja trigger"));
        assertTrue(load.contains("scoreboard objectives add d_lang_en trigger"));
    }

    @Test
    void rebuildIsDeterministic() throws Exception {
        DatapackGenerator generator = new DatapackGenerator();
        Path first = generator.build(tempDir.resolve("first"));
        Path second = generator.build(tempDir.resolve("second"));
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second),
                "same input must yield identical bytes (fixed timestamps)");
    }

    @Test
    void buildWith1201FormatWritesPackFormat15() throws Exception {
        Path vanillaDir = tempDir.resolve("vanilla-1-20-1");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.PACK_FORMAT_1_20_1);

        assertEquals(15, DatapackGenerator.PACK_FORMAT_1_20_1, "1.20.1 datapack format");
        assertEquals(15, DatapackGenerator.packFormatFor("1.20.1"));
        assertEquals(26, DatapackGenerator.packFormatFor("1.20.4"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":15,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // The trigger functions are the same; only the format differs.
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
    }

    @Test
    void packFormatFor1202Is18() throws Exception {
        assertEquals(18, DatapackGenerator.PACK_FORMAT_1_20_2, "1.20.2 datapack format");
        assertEquals(18, DatapackGenerator.packFormatFor("1.20.2"));
        Path vanillaDir = tempDir.resolve("vanilla-1-20-2");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.20.2"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":18,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
    }

    @Test
    void packFormatFor1205And1206Is41() throws Exception {
        // Minecraft Wiki (Template:Data pack format): 41 = 1.20.5–1.20.6.
        // 1.20.5 and 1.20.6 share the format, the same way 1.20.3 and
        // 1.20.4 share 26. The existing values are re-checked here too:
        // 15 = 1.20–1.20.1, 18 = 1.20.2, 26 = 1.20.3–1.20.4.
        assertEquals(41, DatapackGenerator.PACK_FORMAT_1_20_5, "1.20.5 datapack format");
        assertEquals(41, DatapackGenerator.packFormatFor("1.20.5"));
        assertEquals(41, DatapackGenerator.packFormatFor("1.20.6"));
        assertEquals(15, DatapackGenerator.packFormatFor("1.20.1"));
        assertEquals(18, DatapackGenerator.packFormatFor("1.20.2"));
        assertEquals(26, DatapackGenerator.packFormatFor("1.20.4"));
        Path vanillaDir = tempDir.resolve("vanilla-1-20-5");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.20.5"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":41,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // The trigger functions are the same; only the format differs.
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
    }

    @Test
    void packFormatFor121And1211Is48() throws Exception {
        // Minecraft Wiki (Template:Data pack format): 48 = 1.21–1.21.1.
        // 1.21 and 1.21.1 share the format, the same way 1.20.5 and
        // 1.20.6 share 41. The existing values are re-checked here too:
        // 15 = 1.20–1.20.1, 18 = 1.20.2, 26 = 1.20.3–1.20.4,
        // 41 = 1.20.5–1.20.6.
        assertEquals(48, DatapackGenerator.PACK_FORMAT_1_21, "1.21 datapack format");
        assertEquals(48, DatapackGenerator.packFormatFor("1.21"));
        assertEquals(48, DatapackGenerator.packFormatFor("1.21.1"));
        assertEquals(15, DatapackGenerator.packFormatFor("1.20.1"));
        assertEquals(18, DatapackGenerator.packFormatFor("1.20.2"));
        assertEquals(26, DatapackGenerator.packFormatFor("1.20.4"));
        assertEquals(41, DatapackGenerator.packFormatFor("1.20.5"));
        Path vanillaDir = tempDir.resolve("vanilla-1-21");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.21"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":48,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // 1.21では関数フォルダが単数形 function / tags/function になり、
        // 複数形は読み込まれない。関数の中身自体は同じ。
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/function/tick.mcfunction"));
        assertEquals(DatapackGenerator.LOAD_FUNCTION,
                entries.get("data/detergentmc/function/load.mcfunction"));
        assertEquals(DatapackGenerator.TICK_TAG_JSON,
                entries.get("data/minecraft/tags/function/tick.json"));
        assertEquals(DatapackGenerator.LOAD_TAG_JSON,
                entries.get("data/minecraft/tags/function/load.json"));
        assertNull(entries.get("data/detergentmc/functions/tick.mcfunction"),
                "1.21のパックに複数形の関数フォルダが無いこと");
        assertNull(entries.get("data/minecraft/tags/functions/tick.json"),
                "1.21のパックに複数形のタグフォルダが無いこと");
    }

    @Test
    void packFormat41KeepsPluralFunctionFolders() throws Exception {
        // 1.20.6以前は複数形 functions / tags/functions のまま。
        // 1.21（48）で単数形になることと対で確認する。
        Path vanillaDir = tempDir.resolve("vanilla-1-20-6");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.20.6"));
        Map<String, String> entries = readZip(file);
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
        assertEquals(DatapackGenerator.TICK_TAG_JSON,
                entries.get("data/minecraft/tags/functions/tick.json"));
        assertNull(entries.get("data/detergentmc/function/tick.mcfunction"),
                "1.20.6のパックに単数形の関数フォルダが無いこと");
    }

    @Test
    void packFormatFor261Is101() throws Exception {
        // 26.1 server jar version.json: pack data 101.1.
        // 26.1 / 26.1.1 / 26.1.2 share protocol 775 and the format,
        // the same way 1.21 and 1.21.1 share 48.
        assertEquals(101, DatapackGenerator.PACK_FORMAT_26_1, "26.1 datapack format");
        assertEquals(101, DatapackGenerator.packFormatFor("26.1"));
        assertEquals(101, DatapackGenerator.packFormatFor("26.1.1"));
        assertEquals(101, DatapackGenerator.packFormatFor("26.1.2"));
        assertEquals(94, DatapackGenerator.packFormatFor("1.21.11"));
        Path vanillaDir = tempDir.resolve("vanilla-26-1");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("26.1"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":101,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // 26.1も1.21系と同じく単数形 function / tags/function。
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/function/tick.mcfunction"));
        assertNull(entries.get("data/detergentmc/functions/tick.mcfunction"),
                "26.1のパックに複数形の関数フォルダが無いこと");
    }

    @Test
    void packFormatFor1165Is6() throws Exception {
        // 1.16.5 server jar version.json: pack_version 6 (754ノート§7.3実測)。
        assertEquals(6, DatapackGenerator.PACK_FORMAT_1_16_5, "1.16.5 datapack format");
        assertEquals(6, DatapackGenerator.packFormatFor("1.16.5"));
        // 1.16.4も同じプロトコル754・同じpack 6（1.16.4 jar version.json実測。親が統合時に追加）。
        assertEquals(6, DatapackGenerator.packFormatFor("1.16.4"));
        Path vanillaDir = tempDir.resolve("vanilla-1-16-5");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.16.5"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":6,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // 1.16.5は複数形 functions / tags/functions（単数形は1.21から）。
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
        assertNull(entries.get("data/detergentmc/function/tick.mcfunction"),
                "1.16.5のパックに単数形の関数フォルダが無いこと");
        assertEquals("{\"values\":[\"detergentmc:tick\"]}",
                entries.get("data/minecraft/tags/functions/tick.json"));
        assertNull(entries.get("data/minecraft/tags/function/tick.json"),
                "1.16.5のパックに単数形のtagsフォルダが無いこと");
    }

    @Test
    void packFormatFor1162Is6() throws Exception {
        // 1.16.2 server jar version.json: pack_version 6 (751ノート§7.3実測)。
        assertEquals(6, DatapackGenerator.PACK_FORMAT_1_16_2, "1.16.2 datapack format");
        assertEquals(6, DatapackGenerator.packFormatFor("1.16.2"));
        Path vanillaDir = tempDir.resolve("vanilla-1-16-2");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.16.2"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":6,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // 1.16.2は複数形 functions / tags/functions（単数形は1.21から）。
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
        assertNull(entries.get("data/detergentmc/function/tick.mcfunction"),
                "1.16.2のパックに単数形の関数フォルダが無いこと");
        assertEquals("{\"values\":[\"detergentmc:tick\"]}",
                entries.get("data/minecraft/tags/functions/tick.json"));
        assertNull(entries.get("data/minecraft/tags/function/tick.json"),
                "1.16.2のパックに単数形のtagsフォルダが無いこと");
    }

    @Test
    void packFormatFor1163Is6() throws Exception {
        // 1.16.3 server jar version.json: pack_version 6 (753ノート§7.3実測)。
        assertEquals(6, DatapackGenerator.PACK_FORMAT_1_16_3, "1.16.3 datapack format");
        assertEquals(6, DatapackGenerator.packFormatFor("1.16.3"));
        Path vanillaDir = tempDir.resolve("vanilla-1-16-3");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.16.3"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":6,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // 1.16.3は複数形 functions / tags/functions（単数形は1.21から）。
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
        assertNull(entries.get("data/detergentmc/function/tick.mcfunction"),
                "1.16.3のパックに単数形の関数フォルダが無いこと");
        assertEquals("{\"values\":[\"detergentmc:tick\"]}",
                entries.get("data/minecraft/tags/functions/tick.json"));
        assertNull(entries.get("data/minecraft/tags/function/tick.json"),
                "1.16.3のパックに単数形のtagsフォルダが無いこと");
    }

    @Test
    void packFormatFor117Is7() throws Exception {
        // 1.17 server jar version.json: pack data 7 (755ノート§7.3実測)。
        assertEquals(7, DatapackGenerator.PACK_FORMAT_1_17, "1.17 datapack format");
        assertEquals(7, DatapackGenerator.packFormatFor("1.17"));
        assertEquals(7, DatapackGenerator.packFormatFor("1.17.1"));
        Path vanillaDir = tempDir.resolve("vanilla-1-17");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.17"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":7,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // 1.17は複数形 functions / tags/functions（単数形は1.21から）。
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
        assertNull(entries.get("data/detergentmc/function/tick.mcfunction"),
                "1.17のパックに単数形の関数フォルダが無いこと");
        assertEquals("{\"values\":[\"detergentmc:tick\"]}",
                entries.get("data/minecraft/tags/functions/tick.json"));
        assertNull(entries.get("data/minecraft/tags/function/tick.json"),
                "1.17のパックに単数形のtagsフォルダが無いこと");
    }

    @Test
    void packFormatFor1171Is7() throws Exception {
        // 1.17.1 server jar version.json: pack data 7 (756ノート§7.3実測)。
        assertEquals(7, DatapackGenerator.PACK_FORMAT_1_17_1, "1.17.1 datapack format");
        assertEquals(7, DatapackGenerator.packFormatFor("1.17.1"));
        assertEquals(8, DatapackGenerator.packFormatFor("1.18"));
        Path vanillaDir = tempDir.resolve("vanilla-1-17-1");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.17.1"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":7,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // 1.17.1は複数形 functions / tags/functions（単数形は1.21から）。
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
        assertNull(entries.get("data/detergentmc/function/tick.mcfunction"),
                "1.17.1のパックに単数形の関数フォルダが無いこと");
        assertEquals("{\"values\":[\"detergentmc:tick\"]}",
                entries.get("data/minecraft/tags/functions/tick.json"));
        assertNull(entries.get("data/minecraft/tags/function/tick.json"),
                "1.17.1のパックに単数形のtagsフォルダが無いこと");
    }

    @Test
    void packFormatFor118Is8() throws Exception {
        // 1.18 server jar version.json: pack data 8 (757ノート§7.3実測)。
        // 1.18 / 1.18.1 share protocol 757 and the format,
        // the same way 1.21 and 1.21.1 share 48.
        assertEquals(8, DatapackGenerator.PACK_FORMAT_1_18, "1.18 datapack format");
        assertEquals(8, DatapackGenerator.packFormatFor("1.18"));
        assertEquals(8, DatapackGenerator.packFormatFor("1.18.1"));
        assertEquals(26, DatapackGenerator.packFormatFor("1.20.4"));
        Path vanillaDir = tempDir.resolve("vanilla-1-18");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.18"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\"pack\":{\"pack_format\":8,\"description\":\"DetergentMC\"}}",
                entries.get("pack.mcmeta"));
        // 1.18は複数形 functions / tags/functions（単数形は1.21から）。
        assertEquals(DatapackGenerator.TICK_FUNCTION,
                entries.get("data/detergentmc/functions/tick.mcfunction"));
        assertNull(entries.get("data/detergentmc/function/tick.mcfunction"),
                "1.18のパックに単数形の関数フォルダが無いこと");
        assertEquals("{\"values\":[\"detergentmc:tick\"]}",
                entries.get("data/minecraft/tags/functions/tick.json"));
        assertNull(entries.get("data/minecraft/tags/function/tick.json"),
                "1.18のパックに単数形のtagsフォルダが無いこと");
    }

    @Test
    void buildRejectsNullVanillaDir() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatapackGenerator().build(null));
    }
}
