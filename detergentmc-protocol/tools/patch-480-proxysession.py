#!/usr/bin/env python3
"""ProxySession に 480（1.14.1）対応を追加する。

480 のワイヤ形式は 485 と完全一致（パケットID・形式・レジストリとも
差分0件、実データで証明済み）ため、485／490／498 の経路を共用し、
分岐条件に is480()／isServer480() を足すだけにする：

1. 共有の配線（フィールド・検証・ヘルパー・ログイン系の列挙・
   handleClientPlay／handleServerPlay の分岐条件）に 480 を足す。
2. oldClientProtocol()／oldServerProtocol() を 480／485／490／498 の
   4分岐にする（480 列は 485 列と同値だが、参照先として正しく返す）。
3. Trade List の変換は 485 用（demand と canRestock の付け外し）を
   そのまま共用する（480 の Trade List 形式は 485 と同一）。

使い方: python3 tools/patch-480-proxysession.py
冪等：480 が既にあれば何もしない。
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
    if "is480()" in text:
        print("skip（既存）: 480 はすでにある")
        return 0

    # --- フィールド：pack480（pack485 の前） ---
    text = rep(text,
               "    /**\n"
               "     * 485クライアントに送るリソースパック（pack_format 4）。\n"
               "     * {@code null} の場合は490用パックにフォールバックする。\n"
               "     */\n"
               "    private ResourcePack pack485;",
               "    /**\n"
               "     * 480クライアントに送るリソースパック（pack_format 4）。\n"
               "     * {@code null} の場合は485用パックにフォールバックする。\n"
               "     */\n"
               "    private ResourcePack pack480;\n"
               "\n"
               "    /**\n"
               "     * 485クライアントに送るリソースパック（pack_format 4）。\n"
               "     * {@code null} の場合は490用パックにフォールバックする。\n"
               "     */\n"
               "    private ResourcePack pack485;")

    # --- コンストラクタ検証 ---
    text = rep(text,
               "        if (serverProtocol != ProtocolVersion.V1_14_2\n",
               "        if (serverProtocol != ProtocolVersion.V1_14_1\n"
               "                && serverProtocol != ProtocolVersion.V1_14_2\n")
    text = rep(text,
               '"serverProtocol must be 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),',
               '"serverProtocol must be 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15),')

    # --- pack アクセサ（setPack485 の前） ---
    text = rep(text,
               "    /** 485クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack485(ResourcePack pack485) {",
               "    /** 480クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack480(ResourcePack pack480) {\n"
               "        this.pack480 = pack480;\n"
               "    }\n"
               "\n"
               "    /** 480クライアントに送るリソースパック（無ければ null）。 */\n"
               "    public ResourcePack pack480() {\n"
               "        return pack480;\n"
               "    }\n"
               "\n"
               "    /** 485クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack485(ResourcePack pack485) {")

    # --- packFor763LikeClient ---
    text = rep(text,
               "        if (is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            if (is485()) {",
               "        if (is480() || is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            if (is480()) {\n"
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
               "            if (is485()) {")

    # --- is485() を含む分岐条件へ is480() を足す（7件：packFor の外側は
    # 上で処理済みのため、残りは 763化・is763Like・handleClientPlay 内側・
    # handleServerPlay 最終変換・System Chat・Login Success 調整） ---
    text = rep(text,
               "        return (is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;",
               "        return (is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;")
    text = rep(text,
               "        return is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
               "        return is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();")
    text = rep(text,
               "            if (is485() || is490() || is498()) {",
               "            if (is480() || is485() || is490() || is498()) {")
    text = rep(text,
               "        if (is485() || is490() || is498()) {",
               "        if (is480() || is485() || is490() || is498()) {")
    text = rep(text,
               "            // 485／490／498クライアント：763→762→761→760→759→758→498へ六段で変換する。",
               "            // 480／485／490／498クライアント：763→762→761→760→759→758→498へ六段で変換する。")
    text = rep(text,
               "            if (is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) {",
               "            if (is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) {")
    text = rep(text,
               "        if (is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            // 485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。",
               "        if (is480() || is485() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            // 480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。")

    # --- is480() ヘルパー（is485() の前） ---
    text = rep(text,
               "    /**\n"
               "     * クライアントが 1.14.2（485）か。設定フェーズが無い側。\n",
               "    /**\n"
               "     * クライアントが 1.14.1（480）か。設定フェーズが無い側。\n"
               "     * ワイヤ形式は 485 と完全一致（パケットID・形式・レジストリとも\n"
               "     * 差分0件）のため、485／490／498 の経路を共用し、パケットIDの\n"
               "     * 参照先だけ oldClientProtocol() で切り替える。\n"
               "     */\n"
               "    private boolean is480() {\n"
               "        return clientProtocol == ProtocolVersion.V1_14_1;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * クライアントが 1.14.2（485）か。設定フェーズが無い側。\n")

    # --- isServer480() ヘルパー（isServer485() の前） ---
    text = rep(text,
               "    /** 内部が 1.14.2（485）か。設定フェーズが無い側。ワイヤ形式は 490 とほぼ同一。 */\n"
               "    private boolean isServer485() {",
               "    /** 内部が 1.14.1（480）か。設定フェーズが無い側。ワイヤ形式は 485 と完全一致。 */\n"
               "    private boolean isServer480() {\n"
               "        return serverProtocol == ProtocolVersion.V1_14_1;\n"
               "    }\n"
               "\n"
               "    /** 内部が 1.14.2（485）か。設定フェーズが無い側。ワイヤ形式は 490 とほぼ同一。 */\n"
               "    private boolean isServer485() {")

    # --- oldClientProtocol() / oldServerProtocol() を4分岐に ---
    text = rep(text,
               "     * 485／490／498 のうち、このセッションのクライアント側のプロトコル。\n",
               "     * 480／485／490／498 のうち、このセッションのクライアント側のプロトコル。\n")
    text = rep(text,
               "        return is485() ? ProtocolVersion.V1_14_2\n"
               "                : is490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }",
               "        return is480() ? ProtocolVersion.V1_14_1\n"
               "                : is485() ? ProtocolVersion.V1_14_2\n"
               "                : is490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }")
    text = rep(text,
               "     * 485／490／498 のうち、このセッションの内部サーバー側のプロトコル。\n",
               "     * 480／485／490／498 のうち、このセッションの内部サーバー側のプロトコル。\n")
    text = rep(text,
               "        return isServer485() ? ProtocolVersion.V1_14_2\n"
               "                : isServer490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }",
               "        return isServer480() ? ProtocolVersion.V1_14_1\n"
               "                : isServer485() ? ProtocolVersion.V1_14_2\n"
               "                : isServer490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }")

    # --- ログイン系：旧形式サーバーのグループ（9件すべて同じ意味） ---
    text = rep(text,
               "isServer490() || isServer485()",
               "isServer490() || isServer485() || isServer480()",
               9)
    # Login Success（サーバー側の三項） ---
    text = rep(text,
               "                            : isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573()\n",
               "                            : isServer480() || isServer485() || isServer490() || isServer498() || isServer578() || isServer575() || isServer573()\n")
    # --- 旧形式クライアントのグループ（3件：Login Start 前変換・
    # handleClientPlay 前変換・掘削 ack の記録条件） ---
    text = rep(text,
               "(is758() || is485() || is490() || is498() || is578() || is575() || is573())",
               "(is758() || is480() || is485() || is490() || is498() || is578() || is575() || is573())",
               3)
    # --- 内部サーバー経路の注記 ---
    text = rep(text,
               "        // 内部498／490／485：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、\n"
               "        // 最後に758形式から498形式へ変換して送る（490／485 のサーバー行きIDは498と同一）。",
               "        // 内部498／490／485／480：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、\n"
               "        // 最後に758形式から498形式へ変換して送る（490／485／480 のサーバー行きIDは498と同一）。")
    # --- リソースパックの旧式オファー・Join Game の素通し列挙（2件同じ形） ---
    text = rep(text,
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485()) {",
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480()) {",
               2)

    # --- Trade List：480 は 485 用変換をそのまま使う（形式が同一） ---
    text = rep(text,
               "                    is485()\n"
               "                            ? ChunkTranslator.translateTradeList758To485(packet.payload())",
               "                    is480() || is485()\n"
               "                            ? ChunkTranslator.translateTradeList758To485(packet.payload())")
    text = rep(text,
               "                    isServer485()\n"
               "                            ? ChunkTranslator.translateTradeList485To758(packet.payload())",
               "                    isServer480() || isServer485()\n"
               "                            ? ChunkTranslator.translateTradeList485To758(packet.payload())")

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
