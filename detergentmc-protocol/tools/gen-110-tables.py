#!/usr/bin/env python3
"""110（1.9.4／1.9.3）のレジストリ対応表を生成する。

作り方は 210 の表との合成。110 は 210 と同じ Flattening 前の世代で、
値は (数値ID<<4)|メタデータ（4096 通り）。210 との差分は 1.10 の
追加分だけ（Stage A で実 jar 抽出の ground truth 同士を全件照合済み。
extract-110-registries.py／extract-210-registries.py）：

- ブロック: 110 = 210 − {213 magma, 214 nether_wart_block,
  215 red_nether_brick, 216 bone_block, 217 structure_void}。
  共有ブロックの数値 ID は全件同一。近似（758→110 の寄せ先）:
  magma → netherrack (87)、nether_wart_block → nether_brick (112)、
  red_nether_brick → nether_brick (112)、bone_block → quartz_block
  (155、メタはそのまま＝軸の近似)、structure_void → barrier (166、
  どちらも不可視の技術ブロック)。
- アイテム: 110 に無いのは上記 5 ブロックのアイテム形（213〜217）
  と structure_block のアイテム形 (255) の 6 件だけ。255 の不在は
  実バニラ 1.9.4 の /give 実測で確定した（"There is no such item
  with name minecraft:structure_block"。1.9.4 のクライアント jar に
  モデルファイルはあるがアイテム形は未登録）。近似はブロックと
  同じ寄せ先（255 → barrier 166）。
- エンティティ: 110 = 210 − {102 PolarBear}。polar_bear は pig (90)
  へ（この世代の「無いモブは pig」の慣例どおり）。
- 音: 110 の 444 件は 210 の 463 件の名前の部分集合（1.10 追加の
  19 件が差分。改名・削除は無し）。ただし数値 ID は登録順が違う
  ため名前で合成する。1.10 追加音は汎用音（block.stone.break の
  110 番号 = 364）へ寄せる。
- パーティクル: 110 = 210 − {46 fallingdust}。fallingdust は
  explode (0) へ（210 が totem／spit を explode へ寄せた慣例）。
- ブロックエンティティ: 23 件・登録順とも 210 と完全一致
  （実 jar 抽出同士の照合）→ 210 の表をそのまま使う。
- バイオーム: pc/1.9 と pc/1.10 で完全一致 → 210 の表をそのまま。
  ダメージ種別・状態異常も 210 と同じ。

土台の 210 の表は gen-210-tables.py の build_tables を import して
得る（土台の 316 の表はさらに gen-316-tables.py の build_all）。

使い方：
  python3 gen-110-tables.py [--out PATH] [--json PATH]
"""

import argparse
import importlib.util
import json
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
JAVA_DIR = TOOLS.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
MCDATA = Path("/home/hatch/mc-client/node_modules/minecraft-data"
              "/minecraft-data/data/pc")


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(TOOLS))
    spec.loader.exec_module(mod)
    return mod


gen210 = load_module("gen210", TOOLS / "gen-210-tables.py")
gen316 = gen210.gen316
ranges_overrides = gen316.ranges_overrides
java_table = gen316.java_table


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


# --- 110 の ground truth（extract-110-registries.py の出力） ---
BLOCKS_110 = load(TOOLS / "blocks-110.json")          # [[id, name], ...]
ENTITIES_110 = load(TOOLS / "entities-110.json")      # [[id, 旧式名], ...]
SOUNDS_110 = load(TOOLS / "sounds-110.json")          # [name, ...]（添字=ID）
PARTICLES_110 = load(TOOLS / "particles-110.json")    # [[id, name], ...]
BLOCK_ENTITIES_110 = load(TOOLS / "block-entities-110.json")  # [旧式名]
SOUNDS_210 = gen210.SOUNDS_210

# 210 にあって 110 に無いものと近似先
MISSING_BLOCKS_110 = {213, 214, 215, 216, 217}
BLOCK_APPROX_110 = {213: 87, 214: 112, 215: 112, 216: 155, 217: 166}
MISSING_ITEMS_110 = {213, 214, 215, 216, 217, 255}
ITEM_APPROX_110 = {213: 87, 214: 112, 215: 112, 216: 155, 217: 166,
                   255: 166}
POLAR_BEAR_210 = 102
FALLINGDUST_210 = 46


