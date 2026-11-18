#!/usr/bin/env python3
"""1.18（757）の生成入力を実バニラの報告書と minecraft-data から作る。

minecraft-data には pc/1.18 の blocks.json／items.json／sounds.json／
particles.json／entities.json／biomes.json があるが、registries.json が
無い（1.20.5 以降で追加）。そのためメニュー・属性・エンチャントの
レジストリ順は実バニラ jar（server-1.18.jar／server-1.19.jar）から
net.minecraft.data.Main --reports で出した報告書
（reports/registries.json）から取る。報告書は
detergentmc-protocol/build/remap-data/registries-1.18.json／
registries-1.19.json に置いてある（/tmp は 512MB tmpfs で worldgen
報告書のコピー時に満杯になるため、build/remap-data に直接置く）。

入力（--mc118-dir／--mc119-dir は minecraft-data の pc/1.18／pc/1.19
のローカルコピー。無ければ raw.githubusercontent.com から取得する）：
  - vv-1.18.json … ViaVersion mapping-1.18.json（生。menus／attributes
    は無いため registries.json から補う）
  - vv-1.19.json … ViaVersion mapping-1.19.json（同上）
  - blocks-1.18-reports.json … reports/blocks.json
  - blocks-1.19-reports.json … reports/blocks.json
  - registries-1.18.json／registries-1.19.json … reports/registries.json

出力（すべて detergentmc-protocol/build/remap-data/）：
  - vv-1.18.json／vv-1.19.json … 上書き。menus（24件）・attributes
    （13件）を registries.json の protocol_id 順で追加する。
    （ViaVersion mapping-1.20.json には menus が生で入っているが、
    mapping-1.18.json／mapping-1.19.json には無いため）
  - 1.18_blocks.json／1.19_blocks.json … minecraft-data の blocks.json
    と同じ形の list。generate-remap-tables.py が使うのは name と
    defaultState だけ。
  - enchantments-1.18.json … minecraft-data pc/1.17 のもの
    （dataPaths.json で 1.18 の enchantments は pc/1.17 を指す）。
  - enchantments-1.19.json … minecraft-data pc/1.19 のもの。

照合（すべて通過しなければ終了コード 1）：
  - vv のブロック状態と報告書 blocks.json の state id が全件一致。
  - vv の blocks／items／sounds／particles／entities と minecraft-data
    の同名ファイルが名前順で全件一致（items は minecraft-data に air
    が無いため vv[1:] と突き合わせる）。
  - vv の enchantments と minecraft-data の enchantments.json が
    名前順で全件一致し、registries.json の protocol_id 順とも一致。
  - menus は 1.18／1.19 とも 24件。attributes は 1.18／1.19 とも 13件。
"""

import argparse
import json
import sys
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"

MC_BASE = "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc"


