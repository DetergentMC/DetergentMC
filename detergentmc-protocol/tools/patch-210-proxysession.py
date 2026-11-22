"""ProxySession.java に 210（1.10／1.10.1／1.10.2）の経路を追加する。

210 は 315 とパケット ID が完全同一で、ワイヤ形式の差分は4件だけ
（Stage A 実測：spawn_entity_living の type が u8、title の action
番号、collect の pickupItemCount 欠落、block_place のカーソルが
i8。変換器側は gen-210-translators.py）。Keep Alive は varint で
315 と同じ。そのため経路は 315 の経路を写して番号を差し替えた
ものにし、title／アクションバー／collect の3箇所だけ 210 用に
直す：
- 758 の PLAY_ACTION_BAR は 210 の Chat（position=2）へ振り替え
- 210 の Title は action 番号が違うため専用ヘルパーで分割
- 210 の Chat（position=2）は 758 の PLAY_ACTION_BAR へ振り替え
- collect は pickupItemCount の付け外しを変換器に委ねる

冪等ではない（2回実行すると二重定義になる）。git diff で確認する
こと。
"""

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


def to210(text):
    text = text.replace("V1_11", "V1_10_2")
    text = text.replace("1.11", "1.10.2")
    text = text.replace("315", "210")
    return text


def extract_span(start_anchor, end_marker, include_end=True):
    i = src.index(start_anchor)
    j = src.index(end_marker, i)
    if include_end:
        j += len(end_marker)
    return src[i:j]


def extract_method(sig):
    i = src.index(sig)
    j = src.rindex("/**", 0, i)
    end = src.index("\n    }\n", i) + len("\n    }\n")
    return src[j:end]


# ---- 1. 列挙への追加（旧世代の判定群＋packFor の累積フォールバック） ----
sub_all("is315() || is316()", "is210() || is315() || is316()", 4)
sub_all("|| is315())", "|| is315() || is210())", 12)
sub_all("|| isServer315())", "|| isServer315() || isServer210())", 7)
sub_once("|| isServer335() || isServer316() || isServer315()\n",
         "|| isServer335() || isServer316() || isServer315() || isServer210()\n")

# ---- 2. packFor763LikeClient の 210 分岐 ----
sub_once("""            if (is315()) {
                if (pack315 != null) {
                    return pack315;
                }
            }
""",
         """            if (is210()) {
                if (pack210 != null) {
                    return pack210;
                }
            }
            if (is315() || is210()) {
                if (pack315 != null) {
                    return pack315;
                }
            }
            if (is315()) {
                if (pack315 != null) {
                    return pack315;
                }
            }
""")

# ---- 3. フィールド ----
insert_after(
    """    /** 315サーバー向け変換で使う現在のディメンションID（315形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension315Server;
""",
    """
    /** 210クライアント向け変換で使う現在のディメンションID（210形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension210Client;

    /** 210サーバー向け変換で使う現在のディメンションID（210形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension210Server;
""")
insert_after(
    """    private final java.util.Map<Integer, Long> keepAliveIds315ToClient =
            new java.util.HashMap<>();
""",
    """
    /**
     * 210クライアントへ送った Keep Alive の、切り捨て前の i64 ID
     * （キー＝varint に切り捨てた int 値）。315 の復元表と同じ仕組み。
     */
    private final java.util.Map<Integer, Long> keepAliveIds210ToClient =
            new java.util.HashMap<>();
""")
insert_before(
    """    /**
     * 315クライアントに送るリソースパック（pack_format 3、旧レイアウト）。
""",
    """    /**
     * 210クライアントに送るリソースパック（pack_format 2、旧レイアウト）。
     * {@code null} の場合は315用パックにフォールバックする
     * （形式は pack_format 2 で、315 用とは別に用意する）。
     */
    private ResourcePack pack210;

""")
insert_before(
    """    /** 315クライアントに送るリソースパックを設定する（無ければ null）。 */""",
    """    /** 210クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack210(ResourcePack pack210) {
        this.pack210 = pack210;
    }

    /** 210クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack210() {
        return pack210;
    }

""")

# ---- 4. コンストラクタの検証列挙へ V1_10_2 を追加 ----
sub_once("        if (serverProtocol != ProtocolVersion.V1_11\n",
         "        if (serverProtocol != ProtocolVersion.V1_10_2\n"
         "                && serverProtocol != ProtocolVersion.V1_11\n")

