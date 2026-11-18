#!/usr/bin/env python3
"""BE 340（1.10.0〜1.10.1）対応の検証。

340 には minecraft-data の protocol.json が存在しない
（1.10.0／1.10.1／1.10.2 は HTTP 404。protocolVersions.json にも
340 のエントリは無い）。出典は CloudburstMC Protocol（ブランチ 3.0、
コミット 7163a6aedaefbc1d188431ef90ebc703b1138340）の v340
コーデックで、`.tmp-be/cloudburst-protocol/` に clone 済み。
Bedrock_v340 は Bedrock_v332 の CODEC を土台に新規登録 2 件
（LecternUpdate 124／VideoStreamConnect 125）と updateSerializer
7 件を行う。354 との差分（この実装の送受信範囲）はワイヤ形式には
無く、定義のみの差が 2 点（ENTITY_FLAGS の 74〜79 の番号ずれと
ENTITY_DATA の index 102 の有無）で、どちらもこの実装が書く
範囲には現れない（BE-NOTES.md §33、実測メモ
`.tmp-be/be-340-cloudburst-diff.md`）。

共有バージョン（1.10.0／1.10.1）は Wiki の個別ページの infobox
（Protocol version 340）で確認した（BE-NOTES.md §33）。

アイテム表は 354〜431 と同じ bedrock_item_ids_1_16_210.bin を
共用するが、340 については代用である（PMMP BedrockData の
bedrock-1.10.0 タグはツリー 5 ファイルのみで item_id_map.json
すら無く、1.10 系のランタイム ID の実データは存在しない。
340 自体の実測ではない）。StartGame のブロックパレットは
340 も 354 と同じ配列形式（name／meta のみ）だが、1.10 系の
パレット実データが存在しないため、中身は 1.11.0 世代の実測
レガシーパレット（bedrock_block_palette_1_11_0.bin、3,183 件）を
代用する（340 自体の実測ではない）。

使い方: python3 tools/verify-be-340-tables.py
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
print("== A. 出典の同一性（Cloudburst v340 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v340＝1.10.0",
      "| Bedrock_v340  |       1.10.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v354＝1.11.0（340 の 1 つ上の出典確認）",
      "| Bedrock_v354  |       1.11.0        |" in versions_md)

v340_src = read(CB / "v340/Bedrock_v340.java")
check("Bedrock_v340.java の CODEC は protocolVersion(340) と minecraftVersion(\"1.10.0\") を持つ",
      "protocolVersion(340)" in v340_src and 'minecraftVersion("1.10.0")' in v340_src)
check("Bedrock_v340.java の CODEC は Bedrock_v332.CODEC を土台にする",
      "Bedrock_v332.CODEC.toBuilder()" in v340_src)
check("Bedrock_v340.java は helper に新規 BedrockCodecHelper_v340 を使う",
      "new BedrockCodecHelper_v340(ENTITY_DATA, GAME_RULE_TYPES)" in v340_src
      and (CB / "v340/BedrockCodecHelper_v340.java").exists())
check("Bedrock_v340.java の updateSerializer は 7 件",
      v340_src.count("updateSerializer(") == 7,
      f"実際は {v340_src.count('updateSerializer(')}")
check("Bedrock_v340.java の新規登録は LecternUpdate（124）・VideoStreamConnect（125）の 2 件",
      v340_src.count(".registerPacket(") == 2
      and "LecternUpdatePacket::new" in v340_src and ", 124," in v340_src
      and "VideoStreamConnectPacket::new" in v340_src and ", 125," in v340_src)
check("Bedrock_v340.java に登録解除は無い（deregisterPacket 0 件）",
      "deregisterPacket(" not in v340_src)
check("Bedrock_v340.java に PlayerAuthInput は無い（388 で新規登録）",
      "PlayerAuthInput" not in v340_src)
check("v340 に StartGameSerializer_v340 は無い（StartGame の実効は _v332＋_v291）",
      not (CB / "v340/serializer/StartGameSerializer_v340.java").exists())

v354_src = read(CB / "v354/Bedrock_v354.java")
check("Bedrock_v354.java の CODEC は Bedrock_v340.CODEC を土台にする（340 の 1 つ上）",
      "Bedrock_v340.CODEC.toBuilder()" in v354_src)


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


flags340_idx, _ = chain_indices("ENTITY_FLAGS", 0, "v340")
check("ENTITY_FLAGS はチェーン累積で 340 が 80 件（0〜79 連続。354 で 87 件になる）",
      flags340_idx == set(range(80)),
      f"実際は {len(flags340_idx)} 件・最大 {max(flags340_idx) if flags340_idx else -1}")
data340_idx, data340_keys = chain_indices("ENTITY_DATA", 1, "v340")
check("ENTITY_DATA の index はチェーン累積で 340 が 102 件（0〜101 連続。102 TRADE_EXPERIENCE は 354 で追加）",
      data340_idx == set(range(102)),
      f"実際は {len(data340_idx)} 件・最大 {max(data340_idx) if data340_idx else -1}")
check("ENTITY_DATA の index 39 は 340 が HAS_NPC（v340 が INTERACT_TEXT から replace）",
      data340_keys.get("EntityDataTypes.HAS_NPC") == 39)
check("ENTITY_DATA の index 40 は 340 が SKIN_ID（354 と同じ）",
      data340_keys.get("EntityDataTypes.SKIN_ID") == 40)
pt340_idx, _ = chain_indices("PARTICLE_TYPES", 0, "v340")
check("PARTICLE_TYPES はチェーン累積で 340 が 55 件（0〜54 連続。354 と同じ）",
      pt340_idx == set(range(55)),
      f"実際は {len(pt340_idx)} 件・最大 {max(pt340_idx) if pt340_idx else -1}")

for name in ("1.10.0", "1.10.1"):
    check(f"340 の protocol.json は .tmp-be に無い（protocol-{name}.json は存在しない）",
          not (TMP / f"protocol-{name}.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 340 のエントリは無い",
      340 not in pv_protocols)

# A-2. PMMP bedrock-1.10.0 タグ（340 の世代のデータ。パレット・ID 表の材料自体が無い）
tree = json.loads(read(TMP / "be-340/bedrock-1.10.0-tree.json"))
tree_paths = [t["path"] for t in tree["tree"]]
check("PMMP bedrock-1.10.0 タグのツリーは 5 エントリ",
      len(tree_paths) == 5, f"実際は {len(tree_paths)}")
check("bedrock-1.10.0 タグに required_item_list.json／item_id_map.json は無い（340 のランタイム ID 実データは存在しない）",
      "required_item_list.json" not in tree_paths
      and "item_id_map.json" not in tree_paths)
check("bedrock-1.10.0 タグに required_block_states.json／block_id_map.json は無い（340 のパレット実データは存在しない）",
      "required_block_states.json" not in tree_paths
      and "block_id_map.json" not in tree_paths)
ci10 = TMP / "be-340/BedrockData-bedrock-1.10.0/creativeitems.json"
ci10_data = json.loads(read(ci10))
check("PMMP bedrock-1.10.0 の creativeitems.json は 1,254 件・SHA-1 59a9da08…（旧来の数値 ID でランタイム ID ではない）",
      len(ci10_data) == 1254
      and sha1_of(ci10) == "59a9da08fb1cb3969b49c3225641d91160f85580"
      and isinstance(ci10_data[0].get("id"), int))

# ---------------------------------------------------------------------------
print("== B. 340 のパケット一覧（354 の一覧からの差分） ==")

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
check("354 のパケット一覧は 127 件（§32 の再確認）", len(ids354) == 127)

# 340 は 354 から v354 の新規登録 {130 on_screen_texture_animation,
# 131 map_create_locked_copy} を除いたもの（354 の集合からの逆算で、
# 1.10.0 自体の protocol.json を実測したものではない。
# 実測メモ `.tmp-be/be-340-cloudburst-diff.md` §3.2 参照）
ids340 = {i: n for i, n in ids354.items() if i not in {130, 131}}
check("340 のパケット一覧は 125 件（protocol.json 由来の母集団）",
      len(ids340) == 125, f"実際は {len(ids340)}")
check("340 の最大 ID は 128（130・131 は v354 の新規登録）",
      max(ids340) == 128)
check("340 の欠番は [37, 116, 126]（ID は 1 始まり）",
      [i for i in range(1, 129) if i not in ids340] == [37, 116, 126])
check("340 の一覧に player_auth_input（144）は無い（388 で新規登録）",
      144 not in ids340)
check("340 の一覧に add_hanging_entity（16）がある（361 で登録解除）",
      16 in ids340)
# Cloudburst の登録ベースでは 340 は 124 件・最大 ID 125・欠番
# [116] になる（チェーン積み上げ：v291 の 116 件 → v313 で 121 →
# v332 で 122 → v340 で +2＝124 件。BE-NOTES.md §33、実測メモ
# `.tmp-be/be-340-cloudburst-diff.md`）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 340 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 340 の一覧の {packet_name} と一致",
              value in ids340 and ids340[value].startswith(packet_name),
              f"一覧では {ids340.get(value)}")
check("PACKET_PLAYER_AUTH_INPUT（144）は 340 の一覧に無い（パケット自体が存在しない）",
      const_value("PACKET_PLAYER_AUTH_INPUT") == 144
      and const_value("PACKET_PLAYER_AUTH_INPUT") not in ids340)

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（340 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_10_1(340, \"1.10.1\") がある",
      'V1_10_1(340, "1.10.1")' in ver_src)
check("V1_10_1 は enum の先頭（V1_11_4 より前）",
      ver_src.index("V1_10_1(340") < ver_src.index("V1_11_4(354"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("クラス javadoc の protocol.json ファイル数は 51 のまま（340 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 340 は 1.10.0／1.10.1 の共有と Wiki infobox の出典がある",
      "1.10.0 / 1.10.1 で共有する" in ver_src
      and "Protocol version 340" in ver_src)
check("enum コメントに 340 の 1 つ下は 332（1.9.0）とある",
      "340 の 1 つ下は 332" in ver_src)
check("enum コメントに 341〜349 は Wiki 一覧表に行が無い番号帯とある",
      "341〜349 は Wiki の Protocol version" in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 340 -> \"1_16_210\" がある",
      'case 340 -> "1_16_210";' in ver_src)
check("case 340 のコメントに「340 自体の実測ではない」の代用明記がある",
      "340 自体の実測ではない" in ver_src)
check("itemTableTag の case 354／361／388／389／390／407／408／419／422／428／431 は不変",
      'case 354 -> "1_16_210";' in ver_src
      and 'case 361 -> "1_16_210";' in ver_src
      and 'case 388 -> "1_16_210";' in ver_src
      and 'case 389 -> "1_16_210";' in ver_src
      and 'case 390 -> "1_16_210";' in ver_src
      and 'case 407 -> "1_16_210";' in ver_src and 'case 408 -> "1_16_210";' in ver_src
      and 'case 419 -> "1_16_210";' in ver_src and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（354〜431 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（340 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("340 用の .bin（1_10_1／1_10_0）は作っていない",
      not any(b in ("bedrock_item_ids_1_10_1.bin", "bedrock_item_ids_1_10_0.bin")
              for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 340 のエントリは無い（354〜422 と同じ扱い）",
      "340" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 340 世代の形式を確認） ==")

sg332 = read(CB / "v332/serializer/StartGameSerializer_v332.java")
check("StartGameSerializer_v332（340 の LevelSettings 実効）に onlySpawningV1Villagers は無い（30 フィールド。354 と同じ）",
      "OnlySpawningV1Villagers" not in sg332)
sg291 = read(CB / "v291/serializer/StartGameSerializer_v291.java")
check("StartGameSerializer_v291（340 のトップレベル実効）はパレットを配列形式で書く（件数＋name／meta ShortLE のみ。id は無い。354 と同じ）",
      "VarInts.writeUnsignedInt(buffer, palette.size())" in sg291
      and 'helper.writeString(buffer, blockTag.getString("name"))' in sg291
      and 'buffer.writeShortLE(entry.getShort("meta"))' in sg291
      and 'entry.getShort("id")' not in sg291)
check("StartGameSerializer_v291 に itemDefinitions は無い（パレットの直後は multiplayerCorrelationId。354 と同じ）",
      "ItemDefinition" not in sg291
      and "helper.writeString(buffer, packet.getMultiplayerCorrelationId())" in sg291)

fc291 = read(CB / "v291/serializer/FullChunkDataSerializer_v291.java")
check("FullChunkDataSerializer_v291（340 の LevelChunk 実効）は chunkX／chunkZ と payload のみ（354 と同じ）",
      "VarInts.writeInt(buffer, packet.getChunkX())" in fc291
      and "getSubChunksLength" not in fc291 and "CachingEnabled" not in fc291)

apaint332 = read(CB / "v332/serializer/AddPaintingSerializer_v332.java")
check("AddPaintingSerializer_v332（340 の実効）は座標を writeBlockPosition で書く（354 と同じ）",
      "helper.writeBlockPosition(buffer, packet.getPosition().toInt())" in apaint332)

ap291 = read(CB / "v291/serializer/AddPlayerSerializer_v291.java")
check("AddPlayerSerializer_v291 に buildPlatform は無い（340 の実効。354 と同じ）",
      "BuildPlatform" not in ap291)
pl291 = read(CB / "v291/serializer/PlayerListSerializer_v291.java")
check("PlayerListSerializer_v291 の add は旧直書き（340 の実効。354 と同じ）",
      "helper.writeString(buffer, skin.getSkinId())" in pl291
      and "helper.writeByteArray(buffer, skin.getSkinData().getImage())" in pl291
      and "trustedSkin" not in pl291)
rps313 = read(CB / "v313/serializer/ResourcePackStackSerializer_v313.java")
check("ResourcePackStackSerializer_v313 に gameVersion は無い（340 の実効。354 と同じ）",
      "GameVersion" not in rps313)

for hv in ("v291", "v313", "v332", "v340"):
    hsrc = read(CB / f"{hv}/BedrockCodecHelper_{hv}.java")
    check(f"BedrockCodecHelper_{hv} に readSkin／writeSkin の上書きは無い（340 のヘルパー継承チェーンに Skin 形式が存在しない）",
          "readSkin" not in hsrc and "writeSkin" not in hsrc)

helper340 = read(CB / "v340/BedrockCodecHelper_v340.java")
check("BedrockCodecHelper_v340 の readItemUse／writeItemUse は末尾に blockDefinition の runtimeId を足す（354 も同じヘルパーを使うため Transaction の ITEM_USE は 340＝354）",
      "this.blockDefinitions.getDefinition(VarInts.readUnsignedInt(buffer))" in helper340
      and "writeItemUse" in helper340)
check("BedrockCodecHelper_v340 の readItem／writeItem は shield のときだけ blockingTicks を足す（354 と同じ）",
      "blockingTicks" in helper340)

it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（340 は transaction_type から始まる。354 と同じ）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)

check("MoveEntityDelta は 340 では _v291 が実効（Bedrock_v340.java は触っていない。セッションは MoveEntity で代替）",
      "MoveEntityDeltaPacket" not in v340_src)

# ---------------------------------------------------------------------------
print("== G. コード側の実装（述語・分岐・範囲表記・テスト名） ==")

for pred in ("hasStartGameOnlySpawningV1Villagers", "hasLevelChunkSubChunkCount"):
    check(f"述語 {pred}() が protocol >= 361 で定義されている（340 では新しい境界は生まれない）",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol >= 361;", ver_src)
          is not None)
check("述語 hasStartGameLegacyPaletteEntryId() が protocol == 361 で定義されている（不変）",
      re.search(r"public boolean hasStartGameLegacyPaletteEntryId\(\) \{\s*return protocol == 361;",
                ver_src) is not None)
check("述語 hasItemstates() の下限が 361 のまま（protocol >= 361 && protocol <= 748）",
      re.search(r"public boolean hasItemstates\(\) \{\s*return protocol >= 361 && protocol <= 748;",
                ver_src) is not None)
check("述語 addPaintingUsesBlockCoordinates() が protocol <= 354 || 422〜428 のまま（340 は <= 354 に含まれる）",
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
check("encodeStartGame がパレットを hasStartGameLegacyPaletteEntryId で出し分けている（1 箇所。不変。340 は id 無し側）",
      pkt_src.count("version.hasStartGameLegacyPaletteEntryId()") == 1)
check("encodeStartGame が 1.11.0 パレットを使う（load1_11_0 は 1 箇所。340 もこれを代用する）",
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
check("BedrockLegacyBlockPalette が bedrock_block_palette_1_11_0.bin を読む（load1_11_0。340 も代用で使う）",
      'RESOURCE_NAME_1_11_0 = "bedrock_block_palette_1_11_0.bin"' in legacy_src
      and "load1_11_0()" in legacy_src)
check("BedrockLegacyBlockPalette の javadoc に 340 の代用明記がある",
      "340（1.10.1）も形式は同じ" in legacy_src
      and "340 自体の実測ではない" in legacy_src)
pal11_bin = RES / "bedrock_block_palette_1_11_0.bin"
check("bedrock_block_palette_1_11_0.bin は 83,378 バイト・SHA-1 15ded0d9…のまま（340 は新規パレットを作らず代用）",
      len(pal11_bin.read_bytes()) == 83378
      and sha1_of(pal11_bin) == "15ded0d92930b81f3cbdbcdf581890eee8a6b07c")
pal12_bin = RES / "bedrock_block_palette_1_12_0.bin"
check("bedrock_block_palette_1_12_0.bin は 92,944 バイト・SHA-1 3553c5c9…のまま（不変）",
      len(pal12_bin.read_bytes()) == 92944
      and sha1_of(pal12_bin) == "3553c5c934caf5be473a954a1222a1a5bb90e6bf")
check("340 用のパレット .bin（1_10_0）は作っていない（実データが無いため代用）",
      not (RES / "bedrock_block_palette_1_10_0.bin").exists())

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession の切断メッセージが 291-2193 になっている",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in ses_src)
check("BedrockSession が 340 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
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
      and "void allSixtySupportedProtocolsResolve()" not in vt_src)
check("BedrockVersionTest に protocol340BoundaryGates がある",
      "protocol340BoundaryGates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "allSixtyTwoProtocolsReachStartGame" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame340Equals354", "levelChunk340Equals354",
              "addPainting340UsesBlockCoordinates",
              "playerListAndResourcePacks340Equal354",
              "decodeInventoryTransactionV0As340"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)

# ---------------------------------------------------------------------------
print("== H. ブロック表（不変の確認） ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 が d2b6acbb…のまま",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 が ee36fffb…のまま",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
check("1_10 系のブロックランタイム ID .bin は作っていない（レガシーパレットとは別物）",
      not list(RES.glob("bedrock_block_runtime_ids_1_10_*.bin")))

# ---------------------------------------------------------------------------
print("== I. 文書（BEDROCK.md／PLAYTEST-BEDROCK.md） ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、60 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 61 個見出しで、340 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 340 | 1.10.0 / 1.10.1 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 63 プロトコル／54 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407・390・389・388・361・354・340 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に StartGame の 340（354 と同一形式・パレット代用）がある",
      "1.10.1（340）も同一形式（実効シリアライザは 354 と全て同じ" in bedrock_md)
check("BEDROCK.md の境界表に LevelChunk の 340（subChunkCount 無し）がある",
      "1.10.1（340）も同じ（BE-NOTES.md §33）。1.9.0（332）も同じ（BE-NOTES.md §34）。1.8.1（313）も同じ（BE-NOTES.md §35）。1.7.1（291）も同じ（BE-NOTES.md §36）。26.60（2225）は末尾に client_biome_update が付く" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の 340（ブロック座標）がある",
      "coordinates は 1.7.1・1.8.1・1.9.0・1.10.1・1.11.4 と 1.16.201／1.16.210 が BlockCoordinates" in bedrock_md)
check("BEDROCK.md の境界表に SetEntityData の 340（ENTITY_FLAGS 80 件・index 102 無し）がある",
      "1.10.1（340）は ENTITY_FLAGS が 80 件" in bedrock_md)
check("BEDROCK.md の境界表に MoveEntityDelta の代替列挙に 340 がある",
      "291・313・332・340・354・361・388・389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する" in bedrock_md)
check("BEDROCK.md の境界表に InventoryTransaction の 340（同一形式）がある",
      "1.10.1（340）も同一形式（BE-NOTES.md §33）" in bedrock_md)
check("BEDROCK.md のパレット例外に 340（1.11.0 世代を代用）がある",
      "例外は 340（1.10.1）も同じ：形式は name／meta のみの配列だが" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 63 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md のアイテム表が 38 本のバイナリになっている",
      "38 本のバイナリ" in bedrock_md)
check("BEDROCK.md の網羅率に 1.10.1 の代用明記つきエントリがある",
      "1.10.1 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "340 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 63 プロトコルの対応表／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／63 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.10.1 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・1.10.1・1.11.4・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（340 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち。340 にはヘルパー形式自体が無い）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("PlayerSkin は BedrockPackets／BedrockSession に未実装のまま（340 でも実装しない）",
      "PlayerSkin" not in pkt_src and "PlayerSkin" not in ses_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（340 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（340 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の shift は 340 でも無関係）",
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
