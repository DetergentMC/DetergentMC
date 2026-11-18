#!/usr/bin/env python3
"""Bedrock 1.18.2（プロトコル 475）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py /
verify-be-534-tables.py / verify-be-527-tables.py /
verify-be-503-tables.py / verify-be-486-tables.py にならい、表・定数・述語の
前提を機械計数で確認する。すべて実ファイル（BedrockVersion.java /
BedrockProtocol.java / BedrockSession.java / protocol-1.18.0.json /
protocol-1.18.11.json / protocol-1.18.30.json / protocol-1.19.1.json / protocol-1.19.10.json /
protocol-1.19.20.json / protocol-1.19.30.json / protocol-1.19.40.json /
protocol-1.19.50.json / .bin データ）に対するチェックで、推測はない。

- 475 の protocol.json は minecraft-data の `data/bedrock/1.18.0/` にある。
- 475 は 1.18.0 / 1.18.1 / 1.18.2 で共有するプロトコル
  （Minecraft Wiki「Bedrock Edition 1.18.0」「Bedrock Edition 1.18.1」
  「Bedrock Edition 1.18.2」個別ページの infobox「Protocol version 475」・
  protocolVersions.json では 475＝1.18.0 のみ。displayName は共有する
  うち最新の 1.18.2（486 の V1_18_12 と同じ流儀）。minecraft-data に
  1.18.1／1.18.2 の protocol.json は無く（HTTP 404）、475 の代表
  ディレクトリは 1.18.0 のみ。BE-NOTES.md §15）。
- **475 の protocol.json は 486 のものとバイト同一ではない**
  （9,752 行・252,425 バイト・SHA-1 `f58c442e…`、cmp で差分あり）。
  475 と 486 の差分は追加 3（176 client_start_item_cooldown・
  177 script_message・178 code_builder_source）・削除 0・リネーム 0・
  型変更 17 件。
- 475 の 1 つ下は 471（1.17.40）。472〜474 にエントリは無い。
  476〜485 は飛ばす（476＝Beta 1.18.10.21／1.18.0.23、477＝Beta
  1.18.10.22、479＝Beta 1.18.10.24、485＝Beta 1.18.10.27。
  478・480〜484 は Wiki 表にエントリ無しで未確認。BE-NOTES.md §15）。
- アイテム表は新規生成が必要（bedrock_item_ids_1_18_0.bin）。
  bedrock-1.18.0 タグは存在する（4 ファイルとも HTTP 200）。
  1.18.0 自体の実測データで、代用ではない。ただし bedrock-1.18.1／
  bedrock-1.18.2 タグは存在しないため、1.18.1／1.18.2 自体の
  レジストリが 1.18.0 と同一かは実測できない（未確認）。1_18_10 の表とは
  37/1312 行が違うため共用できない（BE-NOTES.md §15）。
- ブロックパレットは 1.18.10／1.18.30／1.19.0／1.19.10／1.19.40 のものと
  バイト同一ではない（7,948 エントリー）。それでもブロック表は新規
  不要（既存ペアを流用。589 と同じ根拠。BE-NOTES.md §15）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 475 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 475 回の実質的な変更：**新たに必要なバージョン分岐は無し**。
  475 は核パケットについては 486 形そのもので、486 回で入った分岐
  （hasStartGameLu64Seed() >=503、hasAddPlayerGamemode() >=503 など）は
  475 でも同じ真偽になる。BedrockVersion.java の全述語の閾値を実測し、
  475 と 486 の間に落ちる閾値が一つも無いことを確認した（§J）。
  StartGame は 475 と 486 で JSON 完全一致（62 フィールド、seed・
  dimension ともに zigzag32）。AddPlayer は device_os の型だけ違う
  （475 は li32、486 は li32 ベースの DeviceOS mapper でワイヤ同一）。
  LevelChunk は 486 で highest_subchunk_count の switch が挿入されるが、
  現行コードの送る形では分岐不要（BE-NOTES.md §15）。

使い方: python3 detergentmc-bedrock/tools/verify-be-475-tables.py
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


def item_stack_request_type_id(types):
    """ItemStackRequest の actions 配列の要素コンテナにある type_id の mapper を返す。
    type_id は ItemStackRequest の直下フィールドではなく actions 要素の中にある。"""
    actions = field_type(types, "ItemStackRequest", "actions")
    inner = actions[1]["type"]
    for f in inner[1]:
        if f.get("name") == "type_id":
            return f["type"]
    return None


def packet_ids(data):
    mapper = data["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
    return {int(k): v for k, v in mapper.items()}


# ---------- A. protocol.json（475）の同一性 ----------
p75 = TMP / "protocol-1.18.0.json"
check("475 protocol.json が存在する", p75.is_file(), str(p75))
check("475 protocol.json の行数が 9,752 行（wc -l、改行数）",
      p75.is_file()
      and p75.read_text().count("\n") == 9752,
      f"{p75.read_text().count(chr(10))} 行" if p75.is_file() else "なし")
check("475 protocol.json のサイズが 252,425 バイト",
      p75.is_file() and p75.stat().st_size == 252425,
      f"{p75.stat().st_size} バイト" if p75.is_file() else "なし")
check("475 protocol.json の SHA-1 が f58c442e…と一致",
      p75.is_file()
      and sha1(p75) == "f58c442e7d7072994e929b5eb3d63522ef334530",
      sha1(p75) if p75.is_file() else "なし")

data475 = json.loads(p75.read_text())
types475 = data475["types"]
ids475 = packet_ids(data475)

# ---------- A2. 隣接バージョン（486・503・527・534・544・554・557・560）の protocol.json ----------
p86 = TMP / "protocol-1.18.11.json"
check("486 protocol.json（上側の隣接）が存在する", p86.is_file(), str(p86))
check("486 protocol.json の行数が 10,044 行・サイズが 260,293 バイト・SHA-1 が 2ffd5781…と一致",
      p86.is_file() and p86.read_text().count("\n") == 10044
      and p86.stat().st_size == 260293
      and sha1(p86) == "2ffd57813e9904b8962ddd390c6b521b77ab4aa0",
      f"{p86.read_text().count(chr(10))} 行・{p86.stat().st_size} バイト" if p86.is_file() else "なし")
check("475 の protocol.json は 486 のものとバイト同一ではない（SHA-1 が違う）",
      p75.is_file() and p86.is_file()
      and sha1(p75) != sha1(p86))
p03 = TMP / "protocol-1.18.30.json"
check("503 protocol.json（上側の隣接）が存在する", p03.is_file(), str(p03))
check("503 protocol.json の行数が 10,289 行・サイズが 267,010 バイト・SHA-1 が 7d84fdc2…と一致",
      p03.is_file() and p03.read_text().count("\n") == 10289
      and p03.stat().st_size == 267010
      and sha1(p03) == "7d84fdc210ed3fb93f584894bc06875839fe5050",
      f"{p03.read_text().count(chr(10))} 行・{p03.stat().st_size} バイト" if p03.is_file() else "なし")
check("486 の protocol.json は 503 のものとバイト同一ではない（SHA-1 が違う）",
      p86.is_file() and p03.is_file()
      and sha1(p86) != sha1(p03))
p101 = TMP / "protocol-1.19.1.json"
check("527 protocol.json が存在する", p101.is_file(), str(p101))
check("527 protocol.json の行数が 10,473 行・サイズが 271,781 バイト・SHA-1 が 02b182a1…と一致",
      p101.is_file() and p101.read_text().count("\n") == 10473
      and p101.stat().st_size == 271781
      and sha1(p101) == "02b182a1e3433ec115a1ec854c1ef07b30e6ff4e",
      f"{p101.read_text().count(chr(10))} 行・{p101.stat().st_size} バイト" if p101.is_file() else "なし")
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
    check("protocolVersions.json に 476〜485 のエントリが無い",
          not any(476 <= e["version"] <= 485 for e in pv))
    check("protocolVersions.json に 472〜474 のエントリが無い",
          not any(472 <= e["version"] <= 474 for e in pv))
    check("protocolVersions.json の 475 は 1.18.0",
          [e["minecraftVersion"] for e in pv if e["version"] == 475] == ["1.18.0"])
    check("protocolVersions.json の 475 の 1 つ下は 471（1.17.40）",
          max(e["version"] for e in pv if e["version"] < 475) == 471)
    check("protocolVersions.json の 471 は 1.17.40",
          [e["minecraftVersion"] for e in pv if e["version"] == 471] == ["1.17.40"])
    check("protocolVersions.json の 486 は 1.18.11",
          [e["minecraftVersion"] for e in pv if e["version"] == 486] == ["1.18.11"])
    check("protocolVersions.json の 486 の 1 つ下は 475（1.18.0）",
          max(e["version"] for e in pv if e["version"] < 486) == 475)
    check("protocolVersions.json の 503 は 1.18.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 503] == ["1.18.30"])
    check("protocolVersions.json の 503 の 1 つ下は 486（1.18.11）",
          max(e["version"] for e in pv if e["version"] < 503) == 486)
    check("protocolVersions.json の 527 は 1.19.1",
          [e["minecraftVersion"] for e in pv if e["version"] == 527] == ["1.19.1"])

data486 = json.loads(p86.read_text()) if p86.is_file() else None
data503 = json.loads(p03.read_text()) if p03.is_file() else None
data527 = json.loads(p101.read_text()) if p101.is_file() else None
data534 = json.loads(p110.read_text()) if p110.is_file() else None
data544 = json.loads(p120.read_text()) if p120.is_file() else None
data554 = json.loads(p130.read_text()) if p130.is_file() else None
data557 = json.loads(p140.read_text()) if p140.is_file() else None
data560 = json.loads(p150.read_text()) if p150.is_file() else None
types486 = data486["types"] if data486 else {}
types503 = data503["types"] if data503 else {}
types527 = data527["types"] if data527 else {}
types534 = data534["types"] if data534 else {}
types544 = data544["types"] if data544 else {}
types554 = data554["types"] if data554 else {}
types557 = data557["types"] if data557 else {}
types560 = data560["types"] if data560 else {}
ids486 = packet_ids(data486) if data486 else {}
ids503 = packet_ids(data503) if data503 else {}
ids527 = packet_ids(data527) if data527 else {}

# ---------- B. パケット一覧（475）の件数・境界 ----------
check("475 のパケット数が 172 件", len(ids475) == 172, f"{len(ids475)} 件")
check("475 の最大パケット ID が 175", max(ids475) == 175, f"最大 {max(ids475)}")
check("475 の型定義数が 265", len(types475) == 265, f"{len(types475)}")
check("486 のパケット数が 175 件", len(ids486) == 175, f"{len(ids486)} 件")
check("486 の最大パケット ID が 178", max(ids486) == 178, f"最大 {max(ids486)}")
check("486 の型定義数が 271", len(types486) == 271, f"{len(types486)}")
check("475 の ID の欠番が 0・16・37・116（486 と同じ）",
      [i for i in range(max(ids475) + 1) if i not in ids475] == [0, 16, 37, 116])
check("475 のパケット ID 0〜175 が 486 の同 ID と名称がすべて一致",
      all(ids475[i] == ids486[i] for i in ids475),
      f"{sum(1 for i in ids475 if ids475[i] == ids486.get(i))} / {len(ids475)} 件一致")

d475 = TMP / "packet-diff-475-vs-486.json"
d486 = TMP / "packet-diff-486-vs-503.json"
check("475 vs 486 の差分 JSON が存在する", d475.is_file(), str(d475))
check("486 vs 503 の差分 JSON が存在する", d486.is_file(), str(d486))
if d475.is_file() and d486.is_file():
    j475 = json.loads(d475.read_text())
    j486 = json.loads(d486.read_text())
    check("475 vs 486：追加 3 件が 176 client_start_item_cooldown・177 script_message・178 code_builder_source",
          j475["added"] == {"176": "client_start_item_cooldown", "177": "script_message",
                            "178": "code_builder_source"},
          json.dumps(j475["added"]))
    check("475 vs 486：削除 0・リネーム 0",
          j475["removed"] == {} and j475["renamed"] == {})
    check("475 vs 486：型変更 17 件",
          len(j475["changed_types"]) == 17, str(j475["changed_types"]))
    check("475 vs 486：型変更の内訳が CommandOrigin・DeviceOS (only in B)・ItemStackRequest・MetadataFlags2・SoundType・SubChunkEntryWithCaching (only in B)・SubChunkEntryWithoutCaching (only in B)・mcpe_packet・packet_add_player・packet_add_volume_entity・packet_boss_event・packet_client_start_item_cooldown (only in B)・packet_code_builder_source (only in B)・packet_level_chunk・packet_script_message (only in B)・packet_subchunk・packet_subchunk_request",
          j475["changed_types"] == ["CommandOrigin", "DeviceOS (only in B)",
                                     "ItemStackRequest", "MetadataFlags2",
                                     "SoundType",
                                     "SubChunkEntryWithCaching (only in B)",
                                     "SubChunkEntryWithoutCaching (only in B)",
                                     "mcpe_packet", "packet_add_player",
                                     "packet_add_volume_entity",
                                     "packet_boss_event",
                                     "packet_client_start_item_cooldown (only in B)",
                                     "packet_code_builder_source (only in B)",
                                     "packet_level_chunk",
                                     "packet_script_message (only in B)",
                                     "packet_subchunk",
                                     "packet_subchunk_request"],
          json.dumps(j475["changed_types"]))
    check("486 vs 503：追加 4 件が 179 ticking_areas_load_status・180 dimension_data・181 agent_action・182 change_mob_property",
          j486["added"] == {"179": "ticking_areas_load_status", "180": "dimension_data",
                            "181": "agent_action", "182": "change_mob_property"},
          json.dumps(j486["added"]))
    check("486 vs 503：削除 0・リネーム 0",
          j486["removed"] == {} and j486["renamed"] == {})
    check("486 vs 503：型変更 17 件",
          len(j486["changed_types"]) == 17, str(j486["changed_types"]))

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
json_by_name = {v: k for k, v in ids475.items()}
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
check("enum エントリ V1_18_2 が存在する", "V1_18_2" in entry_by_name)
check("V1_18_2 のプロトコルが 475",
      entry_by_name.get("V1_18_2", (None,))[0] == 475,
      str(entry_by_name.get("V1_18_2")))
check("V1_18_2 の表示名が 1.18.2",
      entry_by_name.get("V1_18_2", (None, None))[1] == "1.18.2")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_18_2 が V1_18_12 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_18_2(475") < ver_src.index("V1_18_12(486"))
check("enum の javadoc に 1.18.0 の protocol.json が含まれる",
      "1.18.0" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 475 -> \"(1_18_0)\";", ver_src)
check("itemTableTag：475 が 1_18_0 の表を使う（新規生成・1.18.0 自体の実測）", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_18_0.bin"
check("アイテム表 bedrock_item_ids_1_18_0.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が 1084ef02…と一致（1.18.0 候補表）",
      tag_bin.is_file()
      and sha1(tag_bin) == "1084ef02d20bba8295b5f48892f6c30a844170e0",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
check("1.18.1／1.18.2 名のアイテム表は無い（タグは出所の 1_18_0 が正しい）",
      not (RES / "bedrock_item_ids_1_18_1.bin").exists()
      and not (RES / "bedrock_item_ids_1_18_2.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規 1 本）", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s475 = summary.get("475", {})
check("item_tables_summary.json に 475 のエントリがある",
      "475" in summary)
check("item_tables_summary.json の 475 が tag=bedrock-1.18.0・exact=752／shared=236／legacy=33／fallback=291",
      s475.get("tag") == "bedrock-1.18.0" and s475.get("exact") == 752
      and s475.get("shared") == 236 and s475.get("legacy") == 33
      and s475.get("fallback") == 291,
      str({k: s475.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))
check("item_tables_summary.json の 475 の missing_names が 291 件",
      len(s475.get("missing_names", [])) == 291,
      f"{len(s475.get('missing_names', []))} 件")

# ---------- F. 述語の前提：protocol.json 側の事実 ----------
# (1) StartGame：62 フィールド・述語の前提はすべて「無し」
sg475 = field_names(types475, "packet_start_game")
sg486 = field_names(types486, "packet_start_game")
check("StartGame：475 は 62 フィールド", len(sg475) == 62, f"{len(sg475)} フィールド")
check("StartGame：486 は 62 フィールド", len(sg486) == 62, f"{len(sg486)} フィールド")
check("StartGame：475 と 486 のフィールド名がすべて同じ（順序も同じ）",
      sg475 == sg486)
check("StartGame：475 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types475, "packet_start_game", "seed") == "zigzag32")
check("StartGame：486 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types486, "packet_start_game", "seed") == "zigzag32")
check("StartGame：475 の dimension は zigzag32",
      field_type(types475, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：486 の dimension は zigzag32",
      field_type(types486, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：475 に property_data が無い（hasStartGame527Fields()=false の前提）",
      "property_data" not in sg475)
check("StartGame：475 に world_template_id が無い（hasStartGame527Fields()=false の前提）",
      "world_template_id" not in sg475)
check("StartGame：475 に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg475)
check("StartGame：475 に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg475 for t in ["server_identifier", "world_identifier",
                                   "scenario_identifier", "owner_identifier"]))
check("StartGame：475 に editor_world が無い（hasEditorWorld=false）",
      "editor_world" not in sg475)
check("StartGame：475 の permission_level は zigzag32（hasPermissionLevelMapper=false）",
      field_type(types475, "packet_start_game", "permission_level") == "zigzag32")
check("StartGame：475 の movement_authority が存在する（hasMovementAuthority=true）",
      "movement_authority" in sg475)
check("StartGame：475 の itemstates が存在する（hasItemstates=true）",
      "itemstates" in sg475)
check("StartGame：475 に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg475)
check("StartGame：475 に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg475 and "server_join_info" not in sg475)
check("StartGame：475 に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg475)
check("StartGame：475 に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg475)
check("StartGame：475 に created_in_editor・exported_from_editor・block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(f in sg475 for f in ["created_in_editor", "exported_from_editor",
                                   "block_network_ids_are_hashes"]))
check("StartGame：475 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg475)
check("StartGame：475 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg475)
check("StartGame：475 に persona_disabled・custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg475 and "custom_skins_disabled" not in sg475)
check("StartGame：475 に chat_restriction_level・disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg475
      and "disable_player_interactions" not in sg475)
check("StartGame：475 に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg475)

# (2) Resource packs info / stack：486 と同じ形
rpi = field_names(types475, "packet_resource_packs_info")
rpi486 = field_names(types486, "packet_resource_packs_info")
check("Resource packs info：475 は 486 と同じ 5 フィールド（must_accept・has_scripts・force_server_packs・behaviour_packs・texture_packs）",
      rpi == rpi486 == ["must_accept", "has_scripts", "force_server_packs",
                        "behaviour_packs", "texture_packs"])
check("Resource packs info：475 に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("Resource packs info：475 に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("Resource packs info：475 に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("Resource packs info：475 に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
check("TexturePackInfos の countType が li16（486 と同じ）",
      types475["TexturePackInfos"][1].get("countType") == "li16")
rps = field_names(types475, "packet_resource_pack_stack")
rps486 = field_names(types486, "packet_resource_pack_stack")
check("Resource pack stack：475 は 486 と同じ 6 フィールド（behavior_packs を含む）",
      rps == rps486 == ["must_accept", "behavior_packs", "resource_packs",
                        "game_version", "experiments",
                        "experiments_previously_used"])
check("Resource pack stack：475 に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

# (3) エンティティ・アイテム
check("Mob equipment の item が Item（ItemV4 ではない）",
      field_type(types475, "packet_mob_equipment", "item") == "Item")
check("Add item entity の item が Item",
      field_type(types475, "packet_add_item_entity", "item") == "Item")
mae = [n for n, _ in container_fields(types475, "packet_mob_armor_equipment")]
check("Mob armor equipment が helmet・chestplate・leggings・boots の 4 つ（body は無い）",
      mae == ["runtime_entity_id", "helmet", "chestplate", "leggings", "boots"])
check("ItemV4 型が 475 の protocol.json に存在しない", "ItemV4" not in types475)
ap475 = field_names(types475, "packet_add_player")
ap486 = field_names(types486, "packet_add_player")
check("Add player：475 は 21 フィールド",
      len(ap475) == 21, f"{len(ap475)} フィールド")
check("Add player：486 は 21 フィールド",
      len(ap486) == 21, f"{len(ap486)} フィールド")
check("Add player：475 の device_os は li32（486 は DeviceOS 型参照で中身は li32 ベースの mapper）",
      field_type(types475, "packet_add_player", "device_os") == "li32"
      and field_type(types486, "packet_add_player", "device_os") == "DeviceOS"
      and types486["DeviceOS"]
      == ["mapper", {"type": "li32", "mappings": {"0": "Undefined", "1": "Android", "2": "IOS", "3": "OSX", "4": "FireOS", "5": "GearVR", "6": "Hololens", "7": "Win10", "8": "Win32", "9": "Dedicated", "10": "TVOS", "11": "Orbis", "12": "NintendoSwitch", "13": "Xbox", "14": "WindowsPhone"}}])
check("Add player：475 のフィールド一覧が uuid・username・entity_id_self・runtime_entity_id・platform_chat_id・position・velocity・pitch・yaw・head_yaw・held_item・metadata・flags・command_permission・action_permissions・permission_level・custom_stored_permissions・user_id・links・device_id・device_os",
      ap475 == ["uuid", "username", "entity_id_self", "runtime_entity_id",
                "platform_chat_id", "position", "velocity", "pitch", "yaw",
                "head_yaw", "held_item", "metadata", "flags",
                "command_permission", "action_permissions", "permission_level",
                "custom_stored_permissions", "user_id", "links", "device_id",
                "device_os"])
check("Add player：486 のフィールド一覧が 475 と一致する",
      ap486 == ap475)
check("Add player：475・486 の entity_id_self は zigzag64（名称も型も同じ）",
      field_type(types475, "packet_add_player", "entity_id_self") == "zigzag64"
      and field_type(types486, "packet_add_player", "entity_id_self") == "zigzag64")
check("Add player：475 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap475)
check("Add player：486 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap486)
check("Add entity は 475 と 486 で同じ（11 フィールド・body_yaw が無い）",
      field_names(types475, "packet_add_entity") == field_names(types486, "packet_add_entity")
      and len(field_names(types475, "packet_add_entity")) == 11)

# (4) トランザクション・アイテム関連
leg = {n: t for n, t in container_fields(types475, "TransactionLegacy")}
check("TransactionLegacy の legacy_transactions が switch（486 と同じ）",
      isinstance(leg.get("legacy_transactions"), list)
      and leg["legacy_transactions"][0] == "switch")
tx = {n: t for n, t in container_fields(types475, "Transaction")}
tx_map = tx.get("transaction_type")[1]["mappings"]
check("Transaction の transaction_type が 0=normal・1=inventory_mismatch・2=item_use・3=item_use_on_entity・4=item_release（486 と同じ）",
      tx_map.get("0") == "normal"
      and tx_map.get("1") == "inventory_mismatch"
      and tx_map.get("2") == "item_use"
      and tx_map.get("3") == "item_use_on_entity"
      and tx_map.get("4") == "item_release")
check("Transaction の actions が TransactionActions（486 と同じ）",
      tx.get("actions") == "TransactionActions")
check("Transaction が 475 と 486 で JSON 同一",
      types475["Transaction"] == types486["Transaction"])
ta = types475["TransactionActions"]
ta_fields = {f["name"]: f["type"] for f in ta[1]["type"][1] if "name" in f}
check("TransactionActions の old/new item が旧型 Item（transactionUsesItemV4=false）",
      ta_fields.get("old_item") == "Item" and ta_fields.get("new_item") == "Item",
      f"old={ta_fields.get('old_item')} new={ta_fields.get('new_item')}")
check("TransactionActions に window_id／flags の option 欄が無い",
      "window_id" not in ta_fields and "flags" not in ta_fields)
check("ItemStackRequest に cause が無い（486 と同じ）",
      "cause" not in field_names(types475, "ItemStackRequest"))
check("RecipeIngredient：475 が旧形式（network_id zigzag32＋匿名 switch）",
      types475["RecipeIngredient"][0] == "container"
      and types475["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types475["RecipeIngredient"][1][1].get("anon") is True,
      json.dumps(types475["RecipeIngredient"][1][0]))
check("RecipeIngredient：486 も旧形式（network_id zigzag32＋匿名 switch）",
      types486["RecipeIngredient"][0] == "container"
      and types486["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types486["RecipeIngredient"][1][1].get("anon") is True)
check("RecipeIngredient：475 の匿名 switch が network_id=0 で void、default で network_data＋count（zigzag32）",
      types475["RecipeIngredient"][1][1]["type"][1].get("compareTo") == "network_id"
      and types475["RecipeIngredient"][1][1]["type"][1]["fields"].get("0") == "void"
      and [f["name"] for f in types475["RecipeIngredient"][1][1]["type"][1]["default"][1]] == ["network_data", "count"])
check("RecipeIngredient：554 が新形式（先頭が type の u8 マッパー：invalid／int_id_meta／molang／item_tag／string_id_meta）",
      types554["RecipeIngredient"][0] == "container"
      and types554["RecipeIngredient"][1][0].get("name") == "type"
      and types554["RecipeIngredient"][1][0]["type"][1].get("mappings") == {"0": "invalid", "1": "int_id_meta",
                                                                            "2": "molang", "3": "item_tag",
                                                                            "4": "string_id_meta"})

# (5) 核パケットの 475 vs 486：14 件 JSON 同一・2 件が違う
MAJOR = ["packet_add_player", "packet_add_entity", "packet_add_item_entity",
         "packet_level_chunk", "packet_move_entity", "packet_move_player",
         "packet_update_block", "packet_set_entity_data",
         "packet_mob_equipment", "packet_mob_armor_equipment",
         "packet_start_game", "packet_player_list",
         "packet_inventory_transaction", "packet_network_chunk_publisher_update",
         "packet_resource_packs_info", "packet_resource_pack_stack"]
for name in MAJOR:
    if name in ("packet_add_player", "packet_level_chunk"):
        continue
    check(f"核パケット {name} が 475 と 486 で JSON 同一（分岐不要）",
          types475[name] == types486[name])
check("核パケット packet_player_auth_input が 475 と 486 で JSON 同一（interaction_model は 527 で追加）",
      types475["packet_player_auth_input"] == types486["packet_player_auth_input"])
check("核パケット packet_start_game が 475 と 486 で JSON 同一（62 フィールド・seed・dimension ともに zigzag32）",
      types475["packet_start_game"] == types486["packet_start_game"])
check("核パケット packet_add_player が 475 と 486 で違う（device_os の型だけ：475 は li32、486 は DeviceOS mapper）",
      types475["packet_add_player"] != types486["packet_add_player"])
check("核パケット packet_level_chunk が 475 と 486 で違う（486 で highest_subchunk_count の switch が挿入）",
      types475["packet_level_chunk"] != types486["packet_level_chunk"])
check("核パケット packet_level_chunk：475 は 6 フィールド・486 は 7 フィールド（highest_subchunk_count が 486 で追加）",
      field_names(types475, "packet_level_chunk")
      == ["x", "z", "sub_chunk_count", "cache_enabled", "blobs", "payload"]
      and field_names(types486, "packet_level_chunk")
      == ["x", "z", "sub_chunk_count", "highest_subchunk_count", "cache_enabled",
          "blobs", "payload"])

# ---------- G. 境界：475 と 486・503・527・534・544・554・557・560 の分岐点 ----------
check("475 に 176〜178（client_start_item_cooldown・script_message・code_builder_source）が無い（486 で追加）",
      all(i not in ids475 for i in [176, 177, 178]))
check("486 に 176〜178 がある",
      all(i in ids486 for i in [176, 177, 178]))
check("パケット数：486 は 475 より 3 件多い", len(ids486) - len(ids475) == 3)
check("型定義数：486 は 475 より 6 件多い",
      len(types486) - len(types475) == 6)
check("SoundType：475 は 373 件・486 は 376 件",
      len(types475["SoundType"][1]["mappings"]) == 373
      and len(types486["SoundType"][1]["mappings"]) == 376)
check("SoundType：475 の 372 は Undefined、486 の 372 は Tongue（改名）",
      types475["SoundType"][1]["mappings"]["372"] == "Undefined"
      and types486["SoundType"][1]["mappings"]["372"] == "Tongue")
check("SoundType：486 の 373〜375 が 475 に無い（CrackIronGolem・RepairIronGolem・Undefined が 486 で追加）",
      not any(str(i) in types475["SoundType"][1]["mappings"] for i in range(373, 376))
      and types486["SoundType"][1]["mappings"]["373"] == "CrackIronGolem"
      and types486["SoundType"][1]["mappings"]["374"] == "RepairIronGolem"
      and types486["SoundType"][1]["mappings"]["375"] == "Undefined")
check("GameMode：475・486 ともに 6 件（0〜5、spectator は 503 で追加）",
      len(types475["GameMode"][1]["mappings"]) == 6
      and len(types486["GameMode"][1]["mappings"]) == 6
      and "6" not in types475["GameMode"][1]["mappings"]
      and "6" not in types486["GameMode"][1]["mappings"])
check("MetadataDictionary：475・486 ともにキー 124 個（125・126・127 は 503 で追加）",
      len(types475["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]) == 124
      and len(types486["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]) == 124)
check("MetadataFlags2：475 は 34 フラグ・486 は 38 フラグ（in_ascendable_block・over_descendable_block・croaking・eat_mob が 486 で追加）",
      len(types475["MetadataFlags2"][1]["flags"]) == 34
      and len(types486["MetadataFlags2"][1]["flags"]) == 38
      and types486["MetadataFlags2"][1]["flags"][34:] == ["in_ascendable_block",
                                                          "over_descendable_block",
                                                          "croaking", "eat_mob"])
check("packet_level_event：475・486 ともに 172 件（2037 sculk_charge・2038 sculk_charge_pop は 503 で追加）",
      len(field_type(types475, "packet_level_event", "event")[1]["mappings"]) == 172
      and len(field_type(types486, "packet_level_event", "event")[1]["mappings"]) == 172
      and "2037" not in field_type(types475, "packet_level_event", "event")[1]["mappings"])
check("packet_entity_event：475・486 ともに 54 件（caravan・grow_up・vibration_detected は 503 で追加）",
      len(field_type(types475, "packet_entity_event", "event_id")[1]["mappings"]) == 54
      and len(field_type(types486, "packet_entity_event", "event_id")[1]["mappings"]) == 54
      and not any(v in field_type(types475, "packet_entity_event", "event_id")[1]["mappings"].values()
                  for v in ["caravan", "grow_up", "vibration_detected"]))
check("packet_spawn_particle_effect：475・486 ともに 4 フィールド（molang_variables は 503 で追加）",
      field_names(types475, "packet_spawn_particle_effect")
      == ["dimension", "entity_id", "position", "particle_name"]
      and field_names(types486, "packet_spawn_particle_effect")
      == ["dimension", "entity_id", "position", "particle_name", ])
check("packet_add_volume_entity：475 は 3 フィールド（entity_id・nbt・engine_version）・486 は 5 フィールド（runtime_id・nbt・json_identifier・instance_name・engine_version）",
      field_names(types475, "packet_add_volume_entity")
      == ["entity_id", "nbt", "engine_version"]
      and field_names(types486, "packet_add_volume_entity")
      == ["runtime_id", "nbt", "json_identifier", "instance_name", "engine_version"])
check("packet_boss_event：475 の switch に query が無く 486 にある",
      "query" not in str(types475["packet_boss_event"])
      and "query" in str(types486["packet_boss_event"]))
check("CommandOrigin：475 の type は 0〜14（script まで）・486 は 15 executor が追加",
      field_type(types475, "CommandOrigin", "type")[1]["mappings"]
      == {"0": "player", "1": "block", "2": "minecart_block", "3": "dev_console",
          "4": "test", "5": "automation_player", "6": "client_automation",
          "7": "dedicated_server", "8": "entity", "9": "virtual",
          "10": "game_argument", "11": "entity_server", "12": "precompiled",
          "13": "game_director_entity_server", "14": "script"}
      and field_type(types486, "CommandOrigin", "type")[1]["mappings"]["15"] == "executor")
check("ItemStackRequest：475 の type_id は 0〜17（7=lab_table_combine・8=beacon_payment・9=mine_block）・486 は 7=place_in_container・8=take_out_container が挿入されて 0〜19",
      item_stack_request_type_id(types475)[1]["mappings"]
      == {"0": "take", "1": "place", "2": "swap", "3": "drop", "4": "destroy",
          "5": "consume", "6": "create", "7": "lab_table_combine",
          "8": "beacon_payment", "9": "mine_block", "10": "craft_recipe",
          "11": "craft_recipe_auto", "12": "craft_creative", "13": "optional",
          "14": "craft_grindstone_request", "15": "craft_loom_request",
          "16": "non_implemented", "17": "results_deprecated"}
      and item_stack_request_type_id(types486)[1]["mappings"]["7"] == "place_in_container"
      and item_stack_request_type_id(types486)[1]["mappings"]["8"] == "take_out_container"
      and item_stack_request_type_id(types486)[1]["mappings"]["9"] == "lab_table_combine")
check("ItemStackRequest：475・486 に cause が無い（557 で追加）",
      "cause" not in str(types475["ItemStackRequest"])
      and "cause" not in str(types486["ItemStackRequest"]))
check("SubChunkEntryWithCaching／SubChunkEntryWithoutCaching は 475 に無く 486 にある",
      "SubChunkEntryWithCaching" not in types475
      and "SubChunkEntryWithoutCaching" not in types475
      and "SubChunkEntryWithCaching" in types486
      and "SubChunkEntryWithoutCaching" in types486)
check("DeviceOS 型は 475 に無く 486 にある",
      "DeviceOS" not in types475 and "DeviceOS" in types486)
check("EntityProperties 型は 475・486 のどちらにも無い（557 で追加）",
      "EntityProperties" not in types475 and "EntityProperties" not in types486)
check("packet_subchunk：475 は旧形式 10 フィールド・486 は新形式 4 フィールド",
      field_names(types475, "packet_subchunk")
      == ["dimension", "x", "y", "z", "data", "request_result", "heightmap_type",
          "heightmap", "cache_enabled", "blob_id"]
      and field_names(types486, "packet_subchunk")
      == ["cache_enabled", "dimension", "origin", "entries"])
check("packet_subchunk_request：475 は旧形式 4 フィールド・486 は新形式 3 フィールド",
      field_names(types475, "packet_subchunk_request")
      == ["dimension", "x", "y", "z"]
      and field_names(types486, "packet_subchunk_request")
      == ["dimension", "origin", "requests"])
check("packet_adventure_settings の command_permission が 475 と 486 で同じ（inline varint mapper、0=normal・1=operator・2=host・3=automation・4=admin）",
      field_type(types475, "packet_adventure_settings", "command_permission")
      == field_type(types486, "packet_adventure_settings", "command_permission")
      == ["mapper", {"type": "varint", "mappings": {"0": "normal", "1": "operator",
                                                     "2": "host", "3": "automation",
                                                     "4": "admin"}}])
check("StructureBlockSettings：475 と 486 で同じ（non_ticking_players_and_ticking_areas は 503 で追加）",
      types475["StructureBlockSettings"] == types486["StructureBlockSettings"]
      and "non_ticking_players_and_ticking_areas" not in str(types475["StructureBlockSettings"]))
check("packet_available_commands：475 と 486 で同じ（引数型マッピングの入替は 503 で起きる）",
      types475["packet_available_commands"] == types486["packet_available_commands"])
check("RecipeIngredient：475・486 が旧形式・554 が新形式（分岐点は 554）",
      types475["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types486["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types554["RecipeIngredient"][1][0].get("name") == "type")
check("packet_text：475・486・527 に type 11 が無い（582 で追加）",
      "11" not in field_type(types475, "packet_text", "type")[1]["mappings"]
      and "11" not in field_type(types486, "packet_text", "type")[1]["mappings"]
      and "11" not in field_type(types527, "packet_text", "type")[1]["mappings"])
check("network_settings：475・486・527 の compression_threshold が u16（554 で lu16）",
      field_type(types475, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types486, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types527, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types554, "packet_network_settings", "compression_threshold") == "lu16")
check("structure_block_update：475・486・527 は 9 フィールド（water_logged は 554 で追加）",
      len(field_names(types475, "packet_structure_block_update")) == 9
      and len(field_names(types486, "packet_structure_block_update")) == 9
      and len(field_names(types527, "packet_structure_block_update")) == 9
      and "water_logged" not in field_names(types475, "packet_structure_block_update"))
# 544 の StartGame 追加 5 フィールドが 475・486・527・534 に無い
for f in ["persona_disabled", "custom_skins_disabled", "chat_restriction_level",
          "disable_player_interactions", "client_side_generation"]:
    check(f"StartGame：475・486・527・534 に {f} が無い（544 で追加されるもの）",
          f not in sg475 and f not in sg486
          and f not in field_names(types527, "packet_start_game")
          and f not in field_names(types534, "packet_start_game")
          and f in field_names(types544, "packet_start_game"))

# 567 の StartGame 追加（emote_chat_muted）が 475・486・527・534・544 に無い
check("StartGame：475・486・527・534・544 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg475 and "emote_chat_muted" not in sg486
      and "emote_chat_muted" not in field_names(types527, "packet_start_game")
      and "emote_chat_muted" not in field_names(types534, "packet_start_game")
      and "emote_chat_muted" not in field_names(types544, "packet_start_game"))
# 582 の StartGame 追加が 475・486・527・534・544・554・557・560 に無い
for f in ["created_in_editor", "exported_from_editor", "block_network_ids_are_hashes"]:
    check(f"StartGame：475・486・527・534・544・554・557・560 に {f} が無い（582 で追加されるもの）",
          f not in sg475 and f not in sg486
          and f not in field_names(types527, "packet_start_game")
          and f not in field_names(types534, "packet_start_game")
          and f not in field_names(types544, "packet_start_game")
          and f not in field_names(types554, "packet_start_game")
          and f not in field_names(types557, "packet_start_game")
          and f not in field_names(types560, "packet_start_game"))

# 589 の StartGame 追加（server_controlled_sound）が 475・486・527・534・544・554・557・560 に無い
check("StartGame：475・486・527・534・544・554・557・560 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg475
      and "server_controlled_sound" not in sg486
      and "server_controlled_sound" not in field_names(types527, "packet_start_game")
      and "server_controlled_sound" not in field_names(types534, "packet_start_game")
      and "server_controlled_sound" not in field_names(types544, "packet_start_game")
      and "server_controlled_sound" not in field_names(types554, "packet_start_game")
      and "server_controlled_sound" not in field_names(types557, "packet_start_game")
      and "server_controlled_sound" not in field_names(types560, "packet_start_game"))
check("582 の StartGame 削除要素（movement_authority・itemstates）が 475・486・527・534・544・554・557・560 にある",
      all(f in field_names(t, "packet_start_game")
          for t in [types475, types486, types527, types534, types544, types554, types557, types560]
          for f in ["movement_authority", "itemstates"]))
check("PlayerAttributes：475・486 は 5 項目（min・max・current・default・name、modifiers は無い）",
      [f["name"] for f in types475["PlayerAttributes"][1]["type"][1] if "name" in f]
      == ["min", "max", "current", "default", "name"]
      and [f["name"] for f in types486["PlayerAttributes"][1]["type"][1] if "name" in f]
      == ["min", "max", "current", "default", "name"])
check("clientbound_map_item_data：475・486 に origin が無い（560 で追加）",
      "origin" not in field_names(types475, "packet_clientbound_map_item_data")
      and "origin" not in field_names(types486, "packet_clientbound_map_item_data"))
check("map_info_request：475・486 は map_id のみ",
      field_names(types475, "packet_map_info_request") == ["map_id"]
      and field_names(types486, "packet_map_info_request") == ["map_id"])
check("modal_form_response：475・486 は form_id＋data の 2 フィールド",
      field_names(types475, "packet_modal_form_response") == ["form_id", "data"]
      and field_names(types486, "packet_modal_form_response") == ["form_id", "data"])
check("network_chunk_publisher_update：475・486 は coordinates＋radius の 2 フィールド",
      field_names(types475, "packet_network_chunk_publisher_update") == ["coordinates", "radius"]
      and field_names(types486, "packet_network_chunk_publisher_update") == ["coordinates", "radius"])

# ---------- H. ブロックデータ（1.18.0 の実測・新規不要の根拠） ----------
d475 = TMP / "be-475"
canon475 = d475 / "canon-bedrock-1.18.0.nbt"
req475 = d475 / "req-bedrock-1.18.0-required_item_list.json"
biome475 = d475 / "biomeid-bedrock-1.18.0.json"
blockid475 = d475 / "blockid-bedrock-1.18.0.json"
check("1.18.0 の canonical_block_states NBT が存在する", canon475.is_file())
check("1.18.0 の canonical_block_states NBT のサイズが 1,174,943 バイト",
      canon475.is_file() and canon475.stat().st_size == 1174943,
      f"{canon475.stat().st_size} バイト" if canon475.is_file() else "なし")
check("1.18.0 の canonical_block_states NBT の SHA-1 が 1295991d…と一致",
      canon475.is_file()
      and sha1(canon475) == "1295991d8cba2420428244f287b156774d1803a3",
      sha1(canon475) if canon475.is_file() else "なし")
check("1.18.0 の required_item_list.json が存在する", req475.is_file())
check("1.18.0 の required_item_list.json のサイズが 108,907 バイト",
      req475.is_file() and req475.stat().st_size == 108907,
      f"{req475.stat().st_size} バイト" if req475.is_file() else "なし")
check("1.18.0 の required_item_list.json の SHA-1 が 908bd84d…と一致",
      req475.is_file()
      and sha1(req475) == "908bd84d55aa36e75b6d5c130e64dc6d470ec250",
      sha1(req475) if req475.is_file() else "なし")
if req475.is_file():
    req_list = json.loads(req475.read_text())
    check("1.18.0 の required_item_list.json のエントリ数が 1,080",
          len(req_list) == 1080, f"{len(req_list)} 件")
check("1.18.0 の biome_id_map.json が存在する", biome475.is_file())
check("1.18.0 の biome_id_map.json の SHA-1 が 17fe0c96…と一致（1.18.10／1.18.30 のものとバイト同一）",
      biome475.is_file()
      and sha1(biome475) == "17fe0c969b93ea7e825ef0ec7391eb9240199784",
      sha1(biome475) if biome475.is_file() else "なし")
if biome475.is_file():
    biome_map = json.loads(biome475.read_text())
    check("1.18.0 の biome_id_map が 83 エントリー",
          len(biome_map) == 83, f"{len(biome_map)} 件")
check("1.18.0 の block_id_map.json が存在する", blockid475.is_file())
check("1.18.0 の block_id_map.json の SHA-1 が f3855a10…と一致（1.18.10／1.18.30／1.19.0／1.19.10 のものとバイト同一）",
      blockid475.is_file()
      and sha1(blockid475) == "f3855a102c698f960020d928bcaef10d25724062",
      sha1(blockid475) if blockid475.is_file() else "なし")
if blockid475.is_file():
    blockid_map = json.loads(blockid475.read_text())
    check("1.18.0 の block_id_map が 553 エントリー",
          len(blockid_map) == 553, f"{len(blockid_map)} 件")
check("1.18.0 名のブロック表は無い（新規不要、1.20.4／1.20.50 を共用）",
      not list(RES.glob("bedrock_block_runtime_ids_1_18_0.bin"))
      and not list(RES.glob("canonical_block_states_1_18_0.nbt")))

# ---------- I. 475 回で変えていないもの（既存の境界の維持） ----------
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

# ---------- J. 475 回の実質的な変更：新たに必要なバージョン分岐は無し ----------
packets_src = (SRC / "BedrockPackets.java").read_text()
check("BedrockPackets.java が hasStartGameLu64Seed() を使っている",
      "hasStartGameLu64Seed()" in packets_src)
check("BedrockPackets.java が hasAddPlayerGamemode() を使っている",
      "hasAddPlayerGamemode()" in packets_src)
check("BedrockPackets.java の seed の書き込みが hasStartGameLu64Seed() で分岐している（lu64 と zigzag32）",
      re.search(r"if \(version\.hasStartGameLu64Seed\(\)\) \{\s*out\.writeLittleLong\(0\); // seed"
                r".*?BedrockVarInt\.writeZigzag32\(out, 0\); // seed",
                packets_src, re.DOTALL) is not None)
check("BedrockPackets.java の gamemode の書き込みが hasAddPlayerGamemode() で分岐している",
      re.search(r"if \(version\.hasAddPlayerGamemode\(\)\) \{\s*BedrockVarInt\.writeZigzag32\(out, gamemode\);",
                packets_src) is not None)
check("BedrockVersion.java に hasStartGameLu64Seed() の定義がある（protocol >= 503）",
      re.search(r"public boolean hasStartGameLu64Seed\(\)\s*\{[^}]*protocol\s*>=\s*503",
                ver_src) is not None)
check("BedrockVersion.java に hasAddPlayerGamemode() の定義がある（protocol >= 503）",
      re.search(r"public boolean hasAddPlayerGamemode\(\)\s*\{[^}]*protocol\s*>=\s*503",
                ver_src) is not None)
check("BedrockPackets.java に encodeStartGame のバージョン分岐オーバーロードがある",
      "encodeStartGame" in packets_src and "BedrockVersion" in packets_src)
# 475 と 486 の間に落ちる述語の閾値が無いこと（分岐不要の根拠）
thresholds = sorted({int(n) for n in re.findall(r"protocol\s*>=\s*(\d+)", ver_src)})
check("BedrockVersion.java の述語の閾値に 476〜486 が無い（475 と 486 の間に分岐点が無い）",
      not any(476 <= t <= 486 for t in thresholds),
      f"閾値一覧: {thresholds}")
check("BedrockVersion.java の述語の最小の閾値は 332（313 回で resourcePacksInfoHasScripting を追加したため 332 が最小。465・471・475・486 はどれも 428 以上の同じ側）",
      min(thresholds) == 332, f"最小の閾値: {min(thresholds)}")
check("475 の StartGame は 486 と JSON 完全一致のため、486 形の encodeStartGame が 475 にも合う",
      types475["packet_start_game"] == types486["packet_start_game"])
check("475 の AddPlayer は device_os の型以外 486 と同じため、現行の encodeAddPlayer（li32 で device_os を書く）が 475・486 どちらにも合う",
      field_names(types475, "packet_add_player") == field_names(types486, "packet_add_player")
      and field_type(types475, "packet_add_player", "device_os") == "li32")

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
