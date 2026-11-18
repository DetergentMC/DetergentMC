#!/usr/bin/env python3
"""BE 291（1.7.1）対応の検証。

291 には minecraft-data の protocol.json が存在しない
（1.7.0／1.7.1 は HTTP 404 でディレクトリ自体が無く、
protocolVersions.json にも 291／1.7 系のエントリは無い）。
出典は CloudburstMC Protocol（ブランチ 3.0、コミット
7163a6aedaefbc1d188431ef90ebc703b1138340）の v291 コーデックで、
`.tmp-be/cloudburst-protocol/` に clone 済み。Bedrock_v291 は
継承チェーンの最古（親を持たないルートコーデック）で、
Bedrock_v313 がこれを土台にする。313 との差分（この実装の
送受信範囲）はワイヤ形式の差が 3 点と送信可否が 1 点：
StartGame の LevelSettings が _v291 の純粋な 31 フィールドで
313 の末尾 2 bool が無いこと、ResourcePackStack が _v291 形式で
末尾の legacy experiment bool が無いこと、AddEntity の第 3
フィールドが identifier 文字列ではなく旧来の数値タイプ ID
（VarUnsignedInt）であること、NetworkChunkPublisherUpdate
（121）が 313 で新規登録のため 291 には存在しないこと
（BE-NOTES.md §36、実測メモ `.tmp-be/be-291-cloudburst-diff.md`）。

共有は 1.7.0／1.7.1（Wiki の両個別ページの infobox
「Protocol version 291」で確認。displayName は最新の 1.7.1。
BE-NOTES.md §36、実測メモ `.tmp-be/be-291-external.md`）。
291 は当初終点だった 313（1.8 世代）の下への延長第 1 弾で、
2026-11-18 のユーザーの指示（「一番古いところまで」）による。

AddEntity の数値タイプ ID の出典は PocketMine-MP 3.4.0
（同タグの ProtocolInfo.php が CURRENT_PROTOCOL = 291／
MINECRAFT_VERSION_NETWORK = '1.7.0'）の EntityIds.php
（`.tmp-be/bedrock291/` に保存済み）。

アイテム表は 313〜431 と同じ bedrock_item_ids_1_16_210.bin を
共用するが、291 については代用である（PMMP BedrockData に
bedrock-1.7.0 タグ自体が存在せず、1.7 系のランタイム ID の
実データは存在しない。291 自体の実測ではない）。StartGame の
ブロックパレットは 291 も配列形式（name／meta のみ）だが、
1.7 系のパレット実データが存在しないため、中身は 1.11.0 世代の
実測レガシーパレット（bedrock_block_palette_1_11_0.bin、
3,183 件）を代用する（291 自体の実測ではない）。

使い方: python3 tools/verify-be-291-tables.py
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


LEVEL_CONST = {"LEVEL_EVENT_SOUND": 1000, "LEVEL_EVENT_PARTICLE": 2000,
               "LEVEL_EVENT_WORLD": 3000, "LEVEL_EVENT_BLOCK": 3500,
               "LEVEL_EVENT_PARTICLE_TYPE": 0x4000}


def eval_key(expr):
    total = 0
    for p in [x.strip() for x in expr.split("+")]:
        if p in LEVEL_CONST:
            total += LEVEL_CONST[p]
        elif p.startswith("0x"):
            total += int(p, 16)
        else:
            total += int(p)
    return total


def split_args(s):
    args, depth, cur = [], 0, ""
    for ch in s:
        if ch == "(":
            depth += 1
        if ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        args.append(cur.strip())
    return args


def codec_packets(upto):
    """コーデックチェーン（v291→…→upto）の登録パケットを
    {クラス名: ID} で返す（`.tmp-be/analyze-291.py` と同じ方式）。"""
    packets = {}
    for v in ("v291", "v313"):
        text = read(CB / f"{v}/Bedrock_{v}.java")
        csec = text[text.find("CODEC ="):]
        for m in re.finditer(r"\.(registerPacket|deregisterPacket|updateSerializer)\s*\(", csec):
            start = m.end()
            depth, j = 1, start
            while j < len(csec) and depth > 0:
                if csec[j] == "(":
                    depth += 1
                elif csec[j] == ")":
                    depth -= 1
                j += 1
            args = split_args(csec[start:j - 1])
            cls = args[0].replace(".class", "").replace("::new", "").strip()
            if m.group(1) == "deregisterPacket":
                packets.pop(cls, None)
            elif m.group(1) == "registerPacket":
                packets[cls] = int(args[2])
        if v == upto:
            break
    return packets


def level_events_v291():
    """Bedrock_v291 の LEVEL_EVENTS のキー集合（式を評価する）。"""
    src = read(CB / "v291/Bedrock_v291.java")
    blk = typemap_block(src, "LEVEL_EVENTS")
    keys = set()
    for m in re.finditer(r"\.(?:insert|put|replace)\(([^;]*?)\),?\s*(?:\.|$)", blk):
        pass
    for m in re.finditer(r"\.(?:insert|put)\(", blk):
        start = m.end()
        depth, j = 1, start
        while j < len(blk) and depth > 0:
            if blk[j] == "(":
                depth += 1
            elif blk[j] == ")":
                depth -= 1
            j += 1
        args = split_args(blk[start:j - 1])
        keys.add(eval_key(args[0]))
    return keys


# ---------------------------------------------------------------------------
print("== A. 出典の同一性（Cloudburst v291 コーデックと protocol.json の不在） ==")

versions_md = read(TMP / "cloudburst-protocol/VERSIONS.md")
check("VERSIONS.md で Bedrock_v291＝1.7.0",
      "| Bedrock_v291  |        1.7.0        |" in versions_md)
check("VERSIONS.md で Bedrock_v313＝1.8.0（291 の 1 つ上の出典確認）",
      "| Bedrock_v313  |        1.8.0        |" in versions_md)

v291_src = read(CB / "v291/Bedrock_v291.java")
check("Bedrock_v291.java の CODEC は protocolVersion(291) と minecraftVersion(\"1.7.0\") を持つ",
      "protocolVersion(291)" in v291_src and 'minecraftVersion("1.7.0")' in v291_src)
check("Bedrock_v291.java は継承チェーンの最古（BedrockCodec.builder() で toBuilder を使わない）",
      "BedrockCodec.builder()" in v291_src and "toBuilder" not in v291_src)
check("Bedrock_v291.java は helper に新規 BedrockCodecHelper_v291 を使う",
      "new BedrockCodecHelper_v291(ENTITY_DATA, GAME_RULE_TYPES)" in v291_src
      and (CB / "v291/BedrockCodecHelper_v291.java").exists())
v313_src = read(CB / "v313/Bedrock_v313.java")
check("Bedrock_v313.java の CODEC は Bedrock_v291.CODEC を土台にする（291 の 1 つ上）",
      "Bedrock_v291.CODEC.toBuilder()" in v313_src)

pk291 = codec_packets("v291")
pk291_ids = set(pk291.values())
check("v291 の登録パケットは 116 件（Cloudburst の登録ベース）",
      len(pk291) == 116, f"実際は {len(pk291)}")
check("v291 の最大 ID は 117・欠番は [116] のみ（ID は 1 始まり）",
      max(pk291_ids) == 117
      and [i for i in range(1, 118) if i not in pk291_ids] == [116],
      f"最大 {max(pk291_ids) if pk291_ids else -1}")
check("v291 に PlayerAuthInput は無い（388 で新規登録）",
      not any("PlayerAuthInput" in k for k in pk291) and 144 not in pk291_ids)
check("v291 に LevelSoundEvent（123）と LevelSoundEvent2（120）は無い（あるのは LevelSoundEvent1Packet＝24）",
      "LevelSoundEventPacket" not in pk291 and "LevelSoundEvent2Packet" not in pk291
      and pk291.get("LevelSoundEvent1Packet") == 24 and 123 not in pk291_ids)
check("v291 に NetworkChunkPublisherUpdate は無い（121 は 313 で新規登録）",
      not any("NetworkChunkPublisherUpdate" in k for k in pk291)
      and 121 not in pk291_ids)
check("v291 に BiomeDefinitionList／AvailableEntityIdentifiers は無い（122／119 は 313 で新規登録）",
      not any("BiomeDefinitionList" in k for k in pk291)
      and not any("AvailableEntityIdentifiers" in k for k in pk291))
pk313 = codec_packets("v313")
check("v313 の登録パケットは 121 件・最大 ID 122・欠番 [116]（291 から +5 件の再確認。ID は 1 始まり）",
      len(pk313) == 121 and max(pk313.values()) == 122
      and [i for i in range(1, 123) if i not in set(pk313.values())] == [116],
      f"実際は {len(pk313)} 件・最大 {max(pk313.values()) if pk313 else -1}")

flags291_idx, _ = chain_upto("ENTITY_FLAGS", "v291")
check("ENTITY_FLAGS はチェーン累積で 291 が 61 件（0〜60 連続。313 の 71 件より 10 件少ない）",
      flags291_idx == set(range(61)),
      f"実際は {len(flags291_idx)} 件・最大 {max(flags291_idx) if flags291_idx else -1}")
data291_idx, data291_keys = chain_upto("ENTITY_DATA", "v291")
check("ENTITY_DATA の index はチェーン累積で 291 が 88 件（0〜87 連続。313 の 0〜93 より狭い）",
      data291_idx == set(range(88)),
      f"実際は {len(data291_idx)} 件・最大 {max(data291_idx) if data291_idx else -1}")
check("ENTITY_DATA の index 39 は 291 が INTERACT_TEXT（313 と同じ）",
      data291_keys.get("EntityDataTypes.INTERACT_TEXT") == 39)
pt291_idx, _ = chain_upto("PARTICLE_TYPES", "v291")
check("PARTICLE_TYPES はチェーン累積で 291 が 45 件（0〜44 連続。313 の 55 件より 10 件少ない）",
      pt291_idx == set(range(45)),
      f"実際は {len(pt291_idx)} 件・最大 {max(pt291_idx) if pt291_idx else -1}")
cp291_idx, _ = chain_upto("COMMAND_PARAMS", "v291")
check("COMMAND_PARAMS はチェーン累積で 291 が 13 件・最大 41（313 の最大 43 と違う。AvailableCommands は未送信で無関係）",
      len(cp291_idx) == 13 and max(cp291_idx) == 41 and 43 not in cp291_idx,
      f"実際は {sorted(cp291_idx)}")
se291_idx, _ = chain_upto("SOUND_EVENTS", "v291")
check("SOUND_EVENTS はチェーン累積で 291 が 235 件・最大 239（313 の 251 件・最大 255 より少ない。LevelSoundEvent は未送信で無関係）",
      len(se291_idx) == 235 and max(se291_idx) == 239,
      f"実際は {len(se291_idx)} 件・最大 {max(se291_idx) if se291_idx else -1}")
ee291_idx, _ = chain_upto("ENTITY_EVENTS", "v291")
check("ENTITY_EVENTS はチェーン累積で 291 が 54 件・最大 72（定義の差のみ）",
      len(ee291_idx) == 54 and max(ee291_idx) == 72,
      f"実際は {len(ee291_idx)} 件・最大 {max(ee291_idx) if ee291_idx else -1}")
le291 = level_events_v291()
le291_scalar = {k for k in le291 if k != 0x4000}
check("LEVEL_EVENTS は 291 がスカラー 72 件・最大 3510（0x4000 の粒子種別サブマップを除く）",
      len(le291_scalar) == 72 and max(le291_scalar) == 3510,
      f"実際は {len(le291_scalar)} 件・最大 {max(le291_scalar) if le291_scalar else -1}")

check("291 の protocol.json は .tmp-be に無い（protocol-1.7.0.json／protocol-1.7.1.json は存在しない）",
      not (TMP / "protocol-1.7.0.json").exists()
      and not (TMP / "protocol-1.7.1.json").exists())
pv = json.loads(read(TMP / "protocolVersions.json"))
pv_protocols = [e["version"] for e in pv]
check("protocolVersions.json に 291 のエントリは無い",
      291 not in pv_protocols)
check("protocolVersions.json に 1.7 系のエントリは無い",
      not any(e["minecraftVersion"].startswith("1.7") for e in pv))
ext_memo = read(TMP / "be-291-external.md")
check("外部出典メモに PMMP BedrockData の bedrock-1.7.0 タグ不在（404）の記録がある",
      "bedrock-1.7.0" in ext_memo and "404" in ext_memo)
check("外部出典メモに 291 より下の最古の出典（70＝0.14.3）の記録がある",
      "70" in ext_memo and "0.14" in ext_memo)

# A-2. PMMP PocketMine-MP 3.4.0（291 と同時代。AddEntity 数値 ID の出典）
pmmp = TMP / "bedrock291/pmmp340"
entity_ids_php = pmmp / "src/pocketmine/entity/EntityIds.php"
check("PocketMine-MP 3.4.0 の EntityIds.php の SHA-1 は 5585c6a7…",
      sha1_of(entity_ids_php) == "5585c6a7cbb18f0b8e87b765e5f35686a07a310b")
eids = read(entity_ids_php)
check("EntityIds.php の代表値（CHICKEN=10／VILLAGER=15／ZOMBIE=32／ARROW=80／SPLASH_POTION=86／FISH=112）",
      "public const CHICKEN = 10;" in eids and "public const VILLAGER = 15;" in eids
      and "public const ZOMBIE = 32;" in eids and "public const ARROW = 80;" in eids
      and "public const SPLASH_POTION = 86;" in eids and "public const FISH = 112;" in eids)
check("EntityIds.php に PHANTOM と CAT の定数は無い（291 では対応付け不可）",
      "PHANTOM" not in eids and "const CAT" not in eids)
proto_info = read(pmmp / "src/pocketmine/network/mcpe/protocol/ProtocolInfo.php")
check("PocketMine-MP 3.4.0 の ProtocolInfo.php は CURRENT_PROTOCOL = 291／MINECRAFT_VERSION_NETWORK = '1.7.0'",
      "public const CURRENT_PROTOCOL = 291;" in proto_info
      and "MINECRAFT_VERSION_NETWORK = '1.7.0'" in proto_info)

# ---------------------------------------------------------------------------
print("== B. 291 のパケット一覧（313 の一覧からの差分） ==")

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
ids313 = {i: n for i, n in ids332.items() if i not in {123}}
check("313 のパケット一覧は 122 件（§35 の再確認）", len(ids313) == 122)

# 291 は 313 から v313 の新規登録 {118 spawn_particle_effect／
# 119 available_entity_identifiers／120 level_sound_event_2／
# 121 network_chunk_publisher_update／122 biome_definition_list}
# を除いたもの（313 の集合からの逆算で、1.7.x 自体の
# protocol.json を実測したものではない。実測メモ
# `.tmp-be/be-291-cloudburst-diff.md` 参照）
ids291 = {i: n for i, n in ids313.items() if i not in {118, 119, 120, 121, 122}}
check("291 のパケット一覧は 117 件（protocol.json 由来の母集団）",
      len(ids291) == 117, f"実際は {len(ids291)}")
check("291 の最大 ID は 128（313 と同じ）",
      max(ids291) == 128)
check("291 の欠番は [37, 116, 118, 119, 120, 121, 122, 123, 124, 125, 126]（ID は 1 始まり）",
      [i for i in range(1, 129) if i not in ids291]
      == [37, 116, 118, 119, 120, 121, 122, 123, 124, 125, 126])
check("291 の一覧に player_auth_input（144）は無い（388 で新規登録）",
      144 not in ids291)
check("291 の一覧に network_chunk_publisher_update（121）は無い（313 で新規登録）",
      121 not in ids291)
check("291 の一覧に add_hanging_entity（16）がある（361 で登録解除）",
      16 in ids291)
# Cloudburst の登録ベースでは 291 は 116 件・最大 ID 117・欠番
# [116] になる（v291 はルートコーデック。BE-NOTES.md §36、
# 実測メモ `.tmp-be/be-291-cloudburst-diff.md`、A 節で確認）。

# ---------------------------------------------------------------------------
print("== C. 核パケットの ID が 291 の一覧に含まれる ==")

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
        check(f"{const_name}（{value}）が 291 の一覧の {packet_name} と一致",
              value in ids291 and ids291[value].startswith(packet_name),
              f"一覧では {ids291.get(value)}")
check("PACKET_PLAYER_AUTH_INPUT（144）は 291 の一覧に無い（パケット自体が存在しない）",
      const_value("PACKET_PLAYER_AUTH_INPUT") == 144
      and const_value("PACKET_PLAYER_AUTH_INPUT") not in ids291)
check("PACKET_NETWORK_CHUNK_PUBLISHER_UPDATE（121）は 291 の一覧に無い（313 で新規登録）",
      const_value("PACKET_NETWORK_CHUNK_PUBLISHER_UPDATE") == 121
      and const_value("PACKET_NETWORK_CHUNK_PUBLISHER_UPDATE") not in ids291)

# ---------------------------------------------------------------------------
print("== D. BedrockVersion の enum（291 の追加） ==")

ver_src = read(SRC / "BedrockVersion.java")
check("V1_7_1(291, \"1.7.1\") がある",
      'V1_7_1(291, "1.7.1")' in ver_src)
check("V1_7_1 は enum の先頭（V1_8_1 より前）",
      ver_src.index("V1_7_1(291") < ver_src.index("V1_8_1(313"))
entries = re.findall(r"^    V1_[A-Z0-9_]+\(\d+,", ver_src, re.M)
check("enum のエントリは 66 件", len(entries) == 66, f"実際は {len(entries)}")
protocols = re.findall(r"^    V1_[A-Z0-9_]+\((\d+),", ver_src, re.M)
check("相異なるプロトコルは 64 個", len(set(protocols)) == 64,
      f"実際は {len(set(protocols))}")
check("対応プロトコルの最小は 291・最大は 2225（RakNet は 291〜2193）",
      min(int(p) for p in protocols) == 291
      and max(int(p) for p in protocols) == 2225)
check("クラス javadoc の protocol.json ファイル数は 51 のまま（291 は protocol.json を増やさない）",
      "51 ファイル" in ver_src)
check("enum コメントに 291 は 1.7.0／1.7.1 の共有と Wiki infobox の出典がある",
      "291 は 1.7.0 / 1.7.1 で共有するプロトコル" in ver_src
      and "Protocol version 291" in ver_src
      and "compatible with 1.7.0 servers" in ver_src)
check("enum コメントに延長の経緯（2026-11-18 のユーザーの指示）がある",
      "一番古いところ" in ver_src and "2026-11-18" in ver_src)
check("enum に 290 のエントリは無い（開発版 1.7.0.3 の番号は対象外）",
      "(290," not in ver_src)

# ---------------------------------------------------------------------------
print("== E. アイテム表（1_16_210 を代用として共用） ==")

check("itemTableTag に case 291 -> \"1_16_210\" がある",
      'case 291 -> "1_16_210";' in ver_src)
check("case 291 のコメントに「291 自体の実測ではない」の代用明記がある",
      "291 自体の実測ではない" in ver_src)
check("case 291 のコメントに bedrock-1.7.0 タグ自体が存在しない旨がある",
      "bedrock-1.7.0 タグ自体が存在せず" in ver_src)
check("itemTableTag の case 313／332／340／354／361／388／389／390／407／408／419／422／428／431 は不変",
      'case 313 -> "1_16_210";' in ver_src
      and 'case 332 -> "1_16_210";' in ver_src
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
check("bedrock_item_ids_1_16_210.bin の SHA-1 が a35eaa5a…と一致（313〜431 と同じ表）",
      sha1_of(bin210) == "a35eaa5a90525651aa5983808508c0e87124cc27")
bins = sorted(p.name for p in RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin は 38 個のまま（291 用の新規生成は無い）",
      len(bins) == 38, f"実際は {len(bins)}")
check("291 用の .bin（1_7_1）は作っていない",
      not any(b == "bedrock_item_ids_1_7_1.bin" for b in bins))
summary = json.loads(read(MOD / "tools/item_tables_summary.json"))
check("item_tables_summary.json に 291 のエントリは無い（313〜422 と同じ扱い）",
      "291" not in summary)
s431 = summary["431"]
check("1_16_210 表の内訳は exact=624／shared=236／legacy=33／fallback=419 のまま（summary の 431 エントリ）",
      s431["tag"] == "bedrock-1.16.210" and s431["exact"] == 624
      and s431["shared"] == 236 and s431["legacy"] == 33
      and s431["fallback"] == 419)

# ---------------------------------------------------------------------------
print("== F. 述語の前提（Cloudburst のソースで 291 世代の形式を確認） ==")

sg291 = read(CB / "v291/serializer/StartGameSerializer_v291.java")
sg313 = read(CB / "v313/serializer/StartGameSerializer_v313.java")
check("StartGameSerializer_v291 の writeLevelSettings は末尾 2 bool を書かない（純粋な 31 フィールド）",
      "buffer.writeBoolean(packet.isFromWorldTemplate())" not in sg291
      and "packet.isFromLockedWorldTemplate()" in sg291)
check("StartGameSerializer_v313 は _v291 の LevelSettings に fromWorldTemplate／fromLockedWorldTemplate を追加する",
      "super.writeLevelSettings(buffer, helper, packet)" in sg313
      and "buffer.writeBoolean(packet.isFromWorldTemplate())" in sg313
      and "buffer.writeBoolean(packet.isFromLockedWorldTemplate())" in sg313)
check("StartGameSerializer_v291 に trustingPlayers と放送モードの bool がある（291 の実効レイアウト）",
      "packet.isTrustingPlayers()" in sg291
      and "packet.getXblBroadcastMode() != GamePublishSetting.NO_MULTI_PLAY" in sg291
      and "packet.getPlatformBroadcastMode() != GamePublishSetting.NO_MULTI_PLAY" in sg291)
rps291 = read(CB / "v291/serializer/ResourcePackStackSerializer_v291.java")
rps313 = read(CB / "v313/serializer/ResourcePackStackSerializer_v313.java")
check("ResourcePackStackSerializer_v291 に experiments／legacy experiment の bool は無い",
      "Experiment" not in rps291)
check("ResourcePackStackSerializer_v313 は末尾に legacy experiment の bool を書く",
      "LEGACY_EXPERIMENT_DATA" in rps313
      and "packet.getExperiments().contains(LEGACY_EXPERIMENT_DATA)" in rps313)
ae291 = read(CB / "v291/serializer/AddEntitySerializer_v291.java")
ae313 = read(CB / "v313/serializer/AddEntitySerializer_v313.java")
check("AddEntitySerializer_v291 は entityType を VarUnsignedInt（数値 ID）で書く",
      "VarInts.writeUnsignedInt(buffer, packet.getEntityType())" in ae291
      and "packet.getIdentifier()" not in ae291)
check("AddEntitySerializer_v313 は identifier を文字列で書く",
      "helper.writeString(buffer, packet.getIdentifier())" in ae313)
helper291 = read(CB / "v291/BedrockCodecHelper_v291.java")
helper313 = read(CB / "v313/BedrockCodecHelper_v313.java")
check("BedrockCodecHelper_v291 に readSource の上書きは無い（Base の読み方が実効）",
      "readSource" not in helper291)
check("BedrockCodecHelper_v313 は readSource で UNTRACKED_INTERACTION_UI を特別扱いする（291 との差）",
      "readSource" in helper313 and "UNTRACKED_INTERACTION_UI" in helper313)
check("BedrockCodecHelper_v291 に readSkin／writeSkin は無い（Skin のヘルパー形式自体が存在しない）",
      "readSkin" not in helper291 and "writeSkin" not in helper291)
it291 = read(CB / "v291/serializer/InventoryTransactionSerializer_v291.java")
check("InventoryTransactionSerializer_v291 に legacyRequestId は無い（291 世代の基本形式）",
      "legacyRequestId" not in it291 and "LegacyRequestId" not in it291)

# ---------------------------------------------------------------------------
print("== G. 実装（述語・エンコーダ・範囲文字列・テスト名） ==")

check("述語 startGameUsesV313LevelSettings は protocol == 313",
      re.search(r"public boolean startGameUsesV313LevelSettings\(\) \{\s*return protocol == 313;",
                ver_src) is not None)
check("述語 resourcePackStackUsesV291Format は protocol <= 291",
      re.search(r"public boolean resourcePackStackUsesV291Format\(\) \{\s*return protocol <= 291;",
                ver_src) is not None)
check("述語 addEntityUsesLegacyTypeId は protocol <= 291",
      re.search(r"public boolean addEntityUsesLegacyTypeId\(\) \{\s*return protocol <= 291;",
                ver_src) is not None)
check("述語 hasNetworkChunkPublisherUpdate は protocol > 291（対応範囲では 313 以降と同値）",
      re.search(r"public boolean hasNetworkChunkPublisherUpdate\(\) \{\s*return protocol > 291;",
                ver_src) is not None)
gte = [int(x) for x in re.findall(r"protocol >= (\d+)", ver_src)]
check("BedrockVersion.java の述語の最小の >= 閾値は 332 のまま（291 の述語は == ／ <= ／ > の形）",
      min(gte) == 332, f"実際は {min(gte) if gte else None}")
pkt_src = read(SRC / "BedrockPackets.java")
check("BedrockPackets の StartGame で startGameUsesV313LevelSettings の分岐は 1 箇所",
      pkt_src.count("version.startGameUsesV313LevelSettings()") == 1)
check("BedrockPackets の ResourcePackStack で resourcePackStackUsesV291Format の分岐は 1 箇所",
      pkt_src.count("version.resourcePackStackUsesV291Format()") == 1)
check("BedrockPackets の AddEntity で addEntityUsesLegacyTypeId の分岐は 1 箇所",
      pkt_src.count("version.addEntityUsesLegacyTypeId()") == 1)
check("BedrockPackets の AddEntity は BedrockLegacyEntityTypeIds で数値 ID を引く",
      "BedrockLegacyEntityTypeIds.legacyIdFor(entityType)" in pkt_src)
legacy_src = read(SRC / "BedrockLegacyEntityTypeIds.java")
check("BedrockLegacyEntityTypeIds に PocketMine-MP 3.4.0 の EntityIds.php の出典（SHA-1）がある",
      "PocketMine-MP 3.4.0" in legacy_src
      and "5585c6a7cbb18f0b8e87b765e5f35686a07a310b" in legacy_src)
check("BedrockLegacyEntityTypeIds の代表値（zombie=32／chicken=10／arrow=80／cod=112／villager_v2=15）",
      'case "minecraft:zombie" -> 32;' in legacy_src
      and 'case "minecraft:chicken" -> 10;' in legacy_src
      and 'case "minecraft:arrow" -> 80;' in legacy_src
      and 'case "minecraft:cod" -> 112;' in legacy_src
      and '"minecraft:villager_v2" -> 15;' in legacy_src)
session_src = read(SRC / "BedrockSession.java")
check("BedrockSession の切断メッセージは 291-2193",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in session_src)
check("BedrockSession は 291 に NetworkChunkPublisherUpdate を送らない分岐がある",
      "bedrockVersion.hasNetworkChunkPublisherUpdate()" in session_src)
check("BedrockSession は対応表に無いエンティティを 291 に送らない分岐がある",
      "BedrockLegacyEntityTypeIds.legacyIdFor(type) < 0" in session_src)
check("BedrockProtocol の javadoc の対象範囲は 291〜2193 で protocol.json は 62 バージョン分",
      "Bedrock 1.7.1〜26.52（プロトコル 291〜2193" in proto_src
      and "protocol.json（62 バージョン分）" in proto_src
      and "v313・v291 コーデックで確認した" in proto_src)
server_src = read(SRC / "BedrockServer.java")
check("BedrockServer の開始ログは 291〜2193",
      "対象 Bedrock 1.7.1〜26.52 / プロトコル 291〜2193（RakNet）" in server_src)
vtest = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java")
check("BedrockVersionTest に allSixtyFourSupportedProtocolsResolve と protocol291BoundaryGates がある",
      "allSixtyFourSupportedProtocolsResolve" in vtest
      and "protocol291BoundaryGates" in vtest)
ptest = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java")
check("BedrockPacketsVersionTest に 291 のテストがある",
      "startGame291UsesPureV291LevelSettings" in ptest
      and "playerListAndResourcePacks291Vs313" in ptest
      and "addEntity291UsesLegacyTypeId" in ptest
      and "decodeInventoryTransactionV0As291" in ptest)
stest = read(MOD / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java")
check("BedrockSessionVersionTest に allSixtyThreeProtocolsReachStartGame がある",
      "allSixtyThreeProtocolsReachStartGame" in stest)

# ---------------------------------------------------------------------------
print("== H. ブロック表・パレットは不変 ==")

check("canonical_block_states_1_20_50.nbt の SHA-1 は不変",
      sha1_of(RES / "canonical_block_states_1_20_50.nbt")
      == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("bedrock_block_runtime_ids_1_20_4.bin の SHA-1 は不変",
      sha1_of(RES / "bedrock_block_runtime_ids_1_20_4.bin")
      == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
pal111 = RES / "bedrock_block_palette_1_11_0.bin"
check("bedrock_block_palette_1_11_0.bin は 83,378 バイト・SHA-1 15ded0d9…のまま（291 も代用で使う）",
      pal111.stat().st_size == 83378
      and sha1_of(pal111) == "15ded0d92930b81f3cbdbcdf581890eee8a6b07c")
pal112 = RES / "bedrock_block_palette_1_12_0.bin"
check("bedrock_block_palette_1_12_0.bin は 92,944 バイト・SHA-1 3553c5c9…のまま",
      pal112.stat().st_size == 92944
      and sha1_of(pal112) == "3553c5c934caf5be473a954a1222a1a5bb90e6bf")

# ---------------------------------------------------------------------------
print("== I. ドキュメント ==")

bedrock_md = read(ROOT / "docs/BEDROCK.md")
check("BEDROCK.md の対象は 1.7.1〜26.52／291〜2193／公式 63 個／全 64 プロトコル",
      "1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個" in bedrock_md
      and "（全 64 プロトコル）" in bedrock_md)
check("BEDROCK.md の一覧に 291 の行がある（見出しは 64 個）",
      "対応プロトコル一覧（64 個" in bedrock_md
      and "| 291 | 1.7.0 / 1.7.1 |" in bedrock_md)
check("BEDROCK.md の境界表に StartGame の 291 の注記がある",
      "1.7.1（291）は LevelSettings が _v291 の純粋な 31 フィールド" in bedrock_md)
check("BEDROCK.md の境界表に LevelChunk の 291 の注記がある",
      "1.7.1（291）も同じ（BE-NOTES.md §36）。26.60（2225）は末尾に client_biome_update が付く" in bedrock_md)
check("BEDROCK.md の境界表に AddEntity の 291 の注記がある",
      "1.7.1（291）は第 3 フィールドが identifier 文字列ではなく旧来の数値タイプ ID" in bedrock_md)
check("BEDROCK.md の境界表に ResourcePackStack の 291 の注記がある",
      "1.7.1（291）は legacy experiment フラグ自体が無く" in bedrock_md)
check("BEDROCK.md の境界表に AddPainting の 291 の列挙がある",
      "coordinates は 1.7.1・1.8.1・1.9.0" in bedrock_md)
check("BEDROCK.md のパレット節に 291 の例外がある",
      "例外は 291（1.7.1）も同じ" in bedrock_md)
playtest_md = read(ROOT / "docs/PLAYTEST-BEDROCK.md")
check("PLAYTEST-BEDROCK.md の対象は 1.7.1〜26.52／291〜2193／63 プロトコル",
      "（1.7.1〜26.52 クライアント）" in playtest_md
      and "プロトコル 291〜2193 の RakNet 世代。63 プロトコル" in playtest_md)

# ---------------------------------------------------------------------------
print()
total = passed + failed
if failed:
    print(f"FAILED: {failed}/{total} 件")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
print(f"ALL PASS: {total}/{total} checks（BE 291）")
