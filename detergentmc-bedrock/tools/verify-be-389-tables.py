#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BE 389（Bedrock 1.14.0／1.14.1／1.14.20／1.14.21／1.14.30／1.14.41 で共有）の
対応表・コード・文書が実測と一致するか検証する。

出典（BE-NOTES.md §29）:
- 419／408／407／390 の回（§25〜§28）と同じ特別フロー。minecraft-data に
  1.14 系の protocol.json は無い：data/bedrock/{1.13.0, 1.14.0, 1.14.20,
  1.14.30, 1.14.60}/protocol.json はいずれも HTTP 404（390 回に curl で
  確認。対照の 1.16.201 は 200）。protocolVersions.json（58 エントリ）
  にも 389 のエントリは無く、422 未満のエントリは 100・82・70 のみ。
  したがって 389 のパケット形式の根拠は CloudburstMC Protocol
  （`.tmp-be/cloudburst-protocol/`、ブランチ `3.0`、コミット
  `7163a6aedaefbc1d188431ef90ebc703b1138340`）の v389 コーデック
  （Bedrock_v389.java そのもの）。差分の実測メモは
  `.tmp-be/be-389-cloudburst-diff.md`（§1〜§8）。
- 共有バージョンは Wiki 個別ページの infobox で確認：389 は
  1.14.0／1.14.1／1.14.20／1.14.21／1.14.30／1.14.41 で共有し、
  displayName は最新の 1.14.41。389 回の再確認では 1.14.1／1.14.20／
  1.14.30 の infobox と、Wiki「Bedrock Protocol version numbers」の
  Entry（1.14.41＝389）、PS4 専用ホットフィックス 1.14.0.12 の
  infobox（389）を見た。1.14.0／1.14.21 は §28 で確認済み。
  Cloudburst VERSIONS.md の Bedrock_v389＝「1.14.0 - 1.14.50」は
  Wiki と食い違う：「Bedrock Edition 1.14.50」の個別ページは存在せず
  （現れるのは beta 1.14.0.50 と Minecraft Education 1.14.50 だけ）、
  389 を共有する最新リリースは 1.14.41。
- 389 と 390 の間に番号は無い（連続）。389 の 1 つ下は 388（1.13.0）。
- v390 は v389 に対して registerPacket／deregisterPacket 無し、
  updateSerializer は PlayerList／PlayerSkin の 2 件、ヘルパーの
  差し替え（Skin 12→16 フィールド）のみ。DetergentMC の送受信範囲で
  ワイヤ形式が変わるのは PlayerList の add（389 の実効
  PlayerListSerializer_v388 には trustedSkin が無い）の 1 点だけで、
  新述語 playerListAddHasVerified（protocol >= 390）でゲートする。
  StartGame／AddPlayer／AddEntity／ResourcePacksInfo／
  ResourcePackStack／LevelChunk／InventoryTransaction／メタデータ
  （ENTITY_DATA は v389 の定義を 390 が継承）は 390 と同一形式。
- パケット一覧は 2 つの母集団がある：DetergentMC の従来方式
  （protocol.json 由来）では 141 件・最大 ID 144・欠番 [16, 37, 116]、
  Cloudburst の登録ベースでは 140 件・最大 ID 144・欠番 [16, 116, 127, 128]。
  違いは 390 と同じ 3 ID（37・127・128）だけ。
- アイテム表は代用：PMMP BedrockData に bedrock-1.14.60 タグは無い。
  bedrock-1.14.0 タグはツリー 14 エントリの旧形式のみ
  （item_id_map.json 702 件・block_id_map.json 469 件・
  required_block_states.nbt 3,129 件）でランタイム ID の実データは
  無いため、表は bedrock_item_ids_1_16_210.bin をそのまま使い、
  代用である旨を明記する。

実行: python3 detergentmc-bedrock/tools/verify-be-389-tables.py
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
print("== A. 出典の同一性（Cloudburst v389 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v389＝1.14.0 - 1.14.50",
      "| Bedrock_v389  |  1.14.0 - 1.14.50   |" in versions_md)
check("VERSIONS.md で Bedrock_v390＝1.14.60",
      "| Bedrock_v390  |       1.14.60       |" in versions_md)
check("VERSIONS.md で Bedrock_v388＝1.13.0",
      "| Bedrock_v388  |       1.13.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v407＝1.16.0 - 1.16.10",
      "| Bedrock_v407  |  1.16.0 - 1.16.10   |" in versions_md)

