#!/usr/bin/env python3
"""108 Stage D2 の配線を既存ファイルへ適用する（109 の D2 と同型）。

対象：ResourcePackBuilder、ResourcePackMode、ResourcePackServer。
108 のパックは 109 と同じ旧レイアウトで、pack_format は 2。
ファイル名と pack.mcmeta の説明文だけが違う。フォールバック順は
108 → 109 → 315 → 316 → 335 → …。
各置換は期待件数を検証し、合わなければ失敗として報告する。
ResourcePackServer の委譲コンストラクタへの null 挿入は
patch-108-stage-d2c.py で行う。
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
    # pack_format 2 の定数は PACK_FORMAT_1_9_4 が既にあるため流用する
    # （109 の d2 が新規定数を足したのと違い、108 では追加しない）。
    ('    public static final String PACK_FILE_NAME_109 = "detergentmc-pack-1.9.2.zip";',
     '    public static final String PACK_FILE_NAME_108 = "detergentmc-pack-1.9.1.zip";\n'
     '    public static final String PACK_FILE_NAME_109 = "detergentmc-pack-1.9.2.zip";', 1),
    ("""    private static final String PACK_MCMETA_109 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9.2)\\"}}";""",
     """    private static final String PACK_MCMETA_108 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9.1)\\"}}";

    private static final String PACK_MCMETA_109 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9.2)\\"}}";""", 1),
    ("            case ProtocolVersion.V1_9_2 -> PACK_FORMAT_1_9_4;",
     "            case ProtocolVersion.V1_9_1 -> PACK_FORMAT_1_9_4;\n"
     "            case ProtocolVersion.V1_9_2 -> PACK_FORMAT_1_9_4;", 1),
    ("""    public BuiltPack build109(Path outputDir) throws IOException {""",
     """    public BuiltPack build108(Path outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir is required");
        }
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(PACK_FILE_NAME_108);
        writePack108(file);
        return new BuiltPack(file, sha1Hex(file));
    }

    public BuiltPack build109(Path outputDir) throws IOException {""", 1),
    ("""    public void writePack109(Path file) throws IOException {""",
     """    public void writePack108(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_9_4, PACK_MCMETA_108);
    }

    public void writePack109(Path file) throws IOException {""", 1),
])

# ---------- ResourcePackMode ----------
mode_path = PROTO / "ResourcePackMode.java"
text = mode_path.read_text(encoding="utf-8")

# 1. レコードのコンポーネントに pack108 を追加。
old = ("            ResourcePack pack110, ResourcePack pack109) {")
if text.count(old) != 1:
    failures.append("ResourcePackMode: レコード定義が見つからない")
else:
    text = text.replace(old,
                        "            ResourcePack pack110, ResourcePack pack109,\n"
                        "            ResourcePack pack108) {")

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

# 3. packForProtocol に 108 分岐を追加（109 分岐の直前）。
old = """            if (protocol == ProtocolVersion.V1_9_2) {
                return pack109 != null ? pack109"""
if text.count(old) != 1:
    failures.append("ResourcePackMode: packForProtocol の 109 分岐が見つからない")
else:
    text = text.replace(old, """            if (protocol == ProtocolVersion.V1_9_1) {
                return pack108 != null ? pack108
                        : pack109 != null ? pack109
                        : pack110 != null ? pack110
                        : pack315 != null ? pack315
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
            if (protocol == ProtocolVersion.V1_9_2) {
                return pack109 != null ? pack109""")

# 4. decide() 完全版：シグネチャへ 108 ペアを追加し、旧シグネチャの
#    互換オーバーロードを直前に挿入する。
sig_re = re.compile(
    r"public static Decision decide\(String configuredUrl, String configuredSha1,"
    r"(?:[^)]*?)boolean required, String promptKey\) \{", re.DOTALL)
candidates = [m for m in sig_re.finditer(text) if "configuredUrl109" in m.group(0)]
if len(candidates) != 1:
    failures.append(f"ResourcePackMode: decide完全版の候補が {len(candidates)} 件")
else:
    m = candidates[0]
    signature = m.group(0)
    names = re.findall(r"String (\w+)", signature)
    if names and names[-1] == "promptKey":
        names = names[:-1]
    delegation_args = ", ".join(names)
    compat = ("/** Backwards-compatible overload without a 1.9.1 pack pair. */\n    "
              + signature + "\n        return decide(" + delegation_args
              + ", \"\", \"\", required, promptKey);\n    }\n\n    ")
    new_signature = signature.replace(
        "String configuredUrl109, String configuredSha1109,",
        "String configuredUrl109, String configuredSha1109,\n"
        "            String configuredUrl108, String configuredSha1099,")
    text = text[:m.start()] + compat + new_signature + text[m.end():]

# 5. decide() 本体：pack108 の宣言・検証・詰め替え。
for old, new, expected in [
    ("            ResourcePack pack109 = null;",
     "            ResourcePack pack109 = null;\n            ResourcePack pack108 = null;", 1),
    ("""                    pack109 = new ResourcePack(url109,
                            configuredSha1109.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""",
     """                    pack109 = new ResourcePack(url109,
                            configuredSha1109.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url108 = configuredUrl108 == null ? "" : configuredUrl108.trim();
            if (!url108.isEmpty()) {
                if (!url108.startsWith("https://")) {
                    problems.add("resource-pack.url-108 must start with https:// (got '"
                            + url108 + "'); 108 clients will be offered the 1.9.2 pack instead");
                } else if (!isValidSha1(configuredSha1099)) {
                    problems.add("resource-pack.sha1-108 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1099
                            + "'; 108 clients will be offered the 1.9.2 pack instead");
                } else {
                    pack108 = new ResourcePack(url108,
                            configuredSha1099.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""", 1),
    ("pack404, pack401, pack393, pack340, pack338, pack335, pack316, pack315, pack210, pack110, pack109);",
     "pack404, pack401, pack393, pack340, pack338, pack335, pack316, pack315, pack210, pack110, pack109, pack108);", 1),
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
    ('    public static final String PACK_PATH_109 = "/detergentmc-pack-1.9.2.zip";',
     '    public static final String PACK_PATH_108 = "/detergentmc-pack-1.9.1.zip";\n'
     '    public static final String PACK_PATH_109 = "/detergentmc-pack-1.9.2.zip";', 1),
    ("""    private final Path pack109File;
    private final String sha1109;""",
     """    private final Path pack108File;
    private final String sha1108;
    private final Path pack109File;
    private final String sha1109;""", 1),
    ("    private byte[] pack109Bytes;",
     "    private byte[] pack108Bytes;\n    private byte[] pack109Bytes;", 1),
    ("""        this.pack109File = null;
        this.sha1109 = null;""",
     """        this.pack108File = null;
        this.sha1108 = null;
        this.pack109File = null;
        this.sha1109 = null;""", 6),
    ("Path pack110File, String sha1110, Path pack109File, String sha1109, Path pack315File,",
     "Path pack110File, String sha1110, Path pack109File, String sha1109, Path pack108File, String sha1108, Path pack315File,", 1),
    ("""            throw new IllegalArgumentException("sha1109 is required when pack109File is given");
        }""",
     """            throw new IllegalArgumentException("sha1109 is required when pack109File is given");
        }
        if (pack108File != null && (sha1108 == null || sha1108.isBlank())) {
            throw new IllegalArgumentException("sha1108 is required when pack108File is given");
        }""", 1),
    ("""        this.pack109File = pack109File;
        this.sha1109 = sha1109;""",
     """        this.pack109File = pack109File;
        this.sha1109 = sha1109;
        this.pack108File = pack108File;
        this.sha1108 = sha1108;""", 1),
    ("""        if (pack109File != null) {
            pack109Bytes = readAndVerify(pack109File, sha1109);
        }""",
     """        if (pack109File != null) {
            pack109Bytes = readAndVerify(pack109File, sha1109);
        }
        if (pack108File != null) {
            pack108Bytes = readAndVerify(pack108File, sha1108);
        }""", 1),
    ("""        if (pack109Bytes != null) {
            httpServer.createContext(PACK_PATH_109, exchange -> serveBytes(exchange, pack109Bytes));
        }""",
     """        if (pack109Bytes != null) {
            httpServer.createContext(PACK_PATH_109, exchange -> serveBytes(exchange, pack109Bytes));
        }
        if (pack108Bytes != null) {
            httpServer.createContext(PACK_PATH_108, exchange -> serveBytes(exchange, pack108Bytes));
        }""", 1),
    ("        pack109Bytes = null;",
     "        pack108Bytes = null;\n        pack109Bytes = null;", 1),
    ("""    /** The download URL of the 1.9.2 variant, or {@code null} when not served. */
    public synchronized String url109() {""",
     """    /** The download URL of the 1.9.1 variant, or {@code null} when not served. */
    public synchronized String url108() {
        if (pack108File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_108;
    }

    /**
     * The 1.9.1 {@link ResourcePack} to offer to 108 clients, or
     * {@code null} when no 1.9.1 variant is served.
     */
    public synchronized ResourcePack pack108() {
        return pack108File == null ? null
                : new ResourcePack(url108(), sha1108, required, promptKey);
    }

    /** The download URL of the 1.9.2 variant, or {@code null} when not served. */
    public synchronized String url109() {""", 1),
    ("""        if (clientProtocol == ProtocolVersion.V1_9_2) {""",
     """        if (clientProtocol == ProtocolVersion.V1_9_1) {
            // 108パックが無い場合は109パック（同じ pack_format 2・
            // 同じ旧レイアウト。説明文だけが 1.9.2 用になる）、
            // さらに無い場合は109分岐と同じ順に代替する。
            ResourcePack pack108 = pack108();
            if (pack108 != null) {
                return pack108;
            }
            return packForProtocol(ProtocolVersion.V1_9_2);
        }
        if (clientProtocol == ProtocolVersion.V1_9_2) {""", 1),
    ("""            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built315,""",
     """            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built108,
            ResourcePackBuilder.BuiltPack built315,""", 1),
    ("built109.file(), built109.sha1(), built315.file(),",
     "built109.file(), built109.sha1(), built108.file(), built108.sha1(), built315.file(),", 1),
    # 総数の文言（フル BuiltPack コンストラクタの javadoc）は
    # patch-108-stage-d2d.py で thirty-eight → thirty-nine に直す。
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
