#!/usr/bin/env python3
"""Bedrock 1.17.2（プロトコル 440）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py /
verify-be-534-tables.py / verify-be-527-tables.py /
verify-be-503-tables.py / verify-be-486-tables.py / verify-be-475-tables.py /
verify-be-471-tables.py / verify-be-465-tables.py / verify-be-448-tables.py
にならい、表・定数・述語の前提を機械計数で確認する。すべて実ファイル
（BedrockVersion.java / BedrockProtocol.java / BedrockSession.java /
protocol-1.17.0.json / protocol-1.17.10.json / protocol-1.17.30.json /
protocol-1.17.40.json / protocol-1.18.0.json / protocol-1.18.11.json /
protocol-1.18.30.json / protocol-1.19.1.json / protocol-1.19.10.json /
protocol-1.19.20.json / protocol-1.19.30.json / protocol-1.19.40.json /
protocol-1.19.50.json / .bin データ）に対するチェックで、推測はない。

- 440 の protocol.json は minecraft-data の `data/bedrock/1.17.0/` にある。
- 440 は 1.17.0 / 1.17.1 / 1.17.2 で共有するプロトコル（Minecraft Wiki
  「Bedrock Edition 1.17.0」「Bedrock Edition 1.17.1」「Bedrock Edition
  1.17.2」個別ページの infobox「Protocol version 440」・
  protocolVersions.json では 440＝1.17.0 のみ。displayName は共有するうち
  最新の 1.17.2（448 の V1_17_11 と同じ流儀）。minecraft-data に 1.17.1／
  1.17.2 の protocol.json は無く（HTTP 404）、440 の代表ディレクトリは
  1.17.0 のみ。BE-NOTES.md §19）。
- 440 と 448 の差分は追加 2（168 simulation_type・169 npc_dialogue）・
  削除 0・リネーム 0・型変更 10 件。StartGame は 440・448 ともに
  60 フィールドで JSON 完全一致。add_player・add_entity・
  set_entity_data も 440＝448 で JSON 完全一致。
- 440 の 1 つ下は 431（1.16.220）。441〜447 にエントリは無い
  （441 は Beta 1.17.10.21 の番号、442〜447 は Wiki 表にエントリ無しで
  未確認。BE-NOTES.md §19）。
- アイテム表は新規生成が必要（bedrock_item_ids_1_17_0.bin）。
  bedrock-1.17.0 タグは存在する（required_item_list.json 104,583 バイト・
  1,038 名）。1.17.0 自体の実測データから同じ手順で構築した候補表は
  exact=730／shared=236／legacy=33／fallback=313、SHA-1 `76dc32fb…`。
  1_17_10 の表とは 17/1312 行、1_17_40 の表とは 47/1312 行が違うため
  共用できない（BE-NOTES.md §19）。
- ブロックパレットは 1.17.10 のものとバイト同一ではない（7,682 エントリー
  対 7,852 エントリー）。それでもブロック表は新規不要（既存ペアを流用。
  589 と同じ根拠。1.17.0 と 1.20.50 の同一ランタイム ID は 0/7,682。
  BE-NOTES.md §19）。biome_id_map.json は bedrock-1.17.0 タグに
  存在しない（HTTP 404）が、設計上使っていないため要否判断に影響しない
  （未確認のまま）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 440 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 440 回の実質的な変更：**hasForceServerPacks() の下限追加**
  （protocol >= 448 && protocol <= 712）。ResourcePacksInfo の
  force_server_packs は 448 で新規（440 には無い）が、旧述語は
  protocol <= 712 だけで 440 にも書いてしまっていた。440 では
  書かない・読まない（BedrockPackets.encodeResourcePacksInfo と
  BedrockPacketsVersionTest.parseResourcePacksInfo を分岐）。
  それ以外の 9 件の型変更のうちコードが読む・書くのは無い（Skin は
  writeEmptySkin が 567 形式のまま。§J 参照）。

使い方: python3 detergentmc-bedrock/tools/verify-be-440-tables.py
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


def find_flags_type(obj):
    """packet_available_commands の JSON 内で name が flags の欄の type を返す。
    flags は enums 配列の要素コンテナの中にある。"""
    if isinstance(obj, dict):
        if obj.get("name") == "flags":
            return obj.get("type")
        for v in obj.values():
            r = find_flags_type(v)
            if r is not None:
                return r
    elif isinstance(obj, list):
        for v in obj:
            r = find_flags_type(v)
            if r is not None:
                return r
    return None


def packet_ids(data):
    mapper = data["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
    return {int(k): v for k, v in mapper.items()}


# ---------- A. protocol.json（440）の同一性 ----------
p40 = TMP / "protocol-1.17.0.json"
check("440 protocol.json が存在する", p40.is_file(), str(p40))
check("440 protocol.json の行数が 9,115 行（wc -l、改行数）",
      p40.is_file()
      and p40.read_text().count("\n") == 9115,
      f"{p40.read_text().count(chr(10))} 行" if p40.is_file() else "なし")
check("440 protocol.json のサイズが 235,230 バイト",
      p40.is_file() and p40.stat().st_size == 235230,
      f"{p40.stat().st_size} バイト" if p40.is_file() else "なし")
check("440 protocol.json の SHA-1 が 9958534f…と一致",
      p40.is_file()
      and sha1(p40) == "9958534fd0f90f0a211044cd525639295458a7bb",
      sha1(p40) if p40.is_file() else "なし")

data440 = json.loads(p40.read_text())
types440 = data440["types"]
ids440 = packet_ids(data440)

# ---------- A2. 隣接バージョン（448・465・471・475・486・503・527・534・544・554・557・560）の protocol.json ----------
p48 = TMP / "protocol-1.17.10.json"
check("448 protocol.json（上側の隣接）が存在する", p48.is_file(), str(p48))
check("448 protocol.json の行数が 9,289 行・サイズが 241,661 バイト・SHA-1 が 3a5c09f3…と一致",
      p48.is_file() and p48.read_text().count("\n") == 9289
      and p48.stat().st_size == 241661
      and sha1(p48) == "3a5c09f3e6b0a52d86878ce09a9b28e17919acb9",
      f"{p48.read_text().count(chr(10))} 行・{p48.stat().st_size} バイト" if p48.is_file() else "なし")
check("440 の protocol.json は 448 のものとバイト同一ではない（SHA-1 が違う）",
      p40.is_file() and p48.is_file()
      and sha1(p40) != sha1(p48))
p65 = TMP / "protocol-1.17.30.json"
check("465 protocol.json が存在する", p65.is_file(), str(p65))
check("465 protocol.json の行数が 9,590 行・サイズが 248,252 バイト・SHA-1 が 6e4f4df8…と一致",
      p65.is_file() and p65.read_text().count("\n") == 9590
      and p65.stat().st_size == 248252
      and sha1(p65) == "6e4f4df82649c941c17aff5fd4ddee77b1266e10",
      f"{p65.read_text().count(chr(10))} 行・{p65.stat().st_size} バイト" if p65.is_file() else "なし")
p71 = TMP / "protocol-1.17.40.json"
check("471 protocol.json が存在する", p71.is_file(), str(p71))
check("471 protocol.json の行数が 9,687 行・サイズが 250,827 バイト・SHA-1 が 081fa7f4…と一致",
      p71.is_file() and p71.read_text().count("\n") == 9687
      and p71.stat().st_size == 250827
      and sha1(p71) == "081fa7f411b80ed9d22116c929c82aa61d8462f8",
      f"{p71.read_text().count(chr(10))} 行・{p71.stat().st_size} バイト" if p71.is_file() else "なし")
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
    check("protocolVersions.json に 441〜447 のエントリが無い",
          not any(441 <= e["version"] <= 447 for e in pv))
    check("protocolVersions.json の 440 は 1.17.0",
          [e["minecraftVersion"] for e in pv if e["version"] == 440] == ["1.17.0"])
    check("protocolVersions.json の 440 の 1 つ下は 431（1.16.220）",
          max(e["version"] for e in pv if e["version"] < 440) == 431)
    check("protocolVersions.json の 431 は 1.16.220",
          [e["minecraftVersion"] for e in pv if e["version"] == 431] == ["1.16.220"])
    check("protocolVersions.json の 448 は 1.17.10",
          [e["minecraftVersion"] for e in pv if e["version"] == 448] == ["1.17.10"])
    check("protocolVersions.json の 448 の 1 つ下は 440（1.17.0）",
          max(e["version"] for e in pv if e["version"] < 448) == 440)
    check("protocolVersions.json の 465 は 1.17.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 465] == ["1.17.30"])
    check("protocolVersions.json の 465 の 1 つ下は 448（1.17.10）",
          max(e["version"] for e in pv if e["version"] < 465) == 448)
    check("protocolVersions.json の 471 は 1.17.40",
          [e["minecraftVersion"] for e in pv if e["version"] == 471] == ["1.17.40"])
    check("protocolVersions.json の 475 は 1.18.0",
          [e["minecraftVersion"] for e in pv if e["version"] == 475] == ["1.18.0"])
    check("protocolVersions.json の 486 は 1.18.11",
          [e["minecraftVersion"] for e in pv if e["version"] == 486] == ["1.18.11"])
    check("protocolVersions.json の 503 は 1.18.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 503] == ["1.18.30"])
    check("protocolVersions.json の 527 は 1.19.1",
          [e["minecraftVersion"] for e in pv if e["version"] == 527] == ["1.19.1"])

data448 = json.loads(p48.read_text()) if p48.is_file() else None
data465 = json.loads(p65.read_text()) if p65.is_file() else None
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
types448 = data448["types"] if data448 else {}
types465 = data465["types"] if data465 else {}
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
ids448 = packet_ids(data448) if data448 else {}
ids465 = packet_ids(data465) if data465 else {}
ids471 = packet_ids(data471) if data471 else {}
ids475 = packet_ids(data475) if data475 else {}
ids486 = packet_ids(data486) if data486 else {}
ids503 = packet_ids(data503) if data503 else {}
ids527 = packet_ids(data527) if data527 else {}

# ---------- B. パケット一覧（440）の件数・境界 ----------
check("440 のパケット数が 164 件", len(ids440) == 164, f"{len(ids440)} 件")
check("440 の最大パケット ID が 167", max(ids440) == 167, f"最大 {max(ids440)}")
check("440 の型定義数が 253", len(types440) == 253, f"{len(types440)}")
check("448 のパケット数が 166 件", len(ids448) == 166, f"{len(ids448)} 件")
check("448 の最大パケット ID が 169", max(ids448) == 169, f"最大 {max(ids448)}")
check("448 の型定義数が 255", len(types448) == 255, f"{len(types448)}")
check("440 の ID の欠番が 0・16・37・116（448 と同じ）",
      [i for i in range(max(ids440) + 1) if i not in ids440] == [0, 16, 37, 116])
check("440 のパケット ID 0〜167 が 448 の同 ID と名称がすべて一致",
      all(ids440[i] == ids448[i] for i in ids440),
      f"{sum(1 for i in ids440 if ids440[i] == ids448.get(i))} / {len(ids440)} 件一致")

d440 = TMP / "packet-diff-440-vs-448.json"
check("440 vs 448 の差分 JSON が存在する", d440.is_file(), str(d440))
if d440.is_file():
    j440 = json.loads(d440.read_text())
    check("440 vs 448：追加 2 件（168 simulation_type・169 npc_dialogue）",
          j440["added"] == {"168": "simulation_type", "169": "npc_dialogue"},
          json.dumps(j440["added"]))
    check("440 vs 448：削除 0・リネーム 0",
          j440["removed"] == {} and j440["renamed"] == {})
    check("440 vs 448：型変更 10 件",
          len(j440["changed_types"]) == 10, str(j440["changed_types"]))
    check("440 vs 448：型変更の内訳が InputFlag・SoundType・mcpe_packet・packet_available_commands・packet_level_event・packet_npc_dialogue・packet_npc_request・packet_resource_packs_info・packet_set_title・packet_simulation_type",
          j440["changed_types"] == ["InputFlag", "SoundType", "mcpe_packet",
                                     "packet_available_commands",
                                     "packet_level_event",
                                     "packet_npc_dialogue (only in B)",
                                     "packet_npc_request",
                                     "packet_resource_packs_info",
                                     "packet_set_title",
                                     "packet_simulation_type (only in B)"],
          json.dumps(j440["changed_types"]))

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
json_by_name = {v: k for k, v in ids440.items()}
# update_subchunk_blocks・subchunk・subchunk_request は 440 には存在しない
# （465・471 で新規追加）ためループから除き、不存在を別途確認する。
for const, pname in CORE:
    if const in ("UPDATE_SUB_CHUNK_BLOCKS", "SUB_CHUNK", "SUB_CHUNK_REQUEST"):
        continue
    java_id = int(java_packets[const], 16)
    json_id = json_by_name.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が protocol.json と一致",
          json_id == java_id, f"json={json_id}")
check("パケット ID 70 の名称が chunk_radius_update（Java 定数名 CHUNK_RADIUS_UPDATED とは名称違い・448 回と同じ）",
      ids440.get(70) == "chunk_radius_update")
check("パケット UPDATE_SUB_CHUNK_BLOCKS（0xac=172）が 440 の protocol.json に存在しない（465 で新規追加）",
      json_by_name.get("update_subchunk_blocks") is None)
check("パケット SUB_CHUNK（0xae=174）が 440 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk") is None)
check("パケット SUB_CHUNK_REQUEST（0xaf=175）が 440 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk_request") is None)
check("パケット SIMULATION_TYPE（168）が 440 の protocol.json に存在しない（448 で新規追加）",
      json_by_name.get("simulation_type") is None)
check("パケット NPC_DIALOGUE（169）が 440 の protocol.json に存在しない（448 で新規追加）",
      json_by_name.get("npc_dialogue") is None)

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_17_2 が存在する", "V1_17_2" in entry_by_name)
check("V1_17_2 のプロトコルが 440",
      entry_by_name.get("V1_17_2", (None,))[0] == 440,
      str(entry_by_name.get("V1_17_2")))
check("V1_17_2 の表示名が 1.17.2",
      entry_by_name.get("V1_17_2", (None, None))[1] == "1.17.2")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_17_2 が V1_17_11 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_17_2(440") < ver_src.index("V1_17_11(448"))
check("enum の javadoc に 1.17.0 の protocol.json が含まれる",
      "1.17.0" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 440 -> \"(1_17_0)\";", ver_src)
check("itemTableTag：440 が 1_17_0 の表を使う（新規生成・1.17.0 自体の実測）", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_17_0.bin"
check("アイテム表 bedrock_item_ids_1_17_0.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が 76dc32fb…と一致（1.17.0 候補表）",
      tag_bin.is_file()
      and sha1(tag_bin) == "76dc32fb7443cbf4747cd0301a74e5b7c466af04",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規 1 本）", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s440 = summary.get("440", {})
check("item_tables_summary.json に 440 のエントリがある",
      "440" in summary)
check("item_tables_summary.json の 440 が tag=bedrock-1.17.0・exact=730／shared=236／legacy=33／fallback=313",
      s440.get("tag") == "bedrock-1.17.0" and s440.get("exact") == 730
      and s440.get("shared") == 236 and s440.get("legacy") == 33
      and s440.get("fallback") == 313,
      str({k: s440.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))
check("item_tables_summary.json の 440 の missing_names が 313 件",
      len(s440.get("missing_names", [])) == 313,
      f"{len(s440.get('missing_names', []))} 件")
check("item_tables_summary.json の 448 のエントリが残っている（全テーブル再生成による消失なし）",
      "448" in summary and summary["448"].get("exact") == 747
      and summary["448"].get("fallback") == 296)
check("item_tables_summary.json の 465 のエントリが残っている（全テーブル再生成による消失なし）",
      "465" in summary and summary["465"].get("exact") == 751
      and summary["465"].get("fallback") == 292)
check("item_tables_summary.json の 471 のエントリが残っている（全テーブル再生成による消失なし）",
      "471" in summary and summary["471"].get("exact") == 751
      and summary["471"].get("fallback") == 292)
check("item_tables_summary.json の 475 のエントリが残っている（全テーブル再生成による消失なし）",
      "475" in summary and summary["475"].get("exact") == 752
      and summary["475"].get("fallback") == 291)

# ---------- F. 述語の前提：protocol.json 側の事実 ----------
# (1) StartGame：440 は 60 フィールド、448 も 60 フィールドで JSON 完全一致
sg440 = field_names(types440, "packet_start_game")
sg448 = field_names(types448, "packet_start_game")
check("StartGame：440 は 60 フィールド", len(sg440) == 60, f"{len(sg440)} フィールド")
check("StartGame：448 は 60 フィールド", len(sg448) == 60, f"{len(sg448)} フィールド")
check("StartGame：440 のフィールド一覧が 448 と一致する（順序も同じ）",
      sg440 == sg448)
check("StartGame：440 に edu_resource_uri が無い（hasStartGameEduResourceUri()=false の前提）",
      "edu_resource_uri" not in sg440)
check("StartGame：448 に edu_resource_uri が無い（hasStartGameEduResourceUri()=false の前提）",
      "edu_resource_uri" not in sg448)
check("StartGame：440 に block_pallete_checksum が無い（hasStartGameBlockPaletteChecksum()=false の前提）",
      "block_pallete_checksum" not in sg440
      and "block_pallette_checksum" not in sg440)
check("StartGame：448 に block_pallete_checksum が無い（hasStartGameBlockPaletteChecksum()=false の前提）",
      "block_pallete_checksum" not in sg448
      and "block_pallette_checksum" not in sg448)
check("StartGame：440 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types440, "packet_start_game", "seed") == "zigzag32")
check("StartGame：448 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types448, "packet_start_game", "seed") == "zigzag32")
check("StartGame：440 の dimension は zigzag32",
      field_type(types440, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：448 の dimension は zigzag32",
      field_type(types448, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：440 に property_data が無い（hasStartGame527Fields()=false の前提）",
      "property_data" not in sg440)
check("StartGame：440 に world_template_id が無い（hasStartGame527Fields()=false の前提）",
      "world_template_id" not in sg440)
check("StartGame：440 に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg440)
check("StartGame：440 に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg440 for t in ["server_identifier", "world_identifier",
                                   "scenario_identifier", "owner_identifier"]))
check("StartGame：440 に editor_world が無い（hasEditorWorld=false）",
      "editor_world" not in sg440)
check("StartGame：440 の permission_level は zigzag32（hasPermissionLevelMapper=false）",
      field_type(types440, "packet_start_game", "permission_level") == "zigzag32")
check("StartGame：440 の movement_authority が存在する（hasMovementAuthority=true）",
      "movement_authority" in sg440)
check("StartGame：440 の itemstates が存在する（hasItemstates=true）",
      "itemstates" in sg440)
check("StartGame：440 に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg440)
check("StartGame：440 に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg440 and "server_join_info" not in sg440)
check("StartGame：440 に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg440)
check("StartGame：440 に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg440)
check("StartGame：440 に created_in_editor・exported_from_editor・block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(f in sg440 for f in ["created_in_editor", "exported_from_editor",
                                   "block_network_ids_are_hashes"]))
check("StartGame：440 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg440)
check("StartGame：440 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg440)
check("StartGame：440 に persona_disabled・custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg440 and "custom_skins_disabled" not in sg440)
check("StartGame：440 に chat_restriction_level・disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg440
      and "disable_player_interactions" not in sg440)
check("StartGame：440 に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg440)

# (2) Resource packs info / stack：440 は 4 フィールド、448 は 5 フィールド
rpi = field_names(types440, "packet_resource_packs_info")
rpi448 = field_names(types448, "packet_resource_packs_info")
check("Resource packs info：440 は 4 フィールド（must_accept・has_scripts・behaviour_packs・texture_packs）",
      rpi == ["must_accept", "has_scripts", "behaviour_packs", "texture_packs"])
check("Resource packs info：448 は 5 フィールド（440 に force_server_packs を挿入）",
      rpi448 == ["must_accept", "has_scripts", "force_server_packs",
                 "behaviour_packs", "texture_packs"])
check("Resource packs info：448 の force_server_packs が index 2（has_scripts の次）",
      rpi448.index("force_server_packs") == 2)
check("Resource packs info：440 に force_server_packs が無い（hasForceServerPacks()=false の前提）",
      "force_server_packs" not in rpi)
check("Resource packs info：440 に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("Resource packs info：440 に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("Resource packs info：440 に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("Resource packs info：440 に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
check("TexturePackInfos の countType が li16（448 と同じ）",
      types440["TexturePackInfos"][1].get("countType") == "li16"
      and types448["TexturePackInfos"][1].get("countType") == "li16")
rps = field_names(types440, "packet_resource_pack_stack")
rps448 = field_names(types448, "packet_resource_pack_stack")
check("Resource pack stack：440 は 448 と同じ 6 フィールド（behavior_packs を含む）",
      rps == rps448 == ["must_accept", "behavior_packs", "resource_packs",
                        "game_version", "experiments",
                        "experiments_previously_used"])
check("Resource pack stack：440 に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

# (3) エンティティ・アイテム
check("Mob equipment の item が Item（ItemV4 ではない）",
      field_type(types440, "packet_mob_equipment", "item") == "Item")
check("Add item entity の item が Item",
      field_type(types440, "packet_add_item_entity", "item") == "Item")
mae = [n for n, _ in container_fields(types440, "packet_mob_armor_equipment")]
check("Mob armor equipment が helmet・chestplate・leggings・boots の 4 つ（body は無い）",
      mae == ["runtime_entity_id", "helmet", "chestplate", "leggings", "boots"])
check("ItemV4 型が 440 の protocol.json に存在しない", "ItemV4" not in types440)
ap440 = field_names(types440, "packet_add_player")
ap448 = field_names(types448, "packet_add_player")
check("Add player：440 は 21 フィールド",
      len(ap440) == 21, f"{len(ap440)} フィールド")
check("Add player：448 は 21 フィールド",
      len(ap448) == 21, f"{len(ap448)} フィールド")
check("Add player：440 のフィールド一覧が uuid・username・entity_id_self・runtime_entity_id・platform_chat_id・position・velocity・pitch・yaw・head_yaw・held_item・metadata・flags・command_permission・action_permissions・permission_level・custom_stored_permissions・user_id・links・device_id・device_os",
      ap440 == ["uuid", "username", "entity_id_self", "runtime_entity_id",
                "platform_chat_id", "position", "velocity", "pitch", "yaw",
                "head_yaw", "held_item", "metadata", "flags",
                "command_permission", "action_permissions", "permission_level",
                "custom_stored_permissions", "user_id", "links", "device_id",
                "device_os"])
check("Add player：448 のフィールド一覧が 440 と一致する",
      ap448 == ap440)
check("Add player：440 の device_os は li32",
      field_type(types440, "packet_add_player", "device_os") == "li32")
check("Add player：440・448 の entity_id_self は zigzag64（名称も型も同じ）",
      field_type(types440, "packet_add_player", "entity_id_self") == "zigzag64"
      and field_type(types448, "packet_add_player", "entity_id_self") == "zigzag64")
check("Add player：440 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap440)
check("Add player：448 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap448)
check("Add entity は 440 と 448 で同じ（11 フィールド・body_yaw が無い）",
      field_names(types440, "packet_add_entity") == field_names(types448, "packet_add_entity")
      and len(field_names(types440, "packet_add_entity")) == 11)
check("Set entity data は 440 と 448 で同じ（3 フィールド）",
      field_names(types440, "packet_set_entity_data") == field_names(types448, "packet_set_entity_data")
      and len(field_names(types440, "packet_set_entity_data")) == 3)

# (4) トランザクション・アイテム関連
leg = {n: t for n, t in container_fields(types440, "TransactionLegacy")}
check("TransactionLegacy の legacy_transactions が switch（448 と同じ）",
      isinstance(leg.get("legacy_transactions"), list)
      and leg["legacy_transactions"][0] == "switch")
tx = {n: t for n, t in container_fields(types440, "Transaction")}
tx_map = tx.get("transaction_type")[1]["mappings"]
check("Transaction の transaction_type が 0=normal・1=inventory_mismatch・2=item_use・3=item_use_on_entity・4=item_release（448 と同じ）",
      tx_map.get("0") == "normal"
      and tx_map.get("1") == "inventory_mismatch"
      and tx_map.get("2") == "item_use"
      and tx_map.get("3") == "item_use_on_entity"
      and tx_map.get("4") == "item_release")
check("Transaction の actions が TransactionActions（448 と同じ）",
      tx.get("actions") == "TransactionActions")
check("Transaction が 440 と 448 で JSON 同一",
      types440["Transaction"] == types448["Transaction"])
ta = types440["TransactionActions"]
ta_fields = {f["name"]: f["type"] for f in ta[1]["type"][1] if "name" in f}
check("TransactionActions の old/new item が旧型 Item（transactionUsesItemV4=false）",
      ta_fields.get("old_item") == "Item" and ta_fields.get("new_item") == "Item",
      f"old={ta_fields.get('old_item')} new={ta_fields.get('new_item')}")
check("TransactionActions に window_id／flags の option 欄が無い",
      "window_id" not in ta_fields and "flags" not in ta_fields)
isr440 = item_stack_request_type_id(types440)
isr448 = item_stack_request_type_id(types448)
check("ItemStackRequest：440 の type_id が u8 マッパーで 16 件（0〜15）",
      isr440 is not None and isr440[1]["mappings"] == {
          "0": "take", "1": "place", "2": "swap", "3": "drop",
          "4": "destroy", "5": "consume", "6": "create",
          "7": "lab_table_combine", "8": "beacon_payment",
          "9": "mine_block", "10": "craft_recipe",
          "11": "craft_recipe_auto", "12": "craft_creative",
          "13": "optional", "14": "non_implemented",
          "15": "results_deprecated"},
      json.dumps(isr440[1]["mappings"]) if isr440 else "なし")
check("ItemStackRequest：440 の type_id が 448 と JSON 同一",
      isr440 == isr448)
check("ItemStackRequest が 440 と 448 で JSON 同一",
      types440["ItemStackRequest"] == types448["ItemStackRequest"])
check("RecipeIngredient：440 が旧形式（network_id zigzag32＋匿名 switch）",
      types440["RecipeIngredient"][0] == "container"
      and types440["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types440["RecipeIngredient"][1][1].get("anon") is True,
      json.dumps(types440["RecipeIngredient"][1][0]))
check("RecipeIngredient：448 も旧形式（network_id zigzag32＋匿名 switch）",
      types448["RecipeIngredient"][0] == "container"
      and types448["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types448["RecipeIngredient"][1][1].get("anon") is True)
check("RecipeIngredient：440 の匿名 switch が network_id=0 で void、default で network_data＋count（zigzag32）",
      types440["RecipeIngredient"][1][1]["type"][1].get("compareTo") == "network_id"
      and types440["RecipeIngredient"][1][1]["type"][1]["fields"].get("0") == "void"
      and [f["name"] for f in types440["RecipeIngredient"][1][1]["type"][1]["default"][1]] == ["network_data", "count"])
check("RecipeIngredient：554 が新形式（先頭が type の u8 マッパー：invalid／int_id_meta／molang／item_tag／string_id_meta）",
      types554["RecipeIngredient"][0] == "container"
      and types554["RecipeIngredient"][1][0].get("name") == "type"
      and types554["RecipeIngredient"][1][0]["type"][1].get("mappings") == {"0": "invalid", "1": "int_id_meta",
                                                                            "2": "molang", "3": "item_tag",
                                                                            "4": "string_id_meta"})

# (5) 核パケットの 440 vs 448：StartGame を含め 16 件中 15 件が JSON 同一
MAJOR = ["packet_add_player", "packet_add_entity", "packet_add_item_entity",
         "packet_level_chunk", "packet_move_entity", "packet_move_player",
         "packet_update_block", "packet_set_entity_data",
         "packet_mob_equipment", "packet_mob_armor_equipment",
         "packet_player_list",
         "packet_inventory_transaction", "packet_network_chunk_publisher_update",
         "packet_resource_pack_stack"]
for name in MAJOR:
    check(f"核パケット {name} が 440 と 448 で JSON 同一（分岐不要）",
          types440[name] == types448[name])
check("核パケット packet_resource_packs_info は 440 と 448 で違う（force_server_packs の有無・分岐あり）",
      types440["packet_resource_packs_info"] != types448["packet_resource_packs_info"])
check("核パケット packet_player_auth_input が 440 と 448 で JSON 同一",
      types440["packet_player_auth_input"] == types448["packet_player_auth_input"])
check("核パケット packet_start_game は 440 と 448 で JSON 同一（分岐不要）",
      types440["packet_start_game"] == types448["packet_start_game"])

# ---------- G. 440／448 の境界値 ----------
st440 = types440["SoundType"][1]["mappings"]
st448 = types448["SoundType"][1]["mappings"]
check("SoundType：440 は 361 エントリ（0〜360）", len(st440) == 361, f"{len(st440)}")
check("SoundType：448 は 363 エントリ（0〜362）", len(st448) == 363, f"{len(st448)}")
check("SoundType：440 と 448 で JSON は違う（360 の値変更＋361・362 の追加）",
      types440["SoundType"] != types448["SoundType"])
check("SoundType：440 の 360 が Undefined（448 では extinguish_candle）",
      st440.get("360") == "Undefined" and st448.get("360") == "extinguish_candle")
check("SoundType：448 の 361 が ambient_candle・362 が Undefined（440 には無い）",
      st448.get("361") == "ambient_candle" and st448.get("362") == "Undefined"
      and st440.get("361") is None and st440.get("362") is None)
check("SoundType：440 の 4 が Jump・5 が Break・6 が Place（448 と同じ）",
      st440.get("4") == "Jump" and st440.get("5") == "Break"
      and st440.get("6") == "Place")
le440 = types440["packet_level_event"]
le448 = types448["packet_level_event"]
le_map = le440[1][0]["type"][1]["mappings"]
le_map448 = le448[1][0]["type"][1]["mappings"]
check("Level event：440 は 88 エントリ（最大キー 16384）",
      len(le_map) == 88, f"{len(le_map)}")
check("Level event：448 は 168 エントリ（最大キー 16465）",
      len(le_map448) == 168, f"{len(le_map448)}")
check("Level event：440 と 448 で JSON は違う（add_particle_* 80 件の追加）",
      le440 != le448)
check("Level event：440 の 2000 が particle_shoot・2001 が particle_destroy・2002 が particle_splash・2003 が particle_eye_despawn（448 と同じ）",
      le_map.get("2000") == "particle_shoot"
      and le_map.get("2001") == "particle_destroy"
      and le_map.get("2002") == "particle_splash"
      and le_map.get("2003") == "particle_eye_despawn")
check("Level event：440 の 9800 が players_sleeping・9801 は無い（最大キーは 16384 add_particle_mask）",
      le_map.get("9800") == "players_sleeping"
      and le_map.get("9801") is None
      and max(int(k) for k in le_map) == 16384
      and le_map.get("16384") == "add_particle_mask")
check("Entity event：440・448 ともに 54 エントリ（448 と同じ）",
      len(field_type(types440, "packet_entity_event", "event_id")[1]["mappings"]) == 54
      and len(field_type(types448, "packet_entity_event", "event_id")[1]["mappings"]) == 54)
check("Entity event：440 と 448 で JSON 同一",
      types440["packet_entity_event"] == types448["packet_entity_event"])
check("Entity event：440・448 ともに 1=jump・2=hurt_animation・3=death_animation・4=arm_swing（448 と同じ）",
      field_type(types440, "packet_entity_event", "event_id")[1]["mappings"].get("1") == "jump"
      and field_type(types440, "packet_entity_event", "event_id")[1]["mappings"].get("2") == "hurt_animation"
      and field_type(types440, "packet_entity_event", "event_id")[1]["mappings"].get("3") == "death_animation"
      and field_type(types440, "packet_entity_event", "event_id")[1]["mappings"].get("4") == "arm_swing")
check("Entity event：440・448 ともに 8=shake_wet（totem・shake_web・feed はまだ無い）",
      field_type(types440, "packet_entity_event", "event_id")[1]["mappings"].get("8") == "shake_wet"
      and "totem" not in field_type(types440, "packet_entity_event", "event_id")[1]["mappings"].values()
      and "shake_web" not in field_type(types440, "packet_entity_event", "event_id")[1]["mappings"].values()
      and "feed" not in field_type(types440, "packet_entity_event", "event_id")[1]["mappings"].values())
check("GameMode：440・448 ともに 6 エントリ（0〜5・5=fallback）",
      len(types440["GameMode"][1]["mappings"]) == 6
      and len(types448["GameMode"][1]["mappings"]) == 6
      and types440["GameMode"][1]["mappings"].get("5") == "fallback")
check("GameMode：440 と 448 で JSON 同一",
      types440["GameMode"] == types448["GameMode"])
check("MetadataDictionary：440・448 ともに 124 エントリ（448 と同じ）",
      len(types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]) == 124
      and len(types448["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]) == 124)
check("MetadataDictionary：440 と 448 で JSON 同一",
      types440["MetadataDictionary"] == types448["MetadataDictionary"])
check("MetadataDictionary：440・448 ともに 0=flags・1=health・2=variant・3=color・4=nametag",
      types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("0") == "flags"
      and types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("1") == "health"
      and types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("2") == "variant"
      and types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("3") == "color"
      and types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("4") == "nametag")
check("MetadataDictionary：440・448 ともに 121=freezing_effect_strength・122=buoyancy_data・123=goat_horn_count",
      types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("121") == "freezing_effect_strength"
      and types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("122") == "buoyancy_data"
      and types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("123") == "goat_horn_count")
check("MetadataFlags2：440・448 ともに 34 フラグ（448 と同じ）",
      len(types440["MetadataFlags2"][1]["flags"]) == 34
      and len(types448["MetadataFlags2"][1]["flags"]) == 34)
check("MetadataFlags2：440 と 448 で JSON 同一",
      types440["MetadataFlags2"] == types448["MetadataFlags2"])
check("MetadataFlags2：440・448 ともに playing_dead が 33 番目（最後）・has_entity_properties はまだ無い",
      types440["MetadataFlags2"][1]["flags"][33] == "playing_dead"
      and "has_entity_properties" not in types440["MetadataFlags2"][1]["flags"]
      and "has_entity_properties" not in types448["MetadataFlags2"][1]["flags"])
check("Subchunk：440 に packet_subchunk 型が存在しない（471 で新規追加）",
      "packet_subchunk" not in types440)
check("Subchunk request：440 に packet_subchunk_request 型が存在しない（471 で新規追加）",
      "packet_subchunk_request" not in types440)
check("CommandFlags：440 は 3 フラグ（unused・has_semantic_constraint・collapse_enum）",
      [f["name"] for f in types440["CommandFlags"][1]] == ["unused",
                                                           "has_semantic_constraint",
                                                           "collapse_enum"])
check("CommandFlags：448 は 3 フラグ（440 と同じ）",
      [f["name"] for f in types448["CommandFlags"][1]] == ["unused",
                                                           "has_semantic_constraint",
                                                           "collapse_enum"])
check("MaterialReducer 型が 440 の protocol.json に存在しない（465 で新規追加）",
      "MaterialReducer" not in types440)
check("BlockUpdate 型が 440 の protocol.json に存在しない（465 で新規追加）",
      "BlockUpdate" not in types440)
check("EducationSharedResourceURI 型が 440 の protocol.json に存在しない（465 で新規追加）",
      "EducationSharedResourceURI" not in types440)
check("EducationExternalLinkSettings 型が 440 の protocol.json に存在しない（465 で新規追加）",
      "EducationExternalLinkSettings" not in types440)
check("Animate：440 は row_right=128・row_left=129＋匿名 switch（boat_rowing_time lf32）",
      types440["packet_animate"][1][0]["type"][1]["mappings"].get("128") == "row_right"
      and types440["packet_animate"][1][0]["type"][1]["mappings"].get("129") == "row_left"
      and len(container_fields(types440, "packet_animate")) == 3
      and container_fields(types440, "packet_animate")[2][1][0] == "switch")
check("Animate：440 の anon switch は action_id 依存で row_right／row_left に boat_rowing_time（lf32）",
      container_fields(types440, "packet_animate")[2][1][1].get("compareTo") == "action_id"
      and container_fields(types440, "packet_animate")[2][1][1]["fields"]["row_right"] == ["container", [{"name": "boat_rowing_time", "type": "lf32"}]]
      and container_fields(types440, "packet_animate")[2][1][1]["fields"]["row_left"] == ["container", [{"name": "boat_rowing_time", "type": "lf32"}]])
check("Animate：440 と 448 で JSON 同一",
      types440["packet_animate"] == types448["packet_animate"])
check("Animate entity：440 は 6 フィールド（stop_condition_version が無い）",
      field_names(types440, "packet_animate_entity") == ["animation", "next_state",
                                                        "stop_condition", "controller",
                                                        "blend_out_time", "runtime_entity_ids"])
check("Animate entity：448 は 6 フィールド（440 と同じ）",
      field_names(types448, "packet_animate_entity") == ["animation", "next_state",
                                                        "stop_condition", "controller",
                                                        "blend_out_time", "runtime_entity_ids"])
check("Crafting data：440 の順序が recipes・potion_type_recipes・potion_container_recipes・is_clean",
      field_names(types440, "packet_crafting_data") == ["recipes",
                                                       "potion_type_recipes",
                                                       "potion_container_recipes",
                                                       "is_clean"])
check("Crafting data：448 の順序が 440 と同じ",
      field_names(types448, "packet_crafting_data") == ["recipes",
                                                       "potion_type_recipes",
                                                       "potion_container_recipes",
                                                       "is_clean"])
check("Hurt armor：440 は health の 1 フィールド",
      field_names(types440, "packet_hurt_armor") == ["health"])
check("Hurt armor：448 は health の 1 フィールド（440 と同じ）",
      field_names(types448, "packet_hurt_armor") == ["health"])
check("Education settings：440 は 6 フィールド（CodeBuilderDefaultURI・CodeBuilderTitle・CanResizeCodeBuilder・HasOverrideURI・OverrideURI・HasQuiz）",
      field_names(types440, "packet_education_settings") == ["CodeBuilderDefaultURI",
                                                            "CodeBuilderTitle",
                                                            "CanResizeCodeBuilder",
                                                            "HasOverrideURI",
                                                            "OverrideURI", "HasQuiz"])
check("Education settings：448 は 6 フィールド（440 と同じ）",
      field_names(types448, "packet_education_settings") == ["CodeBuilderDefaultURI",
                                                            "CodeBuilderTitle",
                                                            "CanResizeCodeBuilder",
                                                            "HasOverrideURI",
                                                            "OverrideURI", "HasQuiz"])
check("Photo transfer：440 は image_name・image_data・book_id の 3 フィールド",
      field_names(types440, "packet_photo_transfer") == ["image_name", "image_data",
                                                        "book_id"])
check("Photo transfer：448 は 3 フィールド（440 と同じ）",
      field_names(types448, "packet_photo_transfer") == ["image_name", "image_data",
                                                        "book_id"])
check("Add volume entity：440 は entity_id・nbt の 2 フィールド（engine_version が無い）",
      field_names(types440, "packet_add_volume_entity") == ["entity_id", "nbt"])
check("Add volume entity：448 は 2 フィールド（440 と同じ）",
      field_names(types448, "packet_add_volume_entity") == ["entity_id", "nbt"])
check("Entity pick request：440 は runtime_entity_id・selected_slot の 2 フィールド（with_data が無い）",
      field_names(types440, "packet_entity_pick_request") == ["runtime_entity_id",
                                                             "selected_slot"])
check("Entity pick request：448 は 2 フィールド（440 と同じ）",
      field_names(types448, "packet_entity_pick_request") == ["runtime_entity_id",
                                                             "selected_slot"])
check("Update block synced：440 と 448 でフィールド名は同じ 6 件で transition_type の型も同じ（varint64）",
      field_names(types440, "packet_update_block_synced") == field_names(types448, "packet_update_block_synced")
      and field_type(types440, "packet_update_block_synced", "transition_type")[1]["type"] == "varint64"
      and field_type(types448, "packet_update_block_synced", "transition_type")[1]["type"] == "varint64")
check("Skin：440 は 17 フィールド（geometry_data_version・primary_user が無い）",
      len(types440["Skin"][1]) == 17
      and "geometry_data_version" not in [f["name"] for f in types440["Skin"][1]]
      and "primary_user" not in [f["name"] for f in types440["Skin"][1]])
check("Skin：448 は 17 フィールド（440 と JSON 同一）",
      len(types448["Skin"][1]) == 17
      and types440["Skin"] == types448["Skin"])
check("Skin：440 では premium・persona・cape_on_classic が animation_data の直後（448 と同じ index 8）",
      [f["name"] for f in types440["Skin"][1]].index("premium") == 8
      and [f["name"] for f in types448["Skin"][1]].index("premium") == 8)
check("Text：440 の type マッパーに 11 が無い（448 も無し）",
      "11" not in types440["packet_text"][1][0]["type"][1]["mappings"]
      and "11" not in types448["packet_text"][1][0]["type"][1]["mappings"])
check("Network settings：440・448 ともに compression_threshold は u16",
      field_type(types440, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types448, "packet_network_settings", "compression_threshold") == "u16")
check("Structure block update：440・448 ともに 9 フィールド（water_logged が無い）",
      len(field_names(types440, "packet_structure_block_update")) == 9
      and len(field_names(types448, "packet_structure_block_update")) == 9)
check("Spawn particle effect：440・448 ともに 4 フィールド（dimension・entity_id・position・particle_name）",
      field_names(types440, "packet_spawn_particle_effect") == ["dimension",
                                                                "entity_id",
                                                                "position",
                                                                "particle_name"]
      and field_names(types440, "packet_spawn_particle_effect") == field_names(types448, "packet_spawn_particle_effect"))
check("Boss event：440・448 ともに query が無い（ボスバー問い合わせは 582 以降）",
      "query" not in str(types440["packet_boss_event"])
      and "query" not in str(types448["packet_boss_event"]))
check("CommandOrigin：440 と 448 で JSON 同一（type マッピング含む）",
      types440["CommandOrigin"] == types448["CommandOrigin"])
check("Adventure settings：440 と 448 で command_permission が同じ",
      field_type(types440, "packet_adventure_settings", "command_permission") == field_type(types448, "packet_adventure_settings", "command_permission"))
check("StructureBlockSettings：440 と 448 で JSON 同一",
      types440["StructureBlockSettings"] == types448["StructureBlockSettings"])
check("Available commands：440 の flags は u8、448 の flags は lu16（型変更の 1 箇所）",
      find_flags_type(types440["packet_available_commands"]) == "u8"
      and find_flags_type(types448["packet_available_commands"]) == "lu16",
      f"440={find_flags_type(types440['packet_available_commands'])} 448={find_flags_type(types448['packet_available_commands'])}")
check("Modal form response：440 は form_id・data の 2 フィールド（448 と同じ）",
      field_names(types440, "packet_modal_form_response") == ["form_id", "data"]
      and field_names(types440, "packet_modal_form_response") == field_names(types448, "packet_modal_form_response"))
check("Map info request：440 は map_id の 1 フィールド（448 と同じ）",
      field_names(types440, "packet_map_info_request") == ["map_id"]
      and field_names(types440, "packet_map_info_request") == field_names(types448, "packet_map_info_request"))
check("Network chunk publisher update：440 は coordinates・radius の 2 フィールド（448 と同じ）",
      field_names(types440, "packet_network_chunk_publisher_update") == ["coordinates", "radius"]
      and field_names(types440, "packet_network_chunk_publisher_update") == field_names(types448, "packet_network_chunk_publisher_update"))
check("Clientbound map item data：440 に origin が無い（448 と同じ）",
      "origin" not in field_names(types440, "packet_clientbound_map_item_data")
      and "origin" not in field_names(types448, "packet_clientbound_map_item_data"))
pa440 = [f["name"] for f in types440["PlayerAttributes"][1]["type"][1] if "name" in f]
check("PlayerAttributes：440 は min・max・current・default・name の 5 項目（448 と同じ）",
      pa440 == ["min", "max", "current", "default", "name"])
check("PlayerAttributes：448 も同じ 5 項目",
      [f["name"] for f in types448["PlayerAttributes"][1]["type"][1] if "name" in f] == ["min", "max", "current", "default", "name"])
check("InputFlag：440 は 37 フラグで ascend_scaffolding・descend_scaffolding（448 では ascend_block・descend_block）",
      len(types440["InputFlag"][1]["flags"]) == 37
      and "ascend_scaffolding" in types440["InputFlag"][1]["flags"]
      and "descend_scaffolding" in types440["InputFlag"][1]["flags"]
      and "ascend_block" in types448["InputFlag"][1]["flags"]
      and "descend_block" in types448["InputFlag"][1]["flags"])
check("DeviceOS 型が 440 の protocol.json に存在しない", "DeviceOS" not in types440)
check("EntityProperties 型が 440 の protocol.json に存在しない", "EntityProperties" not in types440)
check("SubChunkEntryWithCaching 型が 440 の protocol.json に存在しない", "SubChunkEntryWithCaching" not in types440)

# ---------- H. ブロック・バイオーム・item 名前対応 ----------
be440_dir = TMP / "be-440"
req440 = be440_dir / "req-bedrock-1.17.0-required_item_list.json"
canon440 = be440_dir / "canon-bedrock-1.17.0.nbt"
biome440 = be440_dir / "biomeid-bedrock-1.17.0.json"
blockid440 = be440_dir / "blockid-bedrock-1.17.0.json"
req448 = TMP / "be-448" / "req-bedrock-1.17.10-required_item_list.json"
canon448 = TMP / "be-448" / "canon-bedrock-1.17.10.nbt"
check("1.17.0 の required_item_list.json が存在する", req440.is_file(), str(req440))
check("1.17.0 の required_item_list.json のサイズが 104,583 バイト",
      req440.is_file() and req440.stat().st_size == 104583,
      f"{req440.stat().st_size} バイト" if req440.is_file() else "なし")
check("1.17.0 の required_item_list.json の SHA-1 が 2b799b11…と一致",
      req440.is_file()
      and sha1(req440) == "2b799b11e3c2cab2415fad75d8c6d981cb74ccb9",
      sha1(req440) if req440.is_file() else "なし")
check("1.17.0 の required_item_list.json の名前が 1,038 件",
      req440.is_file()
      and len(json.loads(req440.read_text())) == 1038,
      f"{len(json.loads(req440.read_text()))} 件" if req440.is_file() else "なし")
check("1.17.0 の required_item_list.json が 1.17.10 のものとバイト同一ではない",
      req440.is_file() and req448.is_file()
      and sha1(req440) != sha1(req448))
check("1.17.0 の canonical_block_states.nbt が存在する", canon440.is_file(), str(canon440))
check("1.17.0 の canonical_block_states.nbt のサイズが 1,154,321 バイト",
      canon440.is_file() and canon440.stat().st_size == 1154321,
      f"{canon440.stat().st_size} バイト" if canon440.is_file() else "なし")
check("1.17.0 の canonical_block_states.nbt の SHA-1 が df897310…と一致",
      canon440.is_file()
      and sha1(canon440) == "df897310c5f85eb4150503ec095f6277f35b8586",
      sha1(canon440) if canon440.is_file() else "なし")
sys.path.insert(0, str(BE / "tools"))
from parse_nbt import parse_canonical  # noqa: E402
canon_entries_440 = (len(parse_canonical(str(canon440))[0])
                     if canon440.is_file() else -1)
canon_entries_448 = (len(parse_canonical(str(canon448))[0])
                     if canon448.is_file() else -1)
check("1.17.0 の canonical_block_states.nbt が 1.17.10 のものとバイト同一ではない",
      canon440.is_file() and canon448.is_file()
      and sha1(canon440) != sha1(canon448))
check("1.17.0 の canonical_block_states.nbt が 7,682 エントリー（NBT のパレット長・parse_nbt.py で数えた実測）",
      canon440.is_file() and canon_entries_440 == 7682,
      f"{canon_entries_440} エントリー" if canon440.is_file() else "なし")
check("1.17.10 の canonical_block_states.nbt が 7,852 エントリー",
      canon448.is_file() and canon_entries_448 == 7852,
      f"{canon_entries_448} エントリー" if canon448.is_file() else "なし")
check("1.17.0 の block_id_map.json が存在する", blockid440.is_file(), str(blockid440))
check("1.17.0 の block_id_map.json の SHA-1 が f3855a10…と一致",
      blockid440.is_file()
      and sha1(blockid440) == "f3855a102c698f960020d928bcaef10d25724062",
      sha1(blockid440) if blockid440.is_file() else "なし")
check("1.17.0 の block_id_map.json が 553 エントリー",
      blockid440.is_file()
      and len(json.loads(blockid440.read_text())) == 553,
      f"{len(json.loads(blockid440.read_text()))} 件" if blockid440.is_file() else "なし")
blockid18 = TMP / "be-475" / "blockid-bedrock-1.18.0.json"
check("1.17.0 の block_id_map.json が 1.18.0 のものとバイト同一（SHA-1 が同じ）",
      blockid440.is_file() and blockid18.is_file()
      and sha1(blockid440) == sha1(blockid18))
check("1.17.0 の biome_id_map.json が存在しない（bedrock-1.17.0 タグに無く HTTP 404・未確認のまま）",
      not biome440.is_file(), str(biome440))
check("1.17.0 のブロック用 .bin は無く（既存ペアの流用）",
      not (RES / "bedrock_block_runtime_ids_1_17_0.bin").exists())
check("既存ペアの canonical_block_states_1_20_50.nbt が存在し SHA-1 が d2b6acbb…と一致",
      (RES / "canonical_block_states_1_20_50.nbt").is_file()
      and sha1(RES / "canonical_block_states_1_20_50.nbt") == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("既存ペアの bedrock_block_runtime_ids_1_20_4.bin が存在し SHA-1 が ee36fffb…と一致",
      (RES / "bedrock_block_runtime_ids_1_20_4.bin").is_file()
      and sha1(RES / "bedrock_block_runtime_ids_1_20_4.bin") == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
# パレットの状態比較（compare_palette_440.py と同じ key で数えた実測）
def _pal_key(x):
    return (x["name"], json.dumps(x.get("states", {}), sort_keys=True, ensure_ascii=False))
if canon440.is_file() and canon448.is_file():
    _e440 = parse_canonical(str(canon440))[0]
    _e448 = parse_canonical(str(canon448))[0]
    _k440 = {_pal_key(x): i for i, x in enumerate(_e440)}
    _k448 = {_pal_key(x): i for i, x in enumerate(_e448)}
    _same = sum(1 for kk, i in _k440.items() if _k448.get(kk) == i)
    _only440 = [kk for kk in _k440 if kk not in _k448]
    _only448 = [kk for kk in _k448 if kk not in _k440]
    check("1.17.0 vs 1.17.10：同一ランタイム ID が 478/7,682",
          _same == 478, f"{_same}/7,682")
    check("1.17.0 にのみある状態が 0 件（1.17.0 は 1.17.10 の厳密な部分集合）",
          len(_only440) == 0, f"{len(_only440)} 件")
    check("1.17.10 にのみある状態が 170 件（ロウソク類のみ）",
          len(_only448) == 170
          and all(n.startswith("minecraft:") and "candle" in n for n, _ in _only448),
          f"{len(_only448)} 件")

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
check("BedrockVersion の NetherNet 分岐（2225）が 440 と無関係に残っている",
      "2225" in ver_src and "NetherNet" in ver_src)
check("BedrockChunkTranslator が BedrockVersion を参照していない（チャンク変換はバージョン非依存。BedrockEntityTranslator は 431 回の goat_horn_count 分岐で BedrockVersion を取るようになったため対象外）",
      "BedrockVersion" not in (SRC / "BedrockChunkTranslator.java").read_text())

# ---------- J. 440 回の実質的な変更：hasForceServerPacks() の下限追加 ----------
check("述語 hasForceServerPacks() が protocol >= 448 && protocol <= 712 で定義されている",
      bool(re.search(r"boolean hasForceServerPacks\(\) \{\s*return protocol >= 448 && protocol <= 712;",
                     ver_src)))
check("BedrockPackets.encodeResourcePacksInfo が force_server_packs を hasForceServerPacks() で分岐している",
      "hasForceServerPacks()" in (SRC / "BedrockPackets.java").read_text())
check("BedrockPacketsVersionTest.parseResourcePacksInfo が force_server_packs を hasForceServerPacks() で分岐している",
      "hasForceServerPacks()" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text())
check("BedrockVersionTest に resourcePackGatesMatchVerifiedShapes テストがあり V1_17_2 が hasForceServerPacks()=false",
      "resourcePackGatesMatchVerifiedShapes" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text()
      and "V1_17_2.hasForceServerPacks()" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text())
check("441〜447 に分岐の閾値が無い（440 の次の閾値は 448）",
      not re.search(r"protocol >= 44[1-7]\b", ver_src))
pkt_src = (SRC / "BedrockPackets.java").read_text()
check("10 件の型変更のうちコードが読む・書くのは ResourcePacksInfo の force_server_packs のみ（packet_set_title・packet_npc_request・packet_available_commands・packet_level_event・InputFlag・SoundType・packet_simulation_type・packet_npc_dialogue はコードで参照されない）",
      not any(re.search(rf"\b{name}\b", pkt_src)
              for name in ["packet_set_title",
                           "packet_npc_request",
                           "packet_available_commands",
                           "packet_level_event",
                           "InputFlag", "SoundType",
                           "packet_simulation_type",
                           "packet_npc_dialogue"]))
check("Skin の既知の制限：writeEmptySkin は geometry_data_version・primary_user・overriding_player_appearance を書く 567 形式のまま（440 の Skin は 17 フィールドで順序も違う。PlayerList は偽クライアントが解析しないため 465〜560 と同じ既存の近似で、440 回では直さない。BE-NOTES.md §19 参照）",
      "geometry data version" in pkt_src
      and "primary user" in pkt_src
      and "overriding player appearance" in pkt_src)

print()
print(f"TOTAL: {len(CHECKS)} checks, {len(CHECKS) - len(FAILURES)} passed, {len(FAILURES)} failed")
if FAILURES:
    print("FAILURES:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
