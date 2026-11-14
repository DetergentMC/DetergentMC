#!/usr/bin/env python3
"""patch-resourcepack*-772.py / patch-datapackgenerator-772.py から
773版を生成する。

方針：old773 = new772（772の new が773の old）。new773 は
- new772 が old772 の純粋な append（new772.startswith(old772)）なら、
  追加分（suffix）を shift() で写して old773 に足す。suffix 内の数値は
  明示ルールで直す（pack_format 64→69、データパック 81→88 等）。
- それ以外（委譲チェーン・コンストラクタ等）は edit ごとに手書きする。
"""
import ast, importlib.util
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("gen773ps", TOOLS / "gen-773-proxysession.py")
g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
shift = g.shift


def load_pairs(fn):
    src = open(TOOLS / fn, encoding="utf-8").read()
    tree = ast.parse(src)
    out = []
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 \
                and isinstance(node.targets[0], ast.Name) and node.targets[0].id == "EDITS":
            out.append(("PATH", ast.literal_eval(node.value)))
        if isinstance(node, ast.Expr) and isinstance(node.value, ast.Call) \
                and getattr(node.value.func, "id", None) == "edit":
            args = node.value.args
            path_var = args[0].id if isinstance(args[0], ast.Name) else "PATH"
            out.append((path_var, ast.literal_eval(args[1])))
    return out


def fix_suffix(s):
    """追加分（773ブロック）の数値を直す。
    shift() は "PACK_FORMAT_1_21_7"（アンダースコア形）を写さないため、
    定数名もここで直す。"""
    s = s.replace("PACK_FORMAT_1_21_7 = 64", "PACK_FORMAT_1_21_9 = 69")
    s = s.replace("PACK_FORMAT_1_21_7 = 81", "PACK_FORMAT_1_21_9 = 88")
    s = s.replace("PACK_FORMAT_1_21_7", "PACK_FORMAT_1_21_9")
    # shift() は "sha1772"（"1772" の中の 772）も写さない
    s = s.replace("sha1772", "sha1773")
    # "configuredSha1772" のように大文字 S の場合もある（ResourcePackMode の
    # decide 本体の url773 ブロック。2026-11-12 に ResourcePackMode773Test の
    # 4件の失敗から発見）。
    s = s.replace("Sha1772", "Sha1773")
    s = s.replace("PACK_FORMAT_1_21_9 = 64", "PACK_FORMAT_1_21_9 = 69")
    s = s.replace("PACK_FORMAT_1_21_9 = 81", "PACK_FORMAT_1_21_9 = 88")
    s = s.replace('pack_format\\":64', 'pack_format\\":69')
    s = s.replace("(protocol 773, {@code pack_format} 64)", "(protocol 773, {@code pack_format} 69)")
    s = s.replace("The 1.21.9 resource-pack format is 64, not 63",
                  "The 1.21.9 resource-pack format is 69, not 64")
    s = s.replace("lists 64 for 1.21.9", "lists 69 for 1.21.9")
    # 改行で分断されている場合（「is\n * 64, not 63」「lists 64 for\n * 1.21.9」）
    s = s.replace("64, not 63", "69, not 64")
    s = s.replace("lists 64 for", "lists 69 for")
    # 11番目のパック（shift は "tenth"→"eleventh" を写さない）
    s = s.replace("When a tenth pack is given", "When an eleventh pack is given")
    s = s.replace("All ten packs share the same port.", "All eleven packs share the same port.")
    s = s.replace(
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, int, boolean)}",
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, ResourcePackBuilder.BuiltPack, "
        "ResourcePackBuilder.BuiltPack, int, boolean)}")
    s = s.replace("format-63 pack", "format-64 pack")
    s = s.replace("for 1.21.9／1.21.10 is 81", "for 1.21.9／1.21.10 is 88")
    s = s.replace("81 = 1.21.9 / 1.21.10", "88 = 1.21.9 / 1.21.10")
    s = s.replace("({@code pack_format} 64)", "({@code pack_format} 69)")
    s = s.replace("1.21.9 (pack_format 64)", "1.21.9 (pack_format 69)")
    s = s.replace("773クライアントに送るリソースパック（pack_format 64）",
                  "773クライアントに送るリソースパック（pack_format 69）")
    s = s.replace("773クライアントには pack773（pack_format 64）",
                  "773クライアントには pack773（pack_format 69）")
    s = s.replace("to 773 clients (pack_format 64)", "to 773 clients (pack_format 69)")
    s = s.replace("81 for 1.21.9", "88 for 1.21.9")
    return s


