#!/usr/bin/env python3
"""ClientAdapter.java へ Java 1.21.4（プロトコル769）の受け入れを入れる。

decide／supportedClientProtocols／versionName／differences を769対応にする。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ClientAdapter.java"

EDITS = [
    ("class javadoc clients", """\
 * <p>対応クライアントは 1.20.1（763）と 1.20.2（764）と 1.20.4（765）
 * と 1.20.5／1.20.6（766）と 1.21／1.21.1（767）と 1.21.2／1.21.3（768）。それより古いクライアントは「古すぎる」、""",
     """\
 * <p>対応クライアントは 1.20.1（763）と 1.20.2（764）と 1.20.4（765）
 * と 1.20.5／1.20.6（766）と 1.21／1.21.1（767）と 1.21.2／1.21.3（768）
 * と 1.21.4（769）。それより古いクライアントは「古すぎる」、"""),
    ("class javadoc 769 play", """\
 * 振り分ける。プレイ状態のパケット変換は未実装のものがある
 * （ {@link io.detergentmc.protocol.ProxySession} 参照）。768のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の768対応で実装する。""",
     """\
 * 振り分ける。プレイ状態のパケット変換は未実装のものがある
 * （ {@link io.detergentmc.protocol.ProxySession} 参照）。768のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の768対応で実装する。769のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の769対応で実装する。"""),
    ("decide javadoc 769 entry", """\
     *   <li>768（1.21.2／1.21.3）：変換して受け入れる。設定フェーズは767と同形で中継する。</li>""",
     """\
     *   <li>768（1.21.2／1.21.3）：変換して受け入れる。設定フェーズは767と同形で中継する。</li>
     *   <li>769（1.21.4）：変換して受け入れる。設定フェーズは768と同形で中継する。</li>"""),
    ("decide(int) javadoc too-new", """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>768より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol) {""",
     """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>769より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol) {"""),
    ("decide(int,int) javadoc accepted set", """\
     * 受け入れるクライアントの集合は内部によらず 763・764・765・766・767・768 で、""",
     """\
     * 受け入れるクライアントの集合は内部によらず 763・764・765・766・767・768・769 で、"""),
    ("decide(int,int) javadoc internal 769", """\
     *   <li>内部768＋763クライアント：設定フェーズはプロキシが768クライアント役
     *       として消費する。内部768＋764／765／766／767クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>""",
     """\
     *   <li>内部768＋763クライアント：設定フェーズはプロキシが768クライアント役
     *       として消費する。内部768＋764／765／766／767クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部769＋763クライアント：設定フェーズはプロキシが769クライアント役
     *       として消費する。内部769＋764／765／766／767／768クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>"""),
    ("decide(int,int) javadoc too-new", """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>768より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol, int serverProtocol) {""",
     """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>769より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol, int serverProtocol) {"""),
    ("decide condition", """\
                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2) {
            Direction dir = direction(clientProtocol, serverProtocol);""",
     """\
                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4) {
            Direction dir = direction(clientProtocol, serverProtocol);"""),
    ("decide 769 branch", """\
            if (clientProtocol == ProtocolVersion.V1_21_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル768（1.21.2／1.21.3）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は767⇔768で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル768（1.21.2／1.21.3）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は767⇔768で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }""",
     """\
            if (clientProtocol == ProtocolVersion.V1_21_2) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル768（1.21.2／1.21.3）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は767⇔768で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル768（1.21.2／1.21.3）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は767⇔768で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }
            if (clientProtocol == ProtocolVersion.V1_21_4) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル769（1.21.4）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は768⇔769で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル769（1.21.4）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は768⇔769で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }"""),
    ("too-new message", """\
                        + "このサーバーは1.21.3（768）まで対応しています。");""",
     """\
                        + "このサーバーは1.21.4（769）まで対応しています。");"""),
    ("supportedClientProtocols", """\
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_20_2,
                SERVER_PROTOCOL, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21,
                ProtocolVersion.V1_21_2);""",
     """\
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_20_2,
                SERVER_PROTOCOL, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21,
                ProtocolVersion.V1_21_2, ProtocolVersion.V1_21_4);"""),
    ("versionName", """\
        if (protocol == ProtocolVersion.V1_21_2) {
            return "1.21.2";
        }
        return null;""",
     """\
        if (protocol == ProtocolVersion.V1_21_2) {
            return "1.21.2";
        }
        if (protocol == ProtocolVersion.V1_21_4) {
            return "1.21.4";
        }
        return null;"""),
    ("differences(int) 769 block", """\
                            "プレイ状態の768変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の768対応で実装済み。スロット・"
                                    + "Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは767⇔768で変換する。残る制限：Window Click・"
                                    + "Set Creative Slot は763／764／765向けに落とす。"
                                    + "Declare Recipes・Tags・Statistics・World Event は全方向で"
                                    + "未対応（1.20系と同じ）。"));
        }""",
     """\
                            "プレイ状態の768変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の768対応で実装済み。スロット・"
                                    + "Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは767⇔768で変換する。残る制限：Window Click・"
                                    + "Set Creative Slot は763／764／765向けに落とす。"
                                    + "Declare Recipes・Tags・Statistics・World Event は全方向で"
                                    + "未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_4) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が769と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.3と1.21.4で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は768と同じ形。",
                            Handling.UNAFFECTED,
                            "768と769の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは769にもあり、768と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（バイオーム pale_garden の追加）。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は768と同じ。Registry Data の形式も"
                                    + "768と同じエントリ配列だが、バイオームだけ順序が変わる"
                                    + "（pale_garden が 39 に入り、plains が 39→40 にずれる）。"
                                    + "Custom Report Details・Server Links は768にも769にもある。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が768から変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・パーティクル・バイオーム）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 27,318→27,866件（樹脂・アイブロッサム・"
                                    + "クリエイキングハートの追加と挿入によるずれ）、アイテムは"
                                    + " 1,375→1,385件（樹脂のレンガなど）、エンティティは 150→149件"
                                    + "（creaking_transient の削除）、音は 1,636→1,651件、"
                                    + "パーティクルは 111→112件（pale_oak_leaves の追加）。"
                                    + "バイオームに pale_garden が追加された。"
                                    + "属性・エンチャント・データコンポーネント種別は変わらない"
                                    + "（RegistryRemapper の769対応表）。ダメージ種別・絵画・"
                                    + "旗の模様・トリム・楽器・ジュークボックスの歌は変わらない。"),
                    new Difference("PLAY",
                            "769で新規のパケットが3件あり、既存パケットのIDも24件変わる"
                                    + "（すべてサーバー行き）。Vehicle Move・World Particles・"
                                    + "Player Info の形式も変わった。",
                            Handling.TRANSLATED,
                            "新規は Pick Item From Block／Entity と Player Loaded、削除は"
                                    + " Pick Item。Vehicle Move の末尾に onGround、World Particles"
                                    + " に alwaysShow、Player Info に showHat が付いた。"
                                    + "プレイ状態の769変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の769対応で実装済み。スロット・"
                                    + "Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは768⇔769で変換する。残る制限：Pick Item は"
                                    + "両方向とも落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }"""),
    ("differences(int,int) server condition", """\
        if (serverProtocol == ProtocolVersion.V1_20_1
                || serverProtocol == ProtocolVersion.V1_20_5
                || serverProtocol == ProtocolVersion.V1_21
                || serverProtocol == ProtocolVersion.V1_21_2) {
            // 内部763・766・767・768の差の一覧は、対応表・レジストリ表の766／767／768対応と
            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの
            // 扱いだけは決まっている（内部763は合成、内部766・767・768は消費・中継）。""",
     """\
        if (serverProtocol == ProtocolVersion.V1_20_1
                || serverProtocol == ProtocolVersion.V1_20_5
                || serverProtocol == ProtocolVersion.V1_21
                || serverProtocol == ProtocolVersion.V1_21_2
                || serverProtocol == ProtocolVersion.V1_21_4) {
            // 内部763・766・767・768・769の差の一覧は、対応表・レジストリ表の766／767／768／769対応と
            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの
            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769は消費・中継）。"""),
    ("differences(int,int) client check", """\
            if (clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2) {""",
     """\
            if (clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4) {"""),
    ("differences(int,int) internal 769 config", """\
                                            : serverProtocol == ProtocolVersion.V1_21_2
                                                    ? "内部768の設定フェーズは、763クライアントにはプロキシが"
                                                            + "768クライアント役として消費し、764／765／766／767クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "768の新規パケット（Recipe Book 系など）は"
                                                            + "古いクライアントには落とす。"
""",
     """\
                                            : serverProtocol == ProtocolVersion.V1_21_4
                                                    ? "内部769の設定フェーズは、763クライアントにはプロキシが"
                                                            + "769クライアント役として消費し、764／765／766／767／768クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "769の新規パケット（Pick Item From Block／Entity・"
                                                            + "Player Loaded）は古いクライアントには落とす。"
                                            : serverProtocol == ProtocolVersion.V1_21_2
                                                    ? "内部768の設定フェーズは、763クライアントにはプロキシが"
                                                            + "768クライアント役として消費し、764／765／766／767クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "768の新規パケット（Recipe Book 系など）は"
                                                            + "古いクライアントには落とす。"
"""),
    ("differences(int,int) PLAY", """\
                            "内部" + serverProtocol + "とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、対応表（PacketIds・"
                                    + "RegistryRemapper）の767／768対応で実装済み。スロットは"
                                    + "766⇔767⇔768で変換し、763／764／765向けの Window Click・\"""",
     """\
                            "内部" + serverProtocol + "とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、対応表（PacketIds・"
                                    + "RegistryRemapper）の767／768／769対応で実装済み。スロットは"
                                    + "766⇔767⇔768⇔769で変換し、763／764／765向けの Window Click・\""""),
    ("differences(int,int) 766/767/768 client branch", """\
        if (clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2) {""",
     """\
        if (clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4) {"""),
    ("differences(int,int) pack_format", """\
                                    + (clientProtocol == ProtocolVersion.V1_21_2 ? "42"
                                            : clientProtocol == ProtocolVersion.V1_21 ? "34" : "32")""",
     """\
                                    + (clientProtocol == ProtocolVersion.V1_21_4 ? "46"
                                            : clientProtocol == ProtocolVersion.V1_21_2 ? "42"
                                            : clientProtocol == ProtocolVersion.V1_21 ? "34" : "32")"""),
]


def main():
    src = PATH.read_text(encoding="utf-8")
    for name, old, new in EDITS:
        n = src.count(old)
        if n != 1:
            print(f"NG: {name}: anchor が {n} 回見つかった（1回のはず）", file=sys.stderr)
            sys.exit(1)
        src = src.replace(old, new, 1)
        print(f"OK: {name}")
    PATH.write_text(src, encoding="utf-8")
    print("ClientAdapter.java に 769 の対応を挿入した")


if __name__ == "__main__":
    main()
