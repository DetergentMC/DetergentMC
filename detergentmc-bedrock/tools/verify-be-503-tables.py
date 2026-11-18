#!/usr/bin/env python3
"""Bedrock 1.18.33（プロトコル 503）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py /
verify-be-534-tables.py / verify-be-527-tables.py にならい、表・定数・述語の
前提を機械計数で確認する。すべて実ファイル（BedrockVersion.java /
BedrockProtocol.java / BedrockSession.java / protocol-1.18.30.json /
protocol-1.19.1.json / protocol-1.19.10.json / protocol-1.19.20.json /
protocol-1.19.30.json / protocol-1.19.40.json / protocol-1.19.50.json /
protocol-1.19.62.json / protocol-1.19.70.json / protocol-1.19.80.json /
protocol-1.20.0.json / .bin データ）に対するチェックで、推測はない。

- 503 の protocol.json は minecraft-data の `data/bedrock/1.18.30/` にある。
- 503 は 1.18.30 / 1.18.31 / 1.18.32 / 1.18.33 で共有するプロトコル
  （Minecraft Wiki「Bedrock Edition 1.18.30」「Bedrock Edition 1.18.32」
  「Bedrock Edition 1.18.33」の infobox「Protocol version 503」・
  1.18.31 は検索結果の infobox 抜粋で確認。protocolVersions.json でも
  503＝1.18.30 のみ。displayName は共有するうち最新の 1.18.33
  （527 の V1_19_2 と同じ流儀））。
- **503 の protocol.json は 527 のものとバイト同一ではない**
  （10,289 行・267,010 バイト・SHA-1 `7d84fdc2…`、cmp で差分あり）。
  503 と 527 の差分は追加 4（183 lesson_progress・184 request_ability・
  185 request_permissions・186 toast_request）・削除 0・リネーム 0・
  型変更 17 件。
- 503 の 1 つ下は 486（1.18.11）。504〜526 にエントリは無い
  （512・514・516・524・526 は Preview 1.19.0 の番号、残りは Wiki 表に
  エントリ無し）。
- アイテム表は新規生成が必要（bedrock_item_ids_1_18_30.bin）。
  bedrock-1.18.30 タグは存在し、503 自体のリリースのデータである
  （「代用」ではない）。1_19_0 の表とは 64/1312 行、1_19_40 の表とは
  65/1312 行が違うため共用できない（BE-NOTES.md §13）。
- ブロックパレットは 1.19.0／1.19.10／1.19.40 のものとバイト同一ではない
  （8,228 エントリー）。それでもブロック表は新規不要（既存ペアを流用。
  589 と同じ根拠。BE-NOTES.md §13）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 503 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 503 回の実質的な変更：StartGame の property_data・world_template_id
  のバージョン分岐（新述語 hasStartGame527Fields()）。503 用コードが
  527 形を無条件に書くと、503 の実ワイヤは 19 バイト多く壊れる
  （BE-NOTES.md §13）。このスクリプトの §J で分岐の存在を確認する。
  AddPlayer・AddEntity・SetEntityData は 503＝527 で分岐不要。
  PlayerAuthInput の interaction_model は 527 で追加されるが、
  decodePlayerAuthInput は先頭（pitch／yaw／position）だけ読むため
  分岐不要。

使い方: python3 detergentmc-bedrock/tools/verify-be-503-tables.py
終了コード 0 = 全チェック通過。
"""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent  # リポジトリ直下
BE = ROOT / "detergentmc-bedrock"
SRC = BE / "src/main/java/io/detergentmc/bedrock"
RES = BE / "src/main/resources/io/detergentmc/bedrock"
TMP = ROOT / ".tmp-be"  # protocol.json 等の調査用一時ファイル（リポジトリ直下）

CHECKS = []
FAILURES = []


def check(name, ok, detail=""):
    CHECKS.append(name)
    status = "PASS" if ok else "FAIL"
    print(f"[{status}] {name}" + (f" — {detail}" if detail else ""))
    if not ok:
        FAILURES.append(name)


def sha1(path):
    return hashlib.sha1(path.read_bytes()).hexdigest()


def container_fields(types, name):
    """protocol.json の container 型のフィールド名一覧（順序付き）を返す。"""
    t = types[name]
    assert t[0] == "container", f"{name} は container ではない: {t[0]}"
    return [(f.get("name"), f["type"]) for f in t[1]]


def field_names(types, name):
    return [n for n, _ in container_fields(types, name) if n is not None]


def field_type(types, name, field):
    for n, t in container_fields(types, name):
        if n == field:
            return t
    return None


