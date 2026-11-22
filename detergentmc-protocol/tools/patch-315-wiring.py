"""ClientAdapter / ClientJoinFlow / ClientProxy に 315 を配線する。

- ClientAdapter：OLDEST を 315 に下げ、supportedClientProtocols()
  には 316（V1_11_2）を明示で残す（最古脱落の罠対策）。decide() の
  受け入れ列挙と説明分岐、versionName()、「古すぎる」文言の最小版。
- ClientJoinFlow：ステージ列の分岐列挙に 315 を追加。
- ClientProxy：コンストラクタ3本の internalProtocol 検証列挙に
  V1_11 を追加し、pack315 のフィールドと設定伝播を追加。

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
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_11_2;",
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_11;",
             "adapter oldest")
s = sub_once(s,
             """        if (clientProtocol == ProtocolVersion.V1_11_2
                || clientProtocol == ProtocolVersion.V1_12
""",
             """        if (clientProtocol == ProtocolVersion.V1_11
                || clientProtocol == ProtocolVersion.V1_11_2
                || clientProtocol == ProtocolVersion.V1_12
""",
             "adapter decide list")
s = sub_once(s,
             """            if (clientProtocol == ProtocolVersion.V1_11_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル316（1.11.2）のクライアントを変換して受け入れます。"
""",
             """            if (clientProtocol == ProtocolVersion.V1_11) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル315（1.11）のクライアントを変換して受け入れます。"
                                + "ワイヤ形式・パケットIDは316（1.11.1／1.11.2）と"
                                + "完全同一で、レジストリ差分はアイテム1件だけ"
                                + "（iron_nugget は 1.11.1 で追加されたため315には"
                                + "無く、金塊で寄せます。totem の改名は数値ID不変の"
                                + "ため無関係）です。そのため変換経路は316の経路を"
                                + "写したものを使い、アイテム写像だけ315専用表を"
                                + "通します。Keep Alive は316と同じ varint です。"
                                + "316と同じく、設定フェーズの省略とブロックの"
                                + "代替（意味対応表）が必要です。");
            }
            if (clientProtocol == ProtocolVersion.V1_11_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル316（1.11.2）のクライアントを変換して受け入れます。"
""",
             "adapter decide branch")
s = sub_once(s,
             '                            + "1.12.1（338）以降のクライアントで参加してください。");',
             '                            + "1.11（315）以降のクライアントで参加してください。");',
             "adapter too-old message")
s = sub_once(s,
             """        // OLDEST（316）だけでは335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_12,""",
             """        // OLDEST（315）だけでは316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_11_2, ProtocolVersion.V1_12,""",
             "adapter supported list")
s = sub_once(s,
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_11_2) {
            return "1.11.2";
        }
""",
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_11) {
            return "1.11";
        }
        if (protocol == ProtocolVersion.V1_11_2) {
            return "1.11.2";
        }
""",
             "adapter versionName")
p.write_text(s, encoding="utf-8")
print("ClientAdapter に 315 を配線した")

# ---- ClientJoinFlow ----
p, s = load("ClientJoinFlow.java")
s = sub_once(s,
             "                || clientProtocol == ProtocolVersion.V1_11_2\n",
             "                || clientProtocol == ProtocolVersion.V1_11\n"
             "                || clientProtocol == ProtocolVersion.V1_11_2\n",
             "joinflow stages")
p.write_text(s, encoding="utf-8")
print("ClientJoinFlow に 315 を配線した")

# ---- ClientProxy ----
p, s = load("ClientProxy.java")
s = sub_all(s,
            "        if (internalProtocol != ProtocolVersion.V1_11_2\n",
            "        if (internalProtocol != ProtocolVersion.V1_11\n"
            "                && internalProtocol != ProtocolVersion.V1_11_2\n",
            3, "clientproxy validation")
s = sub_once(s,
             "    private ResourcePack pack316;",
             "    private ResourcePack pack315;\n"
             "    private ResourcePack pack316;",
             "clientproxy pack field")
s = sub_once(s,
             """    public void setPack316(ResourcePack pack316) {""",
             """    public void setPack315(ResourcePack pack315) {
        this.pack315 = pack315;
    }

    public ResourcePack pack315() {
        return pack315;
    }

    public void setPack316(ResourcePack pack316) {""",
             "clientproxy pack setter")
s = sub_once(s,
             "                session.setPack316(proxy.pack316);",
             "                session.setPack315(proxy.pack315);\n"
             "                session.setPack316(proxy.pack316);",
             "clientproxy session wiring")
p.write_text(s, encoding="utf-8")
print("ClientProxy に 315 を配線した")
