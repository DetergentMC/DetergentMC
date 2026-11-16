#!/usr/bin/env python3
"""1.16.5（754）の生成入力を実バニラの報告書と minecraft-data から作る。

tools/build-1.17-inputs.py（755用）が雛形。ViaVersion の
mapping-1.16.json は 1.16.0 時点のもので、1.16.5（754）の実バニラとは
食い違うため、実バニラ jar の reports（registries-1.16.5.json／
blocks-1.16.5-reports.json）の protocol_id 順で上書きして補正する。
生の mapping-1.16.json は vv-1.16-raw.json として残す。

実測した食い違い（vv-1.16.json 生 vs registries-1.16.5.json）：
  - items：975 vs 976。piglin_brute_spawn_egg（1.16.2 追加）が無い。
    さらに道具・染料まわりの順序が違う（vv は 1.16.0 の順序）。
  - entities：107 vs 108。piglin_brute（id 61。1.16.2 追加）が無い。
  - sounds：985 vs 992。piglin_brute 系 7 件が無い。
  - blockstates：17104 vs 17112。chain（axis 付き 6 状態のはずが
    waterlogged のみ 2 状態）、lantern／soul_lantern（waterlogged 付き
    4 状態のはずが hanging のみ 2 状態）が足りない。計 8 状態。
  - blocks 763・particles 72 は registries と全件一致。

minecraft-data（dataPaths.json で 1.16.5 は protocol・blocks・items・
biomes・entities が pc/1.16.2、enchantments が pc/1.16.4、sounds・
particles が pc/1.16 を指す。**pc/1.16 ではない**。pc/1.16/protocol.json
は 1.16.0 のものでパケットIDが 0x0f 以降 1 つずれているため使わない。
pc/1.16/blocks.json は 404（存在しない））との照合も行う：
  - 1.16_blocks.json（pc/1.16.2）763 件は registries と全件一致。
  - 1.16_items.json（pc/1.16.2）975 件は registries の [1:] と一致（air 無し）。
  - entities（pc/1.16.2）108 件は registries と全件一致。
  - sounds（pc/1.16）985 件は registries から piglin_brute 系 7 件を
    除いたものと一致。
  - particles（pc/1.16）72 件は registries と全件一致。
  - enchantments（pc/1.16.4）38 件は registries と全件一致。

入力（detergentmc-protocol/build/remap-data/）：
  - vv-1.16.json … ViaVersion mapping-1.16.json（生）
  - registries-1.16.5.json … 実バニラ 1.16.5 jar の reports/registries.json
  - blocks-1.16.5-reports.json … 実バニラ 1.16.5 jar の reports/blocks.json
  - 1.16_blocks.json … minecraft-data pc/1.16.2 の blocks.json（763件）
  - 1.16_items.json … minecraft-data pc/1.16.2 の items.json（975件。air無し）
  - enchantments-1.16.json … minecraft-data pc/1.16.4 の enchantments.json（38件）
  - biomes-1.16.json … minecraft-data pc/1.16.2 の biomes.json（79件。id 0〜173）

出力（すべて detergentmc-protocol/build/remap-data/）：
  - vv-1.16-raw.json … 生の mapping-1.16.json（保存のみ）
  - vv-1.16.json … 上書き。blockstates（17112件）・blocks（763件）・
    items（976件）・entities（108件）・sounds（992件）・particles（72件）
    を registries／reports の protocol_id 順で置き換え、menus（24件）・
    attributes（13件）・blockentities（33件）・enchantments（38件）を
    registries.json の protocol_id 順で追加する。

照合（すべて通過しなければ終了コード 1）：
  - vv のブロック状態と報告書 blocks.json の state id が全件一致。
  - vv の blocks／items／entities／sounds／particles／blockentities／
    enchantments／menus／attributes と registries.json の protocol_id 順が
    全件一致。
  - minecraft-data との突き合わせ（上記の通り）。
"""
import json
import shutil
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
MC116_DIR = Path.home() / "workspace" / "detergentmc-754" / ".tmp-754" / "registry" / "mc116"


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def registry_order(registries, key):
    """registries.json の entries を protocol_id 順に並べ、名前を返す。"""
    entries = registries[key]["entries"]
    return [k.split(":", 1)[1] for _, k in sorted(
        (v["protocol_id"], k) for k, v in entries.items())]


def reports_blockstates(reports_blocks):
    """reports/blocks.json から state id 順のブロック状態文字列を作る。"""
    by_id = {}
    for name, info in reports_blocks.items():
        short = name.split(":", 1)[1]
        for st in info["states"]:
            props = st.get("properties", {})
            s = short + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"
                         if props else "")
            by_id[st["id"]] = s
    return [by_id[i] for i in range(len(by_id))]


