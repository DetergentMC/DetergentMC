"""ProxySession.java に 109（1.9.2）の経路を追加する。

109 は 110 とレジストリが完全同一で、ワイヤ形式の差分は
map_chunk の blockEntities（110 で追加）の1件だけ（Stage A
実測。変換器側は gen-109-translators.py）。パケット ID の差分
（update_sign の有無＋後続6件のずれ）は PacketIds の 109 列が
吸収するため、経路は 110 の経路を写して番号を差し替えたものに
なる。Keep Alive は varint で 110 と同じ。resource_pack_receive
の hash も 110 と同じく先頭にあるため、110 用の付け外しを
109 用にも用意する。

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


def to109(text):
    text = text.replace("V1_9_4", "V1_9_2")
    text = text.replace("1.9.4", "1.9.2")
    text = text.replace("110", "109")
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


# ---- 1. 列挙への追加（旧世代の判定群） ----
sub_all("is110() ||", "is109() || is110() ||", 4)
sub_all("|| is110())", "|| is110() || is109())", 14)
sub_all("|| isServer110())", "|| isServer110() || isServer109())", 7)
sub_all("|| isServer110()\n", "|| isServer110() || isServer109()\n", 1)

# ---- 2. packFor763LikeClient の 109 分岐（109→110→210 の順に試す） ----
sub_once("""            if (is110()) {
                if (pack110 != null) {
                    return pack110;
                }
            }
""",
         """            if (is109()) {
                if (pack109 != null) {
                    return pack109;
                }
            }
            if (is110() || is109()) {
                if (pack110 != null) {
                    return pack110;
                }
            }
            if (is110()) {
                if (pack110 != null) {
                    return pack110;
                }
            }
""")

# ---- 3. フィールド ----
insert_after(
    """    /** 110サーバー向け変換で使う現在のディメンションID（110形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension110Server;
""",
    """
    /** 109クライアント向け変換で使う現在のディメンションID（109形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension109Client;

    /** 109サーバー向け変換で使う現在のディメンションID（109形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension109Server;
""")
insert_after(
    """    private final java.util.Map<Integer, Long> keepAliveIds110ToClient =
            new java.util.HashMap<>();
""",
    """
    /**
     * 109クライアントへ送った Keep Alive の、切り捨て前の i64 ID
     * （キー＝varint に切り捨てた int 値）。110 の復元表と同じ仕組み。
     */
    private final java.util.Map<Integer, Long> keepAliveIds109ToClient =
            new java.util.HashMap<>();
""")
insert_before(
    """    /**
     * 110クライアントに送るリソースパック（pack_format 2、旧レイアウト）。
""",
    """    /**
     * 109クライアントに送るリソースパック（pack_format 2、旧レイアウト）。
     * {@code null} の場合は110用パックにフォールバックする
     * （形式は pack_format 2 で、110 用とは別に用意する）。
     */
    private ResourcePack pack109;

""")
insert_before(
    """    /** 110クライアントに送るリソースパックを設定する（無ければ null）。 */""",
    """    /** 109クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack109(ResourcePack pack109) {
        this.pack109 = pack109;
    }

    /** 109クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack109() {
        return pack109;
    }

""")

# ---- 4. コンストラクタの検証列挙へ V1_9_2 を追加 ----
sub_once("        if (serverProtocol != ProtocolVersion.V1_9_4\n",
         "        if (serverProtocol != ProtocolVersion.V1_9_2\n"
         "                && serverProtocol != ProtocolVersion.V1_9_4\n")

# ---- 5. is109 / isServer109 ----
insert_after(
    """    /** 内部が 1.9.4／1.9.3（110）か。 */
    private boolean isServer110() {
        return serverProtocol == ProtocolVersion.V1_9_4;
    }
""",
    """
    /**
     * クライアントが 1.9.2（109）か。ワイヤ形式は 110 とほぼ同一で、
     * 差分は map_chunk の blockEntities が無いことだけ（変換器と
     * 経路の 109 節が担う）。パケット ID の差分（update_sign の
     * 有無＋後続6件のずれ）は PacketIds の 109 列が吸収する。
     * Keep Alive は varint（110 と同じ）。
     */
    private boolean is109() {
        return clientProtocol == ProtocolVersion.V1_9_2;
    }

    /** 内部が 1.9.2（109）か。 */
    private boolean isServer109() {
        return serverProtocol == ProtocolVersion.V1_9_2;
    }
