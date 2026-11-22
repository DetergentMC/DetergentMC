#!/usr/bin/env python3
"""393（1.13）↔758（1.18.2）のレジストリ対応表を生成する。

404（1.13.2）の表（gen-404-tables.py が生成した tables-404.json）と、
393⇔404 のレジストリ対応を合成して作る。393⇔404 の対応は名前で
突き合わせ、すべて実データで裏付ける（推測で埋めない）：

  - 393 側のブロック／ブロック状態／アイテムは、実バニラ 1.13 jar の
    data-generator ダンプ（~/workspace/legacy-prep-393/gen-1.13）が正。
    404 側は実バニラ 1.13.2 jar のダンプ（~/workspace/mcdata404/
    datagen1132）が正。
  - ブロック状態 393→404：同じブロック名で、393 にあるプロパティが
    すべて一致し、404 で増えたプロパティ（tnt の unstable、サンゴ類と
    conduit の waterlogged）は 404 の既定状態の値であるもの。
    全 8,582 件が一意に決まることを assert する。
  - ブロック状態 404→393：追加プロパティを落として一致するもの。
    dead coral 5 種（1.13.1 で追加）は対応する生きたサンゴへ。
    全 8,599 件が決まることを assert する。
  - アイテム・ブロック（種別）も名前で対応。404 にしか無い
    dead coral は生きたサンゴへ。
  - エンティティ（mob 95）・バイオーム（73）・効果（30）は
    minecraft-data で 1.13⇔1.13.2 が完全一致（Stage A で確認）。
    音（662）とパーティクル（50）は ViaVersion mapping-1.13 が
    1.13 世代共通の表で、404 と同じものを使う。ブロックエンティティ
    （25）と絵画（26）も 404 と同一（絵画は実 1.13 jar の定数プール
    順で確認）。そのため、これらのカテゴリは 404 の表をそのまま使う。
  - ダメージ種別は 42 件の恒等写像（404 と同じ）。

合成の向き：fwd = 758→393（= 404 経由：758→404 の表の後に
404→393 対応を適用）、rev = 393→758（393→404 対応の後に
404→758 の表を適用）。

使い方：
  python3 tools/gen-393-tables.py [--tables404 FILE] [--out FILE]
                                  [--json FILE]
  --tables404 を省略すると ~/workspace/mcdata404/tables-404.json、
  --out を省略すると RegistryTables393.java を標準出力に出す。
"""

import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

# gen-498-tables.py をモジュールとして読み込む（区間圧縮と Java 出力）。
_spec = importlib.util.spec_from_file_location(
    "gen498", Path(__file__).resolve().parent / "gen-498-tables.py")
g = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(g)

ranges_overrides = g.ranges_overrides
java_table = g.java_table
parse_state = g.parse_state
fmt_state = g.fmt_state

DATA404 = Path("/home/hatch/workspace/mcdata404")
GEN393 = Path("/home/hatch/workspace/legacy-prep-393/gen-1.13")

# 1.13.1 でプロパティが増えたブロックと、その追加プロパティ。
ADDED_PROPS = {
    "tnt": {"unstable"},
    "tube_coral": {"waterlogged"}, "brain_coral": {"waterlogged"},
    "bubble_coral": {"waterlogged"}, "fire_coral": {"waterlogged"},
    "horn_coral": {"waterlogged"}, "conduit": {"waterlogged"},
}
DEAD_CORAL = {
    "dead_tube_coral": "tube_coral", "dead_brain_coral": "brain_coral",
    "dead_bubble_coral": "bubble_coral", "dead_fire_coral": "fire_coral",
    "dead_horn_coral": "horn_coral",
}


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def load_blocks(reports_dir):
    """datagen の blocks.json から（ブロック名リスト、状態リスト、既定状態）。"""
    dg = load(Path(reports_dir) / "blocks.json")
    blocks = []
    states = [None] * sum(len(b["states"]) for b in dg.values())
    defaults = {}
    last_max = -1
    for key, blk in dg.items():
        name = key.split(":", 1)[1]
        blocks.append(name)
        ids = [st["id"] for st in blk["states"]]
        assert min(ids) > last_max, f"ブロック {name} の状態IDが登録順でない"
        last_max = max(ids)
        for st in blk["states"]:
            s = fmt_state(name, {k: str(v) for k, v
                                 in st.get("properties", {}).items()})
            assert states[st["id"]] is None
            states[st["id"]] = s
            if st.get("default"):
                defaults[name] = s
    assert all(s is not None for s in states)
    return blocks, states, defaults


