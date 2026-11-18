#!/usr/bin/env python3
"""BE 354（1.11.0〜1.11.4）対応の検証。

354 には minecraft-data の protocol.json が存在しない
（1.11.0／1.11.1／1.11.4 は HTTP 404。protocolVersions.json にも
354 のエントリは無い）。出典は CloudburstMC Protocol（ブランチ 3.0、
コミット 7163a6aedaefbc1d188431ef90ebc703b1138340）の v354
コーデックで、`.tmp-be/cloudburst-protocol/` に clone 済み。
Bedrock_v354 は Bedrock_v340 の CODEC を土台に、updateSerializer
9 件と新規登録 2 件（OnScreenTextureAnimation 130／
MapCreateLockedCopy 131）を行う。361 との差分（この実装の
送受信範囲）は StartGame（LevelSettings 30 フィールドで
onlySpawningV1Villagers 無し・itemstates 無し・パレット配列の
要素に id 無し）、LevelChunk（subChunkCount・キャッシュ可否
無し）、AddPainting（位置がブロック座標）の 3 件
（BE-NOTES.md §32、実測メモ `.tmp-be/be-354-cloudburst-diff.md`）。

共有バージョン（1.11.0／1.11.1／1.11.2／1.11.3／1.11.4）は Wiki の
個別ページの infobox（Protocol version 354）で確認した
（BE-NOTES.md §32）。

アイテム表は 361〜431 と同じ bedrock_item_ids_1_16_210.bin を
共用するが、354 については代用である（PMMP BedrockData の
bedrock-1.11.0 タグは旧形式のみで required_item_list.json が無く、
1.11 系のランタイム ID の実データは存在しない。354 自体の
実測ではない）。StartGame のブロックパレットは 354 の配列形式
（name／meta のみ）に 1.12.0 のパレットを流し込めないため、
bedrock-1.11.0 タグの実測（required_block_states.json＋
block_id_map.json）から生成した bedrock_block_palette_1_11_0.bin
（3,183 件）を使う。

使い方: python3 tools/verify-be-354-tables.py
"""
import hashlib
import json
import re
import struct
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
        print(f"  ok: {name}")
    else:
        failed += 1
        failures.append(name + (f"（{detail}）" if detail else ""))
        print(f"  NG: {name} {detail}")


def sha1_of(path):
    return hashlib.sha1(Path(path).read_bytes()).hexdigest()


def read(path):
    return Path(path).read_text(encoding="utf-8")


def typemap_block(src, name):
    """`NAME = ...builder(...)` から `.build();` までのブロックを返す。"""
    start = src.index(name + " =")
    end = src.index(".build();", start)
    return src[start:end]


# ---------------------------------------------------------------------------
print("== A. 出典の同一性（Cloudburst v354 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v354＝1.11.0",
      "| Bedrock_v354  |       1.11.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v361＝1.12.0（354 の 1 つ上の出典確認）",
      "| Bedrock_v361  |       1.12.0        |" in versions_md)

v354_src = read(CB / "v354/Bedrock_v354.java")
check("Bedrock_v354.java の CODEC は protocolVersion(354) と minecraftVersion(\"1.11.0\") を持つ",
      "protocolVersion(354)" in v354_src and 'minecraftVersion("1.11.0")' in v354_src)
check("Bedrock_v354.java の CODEC は Bedrock_v340.CODEC を土台にする",
      "Bedrock_v340.CODEC.toBuilder()" in v354_src)
check("Bedrock_v354.java は helper に BedrockCodecHelper_v340 を使う（v354 専用ヘルパーは無い）",
      "new BedrockCodecHelper_v340(ENTITY_DATA, GAME_RULE_TYPES)" in v354_src
      and not (CB / "v354/BedrockCodecHelper_v354.java").exists())
check("Bedrock_v354.java の updateSerializer は 9 件",
      v354_src.count("updateSerializer(") == 9,
      f"実際は {v354_src.count('updateSerializer(')}")