# ---- 5. is210 / isServer210 ----
insert_after(
    """    /** 内部が 1.11（315）か。 */
    private boolean isServer315() {
        return serverProtocol == ProtocolVersion.V1_11;
    }
""",
    """
    /**
     * クライアントが 1.10／1.10.1／1.10.2（210）か。ワイヤ形式は
     * 315 とほぼ同一で、差分4件（spawn type の u8、title の action
     * 番号、collect、block_place のカーソル）は変換器と経路の
     * 210 節が担う。Keep Alive は varint（315 と同じ）。
     */
    private boolean is210() {
        return clientProtocol == ProtocolVersion.V1_10_2;
    }

    /** 内部が 1.10／1.10.1／1.10.2（210）か。 */
    private boolean isServer210() {
        return serverProtocol == ProtocolVersion.V1_10_2;
    }
""")

# ---- 6. 分岐の複製（315 の分岐を写して 210 の分岐を前に足す） ----
# 6a. クライアント→758（サーバー行き）
branch = extract_span(
    "            } else if (is315()) {",
    "\n            } else if (is316()) {",
    include_end=False)
insert_before("            } else if (is315()) {", to210(branch) + "\n")

# 6b. クライアント→内部210サーバー
branch = extract_span(
    "        // 内部315：758サーバーと同じ経路",
    "\n        // 内部316：758サーバーと同じ経路",
    include_end=False)
insert_before("        // 内部315：758サーバーと同じ経路",
              to210(branch) + "\n")

# 6c. クライアント行き Outcome
branch = extract_span(
    "        if (is315()) {\n"
    "            // 315クライアント：763→762→761→760→759→758→315へ六段で変換する。",
    "            return convertServerPlayOutcomeTo315Slots(to315);\n        }")
insert_before("        if (is315()) {\n"
              "            // 315クライアント：763→762→761→760→759→758→315へ六段で変換する。",
              to210(branch) + "\n")

# 6d. 内部210サーバーからの経路
branch = extract_span(
    "        // 内部315：先にハイブリッド形式（404符号化＋315番号）へ直してから、",
    "            return routeServerPlay315(packet);\n        }")
insert_before("        // 内部315：先にハイブリッド形式（404符号化＋315番号）へ直してから、",
              to210(branch) + "\n")

# ---- 7. convertServerPlayOutcomeTo210 / To210Slots ----
pair = (extract_method(
            "    private Outcome convertServerPlayOutcomeTo315(Outcome outcome) {")
        + "\n"
        + extract_method(
            "    private Outcome convertServerPlayOutcomeTo315Slots(Outcome outcome) {"))
_i = src.index(
    "    private Outcome convertServerPlayOutcomeTo315Slots(Outcome outcome) {")
_end = src.index("\n    }\n", _i) + len("\n    }\n")
src = src[:_end] + "\n    " + to210(pair) + src[_end:]

# ---- 8. 小物ヘルパーの複製 ----
helper = extract_span(
    "    /** 315 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */",
    "    private static boolean hasSkyLight315(int dimensionId315) {\n"
    "        return dimensionId315 != -1;\n"
    "    }")
insert_after(helper, "\n\n" + to210(helper))

i = src.index("    private static int readDimension315FromRespawn(byte[] payload315) {")
end = src.index("\n    }\n", i) + len("\n    }\n")
dims = src[src.index(
    "    private static int readDimension315FromJoinGame(byte[] payload315) {"):end]
insert_after(dims, "\n\n" + to210(dims))

# ---- 9. 大規模 switch 5本の 210 版を routeServerPlay315 の直後に生成 ----
parts = []
for sig in (
    "    private List<MinecraftPacket> translateServerPlayPacket758To315(MinecraftPacket packet) {",
    "    private List<MinecraftPacket> to758ServerPackets315(MinecraftPacket packet) {",
    "    private MinecraftPacket to758ClientPacket315(MinecraftPacket packet) {",
    "    private Outcome routeServerPlay315(MinecraftPacket packet) {",
    "    private MinecraftPacket to315ClientPacket758(MinecraftPacket packet) {",
):
    parts.append("    " + to210(extract_method(sig)))
block = "\n" + "\n".join(parts)

# ---- 10. 生成した 210 版の中の title／アクションバー／collect を直す ----
def bsub(old, new, expect=1):
    global block
    n = block.count(old)
    assert n == expect, f"生成ブロック内で一意でない({n} != {expect}): {old[:60]}"
    block = block.replace(old, new)

# title（758 → 210）：498 向け合成ではなく 210 専用ヘルパーへ
bsub("ChunkTranslator.titleText758To498", "ChunkTranslator.titleText758To210")
bsub("ChunkTranslator.titleSubtitle758To498",
     "ChunkTranslator.titleSubtitle758To210")
bsub("ChunkTranslator.titleAnimation758To498",
     "ChunkTranslator.titleAnimation758To210")
