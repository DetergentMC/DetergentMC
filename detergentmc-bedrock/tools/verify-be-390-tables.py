#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BE 390（Bedrock 1.14.60。単独バージョンで共有なし）の対応表・コード・
文書が実測と一致するか検証する。

出典（BE-NOTES.md §28）:
- 419／408／407 の回（§25〜§27）と同じ特別フロー。minecraft-data に
  1.14 系の protocol.json は無い：data/bedrock/{1.13.0, 1.14.0, 1.14.20,
  1.14.30, 1.14.60}/protocol.json はいずれも HTTP 404（390 回に curl で
  確認。対照の 1.16.201 は 200）。protocolVersions.json（58 エントリ）
  にも 390 のエントリは無く、422 未満のエントリは 100・82・70 のみ。
  したがって 390 のパケット形式の根拠は CloudburstMC Protocol
  （`.tmp-be/cloudburst-protocol/`、ブランチ `3.0`、コミット
  `7163a6aedaefbc1d188431ef90ebc703b1138340`）の v390 コーデック
  （Bedrock_v390.java そのもの）。
- 共有バージョンは Wiki 個別ページの infobox で 390 回に確認：
  1.14.60 は 390 単独。1.14.0／1.14.30 は 389（描画済みページで確認）、
  1.14.1／1.14.20／1.14.21／1.14.41 も 389（描画済み HTML の infobox 行
  で確認）。「Bedrock Edition 1.14.50」というページは存在しない。
  Cloudburst VERSIONS.md の対応は Bedrock_v388＝1.13.0、
  Bedrock_v389＝1.14.0 - 1.14.50、Bedrock_v390＝1.14.60、
  Bedrock_v407＝1.16.0 - 1.16.10。
- 391〜406 は Wiki の Protocol version 一覧表でいずれもベータ／開発版
  の番号（391〜396＝1.15.0 系ベータ、400〜406＝1.16.0 系ベータ）。
  397〜399 は一覧表に現れず個別には未確認。391／392 は同じ一覧表に
  Education Edition の番号としても現れる（別エディション）。
- v407 は v390 に対して registerPacket 12 件（ID 145〜156）・
  deregisterPacket 1 件（VideoStreamConnect）・updateSerializer 14 件。
  DetergentMC の送受信範囲でワイヤ形式が変わるのは StartGame
  （LevelSettings 32→39 フィールド＋末尾の server_authoritative_inventory）、
  InventoryTransaction（legacy_request_id の追加）、PlayerList の
  remove（v390 の serializer は uuid のみ。protocol.json 系譜の
  422／428 と食い違う）、AddPainting（v361 serializer が vec3f。
  protocol.json の 422／428 と食い違う）の 4 点。差分の実測メモは
  `.tmp-be/be-390-cloudburst-diff.md`。
- パケット一覧は 2 つの母集団がある：DetergentMC の従来方式
  （protocol.json 由来）では 141 件・最大 ID 144・欠番 [16, 37, 116]、
  Cloudburst の登録ベースでは 140 件・最大 ID 144・欠番 [16, 116, 127, 128]。
  違いは 37（EntityFall は Cloudburst に実在するが protocol.json 一覧に
  無い）と 127・128（protocol.json 一覧に在るが Cloudburst では
  コメントのみで未登録）の 3 ID だけ。126（VideoStreamConnect）は
  390 では両方に在り、v407 で Cloudburst が登録解除したもの。
- アイテム表は代用：PMMP BedrockData に bedrock-1.14.60 タグは無い。
  bedrock-1.14.0 タグはツリー 14 エントリの旧形式のみ
  （item_id_map.json 702 件・block_id_map.json 469 件・
  required_block_states.nbt 3,129 件。1.16.0 の同名ファイルとも中身が
  違う）でランタイム ID の実データは無いため、表は
  bedrock_item_ids_1_16_210.bin をそのまま使い、代用である旨を明記する。

