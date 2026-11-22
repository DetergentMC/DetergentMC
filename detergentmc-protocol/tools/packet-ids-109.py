"""109（1.9.2）のパケットID表とレジストリが 110（1.9.4）とどう違うかを
独立したデータ源で照合し、PacketIds.java の 109 列の正しさを裏付ける。

番号対応（Stage A 実測）：**109 は 1.9.2 本体**。実バニラ 1.9.2 の
ステータス応答が protocol 109 を返すことを先行調査で確認済み
（legacy-prep-109 の boot-1.9.2/status-response-1.9.2.json）。
jar の SHA-1 は piston-meta の version json と照合済み
（2b95cc7b136017e064c46d04a5825fe4cfa1be30、8,848,002 バイト）。

データ源（互いに独立）：
  A = minecraft-data pc/1.9.2 の protocol.json（109 自身の抽出。
      version.json が version: 109 を明示）。
  B = minecraft-data pc/1.9.4 の protocol.json（110 自身の抽出。
      version.json が version: 110 を明示）。

照合内容：
  1. A と B の全面比較：総数 119 ⇔ 118、片側のみは play／
     クライアント行きの update_sign だけ、ID 差分は後続6件の
     +1 ずれだけ（sound_effect 0x47⇔0x46 等）、types 節は完全一致、
     フィールド構造差分は play／クライアント行きの
     packet_map_chunk（110 で blockEntities 追加）と
     packet_update_sign（109 のみ）だけ。
  2. PacketIds.java の全エントリ（284件。新規の
     PLAY_UPDATE_SIGN_CLIENTBOUND を含む）について、状態・方向
     ごとに：id110 >= 0 の ID 集合が B の ID 集合から「表に載せ
     ない1件（handshaking の legacy_server_list_ping）」を除いた
     ものと一致し、id109 >= 0 の ID 集合が A の ID 集合から
     同じ1件を除いたものと一致すること。
     ずれる6件は enum 名でも固定する。
  3. レジストリ同一の裏付け：dataPaths.json で 1.9.2／1.9.4 とも
     全レジストリが pc/1.9 に解決すること、実 jar 同士の
     en_US.lang の差分が /stopsound のコマンド文言5行だけの
     こと（ブロック・アイテム・エンティティ等の登録名に差分なし）。
  4. ワイヤ実測の記録が先行調査フォルダにあること（Keep Alive
     varint の読み切り値 7281059）。

使い方：
  python3 tools/packet-ids-109.py [--prep109 DIR] [--prep110 DIR]
  終了コード 0 = 全照合成立、1 = 問題あり。
"""

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAVA_FILE = (SCRIPT_DIR.parent / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")
MCDATA = Path("/home/hatch/mc-client/node_modules/minecraft-data"
              "/minecraft-data/data")

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

# play／クライアント行きで 109 が 110 より +1 ずれる6件（mcdata名）。
SHIFTED = {"sound_effect", "playerlist_header", "collect",
           "entity_teleport", "entity_update_attributes", "entity_effect"}
# 同じ6件の enum 名 → (id110, id109)。
SHIFTED_ENUM = {
    "PLAY_SOUND_EFFECT": (0x46, 0x47),
    "PLAY_PLAYERLIST_HEADER": (0x47, 0x48),
    "PLAY_COLLECT": (0x48, 0x49),
    "PLAY_ENTITY_TELEPORT": (0x49, 0x4a),
    "PLAY_ENTITY_UPDATE_ATTRIBUTES": (0x4a, 0x4b),
    "PLAY_ENTITY_EFFECT": (0x4b, 0x4c),
}


def load_protocol_json(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    out = {}
    for state, dirs in data.items():
        if state == "types":
            continue
        out[state] = {}
        for direction, content in dirs.items():
            packet = content["types"]["packet"]
            mapper = packet[1][0]["type"][1]["mappings"]
            out[state][direction] = {name: int(h, 16)
                                     for h, name in mapper.items()}
    return out, data


def parse_entries(java_text):
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 38) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        vals = [int(g, 16) if g.startswith("0x") else -1
                for g in m.groups()[3:]]
        entries.append((m.group(1), m.group(2), m.group(3), vals))
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prep109", type=Path,
                        default=Path.home() / "workspace/legacy-prep-109")
    parser.add_argument("--prep110", type=Path,
                        default=Path.home() / "workspace/legacy-prep-110")
    args = parser.parse_args()
    problems = []

    a, raw_a = load_protocol_json(args.prep109 / "mcdata/1.9.2/protocol.json")
    b, raw_b = load_protocol_json(args.prep110 / "mcdata/1.9.4/protocol.json")

    # 1) A と B の全面比較
    total_a = total_b = 0
    for state in a:
        for direction in a[state]:
            da, db = a[state][direction], b[state][direction]
            total_a += len(da)
            total_b += len(db)
            only_a = set(da) - set(db)
            only_b = set(db) - set(da)
            if (state, direction) == ("play", "toClient"):
                if only_a != {"update_sign"} or only_b:
                    problems.append(
                        f"play/toClient の片側のみが想定外: "
                        f"only109={sorted(only_a)} only110={sorted(only_b)}")
                diff = {k for k in set(da) & set(db) if da[k] != db[k]}
                if diff != SHIFTED:
                    problems.append(f"ID差分の集合が想定外: {sorted(diff)}")
                for k in diff:
                    if da[k] != db[k] + 1:
                        problems.append(f"{k}: +1 ずれでない "
                                        f"109={da[k]:#x} 110={db[k]:#x}")
            else:
                if only_a or only_b:
                    problems.append(f"{state}/{direction} に片側のみ: "
                                    f"{sorted(only_a)} / {sorted(only_b)}")
                if da != db:
                    problems.append(f"{state}/{direction} の ID 表が不一致")
    print(f"パケット総数: 109={total_a} 110={total_b}")
    if total_a != 119 or total_b != 118:
        problems.append(f"総数が 119⇔118 でない: {total_a}⇔{total_b}")
    if raw_a["types"] != raw_b["types"]:
        problems.append("types 節が一致しない")
    fdiff = set()
    for state in a:
        for direction in a[state]:
            ta = raw_a[state][direction]["types"]
            tb = raw_b[state][direction]["types"]
            for key in ta:
                if key.startswith("packet_") and ta[key] != tb.get(key):
                    fdiff.add((state, direction, key))
            for key in tb:
                if key.startswith("packet_") and key not in ta:
                    fdiff.add((state, direction, key))
    expect_fdiff = {("play", "toClient", "packet_map_chunk"),
                    ("play", "toClient", "packet_update_sign")}
    print(f"フィールド構造差分: {sorted(fdiff)}")
    if fdiff != expect_fdiff:
        problems.append(f"フィールド構造差分が想定外: {sorted(fdiff)}")

    # 2) PacketIds.java の 109 列／110 列と A／B の ID 集合の照合
    entries = parse_entries(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds エントリ数: {len(entries)}")
    if len(entries) != 284:
        problems.append(f"エントリ数が 284 でない: {len(entries)}")
    unlisted_total = 0
    for state_j, state in STATE_MAP.items():
        for dir_j, direction in DIR_MAP.items():
            ids110 = {v[1] for (_, s, d, v) in entries
                      if s == state_j and d == dir_j and v[1] >= 0}
            ids109 = {v[0] for (_, s, d, v) in entries
                      if s == state_j and d == dir_j and v[0] >= 0}
            b_ids = set(b.get(state, {}).get(direction, {}).values())
            a_ids = set(a.get(state, {}).get(direction, {}).values())
            unlisted = b_ids - ids110
            unlisted_total += len(unlisted)
            if ids110 != b_ids - unlisted:
                problems.append(f"{state}/{direction}: id110 集合が B と不一致")
            if ids109 != a_ids - unlisted:
                problems.append(
                    f"{state}/{direction}: id109 集合が A と不一致 "
                    f"(表={sorted(ids109)}, A-未掲載={sorted(a_ids - unlisted)})")
    print(f"表に載せないパケット: {unlisted_total} 件")
    if unlisted_total != 1:
        problems.append(f"未掲載が 1 件（legacy_server_list_ping）でない: "
                        f"{unlisted_total}")
    by_name = {name: vals for name, _, _, vals in entries}
    for name, (id110, id109) in SHIFTED_ENUM.items():
        vals = by_name.get(name)
        if vals is None or vals[0] != id109 or vals[1] != id110:
            problems.append(f"{name}: id109/id110 が想定外: {vals[:2] if vals else None}")
    usc = by_name.get("PLAY_UPDATE_SIGN_CLIENTBOUND")
    if usc is None or usc[0] != 0x46 or any(v != -1 for v in usc[1:]):
        problems.append(f"PLAY_UPDATE_SIGN_CLIENTBOUND が想定外: {usc}")
    us = by_name.get("PLAY_UPDATE_SIGN")
    if us is None or us[0] != 0x19 or us[1] != 0x19:
        problems.append(f"PLAY_UPDATE_SIGN（サーバー行き）が想定外: {us[:2] if us else None}")

    # 3) レジストリ同一の裏付け
    data_paths = json.loads((MCDATA / "dataPaths.json").read_text())["pc"]
    reg_keys = ["blocks", "items", "entities", "effects", "particles",
                "biomes", "language", "attributes", "enchantments",
                "windows", "recipes", "materials", "foods", "instruments"]
    for v in ("1.9.2", "1.9.4"):
        for k in reg_keys:
            if data_paths[v][k] != "pc/1.9":
                problems.append(f"dataPaths {v}.{k} が pc/1.9 でない: "
                                f"{data_paths[v][k]}")
    print("dataPaths: 1.9.2／1.9.4 とも全レジストリが pc/1.9 に解決")

    def lang(jar):
        with zipfile.ZipFile(jar) as z:
            return z.read("assets/minecraft/lang/en_US.lang").decode(
                "utf-8").splitlines()

    l109 = lang(args.prep109 / "server-1.9.2.jar")
    l110 = lang(args.prep110 / "server-1.9.4.jar")
    only110 = sorted(set(l110) - set(l109))
    only109 = sorted(set(l109) - set(l110))
    if only109 or not all(x.startswith("commands.stopsound.")
                          for x in only110) or len(only110) != 5:
        problems.append(f"en_US.lang 差分が想定外: only109={only109} "
                        f"only110={only110}")
    print(f"en_US.lang 差分: 110 側に /stopsound 文言 {len(only110)} 行のみ")

    # 4) ワイヤ実測の記録
    capture = (args.prep109 / "capture-1.9.2.log").read_text(
        encoding="utf-8", errors="replace")
    if "7281059" not in capture:
        problems.append("Keep Alive varint の採取記録（7281059）が無い")
    status = json.loads((args.prep109
                         / "boot-1.9.2/status-response-1.9.2.json")
                        .read_text(encoding="utf-8"))
    if status["version"]["protocol"] != 109:
        problems.append(f"Status の protocol が 109 でない: {status['version']}")

    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("OK: 109 と 110 の差分は update_sign（クライアント行き）の"
          "有無＋後続6件の +1 ずれ＋map_chunk の blockEntities だけ。"
          "PacketIds の 109 列は実測と一致。レジストリは 110 と同一。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