def live_text(rel):
    return open(TOOLS.parent / rel, encoding="utf-8").read()


LIVE = {
    "ResourcePackBuilder.java": live_text("src/main/java/io/detergentmc/protocol/ResourcePackBuilder.java"),
    "ResourcePackMode.java": live_text("src/main/java/io/detergentmc/protocol/ResourcePackMode.java"),
    "ResourcePackServer.java": live_text("src/main/java/io/detergentmc/protocol/ResourcePackServer.java"),
    "DatapackGenerator.java": live_text("../detergentmc-core/src/main/java/io/detergentmc/core/vanilla/DatapackGenerator.java"),
}


def rep(text, old, new, count=1):
    assert text.count(old) == count, f"{old[:60]!r} が {text.count(old)} 回（{count}回のはず）"
    return text.replace(old, new, 1)


# ------------------------------------------------------------------
# 手書き new773（非 append edit）
# ------------------------------------------------------------------
def manual(name773, old773, old772, new772, ctx=""):
    if name773 == "class javadoc 773 section":
        # 772節を「 */」の前に挿入する形。773節は772節を shift して数値を直す。
        assert old773.endswith(" */")
        prefix = old773[:-3]
        sec772 = new772[len(old772) - 3:-3] if new772.endswith(" */") else new772[len(old772) - 3:]
        sec773 = fix_suffix(shift(sec772))
        return prefix + sec773 + " */"
    if name773 == "Decision javadoc pack773":
        new = rep(old773,
                  "     *                before)\n     */\n    public record Decision(",
                  "     *                before)\n"
                  "     * @param pack773 the 1.21.9 pack to offer to 773 clients in\n"
                  "     *                external mode, or {@code null} when none is\n"
                  "     *                configured (773 clients then get {@code pack} as\n"
                  "     *                before)\n"
                  "     */\n    public record Decision(")
        return rep(new, "ResourcePack pack771, ResourcePack pack772) {",
                   "ResourcePack pack771, ResourcePack pack772, ResourcePack pack773) {")
    if name773 == "without-773 Decision constructor":
        new = old773
        new = rep(new, "pack769, null, null, null);", "pack769, null, null, null, null);")
        new = rep(new, "pack770, null, null);", "pack770, null, null, null);")
        new = rep(new, "pack771, null);", "pack771, null, null);")
        # without-772 コンストラクタのあとに without-773 を追加する
        anchor = """        /** Backwards-compatible constructor without a 1.21.7 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,
                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
                ResourcePack pack770, ResourcePack pack771) {
            this(kind, pack, problem, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, null, null);
        }
"""
        assert anchor in new, "without-772 コンストラクタが見つからない"
        add = anchor + """
        /** Backwards-compatible constructor without a 1.21.9 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack763, ResourcePack pack764, ResourcePack pack766,
                ResourcePack pack767, ResourcePack pack768, ResourcePack pack769,
                ResourcePack pack770, ResourcePack pack771, ResourcePack pack772) {
            this(kind, pack, problem, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, null);
        }
"""
        return new.replace(anchor, add, 1)
    if name773 == "packForProtocol javadoc":
        if "server" in ctx:
            return rep(old773, "772→{@link #pack772()}（pack_format 64）、",
                       "772→{@link #pack772()}（pack_format 64）、\n     * 773→{@link #pack773()}（pack_format 69）、")
        return rep(old773, "772→{@code pack772}（64）。",
                   "772→{@code pack772}（64）、773→{@code pack773}（69）。")
    if name773 == "packForProtocol branch":
        if "server" in ctx:
            return rep(old773,
                       "        if (clientProtocol == ProtocolVersion.V1_21_7) {\n            return pack772();\n        }\n        return mainPack;",
                       "        if (clientProtocol == ProtocolVersion.V1_21_7) {\n            return pack772();\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_9) {\n            return pack773();\n        }\n        return mainPack;")
        return rep(old773,
                   "            if (protocol == ProtocolVersion.V1_21_7) {\n"
                   "                return pack772 != null ? pack772 : pack;\n"
                   "            }\n            return pack;",
                   "            if (protocol == ProtocolVersion.V1_21_7) {\n"
                   "                return pack772 != null ? pack772 : pack;\n"
                   "            }\n"
                   "            if (protocol == ProtocolVersion.V1_21_9) {\n"
                   "                return pack773 != null ? pack773 : pack;\n"
                   "            }\n            return pack;")
    if name773 == "decide 772 overload becomes delegating":
        return decide_overload_773(old773)
    if name773 == "decide return":
        return rep(old773, "pack770, pack771, pack772);", "pack770, pack771, pack772, pack773);")
    if name773 == "full constructor javadoc":
        new = rep(old773,
                  "     * @param pack772File 1.21.7 variant zip file, or {@code null}\n"
                  "     *                    to serve no 1.21.7 variant\n"
                  "     * @param sha1772     lower-case hex SHA-1 of {@code pack772File}\n"
                  "     *                    (required when {@code pack772File} is given)\n"
                  "     */",
                  "     * @param pack772File 1.21.7 variant zip file, or {@code null}\n"
                  "     *                    to serve no 1.21.7 variant\n"
                  "     * @param sha1772     lower-case hex SHA-1 of {@code pack772File}\n"
                  "     *                    (required when {@code pack772File} is given)\n"
                  "     * @param pack773File 1.21.9 variant zip file, or {@code null}\n"
                  "     *                    to serve no 1.21.9 variant\n"
                  "     * @param sha1773     lower-case hex SHA-1 of {@code pack773File}\n"
                  "     *                    (required when {@code pack773File} is given)\n"
                  "     */")
        return rep(new,
                   "            Path pack771File, String sha1771, Path pack772File, String sha1772,\n            int port, boolean required, String promptKey) {",
                   "            Path pack771File, String sha1771, Path pack772File, String sha1772,\n"
                   "            Path pack773File, String sha1773,\n            int port, boolean required, String promptKey) {")
    if name773 == "full constructor assignment":
        return rep(old773,
                   "        this.pack772File = pack772File;\n        this.sha1772 = sha1772;\n        this.requestedPort = port;",
                   "        this.pack772File = pack772File;\n        this.sha1772 = sha1772;\n        this.pack773File = pack773File;\n        this.sha1773 = sha1773;\n        this.requestedPort = port;")
    if name773 == "without-773 constructor":
        return without_server_ctor_773(old773)
    if name773 == "BuiltPack 10-pack constructors":
        return builtpack_773(old773)
    if name773 == "stop() clear 772/773":
        return rep(old773, "        pack772Bytes = null;\n        mainPack = null;",
                   "        pack772Bytes = null;\n        pack773Bytes = null;\n        mainPack = null;")
    if name773 == "packFormatFor 773":
        return rep(old773, '"1.21.7" / "1.21.8" -&gt; 81). */',
                   '"1.21.7" / "1.21.8" -&gt; 81, "1.21.9" / "1.21.10" -&gt; 88). */')
    if name773 == "packFormatFor branch 773":
        return rep(old773,
                   '        if ("1.21.7".equals(minecraftVersion) || "1.21.8".equals(minecraftVersion)) {\n'
                   '            return PACK_FORMAT_1_21_7;\n'
                   '        }\n        return PACK_FORMAT_1_20_4;',
                   '        if ("1.21.7".equals(minecraftVersion) || "1.21.8".equals(minecraftVersion)) {\n'
                   '            return PACK_FORMAT_1_21_7;\n'
                   '        }\n'
                   '        if ("1.21.9".equals(minecraftVersion) || "1.21.10".equals(minecraftVersion)) {\n'
                   '            return PACK_FORMAT_1_21_9;\n'
                   '        }\n        return PACK_FORMAT_1_20_4;')
    raise AssertionError(f"手書きが無い非 append edit: {name773}")