実行: python3 detergentmc-bedrock/tools/verify-be-390-tables.py
"""

import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent  # リポジトリ直下
MOD = ROOT / "detergentmc-bedrock"
SRC = MOD / "src/main/java/io/detergentmc/bedrock"
RES = MOD / "src/main/resources/io/detergentmc/bedrock"
TMP = ROOT / ".tmp-be"  # protocol.json 等の調査用一時ファイル（リポジトリ直下）
CB = TMP / "cloudburst-protocol/bedrock-codec/src/main/java/org/cloudburstmc/protocol/bedrock/codec"

passed = 0
failed = 0
failures = []


def check(name, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  OK  {name}")
    else:
        failed += 1
        failures.append(name)
        print(f"  NG  {name} {detail}")


def sha1_of(path):
    return hashlib.sha1(Path(path).read_bytes()).hexdigest()


def read(path):
    return Path(path).read_text(encoding="utf-8")


# ---------------------------------------------------------------------------
print("== A. 出典の同一性（Cloudburst v390 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v390＝1.14.60",
      "| Bedrock_v390  |       1.14.60       |" in versions_md)
check("VERSIONS.md で Bedrock_v389＝1.14.0 - 1.14.50",
      "| Bedrock_v389  |  1.14.0 - 1.14.50   |" in versions_md)
check("VERSIONS.md で Bedrock_v388＝1.13.0",
      "| Bedrock_v388  |       1.13.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v407＝1.16.0 - 1.16.10",
      "| Bedrock_v407  |  1.16.0 - 1.16.10   |" in versions_md)

v390_src = read(CB / "v390/Bedrock_v390.java")
check("Bedrock_v390.java の CODEC は protocolVersion(390) と minecraftVersion(\"1.14.60\") を持つ",
      "protocolVersion(390)" in v390_src and 'minecraftVersion("1.14.60")' in v390_src)
check("Bedrock_v390.java の CODEC は Bedrock_v389.CODEC を土台にする",
      "Bedrock_v389.CODEC.toBuilder()" in v390_src)
check("Bedrock_v390.java は helper に BedrockCodecHelper_v390 を使う",
      "new BedrockCodecHelper_v390(ENTITY_DATA, GAME_RULE_TYPES)" in v390_src)
check("Bedrock_v390.java の updateSerializer は PlayerList と PlayerSkin の 2 件",
      "updateSerializer(PlayerListPacket.class, PlayerListSerializer_v390.INSTANCE)" in v390_src
      and "updateSerializer(PlayerSkinPacket.class, PlayerSkinSerializer_v390.INSTANCE)" in v390_src)

v407_src = read(CB / "v407/Bedrock_v407.java")
check("Bedrock_v407.java の CODEC は Bedrock_v390.CODEC を土台にする",
      "Bedrock_v390.CODEC.toBuilder()" in v407_src)
registered407 = re.findall(r"registerPacket\((\w+Packet)::new, [\w.]+INSTANCE, (\d+),", v407_src)
check("Bedrock_v407.java の registerPacket は新規 12 件（ID 145〜156。ソースに ID 明記）",
      registered407 == [("CreativeContentPacket", "145"),
                        ("PlayerEnchantOptionsPacket", "146"),
                        ("ItemStackRequestPacket", "147"),
                        ("ItemStackResponsePacket", "148"),
                        ("PlayerArmorDamagePacket", "149"),
                        ("CodeBuilderPacket", "150"),
                        ("UpdatePlayerGameTypePacket", "151"),
                        ("EmoteListPacket", "152"),
                        ("PositionTrackingDBServerBroadcastPacket", "153"),
                        ("PositionTrackingDBClientRequestPacket", "154"),
                        ("DebugInfoPacket", "155"),
                        ("PacketViolationWarningPacket", "156")],
      f"実際は {registered407}")
check("Bedrock_v407.java の deregisterPacket は VideoStreamConnectPacket の 1 件",
      re.findall(r"deregisterPacket\((\w+Packet)\.class", v407_src)
      == ["VideoStreamConnectPacket"])
upd407 = len(re.findall(r"updateSerializer\(", v407_src))
check("Bedrock_v407.java の updateSerializer は 14 件",
      upd407 == 14, f"実際は {upd407}")

for name in ("1.13.0", "1.14.0", "1.14.20", "1.14.30", "1.14.60"):
    check(f"390 の protocol.json は .tmp-be に無い（protocol-{name}.json は存在しない）",
          not (TMP / f"protocol-{name}.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 390 のエントリは無い",
      390 not in pv_protocols)
below422 = sorted(p for p in pv_protocols if p < 422)
check("protocolVersions.json で 422 未満のエントリは 100・82・70 のみ（390 は載っていない）",
      below422 == [70, 82, 100], f"実際は {below422}")

# A-2. PMMP bedrock-1.14.0 タグ（390 と同じ 1.14 世代のデータ。旧形式のみ）
tree = json.loads(read(TMP / "be-390/bedrock-1.14.0-tree.json"))
tree_paths = [t["path"] for t in tree["tree"]]
check("PMMP bedrock-1.14.0 タグのツリーは 14 エントリ",
      len(tree_paths) == 14, f"実際は {len(tree_paths)}")
check("bedrock-1.14.0 タグに required_item_list.json は無い（390 のランタイム ID 実データは存在しない）",
      "required_item_list.json" not in tree_paths)
check("bedrock-1.14.0 タグに canonical_block_states.nbt は無い（旧形式の required_block_states.nbt のみ）",
      "canonical_block_states.nbt" not in tree_paths
      and "required_block_states.nbt" in tree_paths)

iim14 = TMP / "be-390/item_id_map.json"
check("PMMP bedrock-1.14.0 の item_id_map.json は 702 件・SHA-1 dac102a3…",
      len(json.loads(read(iim14))) == 702
      and sha1_of(iim14) == "dac102a30f7ed1a565d01f6a78cf65382c6e140c")
bim14 = TMP / "be-390/block_id_map.json"
check("PMMP bedrock-1.14.0 の block_id_map.json は 469 件・SHA-1 b5af45a4…",
      len(json.loads(read(bim14))) == 469
      and sha1_of(bim14) == "b5af45a4c1affdc4c86c9d650a3a163ff04a0e27")
check("1.14.0 の item_id_map は 1.16.0 のものと中身が違う（702 件 vs 807 件。世代が違う裏付け）",
      len(json.loads(read(TMP / "be-408/item_id_map_1.16.0.json"))) == 807)

# ---------------------------------------------------------------------------
print("== B. 390 のパケット一覧（407 の一覧 − v407 の新規登録 12 件） ==")

p422 = json.loads(read(TMP / "protocol-1.16.201.json"))
mappings = p422["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
ids422 = {int(k): v for k, v in mappings.items()}
check("422 のパケット一覧は 160 件（土台の再確認）", len(ids422) == 160)

ids407 = {i: n for i, n in ids422.items()
          if i not in {157, 158, 159, 160, 161, 162} and i != 163}
check("407 のパケット一覧は 153 件（§27 の再確認）", len(ids407) == 153)

new407 = {145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156}
check("v407 の新規 12 件は 145〜156（creative_content〜packet_violation_warning）",
      all(ids422[i].startswith(n) for i, n in zip(
          sorted(new407),
          ["creative_content", "player_enchant_options", "item_stack_request",
           "item_stack_response", "player_armor_damage", "code_builder",
           "update_player_game_type", "emote_list", "position_tracking_db_broadcast",
           "position_tracking_db_request", "debug_info", "packet_violation_warning"])))
ids390 = {i: n for i, n in ids407.items() if i not in new407}
check("390 のパケット一覧は 141 件（protocol.json 由来の母集団）",
      len(ids390) == 141, f"実際は {len(ids390)}")
check("390 の最大 ID は 144（player_auth_input。v388 世代で登録済みで 390 にも在る）",
      max(ids390) == 144 and ids390[144].startswith("player_auth_input"))
check("390 の欠番は [16, 37, 116]（407・408・419・422 と同じ。ID は 1 始まり）",
      [i for i in range(1, 145) if i not in ids390] == [16, 37, 116])
# Cloudburst の登録ベースでは 140 件・最大 ID 144・欠番 [16, 116, 127, 128]
# になる。違いは 37（EntityFall は Cloudburst に実在するが protocol.json
# 一覧に無い）と 127・128（protocol.json 一覧に在るが Cloudburst では
# v361 のコメントのみで未登録）の 3 ID だけ。126（video_stream_connect）は
# 390 では両方に在る（BE-NOTES.md §28、実測メモ
# `.tmp-be/be-390-cloudburst-diff.md` §1）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 390 の一覧に含まれる ==")

proto_src = read(SRC / "BedrockProtocol.java")


def const_value(name):
    m = re.search(rf"public static final int {name} = (0x[0-9a-fA-F]+|\d+);", proto_src)
    return int(m.group(1), 0) if m else None


CORE = [
    ("PACKET_LOGIN", "login"),
    ("PACKET_PLAY_STATUS", "play_status"),
    ("PACKET_RESOURCE_PACKS_INFO", "resource_packs_info"),
    ("PACKET_RESOURCE_PACK_STACK", "resource_pack_stack"),
    ("PACKET_RESOURCE_PACK_CLIENT_RESPONSE", "resource_pack_client_response"),
    ("PACKET_START_GAME", "start_game"),
    ("PACKET_TEXT", "text"),
    ("PACKET_MOVE_PLAYER", "move_player"),
    ("PACKET_PLAYER_AUTH_INPUT", "player_auth_input"),
    ("PACKET_LEVEL_CHUNK", "level_chunk"),
    ("PACKET_ADD_ENTITY", "add_entity"),
    ("PACKET_ADD_PLAYER", "add_player"),
    ("PACKET_REMOVE_ENTITY", "remove_entity"),
    ("PACKET_MOVE_ENTITY", "move_entity"),
    ("PACKET_MOVE_ENTITY_DELTA", "move_entity_delta"),
    ("PACKET_SET_ENTITY_DATA", "set_entity_data"),
    ("PACKET_SET_ENTITY_MOTION", "set_entity_motion"),
    ("PACKET_UPDATE_ATTRIBUTES", "update_attributes"),
    ("PACKET_PLAYER_LIST", "player_list"),
    ("PACKET_DISCONNECT", "disconnect"),
    ("ID_BATCH", None),  # バッチは一覧に無い（0xfe 固定）
]
for const_name, packet_name in CORE:
    value = const_value(const_name)
    if packet_name is None:
        check(f"{const_name} = 0xfe（バッチ）", value == 0xFE, f"実際は {value}")
    else:
        check(f"{const_name}（{value}）が 390 の一覧の {packet_name} と一致",
              value in ids390 and ids390[value].startswith(packet_name),
              f"一覧では {ids390.get(value)}")

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（390 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_14_60(390, \"1.14.60\") がある",
      'V1_14_60(390, "1.14.60")' in ver_src)
check("V1_14_60 は enum の先頭（V1_16_10 より前）",
      ver_src.index("V1_14_60(390") < ver_src.index("V1_16_10(407"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("クラス javadoc の protocol.json ファイル数は 51 のまま（390 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 390 は 1.14.60 単独（他は 389）と Wiki infobox の出典がある",
      "Protocol version 390" in ver_src
      and "「Bedrock Edition 1.14.50」の個別ページは存在しない" in ver_src)
check("enum コメントに 391〜406 はベータ番号・397〜399 は未確認とある",
      "397〜399 は表に無く未確認" in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 390 -> \"1_16_210\" がある",
      'case 390 -> "1_16_210";' in ver_src)
check("case 390 のコメントに「390 自体の実測ではない」の代用明記がある",
      "390 自体の実測ではない" in ver_src)
check("itemTableTag の case 407／408／419／422／428／431 は不変",
      'case 407 -> "1_16_210";' in ver_src and 'case 408 -> "1_16_210";' in ver_src
      and 'case 419 -> "1_16_210";' in ver_src and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（407・408・419・422 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（390 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("390 用の .bin（1_14_60／1_14_0）は作っていない",
      not any(b in ("bedrock_item_ids_1_14_60.bin", "bedrock_item_ids_1_14_0.bin")
              for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 390 のエントリは無い（407・408・419・422 と同じ扱い）",
      "390" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 390 世代の形式を確認） ==")

sg388 = read(CB / "v388/serializer/StartGameSerializer_v388.java")
sg407 = read(CB / "v407/serializer/StartGameSerializer_v407.java")
check("StartGameSerializer_v388 に spawnBiomeType／customBiomeName は無い（390 の LevelSettings は 32 フィールド）",
      "SpawnBiomeType" not in sg388 and "CustomBiomeName" not in sg388)
check("StartGameSerializer_v407 は seed の直後に spawnBiomeType（ShortLE）と customBiomeName を書く",
      "buffer.writeShortLE(packet.getSpawnBiomeType().ordinal())" in sg407
      and "helper.writeString(buffer, packet.getCustomBiomeName())" in sg407)
check("StartGameSerializer_v388 に educationProductionId／limitedWorldWidth／inventoriesServerAuthoritative は無い",
      "EducationProductionId" not in sg388 and "LimitedWorldWidth" not in sg388
      and "InventoriesServerAuthoritative" not in sg388)
check("StartGameSerializer_v407 は educationProductionId・limitedWorldWidth・末尾の inventoriesServerAuthoritative を書く",
      "helper.writeString(buffer, packet.getEducationProductionId())" in sg407
      and "buffer.writeIntLE(packet.getLimitedWorldWidth())" in sg407
      and "buffer.writeBoolean(packet.isInventoriesServerAuthoritative())" in sg407)
sg332 = read(CB / "v332/serializer/StartGameSerializer_v332.java")
check("StartGameSerializer_v332 は eduEditionOffers を Boolean で書く（390 の実効形式。値 0 は VarInt の 0 とバイト同一）",
      "buffer.writeBoolean(packet.getEduEditionOffers() != 0)" in sg332)
check("StartGameSerializer_v407 は eduEditionOffers を VarInt で書く",
      "VarInts.writeInt(buffer, packet.getEduEditionOffers())" in sg407)
check("StartGameSerializer_v388 はブロックパレットを helper.writeTag（単一 NBT タグ）で書く（407 と同じ）",
      "helper.writeTag(buffer, packet.getBlockPalette())" in sg388)

it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
it407 = read(CB / "v407/serializer/InventoryTransactionSerializer_v407.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（390 は transaction_type から始まる）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)
check("InventoryTransactionSerializer_v407 は legacyRequestId を書く",
      "legacyRequestId" in it407)

pl390 = read(CB / "v390/serializer/PlayerListSerializer_v390.java")
check("PlayerListSerializer_v390 の add は末尾に trustedSkin のブロックを書く",
      "buffer.writeBoolean(entry.isTrustedSkin())" in pl390)
check("PlayerListSerializer_v390 の remove は uuid のみでエントリを作る（verified は読まない）",
      "packet.getEntries().add(new Entry(helper.readUuid(buffer)))" in pl390)
check("v407 ディレクトリに PlayerList の serializer は無い（407 の実効形式も v390 の serializer）",
      not (CB / "v407/serializer/PlayerListSerializer_v407.java").exists())

ap361 = read(CB / "v361/serializer/AddPaintingSerializer_v361.java")
ap332 = read(CB / "v332/serializer/AddPaintingSerializer_v332.java")
check("AddPaintingSerializer_v361 は座標を writeVector3f で書く（390・407 の実効形式）",
      "helper.writeVector3f(buffer, packet.getPosition())" in ap361)
check("AddPaintingSerializer_v332 は座標を writeBlockPosition で書く（旧形式。390 では実効でない）",
      "helper.writeBlockPosition(buffer, packet.getPosition().toInt())" in ap332)

rps388 = read(CB / "v388/serializer/ResourcePackStackSerializer_v388.java")
check("ResourcePackStackSerializer_v388 は v313 を継承し末尾に gameVersion を足す（390 の実効形式。407 と同じ）",
      "extends ResourcePackStackSerializer_v313" in rps388
      and "helper.writeString(buffer, packet.getGameVersion())" in rps388)

for name in ("MovePlayer", "SetEntityData", "UpdateAttributes"):
    s291 = read(CB / f"v291/serializer/{name}Serializer_v291.java")
    check(f"{name}Serializer_v291 に tick は無い（390 の実効形式。407 と同じ）",
          "getTick" not in s291)

med291 = read(CB / "v291/serializer/MoveEntityDeltaSerializer_v291.java")
check("MoveEntityDeltaSerializer_v291 は位置を VarInts.readInt の差分値として読む（390 も旧形式）",
      "packet.setDeltaX(VarInts.readInt(buffer))" in med291)

# F-2. PMMP required_block_states.nbt のコンテナ形式（旧形式パレットの裏付け。
# bedrock-1.14.0 タグは 390 と同じ 1.14 世代のデータ）
data = (TMP / "be-390/required_block_states.nbt").read_bytes()
pos = 0


def read_uvarint(buf, p):
    value = 0
    shift = 0
    while True:
        b = buf[p]
        p += 1
        value |= (b & 0x7F) << shift
        if not (b & 0x80):
            return value, p
        shift += 7


def read_zigzag(buf, p):
    raw, p = read_uvarint(buf, p)
    return (raw >> 1) ^ -(raw & 1), p


t0 = data[pos]
pos += 1
name_len, pos = read_zigzag(data, pos)
elem_type = data[pos]
pos += 1
count, pos = read_zigzag(data, pos)
check("required_block_states.nbt のルートは名前なし TAG_List・要素型 0x0A・件数 3,129（zigzag varint）",
      t0 == 0x09 and name_len == 0 and elem_type == 0x0A and count == 3129,
      f"実際は type={t0} name_len={name_len} elem={elem_type} count={count}")
check("required_block_states.nbt は 302,699 バイト・SHA-1 b3cbee89…",
      len(data) == 302699
      and sha1_of(TMP / "be-390/required_block_states.nbt")
      == "b3cbee89bc00280a3a4193d5a7c92c6311171ced")

# ---------------------------------------------------------------------------
print("== G. コード側の実装（述語・分岐・範囲表記・テスト名） ==")

for pred in ("hasStartGameExperiments", "hasStartGameBlockProperties",
             "hasResourcePackStackExperiments", "hasMovePlayerTick",
             "hasSetEntityDataTick", "hasUpdateAttributesTick",
             "moveEntityDeltaUsesAbsolutePosition"):
    check(f"述語 {pred}() が protocol >= 419 で定義されている",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol >= 419;", ver_src)
          is not None)
for pred in ("hasStartGame407Fields", "transactionHasLegacyRequestId"):
    check(f"述語 {pred}() が protocol >= 407 で定義されている（§28 で新規）",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol >= 407;", ver_src)
          is not None)
gte = sorted({int(n) for n in re.findall(r"protocol >= (\d+)", ver_src)})
check("BedrockVersion の最小の >= 閾値は 332（313 回で resourcePacksInfoHasScripting を追加。361 回で 388 境界の述語を追加）",
      gte[0] == 332, f"実際は {gte[0]}")
check("391〜406 の間に >= の閾値は無い（389 と 407 の間で違う述語は 390 境界の playerListAddHasVerified と 407 境界の 2 件だけの前提）",
      all(not (391 <= t <= 406) for t in gte), f"閾値は {gte}")
check("述語 addPaintingUsesBlockCoordinates() が protocol >= 422 && protocol <= 428 で定義されている（§28 で下限を追加）",
      re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 || \(protocol >= 422 && protocol <= 428\);",
                ver_src) is not None)
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 で定義されている（§28 で下限を追加）",
      re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                ver_src) is not None)

pkt_src = read(SRC / "BedrockPackets.java")
check("encodeStartGame が v407 フィールド群を hasStartGame407Fields でゲートしている（5 箇所）",
      pkt_src.count("version.hasStartGame407Fields()") == 5,
      f"実際は {pkt_src.count('version.hasStartGame407Fields()')} 箇所")
check("InventoryTransaction の復号が legacy 部分を transactionHasLegacyRequestId でゲートしている（2 箇所）",
      pkt_src.count("version.transactionHasLegacyRequestId()") == 2,
      f"実際は {pkt_src.count('version.transactionHasLegacyRequestId()')} 箇所")
check("BedrockPackets に skipTransactionActionV0（v291 世代の基本形式の読み飛ばし）がある",
      "skipTransactionActionV0" in pkt_src)
check("encodeMoveEntityDelta は FloatLE 形式のまま（390 ではセッションが使わない）",
      "out.writeLittleFloat(x == null ? 0 : x)" in pkt_src)

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession が 390 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
      "bedrockVersion.moveEntityDeltaUsesAbsolutePosition()" in ses_src
      and "sendEntityMoveDelta" in ses_src)
check("BedrockSession の切断メッセージが 291-2193 になっている",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in ses_src)

proto_javadoc = read(SRC / "BedrockProtocol.java")
check("BedrockProtocol の javadoc が 1.7.1〜26.52／291〜2193／62 バージョン分になっている",
      "Bedrock 1.7.1〜26.52（プロトコル 291〜2193" in proto_javadoc
      and "protocol.json（62 バージョン分）" in proto_javadoc
      and "419・408・407・390・389・388・361・354・340・332・313・291 は" in proto_javadoc)
srv_src = read(SRC / "BedrockServer.java")
check("BedrockServer の起動ログが 1.7.1〜26.52／291〜2193 になっている",
      "対象 Bedrock 1.7.1〜26.52 / プロトコル 291〜2193（RakNet）" in srv_src)

vt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java")
check("BedrockVersionTest の対応表テストが allSixtyFourSupportedProtocolsResolve に改名されている",
      "allSixtyFourSupportedProtocolsResolve" in vt_src
      and "allSixtyTwoSupportedProtocolsResolve" not in vt_src)
check("BedrockVersionTest に protocol390BoundaryGates がある",
      "protocol390BoundaryGates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "allSixtyOneProtocolsReachStartGame" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame390Omits407Fields", "resourcePacks390Equals407",
              "tickFields390Omitted", "playerListRemove390HasNoVerified",
              "decodeInventoryTransactionV0", "decodeInventoryTransactionV0WithAction",
              "decodeInventoryTransactionV0As407IsNull"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)
set_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionEntityTest.java")
check("BedrockSessionEntityTest に entityMove390UsesMoveEntityNotDelta がある",
      "entityMove390UsesMoveEntityNotDelta" in set_src)

# ---------------------------------------------------------------------------
print("== H. ブロック表（不変の確認） ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 が d2b6acbb…のまま",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 が ee36fffb…のまま",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
check("1_14 系のブロック .bin は作っていない",
      not list(RES.glob("bedrock_block_runtime_ids_1_14_*.bin")))

# ---------------------------------------------------------------------------
print("== I. 文書（BEDROCK.md／PLAYTEST-BEDROCK.md） ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、55 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 56 個見出しで、390 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 390 | 1.14.60 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 55 プロトコル／49 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407・390 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に 390 の StartGame（v407 追加分が無い）がある",
      "1.14.60（390）の StartGame はさらに v407 追加分" in bedrock_md
      and "LevelSettings は 32 フィールド（407 は 39 フィールド）" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の vec3f（390〜419）と protocol.json との食い違いがある",
      "（1.12.1〜1.16.101 と 1.16.220 以降）が vec3f" in bedrock_md)
check("BEDROCK.md の境界表に PlayerList の verified は 422・428 の remove のみとある",
      "verified は 1.16.201／1.16.210 では remove でも存在する（records_count 個の bool 配列）" in bedrock_md)
check("BEDROCK.md の境界表に InventoryTransaction の 390（legacy_request_id 無し）がある",
      "1.14.60（390）には legacy_request_id が無く" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 63 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md のアイテム表が 38 本のバイナリになっている",
      "38 本のバイナリ" in bedrock_md)
check("BEDROCK.md の網羅率に 1.14.60 の代用明記つきエントリがある",
      "1.14.60 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "390 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 63 プロトコルの対応表／31 形状／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "31 形状を最後まで読み直して確認" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／55 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.14.60 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・1.10.1・1.11.4・1.12.1・1.13.3・1.14.41・1.14.60・1.16.10・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（390 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（390 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（390 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の shift は 390 でも無関係）",
      "AvailableCommands" not in pkt_src)
v389_src = read(CB / "v389/Bedrock_v389.java")
check("ENTITY_DATA は Bedrock_v389 で定義され 390 が継承する（390 の世代の定義）",
      "EntityDataTypeMap ENTITY_DATA" in v389_src)

# ---------------------------------------------------------------------------
print()
print(f"結果: {passed} 件 OK / {failed} 件 NG（全 {passed + failed} 件）")
if failures:
    print("失敗したチェック:")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
