"""ProxySession.java に 316（1.11.2）の経路を追加する。

340／335 の経路を雛形にする。316 はレジストリ番号が 340 と違う
ため 335 のような「340 経路の共用＋ID 付け替え」では足りず、
変換は 316 専用の翻訳子（ChunkTranslator／SlotTranslator の
316 節）へ通す。大規模な switch メソッド4本（translateServerPlay
Packet758To340・to758ServerPackets340・to758ClientPacket340・
routeServerPlay340）は 340 版のソースをテキスト変換して 316 版を
生成する（翻訳子の差し替えはメソッド名の 340→316 で効く）。

冪等ではない（2回実行すると二重定義になる）。git diff で確認する
こと。
"""

import re
from pathlib import Path

F = (Path(__file__).parent.parent / "src" / "main" / "java"
     / "io" / "detergentmc" / "protocol" / "ProxySession.java")
src = F.read_text(encoding="utf-8")


def sub_once(old, new):
    global src
    assert src.count(old) == 1, f"一意でない({src.count(old)}): {old[:70]}"
    src = src.replace(old, new)


def sub_all(old, new, expect):
    global src
    n = src.count(old)
    assert n == expect, f"件数不一致({n} != {expect}): {old[:70]}"
    src = src.replace(old, new)
    print(f"  列挙追加 {expect} 件: ...{old[-40:]}")


def insert_after(anchor, text):
    sub_once(anchor, anchor + text)


def insert_before(anchor, text):
    sub_once(anchor, text + anchor)


# ---- 1. Keep Alive 復元表 ----
insert_after(
    """    private final java.util.Map<Integer, Long> keepAliveIds335ToClient =
            new java.util.HashMap<>();
""",
    """
    /**
     * 316クライアントへ送った Keep Alive の、切り捨て前の i64 ID
     * （キー＝varint に切り捨てた int 値）。335 の復元表と同じ仕組み。
     */
    private final java.util.Map<Integer, Long> keepAliveIds316ToClient =
            new java.util.HashMap<>();
""")

# ---- 2. is316 / isServer316 ----
insert_after(
    """    /** 内部が 1.12（335）か。 */
    private boolean isServer335() {
        return serverProtocol == ProtocolVersion.V1_12;
    }
""",
    """
    /**
     * クライアントが 1.11.2（316）か。ワイヤ形式は 340 と同じ世代
     * （スロットの itemDamage・メタデータ型 i8・粒子 ID i32）だが、
     * レジストリ番号とパケットIDが違うため変換経路は 316 専用。
     * Keep Alive は varint（335／338 と同じ）。
     */
    private boolean is316() {
        return clientProtocol == ProtocolVersion.V1_11_2;
    }

    /** 内部が 1.11.2（316）か。 */
    private boolean isServer316() {
        return serverProtocol == ProtocolVersion.V1_11_2;
    }
""")

# ---- 3. 列挙への追加（旧世代サーバー／クライアントの判定群） ----
sub_all("|| isServer340() || isServer338() || isServer335()",
        "|| isServer340() || isServer338() || isServer335() || isServer316()", 8)
sub_all("|| is393() || is340() || is338() || is335()",
        "|| is393() || is340() || is338() || is335() || is316()", 7)
sub_once("        return (is335() || is338() || is340() || is393() || is401() || is404()",
         "        return (is316() || is335() || is338() || is340() || is393() || is401() || is404()")
sub_once("        return is335() || is338() || is340() || is393() || is401() || is404()",
         "        return is316() || is335() || is338() || is340() || is393() || is401() || is404()")
sub_once("        if (is335() || is338() || is340() || is393() || is404() || is401()",
         "        if (is316() || is335() || is338() || is340() || is393() || is404() || is401()")

