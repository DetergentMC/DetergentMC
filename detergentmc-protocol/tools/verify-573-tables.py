#!/usr/bin/env python3
"""573（1.15）対応の Stage A 検証。

確認すること（すべて実データとの照合。推測の裏付けには使わない）：
  1. 実バニラ 1.15／1.15.1 両 jar の data-generator ダンプ
     （reports/registries.json・reports/blocks.json・reports/commands.json）
     が全カテゴリ完全一致すること（件数と中身の両方）。
  2. minecraft-data の protocol.json が 1.15 と 1.15.1 で
     バイト単位で完全一致すること。
  3. PacketIds.java の全エントリで 573 列 == 575 列であること。
  4. RegistryRemapper.java に 573 の定数・委譲メソッドがあること。

使い方：
  python3 tools/verify-573-tables.py [--data-dir DIR]
  --data-dir を省略すると /tmp/mc573 を使う（datagen115/・datagen1151/・
  pc/1.15/・pc/1.15.1/ を置くこと）。ダンプが無い項目は SKIP と表示し、
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
DATA_DIR = Path("/tmp/mc573")
PROTOCOL_DIR = REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"

failures = 0


def check(ok, label, detail=""):
    global failures
    print(("OK  " if ok else "NG  ") + label + (f"（{detail}）" if detail else ""))
    if not ok:
        failures += 1


def compare_datagen(data_dir):
    files = {}
    for tag, sub in (("1.15", "datagen115"), ("1.15.1", "datagen1151")):
        for name in ("registries.json", "blocks.json", "commands.json"):
            p = data_dir / sub / "reports" / name
            if not p.exists():
                print("SKIP datagen ダンプが無い（--data-dir を確認）")
                return
            files[(tag, name)] = json.loads(p.read_text(encoding="utf-8"))
    r1, r2 = files[("1.15", "registries.json")], files[("1.15.1", "registries.json")]
    check(r1 == r2, "registries.json 全カテゴリ完全一致",
          f"{len(r1)} カテゴリ")
    b1, b2 = files[("1.15", "blocks.json")], files[("1.15.1", "blocks.json")]
    states1 = sum(len(v.get("states", [])) for v in b1.values())
    check(b1 == b2, "blocks.json 完全一致",
          f"ブロック {len(b1)} 件・状態 {states1} 件")
    check(files[("1.15", "commands.json")] == files[("1.15.1", "commands.json")],
          "commands.json 完全一致")


def compare_protocol_json(data_dir):
    p1 = data_dir / "pc" / "1.15" / "protocol.json"
    p2 = data_dir / "pc" / "1.15.1" / "protocol.json"
    if not (p1.exists() and p2.exists()):
        print("SKIP protocol.json が無い（--data-dir を確認）")
        return
    check(p1.read_bytes() == p2.read_bytes(),
          "protocol.json（1.15 vs 1.15.1）バイト一致")


def check_packet_ids():
    text = (PROTOCOL_DIR / "PacketIds.java").read_text(encoding="utf-8")
    entry_re = re.compile(
        r"^\s+([A-Z][A-Z0-9_]*)\(State\.(?:PLAY|LOGIN|HANDSHAKING|CONFIGURATION|STATUS),"
        r" Direction\.(?:CLIENTBOUND|SERVERBOUND),"
        r" (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+),", re.M)
    entries = entry_re.findall(text)
    bad = [name for name, a, b in entries if a != b]
    check(len(entries) >= 280 and not bad,
          f"PacketIds 全 {len(entries)} エントリで 573 列 == 575 列",
          ("不一致: " + ",".join(bad[:5])) if bad else "")


def check_remapper():
    text = (PROTOCOL_DIR / "RegistryRemapper.java").read_text(encoding="utf-8")
    methods = [
        "mapBlockState758To573", "mapBlockState573To758",
        "mapItem758To573", "mapItem573To758",
        "mapEntityType758To573", "mapEntityType573To758",
        "mapSound758To573", "mapSound573To758",
        "mapParticle758To573", "mapParticle573To758",
        "mapBlock758To573", "mapBlock573To758",
        "mapMenu758To573", "mapMenu573To758",
        "mapBlockEntityType758To573", "mapBlockEntityType573To758",
        "mapBiome758To573", "mapBiome573To758",
        "biomeNames573",
        "mapDamageType758To573", "mapDamageType573To758",
        "mapSoundName758To573", "mapSoundName573To758",
    ]
    missing = [m for m in methods if f" {m}(" not in text]
    check(not missing, f"RegistryRemapper の 573 メソッド {len(methods)} 件",
          ("不足: " + ",".join(missing)) if missing else "")
    check("MAX_BLOCK_STATE_573 = MAX_BLOCK_STATE_578" in text,
          "MAX_*_573 定数が 578 と同値で定義されている")


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
