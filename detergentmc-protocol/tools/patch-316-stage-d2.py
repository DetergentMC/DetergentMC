#!/usr/bin/env python3
"""316 Stage D2 の配線を既存ファイルへ適用する（338 の D2 と同型）。

対象：ResourcePackBuilder、ResourcePackMode、ResourcePackServer、
ClientProxy、ProxySession（パック受け渡し部分）。
各置換は期待件数を検証し、合わなければ失敗として報告する。
ResourcePackMode の decide() は、338 ペアまでで終わる現在の完全版
シグネチャをプログラム的に取り出し、335 ペア付きの新しい完全版と、
旧シグネチャの互換オーバーロードに分ける。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
PROTO = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol"
failures = []


def patch(path, replacements):
    text = path.read_text(encoding="utf-8")
    for old, new, expected in replacements:
        count = text.count(old)
        if count != expected:
            failures.append(f"{path.name}: 期待 {expected} 件のところ {count} 件: {old[:70]!r}")
            continue
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
    print(f"{path.name}: 適用した")


# ---------- ResourcePackBuilder ----------
patch(PROTO / "ResourcePackBuilder.java", [
    ("""    /** Minecraft 1.12 resource-pack format (pack_format 3; same value and layout as 1.12.1/1.12.2). */
    public static final int PACK_FORMAT_1_12 = 3;""",
     """    /** Minecraft 1.11.2 resource-pack format (pack_format 3; same value and layout as 1.12/1.12.1/1.12.2). */
    public static final int PACK_FORMAT_1_11_2 = 3;

    /** Minecraft 1.12 resource-pack format (pack_format 3; same value and layout as 1.12.1/1.12.2). */
    public static final int PACK_FORMAT_1_12 = 3;""", 1),
    ('    public static final String PACK_FILE_NAME_335 = "detergentmc-pack-1.12.zip";',
     '    public static final String PACK_FILE_NAME_316 = "detergentmc-pack-1.11.2.zip";\n'
     '    public static final String PACK_FILE_NAME_335 = "detergentmc-pack-1.12.zip";', 1),
    ("""    private static final String PACK_MCMETA_335 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.12)\\"}}";""",
     """    private static final String PACK_MCMETA_316 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.11.2)\\"}}";

    private static final String PACK_MCMETA_335 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.12)\\"}}";""", 1),
    ("            case ProtocolVersion.V1_12 -> PACK_FORMAT_1_12;",
     "            case ProtocolVersion.V1_11_2 -> PACK_FORMAT_1_11_2;\n"
     "            case ProtocolVersion.V1_12 -> PACK_FORMAT_1_12;", 1),
    ("""    public BuiltPack build335(Path outputDir) throws IOException {""",
     """    /**
     * Builds the 1.11.2 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.11.2.zip} and returns the file
     * plus its SHA-1. Same pack_format (3) and same old pre-1.13 asset
     * layout as the 1.12/1.12.2 variants; only the file name and the
     * {@code pack.mcmeta} description differ.
     */
    public BuiltPack build316(Path outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir is required");
        }
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(PACK_FILE_NAME_316);
        writePack316(file);
        return new BuiltPack(file, sha1Hex(file));
    }

    public BuiltPack build335(Path outputDir) throws IOException {""", 1),
    ("""    /** Writes the 1.12 variant of the pack zip to {@code file}. */
    public void writePack335(Path file) throws IOException {""",
     """    /** Writes the 1.11.2 variant of the pack zip to {@code file}. */
    public void writePack316(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_11_2, PACK_MCMETA_316);
    }

    /** Writes the 1.12 variant of the pack zip to {@code file}. */
    public void writePack335(Path file) throws IOException {""", 1),
])

# ---------- ResourcePackMode ----------
mode_path = PROTO / "ResourcePackMode.java"
text = mode_path.read_text(encoding="utf-8")

# 1. レコードのコンポーネントに pack316 を追加。
old = ("            ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,\n"
       "            ResourcePack pack335) {")
if text.count(old) != 1:
    failures.append("ResourcePackMode: レコード定義が見つからない")
else:
    text = text.replace(old,
                        "            ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,\n"
                        "            ResourcePack pack335, ResourcePack pack316) {")

# 2. 互換コンストラクタの this(...) 委譲のうち、canonical コンストラクタへ
#    直接委譲するもの（引数数が最大＝canonical のコンポーネント数と一致）
#    だけ引数を1つ増やす。中間コンストラクタへの委譲は件数が合わなく
#    なるため触らない。
lines = text.split("\n")
counts = []
for line in lines:
    s = line.strip()
    if s.startswith("this(kind, pack, problem,") and s.endswith(");"):
        counts.append(s[len("this("):-2].count(",") + 1)
canonical_count = max(counts)
delegations = 0
for i, line in enumerate(lines):
    s = line.strip()
    if s.startswith("this(kind, pack, problem,") and s.endswith(");"):
        if s[len("this("):-2].count(",") + 1 == canonical_count:
            lines[i] = line.rstrip()[:-2] + ", null);"
            delegations += 1
text = "\n".join(lines)
if delegations < 1:
    failures.append(f"ResourcePackMode: canonical委譲が {delegations} 件しかない")
print(f"ResourcePackMode: canonical への this委譲 {delegations} 件を更新")

# 3. pack316 無しの互換コンストラクタを追加（338無しコンストラクタの直後）。
anchor = "pack393, pack340, null, null);\n        }"
if text.count(anchor) != 1:
    failures.append("ResourcePackMode: 互換コンストラクタの挿入位置が見つからない")
else:
    new_ctor = anchor + """

        /** Backwards-compatible constructor without a 1.11.2 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack758, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
                ResourcePack pack766, ResourcePack pack767, ResourcePack pack768,
                ResourcePack pack769, ResourcePack pack770, ResourcePack pack771,
                ResourcePack pack772, ResourcePack pack773, ResourcePack pack774,
                ResourcePack pack775, ResourcePack pack776, ResourcePack pack578,
                ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,
                ResourcePack pack490, ResourcePack pack485, ResourcePack pack480, ResourcePack pack477,
                ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,
                ResourcePack pack335) {
            this(kind, pack, problem, pack758, pack759, pack760, pack761, pack762, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485, pack480, pack477, pack404, pack401, pack393, pack340, pack335, null);
        }"""
    text = text.replace(anchor, new_ctor)

# 4. Decision.packForProtocol に 335 分岐を追加（338 分岐のヘッダは
#    残し、その直前へ 335 分岐を挿入する）。
old = """            if (protocol == ProtocolVersion.V1_12) {
                return pack335 != null ? pack335"""
if text.count(old) != 1:
    failures.append("ResourcePackMode: packForProtocol の 338 分岐が見つからない")
else:
    text = text.replace(old, """            if (protocol == ProtocolVersion.V1_11_2) {
                return pack316 != null ? pack316
                        : pack335 != null ? pack335
                        : pack340 != null ? pack340
                        : pack393 != null ? pack393
                        : pack401 != null ? pack401
                        : pack404 != null ? pack404
                        : pack477 != null ? pack477
                        : pack480 != null ? pack480
                        : pack485 != null ? pack485
                        : pack490 != null ? pack490
                        : pack498 != null ? pack498
                        : pack573 != null ? pack573
                        : pack575 != null ? pack575
                        : pack578 != null ? pack578
                        : pack758 != null ? pack758
                        : pack759 != null ? pack759
                        : pack760 != null ? pack760
                        : pack761 != null ? pack761
                        : pack762 != null ? pack762
                        : pack763 != null ? pack763 : pack;
            }
            if (protocol == ProtocolVersion.V1_12) {
                return pack335 != null ? pack335""")

# 5. decide() 完全版：シグネチャへ 335 ペアを追加し、旧シグネチャの
#    互換オーバーロードを直前に挿入する。
sig_re = re.compile(
    r"public static Decision decide\(String configuredUrl, String configuredSha1,"
    r"(?:[^)]*?)boolean required, String promptKey\) \{", re.DOTALL)
candidates = [m for m in sig_re.finditer(text) if "configuredUrl335" in m.group(0)]
if len(candidates) != 1:
    failures.append(f"ResourcePackMode: decide完全版の候補が {len(candidates)} 件")
else:
    m = candidates[0]
    signature = m.group(0)
    # シグネチャ末尾の `String promptKey` は引数名一覧から除く
    # （promptKey は委譲の最後に別途渡す）。
    names = re.findall(r"String (\w+)", signature)
    if names and names[-1] == "promptKey":
        names = names[:-1]
    delegation_args = ", ".join(names)
    compat = ("/** Backwards-compatible overload without a 1.11.2 pack pair. */\n    "
              + signature + "\n        return decide(" + delegation_args
              + ", \"\", \"\", required, promptKey);\n    }\n\n    ")
    new_signature = signature.replace(
        "String configuredUrl335, String configuredSha1335,",
        "String configuredUrl335, String configuredSha1335,\n"
        "            String configuredUrl316, String configuredSha1316,")
    text = text[:m.start()] + compat + new_signature + text[m.end():]

# 6. decide() 本体：pack316 の宣言・検証・詰め替え。
for old, new, expected in [
    ("            ResourcePack pack335 = null;",
     "            ResourcePack pack335 = null;\n            ResourcePack pack316 = null;", 1),
    ("""                    pack335 = new ResourcePack(url335,
                            configuredSha1335.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""",
     """                    pack335 = new ResourcePack(url335,
                            configuredSha1335.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url316 = configuredUrl316 == null ? "" : configuredUrl316.trim();
            if (!url316.isEmpty()) {
                if (!url316.startsWith("https://")) {
                    problems.add("resource-pack.url-335 must start with https:// (got '"
                            + url316 + "'); 316 clients will be offered the 1.12 pack instead");
                } else if (!isValidSha1(configuredSha1316)) {
                    problems.add("resource-pack.sha1-335 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1316
                            + "'; 316 clients will be offered the 1.12 pack instead");
                } else {
                    pack316 = new ResourcePack(url316,
                            configuredSha1316.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""", 1),
    ("pack404, pack401, pack393, pack340, pack335);",
     "pack404, pack401, pack393, pack340, pack335, pack316);", 1),
]:
    count = text.count(old)
    if count != expected:
        failures.append(f"ResourcePackMode: 期待 {expected} 件のところ {count} 件: {old[:70]!r}")
        continue
    text = text.replace(old, new)
mode_path.write_text(text, encoding="utf-8")
print("ResourcePackMode.java: 適用した")

# ---------- ResourcePackServer ----------
patch(PROTO / "ResourcePackServer.java", [
    ('    public static final String PACK_PATH_335 = "/detergentmc-pack-1.12.zip";',
     '    public static final String PACK_PATH_316 = "/detergentmc-pack-1.11.2.zip";\n'
     '    public static final String PACK_PATH_335 = "/detergentmc-pack-1.12.zip";', 1),
    ("""    private final Path pack335File;
    private final String sha1335;""",
     """    private final Path pack316File;
    private final String sha1316;
    private final Path pack335File;
    private final String sha1335;""", 1),
    ("    private byte[] pack335Bytes;",
     "    private byte[] pack316Bytes;\n    private byte[] pack335Bytes;", 1),
    ("""        this.pack335File = null;
        this.sha1335 = null;""",
     """        this.pack316File = null;
        this.sha1316 = null;
        this.pack335File = null;
        this.sha1335 = null;""", 6),
    ("public ResourcePackServer(Path packFile, String sha1, Path pack335File, String sha1335, Path pack340File,",
     "public ResourcePackServer(Path packFile, String sha1, Path pack316File, String sha1316, Path pack335File, String sha1335, Path pack340File,", 1),
    ("""        if (pack335File != null && (sha1335 == null || sha1335.isBlank())) {
            throw new IllegalArgumentException("sha1335 is required when pack335File is given");
        }""",
     """        if (pack316File != null && (sha1316 == null || sha1316.isBlank())) {
            throw new IllegalArgumentException("sha1316 is required when pack316File is given");
        }
        if (pack335File != null && (sha1335 == null || sha1335.isBlank())) {
            throw new IllegalArgumentException("sha1335 is required when pack335File is given");
        }""", 1),
    ("""        this.pack335File = pack335File;
        this.sha1335 = sha1335;""",
     """        this.pack316File = pack316File;
        this.sha1316 = sha1316;
        this.pack335File = pack335File;
        this.sha1335 = sha1335;""", 1),
    ("""        if (pack335File != null) {
            pack335Bytes = readAndVerify(pack335File, sha1335);
        }""",
     """        if (pack316File != null) {
            pack316Bytes = readAndVerify(pack316File, sha1316);
        }
        if (pack335File != null) {
            pack335Bytes = readAndVerify(pack335File, sha1335);
        }""", 1),
    ("""        if (pack335Bytes != null) {
            httpServer.createContext(PACK_PATH_335, exchange -> serveBytes(exchange, pack335Bytes));
        }""",
     """        if (pack316Bytes != null) {
            httpServer.createContext(PACK_PATH_316, exchange -> serveBytes(exchange, pack316Bytes));
        }
        if (pack335Bytes != null) {
            httpServer.createContext(PACK_PATH_335, exchange -> serveBytes(exchange, pack335Bytes));
        }""", 1),
    ("        pack335Bytes = null;",
     "        pack316Bytes = null;\n        pack335Bytes = null;", 1),
    ("""    /** The download URL of the 1.12 variant, or {@code null} when not served. */
    public synchronized String url335() {""",
     """    /** The download URL of the 1.11.2 variant, or {@code null} when not served. */
    public synchronized String url316() {
        if (pack316File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_316;
    }

    /**
     * The 1.11.2 {@link ResourcePack} to offer to 335 clients, or
     * {@code null} when no 1.11.2 variant is served.
     */
    public synchronized ResourcePack pack316() {
        return pack316File == null ? null
                : new ResourcePack(url316(), sha1316, required, promptKey);
    }

    /** The download URL of the 1.12 variant, or {@code null} when not served. */
    public synchronized String url335() {""", 1),
    ("""        if (clientProtocol == ProtocolVersion.V1_12) {
            // 338パックが無い場合は340パック（同じ pack_format 3・""",
     """        if (clientProtocol == ProtocolVersion.V1_11_2) {
            // 335パックが無い場合は338パック（同じ pack_format 3・
            // 同じ旧レイアウト。説明文だけが 1.12 用になる）、
            // さらに無い場合は338分岐と同じ順に代替する。
            ResourcePack pack316 = pack316();
            if (pack316 != null) {
                return pack316;
            }
            return packForProtocol(ProtocolVersion.V1_12);
        }
        if (clientProtocol == ProtocolVersion.V1_12) {
            // 338パックが無い場合は340パック（同じ pack_format 3・""", 1),
    ("            ResourcePackBuilder.BuiltPack built335,",
     "            ResourcePackBuilder.BuiltPack built316,\n            ResourcePackBuilder.BuiltPack built335,", 1),
    ("        this(built.file(), built.sha1(), built335.file(), built335.sha1(), built340.file(),",
     "        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built335.file(), built335.sha1(), built340.file(),", 1),
    ("serve all thirty-two packs", "serve all thirty-three packs", 1),
])

# 注：ResourcePackServer の BuiltPack 系コンストラクタのうち、フル
# コンストラクタへ委譲する11件は、335 スロット分の `null, null` を
# 委譲先頭（built.sha1() の直後）へ別途挿入した（コンパイルエラーで
# 特定した行：2183, 2200, 2260, 2287, 2313, 2340, 2367, 2394, 2421,
# 2448, 2475。いずれも built316 を持たないコンストラクタのため null）。

# ---------- ClientProxy ----------
patch(PROTO / "ClientProxy.java", [
    ("    private ResourcePack pack335;",
     "    private ResourcePack pack316;\n    private ResourcePack pack335;", 1),
    ("""    /**
     * 338クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-338} 等）。
     */
    public void setPack335(ResourcePack pack335) {""",
     """    /**
     * 335クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-335} 等）。
     */
    public void setPack316(ResourcePack pack316) {
        this.pack316 = pack316;
    }

    /** 335クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack316() {
        return pack316;
    }

    /**
     * 338クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-338} 等）。
     */
    public void setPack335(ResourcePack pack335) {""", 1),
    ("                session.setPack335(proxy.pack335);",
     "                session.setPack316(proxy.pack316);\n                session.setPack335(proxy.pack335);", 1),
])

# ---------- ProxySession（パック受け渡し） ----------
patch(PROTO / "ProxySession.java", [
    ("    private ResourcePack pack335;",
     "    private ResourcePack pack316;\n    private ResourcePack pack335;", 1),
    ("""    /** 338クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack335(ResourcePack pack335) {""",
     """    /** 335クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack316(ResourcePack pack316) {
        this.pack316 = pack316;
    }

    /** 335クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack316() {
        return pack316;
    }

    /** 338クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack335(ResourcePack pack335) {""", 1),
    ("""            if (is335()) {
                if (pack335 != null) {
                    return pack335;
                }
            }""",
     """            if (is316()) {
                if (pack316 != null) {
                    return pack316;
                }
            }
            if (is335()) {
                if (pack335 != null) {
                    return pack335;
                }
            }""", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