def build_tables(t210, meta210):
    problems = []

    def fail(msg):
        problems.append(msg)

    # ---------- ground truth の自己照合（210 との差分の固定） ----------
    blocks210 = {i: n for i, n in gen210.BLOCKS_210}
    blocks110 = {i: n for i, n in BLOCKS_110}
    if set(blocks210) - set(blocks110) != MISSING_BLOCKS_110:
        fail(f"ブロック差分が想定と違う: {sorted(set(blocks210) - set(blocks110))}")
    if set(blocks110) - set(blocks210):
        fail(f"110 のみのブロックがある: {sorted(set(blocks110) - set(blocks210))}")
    for i, n in blocks110.items():
        if blocks210.get(i) != n:
            fail(f"ブロック {i} の名前が 210 と違う: 110={n} 210={blocks210.get(i)}")
    ent210_ids = {i for i, _ in gen210.ENTITIES_210_RAW}
    ent110_ids = {i for i, _ in ENTITIES_110}
    if ent210_ids - ent110_ids != {POLAR_BEAR_210} or ent110_ids - ent210_ids:
        fail(f"エンティティ登録差分が想定と違う: "
             f"210のみ={sorted(ent210_ids - ent110_ids)} "
             f"110のみ={sorted(ent110_ids - ent210_ids)}")
    if [n for _, n in ENTITIES_110] != [n for i, n in gen210.ENTITIES_210_RAW
                                        if i != POLAR_BEAR_210]:
        fail("エンティティの登録順・名前が PolarBear 除去と一致しない")
    s210_set = set(SOUNDS_210)
    if not set(SOUNDS_110) <= s210_set:
        fail(f"110 のみに無い音がある: {sorted(set(SOUNDS_110) - s210_set)}")
    if len(SOUNDS_110) != 444 or len(s210_set - set(SOUNDS_110)) != 19:
        fail(f"音の件数が想定と違う: 110={len(SOUNDS_110)} "
             f"差分={len(s210_set - set(SOUNDS_110))}")
    p210 = {i: n for i, n in gen210.PARTICLES_210}
    p110 = {i: n for i, n in PARTICLES_110}
    if set(p210) - set(p110) != {FALLINGDUST_210} or set(p110) - set(p210):
        fail(f"パーティクル差分が想定と違う: {sorted(set(p210) - set(p110))}")
    for i, n in p110.items():
        if p210.get(i) != n:
            fail(f"パーティクル {i} の名前が 210 と違う")
    if BLOCK_ENTITIES_110 != list(gen210.BLOCK_ENTITIES_210_RAW):
        fail("ブロックエンティティが 210 と完全一致しない")

    stone758 = t210["BLOCK_STATE"][1][(1 << 4) | 0]

    # ---------- ブロック状態 ----------
    fwd210, rev210 = t210["BLOCK_STATE"]

    def approx_bs(v):
        block, meta = v >> 4, v & 15
        if block in BLOCK_APPROX_110:
            return (BLOCK_APPROX_110[block] << 4) | meta
        return v

    fwd = [approx_bs(v) for v in fwd210]
    rev = [stone758 if (i >> 4) in MISSING_BLOCKS_110 else rev210[i]
           for i in range(4096)]

    # ---------- ブロック ----------
    bfwd210, brev210 = t210["BLOCK"]
    bfwd = [BLOCK_APPROX_110.get(v, v) for v in bfwd210]
    brev = [brev210[1] if i in MISSING_BLOCKS_110 else brev210[i]
            for i in range(256)]

    # ---------- アイテム ----------
    ifwd210, irev210 = t210["ITEM"]
    ifwd = [ITEM_APPROX_110.get(v, v) for v in ifwd210]
    irev = [irev210[1] if i in MISSING_ITEMS_110 else irev210[i]
            for i in range(len(irev210))]
    # mcdata pc/1.9 items.json（365 件）との端末照合：数値 ID で照合
    # （210 と共有するアイテムは数値 ID が同一。Stage A で名前ベース
    # に確認済み）。
    items19 = load(MCDATA / "1.9" / "items.json")
    if len(items19) != 365:
        fail(f"mcdata 1.9 items が 365 件でない: {len(items19)}")
    for it in items19:
        i = it["id"]
        if i in MISSING_ITEMS_110:
            fail(f"アイテム {it['name']}({i}) は 110 に無い想定なのに "
                 f"mcdata 1.9 にある")
        if irev[i] != irev210[i]:
            fail(f"アイテム {it['name']}: rev110[{i}]={irev[i]} "
                 f"!= rev210 {irev210[i]}")
        if ifwd[irev210[i]] != i:
            fail(f"アイテム {it['name']}: 往復が戻らない "
                 f"110={i} → 758={irev210[i]} → 110={ifwd[irev210[i]]}")

    # ---------- エンティティ ----------
    efwd210, erev210 = t210["ENTITY_TYPE"]
    efwd = [90 if v == POLAR_BEAR_210 else v for v in efwd210]
    erev = [meta210["pig_758"] if i == POLAR_BEAR_210 else erev210[i]
            for i in range(len(erev210))]

    # ---------- 音（名前で合成） ----------
    sfwd210, srev210 = t210["SOUND"]
    s210_idx = {n: i for i, n in enumerate(SOUNDS_210)}
    s110_idx = {n: i for i, n in enumerate(SOUNDS_110)}
    generic110 = s110_idx["block.stone.break"]
    sfwd = []
    for t in range(len(sfwd210)):
        name = SOUNDS_210[sfwd210[t]]
        sfwd.append(s110_idx.get(name, generic110))
    srev = [srev210[s210_idx[n]] for n in SOUNDS_110]

    # ---------- パーティクル ----------
    pfwd210, prev210 = t210["PARTICLE"]
    pfwd = [0 if v == FALLINGDUST_210 else v for v in pfwd210]
    prev = [prev210[i] for i in range(46)]

    # ---------- ブロックエンティティ／バイオーム／ダメージ（210 と同一） --
    befwd, berev = t210["BLOCK_ENTITY"]
    bifwd, birev = t210["BIOME"]
    dfwd, drev = t210["DAMAGE_TYPE"]

    tables = {
        "BLOCK_STATE": (fwd, rev),
        "ITEM": (ifwd, irev),
        "ENTITY_TYPE": (efwd, erev),
        "SOUND": (sfwd, srev),
        "PARTICLE": (pfwd, prev),
        "BLOCK": (bfwd, brev),
        "BLOCK_ENTITY": (befwd, berev),
        "BIOME": (bifwd, birev),
        "DAMAGE_TYPE": (dfwd, drev),
    }
    meta = {
        "pig_110": 90,
        "pig_758": meta210["pig_758"],
        "generic_sound_110": generic110,
        "stone_break_758": meta210["stone_break_758"],
        "plains_758": meta210["plains_758"],
        "be_names": meta210["be_names"],
        "be_legacy_names": meta210["be_legacy_names"],
        "biome_names": meta210["biome_names"],
    }
    return tables, meta, problems


