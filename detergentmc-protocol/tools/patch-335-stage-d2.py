#!/usr/bin/env python3
"""335 Stage D2 の配線を既存ファイルへ適用する（338 の D2 と同型）。

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
    ("""    /** Minecraft 1.12.1 resource-pack format (pack_format 3; same value and layout as 1.12.2). */
    public static final int PACK_FORMAT_1_12_1 = 3;""",
     """    /** Minecraft 1.12 resource-pack format (pack_format 3; same value and layout as 1.12.1/1.12.2). */
    public static final int PACK_FORMAT_1_12 = 3;

    /** Minecraft 1.12.1 resource-pack format (pack_format 3; same value and layout as 1.12.2). */
    public static final int PACK_FORMAT_1_12_1 = 3;""", 1),
    ('    public static final String PACK_FILE_NAME_338 = "detergentmc-pack-1.12.1.zip";',
     '    public static final String PACK_FILE_NAME_335 = "detergentmc-pack-1.12.zip";\n'
     '    public static final String PACK_FILE_NAME_338 = "detergentmc-pack-1.12.1.zip";', 1),
    ("""    private static final String PACK_MCMETA_338 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.12.1)\\"}}";""",
     """    private static final String PACK_MCMETA_335 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.12)\\"}}";

    private static final String PACK_MCMETA_338 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.12.1)\\"}}";""", 1),
    ("            case ProtocolVersion.V1_12_1 -> PACK_FORMAT_1_12_1;",
     "            case ProtocolVersion.V1_12 -> PACK_FORMAT_1_12;\n"
     "            case ProtocolVersion.V1_12_1 -> PACK_FORMAT_1_12_1;", 1),
    ("""    public BuiltPack build338(Path outputDir) throws IOException {""",
     """    /**
     * Builds the 1.12 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.12.zip} and returns the file
     * plus its SHA-1. Same pack_format (3) and same old pre-1.13 asset
     * layout as the 1.12.1/1.12.2 variants; only the file name and the
     * {@code pack.mcmeta} description differ.
     */
    public BuiltPack build335(Path outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir is required");
        }
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(PACK_FILE_NAME_335);
        writePack335(file);
        return new BuiltPack(file, sha1Hex(file));
    }

    public BuiltPack build338(Path outputDir) throws IOException {""", 1),
    ("""    /** Writes the 1.12.1 variant of the pack zip to {@code file}. */
    public void writePack338(Path file) throws IOException {""",
     """    /** Writes the 1.12 variant of the pack zip to {@code file}. */
    public void writePack335(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_12, PACK_MCMETA_335);
    }

    /** Writes the 1.12.1 variant of the pack zip to {@code file}. */
    public void writePack338(Path file) throws IOException {""", 1),
])

# ---------- ResourcePackMode ----------
mode_path = PROTO / "ResourcePackMode.java"
text = mode_path.read_text(encoding="utf-8")

# 1. レコードのコンポーネントに pack335 を追加。
old = ("            ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,\n"
       "            ResourcePack pack338) {")
if text.count(old) != 1:
    failures.append("ResourcePackMode: レコード定義が見つからない")
else:
    text = text.replace(old,
                        "            ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,\n"
                        "            ResourcePack pack338, ResourcePack pack335) {")

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
if delegations != 5:
    failures.append(f"ResourcePackMode: canonical委譲は5件のはずが {delegations} 件")
print(f"ResourcePackMode: canonical への this委譲 {delegations} 件を更新")

# 3. pack335 無しの互換コンストラクタを追加（338無しコンストラクタの直後）。
anchor = "pack393, pack340, null, null);\n        }"
if text.count(anchor) != 1:
    failures.append("ResourcePackMode: 互換コンストラクタの挿入位置が見つからない")
else:
    new_ctor = anchor + """

        /** Backwards-compatible constructor without a 1.12 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack758, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
                ResourcePack pack766, ResourcePack pack767, ResourcePack pack768,
                ResourcePack pack769, ResourcePack pack770, ResourcePack pack771,
                ResourcePack pack772, ResourcePack pack773, ResourcePack pack774,
                ResourcePack pack775, ResourcePack pack776, ResourcePack pack578,
                ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,
                ResourcePack pack490, ResourcePack pack485, ResourcePack pack480, ResourcePack pack477,
                ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,
                ResourcePack pack338) {
            this(kind, pack, problem, pack758, pack759, pack760, pack761, pack762, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485, pack480, pack477, pack404, pack401, pack393, pack340, pack338, null);
        }"""
    text = text.replace(anchor, new_ctor)

# 4. Decision.packForProtocol に 335 分岐を追加（338 分岐のヘッダは
#    残し、その直前へ 335 分岐を挿入する）。
old = """            if (protocol == ProtocolVersion.V1_12_1) {
                return pack338 != null ? pack338"""
if text.count(old) != 1:
    failures.append("ResourcePackMode: packForProtocol の 338 分岐が見つからない")
else:
    text = text.replace(old, """            if (protocol == ProtocolVersion.V1_12) {
                return pack335 != null ? pack335
                        : pack338 != null ? pack338
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
            if (protocol == ProtocolVersion.V1_12_1) {
                return pack338 != null ? pack338""")

# 5. decide() 完全版：シグネチャへ 335 ペアを追加し、旧シグネチャの
#    互換オーバーロードを直前に挿入する。
sig_re = re.compile(
    r"public static Decision decide\(String configuredUrl, String configuredSha1,"
    r"(?:[^)]*?)boolean required, String promptKey\) \{", re.DOTALL)
candidates = [m for m in sig_re.finditer(text) if "configuredUrl338" in m.group(0)]
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
    compat = ("/** Backwards-compatible overload without a 1.12 pack pair. */\n    "
              + signature + "\n        return decide(" + delegation_args
              + ", \"\", \"\", required, promptKey);\n    }\n\n    ")
    new_signature = signature.replace(
        "String configuredUrl338, String configuredSha1338,",
        "String configuredUrl338, String configuredSha1338,\n"
        "            String configuredUrl335, String configuredSha1335,")
    text = text[:m.start()] + compat + new_signature + text[m.end():]

# 6. decide() 本体：pack335 の宣言・検証・詰め替え。
for old, new, expected in [
    ("            ResourcePack pack338 = null;",
     "            ResourcePack pack338 = null;\n            ResourcePack pack335 = null;", 1),
    ("""                    pack338 = new ResourcePack(url338,
                            configuredSha1338.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""",
     """                    pack338 = new ResourcePack(url338,
                            configuredSha1338.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url335 = configuredUrl335 == null ? "" : configuredUrl335.trim();
            if (!url335.isEmpty()) {
                if (!url335.startsWith("https://")) {
                    problems.add("resource-pack.url-335 must start with https:// (got '"
                            + url335 + "'); 335 clients will be offered the 1.12.1 pack instead");
                } else if (!isValidSha1(configuredSha1335)) {
                    problems.add("resource-pack.sha1-335 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1335
                            + "'; 335 clients will be offered the 1.12.1 pack instead");
                } else {
                    pack335 = new ResourcePack(url335,
                            configuredSha1335.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""", 1),
    ("pack404, pack401, pack393, pack340, pack338);",
     "pack404, pack401, pack393, pack340, pack338, pack335);", 1),
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
    ('    public static final String PACK_PATH_338 = "/detergentmc-pack-1.12.1.zip";',
     '    public static final String PACK_PATH_335 = "/detergentmc-pack-1.12.zip";\n'
     '    public static final String PACK_PATH_338 = "/detergentmc-pack-1.12.1.zip";', 1),
    ("""    private final Path pack338File;
    private final String sha1338;""",
     """    private final Path pack335File;
    private final String sha1335;
    private final Path pack338File;
    private final String sha1338;""", 1),
    ("    private byte[] pack338Bytes;",
     "    private byte[] pack335Bytes;\n    private byte[] pack338Bytes;", 1),
    ("""        this.pack338File = null;
        this.sha1338 = null;""",
     """        this.pack335File = null;
        this.sha1335 = null;
        this.pack338File = null;
        this.sha1338 = null;""", 6),
    ("public ResourcePackServer(Path packFile, String sha1, Path pack338File, String sha1338, Path pack340File,",
     "public ResourcePackServer(Path packFile, String sha1, Path pack335File, String sha1335, Path pack338File, String sha1338, Path pack340File,", 1),
    ("""        if (pack338File != null && (sha1338 == null || sha1338.isBlank())) {
            throw new IllegalArgumentException("sha1338 is required when pack338File is given");
        }""",
     """        if (pack335File != null && (sha1335 == null || sha1335.isBlank())) {
            throw new IllegalArgumentException("sha1335 is required when pack335File is given");
        }
        if (pack338File != null && (sha1338 == null || sha1338.isBlank())) {
            throw new IllegalArgumentException("sha1338 is required when pack338File is given");
        }""", 1),
    ("""        this.pack338File = pack338File;
        this.sha1338 = sha1338;""",
     """        this.pack335File = pack335File;
        this.sha1335 = sha1335;
        this.pack338File = pack338File;
        this.sha1338 = sha1338;""", 1),
    ("""        if (pack338File != null) {
            pack338Bytes = readAndVerify(pack338File, sha1338);
        }""",
     """        if (pack335File != null) {
            pack335Bytes = readAndVerify(pack335File, sha1335);
        }
        if (pack338File != null) {
            pack338Bytes = readAndVerify(pack338File, sha1338);
        }""", 1),
    ("""        if (pack338Bytes != null) {
            httpServer.createContext(PACK_PATH_338, exchange -> serveBytes(exchange, pack338Bytes));
        }""",
     """        if (pack335Bytes != null) {
            httpServer.createContext(PACK_PATH_335, exchange -> serveBytes(exchange, pack335Bytes));
        }
        if (pack338Bytes != null) {
            httpServer.createContext(PACK_PATH_338, exchange -> serveBytes(exchange, pack338Bytes));
        }""", 1),
    ("        pack338Bytes = null;",
     "        pack335Bytes = null;\n        pack338Bytes = null;", 1),
    ("""    /** The download URL of the 1.12.1 variant, or {@code null} when not served. */
    public synchronized String url338() {""",
     """    /** The download URL of the 1.12 variant, or {@code null} when not served. */
    public synchronized String url335() {
        if (pack335File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_335;
    }

    /**
     * The 1.12 {@link ResourcePack} to offer to 335 clients, or
     * {@code null} when no 1.12 variant is served.
     */
    public synchronized ResourcePack pack335() {
        return pack335File == null ? null
                : new ResourcePack(url335(), sha1335, required, promptKey);
    }

    /** The download URL of the 1.12.1 variant, or {@code null} when not served. */
    public synchronized String url338() {""", 1),
    ("""        if (clientProtocol == ProtocolVersion.V1_12_1) {
            // 338パックが無い場合は340パック（同じ pack_format 3・""",
     """        if (clientProtocol == ProtocolVersion.V1_12) {
            // 335パックが無い場合は338パック（同じ pack_format 3・
            // 同じ旧レイアウト。説明文だけが 1.12.1 用になる）、
            // さらに無い場合は338分岐と同じ順に代替する。
            ResourcePack pack335 = pack335();
            if (pack335 != null) {
                return pack335;
            }
            return packForProtocol(ProtocolVersion.V1_12_1);
        }
        if (clientProtocol == ProtocolVersion.V1_12_1) {
            // 338パックが無い場合は340パック（同じ pack_format 3・""", 1),
    ("            ResourcePackBuilder.BuiltPack built338,",
     "            ResourcePackBuilder.BuiltPack built335,\n            ResourcePackBuilder.BuiltPack built338,", 1),
    ("        this(built.file(), built.sha1(), built338.file(), built338.sha1(), built340.file(),",
     "        this(built.file(), built.sha1(), built335.file(), built335.sha1(), built338.file(), built338.sha1(), built340.file(),", 1),
    ("serve all thirty-two packs", "serve all thirty-three packs", 1),
])

