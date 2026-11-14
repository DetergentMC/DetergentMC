#!/usr/bin/env python3
"""774（1.21.11）リソースパック配線：773のやり方をなぞって774を追加する。

対象：
  - ResourcePackBuilder（PACK_FORMAT_1_21_11=75、build774／writePack774、
    packFormatForProtocol、PACK_MCMETA_774）
  - ResourcePackMode（Decision に pack774、26引数 decide、委譲の null 数更新）
  - ResourcePackServer（PACK_PATH_774、12パック目）
  - DatapackGenerator（PACK_FORMAT_1_21_11=94）
  - DetergentServer（resourcePack774、decide呼び出し、createClientProxy、hostResourcePack）
  - ClientProxy（pack774フィールド、18引数コンストラクタ、検証、アクセサ、resolveHandshake）

全置き換えは exact-match＋出現回数検査。失敗したら何も書き戻さない。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent


def replace(text, old, new, count=1, name=""):
    found = text.count(old)
    if found != count:
        raise SystemExit(
            f"anchor mismatch ({name}): expected {count}, found {found}: {old[:100]!r}")
    return text.replace(old, new)


def write(path, text):
    path.write_text(text, encoding="utf-8")
    print(f"wrote {path.relative_to(ROOT)}")


# ---------------------------------------------------------------- ResourcePackBuilder
p = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackBuilder.java"
t = p.read_text(encoding="utf-8")

# 773のjavadocのコピペ残り（64→69）を直す（2か所）。
t = replace(t,
            "    /** Minecraft 1.21.9 / 1.21.10 resource-pack format (Minecraft Wiki: pack_format 64). */",
            "    /** Minecraft 1.21.9 / 1.21.10 resource-pack format (Minecraft Wiki: pack_format 69). */",
            name="builder-773-format-javadoc")
t = replace(t,
            "    /** File name of the generated 1.21.9 pack inside the output directory\n"
            "     * (protocol 773, resource-pack format 64). One pack serves both 1.21.9",
            "    /** File name of the generated 1.21.9 pack inside the output directory\n"
            "     * (protocol 773, resource-pack format 69). One pack serves both 1.21.9",
            name="builder-773-filename-javadoc")

t = replace(t,
            "    /** Minecraft 1.21.9 / 1.21.10 resource-pack format (Minecraft Wiki: pack_format 69). */\n"
            "    public static final int PACK_FORMAT_1_21_9 = 69;\n",
            "    /** Minecraft 1.21.9 / 1.21.10 resource-pack format (Minecraft Wiki: pack_format 69). */\n"
            "    public static final int PACK_FORMAT_1_21_9 = 69;\n"
            "\n"
            "    /** Minecraft 1.21.11 resource-pack format (Minecraft Wiki: pack_format 75). */\n"
            "    public static final int PACK_FORMAT_1_21_11 = 75;\n",
            name="builder-format-const")
t = replace(t,
            "    public static final String PACK_FILE_NAME_773 = \"detergentmc-pack-1.21.9.zip\";\n",
            "    public static final String PACK_FILE_NAME_773 = \"detergentmc-pack-1.21.9.zip\";\n"
            "\n"
            "    /** File name of the generated 1.21.11 pack inside the output directory\n"
            "     * (protocol 774, resource-pack format 75).\n"
            "     */\n"
            "    public static final String PACK_FILE_NAME_774 = \"detergentmc-pack-1.21.11.zip\";\n",
            name="builder-filename-774")
t = replace(t,
            "    private static final String PACK_MCMETA_773 = \"{\\\"pack\\\":{\\\"pack_format\\\":69,\"\n"
            "            + \"\\\"description\\\":\\\"DetergentMC resource pack\\\"}}\";\n",
            "    private static final String PACK_MCMETA_773 = \"{\\\"pack\\\":{\\\"pack_format\\\":69,\"\n"
            "            + \"\\\"description\\\":\\\"DetergentMC resource pack\\\"}}\";\n"
            "\n"
            "    private static final String PACK_MCMETA_774 = \"{\\\"pack\\\":{\\\"pack_format\\\":75,\"\n"
            "            + \"\\\"description\\\":\\\"DetergentMC resource pack\\\"}}\";\n",
            name="builder-mcmeta-774")
t = replace(t,
            "     * 55 for 770 (1.21.5), 63 for 771 (1.21.6).\n",
            "     * 55 for 770 (1.21.5), 63 for 771 (1.21.6), 64 for 772 (1.21.7／1.21.8),\n"
            "     * 69 for 773 (1.21.9／1.21.10), 75 for 774 (1.21.11).\n",
            name="builder-packformat-javadoc")
t = replace(t,
            "            case ProtocolVersion.V1_21_9 -> PACK_FORMAT_1_21_9;\n",
            "            case ProtocolVersion.V1_21_9 -> PACK_FORMAT_1_21_9;\n"
            "            case ProtocolVersion.V1_21_11 -> PACK_FORMAT_1_21_11;\n",
            name="builder-packformat-switch")
t = replace(t,
            "    public BuiltPack build773(Path outputDir) throws IOException {\n"
            "        return build(outputDir, PACK_FORMAT_1_21_9, PACK_FILE_NAME_773);\n"
            "    }\n",
            "    public BuiltPack build773(Path outputDir) throws IOException {\n"
            "        return build(outputDir, PACK_FORMAT_1_21_9, PACK_FILE_NAME_773);\n"
            "    }\n"
            "\n"
            "    /**\n"
            "     * Builds the 1.21.11 variant of the pack into\n"
            "     * {@code outputDir/detergentmc-pack-1.21.11.zip} and returns the file\n"
            "     * plus its SHA-1. Same demo assets as {@link #build(Path)}, only\n"
            "     * {@code pack.mcmeta} differs ({@code pack_format} 75).\n"
            "     */\n"
            "    public BuiltPack build774(Path outputDir) throws IOException {\n"
            "        return build(outputDir, PACK_FORMAT_1_21_11, PACK_FILE_NAME_774);\n"
            "    }\n",
            name="builder-build774")
t = replace(t,
            "    /** Writes the 1.21.9 variant of the pack zip to {@code file}. */\n"
            "    public void writePack773(Path file) throws IOException {\n"
            "        writePack(file, PACK_FORMAT_1_21_9);\n"
            "    }\n",
            "    /** Writes the 1.21.9 variant of the pack zip to {@code file}. */\n"
            "    public void writePack773(Path file) throws IOException {\n"
            "        writePack(file, PACK_FORMAT_1_21_9);\n"
            "    }\n"
            "\n"
            "    /** Writes the 1.21.11 variant of the pack zip to {@code file}. */\n"
            "    public void writePack774(Path file) throws IOException {\n"
            "        writePack(file, PACK_FORMAT_1_21_11);\n"
            "    }\n",
            name="builder-writepack774")
t = replace(t,
            "            case PACK_FORMAT_1_21_9 -> PACK_MCMETA_773;\n",
            "            case PACK_FORMAT_1_21_9 -> PACK_MCMETA_773;\n"
            "            case PACK_FORMAT_1_21_11 -> PACK_MCMETA_774;\n",
            name="builder-mcmeta-switch")
write(p, t)

# ---------------------------------------------------------------- DatapackGenerator
p = ROOT / "detergentmc-core/src/main/java/io/detergentmc/core/vanilla/DatapackGenerator.java"
t = p.read_text(encoding="utf-8")
t = replace(t,
            "    /** Minecraft 1.21.9 / 1.21.10 datapack format (Minecraft Wiki: 88 = 1.21.9 / 1.21.10). */\n"
            "    public static final int PACK_FORMAT_1_21_9 = 88;\n",
            "    /** Minecraft 1.21.9 / 1.21.10 datapack format (Minecraft Wiki: 88 = 1.21.9 / 1.21.10). */\n"
            "    public static final int PACK_FORMAT_1_21_9 = 88;\n"
            "\n"
            "    /** Minecraft 1.21.11 datapack format (Minecraft Wiki: 94 = 1.21.11). */\n"
            "    public static final int PACK_FORMAT_1_21_11 = 94;\n",
            name="datapack-const")
t = replace(t,
            "\"1.21.9\" / \"1.21.10\" -&gt; 88). */",
            "\"1.21.9\" / \"1.21.10\" -&gt; 88, \"1.21.11\" -&gt; 94). */",
            name="datapack-javadoc")
t = replace(t,
            "        if (\"1.21.9\".equals(minecraftVersion) || \"1.21.10\".equals(minecraftVersion)) {\n"
            "            return PACK_FORMAT_1_21_9;\n"
            "        }\n",
            "        if (\"1.21.9\".equals(minecraftVersion) || \"1.21.10\".equals(minecraftVersion)) {\n"
            "            return PACK_FORMAT_1_21_9;\n"
            "        }\n"
            "        if (\"1.21.11\".equals(minecraftVersion)) {\n"
            "            return PACK_FORMAT_1_21_11;\n"
            "        }\n",
            name="datapack-packformatfor")
write(p, t)
print("part 1 OK")

# ---------------------------------------------------------------- ResourcePackMode
p = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackMode.java"
t = p.read_text(encoding="utf-8")

t = replace(t,
            "     * @param pack773 the 1.21.9 pack to offer to 773 clients in\n"
            "     *                external mode, or {@code null} when none is\n"
            "     *                configured (773 clients then get {@code pack} as\n"
            "     *                before)\n",
            "     * @param pack773 the 1.21.9 pack to offer to 773 clients in\n"
            "     *                external mode, or {@code null} when none is\n"
            "     *                configured (773 clients then get {@code pack} as\n"
            "     *                before)\n"
            "     * @param pack774 the 1.21.11 pack to offer to 774 clients in\n"
            "     *                external mode, or {@code null} when none is\n"
            "     *                configured (774 clients then get {@code pack} as\n"
            "     *                before)\n",
            name="mode-decision-javadoc")
t = replace(t,
            "            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773) {\n",
            "            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772,\n"
            "            ResourcePack pack773, ResourcePack pack774) {\n",
            name="mode-decision-record")
# 「1.21.9パックなし」互換コンストラクタは正準コンストラクタ呼び出しに null を1つ追加（pack773・pack774とも無し）。
t = replace(t,
            "        /** Backwards-compatible constructor without a 1.21.9 pack. */\n"
            "        public Decision(Kind kind, ResourcePack pack, String problem,\n"
            "                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,\n"
            "                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
            "                ResourcePack pack770, ResourcePack pack771, ResourcePack pack772) {\n"
            "            this(kind, pack, problem, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, null);\n"
            "        }\n",
            "        /** Backwards-compatible constructor without a 1.21.9 pack. */\n"
            "        public Decision(Kind kind, ResourcePack pack, String problem,\n"
            "                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,\n"
            "                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
            "                ResourcePack pack770, ResourcePack pack771, ResourcePack pack772) {\n"
            "            this(kind, pack, problem, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, null, null);\n"
            "        }\n"
            "\n"
            "        /** Backwards-compatible constructor without a 1.21.11 pack. */\n"
            "        public Decision(Kind kind, ResourcePack pack, String problem,\n"
            "                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,\n"
            "                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
            "                ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773) {\n"
            "            this(kind, pack, problem, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null);\n"
            "        }\n",
            name="mode-decision-ctors")
t = replace(t,
            "         * 771→{@code pack771}（63）、772→{@code pack772}（64）、773→{@code pack773}（69）。\n",
            "         * 771→{@code pack771}（63）、772→{@code pack772}（64）、773→{@code pack773}（69）、\n"
            "         * 774→{@code pack774}（75）。\n",
            name="mode-packforprotocol-javadoc")
t = replace(t,
            "            if (protocol == ProtocolVersion.V1_21_9) {\n"
            "                return pack773 != null ? pack773 : pack;\n"
            "            }\n",
            "            if (protocol == ProtocolVersion.V1_21_9) {\n"
            "                return pack773 != null ? pack773 : pack;\n"
            "            }\n"
            "            if (protocol == ProtocolVersion.V1_21_11) {\n"
            "                return pack774 != null ? pack774 : pack;\n"
            "            }\n",
            name="mode-packforprotocol")
# 770／771／772の委譲はフルdecideを直接呼ぶため、nullの数を2つずつ増やす（774分）。
t = replace(t,
            "                configuredUrl770, configuredSha1770,\n"
            "                null, null, null, null, null, null, required, promptKey);",
            "                configuredUrl770, configuredSha1770,\n"
            "                null, null, null, null, null, null, null, null, required, promptKey);",
            name="mode-delegate-770")
t = replace(t,
            "                configuredUrl771, configuredSha1771,\n"
            "                null, null, null, null, required, promptKey);",
            "                configuredUrl771, configuredSha1771,\n"
            "                null, null, null, null, null, null, required, promptKey);",
            name="mode-delegate-771")
t = replace(t,
            "                configuredUrl772, configuredSha1772,\n"
            "                null, null, required, promptKey);",
            "                configuredUrl772, configuredSha1772,\n"
            "                null, null, null, null, required, promptKey);",
            name="mode-delegate-772")

# --- 24引数フルdecideを26引数化する。旧メソッドを抜き出して新メソッドを作り、
# --- 旧メソッドの位置に「24引数＝委譲版」＋「26引数＝フル版」を置く。
marker = ("    /**\n"
          "     * {@link #decide(String, String, String, String, String, String, String, String, String, String, String, String, String, String, boolean, String)}\n"
          "     * with the optional 1.20.1, 1.20.2, 1.20.5 / 1.20.6, 1.21 /\n"
          "     * 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6 and 1.21.7／1.21.8 and 1.21.9／1.21.10 packs")
start = t.find(marker)
if start < 0:
    raise SystemExit("full decide javadoc marker not found")
end = t.rfind("\n    }\n}")
if end < start:
    raise SystemExit("full decide end not found")
old_method = t[start:end + len("\n    }")]
# 新26引数フル版：旧メソッドに774を足す。
new_method = old_method
new_method = replace(new_method,
                     "     * 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6 and 1.21.7／1.21.8 and 1.21.9／1.21.10 packs",
                     "     * 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6 and 1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 packs",
                     name="mode-full-javadoc-1")
new_method = replace(new_method,
                     "     * {@code resource-pack.url-772} / {@code resource-pack.sha1-772} and\n"
                     "     * {@code resource-pack.url-773} / {@code resource-pack.sha1-773}).",
                     "     * {@code resource-pack.url-772} / {@code resource-pack.sha1-772},\n"
                     "     * {@code resource-pack.url-773} / {@code resource-pack.sha1-773} and\n"
                     "     * {@code resource-pack.url-774} / {@code resource-pack.sha1-774}).",
                     name="mode-full-javadoc-2")
new_method = replace(new_method,
                     "     * The 773 pack is one pack for 1.21.9／1.21.10 (protocol 773,\n"
                     "     * pack_format 69) and follows the same rules as the 763 / 764 / 766 /\n"
                     "     * 767 / 768 / 769 / 770 / 771 / 772 packs: empty means no pack for that version, {@code https://}\n"
                     "     * with a valid SHA-1 yields {@code pack773}, anything else drops\n"
                     "     * the 773 pack with a warning and leaves the primary pack unchanged.\n"
                     "     *\n"
                     "     * @param configuredUrl772  {@code resource-pack.url-772} (may be null/blank)\n"
                     "     * @param configuredSha1772 {@code resource-pack.sha1-772} (may be null/blank)\n"
                     "     * @param configuredUrl773  {@code resource-pack.url-773} (may be null/blank)\n"
                     "     * @param configuredSha1773 {@code resource-pack.sha1-773} (may be null/blank)\n",
                     "     * The 773 pack is one pack for 1.21.9／1.21.10 (protocol 773,\n"
                     "     * pack_format 69) and follows the same rules as the 763 / 764 / 766 /\n"
                     "     * 767 / 768 / 769 / 770 / 771 / 772 packs: empty means no pack for that version, {@code https://}\n"
                     "     * with a valid SHA-1 yields {@code pack773}, anything else drops\n"
                     "     * the 773 pack with a warning and leaves the primary pack unchanged.\n"
                     "     * The 774 pack is one pack for 1.21.11 (protocol 774,\n"
                     "     * pack_format 75) and follows the same rules as the 773 pack.\n"
                     "     *\n"
                     "     * @param configuredUrl772  {@code resource-pack.url-772} (may be null/blank)\n"
                     "     * @param configuredSha1772 {@code resource-pack.sha1-772} (may be null/blank)\n"
                     "     * @param configuredUrl773  {@code resource-pack.url-773} (may be null/blank)\n"
                     "     * @param configuredSha1773 {@code resource-pack.sha1-773} (may be null/blank)\n"
                     "     * @param configuredUrl774  {@code resource-pack.url-774} (may be null/blank)\n"
                     "     * @param configuredSha1774 {@code resource-pack.sha1-774} (may be null/blank)\n",
                     name="mode-full-javadoc-3")
new_method = replace(new_method,
                     "            String configuredUrl773, String configuredSha1773,\n"
                     "            boolean required, String promptKey) {",
                     "            String configuredUrl773, String configuredSha1773,\n"
                     "            String configuredUrl774, String configuredSha1774,\n"
                     "            boolean required, String promptKey) {",
                     name="mode-full-signature")
new_method = replace(new_method,
                     "            ResourcePack pack773 = null;\n",
                     "            ResourcePack pack773 = null;\n"
                     "            ResourcePack pack774 = null;\n",
                     name="mode-full-pack774-var")
new_method = replace(new_method,
                     "            String url773 = configuredUrl773 == null ? \"\" : configuredUrl773.trim();\n"
                     "            if (!url773.isEmpty()) {\n"
                     "                if (!url773.startsWith(\"https://\")) {\n"
                     "                    problems.add(\"resource-pack.url-773 must start with https:// (got '\"\n"
                     "                            + url773 + \"'); 773 clients will be offered the 1.20.4 pack instead\");\n"
                     "                } else if (!isValidSha1(configuredSha1773)) {\n"
                     "                    problems.add(\"resource-pack.sha1-773 is missing or not a 40-digit hex \"\n"
                     "                            + \"SHA-1: '\" + configuredSha1773\n"
                     "                            + \"'; 773 clients will be offered the 1.20.4 pack instead\");\n"
                     "                } else {\n"
                     "                    pack773 = new ResourcePack(url773,\n"
                     "                            configuredSha1773.trim().toLowerCase(Locale.ROOT),\n"
                     "                            required, prompt);\n"
                     "                }\n"
                     "            }\n",
                     "            String url773 = configuredUrl773 == null ? \"\" : configuredUrl773.trim();\n"
                     "            if (!url773.isEmpty()) {\n"
                     "                if (!url773.startsWith(\"https://\")) {\n"
                     "                    problems.add(\"resource-pack.url-773 must start with https:// (got '\"\n"
                     "                            + url773 + \"'); 773 clients will be offered the 1.20.4 pack instead\");\n"
                     "                } else if (!isValidSha1(configuredSha1773)) {\n"
                     "                    problems.add(\"resource-pack.sha1-773 is missing or not a 40-digit hex \"\n"
                     "                            + \"SHA-1: '\" + configuredSha1773\n"
                     "                            + \"'; 773 clients will be offered the 1.20.4 pack instead\");\n"
                     "                } else {\n"
                     "                    pack773 = new ResourcePack(url773,\n"
                     "                            configuredSha1773.trim().toLowerCase(Locale.ROOT),\n"
                     "                            required, prompt);\n"
                     "                }\n"
                     "            }\n"
                     "            String url774 = configuredUrl774 == null ? \"\" : configuredUrl774.trim();\n"
                     "            if (!url774.isEmpty()) {\n"
                     "                if (!url774.startsWith(\"https://\")) {\n"
                     "                    problems.add(\"resource-pack.url-774 must start with https:// (got '\"\n"
                     "                            + url774 + \"'); 774 clients will be offered the 1.20.4 pack instead\");\n"
                     "                } else if (!isValidSha1(configuredSha1774)) {\n"
                     "                    problems.add(\"resource-pack.sha1-774 is missing or not a 40-digit hex \"\n"
                     "                            + \"SHA-1: '\" + configuredSha1774\n"
                     "                            + \"'; 774 clients will be offered the 1.20.4 pack instead\");\n"
                     "                } else {\n"
                     "                    pack774 = new ResourcePack(url774,\n"
                     "                            configuredSha1774.trim().toLowerCase(Locale.ROOT),\n"
                     "                            required, prompt);\n"
                     "                }\n"
                     "            }\n",
                     name="mode-full-url774-block")
new_method = replace(new_method,
                     "                    pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773);",
                     "                    pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774);",
                     name="mode-full-decision-call")
# 24引数版は26引数版への委譲にする。
delegate_method = """    /**
     * {@link #decide(String, String, String, String, String, String, String, String, String, String, String, String, String, String, boolean, String)}
     * with the optional 1.20.1, 1.20.2, 1.20.5 / 1.20.6, 1.21 /
     * 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6 and 1.21.7／1.21.8 and 1.21.9／1.21.10 packs
     * ({@code resource-pack.url-763} / {@code resource-pack.sha1-763},
     * {@code resource-pack.url-764} / {@code resource-pack.sha1-764},
     * {@code resource-pack.url-766} / {@code resource-pack.sha1-766},
     * {@code resource-pack.url-767} / {@code resource-pack.sha1-767},
     * {@code resource-pack.url-768} / {@code resource-pack.sha1-768},
     * {@code resource-pack.url-769} / {@code resource-pack.sha1-769},
     * {@code resource-pack.url-770} / {@code resource-pack.sha1-770},
     * {@code resource-pack.url-771} / {@code resource-pack.sha1-771},
     * {@code resource-pack.url-772} / {@code resource-pack.sha1-772} and
     * {@code resource-pack.url-773} / {@code resource-pack.sha1-773}).
     * Without a 1.21.11 pack: no 774 pack is configured; delegates to
     * the 26-argument decide with empty url-774 / sha1-774.
     *
     * @param configuredUrl773  {@code resource-pack.url-773} (may be null/blank)
     * @param configuredSha1773 {@code resource-pack.sha1-773} (may be null/blank)
     */
    public static Decision decide(String configuredUrl, String configuredSha1,
            String configuredUrl763, String configuredSha1763,
            String configuredUrl764, String configuredSha1764,
            String configuredUrl766, String configuredSha1766,
            String configuredUrl767, String configuredSha1767,
            String configuredUrl768, String configuredSha1768,
            String configuredUrl769, String configuredSha1769,
            String configuredUrl770, String configuredSha1770,
            String configuredUrl771, String configuredSha1771,
            String configuredUrl772, String configuredSha1772,
            String configuredUrl773, String configuredSha1773,
            boolean required, String promptKey) {
        return decide(configuredUrl, configuredSha1,
                configuredUrl763, configuredSha1763,
                configuredUrl764, configuredSha1764,
                configuredUrl766, configuredSha1766,
                configuredUrl767, configuredSha1767,
                configuredUrl768, configuredSha1768,
                configuredUrl769, configuredSha1769,
                configuredUrl770, configuredSha1770,
                configuredUrl771, configuredSha1771,
                configuredUrl772, configuredSha1772,
                configuredUrl773, configuredSha1773,
                null, null, required, promptKey);
    }

