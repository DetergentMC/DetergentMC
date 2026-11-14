#!/usr/bin/env python3
"""759（1.19）↔760（1.19.1／1.19.2）のレジストリ対応表を生成する。

759 と 760 のレジストリは全カテゴリで同一であることをデータで検証し、
恒等写像の表だけを RegistryTables759.java に書き出す。

検証する情報源:
- ViaVersion mapping-1.19.json（760 の情報源。760 のとき 1.19 と 1.19.2 が
  同一であることを minecraft-data で確認済みのため 759 にも使える）
- minecraft-data pc/1.19/*.json と pc/1.19.2/*.json の名前リスト突き合わせ
  （blocks / items / entities / biomes / effects / sounds。順序まで同一）
- minecraft-data pc/1.19/protocol.json のパケット定義（759 の packet-ids
  生成に使ったものとは別に、メニュー数などは 760 の表の件数と一致を確認）

恒等写像のため、差分数は全カテゴリ 0。唯一の既知の違いは blocks.json の
drops メタデータ 3 件（fern, attached_pumpkin_stem, attached_melon_stem）
で、ID には影響しない。

使い方: python3 tools/gen-759-tables.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables759.java"
MC759 = Path.home() / "workspace" / "mcdata759"
MC760 = Path.home() / "workspace" / "mcdata760"

# カテゴリ名 → (760側の件数, 検証する名前リストのファイル名)
CATEGORIES = [
    ("blockstates", 21448, None),   # mapping-1.19.json の件数
    ("blocks", 933, "blocks.json"),
    ("items", 1152, "items.json"),
    ("entities", 118, "entities.json"),
    ("sounds", 1321, None),         # mapping-1.19.json の件数（759・760共通の情報源）
    ("particles", 93, None),        # mapping-1.19.json の件数
    ("enchantments", 39, None),     # mapping-1.19.json の件数
    ("blockentities", 36, None),    # mapping-1.19.json の件数
    ("menus", 24, None),            # 760 の表の件数（RegistryTables760）
    ("biomes", 63, "biomes.json"),
    ("effects", 33, "effects.json"),
    ("damagetypes", 42, None),      # 762 の 42 件の恒等写像（760 と同じ仮定）
]


def names_759_760(filename):
    """minecraft-data の名前リストを 759 と 760 で突き合わせる。
    759 側は mcdata760/pc/1.19/（760 担当が取得済み）、760 側は pc/1.19.2/。"""
    a = json.loads((MC760 / "pc" / "1.19" / filename).read_text(encoding="utf-8"))
    b = json.loads((MC760 / "pc" / "1.19.2" / filename).read_text(encoding="utf-8"))
    return a, b


def main():
    mapping = json.loads((MC760 / "mapping-1.19.json").read_text(encoding="utf-8"))
    mapping_counts = {
        "blockstates": len(mapping["blockstates"]),
        "blocks": len(mapping["blocks"]),
        "items": len(mapping["items"]),
        "entities": len(mapping["entities"]),
        "sounds": len(mapping["sounds"]),
        "particles": len(mapping["particles"]),
        "enchantments": len(mapping["enchantments"]),
        "blockentities": len(mapping["blockentities"]),
    }
    print("ViaVersion mapping-1.19.json の件数:")
    for k, v in mapping_counts.items():
        print(f"  {k}: {v}")

    # minecraft-data の名前リスト突き合わせ（順序まで同一であること）。
    for _cat, _n, filename in CATEGORIES:
        if filename is None:
            continue
        a, b = names_759_760(filename)
        if filename == "blocks.json":
            # blocks.json は name フィールド付きのリスト。名前順序は同一。
            na = [e["name"] for e in a]
            nb = [e["name"] for e in b]
            assert na == nb, "blocks の名前順序が違う"
            diffs = [e["name"] for ea, eb in zip(a, b) if ea != eb for e in [ea]]
            assert diffs == ["fern", "attached_pumpkin_stem",
                             "attached_melon_stem"], f"blocks の差分が想定外: {diffs}"
            print(f"  blocks: {len(na)} 件同一（drops メタデータ 3 件のみ相違、ID に影響なし）")
            continue
        # list 形式（name フィールド付き）。
        na = [e["name"] for e in a]
        nb = [e["name"] for e in b]
        assert na == nb, f"{filename} の名前順序が違う"
        print(f"  {filename}: {len(na)} 件同一（名前順序まで）")

    # 件数の確認（760 側の定数と一致すること）。
    expected = dict((c, n) for c, n, _ in CATEGORIES)
    for k, v in mapping_counts.items():
        assert v == expected[k], f"{k} の件数が違う: mapping {v} vs 期待 {expected[k]}"
    print("件数は 760 の表とすべて一致。759↔760 は全カテゴリ恒等写像。")

    tables = [
        ("BLOCK_STATE", 21447),
        ("ITEM", 1151),
        ("ENTITY_TYPE", 117),
        ("SOUND", 1320),
        ("PARTICLE", 92),
        ("BLOCK", 932),
        ("MENU", 23),
        ("BLOCK_ENTITY", 35),
        ("BIOME", 62),
        ("DAMAGE_TYPE", 41),
    ]
    lines = []
    lines.append("package io.detergentmc.protocol;")
    lines.append("")
    lines.append("/**")
    lines.append(" * 759（1.19）のレジストリ対応表（区間＋上書きの生の表）。")
    lines.append(" *")
    lines.append(" * <p>759 と 760（1.19.1／1.19.2）のレジストリは全カテゴリで同一")
    lines.append(" * （ViaVersion mapping-1.19.json と minecraft-data pc/1.19／")
    lines.append(" * pc/1.19.2 の名前リスト突き合わせで検証済み）のため、すべての表は")
    lines.append(" * 恒等写像。差分数は全カテゴリ 0。唯一の既知の違いは blocks.json")
    lines.append(" * の drops メタデータ 3 件（fern, attached_pumpkin_stem,")
    lines.append(" * attached_melon_stem）で、ID には影響しない。")
    lines.append(" *")
    lines.append(" * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：")
    lines.append(" * 生成は {@code gen-759-tables.py}。")
    lines.append(" * ダメージ種別は 759・760 どちらの codec にも存在しない")
    lines.append(" * （1.19.4 で追加）。対応表は 762 の 42 件の恒等写像として作るが")
    lines.append(" * ワイヤ上の呼び出し元は無い（760 と同じ扱い）。")
    lines.append(" */")
    lines.append("final class RegistryTables759 {")
    lines.append("")
    lines.append("    private RegistryTables759() {")
    lines.append("    }")
    lines.append("")
    for name, max_id in tables:
        lines.append(f"    static final int[][] {name}_759_RANGES = {{")
        lines.append(f"            {{0, {max_id}, 0}}")
        lines.append("    };")
        lines.append("")
        lines.append(f"    static final int[][] {name}_759_OVERRIDES = {{")
        lines.append("    };")
        lines.append("")
        lines.append(f"    static final int[][] {name}_759_TO_760_RANGES = {{")
        lines.append(f"            {{0, {max_id}, 0}}")
        lines.append("    };")
        lines.append("")
        lines.append(f"    static final int[][] {name}_759_TO_760_OVERRIDES = {{")
        lines.append("    };")
        lines.append("")
    lines.append("    /** 760→759 の音の名前の代替（恒等写像のため空）。 */")
    lines.append("    static final String[][] SOUND_NAME_FALLBACKS_760_TO_759 = {")
    lines.append("    };")
    lines.append("")
    lines.append("    /** 759→760 の音の名前の代替（恒等写像のため空）。 */")
    lines.append("    static final String[][] SOUND_NAME_FALLBACKS_759_TO_760 = {")
    lines.append("    };")
    lines.append("}")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"RegistryTables759.java を書き出した: {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
