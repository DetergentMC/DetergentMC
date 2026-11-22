#!/usr/bin/env python3
"""315 Stage D2 の配線を既存ファイルへ適用する（316 の D2 と同型）。

対象：ResourcePackBuilder、ResourcePackMode、ResourcePackServer。
315 のパックは 316 と形式同一（pack_format 3・旧レイアウト）で、
ファイル名と pack.mcmeta の説明文だけが違う。フォールバック順は
315 → 316 → 335 → 338 → …。
各置換は期待件数を検証し、合わなければ失敗として報告する。
ResourcePackServer の委譲コンストラクタへの null 挿入は、コンパイル
エラーで特定して別途行う（316 の D2 と同じ手順）。
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
    ("    public static final int PACK_FORMAT_1_11_2 = 3;",
     "    public static final int PACK_FORMAT_1_11 = 3;\n"
     "    public static final int PACK_FORMAT_1_11_2 = 3;", 1),
    ('    public static final String PACK_FILE_NAME_316 = "detergentmc-pack-1.11.2.zip";',
     '    public static final String PACK_FILE_NAME_315 = "detergentmc-pack-1.11.zip";\n'
     '    public static final String PACK_FILE_NAME_316 = "detergentmc-pack-1.11.2.zip";', 1),
    ("""    private static final String PACK_MCMETA_316 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.11.2)\\"}}";""",
     """    private static final String PACK_MCMETA_315 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.11)\\"}}";

    private static final String PACK_MCMETA_316 = "{\\"pack\\":{\\"pack_format\\":3,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.11.2)\\"}}";""", 1),
    ("            case ProtocolVersion.V1_11_2 -> PACK_FORMAT_1_11_2;",
     "            case ProtocolVersion.V1_11 -> PACK_FORMAT_1_11;\n"
     "            case ProtocolVersion.V1_11_2 -> PACK_FORMAT_1_11_2;", 1),
    ("""    /**
     * Builds the 1.11.2 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.11.2.zip} and returns the file
     * plus its SHA-1. Same pack_format (3) and same old pre-1.13 asset
     * layout as the 1.12/1.12.2 variants; only the file name and the
     * {@code pack.mcmeta} description differ.
     */
    public BuiltPack build316(Path outputDir) throws IOException {""",
     """    /**
     * Builds the 1.11 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.11.zip} and returns the file
     * plus its SHA-1. Same pack_format (3) and same old pre-1.13 asset
     * layout as the 1.11.2/1.12 variants; only the file name and the
     * {@code pack.mcmeta} description differ.
     */
    public BuiltPack build315(Path outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir is required");
        }
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(PACK_FILE_NAME_315);
        writePack315(file);
        return new BuiltPack(file, sha1Hex(file));
    }

    /**
     * Builds the 1.11.2 variant of the pack into
     * {@code outputDir/detergentmc-pack-1.11.2.zip} and returns the file
     * plus its SHA-1. Same pack_format (3) and same old pre-1.13 asset
     * layout as the 1.12/1.12.2 variants; only the file name and the
     * {@code pack.mcmeta} description differ.
     */
    public BuiltPack build316(Path outputDir) throws IOException {""", 1),
    ("""    /** Writes the 1.11.2 variant of the pack zip to {@code file}. */
    public void writePack316(Path file) throws IOException {""",
     """    /** Writes the 1.11 variant of the pack zip to {@code file}. */
    public void writePack315(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_11, PACK_MCMETA_315);
    }

    /** Writes the 1.11.2 variant of the pack zip to {@code file}. */
    public void writePack316(Path file) throws IOException {""", 1),
])

# ---------- ResourcePackMode ----------
mode_path = PROTO / "ResourcePackMode.java"
text = mode_path.read_text(encoding="utf-8")

# 1. レコードのコンポーネントに pack315 を追加。
old = ("            ResourcePack pack338, ResourcePack pack335, ResourcePack pack316) {")
if text.count(old) != 1:
    failures.append("ResourcePackMode: レコード定義が見つからない")
else:
    text = text.replace(old,
                        "            ResourcePack pack338, ResourcePack pack335, ResourcePack pack316,\n"
                        "            ResourcePack pack315) {")

# 2. canonical コンストラクタへ直接委譲する this(...) だけ引数を1つ増やす。
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

