"""ProxySession.java に 315（1.11）の経路を追加する。

315 は 316 とワイヤ形式が完全同一（Stage A 実測：パケットID差分
0件、形式差分なし、Keep Alive は両者 varint）で、レジストリ差分は
アイテム1件だけ（RegistryRemapper の 315 専用表が担う）。そのため
経路は 316 の経路を写して番号を差し替えたものにする。大規模な
switch メソッド5本（translateServerPlayPacket758To316・
to758ServerPackets316・to758ClientPacket316・routeServerPlay316・
to316ClientPacket758）は 316 版のソースをテキスト変換して生成する。

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


def to315(text):
    text = text.replace("V1_11_2", "V1_11")
    text = text.replace("1.11.2", "1.11")
    text = text.replace("316", "315")
    return text


def extract_span(start_anchor, end_marker, include_end=True):
    """start_anchor から end_marker の直前（または含む）までを切り出す。"""
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
sub_all("is316() || is335()", "is315() || is316() || is335()", 4)
sub_all("|| is316())", "|| is316() || is315())", 11)
sub_all("|| isServer316())", "|| isServer316() || isServer315())", 7)
sub_once("|| isServer335() || isServer316()\n",
         "|| isServer335() || isServer316() || isServer315()\n")

# ---- 2. packFor763LikeClient の 315 分岐 ----
sub_once("""            if (is316()) {
                if (pack316 != null) {
                    return pack316;
                }
            }
""",
         """            if (is315()) {
                if (pack315 != null) {
                    return pack315;
                }
            }
            if (is316() || is315()) {
                if (pack316 != null) {
                    return pack316;
                }
            }
""")

# ---- 3. フィールド ----
insert_after(
    """    /** 316サーバー向け変換で使う現在のディメンションID（316形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension316Server;
""",
    """
    /** 315クライアント向け変換で使う現在のディメンションID（315形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension315Client;

    /** 315サーバー向け変換で使う現在のディメンションID（315形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension315Server;
""")
insert_after(
    """    private final java.util.Map<Integer, Long> keepAliveIds316ToClient =
            new java.util.HashMap<>();
""",
    """
    /**
     * 315クライアントへ送った Keep Alive の、切り捨て前の i64 ID
     * （キー＝varint に切り捨てた int 値）。316 の復元表と同じ仕組み。
     */
    private final java.util.Map<Integer, Long> keepAliveIds315ToClient =
            new java.util.HashMap<>();
""")
insert_before(
    """    /**
     * 338クライアントに送るリソースパック（pack_format 3、旧レイアウト）。
     * {@code null} の場合は340用パックにフォールバックする
""",
    """    /**
     * 315クライアントに送るリソースパック（pack_format 3、旧レイアウト）。
     * {@code null} の場合は316用パックにフォールバックする
     * （形式・レイアウトは同一で、説明文だけが 1.11 用になる）。
     */
    private ResourcePack pack315;

""")
insert_before(
    """    /** 316クライアントに送るリソースパックを設定する（無ければ null）。 */""",
    """    /** 315クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack315(ResourcePack pack315) {
        this.pack315 = pack315;
    }

    /** 315クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack315() {
        return pack315;
    }

""")

# ---- 4. コンストラクタの検証列挙へ V1_11 を追加 ----
sub_once("        if (serverProtocol != ProtocolVersion.V1_11_2\n",
         "        if (serverProtocol != ProtocolVersion.V1_11\n"
         "                && serverProtocol != ProtocolVersion.V1_11_2\n")

# ---- 5. is315 / isServer315 ----
insert_after(
    """    /** 内部が 1.11.2（316）か。 */
    private boolean isServer316() {
        return serverProtocol == ProtocolVersion.V1_11_2;
    }