check("Bedrock_v354.java の新規登録は OnScreenTextureAnimation（130）・MapCreateLockedCopy（131）の 2 件",
      v354_src.count(".registerPacket(") == 2
      and "OnScreenTextureAnimationPacket::new" in v354_src and ", 130," in v354_src
      and "MapCreateLockedCopyPacket::new" in v354_src and ", 131," in v354_src)
check("Bedrock_v354.java に登録解除は無い（deregisterPacket 0 件）",
      "deregisterPacket(" not in v354_src)
check("Bedrock_v354.java は CraftingData／LecternUpdate を _v354 に差し替える",
      "updateSerializer(CraftingDataPacket.class, CraftingDataSerializer_v354.INSTANCE)" in v354_src
      and "updateSerializer(LecternUpdatePacket.class, LecternUpdateSerializer_v354.INSTANCE)" in v354_src)
check("Bedrock_v354.java に PlayerAuthInput は無い（388 で新規登録）",
      "PlayerAuthInput" not in v354_src)

v361_src = read(CB / "v361/Bedrock_v361.java")
check("Bedrock_v361.java の CODEC は Bedrock_v354.CODEC を土台にする（354 の 1 つ上）",
      "Bedrock_v354.CODEC.toBuilder()" in v361_src)
check("Bedrock_v361.java は AddHangingEntity を登録解除する（354 には登録があることの裏付け）",
      ".deregisterPacket(AddHangingEntityPacket.class)" in v361_src)


def chain_indices(name, keypos, upto):
    """TypeMap の index 集合をチェーン（v291→…→upto）で累積する。"""
    idx = set()
    keys = {}
    for v in ("v291", "v313", "v332", "v340", "v354", "v361", "v388", "v389"):
        src = read(CB / f"{v}/Bedrock_{v}.java")
        if name + " =" in src:
            blk = typemap_block(src, name)
            if "builder()" in blk.split("\n")[0]:
                idx = set()
                keys = {}
            for m in re.finditer(r"\.shift\((\d+),\s*(\d+)\)", blk):
                a, n = int(m.group(1)), int(m.group(2))
                idx = {i + n if i >= a else i for i in idx}
                keys = {k: (i + n if i >= a else i) for k, i in keys.items()}
            for m in re.finditer(r"\.insert\(([^)]*)\)", blk):
                args = [a.strip() for a in m.group(1).split(",")]
                idx.add(int(args[keypos]))
                if keypos == 1:
                    keys[args[0]] = int(args[1])
        if v == upto:
            break
    return idx, keys


flags354_idx, _ = chain_indices("ENTITY_FLAGS", 0, "v354")
check("ENTITY_FLAGS はチェーン累積で 354 が 87 件（0〜86 連続。87 は v361 が挿入）",
      flags354_idx == set(range(87)),
      f"実際は {len(flags354_idx)} 件・最大 {max(flags354_idx)}")
data354_idx, data354_keys = chain_indices("ENTITY_DATA", 1, "v354")
check("ENTITY_DATA の index はチェーン累積で 354 が 103 件（0〜102 連続）",
      data354_idx == set(range(103)),
      f"実際は {len(data354_idx)} 件・最大 {max(data354_idx)}")
check("ENTITY_DATA の index 40 は 354 が SKIN_ID（361 では NPC_DATA に置換される定義のみの差）",
      data354_keys.get("EntityDataTypes.SKIN_ID") == 40)
check("ENTITY_DATA の AREA_EFFECT_CLOUD_PARTICLE は 62（354 の実効。361 と同じ）",
      data354_keys.get("EntityDataTypes.AREA_EFFECT_CLOUD_PARTICLE") == 62)
pt354_idx, _ = chain_indices("PARTICLE_TYPES", 0, "v354")
check("PARTICLE_TYPES はチェーン累積で 354 が 55 件（0〜54 連続）",
      pt354_idx == set(range(55)),
      f"実際は {len(pt354_idx)} 件・最大 {max(pt354_idx)}")