# 3. pack315 無しの互換コンストラクタを追加（316無しコンストラクタの直後）。
anchor = "pack340, pack338, pack335, null);\n        }"
if text.count(anchor) != 1:
    failures.append("ResourcePackMode: 互換コンストラクタの挿入位置が見つからない")
else:
    new_ctor = anchor + """

        /** Backwards-compatible constructor without a 1.11 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack758, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
                ResourcePack pack766, ResourcePack pack767, ResourcePack pack768,
                ResourcePack pack769, ResourcePack pack770, ResourcePack pack771,
                ResourcePack pack772, ResourcePack pack773, ResourcePack pack774,
                ResourcePack pack775, ResourcePack pack776, ResourcePack pack578,
                ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,
                ResourcePack pack490, ResourcePack pack485, ResourcePack pack480, ResourcePack pack477,
                ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,
                ResourcePack pack338, ResourcePack pack335, ResourcePack pack316) {
            this(kind, pack, problem, pack758, pack759, pack760, pack761, pack762, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485, pack480, pack477, pack404, pack401, pack393, pack340, pack338, pack335, pack316, null);
        }"""
    text = text.replace(anchor, new_ctor)

# 4. packForProtocol に 315 分岐を追加（316 分岐の直前）。
old = """            if (protocol == ProtocolVersion.V1_11_2) {
                return pack316 != null ? pack316"""
if text.count(old) != 1:
    failures.append("ResourcePackMode: packForProtocol の 316 分岐が見つからない")
else:
    text = text.replace(old, """            if (protocol == ProtocolVersion.V1_11) {
                return pack315 != null ? pack315
                        : pack316 != null ? pack316
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
            if (protocol == ProtocolVersion.V1_11_2) {
                return pack316 != null ? pack316""")

# 5. decide() 完全版：シグネチャへ 315 ペアを追加し、旧シグネチャの
#    互換オーバーロードを直前に挿入する。
sig_re = re.compile(
    r"public static Decision decide\(String configuredUrl, String configuredSha1,"
    r"(?:[^)]*?)boolean required, String promptKey\) \{", re.DOTALL)
candidates = [m for m in sig_re.finditer(text) if "configuredUrl316" in m.group(0)]
if len(candidates) != 1:
    failures.append(f"ResourcePackMode: decide完全版の候補が {len(candidates)} 件")
else:
    m = candidates[0]
    signature = m.group(0)
    names = re.findall(r"String (\w+)", signature)
    if names and names[-1] == "promptKey":
        names = names[:-1]
    delegation_args = ", ".join(names)
    compat = ("/** Backwards-compatible overload without a 1.11 pack pair. */\n    "
              + signature + "\n        return decide(" + delegation_args
              + ", \"\", \"\", required, promptKey);\n    }\n\n    ")
    new_signature = signature.replace(
        "String configuredUrl316, String configuredSha1316,",
        "String configuredUrl316, String configuredSha1316,\n"
        "            String configuredUrl315, String configuredSha1315,")
    text = text[:m.start()] + compat + new_signature + text[m.end():]

