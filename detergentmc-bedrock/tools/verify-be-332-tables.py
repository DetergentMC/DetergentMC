#!/usr/bin/env python3
"""BE 332（1.9.0）対応の検証。

332 には minecraft-data の protocol.json が存在しない
（1.9.0 は HTTP 404。protocolVersions.json にも 332 のエントリは
無い）。出典は CloudburstMC Protocol（ブランチ 3.0、コミット
7163a6aedaefbc1d188431ef90ebc703b1138340）の v332 コーデックで、
`.tmp-be/cloudburst-protocol/` に clone 済み。Bedrock_v332 は
Bedrock_v313 の CODEC を土台に updateSerializer 9 件と新規登録
1 件（LevelSoundEvent 123）を行う。332 は StartGame の
LevelSettings シリアライザ（StartGameSerializer_v332）と
AddPainting（_v332、ブロック座標）の世代そのものだが、340 との
差分（この実装の送受信範囲）はワイヤ形式には無く、定義のみの差
（ENTITY_FLAGS の 71〜79 の有無、ENTITY_DATA の index 39 の意味と
99〜101 の有無、COMMAND_PARAMS の FILE_PATH の番号、
SOUND_EVENTS の件数）で、いずれもこの実装が書く範囲には現れない
（BE-NOTES.md §34、実測メモ `.tmp-be/be-332-cloudburst-diff.md`）。

共有バージョンは無く 332 は 1.9.0 のみ（Wiki の 1.9.0 ページの
infobox「Protocol version 332」で確認。1.9.1〜1.9.4 のページは
存在しない。BE-NOTES.md §34）。

アイテム表は 340〜431 と同じ bedrock_item_ids_1_16_210.bin を
共用するが、332 については代用である（PMMP BedrockData の
bedrock-1.9.0 タグはツリー 5 ファイルのみで item_id_map.json
すら無く、1.9 系のランタイム ID の実データは存在しない。
332 自体の実測ではない）。StartGame のブロックパレットは
332 も配列形式（name／meta のみ）だが、1.9 系のパレット実データが
存在しないため、中身は 1.11.0 世代の実測レガシーパレット
（bedrock_block_palette_1_11_0.bin、3,183 件）を代用する
（332 自体の実測ではない）。

使い方: python3 tools/verify-be-332-tables.py
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
print("== A. 出典の同一性（Cloudburst v332 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v332＝1.9.0",
      "| Bedrock_v332  |        1.9.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v340＝1.10.0（332 の 1 つ上の出典確認）",
      "| Bedrock_v340  |       1.10.0        |" in versions_md)

v332_src = read(CB / "v332/Bedrock_v332.java")
check("Bedrock_v332.java の CODEC は protocolVersion(332) と minecraftVersion(\"1.9.0\") を持つ",
      "protocolVersion(332)" in v332_src and 'minecraftVersion("1.9.0")' in v332_src)
check("Bedrock_v332.java の CODEC は Bedrock_v313.CODEC を土台にする",
      "Bedrock_v313.CODEC.toBuilder()" in v332_src)
check("Bedrock_v332.java は helper に新規 BedrockCodecHelper_v332 を使う",
      "new BedrockCodecHelper_v332(ENTITY_DATA, GAME_RULE_TYPES)" in v332_src
      and (CB / "v332/BedrockCodecHelper_v332.java").exists())
check("Bedrock_v332.java の updateSerializer は 9 件",
      v332_src.count("updateSerializer(") == 9,
      f"実際は {v332_src.count('updateSerializer(')}")
check("Bedrock_v332.java の新規登録は LevelSoundEvent（123）の 1 件",
      v332_src.count(".registerPacket(") == 1
      and "LevelSoundEventPacket::new" in v332_src and ", 123," in v332_src)
check("Bedrock_v332.java に登録解除は無い（deregisterPacket 0 件）",
      "deregisterPacket(" not in v332_src)
check("Bedrock_v332.java に PlayerAuthInput は無い（388 で新規登録）",
      "PlayerAuthInput" not in v332_src)
check("v332 に StartGameSerializer_v332 がある（StartGame の LevelSettings は 332 自身の世代）",
      (CB / "v332/serializer/StartGameSerializer_v332.java").exists())
check("v332 に AddPaintingSerializer_v332 がある（340 の実効でもある）",
      (CB / "v332/serializer/AddPaintingSerializer_v332.java").exists())

v340_src = read(CB / "v340/Bedrock_v340.java")
check("Bedrock_v340.java の CODEC は Bedrock_v332.CODEC を土台にする（332 の 1 つ上）",
      "Bedrock_v332.CODEC.toBuilder()" in v340_src)


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
            if keypos == 1:
                for m in re.finditer(r"\.replace\(([^)]*)\)", blk):
                    args = [a.strip() for a in m.group(1).split(",")]
                    keys[args[0]] = int(args[1])
        if v == upto:
            break
    return idx, keys


flags332_idx, _ = chain_indices("ENTITY_FLAGS", 0, "v332")
check("ENTITY_FLAGS はチェーン累積で 332 が 71 件（0〜70 連続。340 で 80 件になる）",
      flags332_idx == set(range(71)),
      f"実際は {len(flags332_idx)} 件・最大 {max(flags332_idx) if flags332_idx else -1}")
data332_idx, data332_keys = chain_indices("ENTITY_DATA", 1, "v332")
check("ENTITY_DATA の index はチェーン累積で 332 が 99 件（0〜98 連続。99〜101 は 340 で追加）",
      data332_idx == set(range(99)),
      f"実際は {len(data332_idx)} 件・最大 {max(data332_idx) if data332_idx else -1}")
check("ENTITY_DATA の index 39 は 332 が INTERACT_TEXT（340 が HAS_NPC に replace。この実装は 39 を使わない）",
      data332_keys.get("EntityDataTypes.INTERACT_TEXT") == 39
      and "EntityDataTypes.HAS_NPC" not in data332_keys)
check("ENTITY_DATA の index 40 は 332 が SKIN_ID（340 と同じ）",
      data332_keys.get("EntityDataTypes.SKIN_ID") == 40)
pt332_idx, _ = chain_indices("PARTICLE_TYPES", 0, "v332")
check("PARTICLE_TYPES はチェーン累積で 332 が 55 件（0〜54 連続。340 と同じ）",
      pt332_idx == set(range(55)),
      f"実際は {len(pt332_idx)} 件・最大 {max(pt332_idx) if pt332_idx else -1}")
cp332_idx, _ = chain_indices("COMMAND_PARAMS", 0, "v332")
check("COMMAND_PARAMS はチェーン累積で 332 が 14 件・最大 45（FILE_PATH は 15。340 の shift(15,-1) で 14／最大 44 になる。AvailableCommands は未送信で無関係）",
      cp332_idx == {1, 2, 3, 4, 5, 6, 7, 15, 28, 30, 33, 35, 38, 45},
      f"実際は {sorted(cp332_idx)}")
se332_idx, _ = chain_indices("SOUND_EVENTS", 0, "v332")
check("SOUND_EVENTS はチェーン累積で 332 が 251 件・最大 255（255 は UNDEFINED。LevelSoundEvent は未送信で無関係）",
      len(se332_idx) == 251 and max(se332_idx) == 255,
      f"実際は {len(se332_idx)} 件・最大 {max(se332_idx) if se332_idx else -1}")

check("332 の protocol.json は .tmp-be に無い（protocol-1.9.0.json は存在しない）",
      not (TMP / "protocol-1.9.0.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 332 のエントリは無い",
      332 not in pv_protocols)

# A-2. PMMP bedrock-1.9.0 タグ（332 の世代のデータ。パレット・ID 表の材料自体が無い）
tree = json.loads(read(TMP / "be-332/bedrock-1.9.0-tree.json"))
tree_paths = [t["path"] for t in tree["tree"]]
check("PMMP bedrock-1.9.0 タグのツリーは 5 エントリ",
      len(tree_paths) == 5, f"実際は {len(tree_paths)}")
check("bedrock-1.9.0 タグに required_item_list.json／item_id_map.json は無い（332 のランタイム ID 実データは存在しない）",
      "required_item_list.json" not in tree_paths
      and "item_id_map.json" not in tree_paths)
check("bedrock-1.9.0 タグに required_block_states.json／block_id_map.json は無い（332 のパレット実データは存在しない）",
      "required_block_states.json" not in tree_paths
      and "block_id_map.json" not in tree_paths)
ci19 = TMP / "be-332/BedrockData-bedrock-1.9.0/creativeitems.json"
ci19_data = json.loads(read(ci19))
check("PMMP bedrock-1.9.0 の creativeitems.json は 1,222 件・SHA-1 f21f9a0c…（旧来の数値 ID でランタイム ID ではない）",
      len(ci19_data) == 1222
      and sha1_of(ci19) == "f21f9a0cc32ee3368a345753a1fed4bc7286cc16"
      and isinstance(ci19_data[0].get("id"), int))

# ---------------------------------------------------------------------------
print("== B. 332 のパケット一覧（340 の一覧からの差分） ==")

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
ids354 = {i: n for i, n in ids361.items()
          if i not in {126, 129, 132, 133, 134, 135, 136}}
ids354[16] = "add_hanging_entity"
ids340 = {i: n for i, n in ids354.items() if i not in {130, 131}}
check("340 のパケット一覧は 125 件（§33 の再確認）", len(ids340) == 125)

# 332 は 340 から v340 の新規登録 {124 lectern_update,
# 125 video_stream_connect} を除いたもの（340 の集合からの逆算で、
# 1.9.0 自体の protocol.json を実測したものではない。
# 実測メモ `.tmp-be/be-332-cloudburst-diff.md` 参照）
ids332 = {i: n for i, n in ids340.items() if i not in {124, 125}}
check("332 のパケット一覧は 123 件（protocol.json 由来の母集団）",
      len(ids332) == 123, f"実際は {len(ids332)}")
check("332 の最大 ID は 128（124・125 は v340 の新規登録）",
      max(ids332) == 128)
check("332 の欠番は [37, 116, 124, 125, 126]（ID は 1 始まり）",
      [i for i in range(1, 129) if i not in ids332] == [37, 116, 124, 125, 126])
check("332 の一覧に player_auth_input（144）は無い（388 で新規登録）",
      144 not in ids332)
check("332 の一覧に add_hanging_entity（16）がある（361 で登録解除）",
      16 in ids332)
# Cloudburst の登録ベースでは 332 は 122 件・最大 ID 123・欠番
# [116] になる（チェーン積み上げ：v291 の 116 件 → v313 で 121 →
# v332 で +1＝122 件。BE-NOTES.md §34、実測メモ
# `.tmp-be/be-332-cloudburst-diff.md`）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 332 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 332 の一覧の {packet_name} と一致",
              value in ids332 and ids332[value].startswith(packet_name),
              f"一覧では {ids332.get(value)}")
check("PACKET_PLAYER_AUTH_INPUT（144）は 332 の一覧に無い（パケット自体が存在しない）",
      const_value("PACKET_PLAYER_AUTH_INPUT") == 144
      and const_value("PACKET_PLAYER_AUTH_INPUT") not in ids332)

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（332 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_9_0(332, \"1.9.0\") がある",
      'V1_9_0(332, "1.9.0")' in ver_src)
check("V1_9_0 は enum の先頭（V1_10_1 より前）",
      ver_src.index("V1_9_0(332") < ver_src.index("V1_10_1(340"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("クラス javadoc の protocol.json ファイル数は 51 のまま（332 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 332 は 1.9.0 のみ（共有なし）と Wiki infobox の出典がある",
      "332 は 1.9.0 のみが使うプロトコル" in ver_src
      and "Protocol version 332" in ver_src)
check("enum コメントに 332 の 1 つ下は 313（1.8.0）とある",
      "332 の 1 つ下は 313" in ver_src)
check("enum コメントに 333〜339 は Wiki 一覧表に行が無い番号帯とある",
      "333〜339 は Wiki の Protocol version" in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 332 -> \"1_16_210\" がある",
      'case 332 -> "1_16_210";' in ver_src)
check("case 332 のコメントに「332 自体の実測ではない」の代用明記がある",
      "332 自体の実測ではない" in ver_src)
check("itemTableTag の case 340／354／361／388／389／390／407／408／419／422／428／431 は不変",
      'case 340 -> "1_16_210";' in ver_src
      and 'case 354 -> "1_16_210";' in ver_src
      and 'case 361 -> "1_16_210";' in ver_src
      and 'case 388 -> "1_16_210";' in ver_src
      and 'case 389 -> "1_16_210";' in ver_src
      and 'case 390 -> "1_16_210";' in ver_src
      and 'case 407 -> "1_16_210";' in ver_src and 'case 408 -> "1_16_210";' in ver_src
      and 'case 419 -> "1_16_210";' in ver_src and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（340〜431 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（332 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("332 用の .bin（1_9_0）は作っていない",
      not any(b == "bedrock_item_ids_1_9_0.bin" for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 332 のエントリは無い（340〜422 と同じ扱い）",
      "332" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 332 世代の形式を確認） ==")

sg332 = read(CB / "v332/serializer/StartGameSerializer_v332.java")
check("StartGameSerializer_v332（332 の LevelSettings 実効）に onlySpawningV1Villagers は無い（30 フィールド。340 と同じ）",
      "OnlySpawningV1Villagers" not in sg332)
sg291 = read(CB / "v291/serializer/StartGameSerializer_v291.java")
check("StartGameSerializer_v291（332 のトップレベル実効）はパレットを配列形式で書く（件数＋name／meta ShortLE のみ。id は無い。340 と同じ）",
      "VarInts.writeUnsignedInt(buffer, palette.size())" in sg291
      and 'helper.writeString(buffer, blockTag.getString("name"))' in sg291
      and 'buffer.writeShortLE(entry.getShort("meta"))' in sg291
      and 'entry.getShort("id")' not in sg291)
check("StartGameSerializer_v291 に itemDefinitions は無い（パレットの直後は multiplayerCorrelationId。340 と同じ）",
      "ItemDefinition" not in sg291
      and "helper.writeString(buffer, packet.getMultiplayerCorrelationId())" in sg291)

fc291 = read(CB / "v291/serializer/FullChunkDataSerializer_v291.java")
check("FullChunkDataSerializer_v291（332 の LevelChunk 実効）は chunkX／chunkZ と payload のみ（340 と同じ）",
      "VarInts.writeInt(buffer, packet.getChunkX())" in fc291
      and "getSubChunksLength" not in fc291 and "CachingEnabled" not in fc291)

apaint332 = read(CB / "v332/serializer/AddPaintingSerializer_v332.java")
check("AddPaintingSerializer_v332（332 自身の実効）は座標を writeBlockPosition で書く",
      "helper.writeBlockPosition(buffer, packet.getPosition().toInt())" in apaint332)

ap291 = read(CB / "v291/serializer/AddPlayerSerializer_v291.java")
check("AddPlayerSerializer_v291 に buildPlatform は無い（332 の実効。340 と同じ）",
      "BuildPlatform" not in ap291)
pl291 = read(CB / "v291/serializer/PlayerListSerializer_v291.java")
check("PlayerListSerializer_v291 の add は旧直書き（332 の実効。340 と同じ）",
      "helper.writeString(buffer, skin.getSkinId())" in pl291
      and "helper.writeByteArray(buffer, skin.getSkinData().getImage())" in pl291
      and "trustedSkin" not in pl291)
rps313 = read(CB / "v313/serializer/ResourcePackStackSerializer_v313.java")
check("ResourcePackStackSerializer_v313 に gameVersion は無い（332 の実効。340 と同じ）",
      "GameVersion" not in rps313)

for hv in ("v291", "v313", "v332"):
    hsrc = read(CB / f"{hv}/BedrockCodecHelper_{hv}.java")
    check(f"BedrockCodecHelper_{hv} に readSkin／writeSkin の上書きは無い（332 のヘルパー継承チェーンに Skin 形式が存在しない）",
          "readSkin" not in hsrc and "writeSkin" not in hsrc)

helper332 = read(CB / "v332/BedrockCodecHelper_v332.java")
helper340 = read(CB / "v340/BedrockCodecHelper_v340.java")
check("BedrockCodecHelper_v332 に blockingTicks は無い（340 ヘルパーの追加。332 の Item は素の形式）",
      "blockingTicks" not in helper332 and "blockingTicks" in helper340)
check("BedrockCodecHelper_v332 に readItemUse は無い（340 ヘルパーの追加。この実装は ITEM_USE の中身を読まないため無関係）",
      "readItemUse" not in helper332 and "readItemUse" in helper340)

it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（332 は transaction_type から始まる。340 と同じ）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)

check("MoveEntityDelta は 332 では _v291 が実効（Bedrock_v332.java は触っていない。セッションは MoveEntity で代替）",
      "MoveEntityDeltaPacket" not in v332_src)

# ---------------------------------------------------------------------------
print("== G. コード側の実装（述語・分岐・範囲表記・テスト名） ==")

for pred in ("hasStartGameOnlySpawningV1Villagers", "hasLevelChunkSubChunkCount"):
    check(f"述語 {pred}() が protocol >= 361 で定義されている（332 では新しい境界は生まれない）",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol >= 361;", ver_src)
          is not None)
check("述語 hasStartGameLegacyPaletteEntryId() が protocol == 361 で定義されている（不変）",
      re.search(r"public boolean hasStartGameLegacyPaletteEntryId\(\) \{\s*return protocol == 361;",
                ver_src) is not None)
check("述語 hasItemstates() の下限が 361 のまま（protocol >= 361 && protocol <= 748）",
      re.search(r"public boolean hasItemstates\(\) \{\s*return protocol >= 361 && protocol <= 748;",
                ver_src) is not None)
check("述語 addPaintingUsesBlockCoordinates() が protocol <= 354 || 422〜428 のまま（332 は <= 354 に含まれる）",
      re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 \|\| \(protocol >= 422 && protocol <= 428\);",
                ver_src) is not None)
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 のまま（§28 の境界は不変）",
      re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                ver_src) is not None)
gte = sorted({int(n) for n in re.findall(r"protocol >= (\d+)", ver_src)})
check("BedrockVersion の最小の >= 閾値は 332 になった（313 回で resourcePacksInfoHasScripting を追加）",
      gte[0] == 332, f"実際は {gte[0]}")

pkt_src = read(SRC / "BedrockPackets.java")
check("encodeStartGame が only spawn v1 villagers を hasStartGameOnlySpawningV1Villagers でゲートしている（1 箇所。不変）",
      pkt_src.count("version.hasStartGameOnlySpawningV1Villagers()") == 1,
      f"実際は {pkt_src.count('version.hasStartGameOnlySpawningV1Villagers()')} 箇所")
check("encodeStartGame がパレットを hasStartGameLegacyPaletteEntryId で出し分けている（1 箇所。不変。332 は id 無し側）",
      pkt_src.count("version.hasStartGameLegacyPaletteEntryId()") == 1)
check("encodeStartGame が 1.11.0 パレットを使う（load1_11_0 は 1 箇所。332 もこれを代用する）",
      pkt_src.count("BedrockLegacyBlockPalette.load1_11_0().rawArrayBytes()") == 1)
check("encodeStartGame が 1.12.0 パレットを使う（load は 1 箇所。不変）",
      pkt_src.count("BedrockLegacyBlockPalette.load().rawArrayBytes()") == 1)
check("encodeLevelChunk が subChunkCount を hasLevelChunkSubChunkCount でゲートしている（1 箇所。不変）",
      pkt_src.count("version.hasLevelChunkSubChunkCount()") == 1,
      f"実際は {pkt_src.count('version.hasLevelChunkSubChunkCount()')} 箇所")
check("encodeStartGame が itemstates を hasItemstates でゲートしている（1 箇所。不変）",
      pkt_src.count("version.hasItemstates()") == 1,
      f"実際は {pkt_src.count('version.hasItemstates()')} 箇所")

legacy_src = read(SRC / "BedrockLegacyBlockPalette.java")
check("BedrockLegacyBlockPalette が bedrock_block_palette_1_11_0.bin を読む（load1_11_0。332 も代用で使う）",
      'RESOURCE_NAME_1_11_0 = "bedrock_block_palette_1_11_0.bin"' in legacy_src
      and "load1_11_0()" in legacy_src)
check("BedrockLegacyBlockPalette の javadoc に 332 の代用明記がある",
      "332（1.9.0）も形式は同じ" in legacy_src
      and "332 自体の実測ではない" in legacy_src)
pal11_bin = RES / "bedrock_block_palette_1_11_0.bin"
check("bedrock_block_palette_1_11_0.bin は 83,378 バイト・SHA-1 15ded0d9…のまま（332 は新規パレットを作らず代用）",
      len(pal11_bin.read_bytes()) == 83378
      and sha1_of(pal11_bin) == "15ded0d92930b81f3cbdbcdf581890eee8a6b07c")
pal12_bin = RES / "bedrock_block_palette_1_12_0.bin"
check("bedrock_block_palette_1_12_0.bin は 92,944 バイト・SHA-1 3553c5c9…のまま（不変）",
      len(pal12_bin.read_bytes()) == 92944
      and sha1_of(pal12_bin) == "3553c5c934caf5be473a954a1222a1a5bb90e6bf")
check("332 用のパレット .bin（1_9_0）は作っていない（実データが無いため代用）",
      not (RES / "bedrock_block_palette_1_9_0.bin").exists())

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession の切断メッセージが 291-2193 になっている",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in ses_src)
check("BedrockSession が 332 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
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
      "void allSixtyFourSupportedProtocolsResolve()" in vt_src
      and "void allSixtyOneSupportedProtocolsResolve()" not in vt_src)
check("BedrockVersionTest に protocol332BoundaryGates がある",
      "protocol332BoundaryGates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "allSixtyTwoProtocolsReachStartGame" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame332Equals340", "levelChunk332Equals340",
              "addPainting332UsesBlockCoordinates",
              "playerListAndResourcePacks332Equal340",
              "decodeInventoryTransactionV0As332"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)

# ---------------------------------------------------------------------------
print("== H. ブロック表（不変の確認） ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 が d2b6acbb…のまま",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 が ee36fffb…のまま",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
check("1_9 系のブロックランタイム ID .bin は作っていない（レガシーパレットとは別物）",
      not list(RES.glob("bedrock_block_runtime_ids_1_9_*.bin")))

# ---------------------------------------------------------------------------
print("== I. 文書（BEDROCK.md／PLAYTEST-BEDROCK.md） ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、61 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 62 個見出しで、332 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 332 | 1.9.0 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 63 プロトコル／55 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407・390・389・388・361・354・340・332 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に StartGame の 332（340 と同一形式・パレット代用）がある",
      "1.9.0（332）も同一形式（実効シリアライザは 340 と全て同じ" in bedrock_md)
check("BEDROCK.md の境界表に LevelChunk の 332（subChunkCount 無し）がある",
      "1.9.0（332）も同じ（BE-NOTES.md §34）。1.8.1（313）も同じ（BE-NOTES.md §35）。1.7.1（291）も同じ（BE-NOTES.md §36）。26.60（2225）は末尾に client_biome_update が付く" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の 332（ブロック座標）がある",
      "coordinates は 1.7.1・1.8.1・1.9.0・1.10.1・1.11.4 と 1.16.201／1.16.210 が BlockCoordinates" in bedrock_md)
check("BEDROCK.md の境界表に SetEntityData の 332（ENTITY_FLAGS 71 件・index 39 は INTERACT_TEXT）がある",
      "1.9.0（332）は ENTITY_FLAGS が 71 件" in bedrock_md)
check("BEDROCK.md の境界表に MoveEntityDelta の代替列挙に 332 がある",
      "291・313・332・340・354・361・388・389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する" in bedrock_md)
check("BEDROCK.md の境界表に InventoryTransaction の 332（同一形式）がある",
      "1.9.0（332）も同一形式（BE-NOTES.md §34）" in bedrock_md)
check("BEDROCK.md のパレット例外に 332（1.11.0 世代を代用）がある",
      "例外は 332（1.9.0）も同じ：形式は name／meta のみの配列だが" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 63 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md のアイテム表が 38 本のバイナリになっている",
      "38 本のバイナリ" in bedrock_md)
check("BEDROCK.md の網羅率に 1.9.0 の代用明記つきエントリがある",
      "1.9.0 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "332 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 63 プロトコルの対応表／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／63 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.9.0 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・1.10.1・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（332 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち。332 にはヘルパー形式自体が無い）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("PlayerSkin は BedrockPackets／BedrockSession に未実装のまま（332 でも実装しない）",
      "PlayerSkin" not in pkt_src and "PlayerSkin" not in ses_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（332 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（332 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の FILE_PATH の番号は 332 でも無関係）",
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