# ---- 4. クライアント→758（サーバー行き）の 316 分岐 ----
insert_before(
    """            } else if (is340() || is338() || is335()) {
                // 340／338／335クライアント：パケットIDが758と違うものが多いため、""",
    """            } else if (is316()) {
                // 316クライアント：先に Keep Alive を i64 へ戻し（復元表は
                // 316 専用）、スロット等の実ワイヤ差分をハイブリッド形式へ
                // 付け替えてから、名前経由で758形式へ直す。316 に無い
                // パケットは to758ClientPacket316 が落とす。
                packet = keepAliveFrom338Wire(packet,
                        PacketName.PLAY_KEEP_ALIVE_TO_SERVER,
                        keepAliveIds316ToClient);
                packet = SlotTranslator.serverboundFrom316(packet);
                packet = to758ClientPacket316(packet);
                if (packet == null) {
                    return Outcome.drop();
                }
""")

# ---- 5. クライアント→内部316サーバー ----
insert_before(
    """        // 内部340／338：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、""",
    """        // 内部316：758サーバーと同じ経路（763→762→761→760→759→758形式）を通し、
        // 最後に758形式から316形式（ハイブリッド）へ変換して送る。その後
        // スロット等の実ワイヤ差分を SlotTranslator の 316 層で付け替え、
        // Keep Alive を varint へ付け替える（316 も varint）。
        if (isServer316()) {
            Outcome to763 = is763Like()
                    ? Outcome.toServer(packet)
                    : handleClientPlayTo763(packet);
            List<MinecraftPacket> toServer = new ArrayList<>();
            for (MinecraftPacket p : to763.toServer()) {
                MinecraftPacket p762 = to762ClientPacket(p);
                if (p762 == null) {
                    continue;
                }
                MinecraftPacket p761 = to761ClientPacket762(p762);
                if (p761 == null) {
                    continue;
                }
                MinecraftPacket p760 = to760ClientPacket761(p761);
                if (p760 == null) {
                    continue;
                }
                MinecraftPacket p759 = to759ClientPacket760(p760);
                if (p759 == null) {
                    continue;
                }
                MinecraftPacket p758 = to758ClientPacket759(p759);
                if (p758 == null) {
                    continue;
                }
                MinecraftPacket p316 = to316ClientPacket758(p758);
                if (p316 == null) {
                    continue;
                }
                p316 = SlotTranslator.clientboundTo316(p316);
                p316 = keepAliveTo338Wire(p316,
                        PacketName.PLAY_KEEP_ALIVE_TO_SERVER, null);
                toServer.add(p316);
            }
            return new Outcome(toServer, to763.toClient(), to763.relay(),
                    to763.closeAfterSend());
        }
""")

# ---- 6. クライアント行き Outcome の 316 分岐 ----
insert_before(
    """        if (is340() || is338() || is335()) {
            // 340／338／335クライアント：763→762→761→760→759→758→340へ六段で変換する。""",
    """        if (is316()) {
            // 316クライアント：763→762→761→760→759→758→316へ六段で変換する。
            // その後スロット等の実ワイヤ差分（itemDamage・型 i8・粒子 ID i32、
            // メタデータ型 13〜16 の落とし）を SlotTranslator の 316 層で
            // 付け替え、Keep Alive を varint へ付け替える。
            Outcome to762 = convertServerPlayOutcomeTo762(outcome);
            Outcome to761 = convertServerPlayOutcomeTo761(to762);
            Outcome to760 = convertServerPlayOutcomeTo760(to761);
            Outcome to759 = convertServerPlayOutcomeTo759(to760);
            Outcome to758 = convertServerPlayOutcomeTo758(to759);
            Outcome to316 = convertServerPlayOutcomeTo316(to758);
            return convertServerPlayOutcomeTo316Slots(to316);
        }
""")

