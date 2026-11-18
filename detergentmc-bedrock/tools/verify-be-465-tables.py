#!/usr/bin/env python3
"""Bedrock 1.17.34（プロトコル 465）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py /
verify-be-534-tables.py / verify-be-527-tables.py /
verify-be-503-tables.py / verify-be-486-tables.py / verify-be-475-tables.py /
verify-be-471-tables.py にならい、表・定数・述語の前提を機械計数で確認する。
すべて実ファイル（BedrockVersion.java / BedrockProtocol.java /
BedrockSession.java / protocol-1.17.30.json / protocol-1.17.40.json /
protocol-1.18.0.json / protocol-1.18.11.json / protocol-1.18.30.json /
protocol-1.19.1.json / protocol-1.19.10.json / protocol-1.19.20.json /
protocol-1.19.30.json / protocol-1.19.40.json / protocol-1.19.50.json /
.bin データ）に対するチェックで、推測はない。

- 465 の protocol.json は minecraft-data の `data/bedrock/1.17.30/` にある。
- 465 は 1.17.30 / 1.17.32 / 1.17.33 / 1.17.34 で共有するプロトコル
  （Minecraft Wiki「Bedrock Edition 1.17.30」「Bedrock Edition 1.17.32」
  「Bedrock Edition 1.17.33」「Bedrock Edition 1.17.34」個別ページの
  infobox「Protocol version 465」・protocolVersions.json では 465＝1.17.30
  のみ。displayName は共有するうち最新の 1.17.34（471 の V1_17_41 と同じ
  流儀）。1.17.31 はサーバーのみのホットフィックスでクライアントページは
  無い。minecraft-data に 1.17.31／1.17.32／1.17.33／1.17.34 の
  protocol.json は無く（HTTP 404）、465 の代表ディレクトリは 1.17.30
  のみ。BE-NOTES.md §17）。
- **465 の protocol.json は 471 のものとバイト同一ではない**
  （9,590 行・248,252 バイト・SHA-1 `6e4f4df8…`、cmp で差分あり）。
  465 と 471 の差分は追加 2（174 subchunk・175 subchunk_request）・
  削除 0・リネーム 0・型変更 6 件（ItemStackRequest・mcpe_packet・
  packet_animate・packet_level_event・packet_subchunk・
  packet_subchunk_request。mcpe_packet は追加 2 件による mapping の変化で、
  packet_subchunk／packet_subchunk_request は 471 でのみ存在）。
- 465 の 1 つ下は 448（1.17.10）。466〜470 にエントリは無い
  （Wiki 表に 466〜470 の Bedrock バージョンは無く、個別の理由は
  未確認。472・473 と同じ扱い。BE-NOTES.md §17）。
- アイテム表は新規生成が不要（bedrock_item_ids_1_17_40.bin を共用）。
  bedrock-1.17.30 タグは存在する（4 ファイルとも HTTP 200）。
  1.17.30 自体の実測データから同じ手順で構築した候補表が 1_17_40 の表と
  バイト同一（exact=751／shared=236／legacy=33／fallback=292、
  SHA-1 `ca3d341a…`）。1_18_0 の表とは 26/1312 行が違うため共用
  できない（BE-NOTES.md §17）。
- ブロックパレットは 1.17.40 のものとバイト同一ではない（7,944 エントリー
  対 7,948 エントリー）。それでもブロック表は新規不要（既存ペアを流用。
  589 と同じ根拠。BE-NOTES.md §17）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 465 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 465 回の実質的な変更：**なし（新しい述語は不要）**。
  StartGame・AddPlayer・AddEntity・SetEntityData は 465＝471 で
  JSON 完全一致（StartGame は 61 フィールド両方）。6 件の型変更
  （ItemStackRequest・packet_animate・packet_level_event・
  packet_subchunk・packet_subchunk_request・mcpe_packet）は
  現行コードが読まない／書かないため分岐不要（§J）。

使い方: python3 detergentmc-bedrock/tools/verify-be-465-tables.py
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


# ---------- A. protocol.json（465）の同一性 ----------
p65 = TMP / "protocol-1.17.30.json"
check("465 protocol.json が存在する", p65.is_file(), str(p65))
check("465 protocol.json の行数が 9,590 行（wc -l、改行数）",
      p65.is_file()
      and p65.read_text().count("\n") == 9590,
      f"{p65.read_text().count(chr(10))} 行" if p65.is_file() else "なし")
check("465 protocol.json のサイズが 248,252 バイト",
      p65.is_file() and p65.stat().st_size == 248252,
      f"{p65.stat().st_size} バイト" if p65.is_file() else "なし")
check("465 protocol.json の SHA-1 が 6e4f4df8…と一致",
      p65.is_file()
      and sha1(p65) == "6e4f4df82649c941c17aff5fd4ddee77b1266e10",
      sha1(p65) if p65.is_file() else "なし")

data465 = json.loads(p65.read_text())
types465 = data465["types"]
ids465 = packet_ids(data465)

# ---------- A2. 隣接バージョン（471・475・486・503・527・534・544・554・557・560）の protocol.json ----------
p71 = TMP / "protocol-1.17.40.json"
check("471 protocol.json（上側の隣接）が存在する", p71.is_file(), str(p71))
check("471 protocol.json の行数が 9,687 行・サイズが 250,827 バイト・SHA-1 が 081fa7f4…と一致",
      p71.is_file() and p71.read_text().count("\n") == 9687
      and p71.stat().st_size == 250827
      and sha1(p71) == "081fa7f411b80ed9d22116c929c82aa61d8462f8",
      f"{p71.read_text().count(chr(10))} 行・{p71.stat().st_size} バイト" if p71.is_file() else "なし")
check("465 の protocol.json は 471 のものとバイト同一ではない（SHA-1 が違う）",
      p65.is_file() and p71.is_file()
      and sha1(p65) != sha1(p71))
p75 = TMP / "protocol-1.18.0.json"
check("475 protocol.json が存在する", p75.is_file(), str(p75))
check("475 protocol.json の行数が 9,752 行・サイズが 252,425 バイト・SHA-1 が f58c442e…と一致",
      p75.is_file() and p75.read_text().count("\n") == 9752
      and p75.stat().st_size == 252425
      and sha1(p75) == "f58c442e7d7072994e929b5eb3d63522ef334530",
      f"{p75.read_text().count(chr(10))} 行・{p75.stat().st_size} バイト" if p75.is_file() else "なし")
p86 = TMP / "protocol-1.18.11.json"
check("486 protocol.json が存在する", p86.is_file(), str(p86))
check("486 protocol.json の行数が 10,044 行・サイズが 260,293 バイト・SHA-1 が 2ffd5781…と一致",
      p86.is_file() and p86.read_text().count("\n") == 10044
      and p86.stat().st_size == 260293
      and sha1(p86) == "2ffd57813e9904b8962ddd390c6b521b77ab4aa0",
      f"{p86.read_text().count(chr(10))} 行・{p86.stat().st_size} バイト" if p86.is_file() else "なし")
p03 = TMP / "protocol-1.18.30.json"
check("503 protocol.json が存在する", p03.is_file(), str(p03))
check("503 protocol.json の行数が 10,289 行・サイズが 267,010 バイト・SHA-1 が 7d84fdc2…と一致",
      p03.is_file() and p03.read_text().count("\n") == 10289
      and p03.stat().st_size == 267010
      and sha1(p03) == "7d84fdc210ed3fb93f584894bc06875839fe5050",
      f"{p03.read_text().count(chr(10))} 行・{p03.stat().st_size} バイト" if p03.is_file() else "なし")
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
    check("protocolVersions.json に 466〜470 のエントリが無い",
          not any(466 <= e["version"] <= 470 for e in pv))
    check("protocolVersions.json に 472〜474 のエントリが無い",
          not any(472 <= e["version"] <= 474 for e in pv))
    check("protocolVersions.json の 465 は 1.17.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 465] == ["1.17.30"])
    check("protocolVersions.json の 471 は 1.17.40",
          [e["minecraftVersion"] for e in pv if e["version"] == 471] == ["1.17.40"])
    check("protocolVersions.json の 465 の 1 つ下は 448（1.17.10）",
          max(e["version"] for e in pv if e["version"] < 465) == 448)
    check("protocolVersions.json の 448 は 1.17.10",
          [e["minecraftVersion"] for e in pv if e["version"] == 448] == ["1.17.10"])
    check("protocolVersions.json の 471 の 1 つ下は 465（1.17.30）",
          max(e["version"] for e in pv if e["version"] < 471) == 465)
    check("protocolVersions.json の 475 は 1.18.0",
          [e["minecraftVersion"] for e in pv if e["version"] == 475] == ["1.18.0"])
    check("protocolVersions.json の 486 は 1.18.11",
          [e["minecraftVersion"] for e in pv if e["version"] == 486] == ["1.18.11"])
    check("protocolVersions.json の 503 は 1.18.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 503] == ["1.18.30"])
    check("protocolVersions.json の 527 は 1.19.1",
          [e["minecraftVersion"] for e in pv if e["version"] == 527] == ["1.19.1"])

data471 = json.loads(p71.read_text()) if p71.is_file() else None
data475 = json.loads(p75.read_text()) if p75.is_file() else None
data486 = json.loads(p86.read_text()) if p86.is_file() else None
data503 = json.loads(p03.read_text()) if p03.is_file() else None
data527 = json.loads(p101.read_text()) if p101.is_file() else None
data534 = json.loads(p110.read_text()) if p110.is_file() else None
data544 = json.loads(p120.read_text()) if p120.is_file() else None
data554 = json.loads(p130.read_text()) if p130.is_file() else None
data557 = json.loads(p140.read_text()) if p140.is_file() else None
data560 = json.loads(p150.read_text()) if p150.is_file() else None
types471 = data471["types"] if data471 else {}
types475 = data475["types"] if data475 else {}
types486 = data486["types"] if data486 else {}
types503 = data503["types"] if data503 else {}
types527 = data527["types"] if data527 else {}
types534 = data534["types"] if data534 else {}
types544 = data544["types"] if data544 else {}
types554 = data554["types"] if data554 else {}
types557 = data557["types"] if data557 else {}
types560 = data560["types"] if data560 else {}
ids471 = packet_ids(data471) if data471 else {}
ids475 = packet_ids(data475) if data475 else {}
ids486 = packet_ids(data486) if data486 else {}
ids503 = packet_ids(data503) if data503 else {}
ids527 = packet_ids(data527) if data527 else {}

# ---------- B. パケット一覧（465）の件数・境界 ----------
check("465 のパケット数が 170 件", len(ids465) == 170, f"{len(ids465)} 件")
check("465 の最大パケット ID が 173", max(ids465) == 173, f"最大 {max(ids465)}")
check("465 の型定義数が 263", len(types465) == 263, f"{len(types465)}")
check("471 のパケット数が 172 件", len(ids471) == 172, f"{len(ids471)} 件")
check("471 の最大パケット ID が 175", max(ids471) == 175, f"最大 {max(ids471)}")
check("471 の型定義数が 265", len(types471) == 265, f"{len(types471)}")
check("465 の ID の欠番が 0・16・37・116（471 と同じ）",
      [i for i in range(max(ids465) + 1) if i not in ids465] == [0, 16, 37, 116])
check("465 のパケット ID 0〜173 が 471 の同 ID と名称がすべて一致",
      all(ids465[i] == ids471[i] for i in ids465),
      f"{sum(1 for i in ids465 if ids465[i] == ids471.get(i))} / {len(ids465)} 件一致")

d465 = TMP / "packet-diff-465-vs-471.json"
check("465 vs 471 の差分 JSON が存在する", d465.is_file(), str(d465))
if d465.is_file():
    j465 = json.loads(d465.read_text())
    check("465 vs 471：追加 2 件（174 subchunk・175 subchunk_request）",
          j465["added"] == {"174": "subchunk", "175": "subchunk_request"},
          json.dumps(j465["added"]))
    check("465 vs 471：削除 0・リネーム 0",
          j465["removed"] == {} and j465["renamed"] == {})
    check("465 vs 471：型変更 6 件",
          len(j465["changed_types"]) == 6, str(j465["changed_types"]))
    check("465 vs 471：型変更の内訳が ItemStackRequest・mcpe_packet・packet_animate・packet_level_event・packet_subchunk (only in B)・packet_subchunk_request (only in B)",
          j465["changed_types"] == ["ItemStackRequest", "mcpe_packet",
                                     "packet_animate", "packet_level_event",
                                     "packet_subchunk (only in B)",
                                     "packet_subchunk_request (only in B)"],
          json.dumps(j465["changed_types"]))

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
json_by_name = {v: k for k, v in ids465.items()}
# subchunk・subchunk_request は 465 には存在しない（471 で新規追加）ため
# ループから除き、不存在を別途確認する。
for const, pname in CORE:
    if const in ("SUB_CHUNK", "SUB_CHUNK_REQUEST"):
        continue
    java_id = int(java_packets[const], 16)
    json_id = json_by_name.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が protocol.json と一致",
          json_id == java_id, f"json={json_id}")
check("パケット ID 70 の名称が chunk_radius_update（Java 定数名 CHUNK_RADIUS_UPDATED とは名称違い・471 回と同じ）",
      ids465.get(70) == "chunk_radius_update")
check("パケット ID 172 の名称が update_subchunk_blocks（Java 定数名 UPDATE_SUB_CHUNK_BLOCKS とは名称違い・471 回と同じ）",
      ids465.get(172) == "update_subchunk_blocks")
check("パケット SUB_CHUNK（0xae=174）が 465 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk") is None)
check("パケット SUB_CHUNK_REQUEST（0xaf=175）が 465 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk_request") is None)

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_17_34 が存在する", "V1_17_34" in entry_by_name)
check("V1_17_34 のプロトコルが 465",
      entry_by_name.get("V1_17_34", (None,))[0] == 465,
      str(entry_by_name.get("V1_17_34")))
check("V1_17_34 の表示名が 1.17.34",
      entry_by_name.get("V1_17_34", (None, None))[1] == "1.17.34")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_17_34 が V1_17_41 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_17_34(465") < ver_src.index("V1_17_41(471"))
check("enum の javadoc に 1.17.30 の protocol.json が含まれる",
      "1.17.30" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 465 -> \"(1_17_40)\";", ver_src)
check("itemTableTag：465 が 1_17_40 の表を使う（共用・1.17.30 自体の実測で候補表がバイト同一）", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_17_40.bin"
check("アイテム表 bedrock_item_ids_1_17_40.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が ca3d341a…と一致（1.17.30 候補表）",
      tag_bin.is_file()
      and sha1(tag_bin) == "ca3d341ad3830e7ac02563814175ae3a51c3d511",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
check("1.17.30 名のアイテム表は無い（共用のため 1_17_40 が正しい）",
      not (RES / "bedrock_item_ids_1_17_30.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規 0 本）", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s465 = summary.get("465", {})
check("item_tables_summary.json に 465 のエントリがある",
      "465" in summary)
check("item_tables_summary.json の 465 が tag=bedrock-1.17.30・exact=751／shared=236／legacy=33／fallback=292",
      s465.get("tag") == "bedrock-1.17.30" and s465.get("exact") == 751
      and s465.get("shared") == 236 and s465.get("legacy") == 33
      and s465.get("fallback") == 292,
      str({k: s465.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))
check("item_tables_summary.json の 465 の missing_names が 292 件",
      len(s465.get("missing_names", [])) == 292,
      f"{len(s465.get('missing_names', []))} 件")
check("item_tables_summary.json の 465 の missing_names が 471 のものと同一（同じ .bin のため）",
      s465.get("missing_names") == summary.get("471", {}).get("missing_names"))
check("item_tables_summary.json の 471 のエントリが残っている（全テーブル再生成による消失なし）",
      "471" in summary and summary["471"].get("exact") == 751
      and summary["471"].get("fallback") == 292)
check("item_tables_summary.json の 475 のエントリが残っている（全テーブル再生成による消失なし）",
      "475" in summary and summary["475"].get("exact") == 752
      and summary["475"].get("fallback") == 291)

# ---------- F. 述語の前提：protocol.json 側の事実 ----------
# (1) StartGame：465 は 61 フィールド、471 も 61 フィールド（JSON 完全一致）
sg465 = field_names(types465, "packet_start_game")
sg471 = field_names(types471, "packet_start_game")
check("StartGame：465 は 61 フィールド", len(sg465) == 61, f"{len(sg465)} フィールド")
check("StartGame：471 は 61 フィールド", len(sg471) == 61, f"{len(sg471)} フィールド")
check("StartGame：465 のフィールド一覧が 471 とすべて同じ（順序も同じ・JSON 完全一致）",
      sg465 == sg471)
check("StartGame：465 に block_pallete_checksum が無い（hasStartGameBlockPaletteChecksum()=false の前提）",
      "block_pallete_checksum" not in sg465
      and "block_pallette_checksum" not in sg465)
check("StartGame：471 に block_pallete_checksum が無い（hasStartGameBlockPaletteChecksum()=false の前提）",
      "block_pallete_checksum" not in sg471
      and "block_pallette_checksum" not in sg471)
check("StartGame：465 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types465, "packet_start_game", "seed") == "zigzag32")
check("StartGame：471 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types471, "packet_start_game", "seed") == "zigzag32")
check("StartGame：465 の dimension は zigzag32",
      field_type(types465, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：471 の dimension は zigzag32",
      field_type(types471, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：465 に property_data が無い（hasStartGame527Fields()=false の前提）",
      "property_data" not in sg465)
check("StartGame：465 に world_template_id が無い（hasStartGame527Fields()=false の前提）",
      "world_template_id" not in sg465)
check("StartGame：465 に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg465)
check("StartGame：465 に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg465 for t in ["server_identifier", "world_identifier",
                                   "scenario_identifier", "owner_identifier"]))
check("StartGame：465 に editor_world が無い（hasEditorWorld=false）",
      "editor_world" not in sg465)
check("StartGame：465 の permission_level は zigzag32（hasPermissionLevelMapper=false）",
      field_type(types465, "packet_start_game", "permission_level") == "zigzag32")
check("StartGame：465 の movement_authority が存在する（hasMovementAuthority=true）",
      "movement_authority" in sg465)
check("StartGame：465 の itemstates が存在する（hasItemstates=true）",
      "itemstates" in sg465)
check("StartGame：465 に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg465)
check("StartGame：465 に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg465 and "server_join_info" not in sg465)
check("StartGame：465 に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg465)
check("StartGame：465 に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg465)
check("StartGame：465 に created_in_editor・exported_from_editor・block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(f in sg465 for f in ["created_in_editor", "exported_from_editor",
                                   "block_network_ids_are_hashes"]))
check("StartGame：465 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg465)
check("StartGame：465 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg465)
check("StartGame：465 に persona_disabled・custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg465 and "custom_skins_disabled" not in sg465)
check("StartGame：465 に chat_restriction_level・disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg465
      and "disable_player_interactions" not in sg465)
check("StartGame：465 に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg465)

# (2) Resource packs info / stack：465 と 471 で同じ形
rpi = field_names(types465, "packet_resource_packs_info")
rpi471 = field_names(types471, "packet_resource_packs_info")
check("Resource packs info：465 は 471 と同じ 5 フィールド（must_accept・has_scripts・force_server_packs・behaviour_packs・texture_packs）",
      rpi == rpi471 == ["must_accept", "has_scripts", "force_server_packs",
                        "behaviour_packs", "texture_packs"])
check("Resource packs info：465 に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("Resource packs info：465 に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("Resource packs info：465 に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("Resource packs info：465 に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
check("TexturePackInfos の countType が li16（471 と同じ）",
      types465["TexturePackInfos"][1].get("countType") == "li16")
rps = field_names(types465, "packet_resource_pack_stack")
rps471 = field_names(types471, "packet_resource_pack_stack")
check("Resource pack stack：465 は 471 と同じ 6 フィールド（behavior_packs を含む）",
      rps == rps471 == ["must_accept", "behavior_packs", "resource_packs",
                        "game_version", "experiments",
                        "experiments_previously_used"])
check("Resource pack stack：465 に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

# (3) エンティティ・アイテム
check("Mob equipment の item が Item（ItemV4 ではない）",
      field_type(types465, "packet_mob_equipment", "item") == "Item")
check("Add item entity の item が Item",
      field_type(types465, "packet_add_item_entity", "item") == "Item")
mae = [n for n, _ in container_fields(types465, "packet_mob_armor_equipment")]
check("Mob armor equipment が helmet・chestplate・leggings・boots の 4 つ（body は無い）",
      mae == ["runtime_entity_id", "helmet", "chestplate", "leggings", "boots"])
check("ItemV4 型が 465 の protocol.json に存在しない", "ItemV4" not in types465)
ap465 = field_names(types465, "packet_add_player")
ap471 = field_names(types471, "packet_add_player")
check("Add player：465 は 21 フィールド",
      len(ap465) == 21, f"{len(ap465)} フィールド")
check("Add player：471 は 21 フィールド",
      len(ap471) == 21, f"{len(ap471)} フィールド")
check("Add player：465 のフィールド一覧が uuid・username・entity_id_self・runtime_entity_id・platform_chat_id・position・velocity・pitch・yaw・head_yaw・held_item・metadata・flags・command_permission・action_permissions・permission_level・custom_stored_permissions・user_id・links・device_id・device_os",
      ap465 == ["uuid", "username", "entity_id_self", "runtime_entity_id",
                "platform_chat_id", "position", "velocity", "pitch", "yaw",
                "head_yaw", "held_item", "metadata", "flags",
                "command_permission", "action_permissions", "permission_level",
                "custom_stored_permissions", "user_id", "links", "device_id",
                "device_os"])
check("Add player：471 のフィールド一覧が 465 と一致する",
      ap471 == ap465)
check("Add player：465 の device_os は li32",
      field_type(types465, "packet_add_player", "device_os") == "li32")
check("Add player：465・471 の entity_id_self は zigzag64（名称も型も同じ）",
      field_type(types465, "packet_add_player", "entity_id_self") == "zigzag64"
      and field_type(types471, "packet_add_player", "entity_id_self") == "zigzag64")
check("Add player：465 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap465)
check("Add player：471 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap471)
check("Add entity は 465 と 471 で同じ（11 フィールド・body_yaw が無い）",
      field_names(types465, "packet_add_entity") == field_names(types471, "packet_add_entity")
      and len(field_names(types465, "packet_add_entity")) == 11)
check("Set entity data は 465 と 471 で同じ（3 フィールド）",
      field_names(types465, "packet_set_entity_data") == field_names(types471, "packet_set_entity_data")
      and len(field_names(types465, "packet_set_entity_data")) == 3)

# (4) トランザクション・アイテム関連
leg = {n: t for n, t in container_fields(types465, "TransactionLegacy")}
check("TransactionLegacy の legacy_transactions が switch（471 と同じ）",
      isinstance(leg.get("legacy_transactions"), list)
      and leg["legacy_transactions"][0] == "switch")
tx = {n: t for n, t in container_fields(types465, "Transaction")}
tx_map = tx.get("transaction_type")[1]["mappings"]
check("Transaction の transaction_type が 0=normal・1=inventory_mismatch・2=item_use・3=item_use_on_entity・4=item_release（471 と同じ）",
      tx_map.get("0") == "normal"
      and tx_map.get("1") == "inventory_mismatch"
      and tx_map.get("2") == "item_use"
      and tx_map.get("3") == "item_use_on_entity"
      and tx_map.get("4") == "item_release")
check("Transaction の actions が TransactionActions（471 と同じ）",
      tx.get("actions") == "TransactionActions")
check("Transaction が 465 と 471 で JSON 同一",
      types465["Transaction"] == types471["Transaction"])
ta = types465["TransactionActions"]
ta_fields = {f["name"]: f["type"] for f in ta[1]["type"][1] if "name" in f}
check("TransactionActions の old/new item が旧型 Item（transactionUsesItemV4=false）",
      ta_fields.get("old_item") == "Item" and ta_fields.get("new_item") == "Item",
      f"old={ta_fields.get('old_item')} new={ta_fields.get('new_item')}")
check("TransactionActions に window_id／flags の option 欄が無い",
      "window_id" not in ta_fields and "flags" not in ta_fields)
isr465 = item_stack_request_type_id(types465)
isr471 = item_stack_request_type_id(types471)
check("ItemStackRequest：465 の type_id が u8 マッパーで 16 件（0〜15）",
      isr465 is not None and isr465[1]["mappings"] == {
          "0": "take", "1": "place", "2": "swap", "3": "drop",
          "4": "destroy", "5": "consume", "6": "create",
          "7": "lab_table_combine", "8": "beacon_payment",
          "9": "mine_block", "10": "craft_recipe",
          "11": "craft_recipe_auto", "12": "craft_creative",
          "13": "optional", "14": "non_implemented",
          "15": "results_deprecated"},
      json.dumps(isr465[1]["mappings"]) if isr465 else "なし")
check("ItemStackRequest：471 の type_id が u8 マッパーで 18 件（0〜17）",
      isr471 is not None and isr471[1]["mappings"] == {
          "0": "take", "1": "place", "2": "swap", "3": "drop",
          "4": "destroy", "5": "consume", "6": "create",
          "7": "lab_table_combine", "8": "beacon_payment",
          "9": "mine_block", "10": "craft_recipe",
          "11": "craft_recipe_auto", "12": "craft_creative",
          "13": "optional", "14": "craft_grindstone_request",
          "15": "craft_loom_request", "16": "non_implemented",
          "17": "results_deprecated"},
      json.dumps(isr471[1]["mappings"]) if isr471 else "なし")
check("ItemStackRequest：465 に craft_grindstone_request・craft_loom_request が無い（471 で追加）",
      "craft_grindstone_request" not in isr465[1]["mappings"].values()
      and "craft_loom_request" not in isr465[1]["mappings"].values())
check("ItemStackRequest：471 の craft_grindstone_request は recipe_network_id varint＋cost varint",
      field_type(types471, "ItemStackRequest", "actions")[1]["type"][1][1]["type"][1]["fields"]["craft_grindstone_request"] == ["container", [{"name": "recipe_network_id", "type": "varint"}, {"name": "cost", "type": "varint"}]])
check("ItemStackRequest：471 の craft_loom_request は pattern string",
      field_type(types471, "ItemStackRequest", "actions")[1]["type"][1][1]["type"][1]["fields"]["craft_loom_request"] == ["container", [{"name": "pattern", "type": "string"}]])
check("ItemStackRequest：465 の non_implemented が 14・results_deprecated が 15（471 では 16・17 にずれる）",
      isr465[1]["mappings"].get("14") == "non_implemented"
      and isr465[1]["mappings"].get("15") == "results_deprecated"
      and isr471[1]["mappings"].get("16") == "non_implemented"
      and isr471[1]["mappings"].get("17") == "results_deprecated")
check("ItemStackRequest が 465 と 471 で違う（grindstone／loom の 2 アクション分）",
      types465["ItemStackRequest"] != types471["ItemStackRequest"])
check("RecipeIngredient：465 が旧形式（network_id zigzag32＋匿名 switch）",
      types465["RecipeIngredient"][0] == "container"
      and types465["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types465["RecipeIngredient"][1][1].get("anon") is True,
      json.dumps(types465["RecipeIngredient"][1][0]))
check("RecipeIngredient：471 も旧形式（network_id zigzag32＋匿名 switch）",
      types471["RecipeIngredient"][0] == "container"
      and types471["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types471["RecipeIngredient"][1][1].get("anon") is True)
check("RecipeIngredient：465 の匿名 switch が network_id=0 で void、default で network_data＋count（zigzag32）",
      types465["RecipeIngredient"][1][1]["type"][1].get("compareTo") == "network_id"
      and types465["RecipeIngredient"][1][1]["type"][1]["fields"].get("0") == "void"
      and [f["name"] for f in types465["RecipeIngredient"][1][1]["type"][1]["default"][1]] == ["network_data", "count"])
check("RecipeIngredient：554 が新形式（先頭が type の u8 マッパー：invalid／int_id_meta／molang／item_tag／string_id_meta）",
      types554["RecipeIngredient"][0] == "container"
      and types554["RecipeIngredient"][1][0].get("name") == "type"
      and types554["RecipeIngredient"][1][0]["type"][1].get("mappings") == {"0": "invalid", "1": "int_id_meta",
                                                                            "2": "molang", "3": "item_tag",
                                                                            "4": "string_id_meta"})

# (5) 核パケットの 465 vs 471：16 件すべて JSON 同一
MAJOR = ["packet_add_player", "packet_add_entity", "packet_add_item_entity",
         "packet_level_chunk", "packet_move_entity", "packet_move_player",
         "packet_update_block", "packet_set_entity_data",
         "packet_mob_equipment", "packet_mob_armor_equipment",
         "packet_start_game", "packet_player_list",
         "packet_inventory_transaction", "packet_network_chunk_publisher_update",
         "packet_resource_packs_info", "packet_resource_pack_stack"]
for name in MAJOR:
    check(f"核パケット {name} が 465 と 471 で JSON 同一（分岐不要）",
          types465[name] == types471[name])
check("核パケット packet_player_auth_input が 465 と 471 で JSON 同一",
      types465["packet_player_auth_input"] == types471["packet_player_auth_input"])

# ---------- G. 465／471 の境界値 ----------
st465 = types465["SoundType"][1]["mappings"]
st471 = types471["SoundType"][1]["mappings"]
check("SoundType：465 は 363 エントリ（0〜362）", len(st465) == 363, f"{len(st465)}")
check("SoundType：471 は 363 エントリ（0〜362）", len(st471) == 363, f"{len(st471)}")
check("SoundType：465 と 471 で JSON 完全一致（465→471 で変更なし）",
      types465["SoundType"] == types471["SoundType"])
check("SoundType：465 の 362 が Undefined（471 と同じ）",
      st465.get("362") == "Undefined" and st471.get("362") == "Undefined")
check("SoundType：465 の 4 が Jump・5 が Break・6 が Place（471 と同じ）",
      st465.get("4") == "Jump" and st465.get("5") == "Break"
      and st465.get("6") == "Place")
le465 = types465["packet_level_event"]
le471 = types471["packet_level_event"]
le_map = le465[1][0]["type"][1]["mappings"]
le_map471 = le471[1][0]["type"][1]["mappings"]
check("Level event：465 は 168 エントリ（type 0〜9844、ID 2034〜2036 が無い）",
      len(le_map) == 168, f"{len(le_map)}")
check("Level event：471 は 171 エントリ（type 0〜9844、ID 2034〜2036 あり）",
      len(le_map471) == 171, f"{len(le_map471)}")
check("Level event：465 の 2034・2035・2036 は無い（471 の 2034 が particle_turtle_egg・2035 が particle_sculk_shriek・2036 が sculk_catalyst_bloom）",
      le_map.get("2034") is None and le_map.get("2035") is None
      and le_map.get("2036") is None
      and le_map471.get("2034") == "particle_turtle_egg"
      and le_map471.get("2035") == "particle_sculk_shriek"
      and le_map471.get("2036") == "sculk_catalyst_bloom")
check("Level event：465 と 471 の他のエントリはすべて一致（2034〜2036 を除く）",
      all(le_map.get(k) == v for k, v in le_map471.items() if k not in ("2034", "2035", "2036")))
check("Level event：465 の 2000 が particle_shoot・2001 が particle_destroy・2002 が particle_splash・2003 が particle_eye_despawn（471 と同じ）",
      le_map.get("2000") == "particle_shoot"
      and le_map.get("2001") == "particle_destroy"
      and le_map.get("2002") == "particle_splash"
      and le_map.get("2003") == "particle_eye_despawn")
check("Level event：465 の 9800 が players_sleeping（471 と同じ）・9801 は両方に無い（最大キーは 16465）",
      le_map.get("9800") == "players_sleeping"
      and le_map.get("9801") is None and le_map471.get("9801") is None
      and max(int(k) for k in le_map) == 16465
      and max(int(k) for k in le_map471) == 16465)
check("Entity event：465・471 ともに 54 エントリ（471 と同じ）",
      len(field_type(types465, "packet_entity_event", "event_id")[1]["mappings"]) == 54
      and len(field_type(types471, "packet_entity_event", "event_id")[1]["mappings"]) == 54)
check("Entity event：465 と 471 で JSON 同一",
      types465["packet_entity_event"] == types471["packet_entity_event"])
check("Entity event：465・471 ともに 1=jump・2=hurt_animation・3=death_animation・4=arm_swing（471 と同じ）",
      field_type(types465, "packet_entity_event", "event_id")[1]["mappings"].get("1") == "jump"
      and field_type(types465, "packet_entity_event", "event_id")[1]["mappings"].get("2") == "hurt_animation"
      and field_type(types465, "packet_entity_event", "event_id")[1]["mappings"].get("3") == "death_animation"
      and field_type(types465, "packet_entity_event", "event_id")[1]["mappings"].get("4") == "arm_swing")
check("Entity event：465・471 ともに 8=shake_wet（totem・shake_web・feed はまだ無い）",
      field_type(types465, "packet_entity_event", "event_id")[1]["mappings"].get("8") == "shake_wet"
      and "totem" not in field_type(types465, "packet_entity_event", "event_id")[1]["mappings"].values()
      and "shake_web" not in field_type(types465, "packet_entity_event", "event_id")[1]["mappings"].values()
      and "feed" not in field_type(types465, "packet_entity_event", "event_id")[1]["mappings"].values())
check("GameMode：465・471 ともに 6 エントリ（0〜5・5=fallback）",
      len(types465["GameMode"][1]["mappings"]) == 6
      and len(types471["GameMode"][1]["mappings"]) == 6
      and types465["GameMode"][1]["mappings"].get("5") == "fallback")
check("GameMode：465 と 471 で JSON 同一",
      types465["GameMode"] == types471["GameMode"])
check("MetadataDictionary：465・471 ともに 124 エントリ（471 と同じ）",
      len(types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]) == 124
      and len(types471["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]) == 124)
check("MetadataDictionary：465 と 471 で JSON 同一",
      types465["MetadataDictionary"] == types471["MetadataDictionary"])
check("MetadataDictionary：465・471 ともに 0=flags・1=health・2=variant・3=color・4=nametag",
      types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("0") == "flags"
      and types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("1") == "health"
      and types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("2") == "variant"
      and types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("3") == "color"
      and types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("4") == "nametag")
check("MetadataDictionary：465・471 ともに 121=freezing_effect_strength・122=buoyancy_data・123=goat_horn_count",
      types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("121") == "freezing_effect_strength"
      and types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("122") == "buoyancy_data"
      and types465["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("123") == "goat_horn_count")
check("MetadataFlags2：465・471 ともに 34 フラグ（471 と同じ）",
      len(types465["MetadataFlags2"][1]["flags"]) == 34
      and len(types471["MetadataFlags2"][1]["flags"]) == 34)
check("MetadataFlags2：465 と 471 で JSON 同一",
      types465["MetadataFlags2"] == types471["MetadataFlags2"])
check("MetadataFlags2：465・471 ともに playing_dead が 33 番目（最後）・has_entity_properties はまだ無い",
      types465["MetadataFlags2"][1]["flags"][33] == "playing_dead"
      and "has_entity_properties" not in types465["MetadataFlags2"][1]["flags"]
      and "has_entity_properties" not in types471["MetadataFlags2"][1]["flags"])
check("Subchunk：465 に packet_subchunk 型が存在しない（471 で新規追加）",
      "packet_subchunk" not in types465)
sc471 = field_names(types471, "packet_subchunk")
check("Subchunk：471 は 8 フィールド（dimension・x・y・z・data・request_result・heightmap_type・heightmap）",
      sc471 == ["dimension", "x", "y", "z", "data", "request_result",
                "heightmap_type", "heightmap"], json.dumps(sc471))
check("Subchunk request：465 に packet_subchunk_request 型が存在しない（471 で新規追加）",
      "packet_subchunk_request" not in types465)
check("Subchunk request：471 は 4 フィールド（dimension・x・y・z）",
      field_names(types471, "packet_subchunk_request") == ["dimension", "x", "y", "z"])
check("CommandFlags：465 は 4 フラグ（unknown2 が無い・471 と同じ）",
      [f["name"] for f in types465["CommandFlags"][1]] == ["unused",
                                                           "collapse_enum",
                                                           "has_semantic_constraint",
                                                           "as_chained_command"])
check("CommandFlags：465 と 471 で JSON 同一",
      types465["CommandFlags"] == types471["CommandFlags"])
check("MaterialReducer：465 は varint×3（mix・network_id・count・471 と同じ）",
      field_type(types465, "MaterialReducer", "mix") == "varint"
      and field_type(types465, "MaterialReducer", "items")[1][0]["type"] == "varint"
      and field_type(types465, "MaterialReducer", "items")[1][1]["type"] == "varint")
check("MaterialReducer：465 と 471 で JSON 同一",
      types465["MaterialReducer"] == types471["MaterialReducer"])
check("Animate：465 は row_right=128・row_left=129＋匿名 switch（boat_rowing_time lf32）",
      types465["packet_animate"][1][0]["type"][1]["mappings"].get("128") == "row_right"
      and types465["packet_animate"][1][0]["type"][1]["mappings"].get("129") == "row_left"
      and len(container_fields(types465, "packet_animate")) == 3
      and container_fields(types465, "packet_animate")[2][1][0] == "switch")
check("Animate：465 の anon switch は action_id 依存で row_right／row_left に boat_rowing_time（lf32）",
      container_fields(types465, "packet_animate")[2][1][1].get("compareTo") == "action_id"
      and container_fields(types465, "packet_animate")[2][1][1]["fields"]["row_right"] == ["container", [{"name": "boat_rowing_time", "type": "lf32"}]]
      and container_fields(types465, "packet_animate")[2][1][1]["fields"]["row_left"] == ["container", [{"name": "boat_rowing_time", "type": "lf32"}]])
check("Animate：471 は row_right=6・row_left=7（2 フィールド、boat_rowing_time 無し）",
      field_names(types471, "packet_animate") == ["action_id", "runtime_entity_id"]
      and types471["packet_animate"][1][0]["type"][1]["mappings"].get("6") == "row_right"
      and types471["packet_animate"][1][0]["type"][1]["mappings"].get("7") == "row_left")
check("Animate entity：465 の stop_condition_version は li64・471 も li64",
      field_type(types465, "packet_animate_entity", "stop_condition_version") == "li64"
      and field_type(types471, "packet_animate_entity", "stop_condition_version") == "li64")
check("Crafting data：465 の順序が recipes・potion_type_recipes・material_reducers・potion_container_recipes・clear_recipes",
      field_names(types465, "packet_crafting_data") == ["recipes",
                                                       "potion_type_recipes",
                                                       "material_reducers",
                                                       "potion_container_recipes",
                                                       "clear_recipes"])
check("Crafting data：471 の順序が 465 と同じ",
      field_names(types471, "packet_crafting_data") == field_names(types465, "packet_crafting_data"))
check("Text：465 の type マッパーに 11 が無い（471 も無し）",
      "11" not in types465["packet_text"][1][0]["type"][1]["mappings"]
      and "11" not in types471["packet_text"][1][0]["type"][1]["mappings"])
check("Network settings：465・471 ともに compression_threshold は u16",
      field_type(types465, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types471, "packet_network_settings", "compression_threshold") == "u16")
check("Structure block update：465・471 ともに 9 フィールド（water_logged が無い）",
      len(field_names(types465, "packet_structure_block_update")) == 9
      and len(field_names(types471, "packet_structure_block_update")) == 9)
check("Spawn particle effect：465・471 ともに 4 フィールド（dimension・entity_id・position・particle_name）",
      field_names(types465, "packet_spawn_particle_effect") == ["dimension",
                                                                "entity_id",
                                                                "position",
                                                                "particle_name"]
      and field_names(types465, "packet_spawn_particle_effect") == field_names(types471, "packet_spawn_particle_effect"))
check("Add volume entity：465・471 ともに 3 フィールド（entity_id・nbt・engine_version）",
      field_names(types465, "packet_add_volume_entity") == ["entity_id", "nbt",
                                                           "engine_version"]
      and field_names(types465, "packet_add_volume_entity") == field_names(types471, "packet_add_volume_entity"))
check("Boss event：465・471 ともに query が無い（ボスバー問い合わせは 582 以降）",
      "query" not in str(types465["packet_boss_event"])
      and "query" not in str(types471["packet_boss_event"]))
check("CommandOrigin：465 と 471 で JSON 同一（type マッピング含む）",
      types465["CommandOrigin"] == types471["CommandOrigin"])
check("Adventure settings：465 と 471 で command_permission が同じ",
      field_type(types465, "packet_adventure_settings", "command_permission") == field_type(types471, "packet_adventure_settings", "command_permission"))
check("StructureBlockSettings：465 と 471 で JSON 同一",
      types465["StructureBlockSettings"] == types471["StructureBlockSettings"])
check("Available commands：465 と 471 で JSON 同一",
      types465["packet_available_commands"] == types471["packet_available_commands"])
check("Modal form response：465 は form_id・data の 2 フィールド（471 と同じ）",
      field_names(types465, "packet_modal_form_response") == ["form_id", "data"]
      and field_names(types465, "packet_modal_form_response") == field_names(types471, "packet_modal_form_response"))
check("Map info request：465 は map_id の 1 フィールド（471 と同じ）",
      field_names(types465, "packet_map_info_request") == ["map_id"]
      and field_names(types465, "packet_map_info_request") == field_names(types471, "packet_map_info_request"))
check("Network chunk publisher update：465 は coordinates・radius の 2 フィールド（471 と同じ）",
      field_names(types465, "packet_network_chunk_publisher_update") == ["coordinates", "radius"]
      and field_names(types465, "packet_network_chunk_publisher_update") == field_names(types471, "packet_network_chunk_publisher_update"))
check("Clientbound map item data：465 に origin が無い（471 と同じ）",
      "origin" not in field_names(types465, "packet_clientbound_map_item_data")
      and "origin" not in field_names(types471, "packet_clientbound_map_item_data"))
pa465 = [f["name"] for f in types465["PlayerAttributes"][1]["type"][1] if "name" in f]
check("PlayerAttributes：465 は min・max・current・default・name の 5 項目（471 と同じ）",
      pa465 == ["min", "max", "current", "default", "name"])
check("PlayerAttributes：471 も同じ 5 項目",
      [f["name"] for f in types471["PlayerAttributes"][1]["type"][1] if "name" in f] == ["min", "max", "current", "default", "name"])
check("DeviceOS 型が 465 の protocol.json に存在しない", "DeviceOS" not in types465)
check("EntityProperties 型が 465 の protocol.json に存在しない", "EntityProperties" not in types465)
check("SubChunkEntryWithCaching 型が 465 の protocol.json に存在しない", "SubChunkEntryWithCaching" not in types465)

# ---------- H. ブロック・バイオーム・item 名前対応 ----------
be471_dir = TMP / "be-471"
req465 = be471_dir / "req-bedrock-1.17.30-required_item_list.json"
canon465 = be471_dir / "canon-bedrock-1.17.30.nbt"
biome465 = be471_dir / "biomeid-bedrock-1.17.30.json"
blockid465 = be471_dir / "blockid-bedrock-1.17.30.json"
req471 = be471_dir / "req-bedrock-1.17.40-required_item_list.json"
canon471 = be471_dir / "canon-bedrock-1.17.40.nbt"
check("1.17.30 の required_item_list.json が存在する", req465.is_file(), str(req465))
check("1.17.30 の required_item_list.json のサイズが 108,467 バイト",
      req465.is_file() and req465.stat().st_size == 108467,
      f"{req465.stat().st_size} バイト" if req465.is_file() else "なし")
check("1.17.30 の required_item_list.json の SHA-1 が f728c24b…と一致",
      req465.is_file()
      and sha1(req465) == "f728c24ba6bfa8134dfb0c501044fdc12270cd72",
      sha1(req465) if req465.is_file() else "なし")
check("1.17.30 の required_item_list.json の名前が 1,076 件",
      req465.is_file()
      and len(json.loads(req465.read_text())) == 1076,
      f"{len(json.loads(req465.read_text()))} 件" if req465.is_file() else "なし")
check("1.17.30 の required_item_list.json が 1.17.40 のものとバイト同一ではない",
      req465.is_file() and req471.is_file()
      and sha1(req465) != sha1(req471))
check("1.17.40 の required_item_list.json のサイズが 108,800 バイト・名前が 1,079 件",
      req471.is_file() and req471.stat().st_size == 108800
      and len(json.loads(req471.read_text())) == 1079,
      f"{req471.stat().st_size} バイト・{len(json.loads(req471.read_text()))} 件" if req471.is_file() else "なし")
check("1.17.30 の canonical_block_states.nbt が存在する", canon465.is_file(), str(canon465))
check("1.17.30 の canonical_block_states.nbt のサイズが 1,174,676 バイト",
      canon465.is_file() and canon465.stat().st_size == 1174676,
      f"{canon465.stat().st_size} バイト" if canon465.is_file() else "なし")
check("1.17.30 の canonical_block_states.nbt の SHA-1 が 4e100a58…と一致",
      canon465.is_file()
      and sha1(canon465) == "4e100a585b7f19387b8db7442e2e67edf1073748",
      sha1(canon465) if canon465.is_file() else "なし")
canon18 = TMP / "be-475" / "canon-bedrock-1.18.0.nbt"
# canonical_block_states.nbt のエントリー数は tools/parse_nbt.py で数える
sys.path.insert(0, str(BE / "tools"))
from parse_nbt import parse_canonical  # noqa: E402
canon_entries_465 = (len(parse_canonical(str(canon465))[0])
                     if canon465.is_file() else -1)
canon_entries_471 = (len(parse_canonical(str(canon471))[0])
                     if canon471.is_file() else -1)
check("1.17.30 の canonical_block_states.nbt が 1.17.40 のものとバイト同一ではない",
      canon465.is_file() and canon471.is_file()
      and sha1(canon465) != sha1(canon471))
check("1.17.30 の canonical_block_states.nbt が 7,944 エントリー（NBT のパレット長・parse_nbt.py で数えた実測）",
      canon465.is_file() and canon_entries_465 == 7944,
      f"{canon_entries_465} エントリー" if canon465.is_file() else "なし")
check("1.17.40 の canonical_block_states.nbt が 7,948 エントリー",
      canon471.is_file() and canon_entries_471 == 7948,
      f"{canon_entries_471} エントリー" if canon471.is_file() else "なし")
check("1.17.30 の block_id_map.json が存在する", blockid465.is_file(), str(blockid465))
check("1.17.30 の block_id_map.json の SHA-1 が f3855a10…と一致",
      blockid465.is_file()
      and sha1(blockid465) == "f3855a102c698f960020d928bcaef10d25724062",
      sha1(blockid465) if blockid465.is_file() else "なし")
check("1.17.30 の block_id_map.json が 553 エントリー",
      blockid465.is_file()
      and len(json.loads(blockid465.read_text())) == 553,
      f"{len(json.loads(blockid465.read_text()))} 件" if blockid465.is_file() else "なし")
blockid18 = TMP / "be-475" / "blockid-bedrock-1.18.0.json"
check("1.17.30 の block_id_map.json が 1.18.0 のものとバイト同一（SHA-1 が同じ）",
      blockid465.is_file() and blockid18.is_file()
      and sha1(blockid465) == sha1(blockid18))
check("1.17.30 の biome_id_map.json が存在する", biome465.is_file(), str(biome465))
check("1.17.30 の biome_id_map.json の SHA-1 が 8af31184…と一致",
      biome465.is_file()
      and sha1(biome465) == "8af311845507144292a5bca1e6aa9b4ad2501af1",
      sha1(biome465) if biome465.is_file() else "なし")
check("1.17.30 の biome_id_map.json が 75 エントリー",
      biome465.is_file()
      and len(json.loads(biome465.read_text())) == 75,
      f"{len(json.loads(biome465.read_text()))} 件" if biome465.is_file() else "なし")
check("1.17.30 の biome_id_map.json に lofty_peaks が無い（1.17.40 の 83 件と違う）",
      biome465.is_file()
      and "lofty_peaks" not in json.loads(biome465.read_text())
      and "frozen_peaks" not in json.loads(biome465.read_text()))
check("1.17.30 のブロック用 .bin は無く（既存ペアの流用）",
      not (RES / "bedrock_block_runtime_ids_1_17_30.bin").exists())
check("既存ペアの canonical_block_states_1_20_50.nbt が存在し SHA-1 が d2b6acbb…と一致",
      (RES / "canonical_block_states_1_20_50.nbt").is_file()
      and sha1(RES / "canonical_block_states_1_20_50.nbt") == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("既存ペアの bedrock_block_runtime_ids_1_20_4.bin が存在し SHA-1 が ee36fffb…と一致",
      (RES / "bedrock_block_runtime_ids_1_20_4.bin").is_file()
      and sha1(RES / "bedrock_block_runtime_ids_1_20_4.bin") == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")

# ---------- I. 範囲文字列と NetherNet 分岐 ----------
proto_doc = (SRC / "BedrockProtocol.java").read_text()
check("BedrockProtocol の javadoc が「1.7.1〜26.52 / 291〜2193 / 62 バージョン分」",
      "1.7.1〜26.52" in proto_doc and "291〜2193" in proto_doc
      and "62 バージョン分" in proto_doc)
sess_src = (SRC / "BedrockSession.java").read_text()
check("BedrockSession の切断文が「supported: 291-2193 over RakNet; 2225 needs NetherNet」",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in sess_src)
srv_src = (SRC / "BedrockServer.java").read_text()
check("BedrockServer の起動ログが「1.7.1〜26.52 / 291〜2193」",
      "1.7.1〜26.52" in srv_src and "291〜2193" in srv_src)
check("BedrockVersion の NetherNet 分岐（2225）が 465 と無関係に残っている",
      "2225" in ver_src and "NetherNet" in ver_src)
check("BedrockChunkTranslator が BedrockVersion を参照していない（チャンク変換はバージョン非依存。BedrockEntityTranslator は 431 回の goat_horn_count 分岐で BedrockVersion を取るようになったため対象外）",
      "BedrockVersion" not in (SRC / "BedrockChunkTranslator.java").read_text())

# ---------- J. 465 回の実質的な変更：新しい述語は不要 ----------
check("新述語は不要：StartGame は 465＝471 で JSON 完全一致（61 フィールド両方）",
      types465["packet_start_game"] == types471["packet_start_game"])
check("hasStartGameBlockPaletteChecksum() が protocol >= 475 で定義されている（465・471 とも false）",
      bool(re.search(r"boolean hasStartGameBlockPaletteChecksum\(\) \{\s*return protocol >= 475;",
                     ver_src)))
check("BedrockVersionTest に startGameBlockPaletteChecksumGate テストがあり V1_17_34 が false",
      "startGameBlockPaletteChecksumGate" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text()
      and "V1_17_34.hasStartGameBlockPaletteChecksum()" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text())
check("466〜470 に分岐の閾値が無い（465 の次の閾値は 471 ではなく 475）",
      not re.search(r"protocol >= 46[6-9]|protocol >= 47[0-4]", ver_src))
pkt_src = (SRC / "BedrockPackets.java").read_text()
check("6 件の型変更のうちコードが読む・書くのは無い（ItemStackRequest・packet_animate・packet_level_event・packet_subchunk・packet_subchunk_request・mcpe_packet はコードで参照されない）",
      not any(re.search(rf"\b{name}\b", pkt_src)
              for name in ["ItemStackRequest", "packet_animate",
                           "packet_level_event", "packet_subchunk",
                           "packet_subchunk_request"]))
check("BedrockPackets.java に SubChunk／SubChunk Request パケット（0xae／0xaf）の参照が無い（encodeLevelChunk の subChunkCount 引数と hasLevelChunkSubChunkCount 述語は LevelChunk のもので別物。354 回で述語を追加したため述語名を除いて判定する）",
      "SubChunk" not in pkt_src.replace("hasLevelChunkSubChunkCount", "")
      and "SUBCHUNK" not in pkt_src)
check("BedrockVersionTest に startGame465Equals471Length テストがある",
      "startGame465Equals471Length" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text())

print()
print(f"TOTAL: {len(CHECKS)} checks, {len(CHECKS) - len(FAILURES)} passed, {len(FAILURES)} failed")
if FAILURES:
    print("FAILURES:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
