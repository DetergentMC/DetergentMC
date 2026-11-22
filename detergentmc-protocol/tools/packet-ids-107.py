"""107（1.9）と 108（1.9.1）のパケット ID が完全一致し、ワイヤ形式
差分が Join Game の dimension の型（i8 ⇔ i32）ただ1件であることを
実データで照合し、PacketIds.java の 107 列（108 列と同値）の正しさを
裏付ける。

番号対応（Stage A 実測）：**107 は 1.9 本体**。実バニラ 1.9 の
ステータス応答が protocol 107 を返すことを Stage A で自ら採取して
確認した（`{"name":"1.9","protocol":107}`）。jar の SHA-1 は
piston-meta の version json と照合済み
（b4d449cf2918e0f3bd8aa18954b916a4d1880f0d、8,848,015 バイト）。
pack_format 2（jar に pack.mcmeta 無し）、world_version
（DataVersion）は 169、Java 8、data-generator 無し。

照合内容：
  1. protocol.json 全面比較：pc/1.9（107 自身）と pc/1.9.2（109）の
     差分は packet_login の dimension の型（i8 ⇔ i32）ただ1件。
     パケット集合・ID・Respawn を含む他の全フィールド型は完全一致。
     108 は 109 と差分ゼロ（108 Stage A で証明済み）なので、
     107 の ID 表は 108 とも一致する。
  2. ワイヤ採取（先行調査＋ Stage A 再採取）：Join Game は
     17 バイトで dimension を i8 として過不足なく消費（i32 では
     値が崩壊する）。Chunk Data は dataLen=184,665 に対し
     セクション実消費 30,809＋バイオーム 256 で、残り
     153,600 バイト（512 の倍数）が全てゼロ埋め。データ部の
     後ろに blockEntities は無い（残量 0）。Keep Alive は
     varint（4 バイト読み切り）で、応答 0x0b の後に送った
     チャットがバニラログに到達した。
  3. level.dat の DataVersion は 169。
  4. 属性 generic.armorToughness は 1.9 の jar の en_US.lang に
     無く、1.9.1 の jar で新規出現する（属性は名前文字列で
     運ばれるためワイヤ形式差分ではない。107 方向への扱いは
     RegistryRemapper／PlayTranslator 側の名前表の問題）。
  5. PacketIds.java の全エントリ（284件）について id107 列が
     id108 列と全件一致すること。

使い方：
  python3 tools/packet-ids-107.py [--prep107 DIR] [--prep108 DIR]
        [--prep109 DIR]
  終了コード 0 = 全照合成立、1 = 問題あり。
"""

