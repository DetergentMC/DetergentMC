#!/usr/bin/env python3
"""PacketIds.java の各エントリに minecraft-data 1.15（573）のIDを付ける。

573 の ID の決め方（推測で埋めない）：
  1. minecraft-data の protocol.json は 1.15 と 1.15.1 で
     バイト単位で完全一致する（このスクリプトで SHA-256 を照合し、
     一致しなければ失敗する）。protocol.json はパケットの ID だけでなく
     フィールド構成まで含むため、一致は「573 と 575 は ID も形式も同じ」
     ことの裏付けになる。1.15.1 の protocol.json は 1.15.2 とも同一で
     あることが 575 の作業で確認済み。
  2. 575 の ID は packet-ids-575.py が同じ裏付けで 578 と同値として
     PacketIds.java に入れたもの。1.15 の protocol.json がそれと同一で
     ある以上、同じ写像で 573 の ID も同じ値になる。
  3. 加えて、両 jar の data-generator ダンプ（reports/registries.json・
     reports/blocks.json・reports/commands.json）が全カテゴリ完全一致
     することは Stage A で別途確認済み（docs/PROXY-1.15.md に記録）。

そのため 573 の ID は、PacketIds.java の既存の 575 の値と同値になる。
このスクリプトはその写像を JSON に書き出す（insert-573-packet-ids.py が使う）。

使い方：
  python3 tools/packet-ids-573.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると /tmp/mc573 を使う（pc/1.15/protocol.json と
  pc/1.15.1/protocol.json を置くこと）。
  --out を省略すると /tmp/mc573/packet-ids-573.json に書き出す。
  終了コード 0 = protocol.json が完全一致し、全エントリを書き出せた。
  1 = 不一致、またはエントリの読み取りに失敗した。
"""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = Path("/tmp/mc573")
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

ENTRY_RE = re.compile(
    r"^\s+([A-Z][A-Z0-9_]*)\(State\.(?:PLAY|LOGIN|HANDSHAKING|CONFIGURATION|STATUS),"
    r" Direction\.(?:CLIENTBOUND|SERVERBOUND),"
    r" (-1|0x[0-9a-fA-F]+),")


def parse_id(token):
    return -1 if token == "-1" else int(token, 16)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--out", type=Path, default=DATA_DIR / "packet-ids-573.json")
    args = parser.parse_args()

    p115 = args.data_dir / "pc" / "1.15" / "protocol.json"
    p1151 = args.data_dir / "pc" / "1.15.1" / "protocol.json"
    b1 = p115.read_bytes()
    b2 = p1151.read_bytes()
    h1 = hashlib.sha256(b1).hexdigest()
    h2 = hashlib.sha256(b2).hexdigest()
    print(f"1.15 protocol.json sha256: {h1}")
    print(f"1.15.1 protocol.json sha256: {h2}")
    if b1 != b2:
        print("NG: 1.15 と 1.15.1 の protocol.json が一致しない。"
              "573 の ID を 575 と同値にはできない。", file=sys.stderr)
        return 1
    print("OK: protocol.json はバイト単位で完全一致")

    entries = {}
    for line in JAVA_FILE.read_text(encoding="utf-8").splitlines():
        m = ENTRY_RE.match(line)
        if m:
            entries[m.group(1)] = parse_id(m.group(2))
    if len(entries) < 200:
        print(f"NG: エントリが少なすぎる（{len(entries)}件）。読み取り失敗の疑い。",
              file=sys.stderr)
        return 1
    print(f"OK: PacketIds のエントリ {len(entries)} 件を読み取った")

    args.out.write_text(json.dumps(entries, indent=1, sort_keys=True),
                        encoding="utf-8")
    print(f"書き出し: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
