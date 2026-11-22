"""ProxySession.java に 108（1.9.1）の経路を追加する。

108 は 109 とレジストリ・ワイヤ形式とも完全同一（Stage A
実測。変換器側は gen-108-translators.py）。パケット ID も
109 と完全一致するため、経路は 109 の経路を写して番号を
差し替えたものになる。Keep Alive は varint で 109 と同じ。
resource_pack_receive の hash も 109 と同じく先頭にあるため、
109 用の付け外しを 108 用にも用意する。

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


def to108(text):
    text = text.replace("V1_9_2", "V1_9_1")
    text = text.replace("1.9.2", "1.9.1")
    text = text.replace("109", "108")
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
sub_all("is109() ||", "is108() || is109() ||", 4)
sub_all("|| is109())", "|| is109() || is108())", 15)
sub_all("|| isServer109())", "|| isServer109() || isServer108())", 7)
sub_all("|| isServer109()\n", "|| isServer109() || isServer108()\n", 1)

# ---- 2. packFor763LikeClient の 108 分岐（108→109→110 の順に試す） ----
sub_once("""            if (is109()) {
                if (pack109 != null) {
                    return pack109;
                }
            }
""",
         """            if (is108()) {
                if (pack108 != null) {
                    return pack108;
                }
            }
            if (is109() || is108()) {
                if (pack109 != null) {
                    return pack109;
                }
            }
            if (is109()) {
                if (pack109 != null) {
                    return pack109;
                }
            }
""")

# ---- 3. フィールド ----
insert_after(
    """    /** 109サーバー向け変換で使う現在のディメンションID（109形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension109Server;
""",
    """
    /** 108クライアント向け変換で使う現在のディメンションID（108形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension108Client;

    /** 108サーバー向け変換で使う現在のディメンションID（108形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension108Server;
""")
insert_after(
    """    private final java.util.Map<Integer, Long> keepAliveIds109ToClient =
            new java.util.HashMap<>();
""",
    """
    /**
     * 108クライアントへ送った Keep Alive の、切り捨て前の i64 ID
     * （キー＝varint に切り捨てた int 値）。109 の復元表と同じ仕組み。
     */
    private final java.util.Map<Integer, Long> keepAliveIds108ToClient =
            new java.util.HashMap<>();
""")
insert_before(
    """    /**
     * 109クライアントに送るリソースパック（pack_format 2、旧レイアウト）。
""",
    """    /**
     * 108クライアントに送るリソースパック（pack_format 2、旧レイアウト）。
     * {@code null} の場合は109用パックにフォールバックする
     * （形式は pack_format 2 で、109 用とは別に用意する）。
     */
    private ResourcePack pack108;

""")
insert_before(
    """    /** 109クライアントに送るリソースパックを設定する（無ければ null）。 */""",
    """    /** 108クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack108(ResourcePack pack108) {
        this.pack108 = pack108;
    }

    /** 108クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack108() {
        return pack108;
    }

""")

# ---- 4. コンストラクタの検証列挙へ V1_9_1 を追加 ----
sub_once("        if (serverProtocol != ProtocolVersion.V1_9_2\n",
         "        if (serverProtocol != ProtocolVersion.V1_9_1\n"
         "                && serverProtocol != ProtocolVersion.V1_9_2\n")

# ---- 5. is108 / isServer108 ----
insert_after(
    """    /** 内部が 1.9.2（109）か。 */
    private boolean isServer109() {
        return serverProtocol == ProtocolVersion.V1_9_2;
    }
""",
    """
    /**
     * クライアントが 1.9.1（108）か。ワイヤ形式は 109 と完全同一
     * （Stage A 実測：両 jar の全面比較＋ワイヤ採取）で、パケット
     * ID も 109 と完全一致する。Keep Alive は varint（109 と同じ）。
     */
    private boolean is108() {
        return clientProtocol == ProtocolVersion.V1_9_1;
    }

    /** 内部が 1.9.1（108）か。 */
    private boolean isServer108() {
        return serverProtocol == ProtocolVersion.V1_9_1;
    }