def decide_overload_773(old773):
    """772フル decide を委譲版にし、773フル decide を追加する。"""
    new = old773
    # 770委譲：null 4個 → 6個
    new = rep(new, "null, null, null, null, required, promptKey);",
              "null, null, null, null, null, null, required, promptKey);")
    # 771委譲：null 2個 → 4個（771委譲の return decide は1か所）
    new = rep(new, "                configuredUrl771, configuredSha1771,\n                null, null, required, promptKey);",
              "                configuredUrl771, configuredSha1771,\n                null, null, null, null, required, promptKey);")
    # 772フル decide の javadoc・署名を抜き出す
    javadoc_start = new.find("     * The 772 pack is one pack for 1.21.7")
    assert javadoc_start >= 0
    javadoc_start = new.rfind("    /**", 0, javadoc_start)
    sig_end = new.find("            boolean required, String promptKey) {", javadoc_start)
    assert sig_end >= 0
    sig_end += len("            boolean required, String promptKey) {")
    block772 = new[javadoc_start:sig_end]
    # 773フル版：772の項を残したまま773の項を足す（改名しない）。
    block773 = block772.replace("1.21.7／1.21.8 packs", "1.21.7／1.21.8 and 1.21.9／1.21.10 packs")
    block773 = block773.replace(
        "     * {@code resource-pack.url-771} / {@code resource-pack.sha1-771} and\n"
        "     * {@code resource-pack.url-772} / {@code resource-pack.sha1-772}).",
        "     * {@code resource-pack.url-771} / {@code resource-pack.sha1-771},\n"
        "     * {@code resource-pack.url-772} / {@code resource-pack.sha1-772} and\n"
        "     * {@code resource-pack.url-773} / {@code resource-pack.sha1-773}).")
    block773 = block773.replace("The 772 pack is one pack for 1.21.7／1.21.8 (protocol 772,\n     * pack_format 64)",
                                "The 773 pack is one pack for 1.21.9／1.21.10 (protocol 773,\n     * pack_format 69)")
    block773 = block773.replace("767 / 768 / 769 / 770 / 771 packs", "767 / 768 / 769 / 770 / 771 / 772 packs")
    block773 = block773.replace("yields {@code pack772}", "yields {@code pack773}")
    block773 = block773.replace("the 772 pack", "the 773 pack")
    block773 = block773.replace(
        "     * @param configuredUrl772  {@code resource-pack.url-772} (may be null/blank)\n"
        "     * @param configuredSha1772 {@code resource-pack.sha1-772} (may be null/blank)\n"
        "     */",
        "     * @param configuredUrl772  {@code resource-pack.url-772} (may be null/blank)\n"
        "     * @param configuredSha1772 {@code resource-pack.sha1-772} (may be null/blank)\n"
        "     * @param configuredUrl773  {@code resource-pack.url-773} (may be null/blank)\n"
        "     * @param configuredSha1773 {@code resource-pack.sha1-773} (may be null/blank)\n"
        "     */")
    block773 = block773.replace(
        "            String configuredUrl772, String configuredSha1772,\n"
        "            boolean required, String promptKey) {",
        "            String configuredUrl772, String configuredSha1772,\n"
        "            String configuredUrl773, String configuredSha1773,\n"
        "            boolean required, String promptKey) {")
    # 772委譲版の本体（block772 は署名の「{」まで含むため「 {」は足さない）
    delegate772 = block772 + "\n        return decide(configuredUrl, configuredSha1,\n"
    delegate772 += ("                configuredUrl763, configuredSha1763,\n"
                    "                configuredUrl764, configuredSha1764,\n"
                    "                configuredUrl766, configuredSha1766,\n"
                    "                configuredUrl767, configuredSha1767,\n"
                    "                configuredUrl768, configuredSha1768,\n"
                    "                configuredUrl769, configuredSha1769,\n"
                    "                configuredUrl770, configuredSha1770,\n"
                    "                configuredUrl771, configuredSha1771,\n"
                    "                configuredUrl772, configuredSha1772,\n"
                    "                null, null, required, promptKey);\n    }\n\n")
    new = new[:javadoc_start] + delegate772 + block773 + new[sig_end:]
    return new


