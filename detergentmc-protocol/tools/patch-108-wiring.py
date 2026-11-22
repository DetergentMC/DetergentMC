"""ClientAdapter / ClientJoinFlow / ClientProxy に 108 を配線する。

- ClientAdapter：OLDEST を 108 に下げ、supportedClientProtocols()
  には 109（V1_9_2）を明示で残す（最古脱落の罠対策）。decide() の
  受け入れ列挙と説明分岐、versionName()、「古すぎる」文言の最小版。
- ClientJoinFlow：ステージ列の分岐列挙に 108 を追加。
- ClientProxy：コンストラクタ3本の internalProtocol 検証列挙に
  V1_9_1 を追加し、pack108 のフィールドと設定伝播を追加。

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
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_9_2;",
             "    public static final int OLDEST_SUPPORTED_CLIENT_PROTOCOL = ProtocolVersion.V1_9_1;",
             "adapter oldest")
s = sub_once(s,
             """        if (clientProtocol == ProtocolVersion.V1_9_2
                || clientProtocol == ProtocolVersion.V1_9_4
""",
             """        if (clientProtocol == ProtocolVersion.V1_9_1
                || clientProtocol == ProtocolVersion.V1_9_2
                || clientProtocol == ProtocolVersion.V1_9_4
""",
             "adapter decide list")
s = sub_once(s,
             """            if (clientProtocol == ProtocolVersion.V1_9_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル109（1.9.2）のクライアントを変換して受け入れます。"
""",
             """            if (clientProtocol == ProtocolVersion.V1_9_1) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル108（1.9.1）のクライアントを変換して受け入れます。"
                                + "レジストリ・ワイヤ形式とも109（1.9.2）と完全同一です"
                                + "（両 jar の全面比較とワイヤ採取で確認済み）。"
                                + "109と同じく Chunk Data に blockEntities フィールドが"
                                + "無く（ブロックエンティティはチャンクでは運ばれません）、"
                                + "近似も109と同じ（magma はネザーラック、骨ブロックは"
                                + "クォーツブロック、ホッキョクグマはブタ）。"
                                + "Keep Alive は109と同じ varint です。109と"
                                + "同じく、設定フェーズの省略とブロックの代替"
                                + "（意味対応表）が必要です。");
            }
            if (clientProtocol == ProtocolVersion.V1_9_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        "プロトコル109（1.9.2）のクライアントを変換して受け入れます。"
""",
             "adapter decide branch")
s = sub_once(s,
             '                            + "1.9.2（109）以降のクライアントで参加してください。");',
             '                            + "1.9.1（108）以降のクライアントで参加してください。");',
             "adapter too-old message")
s = sub_once(s,
             """        // OLDEST（109）だけでは110・210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、110・210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 110・210・315・316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_9_4,""",
             """        // OLDEST（108）だけでは109・110・210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760が漏れるため、109・110・210・315・316・335・338・340・393・401・404・477・480・485・490・498・573・575・578・758・759・760は明示する。
        // 109・110・210・315・316・335・338・340・393・401・404・477・573・575・578・758・759・760をここから外すと ClientJoinFlow がそのクライアントを拒否する。
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_9_2, ProtocolVersion.V1_9_4,""",
             "adapter supported list")
s = sub_once(s,
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_9_2) {
            return "1.9.2";
        }
""",
             """    public static String versionName(int protocol) {
        if (protocol == ProtocolVersion.V1_9_1) {
            return "1.9.1";
        }
        if (protocol == ProtocolVersion.V1_9_2) {
            return "1.9.2";
        }
""",
             "adapter versionName")
p.write_text(s, encoding="utf-8")
print("ClientAdapter に 108 を配線した")

# ---- ClientJoinFlow ----
p, s = load("ClientJoinFlow.java")
s = sub_once(s,
             "                || clientProtocol == ProtocolVersion.V1_9_2\n",
             "                || clientProtocol == ProtocolVersion.V1_9_1\n"
             "                || clientProtocol == ProtocolVersion.V1_9_2\n",
             "joinflow stages")
p.write_text(s, encoding="utf-8")
print("ClientJoinFlow に 108 を配線した")

# ---- ClientProxy ----
p, s = load("ClientProxy.java")
s = sub_all(s,
            "        if (internalProtocol != ProtocolVersion.V1_9_2\n",
            "        if (internalProtocol != ProtocolVersion.V1_9_1\n"
            "                && internalProtocol != ProtocolVersion.V1_9_2\n",
            3, "clientproxy validation")
s = sub_once(s,
             "    private ResourcePack pack109;",
             "    private ResourcePack pack108;\n"
             "    private ResourcePack pack109;",
             "clientproxy pack field")
s = sub_once(s,
             """    public void setPack109(ResourcePack pack109) {""",
             """    public void setPack108(ResourcePack pack108) {
        this.pack108 = pack108;
    }

    public ResourcePack pack108() {
        return pack108;
    }

    public void setPack109(ResourcePack pack109) {""",
             "clientproxy pack setter")
s = sub_once(s,
             "                session.setPack109(proxy.pack109);",
             "                session.setPack108(proxy.pack108);\n"
             "                session.setPack109(proxy.pack109);",
             "clientproxy session wiring")
p.write_text(s, encoding="utf-8")
print("ClientProxy に 108 を配線した")