""")

# ---- 6. 分岐の複製（110 の分岐を写して 109 の分岐を前に足す） ----
# 6a. クライアント→758（サーバー行き）
branch = extract_span(
    "            } else if (is110()) {",
    "\n            } else if (is210()) {",
    include_end=False)
insert_before("            } else if (is110()) {", to109(branch) + "\n")

# 6b. クライアント→内部109サーバー
branch = extract_span(
    "        // 内部110：758サーバーと同じ経路",
    "\n        // 内部210：758サーバーと同じ経路",
    include_end=False)
insert_before("        // 内部110：758サーバーと同じ経路",
              to109(branch) + "\n")

# 6c. クライアント行き Outcome
branch = extract_span(
    "        if (is110()) {\n"
    "            // 110クライアント：763→762→761→760→759→758→110へ六段で変換する。",
    "            return convertServerPlayOutcomeTo110Slots(to110);\n        }")
insert_before("        if (is110()) {\n"
              "            // 110クライアント：763→762→761→760→759→758→110へ六段で変換する。",
              to109(branch) + "\n")

# 6d. 内部109サーバーからの経路
branch = extract_span(
    "        // 内部110：先にハイブリッド形式（404符号化＋110番号）へ直してから、",
    "            return routeServerPlay110(packet);\n        }")
insert_before("        // 内部110：先にハイブリッド形式（404符号化＋110番号）へ直してから、",
              to109(branch) + "\n")

# ---- 7. convertServerPlayOutcomeTo109 / To109Slots ----
pair = (extract_method(
            "    private Outcome convertServerPlayOutcomeTo110(Outcome outcome) {")
        + "\n"
        + extract_method(
            "    private Outcome convertServerPlayOutcomeTo110Slots(Outcome outcome) {"))
_i = src.index(
    "    private Outcome convertServerPlayOutcomeTo110Slots(Outcome outcome) {")
_end = src.index("\n    }\n", _i) + len("\n    }\n")
src = src[:_end] + "\n    " + to109(pair) + src[_end:]

# ---- 8. 小物ヘルパーの複製 ----
helper = extract_span(
    "    /** 110 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */",
    "    private static boolean hasSkyLight110(int dimensionId110) {\n"
    "        return dimensionId110 != -1;\n"
    "    }")
insert_after(helper, "\n\n" + to109(helper))

i = src.index("    private static int readDimension110FromRespawn(byte[] payload110) {")
end = src.index("\n    }\n", i) + len("\n    }\n")
dims = src[src.index(
    "    private static int readDimension110FromJoinGame(byte[] payload110) {"):end]
insert_after(dims, "\n\n" + to109(dims))

# ---- 8b. resource_pack_receive の hash 付け外しヘルパー（109 用） ----
helper = extract_span(
    "    /**\n"
    "     * 110 の Resource Pack Response（先頭に hash の文字列＋result）を",
    "    private static byte[] prependResourcePackHash110(byte[] payload) {\n"
    "        PacketIO.Writer writer = new PacketIO.Writer();\n"
    "        writer.writeString(\"\");\n"
    "        writer.writeBytes(payload);\n"
    "        return writer.bytes();\n"
    "    }")
insert_after(helper, "\n\n" + to109(helper))

# ---- 9. 大規模 switch 5本の 109 版を routeServerPlay110 の直後に生成 ----
parts = []
for sig in (
    "    private List<MinecraftPacket> translateServerPlayPacket758To110(MinecraftPacket packet) {",
    "    private List<MinecraftPacket> to758ServerPackets110(MinecraftPacket packet) {",
    "    private MinecraftPacket to758ClientPacket110(MinecraftPacket packet) {",
    "    private Outcome routeServerPlay110(MinecraftPacket packet) {",
    "    private MinecraftPacket to110ClientPacket758(MinecraftPacket packet) {",
):
    parts.append("    " + to109(extract_method(sig)))
block = "\n" + "\n".join(parts)

route_sig = "    private Outcome routeServerPlay110(MinecraftPacket packet) {"
i = src.index(route_sig)
end = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:end] + "\n" + block + src[end:]

F.write_text(src, encoding="utf-8")
print("ProxySession に 109 の経路を追加した")
