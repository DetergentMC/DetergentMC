#!/usr/bin/env python3
"""109 Stage D2 の配線を既存ファイルへ適用する（110 の D2 と同型）。

対象：ResourcePackBuilder、ResourcePackMode、ResourcePackServer。
109 のパックは 110 と同じ旧レイアウトで、pack_format は 2。
ファイル名と pack.mcmeta の説明文だけが違う。フォールバック順は
109 → 110 → 315 → 316 → 335 → …。
各置換は期待件数を検証し、合わなければ失敗として報告する。
ResourcePackServer の委譲コンストラクタへの null 挿入は
patch-109-stage-d2c.py で行う。
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
    ("    public static final int PACK_FORMAT_1_10_2 = 2;",
     "    public static final int PACK_FORMAT_1_9_4 = 2;\n"
     "    public static final int PACK_FORMAT_1_10_2 = 2;", 1),
    ('    public static final String PACK_FILE_NAME_110 = "detergentmc-pack-1.9.4.zip";',
     '    public static final String PACK_FILE_NAME_109 = "detergentmc-pack-1.9.2.zip";\n'
     '    public static final String PACK_FILE_NAME_110 = "detergentmc-pack-1.9.4.zip";', 1),
    ("""    private static final String PACK_MCMETA_110 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9.4)\\"}}";""",
     """    private static final String PACK_MCMETA_109 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9.2)\\"}}";

    private static final String PACK_MCMETA_110 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9.4)\\"}}";""", 1),
    ("            case ProtocolVersion.V1_9_4 -> PACK_FORMAT_1_10_2;",
     "            case ProtocolVersion.V1_9_2 -> PACK_FORMAT_1_9_4;\n"
     "            case ProtocolVersion.V1_9_4 -> PACK_FORMAT_1_10_2;", 1),
    ("""    public BuiltPack build110(Path outputDir) throws IOException {""",
     """    public BuiltPack build109(Path outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir is required");
        }
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(PACK_FILE_NAME_109);
        writePack109(file);
        return new BuiltPack(file, sha1Hex(file));
    }

    public BuiltPack build110(Path outputDir) throws IOException {""", 1),
    ("""    public void writePack110(Path file) throws IOException {""",
     """    public void writePack109(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_9_4, PACK_MCMETA_109);
    }

    public void writePack110(Path file) throws IOException {""", 1),
])

# ---------- ResourcePackMode ----------
mode_path = PROTO / "ResourcePackMode.java"
text = mode_path.read_text(encoding="utf-8")

# 1. レコードのコンポーネントに pack109 を追加。
old = ("            ResourcePack pack315, ResourcePack pack110) {")
if text.count(old) != 1:
    failures.append("ResourcePackMode: レコード定義が見つからない")
else:
    text = text.replace(old,
                        "            ResourcePack pack315, ResourcePack pack110,\n"
                        "            ResourcePack pack109) {")

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

# 3. packForProtocol に 109 分岐を追加（110 分岐の直前）。
old = """            if (protocol == ProtocolVersion.V1_9_4) {
                return pack110 != null ? pack110"""
if text.count(old) != 1:
    failures.append("ResourcePackMode: packForProtocol の 110 分岐が見つからない")
else:
    text = text.replace(old, """            if (protocol == ProtocolVersion.V1_9_2) {
                return pack109 != null ? pack109
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
            if (protocol == ProtocolVersion.V1_9_4) {
                return pack110 != null ? pack110""")

# 4. decide() 完全版：シグネチャへ 109 ペアを追加し、旧シグネチャの
#    互換オーバーロードを直前に挿入する。
sig_re = re.compile(
    r"public static Decision decide\(String configuredUrl, String configuredSha1,"
    r"(?:[^)]*?)boolean required, String promptKey\) \{", re.DOTALL)
candidates = [m for m in sig_re.finditer(text) if "configuredUrl110" in m.group(0)]
if len(candidates) != 1:
    failures.append(f"ResourcePackMode: decide完全版の候補が {len(candidates)} 件")
else:
    m = candidates[0]
    signature = m.group(0)
    names = re.findall(r"String (\w+)", signature)
    if names and names[-1] == "promptKey":
        names = names[:-1]
    delegation_args = ", ".join(names)
    compat = ("/** Backwards-compatible overload without a 1.9.2 pack pair. */\n    "
              + signature + "\n        return decide(" + delegation_args
              + ", \"\", \"\", required, promptKey);\n    }\n\n    ")
    new_signature = signature.replace(
        "String configuredUrl110, String configuredSha1110,",
        "String configuredUrl110, String configuredSha1110,\n"
        "            String configuredUrl109, String configuredSha1109,")
    text = text[:m.start()] + compat + new_signature + text[m.end():]

# 5. decide() 本体：pack109 の宣言・検証・詰め替え。
for old, new, expected in [
    ("            ResourcePack pack110 = null;",
     "            ResourcePack pack110 = null;\n            ResourcePack pack109 = null;", 1),
    ("""                    pack110 = new ResourcePack(url110,
                            configuredSha1110.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""",
     """                    pack110 = new ResourcePack(url110,
                            configuredSha1110.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url109 = configuredUrl109 == null ? "" : configuredUrl109.trim();
            if (!url109.isEmpty()) {
                if (!url109.startsWith("https://")) {
                    problems.add("resource-pack.url-109 must start with https:// (got '"
                            + url109 + "'); 109 clients will be offered the 1.9.4 pack instead");
                } else if (!isValidSha1(configuredSha1109)) {
                    problems.add("resource-pack.sha1-109 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1109
                            + "'; 109 clients will be offered the 1.9.4 pack instead");
                } else {
                    pack109 = new ResourcePack(url109,
                            configuredSha1109.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""", 1),
    ("pack404, pack401, pack393, pack340, pack338, pack335, pack316, pack315, pack110);",
     "pack404, pack401, pack393, pack340, pack338, pack335, pack316, pack315, pack110, pack109);", 1),
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
    ('    public static final String PACK_PATH_110 = "/detergentmc-pack-1.9.4.zip";',
     '    public static final String PACK_PATH_109 = "/detergentmc-pack-1.9.2.zip";\n'
     '    public static final String PACK_PATH_110 = "/detergentmc-pack-1.9.4.zip";', 1),
    ("""    private final Path pack110File;
    private final String sha1110;""",
     """    private final Path pack109File;
    private final String sha1109;
    private final Path pack110File;
    private final String sha1110;""", 1),
    ("    private byte[] pack110Bytes;",
     "    private byte[] pack109Bytes;\n    private byte[] pack110Bytes;", 1),
    ("""        this.pack110File = null;
        this.sha1110 = null;""",
     """        this.pack109File = null;
        this.sha1109 = null;
        this.pack110File = null;
        this.sha1110 = null;""", 6),
    ("Path pack316File, String sha1316, Path pack110File, String sha1110, Path pack315File,",
     "Path pack316File, String sha1316, Path pack110File, String sha1110, Path pack109File, String sha1109, Path pack315File,", 1),
    ("""            throw new IllegalArgumentException("sha1110 is required when pack110File is given");
        }""",
     """            throw new IllegalArgumentException("sha1110 is required when pack110File is given");
        }
        if (pack109File != null && (sha1109 == null || sha1109.isBlank())) {
            throw new IllegalArgumentException("sha1109 is required when pack109File is given");
        }""", 1),
    ("""        this.pack110File = pack110File;
        this.sha1110 = sha1110;""",
     """        this.pack110File = pack110File;
        this.sha1110 = sha1110;
        this.pack109File = pack109File;
        this.sha1109 = sha1109;""", 1),
    ("""        if (pack110File != null) {
            pack110Bytes = readAndVerify(pack110File, sha1110);
        }""",
     """        if (pack110File != null) {
            pack110Bytes = readAndVerify(pack110File, sha1110);
        }
        if (pack109File != null) {
            pack109Bytes = readAndVerify(pack109File, sha1109);
        }""", 1),
    ("""        if (pack110Bytes != null) {
            httpServer.createContext(PACK_PATH_110, exchange -> serveBytes(exchange, pack110Bytes));
        }""",
     """        if (pack110Bytes != null) {
            httpServer.createContext(PACK_PATH_110, exchange -> serveBytes(exchange, pack110Bytes));
        }
        if (pack109Bytes != null) {
            httpServer.createContext(PACK_PATH_109, exchange -> serveBytes(exchange, pack109Bytes));
        }""", 1),
    ("        pack110Bytes = null;",
     "        pack109Bytes = null;\n        pack110Bytes = null;", 1),
    ("""    /** The download URL of the 1.9.4 variant, or {@code null} when not served. */
    public synchronized String url110() {""",
     """    /** The download URL of the 1.9.2 variant, or {@code null} when not served. */
    public synchronized String url109() {
        if (pack109File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_109;
    }

    /**
     * The 1.9.2 {@link ResourcePack} to offer to 109 clients, or
     * {@code null} when no 1.9.2 variant is served.
     */
    public synchronized ResourcePack pack109() {
        return pack109File == null ? null
                : new ResourcePack(url109(), sha1109, required, promptKey);
    }

    /** The download URL of the 1.9.4 variant, or {@code null} when not served. */
    public synchronized String url110() {""", 1),
    ("""        if (clientProtocol == ProtocolVersion.V1_9_4) {""",
     """        if (clientProtocol == ProtocolVersion.V1_9_2) {
            // 109パックが無い場合は110パック（同じ pack_format 2・
            // 同じ旧レイアウト。説明文だけが 1.9.4 用になる）、
            // さらに無い場合は110分岐と同じ順に代替する。
            ResourcePack pack109 = pack109();
            if (pack109 != null) {
                return pack109;
            }
            return packForProtocol(ProtocolVersion.V1_9_4);
        }
        if (clientProtocol == ProtocolVersion.V1_9_4) {""", 1),
    ("""            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built315,""",
     """            ResourcePackBuilder.BuiltPack built110,
            ResourcePackBuilder.BuiltPack built109,
            ResourcePackBuilder.BuiltPack built315,""", 1),
    ("built110.file(), built110.sha1(), built315.file(),",
     "built110.file(), built110.sha1(), built109.file(), built109.sha1(), built315.file(),", 1),
    ("serve all thirty-six packs", "serve all thirty-seven packs", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
