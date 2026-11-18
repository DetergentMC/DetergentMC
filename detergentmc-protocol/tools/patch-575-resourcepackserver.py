#!/usr/bin/env python3
"""ResourcePackServer.java に 575（1.15.1）のパック配線を足す。

578 のときと同じ流儀：PACK_PATH_575、pack575File／sha1575 フィールド、
最長コンストラクタへの pack575File／sha1575 引数（578 の直前）、
他コンストラクタへの null 代入、start／stop のバイト読み込みと破棄、
HTTP コンテキスト、url575()／pack575()、packForProtocol の 575 分岐。

最長コンストラクタの呼び出し側（BuiltPack 版コンストラクタと
DetergentServer）は別途直すこと。使い方:
    python3 tools/patch-575-resourcepackserver.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ResourcePackServer.java"


def sub(text, old, new, count):
    found = text.count(old)
    if found != count:
        print(f"NG: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")
    if "PACK_PATH_575" in text:
        print("skip（既存）: 575 配線はすでにある")
        return 0

    # 1. パス定数（578 の直前）。
    text = sub(text,
               '    public static final String PACK_PATH_578 = "/detergentmc-pack-1.15.2.zip";',
               '    /** Path of the 1.15.1 (pack_format 5) variant, when one is served. */\n'
               '    public static final String PACK_PATH_575 = "/detergentmc-pack-1.15.1.zip";\n'
               '\n'
               '    public static final String PACK_PATH_578 = "/detergentmc-pack-1.15.2.zip";', 1)

    # 2. フィールド。
    text = sub(text,
               "    private final Path pack578File;\n    private final String sha1578;",
               "    private final Path pack575File;\n    private final String sha1575;\n"
               "    private final Path pack578File;\n    private final String sha1578;", 1)
    text = sub(text,
               "    private byte[] pack578Bytes;",
               "    private byte[] pack575Bytes;\n    private byte[] pack578Bytes;", 1)

    # 3. 他コンストラクタの null 代入（6 件）。
    text = sub(text,
               "        this.pack578File = null;",
               "        this.pack575File = null;\n        this.sha1575 = null;\n"
               "        this.pack578File = null;", 6)

    # 4. 最長コンストラクタ：引数・検証・代入。
    text = sub(text,
               "    public ResourcePackServer(Path packFile, String sha1, Path pack578File, String sha1578,",
               "    public ResourcePackServer(Path packFile, String sha1, Path pack575File, String sha1575, Path pack578File, String sha1578,", 1)
    text = sub(text,
               '        if (pack578File != null && (sha1578 == null || sha1578.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.15.2 pack is required");\n'
               '        }',
               '        if (pack575File != null && (sha1575 == null || sha1575.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.15.1 pack is required");\n'
               '        }\n'
               '        if (pack578File != null && (sha1578 == null || sha1578.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.15.2 pack is required");\n'
               '        }', 1)
    text = sub(text,
               "        this.pack578File = pack578File;\n        this.sha1578 = sha1578;",
               "        this.pack575File = pack575File;\n        this.sha1575 = sha1575;\n"
               "        this.pack578File = pack578File;\n        this.sha1578 = sha1578;", 1)

    # 5. start(): バイト読み込みと HTTP コンテキスト。
    text = sub(text,
               "        if (pack578File != null) {\n            pack578Bytes = readAndVerify(pack578File, sha1578);\n        }",
               "        if (pack575File != null) {\n            pack575Bytes = readAndVerify(pack575File, sha1575);\n        }\n"
               "        if (pack578File != null) {\n            pack578Bytes = readAndVerify(pack578File, sha1578);\n        }", 1)
    text = sub(text,
               "        if (pack578Bytes != null) {\n            httpServer.createContext(PACK_PATH_578, exchange -> serveBytes(exchange, pack578Bytes));\n        }",
               "        if (pack575Bytes != null) {\n            httpServer.createContext(PACK_PATH_575, exchange -> serveBytes(exchange, pack575Bytes));\n        }\n"
               "        if (pack578Bytes != null) {\n            httpServer.createContext(PACK_PATH_578, exchange -> serveBytes(exchange, pack578Bytes));\n        }", 1)

    # 6. stop(): バイト破棄。
    text = sub(text,
               "        pack578Bytes = null;",
               "        pack575Bytes = null;\n        pack578Bytes = null;", 1)

    # 7. url575() / pack575()（url578 の直前）。
    text = sub(text,
               "    /** The download URL of the 1.15.2 variant, or {@code null} when not served. */\n"
               "    public synchronized String url578() {",
               "    /** The download URL of the 1.15.1 variant, or {@code null} when not served. */\n"
               "    public synchronized String url575() {\n"
               "        if (pack575File == null) {\n"
               "            return null;\n"
               "        }\n"
               "        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;\n"
               "        return \"http://127.0.0.1:\" + port + PACK_PATH_575;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * The 1.15.1 {@link ResourcePack} to offer to 575 clients, or\n"
               "     * {@code null} when no 1.15.1 variant is served.\n"
               "     */\n"
               "    public synchronized ResourcePack pack575() {\n"
               "        return pack575File == null ? null\n"
               "                : new ResourcePack(url575(), sha1575, required, promptKey);\n"
               "    }\n"
               "\n"
               "    /** The download URL of the 1.15.2 variant, or {@code null} when not served. */\n"
               "    public synchronized String url578() {", 1)

    # 8. packForProtocol の 575 分岐（578 分岐の直前）。
    text = sub(text,
               "    public synchronized ResourcePack packForProtocol(int clientProtocol) {\n"
               "        if (clientProtocol == ProtocolVersion.V1_15_2) {",
               "    public synchronized ResourcePack packForProtocol(int clientProtocol) {\n"
               "        if (clientProtocol == ProtocolVersion.V1_15_1) {\n"
               "            // 575パックが無い旧コンストラクタでは578パック、さらに無い場合は\n"
               "            // 758・759・760・761・762・763パックの順に代替する。\n"
               "            ResourcePack pack575 = pack575();\n"
               "            if (pack575 != null) {\n"
               "                return pack575;\n"
               "            }\n"
               "            ResourcePack pack578 = pack578();\n"
               "            if (pack578 != null) {\n"
               "                return pack578;\n"
               "            }\n"
               "            ResourcePack pack758 = pack758();\n"
               "            if (pack758 != null) {\n"
               "                return pack758;\n"
               "            }\n"
               "            ResourcePack pack759 = pack759();\n"
               "            if (pack759 != null) {\n"
               "                return pack759;\n"
               "            }\n"
               "            ResourcePack pack760 = pack760();\n"
               "            if (pack760 != null) {\n"
               "                return pack760;\n"
               "            }\n"
               "            ResourcePack pack761 = pack761();\n"
               "            if (pack761 != null) {\n"
               "                return pack761;\n"
               "            }\n"
               "            ResourcePack pack762 = pack762();\n"
               "            return pack762 != null ? pack762 : pack763();\n"
               "        }\n"
               "        if (clientProtocol == ProtocolVersion.V1_15_2) {", 1)

    # 9. BuiltPack 版の最長コンストラクタ：委譲に null, null を差し込む。
    text = sub(text,
               "        this(built.file(), built.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),",
               "        this(built.file(), built.sha1(), null, null, built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),", 1)

    # 10. BuiltPack 版の最長コンストラクタ（built575 付き）を追加。
    anchor = ("    /** Convenience: serve all twenty packs, with an explicit prompt key. */")
    new_ctor = (
        "    /** Convenience: serve all twenty-one packs, with an explicit prompt key. */\n"
        "    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
        "            ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,\n"
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
        "        this(built.file(), built.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),\n"
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
    text = sub(text, anchor, new_ctor + anchor, 1)

    P.write_text(text, encoding="utf-8")
    print("ResourcePackServer.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
