#!/usr/bin/env python3
"""ClientAdapter.java へ Java 1.21.6（プロトコル771）の受け入れを入れる。

decide／supportedClientProtocols／versionName／differences を771対応にする。
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
 * と 1.21.4（769）と 1.21.5（770）。それより古いクライアントは「古すぎる」、""",
     """\
 * <p>対応クライアントは 1.20.1（763）と 1.20.2（764）と 1.20.4（765）
 * と 1.20.5／1.20.6（766）と 1.21／1.21.1（767）と 1.21.2／1.21.3（768）
 * と 1.21.4（769）と 1.21.5（770）と 1.21.6（771）。それより古いクライアントは「古すぎる」、"""),
    ("class javadoc 771 play", """\
 * 振り分ける。プレイ状態のパケット変換は未実装のものがある
 * （ {@link io.detergentmc.protocol.ProxySession} 参照）。768のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の768対応で実装する。769のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の769対応で実装する。770のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の770対応で実装する。""",
     """\
 * 振り分ける。プレイ状態のパケット変換は未実装のものがある
 * （ {@link io.detergentmc.protocol.ProxySession} 参照）。768のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の768対応で実装する。769のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の769対応で実装する。770のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の770対応で実装する。771のプレイ状態の
 * 変換も同じく、対応表・レジストリ表の771対応で実装する。"""),
    ("decide javadoc 771 entry", """\
     *   <li>770（1.21.5）：変換して受け入れる。設定フェーズは769と同形で中継する。</li>""",
     """\
     *   <li>770（1.21.5）：変換して受け入れる。設定フェーズは769と同形で中継する。</li>
     *   <li>771（1.21.6）：変換して受け入れる。設定フェーズは770と同形で中継する。</li>"""),
    ("decide(int) javadoc too-new", """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>770より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol) {""",
     """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>771より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol) {"""),
    ("decide(int,int) javadoc accepted set", """\
     * 受け入れるクライアントの集合は内部によらず 763・764・765・766・767・768・769・770 で、""",
     """\
     * 受け入れるクライアントの集合は内部によらず 763・764・765・766・767・768・769・770・771 で、"""),
    ("decide(int,int) javadoc internal 771", """\
     *   <li>内部770＋763クライアント：設定フェーズはプロキシが770クライアント役
     *       として消費する。内部770＋764／765／766／767／768／769クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>""",
     """\
     *   <li>内部770＋763クライアント：設定フェーズはプロキシが770クライアント役
     *       として消費する。内部770＋764／765／766／767／768／769クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>
     *   <li>内部771＋763クライアント：設定フェーズはプロキシが771クライアント役
     *       として消費する。内部771＋764／765／766／767／768／769／770クライアント：設定フェーズは
     *       中継し、Registry Data の形式と順序だけ変換する。</li>"""),
    ("decide(int,int) javadoc too-new", """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>770より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol, int serverProtocol) {""",
     """\
     *   <li>763より古い：古すぎるとして断る。</li>
     *   <li>771より新しい：新しすぎるとして断る。</li>
     * </ul>
     */
    public static Handshake decide(int clientProtocol, int serverProtocol) {"""),
    ("decide condition", """\
                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5) {
            Direction dir = direction(clientProtocol, serverProtocol);""",
     """\
                || clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5
                || clientProtocol == ProtocolVersion.V1_21_6) {
            Direction dir = direction(clientProtocol, serverProtocol);"""),
    ("decide 771 branch", """\
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
            }""",
     """\
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
            }
            if (clientProtocol == ProtocolVersion.V1_21_6) {
                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,
                        dir == Direction.UP
                                ? "プロトコル771（1.21.6）のクライアントを変換して受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は770⇔771で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。"
                                : "プロトコル771（1.21.6）のクライアントを下向き変換で受け入れます。"
                                        + "設定フェーズはそのまま中継し、レジストリ差分だけを変換します。"
                                        + "スロットと属性は770⇔771で変換します"
                                        + "（763／764／765向けはWindow Click・Set Creative Slotを落とします）。");
            }"""),
    ("too-new message", """\
                        + "このサーバーは1.21.5（770）まで対応しています。");""",
     """\
                        + "このサーバーは1.21.6（771）まで対応しています。");"""),
    ("supportedClientProtocols", """\
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_20_2,
                SERVER_PROTOCOL, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21,
                ProtocolVersion.V1_21_2, ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5);""",
     """\
        return List.of(OLDEST_SUPPORTED_CLIENT_PROTOCOL, ProtocolVersion.V1_20_2,
                SERVER_PROTOCOL, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21,
                ProtocolVersion.V1_21_2, ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5,
                ProtocolVersion.V1_21_6);"""),
    ("versionName", """\
        if (protocol == ProtocolVersion.V1_21_5) {
            return "1.21.5";
        }
        return null;""",
     """\
        if (protocol == ProtocolVersion.V1_21_5) {
            return "1.21.5";
        }
        if (protocol == ProtocolVersion.V1_21_6) {
            return "1.21.6";
        }
        return null;"""),
    ("differences(int) 771 block", """\
                                    + "変わった。プレイ状態の770変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の770対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは769⇔770で変換する。残る制限：Test Instance"
                                    + " Block 系のパケットは落とす。未対応のデータコンポーネント"
                                    + "種別は落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }""",
     """\
                                    + "変わった。プレイ状態の770変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の770対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは769⇔770で変換する。残る制限：Test Instance"
                                    + " Block 系のパケットは落とす。未対応のデータコンポーネント"
                                    + "種別は落とす。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
        if (clientProtocol == ProtocolVersion.V1_21_6) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が771と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.5と1.21.6で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は770と同じ形。",
                            Handling.UNAFFECTED,
                            "770と771の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは771にもあり、770と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（ブロック状態・アイテム・エンティティ・音・"
                                    + "ジュークボックスの歌）。minecraft:dialog レジストリが"
                                    + "新規で、Show／Clear Dialog パケットも新規。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は770と同じ。Registry Data の形式も"
                                    + "770と同じエントリ配列だが、ブロック状態・アイテム・"
                                    + "エンティティ・音・ジュークボックスの歌の順序が変わる。"
                                    + "minecraft:dialog レジストリ（3件）が新規。"
                                    + "Custom Report Details・Server Links（サーバー行き）は"
                                    + "771で削除され、代わりに Custom Click Action（0x08）が"
                                    + "新規。Show Dialog（0x12）／Clear Dialog（0x11）も新規。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が770から変わった（ブロック状態・"
                                    + "アイテム・エンティティ・音・ジュークボックスの歌・属性）。"
                                    + "minecraft:dialog レジストリが新規。",
                            Handling.TRANSLATED,
                            "ブロック状態は 27,914→27,946件（乾燥ガストの追加のみ）、"
                                    + "アイテムは 1,396→1,415件（ハーネス・乾燥ガストの"
                                    + "スポーンエッグなど）、エンティティは 150→151件"
                                    + "（ハッピーガストの追加）、音は 1,702→1,727件"
                                    + "（+29／-4、ambient と blow_out の衝突あり）。"
                                    + "属性は 32→35件（camera_distance・"
                                    + "waypoint_transmit_range・waypoint_receive_range の追加）。"
                                    + "ジュークボックスの歌は 19→20件（tears を17番に挿入）。"
                                    + "バイオーム・エンチャント・メニュー・パーティクルは変わらない。"
                                    + "データコンポーネント種別は 96件のまま。"
                                    + "ダメージ種別・絵画・旗の模様・トリム・楽器は変わらない"
                                    + "（ダメージ種別は770と同じ49種・同じ並び。実バニラ1.21.6"
                                    + "のキャプチャで確認済み）。"),
                    new Difference("PLAY",
                            "771で新規のパケットが8件あり、削除が2件、既存パケットのIDも"
                                    + "58件変わる（プレイ送信側）。クライアント行きのIDは"
                                    + "770と変わらない。ロケーターバー（tracked_waypoint）と"
                                    + "ダイアログ（show_dialog／clear_dialog）が新規。",
                            Handling.TRANSLATED,
                            "新規は Change Gamemode（送信側 0x04）・Custom Click Action"
                                    + "（送信側 0x41、設定 0x08）・Tracked Waypoint（0x83）・"
                                    + "Show Dialog（0x85）・Clear Dialog（0x84）・設定の"
                                    + "Show Dialog（0x12）／Clear Dialog（0x11）。"
                                    + "削除は Custom Report Details／Server Links（送信側）。"
                                    + "Change Gamemode 0x04 の挿入で送信側 0x04 以降が+1ずれる"
                                    + "（Chat Message 0x07→0x08、Window Click 0x10→0x11、"
                                    + "Set Creative Slot 0x36→0x37、Keep Alive 応答 0x1A→0x1B、"
                                    + "Ping 応答 0x2B→0x2C）。Chat Message の checksum・"
                                    + "Vehicle Move の onGround・Window Click の HashedSlot・"
                                    + "Set Creative Slot の UntrustedSlot・Player Chat の"
                                    + "globalIndex は770・771とも同じため、番号だけ付け替える。"
                                    + "プレイ状態の771変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の771対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは770⇔771で変換する。残る制限：ロケーターバー・"
                                    + "ダイアログ・Change Gamemode・Custom Click Action は770に"
                                    + "相当が無いため旧クライアント向けに落とす。ハッピーガストは"
                                    + "770ではガストに代替する。乾燥ガスト・ハーネスはアイテム・"
                                    + "ブロック状態の付け替えで通す。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }"""),
    ("differences(int,int) server condition", """\
        if (serverProtocol == ProtocolVersion.V1_20_1
                || serverProtocol == ProtocolVersion.V1_20_5
                || serverProtocol == ProtocolVersion.V1_21
                || serverProtocol == ProtocolVersion.V1_21_2
                || serverProtocol == ProtocolVersion.V1_21_4
                || serverProtocol == ProtocolVersion.V1_21_5) {
            // 内部763・766・767・768・769・770の差の一覧は、対応表・レジストリ表の766／767／768／769／770対応と
            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの
            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770は消費・中継）。""",
     """\
        if (serverProtocol == ProtocolVersion.V1_20_1
                || serverProtocol == ProtocolVersion.V1_20_5
                || serverProtocol == ProtocolVersion.V1_21
                || serverProtocol == ProtocolVersion.V1_21_2
                || serverProtocol == ProtocolVersion.V1_21_4
                || serverProtocol == ProtocolVersion.V1_21_5
                || serverProtocol == ProtocolVersion.V1_21_6) {
            // 内部763・766・767・768・769・770・771の差の一覧は、対応表・レジストリ表の766／767／768／769／770／771対応と
            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの
            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770・771は消費・中継）。"""),
    ("differences(int,int) client check", """\
            if (clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4
                    && clientProtocol != ProtocolVersion.V1_21_5) {""",
     """\
            if (clientProtocol != ProtocolVersion.V1_20_1
                    && clientProtocol != ProtocolVersion.V1_20_2
                    && clientProtocol != ProtocolVersion.V1_20_4
                    && clientProtocol != ProtocolVersion.V1_20_5
                    && clientProtocol != ProtocolVersion.V1_21
                    && clientProtocol != ProtocolVersion.V1_21_2
                    && clientProtocol != ProtocolVersion.V1_21_4
                    && clientProtocol != ProtocolVersion.V1_21_5
                    && clientProtocol != ProtocolVersion.V1_21_6) {"""),
    ("differences(int,int) internal 771 config", """\
                                            : serverProtocol == ProtocolVersion.V1_21_5
                                                    ? "内部770の設定フェーズは、763クライアントにはプロキシが"
                                                            + "770クライアント役として消費し、764／765／766／767／768／769クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは無い。"
""",
     """\
                                            : serverProtocol == ProtocolVersion.V1_21_6
                                                    ? "内部771の設定フェーズは、763クライアントにはプロキシが"
                                                            + "771クライアント役として消費し、764／765／766／767／768／769／770クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは Show／Clear Dialog。"
                                            : serverProtocol == ProtocolVersion.V1_21_5
                                                    ? "内部770の設定フェーズは、763クライアントにはプロキシが"
                                                            + "770クライアント役として消費し、764／765／766／767／768／769クライアント"
                                                            + "には中継する（Registry Data の形式と順序だけ変換）。"
                                                            + "設定フェーズの新規パケットは無い。"
"""),
    ("differences(int,int) PLAY", """\
                            "内部" + serverProtocol + "とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、対応表（PacketIds・"
                                    + "RegistryRemapper）の767／768／769／770対応で実装済み。スロットは"
                                    + "766⇔767⇔768⇔769⇔770で変換し、763／764／765向けの Window Click・\"""",
     """\
                            "内部" + serverProtocol + "とクライアント" + clientProtocol
                                    + "の間のプレイ状態の変換は、対応表（PacketIds・"
                                    + "RegistryRemapper）の767／768／769／770／771対応で実装済み。スロットは"
                                    + "766⇔767⇔768⇔769⇔770⇔771で変換し、763／764／765向けの Window Click・\""""),
    ("differences(int,int) 766/767/768/769/770 client branch", """\
        if (clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5) {""",
     """\
        if (clientProtocol == ProtocolVersion.V1_20_5
                || clientProtocol == ProtocolVersion.V1_21
                || clientProtocol == ProtocolVersion.V1_21_2
                || clientProtocol == ProtocolVersion.V1_21_4
                || clientProtocol == ProtocolVersion.V1_21_5
                || clientProtocol == ProtocolVersion.V1_21_6) {"""),
    ("differences(int,int) pack_format", """\
                                    + (clientProtocol == ProtocolVersion.V1_21_5 ? "55"
                                            : clientProtocol == ProtocolVersion.V1_21_4 ? "46"
                                            : clientProtocol == ProtocolVersion.V1_21_2 ? "42"
                                            : clientProtocol == ProtocolVersion.V1_21 ? "34" : "32")""",
     """\
                                    + (clientProtocol == ProtocolVersion.V1_21_6 ? "63"
                                            : clientProtocol == ProtocolVersion.V1_21_5 ? "55"
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
    print("ClientAdapter.java に 771 の対応を挿入した")


if __name__ == "__main__":
    main()
