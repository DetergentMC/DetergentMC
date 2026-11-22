"""335（1.12）を ProxySession／ClientAdapter／ClientJoinFlow／
ClientProxy へ配線する。

338 が 340 の経路を共用した流儀を継ぐが、335 は 338 と違い
パケットIDが 338／340 と 53 件ずれる（レシピブック再編）。そのため
ID の解釈・生成が起きる境界4点では、名前経由の ID 付け替えを
335 境界で挟む：

  (1) handleClientPlay の 335 クライアント入口：
      ID を 335→340 へ付け替え（Prepare Crafting Grid は落とす）、
      Keep Alive を varint→i64 へ（復元表は 335 専用）。
      以後は 340 経路をそのまま共用する。
  (2) handleServerPlay の 335 クライアント向け最終段：
      convertServerPlayOutcomeTo335 が Keep Alive を i64→varint へ
      付け替えてから、ID を 340→335 へ付け替える
      （335 に無い Craft Recipe Response は落とす）。
  (3) handleServerPlayRouted の内部 335 サーバー入口：
      ID を 335→340 へ付け替えてから 340 経路へ乗せる
      （Keep Alive は 338 と同じ varint のため、338 と同じ
      keepAliveFrom338Wire を使う）。
  (4) handleClientPlay の内部 335 サーバー向け最終段：
      340 形式まで変換した後、Keep Alive を varint へ付け替えて
      から ID を 340→335 へ付け替える。

それ以外の列挙（ログイン・参加フロー・旧クライアント／旧サーバー
の意味的な括り）は、形式が 338 と完全に同一なため is335()／
isServer335() を 338 の隣へ追加するだけにする。最古を 335 へ
下げても 338・340 が列挙から落ちない形にする（404／401 で踏んだ
最古脱落バグの再発防止）。

Keep Alive の付け替え自体は 338 の関数をそのまま使う：照合は
338 の番号で行うが、クライアント行き Keep Alive は 335・338
とも 0x1f で同一、サーバー行きは境界の付け替え後に 340／338 の
番号（0x0b）になっているため、そのまま効く。

使い方：
  python3 tools/patch-335-proxysession.py
  各置換は出現回数を検証し、合わなければ書き込まずに終了する。
"""

import sys
from pathlib import Path

BASE = (Path(__file__).resolve().parent.parent / "src" / "main" / "java"
        / "io" / "detergentmc" / "protocol")

failures = []


def patch(filename, replacements):
    path = BASE / filename
    text = path.read_text(encoding="utf-8")
    for old, new, expected in replacements:
        count = text.count(old)
        if count != expected:
            failures.append(
                f"{filename}: 期待 {expected} 件 != 実際 {count} 件: "
                + old[:70].replace("\n", "\\n"))
            continue
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
    print(f"{filename}: 適用した")


