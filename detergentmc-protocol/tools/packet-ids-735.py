#!/usr/bin/env python3
"""PacketIds.java の各エントリに 1.16（735）のIDを付ける。


minecraft-data の data/pc/1.16/protocol.json は data/pc/1.16.2/protocol.json
とバイト同一で、実バニラ 1.16 jar のパケット登録順とは食い違う
（minecraft-data 側の誤写。例：Join Game は pc/1.16 では 0x24 だが
実 jar では 0x25、multi_block_change は pc/1.16 では 0x3b だが実 jar
では 0x0f、toServer は pc/1.16 では 48 件だが実 jar では 47 件で
displayed_recipe が無い）。そのため 735 のIDは実バニラ jar から直接
決める（tools/extract-jar-packets.py が ConnectionProtocol の登録順を
抜き出した jar-packets-1.16.json を読む）。

方法：
1. PacketIds.java のエントリ（名前・状態・方向・736〜775のID）を
読む。現在のエントリは 24 引数（736〜775）である。
グループ番号：name=1・state=2・direction=3・ID（736〜775）=4〜27・
終端子=group(28)。
※ 735列は 736列の「前」に挿入する（引数の並びは 735, 736, 751, 753, 754, 755, 756, 757,
759,..., 775 になる）。
2. 検証済みの packet-ids-736.json（id736）と 1.16.1 jar の登録順
（jar-packets-1.16.1.json）から、パケットクラス→minecraft-data
内部名の対応表を作る（同じクラスを共有する分割パケットは複数名が
1クラスに対応する）。この対応表で 1.16.1 jar の登録順が id736 を
全件再現することをまず確認する（手法の自己検証）。
3. 同じ対応表で 1.16 jar の登録順（jar-packets-1.16.json）から各
エントリの id735 を引く。1.16 jar に無いクラスのエントリは -1。
1.16 jar にあって対応表に無いクラスがあれば新規パケットの候補
として一覧に出す（意図的に対象外のものは除く）。
4. 件数を数える（変更前=736、変更後=735）。

実測結果（このスクリプトの実行結果）：
- 1.16 jar と 1.16.1 jar のパケット登録順は全状態・全方向で完全同一
（HANDSHAKING 1件・LOGIN 3／5件・PLAY 47／92件・STATUS 2／2件）。
1.16.2 jar では ChunkBlocksUpdate→SectionBlocksUpdate の移動と
RecipeBookUpdate の2分割があり、736→751 の差分（変更62件・削除1件）
は 751（1.16.2）で起きたものである。
- よって 736→735 は変更0件・新規0件・削除0件で、id735 は全272件で
id736 と一致する。Join Game 0x25、Chunk Data 0x21、Update Time 0x4e、
Respawn 0x3a、Keep Alive 0x20、Entity Destroy 0x37、Ping 0x12／
Pong 0x07、Multi Block Change 0x0f、Tab Complete 0x10、
Displayed Recipe -1（1.16 にも無い）、Sculk -1。
- ViaVersion の 1.16 enum も 1.16.1 レイアウト（＝実 1.16 レイアウト）と
一致し、Protocol1_16To1_16_1 に付け替えは無い（別途
tools/verify-735-packet-ids.py で照合）。

情報源：
- 実バニラ 1.16 server.jar（piston-meta sha1 a0d03225... と照合済み）
内の net.minecraft.network.ConnectionProtocol（mf）の static 初期化子。
クラス名は Mojang 公式 server_mappings（sha1 11120c39...、1.16.1 と
同じファイル）で難読化解除。
- 実バニラ 1.16.1 server.jar（同じ方法で抜き出した
jar-packets-1.16.1.json）と、検証済みの packet-ids-736.json。

使い方：
python3 tools/packet-ids-735.py [--data-dir DIR] [--out FILE]
--data-dir を省略すると detergentmc-protocol/build/remap-data を使う。
--out を省略すると detergentmc-protocol/build/remap-data/packet-ids-735.json
に書き出す。終了コード 0 = 全件写像でき、自己検証も全件通った。
1 = 写像できない／自己検証不一致のエントリがある。

件数の数え方（736版と同じ定義）：
変更 = 変更前 >= 0 かつ 変更後 >= 0 かつ ID が違うもの。
新規 = 変更前 < 0 かつ 変更後 >= 0 のエントリ、または新しい表にあって
PacketIds に無いパケット。
削除 = 変更前 >= 0 かつ 変更後 < 0 のもの。
※ ここでは「変更前」= 736、「変更後」= 735 とする（736→735 の差分）。

件数ガード（736版と同じ）：
PacketIds.java のエントリが 272件でなければ終了コード 1 で書き出さない。
735列挿入後（25引数版）に再実行すると 24引数版の正規表現は一致せず
0件になり、空の写像 {} を書き出してしまう事故を防ぐ。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
/ "io" / "detergentmc" / "protocol" / "PacketIds.java")

# jar-packets-*.json のキー（状態／方向）と PacketIds.java の対応。
# flow.a＝サーバー行き（toServer）、flow.b＝クライアント行き（toClient）。
JAR_KEY = {("HANDSHAKING", "SERVERBOUND"): "HANDSHAKING/flow.a",
("LOGIN", "CLIENTBOUND"): "LOGIN/flow.b",
("LOGIN", "SERVERBOUND"): "LOGIN/flow.a",
("PLAY", "CLIENTBOUND"): "PLAY/flow.b",
("PLAY", "SERVERBOUND"): "PLAY/flow.a"}

# 1.16 jar にあって PacketIds の対象外のクラス（新規パケットの一覧から除く）。
# ClientboundMoveEntityPacket（PLAY クライアント行き 0x2b）：1.17 で削除
# された entity パケットのクラスで、PacketIds のどのバージョンにも
# エントリが無い（ViaVersion の Protocol1_16_4To1_17 でも未登録）。
EXCLUDED_JAR_CLASSES = {("PLAY", "CLIENTBOUND"): {"ClientboundMoveEntityPacket"}}


def parse_packet_ids(java_text):
    """PacketIds.java の enum エントリを抜き出す（24引数版：736〜775）。

    グループ番号：name=1、state=2、direction=3、ID（736〜775）=4〜27、
    終端子=group(28)。
    """
    id_pat = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([id_pat] * 24) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        groups = m.groups()
        name, state, direction = groups[0], groups[1], groups[2]
        ids = [int(g, 0) for g in groups[3:]]
        assert len(ids) == 24, f"{name}: IDが24個ではない（{len(ids)}個）"
        entries.append({
            "name": name, "state": state, "direction": direction,
            **{f"id{p}": v for p, v in
               zip((736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
                    770, 771, 772, 773, 774, 775), ids)},
        })
    return entries



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    jar116 = json.loads((args.data_dir / "jar-packets-1.16.json").read_text(encoding="utf-8"))
    jar1161 = json.loads((args.data_dir / "jar-packets-1.16.1.json").read_text(encoding="utf-8"))
    map736 = json.loads((args.data_dir / "packet-ids-736.json").read_text(encoding="utf-8"))
    if len(map736) != 272:
        print(f"packet-ids-736.json の件数が 272 件ではない（{len(map736)} 件）。",
              file=sys.stderr)
        return 1

    entries = parse_packet_ids(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds.java のエントリ: {len(entries)} 件")
    if len(entries) != 272:
        print(f"エントリが 272 件ではない（{len(entries)} 件）。"
              "PacketIds.java が 25引数版（735列挿入済み）の可能性がある。",
              file=sys.stderr)
        return 1

    # クラス→内部名の対応表を packet-ids-736.json と 1.16.1 jar の登録順から作る。
    # キーは (状態, 方向, クラス名)。値は内部名の集合（分割パケットで複数）。
    class_to_mc = {}
    for name, r in map736.items():
        if r["id736"] < 0:
            continue
        key = (r["state"], r["direction"])
        jar_key = JAR_KEY.get(key)
        if jar_key is None:
            # HANDSHAKING 以外の CONFIGURATION は 1.16 には無い（全て -1）。
            continue
        classes = jar1161[jar_key]
        if r["id736"] >= len(classes):
            print(f"{name}: id736={r['id736']} が 1.16.1 jar の {jar_key}"
                  f"（{len(classes)} 件）の範囲外", file=sys.stderr)
            return 1
        cls = classes[r["id736"]]
        class_to_mc.setdefault((r["state"], r["direction"], cls), set()).add(r["mc"])
    print(f"クラス→内部名の対応表: {len(class_to_mc)} クラス"
          f"（{sum(len(v) for v in class_to_mc.values())} 内部名）")

    # 自己検証：1.16.1 jar の登録順が id736 を全件再現すること。
    # （対応表の作り方と jar 抽出の正しさの確認。）
    problems = []
    by_mc = {}
    for (state, direction, cls), mcs in class_to_mc.items():
        for mc in mcs:
            by_mc[(state, direction, mc)] = cls
    for e in entries:
        key = (e["state"], e["direction"])
        jar_key = JAR_KEY.get(key)
        if jar_key is None:
            if e["id736"] >= 0:
                problems.append(f"{e['name']}: {key} は jar に無いのに id736={e['id736']}")
            continue
        classes = jar1161[jar_key]
        # エントリの内部名（packet-ids-736.json の mc）を引く。
        mc = map736[e["name"]]["mc"]
        cls = by_mc.get((e["state"], e["direction"], mc))
        if cls is None:
            if e["id736"] >= 0:
                problems.append(f"{e['name']}: 内部名 {mc} のクラスが無いのに id736={e['id736']}")
            continue
        if classes.index(cls) != e["id736"]:
            problems.append(f"{e['name']}: 1.16.1 jar の {cls} は"
                            f" {classes.index(cls):#04x} だが id736={e['id736']:#04x}")
    print(f"自己検証（1.16.1 jar → id736）：不一致 {len(problems)} 件")
    for p in problems:
        print(f"  {p}", file=sys.stderr)
    if problems:
        return 1

    # 1.16 jar の登録順から id735 を引く。
    result = {}
    for e in entries:
        key = (e["state"], e["direction"])
        jar_key = JAR_KEY.get(key)
        mc = map736[e["name"]]["mc"]
        if jar_key is None:
            id735 = -1
        else:
            cls = by_mc.get((e["state"], e["direction"], mc))
            if cls is None:
                id735 = -1
            else:
                classes = jar116[jar_key]
                id735 = classes.index(cls) if cls in classes else -1
        result[e["name"]] = {
            "mc": mc, "state": e["state"], "direction": e["direction"],
            **{f"id{p}": e[f"id{p}"] for p in
               (736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
                770, 771, 772, 773, 774, 775)},
            "id735": id735,
        }
    print(f"一意に写像できた: {len(result)} 件")

    # 件数（定義は docstring の「件数の数え方」を参照。変更前=736）。
    def count_diff(before_key, after_key, scope=None):
        changed = new = removed = 0
        changed_list = []
        for name, r in result.items():
            if scope is not None and (r["state"], r["direction"]) != scope:
                continue
            b, a = r[before_key], r[after_key]
            if b >= 0 and a >= 0 and a != b:
                changed += 1
                changed_list.append(name)
            elif b < 0 and a >= 0:
                new += 1
            elif b >= 0 and a < 0:
                removed += 1
        return changed, new, removed, changed_list

    total = count_diff("id736", "id735")
    print(f"736→735 全体: 変更 {total[0]} 件・新規 {total[1]} 件・削除 {total[2]} 件")
    scopes = [("HANDSHAKING", "SERVERBOUND"), ("LOGIN", "CLIENTBOUND"),
              ("LOGIN", "SERVERBOUND"), ("CONFIGURATION", "CLIENTBOUND"),
              ("CONFIGURATION", "SERVERBOUND"), ("PLAY", "CLIENTBOUND"),
              ("PLAY", "SERVERBOUND")]
    for scope in scopes:
        c, n, rm, changed_list = count_diff("id736", "id735", scope)
        print(f"  {scope[0]}/{scope[1]}: 変更 {c} 件・新規 {n} 件・削除 {rm} 件")
        for name in changed_list:
            r = result[name]
            print(f"    {name}: 736={r['id736']:#04x} -> 735={r['id735']:#04x} "
                  f"({r['mc']})")
    removed_list = [n for n, r in result.items()
                    if r["id736"] >= 0 and r["id735"] < 0]
    print(f"736で削除（-1）: {removed_list}")
    revived_list = [n for n, r in result.items()
                    if r["id736"] < 0 and r["id735"] >= 0]
    print(f"736で復活（736で-1・735で値あり）: {revived_list}")

    # 1.16 jar にあって表に無いクラス（新規パケットの候補）。
    known_classes = {(state, direction, cls)
                     for (state, direction, cls) in class_to_mc}
    new_packets = []
    for (state, direction), jar_key in JAR_KEY.items():
        for i, cls in enumerate(jar116[jar_key]):
            if (state, direction, cls) in known_classes:
                continue
            if cls in EXCLUDED_JAR_CLASSES.get((state, direction), set()):
                continue
            new_packets.append((state, direction, cls, i))
    print(f"1.16 jar にあって表に無いクラス: {len(new_packets)} 件")
    for state, direction, cls, i in new_packets:
        print(f"  {state}/{direction} {cls} = {i:#04x}")

    out_path = args.out or args.data_dir / "packet-ids-735.json"
    out_path.write_text(json.dumps(result, indent=2, ensure_ascii=False)
                        + "\n", encoding="utf-8")
    print(f"写像を {out_path} に書き出した。")

    if problems or new_packets:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