def sound_name_fallbacks_110():
    """Stop Sound 用の名前フォールバック対（110 版）。

    土台は 210 の流儀：sound-names-758-to-316.json／
    sound-names-316-to-758.json の対から、316 名が 110 にあれば
    それを、無ければ 110 の名前空間から接頭辞の近いものを探す。
    """
    snd110 = set(SOUNDS_110)
    s316 = load(TOOLS / "sounds-316.json")
    pairs758_316 = load(TOOLS / "sound-names-758-to-316.json")
    pairs316_758 = load(TOOLS / "sound-names-316-to-758.json")
    name316_of_758 = {a: b for a, b in pairs758_316}
    name758_of_316 = {a: b for a, b in pairs316_758}
    sounds758 = load(Path.home() / "workspace" / "mcdata404"
                     / "mapping-1.18.json")["sounds"]

    def approx(name, space):
        toks = name.split(".")
        for k in range(len(toks) - 1, 0, -1):
            pref = ".".join(toks[:k]) + "."
            cand = sorted(s for s in space if s.startswith(pref))
            if cand:
                return cand[0]
        return None

    fwd_pairs = []
    for n758 in sounds758:
        n316 = name316_of_758.get(n758, n758)
        if n316 in snd110:
            n110 = n316
        else:
            n110 = approx(n316, snd110)
        if n110 and n110 != n758:
            fwd_pairs.append([n758, n110])
    rev_pairs = []
    for n110 in SOUNDS_110:
        n316 = gen210.SOUND_RENAME_210.get(n110, n110)
        n758 = name758_of_316.get(n316, n316)
        if n758 != n110:
            rev_pairs.append([n110, n758])
    return fwd_pairs, rev_pairs


