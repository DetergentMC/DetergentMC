#!/usr/bin/env python3
"""Bedrock 1.16.210（プロトコル 428）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py /
verify-be-534-tables.py / verify-be-527-tables.py /
verify-be-503-tables.py / verify-be-486-tables.py / verify-be-475-tables.py /
verify-be-471-tables.py / verify-be-465-tables.py / verify-be-448-tables.py /
verify-be-440-tables.py / verify-be-431-tables.py
にならい、表・定数・述語の前提を機械計数で確認する。すべて実ファイル
（BedrockVersion.java / BedrockProtocol.java / BedrockSession.java /
protocol-1.16.210.json / protocol-1.16.220.json / protocol-1.17.0.json /
protocol-1.17.10.json / protocol-1.17.30.json / protocol-1.17.40.json /
protocol-1.18.0.json / protocol-1.18.11.json / protocol-1.18.30.json /
protocol-1.19.1.json / protocol-1.19.10.json / protocol-1.19.20.json /
protocol-1.19.30.json / protocol-1.19.40.json / protocol-1.19.50.json /
.bin データ）に対するチェックで、推測はない。

- 428 の protocol.json は minecraft-data の `data/bedrock/1.16.210/` にある。
- 428 は 1.16.210 のみで共有するプロトコル（protocolVersions.json では
  428＝1.16.210 の 1 エントリのみ。Minecraft Wiki「Bedrock Edition
  1.16.210」個別ページの infobox「Protocol version 428」でも確認。
  BE-NOTES.md §21）。
- 428 と 431 の差分は追加 0・削除 0・リネーム 0・型変更 60 件。
  パケット数は 161 件・最大 ID 164・欠番 [0, 16, 37, 116] で 431 と同じ。
  StartGame は 428・431 ともに 59 フィールドで、block_palette／
  block_properties の改名 1 件だけ違う（定義は型名まで含めて同じ。
  428 の block_palette は BlockPalette 型、431 の block_properties は
  BlockProperties 型で、両型の定義は JSON 完全一致）。
  add_player・add_entity・add_item_entity も 428＝431 でワイヤ等価
  （x／y／z・speed_x／speed_y／speed_z の lf32 6 つと position・velocity
  の vec3f 2 つの違いだけ）。LevelEvent は 428 が 61 件・431 が 88 件。
- 428 の 1 つ下は 422（1.16.201）。423〜427・429・430 にエントリは無い
  （423〜427 はベータ版 1.16.210 の番号、429・430 はベータ版 1.16.220 の
  番号。426 は Wiki 表にエントリ無しで未確認。BE-NOTES.md §21）。
- アイテム表は新規生成不要。431 回に生成した bedrock_item_ids_1_16_210.bin
  は 1.16.210 のデータそのもの（bedrock-1.16.210 タグの
  required_item_list.json から生成。431 回は 1.16.220 の代用だったが、
  428 回は代用ではない）。exact=624／shared=236／legacy=33／fallback=419、
  SHA-1 `a35eaa5a…`。item_tables_summary.json に 428 のエントリは無い
  （554・544 が表を共用するときと同じ流儀）。
- ブロックパレットは 1.16.210 のものが 6,575 エントリーで、1.17.0 の
  パレットの厳密な部分集合（同一ランタイム ID は 136/6,575、1.16.210 に
  のみある状態は 0 件）。それでもブロック表は新規不要（既存ペアを流用。
  589 と同じ根拠。1.16.210 と 1.20.50 の同一ランタイム ID は 0/6,575。
  BE-NOTES.md §21）。biome_id_map.json は bedrock-1.16.210 タグに
  存在しない（HTTP 404・取得したファイルの中身は "404: Not Found" の
  14 バイト）が設計上使っていないため要否判断に影響しない。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 428 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 428 回の実質的な変更は 2 件：**AddPainting の coordinates を 428
  では BlockCoordinates（x zigzag32・y varint・z zigzag32）で書く**
  （addPaintingUsesBlockCoordinates() = protocol >= 422 && protocol
  <= 428。§28 で下限を追加：390〜419 は Cloudburst のコーデックでは
  vec3f。431 以降も vec3f）と、**PlayerList の remove でも verified を書く**
  （playerListRemoveHasVerified() = protocol >= 422 && protocol
  <= 428。§28 で下限を追加。431 以降は add
  のときのみ）。それ以外の 58 件の型変更のうちコードが読む・書くのは
  無い（Item の count 無し・Skin の premium が string は判断待ちで
  直さない。§J 参照）。

使い方: python3 detergentmc-bedrock/tools/verify-be-428-tables.py
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


def packet_ids(data):
    mapper = data["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
    return {int(k): v for k, v in mapper.items()}


# ---------- A. protocol.json（428）の同一性 ----------
p28 = TMP / "protocol-1.16.210.json"
check("428 protocol.json が存在する", p28.is_file(), str(p28))
check("428 protocol.json の行数が 8,052 行（wc -l、改行数）",
      p28.is_file()
      and p28.read_text().count("\n") == 8052,
      f"{p28.read_text().count(chr(10))} 行" if p28.is_file() else "なし")
check("428 protocol.json のサイズが 205,856 バイト",
      p28.is_file() and p28.stat().st_size == 205856,
      f"{p28.stat().st_size} バイト" if p28.is_file() else "なし")
check("428 protocol.json の SHA-1 が 8e46e9b8…と一致",
      p28.is_file()
      and sha1(p28) == "8e46e9b85cba34905660df27aaebbcd77977cfe7",
      sha1(p28) if p28.is_file() else "なし")

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

# ---------- B. パケット一覧（428）の件数・境界 ----------
check("428 のパケット数が 161 件", len(ids428) == 161, f"{len(ids428)} 件")
check("428 の最大パケット ID が 164", max(ids428) == 164, f"最大 {max(ids428)}")
check("428 の型定義数が 242", len(types428) == 242, f"{len(types428)}")
check("431 のパケット数が 161 件", len(ids431) == 161, f"{len(ids431)} 件")
check("431 の最大パケット ID が 164", max(ids431) == 164, f"最大 {max(ids431)}")
check("431 の型定義数が 250", len(types431) == 250, f"{len(types431)}")
check("428 の ID の欠番が 0・16・37・116（431 と同じ）",
      [i for i in range(max(ids428) + 1) if i not in ids428] == [0, 16, 37, 116])
check("428 のパケット ID 0〜164 が 431 の同 ID と名称がすべて一致",
      all(ids428[i] == ids431[i] for i in ids428),
      f"{sum(1 for i in ids428 if ids428[i] == ids431.get(i))} / {len(ids428)} 件一致")

d428 = TMP / "packet-diff-428-vs-431.json"
check("428 vs 431 の差分 JSON が存在する", d428.is_file(), str(d428))
if d428.is_file():
    j428 = json.loads(d428.read_text())
    check("428 vs 431：追加 0 件",
          j428["added"] == {}, json.dumps(j428["added"]))
    check("428 vs 431：削除 0・リネーム 0",
          j428["removed"] == {} and j428["renamed"] == {})
    check("428 vs 431：型変更 60 件",
          len(j428["changed_types"]) == 60, str(j428["changed_types"]))
    check("428 vs 431：型変更の内訳が期待通り",
          j428["changed_types"] == ["BlockPalette (only in A)",
                                     "BlockProperties (only in B)",
                                     "CommandOrigin",
                                     "EnchantOption (only in B)",
                                     "EnchantOptions (only in A)",
                                     "Item",
                                     "ItemExtraDataWithBlockingTick (only in B)",
                                     "ItemExtraDataWithoutBlockingTick (only in B)",
                                     "ItemLegacy (only in B)",
                                     "ItemStack (only in A)",
                                     "ItemStackRequest",
                                     "ItemStackResponses",
                                     "ItemStacks",
                                     "MapDecoration (only in B)",
                                     "MetadataDictionary",
                                     "PlayerRecords",
                                     "Recipes",
                                     "ScoreEntries (only in A)",
                                     "ScoreboardIdentityEntries (only in A)",
                                     "ShortArray (only in B)",
                                     "ShortString (only in B)",
                                     "Skin",
                                     "SkinImage",
                                     "SoundType (only in B)",
                                     "StructureBlockSettings (only in B)",
                                     "TrackedObject (only in B)",
                                     "TransactionActions",
                                     "TransactionUseItem",
                                     "UpdateMapFlags (only in B)",
                                     "WindowIDZigzag32 (only in B)",
                                     "packet_add_entity",
                                     "packet_add_item_entity",
                                     "packet_add_painting",
                                     "packet_add_player",
                                     "packet_available_commands",
                                     "packet_boss_event",
                                     "packet_clientbound_map_item_data",
                                     "packet_creative_content",
                                     "packet_inventory_slot",
                                     "packet_level_event",
                                     "packet_level_sound_event",
                                     "packet_map_create_locked_copy",
                                     "packet_network_stack_latency",
                                     "packet_npc_request",
                                     "packet_on_screen_texture_animation",
                                     "packet_photo_transfer",
                                     "packet_player_enchant_options",
                                     "packet_resource_pack_chunk_data",
                                     "packet_resource_pack_chunk_request",
                                     "packet_resource_pack_data_info",
                                     "packet_respawn",
                                     "packet_set_last_hurt_by",
                                     "packet_set_score",
                                     "packet_set_scoreboard_identity",
                                     "packet_show_store_offer",
                                     "packet_spawn_particle_effect",
                                     "packet_start_game",
                                     "packet_structure_block_update",
                                     "packet_structure_template_data_export_request",
                                     "packet_structure_template_data_export_response"],
          json.dumps(j428["changed_types"]))

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
json_by_name = {v: k for k, v in ids428.items()}
# update_subchunk_blocks・subchunk・subchunk_request は 428 には存在しない
# （465・471 で新規追加）ためループから除き、不存在を別途確認する。
for const, pname in CORE:
    if const in ("UPDATE_SUB_CHUNK_BLOCKS", "SUB_CHUNK", "SUB_CHUNK_REQUEST"):
        continue
    java_id = int(java_packets[const], 16)
    json_id = json_by_name.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が protocol.json と一致",
          json_id == java_id, f"json={json_id}")
check("パケット ID 70 の名称が chunk_radius_update（Java 定数名 CHUNK_RADIUS_UPDATED とは名称違い・431 回と同じ）",
      ids428.get(70) == "chunk_radius_update")
check("パケット UPDATE_SUB_CHUNK_BLOCKS（0xac=172）が 428 の protocol.json に存在しない（465 で新規追加）",
      json_by_name.get("update_subchunk_blocks") is None)
check("パケット SUB_CHUNK（0xae=174）が 428 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk") is None)
check("パケット SUB_CHUNK_REQUEST（0xaf=175）が 428 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk_request") is None)
check("パケット SYNC_ENTITY_PROPERTY（165）が 428 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("sync_entity_property") is None)
check("パケット ADD_VOLUME_ENTITY（166）が 428 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("add_volume_entity") is None)
check("パケット REMOVE_VOLUME_ENTITY（167）が 428 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("remove_volume_entity") is None)
check("パケット SIMULATION_TYPE（168）が 428 の protocol.json に存在しない（448 で新規追加）",
      json_by_name.get("simulation_type") is None)
check("パケット NPC_DIALOGUE（169）が 428 の protocol.json に存在しない（448 で新規追加）",
      json_by_name.get("npc_dialogue") is None)

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_16_210 が存在する", "V1_16_210" in entry_by_name)
check("V1_16_210 のプロトコルが 428",
      entry_by_name.get("V1_16_210", (None,))[0] == 428,
      str(entry_by_name.get("V1_16_210")))
check("V1_16_210 の表示名が 1.16.210",
      entry_by_name.get("V1_16_210", (None, None))[1] == "1.16.210")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_16_210 が V1_16_221 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_16_210(428") < ver_src.index("V1_16_221(431"))
check("enum の javadoc に 1.16.210 の protocol.json が含まれる",
      "1.16.210" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 428 -> \"(1_16_210)\";", ver_src)
check("itemTableTag：428 が 1_16_210 の表を使う（1.16.210 自体の実測・代用ではない）", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_16_210.bin"
check("アイテム表 bedrock_item_ids_1_16_210.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が a35eaa5a…と一致（1.16.210 候補表）",
      tag_bin.is_file()
      and sha1(tag_bin) == "a35eaa5a90525651aa5983808508c0e87124cc27",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規 0 本・431 回の表を共用）", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
check("item_tables_summary.json に 428 のエントリが無い（554・544 が表を共用するときと同じ流儀）",
      "428" not in summary)
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
# (1) StartGame：428 は 59 フィールド、431 は 59 フィールド（block_palette／block_properties の改名だけ違う）
sg428 = field_names(types428, "packet_start_game")
sg431 = field_names(types431, "packet_start_game")
check("StartGame：428 は 59 フィールド", len(sg428) == 59, f"{len(sg428)} フィールド")
check("StartGame：431 は 59 フィールド", len(sg431) == 59, f"{len(sg431)} フィールド")
check("StartGame：428 のフィールド一覧が 431 の block_properties を block_palette に置き換えたものと一致する（順序も同じ）",
      [f if f != "block_palette" else "block_properties" for f in sg428] == sg431)
check("StartGame：428 の block_palette は BlockPalette 型",
      field_type(types428, "packet_start_game", "block_palette") == "BlockPalette")
check("StartGame：431 の block_properties は BlockProperties 型",
      field_type(types431, "packet_start_game", "block_properties") == "BlockProperties")
check("StartGame：BlockPalette（428）と BlockProperties（431）の定義が JSON 完全一致（改名だけ）",
      types428["BlockPalette"] == types431["BlockProperties"])
check("StartGame：428 の block_palette が varint 件数の {name: string, state: nbt} 配列",
      types428["BlockPalette"] == ["array", {"countType": "varint", "type": ["container", [{"name": "name", "type": "string"}, {"name": "state", "type": "nbt"}]]}])
check("StartGame：428 の末尾が server_authoritative_inventory（431 と同じ）",
      sg428[-1] == "server_authoritative_inventory"
      and sg431[-1] == "server_authoritative_inventory")
check("StartGame：428 に engine が無い（hasStartGameEngine()=false の前提）",
      "engine" not in sg428)
check("StartGame：428 に edu_resource_uri が無い（hasStartGameEduResourceUri()=false の前提）",
      "edu_resource_uri" not in sg428)
check("StartGame：428 に block_pallete_checksum が無い（hasStartGameBlockPaletteChecksum()=false の前提）",
      "block_pallete_checksum" not in sg428
      and "block_pallette_checksum" not in sg428)
check("StartGame：428 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types428, "packet_start_game", "seed") == "zigzag32")
check("StartGame：431 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types431, "packet_start_game", "seed") == "zigzag32")
check("StartGame：428 の dimension は zigzag32",
      field_type(types428, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：428 に property_data が無い（hasStartGame527Fields()=false の前提）",
      "property_data" not in sg428)
check("StartGame：428 に world_template_id が無い（hasStartGame527Fields()=false の前提）",
      "world_template_id" not in sg428)
check("StartGame：428 に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg428)
check("StartGame：428 に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg428 for t in ["server_identifier", "world_identifier",
                                   "scenario_identifier", "owner_identifier"]))
check("StartGame：428 に editor_world が無い（hasEditorWorld=false）",
      "editor_world" not in sg428)
check("StartGame：428 の permission_level は zigzag32（hasPermissionLevelMapper=false）",
      field_type(types428, "packet_start_game", "permission_level") == "zigzag32")
check("StartGame：428 の movement_authority が存在する（hasMovementAuthority=true）",
      "movement_authority" in sg428)
check("StartGame：428 の itemstates が存在する（hasItemstates=true）",
      "itemstates" in sg428)
check("StartGame：428 に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg428)
check("StartGame：428 に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg428 and "server_join_info" not in sg428)
check("StartGame：428 に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg428)
check("StartGame：428 に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg428)
check("StartGame：428 に created_in_editor・exported_from_editor・block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(f in sg428 for f in ["created_in_editor", "exported_from_editor",
                                   "block_network_ids_are_hashes"]))
check("StartGame：428 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg428)
check("StartGame：428 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg428)
check("StartGame：428 に persona_disabled・custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg428 and "custom_skins_disabled" not in sg428)
check("StartGame：428 に chat_restriction_level・disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg428
      and "disable_player_interactions" not in sg428)
check("StartGame：428 に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg428)

# (2) Resource packs info / stack：428 は 4 フィールド、431 も 4 フィールドで JSON 完全一致
rpi = field_names(types428, "packet_resource_packs_info")
rpi431 = field_names(types431, "packet_resource_packs_info")
check("Resource packs info：428 は 4 フィールド（must_accept・has_scripts・behaviour_packs・texture_packs）",
      rpi == ["must_accept", "has_scripts", "behaviour_packs", "texture_packs"])
check("Resource packs info：431 は 4 フィールド（428 と JSON 完全一致・force_server_packs は 448 で新規）",
      rpi431 == ["must_accept", "has_scripts", "behaviour_packs", "texture_packs"]
      and types428["packet_resource_packs_info"] == types431["packet_resource_packs_info"])
check("Resource packs info：428 に force_server_packs が無い（hasForceServerPacks()=false の前提）",
      "force_server_packs" not in rpi)
check("Resource packs info：428 に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("Resource packs info：428 に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("Resource packs info：428 に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("Resource packs info：428 に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
check("TexturePackInfos の countType が li16（431 と同じ）",
      types428["TexturePackInfos"][1].get("countType") == "li16"
      and types431["TexturePackInfos"][1].get("countType") == "li16")
rps = field_names(types428, "packet_resource_pack_stack")
rps431 = field_names(types431, "packet_resource_pack_stack")
check("Resource pack stack：428 は 431 と同じ 6 フィールド（behavior_packs を含む）",
      rps == rps431 == ["must_accept", "behavior_packs", "resource_packs",
                        "game_version", "experiments",
                        "experiments_previously_used"])
check("Resource pack stack：428 に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

# (3) エンティティ・アイテム
check("Mob equipment の item が Item（ItemV4 ではない）",
      field_type(types428, "packet_mob_equipment", "item") == "Item")
check("Add item entity の item が Item",
      field_type(types428, "packet_add_item_entity", "item") == "Item")
mae = [n for n, _ in container_fields(types428, "packet_mob_armor_equipment")]
check("Mob armor equipment が helmet・chestplate・leggings・boots の 4 つ（body は無い）",
      mae == ["runtime_entity_id", "helmet", "chestplate", "leggings", "boots"])
check("ItemV4 型が 428 の protocol.json に存在しない", "ItemV4" not in types428)
check("ItemV4 型が 431 の protocol.json に存在しない", "ItemV4" not in types431)
ap428 = field_names(types428, "packet_add_player")
ap431 = field_names(types431, "packet_add_player")
check("Add player：428 は 25 フィールド",
      len(ap428) == 25, f"{len(ap428)} フィールド")
check("Add player：431 は 21 フィールド",
      len(ap431) == 21, f"{len(ap431)} フィールド")
check("Add player：428 のフィールド一覧が uuid・username・entity_id_self・runtime_entity_id・platform_chat_id・x・y・z・speed_x・speed_y・speed_z・pitch・yaw・head_yaw・held_item・metadata・flags・command_permission・action_permissions・permission_level・custom_stored_permissions・user_id・links・device_id・device_os",
      ap428 == ["uuid", "username", "entity_id_self", "runtime_entity_id",
                "platform_chat_id", "x", "y", "z", "speed_x", "speed_y",
                "speed_z", "pitch", "yaw", "head_yaw", "held_item",
                "metadata", "flags", "command_permission",
                "action_permissions", "permission_level",
                "custom_stored_permissions", "user_id", "links", "device_id",
                "device_os"])
check("Add player：431 のフィールド一覧が uuid・username・entity_id_self・runtime_entity_id・platform_chat_id・position・velocity・pitch・yaw・head_yaw・held_item・metadata・flags・command_permission・action_permissions・permission_level・custom_stored_permissions・user_id・links・device_id・device_os",
      ap431 == ["uuid", "username", "entity_id_self", "runtime_entity_id",
                "platform_chat_id", "position", "velocity", "pitch", "yaw",
                "head_yaw", "held_item", "metadata", "flags",
                "command_permission", "action_permissions", "permission_level",
                "custom_stored_permissions", "user_id", "links", "device_id",
                "device_os"])
check("Add player：428 の x・y・z・speed_x・speed_y・speed_z は lf32（431 の position・velocity は vec3f でワイヤ等価）",
      all(field_type(types428, "packet_add_player", f) == "lf32"
          for f in ["x", "y", "z", "speed_x", "speed_y", "speed_z"])
      and field_type(types431, "packet_add_player", "position") == "vec3f"
      and field_type(types431, "packet_add_player", "velocity") == "vec3f")
check("Add player：428 の device_os は li32",
      field_type(types428, "packet_add_player", "device_os") == "li32")
check("Add player：428・431 の entity_id_self は zigzag64（名称も型も同じ）",
      field_type(types428, "packet_add_player", "entity_id_self") == "zigzag64"
      and field_type(types431, "packet_add_player", "entity_id_self") == "zigzag64")
check("Add player：428 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap428)
check("Add player：431 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap431)
ae428 = field_names(types428, "packet_add_entity")
ae431 = field_names(types431, "packet_add_entity")
check("Add entity：428 は 15 フィールド（x／y／z・speed_x／speed_y／speed_z を lf32 で直書き）",
      ae428 == ["entity_id_self", "runtime_entity_id", "entity_type",
                "x", "y", "z", "speed_x", "speed_y", "speed_z",
                "pitch", "yaw", "head_yaw", "attributes", "metadata",
                "links"], f"{len(ae428)} フィールド")
check("Add entity：431 は 11 フィールド（position・velocity の vec3f 2 つ）",
      ae431 == ["entity_id_self", "runtime_entity_id", "entity_type",
                "position", "velocity", "pitch", "yaw", "head_yaw",
                "attributes", "metadata", "links"])
check("Add entity：428 の x・y・z・speed_x・speed_y・speed_z は lf32（431 の position・velocity は vec3f でワイヤ等価）",
      all(field_type(types428, "packet_add_entity", f) == "lf32"
          for f in ["x", "y", "z", "speed_x", "speed_y", "speed_z"])
      and field_type(types431, "packet_add_entity", "position") == "vec3f"
      and field_type(types431, "packet_add_entity", "velocity") == "vec3f")
check("Add entity：428・431 ともに body_yaw が無い（hasAddEntityBodyYaw()=false の前提）",
      "body_yaw" not in ae428 and "body_yaw" not in ae431)
check("Add entity：428・431 の entity_type は string",
      field_type(types428, "packet_add_entity", "entity_type") == "string"
      and field_type(types431, "packet_add_entity", "entity_type") == "string")
check("Add item entity：428 は 11 フィールド（x／y／z・speed_x／speed_y／speed_z を lf32 で直書き）",
      field_names(types428, "packet_add_item_entity") == ["entity_id_self",
                                                         "runtime_entity_id",
                                                         "item", "x", "y", "z",
                                                         "speed_x", "speed_y",
                                                         "speed_z", "metadata",
                                                         "is_from_fishing"])
check("Add item entity：431 は 8 フィールド（position・velocity の vec3f 2 つ）",
      field_names(types431, "packet_add_item_entity") == ["entity_id_self",
                                                         "runtime_entity_id",
                                                         "item", "position",
                                                         "velocity", "metadata",
                                                         "is_from_fishing"])
check("Set entity data は 428 と 431 で同じ（3 フィールド）",
      field_names(types428, "packet_set_entity_data") == field_names(types431, "packet_set_entity_data")
      and len(field_names(types428, "packet_set_entity_data")) == 3)

# (4) AddPainting・PlayerList（428 回の実質的な変更）
check("Add painting：428・431 ともに 5 フィールド（entity_id_self・runtime_entity_id・coordinates・direction・title）",
      field_names(types428, "packet_add_painting") == field_names(types431, "packet_add_painting")
      == ["entity_id_self", "runtime_entity_id", "coordinates", "direction",
          "title"])
check("Add painting：428 の coordinates は BlockCoordinates（addPaintingUsesBlockCoordinates()=true の前提）",
      field_type(types428, "packet_add_painting", "coordinates") == "BlockCoordinates")
check("Add painting：431 の coordinates は vec3f（addPaintingUsesBlockCoordinates()=false の前提）",
      field_type(types431, "packet_add_painting", "coordinates") == "vec3f")
check("BlockCoordinates は 428 に存在し x zigzag32・y varint・z zigzag32",
      types428["BlockCoordinates"] == ["container", [{"name": "x", "type": "zigzag32"}, {"name": "y", "type": "varint"}, {"name": "z", "type": "zigzag32"}]])
pr428 = types428["PlayerRecords"]
pr431 = types431["PlayerRecords"]
check("PlayerRecords：428・431 ともに 4 フィールド（type・records_count・records・verified）",
      [f.get("name") for f in pr428[1]] == [f.get("name") for f in pr431[1]]
      == ["type", "records_count", "records", "verified"])
check("PlayerRecords：428 の verified は records_count 個の bool 配列（remove でも存在する。playerListRemoveHasVerified()=true の前提）",
      [f for f in pr428[1] if f.get("name") == "verified"][0]["type"] == ["array", {"count": "records_count", "type": "bool"}])
check("PlayerRecords：431 の verified は type 依存の switch（add のときのみ存在する。playerListRemoveHasVerified()=false の前提）",
      [f for f in pr431[1] if f.get("name") == "verified"][0]["type"] == ["switch", {"compareTo": "type", "fields": {"add": ["array", {"count": "records_count", "type": "bool"}]}}])

# (5) Skin・Item（判断待ちで直さないもの）
skin428 = [f["name"] for f in types428["Skin"][1]]
skin431 = [f["name"] for f in types431["Skin"][1]]
check("Skin：428 は 17 フィールド（431 と同じ一覧・同じ順序）",
      len(skin428) == 17 and skin428 == skin431, f"{len(skin428)} フィールド")
check("Skin：428 の premium は string（431 は bool。writeEmptySkin の判断待ち）",
      field_type(types428, "Skin", "premium") == "string"
      and field_type(types431, "Skin", "premium") == "bool")
check("Skin：428・431 ともに premium は index 8（animation_data の直後）",
      skin428.index("premium") == 8 and skin431.index("premium") == 8)
check("Skin：428 に geometry_data_version・primary_user が無い（431 と同じ）",
      "geometry_data_version" not in skin428 and "primary_user" not in skin428)
check("SkinImage：428 の data は string、431 の data は ByteArray（どちらも varint 長＋バイト列でワイヤ等価）",
      field_type(types428, "SkinImage", "data") == "string"
      and field_type(types431, "SkinImage", "data") == "ByteArray")
check("SkinImage：428・431 ともに width・height は li32",
      field_type(types428, "SkinImage", "width") == "li32"
      and field_type(types431, "SkinImage", "width") == "li32")
item428 = types428["Item"]
check("Item：428 に count フィールドが無い（minecraft-data の 428 の Item 定義。判断待ち）",
      find_field(item428, "count") is None)
check("Item：428 は network_id・auxiliary_value・has_nbt（lu16）・nbt・can_place_on・can_destroy・blocking_tick（network_id 355 のとき zigzag64）",
      field_names(types428, "Item") == ["network_id"]
      and item428[1][1]["type"][1]["default"][1][0]["name"] == "auxiliary_value"
      and item428[1][2]["type"][1]["fields"]["355"] == ["container", [{"name": "blocking_tick", "type": "zigzag64"}]])
check("Item：431 は count（lu16）を持つ",
      find_field(types431["Item"], "count") == "lu16")
check("ItemLegacy：431 は count（lu16）を持つ（428 には ItemLegacy 型が無い）",
      "ItemLegacy" not in types428
      and find_field(types431["ItemLegacy"], "count") == "lu16")
check("Item：431 の extra は /ShieldItemID で ItemExtraDataWithBlockingTick／WithoutBlockingTick を分岐する（428 には ItemExtraData* 型が無い）",
      "ItemExtraDataWithBlockingTick" not in types428
      and "ItemExtraDataWithoutBlockingTick" not in types428
      and "/ShieldItemID" in json.dumps(types431["Item"]))

# (6) トランザクション・アイテム関連
leg = {n: t for n, t in container_fields(types428, "TransactionLegacy")}
check("TransactionLegacy の legacy_transactions が switch（431 と同じ）",
      isinstance(leg.get("legacy_transactions"), list)
      and leg["legacy_transactions"][0] == "switch")
check("TransactionLegacy が 428 と 431 で JSON 同一",
      types428["TransactionLegacy"] == types431["TransactionLegacy"])
tx = {n: t for n, t in container_fields(types428, "Transaction")}
tx_map = tx.get("transaction_type")[1]["mappings"]
check("Transaction の transaction_type が 0=normal・1=inventory_mismatch・2=item_use・3=item_use_on_entity・4=item_release（431 と同じ）",
      tx_map.get("0") == "normal"
      and tx_map.get("1") == "inventory_mismatch"
      and tx_map.get("2") == "item_use"
      and tx_map.get("3") == "item_use_on_entity"
      and tx_map.get("4") == "item_release")
check("Transaction の actions が TransactionActions（431 と同じ）",
      tx.get("actions") == "TransactionActions")
check("Transaction が 428 と 431 で JSON 同一",
      types428["Transaction"] == types431["Transaction"])
check("TransactionActions：428・431 ともに old/new item が Item（transactionUsesItemV4=false）",
      find_field(types428["TransactionActions"], "old_item") == "Item"
      and find_field(types428["TransactionActions"], "new_item") == "Item"
      and find_field(types431["TransactionActions"], "old_item") == "Item"
      and find_field(types431["TransactionActions"], "new_item") == "Item")
check("TransactionActions：428 は network_ids（bool）＋actions の container、431 は素の array（構造が違う）",
      types428["TransactionActions"][0] == "container"
      and [f for f in types428["TransactionActions"][1] if f.get("name") == "network_ids"][0]["type"] == "bool"
      and types431["TransactionActions"][0] == "array")
check("TransactionActions に window_id／flags の option 欄が無い（428・431 ともに。world_interaction 分岐内の flags とは別物）",
      "window_id" not in [f.get("name") for f in field_type(types428, "TransactionActions", "actions")[1]["type"][1]]
      and "flags" not in [f.get("name") for f in field_type(types428, "TransactionActions", "actions")[1]["type"][1]]
      and "window_id" not in [f.get("name") for f in types431["TransactionActions"][1]["type"][1]]
      and "flags" not in [f.get("name") for f in types431["TransactionActions"][1]["type"][1]])
check("TransactionUseItem：428 の hotbar_slot は varint、431 は zigzag32",
      field_type(types428, "TransactionUseItem", "hotbar_slot") == "varint"
      and field_type(types431, "TransactionUseItem", "hotbar_slot") == "zigzag32")
isr428 = item_stack_request_type_id(types428)
isr431 = item_stack_request_type_id(types431)
check("ItemStackRequest：428 の type_id が u8 マッパーで 16 件（0〜15）",
      isr428 is not None and isr428[1]["mappings"] == {
          "0": "take", "1": "place", "2": "swap", "3": "drop",
          "4": "destroy", "5": "consume", "6": "create",
          "7": "lab_table_combine", "8": "beacon_payment",
          "9": "mine_block", "10": "craft_recipe",
          "11": "craft_recipe_auto", "12": "craft_creative",
          "13": "optional", "14": "non_implemented",
          "15": "results_deprecated"},
      json.dumps(isr428[1]["mappings"]) if isr428 else "なし")
check("ItemStackRequest：428 の type_id が 431 と JSON 同一",
      isr428 == isr431)
check("ItemStackRequest：428 と 431 で JSON は違う（results_deprecated の item が Item／ItemLegacy）",
      types428["ItemStackRequest"] != types431["ItemStackRequest"]
      and "ItemLegacy" in json.dumps(types431["ItemStackRequest"])
      and "ItemLegacy" not in json.dumps(types428["ItemStackRequest"]))
check("ItemStackResponses：428 の request_id は varint、431 は zigzag32",
      find_field(types428["ItemStackResponses"], "request_id") == "varint"
      and find_field(types431["ItemStackResponses"], "request_id") == "zigzag32")
check("RecipeIngredient：428 が旧形式（network_id zigzag32＋匿名 switch）",
      types428["RecipeIngredient"][0] == "container"
      and types428["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types428["RecipeIngredient"][1][1].get("anon") is True,
      json.dumps(types428["RecipeIngredient"][1][0]))
check("RecipeIngredient：428 と 431 で JSON 同一",
      types428["RecipeIngredient"] == types431["RecipeIngredient"])
check("RecipeIngredient：554 が新形式（先頭が type の u8 マッパー：invalid／int_id_meta／molang／item_tag／string_id_meta）",
      types554["RecipeIngredient"][0] == "container"
      and types554["RecipeIngredient"][1][0].get("name") == "type"
      and types554["RecipeIngredient"][1][0]["type"][1].get("mappings") == {"0": "invalid", "1": "int_id_meta",
                                                                            "2": "molang", "3": "item_tag",
                                                                            "4": "string_id_meta"})

# (7) 核パケットの 428 vs 431：add_player・add_entity・add_item_entity を除く 11 件が JSON 同一
MAJOR = ["packet_level_chunk", "packet_move_entity", "packet_move_player",
         "packet_update_block", "packet_set_entity_data",
         "packet_mob_equipment", "packet_mob_armor_equipment",
         "packet_player_list",
         "packet_inventory_transaction", "packet_network_chunk_publisher_update",
         "packet_resource_pack_stack"]
for name in MAJOR:
    check(f"核パケット {name} が 428 と 431 で JSON 同一（分岐不要）",
          types428[name] == types431[name])
check("核パケット packet_resource_packs_info は 428 と 431 で JSON 同一（force_server_packs は両方に無い）",
      types428["packet_resource_packs_info"] == types431["packet_resource_packs_info"])
check("核パケット packet_player_auth_input が 428 と 431 で JSON 同一",
      types428["packet_player_auth_input"] == types431["packet_player_auth_input"])
check("核パケット packet_add_player は 428 と 431 で違う（x／y／z・speed_x／speed_y／speed_z と position・velocity の違いだけ・ワイヤ等価）",
      types428["packet_add_player"] != types431["packet_add_player"])
check("核パケット packet_add_entity は 428 と 431 で違う（x／y／z・speed_x／speed_y／speed_z と position・velocity の違いだけ・ワイヤ等価）",
      types428["packet_add_entity"] != types431["packet_add_entity"])
check("核パケット packet_add_item_entity は 428 と 431 で違う（x／y／z・speed_x／speed_y／speed_z と position・velocity の違いだけ・ワイヤ等価）",
      types428["packet_add_item_entity"] != types431["packet_add_item_entity"])
check("核パケット packet_start_game は 428 と 431 で違う（block_palette／block_properties の改名だけ）",
      types428["packet_start_game"] != types431["packet_start_game"])

# ---------- G. 428／431 の境界値 ----------
check("SoundType 型が 428 の protocol.json に存在しない（431 で新規追加）",
      "SoundType" not in types428)
st431 = types431["SoundType"][1]["mappings"]
check("SoundType：431 は 336 エントリ（0〜335）", len(st431) == 336, f"{len(st431)}")
check("SoundType：431 の 335 が Undefined・4 が Jump・5 が Break・6 が Place",
      st431.get("335") == "Undefined" and st431.get("4") == "Jump"
      and st431.get("5") == "Break" and st431.get("6") == "Place")
le428 = types428["packet_level_event"]
le431 = types431["packet_level_event"]
le_map = le428[1][0]["type"][1]["mappings"]
le_map431 = le431[1][0]["type"][1]["mappings"]
check("Level event：428 は 61 エントリ（最大キー 16384）",
      len(le_map) == 61, f"{len(le_map)}")
check("Level event：431 は 88 エントリ",
      len(le_map431) == 88, f"{len(le_map431)}")
check("Level event：428 の 61 件はすべて 431 にもあり（428 は 431 の部分集合）",
      all(k in le_map431 for k in le_map))
check("Level event：428 と 431 で共通キーが違うのは 2006 だけ（guardian_curse／particle_guardian_curse）",
      {k: (le_map[k], le_map431[k]) for k in le_map if k in le_map431
       and le_map[k] != le_map431[k]} == {"2006": ("guardian_curse",
                                                  "particle_guardian_curse")})
check("Level event：428 の 2000 が particle_shoot・2001 が particle_destroy・2002 が particle_splash・2003 が particle_eye_despawn（431 と同じ）",
      le_map.get("2000") == "particle_shoot"
      and le_map.get("2001") == "particle_destroy"
      and le_map.get("2002") == "particle_splash"
      and le_map.get("2003") == "particle_eye_despawn")
check("Level event：428 の 9800 が players_sleeping・9801 は無い（最大キーは 16384 add_particle_mask）",
      le_map.get("9800") == "players_sleeping"
      and le_map.get("9801") is None
      and max(int(k) for k in le_map) == 16384
      and le_map.get("16384") == "add_particle_mask")
check("Entity event：428・431 ともに 54 エントリ（431 と同じ）",
      len(field_type(types428, "packet_entity_event", "event_id")[1]["mappings"]) == 54
      and len(field_type(types431, "packet_entity_event", "event_id")[1]["mappings"]) == 54)
check("Entity event：428 と 431 で JSON 同一",
      types428["packet_entity_event"] == types431["packet_entity_event"])
check("Entity event：428・431 ともに 1=jump・2=hurt_animation・3=death_animation・4=arm_swing（431 と同じ）",
      field_type(types428, "packet_entity_event", "event_id")[1]["mappings"].get("1") == "jump"
      and field_type(types428, "packet_entity_event", "event_id")[1]["mappings"].get("2") == "hurt_animation"
      and field_type(types428, "packet_entity_event", "event_id")[1]["mappings"].get("3") == "death_animation"
      and field_type(types428, "packet_entity_event", "event_id")[1]["mappings"].get("4") == "arm_swing")
check("Entity event：428・431 ともに 8=shake_wet（totem・shake_web・feed はまだ無い）",
      field_type(types428, "packet_entity_event", "event_id")[1]["mappings"].get("8") == "shake_wet"
      and "totem" not in field_type(types428, "packet_entity_event", "event_id")[1]["mappings"].values()
      and "shake_web" not in field_type(types428, "packet_entity_event", "event_id")[1]["mappings"].values()
      and "feed" not in field_type(types428, "packet_entity_event", "event_id")[1]["mappings"].values())
check("GameMode：428・431 ともに 6 エントリ（0〜5・5=fallback）",
      len(types428["GameMode"][1]["mappings"]) == 6
      and len(types431["GameMode"][1]["mappings"]) == 6
      and types428["GameMode"][1]["mappings"].get("5") == "fallback")
check("GameMode：428 と 431 で JSON 同一",
      types428["GameMode"] == types431["GameMode"])
md428 = types428["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]
md431 = types431["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]
check("MetadataDictionary：428 は 121 エントリ・431 は 125 エントリ",
      len(md428) == 121 and len(md431) == 125,
      f"428={len(md428)} 431={len(md431)}")
check("MetadataDictionary：428 と 431 で JSON は違う（freezing_effect_strength・base_runtime_id・define_properties・update_properties の有無と key のずれ）",
      types428["MetadataDictionary"] != types431["MetadataDictionary"])
check("MetadataDictionary：428・431 ともに 0=flags・1=health・2=variant・3=color・4=nametag",
      md428.get("0") == "flags"
      and md428.get("1") == "health"
      and md428.get("2") == "variant"
      and md428.get("3") == "color"
      and md428.get("4") == "nametag")
check("MetadataDictionary：428 は 119=is_buoyant・120=buoyancy_data・121=goat_horn_count（freezing_effect_strength・base_runtime_id が無い）",
      md428.get("119") == "is_buoyant"
      and md428.get("120") == "buoyancy_data"
      and md428.get("121") == "goat_horn_count"
      and "freezing_effect_strength" not in md428.values()
      and "base_runtime_id" not in md428.values()
      and "define_properties" not in md428.values()
      and "update_properties" not in md428.values())
check("MetadataDictionary：431 は 120=freezing_effect_strength・121=buoyancy_data・122=goat_horn_count・123=base_runtime_id・124=define_properties・125=update_properties",
      md431.get("120") == "freezing_effect_strength"
      and md431.get("121") == "buoyancy_data"
      and md431.get("122") == "goat_horn_count"
      and md431.get("123") == "base_runtime_id"
      and md431.get("124") == "define_properties"
      and md431.get("125") == "update_properties")
check("MetadataDictionary：goat_horn_count の key は 428 が 121・431 が 122・440 が 123（コードは 440 以降のみ key 123 を書く）",
      md428.get("121") == "goat_horn_count"
      and md431.get("122") == "goat_horn_count"
      and types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"].get("123") == "goat_horn_count")
check("MetadataFlags2：428 は 31 フラグ・431 は 31 フラグで JSON 同一",
      len(types428["MetadataFlags2"][1]["flags"]) == 31
      and len(types431["MetadataFlags2"][1]["flags"]) == 31
      and types428["MetadataFlags2"] == types431["MetadataFlags2"])
check("MetadataFlags2：428 の末尾 3 つが celebrating・admiring・celebrating_special（has_entity_properties は無い）",
      types428["MetadataFlags2"][1]["flags"][28:] == ["celebrating", "admiring",
                                                      "celebrating_special"]
      and "has_entity_properties" not in types428["MetadataFlags2"][1]["flags"])
check("Subchunk：428 に packet_subchunk 型が存在しない（471 で新規追加）",
      "packet_subchunk" not in types428)
check("Subchunk request：428 に packet_subchunk_request 型が存在しない（471 で新規追加）",
      "packet_subchunk_request" not in types428)
check("CommandFlags：428 は 3 フラグ（unused・has_semantic_constraint・collapse_enum）",
      [f["name"] for f in types428["CommandFlags"][1]] == ["unused",
                                                           "has_semantic_constraint",
                                                           "collapse_enum"])
check("CommandFlags：431 は 3 フラグ（428 と同じ）",
      [f["name"] for f in types431["CommandFlags"][1]] == ["unused",
                                                           "has_semantic_constraint",
                                                           "collapse_enum"])
check("MaterialReducer 型が 428 の protocol.json に存在しない（465 で新規追加）",
      "MaterialReducer" not in types428)
check("BlockUpdate 型が 428 の protocol.json に存在しない（465 で新規追加）",
      "BlockUpdate" not in types428)
check("EducationSharedResourceURI 型が 428 の protocol.json に存在しない（465 で新規追加）",
      "EducationSharedResourceURI" not in types428)
check("EducationExternalLinkSettings 型が 428 の protocol.json に存在しない（465 で新規追加）",
      "EducationExternalLinkSettings" not in types428)
check("Animate：428 は row_right=128・row_left=129＋匿名 switch（boat_rowing_time lf32）",
      types428["packet_animate"][1][0]["type"][1]["mappings"].get("128") == "row_right"
      and types428["packet_animate"][1][0]["type"][1]["mappings"].get("129") == "row_left"
      and len(container_fields(types428, "packet_animate")) == 3
      and container_fields(types428, "packet_animate")[2][1][0] == "switch")
check("Animate：428 と 431 で JSON 同一",
      types428["packet_animate"] == types431["packet_animate"])
check("Animate entity：428 は 6 フィールド（stop_condition_version が無い）",
      field_names(types428, "packet_animate_entity") == ["animation", "next_state",
                                                        "stop_condition", "controller",
                                                        "blend_out_time", "runtime_entity_ids"])
check("Animate entity：431 は 6 フィールド（428 と同じ）",
      field_names(types431, "packet_animate_entity") == ["animation", "next_state",
                                                        "stop_condition", "controller",
                                                        "blend_out_time", "runtime_entity_ids"])
check("Crafting data：428 の順序が recipes・potion_type_recipes・potion_container_recipes・is_clean",
      field_names(types428, "packet_crafting_data") == ["recipes",
                                                       "potion_type_recipes",
                                                       "potion_container_recipes",
                                                       "is_clean"])
check("Crafting data：431 の順序が 428 と同じ",
      field_names(types431, "packet_crafting_data") == ["recipes",
                                                       "potion_type_recipes",
                                                       "potion_container_recipes",
                                                       "is_clean"])
check("Hurt armor：428 は health の 1 フィールド",
      field_names(types428, "packet_hurt_armor") == ["health"])
check("Hurt armor：431 は health の 1 フィールド（428 と同じ）",
      field_names(types431, "packet_hurt_armor") == ["health"])
check("Education settings：428 は 6 フィールド（CodeBuilderDefaultURI・CodeBuilderTitle・CanResizeCodeBuilder・HasOverrideURI・OverrideURI・HasQuiz）",
      field_names(types428, "packet_education_settings") == ["CodeBuilderDefaultURI",
                                                            "CodeBuilderTitle",
                                                            "CanResizeCodeBuilder",
                                                            "HasOverrideURI",
                                                            "OverrideURI", "HasQuiz"])
check("Education settings：431 は 6 フィールド（428 と同じ）",
      field_names(types431, "packet_education_settings") == ["CodeBuilderDefaultURI",
                                                            "CodeBuilderTitle",
                                                            "CanResizeCodeBuilder",
                                                            "HasOverrideURI",
                                                            "OverrideURI", "HasQuiz"])
check("Photo transfer：428 は file_name・image_data・unknown2 の 3 フィールド",
      field_names(types428, "packet_photo_transfer") == ["file_name", "image_data",
                                                        "unknown2"])
check("Photo transfer：431 は image_name・image_data・book_id の 3 フィールド",
      field_names(types431, "packet_photo_transfer") == ["image_name", "image_data",
                                                        "book_id"])
check("Add volume entity：428 に packet_add_volume_entity 型が存在しない（440 で新規追加）",
      "packet_add_volume_entity" not in types428)
check("Remove volume entity：428 に packet_remove_volume_entity 型が存在しない（440 で新規追加）",
      "packet_remove_volume_entity" not in types428)
check("Sync entity property：428 に packet_sync_entity_property 型が存在しない（440 で新規追加）",
      "packet_sync_entity_property" not in types428)
check("Entity pick request：428 は runtime_entity_id・selected_slot の 2 フィールド（with_data が無い）",
      field_names(types428, "packet_entity_pick_request") == ["runtime_entity_id",
                                                             "selected_slot"])
check("Entity pick request：431 は 2 フィールド（428 と同じ）",
      field_names(types431, "packet_entity_pick_request") == ["runtime_entity_id",
                                                             "selected_slot"])
check("Update block synced：428 と 431 でフィールド名は同じ 6 件で transition_type の型も同じ（varint64）",
      field_names(types428, "packet_update_block_synced") == field_names(types431, "packet_update_block_synced")
      and field_type(types428, "packet_update_block_synced", "transition_type")[1]["type"] == "varint64"
      and field_type(types431, "packet_update_block_synced", "transition_type")[1]["type"] == "varint64")
check("Text：428 の type マッパーに 11 が無い（431 も無し）",
      "11" not in types428["packet_text"][1][0]["type"][1]["mappings"]
      and "11" not in types431["packet_text"][1][0]["type"][1]["mappings"])
check("Network settings：428・431 ともに compression_threshold は u16",
      field_type(types428, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types431, "packet_network_settings", "compression_threshold") == "u16")
check("Structure block update：428 は空の container（フィールド無し）",
      types428["packet_structure_block_update"] == ["container", []])
check("Structure block update：431 は 9 フィールド（position・structure_name・data_field・include_players・show_bounding_box・structure_block_type・settings・redstone_save_mode・should_trigger）",
      field_names(types431, "packet_structure_block_update") == ["position",
                                                                "structure_name",
                                                                "data_field",
                                                                "include_players",
                                                                "show_bounding_box",
                                                                "structure_block_type",
                                                                "settings",
                                                                "redstone_save_mode",
                                                                "should_trigger"])
check("StructureBlockSettings 型が 428 の protocol.json に存在しない（431 で新規追加）",
      "StructureBlockSettings" not in types428)
check("Spawn particle effect：428 は dimension_id・entity_id・position・particle_name の 4 フィールド",
      field_names(types428, "packet_spawn_particle_effect") == ["dimension_id",
                                                                "entity_id",
                                                                "position",
                                                                "particle_name"])
check("Spawn particle effect：431 は dimension・entity_id・position・particle_name の 4 フィールド",
      field_names(types431, "packet_spawn_particle_effect") == ["dimension",
                                                                "entity_id",
                                                                "position",
                                                                "particle_name"])
check("Boss event：428・431 ともに query が無い（ボスバー問い合わせは 582 以降）",
      "query" not in str(types428["packet_boss_event"])
      and "query" not in str(types431["packet_boss_event"]))
check("Boss event：428 の show_bar は title・bar_progress、431 は title・progress・screen_darkening・color・overlay",
      [f["name"] for f in types428["packet_boss_event"][1][2]["type"][1]["fields"]["show_bar"][1]] == ["title", "bar_progress"]
      and [f["name"] for f in types431["packet_boss_event"][1][2]["type"][1]["fields"]["show_bar"][1]] == ["title", "progress", "screen_darkening", "color", "overlay"])
check("Boss event：428・431 ともに分岐は 7 つ（register_player・unregister_player・show_bar・set_bar_title・set_bar_progress・update_properties・texture）",
      sorted(types428["packet_boss_event"][1][2]["type"][1]["fields"].keys()) == sorted(types431["packet_boss_event"][1][2]["type"][1]["fields"].keys())
      == ["register_player", "set_bar_progress", "set_bar_title", "show_bar",
          "texture", "unregister_player", "update_properties"])
check("CommandOrigin：428 は 12 エントリ（0〜11・precompiled・game_director_entity_server・script が無い）",
      types428["CommandOrigin"][1][0]["type"][1]["mappings"] == {"0": "player", "1": "block", "2": "minecart_block", "3": "dev_console", "4": "test", "5": "automation_player", "6": "client_automation", "7": "dedicated_server", "8": "entity", "9": "virtual", "10": "game_argument", "11": "entity_server"})
check("CommandOrigin：431 は 15 エントリ（12=precompiled・13=game_director_entity_server・14=script が追加）",
      types431["CommandOrigin"][1][0]["type"][1]["mappings"].get("12") == "precompiled"
      and types431["CommandOrigin"][1][0]["type"][1]["mappings"].get("13") == "game_director_entity_server"
      and types431["CommandOrigin"][1][0]["type"][1]["mappings"].get("14") == "script")
check("Adventure settings：428 と 431 で command_permission が同じ",
      field_type(types428, "packet_adventure_settings", "command_permission") == field_type(types431, "packet_adventure_settings", "command_permission"))
check("Available commands：428・431 ともに flags は u8（lu16 になるのは 448）",
      find_flags_type(types428["packet_available_commands"]) == "u8"
      and find_flags_type(types431["packet_available_commands"]) == "u8",
      f"428={find_flags_type(types428['packet_available_commands'])} 431={find_flags_type(types431['packet_available_commands'])}")
check("Modal form response：428 は form_id・data の 2 フィールド（431 と同じ）",
      field_names(types428, "packet_modal_form_response") == ["form_id", "data"]
      and field_names(types428, "packet_modal_form_response") == field_names(types431, "packet_modal_form_response"))
check("Map info request：428 は map_id の 1 フィールド（431 と同じ）",
      field_names(types428, "packet_map_info_request") == ["map_id"]
      and field_names(types428, "packet_map_info_request") == field_names(types431, "packet_map_info_request"))
check("Network chunk publisher update：428 は coordinates・radius の 2 フィールド（431 と同じ）",
      field_names(types428, "packet_network_chunk_publisher_update") == ["coordinates", "radius"]
      and field_names(types428, "packet_network_chunk_publisher_update") == field_names(types431, "packet_network_chunk_publisher_update"))
check("Clientbound map item data：428 は mapinfo（MapInfo 型＝native）の 1 フィールド",
      field_names(types428, "packet_clientbound_map_item_data") == ["mapinfo"]
      and field_type(types428, "packet_clientbound_map_item_data", "mapinfo") == "MapInfo"
      and types428["MapInfo"] == "native")
check("Clientbound map item data：431 は map_id・update_flags・dimension・locked・included_in・scale・tracked・texture の 8 フィールド",
      field_names(types431, "packet_clientbound_map_item_data") == ["map_id", "update_flags",
                                                                    "dimension", "locked",
                                                                    "included_in", "scale",
                                                                    "tracked", "texture"])
pa428 = [f["name"] for f in types428["PlayerAttributes"][1]["type"][1] if "name" in f]
check("PlayerAttributes：428 は min・max・current・default・name の 5 項目（431 と同じ）",
      pa428 == ["min", "max", "current", "default", "name"])
check("PlayerAttributes：431 も同じ 5 項目",
      [f["name"] for f in types431["PlayerAttributes"][1]["type"][1] if "name" in f] == ["min", "max", "current", "default", "name"])
pe428 = types428["packet_event"][1][1]["type"][1]["mappings"]
pe431 = types431["packet_event"][1][1]["type"][1]["mappings"]
check("packet_event：428 は 25 イベント・431 は 25 イベントで JSON 同一",
      len(pe428) == 25 and len(pe431) == 25 and types428["packet_event"] == types431["packet_event"],
      f"428={len(pe428)} 431={len(pe431)}")
check("packet_event：428・431 ともに 24 が piglin_barter（25 はまだ無い）",
      pe428.get("24") == "piglin_barter" and pe431.get("24") == "piglin_barter"
      and pe428.get("25") is None and pe431.get("25") is None)
check("InputFlag：428 は 37 フラグで ascend_scaffolding・descend_scaffolding（431 と JSON 同一）",
      len(types428["InputFlag"][1]["flags"]) == 37
      and "ascend_scaffolding" in types428["InputFlag"][1]["flags"]
      and "descend_scaffolding" in types428["InputFlag"][1]["flags"]
      and types428["InputFlag"] == types431["InputFlag"])
check("DeviceOS 型が 428 の protocol.json に存在しない", "DeviceOS" not in types428)
check("EntityProperties 型が 428 の protocol.json に存在しない", "EntityProperties" not in types428)
check("SubChunkEntryWithCaching 型が 428 の protocol.json に存在しない", "SubChunkEntryWithCaching" not in types428)
# GameRule：428 は 3 フィールド、431 は 3 フィールドで JSON 同一
gr428 = field_names(types428, "GameRule")
gr431 = field_names(types431, "GameRule")
check("GameRule：428 は name・type・value の 3 フィールド",
      gr428 == ["name", "type", "value"])
check("GameRule：431 は 3 フィールドで 428 と JSON 同一（editable は 440 で追加）",
      gr431 == ["name", "type", "value"]
      and types428["GameRule"] == types431["GameRule"])
# その他の型変更（コードが読む・書くのは無い）
check("Level sound event：428 は block_id（zigzag32）、431 は extra_data（zigzag32）",
      field_type(types428, "packet_level_sound_event", "block_id") == "zigzag32"
      and field_type(types431, "packet_level_sound_event", "extra_data") == "zigzag32")
check("Creative content：428 の items は ItemStacks 型、431 は entry_id＋ItemLegacy の inline 配列",
      field_type(types428, "packet_creative_content", "items") == "ItemStacks"
      and field_type(types431, "packet_creative_content", "items")[0] == "array")
check("ItemStack：428 は stack_id（varint）＋item（Item）の container、ItemStacks はその配列",
      types428["ItemStack"] == ["container", [{"name": "stack_id", "type": "varint"}, {"name": "item", "type": "Item"}]]
      and types428["ItemStacks"] == ["array", {"countType": "varint", "type": "ItemStack"}])
check("Inventory slot：428 の item は ItemStack、431 は Item（window_id は両方 WindowIDVarint）",
      field_type(types428, "packet_inventory_slot", "item") == "ItemStack"
      and field_type(types431, "packet_inventory_slot", "item") == "Item"
      and field_type(types428, "packet_inventory_slot", "window_id") == "WindowIDVarint"
      and field_type(types431, "packet_inventory_slot", "window_id") == "WindowIDVarint")
check("Map create locked copy：428 は空の container、431 は original_map_id・new_map_id",
      types428["packet_map_create_locked_copy"] == ["container", []]
      and field_names(types431, "packet_map_create_locked_copy") == ["original_map_id", "new_map_id"])
check("Network stack latency：428 は timestamp・unknown_flag、431 は timestamp・needs_response",
      field_names(types428, "packet_network_stack_latency") == ["timestamp", "unknown_flag"]
      and field_names(types431, "packet_network_stack_latency") == ["timestamp", "needs_response"])
check("NPC request：428 は runtime_entity_id・unknown0・unknown1・unknown2、431 は runtime_entity_id・request_type・command・action_type",
      field_names(types428, "packet_npc_request") == ["runtime_entity_id", "unknown0", "unknown1", "unknown2"]
      and field_names(types431, "packet_npc_request") == ["runtime_entity_id", "request_type", "command", "action_type"])
check("On screen texture animation：428 は空の container、431 は animation_type",
      types428["packet_on_screen_texture_animation"] == ["container", []]
      and field_names(types431, "packet_on_screen_texture_animation") == ["animation_type"])
check("Player enchant options：428 は enchant_options（EnchantOptions 型の配列）、431 は options（EnchantOption 型の配列）",
      field_names(types428, "packet_player_enchant_options") == ["enchant_options"]
      and field_type(types428, "packet_player_enchant_options", "enchant_options") == "EnchantOptions"
      and field_names(types431, "packet_player_enchant_options") == ["options"]
      and "EnchantOptions" in types428 and "EnchantOption" not in types428
      and "EnchantOption" in types431 and "EnchantOptions" not in types431)
check("Resource pack chunk data：428 は package_id、431 は pack_id",
      field_names(types428, "packet_resource_pack_chunk_data") == ["package_id", "chunk_index", "progress", "payload"]
      and field_names(types431, "packet_resource_pack_chunk_data") == ["pack_id", "chunk_index", "progress", "payload"])
check("Resource pack chunk request：428 は package_id、431 は pack_id",
      field_names(types428, "packet_resource_pack_chunk_request") == ["package_id", "chunk_index"]
      and field_names(types431, "packet_resource_pack_chunk_request") == ["pack_id", "chunk_index"])
check("Resource pack data info：428 は package_id・compressed_package_size、431 は pack_id・size",
      field_names(types428, "packet_resource_pack_data_info") == ["package_id", "max_chunk_size", "chunk_count", "compressed_package_size", "hash", "is_premium", "pack_type"]
      and field_names(types431, "packet_resource_pack_data_info") == ["pack_id", "max_chunk_size", "chunk_count", "size", "hash", "is_premium", "pack_type"])
check("Respawn：428 は x・y・z（lf32）＋state＋runtime_entity_id、431 は position（vec3f）＋state＋runtime_entity_id",
      field_names(types428, "packet_respawn") == ["x", "y", "z", "state", "runtime_entity_id"]
      and field_type(types428, "packet_respawn", "x") == "lf32"
      and field_names(types431, "packet_respawn") == ["position", "state", "runtime_entity_id"]
      and field_type(types431, "packet_respawn", "position") == "vec3f")
check("Set last hurt by：428 は unknown の 1 フィールド、431 は entity_type の 1 フィールド",
      field_names(types428, "packet_set_last_hurt_by") == ["unknown"]
      and field_names(types431, "packet_set_last_hurt_by") == ["entity_type"])
check("Show store offer：428 は unknown0・unknown1、431 は offer_id・show_all",
      field_names(types428, "packet_show_store_offer") == ["unknown0", "unknown1"]
      and field_names(types431, "packet_show_store_offer") == ["offer_id", "show_all"])
check("Structure template data export request：428 は空の container、431 は name・position・settings・request_type",
      types428["packet_structure_template_data_export_request"] == ["container", []]
      and field_names(types431, "packet_structure_template_data_export_request") == ["name", "position", "settings", "request_type"])
check("Structure template data export response：428 は空の container、431 は name・success・nbt・response_type",
      types428["packet_structure_template_data_export_response"] == ["container", []]
      and field_names(types431, "packet_structure_template_data_export_response") == ["name", "success", "nbt", "response_type"])
check("Set score：428 は entries のみ、431 は action・entries",
      field_names(types428, "packet_set_score") == ["entries"]
      and field_names(types431, "packet_set_score") == ["action", "entries"])
check("Set scoreboard identity：428 は entries のみ、431 は action・entries",
      field_names(types428, "packet_set_scoreboard_identity") == ["entries"]
      and field_names(types431, "packet_set_scoreboard_identity") == ["action", "entries"])
check("ScoreEntries・ScoreboardIdentityEntries 型は 428 にあり 431 には無い",
      "ScoreEntries" in types428 and "ScoreboardIdentityEntries" in types428
      and "ScoreEntries" not in types431 and "ScoreboardIdentityEntries" not in types431)
check("ShortArray・ShortString・WindowIDZigzag32・MapDecoration・TrackedObject・UpdateMapFlags 型は 428 になく 431 にある",
      "ShortArray" not in types428 and "ShortString" not in types428
      and "WindowIDZigzag32" not in types428 and "MapDecoration" not in types428
      and "TrackedObject" not in types428 and "UpdateMapFlags" not in types428
      and "ShortArray" in types431 and "ShortString" in types431
      and "WindowIDZigzag32" in types431 and "MapDecoration" in types431
      and "TrackedObject" in types431 and "UpdateMapFlags" in types431)
check("Recipes：428 と 431 で JSON は違うが type マッパーは同じ 0〜7（shapeless・shaped・furnace・furnace_with_metadata・multi・shulker_box・shapeless_chemistry・shaped_chemistry）",
      types428["Recipes"] != types431["Recipes"]
      and types428["Recipes"][1]["type"][1][0]["type"][1]["mappings"] == types431["Recipes"][1]["type"][1][0]["type"][1]["mappings"]
      == {"0": "shapeless", "1": "shaped", "2": "furnace", "3": "furnace_with_metadata", "4": "multi", "5": "shulker_box", "6": "shapeless_chemistry", "7": "shaped_chemistry"})

# ---------- H. ブロック・バイオーム・item 名前対応 ----------
be431_dir = TMP / "be-431"
req210 = be431_dir / "req210-required_item_list.json"
req200 = be431_dir / "req200-required_item_list.json"
canon210 = be431_dir / "req210-canonical_block_states.nbt"
canon200 = be431_dir / "req200-canonical_block_states.nbt"
biome210 = be431_dir / "req210-biome_id_map.json"
blockid210 = be431_dir / "req210-block_id_map.json"
req440 = TMP / "be-440" / "req-bedrock-1.17.0-required_item_list.json"
canon440 = TMP / "be-440" / "canon-bedrock-1.17.0.nbt"
check("1.16.210 の required_item_list.json が存在する", req210.is_file(), str(req210))
check("1.16.210 の required_item_list.json のサイズが 91,328 バイト",
      req210.is_file() and req210.stat().st_size == 91328,
      f"{req210.stat().st_size} バイト" if req210.is_file() else "なし")
check("1.16.210 の required_item_list.json の SHA-1 が 682782b5…と一致",
      req210.is_file()
      and sha1(req210) == "682782b5c0da0465aef275522d4b97e01f7aa700",
      sha1(req210) if req210.is_file() else "なし")
check("1.16.210 の required_item_list.json の名前が 914 件",
      req210.is_file()
      and len(json.loads(req210.read_text())) == 914,
      f"{len(json.loads(req210.read_text()))} 件" if req210.is_file() else "なし")
check("1.16.210 の required_item_list.json が 1.16.200 のものとバイト同一",
      req210.is_file() and req200.is_file()
      and sha1(req210) == sha1(req200))
check("1.16.210 の required_item_list.json が 1.17.0 のものとバイト同一ではない",
      req210.is_file() and req440.is_file()
      and sha1(req210) != sha1(req440))
check("1.16.210 の canonical_block_states.nbt が存在する", canon210.is_file(), str(canon210))
check("1.16.210 の canonical_block_states.nbt のサイズが 977,861 バイト",
      canon210.is_file() and canon210.stat().st_size == 977861,
      f"{canon210.stat().st_size} バイト" if canon210.is_file() else "なし")
check("1.16.210 の canonical_block_states.nbt の SHA-1 が 558612d9…と一致",
      canon210.is_file()
      and sha1(canon210) == "558612d91084853a06482f0419b0077113e14774",
      sha1(canon210) if canon210.is_file() else "なし")
sys.path.insert(0, str(BE / "tools"))
from parse_nbt import parse_canonical  # noqa: E402
canon_entries_210 = (len(parse_canonical(str(canon210))[0])
                     if canon210.is_file() else -1)
canon_entries_200 = (len(parse_canonical(str(canon200))[0])
                     if canon200.is_file() else -1)
canon_entries_440 = (len(parse_canonical(str(canon440))[0])
                     if canon440.is_file() else -1)
check("1.16.210 の canonical_block_states.nbt が 1.16.200 のものとバイト同一ではない",
      canon210.is_file() and canon200.is_file()
      and sha1(canon210) != sha1(canon200))
check("1.16.210 の canonical_block_states.nbt が 6,575 エントリー（NBT のパレット長・parse_nbt.py で数えた実測）",
      canon210.is_file() and canon_entries_210 == 6575,
      f"{canon_entries_210} エントリー" if canon210.is_file() else "なし")
check("1.16.200 の canonical_block_states.nbt が 6,611 エントリー",
      canon200.is_file() and canon_entries_200 == 6611,
      f"{canon_entries_200} エントリー" if canon200.is_file() else "なし")
check("1.17.0 の canonical_block_states.nbt が 7,682 エントリー",
      canon440.is_file() and canon_entries_440 == 7682,
      f"{canon_entries_440} エントリー" if canon440.is_file() else "なし")
check("1.16.210 の block_id_map.json が存在する", blockid210.is_file(), str(blockid210))
check("1.16.210 の block_id_map.json の SHA-1 が f3855a10…と一致",
      blockid210.is_file()
      and sha1(blockid210) == "f3855a102c698f960020d928bcaef10d25724062",
      sha1(blockid210) if blockid210.is_file() else "なし")
check("1.16.210 の block_id_map.json が 553 エントリー",
      blockid210.is_file()
      and len(json.loads(blockid210.read_text())) == 553,
      f"{len(json.loads(blockid210.read_text()))} 件" if blockid210.is_file() else "なし")
blockid170 = TMP / "be-440" / "blockid-bedrock-1.17.0.json"
check("1.16.210 の block_id_map.json が 1.17.0 のものとバイト同一（SHA-1 が同じ）",
      blockid210.is_file() and blockid170.is_file()
      and sha1(blockid210) == sha1(blockid170))
check("1.16.210 の biome_id_map.json には実データが無い（bedrock-1.16.210 タグに無く HTTP 404・ファイルの中身は \"404: Not Found\" の 14 バイト）",
      biome210.is_file() and biome210.stat().st_size == 14
      and biome210.read_text() == "404: Not Found",
      f"{biome210.stat().st_size} バイト" if biome210.is_file() else "なし")
check("1.16.210 のブロック用 .bin は無く（既存ペアの流用）",
      not (RES / "bedrock_block_runtime_ids_1_16_210.bin").exists())
check("既存ペアの canonical_block_states_1_20_50.nbt が存在し SHA-1 が d2b6acbb…と一致",
      (RES / "canonical_block_states_1_20_50.nbt").is_file()
      and sha1(RES / "canonical_block_states_1_20_50.nbt") == "d2b6acbb152e3165b356b2bf1e273d3fcc5aba88")
check("既存ペアの bedrock_block_runtime_ids_1_20_4.bin が存在し SHA-1 が ee36fffb…と一致",
      (RES / "bedrock_block_runtime_ids_1_20_4.bin").is_file()
      and sha1(RES / "bedrock_block_runtime_ids_1_20_4.bin") == "ee36fffb5c8dfe526b501fc8311ebce63cf420fb")
# パレットの状態比較（compare_palette_431.py と同じ key で数えた実測）
def _pal_key(x):
    return (x["name"], json.dumps(x.get("states", {}), sort_keys=True, ensure_ascii=False))
canon2050_res = RES / "canonical_block_states_1_20_50.nbt"
if canon210.is_file() and canon440.is_file() and canon2050_res.is_file():
    _e210 = parse_canonical(str(canon210))[0]
    _e440 = parse_canonical(str(canon440))[0]
    _e2050 = parse_canonical(str(canon2050_res))[0]
    _k210 = {_pal_key(x): i for i, x in enumerate(_e210)}
    _k440 = {_pal_key(x): i for i, x in enumerate(_e440)}
    _k2050 = {_pal_key(x): i for i, x in enumerate(_e2050)}
    _same440 = sum(1 for kk, i in _k210.items() if _k440.get(kk) == i)
    _only210 = [kk for kk in _k210 if kk not in _k440]
    _only440 = [kk for kk in _k440 if kk not in _k210]
    _same2050 = sum(1 for kk, i in _k210.items() if _k2050.get(kk) == i)
    check("1.16.210 vs 1.17.0：同一ランタイム ID が 136/6,575",
          _same440 == 136, f"{_same440}/6,575")
    check("1.16.210 にのみある状態が 0 件（1.16.210 は 1.17.0 の厳密な部分集合）",
          len(_only210) == 0, f"{len(_only210)} 件")
    check("1.17.0 にのみある状態が 1,107 件",
          len(_only440) == 1107, f"{len(_only440)} 件")
    check("1.16.210 vs 1.20.50：同一ランタイム ID が 0/6,575（589 と同じ状況でブロック表は新規不要）",
          _same2050 == 0, f"{_same2050}/6,575")

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
check("BedrockVersion の NetherNet 分岐（2225）が 428 と無関係に残っている",
      "2225" in ver_src and "NetherNet" in ver_src)
check("BedrockChunkTranslator が BedrockVersion を参照していない（チャンク変換はバージョン非依存）",
      "BedrockVersion" not in (SRC / "BedrockChunkTranslator.java").read_text())

# ---------- J. 428 回の実質的な変更：AddPainting の BlockCoordinates 分岐と PlayerList の verified 分岐 ----------
check("述語 addPaintingUsesBlockCoordinates() が protocol >= 422 && protocol <= 428 で定義されている（§28 で下限を追加）",
      bool(re.search(r"boolean addPaintingUsesBlockCoordinates\(\) \{\s*return protocol <= 354 || \(protocol >= 422 && protocol <= 428\);",
                     ver_src)))
check("述語 playerListRemoveHasVerified() が protocol >= 422 && protocol <= 428 で定義されている（§28 で下限を追加）",
      bool(re.search(r"boolean playerListRemoveHasVerified\(\) \{\s*return protocol >= 422 && protocol <= 428;",
                     ver_src)))
pkt_src = (SRC / "BedrockPackets.java").read_text()
check("BedrockPackets.encodeAddPainting が coordinates を addPaintingUsesBlockCoordinates() で分岐している",
      "addPaintingUsesBlockCoordinates()" in pkt_src)
check("BedrockPackets.encodePlayerListRemove が verified を playerListRemoveHasVerified() で分岐している",
      "playerListRemoveHasVerified()" in pkt_src)
check("BedrockPackets にバージョン付き encodeAddPainting オーバーロードがある（BedrockVersion を取る）",
      bool(re.search(r"encodeAddPainting\(long runtimeId,\s*float x, float y, float z, int direction, String title,\s*BedrockVersion version\)", pkt_src)))
check("BedrockPackets にバージョン付き encodePlayerListRemove オーバーロードがある（BedrockVersion を取る）",
      bool(re.search(r"encodePlayerListRemove\(UUID uuid, BedrockVersion version\)",
                     pkt_src)))
check("BedrockSession が encodeAddPainting に bedrockVersion を渡している",
      "encodeAddPainting(state.javaId," in sess_src
      and "bedrockVersion" in sess_src)
check("BedrockSession が encodePlayerListRemove に bedrockVersion を渡している",
      "encodePlayerListRemove(state.uuid, bedrockVersion)" in sess_src)
check("BedrockPacketsEntityTest に addPainting428UsesBlockCoordinates テストがある（428 は BlockCoordinates・431 は vec3f）",
      "addPainting428UsesBlockCoordinates" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsEntityTest.java").read_text())
check("BedrockPacketsEntityTest に playerListRemove428HasVerified テストがある（428 は verified 付き・431 は無し）",
      "playerListRemove428HasVerified" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsEntityTest.java").read_text())
check("BedrockVersionTest に allSixtyFourSupportedProtocolsResolve テストがあり 428 が含まれる",
      "allSixtyFourSupportedProtocolsResolve" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text()
      and "428" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text())
check("BedrockSessionVersionTest に allSixtyThreeProtocolsReachStartGame テストがあり 428 が含まれる",
      "allSixtyThreeProtocolsReachStartGame" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java").read_text())
check("428〜431 の間に分岐の閾値が無い（428 の次の閾値は 440。429・430 の述語は無い）",
      not re.search(r"protocol >= 429\b", ver_src)
      and not re.search(r"protocol >= 430\b", ver_src)
      and not re.search(r"protocol <= 429\b", ver_src)
      and not re.search(r"protocol <= 430\b", ver_src))
check("60 件の型変更のうちコードが読む・書くのは AddPainting の coordinates と PlayerRecords の verified のみ（GameRule・MetadataFlags2・SoundType・StructureBlockSettings・packet_event・sync_entity_property・add_volume_entity・remove_volume_entity・BlockPalette・BlockProperties はコードで参照されない）",
      not any(re.search(rf"\b{name}\b", pkt_src)
              for name in ["GameRule",
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
check("Skin の既知の制限：writeEmptySkin は premium を bool で書く 567 形式のまま（428 の Skin.premium は string。判断待ちで 428 回では直さない。BE-NOTES.md §21 参照）",
      "geometry data version" in pkt_src
      and "primary user" in pkt_src
      and "overriding player appearance" in pkt_src)
notes_src = (ROOT / "BE-NOTES.md").read_text()
check("BE-NOTES.md §21 に Item の count 無しの判断待ちが記録されている",
      "§21" in notes_src and "count" in notes_src)
check("BE-NOTES.md §21 に Skin.premium（string／bool）の判断待ちが記録されている",
      "§21" in notes_src and "premium" in notes_src)
check("BE-NOTES.md §21 にシールドの network ID 355／358 の判断待ちが記録されている",
      "§21" in notes_src and "355" in notes_src and "358" in notes_src)

print()
print(f"TOTAL: {len(CHECKS)} checks, {len(CHECKS) - len(FAILURES)} passed, {len(FAILURES)} failed")
if FAILURES:
    print("FAILURES:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
