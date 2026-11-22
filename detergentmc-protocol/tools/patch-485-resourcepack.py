#!/usr/bin/env python3
"""485（1.14.2）のリソースパック配線を足す。

対象：ResourcePackBuilder、ResourcePackServer、ResourcePackMode。
490 のときと同じ流儀。485 の pack_format は 4（実バニラ 1.14.2 の
version.json で実測）で 490 と同じ値のため、mcmeta の中身は
PACK_MCMETA_490 と同一になる。

使い方: python3 tools/patch-485-resourcepack.py
冪等：485 が既にあれば何もしない。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/io/detergentmc/protocol"


def sub(text, old, new, count, where):
    found = text.count(old)
    if found != count:
        print(f"NG[{where}]: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def builder():
    p = SRC / "ResourcePackBuilder.java"
    text = p.read_text(encoding="utf-8")
    if "PACK_FORMAT_1_14_2" in text:
        print("skip（既存）: ResourcePackBuilder")
        return
    # 1. pack_format 定数（1.14.3 の直前）。
    text = sub(text,
               "    public static final int PACK_FORMAT_1_14_3 = 4;",
               "    /**\n"
               "     * Minecraft 1.14.2 resource-pack format (real vanilla 1.14.2 server\n"
               "     * jar {@code version.json}: {@code pack_version} 4; same value as\n"
               "     * 1.14.3).\n"
               "     */\n"
               "    public static final int PACK_FORMAT_1_14_2 = 4;\n"
               "\n"
               "    public static final int PACK_FORMAT_1_14_3 = 4;", 1, "builder")
    # 2. ファイル名定数。
    text = sub(text,
               '    public static final String PACK_FILE_NAME_490 = "detergentmc-pack-1.14.3.zip";',
               '    /** File name of the generated 1.14.2 pack inside the output directory. */\n'
               '    public static final String PACK_FILE_NAME_485 = "detergentmc-pack-1.14.2.zip";\n'
               "\n"
               '    public static final String PACK_FILE_NAME_490 = "detergentmc-pack-1.14.3.zip";', 1, "builder")
    # 3. packFormatForProtocol の分岐。
    text = sub(text,
               "            case ProtocolVersion.V1_14_3 -> PACK_FORMAT_1_14_3;",
               "            case ProtocolVersion.V1_14_2 -> PACK_FORMAT_1_14_2;\n"
               "            case ProtocolVersion.V1_14_3 -> PACK_FORMAT_1_14_3;", 1, "builder")
    # 4. build485（build490 の直前）。
    text = sub(text,
               "    public BuiltPack build490(Path outputDir) throws IOException {",
               "    /**\n"
               "     * Builds the 1.14.2 variant of the pack into\n"
               "     * {@code outputDir/detergentmc-pack-1.14.2.zip} and returns the file\n"
               "     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only\n"
               "     * {@code pack.mcmeta} differs ({@code pack_format} 4, identical\n"
               "     * content to the 1.14.3 variant).\n"
               "     */\n"
               "    public BuiltPack build485(Path outputDir) throws IOException {\n"
               "        return build(outputDir, PACK_FORMAT_1_14_2, PACK_FILE_NAME_485);\n"
               "    }\n"
               "\n"
               "    public BuiltPack build490(Path outputDir) throws IOException {", 1, "builder")
    # 5. writePack485（writePack490 の直前）。
    text = sub(text,
               "    /** Writes the 1.14.3 variant of the pack zip to {@code file}. */\n"
               "    public void writePack490(Path file) throws IOException {",
               "    /** Writes the 1.14.2 variant of the pack zip to {@code file}. */\n"
               "    public void writePack485(Path file) throws IOException {\n"
               "        writePack(file, PACK_FORMAT_1_14_2);\n"
               "    }\n"
               "\n"
               "    /** Writes the 1.14.3 variant of the pack zip to {@code file}. */\n"
               "    public void writePack490(Path file) throws IOException {", 1, "builder")
    p.write_text(text, encoding="utf-8")
    print("ResourcePackBuilder.java を更新した")


def server():
    p = SRC / "ResourcePackServer.java"
    text = p.read_text(encoding="utf-8")
    if "PACK_PATH_485" in text:
        print("skip（既存）: ResourcePackServer")
        return
    # 1. パス定数（490 の直前）。
    text = sub(text,
               '    public static final String PACK_PATH_490 = "/detergentmc-pack-1.14.3.zip";',
               '    /** Path of the 1.14.2 (pack_format 4) variant, when one is served. */\n'
               '    public static final String PACK_PATH_485 = "/detergentmc-pack-1.14.2.zip";\n'
               "\n"
               '    public static final String PACK_PATH_490 = "/detergentmc-pack-1.14.3.zip";', 1, "server")
    # 2. フィールド。
    text = sub(text,
               "    private final Path pack490File;\n    private final String sha1490;",
               "    private final Path pack485File;\n    private final String sha1485;\n"
               "    private final Path pack490File;\n    private final String sha1490;", 1, "server")
    text = sub(text,
               "    private byte[] pack490Bytes;",
               "    private byte[] pack485Bytes;\n    private byte[] pack490Bytes;", 1, "server")
    # 3. 他コンストラクタの null 代入（6件）。
    text = sub(text,
               "        this.pack490File = null;\n        this.sha1490 = null;",
               "        this.pack485File = null;\n        this.sha1485 = null;\n"
               "        this.pack490File = null;\n        this.sha1490 = null;", 6, "server")
    # 4. 最長コンストラクタ：引数・検証・代入。
    text = sub(text,
               "    public ResourcePackServer(Path packFile, String sha1, Path pack490File, String sha1490,",
               "    public ResourcePackServer(Path packFile, String sha1, Path pack485File, String sha1485, Path pack490File, String sha1490,", 1, "server")
    text = sub(text,
               '        if (pack490File != null && (sha1490 == null || sha1490.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.14.3 pack is required");\n'
               '        }',
               '        if (pack485File != null && (sha1485 == null || sha1485.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.14.2 pack is required");\n'
               '        }\n'
               '        if (pack490File != null && (sha1490 == null || sha1490.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.14.3 pack is required");\n'
               '        }', 1, "server")
    text = sub(text,
               "        this.pack490File = pack490File;\n        this.sha1490 = sha1490;",
               "        this.pack485File = pack485File;\n        this.sha1485 = sha1485;\n"
               "        this.pack490File = pack490File;\n        this.sha1490 = sha1490;", 1, "server")
    # 5. start(): バイト読み込みと HTTP コンテキスト。
    text = sub(text,
               "        if (pack490File != null) {\n            pack490Bytes = readAndVerify(pack490File, sha1490);\n        }",
               "        if (pack485File != null) {\n            pack485Bytes = readAndVerify(pack485File, sha1485);\n        }\n"
               "        if (pack490File != null) {\n            pack490Bytes = readAndVerify(pack490File, sha1490);\n        }", 1, "server")
    text = sub(text,
               "        if (pack490Bytes != null) {\n            httpServer.createContext(PACK_PATH_490, exchange -> serveBytes(exchange, pack490Bytes));\n        }",
               "        if (pack485Bytes != null) {\n            httpServer.createContext(PACK_PATH_485, exchange -> serveBytes(exchange, pack485Bytes));\n        }\n"
               "        if (pack490Bytes != null) {\n            httpServer.createContext(PACK_PATH_490, exchange -> serveBytes(exchange, pack490Bytes));\n        }", 1, "server")
    # 6. stop(): バイト破棄。
    text = sub(text,
               "        pack490Bytes = null;",
               "        pack485Bytes = null;\n        pack490Bytes = null;", 1, "server")
    # 7. url485() / pack485()（url490 の直前）。
    text = sub(text,
               "    public synchronized String url490() {",
               "    /** The download URL of the 1.14.2 variant, or {@code null} when not served. */\n"
               "    public synchronized String url485() {\n"
               "        if (pack485File == null) {\n"
               "            return null;\n"
               "        }\n"
               "        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;\n"
               "        return \"http://127.0.0.1:\" + port + PACK_PATH_485;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * The 1.14.2 {@link ResourcePack} to offer to 485 clients, or\n"
               "     * {@code null} when no 1.14.2 variant is served.\n"
               "     */\n"
               "    public synchronized ResourcePack pack485() {\n"
               "        return pack485File == null ? null\n"
               "                : new ResourcePack(url485(), sha1485, required, promptKey);\n"
               "    }\n"
               "\n"
               "    public synchronized String url490() {", 1, "server")
    # 8. packForProtocol の 485 分岐（490 分岐の直前）。
    text = sub(text,
               "    public synchronized ResourcePack packForProtocol(int clientProtocol) {\n"
               "        if (clientProtocol == ProtocolVersion.V1_14_3) {",
               "    public synchronized ResourcePack packForProtocol(int clientProtocol) {\n"
               "        if (clientProtocol == ProtocolVersion.V1_14_2) {\n"
               "            // 485パックが無い場合は490パック（同じ pack_format 4）、\n"
               "            // さらに無い場合は490分岐と同じ順に代替する。\n"
               "            ResourcePack pack485 = pack485();\n"
               "            if (pack485 != null) {\n"
               "                return pack485;\n"
               "            }\n"
               "            return packForProtocol(ProtocolVersion.V1_14_3);\n"
               "        }\n"
               "        if (clientProtocol == ProtocolVersion.V1_14_3) {", 1, "server")
    # 9. BuiltPack 版コンストラクタの委譲（最長行きの5件）に 485 分を差し込む。
    text = sub(text,
               "        this(built.file(), built.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(),",
               "        this(built.file(), built.sha1(), null, null, built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, built498.file(), built498.sha1(), built573.file(), built573.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, built498.file(), built498.sha1(), built573.file(), built573.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, built573.file(), built573.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, built573.file(), built573.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, built575.file(), built575.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, built575.file(), built575.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, built578.file(), built578.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, built578.file(), built578.sha1(),", 1, "server")
    # 10. BuiltPack 版の最長コンストラクタ（built485 付き、25パック）を追加。
    anchor = "    /** Convenience: serve all twenty-four packs, with an explicit prompt key. */"
    new_ctor = (
        "    /** Convenience: serve all twenty-five packs, with an explicit prompt key. */\n"
        "    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
        "            ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,\n"
        "            ResourcePackBuilder.BuiltPack built762,\n"
        "            ResourcePackBuilder.BuiltPack built763,\n"
        "            ResourcePackBuilder.BuiltPack built764,\n"
        "            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,\n"
        "            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,\n"
        "            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,\n"
        "            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,\n"
        "            ResourcePackBuilder.BuiltPack built774,\n"
        "            ResourcePackBuilder.BuiltPack built775,\n"
        "            ResourcePackBuilder.BuiltPack built776,\n"
        "            int port, boolean required, String promptKey) {\n"
        "        this(built.file(), built.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),\n"
        "                built761.file(), built761.sha1(), built762.file(), built762.sha1(),\n"
        "                built763.file(), built763.sha1(),\n"
        "                built764.file(), built764.sha1(), built766.file(), built766.sha1(),\n"
        "                built767.file(), built767.sha1(), built768.file(), built768.sha1(),\n"
        "                built769.file(), built769.sha1(), built770.file(), built770.sha1(),\n"
        "                built771.file(), built771.sha1(), built772.file(), built772.sha1(),\n"
        "                built773.file(), built773.sha1(), built774.file(), built774.sha1(),\n"
        "                built775.file(), built775.sha1(),\n"
        "                built776.file(), built776.sha1(),\n"
        "                port, required, promptKey);\n"
        "    }\n"
        "\n"
    )
    text = sub(text, anchor, new_ctor + anchor, 1, "server")
    p.write_text(text, encoding="utf-8")
    print("ResourcePackServer.java を更新した")


def mode():
    p = SRC / "ResourcePackMode.java"
    text = p.read_text(encoding="utf-8")
    if "pack485" in text:
        print("skip（既存）: ResourcePackMode")
        return
    # 1. Decision レコードに pack485 を追加（末尾）。
    text = sub(text,
               "ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,\n"
               "            ResourcePack pack490) {",
               "ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,\n"
               "            ResourcePack pack490, ResourcePack pack485) {", 1, "mode")
    # 2. 正規コンストラクタへ直接委譲する呼び出し（引数総数 26）だけに null を追加。
    deleg = re.compile(r"this\(kind, pack, problem,(.*?)\);", re.S)
    matches = deleg.findall(text)
    if len(matches) != 13:
        print(f"NG[mode]: 委譲コンストラクタが {len(matches)} 件（期待 13）")
        sys.exit(1)
    added = []

    def add_null(m):
        args = m.group(1)
        total = 3 + args.count(",") + 1
        if total != 26:
            return m.group(0)
        added.append(total)
        return "this(kind, pack, problem," + args.rstrip() + ", null);"

    text = deleg.sub(add_null, text)
    if len(added) != 4:
        print(f"NG[mode]: null を足した委譲が {len(added)} 件（期待 4）")
        sys.exit(1)
    # 3. packForProtocol() に 485 の分岐（490 の直前）。
    text = sub(text,
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_14_3) {",
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_14_2) {\n"
               "                return pack485 != null ? pack485\n"
               "                        : pack490 != null ? pack490\n"
               "                        : pack498 != null ? pack498\n"
               "                        : pack573 != null ? pack573\n"
               "                        : pack575 != null ? pack575\n"
               "                        : pack578 != null ? pack578\n"
               "                        : pack758 != null ? pack758\n"
               "                        : pack759 != null ? pack759\n"
               "                        : pack760 != null ? pack760\n"
               "                        : pack761 != null ? pack761\n"
               "                        : pack762 != null ? pack762\n"
               "                        : pack763 != null ? pack763 : pack;\n"
               "            }\n"
               "            if (protocol == ProtocolVersion.V1_14_3) {", 1, "mode")
    # 4. decide() 本体の引数に url485／sha1485 を追加（490 の直後）。
    text = sub(text,
               "            String configuredUrl490, String configuredSha1490,\n"
               "            boolean required, String promptKey) {",
               "            String configuredUrl490, String configuredSha1490,\n"
               "            String configuredUrl485, String configuredSha1485,\n"
               "            boolean required, String promptKey) {", 1, "mode")
    # 5. pack485 ローカル変数。
    text = sub(text,
               "            ResourcePack pack490 = null;\n",
               "            ResourcePack pack490 = null;\n"
               "            ResourcePack pack485 = null;\n", 1, "mode")
    # 6. url485 の検証ブロック（490 の検証ブロックの直後。
    #    フォールバック先は 490 のパック）。
    text = sub(text,
               "                    pack490 = new ResourcePack(url490,\n"
               "                            configuredSha1490.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n",
               "                    pack490 = new ResourcePack(url490,\n"
               "                            configuredSha1490.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n"
               "            String url485 = configuredUrl485 == null ? \"\" : configuredUrl485.trim();\n"
               "            if (!url485.isEmpty()) {\n"
               "                if (!url485.startsWith(\"https://\")) {\n"
               "                    problems.add(\"resource-pack.url-485 must start with https:// (got '\"\n"
               "                            + url485 + \"'); 485 clients will be offered the 1.14.3 pack instead\");\n"
               "                } else if (!isValidSha1(configuredSha1485)) {\n"
               "                    problems.add(\"resource-pack.sha1-485 is missing or not a 40-digit hex \"\n"
               "                            + \"SHA-1: '\" + configuredSha1485\n"
               "                            + \"'; 485 clients will be offered the 1.14.3 pack instead\");\n"
               "                } else {\n"
               "                    pack485 = new ResourcePack(url485,\n"
               "                            configuredSha1485.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n", 1, "mode")
    # 7. EXTERNAL の Decision 生成に pack485 を渡す。
    text = sub(text,
               "pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490);",
               "pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485);", 1, "mode")
    p.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java を更新した（decide() の呼び出し側は別途直すこと）")


if __name__ == "__main__":
    builder()
    server()
    mode()
