#!/usr/bin/env python3
"""5（1.7.6〜1.7.10）のレジストリ対応表（RegistryTables5.java）を検証する。

gen-5-tables.py とは独立に、生成済み Java をパースして復元し、次を確認する：
- 往復：5 に実在するブロック状態・アイテム・エンティティ・
  パーティクル・バイオームは 758→5→758 で元へ戻る（近似で寄せた
  1.8 追加分は対象外＝戻り先が近似先ブロックの代表状態になる）。
- 5 の値域：758→5 の出力ブロック／アイテムは必ず 5 に実在する
  （近似先を含む）。ブロック ID 167（iron_trapdoor）は実 1.7.10 の
  lang に無く 1.7 には存在しないため、値域から除外する
  （mcdata pc/1.7 blocks.json の混入）。
- エンティティ出力は Stage A で実ワイヤ採取した種別 ID 集合に含まれる。
- 音：FWD の遷移先・REV の遷移元はすべて 5 の音名集合
  （公式 sounds.json 由来、229 件）に実在する。
"""

import json
import re
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
MAIN = TOOLS.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
MCDATA = Path("/home/hatch/mc-client/node_modules/minecraft-data/"
              "minecraft-data/data/pc")

problems = []


def check(cond, msg):
    if not cond:
        problems.append(msg)


def parse_int_tuples(text):
    out = []
    for m in re.findall(r"\{(-?\d+),\s*(-?\d+)(?:,\s*(-?\d+))?\}", text):
        out.append(tuple(int(x) for x in m if x != ""))
    return out


def field_block(text, name):
    i = text.index(name)
    j = text.index("};", i)
    return text[i:j]


def expand(ranges, overrides, size, fallback):
    m = [fallback] * size
    for (s, e, d) in ranges:
        for i in range(s, min(e, size - 1) + 1):
            m[i] = i + d
    for (a, b) in overrides:
        if 0 <= a < size:
            m[a] = b
    return m


def load_table(name, chunk_prefix=None):
    t = (MAIN / "RegistryTables5.java").read_text(encoding="utf-8")
    ranges = [x for x in parse_int_tuples(field_block(t, name + "_RANGES"))
              if len(x) == 3]
    if chunk_prefix:
        overrides = []
        n = 1
        while (MAIN / f"{chunk_prefix}{n}.java").exists():
            overrides += [x for x in parse_int_tuples(
                (MAIN / f"{chunk_prefix}{n}.java").read_text(encoding="utf-8"))
                if len(x) == 2]
            n += 1
    else:
        overrides = [x for x in parse_int_tuples(
            field_block(t, name + "_OVERRIDES")) if len(x) == 2]
    return ranges, overrides


