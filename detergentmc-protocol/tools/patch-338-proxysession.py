#!/usr/bin/env python3
"""ProxySession.java に 338（1.12.1）の配線を追加する。

338 は 340 と完全に同一形式（パケットIDも全件一致）で、唯一の
形式差分は Keep Alive の keepAliveId 型（338 は varint、340 以降は
i64）。そのため 393→404 のような専用ディスパッチ群は作らず、
401 が 404 の経路を共用したのと同じ流儀で、340 の分岐に
is338()／isServer338() を参加させ、Keep Alive だけを 338 境界の
4箇所（クライアント行き最終段、サーバー行き入口、内部サーバーの
経路入口、内部サーバーへの下り最終段）で付け替える。

やること：
 1. 旧クライアント／旧サーバーの列挙に is338()／isServer338() を
    足す（Login Start・Login Success・リソースパック提示・
    Join Game 763 経路など）。最古を 338 に下げても 340 が列挙から
    落ちないよう、既存の列挙へ追加だけする。
 2. 340 と分岐を共用する4箇所に 338 を参加させ、Keep Alive の
    付け替えを差し込む。
 3. ヘルパー（is338／isServer338、Keep Alive 変換、
    convertServerPlayOutcomeTo338）を追加する。
 4. コンストラクタの serverProtocol 検証に V1_12_1 を足す。

Keep Alive の値域差（i64 → varint は 32 ビットに収まらない値を
切り捨てる）への対処：クライアント行きで切り捨てが発生したら
元の i64 をセッションに覚えておき、応答が戻ってきたら元の値へ
復元する（Acknowledge Player Digging の sequence 対応表と同じ
考え方）。内部サーバーが 1.12.x の場合は ID 自体が int 範囲の
ため切り捨ては起きない。

使い方：
  python3 tools/patch-338-proxysession.py
  冪等ではない（各置換の出現回数を検証し、合わなければ中断する）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ProxySession.java"


def replace(text, old, new, count):
    n = text.count(old)
    if n != count:
        raise SystemExit(f"置換回数が合いません（{n} != {count}）: {old[:70]}...")
    return text.replace(old, new)


def insert_after(text, anchor, addition):
    if anchor not in text:
        raise SystemExit(f"アンカーが見つかりません: {anchor[:70]}...")
    return text.replace(anchor, anchor + addition, 1)


def main():
    text = P.read_text(encoding="utf-8")
    if "isServer338()" in text:
        print("skip（既存）: 338 の配線はすでにある")
        return 0

    # ---- 1. 列挙の拡張 -------------------------------------------------
    text = replace(text,
        "(is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759())",
        "(is338() || is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759())", 1)
    text = replace(text,
        "return is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();",
        "return is338() || is340() || is393() || is401() || is404() || is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is759() || is760() || is761() || is762() || is763();", 1)
    text = replace(text,
        "if (is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393() || is340()) {",
        "if (is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393() || is340() || is338()) {", 2)
    text = replace(text,
        "(isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401() || isServer393() || isServer340())",
        "(isServer758() || isServer498() || isServer490() || isServer485() || isServer480() || isServer477() || isServer578() || isServer575() || isServer573() || isServer404() || isServer401() || isServer393() || isServer340() || isServer338())", 6)
    text = replace(text,
        "!(is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393() || is340())",
        "!(is758() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573() || is404() || is401() || is393() || is340() || is338())", 1)
    text = replace(text,
        "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480() || is477() || is404() || is401() || is393() || is340()) {",
        "if (is762() || is761() || is760() || is759() || is758() || is578() || is575() || is573() || is498() || is490() || is485() || is480() || is477() || is404() || is401() || is393() || is340() || is338()) {", 2)
    text = replace(text,
        "if (is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759() || is404() || is401() || is393() || is340()) {",
        "if (is477() || is480() || is485() || is490() || is498() || is573() || is575() || is578() || is758() || is761() || is760() || is759() || is404() || is401() || is393() || is340() || is338()) {", 1)
    # Login Success の付け替え（340 のグループに 338 を追加）
    text = replace(text,
        "if (is340() || is393() || is404() || is401() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {\n            // 340／393／401／404／477／480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。",
        "if (is338() || is340() || is393() || is404() || is401() || is477() || is480() || is485() || is490() || is498() || is578() || is575() || is573()) {\n            // 338／340／393／401／404／477／480／485／490／498／578 の Login Success は UUID（文字列）＋名前だけ。", 1)

    # ---- 2. コンストラクタの serverProtocol 検証 -------------------------
    text = replace(text,
        "        if (serverProtocol != ProtocolVersion.V1_12_2\n                && serverProtocol != ProtocolVersion.V1_13\n",
        "        if (serverProtocol != ProtocolVersion.V1_12_1\n                && serverProtocol != ProtocolVersion.V1_12_2\n                && serverProtocol != ProtocolVersion.V1_13\n", 1)
    text = replace(text,
        '"serverProtocol must be 340 (1.12.2), 393 (1.13), 401 (1.13.1), 404 (1.13.2), 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), ',
        '"serverProtocol must be 338 (1.12.1), 340 (1.12.2), 393 (1.13), 401 (1.13.1), 404 (1.13.2), 477 (1.14), 480 (1.14.1), 485 (1.14.2), 490 (1.14.3), 498 (1.14.4), 573 (1.15), 575 (1.15.1), 578 (1.15.2), 758 (1.18.2), 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), ', 1)

    # ---- 3. フィールド ---------------------------------------------------
    text = insert_after(text,
        "    private int dimension340Server;\n",
        """
    /**
     * 338クライアントへ送った Keep Alive の、切り捨て前の i64 ID
     * （キー＝varint に切り捨てた int 値）。応答を i64 へ戻すときに
     * 取り出す。切り捨てが起きていない場合は登録されない。
     */
    private final java.util.Map<Integer, Long> keepAliveIds338ToClient =
            new java.util.HashMap<>();