def load_items(reports_dir):
    dg = load(Path(reports_dir) / "items.json")
    return [k.split(":", 1)[1] for k, _ in
            sorted(dg.items(), key=lambda kv: kv[1]["protocol_id"])]


def build_state_maps(states393, states404, def404):
    """393⇔404 のブロック状態対応（全件 assert 付き）。"""
    by_name404 = {}
    for i, s in enumerate(states404):
        by_name404.setdefault(parse_state(s)[0], []).append(i)
    idx393 = {s: i for i, s in enumerate(states393)}

    fwd = {}  # 393 -> 404
    for sid, s in enumerate(states393):
        name, props = parse_state(s)
        _, dprops = parse_state(def404[name])
        cands = []
        for cid in by_name404[name]:
            _, cp = parse_state(states404[cid])
            if all(cp.get(k) == v for k, v in props.items()) and \
                    all(cp[k] == dprops[k] for k in cp if k not in props):
                cands.append(cid)
        assert len(cands) == 1, f"393 状態 {sid}（{s}）の対応が {len(cands)} 件"
        fwd[sid] = cands[0]

    rev = {}  # 404 -> 393
    for sid, s in enumerate(states404):
        name, props = parse_state(s)
        if name in DEAD_CORAL:
            rev[sid] = idx393[DEAD_CORAL[name]]
            continue
        keep = {k: v for k, v in props.items()
                if k not in ADDED_PROPS.get(name, set())}
        t = fmt_state(name, keep)
        assert t in idx393, f"404 状態 {sid}（{s}）の対応先 {t} が無い"
        rev[sid] = idx393[t]
    assert len(fwd) == len(states393) and len(rev) == len(states404)
    return fwd, rev