PROXYSESSION = [
    # コンストラクタ検証に V1_12 を追加。
    (
        "        if (serverProtocol != ProtocolVersion.V1_12_1\n"
        "                && serverProtocol != ProtocolVersion.V1_12_2",
        "        if (serverProtocol != ProtocolVersion.V1_12\n"
        "                && serverProtocol != ProtocolVersion.V1_12_1\n"
        "                && serverProtocol != ProtocolVersion.V1_12_2",
        1,
    ),
    # 意味的な括り（先頭が is338() || is340() の列挙4件）。
    (
        "is338() || is340()",
        "is335() || is338() || is340()",
        4,
    ),
    # 意味的な括り（is340() || is338() で終わる列挙11件）。
    (
        "is340() || is338()",
        "is340() || is338() || is335()",
        11,
    ),
    # 意味的な括り（サーバー側10件）。
    (
        "isServer340() || isServer338()",
        "isServer340() || isServer338() || isServer335()",
        10,
    ),
    # 境界(1)：handleClientPlay の 335 クライアント入口。
    (
        "                if (is338()) {\n"
        "                    packet = keepAliveFrom338Wire(packet,\n"
        "                            PacketName.PLAY_KEEP_ALIVE_TO_SERVER,\n"
        "                            keepAliveIds338ToClient);\n"
        "                }\n"
        "                packet = SlotTranslator.serverboundFrom340(packet);",
        "                if (is335()) {\n"
        "                    packet = remapClientPacket335To340(packet);\n"
        "                    if (packet == null) {\n"
        "                        return Outcome.drop();\n"
        "                    }\n"
        "                    packet = keepAliveFrom338Wire(packet,\n"
        "                            PacketName.PLAY_KEEP_ALIVE_TO_SERVER,\n"
        "                            keepAliveIds335ToClient);\n"
        "                }\n"
        "                if (is338()) {\n"
        "                    packet = keepAliveFrom338Wire(packet,\n"
        "                            PacketName.PLAY_KEEP_ALIVE_TO_SERVER,\n"
        "                            keepAliveIds338ToClient);\n"
        "                }\n"
        "                packet = SlotTranslator.serverboundFrom340(packet);",
        1,
    ),
    # 境界(1)のコメント。
    (
        "            } else if (is340() || is338() || is335()) {\n"
        "                // 340／338クライアント：パケットIDが758と違うものが多いため、",
        "            } else if (is340() || is338() || is335()) {\n"
        "                // 340／338／335クライアント：パケットIDが758と違うものが多いため、",
        1,
    ),
    (
        "                // 338 の Keep Alive は varint のため、先に i64 へ付け替える\n"
        "                // （クライアント行きで切り捨てた ID は元の値へ復元する）。",
        "                // 338 の Keep Alive は varint のため、先に i64 へ付け替える\n"
        "                // （クライアント行きで切り捨てた ID は元の値へ復元する）。\n"
        "                // 335 はパケットIDが 338 と 53 件ずれるため、先に名前経由で\n"
        "                // 340 の番号へ付け替える（Prepare Crafting Grid は 338 以降に\n"
        "                // 届け先が無いため落とす）。Keep Alive は 335 も varint のため\n"
        "                // 付け替え後に 338 と同じ処理（復元表は 335 専用）を行う。",
        1,
    ),
    # 境界(4)：handleClientPlay の内部 335 サーバー向け最終段。
    (
        "                if (isServer338()) {\n"
        "                    p340 = keepAliveTo338Wire(p340,\n"
        "                            PacketName.PLAY_KEEP_ALIVE_TO_SERVER, null);\n"
        "                }\n"
        "                toServer.add(p340);",
        "                if (isServer338()) {\n"
        "                    p340 = keepAliveTo338Wire(p340,\n"
        "                            PacketName.PLAY_KEEP_ALIVE_TO_SERVER, null);\n"
        "                }\n"
        "                if (isServer335()) {\n"
        "                    p340 = keepAliveTo338Wire(p340,\n"
        "                            PacketName.PLAY_KEEP_ALIVE_TO_SERVER, null);\n"
        "                    p340 = remapServerboundPacket340To335(p340);\n"
        "                    if (p340 == null) {\n"
        "                        continue;\n"
        "                    }\n"
        "                }\n"
        "                toServer.add(p340);",
        1,
    ),
    # 境界(2)：handleServerPlay の 335 クライアント向け最終段。
    (
        "            return is338() ? convertServerPlayOutcomeTo338(toSlots) : toSlots;",
        "            if (is335()) {\n"
        "                return convertServerPlayOutcomeTo335(toSlots);\n"
        "            }\n"
        "            return is338() ? convertServerPlayOutcomeTo338(toSlots) : toSlots;",
        1,
    ),
    (
        "        if (is340() || is338() || is335()) {\n"
        "            // 340／338クライアント：763→762→761→760→759→758→340へ六段で変換する。",
        "        if (is340() || is338() || is335()) {\n"
        "            // 340／338／335クライアント：763→762→761→760→759→758→340へ六段で変換する。",
        1,
    ),
    (
        "            // 338 はその後 Keep Alive だけ varint へ付け替える（形式は\n"
        "            // それ以外すべて 340 と同一）。",
        "            // 338 はその後 Keep Alive だけ varint へ付け替える（形式は\n"
        "            // それ以外すべて 340 と同一）。\n"
        "            // 335 はその後 Keep Alive を varint へ付け替えてから、パケットIDを\n"
        "            // 名前経由で 335 の番号へ付け替える（形式はそれ以外すべて 340 と同一）。",
        1,
    ),
    # 境界(3)：handleServerPlayRouted の内部 335 サーバー入口。
    (
        "        if (isServer340() || isServer338() || isServer335()) {\n"
        "            if (isServer338()) {\n"
        "                packet = keepAliveFrom338Wire(packet, PacketName.PLAY_KEEP_ALIVE,\n"
        "                        null);\n"
        "            }",
        "        if (isServer340() || isServer338() || isServer335()) {\n"
        "            if (isServer335()) {\n"
        "                packet = remapServerPacket335To340(packet);\n"
        "            }\n"
        "            if (isServer338() || isServer335()) {\n"
        "                packet = keepAliveFrom338Wire(packet, PacketName.PLAY_KEEP_ALIVE,\n"
        "                        null);\n"
        "            }",
        1,
    ),
    (
        "        // 内部340／338：先にハイブリッド形式（404符号化＋340番号）へ直してから、",
        "        // 内部340／338／335：先にハイブリッド形式（404符号化＋340番号）へ直してから、",
        1,
    ),
    (
        "        // 338 の Keep Alive は varint のため、先に i64 へ付け替えてから\n"
        "        // 340 と同じ経路に乗せる。",
        "        // 338 の Keep Alive は varint のため、先に i64 へ付け替えてから\n"
        "        // 340 と同じ経路に乗せる。\n"
        "        // 335 のパケットIDは 338 と 53 件ずれるため、先に名前経由で\n"
        "        // 340 の番号へ付け替えてから乗せる（Keep Alive は 335 も varint）。",
        1,
    ),
    # Keep Alive 復元表（335 専用）を 338 の隣へ追加。
    (
        "    private final java.util.Map<Integer, Long> keepAliveIds338ToClient =\n"
        "            new java.util.HashMap<>();",
        "    private final java.util.Map<Integer, Long> keepAliveIds338ToClient =\n"
        "            new java.util.HashMap<>();\n"
        "\n"
        "    /**\n"
        "     * 335クライアントへ送った Keep Alive の、切り捨て前の i64 ID\n"
        "     * （キー＝varint に切り捨てた int 値）。338 の復元表と同じ仕組みで、\n"
        "     * セッションのクライアントは1プロトコルだけのため 338 とは分ける。\n"
        "     */\n"
        "    private final java.util.Map<Integer, Long> keepAliveIds335ToClient =\n"
        "            new java.util.HashMap<>();",
        1,
    ),
    # is335()／isServer335() と境界の付け替え関数群を、338 の定義の前へ追加。
    (
        "    /**\n"
        "     * クライアントが 1.12.1（338）か。ワイヤ形式は Keep Alive の",
        "    /**\n"
        "     * クライアントが 1.12（335）か。ワイヤ形式は 338 と完全に同一\n"
        "     * （共有パケットの形式差分は無い）で、レジストリも 338／340 と\n"
        "     * 共用する。違いはパケットIDだけ（338 と 53 件ずれる。レシピ\n"
        "     * ブック再編）で、境界で名前経由の付け替えを行う。Keep Alive は\n"
        "     * 338 と同じ varint。\n"
        "     */\n"
        "    private boolean is335() {\n"
        "        return clientProtocol == ProtocolVersion.V1_12;\n"
        "    }\n"
        "\n"
        "    /** 内部が 1.12（335）か。 */\n"
        "    private boolean isServer335() {\n"
        "        return serverProtocol == ProtocolVersion.V1_12;\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * 335クライアントが送ってきたプレイ状態のパケットのIDを、名前経由で\n"
        "     * 340 の番号へ付け替える。以後の処理は 340 経路を共用するため。\n"
        "     * 335 にだけある Prepare Crafting Grid は 338 以降に届け先が無いため\n"
        "     * 落とす（{@code null}）。335 に存在しないIDも落とす。\n"
        "     */\n"
        "    private static MinecraftPacket remapClientPacket335To340(MinecraftPacket packet) {\n"
        "        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
        "                Direction.SERVERBOUND, ProtocolVersion.V1_12).orElse(null);\n"
        "        if (name == null) {\n"
        "            LOG.warning(\"335に存在しないプレイ状態のパケット（id=0x\"\n"
        "                    + Integer.toHexString(packet.id()) + \"）を落とします。\");\n"
        "            return null;\n"
        "        }\n"
        "        if (name == PacketName.PLAY_PREPARE_CRAFTING_GRID) {\n"
        "            LOG.warning(\"338以降に存在しないパケット \" + name + \" を落とします。\");\n"
        "            return null;\n"
        "        }\n"
        "        OptionalInt id340 = PacketIds.id(name, State.PLAY, Direction.SERVERBOUND,\n"
        "                ProtocolVersion.V1_12_2);\n"
        "        if (id340.isEmpty()) {\n"
        "            LOG.warning(\"340に存在しないパケット \" + name + \"（335 id=0x\"\n"
        "                    + Integer.toHexString(packet.id()) + \"）を落とします。\");\n"
        "            return null;\n"
        "        }\n"
        "        return new MinecraftPacket(id340.getAsInt(), packet.payload());\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * 内部335サーバーが送ってきたプレイ状態のパケットのIDを、名前経由で\n"
        "     * 340 の番号へ付け替える。335 のクライアント行きパケットは全て\n"
        "     * 340 にもあるため、付け替えできないものは 340 経路の未知IDの\n"
        "     * 扱いに合わせて素通しする。\n"
        "     */\n"
        "    private static MinecraftPacket remapServerPacket335To340(MinecraftPacket packet) {\n"
        "        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
        "                Direction.CLIENTBOUND, ProtocolVersion.V1_12).orElse(null);\n"
        "        if (name == null) {\n"
        "            return packet;\n"
        "        }\n"
        "        OptionalInt id340 = PacketIds.id(name, State.PLAY, Direction.CLIENTBOUND,\n"
        "                ProtocolVersion.V1_12_2);\n"
        "        return id340.isEmpty() ? packet\n"
        "                : new MinecraftPacket(id340.getAsInt(), packet.payload());\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * 340形式のサーバー行きパケットのIDを、名前経由で 335 の番号へ\n"
        "     * 付け替える（内部335サーバーへ送る最終段）。335 に無いパケットは\n"
        "     * {@code null}（落とす）。340 に存在しないIDは素通しする。\n"
        "     */\n"
        "    private static MinecraftPacket remapServerboundPacket340To335(MinecraftPacket packet) {\n"
        "        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
        "                Direction.SERVERBOUND, ProtocolVersion.V1_12_2).orElse(null);\n"
        "        if (name == null) {\n"
        "            return packet;\n"
        "        }\n"
        "        OptionalInt id335 = PacketIds.id(name, State.PLAY, Direction.SERVERBOUND,\n"
        "                ProtocolVersion.V1_12);\n"
        "        if (id335.isEmpty()) {\n"
        "            LOG.warning(\"335に存在しないパケット \" + name + \"（340 id=0x\"\n"
        "                    + Integer.toHexString(packet.id()) + \"）を落とします。\");\n"
        "            return null;\n"
        "        }\n"
        "        return new MinecraftPacket(id335.getAsInt(), packet.payload());\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * 340形式まで変換した結果を、335クライアント向けに仕上げる。\n"
        "     * Keep Alive は 335 も varint のため 338 と同じ付け替え（復元表は\n"
        "     * 335 専用）を行い、その後パケットIDを名前経由で 335 の番号へ\n"
        "     * 付け替える。335 に無いパケット（Craft Recipe Response）は落とす。\n"
        "     * 340 に存在しないIDは素通しする。\n"
        "     */\n"
        "    private Outcome convertServerPlayOutcomeTo335(Outcome outcome) {\n"
        "        if (outcome.toClient().isEmpty()) {\n"
        "            return outcome;\n"
        "        }\n"
        "        List<MinecraftPacket> converted = new ArrayList<>();\n"
        "        for (MinecraftPacket packet : outcome.toClient()) {\n"
        "            MinecraftPacket p = keepAliveTo338Wire(packet,\n"
        "                    PacketName.PLAY_KEEP_ALIVE, keepAliveIds335ToClient);\n"
        "            PacketName name = PacketIds.nameFor(p.id(), State.PLAY,\n"
        "                    Direction.CLIENTBOUND, ProtocolVersion.V1_12_2).orElse(null);\n"
        "            if (name == null) {\n"
        "                converted.add(p);\n"
        "                continue;\n"
        "            }\n"
        "            OptionalInt id335 = PacketIds.id(name, State.PLAY,\n"
        "                    Direction.CLIENTBOUND, ProtocolVersion.V1_12);\n"
        "            if (id335.isEmpty()) {\n"
        "                LOG.warning(\"335に存在しないパケット \" + name + \"（340 id=0x\"\n"
        "                        + Integer.toHexString(p.id()) + \"）を落とします。\");\n"
        "                continue;\n"
        "            }\n"
        "            converted.add(new MinecraftPacket(id335.getAsInt(), p.payload()));\n"
        "        }\n"
        "        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * クライアントが 1.12.1（338）か。ワイヤ形式は Keep Alive の",
        1,
    ),
]