""",
    """
    /**
     * クライアントが 1.11（315）か。ワイヤ形式は 316 と完全同一で、
     * 変換経路も 316 の経路を写したものを使う（レジストリ差分は
     * アイテム1件だけ＝RegistryRemapper の 315 専用表）。
     * Keep Alive は varint（316 と同じ）。
     */
    private boolean is315() {
        return clientProtocol == ProtocolVersion.V1_11;
    }

    /** 内部が 1.11（315）か。 */
    private boolean isServer315() {
        return serverProtocol == ProtocolVersion.V1_11;
    }
""")

# ---- 6. 分岐の複製（316 の分岐を写して 315 の分岐を前に足す） ----
# 6a. クライアント→758（サーバー行き）
branch = extract_span(
    "            } else if (is316()) {",
    "\n            } else if (is340() || is338() || is335()) {",
    include_end=False)
insert_before("            } else if (is316()) {", to315(branch) + "\n")

# 6b. クライアント→内部315サーバー
branch = extract_span(
    "        // 内部316：758サーバーと同じ経路",
    "\n        // 内部340／338：758サーバーと同じ経路",
    include_end=False)
insert_before("        // 内部316：758サーバーと同じ経路",
              to315(branch) + "\n")

# 6c. クライアント行き Outcome
branch = extract_span(
    "        if (is316()) {\n"
    "            // 316クライアント：763→762→761→760→759→758→316へ六段で変換する。",
    "            return convertServerPlayOutcomeTo316Slots(to316);\n        }")
insert_before("        if (is316()) {\n"
              "            // 316クライアント：763→762→761→760→759→758→316へ六段で変換する。",
              to315(branch) + "\n")

# 6d. 内部315サーバーからの経路
branch = extract_span(
    "        // 内部316：先にハイブリッド形式（404符号化＋316番号）へ直してから、",
    "            return routeServerPlay316(packet);\n        }")
insert_before("        // 内部316：先にハイブリッド形式（404符号化＋316番号）へ直してから、",
              to315(branch) + "\n")

# ---- 7. convertServerPlayOutcomeTo315 / To315Slots ----
pair = (extract_method(
            "    private Outcome convertServerPlayOutcomeTo316(Outcome outcome) {")
        + "\n"
        + extract_method(
            "    private Outcome convertServerPlayOutcomeTo316Slots(Outcome outcome) {"))
_i = src.index(
    "    private Outcome convertServerPlayOutcomeTo316Slots(Outcome outcome) {")
_end = src.index("\n    }\n", _i) + len("\n    }\n")
src = src[:_end] + "\n    " + to315(pair) + src[_end:]

# ---- 8. 小物ヘルパーの複製 ----
helper = extract_span(
    "    /** 316 の次元に空光があるか（nether=-1 は無し、end=1 は有り）。 */",
    "    private static boolean hasSkyLight316(int dimensionId316) {\n"
    "        return dimensionId316 != -1;\n"
    "    }")
insert_after(helper, "\n\n" + to315(helper))

i = src.index("    private static int readDimension316FromRespawn(byte[] payload316) {")
end = src.index("\n    }\n", i) + len("\n    }\n")
dims = src[src.index(
    "    private static int readDimension316FromJoinGame(byte[] payload316) {"):end]
insert_after(dims, "\n\n" + to315(dims))

# ---- 9. 大規模 switch 5本の 315 版を routeServerPlay316 の直後に生成 ----
parts = []
for sig in (
    "    private List<MinecraftPacket> translateServerPlayPacket758To316(MinecraftPacket packet) {",
    "    private List<MinecraftPacket> to758ServerPackets316(MinecraftPacket packet) {",
    "    private MinecraftPacket to758ClientPacket316(MinecraftPacket packet) {",
    "    private Outcome routeServerPlay316(MinecraftPacket packet) {",
    "    private MinecraftPacket to316ClientPacket758(MinecraftPacket packet) {",
):
    parts.append("    " + to315(extract_method(sig)))
block = "\n" + "\n".join(parts)
route_sig = "    private Outcome routeServerPlay316(MinecraftPacket packet) {"
i = src.index(route_sig)
end = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:end] + "\n" + block + src[end:]

F.write_text(src, encoding="utf-8")
print("ProxySession に 315 の経路を追加した")
