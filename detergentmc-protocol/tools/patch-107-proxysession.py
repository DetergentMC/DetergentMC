#!/usr/bin/env python3
"""ProxySession に 107（1.9）の経路を追加する。

107 は Join Game の dimension が i8 である点を除き 108 と同一のため、
108 の分岐・メソッドを写して番号を差し替える。唯一の手当て：
readDimension107FromJoinGame は dimension を readByte で読む
（108 の写しのままだと readInt になり壊れる）。
"""
import pathlib

PATH = (pathlib.Path(__file__).resolve().parent.parent
        / "src/main/java/io/detergentmc/protocol/ProxySession.java")
src = PATH.read_text(encoding="utf-8")


def sub1(old: str, new: str) -> None:
    global src
    assert src.count(old) == 1, f"出現回数が1でない: {old!r} -> {src.count(old)}"
    src = src.replace(old, new, 1)


def sub_all(old: str, new: str, count: int) -> None:
    global src
    assert src.count(old) == count, \
        f"出現回数が {count} でない: {old!r} -> {src.count(old)}"
    src = src.replace(old, new)


def to107(text: str) -> str:
    text = text.replace("V1_9_1", "V1_9")
    text = text.replace("1.9.1", "1.9")
    return text.replace("108", "107")


# 1. 列挙への追加（is108() の隣に is107() を足す）
sub_all("is108() ||", "is108() || is107() ||", 4)
sub_all("|| is108())", "|| is108() || is107())", 16)
sub_all("|| isServer108())", "|| isServer108() || isServer107())", 7)
sub_all("|| isServer108()\n", "|| isServer108() || isServer107()\n", 1)

# 2. packFor763LikeClient：先頭分岐（外側条件は手順1の列挙追加で
#    is108() || is107() || … の形に既に 107 が入っている）
sub1("""            if (is108()) {
                if (pack108 != null) {""",
     """            if (is107()) {
                if (pack107 != null) {
                    return pack107;
                }
            }
            if (is108() || is107()) {
                if (pack108 != null) {""")

# 3. dimension107Client / dimension107Server フィールド
sub1("""    /** 108クライアント向け変換で使う現在のディメンションID（108形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension108Client;""",
     """    /** 107クライアント向け変換で使う現在のディメンションID（107形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension107Client;

    /** 107サーバー向け変換で使う現在のディメンションID（107形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension107Server;

    /** 108クライアント向け変換で使う現在のディメンションID（108形式。0=オーバーワールド、-1=ネザー）。空光の有無に使う。 */
    private int dimension108Client;""")

# 4. Keep Alive 復元表（107）
sub1("""    private final java.util.Map<Integer, Long> keepAliveIds108ToClient =
            new java.util.HashMap<>();""",
     """    private final java.util.Map<Integer, Long> keepAliveIds108ToClient =
            new java.util.HashMap<>();

    /**
     * 107クライアントへ送った Keep Alive の、切り捨て前の i64 ID
     * （キー＝varint に切り捨てた int 値）。108 の復元表と同じ仕組み。
     */
    private final java.util.Map<Integer, Long> keepAliveIds107ToClient =
            new java.util.HashMap<>();""")

# 5. pack107 フィールド
sub1("""    /**
     * 108クライアントに送るリソースパック（pack_format 2、旧レイアウト）。""",
     """    /**
     * 107クライアントに送るリソースパック（pack_format 2、旧レイアウト）。
     * {@code null} の場合は108用パックにフォールバックする
     * （形式は pack_format 2 で、108 用とは別に用意する）。
     */
    private ResourcePack pack107;

    /**
     * 108クライアントに送るリソースパック（pack_format 2、旧レイアウト）。""")

# 6. コンストラクタの serverProtocol 検証に V1_9 を追加
sub1("        if (serverProtocol != ProtocolVersion.V1_9_1\n",
     "        if (serverProtocol != ProtocolVersion.V1_9\n"
     "                && serverProtocol != ProtocolVersion.V1_9_1\n")

# 7. setPack107 / pack107()
sub1("    /** 108クライアントに送るリソースパックを設定する（無ければ null）。 */",
     """    /** 107クライアントに送るリソースパックを設定する（無ければ null）。 */
    public void setPack107(ResourcePack pack107) {
        this.pack107 = pack107;
    }

    /** 107クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack107() {
        return pack107;
    }

    /** 108クライアントに送るリソースパックを設定する（無ければ null）。 */""")

# 8. is107() / isServer107()
sub1("""    /** 内部が 1.9.1（108）か。 */
    private boolean isServer108() {
        return serverProtocol == ProtocolVersion.V1_9_1;
    }
""",
     """    /** 内部が 1.9.1（108）か。 */
    private boolean isServer108() {
        return serverProtocol == ProtocolVersion.V1_9_1;
    }

    /**
     * クライアントが 1.9（107）か。ワイヤ形式は Join Game の
     * dimension が i8（108 以降は i32）である点を除き 108 と完全同一
     * （Stage A 実測：protocol.json 全面比較＋実バニラ 1.9 の
     * ワイヤ採取）。Keep Alive は varint（108 と同じ）。107 固有の
     * 癖として、107 バックエンドの Chunk Data は dataLen に大きな
     * ゼロ埋めを含むが、チャンク変換器は末尾一致を要求しないため
     * そのまま扱える。
     */
    private boolean is107() {
        return clientProtocol == ProtocolVersion.V1_9;
    }

    /** 内部が 1.9（107）か。 */
    private boolean isServer107() {
        return serverProtocol == ProtocolVersion.V1_9;
    }
""")

