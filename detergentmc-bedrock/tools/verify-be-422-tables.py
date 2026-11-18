#!/usr/bin/env python3
"""Bedrock 1.16.201（プロトコル 422）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py /
verify-be-534-tables.py / verify-be-527-tables.py /
verify-be-503-tables.py / verify-be-486-tables.py / verify-be-475-tables.py /
verify-be-471-tables.py / verify-be-465-tables.py / verify-be-448-tables.py /
verify-be-440-tables.py / verify-be-431-tables.py / verify-be-428-tables.py
にならい、表・定数・述語の前提を機械計数で確認する。すべて実ファイル
（BedrockVersion.java / BedrockProtocol.java / BedrockSession.java /
protocol-1.16.201.json / protocol-1.16.210.json / protocol-1.16.220.json /
protocol-1.17.0.json / protocol-1.17.10.json / protocol-1.17.30.json /
protocol-1.17.40.json / protocol-1.18.0.json / protocol-1.18.11.json /
protocol-1.18.30.json / protocol-1.19.1.json / protocol-1.19.10.json /
protocol-1.19.20.json / protocol-1.19.30.json / protocol-1.19.40.json /
protocol-1.19.50.json / .bin データ）に対するチェックで、推測はない。

- 422 の protocol.json は minecraft-data の `data/bedrock/1.16.201/` にある。
- 422 は 1.16.200 と 1.16.201 の 2 バージョンで共有するプロトコル
  （protocolVersions.json では 422＝1.16.201 の 1 エントリのみ。Minecraft
  Wiki「Bedrock Edition 1.16.200」「Bedrock Edition 1.16.201」個別ページの
  infobox「Protocol version 422」でも確認。BE-NOTES.md §22）。
  displayName は 1.16.201（protocol.json のディレクトリ名）。
  minecraft-data に `data/bedrock/1.16.200/protocol.json` は存在しない
  （HTTP 404）。
- 422 と 428 の差分は追加 1・削除 0・リネーム 0・型変更 52 件。
  パケット数は 160 件・最大 ID 163・欠番 [16, 37, 116] で 428 と同じ。
  StartGame は 422 が 57 フィールド・428 が 59 フィールドで、
  rewind_history_size・server_authoritative_block_breaking の 2 フィールド
  だけ違う（428 で新規）。spawn は 428 の player_position と改名だけで
  ワイヤ同一。movement_authority の値 2 の名称は 422 が server_v2_rewind・
  428 が server_with_rewind（値は同じ zigzag32 の 2）。
  AddPlayer・AddEntity・AddItemEntity・AddPainting はフィールド名が同一で
  runtime_entity_id の型だけが違う（422 は varint、428 は varint64）。
  どちらも符号なし LEB128 で、値が 32 ビットに収まる限りバイト同一のため
  分岐は要らない（BedrockVarInt.writeUnsignedVarInt）。
- 422 の 1 つ下は 419（1.16.101／1.16.100）。423〜427 はベータ版 1.16.210
  の番号、429・430 はベータ版 1.16.220 の番号。426 は Wiki 表にエントリ
  無しで未確認。protocolVersions.json にも 423〜427・429・430 のエントリは
  無い。419 の protocol.json は minecraft-data に存在しない
  （`data/bedrock/1.16.101/protocol.json` と `data/bedrock/1.16.100/
  protocol.json` はどちらも HTTP 404）。protocolVersions.json にも 419 の
  エントリは無い（422 より小さい最大のエントリは 100）。BE-NOTES.md §22。
- アイテム表は新規生成不要。bedrock-1.16.201 タグは存在しない（4 ファイル
  すべて HTTP 404）が、bedrock-1.16.200 タグの required_item_list.json
  （91,328 バイト、SHA-1 682782b5…）から再生成した候補表が既存の
  bedrock_item_ids_1_16_210.bin とバイト同一（SHA-1 a35eaa5a…、
  exact=624／shared=236／legacy=33／fallback=419）。1.16.200 は 422 を
  共有するリリース版そのものなので他バージョンからの代用ではない。ただし
  1.16.201 固有のアイテム差分が無いことの直接の証拠は無い（未確認）。
  item_tables_summary.json に 422 のエントリは無い
  （554・544・428 が表を共用するときと同じ流儀）。
- ブロックパレットは 1.16.200 のものが 6,611 エントリーで、1.16.210 の
  パレットとはバイト同一ではない（同一ランタイム ID は 6,026/6,611、
  1.16.200 にのみある状態 48 件、1.16.210 にのみある状態 12 件）。
  それでもブロック表は新規不要（既存ペアを流用。589 と同じ根拠。
  1.16.200 と 1.20.50 の同一ランタイム ID は 0/6,611。BE-NOTES.md §22）。
  biome_id_map.json は bedrock-1.16.201／bedrock-1.16.200 のどちらのタグにも
  存在しない（HTTP 404・取得したファイルの中身は "404: Not Found" の
  14 バイト）が設計上使っていないため要否判断に影響しない。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 422 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 422 回の実質的な変更は 1 件：**StartGame の rewind_history_size・
  server_authoritative_block_breaking を 428 以降でのみ書く**
  （hasStartGameRewindFields() = protocol >= 428。422 には無い）。
  AddPainting の coordinates・PlayerList の verified は protocol <= 428
  の既存の分岐で 422 も正しく扱われる（BE-NOTES.md §21 の分岐）。
  それ以外の 51 件の型変更のうちコードが読む・書くのは無い
  （Item の count 無し・Skin の premium が string・MetadataDictionary の
  キーずれ・InputFlag・player_auth_input の追加 3 フィールド・
  Transaction の network_ids は判断待ちで直さない。§J 参照）。

使い方: python3 detergentmc-bedrock/tools/verify-be-422-tables.py
終了コード 0 = 全チェック通過。
"""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent  # リポジトリ直下
BE = ROOT / "detergentmc-bedrock"
SRC = BE / "src/main/java/io/detergentmc/bedrock"
RES = BE / "src/main/resources/io/detergentmc/bedrock"
TMP = ROOT / ".tmp-be"  # protocol.json 等の調査用一時ファイル（リポジトリ直下）

CHECKS = []
FAILURES = []


def check(name, ok, detail=""):
    CHECKS.append(name)
    status = "PASS" if ok else "FAIL"
    print(f"[{status}] {name}" + (f" — {detail}" if detail else ""))
    if not ok:
        FAILURES.append(name)


def sha1(path):
    return hashlib.sha1(path.read_bytes()).hexdigest()


def container_fields(types, name):
    """protocol.json の container 型のフィールド名一覧（順序付き）を返す。"""
    t = types[name]
    assert t[0] == "container", f"{name} は container ではない: {t[0]}"
    return [(f.get("name"), f["type"]) for f in t[1]]


def field_names(types, name):
    return [n for n, _ in container_fields(types, name) if n is not None]


def field_type(types, name, field):
    for n, t in container_fields(types, name):
        if n == field:
            return t
    return None


def find_field(obj, name):
    """JSON 内で name が name の欄の type を再帰的に探して返す。"""
    if isinstance(obj, dict):
        if obj.get("name") == name:
            return obj.get("type")
        for v in obj.values():
            r = find_field(v, name)
            if r is not None:
                return r
    elif isinstance(obj, list):
        for v in obj:
            r = find_field(v, name)
            if r is not None:
                return r
    return None


def item_stack_request_type_id(types):
    """ItemStackRequest の actions 配列の要素コンテナにある type_id の mapper を返す。
    type_id は ItemStackRequest の直下フィールドではなく actions 要素の中にある。"""
    actions = field_type(types, "ItemStackRequest", "actions")
    inner = actions[1]["type"]
    for f in inner[1]:
        if f.get("name") == "type_id":
            return f["type"]
    return None


def find_flags_type(obj):
    """packet_available_commands の JSON 内で name が flags の欄の type を返す。
    flags は enums 配列の要素コンテナの中にある。"""
    if isinstance(obj, dict):
        if obj.get("name") == "flags":
            return obj.get("type")
        for v in obj.values():
            r = find_flags_type(v)
            if r is not None:
                return r
    elif isinstance(obj, list):
        for v in obj:
            r = find_flags_type(v)
            if r is not None:
                return r
    return None


def find_mapper_with(obj, key):
    """JSON 内で mappings の値に key を含む mapper を再帰的に探して返す。"""
    if isinstance(obj, dict):
        if isinstance(obj.get("mappings"), dict) and key in obj["mappings"].values():
            return obj["mappings"]
        for v in obj.values():
            r = find_mapper_with(v, key)
            if r is not None:
                return r
    elif isinstance(obj, list):
        for v in obj:
            r = find_mapper_with(v, key)
            if r is not None:
                return r
    return None