# ---- 7. 内部316サーバーからの経路 ----
insert_before(
    """        // 内部340／338／335：先にハイブリッド形式（404符号化＋340番号）へ直してから、""",
    """        // 内部316：先にハイブリッド形式（404符号化＋316番号）へ直してから、
        // 758サーバーと同じ経路に乗せる。光はチャンクに内蔵される。
        // Player Info は316・758で同一形式。Keep Alive は varint のため
        // 先に i64 へ付け替えてから乗せる。
        if (isServer316()) {
            packet = keepAliveFrom338Wire(packet, PacketName.PLAY_KEEP_ALIVE,
                    null);
            packet = SlotTranslator.clientboundFrom316(packet);
            return routeServerPlay316(packet);
        }
""")

# ---- 8. convertServerPlayOutcomeTo316 / To316Slots ----
insert_after(
    """    private Outcome convertServerPlayOutcomeTo340Slots(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.add(SlotTranslator.clientboundTo340(packet));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }
""",
    """
    /**
     * 758形式のサーバー結果を316形式（ハイブリッド）へ変換する。
     * 316 に存在しないパケットは {@link #translateServerPlayPacket758To316}
     * が落とす。
     */
    private Outcome convertServerPlayOutcomeTo316(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            converted.addAll(translateServerPlayPacket758To316(packet));
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }

    /**
     * 316形式（ハイブリッド）まで変換した結果を、316クライアント向けに
     * 実ワイヤ差分（スロットの itemDamage、メタデータ型 13〜16 の
     * 落とし等）だけ付け替え、Keep Alive を varint へ付け替える
     * （復元表は 316 専用）。
     */
    private Outcome convertServerPlayOutcomeTo316Slots(Outcome outcome) {
        if (outcome.toClient().isEmpty()) {
            return outcome;
        }
        List<MinecraftPacket> converted = new ArrayList<>();
        for (MinecraftPacket packet : outcome.toClient()) {
            MinecraftPacket p = SlotTranslator.clientboundTo316(packet);
            p = keepAliveTo338Wire(p, PacketName.PLAY_KEEP_ALIVE,
                    keepAliveIds316ToClient);
            converted.add(p);
        }
        return new Outcome(outcome.toServer(), converted, outcome.relay(), outcome.closeAfterSend());
    }
""")

# ---- 9. to316ClientPacket758（手書き） ----
# to316ClientPacket758 は to340ClientPacket758 の直後に丸ごと足す
TO316 = """
    /**
     * 758形式のクライアント→サーバーのプレイ状態のパケット1個を316形式に直す。
     * パケットIDは758と違うものが61件あるため、名前で引いて316の番号に
     * 付け替える。形式が違うものだけ変換し、それ以外はペイロードをそのまま通す。
     * 316に存在しないパケットは {@code null}（落とす）。
     * （内部316サーバー向け。{@link #to340ClientPacket758} と同型。）
     */
    private MinecraftPacket to316ClientPacket758(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, 758).orElse(null);
        if (name == null) {
            LOG.warning("758に存在しないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return null;
        }
        if (name == PacketName.PLAY_EDIT_BOOK) {
            // 758 の Edit Book（ページの文字列）からは316の本アイテム（NBT）
            // を合成できないため落とす。
            LOG.warning("316に直せないパケット " + name + " を落とします。");
            return null;
        }
        if (name == PacketName.PLAY_CRAFT_RECIPE_REQUEST) {
            // レシピブック自体が 316 に無いため落とす。
            LOG.warning("316に直せないパケット " + name + " を落とします。");
            return null;
        }
        OptionalInt id316 = PacketIds.id(name, State.PLAY, Direction.SERVERBOUND,
                ProtocolVersion.V1_11_2);
        if (id316.isEmpty()) {
            LOG.warning("316に存在しないパケット " + name + "（758 id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return null;
        }
        int targetId = id316.getAsInt();
        return switch (name) {
            case PLAY_WINDOW_CLICK -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateWindowClick758To316(packet.payload()));
            case PLAY_TAB_COMPLETE -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateTabCompleteRequest758To316(packet.payload()));
            case PLAY_SET_CREATIVE_SLOT -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateSetCreativeSlot758To316(packet.payload()));
            case PLAY_CLIENT_INFORMATION -> new MinecraftPacket(targetId,
                    stripServerListings498(packet.payload()));
            case PLAY_BLOCK_PLACE -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateBlockPlace758To316(packet.payload()));
            case PLAY_BLOCK_DIG -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateBlockDig316(packet.payload(), true));
            case PLAY_UPDATE_SIGN -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateUpdateSign316(packet.payload(), true));
            case PLAY_UPDATE_COMMAND_BLOCK -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateUpdateCommandBlock316(packet.payload(), true));
            case PLAY_UPDATE_STRUCTURE_BLOCK -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateUpdateStructureBlock316(packet.payload(), true));
            case PLAY_QUERY_BLOCK_NBT -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateQueryBlockNbt316(packet.payload(), true));
            default -> new MinecraftPacket(targetId, packet.payload());
        };
    }
"""
anchor340 = """            default -> new MinecraftPacket(targetId, packet.payload());
        };
    }

    /**
     * 759形式のサーバー→クライアントのプレイ状態のパケット1個を758形式に直す。"""
