#!/usr/bin/env python3
"""ClientProxy.java に 573（1.15）の配線を足す。

3つのコンストラクタの internalProtocol 許可リストへ 573 を追加し、
pack573 の後付けフィールドとセッションへの受け渡しを 575／578 と同じ
流儀で足す。

使い方: python3 tools/patch-573-clientproxy.py
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
    if "pack573" in text:
        print("skip（既存）: 573 配線はすでにある")
        return 0

    text = sub(text,
               "        if (internalProtocol != ProtocolVersion.V1_15_1\n",
               "        if (internalProtocol != ProtocolVersion.V1_15\n"
               "                && internalProtocol != ProtocolVersion.V1_15_1\n", 3)
    text = sub(text,
               '"internalProtocol must be 575 (1.15.1), 578 (1.15.2), 758 (1.18.2),',
               '"internalProtocol must be 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2),', 3)

    text = sub(text,
               "    private ResourcePack pack575;\n",
               "    private ResourcePack pack575;\n"
               "    /**\n"
               "     * 573クライアントに送るリソースパック（pack_format 5）。無ければ null。\n"
               "     * 575 と同じ後付けの流儀（{@link #setPack573(ResourcePack)}）。\n"
               "     */\n"
               "    private ResourcePack pack573;\n", 1)

    text = sub(text,
               "    /**\n"
               "     * 575クライアントに送るリソースパックを設定する\n",
               "    /**\n"
               "     * 573クライアントに送るリソースパックを設定する\n"
               "     * （{@code detergentmc.yml} の {@code resource-pack.url-573} 等）。\n"
               "     * 起動（{@link #start()}）前に呼ぶこと。\n"
               "     */\n"
               "    public void setPack573(ResourcePack pack573) {\n"
               "        this.pack573 = pack573;\n"
               "    }\n\n"
               "    /** 573クライアントに送るリソースパック（未設定なら null）。 */\n"
               "    public ResourcePack pack573() {\n"
               "        return pack573;\n"
               "    }\n\n"
               "    /**\n"
               "     * 575クライアントに送るリソースパックを設定する\n", 1)

    text = sub(text,
               "                session.setPack575(proxy.pack575);",
               "                session.setPack573(proxy.pack573);\n"
               "                session.setPack575(proxy.pack575);", 1)

    P.write_text(text, encoding="utf-8")
    print("ClientProxy.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