def packet_ids(data):
    mapper = data["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
    return {int(k): v for k, v in mapper.items()}


# ---------- A. protocol.json（422）の同一性 ----------
p22 = TMP / "protocol-1.16.201.json"
check("422 protocol.json が存在する", p22.is_file(), str(p22))
check("422 protocol.json の行数が 7,465 行（wc -l、改行数）",
      p22.is_file()
      and p22.read_text().count("\n") == 7465,
      f"{p22.read_text().count(chr(10))} 行" if p22.is_file() else "なし")
check("422 protocol.json のサイズが 190,275 バイト",
      p22.is_file() and p22.stat().st_size == 190275,
      f"{p22.stat().st_size} バイト" if p22.is_file() else "なし")
check("422 protocol.json の SHA-1 が 02eed26e…と一致",
      p22.is_file()
      and sha1(p22) == "02eed26e80b9d08e8ae8721fb40a88f6bdbf9847",
      sha1(p22) if p22.is_file() else "なし")

data422 = json.loads(p22.read_text())
types422 = data422["types"]
ids422 = packet_ids(data422)

# ---------- A1. 隣接バージョン（428）の protocol.json ----------
p28 = TMP / "protocol-1.16.210.json"
check("428 protocol.json（上側の隣接）が存在する", p28.is_file(), str(p28))
check("428 protocol.json の行数が 8,052 行・サイズが 205,856 バイト・SHA-1 が 8e46e9b8…と一致",
      p28.is_file() and p28.read_text().count("\n") == 8052
      and p28.stat().st_size == 205856
      and sha1(p28) == "8e46e9b85cba34905660df27aaebbcd77977cfe7",
      f"{p28.read_text().count(chr(10))} 行・{p28.stat().st_size} バイト" if p28.is_file() else "なし")
check("422 の protocol.json は 428 のものとバイト同一ではない（SHA-1 が違う）",
      p22.is_file() and p28.is_file()
      and sha1(p22) != sha1(p28))

data428 = json.loads(p28.read_text())
types428 = data428["types"]
ids428 = packet_ids(data428)
# ---------- A2. 隣接バージョン（431・440・448・465・471・475・486・503・527・534・544・554・557・560）の protocol.json ----------
p31 = TMP / "protocol-1.16.220.json"
check("431 protocol.json（上側の隣接）が存在する", p31.is_file(), str(p31))
check("431 protocol.json の行数が 9,002 行・サイズが 232,353 バイト・SHA-1 が a51a86a9…と一致",
      p31.is_file() and p31.read_text().count("\n") == 9002
      and p31.stat().st_size == 232353
      and sha1(p31) == "a51a86a9e60a583ca6746bf65676d096a735c688",
      f"{p31.read_text().count(chr(10))} 行・{p31.stat().st_size} バイト" if p31.is_file() else "なし")
check("428 の protocol.json は 431 のものとバイト同一ではない（SHA-1 が違う）",
      p28.is_file() and p31.is_file()
      and sha1(p28) != sha1(p31))
p40 = TMP / "protocol-1.17.0.json"
check("440 protocol.json が存在する", p40.is_file(), str(p40))
check("440 protocol.json の行数が 9,115 行・サイズが 235,230 バイト・SHA-1 が 9958534f…と一致",
      p40.is_file() and p40.read_text().count("\n") == 9115
      and p40.stat().st_size == 235230
      and sha1(p40) == "9958534fd0f90f0a211044cd525639295458a7bb",
      f"{p40.read_text().count(chr(10))} 行・{p40.stat().st_size} バイト" if p40.is_file() else "なし")
p48 = TMP / "protocol-1.17.10.json"
check("448 protocol.json が存在する", p48.is_file(), str(p48))
check("448 protocol.json の行数が 9,289 行・サイズが 241,661 バイト・SHA-1 が 3a5c09f3…と一致",
      p48.is_file() and p48.read_text().count("\n") == 9289
      and p48.stat().st_size == 241661
      and sha1(p48) == "3a5c09f3e6b0a52d86878ce09a9b28e17919acb9",
      f"{p48.read_text().count(chr(10))} 行・{p48.stat().st_size} バイト" if p48.is_file() else "なし")
p65 = TMP / "protocol-1.17.30.json"
check("465 protocol.json が存在する", p65.is_file(), str(p65))
check("465 protocol.json の行数が 9,590 行・サイズが 248,252 バイト・SHA-1 が 6e4f4df8…と一致",
      p65.is_file() and p65.read_text().count("\n") == 9590
      and p65.stat().st_size == 248252
      and sha1(p65) == "6e4f4df82649c941c17aff5fd4ddee77b1266e10",
      f"{p65.read_text().count(chr(10))} 行・{p65.stat().st_size} バイト" if p65.is_file() else "なし")
p71 = TMP / "protocol-1.17.40.json"
check("471 protocol.json が存在する", p71.is_file(), str(p71))
check("471 protocol.json の行数が 9,687 行・サイズが 250,827 バイト・SHA-1 が 081fa7f4…と一致",
      p71.is_file() and p71.read_text().count("\n") == 9687
      and p71.stat().st_size == 250827
      and sha1(p71) == "081fa7f411b80ed9d22116c929c82aa61d8462f8",
      f"{p71.read_text().count(chr(10))} 行・{p71.stat().st_size} バイト" if p71.is_file() else "なし")
p75 = TMP / "protocol-1.18.0.json"
check("475 protocol.json が存在する", p75.is_file(), str(p75))
check("475 protocol.json の行数が 9,752 行・サイズが 252,425 バイト・SHA-1 が f58c442e…と一致",
      p75.is_file() and p75.read_text().count("\n") == 9752
      and p75.stat().st_size == 252425
      and sha1(p75) == "f58c442e7d7072994e929b5eb3d63522ef334530",
      f"{p75.read_text().count(chr(10))} 行・{p75.stat().st_size} バイト" if p75.is_file() else "なし")
p86 = TMP / "protocol-1.18.11.json"
check("486 protocol.json が存在する", p86.is_file(), str(p86))
check("486 protocol.json の行数が 10,044 行・サイズが 260,293 バイト・SHA-1 が 2ffd5781…と一致",
      p86.is_file() and p86.read_text().count("\n") == 10044
      and p86.stat().st_size == 260293
      and sha1(p86) == "2ffd57813e9904b8962ddd390c6b521b77ab4aa0",
      f"{p86.read_text().count(chr(10))} 行・{p86.stat().st_size} バイト" if p86.is_file() else "なし")
p03 = TMP / "protocol-1.18.30.json"
check("503 protocol.json が存在する", p03.is_file(), str(p03))
check("503 protocol.json の行数が 10,289 行・サイズが 267,010 バイト・SHA-1 が 7d84fdc2…と一致",
      p03.is_file() and p03.read_text().count("\n") == 10289
      and p03.stat().st_size == 267010
      and sha1(p03) == "7d84fdc210ed3fb93f584894bc06875839fe5050",
      f"{p03.read_text().count(chr(10))} 行・{p03.stat().st_size} バイト" if p03.is_file() else "なし")
p101 = TMP / "protocol-1.19.1.json"
check("527 protocol.json が存在する", p101.is_file(), str(p101))
check("527 protocol.json の行数が 10,473 行・サイズが 271,781 バイト・SHA-1 が 02b182a1…と一致",
      p101.is_file() and p101.read_text().count("\n") == 10473
      and p101.stat().st_size == 271781
      and sha1(p101) == "02b182a1e3433ec115a1ec854c1ef07b30e6ff4e",
      f"{p101.read_text().count(chr(10))} 行・{p101.stat().st_size} バイト" if p101.is_file() else "なし")
p110 = TMP / "protocol-1.19.10.json"
check("534 protocol.json が存在する", p110.is_file(), str(p110))
check("534 protocol.json の行数が 10,646 行・サイズが 275,518 バイト・SHA-1 が 6586b076…と一致",
      p110.is_file() and p110.read_text().count("\n") == 10646
      and p110.stat().st_size == 275518
      and sha1(p110) == "6586b07615e11c7a318c98da4170496ab2c95026",
      f"{p110.read_text().count(chr(10))} 行・{p110.stat().st_size} バイト" if p110.is_file() else "なし")
p120 = TMP / "protocol-1.19.20.json"
check("544 protocol.json が存在する", p120.is_file(), str(p120))
check("544 protocol.json の行数が 10,836 行・サイズが 280,171 バイト・SHA-1 が 60acb22c…と一致",
      p120.is_file() and p120.read_text().count("\n") == 10836
      and p120.stat().st_size == 280171
      and sha1(p120) == "60acb22c50099d389704f9a811cf2682d0028241",
      f"{p120.read_text().count(chr(10))} 行・{p120.stat().st_size} バイト" if p120.is_file() else "なし")
p130 = TMP / "protocol-1.19.30.json"
check("554 protocol.json が存在する", p130.is_file(), str(p130))
check("554 protocol.json の行数が 11,048 行・サイズが 285,450 バイト・SHA-1 が 960342ef…と一致",
      p130.is_file() and p130.read_text().count("\n") == 11048
      and p130.stat().st_size == 285450
      and sha1(p130) == "960342ef57d6207a8e7fd581dbb47eff34abd058",
      f"{p130.read_text().count(chr(10))} 行・{p130.stat().st_size} バイト" if p130.is_file() else "なし")
p140 = TMP / "protocol-1.19.40.json"
check("557 protocol.json が存在する", p140.is_file(), str(p140))
check("557 protocol.json の行数が 11,130 行・サイズが 287,657 バイト・SHA-1 が f3f81376…と一致",
      p140.is_file() and p140.read_text().count("\n") == 11130
      and p140.stat().st_size == 287657
      and sha1(p140) == "f3f8137679253a10f2dfafb74d3c4be10d519883",
      f"{p140.read_text().count(chr(10))} 行・{p140.stat().st_size} バイト" if p140.is_file() else "なし")
p150 = TMP / "protocol-1.19.50.json"
check("560 protocol.json が存在する", p150.is_file(), str(p150))
check("560 protocol.json の行数が 11,179 行・サイズが 288,987 バイト・SHA-1 が 5fd84ac8…と一致",
      p150.is_file() and p150.read_text().count("\n") == 11179
      and p150.stat().st_size == 288987
      and sha1(p150) == "5fd84ac85c9ed237e5aa8dc680a0c7cb87c5b9bd",
      f"{p150.read_text().count(chr(10))} 行・{p150.stat().st_size} バイト" if p150.is_file() else "なし")
pver = TMP / "protocolVersions.json"
check("protocolVersions.json が存在する", pver.is_file(), str(pver))
if pver.is_file():
    pv = json.loads(pver.read_text())
    check("protocolVersions.json に 423〜427・429・430 のエントリが無い",
          not any(e["version"] in (423, 424, 425, 426, 427, 429, 430) for e in pv))
    check("protocolVersions.json の 428 は 1.16.210（1 エントリのみ）",
          [e["minecraftVersion"] for e in pv if e["version"] == 428] == ["1.16.210"])
    check("protocolVersions.json の 428 の 1 つ下は 422（1.16.201）",
          max(e["version"] for e in pv if e["version"] < 428) == 422)
    check("protocolVersions.json の 422 は 1.16.201",
          [e["minecraftVersion"] for e in pv if e["version"] == 422] == ["1.16.201"])
    check("protocolVersions.json の 428 の 1 つ上は 431（1.16.220）",
          min(e["version"] for e in pv if e["version"] > 428) == 431)
    check("protocolVersions.json の 431 は 1.16.220",
          [e["minecraftVersion"] for e in pv if e["version"] == 431] == ["1.16.220"])
    check("protocolVersions.json の 431 の 1 つ下は 428（1.16.210）",
          max(e["version"] for e in pv if e["version"] < 431) == 428)
    check("protocolVersions.json の 440 は 1.17.0",
          [e["minecraftVersion"] for e in pv if e["version"] == 440] == ["1.17.0"])
    check("protocolVersions.json の 440 の 1 つ下は 431（1.16.220）",
          max(e["version"] for e in pv if e["version"] < 440) == 431)
    check("protocolVersions.json の 448 は 1.17.10",
          [e["minecraftVersion"] for e in pv if e["version"] == 448] == ["1.17.10"])
    check("protocolVersions.json の 448 の 1 つ下は 440（1.17.0）",
          max(e["version"] for e in pv if e["version"] < 448) == 440)
    check("protocolVersions.json の 465 は 1.17.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 465] == ["1.17.30"])
    check("protocolVersions.json の 465 の 1 つ下は 448（1.17.10）",
          max(e["version"] for e in pv if e["version"] < 465) == 448)
    check("protocolVersions.json の 471 は 1.17.40",
          [e["minecraftVersion"] for e in pv if e["version"] == 471] == ["1.17.40"])
    check("protocolVersions.json の 475 は 1.18.0",
          [e["minecraftVersion"] for e in pv if e["version"] == 475] == ["1.18.0"])
    check("protocolVersions.json の 486 は 1.18.11",
          [e["minecraftVersion"] for e in pv if e["version"] == 486] == ["1.18.11"])
    check("protocolVersions.json の 503 は 1.18.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 503] == ["1.18.30"])
    check("protocolVersions.json の 527 は 1.19.1",
          [e["minecraftVersion"] for e in pv if e["version"] == 527] == ["1.19.1"])

data431 = json.loads(p31.read_text()) if p31.is_file() else None
data440 = json.loads(p40.read_text()) if p40.is_file() else None
data448 = json.loads(p48.read_text()) if p48.is_file() else None
data465 = json.loads(p65.read_text()) if p65.is_file() else None
data471 = json.loads(p71.read_text()) if p71.is_file() else None
data475 = json.loads(p75.read_text()) if p75.is_file() else None
data486 = json.loads(p86.read_text()) if p86.is_file() else None
data503 = json.loads(p03.read_text()) if p03.is_file() else None
data527 = json.loads(p101.read_text()) if p101.is_file() else None
data534 = json.loads(p110.read_text()) if p110.is_file() else None
data544 = json.loads(p120.read_text()) if p120.is_file() else None
data554 = json.loads(p130.read_text()) if p130.is_file() else None
data557 = json.loads(p140.read_text()) if p140.is_file() else None
data560 = json.loads(p150.read_text()) if p150.is_file() else None
types431 = data431["types"] if data431 else {}
types440 = data440["types"] if data440 else {}
types448 = data448["types"] if data448 else {}
types465 = data465["types"] if data465 else {}
types471 = data471["types"] if data471 else {}
types475 = data475["types"] if data475 else {}
types486 = data486["types"] if data486 else {}
types503 = data503["types"] if data503 else {}
types527 = data527["types"] if data527 else {}
types534 = data534["types"] if data534 else {}
types544 = data544["types"] if data544 else {}
types554 = data554["types"] if data554 else {}
types557 = data557["types"] if data557 else {}
types560 = data560["types"] if data560 else {}
ids431 = packet_ids(data431) if data431 else {}
ids440 = packet_ids(data440) if data440 else {}
ids448 = packet_ids(data448) if data448 else {}
ids465 = packet_ids(data465) if data465 else {}
ids471 = packet_ids(data471) if data471 else {}
ids475 = packet_ids(data475) if data475 else {}
ids486 = packet_ids(data486) if data486 else {}
ids503 = packet_ids(data503) if data503 else {}
ids527 = packet_ids(data527) if data527 else {}


# ---------- A2 補足：422 の 1 つ下（419）の protocolVersions.json での位置 ----------
if pver.is_file():
    check("protocolVersions.json の 422 は 1.16.201（1 エントリのみ）",
          [e["minecraftVersion"] for e in pv if e["version"] == 422] == ["1.16.201"])
    check("protocolVersions.json の 422 の 1 つ下は 100（419 のエントリは無い）",
          max(e["version"] for e in pv if e["version"] < 422) == 100
          and not any(e["version"] == 419 for e in pv))
    check("protocolVersions.json の 422 の 1 つ上は 428（1.16.210）",
          min(e["version"] for e in pv if e["version"] > 422) == 428)

# ---------- B. パケット一覧（422）の件数・境界 ----------
check("422 のパケット数が 160 件", len(ids422) == 160, f"{len(ids422)} 件")
check("422 の最大パケット ID が 163", max(ids422) == 163, f"最大 {max(ids422)}")
check("422 の型定義数が 233", len(types422) == 233, f"{len(types422)}")
check("428 のパケット数が 161 件", len(ids428) == 161, f"{len(ids428)} 件")
check("428 の最大パケット ID が 164", max(ids428) == 164, f"最大 {max(ids428)}")
check("428 の型定義数が 242", len(types428) == 242, f"{len(types428)}")
check("422 の ID の欠番が 0・16・37・116（428 と同じ）",
      [i for i in range(max(ids422) + 1) if i not in ids422] == [0, 16, 37, 116])
check("422 のパケット ID 0〜163 が 428 の同 ID と名称がすべて一致（164 を除く）",
      all(ids422[i] == ids428[i] for i in ids422),
      f"{sum(1 for i in ids422 if ids422[i] == ids428.get(i))} / {len(ids422)} 件一致")
check("422 のパケット ID 160〜163 が 428 と同一（player_fog・correct_player_move_prediction・item_component・filter_text_packet）",
      ids422.get(160) == "player_fog"
      and ids422.get(161) == "correct_player_move_prediction"
      and ids422.get(162) == "item_component"
      and ids422.get(163) == "filter_text_packet")

d422 = TMP / "packet-diff-422-vs-428.json"
check("422 vs 428 の差分 JSON が存在する", d422.is_file(), str(d422))
if d422.is_file():
    j422 = json.loads(d422.read_text())
    check("422 vs 428：追加 1 件（164 debug_renderer）",
          j422["added"] == {"164": "debug_renderer"}, json.dumps(j422["added"]))
    check("422 vs 428：削除 0・リネーム 0",
          j422["removed"] == {} and j422["renamed"] == {})
    check("422 vs 428：型変更 52 件",
          len(j422["changed_types"]) == 52, str(j422["changed_types"]))
    check("422 vs 428：型変更の内訳が期待通り",
          j422["changed_types"] == ["Action (only in B)",
                                     "ActionPermissions",
                                     "ContainerSlotType (only in B)",
                                     "InputFlag",
                                     "ItemStack",
                                     "ItemStackRequest (only in B)",
                                     "ItemStackRequests (only in A)",
                                     "ItemStackResponses",
                                     "MetadataDictionary",
                                     "MetadataFlags1 (only in B)",
                                     "MetadataFlags2 (only in B)",
                                     "Recipes",
                                     "Skin",
                                     "StackRequestSlotInfo",
                                     "Transaction",
                                     "TransactionActions (only in B)",
                                     "TransactionLegacy (only in B)",
                                     "TransactionUseItem (only in B)",
                                     "UpdateBlockFlags (only in B)",
                                     "WindowType",
                                     "mcpe_packet",
                                     "packet_add_entity",
                                     "packet_add_item_entity",
                                     "packet_add_painting",
                                     "packet_add_player",
                                     "packet_available_commands",
                                     "packet_camera_shake",
                                     "packet_command_output",
                                     "packet_debug_renderer (only in B)",
                                     "packet_entity_event",
                                     "packet_event",
                                     "packet_gui_data_pick_item",
                                     "packet_interact",
                                     "packet_inventory_content",
                                     "packet_item_stack_request",
                                     "packet_mob_armor_equipment",
                                     "packet_mob_effect",
                                     "packet_mob_equipment",
                                     "packet_move_entity",
                                     "packet_npc_request",
                                     "packet_player_action",
                                     "packet_player_armor_damage",
                                     "packet_player_auth_input",
                                     "packet_respawn",
                                     "packet_set_entity_data",
                                     "packet_set_entity_motion",
                                     "packet_set_title",
                                     "packet_show_credits",
                                     "packet_start_game",
                                     "packet_take_item_entity",
                                     "packet_update_block",
                                     "packet_update_block_synced"],
          json.dumps(j422["changed_types"]))

# ---------- C. 核パケット ID：BedrockProtocol.java と protocol.json の一致 ----------
proto_src = (SRC / "BedrockProtocol.java").read_text()
java_packets = dict(re.findall(
    r"PACKET_(\w+)\s*=\s*0x([0-9a-fA-F]+);", proto_src))
# (Java 定数名, protocol.json のパケット名)
CORE = [
    ("LOGIN", "login"), ("PLAY_STATUS", "play_status"),
    ("DISCONNECT", "disconnect"),
    ("RESOURCE_PACKS_INFO", "resource_packs_info"),
    ("RESOURCE_PACK_STACK", "resource_pack_stack"),
    ("RESOURCE_PACK_CLIENT_RESPONSE", "resource_pack_client_response"),
    ("TEXT", "text"), ("SET_TIME", "set_time"),
    ("START_GAME", "start_game"), ("ADD_PLAYER", "add_player"),
    ("ADD_ENTITY", "add_entity"), ("REMOVE_ENTITY", "remove_entity"),
    ("ADD_ITEM_ENTITY", "add_item_entity"),
    ("MOVE_ENTITY", "move_entity"), ("MOVE_PLAYER", "move_player"),
    ("UPDATE_BLOCK", "update_block"), ("ADD_PAINTING", "add_painting"),
    ("ENTITY_EVENT", "entity_event"), ("MOB_EFFECT", "mob_effect"),
    ("UPDATE_ATTRIBUTES", "update_attributes"),
    ("INVENTORY_TRANSACTION", "inventory_transaction"),
    ("MOB_EQUIPMENT", "mob_equipment"),
    ("MOB_ARMOR_EQUIPMENT", "mob_armor_equipment"),
    ("INTERACT", "interact"), ("PLAYER_ACTION", "player_action"),
    ("SET_ENTITY_DATA", "set_entity_data"),
    ("SET_ENTITY_MOTION", "set_entity_motion"),
    ("SET_ENTITY_LINK", "set_entity_link"),
    ("PLAYER_LIST", "player_list"),
    ("SPAWN_EXPERIENCE_ORB", "spawn_experience_orb"),
    ("MOVE_ENTITY_DELTA", "move_entity_delta"),
    ("LEVEL_CHUNK", "level_chunk"),
    ("REQUEST_CHUNK_RADIUS", "request_chunk_radius"),
    ("CHUNK_RADIUS_UPDATED", "chunk_radius_update"),
    ("COMMAND_REQUEST", "command_request"),
    ("NETWORK_CHUNK_PUBLISHER_UPDATE", "network_chunk_publisher_update"),
    ("CLIENT_CACHE_STATUS", "client_cache_status"),
    ("PLAYER_AUTH_INPUT", "player_auth_input"),
    ("UPDATE_SUB_CHUNK_BLOCKS", "update_subchunk_blocks"),
    ("SUB_CHUNK", "subchunk"), ("SUB_CHUNK_REQUEST", "subchunk_request"),
]
json_by_name = {v: k for k, v in ids422.items()}
# update_subchunk_blocks・subchunk・subchunk_request は 422 には存在しない
# （465・471 で新規追加）ためループから除き、不存在を別途確認する。
for const, pname in CORE:
    if const in ("UPDATE_SUB_CHUNK_BLOCKS", "SUB_CHUNK", "SUB_CHUNK_REQUEST"):
        continue
    java_id = int(java_packets[const], 16)
    json_id = json_by_name.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が protocol.json と一致",
          json_id == java_id, f"json={json_id}")
check("パケット ID 70 の名称が chunk_radius_update（Java 定数名 CHUNK_RADIUS_UPDATED とは名称違い・428 回と同じ）",
      ids422.get(70) == "chunk_radius_update")
check("パケット UPDATE_SUB_CHUNK_BLOCKS（0xac=172）が 422 の protocol.json に存在しない（465 で新規追加）",
      json_by_name.get("update_subchunk_blocks") is None)
check("パケット SUB_CHUNK（0xae=174）が 422 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk") is None)
check("パケット SUB_CHUNK_REQUEST（0xaf=175）が 422 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk_request") is None)
check("パケット SYNC_ENTITY_PROPERTY（165）が 422 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("sync_entity_property") is None)
check("パケット ADD_VOLUME_ENTITY（166）が 422 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("add_volume_entity") is None)
check("パケット REMOVE_VOLUME_ENTITY（167）が 422 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("remove_volume_entity") is None)
check("パケット SIMULATION_TYPE（168）が 422 の protocol.json に存在しない（448 で新規追加）",
      json_by_name.get("simulation_type") is None)
check("パケット NPC_DIALOGUE（169）が 422 の protocol.json に存在しない（448 で新規追加）",
      json_by_name.get("npc_dialogue") is None)
check("パケット DEBUG_RENDERER（164）が 422 の protocol.json に存在しない（428 で新規追加）",
      json_by_name.get("debug_renderer") is None
      and ids428.get(164) == "debug_renderer")

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_16_201 が存在する", "V1_16_201" in entry_by_name)
check("V1_16_201 のプロトコルが 422",
      entry_by_name.get("V1_16_201", (None,))[0] == 422,
      str(entry_by_name.get("V1_16_201")))
check("V1_16_201 の表示名が 1.16.201",
      entry_by_name.get("V1_16_201", (None, None))[1] == "1.16.201")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_16_201 が V1_16_210 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_16_201(422") < ver_src.index("V1_16_210(428"))
check("enum の javadoc に 1.16.201 の protocol.json が含まれる",
      "1.16.201" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 422 -> \"(1_16_210)\";", ver_src)
check("itemTableTag：422 が 1_16_210 の表を使う（1.16.200 自体の実測・他バージョンからの代用ではない）", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_16_210.bin"
check("アイテム表 bedrock_item_ids_1_16_210.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が a35eaa5a…と一致（1.16.200 候補表とバイト同一）",
      tag_bin.is_file()
      and sha1(tag_bin) == "a35eaa5a90525651aa5983808508c0e87124cc27",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規 0 本・428 回の表を共用）", len(item_bins) == 38, f"{len(item_bins)} 本")
check("1.16.201 名のアイテム表 .bin は無い（bedrock_item_ids_1_16_201.bin は作らない）",
      not (RES / "bedrock_item_ids_1_16_201.bin").exists())
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
check("item_tables_summary.json に 422 のエントリが無い（554・544・428 が表を共用するときと同じ流儀）",
      "422" not in summary)
s431 = summary.get("431", {})
check("item_tables_summary.json の 431 のエントリが残っている（tag=bedrock-1.16.210・exact=624／shared=236／legacy=33／fallback=419）",
      s431.get("tag") == "bedrock-1.16.210" and s431.get("exact") == 624
      and s431.get("shared") == 236 and s431.get("legacy") == 33
      and s431.get("fallback") == 419,
      str({k: s431.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))
check("item_tables_summary.json の 431 の missing_names が 419 件",
      len(s431.get("missing_names", [])) == 419,
      f"{len(s431.get('missing_names', []))} 件")
check("item_tables_summary.json の 440 のエントリが残っている（全テーブル再生成による消失なし）",
      "440" in summary and summary["440"].get("exact") == 730
      and summary["440"].get("fallback") == 313)
check("item_tables_summary.json の 448 のエントリが残っている（全テーブル再生成による消失なし）",
      "448" in summary and summary["448"].get("exact") == 747
      and summary["448"].get("fallback") == 296)
check("item_tables_summary.json の 465 のエントリが残っている（全テーブル再生成による消失なし）",
      "465" in summary and summary["465"].get("exact") == 751
      and summary["465"].get("fallback") == 292)
check("item_tables_summary.json の 471 のエントリが残っている（全テーブル再生成による消失なし）",
      "471" in summary and summary["471"].get("exact") == 751
      and summary["471"].get("fallback") == 292)
check("item_tables_summary.json の 475 のエントリが残っている（全テーブル再生成による消失なし）",
      "475" in summary and summary["475"].get("exact") == 752
      and summary["475"].get("fallback") == 291)

# ---------- F. 述語の前提：protocol.json 側の事実 ----------
# (1) StartGame：422 は 57 フィールド、428 は 59 フィールド
sg422 = field_names(types422, "packet_start_game")
sg428 = field_names(types428, "packet_start_game")
check("StartGame：422 は 57 フィールド", len(sg422) == 57, f"{len(sg422)} フィールド")
check("StartGame：428 は 59 フィールド", len(sg428) == 59, f"{len(sg428)} フィールド")
check("StartGame：422 のフィールド一覧が 428 から rewind_history_size・server_authoritative_block_breaking を除き spawn を player_position に置き換えたものと一致する（順序も同じ）",
      [f if f != "spawn" else "player_position" for f in sg422]
      == [f for f in sg428 if f not in ("rewind_history_size", "server_authoritative_block_breaking")])
check("StartGame：422 に rewind_history_size が無い（hasStartGameRewindFields()=false の前提）",
      "rewind_history_size" not in sg422)
check("StartGame：428 に rewind_history_size がある（hasStartGameRewindFields()=true の前提）",
      "rewind_history_size" in sg428)
check("StartGame：422 に server_authoritative_block_breaking が無い（hasStartGameRewindFields()=false の前提）",
      "server_authoritative_block_breaking" not in sg422)
check("StartGame：428 に server_authoritative_block_breaking がある",
      "server_authoritative_block_breaking" in sg428)
check("StartGame：422 の rewind_history_size の位置は movement_authority の直後（428 では 51 番目・current_tick の直前）",
      sg428.index("rewind_history_size") == 51
      and sg428.index("server_authoritative_block_breaking") == 52
      and sg428.index("current_tick") == 53
      and sg422.index("movement_authority") == 50
      and sg422.index("current_tick") == 51)
check("StartGame：422 の spawn は vec3f、428 の player_position は vec3f（改名だけ・ワイヤ同一）",
      field_type(types422, "packet_start_game", "spawn") == "vec3f"
      and field_type(types428, "packet_start_game", "player_position") == "vec3f")
check("StartGame：422 の movement_authority の値 2 は server_v2_rewind、428 は server_with_rewind（値は同じ zigzag32 の 2）",
      field_type(types422, "packet_start_game", "movement_authority")[1]["mappings"].get("2") == "server_v2_rewind"
      and field_type(types428, "packet_start_game", "movement_authority")[1]["mappings"].get("2") == "server_with_rewind"
      and field_type(types422, "packet_start_game", "movement_authority")[1]["mappings"].get("0") == "client"
      and field_type(types428, "packet_start_game", "movement_authority")[1]["mappings"].get("1") == "server")
check("StartGame：422・428 ともに runtime_entity_id は varint64（StartGame では型が変わらない）",
      field_type(types422, "packet_start_game", "runtime_entity_id") == "varint64"
      and field_type(types428, "packet_start_game", "runtime_entity_id") == "varint64")
check("StartGame：422 の block_palette は BlockPalette 型（428 と同じ）",
      field_type(types422, "packet_start_game", "block_palette") == "BlockPalette")
check("StartGame：BlockPalette の定義が 422 と 428 で JSON 完全一致",
      types422["BlockPalette"] == types428["BlockPalette"])
check("StartGame：422 の block_palette が varint 件数の {name: string, state: nbt} 配列",
      types422["BlockPalette"] == ["array", {"countType": "varint", "type": ["container", [{"name": "name", "type": "string"}, {"name": "state", "type": "nbt"}]]}])
check("StartGame：422 の末尾が server_authoritative_inventory（428 と同じ）",
      sg422[-1] == "server_authoritative_inventory"
      and sg428[-1] == "server_authoritative_inventory")
check("StartGame：422 に engine が無い（hasStartGameEngine()=false の前提）",
      "engine" not in sg422)
check("StartGame：422 に edu_resource_uri が無い（hasStartGameEduResourceUri()=false の前提）",
      "edu_resource_uri" not in sg422)
check("StartGame：422 に block_pallete_checksum が無い（hasStartGameBlockPaletteChecksum()=false の前提）",
      "block_pallete_checksum" not in sg422
      and "block_pallette_checksum" not in sg422)
check("StartGame：422 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types422, "packet_start_game", "seed") == "zigzag32")
check("StartGame：428 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types428, "packet_start_game", "seed") == "zigzag32")
check("StartGame：422 の dimension は zigzag32",
      field_type(types422, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：422 に property_data が無い（hasStartGame527Fields()=false の前提）",
      "property_data" not in sg422)
check("StartGame：422 に world_template_id が無い（hasStartGame527Fields()=false の前提）",
      "world_template_id" not in sg422)
check("StartGame：422 に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg422)
check("StartGame：422 に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg422 for t in ["server_identifier", "world_identifier",
                                   "scenario_identifier", "owner_identifier"]))
check("StartGame：422 に editor_world が無い（hasEditorWorld=false）",
      "editor_world" not in sg422)
check("StartGame：422 の permission_level は zigzag32（hasPermissionLevelMapper=false）",
      field_type(types422, "packet_start_game", "permission_level") == "zigzag32")
check("StartGame：422 の movement_authority が存在する（hasMovementAuthority=true）",
      "movement_authority" in sg422)
check("StartGame：422 の itemstates が存在する（hasItemstates=true）",
      "itemstates" in sg422)
check("StartGame：422 に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg422)
check("StartGame：422 に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg422 and "server_join_info" not in sg422)
check("StartGame：422 に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg422)
check("StartGame：422 に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg422)
check("StartGame：422 に created_in_editor・exported_from_editor・block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(f in sg422 for f in ["created_in_editor", "exported_from_editor",
                                   "block_network_ids_are_hashes"]))
check("StartGame：422 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg422)
check("StartGame：422 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg422)
check("StartGame：422 に persona_disabled・custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg422 and "custom_skins_disabled" not in sg422)
check("StartGame：422 に chat_restriction_level・disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg422
      and "disable_player_interactions" not in sg422)
check("StartGame：422 に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg422)

# (2) Resource packs info / stack：422 は 4 フィールド、428 も 4 フィールドで JSON 完全一致
rpi = field_names(types422, "packet_resource_packs_info")
rpi428 = field_names(types428, "packet_resource_packs_info")
check("Resource packs info：422 は 4 フィールド（must_accept・has_scripts・behaviour_packs・texture_packs）",
      rpi == ["must_accept", "has_scripts", "behaviour_packs", "texture_packs"])
check("Resource packs info：428 は 4 フィールド（422 と JSON 完全一致・force_server_packs は 448 で新規）",
      rpi428 == ["must_accept", "has_scripts", "behaviour_packs", "texture_packs"]
      and types422["packet_resource_packs_info"] == types428["packet_resource_packs_info"])
check("Resource packs info：422 に force_server_packs が無い（hasForceServerPacks()=false の前提）",
      "force_server_packs" not in rpi)
check("Resource packs info：422 に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("Resource packs info：422 に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("Resource packs info：422 に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("Resource packs info：422 に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
check("TexturePackInfos の countType が li16（428 と同じ）",
      types422["TexturePackInfos"][1].get("countType") == "li16"
      and types428["TexturePackInfos"][1].get("countType") == "li16")
rps = field_names(types422, "packet_resource_pack_stack")
rps428 = field_names(types428, "packet_resource_pack_stack")
check("Resource pack stack：422 は 428 と同じ 6 フィールド（behavior_packs を含む）",
      rps == rps428 == ["must_accept", "behavior_packs", "resource_packs",
                        "game_version", "experiments",
                        "experiments_previously_used"])
check("Resource pack stack：422 に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

# (3) エンティティ・アイテム
check("Mob equipment の item が Item（ItemV4 ではない）",
      field_type(types422, "packet_mob_equipment", "item") == "Item")
check("Add item entity の item が Item",
      field_type(types422, "packet_add_item_entity", "item") == "Item")
mae = [n for n, _ in container_fields(types422, "packet_mob_armor_equipment")]
check("Mob armor equipment が helmet・chestplate・leggings・boots の 4 つ（body は無い）",
      mae == ["runtime_entity_id", "helmet", "chestplate", "leggings", "boots"])
check("ItemV4 型が 422 の protocol.json に存在しない", "ItemV4" not in types422)
check("ItemV4 型が 428 の protocol.json に存在しない", "ItemV4" not in types428)
ap422 = field_names(types422, "packet_add_player")
ap428 = field_names(types428, "packet_add_player")
check("Add player：422 は 25 フィールド（428 と同じ件数）",
      len(ap422) == 25, f"{len(ap422)} フィールド")
check("Add player：428 は 25 フィールド",
      len(ap428) == 25, f"{len(ap428)} フィールド")
check("Add player：422 と 428 のフィールド一覧が同一（uuid・username・entity_id_self・runtime_entity_id・platform_chat_id・x・y・z・speed_x・speed_y・speed_z・pitch・yaw・head_yaw・held_item・metadata・flags・command_permission・action_permissions・permission_level・custom_stored_permissions・user_id・links・device_id・device_os）",
      ap422 == ap428 == ["uuid", "username", "entity_id_self", "runtime_entity_id",
                         "platform_chat_id", "x", "y", "z", "speed_x", "speed_y",
                         "speed_z", "pitch", "yaw", "head_yaw", "held_item",
                         "metadata", "flags", "command_permission",
                         "action_permissions", "permission_level",
                         "custom_stored_permissions", "user_id", "links", "device_id",
                         "device_os"])
check("Add player：422 の runtime_entity_id は varint、428 は varint64（どちらも符号なし LEB128 で値が小さい限りバイト同一のため分岐不要）",
      field_type(types422, "packet_add_player", "runtime_entity_id") == "varint"
      and field_type(types428, "packet_add_player", "runtime_entity_id") == "varint64")
check("Add player：422 の x・y・z・speed_x・speed_y・speed_z は lf32（428 と同じ）",
      all(field_type(types422, "packet_add_player", f) == "lf32"
          for f in ["x", "y", "z", "speed_x", "speed_y", "speed_z"])
      and all(field_type(types428, "packet_add_player", f) == "lf32"
              for f in ["x", "y", "z", "speed_x", "speed_y", "speed_z"]))
check("Add player：422 の device_os は li32",
      field_type(types422, "packet_add_player", "device_os") == "li32")
check("Add player：422・428 の entity_id_self は zigzag64（名称も型も同じ）",
      field_type(types422, "packet_add_player", "entity_id_self") == "zigzag64"
      and field_type(types428, "packet_add_player", "entity_id_self") == "zigzag64")
check("Add player：422 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap422)
check("Add player：428 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap428)
check("Add player：422・428 ともに abilities 配列が無い（hasAddPlayerAbilitiesArray()=false の前提・能力系を直書きする旧形式）",
      "abilities" not in ap422 and "abilities" not in ap428)
ae422 = field_names(types422, "packet_add_entity")
ae428 = field_names(types428, "packet_add_entity")
check("Add entity：422 は 15 フィールド（x／y／z・speed_x／speed_y／speed_z を lf32 で直書き・428 と同じ一覧）",
      ae422 == ["entity_id_self", "runtime_entity_id", "entity_type",
                "x", "y", "z", "speed_x", "speed_y", "speed_z",
                "pitch", "yaw", "head_yaw", "attributes", "metadata",
                "links"], f"{len(ae422)} フィールド")
check("Add entity：428 は 15 フィールド（422 と同じ一覧）",
      ae428 == ae422)
check("Add entity：422 の runtime_entity_id は varint、428 は varint64",
      field_type(types422, "packet_add_entity", "runtime_entity_id") == "varint"
      and field_type(types428, "packet_add_entity", "runtime_entity_id") == "varint64")
check("Add entity：422・428 ともに body_yaw が無い（hasAddEntityBodyYaw()=false の前提）",
      "body_yaw" not in ae422 and "body_yaw" not in ae428)
check("Add item entity：422 は 11 フィールド（428 と同じ一覧）",
      field_names(types422, "packet_add_item_entity") == ["entity_id_self",
                                                         "runtime_entity_id",
                                                         "item", "x", "y", "z",
                                                         "speed_x", "speed_y",
                                                         "speed_z", "metadata",
                                                         "is_from_fishing"]
      and field_names(types428, "packet_add_item_entity") == field_names(types422, "packet_add_item_entity"))
check("Add item entity：422 の runtime_entity_id は varint、428 は varint64",
      field_type(types422, "packet_add_item_entity", "runtime_entity_id") == "varint"
      and field_type(types428, "packet_add_item_entity", "runtime_entity_id") == "varint64")
check("Set entity data は 422 と 428 で同じ（3 フィールド）",
      field_names(types422, "packet_set_entity_data") == field_names(types428, "packet_set_entity_data")
      and len(field_names(types422, "packet_set_entity_data")) == 3)
check("Set entity data：422 の runtime_entity_id は varint、428 は varint64",
      field_type(types422, "packet_set_entity_data", "runtime_entity_id") == "varint"
      and field_type(types428, "packet_set_entity_data", "runtime_entity_id") == "varint64")

# (4) AddPainting・PlayerList（422 も 428 と同じ分岐で扱える）
check("Add painting：422・428 ともに 5 フィールド（entity_id_self・runtime_entity_id・coordinates・direction・title）",
      field_names(types422, "packet_add_painting") == field_names(types428, "packet_add_painting")
      == ["entity_id_self", "runtime_entity_id", "coordinates", "direction",
          "title"])
check("Add painting：422 の coordinates は BlockCoordinates（addPaintingUsesBlockCoordinates()=true の前提）",
      field_type(types422, "packet_add_painting", "coordinates") == "BlockCoordinates")
check("Add painting：428 の coordinates は BlockCoordinates（addPaintingUsesBlockCoordinates()=true の前提）",
      field_type(types428, "packet_add_painting", "coordinates") == "BlockCoordinates")
check("Add painting：422 の runtime_entity_id は varint、428 は varint64",
      field_type(types422, "packet_add_painting", "runtime_entity_id") == "varint"
      and field_type(types428, "packet_add_painting", "runtime_entity_id") == "varint64")
check("BlockCoordinates は 422 に存在し x zigzag32・y varint・z zigzag32",
      types422["BlockCoordinates"] == ["container", [{"name": "x", "type": "zigzag32"}, {"name": "y", "type": "varint"}, {"name": "z", "type": "zigzag32"}]])
pr422 = types422["PlayerRecords"]
pr428 = types428["PlayerRecords"]
check("PlayerRecords：422・428 ともに 4 フィールド（type・records_count・records・verified）で JSON 完全一致",
      [f.get("name") for f in pr422[1]] == [f.get("name") for f in pr428[1]]
      == ["type", "records_count", "records", "verified"]
      and pr422 == pr428)
check("PlayerRecords：422 の verified は records_count 個の bool 配列（remove でも存在する。playerListRemoveHasVerified()=true の前提）",
      [f for f in pr422[1] if f.get("name") == "verified"][0]["type"] == ["array", {"count": "records_count", "type": "bool"}])

# (5) Skin・Item（判断待ちで直さないもの）
skin422 = [f["name"] for f in types422["Skin"][1]]
skin428 = [f["name"] for f in types428["Skin"][1]]
check("Skin：422 は 16 フィールド（428 は 17 フィールドで play_fab_id を追加）",
      len(skin422) == 16 and len(skin428) == 17, f"422={len(skin422)} 428={len(skin428)}")
check("Skin：422 のフィールド一覧（play_fab_id が無い）",
      skin422 == ["skin_id", "skin_resource_pack", "skin_data", "animations",
                  "cape_data", "geometry_data", "animation_data", "premium",
                  "persona", "cape_on_classic", "cape_id", "full_skin_id",
                  "arm_size", "skin_color", "personal_pieces",
                  "piece_tint_colors"])
check("Skin：428 は skin_id の直後に play_fab_id を追加しただけで、それ以外は 422 と名称・順序とも同一",
      skin428[:1] == ["skin_id"] and skin428[1] == "play_fab_id"
      and skin428[2:] == skin422[1:])
check("Skin：422 の premium は string（428 も string。writeEmptySkin は bool で書くため判断待ち）",
      field_type(types422, "Skin", "premium") == "string"
      and field_type(types428, "Skin", "premium") == "string")
check("Skin：422 の premium は index 7（428 は index 8）",
      skin422.index("premium") == 7 and skin428.index("premium") == 8)
check("Skin：422・428 ともに geometry_data_version・primary_user・overriding_player_appearance が無い（writeEmptySkin は 567 形式で書くため判断待ち）",
      "geometry_data_version" not in skin422 and "primary_user" not in skin422
      and "overriding_player_appearance" not in skin422
      and "geometry_data_version" not in skin428)
check("SkinImage：422・428 ともに data は string で JSON 完全一致",
      field_type(types422, "SkinImage", "data") == "string"
      and types422["SkinImage"] == types428["SkinImage"])
check("SkinImage：422・428 ともに width・height は li32",
      field_type(types422, "SkinImage", "width") == "li32"
      and field_type(types428, "SkinImage", "width") == "li32")
item422 = types422["Item"]
check("Item：422 に count フィールドが無い（minecraft-data の 422 の Item 定義。判断待ち）",
      find_field(item422, "count") is None)
check("Item：422 と 428 で JSON 完全一致（count 無しは 422 でも同じ）",
      types422["Item"] == types428["Item"])
check("Item：422 は network_id・auxiliary_value・has_nbt（lu16）・nbt・can_place_on・can_destroy・blocking_tick（network_id 355 のとき zigzag64）",
      field_names(types422, "Item") == ["network_id"]
      and item422[1][1]["type"][1]["default"][1][0]["name"] == "auxiliary_value"
      and item422[1][2]["type"][1]["fields"]["355"] == ["container", [{"name": "blocking_tick", "type": "zigzag64"}]])

# (6) トランザクション・アイテム関連
check("Transaction：422 はインラインの 6 フィールド（legacy_request_id・legacy_transactions・transaction_type・network_ids・inventory_actions・transaction_data）",
      field_names(types422, "Transaction") == ["legacy_request_id",
                                               "legacy_transactions",
                                               "transaction_type",
                                               "network_ids",
                                               "inventory_actions",
                                               "transaction_data"])
check("Transaction：428 は 4 フィールド（legacy・transaction_type・actions・transaction_data）で旧部分を 3 型に切り出し",
      field_names(types428, "Transaction") == ["legacy", "transaction_type",
                                               "actions", "transaction_data"])
check("Transaction：422 に TransactionLegacy 型が無い（428 にはある）",
      "TransactionLegacy" not in types422 and "TransactionLegacy" in types428)
check("Transaction：422 に TransactionActions 型が無い（428 にはある）",
      "TransactionActions" not in types422 and "TransactionActions" in types428)
check("Transaction：422 に TransactionUseItem 型が無い（428 にはある）",
      "TransactionUseItem" not in types422 and "TransactionUseItem" in types428)
check("Transaction：422 の network_ids は Transaction の直下の bool（428 は TransactionActions の中の bool）",
      field_type(types422, "Transaction", "network_ids") == "bool"
      and [f for f in types428["TransactionActions"][1] if f.get("name") == "network_ids"][0]["type"] == "bool")
check("Transaction：422・428 ともに transaction_type が 0=normal・1=inventory_mismatch・2=item_use・3=item_use_on_entity・4=item_release",
      field_type(types422, "Transaction", "transaction_type")[1]["mappings"] == field_type(types428, "Transaction", "transaction_type")[1]["mappings"] == {"0": "normal", "1": "inventory_mismatch", "2": "item_use", "3": "item_use_on_entity", "4": "item_release"})
check("ItemStack：422 は runtime_id（zigzag32）＋item（Item）、428 は stack_id（varint）＋item（Item）",
      types422["ItemStack"] == ["container", [{"name": "runtime_id", "type": "zigzag32"}, {"name": "item", "type": "Item"}]]
      and types428["ItemStack"] == ["container", [{"name": "stack_id", "type": "varint"}, {"name": "item", "type": "Item"}]])
check("ItemStackRequests：422 にのみある型（varint 件数の配列）",
      "ItemStackRequests" in types422 and "ItemStackRequests" not in types428)
check("ItemStackRequest：428 にのみある型（422 には無い）",
      "ItemStackRequest" in types428 and "ItemStackRequest" not in types422)
check("packet_item_stack_request：422 は requests が ItemStackRequests、428 は ItemStackRequest の配列",
      field_type(types422, "packet_item_stack_request", "requests") == "ItemStackRequests"
      and field_type(types428, "packet_item_stack_request", "requests") == ["array", {"countType": "varint", "type": "ItemStackRequest"}])
check("ItemStackResponses：422 は result（u8）＋containers 直書き、428 は status マッパーによる switch＋durability_correction",
      find_field(types422["ItemStackResponses"], "result") == "u8"
      and find_field(types428["ItemStackResponses"], "status") is not None
      and find_field(types428["ItemStackResponses"], "durability_correction") == "zigzag32")
check("ItemStackResponses：422・428 ともに request_id は varint",
      find_field(types422["ItemStackResponses"], "request_id") == "varint"
      and find_field(types428["ItemStackResponses"], "request_id") == "varint")
check("StackRequestSlotInfo：422 は container_id（u8）・slot_id（u8）・stack_id、428 は slot_type（ContainerSlotType）・slot・stack_id",
      types422["StackRequestSlotInfo"] == ["container", [{"name": "container_id", "type": "u8"}, {"name": "slot_id", "type": "u8"}, {"name": "stack_id", "type": "zigzag32"}]]
      and types428["StackRequestSlotInfo"] == ["container", [{"name": "slot_type", "type": "ContainerSlotType"}, {"name": "slot", "type": "u8"}, {"name": "stack_id", "type": "zigzag32"}]])
check("ContainerSlotType：428 にのみある型（422 には無い）",
      "ContainerSlotType" not in types422 and "ContainerSlotType" in types428)
check("WindowType：422 は u8 マッパー 0〜33、428 は i8 マッパーで -9 none・-1 inventory を追加（0〜33 の名称は同じ）",
      types422["WindowType"][1]["type"] == "u8"
      and types428["WindowType"][1]["type"] == "i8"
      and types428["WindowType"][1]["mappings"].get("-9") == "none"
      and types428["WindowType"][1]["mappings"].get("-1") == "inventory"
      and all(types422["WindowType"][1]["mappings"].get(str(i)) == types428["WindowType"][1]["mappings"].get(str(i)) for i in range(34)))
check("Recipes：422・428 ともに type マッパーは同じ 0〜7（shapeless・shaped・furnace・furnace_with_metadata・multi・shulker_box・shapeless_chemistry・shaped_chemistry）",
      types422["Recipes"][1]["type"][1][0]["type"][1]["mappings"] == types428["Recipes"][1]["type"][1][0]["type"][1]["mappings"]
      == {"0": "shapeless", "1": "shaped", "2": "furnace", "3": "furnace_with_metadata", "4": "multi", "5": "shulker_box", "6": "shapeless_chemistry", "7": "shaped_chemistry"})

# (7) 核パケットの 422 vs 428：130 件が JSON 同一
MAJOR = ["packet_level_chunk", "packet_move_player", "packet_text",
         "packet_crafting_data", "packet_level_event",
         "packet_resource_packs_info", "packet_resource_pack_stack",
         "packet_player_list", "packet_login", "packet_disconnect",
         "packet_play_status", "packet_animate",
         "packet_request_chunk_radius", "packet_inventory_transaction",
         "packet_item_stack_response"]
for name in MAJOR:
    check(f"核パケット {name} が 422 と 428 で JSON 同一（分岐不要）",
          types422[name] == types428[name])
check("核パケット packet_move_entity は 422 と 428 で違う（runtime_entity_id の varint／varint64 だけ）",
      types422["packet_move_entity"] != types428["packet_move_entity"])
check("核パケット packet_add_player・packet_add_entity・packet_add_item_entity は 422 と 428 で違う（runtime_entity_id の varint／varint64 だけ）",
      types422["packet_add_player"] != types428["packet_add_player"]
      and types422["packet_add_entity"] != types428["packet_add_entity"]
      and types422["packet_add_item_entity"] != types428["packet_add_item_entity"])
check("核パケット packet_start_game は 422 と 428 で違う（rewind 2 フィールド＋spawn／player_position の改名）",
      types422["packet_start_game"] != types428["packet_start_game"])
check("packet_* 型のうち 422 の 160 件中 130 件が 428 と JSON 同一（差分 JSON の内訳と整合）",
      sum(1 for k in types422 if k.startswith("packet_") and types422[k] == types428.get(k)) == 130,
      f"{sum(1 for k in types422 if k.startswith('packet_') and types422[k] == types428.get(k))} 件")

# ---------- G. 422／428 の境界値 ----------
check("SoundType 型が 422 の protocol.json に存在しない（428 にも無い。431 で新規追加）",
      "SoundType" not in types422 and "SoundType" not in types428)
check("packet_debug_renderer：428 は type が li32 マッパー（1 clear／2 add_cube）で switch 付き",
      types428["packet_debug_renderer"][1][0]["type"][1]["mappings"] == {"1": "clear", "2": "add_cube"})
check("packet_debug_renderer：422 には存在しない（428 で新規追加）",
      "packet_debug_renderer" not in types422)
le422 = types422["packet_level_event"]
le428 = types428["packet_level_event"]
le_map = le422[1][0]["type"][1]["mappings"]
le_map428 = le428[1][0]["type"][1]["mappings"]
check("Level event：422 は 61 エントリ（最大キー 16384）",
      len(le_map) == 61, f"{len(le_map)}")
check("Level event：428 は 61 エントリで 422 と JSON 完全一致",
      len(le_map428) == 61 and le422 == le428)
check("Level event：422 の 2000 が particle_shoot・2001 が particle_destroy・2002 が particle_splash・2003 が particle_eye_despawn（428 と同じ）",
      le_map.get("2000") == "particle_shoot"
      and le_map.get("2001") == "particle_destroy"
      and le_map.get("2002") == "particle_splash"
      and le_map.get("2003") == "particle_eye_despawn")
check("Level event：422 の 9800 が players_sleeping・9801 は無い（最大キーは 16384 add_particle_mask）",
      le_map.get("9800") == "players_sleeping"
      and le_map.get("9801") is None
      and max(int(k) for k in le_map) == 16384
      and le_map.get("16384") == "add_particle_mask")
check("Entity event：422・428 ともに event_id マッパーが 54 エントリで同一（runtime_entity_id の型だけ違う）",
      len(field_type(types422, "packet_entity_event", "event_id")[1]["mappings"]) == 54
      and field_type(types422, "packet_entity_event", "event_id")[1]["mappings"]
      == field_type(types428, "packet_entity_event", "event_id")[1]["mappings"])
check("Entity event：422・428 ともに 1=jump・2=hurt_animation・3=death_animation・4=arm_swing",
      field_type(types422, "packet_entity_event", "event_id")[1]["mappings"].get("1") == "jump"
      and field_type(types422, "packet_entity_event", "event_id")[1]["mappings"].get("2") == "hurt_animation"
      and field_type(types422, "packet_entity_event", "event_id")[1]["mappings"].get("3") == "death_animation"
      and field_type(types422, "packet_entity_event", "event_id")[1]["mappings"].get("4") == "arm_swing")
check("GameMode：422・428 ともに 6 エントリ（0〜5・5=fallback）で JSON 同一",
      len(types422["GameMode"][1]["mappings"]) == 6
      and len(types428["GameMode"][1]["mappings"]) == 6
      and types422["GameMode"][1]["mappings"].get("5") == "fallback"
      and types422["GameMode"] == types428["GameMode"])
md422 = types422["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]
md428 = types428["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]
check("MetadataDictionary：422 は 118 エントリ・428 は 121 エントリ",
      len(md422) == 118 and len(md428) == 121,
      f"422={len(md422)} 428={len(md428)}")
check("MetadataDictionary：422 と 428 で JSON は違う（キー 0 の名称・キー 19 の有無・60 以降のずれ）",
      types422["MetadataDictionary"] != types428["MetadataDictionary"])
check("MetadataDictionary：422 のキー 0 は index、428 は flags（0〜18・22〜51・53〜59 は両方同一）",
      md422.get("0") == "index"
      and md428.get("0") == "flags"
      and md422.get("1") == "health"
      and md422.get("2") == "variant"
      and md422.get("3") == "color"
      and md422.get("4") == "nametag"
      and all(md422.get(str(k)) == md428.get(str(k)) for k in list(range(1, 19)) + list(range(22, 52)) + list(range(53, 60))))
check("MetadataDictionary：422 にキー 19 が無い（428 は horse_type）・キー 25 も欠番",
      "19" not in md422 and "25" not in md422
      and md428.get("19") == "horse_type")
check("MetadataDictionary：422 は 20=old_swell・21=swell_dir、428 は 20=creeper_swell・21=creeper_swell_direction（改名だけ）",
      md422.get("20") == "old_swell"
      and md422.get("21") == "swell_dir"
      and md428.get("20") == "creeper_swell"
      and md428.get("21") == "creeper_swell_direction")
check("MetadataDictionary：422 は 52=aerial_attack、428 は 52=wither_aerial_attack（改名だけ）",
      md422.get("52") == "aerial_attack"
      and md428.get("52") == "wither_aerial_attack")
check("MetadataDictionary：422 の 60 以降は 428 より 1 つ小さい（428 は 60 に rider_seat_rotation_offset を挿入）",
      md422.get("60") == "area_effect_cloud_radius"
      and md428.get("60") == "rider_seat_rotation_offset"
      and md428.get("61") == "area_effect_cloud_radius"
      and md422.get("61") == "area_effect_cloud_waiting")
check("MetadataDictionary：422 は 118=is_buoyant・119=buoyancy_data が末尾（goat_horn_count が無い）",
      md422.get("118") == "is_buoyant"
      and md422.get("119") == "buoyancy_data"
      and "goat_horn_count" not in md422.values())
check("MetadataDictionary：428 は 119=is_buoyant・120=buoyancy_data・121=goat_horn_count",
      md428.get("119") == "is_buoyant"
      and md428.get("120") == "buoyancy_data"
      and md428.get("121") == "goat_horn_count")
check("MetadataDictionary：goat_horn_count の key は 422 には無く 428 が 121・431 が 122・440 が 123（コードは 440 以降のみ key 123 を書く）",
      md428.get("121") == "goat_horn_count"
      and types431["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("122") == "goat_horn_count"
      and types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("123") == "goat_horn_count")
check("MetadataFlags1・MetadataFlags2：422 には無く、428 にのみある型",
      "MetadataFlags1" not in types422 and "MetadataFlags2" not in types422
      and "MetadataFlags1" in types428 and "MetadataFlags2" in types428)
check("MetadataFlags2：428 は 31 フラグ、MetadataFlags1：428 は 64 フラグ",
      len(types428["MetadataFlags2"][1]["flags"]) == 31
      and len(types428["MetadataFlags1"][1]["flags"]) == 64)
check("Subchunk：422 に packet_subchunk 型が存在しない（471 で新規追加）",
      "packet_subchunk" not in types422)
check("Subchunk request：422 に packet_subchunk_request 型が存在しない（471 で新規追加）",
      "packet_subchunk_request" not in types422)
check("CommandFlags：422 は 3 フラグ（unused・has_semantic_constraint・collapse_enum）",
      [f["name"] for f in types422["CommandFlags"][1]] == ["unused",
                                                           "has_semantic_constraint",
                                                           "collapse_enum"])
check("CommandFlags：428 は 3 フラグ（422 と同じ）",
      [f["name"] for f in types428["CommandFlags"][1]] == ["unused",
                                                           "has_semantic_constraint",
                                                           "collapse_enum"])
check("MaterialReducer 型が 422 の protocol.json に存在しない（465 で新規追加）",
      "MaterialReducer" not in types422)
check("DeviceOS 型が 422 の protocol.json に存在しない", "DeviceOS" not in types422)
check("EntityProperties 型が 422 の protocol.json に存在しない", "EntityProperties" not in types422)
check("GameRule：422 は name・type・value の 3 フィールドで 428 と JSON 同一",
      field_names(types422, "GameRule") == ["name", "type", "value"]
      and types422["GameRule"] == types428["GameRule"])
pe422 = types422["packet_event"][1][1]["type"][1]["mappings"]
pe428 = types428["packet_event"][1][1]["type"][1]["mappings"]
check("packet_event：422 は 18 イベント・428 は 25 イベント",
      len(pe422) == 18 and len(pe428) == 25,
      f"422={len(pe422)} 428={len(pe428)}")
check("packet_event：422 の 0〜17 は 428 と同一（428 での追加は 18 actor_definition・19 raid_update・20 player_movement_anomaly・21 player_movement_corrected・22 honey_harvested・23 target_block_hit・24 piglin_barter）",
      all(pe422.get(str(k)) == pe428.get(str(k)) for k in range(18))
      and pe428.get("18") == "actor_definition"
      and pe428.get("24") == "piglin_barter")
check("InputFlag：422 は 25 フラグ・基底 varint、428 は 37 フラグ・基底 varint64（big）",
      len(types422["InputFlag"][1]["flags"]) == 25
      and types422["InputFlag"][1]["type"] == "varint"
      and len(types428["InputFlag"][1]["flags"]) == 37
      and types428["InputFlag"][1]["type"] == "varint64"
      and types428["InputFlag"][1].get("big") is True)
check("InputFlag：422 に無い 12 フラグは start_sprinting・stop_sprinting・start_sneaking・stop_sneaking・start_swimming・stop_swimming・start_jumping・start_gliding・stop_gliding・item_interact・block_action・item_stack_request（428 にはある）",
      all(f not in types422["InputFlag"][1]["flags"]
          for f in ["start_sprinting", "stop_sprinting", "start_sneaking",
                    "stop_sneaking", "start_swimming", "stop_swimming",
                    "start_jumping", "start_gliding", "stop_gliding",
                    "item_interact", "block_action", "item_stack_request"])
      and all(f in types428["InputFlag"][1]["flags"]
              for f in ["start_sprinting", "stop_sprinting", "start_sneaking",
                        "stop_sneaking", "start_swimming", "stop_swimming",
                        "start_jumping", "start_gliding", "stop_gliding",
                        "item_interact", "block_action", "item_stack_request"]))
check("packet_player_auth_input：422 は 11 フィールド、428 は 14 フィールド（428 は末尾に transaction・item_stack_request・block_action を追加）",
      field_names(types422, "packet_player_auth_input") == ["pitch", "yaw",
                                                           "position", "move_vector",
                                                           "head_yaw", "input_data",
                                                           "input_mode", "play_mode",
                                                           "gaze_direction", "tick",
                                                           "delta"]
      and field_names(types428, "packet_player_auth_input") == field_names(types422, "packet_player_auth_input") + ["transaction", "item_stack_request", "block_action"])
check("packet_player_auth_input：先頭 3 フィールド（pitch・yaw・position）は 422 と 428 で同一（decodePlayerAuthInput が読むのはここだけ）",
      container_fields(types422, "packet_player_auth_input")[:3]
      == container_fields(types428, "packet_player_auth_input")[:3])
check("packet_player_auth_input：422 の input_mode は 4 件（0 mouse／1 touch／2 game_pad／3 motion_controller）、428 は 5 件（0 unknown を追加）",
      field_type(types422, "packet_player_auth_input", "input_mode")[1]["mappings"] == {"0": "mouse", "1": "touch", "2": "game_pad", "3": "motion_controller"}
      and field_type(types428, "packet_player_auth_input", "input_mode")[1]["mappings"] == {"0": "unknown", "1": "mouse", "2": "touch", "3": "game_pad", "4": "motion_controller"})
check("packet_gui_data_pick_item：422 は空の container（0 フィールド）、428 は 3 フィールド（item_name・item_effects・hotbar_slot）",
      types422["packet_gui_data_pick_item"] == ["container", []]
      and field_names(types428, "packet_gui_data_pick_item") == ["item_name", "item_effects", "hotbar_slot"])
check("packet_update_block：422 は coordinates・block_runtime_id・block_priority（varint）・storage、428 は position・block_runtime_id・flags（UpdateBlockFlags）・layer",
      field_names(types422, "packet_update_block") == ["coordinates", "block_runtime_id", "block_priority", "storage"]
      and field_names(types428, "packet_update_block") == ["position", "block_runtime_id", "flags", "layer"]
      and field_type(types422, "packet_update_block", "block_priority") == "varint"
      and field_type(types428, "packet_update_block", "flags") == "UpdateBlockFlags")
check("UpdateBlockFlags：428 にのみある型（422 には無い）",
      "UpdateBlockFlags" not in types422 and "UpdateBlockFlags" in types428)
check("packet_update_block_synced：422 は coordinates・block_runtime_id・block_priority・data_layer_id・unknown0・unknown1、428 は position・block_runtime_id・flags・layer・entity_unique_id・transition_type",
      field_names(types422, "packet_update_block_synced") == ["coordinates", "block_runtime_id", "block_priority", "data_layer_id", "unknown0", "unknown1"]
      and field_names(types428, "packet_update_block_synced") == ["position", "block_runtime_id", "flags", "layer", "entity_unique_id", "transition_type"])
check("packet_available_commands：422 は file_path=14・string=29・position=37・message=41・raw_text=43・json=46・command=53、428 は file_path=16・string=32・position=40・message=44・raw_text=46・json=50・command=63",
      find_mapper_with(types422["packet_available_commands"], "file_path").get("14") == "file_path"
      and find_mapper_with(types422["packet_available_commands"], "file_path").get("29") == "string"
      and find_mapper_with(types422["packet_available_commands"], "file_path").get("37") == "position"
      and find_mapper_with(types422["packet_available_commands"], "file_path").get("41") == "message"
      and find_mapper_with(types422["packet_available_commands"], "file_path").get("43") == "raw_text"
      and find_mapper_with(types422["packet_available_commands"], "file_path").get("46") == "json"
      and find_mapper_with(types422["packet_available_commands"], "file_path").get("53") == "command"
      and find_mapper_with(types428["packet_available_commands"], "file_path").get("16") == "file_path"
      and find_mapper_with(types428["packet_available_commands"], "file_path").get("32") == "string"
      and find_mapper_with(types428["packet_available_commands"], "file_path").get("40") == "position"
      and find_mapper_with(types428["packet_available_commands"], "file_path").get("44") == "message"
      and find_mapper_with(types428["packet_available_commands"], "file_path").get("46") == "raw_text"
      and find_mapper_with(types428["packet_available_commands"], "file_path").get("50") == "json"
      and find_mapper_with(types428["packet_available_commands"], "file_path").get("63") == "command")
check("packet_player_action：422 は action がインラインの zigzag32 マッパー 26 件（0〜25）、428 は Action 型への参照で 28 件（0〜27）",
      len(field_type(types422, "packet_player_action", "action")[1]["mappings"]) == 26
      and len(types428["Action"][1]["mappings"]) == 28
      and field_type(types428, "packet_player_action", "action") == "Action")
check("packet_player_action：422 の 18 は continue_break・25 は ineract_block（typo）、428 は 18 crack_break・26 predict_break・27 continue_break",
      field_type(types422, "packet_player_action", "action")[1]["mappings"].get("18") == "continue_break"
      and field_type(types422, "packet_player_action", "action")[1]["mappings"].get("25") == "ineract_block"
      and types428["Action"][1]["mappings"].get("18") == "crack_break"
      and types428["Action"][1]["mappings"].get("26") == "predict_break"
      and types428["Action"][1]["mappings"].get("27") == "continue_break")
check("packet_interact：422 は target_runtime_entity_id（varint）、428 は target_entity_id（varint64）",
      field_type(types422, "packet_interact", "target_runtime_entity_id") == "varint"
      and field_type(types428, "packet_interact", "target_entity_id") == "varint64")
check("packet_camera_shake：422 は 3 フィールド、428 は action マッパー（0 add／1 stop）を追加して 4 フィールド",
      field_names(types422, "packet_camera_shake") == ["intensity", "duration", "type"]
      and field_names(types428, "packet_camera_shake") == ["intensity", "duration", "type", "action"]
      and field_type(types428, "packet_camera_shake", "action")[1]["mappings"] == {"0": "add", "1": "stop"})
check("packet_command_output：422 は output_type が i8・4 フィールド、428 は output_type がマッパー（1 last／2 silent／3 all／4 data_set）で data_set を追加して 5 フィールド",
      field_names(types422, "packet_command_output") == ["origin", "output_type", "success_count", "output"]
      and field_type(types422, "packet_command_output", "output_type") == "i8"
      and field_names(types428, "packet_command_output") == ["origin", "output_type", "success_count", "output", "data_set"]
      and field_type(types428, "packet_command_output", "output_type")[1]["mappings"] == {"1": "last", "2": "silent", "3": "all", "4": "data_set"})
check("packet_set_title：422 の type は素の zigzag32、428 は 9 件のマッパー",
      field_type(types422, "packet_set_title", "type") == "zigzag32"
      and len(field_type(types428, "packet_set_title", "type")[1]["mappings"]) == 9)
check("packet_inventory_content：422 は inventory_id、428 は window_id（型は両方 WindowIDVarint で改名のみ）",
      field_names(types422, "packet_inventory_content") == ["inventory_id", "input"]
      and field_names(types428, "packet_inventory_content") == ["window_id", "input"]
      and field_type(types422, "packet_inventory_content", "inventory_id") == "WindowIDVarint"
      and field_type(types428, "packet_inventory_content", "window_id") == "WindowIDVarint")
check("packet_mob_equipment：422 は windows_id、428 は window_id（型は両方 WindowID で改名のみ）",
      field_names(types422, "packet_mob_equipment")[-1] == "windows_id"
      and field_names(types428, "packet_mob_equipment")[-1] == "window_id")

# ---------- H. ブロック・バイオーム・item 名前対応 ----------
be422_dir = TMP / "be-422"
req200 = be422_dir / "bedrock-1.16.200-required_item_list.json"
canon200 = be422_dir / "bedrock-1.16.200-canonical_block_states.nbt"
blockid200 = be422_dir / "bedrock-1.16.200-block_id_map.json"
biome200 = be422_dir / "bedrock-1.16.200-biome_id_map.json"
biome201 = be422_dir / "bedrock-1.16.201-biome_id_map.json"
req201 = be422_dir / "bedrock-1.16.201-required_item_list.json"
canon201 = be422_dir / "bedrock-1.16.201-canonical_block_states.nbt"
blockid201 = be422_dir / "bedrock-1.16.201-block_id_map.json"
be431_dir = TMP / "be-431"
req210 = be431_dir / "req210-required_item_list.json"
canon210 = be431_dir / "req210-canonical_block_states.nbt"
blockid210 = be431_dir / "req210-block_id_map.json"
biome210 = be431_dir / "req210-biome_id_map.json"
req440 = TMP / "be-440" / "req-bedrock-1.17.0-required_item_list.json"
canon440 = TMP / "be-440" / "canon-bedrock-1.17.0.nbt"
check("1.16.200 の required_item_list.json が存在する", req200.is_file(), str(req200))
check("1.16.200 の required_item_list.json のサイズが 91,328 バイト",
      req200.is_file() and req200.stat().st_size == 91328,
      f"{req200.stat().st_size} バイト" if req200.is_file() else "なし")
check("1.16.200 の required_item_list.json の SHA-1 が 682782b5…と一致",
      req200.is_file()
      and sha1(req200) == "682782b5c0da0465aef275522d4b97e01f7aa700",
      sha1(req200) if req200.is_file() else "なし")
check("1.16.200 の required_item_list.json の名前が 914 件",
      req200.is_file()
      and len(json.loads(req200.read_text())) == 914,
      f"{len(json.loads(req200.read_text()))} 件" if req200.is_file() else "なし")
check("1.16.200 の required_item_list.json が 1.16.210 のものとバイト同一",
      req200.is_file() and req210.is_file()
      and sha1(req200) == sha1(req210))
check("1.16.200 の required_item_list.json が 1.17.0 のものとバイト同一ではない",
      req200.is_file() and req440.is_file()
      and sha1(req200) != sha1(req440))
check("bedrock-1.16.201 タグの required_item_list.json は HTTP 404（14 バイト・\"404: Not Found\"）でタグ自体が存在しない",
      req201.is_file() and req201.stat().st_size == 14
      and req201.read_text() == "404: Not Found")
check("bedrock-1.16.201 タグの canonical_block_states.nbt・block_id_map.json も HTTP 404（14 バイト）",
      canon201.is_file() and canon201.stat().st_size == 14
      and blockid201.is_file() and blockid201.stat().st_size == 14)
check("1.16.200 から再生成した候補表が既存の bedrock_item_ids_1_16_210.bin とバイト同一（新規生成不要）",
      (be422_dir / "bedrock_item_ids_1_16_200_candidate.bin").is_file()
      and sha1(be422_dir / "bedrock_item_ids_1_16_200_candidate.bin") == sha1(tag_bin)
      == "a35eaa5a90525651aa5983808508c0e87124cc27")
check("1.16.200 の canonical_block_states.nbt が存在する", canon200.is_file(), str(canon200))
check("1.16.200 の canonical_block_states.nbt のサイズが 981,311 バイト",
      canon200.is_file() and canon200.stat().st_size == 981311,
      f"{canon200.stat().st_size} バイト" if canon200.is_file() else "なし")
check("1.16.200 の canonical_block_states.nbt の SHA-1 が 1d6771cd…と一致",
      canon200.is_file()
      and sha1(canon200) == "1d6771cd43536248f9e9c820fad9001c7ad92618",
      sha1(canon200) if canon200.is_file() else "なし")
sys.path.insert(0, str(BE / "tools"))
from parse_nbt import parse_canonical  # noqa: E402
canon_entries_200 = (len(parse_canonical(str(canon200))[0])
                     if canon200.is_file() else -1)
canon_entries_210 = (len(parse_canonical(str(canon210))[0])
                     if canon210.is_file() else -1)
canon_entries_440 = (len(parse_canonical(str(canon440))[0])
                     if canon440.is_file() else -1)
check("1.16.200 の canonical_block_states.nbt が 1.16.210 のものとバイト同一ではない",
      canon200.is_file() and canon210.is_file()
      and sha1(canon200) != sha1(canon210))
check("1.16.200 の canonical_block_states.nbt が 6,611 エントリー（NBT のパレット長・parse_nbt.py で数えた実測）",
      canon200.is_file() and canon_entries_200 == 6611,
      f"{canon_entries_200} エントリー" if canon200.is_file() else "なし")
check("1.16.210 の canonical_block_states.nbt が 6,575 エントリー",
      canon210.is_file() and canon_entries_210 == 6575,
      f"{canon_entries_210} エントリー" if canon210.is_file() else "なし")
check("1.17.0 の canonical_block_states.nbt が 7,682 エントリー",
      canon440.is_file() and canon_entries_440 == 7682,
      f"{canon_entries_440} エントリー" if canon440.is_file() else "なし")
check("1.16.200 の block_id_map.json が存在する", blockid200.is_file(), str(blockid200))
check("1.16.200 の block_id_map.json の SHA-1 が f3855a10…と一致",
      blockid200.is_file()
      and sha1(blockid200) == "f3855a102c698f960020d928bcaef10d25724062",
      sha1(blockid200) if blockid200.is_file() else "なし")
check("1.16.200 の block_id_map.json が 553 エントリー",
      blockid200.is_file()
      and len(json.loads(blockid200.read_text())) == 553,
      f"{len(json.loads(blockid200.read_text()))} 件" if blockid200.is_file() else "なし")
check("1.16.200 の block_id_map.json が 1.16.210 のものとバイト同一（SHA-1 が同じ）",
      blockid200.is_file() and blockid210.is_file()
      and sha1(blockid200) == sha1(blockid210))
blockid170 = TMP / "be-440" / "blockid-bedrock-1.17.0.json"
check("1.16.200 の block_id_map.json が 1.17.0 のものとバイト同一（SHA-1 が同じ）",
      blockid200.is_file() and blockid170.is_file()
      and sha1(blockid200) == sha1(blockid170))
check("1.16.200 の biome_id_map.json には実データが無い（bedrock-1.16.200 タグに無く HTTP 404・ファイルの中身は \"404: Not Found\" の 14 バイト）",
      biome200.is_file() and biome200.stat().st_size == 14
      and biome200.read_text() == "404: Not Found",
      f"{biome200.stat().st_size} バイト" if biome200.is_file() else "なし")
check("1.16.201 の biome_id_map.json にも実データが無い（bedrock-1.16.201 タグに無く HTTP 404・14 バイト）",
      biome201.is_file() and biome201.stat().st_size == 14
      and biome201.read_text() == "404: Not Found",
      f"{biome201.stat().st_size} バイト" if biome201.is_file() else "なし")
check("1.16.201 のブロック用 .bin は無く（既存ペアの流用）",
      not (RES / "bedrock_block_runtime_ids_1_16_201.bin").exists())
check("既存ペアの canonical_block_states_1_20_50.nbt が存在し SHA-1 が d2b6acbb…と一致",
      (RES / "canonical_block_states_1_20_50.nbt").is_file()
      and sha1(RES / "canonical_block_states_1_20_50.nbt") == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("既存ペアの bedrock_block_runtime_ids_1_20_4.bin が存在し SHA-1 が ee36fffb…と一致",
      (RES / "bedrock_block_runtime_ids_1_20_4.bin").is_file()
      and sha1(RES / "bedrock_block_runtime_ids_1_20_4.bin") == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
# パレットの状態比較（compare_palette_422.py と同じ key で数えた実測）
def _pal_key(x):
    return (x["name"], json.dumps(x.get("states", {}), sort_keys=True, ensure_ascii=False))
canon2050_res = RES / "canonical_block_states_1_20_50.nbt"
if canon200.is_file() and canon210.is_file() and canon440.is_file() and canon2050_res.is_file():
    _e200 = parse_canonical(str(canon200))[0]
    _e210 = parse_canonical(str(canon210))[0]
    _e440 = parse_canonical(str(canon440))[0]
    _e2050 = parse_canonical(str(canon2050_res))[0]
    _k200 = {_pal_key(x): i for i, x in enumerate(_e200)}
    _k210 = {_pal_key(x): i for i, x in enumerate(_e210)}
    _k440 = {_pal_key(x): i for i, x in enumerate(_e440)}
    _k2050 = {_pal_key(x): i for i, x in enumerate(_e2050)}
    _same210 = sum(1 for kk, i in _k200.items() if _k210.get(kk) == i)
    _only200 = [kk for kk in _k200 if kk not in _k210]
    _only210 = [kk for kk in _k210 if kk not in _k200]
    _same440 = sum(1 for kk, i in _k200.items() if _k440.get(kk) == i)
    _same2050 = sum(1 for kk, i in _k200.items() if _k2050.get(kk) == i)
    check("1.16.200 vs 1.16.210：同一ランタイム ID が 6,026/6,611",
          _same210 == 6026, f"{_same210}/6,611")
    check("1.16.200 にのみある状態が 48 件",
          len(_only200) == 48, f"{len(_only200)} 件")
    check("1.16.210 にのみある状態が 12 件",
          len(_only210) == 12, f"{len(_only210)} 件")
    check("1.16.200 vs 1.17.0：同一ランタイム ID が 136/6,611",
          _same440 == 136, f"{_same440}/6,611")
    check("1.16.200 vs 1.20.50：同一ランタイム ID が 0/6,611（589 と同じ状況でブロック表は新規不要）",
          _same2050 == 0, f"{_same2050}/6,611")

# ---------- I. 範囲文字列と NetherNet 分岐 ----------
proto_doc = (SRC / "BedrockProtocol.java").read_text()
check("BedrockProtocol の javadoc が「1.7.1〜26.52 / 291〜2193 / 62 バージョン分」",
      "1.7.1〜26.52" in proto_doc and "291〜2193" in proto_doc
      and "62 バージョン分" in proto_doc)
sess_src = (SRC / "BedrockSession.java").read_text()
check("BedrockSession の切断文が「supported: 291-2193 over RakNet; 2225 needs NetherNet」",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in sess_src)
srv_src = (SRC / "BedrockServer.java").read_text()
check("BedrockServer の起動ログが「1.7.1〜26.52 / 291〜2193」",
      "1.7.1〜26.52" in srv_src and "291〜2193" in srv_src)
check("BedrockVersion の NetherNet 分岐（2225）が 422 と無関係に残っている",
      "2225" in ver_src and "NetherNet" in ver_src)
check("BedrockChunkTranslator が BedrockVersion を参照していない（チャンク変換はバージョン非依存）",
      "BedrockVersion" not in (SRC / "BedrockChunkTranslator.java").read_text())
check("BedrockEntityTranslator は BedrockVersion を引数で受け取る（バージョン判定は呼び出し元から渡される述語経由）",
      "BedrockVersion version" in (SRC / "BedrockEntityTranslator.java").read_text())

# ---------- J. 422 回の実質的な変更：StartGame の rewind 2 フィールド分岐 ----------
check("述語 hasStartGameRewindFields() が protocol >= 428 で定義されている",
      bool(re.search(r"boolean hasStartGameRewindFields\(\) \{\s*return protocol >= 428;",
                     ver_src)))
check("述語 addPaintingUsesBlockCoordinates() が protocol >= 422 && protocol <= 428 で定義されている（422 も対象。§28 で下限を追加）",
      bool(re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 || \(protocol >= 422 && protocol <= 428\);",
                     ver_src)))
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 で定義されている（422 も対象。§28 で下限を追加）",
      bool(re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                     ver_src)))
pkt_src = (SRC / "BedrockPackets.java").read_text()
check("BedrockPackets.encodeStartGame が rewind_history_size・server_authoritative_block_breaking を hasStartGameRewindFields() で分岐している",
      "hasStartGameRewindFields()" in pkt_src)
check("BedrockPackets.encodeAddPainting が coordinates を addPaintingUsesBlockCoordinates() で分岐している",
      "addPaintingUsesBlockCoordinates()" in pkt_src)
check("BedrockPackets.encodePlayerListRemove が verified を playerListRemoveHasVerified() で分岐している",
      "playerListRemoveHasVerified()" in pkt_src)
check("BedrockVersionTest に allSixtyFourSupportedProtocolsResolve テストがあり 422 が含まれる",
      "allSixtyFourSupportedProtocolsResolve" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text()
      and "422" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text())
check("BedrockVersionTest に startGameRewindFieldsGate テストがある（422 は false・428 は true）",
      "startGameRewindFieldsGate" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text())
check("BedrockSessionVersionTest に allSixtyThreeProtocolsReachStartGame テストがあり 422 が含まれる",
      "allSixtyThreeProtocolsReachStartGame" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java").read_text())
check("BedrockPacketsVersionTest に startGame422OmitsRewindFields テストがある（422 は 428 より 2 バイト短い）",
      "startGame422OmitsRewindFields" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text())
check("BedrockPacketsVersionTest の startGameParsesForEveryShape に V1_16_201 と V1_16_210 が含まれる",
      "V1_16_201" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text()
      and "V1_16_210" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text())
check("BedrockPacketsEntityTest に addPlayer422Equals428 テストがある（AddPlayer は 422 と 428 でバイト同一）",
      "addPlayer422Equals428" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsEntityTest.java").read_text())
check("422〜428 の間に分岐の閾値が無い（422 の次の閾値は 428。423〜427 の述語は無い）",
      not re.search(r"protocol >= 42[3-7]\b", ver_src)
      and not re.search(r"protocol <= 42[3-7]\b", ver_src)
      and not re.search(r"protocol == 42[3-7]\b", ver_src))
check("52 件の型変更のうちコードが書くのは StartGame の rewind 2 フィールドのみ（GameRule・MetadataFlags1・MetadataFlags2・SoundType・StructureBlockSettings・packet_event・sync_entity_property・add_volume_entity・remove_volume_entity・BlockPalette・BlockProperties はコードで参照されない）",
      not any(re.search(rf"\b{name}\b", pkt_src)
              for name in ["GameRule",
                           "MetadataFlags1",
                           "MetadataFlags2",
                           "SoundType",
                           "StructureBlockSettings",
                           "packet_event",
                           "sync_entity_property",
                           "add_volume_entity",
                           "remove_volume_entity",
                           "BlockPalette",
                           "BlockProperties"]))
check("GameRule は件数 0 で送るため editable を書かない（BedrockPackets に GameRule のエンコードが無い）",
      "GameRule" not in pkt_src)
check("Skin の既知の制限：writeEmptySkin は premium を bool で書く 567 形式のまま（422・428 の Skin.premium は string。判断待ちで 422 回では直さない。BE-NOTES.md §22 参照）",
      "geometry data version" in pkt_src
      and "primary user" in pkt_src
      and "overriding player appearance" in pkt_src)
check("decodeInventoryTransaction は Transaction の network_ids（bool）を読み飛ばさない（422・428 ともに判断待ちで直さない。BE-NOTES.md §22 参照）",
      re.search(r"\bnetwork_ids\b", pkt_src) is None)
notes_src = (ROOT / "BE-NOTES.md").read_text()
check("BE-NOTES.md §22 に StartGame の rewind 2 フィールド分岐が記録されている",
      "§22" in notes_src and "rewind" in notes_src.lower())
check("BE-NOTES.md §22 に runtime_entity_id の varint／varint64 の判断が記録されている",
      "§22" in notes_src and "runtime_entity_id" in notes_src)
check("BE-NOTES.md §22 に MetadataDictionary のキーずれの判断待ちが記録されている",
      "§22" in notes_src and "MetadataDictionary" in notes_src)
check("BE-NOTES.md §22 に Transaction の network_ids の判断待ちが記録されている",
      "§22" in notes_src and "network_ids" in notes_src)
check("BE-NOTES.md §22 に 419（1.16.101）の protocol.json が無いことが記録されている",
      "§22" in notes_src and "419" in notes_src)

print()
print(f"TOTAL: {len(CHECKS)} checks, {len(CHECKS) - len(FAILURES)} passed, {len(FAILURES)} failed")
if FAILURES:
    print("FAILURES:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