CLIENTPROXY = [
    (
        "        if (internalProtocol != ProtocolVersion.V1_12_1\n"
        "                && internalProtocol != ProtocolVersion.V1_12_2",
        "        if (internalProtocol != ProtocolVersion.V1_12\n"
        "                && internalProtocol != ProtocolVersion.V1_12_1\n"
        "                && internalProtocol != ProtocolVersion.V1_12_2",
        3,
    ),
]

CLIENTADAPTER = [
    (
        "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_12_1;",
        "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_12;",
        1,
    ),
    (
        "        if (clientProtocol == ProtocolVersion.V1_12_1\n"
        "                || clientProtocol == ProtocolVersion.V1_12_2",
        "        if (clientProtocol == ProtocolVersion.V1_12\n"
        "                || clientProtocol == ProtocolVersion.V1_12_1\n"
        "                || clientProtocol == ProtocolVersion.V1_12_2",
        1,
    ),
    (
        "            if (clientProtocol == ProtocolVersion.V1_12_1) {\n"
        "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
        "                        \"プロトコル338（1.12.1）のクライアントを変換して受け入れます。\"",
        "            if (clientProtocol == ProtocolVersion.V1_12) {\n"
        "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
        "                        \"プロトコル335（1.12）のクライアントを変換して受け入れます。\"\n"
        "                                + \"ワイヤ形式は338（1.12.1）と完全に同一で、\"\n"
        "                                + \"レジストリも同一です（実jarのprotocol.jsonの\"\n"
        "                                + \"平坦化比較と、両jarのassets再帰diff 0件で\"\n"
        "                                + \"確認済み）。差分はパケットIDだけで、\"\n"
        "                                + \"レシピブック再編により53件が機械的にずれます\"\n"
        "                                + \"（335にだけある Prepare Crafting Grid は\"\n"
        "                                + \"落とします）。境界で名前経由のID付け替えを行い、\"\n"
        "                                + \"変換経路・レジストリ対応表はすべて338／340と\"\n"
        "                                + \"共用します。Keep Alive は338と同じ varint です。\"\n"
        "                                + \"338／340と同じく、設定フェーズの省略と\"\n"
        "                                + \"ブロックの代替（意味対応表）が必要です。\");\n"
        "            }\n"
        "            if (clientProtocol == ProtocolVersion.V1_12_1) {\n"
        "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
        "                        \"プロトコル338（1.12.1）のクライアントを変換して受け入れます。\"",
        1,
    ),
    (
        "        // OLDEST（338）だけでは340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。\n"
        "        // 340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。\n"
        "        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_12_2,",
        "        // OLDEST（335）だけでは338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。\n"
        "        // 338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。\n"
        "        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_12_1, ProtocolVersion.V1_12_2,",
        1,
    ),
    (
        "    public static String versionName(int protocol) {\n"
        "        if (protocol == ProtocolVersion.V1_12_1) {",
        "    public static String versionName(int protocol) {\n"
        "        if (protocol == ProtocolVersion.V1_12) {\n"
        "            return \"1.12\";\n"
        "        }\n"
        "        if (protocol == ProtocolVersion.V1_12_1) {",
        1,
    ),
]

CLIENTJOINFLOW = [
    (
        "                || clientProtocol == ProtocolVersion.V1_12_2\n"
        "                || clientProtocol == ProtocolVersion.V1_12_1",
        "                || clientProtocol == ProtocolVersion.V1_12_2\n"
        "                || clientProtocol == ProtocolVersion.V1_12_1\n"
        "                || clientProtocol == ProtocolVersion.V1_12",
        1,
    ),
]


def main():
    patch("ProxySession.java", PROXYSESSION)
    patch("ClientProxy.java", CLIENTPROXY)
    patch("ClientAdapter.java", CLIENTADAPTER)
    patch("ClientJoinFlow.java", CLIENTJOINFLOW)
    if failures:
        for f in failures:
            print(f"失敗: {f}", file=sys.stderr)
        return 1
    print("全ての置換が期待件数で適用できた。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
