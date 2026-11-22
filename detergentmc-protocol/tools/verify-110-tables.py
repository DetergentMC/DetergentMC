"""RegistryTables110.java（と分割クラス）の検証。

gen-110-tables.py を実行して期待値（JSON）を実データから作り直し、
生成済みの Java ファイル（本体＋ブロック状態の分割クラス＋
RegistryTables110Snd）を読み、区間＋上書きを展開した結果と
全件照合する。あわせて意味対応の代表値（magma→netherrack、
nether_wart_block／red_nether_brick→nether_brick、bone_block→
quartz_block、structure_void／structure_block→barrier、
polar_bear→pig、fallingdust→explode、1.10 追加音→汎用音、
ブロックエンティティの旧式名等）も確認する。

使い方：
  python3 tools/verify-110-tables.py [--java-dir DIR]
  --java-dir を省略すると src/main/java/io/detergentmc/protocol を使う。
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent


def parse_table(text, name):
    m = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"表が見つからない: {name}")
    return [tuple(int(x) for x in e.split(","))
            for e in re.findall(r"\{([^{}]+)\}", m.group(1))]


def parse_concat_table(text, name, java_dir):
    m = re.search(re.escape(name) + r"\s*=\s*concat\(([^)]+)\);", text)
    if not m:
        raise SystemExit(f"concat 表が見つからない: {name}")
    out = []
    for cname in re.findall(r"(RegistryTables110\w+)\.V", m.group(1)):
        ctext = (java_dir / f"{cname}.java").read_text(encoding="utf-8")
        out.extend(parse_table(ctext, "V"))
    return out


def parse_string_array(text, name):
    m = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"文字列配列が見つからない: {name}")
    return re.findall(r'"([^"]*)"', m.group(1))


def parse_pairs(text, name):
    m = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"対の表が見つからない: {name}")
    return [list(p) for p in re.findall(r'\{"([^"]*)", "([^"]*)"\}', m.group(1))]


def expand(size, ranges, overrides, fallback):
    out = [fallback] * size
    for a, b, d in ranges:
        for i in range(a, b + 1):
            out[i] = i + d
    for a, v in overrides:
        out[a] = v
    return out


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--java-dir", type=Path,
                    default=TOOLS.parent / "src" / "main" / "java"
                    / "io" / "detergentmc" / "protocol")
    args = ap.parse_args()
    java_dir = args.java_dir

    subprocess.run([sys.executable, str(TOOLS / "gen-110-tables.py"),
                    "--out", "/tmp/RegistryTables110.java",
                    "--json", "/tmp/tables-110-expected.json"],
                   check=True, capture_output=True, text=True)
    expected = load("/tmp/tables-110-expected.json")
    subprocess.run([sys.executable, str(TOOLS / "gen-210-tables.py"),
                    "--out", "/tmp/RegistryTables210.java",
                    "--json", "/tmp/tables-210-expected.json"],
                   check=True, capture_output=True, text=True)
    expected210 = load("/tmp/tables-210-expected.json")

    text = (java_dir / "RegistryTables110.java").read_text(encoding="utf-8")
    snd_text = (java_dir / "RegistryTables110Snd.java").read_text(
        encoding="utf-8")

    pig758 = expected["ENTITY_TYPE"]["rev"][90]
    stone_break758 = expected["SOUND"]["rev"][364]
    plains758 = expected["BIOME"]["rev"][1]
    fallbacks_fwd = {"BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": 90,
                     "SOUND": 364, "PARTICLE": 0, "BLOCK": 1,
                     "BLOCK_ENTITY": -1, "BIOME": 1, "DAMAGE_TYPE": 16}
    fallbacks_rev = {"BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": pig758,
                     "SOUND": stone_break758, "PARTICLE": 0, "BLOCK": 1,
                     "BLOCK_ENTITY": -1, "BIOME": plains758,
                     "DAMAGE_TYPE": 16}

    problems = []
    total = 0
    for name, t in expected.items():
        ef, er = t["fwd"], t["rev"]
        if name == "BLOCK_STATE":
            fo = parse_concat_table(text, "BLOCK_STATE_110_OVERRIDES",
                                    java_dir)
            ro = parse_concat_table(text, "BLOCK_STATE_110_TO_758_OVERRIDES",
                                    java_dir)
        else:
            fo = parse_table(text, f"{name}_110_OVERRIDES")
            ro = parse_table(text, f"{name}_110_TO_758_OVERRIDES")
        fr = parse_table(text, f"{name}_110_RANGES")
        rr = parse_table(text, f"{name}_110_TO_758_RANGES")
        gf = expand(len(ef), fr, fo, fallbacks_fwd[name])
        gr = expand(len(er), rr, ro, fallbacks_rev[name])
        total += len(ef) + len(er)
        if gf != ef:
            bad = [i for i in range(len(ef)) if gf[i] != ef[i]][:5]
            problems.append(f"{name} fwd 不一致（例: {bad}）")
        if gr != er:
            bad = [i for i in range(len(er)) if gr[i] != er[i]][:5]
            problems.append(f"{name} rev 不一致（例: {bad}）")
    print(f"数値表の全件照合: {total} 件")

    # 音の名前フォールバック
    exp_fwd = load(TOOLS / "sound-names-758-to-110.json")
    exp_rev = load(TOOLS / "sound-names-110-to-758.json")
    if parse_pairs(snd_text, "FWD") != exp_fwd:
        problems.append("Snd FWD が sound-names-758-to-110.json と不一致")
    if parse_pairs(snd_text, "REV") != exp_rev:
        problems.append("Snd REV が sound-names-110-to-758.json と不一致")
    print(f"音の名前フォールバック: 758→110 {len(exp_fwd)} 対、"
          f"110→758 {len(exp_rev)} 対")

    # ブロックエンティティ名・バイオーム名
    be_legacy = load(TOOLS / "block-entities-110.json")
    if parse_string_array(text, "BLOCK_ENTITY_LEGACY_NAMES_110") != be_legacy:
        problems.append("BLOCK_ENTITY_LEGACY_NAMES_110 が抽出結果と不一致")
    text210 = (java_dir / "RegistryTables210.java").read_text(encoding="utf-8")
    if parse_string_array(text, "BLOCK_ENTITY_NAMES_110") \
            != parse_string_array(text210, "BLOCK_ENTITY_NAMES_210"):
        problems.append("BLOCK_ENTITY_NAMES_110 が 210 と不一致")
    text316 = (java_dir / "RegistryTables316.java").read_text(encoding="utf-8")
    if parse_string_array(text, "BIOME_NAMES_110") \
            != parse_string_array(text316, "BIOME_NAMES_316"):
        problems.append("BIOME_NAMES_110 が 316 と不一致")

    # ---- 意味対応の代表値 ----
    T = {k: (v["fwd"], v["rev"]) for k, v in expected.items()}
    T210 = {k: (v["fwd"], v["rev"]) for k, v in expected210.items()}

    def check(desc, got, want):
        if got != want:
            problems.append(f"代表値 {desc}: {got} != {want}")

    m18 = load(Path.home() / "workspace" / "mcdata404" / "mapping-1.18.json")
    item758 = {n: i for i, n in enumerate(m18["items"])}
    ent758 = {n: i for i, n in enumerate(m18["entities"])}
    snd758 = {n: i for i, n in enumerate(m18["sounds"])}
    part758 = {n: i for i, n in enumerate(m18["particles"])}

    bs_f, bs_r = T["BLOCK_STATE"]
    bs210_f = T210["BLOCK_STATE"][0]
    approx = {213: 87, 214: 112, 215: 112, 216: 155, 217: 166}
    for s, v210 in enumerate(bs210_f):
        block, meta = v210 >> 4, v210 & 15
        if block in approx:
            check(f"ブロック {block} の状態 {s}", bs_f[s],
                  (approx[block] << 4) | meta)
    for b in approx:
        check(f"rev で {b} は石（758）", bs_r[b << 4], bs_r[1 << 4])

    it_f, it_r = T["ITEM"]
    check("magma_block アイテム→netherrack", it_f[item758["magma_block"]], 87)
    check("nether_wart_block アイテム→nether_brick",
          it_f[item758["nether_wart_block"]], 112)
    check("red_nether_bricks アイテム→nether_brick",
          it_f[item758["red_nether_bricks"]], 112)
    check("bone_block アイテム→quartz_block", it_f[item758["bone_block"]], 155)
    check("structure_void アイテム→barrier", it_f[item758["structure_void"]], 166)
    check("structure_block アイテム→barrier", it_f[item758["structure_block"]], 166)
    check("rev 255 はフォールバック（石アイテム）", it_r[255], it_r[1])

    en_f, en_r = T["ENTITY_TYPE"]
    check("polar_bear→pig", en_f[ent758["polar_bear"]], 90)
    check("rev 102 は pig（758）", en_r[102], pig758)
    check("pig は同一", en_f[ent758["pig"]], 90)

    so_f, so_r = T["SOUND"]
    check("1.10 追加音 polar_bear.ambient→汎用音",
          so_f[snd758["entity.polar_bear.ambient"]], 364)
    check("block.stone.break の rev", so_r[364], snd758["block.stone.break"])

    pa_f, pa_r = T["PARTICLE"]
    check("falling_dust→explode", pa_f[part758["falling_dust"]], 0)

    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("検証 OK：数値表・名前表・代表値すべて一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