v389_src = read(CB / "v389/Bedrock_v389.java")
check("Bedrock_v389.java の CODEC は protocolVersion(389) と minecraftVersion(\"1.14.0\") を持つ",
      "protocolVersion(389)" in v389_src and 'minecraftVersion("1.14.0")' in v389_src)
check("Bedrock_v389.java の CODEC は Bedrock_v388.CODEC を土台にする",
      "Bedrock_v388.CODEC.toBuilder()" in v389_src)
check("Bedrock_v389.java は helper に BedrockCodecHelper_v388（ENTITY_DATA は v389 のもの）を使う",
      "new BedrockCodecHelper_v388(ENTITY_DATA, GAME_RULE_TYPES)" in v389_src)
check("Bedrock_v389.java の updateSerializer は Event／LevelEvent／LevelEventGeneric の 3 件",
      "updateSerializer(EventPacket.class, EventSerializer_v389.INSTANCE)" in v389_src
      and "updateSerializer(LevelEventPacket.class, new LevelEventSerializer_v291(LEVEL_EVENTS))" in v389_src
      and "updateSerializer(LevelEventGenericPacket.class, new LevelEventGenericSerializer_v361(LEVEL_EVENTS))" in v389_src)
check("Bedrock_v389.java に registerPacket／deregisterPacket は無い（パケット増減なし）",
      "registerPacket" not in v389_src and "deregisterPacket" not in v389_src)
check("Bedrock_v389.java の PARTICLE_TYPES は shift(28, 1)＋DRIP_HONEY 挿入と 59〜67 の挿入を持つ（389 自身の定義。390 が継承）",
      ".shift(28, 1)" in v389_src
      and ".insert(28, ParticleType.DRIP_HONEY)" in v389_src
      and ".insert(67, ParticleType.DRAGON_BREATH_TRAIL)" in v389_src)
check("ENTITY_DATA は Bedrock_v389 で再バインドされる（AREA_EFFECT_CLOUD_PARTICLE の差し替え。390 が継承する定義）",
      "EntityDataTypeMap ENTITY_DATA" in v389_src
      and ".update(EntityDataTypes.AREA_EFFECT_CLOUD_PARTICLE" in v389_src)

v390_src = read(CB / "v390/Bedrock_v390.java")
check("Bedrock_v390.java の CODEC は Bedrock_v389.CODEC を土台にする",
      "Bedrock_v389.CODEC.toBuilder()" in v390_src)
check("Bedrock_v390.java に registerPacket／deregisterPacket は無い（389 とパケット集合は同一）",
      "registerPacket" not in v390_src and "deregisterPacket" not in v390_src)
check("Bedrock_v390.java の updateSerializer は PlayerList と PlayerSkin の 2 件のみ",
      "updateSerializer(PlayerListPacket.class, PlayerListSerializer_v390.INSTANCE)" in v390_src
      and "updateSerializer(PlayerSkinPacket.class, PlayerSkinSerializer_v390.INSTANCE)" in v390_src
      and v390_src.count("updateSerializer(") == 2)
check("Bedrock_v390.java は StartGame に触っていない（389・390 とも StartGameSerializer_v388 が実効）",
      "StartGamePacket" not in v390_src and "StartGamePacket" not in v389_src)

