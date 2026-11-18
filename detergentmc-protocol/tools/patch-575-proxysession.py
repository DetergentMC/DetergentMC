#!/usr/bin/env python3
"""ProxySession.java に 575（1.15.1）の配線を足す。

575 のワイヤ形式は 578 と完全一致する（Stage A で実 jar のダンプと
protocol.json のバイト一致で確認済み）ため、形式で分岐する箇所は
is578()／isServer578() の条件に is575()／isServer575() を足す。
プロトコル番号そのものが要る箇所（コンストラクタの許可リスト、
リソースパックの選択）だけを個別に足す。

使い方: python3 tools/patch-575-proxysession.py
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
    if "is575()" in text:
        print("skip（既存）: 575 配線はすでにある")
        return 0

    # コンストラクタの許可リスト（serverProtocol）。
    text = sub(text,
               "        if (serverProtocol != ProtocolVersion.V1_15_2\n",
               "        if (serverProtocol != ProtocolVersion.V1_15_1\n"
               "                && serverProtocol != ProtocolVersion.V1_15_2\n", 1)
    text = sub(text,
               '"serverProtocol must be 578 (1.15.2), 758 (1.18.2),',
               '"serverProtocol must be 575 (1.15.1), 578 (1.15.2), 758 (1.18.2),', 1)

    # pack575 フィールド。
    text = sub(text,
               "    private ResourcePack pack578;\n",
               "    private ResourcePack pack578;\n\n"
               "    /**\n"
               "     * 575クライアントに送るリソースパック（pack_format 5）。\n"
               "     * {@code null} の場合は578用パックにフォールバックする\n"
               "     * （どちらも pack_format 5 のため形式上の問題は無い）。\n"
               "     */\n"
               "    private ResourcePack pack575;\n", 1)

    # setPack575 / pack575。
    text = sub(text,
               "    /** 578クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack578(ResourcePack pack578) {",
               "    /** 575クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack575(ResourcePack pack575) {\n"
               "        this.pack575 = pack575;\n"
               "    }\n\n"
               "    /** 575クライアントに送るリソースパック（無ければ null）。 */\n"
               "    public ResourcePack pack575() {\n"
               "        return pack575;\n"
               "    }\n\n"
               "    /** 578クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack578(ResourcePack pack578) {", 1)

    # packFor763LikeClient：575 は pack575 を優先する。
    text = sub(text,
               "        if (is578()) {\n"
               "            if (pack578 != null) {\n"
               "                return pack578;\n"
               "            }",
               "        if (is578() || is575()) {\n"
               "            if (is575() && pack575 != null) {\n"
               "                return pack575;\n"
               "            }\n"
               "            if (pack578 != null) {\n"
               "                return pack578;\n"
               "            }", 1)

    # clientProtocolForServerPackets。
    text = sub(text,
               "return (is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;",
               "return (is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;", 1)

    # is575() の定義（is578() の直後）。
    text = sub(text,
               "    /** クライアントが 1.15.2（578）か。設定フェーズが無い側。 */\n"
               "    private boolean is578() {\n"
               "        return clientProtocol == ProtocolVersion.V1_15_2;\n"
               "    }",
               "    /** クライアントが 1.15.2（578）か。設定フェーズが無い側。 */\n"
               "    private boolean is578() {\n"
               "        return clientProtocol == ProtocolVersion.V1_15_2;\n"
               "    }\n\n"
               "    /**\n"
               "     * クライアントが 1.15.1（575）か。設定フェーズが無い側。\n"
               "     * ワイヤ形式は 578 と完全一致するため、形式の分岐では\n"
               "     * is578() と並べて使う。\n"
               "     */\n"
               "    private boolean is575() {\n"
               "        return clientProtocol == ProtocolVersion.V1_15_1;\n"
               "    }", 1)

    # is763Like。
    text = sub(text,
               "        return is578() || is758() || is759() || is760() || is761() || is762() || is763();",
               "        return is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();", 1)

    # isServer575() の定義（isServer578() の直後）。
    text = sub(text,
               "    /** 内部が 1.15.2（578）か。設定フェーズが無い側。 */\n"
               "    private boolean isServer578() {\n"
               "        return serverProtocol == ProtocolVersion.V1_15_2;\n"
               "    }",
               "    /** 内部が 1.15.2（578）か。設定フェーズが無い側。 */\n"
               "    private boolean isServer578() {\n"
               "        return serverProtocol == ProtocolVersion.V1_15_2;\n"
               "    }\n\n"
               "    /** 内部が 1.15.1（575）か。設定フェーズが無い側。ワイヤ形式は 578 と同一。 */\n"
               "    private boolean isServer575() {\n"
               "        return serverProtocol == ProtocolVersion.V1_15_1;\n"
               "    }", 1)

    # ログイン系：クライアント形式の分岐。
    text = sub(text,
               "if (is758() || is578()) {",
               "if (is758() || is578() || is575()) {", 2)
    # ログイン系：サーバー形式の分岐。
    text = sub(text,
               "(isServer758() || isServer578())",
               "(isServer758() || isServer578() || isServer575())", 6)
    # handleClientPlay の内側（578→758 の事前変換）。
    text = sub(text,
               "            if (is578()) {",
               "            if (is578() || is575()) {", 1)
    # サーバー 578 形式の経路（routeServerPlay578 の呼び出し等）。
    text = sub(text,
               "        if (isServer578()) {",
               "        if (isServer578() || isServer575()) {", 2)
    text = sub(text,
               "!(is758() || is578())",
               "!(is758() || is578() || is575())", 1)
    # Login Success のサーバー形式の分岐（三項）。
    text = sub(text,
               "                            : isServer578()\n",
               "                            : isServer578() || isServer575()\n", 1)
    text = sub(text,
               "|| isServer578())) {",
               "|| isServer578() || isServer575())) {", 1)
    # handleServerPlay のクライアント形式の分岐と Login Success の調整。
    text = sub(text,
               "        if (is578()) {",
               "        if (is578() || is575()) {", 2)
    # プレイ中の ID 付け替え系。
    text = sub(text,
               "if (is762() || is761() || is760() || is759() || is758() || is578()) {",
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575()) {", 2)
    text = sub(text,
               "if (is578() || is758() || is761() || is760() || is759()) {",
               "if (is575() || is578() || is758() || is761() || is760() || is759()) {", 1)

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
