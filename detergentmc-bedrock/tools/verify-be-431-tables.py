#!/usr/bin/env python3
"""Bedrock 1.16.221（プロトコル 431）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py /
verify-be-534-tables.py / verify-be-527-tables.py /
verify-be-503-tables.py / verify-be-486-tables.py / verify-be-475-tables.py /
verify-be-471-tables.py / verify-be-465-tables.py / verify-be-448-tables.py /
verify-be-440-tables.py
にならい、表・定数・述語の前提を機械計数で確認する。すべて実ファイル
（BedrockVersion.java / BedrockProtocol.java / BedrockSession.java /
protocol-1.16.220.json / protocol-1.17.0.json / protocol-1.17.10.json /
protocol-1.17.30.json / protocol-1.17.40.json / protocol-1.18.0.json /
protocol-1.18.11.json / protocol-1.18.30.json / protocol-1.19.1.json /
protocol-1.19.10.json / protocol-1.19.20.json / protocol-1.19.30.json /
protocol-1.19.40.json / protocol-1.19.50.json / .bin データ）に対する
チェックで、推測はない。

- 431 の protocol.json は minecraft-data の `data/bedrock/1.16.220/` にある。
- 431 は 1.16.220 / 1.16.221 で共有するプロトコル（Minecraft Wiki
  「Bedrock Edition 1.16.220」「Bedrock Edition 1.16.221」個別ページの
  infobox「Protocol version 431」・protocolVersions.json では 431＝1.16.220
  のみ。displayName は共有するうち最新の 1.16.221（440 の V1_17_2 と
  同じ流儀）。minecraft-data に 1.16.221 の protocol.json は無く
  （HTTP 404）、431 の代表ディレクトリは 1.16.220 のみ。
  BE-NOTES.md §20）。
- 431 と 440 の差分は追加 3（165 sync_entity_property・166
  add_volume_entity・167 remove_volume_entity）・削除 0・リネーム 0・
  型変更 11 件。StartGame は 431 が 59 フィールド、440 が 60 フィールド
  で、440 は末尾に engine を追加するだけ（431 の一覧＝440 の一覧から
  engine を除いたものと一致）。add_player・add_entity・
  set_entity_data・ResourcePacksInfo・ResourcePackStack も 431＝440 で
  JSON 完全一致。LevelEvent 88 件・InputFlag 37 フラグも JSON 完全一致
  （431 回のプロトコル調査の「InputFlag 36 フラグ」は誤りで、実測は
  431・440 ともに 37 フラグ。440 回と同じ訂正）。
- 431 の 1 つ下は 428（1.16.210）。432〜439 にエントリは無い
  （433／434／435／437 はベータ版の番号、432／436／438／439 は Wiki 表に
  エントリ無しで未確認。BE-NOTES.md §20）。
- アイテム表は新規生成が必要（bedrock_item_ids_1_16_210.bin）。
  bedrock-1.16.220 タグは存在しない（required_item_list.json・
  canonical_block_states.nbt・block_id_map.json・biome_id_map.json の
  4 ファイルすべて HTTP 404）。代用は最寄りの下位タグ
  bedrock-1.16.210（プロトコル 428）の実測データで、1.16.220 自体の
  実測ではない（554 が 1_19_40 の表を共用するのと同じく、ファイル名は
  データの出所 1.16.210 に合わせた）。1.16.210 のレジストリは 914 名で、
  1.16.200 のものとバイト同一。候補表は exact=624／shared=236／
  legacy=33／fallback=419、SHA-1 `a35eaa5a…`。1_17_0 の表とは
  369/1312 行が違うため共用できない（BE-NOTES.md §20）。
- ブロックパレットは 1.16.210 のものが 6,575 エントリーで、1.17.0 の
  パレットの厳密な部分集合（同一ランタイム ID は 136/6,575、1.16.210 に
  のみある状態は 0 件）。それでもブロック表は新規不要（既存ペアを流用。
  589 と同じ根拠。1.16.210 と 1.20.50 の同一ランタイム ID は 0/6,575。
  BE-NOTES.md §20）。biome_id_map.json は bedrock-1.16.210 タグに
  存在しない（HTTP 404・取得したファイルの中身は "404: Not Found" の
  14 バイト）が、設計上使っていないため要否判断に影響しない。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 431 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 431 回の実質的な変更は 2 件：**StartGame の engine を 440 以降のみ
  書く**（hasStartGameEngine() = protocol >= 440。431 の StartGame は
  59 フィールドで engine が無い）と、**ヤギの角の数（goat_horn_count）
  を 440 以降のみ書く**（hasGoatHornCountKey() = protocol >= 440。
  431 の MetadataDictionary の key 123 は base_runtime_id で、440 で
  goat_horn_count になる）。それ以外の 11 件の型変更のうちコードが
  読む・書くのは無い（GameRule は件数 0 で送るため editable を書かない。
  Skin は writeEmptySkin が 567 形式のまま。§J 参照）。

使い方: python3 detergentmc-bedrock/tools/verify-be-431-tables.py
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


# ---------- A. protocol.json（431）の同一性 ----------
p31 = TMP / "protocol-1.16.220.json"
check("431 protocol.json が存在する", p31.is_file(), str(p31))
check("431 protocol.json の行数が 9,002 行（wc -l、改行数）",
      p31.is_file()
      and p31.read_text().count("\n") == 9002,
      f"{p31.read_text().count(chr(10))} 行" if p31.is_file() else "なし")
check("431 protocol.json のサイズが 232,353 バイト",
      p31.is_file() and p31.stat().st_size == 232353,
      f"{p31.stat().st_size} バイト" if p31.is_file() else "なし")
check("431 protocol.json の SHA-1 が a51a86a9…と一致",
      p31.is_file()
      and sha1(p31) == "a51a86a9e60a583ca6746bf65676d096a735c688",
      sha1(p31) if p31.is_file() else "なし")

data431 = json.loads(p31.read_text())
types431 = data431["types"]
ids431 = packet_ids(data431)

# ---------- A2. 隣接バージョン（440・448・465・471・475・486・503・527・534・544・554・557・560）の protocol.json ----------
p40 = TMP / "protocol-1.17.0.json"
check("440 protocol.json（上側の隣接）が存在する", p40.is_file(), str(p40))
check("440 protocol.json の行数が 9,115 行・サイズが 235,230 バイト・SHA-1 が 9958534f…と一致",
      p40.is_file() and p40.read_text().count("\n") == 9115
      and p40.stat().st_size == 235230
      and sha1(p40) == "9958534fd0f90f0a211044cd525639295458a7bb",
      f"{p40.read_text().count(chr(10))} 行・{p40.stat().st_size} バイト" if p40.is_file() else "なし")
check("431 の protocol.json は 440 のものとバイト同一ではない（SHA-1 が違う）",
      p31.is_file() and p40.is_file()
      and sha1(p31) != sha1(p40))
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
    check("protocolVersions.json に 432〜439 のエントリが無い",
          not any(432 <= e["version"] <= 439 for e in pv))
    check("protocolVersions.json の 431 は 1.16.220",
          [e["minecraftVersion"] for e in pv if e["version"] == 431] == ["1.16.220"])
    check("protocolVersions.json の 431 の 1 つ下は 428（1.16.210）",
          max(e["version"] for e in pv if e["version"] < 431) == 428)
    check("protocolVersions.json の 428 は 1.16.210",
          [e["minecraftVersion"] for e in pv if e["version"] == 428] == ["1.16.210"])
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
ids440 = packet_ids(data440) if data440 else {}
ids448 = packet_ids(data448) if data448 else {}
ids465 = packet_ids(data465) if data465 else {}
ids471 = packet_ids(data471) if data471 else {}
ids475 = packet_ids(data475) if data475 else {}
ids486 = packet_ids(data486) if data486 else {}
ids503 = packet_ids(data503) if data503 else {}
ids527 = packet_ids(data527) if data527 else {}

# ---------- B. パケット一覧（431）の件数・境界 ----------
check("431 のパケット数が 161 件", len(ids431) == 161, f"{len(ids431)} 件")
check("431 の最大パケット ID が 164", max(ids431) == 164, f"最大 {max(ids431)}")
check("431 の型定義数が 250", len(types431) == 250, f"{len(types431)}")
check("440 のパケット数が 164 件", len(ids440) == 164, f"{len(ids440)} 件")
check("440 の最大パケット ID が 167", max(ids440) == 167, f"最大 {max(ids440)}")
check("440 の型定義数が 253", len(types440) == 253, f"{len(types440)}")
check("431 の ID の欠番が 0・16・37・116（440 と同じ）",
      [i for i in range(max(ids431) + 1) if i not in ids431] == [0, 16, 37, 116])
check("431 のパケット ID 0〜164 が 440 の同 ID と名称がすべて一致",
      all(ids431[i] == ids440[i] for i in ids431),
      f"{sum(1 for i in ids431 if ids431[i] == ids440.get(i))} / {len(ids431)} 件一致")

d431 = TMP / "packet-diff-431-vs-440.json"
check("431 vs 440 の差分 JSON が存在する", d431.is_file(), str(d431))
if d431.is_file():
    j431 = json.loads(d431.read_text())
    check("431 vs 440：追加 3 件（165 sync_entity_property・166 add_volume_entity・167 remove_volume_entity）",
          j431["added"] == {"165": "sync_entity_property",
                            "166": "add_volume_entity",
                            "167": "remove_volume_entity"},
          json.dumps(j431["added"]))
    check("431 vs 440：削除 0・リネーム 0",
          j431["removed"] == {} and j431["renamed"] == {})
    check("431 vs 440：型変更 11 件",
          len(j431["changed_types"]) == 11, str(j431["changed_types"]))
    check("431 vs 440：型変更の内訳が GameRule・MetadataDictionary・MetadataFlags2・SoundType・StructureBlockSettings・mcpe_packet・packet_add_volume_entity・packet_event・packet_remove_volume_entity・packet_start_game・packet_sync_entity_property",
          j431["changed_types"] == ["GameRule", "MetadataDictionary",
                                     "MetadataFlags2", "SoundType",
                                     "StructureBlockSettings", "mcpe_packet",
                                     "packet_add_volume_entity (only in B)",
                                     "packet_event",
                                     "packet_remove_volume_entity (only in B)",
                                     "packet_start_game",
                                     "packet_sync_entity_property (only in B)"],
          json.dumps(j431["changed_types"]))

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
json_by_name = {v: k for k, v in ids431.items()}
# update_subchunk_blocks・subchunk・subchunk_request は 431 には存在しない
# （465・471 で新規追加）ためループから除き、不存在を別途確認する。
for const, pname in CORE:
    if const in ("UPDATE_SUB_CHUNK_BLOCKS", "SUB_CHUNK", "SUB_CHUNK_REQUEST"):
        continue
    java_id = int(java_packets[const], 16)
    json_id = json_by_name.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が protocol.json と一致",
          json_id == java_id, f"json={json_id}")
check("パケット ID 70 の名称が chunk_radius_update（Java 定数名 CHUNK_RADIUS_UPDATED とは名称違い・440 回と同じ）",
      ids431.get(70) == "chunk_radius_update")
check("パケット UPDATE_SUB_CHUNK_BLOCKS（0xac=172）が 431 の protocol.json に存在しない（465 で新規追加）",
      json_by_name.get("update_subchunk_blocks") is None)
check("パケット SUB_CHUNK（0xae=174）が 431 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk") is None)
check("パケット SUB_CHUNK_REQUEST（0xaf=175）が 431 の protocol.json に存在しない（471 で新規追加）",
      json_by_name.get("subchunk_request") is None)
check("パケット SYNC_ENTITY_PROPERTY（165）が 431 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("sync_entity_property") is None)
check("パケット ADD_VOLUME_ENTITY（166）が 431 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("add_volume_entity") is None)
check("パケット REMOVE_VOLUME_ENTITY（167）が 431 の protocol.json に存在しない（440 で新規追加）",
      json_by_name.get("remove_volume_entity") is None)
check("パケット SIMULATION_TYPE（168）が 431 の protocol.json に存在しない（448 で新規追加）",
      json_by_name.get("simulation_type") is None)
check("パケット NPC_DIALOGUE（169）が 431 の protocol.json に存在しない（448 で新規追加）",
      json_by_name.get("npc_dialogue") is None)

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_16_221 が存在する", "V1_16_221" in entry_by_name)
check("V1_16_221 のプロトコルが 431",
      entry_by_name.get("V1_16_221", (None,))[0] == 431,
      str(entry_by_name.get("V1_16_221")))
check("V1_16_221 の表示名が 1.16.221",
      entry_by_name.get("V1_16_221", (None, None))[1] == "1.16.221")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_16_221 が V1_17_2 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_16_221(431") < ver_src.index("V1_17_2(440"))
check("enum の javadoc に 1.16.220 の protocol.json が含まれる",
      "1.16.220" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 431 -> \"(1_16_210)\";", ver_src)
check("itemTableTag：431 が 1_16_210 の表を使う（1.16.210 代用・1.16.220 自体の実測ではない）", m is not None,
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
check("アイテム表 .bin が 38 本（新規 1 本）", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s431 = summary.get("431", {})
check("item_tables_summary.json に 431 のエントリがある",
      "431" in summary)
check("item_tables_summary.json の 431 が tag=bedrock-1.16.210・exact=624／shared=236／legacy=33／fallback=419",
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
# (1) StartGame：431 は 59 フィールド、440 は 60 フィールド（末尾の engine だけ違う）
sg431 = field_names(types431, "packet_start_game")
sg440 = field_names(types440, "packet_start_game")
check("StartGame：431 は 59 フィールド", len(sg431) == 59, f"{len(sg431)} フィールド")
check("StartGame：440 は 60 フィールド", len(sg440) == 60, f"{len(sg440)} フィールド")
check("StartGame：431 のフィールド一覧が 440 から engine を除いたものと一致する（順序も同じ）",
      sg431 == sg440[:-1])
check("StartGame：440 の末尾が engine（hasStartGameEngine()=true の前提）",
      sg440[-1] == "engine")
check("StartGame：431 に engine が無い（hasStartGameEngine()=false の前提）",
      "engine" not in sg431)
check("StartGame：431 の末尾が server_authoritative_inventory（440 と同じ位置・engine の直前）",
      sg431[-1] == "server_authoritative_inventory"
      and sg440[-2] == "server_authoritative_inventory")
check("StartGame：431 に edu_resource_uri が無い（hasStartGameEduResourceUri()=false の前提）",
      "edu_resource_uri" not in sg431)
check("StartGame：440 に edu_resource_uri が無い（hasStartGameEduResourceUri()=false の前提）",
      "edu_resource_uri" not in sg440)
check("StartGame：431 に block_pallete_checksum が無い（hasStartGameBlockPaletteChecksum()=false の前提）",
      "block_pallete_checksum" not in sg431
      and "block_pallette_checksum" not in sg431)
check("StartGame：440 に block_pallete_checksum が無い（hasStartGameBlockPaletteChecksum()=false の前提）",
      "block_pallete_checksum" not in sg440
      and "block_pallette_checksum" not in sg440)
check("StartGame：431 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types431, "packet_start_game", "seed") == "zigzag32")
check("StartGame：440 の seed は zigzag32（hasStartGameLu64Seed()=false の前提）",
      field_type(types440, "packet_start_game", "seed") == "zigzag32")
check("StartGame：431 の dimension は zigzag32",
      field_type(types431, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：440 の dimension は zigzag32",
      field_type(types440, "packet_start_game", "dimension") == "zigzag32")
check("StartGame：431 に property_data が無い（hasStartGame527Fields()=false の前提）",
      "property_data" not in sg431)
check("StartGame：431 に world_template_id が無い（hasStartGame527Fields()=false の前提）",
      "world_template_id" not in sg431)
check("StartGame：431 に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg431)
check("StartGame：431 に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg431 for t in ["server_identifier", "world_identifier",
                                   "scenario_identifier", "owner_identifier"]))
check("StartGame：431 に editor_world が無い（hasEditorWorld=false）",
      "editor_world" not in sg431)
check("StartGame：431 の permission_level は zigzag32（hasPermissionLevelMapper=false）",
      field_type(types431, "packet_start_game", "permission_level") == "zigzag32")
check("StartGame：431 の movement_authority が存在する（hasMovementAuthority=true）",
      "movement_authority" in sg431)
check("StartGame：431 の itemstates が存在する（hasItemstates=true）",
      "itemstates" in sg431)
check("StartGame：431 に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg431)
check("StartGame：431 に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg431 and "server_join_info" not in sg431)
check("StartGame：431 に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg431)
check("StartGame：431 に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg431)
check("StartGame：431 に created_in_editor・exported_from_editor・block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(f in sg431 for f in ["created_in_editor", "exported_from_editor",
                                   "block_network_ids_are_hashes"]))
check("StartGame：431 に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg431)
check("StartGame：431 に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg431)
check("StartGame：431 に persona_disabled・custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg431 and "custom_skins_disabled" not in sg431)
check("StartGame：431 に chat_restriction_level・disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg431
      and "disable_player_interactions" not in sg431)
check("StartGame：431 に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg431)

# (2) Resource packs info / stack：431 は 4 フィールド、440 も 4 フィールドで JSON 完全一致
rpi = field_names(types431, "packet_resource_packs_info")
rpi440 = field_names(types440, "packet_resource_packs_info")
check("Resource packs info：431 は 4 フィールド（must_accept・has_scripts・behaviour_packs・texture_packs）",
      rpi == ["must_accept", "has_scripts", "behaviour_packs", "texture_packs"])
check("Resource packs info：440 は 4 フィールド（431 と JSON 完全一致・force_server_packs は 448 で新規）",
      rpi440 == ["must_accept", "has_scripts", "behaviour_packs", "texture_packs"]
      and types431["packet_resource_packs_info"] == types440["packet_resource_packs_info"])
check("Resource packs info：431 に force_server_packs が無い（hasForceServerPacks()=false の前提）",
      "force_server_packs" not in rpi)
check("Resource packs info：431 に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("Resource packs info：431 に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("Resource packs info：431 に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("Resource packs info：431 に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
check("TexturePackInfos の countType が li16（440 と同じ）",
      types431["TexturePackInfos"][1].get("countType") == "li16"
      and types440["TexturePackInfos"][1].get("countType") == "li16")
rps = field_names(types431, "packet_resource_pack_stack")
rps440 = field_names(types440, "packet_resource_pack_stack")
check("Resource pack stack：431 は 440 と同じ 6 フィールド（behavior_packs を含む）",
      rps == rps440 == ["must_accept", "behavior_packs", "resource_packs",
                        "game_version", "experiments",
                        "experiments_previously_used"])
check("Resource pack stack：431 に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

# (3) エンティティ・アイテム
check("Mob equipment の item が Item（ItemV4 ではない）",
      field_type(types431, "packet_mob_equipment", "item") == "Item")
check("Add item entity の item が Item",
      field_type(types431, "packet_add_item_entity", "item") == "Item")
mae = [n for n, _ in container_fields(types431, "packet_mob_armor_equipment")]
check("Mob armor equipment が helmet・chestplate・leggings・boots の 4 つ（body は無い）",
      mae == ["runtime_entity_id", "helmet", "chestplate", "leggings", "boots"])
check("ItemV4 型が 431 の protocol.json に存在しない", "ItemV4" not in types431)
ap431 = field_names(types431, "packet_add_player")
ap440 = field_names(types440, "packet_add_player")
check("Add player：431 は 21 フィールド",
      len(ap431) == 21, f"{len(ap431)} フィールド")
check("Add player：440 は 21 フィールド",
      len(ap440) == 21, f"{len(ap440)} フィールド")
check("Add player：431 のフィールド一覧が uuid・username・entity_id_self・runtime_entity_id・platform_chat_id・position・velocity・pitch・yaw・head_yaw・held_item・metadata・flags・command_permission・action_permissions・permission_level・custom_stored_permissions・user_id・links・device_id・device_os",
      ap431 == ["uuid", "username", "entity_id_self", "runtime_entity_id",
                "platform_chat_id", "position", "velocity", "pitch", "yaw",
                "head_yaw", "held_item", "metadata", "flags",
                "command_permission", "action_permissions", "permission_level",
                "custom_stored_permissions", "user_id", "links", "device_id",
                "device_os"])
check("Add player：440 のフィールド一覧が 431 と一致する",
      ap440 == ap431)
check("Add player：431 の device_os は li32",
      field_type(types431, "packet_add_player", "device_os") == "li32")
check("Add player：431・440 の entity_id_self は zigzag64（名称も型も同じ）",
      field_type(types431, "packet_add_player", "entity_id_self") == "zigzag64"
      and field_type(types440, "packet_add_player", "entity_id_self") == "zigzag64")
check("Add player：431 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap431)
check("Add player：440 に gamemode が無い（hasAddPlayerGamemode()=false の前提）",
      "gamemode" not in ap440)
check("Add entity は 431 と 440 で同じ（11 フィールド・body_yaw が無い）",
      field_names(types431, "packet_add_entity") == field_names(types440, "packet_add_entity")
      and len(field_names(types431, "packet_add_entity")) == 11)
check("Set entity data は 431 と 440 で同じ（3 フィールド）",
      field_names(types431, "packet_set_entity_data") == field_names(types440, "packet_set_entity_data")
      and len(field_names(types431, "packet_set_entity_data")) == 3)

# (4) トランザクション・アイテム関連
leg = {n: t for n, t in container_fields(types431, "TransactionLegacy")}
check("TransactionLegacy の legacy_transactions が switch（440 と同じ）",
      isinstance(leg.get("legacy_transactions"), list)
      and leg["legacy_transactions"][0] == "switch")
tx = {n: t for n, t in container_fields(types431, "Transaction")}
tx_map = tx.get("transaction_type")[1]["mappings"]
check("Transaction の transaction_type が 0=normal・1=inventory_mismatch・2=item_use・3=item_use_on_entity・4=item_release（440 と同じ）",
      tx_map.get("0") == "normal"
      and tx_map.get("1") == "inventory_mismatch"
      and tx_map.get("2") == "item_use"
      and tx_map.get("3") == "item_use_on_entity"
      and tx_map.get("4") == "item_release")
check("Transaction の actions が TransactionActions（440 と同じ）",
      tx.get("actions") == "TransactionActions")
check("Transaction が 431 と 440 で JSON 同一",
      types431["Transaction"] == types440["Transaction"])
ta = types431["TransactionActions"]
ta_fields = {f["name"]: f["type"] for f in ta[1]["type"][1] if "name" in f}
check("TransactionActions の old/new item が旧型 Item（transactionUsesItemV4=false）",
      ta_fields.get("old_item") == "Item" and ta_fields.get("new_item") == "Item",
      f"old={ta_fields.get('old_item')} new={ta_fields.get('new_item')}")
check("TransactionActions に window_id／flags の option 欄が無い",
      "window_id" not in ta_fields and "flags" not in ta_fields)
isr431 = item_stack_request_type_id(types431)
isr440 = item_stack_request_type_id(types440)
check("ItemStackRequest：431 の type_id が u8 マッパーで 16 件（0〜15）",
      isr431 is not None and isr431[1]["mappings"] == {
          "0": "take", "1": "place", "2": "swap", "3": "drop",
          "4": "destroy", "5": "consume", "6": "create",
          "7": "lab_table_combine", "8": "beacon_payment",
          "9": "mine_block", "10": "craft_recipe",
          "11": "craft_recipe_auto", "12": "craft_creative",
          "13": "optional", "14": "non_implemented",
          "15": "results_deprecated"},
      json.dumps(isr431[1]["mappings"]) if isr431 else "なし")
check("ItemStackRequest：431 の type_id が 440 と JSON 同一",
      isr431 == isr440)
check("ItemStackRequest が 431 と 440 で JSON 同一",
      types431["ItemStackRequest"] == types440["ItemStackRequest"])
check("RecipeIngredient：431 が旧形式（network_id zigzag32＋匿名 switch）",
      types431["RecipeIngredient"][0] == "container"
      and types431["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types431["RecipeIngredient"][1][1].get("anon") is True,
      json.dumps(types431["RecipeIngredient"][1][0]))
check("RecipeIngredient：440 も旧形式（network_id zigzag32＋匿名 switch）",
      types440["RecipeIngredient"][0] == "container"
      and types440["RecipeIngredient"][1][0] == {"name": "network_id", "type": "zigzag32"}
      and types440["RecipeIngredient"][1][1].get("anon") is True)
check("RecipeIngredient：431 の匿名 switch が network_id=0 で void、default で network_data＋count（zigzag32）",
      types431["RecipeIngredient"][1][1]["type"][1].get("compareTo") == "network_id"
      and types431["RecipeIngredient"][1][1]["type"][1]["fields"].get("0") == "void"
      and [f["name"] for f in types431["RecipeIngredient"][1][1]["type"][1]["default"][1]] == ["network_data", "count"])
check("RecipeIngredient：554 が新形式（先頭が type の u8 マッパー：invalid／int_id_meta／molang／item_tag／string_id_meta）",
      types554["RecipeIngredient"][0] == "container"
      and types554["RecipeIngredient"][1][0].get("name") == "type"
      and types554["RecipeIngredient"][1][0]["type"][1].get("mappings") == {"0": "invalid", "1": "int_id_meta",
                                                                            "2": "molang", "3": "item_tag",
                                                                            "4": "string_id_meta"})

# (5) 核パケットの 431 vs 440：StartGame を除く 16 件中 16 件が JSON 同一
MAJOR = ["packet_add_player", "packet_add_entity", "packet_add_item_entity",
         "packet_level_chunk", "packet_move_entity", "packet_move_player",
         "packet_update_block", "packet_set_entity_data",
         "packet_mob_equipment", "packet_mob_armor_equipment",
         "packet_player_list",
         "packet_inventory_transaction", "packet_network_chunk_publisher_update",
         "packet_resource_pack_stack"]
for name in MAJOR:
    check(f"核パケット {name} が 431 と 440 で JSON 同一（分岐不要）",
          types431[name] == types440[name])
check("核パケット packet_resource_packs_info は 431 と 440 で JSON 同一（force_server_packs は両方に無い）",
      types431["packet_resource_packs_info"] == types440["packet_resource_packs_info"])
check("核パケット packet_player_auth_input が 431 と 440 で JSON 同一",
      types431["packet_player_auth_input"] == types440["packet_player_auth_input"])
check("核パケット packet_start_game は 431 と 440 で違う（440 の末尾に engine・分岐あり）",
      types431["packet_start_game"] != types440["packet_start_game"])

# ---------- G. 431／440 の境界値 ----------
st431 = types431["SoundType"][1]["mappings"]
st440 = types440["SoundType"][1]["mappings"]
check("SoundType：431 は 336 エントリ（0〜335）", len(st431) == 336, f"{len(st431)}")
check("SoundType：440 は 361 エントリ（0〜360）", len(st440) == 361, f"{len(st440)}")
check("SoundType：431 と 440 で JSON は違う（335 の値変更＋336〜360 の追加）",
      types431["SoundType"] != types440["SoundType"])
check("SoundType：431 の 335 が Undefined（440 では unknown335）",
      st431.get("335") == "Undefined" and st440.get("335") == "unknown335")
check("SoundType：440 の 360 が Undefined（431 には 336〜360 が無い）",
      st440.get("360") == "Undefined"
      and st431.get("336") is None and st431.get("360") is None)
check("SoundType：431 の 4 が Jump・5 が Break・6 が Place（440 と同じ）",
      st431.get("4") == "Jump" and st431.get("5") == "Break"
      and st431.get("6") == "Place")
le431 = types431["packet_level_event"]
le440 = types440["packet_level_event"]
le_map = le431[1][0]["type"][1]["mappings"]
le_map440 = le440[1][0]["type"][1]["mappings"]
check("Level event：431 は 88 エントリ（最大キー 16384）",
      len(le_map) == 88, f"{len(le_map)}")
check("Level event：440 は 88 エントリ（431 と JSON 同一）",
      len(le_map440) == 88 and le431 == le440, f"{len(le_map440)}")
check("Level event：431 の 2000 が particle_shoot・2001 が particle_destroy・2002 が particle_splash・2003 が particle_eye_despawn（440 と同じ）",
      le_map.get("2000") == "particle_shoot"
      and le_map.get("2001") == "particle_destroy"
      and le_map.get("2002") == "particle_splash"
      and le_map.get("2003") == "particle_eye_despawn")
check("Level event：431 の 9800 が players_sleeping・9801 は無い（最大キーは 16384 add_particle_mask）",
      le_map.get("9800") == "players_sleeping"
      and le_map.get("9801") is None
      and max(int(k) for k in le_map) == 16384
      and le_map.get("16384") == "add_particle_mask")
check("Entity event：431・440 ともに 54 エントリ（440 と同じ）",
      len(field_type(types431, "packet_entity_event", "event_id")[1]["mappings"]) == 54
      and len(field_type(types440, "packet_entity_event", "event_id")[1]["mappings"]) == 54)
check("Entity event：431 と 440 で JSON 同一",
      types431["packet_entity_event"] == types440["packet_entity_event"])
check("Entity event：431・440 ともに 1=jump・2=hurt_animation・3=death_animation・4=arm_swing（440 と同じ）",
      field_type(types431, "packet_entity_event", "event_id")[1]["mappings"].get("1") == "jump"
      and field_type(types431, "packet_entity_event", "event_id")[1]["mappings"].get("2") == "hurt_animation"
      and field_type(types431, "packet_entity_event", "event_id")[1]["mappings"].get("3") == "death_animation"
      and field_type(types431, "packet_entity_event", "event_id")[1]["mappings"].get("4") == "arm_swing")
check("Entity event：431・440 ともに 8=shake_wet（totem・shake_web・feed はまだ無い）",
      field_type(types431, "packet_entity_event", "event_id")[1]["mappings"].get("8") == "shake_wet"
      and "totem" not in field_type(types431, "packet_entity_event", "event_id")[1]["mappings"].values()
      and "shake_web" not in field_type(types431, "packet_entity_event", "event_id")[1]["mappings"].values()
      and "feed" not in field_type(types431, "packet_entity_event", "event_id")[1]["mappings"].values())
check("GameMode：431・440 ともに 6 エントリ（0〜5・5=fallback）",
      len(types431["GameMode"][1]["mappings"]) == 6
      and len(types440["GameMode"][1]["mappings"]) == 6
      and types431["GameMode"][1]["mappings"].get("5") == "fallback")
check("GameMode：431 と 440 で JSON 同一",
      types431["GameMode"] == types440["GameMode"])
md431 = types431["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]
md440 = types440["MetadataDictionary"][1]["type"][1][0]["type"][1]["mappings"]
check("MetadataDictionary：431 は 125 エントリ・440 は 124 エントリ",
      len(md431) == 125 and len(md440) == 124,
      f"431={len(md431)} 440={len(md440)}")
check("MetadataDictionary：431 と 440 で JSON は違う（define_properties の削除＋base_runtime_id の移動）",
      types431["MetadataDictionary"] != types440["MetadataDictionary"])
check("MetadataDictionary：431・440 ともに 0=flags・1=health・2=variant・3=color・4=nametag",
      md431.get("0") == "flags"
      and md431.get("1") == "health"
      and md431.get("2") == "variant"
      and md431.get("3") == "color"
      and md431.get("4") == "nametag")
check("MetadataDictionary：431 は 120=freezing_effect_strength・121=buoyancy_data・122=goat_horn_count・123=base_runtime_id・124=define_properties・125=update_properties",
      md431.get("120") == "freezing_effect_strength"
      and md431.get("121") == "buoyancy_data"
      and md431.get("122") == "goat_horn_count"
      and md431.get("123") == "base_runtime_id"
      and md431.get("124") == "define_properties"
      and md431.get("125") == "update_properties")
check("MetadataDictionary：440 は 120=base_runtime_id・121=freezing_effect_strength・122=buoyancy_data・123=goat_horn_count・124=update_properties（define_properties が無い）",
      md440.get("120") == "base_runtime_id"
      and md440.get("121") == "freezing_effect_strength"
      and md440.get("122") == "buoyancy_data"
      and md440.get("123") == "goat_horn_count"
      and md440.get("124") == "update_properties"
      and "define_properties" not in md440.values())
check("MetadataFlags2：431 は 31 フラグ・440 は 34 フラグ",
      len(types431["MetadataFlags2"][1]["flags"]) == 31
      and len(types440["MetadataFlags2"][1]["flags"]) == 34)
check("MetadataFlags2：431 と 440 で JSON は違う（unknown95・ram_attack・playing_dead の追加）",
      types431["MetadataFlags2"] != types440["MetadataFlags2"])
check("MetadataFlags2：431 の末尾 3 つが celebrating・admiring・celebrating_special（440 の unknown95・ram_attack・playing_dead は無い）",
      types431["MetadataFlags2"][1]["flags"][28:] == ["celebrating", "admiring",
                                                      "celebrating_special"]
      and "unknown95" not in types431["MetadataFlags2"][1]["flags"]
      and "ram_attack" not in types431["MetadataFlags2"][1]["flags"]
      and "playing_dead" not in types431["MetadataFlags2"][1]["flags"])
check("MetadataFlags2：440 の末尾 3 つが unknown95・ram_attack・playing_dead・has_entity_properties はまだ無い",
      types440["MetadataFlags2"][1]["flags"][31:] == ["unknown95", "ram_attack",
                                                      "playing_dead"]
      and "has_entity_properties" not in types431["MetadataFlags2"][1]["flags"]
      and "has_entity_properties" not in types440["MetadataFlags2"][1]["flags"])
check("Subchunk：431 に packet_subchunk 型が存在しない（471 で新規追加）",
      "packet_subchunk" not in types431)
check("Subchunk request：431 に packet_subchunk_request 型が存在しない（471 で新規追加）",
      "packet_subchunk_request" not in types431)
check("CommandFlags：431 は 3 フラグ（unused・has_semantic_constraint・collapse_enum）",
      [f["name"] for f in types431["CommandFlags"][1]] == ["unused",
                                                           "has_semantic_constraint",
                                                           "collapse_enum"])
check("CommandFlags：440 は 3 フラグ（431 と同じ）",
      [f["name"] for f in types440["CommandFlags"][1]] == ["unused",
                                                           "has_semantic_constraint",
                                                           "collapse_enum"])
check("MaterialReducer 型が 431 の protocol.json に存在しない（465 で新規追加）",
      "MaterialReducer" not in types431)
check("BlockUpdate 型が 431 の protocol.json に存在しない（465 で新規追加）",
      "BlockUpdate" not in types431)
check("EducationSharedResourceURI 型が 431 の protocol.json に存在しない（465 で新規追加）",
      "EducationSharedResourceURI" not in types431)
check("EducationExternalLinkSettings 型が 431 の protocol.json に存在しない（465 で新規追加）",
      "EducationExternalLinkSettings" not in types431)
check("Animate：431 は row_right=128・row_left=129＋匿名 switch（boat_rowing_time lf32）",
      types431["packet_animate"][1][0]["type"][1]["mappings"].get("128") == "row_right"
      and types431["packet_animate"][1][0]["type"][1]["mappings"].get("129") == "row_left"
      and len(container_fields(types431, "packet_animate")) == 3
      and container_fields(types431, "packet_animate")[2][1][0] == "switch")
check("Animate：431 の anon switch は action_id 依存で row_right／row_left に boat_rowing_time（lf32）",
      container_fields(types431, "packet_animate")[2][1][1].get("compareTo") == "action_id"
      and container_fields(types431, "packet_animate")[2][1][1]["fields"]["row_right"] == ["container", [{"name": "boat_rowing_time", "type": "lf32"}]]
      and container_fields(types431, "packet_animate")[2][1][1]["fields"]["row_left"] == ["container", [{"name": "boat_rowing_time", "type": "lf32"}]])
check("Animate：431 と 440 で JSON 同一",
      types431["packet_animate"] == types440["packet_animate"])
check("Animate entity：431 は 6 フィールド（stop_condition_version が無い）",
      field_names(types431, "packet_animate_entity") == ["animation", "next_state",
                                                        "stop_condition", "controller",
                                                        "blend_out_time", "runtime_entity_ids"])
check("Animate entity：440 は 6 フィールド（431 と同じ）",
      field_names(types440, "packet_animate_entity") == ["animation", "next_state",
                                                        "stop_condition", "controller",
                                                        "blend_out_time", "runtime_entity_ids"])
check("Crafting data：431 の順序が recipes・potion_type_recipes・potion_container_recipes・is_clean",
      field_names(types431, "packet_crafting_data") == ["recipes",
                                                       "potion_type_recipes",
                                                       "potion_container_recipes",
                                                       "is_clean"])
check("Crafting data：440 の順序が 431 と同じ",
      field_names(types440, "packet_crafting_data") == ["recipes",
                                                       "potion_type_recipes",
                                                       "potion_container_recipes",
                                                       "is_clean"])
check("Hurt armor：431 は health の 1 フィールド",
      field_names(types431, "packet_hurt_armor") == ["health"])
check("Hurt armor：440 は health の 1 フィールド（431 と同じ）",
      field_names(types440, "packet_hurt_armor") == ["health"])
check("Education settings：431 は 6 フィールド（CodeBuilderDefaultURI・CodeBuilderTitle・CanResizeCodeBuilder・HasOverrideURI・OverrideURI・HasQuiz）",
      field_names(types431, "packet_education_settings") == ["CodeBuilderDefaultURI",
                                                            "CodeBuilderTitle",
                                                            "CanResizeCodeBuilder",
                                                            "HasOverrideURI",
                                                            "OverrideURI", "HasQuiz"])
check("Education settings：440 は 6 フィールド（431 と同じ）",
      field_names(types440, "packet_education_settings") == ["CodeBuilderDefaultURI",
                                                            "CodeBuilderTitle",
                                                            "CanResizeCodeBuilder",
                                                            "HasOverrideURI",
                                                            "OverrideURI", "HasQuiz"])
check("Photo transfer：431 は image_name・image_data・book_id の 3 フィールド",
      field_names(types431, "packet_photo_transfer") == ["image_name", "image_data",
                                                        "book_id"])
check("Photo transfer：440 は 3 フィールド（431 と同じ）",
      field_names(types440, "packet_photo_transfer") == ["image_name", "image_data",
                                                        "book_id"])
check("Add volume entity：431 に packet_add_volume_entity 型が存在しない（440 で新規追加）",
      "packet_add_volume_entity" not in types431)
check("Remove volume entity：431 に packet_remove_volume_entity 型が存在しない（440 で新規追加）",
      "packet_remove_volume_entity" not in types431)
check("Sync entity property：431 に packet_sync_entity_property 型が存在しない（440 で新規追加）",
      "packet_sync_entity_property" not in types431)
check("Entity pick request：431 は runtime_entity_id・selected_slot の 2 フィールド（with_data が無い）",
      field_names(types431, "packet_entity_pick_request") == ["runtime_entity_id",
                                                             "selected_slot"])
check("Entity pick request：440 は 2 フィールド（431 と同じ）",
      field_names(types440, "packet_entity_pick_request") == ["runtime_entity_id",
                                                             "selected_slot"])
check("Update block synced：431 と 440 でフィールド名は同じ 6 件で transition_type の型も同じ（varint64）",
      field_names(types431, "packet_update_block_synced") == field_names(types440, "packet_update_block_synced")
      and field_type(types431, "packet_update_block_synced", "transition_type")[1]["type"] == "varint64"
      and field_type(types440, "packet_update_block_synced", "transition_type")[1]["type"] == "varint64")
check("Skin：431 は 17 フィールド（geometry_data_version・primary_user が無い）",
      len(types431["Skin"][1]) == 17
      and "geometry_data_version" not in [f["name"] for f in types431["Skin"][1]]
      and "primary_user" not in [f["name"] for f in types431["Skin"][1]])
check("Skin：440 は 17 フィールド（431 と JSON 同一）",
      len(types440["Skin"][1]) == 17
      and types431["Skin"] == types440["Skin"])
check("Skin：431 では premium・persona・cape_on_classic が animation_data の直後（440 と同じ index 8）",
      [f["name"] for f in types431["Skin"][1]].index("premium") == 8
      and [f["name"] for f in types440["Skin"][1]].index("premium") == 8)
check("Text：431 の type マッパーに 11 が無い（440 も無し）",
      "11" not in types431["packet_text"][1][0]["type"][1]["mappings"]
      and "11" not in types440["packet_text"][1][0]["type"][1]["mappings"])
check("Network settings：431・440 ともに compression_threshold は u16",
      field_type(types431, "packet_network_settings", "compression_threshold") == "u16"
      and field_type(types440, "packet_network_settings", "compression_threshold") == "u16")
check("Structure block update：431・440 ともに 9 フィールド（water_logged が無い）",
      len(field_names(types431, "packet_structure_block_update")) == 9
      and len(field_names(types440, "packet_structure_block_update")) == 9)
check("Spawn particle effect：431・440 ともに 4 フィールド（dimension・entity_id・position・particle_name）",
      field_names(types431, "packet_spawn_particle_effect") == ["dimension",
                                                                "entity_id",
                                                                "position",
                                                                "particle_name"]
      and field_names(types431, "packet_spawn_particle_effect") == field_names(types440, "packet_spawn_particle_effect"))
check("Boss event：431・440 ともに query が無い（ボスバー問い合わせは 582 以降）",
      "query" not in str(types431["packet_boss_event"])
      and "query" not in str(types440["packet_boss_event"]))
check("CommandOrigin：431 と 440 で JSON 同一（type マッピング含む）",
      types431["CommandOrigin"] == types440["CommandOrigin"])
check("Adventure settings：431 と 440 で command_permission が同じ",
      field_type(types431, "packet_adventure_settings", "command_permission") == field_type(types440, "packet_adventure_settings", "command_permission"))
check("StructureBlockSettings：431 と 440 で JSON は違う（rotation／mirror のマッパー化＋animation_mode／animation_duration の追加）",
      types431["StructureBlockSettings"] != types440["StructureBlockSettings"])
check("StructureBlockSettings：431 は 11 フィールド（animation_mode・animation_duration が無い）",
      field_names(types431, "StructureBlockSettings") == ["palette_name",
                                                          "ignore_entities",
                                                          "ignore_blocks",
                                                          "size",
                                                          "structure_offset",
                                                          "last_editing_player_unique_id",
                                                          "rotation", "mirror",
                                                          "integrity", "seed",
                                                          "pivot"])
check("StructureBlockSettings：440 は 13 フィールド（mirror の次に animation_mode・animation_duration）",
      field_names(types440, "StructureBlockSettings") == ["palette_name",
                                                          "ignore_entities",
                                                          "ignore_blocks",
                                                          "size",
                                                          "structure_offset",
                                                          "last_editing_player_unique_id",
                                                          "rotation", "mirror",
                                                          "animation_mode",
                                                          "animation_duration",
                                                          "integrity", "seed",
                                                          "pivot"])
check("StructureBlockSettings：431 の rotation・mirror は素の u8（マッパーではない）",
      field_type(types431, "StructureBlockSettings", "rotation") == "u8"
      and field_type(types431, "StructureBlockSettings", "mirror") == "u8")
check("StructureBlockSettings：440 の rotation は u8 マッパー（0=none・1=90_deg・2=180_deg・3=270_deg）・mirror は u8 マッパー（0=none・1=x_axis・2=z_axis・3=both_axes）",
      field_type(types440, "StructureBlockSettings", "rotation") == ["mapper", {"type": "u8", "mappings": {"0": "none", "1": "90_deg", "2": "180_deg", "3": "270_deg"}}]
      and field_type(types440, "StructureBlockSettings", "mirror") == ["mapper", {"type": "u8", "mappings": {"0": "none", "1": "x_axis", "2": "z_axis", "3": "both_axes"}}])
check("Available commands：431・440 ともに flags は u8（lu16 になるのは 448）",
      find_flags_type(types431["packet_available_commands"]) == "u8"
      and find_flags_type(types440["packet_available_commands"]) == "u8",
      f"431={find_flags_type(types431['packet_available_commands'])} 440={find_flags_type(types440['packet_available_commands'])}")
check("Modal form response：431 は form_id・data の 2 フィールド（440 と同じ）",
      field_names(types431, "packet_modal_form_response") == ["form_id", "data"]
      and field_names(types431, "packet_modal_form_response") == field_names(types440, "packet_modal_form_response"))
check("Map info request：431 は map_id の 1 フィールド（440 と同じ）",
      field_names(types431, "packet_map_info_request") == ["map_id"]
      and field_names(types431, "packet_map_info_request") == field_names(types440, "packet_map_info_request"))
check("Network chunk publisher update：431 は coordinates・radius の 2 フィールド（440 と同じ）",
      field_names(types431, "packet_network_chunk_publisher_update") == ["coordinates", "radius"]
      and field_names(types431, "packet_network_chunk_publisher_update") == field_names(types440, "packet_network_chunk_publisher_update"))
check("Clientbound map item data：431 に origin が無い（440 と同じ）",
      "origin" not in field_names(types431, "packet_clientbound_map_item_data")
      and "origin" not in field_names(types440, "packet_clientbound_map_item_data"))
pa431 = [f["name"] for f in types431["PlayerAttributes"][1]["type"][1] if "name" in f]
check("PlayerAttributes：431 は min・max・current・default・name の 5 項目（440 と同じ）",
      pa431 == ["min", "max", "current", "default", "name"])
check("PlayerAttributes：440 も同じ 5 項目",
      [f["name"] for f in types440["PlayerAttributes"][1]["type"][1] if "name" in f] == ["min", "max", "current", "default", "name"])
pe431 = types431["packet_event"][1][1]["type"][1]["mappings"]
pe440 = types440["packet_event"][1][1]["type"][1]["mappings"]
check("packet_event：431 は 25 イベント・440 は 26 イベント",
      len(pe431) == 25 and len(pe440) == 26,
      f"431={len(pe431)} 440={len(pe440)}")
check("packet_event：440 の 25 が waxed_or_unwaxed_copper（431 には無い）",
      pe440.get("25") == "waxed_or_unwaxed_copper"
      and pe431.get("25") is None)
check("packet_event：431 と 440 で 0〜24 は同じ（順序も同じ）",
      all(pe431[str(i)] == pe440[str(i)] for i in range(25)))
check("InputFlag：431 は 37 フラグで ascend_scaffolding・descend_scaffolding（440 と JSON 同一。調査当初の「36 フラグ」は誤り）",
      len(types431["InputFlag"][1]["flags"]) == 37
      and "ascend_scaffolding" in types431["InputFlag"][1]["flags"]
      and "descend_scaffolding" in types431["InputFlag"][1]["flags"]
      and types431["InputFlag"] == types440["InputFlag"])
check("DeviceOS 型が 431 の protocol.json に存在しない", "DeviceOS" not in types431)
check("EntityProperties 型が 431 の protocol.json に存在しない", "EntityProperties" not in types431)
check("SubChunkEntryWithCaching 型が 431 の protocol.json に存在しない", "SubChunkEntryWithCaching" not in types431)
# GameRule：431 は 3 フィールド、440 は 4 フィールド（editable が name の次に挿入）
gr431 = field_names(types431, "GameRule")
gr440 = field_names(types440, "GameRule")
check("GameRule：431 は name・type・value の 3 フィールド",
      gr431 == ["name", "type", "value"])
check("GameRule：440 は name・editable・type・value の 4 フィールド",
      gr440 == ["name", "editable", "type", "value"])
check("GameRule：440 の editable が index 1（name の次）",
      gr440.index("editable") == 1)

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
check("BedrockVersion の NetherNet 分岐（2225）が 431 と無関係に残っている",
      "2225" in ver_src and "NetherNet" in ver_src)
check("BedrockChunkTranslator が BedrockVersion を参照していない（チャンク変換はバージョン非依存。BedrockEntityTranslator は 431 回の goat_horn_count 分岐で BedrockVersion を取るようになったため対象外）",
      "BedrockVersion" not in (SRC / "BedrockChunkTranslator.java").read_text())

# ---------- J. 431 回の実質的な変更：StartGame の engine 分岐と goat_horn_count 分岐 ----------
check("述語 hasStartGameEngine() が protocol >= 440 で定義されている",
      bool(re.search(r"boolean hasStartGameEngine\(\) \{\s*return protocol >= 440;",
                     ver_src)))
check("述語 hasGoatHornCountKey() が protocol >= 440 で定義されている",
      bool(re.search(r"boolean hasGoatHornCountKey\(\) \{\s*return protocol >= 440;",
                     ver_src)))
pkt_src = (SRC / "BedrockPackets.java").read_text()
check("BedrockPackets.encodeStartGame が engine を hasStartGameEngine() で分岐している",
      "hasStartGameEngine()" in pkt_src)
check("BedrockPacketsVersionTest.parseStartGame が engine を hasStartGameEngine() で分岐している",
      "hasStartGameEngine()" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text())
check("BedrockPacketsVersionTest に startGame431OmitsEngine テストがある（431 は 440 より 4 バイト短い）",
      "startGame431OmitsEngine" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text())
check("BedrockPacketsVersionTest の startGameParsesForEveryShape に V1_16_221 が含まれている",
      "V1_16_221" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockPacketsVersionTest.java").read_text())
ent_src = (SRC / "BedrockEntityTranslator.java").read_text()
check("BedrockEntityTranslator.toBedrockData に BedrockVersion を取る 3 引数版がある",
      "toBedrockData(JavaMetadata meta, int type765," in ent_src
      and "BedrockVersion version" in ent_src)
check("BedrockEntityTranslator が goat_horn_count を hasGoatHornCountKey() で分岐している",
      "hasGoatHornCountKey()" in ent_src)
check("BedrockSession が toBedrockData に bedrockVersion を渡している",
      "toBedrockData(metadata, state.type765, bedrockVersion)" in sess_src)
check("BedrockEntityTranslatorTest に metadataGoatHornCountOnlyFrom440 テストがある（431 では key 123 を書かない）",
      "metadataGoatHornCountOnlyFrom440" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockEntityTranslatorTest.java").read_text())
check("BedrockVersionTest に allSixtyFourSupportedProtocolsResolve テストがあり 431 が含まれる",
      "allSixtyFourSupportedProtocolsResolve" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text()
      and "431" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text())
check("BedrockVersionTest の resourcePackGatesMatchVerifiedShapes に V1_16_221 のアサーションがある",
      "V1_16_221.hasForceServerPacks()" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockVersionTest.java").read_text())
check("BedrockSessionVersionTest に allSixtyThreeProtocolsReachStartGame テストがあり 431 が含まれる",
      "allSixtyThreeProtocolsReachStartGame" in (BE / "src/test/java/io/detergentmc/bedrock/BedrockSessionVersionTest.java").read_text())
check("432〜439 に分岐の閾値が無い（431 の次の閾値は 440）",
      not re.search(r"protocol >= 43[2-9]\b", ver_src))
check("11 件の型変更のうちコードが読む・書くのは StartGame の engine と MetadataDictionary の key 123 のみ（GameRule・MetadataFlags2・SoundType・StructureBlockSettings・packet_event・sync_entity_property・add_volume_entity・remove_volume_entity はコードで参照されない）",
      not any(re.search(rf"\b{name}\b", pkt_src)
              for name in ["GameRule",
                           "MetadataFlags2",
                           "packet_event",
                           "sync_entity_property",
                           "add_volume_entity",
                           "remove_volume_entity"]))
check("GameRule は件数 0 で送るため editable を書かない（BedrockPackets に GameRule のエンコードが無い）",
      "GameRule" not in pkt_src)
check("Skin の既知の制限：writeEmptySkin は geometry_data_version・primary_user・overriding_player_appearance を書く 567 形式のまま（431 の Skin は 17 フィールドで順序も違う。PlayerList は偽クライアントが解析しないため 465〜560 と同じ既存の近似で、431 回では直さない。BE-NOTES.md §20 参照）",
      "geometry data version" in pkt_src
      and "primary user" in pkt_src
      and "overriding player appearance" in pkt_src)

print()
print(f"TOTAL: {len(CHECKS)} checks, {len(CHECKS) - len(FAILURES)} passed, {len(FAILURES)} failed")
if FAILURES:
    print("FAILURES:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
