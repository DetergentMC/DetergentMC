#!/usr/bin/env python3
"""BE 313（1.8.1）対応の検証。

313 には minecraft-data の protocol.json が存在しない
（1.8.0／1.8.1 は HTTP 404。protocolVersions.json にも 313 の
エントリは無い）。出典は CloudburstMC Protocol（ブランチ 3.0、
コミット 7163a6aedaefbc1d188431ef90ebc703b1138340）の
v313 コーデックで、`.tmp-be/cloudburst-protocol/` に clone 済み。
Bedrock_v313 は Bedrock_v291 の CODEC を土台に updateSerializer
8 件と新規登録 5 件を行う。332（v313 を土台にする）との差分
（この実装の送受信範囲）はワイヤ形式の差が 2 点だけ：
StartGame の LevelSettings が _v291 レイアウト＋末尾 2 bool の
StartGameSerializer_v313（33 フィールド）であることと、
ResourcePacksInfo が _v291 で scriptingEnabled が無いこと
（BE-NOTES.md §35、実測メモ `.tmp-be/be-313-cloudburst-diff.md`）。

共有は 1.8.0／1.8.1（Wiki の両個別ページの infobox
「Protocol version 313」で確認。displayName は最新の 1.8.1。
BE-NOTES.md §35、実測メモ `.tmp-be/be-313-external.md`）。
313 の 1 つ下の 312 はベータ 1.8.0.14 の番号で、1.8 世代の
リリースプロトコルは 313 が最後（ループの終点）。

アイテム表は 332〜431 と同じ bedrock_item_ids_1_16_210.bin を
共用するが、313 については代用である（PMMP BedrockData の
bedrock-1.8.0 タグはツリー 4 ファイルのみで item_id_map.json
すら無く、1.8 系のランタイム ID の実データは存在しない。
313 自体の実測ではない）。StartGame のブロックパレットは
313 も配列形式（name／meta のみ）だが、1.8 系のパレット実データが
存在しないため、中身は 1.11.0 世代の実測レガシーパレット
（bedrock_block_palette_1_11_0.bin、3,183 件）を代用する
（313 自体の実測ではない）。

使い方: python3 tools/verify-be-313-tables.py
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


def chain_upto(name, upto):
    """TypeMap の index 集合とキー対応をチェーン（v291→…→upto）で
    累積する。insert／put／replace／shift を解釈する
    （`.tmp-be/analyze-313.py` と同じ方式）。"""
    idx = set()
    keys = {}
    for v in ("v291", "v313"):
        src = read(CB / f"{v}/Bedrock_{v}.java")
        if name + " =" in src:
            blk = typemap_block(src, name)
            if "builder()" in blk.split("\n")[0] and "toBuilder()" not in blk.split("\n")[0]:
                idx = set()
                keys = {}
            for m in re.finditer(r"\.shift\((\d+),\s*(-?\d+)\)", blk):
                a, n = int(m.group(1)), int(m.group(2))
                idx = {i + n if i >= a else i for i in idx}
                keys = {k: (i + n if i >= a else i) for k, i in keys.items()}
            for m in re.finditer(r"\.(?:insert|put)\(([^)]*)\)", blk):
                args = [a.strip() for a in m.group(1).split(",")]
                # ENTITY_DATA は insert(TYPE, index)、他は insert(index, ...)
                if name == "ENTITY_DATA":
                    keys[args[0]] = int(args[1])
                    idx.add(int(args[1]))
                else:
                    idx.add(int(args[0]))
                    if len(args) > 1:
                        keys[args[1]] = int(args[0])
            for m in re.finditer(r"\.replace\(([^)]*)\)", blk):
                args = [a.strip() for a in m.group(1).split(",")]
                if name == "ENTITY_DATA":
                    keys[args[0]] = int(args[1])
                    idx.add(int(args[1]))
        if v == upto:
            break
    return idx, keys


# ---------------------------------------------------------------------------
print("== A. 出典の同一性（Cloudburst v313 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v313＝1.8.0",
      "| Bedrock_v313  |        1.8.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v332＝1.9.0（313 の 1 つ上の出典確認）",
      "| Bedrock_v332  |        1.9.0        |" in versions_md)

v313_src = read(CB / "v313/Bedrock_v313.java")
check("Bedrock_v313.java の CODEC は protocolVersion(313) と minecraftVersion(\"1.8.0\") を持つ",
      "protocolVersion(313)" in v313_src and 'minecraftVersion("1.8.0")' in v313_src)
check("Bedrock_v313.java の CODEC は Bedrock_v291.CODEC を土台にする",
      "Bedrock_v291.CODEC.toBuilder()" in v313_src)
check("Bedrock_v313.java は helper に新規 BedrockCodecHelper_v313 を使う",
      "new BedrockCodecHelper_v313(ENTITY_DATA, GAME_RULE_TYPES)" in v313_src
      and (CB / "v313/BedrockCodecHelper_v313.java").exists())
check("Bedrock_v313.java の updateSerializer は 8 件",
      v313_src.count("updateSerializer(") == 8,
      f"実際は {v313_src.count('updateSerializer(')}")
check("Bedrock_v313.java の新規登録は 5 件（118／119／120／121／122）",
      v313_src.count(".registerPacket(") == 5
      and "SpawnParticleEffectPacket::new" in v313_src and ", 118," in v313_src
      and "AvailableEntityIdentifiersPacket::new" in v313_src and ", 119," in v313_src
      and "LevelSoundEvent2Packet::new" in v313_src and ", 120," in v313_src
      and "NetworkChunkPublisherUpdatePacket::new" in v313_src and ", 121," in v313_src
      and "BiomeDefinitionListPacket::new" in v313_src and ", 122," in v313_src)
check("Bedrock_v313.java に登録解除は無い（deregisterPacket 0 件）",
      "deregisterPacket(" not in v313_src)
check("Bedrock_v313.java に PlayerAuthInput は無い（388 で新規登録）",
      "PlayerAuthInput" not in v313_src)
check("Bedrock_v313.java に LevelSoundEvent（単体）は無い（332 で新規登録）",
      "LevelSoundEventPacket::new" not in v313_src)
check("v313 に StartGameSerializer_v313 がある（StartGame の LevelSettings は 313 自身の世代）",
      (CB / "v313/serializer/StartGameSerializer_v313.java").exists())

v332_src = read(CB / "v332/Bedrock_v332.java")
check("Bedrock_v332.java の CODEC は Bedrock_v313.CODEC を土台にする（313 の 1 つ上）",
      "Bedrock_v313.CODEC.toBuilder()" in v332_src)

flags313_idx, _ = chain_upto("ENTITY_FLAGS", "v313")
check("ENTITY_FLAGS はチェーン累積で 313 が 71 件（0〜70 連続。332 と同じ）",
      flags313_idx == set(range(71)),
      f"実際は {len(flags313_idx)} 件・最大 {max(flags313_idx) if flags313_idx else -1}")
data313_idx, data313_keys = chain_upto("ENTITY_DATA", "v313")
check("ENTITY_DATA の index はチェーン累積で 313 が 94 件（0〜93 連続。94〜98 は 332 で追加）",
      data313_idx == set(range(94)),
      f"実際は {len(data313_idx)} 件・最大 {max(data313_idx) if data313_idx else -1}")
check("ENTITY_DATA の index 39 は 313 が INTERACT_TEXT（332 と同じ。この実装は 39 を使わない）",
      data313_keys.get("EntityDataTypes.INTERACT_TEXT") == 39)
pt313_idx, _ = chain_upto("PARTICLE_TYPES", "v313")
check("PARTICLE_TYPES はチェーン累積で 313 が 55 件（0〜54 連続。332 と同じ）",
      pt313_idx == set(range(55)),
      f"実際は {len(pt313_idx)} 件・最大 {max(pt313_idx) if pt313_idx else -1}")
cp313_idx, _ = chain_upto("COMMAND_PARAMS", "v313")
check("COMMAND_PARAMS はチェーン累積で 313 が 13 件・最大 43（FILE_PATH は 332 で新規。AvailableCommands は未送信で無関係）",
      cp313_idx == {1, 2, 3, 4, 5, 6, 7, 26, 28, 31, 33, 36, 43},
      f"実際は {sorted(cp313_idx)}")
se313_idx, _ = chain_upto("SOUND_EVENTS", "v313")
check("SOUND_EVENTS はチェーン累積で 313 が 251 件・最大 255（332 と同じ。LevelSoundEvent は未送信で無関係）",
      len(se313_idx) == 251 and max(se313_idx) == 255,
      f"実際は {len(se313_idx)} 件・最大 {max(se313_idx) if se313_idx else -1}")

check("313 の protocol.json は .tmp-be に無い（protocol-1.8.0.json／protocol-1.8.1.json は存在しない）",
      not (TMP / "protocol-1.8.0.json").exists()
      and not (TMP / "protocol-1.8.1.json").exists())

pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 313 のエントリは無い",
      313 not in pv_protocols)
check("protocolVersions.json に 1.8 系のエントリは無い",
      not any(e["minecraftVersion"].startswith("1.8") for e in pv))

# A-2. PMMP bedrock-1.8.0 タグ（313 の世代のデータ。パレット・ID 表の材料自体が無い）
tree_lines = [ln for ln in read(TMP / "be-313/tree.txt").splitlines() if ln]
check("PMMP bedrock-1.8.0 タグのツリーは 4 エントリ（LICENSE／README.md／creativeitems.json／recipes.json）",
      tree_lines == ["LICENSE", "README.md", "creativeitems.json", "recipes.json"],
      f"実際は {tree_lines}")
ci18 = TMP / "be-313/creativeitems.json"
ci18_data = json.loads(read(ci18))
check("PMMP bedrock-1.8.0 の creativeitems.json は 1,169 件・SHA-1 1405c0a8…（旧来の数値 ID でランタイム ID ではない）",
      len(ci18_data) == 1169
      and sha1_of(ci18) == "1405c0a8c448ad2c6ba078bca5c3cd8d257632a7"
      and isinstance(ci18_data[0].get("id"), int))
check("PMMP bedrock-1.8.0 の recipes.json の SHA-1 は 53bc9ffd…",
      sha1_of(TMP / "be-313/recipes.json")
      == "53bc9ffd9f73736b71d0b0a653260a3b133c5a23")

# ---------------------------------------------------------------------------
print("== B. 313 のパケット一覧（332 の一覧からの差分） ==")

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
ids332 = {i: n for i, n in ids340.items() if i not in {124, 125}}
check("332 のパケット一覧は 123 件（§34 の再確認）", len(ids332) == 123)

# 313 は 332 から v332 の新規登録 {123 level_sound_event} を除いた
# もの（332 の集合からの逆算で、1.8.x 自体の protocol.json を
# 実測したものではない。実測メモ `.tmp-be/be-313-cloudburst-diff.md`
# 参照）
ids313 = {i: n for i, n in ids332.items() if i not in {123}}
check("313 のパケット一覧は 122 件（protocol.json 由来の母集団）",
      len(ids313) == 122, f"実際は {len(ids313)}")
check("313 の最大 ID は 128（123 は v332 の新規登録）",
      max(ids313) == 128)
check("313 の欠番は [37, 116, 123, 124, 125, 126]（ID は 1 始まり）",
      [i for i in range(1, 129) if i not in ids313] == [37, 116, 123, 124, 125, 126])
check("313 の一覧に player_auth_input（144）は無い（388 で新規登録）",
      144 not in ids313)
check("313 の一覧に level_sound_event（123）は無い（332 で新規登録）",
      123 not in ids313)
check("313 の一覧に network_chunk_publisher_update（121）がある（313 で新規登録）",
      ids313.get(121, "").startswith("network_chunk_publisher_update"))
check("313 の一覧に add_hanging_entity（16）がある（361 で登録解除）",
      16 in ids313)
# Cloudburst の登録ベースでは 313 は 121 件・最大 ID 122・欠番
# [116] になる（チェーン積み上げ：v291 の 116 件 → v313 で +5＝
# 121 件。BE-NOTES.md §35、実測メモ
# `.tmp-be/be-313-cloudburst-diff.md`）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 313 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 313 の一覧の {packet_name} と一致",
              value in ids313 and ids313[value].startswith(packet_name),
              f"一覧では {ids313.get(value)}")
check("PACKET_PLAYER_AUTH_INPUT（144）は 313 の一覧に無い（パケット自体が存在しない）",
      const_value("PACKET_PLAYER_AUTH_INPUT") == 144
      and const_value("PACKET_PLAYER_AUTH_INPUT") not in ids313)

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（313 の追加）と終点 ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_8_1(313, \"1.8.1\") がある",
      'V1_8_1(313, "1.8.1")' in ver_src)
check("V1_8_1 は enum の先頭（V1_9_0 より前）",
      ver_src.index("V1_8_1(313") < ver_src.index("V1_9_0(332"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("対応プロトコルの最小は 291・最大は 2225（RakNet は 291〜2193）",
      min(int(p) for p in protocols) == 291
      and max(int(p) for p in protocols) == 2225)
check("クラス javadoc の protocol.json ファイル数は 51 のまま（313 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 313 は 1.8.0／1.8.1 の共有と Wiki infobox の出典がある",
      "313 は 1.8.0 / 1.8.1 で共有するプロトコル" in ver_src
      and "Protocol version 313" in ver_src)
check("enum コメントに終点の根拠（312 はベータ 1.8.0.14 の番号）がある",
      "312 はベータ 1.8.0.14 の番号" in ver_src)
check("enum に 312 のエントリは無い（ベータ番号は対象外）",
      "(312," not in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 313 -> \"1_16_210\" がある",
      'case 313 -> "1_16_210";' in ver_src)
check("case 313 のコメントに「313 自体の実測ではない」の代用明記がある",
      "313 自体の実測ではない" in ver_src)
check("itemTableTag の case 332／340／354／361／388／389／390／407／408／419／422／428／431 は不変",
      'case 332 -> "1_16_210";' in ver_src
      and 'case 340 -> "1_16_210";' in ver_src
      and 'case 354 -> "1_16_210";' in ver_src
      and 'case 361 -> "1_16_210";' in ver_src
      and 'case 388 -> "1_16_210";' in ver_src
      and 'case 389 -> "1_16_210";' in ver_src
      and 'case 390 -> "1_16_210";' in ver_src
      and 'case 407 -> "1_16_210";' in ver_src and 'case 408 -> "1_16_210";' in ver_src
      and 'case 419 -> "1_16_210";' in ver_src and 'case 422 -> "1_16_210";' in ver_src
      and 'case 428 -> "1_16_210";' in ver_src and 'case 431 -> "1_16_210";' in ver_src)
bin210 = RES / "bedrock_item_ids_1_16_210.bin"
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（332〜431 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（313 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("313 用の .bin（1_8_1）は作っていない",
      not any(b == "bedrock_item_ids_1_8_1.bin" for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 313 のエントリは無い（332〜422 と同じ扱い）",
      "313" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 313 世代の形式を確認） ==")

sg313 = read(CB / "v313/serializer/StartGameSerializer_v313.java")
check("StartGameSerializer_v313 は _v291 の LevelSettings に fromWorldTemplate／fromLockedWorldTemplate を追加する",
      "super.writeLevelSettings(buffer, helper, packet)" in sg313
      and "buffer.writeBoolean(packet.isFromWorldTemplate())" in sg313
      and "buffer.writeBoolean(packet.isFromLockedWorldTemplate())" in sg313)
sg291 = read(CB / "v291/serializer/StartGameSerializer_v291.java")
check("StartGameSerializer_v291 の writeLevelSettings に trustingPlayers と放送モードの bool がある（313 の実効レイアウト）",
      "packet.isTrustingPlayers()" in sg291
      and "packet.getXblBroadcastMode() != GamePublishSetting.NO_MULTI_PLAY" in sg291
      and "packet.getPlatformBroadcastMode() != GamePublishSetting.NO_MULTI_PLAY" in sg291)
check("StartGameSerializer_v291 はパレットを配列形式で書く（件数＋name／meta ShortLE のみ。id は無い。313 も同じ）",
      "VarInts.writeUnsignedInt(buffer, palette.size())" in sg291
      and 'helper.writeString(buffer, blockTag.getString("name"))' in sg291
      and 'buffer.writeShortLE(entry.getShort("meta"))' in sg291
      and 'entry.getShort("id")' not in sg291)
sg332 = read(CB / "v332/serializer/StartGameSerializer_v332.java")
check("StartGameSerializer_v332（332 の実効）には trustingPlayers は無い（313 との差）",
      "TrustingPlayers" not in sg332)

rpi291 = read(CB / "v291/serializer/ResourcePacksInfoSerializer_v291.java")
rpi332 = read(CB / "v332/serializer/ResourcePacksInfoSerializer_v332.java")
check("ResourcePacksInfoSerializer_v291（313 の実効）に scripting は無い",
      "scripting" not in rpi291.lower())
check("ResourcePacksInfoSerializer_v332（332 の実効）には scriptingEnabled がある（313 との差）",
      "scriptingEnabled" in rpi332 or "ScriptingEnabled" in rpi332)

ap291_src = read(CB / "v291/serializer/AddPaintingSerializer_v291.java")
ap332_src = read(CB / "v332/serializer/AddPaintingSerializer_v332.java")


def ap_norm(s, cls):
    lines = [ln for ln in s.splitlines() if not ln.startswith("package ")]
    return "\n".join(lines).replace(cls, "CLS")


check("AddPaintingSerializer_v291 と _v332 は実装が同一（差は package 名とクラス名のみ。どちらも BlockPosition）",
      ap_norm(ap291_src, "AddPaintingSerializer_v291")
      == ap_norm(ap332_src, "AddPaintingSerializer_v332")
      and "helper.writeBlockPosition(buffer, packet.getPosition().toInt())" in ap291_src)

fc291 = read(CB / "v291/serializer/FullChunkDataSerializer_v291.java")
check("FullChunkDataSerializer_v291（313 の LevelChunk 実効）は chunkX／chunkZ と payload のみ（332 と同じ）",
      "VarInts.writeInt(buffer, packet.getChunkX())" in fc291
      and "getSubChunksLength" not in fc291 and "CachingEnabled" not in fc291)

ap291 = read(CB / "v291/serializer/AddPlayerSerializer_v291.java")
check("AddPlayerSerializer_v291 に buildPlatform は無い（313 の実効。332 と同じ）",
      "BuildPlatform" not in ap291)
pl291 = read(CB / "v291/serializer/PlayerListSerializer_v291.java")
check("PlayerListSerializer_v291 の add は旧直書き（313 の実効。332 と同じ）",
      "helper.writeString(buffer, skin.getSkinId())" in pl291
      and "helper.writeByteArray(buffer, skin.getSkinData().getImage())" in pl291
      and "trustedSkin" not in pl291)
rps313 = read(CB / "v313/serializer/ResourcePackStackSerializer_v313.java")
check("ResourcePackStackSerializer_v313 に gameVersion は無い（313 自身の実効）",
      "GameVersion" not in rps313)

for hv in ("v291", "v313", "v332"):
    hsrc = read(CB / f"{hv}/BedrockCodecHelper_{hv}.java")
    check(f"BedrockCodecHelper_{hv} に readSkin／writeSkin の上書きは無い（313 のヘルパー継承チェーンに Skin 形式が存在しない）",
          "readSkin" not in hsrc and "writeSkin" not in hsrc)
helper313 = read(CB / "v313/BedrockCodecHelper_v313.java")
check("BedrockCodecHelper_v313 の上書きは readSource のみ（readItem／writeItem の上書きは無い）",
      "public InventorySource readSource" in helper313
      and "readItem(" not in helper313 and "writeItem(" not in helper313)

it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（313 は transaction_type から始まる。332 と同じ）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)

check("MoveEntityDelta は 313 では _v291 が実効（Bedrock_v313.java は触っていない。セッションは MoveEntity で代替）",
      "MoveEntityDeltaPacket" not in v313_src)

# ---------------------------------------------------------------------------
print("== G. コード側の実装（述語・分岐・範囲表記・テスト名） ==")

check("述語 startGameUsesV291LevelSettings() が protocol <= 313 で定義されている（313 回で新規）",
      re.search(r"public boolean startGameUsesV291LevelSettings\(\) \{\s*return protocol <= 313;",
                ver_src) is not None)
check("述語 resourcePacksInfoHasScripting() が protocol >= 332 で定義されている（313 回で新規）",
      re.search(r"public boolean resourcePacksInfoHasScripting\(\) \{\s*return protocol >= 332;",
                ver_src) is not None)
check("述語 addPaintingUsesBlockCoordinates() が protocol <= 354 || 422〜428 のまま（313 は <= 354 に含まれる）",
      re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 \|\| \(protocol >= 422 && protocol <= 428\);",
                ver_src) is not None)
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 のまま（§28 の境界は不変）",
      re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                ver_src) is not None)
gte = sorted({int(n) for n in re.findall(r"protocol >= (\d+)", ver_src)})
check("BedrockVersion の最小の >= 閾値は 332 になった（313 回で resourcePacksInfoHasScripting を追加）",
      gte[0] == 332, f"実際は {gte[0]}")

pkt_src = read(SRC / "BedrockPackets.java")
check("encodeStartGame が LevelSettings を startGameUsesV291LevelSettings で出し分けている（1 箇所）",
      pkt_src.count("version.startGameUsesV291LevelSettings()") == 1,
      f"実際は {pkt_src.count('version.startGameUsesV291LevelSettings()')} 箇所")
check("encodeResourcePacksInfo が has scripts を resourcePacksInfoHasScripting でゲートしている（1 箇所）",
      pkt_src.count("version.resourcePacksInfoHasScripting()") == 1,
      f"実際は {pkt_src.count('version.resourcePacksInfoHasScripting()')} 箇所")
check("encodeStartGame が 1.11.0 パレットを使う（load1_11_0 は 1 箇所。313 もこれを代用する）",
      pkt_src.count("BedrockLegacyBlockPalette.load1_11_0().rawArrayBytes()") == 1)
check("encodeStartGame が 1.12.0 パレットを使う（load は 1 箇所。不変）",
      pkt_src.count("BedrockLegacyBlockPalette.load().rawArrayBytes()") == 1)

legacy_src = read(SRC / "BedrockLegacyBlockPalette.java")
check("BedrockLegacyBlockPalette が bedrock_block_palette_1_11_0.bin を読む（load1_11_0。313 も代用で使う）",
      'RESOURCE_NAME_1_11_0 = "bedrock_block_palette_1_11_0.bin"' in legacy_src
      and "load1_11_0()" in legacy_src)
check("BedrockLegacyBlockPalette の javadoc に 313 の代用明記がある",
      "313（1.8.1）も形式は同じ" in legacy_src
      and "313 自体の実測ではない" in legacy_src)
pal11_bin = RES / "bedrock_block_palette_1_11_0.bin"
check("bedrock_block_palette_1_11_0.bin は 83,378 バイト・SHA-1 15ded0d9…のまま（313 は新規パレットを作らず代用）",
      len(pal11_bin.read_bytes()) == 83378
      and sha1_of(pal11_bin) == "15ded0d92930b81f3cbdbcdf581890eee8a6b07c")
pal12_bin = RES / "bedrock_block_palette_1_12_0.bin"
check("bedrock_block_palette_1_12_0.bin は 92,944 バイト・SHA-1 3553c5c9…のまま（不変）",
      len(pal12_bin.read_bytes()) == 92944
      and sha1_of(pal12_bin) == "3553c5c934caf5be473a954a1222a1a5bb90e6bf")
check("313 用のパレット .bin（1_8_0）は作っていない（実データが無いため代用）",
      not (RES / "bedrock_block_palette_1_8_0.bin").exists())

ses_src = read(SRC / "BedrockSession.java")
check("BedrockSession の切断メッセージが 291-2193 になっている",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in ses_src)
check("BedrockSession が 313 でも MoveEntity 代替を使う（moveEntityDeltaUsesAbsolutePosition の分岐）",
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
      and "void allSixtyTwoSupportedProtocolsResolve()" not in vt_src)
check("BedrockVersionTest に protocol313BoundaryGates がある",
      "protocol313BoundaryGates" in vt_src)
svt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest が allSixtyThreeProtocolsReachStartGame に改名されている",
      "allSixtyThreeProtocolsReachStartGame" in svt_src
      and "void allSixtyOneProtocolsReachStartGame()" not in svt_src)
pvt_src = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
for tname in ("startGame313UsesV291LevelSettings", "levelChunk313Equals332",
              "addPainting313UsesBlockCoordinates",
              "playerListAndResourcePacks313Vs332",
              "decodeInventoryTransactionV0As313"):
    check(f"BedrockPacketsVersionTest に {tname} がある", tname in pvt_src)

# ---------------------------------------------------------------------------
print("== H. ブロック表（不変の確認） ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 が d2b6acbb…のまま",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 が ee36fffb…のまま",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
check("1_8 系のブロックランタイム ID .bin は作っていない（レガシーパレットとは別物）",
      not list(RES.glob("bedrock_block_runtime_ids_1_8_*.bin")))

# ---------------------------------------------------------------------------
print("== I. 文書（BEDROCK.md／PLAYTEST-BEDROCK.md） ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象が 1.7.1〜26.52（291〜2193、62 個）／全 63 プロトコルになっている",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の対応プロトコル一覧が 63 個見出しで、313 の行がある",
      "### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）" in bedrock_md
      and "| 313 | 1.8.0 / 1.8.1 |" in bedrock_md)
check("BEDROCK.md のマトリクス節が 63 プロトコル／56 個／全 63 プロトコルになっている",
      "Bedrock クライアント（上記 63 プロトコル）" in bedrock_md
      and "それ以外の Bedrock プロトコル（57 個）" in bedrock_md
      and "全 63 プロトコル）で形式を確認している" in bedrock_md)
check("BEDROCK.md の核パケット ID の範囲が 291〜2225 で、出典が 419・408・407・390・389・388・361・354・340・332・313 のコーデック確認になっている",
      "291〜2225 の全バージョンで同じ" in bedrock_md
      and "419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・" in bedrock_md
      and "（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認" in bedrock_md)
check("BEDROCK.md の境界表に StartGame の 313（_v291 レイアウト＋末尾 2 bool・パレット代用）がある",
      "1.8.1（313）は LevelSettings が _v291 のレイアウト" in bedrock_md)
check("BEDROCK.md の境界表に ResourcePacksInfo の 313（scriptingEnabled 無し）がある",
      "scriptingEnabled（has_scripts）とエントリの scripting は 332 以降で、1.8.1（313）には無い" in bedrock_md)
check("BEDROCK.md の境界表に LevelChunk の 313（subChunkCount 無し）がある",
      "1.8.1（313）も同じ（BE-NOTES.md §35）。1.7.1（291）も同じ（BE-NOTES.md §36）。26.60（2225）は末尾に client_biome_update が付く" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の 313（ブロック座標）がある",
      "coordinates は 1.7.1・1.8.1・1.9.0・1.10.1・1.11.4 と 1.16.201／1.16.210 が BlockCoordinates" in bedrock_md)
check("BEDROCK.md の境界表に SetEntityData の 313（ENTITY_FLAGS 71 件・index 39 は INTERACT_TEXT）がある",
      "1.8.1（313）は ENTITY_FLAGS が 71 件" in bedrock_md)
check("BEDROCK.md の境界表に MoveEntityDelta の代替列挙に 313 がある",
      "291・313・332・340・354・361・388・389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する" in bedrock_md)
check("BEDROCK.md の境界表に InventoryTransaction の 313（同一形式）がある",
      "1.8.1（313）も同一形式（BE-NOTES.md §35）" in bedrock_md)
check("BEDROCK.md のパレット例外に 313（1.11.0 世代を代用）がある",
      "例外は 313（1.8.1）も同じ：形式は name／meta のみの配列だが" in bedrock_md)
check("BEDROCK.md の BedrockVersion の説明が 63 プロトコルになっている",
      "`BedrockVersion`（64 プロトコル＋ゲート判定）" in bedrock_md)
check("BEDROCK.md のアイテム表が 38 本のバイナリになっている",
      "38 本のバイナリ" in bedrock_md)
check("BEDROCK.md の網羅率に 1.8.1 の代用明記つきエントリがある",
      "1.8.1 は **893**（68.1%、1.16.210 表を共用。" in bedrock_md
      and "313 自体の実測ではない" in bedrock_md)
check("BEDROCK.md のテスト説明が 63 プロトコルの対応表／全 63 プロトコルになっている",
      "`BedrockVersionTest`（64 プロトコルの対応表" in bedrock_md
      and "`BedrockSessionVersionTest`（全 63 プロトコルで Login→" in bedrock_md)

playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md のタイトルが 1.7.1〜26.52 になっている",
      "# Bedrock プレイテスト手順書（1.7.1〜26.52 クライアント）" in playtest_md)
check("PLAYTEST-BEDROCK.md の対象が 291〜2193／63 プロトコルになっている",
      "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)
check("PLAYTEST-BEDROCK.md のバージョン列挙の先頭に 1.8.1 がある",
      "63 プロトコルに対応したため、1.7.1・1.8.1・1.9.0・" in playtest_md)

# ---------------------------------------------------------------------------
print("== J. 判断待ちの不変確認（313 でも触っていないこと） ==")

check("writeEmptySkin は不変（premium を bool で書く形のまま。Skin 形式の分岐は判断待ち。313 にはヘルパー形式自体が無い）",
      "out.writeBoolean(false); // premium" in pkt_src
      and "static void writeEmptySkin" in pkt_src)
check("StartGame の experiments 件数は 419 以上でも varint のまま（li32 との判断は判断待ち）",
      "BedrockVarInt.writeUnsignedVarInt(out, 0); // experiments: なし（419 で新規。408 には無い）" in pkt_src)
check("PlayerSkin は BedrockPackets／BedrockSession に未実装のまま（313 でも実装しない）",
      "PlayerSkin" not in pkt_src and "PlayerSkin" not in ses_src)
check("ItemStackResponse は BedrockPackets／BedrockSession に未実装のまま（313 でも実装しない）",
      "ItemStackResponse" not in pkt_src and "ItemStackResponse" not in ses_src)
check("ContainerClose は BedrockPackets／BedrockSession に未実装のまま（313 でも実装しない）",
      "ContainerClose" not in pkt_src and "ContainerClose" not in ses_src)
check("AvailableCommands は符号化していないまま（COMMAND_PARAMS の FILE_PATH の番号は 313 でも無関係）",
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
