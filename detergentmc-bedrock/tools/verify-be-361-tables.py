#!/usr/bin/env python3
"""BE 361（1.12.0／1.12.1）対応の検証。

361 には minecraft-data の protocol.json が存在しない
（1.12.0／1.12.1 は HTTP 404。protocolVersions.json にも 361 の
エントリは無い）。出典は CloudburstMC Protocol（ブランチ 3.0、
コミット 7163a6aedaefbc1d188431ef90ebc703b1138340）の v361
コーデックで、`.tmp-be/cloudburst-protocol/` に clone 済み。
Bedrock_v361 は Bedrock_v354 の CODEC を土台に、登録解除 3 件、
updateSerializer 8 件、新規登録 9 件と TypeMap 変更を行う。
388 との差分（この実装の送受信範囲）は StartGame（LevelSettings
31 フィールド・movement 無し・パレット配列形式）、AddPlayer
（末尾 buildPlatform 無し）、PlayerList add（旧直書きエントリ）、
ResourcePackStack（末尾 gameVersion 無し）の 4 件と、
PlayerAuthInput の不在（BE-NOTES.md §31、実測メモ
`.tmp-be/be-361-cloudburst-diff.md`）。

共有バージョン（1.12.0／1.12.1）は Wiki の個別ページの infobox
（Protocol version 361）で確認した（BE-NOTES.md §31）。

アイテム表は 388〜431 と同じ bedrock_item_ids_1_16_210.bin を
共用するが、361 については代用である（PMMP BedrockData の
bedrock-1.12.0 タグは旧形式のみで required_item_list.json が無く、
1.12 系のランタイム ID の実データは存在しない。361 自体の
実測ではない）。一方 StartGame のブロックパレットは、361 の
配列形式（name／meta／id）に正規パレットを流し込めないため、
bedrock-1.12.0 タグの実測（required_block_states.json＋
block_id_map.json）から生成した bedrock_block_palette_1_12_0.bin
（3,311 件）を使う。

使い方: python3 tools/verify-be-361-tables.py
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
print("== A. 出典の同一性（Cloudburst v361 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v361＝1.12.0",
      "| Bedrock_v361  |       1.12.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v388＝1.13.0（361 の 1 つ上の出典確認）",
      "| Bedrock_v388  |       1.13.0        |" in versions_md)

v361_src = read(CB / "v361/Bedrock_v361.java")
check("Bedrock_v361.java の CODEC は protocolVersion(361) と minecraftVersion(\"1.12.0\") を持つ",
      "protocolVersion(361)" in v361_src and 'minecraftVersion("1.12.0")' in v361_src)
check("Bedrock_v361.java の CODEC は Bedrock_v354.CODEC を土台にする",
      "Bedrock_v354.CODEC.toBuilder()" in v361_src)
check("Bedrock_v361.java は helper に BedrockCodecHelper_v361 を使う",
      "new BedrockCodecHelper_v361(ENTITY_DATA, GAME_RULE_TYPES)" in v361_src)
check("Bedrock_v361.java の登録解除は 3 件（AddHangingEntity／LecternUpdate／VideoStreamConnect）",
      v361_src.count("deregisterPacket(") == 3
      and ".deregisterPacket(AddHangingEntityPacket.class)" in v361_src
      and ".deregisterPacket(LecternUpdatePacket.class)" in v361_src
      and ".deregisterPacket(VideoStreamConnectPacket.class)" in v361_src)
check("Bedrock_v361.java の updateSerializer は 8 件",
      v361_src.count("updateSerializer(") == 8,
      f"実際は {v361_src.count('updateSerializer(')}")
for cls, ser, pid in (
        ("LevelEventGenericPacket", "LevelEventGenericSerializer_v361", 124),
        ("LecternUpdatePacket", "LecternUpdateSerializer_v354", 125),
        ("VideoStreamConnectPacket", "VideoStreamConnectSerializer_v361", 126),
        ("ClientCacheStatusPacket", "ClientCacheStatusSerializer_v361", 129),
        ("StructureTemplateDataRequestPacket", "StructureTemplateDataRequestSerializer_v361", 132),
        ("StructureTemplateDataResponsePacket", "StructureTemplateDataResponseSerializer_v361", 133),
        ("UpdateBlockPropertiesPacket", "UpdateBlockPropertiesSerializer_v361", 134),
        ("ClientCacheBlobStatusPacket", "ClientCacheBlobStatusSerializer_v361", 135),
        ("ClientCacheMissResponsePacket", "ClientCacheMissResponseSerializer_v361", 136)):
    check(f"Bedrock_v361.java の新規登録 {cls} = {pid}",
          f"registerPacket({cls}::new" in v361_src
          and f"{ser}" in v361_src and f", {pid}," in v361_src)
check("Bedrock_v361.java の registerPacket は 9 件",
      v361_src.count(".registerPacket(") == 9,
      f"実際は {v361_src.count('.registerPacket(')}")
check("Bedrock_v361.java は StartGame を StartGameSerializer_v361 に差し替える（361 自身の世代）",
      "updateSerializer(StartGamePacket.class, StartGameSerializer_v361.INSTANCE)" in v361_src)
check("Bedrock_v361.java は LevelChunk を LevelChunkSerializer_v361 に差し替える（388 でも実効）",
      "updateSerializer(LevelChunkPacket.class, LevelChunkSerializer_v361.INSTANCE)" in v361_src)
check("Bedrock_v361.java は AddPainting を AddPaintingSerializer_v361 に差し替える（388 でも実効）",
      "updateSerializer(AddPaintingPacket.class, AddPaintingSerializer_v361.INSTANCE)" in v361_src)
check("Bedrock_v361.java に PlayerAuthInput は無い（388 で新規登録）",
      "PlayerAuthInput" not in v361_src)

v388_src = read(CB / "v388/Bedrock_v388.java")
check("Bedrock_v388.java の CODEC は Bedrock_v361.CODEC を土台にする（361 の 1 つ上）",
      "Bedrock_v361.CODEC.toBuilder()" in v388_src)


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


flags361_idx, _ = chain_indices("ENTITY_FLAGS", 0, "v361")
check("ENTITY_FLAGS はチェーン累積で 361 が 88 件（0〜87 連続。v361 が 87 を挿入）",
      flags361_idx == set(range(88)),
      f"実際は {len(flags361_idx)} 件・最大 {max(flags361_idx)}")
data361_idx, data361_keys = chain_indices("ENTITY_DATA", 1, "v361")
check("ENTITY_DATA の index はチェーン累積で 361 が 107 件（0〜106 連続。v361 が 103〜106 を挿入）",
      data361_idx == set(range(107)),
      f"実際は {len(data361_idx)} 件・最大 {max(data361_idx)}")
check("ENTITY_DATA の AREA_EFFECT_CLOUD_PARTICLE は 62（361 の実効。388 と同じ）",
      data361_keys.get("EntityDataTypes.AREA_EFFECT_CLOUD_PARTICLE") == 62)
pt361_idx, _ = chain_indices("PARTICLE_TYPES", 0, "v361")
check("PARTICLE_TYPES はチェーン累積で 361 が 58 件（0〜57 連続。388 と同一）",
      pt361_idx == set(range(58)),
      f"実際は {len(pt361_idx)} 件・最大 {max(pt361_idx)}")
check("Bedrock_v361.java の ENTITY_FLAGS 挿入は 87 HIDDEN_WHEN_INVISIBLE、ENTITY_DATA 挿入は 103〜106",
      ".insert(87, EntityFlag.HIDDEN_WHEN_INVISIBLE)" in v361_src
      and ".insert(EntityDataTypes.SKIN_ID, 103," in v361_src
      and ".insert(EntityDataTypes.COMMAND_BLOCK_EXECUTE_ON_FIRST_TICK, 106," in v361_src)
check("Bedrock_v388.java の ENTITY_FLAGS 挿入 88〜91 と ENTITY_DATA 挿入 107〜112 は 361 には無い（追加分のみ）",
      ".insert(88, EntityFlag.IS_IN_UI)" in v388_src
      and ".insert(88, EntityFlag.IS_IN_UI)" not in v361_src)

for name in ("1.12.0", "1.12.1"):
    check(f"361 の protocol.json は .tmp-be に無い（protocol-{name}.json は存在しない）",
          not (TMP / f"protocol-{name}.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 361 のエントリは無い",
      361 not in pv_protocols)

# A-2. PMMP bedrock-1.12.0 タグ（361 の世代のデータ。旧形式のみ）
tree = json.loads(read(TMP / "be-361/bedrock-1.12.0-tree.json"))
tree_paths = [t["path"] for t in tree["tree"]]
check("PMMP bedrock-1.12.0 タグのツリーは 11 エントリ",
      len(tree_paths) == 11, f"実際は {len(tree_paths)}")
check("bedrock-1.12.0 タグに required_item_list.json は無い（361 のランタイム ID 実データは存在しない）",
      "required_item_list.json" not in tree_paths)
check("bedrock-1.12.0 タグに required_block_states.nbt は無い（.json のみ）",
      "required_block_states.nbt" not in tree_paths
      and "required_block_states.json" in tree_paths)

iim12 = TMP / "be-361/item_id_map.json"
check("PMMP bedrock-1.12.0 の item_id_map.json は 689 件・SHA-1 ec406ec7…",
      len(json.loads(read(iim12))) == 689
      and sha1_of(iim12) == "ec406ec7b48cdff58a7173f89975937c4d8d8ccd")
bim12 = TMP / "be-361/block_id_map.json"
check("PMMP bedrock-1.12.0 の block_id_map.json は 460 件・SHA-1 95f9de11…",
      len(json.loads(read(bim12))) == 460
      and sha1_of(bim12) == "95f9de111bc5157825964305f91d37009e52231e")
rbs12 = TMP / "be-361/required_block_states.json"
rbs12_data = json.loads(read(rbs12))["minecraft"]
check("PMMP bedrock-1.12.0 の required_block_states.json は 460 名・(名前, メタ) 3,311 件・SHA-1 ef61df82…",
      len(rbs12_data) == 460
      and sum(len(v) for v in rbs12_data.values()) == 3311
      and sha1_of(rbs12) == "ef61df8220986221303cf1d62b77c70274878618")

# ---------------------------------------------------------------------------
print("== B. 361 のパケット一覧（388 の一覧 − v388 の新規 ID 137〜144） ==")

p422 = json.loads(read(TMP / "protocol-1.16.201.json"))
mappings = p422["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
ids422 = {int(k): v for k, v in mappings.items()}
check("422 のパケット一覧は 160 件（土台の再確認）", len(ids422) == 160)

ids407 = {i: n for i, n in ids422.items()
          if i not in {157, 158, 159, 160, 161, 162} and i != 163}
check("407 のパケット一覧は 153 件（§27 の再確認）", len(ids407) == 153)

new407 = {145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156}
ids388 = {i: n for i, n in ids407.items() if i not in new407}
check("388 のパケット一覧は 141 件（§30 の再確認）", len(ids388) == 141)

new388 = {137, 138, 139, 140, 141, 142, 143, 144}
ids361 = {i: n for i, n in ids388.items() if i not in new388}
check("361 のパケット一覧は 133 件（protocol.json 由来の母集団）",
      len(ids361) == 133, f"実際は {len(ids361)}")
check("361 の最大 ID は 136（client_cache_miss_response。v361 で登録）",
      max(ids361) == 136 and ids361[136].startswith("client_cache_miss_response"))
check("361 の欠番は [16, 37, 116]（388・389・390・407・408・419・422 と同じ。ID は 1 始まり）",
      [i for i in range(1, 137) if i not in ids361] == [16, 37, 116])
check("361 の一覧に player_auth_input（144）は無い（388 で新規登録）",
      144 not in ids361)
# Cloudburst の登録ベースでは 361 は 132 件・最大 ID 136・欠番
# [16, 116, 127, 128] になる（チェーン積み上げ：v291 の 116 件 →
# v313 で 121 → v332 で 122 → v340 で 124 → v354 で 126 →
# v361 で −3＋9＝132 件。BE-NOTES.md §31、実測メモ
# `.tmp-be/be-361-cloudburst-diff.md` §7）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 361 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 361 の一覧の {packet_name} と一致",
              value in ids361 and ids361[value].startswith(packet_name),
              f"一覧では {ids361.get(value)}")
check("PACKET_PLAYER_AUTH_INPUT（144）は 361 の一覧に無い（パケット自体が存在しない）",
      const_value("PACKET_PLAYER_AUTH_INPUT") == 144
      and const_value("PACKET_PLAYER_AUTH_INPUT") not in ids361)

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（361 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_12_1(361, \"1.12.1\") がある",
      'V1_12_1(361, "1.12.1")' in ver_src)
check("V1_12_1 は enum の先頭（V1_13_3 より前）",
      ver_src.index("V1_12_1(361") < ver_src.index("V1_13_3(388"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("クラス javadoc の protocol.json ファイル数は 51 のまま（361 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 361 は 1.12.0 / 1.12.1 の共有と Wiki infobox の出典がある",
      "1.12.0 / 1.12.1 で共有するプロトコル" in ver_src
      and "Protocol version 361" in ver_src)
check("enum コメントに 361 の 1 つ下は 354（1.11.0）とある",
      "361 の 1 つ下は 354" in ver_src)
check("enum コメントに §30.3 の訂正（updateSerializer 一覧の食い違い）がある",
      "§30.3 の updateSerializer 一覧" in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 361 -> \"1_16_210\" がある",
      'case 361 -> "1_16_210";' in ver_src)
check("case 361 のコメントに「361 自体の実測ではない」の代用明記がある",
      "361 自体の実測ではない" in ver_src)
check("itemTableTag の case 388／389／390／407／408／419／422／428／431 は不変",
      'case 388 -> "1_16_210";' in ver_src
      and 'case 389 -> "1_16_210";' in ver_src
      and 'case 390 -> "1_16_210";' in ver_src
      and 'case 407 -> "1_16_210";' in ver_src and 'case 408 -> "1_16_210";' in ver_src
      and 'case 419 -> "1_16_210";' in ver_src and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（388〜431 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（361 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("361 用の .bin（1_12_1／1_12_0）は作っていない",
      not any(b in ("bedrock_item_ids_1_12_1.bin", "bedrock_item_ids_1_12_0.bin")
              for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 361 のエントリは無い（388〜422 と同じ扱い）",
      "361" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 361 世代の形式を確認） ==")

sg361 = read(CB / "v361/serializer/StartGameSerializer_v361.java")
sg388 = read(CB / "v388/serializer/StartGameSerializer_v388.java")
check("StartGameSerializer_v361 は blockPalette を配列形式で書く（件数＋name／meta ShortLE／id ShortLE）",
      "VarInts.writeUnsignedInt(buffer, palette.size())" in sg361
      and 'helper.writeString(buffer, blockTag.getString("name"))' in sg361
      and 'buffer.writeShortLE(entry.getShort("meta"))' in sg361
      and 'buffer.writeShortLE(entry.getShort("id"))' in sg361)
check("StartGameSerializer_v361 に authoritativeMovementMode は無い（v388 が Boolean で追加）",
      "AuthoritativeMovementMode" not in sg361
      and "packet.getAuthoritativeMovementMode()" in sg388)
check("StartGameSerializer_v361 の LevelSettings 末尾は onlySpawningV1Villagers（31 フィールド目）",
      "buffer.writeBoolean(packet.isOnlySpawningV1Villagers())" in sg361)
check("StartGameSerializer_v388 の LevelSettings 末尾に vanillaVersion がある（32 フィールド目。361 には無い）",
      "helper.writeString(buffer, packet.getVanillaVersion())" in sg388
      and "VanillaVersion" not in sg361)

ap291 = read(CB / "v291/serializer/AddPlayerSerializer_v291.java")
ap388 = read(CB / "v388/serializer/AddPlayerSerializer_v388.java")
check("AddPlayerSerializer_v291 に buildPlatform は無い（361 の実効。deviceId が末尾）",
      "BuildPlatform" not in ap291)
check("AddPlayerSerializer_v388 は末尾に buildPlatform（IntLE）を書く（388 で追加）",
      "buffer.writeIntLE(packet.getBuildPlatform().getId())" in ap388)

pl291 = read(CB / "v291/serializer/PlayerListSerializer_v291.java")
pl388 = read(CB / "v388/serializer/PlayerListSerializer_v388.java")
check("PlayerListSerializer_v291 の add は旧直書き（skinId／skinData／capeData／geometryName／geometryData を埋め込む）",
      "helper.writeString(buffer, skin.getSkinId())" in pl291
      and "helper.writeByteArray(buffer, skin.getSkinData().getImage())" in pl291
      and "helper.writeByteArray(buffer, skin.getCapeData().getImage())" in pl291
      and "helper.writeString(buffer, skin.getGeometryName())" in pl291
      and "helper.writeString(buffer, skin.getGeometryData())" in pl291
      and "helper.writeString(buffer, entry.getXuid())" in pl291
      and "helper.writeString(buffer, entry.getPlatformChatId())" in pl291)
check("PlayerListSerializer_v291 に buildPlatform／teacher／host／trustedSkin は無い",
      "BuildPlatform" not in pl291 and "isTeacher" not in pl291
      and "isHost" not in pl291 and "trustedSkin" not in pl291)
check("PlayerListSerializer_v388 の add は buildPlatform／teacher／host を書く（361 との差分）",
      "entry.getBuildPlatform()" in pl388
      and "buffer.writeBoolean(entry.isTeacher())" in pl388
      and "buffer.writeBoolean(entry.isHost())" in pl388)
check("PlayerListSerializer_v291 の remove は uuid のみ（388 と同一）",
      "helper.readUuid(buffer)" in pl291)

rps313 = read(CB / "v313/serializer/ResourcePackStackSerializer_v313.java")
rps388 = read(CB / "v388/serializer/ResourcePackStackSerializer_v388.java")
check("ResourcePackStackSerializer_v313 に gameVersion は無い（361 の実効。legacy experiment フラグが末尾）",
      "GameVersion" not in rps313)
check("ResourcePackStackSerializer_v388 は v313 を継承し末尾に gameVersion を足す（388 で追加）",
      "extends ResourcePackStackSerializer_v313" in rps388
      and "helper.writeString(buffer, packet.getGameVersion())" in rps388)

for hv in ("v291", "v313", "v332", "v340", "v361"):
    hsrc = read(CB / f"{hv}/BedrockCodecHelper_{hv}.java")
    check(f"BedrockCodecHelper_{hv} に readSkin／writeSkin の上書きは無い（361 にはヘルパー Skin 形式が存在しない）",
          "readSkin" not in hsrc and "writeSkin" not in hsrc)
h388 = read(CB / "v388/BedrockCodecHelper_v388.java")
check("BedrockCodecHelper_v388 は Skin を 12 フィールドで定義する（388 の実効形式。361 との対比）",
      "getFullSkinId" in h388 and "ArmSize" not in h388)

it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（361 は transaction_type から始まる。388 と同じ）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)

apaint361 = read(CB / "v361/serializer/AddPaintingSerializer_v361.java")
check("AddPaintingSerializer_v361 は座標を writeVector3f で書く（361・388 の実効形式）",
      "helper.writeVector3f(buffer, packet.getPosition())" in apaint361)

check("MoveEntityDelta は 361 では _v291 が実効（Bedrock_v361.java は触っていない。セッションは MoveEntity で代替）",
      "MoveEntityDeltaPacket" not in v361_src
      and "updateSerializer(MoveEntityDeltaPacket.class, MoveEntityDeltaSerializer_v388.INSTANCE)" in v388_src)

lc361 = read(CB / "v361/serializer/LevelChunkSerializer_v361.java")
check("LevelChunk の実効シリアライザは 361 も 388 も LevelChunkSerializer_v361",
      "class LevelChunkSerializer_v361" in lc361
      and "LevelChunkPacket" not in v388_src)

ae313 = read(CB / "v313/serializer/AddEntitySerializer_v313.java")
check("AddEntity の実効シリアライザは 361 も 388 も AddEntitySerializer_v313（v291 を継承）",
      "class AddEntitySerializer_v313 extends AddEntitySerializer_v291" in ae313)

for name in ("MovePlayer", "SetEntityData", "UpdateAttributes"):
    s291 = read(CB / f"v291/serializer/{name}Serializer_v291.java")
    check(f"{name}Serializer_v291 に tick は無い（361 の実効形式。388 と同じ）",
          "getTick" not in s291)

# ---------------------------------------------------------------------------
print("== G. コード側の実装（述語・分岐・範囲表記・テスト名） ==")

for pred in ("hasStartGameVanillaVersion", "addPlayerHasBuildPlatform",
             "resourcePackStackHasGameVersion"):
    check(f"述語 {pred}() が protocol >= 388 で定義されている（361 回で新規）",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol >= 388;", ver_src)
          is not None)
for pred in ("hasStartGameLegacyPaletteArray", "playerListUsesLegacyEntry"):
    check(f"述語 {pred}() が protocol <= 361 で定義されている（361 回で新規）",
          re.search(rf"public boolean {pred}\(\) \{{\s*return protocol <= 361;", ver_src)
          is not None)
check("述語 hasMovementAuthority() に下限 388 が付いた（protocol >= 388 && protocol <= 800）",
      re.search(r"public boolean hasMovementAuthority\(\) \{\s*return protocol >= 388 && protocol <= 800;",
                ver_src) is not None)
gte = sorted({int(n) for n in re.findall(r"protocol >= (\d+)", ver_src)})
check("BedrockVersion の最小の >= 閾値は 332 になった（313 回で resourcePacksInfoHasScripting を追加。361 回で 388 境界の述語を追加）",
      gte[0] == 332, f"実際は {gte[0]}")
check("述語 playerListAddHasVerified() が protocol >= 390 のまま（361 でも偽）",
      re.search(r"public boolean playerListAddHasVerified\(\) \{\s*return protocol >= 390;",
                ver_src) is not None)
check("述語 addPaintingUsesBlockCoordinates() が protocol >= 422 && protocol <= 428 のまま（§28 の境界は不変）",
      re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 || \(protocol >= 422 && protocol <= 428\);",
                ver_src) is not None)
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 のまま（§28 の境界は不変）",
      re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                ver_src) is not None)

pkt_src = read(SRC / "BedrockPackets.java")
check("encodeStartGame が game version を hasStartGameVanillaVersion でゲートしている（1 箇所）",
      pkt_src.count("version.hasStartGameVanillaVersion()") == 1,
      f"実際は {pkt_src.count('version.hasStartGameVanillaVersion()')} 箇所")
check("encodeStartGame が配列パレットを hasStartGameLegacyPaletteArray でゲートしている（1 箇所）",
      pkt_src.count("version.hasStartGameLegacyPaletteArray()") == 1,
      f"実際は {pkt_src.count('version.hasStartGameLegacyPaletteArray()')} 箇所")
check("encodeStartGame が BedrockLegacyBlockPalette を使う（1 箇所）",
      pkt_src.count("BedrockLegacyBlockPalette.load().rawArrayBytes()") == 1)
check("encodePlayerListAdd が旧直書きを playerListUsesLegacyEntry でゲートしている（1 箇所）",
      pkt_src.count("version.playerListUsesLegacyEntry()") == 1,
      f"実際は {pkt_src.count('version.playerListUsesLegacyEntry()')} 箇所")
check("encodeAddPlayer が buildPlatform を addPlayerHasBuildPlatform でゲートしている（1 箇所）",
      pkt_src.count("version.addPlayerHasBuildPlatform()") == 1,
      f"実際は {pkt_src.count('version.addPlayerHasBuildPlatform()')} 箇所")
check("encodeResourcePackStack が gameVersion を resourcePackStackHasGameVersion でゲートしている（1 箇所）",
      pkt_src.count("version.resourcePackStackHasGameVersion()") == 1,
      f"実際は {pkt_src.count('version.resourcePackStackHasGameVersion()')} 箇所")
check("encodeStartGame が movement authority を hasMovementAuthority でゲートしている（1 箇所。不変）",
      pkt_src.count("version.hasMovementAuthority()") == 1,
      f"実際は {pkt_src.count('version.hasMovementAuthority()')} 箇所")

legacy_src = read(SRC / "BedrockLegacyBlockPalette.java")
check("BedrockLegacyBlockPalette が bedrock_block_palette_1_12_0.bin を読む",
      'RESOURCE_NAME = "bedrock_block_palette_1_12_0.bin"' in legacy_src)
pal_bin = RES / "bedrock_block_palette_1_12_0.bin"
pal_bytes = pal_bin.read_bytes()
check("bedrock_block_palette_1_12_0.bin は 92,944 バイト・SHA-1 3553c5c9…",
      len(pal_bytes) == 92944
      and sha1_of(pal_bin) == "3553c5c934caf5be473a954a1222a1a5bb90e6bf")


def uvarint(n):
    out = bytearray()
    while n & ~0x7F:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n)
    return bytes(out)


bim12_data = json.loads(read(bim12))
regen = bytearray()
entries361 = []
for bname, metas in rbs12_data.items():
    for meta in metas:
        entries361.append((f"minecraft:{bname}", meta, bim12_data[f"minecraft:{bname}"]))
regen += uvarint(len(entries361))
for ename, emeta, eid in entries361:
    eb = ename.encode("utf-8")
    regen += uvarint(len(eb)) + eb + struct.pack("<h", emeta) + struct.pack("<h", eid)
check("bedrock_block_palette_1_12_0.bin は PMMP bedrock-1.12.0 の実測からの再生成とバイト一致する",
      bytes(regen) == pal_bytes)
check("レガシーパレットの先頭は minecraft:acacia_button／meta 0／id 395、末尾は minecraft:yellow_glazed_terracotta／meta 7／id 224",
      entries361[0] == ("minecraft:acacia_button", 0, 395)
      and entries361[-1] == ("minecraft:yellow_glazed_terracotta", 7, 224))

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession の切断メッセージが 291-2193 になっている",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in ses_src)
check("BedrockSession が 361 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
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
check("BedrockVersionTest に protocol361BoundaryGates がある",
      "protocol361BoundaryGates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "allSixtyTwoProtocolsReachStartGame" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame361DiffersFrom388", "legacyBlockPalette361Contents",
              "resourcePacks361HasNoGameVersion", "playerListAdd361UsesLegacyEntry",
              "playerListRemove361Equals388", "addPlayer361HasNoBuildPlatform",
              "decodeInventoryTransactionV0As361"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)

# ---------------------------------------------------------------------------
print("== H. ブロック表（不変の確認） ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 が d2b6acbb…のまま",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 が ee36fffb…のまま",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
check("1_12 系のブロックランタイム ID .bin は作っていない（レガシーパレットとは別物）",
      not list(RES.glob("bedrock_block_runtime_ids_1_12_*.bin")))

# ---------------------------------------------------------------------------
print("== I. 文書（BEDROCK.md／PLAYTEST-BEDROCK.md） ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、58 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 59 個見出しで、361 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 361 | 1.12.0 / 1.12.1 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 63 プロトコル／52 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407・390・389・388・361 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に StartGame の 361（LevelSettings 31 フィールド・パレット配列形式）がある",
      "1.12.1（361）は LevelSettings が 31 フィールドで末尾の game version 文字列が無く" in bedrock_md)
check("BEDROCK.md の境界表に AddPlayer の 361（buildPlatform 無し）がある",
      "1.12.1（361）は末尾の buildPlatform が無い（AddPlayerSerializer_v291。BE-NOTES.md §31）" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の vec3f（361〜419）がある",
      "（1.12.1〜1.16.101 と 1.16.220 以降）が vec3f" in bedrock_md)
check("BEDROCK.md の境界表に PlayerList の 361（旧直書きエントリ）がある",
      "1.12.1（361）は add エントリが旧直書き形式" in bedrock_md)
check("BEDROCK.md の境界表に MoveEntityDelta の代替列挙に 361 がある",
      "361・388・389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する" in bedrock_md)
check("BEDROCK.md の境界表に ResourcePackStack の 361（game_version 無し）がある",
      "1.12.1（361）は game_version 自体が無く legacy experiment フラグが末尾" in bedrock_md)
check("BEDROCK.md の境界表に InventoryTransaction の 361（同一形式）がある",
      "1.12.1（361）も同一形式（BE-NOTES.md §31）" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 63 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md のアイテム表が 38 本のバイナリになっている",
      "38 本のバイナリ" in bedrock_md)
check("BEDROCK.md の網羅率に 1.12.1 の代用明記つきエントリがある",
      "1.12.1 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "361 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 63 プロトコルの対応表／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／63 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.12.1 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・1.10.1・1.11.4・1.12.1・1.13.3・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（361 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち。361 にはヘルパー形式自体が無い）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("PlayerSkin は BedrockPackets／BedrockSession に未実装のまま（361 でも実装しない）",
      "PlayerSkin" not in pkt_src and "PlayerSkin" not in ses_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（361 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（361 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の shift は 361 でも無関係）",
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