def packet_ids(data):
    mapper = data["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
    return {int(k): v for k, v in mapper.items()}


# ---------- A. protocol.json（503）の同一性 ----------
p103 = TMP / "protocol-1.18.30.json"
check("503 protocol.json が存在する", p103.is_file(), str(p103))
check("503 protocol.json の行数が 10,289 行（wc -l、改行数）",
      p103.is_file()
      and p103.read_text().count("\n") == 10289,
      f"{p103.read_text().count(chr(10))} 行" if p103.is_file() else "なし")
check("503 protocol.json のサイズが 267,010 バイト",
      p103.is_file() and p103.stat().st_size == 267010,
      f"{p103.stat().st_size} バイト" if p103.is_file() else "なし")
check("503 protocol.json の SHA-1 が 7d84fdc2…と一致",
      p103.is_file()
      and sha1(p103) == "7d84fdc210ed3fb93f584894bc06875839fe5050",
      sha1(p103) if p103.is_file() else "なし")

data503 = json.loads(p103.read_text())
types503 = data503["types"]
ids503 = packet_ids(data503)

# ---------- A2. 隣接バージョン（527・534・544・554・557・560）の protocol.json ----------
p101 = TMP / "protocol-1.19.1.json"
check("527 protocol.json（上側の隣接）が存在する", p101.is_file(), str(p101))
check("527 protocol.json の行数が 10,473 行・サイズが 271,781 バイト・SHA-1 が 02b182a1…と一致",
      p101.is_file() and p101.read_text().count("\n") == 10473
      and p101.stat().st_size == 271781
      and sha1(p101) == "02b182a1e3433ec115a1ec854c1ef07b30e6ff4e",
      f"{p101.read_text().count(chr(10))} 行・{p101.stat().st_size} バイト" if p101.is_file() else "なし")
check("503 の protocol.json は 527 のものとバイト同一ではない（SHA-1 が違う）",
      p103.is_file() and p101.is_file()
      and sha1(p103) != sha1(p101))
p110 = TMP / "protocol-1.19.10.json"
check("534 protocol.json が存在する", p110.is_file(), str(p110))
check("534 protocol.json の行数が 10,646 行・サイズが 275,518 バイト・SHA-1 が 6586b076…と一致",
      p110.is_file() and p110.read_text().count("\n") == 10646
      and p110.stat().st_size == 275518
      and sha1(p110) == "6586b07615e11c7a318c98da4170496ab2c95026",
      f"{p110.read_text().count(chr(10))} 行・{p110.stat().st_size} バイト" if p110.is_file() else "なし")
p120 = TMP / "protocol-1.19.20.json"
check("544 protocol.json が存在する", p120.is_file(), str(p120))
check("544 protocol.json の行数が 10,836 行・サイズが 280,171 バイト・SHA-1 が 60acb22c…と一致",
      p120.is_file() and p120.read_text().count("\n") == 10836
      and p120.stat().st_size == 280171
      and sha1(p120) == "60acb22c50099d389704f9a811cf2682d0028241",
      f"{p120.read_text().count(chr(10))} 行・{p120.stat().st_size} バイト" if p120.is_file() else "なし")
p130 = TMP / "protocol-1.19.30.json"
check("554 protocol.json が存在する", p130.is_file(), str(p130))
check("554 protocol.json の行数が 11,048 行・サイズが 285,450 バイト・SHA-1 が 960342ef…と一致",
      p130.is_file() and p130.read_text().count("\n") == 11048
      and p130.stat().st_size == 285450
      and sha1(p130) == "960342ef57d6207a8e7fd581dbb47eff34abd058",
      f"{p130.read_text().count(chr(10))} 行・{p130.stat().st_size} バイト" if p130.is_file() else "なし")
p140 = TMP / "protocol-1.19.40.json"
check("557 protocol.json が存在する", p140.is_file(), str(p140))
check("557 protocol.json の行数が 11,130 行・サイズが 287,657 バイト・SHA-1 が f3f81376…と一致",
      p140.is_file() and p140.read_text().count("\n") == 11130
      and p140.stat().st_size == 287657
      and sha1(p140) == "f3f8137679253a10f2dfafb74d3c4be10d519883",
      f"{p140.read_text().count(chr(10))} 行・{p140.stat().st_size} バイト" if p140.is_file() else "なし")
p150 = TMP / "protocol-1.19.50.json"
check("560 protocol.json が存在する", p150.is_file(), str(p150))
check("560 protocol.json の行数が 11,179 行・サイズが 288,987 バイト・SHA-1 が 5fd84ac8…と一致",
      p150.is_file() and p150.read_text().count("\n") == 11179
      and p150.stat().st_size == 288987
      and sha1(p150) == "5fd84ac85c9ed237e5aa8dc680a0c7cb87c5b9bd",
      f"{p150.read_text().count(chr(10))} 行・{p150.stat().st_size} バイト" if p150.is_file() else "なし")
pver = TMP / "protocolVersions.json"
check("protocolVersions.json が存在する", pver.is_file(), str(pver))
if pver.is_file():
    pv = json.loads(pver.read_text())
    check("protocolVersions.json に 504〜526 のエントリが無い",
          not any(504 <= e["version"] <= 526 for e in pv))
    check("protocolVersions.json の 503 は 1.18.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 503] == ["1.18.30"])
    check("protocolVersions.json の 503 の 1 つ下は 486（1.18.11）",
          max(e["version"] for e in pv if e["version"] < 503) == 486)
    check("protocolVersions.json の 527 は 1.19.1",
          [e["minecraftVersion"] for e in pv if e["version"] == 527] == ["1.19.1"])
    check("protocolVersions.json の 534 は 1.19.10 のみ",
          [e["minecraftVersion"] for e in pv if e["version"] == 534] == ["1.19.10"])
    check("protocolVersions.json の 544 は 1.19.20 のみ",
          [e["minecraftVersion"] for e in pv if e["version"] == 544] == ["1.19.20"])

data527 = json.loads(p101.read_text()) if p101.is_file() else None
data534 = json.loads(p110.read_text()) if p110.is_file() else None
data544 = json.loads(p120.read_text()) if p120.is_file() else None
data554 = json.loads(p130.read_text()) if p130.is_file() else None
data557 = json.loads(p140.read_text()) if p140.is_file() else None
data560 = json.loads(p150.read_text()) if p150.is_file() else None
ids527 = packet_ids(data527) if data527 else {}
ids534 = packet_ids(data534) if data534 else {}
ids544 = packet_ids(data544) if data544 else {}
ids554 = packet_ids(data554) if data554 else {}
ids557 = packet_ids(data557) if data557 else {}
ids560 = packet_ids(data560) if data560 else {}

# ---------- B. パケット一覧（503）の件数・境界 ----------
check("503 のパケット数が 179 件", len(ids503) == 179, f"{len(ids503)} 件")
check("503 の最大パケット ID が 182", max(ids503) == 182, f"最大 {max(ids503)}")
check("503 の型定義数が 275", len(types503) == 275, f"{len(types503)}")
check("527 のパケット数が 183 件", len(ids527) == 183, f"{len(ids527)} 件")
check("527 の最大パケット ID が 186", max(ids527) == 186, f"最大 {max(ids527)}")
check("527 の型定義数が 281", len(data527["types"]) == 281 if data527 else False)
check("503 の ID の欠番が 0・16・37・116（527 と同じ）",
      [i for i in range(max(ids503) + 1) if i not in ids503] == [0, 16, 37, 116])
check("503 のパケット ID 0〜182 が 527 の同 ID と名称がすべて一致",
      all(ids503[i] == ids527[i] for i in ids503),
      f"{sum(1 for i in ids503 if ids503[i] == ids527.get(i))} / {len(ids503)} 件一致")

d503 = TMP / "packet-diff-503-vs-527.json"
d527 = TMP / "packet-diff-527-vs-534.json"
check("503 vs 527 の差分 JSON が存在する", d503.is_file(), str(d503))
check("527 vs 534 の差分 JSON が存在する", d527.is_file(), str(d527))
if d503.is_file() and d527.is_file():
    j503 = json.loads(d503.read_text())
    j527 = json.loads(d527.read_text())
    check("503 vs 527：追加 4 件が 183 lesson_progress・184 request_ability・185 request_permissions・186 toast_request",
          j503["added"] == {"183": "lesson_progress", "184": "request_ability",
                            "185": "request_permissions", "186": "toast_request"},
          json.dumps(j503["added"]))
    check("503 vs 527：削除 0・リネーム 0",
          j503["removed"] == {} and j503["renamed"] == {})
    check("503 vs 527：型変更 17 件",
          len(j503["changed_types"]) == 17, str(j503["changed_types"]))
    check("503 vs 527：型変更の内訳が Action・DeviceOS・PermissionLevel・RequestPermissions・SoundType・mcpe_packet・packet_adventure_settings・packet_available_commands・packet_event・packet_lesson_progress・packet_level_event・packet_player_action・packet_player_auth_input・packet_request_ability・packet_request_permissions・packet_start_game・packet_toast_request",
          j503["changed_types"] == ["Action", "DeviceOS",
                                     "PermissionLevel (only in B)",
                                     "RequestPermissions (only in B)",
                                     "SoundType", "mcpe_packet",
                                     "packet_adventure_settings",
                                     "packet_available_commands",
                                     "packet_event",
                                     "packet_lesson_progress (only in B)",
                                     "packet_level_event",
                                     "packet_player_action",
                                     "packet_player_auth_input",
                                     "packet_request_ability (only in B)",
                                     "packet_request_permissions (only in B)",
                                     "packet_start_game",
                                     "packet_toast_request (only in B)"],
          json.dumps(j503["changed_types"]))
    check("527 vs 534：追加 4 件が 187 update_abilities・188 update_adventure_settings・189 death_info・190 editor_network",
          j527["added"] == {"187": "update_abilities", "188": "update_adventure_settings",
                            "189": "death_info", "190": "editor_network"},
          json.dumps(j527["added"]))
    check("527 vs 534：削除 0・リネーム 0",
          j527["removed"] == {} and j527["renamed"] == {})
    check("527 vs 534：型変更 17 件",
          len(j527["changed_types"]) == 17, str(j527["changed_types"]))

# ---------- C. 核パケット ID：BedrockProtocol.java と protocol.json の一致 ----------
proto_src = (SRC / "BedrockProtocol.java").read_text()
java_packets = dict(re.findall(
    r"PACKET_(\w+)\s*=\s*0x([0-9a-fA-F]+);", proto_src))
# (Java 定数名, protocol.json のパケット名)
CORE = [
    ("LOGIN", "login"), ("PLAY_STATUS", "play_status"),
    ("DISCONNECT", "disconnect"),
    ("RESOURCE_PACKS_INFO", "resource_packs_info"),
    ("RESOURCE_PACK_STACK", "resource_pack_stack"),
    ("RESOURCE_PACK_CLIENT_RESPONSE", "resource_pack_client_response"),
    ("TEXT", "text"), ("SET_TIME", "set_time"),
    ("START_GAME", "start_game"), ("ADD_PLAYER", "add_player"),
    ("ADD_ENTITY", "add_entity"), ("REMOVE_ENTITY", "remove_entity"),
    ("ADD_ITEM_ENTITY", "add_item_entity"),
    ("MOVE_ENTITY", "move_entity"), ("MOVE_PLAYER", "move_player"),
    ("UPDATE_BLOCK", "update_block"), ("ADD_PAINTING", "add_painting"),
    ("ENTITY_EVENT", "entity_event"), ("MOB_EFFECT", "mob_effect"),
    ("UPDATE_ATTRIBUTES", "update_attributes"),
    ("INVENTORY_TRANSACTION", "inventory_transaction"),
    ("MOB_EQUIPMENT", "mob_equipment"),
    ("MOB_ARMOR_EQUIPMENT", "mob_armor_equipment"),
    ("INTERACT", "interact"), ("PLAYER_ACTION", "player_action"),
    ("SET_ENTITY_DATA", "set_entity_data"),
    ("SET_ENTITY_MOTION", "set_entity_motion"),
    ("SET_ENTITY_LINK", "set_entity_link"),
    ("PLAYER_LIST", "player_list"),
    ("SPAWN_EXPERIENCE_ORB", "spawn_experience_orb"),
    ("MOVE_ENTITY_DELTA", "move_entity_delta"),
    ("LEVEL_CHUNK", "level_chunk"),
    ("REQUEST_CHUNK_RADIUS", "request_chunk_radius"),
    ("CHUNK_RADIUS_UPDATED", "chunk_radius_update"),
    ("COMMAND_REQUEST", "command_request"),
    ("NETWORK_CHUNK_PUBLISHER_UPDATE", "network_chunk_publisher_update"),
    ("CLIENT_CACHE_STATUS", "client_cache_status"),
    ("PLAYER_AUTH_INPUT", "player_auth_input"),
    ("UPDATE_SUB_CHUNK_BLOCKS", "update_subchunk_blocks"),
    ("SUB_CHUNK", "subchunk"), ("SUB_CHUNK_REQUEST", "subchunk_request"),
]
json_by_name = {v: k for k, v in ids503.items()}
for const, pname in CORE:
    java_id = int(java_packets[const], 16)
    json_id = json_by_name.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が protocol.json と一致",
          json_id == java_id, f"json={json_id}")

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_18_33 が存在する", "V1_18_33" in entry_by_name)
check("V1_18_33 のプロトコルが 503",
      entry_by_name.get("V1_18_33", (None,))[0] == 503,
      str(entry_by_name.get("V1_18_33")))
check("V1_18_33 の表示名が 1.18.33",
      entry_by_name.get("V1_18_33", (None, None))[1] == "1.18.33")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_18_33 が V1_19_2 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_18_33(503") < ver_src.index("V1_19_2(527"))
check("enum の javadoc に 1.18.30 の protocol.json が含まれる",
      "1.18.30" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 503 -> \"(1_18_30)\";", ver_src)
check("itemTableTag：503 が 1_18_30 の表を使う（新規生成）", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_18_30.bin"
check("アイテム表 bedrock_item_ids_1_18_30.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が 17cbfb06…と一致（1.18.30 候補表）",
      tag_bin.is_file()
      and sha1(tag_bin) == "17cbfb062c472e26ad2b35c4eab51729faafa225",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
check("1.18.31／1.18.32／1.18.33 名のアイテム表は無い（タグは出所の 1_18_30 が正しい）",
      not (RES / "bedrock_item_ids_1_18_31.bin").exists()
      and not (RES / "bedrock_item_ids_1_18_32.bin").exists()
      and not (RES / "bedrock_item_ids_1_18_33.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規 1 本）", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s503 = summary.get("503", {})
check("item_tables_summary.json に 503 のエントリがある",
      "503" in summary)
check("item_tables_summary.json の 503 が tag=bedrock-1.18.30・exact=777／shared=236／legacy=33／fallback=266",
      s503.get("tag") == "bedrock-1.18.30" and s503.get("exact") == 777
      and s503.get("shared") == 236 and s503.get("legacy") == 33
      and s503.get("fallback") == 266,
      str({k: s503.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))
check("item_tables_summary.json の 503 の missing_names が 266 件",
      len(s503.get("missing_names", [])) == 266,
      f"{len(s503.get('missing_names', []))} 件")

# ---------- F. 述語の前提：protocol.json 側の事実 ----------
types527 = data527["types"]
types534 = data534["types"]
types544 = data544["types"]
types554 = data554["types"]
types557 = data557["types"]
types560 = data560["types"]

# (1) StartGame：62 フィールド・述語の前提はすべて「無し」
sg503 = field_names(types503, "packet_start_game")
sg527 = field_names(types527, "packet_start_game")
check("StartGame：503 は 62 フィールド", len(sg503) == 62, f"{len(sg503)} フィールド")
check("StartGame：527 は 64 フィールド", len(sg527) == 64, f"{len(sg527)} フィールド")
check("StartGame：527 だけにある 3 フィールドが block_pallette_checksum・property_data・world_template_id",
      set(sg527) - set(sg503) == {"block_pallette_checksum", "property_data", "world_template_id"})
check("StartGame：503 だけにある 1 フィールドが block_pallete_checksum（single-l）",
      set(sg503) - set(sg527) == {"block_pallete_checksum"})
check("StartGame：503 は block_pallete_checksum を持つ（single-l、527 の double-l 綴りは無い）",
      "block_pallete_checksum" in sg503
      and "block_pallette_checksum" not in sg503)
check("StartGame：527 は block_pallette_checksum を持つ（double-l、503 の single-l 綴りは無い）",
      "block_pallette_checksum" in sg527
      and "block_pallete_checksum" not in sg527)
check("StartGame：503 に property_data が無い（hasStartGame527Fields()=false の前提）",
      "property_data" not in sg503)
check("StartGame：503 に world_template_id が無い（hasStartGame527Fields()=false の前提）",
      "world_template_id" not in sg503)
check("StartGame：503 に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg503)
check("StartGame：503 に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg503 for t in ["server_identifier", "world_identifier",
                                   "scenario_identifier", "owner_identifier"]))
check("StartGame：503 に editor_world が無い（hasEditorWorld=false）",
      "editor_world" not in sg503)
check("StartGame：503 の permission_level は zigzag32（hasPermissionLevelMapper=false）",
      field_type(types503, "packet_start_game", "permission_level") == "zigzag32")
check("StartGame：503 の movement_authority が存在する（hasMovementAuthority=true）",
      "movement_authority" in sg503)
check("StartGame：503 の itemstates が存在する（hasItemstates=true）",
      "itemstates" in sg503)
check("StartGame：503 に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg503)
check("StartGame：503 に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg503 and "server_join_info" not in sg503)
check("StartGame：503 に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg503)
check("StartGame：503 に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg503)
check("StartGame：503 に created_in_editor・exported_from_editor・block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(f in sg503 for f in ["created_in_editor", "exported_from_editor",
                                   "block_network_ids_are_hashes"]))
check("StartGame：503 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg503)
check("StartGame：503 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg503)
check("StartGame：503 に persona_disabled・custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg503 and "custom_skins_disabled" not in sg503)
check("StartGame：503 に chat_restriction_level・disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg503
      and "disable_player_interactions" not in sg503)
check("StartGame：503 に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg503)

# (2) Resource packs info / stack：527 と同じ形
rpi = field_names(types503, "packet_resource_packs_info")
rpi527 = field_names(types527, "packet_resource_packs_info")
check("Resource packs info：503 は 527 と同じ 5 フィールド（must_accept・has_scripts・force_server_packs・behaviour_packs・texture_packs）",
      rpi == rpi527 == ["must_accept", "has_scripts", "force_server_packs",
                        "behaviour_packs", "texture_packs"])
check("Resource packs info：503 に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("Resource packs info：503 に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("Resource packs info：503 に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("Resource packs info：503 に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
check("TexturePackInfos の countType が li16（527 と同じ）",
      types503["TexturePackInfos"][1].get("countType") == "li16")
rps = field_names(types503, "packet_resource_pack_stack")
rps527 = field_names(types527, "packet_resource_pack_stack")
check("Resource pack stack：503 は 527 と同じ 6 フィールド（behavior_packs を含む）",
      rps == rps527 == ["must_accept", "behavior_packs", "resource_packs",
                        "game_version", "experiments",
                        "experiments_previously_used"])
check("Resource pack stack：503 に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

# (3) エンティティ・アイテム
check("Mob equipment の item が Item（ItemV4 ではない）",
      field_type(types503, "packet_mob_equipment", "item") == "Item")
check("Add item entity の item が Item",
      field_type(types503, "packet_add_item_entity", "item") == "Item")
mae = [n for n, _ in container_fields(types503, "packet_mob_armor_equipment")]
check("Mob armor equipment が helmet・chestplate・leggings・boots の 4 つ（body は無い）",
      mae == ["runtime_entity_id", "helmet", "chestplate", "leggings", "boots"])
check("ItemV4 型が 503 の protocol.json に存在しない", "ItemV4" not in types503)
check("Add player は 527 と同じ（old form、body_yaw が無い）",
      field_names(types503, "packet_add_player") == field_names(types527, "packet_add_player"))
check("Add entity は 527 と同じ（old form、body_yaw が無い）",
      field_names(types503, "packet_add_entity") == field_names(types527, "packet_add_entity"))

# (4) トランザクション・アイテム関連
leg = {n: t for n, t in container_fields(types503, "TransactionLegacy")}
check("TransactionLegacy の legacy_transactions が switch（527 と同じ）",
      isinstance(leg.get("legacy_transactions"), list)
      and leg["legacy_transactions"][0] == "switch")
tx = {n: t for n, t in container_fields(types503, "Transaction")}
tx_map = tx.get("transaction_type")[1]["mappings"]
check("Transaction の transaction_type が 0=normal・1=inventory_mismatch・2=item_use（527 と同じ）",
      tx_map.get("0") == "normal"
      and tx_map.get("1") == "inventory_mismatch"
      and tx_map.get("2") == "item_use")
check("Transaction の actions が TransactionActions（527 と同じ）",
      tx.get("actions") == "TransactionActions")
ta = types503["TransactionActions"]
ta_fields = {f["name"]: f["type"] for f in ta[1]["type"][1] if "name" in f}
check("TransactionActions の old/new item が旧型 Item（transactionUsesItemV4=false）",
      ta_fields.get("old_item") == "Item" and ta_fields.get("new_item") == "Item",
      f"old={ta_fields.get('old_item')} new={ta_fields.get('new_item')}")
check("TransactionActions に window_id／flags の option 欄が無い",
      "window_id" not in ta_fields and "flags" not in ta_fields)
check("ItemStackRequest に cause が無い（527 と同じ）",
      "cause" not in field_names(types503, "ItemStackRequest"))
check("RecipeIngredient：503 が旧形式（network_id zigzag32＋匿名 switch）",
      types503["RecipeIngredient"][0] == "container"
      and types503["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types503["RecipeIngredient"][1][1].get("anon") is True,
      json.dumps(types503["RecipeIngredient"][1][0]))
check("RecipeIngredient：527・534 も旧形式（network_id zigzag32＋匿名 switch）",
      types527["RecipeIngredient"][0] == "container"
      and types527["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types527["RecipeIngredient"][1][1].get("anon") is True
      and types534["RecipeIngredient"][0] == "container"
      and types534["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types534["RecipeIngredient"][1][1].get("anon") is True)
check("RecipeIngredient：503 の匿名 switch が network_id=0 で void、default で network_data＋count（zigzag32）",
      types503["RecipeIngredient"][1][1]["type"][1].get("compareTo") == "network_id"
      and types503["RecipeIngredient"][1][1]["type"][1]["fields"].get("0") == "void"
      and [f["name"] for f in types503["RecipeIngredient"][1][1]["type"][1]["default"][1]] == ["network_data", "count"])
check("RecipeIngredient：554 が新形式（先頭が type の u8 マッパー：invalid／int_id_meta／molang／item_tag／string_id_meta）",
      types554["RecipeIngredient"][0] == "container"
      and types554["RecipeIngredient"][1][0].get("name") == "type"
      and types554["RecipeIngredient"][1][0]["type"][1].get("mappings") == {"0": "invalid", "1": "int_id_meta",
                                                                            "2": "molang", "3": "item_tag",
                                                                            "4": "string_id_meta"})

# (5) 核パケットの 503 vs 527：14 件 JSON 同一・2 件が違う
MAJOR = ["packet_add_player", "packet_add_entity", "packet_add_item_entity",
         "packet_level_chunk", "packet_move_entity", "packet_move_player",
         "packet_update_block", "packet_set_entity_data",
         "packet_mob_equipment", "packet_mob_armor_equipment",
         "packet_start_game", "packet_player_list",
         "packet_inventory_transaction", "packet_network_chunk_publisher_update",
         "packet_resource_packs_info", "packet_resource_pack_stack"]
for name in MAJOR:
    if name in ("packet_start_game",):
        continue
    check(f"核パケット {name} が 503 と 527 で JSON 同一（分岐不要）",
          types503[name] == types527[name])
check("核パケット packet_player_auth_input が 503 と 527 で違う（interaction_model が 527 で追加）",
      types503["packet_player_auth_input"] != types527["packet_player_auth_input"])
check("核パケット packet_start_game が 503 と 527 で違う（property_data・world_template_id が 527 で追加）",
      types503["packet_start_game"] != types527["packet_start_game"])

# ---------- G. 境界：503 と 527・534・544・554・557・560 の分岐点 ----------
check("503 に 183〜186（lesson_progress・request_ability・request_permissions・toast_request）が無い（527 で追加）",
      all(i not in ids503 for i in [183, 184, 185, 186]))
check("527 に 183〜186 がある",
      all(i in ids527 for i in [183, 184, 185, 186]))
check("パケット数：527 は 503 より 4 件多い", len(ids527) - len(ids503) == 4)
check("型定義数：527 は 503 より 6 件多い",
      len(types527) - len(types503) == 6)
check("SoundType：503 は 424 件（0〜423）・527 は 442 件・534 は 443 件",
      len(types503["SoundType"][1]["mappings"]) == 424
      and len(types527["SoundType"][1]["mappings"]) == 442
      and len(types534["SoundType"][1]["mappings"]) == 443)
check("SoundType：503 の 423 が Undefined（527 は _）",
      types503["SoundType"][1]["mappings"]["423"] == "Undefined"
      and types527["SoundType"][1]["mappings"]["423"] == "_")
check("SoundType：503 に 441・442 が無い（527 は 441 が Undefined、534 は 442 が Undefined）",
      "441" not in types503["SoundType"][1]["mappings"]
      and "442" not in types503["SoundType"][1]["mappings"]
      and types527["SoundType"][1]["mappings"]["441"] == "Undefined"
      and types534["SoundType"][1]["mappings"]["442"] == "Undefined")
check("SoundType：527 の 432 が _（534 は DrinkMilk）",
      types527["SoundType"][1]["mappings"]["432"] == "_"
      and types534["SoundType"][1]["mappings"]["432"] == "DrinkMilk")
check("Action：503 は 28 件・527 は 30 件（28・29 start／stop_item_use_on が 527 で追加）",
      len(types503["Action"][1]["mappings"]) == 28
      and len(types527["Action"][1]["mappings"]) == 30
      and "28" not in types503["Action"][1]["mappings"]
      and types527["Action"][1]["mappings"]["28"] == "start_item_use_on")
check("DeviceOS：503 は 15 件・527 は 16 件（15 Linux が 527 で追加）",
      len(types503["DeviceOS"][1]["mappings"]) == 15
      and len(types527["DeviceOS"][1]["mappings"]) == 16
      and types527["DeviceOS"][1]["mappings"]["15"] == "Linux")
check("PermissionLevel・RequestPermissions 型が 503 に無く 527 にある",
      "PermissionLevel" not in types503 and "PermissionLevel" in types527
      and "RequestPermissions" not in types503 and "RequestPermissions" in types527)
ev503 = field_type(types503, "packet_event", "event_type")[1]["mappings"]
ev527 = field_type(types527, "packet_event", "event_type")[1]["mappings"]
check("packet_event：503 は 26 件・527 は 30 件（26〜29 が 527 で追加）",
      len(ev503) == 26 and len(ev527) == 30 and "26" not in ev503)
check("packet_level_event：503 は 174 件・527 は 175 件（2039 sonic_explosion が 527 で追加）",
      len(field_type(types503, "packet_level_event", "event")[1]["mappings"]) == 174
      and len(field_type(types527, "packet_level_event", "event")[1]["mappings"]) == 175
      and "2039" not in field_type(types503, "packet_level_event", "event")[1]["mappings"]
      and field_type(types527, "packet_level_event", "event")[1]["mappings"]["2039"] == "sonic_explosion")
check("packet_player_action：503 は 4 フィールド（result_position が無い）・527 は 5 フィールド",
      field_names(types503, "packet_player_action") == ["runtime_entity_id", "action", "position", "face"]
      and field_names(types527, "packet_player_action") == ["runtime_entity_id", "action", "position", "result_position", "face"])
check("packet_player_auth_input：503 は 14 フィールド・527 は 15 フィールド（interaction_model が 527 で追加）",
      len(field_names(types503, "packet_player_auth_input")) == 14
      and len(field_names(types527, "packet_player_auth_input")) == 15
      and "interaction_model" not in field_names(types503, "packet_player_auth_input")
      and field_names(types527, "packet_player_auth_input")[8] == "interaction_model")
check("packet_player_auth_input の interaction_model は play_mode の直後・gaze_direction の直前",
      field_names(types527, "packet_player_auth_input")[7] == "play_mode"
      and field_names(types527, "packet_player_auth_input")[9] == "gaze_direction")
check("packet_adventure_settings の command_permission が 503 と 527 で同じ（inline varint mapper、0=normal・1=operator・2=host・3=automation・4=admin）",
      field_type(types503, "packet_adventure_settings", "command_permission")
      == field_type(types527, "packet_adventure_settings", "command_permission")
      == ["mapper", {"type": "varint", "mappings": {"0": "normal", "1": "operator",
                                                     "2": "host", "3": "automation",
                                                     "4": "admin"}}])
check("packet_adventure_settings の command_permission が 534 で CommandPermissionLevelVarint になる（分岐点は 534）",
      field_type(types534, "packet_adventure_settings", "command_permission")
      == "CommandPermissionLevelVarint")
check("packet_play_status：503 と 527 は同じ 8 件（failed_editor_* は 534 で追加）",
      field_type(types503, "packet_play_status", "status")[1]["mappings"]
      == field_type(types527, "packet_play_status", "status")[1]["mappings"]
      and len(field_type(types503, "packet_play_status", "status")[1]["mappings"]) == 8)
check("packet_play_status：534 は 10 件（8・9 failed_editor_vanilla_mismatch・failed_vanilla_editor_mismatch）",
      len(field_type(types534, "packet_play_status", "status")[1]["mappings"]) == 10
      and field_type(types534, "packet_play_status", "status")[1]["mappings"]["8"] == "failed_editor_vanilla_mismatch")
check("packet_entity_event：503・527 に 78 drink_milk が無く 534 にある",
      "78" not in field_type(types503, "packet_entity_event", "event_id")[1]["mappings"]
      and "78" not in field_type(types527, "packet_entity_event", "event_id")[1]["mappings"]
      and field_type(types534, "packet_entity_event", "event_id")[1]["mappings"]["78"] == "drink_milk")
check("AddPlayer：503 は 22 フィールド・527 と同一（old form）",
      len(field_names(types503, "packet_add_player")) == 22
      and field_names(types503, "packet_add_player") == field_names(types527, "packet_add_player"))
check("AddEntity：503 は 11 フィールド・527 と同一（old form）",
      len(field_names(types503, "packet_add_entity")) == 11
      and field_names(types503, "packet_add_entity") == field_names(types527, "packet_add_entity"))
check("EntityProperties 分岐：503・527 の protocol.json に EntityProperties 型が無く 557 にある",
      "EntityProperties" not in types503
      and "EntityProperties" not in types527
      and "EntityProperties" in types557)
check("EntityProperties 分岐：534 の protocol.json にも EntityProperties 型が無い",
      "EntityProperties" not in types534)
check("EntityProperties 分岐：AddPlayer・AddEntity・SetEntityData が 503＝527 で分岐不要",
      field_names(types503, "packet_add_player") == field_names(types527, "packet_add_player")
      and field_names(types503, "packet_add_entity") == field_names(types527, "packet_add_entity")
      and field_names(types503, "packet_set_entity_data") == field_names(types527, "packet_set_entity_data"))
check("ItemStackRequest：503・527・534 に cause が無い（557 で追加）",
      "cause" not in field_names(types503, "ItemStackRequest")
      and "cause" not in field_names(types527, "ItemStackRequest")
      and "cause" not in field_names(types534, "ItemStackRequest"))
check("RecipeIngredient：503・527・534 が旧形式・554 が新形式（分岐点は 554）",
      types503["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types527["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types534["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types554["RecipeIngredient"][1][0].get("name") == "type")
check("packet_text：503・527・534 に type 11 が無い（582 で追加）",
      "11" not in field_type(types503, "packet_text", "type")[1]["mappings"]
      and "11" not in field_type(types527, "packet_text", "type")[1]["mappings"]
      and "11" not in field_type(types534, "packet_text", "type")[1]["mappings"])
check("network_settings：503・527・534 の compression_threshold が u16（554 で lu16）",
      field_type(types503, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types527, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types534, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types554, "packet_network_settings", "compression_threshold") == "lu16")
check("structure_block_update：503・527・534 は 9 フィールド（water_logged は 554 で追加）",
      len(field_names(types503, "packet_structure_block_update")) == 9
      and len(field_names(types527, "packet_structure_block_update")) == 9
      and len(field_names(types534, "packet_structure_block_update")) == 9
      and "water_logged" not in field_names(types503, "packet_structure_block_update"))
# 544 の StartGame 追加 5 フィールドが 503・527・534 に無い
for f in ["persona_disabled", "custom_skins_disabled", "chat_restriction_level",
          "disable_player_interactions", "client_side_generation"]:
    check(f"StartGame：503・527・534 に {f} が無い（544 で追加されるもの）",
          f not in sg503 and f not in sg527
          and f not in field_names(types534, "packet_start_game")
          and f in field_names(types544, "packet_start_game"))

# 567 の StartGame 追加（emote_chat_muted）が 503・527・534・544 に無い
check("StartGame：503・527・534・544 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg503 and "emote_chat_muted" not in sg527
      and "emote_chat_muted" not in field_names(types534, "packet_start_game")
      and "emote_chat_muted" not in field_names(types544, "packet_start_game"))
# 582 の StartGame 追加が 503・527・534・544・554・557・560 に無い
for f in ["created_in_editor", "exported_from_editor", "block_network_ids_are_hashes"]:
    check(f"StartGame：503・527・534・544・554・557・560 に {f} が無い（582 で追加されるもの）",
          f not in sg503 and f not in sg527
          and f not in field_names(types534, "packet_start_game")
          and f not in field_names(types544, "packet_start_game")
          and f not in field_names(types554, "packet_start_game")
          and f not in field_names(types557, "packet_start_game")
          and f not in field_names(types560, "packet_start_game"))

# 589 の StartGame 追加（server_controlled_sound）が 503・527・534・544・554・557・560 に無い
check("StartGame：503・527・534・544・554・557・560 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg503
      and "server_controlled_sound" not in sg527
      and "server_controlled_sound" not in field_names(types534, "packet_start_game")
      and "server_controlled_sound" not in field_names(types544, "packet_start_game")
      and "server_controlled_sound" not in field_names(types554, "packet_start_game")
      and "server_controlled_sound" not in field_names(types557, "packet_start_game")
      and "server_controlled_sound" not in field_names(types560, "packet_start_game"))
check("582 の StartGame 削除要素（movement_authority・itemstates）が 503・527・534・544・554・557・560 にある",
      all(f in field_names(t, "packet_start_game")
          for t in [types503, types527, types534, types544, types554, types557, types560]
          for f in ["movement_authority", "itemstates"]))
check("PlayerAttributes：503・527・534 は 5 項目（min・max・current・default・name、modifiers は無い）",
      [f["name"] for f in types503["PlayerAttributes"][1]["type"][1] if "name" in f]
      == ["min", "max", "current", "default", "name"]
      and [f["name"] for f in types527["PlayerAttributes"][1]["type"][1] if "name" in f]
      == ["min", "max", "current", "default", "name"])
check("clientbound_map_item_data：503・527・534 に origin が無い（560 で追加）",
      "origin" not in field_names(types503, "packet_clientbound_map_item_data")
      and "origin" not in field_names(types527, "packet_clientbound_map_item_data")
      and "origin" not in field_names(types534, "packet_clientbound_map_item_data"))
check("map_info_request：503・527・534 は map_id のみ",
      field_names(types503, "packet_map_info_request") == ["map_id"]
      and field_names(types527, "packet_map_info_request") == ["map_id"]
      and field_names(types534, "packet_map_info_request") == ["map_id"])
check("modal_form_response：503・527・534 は form_id＋data の 2 フィールド",
      field_names(types503, "packet_modal_form_response") == ["form_id", "data"]
      and field_names(types527, "packet_modal_form_response") == ["form_id", "data"]
      and field_names(types534, "packet_modal_form_response") == ["form_id", "data"])
check("network_chunk_publisher_update：503・527・534 は coordinates＋radius の 2 フィールド",
      field_names(types503, "packet_network_chunk_publisher_update") == ["coordinates", "radius"]
      and field_names(types527, "packet_network_chunk_publisher_update") == ["coordinates", "radius"]
      and field_names(types534, "packet_network_chunk_publisher_update") == ["coordinates", "radius"])

# ---------- H. ブロックデータ（1.18.30 の実測・新規不要の根拠） ----------
d503 = TMP / "be-503"
canon503 = d503 / "canon-bedrock-1.18.30.nbt"
req503 = d503 / "req-bedrock-1.18.30-required_item_list.json"
biome503 = d503 / "biomeid-bedrock-1.18.30.json"
blockid503 = d503 / "blockid-bedrock-1.18.30.json"
check("1.18.30 の canonical_block_states NBT が存在する", canon503.is_file())
check("1.18.30 の canonical_block_states NBT のサイズが 1,218,436 バイト",
      canon503.is_file() and canon503.stat().st_size == 1218436,
      f"{canon503.stat().st_size} バイト" if canon503.is_file() else "なし")
check("1.18.30 の canonical_block_states NBT の SHA-1 が 046c9eb6…と一致",
      canon503.is_file()
      and sha1(canon503) == "046c9eb68f9ce9783d2a4fa2993e21f73529464b",
      sha1(canon503) if canon503.is_file() else "なし")
check("1.18.30 の required_item_list.json が存在する", req503.is_file())
check("1.18.30 の required_item_list.json のサイズが 111,599 バイト",
      req503.is_file() and req503.stat().st_size == 111599,
      f"{req503.stat().st_size} バイト" if req503.is_file() else "なし")
check("1.18.30 の required_item_list.json の SHA-1 が 41b79951…と一致",
      req503.is_file()
      and sha1(req503) == "41b79951ba7789506a8a91d4db3faeae99fe3f4f",
      sha1(req503) if req503.is_file() else "なし")
if req503.is_file():
    req_list = json.loads(req503.read_text())
    check("1.18.30 の required_item_list.json のエントリ数が 1,106",
          len(req_list) == 1106, f"{len(req_list)} 件")
check("1.18.30 の biome_id_map.json が存在する", biome503.is_file())
check("1.18.30 の biome_id_map.json の SHA-1 が 17fe0c96…と一致",
      biome503.is_file()
      and sha1(biome503) == "17fe0c969b93ea7e825ef0ec7391eb9240199784",
      sha1(biome503) if biome503.is_file() else "なし")
if biome503.is_file():
    biome_map = json.loads(biome503.read_text())
    check("1.18.30 の biome_id_map が 83 エントリー（1.19.0 の 85 より 2 少ない：deep_dark・mangrove_swamp が無い）",
          len(biome_map) == 83, f"{len(biome_map)} 件")
check("1.18.30 の block_id_map.json が存在する", blockid503.is_file())
check("1.18.30 の block_id_map.json の SHA-1 が f3855a10…と一致（1.19.0 のものとバイト同一）",
      blockid503.is_file()
      and sha1(blockid503) == "f3855a102c698f960020d928bcaef10d25724062",
      sha1(blockid503) if blockid503.is_file() else "なし")
if blockid503.is_file():
    blockid_map = json.loads(blockid503.read_text())
    check("1.18.30 の block_id_map が 553 エントリー",
          len(blockid_map) == 553, f"{len(blockid_map)} 件")
check("1.18.30 名のブロック表は無い（新規不要、1.20.4／1.20.50 を共用）",
      not list(RES.glob("bedrock_block_runtime_ids_1_18_30.bin"))
      and not list(RES.glob("canonical_block_states_1_18_30.nbt")))

# ---------- I. 503 回で変えていないもの（既存の境界の維持） ----------
bedrock_java = SRC / "BedrockProtocol.java"
bedrock_java_src = bedrock_java.read_text()
check("BedrockProtocol.java の TARGET_VERSION が 1.20.50 のまま",
      '"1.20.50"' in bedrock_java_src)
check("BedrockProtocol.java の javadoc の範囲が 1.7.1〜26.52",
      "1.7.1〜26.52" in bedrock_java_src)
check("BedrockProtocol.java の javadoc の範囲が 291〜2193",
      "291〜2193" in bedrock_java_src)
check("BedrockProtocol.java の javadoc の対応バージョン数が 62 バージョン分",
      "62 バージョン分" in bedrock_java_src)
session_src = (SRC / "BedrockSession.java").read_text()
check("BedrockSession.java の切断文言の対応範囲が 291-2193",
      "291-2193" in session_src)
server_src = (SRC / "BedrockServer.java").read_text()
check("BedrockServer.java の起動ログの範囲が 1.7.1〜26.52 / 291〜2193",
      "1.7.1〜26.52" in server_src and "291〜2193" in server_src)
check("BedrockProtocol.java は NetherNet 用の分岐（isNetherNetProtocol、2225 で無効化）を持っている",
      "isNetherNetProtocol" in bedrock_java_src and "2225" in ver_src
      and "NETHERNET_PROTOCOL = 2225" in ver_src)
check("BedrockChunkTranslator.java は BedrockVersion を参照していない（ブロック表の分岐は無い）",
      "BedrockVersion" not in (SRC / "BedrockChunkTranslator.java").read_text())
check("BedrockEntityTranslator.java は BedrockVersion を 2 箇所だけ参照する（431 回の goat_horn_count 分岐・toBedrockData の 3 引数版と既定 V1_20_50）",
      (SRC / "BedrockEntityTranslator.java").read_text().count("BedrockVersion") == 2
      and "hasGoatHornCountKey()" in (SRC / "BedrockEntityTranslator.java").read_text())
check("JavaBackendConnection.java は BedrockVersion を参照していない",
      "BedrockVersion" not in (SRC / "JavaBackendConnection.java").read_text())

# ---------- J. 503 回の実質的な変更：hasStartGame527Fields() の分岐 ----------
packets_src = (SRC / "BedrockPackets.java").read_text()
check("BedrockPackets.java が hasStartGame527Fields() を使っている",
      "hasStartGame527Fields()" in packets_src)
check("BedrockPackets.java の property_data の書き込みが hasStartGame527Fields() で分岐している（コメント「527 以降のみ（503 には無い。hasStartGame527Fields() を参照）」＋0x0A の書き込み）",
      re.search(r"property data.*?527 以降のみ（503 には無い。hasStartGame527Fields\(\) を参照）"
                r".*?if \(version\.hasStartGame527Fields\(\)\) \{\s*out\.writeByte\(0x0A\)",
                packets_src, re.DOTALL) is not None)
check("BedrockPackets.java の world_template_id の書き込みが hasStartGame527Fields() で分岐している（16 バイトの書き込み＋コメント「world template id（空 UUID。503 には無い）」）",
      re.search(r"if \(version\.hasStartGame527Fields\(\)\) \{\s*out\.writeBytes\(new byte\[16\]\); // world template id",
                packets_src) is not None)
check("BedrockVersion.java に hasStartGame527Fields() の定義がある（protocol >= 527）",
      re.search(r"public boolean hasStartGame527Fields\(\)\s*\{[^}]*protocol\s*>=\s*527",
                ver_src) is not None)
check("BedrockPackets.java に encodeStartGame のバージョン分岐オーバーロードがある",
      "encodeStartGame" in packets_src and "BedrockVersion" in packets_src)

# ---------- 集計 ----------
print()
print(f"合計 {len(CHECKS)} 件中 {len(CHECKS) - len(FAILURES)} 件通過、"
      f"{len(FAILURES)} 件失敗")
if FAILURES:
    print("失敗したチェック:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
