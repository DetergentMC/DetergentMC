"""ResourcePackMode / ResourcePackServer に 210 のパックを追加する。

315 の Stage D（patch-315-stage-d2 系）と同型。フォールバック順は
210 → 315 → 316 → 335 → …。ResourcePackServer のフルコンストラクタ
では 210 のペアを 316 の直後・315 の直前に入れ、フルへ直接委譲する
this(...) には引数位置4の直後へ null, null を挿入する（d2c と同じ
判定：引数73個・末尾 promptKey）。

各置換は期待件数を検証する。冪等ではない。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
PROTO = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol"
failures = []


def sub(text, old, new, expect, where):
    n = text.count(old)
    if n != expect:
        failures.append(f"{where}: 期待 {expect} 件のところ {n} 件: {old[:60]!r}")
        return text
    return text.replace(old, new)


# ---------------- ResourcePackMode ----------------
p = PROTO / "ResourcePackMode.java"
t = p.read_text(encoding="utf-8")

# 1. レコード定義へ pack210 を末尾に追加
t = sub(t,
        "            ResourcePack pack338, ResourcePack pack335, ResourcePack pack316,\n"
        "            ResourcePack pack315) {",
        "            ResourcePack pack338, ResourcePack pack335, ResourcePack pack316,\n"
        "            ResourcePack pack315, ResourcePack pack210) {",
        1, "mode record")

# 2. 互換コンストラクタの委譲17件へ末尾 null を追加
lines = t.split("\n")
count = 0
for i, ln in enumerate(lines):
    if ln.lstrip().startswith("this(kind, pack, problem") and ln.rstrip().endswith(");"):
        lines[i] = ln.rstrip()[:-2] + ", null);"
        count += 1
if count != 17:
    failures.append(f"mode delegations: 期待 17 件のところ {count} 件")
t = "\n".join(lines)

# 3. 「210 無し」互換コンストラクタを「315 無し」の直後に追加
anchor = """        /** Backwards-compatible constructor without a 1.11 pack. */
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
            this(kind, pack, problem, pack758, pack759, pack760, pack761, pack762, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485, pack480, pack477, pack404, pack401, pack393, pack340, pack338, pack335, pack316, null, null);
        }