""")

# ---- 6. 分岐の複製（109 の分岐を写して 108 の分岐を前に足す） ----
# 6a. クライアント→758（サーバー行き）
branch = extract_span(
    "            } else if (is109()) {",
    "\n            } else if (is110()) {",
    include_end=False)
insert_before("            } else if (is109()) {", to108(branch) + "\n")

# 6b. クライアント→内部108サーバー
branch = extract_span(
    "        // 内部109：758サーバーと同じ経路",
    "\n        // 内部110：758サーバーと同じ経路",
    include_end=False)
insert_before("        // 内部109：758サーバーと同じ経路",
              to108(branch) + "\n")

# 6c. クライアント行き Outcome
branch = extract_span(
    "        if (is109()) {\n"
    "            // 109クライアント：763→762→761→760→759→758→109へ六段で変換する。",
    "            return convertServerPlayOutcomeTo109Slots(to109);\n        }")
insert_before("        if (is109()) {\n"
              "            // 109クライアント：763→762→761→760→759→758→109へ六段で変換する。",
              to108(branch) + "\n")

# 6d. 内部108サーバーからの経路
branch = extract_span(
    "        // 内部109：先にハイブリッド形式（404符号化＋109番号）へ直してから、",
    "            return routeServerPlay109(packet);\n        }")
insert_before("        // 内部109：先にハイブリッド形式（404符号化＋109番号）へ直してから、",
              to108(branch) + "\n")

# ---- 7. convertServerPlayOutcomeTo108 / To108Slots ----
pair = (extract_method(
            "    private Outcome convertServerPlayOutcomeTo109(Outcome outcome) {")
        + "\n"
        + extract_method(
            "    private Outcome convertServerPlayOutcomeTo109Slots(Outcome outcome) {"))
_i = src.index(
    "    private Outcome convertServerPlayOutcomeTo109Slots(Outcome outcome) {")
_end = src.index("\n    }\n", _i) + len("\n    }\n")
src = src[:_end] + "\n    " + to108(pair) + src[_end:]

# ---- 8. 小物ヘルパーの複製 ----
helper = extract_span(
    "    /** 109 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */",
    "    private static boolean hasSkyLight109(int dimensionId109) {\n"
    "        return dimensionId109 != -1;\n"
    "    }")
insert_after(helper, "\n\n" + to108(helper))

i = src.index("    private static int readDimension109FromRespawn(byte[] payload109) {")
end = src.index("\n    }\n", i) + len("\n    }\n")
dims = src[src.index(
    "    private static int readDimension109FromJoinGame(byte[] payload109) {"):end]
insert_after(dims, "\n\n" + to108(dims))

# ---- 8b. resource_pack_receive の hash 付け外しヘルパー（108 用） ----
helper = extract_span(
    "    /**\n"
    "     * 758 形式の Resource Pack Response（result のみ）を 109 形式",
    "    private static byte[] prependResourcePackHash109(byte[] payload) {\n"
    "        PacketIO.Writer writer = new PacketIO.Writer();\n"
    "        writer.writeString(\"\");\n"
    "        writer.writeBytes(payload);\n"
    "        return writer.bytes();\n"
    "    }")
insert_after(helper, "\n\n" + to108(helper))

# ---- 8c. stripResourcePackHash108（109 用が strip→prepend の対で
# 置かれているため、prepend の直前に strip も複製する） ----
helper = extract_span(
    "    /**\n"
    "     * 109 の Resource Pack Response（先頭に hash の文字列＋result）を",
    "    private static byte[] stripResourcePackHash109(byte[] payload) {\n"
    "        try {\n"
    "            PacketIO.Reader reader = new PacketIO.Reader(payload);\n"
    "            reader.readString(); // hash（758形式には無いため捨てる）\n"
    "            return reader.readBytes(reader.remaining());\n"
    "        } catch (IllegalArgumentException e) {\n"
    "            return payload;\n"
    "        }\n"
    "    }")
insert_before(
    "    /**\n"
    "     * 758 形式の Resource Pack Response（result のみ）を 108 形式",
    to108(helper) + "\n\n")

# ---- 9. 大規模 switch 5本の 108 版を routeServerPlay109 の直後に生成 ----
parts = []
for sig in (
    "    private List<MinecraftPacket> translateServerPlayPacket758To109(MinecraftPacket packet) {",
    "    private List<MinecraftPacket> to758ServerPackets109(MinecraftPacket packet) {",
    "    private MinecraftPacket to758ClientPacket109(MinecraftPacket packet) {",
    "    private Outcome routeServerPlay109(MinecraftPacket packet) {",
    "    private MinecraftPacket to109ClientPacket758(MinecraftPacket packet) {",
):
    parts.append("    " + to108(extract_method(sig)))
block = "\n" + "\n".join(parts)

route_sig = "    private Outcome routeServerPlay109(MinecraftPacket packet) {"
i = src.index(route_sig)
end = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:end] + "\n" + block + src[end:]

F.write_text(src, encoding="utf-8")
print("ProxySession に 108 の経路を追加した")