def without_server_ctor_773(old773):
    """without-770／771／772 の委譲に null を2個ずつ足し、without-773 を追加する。"""
    new = old773
    new = rep(new, "pack769File, sha1769, null, null, null, null, null, null, port, required, promptKey);",
              "pack769File, sha1769, null, null, null, null, null, null, null, null, port, required, promptKey);")
    new = rep(new, "pack770File, sha1770, null, null, null, null,\n                port, required, promptKey);",
              "pack770File, sha1770, null, null, null, null, null, null,\n                port, required, promptKey);")
    new = rep(new, "pack771File, sha1771, null, null,\n                port, required, promptKey);",
              "pack771File, sha1771, null, null, null, null,\n                port, required, promptKey);")
    # without-772 コンストラクタのブロックを抜き出す（「    /**」から「    }\n」まで）
    marker = "without a 1.21.7 variant: no 772 pack is served."
    mi = new.find(marker)
    assert mi >= 0
    start_i = new.rfind("    /**", 0, mi)
    end_i = new.find("    }\n", mi) + len("    }\n")
    anchor = new[start_i:end_i]
    add = anchor + """
    /**
     * {@link #ResourcePackServer(Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, Path, String, int, boolean, String)}
     * without a 1.21.9 variant: no 773 pack is served.
     */
    public ResourcePackServer(Path packFile, String sha1, Path pack763File, String sha1763,
            Path pack764File, String sha1764, Path pack766File, String sha1766,
            Path pack767File, String sha1767, Path pack768File, String sha1768,
            Path pack769File, String sha1769, Path pack770File, String sha1770,
            Path pack771File, String sha1771, Path pack772File, String sha1772,
            int port, boolean required, String promptKey) {
        this(packFile, sha1, pack763File, sha1763, pack764File, sha1764,
                pack766File, sha1766, pack767File, sha1767, pack768File, sha1768,
                pack769File, sha1769, pack770File, sha1770, pack771File, sha1771,
                pack772File, sha1772, null, null,
                port, required, promptKey);
    }
"""
    return new.replace(anchor, add, 1)


