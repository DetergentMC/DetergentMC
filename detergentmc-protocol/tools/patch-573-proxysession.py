#!/usr/bin/env python3
"""ProxySession.java に 573（1.15）の配線を足す。

573 のワイヤ形式は 575／578 と完全一致する（Stage A で実 jar のダンプと
protocol.json のバイト一致で確認済み）ため、形式で分岐する箇所は
is575()／is578()／isServer575()／isServer578() の条件に
is573()／isServer573() を足す。プロトコル番号そのものが要る箇所
（コンストラクタの許可リスト、リソースパックの選択）だけを個別に足す。

使い方: python3 tools/patch-573-proxysession.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ProxySession.java"


def sub(text, old, new, count):
    found = text.count(old)
    if found != count:
        print(f"NG: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")
    if "is573()" in text:
        print("skip（既存）: 573 配線はすでにある")
        return 0

    # コンストラクタの許可リスト（serverProtocol）。
    text = sub(text,
               "        if (serverProtocol != ProtocolVersion.V1_15_1\n",
               "        if (serverProtocol != ProtocolVersion.V1_15\n"
               "                && serverProtocol != ProtocolVersion.V1_15_1\n", 1)
    text = sub(text,
               '"serverProtocol must be 575 (1.15.1), 578 (1.15.2), 758 (1.18.2),',
               '"serverProtocol must be 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2),', 1)

    # pack573 フィールド（pack575 の直後）。
    text = sub(text,
               "    private ResourcePack pack575;\n",
               "    private ResourcePack pack575;\n\n"
               "    /**\n"
               "     * 573クライアントに送るリソースパック（pack_format 5）。\n"
               "     * {@code null} の場合は575用パックにフォールバックする\n"
               "     * （どちらも pack_format 5 のため形式上の問題は無い）。\n"
               "     */\n"
               "    private ResourcePack pack573;\n", 1)

    # setPack573 / pack573（setPack575 の直前）。
    text = sub(text,
               "    /** 575クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack575(ResourcePack pack575) {",
               "    /** 573クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack573(ResourcePack pack573) {\n"
               "        this.pack573 = pack573;\n"
               "    }\n\n"
               "    /** 573クライアントに送るリソースパック（無ければ null）。 */\n"
               "    public ResourcePack pack573() {\n"
               "        return pack573;\n"
               "    }\n\n"
               "    /** 575クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack575(ResourcePack pack575) {", 1)

    # packFor763LikeClient：573 は pack573 を最優先し、無ければ pack575 に落とす。
    text = sub(text,
               "        if (is578() || is575()) {\n"
               "            if (is575() && pack575 != null) {\n"
               "                return pack575;\n"
               "            }",
               "        if (is578() || is575() || is573()) {\n"
               "            if (is573() && pack573 != null) {\n"
               "                return pack573;\n"
               "            }\n"
               "            if (is575() && pack575 != null) {\n"
               "                return pack575;\n"
               "            }", 1)

    # clientProtocolForServerPackets。
    text = sub(text,
               "return (is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;",
               "return (is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;", 1)

    # is573() の定義（is575() の直後）。
    text = sub(text,
               "    private boolean is575() {\n"
               "        return clientProtocol == ProtocolVersion.V1_15_1;\n"
               "    }",
               "    private boolean is575() {\n"
               "        return clientProtocol == ProtocolVersion.V1_15_1;\n"
               "    }\n\n"
               "    /**\n"
               "     * クライアントが 1.15（573）か。設定フェーズが無い側。\n"
               "     * ワイヤ形式は 575／578 と完全一致するため、形式の分岐では\n"
               "     * is575()／is578() と並べて使う。\n"
               "     */\n"
               "    private boolean is573() {\n"
               "        return clientProtocol == ProtocolVersion.V1_15;\n"
               "    }", 1)

    # is763Like。
    text = sub(text,
               "        return is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
               "        return is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();", 1)

    # isServer573() の定義（isServer575() の直後）。
    text = sub(text,
               "    /** 内部が 1.15.1（575）か。設定フェーズが無い側。ワイヤ形式は 578 と同一。 */\n"
               "    private boolean isServer575() {\n"
               "        return serverProtocol == ProtocolVersion.V1_15_1;\n"
               "    }",
               "    /** 内部が 1.15.1（575）か。設定フェーズが無い側。ワイヤ形式は 578 と同一。 */\n"
               "    private boolean isServer575() {\n"
               "        return serverProtocol == ProtocolVersion.V1_15_1;\n"
               "    }\n\n"
               "    /** 内部が 1.15（573）か。設定フェーズが無い側。ワイヤ形式は 575／578 と同一。 */\n"
               "    private boolean isServer573() {\n"
               "        return serverProtocol == ProtocolVersion.V1_15;\n"
               "    }", 1)

    # ログイン系：クライアント形式の分岐。
    text = sub(text,
               "if (is758() || is578() || is575()) {",
               "if (is758() || is578() || is575() || is573()) {", 2)
    # ログイン系：サーバー形式の分岐。
    text = sub(text,
               "(isServer758() || isServer578() || isServer575())",
               "(isServer758() || isServer578() || isServer575() || isServer573())", 6)
    # handleClientPlay の内側（578→758 の事前変換）。
    text = sub(text,
               "            if (is578() || is575()) {",
               "            if (is578() || is575() || is573()) {", 1)
    # サーバー 578 形式の経路（routeServerPlay578 の呼び出し等）。
    text = sub(text,
               "        if (isServer578() || isServer575()) {",
               "        if (isServer578() || isServer575() || isServer573()) {", 2)
    text = sub(text,
               "!(is758() || is578() || is575())",
               "!(is758() || is578() || is575() || is573())", 1)
    # Login Success のサーバー形式の分岐（三項）。
    text = sub(text,
               "                            : isServer578() || isServer575()\n",
               "                            : isServer578() || isServer575() || isServer573()\n", 1)
    text = sub(text,
               "|| isServer578() || isServer575())) {",
               "|| isServer578() || isServer575() || isServer573())) {", 1)
    # handleServerPlay のクライアント形式の分岐と Login Success の調整。
    text = sub(text,
               "        if (is578() || is575()) {",
               "        if (is578() || is575() || is573()) {", 2)
    # プレイ中の ID 付け替え系。
    text = sub(text,
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575()) {",
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573()) {", 2)
    text = sub(text,
               "if (is575() || is578() || is758() || is761() || is760() || is759()) {",
               "if (is573() || is575() || is578() || is758() || is761() || is760() || is759()) {", 1)

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
