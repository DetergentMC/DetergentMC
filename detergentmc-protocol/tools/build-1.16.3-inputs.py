#!/usr/bin/env python3
"""1.16.3（753）の生成入力を実バニラの報告書と minecraft-data から作る。

tools/build-1.16-inputs.py（754用）が雛形。結論から言うと 1.16.3 の
レジストリは 1.16.5 とバイト単位で完全一致するため（STAGE-A-753-NOTES.md
§7.4。cmp で確認済み）、補正済み vv-1.16.json は 1.16.3 にもそのまま
適用できる。このスクリプトはそれを実測で確認し、753 側の入力として
vv-1.16.3.json（vv-1.16.json とバイト同一のコピー）と
enchantments-1.16.3.json（pc/1.16.4 順。後述の罠のため）を書き出す。

dataPaths.json（PrismarineJS/minecraft-data master。2026-11-16 取得。
.tmp-753/registry/dataPaths.json に保存）の 1.16.3 の指し先（実測）：
  - protocol・blocks・items・biomes・entities：pc/1.16.2
  - enchantments：pc/1.13.2（※罠。下記参照）
  - particles・attributes：pc/1.16、sounds：キー無し
  - version：pc/1.16.3
  754 の前例と同じく pc/1.16.2 が正しい。pc/1.16/protocol.json は
  1.16.0 のもので使わない（754 §11.2 の罠と同じ）。

罠（1.16.3 の enchantments は dataPaths の指し先が古い）：
  dataPaths.json で 1.16.3 の enchantments は pc/1.13.2 を指すが、
  pc/1.13.2/enchantments.json は 34 件しかなく、1.16 で追加された
  multishot・piercing・quick_charge・soul_speed の 4 件が無い。
  実バニラ 1.16.3 の registries.json は 38 件で、順序は pc/1.16.4
  （enchantments-1.16.json）と完全一致する。つまり minecraft-data の
  1.16.3 向け enchantments.json は更新されておらず、dataPaths の
  指し先は stale である。権威あるのは実バニラの registries.json の
  protocol_id 順のため、753 側のエンチャント一覧は pc/1.16.4 順
  （＝registries-1.16.3.json の順）を使う。pc/1.13.2 のファイルは
  .tmp-753/registry/mc/enchantments-1.13.2.json に残してある。
  同じく sounds は dataPaths の 1.16.3 にキーが無いが、pc/1.16 の
  sounds.json（985 件）は registries から piglin_brute 系 7 件を
  除いたものと一致する（754 と同じ）。

入力（detergentmc-protocol/build/remap-data/）：
  - vv-1.16.json … ViaVersion mapping-1.16.json を実バニラ 1.16.5 の
    registries／reports の順序で補正したもの（754 用。build-1.16-inputs.py）
  - registries-1.16.3.json … 実バニラ 1.16.3 jar の reports/registries.json
  - blocks-1.16.3-reports.json … 実バニラ 1.16.3 jar の reports/blocks.json
  - registries-1.16.5.json／blocks-1.16.5-reports.json … 1.16.5 版（同一性の確認用）
  - 1.16_blocks.json … minecraft-data pc/1.16.2 の blocks.json（763件）
  - 1.16_items.json … minecraft-data pc/1.16.2 の items.json（975件。air無し）
  - enchantments-1.16.json … minecraft-data pc/1.16.4 の enchantments.json（38件）
  - biomes-1.16.json … minecraft-data pc/1.16.2 の biomes.json（79件。id 0〜173）

出力（すべて detergentmc-protocol/build/remap-data/）：
  - vv-1.16.3.json … vv-1.16.json とバイト同一。blockstates（17112件）・
    blocks（763件）・items（976件）・entities（108件）・sounds（992件）・
    particles（72件）・menus（24件）・attributes（13件）・blockentities
    （33件）・enchantments（38件）が registries-1.16.3.json／
    blocks-1.16.3-reports.json の protocol_id 順と全件一致することを
    確認してから書き出す。
  - enchantments-1.16.3.json … enchantments-1.16.json とバイト同一。
    registries-1.16.3.json の protocol_id 順と一致することを確認してから
    書き出す（pc/1.13.2 ではない。上記の罠を参照）。

照合（すべて通過しなければ終了コード 1）：
  - registries-1.16.3.json と registries-1.16.5.json がバイト同一。
  - blocks-1.16.3-reports.json と blocks-1.16.5-reports.json がバイト同一。
  - vv-1.16.json の各リストと registries-1.16.3.json の protocol_id 順が
    全件一致。ブロック状態は reports とも全件一致。
  - minecraft-data との突き合わせ（754 と同じ。pc/1.16.2 の
    blocks／items／entities／biomes、pc/1.16 の sounds／particles、
    pc/1.16.4 の enchantments）。
"""
import hashlib
import json
import shutil
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
MC_DIR = REPO_ROOT / ".tmp-753" / "registry" / "mc"


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
    vv = load(DATA_DIR / "vv-1.16.json")
    reg163 = load(DATA_DIR / "registries-1.16.3.json")
    rep163 = load(DATA_DIR / "blocks-1.16.3-reports.json")

    # --- 1.16.3 と 1.16.5 のレポートがバイト同一であること ---
    for name in ("registries-1.16.3.json", "blocks-1.16.3-reports.json"):
        other = name.replace("1.16.3", "1.16.5")
        same = (DATA_DIR / name).read_bytes() == (DATA_DIR / other).read_bytes()
        print(f"[1.16.3] {name} == {other}（バイト同一）: {same}")
        if not same:
            failures.append(f"{name}-differs-from-1.16.5")

    # --- vv-1.16.json の各リストと registries-1.16.3.json の照合 ---
    for key, reg_key in (("blocks", "minecraft:block"),
                         ("items", "minecraft:item"),
                         ("entities", "minecraft:entity_type"),
                         ("sounds", "minecraft:sound_event"),
                         ("particles", "minecraft:particle_type"),
                         ("menus", "minecraft:menu"),
                         ("attributes", "minecraft:attribute"),
                         ("blockentities", "minecraft:block_entity_type"),
                         ("enchantments", "minecraft:enchantment")):
        ro = registry_order(reg163, reg_key)
        vv_names = vv.get(key)
        if vv_names == ro:
            print(f"[1.16.3] {key}: vv-1.16.json と registries が {len(ro)} 件一致")
        else:
            missing = sorted(set(ro) - set(vv_names))
            extra = sorted(set(vv_names) - set(ro))
            print(f"[1.16.3] {key}: vv {len(vv_names)} 件 vs registries {len(ro)} 件、"
                  f"不足={missing} 余分={extra}", file=sys.stderr)
            failures.append(f"{key}-1.16.3")
    rep_states = reports_blockstates(rep163)
    if vv["blockstates"] == rep_states:
        print(f"[1.16.3] blockstates vs reports: {len(rep_states)} 件、不一致 0 件")
    else:
        print("[1.16.3] blockstates: vv と reports が違う", file=sys.stderr)
        failures.append("blockstates-1.16.3")
    for tag, want in (("blockstates", 17112), ("blocks", 763), ("items", 976),
                      ("entities", 108), ("sounds", 992), ("particles", 72),
                      ("menus", 24), ("attributes", 13), ("blockentities", 33),
                      ("enchantments", 38)):
        if len(vv[tag]) != want:
            print(f"[1.16.3] {tag}: {len(vv[tag])} 件（想定 {want} 件）",
                  file=sys.stderr)
            failures.append(f"{tag}-count-1.16.3")

    # --- minecraft-data との突き合わせ（754 と同じ。pc/1.16.2・pc/1.16・pc/1.16.4）---
    mc_blocks = [e["name"] for e in sorted(load(DATA_DIR / "1.16_blocks.json"),
                                            key=lambda e: e["id"])]
    if mc_blocks != vv["blocks"]:
        print("[1.16.3] blocks: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("blocks-1.16.3")
    else:
        print(f"[1.16.3] blocks: minecraft-data と vv が {len(mc_blocks)} 件一致")
    mc_items = [e["name"] for e in sorted(load(DATA_DIR / "1.16_items.json"),
                                           key=lambda e: e["id"])]
    if mc_items != vv["items"][1:] or vv["items"][0] != "air":
        print("[1.16.3] items: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("items-1.16.3")
    else:
        print(f"[1.16.3] items: vv {len(vv['items'])} 件と minecraft-data "
              f"{len(mc_items)} 件が一致（air なし）")
    mc_entities = [e["name"] for e in sorted(load(MC_DIR / "entities-1.16.2.json"),
                                              key=lambda e: e["id"])]
    if mc_entities != vv["entities"]:
        print("[1.16.3] entities: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("entities-1.16.3")
    else:
        print(f"[1.16.3] entities: minecraft-data と vv が {len(mc_entities)} 件一致")
    mc_sounds = [e["name"] for e in sorted(load(MC_DIR / "sounds-1.16.json"),
                                            key=lambda e: e["id"])]
    reg_sounds = registry_order(reg163, "minecraft:sound_event")
    if mc_sounds != [n for n in reg_sounds if "piglin_brute" not in n]:
        print("[1.16.3] sounds: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("sounds-1.16.3")
    else:
        print(f"[1.16.3] sounds: minecraft-data {len(mc_sounds)} 件は "
              f"registries から piglin_brute 系 7 件を除いたものと一致")
    mc_particles = [e["name"] for e in sorted(load(MC_DIR / "particles-1.16.json"),
                                               key=lambda e: e["id"])]
    if mc_particles != vv["particles"]:
        print("[1.16.3] particles: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("particles-1.16.3")
    else:
        print(f"[1.16.3] particles: minecraft-data と vv が {len(mc_particles)} 件一致")
    mc_ench = [e["name"] for e in sorted(load(DATA_DIR / "enchantments-1.16.json"),
                                          key=lambda e: e["id"])]
    if mc_ench != vv["enchantments"]:
        print("[1.16.3] enchantments: minecraft-data と vv が違う", file=sys.stderr)
        failures.append("enchantments-1.16.3")
    else:
        print(f"[1.16.3] enchantments: minecraft-data と vv が {len(mc_ench)} 件一致")
    # dataPaths の指し先 pc/1.13.2 が stale であることの記録（34 件。
    # multishot・piercing・quick_charge・soul_speed が無い）。
    mc_ench132 = [e["name"] for e in sorted(
        load(MC_DIR / "enchantments-1.13.2.json"), key=lambda e: e["id"])]
    print(f"[1.16.3] enchantments pc/1.13.2: {len(mc_ench132)} 件で "
          f"registries（38 件）と食い違う（不足="
          f"{sorted(set(vv['enchantments']) - set(mc_ench132))}）。"
          f"dataPaths の指し先は stale のため使わない")
    if len(mc_ench132) != 34:
        print("[1.16.3] enchantments pc/1.13.2 の件数が想定（34）と違う",
              file=sys.stderr)
        failures.append("enchantments-1.13.2-count")
    # statistics（vv 生の 74 件）と custom_stat（74 件）。
    print(f"[1.16.3] statistics: vv 生 {len(vv.get('statistics', []))} 件、"
          f"registries custom_stat "
          f"{len(reg163['minecraft:custom_stat']['entries'])} 件")

    if failures:
        print(f"照合失敗: {failures}", file=sys.stderr)
        return 1

    out_vv = DATA_DIR / "vv-1.16.3.json"
    shutil.copy(DATA_DIR / "vv-1.16.json", out_vv)
    print(f"vv-1.16.3.json を書き出した（vv-1.16.json とバイト同一。sha1 {sha1(out_vv)})")
    out_ench = DATA_DIR / "enchantments-1.16.3.json"
    shutil.copy(DATA_DIR / "enchantments-1.16.json", out_ench)
    print(f"enchantments-1.16.3.json を書き出した（enchantments-1.16.json と"
          f"バイト同一。sha1 {sha1(out_ench)}。pc/1.13.2 ではない）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