def builtpack_773(old773):
    """8-pack／9-pack の委譲に null を足し、10-pack を FULL Path 呼び出し（773は null）にし、11-pack を追加する。"""
    new = old773
    # 8-pack（promptKey 付き）の委譲：null 4個 → 6個
    new = rep(new, "built770.file(), built770.sha1(),\n                null, null, null, null,\n                port, required, promptKey);",
              "built770.file(), built770.sha1(),\n                null, null, null, null, null, null,\n                port, required, promptKey);")
    # 9-pack（promptKey 付き）の委譲：null 2個 → 4個
    new = rep(new, "built771.file(), built771.sha1(), null, null,\n                port, required, promptKey);",
              "built771.file(), built771.sha1(), null, null, null, null,\n                port, required, promptKey);")
    # 10-pack（promptKey 付き）の本体を委譲版にし、11-pack を追加する
    anchor = """    /** Convenience: serve all ten packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                port, required, promptKey);
    }
"""
    assert anchor in new, "10-pack コンストラクタが見つからない"
    add = """    /** Convenience: serve all ten packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                null, null,
                port, required, promptKey);
    }
    /**
     * Convenience: serve the 1.20.4 pack and its 1.20.1, 1.20.2,
     * 1.20.5 / 1.20.6, 1.21 / 1.21.1, 1.21.2 / 1.21.3, 1.21.4, 1.21.5, 1.21.6,
     * 1.21.7／1.21.8 and 1.21.9／1.21.10 variants, built by {@link ResourcePackBuilder}.
     */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            int port, boolean required) {
        this(built, built763, built764, built766, built767, built768, built769, built770,
                built771, built772, built773, port, required, DEFAULT_PROMPT_KEY);
    }

    /** Convenience: serve all eleven packs, with an explicit prompt key. */
    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,
            ResourcePackBuilder.BuiltPack built763, ResourcePackBuilder.BuiltPack built764,
            ResourcePackBuilder.BuiltPack built766, ResourcePackBuilder.BuiltPack built767,
            ResourcePackBuilder.BuiltPack built768, ResourcePackBuilder.BuiltPack built769,
            ResourcePackBuilder.BuiltPack built770, ResourcePackBuilder.BuiltPack built771,
            ResourcePackBuilder.BuiltPack built772, ResourcePackBuilder.BuiltPack built773,
            int port, boolean required, String promptKey) {
        this(built.file(), built.sha1(), built763.file(), built763.sha1(),
                built764.file(), built764.sha1(), built766.file(), built766.sha1(),
                built767.file(), built767.sha1(), built768.file(), built768.sha1(),
                built769.file(), built769.sha1(), built770.file(), built770.sha1(),
                built771.file(), built771.sha1(), built772.file(), built772.sha1(),
                built773.file(), built773.sha1(),
                port, required, promptKey);
    }
"""
    return new.replace(anchor, add, 1)


