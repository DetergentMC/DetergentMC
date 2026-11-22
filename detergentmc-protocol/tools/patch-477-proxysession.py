#!/usr/bin/env python3
"""ProxySession に 477（1.14）対応を追加する。

477 のワイヤ形式は 480 と一致（パケットID・形式の差分0件、レジストリは
変換表に現れない memory_module_type の1件を除き一致。実データで
証明済み）ため、480／485／490／498 の経路を共用し、分岐条件に
is477()／isServer477() を足すだけにする：

1. 共有の配線（フィールド・検証・ヘルパー・ログイン系の列挙・
   handleClientPlay／handleServerPlay の分岐条件）に 477 を足す。
2. oldClientProtocol()／oldServerProtocol() を 477／480／485／490／498 の
   5分岐にする（477 列は 480 列と同値だが、参照先として正しく返す）。
3. Trade List の変換は 485 用（demand と canRestock の付け外し）を
   そのまま共用する（477 の Trade List 形式は 480／485 と同一）。

使い方: python3 tools/patch-477-proxysession.py
冪等：477 が既にあれば何もしない。
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
    if "is477()" in text:
        print("skip（既存）: 477 はすでにある")
        return 0

    # --- フィールド：pack477（pack480 の前） ---
    text = rep(text,
               "    /**\n"
               "     * 480クライアントに送るリソースパック（pack_format 4）。\n"
               "     * {@code null} の場合は485用パックにフォールバックする。\n"
               "     */\n"
               "    private ResourcePack pack480;",
               "    /**\n"
               "     * 477クライアントに送るリソースパック（pack_format 4）。\n"
               "     * {@code null} の場合は480用パックにフォールバックする。\n"
               "     */\n"
               "    private ResourcePack pack477;\n"
               "\n"
               "    /**\n"
               "     * 480クライアントに送るリソースパック（pack_format 4）。\n"
               "     * {@code null} の場合は485用パックにフォールバックする。\n"
               "     */\n"
               "    private ResourcePack pack480;")

    # --- コンストラクタ検証 ---
    text = rep(text,
               "        if (serverProtocol != ProtocolVersion.V1_14_1\n",
               "        if (serverProtocol != ProtocolVersion.V1_14\n"
               "                && serverProtocol != ProtocolVersion.V1_14_1\n")
    text = rep(text,
               '"serverProtocol must be 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),',
               '"serverProtocol must be 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),')

    # --- pack アクセサ（setPack480 の前） ---
    text = rep(text,
               "    /** 480クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack480(ResourcePack pack480) {",
               "    /** 477クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack477(ResourcePack pack477) {\n"
               "        this.pack477 = pack477;\n"
               "    }\n"
               "\n"
               "    /** 477クライアントに送るリソースパック（無ければ null）。 */\n"
               "    public ResourcePack pack477() {\n"
               "        return pack477;\n"
               "    }\n"
               "\n"
               "    /** 480クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack480(ResourcePack pack480) {")

    # --- packFor763LikeClient ---
    text = rep(text,
               "        if (is480() || is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            if (is480()) {",
               "        if (is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            if (is477()) {\n"
               "                if (pack477 != null) {\n"
               "                    return pack477;\n"
               "                }\n"
               "                if (pack480 != null) {\n"
               "                    return pack480;\n"
               "                }\n"
               "                if (pack485 != null) {\n"
               "                    return pack485;\n"
               "                }\n"
               "                if (pack490 != null) {\n"
               "                    return pack490;\n"
               "                }\n"
               "            }\n"
               "            if (is480()) {")

    # --- is480() を含む分岐条件へ is477() を足す ---
    text = rep(text,
               "        return (is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;",
               "        return (is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;")
    text = rep(text,
               "        return is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
               "        return is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();")
    text = rep(text,
               "            if (is480() || is485() || is490() || is498()) {",
               "            if (is477() || is480() || is485() || is490() || is498()) {")
    text = rep(text,
               "        if (is480() || is485() || is490() || is498()) {",
               "        if (is477() || is480() || is485() || is490() || is498()) {")
    text = rep(text,
               "            // 480／485／490／498クライアント：763→762→761→760→759→758→498へ六段で変換する。",
               "            // 477／480／485／490／498クライアント：763→762→761→760→759→758→498へ六段で変換する。")
    text = rep(text,
               "            if (is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) {",
               "            if (is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) {")
    text = rep(text,
               "        if (is480() || is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            // 480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。",
               "        if (is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            // 477／480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。")

    # --- is477() ヘルパー（is480() の前） ---
    text = rep(text,
               "    /**\n"
               "     * クライアントが 1.14.1（480）か。設定フェーズが無い側。\n",
               "    /**\n"
               "     * クライアントが 1.14（477）か。設定フェーズが無い側。\n"
               "     * ワイヤ形式は 480 と一致（パケットID・形式の差分0件、レジストリは\n"
               "     * 変換表に現れない memory_module_type の1件を除き一致）のため、\n"
               "     * 480／485／490／498 の経路を共用し、パケットIDの参照先だけ\n"
               "     * oldClientProtocol() で切り替える。\n"
               "     */\n"
               "    private boolean is477() {\n"
               "        return clientProtocol == ProtocolVersion.V1_14;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * クライアントが 1.14.1（480）か。設定フェーズが無い側。\n")

    # --- isServer477() ヘルパー（isServer480() の前） ---
    text = rep(text,
               "    /** 内部が 1.14.1（480）か。設定フェーズが無い側。ワイヤ形式は 485 と完全一致。 */\n"
               "    private boolean isServer480() {",
               "    /** 内部が 1.14（477）か。設定フェーズが無い側。ワイヤ形式は 480 と一致。 */\n"
               "    private boolean isServer477() {\n"
               "        return serverProtocol == ProtocolVersion.V1_14;\n"
               "    }\n"
               "\n"
               "    /** 内部が 1.14.1（480）か。設定フェーズが無い側。ワイヤ形式は 485 と完全一致。 */\n"
               "    private boolean isServer480() {")

    # --- oldClientProtocol() / oldServerProtocol() を5分岐に ---
    text = rep(text,
               "     * 480／485／490／498 のうち、このセッションのクライアント側のプロトコル。\n",
               "     * 477／480／485／490／498 のうち、このセッションのクライアント側のプロトコル。\n")
    text = rep(text,
               "        return is480() ? ProtocolVersion.V1_14_1\n"
               "                : is485() ? ProtocolVersion.V1_14_2\n"
               "                : is490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }",
               "        return is477() ? ProtocolVersion.V1_14\n"
               "                : is480() ? ProtocolVersion.V1_14_1\n"
               "                : is485() ? ProtocolVersion.V1_14_2\n"
               "                : is490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }")
    text = rep(text,
               "     * 480／485／490／498 のうち、このセッションの内部サーバー側のプロトコル。\n",
               "     * 477／480／485／490／498 のうち、このセッションの内部サーバー側のプロトコル。\n")
    text = rep(text,
               "        return isServer480() ? ProtocolVersion.V1_14_1\n"
               "                : isServer485() ? ProtocolVersion.V1_14_2\n"
               "                : isServer490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }",
               "        return isServer477() ? ProtocolVersion.V1_14\n"
               "                : isServer480() ? ProtocolVersion.V1_14_1\n"
               "                : isServer485() ? ProtocolVersion.V1_14_2\n"
               "                : isServer490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }")

    # --- ログイン系：旧形式サーバーのグループ（9件すべて同じ意味） ---
    text = rep(text,
               "isServer485() || isServer480()",
               "isServer485() || isServer480() || isServer477()",
               9)
    # Login Success（サーバー側の三項） ---
    text = rep(text,
               "                            : isServer480() || isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573()\n",
               "                            : isServer477() || isServer480() || isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573()\n")
    # --- 旧形式クライアントのグループ（3件：Login Start 前変換・
    # handleClientPlay 前変換・掘削 ack の記録条件） ---
    text = rep(text,
               "(is758() || is480() || is485() || is490() || is498() || is578() || is575() || is573())",
               "(is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573())",
               3)
    # --- 内部サーバー経路の注記 ---
    text = rep(text,
               "        // 内部498／490／485／480：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、\n"
               "        // 最後に758形式から498形式へ変換して送る（490／485／480 のサーバー行きIDは498と同一）。",
               "        // 内部498／490／485／480／477：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、\n"
               "        // 最後に758形式から498形式へ変換して送る（490／485／480／477 のサーバー行きIDは498と同一）。")
    # --- リソースパックの旧式オファー・Join Game の素通し列挙（2件同じ形） ---
    text = rep(text,
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480()) {",
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480() || is477()) {",
               2)

    # --- Trade List：477 は 485 用変換をそのまま使う（形式が同一） ---
    text = rep(text,
               "                    is480() || is485()\n"
               "                            ? ChunkTranslator.translateTradeList758To485(packet.payload())",
               "                    is477() || is480() || is485()\n"
               "                            ? ChunkTranslator.translateTradeList758To485(packet.payload())")
    text = rep(text,
               "                    isServer480() || isServer485()\n"
               "                            ? ChunkTranslator.translateTradeList485To758(packet.payload())",
               "                    isServer477() || isServer480() || isServer485()\n"
               "                            ? ChunkTranslator.translateTradeList485To758(packet.payload())")

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
