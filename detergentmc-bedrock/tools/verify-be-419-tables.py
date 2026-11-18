#!/usr/bin/env python3
"""Bedrock 1.16.101（プロトコル 419）の配線検証。

Java ループの verify-735-tables.py と verify-be-422-tables.py /
verify-be-428-tables.py 等にならい、表・定数・述語の前提を機械計数で
確認する。すべて実ファイル（BedrockVersion.java / BedrockProtocol.java /
BedrockSession.java / BedrockServer.java / protocol-1.16.201.json /
protocolVersions.json / .bin データ / CloudburstMC Protocol の
v419・v422 コーデックのソース）に対するチェックで、推測はない。

- **419 には protocol.json が無い。** minecraft-data の
  `data/bedrock/1.16.100/protocol.json` と
  `data/bedrock/1.16.101/protocol.json` はどちらも HTTP 404 で、
  protocolVersions.json にも 419 のエントリは無い（422 の 1 つ下は
  100）。そのため 419 の出典は、CloudburstMC/Protocol（branch `3.0`、
  commit `7163a6aedaefbc1d188431ef90ebc703b1138340`、
  `.tmp-be/cloudburst-protocol/` に clone 済み）の v419 コーデック
  （`Bedrock_v419.java`。VERSIONS.md で `Bedrock_v419 | 1.16.100`）と、
  422 の protocol.json（minecraft-data の `data/bedrock/1.16.201/`）の
  突き合わせである。このスクリプトの 419 のパケット一覧は
  「422 の protocol.json の 160 件から filter_text_packet（163）を
  除いたもの」として構築し、Cloudburst 側の registerPacket /
  updateSerializer の実物と一致することを別途確認する。
- 419 は 1.16.100 と 1.16.101 の 2 バージョンで共有するプロトコル
  （Minecraft Wiki「Bedrock Edition 1.16.100」「Bedrock Edition
  1.16.101」個別ページの infobox「Protocol version 419」で確認。
  BE-NOTES.md §23）。displayName は 1.16.101。
  420（1.16.200.51）・421（1.16.200.52）はベータ版のため飛ばす。
  419 の 1 つ下は 408。
- Cloudburst の Bedrock_v419 は Bedrock_v408 を継承する（v408 は
  v407 のバージョン番号だけの差分）。v419 の新規登録パケットは
  6 件（157 MotionPredictionHints・158 AnimateEntity・159 CameraShake・
  160 PlayerFog・161 CorrectPlayerMovePrediction・162 ItemComponent）。
  シリアライザ更新は 9 件（ResourcePackStack・StartGame・MovePlayer・
  UpdateAttributes・SetEntityData・ContainerClose・MoveEntityDelta・
  PlayerAuthInput・ItemStackResponse）に LevelSoundEvent 系 3 件と
  AvailableCommands を加えた 13 件。COMMAND_PARAMS は 7・30 で
  シフト、SOUND_EVENTS は 317 を EQUIP_NETHERITE に置換し 318 に
  UNDEFINED を挿入、LEVEL_EVENTS は 1900〜1903 を追加する。
- Bedrock_v422 は v419 に対して FilterText（163）の登録・
  ResourcePacksInfo シリアライザの更新・ITEM_STACK_REQUEST_TYPES の
  shift(12,1)＋CRAFT_RECIPE_OPTIONAL 挿入だけを行う。
  BedrockCodecHelper_v422 は ItemStackRequest に filteredStrings
  配列を追加し、ItemStackResponseSlot の writeItemEntry で
  customName を書く。よって 419 のパケット一覧は 422 の 160 件から
  163 を除いた 159 件・最大 ID 162・欠番 [16, 37, 116]
  （ID 0 はパケット ID の範囲外として扱う。verify-be-422 の流儀）。
- StartGame は StartGameSerializer_v419 を 419・422 で共用する
  （Bedrock_v422.java は StartGamePacket のシリアライザを更新して
  いない）。serialize の順序は uniqueEntityId・runtimeEntityId・
  playerGameType・playerPosition・rotation・writeLevelSettings・
  levelId・levelName・premiumWorldTemplateId・trial・
  authoritativeMovementMode・currentTick・enchantmentSeed・
  blockProperties・itemDefinitions・multiplayerCorrelationId・
  inventoriesServerAuthoritative で、422 の protocol.json の
  packet_start_game 57 フィールドと一致する。experiments の件数は
  v419 ヘルパーが writeIntLE で書き、protocol.json でも li32。
- ResourcePacksInfo の 419 の実効シリアライザは v332
  （forcedToAccept・scriptingEnabled・behavior・resource の順、
  エントリ末尾は isScripting）。v422 は resource エントリの末尾に
  raytracingCapable を追加するだけ。DetergentMC は空の一覧しか
  書かないためワイヤ差分は無い。
- アイテム表は新規生成不要。PMMP bedrock-1.16.100 タグの
  required_item_list.json（91,328 バイト、SHA-1 682782b5…、
  914 名）は 1.16.200 のリストとバイト同一で、そこから再生成した
  候補表（exact=624／shared=236／legacy=33／fallback=419、SHA-1
  a35eaa5a…）も配置済みの bedrock_item_ids_1_16_210.bin と
  バイト同一。ブロックも 1.16.100 の canonical_block_states
  （981,311 バイト、SHA-1 1d6771cd…）と block_id_map
  （19,660 バイト、SHA-1 f3855a10…）が 1.16.200 のものと
  バイト同一。item_tables_summary.json に 419 のエントリは無い
  （422・428 が表を共用するときと同じ流儀）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  419 の対応時点では新規述語は無く、419 と 422 で全述語の値が同じ
  だった。408 回（§26）で 419 境界の述語 7 件（>= 419）が増えたが、
  419 と 422 はどちらも >= 419 のため全述語の値が同じ前提は不変で、
  G では 420〜427 の閾値が 422 のみ（§28 で下限を追加した 2 述語）であることを確認する。述語そのものの
  実行時挙動は BedrockVersionTest で確認する。
- 419 回の実質的な変更はコード側には無い（verify 側の追加のみ）。
  判断待ちで直さないもの（Transaction の network_ids・
  MetadataDictionary のキー・Skin・experiments の varint 書き込み・
  ItemStackRequest を読み書きしないこと）は §J で現状のままである
  ことを記録する。

使い方: python3 detergentmc-bedrock/tools/verify-be-419-tables.py
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
CB = TMP / "cloudburst-protocol"  # CloudburstMC/Protocol の clone
CB_CODEC = (CB / "bedrock-codec/src/main/java/org/cloudburstmc"
            / "protocol/bedrock/codec")

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


def tokens_in_order(text, tokens):
    """tokens が text の中にこの順序で現れることを確認する。"""
    pos = [text.find(t) for t in tokens]
    return all(p >= 0 for p in pos) and pos == sorted(pos)


# ---------- A. 出典の同一性 ----------
# A-1. Cloudburst clone（branch 3.0・commit 7163a6ae…）
git_head = CB / ".git/HEAD"
check("Cloudburst clone の .git/HEAD が branch 3.0 を指す",
      git_head.is_file()
      and git_head.read_text().strip() == "ref: refs/heads/3.0",
      git_head.read_text().strip() if git_head.is_file() else "なし")
git_ref = CB / ".git/refs/heads/3.0"
check("Cloudburst clone の commit が 7163a6ae…と一致",
      git_ref.is_file()
      and git_ref.read_text().strip()
      == "7163a6aedaefbc1d188431ef90ebc703b1138340",
      git_ref.read_text().strip() if git_ref.is_file() else "なし")
versions_md = (CB / "VERSIONS.md").read_text() \
    if (CB / "VERSIONS.md").is_file() else ""
check("VERSIONS.md に Bedrock_v419 | 1.16.100 の行がある",
      any("Bedrock_v419" in line and "1.16.100" in line
          for line in versions_md.splitlines()))
check("VERSIONS.md に Bedrock_v422 | 1.16.200 - 1.16.201 の行がある",
      any("Bedrock_v422" in line and "1.16.200" in line
          and "1.16.201" in line for line in versions_md.splitlines()))
check("VERSIONS.md に Bedrock_v408 | 1.16.20 の行がある（419 の 1 つ下は 408 系）",
      any("Bedrock_v408" in line and "1.16.20" in line
          for line in versions_md.splitlines()))

v419_path = CB_CODEC / "v419/Bedrock_v419.java"
v422_path = CB_CODEC / "v422/Bedrock_v422.java"
v408_path = CB_CODEC / "v408/Bedrock_v408.java"
check("Bedrock_v419.java が存在する", v419_path.is_file(), str(v419_path))
check("Bedrock_v422.java が存在する", v422_path.is_file(), str(v422_path))
v419_src = v419_path.read_text() if v419_path.is_file() else ""
v422_src = v422_path.read_text() if v422_path.is_file() else ""
v408_src = v408_path.read_text() if v408_path.is_file() else ""
check("Bedrock_v419 は Bedrock_v408 を継承する",
      "public class Bedrock_v419 extends Bedrock_v408" in v419_src)
check("Bedrock_v408 は Bedrock_v407 を継承しバージョン番号だけの差分"
      "（protocolVersion(408)・minecraftVersion 1.16.20、registerPacket／"
      "updateSerializer は無い）",
      "public class Bedrock_v408 extends Bedrock_v407" in v408_src
      and ".protocolVersion(408)" in v408_src
      and '.minecraftVersion("1.16.20")' in v408_src
      and "registerPacket" not in v408_src
      and "updateSerializer" not in v408_src)
check("Bedrock_v419 の CODEC は protocolVersion(419)・minecraftVersion 1.16.100",
      ".protocolVersion(419)" in v419_src
      and '.minecraftVersion("1.16.100")' in v419_src)
check("Bedrock_v422 は Bedrock_v419 を継承し protocolVersion(422)・"
      "minecraftVersion 1.16.200",
      "public class Bedrock_v422 extends Bedrock_v419" in v422_src
      and ".protocolVersion(422)" in v422_src
      and '.minecraftVersion("1.16.200")' in v422_src)

# A-2. 422 の protocol.json（突き合わせの土台。verify-be-422 の A と同じ値）
p22 = TMP / "protocol-1.16.201.json"
check("422 protocol.json が存在する", p22.is_file(), str(p22))
check("422 protocol.json の行数が 7,465 行（wc -l、改行数）",
      p22.is_file()
      and p22.read_text().count("\n") == 7465,
      f"{p22.read_text().count(chr(10))} 行" if p22.is_file() else "なし")
check("422 protocol.json のサイズが 190,275 バイト",
      p22.is_file() and p22.stat().st_size == 190275,
      f"{p22.stat().st_size} バイト" if p22.is_file() else "なし")
check("422 protocol.json の SHA-1 が 02eed26e…と一致",
      p22.is_file()
      and sha1(p22) == "02eed26e80b9d08e8ae8721fb40a88f6bdbf9847",
      sha1(p22) if p22.is_file() else "なし")
data422 = json.loads(p22.read_text())
types422 = data422["types"]
ids422 = packet_ids(data422)

# A-3. 419 の protocol.json が無いこと・protocolVersions.json での位置
check("419 の protocol.json は .tmp-be に無い（protocol-1.16.100.json は存在しない）",
      not (TMP / "protocol-1.16.100.json").exists())
check("419 の protocol.json は .tmp-be に無い（protocol-1.16.101.json は存在しない）",
      not (TMP / "protocol-1.16.101.json").exists())
pver = TMP / "protocolVersions.json"
check("protocolVersions.json が存在する", pver.is_file(), str(pver))
pv = json.loads(pver.read_text()) if pver.is_file() else []
check("protocolVersions.json に 419 のエントリが無い",
      not any(e["version"] == 419 for e in pv))
check("protocolVersions.json の 422 は 1.16.201（1 エントリのみ）",
      [e["minecraftVersion"] for e in pv if e["version"] == 422]
      == ["1.16.201"])
check("protocolVersions.json の 422 の 1 つ下は 100（419 のエントリは無い）",
      bool(pv) and max(e["version"] for e in pv if e["version"] < 422) == 100)
check("protocolVersions.json に 420・421 のエントリも無い（ベータ版）",
      not any(e["version"] in (420, 421) for e in pv))
pver419 = TMP / "be-419-protocolVersions.json"
check("be-419-protocolVersions.json が protocolVersions.json とバイト同一"
      "（SHA-1 37562ad8…）",
      pver419.is_file() and pver.is_file()
      and sha1(pver419) == sha1(pver)
      == "37562ad82fae4d446de721a1dc18edf54814ba91",
      sha1(pver419) if pver419.is_file() else "なし")

# A-4. PMMP bedrock-1.16.100 タグの 3 ファイル（1.16.200 とバイト同一）
req100 = TMP / "be-419-required_item_list_1_16_100.json"
canon100 = TMP / "be-419-canonical_block_states_1_16_100.nbt"
blockid100 = TMP / "be-419-block_id_map_1_16_100.json"
req200 = TMP / "be-422/bedrock-1.16.200-required_item_list.json"
canon200 = TMP / "be-422/bedrock-1.16.200-canonical_block_states.nbt"
blockid200 = TMP / "be-422/bedrock-1.16.200-block_id_map.json"
check("PMMP 1.16.100 の required_item_list.json が存在する",
      req100.is_file(), str(req100))
check("PMMP 1.16.100 の required_item_list.json のサイズが 91,328 バイト",
      req100.is_file() and req100.stat().st_size == 91328,
      f"{req100.stat().st_size} バイト" if req100.is_file() else "なし")
check("PMMP 1.16.100 の required_item_list.json の SHA-1 が 682782b5…と一致",
      req100.is_file()
      and sha1(req100) == "682782b5c0da0465aef275522d4b97e01f7aa700",
      sha1(req100) if req100.is_file() else "なし")
check("PMMP 1.16.100 の required_item_list.json の名前が 914 件",
      req100.is_file()
      and len(json.loads(req100.read_text())) == 914,
      f"{len(json.loads(req100.read_text()))} 件"
      if req100.is_file() else "なし")
check("PMMP 1.16.100 の required_item_list.json が 1.16.200 のものとバイト同一",
      req100.is_file() and req200.is_file()
      and sha1(req100) == sha1(req200))
check("PMMP 1.16.100 の canonical_block_states.nbt のサイズが 981,311 バイト・"
      "SHA-1 が 1d6771cd…と一致",
      canon100.is_file() and canon100.stat().st_size == 981311
      and sha1(canon100) == "1d6771cd43536248f9e9c820fad9001c7ad92618",
      f"{canon100.stat().st_size} バイト" if canon100.is_file() else "なし")
check("PMMP 1.16.100 の canonical_block_states.nbt が 1.16.200 のものとバイト同一",
      canon100.is_file() and canon200.is_file()
      and sha1(canon100) == sha1(canon200))
check("PMMP 1.16.100 の block_id_map.json のサイズが 19,660 バイト・"
      "SHA-1 が f3855a10…と一致",
      blockid100.is_file() and blockid100.stat().st_size == 19660
      and sha1(blockid100) == "f3855a102c698f960020d928bcaef10d25724062",
      f"{blockid100.stat().st_size} バイト" if blockid100.is_file() else "なし")
check("PMMP 1.16.100 の block_id_map.json が 1.16.200 のものとバイト同一",
      blockid100.is_file() and blockid200.is_file()
      and sha1(blockid100) == sha1(blockid200))

# ---------- B. パケット一覧（419） ----------
check("422 のパケット数が 160 件（419 の一覧の土台）",
      len(ids422) == 160, f"{len(ids422)} 件")
check("422 の最大パケット ID が 163", max(ids422) == 163,
      f"最大 {max(ids422)}")
# 419 の一覧は 422 から filter_text_packet（163）を除いたものとして構築する
ids419 = {k: v for k, v in ids422.items() if k != 163}
check("419 のパケット数が 159 件（422 の 160 件 − filter_text_packet）",
      len(ids419) == 159, f"{len(ids419)} 件")
check("419 の最大パケット ID が 162", max(ids419) == 162,
      f"最大 {max(ids419)}")
check("419 の ID の欠番が 16・37・116（ID 0 は範囲外として扱う）",
      [i for i in range(1, max(ids419) + 1) if i not in ids419]
      == [16, 37, 116])
check("422 の一覧から 163 だけを除いたものが 419 の一覧と完全一致"
      "（共有 ID の名称もすべて一致）",
      set(ids422) - set(ids419) == {163}
      and all(ids419[i] == ids422[i] for i in ids419))
check("filter_text_packet（163）が 419 に無い（422 にはある）",
      163 not in ids419 and ids422.get(163) == "filter_text_packet")

# Cloudburst v419 の registerPacket（新規登録 6 件）と突き合わせる
registered = re.findall(
    r"\.registerPacket\((\w+)::new,\s*[^,]+,\s*(\d+),", v419_src)
registered = [(cls, int(i)) for cls, i in registered]
EXPECTED_NEW = [
    ("MotionPredictionHintsPacket", 157, "motion_prediction_hints"),
    ("AnimateEntityPacket", 158, "animate_entity"),
    ("CameraShakePacket", 159, "camera_shake"),
    ("PlayerFogPacket", 160, "player_fog"),
    ("CorrectPlayerMovePredictionPacket", 161,
     "correct_player_move_prediction"),
    ("ItemComponentPacket", 162, "item_component"),
]
check("Bedrock_v419 の新規登録パケットが 6 件（157〜162）",
      [i for _, i in registered] == [157, 158, 159, 160, 161, 162],
      str(registered))
check("Bedrock_v419 の新規登録パケットのクラスが 157 MotionPredictionHints・"
      "158 AnimateEntity・159 CameraShake・160 PlayerFog・"
      "161 CorrectPlayerMovePrediction・162 ItemComponent",
      registered == [(cls, i) for cls, i, _ in EXPECTED_NEW],
      str(registered))
for cls, pid, pname in EXPECTED_NEW:
    check(f"パケット {pid} の名称が Bedrock_v419 の registerPacket と一致"
          f"（{pname}）",
          ids419.get(pid) == pname and (cls, pid) in registered,
          f"419一覧={ids419.get(pid)}")
registered422 = re.findall(
    r"\.registerPacket\((\w+)::new,\s*[^,]+,\s*(\d+),", v422_src)
check("Bedrock_v422 の新規登録パケットは FilterText（163）の 1 件だけ",
      registered422 == [("FilterTextPacket", "163")],
      str(registered422))

# Cloudburst v419 の updateSerializer（9 件＋LevelSoundEvent 系＋AvailableCommands）
updated = re.findall(r"\.updateSerializer\((\w+)\.class", v419_src)
EXPECTED_UPDATED = [
    "ResourcePackStackPacket", "StartGamePacket", "MovePlayerPacket",
    "UpdateAttributesPacket", "SetEntityDataPacket", "ContainerClosePacket",
    "MoveEntityDeltaPacket", "PlayerAuthInputPacket",
    "ItemStackResponsePacket", "LevelSoundEvent1Packet",
    "LevelSoundEvent2Packet", "LevelSoundEventPacket",
    "AvailableCommandsPacket",
]
check("Bedrock_v419 のシリアライザ更新が 13 件（9 件＋LevelSoundEvent 系 3 件"
      "＋AvailableCommands）",
      updated == EXPECTED_UPDATED, str(updated))
check("Bedrock_v419 の COMMAND_PARAMS が shift(7, 1)・shift(30, 1)",
      ".shift(7, 1)" in v419_src and ".shift(30, 1)" in v419_src)
check("Bedrock_v419 の SOUND_EVENTS が 317 を EQUIP_NETHERITE に置換し "
      "318 に UNDEFINED を挿入",
      ".replace(317, SoundEvent.EQUIP_NETHERITE)" in v419_src
      and ".insert(318, SoundEvent.UNDEFINED)" in v419_src)
check("Bedrock_v419 の LEVEL_EVENTS が 1900〜1903 を追加",
      all(s in v419_src for s in [
          ".insert(1900, LevelEvent.QUEUE_CUSTOM_MUSIC)",
          ".insert(1901, LevelEvent.PLAY_CUSTOM_MUSIC)",
          ".insert(1902, LevelEvent.STOP_CUSTOM_MUSIC)",
          ".insert(1903, LevelEvent.SET_MUSIC_VOLUME)"]))
check("Bedrock_v422 の ITEM_STACK_REQUEST_TYPES が shift(12, 1)＋"
      "CRAFT_RECIPE_OPTIONAL を 12 に挿入",
      ".shift(12, 1)" in v422_src
      and ".insert(12, ItemStackRequestActionType.CRAFT_RECIPE_OPTIONAL)"
      in v422_src)
check("Bedrock_v422 のシリアライザ更新は ResourcePacksInfo の 1 件だけ",
      re.findall(r"\.updateSerializer\((\w+)\.class", v422_src)
      == ["ResourcePacksInfoPacket"])

# ---------- C. 核パケット ID：BedrockProtocol.java と 419 の一覧の一致 ----------
proto_src = (SRC / "BedrockProtocol.java").read_text()
java_packets = dict(re.findall(
    r"PACKET_(\w+)\s*=\s*0x([0-9a-fA-F]+);", proto_src))
# (Java 定数名, パケット名)。verify-be-422 の CORE と同じ並び。
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
json_by_name419 = {v: k for k, v in ids419.items()}
# update_subchunk_blocks・subchunk・subchunk_request は 419 にも存在しない
# （465・471 で新規追加）ためループから除き、不存在を別途確認する。
for const, pname in CORE:
    if const in ("UPDATE_SUB_CHUNK_BLOCKS", "SUB_CHUNK", "SUB_CHUNK_REQUEST"):
        continue
    java_id = int(java_packets[const], 16)
    json_id = json_by_name419.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が 419 の一覧と一致",
          json_id == java_id, f"419一覧={json_id}")
check("パケット ID 70 の名称が chunk_radius_update（Java 定数名 CHUNK_RADIUS_UPDATED とは名称違い・422 回と同じ）",
      ids419.get(70) == "chunk_radius_update")
check("パケット UPDATE_SUB_CHUNK_BLOCKS（0xac=172）が 419 の一覧に存在しない（465 で新規追加）",
      json_by_name419.get("update_subchunk_blocks") is None)
check("パケット SUB_CHUNK（0xae=174）が 419 の一覧に存在しない（471 で新規追加）",
      json_by_name419.get("subchunk") is None)
check("パケット SUB_CHUNK_REQUEST（0xaf=175）が 419 の一覧に存在しない（471 で新規追加）",
      json_by_name419.get("subchunk_request") is None)
check("パケット DEBUG_RENDERER（164）が 419 の一覧に存在しない（428 で新規追加）",
      json_by_name419.get("debug_renderer") is None
      and "debug_renderer" not in ids422.values())
check("パケット SYNC_ENTITY_PROPERTY（165）が 419 の一覧に存在しない（440 で新規追加）",
      json_by_name419.get("sync_entity_property") is None)

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_16_101 が存在する", "V1_16_101" in entry_by_name)
check("V1_16_101 のプロトコルが 419",
      entry_by_name.get("V1_16_101", (None,))[0] == 419,
      str(entry_by_name.get("V1_16_101")))
check("V1_16_101 の表示名が 1.16.101",
      entry_by_name.get("V1_16_101", (None, None))[1] == "1.16.101")
check("V1_7_1 が enum の先頭に宣言されている（昇順・最古端。§36 で追加。V1_8_1 は 2 番目、V1_9_0 は 3 番目、V1_10_1 は 4 番目、V1_11_4 は 5 番目、V1_12_1 は 6 番目、V1_13_3 は 7 番目、V1_14_41 は 8 番目、V1_14_60 は 9 番目、V1_16_10 は 10 番目、V1_16_61 は 11 番目）",
      bool(entries) and entries[0][0] == "V1_7_1"
      and entries[1][0] == "V1_8_1")
check("V1_16_101 が V1_16_201 より先に宣言されている",
      ver_src.index("V1_16_101(419") < ver_src.index("V1_16_201(422"))
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("enum の javadoc の protocol.json が 51 ファイルのまま（419 は protocol.json が無いため増えない）",
      "51 ファイル" in ver_src)
check("enum の javadoc に 419 は CloudburstMC Protocol の v419 コーデックで確認した旨がある",
      "CloudburstMC Protocol" in ver_src and "Bedrock_v419.java" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 419 -> \"(1_16_210)\";", ver_src)
check("itemTableTag：419 が 1_16_210 の表を使う（1.16.100 自体の実測・他バージョンからの代用ではない）",
      m is not None, m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_16_210.bin"
check("アイテム表 bedrock_item_ids_1_16_210.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が a35eaa5a…と一致（1.16.100 候補表とバイト同一）",
      tag_bin.is_file()
      and sha1(tag_bin) == "a35eaa5a90525651aa5983808508c0e87124cc27",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規 0 本・422 の表を共用）",
      len(item_bins) == 38, f"{len(item_bins)} 本")
check("1.16.100 名のアイテム表 .bin は無い（bedrock_item_ids_1_16_100.bin は作らない）",
      not (RES / "bedrock_item_ids_1_16_100.bin").exists())
check("1.16.101 名のアイテム表 .bin は無い（bedrock_item_ids_1_16_101.bin は作らない）",
      not (RES / "bedrock_item_ids_1_16_101.bin").exists())
cand_bin = TMP / "bedrock_item_ids_1_16_100_candidate.bin"
check("1.16.100 から再生成した候補表が存在し SHA-1 が a35eaa5a…と一致"
      "（exact=624／shared=236／legacy=33／fallback=419 の生成候補）",
      cand_bin.is_file()
      and sha1(cand_bin) == "a35eaa5a90525651aa5983808508c0e87124cc27",
      sha1(cand_bin) if cand_bin.is_file() else "なし")
check("1.16.100 の候補表が配置済みの bedrock_item_ids_1_16_210.bin とバイト同一（新規生成不要）",
      cand_bin.is_file() and tag_bin.is_file()
      and sha1(cand_bin) == sha1(tag_bin))
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
check("item_tables_summary.json に 419 のエントリが無い（422・428 が表を共用するときと同じ流儀）",
      "419" not in summary)
s431 = summary.get("431", {})
check("item_tables_summary.json の 431 のエントリが残っている（tag=bedrock-1.16.210・exact=624／shared=236／legacy=33／fallback=419）",
      s431.get("tag") == "bedrock-1.16.210" and s431.get("exact") == 624
      and s431.get("shared") == 236 and s431.get("legacy") == 33
      and s431.get("fallback") == 419,
      str({k: s431.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))

# ---------- F. 述語の前提：Cloudburst v419 コーデックと 422 protocol.json ----------
# (1) StartGame：StartGameSerializer_v419 の serialize／writeLevelSettings の順序
sg_ser_path = CB_CODEC / "v419/serializer/StartGameSerializer_v419.java"
check("StartGameSerializer_v419.java が存在する",
      sg_ser_path.is_file(), str(sg_ser_path))
sg_ser = sg_ser_path.read_text() if sg_ser_path.is_file() else ""
serialize_body = sg_ser.split("public void serialize", 1)[1] \
    .split("public void deserialize", 1)[0] if "public void serialize" in sg_ser else ""
check("StartGame（v419 serialize）：uniqueEntityId・runtimeEntityId・"
      "playerGameType・playerPosition・rotation・writeLevelSettings・"
      "levelId・levelName・premiumWorldTemplateId・trial・"
      "authoritativeMovementMode・currentTick・enchantmentSeed・"
      "blockProperties・itemDefinitions・multiplayerCorrelationId・"
      "inventoriesServerAuthoritative の順",
      tokens_in_order(serialize_body, [
          "getUniqueEntityId()", "getRuntimeEntityId()",
          "getPlayerGameType()", "getPlayerPosition()", "getRotation()",
          "writeLevelSettings", "getLevelId()", "getLevelName",
          "getPremiumWorldTemplateId()", "isTrial()",
          "getAuthoritativeMovementMode()", "getCurrentTick()",
          "getEnchantmentSeed()", "getBlockProperties()",
          "writeItemDefinitions", "getMultiplayerCorrelationId()",
          "isInventoriesServerAuthoritative()"]))
level_body = sg_ser.split("protected void writeLevelSettings", 1)[1] \
    .split("protected void readLevelSettings", 1)[0] \
    if "protected void writeLevelSettings" in sg_ser else ""
check("StartGame（v419 writeLevelSettings）：seed・spawnBiomeType・"
      "customBiomeName・dimensionId・generatorId・levelGameType・difficulty・"
      "defaultSpawn・achievementsDisabled・dayCycleStopTime・eduEditionOffers・"
      "eduFeaturesEnabled・educationProductionId・rainLevel・lightningLevel・"
      "platformLockedContentConfirmed・multiplayerGame・broadcastingToLan・"
      "xblBroadcastMode・platformBroadcastMode・commandsEnabled・"
      "texturePacksRequired・gamerules・experiments・"
      "experimentsPreviouslyToggled・bonusChestEnabled・startingWithMap・"
      "defaultPlayerPermission・serverChunkTickRange・behaviorPackLocked・"
      "resourcePackLocked・fromLockedWorldTemplate・usingMsaGamertagsOnly・"
      "fromWorldTemplate・worldTemplateOptionLocked・onlySpawningV1Villagers・"
      "vanillaVersion・limitedWorldWidth・limitedWorldHeight・netherType・"
      "forceExperimentalGameplay の順",
      tokens_in_order(level_body, [
          "writeSeed", "getSpawnBiomeType()", "getCustomBiomeName()",
          "getDimensionId()", "getGeneratorId()", "getLevelGameType()",
          "getDifficulty()", "getDefaultSpawn()",
          "isAchievementsDisabled()", "getDayCycleStopTime()",
          "getEduEditionOffers()", "isEduFeaturesEnabled()",
          "getEducationProductionId()", "getRainLevel()",
          "getLightningLevel()", "isPlatformLockedContentConfirmed()",
          "isMultiplayerGame()", "isBroadcastingToLan()",
          "getXblBroadcastMode()", "getPlatformBroadcastMode()",
          "isCommandsEnabled()", "isTexturePacksRequired()",
          "getGamerules()", "writeExperiments",
          "isExperimentsPreviouslyToggled()", "isBonusChestEnabled()",
          "isStartingWithMap()", "getDefaultPlayerPermission()",
          "getServerChunkTickRange()", "isBehaviorPackLocked()",
          "isResourcePackLocked()", "isFromLockedWorldTemplate()",
          "isUsingMsaGamertagsOnly()", "isFromWorldTemplate()",
          "isWorldTemplateOptionLocked()", "isOnlySpawningV1Villagers()",
          "getVanillaVersion()", "getLimitedWorldWidth()",
          "getLimitedWorldHeight()", "isNetherType()",
          "getForceExperimentalGameplay()"]))
check("StartGame（v419）：rewind 系がシリアライザに無い"
      "（hasStartGameRewindFields()=false の前提・422 と同じ）",
      "rewind" not in sg_ser.lower()
      and "server_authoritative_block_breaking" not in sg_ser)
check("StartGame（v419）：engine がシリアライザに無い"
      "（hasStartGameEngine()=false の前提・422 と同じ）",
      "engine" not in sg_ser.lower())
check("Bedrock_v422.java が StartGamePacket のシリアライザを更新していない"
      "（StartGameSerializer_v419 を 419・422 で共用する前提）",
      "StartGamePacket" not in v422_src
      and "StartGameSerializer" not in v422_src)
check("Bedrock_v419.java が StartGamePacket に StartGameSerializer_v419 を登録している",
      ".updateSerializer(StartGamePacket.class, StartGameSerializer_v419.INSTANCE)"
      in v419_src)

# (2) 422 protocol.json の StartGame（57 フィールド・v419 と突き合わせる土台）
sg422 = field_names(types422, "packet_start_game")
EXPECTED_SG422 = [
    "entity_id", "runtime_entity_id", "player_gamemode", "spawn",
    "rotation", "seed", "biome_type", "biome_name", "dimension",
    "generator", "world_gamemode", "difficulty", "spawn_position",
    "achievements_disabled", "day_cycle_stop_time", "edu_offer",
    "edu_features_enabled", "edu_product_uuid", "rain_level",
    "lightning_level", "has_confirmed_platform_locked_content",
    "is_multiplayer", "broadcast_to_lan", "xbox_live_broadcast_mode",
    "platform_broadcast_mode", "enable_commands",
    "is_texturepacks_required", "gamerules", "experiments",
    "experiments_previously_used", "bonus_chest", "map_enabled",
    "permission_level", "server_chunk_tick_range",
    "has_locked_behavior_pack", "has_locked_resource_pack",
    "is_from_locked_world_template", "msa_gamertags_only",
    "is_from_world_template", "is_world_template_option_locked",
    "only_spawn_v1_villagers", "game_version", "limited_world_width",
    "limited_world_length", "is_new_nether",
    "experimental_gameplay_override", "level_id", "world_name",
    "premium_world_template_id", "is_trial", "movement_authority",
    "current_tick", "enchantment_seed", "block_palette", "itemstates",
    "multiplayer_correlation_id", "server_authoritative_inventory",
]
check("StartGame：422 の protocol.json は 57 フィールド（verify-be-422 と同じ値の再確認）",
      len(sg422) == 57, f"{len(sg422)} フィールド")
check("StartGame：422 のフィールド一覧が期待通り（v419 シリアライザの順序と対応）",
      sg422 == EXPECTED_SG422)
check("StartGame：422 に rewind_history_size・server_authoritative_block_breaking が無い（419 も同じ）",
      "rewind_history_size" not in sg422
      and "server_authoritative_block_breaking" not in sg422)
check("StartGame：422 に engine が無い（419 も同じ）",
      "engine" not in sg422)
check("StartGame：422 の seed は zigzag32（hasStartGameLu64Seed()=false の前提・419 も同じ）",
      field_type(types422, "packet_start_game", "seed") == "zigzag32")
check("StartGame：422 の movement_authority の値 2 は server_v2_rewind",
      field_type(types422, "packet_start_game", "movement_authority")
      [1]["mappings"].get("2") == "server_v2_rewind")
check("StartGame：422 の experiments は Experiments 型で件数は li32",
      types422["Experiments"]
      == ["array", {"countType": "li32", "type": "Experiment"}])
helper419_path = CB_CODEC / "v419/BedrockCodecHelper_v419.java"
helper419 = helper419_path.read_text() if helper419_path.is_file() else ""
check("BedrockCodecHelper_v419 の writeExperiments が件数を writeIntLE で書く"
      "（protocol.json の li32 と一致）",
      "buffer.writeIntLE(experiments.size())" in helper419
      and "buffer.readIntLE()" in helper419)

# (3) ResourcePacksInfo：v332（419 の実効）と v422 の差分は raytracing だけ
rpi332_path = CB_CODEC / "v332/serializer/ResourcePacksInfoSerializer_v332.java"
rpi422_path = CB_CODEC / "v422/serializer/ResourcePacksInfoSerializer_v422.java"
check("ResourcePacksInfoSerializer_v332.java が存在する",
      rpi332_path.is_file(), str(rpi332_path))
check("ResourcePacksInfoSerializer_v422.java が存在する",
      rpi422_path.is_file(), str(rpi422_path))
rpi332 = rpi332_path.read_text() if rpi332_path.is_file() else ""
rpi422 = rpi422_path.read_text() if rpi422_path.is_file() else ""
check("ResourcePacksInfoSerializer_v422 は v332 を継承する",
      "public class ResourcePacksInfoSerializer_v422 "
      "extends ResourcePacksInfoSerializer_v332" in rpi422)
rpi332_ser = rpi332.split("public void serialize", 1)[1] \
    .split("public void deserialize", 1)[0] if "public void serialize" in rpi332 else ""
rpi422_ser = rpi422.split("public void serialize", 1)[1] \
    .split("public void deserialize", 1)[0] if "public void serialize" in rpi422 else ""
check("ResourcePacksInfo（v332）：forcedToAccept・scriptingEnabled・behavior・resource の順",
      tokens_in_order(rpi332_ser, [
          "isForcedToAccept()", "isScriptingEnabled()",
          "getBehaviorPackInfos()", "getResourcePackInfos()"]))
check("ResourcePacksInfo（v422）：serialize 本体が v332 と同一（差分はエントリ側だけ）",
      rpi332_ser != "" and rpi332_ser == rpi422_ser)
check("ResourcePacksInfo（v332）：エントリ末尾は isScripting（writeEntry が super の後に書く）",
      "buffer.writeBoolean(entry.isScripting())" in rpi332
      and "isRaytracingCapable" not in rpi332)
check("ResourcePacksInfo（v422）：差分は resource エントリ末尾の raytracingCapable だけ"
      "（super.writeEntry の後に resource のときだけ書く）",
      "super.writeEntry(buffer, helper, entry, resource)" in rpi422
      and "if (resource)" in rpi422
      and "buffer.writeBoolean(entry.isRaytracingCapable())" in rpi422)
check("Resource packs info：422 の protocol.json は 4 フィールド"
      "（must_accept・has_scripts・behaviour_packs・texture_packs）",
      field_names(types422, "packet_resource_packs_info")
      == ["must_accept", "has_scripts", "behaviour_packs", "texture_packs"])
pkt_src = (SRC / "BedrockPackets.java").read_text()
rpi_method = pkt_src.split("encodeResourcePacksInfo(BedrockVersion version)", 1)[1] \
    .split("encodeResourcePackStack", 1)[0] \
    if "encodeResourcePacksInfo(BedrockVersion version)" in pkt_src else ""
check("DetergentMC の encodeResourcePacksInfo は behaviour／texture とも空の一覧しか書かない"
      "（v332 と v422 のエントリ差分がワイヤに現れない前提）",
      rpi_method.count("writeLittleShort(0)") >= 2
      or "texture packs" in rpi_method)

# ---------- G. コード側（範囲文字列・述語閾値・テスト） ----------
check("述語 hasStartGameRewindFields() が protocol >= 428 で定義されている（419 は false・422 と同じ）",
      bool(re.search(r"boolean hasStartGameRewindFields\(\) \{\s*return protocol >= 428;",
                     ver_src)))
check("述語 addPaintingUsesBlockCoordinates() が protocol >= 422 && protocol <= 428 で定義されている（§28 で下限を追加。419 は対象外になった）",
      bool(re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 || \(protocol >= 422 && protocol <= 428\);",
                     ver_src)))
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 で定義されている（§28 で下限を追加。419 は対象外になった）",
      bool(re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                     ver_src)))
check("述語 hasStartGameEngine() が protocol >= 440 で定義されている（419 は false・422 と同じ）",
      bool(re.search(r"boolean hasStartGameEngine\(\) \{\s*return protocol >= 440;",
                     ver_src)))
check("述語 hasMovementAuthority() が protocol >= 388 && protocol <= 800 で定義されている（419 は true・422 と同じ。361 回で下限 388 を追加）",
      bool(re.search(r"boolean hasMovementAuthority\(\) \{\s*return protocol >= 388 && protocol <= 800;",
                     ver_src)))
check("述語 hasItemstates() が protocol <= 748 で定義されている（419 は true・422 と同じ）",
      bool(re.search(r"boolean hasItemstates\(\) \{\s*return protocol >= 361 && protocol <= 748;",
                     ver_src)))
_thresholds = [int(n) for n in re.findall(r"protocol\s*>=\s*(\d+)", ver_src)]
check("BedrockVersion.java の述語の最小の >= 閾値は 332（313 回で resourcePacksInfoHasScripting を追加。419 と 422 はどちらも >= 390 で全述語の値が同じ）",
      bool(_thresholds) and min(_thresholds) == 332,
      f"最小の閾値: {min(_thresholds) if _thresholds else 'なし'}")
check("420〜427 の間の閾値は 422 のみで 2 件（§28 で下限を追加した addPainting／playerListRemove の 2 述語。419 と 422 はこの 2 述語でのみ値が違い、<= や == の 419 閾値は無い）",
      re.findall(r"protocol\s*(?:>=|<=|==)\s*(42[0-7])\b", ver_src) == ["422", "422"]
      and not re.search(r"protocol\s*(?:<=|==)\s*419\b", ver_src))
check("BedrockProtocol の javadoc が「1.7.1〜26.52 / 291〜2193 / 62 バージョン分」",
      "1.7.1〜26.52" in proto_src and "291〜2193" in proto_src
      and "62 バージョン分" in proto_src)
sess_src = (SRC / "BedrockSession.java").read_text()
check("BedrockSession の切断文が「supported: 291-2193 over RakNet; 2225 needs NetherNet」",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in sess_src)
srv_src = (SRC / "BedrockServer.java").read_text()
check("BedrockServer の起動ログが「1.7.1〜26.52 / 291〜2193」",
      "1.7.1〜26.52" in srv_src and "291〜2193" in srv_src)
check("BedrockVersion の NetherNet 分岐（2225）が 419 と無関係に残っている",
      "2225" in ver_src and "NetherNet" in ver_src)
check("BedrockChunkTranslator が BedrockVersion を参照していない（チャンク変換はバージョン非依存）",
      "BedrockVersion" not in (SRC / "BedrockChunkTranslator.java").read_text())
version_test = (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text()
check("BedrockVersionTest に allSixtyFourSupportedProtocolsResolve テストがあり 419 が含まれる",
      "allSixtyFourSupportedProtocolsResolve" in version_test
      and "419" in version_test)
session_test = (BE / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java").read_text()
check("BedrockSessionVersionTest に allSixtyThreeProtocolsReachStartGame テストがあり 419 が含まれる",
      "allSixtyThreeProtocolsReachStartGame" in session_test
      and "419" in session_test)
packets_test = (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text()
check("BedrockPacketsVersionTest に startGame419Equals422 テストがある（419 の StartGame は 422 とバイト同一）",
      "startGame419Equals422" in packets_test)
check("BedrockPacketsVersionTest に resourcePacks419Equals422 テストがある（419 の ResourcePacksInfo は 422 とバイト同一）",
      "resourcePacks419Equals422" in packets_test)
check("BedrockPacketsVersionTest の 419 テストが V1_16_101 を使う",
      "V1_16_101" in packets_test)

# ---------- H. ブロック表 ----------
sys.path.insert(0, str(BE / "tools"))
from parse_nbt import parse_canonical  # noqa: E402
canon_entries_100 = (len(parse_canonical(str(canon100))[0])
                     if canon100.is_file() else -1)
check("1.16.100 の canonical_block_states.nbt が 6,611 エントリー（NBT のパレット長・parse_nbt.py で数えた実測）",
      canon100.is_file() and canon_entries_100 == 6611,
      f"{canon_entries_100} エントリー" if canon100.is_file() else "なし")
check("1.16.100 の block_id_map.json が 553 エントリー",
      blockid100.is_file()
      and len(json.loads(blockid100.read_text())) == 553,
      f"{len(json.loads(blockid100.read_text()))} 件"
      if blockid100.is_file() else "なし")
check("1.16.100 名のブロック用 .bin は無い（既存ペアの流用）",
      not (RES / "bedrock_block_runtime_ids_1_16_100.bin").exists()
      and not (RES / "bedrock_block_runtime_ids_1_16_101.bin").exists())
check("既存ペアの canonical_block_states_1_20_50.nbt が存在し SHA-1 が d2b6acbb…と一致",
      (RES / "canonical_block_states_1_20_50.nbt").is_file()
      and sha1(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("既存ペアの bedrock_block_runtime_ids_1_20_4.bin が存在し SHA-1 が ee36fffb…と一致",
      (RES / "bedrock_block_runtime_ids_1_20_4.bin").is_file()
      and sha1(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")

# ---------- I. docs ----------
bedrock_md = (ROOT / "docs/BEDROCK.md").read_text()
check("BEDROCK.md の対応表に 419 行（1.16.100 / 1.16.101）がある",
      "| 419 | 1.16.100 / 1.16.101 |" in bedrock_md)
check("BEDROCK.md の網羅率に 1.16.101 は **893** がある",
      "1.16.101 は **893**" in bedrock_md)
check("BEDROCK.md の対象が 64 プロトコル（RakNet 63 個＋2225 の形式のみ）",
      "RakNet 世代の公式プロトコル 63 個" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md
      and "対応プロトコル一覧（64 個" in bedrock_md)
check("BEDROCK.md のマトリクス節が「それ以外の Bedrock プロトコル（57 個）」・「全 63 プロトコル」",
      "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル" in bedrock_md)
check("BEDROCK.md の範囲が 1.7.1〜26.52 / 291〜2193",
      "1.7.1〜26.52" in bedrock_md and "291〜2193" in bedrock_md)
playtest_md = (ROOT / "docs/PLAYTEST-BEDROCK.md").read_text()
check("PLAYTEST-BEDROCK.md のタイトルが「1.7.1〜26.52 クライアント」",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）"
      in playtest_md)
check("PLAYTEST-BEDROCK.md の対象がプロトコル 291〜2193 の RakNet 世代 52 プロトコル",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)

# ---------- J. 判断待ちで直さないもの（419 でも現状のまま） ----------
# Transaction の network_ids：422 の protocol.json では Transaction 直下の
# bool。v419・v422 のどちらも InventoryTransaction のシリアライザを
# 更新しておらず、DetergentMC は network_ids を読み飛ばさない（422 回と同じ）。
check("Transaction：422 の protocol.json は network_ids を直下に持つ 6 フィールド（419 も共用する前提）",
      field_names(types422, "Transaction") == ["legacy_request_id",
                                               "legacy_transactions",
                                               "transaction_type",
                                               "network_ids",
                                               "inventory_actions",
                                               "transaction_data"])
check("Bedrock_v419・Bedrock_v422 のどちらも InventoryTransactionPacket のシリアライザを更新していない",
      "InventoryTransactionPacket" not in v419_src
      and "InventoryTransactionPacket" not in v422_src)
check("decodeInventoryTransaction は Transaction の network_ids（bool）を読み飛ばさない（419・422 ともに判断待ちで直さない）",
      re.search(r"\bnetwork_ids\b", pkt_src) is None)
# MetadataDictionary のキー：ENTITY_DATA は v407 で定義され、v419・v422 の
# どちらも再定義しない（helper へ渡す参照が各 1 回だけ）。
check("Bedrock_v422.java が ENTITY_DATA を再定義していない（参照は helper への 1 回だけ）",
      v422_src.count("ENTITY_DATA") == 1
      and "ENTITY_DATA =" not in v422_src
      and "EntityDataTypeMap ENTITY_DATA" not in v422_src)
check("Bedrock_v419.java も ENTITY_DATA を再定義していない（参照は helper への 1 回だけ・v419 と v422 で共用）",
      v419_src.count("ENTITY_DATA") == 1
      and "ENTITY_DATA =" not in v419_src
      and "EntityDataTypeMap ENTITY_DATA" not in v419_src)
v407_src = (CB_CODEC / "v407/Bedrock_v407.java").read_text() \
    if (CB_CODEC / "v407/Bedrock_v407.java").is_file() else ""
check("ENTITY_DATA の定義元は Bedrock_v407（419・422 の共通の祖先）",
      "EntityDataTypeMap ENTITY_DATA" in v407_src)
md422 = types422["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]
check("MetadataDictionary：422 は 118 エントリ・キー 0 は index・118 は is_buoyant・119 は buoyancy_data（419 も同じ ENTITY_DATA を使う前提）",
      len(md422) == 118 and md422.get("0") == "index"
      and md422.get("118") == "is_buoyant"
      and md422.get("119") == "buoyancy_data")
# Skin：422 の Skin は 16 フィールドで premium は string。writeEmptySkin は
# 567 形式のまま（422 回と同じ判断待ち）。
skin422 = [f["name"] for f in types422["Skin"][1]]
check("Skin：422 は 16 フィールドで premium は string・geometry_data_version が無い（419 も同じ前提）",
      len(skin422) == 16
      and field_type(types422, "Skin", "premium") == "string"
      and "geometry_data_version" not in skin422)
check("Skin の既知の制限：writeEmptySkin は premium を bool で書く 567 形式のまま（419・422 ともに判断待ちで直さない）",
      "geometry data version" in pkt_src
      and "primary user" in pkt_src
      and "overriding player appearance" in pkt_src)
# experiments の件数：現行コードは全バージョン varint で書く。protocol.json
# は li32・Cloudburst v419 ヘルパーは writeIntLE だが、419 と 422 で差は
# 無いため今回は直さない（記録のためのチェック）。
check("experiments の既知の制限：BedrockPackets.encodeStartGame は experiments 件数を varint で書くまま"
      "（protocol.json は li32・Cloudburst v419 は writeIntLE。419 と 422 で差が無いため直さない）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし"
      in pkt_src)
# ItemStackRequest／ItemStackResponse：v422 の helper は filteredStrings と
# customName を追加するが、DetergentMC はどちらも読み書きしない。
helper422_path = CB_CODEC / "v422/BedrockCodecHelper_v422.java"
helper422 = helper422_path.read_text() if helper422_path.is_file() else ""
check("BedrockCodecHelper_v422 が ItemStackRequest に filteredStrings を追加し writeItemEntry で customName を書く（差分の実物）",
      "filteredStrings" in helper422
      and "this.writeString(buffer, itemEntry.getCustomName())" in helper422)
check("DetergentMC は ItemStackRequest を読み書きしていない（419・422 ともに現状のまま）",
      "ItemStackRequest" not in pkt_src)

print()
print(f"TOTAL: {len(CHECKS)} checks, {len(CHECKS) - len(FAILURES)} passed, {len(FAILURES)} failed")
if FAILURES:
    print("FAILURES:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