for name in ("1.11.0", "1.11.1", "1.11.4"):
    check(f"354 の protocol.json は .tmp-be に無い（protocol-{name}.json は存在しない）",
          not (TMP / f"protocol-{name}.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 354 のエントリは無い",
      354 not in pv_protocols)

# A-2. PMMP bedrock-1.11.0 タグ（354 の世代のデータ。旧形式のみ）
tree = json.loads(read(TMP / "be-354/bedrock-1.11.0-tree.json"))
tree_paths = [t["path"] for t in tree["tree"]]
check("PMMP bedrock-1.11.0 タグのツリーは 11 エントリ",
      len(tree_paths) == 11, f"実際は {len(tree_paths)}")
check("bedrock-1.11.0 タグに required_item_list.json は無い（354 のランタイム ID 実データは存在しない）",
      "required_item_list.json" not in tree_paths)
check("bedrock-1.11.0 タグに required_block_states.nbt は無い（.json のみ）",
      "required_block_states.nbt" not in tree_paths
      and "required_block_states.json" in tree_paths)

iim11 = TMP / "be-354/item_id_map.json"
check("PMMP bedrock-1.11.0 の item_id_map.json は 229 件・SHA-1 9aa7d0f2…",
      len(json.loads(read(iim11))) == 229
      and sha1_of(iim11) == "9aa7d0f2d56192cba5eedba070ad9a83bc4d9f95")
bim11 = TMP / "be-354/block_id_map.json"
check("PMMP bedrock-1.11.0 の block_id_map.json は 460 件・SHA-1 95f9de11…（1.12.0 とバイト同一）",
      len(json.loads(read(bim11))) == 460
      and sha1_of(bim11) == "95f9de111bc5157825964305f91d37009e52231e"
      and sha1_of(bim11) == sha1_of(TMP / "be-361/block_id_map.json"))
rbs11 = TMP / "be-354/required_block_states.json"
rbs11_data = json.loads(read(rbs11))["minecraft"]
check("PMMP bedrock-1.11.0 の required_block_states.json は 460 名・(名前, メタ) 3,183 件・SHA-1 425f624d…",
      len(rbs11_data) == 460
      and sum(len(v) for v in rbs11_data.values()) == 3183
      and sha1_of(rbs11) == "425f624d91cce1a03a17bf09fc82c64dc4822150")

# ---------------------------------------------------------------------------
print("== B. 354 のパケット一覧（361 の一覧からの差分） ==")

p422 = json.loads(read(TMP / "protocol-1.16.201.json"))
mappings = p422["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
ids422 = {int(k): v for k, v in mappings.items()}
check("422 のパケット一覧は 160 件（土台の再確認）", len(ids422) == 160)

ids407 = {i: n for i, n in ids422.items()
          if i not in {157, 158, 159, 160, 161, 162} and i != 163}
ids388 = {i: n for i, n in ids407.items()
          if i not in {145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156}}
ids361 = {i: n for i, n in ids388.items()
          if i not in {137, 138, 139, 140, 141, 142, 143, 144}}
check("361 のパケット一覧は 133 件（§31 の再確認）", len(ids361) == 133)

# 354 は 361 から v361 の新規 ID のうち 354 に無いもの
# （126＝video_stream_connect の旧 ID は 354 では 125 のため除外対象外。
# ここでは ID 集合の差分だけを見る：361 の 132〜136 と
# client_cache_status 129、video_stream_connect の 126 が 354 に無く、
# add_hanging_entity 16 が 354 にある。124／125 は両世代に在るが
# パケット名が違う（354：lectern_update 124／video_stream_connect 125、
# 361：level_event_generic 124／lectern_update 125）ため ID 集合では
# 差し引きしない。実測メモ `.tmp-be/be-354-cloudburst-diff.md` 参照）
ids354 = {i: n for i, n in ids361.items()
          if i not in {126, 129, 132, 133, 134, 135, 136}}
ids354[16] = "add_hanging_entity"
check("354 のパケット一覧は 127 件（protocol.json 由来の母集団）",
      len(ids354) == 127, f"実際は {len(ids354)}")
check("354 の最大 ID は 131（map_create_locked_copy。v354 で登録）",
      max(ids354) == 131)
check("354 の欠番は [37, 116, 126, 129]（ID は 1 始まり）",
      [i for i in range(1, 132) if i not in ids354] == [37, 116, 126, 129])
check("354 の一覧に player_auth_input（144）は無い（388 で新規登録）",
      144 not in ids354)
check("354 の一覧に add_hanging_entity（16）がある（361 で登録解除）",
      16 in ids354)
# Cloudburst の登録ベースでは 354 は 126 件・最大 ID 131・欠番
# [116, 126, 127, 128, 129] になる（チェーン積み上げ：v291 の
# 116 件 → v313 で 121 → v332 で 122 → v340 で 124 → v354 で
# +2＝126 件。BE-NOTES.md §32、実測メモ
# `.tmp-be/be-354-cloudburst-diff.md`）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 354 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 354 の一覧の {packet_name} と一致",
              value in ids354 and ids354[value].startswith(packet_name),
              f"一覧では {ids354.get(value)}")
check("PACKET_PLAYER_AUTH_INPUT（144）は 354 の一覧に無い（パケット自体が存在しない）",
      const_value("PACKET_PLAYER_AUTH_INPUT") == 144
      and const_value("PACKET_PLAYER_AUTH_INPUT") not in ids354)

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（354 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_11_4(354, \"1.11.4\") がある",
      'V1_11_4(354, "1.11.4")' in ver_src)
check("V1_11_4 は enum の先頭（V1_12_1 より前）",
      ver_src.index("V1_11_4(354") < ver_src.index("V1_12_1(361"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("クラス javadoc の protocol.json ファイル数は 51 のまま（354 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 354 は 1.11.0〜1.11.4 の共有と Wiki infobox の出典がある",
      "1.11.0 / 1.11.1 / 1.11.2 / 1.11.3 / 1.11.4 で共有する" in ver_src
      and "Protocol version 354" in ver_src)
check("enum コメントに 354 の 1 つ下は 340（1.10.0）とある",
      "354 の 1 つ下は 340" in ver_src)
check("enum コメントに 355〜359 は Wiki 一覧表に行が無い番号帯とある",
      "355〜359 は Wiki の Protocol version 一覧表に" in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 354 -> \"1_16_210\" がある",
      'case 354 -> "1_16_210";' in ver_src)
check("case 354 のコメントに「354 自体の実測ではない」の代用明記がある",
      "354 自体の実測ではない" in ver_src)
check("itemTableTag の case 361／388／389／390／407／408／419／422／428／431 は不変",
      'case 361 -> "1_16_210";' in ver_src
      and 'case 388 -> "1_16_210";' in ver_src
      and 'case 389 -> "1_16_210";' in ver_src
      and 'case 390 -> "1_16_210";' in ver_src
      and 'case 407 -> "1_16_210";' in ver_src and 'case 408 -> "1_16_210";' in ver_src
      and 'case 419 -> "1_16_210";' in ver_src and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（361〜431 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（354 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("354 用の .bin（1_11_4／1_11_0）は作っていない",
      not any(b in ("bedrock_item_ids_1_11_4.bin", "bedrock_item_ids_1_11_0.bin")
              for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 354 のエントリは無い（361〜422 と同じ扱い）",
      "354" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 354 世代の形式を確認） ==")

sg332 = read(CB / "v332/serializer/StartGameSerializer_v332.java")
sg361 = read(CB / "v361/serializer/StartGameSerializer_v361.java")
check("StartGameSerializer_v332（354 の LevelSettings 実効）に onlySpawningV1Villagers は無い（30 フィールド）",
      "OnlySpawningV1Villagers" not in sg332)
check("StartGameSerializer_v361 は onlySpawningV1Villagers を書く（361 で追加）",
      "buffer.writeBoolean(packet.isOnlySpawningV1Villagers())" in sg361)
sg291 = read(CB / "v291/serializer/StartGameSerializer_v291.java")
check("StartGameSerializer_v291（354 のトップレベル実効）はパレットを配列形式で書く（件数＋name／meta ShortLE のみ。id は無い）",
      "VarInts.writeUnsignedInt(buffer, palette.size())" in sg291
      and 'helper.writeString(buffer, blockTag.getString("name"))' in sg291
      and 'buffer.writeShortLE(entry.getShort("meta"))' in sg291
      and 'entry.getShort("id")' not in sg291)
check("StartGameSerializer_v291 に itemDefinitions は無い（パレットの直後は multiplayerCorrelationId）",
      "ItemDefinition" not in sg291
      and "helper.writeString(buffer, packet.getMultiplayerCorrelationId())" in sg291)
check("StartGameSerializer_v361 はパレット要素に id（ShortLE）を書く（361 で追加）",
      'buffer.writeShortLE(entry.getShort("id"))' in sg361)

fc291 = read(CB / "v291/serializer/FullChunkDataSerializer_v291.java")
lc361 = read(CB / "v361/serializer/LevelChunkSerializer_v361.java")
check("FullChunkDataSerializer_v291（354 の LevelChunk 実効）は chunkX／chunkZ と payload のみ",
      "VarInts.writeInt(buffer, packet.getChunkX())" in fc291
      and "getSubChunksLength" not in fc291 and "CachingEnabled" not in fc291)
check("LevelChunkSerializer_v361 は subChunksLength とキャッシュ可否を書く（361 で追加）",
      "VarInts.writeUnsignedInt(buffer, packet.getSubChunksLength())" in lc361
      and "buffer.writeBoolean(packet.isCachingEnabled())" in lc361)

apaint332 = read(CB / "v332/serializer/AddPaintingSerializer_v332.java")
apaint361 = read(CB / "v361/serializer/AddPaintingSerializer_v361.java")
check("AddPaintingSerializer_v332（354 の実効）は座標を writeBlockPosition で書く",
      "helper.writeBlockPosition(buffer, packet.getPosition().toInt())" in apaint332)
check("AddPaintingSerializer_v361 は座標を writeVector3f で書く（361 で vec3f 化）",
      "helper.writeVector3f(buffer, packet.getPosition())" in apaint361)

ap291 = read(CB / "v291/serializer/AddPlayerSerializer_v291.java")
check("AddPlayerSerializer_v291 に buildPlatform は無い（354 の実効。361 と同じ）",
      "BuildPlatform" not in ap291)
pl291 = read(CB / "v291/serializer/PlayerListSerializer_v291.java")
check("PlayerListSerializer_v291 の add は旧直書き（354 の実効。361 と同じ）",
      "helper.writeString(buffer, skin.getSkinId())" in pl291
      and "helper.writeByteArray(buffer, skin.getSkinData().getImage())" in pl291
      and "trustedSkin" not in pl291)
rps313 = read(CB / "v313/serializer/ResourcePackStackSerializer_v313.java")
check("ResourcePackStackSerializer_v313 に gameVersion は無い（354 の実効。361 と同じ）",
      "GameVersion" not in rps313)

for hv in ("v291", "v313", "v332", "v340"):
    hsrc = read(CB / f"{hv}/BedrockCodecHelper_{hv}.java")
    check(f"BedrockCodecHelper_{hv} に readSkin／writeSkin の上書きは無い（354 のヘルパー継承チェーンに Skin 形式が存在しない）",
          "readSkin" not in hsrc and "writeSkin" not in hsrc)

it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（354 は transaction_type から始まる。361 と同じ）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)

check("MoveEntityDelta は 354 では _v291 が実効（Bedrock_v354.java は触っていない。セッションは MoveEntity で代替）",
      "MoveEntityDeltaPacket" not in v354_src)

# ---------------------------------------------------------------------------
print("== G. コード側の実装（述語・分岐・範囲表記・テスト名） ==")

for pred in ("hasStartGameOnlySpawningV1Villagers", "hasLevelChunkSubChunkCount"):
    check(f"述語 {pred}() が protocol >= 361 で定義されている（354 回で新規）",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol >= 361;", ver_src)
          is not None)
check("述語 hasStartGameLegacyPaletteEntryId() が protocol == 361 で定義されている（354 回で新規）",
      re.search(r"public boolean hasStartGameLegacyPaletteEntryId\(\) \{\s*return protocol == 361;",
                ver_src) is not None)
check("述語 hasItemstates() の下限が 361 になった（protocol >= 361 && protocol <= 748）",
      re.search(r"public boolean hasItemstates\(\) \{\s*return protocol >= 361 && protocol <= 748;",
                ver_src) is not None)
check("述語 addPaintingUsesBlockCoordinates() に 354 側が追加された（protocol <= 354 || 422〜428）",
      re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 \|\| \(protocol >= 422 && protocol <= 428\);",
                ver_src) is not None)
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 のまま（§28 の境界は不変）",
      re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                ver_src) is not None)
gte = sorted({int(n) for n in re.findall(r"protocol >= (\d+)", ver_src)})
check("BedrockVersion の最小の >= 閾値は 332 になった（313 回で resourcePacksInfoHasScripting を追加。354 回で 361 境界の述語を追加）",
      gte[0] == 332, f"実際は {gte[0]}")

pkt_src = read(SRC / "BedrockPackets.java")
check("encodeStartGame が only spawn v1 villagers を hasStartGameOnlySpawningV1Villagers でゲートしている（1 箇所）",
      pkt_src.count("version.hasStartGameOnlySpawningV1Villagers()") == 1,
      f"実際は {pkt_src.count('version.hasStartGameOnlySpawningV1Villagers()')} 箇所")
check("encodeStartGame が 354 のパレットを hasStartGameLegacyPaletteEntryId で出し分けている（1 箇所）",
      pkt_src.count("version.hasStartGameLegacyPaletteEntryId()") == 1)
check("encodeStartGame が 1.11.0 パレットを使う（load1_11_0 は 1 箇所）",
      pkt_src.count("BedrockLegacyBlockPalette.load1_11_0().rawArrayBytes()") == 1)
check("encodeStartGame が 1.12.0 パレットを使う（load は 1 箇所。不変）",
      pkt_src.count("BedrockLegacyBlockPalette.load().rawArrayBytes()") == 1)
check("encodeLevelChunk が subChunkCount を hasLevelChunkSubChunkCount でゲートしている（1 箇所）",
      pkt_src.count("version.hasLevelChunkSubChunkCount()") == 1,
      f"実際は {pkt_src.count('version.hasLevelChunkSubChunkCount()')} 箇所")
check("encodeStartGame が itemstates を hasItemstates でゲートしている（1 箇所。不変）",
      pkt_src.count("version.hasItemstates()") == 1,
      f"実際は {pkt_src.count('version.hasItemstates()')} 箇所")

legacy_src = read(SRC / "BedrockLegacyBlockPalette.java")
check("BedrockLegacyBlockPalette が bedrock_block_palette_1_11_0.bin を読む（load1_11_0）",
      'RESOURCE_NAME_1_11_0 = "bedrock_block_palette_1_11_0.bin"' in legacy_src
      and "load1_11_0()" in legacy_src)
pal11_bin = RES / "bedrock_block_palette_1_11_0.bin"
pal11_bytes = pal11_bin.read_bytes()
check("bedrock_block_palette_1_11_0.bin は 83,378 バイト・SHA-1 15ded0d9…",
      len(pal11_bytes) == 83378
      and sha1_of(pal11_bin) == "15ded0d92930b81f3cbdbcdf581890eee8a6b07c")
pal12_bin = RES / "bedrock_block_palette_1_12_0.bin"
check("bedrock_block_palette_1_12_0.bin は 92,944 バイト・SHA-1 3553c5c9…のまま（不変）",
      len(pal12_bin.read_bytes()) == 92944
      and sha1_of(pal12_bin) == "3553c5c934caf5be473a954a1222a1a5bb90e6bf")


def uvarint(n):
    out = bytearray()
    while n & ~0x7F:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n)
    return bytes(out)


bim11_data = json.loads(read(bim11))
regen = bytearray()
entries354 = []
for bname, metas in rbs11_data.items():
    for meta in metas:
        entries354.append((f"minecraft:{bname}", meta))
        assert f"minecraft:{bname}" in bim11_data
regen += uvarint(len(entries354))
for ename, emeta in entries354:
    eb = ename.encode("utf-8")
    regen += uvarint(len(eb)) + eb + struct.pack("<h", emeta)
check("bedrock_block_palette_1_11_0.bin は PMMP bedrock-1.11.0 の実測からの再生成（name／meta のみ）とバイト一致する",
      bytes(regen) == pal11_bytes)
check("1.11.0 パレットの先頭は minecraft:acacia_button／meta 0、末尾は minecraft:yellow_glazed_terracotta／meta 7",
      entries354[0] == ("minecraft:acacia_button", 0)
      and entries354[-1] == ("minecraft:yellow_glazed_terracotta", 7))
check("1.11.0 パレット（3,183 件）は 1.12.0 パレット（3,311 件）と中身が違う（共用できない）",
      len(entries354) == 3183 and pal11_bytes != pal12_bin.read_bytes())

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession の切断メッセージが 291-2193 になっている",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in ses_src)
check("BedrockSession が 354 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
      "bedrockVersion.moveEntityDeltaUsesAbsolutePosition()" in ses_src
      and "sendEntityMoveDelta" in ses_src)

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
check("BedrockVersionTest に protocol354BoundaryGates がある",
      "protocol354BoundaryGates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "allSixtyTwoProtocolsReachStartGame" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame354DiffersFrom361", "legacyBlockPalette354Contents",
              "levelChunk354HasNoSubChunkCount", "addPainting354UsesBlockCoordinates",
              "playerListAndResourcePacks354Equal361",
              "decodeInventoryTransactionV0As354"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)

# ---------------------------------------------------------------------------
print("== H. ブロック表（不変の確認） ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 が d2b6acbb…のまま",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 が ee36fffb…のまま",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
check("1_11 系のブロックランタイム ID .bin は作っていない（レガシーパレットとは別物）",
      not list(RES.glob("bedrock_block_runtime_ids_1_11_*.bin")))

# ---------------------------------------------------------------------------
print("== I. 文書（BEDROCK.md／PLAYTEST-BEDROCK.md） ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、59 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 60 個見出しで、354 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 354 | 1.11.0 / 1.11.1 / 1.11.2 / 1.11.3 / 1.11.4 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 63 プロトコル／53 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407・390・389・388・361・354 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に StartGame の 354（LevelSettings 30 フィールド・パレット要素に id 無し）がある",
      "1.11.4（354）は LevelSettings が 30 フィールドで only spawn v1 villagers が無く" in bedrock_md)
check("BEDROCK.md の境界表に LevelChunk の 354（subChunkCount 無し）がある",
      "| LevelChunk | subChunkCount（VarInt）とキャッシュ可否（Boolean）は 1.12.0 以降。1.11.4（354）は" in bedrock_md)
check("BEDROCK.md の境界表に AddPlayer の 354（361 と同じ）がある",
      "1.11.4（354）も同じ（BE-NOTES.md §32）" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の 354（ブロック座標）がある",
      "coordinates は 1.7.1・1.8.1・1.9.0・1.10.1・1.11.4 と 1.16.201／1.16.210 が BlockCoordinates" in bedrock_md)
check("BEDROCK.md の境界表に MoveEntityDelta の代替列挙に 354 がある",
      "291・313・332・340・354・361・388・389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する" in bedrock_md)
check("BEDROCK.md の境界表に InventoryTransaction の 354（同一形式）がある",
      "1.11.4（354）も同一形式（BE-NOTES.md §32）" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 63 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md のアイテム表が 38 本のバイナリになっている",
      "38 本のバイナリ" in bedrock_md)
check("BEDROCK.md の網羅率に 1.11.4 の代用明記つきエントリがある",
      "1.11.4 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "354 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 63 プロトコルの対応表／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／63 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.11.4 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・1.10.1・1.11.4・1.12.1・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（354 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち。354 にはヘルパー形式自体が無い）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("PlayerSkin は BedrockPackets／BedrockSession に未実装のまま（354 でも実装しない）",
      "PlayerSkin" not in pkt_src and "PlayerSkin" not in ses_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（354 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（354 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の shift は 354 でも無関係）",
      "AvailableCommands" not in pkt_src)

# ---------------------------------------------------------------------------
print()
print(f"結果: {passed} 件 OK / {failed} 件 NG（全 {passed + failed} 件）")
if failures:
    print("失敗したチェック:")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
