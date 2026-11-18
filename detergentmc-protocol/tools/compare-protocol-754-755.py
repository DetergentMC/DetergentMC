#!/usr/bin/env python3
"""mc-1.16-protocol.json (pc/1.16.2) と mc-1.17-protocol.json の全パケット・全方向の突き合わせ。

755 の ChunkTranslator 担当がやった compare-protocol.py と同じ方法。
入力は detergentmc-protocol/build/remap-data/ の minecraft-data protocol.json
2 件。出力は (1) ID だけ違うもの (2) フィールド構成が違うもの (3) 片方にしか無いもの。

改名・分割は Stage A §8.2 の実測（packet-ids-754.json と同じ）で読み替える：
  destroy_entity (1.17) <-> entity_destroy (1.16.5)
  combat_event (1.16.5) <-> enter/end/death_combat_event (1.17)
  title (1.16.5) <-> set_title_text/set_title_subtitle/set_title_time/clear_titles/action_bar (1.17)
  world_border (1.16.5) <-> initialize_world_border/world_border_center/world_border_lerp_size/
                          world_border_size/world_border_warning_delay/world_border_warning_reach (1.17)
  transaction (1.16.5) <-> ping (行き) / pong (来) (1.17)
  entity (1.16.5 のみ、行き 0x2a) / sculk_vibration_signal (1.17 のみ)

フィールド比較は packet_<name> の JSON を正規化（dict のキーをソート）して比較する。
共有 types（slot 等）の差分は別途トップレベル types 同士で比較する。
"""

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = HERE.parent / "build" / "remap-data"

# 1.17 名 -> 1.16.5 名（分割パケットは 1.17 側の複数名が 1.16.5 側の 1 名に対応）
RENAME_117_TO_116 = {
    "destroy_entity": "entity_destroy",
    "enter_combat_event": "combat_event",
    "end_combat_event": "combat_event",
    "death_combat_event": "combat_event",
    "set_title_text": "title",
    "set_title_subtitle": "title",
    "set_title_time": "title",
    "clear_titles": "title",
    "action_bar": "title",
    "initialize_world_border": "world_border",
    "world_border_center": "world_border",
    "world_border_lerp_size": "world_border",
    "world_border_size": "world_border",
    "world_border_warning_delay": "world_border",
    "world_border_warning_reach": "world_border",
    "ping": "transaction",
    "pong": "transaction",
}
# 方向の違いがあるものだけ別扱いにするための方向付き読み替え
# （ping は行き・pong は来で、どちらも 1.16.5 では transaction）
DIRECTIONAL_RENAME = {
    ("play", "toClient", "ping"): "transaction",
    ("play", "toServer", "pong"): "transaction",
}


def load(name):
    with open(DATA / name, encoding="utf-8") as f:
        return json.load(f)


def mapper(data, state, direction):
    """state/direction の mapper（id -> 内部名）を返す。"""
    packet = data[state][direction]["types"]["packet"]
    # packet は ["container", [ {"name": "name", "type": ["mapper", ...]}, {"name": "params", ...} ]]
    fields = packet[1]
    name_field = next(f for f in fields if f["name"] == "name")
    mappings = name_field["type"][1]["mappings"]
    return {int(k, 16): v for k, v in mappings.items()}


def packet_types(data, state, direction):
    """state/direction の packet_<name> -> 定義 JSON を返す。"""
    return {k: v for k, v in data[state][direction]["types"].items() if k.startswith("packet_")}


def canon(obj):
    return json.dumps(obj, sort_keys=True, ensure_ascii=False)


def main():
    d116 = load("mc-1.16-protocol.json")
    d117 = load("mc-1.17-protocol.json")

    id_only = []   # (state, direction, name116, id116, id117)
    field_diff = []  # (state, direction, name116, name117, id116, id117, detail)
    only116 = []
    only117 = []
    identical = 0

    for state in ["handshaking", "status", "login", "play"]:
        for direction in ["toClient", "toServer"]:
            m116 = mapper(d116, state, direction)
            m117 = mapper(d117, state, direction)
            t116 = packet_types(d116, state, direction)
            t117 = packet_types(d117, state, direction)
            # 1.17 名 -> 1.16.5 名へ読み替えた 1.17 側の写像
            m117_by116 = {}
            for pid, name117 in m117.items():
                # 読み替えは play 状態だけ（status の ping は両バージョンで同じ ping）。
                if state == "play":
                    name116 = DIRECTIONAL_RENAME.get((state, direction, name117),
                                                     RENAME_117_TO_116.get(name117, name117))
                else:
                    name116 = name117
                m117_by116.setdefault(name116, []).append((pid, name117))
            names116 = set(m116.values())
            names117_by116 = set(m117_by116.keys())
            for name116 in sorted(names116 - names117_by116):
                only116.append((state, direction, name116, m116 and
                                next(i for i, n in m116.items() if n == name116)))
            for name116 in sorted(names117_by116 - names116):
                for pid, name117 in m117_by116[name116]:
                    only117.append((state, direction, name117, pid))
            for name116 in sorted(names116 & names117_by116):
                id116 = next(i for i, n in m116.items() if n == name116)
                entries117 = m117_by116[name116]
                def116 = t116.get("packet_" + name116)
                for id117, name117 in entries117:
                    def117 = t117.get("packet_" + name117)
                    if def116 is None or def117 is None:
                        field_diff.append((state, direction, name116, name117, id116, id117,
                                           "packet_ 定義が片方に無い"))
                        continue
                    if canon(def116) == canon(def117):
                        identical += 1
                        if id116 != id117:
                            id_only.append((state, direction, name116, id116, id117))
                    else:
                        field_diff.append((state, direction, name116, name117, id116, id117,
                                           "フィールド構成が違う"))

    print(f"同一フィールド（IDが同じものも含む）: {identical} 件")
    print()
    print(f"== IDだけ違うもの: {len(id_only)} 件 ==")
    for state, direction, name, i116, i117 in id_only:
        print(f"  {state}/{direction} {name}: 754=0x{i116:02x} 755=0x{i117:02x}")
    print()
    print(f"== フィールド構成が違うもの: {len(field_diff)} 件 ==")
    for state, direction, n116, n117, i116, i117, detail in field_diff:
        print(f"  {state}/{direction} {n116} (754=0x{i116:02x}) <-> {n117} (755=0x{i117:02x}): {detail}")
    print()
    print(f"== 1.16.5 にしか無いもの: {len(only116)} 件 ==")
    for state, direction, name, pid in only116:
        print(f"  {state}/{direction} {name}: 0x{pid:02x}")
    print()
    print(f"== 1.17 にしか無いもの: {len(only117)} 件 ==")
    for state, direction, name, pid in only117:
        print(f"  {state}/{direction} {name}: 0x{pid:02x}")
    print()
    # 共有 types の差分
    print("== 共有 types の差分 ==")
    t116 = d116["types"]
    t117 = d117["types"]
    diff_types = 0
    for k in sorted(set(t116) | set(t117)):
        if canon(t116.get(k)) != canon(t117.get(k)):
            diff_types += 1
            print(f"  types.{k}: 違う")
    if diff_types == 0:
        print("  （差分なし）")
    print()
    print(f"件数ガード: toClient/play 1.16.5={len(mapper(d116,'play','toClient'))}件 "
          f"1.17={len(mapper(d117,'play','toClient'))}件 / "
          f"toServer/play 1.16.5={len(mapper(d116,'play','toServer'))}件 "
          f"1.17={len(mapper(d117,'play','toServer'))}件")
    return 0


if __name__ == "__main__":
    sys.exit(main())