bsub("ChunkTranslator.titleClear758To498", "ChunkTranslator.titleClear758To210")
# アクションバー（758 → 210）：Title ではなく Chat（position=2）へ
bsub("""            case PLAY_ACTION_BAR -> {
                int id = PacketIds.id(PacketName.PLAY_TITLE,
                        State.PLAY, Direction.CLIENTBOUND, ProtocolVersion.V1_10_2).orElse(0x45);
                return List.of(new MinecraftPacket(id,
                        ChunkTranslator.titleActionBar758To498(packet.payload())));
            }""",
     """            case PLAY_ACTION_BAR -> {
                // 210 の Title にアクションバーは無い。Chat の
                // position=2（アクションバー表示）で送る。
                int id = PacketIds.id(PacketName.PLAY_CHAT,
                        State.PLAY, Direction.CLIENTBOUND, ProtocolVersion.V1_10_2).orElse(0x0f);
                return List.of(new MinecraftPacket(id,
                        ChunkTranslator.actionBar758ToChat210(packet.payload())));
            }""")
# collect（758 → 210）
bsub("""            case PLAY_CHUNK_DATA -> List.of(new MinecraftPacket(targetId,
                    ChunkTranslator.translateChunkData758To210(
                            packet.payload(), hasSkyLight210(dimension210Client))));""",
     """            case PLAY_CHUNK_DATA -> List.of(new MinecraftPacket(targetId,
                    ChunkTranslator.translateChunkData758To210(
                            packet.payload(), hasSkyLight210(dimension210Client))));
            case PLAY_COLLECT -> List.of(new MinecraftPacket(targetId,
                    ChunkTranslator.translateCollect758To210(packet.payload())));""")
# title（210 → 758）：action 番号の対応と専用ヘルパー
bsub("""                int action = ChunkTranslator.titleAction498(packet.payload());
                PacketName target = switch (action) {
                    case 0 -> PacketName.PLAY_SET_TITLE_TEXT;
                    case 1 -> PacketName.PLAY_SET_TITLE_SUBTITLE;
                    case 2 -> PacketName.PLAY_ACTION_BAR;
                    case 3 -> PacketName.PLAY_SET_TITLE_TIME;
                    case 4, 5 -> PacketName.PLAY_CLEAR_TITLES;
                    default -> null;
                };""",
     """                int action = ChunkTranslator.titleAction210(packet.payload());
                PacketName target = switch (action) {
                    case 0 -> PacketName.PLAY_SET_TITLE_TEXT;
                    case 1 -> PacketName.PLAY_SET_TITLE_SUBTITLE;
                    case 2 -> PacketName.PLAY_SET_TITLE_TIME;
                    case 3, 4 -> PacketName.PLAY_CLEAR_TITLES;
                    default -> null;
                };""")
bsub("ChunkTranslator.translateTitle498To758",
     "ChunkTranslator.translateTitle210To758")
# chat（210 → 758）：position=2 はアクションバーへ振り替え
bsub("""            case PLAY_CHAT -> List.of(new MinecraftPacket(targetId,
                    PlayTranslator.translateChat578To758(packet.payload())));""",
     """            case PLAY_CHAT -> {
                // 210 のアクションバーは Chat の position=2 で届く。
                // 758 の PLAY_ACTION_BAR へ振り替える。
                if (ChunkTranslator.chatPosition210(packet.payload()) == 2) {
                    int barId = PacketIds.id(PacketName.PLAY_ACTION_BAR,
                            State.PLAY, Direction.CLIENTBOUND, 758).orElseThrow();
                    yield List.of(new MinecraftPacket(barId,
                            ChunkTranslator.chat210ToActionBar758(packet.payload())));
                }
                yield List.of(new MinecraftPacket(targetId,
                        PlayTranslator.translateChat578To758(packet.payload())));
            }""")
# collect（210 → 758）
bsub("""            case PLAY_CHUNK_DATA -> List.of(new MinecraftPacket(targetId,
                    ChunkTranslator.translateChunkData210To758(
                            packet.payload(), hasSkyLight210(dimension210Server))));""",
     """            case PLAY_CHUNK_DATA -> List.of(new MinecraftPacket(targetId,
                    ChunkTranslator.translateChunkData210To758(
                            packet.payload(), hasSkyLight210(dimension210Server))));
            case PLAY_COLLECT -> List.of(new MinecraftPacket(targetId,
                    ChunkTranslator.translateCollect210To758(packet.payload())));""")

route_sig = "    private Outcome routeServerPlay315(MinecraftPacket packet) {"
i = src.index(route_sig)
end = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:end] + "\n" + block + src[end:]

F.write_text(src, encoding="utf-8")
print("ProxySession に 210 の経路を追加した")