""")

    # ---- 4. クライアント行きチェーンに 338 --------------------------------
    text = replace(text,
        """        if (is340()) {
            // 340クライアント：763→762→761→760→759→758→340へ六段で変換する。
            // その後スロット等の実ワイヤ差分（itemDamage・型 i8・粒子 ID i32）
            // を SlotTranslator の 340 層で付け替える。
            Outcome to762 = convertServerPlayOutcomeTo762(outcome);
            Outcome to761 = convertServerPlayOutcomeTo761(to762);
            Outcome to760 = convertServerPlayOutcomeTo760(to761);
            Outcome to759 = convertServerPlayOutcomeTo759(to760);
            Outcome to758 = convertServerPlayOutcomeTo758(to759);
            Outcome to340 = convertServerPlayOutcomeTo340(to758);
            return convertServerPlayOutcomeTo340Slots(to340);
        }
""",
        """        if (is340() || is338()) {
            // 340／338クライアント：763→762→761→760→759→758→340へ六段で変換する。
            // その後スロット等の実ワイヤ差分（itemDamage・型 i8・粒子 ID i32）
            // を SlotTranslator の 340 層で付け替える。
            // 338 はその後 Keep Alive だけ varint へ付け替える（形式は
            // それ以外すべて 340 と同一）。
            Outcome to762 = convertServerPlayOutcomeTo762(outcome);
            Outcome to761 = convertServerPlayOutcomeTo761(to762);
            Outcome to760 = convertServerPlayOutcomeTo760(to761);
            Outcome to759 = convertServerPlayOutcomeTo759(to760);
            Outcome to758 = convertServerPlayOutcomeTo758(to759);
            Outcome to340 = convertServerPlayOutcomeTo340(to758);
            Outcome toSlots = convertServerPlayOutcomeTo340Slots(to340);
            return is338() ? convertServerPlayOutcomeTo338(toSlots) : toSlots;
        }
""", 1)

    # ---- 5. 内部サーバーの経路に 338 --------------------------------------
    text = replace(text,
        """        // 内部340：先にハイブリッド形式（404符号化＋340番号）へ直してから、
        // 758サーバーと同じ経路に乗せる。光はチャンクに内蔵される。
        // Player Info は340・758で同一形式。
        if (isServer340()) {
            packet = SlotTranslator.clientboundFrom340(packet);
            return routeServerPlay340(packet);
        }
""",
        """        // 内部340／338：先にハイブリッド形式（404符号化＋340番号）へ直してから、
        // 758サーバーと同じ経路に乗せる。光はチャンクに内蔵される。
        // Player Info は340・758で同一形式。
        // 338 の Keep Alive は varint のため、先に i64 へ付け替えてから
        // 340 と同じ経路に乗せる。
        if (isServer340() || isServer338()) {
            if (isServer338()) {
                packet = keepAliveFrom338Wire(packet, PacketName.PLAY_KEEP_ALIVE,
                        null);
            }
            packet = SlotTranslator.clientboundFrom340(packet);
            return routeServerPlay340(packet);
        }