def emit_java(tables, meta, snd_names, snd_names_rev):
    header = """package io.detergentmc.protocol;

/**
 * 110（1.9.4／1.9.3）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-110-tables.py}、検証は {@code verify-110-tables.py}。
 * 作り方は 210 の表との合成：110 は 210 と同じ Flattening 前の世代で
 * 値は (数値ID&lt;&lt;4)|メタデータ（4096 通り）。210 との差分は
 * 1.10 追加分だけ（ブロック 5 種・アイテム 6 件・polar_bear・
 * 音 19 件・fallingdust）。ブロック（214 種）・エンティティ
 * （60 登録）・音（444 件）・パーティクル（46 件）・
 * ブロックエンティティ（23 件）の登録順は実バニラ 1.9.4 jar の
 * バイトコードから抽出した（extract-110-registries.py）。
 *
 * <p>近似・制限（詳細は docs/PROXY-1.9.4.md）：
 * magma は netherrack へ、nether_wart_block と red_nether_brick は
 * nether_brick へ、bone_block は quartz_block へ、structure_void は
 * barrier へ寄せる。structure_block のアイテム形は 1.9.4 に無い
 * （実サーバーの /give で確認）ため barrier のアイテム形へ寄せる。
 * polar_bear は pig へ、fallingdust は explode へ、1.10 追加音は
 * 汎用音（block.stone.break の 110 番号）へ寄せる。
 * ブロックエンティティの NBT id は 110 の旧式名（"Furnace" 等）で
 * 読み書きする（{@code BLOCK_ENTITY_LEGACY_NAMES_110}。210 と同一）。
 * bed のブロックエンティティは落とす（210 と同じ）。
 */
final class RegistryTables110 {

    private RegistryTables110() {
    }

"""
    parts = [header]
    chunk_files = {}
    fallbacks_fwd = {
        "BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": meta["pig_110"],
        "SOUND": meta["generic_sound_110"], "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": 1, "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": meta["pig_758"],
        "SOUND": meta["stone_break_758"], "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": meta["plains_758"], "DAMAGE_TYPE": 16,
    }
    for name, (fwd, rev) in tables.items():
        fr, fo = ranges_overrides(fwd, len(fwd), fallbacks_fwd[name])
        rr, ro = ranges_overrides(rev, len(rev), fallbacks_rev[name])
        n_fwd, n_rev = len(fwd), len(rev)
        if name == "BLOCK_STATE":
            def emit_chunks(prefix, overrides):
                names = []
                for ci in range(0, len(overrides), 1200):
                    chunk = overrides[ci:ci + 1200]
                    cname = f"RegistryTables110{prefix}{ci // 1200 + 1}"
                    body = ["package io.detergentmc.protocol;",
                            "",
                            f"/** 110 のブロック状態の上書き表の分割クラス"
                            f"（{prefix}、{ci // 1200 + 1} 個目）。"
                            f"生成は gen-110-tables.py。 */",
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
            parts.append(java_table(f"{name}_110", fr, [], n_fwd, "758->110")
                         .replace(f"static final int[][] {name}_110_OVERRIDES = {{",
                                  f"static final int[][] {name}_110_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_110_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_110_OVERRIDES = concat("
                + ", ".join(fwd_names) + ");")
            parts.append("")
            parts.append(java_table(f"{name}_110_TO_758", rr, [], n_rev, "110->758")
                         .replace(f"static final int[][] {name}_110_TO_758_OVERRIDES = {{",
                                  f"static final int[][] {name}_110_TO_758_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_110_TO_758_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_110_TO_758_OVERRIDES = concat("
                + ", ".join(rev_names) + ");")
            parts.append("")
            continue
        parts.append(java_table(f"{name}_110", fr, fo, n_fwd, "758->110"))
        parts.append("")
        parts.append(java_table(f"{name}_110_TO_758", rr, ro, n_rev, "110->758"))
        parts.append("")
    parts.append("    /** 音の名前：758 → 110（Stop Sound 用）。実体は RegistryTables110Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_758_TO_110 ="
                 " RegistryTables110Snd.FWD;")
    parts.append("")
    parts.append("    /** 音の名前：110 → 758（Stop Sound 用）。実体は RegistryTables110Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_110_TO_758 ="
                 " RegistryTables110Snd.REV;")
    parts.append("")
    snd_body = ["package io.detergentmc.protocol;",
                "",
                "/**",
                " * 110（1.9.4／1.9.3）の音の名前フォールバック表（分離クラス）。",
                " *",
                " * <p>RegistryTables110 の静的初期化子が 64KB 制限を超えるため",
                " * 音の名前表だけをここへ分けた。生成は tools/gen-110-tables.py、",
                " * 検証は tools/verify-110-tables.py。",
                " */",
                "final class RegistryTables110Snd {",
                "",
                "    private RegistryTables110Snd() {",
                "    }",
                "",
                "    /** 音の名前：758 → 110（Stop Sound 用）。数値表で届かない音の寄せ先。 */",
                "    static final String[][] FWD = {"]
    for src, dst in snd_names:
        snd_body.append(f'            {{"{src}", "{dst}"}},')
    snd_body.append("    };")
    snd_body.append("")
    snd_body.append("    /** 音の名前：110 → 758（Stop Sound 用）。1.10／1.11 での改名を戻す。 */")
    snd_body.append("    static final String[][] REV = {")
    for src, dst in snd_names_rev:
        snd_body.append(f'            {{"{src}", "{dst}"}},')
    snd_body.append("    };")
    snd_body.append("}")
    chunk_files["RegistryTables110Snd"] = "\n".join(snd_body) + "\n"

    def names_array(field, names, comment):
        lines = [f"    /** {comment} */",
                 f"    static final String[] {field} = {{"]
        for i in range(0, len(names), 4):
            chunk = names[i:i + 4]
            lines.append("            " + " ".join(f'"{n}",' for n in chunk))
        lines.append("    };")
        return "\n".join(lines)
    parts.append(names_array(
        "BLOCK_ENTITY_NAMES_110", meta["be_names"],
        "ブロックエンティティの正規化名（110 の登録順、添字=110 の種別）。210 と同一。"))
    parts.append("")
    parts.append(names_array(
        "BLOCK_ENTITY_LEGACY_NAMES_110", meta["be_legacy_names"],
        "ブロックエンティティの旧式 NBT id（110 の登録順、名前空間なし）。"
        "1.9.4 の実バニラはこの名前で NBT の id を読み書きする。210 と同一。"))
    parts.append("")
    parts.append(names_array("BIOME_NAMES_110", meta["biome_names"],
                             "バイオーム名（316 と同一）。"))
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
    return "\n".join(parts), chunk_files


def main():
    ap = argparse.ArgumentParser()
    base = Path.home() / "workspace"
    ap.add_argument("--out", default=str(JAVA_DIR / "RegistryTables110.java"))
    ap.add_argument("--json", default=None)
    args = ap.parse_args()

    # 土台の 316 の表（gen-316-tables.py の build_all を再利用）
    class A:  # gen316.build_all が読む引数
        tables340 = str(base / "mcdata404" / "tables-340.json")
        mcdata11 = str(MCDATA / "1.11")
        mcdata12 = str(MCDATA / "1.12")
        mcdata = str(base / "mcdata404")
    tables316, _report, meta316 = gen316.build_all(A())
    t316 = {k: (v[0], v[1]) for k, v in tables316.items()}

    tables210, meta210, problems210 = gen210.build_tables(t316, meta316)
    if problems210:
        for p in problems210:
            print("NG(210):", p, file=sys.stderr)
        return 1
    t210 = tables210

    tables, meta, problems = build_tables(t210, meta210)
    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("合成の照合は全件成立。")
    print(f"MAX 値: BLOCK_STATE_110=4095, ITEM_110={len(tables['ITEM'][1]) - 1}, "
          f"ENTITY_TYPE_110={len(tables['ENTITY_TYPE'][1]) - 1}, "
          f"SOUND_110={len(tables['SOUND'][1]) - 1}, "
          f"PARTICLE_110={len(tables['PARTICLE'][1]) - 1}, BLOCK_110=255, "
          f"BIOME_110={len(tables['BIOME'][1]) - 1}, "
          f"BLOCK_ENTITY_110={len(tables['BLOCK_ENTITY'][1]) - 1}")
    print(f"代替: GENERIC_SOUND_110={meta['generic_sound_110']}, "
          f"PIG_110={meta['pig_110']}")

    snd_names, snd_names_rev = sound_name_fallbacks_110()
    print(f"音の名前フォールバック: 758→110 {len(snd_names)} 対、"
          f"110→758 {len(snd_names_rev)} 対")
    (TOOLS / "sound-names-758-to-110.json").write_text(
        json.dumps(snd_names, ensure_ascii=False) + "\n", encoding="utf-8")
    (TOOLS / "sound-names-110-to-758.json").write_text(
        json.dumps(snd_names_rev, ensure_ascii=False) + "\n", encoding="utf-8")

    if args.json:
        dump = {k: {"fwd": v[0], "rev": v[1]} for k, v in tables.items()}
        Path(args.json).write_text(json.dumps(dump), encoding="utf-8")
        print(f"JSON を書き出した: {args.json}")

    java, chunk_files = emit_java(tables, meta, snd_names, snd_names_rev)
    out = Path(args.out)
    out.write_text(java, encoding="utf-8")
    print(f"Java を書き出した: {out}（{len(java)} バイト）")
    for cname, body in chunk_files.items():
        p = out.with_name(cname + ".java")
        p.write_text(body, encoding="utf-8")
        print(f"  分割クラス: {p.name}（{len(body)} バイト）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