def emit(fn773, doc, path_defs, pairs_by_path):
    lines = [doc, "import sys", "from pathlib import Path", ""]
    lines.extend(path_defs)
    lines.append("")
    lines.append("")
    lines.append("def edit(path, pairs):")
    lines.append("    src = path.read_text(encoding=\"utf-8\")")
    lines.append("    for label, old, new in pairs:")
    lines.append("        n = src.count(old)")
    lines.append("        if n != 1:")
    lines.append("            print(f\"NG: {label}: anchor が {n} 回見つかった（1回のはず）\", file=sys.stderr)")
    lines.append("            sys.exit(1)")
    lines.append("        src = src.replace(old, new, 1)")
    lines.append("        print(f\"OK: {label}\")")
    lines.append("    path.write_text(src, encoding=\"utf-8\")")
    lines.append("")
    for var, pairs in pairs_by_path:
        lines.append("")
        lines.append(f"edit({var}, [")
        for name, old, new in pairs:
            lines.append(f"    ({name!r}, {old!r},")
            lines.append(f"     {new!r}),")
        lines.append("])")
    lines.append("")
    lines.append('print("773 の対応を挿入した")')
    lines.append("")
    open(TOOLS / fn773, "w", encoding="utf-8").write("\n".join(lines) + "\n")


NAME773 = {
    "class javadoc 772 section": "class javadoc 773 section",
    "PACK_FORMAT_1_21_7": "PACK_FORMAT_1_21_9",
    "PACK_FILE_NAME_772": "PACK_FILE_NAME_773",
    "PACK_MCMETA_772": "PACK_MCMETA_773",
    "packFormatForProtocol 772": "packFormatForProtocol 773",
    "build772": "build773",
    "writePack772": "writePack773",
    "writePack switch 772": "writePack switch 773",
    "Decision javadoc pack772": "Decision javadoc pack773",
    "without-772 Decision constructor": "without-773 Decision constructor",
    "decide 771 overload becomes delegating": "decide 772 overload becomes delegating",
    "decide body pack772 var": "decide body pack773 var",
    "decide body url772 block": "decide body url773 block",
    "PACK_PATH_772": "PACK_PATH_773",
    "pack772 fields": "pack773 fields",
    "pack772Bytes field": "pack773Bytes field",
    "without-772 constructor": "without-773 constructor",
    "BuiltPack 9-pack constructors": "BuiltPack 10-pack constructors",
    "start() load 772": "start() load 773",
    "start() serve 772": "start() serve 773",
    "stop() clear 771/772": "stop() clear 772/773",
    "url772/pack772": "url773/pack773",
    "packFormatFor 772": "packFormatFor 773",
    "packFormatFor branch 772": "packFormatFor branch 773",
    "packForProtocol javadoc": "packForProtocol javadoc",
    "packForProtocol branch": "packForProtocol branch",
    "decide return": "decide return",
    "full constructor javadoc": "full constructor javadoc",
    "full constructor validation": "full constructor validation",
    "full constructor assignment": "full constructor assignment",
}