def fetch(url, dest):
    if dest.exists():
        return
    print(f"fetch {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "DetergentMC/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r, open(dest, "wb") as f:
        f.write(r.read())


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def registry_order(registries, key):
    """registries.json の entries を protocol_id 順に並べ、名前を返す。"""
    entries = registries[key]["entries"]
    return [k.split(":", 1)[1] for _, k in sorted(
        (v["protocol_id"], k) for k, v in entries.items())]


def check_blockstates(vv_states, reports_blocks):
    """vv のブロック状態文字列と報告書 blocks.json の state id を照合。"""
    # 報告書: {minecraft:name: {states: [{id, properties, default?}]}}。
    by_props = {}
    for name, info in reports_blocks.items():
        short = name.split(":", 1)[1]
        for st in info["states"]:
            props = st.get("properties", {})
            by_props[(short, tuple(sorted(props.items())))] = st["id"]
    mismatches = []
    for i, s in enumerate(vv_states):
        if "[" in s:
            name, rest = s.split("[", 1)
            props = tuple(sorted(
                kv.split("=", 1) for kv in rest[:-1].split(",")))
            # 値は文字列のまま。報告書の properties の値も文字列。
            props = tuple((k, v) for k, v in
                          ((a, b) for a, b in props))
        else:
            name, props = s, ()
        got = by_props.get((name, props))
        if got != i:
            mismatches.append((i, s, got))
            if len(mismatches) >= 5:
                break
    return mismatches


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--mc118-dir", type=Path, default=Path("/tmp/mc118"))
    parser.add_argument("--mc119-dir", type=Path, default=Path("/tmp/mc119"))
    args = parser.parse_args()
    data_dir = args.data_dir
    failures = []

    vv18 = load(data_dir / "vv-1.18.json")
    vv19 = load(data_dir / "vv-1.19.json")
    reg18 = load(data_dir / "registries-1.18.json")
    reg19 = load(data_dir / "registries-1.19.json")
    rep18 = load(data_dir / "blocks-1.18-reports.json")
    rep19 = load(data_dir / "blocks-1.19-reports.json")

    # minecraft-data の取得（ローカルになければ fetch）。
    for d, ver, files in (
            (args.mc118_dir, "1.18", ["blocks.json", "items.json", "sounds.json",
                                      "particles.json", "entities.json", "biomes.json"]),
            (args.mc119_dir, "1.19", ["blocks.json", "items.json", "sounds.json",
                                      "particles.json", "entities.json", "biomes.json"])):
        d.mkdir(parents=True, exist_ok=True)
        for f in files:
            fetch(f"{MC_BASE}/{ver}/{f}", d / f)
    fetch(f"{MC_BASE}/1.17/enchantments.json", args.mc118_dir / "enchantments.json")
    fetch(f"{MC_BASE}/1.19/enchantments.json", args.mc119_dir / "enchantments.json")

    mc18 = {f: load(args.mc118_dir / f)
            for f in ["blocks.json", "items.json", "sounds.json",
                      "particles.json", "entities.json", "biomes.json",
                      "enchantments.json"]}
    mc19 = {f: load(args.mc119_dir / f)
            for f in ["blocks.json", "items.json", "sounds.json",
                      "particles.json", "entities.json", "biomes.json",
                      "enchantments.json"]}

    # --- 照合1: vv ブロック状態と報告書 blocks.json の state id ---
    for tag, vv, rep in (("1.18", vv18, rep18), ("1.19", vv19, rep19)):
        mm = check_blockstates(vv["blockstates"], rep)
        print(f"[{tag}] blockstates vs reports: {len(vv['blockstates'])} 件、"
              f"不一致 {len(mm)} 件 {mm[:3]}")
        if mm:
            failures.append(f"blockstates-{tag}")

    # --- 照合2: vv と minecraft-data の名前順 ---
    for tag, vv, mc in (("1.18", vv18, mc18), ("1.19", vv19, mc19)):
        for key, mc_key, skip_first in (
                ("blocks", "blocks.json", False),
                ("sounds", "sounds.json", False),
                ("particles", "particles.json", False),
                ("entities", "entities.json", False),
                ("enchantments", "enchantments.json", False)):
            vv_names = vv[key]
            mc_names = [e["name"] for e in sorted(mc[mc_key], key=lambda e: e["id"])]
            if vv_names != mc_names:
                diff = [(i, a, b) for i, (a, b) in enumerate(zip(vv_names, mc_names))
                        if a != b][:5]
                print(f"[{tag}] {key}: vv {len(vv_names)} 件 vs mc {len(mc_names)} 件、"
                      f"差分 {diff}")
                failures.append(f"{key}-{tag}")
            else:
                print(f"[{tag}] {key}: vv と minecraft-data が {len(vv_names)} 件一致")
        # items: minecraft-data 1.18 には air が無いが、1.19 にはある
        # （id 0）。ある場合はそのまま、無い場合は vv[1:] と突き合わせる。
        vv_items = vv["items"]
        mc_items = [e["name"] for e in sorted(mc["items.json"], key=lambda e: e["id"])]
        mc_has_air = mc_items and mc_items[0] == "air"
        ok_items = (vv_items == mc_items) if mc_has_air else (
            vv_items[1:] == mc_items and vv_items[0] == "air")
        if not ok_items:
            print(f"[{tag}] items: vv {len(vv_items)} 件 vs mc {len(mc_items)} 件で不一致")
            failures.append(f"items-{tag}")
        else:
            print(f"[{tag}] items: vv {len(vv_items)} 件と "
                  f"minecraft-data {len(mc_items)} 件が一致"
                  f"（air {'あり' if mc_has_air else 'なし'}）")

    # --- 照合3: エンチャントは registries.json の protocol_id 順とも一致 ---
    for tag, vv, reg in (("1.18", vv18, reg18), ("1.19", vv19, reg19)):
        reg_order = registry_order(reg, "minecraft:enchantment")
        if vv["enchantments"] != reg_order:
            print(f"[{tag}] enchantments: vv と registries.json の順序が違う")
            failures.append(f"enchantments-reg-{tag}")
        else:
            print(f"[{tag}] enchantments: vv と registries.json が "
                  f"{len(reg_order)} 件一致")

    # --- menus／attributes を registries.json から補う ---
    for tag, vv, reg in (("1.18", vv18, reg18), ("1.19", vv19, reg19)):
        menus = registry_order(reg, "minecraft:menu")
        attrs = registry_order(reg, "minecraft:attribute")
        print(f"[{tag}] menus {len(menus)} 件: {menus}")
        print(f"[{tag}] attributes {len(attrs)} 件: {attrs}")
        if len(menus) != 24:
            failures.append(f"menus-count-{tag}")
        if len(attrs) != 13:
            failures.append(f"attributes-count-{tag}")
        vv["menus"] = menus
        vv["attributes"] = attrs

    if failures:
        print(f"照合失敗: {failures}", file=sys.stderr)
        return 1

    # --- 出力 ---
    (data_dir / "vv-1.18.json").write_text(json.dumps(vv18), encoding="utf-8")
    (data_dir / "vv-1.19.json").write_text(json.dumps(vv19), encoding="utf-8")
    print("vv-1.18.json／vv-1.19.json に menus／attributes を追加した")
    for tag, mc in (("1.18", mc18), ("1.19", mc19)):
        out = data_dir / f"{tag}_blocks.json"
        out.write_text(json.dumps(mc["blocks.json"]), encoding="utf-8")
        print(f"{out.name}: {len(mc['blocks.json'])} 件")
        out = data_dir / f"enchantments-{tag}.json"
        ench = [{"id": e["id"], "name": e["name"]}
                for e in sorted(mc["enchantments.json"], key=lambda e: e["id"])]
        out.write_text(json.dumps(ench), encoding="utf-8")
        print(f"{out.name}: {len(ench)} 件")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
