"""ClientAdapter / ClientJoinFlow / ClientProxy に 110 を配線する。

- ClientAdapter：OLDEST を 110 に下げ、supportedClientProtocols()
  には 210（V1_10_2）を明示で残す（最古脱落の罠対策）。decide() の
  受け入れ列挙と説明分岐、versionName()、「古すぎる」文言の最小版。
- ClientJoinFlow：ステージ列の分岐列挙に 110 を追加。
- ClientProxy：コンストラクタ3本の internalProtocol 検証列挙に
  V1_9_4 を追加し、pack110 のフィールドと設定伝播を追加。

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
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_10_2;",
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_9_4;",
             "adapter oldest")
s = sub_once(s,
             """        if (clientProtocol == ProtocolVersion.V1_10_2
                || clientProtocol == ProtocolVersion.V1_11
""",
             """        if (clientProtocol == ProtocolVersion.V1_9_4
                || clientProtocol == ProtocolVersion.V1_10_2
                || clientProtocol == ProtocolVersion.V1_11
""",
             "adapter decide list")
s = sub_once(s,
             """            if (clientProtocol == ProtocolVersion.V1_10_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル210（1.10／1.10.1／1.10.2）のクライアントを変換して受け入れます。"
""",
             """            if (clientProtocol == ProtocolVersion.V1_9_4) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル110（1.9.4／1.9.3）のクライアントを変換して受け入れます。"
                                + "パケットIDは210（1.10）と完全同一で、ワイヤ形式の"
                                + "差分は3件だけ（Sound Effect／Named Sound Effect の"
                                + "pitch が u8、Resource Pack Response の先頭に"
                                + "hash がある）です。"
                                + "レジストリは 1.10 の追加分（magma、nether wart"
                                + "ブロック、骨ブロック、structure void、"
                                + "ホッキョクグマ等）が無く、近似で寄せます"
                                + "（magma はネザーラック、骨ブロックは"
                                + "クォーツブロック、ホッキョクグマはブタ）。"
                                + "Keep Alive は210と同じ varint です。210と"
                                + "同じく、設定フェーズの省略とブロックの代替"
                                + "（意味対応表）が必要です。");
            }
            if (clientProtocol == ProtocolVersion.V1_10_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル210（1.10／1.10.1／1.10.2）のクライアントを変換して受け入れます。"
""",
             "adapter decide branch")
s = sub_once(s,
             '                            + "1.10.2（210）以降のクライアントで参加してください。");',
             '                            + "1.9.4（110）以降のクライアントで参加してください。");',
             "adapter too-old message")
s = sub_once(s,
             """        // OLDEST（210）だけでは315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 315・316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_11,""",
             """        // OLDEST（110）だけでは210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 210・315・316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_10_2, ProtocolVersion.V1_11,""",
             "adapter supported list")
s = sub_once(s,
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_10_2) {
            return "1.10.2";
        }
""",
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_9_4) {
            return "1.9.4";
        }
        if (protocol == ProtocolVersion.V1_10_2) {
            return "1.10.2";
        }
""",
             "adapter versionName")
p.write_text(s, encoding="utf-8")
print("ClientAdapter に 110 を配線した")

# ---- ClientJoinFlow ----
p, s = load("ClientJoinFlow.java")
s = sub_once(s,
             "                || clientProtocol == ProtocolVersion.V1_10_2\n",
             "                || clientProtocol == ProtocolVersion.V1_9_4\n"
             "                || clientProtocol == ProtocolVersion.V1_10_2\n",
             "joinflow stages")
p.write_text(s, encoding="utf-8")
print("ClientJoinFlow に 110 を配線した")

# ---- ClientProxy ----
p, s = load("ClientProxy.java")
s = sub_all(s,
            "        if (internalProtocol != ProtocolVersion.V1_10_2\n",
            "        if (internalProtocol != ProtocolVersion.V1_9_4\n"
            "                && internalProtocol != ProtocolVersion.V1_10_2\n",
            3, "clientproxy validation")
s = sub_once(s,
             "    private ResourcePack pack210;",
             "    private ResourcePack pack110;\n"
             "    private ResourcePack pack210;",
             "clientproxy pack field")
s = sub_once(s,
             """    public void setPack210(ResourcePack pack210) {""",
             """    public void setPack110(ResourcePack pack110) {
        this.pack110 = pack110;
    }

    public ResourcePack pack110() {
        return pack110;
    }

    public void setPack210(ResourcePack pack210) {""",
             "clientproxy pack setter")
s = sub_once(s,
             "                session.setPack210(proxy.pack210);",
             "                session.setPack110(proxy.pack110);\n"
             "                session.setPack210(proxy.pack210);",
             "clientproxy session wiring")
p.write_text(s, encoding="utf-8")
print("ClientProxy に 110 を配線した")
