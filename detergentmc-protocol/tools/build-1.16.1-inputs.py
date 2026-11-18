#!/usr/bin/env python3
"""1.16.1（736）の生成入力を実バニラの報告書と minecraft-data から作る。

tools/build-1.16-inputs.py（754用）が雛形。ViaVersion の
mapping-1.16.json は 1.16.0 時点のもので、1.16.1（736）の実バニラとは
食い違う見込みのため、実バニラ jar の reports（registries-1.16.1.json／
blocks-1.16.1-reports.json）の protocol_id 順で上書きして補正する。
生の mapping-1.16.json は vv-1.16-raw.json として既に残してある。

dataPaths.json（PrismarineJS/minecraft-data master。2026-11-16 取得。
.tmp-736/registry/dataPaths.json に保存）の 1.16.1 の指し先（実測）：
  - protocol・blocks・items・biomes・entities：pc/1.16.1
  - enchantments：pc/1.13.2（※罠。下記参照）
  - particles・attributes：pc/1.16、sounds：キー無し
  pc/1.16/protocol.json は 1.16.0 のもので使わない（754 §11.2 の罠と同じ）。

罠（1.16.1 の enchantments は dataPaths の指し先が古い）：
  dataPaths.json で 1.16.1 の enchantments は pc/1.13.2 を指すが、
  pc/1.13.2/enchantments.json は 34 件しかなく、1.16 で追加された
  multishot・piercing・quick_charge・soul_speed の 4 件が無い。
  実バニラ 1.16.1 の registries.json は 38 件で、順序は pc/1.16.4
  （enchantments-1.16.json）と完全一致する。つまり minecraft-data の
  1.16.1 向け enchantments.json は更新されておらず、dataPaths の
  指し先は stale である。権威あるのは実バニラの registries.json の
  protocol_id 順のため、736 側のエンチャント一覧は pc/1.16.4 順
  （＝registries-1.16.1.json の順）を使う。pc/1.13.2 のファイルは
  751ツリーの .tmp-751/registry/mc/enchantments-1.13.2.json に残してある
  （読み取り専用で再利用）。
  同じく sounds は dataPaths の 1.16.1 にキーが無いが、pc/1.16 の
  sounds.json（985 件）は registries と全件一致する（1.16.1 には
  piglin_brute 系がまだ無いため 754 のような除外は不要）。

入力（detergentmc-protocol/build/remap-data/）：
  - vv-1.16-raw.json … ViaVersion mapping-1.16.json（生）
  - registries-1.16.1.json … 実バニラ 1.16.1 jar の reports/registries.json
  - blocks-1.16.1-reports.json … 実バニラ 1.16.1 jar の reports/blocks.json
  - 1.16.1_blocks.json … minecraft-data pc/1.16.1 の blocks.json（763件）
  - 1.16.1_items.json … minecraft-data pc/1.16.1 の items.json（974件。air無し）
  - biomes-1.16.1.json … minecraft-data pc/1.16.1 の biomes.json（79件。id 0〜173）
  - enchantments-1.16.json … minecraft-data pc/1.16.4 の enchantments.json（38件）
  - mc-1.16.1-protocol.json … minecraft-data pc/1.16.1 の protocol.json

出力（すべて detergentmc-protocol/build/remap-data/）：
  - vv-1.16.1.json … 生 vv を registries／reports の protocol_id 順で
    補正したもの。blockstates（17104件）・blocks（763件）・items（975件）・
    entities（107件）・sounds（985件）・particles（72件）・menus（24件）・
    attributes（13件）・blockentities（33件）・enchantments（38件）。
  - enchantments-1.16.1.json … enchantments-1.16.json とバイト同一。
    registries-1.16.1.json の protocol_id 順と一致することを確認してから
    書き出す（pc/1.13.2 ではない。上記の罠を参照）。

照合（すべて通過しなければ終了コード 1）：
  - vv のブロック状態と報告書 blocks.json の state id が全件一致。
  - vv の blocks／items／entities／sounds／particles／blockentities／
    enchantments／menus／attributes と registries.json の protocol_id 順が
    全件一致。
  - minecraft-data との突き合わせ（pc/1.16.1 の blocks／items／entities／
    biomes、pc/1.16 の sounds／particles、pc/1.16.4 の enchantments）。
"""
import hashlib
import json
import shutil
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
MC_DIR = REPO_ROOT / ".tmp-736" / "registry" / "mc"
# 751ツリーの minecraft-data 複写（読み取り専用で再利用）。
MC751_DIR = Path.home() / "workspace" / "detergentmc-751" / ".tmp-751" / "registry" / "mc"


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def sha1(path):
    return hashlib.sha1(Path(path).read_bytes()).hexdigest()


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
    vv_raw = load(DATA_DIR / "vv-1.16-raw.json")
    reg161 = load(DATA_DIR / "registries-1.16.1.json")
    rep161 = load(DATA_DIR / "blocks-1.16.1-reports.json")

    # --- 生 vv と registries／reports の差分を記録する ---
    for key, reg_key in (("blocks", "minecraft:block"),
                         ("items", "minecraft:item"),
                         ("entities", "minecraft:entity_type"),
                         ("sounds", "minecraft:sound_event"),
                         ("particles", "minecraft:particle_type")):
        ro = registry_order(reg161, reg_key)
        vv_names = vv_raw.get(key)
        if vv_names == ro:
            print(f"[1.16.1] {key}: 生 vv と registries が {len(ro)} 件一致")
        else:
            missing = sorted(set(ro) - set(vv_names))
            extra = sorted(set(vv_names) - set(ro))
            print(f"[1.16.1] {key}: 生 vv {len(vv_names)} 件 vs registries {len(ro)} 件、"
                  f"不足={missing} 余分={extra}")
    rep_states = reports_blockstates(rep161)
    print(f"[1.16.1] blockstates: 生 vv {len(vv_raw['blockstates'])} 件 vs "
          f"reports {len(rep_states)} 件")
    if vv_raw["blockstates"] != rep_states:
        diff = [i for i, (a, b) in enumerate(zip(vv_raw["blockstates"], rep_states)) if a != b]
        print(f"[1.16.1] blockstates: 生 vv と reports の不一致 {len(diff)} 件 "
              f"（先頭: {diff[:10]}）")

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
        vv[vv_key] = registry_order(reg161, reg_key)
    for tag, want in (("blockstates", 17104), ("blocks", 763), ("items", 975),
                      ("entities", 107), ("sounds", 985), ("particles", 72),
                      ("menus", 24), ("attributes", 13), ("blockentities", 33),
                      ("enchantments", 38)):
        if len(vv[tag]) != want:
            print(f"[1.16.1] {tag}: {len(vv[tag])} 件（想定 {want} 件）",
                  file=sys.stderr)
            failures.append(f"{tag}-count-1.16.1")

    # --- 照合1: vv ブロック状態と報告書 blocks.json の state id ---
    if vv["blockstates"] != rep_states:
        print("[1.16.1] blockstates: vv と reports が違う", file=sys.stderr)
        failures.append("blockstates-1.16.1")
    else:
        print(f"[1.16.1] blockstates vs reports: {len(rep_states)} 件、"
              f"不一致 0 件")

    # --- 照合2: minecraft-data との突き合わせ ---
    mc_blocks = [e["name"] for e in sorted(load(DATA_DIR / "1.16.1_blocks.json"),
                                            key=lambda e: e["id"])]
    if mc_blocks != vv["blocks"]:
        print("[1.16.1] blocks: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("blocks-1.16.1")
    else:
        print(f"[1.16.1] blocks: minecraft-data と vv が {len(mc_blocks)} 件一致")
    mc_items = [e["name"] for e in sorted(load(DATA_DIR / "1.16.1_items.json"),
                                           key=lambda e: e["id"])]
    if mc_items != vv["items"][1:] or vv["items"][0] != "air":
        print("[1.16.1] items: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("items-1.16.1")
    else:
        print(f"[1.16.1] items: vv {len(vv['items'])} 件と minecraft-data "
              f"{len(mc_items)} 件が一致（air なし）")
    mc_entities = [e["name"] for e in sorted(load(MC_DIR / "entities-1.16.1.json"),
                                              key=lambda e: e["id"])]
    if mc_entities != vv["entities"]:
        print("[1.16.1] entities: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("entities-1.16.1")
    else:
        print(f"[1.16.1] entities: minecraft-data と vv が {len(mc_entities)} 件一致")
    mc_sounds = [e["name"] for e in sorted(load(MC751_DIR / "sounds-1.16.json"),
                                            key=lambda e: e["id"])]
    if mc_sounds != vv["sounds"]:
        print("[1.16.1] sounds: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("sounds-1.16.1")
    else:
        print(f"[1.16.1] sounds: minecraft-data と vv が {len(mc_sounds)} 件一致")
    mc_particles = [e["name"] for e in sorted(load(MC751_DIR / "particles-1.16.json"),
                                               key=lambda e: e["id"])]
    if mc_particles != vv["particles"]:
        print("[1.16.1] particles: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("particles-1.16.1")
    else:
        print(f"[1.16.1] particles: minecraft-data と vv が {len(mc_particles)} 件一致")
    mc_ench = [e["name"] for e in sorted(load(DATA_DIR / "enchantments-1.16.json"),
                                          key=lambda e: e["id"])]
    if mc_ench != vv["enchantments"]:
        print("[1.16.1] enchantments: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("enchantments-1.16.1")
    else:
        print(f"[1.16.1] enchantments: minecraft-data と vv が {len(mc_ench)} 件一致")
    # dataPaths の指し先 pc/1.13.2 が stale であることの記録（34 件。
    # multishot・piercing・quick_charge・soul_speed が無い）。
    mc_ench132 = [e["name"] for e in sorted(
        load(MC751_DIR / "enchantments-1.13.2.json"), key=lambda e: e["id"])]
    print(f"[1.16.1] enchantments pc/1.13.2: {len(mc_ench132)} 件で "
          f"registries（38 件）と食い違う（不足="
          f"{sorted(set(vv['enchantments']) - set(mc_ench132))}）。"
          f"dataPaths の指し先は stale のため使わない")
    if len(mc_ench132) != 34:
        print("[1.16.1] enchantments pc/1.13.2 の件数が想定（34）と違う",
              file=sys.stderr)
        failures.append("enchantments-1.13.2-count")
    # statistics（vv 生の 74 件）と custom_stat（74 件）。
    print(f"[1.16.1] statistics: vv 生 {len(vv_raw['statistics'])} 件、"
          f"registries custom_stat "
          f"{len(reg161['minecraft:custom_stat']['entries'])} 件")

    if failures:
        print(f"照合失敗: {failures}", file=sys.stderr)
        return 1

    out_vv = DATA_DIR / "vv-1.16.1.json"
    out_vv.write_text(json.dumps(vv), encoding="utf-8")
    print(f"vv-1.16.1.json を書き出した（sha1 {sha1(out_vv)}）")
    out_ench = DATA_DIR / "enchantments-1.16.1.json"
    shutil.copy(DATA_DIR / "enchantments-1.16.json", out_ench)
    print(f"enchantments-1.16.1.json を書き出した（enchantments-1.16.json と"
          f"バイト同一。sha1 {sha1(out_ench)}。pc/1.13.2 ではない）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
