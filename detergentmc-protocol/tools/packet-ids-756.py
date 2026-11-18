#!/usr/bin/env python3
"""PacketIds.java の各エントリに 1.17.1（756）のIDを付ける。

tools/packet-ids-757.py と同じ方法だが、対象は 757→756 の差分。
minecraft-data に pc/1.17.1/protocol.json と pc/1.18/protocol.json
がある（2026-11-15 確認）。そのため 756 のID表は minecraft-data
から直接取る。ViaVersion は使わない（第二の照合としては Wiki を使う）。

  1. PacketIds.java のエントリ（名前・状態・方向・757〜775のID）を
     読む。現在のエントリは 18 引数（757〜775）である。
     ※ 777版の罠：パッチスクリプトが終端子に m.group(19)（16番目のID）
     を使っていたが正しくは m.group(20) だった（docs/PROXY-26.1.md §5）。
     18引数の正規表現ではグループは name=1・state=2・direction=3・
     ID（757〜775）=4〜21 である。756列を挿入するときは終端子が
     m.group(22)（19番目のID）になる。数え直すこと。
     ※ 756列は 757列の「前」に挿入する（引数の並びは 756, 757, 759, ...,
     775 になる）。777のときは末尾への追加、757は先頭への挿入だったが、
     756も先頭への挿入になるため、insert-756-java.py は別設計になる。
     終端子のグループ番号は 19引数版でも group(23) になる。
  2. 各エントリについて、minecraft-data の protocol.json（1.18）の
     同じ状態・方向の mapper 表から、id757 と一致する内部名を1件だけ
     求める。id757 が -1 のエントリは、まず id759（1.19 の表）で特定し、
     それも -1 なら 763〜775 のIDの組でも同じ内部名になるかで特定する
     （777版と同じ写像の考え方）。
  3. その内部名を minecraft-data の pc/1.17.1/protocol.json で引き、
     756のIDを取る。1.17.1 に無い内部名は -1（757で追加されたパケット）。
  4. 検証：同じ内部名で 1.18 のIDを引いた値が、エントリの id757 と
     全件一致すること。763〜775 のIDの組も、内部名の各バージョンの
     値と全件一致すること（777版と同じ）。
  5. 1.17.1にあって表に無いパケットも一覧に出す（新規パケットの候補。
     Ping Request など意図的に入れないものはここで除外する）。

情報源：
  - minecraft-data（PrismarineJS/minecraft-data、master）:
    data/pc/{1.17.1,1.18,1.19,1.20,1.20.2,1.20.3,1.20.5,1.21.1,1.21.3,1.21.4,
    1.21.5,1.21.6,1.21.8,1.21.9,1.21.11,26.1}/protocol.json。
    data/dataPaths.json で確認済み：1.17.1 は pc/1.17.1 と同じ
    protocol.json を使う（1.17.1 も protocol 756）。
    1.17.1・1.18 の protocol.json には configuration 状態が無い
    （設定フェーズは 1.20.2／764 で追加）。そのため CONFIGURATION の
    エントリは 756・757 とも -1 になる。
  - Minecraft Wiki の Packets ページ（1.17.1、protocol 756）による独立照合
    は別途行う（本スクリプトの出力と突き合わせる）。

使い方：
  python3 tools/packet-ids-756.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると detergentmc-protocol/build/remap-data を使う。
  --out を省略すると detergentmc-protocol/build/remap-data/packet-ids-756.json
  に書き出す。終了コード 0 = 全件一意に写像でき、検証も全件通った。
  1 = 写像できない／複数候補／検証不一致のエントリがある。

件数の数え方（777版と同じ定義）：
  変更 = 変更前 >= 0 かつ 変更後 >= 0 かつ ID が違うもの。
  新規 = 変更前 < 0 かつ 変更後 >= 0 のエントリ、または新しい表にあって
         PacketIds に無いパケット。
  削除 = 変更前 >= 0 かつ 変更後 < 0 のもの。
  ※ ここでは「変更前」= 757、「変更後」= 756 とする（757→756 の差分）。

件数ガード（757の§9.7の罠の再発防止）：
  PacketIds.java のエントリが 272件でなければ終了コード 1 で書き出さない。
  756列挿入後（19引数版）に再実行すると 18引数版の正規表現は一致せず
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

# 757（1.18）と 759（1.19）と 756（1.17.1）の protocol.json。
# 759 は id757 が -1 のエントリの特定（第2段階）と検証に使う。
MC_757_FILE = "mc-1.18-protocol.json"
MC_759_FILE = "mc-1.19-protocol.json"
MC_756_FILE = "mc-1.17.1-protocol.json"

# PacketIds の対象外として意図的に入れないパケット（minecraft-data の
# 表にはあるが PacketIds には無いもの）。新規パケットの一覧から除く。
# キーは (Java の状態名, Java の方向名, minecraft-data の内部名)。
EXCLUDED_FROM_TABLE = {("PLAY", "SERVERBOUND", "ping_request")}

# 757〜775 で全て -1 のエントリのうち、1.17.1 にある（かもしれない）
# ものの minecraft-data 内部名。packet-ids-757.py の old_only と同じ
# 考え方だが、756 向けに 1.17.1 の表で確認する。
# 注意：packet-ids-757.json のキー（757・759・763〜775）だけ見ると
# 全て -1 は PLAY_MESSAGE_HEADER 1件だけだが、PacketIds.java には
# 760／761／762 列もあり、PLAY_SHOULD_DISPLAY_CHAT_PREVIEW・
# PLAY_CHAT_PREVIEW・PLAY_CHAT_PREVIEW_TO_SERVER は 759／760 に値を持つ。
# これら3件は id759 で特定できる（下の第2段階）ため OLD_ONLY には
# 入れない。OLD_ONLY に入るのは 757〜775 の18列すべてが -1 のものだけ。
OLD_ONLY = {
    "PLAY_MESSAGE_HEADER": "message_header",
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
    """PacketIds.java の enum エントリを抜き出す（18引数版：757〜775）。

    グループ番号：name=1、state=2、direction=3、ID（757〜775）=4〜21。
    """
    id_pat = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([id_pat] * 18) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        groups = m.groups()
        name, state, direction = groups[0], groups[1], groups[2]
        ids = [int(g, 0) for g in groups[3:]]
        assert len(ids) == 18, f"{name}: IDが18個ではない（{len(ids)}個）"
        entries.append({
            "name": name, "state": state, "direction": direction,
            **{f"id{p}": v for p, v in
               zip((757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769,
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
    for label, fname in (("757", MC_757_FILE), ("759", MC_759_FILE),
                         ("756", MC_756_FILE)):
        path = args.data_dir / fname
        if not path.exists():
            print(f"無い: {path}", file=sys.stderr)
            return 1
        versions[label] = load_mappings(path)

    entries = parse_packet_ids(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds.java のエントリ: {len(entries)} 件")
    if len(entries) != 272:
        # 756列が既に入っている（19引数版）と 18引数版の正規表現は
        # 一致しないため 0件になる。空の写像を書き出してしまう事故を防ぐ。
        print(f"エントリが 272 件ではない（{len(entries)} 件）。"
              "PacketIds.java が 19引数版（756列挿入済み）の可能性がある。",
              file=sys.stderr)
        return 1

    version_labels = [label for label, _ in MC_VERSIONS]
    result = {}
    problems = []
    for e in entries:
        state = STATE_MAP[e["state"]]
        direction = DIR_MAP[e["direction"]]
        ids = {f"id{label}": e[f"id{label}"] for label in version_labels}
        ids["id757"] = e["id757"]
        ids["id759"] = e["id759"]
        mc_name = None
        # 1. id757 から内部名を特定する（1.18 の表で ID が一致するもの）。
        if e["id757"] >= 0:
            table757 = versions["757"].get(state, {}).get(direction, {})
            hits = {n for n, i in table757.items() if i == e["id757"]}
            if len(hits) != 1:
                problems.append(
                    f"{e['name']}: id757={e['id757']:#04x} の候補 {sorted(hits)}")
                continue
            mc_name = hits.pop()
        elif e["id759"] >= 0:
            # 2. id757 が -1 の場合、まず id759（1.19 の表）で特定する。
            # （例：PLAY_CHAT_PREVIEW は 757 で -1 だが 759=0x0c・760=0x0c。）
            table759 = versions["759"].get(state, {}).get(direction, {})
            hits = {n for n, i in table759.items() if i == e["id759"]}
            if len(hits) != 1:
                problems.append(
                    f"{e['name']}: id759={e['id759']:#04x} の候補 {sorted(hits)}")
                continue
            mc_name = hits.pop()
        else:
            # 3. id757・id759 とも -1 の場合、763〜775 のIDの組で特定する。
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
                # 757〜775 で全て -1 のためIDから内部名を特定できない。
                mc_name = OLD_ONLY.get(e["name"])
                if mc_name is None:
                    problems.append(
                        f"{e['name']}: 757〜775 で全て -1 だが既知の旧専用パケットではない")
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
        for v in ["757", "759"] + version_labels:
            table = versions[v].get(state, {}).get(direction, {})
            actual = table.get(mc_name, -1)
            if actual != ids[f"id{v}"]:
                problems.append(
                    f"{e['name']}: {mc_name} の {v} は {actual} だが "
                    f"エントリは {ids[f'id{v}']}")
                mc_name = None
                break
        if mc_name is None:
            continue
        # 756のIDを取る。
        if e["state"] == "HANDSHAKING":
            # minecraft-data の handshaking/toServer は set_protocol 0x00 と
            # legacy_server_list_ping 0xFE で、後者は PacketIds の対象外。
            id756 = 0x00
        else:
            table756 = versions["756"].get(state, {}).get(direction, {})
            id756 = table756.get(mc_name, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"],
            "direction": e["direction"], **ids, "id756": id756,
        }

    print(f"一意に写像できた: {len(result)} 件")

    # 件数（定義は docstring の「件数の数え方」を参照。変更前=757）。
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

    total = count_diff("id757", "id756")
    print(f"757→756 全体: 変更 {total[0]} 件・新規 {total[1]} 件・削除 {total[2]} 件")
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    scopes = [("HANDSHAKING", "SERVERBOUND"), ("LOGIN", "CLIENTBOUND"),
              ("LOGIN", "SERVERBOUND"), ("CONFIGURATION", "CLIENTBOUND"),
              ("CONFIGURATION", "SERVERBOUND"), ("PLAY", "CLIENTBOUND"),
              ("PLAY", "SERVERBOUND")]
    for scope in scopes:
        c, n, rm, changed_list = count_diff("id757", "id756", scope)
        print(f"  {scope[0]}/{scope[1]}: 変更 {c} 件・新規 {n} 件・削除 {rm} 件")
        for name in changed_list:
            r = result[name]
            print(f"    {name}: 757={r['id757']:#04x} -> 756={r['id756']:#04x} "
                  f"({r['mc']})")
    removed_list = [n for n, r in result.items()
                    if r["id757"] >= 0 and r["id756"] < 0]
    print(f"757で削除（-1）: {removed_list}")
    revived_list = [n for n, r in result.items()
                    if r["id757"] < 0 and r["id756"] >= 0]
    print(f"757で復活（757で-1・756で値あり）: {revived_list}")

    # 1.17.1にあって表に無いパケット（新規パケットの候補）。
    # status 状態はこの表の対象外（PacketIds の javadoc 参照）のため除く。
    # Handshake は定数のため除く。
    known_mc = {(r["state"], r["direction"], r["mc"])
                for r in result.values()}
    new_packets = []
    for state in ("login", "play"):
        for direction in ("toClient", "toServer"):
            table = versions["756"].get(state, {}).get(direction, {})
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                key = (state_names[state], dir_names[direction], mc_name)
                if key in EXCLUDED_FROM_TABLE:
                    continue
                if key not in known_mc:
                    new_packets.append((key[0], key[1], mc_name, i))
    print(f"1.17.1にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out_path = args.out or args.data_dir / "packet-ids-756.json"
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