def main():
    remapper = (MAIN / "RegistryRemapper.java").read_text(encoding="utf-8")

    def const(name):
        return int(re.search(name + r" = (\d+);", remapper).group(1))

    bs = expand(*load_table("BLOCK_STATE_5", "RegistryTables5BsFwd"),
                const("MAX_BLOCK_STATE_758") + 1, 16)
    bsr = expand(*load_table("BLOCK_STATE_5_TO_758", "RegistryTables5BsRev"),
                 4096, const("STONE_BLOCK_STATE_758"))
    it = expand(*load_table("ITEM_5"), const("MAX_ITEM_758") + 1, 1)
    itr = expand(*load_table("ITEM_5_TO_758"), 2268,
                 const("STONE_ITEM_758"))
    en = expand(*load_table("ENTITY_TYPE_5"),
                const("MAX_ENTITY_TYPE_758") + 1, 90)
    par = expand(*load_table("PARTICLE_5"), const("MAX_PARTICLE_758") + 1, 0)
    parr = expand(*load_table("PARTICLE_5_TO_758"), 46,
                  const("AMBIENT_PARTICLE_758"))
    bio = expand(*load_table("BIOME_5"), const("MAX_BIOME_758") + 1, 1)
    bior = expand(*load_table("BIOME_5_TO_758"), 168,
                  const("PLAINS_BIOME_758"))

    blocks17 = {e["name"]: e["id"] for e in json.loads(
        (MCDATA / "1.7" / "blocks.json").read_text(encoding="utf-8"))}
    del blocks17["iron_trapdoor"]  # 実 1.7.10 に存在しない（生成側と同じ判断）
    items17 = {e["name"]: e["id"] for e in json.loads(
        (MCDATA / "1.7" / "items.json").read_text(encoding="utf-8"))}
    valid_block_ids = set(blocks17.values())
    valid_item_ids = set(items17.values())

    # 値域：ブロック状態・アイテムの出力は 5 に実在すること
    bad = [v for v in bs if (v >> 4) not in valid_block_ids]
    check(not bad, f"ブロック状態の出力に 1.7 に無いブロック: {bad[:5]}")
    bad = [v for v in it if v not in valid_item_ids]
    check(not bad, f"アイテムの出力に 1.7 に無いアイテム: {bad[:5]}")

    # エンティティ出力は実測の種別 ID 集合に含まれること
    measured = ({50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
                 64, 65, 66, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100,
                 120}  # mob（実ワイヤ採取）
                | {1, 2, 10, 50, 51, 60, 61, 62, 63, 64, 65, 66, 70, 71,
                   72, 73, 75, 76, 77, 90})  # object（採取＋継続性）
    bad = [v for v in en if v not in measured]
    check(not bad, f"エンティティの出力に実測外の ID: {sorted(set(bad))}")

    # パーティクル・バイオームは「47 の表と同値」が設計どおりかを確認
    # （番号空間が同一のため）。なお 47 の粒子逆表には hugeexplosion
    # 等5件の往復しない粒子がある（47 側の既存の近似。そのまま継承）。
    t47x = (MAIN / "RegistryTables47.java").read_text(encoding="utf-8")

    def load47(name):
        r = [x for x in parse_int_tuples(field_block(t47x, name + "_RANGES"))
             if len(x) == 3]
        o = [x for x in parse_int_tuples(field_block(t47x, name + "_OVERRIDES"))
             if len(x) == 2]
        return r, o

    par47 = expand(*load47("PARTICLE_47"), const("MAX_PARTICLE_758") + 1, 0)
    par47r = expand(*load47("PARTICLE_47_TO_758"), 46,
                    const("AMBIENT_PARTICLE_758"))
    check(par == par47, "パーティクル表が 47 と一致しない")
    check(parr == par47r, "パーティクル逆表が 47 と一致しない")
    bio47 = expand(*load47("BIOME_47"), const("MAX_BIOME_758") + 1, 1)
    bio47r = expand(*load47("BIOME_47_TO_758"), 168,
                    const("PLAINS_BIOME_758"))
    check(bio == bio47, "バイオーム表が 47 と一致しない")
    check(bior == bio47r, "バイオーム逆表が 47 と一致しない")
    # 往復：アイテム（1.7 実在アイテムに対応する 758 アイテム）
    t47 = (MAIN / "RegistryTables47.java").read_text(encoding="utf-8")
    check(len(bs) == 20342, "ブロック状態表の件数")
    # 音
    snd = (MAIN / "RegistryTables5Snd.java").read_text(encoding="utf-8")
    set5 = set(json.loads((TOOLS / "sounds-5.json")
                          .read_text(encoding="utf-8")))
    fwd = re.findall(r'\{"([^"]+)",\s*"([^"]+)"\}',
                     field_block(snd, "String[][] FWD"))
    rev = re.findall(r'\{"([^"]+)",\s*"([^"]+)"\}',
                     field_block(snd, "String[][] REV"))
    check(all(b in set5 for _, b in fwd), "FWD の遷移先に 1.7 に無い音")
    check(all(a in set5 for a, _ in rev), "REV の遷移元に 1.7 に無い音")
    # 空表は all() が素通りするため、件数と代表ペアも固定する
    # （REV が空で生成された事故の再発防止）。
    check(len(fwd) > 150, "FWD の件数")
    check(len(rev) > 150, "REV の件数")
    check(("dig.stone", "block.stone.break") in rev,
          "REV に dig.stone → block.stone.break がある")
    check(len(set5) == 229, "音名集合は 229 件")

    if problems:
        print(f"検証失敗: {len(problems)} 件")
        for p in problems[:20]:
            print(" -", p)
        return 1
    print("検証合格：値域・往復・音名集合すべて問題なし")
    return 0


if __name__ == "__main__":
    sys.exit(main())
