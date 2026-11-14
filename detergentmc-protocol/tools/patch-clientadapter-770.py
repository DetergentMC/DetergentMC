#!/usr/bin/env python3
"""ClientAdapter.java へ Java 1.21.5（プロトコル770）の受け入れを入れる。

decide／supportedClientProtocols／versionName／differences を770対応にする。
各アンカーはファイル中にちょうど1回だけ現れること。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ClientAdapter.java"

EDITS = [
    ("class javadoc clients", """\
 * <p>対応クライアントは 1.20.1（763）と 1.20.2（764）と 1.20.4（765）
 * と 1.20.5／1.20.6（766）と 1.21／1.21.1（767）と 1.21.2／1.21.3（768）
 * と 1.21.4（769）。それより古いクライアントは「古すぎる」、""",
     """\
 * <p>対応クライアントは 1.20.1（763）と 1.20.2（764）と 1.20.4（765）
 * と 1.20.5／1.20.6（766）と 1.21／1.21.1（767）と 1.21.2／1.21.3（768）
 * と 1.21.4（769）と 1.21.5（770）。それより古いクライアントは「古すぎる」、"""),
    ("class javadoc 770 play", """\
 * 振り分ける。プレイ状態のパケット変換は未実装のものがある
 * （ {@link io.detergentmc.protocol.ProxySession} 参照）。768のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の768対応で実装する。769のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の769対応で実装する。""",
     """\
 * 振り分ける。プレイ状態のパケット変換は未実装のものがある
 * （ {@link io.detergentmc.protocol.ProxySession} 参照）。768のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の768対応で実装する。769のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の769対応で実装する。770のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の770対応で実装する。"""),
    ("decide javadoc 770 entry", """\
     *   <li>769（1.21.4）：変換して受け入れる。設定フェーズは768と同形で中継する。</li>""",
     """\
     *   <li>769（1.21.4）：変換して受け入れる。設定フェーズは768と同形で中継する。</li>
     *   <li>770（1.21.5）：変換して受け入れる。設定フェーズは769と同形で中継する。</li>"""),
    ("decide(int) javadoc too-new", """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>769より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol) {""",
     """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>770より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol) {"""),
    ("decide(int,int) javadoc accepted set", """\
     * 受け入れるクライアントの集合は内部によらず 763・764・765・766・767・768・769 で、""",
     """\
     * 受け入れるクライアントの集合は内部によらず 763・764・765・766・767・768・769・770 で、"""),
    ("decide(int,int) javadoc internal 770", """\
     *   <li>内部769＋763クライアント：設定フェーズはプロキシが769クライアント役
     *       として消費する。内部769＋764／765／766／767／768クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>""",
     """\
     *   <li>内部769＋763クライアント：設定フェーズはプロキシが769クライアント役
     *       として消費する。内部769＋764／765／766／767／768クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部770＋763クライアント：設定フェーズはプロキシが770クライアント役
     *       として消費する。内部770＋764／765／766／767／768／769クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>"""),
    ("decide(int,int) javadoc too-new", """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>769より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol, int serverProtocol) {""",
     """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>770より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol, int serverProtocol) {"""),
    ("decide condition", """\
                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4) {
            Direction dir = direction(clientProtocol, serverProtocol);""",
     """\
                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5) {
            Direction dir = direction(clientProtocol, serverProtocol);"""),
    ("decide 770 branch", """\
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
            }""",
     """\
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
            }
            if (clientProtocol == ProtocolVersion.V1_21_5) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル770（1.21.5）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は769⇔770で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル770（1.21.5）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は769⇔770で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }"""),
    ("too-new message", """\
                        + "このサーバーは1.21.4（769）まで対応しています。");""",
     """\
                        + "このサーバーは1.21.5（770）まで対応しています。");"""),
    ("supportedClientProtocols", """\
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_20_2,
                SERVER_PROTOCOL, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21,
                ProtocolVersion.V1_21_2, ProtocolVersion.V1_21_4);""",
     """\
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_20_2,
                SERVER_PROTOCOL, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21,
                ProtocolVersion.V1_21_2, ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5);"""),
    ("versionName", """\
        if (protocol == ProtocolVersion.V1_21_4) {
            return "1.21.4";
        }
        return null;""",
     """\
        if (protocol == ProtocolVersion.V1_21_4) {
            return "1.21.4";
        }
        if (protocol == ProtocolVersion.V1_21_5) {
            return "1.21.5";
        }
        return null;"""),
    ("differences(int) 770 block", """\
                                    + "プレイ状態の769変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の769対応で実装済み。スロット・"
                                    + "Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは768⇔769で変換する。残る制限：Pick Item は"
                                    + "両方向とも落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }""",
     """\
                                    + "プレイ状態の769変換は対応表（PacketIds）・レジストリ表"
                                    + "（RegistryRemapper）の769対応で実装済み。スロット・"
                                    + "Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは768⇔769で変換する。残る制限：Pick Item は"
                                    + "両方向とも落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_5) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が770と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.4と1.21.5で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は769と同じ形。",
                            Handling.UNAFFECTED,
                            "769と770の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは770にもあり、769と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（ブロック状態・アイテム・エンティティ・音・"
                                    + "パーティクル）。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は769と同じ。Registry Data の形式も"
                                    + "769と同じエントリ配列だが、ブロック状態・アイテム・"
                                    + "エンティティ・音・パーティクルの順序が変わる。"
                                    + "Custom Report Details・Server Links は769にも770にもある。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が769から変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・パーティクル）。"
                                    + "データコンポーネント種別も 67→96件に増えた。",
                            Handling.TRANSLATED,
                            "ブロック状態は 27,866→27,914件（茂み・ワイルドフラワー・"
                                    + "リーフリッター・ホタルブッシュ・サボテンの花・枯れ草・"
                                    + "テストブロックの追加と挿入によるずれ）、アイテムは"
                                    + " 1,385→1,396件（青・茶の卵など）、エンティティは 149→150件"
                                    + "（potion の splash／lingering への分割）、音は 1,651→1,702件、"
                                    + "パーティクルは 112→114件（tinted_leaves・firefly の追加）。"
                                    + "バイオーム・属性・エンチャント・メニューは変わらない。"
                                    + "データコンポーネント種別は 67→96件（tooltip_display の"
                                    + "追加と hide_tooltip／hide_additional_tooltip の統合など）。"
                                    + "ダメージ種別・絵画・旗の模様・トリム・楽器・ジュークボックスの"
                                    + "歌は変わらない。"),
                    new Difference("PLAY",
                            "770で新規のパケットが3件あり、既存パケットのIDも122件変わる"
                                    + "（送信側117件、受信側5件）。Chunk Data の heightmap、"
                                    + "Player Chat、Chat Message／Chat Command Signed、"
                                    + "Window Click／Set Creative Slot、Entity Metadata の"
                                    + "形式も変わった。",
                            Handling.TRANSLATED,
                            "新規は Test Instance Block Status（送信側）と Set Test Block・"
                                    + "Test Instance Block Action（受信側）、削除は"
                                    + " Spawn Entity Experience Orb（経験値オーブは通常の"
                                    + " Spawn Entity に統合）。Chunk Data の heightmap は"
                                    + " NBT から直接の配列に、Player Chat は先頭に globalIndex、"
                                    + "Chat Message／Chat Command Signed は末尾に checksum が"
                                    + "付いた。Window Click は HashedSlot、Set Creative Slot は"
                                    + " UntrustedSlot になった。Entity Metadata は cow／wolf／"
                                    + "frog／pig／chicken／painting の variant の型・番号が"
                                    + "変わった。プレイ状態の770変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の770対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは769⇔770で変換する。残る制限：Test Instance"
                                    + " Block 系のパケットは落とす。未対応のデータコンポーネント"
                                    + "種別は落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }"""),
    ("differences(int,int) server condition", """\
        if (serverProtocol == ProtocolVersion.V1_20_1
                || serverProtocol == ProtocolVersion.V1_20_5
                || serverProtocol == ProtocolVersion.V1_21
                || serverProtocol == ProtocolVersion.V1_21_2
                || serverProtocol == ProtocolVersion.V1_21_4) {
            // 内部763・766・767・768・769の差の一覧は、対応表・レジストリ表の766／767／768／769対応と
            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの
            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769は消費・中継）。""",
     """\
        if (serverProtocol == ProtocolVersion.V1_20_1
                || serverProtocol == ProtocolVersion.V1_20_5
                || serverProtocol == ProtocolVersion.V1_21
                || serverProtocol == ProtocolVersion.V1_21_2
                || serverProtocol == ProtocolVersion.V1_21_4
                || serverProtocol == ProtocolVersion.V1_21_5) {
            // 内部763・766・767・768・769・770の差の一覧は、対応表・レジストリ表の766／767／768／769／770対応と
            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの
            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770は消費・中継）。"""),
    ("differences(int,int) client check", """\
            if (clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4) {""",
     """\
            if (clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4
                    && clientProtocol != ProtocolVersion.V1_21_5) {"""),
    ("differences(int,int) internal 770 config", """\
                                            : serverProtocol == ProtocolVersion.V1_21_4
                                                    ? "内部769の設定フェーズは、763クライアントにはプロキシが"
                                                            + "769クライアント役として消費し、764／765／766／767／768クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "769の新規パケット（Pick Item From Block／Entity・"
                                                            + "Player Loaded）は古いクライアントには落とす。"
""",
     """\
                                            : serverProtocol == ProtocolVersion.V1_21_5
                                                    ? "内部770の設定フェーズは、763クライアントにはプロキシが"
                                                            + "770クライアント役として消費し、764／765／766／767／768／769クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは無い。"
                                            : serverProtocol == ProtocolVersion.V1_21_4
                                                    ? "内部769の設定フェーズは、763クライアントにはプロキシが"
                                                            + "769クライアント役として消費し、764／765／766／767／768クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "769の新規パケット（Pick Item From Block／Entity・"
                                                            + "Player Loaded）は古いクライアントには落とす。"
"""),
    ("differences(int,int) PLAY", """\
                            "内部" + serverProtocol + "とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、対応表（PacketIds・"
                                    + "RegistryRemapper）の767／768／769対応で実装済み。スロットは"
                                    + "766⇔767⇔768⇔769で変換し、763／764／765向けの Window Click・\"""",
     """\
                            "内部" + serverProtocol + "とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、対応表（PacketIds・"
                                    + "RegistryRemapper）の767／768／769／770対応で実装済み。スロットは"
                                    + "766⇔767⇔768⇔769⇔770で変換し、763／764／765向けの Window Click・\""""),
    ("differences(int,int) 766/767/768/769 client branch", """\
        if (clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4) {""",
     """\
        if (clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5) {"""),
    ("differences(int,int) pack_format", """\
                                    + (clientProtocol == ProtocolVersion.V1_21_4 ? "46"
                                            : clientProtocol == ProtocolVersion.V1_21_2 ? "42"
                                            : clientProtocol == ProtocolVersion.V1_21 ? "34" : "32")""",
     """\
                                    + (clientProtocol == ProtocolVersion.V1_21_5 ? "55"
                                            : clientProtocol == ProtocolVersion.V1_21_4 ? "46"
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
    print("ClientAdapter.java に 770 の対応を挿入した")


if __name__ == "__main__":
    main()
