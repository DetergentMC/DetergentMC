#!/usr/bin/env python3
"""773テストをなぞって774テストを生成する（ResourcePackBuilder／ResourcePackServer）。

- 既存の ResourcePackBuilder773Test の「774は拒否」テストは 775 拒否へ更新する
  （774が受け入れ対象になったため）。
- 新規 ResourcePackBuilder774Test／ResourcePackServer774Test を 773版から
  文字列置換で生成する。ResourcePackMode774Test は手書き（別ファイル）。
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
TEST = ROOT / "detergentmc-protocol/src/test/java/io/detergentmc/protocol"


def replace(text, old, new, count=1, name=""):
    found = text.count(old)
    if found != count:
        raise SystemExit(
            f"anchor mismatch ({name}): expected {count}, found {found}: {old[:120]!r}")
    return text.replace(old, new)


# --- 773テストの「774拒否」を775拒否へ（既に更新済みならスキップ） ---
p = TEST / "ResourcePackBuilder773Test.java"
t = p.read_text(encoding="utf-8")
if "packFormatForProtocol(774)" in t:
    t = replace(t,
                "    void packFormatForProtocolRejectsUnknownProtocol() {\n"
                "        assertThrows(IllegalArgumentException.class,\n"
                "                () -> ResourcePackBuilder.packFormatForProtocol(774));\n"
                "    }",
                "    void packFormatForProtocolRejectsUnknownProtocol() {\n"
                "        // 774は対応済みのため、拒否されるのは775（26.1以降の未対応番号）。\n"
                "        assertThrows(IllegalArgumentException.class,\n"
                "                () -> ResourcePackBuilder.packFormatForProtocol(775));\n"
                "    }",
                name="builder773-reject-775")
    p.write_text(t, encoding="utf-8")
    print("updated ResourcePackBuilder773Test")
else:
    print("ResourcePackBuilder773Test already updated; skip")

# --- ResourcePackBuilder774Test ---
t = (TEST / "ResourcePackBuilder773Test.java").read_text(encoding="utf-8")
t = replace(t, "773（1.21.9）用のリソースパック（pack_format 69）。",
            "774（1.21.11）用のリソースパック（pack_format 75）。", name="b-class-javadoc")
t = replace(t, "class ResourcePackBuilder773Test {",
            "class ResourcePackBuilder774Test {", name="b-class-name")
t = replace(t, "void builds773PackWithFormat69AndSameDemoAssets()",
            "void builds774PackWithFormat75AndSameDemoAssets()", name="b-t1")
t = replace(t, "void pack773DiffersFromPack772OnlyInMcmeta()",
            "void pack774DiffersFromPack773OnlyInMcmeta()", name="b-t2")
t = replace(t, "void writePack773WritesFormat69()",
            "void writePack774WritesFormat75()", name="b-t3")
t = replace(t, "void packFormatForProtocolRejectsUnknownProtocol() {\n"
               "        // 774は対応済みのため、拒否されるのは775（26.1以降の未対応番号）。\n"
               "        assertThrows(IllegalArgumentException.class,\n"
               "                () -> ResourcePackBuilder.packFormatForProtocol(775));\n"
               "    }",
            "void packFormatForProtocolRejectsUnknownProtocol() {\n"
            "        // 775（26.1以降の未対応番号）は拒否される。\n"
            "        assertThrows(IllegalArgumentException.class,\n"
            "                () -> ResourcePackBuilder.packFormatForProtocol(775));\n"
            "    }",
            name="b-t4")
# 識別子・数値の置換（773→774、69→75、772→773の順。重なりを避けるため
# まず773を774へ、その後772を773へ）。
t = t.replace("built773", "built774")
t = t.replace("pack773", "pack774")
t = t.replace("build773", "build774")
t = t.replace("writePack773", "writePack774")
t = t.replace("PACK_FILE_NAME_773", "PACK_FILE_NAME_774")
t = t.replace("PACK_FORMAT_1_21_9", "PACK_FORMAT_1_21_11")
t = replace(t, "ResourcePackBuilder.BuiltPack pack772 = builder.build772(tempDir.resolve(\"772\"));",
            "ResourcePackBuilder.BuiltPack pack773 = builder.build773(tempDir.resolve(\"773\"));",
            name="b-diff-base")
t = replace(t, "ResourcePackBuilder.BuiltPack pack774 = builder.build774(tempDir.resolve(\"773\"));",
            "ResourcePackBuilder.BuiltPack pack774 = builder.build774(tempDir.resolve(\"774\"));",
            name="b-diff-new")
t = replace(t, "assertNotEquals(pack772.sha1(), pack774.sha1());",
            "assertNotEquals(pack773.sha1(), pack774.sha1());",
            name="b-diff-assert")
t = replace(t, "ResourcePackBuilder.BuiltPack again = builder.build774(tempDir.resolve(\"773b\"));",
            "ResourcePackBuilder.BuiltPack again = builder.build774(tempDir.resolve(\"774b\"));",
            name="b-diff-again")
# （注：pack773.zip は先の pack773→pack774 置換で既に pack774.zip になっている。）
t = replace(t, "// Same four entries as the 1.20.4 pack — nothing 1.21.9-only in here.",
            "// Same four entries as the 1.20.4 pack — nothing 1.21.11-only in here.",
            name="b-comment-1")
t = replace(t,
            "        // pack.mcmeta: 1.21.9 format 69 (not 15, not 18, not 22,\n"
            "        // not 32, not 34, not 42, not 46, not 64). 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4,\n"
            "        // 32 is 1.20.5–1.20.6, 34 is 1.21–1.21.1, 42 is 1.21.2–1.21.3, 46 is 1.21.4\n"
            "        // and 64 is 1.21.7, 69 is 1.21.9 (Minecraft Wiki pack_format).",
            "        // pack.mcmeta: 1.21.11 format 75 (not 15, not 18, not 22,\n"
            "        // not 32, not 34, not 42, not 46, not 64, not 69). 15 is 1.20–1.20.1, 18 is 1.20.2, 22 is 1.20.3–1.20.4,\n"
            "        // 32 is 1.20.5–1.20.6, 34 is 1.21–1.21.1, 42 is 1.21.2–1.21.3, 46 is 1.21.4\n"
            "        // and 64 is 1.21.7, 69 is 1.21.9, 75 is 1.21.11 (Minecraft Wiki pack_format).",
            name="b-comment-2")
t = replace(t, "assertTrue(mcmeta.contains(\"\\\"pack_format\\\":69\"), mcmeta);",
            "assertTrue(mcmeta.contains(\"\\\"pack_format\\\":75\"), mcmeta);", count=2,
            name="b-mcmeta-75")
t = replace(t, "assertTrue(!mcmeta.contains(\"\\\"pack_format\\\":64\"), mcmeta);",
            "assertTrue(!mcmeta.contains(\"\\\"pack_format\\\":64\"), mcmeta);\n"
            "        assertTrue(!mcmeta.contains(\"\\\"pack_format\\\":69\"), mcmeta);",
            name="b-mcmeta-not69")
t = replace(t, "assertEquals(69, ResourcePackBuilder.PACK_FORMAT_1_21_11);",
            "assertEquals(75, ResourcePackBuilder.PACK_FORMAT_1_21_11);", name="b-const-75")
t = replace(t, "assertEquals(69, ResourcePackBuilder.packFormatForProtocol(773));",
            "assertEquals(75, ResourcePackBuilder.packFormatForProtocol(774));", name="b-format-774")
t = replace(t, "assertEquals(ResourcePackBuilder.PACK_FORMAT_1_21_11,\n"
               "                ResourcePackBuilder.packFormatForProtocol(773));",
            "assertEquals(ResourcePackBuilder.PACK_FORMAT_1_21_11,\n"
            "                ResourcePackBuilder.packFormatForProtocol(774));",
            name="b-format-774b")
t = replace(t, "// Rebuilding the 773 pack is deterministic, too.",
            "// Rebuilding the 774 pack is deterministic, too.", name="b-comment-3")
(TEST / "ResourcePackBuilder774Test.java").write_text(t, encoding="utf-8")
print("wrote ResourcePackBuilder774Test")

# --- ResourcePackServer774Test ---
t = (TEST / "ResourcePackServer773Test.java").read_text(encoding="utf-8")
t = replace(t, "773（1.21.9）用のリソースパックの配信（同じ25567番ポートで出し分け）。",
            "774（1.21.11）用のリソースパックの配信（同じ25567番ポートで出し分け）。",
            name="s-class-javadoc")
t = replace(t, "class ResourcePackServer773Test {",
            "class ResourcePackServer774Test {", name="s-class-name")
t = replace(t, "void servesAllElevenPacksOnTheSamePort()",
            "void servesAllTwelvePacksOnTheSamePort()", name="s-t1")
t = replace(t, "void tenPackServerHasNoPack773()",
            "void elevenPackServerHasNoPack774()", name="s-t2")
# 773→774へ、772→773へ（この順で重なりを回避）。
t = t.replace("built773", "built774")
t = t.replace("pack773", "pack774")
t = t.replace("PACK_PATH_773", "PACK_PATH_774")
t = replace(t, "ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve(\"771\"));\n"
               "        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve(\"772\"));\n"
               "        ResourcePackBuilder.BuiltPack built774 = builder.build773(tempDir.resolve(\"773\"));",
            "ResourcePackBuilder.BuiltPack built771 = builder.build771(tempDir.resolve(\"771\"));\n"
            "        ResourcePackBuilder.BuiltPack built772 = builder.build772(tempDir.resolve(\"772\"));\n"
            "        ResourcePackBuilder.BuiltPack built773 = builder.build773(tempDir.resolve(\"773\"));\n"
            "        ResourcePackBuilder.BuiltPack built774 = builder.build774(tempDir.resolve(\"774\"));",
            name="s-built773-774")
t = replace(t, "ResourcePack pack772 = server.pack772();",
            "ResourcePack pack773 = server.pack773();", name="s-pack773-var")
t = replace(t, "ResourcePack pack774 = server.pack774();",
            "ResourcePack pack774 = server.pack774();", name="s-pack774-var")
t = replace(t, "assertEquals(built772.sha1(), pack772.sha1());",
            "assertEquals(built773.sha1(), pack773.sha1());", name="s-pack773-sha1")
t = replace(t, "assertEquals(built774.sha1(), pack774.sha1());",
            "assertEquals(built774.sha1(), pack774.sha1());", name="s-pack774-sha1")
t = replace(t, "assertTrue(pack772.url().endsWith(ResourcePackServer.PACK_PATH_772),\n"
               "                    pack772.url());",
            "assertTrue(pack773.url().endsWith(ResourcePackServer.PACK_PATH_773),\n"
            "                    pack773.url());",
            name="s-endswith-773")
# 旧パックへの言及は773のままにしない（772→773への置換が既に済んでいるものは除く）。
t = replace(t, "// The 1.21.7 pack (pack_format 64) is served too.",
            "// The 1.21.9 pack (pack_format 69) is served too.", name="s-comment-772")
t = replace(t, "// The 1.21.9 pack (pack_format 69) serves 773 clients",
            "// The 1.21.11 pack (pack_format 75) serves 774 clients", name="s-comment-773")
t = replace(t, "assertFalse(pack774.url().equals(pack772.url()));",
            "assertFalse(pack774.url().equals(pack773.url()));",
            name="s-assertfalse-url")
t = replace(t, "assertFalse(pack774.sha1().equals(pack772.sha1()));",
            "assertFalse(pack774.sha1().equals(pack773.sha1()));",
            name="s-assertfalse-sha1")
t = replace(t, "assertEquals(pack774.sha1(), server.packForProtocol(773).sha1());",
            "assertEquals(pack774.sha1(), server.packForProtocol(774).sha1());",
            name="s-packforprotocol-774")
t = replace(t, "assertEquals(built772.sha1(), server.packForProtocol(772).sha1());",
            "assertEquals(built773.sha1(), server.packForProtocol(773).sha1());",
            name="s-packforprotocol-773")
t = replace(t, "// All eleven share one port, but have different paths and SHA-1s.",
            "// All twelve share one port, but have different paths and SHA-1s.",
            name="s-comment-12")
# 11パック呼び出しに built774 を追加する（12パック版コンストラクタ）。
t = replace(t,
            "        ResourcePackServer server = new ResourcePackServer(\n"
            "                built, built763, built764, built766, built767, built768, built769, built770, built771,\n"
            "                built772, built774, 0, false);",
            "        ResourcePackServer server = new ResourcePackServer(\n"
            "                built, built763, built764, built766, built767, built768, built769, built770, built771,\n"
            "                built772, built773, built774, 0, false);",
            name="s-ctor-12")
# 2つ目のテストで build772 の行が built773 へ置換済みか確認し、11パック呼び出しはそのまま。
# （注：assertNull(server.pack774()) は先の pack773→pack774 置換で既に正しい。）
t = replace(t,
            "            // 773パックが無ければ packForProtocol(773) は null になる\n"
            "            // （9パックのときの packForProtocol(772) と同じ契約。呼び出し側が\n"
            "            // 本体パックへフォールバックする）。\n"
            "            assertNull(server.packForProtocol(773));",
            "            // 774パックが無ければ packForProtocol(774) は null になる\n"
            "            // （10パックのときの packForProtocol(773) と同じ契約。呼び出し側が\n"
            "            // 本体パックへフォールバックする）。\n"
            "            assertNull(server.packForProtocol(774));",
            name="s-nopack774-comment")
(TEST / "ResourcePackServer774Test.java").write_text(t, encoding="utf-8")
print("wrote ResourcePackServer774Test")
