#!/usr/bin/env python3
"""ClientAdapter.java に 776（26.2）対応を入れる。

775 の ClientAdapter 作業（patch-clientadapter-775.py）を 776 へシフトする。
packet-ids-776.json / 本物のバニラ26.2のjar / vv-26.2.json で裏を取った事実だけを使う：
- パケットID変更 0件、新規 0件、削除 0件。唯一の違いは play toServer 0x3e の
  改名（spectate_entity → spectator_action、同じ位置）
- ペイロード変更：Join Game 末尾に onlineMode bool を追加、Login Success 末尾に
  sessionId UUID を追加、Teams の Parameters を作り直し（ワイヤ順と color の
  Optional 化・options の byte 化）、SpectatorAction は OptionalInt（0=停止、
  それ以外は entityId+1）
- レジストリ：ブロック状態 29,873→32,366（+2,493）、ブロック 1,168→1,196（+28）、
  アイテム 1,506→1,537（+31）、エンティティ 157→158（+1 sulfur_cube）、
  音 1,902→1,968（+66）、パーティクル 117→125（+8）、データコンポーネント
  110→111（+1 sulfur_cube_content）、属性 35→40（+5）、ブロックエンティティ
  49件で同一件数だが bed を削除し potent_sulfur を追加、ダメージ種別 50→51
  （+sulfur_cube_hot）、ジュークボックスの歌 21→22（+bounce）、バイオーム
  65→66（+sulfur_caves）、sulfur_cube_archetype を新規追加（12件）、
  エンチャント 43件で同一、argumenttypes の color → team_color に改名
- 主要ID（776）：Join Game 0x31、Chunk Data 0x2D、Keep Alive 0x2C、Ping 0x3D、
  Respawn 0x52、Disconnect 0x20、Add Resource Pack 0x51、Spawn Position 0x61、
  Player Info Update 0x46、Tracked Waypoint 0x8A、Show Dialog 0x8C、Clear Dialog 0x8B
  （すべて775と同一）

使い方：python3 patch-clientadapter-776.py（detergentmc リポジトリ直下で実行）
"""
import sys
from pathlib import Path

PATH = Path("detergentmc-protocol/src/main/java/io/detergentmc/protocol/ClientAdapter.java")

failures = []


def rep(src, old, new, note, count=1):
    """old が count 回だけ現れることを確認して置換する。"""
    n = src.count(old)
    if n != count:
        failures.append(f"{note}: 期待 {count} 回だが {n} 回（{old[:60]!r}）")
        return src
    return src.replace(old, new)


