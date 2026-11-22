#!/usr/bin/env python3
"""575（1.15.1）対応の Stage A 検証。

確認すること（すべて実データとの照合。推測の裏付けには使わない）：
  1. 実バニラ 1.15.1／1.15.2 両 jar の data-generator ダンプ
     （reports/registries.json・reports/blocks.json）が全カテゴリ
     完全一致すること（件数と中身の両方）。
  2. minecraft-data の protocol.json が 1.15.1 と 1.15.2 で
     バイト単位で完全一致すること。
  3. PacketIds.java の全エントリで 575 列 == 578 列であること。
  4. RegistryRemapper.java に 575 の定数・委譲メソッドがあること。

使い方：
  python3 tools/verify-575-tables.py [--data-dir DIR]
  --data-dir を省略すると /tmp/mc575 を使う（datagen1151/・datagen1152/・
  pc/1.15.1/・pc/1.15.2/ を置くこと）。ダンプが無い項目は SKIP と表示し、
  残りの確認だけで判定する。
  終了コード 0 = 実行した確認がすべて一致。1 = 不一致あり。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = Path("/tmp/mc575")
PROTOCOL_DIR = REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"

failures = 0


def check(ok, label, detail=""):
    global failures
    print(("OK  " if ok else "NG  ") + label + (f"（{detail}）" if detail else ""))
    if not ok:
        failures += 1


def compare_datagen(data_dir):
    r1p = data_dir / "datagen1151" / "reports" / "registries.json"
    r2p = data_dir / "datagen1152" / "reports" / "registries.json"
    b1p = data_dir / "datagen1151" / "reports" / "blocks.json"
    b2p = data_dir / "datagen1152" / "reports" / "blocks.json"
    if not (r1p.exists() and r2p.exists() and b1p.exists() and b2p.exists()):
        print("SKIP datagen ダンプが無い（--data-dir を確認）")
        return
    r1 = json.loads(r1p.read_text(encoding="utf-8"))
    r2 = json.loads(r2p.read_text(encoding="utf-8"))
    check(r1 == r2, "registries.json 全カテゴリ完全一致",
          f"{len(r1)} カテゴリ")
    b1 = json.loads(b1p.read_text(encoding="utf-8"))
    b2 = json.loads(b2p.read_text(encoding="utf-8"))
    states1 = sum(len(v.get("states", [])) for v in b1.values())
    check(b1 == b2, "blocks.json 完全一致",
          f"ブロック {len(b1)} 件・状態 {states1} 件")


def compare_protocol_json(data_dir):
    p1 = data_dir / "pc" / "1.15.1" / "protocol.json"
    p2 = data_dir / "pc" / "1.15.2" / "protocol.json"
    if not (p1.exists() and p2.exists()):
        print("SKIP protocol.json が無い（--data-dir を確認）")
        return
    check(p1.read_bytes() == p2.read_bytes(),
          "protocol.json（1.15.1 vs 1.15.2）バイト一致")


def check_packet_ids():
    text = (PROTOCOL_DIR / "PacketIds.java").read_text(encoding="utf-8")
    entry_re = re.compile(
        r"^\s+([A-Z][A-Z0-9_]*)\(State\.(?:PLAY|LOGIN|HANDSHAKING|CONFIGURATION|STATUS),"
        r" Direction\.(?:CLIENTBOUND|SERVERBOUND),"
        r" (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+),", re.M)
    entries = entry_re.findall(text)
    bad = [name for name, a, b in entries if a != b]
    check(len(entries) >= 280 and not bad,
          f"PacketIds 全 {len(entries)} エントリで 575 列 == 578 列",
          ("不一致: " + ",".join(bad[:5])) if bad else "")


def check_remapper():
    text = (PROTOCOL_DIR / "RegistryRemapper.java").read_text(encoding="utf-8")
    methods = [
        "mapBlockState758To575", "mapBlockState575To758",
        "mapItem758To575", "mapItem575To758",
        "mapEntityType758To575", "mapEntityType575To758",
        "mapSound758To575", "mapSound575To758",
        "mapParticle758To575", "mapParticle575To758",
        "mapBlock758To575", "mapBlock575To758",
        "mapMenu758To575", "mapMenu575To758",
        "mapBlockEntityType758To575", "mapBlockEntityType575To758",
        "mapBiome758To575", "mapBiome575To758",
        "biomeNames575",
        "mapDamageType758To575", "mapDamageType575To758",
        "mapSoundName758To575", "mapSoundName575To758",
    ]
    missing = [m for m in methods if f" {m}(" not in text]
    check(not missing, f"RegistryRemapper の 575 メソッド {len(methods)} 件",
          ("不足: " + ",".join(missing)) if missing else "")
    check("MAX_BLOCK_STATE_575 = MAX_BLOCK_STATE_578" in text,
          "MAX_*_575 定数が 578 と同値で定義されている")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args()
    compare_datagen(args.data_dir)
    compare_protocol_json(args.data_dir)
    check_packet_ids()
    check_remapper()
    print("結果:", "全件一致" if failures == 0 else f"{failures} 件不一致")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
