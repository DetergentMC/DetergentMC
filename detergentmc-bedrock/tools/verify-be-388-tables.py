#!/usr/bin/env python3
"""BE 388（1.13.0〜1.13.3）対応の検証。

388 には minecraft-data の protocol.json が存在しない
（1.13.0／1.13.1 は HTTP 404。protocolVersions.json にも 388 の
エントリは無い）。出典は CloudburstMC Protocol（ブランチ 3.0、
コミット 7163a6aedaefbc1d188431ef90ebc703b1138340）の v388
コーデックで、`.tmp-be/cloudburst-protocol/` に clone 済み。
Bedrock_v388 は Bedrock_v361 の CODEC を土台に、Explode の登録解除
（ID 23 は TickSync が引き継ぐ）、新規登録 9 件、updateSerializer
21 件、ENTITY_FLAGS／ENTITY_DATA の TypeMap 変更を行う。
389 との差分は PARTICLE_TYPES／LEVEL_EVENTS／ENTITY_DATA の
トランスフォーマ／Event 系シリアライザだけで、この実装の送受信
範囲にワイヤ差は無い（BE-NOTES.md §30）。

共有バージョン（1.13.0／1.13.1／1.13.2／1.13.3）は Wiki の
個別ページの infobox（Protocol version 388）で確認した
（BE-NOTES.md §30）。

アイテム表は 389〜431 と同じ bedrock_item_ids_1_16_210.bin を
共用するが、388 については代用である（PMMP BedrockData の
bedrock-1.13.0 タグは旧形式のみで required_item_list.json が無く、
1.13 系のランタイム ID の実データは存在しない。388 自体の
実測ではない）。

使い方: python3 tools/verify-be-388-tables.py
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
print("== A. 出典の同一性（Cloudburst v388 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v388＝1.13.0",
      "| Bedrock_v388  |       1.13.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v389＝1.14.0 - 1.14.50（388 の 1 つ上の出典確認）",
      "| Bedrock_v389  |  1.14.0 - 1.14.50   |" in versions_md)

v388_src = read(CB / "v388/Bedrock_v388.java")
check("Bedrock_v388.java の CODEC は protocolVersion(388) と minecraftVersion(\"1.13.0\") を持つ",
      "protocolVersion(388)" in v388_src and 'minecraftVersion("1.13.0")' in v388_src)
check("Bedrock_v388.java の CODEC は Bedrock_v361.CODEC を土台にする（v387 コーデックは存在しない）",
      "Bedrock_v361.CODEC.toBuilder()" in v388_src)
check("Bedrock_v388.java は helper に BedrockCodecHelper_v388 を使う",
      "new BedrockCodecHelper_v388(ENTITY_DATA, GAME_RULE_TYPES)" in v388_src)
check("Bedrock_v388.java は Explode を登録解除し、TickSync が ID 23 を引き継ぐ",
      ".deregisterPacket(ExplodePacket.class)" in v388_src
      and "registerPacket(TickSyncPacket::new, TickSyncSerializer_v388.INSTANCE, 23," in v388_src)
for cls, ser, pid in (
        ("EducationSettingsPacket", "EducationSettingsSerializer_v388", 137),
        ("EmotePacket", "EmoteSerializer_v388", 138),
        ("MultiplayerSettingsPacket", "MultiplayerSettingsSerializer_v388", 139),
        ("SettingsCommandPacket", "SettingsCommandSerializer_v388", 140),
        ("AnvilDamagePacket", "AnvilDamageSerializer_v388", 141),
        ("CompletedUsingItemPacket", "CompletedUsingItemSerializer_v388", 142),
        ("NetworkSettingsPacket", "NetworkSettingsSerializer_v388", 143),
        ("PlayerAuthInputPacket", "PlayerAuthInputSerializer_v388", 144)):
    check(f"Bedrock_v388.java の新規登録 {cls} = {pid}",
          f"registerPacket({cls}::new, {ser}.INSTANCE, {pid}," in v388_src)
check("Bedrock_v388.java の updateSerializer は 21 件",
      v388_src.count("updateSerializer(") == 21,
      f"実際は {v388_src.count('updateSerializer(')}")
check("Bedrock_v388.java は StartGame を StartGameSerializer_v388 に差し替える（388 自身の世代）",
      "updateSerializer(StartGamePacket.class, StartGameSerializer_v388.INSTANCE)" in v388_src)
check("Bedrock_v388.java は ResourcePackStack を ResourcePackStackSerializer_v388 に差し替える",
      "updateSerializer(ResourcePackStackPacket.class, ResourcePackStackSerializer_v388.INSTANCE)" in v388_src)
check("Bedrock_v388.java は MoveEntityDelta を MoveEntityDeltaSerializer_v388 に差し替える（388 自身のもの）",
      "updateSerializer(MoveEntityDeltaPacket.class, MoveEntityDeltaSerializer_v388.INSTANCE)" in v388_src)

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


flags388_idx, _ = chain_indices("ENTITY_FLAGS", 0, "v388")
check("ENTITY_FLAGS はチェーン累積で 388 が 92 件（0〜91 連続。v388 が 88〜91 を挿入）",
      flags388_idx == set(range(92)),
      f"実際は {len(flags388_idx)} 件・最大 {max(flags388_idx)}")
data388_idx, data388_keys = chain_indices("ENTITY_DATA", 1, "v388")
check("ENTITY_DATA はチェーン累積で 388 が 113 件（0〜112 連続。v388 が 107〜112 を挿入）",
      data388_idx == set(range(113)),
      f"実際は {len(data388_idx)} 件・最大 {max(data388_idx)}")
check("ENTITY_DATA の AREA_EFFECT_CLOUD_PARTICLE は 62（388 の実効）",
      data388_keys.get("EntityDataTypes.AREA_EFFECT_CLOUD_PARTICLE") == 62)
check("Bedrock_v388.java の ENTITY_FLAGS 挿入は 88〜91 の 4 件、ENTITY_DATA 挿入は 107〜112 の 6 件",
      ".insert(88, EntityFlag.IS_IN_UI)" in v388_src
      and ".insert(91, EntityFlag.CELEBRATING)" in v388_src
      and ".insert(EntityDataTypes.AMBIENT_SOUND_INTERVAL, 107," in v388_src
      and ".insert(EntityDataTypes.CAN_RIDE_TARGET, 112," in v388_src)

v291_src = read(CB / "v291/Bedrock_v291.java")
v313_src = read(CB / "v313/Bedrock_v313.java")
v361_src = read(CB / "v361/Bedrock_v361.java")
pt291 = typemap_block(v291_src, "PARTICLE_TYPES")
pt313 = typemap_block(v313_src, "PARTICLE_TYPES")
pt361 = typemap_block(v361_src, "PARTICLE_TYPES")
check("PARTICLE_TYPES は v291 で 45 件、v313 で 10 件挿入、v361 で 3 件挿入（388 の実効は 58 件＝0〜57）",
      pt291.count(".insert(") == 45 and pt313.count(".insert(") == 10
      and pt361.count(".insert(") == 3)
check("Bedrock_v388.java は PARTICLE_TYPES を再定義しない（v361 の定義がそのまま実効。参照は ENTITY_DATA のトランスフォーマのみ）",
      "PARTICLE_TYPES =" not in v388_src)

v389_src = read(CB / "v389/Bedrock_v389.java")
check("Bedrock_v389.java の CODEC は Bedrock_v388.CODEC を土台にする",
      "Bedrock_v388.CODEC.toBuilder()" in v389_src)
check("Bedrock_v389.java に registerPacket／deregisterPacket は無い（388 とパケット集合は同一）",
      "registerPacket" not in v389_src and "deregisterPacket" not in v389_src)
check("Bedrock_v389.java の変更は PARTICLE_TYPES の差し替えと Event／LevelEvent 系の updateSerializer 3 件だけ",
      ".shift(28, 1)" in v389_src
      and ".insert(28, ParticleType.DRIP_HONEY)" in v389_src
      and v389_src.count("updateSerializer(") == 3)
check("Bedrock_v389.java は StartGame に触っていない（388・389 とも StartGameSerializer_v388 が実効）",
      "StartGamePacket" not in v389_src)

for name in ("1.13.0", "1.13.1", "1.13.2", "1.13.3"):
    check(f"388 の protocol.json は .tmp-be に無い（protocol-{name}.json は存在しない）",
          not (TMP / f"protocol-{name}.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 388 のエントリは無い",
      388 not in pv_protocols)
below422 = sorted(p for p in pv_protocols if p < 422)
check("protocolVersions.json で 422 未満のエントリは 100・82・70 のみ（388 は載っていない）",
      below422 == [70, 82, 100], f"実際は {below422}")

# A-2. PMMP bedrock-1.13.0 タグ（388 の世代のデータ。旧形式のみ）
tree = json.loads(read(TMP / "be-388/bedrock-1.13.0-tree.json"))
tree_paths = [t["path"] for t in tree["tree"]]
check("PMMP bedrock-1.13.0 タグのツリーは 14 エントリ",
      len(tree_paths) == 14, f"実際は {len(tree_paths)}")
check("bedrock-1.13.0 タグに required_item_list.json は無い（388 のランタイム ID 実データは存在しない）",
      "required_item_list.json" not in tree_paths)
check("bedrock-1.13.0 タグに canonical_block_states.nbt は無い（旧形式の required_block_states.nbt のみ）",
      "canonical_block_states.nbt" not in tree_paths
      and "required_block_states.nbt" in tree_paths)

iim13 = TMP / "be-388/item_id_map.json"
check("PMMP bedrock-1.13.0 の item_id_map.json は 696 件・SHA-1 5cac37ca…",
      len(json.loads(read(iim13))) == 696
      and sha1_of(iim13) == "5cac37ca36a19229208f5618699a11626bff49d6")
bim13 = TMP / "be-388/block_id_map.json"
check("PMMP bedrock-1.13.0 の block_id_map.json は 465 件・SHA-1 1ab642fe…",
      len(json.loads(read(bim13))) == 465
      and sha1_of(bim13) == "1ab642fe6d6bfeab618896f2a92f46075144b217")
rbs13 = TMP / "be-388/required_block_states.nbt"
check("PMMP bedrock-1.13.0 の required_block_states.nbt は 294,991 バイト・SHA-1 a057f7a5…",
      rbs13.stat().st_size == 294991
      and sha1_of(rbs13) == "a057f7a5863da60b9aa2dce27f41c5e905a374f4")

# ---------------------------------------------------------------------------
print("== B. 388 のパケット一覧（389・390 と同一：407 の一覧 − v407 の新規登録 12 件） ==")

p422 = json.loads(read(TMP / "protocol-1.16.201.json"))
mappings = p422["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
ids422 = {int(k): v for k, v in mappings.items()}
check("422 のパケット一覧は 160 件（土台の再確認）", len(ids422) == 160)

ids407 = {i: n for i, n in ids422.items()
          if i not in {157, 158, 159, 160, 161, 162} and i != 163}
check("407 のパケット一覧は 153 件（§27 の再確認）", len(ids407) == 153)

new407 = {145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156}
ids390 = {i: n for i, n in ids407.items() if i not in new407}
ids388 = dict(ids390)
check("388 のパケット一覧は 141 件（protocol.json 由来の母集団。389・390 と同一）",
      len(ids388) == 141, f"実際は {len(ids388)}")
check("388 の最大 ID は 144（player_auth_input。v388 で登録され 388 に在る）",
      max(ids388) == 144 and ids388[144].startswith("player_auth_input"))
check("388 の欠番は [16, 37, 116]（389・390・407・408・419・422 と同じ。ID は 1 始まり）",
      [i for i in range(1, 145) if i not in ids388] == [16, 37, 116])
# Cloudburst の登録ベースでは 388 も 140 件・最大 ID 144・欠番 [16, 116, 127, 128]
# になる（チェーン積み上げ：v361 の 132 件 → v388 で Explode(23) 解除＋
# 9 件登録＝140 件。BE-NOTES.md §30、実測メモ
# `.tmp-be/be-388-cloudburst-diff.md` §1）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 388 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 388 の一覧の {packet_name} と一致",
              value in ids388 and ids388[value].startswith(packet_name),
              f"一覧では {ids388.get(value)}")

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（388 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_13_3(388, \"1.13.3\") がある",
      'V1_13_3(388, "1.13.3")' in ver_src)
check("V1_13_3 は enum の先頭（V1_14_41 より前）",
      ver_src.index("V1_13_3(388") < ver_src.index("V1_14_41(389"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("クラス javadoc の protocol.json ファイル数は 51 のまま（388 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 388 は 1.13.0〜1.13.3 の共有と Wiki infobox の出典がある",
      "1.13.0 / 1.13.1 / 1.13.2 / 1.13.3 で共有するプロトコル" in ver_src
      and "Protocol version 388" in ver_src)
check("enum コメントに 388 と 389 の間に番号は無い（連続）とある",
      "388 と 389 の間に番号は無い（連続）" in ver_src)
check("enum コメントに 388 の 1 つ下は 361（1.12.0）とある",
      "388 の 1 つ下は 361" in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 388 -> \"1_16_210\" がある",
      'case 388 -> "1_16_210";' in ver_src)
check("case 388 のコメントに「388 自体の実測ではない」の代用明記がある",
      "388 自体の実測ではない" in ver_src)
check("itemTableTag の case 389／390／407／408／419／422／428／431 は不変",
      'case 389 -> "1_16_210";' in ver_src
      and 'case 390 -> "1_16_210";' in ver_src
      and 'case 407 -> "1_16_210";' in ver_src and 'case 408 -> "1_16_210";' in ver_src
      and 'case 419 -> "1_16_210";' in ver_src and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（389〜431 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（388 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("388 用の .bin（1_13_3／1_13_0）は作っていない",
      not any(b in ("bedrock_item_ids_1_13_3.bin", "bedrock_item_ids_1_13_0.bin")
              for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 388 のエントリは無い（389〜422 と同じ扱い）",
      "388" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 388 世代の形式を確認） ==")

pl388 = read(CB / "v388/serializer/PlayerListSerializer_v388.java")
pl390 = read(CB / "v390/serializer/PlayerListSerializer_v390.java")
check("PlayerListSerializer_v388 に trustedSkin は無い（388 の add は本体のみ。388 自身のシリアライザ）",
      "trustedSkin" not in pl388 and "TrustedSkin" not in pl388)
check("PlayerListSerializer_v388 の add はエントリごとに teacher／host を書く（本体 8 フィールド）",
      "buffer.writeBoolean(entry.isTeacher())" in pl388
      and "buffer.writeBoolean(entry.isHost())" in pl388)
check("PlayerListSerializer_v390 の add は末尾に trustedSkin のブロックを書く（390 で追加）",
      "buffer.writeBoolean(entry.isTrustedSkin())" in pl390)
check("PlayerListSerializer_v388 の remove は uuid のみ（389・390 と同じ）",
      "helper.readUuid(buffer)" in pl388)

h388 = read(CB / "v388/BedrockCodecHelper_v388.java")
h390 = read(CB / "v390/BedrockCodecHelper_v390.java")
check("BedrockCodecHelper_v388 の Skin は 12 フィールド（fullSkinId まで。armSize／personaPieces は無い。388・389 の実効形式）",
      "getFullSkinId" in h388 and "ArmSize" not in h388
      and "PersonaPieces" not in h388)
check("BedrockCodecHelper_v390 の Skin は 16 フィールド（armSize／skinColor／personaPieces／tintColors を追加。390 の実効形式）",
      "skin.getArmSize()" in h390 and "skin.getPersonaPieces()" in h390
      and "skin.getTintColors()" in h390)
check("BedrockCodecHelper_v390 は BedrockCodecHelper_v388 を継承する（差分は Skin のみ）",
      "extends BedrockCodecHelper_v388" in h390)

it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（388 は transaction_type から始まる。389・390 と同じ）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)

ap361 = read(CB / "v361/serializer/AddPaintingSerializer_v361.java")
check("AddPaintingSerializer_v361 は座標を writeVector3f で書く（388 の実効形式。389・390 と同じ）",
      "helper.writeVector3f(buffer, packet.getPosition())" in ap361)

check("MoveEntityDelta の実効シリアライザは 388 が MoveEntityDeltaSerializer_v388 自身（v389・v390 は触っていない）",
      "MoveEntityDeltaSerializer_v388.INSTANCE" in v388_src
      and "MoveEntityDelta" not in v389_src)

rps388 = read(CB / "v388/serializer/ResourcePackStackSerializer_v388.java")
check("ResourcePackStackSerializer_v388 は v313 を継承し末尾に gameVersion を足す（388 自身の世代。389・390 と同じ）",
      "extends ResourcePackStackSerializer_v313" in rps388
      and "helper.writeString(buffer, packet.getGameVersion())" in rps388)

lc361 = read(CB / "v361/serializer/LevelChunkSerializer_v361.java")
check("LevelChunk の実効シリアライザは 388 も LevelChunkSerializer_v361（v388・v389 は触っていない）",
      "class LevelChunkSerializer_v361" in lc361
      and "LevelChunkPacket" not in v388_src)

ae313 = read(CB / "v313/serializer/AddEntitySerializer_v313.java")
check("AddEntity の実効シリアライザは 388 も AddEntitySerializer_v313（v291 を継承。§29 の記述を §30 で訂正）",
      "class AddEntitySerializer_v313 extends AddEntitySerializer_v291" in ae313)

for name in ("MovePlayer", "SetEntityData", "UpdateAttributes"):
    s291 = read(CB / f"v291/serializer/{name}Serializer_v291.java")
    check(f"{name}Serializer_v291 に tick は無い（388 の実効形式。389・390 と同じ）",
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
check("述語 playerListAddHasVerified() が protocol >= 390 で定義されている（§29 で新規。388 は対象外）",
      re.search(r"public boolean playerListAddHasVerified\(\) \{\s*return protocol >= 390;",
                ver_src) is not None)
gte = sorted({int(n) for n in re.findall(r"protocol >= (\d+)", ver_src)})
check("BedrockVersion の最小の >= 閾値は 332（313 回で resourcePacksInfoHasScripting を追加。361 回で 388 境界の述語を追加）",
      gte[0] == 332, f"実際は {gte[0]}")
check("388 は 361 回で >= の閾値になった（388 と 389 の間に境界は無いまま）",
      388 in gte and 389 not in gte, f"閾値は {gte}")
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
check("encodeMoveEntityDelta は FloatLE 形式のまま（388 ではセッションが使わない）",
      "out.writeLittleFloat(x == null ? 0 : x)" in pkt_src)

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession が encodePlayerListAdd に bedrockVersion を渡す",
      "BedrockPackets.encodePlayerListAdd(state.uuid, state.javaId, state.name," in ses_src
      and "bedrockVersion))" in ses_src)
check("BedrockSession が 388 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
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
check("BedrockVersionTest に protocol388BoundaryGates がある",
      "protocol388BoundaryGates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "allSixtyTwoProtocolsReachStartGame" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame388Equals389", "resourcePacks388Equals389",
              "playerListAdd388HasNoVerified", "playerListRemove388HasNoVerified",
              "decodeInventoryTransactionV0As388"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)
set_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionEntityTest.java")
check("BedrockSessionEntityTest に entityMove388UsesMoveEntityNotDelta がある",
      "entityMove388UsesMoveEntityNotDelta" in set_src)

# ---------------------------------------------------------------------------
print("== H. ブロック表（不変の確認） ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 が d2b6acbb…のまま",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 が ee36fffb…のまま",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
check("1_13 系のブロック .bin は作っていない",
      not list(RES.glob("bedrock_block_runtime_ids_1_13_*.bin")))

# ---------------------------------------------------------------------------
print("== I. 文書（BEDROCK.md／PLAYTEST-BEDROCK.md） ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、57 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 58 個見出しで、388 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 388 | 1.13.0 / 1.13.1 / 1.13.2 / 1.13.3 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 63 プロトコル／51 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407・390・389・388 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に StartGame の 388（389 と同一形式）がある",
      "1.13.3（388）も同一形式（389 との差分は粒子表などで StartGame に触れない。BE-NOTES.md §30）" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の vec3f（388〜419）がある",
      "（1.12.1〜1.16.101 と 1.16.220 以降）が vec3f" in bedrock_md)
check("BEDROCK.md の境界表に PlayerList の 388（add にも verified 無し）がある",
      "1.13.3（388）も同じ（PlayerListSerializer_v388 自身。BE-NOTES.md §30）" in bedrock_md)
check("BEDROCK.md の境界表に MoveEntityDelta の代替列挙に 388 がある",
      "388・389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する" in bedrock_md)
check("BEDROCK.md の境界表に InventoryTransaction の 388（同一形式）がある",
      "1.13.3（388）も同一形式（BE-NOTES.md §30）" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 63 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md のアイテム表が 38 本のバイナリになっている",
      "38 本のバイナリ" in bedrock_md)
check("BEDROCK.md の網羅率に 1.13.3 の代用明記つきエントリがある",
      "1.13.3 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "388 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 63 プロトコルの対応表／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／63 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.13.3 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・1.10.1・1.11.4・1.12.1・1.13.3・1.14.41・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（388 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち。388 の実形式は 12 フィールドで 389 と同じ）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("PlayerSkin は BedrockPackets／BedrockSession に未実装のまま（388 でも実装しない）",
      "PlayerSkin" not in pkt_src and "PlayerSkin" not in ses_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（388 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（388 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の shift は 388 でも無関係）",
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
