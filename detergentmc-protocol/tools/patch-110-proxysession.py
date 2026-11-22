"""ProxySession.java に 110（1.9.4／1.9.3）の経路を追加する。

110 は 210 とパケット ID が完全同一で、ワイヤ形式の差分は3件だけ
（Stage A 実測：sound_effect／named_sound_effect の pitch が u8、
resource_pack_receive の先頭に hash がある。音の pitch は変換器側
＝ gen-110-translators.py）。Keep Alive は varint で 210 と同じ。
そのため経路は 210 の経路を写して番号を差し替えたものにし、
resource_pack_receive の hash の付け外しだけ 110 用に足す：
- 110 クライアント → 758 形式：先頭の hash（文字列）を落とす
- 758 形式 → 内部 110 サーバー：先頭に空の hash を補う
  （受理の実測は Stage E で行う）

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


def to110(text):
    text = text.replace("V1_10_2", "V1_9_4")
    text = text.replace("1.10.2", "1.9.4")
    text = text.replace("210", "110")
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
sub_all("is210() || is315() || is316()", "is110() || is210() || is315() || is316()", 4)
sub_all("|| is210())", "|| is210() || is110())", 13)
sub_all("|| isServer210())", "|| isServer210() || isServer110())", 7)
sub_once("|| isServer335() || isServer316() || isServer315() || isServer210()\n",
         "|| isServer335() || isServer316() || isServer315() || isServer210() || isServer110()\n")

# ---- 2. packFor763LikeClient の 110 分岐 ----
sub_once("""            if (is210()) {
                if (pack210 != null) {
                    return pack210;
                }
            }
""",
         """            if (is110()) {
                if (pack110 != null) {
                    return pack110;
                }
            }
            if (is210() || is110()) {
                if (pack210 != null) {
                    return pack210;
                }
            }
            if (is210()) {
                if (pack210 != null) {
                    return pack210;
                }
            }
""")

# ---- 3. フィールド ----
insert_after(
    """    /** 210サーバー向け変換で使う現在のディメンションID（210形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension210Server;
""",
    """
    /** 110クライアント向け変換で使う現在のディメンションID（110形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension110Client;

    /** 110サーバー向け変換で使う現在のディメンションID（110形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension110Server;
""")
insert_after(
    """    private final java.util.Map<Integer, Long> keepAliveIds210ToClient =
            new java.util.HashMap<>();
""",
    """
    /**
     * 110クライアントへ送った Keep Alive の、切り捨て前の i64 ID
     * （キー＝varint に切り捨てた int 値）。210 の復元表と同じ仕組み。
     */
    private final java.util.Map<Integer, Long> keepAliveIds110ToClient =
            new java.util.HashMap<>();
""")
insert_before(
    """    /**
     * 210クライアントに送るリソースパック（pack_format 2、旧レイアウト）。
""",
    """    /**
     * 110クライアントに送るリソースパック（pack_format 2、旧レイアウト）。
     * {@code null} の場合は210用パックにフォールバックする
     * （形式は pack_format 2 で、210 用とは別に用意する）。
     */
    private ResourcePack pack110;

""")
insert_before(
    """    /** 210クライアントに送るリソースパックを設定する（無ければ null）。 */""",
    """    /** 110クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack110(ResourcePack pack110) {
        this.pack110 = pack110;
    }

    /** 110クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack110() {
        return pack110;
    }

""")

# ---- 4. コンストラクタの検証列挙へ V1_9_4 を追加 ----
sub_once("        if (serverProtocol != ProtocolVersion.V1_10_2\n",
         "        if (serverProtocol != ProtocolVersion.V1_9_4\n"
         "                && serverProtocol != ProtocolVersion.V1_10_2\n")

# ---- 5. is110 / isServer110 ----
insert_after(
    """    /** 内部が 1.10／1.10.1／1.10.2（210）か。 */
    private boolean isServer210() {
        return serverProtocol == ProtocolVersion.V1_10_2;
    }
""",
    """
    /**
     * クライアントが 1.9.4／1.9.3（110）か。ワイヤ形式は 210 と
     * ほぼ同一で、差分は音の pitch（u8）と resource_pack_receive
     * の hash の2件（変換器と経路の 110 節が担う）。
     * Keep Alive は varint（210 と同じ）。
     */
    private boolean is110() {
        return clientProtocol == ProtocolVersion.V1_9_4;
    }

    /** 内部が 1.9.4／1.9.3（110）か。 */
    private boolean isServer110() {
        return serverProtocol == ProtocolVersion.V1_9_4;
    }
