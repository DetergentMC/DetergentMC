"""ClientAdapter / ClientJoinFlow / ClientProxy に 109 を配線する。

- ClientAdapter：OLDEST を 109 に下げ、supportedClientProtocols()
  には 110（V1_9_4）を明示で残す（最古脱落の罠対策）。decide() の
  受け入れ列挙と説明分岐、versionName()、「古すぎる」文言の最小版。
- ClientJoinFlow：ステージ列の分岐列挙に 109 を追加。
- ClientProxy：コンストラクタ3本の internalProtocol 検証列挙に
  V1_9_2 を追加し、pack109 のフィールドと設定伝播を追加。

冪等ではない。git diff で確認すること。
"""

from pathlib import Path

BASE = (Path(__file__).parent.parent / "src" / "main" / "java"
        / "io" / "detergentmc" / "protocol")


def load(name):
    p = BASE / name
    return p, p.read_text(encoding="utf-8")


def sub_once(src, old, new, where):
    assert src.count(old) == 1, f"{where}: 一意でない({src.count(old)}): {old[:60]}"
    return src.replace(old, new)


def sub_all(src, old, new, expect, where):
    n = src.count(old)
    assert n == expect, f"{where}: 件数不一致({n} != {expect}): {old[:60]}"
    return src.replace(old, new)


# ---- ClientAdapter ----
p, s = load("ClientAdapter.java")
s = sub_once(s,
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_9_4;",
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_9_2;",
             "adapter oldest")
s = sub_once(s,
             """        if (clientProtocol == ProtocolVersion.V1_9_4
                || clientProtocol == ProtocolVersion.V1_10_2
""",
             """        if (clientProtocol == ProtocolVersion.V1_9_2
                || clientProtocol == ProtocolVersion.V1_9_4
                || clientProtocol == ProtocolVersion.V1_10_2
""",
             "adapter decide list")
s = sub_once(s,
             """            if (clientProtocol == ProtocolVersion.V1_9_4) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル110（1.9.4／1.9.3）のクライアントを変換して受け入れます。"
""",
             """            if (clientProtocol == ProtocolVersion.V1_9_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル109（1.9.2）のクライアントを変換して受け入れます。"
                                + "レジストリは110（1.9.4）と完全同一で、ワイヤ形式の"
                                + "差分は Chunk Data に blockEntities フィールドが"
                                + "無いことの1件だけです（ブロックエンティティは"
                                + "チャンクでは運ばれません）。パケットIDは"
                                + "update_sign（クライアント行き）が109にだけあり、"
                                + "後続6件が110より +1 ずれます。"
                                + "近似は110と同じ（magma はネザーラック、骨ブロックは"
                                + "クォーツブロック、ホッキョクグマはブタ）。"
                                + "Keep Alive は110と同じ varint です。110と"
                                + "同じく、設定フェーズの省略とブロックの代替"
                                + "（意味対応表）が必要です。");
            }
            if (clientProtocol == ProtocolVersion.V1_9_4) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル110（1.9.4／1.9.3）のクライアントを変換して受け入れます。"
""",
             "adapter decide branch")
s = sub_once(s,
             '                            + "1.9.4（110）以降のクライアントで参加してください。");',
             '                            + "1.9.2（109）以降のクライアントで参加してください。");',
             "adapter too-old message")
s = sub_once(s,
             """        // OLDEST（110）だけでは210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 210・315・316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_10_2,""",
             """        // OLDEST（109）だけでは110・210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、110・210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 110・210・315・316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_9_4, ProtocolVersion.V1_10_2,""",
             "adapter supported list")
s = sub_once(s,
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_9_4) {
            return "1.9.4";
        }
""",
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_9_2) {
            return "1.9.2";
        }
        if (protocol == ProtocolVersion.V1_9_4) {
            return "1.9.4";
        }
""",
             "adapter versionName")
p.write_text(s, encoding="utf-8")
print("ClientAdapter に 109 を配線した")

# ---- ClientJoinFlow ----
p, s = load("ClientJoinFlow.java")
s = sub_once(s,
             "                || clientProtocol == ProtocolVersion.V1_9_4\n",
             "                || clientProtocol == ProtocolVersion.V1_9_2\n"
             "                || clientProtocol == ProtocolVersion.V1_9_4\n",
             "joinflow stages")
p.write_text(s, encoding="utf-8")
print("ClientJoinFlow に 109 を配線した")

# ---- ClientProxy ----
p, s = load("ClientProxy.java")
s = sub_all(s,
            "        if (internalProtocol != ProtocolVersion.V1_9_4\n",
            "        if (internalProtocol != ProtocolVersion.V1_9_2\n"
            "                && internalProtocol != ProtocolVersion.V1_9_4\n",
            3, "clientproxy validation")
s = sub_once(s,
             "    private ResourcePack pack110;",
             "    private ResourcePack pack109;\n"
             "    private ResourcePack pack110;",
             "clientproxy pack field")
s = sub_once(s,
             """    public void setPack110(ResourcePack pack110) {""",
             """    public void setPack109(ResourcePack pack109) {
        this.pack109 = pack109;
    }

    public ResourcePack pack109() {
        return pack109;
    }

    public void setPack110(ResourcePack pack110) {""",
             "clientproxy pack setter")
s = sub_once(s,
             "                session.setPack110(proxy.pack110);",
             "                session.setPack109(proxy.pack109);\n"
             "                session.setPack110(proxy.pack110);",
             "clientproxy session wiring")
p.write_text(s, encoding="utf-8")
print("ClientProxy に 109 を配線した")