# 6. decide() 本体：pack315 の宣言・検証・詰め替え。
for old, new, expected in [
    ("            ResourcePack pack316 = null;",
     "            ResourcePack pack316 = null;\n            ResourcePack pack315 = null;", 1),
    ("""                    pack316 = new ResourcePack(url316,
                            configuredSha1316.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""",
     """                    pack316 = new ResourcePack(url316,
                            configuredSha1316.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url315 = configuredUrl315 == null ? "" : configuredUrl315.trim();
            if (!url315.isEmpty()) {
                if (!url315.startsWith("https://")) {
                    problems.add("resource-pack.url-315 must start with https:// (got '"
                            + url315 + "'); 315 clients will be offered the 1.11.2 pack instead");
                } else if (!isValidSha1(configuredSha1315)) {
                    problems.add("resource-pack.sha1-315 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1315
                            + "'; 315 clients will be offered the 1.11.2 pack instead");
                } else {
                    pack315 = new ResourcePack(url315,
                            configuredSha1315.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""", 1),
    ("pack404, pack401, pack393, pack340, pack338, pack335, pack316);",
     "pack404, pack401, pack393, pack340, pack338, pack335, pack316, pack315);", 1),
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
    ('    public static final String PACK_PATH_316 = "/detergentmc-pack-1.11.2.zip";',
     '    public static final String PACK_PATH_315 = "/detergentmc-pack-1.11.zip";\n'
     '    public static final String PACK_PATH_316 = "/detergentmc-pack-1.11.2.zip";', 1),
    ("""    private final Path pack316File;
    private final String sha1316;""",
     """    private final Path pack315File;
    private final String sha1315;
    private final Path pack316File;
    private final String sha1316;""", 1),
    ("    private byte[] pack316Bytes;",
     "    private byte[] pack315Bytes;\n    private byte[] pack316Bytes;", 1),
    ("""        this.pack316File = null;
        this.sha1316 = null;""",
     """        this.pack315File = null;
        this.sha1315 = null;
        this.pack316File = null;
        this.sha1316 = null;""", 6),
    ("Path pack316File, String sha1316, Path pack335File,",
     "Path pack316File, String sha1316, Path pack315File, String sha1315, Path pack335File,", 1),
    ("""        if (pack316File != null && (sha1316 == null || sha1316.isBlank())) {
            throw new IllegalArgumentException("sha1316 is required when pack316File is given");
        }""",
     """        if (pack316File != null && (sha1316 == null || sha1316.isBlank())) {
            throw new IllegalArgumentException("sha1316 is required when pack316File is given");
        }
        if (pack315File != null && (sha1315 == null || sha1315.isBlank())) {
            throw new IllegalArgumentException("sha1315 is required when pack315File is given");
        }""", 1),
    ("""        this.pack316File = pack316File;
        this.sha1316 = sha1316;""",
     """        this.pack316File = pack316File;
        this.sha1316 = sha1316;
        this.pack315File = pack315File;
        this.sha1315 = sha1315;""", 1),
    ("""        if (pack316File != null) {
            pack316Bytes = readAndVerify(pack316File, sha1316);
        }""",
     """        if (pack316File != null) {
            pack316Bytes = readAndVerify(pack316File, sha1316);
        }
        if (pack315File != null) {
            pack315Bytes = readAndVerify(pack315File, sha1315);
        }""", 1),
    ("""        if (pack316Bytes != null) {
            httpServer.createContext(PACK_PATH_316, exchange -> serveBytes(exchange, pack316Bytes));
        }""",
     """        if (pack316Bytes != null) {
            httpServer.createContext(PACK_PATH_316, exchange -> serveBytes(exchange, pack316Bytes));
        }
        if (pack315Bytes != null) {
            httpServer.createContext(PACK_PATH_315, exchange -> serveBytes(exchange, pack315Bytes));
        }""", 1),
    ("        pack316Bytes = null;",
     "        pack315Bytes = null;\n        pack316Bytes = null;", 1),
    ("""    /** The download URL of the 1.11.2 variant, or {@code null} when not served. */
    public synchronized String url316() {""",
     """    /** The download URL of the 1.11 variant, or {@code null} when not served. */
    public synchronized String url315() {
        if (pack315File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_315;
    }

    /**
     * The 1.11 {@link ResourcePack} to offer to 315 clients, or
     * {@code null} when no 1.11 variant is served.
     */
    public synchronized ResourcePack pack315() {
        return pack315File == null ? null
                : new ResourcePack(url315(), sha1315, required, promptKey);
    }

    /** The download URL of the 1.11.2 variant, or {@code null} when not served. */
    public synchronized String url316() {""", 1),
    ("""        if (clientProtocol == ProtocolVersion.V1_11_2) {
            // 316パックが無い場合は335パック（同じ pack_format 3・""",
     """        if (clientProtocol == ProtocolVersion.V1_11) {
            // 315パックが無い場合は316パック（同じ pack_format 3・
            // 同じ旧レイアウト。説明文だけが 1.11.2 用になる）、
            // さらに無い場合は316分岐と同じ順に代替する。
            ResourcePack pack315 = pack315();
            if (pack315 != null) {
                return pack315;
            }
            return packForProtocol(ProtocolVersion.V1_11_2);
        }
        if (clientProtocol == ProtocolVersion.V1_11_2) {
            // 316パックが無い場合は335パック（同じ pack_format 3・""", 1),
    ("            ResourcePackBuilder.BuiltPack built316,",
     "            ResourcePackBuilder.BuiltPack built315,\n            ResourcePackBuilder.BuiltPack built316,", 1),
    ("        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built335.file(),",
     "        this(built.file(), built.sha1(), built316.file(), built316.sha1(), built315.file(), built315.sha1(), built335.file(),", 1),
    ("serve all thirty-four packs", "serve all thirty-five packs", 2),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