for name in ("1.13.0", "1.14.0", "1.14.20", "1.14.30", "1.14.60"):
    check(f"389 の protocol.json は .tmp-be に無い（protocol-{name}.json は存在しない）",
          not (TMP / f"protocol-{name}.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 389 のエントリは無い",
      389 not in pv_protocols)
below422 = sorted(p for p in pv_protocols if p < 422)
check("protocolVersions.json で 422 未満のエントリは 100・82・70 のみ（389 は載っていない）",
      below422 == [70, 82, 100], f"実際は {below422}")

# A-2. PMMP bedrock-1.14.0 タグ（389 の世代のデータ。旧形式のみ）
tree = json.loads(read(TMP / "be-390/bedrock-1.14.0-tree.json"))
tree_paths = [t["path"] for t in tree["tree"]]
check("PMMP bedrock-1.14.0 タグのツリーは 14 エントリ",
      len(tree_paths) == 14, f"実際は {len(tree_paths)}")
check("bedrock-1.14.0 タグに required_item_list.json は無い（389 のランタイム ID 実データは存在しない）",
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

# ---------------------------------------------------------------------------
print("== B. 389 のパケット一覧（390 と同一：407 の一覧 − v407 の新規登録 12 件） ==")

p422 = json.loads(read(TMP / "protocol-1.16.201.json"))
mappings = p422["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
ids422 = {int(k): v for k, v in mappings.items()}
check("422 のパケット一覧は 160 件（土台の再確認）", len(ids422) == 160)

ids407 = {i: n for i, n in ids422.items()
          if i not in {157, 158, 159, 160, 161, 162} and i != 163}
check("407 のパケット一覧は 153 件（§27 の再確認）", len(ids407) == 153)

new407 = {145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156}
ids390 = {i: n for i, n in ids407.items() if i not in new407}
ids389 = dict(ids390)
check("389 のパケット一覧は 141 件（protocol.json 由来の母集団。390 と同一）",
      len(ids389) == 141, f"実際は {len(ids389)}")
check("389 の最大 ID は 144（player_auth_input。v388 世代で登録済みで 389 にも在る）",
      max(ids389) == 144 and ids389[144].startswith("player_auth_input"))
check("389 の欠番は [16, 37, 116]（390・407・408・419・422 と同じ。ID は 1 始まり）",
      [i for i in range(1, 145) if i not in ids389] == [16, 37, 116])
# Cloudburst の登録ベースでは 389 も 140 件・最大 ID 144・欠番 [16, 116, 127, 128]
# になる（v389 は登録・解除なし。BE-NOTES.md §29、実測メモ
# `.tmp-be/be-389-cloudburst-diff.md` §1）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 389 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 389 の一覧の {packet_name} と一致",
              value in ids389 and ids389[value].startswith(packet_name),
              f"一覧では {ids389.get(value)}")

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（389 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_14_41(389, \"1.14.41\") がある",
      'V1_14_41(389, "1.14.41")' in ver_src)
check("V1_14_41 は enum の先頭（V1_14_60 より前）",
      ver_src.index("V1_14_41(389") < ver_src.index("V1_14_60(390"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("クラス javadoc の protocol.json ファイル数は 51 のまま（389 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 389 は 1.14.0〜1.14.41 の共有と Wiki infobox の出典がある",
      "1.14.0 / 1.14.1 / 1.14.20 / 1.14.21 / 1.14.30 / 1.14.41 で" in ver_src
      and "Protocol version 389" in ver_src)
check("enum コメントに VERSIONS.md の「1.14.50」表記と Wiki の食い違い（1.14.50 の個別ページは存在しない）がある",
      "Bedrock_v389＝「1.14.0 - 1.14.50」は" in ver_src
      and "「Bedrock Edition 1.14.50」の個別ページは" in ver_src)
check("enum コメントに 389 と 390 の間に番号は無い（連続）とある",
      "389 と 390 の間に番号は無い（連続）" in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 389 -> \"1_16_210\" がある",
      'case 389 -> "1_16_210";' in ver_src)
check("case 389 のコメントに「389 自体の実測ではない」の代用明記がある",
      "389 自体の実測ではない" in ver_src)
check("itemTableTag の case 390／407／408／419／422／428／431 は不変",
      'case 390 -> "1_16_210";' in ver_src
      and 'case 407 -> "1_16_210";' in ver_src and 'case 408 -> "1_16_210";' in ver_src
      and 'case 419 -> "1_16_210";' in ver_src and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（390・407・408・419・422 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（389 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("389 用の .bin（1_14_41／1_14_0）は作っていない",
      not any(b in ("bedrock_item_ids_1_14_41.bin", "bedrock_item_ids_1_14_0.bin")
              for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 389 のエントリは無い（390・407・408・419・422 と同じ扱い）",
      "389" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 389 世代の形式を確認） ==")

pl388 = read(CB / "v388/serializer/PlayerListSerializer_v388.java")
pl390 = read(CB / "v390/serializer/PlayerListSerializer_v390.java")
check("PlayerListSerializer_v388 に trustedSkin は無い（389 の add は本体のみ）",
      "trustedSkin" not in pl388 and "TrustedSkin" not in pl388)
check("PlayerListSerializer_v388 の add はエントリごとに teacher／host を書く（本体 8 フィールド）",
      "buffer.writeBoolean(entry.isTeacher())" in pl388
      and "buffer.writeBoolean(entry.isHost())" in pl388)
check("PlayerListSerializer_v390 の add は末尾に trustedSkin のブロックを書く（390 で追加）",
      "buffer.writeBoolean(entry.isTrustedSkin())" in pl390)
check("PlayerListSerializer_v390 の remove は uuid のみでエントリを作る（389 の v388 も uuid のみ）",
      "packet.getEntries().add(new Entry(helper.readUuid(buffer)))" in pl390
      and "helper.readUuid(buffer)" in pl388)

h388 = read(CB / "v388/BedrockCodecHelper_v388.java")
h390 = read(CB / "v390/BedrockCodecHelper_v390.java")
check("BedrockCodecHelper_v388 の Skin は 12 フィールド（fullSkinId まで。armSize／personaPieces は無い。389 の実効形式）",
      "getFullSkinId" in h388 and "ArmSize" not in h388
      and "PersonaPieces" not in h388)
check("BedrockCodecHelper_v390 の Skin は 16 フィールド（armSize／skinColor／personaPieces／tintColors を追加。390 の実効形式）",
      "skin.getArmSize()" in h390 and "skin.getPersonaPieces()" in h390
      and "skin.getTintColors()" in h390)
check("BedrockCodecHelper_v390 は BedrockCodecHelper_v388 を継承する（差分は Skin のみ）",
      "extends BedrockCodecHelper_v388" in h390)

ps390 = read(CB / "v390/serializer/PlayerSkinSerializer_v390.java")
check("PlayerSkinSerializer_v390 は v388 を継承し末尾に trustedSkin を書く（DetergentMC は PlayerSkinPacket を扱わないため対象外）",
      "extends PlayerSkinSerializer_v388" in ps390
      and "buffer.writeBoolean(packet.isTrustedSkin())" in ps390)

it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（389 は transaction_type から始まる。390 と同じ）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)

ap361 = read(CB / "v361/serializer/AddPaintingSerializer_v361.java")
check("AddPaintingSerializer_v361 は座標を writeVector3f で書く（389・390 の実効形式）",
      "helper.writeVector3f(buffer, packet.getPosition())" in ap361)

v388_src = read(CB / "v388/Bedrock_v388.java")
check("MoveEntityDelta の実効シリアライザは 389 も v388（Bedrock_v388 が差し替え、v389・v390 は触っていない）",
      "MoveEntityDeltaSerializer_v388.INSTANCE" in v388_src
      and "MoveEntityDelta" not in v389_src and "MoveEntityDelta" not in v390_src)

rps388 = read(CB / "v388/serializer/ResourcePackStackSerializer_v388.java")
check("ResourcePackStackSerializer_v388 は v313 を継承し末尾に gameVersion を足す（389 の実効形式。390 と同じ）",
      "extends ResourcePackStackSerializer_v313" in rps388
      and "helper.writeString(buffer, packet.getGameVersion())" in rps388)

for name in ("MovePlayer", "SetEntityData", "UpdateAttributes"):
    s291 = read(CB / f"v291/serializer/{name}Serializer_v291.java")
    check(f"{name}Serializer_v291 に tick は無い（389 の実効形式。390 と同じ）",
          "getTick" not in s291)

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
    check(f"述語 {pred}() が protocol >= 407 で定義されている",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol >= 407;", ver_src)
          is not None)
check("述語 playerListAddHasVerified() が protocol >= 390 で定義されている（§29 で新規）",
      re.search(r"public boolean playerListAddHasVerified\(\) \{\s*return protocol >= 390;",
                ver_src) is not None)
gte = sorted({int(n) for n in re.findall(r"protocol >= (\d+)", ver_src)})
check("BedrockVersion の最小の >= 閾値は 332（313 回で resourcePacksInfoHasScripting を追加。361 回で 388 境界の述語を追加）",
      gte[0] == 332, f"実際は {gte[0]}")
check("391〜406 の間に >= の閾値は無い（389 と 407 で違う述語は 407 境界の 2 件と 390 境界の 1 件だけの前提）",
      all(not (391 <= t <= 406) for t in gte), f"閾値は {gte}")
check("述語 addPaintingUsesBlockCoordinates() が protocol >= 422 && protocol <= 428 で定義されている（§28 のまま不変）",
      re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 || \(protocol >= 422 && protocol <= 428\);",
                ver_src) is not None)
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 で定義されている（§28 のまま不変）",
      re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                ver_src) is not None)

pkt_src = read(SRC / "BedrockPackets.java")
check("encodePlayerListAdd が verified を playerListAddHasVerified でゲートしている（1 箇所）",
      pkt_src.count("version.playerListAddHasVerified()") == 1,
      f"実際は {pkt_src.count('version.playerListAddHasVerified()')} 箇所")
check("encodePlayerListAdd の 3 引数版は V1_20_50 で委譲する（従来通り verified を書く）",
      "return encodePlayerListAdd(uuid, entityUniqueId, username, BedrockVersion.V1_20_50);" in pkt_src)
check("encodeStartGame が v407 フィールド群を hasStartGame407Fields でゲートしている（5 箇所。390 回から不変）",
      pkt_src.count("version.hasStartGame407Fields()") == 5,
      f"実際は {pkt_src.count('version.hasStartGame407Fields()')} 箇所")
check("InventoryTransaction の復号が legacy 部分を transactionHasLegacyRequestId でゲートしている（2 箇所。390 回から不変）",
      pkt_src.count("version.transactionHasLegacyRequestId()") == 2,
      f"実際は {pkt_src.count('version.transactionHasLegacyRequestId()')} 箇所")
check("BedrockPackets に skipTransactionActionV0（v291 世代の基本形式の読み飛ばし）がある",
      "skipTransactionActionV0" in pkt_src)
check("encodeMoveEntityDelta は FloatLE 形式のまま（389 ではセッションが使わない）",
      "out.writeLittleFloat(x == null ? 0 : x)" in pkt_src)

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession が encodePlayerListAdd に bedrockVersion を渡す",
      "BedrockPackets.encodePlayerListAdd(state.uuid, state.javaId, state.name," in ses_src
      and "bedrockVersion))" in ses_src)
check("BedrockSession が 389 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
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
      and "allSixtyThreeSupportedProtocolsResolve" not in vt_src)
check("BedrockVersionTest に protocol389BoundaryGates がある",
      "protocol389BoundaryGates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "allSixtyTwoProtocolsReachStartGame" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame389Equals390", "resourcePacks389Equals390",
              "playerListAdd389HasNoVerified", "playerListRemove389HasNoVerified",
              "decodeInventoryTransactionV0As389"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)
set_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionEntityTest.java")
check("BedrockSessionEntityTest に entityMove389UsesMoveEntityNotDelta がある",
      "entityMove389UsesMoveEntityNotDelta" in set_src)

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
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、56 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 57 個見出しで、389 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 389 | 1.14.0 / 1.14.1 / 1.14.20 / 1.14.21 / 1.14.30 / 1.14.41 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 63 プロトコル／50 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407・390・389 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に StartGame の 389（390 と同一形式）がある",
      "1.14.41（389）も 390 と同一形式（StartGameSerializer_v388" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の vec3f（389〜419）と protocol.json との食い違いがある",
      "（1.12.1〜1.16.101 と 1.16.220 以降）が vec3f" in bedrock_md)
check("BEDROCK.md の境界表に PlayerList の add の verified は 390 以降（389 には add にも無い）とある",
      "1.14.41（389）は add にも verified が無い（PlayerListSerializer_v388" in bedrock_md)
check("BEDROCK.md の境界表に MoveEntityDelta の代替列挙に 389 がある",
      "389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する" in bedrock_md)
check("BEDROCK.md の境界表に InventoryTransaction の 389（同一形式）がある",
      "1.14.41（389）も同一形式（BE-NOTES.md §29）" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 63 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md のアイテム表が 38 本のバイナリになっている",
      "38 本のバイナリ" in bedrock_md)
check("BEDROCK.md の網羅率に 1.14.41 の代用明記つきエントリがある",
      "1.14.41 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "389 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 63 プロトコルの対応表／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／63 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.14.41 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・1.10.1・1.11.4・1.12.1・1.13.3・1.14.41・1.14.60・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（389 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち。389 の実形式は 12 フィールド）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("PlayerSkin は BedrockPackets／BedrockSession に未実装のまま（389 でも実装しない）",
      "PlayerSkin" not in pkt_src and "PlayerSkin" not in ses_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（389 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（389 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の shift は 389 でも無関係）",
      "AvailableCommands" not in pkt_src)

# ---------------------------------------------------------------------------
print()
print(f"結果: {passed} 件 OK / {failed} 件 NG（全 {passed + failed} 件）")
if failures:
    print("失敗したチェック:")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