sub_once(anchor340, """            default -> new MinecraftPacket(targetId, packet.payload());
        };
    }
""" + TO316 + """
    /**
     * 759形式のサーバー→クライアントのプレイ状態のパケット1個を758形式に直す。""")

# ---- 10. 大規模 switch 4本の 316 版を routeServerPlay340 の直後に生成 ----
def extract_method(sig):
    i = src.index(sig)
    # 直前の javadoc の開始まで遡る
    j = src.rindex("/**", 0, i)
    end = src.index("\n    }\n", i) + len("\n    }\n")
    return src[j:end]


def to316(text):
    text = text.replace("340", "316")
    text = text.replace("1.12.2", "1.11.2")
    text = text.replace("V1_12_2", "V1_11_2")
    return text


parts = []
m = to316(extract_method(
    "    private List<MinecraftPacket> translateServerPlayPacket758To340(MinecraftPacket packet) {"))
# 316 の統合パケットIDの orElse 既定値を 316 の実IDに直す
m = m.replace("PacketName.PLAY_TITLE,\n                        State.PLAY, Direction.CLIENTBOUND, ProtocolVersion.V1_11_2).orElse(0x48)",
              "PacketName.PLAY_TITLE,\n                        State.PLAY, Direction.CLIENTBOUND, ProtocolVersion.V1_11_2).orElse(0x45)")
m = m.replace("PacketName.PLAY_COMBAT_EVENT,\n                        State.PLAY, Direction.CLIENTBOUND, ProtocolVersion.V1_11_2).orElse(0x2D)",
              "PacketName.PLAY_COMBAT_EVENT,\n                        State.PLAY, Direction.CLIENTBOUND, ProtocolVersion.V1_11_2).orElse(0x2c)")
m = m.replace("PacketName.PLAY_WORLD_BORDER,\n                        State.PLAY, Direction.CLIENTBOUND, ProtocolVersion.V1_11_2).orElse(0x38)",
              "PacketName.PLAY_WORLD_BORDER,\n                        State.PLAY, Direction.CLIENTBOUND, ProtocolVersion.V1_11_2).orElse(0x35)")
parts.append(m)
parts.append(to316(extract_method(
    "    private List<MinecraftPacket> to758ServerPackets340(MinecraftPacket packet) {")))
parts.append(to316(extract_method(
    "    private MinecraftPacket to758ClientPacket340(MinecraftPacket packet) {")))
parts.append(to316(extract_method(
    "    private Outcome routeServerPlay340(MinecraftPacket packet) {")))
block = "\n" + "\n".join(parts)
route_end_sig = "    private Outcome routeServerPlay340(MinecraftPacket packet) {"
i = src.index(route_end_sig)
end = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:end] + "\n" + block + src[end:]

F.write_text(src, encoding="utf-8")
print("ProxySession に 316 の経路を追加した")