"""
# 注：ステップ2で末尾 null が1つ増えているため委譲は ", null, null);" で終わる
if t.count(anchor) != 1:
    failures.append(f"mode compat anchor: {t.count(anchor)} 件")
else:
    new_ctor = anchor + """
        /** Backwards-compatible constructor without a 1.10.2 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack758, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
                ResourcePack pack766, ResourcePack pack767, ResourcePack pack768,
                ResourcePack pack769, ResourcePack pack770, ResourcePack pack771,
                ResourcePack pack772, ResourcePack pack773, ResourcePack pack774,
                ResourcePack pack775, ResourcePack pack776, ResourcePack pack578,
                ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,
                ResourcePack pack490, ResourcePack pack485, ResourcePack pack480, ResourcePack pack477,
                ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,
                ResourcePack pack338, ResourcePack pack335, ResourcePack pack316,
                ResourcePack pack315) {
            this(kind, pack, problem, pack758, pack759, pack760, pack761, pack762, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485, pack480, pack477, pack404, pack401, pack393, pack340, pack338, pack335, pack316, pack315, null);
        }
"""
    t = t.replace(anchor, new_ctor)

# 4. packForProtocol に 210 分岐（チェーンは 315 分岐＋210 先頭）
chain315 = """                return pack315 != null ? pack315
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
"""
chain210 = """                return pack210 != null ? pack210
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
"""
t = sub(t,
        "            if (protocol == ProtocolVersion.V1_11) {\n" + chain315,
        "            if (protocol == ProtocolVersion.V1_10_2) {\n" + chain210
        + "            }\n"
        + "            if (protocol == ProtocolVersion.V1_11) {\n" + chain315,
        1, "mode packForProtocol")

# 5. フル decide の引数に 210 を追加（315 の直後）
t = sub(t,
        "            String configuredUrl315, String configuredSha1315,\n"
        "            boolean required, String promptKey) {",
        "            String configuredUrl315, String configuredSha1315,\n"
        "            String configuredUrl210, String configuredSha1210,\n"
        "            boolean required, String promptKey) {",
        1, "mode decide signature")

# 6. フルへ委譲するオーバーロード（315 スロットが "", ""）へ "", "" を追加
t = sub(t,
        'configuredUrl316, configuredSha1316, "", "", required, promptKey);',
        'configuredUrl316, configuredSha1316, "", "", "", "", required, promptKey);',
        1, "mode decide delegation")

# 7. 本体：pack210 の宣言・外部ブロック・Decision 構築
t = sub(t,
        "            ResourcePack pack315 = null;",
        "            ResourcePack pack315 = null;\n"
        "            ResourcePack pack210 = null;",
        1, "mode pack210 decl")
block315 = """            String url315 = configuredUrl315 == null ? "" : configuredUrl315.trim();
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
            }
"""
block210 = block315 + """            String url210 = configuredUrl210 == null ? "" : configuredUrl210.trim();
            if (!url210.isEmpty()) {
                if (!url210.startsWith("https://")) {
                    problems.add("resource-pack.url-210 must start with https:// (got '"
                            + url210 + "'); 210 clients will be offered the 1.11 pack instead");
                } else if (!isValidSha1(configuredSha1210)) {
                    problems.add("resource-pack.sha1-210 is missing or not a 40-digit hex "
                            + "SHA-1: '" + configuredSha1210
                            + "'; 210 clients will be offered the 1.11 pack instead");
                } else {
                    pack210 = new ResourcePack(url210,
                            configuredSha1210.trim().toLowerCase(Locale.ROOT),
                            required, prompt);
                }
            }
"""
t = sub(t, block315, block210, 1, "mode external block")
t = sub(t,
        "pack340, pack338, pack335, pack316, pack315);",
        "pack340, pack338, pack335, pack316, pack315, pack210);",
        1, "mode decision construction")
p.write_text(t, encoding="utf-8")
print("ResourcePackMode: 適用した")

# ---------------- ResourcePackServer ----------------
p = PROTO / "ResourcePackServer.java"
t = p.read_text(encoding="utf-8")

t = sub(t,
        '    public static final String PACK_PATH_315 = "/detergentmc-pack-1.11.zip";',
        '    public static final String PACK_PATH_210 = "/detergentmc-pack-1.10.2.zip";\n'
        '    public static final String PACK_PATH_315 = "/detergentmc-pack-1.11.zip";',
        1, "server path")
t = sub(t,
        "    private final Path pack315File;\n    private final String sha1315;",
        "    private final Path pack210File;\n    private final String sha1210;\n"
        "    private final Path pack315File;\n    private final String sha1315;",
        1, "server fields")
t = sub(t,
        "    private byte[] pack315Bytes;",
        "    private byte[] pack210Bytes;\n    private byte[] pack315Bytes;",
        1, "server bytes field")
# フルコンストラクタ：引数・検証・代入
t = sub(t,
        "Path pack316File, String sha1316, Path pack315File, String sha1315,",
        "Path pack316File, String sha1316, Path pack210File, String sha1210, Path pack315File, String sha1315,",
        1, "server full ctor params")
t = sub(t,
        """        if (pack315File != null && (sha1315 == null || sha1315.isBlank())) {
            throw new IllegalArgumentException("sha1315 is required when pack315File is given");
        }""",
        """        if (pack210File != null && (sha1210 == null || sha1210.isBlank())) {
            throw new IllegalArgumentException("sha1210 is required when pack210File is given");
        }
        if (pack315File != null && (sha1315 == null || sha1315.isBlank())) {
            throw new IllegalArgumentException("sha1315 is required when pack315File is given");
        }""",
        1, "server full ctor validation")
t = sub(t,
        "        this.pack315File = pack315File;\n        this.sha1315 = sha1315;",
        "        this.pack210File = pack210File;\n        this.sha1210 = sha1210;\n"
        "        this.pack315File = pack315File;\n        this.sha1315 = sha1315;",
        1, "server full ctor assign")
# フィールド直初期化コンストラクタ群（this.pack315File = null の箇所）
n = t.count("        this.pack315File = null;\n        this.sha1315 = null;")
if n == 0:
    failures.append(f"server null-init: 0 件")
else:
    t = t.replace("        this.pack315File = null;\n        this.sha1315 = null;",
                  "        this.pack210File = null;\n        this.sha1210 = null;\n"
                  "        this.pack315File = null;\n        this.sha1315 = null;")
    print(f"server null-init: {n} 件に 210 の初期化を追加")
# start(): 読み込みと Context 登録
t = sub(t,
        """        if (pack315File != null) {
            pack315Bytes = readAndVerify(pack315File, sha1315);
        }""",
        """        if (pack210File != null) {
            pack210Bytes = readAndVerify(pack210File, sha1210);
        }
        if (pack315File != null) {
            pack315Bytes = readAndVerify(pack315File, sha1315);
        }""",
        1, "server start load")
t = sub(t,
        """        if (pack315Bytes != null) {
            httpServer.createContext(PACK_PATH_315, exchange -> serveBytes(exchange, pack315Bytes));
        }""",
        """        if (pack210Bytes != null) {
            httpServer.createContext(PACK_PATH_210, exchange -> serveBytes(exchange, pack210Bytes));
        }
        if (pack315Bytes != null) {
            httpServer.createContext(PACK_PATH_315, exchange -> serveBytes(exchange, pack315Bytes));
        }""",
        1, "server start context")
# stop()
t = sub(t,
        "        pack315Bytes = null;",
        "        pack210Bytes = null;\n        pack315Bytes = null;",
        1, "server stop")
# url210 / pack210
t = sub(t,
        """    /** The download URL of the 1.11 variant, or {@code null} when not served. */
    public synchronized String url315() {""",
        """    /** The download URL of the 1.10.2 variant, or {@code null} when not served. */
    public synchronized String url210() {
        if (pack210File == null) {
            return null;
        }
        int port = httpServer != null ? httpServer.getAddress().getPort() : requestedPort;
        return "http://127.0.0.1:" + port + PACK_PATH_210;
    }

    /**
     * The 1.10.2 {@link ResourcePack} to offer to 210 clients, or
     * {@code null} when no 1.10.2 variant is served.
     */
    public synchronized ResourcePack pack210() {
        return pack210File == null ? null
                : new ResourcePack(url210(), sha1210, required, promptKey);
    }

    /** The download URL of the 1.11 variant, or {@code null} when not served. */
    public synchronized String url315() {""",
        1, "server url/pack 210")
# packForProtocol に 210 分岐
t = sub(t,
        """        if (clientProtocol == ProtocolVersion.V1_11) {""",
        """        if (clientProtocol == ProtocolVersion.V1_10_2) {
            // 210パックが無い場合は315パック（pack_format は 2 と 3 で
            // 異なるが、旧レイアウトは共通。315 分岐と同じ順に代替する。
            // 本来は形式が違うため 210 専用パックの用意が望ましい）、
            // さらに無い場合は315分岐と同じ順に代替する。
            ResourcePack pack210 = pack210();
            if (pack210 != null) {
                return pack210;
            }
            return packForProtocol(ProtocolVersion.V1_11);
        }
        if (clientProtocol == ProtocolVersion.V1_11) {""",
        1, "server packForProtocol")
p.write_text(t, encoding="utf-8")
print("ResourcePackServer: 適用した")

# フルへ直接委譲する this(...) へ null, null を挿入（引数73個・末尾 promptKey）
FULL_ARGS = 75
TARGET = FULL_ARGS - 2


def split_args(argstr):
    args, depth, cur = [], 0, ""
    for ch in argstr:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            args.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        args.append(cur)
    return args


t = p.read_text(encoding="utf-8")
out = []
i = 0
fixed = 0
while True:
    j = t.find("this(", i)
    if j == -1:
        out.append(t[i:])
        break
    depth = 0
    k = j + len("this(")
    start = k
    while True:
        ch = t[k]
        if ch == "(":
            depth += 1
        elif ch == ")":
            if depth == 0:
                break
            depth -= 1
        k += 1
    argstr = t[start:k]
    args = split_args(argstr)
    if len(args) == TARGET and args[-1].strip() == "promptKey":
        args = args[:4] + [" null", " null"] + args[4:]
        out.append(t[i:start])
        out.append(",".join(args))
        out.append(t[k:k + 2])
        i = k + 2
        fixed += 1
    else:
        out.append(t[i:j + 5])
        i = j + 5
p.write_text("".join(out), encoding="utf-8")
print(f"委譲コンストラクタ {fixed} 件へ null, null を挿入した")
if fixed == 0:
    failures.append("server delegations: 0 件")

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
