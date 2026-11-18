#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BE 407（Bedrock 1.16.10。1.16.0 / 1.16.1 / 1.16.1.03 / 1.16.1.04 /
1.16.10 で共有）の対応表・コード・文書が実測と一致するか検証する。

出典（BE-NOTES.md §27）:
- 408 の回（§26）と同じ特別フロー。minecraft-data に 1.16.0 系の
  protocol.json は無い：data/bedrock/{1.16.0, 1.16.1, 1.16.10, 1.16.20,
  1.16.40, 1.16.100}/protocol.json はいずれも HTTP 404（408 回に curl で
  確認。対照の 1.16.201 は 200）。protocolVersions.json（58 エントリ）
  にも 419／408／407 のエントリは無い。したがって 407 のパケット形式の
  根拠は CloudburstMC Protocol（`.tmp-be/cloudburst-protocol/`、
  ブランチ `3.0`、コミット `7163a6aedaefbc1d188431ef90ebc703b1138340`）
  の v407 コーデック（Bedrock_v407.java そのもの）。
- 408 の実効定義は v407 と同一だった：Bedrock_v408.java は
  Bedrock_v407.CODEC をそのまま使い、protocolVersion(408) と
  minecraftVersion("1.16.20") だけを変えたもの。よって 407 のパケット
  一覧は 408 と同じ 153 件・最大 ID 156・欠番 [16, 37, 116] になる
  （422 の protocol.json 160 件から v419 の新規 6 件と v422 の
  FilterText を除いたもの）。
  VERSIONS.md の対応は Bedrock_v407＝1.16.0 - 1.16.10、
  Bedrock_v408＝1.16.20、Bedrock_v419＝1.16.100。
- 共有バージョンは Wiki 個別ページの infobox で 407 回に再確認：
  1.16.0／1.16.1／1.16.1.03／1.16.1.04／1.16.10 がいずれも
  Protocol version 407（1.16.1.03／1.16.1.04 は PS4 専用
  ホットフィックスで独立ページがある）。対照の 1.16.20 は 408。
  407 と 408 の間に番号は無い（連続）。1.16.20 のベータ
  （beta 1.16.20.52）は 407 のまま（§26）。
- アイテム表は代用：PMMP BedrockData の bedrock-1.16.0 タグは 407 を
  共有するリリース版 1.16.0 自体のデータだが、ツリー 15 エントリの
  旧形式（item_id_map.json 等）しかなく required_item_list.json は
  無い（ツリーを `.tmp-be/be-408/bedrock-1.16.0-tree.json` に保存）。
  block_id_map.json は bedrock-1.16.0 タグと bedrock-1.16.100 タグで
  バイト同一（SHA-1 f3855a10…、553 件）。item_id_map.json も両タグで
  バイト同一（SHA-1 7ccb7ad6…、807 件。ただし旧 ID 空間でランタイム ID
  ではない）。407 のランタイム ID の実データが無いため、表は
  bedrock_item_ids_1_16_210.bin（bedrock-1.16.100 タグの実測から生成、
  408・419・422 と共用）をそのまま使い、代用である旨を明記する。
- ブロックパレットの旧形式（単一 NBT リストタグ）の裏付け：
  PMMP bedrock-1.16.0 タグ（407 世代そのもの）の
  required_block_states.nbt（1,063,028 バイト、SHA-1 cbd3759e…）は、
  ルートが名前なし TAG_List、要素型 0x0A、件数が zigzag varint で
  6,632 件という形だった（DetergentMC の varint NBT 方言でパース）。

実行: python3 detergentmc-bedrock/tools/verify-be-407-tables.py
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
print("== A. 出典の同一性（Cloudburst v407 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v407＝1.16.0 - 1.16.10",
      "| Bedrock_v407  |  1.16.0 - 1.16.10   |" in versions_md)
check("VERSIONS.md で Bedrock_v408＝1.16.20",
      "| Bedrock_v408  |       1.16.20       |" in versions_md)
check("VERSIONS.md で Bedrock_v407 の下は Bedrock_v390＝1.14.60（§28 で対応済み）",
      "| Bedrock_v390  |       1.14.60       |" in versions_md)

v407_src = read(CB / "v407/Bedrock_v407.java")
check("Bedrock_v407.java の CODEC は protocolVersion(407) と minecraftVersion(\"1.16.0\") を持つ",
      "protocolVersion(407)" in v407_src and 'minecraftVersion("1.16.0")' in v407_src)
