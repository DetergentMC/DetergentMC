"""ClientAdapter / ClientJoinFlow / ClientProxy に 210 を配線する。

- ClientAdapter：OLDEST を 210 に下げ、supportedClientProtocols()
  には 315（V1_11）を明示で残す（最古脱落の罠対策）。decide() の
  受け入れ列挙と説明分岐、versionName()、「古すぎる」文言の最小版。
- ClientJoinFlow：ステージ列の分岐列挙に 210 を追加。
- ClientProxy：コンストラクタ3本の internalProtocol 検証列挙に
  V1_10_2 を追加し、pack210 のフィールドと設定伝播を追加。

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
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_11;",
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_10_2;",
             "adapter oldest")
s = sub_once(s,
             """        if (clientProtocol == ProtocolVersion.V1_11
                || clientProtocol == ProtocolVersion.V1_11_2
""",
             """        if (clientProtocol == ProtocolVersion.V1_10_2
                || clientProtocol == ProtocolVersion.V1_11
                || clientProtocol == ProtocolVersion.V1_11_2
""",
             "adapter decide list")
s = sub_once(s,
             """            if (clientProtocol == ProtocolVersion.V1_11) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル315（1.11）のクライアントを変換して受け入れます。"
""",
             """            if (clientProtocol == ProtocolVersion.V1_10_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル210（1.10／1.10.1／1.10.2）のクライアントを変換して受け入れます。"
                                + "パケットIDは315（1.11）と完全同一で、ワイヤ形式の"
                                + "差分は4件だけ（Spawn Entity Living の種別が u8、"
                                + "Title の action 番号、Collect の個数フィールド"
                                + "欠落、Block Placement のカーソルが i8）です。"
                                + "レジストリは 1.11 の追加分（observer、シュルカー"
                                + "ボックス、totem 等）が無く、近似で寄せます"
                                + "（observer はドロッパー、シュルカーはチェスト、"
                                + "totem は金のリンゴ）。Keep Alive は315と同じ "
                                + "varint です。315と同じく、設定フェーズの省略と"
                                + "ブロックの代替（意味対応表）が必要です。");
            }
            if (clientProtocol == ProtocolVersion.V1_11) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル315（1.11）のクライアントを変換して受け入れます。"
""",
             "adapter decide branch")
s = sub_once(s,
             '                            + "1.11（315）以降のクライアントで参加してください。");',
             '                            + "1.10.2（210）以降のクライアントで参加してください。");',
             "adapter too-old message")
s = sub_once(s,
             """        // OLDEST（315）だけでは316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_11_2,""",
             """        // OLDEST（210）だけでは315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 315・316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_11, ProtocolVersion.V1_11_2,""",
             "adapter supported list")
s = sub_once(s,
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_11) {
            return "1.11";
        }
""",
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_10_2) {
            return "1.10.2";
        }
        if (protocol == ProtocolVersion.V1_11) {
            return "1.11";
        }
""",
             "adapter versionName")
p.write_text(s, encoding="utf-8")
print("ClientAdapter に 210 を配線した")

# ---- ClientJoinFlow ----
p, s = load("ClientJoinFlow.java")
s = sub_once(s,
             "                || clientProtocol == ProtocolVersion.V1_11\n",
             "                || clientProtocol == ProtocolVersion.V1_10_2\n"
             "                || clientProtocol == ProtocolVersion.V1_11\n",
             "joinflow stages")
p.write_text(s, encoding="utf-8")
print("ClientJoinFlow に 210 を配線した")

# ---- ClientProxy ----
p, s = load("ClientProxy.java")
s = sub_all(s,
            "        if (internalProtocol != ProtocolVersion.V1_11\n",
            "        if (internalProtocol != ProtocolVersion.V1_10_2\n"
            "                && internalProtocol != ProtocolVersion.V1_11\n",
            3, "clientproxy validation")
s = sub_once(s,
             "    private ResourcePack pack315;",
             "    private ResourcePack pack210;\n"
             "    private ResourcePack pack315;",
             "clientproxy pack field")
s = sub_once(s,
             """    public void setPack315(ResourcePack pack315) {""",
             """    public void setPack210(ResourcePack pack210) {
        this.pack210 = pack210;
    }

    public ResourcePack pack210() {
        return pack210;
    }

    public void setPack315(ResourcePack pack315) {""",
             "clientproxy pack setter")
s = sub_once(s,
             "                session.setPack315(proxy.pack315);",
             "                session.setPack210(proxy.pack210);\n"
             "                session.setPack315(proxy.pack315);",
             "clientproxy session wiring")
p.write_text(s, encoding="utf-8")
print("ClientProxy に 210 を配線した")
