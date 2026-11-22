#!/usr/bin/env python3
"""ClientProxy.java に 575（1.15.1）の配線を足す。

3つのコンストラクタの internalProtocol 許可リストへ 575 を追加し、
pack575 の後付けフィールドとセッションへの受け渡しを 578 と同じ
流儀で足す。

使い方: python3 tools/patch-575-clientproxy.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ClientProxy.java"


def sub(text, old, new, count):
    found = text.count(old)
    if found != count:
        print(f"NG: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")
    if "pack575" in text:
        print("skip（既存）: 575 配線はすでにある")
        return 0

    text = sub(text,
               "        if (internalProtocol != ProtocolVersion.V1_15_2\n",
               "        if (internalProtocol != ProtocolVersion.V1_15_1\n"
               "                && internalProtocol != ProtocolVersion.V1_15_2\n", 3)
    text = sub(text,
               '"internalProtocol must be 578 (1.15.2), 758 (1.18.2),',
               '"internalProtocol must be 575 (1.15.1), 578 (1.15.2), 758 (1.18.2),', 3)

    text = sub(text,
               "    private ResourcePack pack578;\n",
               "    private ResourcePack pack578;\n"
               "    /**\n"
               "     * 575クライアントに送るリソースパック（pack_format 5）。無ければ null。\n"
               "     * 578 と同じ後付けの流儀（{@link #setPack575(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack575;\n", 1)

    text = sub(text,
               "    public void setPack578(ResourcePack pack578) {",
               "    /**\n"
               "     * 575クライアントに送るリソースパックを設定する\n"
               "     * （{@code detergentmc.yml} の {@code resource-pack.url-575} 等）。\n"
               "     * 起動（{@link #start()}）前に呼ぶこと。\n"
               "     */\n"
               "    public void setPack575(ResourcePack pack575) {\n"
               "        this.pack575 = pack575;\n"
               "    }\n\n"
               "    /** 575クライアントに送るリソースパック（未設定なら null）。 */\n"
               "    public ResourcePack pack575() {\n"
               "        return pack575;\n"
               "    }\n\n"
               "    public void setPack578(ResourcePack pack578) {", 1)

    text = sub(text,
               "                session.setPack578(proxy.pack578);",
               "                session.setPack575(proxy.pack575);\n"
               "                session.setPack578(proxy.pack578);", 1)

    P.write_text(text, encoding="utf-8")
    print("ClientProxy.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
