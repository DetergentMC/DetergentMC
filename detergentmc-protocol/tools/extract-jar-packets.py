#!/usr/bin/env python3
"""実バニラ server.jar から ConnectionProtocol のパケット登録順を抜き出す。

javap で ConnectionProtocol（1.16／1.16.1 は `mf`、1.16.2 は `ne`）
の static 初期化子を読み、`PacketFlow`（`flow.a`＝サーバー行き、
`flow.b`＝クライアント行き）ごとに `ldc class` で登録される
パケットクラスの順序を出す。登録順がそのままワイヤ上のパケットID
（0 から連番）になる。クラス名は server_mappings.txt（Mojang 公式
マッピング。piston-meta の server_mappings の sha1 と照合済みのもの）
で難読化解除する。

使い方：
  python3 tools/extract-jar-packets.py --jar run/vanilla/server-1.16.jar \\
      --mappings .tmp-735/packet/server_mappings.txt --proto-class mf \\
      --out detergentmc-protocol/build/remap-data/jar-packets-1.16.json
  javap は JDK 17（~/.tools/jdk-17.0.21+9）を使う。
  終了コード 0 = 全状態・全方向が抜き出せた。

注意：minecraft-data の pc/1.16/protocol.json は pc/1.16.2 とバイト同一で、
実バニラ 1.16 jar の登録順とは食い違う（minecraft-data 側の誤写。
1.16 の Join Game は 0x24 ではなく 0x25 等）。そのため 735 の
パケットIDは minecraft-data からではなくこのスクリプトの jar 実測で
決める（tools/packet-ids-735.py がこの出力を読む）。
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


def load_mappings(path):
    obf2real = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        m = re.match(r"^([\w.$]+) -> ([\w$]+):$", line)
        if m:
            obf2real[m.group(2)] = m.group(1)
    return obf2real


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--mappings", type=Path, required=True)
    parser.add_argument("--proto-class", required=True,
                        help="ConnectionProtocol の難読化クラス名（1.16／1.16.1 は mf、1.16.2 は ne）")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    javap = str(Path.home() / ".tools" / "jdk-17.0.21+9" / "bin" / "javap")
    proc = subprocess.run(
        [javap, "-classpath", str(args.jar), "-p", "-c", args.proto_class],
        capture_output=True, text=True)
    if proc.returncode != 0:
        print(f"javap が失敗した：{proc.stderr}", file=sys.stderr)
        return 1
    obf2real = load_mappings(args.mappings)
    state = None
    direction = None
    cur = []
    out = {}
    for line in proc.stdout.splitlines():
        m = re.search(r"// String (HANDSHAKING|PLAY|STATUS|LOGIN)$", line)
        if m:
            state = m.group(1)
            continue
        m = re.search(r"getstatic\s+#\d+\s+// Field (\w+)\.([ab]):L(\w+);", line)
        if m and state:
            direction = "flow." + m.group(2)
            cur = []
            out.setdefault((state, direction), [])
            continue
        m = re.search(r"// class ([\w$]+)$", line)
        if m and state and direction:
            real = obf2real.get(m.group(1), m.group(1))
            if real.startswith("net.minecraft.network.ConnectionProtocol"):
                continue
            cur.append(real.split(".")[-1])
            out[(state, direction)] = cur
    # 方向の確認：flow.a はサーバー行き、flow.b はクライアント行き。
    for (state, direction), classes in sorted(out.items()):
        if not classes:
            print(f"{state}/{direction} が空", file=sys.stderr)
            return 1
        first = classes[0]
        if direction == "flow.a" and not first.startswith("Serverbound"):
            # HANDSHAKING は ClientIntentionPacket（サーバー行き）が先頭。
            if not (state == "HANDSHAKING" and first == "ClientIntentionPacket"):
                print(f"{state}/{direction} の先頭が {first}（サーバー行きのはず）",
                      file=sys.stderr)
                return 1
        if direction == "flow.b" and not first.startswith("Clientbound"):
            print(f"{state}/{direction} の先頭が {first}（クライアント行きのはず）",
                  file=sys.stderr)
            return 1
    serializable = {f"{s}/{d}": v for (s, d), v in sorted(out.items())}
    args.out.write_text(json.dumps(serializable, indent=1, ensure_ascii=False)
                        + "\n", encoding="utf-8")
    for k, v in serializable.items():
        print(f"{k}: {len(v)} 件")
    print(f"書き出した：{args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