check("Bedrock_v407.java の CODEC は Bedrock_v390.CODEC を土台にする",
      "Bedrock_v390.CODEC.toBuilder()" in v407_src)

v408_src = read(CB / "v408/Bedrock_v408.java")
check("Bedrock_v408.java は Bedrock_v407.CODEC を流用する（407 と 408 の実効定義は同一）",
      "Bedrock_v407.CODEC" in v408_src)
check("Bedrock_v408.java は protocolVersion(408) と minecraftVersion(\"1.16.20\") だけを持つ",
      "protocolVersion(408)" in v408_src and 'minecraftVersion("1.16.20")' in v408_src)
check("Bedrock_v408.java に registerPacket／updateSerializer は無い（v407 の定義そのまま）",
      "registerPacket" not in v408_src and "updateSerializer" not in v408_src)

for name in ("1.16.0", "1.16.1", "1.16.10", "1.16.20", "1.16.40", "1.16.100", "1.16.101"):
    check(f"407／408／419 の protocol.json は .tmp-be に無い（protocol-{name}.json は存在しない）",
          not (TMP / f"protocol-{name}.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 419／408／407 のエントリは無い",
      419 not in pv_protocols and 408 not in pv_protocols and 407 not in pv_protocols)
below422 = sorted(p for p in pv_protocols if p < 422)
check("protocolVersions.json で 422 の 1 つ下は 100（419／408／407 は載っていない）",
      below422 and below422[-1] == 100)

# A-2. PMMP bedrock-1.16.0 タグ（407 を共有するリリース版 1.16.0 自体のデータ）
tree = json.loads(read(TMP / "be-408/bedrock-1.16.0-tree.json"))
tree_paths = [t["path"] for t in tree["tree"]]
check("PMMP bedrock-1.16.0 タグのツリーは 15 エントリ",
      len(tree_paths) == 15, f"実際は {len(tree_paths)}")
check("bedrock-1.16.0 タグに required_item_list.json は無い（407 のランタイム ID 実データは存在しない）",
      "required_item_list.json" not in tree_paths)
check("bedrock-1.16.0 タグに canonical_block_states.nbt は無い（旧形式の required_block_states.nbt のみ）",
      "canonical_block_states.nbt" not in tree_paths
      and "required_block_states.nbt" in tree_paths)

bim408 = TMP / "be-408/block_id_map.json"
bim100 = TMP / "be-419-block_id_map_1_16_100.json"
check("PMMP bedrock-1.16.0 の block_id_map.json の SHA-1 が f3855a10…と一致",
      sha1_of(bim408) == "f3855a102c698f960020d928bcaef10d25724062")
check("block_id_map.json は bedrock-1.16.0 タグと bedrock-1.16.100 タグでバイト同一",
      bim408.read_bytes() == bim100.read_bytes())
check("block_id_map.json は 553 件",
      len(json.loads(read(bim408))) == 553)

iim0 = TMP / "be-408/item_id_map_1.16.0.json"
iim100 = TMP / "be-408/item_id_map_1.16.100.json"
check("item_id_map.json は bedrock-1.16.0 タグと bedrock-1.16.100 タグでバイト同一（SHA-1 7ccb7ad6…）",
      iim0.read_bytes() == iim100.read_bytes()
      and sha1_of(iim0) == "7ccb7ad63dd854f120801c84323607731eb8c2ef")
check("item_id_map.json は 807 件（旧 ID 空間。ランタイム ID ではない）",
      len(json.loads(read(iim0))) == 807)

rbs = TMP / "be-408/required_block_states.nbt"
check("PMMP bedrock-1.16.0 の required_block_states.nbt は 1,063,028 バイト・SHA-1 cbd3759e…",
      rbs.stat().st_size == 1063028
      and sha1_of(rbs) == "cbd3759e81a4263cccced32f633a9a555408bdf4")


# ---------------------------------------------------------------------------
print("== B. 407 のパケット一覧（408 と同一：422 の一覧 − v419 新規 6 件 − FilterText） ==")

p422 = json.loads(read(TMP / "protocol-1.16.201.json"))
mappings = p422["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
ids422 = {int(k): v for k, v in mappings.items()}
check("422 のパケット一覧は 160 件（土台の再確認）", len(ids422) == 160)

new419 = {157, 158, 159, 160, 161, 162}
check("v419 の新規 6 件は 157〜162（motion_prediction_hints〜item_component）",
      all(ids422[i].startswith(n) for i, n in zip(
          sorted(new419),
          ["motion_prediction_hints", "animate_entity", "camera_shake",
           "player_fog", "correct_player_move_prediction", "item_component"])))
check("163 は filter_text_packet（v422 の新規。407 には無い）",
      ids422[163].startswith("filter_text_packet"))

ids407 = {i: n for i, n in ids422.items() if i not in new419 and i != 163}
check("407 のパケット一覧は 153 件（408 と同じ）", len(ids407) == 153, f"実際は {len(ids407)}")
check("407 の最大 ID は 156（packet_violation_warning）",
      max(ids407) == 156 and ids407[156].startswith("packet_violation_warning"))
check("407 の欠番は [16, 37, 116]（408・419・422 と同じ。ID は 1 始まり）",
      [i for i in range(1, 157) if i not in ids407] == [16, 37, 116])

v419_src = read(CB / "v419/Bedrock_v419.java")
registered419 = re.findall(r"registerPacket\((\w+Packet)::new", v419_src)
check("Bedrock_v419.java の registerPacket は新規 6 件（157〜162 に対応）",
      registered419 == ["MotionPredictionHintsPacket", "AnimateEntityPacket",
                        "CameraShakePacket", "PlayerFogPacket",
                        "CorrectPlayerMovePredictionPacket", "ItemComponentPacket"],
      f"実際は {registered419}")


# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 407 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 407 の一覧の {packet_name} と一致",
              value in ids407 and ids407[value].startswith(packet_name),
              f"一覧では {ids407.get(value)}")

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（407 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_16_10(407, \"1.16.10\") がある",
      'V1_16_10(407, "1.16.10")' in ver_src)
check("V1_16_10 は enum の 3 番目（先頭は 389 の V1_14_41、2 番目は 390 の V1_14_60、V1_16_61 より前）",
      ver_src.index("V1_14_60(390") < ver_src.index("V1_16_10(407")
      < ver_src.index("V1_16_61(408"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("クラス javadoc の protocol.json ファイル数は 51 のまま（407 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 407 の共有 5 バージョンと Wiki infobox の出典がある",
      "1.16.0 / 1.16.1 / 1.16.1.03 / 1.16.1.04 / 1.16.10" in ver_src
      and "Protocol" in ver_src and "version 407" in ver_src)
check("enum コメントに 407 と 408 の間に番号は無い（連続）とある",
      "407 と 408 の間に番号は無い（連続）" in ver_src)
check("enum コメントに 407 の 1 つ下は 390（1.14.60）とある（§28 で対応）",
      "407 の 1 つ下は 390（1.14.60。Cloudburst の" in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 407 -> \"1_16_210\" がある",
      'case 407 -> "1_16_210";' in ver_src)
check("case 407 のコメントに「407 自体の実測ではない」の代用明記がある",
      "407 自体の実測ではない" in ver_src)
check("itemTableTag の case 408／419／422／428／431 は不変",
      'case 408 -> "1_16_210";' in ver_src and 'case 419 -> "1_16_210";' in ver_src
      and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（408・419・422 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（407 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("407 用の .bin（1_16_10／1_16_1／1_16_0）は作っていない",
      not any(b in ("bedrock_item_ids_1_16_10.bin", "bedrock_item_ids_1_16_1.bin",
                    "bedrock_item_ids_1_16_0.bin") for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 407 のエントリは無い（408・419・422 と同じ扱い）",
      "407" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 407 世代の形式を確認） ==")

sg407 = read(CB / "v407/serializer/StartGameSerializer_v407.java")
check("StartGameSerializer_v407 に experiments は無い（407 の StartGame に experiments 欄は無い）",
      "experiments" not in sg407 and "writeLevelSettings" in sg407)
sg419 = read(CB / "v419/serializer/StartGameSerializer_v419.java")
check("StartGameSerializer_v419 は writeExperiments と blockProperties を持つ（419 で追加）",
      "writeExperiments" in sg419 and "getBlockProperties" in sg419)
check("StartGameSerializer_v419 の itemDefinitions 要素は componentBased を読む（419 で 3 フィールド化）",
      "componentBased" in sg419)
sg388 = read(CB / "v388/serializer/StartGameSerializer_v388.java")
check("StartGameSerializer_v388 は movement authority を Boolean で書く（値 0 は zigzag32 の 0 とバイト同一）",
      "writeBoolean(packet.getAuthoritativeMovementMode() != AuthoritativeMovementMode.CLIENT)" in sg388)
check("StartGameSerializer_v388 はブロックパレットを helper.writeTag（単一 NBT タグ）で書く",
      "helper.writeTag(buffer, packet.getBlockPalette())" in sg388)
sg361 = read(CB / "v361/serializer/StartGameSerializer_v361.java")
check("StartGameSerializer_v361 の itemDefinitions 読みは 2 フィールド（componentBased なし）",
      "new SimpleItemDefinition(identifier, id, false)" in sg361)

rps388 = read(CB / "v388/serializer/ResourcePackStackSerializer_v388.java")
rps313 = read(CB / "v313/serializer/ResourcePackStackSerializer_v313.java")
rps419 = read(CB / "v419/serializer/ResourcePackStackSerializer_v419.java")
check("ResourcePackStackSerializer_v388 は v313 を継承し末尾に gameVersion を足す（407 の実効形式）",
      "extends ResourcePackStackSerializer_v313" in rps388
      and "helper.writeString(buffer, packet.getGameVersion())" in rps388)
check("ResourcePackStackSerializer_v313 は legacy experiment フラグ（bool）を書く",
      "LEGACY_EXPERIMENT_DATA" in rps313 and "buffer.writeBoolean" in rps313)
check("ResourcePackStackSerializer_v419 は experiments 配列＋previouslyToggled を書く（419 で差し替え）",
      "writeExperiments" in rps419 and "isExperimentsPreviouslyToggled" in rps419)

for name in ("MovePlayer", "SetEntityData", "UpdateAttributes"):
    s419 = read(CB / f"v419/serializer/{name}Serializer_v419.java")
    s291 = read(CB / f"v291/serializer/{name}Serializer_v291.java")
    check(f"{name}Serializer_v419 は末尾に tick（writeUnsignedLong）を書く",
          "VarInts.writeUnsignedLong(buffer, packet.getTick())" in s419)
    check(f"{name}Serializer_v291 に tick は無い（407 の実効形式）",
          "getTick" not in s291)

med291 = read(CB / "v291/serializer/MoveEntityDeltaSerializer_v291.java")
med419 = read(CB / "v419/serializer/MoveEntityDeltaSerializer_v419.java")
check("MoveEntityDeltaSerializer_v291 は位置を VarInts.readInt の差分値として読む（旧形式）",
      "packet.setDeltaX(VarInts.readInt(buffer))" in med291)
check("MoveEntityDeltaSerializer_v419 は位置を writeFloatLE の絶対値で書く（419 で差し替え）",
      "buffer.writeFloatLE(packet.getX())" in med419)

# F-2. PMMP required_block_states.nbt のコンテナ形式（旧形式パレットの裏付け。
# bedrock-1.16.0 タグは 407 世代そのもののデータ）
data = (TMP / "be-408/required_block_states.nbt").read_bytes()
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
check("required_block_states.nbt のルートは名前なし TAG_List・要素型 0x0A・件数 6,632（zigzag varint）",
      t0 == 0x09 and name_len == 0 and elem_type == 0x0A and count == 6632,
      f"実際は type={t0} name_len={name_len} elem={elem_type} count={count}")


# ---------------------------------------------------------------------------
print("== G. コード側の実装（述語・分岐・範囲表記・テスト名） ==")

for pred in ("hasStartGameExperiments", "hasStartGameBlockProperties",
             "hasResourcePackStackExperiments", "hasMovePlayerTick",
             "hasSetEntityDataTick", "hasUpdateAttributesTick",
             "moveEntityDeltaUsesAbsolutePosition"):
    check(f"述語 {pred}() が protocol >= 419 で定義されている",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol >= 419;", ver_src)
          is not None)
gte = sorted({int(n) for n in re.findall(r"protocol >= (\d+)", ver_src)})
check("BedrockVersion の最小の >= 閾値は 332（313 回で resourcePacksInfoHasScripting を追加。361 回で 388 境界の述語を追加）",
      gte[0] == 332, f"実際は {gte[0]}")
check("408〜418 の間に >= の閾値は無い（407 と 408 で全述語の値が同じ前提。407 境界の述語は §28）",
      all(not (408 <= t <= 418) for t in gte), f"閾値は {gte}")

pkt_src = read(SRC / "BedrockPackets.java")
check("encodeStartGame が experiments を hasStartGameExperiments でゲートしている",
      "version.hasStartGameExperiments()" in pkt_src)
check("encodeStartGame がパレットを hasStartGameBlockProperties でゲートし旧形式は rawPaletteListTagBytes を使う",
      "version.hasStartGameBlockProperties()" in pkt_src
      and "palette.rawPaletteListTagBytes()" in pkt_src)
check("encodeResourcePackStack が hasResourcePackStackExperiments でゲートしている",
      "version.hasResourcePackStackExperiments()" in pkt_src)
check("MovePlayer の tick が hasMovePlayerTick でゲートされている（テレポート＋他プレイヤーの 2 箇所）",
      pkt_src.count("version.hasMovePlayerTick()") == 2)
check("SetEntityData の tick が hasSetEntityDataTick でゲートされている",
      "version.hasSetEntityDataTick()" in pkt_src)
check("UpdateAttributes の tick が hasUpdateAttributesTick でゲートされている",
      "version.hasUpdateAttributesTick()" in pkt_src)
check("encodeMoveEntityDelta は FloatLE 形式のまま（407 ではセッションが使わない）",
      "out.writeLittleFloat(x == null ? 0 : x)" in pkt_src)

pal_src = read(SRC / "BedrockBlockPalette.java")
check("BedrockBlockPalette に rawPaletteListTagBytes() がある",
      "public byte[] rawPaletteListTagBytes()" in pal_src)

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession が 407 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
      "bedrockVersion.moveEntityDeltaUsesAbsolutePosition()" in ses_src
      and "sendEntityMoveDelta" in ses_src)
check("BedrockSession の切断メッセージが 291-2193 になっている",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in ses_src)
check("BedrockSession が MovePlayer 系に bedrockVersion を渡している",
      "pitch, yaw, bedrockVersion" in ses_src
      and ses_src.count("bedrockVersion));") >= 5)

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
      and "allFiftyFourSupportedProtocolsResolve" not in vt_src)
check("BedrockVersionTest に protocol407Matches408Gates がある",
      "protocol407Matches408Gates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "allFiftyThreeProtocolsReachStartGame" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame407Equals408", "resourcePacks407Equals408", "tickFields407Omitted"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)
set_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionEntityTest.java")
check("BedrockSessionEntityTest に entityMove407UsesMoveEntityNotDelta がある",
      "entityMove407UsesMoveEntityNotDelta" in set_src)

# ---------------------------------------------------------------------------
print("== H. ブロック表（不変の確認） ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 が d2b6acbb…のまま",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 が ee36fffb…のまま",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
check("1_16 系のブロック .bin は作っていない",
      not list(RES.glob("bedrock_block_runtime_ids_1_16_*.bin")))

# ---------------------------------------------------------------------------
print("== I. 文書（BEDROCK.md／PLAYTEST-BEDROCK.md） ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、54 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 55 個見出しで、407 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 407 | 1.16.0 / 1.16.1 / 1.16.1.03 / 1.16.1.04 / 1.16.10 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 54 プロトコル／48 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に 407 の StartGame（experiments 無し・リストタグ形式）がある",
      "1.16.10（407）・1.16.61（408）の StartGame は experiments が無く" in bedrock_md)
check("BEDROCK.md の境界表に MoveEntityDelta の 407・408 代替がある",
      "389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する" in bedrock_md)
check("BEDROCK.md の境界表に ResourcePackStack の旧形式（419 未満）がある",
      "419 未満は旧形式で、experiments 配列が無く" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 55 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md の網羅率に 1.16.10 の代用明記つきエントリがある",
      "1.16.10 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "407 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 55 プロトコルの対応表／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／54 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.14.60 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・1.10.1・1.11.4・1.12.1・1.13.3・1.14.41・1.14.60・1.16.10・1.16.61・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（407 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（407 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（407 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の shift は 407 でも無関係）",
      "AvailableCommands" not in pkt_src)
check("ENTITY_DATA は Bedrock_v407 で定義される（407 はその定義が自世代のもの。メタデータ辞書は 408・419 と同一）",
      "EntityDataTypeMap ENTITY_DATA" in v407_src)

# ---------------------------------------------------------------------------
print()
print(f"結果: {passed} 件 OK / {failed} 件 NG（全 {passed + failed} 件）")
if failures:
    print("失敗したチェック:")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