def main():
    failures = []
    vv_raw = load(DATA_DIR / "vv-1.16.json")
    reg16 = load(DATA_DIR / "registries-1.16.5.json")
    rep16 = load(DATA_DIR / "blocks-1.16.5-reports.json")

    # 生の mapping-1.16.json を残す（上書き前に保存）。
    raw_path = DATA_DIR / "vv-1.16-raw.json"
    if not raw_path.exists():
        shutil.copy(DATA_DIR / "vv-1.16.json", raw_path)
        print("vv-1.16-raw.json に生の mapping-1.16.json を保存した")

    # --- 生 vv と registries／reports の差分を記録する ---
    for key, reg_key in (("blocks", "minecraft:block"),
                         ("items", "minecraft:item"),
                         ("entities", "minecraft:entity_type"),
                         ("sounds", "minecraft:sound_event"),
                         ("particles", "minecraft:particle_type")):
        ro = registry_order(reg16, reg_key)
        vv_names = vv_raw.get(key)
        if vv_names == ro:
            print(f"[1.16.5] {key}: 生 vv と registries が {len(ro)} 件一致")
        else:
            missing = sorted(set(ro) - set(vv_names))
            extra = sorted(set(vv_names) - set(ro))
            print(f"[1.16.5] {key}: 生 vv {len(vv_names)} 件 vs registries {len(ro)} 件、"
                  f"不足={missing} 余分={extra}")
    rep_states = reports_blockstates(rep16)
    print(f"[1.16.5] blockstates: 生 vv {len(vv_raw['blockstates'])} 件 vs "
          f"reports {len(rep_states)} 件")

    # --- vv を registries／reports の protocol_id 順で上書きする ---
    vv = dict(vv_raw)
    vv["blockstates"] = rep_states
    for vv_key, reg_key in (("blocks", "minecraft:block"),
                            ("items", "minecraft:item"),
                            ("entities", "minecraft:entity_type"),
                            ("sounds", "minecraft:sound_event"),
                            ("particles", "minecraft:particle_type"),
                            ("menus", "minecraft:menu"),
                            ("attributes", "minecraft:attribute"),
                            ("blockentities", "minecraft:block_entity_type"),
                            ("enchantments", "minecraft:enchantment")):
        vv[vv_key] = registry_order(reg16, reg_key)
    for tag, want in (("blockstates", 17112), ("blocks", 763), ("items", 976),
                      ("entities", 108), ("sounds", 992), ("particles", 72),
                      ("menus", 24), ("attributes", 13), ("blockentities", 33),
                      ("enchantments", 38)):
        if len(vv[tag]) != want:
            print(f"[1.16.5] {tag}: {len(vv[tag])} 件（想定 {want} 件）",
                  file=sys.stderr)
            failures.append(f"{tag}-count-1.16.5")

    # --- 照合1: vv ブロック状態と報告書 blocks.json の state id ---
    if vv["blockstates"] != rep_states:
        print("[1.16.5] blockstates: vv と reports が違う", file=sys.stderr)
        failures.append("blockstates-1.16.5")
    else:
        print(f"[1.16.5] blockstates vs reports: {len(rep_states)} 件、"
              f"不一致 0 件")

    # --- 照合2: minecraft-data との突き合わせ ---
    mc_blocks = [e["name"] for e in sorted(load(DATA_DIR / "1.16_blocks.json"),
                                            key=lambda e: e["id"])]
    if mc_blocks != vv["blocks"]:
        print("[1.16.5] blocks: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("blocks-1.16.5")
    else:
        print(f"[1.16.5] blocks: minecraft-data と vv が {len(mc_blocks)} 件一致")
    mc_items = [e["name"] for e in sorted(load(DATA_DIR / "1.16_items.json"),
                                           key=lambda e: e["id"])]
    if mc_items != vv["items"][1:] or vv["items"][0] != "air":
        print("[1.16.5] items: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("items-1.16.5")
    else:
        print(f"[1.16.5] items: vv {len(vv['items'])} 件と minecraft-data "
              f"{len(mc_items)} 件が一致（air なし）")
    mc_entities = [e["name"] for e in sorted(load(MC116_DIR / "1.16.2_entities.json"),
                                              key=lambda e: e["id"])]
    if mc_entities != vv["entities"]:
        print("[1.16.5] entities: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("entities-1.16.5")
    else:
        print(f"[1.16.5] entities: minecraft-data と vv が {len(mc_entities)} 件一致")
    mc_sounds = [e["name"] for e in sorted(load(MC116_DIR / "1.16_sounds.json"),
                                            key=lambda e: e["id"])]
    reg_sounds = registry_order(reg16, "minecraft:sound_event")
    if mc_sounds != [n for n in reg_sounds if "piglin_brute" not in n]:
        print("[1.16.5] sounds: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("sounds-1.16.5")
    else:
        print(f"[1.16.5] sounds: minecraft-data {len(mc_sounds)} 件は "
              f"registries から piglin_brute 系 7 件を除いたものと一致")
    mc_particles = [e["name"] for e in sorted(load(MC116_DIR / "1.16_particles.json"),
                                               key=lambda e: e["id"])]
    if mc_particles != vv["particles"]:
        print("[1.16.5] particles: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("particles-1.16.5")
    else:
        print(f"[1.16.5] particles: minecraft-data と vv が {len(mc_particles)} 件一致")
    mc_ench = [e["name"] for e in sorted(load(DATA_DIR / "enchantments-1.16.json"),
                                          key=lambda e: e["id"])]
    if mc_ench != vv["enchantments"]:
        print("[1.16.5] enchantments: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("enchantments-1.16.5")
    else:
        print(f"[1.16.5] enchantments: minecraft-data と vv が {len(mc_ench)} 件一致")
    # statistics（vv 生の 74 件）と custom_stat（74 件）。
    print(f"[1.16.5] statistics: vv 生 {len(vv_raw['statistics'])} 件、"
          f"registries custom_stat "
          f"{len(reg16['minecraft:custom_stat']['entries'])} 件")

    if failures:
        print(f"照合失敗: {failures}", file=sys.stderr)
        return 1

    (DATA_DIR / "vv-1.16.json").write_text(json.dumps(vv), encoding="utf-8")
    print("vv-1.16.json を registries／reports の順序で補正した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
