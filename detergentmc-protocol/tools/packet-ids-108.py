"""108（1.9.1）と 109（1.9.2）の差分がゼロであることを実データで
照合し、PacketIds.java の 108 列（109 列と同値）の正しさを裏付ける。

番号対応（Stage A 実測）：**108 は 1.9.1 本体**。実バニラ 1.9.1 の
ステータス応答が protocol 108 を返すことを自ら採取して確認した
（legacy-prep-108 の boot-1.9.1/status-response-1.9.1.json と
Stage A での再採取）。jar の SHA-1 は piston-meta の version json
と照合済み（bf95d9118d9b4b827f524c878efd275125b56181、
8,848,010 バイト）。pack_format 2（両 jar に pack.mcmeta 無し）、
world_version（DataVersion）は 175 ⇔ 176、Java 8、data-generator
無し（jar に data/ 自体が無い）。

照合内容：
  1. 実 jar 同士の比較：エントリ一覧が完全一致（6,354 件）、
     非クラスファイルは CRC まで全て一致（assets・lang・
     loot table 等に差分なし）。差分のあるクラスは 11 件だけで、
     その文字列定数の差分は版数文字列（"1.9.1"／"1.9.2"、
     Outdated 文言）と em.class（パケットバッファ）の
     エラーメッセージ文言1件（"IntArray with size" ⇔
     "VarIntArray with size"）だけ。レジストリ登録名の文字列に
     差分は無い。
  2. ワイヤ採取（先行調査＋ Stage A 再採取）：Join Game は
     両版とも 20 バイトで dimension を i32 として過不足なく
     消費（i8 では崩れる）。Chunk Data は両版とも末尾に
     blockEntities が無い（データ部の後ろの残量 0）。
     Keep Alive は両版とも varint（1.9.1 は d9efb803＝7223257
     を 4 バイトで読み切り）で、応答 0x0b の後に送った
     チャットがバニラログに到達した。
  3. PacketIds.java の全エントリ（284件）について id108 列が
     id109 列と全件一致すること。
  4. minecraft-data の dataPaths.json に 1.9.1 の項が無いこと
     （1.9 系のレジストリは pc/1.9 に解決。プロトコルの
     ground truth は 109 自身の pc/1.9.2 protocol.json と
     上記ワイヤ実測）。

使い方：
  python3 tools/packet-ids-108.py [--prep108 DIR] [--prep109 DIR]
  終了コード 0 = 全照合成立、1 = 問題あり。
"""

