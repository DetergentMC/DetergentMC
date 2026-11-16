#!/usr/bin/env python3
"""PacketIds.java の各エントリに 1.16.3（753）のIDを付ける。

tools/packet-ids-754.py と同じ方法だが、対象は 754→753 の差分。
minecraft-data の dataPaths.json で確認済み：1.16.3 の `protocol` は
pc/1.16.2 を指す（1.16.4・1.16.5 も同じ pc/1.16.2。1.16 は pc/1.16、
1.16.1 は pc/1.16.1 なので注意）。そのため 753 のID表は
data/pc/1.16.2/protocol.json から取る。実測ではこのファイルは 754 の
入力（mc-1.16-protocol.json）とバイト単位で完全一致する（`cmp` で確認）。
ViaVersion は使わない（第二の照合としては Wiki を使う）。

  1. PacketIds.java のエントリ（名前・状態・方向・754〜775のID）を
     読む。現在のエントリは 21 引数（754〜775）である。
     グループ番号：name=1・state=2・direction=3・ID（754〜775）=4〜24・
     終端子=group(25)。
     ※ 753列は 754列の「前」に挿入する（引数の並びは 753, 754, 755, 756, 757,
     759, ..., 775 になる）。
  2. 各エントリについて、minecraft-data の protocol.json（1.17）の
     同じ状態・方向の mapper 表から、id755 と一致する内部名を1件だけ
     求める。id755 が -1 のエントリは、まず id759（1.19 の表）で特定し、
     それも -1 なら id757（1.18 の表）で特定し、さらに -1 なら
     763〜775 のIDの組でも同じ内部名になるかで特定する
     （755版と同じ写像の考え方）。
  3. その内部名を minecraft-data の pc/1.16.2/protocol.json で引き、
     753のIDを取る。1.16.3 に無い内部名は -1（1.17で追加されたパケット）。
     内部名の改名・分割は `RENAME_753` で読み替える（下記参照）。
     `RENAME_753` の内容は `RENAME_754` と同じ（753 の入力ファイルは
     754 の入力ファイルとバイト同一のため）。
  4. 検証：同じ内部名で 1.17 のIDを引いた値が、エントリの id755 と
     全件一致すること。754・757・759・763〜775 のIDの組も、内部名の
     各バージョンの値と全件一致すること（754版と同じ。ただし
     destroy_entity は 1.17.1 で entity_destroy に改名されたため、
     755以外との照合では 1.17.1側の名前で引く。754・753 との照合では
     1.16.2側の名前（RENAME_753 で読み替えたもの）で引く）。
  5. 1.16.3にあって表に無いパケットも一覧に出す（新規パケットの候補。
     Ping Request など意図的に入れないものはここで除外する）。

1.17 と 1.16.3 の間の内部名の改名・分割（minecraft-data の
protocol.json の比較で実測。754版と同じ。753 の入力は 754 の入力と
バイト同一のため改名・分割も同じ）：
  - destroy_entity（1.17）→ entity_destroy（1.16.3）。1件の改名。
    PacketIds.java のエントリ名は PLAY_ENTITY_DESTROY（1.17.1側の名前）。
  - combat_event（1.16.3、0x31）→ enter／end／death_combat_event
    （1.17）に3分割。3エントリとも 753 では 0x31。
  - title（1.16.3、0x4f）→ set_title_text／set_title_subtitle／
    set_title_time／clear_titles／action_bar（1.17）に5分割。
    5エントリとも 753 では 0x4f。
  - world_border（1.16.3、0x3d）→ initialize_world_border／
    world_border_center／world_border_lerp_size／world_border_size／
    world_border_warning_delay／world_border_warning_reach（1.17）に6分割。
    6エントリとも 753 では 0x3d。
  - transaction（1.16.3）→ ping（1.17、クライアント行き 0x30）／
    pong（1.17、サーバー行き 0x1d）に置き換え。PLAY_PING の 753 は
    1.16.3 の transaction（クライアント行き 0x11）、PLAY_PONG の 753 は
    1.16.3 の transaction（サーバー行き 0x07）。
    ViaVersion の Protocol1_16_4To1_17 でも 1.16.2 側は
    CONTAINER_ACK（0x11／0x07）で、1.17 側は PING／PONG である。
  - entity（1.16.3、クライアント行き 0x2a。ViaVersion では MOVE_ENTITY）：
    1.17 で削除され、PacketIds のどのバージョンにもエントリが無い。
    ViaVersion の Protocol1_16_4To1_17 でも未登録（落とされる）。
    そのため表の対象外とし、EXCLUDED_FROM_TABLE に入れる
    （755版の ping_request と同じ扱い）。

情報源：
  - minecraft-data（PrismarineJS/minecraft-data、master）:
    data/pc/{1.16.2,1.17,1.17.1,1.18,1.19,1.20,1.20.2,1.20.3,1.20.5,1.21.1,1.21.3,1.21.4,
    1.21.5,1.21.6,1.21.8,1.21.9,1.21.11,26.1}/protocol.json。
    data/dataPaths.json で確認済み：1.16.5 は pc/1.16.2 と同じ
    protocol.json を使う（1.16.5 も protocol 754）。
    1.16.3・1.16.5・1.17 の protocol.json には configuration 状態が無い
    （設定フェーズは 1.20.2／764 で追加）。そのため CONFIGURATION の
    エントリは 753・754・755 とも -1 になる。
  - Minecraft Wiki の Packets ページ（1.16.3、protocol 753）による独立照合
    は別途行う（本スクリプトの出力と突き合わせる）。

使い方：
  python3 tools/packet-ids-753.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると detergentmc-protocol/build/remap-data を使う。
  --out を省略すると detergentmc-protocol/build/remap-data/packet-ids-753.json
  に書き出す。終了コード 0 = 全件一意に写像でき、検証も全件通った。
  1 = 写像できない／複数候補／検証不一致のエントリがある。

件数の数え方（755版と同じ定義）：
  変更 = 変更前 >= 0 かつ 変更後 >= 0 かつ ID が違うもの。
  新規 = 変更前 < 0 かつ 変更後 >= 0 のエントリ、または新しい表にあって
         PacketIds に無いパケット。
  削除 = 変更前 >= 0 かつ 変更後 < 0 のもの。
  ※ ここでは「変更前」= 754、「変更後」= 753 とする（754→753 の差分）。

件数ガード（757の§9.7の罠の再発防止）：
  PacketIds.java のエントリが 272件でなければ終了コード 1 で書き出さない。
  753列挿入後（22引数版）に再実行すると 21引数版の正規表現は一致せず
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

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

# minecraft-data の protocol.json 13ファイル（763〜775）。内部名の特定用。
MC_VERSIONS = (("763", "mc-1.20-protocol.json"),
               ("764", "mc-1.20.2-protocol.json"),
               ("765", "mc-1.20.3-protocol.json"),
               ("766", "mc-1.20.5-protocol.json"),
               ("767", "mc-1.21.1-protocol.json"),
               ("768", "mc-1.21.3-protocol.json"),
               ("769", "mc-1.21.4-protocol.json"),
               ("770", "mc-1.21.5-protocol.json"),
               ("771", "mc-1.21.6-protocol.json"),
               ("772", "mc-1.21.8-protocol.json"),
               ("773", "mc-1.21.9-protocol.json"),
               ("774", "mc-1.21.11-protocol.json"),
               ("775", "mc-26.1-protocol.json"))

# 755（1.17）と 757（1.18）と 759（1.19）と 754（1.16.5）の protocol.json。
# 759 は id755 が -1 のエントリの特定（第2段階）と検証に使う。
MC_755_FILE = "mc-1.17-protocol.json"
MC_757_FILE = "mc-1.18-protocol.json"
MC_759_FILE = "mc-1.19-protocol.json"
MC_754_FILE = "mc-1.16-protocol.json"
MC_753_FILE = "mc-1.16.3-protocol.json"

# PacketIds の対象外として意図的に入れないパケット（minecraft-data の
# 表にはあるが PacketIds には無いもの）。新規パケットの一覧から除く。
# キーは (Java の状態名, Java の方向名, minecraft-data の内部名)。
EXCLUDED_FROM_TABLE = {("PLAY", "SERVERBOUND", "ping_request"),
                       # entity（1.16.3 クライアント行き 0x2a。ViaVersion では
                       # MOVE_ENTITY）は 1.17 で削除され、PacketIds のどの
                       # バージョンにもエントリが無い。ViaVersion の
                       # Protocol1_16_4To1_17 でも未登録（落とされる）。
                       ("PLAY", "CLIENTBOUND", "entity")}

# 755〜775 で全て -1 のエントリのうち、1.17 にある（かもしれない）
# ものの minecraft-data 内部名。packet-ids-755.py の OLD_ONLY と同じ。
OLD_ONLY = {
    "PLAY_MESSAGE_HEADER": "message_header",
}

# 1.17 と 1.16.3 の間の内部名の改名・分割（minecraft-data の protocol.json
# の比較で実測。キーは 1.17 側（PacketIds の写像で使う名前）、値は 1.16.3 側の名前。
# 753 の入力は 754 の入力とバイト同一のため 754版と同じ内容。
RENAME_753 = {
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
# 逆向き（1.16.3 の表の名前 → 1.17 側の名前）。新規パケットの一覧用。
# 1対多の分割（combat_event／title／world_border／transaction）は
# 1.17 側の代表名を1つだけ入れる（新規一覧は「表に無い」判定だけに使う）。
RENAME_753_REVERSE = {
    "entity_destroy": "destroy_entity",
    "combat_event": "enter_combat_event",
    "title": "set_title_text",
    "world_border": "initialize_world_border",
    "transaction": "ping",
}

# 検証用の名前読み替え：destroy_entity は 1.17.1 で entity_destroy に
# 改名されたため、755以外（754・753・757・759・763〜775）との照合では
# 1.17.1側の名前で引く。755 との照合では 1.17側の名前のまま。
# 754・753 との照合では 1.16.2側の名前（RENAME_753 で読み替えたもの）で引く。
VERIFY_RENAME_AFTER_755 = {
    "destroy_entity": "entity_destroy",
}


def load_mappings(path):
    """protocol.json から {状態: {方向: {内部名: id}}} を作る。"""
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    out = {}
    for state, dirs in data.items():
        if state == "types":
            continue
        out[state] = {}
        for direction, content in dirs.items():
            packet = content["types"]["packet"]
            # ["container", [{"name": "name", "type": ["mapper", {...}]}, ...]]
            mapper = packet[1][0]["type"][1]["mappings"]
            out[state][direction] = {name: int(hex_id, 16)
                                     for hex_id, name in mapper.items()}
    return out


def parse_packet_ids(java_text):
    """PacketIds.java の enum エントリを抜き出す（21引数版：754〜775）。

    グループ番号：name=1、state=2、direction=3、ID（754〜775）=4〜24。
    """
    id_pat = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([id_pat] * 21) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        groups = m.groups()
        name, state, direction = groups[0], groups[1], groups[2]
        ids = [int(g, 0) for g in groups[3:]]
        assert len(ids) == 21, f"{name}: IDが21個ではない（{len(ids)}個）"
        entries.append({
            "name": name, "state": state, "direction": direction,
            **{f"id{p}": v for p, v in
               zip((754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
                    770, 771, 772, 773, 774, 775), ids)},
        })
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    versions = {}
    for label, fname in MC_VERSIONS:
        path = args.data_dir / fname
        if not path.exists():
            print(f"無い: {path}（先に protocol.json を置くこと）", file=sys.stderr)
            return 1
        versions[label] = load_mappings(path)
    for label, fname in (("755", MC_755_FILE), ("757", MC_757_FILE),
                         ("759", MC_759_FILE), ("754", MC_754_FILE),
                         ("753", MC_753_FILE)):
        path = args.data_dir / fname
        if not path.exists():
            print(f"無い: {path}", file=sys.stderr)
            return 1
        versions[label] = load_mappings(path)

    entries = parse_packet_ids(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds.java のエントリ: {len(entries)} 件")
    if len(entries) != 272:
        # 753列が既に入っている（22引数版）と 21引数版の正規表現は
        # 一致しないため 0件になる。空の写像を書き出してしまう事故を防ぐ。
        print(f"エントリが 272 件ではない（{len(entries)} 件）。"
              "PacketIds.java が 22引数版（753列挿入済み）の可能性がある。",
              file=sys.stderr)
        return 1

    version_labels = [label for label, _ in MC_VERSIONS]
    result = {}
    problems = []
    for e in entries:
        state = STATE_MAP[e["state"]]
        direction = DIR_MAP[e["direction"]]
        ids = {f"id{label}": e[f"id{label}"] for label in version_labels}
        ids["id754"] = e["id754"]
        ids["id755"] = e["id755"]
        ids["id756"] = e["id756"]
        ids["id757"] = e["id757"]
        ids["id759"] = e["id759"]
        mc_name = None
        # 1. id755 から内部名を特定する（1.17 の表で ID が一致するもの）。
        if e["id755"] >= 0:
            table755 = versions["755"].get(state, {}).get(direction, {})
            hits = {n for n, i in table755.items() if i == e["id755"]}
            if len(hits) != 1:
                problems.append(
                    f"{e['name']}: id755={e['id755']:#04x} の候補 {sorted(hits)}")
                continue
            mc_name = hits.pop()
        elif e["id759"] >= 0:
            # 2. id755 が -1 の場合、まず id759（1.19 の表）で特定する。
            table759 = versions["759"].get(state, {}).get(direction, {})
            hits = {n for n, i in table759.items() if i == e["id759"]}
            if len(hits) != 1:
                problems.append(
                    f"{e['name']}: id759={e['id759']:#04x} の候補 {sorted(hits)}")
                continue
            mc_name = hits.pop()
        elif e["id757"] >= 0:
            # 3. id755・id759 とも -1 の場合、id757（1.18 の表）で特定する。
            table757 = versions["757"].get(state, {}).get(direction, {})
            hits = {n for n, i in table757.items() if i == e["id757"]}
            if len(hits) != 1:
                problems.append(
                    f"{e['name']}: id757={e['id757']:#04x} の候補 {sorted(hits)}")
                continue
            mc_name = hits.pop()
        else:
            # 4. id755・id759・id757 とも -1 の場合、763〜775 のIDの組で特定する。
            candidates = set()
            has_any = False
            for v in version_labels:
                if ids[f"id{v}"] < 0:
                    continue
                has_any = True
                table = versions[v].get(state, {}).get(direction, {})
                hits = {n for n, i in table.items() if i == ids[f"id{v}"]}
                candidates = hits if not candidates else candidates & hits
            if not has_any:
                # 755〜775 で全て -1 のためIDから内部名を特定できない。
                mc_name = OLD_ONLY.get(e["name"])
                if mc_name is None:
                    problems.append(
                        f"{e['name']}: 755〜775 で全て -1 だが既知の旧専用パケットではない")
                    continue
            elif len(candidates) != 1:
                problems.append(
                    f"{e['name']} ({e['state']}/{e['direction']} "
                    + " ".join(f"{v}={ids[f'id{v}']}" for v in version_labels)
                    + f"): 候補 {sorted(candidates)}")
                continue
            else:
                mc_name = candidates.pop()
        # 検証：内部名の各バージョンの値がエントリのIDと全件一致すること。
        # 755 は 1.17側の名前のまま、757・759・763〜775 は 1.17.1側の名前で引く。
        for v in ["755", "757", "759"] + version_labels:
            table = versions[v].get(state, {}).get(direction, {})
            check_name = (mc_name if v == "755"
                          else VERIFY_RENAME_AFTER_755.get(mc_name, mc_name))
            actual = table.get(check_name, -1)
            if actual != ids[f"id{v}"]:
                problems.append(
                    f"{e['name']}: {check_name} の {v} は {actual} だが "
                    f"エントリは {ids[f'id{v}']}")
                mc_name = None
                break
        if mc_name is None:
            continue
        # 753のIDを取る。改名・分割がある場合は 1.16.3 側の名前で引く。
        if e["state"] == "HANDSHAKING":
            # minecraft-data の handshaking/toServer は set_protocol 0x00 と
            # legacy_server_list_ping 0xFE で、後者は PacketIds の対象外。
            id753 = 0x00
        else:
            table753 = versions["753"].get(state, {}).get(direction, {})
            mc_name_753 = RENAME_753.get(mc_name, mc_name)
            id753 = table753.get(mc_name_753, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"],
            "direction": e["direction"], **ids, "id753": id753,
        }

    print(f"一意に写像できた: {len(result)} 件")

    # 件数（定義は docstring の「件数の数え方」を参照。変更前=755）。
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

    total = count_diff("id754", "id753")
    print(f"754→753 全体: 変更 {total[0]} 件・新規 {total[1]} 件・削除 {total[2]} 件")
    scopes = [("HANDSHAKING", "SERVERBOUND"), ("LOGIN", "CLIENTBOUND"),
              ("LOGIN", "SERVERBOUND"), ("CONFIGURATION", "CLIENTBOUND"),
              ("CONFIGURATION", "SERVERBOUND"), ("PLAY", "CLIENTBOUND"),
              ("PLAY", "SERVERBOUND")]
    for scope in scopes:
        c, n, rm, changed_list = count_diff("id754", "id753", scope)
        print(f"  {scope[0]}/{scope[1]}: 変更 {c} 件・新規 {n} 件・削除 {rm} 件")
        for name in changed_list:
            r = result[name]
            print(f"    {name}: 754={r['id754']:#04x} -> 753={r['id753']:#04x} "
                  f"({r['mc']})")
    removed_list = [n for n, r in result.items()
                    if r["id754"] >= 0 and r["id753"] < 0]
    print(f"754で削除（-1）: {removed_list}")
    revived_list = [n for n, r in result.items()
                    if r["id754"] < 0 and r["id753"] >= 0]
    print(f"754で復活（754で-1・753で値あり）: {revived_list}")

    # 1.16.3にあって表に無いパケット（新規パケットの候補）。
    # status 状態はこの表の対象外（PacketIds の javadoc 参照）のため除く。
    # Handshake は定数のため除く。
    # 1.16.3 の内部名は改名を 1.17 側の名前に読み替えて突き合わせる。
    # 分割パケット（combat_event 等）は代表名1つで突き合わせるため、
    # RENAME_753_REVERSE に無い 1.16.3 名はそのまま使う。
    known_mc = {(r["state"], r["direction"], r["mc"])
                for r in result.values()}
    # 分割先の 1.17 名も「既知」として扱う（combat_event の3分割先など）。    # 1.16.3 名 → 1.17 名の逆引きを、分割先すべてについて作る。
    reverse_full = {}
    for name117, name116 in RENAME_753.items():
        reverse_full.setdefault(name116, set()).add(name117)
    new_packets = []
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    for state in ("login", "play"):
        for direction in ("toClient", "toServer"):
            table = versions["753"].get(state, {}).get(direction, {})
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                key = (state_names[state], dir_names[direction], mc_name)
                if key in EXCLUDED_FROM_TABLE:
                    continue
                # 1.16.3 名に対応する 1.17 名のいずれかが表にあれば既知。
                names117 = reverse_full.get(mc_name, {mc_name})
                if any((state_names[state], dir_names[direction], n) in known_mc
                       for n in names117):
                    continue
                new_packets.append((key[0], key[1], mc_name, i))
    print(f"1.16.3にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out_path = args.out or args.data_dir / "packet-ids-753.json"
    out_path.write_text(json.dumps(result, indent=2, ensure_ascii=False)
                        + "\n", encoding="utf-8")
    print(f"写像を {out_path} に書き出した。")

    if problems:
        print("写像できないエントリ:", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
