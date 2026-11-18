#!/usr/bin/env python3
"""ProxySession に 490（1.14.3）対応を追加する。

490 のワイヤ形式は 498 とほぼ同一（パケットIDの差は
acknowledge_player_digging の有無だけ、形式の差は Trade List の
demand だけ）ため、498 の経路を共用し、差のある箇所だけ分岐する：

1. 共有の配線（フィールド・検証・ヘルパー・ログイン系の列挙・
   handleClientPlay／handleServerPlay の分岐条件）に is490()／
   isServer490() を足す。
2. 498 専用メソッド群の中のパケットID参照（PacketIds の 498 列）を、
   クライアント側は oldClientProtocol()、サーバー側は
   oldServerProtocol() に置き換える。490 では
   acknowledge_player_digging の ID が -1 のため、クライアント向けは
   既存の「存在しないパケットは落とす」経路で自動的に落ち、
   サーバー側は名前が引けず素通し側に倒れる。
3. Trade List の変換だけ 490 用（demand の付け外し）に分岐する。

使い方: python3 tools/patch-490-proxysession.py
冪等：490 が既にあれば何もしない。
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
    if "is490()" in text:
        print("skip（既存）: 490 はすでにある")
        return 0

    # --- フィールド：pack490（pack498 の前） ---
    text = rep(text,
               "    /**\n"
               "     * 498クライアントに送るリソースパック（pack_format 4）。\n",
               "    /**\n"
               "     * 490クライアントに送るリソースパック（pack_format 4）。\n"
               "     * {@code null} の場合は498用パックにフォールバックする。\n"
               "     */\n"
               "    private ResourcePack pack490;\n"
               "\n"
               "    /**\n"
               "     * 498クライアントに送るリソースパック（pack_format 4）。\n")

    # --- コンストラクタ検証 ---
    text = rep(text,
               "        if (serverProtocol != ProtocolVersion.V1_14_4\n",
               "        if (serverProtocol != ProtocolVersion.V1_14_3\n"
               "                && serverProtocol != ProtocolVersion.V1_14_4\n")
    text = rep(text,
               '"serverProtocol must be 498 (1.14.4), 573 (1.15),',
               '"serverProtocol must be 490 (1.14.3), 498 (1.14.4), 573 (1.15),')

    # --- pack アクセサ（setPack498 の前） ---
    text = rep(text,
               "    /** 498クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack498(ResourcePack pack498) {",
               "    /** 490クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack490(ResourcePack pack490) {\n"
               "        this.pack490 = pack490;\n"
               "    }\n"
               "\n"
               "    /** 490クライアントに送るリソースパック（無ければ null）。 */\n"
               "    public ResourcePack pack490() {\n"
               "        return pack490;\n"
               "    }\n"
               "\n"
               "    /** 498クライアントに送るリソースパックを設定する（無ければ null）。 */\n"
               "    public void setPack498(ResourcePack pack498) {")

    # --- packFor763LikeClient ---
    text = rep(text,
               "        if (is498() || is578() || is575() || is573()) {\n"
               "            if (is498() && pack498 != null) {",
               "        if (is490() || is498() || is578() || is575() || is573()) {\n"
               "            if (is490() && pack490 != null) {\n"
               "                return pack490;\n"
               "            }\n"
               "            if (is498() && pack498 != null) {")

    # --- 763 化ヘルパー ---
    text = rep(text,
               "        return (is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;",
               "        return (is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) ? 763 : clientProtocol;")

    # --- is490() ヘルパー（is498() の前） ---
    text = rep(text,
               "    /**\n"
               "     * クライアントが 1.14.4（498）か。設定フェーズが無い側。\n",
               "    /**\n"
               "     * クライアントが 1.14.3（490）か。設定フェーズが無い側。\n"
               "     * ワイヤ形式は 498 とほぼ同一（差は acknowledge_player_digging の\n"
               "     * 有無と Trade List の demand だけ）のため、498 の経路を共用し、\n"
               "     * パケットIDの参照先だけ oldClientProtocol() で切り替える。\n"
               "     */\n"
               "    private boolean is490() {\n"
               "        return clientProtocol == ProtocolVersion.V1_14_3;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * クライアントが 1.14.4（498）か。設定フェーズが無い側。\n")

    # --- is763Like() ---
    text = rep(text,
               "        return is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
               "        return is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();")

    # --- isServer490() ヘルパー（isServer498() の前） ---
    text = rep(text,
               "    /** 内部が 1.14.4（498）か。設定フェーズが無い側。 */\n"
               "    private boolean isServer498() {",
               "    /** 内部が 1.14.3（490）か。設定フェーズが無い側。ワイヤ形式は 498 とほぼ同一。 */\n"
               "    private boolean isServer490() {\n"
               "        return serverProtocol == ProtocolVersion.V1_14_3;\n"
               "    }\n"
               "\n"
               "    /** 内部が 1.14.4（498）か。設定フェーズが無い側。 */\n"
               "    private boolean isServer498() {")

    # --- oldClientProtocol() / oldServerProtocol()（isServer490 の直後） ---
    text = rep(text,
               "    /** 内部が 1.18.2（758）か。設定フェーズが無い側。 */\n"
               "    private boolean isServer758() {",
               "    /**\n"
               "     * 490／498 のうち、このセッションのクライアント側のプロトコル。\n"
               "     * 498 経路のパケットID参照先を切り替えるために使う。\n"
               "     */\n"
               "    private int oldClientProtocol() {\n"
               "        return is490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }\n"
               "\n"
               "    /**\n"
               "     * 490／498 のうち、このセッションの内部サーバー側のプロトコル。\n"
               "     * 498 経路のパケットID参照先を切り替えるために使う。\n"
               "     */\n"
               "    private int oldServerProtocol() {\n"
               "        return isServer490() ? ProtocolVersion.V1_14_3 : ProtocolVersion.V1_14_4;\n"
               "    }\n"
               "\n"
               "    /** 内部が 1.18.2（758）か。設定フェーズが無い側。 */\n"
               "    private boolean isServer758() {")

    # --- ログイン系：旧形式サーバーのグループ（6件すべて同じ意味） ---
    text = rep(text,
               "(isServer758() || isServer498() || isServer578() || isServer575() || isServer573())",
               "(isServer758() || isServer498() || isServer490() || isServer578() || isServer575() || isServer573())",
               6)
    # Login Start のクライアント側グループ（handleClientLogin 側）
    text = rep(text,
               "                if (is758() || is498() || is578() || is575() || is573()) {\n"
               "                    payload = (isServer758() || isServer498() || isServer490() || isServer578() || isServer575() || isServer573())",
               "                if (is758() || is490() || is498() || is578() || is575() || is573()) {\n"
               "                    payload = (isServer758() || isServer498() || isServer490() || isServer578() || isServer575() || isServer573())")
    # Login Success（サーバー側）
    text = rep(text,
               "                            : isServer498() || isServer578() || isServer575() || isServer573()\n",
               "                            : isServer490() || isServer498() || isServer578() || isServer575() || isServer573()\n")
    # 設定フェーズ無しのグループ（Login Success 後の分岐）
    text = rep(text,
               "isServer758() || isServer498() || isServer578() || isServer575() || isServer573())) {",
               "isServer758() || isServer498() || isServer490() || isServer578() || isServer575() || isServer573())) {")
    # adjustLoginSuccessPayload（クライアント向け Login Success）
    text = rep(text,
               "        if (is498() || is578() || is575() || is573()) {\n"
               "            // 498／578 の Login Success は UUID（文字列）＋名前だけ。",
               "        if (is490() || is498() || is578() || is575() || is573()) {\n"
               "            // 490／498／578 の Login Success は UUID（文字列）＋名前だけ。")

    # --- handleClientPlay：クライアント→サーバーの前変換 ---
    text = rep(text,
               "        if (is758() || is498() || is578() || is575() || is573()) {\n"
               "            if (is498()) {",
               "        if (is758() || is490() || is498() || is578() || is575() || is573()) {\n"
               "            if (is490() || is498()) {")

    # --- handleClientPlay：内部490サーバーへの経路 ---
    text = rep(text,
               "        // 内部498：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、\n"
               "        // 最後に758形式から498形式へ変換して送る。\n"
               "        if (isServer498()) {",
               "        // 内部498／490：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、\n"
               "        // 最後に758形式から498形式へ変換して送る（490 のサーバー行きIDは498と同一）。\n"
               "        if (isServer498() || isServer490()) {")

    # --- 掘削 ack の記録条件 ---
    text = rep(text,
               "!(is758() || is498() || is578() || is575() || is573())",
               "!(is758() || is490() || is498() || is578() || is575() || is573())")

    # --- handleServerPlay：490／498クライアント向けの最終変換 ---
    text = rep(text,
               "        if (is498()) {\n"
               "            // 498クライアント：763→762→761→760→759→758→498へ六段で変換する。",
               "        if (is490() || is498()) {\n"
               "            // 490／498クライアント：763→762→761→760→759→758→498へ六段で変換する。")

    # --- handleServerPlayRouted：内部490サーバーの経路 ---
    text = rep(text,
               "        if (isServer498()) {\n"
               "            return routeServerPlay498(packet);\n"
               "        }",
               "        if (isServer498() || isServer490()) {\n"
               "            return routeServerPlay498(packet);\n"
               "        }")

    # --- リソースパックの旧式オファー・Join Game の素通し列挙（2件同じ形） ---
    text = rep(text,
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498()) {",
               "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490()) {",
               2)
    # --- System Chat 等の素通し列挙 ---
    text = rep(text,
               "            if (is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) {",
               "            if (is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759()) {")

    # --- 498 専用メソッド群：パケットID参照先の切り替え ---
    # クライアント向け（translateServerPlayPacket758To498 の範囲）。
    start = text.index("    private List<MinecraftPacket> translateServerPlayPacket758To498")
    end = text.index("    private static int readDimension498FromJoinGame")
    region = text[start:end]
    n = region.count("Direction.CLIENTBOUND, 498)")
    if n != 17:
        fail(f"translateServerPlayPacket758To498 内の CLIENTBOUND, 498) が {n} 件（期待 17）")
    region = region.replace("Direction.CLIENTBOUND, 498)",
                            "Direction.CLIENTBOUND, oldClientProtocol())")
    # Trade List だけ 490 用に分岐。
    region = rep(region,
                 "                    ChunkTranslator.translateTradeList758To498(packet.payload())));",
                 "                    is490()\n"
                 "                            ? ChunkTranslator.translateTradeList758To490(packet.payload())\n"
                 "                            : ChunkTranslator.translateTradeList758To498(packet.payload())));")
    text = text[:start] + region + text[end:]

    # クライアント→サーバー（to758ClientPacket498 の範囲）。
    start = text.index("    private MinecraftPacket to758ClientPacket498")
    end = text.index("    private static byte[] appendServerListings498")
    region = text[start:end]
    region = rep(region, "Direction.SERVERBOUND, 498)",
                 "Direction.SERVERBOUND, oldClientProtocol())")
    text = text[:start] + region + text[end:]

    # サーバー向け（to498ClientPacket758 の範囲）。
    start = text.index("    private MinecraftPacket to498ClientPacket758")
    end = text.index("    private Outcome routeServerPlay498")
    region = text[start:end]
    region = rep(region, "Direction.SERVERBOUND, 498)",
                 "Direction.SERVERBOUND, oldServerProtocol())")
    text = text[:start] + region + text[end:]

    # サーバー→758（routeServerPlay498〜to758ServerPackets498 の範囲）。
    start = text.index("    private Outcome routeServerPlay498")
    end = text.index("    private static int[] readChunkCoords498")
    region = text[start:end]
    n = region.count("Direction.CLIENTBOUND, 498)")
    if n != 2:
        fail(f"routeServerPlay498〜to758ServerPackets498 内の CLIENTBOUND, 498) が {n} 件（期待 2）")
    region = region.replace("Direction.CLIENTBOUND, 498)",
                            "Direction.CLIENTBOUND, oldServerProtocol())")
    region = rep(region,
                 "                    ChunkTranslator.translateTradeList498To758(packet.payload())));",
                 "                    isServer490()\n"
                 "                            ? ChunkTranslator.translateTradeList490To758(packet.payload())\n"
                 "                            : ChunkTranslator.translateTradeList498To758(packet.payload())));")
    text = text[:start] + region + text[end:]

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