""")

# ---- 6. 分岐の複製（210 の分岐を写して 110 の分岐を前に足す） ----
# 6a. クライアント→758（サーバー行き）
branch = extract_span(
    "            } else if (is210()) {",
    "\n            } else if (is315()) {",
    include_end=False)
insert_before("            } else if (is210()) {", to110(branch) + "\n")

# 6b. クライアント→内部110サーバー
branch = extract_span(
    "        // 内部210：758サーバーと同じ経路",
    "\n        // 内部315：758サーバーと同じ経路",
    include_end=False)
insert_before("        // 内部210：758サーバーと同じ経路",
              to110(branch) + "\n")

# 6c. クライアント行き Outcome
branch = extract_span(
    "        if (is210()) {\n"
    "            // 210クライアント：763→762→761→760→759→758→210へ六段で変換する。",
    "            return convertServerPlayOutcomeTo210Slots(to210);\n        }")
insert_before("        if (is210()) {\n"
              "            // 210クライアント：763→762→761→760→759→758→210へ六段で変換する。",
              to110(branch) + "\n")

# 6d. 内部110サーバーからの経路
branch = extract_span(
    "        // 内部210：先にハイブリッド形式（404符号化＋210番号）へ直してから、",
    "            return routeServerPlay210(packet);\n        }")
insert_before("        // 内部210：先にハイブリッド形式（404符号化＋210番号）へ直してから、",
              to110(branch) + "\n")

# ---- 7. convertServerPlayOutcomeTo110 / To110Slots ----
pair = (extract_method(
            "    private Outcome convertServerPlayOutcomeTo210(Outcome outcome) {")
        + "\n"
        + extract_method(
            "    private Outcome convertServerPlayOutcomeTo210Slots(Outcome outcome) {"))
_i = src.index(
    "    private Outcome convertServerPlayOutcomeTo210Slots(Outcome outcome) {")
_end = src.index("\n    }\n", _i) + len("\n    }\n")
src = src[:_end] + "\n    " + to110(pair) + src[_end:]

# ---- 8. 小物ヘルパーの複製 ----
helper = extract_span(
    "    /** 210 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */",
    "    private static boolean hasSkyLight210(int dimensionId210) {\n"
    "        return dimensionId210 != -1;\n"
    "    }")
insert_after(helper, "\n\n" + to110(helper))

i = src.index("    private static int readDimension210FromRespawn(byte[] payload210) {")
end = src.index("\n    }\n", i) + len("\n    }\n")
dims = src[src.index(
    "    private static int readDimension210FromJoinGame(byte[] payload210) {"):end]
insert_after(dims, "\n\n" + to110(dims))

# ---- 8b. resource_pack_receive の hash 付け外しヘルパー ----
insert_after(
    """    private static byte[] stripServerListings498(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return java.util.Arrays.copyOf(payload, payload.length - 1);
    }
""",
    """
    /**
     * 110 の Resource Pack Response（先頭に hash の文字列＋result）を
     * 758 形式（result のみ）にする。hash は 758 形式に無いため捨てる。
     */
    private static byte[] stripResourcePackHash110(byte[] payload) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            reader.readString(); // hash（758形式には無いため捨てる）
            return reader.readBytes(reader.remaining());
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * 758 形式の Resource Pack Response（result のみ）を 110 形式
     * （先頭に hash の文字列＋result）にする。hash は空文字列を補う
     * （実バニラ 1.9.4 での受理は Stage E で確認する）。
     */
    private static byte[] prependResourcePackHash110(byte[] payload) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("");
        writer.writeBytes(payload);
        return writer.bytes();
    }
""")

# ---- 9. 大規模 switch 5本の 110 版を routeServerPlay210 の直後に生成 ----
parts = []
for sig in (
    "    private List<MinecraftPacket> translateServerPlayPacket758To210(MinecraftPacket packet) {",
    "    private List<MinecraftPacket> to758ServerPackets210(MinecraftPacket packet) {",
    "    private MinecraftPacket to758ClientPacket210(MinecraftPacket packet) {",
    "    private Outcome routeServerPlay210(MinecraftPacket packet) {",
    "    private MinecraftPacket to210ClientPacket758(MinecraftPacket packet) {",
):
    parts.append("    " + to110(extract_method(sig)))
block = "\n" + "\n".join(parts)

# ---- 10. 生成した 110 版の中の resource_pack_receive を直す ----
def bsub(old, new, expect=1):
    global block
    n = block.count(old)
    assert n == expect, f"生成ブロック内で一意でない({n} != {expect}): {old[:60]}"
    block = block.replace(old, new)

# 110 クライアント → 758 形式：先頭の hash を落とす
bsub("""            case PLAY_QUERY_BLOCK_NBT -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateQueryBlockNbt110(packet.payload(), false));
            default -> new MinecraftPacket(targetId, packet.payload());""",
     """            case PLAY_QUERY_BLOCK_NBT -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateQueryBlockNbt110(packet.payload(), false));
            case PLAY_RESOURCE_PACK_RESPONSE -> new MinecraftPacket(targetId,
                    stripResourcePackHash110(packet.payload()));
            default -> new MinecraftPacket(targetId, packet.payload());""")
# 758 形式 → 内部 110 サーバー：先頭に空の hash を補う
bsub("""            case PLAY_QUERY_BLOCK_NBT -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateQueryBlockNbt110(packet.payload(), true));
            default -> new MinecraftPacket(targetId, packet.payload());""",
     """            case PLAY_QUERY_BLOCK_NBT -> new MinecraftPacket(targetId,
                    ChunkTranslator.translateQueryBlockNbt110(packet.payload(), true));
            case PLAY_RESOURCE_PACK_RESPONSE -> new MinecraftPacket(targetId,
                    prependResourcePackHash110(packet.payload()));
            default -> new MinecraftPacket(targetId, packet.payload());""")

route_sig = "    private Outcome routeServerPlay210(MinecraftPacket packet) {"
i = src.index(route_sig)
end = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:end] + "\n" + block + src[end:]

F.write_text(src, encoding="utf-8")
print("ProxySession に 110 の経路を追加した")
