#!/usr/bin/env python3
"""RegistryTables404.java（と分割クラス）の検証。

gen-404-tables.py を実行して期待値（JSON）を実データから作り直し、
生成済みの Java ファイル（本体＋ブロック状態の分割クラス）を
読んで展開した結果と全件照合する。代表値の目視確認も行う。

使い方：
  python3 tools/verify-404-tables.py [--data-dir DIR] [--java-dir DIR]
  --data-dir を省略すると ~/workspace/mcdata404、--java-dir を省略すると
  src/main/java/io/detergentmc/protocol を使う。
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


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
    for cname in re.findall(r"(RegistryTables404\w+)\.V", m.group(1)):
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path,
                        default=Path("/home/hatch/workspace/mcdata404"))
    parser.add_argument("--java-dir", type=Path, default=Path(
        "src/main/java/io/detergentmc/protocol"))
    args = parser.parse_args()
    d = args.data_dir
    tools = Path(__file__).resolve().parent

    # 期待値を実データから作り直す（gen の JSON 出力）。
    expect_json = d / "tables-404-verify.json"
    r = subprocess.run(
        [sys.executable, str(tools / "gen-404-tables.py"),
         "--data-dir", str(d), "--json", str(expect_json)],
        capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr, file=sys.stderr)
        raise SystemExit("gen-404-tables.py が失敗した")
    expect = json.loads(expect_json.read_text(encoding="utf-8"))

    text = (args.java_dir / "RegistryTables404.java").read_text(encoding="utf-8")

    def load(p):
        return json.loads(Path(p).read_text(encoding="utf-8"))

    vv758 = load(d / "mapping-1.18.json")
    vv113 = load(d / "mapping-1.13.json")
    entities404 = [e["name"] for e in load(d / "pc" / "1.13.2" / "entities.json")
                   if e["type"] == "mob"]
    ent_rename = {"fireworks_rocket": "firework_rocket",
                  "commandblock_minecart": "command_block_minecart",
                  "iron_golem}": "iron_golem"}
    entities404 = [ent_rename.get(n, n) for n in entities404]
    biomes404 = [b["name"] for b in sorted(
        load(d / "pc" / "1.13.2" / "biomes.json"), key=lambda b: b["id"])]
    sounds404 = vv113["sounds"]

    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": entities404.index("pig"),
        "SOUND": sounds404.index("block.stone.break"), "PARTICLE": 0,
        "BLOCK": 1, "BLOCK_ENTITY": -1,
        "BIOME": biomes404.index("plains"), "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": vv758["entities"].index("pig"),
        "SOUND": vv758["sounds"].index("block.stone.break"), "PARTICLE": 0,
        "BLOCK": 1, "BLOCK_ENTITY": -1,
        "BIOME": 0, "DAMAGE_TYPE": 16,
    }
    biomes758 = [b["name"] for b in sorted(
        load(d / "pc" / "1.18" / "biomes.json"), key=lambda b: b["id"])]
    fallbacks_rev["BIOME"] = biomes758.index("plains")

    total = 0
    for name, maps in expect.items():
        for dirn, key, fb in (("fwd", f"{name}_404", fallbacks_fwd[name]),
                              ("rev", f"{name}_404_TO_758", fallbacks_rev[name])):
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
    for jname, tname in (("sound-names-758-to-404.json", "SOUND_NAME_FALLBACKS_758_TO_404"),
                         ("sound-names-404-to-758.json", "SOUND_NAME_FALLBACKS_404_TO_758")):
        want = [tuple(x) for x in load(tools / jname)]
        got = parse_string_table(text, tname)
        assert got == want, f"{tname} が JSON と違う"
        print(f"{tname}: {len(want)} 対一致。")
        total += len(want)
    # 名前配列。
    be_names = parse_string_array(text, "BLOCK_ENTITY_NAMES_404")
    assert len(be_names) == 25, be_names
    biome_names = parse_string_array(text, "BIOME_NAMES_404")
    assert biome_names == biomes404, "BIOME_NAMES_404 が mcdata と違う"
    print(f"名前配列: BLOCK_ENTITY_NAMES_404={len(be_names)} 件、"
          f"BIOME_NAMES_404={len(biome_names)} 件一致。")

    # 代表値の目視確認。
    bs_rev = expect["BLOCK_STATE"]["rev"]
    bs_fwd = expect["BLOCK_STATE"]["fwd"]
    dg = load(d / "datagen1132" / "reports" / "blocks.json")
    states404 = [None] * 8599
    for key, blk in dg.items():
        nm = key.split(":", 1)[1]
        for st in blk["states"]:
            states404[st["id"]] = (nm, st.get("properties", {}))
    idx758_state = {s: i for i, s in enumerate(vv758["blockstates"])}
    sign404 = next(i for i, (nm, p) in enumerate(states404)
                   if nm == "sign" and p.get("rotation") == "0"
                   and p.get("waterlogged") == "false")
    sign758 = idx758_state["oak_sign[rotation=0,waterlogged=false]"]
    print(f"代表値: 404 sign[rotation=0] ({sign404}) → 758 oak_sign ({sign758})"
          f" 実測 {bs_rev[sign404]}")
    assert bs_rev[sign404] == sign758
    assert bs_fwd[sign758] == next(
        i for i, (nm, p) in enumerate(states404)
        if nm == "sign" and p.get("rotation") == "0" and p.get("waterlogged") == "false")
    ent_rev = expect["ENTITY_TYPE"]["rev"]
    assert ent_rev[entities404.index("zombie")] == vv758["entities"].index("zombie")
    assert ent_rev[entities404.index("trident")] == vv758["entities"].index("trident")
    assert expect["ENTITY_TYPE"]["fwd"][vv758["entities"].index("cat")] == \
        entities404.index("ocelot")
    print("代表値: zombie／trident の往復、cat→ocelot を確認。")
    print(f"合計 {total} 件すべて一致。検証成功。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
