"""RegistryTables316.java（と分割クラス）の検証。

gen-316-tables.py を実行して期待値（JSON）を実データから作り直し、
生成済みの Java ファイル（本体＋ブロック状態の分割クラス）を
読んで展開した結果と全件照合する。あわせて 340 の表との合成規則
（近似・据え置き・打ち切り）が期待値どおりか、意味対応の代表値
（コンクリートの色保持、鉄塊の同一通過、ベッドの打ち切り等）も確認する。

使い方：
  python3 tools/verify-316-tables.py [--java-dir DIR]
  --java-dir を省略すると src/main/java/io/detergentmc/protocol を使う。
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

MC = Path("/home/hatch/workspace/mcdata404")


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
    for cname in re.findall(r"(RegistryTables316\w+)\.V", m.group(1)):
        ctext = (java_dir / f"{cname}.java").read_text(encoding="utf-8")
        out.extend(parse_table(ctext, "V"))
    return out


def parse_string_array(text, name):
    m = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"文字列配列が見つからない: {name}")
    return re.findall(r'"([^"]*)"', m.group(1))


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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-dir", default=None)
    args = parser.parse_args()
    tools = Path(__file__).parent
    java_dir = Path(args.java_dir) if args.java_dir else \
        tools.parent / "src/main/java/io/detergentmc/protocol"

    exp_path = Path("/tmp/tables-316-expected.json")
    subprocess.run([sys.executable, str(tools / "gen-316-tables.py"),
                    "--json", str(exp_path),
                    "--out", "/tmp/RegistryTables316.java"],
                   check=True, capture_output=True)
    exp = load(exp_path)
    t340 = load(MC / "tables-340.json")

    text = (java_dir / "RegistryTables316.java").read_text(encoding="utf-8")
    snd_text = (java_dir / "RegistryTables316Snd.java").read_text(
        encoding="utf-8")

    pig758 = exp["ENTITY_TYPE"]["rev"][90]
    stone758 = exp["SOUND"]["rev"][397]
    plains758 = exp["BIOME"]["rev"][1]
    fallbacks_fwd = {
        "BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": 90,
        "SOUND": 397, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": 1, "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": pig758,
        "SOUND": stone758, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": plains758, "DAMAGE_TYPE": 16,
    }

    total = 0
    for name, (ef, er) in ((k, (v["fwd"], v["rev"]))
                           for k, v in exp.items()):
        if name == "BLOCK_STATE":
            fr = parse_table(text, "BLOCK_STATE_316_RANGES")
            fo = parse_concat_table(text, "BLOCK_STATE_316_OVERRIDES",
                                    java_dir)
            rr = parse_table(text, "BLOCK_STATE_316_TO_758_RANGES")
            ro = parse_concat_table(text,
                                    "BLOCK_STATE_316_TO_758_OVERRIDES",
                                    java_dir)
        else:
            fr = parse_table(text, f"{name}_316_RANGES")
            fo = parse_table(text, f"{name}_316_OVERRIDES")
            rr = parse_table(text, f"{name}_316_TO_758_RANGES")
            ro = parse_table(text, f"{name}_316_TO_758_OVERRIDES")
        gf = expand(len(ef), fr, fo, fallbacks_fwd[name])
        gr = expand(len(er), rr, ro, fallbacks_rev[name])
        assert gf == ef, f"{name} fwd 不一致"
        assert gr == er, f"{name} rev 不一致"
        total += len(ef) + len(er)
        print(f"{name}: fwd {len(ef)} 件・rev {len(er)} 件 一致")

    # ---- 合成規則の独立確認（340 の表と直接つき合わせ） ----
    f340 = t340["BLOCK_STATE"]["fwd"]
    f316 = exp["BLOCK_STATE"]["fwd"]
    r316 = exp["BLOCK_STATE"]["rev"]
    for i, v in enumerate(f340):
        blk = v >> 4
        if blk in range(235, 251):
            want = (159 << 4) | (blk - 235)
        elif blk in (251, 252):
            want = (35 << 4) | (v & 15)
        else:
            want = v
        assert f316[i] == want, f"BLOCK_STATE fwd 合成規則違反: {i}"
    invalid_bs = t340["BLOCK_STATE"]["rev"][(1 << 4) | 15]
    for i, v in enumerate(r316):
        if (i >> 4) in range(235, 253):
            assert v == invalid_bs, f"BLOCK_STATE rev 打ち切り違反: {i}"
        else:
            assert v == t340["BLOCK_STATE"]["rev"][i]
    print("BLOCK_STATE 合成規則（近似・打ち切り）: 全件成立")

    fi340, fi316 = t340["ITEM"]["fwd"], exp["ITEM"]["fwd"]
    for i, v in enumerate(fi340):
        if v in range(235, 251):
            want = 159
        elif v in (251, 252):
            want = 35
        elif v == 452:
            want = 452
        elif v == 453:
            want = 340
        else:
            want = v
        assert fi316[i] == want, f"ITEM fwd 合成規則違反: {i}"
    print("ITEM 合成規則: 全件成立")

    fe340, fe316 = t340["ENTITY_TYPE"]["fwd"], exp["ENTITY_TYPE"]["fwd"]
    for i, v in enumerate(fe340):
        assert fe316[i] == (90 if v in (37, 105) else v)
    assert exp["ENTITY_TYPE"]["rev"][37] == pig758
    assert exp["ENTITY_TYPE"]["rev"][105] == pig758
    print("ENTITY_TYPE 合成規則: 全件成立")

    snd316 = load(tools / "sounds-316.json")
    snd340 = load(tools / "sounds-340.json")
    idx316 = {n: i for i, n in enumerate(snd316)}
    fs340, fs316 = t340["SOUND"]["fwd"], exp["SOUND"]["fwd"]
    for i, v in enumerate(fs340):
        nm = snd340[v]
        assert fs316[i] == idx316.get(nm, 397), f"SOUND fwd 違反: {i}"
    for j, nm in enumerate(snd316):
        assert exp["SOUND"]["rev"][j] == t340["SOUND"]["rev"][snd340.index(nm)]
    print("SOUND 合成規則（名前合成）: 全件成立")

    for name in ("PARTICLE", "BIOME", "DAMAGE_TYPE"):
        assert exp[name]["fwd"] == t340[name]["fwd"], name
        assert exp[name]["rev"] == t340[name]["rev"], name
    print("PARTICLE/BIOME/DAMAGE_TYPE の 340 据え置き: 成立")

    assert exp["BLOCK_ENTITY"]["rev"] == t340["BLOCK_ENTITY"]["rev"][:23]
    assert all(v != 23 for v in exp["BLOCK_ENTITY"]["fwd"])
    assert -1 in exp["BLOCK_ENTITY"]["fwd"]
    print("BLOCK_ENTITY（bed 打ち切り）: 成立")

    # ---- 名前配列と音の名前対 ----
    be = parse_string_array(text, "BLOCK_ENTITY_NAMES_316")
    assert len(be) == 23 and "bed" not in be and be[0] == "furnace", be[:3]
    bio = parse_string_array(text, "BIOME_NAMES_316")
    bio340 = parse_string_array(
        (java_dir / "RegistryTables340.java").read_text(encoding="utf-8"),
        "BIOME_NAMES_340")
    assert bio == bio340
    print(f"名前配列: BLOCK_ENTITY {len(be)} 件・BIOME {len(bio)} 件 一致")

    snd758 = set(load(MC / "mapping-1.18.json")["sounds"])
    fwd_pairs = load(tools / "sound-names-758-to-316.json")
    rev_pairs = load(tools / "sound-names-316-to-758.json")
    for a, b in fwd_pairs:
        assert a in snd758 and b in idx316 and a != b, (a, b)
    for a, b in rev_pairs:
        assert a in idx316 and b in snd758 and a != b, (a, b)
    print(f"音の名前フォールバック: 758→316 {len(fwd_pairs)} 対・"
          f"316→758 {len(rev_pairs)} 対 妥当")

    # ---- 意味対応の代表値 ----
    assert f316[f340.index((35 << 4) | 0)] == (35 << 4) | 0      # 白羊毛
    assert f316[f340.index((251 << 4) | 5)] == (35 << 4) | 5     # 緑コンクリ→緑羊毛
    assert f316[f340.index((252 << 4) | 14)] == (35 << 4) | 14   # 赤パウダー→赤羊毛
    assert f316[f340.index((240 << 4) | 0)] == (159 << 4) | 5    # 彩釉(240=黄緑)→粘土
    assert fi316[fi340.index(452)] == 452                        # 鉄塊→鉄塊（1.11.1 追加、316 に存在）
    assert fi316[fi340.index(453)] == 340                        # 知識の書→本
    assert fe316[fe340.index(105)] == 90                         # parrot→pig
    assert exp["SOUND"]["fwd"][stone758] == 397                  # 汎用音
    print("意味対応の代表値: 全件成立")

    print(f"検証完了: 表 {total} 件が生成済み Java と一致")


if __name__ == "__main__":
    main()