# 注：ResourcePackServer の BuiltPack 系コンストラクタのうち、フル
# コンストラクタへ委譲する11件は、335 スロット分の `null, null` を
# 委譲先頭（built.sha1() の直後）へ別途挿入した（コンパイルエラーで
# 特定した行：2183, 2200, 2260, 2287, 2313, 2340, 2367, 2394, 2421,
# 2448, 2475。いずれも built335 を持たないコンストラクタのため null）。

# ---------- ClientProxy ----------
patch(PROTO / "ClientProxy.java", [
    ("    private ResourcePack pack338;",
     "    private ResourcePack pack335;\n    private ResourcePack pack338;", 1),
    ("""    /**
     * 338クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-338} 等）。
     */
    public void setPack338(ResourcePack pack338) {""",
     """    /**
     * 335クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-335} 等）。
     */
    public void setPack335(ResourcePack pack335) {
        this.pack335 = pack335;
    }

    /** 335クライアントに送るリソースパック（未設定なら null）。 */
    public ResourcePack pack335() {
        return pack335;
    }

    /**
     * 338クライアントに送るリソースパックを設定する
     * （{@code detergentmc.yml} の {@code resource-pack.url-338} 等）。
     */
    public void setPack338(ResourcePack pack338) {""", 1),
    ("                session.setPack338(proxy.pack338);",
     "                session.setPack335(proxy.pack335);\n                session.setPack338(proxy.pack338);", 1),
])

# ---------- ProxySession（パック受け渡し） ----------
patch(PROTO / "ProxySession.java", [
    ("    private ResourcePack pack338;",
     "    private ResourcePack pack335;\n    private ResourcePack pack338;", 1),
    ("""    /** 338クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack338(ResourcePack pack338) {""",
     """    /** 335クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack335(ResourcePack pack335) {
        this.pack335 = pack335;
    }

    /** 335クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack335() {
        return pack335;
    }

    /** 338クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack338(ResourcePack pack338) {""", 1),
    ("""            if (is338()) {
                if (pack338 != null) {
                    return pack338;
                }
            }""",
     """            if (is335()) {
                if (pack335 != null) {
                    return pack335;
                }
            }
            if (is338()) {
                if (pack338 != null) {
                    return pack338;
                }
            }""", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
