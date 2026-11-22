#!/usr/bin/env python3
"""RegistryTables393.java（と分割クラス）の検証。

gen-393-tables.py を実行して期待値（JSON）を実データから作り直し、
生成済みの Java ファイル（本体＋ブロック状態の分割クラス）を
読んで展開した結果と全件照合する。代表値の目視確認も行う
（サンゴと tnt の 1.13.1 追加プロパティまわり）。

使い方：
  python3 tools/verify-393-tables.py [--java-dir DIR]
  --java-dir を省略すると src/main/java/io/detergentmc/protocol を使う。
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

DATA404 = Path("/home/hatch/workspace/mcdata404")
GEN393 = Path("/home/hatch/workspace/legacy-prep-393/gen-1.13")


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
    for cname in re.findall(r"(RegistryTables393\w+)\.V", m.group(1)):
        ctext = (java_dir / f"{cname}.java").read_text(encoding="utf-8")
        out.extend(parse_table(ctext, "V"))
    return out


def parse_string_table(text, name):
    m = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"文字列表が見つからない: {name}")
    return [tuple(re.findall(r'"([^"]*)"', e))
            for e in re.findall(r"\{([^{}]+)\}", m.group(1))]


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


def fmt_state(name, props):
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-dir", type=Path, default=Path(
        "src/main/java/io/detergentmc/protocol"))
    args = parser.parse_args()
    tools = Path(__file__).resolve().parent

    # 期待値を実データから作り直す（gen の JSON 出力）。
    expect_json = Path("/tmp/tables-393-verify.json")
    r = subprocess.run(
        [sys.executable, str(tools / "gen-393-tables.py"),
         "--json", str(expect_json)],
        capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr, file=sys.stderr)
        raise SystemExit("gen-393-tables.py が失敗した")
    expect = json.loads(expect_json.read_text(encoding="utf-8"))

    text = (args.java_dir / "RegistryTables393.java").read_text(encoding="utf-8")

    vv758 = load(DATA404 / "mapping-1.18.json")
    vv113 = load(DATA404 / "mapping-1.13.json")
    entities393 = [e["name"] for e in load(DATA404 / "pc" / "1.13.2" / "entities.json")
                   if e["type"] == "mob"]
    ent_rename = {"fireworks_rocket": "firework_rocket",
                  "commandblock_minecart": "command_block_minecart",
                  "iron_golem}": "iron_golem"}
    entities393 = [ent_rename.get(n, n) for n in entities393]
    biomes393 = [b["name"] for b in sorted(
        load(DATA404 / "pc" / "1.13.2" / "biomes.json"), key=lambda b: b["id"])]
    sounds393 = vv113["sounds"]

    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": entities393.index("pig"),
        "SOUND": sounds393.index("block.stone.break"), "PARTICLE": 0,
        "BLOCK": 1, "BLOCK_ENTITY": -1,
        "BIOME": biomes393.index("plains"), "DAMAGE_TYPE": 16,
    }
    biomes758 = [b["name"] for b in sorted(
        load(DATA404 / "pc" / "1.18" / "biomes.json"), key=lambda b: b["id"])]
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": vv758["entities"].index("pig"),
        "SOUND": vv758["sounds"].index("block.stone.break"), "PARTICLE": 0,
        "BLOCK": 1, "BLOCK_ENTITY": -1,
        "BIOME": biomes758.index("plains"), "DAMAGE_TYPE": 16,
    }

    total = 0
    for name, maps in expect.items():
        for dirn, key, fb in (("fwd", f"{name}_393", fallbacks_fwd[name]),
                              ("rev", f"{name}_393_TO_758", fallbacks_rev[name])):
            ranges = parse_table(text, key + "_RANGES")
            if name == "BLOCK_STATE":
                overrides = parse_concat_table(text, key + "_OVERRIDES",
                                               args.java_dir)
            else:
                overrides = parse_table(text, key + "_OVERRIDES")
            got = expand(len(maps[dirn]), ranges, overrides, fb)
            want = maps[dirn]
            assert got == want, (
                f"{name} {dirn}: Java 表と期待値が違う "
                f"（最初の相違 index {[i for i, (a, b) in enumerate(zip(got, want)) if a != b][:5]}）")
            total += len(want)
            print(f"{name} {dirn}: {len(want)} 件一致。")
    # 音の名前表。
    for jname, tname in (("sound-names-758-to-393.json", "SOUND_NAME_FALLBACKS_758_TO_393"),
                         ("sound-names-393-to-758.json", "SOUND_NAME_FALLBACKS_393_TO_758")):
        want = [tuple(x) for x in load(tools / jname)]
        got = parse_string_table(text, tname)
        assert got == want, f"{tname} が JSON と違う"
        print(f"{tname}: {len(want)} 対一致。")
        total += len(want)
    # 名前配列。
    be_names = parse_string_array(text, "BLOCK_ENTITY_NAMES_393")
    assert len(be_names) == 25, be_names
    biome_names = parse_string_array(text, "BIOME_NAMES_393")
    assert biome_names == biomes393, "BIOME_NAMES_393 が mcdata と違う"
    print(f"名前配列: BLOCK_ENTITY_NAMES_393={len(be_names)} 件、"
          f"BIOME_NAMES_393={len(biome_names)} 件一致。")

    # 代表値の目視確認（393 固有の差分まわり）。
    bs_rev = expect["BLOCK_STATE"]["rev"]
    bs_fwd = expect["BLOCK_STATE"]["fwd"]
    dg = load(GEN393 / "generated" / "reports" / "blocks.json")
    states393 = [None] * 8582
    for key, blk in dg.items():
        nm = key.split(":", 1)[1]
        for st in blk["states"]:
            states393[st["id"]] = fmt_state(
                nm, {k: str(v) for k, v in st.get("properties", {}).items()})
    idx758_state = {s: i for i, s in enumerate(vv758["blockstates"])}
    # 393 の tube_coral（プロパティ無しの単一状態）→ 758 は
    # waterlogged 付き。404 の既定値（true）へ寄るはず。
    tube393 = states393.index("tube_coral")
    got758 = vv758["blockstates"][bs_rev[tube393]]
    print(f"代表値: 393 tube_coral ({tube393}) → 758 {got758}")
    assert got758 == "tube_coral[waterlogged=true]", got758
    # 758 の dead_tube_coral → 393 は生きた tube_coral の単一状態。
    dead758 = idx758_state["dead_tube_coral[waterlogged=false]"]
    assert states393[bs_fwd[dead758]] == "tube_coral"
    print(f"代表値: 758 dead_tube_coral[waterlogged=false] ({dead758})"
          f" → 393 tube_coral ({bs_fwd[dead758]})")
    # 393 の tnt（単一状態）→ 758 は unstable=false（404 の既定値）。
    tnt393 = states393.index("tnt")
    got758 = vv758["blockstates"][bs_rev[tnt393]]
    print(f"代表値: 393 tnt ({tnt393}) → 758 {got758}")
    assert got758 == "tnt[unstable=false]", got758
    # アイテム：758 の dead_horn_coral → 393 の horn_coral。
    items393 = [k.split(":", 1)[1] for k, _ in sorted(
        load(GEN393 / "generated" / "reports" / "items.json").items(),
        key=lambda kv: kv[1]["protocol_id"])]
    item_fwd = expect["ITEM"]["fwd"]
    dead_item758 = vv758["items"].index("dead_horn_coral")
    assert items393[item_fwd[dead_item758]] == "horn_coral"
    print(f"代表値: 758 アイテム dead_horn_coral ({dead_item758})"
          f" → 393 horn_coral ({item_fwd[dead_item758]})")
    # エンティティは 404 と同じ確認。
    ent_rev = expect["ENTITY_TYPE"]["rev"]
    assert ent_rev[entities393.index("zombie")] == vv758["entities"].index("zombie")
    assert ent_rev[entities393.index("trident")] == vv758["entities"].index("trident")
    print("代表値: zombie／trident の往復を確認。")
    print(f"合計 {total} 件すべて一致。検証成功。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
