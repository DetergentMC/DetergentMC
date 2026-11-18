#!/usr/bin/env python3
"""Bedrock 1.19.2（プロトコル 527）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py /
verify-be-534-tables.py にならい、表・定数・述語の前提を機械計数で確認する。
すべて実ファイル（BedrockVersion.java / BedrockProtocol.java /
BedrockSession.java / protocol-1.19.1.json / protocol-1.19.10.json /
protocol-1.19.20.json / protocol-1.19.30.json / protocol-1.19.40.json /
protocol-1.19.50.json / protocol-1.19.62.json / protocol-1.19.80.json /
protocol-1.20.0.json / .bin データ）に対するチェックで、推測はない。

- 527 の protocol.json は minecraft-data の `data/bedrock/1.19.1/` にある。
- 527 は 1.19.0 / 1.19.1 / 1.19.2 で共有するプロトコル（Minecraft Wiki
  「Bedrock Edition 1.19.2」「Bedrock Edition 1.19.0」の infobox
  「Protocol version 527」・同 Wiki「Protocol version」表で確認。
  protocolVersions.json でも 527＝1.19.1 のみ。displayName は共有する
  うち最新の 1.19.2（534 の V1_19_11 と同じ流儀））。
- **527 の protocol.json は 534 のものとバイト同一ではない**
  （10,473 行・271,781 バイト・SHA-1 `02b182a1…`、cmp で差分あり）。
  527 と 534 の差分は追加 4（187 update_abilities・188
  update_adventure_settings・189 death_info・190 editor_network）・
  削除 0・リネーム 0・型変更 17 件。
- 527 の 1 つ下は 526（Preview 1.19.0.68）のため飛ばし、次のリリース・
  プロトコルは 503（1.18.30）。528〜533 にエントリは無い
  （530・532・533 は Preview 1.19.10.20／.21／.22 の番号、
  528・529・531 はスキップされた番号）。
- アイテム表は新規生成が必要（bedrock_item_ids_1_19_0.bin）。
  1.19.1 自体の PMMP BedrockData タグは存在しないが、1.19.1 は
  The Wild Update（1.19.0）の Nintendo Switch 版のバージョン番号で、
  プロトコル 527 は 1.19.0 リリースそのもののプロトコル（サーバーバージョン
  1.19.1.01）のため、bedrock-1.19.0 タグのデータは 527 のリリース自体の
  データであり、545 回のような「代用」ではない。1_19_40 の表とは
  3/1312 行が違うため共用できない（BE-NOTES.md §12）。
- ブロックパレットは 1.19.10 とバイト同一（8,289 エントリー）。
  それでもブロック表は新規不要（既存ペアを流用。589 と同じ根拠。
  BE-NOTES.md §12）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 527 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 527 回の実質的な変更：StartGame の editor_world・permission_level
  のバージョン分岐、AddPlayer の旧形式分岐、AddEntity の body_yaw
  分岐。527 用コードが 534 形を無条件に書くと、527 の実ワイヤは
  壊れる（BE-NOTES.md §12）。このスクリプトの §J で分岐の存在を確認する。

使い方: python3 detergentmc-bedrock/tools/verify-be-527-tables.py
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


# ---------- A. protocol.json（527）の同一性 ----------
p101 = TMP / "protocol-1.19.1.json"
check("527 protocol.json が存在する", p101.is_file(), str(p101))
check("527 protocol.json の行数が 10,473 行（wc -l、改行数）",
      p101.is_file()
      and p101.read_text().count("\n") == 10473,
      f"{p101.read_text().count(chr(10))} 行" if p101.is_file() else "なし")
check("527 protocol.json のサイズが 271,781 バイト",
      p101.is_file() and p101.stat().st_size == 271781,
      f"{p101.stat().st_size} バイト" if p101.is_file() else "なし")
check("527 protocol.json の SHA-1 が 02b182a1…と一致",
      p101.is_file()
      and sha1(p101) == "02b182a1e3433ec115a1ec854c1ef07b30e6ff4e",
      sha1(p101) if p101.is_file() else "なし")

data527 = json.loads(p101.read_text())
types527 = data527["types"]
ids527 = packet_ids(data527)

# ---------- A2. 隣接バージョン（534・544・554・557・560）の protocol.json ----------
p110 = TMP / "protocol-1.19.10.json"
check("534 protocol.json（上側の隣接）が存在する", p110.is_file(), str(p110))
check("534 protocol.json の行数が 10,646 行・サイズが 275,518 バイト・SHA-1 が 6586b076…と一致",
      p110.is_file() and p110.read_text().count("\n") == 10646
      and p110.stat().st_size == 275518
      and sha1(p110) == "6586b07615e11c7a318c98da4170496ab2c95026",
      f"{p110.read_text().count(chr(10))} 行・{p110.stat().st_size} バイト" if p110.is_file() else "なし")
check("527 の protocol.json は 534 のものとバイト同一ではない（SHA-1 が違う）",
      p101.is_file() and p110.is_file()
      and sha1(p101) != sha1(p110))
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
    check("protocolVersions.json に 528〜533 のエントリが無い",
          not any(528 <= e["version"] <= 533 for e in pv))
    check("protocolVersions.json の 527 は 1.19.1",
          [e["minecraftVersion"] for e in pv if e["version"] == 527] == ["1.19.1"])
    check("protocolVersions.json の 527 の 1 つ下は 503（1.18.30、526 は Preview のためエントリ無し）",
          max(e["version"] for e in pv if e["version"] < 527) == 503)
    check("protocolVersions.json の 534 は 1.19.10 のみ",
          [e["minecraftVersion"] for e in pv if e["version"] == 534] == ["1.19.10"])
    check("protocolVersions.json の 544 は 1.19.20 のみ",
          [e["minecraftVersion"] for e in pv if e["version"] == 544] == ["1.19.20"])

data534 = json.loads(p110.read_text()) if p110.is_file() else None
data544 = json.loads(p120.read_text()) if p120.is_file() else None
data554 = json.loads(p130.read_text()) if p130.is_file() else None
data557 = json.loads(p140.read_text()) if p140.is_file() else None
data560 = json.loads(p150.read_text()) if p150.is_file() else None
ids534 = packet_ids(data534) if data534 else {}
ids544 = packet_ids(data544) if data544 else {}
ids554 = packet_ids(data554) if data554 else {}
ids557 = packet_ids(data557) if data557 else {}
ids560 = packet_ids(data560) if data560 else {}

# ---------- B. パケット一覧（527）の件数・境界 ----------
check("527 のパケット数が 183 件", len(ids527) == 183, f"{len(ids527)} 件")
check("527 の最大パケット ID が 186", max(ids527) == 186, f"最大 {max(ids527)}")
check("527 の型定義数が 281", len(types527) == 281, f"{len(types527)}")
check("534 のパケット数が 187 件", len(ids534) == 187, f"{len(ids534)} 件")
check("534 の最大パケット ID が 190", max(ids534) == 190, f"最大 {max(ids534)}")
check("534 の型定義数が 289", len(data534["types"]) == 289 if data534 else False)
check("544 のパケット数が 188 件", len(ids544) == 188, f"{len(ids544)} 件")
check("544 の最大パケット ID が 191", max(ids544) == 191, f"最大 {max(ids544)}")
check("544 の型定義数が 290", len(data544["types"]) == 290 if data544 else False)
check("527 の ID の欠番が 0・16・37・116（534・544 と同じ）",
      [i for i in range(max(ids527) + 1) if i not in ids527] == [0, 16, 37, 116])

d527 = TMP / "packet-diff-527-vs-534.json"
d534 = TMP / "packet-diff-534-vs-544.json"
d544 = TMP / "packet-diff-544-vs-545.json"
d545 = TMP / "packet-diff-545-vs-554.json"
check("527 vs 534 の差分 JSON が存在する", d527.is_file(), str(d527))
check("534 vs 544 の差分 JSON が存在する", d534.is_file(), str(d534))
check("544 vs 545 の差分 JSON が存在する", d544.is_file(), str(d544))
check("545 vs 554 の差分 JSON が存在する", d545.is_file(), str(d545))
if d527.is_file() and d534.is_file() and d544.is_file() and d545.is_file():
    j527 = json.loads(d527.read_text())
    j534 = json.loads(d534.read_text())
    j544 = json.loads(d544.read_text())
    j545 = json.loads(d545.read_text())
    check("527 vs 534：追加 4 件が 187 update_abilities・188 update_adventure_settings・189 death_info・190 editor_network",
          j527["added"] == {"187": "update_abilities", "188": "update_adventure_settings",
                            "189": "death_info", "190": "editor_network"},
          json.dumps(j527["added"]))
    check("527 vs 534：削除 0・リネーム 0",
          j527["removed"] == {} and j527["renamed"] == {})
    check("527 vs 534：型変更 17 件",
          len(j527["changed_types"]) == 17, str(j527["changed_types"]))
    check("527 vs 534：型変更に AbilityLayers・AbilitySet・CommandPermissionLevel・CommandPermissionLevelVarint・packet_death_info・packet_editor_network・packet_update_abilities・packet_update_adventure_settings（534 のみ）が含まれる",
          all(t in j527["changed_types"] for t in
              ["AbilityLayers (only in B)", "AbilitySet (only in B)",
               "CommandPermissionLevel (only in B)",
               "CommandPermissionLevelVarint (only in B)",
               "packet_death_info (only in B)",
               "packet_editor_network (only in B)",
               "packet_update_abilities (only in B)",
               "packet_update_adventure_settings (only in B)"]),
          json.dumps(j527["changed_types"]))
    check("534 vs 544：追加 1 件が 191 feature_registry",
          j534["added"] == {"191": "feature_registry"},
          json.dumps(j534["added"]))
    check("534 vs 544：削除 0・リネーム 0",
          j534["removed"] == {} and j534["renamed"] == {})
    check("534 vs 544：型変更 8 件（PlayerAttributes・mcpe_packet・packet_clientbound_map_item_data・packet_feature_registry・packet_map_info_request・packet_modal_form_response・packet_network_chunk_publisher_update・packet_start_game）",
          j534["changed_types"] == ["PlayerAttributes", "mcpe_packet",
                                     "packet_clientbound_map_item_data",
                                     "packet_feature_registry (only in B)",
                                     "packet_map_info_request",
                                     "packet_modal_form_response",
                                     "packet_network_chunk_publisher_update",
                                     "packet_start_game"],
          json.dumps(j534["changed_types"]))
    check("544 vs 545：追加 0・削除 0・リネーム 0・型変更 0（バイト同一）",
          j544["added"] == {} and j544["removed"] == {} and j544["renamed"] == {}
          and j544["changed_types"] == [],
          json.dumps(j544["changed_types"]))
    check("545 vs 554：追加 4 件が 192 server_stats・193 request_network_settings・194 game_test_request・195 game_test_results",
          j545["added"] == {"192": "server_stats", "193": "request_network_settings",
                            "194": "game_test_request", "195": "game_test_results"},
          json.dumps(j545["added"]))
    check("545 vs 554：削除 0・リネーム 0",
          j545["removed"] == {} and j545["renamed"] == {})
    check("545 vs 554：型変更 11 件",
          len(j545["changed_types"]) == 11, str(j545["changed_types"]))

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
json_by_name = {v: k for k, v in ids527.items()}
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
check("enum エントリ V1_19_2 が存在する", "V1_19_2" in entry_by_name)
check("V1_19_2 のプロトコルが 527",
      entry_by_name.get("V1_19_2", (None,))[0] == 527,
      str(entry_by_name.get("V1_19_2")))
check("V1_19_2 の表示名が 1.19.2",
      entry_by_name.get("V1_19_2", (None, None))[1] == "1.19.2")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_19_2 が V1_19_11 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_19_2(527") < ver_src.index("V1_19_11(534"))
check("enum の javadoc に 1.19.1 の protocol.json が含まれる",
      "1.19.1" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 527 -> \"(1_19_0)\";", ver_src)
check("itemTableTag：527 が 1_19_0 の表を使う（新規生成）", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_19_0.bin"
check("アイテム表 bedrock_item_ids_1_19_0.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が 41f4dc1c…と一致（1.19.0 候補表）",
      tag_bin.is_file()
      and sha1(tag_bin) == "41f4dc1c60da0aaa59c2a5598f21725388057c23",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
check("1.19.1／1.19.2 名のアイテム表は無い（タグは出所の 1_19_0 が正しい）",
      not (RES / "bedrock_item_ids_1_19_1.bin").exists()
      and not (RES / "bedrock_item_ids_1_19_2.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規 1 本）", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s527 = summary.get("527", {})
check("item_tables_summary.json に 527 のエントリがある",
      "527" in summary)
check("item_tables_summary.json の 527 が tag=bedrock-1.19.0・exact=800／shared=264／legacy=33／fallback=215",
      s527.get("tag") == "bedrock-1.19.0" and s527.get("exact") == 800
      and s527.get("shared") == 264 and s527.get("legacy") == 33
      and s527.get("fallback") == 215,
      str({k: s527.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))
check("item_tables_summary.json の 527 の missing_names が 215 件",
      len(s527.get("missing_names", [])) == 215,
      f"{len(s527.get('missing_names', []))} 件")

# ---------- F. 述語の前提と protocol.json（527）の突き合わせ ----------
sg = field_names(types527, "packet_start_game")
check("start_game が 64 フィールド（534 は 65 フィールド）", len(sg) == 64, f"{len(sg)} フィールド")
check("start_game に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg)
check("start_game に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg for t in ["server_identifier", "world_identifier",
                                "scenario_identifier", "owner_identifier"]))
check("start_game に editor_world が無い（hasEditorWorld=false）",
      "editor_world" not in sg)
check("start_game の permission_level が zigzag32（hasPermissionLevelMapper=false）",
      field_type(types527, "packet_start_game", "permission_level") == "zigzag32",
      str(field_type(types527, "packet_start_game", "permission_level")))
check("start_game に movement_authority がある（hasMovementAuthority=true）",
      "movement_authority" in sg)
check("start_game に itemstates がある（hasItemstates=true）",
      "itemstates" in sg)
check("start_game に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg)
check("start_game に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg and "server_join_info" not in sg)
check("start_game に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg)
check("start_game に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg)
check("start_game に created_in_editor／exported_from_editor／block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(t in sg for t in ["created_in_editor", "exported_from_editor",
                                "block_network_ids_are_hashes"]))
check("start_game に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg)
check("start_game に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg)
check("start_game に persona_disabled／custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg and "custom_skins_disabled" not in sg)
check("start_game に chat_restriction_level／disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg and "disable_player_interactions" not in sg)
check("start_game に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg)

rpi = field_names(types527, "packet_resource_packs_info")
check("resource_packs_info に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("resource_packs_info に force_server_packs がある（hasForceServerPacks=true）",
      "force_server_packs" in rpi)
check("resource_packs_info に behaviour_packs がある（hasBehaviourPacks=true）",
      "behaviour_packs" in rpi)
check("resource_packs_info に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("resource_packs_info に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("resource_packs_info に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
tpi = types527["TexturePackInfos"]
check("texture_packs の件数が li16（resourcePacksCountIsVarint=false）",
      tpi[0] == "array" and tpi[1].get("countType") == "li16",
      json.dumps(tpi[1].get("countType")) if isinstance(tpi[1], dict) else str(tpi))

rps = field_names(types527, "packet_resource_pack_stack")
check("resource_pack_stack に behavior_packs がある（hasBehaviorPacksInStack=true）",
      "behavior_packs" in rps)
check("resource_pack_stack に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

me = field_type(types527, "packet_mob_equipment", "item")
check("mob_equipment の item が旧型 Item（mobEquipmentUsesItemV4=false）",
      me == "Item", str(me))
aie = field_type(types527, "packet_add_item_entity", "item")
check("add_item_entity の item が旧型 Item（addItemUsesItemV4=false）",
      aie == "Item", str(aie))
mae = container_fields(types527, "packet_mob_armor_equipment")
mae_items = [n for n, t in mae if t == "Item"]
check("mob_armor_equipment が旧型 Item×4（helmet/chestplate/leggings/boots）",
      mae_items == ["helmet", "chestplate", "leggings", "boots"],
      str(mae_items))
check("mob_armor_equipment に body が無い（mobArmorHasBodySlot=false）",
      "body" not in mae_items)
check("ItemV4 型が 527 の protocol.json に存在しない",
      "ItemV4" not in types527)

legacy = types527["TransactionLegacy"]
legacy_map = {f["name"]: f["type"] for f in legacy[1]}
legacy_tx = legacy_map.get("legacy_transactions")
check("Transaction の legacy_transactions が switch 形式（transactionLegacyIsOption=false）",
      isinstance(legacy_tx, list) and len(legacy_tx) >= 1
      and legacy_tx[0] == "switch"
      and legacy_tx[1].get("compareTo") == "legacy_request_id",
      json.dumps(legacy_tx)[:80])
tx = types527["Transaction"]
tx_map = {f["name"]: f["type"] for f in tx[1]}
check("Transaction の transaction_type／actions が素のフィールド（transactionFieldsAreOptions=false）",
      (isinstance(tx_map.get("transaction_type"), list)
       and tx_map["transaction_type"][0] == "mapper"
       and tx_map["transaction_type"][1].get("type") == "varint")
      and tx_map.get("actions") == "TransactionActions",
      f"type={json.dumps(tx_map.get('transaction_type'))[:60]} actions={tx_map.get('actions')}")
ta = types527["TransactionActions"]
ta_fields = {f["name"]: f["type"] for f in ta[1]["type"][1] if "name" in f}
check("TransactionActions の old/new item が旧型 Item（transactionUsesItemV4=false）",
      ta_fields.get("old_item") == "Item" and ta_fields.get("new_item") == "Item",
      f"old={ta_fields.get('old_item')} new={ta_fields.get('new_item')}")
check("TransactionActions に window_id／flags の option 欄が無い",
      "window_id" not in ta_fields and "flags" not in ta_fields)
use_entity = tx_map["transaction_data"][1]["fields"]["item_use_on_entity"]
held = [f["type"] for f in use_entity[1] if f["name"] == "held_item"]
check("item_use_on_entity の held_item が旧型 Item",
      held == ["Item"], str(held))

# 527 と 534 の主要パケット（StartGame・add_player・add_entity を除く 13 件）が JSON 完全一致
if data534:
    types534 = data534["types"]
    for pname in ["packet_resource_packs_info",
                  "packet_resource_pack_stack", "packet_mob_equipment",
                  "packet_add_item_entity", "packet_mob_armor_equipment",
                  "packet_inventory_transaction", "packet_level_chunk",
                  "packet_update_block", "packet_move_player",
                  "packet_set_entity_data", "packet_player_auth_input",
                  "packet_emote", "packet_command_request"]:
        check(f"{pname} が 527 と 534 で JSON 完全一致",
              types527.get(pname) == types534.get(pname))
    check("packet_start_game は 527 と 534 で異なる（editor_world 1 フィールド＋permission_level の型）",
          types527.get("packet_start_game") != types534.get("packet_start_game"))
    check("packet_add_player は 527 と 534 で異なる（527 は旧形式 22 フィールド、534 は 19 フィールド）",
          types527.get("packet_add_player") != types534.get("packet_add_player"))
    check("packet_add_entity は 527 と 534 で異なる（534 に body_yaw が追加）",
          types527.get("packet_add_entity") != types534.get("packet_add_entity"))

# 527 と 557 の主要パケット（StartGame と add_player・add_entity・set_entity_data を除く 12 件）が JSON 完全一致
if data557:
    types557 = data557["types"]
    for pname in ["packet_resource_packs_info",
                  "packet_resource_pack_stack", "packet_mob_equipment",
                  "packet_add_item_entity", "packet_mob_armor_equipment",
                  "packet_inventory_transaction", "packet_level_chunk",
                  "packet_update_block", "packet_move_player",
                  "packet_player_auth_input",
                  "packet_emote", "packet_command_request"]:
        check(f"{pname} が 527 と 557 で JSON 完全一致",
              types527.get(pname) == types557.get(pname))
    check("packet_start_game は 527 と 557 で異なる（534 追加分 editor_world＋544 追加分 5 フィールド＋emote_chat_muted）",
          types527.get("packet_start_game") != types557.get("packet_start_game"))
    check("packet_add_player・packet_add_entity・packet_set_entity_data は 527 と 557 で異なる（527 は旧形式、557 に properties が追加）",
          types527.get("packet_add_player") != types557.get("packet_add_player")
          and types527.get("packet_add_entity") != types557.get("packet_add_entity")
          and types527.get("packet_set_entity_data") != types557.get("packet_set_entity_data"))

# ---------- G. バージョン境界の確認 ----------
# 187〜190（534 で追加されるパケット）が 527 には無い
check("527 に 187 update_abilities が無い（534 で追加）",
      ids527.get(187) is None)
check("527 に 188 update_adventure_settings が無い（534 で追加）",
      ids527.get(188) is None)
check("527 に 189 death_info が無い（534 で追加）",
      ids527.get(189) is None)
check("527 に 190 editor_network が無い（534 で追加）",
      ids527.get(190) is None)
check("534 に 187〜190（update_abilities・update_adventure_settings・death_info・editor_network）がある",
      [ids534.get(i) for i in (187, 188, 189, 190)] ==
      ["update_abilities", "update_adventure_settings", "death_info",
       "editor_network"])

# 527 は 534 より 4 件少なく、534 は 544 より 1 件少ない
check("534 のパケット数が 527 より 4 件多い",
      len(ids534) - len(ids527) == 4)
check("544 のパケット数が 534 より 1 件多い",
      len(ids544) - len(ids534) == 1)
check("544 の型定義数が 534 より 1 個多い",
      len(data544["types"]) - len(data534["types"]) == 1)
check("544 の protocol.json は 534 のものとバイト同一ではない（SHA-1 が違う）",
      p120.is_file() and p110.is_file()
      and sha1(p120) != sha1(p110))
check("544 の protocol.json は 545 のものとバイト同一（SHA-1 が同じ 60acb22c…）",
      (TMP / "protocol-1.19.21.json").is_file()
      and sha1(TMP / "protocol-1.19.21.json") == "60acb22c50099d389704f9a811cf2682d0028241")

# StartGame の editor_world と permission_level の 527 形を直接確認
sg534 = field_names(data534["types"], "packet_start_game")
check("start_game が 534 で 65 フィールド（527 は 64）", len(sg534) == 65, f"{len(sg534)} フィールド")
check("start_game に editor_world があるのは 534 のみ（527 には無い）",
      "editor_world" in sg534 and "editor_world" not in sg)
check("start_game の permission_level が 534 では PermissionLevel（u8 マッパー）",
      field_type(data534["types"], "packet_start_game", "permission_level") == "PermissionLevel"
      and data534["types"].get("PermissionLevel", [None, {}])[0] == "mapper"
      and data534["types"]["PermissionLevel"][1].get("type") == "u8",
      str(field_type(data534["types"], "packet_start_game", "permission_level"))[:80])

# add_player の 527 形（旧形式）と 534 形の確認
ap527 = field_names(types527, "packet_add_player")
ap534 = field_names(data534["types"], "packet_add_player")
check("add_player が 527 で 22 フィールド（534 は 19 フィールド）",
      len(ap527) == 22 and len(ap534) == 19,
      f"527={len(ap527)} 534={len(ap534)}")
check("add_player が 527 で unique_entity_id・runtime_entity_id（534 では unique_id・runtime_id）",
      "unique_entity_id" in ap527 and "runtime_entity_id" in ap527
      and "unique_id" not in ap527 and "runtime_id" not in ap527
      and "unique_id" in ap534 and "runtime_id" in ap534
      and "unique_entity_id" not in ap534 and "runtime_entity_id" not in ap534)
check("add_player が 527 で能力系を直書き（flags・command_permission・action_permissions・permission_level・custom_stored_permissions・user_id）",
      all(f in ap527 for f in ["flags", "command_permission", "action_permissions",
                               "permission_level", "custom_stored_permissions", "user_id"])
      and "abilities" not in ap527)
check("add_player が 534 で能力系が abilities 配列（flags・action_permissions・custom_stored_permissions・user_id は直書き欄に無い）",
      "abilities" in ap534
      and not any(f in ap534 for f in ["flags", "action_permissions",
                                        "custom_stored_permissions", "user_id"]))
check("add_player が 534 で unique_id が li64・permission_level が PermissionLevel・command_permission が CommandPermissionLevel",
      field_type(data534["types"], "packet_add_player", "unique_id") == "li64"
      and field_type(data534["types"], "packet_add_player", "permission_level") == "PermissionLevel"
      and field_type(data534["types"], "packet_add_player", "command_permission") == "CommandPermissionLevel")
check("add_player の 527 の permission_level が varint（直書き能力系は全て varint、user_id は li64）",
      field_type(types527, "packet_add_player", "permission_level") == "varint"
      and field_type(types527, "packet_add_player", "flags") == "varint"
      and field_type(types527, "packet_add_player", "user_id") == "li64",
      str(field_type(types527, "packet_add_player", "permission_level")))

# add_entity の 527 形（body_yaw 無し）と 534 形の確認
ae527 = field_names(types527, "packet_add_entity")
ae534 = field_names(data534["types"], "packet_add_entity")
check("add_entity が 527 で 11 フィールド（534 は 12 フィールド）",
      len(ae527) == 11 and len(ae534) == 12,
      f"527={len(ae527)} 534={len(ae534)}")
check("add_entity に body_yaw があるのは 534 のみ（527 には無い）",
      "body_yaw" not in ae527 and "body_yaw" in ae534)
check("add_entity の 527 の body_yaw 以外の 11 フィールドが 534 と同じ（先頭の ID 2 項目は 527 が entity_id_self・runtime_entity_id、534 が unique_id・runtime_id）",
      ae527[2:] == [f for f in ae534 if f not in ("body_yaw", "unique_id", "runtime_id")]
      and ae527[:2] == ["entity_id_self", "runtime_entity_id"]
      and ae534[:2] == ["unique_id", "runtime_id"])

# 527 の EntityProperties が無い（557 で追加されるもの）
check("527 の protocol.json に EntityProperties 型が存在しない",
      "EntityProperties" not in types527)
check("527 の add_player・add_entity・set_entity_data の末尾に properties が無い",
      "properties" not in field_names(types527, "packet_add_player")
      and "properties" not in field_names(types527, "packet_add_entity")
      and "properties" not in field_names(types527, "packet_set_entity_data"))
check("534 の protocol.json にも EntityProperties 型が存在しない",
      "EntityProperties" not in data534["types"])
check("557 の protocol.json に EntityProperties 型が存在する",
      "EntityProperties" in data557["types"])
check("set_entity_data が 527・534・544 とも 3 フィールド（properties は 557 で追加）",
      len(field_names(types527, "packet_set_entity_data")) == 3
      and len(field_names(data534["types"], "packet_set_entity_data")) == 3
      and len(field_names(data544["types"], "packet_set_entity_data")) == 3)

# ItemStackRequest（cause は 554 で追加されるもの）
check("527 の ItemStackRequest に cause が無い（554 で追加）",
      "cause" not in field_names(types527, "ItemStackRequest"))
check("534 の ItemStackRequest にも cause が無い",
      "cause" not in field_names(data534["types"], "ItemStackRequest"))
check("554 の ItemStackRequest に cause がある（0〜10 の mapper）",
      field_type(data554["types"], "ItemStackRequest", "cause") is not None)

# RecipeIngredient（旧形式は network_id zigzag32＋匿名 switch、554 で新形式）
ri527 = types527["RecipeIngredient"]
ri534 = data534["types"]["RecipeIngredient"]
ri554 = data554["types"]["RecipeIngredient"]
check("527 の RecipeIngredient が旧形式（network_id zigzag32＋匿名 switch、count は switch の default 側）",
      ri527[0] == "container" and ri527[1][0] == {"name": "network_id", "type": "zigzag32"}
      and ri527[1][1].get("anon") is True,
      json.dumps(ri527[1][0]))
check("534 の RecipeIngredient も旧形式（network_id zigzag32＋匿名 switch）",
      ri534[0] == "container" and ri534[1][0] == {"name": "network_id", "type": "zigzag32"}
      and ri534[1][1].get("anon") is True)
check("527 の RecipeIngredient の匿名 switch が network_id=0 で void、default で network_data＋count（zigzag32）",
      ri527[1][1]["type"][1].get("compareTo") == "network_id"
      and ri527[1][1]["type"][1]["fields"].get("0") == "void"
      and [f["name"] for f in ri527[1][1]["type"][1]["default"][1]] == ["network_data", "count"])
check("554 の RecipeIngredient が新形式（先頭が type の u8 マッパー：invalid／int_id_meta／molang／item_tag／string_id_meta）",
      ri554[0] == "container" and ri554[1][0].get("name") == "type"
      and ri554[1][0]["type"][1].get("mappings") == {"0": "invalid", "1": "int_id_meta",
                                                     "2": "molang", "3": "item_tag",
                                                     "4": "string_id_meta"})

# SoundType（527 は 442 件、534 は 443 件、554 は 443 件）
st527 = types527["SoundType"][1]["mappings"]
st534 = data534["types"]["SoundType"][1]["mappings"]
st554 = data554["types"]["SoundType"][1]["mappings"]
check("527 の SoundType が 442 件（534 は 443 件）",
      len(st527) == 442 and len(st534) == 443, f"527={len(st527)} 534={len(st534)}")
check("527 の SoundType 441 が Undefined（534 も 442 が Undefined）",
      st527.get("441") == "Undefined" and st534.get("442") == "Undefined")
check("527 の SoundType 432 が _（534 は DrinkMilk）",
      st527.get("432") == "_" and st534.get("432") == "DrinkMilk")
check("527 の SoundType 442 が無い（534 には 442 がある）",
      "442" not in st527 and "442" in st534)
check("554 の SoundType 442 が EnchantingTableUse（534 は Undefined）",
      st554.get("442") == "EnchantingTableUse")

# packet_text（json_announcement は 554 で追加されるもの）
text527 = [f for f in types527["packet_text"][1] if f.get("name") == "type"]
text534 = [f for f in data534["types"]["packet_text"][1] if f.get("name") == "type"]
text554 = [f for f in data554["types"]["packet_text"][1] if f.get("name") == "type"]
check("527 の packet_text に type 11（json_announcement）が無い（534 も無い、554 にある）",
      text527[0]["type"][1]["mappings"].get("11") is None
      and text534[0]["type"][1]["mappings"].get("11") is None
      and text554[0]["type"][1]["mappings"].get("11") == "json_announcement")

# packet_network_settings・packet_structure_block_update（554 で型・フィールドが変わるもの）
check("527 の packet_network_settings の compression_threshold が u16（534 も u16、554 は lu16）",
      field_type(types527, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(data534["types"], "packet_network_settings", "compression_threshold") == "u16"
      and field_type(data554["types"], "packet_network_settings", "compression_threshold") == "lu16")
sbu527 = field_names(types527, "packet_structure_block_update")
sbu534 = field_names(data534["types"], "packet_structure_block_update")
sbu554 = field_names(data554["types"], "packet_structure_block_update")
check("527 の packet_structure_block_update が 9 フィールド（534 も 9、554 は 10）",
      len(sbu527) == 9 and len(sbu534) == 9 and len(sbu554) == 10,
      f"527={len(sbu527)} 534={len(sbu534)} 554={len(sbu554)}")
check("554 の packet_structure_block_update に water_logged が追加される（527・534 の 9 フィールドはそのまま）",
      len(sbu554) == 10 and sbu554[:9] == sbu527 and sbu554[9] == "water_logged",
      str(sbu554))

# packet_adventure_settings（コマンド権限マッピングが 534 で入れ替わるもの）
av527 = [f for f in types527["packet_adventure_settings"][1] if f["name"] == "command_permission"]
av534 = [f for f in data534["types"]["packet_adventure_settings"][1] if f["name"] == "command_permission"]
check("527 の packet_adventure_settings の command_permission が normal／host=2／automation=3（inline の varint マッパー）",
      av527[0]["type"][0] == "mapper"
      and av527[0]["type"][1].get("type") == "varint"
      and av527[0]["type"][1]["mappings"] == {"0": "normal", "1": "operator",
                                              "2": "host", "3": "automation",
                                              "4": "admin"},
      json.dumps(av527[0]["type"][1]["mappings"]))
check("534 の packet_adventure_settings の command_permission が normal／automation=2／host=3（CommandPermissionLevelVarint 名前型の u8 マッパー）",
      av534[0]["type"] == "CommandPermissionLevelVarint"
      and data534["types"]["CommandPermissionLevelVarint"][0] == "mapper"
      and data534["types"]["CommandPermissionLevelVarint"][1].get("type") == "u8"
      and data534["types"]["CommandPermissionLevelVarint"][1]["mappings"] == {"0": "normal", "1": "operator",
                                                                               "2": "automation", "3": "host",
                                                                               "4": "owner", "5": "internal"},
      json.dumps(data534["types"]["CommandPermissionLevelVarint"][1]["mappings"]))

# packet_play_status（ステータス 8・9 は 534 で追加されるもの）
ps527 = [f for f in types527["packet_play_status"][1] if f["name"] == "status"]
ps534 = [f for f in data534["types"]["packet_play_status"][1] if f["name"] == "status"]
check("527 の packet_play_status に 8・9 が無い（534 にある）",
      "8" not in ps527[0]["type"][1]["mappings"]
      and "9" not in ps527[0]["type"][1]["mappings"]
      and "8" in ps534[0]["type"][1]["mappings"]
      and "9" in ps534[0]["type"][1]["mappings"],
      f"527={json.dumps(ps527[0]['type'][1]['mappings'])}")
check("527 の packet_play_status が 0 login_success・1 failed_client・2 failed_spawn・3 player_spawn・4 failed_invalid_tenant・5 failed_vanilla_edu・6 failed_edu_vanilla・7 failed_server_full",
      ps527[0]["type"][1]["mappings"] == {"0": "login_success",
                                          "1": "failed_client",
                                          "2": "failed_spawn",
                                          "3": "player_spawn",
                                          "4": "failed_invalid_tenant",
                                          "5": "failed_vanilla_edu",
                                          "6": "failed_edu_vanilla",
                                          "7": "failed_server_full"})
check("534 の packet_play_status が 527 の 8 件に 8 failed_editor_vanilla_mismatch・9 failed_vanilla_editor_mismatch を追加",
      ps534[0]["type"][1]["mappings"].get("8") == "failed_editor_vanilla_mismatch"
      and ps534[0]["type"][1]["mappings"].get("9") == "failed_vanilla_editor_mismatch"
      and len(ps534[0]["type"][1]["mappings"]) == 10)

# packet_entity_event（78 drink_milk は 534 で追加されるもの）
ee527 = [f for f in types527["packet_entity_event"][1] if f["name"] == "event_id"]
ee534 = [f for f in data534["types"]["packet_entity_event"][1] if f["name"] == "event_id"]
check("527 の packet_entity_event に 78 drink_milk が無い（534 にある）",
      "78" not in ee527[0]["type"][1]["mappings"]
      and ee534[0]["type"][1]["mappings"].get("78") == "drink_milk",
      json.dumps(ee527[0]["type"][1]["mappings"]))

# 534 vs 544 の 6 つの型変更（527 は 534 と同じ旧形式であることを確認）
def array_item_fields(types, name):
    """array 型の要素 container のフィールド名一覧を返す。"""
    t = types[name]
    assert t[0] == "array", f"{name} は array ではない: {t[0]}"
    item = t[1]["type"]
    assert item[0] == "container", f"{name} の要素は container ではない: {item[0]}"
    return [f["name"] for f in item[1] if "name" in f]


pa527 = array_item_fields(types527, "PlayerAttributes")
pa534 = array_item_fields(data534["types"], "PlayerAttributes")
pa544 = array_item_fields(data544["types"], "PlayerAttributes")
check("527 の PlayerAttributes が 5 項目で modifiers が無い（534 と同じ）",
      pa527 == ["min", "max", "current", "default", "name"]
      and pa534 == ["min", "max", "current", "default", "name"],
      str(pa527))
check("544 の PlayerAttributes が 6 項目で modifiers がある",
      pa544 == ["min", "max", "current", "default", "name", "modifiers"],
      str(pa544))
cmid527 = field_names(types527, "packet_clientbound_map_item_data")
cmid534 = field_names(data534["types"], "packet_clientbound_map_item_data")
cmid544 = field_names(data544["types"], "packet_clientbound_map_item_data")
check("527 の packet_clientbound_map_item_data に origin が無い（534 と同じ、544 にある）",
      "origin" not in cmid527 and "origin" not in cmid534 and "origin" in cmid544)
mir527 = field_names(types527, "packet_map_info_request")
mir534 = field_names(data534["types"], "packet_map_info_request")
mir544 = field_names(data544["types"], "packet_map_info_request")
check("527 の packet_map_info_request が map_id のみ（534 と同じ、544 に client_pixels がある）",
      mir527 == ["map_id"] and mir534 == ["map_id"]
      and "client_pixels" in mir544, str(mir527))
mfr527 = field_names(types527, "packet_modal_form_response")
mfr534 = field_names(data534["types"], "packet_modal_form_response")
mfr544 = field_names(data544["types"], "packet_modal_form_response")
check("527 の packet_modal_form_response が 2 フィールド（form_id・data、534 と同じ）",
      mfr527 == ["form_id", "data"] and mfr534 == ["form_id", "data"],
      str(mfr527))
check("544 の packet_modal_form_response が 4 フィールド（form_id・has_response_data・data・has_cancel_reason）",
      mfr544 == ["form_id", "has_response_data", "data", "has_cancel_reason"],
      str(mfr544))
ncpu527 = field_names(types527, "packet_network_chunk_publisher_update")
ncpu534 = field_names(data534["types"], "packet_network_chunk_publisher_update")
ncpu544 = field_names(data544["types"], "packet_network_chunk_publisher_update")
check("527 の packet_network_chunk_publisher_update が coordinates・radius のみ（534 と同じ）",
      ncpu527 == ["coordinates", "radius"] and ncpu534 == ["coordinates", "radius"],
      str(ncpu527))
check("544 の packet_network_chunk_publisher_update に saved_chunks がある",
      "saved_chunks" in ncpu544)

# 544 の StartGame 追加 5 フィールドが 527・534 に無い
for f in ["persona_disabled", "custom_skins_disabled", "chat_restriction_level",
          "disable_player_interactions", "client_side_generation"]:
    check(f"start_game に {f} が無い（544 で追加されるもの）",
          f not in sg and f not in sg534
          and f in field_names(data544["types"], "packet_start_game"))

# 567 の StartGame 追加（emote_chat_muted）が 527・534・544 に無い
check("start_game に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg and "emote_chat_muted" not in sg534
      and "emote_chat_muted" not in field_names(data544["types"], "packet_start_game"))

# 582 の StartGame 追加が 527・534・544・554・557・560 に無い
for f in ["created_in_editor", "exported_from_editor", "block_network_ids_are_hashes"]:
    check(f"start_game に {f} が無い（582 で追加されるもの）",
          f not in sg and f not in sg534
          and f not in field_names(data544["types"], "packet_start_game")
          and f not in field_names(data554["types"], "packet_start_game")
          and f not in field_names(data557["types"], "packet_start_game")
          and f not in field_names(data560["types"], "packet_start_game"))

# 589 の StartGame 追加（server_controlled_sound）が 527・534・544・554・557・560 に無い
check("start_game に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg
      and "server_controlled_sound" not in sg534
      and "server_controlled_sound" not in field_names(data544["types"], "packet_start_game")
      and "server_controlled_sound" not in field_names(data554["types"], "packet_start_game")
      and "server_controlled_sound" not in field_names(data557["types"], "packet_start_game")
      and "server_controlled_sound" not in field_names(data560["types"], "packet_start_game"))

# ---------- H. ブロック関連のデータ（1.20.4 のパレット＋1.20.50 の canonical が既存で足りる） ----------
check("ブロックランタイム ID 表 bedrock_block_runtime_ids_1_20_4.bin が存在する",
      (RES / "bedrock_block_runtime_ids_1_20_4.bin").is_file())
check("canonical ブロック状態 canonical_block_states_1_20_50.nbt が存在する",
      (RES / "canonical_block_states_1_20_50.nbt").is_file())
check("1.19.0 名のブロック表は無い（新規不要、1.20.4／1.20.50 を共用）",
      not list(RES.glob("bedrock_block_runtime_ids_1_19_*.bin"))
      and not list(RES.glob("canonical_block_states_1_19_*.nbt")))
check("1.19.0 名のアイテム表が 1 本だけ（bedrock_item_ids_1_19_0.bin）",
      sorted(RES.glob("bedrock_item_ids_1_19_0.bin")) == [RES / "bedrock_item_ids_1_19_0.bin"])

be527 = TMP / "be-527"
check("1.19.0 の canonical ブロック状態が存在する（調査用、ブロックパレットの同一性確認用）",
      (be527 / "canon-bedrock-1.19.0.nbt").is_file())
check("1.19.0 の canonical ブロック状態が 1,224,985 バイト・SHA-1 が cde5786a…と一致",
      (be527 / "canon-bedrock-1.19.0.nbt").is_file()
      and (be527 / "canon-bedrock-1.19.0.nbt").stat().st_size == 1224985
      and sha1(be527 / "canon-bedrock-1.19.0.nbt") == "cde5786a1334d61ba8d10cedb9e37508dc601d84",
      sha1(be527 / "canon-bedrock-1.19.0.nbt") if (be527 / "canon-bedrock-1.19.0.nbt").is_file() else "なし")
check("1.19.0 の required_item_list.json が存在する（調査用、アイテム表の生成元）",
      (be527 / "req-bedrock-1.19.0-required_item_list.json").is_file())
check("1.19.0 の required_item_list.json が 114,179 バイト・1,131 名・SHA-1 が 99152982…と一致",
      (be527 / "req-bedrock-1.19.0-required_item_list.json").is_file()
      and (be527 / "req-bedrock-1.19.0-required_item_list.json").stat().st_size == 114179
      and len(json.loads((be527 / "req-bedrock-1.19.0-required_item_list.json").read_text())) == 1131
      and sha1(be527 / "req-bedrock-1.19.0-required_item_list.json") == "991529829fdcb7538a608b7bf6207a8a995a9a76",
      sha1(be527 / "req-bedrock-1.19.0-required_item_list.json")[:8] if (be527 / "req-bedrock-1.19.0-required_item_list.json").is_file() else "なし")
check("1.19.0 の biome_id_map が存在する（調査用、1.19.10 と同一の裏付け）",
      (be527 / "biomeid-bedrock-1.19.0.json").is_file())
check("1.19.0 の biome_id_map が 85 エントリー・SHA-1 が c82b9c74…と一致",
      (be527 / "biomeid-bedrock-1.19.0.json").is_file()
      and len(json.loads((be527 / "biomeid-bedrock-1.19.0.json").read_text())) == 85
      and sha1(be527 / "biomeid-bedrock-1.19.0.json") == "c82b9c745c0e97bd4d3fa4cbf8337e6dccefb2b7",
      sha1(be527 / "biomeid-bedrock-1.19.0.json")[:8] if (be527 / "biomeid-bedrock-1.19.0.json").is_file() else "なし")
check("1.19.0 の block_id_map が存在する（調査用、1.19.10 と同一の裏付け）",
      (be527 / "blockid-bedrock-1.19.0.json").is_file())
check("1.19.0 の block_id_map が 553 エントリー・SHA-1 が f3855a10…と一致",
      (be527 / "blockid-bedrock-1.19.0.json").is_file()
      and len(json.loads((be527 / "blockid-bedrock-1.19.0.json").read_text())) == 553
      and sha1(be527 / "blockid-bedrock-1.19.0.json") == "f3855a102c698f960020d928bcaef10d25724062",
      sha1(be527 / "blockid-bedrock-1.19.0.json")[:8] if (be527 / "blockid-bedrock-1.19.0.json").is_file() else "なし")

# ---------- I. 変わっていないこと ----------
target_src = (SRC / "BedrockPackets.java").read_text()
proto_javadoc = (SRC / "BedrockProtocol.java").read_text()
check("BedrockProtocol.java の TARGET_VERSION は \"1.20.50\" のまま（527 対応で変えない）",
      'TARGET_VERSION = "1.20.50";' in proto_javadoc)
session_src = (SRC / "BedrockSession.java").read_text()
check("BedrockSession.java の切断メッセージが 291-2193 の範囲になっている",
      "291-2193" in session_src)
server_src = (SRC / "BedrockServer.java").read_text()
check("BedrockServer.java の起動ログが Bedrock 1.7.1〜26.52 / プロトコル 291〜2193 になっている",
      "1.7.1〜26.52" in server_src and "291〜2193" in server_src)
check("BedrockProtocol.java の javadoc が Bedrock 1.7.1〜26.52・プロトコル 291〜2193・protocol.json 62 バージョン分になっている",
      "1.7.1〜26.52" in proto_javadoc and "291〜2193" in proto_javadoc
      and "62 バージョン分" in proto_javadoc)
check("BedrockChunkTranslator.java は BedrockVersion を参照していない（ブロック表の分岐は無い）",
      "BedrockVersion" not in (SRC / "BedrockChunkTranslator.java").read_text())
check("BedrockEntityTranslator.java は BedrockVersion を 2 箇所だけ参照する（431 回の goat_horn_count 分岐・toBedrockData の 3 引数版と既定 V1_20_50）",
      (SRC / "BedrockEntityTranslator.java").read_text().count("BedrockVersion") == 2
      and "hasGoatHornCountKey()" in (SRC / "BedrockEntityTranslator.java").read_text())
check("JavaBackendConnection.java は BedrockVersion を参照していない",
      "BedrockVersion" not in (SRC / "JavaBackendConnection.java").read_text())
check("BedrockProtocol.java は NetherNet 用の分岐（isNetherNetProtocol、2225 で無効化）を持っている",
      "isNetherNetProtocol" in proto_javadoc and "2225" in ver_src
      and "NETHERNET_PROTOCOL = 2225" in ver_src)

# ---------- J. 527 のバージョン分岐 ----------
pkts = (SRC / "BedrockPackets.java").read_text()
check("BedrockPackets.java が hasEditorWorld() を使う（StartGame の editor_world 分岐）",
      "hasEditorWorld()" in pkts)
check("BedrockPackets.java が hasPermissionLevelMapper() を使う（StartGame の permission_level 分岐）",
      "hasPermissionLevelMapper()" in pkts)
check("BedrockPackets.java が hasAddPlayerAbilitiesArray() を使う（AddPlayer の旧形式分岐）",
      "hasAddPlayerAbilitiesArray()" in pkts)
check("BedrockPackets.java が hasAddEntityBodyYaw() を使う（AddEntity の body_yaw 分岐）",
      "hasAddEntityBodyYaw()" in pkts)
check("BedrockPackets.java が encodeStartGame のバージョン付きオーバーロードを持つ",
      "encodeStartGame(" in pkts and "BedrockVersion" in pkts)
check("BedrockPackets.java が encodeAddPlayer のバージョン付きオーバーロードを持つ",
      "encodeAddPlayer(" in pkts)
check("BedrockPackets.java が encodeAddEntity のバージョン付きオーバーロードを持つ",
      "encodeAddEntity(" in pkts)
check("BedrockVersion.java が hasEditorWorld() を定義している（>= 534）",
      "hasEditorWorld()" in ver_src and "protocol >= 534" in ver_src)
check("BedrockVersion.java が hasPermissionLevelMapper() を定義している（>= 534）",
      "hasPermissionLevelMapper()" in ver_src)
check("BedrockVersion.java が hasAddPlayerAbilitiesArray() を定義している（>= 534）",
      "hasAddPlayerAbilitiesArray()" in ver_src)
check("BedrockVersion.java が hasAddEntityBodyYaw() を定義している（>= 534）",
      "hasAddEntityBodyYaw()" in ver_src)

print()
print(f"合計 {len(CHECKS)} 件、失敗 {len(FAILURES)} 件")
if FAILURES:
    print("失敗:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