def process(fn772, fn773, doc, path_defs, live_key):
    pairs_by_path = load_pairs(fn772)
    new_by_path = []
    for var, pairs in pairs_by_path:
        new_pairs = []
        for name, old772, new772 in pairs:
            name773 = NAME773.get(name, name.replace("772", "773"))
            old773 = new772
            live = LIVE[live_key]
            assert live.count(old773) == 1, f"{fn773} {name773}: old_773 が live に {live.count(old773)} 回"
            if new772.startswith(old772):
                suffix = new772[len(old772):]
                new773 = old773 + fix_suffix(shift(suffix))
            else:
                new773 = manual(name773, old773, old772, new772, ctx=fn772)
            new_pairs.append((name773, old773, new773))
        new_by_path.append((var, new_pairs))
    emit(fn773, doc, path_defs, new_by_path)
    print(f"{fn773} を生成した")


process("patch-resourcepackbuilder-772.py", "patch-resourcepackbuilder-773.py",
        '"""ResourcePackBuilder.java へ Java 1.21.9／1.21.10（プロトコル773）の対応を入れる。\n\npack_format 69（リソースパック）は Minecraft Wiki の pack_format 表で\n確認した値。772の流儀をなぞる：PACK_FORMAT_1_21_7／PACK_FILE_NAME_772／\nPACK_MCMETA_772／build772／writePack772 の直後に773版を増やす。\n各アンカーはファイル中にちょうど1回だけ現れること。\n"""',
        ['PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \\',
         '    / "io" / "detergentmc" / "protocol" / "ResourcePackBuilder.java"'],
        "ResourcePackBuilder.java")

process("patch-resourcepackmode-772.py", "patch-resourcepackmode-773.py",
        '"""ResourcePackMode.java へ Java 1.21.9／1.21.10（プロトコル773）の対応を入れる。\n\nDecision レコードに pack773 を追加し、decide() の 773 オーバーロードを\n増やす。772の流儀をなぞる：フル decide() を773まで伸ばし、772までの\ndecide() を「773無し」の委譲版にする。後方互換コンストラクタも同様。\n各アンカーはファイル中にちょうど1回だけ現れること。\n"""',
        ['PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \\',
         '    / "io" / "detergentmc" / "protocol" / "ResourcePackMode.java"'],
        "ResourcePackMode.java")

process("patch-resourcepackserver-772.py", "patch-resourcepackserver-773.py",
        '"""ResourcePackServer.java へ Java 1.21.9／1.21.10（プロトコル773）の対応を入れる。\n\n773用パック（pack_format 69、detergentmc-pack-1.21.9.zip）を11番目に増やす。\n772の流儀をなぞる：フルコンストラクタを773まで伸ばし、772までの\nコンストラクタを「773無し」の委譲版にする。\n各アンカーはファイル中にちょうど1回だけ現れること。\n"""',
        ['PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \\',
         '    / "io" / "detergentmc" / "protocol" / "ResourcePackServer.java"'],
        "ResourcePackServer.java")

process("patch-datapackgenerator-772.py", "patch-datapackgenerator-773.py",
        '"""DatapackGenerator.java へ Java 1.21.9／1.21.10（プロトコル773）の対応を入れる。\n\nデータパックの pack_format 88 は Minecraft Wiki の pack_format 表で\n確認した値（リソースパックの 69 とは別の番号）。772の流儀をなぞる：\nPACK_FORMAT_1_21_7（81）の直後に PACK_FORMAT_1_21_9（88）を増やし、\npackFormatFor に "1.21.9"／"1.21.10" の分岐を増やす。\n各アンカーはファイル中にちょうど1回だけ現れること。\n"""',
        ['PATH = Path(__file__).resolve().parent.parent.parent / "detergentmc-core" / "src" / "main" / "java" \\',
         '    / "io" / "detergentmc" / "core" / "vanilla" / "DatapackGenerator.java"'],
        "DatapackGenerator.java")

print("生成完了")