""", 1)

    # ---- 6. サーバー行き（クライアント発）に 338 --------------------------
    text = replace(text,
        """            } else if (is340()) {
                // 340クライアント：パケットIDが758と違うものが多いため、
                // 先に758形式へ直してから、758クライアントと同じ経路に乗せる。
                // 758形式に変換できないものは落とす。
                // スロット等の実ワイヤ差分は先にハイブリッド形式へ付け替える。
                packet = SlotTranslator.serverboundFrom340(packet);
""",
        """            } else if (is340() || is338()) {
                // 340／338クライアント：パケットIDが758と違うものが多いため、
                // 先に758形式へ直してから、758クライアントと同じ経路に乗せる。
                // 758形式に変換できないものは落とす。
                // スロット等の実ワイヤ差分は先にハイブリッド形式へ付け替える。
                // 338 の Keep Alive は varint のため、先に i64 へ付け替える
                // （クライアント行きで切り捨てた ID は元の値へ復元する）。
                if (is338()) {
                    packet = keepAliveFrom338Wire(packet,
                            PacketName.PLAY_KEEP_ALIVE_TO_SERVER,
                            keepAliveIds338ToClient);
                }
                packet = SlotTranslator.serverboundFrom340(packet);
""", 1)

    # ---- 7. 内部サーバーへの下りチェーンに 338 ------------------------------
    text = replace(text,
        """        // 内部340：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、
        // 最後に758形式から340形式（ハイブリッド）へ変換して送る。その後
        // スロット等の実ワイヤ差分を SlotTranslator の 340 層で付け替える。
        if (isServer340()) {""",
        """        // 内部340／338：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、
        // 最後に758形式から340形式（ハイブリッド）へ変換して送る。その後
        // スロット等の実ワイヤ差分を SlotTranslator の 340 層で付け替える。
        // 338 へは最後に Keep Alive だけ varint へ付け替える。
        if (isServer340() || isServer338()) {""", 1)
    text = replace(text,
        """                MinecraftPacket p340 = to340ClientPacket758(p758);
                if (p340 == null) {
                    continue;
                }
                p340 = SlotTranslator.clientboundTo340(p340);
                toServer.add(p340);""",
        """                MinecraftPacket p340 = to340ClientPacket758(p758);
                if (p340 == null) {
                    continue;
                }
                p340 = SlotTranslator.clientboundTo340(p340);
                if (isServer338()) {
                    p340 = keepAliveTo338Wire(p340,
                            PacketName.PLAY_KEEP_ALIVE_TO_SERVER, null);
                }
                toServer.add(p340);""", 1)

    # ---- 8. ヘルパー --------------------------------------------------------
    text = insert_after(text,
        """    /** 内部が 1.12.2（340）か。 */
    private boolean isServer340() {
        return serverProtocol == ProtocolVersion.V1_12_2;
    }
""",
        """
    /**
     * クライアントが 1.12.1（338）か。ワイヤ形式は Keep Alive の
     * keepAliveId 型（varint）を除いて 340 と完全に同一（パケットIDも
     * 全件一致）ため、変換経路は 340 と共用し、Keep Alive だけを
     * 338 境界で付け替える。
     */
    private boolean is338() {
        return clientProtocol == ProtocolVersion.V1_12_1;
    }

    /** 内部が 1.12.1（338）か。 */
    private boolean isServer338() {
        return serverProtocol == ProtocolVersion.V1_12_1;
    }

    /**
     * 340形式（i64）まで変換した結果を、338クライアント向けに
     * Keep Alive だけ varint へ付け替える。
     */
    private Outcome convertServerPlayOutcomeTo338(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.add(keepAliveTo338Wire(packet, PacketName.PLAY_KEEP_ALIVE,
                    keepAliveIds338ToClient));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }

    /**
     * Keep Alive のペイロードを i64 から 338 の varint へ付け替える。
     * Keep Alive 以外のパケットはそのまま返す。i64 の値が int 範囲に
     * 収まらない場合は下位 32 ビットに切り捨て、{@code restore} が
     * 非 null なら元の値を覚えておく（応答側の
     * {@link #keepAliveFrom338Wire} で復元する）。内部338サーバーへ
     * 送る場合は、値の出所が 338 サーバー自身の varint のため
     * 切り捨ては起きず、{@code restore} は null でよい。
     * ID は 338・340 で同一のため、338 の番号で照合する。
     */
    private static MinecraftPacket keepAliveTo338Wire(MinecraftPacket packet,
            PacketName name, java.util.Map<Integer, Long> restore) {
        int id338 = PacketIds.id(name, ProtocolVersion.V1_12_1).orElse(-1);
        if (id338 < 0 || packet.id() != id338) {
            return packet;
        }
        long value;
        try {
            value = new PacketIO.Reader(packet.payload()).readLong();
        } catch (RuntimeException e) {
            return packet;
        }
        int truncated = (int) value;
        if (truncated != value && restore != null) {
            restore.put(truncated, value);
        }
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(truncated);
        return new MinecraftPacket(packet.id(), writer.bytes());
    }

    /**
     * Keep Alive のペイロードを 338 の varint から i64 へ付け替える。
     * Keep Alive 以外のパケットはそのまま返す。{@code restore} が
     * 非 null で切り捨て前の値が残っていればそれを復元し、無ければ
     * varint の値を符号拡張して使う。内部338サーバーから受ける場合は
     * 値が元々 varint 範囲のため、{@code restore} は null でよい。
     */
    private static MinecraftPacket keepAliveFrom338Wire(MinecraftPacket packet,
            PacketName name, java.util.Map<Integer, Long> restore) {
        int id338 = PacketIds.id(name, ProtocolVersion.V1_12_1).orElse(-1);
        if (id338 < 0 || packet.id() != id338) {
            return packet;
        }
        int value;
        try {
            value = new PacketIO.Reader(packet.payload()).readVarInt();
        } catch (RuntimeException e) {
            return packet;
        }
        Long restored = restore != null ? restore.remove(value) : null;
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeLong(restored != null ? restored : value);
        return new MinecraftPacket(packet.id(), writer.bytes());
    }
""")

    P.write_text(text, encoding="utf-8")
    print("ProxySession.java に 338 の配線を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
