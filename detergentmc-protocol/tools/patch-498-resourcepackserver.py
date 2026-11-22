#!/usr/bin/env python3
"""ResourcePackServer.java に 498（1.14.4）のパック配線を足す。

573 のときと同じ流儀：PACK_PATH_498、pack498File／sha1498 フィールド、
最長コンストラクタへの pack498File／sha1498 引数（573 の直前）、
他コンストラクタへの null 代入、start／stop のバイト読み込みと破棄、
HTTP コンテキスト、url498()／pack498()、packForProtocol の 498 分岐、
BuiltPack 版コンストラクタの委譲調整と23パック版の追加。

最長コンストラクタの呼び出し側（DetergentServer）は別途直すこと。
使い方: python3 tools/patch-498-resourcepackserver.py
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
    if "PACK_PATH_498" in text:
        print("skip（既存）: 498 配線はすでにある")
        return 0

    # 1. パス定数（573 の直前）。
    text = sub(text,
               '    /** Path of the 1.15 (pack_format 5) variant, when one is served. */\n'
               '    public static final String PACK_PATH_573 = "/detergentmc-pack-1.15.zip";',
               '    /** Path of the 1.14.4 (pack_format 4) variant, when one is served. */\n'
               '    public static final String PACK_PATH_498 = "/detergentmc-pack-1.14.4.zip";\n'
               '\n'
               '    /** Path of the 1.15 (pack_format 5) variant, when one is served. */\n'
               '    public static final String PACK_PATH_573 = "/detergentmc-pack-1.15.zip";', 1)

    # 2. フィールド。
    text = sub(text,
               "    private final Path pack573File;\n    private final String sha1573;",
               "    private final Path pack498File;\n    private final String sha1498;\n"
               "    private final Path pack573File;\n    private final String sha1573;", 1)
    text = sub(text,
               "    private byte[] pack573Bytes;",
               "    private byte[] pack498Bytes;\n    private byte[] pack573Bytes;", 1)

    # 3. 他コンストラクタの null 代入（6 件）。
    text = sub(text,
               "        this.pack573File = null;\n        this.sha1573 = null;",
               "        this.pack498File = null;\n        this.sha1498 = null;\n"
               "        this.pack573File = null;\n        this.sha1573 = null;", 6)

    # 4. 最長コンストラクタ：引数・検証・代入。
    text = sub(text,
               "    public ResourcePackServer(Path packFile, String sha1, Path pack573File, String sha1573,",
               "    public ResourcePackServer(Path packFile, String sha1, Path pack498File, String sha1498, Path pack573File, String sha1573,", 1)
    text = sub(text,
               '        if (pack573File != null && (sha1573 == null || sha1573.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.15 pack is required");\n'
               '        }',
               '        if (pack498File != null && (sha1498 == null || sha1498.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.14.4 pack is required");\n'
               '        }\n'
               '        if (pack573File != null && (sha1573 == null || sha1573.isBlank())) {\n'
               '            throw new IllegalArgumentException("sha1 for the 1.15 pack is required");\n'
               '        }', 1)
    text = sub(text,
               "        this.pack573File = pack573File;\n        this.sha1573 = sha1573;",
               "        this.pack498File = pack498File;\n        this.sha1498 = sha1498;\n"
               "        this.pack573File = pack573File;\n        this.sha1573 = sha1573;", 1)

    # 5. start(): バイト読み込みと HTTP コンテキスト。
    text = sub(text,
               "        if (pack573File != null) {\n            pack573Bytes = readAndVerify(pack573File, sha1573);\n        }",
               "        if (pack498File != null) {\n            pack498Bytes = readAndVerify(pack498File, sha1498);\n        }\n"
               "        if (pack573File != null) {\n            pack573Bytes = readAndVerify(pack573File, sha1573);\n        }", 1)
    text = sub(text,
               "        if (pack573Bytes != null) {\n            httpServer.createContext(PACK_PATH_573, exchange -> serveBytes(exchange, pack573Bytes));\n        }",
               "        if (pack498Bytes != null) {\n            httpServer.createContext(PACK_PATH_498, exchange -> serveBytes(exchange, pack498Bytes));\n        }\n"
               "        if (pack573Bytes != null) {\n            httpServer.createContext(PACK_PATH_573, exchange -> serveBytes(exchange, pack573Bytes));\n        }", 1)

    # 6. stop(): バイト破棄。
    text = sub(text,
               "        pack573Bytes = null;",
               "        pack498Bytes = null;\n        pack573Bytes = null;", 1)

    # 7. url498() / pack498()（url573 の直前）。
    text = sub(text,
               "    /** The download URL of the 1.15 variant, or {@code null} when not served. */\n"
               "    public synchronized String url573() {",
               "    /** The download URL of the 1.14.4 variant, or {@code null} when not served. */\n"
               "    public synchronized String url498() {\n"
               "        if (pack498File == null) {\n"
               "            return null;\n"
               "        }\n"
               "        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;\n"
               "        return \"http://127.0.0.1:\" + port + PACK_PATH_498;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * The 1.14.4 {@link ResourcePack} to offer to 498 clients, or\n"
               "     * {@code null} when no 1.14.4 variant is served.\n"
               "     */\n"
               "    public synchronized ResourcePack pack498() {\n"
               "        return pack498File == null ? null\n"
               "                : new ResourcePack(url498(), sha1498, required, promptKey);\n"
               "    }\n"
               "\n"
               "    /** The download URL of the 1.15 variant, or {@code null} when not served. */\n"
               "    public synchronized String url573() {", 1)

    # 8. packForProtocol の 498 分岐（573 分岐の直前）。
    text = sub(text,
               "    public synchronized ResourcePack packForProtocol(int clientProtocol) {\n"
               "        if (clientProtocol == ProtocolVersion.V1_15) {",
               "    public synchronized ResourcePack packForProtocol(int clientProtocol) {\n"
               "        if (clientProtocol == ProtocolVersion.V1_14_4) {\n"
               "            // 498パックが無い旧コンストラクタでは573パック、さらに無い場合は\n"
               "            // 575・578・758・759・760・761・762・763パックの順に代替する。\n"
               "            ResourcePack pack498 = pack498();\n"
               "            if (pack498 != null) {\n"
               "                return pack498;\n"
               "            }\n"
               "            ResourcePack pack573 = pack573();\n"
               "            if (pack573 != null) {\n"
               "                return pack573;\n"
               "            }\n"
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
               "        if (clientProtocol == ProtocolVersion.V1_15) {", 1)

    # 9. BuiltPack 版コンストラクタの委譲に 498 分の null, null を差し込む。
    text = sub(text,
               "        this(built.file(), built.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),",
               "        this(built.file(), built.sha1(), null, null, built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),", 1)
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),", 1)
    text = sub(text,
               "        this(built.file(), built.sha1(), null, null, null, null, built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),",
               "        this(built.file(), built.sha1(), null, null, null, null, null, null, built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),", 1)

    # 10. BuiltPack 版の最長コンストラクタ（built498 付き、23パック）を追加。
    anchor = "    /** Convenience: serve all twenty-two packs, with an explicit prompt key. */"
    new_ctor = (
        "    /** Convenience: serve all twenty-three packs, with an explicit prompt key. */\n"
        "    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
        "            ResourcePackBuilder.BuiltPack built498, ResourcePackBuilder.BuiltPack built573, ResourcePackBuilder.BuiltPack built575, ResourcePackBuilder.BuiltPack built578, ResourcePackBuilder.BuiltPack built758, ResourcePackBuilder.BuiltPack built759, ResourcePackBuilder.BuiltPack built760, ResourcePackBuilder.BuiltPack built761,\n"
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
        "        this(built.file(), built.sha1(), built498.file(), built498.sha1(), built573.file(), built573.sha1(), built575.file(), built575.sha1(), built578.file(), built578.sha1(), built758.file(), built758.sha1(), built759.file(), built759.sha1(), built760.file(), built760.sha1(),\n"
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