"""
t = t[:start] + delegate_method + new_method + t[end + len("\n    }"):]
write(p, t)
print("part 2 OK")

# ---------------------------------------------------------------- ResourcePackServer
p = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"
t = p.read_text(encoding="utf-8")
t = replace(t,
            " * {@link #pack773()} returns the {@link ResourcePack} the proxy offers\n"
            " * to 773 clients (pack_format 69). All eleven packs share the same port.\n"
            " */",
            " * {@link #pack773()} returns the {@link ResourcePack} the proxy offers\n"
            " * to 773 clients (pack_format 69). All eleven packs share the same port.\n"
            " *\n"
            " * <h2>1.21.11 variant</h2>\n"
            " *\n"
            " * <p>When a twelfth pack is given (see\n"
            " * {@link #ResourcePackServer(ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}),\n"
            " * the server also serves it at {@link #PACK_PATH_774} and\n"
            " * {@link #pack774()} returns the {@link ResourcePack} the proxy offers\n"
            " * to 774 clients (pack_format 75). All twelve packs share the same port.\n"
            " */",
            name="server-class-javadoc")
t = replace(t,
            "    /** Path of the 1.21.9 (pack_format 69) variant, when one is served. */\n"
            "    public static final String PACK_PATH_773 = \"/detergentmc-pack-1.21.9.zip\";\n",
            "    /** Path of the 1.21.9 (pack_format 69) variant, when one is served. */\n"
            "    public static final String PACK_PATH_773 = \"/detergentmc-pack-1.21.9.zip\";\n"
            "\n"
            "    /** Path of the 1.21.11 (pack_format 75) variant, when one is served. */\n"
            "    public static final String PACK_PATH_774 = \"/detergentmc-pack-1.21.11.zip\";\n",
            name="server-pack-path-774")
t = replace(t,
            "    private final Path pack773File;\n"
            "    private final String sha1773;\n",
            "    private final Path pack773File;\n"
            "    private final String sha1773;\n"
            "    private final Path pack774File;\n"
            "    private final String sha1774;\n",
            name="server-fields-774")
t = replace(t,
            "    private byte[] pack773Bytes;\n",
            "    private byte[] pack773Bytes;\n"
            "    private byte[] pack774Bytes;\n",
            name="server-bytes-774")
# 「1.21.9なし」Pathコンストラクタは773・774の両方に null を渡す。
t = replace(t,
            "                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,\n"
            "                pack772File, sha1772, null, null,\n"
            "                port, required, promptKey);",
            "                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,\n"
            "                pack772File, sha1772, null, null, null, null,\n"
            "                port, required, promptKey);",
            name="server-path-ctor-without-773")
# 「1.21.11なし」Pathコンストラクタを新設（773までを受け取り774に null を渡す）。
t = replace(t,
            "    /**\n"
            "     * @param pack769File 1.21.4 variant zip file, or {@code null}\n"
            "     *                    to serve no 1.21.4 variant\n",
            "    /**\n"
            "     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}\n"
            "     * without a 1.21.11 variant: no 774 pack is served.\n"
            "     */\n"
            "    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,\n"
            "            Path pack764File, String sha1764, Path pack766File, String sha1766,\n"
            "            Path pack767File, String sha1767, Path pack768File, String sha1768,\n"
            "            Path pack769File, String sha1769, Path pack770File, String sha1770,\n"
            "            Path pack771File, String sha1771, Path pack772File, String sha1772,\n"
            "            Path pack773File, String sha1773,\n"
            "            int port, boolean required, String promptKey) {\n"
            "        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,\n"
            "                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,\n"
            "                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,\n"
            "                pack772File, sha1772, pack773File, sha1773, null, null,\n"
            "                port, required, promptKey);\n"
            "    }\n"
            "\n"
            "    /**\n"
            "     * @param pack769File 1.21.4 variant zip file, or {@code null}\n"
            "     *                    to serve no 1.21.4 variant\n",
            name="server-path-ctor-without-774")
# フルPathコンストラクタ：パラメータ・javadoc・検証・代入。
t = replace(t,
            "     * @param pack773File 1.21.9 variant zip file, or {@code null}\n"
            "     *                    to serve no 1.21.9 variant\n"
            "     * @param sha1773     lower-case hex SHA-1 of {@code pack773File}\n"
            "     *                    (required when {@code pack773File} is given)\n"
            "     */\n"
            "    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,\n"
            "            Path pack764File, String sha1764, Path pack766File, String sha1766,\n"
            "            Path pack767File, String sha1767, Path pack768File, String sha1768,\n"
            "            Path pack769File, String sha1769, Path pack770File, String sha1770,\n"
            "            Path pack771File, String sha1771, Path pack772File, String sha1772,\n"
            "            Path pack773File, String sha1773,\n"
            "            int port, boolean required, String promptKey) {",
            "     * @param pack773File 1.21.9 variant zip file, or {@code null}\n"
            "     *                    to serve no 1.21.9 variant\n"
            "     * @param sha1773     lower-case hex SHA-1 of {@code pack773File}\n"
            "     *                    (required when {@code pack773File} is given)\n"
            "     * @param pack774File 1.21.11 variant zip file, or {@code null}\n"
            "     *                    to serve no 1.21.11 variant\n"
            "     * @param sha1774     lower-case hex SHA-1 of {@code pack774File}\n"
            "     *                    (required when {@code pack774File} is given)\n"
            "     */\n"
            "    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,\n"
            "            Path pack764File, String sha1764, Path pack766File, String sha1766,\n"
            "            Path pack767File, String sha1767, Path pack768File, String sha1768,\n"
            "            Path pack769File, String sha1769, Path pack770File, String sha1770,\n"
            "            Path pack771File, String sha1771, Path pack772File, String sha1772,\n"
            "            Path pack773File, String sha1773, Path pack774File, String sha1774,\n"
            "            int port, boolean required, String promptKey) {",
            name="server-full-path-ctor-sig")
t = replace(t,
            "        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {\n"
            "            throw new IllegalArgumentException(\"sha1 for the 1.21.9 pack is required\");\n"
            "        }\n",
            "        if (pack773File != null && (sha1773 == null || sha1773.isBlank())) {\n"
            "            throw new IllegalArgumentException(\"sha1 for the 1.21.9 pack is required\");\n"
            "        }\n"
            "        if (pack774File != null && (sha1774 == null || sha1774.isBlank())) {\n"
            "            throw new IllegalArgumentException(\"sha1 for the 1.21.11 pack is required\");\n"
            "        }\n",
            name="server-full-path-ctor-validation")
t = replace(t,
            "        this.pack773File = pack773File;\n"
            "        this.sha1773 = sha1773;\n",
            "        this.pack773File = pack773File;\n"
            "        this.sha1773 = sha1773;\n"
            "        this.pack774File = pack774File;\n"
            "        this.sha1774 = sha1774;\n",
            name="server-full-path-ctor-assign")
# 11パックBuiltPackコンストラクタ（promptKey付き）はフルPath呼び出しに774分の null を渡す。
t = replace(t,
            "                built771.file(), built771.sha1(), built772.file(), built772.sha1(),\n"
            "                built773.file(), built773.sha1(),\n"
            "                port, required, promptKey);",
            "                built771.file(), built771.sha1(), built772.file(), built772.sha1(),\n"
            "                built773.file(), built773.sha1(), null, null,\n"
            "                port, required, promptKey);",
            name="server-builtpack-11")
# 12パックBuiltPackコンストラクタを新設（promptKeyあり／なし）。
t = replace(t,
            "    /**\n"
            "     * Starts the HTTP server and returns the {@link ResourcePack} clients\n",
            "    /**\n"
            "     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,\n"
            "     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,\n"
            "     * 1.21.7／1.21.8, 1.21.9／1.21.10 and 1.21.11 variants, built by {@link ResourcePackBuilder}.\n"
            "     */\n"
            "    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
            "            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,\n"
            "            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,\n"
            "            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,\n"
            "            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,\n"
            "            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,\n"
            "            ResourcePackBuilder.BuiltPack built774,\n"
            "            int port, boolean required) {\n"
            "        this(built, built763, built764, built766, built767, built768, built769, built770,\n"
            "                built771, built772, built773, built774, port, required, DEFAULT_PROMPT_KEY);\n"
            "    }\n"
            "\n"
            "    /** Convenience: serve all twelve packs, with an explicit prompt key. */\n"
            "    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
            "            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,\n"
            "            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,\n"
            "            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,\n"
            "            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,\n"
            "            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,\n"
            "            ResourcePackBuilder.BuiltPack built774,\n"
            "            int port, boolean required, String promptKey) {\n"
            "        this(built.file(), built.sha1(), built763.file(), built763.sha1(),\n"
            "                built764.file(), built764.sha1(), built766.file(), built766.sha1(),\n"
            "                built767.file(), built767.sha1(), built768.file(), built768.sha1(),\n"
            "                built769.file(), built769.sha1(), built770.file(), built770.sha1(),\n"
            "                built771.file(), built771.sha1(), built772.file(), built772.sha1(),\n"
            "                built773.file(), built773.sha1(), built774.file(), built774.sha1(),\n"
            "                port, required, promptKey);\n"
            "    }\n"
            "\n"
            "\n"
            "    /**\n"
            "     * Starts the HTTP server and returns the {@link ResourcePack} clients\n",
            name="server-builtpack-12")
t = replace(t,
            "        if (pack773File != null) {\n"
            "            pack773Bytes = readAndVerify(pack773File, sha1773);\n"
            "        }\n",
            "        if (pack773File != null) {\n"
            "            pack773Bytes = readAndVerify(pack773File, sha1773);\n"
            "        }\n"
            "        if (pack774File != null) {\n"
            "            pack774Bytes = readAndVerify(pack774File, sha1774);\n"
            "        }\n",
            name="server-start-load-774")
t = replace(t,
            "        if (pack773Bytes != null) {\n"
            "            httpServer.createContext(PACK_PATH_773, exchange -> serveBytes(exchange, pack773Bytes));\n"
            "        }\n",
            "        if (pack773Bytes != null) {\n"
            "            httpServer.createContext(PACK_PATH_773, exchange -> serveBytes(exchange, pack773Bytes));\n"
            "        }\n"
            "        if (pack774Bytes != null) {\n"
            "            httpServer.createContext(PACK_PATH_774, exchange -> serveBytes(exchange, pack774Bytes));\n"
            "        }\n",
            name="server-start-context-774")
t = replace(t,
            "        pack773Bytes = null;\n",
            "        pack773Bytes = null;\n"
            "        pack774Bytes = null;\n",
            name="server-stop-774")
t = replace(t,
            "    /**\n"
            "     * The 1.21.9 {@link ResourcePack} to offer to 773 clients,\n"
            "     * or {@code null} when no 1.21.9 variant is served.\n"
            "     */\n"
            "    public synchronized ResourcePack pack773() {\n"
            "        return pack773File == null ? null\n"
            "                : new ResourcePack(url773(), sha1773, required, promptKey);\n"
            "    }\n",
            "    /**\n"
            "     * The 1.21.9 {@link ResourcePack} to offer to 773 clients,\n"
            "     * or {@code null} when no 1.21.9 variant is served.\n"
            "     */\n"
            "    public synchronized ResourcePack pack773() {\n"
            "        return pack773File == null ? null\n"
            "                : new ResourcePack(url773(), sha1773, required, promptKey);\n"
            "    }\n"
            "\n"
            "    /** The download URL of the 1.21.11 variant, or {@code null} when not served. */\n"
            "    public synchronized String url774() {\n"
            "        if (pack774File == null) {\n"
            "            return null;\n"
            "        }\n"
            "        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;\n"
            "        return \"http://127.0.0.1:\" + port + PACK_PATH_774;\n"
            "    }\n"
            "\n"
            "    /**\n"
            "     * The 1.21.11 {@link ResourcePack} to offer to 774 clients,\n"
            "     * or {@code null} when no 1.21.11 variant is served.\n"
            "     */\n"
            "    public synchronized ResourcePack pack774() {\n"
            "        return pack774File == null ? null\n"
            "                : new ResourcePack(url774(), sha1774, required, promptKey);\n"
            "    }\n",
            name="server-url774-pack774")
t = replace(t,
            "     * 772→{@link #pack772()}（pack_format 64）、\n"
            "     * 773→{@link #pack773()}（pack_format 69）、\n",
            "     * 772→{@link #pack772()}（pack_format 64）、\n"
            "     * 773→{@link #pack773()}（pack_format 69）、\n"
            "     * 774→{@link #pack774()}（pack_format 75）、\n",
            name="server-packforprotocol-javadoc")
t = replace(t,
            "        if (clientProtocol == ProtocolVersion.V1_21_9) {\n"
            "            return pack773();\n"
            "        }\n"
            "        return mainPack;",
            "        if (clientProtocol == ProtocolVersion.V1_21_9) {\n"
            "            return pack773();\n"
            "        }\n"
            "        if (clientProtocol == ProtocolVersion.V1_21_11) {\n"
            "            return pack774();\n"
            "        }\n"
            "        return mainPack;",
            name="server-packforprotocol")
write(p, t)
print("part 3 OK")

# ---------------------------------------------------------------- DetergentServer
p = ROOT / "detergentmc-core/src/main/java/io/detergentmc/core/DetergentServer.java"
t = p.read_text(encoding="utf-8")
t = replace(t,
            "    /** 773クライアントに送るリソースパック（pack_format 69）。無ければ null。 */\n"
            "    private ResourcePack resourcePack773;\n",
            "    /** 773クライアントに送るリソースパック（pack_format 69）。無ければ null。 */\n"
            "    private ResourcePack resourcePack773;\n"
            "    /** 774クライアントに送るリソースパック（pack_format 75）。無ければ null。 */\n"
            "    private ResourcePack resourcePack774;\n",
            name="core-field-774")
t = replace(t,
            "     * 1.21.6, the 772 pack (pack_format 64) when it is\n"
            "     * 1.21.7／1.21.8, the 773 pack (pack_format 69) when it is\n"
            "     * 1.21.9／1.21.10, the 765 pack (pack_format 22) otherwise.\n",
            "     * 1.21.6, the 772 pack (pack_format 64) when it is\n"
            "     * 1.21.7／1.21.8, the 773 pack (pack_format 69) when it is\n"
            "     * 1.21.9／1.21.10, the 774 pack (pack_format 75) when it is\n"
            "     * 1.21.11, the 765 pack (pack_format 22) otherwise.\n",
            name="core-hostresourcepack-javadoc")
t = replace(t,
            "        if (serverProtocol == ProtocolVersion.V1_21_9 && resourcePack773 != null) {\n"
            "            return resourcePack773;\n"
            "        }\n",
            "        if (serverProtocol == ProtocolVersion.V1_21_9 && resourcePack773 != null) {\n"
            "            return resourcePack773;\n"
            "        }\n"
            "        if (serverProtocol == ProtocolVersion.V1_21_11 && resourcePack774 != null) {\n"
            "            return resourcePack774;\n"
            "        }\n",
            name="core-hostresourcepack")
t = replace(t,
            "        ResourcePack packFor773Clients =\n"
            "                serverProtocol == ProtocolVersion.V1_21_9 ? null : resourcePack773;\n",
            "        ResourcePack packFor773Clients =\n"
            "                serverProtocol == ProtocolVersion.V1_21_9 ? null : resourcePack773;\n"
            "        ResourcePack packFor774Clients =\n"
            "                serverProtocol == ProtocolVersion.V1_21_11 ? null : resourcePack774;\n",
            name="core-createproxy-pack774")
t = replace(t,
            "                packFor768Clients, packFor769Clients, packFor770Clients, packFor771Clients,\n"
            "                packFor772Clients, packFor773Clients, serverProtocol);",
            "                packFor768Clients, packFor769Clients, packFor770Clients, packFor771Clients,\n"
            "                packFor772Clients, packFor773Clients, packFor774Clients, serverProtocol);",
            name="core-createproxy-call")
t = replace(t,
            "     * {@code resource-pack.url-772} / {@code resource-pack.sha1-772} and\n"
            "     * {@code resource-pack.url-773} / {@code resource-pack.sha1-773}\n",
            "     * {@code resource-pack.url-772} / {@code resource-pack.sha1-772},\n"
            "     * {@code resource-pack.url-773} / {@code resource-pack.sha1-773} and\n"
            "     * {@code resource-pack.url-774} / {@code resource-pack.sha1-774}\n",
            name="core-startresourcepack-javadoc")
t = replace(t,
            "                        config.getString(\"resource-pack.url-773\", \"\"),\n"
            "                        config.getString(\"resource-pack.sha1-773\", \"\"),\n"
            "                        required, promptKey);",
            "                        config.getString(\"resource-pack.url-773\", \"\"),\n"
            "                        config.getString(\"resource-pack.sha1-773\", \"\"),\n"
            "                        config.getString(\"resource-pack.url-774\", \"\"),\n"
            "                        config.getString(\"resource-pack.sha1-774\", \"\"),\n"
            "                        required, promptKey);",
            name="core-decide-call")
t = replace(t,
            "                resourcePack773 = decision.pack773();\n"
            "                if (resourcePack773 != null) {\n"
            "                    LOG.info(() -> translator.translate(\n"
            "                            \"resource-pack.external-enabled\", resourcePack773.url()));\n"
            "                }\n",
            "                resourcePack773 = decision.pack773();\n"
            "                if (resourcePack773 != null) {\n"
            "                    LOG.info(() -> translator.translate(\n"
            "                            \"resource-pack.external-enabled\", resourcePack773.url()));\n"
            "                }\n"
            "                resourcePack774 = decision.pack774();\n"
            "                if (resourcePack774 != null) {\n"
            "                    LOG.info(() -> translator.translate(\n"
            "                            \"resource-pack.external-enabled\", resourcePack774.url()));\n"
            "                }\n",
            name="core-external-pack774")
t = replace(t,
            "            // 1.21.7／1.21.8用（pack_format 64）、1.21.9／1.21.10用\n"
            "            // （pack_format 69）の11つを生成し、同じHTTPサーバー\n"
            "            // （同じ25567番ポート）で配信する。\n",
            "            // 1.21.7／1.21.8用（pack_format 64）、1.21.9／1.21.10用\n"
            "            // （pack_format 69）、1.21.11用（pack_format 75）の12つを生成し、\n"
            "            // 同じHTTPサーバー（同じ25567番ポート）で配信する。\n",
            name="core-loopback-comment-1")
t = replace(t,
            "            // 771クライアントには9番目、772クライアントには10番目、\n"
            "            // 773クライアントには11番目をプロキシが出し分ける。\n",
            "            // 771クライアントには9番目、772クライアントには10番目、\n"
            "            // 773クライアントには11番目、774クライアントには12番目を\n"
            "            // プロキシが出し分ける。\n",
            name="core-loopback-comment-2")
t = replace(t,
            "            ResourcePackBuilder.BuiltPack built773 = new ResourcePackBuilder()\n"
            "                    .build773(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n",
            "            ResourcePackBuilder.BuiltPack built773 = new ResourcePackBuilder()\n"
            "                    .build773(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
            "            ResourcePackBuilder.BuiltPack built774 = new ResourcePackBuilder()\n"
            "                    .build774(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n",
            name="core-loopback-build774")
t = replace(t,
            "            resourcePackServer = new ResourcePackServer(\n"
            "                    built, built763, built764, built766, built767, built768, built769, built770,\n"
            "                    built771, built772, built773, port, required,",
            "            resourcePackServer = new ResourcePackServer(\n"
            "                    built, built763, built764, built766, built767, built768, built769, built770,\n"
            "                    built771, built772, built773, built774, port, required,",
            name="core-loopback-server-ctor")
t = replace(t,
            "            resourcePack773 = resourcePackServer.pack773();\n",
            "            resourcePack773 = resourcePackServer.pack773();\n"
            "            resourcePack774 = resourcePackServer.pack774();\n",
            name="core-loopback-pack774")
t = replace(t,
            "            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4, 71 for 1.21.5, 80 for 1.21.6, 81 for 1.21.7／1.21.8, 88 for 1.21.9／1.21.10.\n",
            "            // 57 for 1.21.2 / 1.21.3, 61 for 1.21.4, 71 for 1.21.5, 80 for 1.21.6, 81 for 1.21.7／1.21.8, 88 for 1.21.9／1.21.10, 94 for 1.21.11.\n",
            name="core-datapack-comment")
write(p, t)
print("part 4 OK")

# ---------------------------------------------------------------- ClientProxy
p = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ClientProxy.java"
t = p.read_text(encoding="utf-8")
t = replace(t,
            "    private final ResourcePack pack773;\n"
            "    /**\n"
            "     * 内部バニラのプロトコル（763＝1.20／1.20.1、766＝1.20.5／1.20.6、",
            "    private final ResourcePack pack773;\n"
            "    /**\n"
            "     * 774クライアントに送るリソースパック（pack_format 75）。内部が774で\n"
            "     * ない場合にだけ使う。内部が774の場合はバニラが送ってきたパックをその\n"
            "     * まま出すため null のままでよい。無ければ null。セッションへは\n"
            "     * {@link ProxySession#setPack774(ResourcePack)} で渡す\n"
            "     * （{@code resolveHandshake} には774用パックの引数がまだ無いため）。\n"
            "     */\n"
            "    private final ResourcePack pack774;\n"
            "    /**\n"
            "     * 内部バニラのプロトコル（763＝1.20／1.20.1、766＝1.20.5／1.20.6、",
            name="proxy-field-774")
t = replace(t,
            "     * 769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10）。呼び出し元は",
            "     * 769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11）。呼び出し元は",
            name="proxy-internalprotocol-field-javadoc")
t = replace(t,
            "    /**\n"
            "     * 773クライアントに送るリソースパック（無ければ null）。内部が773で\n"
            "     * ない場合に使う差し替え用で、内部が773の場合は null のまま\n"
            "     * （バニラのパックをそのまま出す）。\n"
            "     */\n"
            "    public ResourcePack pack773() {\n"
            "        return pack773;\n"
            "    }\n",
            "    /**\n"
            "     * 773クライアントに送るリソースパック（無ければ null）。内部が773で\n"
            "     * ない場合に使う差し替え用で、内部が773の場合は null のまま\n"
            "     * （バニラのパックをそのまま出す）。\n"
            "     */\n"
            "    public ResourcePack pack773() {\n"
            "        return pack773;\n"
            "    }\n"
            "\n"
            "    /**\n"
            "     * 774クライアントに送るリソースパック（無ければ null）。内部が774で\n"
            "     * ない場合に使う差し替え用で、内部が774の場合は null のまま\n"
            "     * （バニラのパックをそのまま出す）。\n"
            "     */\n"
            "    public ResourcePack pack774() {\n"
            "        return pack774;\n"
            "    }\n",
            name="proxy-pack774-accessor")
t = replace(t,
            "    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5、767＝1.21、768＝1.21.2、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10）。 */\n",
            "    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5、767＝1.21、768＝1.21.2、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11）。 */\n",
            name="proxy-internalprotocol-accessor-javadoc")
t = replace(t,
            "                // 767／768／769／770／771／772／773用パックは resolveHandshake の引数には無いため、\n"
            "                // セッションへ直接渡す（ProxySession#setPack767／setPack768／setPack769／setPack770／setPack772／setPack773）。\n",
            "                // 767／768／769／770／771／772／773／774用パックは resolveHandshake の引数には無いため、\n"
            "                // セッションへ直接渡す（ProxySession#setPack767／setPack768／setPack769／setPack770／setPack772／setPack773／setPack774）。\n",
            name="proxy-resolve-comment-1")
t = replace(t,
            "                // 773クライアントには pack773（pack_format 69）を出し、\n"
            "                // 未設定の場合は ProxySession が pack772 に落とす。\n",
            "                // 773クライアントには pack773（pack_format 69）を出し、\n"
            "                // 未設定の場合は ProxySession が pack772 に落とす。\n"
            "                // 774クライアントには pack774（pack_format 75）を出し、\n"
            "                // 未設定の場合は ProxySession が pack773 に落とす。\n",
            name="proxy-resolve-comment-2")
t = replace(t,
            "                session.setPack773(proxy.pack773);\n",
            "                session.setPack773(proxy.pack773);\n"
            "                session.setPack774(proxy.pack774);\n",
            name="proxy-resolve-setpack774")

# --- 17引数コンストラクタを委譲化し、18引数（pack774付き）を新設する。
marker17 = ("    /**\n"
            "     * 逆向き対応版：内部プロトコルと765／766／767／768／769／770／771／772／773クライアント用のパックを指定する。")
start = t.find(marker17)
if start < 0:
    raise SystemExit("17-arg ctor javadoc marker not found")
end_marker = "    /** 内部バニラのホストが loopback か（信頼済み接続の条件）。 */"
end = t.find(end_marker, start)
if end < 0:
    raise SystemExit("isLoopbackHost marker not found")
old_ctor = t[start:end].rstrip() + "\n"
# 新18引数版：旧コンストラクタに pack774 を足す。
new_ctor = old_ctor
new_ctor = replace(new_ctor,
                   "     * 逆向き対応版：内部プロトコルと765／766／767／768／769／770／771／772／773クライアント用のパックを指定する。",
                   "     * 逆向き対応版：内部プロトコルと765／766／767／768／769／770／771／772／773／774クライアント用のパックを指定する。",
                   name="proxy-ctor18-javadoc-title")
new_ctor = replace(new_ctor,
                   "     * @param pack773         773クライアントに送るリソースパック\n"
                   "     *                        （pack_format 69）。内部が773でない場合に使う。\n"
                   "     *                        {@code null} の場合は773クライアントにも\n"
                   "     *                        772用パックを出す（ProxySession の既定の扱い）\n",
                   "     * @param pack773         773クライアントに送るリソースパック\n"
                   "     *                        （pack_format 69）。内部が773でない場合に使う。\n"
                   "     *                        {@code null} の場合は773クライアントにも\n"
                   "     *                        772用パックを出す（ProxySession の既定の扱い）\n"
                   "     * @param pack774         774クライアントに送るリソースパック\n"
                   "     *                        （pack_format 75）。内部が774でない場合に使う。\n"
                   "     *                        {@code null} の場合は774クライアントにも\n"
                   "     *                        773用パックを出す（ProxySession の既定の扱い）\n",
                   name="proxy-ctor18-javadoc-pack774")
new_ctor = replace(new_ctor,
                   "     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10）。呼び出し元は\n"
                   "     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}\n"
                   "     *                        で求めた値を渡すこと\n"
                   "     */\n"
                   "    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,\n"
                   "            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,\n"
                   "            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,\n"
                   "            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
                   "            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, int internalProtocol) {",
                   "     *                        764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7／1.21.8、773＝1.21.9／1.21.10、774＝1.21.11）。呼び出し元は\n"
                   "     *                        {@code ProtocolVersion.serverProtocolFor(minecraftVersion)}\n"
                   "     *                        で求めた値を渡すこと\n"
                   "     */\n"
                   "    public ClientProxy(int publicPort, String vanillaHost, int vanillaPort,\n"
                   "            boolean onlineMode, ResourcePack pack763, ResourcePack pack764,\n"
                   "            ProxyAuthConfig authConfig, ResourcePack pack765, ResourcePack pack766,\n"
                   "            ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,\n"
                   "            ResourcePack pack770, ResourcePack pack771, ResourcePack pack772, ResourcePack pack773, ResourcePack pack774, int internalProtocol) {",
                   name="proxy-ctor18-signature")
new_ctor = replace(new_ctor,
                   "                && internalProtocol != ProtocolVersion.V1_21_7\n"
                   "                && internalProtocol != ProtocolVersion.V1_21_9) {",
                   "                && internalProtocol != ProtocolVersion.V1_21_7\n"
                   "                && internalProtocol != ProtocolVersion.V1_21_9\n"
                   "                && internalProtocol != ProtocolVersion.V1_21_11) {",
                   name="proxy-ctor18-validation")
new_ctor = replace(new_ctor,
                   "                            + \"769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8) or 773 (1.21.9／1.21.10): \"\n",
                   "                            + \"769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7／1.21.8), 773 (1.21.9／1.21.10) or 774 (1.21.11): \"\n",
                   name="proxy-ctor18-message")
new_ctor = replace(new_ctor,
                   "        this.pack773 = pack773;\n",
                   "        this.pack773 = pack773;\n"
                   "        this.pack774 = pack774;\n",
                   name="proxy-ctor18-assign")
# 17引数版の本体を委譲に置き換える（javadocとシグネチャはそのまま）。
sig_end = old_ctor.find(") {", old_ctor.find("public ClientProxy"))
delegate_body = old_ctor[:sig_end + len(") {")] + """
        this(publicPort, vanillaHost, vanillaPort, onlineMode, pack763, pack764,
                authConfig, pack765, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, null,
                internalProtocol);
    }
"""
t = t[:start] + delegate_body + "\n" + new_ctor + "\n" + t[end:]
write(p, t)
print("part 5 OK")
print("ALL PARTS OK")