def build_name_maps(list393, list404, dead_map):
    """名前ベースの対応（アイテム・ブロック種別用）。"""
    idx404 = {n: i for i, n in enumerate(list404)}
    idx393 = {n: i for i, n in enumerate(list393)}
    fwd = {}
    for i, n in enumerate(list393):
        assert n in idx404, f"393 の {n} が 404 に無い"
        fwd[i] = idx404[n]
    rev = {}
    for i, n in enumerate(list404):
        if n in dead_map:
            rev[i] = idx393[dead_map[n]]
        else:
            assert n in idx393, f"404 の {n} の対応先が決まらない"
            rev[i] = idx393[n]
    return fwd, rev


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tables404", type=Path,
                        default=DATA404 / "tables-404.json")
    parser.add_argument("--gen393", type=Path, default=GEN393)
    parser.add_argument("--data404", type=Path, default=DATA404)
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--json", type=Path, default=None)
    args = parser.parse_args()

    t404 = load(args.tables404)

    # --- 393⇔404 の対応 ---
    blocks393, states393, _ = load_blocks(args.gen393 / "generated" / "reports")
    blocks404, states404, def404 = load_blocks(
        args.data404 / "datagen1132" / "reports")
    assert len(blocks393) == 593 and len(states393) == 8582
    assert len(blocks404) == 598 and len(states404) == 8599
    print(f"393: ブロック {len(blocks393)} 種・状態 {len(states393)} 件、"
          f"404: ブロック {len(blocks404)} 種・状態 {len(states404)} 件"
          f"（いずれも実 jar ダンプ）。")
    bs_fwd393, bs_rev393 = build_state_maps(states393, states404, def404)
    print(f"ブロック状態の対応：393→404 {len(bs_fwd393)} 件・"
          f"404→393 {len(bs_rev393)} 件すべて一意に確定。")

    items393 = load_items(args.gen393 / "generated" / "reports")
    items404 = load_items(args.data404 / "datagen1132" / "reports")
    assert len(items393) == 785 and len(items404) == 790
    item_fwd393, item_rev393 = build_name_maps(items393, items404, DEAD_CORAL)
    blk_fwd393, blk_rev393 = build_name_maps(blocks393, blocks404, DEAD_CORAL)
    print(f"アイテムの対応：393 {len(items393)} 件・404 {len(items404)} 件、"
          f"ブロック種別も名前で全件確定。")

    # --- 代替先の確認 ---
    assert states393[1] == "stone" and states393[0] == "air"
    assert items393[1] == "stone" and blocks393[1] == "stone"
    mc_ents = load(args.data404 / "pc" / "1.13.2" / "entities.json")
    ent_rename = {"fireworks_rocket": "firework_rocket",
                  "commandblock_minecart": "command_block_minecart",
                  "iron_golem}": "iron_golem"}
    entities393 = [ent_rename.get(e["name"], e["name"])
                   for e in mc_ents if e["type"] == "mob"]
    assert len(entities393) == 95
    pig393 = entities393.index("pig")
    vv113 = load(args.data404 / "mapping-1.13.json")
    stone_break_393 = vv113["sounds"].index("block.stone.break")
    biomes393 = {b["name"]: b["id"]
                 for b in load(args.data404 / "pc" / "1.13.2" / "biomes.json")}
    plains393 = biomes393["plains"]
    vv758 = load(args.data404 / "mapping-1.18.json")
    pig758 = vv758["entities"].index("pig")
    stone_break_758 = vv758["sounds"].index("block.stone.break")
    biomes758 = {b["name"]: b["id"]
                 for b in load(args.data404 / "pc" / "1.18" / "biomes.json")}
    print(f"代替先：pig393={pig393}、石の破壊音393={stone_break_393}、"
          f"平原393={plains393}（404 と同じ値になることを確認）。")

    # --- 合成 ---
    def compose(name, to393_from404, from393_to404, n393):
        f404, r404 = t404[name]["fwd"], t404[name]["rev"]
        fwd = [to393_from404[x] for x in f404]
        rev = [r404[from393_to404[j]] for j in range(n393)]
        return fwd, rev

    bs_f, bs_r = compose("BLOCK_STATE", bs_rev393, bs_fwd393, len(states393))
    it_f, it_r = compose("ITEM", item_rev393, item_fwd393, len(items393))
    bl_f, bl_r = compose("BLOCK", blk_rev393, blk_fwd393, len(blocks393))

    maps = {
        "BLOCK_STATE": (bs_f, bs_r, len(bs_f), len(bs_r)),
        "ITEM": (it_f, it_r, len(it_f), len(it_r)),
        "ENTITY_TYPE": (t404["ENTITY_TYPE"]["fwd"], t404["ENTITY_TYPE"]["rev"],
                        len(t404["ENTITY_TYPE"]["fwd"]),
                        len(t404["ENTITY_TYPE"]["rev"])),
        "SOUND": (t404["SOUND"]["fwd"], t404["SOUND"]["rev"],
                  len(t404["SOUND"]["fwd"]), len(t404["SOUND"]["rev"])),
        "PARTICLE": (t404["PARTICLE"]["fwd"], t404["PARTICLE"]["rev"],
                     len(t404["PARTICLE"]["fwd"]),
                     len(t404["PARTICLE"]["rev"])),
        "BLOCK": (bl_f, bl_r, len(bl_f), len(bl_r)),
        "BLOCK_ENTITY": (t404["BLOCK_ENTITY"]["fwd"],
                         t404["BLOCK_ENTITY"]["rev"],
                         len(t404["BLOCK_ENTITY"]["fwd"]),
                         len(t404["BLOCK_ENTITY"]["rev"])),
        "BIOME": (t404["BIOME"]["fwd"], t404["BIOME"]["rev"],
                  len(t404["BIOME"]["fwd"]), len(t404["BIOME"]["rev"])),
        "DAMAGE_TYPE": (t404["DAMAGE_TYPE"]["fwd"],
                        t404["DAMAGE_TYPE"]["rev"], 42, 42),
    }
    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": pig393,
        "SOUND": stone_break_393, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": plains393, "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": pig758,
        "SOUND": stone_break_758, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": biomes758["plains"], "DAMAGE_TYPE": 16,
    }

    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        print(f"{name}: 758→393 entries={n_fwd} ranges={len(fr)} "
              f"overrides={len(fo)} fallback={sum(1 for x in fwd if x == fb)} / "
              f"393→758 entries={n_rev} ranges={len(rr)} overrides={len(ro)} "
              f"fallback={sum(1 for x in rev if x == fallbacks_rev[name])}")

    if args.json:
        out = {name: {"fwd": fwd, "rev": rev}
               for name, (fwd, rev, _, _) in maps.items()}
        args.json.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        print(f"JSON を {args.json} に書き出した。")

    # --- 404 と同一の名前表（ブロックエンティティ・バイオーム）を写す ---
    tables404_java = (Path(__file__).resolve().parent.parent / "src" / "main"
                      / "java" / "io" / "detergentmc" / "protocol"
                      / "RegistryTables404.java").read_text(encoding="utf-8")

    def extract_names(field):
        m = re.search(field + r" = \{(.*?)\};", tables404_java, re.S)
        assert m, field
        return re.findall(r'"([^"]+)"', m.group(1))

    be_names = extract_names("BLOCK_ENTITY_NAMES_404")
    biome_names = extract_names("BIOME_NAMES_404")
    assert len(be_names) == 25 and len(biome_names) == 73

    # --- 音の名前の代替表（404 と同一。音の対応が同じため） ---
    tools_dir = Path(__file__).resolve().parent
    snd_names = [tuple(p) for p in
                 load(tools_dir / "sound-names-758-to-404.json")]
    snd_names_rev = [tuple(p) for p in
                     load(tools_dir / "sound-names-404-to-758.json")]
    (tools_dir / "sound-names-758-to-393.json").write_text(
        json.dumps(snd_names, ensure_ascii=False) + "\n", encoding="utf-8")
    (tools_dir / "sound-names-393-to-758.json").write_text(
        json.dumps(snd_names_rev, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"音の名前の代替表 JSON を書き出した（758→393 は {len(snd_names)} 対、"
          f"393→758 は {len(snd_names_rev)} 対。404 と同一）。")

    header = """package io.detergentmc.protocol;

/**
 * 393（1.13）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-393-tables.py}、検証は {@code verify-393-tables.py}。
 * 作り方は 404 の表との合成：393⇔404 の対応（実バニラ 1.13 jar と
 * 1.13.2 jar の data-generator ダンプ同士を名前で突き合わせ、全件
 * 一意に確定）に、404⇔758 の表（gen-404-tables.py）をつないだ。
 * 393 と 404 のレジストリ差分はサンゴまわりに集中する：1.13.1 で
 * dead coral 5 種が追加され（ブロック状態 +10・アイテム +5）、
 * 既存 7 ブロック（tnt の unstable、サンゴ 5 種と conduit の
 * waterlogged）にプロパティが増えた（状態 +7）。393→404 では
 * 追加プロパティに 404 の既定状態の値を補い、404→393 では
 * 追加プロパティを落とし、dead coral は生きたサンゴへ落とす。
 * エンティティ（mob 95）・バイオーム（73）・効果（30）は
 * minecraft-data で 1.13⇔1.13.2 が完全一致し、音（662）と
 * パーティクル（50）は ViaVersion mapping-1.13 が 1.13 世代共通、
 * ブロックエンティティ（25）と絵画（26。実 1.13 jar の定数プール
 * 順で確認）も 404 と同一のため、これらのカテゴリの表は 404 と
 * 同じ値になる。ダメージ種別は 42 件の恒等写像（404 と同じ）。
 * メニューは 393 にレジストリが無いため対応表を作らない
 * （Open Window は文字列種別で別途変換する）。効果 30 件は 758 の
 * 先頭 30 件と一致するため対応表は作らず、ワイヤ側で 30 以上を
 * 落とす。ブロック状態の上書き表だけは件数が多いため、分割クラス
 * RegistryTables393BsFwd*／BsRev* に分けて {@code concat} で
 * つないでいる。
 */
final class RegistryTables393 {

    private RegistryTables393() {
    }

"""
    parts = [header]
    chunk_files = {}
    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        if name == "BLOCK_STATE":
            def emit_chunks(prefix, overrides):
                names = []
                for ci in range(0, len(overrides), 1200):
                    chunk = overrides[ci:ci + 1200]
                    cname = f"RegistryTables393{prefix}{ci // 1200 + 1}"
                    body = [f"package io.detergentmc.protocol;",
                            "",
                            f"/** 393 のブロック状態の上書き表の分割クラス"
                            f"（{prefix}、{ci // 1200 + 1} 個目）。"
                            f"生成は gen-393-tables.py。 */",
                            f"final class {cname} {{",
                            "",
                            f"    private {cname}() {{",
                            "    }",
                            "",
                            "    static final int[][] V = {"]
                    for oi, o in enumerate(chunk):
                        comma = "," if oi < len(chunk) - 1 else ""
                        body.append(f"            {{{o[0]}, {o[1]}}}{comma}")
                    body.append("    };")
                    body.append("}")
                    chunk_files[cname] = "\n".join(body) + "\n"
                    names.append(f"{cname}.V")
                return names
            fwd_names = emit_chunks("BsFwd", fo)
            rev_names = emit_chunks("BsRev", ro)
            parts.append(java_table(f"{name}_393", fr, [], n_fwd, "758->393")
                         .replace(f"static final int[][] {name}_393_OVERRIDES = {{",
                                  f"static final int[][] {name}_393_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_393_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_393_OVERRIDES = concat("
                + ", ".join(fwd_names) + ");")
            parts.append("")
            parts.append(java_table(f"{name}_393_TO_758", rr, [], n_rev, "393->758")
                         .replace(f"static final int[][] {name}_393_TO_758_OVERRIDES = {{",
                                  f"static final int[][] {name}_393_TO_758_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_393_TO_758_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_393_TO_758_OVERRIDES = concat("
                + ", ".join(rev_names) + ");")
            parts.append("")
            continue
        parts.append(java_table(f"{name}_393", fr, fo, n_fwd, "758->393"))
        parts.append("")
        parts.append(java_table(f"{name}_393_TO_758", rr, ro, n_rev, "393->758"))
        parts.append("")
    lines = ["    /** 音の名前：758 → 393（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_758_TO_393 = {"]
    for src, dst in snd_names:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    lines = ["    /** 音の名前：393 → 758（Stop Sound 用）。改名と music.nether の細分化。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_393_TO_758 = {"]
    for src, dst in snd_names_rev:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")

    def names_array(field, names):
        lines = [f"    static final String[] {field} = {{"]
        for i in range(0, len(names), 4):
            chunk = names[i:i + 4]
            lines.append("            " + " ".join(f'"{n}",' for n in chunk))
        lines.append("    };")
        return "\n".join(lines)
    parts.append(names_array("BLOCK_ENTITY_NAMES_393", be_names))
    parts.append("")
    parts.append(names_array("BIOME_NAMES_393", biome_names))
    parts.append("")
    parts.append("""    /** 分割した上書き表をつなぐ（ブロック状態用）。 */
    static int[][] concat(int[][]... parts) {
        int total = 0;
        for (int[][] part : parts) {
            total += part.length;
        }
        int[][] out = new int[total][];
        int pos = 0;
        for (int[][] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }
""")
    parts.append("}")
    java = "\n".join(parts)
    if args.out:
        args.out.write_text(java, encoding="utf-8")
        print(f"Java を {args.out} に書き出した。")
        for cname, body in chunk_files.items():
            cpath = args.out.parent / f"{cname}.java"
            cpath.write_text(body, encoding="utf-8")
            print(f"分割クラスを {cpath} に書き出した。")
    else:
        print(java)
    return 0


if __name__ == "__main__":
    sys.exit(main())
