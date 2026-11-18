#!/usr/bin/env python3
"""ProxySession に 485（1.14.2）対応を追加する。

485 のワイヤ形式は 490 とほぼ同一（パケットIDは完全一致＝差分0件、
形式の差は Trade List の canRestock の有無だけ）ため、490／498 の
経路を共用し、差のある箇所だけ分岐する：

1. 共有の配線（フィールド・検証・ヘルパー・ログイン系の列挙・
   handleClientPlay／handleServerPlay の分岐条件）に is485()／
   isServer485() を足す。
2. oldClientProtocol()／oldServerProtocol() を 485／490／498 の
   3分岐にする（485 列は 490 列と同値だが、参照先として正しく返す）。
3. Trade List の変換だけ 485 用（demand と canRestock の付け外し）に
   分岐する。

使い方: python3 tools/patch-485-proxysession.py
冪等：485 が既にあれば何もしない。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ProxySession.java"


def fail(msg):
    print(f"NG: {msg}", file=sys.stderr)
    sys.exit(1)


def rep(text, old, new, count=1):
    n = text.count(old)
    if n != count:
        fail(f"置換対象 {old[:70]!r} が {n} 件（期待 {count}）")
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")
    if "is485()" in text:
        print("skip（既存）: 485 はすでにある")
        return 0

    # --- フィールド：pack485（pack490 の前） ---
    text = rep(text,
               "    /**\n"
               "     * 490クライアントに送るリソースパック（pack_format 4）。\n",
               "    /**\n"
               "     * 485クライアントに送るリソースパック（pack_format 4）。\n"
               "     * {@code null} の場合は490用パックにフォールバックする。\n"
               "     */\n"
               "    private ResourcePack pack485;\n"
               "\n"
               "    /**\n"
               "     * 490クライアントに送るリソースパック（pack_format 4）。\n")

    # --- コンストラクタ検証 ---
    text = rep(text,
               "        if (serverProtocol != ProtocolVersion.V1_14_3\n",
               "        if (serverProtocol != ProtocolVersion.V1_14_2\n"
               "                && serverProtocol != ProtocolVersion.V1_14_3\n")
    text = rep(text,
               '"serverProtocol must be 490 (1.14.3), 498 (1.14.4), 573 (1.15),',
               '"serverProtocol must be 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),')

    # --- pack アクセサ（setPack490 の前） ---
    text = rep(text,
               "    /** 490クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack490(ResourcePack pack490) {",
               "    /** 485クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack485(ResourcePack pack485) {\n"
               "        this.pack485 = pack485;\n"
               "    }\n"
               "\n"
               "    /** 485クライアントに送るリソースパック（無ければ null）。 */\n"
               "    public ResourcePack pack485() {\n"
               "        return pack485;\n"
               "    }\n"
               "\n"
               "    /** 490クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack490(ResourcePack pack490) {")

    # --- packFor763LikeClient ---
    text = rep(text,
               "        if (is490() || is498() || is578() || is575() || is573()) {\n"
               "            if (is490() && pack490 != null) {\n"
               "                return pack490;\n"
               "            }",
               "        if (is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            if (is485()) {\n"
               "                if (pack485 != null) {\n"
               "                    return pack485;\n"
               "                }\n"
               "                if (pack490 != null) {\n"
               "                    return pack490;\n"
               "                }\n"
               "            }\n"
               "            if (is490() && pack490 != null) {\n"
               "                return pack490;\n"
               "            }")

    # --- 763 化ヘルパー ---
    text = rep(text,
               "        return (is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;",
               "        return (is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;")

    # --- is485() ヘルパー（is490() の前） ---
    text = rep(text,
               "    /**\n"
               "     * クライアントが 1.14.3（490）か。設定フェーズが無い側。\n",
               "    /**\n"
               "     * クライアントが 1.14.2（485）か。設定フェーズが無い側。\n"
               "     * ワイヤ形式は 490 とほぼ同一（パケットIDは完全一致、差は\n"
               "     * Trade List の canRestock の有無だけ）のため、490／498 の\n"
               "     * 経路を共用し、パケットIDの参照先だけ oldClientProtocol() で\n"
               "     * 切り替える。\n"
               "     */\n"
               "    private boolean is485() {\n"
               "        return clientProtocol == ProtocolVersion.V1_14_2;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * クライアントが 1.14.3（490）か。設定フェーズが無い側。\n")

    # --- is763Like() ---
    text = rep(text,
               "        return is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
               "        return is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();")

    # --- isServer485() ヘルパー（isServer490() の前） ---
    text = rep(text,
               "    /** 内部が 1.14.3（490）か。設定フェーズが無い側。ワイヤ形式は 498 とほぼ同一。 */\n"
               "    private boolean isServer490() {",
               "    /** 内部が 1.14.2（485）か。設定フェーズが無い側。ワイヤ形式は 490 とほぼ同一。 */\n"
               "    private boolean isServer485() {\n"
               "        return serverProtocol == ProtocolVersion.V1_14_2;\n"
               "    }\n"
               "\n"
               "    /** 内部が 1.14.3（490）か。設定フェーズが無い側。ワイヤ形式は 498 とほぼ同一。 */\n"
               "    private boolean isServer490() {")

    # --- oldClientProtocol() / oldServerProtocol() を3分岐に ---
    text = rep(text,
               "     * 490／498 のうち、このセッションのクライアント側のプロトコル。\n",
               "     * 485／490／498 のうち、このセッションのクライアント側のプロトコル。\n")
    text = rep(text,
               "        return is490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }",
               "        return is485() ? ProtocolVersion.V1_14_2\n"
               "                : is490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }")
    text = rep(text,
               "     * 490／498 のうち、このセッションの内部サーバー側のプロトコル。\n",
               "     * 485／490／498 のうち、このセッションの内部サーバー側のプロトコル。\n")
    text = rep(text,
               "        return isServer490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }",
               "        return isServer485() ? ProtocolVersion.V1_14_2\n"
               "                : isServer490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }")

    # --- ログイン系：旧形式サーバーのグループ（7件すべて同じ意味） ---
    text = rep(text,
               "isServer758() || isServer498() || isServer490() || isServer578() || isServer575() || isServer573()",
               "isServer758() || isServer498() || isServer490() || isServer485() || isServer578() || isServer575() || isServer573()",
               7)
    # Login Success（サーバー側の三項） ---
    text = rep(text,
               "                            : isServer490() || isServer498() || isServer578() || isServer575() || isServer573()\n",
               "                            : isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573()\n")
    # --- 旧形式クライアントのグループ（3件：Login Start 前変換・
    # handleClientPlay 前変換・掘削 ack の記録条件） ---
    text = rep(text,
               "(is758() || is490() || is498() || is578() || is575() || is573())",
               "(is758() || is485() || is490() || is498() || is578() || is575() || is573())",
               3)
    # --- handleClientPlay：前変換の内側と最終変換の条件（字下げ違いで各1件） ---
    text = rep(text,
               "            if (is490() || is498()) {",
               "            if (is485() || is490() || is498()) {")
    text = rep(text,
               "        if (is490() || is498()) {",
               "        if (is485() || is490() || is498()) {")
    # --- 内部485サーバーへの経路（同じ形が2件） ---
    text = rep(text,
               "        if (isServer498() || isServer490()) {",
               "        if (isServer498() || isServer490() || isServer485()) {",
               2)
    text = rep(text,
               "        // 内部498／490：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、\n"
               "        // 最後に758形式から498形式へ変換して送る（490 のサーバー行きIDは498と同一）。",
               "        // 内部498／490／485：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、\n"
               "        // 最後に758形式から498形式へ変換して送る（490／485 のサーバー行きIDは498と同一）。")
    # --- handleServerPlay：485／490／498クライアント向けの最終変換の注記 ---
    text = rep(text,
               "            // 490／498クライアント：763→762→761→760→759→758→498へ六段で変換する。",
               "            // 485／490／498クライアント：763→762→761→760→759→758→498へ六段で変換する。")
    # --- リソースパックの旧式オファー・Join Game の素通し列挙（2件同じ形） ---
    text = rep(text,
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490()) {",
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485()) {",
               2)
    # --- System Chat 等の素通し列挙 ---
    text = rep(text,
               "            if (is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) {",
               "            if (is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) {")
    # --- adjustLoginSuccessPayload（クライアント向け Login Success） ---
    text = rep(text,
               "        if (is490() || is498() || is578() || is575() || is573()) {\n"
               "            // 490／498／578 の Login Success は UUID（文字列）＋名前だけ。",
               "        if (is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            // 485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。")

    # --- Trade List：クライアント向け（758→旧形式） ---
    text = rep(text,
               "                    is490()\n"
               "                            ? ChunkTranslator.translateTradeList758To490(packet.payload())\n"
               "                            : ChunkTranslator.translateTradeList758To498(packet.payload())));",
               "                    is485()\n"
               "                            ? ChunkTranslator.translateTradeList758To485(packet.payload())\n"
               "                            : is490()\n"
               "                            ? ChunkTranslator.translateTradeList758To490(packet.payload())\n"
               "                            : ChunkTranslator.translateTradeList758To498(packet.payload())));")
    # --- Trade List：サーバー向け（旧形式→758） ---
    text = rep(text,
               "                    isServer490()\n"
               "                            ? ChunkTranslator.translateTradeList490To758(packet.payload())\n"
               "                            : ChunkTranslator.translateTradeList498To758(packet.payload())));",
               "                    isServer485()\n"
               "                            ? ChunkTranslator.translateTradeList485To758(packet.payload())\n"
               "                            : isServer490()\n"
               "                            ? ChunkTranslator.translateTradeList490To758(packet.payload())\n"
               "                            : ChunkTranslator.translateTradeList498To758(packet.payload())));")

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