import argparse
import gzip
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAVA_FILE = (SCRIPT_DIR.parent / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")
MCDATA = Path("/home/hatch/mc-client/node_modules/minecraft-data"
              "/minecraft-data/data")

EXPECTED_CLASS_DIFFS = {
    "ass.class", "azg.class", "b$1.class", "em.class", "la.class",
    "mc.class", "net/minecraft/server/MinecraftServer.class",
    "pb.class", "pc.class", "qw.class", "zj.class",
}


def jar_entries(path):
    with zipfile.ZipFile(path) as z:
        return {i.filename: (i.CRC, i.file_size) for i in z.infolist()}


def class_strings(path, name):
    with zipfile.ZipFile(path) as z:
        data = z.read(name)
    return set(m.group().decode("ascii")
               for m in re.finditer(rb"[\x20-\x7e]{4,}", data))


def data_version(level_dat):
    data = gzip.open(level_dat, "rb").read()
    i = data.find(b"\x00\x0bDataVersion")
    assert i >= 0, f"DataVersion が無い: {level_dat}"
    return struct.unpack(">i", data[i + 13:i + 17])[0]


def parse_entries(java_text):
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 39) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        vals = [int(g, 16) if g.startswith("0x") else -1
                for g in m.groups()[3:]]
        entries.append((m.group(1), m.group(2), m.group(3), vals))
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prep108", type=Path,
                        default=Path.home() / "workspace/legacy-prep-108")
    parser.add_argument("--prep109", type=Path,
                        default=Path.home() / "workspace/legacy-prep-109")
    args = parser.parse_args()
    problems = []

    jar108 = args.prep108 / "server-1.9.1.jar"
    jar109 = args.prep109 / "server-1.9.2.jar"

    # 1) 実 jar 同士の比較
    a, b = jar_entries(jar108), jar_entries(jar109)
    print(f"jar エントリ数: 1.9.1={len(a)} 1.9.2={len(b)}")
    if set(a) != set(b):
        problems.append(f"エントリ一覧が不一致: "
                        f"only1.9.1={sorted(set(a) - set(b))} "
                        f"only1.9.2={sorted(set(b) - set(a))}")
    diff = {k for k in a if k in b and a[k] != b[k]}
    non_class = {k for k in diff if not k.endswith(".class")}
    if non_class:
        problems.append(f"非クラスファイルに差分: {sorted(non_class)}")
    if diff != EXPECTED_CLASS_DIFFS:
        problems.append(f"差分クラス集合が想定外: {sorted(diff)}")
    print(f"差分: 非クラス 0 件、クラス {len(diff)} 件")
    for name in sorted(diff):
        sa, sb = class_strings(jar108, name), class_strings(jar109, name)
        extra = (sa - sb) | (sb - sa)
        bad = {s for s in extra
               if "1.9.1" not in s and "1.9.2" not in s
               and s not in ("IntArray with size ",
                             "VarIntArray with size ")}
        if bad:
            problems.append(f"{name} の文字列差分が想定外: {sorted(bad)}")
    print("差分クラスの文字列差分は版数文字列と文言1件だけ")

    # DataVersion（world_version）
    dv108 = data_version(args.prep108 / "boot-1.9.1/world/level.dat")
    dv109 = data_version(args.prep109 / "boot-1.9.2/world/level.dat")
    print(f"DataVersion: 1.9.1={dv108} 1.9.2={dv109}")
    if dv108 != 175 or dv109 != 176:
        problems.append(f"DataVersion が 175⇔176 でない: {dv108}⇔{dv109}")

    # 2) ワイヤ採取の記録
    status = json.loads((args.prep108
                         / "boot-1.9.1/status-response-1.9.1.json")
                        .read_text(encoding="utf-8"))
    if status["version"]["protocol"] != 108:
        problems.append(f"Status の protocol が 108 でない: "
                        f"{status['version']}")
    capture = (args.prep108 / "capture-1.9.1.log").read_text(
        encoding="utf-8", errors="replace")
    if "7223257" not in capture or "VARINT" not in capture:
        problems.append("Keep Alive varint の採取記録（7223257）が無い")
    jg108 = (args.prep108 / "join-game-1.9.1.bin").read_bytes()
    jg109 = (args.prep109 / "join-game-1.9.2.bin").read_bytes()
    if len(jg108) != 20 or len(jg109) != 20:
        problems.append(f"Join Game の長さが 20 でない: "
                        f"{len(jg108)}⇔{len(jg109)}")
    # entityId（先頭4バイト）と残りの構造が同形であること
    if jg108[4:] != jg109[4:]:
        problems.append("Join Game の entityId 以降が同形でない")
    print("ワイヤ: Status=108、Join Game 20 バイト同形、"
          "Keep Alive varint 読み切りを確認")

    # 3) PacketIds.java の 108 列 = 109 列
    entries = parse_entries(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds エントリ数: {len(entries)}")
    if len(entries) != 284:
        problems.append(f"エントリ数が 284 でない: {len(entries)}")
    mismatch = [name for name, _, _, v in entries if v[0] != v[1]]
    if mismatch:
        problems.append(f"id108 != id109 のエントリ: {mismatch}")
    print("PacketIds: id108 列は id109 列と全件一致")

    # 4) dataPaths に 1.9.1 の項が無いこと
    data_paths = json.loads((MCDATA / "dataPaths.json").read_text())["pc"]
    if "1.9.1" in data_paths:
        problems.append("dataPaths に 1.9.1 の項がある（想定外）")
    print("dataPaths: 1.9.1 の項は無い（1.9 系は pc/1.9 に解決）")

    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("OK: 108 と 109 の差分はゼロ（jar 全面比較＋ワイヤ実測）。"
          "PacketIds の 108 列は 109 列と一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
