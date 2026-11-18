#!/usr/bin/env python3
"""1.17.1（756）の生成入力を実バニラの報告書と minecraft-data から作る。

tools/build-1.18-inputs.py（757用）が雛形。1.17.1側は
ViaVersion mapping-1.17.json（mapping-1.17.1.json は ViaVersion の
Mappings リポジトリに無いため mapping-1.17.json を使う。757ノート
§7.5 参照）を使う。生の mapping-1.17.json には menus／attributes／
blockentities／enchantments のキーが無い（1.18／1.19 と同じ）ため、
実バニラ jar の reports/registries.json（registries-1.17.1.json）から
protocol_id 順で補う。

入力（detergentmc-protocol/build/remap-data/。757ノート §7.5 で取得済み）：
  - vv-1.17.json … ViaVersion mapping-1.17.json（生）
  - registries-1.17.1.json … 実バニラ 1.17.1 jar の reports/registries.json
  - blocks-1.17.1-reports.json … 実バニラ 1.17.1 jar の reports/blocks.json
  - 1.17_blocks.json … minecraft-data pc/1.17 の blocks.json（898件）
  - 1.17_items.json … minecraft-data pc/1.17 の items.json（1099件。air無し）
  - enchantments-1.17.json … minecraft-data pc/1.17 の enchantments.json（38件）
  - biomes-1.17.json … minecraft-data pc/1.17 の biomes.json（81件。id 0〜175非連番）

出力（すべて detergentmc-protocol/build/remap-data/）：
  - vv-1.17.json … 上書き。menus（24件）・attributes（13件）・
    blockentities（34件）・enchantments（38件）を registries.json の
    protocol_id 順で追加する。

照合（すべて通過しなければ終了コード 1）：
  - vv のブロック状態と報告書 blocks.json の state id が全件一致。
  - vv の blocks／sounds／particles／entities／enchantments と
    minecraft-data の同名ファイルが名前順で全件一致（items は
    minecraft-data に air が無いため vv[1:] と突き合わせる）。
  - vv の blocks／items／entities／sounds／particles／blockentities／
    enchantments／menus／attributes／mob_effect と registries.json の
    protocol_id 順が全件一致。
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
                tuple(kv.split("=", 1)) for kv in rest[:-1].split(",")))
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
    parser.add_argument("--mc117-dir", type=Path, default=Path("/tmp/mc117"))
    args = parser.parse_args()
    data_dir = args.data_dir
    failures = []

    vv17 = load(data_dir / "vv-1.17.json")
    reg17 = load(data_dir / "registries-1.17.1.json")
    rep17 = load(data_dir / "blocks-1.17.1-reports.json")

    # minecraft-data の取得（ローカルになければ fetch）。
    args.mc117_dir.mkdir(parents=True, exist_ok=True)
    for f in ["sounds.json", "particles.json", "entities.json"]:
        fetch(f"{MC_BASE}/1.17/{f}", args.mc117_dir / f)
    mc17 = {f: load(args.mc117_dir / f)
            for f in ["sounds.json", "particles.json", "entities.json"]}
    mc17["blocks.json"] = load(data_dir / "1.17_blocks.json")
    mc17["items.json"] = load(data_dir / "1.17_items.json")
    mc17["enchantments.json"] = load(data_dir / "enchantments-1.17.json")
    mc17["biomes.json"] = load(data_dir / "biomes-1.17.json")

    # --- 照合1: vv ブロック状態と報告書 blocks.json の state id ---
    mm = check_blockstates(vv17["blockstates"], rep17)
    print(f"[1.17] blockstates vs reports: {len(vv17['blockstates'])} 件、"
          f"不一致 {len(mm)} 件 {mm[:3]}")
    if mm:
        failures.append("blockstates-1.17")

    # --- 照合2: vv と minecraft-data の名前順 ---
    for key, mc_key in (("blocks", "blocks.json"), ("sounds", "sounds.json"),
                        ("particles", "particles.json"),
                        ("entities", "entities.json"),
                        ("enchantments", "enchantments.json")):
        vv_names = vv17.get(key)
        mc_names = [e["name"] for e in sorted(mc17[mc_key], key=lambda e: e["id"])]
        if vv_names is None:
            print(f"[1.17] {key}: vv にキーが無い（後で registries.json から補う）")
            continue
        if vv_names != mc_names:
            diff = [(i, a, b) for i, (a, b) in enumerate(zip(vv_names, mc_names))
                    if a != b][:5]
            print(f"[1.17] {key}: vv {len(vv_names)} 件 vs mc {len(mc_names)} 件、"
                  f"差分 {diff}")
            failures.append(f"{key}-1.17")
        else:
            print(f"[1.17] {key}: vv と minecraft-data が {len(vv_names)} 件一致")
    # items: minecraft-data 1.17 には air が無い（id 1 から）。
    vv_items = vv17["items"]
    mc_items = [e["name"] for e in sorted(mc17["items.json"], key=lambda e: e["id"])]
    if vv_items[1:] == mc_items and vv_items[0] == "air":
        print(f"[1.17] items: vv {len(vv_items)} 件と minecraft-data "
              f"{len(mc_items)} 件が一致（air なし）")
    else:
        print(f"[1.17] items: vv {len(vv_items)} 件 vs mc {len(mc_items)} 件で不一致")
        failures.append("items-1.17")

    # --- 照合3: vv と registries.json の protocol_id 順 ---
    for vv_key, reg_key in (("blocks", "minecraft:block"),
                            ("items", "minecraft:item"),
                            ("entities", "minecraft:entity_type"),
                            ("sounds", "minecraft:sound_event"),
                            ("particles", "minecraft:particle_type"),
                            ("blockentities", "minecraft:block_entity_type"),
                            ("enchantments", "minecraft:enchantment"),
                            ("menus", "minecraft:menu"),
                            ("attributes", "minecraft:attribute")):
        reg_order = registry_order(reg17, reg_key)
        vv_names = vv17.get(vv_key)
        if vv_names is None:
            print(f"[1.17] {vv_key}: vv にキーが無い（後で補う）。"
                  f"registries.json は {len(reg_order)} 件")
            continue
        if vv_names != reg_order:
            print(f"[1.17] {vv_key}: vv と registries.json の順序が違う")
            failures.append(f"{vv_key}-reg-1.17")
        else:
            print(f"[1.17] {vv_key}: vv と registries.json が {len(reg_order)} 件一致")
    # mob_effect は vv に無い（ViaVersion mapping には無い）。registries.json の件数だけ記録。
    eff17 = registry_order(reg17, "minecraft:mob_effect")
    print(f"[1.17] mob_effect: registries.json は {len(eff17)} 件")

    # --- menus／attributes／blockentities／enchantments を registries.json から補う ---
    for tag, key in (("menus", "minecraft:menu"),
                     ("attributes", "minecraft:attribute"),
                     ("blockentities", "minecraft:block_entity_type"),
                     ("enchantments", "minecraft:enchantment")):
        names = registry_order(reg17, key)
        print(f"[1.17] {tag} {len(names)} 件")
        vv17[tag] = names
    for tag, want in (("menus", 24), ("attributes", 13),
                      ("blockentities", 34), ("enchantments", 38)):
        if len(vv17[tag]) != want:
            failures.append(f"{tag}-count-1.17")

    if failures:
        print(f"照合失敗: {failures}", file=sys.stderr)
        return 1

    (data_dir / "vv-1.17.json").write_text(json.dumps(vv17), encoding="utf-8")
    print("vv-1.17.json に menus／attributes／blockentities／enchantments を追加した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