import argparse
import gzip
import hashlib
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAVA_FILE = (SCRIPT_DIR.parent / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

EXPECTED_SHA1_19 = "b4d449cf2918e0f3bd8aa18954b916a4d1880f0d"


def data_version(level_dat):
    data = gzip.open(level_dat, "rb").read()
    i = data.find(b"\x00\x0bDataVersion")
    assert i >= 0, f"DataVersion が無い: {level_dat}"
    return struct.unpack(">i", data[i + 13:i + 17])[0]


def parse_varint(buf, i):
    val = 0
    shift = 0
    while True:
        b = buf[i]
        i += 1
        val |= (b & 0x7F) << shift
        if not (b & 0x80):
            return val, i
        shift += 7


def walk_diff(pa, pb, path, diffs):
    if type(pa) is not type(pb):
        diffs.append((path, pa, pb))
        return
    if isinstance(pa, dict):
        for k in set(pa) | set(pb):
            if k not in pa:
                diffs.append((path + "/" + str(k), "ABSENT", pb[k]))
            elif k not in pb:
                diffs.append((path + "/" + str(k), pa[k], "ABSENT"))
            else:
                walk_diff(pa[k], pb[k], path + "/" + str(k), diffs)
    elif isinstance(pa, list):
        if len(pa) != len(pb):
            diffs.append((path, f"len {len(pa)}", f"len {len(pb)}"))
        else:
            for i, (x, y) in enumerate(zip(pa, pb)):
                walk_diff(x, y, f"{path}[{i}]", diffs)
    else:
        if pa != pb:
            diffs.append((path, pa, pb))


def parse_entries(java_text):
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 40) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        vals = [int(g, 16) if g.startswith("0x") else -1
                for g in m.groups()[3:]]
        entries.append((m.group(1), m.group(2), m.group(3), vals))
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prep107", type=Path,
                        default=Path.home() / "workspace/legacy-prep-107")
    parser.add_argument("--prep108", type=Path,
                        default=Path.home() / "workspace/legacy-prep-108")
    parser.add_argument("--prep109", type=Path,
                        default=Path.home() / "workspace/legacy-prep-109")
    args = parser.parse_args()
    problems = []

    jar107 = args.prep107 / "server-1.9.jar"

    # 0) jar の SHA-1 と piston-meta の照合
    sha1 = hashlib.sha1(jar107.read_bytes()).hexdigest()
    version_json = json.loads(
        (args.prep107 / "version-1.9.json").read_text(encoding="utf-8"))
    meta_sha1 = version_json["downloads"]["server"]["sha1"]
    print(f"server jar SHA-1: 実測={sha1} piston-meta={meta_sha1}")
    if sha1 != EXPECTED_SHA1_19 or sha1 != meta_sha1:
        problems.append("server jar の SHA-1 が想定と違う")
    if version_json.get("javaVersion", {}).get("majorVersion") != 8:
        problems.append("javaVersion が 8 でない")

    # 1) protocol.json 全面比較（pc/1.9 ⇔ pc/1.9.2）
    p107 = json.loads((args.prep107 / "mcdata/1.9/protocol.json")
                      .read_text(encoding="utf-8"))
    p109 = json.loads((args.prep109 / "mcdata/1.9.2/protocol.json")
                      .read_text(encoding="utf-8"))
    diffs = []
    walk_diff(p107, p109, "", diffs)
    print(f"protocol.json 差分: {len(diffs)} 件")
    for d in diffs:
        print("  ", d)
    if len(diffs) != 1 or diffs[0][1] != "i8" or diffs[0][2] != "i32" \
            or "packet_login" not in diffs[0][0]:
        problems.append(f"protocol.json の差分が想定外: {diffs}")

    # 2) ワイヤ採取の記録（先行調査の成果物）
    status = json.loads((args.prep107
                         / "boot-1.9/status-response-1.9.json")
                        .read_text(encoding="utf-8"))
    if status["version"]["protocol"] != 107:
        problems.append(f"Status の protocol が 107 でない: "
                        f"{status['version']}")
    capture = (args.prep107 / "capture-1.9.log").read_text(
        encoding="utf-8", errors="replace")
    if "VARINT" not in capture:
        problems.append("Keep Alive varint の採取記録が無い")

    jg = (args.prep107 / "join-game-1.9.bin").read_bytes()
    if len(jg) != 17:
        problems.append(f"Join Game の長さが 17 でない: {len(jg)}")
    else:
        # i8 として読むと過不足なく消費する
        eid, gm, dim, diff_, maxp = struct.unpack(">iBbBB", jg[:8])
        lt_len, off = parse_varint(jg, 8)
        tail = jg[off + lt_len:]
        if len(tail) != 1 or dim not in (-1, 0, 1):
            problems.append(
                f"Join Game を i8 で読んでも整合しない: dim={dim} "
                f"tail={tail.hex()}")
        print(f"Join Game: 17 バイト、i8 読みで dimension={dim}、"
              f"difficulty={diff_}、maxPlayers={maxp}、末尾1バイト")

    # チャンク：dataLen 内のゼロ埋めと blockEntities の不在
    b = (args.prep107 / "chunk-sample-1.9.bin").read_bytes()
    x, z = struct.unpack(">ii", b[:8])
    i = 8
    ground = b[i]
    i += 1
    mask, i = parse_varint(b, i)
    dlen, i = parse_varint(b, i)
    start = i

    def parse_section(i):
        i += 1  # bitsPerBlock
        plen, i = parse_varint(b, i)
        for _ in range(plen):
            _, i = parse_varint(b, i)
        dl2, i = parse_varint(b, i)
        return i + 8 * dl2 + 2048 + 2048

    for s in range(16):
        if mask >> s & 1:
            i = parse_section(i)
    consumed = i - start
    i += 256  # バイオーム
    pad = b[i:start + dlen]
    after = len(b) - (start + dlen)
    print(f"Chunk: dataLen={dlen} 実消費={consumed} "
          f"ゼロ埋め={len(pad)} 末尾残量={after}")
    if not pad or len(pad) % 512 != 0 or any(v != 0 for v in pad):
        problems.append("チャンクのゼロ埋めが想定と違う")
    if after != 0:
        problems.append(f"チャンク末尾の残量が 0 でない: {after}")

    # 3) DataVersion
    dv = data_version(args.prep107 / "boot-1.9/world/level.dat")
    print(f"DataVersion: 1.9={dv}")
    if dv != 169:
        problems.append(f"DataVersion が 169 でない: {dv}")

    # 4) armorToughness の lang 確認
    with zipfile.ZipFile(jar107) as zj:
        lang107 = zj.read("assets/minecraft/lang/en_US.lang").decode(
            "utf-8", errors="replace")
    with zipfile.ZipFile(args.prep108 / "server-1.9.1.jar") as zj:
        lang108 = zj.read("assets/minecraft/lang/en_US.lang").decode(
            "utf-8", errors="replace")
    if "armorToughness" in lang107:
        problems.append("1.9 の lang に armorToughness がある（想定外）")
    if "attribute.name.generic.armorToughness" not in lang108:
        problems.append("1.9.1 の lang に armorToughness が無い（想定外）")
    print("armorToughness: 1.9 には無く 1.9.1 で追加を確認")

    # 5) PacketIds.java の 107 列 = 108 列
    entries = parse_entries(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds エントリ数: {len(entries)}")
    if len(entries) != 284:
        problems.append(f"エントリ数が 284 でない: {len(entries)}")
    mismatch = [name for name, _, _, v in entries if v[0] != v[1]]
    if mismatch:
        problems.append(f"id107 != id108 のエントリ: {mismatch}")
    print("PacketIds: id107 列は id108 列と全件一致")

    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("OK: 107 と 108 の ID は完全一致。ワイヤ形式差分は Join Game "
          "の dimension（i8 ⇔ i32）ただ1件。107 固有の癖として "
          "チャンクの末尾ゼロ埋めを確認。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
