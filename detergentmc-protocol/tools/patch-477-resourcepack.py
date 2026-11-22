#!/usr/bin/env python3
"""477（1.14）のリソースパック配線を足す。

対象：ResourcePackBuilder、ResourcePackServer、ResourcePackMode。
480 のときと同じ流儀。477 の pack_format は 4（実バニラ 1.14 の
version.json で実測）で 480 と同じ値のため、mcmeta の中身は
PACK_MCMETA_480 と同一になる。

使い方: python3 tools/patch-477-resourcepack.py
冪等：477 が既にあれば何もしない。
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
    if "PACK_FORMAT_1_14 =" in text:
        print("skip（既存）: ResourcePackBuilder")
        return
    # 1. pack_format 定数（1.14.1 の直前）。
    text = sub(text,
               "    public static final int PACK_FORMAT_1_14_1 = 4;",
               "    /**\n"
               "     * Minecraft 1.14 resource-pack format (real vanilla 1.14 server\n"
               "     * jar {@code version.json}: {@code pack_version} 4; same value as\n"
               "     * 1.14.1).\n"
               "     */\n"
               "    public static final int PACK_FORMAT_1_14 = 4;\n"
               "\n"
               "    public static final int PACK_FORMAT_1_14_1 = 4;", 1, "builder")
    # 2. ファイル名定数。
    text = sub(text,
               '    public static final String PACK_FILE_NAME_480 = "detergentmc-pack-1.14.1.zip";',
               '    /** File name of the generated 1.14 pack inside the output directory. */\n'
               '    public static final String PACK_FILE_NAME_477 = "detergentmc-pack-1.14.zip";\n'
               "\n"
               '    public static final String PACK_FILE_NAME_480 = "detergentmc-pack-1.14.1.zip";', 1, "builder")
    # 3. packFormatForProtocol の分岐。
    text = sub(text,
               "            case ProtocolVersion.V1_14_1 -> PACK_FORMAT_1_14_1;",
               "            case ProtocolVersion.V1_14 -> PACK_FORMAT_1_14;\n"
               "            case ProtocolVersion.V1_14_1 -> PACK_FORMAT_1_14_1;", 1, "builder")
    # 4. build477（build480 の直前）。
    text = sub(text,
               "    public BuiltPack build480(Path outputDir) throws IOException {",
               "    /**\n"
               "     * Builds the 1.14 variant of the pack into\n"
               "     * {@code outputDir/detergentmc-pack-1.14.zip} and returns the file\n"
               "     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only\n"
               "     * {@code pack.mcmeta} differs ({@code pack_format} 4, identical\n"
               "     * content to the 1.14.1 variant).\n"
               "     */\n"
               "    public BuiltPack build477(Path outputDir) throws IOException {\n"
               "        return build(outputDir, PACK_FORMAT_1_14, PACK_FILE_NAME_477);\n"
               "    }\n"
               "\n"
               "    public BuiltPack build480(Path outputDir) throws IOException {", 1, "builder")
    # 5. writePack477（writePack480 の直前）。
    text = sub(text,
               "    public void writePack480(Path file) throws IOException {",
               "    /** Writes the 1.14 variant of the pack zip to {@code file}. */\n"
               "    public void writePack477(Path file) throws IOException {\n"
               "        writePack(file, PACK_FORMAT_1_14);\n"
               "    }\n"
               "\n"
               "    public void writePack480(Path file) throws IOException {", 1, "builder")
    p.write_text(text, encoding="utf-8")
    print("ResourcePackBuilder.java を更新した")


def server():
    p = SRC / "ResourcePackServer.java"
    text = p.read_text(encoding="utf-8")
    if "PACK_PATH_477" in text:
        print("skip（既存）: ResourcePackServer")
        return
    # 1. パス定数（480 の直前）。
    text = sub(text,
               '    public static final String PACK_PATH_480 = "/detergentmc-pack-1.14.1.zip";',
               '    /** Path of the 1.14 (pack_format 4) variant, when one is served. */\n'
               '    public static final String PACK_PATH_477 = "/detergentmc-pack-1.14.zip";\n'
               "\n"
               '    public static final String PACK_PATH_480 = "/detergentmc-pack-1.14.1.zip";', 1, "server")
    # 2. フィールド。
    text = sub(text,
               "    private final Path pack480File;\n    private final String sha1480;",
               "    private final Path pack477File;\n    private final String sha1477;\n"
               "    private final Path pack480File;\n    private final String sha1480;", 1, "server")
    text = sub(text,
               "    private byte[] pack480Bytes;",
               "    private byte[] pack477Bytes;\n    private byte[] pack480Bytes;", 1, "server")
    # 3. 他コンストラクタの null 代入（6件）。
    text = sub(text,
               "        this.pack480File = null;\n        this.sha1480 = null;",
               "        this.pack477File = null;\n        this.sha1477 = null;\n"
               "        this.pack480File = null;\n        this.sha1480 = null;", 6, "server")
    # 4. 最長コンストラクタ：引数・検証・代入。
    text = sub(text,
               "    public ResourcePackServer(Path packFile, String sha1, Path pack480File, String sha1480,",
               "    public ResourcePackServer(Path packFile, String sha1, Path pack477File, String sha1477, Path pack480File, String sha1480,", 1, "server")
    text = sub(text,
               '        if (pack480File != null && (sha1480 == null || sha1480.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.14.1 pack is required");\n'
               '        }',
               '        if (pack477File != null && (sha1477 == null || sha1477.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.14 pack is required");\n'
               '        }\n'
               '        if (pack480File != null && (sha1480 == null || sha1480.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.14.1 pack is required");\n'
               '        }', 1, "server")
    text = sub(text,
               "        this.pack480File = pack480File;\n        this.sha1480 = sha1480;",
               "        this.pack477File = pack477File;\n        this.sha1477 = sha1477;\n"
               "        this.pack480File = pack480File;\n        this.sha1480 = sha1480;", 1, "server")
    # 5. start(): バイト読み込みと HTTP コンテキスト。
    text = sub(text,
               "        if (pack480File != null) {\n            pack480Bytes = readAndVerify(pack480File, sha1480);\n        }",
               "        if (pack477File != null) {\n            pack477Bytes = readAndVerify(pack477File, sha1477);\n        }\n"
               "        if (pack480File != null) {\n            pack480Bytes = readAndVerify(pack480File, sha1480);\n        }", 1, "server")
    text = sub(text,
               "        if (pack480Bytes != null) {\n            httpServer.createContext(PACK_PATH_480, exchange -> serveBytes(exchange, pack480Bytes));\n        }",
               "        if (pack477Bytes != null) {\n            httpServer.createContext(PACK_PATH_477, exchange -> serveBytes(exchange, pack477Bytes));\n        }\n"
               "        if (pack480Bytes != null) {\n            httpServer.createContext(PACK_PATH_480, exchange -> serveBytes(exchange, pack480Bytes));\n        }", 1, "server")
    # 6. stop(): バイト破棄。
    text = sub(text,
               "        pack480Bytes = null;",
               "        pack477Bytes = null;\n        pack480Bytes = null;", 1, "server")
    # 7. url477() / pack477()（url480 の直前）。
    text = sub(text,
               "    public synchronized String url480() {",
               "    /** The download URL of the 1.14 variant, or {@code null} when not served. */\n"
               "    public synchronized String url477() {\n"
               "        if (pack477File == null) {\n"
               "            return null;\n"
               "        }\n"
               "        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;\n"
               "        return \"http://127.0.0.1:\" + port + PACK_PATH_477;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * The 1.14 {@link ResourcePack} to offer to 477 clients, or\n"
               "     * {@code null} when no 1.14 variant is served.\n"
               "     */\n"
               "    public synchronized ResourcePack pack477() {\n"
               "        return pack477File == null ? null\n"
               "                : new ResourcePack(url477(), sha1477, required, promptKey);\n"
               "    }\n"
               "\n"
               "    public synchronized String url480() {", 1, "server")
    # 8. packForProtocol の 477 分岐（480 分岐の直前）。
    text = sub(text,
               "    public synchronized ResourcePack packForProtocol(int clientProtocol) {\n"
               "        if (clientProtocol == ProtocolVersion.V1_14_1) {",
               "    public synchronized ResourcePack packForProtocol(int clientProtocol) {\n"
               "        if (clientProtocol == ProtocolVersion.V1_14) {\n"
               "            // 477パックが無い場合は480パック（同じ pack_format 4）、\n"
               "            // さらに無い場合は480分岐と同じ順に代替する。\n"
               "            ResourcePack pack477 = pack477();\n"
               "            if (pack477 != null) {\n"
               "                return pack477;\n"
               "            }\n"
               "            return packForProtocol(ProtocolVersion.V1_14_1);\n"
               "        }\n"
               "        if (clientProtocol == ProtocolVersion.V1_14_1) {", 1, "server")
    # 9. BuiltPack 版コンストラクタの委譲（最長行きの7件）に 477 分の
    # null ペアを差し込む。
    text = sub(text,
               "        this(built.file(), built.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(),",
               "        this(built.file(), built.sha1(), null, null, built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, built498.file(), built498.sha1(), built573.file(), built573.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, built498.file(), built498.sha1(), built573.file(), built573.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, built573.file(), built573.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, built573.file(), built573.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, built575.file(), built575.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, built575.file(), built575.sha1(),", 1, "server")
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, built578.file(), built578.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, built578.file(), built578.sha1(),", 1, "server")
    # 10. BuiltPack 版の最長コンストラクタ（built477 付き、27パック）を追加。
    anchor = "    /** Convenience: serve all twenty-six packs, with an explicit prompt key. */"
    new_ctor = (
        "    /** Convenience: serve all twenty-seven packs, with an explicit prompt key. */\n"
        "    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
        "            ResourcePackBuilder.BuiltPack built477, ResourcePackBuilder.BuiltPack built480, ResourcePackBuilder.BuiltPack built485, ResourcePackBuilder.BuiltPack built490, ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,\n"
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
        "        this(built.file(), built.sha1(), built477.file(), built477.sha1(), built480.file(), built480.sha1(), built485.file(), built485.sha1(), built490.file(), built490.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),\n"
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
    if "pack477" in text:
        print("skip（既存）: ResourcePackMode")
        return
    # 1. Decision レコードに pack477 を追加（末尾）。
    text = sub(text,
               "ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,\n"
               "            ResourcePack pack490, ResourcePack pack485, ResourcePack pack480) {",
               "ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,\n"
               "            ResourcePack pack490, ResourcePack pack485, ResourcePack pack480, ResourcePack pack477) {", 1, "mode")
    # 2. 正規コンストラクタへ直接委譲する呼び出し（引数総数 28）だけに null を追加。
    deleg = re.compile(r"this\(kind, pack, problem,(.*?)\);", re.S)
    matches = deleg.findall(text)
    if len(matches) != 13:
        print(f"NG[mode]: 委譲コンストラクタが {len(matches)} 件（期待 13）")
        sys.exit(1)
    added = []

    def add_null(m):
        args = m.group(1)
        total = 3 + args.count(",") + 1
        if total != 28:
            return m.group(0)
        added.append(total)
        return "this(kind, pack, problem," + args.rstrip() + ", null);"

    text = deleg.sub(add_null, text)
    if len(added) != 4:
        print(f"NG[mode]: null を足した委譲が {len(added)} 件（期待 4）")
        sys.exit(1)
    # 3. packForProtocol() に 477 の分岐（480 の直前）。
    text = sub(text,
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_14_1) {",
               "        public ResourcePack packForProtocol(int protocol) {\n"
               "            if (protocol == ProtocolVersion.V1_14) {\n"
               "                return pack477 != null ? pack477\n"
               "                        : pack480 != null ? pack480\n"
               "                        : pack485 != null ? pack485\n"
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
               "            if (protocol == ProtocolVersion.V1_14_1) {", 1, "mode")
    # 4. decide() 本体の引数に url477／sha1477 を追加（480 の直後）。
    text = sub(text,
               "            String configuredUrl480, String configuredSha1480,\n"
               "            boolean required, String promptKey) {",
               "            String configuredUrl480, String configuredSha1480,\n"
               "            String configuredUrl477, String configuredSha1477,\n"
               "            boolean required, String promptKey) {", 1, "mode")
    # 5. pack477 ローカル変数。
    text = sub(text,
               "            ResourcePack pack480 = null;\n",
               "            ResourcePack pack480 = null;\n"
               "            ResourcePack pack477 = null;\n", 1, "mode")
    # 6. url477 の検証ブロック（480 の検証ブロックの直後。
    #    フォールバック先は 480 のパック）。
    text = sub(text,
               "                    pack480 = new ResourcePack(url480,\n"
               "                            configuredSha1480.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n",
               "                    pack480 = new ResourcePack(url480,\n"
               "                            configuredSha1480.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n"
               "            String url477 = configuredUrl477 == null ? \"\" : configuredUrl477.trim();\n"
               "            if (!url477.isEmpty()) {\n"
               "                if (!url477.startsWith(\"https://\")) {\n"
               "                    problems.add(\"resource-pack.url-477 must start with https:// (got '\"\n"
               "                            + url477 + \"'); 477 clients will be offered the 1.14.1 pack instead\");\n"
               "                } else if (!isValidSha1(configuredSha1477)) {\n"
               "                    problems.add(\"resource-pack.sha1-477 is missing or not a 40-digit hex \"\n"
               "                            + \"SHA-1: '\" + configuredSha1477\n"
               "                            + \"'; 477 clients will be offered the 1.14.1 pack instead\");\n"
               "                } else {\n"
               "                    pack477 = new ResourcePack(url477,\n"
               "                            configuredSha1477.trim().toLowerCase(Locale.ROOT),\n"
               "                            required, prompt);\n"
               "                }\n"
               "            }\n", 1, "mode")
    # 7. EXTERNAL の Decision 生成に pack477 を渡す。
    text = sub(text,
               "pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485, pack480);",
               "pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485, pack480, pack477);", 1, "mode")
    p.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java を更新した（decide() の呼び出し側は別途直すこと）")


def mode_overloads():
    """フル decide() へ直接委譲する2つのオーバーロードに null ペアを足す。"""
    p = SRC / "ResourcePackMode.java"
    text = p.read_text(encoding="utf-8")
    if "configuredUrl759, configuredSha1759, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, required, promptKey);" in text:
        print("skip（既存）: ResourcePackMode オーバーロード")
        return
    text = sub(text,
               "configuredUrl775, configuredSha1775, configuredUrl776, configuredSha1776, configuredUrl760, configuredSha1760, configuredUrl759, configuredSha1759, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, required, promptKey);",
               "configuredUrl775, configuredSha1775, configuredUrl776, configuredSha1776, configuredUrl760, configuredSha1760, configuredUrl759, configuredSha1759, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, required, promptKey);", 1, "mode-overloads")
    text = sub(text,
               "configuredUrl775, configuredSha1775, configuredUrl776, configuredSha1776, configuredUrl760, configuredSha1760, configuredUrl759, configuredSha1759, configuredUrl758, configuredSha1758, null, null, null, null, null, null, null, null, null, null, null, null, null, null, required, promptKey);",
               "configuredUrl775, configuredSha1775, configuredUrl776, configuredSha1776, configuredUrl760, configuredSha1760, configuredUrl759, configuredSha1759, configuredUrl758, configuredSha1758, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, required, promptKey);", 1, "mode-overloads")
    p.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java のオーバーロードを更新した")


if __name__ == "__main__":
    builder()
    server()
    mode()
    mode_overloads()
