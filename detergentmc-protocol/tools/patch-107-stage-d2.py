#!/usr/bin/env python3
"""107 Stage D2 の配線を既存ファイルへ適用する（108 の D2 と同型）。

対象：ResourcePackBuilder、ResourcePackMode、ResourcePackServer。
107 のパックは 108 と同じ旧レイアウトで、pack_format は 2。
ファイル名と pack.mcmeta の説明文だけが違う。フォールバック順は
107 → 108 → 109 → 110 → 210 → 315 → 316 → 335 → …。
各置換は期待件数を検証し、合わなければ失敗として報告する。
ResourcePackServer の委譲コンストラクタへの null 挿入は
patch-107-stage-d2c.py で行う。
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
    ('    public static final String PACK_FILE_NAME_108 = "detergentmc-pack-1.9.1.zip";',
     '    public static final String PACK_FILE_NAME_107 = "detergentmc-pack-1.9.zip";\n'
     '    public static final String PACK_FILE_NAME_108 = "detergentmc-pack-1.9.1.zip";', 1),
    ("""    private static final String PACK_MCMETA_108 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9.1)\\"}}";""",
     """    private static final String PACK_MCMETA_107 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9)\\"}}";

    private static final String PACK_MCMETA_108 = "{\\"pack\\":{\\"pack_format\\":2,"
            + "\\"description\\":\\"DetergentMC bubble pack (1.9.1)\\"}}";""", 1),
    ("            case ProtocolVersion.V1_9_1 -> PACK_FORMAT_1_9_4;",
     "            case ProtocolVersion.V1_9 -> PACK_FORMAT_1_9_4;\n"
     "            case ProtocolVersion.V1_9_1 -> PACK_FORMAT_1_9_4;", 1),
    ("""    public BuiltPack build108(Path outputDir) throws IOException {""",
     """    public BuiltPack build107(Path outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir is required");
        }
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(PACK_FILE_NAME_107);
        writePack107(file);
        return new BuiltPack(file, sha1Hex(file));
    }

    public BuiltPack build108(Path outputDir) throws IOException {""", 1),
    ("""    public void writePack108(Path file) throws IOException {""",
     """    public void writePack107(Path file) throws IOException {
        writePack(file, PACK_FORMAT_1_9_4, PACK_MCMETA_107);
    }

    public void writePack108(Path file) throws IOException {""", 1),
])

# ---------- ResourcePackMode ----------
mode_path = PROTO / "ResourcePackMode.java"
text = mode_path.read_text(encoding="utf-8")

# 1. レコードのコンポーネントに pack107 を追加。
old = ("            ResourcePack pack110, ResourcePack pack109,\n"
       "            ResourcePack pack108) {")
if text.count(old) != 1:
    failures.append("ResourcePackMode: レコード定義が見つからない")
else:
    text = text.replace(old,
                        "            ResourcePack pack110, ResourcePack pack109,\n"
                        "            ResourcePack pack108, ResourcePack pack107) {")

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

# 3. packForProtocol に 107 分岐を追加（108 分岐の直前）。
old = """            if (protocol == ProtocolVersion.V1_9_1) {
                return pack108 != null ? pack108"""
if text.count(old) != 1:
    failures.append("ResourcePackMode: packForProtocol の 108 分岐が見つからない")
else:
    text = text.replace(old, """            if (protocol == ProtocolVersion.V1_9) {
                return pack107 != null ? pack107
                        : pack108 != null ? pack108
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
            if (protocol == ProtocolVersion.V1_9_1) {
                return pack108 != null ? pack108""")

# 4. decide() 完全版：シグネチャへ 107 ペアを追加し、旧シグネチャの
#    互換オーバーロードを直前に挿入する。
sig_re = re.compile(
    r"public static Decision decide\(String configuredUrl, String configuredSha1,"
    r"(?:[^)]*?)boolean required, String promptKey\) \{", re.DOTALL)
candidates = [m for m in sig_re.finditer(text) if "configuredUrl108" in m.group(0)]
if len(candidates) != 1:
    failures.append(f"ResourcePackMode: decide完全版の候補が {len(candidates)} 件")
else:
    m = candidates[0]
    signature = m.group(0)
    names = re.findall(r"String (\w+)", signature)
    if names and names[-1] == "promptKey":
        names = names[:-1]
    delegation_args = ", ".join(names)
    compat = ("/** Backwards-compatible overload without a 1.9 pack pair. */\n    "
              + signature + "\n        return decide(" + delegation_args
              + ", \"\", \"\", required, promptKey);\n    }\n\n    ")
    new_signature = signature.replace(
        "String configuredUrl108, String configuredSha1108,",
        "String configuredUrl108, String configuredSha1108,\n"
        "            String configuredUrl107, String configuredSha1107,")
    text = text[:m.start()] + compat + new_signature + text[m.end():]

# 5. decide() 本体：pack107 の宣言・検証・詰め替え。
for old, new, expected in [
    ("            ResourcePack pack108 = null;",
     "            ResourcePack pack108 = null;\n            ResourcePack pack107 = null;", 1),
    ("""                    pack108 = new ResourcePack(url108,
                            configuredSha1108.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""",
     """                    pack108 = new ResourcePack(url108,
                            configuredSha1108.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
            String url107 = configuredUrl107 == null ? "" : configuredUrl107.trim();
            if (!url107.isEmpty()) {
                if (!url107.startsWith("https://")) {
                    problems.add("resource-pack.url-107 must start with https:// (got '"
                            + url107 + "'); 107 clients will be offered the 1.9.1 pack instead");
                } else if (!isValidSha1(configuredSha1107)) {
                    problems.add("resource-pack.sha1-107 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1107
                            + "'; 107 clients will be offered the 1.9.1 pack instead");
                } else {
                    pack107 = new ResourcePack(url107,
                            configuredSha1107.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }""", 1),
    ("pack404, pack401, pack393, pack340, pack338, pack335, pack316, pack315, pack210, pack110, pack109, pack108);",
     "pack404, pack401, pack393, pack340, pack338, pack335, pack316, pack315, pack210, pack110, pack109, pack108, pack107);", 1),
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
    ('    public static final String PACK_PATH_108 = "/detergentmc-pack-1.9.1.zip";',
     '    public static final String PACK_PATH_107 = "/detergentmc-pack-1.9.zip";\n'
     '    public static final String PACK_PATH_108 = "/detergentmc-pack-1.9.1.zip";', 1),
    ("""    private final Path pack108File;
    private final String sha1108;""",
     """    private final Path pack107File;
    private final String sha1107;
    private final Path pack108File;
    private final String sha1108;""", 1),
    ("    private byte[] pack108Bytes;",
     "    private byte[] pack107Bytes;\n    private byte[] pack108Bytes;", 1),
    ("""        this.pack108File = null;
        this.sha1108 = null;""",
     """        this.pack107File = null;
        this.sha1107 = null;
        this.pack108File = null;
        this.sha1108 = null;""", 6),
    ("Path pack109File, String sha1109, Path pack108File, String sha1108, Path pack315File,",
     "Path pack109File, String sha1109, Path pack108File, String sha1108, Path pack107File, String sha1107, Path pack315File,", 1),
    ("""            throw new IllegalArgumentException("sha1108 is required when pack108File is given");
        }""",
     """            throw new IllegalArgumentException("sha1108 is required when pack108File is given");
        }
        if (pack107File != null && (sha1107 == null || sha1107.isBlank())) {
            throw new IllegalArgumentException("sha1107 is required when pack107File is given");
        }""", 1),
    ("""        this.pack108File = pack108File;
        this.sha1108 = sha1108;""",
     """        this.pack108File = pack108File;
        this.sha1108 = sha1108;
        this.pack107File = pack107File;
        this.sha1107 = sha1107;""", 1),
    ("""        if (pack108File != null) {
            pack108Bytes = readAndVerify(pack108File, sha1108);
        }""",
     """        if (pack108File != null) {
            pack108Bytes = readAndVerify(pack108File, sha1108);
        }
        if (pack107File != null) {
            pack107Bytes = readAndVerify(pack107File, sha1107);
        }""", 1),
    ("""        if (pack108Bytes != null) {
            httpServer.createContext(PACK_PATH_108, exchange -> serveBytes(exchange, pack108Bytes));
        }""",
     """        if (pack108Bytes != null) {
            httpServer.createContext(PACK_PATH_108, exchange -> serveBytes(exchange, pack108Bytes));
        }
        if (pack107Bytes != null) {
            httpServer.createContext(PACK_PATH_107, exchange -> serveBytes(exchange, pack107Bytes));
        }""", 1),
    ("        pack108Bytes = null;",
     "        pack107Bytes = null;\n        pack108Bytes = null;", 1),
    ("""    /** The download URL of the 1.9.1 variant, or {@code null} when not served. */
    public synchronized String url108() {""",
     """    /** The download URL of the 1.9 variant, or {@code null} when not served. */
    public synchronized String url107() {
        if (pack107File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_107;
    }

    /**
     * The 1.9 {@link ResourcePack} to offer to 107 clients, or
     * {@code null} when no 1.9 variant is served.
     */
    public synchronized ResourcePack pack107() {
        return pack107File == null ? null
                : new ResourcePack(url107(), sha1107, required, promptKey);
    }

    /** The download URL of the 1.9.1 variant, or {@code null} when not served. */
    public synchronized String url108() {""", 1),
    ("""        if (clientProtocol == ProtocolVersion.V1_9_1) {""",
     """        if (clientProtocol == ProtocolVersion.V1_9) {
            // 107パックが無い場合は108パック（同じ pack_format 2・
            // 同じ旧レイアウト。説明文だけが 1.9.1 用になる）、
            // さらに無い場合は108分岐と同じ順に代替する。
            ResourcePack pack107 = pack107();
            if (pack107 != null) {
                return pack107;
            }
            return packForProtocol(ProtocolVersion.V1_9_1);
        }
        if (clientProtocol == ProtocolVersion.V1_9_1) {""", 1),
    ("""            ResourcePackBuilder.BuiltPack built108,
            ResourcePackBuilder.BuiltPack built315,""",
     """            ResourcePackBuilder.BuiltPack built108,
            ResourcePackBuilder.BuiltPack built107,
            ResourcePackBuilder.BuiltPack built315,""", 1),
    ("built108.file(), built108.sha1(), built315.file(),",
     "built108.file(), built108.sha1(), built107.file(), built107.sha1(), built315.file(),", 1),
    # 総数の文言（フル BuiltPack コンストラクタの javadoc）は
    # patch-107-stage-d2d.py で thirty-nine → forty に直す。
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
