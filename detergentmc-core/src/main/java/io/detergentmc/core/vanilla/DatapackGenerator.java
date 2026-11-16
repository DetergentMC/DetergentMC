package io.detergentmc.core.vanilla;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the DetergentMC datapack as a zip file.
 *
 * <p>Why a datapack: vanilla 1.20.4 does not log player commands, so
 * in-game commands ({@code /hello}, {@code /lang}) cannot be picked up
 * from the log. The datapack bridges that gap: a player runs
 * {@code /trigger d_hello} (or {@code d_lang_ja} / {@code d_lang_en}),
 * the tick function notices the trigger score and runs
 * {@code /say [DetergentMC] hello} (etc.), and that {@code /say} line
 * <em>does</em> appear in the vanilla log, where
 * {@link VanillaLogParser} recognises the {@code [DetergentMC]} marker
 * and the bridge dispatches the matching DetergentMC command.
 *
 * <p>Layout for Minecraft 1.20.4 (plural {@code functions} /
 * {@code tags/functions}). For 1.21+ the folders are singular
 * ({@code function} / {@code tags/function}); the plural forms are no
 * longer read, so {@link #writePack(Path, int)} picks the folder name
 * from the pack_format (48〜 → singular, otherwise plural):
 * <ul>
 *   <li>{@code pack.mcmeta} with {@code pack_format} 26 （1.21では48）;</li>
 *   <li>{@code data/minecraft/tags/functions/tick.json} and
 *       {@code load.json} pointing at {@code detergentmc:tick} /
 *       {@code detergentmc:load};</li>
 *   <li>{@code data/detergentmc/functions/tick.mcfunction}: enables the
 *       triggers for every player each tick and turns a set trigger into
 *       a {@code /say [DetergentMC] ...} line, then resets the score;</li>
 *   <li>{@code data/detergentmc/functions/load.mcfunction}: creates the
 *       three {@code trigger} objectives on first load (an
 *       "already exists" error on later loads is harmless).</li>
 * </ul>
 *
 * <p>The pack is placed at
 * {@code <vanillaDir>/world/datapacks/detergentmc.zip} so vanilla reads
 * it when the world loads — even on a first start, when the world folder
 * does not exist yet, because the directories are created here. Zip
 * entry timestamps are fixed at the epoch, the same trick
 * {@code ResourcePackBuilder} uses, so rebuilding yields identical bytes.
 */
public final class DatapackGenerator {

    /** Minecraft 1.17.1 datapack format (1.17.1 server jar version.json: pack data 7; 756ノート§7.3実測). */
    public static final int PACK_FORMAT_1_17_1 = 7;

    /** Minecraft 1.18 / 1.18.1 datapack format (1.18 server jar version.json: pack data 8; 1.18 and 1.18.1 share protocol 757 and the format). */
    public static final int PACK_FORMAT_1_18 = 8;

    /** Minecraft 1.19.1 / 1.19.2 datapack format (Minecraft Wiki: 10 = 1.19–1.19.3). */
    public static final int PACK_FORMAT_1_19_1 = 10;

    /** Minecraft 1.19.3 datapack format (Minecraft Wiki: 10 = 1.19–1.19.3). */
    public static final int PACK_FORMAT_1_19_3 = 10;

    /** Minecraft 1.19.4 datapack format (Minecraft Wiki: 12 = 1.19.4). */
    public static final int PACK_FORMAT_1_19_4 = 12;

    /** Minecraft 1.20.1 datapack format. */
    public static final int PACK_FORMAT_1_20_1 = 15;

    /** Minecraft 1.20.2 datapack format (Minecraft Wiki: 18 = 1.20.2). */
    public static final int PACK_FORMAT_1_20_2 = 18;

    /** Minecraft 1.20.4 datapack format (verified against version.json). */
    public static final int PACK_FORMAT_1_20_4 = 26;

    /** Minecraft 1.20.5 / 1.20.6 datapack format (Minecraft Wiki: 41 = 1.20.5–1.20.6). */
    public static final int PACK_FORMAT_1_20_5 = 41;

    /** Minecraft 1.21 / 1.21.1 datapack format (Minecraft Wiki: 48 = 1.21–1.21.1). */
    public static final int PACK_FORMAT_1_21 = 48;

    /** Minecraft 1.21.2 / 1.21.3 datapack format (Minecraft Wiki: 57 = 1.21.2–1.21.3). */
    public static final int PACK_FORMAT_1_21_2 = 57;

    /** Minecraft 1.21.4 datapack format (Minecraft Wiki: 61 = 1.21.4). */
    public static final int PACK_FORMAT_1_21_4 = 61;

    /** Minecraft 1.21.5 datapack format (Minecraft Wiki: 71 = 1.21.5). */
    public static final int PACK_FORMAT_1_21_5 = 71;

    /** Minecraft 1.21.6 datapack format (Minecraft Wiki: 80 = 1.21.6). */
    public static final int PACK_FORMAT_1_21_6 = 80;

    /** Minecraft 1.21.7 / 1.21.8 datapack format (Minecraft Wiki: 81 = 1.21.7 / 1.21.8). */
    public static final int PACK_FORMAT_1_21_7 = 81;

    /** Minecraft 1.21.9 / 1.21.10 datapack format (Minecraft Wiki: 88 = 1.21.9 / 1.21.10). */
    public static final int PACK_FORMAT_1_21_9 = 88;

    /** Minecraft 1.21.11 datapack format (Minecraft Wiki: 94 = 1.21.11). */
    public static final int PACK_FORMAT_1_21_11 = 94;

    /** Minecraft 26.1 datapack format (26.1 server jar version.json: data 101.1; 26.1.1 / 26.1.2 share it). */
    public static final int PACK_FORMAT_26_1 = 101;

    /** Datapack format for a Minecraft version ("1.17.1" -&gt; 7, "1.18" / "1.18.1" -&gt; 8, "1.19.1" / "1.19.2" -&gt; 10, "1.19.3" -&gt; 10, "1.19.4" -&gt; 12, "1.20.1" -&gt; 15, "1.20.2" -&gt; 18, "1.20.4" -&gt; 26, "1.20.5" / "1.20.6" -&gt; 41, "1.21" / "1.21.1" -&gt; 48, "1.21.2" / "1.21.3" -&gt; 57, "1.21.4" -&gt; 61, "1.21.5" -&gt; 71, "1.21.6" -&gt; 80, "1.21.7" / "1.21.8" -&gt; 81, "1.21.9" / "1.21.10" -&gt; 88, "1.21.11" -&gt; 94, "26.1" / "26.1.1" / "26.1.2" -&gt; 101). */
    public static int packFormatFor(String minecraftVersion) {
        if ("1.17.1".equals(minecraftVersion)) {
            return PACK_FORMAT_1_17_1;
        }
        if ("1.18".equals(minecraftVersion) || "1.18.1".equals(minecraftVersion)) {
            return PACK_FORMAT_1_18;
        }
        if ("1.19.1".equals(minecraftVersion) || "1.19.2".equals(minecraftVersion)) {
            return PACK_FORMAT_1_19_1;
        }
        if ("1.19.3".equals(minecraftVersion)) {
            return PACK_FORMAT_1_19_3;
        }
        if ("1.19.4".equals(minecraftVersion)) {
            return PACK_FORMAT_1_19_4;
        }
        if ("1.20.1".equals(minecraftVersion)) {
            return PACK_FORMAT_1_20_1;
        }
        if ("1.20.2".equals(minecraftVersion)) {
            return PACK_FORMAT_1_20_2;
        }
        if ("1.20.5".equals(minecraftVersion) || "1.20.6".equals(minecraftVersion)) {
            return PACK_FORMAT_1_20_5;
        }
        if ("1.21".equals(minecraftVersion) || "1.21.1".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21;
        }
        if ("1.21.2".equals(minecraftVersion) || "1.21.3".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_2;
        }
        if ("1.21.4".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_4;
        }
        if ("1.21.5".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_5;
        }
        if ("1.21.6".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_6;
        }
        if ("1.21.7".equals(minecraftVersion) || "1.21.8".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_7;
        }
        if ("1.21.9".equals(minecraftVersion) || "1.21.10".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_9;
        }
        if ("1.21.11".equals(minecraftVersion)) {
            return PACK_FORMAT_1_21_11;
        }
        if ("26.1".equals(minecraftVersion) || "26.1.1".equals(minecraftVersion)
                || "26.1.2".equals(minecraftVersion)) {
            return PACK_FORMAT_26_1;
        }
        return PACK_FORMAT_1_20_4;
    }

    /** pack.mcmeta for the given datapack format. */
    public static String packMcmeta(int packFormat) {
        return "{\"pack\":{\"pack_format\":" + packFormat
                + ",\"description\":\"DetergentMC\"}}";
    }

    /** File name of the generated pack inside the world's datapacks folder. */
    public static final String PACK_FILE_NAME = "detergentmc.zip";

    /** Default vanilla world folder name (vanilla's {@code level-name}). */
    public static final String WORLD_DIR_NAME = "world";

    /** Marker the tick function prints, so the log parser can tell these lines apart from chat. */
    public static final String MARKER = "[DetergentMC]";

    public static final String PACK_MCMETA =
            "{\"pack\":{\"pack_format\":26,\"description\":\"DetergentMC\"}}";

    public static final String TICK_TAG_JSON = "{\"values\":[\"detergentmc:tick\"]}";
    public static final String LOAD_TAG_JSON = "{\"values\":[\"detergentmc:load\"]}";

    /** Runs every tick: enables the triggers, then converts set triggers into marker lines. */
    public static final String TICK_FUNCTION =
            "scoreboard players enable @a d_hello\n"
                    + "scoreboard players enable @a d_lang_ja\n"
                    + "scoreboard players enable @a d_lang_en\n"
                    + "execute as @a[scores={d_hello=1..}] run say [DetergentMC] hello\n"
                    + "execute as @a[scores={d_hello=1..}] run scoreboard players set @s d_hello 0\n"
                    + "execute as @a[scores={d_lang_ja=1..}] run say [DetergentMC] lang ja\n"
                    + "execute as @a[scores={d_lang_ja=1..}] run scoreboard players set @s d_lang_ja 0\n"
                    + "execute as @a[scores={d_lang_en=1..}] run say [DetergentMC] lang en\n"
                    + "execute as @a[scores={d_lang_en=1..}] run scoreboard players set @s d_lang_en 0\n";

    /** Runs on load: creates the three trigger objectives. */
    public static final String LOAD_FUNCTION =
            "scoreboard objectives add d_hello trigger\n"
                    + "scoreboard objectives add d_lang_ja trigger\n"
                    + "scoreboard objectives add d_lang_en trigger\n";

    /**
     * Builds the pack into
     * {@code <vanillaDir>/world/datapacks/detergentmc.zip} (creating the
     * directories if needed) and returns the file. Uses the 1.20.4
     * datapack format (26).
     */
    public Path build(Path vanillaDir) throws IOException {
        return build(vanillaDir, PACK_FORMAT_1_20_4);
    }

    /**
     * Same as {@link #build(Path)}, but with an explicit datapack format
     * (15 for Minecraft 1.20.1, 18 for 1.20.2, 26 for 1.20.4, 41 for
     * 1.20.5 / 1.20.6, 48 for 1.21 / 1.21.1). A 1.20.1 or 1.20.2 server rejects (or warns about)
     * a pack whose format it does not know, so the format must match the
     * running vanilla version.
     */
    public Path build(Path vanillaDir, int packFormat) throws IOException {
        if (vanillaDir == null) {
            throw new IllegalArgumentException("vanillaDir is required");
        }
        Path datapacksDir = vanillaDir.resolve(WORLD_DIR_NAME).resolve("datapacks");
        Files.createDirectories(datapacksDir);
        Path file = datapacksDir.resolve(PACK_FILE_NAME);
        writePack(file, packFormat);
        return file;
    }

    /** Writes the pack zip to {@code file} (1.20.4 format). */
    public void writePack(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_20_4);
    }

    /** Writes the pack zip to {@code file} with the given datapack format. */
    public void writePack(Path file, int packFormat) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file is required");
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        // 1.21+ (pack_format 48〜) では関数フォルダが単数形 function /
        // tags/function になり、複数形 functions / tags/functions は
        // 読み込まれなくなる。1.20.6以前は複数形のまま。
        String functionDir = packFormat >= PACK_FORMAT_1_21 ? "function" : "functions";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            addTextEntry(zip, "pack.mcmeta", packMcmeta(packFormat));
            addTextEntry(zip, "data/minecraft/tags/" + functionDir + "/tick.json", TICK_TAG_JSON);
            addTextEntry(zip, "data/minecraft/tags/" + functionDir + "/load.json", LOAD_TAG_JSON);
            addTextEntry(zip, "data/detergentmc/" + functionDir + "/tick.mcfunction", TICK_FUNCTION);
            addTextEntry(zip, "data/detergentmc/" + functionDir + "/load.mcfunction", LOAD_FUNCTION);
        }
    }

    private static void addTextEntry(ZipOutputStream zip, String name, String text)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // Fixed timestamp so rebuilding the same pack yields the same zip
        // instead of depending on the clock.
        entry.setLastModifiedTime(java.nio.file.attribute.FileTime.from(Instant.EPOCH));
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