def main():
    src = PATH.read_text(encoding="utf-8")
    orig = src

    # --- A. クラス javadoc の対応クライアント一覧 ---
    src = rep(src,
              "と 1.21.9／1.21.10（773）と 1.21.11（774）と 26.1（775）。それより古いクライアントは「古すぎる」、",
              "と 1.21.9／1.21.10（773）と 1.21.11（774）と 26.1（775）と 26.2（776）。それより古いクライアントは「古すぎる」、",
              "A クラス javadoc")

    # --- B. decide() javadoc ---
    src = rep(src,
              "     *   <li>775（26.1）：変換して受け入れる。設定フェーズは774と同形で中継する。</li>\n",
              "     *   <li>775（26.1）：変換して受け入れる。設定フェーズは774と同形で中継する。</li>\n"
              "     *   <li>776（26.2）：変換して受け入れる。設定フェーズは775と同形で中継する。</li>\n",
              "B decide() javadoc 776行")
    src = rep(src,
              "     *   <li>775より新しい：新しすぎるとして断る。</li>\n"
              "     * </ul>\n"
              "     */\n"
              "    public static Handshake decide(int clientProtocol) {",
              "     *   <li>776より新しい：新しすぎるとして断る。</li>\n"
              "     * </ul>\n"
              "     */\n"
              "    public static Handshake decide(int clientProtocol) {",
              "B decide() javadoc より新しい")

    # --- C. decide(client, server) javadoc ---
    src = rep(src,
              "     * 受け入れるクライアントの集合は内部によらず 759・760・761・762・763・764・765・766・767・768・769・770・771・772・773・774・775 で、",
              "     * 受け入れるクライアントの集合は内部によらず 759・760・761・762・763・764・765・766・767・768・769・770・771・772・773・774・775・776 で、",
              "C decide(client,server) javadoc 集合")
    src = rep(src,
              "     *   <li>内部775＋763クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費する。内部775＋764／765／766／767／768／769／770／771／772／773／774クライアント：設定フェーズは\n"
              "     *       中継し、Registry Data の形式と順序だけ変換する。</li>\n",
              "     *   <li>内部775＋763クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費する。内部775＋764／765／766／767／768／769／770／771／772／773／774クライアント：設定フェーズは\n"
              "     *       中継し、Registry Data の形式と順序だけ変換する。</li>\n"
              "     *   <li>内部776＋763クライアント：設定フェーズはプロキシが776クライアント役\n"
              "     *       として消費する。内部776＋764／765／766／767／768／769／770／771／772／773／774／775クライアント：設定フェーズは\n"
              "     *       中継し、Registry Data の形式と順序だけ変換する。</li>\n",
              "C decide(client,server) javadoc 内部776")
    src = rep(src,
              "     *   <li>内部775＋759クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費し、プレイ状態は775→774→763→762→761→760→759の六段変換で受け入れる。</li>\n",
              "     *   <li>内部775＋759クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費し、プレイ状態は775→774→763→762→761→760→759の六段変換で受け入れる。</li>\n"
              "     *   <li>内部776＋762クライアント：設定フェーズはプロキシが776クライアント役\n"
              "     *       として消費し、プレイ状態は776→775→774→763→762の四段変換で受け入れる。</li>\n"
              "     *   <li>内部776＋761クライアント：設定フェーズはプロキシが776クライアント役\n"
              "     *       として消費し、プレイ状態は776→775→774→763→762→761の五段変換で受け入れる。</li>\n"
              "     *   <li>内部776＋760クライアント：設定フェーズはプロキシが776クライアント役\n"
              "     *       として消費し、プレイ状態は776→775→774→763→762→761→760の六段変換で受け入れる。</li>\n"
              "     *   <li>内部776＋759クライアント：設定フェーズはプロキシが776クライアント役\n"
              "     *       として消費し、プレイ状態は776→775→774→763→762→761→760→759の七段変換で受け入れる。</li>\n",
              "C decide(client,server) javadoc 内部776＋759")
    src = rep(src,
              "     *   <li>775より新しい：新しすぎるとして断る。</li>\n"
              "     * </ul>\n"
              "     */\n"
              "    public static Handshake decide(int clientProtocol, int serverProtocol) {",
              "     *   <li>776より新しい：新しすぎるとして断る。</li>\n"
              "     * </ul>\n"
              "     */\n"
              "    public static Handshake decide(int clientProtocol, int serverProtocol) {",
              "C decide(client,server) javadoc より新しい")

    # --- D. decide() 本体：受け入れ条件に V26_2 を追加 ---
    src = rep(src,
              "                || clientProtocol == ProtocolVersion.V1_21_9\n"
              "                || clientProtocol == ProtocolVersion.V1_21_11\n"
              "                || clientProtocol == ProtocolVersion.V26_1) {\n"
              "            Direction dir = direction(clientProtocol, serverProtocol);",
              "                || clientProtocol == ProtocolVersion.V1_21_9\n"
              "                || clientProtocol == ProtocolVersion.V1_21_11\n"
              "                || clientProtocol == ProtocolVersion.V26_1\n"
              "                || clientProtocol == ProtocolVersion.V26_2) {\n"
              "            Direction dir = direction(clientProtocol, serverProtocol);",
              "D decide() 受け入れ条件")
    # V26_2 分岐を V26_1 分岐の直後（V1_21_7 分岐の直前）に入れる
    src = rep(src,
              "            if (clientProtocol == ProtocolVersion.V1_21_7) {\n"
              "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
              "                        dir == Direction.UP\n"
              "                                ? \"プロトコル772（1.21.7／1.21.8）のクライアントを変換して受け入れます。\"",
              "            if (clientProtocol == ProtocolVersion.V26_2) {\n"
              "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
              "                        dir == Direction.UP\n"
              "                                ? \"プロトコル776（26.2）のクライアントを変換して受け入れます。\"\n"
              "                                        + \"設定フェーズはそのまま中継し、レジストリ差分だけを変換します。\"\n"
              "                                        + \"スロットと属性は775⇔776で変換します\"\n"
              "                                        + \"（763／764／765向けはWindow Click・Set Creative Slotを落とします）。\"\n"
              "                                        + \"パケットIDは775から変わらないため番号の付け替えは要りません。\"\n"
              "                                : \"プロトコル776（26.2）のクライアントを下向き変換で受け入れます。\"\n"
              "                                        + \"設定フェーズはそのまま中継し、レジストリ差分だけを変換します。\"\n"
              "                                        + \"スロットと属性は775⇔776で変換します\"\n"
              "                                        + \"（763／764／765向けはWindow Click・Set Creative Slotを落とします）。\"\n"
              "                                        + \"パケットIDは775から変わらないため番号の付け替えは要りません。\");\n"
              "            }\n"
              "            if (clientProtocol == ProtocolVersion.V1_21_7) {\n"
              "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
              "                        dir == Direction.UP\n"
              "                                ? \"プロトコル772（1.21.7／1.21.8）のクライアントを変換して受け入れます。\"",
              "D decide() V26_2 分岐")
    # REJECT_TOO_NEW のメッセージ
    src = rep(src,
              "                        + \"このサーバーは26.1（775）まで対応しています。\");",
              "                        + \"このサーバーは26.2（776）まで対応しています。\");",
              "D decide() REJECT_TOO_NEW")

    # --- E. supportedClientProtocols() ---
    src = rep(src,
              "                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11,\n"
              "                ProtocolVersion.V26_1);",
              "                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11,\n"
              "                ProtocolVersion.V26_1, ProtocolVersion.V26_2);",
              "E supportedClientProtocols")

    # --- F. versionName()：V26_1 分岐の直後に V26_2 を追加 ---
    src = rep(src,
              "        if (protocol == ProtocolVersion.V26_1) {\n"
              "            return \"26.1\";\n"
              "        }\n"
              "        if (protocol == ProtocolVersion.V1_21_7) {",
              "        if (protocol == ProtocolVersion.V26_1) {\n"
              "            return \"26.1\";\n"
              "        }\n"
              "        if (protocol == ProtocolVersion.V26_2) {\n"
              "            return \"26.2\";\n"
              "        }\n"
              "        if (protocol == ProtocolVersion.V1_21_7) {",
              "F versionName")

    # --- G. differences(776)：775 分岐の直後（V1_19 分岐の直前）に新分岐を挿入 ---
    diff776 = """        if (clientProtocol == ProtocolVersion.V26_2) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が776と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は26.1と26.2で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化は775と同じ形。Login Success は末尾に"
                                    + " sessionId（UUID）が増えた。",
                            Handling.TRANSLATED,
                            "775と776の Login Success は、末尾に sessionId（UUID）が"
                                    + "増えた点だけが違う。776→775では末尾の UUID を落とし、"
                                    + "775→776ではランダムな UUID を合成して付け足す。"
                                    + "online-modeの認証も含め、ログインの流れにそれ以外の差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは776にもあり、775と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（アイテム・ブロック状態・音・パーティクル・"
                                    + "データコンポーネント種別・エンティティ・属性・"
                                    + "ダメージ種別・ジュークボックスの歌・バイオーム）。"
                                    + "Code of Conduct は775と同じ。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は775と同じ。Registry Data の形式も"
                                    + "775と同じエントリ配列だが、アイテム・ブロック状態・"
                                    + "音・パーティクル・データコンポーネント種別・"
                                    + "エンティティ・属性・ダメージ種別・ジュークボックスの歌・"
                                    + "バイオームの順序が変わる。ブロックエンティティは"
                                    + " bed が無くなり potent_sulfur が増えた。"
                                    + "minecraft:dialog レジストリは775と同じ。"
                                    + "Code of Conduct（設定 0x13）・Accept Code of Conduct"
                                    + "（送信側 0x09）は775と同ID・同形のため素通し。Show Dialog（0x12）・"
                                    + "Clear Dialog（0x11）・Custom Click Action（設定 0x08）は"
                                    + "775と同ID・同形のため素通し。設定フェーズの新規パケットは無い。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が775から変わった（アイテム・"
                                    + "ブロック状態・音・パーティクル・データコンポーネント種別・"
                                    + "エンティティ・属性・ブロックエンティティ・ダメージ種別・"
                                    + "ジュークボックスの歌・バイオーム）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 29,873→32,366件（硫黄・辰砂系のブロック"
                                    + " 2,493状態を追加）、"
                                    + "ブロックは 1,168→1,196件（硫黄・辰砂系28種を追加）、"
                                    + "アイテムは 1,506→1,537件（music_disc_bounce など31種を追加）、"
                                    + "エンティティは 157→158件（sulfur_cube を追加）、"
                                    + "音は 1,902→1,968件（硫黄キューブ・間欠泉などの66件を追加）、"
                                    + "パーティクルは 117→125件（geyser・geyser_base・"
                                    + "geyser_plume・geyser_poof・noxious_gas・"
                                    + "noxious_gas_cloud・sulfur_bubbles・sulfur_cube_goo の8種を追加）、"
                                    + "ブロックエンティティは 49件で同一件数だが bed を削除し"
                                    + " potent_sulfur を追加（本物の26.2の BlockEntityTypes で確認）、"
                                    + "データコンポーネント種別は 110→111件"
                                    + "（sulfur_cube_content を追加）、"
                                    + "属性は 35→40件（air_drag_modifier・below_name_distance・"
                                    + "bounciness・friction_modifier・name_tag_distance の5種を追加）、"
                                    + "エンチャントは 43件で同一、効果は 40件で同一、"
                                    + "ダメージ種別は 50→51件（sulfur_cube_hot を42番に挿入）、"
                                    + "ジュークボックスの歌は 21→22件（bounce を4番に挿入）、"
                                    + "バイオームは 65→66件（sulfur_caves を53番に挿入）、"
                                    + "sulfur_cube_archetype を新規追加（12件）、"
                                    + "メニュー・レシピシリアライザ・スロット表示・環境属性・"
                                    + "統計は変わらない。argumenttypes は color が team_color に"
                                    + "改名されただけ（57件で同一）。"),
                    new Difference("PLAY",
                            "776で新規のパケットは0件、削除は0件、パケットIDは"
                                    + "775から0件変わる。play toServer 0x3e が spectate_entity"
                                    + " から spectator_action に改名されただけ。",
                            Handling.TRANSLATED,
                            "パケットIDは775と同一のため番号の付け替えは要らない"
                                    + "（Join Game 0x31、Chunk Data 0x2D、"
                                    + "Keep Alive 0x2C、Ping 0x3D、Respawn 0x52、"
                                    + "Disconnect 0x20、Add Resource Pack 0x51、"
                                    + "Spawn Position 0x61、Player Info Update 0x46、"
                                    + "Tracked Waypoint 0x8A、Show Dialog 0x8C、Clear Dialog 0x8B）。"
                                    + "Join Game は末尾に onlineMode（boolean）が増えたため、"
                                    + "776→775では落とし、775→776ではプロキシの online-mode"
                                    + "設定値で合成する。Login Success は末尾に sessionId"
                                    + "（UUID）が増えたため、776→775では落とし、775→776では"
                                    + "ランダムな UUID を合成する。Teams は Parameters の"
                                    + "ワイヤ順が変わり、color が Optional になり、options が"
                                    + " byte になったため専用の書き換えで変換する。"
                                    + "Spectate Entity は Spectator Action に改名され、"
                                    + "entityId（varint）が OptionalInt（0=停止、それ以外は"
                                    + " entityId+1）に変わったため変換する。"
                                    + "プレイ状態の776変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の776対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは775⇔776で変換する。残る制限：硫黄・辰砂系の"
                                    + "ブロックは775では石に代替する。sulfur_cube は775では"
                                    + "スライムに代替する。Window Click・Set Creative Slot は"
                                    + "763／764／765向けに落とす。Declare Recipes・Tags・"
                                    + "Statistics・World Event は全方向で未対応（1.20系と同じ）。"));
        }
"""
    src = rep(src,
              "        }\n"
              "        if (clientProtocol == ProtocolVersion.V1_19) {\n"
              "            return List.of(\n"
              "                    new Difference(\"HANDSHAKE\",\n"
              "                            \"ハンドシェイク自体の形は同じ。プロトコル番号が759と名乗るだけ。\",",
              "        }\n"
              + diff776
              + "        if (clientProtocol == ProtocolVersion.V1_19) {\n"
              "            return List.of(\n"
              "                    new Difference(\"HANDSHAKE\",\n"
              "                            \"ハンドシェイク自体の形は同じ。プロトコル番号が759と名乗るだけ。\",",
              "G differences(776)")

    # --- H. differences(client, server)：内部776を追加 ---
    src = rep(src,
              "                || serverProtocol == ProtocolVersion.V1_21_9\n"
              "                || serverProtocol == ProtocolVersion.V1_21_11\n"
              "                || serverProtocol == ProtocolVersion.V26_1) {",
              "                || serverProtocol == ProtocolVersion.V1_21_9\n"
              "                || serverProtocol == ProtocolVersion.V1_21_11\n"
              "                || serverProtocol == ProtocolVersion.V26_1\n"
              "                || serverProtocol == ProtocolVersion.V26_2) {",
              "H サーバー条件")
    src = rep(src,
              "            // 内部763・766・767・768・769・770・771・772・773・774・775の差の一覧は、対応表・レジストリ表の766／767／768／769／770／771／772／773／774／775対応と\n"
              "            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの\n"
              "            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770・771・772・773・774・775は消費・中継）。",
              "            // 内部763・766・767・768・769・770・771・772・773・774・775・776の差の一覧は、対応表・レジストリ表の766／767／768／769／770／771／772／773／774／775／776対応と\n"
              "            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの\n"
              "            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770・771・772・773・774・775・776は消費・中継）。",
              "H サーバー条件コメント")
    src = rep(src,
              "                    && clientProtocol != ProtocolVersion.V1_21_9\n"
              "                    && clientProtocol != ProtocolVersion.V1_21_11\n"
              "                    && clientProtocol != ProtocolVersion.V26_1) {\n"
              "                return List.of(new Difference(\"HANDSHAKE\",",
              "                    && clientProtocol != ProtocolVersion.V1_21_9\n"
              "                    && clientProtocol != ProtocolVersion.V1_21_11\n"
              "                    && clientProtocol != ProtocolVersion.V26_1\n"
              "                    && clientProtocol != ProtocolVersion.V26_2) {\n"
              "                return List.of(new Difference(\"HANDSHAKE\",",
              "H 内側クライアント条件")
    src = rep(src,
              "                                            : serverProtocol == ProtocolVersion.V26_1\n"
              "                                                    ? \"内部775の設定フェーズは、763クライアントにはプロキシが\"\n"
              "                                                            + \"775クライアント役として消費し、764／765／766／767／768／769／770／771／772／773／774クライアント\"\n"
              "                                                            + \"には中継する（Registry Data の形式と順序だけ変換）。\"\n"
              "                                                            + \"設定フェーズの新規パケットは無い。\"",
              "                                            : serverProtocol == ProtocolVersion.V26_2\n"
              "                                                    ? \"内部776の設定フェーズは、763クライアントにはプロキシが\"\n"
              "                                                            + \"776クライアント役として消費し、764／765／766／767／768／769／770／771／772／773／774／775クライアント\"\n"
              "                                                            + \"には中継する（Registry Data の形式と順序だけ変換）。\"\n"
              "                                                            + \"設定フェーズの新規パケットは無い。\"\n"
              "                                            : serverProtocol == ProtocolVersion.V26_1\n"
              "                                                    ? \"内部775の設定フェーズは、763クライアントにはプロキシが\"\n"
              "                                                            + \"775クライアント役として消費し、764／765／766／767／768／769／770／771／772／773／774クライアント\"\n"
              "                                                            + \"には中継する（Registry Data の形式と順序だけ変換）。\"\n"
              "                                                            + \"設定フェーズの新規パケットは無い。\"",
              "H CONFIGURATION 三項")
    src = rep(src,
              "                            \"内部\" + serverProtocol + \"とクライアント\" + clientProtocol\n"
              "                                    + \"の間のプレイ状態の変換は、対応表（PacketIds・\"\n"
              "                                    + \"RegistryRemapper）の767／768／769／770／771／772／773／774／775対応で実装済み。スロットは\"\n"
              "                                    + \"766⇔767⇔768⇔769⇔770⇔771⇔772⇔773⇔774⇔775で変換し、763／764／765向けの Window Click・\"",
              "                            \"内部\" + serverProtocol + \"とクライアント\" + clientProtocol\n"
              "                                    + \"の間のプレイ状態の変換は、対応表（PacketIds・\"\n"
              "                                    + \"RegistryRemapper）の767／768／769／770／771／772／773／774／775／776対応で実装済み。スロットは\"\n"
              "                                    + \"766⇔767⇔768⇔769⇔770⇔771⇔772⇔773⇔774⇔775⇔776で変換し、763／764／765向けの Window Click・\"",
              "H PLAY 文言")
    src = rep(src,
              "        if (clientProtocol != ProtocolVersion.V1_20_1\n"
              "                && clientProtocol != ProtocolVersion.V1_20_2\n"
              "                && clientProtocol != ProtocolVersion.V1_20_4\n"
              "                && clientProtocol != ProtocolVersion.V1_20_5\n"
              "                && clientProtocol != ProtocolVersion.V1_21\n"
              "                && clientProtocol != ProtocolVersion.V1_21_2\n"
              "                && clientProtocol != ProtocolVersion.V1_21_4\n"
              "                && clientProtocol != ProtocolVersion.V1_21_5\n"
              "                && clientProtocol != ProtocolVersion.V1_21_6\n"
              "                && clientProtocol != ProtocolVersion.V1_21_7\n"
              "                && clientProtocol != ProtocolVersion.V1_21_9\n"
              "                && clientProtocol != ProtocolVersion.V1_21_11\n"
              "                && clientProtocol != ProtocolVersion.V26_1) {",
              "        if (clientProtocol != ProtocolVersion.V1_20_1\n"
              "                && clientProtocol != ProtocolVersion.V1_20_2\n"
              "                && clientProtocol != ProtocolVersion.V1_20_4\n"
              "                && clientProtocol != ProtocolVersion.V1_20_5\n"
              "                && clientProtocol != ProtocolVersion.V1_21\n"
              "                && clientProtocol != ProtocolVersion.V1_21_2\n"
              "                && clientProtocol != ProtocolVersion.V1_21_4\n"
              "                && clientProtocol != ProtocolVersion.V1_21_5\n"
              "                && clientProtocol != ProtocolVersion.V1_21_6\n"
              "                && clientProtocol != ProtocolVersion.V1_21_7\n"
              "                && clientProtocol != ProtocolVersion.V1_21_9\n"
              "                && clientProtocol != ProtocolVersion.V1_21_11\n"
              "                && clientProtocol != ProtocolVersion.V26_1\n"
              "                && clientProtocol != ProtocolVersion.V26_2) {",
              "H 後置クライアント条件（!=）")
    src = rep(src,
              "        if (clientProtocol == ProtocolVersion.V1_20_5\n"
              "                || clientProtocol == ProtocolVersion.V1_21\n"
              "                || clientProtocol == ProtocolVersion.V1_21_2\n"
              "                || clientProtocol == ProtocolVersion.V1_21_4\n"
              "                || clientProtocol == ProtocolVersion.V1_21_5\n"
              "                || clientProtocol == ProtocolVersion.V1_21_6\n"
              "                || clientProtocol == ProtocolVersion.V1_21_7\n"
              "                || clientProtocol == ProtocolVersion.V1_21_9\n"
              "                || clientProtocol == ProtocolVersion.V1_21_11\n"
              "                || clientProtocol == ProtocolVersion.V26_1) {",
              "        if (clientProtocol == ProtocolVersion.V1_20_5\n"
              "                || clientProtocol == ProtocolVersion.V1_21\n"
              "                || clientProtocol == ProtocolVersion.V1_21_2\n"
              "                || clientProtocol == ProtocolVersion.V1_21_4\n"
              "                || clientProtocol == ProtocolVersion.V1_21_5\n"
              "                || clientProtocol == ProtocolVersion.V1_21_6\n"
              "                || clientProtocol == ProtocolVersion.V1_21_7\n"
              "                || clientProtocol == ProtocolVersion.V1_21_9\n"
              "                || clientProtocol == ProtocolVersion.V1_21_11\n"
              "                || clientProtocol == ProtocolVersion.V26_1\n"
              "                || clientProtocol == ProtocolVersion.V26_2) {",
              "H 後置クライアント条件（==）")

    # --- I. pack_format 三項演算子：776（88）を追加 ---
    src = rep(src,
              "                                    + (clientProtocol == ProtocolVersion.V26_1 ? \"84\"\n"
              "                                            : clientProtocol == ProtocolVersion.V1_21_11 ? \"75\"",
              "                                    + (clientProtocol == ProtocolVersion.V26_2 ? \"88\"\n"
              "                                            : clientProtocol == ProtocolVersion.V26_1 ? \"84\"\n"
              "                                            : clientProtocol == ProtocolVersion.V1_21_11 ? \"75\"",
              "I pack_format 三項")

    if failures:
        print("失敗:")
        for f in failures:
            print("  -", f)
        print("何も書き込まない。")
        return 1
    if src == orig:
        print("変更なし（既に適用済みか）。")
        return 0
    PATH.write_text(src, encoding="utf-8")
    print("ClientAdapter.java に 776 を適用した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
