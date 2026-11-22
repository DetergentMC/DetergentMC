"""RegistryTables210.java（と分割クラス）の検証。

gen-210-tables.py を実行して期待値（JSON）を実データから作り直し、
生成済みの Java ファイル（本体＋ブロック状態の分割クラス＋
RegistryTables210Snd）を読み、区間＋上書きを展開した結果と
全件照合する。あわせて意味対応の代表値（observer→dropper、
シュルカー→chest、totem→golden_apple、iron_nugget→gold_nugget、
亜種の畳み込み、新規モブの pig 寄せ、ブロックエンティティの
旧式名等）も確認する。

使い方：
  python3 tools/verify-210-tables.py [--java-dir DIR]
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
    for cname in re.findall(r"(RegistryTables210\w+)\.V", m.group(1)):
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

    subprocess.run([sys.executable, str(TOOLS / "gen-210-tables.py"),
                    "--out", "/tmp/RegistryTables210.java",
                    "--json", "/tmp/tables-210-expected.json"],
                   check=True, capture_output=True, text=True)
    expected = load("/tmp/tables-210-expected.json")

    text = (java_dir / "RegistryTables210.java").read_text(encoding="utf-8")
    snd_text = (java_dir / "RegistryTables210Snd.java").read_text(
        encoding="utf-8")

    pig758 = expected["ENTITY_TYPE"]["rev"][90]
    stone_break758 = expected["SOUND"]["rev"][375]
    plains758 = expected["BIOME"]["rev"][1]
    fallbacks_fwd = {"BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": 90,
                     "SOUND": 375, "PARTICLE": 0, "BLOCK": 1,
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
            fo = parse_concat_table(text, "BLOCK_STATE_210_OVERRIDES",
                                    java_dir)
            ro = parse_concat_table(text, "BLOCK_STATE_210_TO_758_OVERRIDES",
                                    java_dir)
        else:
            fo = parse_table(text, f"{name}_210_OVERRIDES")
            ro = parse_table(text, f"{name}_210_TO_758_OVERRIDES")
        fr = parse_table(text, f"{name}_210_RANGES")
        rr = parse_table(text, f"{name}_210_TO_758_RANGES")
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
    exp_fwd = load(TOOLS / "sound-names-758-to-210.json")
    exp_rev = load(TOOLS / "sound-names-210-to-758.json")
    if parse_pairs(snd_text, "FWD") != exp_fwd:
        problems.append("Snd FWD が sound-names-758-to-210.json と不一致")
    if parse_pairs(snd_text, "REV") != exp_rev:
        problems.append("Snd REV が sound-names-210-to-758.json と不一致")
    print(f"音の名前フォールバック: 758→210 {len(exp_fwd)} 対、"
          f"210→758 {len(exp_rev)} 対")

    # ブロックエンティティ名・バイオーム名
    be_legacy = load(TOOLS / "block-entities-210.json")
    if parse_string_array(text, "BLOCK_ENTITY_LEGACY_NAMES_210") != be_legacy:
        problems.append("BLOCK_ENTITY_LEGACY_NAMES_210 が抽出結果と不一致")
    be_names = parse_string_array(text, "BLOCK_ENTITY_NAMES_210")
    if len(be_names) != 23 or be_names[0] != "furnace" \
            or "note_block" not in be_names or "shulker_box" in be_names:
        problems.append(f"BLOCK_ENTITY_NAMES_210 が想定と違う: {be_names}")
    text316 = (java_dir / "RegistryTables316.java").read_text(encoding="utf-8")
    if parse_string_array(text, "BIOME_NAMES_210") \
            != parse_string_array(text316, "BIOME_NAMES_316"):
        problems.append("BIOME_NAMES_210 が 316 と不一致")

    # ---- 意味対応の代表値 ----
    T = {k: (v["fwd"], v["rev"]) for k, v in expected.items()}

    def check(desc, got, want):
        if got != want:
            problems.append(f"代表値 {desc}: {got} != {want}")

    m18 = load(Path.home() / "workspace" / "mcdata404" / "mapping-1.18.json")
    item758 = {n: i for i, n in enumerate(m18["items"])}
    ent758 = {n: i for i, n in enumerate(m18["entities"])}
    snd758 = {n: i for i, n in enumerate(m18["sounds"])}
    part758 = {n: i for i, n in enumerate(m18["particles"])}
    # observer の状態（facing north の最小状態を 316 表から引く）
    t316 = load("/tmp/tables-316.json") \
        if Path("/tmp/tables-316.json").exists() else None
    bs_f, bs_r = T["BLOCK_STATE"]
    # 758 の observer 状態は 316 表で (218<<4)|meta へ行くもの
    if t316:
        obs_states = [s for s, v in enumerate(t316["BLOCK_STATE"]["fwd"])
                      if v >> 4 == 218]
        for s in obs_states:
            meta = t316["BLOCK_STATE"]["fwd"][s] & 15
            check(f"observer 状態 {s}", bs_f[s], (158 << 4) | meta)
        shk_states = [s for s, v in enumerate(t316["BLOCK_STATE"]["fwd"])
                      if 219 <= (v >> 4) <= 234]
        for s in shk_states:
            meta = t316["BLOCK_STATE"]["fwd"][s] & 15
            want = (54 << 4) | (2 if meta < 2 else meta)
            check(f"shulker 状態 {s}", bs_f[s], want)
    for i, name in enumerate(m18["blockstates"]):
        if name.startswith("shulker_box["):
            check(f"無色シュルカー状態 {i}→chest", bs_f[i], (54 << 4) | 2)
    check("rev で 218 は石（758）", bs_r[218 << 4], bs_r[1 << 4])
    it_f, it_r = T["ITEM"]
    check("totem_of_undying→golden_apple", it_f[item758["totem_of_undying"]], 322)
    check("shulker_shell→prismarine_shard", it_f[item758["shulker_shell"]], 409)
    check("iron_nugget→gold_nugget", it_f[item758["iron_nugget"]], 371)
    check("observer アイテム→dropper", it_f[item758["observer"]], 158)
    check("shulker_box アイテム→chest", it_f[item758["shulker_box"]], 54)
    check("rev 449 はフォールバック", it_r[449], 1)
    en_f, en_r = T["ENTITY_TYPE"]
    check("husk→zombie", en_f[ent758["husk"]], 54)
    check("stray→skeleton", en_f[ent758["stray"]], 51)
    check("donkey→horse", en_f[ent758["donkey"]], 100)
    check("elder_guardian→guardian", en_f[ent758["elder_guardian"]], 68)
    check("evoker→pig", en_f[ent758["evoker"]], 90)
    check("vex→pig", en_f[ent758["vex"]], 90)
    check("llama→pig", en_f[ent758["llama"]], 90)
    check("polar_bear 同一", en_f[ent758["polar_bear"]], 102)
    check("rev polar_bear", en_r[102], ent758["polar_bear"])
    so_f, so_r = T["SOUND"]
    check("evocation_illager 音→汎用", so_f[snd758["entity.evoker.ambient"]], 375)
    check("totem 音→汎用", so_f[snd758["item.totem.use"]], 375)
    check("rev experience_orb.touch→pickup",
          so_r[load(TOOLS / "sounds-210.json").index(
              "entity.experience_orb.touch")],
          snd758["entity.experience_orb.pickup"])
    pa_f, pa_r = T["PARTICLE"]
    check("totem パーティクル→explode", pa_f[part758["totem_of_undying"]], 0)
    check("spit パーティクル→explode", pa_f[part758["spit"]], 0)
    be_f, be_r = T["BLOCK_ENTITY"]
    check("shulker_box BE→DROP", be_f[22], -1)
    check("bed BE→DROP", be_f[23], -1)
    check("furnace BE 同一", be_f[0], 0)
    check("rev note_block はフォールバック0", be_r[be_names.index("note_block")], 0)

    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("OK: RegistryTables210 は期待値と全件一致し、代表値も正しい。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
