#!/usr/bin/env python3
"""ResourcePackBuilder.java へ Java 1.21.7／1.21.8（プロトコル772）の対応を入れる。

pack_format 64（リソースパック）は Minecraft Wiki の pack_format 表で
確認した値。771の流儀をなぞる：PACK_FORMAT_1_21_6／PACK_FILE_NAME_771／
PACK_MCMETA_771／build771／writePack771 の直後に772版を増やす。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ResourcePackBuilder.java"


def edit(path, pairs):
    src = path.read_text(encoding="utf-8")
    for label, old, new in pairs:
        n = src.count(old)
        if n != 1:
            print(f"NG: {label}: anchor が {n} 回見つかった（1回のはず）", file=sys.stderr)
            sys.exit(1)
        src = src.replace(old, new, 1)
        print(f"OK: {label}")
    path.write_text(src, encoding="utf-8")


edit(PATH, [
    ("class javadoc 772 section", """\
 * for 1.21.6 is 80, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 */""",
     """\
 * for 1.21.6 is 80, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 *
 * <h2>1.21.7／1.21.8 variant (protocol 772, {@code pack_format} 64)</h2>
 *
 * <p>The same demo assets are also built as a 1.21.7 pack
 * ({@link #build772(Path)}): again only {@code pack.mcmeta} differs
 * ({@code pack_format} 64). The 1.21.7 resource-pack format is
 * 64, not 63: Minecraft Wiki's pack_format table lists 64 for
 * 1.21.7. A 1.21.7／1.21.8 client offered the format-63 pack would show
 * it as incompatible, so 772 clients get their own pack. (The data-pack format
 * for 1.21.7／1.21.8 is 81, but that is a separate number — see
 * {@code DatapackGenerator} — and must not be confused with this one.)
 */"""),
    ("PACK_FORMAT_1_21_7", """\
    /** Minecraft 1.21.6 resource-pack format (Minecraft Wiki: pack_format 63). */
    public static final int PACK_FORMAT_1_21_6 = 63;""",
     """\
    /** Minecraft 1.21.6 resource-pack format (Minecraft Wiki: pack_format 63). */
    public static final int PACK_FORMAT_1_21_6 = 63;

    /** Minecraft 1.21.7 / 1.21.8 resource-pack format (Minecraft Wiki: pack_format 64). */
    public static final int PACK_FORMAT_1_21_7 = 64;"""),
    ("PACK_FILE_NAME_772", """\
    /** File name of the generated 1.21.6 pack inside the output directory
     * (protocol 771, resource-pack format 63).
     */
    public static final String PACK_FILE_NAME_771 = "detergentmc-pack-1.21.6.zip";""",
     """\
    /** File name of the generated 1.21.6 pack inside the output directory
     * (protocol 771, resource-pack format 63).
     */
    public static final String PACK_FILE_NAME_771 = "detergentmc-pack-1.21.6.zip";

    /** File name of the generated 1.21.7 pack inside the output directory
     * (protocol 772, resource-pack format 64). One pack serves both 1.21.7
     * and 1.21.8: they share protocol 772 and resource-pack format 64.
     */
    public static final String PACK_FILE_NAME_772 = "detergentmc-pack-1.21.7.zip";"""),
    ("PACK_MCMETA_772", """\
    private static final String PACK_MCMETA_771 = "{\\"pack\\":{\\"pack_format\\":63,"
            + "\\"description\\":\\"DetergentMC resource pack\\"}}";""",
     """\
    private static final String PACK_MCMETA_771 = "{\\"pack\\":{\\"pack_format\\":63,"
            + "\\"description\\":\\"DetergentMC resource pack\\"}}";

    private static final String PACK_MCMETA_772 = "{\\"pack\\":{\\"pack_format\\":64,"
            + "\\"description\\":\\"DetergentMC resource pack\\"}}";"""),
    ("packFormatForProtocol 772", """\
            case ProtocolVersion.V1_21_6 -> PACK_FORMAT_1_21_6;""",
     """\
            case ProtocolVersion.V1_21_6 -> PACK_FORMAT_1_21_6;
            case ProtocolVersion.V1_21_7 -> PACK_FORMAT_1_21_7;"""),
    ("build772", """\
    /**
     * Builds the 1.21.6 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.6.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 63).
     */
    public BuiltPack build771(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_6, PACK_FILE_NAME_771);
    }""",
     """\
    /**
     * Builds the 1.21.6 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.6.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 63).
     */
    public BuiltPack build771(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_6, PACK_FILE_NAME_771);
    }

    /**
     * Builds the 1.21.7 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.21.7.zip} and returns the file
     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only
     * {@code pack.mcmeta} differs ({@code pack_format} 64). One pack serves
     * both 1.21.7 and 1.21.8: they share protocol 772.
     */
    public BuiltPack build772(Path outputDir) throws IOException {
        return build(outputDir, PACK_FORMAT_1_21_7, PACK_FILE_NAME_772);
    }"""),
    ("writePack772", """\
    /** Writes the 1.21.6 variant of the pack zip to {@code file}. */
    public void writePack771(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_6);
    }""",
     """\
    /** Writes the 1.21.6 variant of the pack zip to {@code file}. */
    public void writePack771(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_6);
    }

    /** Writes the 1.21.7 variant of the pack zip to {@code file}. */
    public void writePack772(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_21_7);
    }"""),
    ("writePack switch 772", """\
            case PACK_FORMAT_1_21_6 -> PACK_MCMETA_771;""",
     """\
            case PACK_FORMAT_1_21_6 -> PACK_MCMETA_771;
            case PACK_FORMAT_1_21_7 -> PACK_MCMETA_772;"""),
])

print("ResourcePackBuilder.java に 772 の対応を挿入した")