# 9a. クライアント→758 分岐（サーバーバウンド）の複写
i = src.index("            } else if (is108()) {")
j = src.index("\n            } else if (is109()) {", i)
src = src[:i] + to107(src[i:j]) + "\n" + src[i:]
print("9a: クライアント→758 分岐を複写")

# 9b. クライアント→内部サーバー 分岐の複写
i = src.index("        // 内部108：758サーバーと同じ経路")
j = src.index("\n        // 内部109：758サーバーと同じ経路", i)
src = src[:i] + to107(src[i:j]) + "\n" + src[i:]
print("9b: クライアント→内部サーバー分岐を複写")

# 9c. Outcome 分岐（サーバー→クライアント）の複写
i = src.index("        if (is108()) {\n            // 108クライアント：")
j = src.index("            return convertServerPlayOutcomeTo108Slots(to108);\n        }\n", i)
j += len("            return convertServerPlayOutcomeTo108Slots(to108);\n        }\n")
src = src[:i] + to107(src[i:j]) + src[i:]
print("9c: Outcome 分岐を複写")

# 9d. 内部サーバー経路 分岐の複写
i = src.index("        // 内部108：先にハイブリッド形式")
j = src.index("        return routeServerPlay108(packet);\n        }\n", i)
j += len("        return routeServerPlay108(packet);\n        }\n")
src = src[:i] + to107(src[i:j]) + src[i:]
print("9d: 内部サーバー経路分岐を複写")

# 10. convertServerPlayOutcomeTo107 / ...Slots の複写
i = src.index("    private Outcome convertServerPlayOutcomeTo108(Outcome outcome) {")
j = src.index("keepAliveIds108ToClient);", i)
j = src.index("\n    }\n", j) + len("\n    }\n")
src = src[:j] + "\n" + to107(src[i:j]) + src[j:]
print("10: convertServerPlayOutcomeTo107 ペアを複写")

# 11a. hasSkyLight107 の複写
i = src.index("    private static boolean hasSkyLight108(int dimensionId108) {")
j = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:j] + "\n" + to107(src[i:j]) + src[j:]
print("11a: hasSkyLight107 を複写")

# 11b. readDimension107 ペアの複写＋Join Game 側の i8 手当て
i = src.index("    private static int readDimension108FromJoinGame")
j = src.index("    private static int readDimension108FromRespawn", i)
j = src.index("\n    }\n", j) + len("\n    }\n")
dims107 = to107(src[i:j])
old_dim = """    private static int readDimension107FromJoinGame(byte[] payload107) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload107);
            reader.readInt(); // entityId
            reader.readByte(); // gamemode（hardcore ビット含む）
            return reader.readInt(); // dimension"""
new_dim = """    private static int readDimension107FromJoinGame(byte[] payload107) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload107);
            reader.readInt(); // entityId
            reader.readByte(); // gamemode（hardcore ビット含む）
            return reader.readByte(); // dimension（107 は i8。108 以降は i32）"""
assert dims107.count(old_dim) == 1, "readDimension107 の手当てアンカーが一致しない"
dims107 = dims107.replace(old_dim, new_dim, 1)
src = src[:j] + "\n" + dims107 + src[j:]
print("11b: readDimension107 ペアを複写（Join Game は i8 読みに手当て）")

# 12. リソースパック hash 付け替えヘルパーの複写
i = src.index("    private static byte[] prependResourcePackHash108(byte[] payload) {")
j = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:j] + "\n" + to107(src[i:j]) + src[j:]
i = src.index("    private static byte[] stripResourcePackHash108(byte[] payload) {")
j = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:j] + "\n" + to107(src[i:j]) + src[j:]
print("12: リソースパック hash ヘルパーを複写")

# 13. 大スイッチ5本の複写（758→107 / 107→758 / 経路）
#     108 の5本は連続配置のため一括で写し、routeServerPlay108 の
#     定義の直後へ挿入する（当初は分岐の間へ誤挿入したため
#     tools/fix-107-switch-placement.py で移動した）。
i = src.index("    private List<MinecraftPacket> translateServerPlayPacket758To108")
k = src.index("    private MinecraftPacket to108ClientPacket758(MinecraftPacket packet) {", i)
j = src.index("\n        };\n    }\n", k) + len("\n        };\n    }\n")
block = to107(src[i:j])
sig = "    private Outcome routeServerPlay108(MinecraftPacket packet) {"
i = src.index(sig)
end = src.index("\n    }\n", i) + len("\n    }\n")
src = src[:end] + "\n" + block + "\n" + src[end:]
print("13: 大スイッチ5本を複写")

PATH.write_text(src, encoding="utf-8")
print("ProxySession: 107 経路を追加")
