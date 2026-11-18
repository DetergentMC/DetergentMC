#!/usr/bin/env python3
"""mc-1.16-protocol.json (pc/1.16.2) vs mc-1.17-protocol.json を全状態・全方向で突き合わせる。

755 の ChunkTranslator 担当が .tmp-755/stage-b-chunk/compare-protocol.py で
やったのと同じ方法。minecraft-data の protocol.json の各パケットについて
名前・ID・フィールド構成を比較する。改名・分割パケットは RENAME_754 /
SPLIT_754 で読み替える（Stage A §8.2 と同じ対応）。

使い方: python3 compare-754-755-protocol.py
終了コード: フィールド差分の一覧を出すだけ（0）。件数は最後に表示する。
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = HERE.parent / "build" / "remap-data"
D16 = json.load(open(DATA / "mc-1.16-protocol.json"))
D17 = json.load(open(DATA / "mc-1.17-protocol.json"))

# 1.16.5 の内部名 -> 1.17 の内部名（1 対 1 の改名）
RENAME_754 = {
    "entity_destroy": "destroy_entity",
    "transaction": None,  # ping/pong に置き換え（方向で分ける）。SPLIT 扱い
}
# 1.16.5 の 1 パケット -> 1.17 の複数パケット（分割）
SPLIT_754 = {
    "combat_event": ["enter_combat_event", "end_combat_event", "death_combat_event"],
    "title": ["set_title_text", "set_title_subtitle", "set_title_time",
              "clear_titles", "action_bar"],
    "world_border": ["initialize_world_border", "world_border_center",
                     "world_border_lerp_size", "world_border_size",
                     "world_border_warning_delay", "world_border_warning_reach"],
    "transaction": ["ping", "pong"],  # 方向で片方だけ使う
}


def packet_map(doc, state, direction):
    """id(int) -> 内部名。types['packet'] は mapper。"""
    types = doc[state][direction]["types"]
    pkt = types["packet"]
    assert pkt[0] == "container", pkt[0]
    fields = pkt[1]
    name_field = [f for f in fields if f.get("name") == "name"][0]
    mappings = name_field["type"][1]["mappings"]
    return {int(k, 16): v for k, v in mappings.items()}


def fields_of(doc, state, direction, name):
    types = doc[state][direction]["types"]
    # types のキーは "packet_<名前>"、mapper の名前は "<名前>"。
    if name in types:
        return types[name]
    return types.get("packet_" + name)


def norm(x):
    """フィールド定義を比較用に正規化（JSON 文字列化）。"""
    return json.dumps(x, sort_keys=True)


def main():
    diffs = []  # (state, direction, name16, name17, kind, detail)
    id_changes = []
    only16 = []
    only17 = []
    for state in ["handshaking", "status", "login", "play"]:
        for direction in ["toClient", "toServer"]:
            m16 = packet_map(D16, state, direction)
            m17 = packet_map(D17, state, direction)
            names16 = set(m16.values())
            names17 = set(m17.values())
            # 改名・分割を読み替えた上での名前集合
            mapped16 = set()
            for n in names16:
                if n in SPLIT_754:
                    mapped16.update(SPLIT_754[n])
                elif n in RENAME_754 and RENAME_754[n]:
                    mapped16.add(RENAME_754[n])
                elif n in RENAME_754 and RENAME_754[n] is None:
                    mapped16.update(SPLIT_754[n])
                else:
                    mapped16.add(n)
            for n in sorted(names16 - set(RENAME_754) - set(SPLIT_754)):
                if n not in names17:
                    only16.append((state, direction, n, m16))
            for n in sorted(names17 - mapped16):
                only17.append((state, direction, n, m17))
            # ID 変更（読み替え後の名前で対応付け）
            id16_by_name = {v: k for k, v in m16.items()}
            id17_by_name = {v: k for k, v in m17.items()}
            for n16, i16 in sorted(id16_by_name.items()):
                targets = []
                if n16 in SPLIT_754:
                    targets = SPLIT_754[n16]
                elif n16 in RENAME_754 and RENAME_754[n16]:
                    targets = [RENAME_754[n16]]
                elif n16 in RENAME_754 and RENAME_754[n16] is None:
                    targets = SPLIT_754[n16]
                else:
                    targets = [n16]
                for n17 in targets:
                    if n17 in id17_by_name and id17_by_name[n17] != i16:
                        id_changes.append((state, direction, n16, n17, i16, id17_by_name[n17]))
            # フィールド構成の比較
            for n16 in sorted(names16):
                if n16 in SPLIT_754:
                    # 分割先それぞれと比べる（1.16 側の 1 形式 vs 分割先の各形式）
                    f16 = fields_of(D16, state, direction, n16)
                    for n17 in SPLIT_754[n16]:
                        if n17 not in names17:
                            continue
                        f17 = fields_of(D17, state, direction, n17)
                        if norm(f16) != norm(f17):
                            diffs.append((state, direction, n16, n17, "SPLIT-FIELD-DIFF", None))
                    continue
                if n16 in RENAME_754 and RENAME_754[n16] is None:
                    # transaction: 方向で ping/pong の片方と比べる
                    want = "ping" if direction == "toClient" else "pong"
                    f16 = fields_of(D16, state, direction, n16)
                    f17 = fields_of(D17, state, direction, want)
                    if f17 is not None and norm(f16) != norm(f17):
                        diffs.append((state, direction, n16, want, "FIELD-DIFF", None))
                    continue
                n17 = RENAME_754.get(n16, n16)
                if n17 not in names17:
                    continue
                f16 = fields_of(D16, state, direction, n16)
                f17 = fields_of(D17, state, direction, n17)
                if norm(f16) != norm(f17):
                    diffs.append((state, direction, n16, n17, "FIELD-DIFF", None))

    # トップレベル types の差分（slot 等の共有型）
    type_diffs = []
    for name in sorted(set(D16["types"]) | set(D17["types"])):
        if norm(D16["types"].get(name)) != norm(D17["types"].get(name)):
            type_diffs.append(name)

    print("== 1.16.5 にだけあるパケット（改名・分割を除く） ==")
    for state, direction, n, m in only16:
        print(f"  {state}/{direction} {n}")
    print(f"  計 {len(only16)} 件")
    print("== 1.17 にだけあるパケット（読み替え後） ==")
    for state, direction, n, m in only17:
        print(f"  {state}/{direction} {n}")
    print(f"  計 {len(only17)} 件")
    print("== ID 変更（読み替え後の名前対応。分割は各分割先と比較） ==")
    for state, direction, n16, n17, i16, i17 in id_changes:
        print(f"  {state}/{direction} {n16} -> {n17}: 0x{i16:02x} -> 0x{i17:02x}")
    print(f"  計 {len(id_changes)} 件")
    print("== フィールド構成の差分 ==")
    for state, direction, n16, n17, kind, _ in diffs:
        print(f"  {state}/{direction} {n16} (1.16) vs {n17} (1.17) [{kind}]")
    print(f"  計 {len(diffs)} 件")
    print("== トップレベル types の差分 ==")
    for name in type_diffs:
        print(f"  {name}")
    print(f"  計 {len(type_diffs)} 件")
    return 0


if __name__ == "__main__":
    sys.exit(main())
