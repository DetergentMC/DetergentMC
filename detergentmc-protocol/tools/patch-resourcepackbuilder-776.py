#!/usr/bin/env python3
"""ResourcePackBuilder.java に 776（26.2）パックを追加する。

pack_format 88（26.2 server jar version.json: pack resource 88.0）。
26.2 にホットフィックスは無いため 776 用パックは1つだけ。
"""
import sys
from pathlib import Path

PATH = Path("detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackBuilder.java")
failures = []


def rep(src, old, new, note, count=1):
    n = src.count(old)
    if n != count:
        failures.append(f"{note}: 期待 {count} 回だが {n} 回（{old[:70]!r}）")
        return src
    return src.replace(old, new)


def main():
    src = PATH.read_text(encoding="utf-8")

    src = rep(src,
              "    /** Minecraft 26.1 resource-pack format (26.1 server jar version.json: pack resource 84.0). */\n"
              "    public static final int PACK_FORMAT_26_1 = 84;",
              "    /** Minecraft 26.1 resource-pack format (26.1 server jar version.json: pack resource 84.0). */\n"
              "    public static final int PACK_FORMAT_26_1 = 84;\n"
              "\n"
              "    /** Minecraft 26.2 resource-pack format (26.2 server jar version.json: pack resource 88.0). */\n"
              "    public static final int PACK_FORMAT_26_2 = 88;",
              "PACK_FORMAT_26_2")

    src = rep(src,
              "    /** File name of the generated 26.1 pack inside the output directory\n"
              "     * (protocol 775, resource-pack format 84). One pack serves 26.1,\n"
              "     * 26.1.1 and 26.1.2: they share protocol 775 and resource-pack format 84.\n"
              "     */\n"
              "    public static final String PACK_FILE_NAME_775 = \"detergentmc-pack-26.1.zip\";",
              "    /** File name of the generated 26.1 pack inside the output directory\n"
              "     * (protocol 775, resource-pack format 84). One pack serves 26.1,\n"
              "     * 26.1.1 and 26.1.2: they share protocol 775 and resource-pack format 84.\n"
              "     */\n"
              "    public static final String PACK_FILE_NAME_775 = \"detergentmc-pack-26.1.zip\";\n"
              "\n"
              "    /** File name of the generated 26.2 pack inside the output directory\n"
              "     * (protocol 776, resource-pack format 88). 26.2 has no hotfix releases.\n"
              "     */\n"
              "    public static final String PACK_FILE_NAME_776 = \"detergentmc-pack-26.2.zip\";",
              "PACK_FILE_NAME_776")

    src = rep(src,
              "    private static final String PACK_MCMETA_775 = \"{\\\"pack\\\":{\\\"pack_format\\\":84,\"\n"
              "            + \"\\\"description\\\":\\\"DetergentMC resource pack\\\"}}\";",
              "    private static final String PACK_MCMETA_775 = \"{\\\"pack\\\":{\\\"pack_format\\\":84,\"\n"
              "            + \"\\\"description\\\":\\\"DetergentMC resource pack\\\"}}\";\n"
              "\n"
              "    private static final String PACK_MCMETA_776 = \"{\\\"pack\\\":{\\\"pack_format\\\":88,\"\n"
              "            + \"\\\"description\\\":\\\"DetergentMC resource pack\\\"}}\";",
              "PACK_MCMETA_776")

    src = rep(src,
              "     * 69 for 773 (1.21.9／1.21.10), 75 for 774 (1.21.11), 84 for 775 (26.1).",
              "     * 69 for 773 (1.21.9／1.21.10), 75 for 774 (1.21.11), 84 for 775 (26.1),\n"
              "     * 88 for 776 (26.2).",
              "packFormatForProtocol javadoc")
    src = rep(src,
              "            case ProtocolVersion.V26_1 -> PACK_FORMAT_26_1;",
              "            case ProtocolVersion.V26_1 -> PACK_FORMAT_26_1;\n"
              "            case ProtocolVersion.V26_2 -> PACK_FORMAT_26_2;",
              "packFormatForProtocol 分岐")

    src = rep(src,
              "    /**\n"
              "     * Builds the 26.1 variant of the pack into\n"
              "     * {@code outputDir/detergentmc-pack-26.1.zip} and returns the file\n"
              "     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only\n"
              "     * {@code pack.mcmeta} differs ({@code pack_format} 84). One pack serves\n"
              "     * 26.1, 26.1.1 and 26.1.2: they share protocol 775.\n"
              "     */\n"
              "    public BuiltPack build775(Path outputDir) throws IOException {\n"
              "        return build(outputDir, PACK_FORMAT_26_1, PACK_FILE_NAME_775);\n"
              "    }",
              "    /**\n"
              "     * Builds the 26.1 variant of the pack into\n"
              "     * {@code outputDir/detergentmc-pack-26.1.zip} and returns the file\n"
              "     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only\n"
              "     * {@code pack.mcmeta} differs ({@code pack_format} 84). One pack serves\n"
              "     * 26.1, 26.1.1 and 26.1.2: they share protocol 775.\n"
              "     */\n"
              "    public BuiltPack build775(Path outputDir) throws IOException {\n"
              "        return build(outputDir, PACK_FORMAT_26_1, PACK_FILE_NAME_775);\n"
              "    }\n"
              "\n"
              "    /**\n"
              "     * Builds the 26.2 variant of the pack into\n"
              "     * {@code outputDir/detergentmc-pack-26.2.zip} and returns the file\n"
              "     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only\n"
              "     * {@code pack.mcmeta} differs ({@code pack_format} 88). 26.2 has no\n"
              "     * hotfix releases, so one pack serves all of 26.2: protocol 776.\n"
              "     */\n"
              "    public BuiltPack build776(Path outputDir) throws IOException {\n"
              "        return build(outputDir, PACK_FORMAT_26_2, PACK_FILE_NAME_776);\n"
              "    }",
              "build776")

    src = rep(src,
              "    /** Writes the 26.1 variant of the pack zip to {@code file}. */\n"
              "    public void writePack775(Path file) throws IOException {\n"
              "        writePack(file, PACK_FORMAT_26_1);\n"
              "    }",
              "    /** Writes the 26.1 variant of the pack zip to {@code file}. */\n"
              "    public void writePack775(Path file) throws IOException {\n"
              "        writePack(file, PACK_FORMAT_26_1);\n"
              "    }\n"
              "\n"
              "    /** Writes the 26.2 variant of the pack zip to {@code file}. */\n"
              "    public void writePack776(Path file) throws IOException {\n"
              "        writePack(file, PACK_FORMAT_26_2);\n"
              "    }",
              "writePack776")

    src = rep(src,
              "            case PACK_FORMAT_26_1 -> PACK_MCMETA_775;",
              "            case PACK_FORMAT_26_1 -> PACK_MCMETA_775;\n"
              "            case PACK_FORMAT_26_2 -> PACK_MCMETA_776;",
              "writePack switch")

    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        print("何も書き込まない。")
        return 1
    PATH.write_text(src, encoding="utf-8")
    print("ResourcePackBuilder.java に 776 を追加した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
