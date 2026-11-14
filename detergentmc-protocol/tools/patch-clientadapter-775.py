#!/usr/bin/env python3
"""ClientAdapter.java に 775（26.1）対応を入れる。

774 の ClientAdapter 作業（コミット eb8ba8c、hand-done）を 775 へシフトする。
packet-ids-775.py / minecraft-data 26.1 protocol.json で裏を取った事実だけを使う：
- パケットID変更 164件（toClient 100／toServer 64）、新規 5件、削除 0件
- ペイロード変更：Update Time 再構成、Use Entity 再構成＋attack 分離、
  Enchant Item enchantment i8→varint、Client Command 新 action 2、
  Advancements icon Slot→ItemStackTemplate、hand は varint→mapper（線形同一）、
  Chat Command Signed checksum i8→u8（線形同一）
- レジストリ：ブロック状態 29,671→29,873（+202）、アイテム 1,505→1,506（+1）、
  エンティティ 157 同一、音 1,838→1,902（+64）、パーティクル 115→117（+2）、
  データコンポーネント 104→110（+6）、スロット表示 8→11（+3）、
  エンチャント 43 同一、効果 40 同一、メタデータ種別 39→43（+4 音バリアント）
- 主要ID（775）：Join Game 0x31、Chunk Data 0x2D、Keep Alive 0x2C、Ping 0x3D、
  Respawn 0x52、Disconnect 0x20、Add Resource Pack 0x51、Spawn Position 0x61、
  Player Info Update 0x46、Tracked Waypoint 0x8A、Show Dialog 0x8C、Clear Dialog 0x8B

使い方：python3 patch-clientadapter-775.py（detergentmc リポジトリ直下で実行）
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
              "と 1.21.9／1.21.10（773）と 1.21.11（774）。それより古いクライアントは「古すぎる」、",
              "と 1.21.9／1.21.10（773）と 1.21.11（774）と 26.1（775）。それより古いクライアントは「古すぎる」、",
              "A クラス javadoc")

    # --- B. decide() javadoc ---
    src = rep(src,
              "     *   <li>774（1.21.11）：変換して受け入れる。設定フェーズは773と同形で中継する。</li>\n",
              "     *   <li>774（1.21.11）：変換して受け入れる。設定フェーズは773と同形で中継する。</li>\n"
              "     *   <li>775（26.1）：変換して受け入れる。設定フェーズは774と同形で中継する。</li>\n",
              "B decide() javadoc 775行")
    src = rep(src,
              "     *   <li>774より新しい：新しすぎるとして断る。</li>\n"
              "     * </ul>\n"
              "     */\n"
              "    public static Handshake decide(int clientProtocol) {",
              "     *   <li>775より新しい：新しすぎるとして断る。</li>\n"
              "     * </ul>\n"
              "     */\n"
              "    public static Handshake decide(int clientProtocol) {",
              "B decide() javadoc より新しい")

    # --- C. decide(client, server) javadoc ---
    src = rep(src,
              "     * 受け入れるクライアントの集合は内部によらず 759・760・761・762・763・764・765・766・767・768・769・770・771・772・773・774 で、",
              "     * 受け入れるクライアントの集合は内部によらず 759・760・761・762・763・764・765・766・767・768・769・770・771・772・773・774・775 で、",
              "C decide(client,server) javadoc 集合")
    src = rep(src,
              "     *   <li>内部774＋763クライアント：設定フェーズはプロキシが774クライアント役\n"
              "     *       として消費する。内部774＋764／765／766／767／768／769／770／771／772／773クライアント：設定フェーズは\n"
              "     *       中継し、Registry Data の形式と順序だけ変換する。</li>\n",
              "     *   <li>内部774＋763クライアント：設定フェーズはプロキシが774クライアント役\n"
              "     *       として消費する。内部774＋764／765／766／767／768／769／770／771／772／773クライアント：設定フェーズは\n"
              "     *       中継し、Registry Data の形式と順序だけ変換する。</li>\n"
              "     *   <li>内部775＋763クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費する。内部775＋764／765／766／767／768／769／770／771／772／773／774クライアント：設定フェーズは\n"
              "     *       中継し、Registry Data の形式と順序だけ変換する。</li>\n",
              "C decide(client,server) javadoc 内部775")
    src = rep(src,
              "     *   <li>内部774＋759クライアント：設定フェーズはプロキシが774クライアント役\n"
              "     *       として消費し、プレイ状態は774→763→762→761→760→759の五段変換で受け入れる。</li>\n",
              "     *   <li>内部774＋759クライアント：設定フェーズはプロキシが774クライアント役\n"
              "     *       として消費し、プレイ状態は774→763→762→761→760→759の五段変換で受け入れる。</li>\n"
              "     *   <li>内部775＋762クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費し、プレイ状態は775→774→763→762の三段変換で受け入れる。</li>\n"
              "     *   <li>内部775＋761クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費し、プレイ状態は775→774→763→762→761の四段変換で受け入れる。</li>\n"
              "     *   <li>内部775＋760クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費し、プレイ状態は775→774→763→762→761→760の五段変換で受け入れる。</li>\n"
              "     *   <li>内部775＋759クライアント：設定フェーズはプロキシが775クライアント役\n"
              "     *       として消費し、プレイ状態は775→774→763→762→761→760→759の六段変換で受け入れる。</li>\n",
              "C decide(client,server) javadoc 内部775＋759")
    src = rep(src,
              "     *   <li>774より新しい：新しすぎるとして断る。</li>\n"
              "     * </ul>\n"
              "     */\n"
              "    public static Handshake decide(int clientProtocol, int serverProtocol) {",
              "     *   <li>775より新しい：新しすぎるとして断る。</li>\n"
              "     * </ul>\n"
              "     */\n"
              "    public static Handshake decide(int clientProtocol, int serverProtocol) {",
              "C decide(client,server) javadoc より新しい")

    # --- D. decide() 本体：受け入れ条件に V26_1 を追加 ---
    src = rep(src,
              "                || clientProtocol == ProtocolVersion.V1_21_9\n"
              "                || clientProtocol == ProtocolVersion.V1_21_11) {\n"
              "            Direction dir = direction(clientProtocol, serverProtocol);",
              "                || clientProtocol == ProtocolVersion.V1_21_9\n"
              "                || clientProtocol == ProtocolVersion.V1_21_11\n"
              "                || clientProtocol == ProtocolVersion.V26_1) {\n"
              "            Direction dir = direction(clientProtocol, serverProtocol);",
              "D decide() 受け入れ条件")
    # V26_1 分岐を V1_21_11 分岐の直後（V1_21_7 分岐の直前）に入れる
    src = rep(src,
              "            if (clientProtocol == ProtocolVersion.V1_21_7) {\n"
              "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
              "                        dir == Direction.UP\n"
              "                                ? \"プロトコル772（1.21.7／1.21.8）のクライアントを変換して受け入れます。\"",
              "            if (clientProtocol == ProtocolVersion.V26_1) {\n"
              "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
              "                        dir == Direction.UP\n"
              "                                ? \"プロトコル775（26.1）のクライアントを変換して受け入れます。\"\n"
              "                                        + \"設定フェーズはそのまま中継し、レジストリ差分だけを変換します。\"\n"
              "                                        + \"スロットと属性は774⇔775で変換します\"\n"
              "                                        + \"（763／764／765向けはWindow Click・Set Creative Slotを落とします）。\"\n"
              "                                        + \"パケットIDは774から164件変わるため番号を付け替えます。\"\n"
              "                                : \"プロトコル775（26.1）のクライアントを下向き変換で受け入れます。\"\n"
              "                                        + \"設定フェーズはそのまま中継し、レジストリ差分だけを変換します。\"\n"
              "                                        + \"スロットと属性は774⇔775で変換します\"\n"
              "                                        + \"（763／764／765向けはWindow Click・Set Creative Slotを落とします）。\"\n"
              "                                        + \"パケットIDは774から164件変わるため番号を付け替えます。\");\n"
              "            }\n"
              "            if (clientProtocol == ProtocolVersion.V1_21_7) {\n"
              "                return new Handshake(Decision.ACCEPT_VIA_ADAPTER, clientProtocol,\n"
              "                        dir == Direction.UP\n"
              "                                ? \"プロトコル772（1.21.7／1.21.8）のクライアントを変換して受け入れます。\"",
              "D decide() V26_1 分岐")
    # REJECT_TOO_NEW のメッセージ
    src = rep(src,
              "                        + \"このサーバーは1.21.11（774）まで対応しています。\");",
              "                        + \"このサーバーは26.1（775）まで対応しています。\");",
              "D decide() REJECT_TOO_NEW")

    # --- E. supportedClientProtocols() ---
    src = rep(src,
              "                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11);",
              "                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11,\n"
              "                ProtocolVersion.V26_1);",
              "E supportedClientProtocols")

    # --- F. versionName()：V1_21_11 分岐の直後に V26_1 を追加 ---
    src = rep(src,
              "        if (protocol == ProtocolVersion.V1_21_11) {\n"
              "            return \"1.21.11\";\n"
              "        }\n"
              "        if (protocol == ProtocolVersion.V1_21_7) {",
              "        if (protocol == ProtocolVersion.V1_21_11) {\n"
              "            return \"1.21.11\";\n"
              "        }\n"
              "        if (protocol == ProtocolVersion.V26_1) {\n"
              "            return \"26.1\";\n"
              "        }\n"
              "        if (protocol == ProtocolVersion.V1_21_7) {",
              "F versionName")

    # --- G. differences(775)：774 分岐の直後（V1_19 分岐の直前）に新分岐を挿入 ---
    diff775 = """        if (clientProtocol == ProtocolVersion.V26_1) {
            return List.of(
                    new Difference("HANDSHAKE",
                            "ハンドシェイク自体の形は同じ。プロトコル番号が775と名乗るだけ。",
                            Handling.UNAFFECTED,
                            "ハンドシェイクパケットの構造は1.21.11と26.1で変わっていない。"),
                    new Difference("LOGIN",
                            "ログイン開始・暗号化・ログイン成功は774と同じ形。",
                            Handling.UNAFFECTED,
                            "774と775の Login Success はどちらも末尾に strictErrorHandling が"
                                    + "無い。online-modeの認証も含め、ログインの流れに差はない。"),
                    new Difference("CONFIGURATION",
                            "設定フェーズは775にもあり、774と同形。そのまま中継するが、"
                                    + "Registry Data の順序が変わるレジストリがある"
                                    + "（アイテム・エンティティ・音・データコンポーネント種別・スロット表示）。"
                                    + "Code of Conduct は774と同じ。",
                            Handling.TRANSLATED,
                            "設定フェーズの段階の順番は774と同じ。Registry Data の形式も"
                                    + "774と同じエントリ配列だが、アイテム・エンティティ・"
                                    + "音・データコンポーネント種別・スロット表示の順序が変わる。"
                                    + "minecraft:dialog レジストリは774と同じ。"
                                    + "Code of Conduct（設定 0x13）・Accept Code of Conduct"
                                    + "（送信側 0x09）は774と同ID・同形のため素通し。Show Dialog（0x12）・"
                                    + "Clear Dialog（0x11）・Custom Click Action（設定 0x08）は"
                                    + "774と同ID・同形のため素通し。設定フェーズの新規パケットは無い。"),
                    new Difference("REGISTRY",
                            "レジストリの順序が774から変わった（アイテム・"
                                    + "ブロック状態・音・パーティクル・データコンポーネント種別・"
                                    + "スロット表示・エンティティメタデータ種別）。",
                            Handling.TRANSLATED,
                            "ブロック状態は 29,671→29,873件（金のタンポポ2種と"
                                    + "トランペットの音符ブロック200状態を追加）、"
                                    + "アイテムは 1,505→1,506件（金のタンポポを230番に挿入）、"
                                    + "エンティティは 157件で同一、"
                                    + "音は 1,838→1,902件（子猫・子鶏・子馬・子豚・子狼などの"
                                    + "子どもの音と金のタンポポ・トランペット64件）、"
                                    + "パーティクルは 115→117件（pause_mob_growth・"
                                    + "reset_mob_growth を50・51番に挿入）、"
                                    + "ブロックエンティティは 49件で同一（本物の26.1の codec で"
                                    + "Stage E に確認）、データコンポーネント種別は"
                                    + " 104→110件（additional_trade_cost・dye・"
                                    + "pig/sound_variant・cow/sound_variant・"
                                    + "chicken/sound_variant・cat/sound_variant の6種を追加）、"
                                    + "スロット表示は 8→11件（with_any_potion・"
                                    + "only_with_component・dyed の3種を追加）、"
                                    + "エンチャントは 43件で同一、効果は 40件で同一、"
                                    + "環境属性は 45→48件（visual系3種を追加）、"
                                    + "レシピシリアライザは 22→21件（crafting_special_* 3種を"
                                    + "削除し crafting_dye・crafting_imbue を追加）。"
                                    + "バイオーム・メニュー・属性・ダメージ種別・絵画・"
                                    + "ジュークボックスの歌・旗の模様・トリム・楽器は変わらない。"),
                    new Difference("PLAY",
                            "775で新規のパケットは5件、削除は0件、パケットIDは"
                                    + "774から164件変わる（クライアント行き100件・サーバー行き64件）。",
                            Handling.TRANSLATED,
                            "新規パケットは Game Rule Values（0x27）・"
                                    + "Low Disk Space Warning（0x32）・Attack（送信側 0x01）・"
                                    + "Set Game Rule（送信側 0x39）・Spectate Entity（送信側 0x3E）。"
                                    + "送信側の先頭への Attack 挿入で送信側 0x01 以降が+1、"
                                    + "0x39 以降がさらに+1、0x3E 以降がさらに+1ずれる。"
                                    + "クライアント行きは 0x27 と 0x32 の挿入で 0x27 以降が+1、"
                                    + "0x32 以降が+2ずれる（Join Game 0x31、Chunk Data 0x2D、"
                                    + "Keep Alive 0x2C、Ping 0x3D、Respawn 0x52、"
                                    + "Disconnect 0x20、Add Resource Pack 0x51、"
                                    + "Spawn Position 0x61、Player Info Update 0x46、"
                                    + "Tracked Waypoint 0x8A、Show Dialog 0x8C、Clear Dialog 0x8B）。"
                                    + "Update Time は time・tickDayTime から clockUpdates 配列へ"
                                    + "再構成されたため変換する。Use Entity は再構成され、攻撃は"
                                    + " Attack パケットへ分離されたため変換する。Enchant Item"
                                    + "（送信側）の enchantment フィールドが i8 から varint に"
                                    + "戻ったが、値は 0〜42 のためバイト列は同じ。Client Command に"
                                    + " request_gamerule_values（action 2）が追加された。"
                                    + "Advancements のアイコンが Slot から ItemStackTemplate に"
                                    + "変わった。Entity Metadata の種別は 39→43件（cat／cow／"
                                    + "pig／chicken の sound_variant 4種を挿入）。"
                                    + "Chat Message の checksum・Vehicle Move の onGround・"
                                    + "Window Click の HashedSlot・Set Creative Slot の UntrustedSlot・"
                                    + "Player Chat の globalIndex は774・775とも同じため、"
                                    + "番号だけ付け替える。"
                                    + "プレイ状態の775変換は対応表（PacketIds）・"
                                    + "レジストリ表（RegistryRemapper）の775対応で実装済み。"
                                    + "スロット・Entity Metadata・Update Attributes・Explosion・"
                                    + "移動パケットは774⇔775で変換する。残る制限：金のタンポポは"
                                    + "774では石に代替する。子どもの音バリアントは774では親の音に"
                                    + "代替する。Window Click・Set Creative Slot は"
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
              + diff775
              + "        if (clientProtocol == ProtocolVersion.V1_19) {\n"
              "            return List.of(\n"
              "                    new Difference(\"HANDSHAKE\",\n"
              "                            \"ハンドシェイク自体の形は同じ。プロトコル番号が759と名乗るだけ。\",",
              "G differences(775)")

    # --- H. differences(client, server)：内部775を追加 ---
    src = rep(src,
              "                || serverProtocol == ProtocolVersion.V1_21_9\n"
              "                || serverProtocol == ProtocolVersion.V1_21_11) {",
              "                || serverProtocol == ProtocolVersion.V1_21_9\n"
              "                || serverProtocol == ProtocolVersion.V1_21_11\n"
              "                || serverProtocol == ProtocolVersion.V26_1) {",
              "H サーバー条件")
    src = rep(src,
              "            // 内部763・766・767・768・769・770・771・772・773・774の差の一覧は、対応表・レジストリ表の766／767／768／769／770／771／772／773／774対応と\n"
              "            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの\n"
              "            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770・771・772・773・774は消費・中継）。",
              "            // 内部763・766・767・768・769・770・771・772・773・774・775の差の一覧は、対応表・レジストリ表の766／767／768／769／770／771／772／773／774／775対応と\n"
              "            // 内部763向けのプレイ変換が揃ってから書く。設定フェーズの\n"
              "            // 扱いだけは決まっている（内部763は合成、内部766・767・768・769・770・771・772・773・774・775は消費・中継）。",
              "H サーバー条件コメント")
    src = rep(src,
              "                    && clientProtocol != ProtocolVersion.V1_21_9\n"
              "                    && clientProtocol != ProtocolVersion.V1_21_11) {\n"
              "                return List.of(new Difference(\"HANDSHAKE\",",
              "                    && clientProtocol != ProtocolVersion.V1_21_9\n"
              "                    && clientProtocol != ProtocolVersion.V1_21_11\n"
              "                    && clientProtocol != ProtocolVersion.V26_1) {\n"
              "                return List.of(new Difference(\"HANDSHAKE\",",
              "H 内側クライアント条件")
    src = rep(src,
              "                                            : serverProtocol == ProtocolVersion.V1_21_11\n"
              "                                                    ? \"内部774の設定フェーズは、763クライアントにはプロキシが\"\n"
              "                                                            + \"774クライアント役として消費し、764／765／766／767／768／769／770／771／772／773クライアント\"\n"
              "                                                            + \"には中継する（Registry Data の形式と順序だけ変換）。\"\n"
              "                                                            + \"設定フェーズの新規パケットは Show／Clear Dialog。\"",
              "                                            : serverProtocol == ProtocolVersion.V26_1\n"
              "                                                    ? \"内部775の設定フェーズは、763クライアントにはプロキシが\"\n"
              "                                                            + \"775クライアント役として消費し、764／765／766／767／768／769／770／771／772／773／774クライアント\"\n"
              "                                                            + \"には中継する（Registry Data の形式と順序だけ変換）。\"\n"
              "                                                            + \"設定フェーズの新規パケットは無い。\"\n"
              "                                            : serverProtocol == ProtocolVersion.V1_21_11\n"
              "                                                    ? \"内部774の設定フェーズは、763クライアントにはプロキシが\"\n"
              "                                                            + \"774クライアント役として消費し、764／765／766／767／768／769／770／771／772／773クライアント\"\n"
              "                                                            + \"には中継する（Registry Data の形式と順序だけ変換）。\"\n"
              "                                                            + \"設定フェーズの新規パケットは Show／Clear Dialog。\"",
              "H CONFIGURATION 三項")
    src = rep(src,
              "                            \"内部\" + serverProtocol + \"とクライアント\" + clientProtocol\n"
              "                                    + \"の間のプレイ状態の変換は、対応表（PacketIds・\"\n"
              "                                    + \"RegistryRemapper）の767／768／769／770／771／772／773／774対応で実装済み。スロットは\"\n"
              "                                    + \"766⇔767⇔768⇔769⇔770⇔771⇔772⇔773⇔774で変換し、763／764／765向けの Window Click・\"",
              "                            \"内部\" + serverProtocol + \"とクライアント\" + clientProtocol\n"
              "                                    + \"の間のプレイ状態の変換は、対応表（PacketIds・\"\n"
              "                                    + \"RegistryRemapper）の767／768／769／770／771／772／773／774／775対応で実装済み。スロットは\"\n"
              "                                    + \"766⇔767⇔768⇔769⇔770⇔771⇔772⇔773⇔774⇔775で変換し、763／764／765向けの Window Click・\"",
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
              "                && clientProtocol != ProtocolVersion.V1_21_11) {",
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
              "                || clientProtocol == ProtocolVersion.V1_21_11) {",
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
              "H 後置クライアント条件（==）")

    # --- I. pack_format 三項演算子：775（84）を追加し、774（75）の漏れも直す ---
    # 774 の作業でこの三項に V1_21_11 が追加されなかった既存の抜けで、
    # 774 クライアントは誤って "32" になっていた。775 の追加と同じ行のため併せて直す。
    src = rep(src,
              "                                    + (clientProtocol == ProtocolVersion.V1_21_9 ? \"69\"\n"
              "                                            : clientProtocol == ProtocolVersion.V1_21_7 ? \"64\"",
              "                                    + (clientProtocol == ProtocolVersion.V26_1 ? \"84\"\n"
              "                                            : clientProtocol == ProtocolVersion.V1_21_11 ? \"75\"\n"
              "                                            : clientProtocol == ProtocolVersion.V1_21_9 ? \"69\"\n"
              "                                            : clientProtocol == ProtocolVersion.V1_21_7 ? \"64\"",
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
    print("ClientAdapter.java に 775 を適用した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
