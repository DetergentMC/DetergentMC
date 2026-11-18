# Bedrock（統合版）対応：Geyser 風＋Floodgate 風（内蔵）

DetergentMC は Bedrock（Minecraft 統合版）クライアントを、外部プラグイン
なしで内蔵の機能として受け入れる。Geyser 風の翻訳と Floodgate 風の ID
発行を、DetergentMC 自身（`detergentmc-bedrock` モジュール）が行う。
Geyser / Floodgate / Paper / Mojang のコードは一切使っていない。
プロトコル仕様は公開資料（wiki.vg、Minecraft Wiki、wiki.bedrock.dev、
PrismarineJS minecraft-data の Bedrock protocol.json **36 バージョン分**、
PMMP BedrockData、Microsoft Learn、GeyserMC の公開ドキュメント）
から新しく実装した。プロトコル番号は wiki.vg「Bedrock Protocol
version numbers」と minecraft-data の protocol.json で 1 つずつ確認し、
確認できた番号だけを対応表に入れている。

## 対象バージョン

| 項目 | 値 |
|---|---|
| 対象 Bedrock | **1.7.1〜26.52（プロトコル 291〜2193、RakNet 世代の公式プロトコル 63 個）を RakNet で受け入れる。プロトコル 2225（26.60〜26.65）はパケット形式のみ対応（全 64 プロトコル）** |
| 対象外 | **Bedrock 26.60 / 26.62 / 26.63 / 26.65（プロトコル 2225）の実クライアント接続**。26.60 で RakNet は廃止され、トランスポートが WebRTC ベースの NetherNet に置き換わった。2225 のパケット形式（StartGame・AddPlayer 等の形式差分）は実装済みだが、NetherNet リスナーは未実装のため実クライアントは繋がらない。RakNet 経由で 2225 の Login が来た場合は「パケット形式は対応済み、NetherNet トランスポートは未実装」の切断メッセージを返す（下記の「NetherNet対応の設計」を参照） |
| MOTD の表示 | サーバーリストには 630 / 1.20.50 を出す（Unconnected Pong は 1 通りしか書けないため）。Login でクライアントのプロトコルを判定し、そのバージョン用の形式で送る |

### 対応プロトコル一覧（64 個。2225 はパケット形式のみ）

| プロトコル | Bedrock | プロトコル | Bedrock | プロトコル | Bedrock |
|---|---|---|---|---|---|
| 291 | 1.7.0 / 1.7.1 |  |  |  |  |
| 313 | 1.8.0 / 1.8.1 |  |  |  |  |
| 332 | 1.9.0 |  |  |  |  |
| 340 | 1.10.0 / 1.10.1 |  |  |  |  |
| 354 | 1.11.0 / 1.11.1 / 1.11.2 / 1.11.3 / 1.11.4 |  |  |  |  |
| 361 | 1.12.0 / 1.12.1 |  |  |  |  |
| 388 | 1.13.0 / 1.13.1 / 1.13.2 / 1.13.3 |  |  |  |  |
| 389 | 1.14.0 / 1.14.1 / 1.14.20 / 1.14.21 / 1.14.30 / 1.14.41 |  |  |  |  |
| 390 | 1.14.60 |  |  |  |  |
| 407 | 1.16.0 / 1.16.1 / 1.16.1.03 / 1.16.1.04 / 1.16.10 |  |  |  |  |
| 408 | 1.16.20 / 1.16.21 / 1.16.40 / 1.16.42 / 1.16.50 / 1.16.60 / 1.16.61 |  |  |  |  |
| 419 | 1.16.100 / 1.16.101 |  |  |  |  |
| 422 | 1.16.200 / 1.16.201 |  |  |  |  |
| 428 | 1.16.210 |  |  |  |  |
| 431 | 1.16.220 / 1.16.221 |  |  |  |  |
| 440 | 1.17.0 / 1.17.1 / 1.17.2 |  |  |  |  |
| 448 | 1.17.10 / 1.17.11 |  |  |  |  |
| 465 | 1.17.30 / 1.17.32 / 1.17.33 / 1.17.34 |  |  |  |  |
| 471 | 1.17.40 / 1.17.41 |  |  |  |  |
| 475 | 1.18.0 / 1.18.1 / 1.18.2 |  |  |  |  |
| 486 | 1.18.10 / 1.18.11 / 1.18.12 |  |  |  |  |
| 503 | 1.18.30 / 1.18.31 / 1.18.32 / 1.18.33 |  |  |  |  |
| 527 | 1.19.0 / 1.19.1 / 1.19.2 |  |  |  |  |
| 534 | 1.19.10 / 1.19.11 |  |  |  |  |
| 544 | 1.19.20 |  |  |  |  |
| 545 | 1.19.21 / 1.19.22 |  |  |  |  |
| 554 | 1.19.30 / 1.19.31 |  |  |  |  |
| 557 | 1.19.40 / 1.19.41 |  |  |  |  |
| 560 | 1.19.50 / 1.19.51 |  |  |  |  |
| 567 | 1.19.60 / 1.19.62 |  |  |  |  |
| 575 | 1.19.70 |  |  |  |  |
| 582 | 1.19.80 / 1.19.81 / 1.19.83 |  |  |  |  |
| 589 | 1.20.0 / 1.20.1 | 685 | 1.21.0 / 1.21.1 | 844 | 1.21.111 |
| 594 | 1.20.10〜1.20.15 | 686 | 1.21.2 / 1.21.3 | 859 | 1.21.120 |
|  |  |  |  | 860 | 1.21.124 |
| 618 | 1.20.30〜1.20.32 | 712 | 1.21.20 | 898 | 1.21.130 |
| 622 | 1.20.40 / 1.20.41 | 729 | 1.21.30 | 924 | 1.26.0 |
| 630 | 1.20.50 / 1.20.51 | 748 | 1.21.40 | 944 | 1.26.10 |
| 649 | 1.20.60 / 1.20.62 | 766 | 1.21.50 | 975 | 1.26.20 |
| 662 | 1.20.70〜1.20.73 | 776 | 1.21.60 | 1001 | 1.26.30 |
| 671 | 1.20.80 / 1.20.81 | 786 | 1.21.70 | 2168 | 1.26.40 / 1.26.44 |
|  |  | 800 | 1.21.80 | 2169 | 1.26.45 |
|  |  | 818 | 1.21.90 | 2193 | 1.26.50 / 1.26.51 / 1.26.52 |
|  |  | 819 | 1.21.93 |  |  |
|  |  | 827 | 1.21.100 | 2225 | 1.26.60 / 1.26.62 / 1.26.63 / 1.26.65（パケット形式のみ） |

- 1.21.20（712）と 1.21.30（729）は、以前の資料で 704／712 と
  書かれたものもあるが、wiki.vg と bedrock-rs の両方で 712／729 を
  確認したためこちらを採用している。
- 26.51／26.52／26.62 はホットフィックスのため直前のメジャーと同じ
  プロトコル（2193／2225）を共有する。2193 は 1.26.50 / 1.26.51 /
  1.26.52 の 3 バージョンで共有し、Login が運ぶのはプロトコル番号
  だけのため区別できない。`BedrockVersion.fromProtocol(2193)` は
  最初に一致した V1_26_50 を返す（ワイヤ形式は 3 バージョン同一）。
- 1.21.124 もホットフィックスだが、プロトコルが 859→860 に上がる
  ため別エントリとして扱う（protocol.json は 1.21.120 とバイト同一
  で、アイテム ID・ブロック状態に差分なし。くわしくは BE-NOTES.md
  §2 を参照）。
- 1.18.33 も対象で、プロトコル 503（1.18.30／1.18.31／1.18.32／1.18.33 で共有。displayName
  は共有するうち最新の 1.18.33）。503 の protocol.json は 527 のものと
  バイト同一ではない。504〜526 にエントリは無し（512・514・516・524・526 は
  Preview 1.19.0 の番号、残りは Wiki 表にエントリ無し）。
- 1.19.2 も対象で、プロトコル 527（1.19.0／1.19.1／1.19.2 で共有。displayName
  は共有するうち最新の 1.19.2）。527 の protocol.json は 534 のものと
  バイト同一ではない。526 は Preview 1.19.0.68 の番号のため飛ばし、
  次のリリース・プロトコルは 503（1.18.30）。
- 1.19.11 も対象で、プロトコル 534（1.19.10／1.19.11 で共有。displayName
  は共有するうち最新の 1.19.11）。534 の protocol.json は 544 のものと
  バイト同一ではない。528〜533 にエントリは無し（530・532・533 は
  Preview 1.19.10.20／.21／.22 の番号、528・529・531 はスキップされた
  番号）。
- 1.19.20 も対象で、プロトコル 544（1.19.20 のみ。Preview 1.19.20.24
  も 544）。544 の protocol.json は 545 のものとバイト同一。
  535〜543 にエントリは無し（542・543 は Preview 1.19.20.20／.22／.23
  の番号、535〜541 はスキップされた番号）。
- 1.19.22 も対象で、プロトコル 545（1.19.21／1.19.22 で共有）。
  546〜553 にエントリは無し（546〜550 は Wiki 表にもエントリ無し、
  551〜553 は Preview 1.19.30.20／.22／.25 の番号）。
  StartGame・ResourcePacks・Item・Transaction は 554 と JSON 完全一致で
  70 フィールド。554 で追加されたパケット 4 件（192 server_stats・
  193 request_network_settings・194 game_test_request・
  195 game_test_results）が 545 には無い。型変更は 11 件（共有型 7 件＋
  554 新規パケットの型 4 件）：ItemStackRequest に cause が無い（554 で
  追加）、RecipeIngredient は旧形式（network_id＋switch、554 は新形式）、
  SoundType の 442 は Undefined（554 は EnchantingTableUse）、
  packet_text の type に 11 json_announcement が無い、
  packet_network_settings は compression_threshold u16 の 1 フィールドのみ
  （554 は lu16＋4 フィールド追加）、packet_structure_block_update に
  water_logged が無い。コードはこれらの形状を読まないため、バージョン
  分岐は不要（`BedrockVersion.hasEntityProperties()` は 545＝false）。
  アイテム表は 557 の `bedrock_item_ids_1_19_40.bin` を共用する
  （1.19.21 自体の PMMP データは存在しないが、1.19.20 の
  required_item_list.json が 1.19.30／1.19.40 のものとバイト同一で、
  候補表もバイト同一。1.19.21 は 1.19.20 のホットフィックス。
  くわしくは BE-NOTES.md §9 を参照）。
  ブロック表は 1.20.50 世代を共用する（1.19.20 のパレットは 1.19.30／
  1.19.40 とバイト同一で、1.20.50 パレットとの一致は 0/8,291。
  589 でも同じ状況で対応済みのため新規不要。くわしくは BE-NOTES.md §9 を参照）。
- 1.19.31 も対象で、プロトコル 554（1.19.30／1.19.31 で共有）。
  555 は Preview 1.19.40.22 の番号、556 は Bedrock 欄にエントリが
  無いため飛ばした。StartGame・ResourcePacks・Item・Transaction は
  557 と JSON 完全一致で 70 フィールド。567 との差分は emote_chat_muted
  の 1 フィールドのみ（Java コードはこのフィールドを書かない）。
  557 との違いは add_player・add_entity・set_entity_data の 3 件だけで、
  557 で追加された properties（EntityProperties）が 554 には無い
  （バージョン分岐で省く。`BedrockVersion.hasEntityProperties()`）。
  アイテム表は 557 の `bedrock_item_ids_1_19_40.bin` を共用する
  （1.19.30 と 1.19.40 の required_item_list.json がバイト同一で、
  候補表もバイト同一。860 が 859 の表を共用するのと同じ扱い）。
  ブロック表は 1.20.50 世代を共用する（554 のパレットと 1.20.50 の
  パレットはランタイム ID が 1 個も一致しないが、589 でも同じ状況で
  対応済みのため新規不要。くわしくは BE-NOTES.md §8 を参照）。
- 1.19.41 も対象で、プロトコル 557（1.19.40／1.19.41 で共有）。
  555 は Preview 1.19.40.22 の番号、556 は Bedrock 欄にエントリが
  無いため飛ばした。StartGame・ResourcePacks・Item・Transaction は
  560 と JSON 完全一致で 70 フィールド。567 との差分は emote_chat_muted
  の 1 フィールドのみ（Java コードはこのフィールドを書かない）。
  アイテム表は bedrock-1.19.40 タグから新規生成した専用表
  （`bedrock_item_ids_1_19_40.bin`。1.19.50 の表とは 44/1312 行が
  違うため共用できない。末尾側の ID が +1〜+4 シフト）。ブロック表は
  1.20.50 世代を共用する（557 のパレットと 1.20.50 のパレットは
  ランタイム ID が 1 個も一致しないが、589 でも同じ状況で対応済みの
  ため新規不要。くわしくは BE-NOTES.md §7 を参照）。
- 1.19.51 も対象で、プロトコル 560（1.19.50／1.19.51 で共有）。
  561〜566 はベータ／プレビュー版の番号（561 は Preview 1.19.60.20）
  のため飛ばした。StartGame は 557 と JSON 完全一致で 70 フィールド。
  567 との差分は emote_chat_muted の 1 フィールドのみ（Java コードは
  このフィールドを書かない）。ResourcePacks・Item・Transaction は
  567 と JSON 完全一致。アイテム表は bedrock-1.19.50 タグから新規生成した専用表
  （`bedrock_item_ids_1_19_50.bin`。1.19.60 の表とは 164/1312 行が
  違うため共用できない。ほぼ全アイテムが +5 シフト）。ブロック表は
  1.20.50 世代を共用する（560 のパレットと 1.20.50 のパレットは
  ランタイム ID が 1 個も一致しないが、589 でも同じ状況で対応済みの
  ため新規不要。くわしくは BE-NOTES.md §6 を参照）。
- 1.19.62 も対象で、プロトコル 567（1.19.60／1.19.62 で共有）。
  568（1.19.63）は minecraft-data に protocol.json が存在しないため
  飛ばした。StartGame・ResourcePacks・Item・Transaction は 575 と
  JSON 完全一致。アイテム表は bedrock-1.19.60 タグから新規生成した専用表
  （`bedrock_item_ids_1_19_60.bin`。1.19.70 の表とは 401/1312 行が
  違うため共用できない。ほぼ全アイテムが +1 シフト）。ブロック表は
  1.20.50 世代を共用する（567 のパレットと 1.20.50 のパレットは
  ランタイム ID が 1 個も一致しないが、589 でも同じ状況で対応済みの
  ため新規不要。くわしくは BE-NOTES.md §5 を参照）。
- 1.19.70 も対象で、プロトコル 575。582 との StartGame の差分は
  created_in_editor／exported_from_editor／
  block_network_ids_are_hashes の 3 フィールドのみ。アイテム表は
  bedrock-1.19.70 タグから新規生成した専用表
  （`bedrock_item_ids_1_19_70.bin`。1.19.80 の表とは 64/1312 行が
  違うため共用できない。ネザライト装備の ID ずれ等）。ブロック表は
  1.20.50 世代を共用する（575 のパレットと 1.20.50 のパレットは
  ランタイム ID が 1 個も一致しないが、589 でも同じ状況で対応済みの
  ため新規不要。くわしくは BE-NOTES.md §4 を参照）。なお 568
  （1.19.63）は protocol.json が無いため飛ばし、567（1.19.62）を
  対応した（BE-NOTES.md §5）。
- 1.19.83 もホットフィックスで、プロトコル 582 を 1.19.80／1.19.81
  と共有する。582 の基準 protocol.json は minecraft-data の
  `data/bedrock/1.19.80/protocol.json`（1.19.83 のディレクトリは
  存在しない）。アイテム表は bedrock-1.19.80 タグから新規生成した
  専用表（`bedrock_item_ids_1_19_80.bin`。1.20.0 の表とは 450/1312
  行が違うため共用できない）。ブロック表は 1.20.50 世代を共用する
  （582 のパレットと 1.20.50 のパレットはランタイム ID が 1 個も
  一致しないが、589 でも同じ状況で対応済みのため新規不要。くわしくは
  BE-NOTES.md §3 を参照）。

### Bedrock × 内部サーバーのマトリクス

Bedrock クライアント（上記 63 プロトコル）が、内部 Java サーバー
763（1.20／1.20.1）・764（1.20.2）・765（1.20.3／1.20.4）・
766（1.20.5／1.20.6）のどれにも参加できる。Bedrock→Java の翻訳は
一度 765 相当に正規化してから既存の Java 側クロスバージョン翻訳
（`RegistryRemapper`／`PlayTranslator`）に渡すため、Java 側の
組み合わせはプロキシ側の 16 通りと共通の表で処理する。
`JavaBackendConnection` は `serverProtocol` を受け取り、
`PacketIds` から各プロトコルのパケット ID を引いて 763／764／
765／766 に対応している（Bedrock 側の新規実装ではない。流用）。

統合テスト済みの組み合わせ（本物のバニラに参加・スポーン・チャット・
移動・チャンク読み込み・アイテム形式の読み直しまで確認。
`BedrockMatrixIntegrationTest`）は代表の Bedrock 6 プロトコル
（589・594・630・671・685・2193）× 内部 4 サーバーの **24 通り**。
それ以外の Bedrock プロトコル（57 個）は、偽クライアントでの
Login→Start Game までの単体テスト（`BedrockSessionVersionTest`。
全 63 プロトコル）で形式を確認しているが、本物のバニラとの
統合テストはまだ行っていない。

| Bedrock | 763 (1.20.1) | 764 (1.20.2) | 765 (1.20.4) | 766 (1.20.5) |
|---|:-:|:-:|:-:|:-:|
| 589 (1.20.0) | ○ | ○ | ○ | ○ |
| 594 (1.20.10) | ○ | ○ | ○ | ○ |
| 630 (1.20.50) | ○ | ○ | ○ | ○ |
| 671 (1.20.80) | ○ | ○ | ○ | ○ |
| 685 (1.21.0) | ○ | ○ | ○ | ○ |
| 2193 (1.26.50) | ○ | ○ | ○ | ○ |
| その他 50 プロトコル | △ | △ | △ | △ |

○＝統合テスト済み（本物のバニラ）。△＝単体テストのみ（偽クライアントで
Login→Start Game まで。本物のバニラとの統合テストは未実施）。

- **内部 766 サーバーへの参加には、Select Known Packs への返事が要る。**
  1.20.5 の設定フェーズでサーバーが送る Select Known Packs（0x0e）に
  答えないと、サーバーが先に進まず Finish Configuration が来ない。
  `JavaBackendConnection` は空の一覧（知っているパックは無い）で返す。
  これが無いと内部 766 サーバーへの参加が止まる（今回のマトリクス
  テストで初めて分かり、修正した）。Ping（0x05）にも Pong で答える。

### バージョン別パケット形式（`BedrockVersion`）

Login のプロトコル番号からクライアントの Bedrock バージョンを判定し、
セッションごとにそのバージョン用の形式を使い分ける。核パケット
（Login / Play Status / Start Game / Text / Move Player / LevelChunk /
AddEntity / AddPlayer / SetEntityData / Resource Packs）の **ID は
291〜2225 の全バージョンで同じ**（minecraft-data の protocol.json
62 バージョン分と、419・408・407・390・389・388・361・354・340・332・313・291 は CloudburstMC Protocol の v419・v408・
v407・v390・v389・v388・v361・v354・v340・v332・v313・v291 コーデックで確認。増えたパケットは末尾の高番号に追加されるだけ）。
違うのは**形式**で、境界は次の通り（419・408・407・390・389・388・361・354・340・332・313・291 のみコーデックで確認、
それ以外はすべて protocol.json で確認）。

| パケット | 形式の境界 |
|---|---|
| StartGame | property_data・world_template_id は 1.19.0 以降（503 には無い）。チェックサム欄は 503 が block_pallete_checksum（単 l）、527 以降が block_pallette_checksum（二重 l）。editor_world は 1.19.10 以降（527 には無い）。permission_level は 1.19.10 以降 PermissionLevel（u8 マッパー）、527 は zigzag32。544 追加分（persona_disabled・custom_skins_disabled・chat_restriction_level・disable_player_interactions・client_side_generation）は 1.19.20 以降。emote_chat_muted は 1.19.60 以降。582 追加分（created_in_editor・exported_from_editor・block_network_ids_are_hashes）は 1.19.80 以降。server_controlled_sound は 1.20.0 以降。hardcore は 1.20.80 以降。level_id 前の識別子は 1.21.0〜1.21.80 で 3 つ（server／world／scenario）、1.21.90〜1.21.130 で 4 つ（＋owner）。itemstates は 1.12.0〜1.21.40（1.11.4 には無い）。movement_authority は 1.13.0〜1.21.80（1.12.1 には無い）。tick_death_systems は 1.21.100〜1.21.124 のみ。1.26.0 以降は識別子が末尾に移動し has_server_join_info が付く。編集者向け項目と is_chat_logging は 1.26.30 のみ（is_chat_logging は 1.26.40 でまた無くなる）。rewind_history_size・server_authoritative_block_breaking は 1.16.210 以降（1.14.60・1.16.10・1.16.61・1.16.101・1.16.201 には無い）。1.16.101・1.16.201 の StartGame は 57 フィールドで同一形式、1.16.210 は 59 フィールド。1.16.210 の StartGame は 59 フィールドで 1.16.220 と block_palette／block_properties の改名 1 件だけ違う（定義は同じ）。1.16.10（407）・1.16.61（408）の StartGame は experiments が無く、ブロックパレットは blockProperties 形式ではなく単一の NBT リストタグ形式。movement_authority は Boolean 形式だが、書く値 0（client）は zigzag32 の 0 とバイト同一のため分岐しない。itemDefinitions の要素形式（componentBased の有無）は itemstates を空で送るためワイヤに現れない。1.14.60（390）の StartGame はさらに v407 追加分（spawn_biome_type・custom_biome_name・education_production_id・limited_world_width／limited_world_height・is_new_nether・force_experimental_gameplay・server_authoritative_inventory）が無く、LevelSettings は 32 フィールド（407 は 39 フィールド）。edu_edition_offers は 390 が Boolean 形式だが、書く値 0 は VarInt の 0 とバイト同一のため分岐しない。1.14.41（389）も 390 と同一形式（StartGameSerializer_v388。BE-NOTES.md §29）。1.13.3（388）も同一形式（389 との差分は粒子表などで StartGame に触れない。BE-NOTES.md §30）。1.12.1（361）は LevelSettings が 31 フィールドで末尾の game version 文字列が無く、ブロックパレットは単一タグではなく配列形式（name／meta／id。中身は 1.12.0 世代の実測レガシーパレット 3,311 件。StartGameSerializer_v361。BE-NOTES.md §31）。1.11.4（354）は LevelSettings が 30 フィールドで only spawn v1 villagers が無く、itemstates 自体が無く、ブロックパレットは配列形式だが要素に id が無い（name／meta のみ。中身は 1.11.0 世代の実測レガシーパレット 3,183 件。StartGameSerializer_v332＋トップレベルは _v291。BE-NOTES.md §32）。1.10.1（340）も同一形式（実効シリアライザは 354 と全て同じ。ブロックパレットの中身は 1.10 系の実データが無いため 1.11.0 世代を代用する。BE-NOTES.md §33）。1.9.0（332）も同一形式（実効シリアライザは 340 と全て同じ。ブロックパレットの中身は 1.9 系の実データが無いため 1.11.0 世代を代用する。BE-NOTES.md §34）。1.8.1（313）は LevelSettings が _v291 のレイアウト（31 フィールド）に fromWorldTemplate／fromLockedWorldTemplate を追加した 33 フィールドで、放送モードは bool（NO_MULTI_PLAY のとき false）と zigzag32 の組になり trustingPlayers がある（StartGameSerializer_v313。332 より 3 バイト長い。ブロックパレットの中身は 1.8 系の実データが無いため 1.11.0 世代を代用する。BE-NOTES.md §35）。1.7.1（291）は LevelSettings が _v291 の純粋な 31 フィールドで、313 が末尾に追加した 2 bool が無い（StartGameSerializer_v291。313 より 2 バイト短い。ブロックパレットの中身は 1.7 系の実データが無いため 1.11.0 世代を代用する。BE-NOTES.md §36） |
| LevelChunk | subChunkCount（VarInt）とキャッシュ可否（Boolean）は 1.12.0 以降。1.11.4（354）は FullChunkDataSerializer_v291 が実効で chunkX／chunkZ（zigzag32）と payload のみ（BE-NOTES.md §32）。1.10.1（340）も同じ（BE-NOTES.md §33）。1.9.0（332）も同じ（BE-NOTES.md §34）。1.8.1（313）も同じ（BE-NOTES.md §35）。1.7.1（291）も同じ（BE-NOTES.md §36）。26.60（2225）は末尾に client_biome_update が付く |
| AddPlayer | 527 は旧形式（unique_entity_id・runtime_entity_id・能力系を直書きする 22 フィールド）。1.19.10 以降は abilities 配列形（unique_id・runtime_id・19 フィールド）。properties は 1.19.40 以降。1.14.60／1.16.10／1.16.61／1.16.101／1.16.201／1.16.210／1.16.220 は能力系を直書き（entity_id_self・runtime_entity_id、gamemode は無い。1.14.60／1.16.10／1.16.61／1.16.101／1.16.201／1.16.210 は 25 フィールドで x／y／z・speed_x／speed_y／speed_z を lf32 で直書きし、1.16.220 は position・velocity の vec3f 2 つ。ワイヤ等価。runtime_entity_id は 1.14.60・1.16.10・1.16.61・1.16.101・1.16.201 が varint・1.16.210 が varint64 だが、どちらも符号なし LEB128 で値が小さい限りバイト同一）。1.14.41（389）も 1.14.60 と同じ 25 フィールド形式（AddPlayerSerializer_v388。BE-NOTES.md §29）。1.13.3（388）も同じ（BE-NOTES.md §30）。1.12.1（361）は末尾の buildPlatform が無い（AddPlayerSerializer_v291。BE-NOTES.md §31）。1.11.4（354）も同じ（BE-NOTES.md §32）。1.10.1（340）も同じ（BE-NOTES.md §33）。1.9.0（332）も同じ（BE-NOTES.md §34）。1.8.1（313）も同じ（BE-NOTES.md §35）。1.7.1（291）も同じ（BE-NOTES.md §36） |
| AddEntity | body_yaw は 1.19.10 以降（527 には無い、11 フィールド）。先頭の ID 2 項目は 527 が entity_id_self・runtime_entity_id、1.19.10 以降が unique_id・runtime_id。properties は 1.19.40 以降。1.14.60／1.16.10／1.16.61／1.16.101／1.16.201／1.16.210 は 15 フィールドで x／y／z・speed_x／speed_y／speed_z を lf32 で直書きし、1.16.220 は position・velocity の vec3f 2 つ。ワイヤ等価。body_yaw は無い。1.14.41（389）も同じ 15 フィールド形式（BE-NOTES.md §29）。1.13.3（388）も同じ（実効は AddEntitySerializer_v313。BE-NOTES.md §30）。1.9.0（332）も同じ（実効は AddEntitySerializer_v313。BE-NOTES.md §34）。1.8.1（313）も同じ（実効は AddEntitySerializer_v313 自身。BE-NOTES.md §35）。1.7.1（291）は第 3 フィールドが identifier 文字列ではなく旧来の数値タイプ ID（VarUnsignedInt。AddEntitySerializer_v291。対応表は PocketMine-MP 3.4.0 の EntityIds 由来で、対応付けの無いエンティティは送らない。BE-NOTES.md §36） |
| AddPainting | coordinates は 1.7.1・1.8.1・1.9.0・1.10.1・1.11.4 と 1.16.201／1.16.210 が BlockCoordinates（x zigzag32・y varint・z zigzag32）、それ以外（1.12.1〜1.16.101 と 1.16.220 以降）が vec3f（lf32 × 3）。1.16.201／1.16.210 の BlockCoordinates は protocol.json の直接測定で、Cloudburst コーデック（AddPaintingSerializer_v361 は vec3f）とは記述が食い違う（判断待ち。BE-NOTES.md §28）。1.11.4（354）の BlockCoordinates は Cloudburst コーデック（AddPaintingSerializer_v332）の実測で、vec3f 化は _v361 の変更（BE-NOTES.md §32）。1.10.1（340）も同じ _v332 の実測で BlockCoordinates（BE-NOTES.md §33）。1.9.0（332）も _v332 自身の実測で BlockCoordinates（BE-NOTES.md §34）。1.8.1（313）は _v291 の実測で BlockCoordinates（_v332 と同一実装。BE-NOTES.md §35）。1.7.1（291）は _v291 自身の実測で BlockCoordinates（BE-NOTES.md §36） |
| PlayerList | verified は 1.16.201／1.16.210 では remove でも存在する（records_count 個の bool 配列）。それ以外は add のときのみ（1.14.60〜1.16.101 は PlayerListSerializer_v390 で remove は uuid のみ。1.16.201／1.16.210 については protocol.json と Cloudburst コーデックで記述が食い違う。BE-NOTES.md §28）。1.14.41（389）は add にも verified が無い（PlayerListSerializer_v388。verified の末尾ブロックは 390 で追加。BE-NOTES.md §29）。1.13.3（388）も同じ（PlayerListSerializer_v388 自身。BE-NOTES.md §30）。1.12.1（361）は add エントリが旧直書き形式（skinId／skinData／capeData／geometryName／geometryData をエントリに埋め込み、buildPlatform・Skin・teacher・host は無い。remove は uuid のみで同一。PlayerListSerializer_v291。BE-NOTES.md §31）。1.11.4（354）も同じ（BE-NOTES.md §32）。1.10.1（340）も同じ（BE-NOTES.md §33）。1.9.0（332）も同じ（BE-NOTES.md §34）。1.8.1（313）も同じ（BE-NOTES.md §35）。1.7.1（291）も同じ（BE-NOTES.md §36） |
| MovePlayer | 末尾の tick は 1.16.100 以降（1.14.60・1.16.10・1.16.61 には無い）。復号は先頭部分しか読まないため、受信側は tick の有無に影響されない |
| SetEntityData | 末尾の tick は 1.16.100 以降（1.14.60・1.16.10・1.16.61 には無い）。使えるフラグ・index の定義は世代で違う：1.10.1（340）は ENTITY_FLAGS が 80 件（74〜79 は 1.11.4 と番号がずれる。この実装が書くフラグは最大 63 まででワイヤに現れない）、ENTITY_DATA の index は 0〜101（102 TRADE_EXPERIENCE は 1.11.0 以降。この実装は使わない。BE-NOTES.md §33）。1.9.0（332）は ENTITY_FLAGS が 71 件（0〜70。340 との差は 71〜79 の有無だけ）、ENTITY_DATA の index は 0〜98（39 は INTERACT_TEXT で 340 の HAS_NPC と違うが、この実装は 39 を使わない。BE-NOTES.md §34）。1.8.1（313）は ENTITY_FLAGS が 71 件（0〜70。332 と同じ）、ENTITY_DATA の index は 0〜93（39 は INTERACT_TEXT で 332 と同じ。94〜98 は 332 で追加。この実装は使わない。BE-NOTES.md §35）。1.7.1（291）は ENTITY_FLAGS が 61 件（0〜60）、ENTITY_DATA の index は 0〜87（39 は INTERACT_TEXT で 313 と同じ。この実装が書くフラグは最大 63、index は 89／92 を書く場合があり、291 の定義範囲の外になるがワイヤ形式は不変。BE-NOTES.md §36） |
| MoveEntityDelta | 位置は 1.16.100 以降が FloatLE の絶対座標。1.7.1・1.8.1・1.9.0・1.10.1・1.11.4・1.12.1・1.13.3・1.14.41・1.14.60・1.16.10・1.16.61 は VarInt 差分形式だが差分のスケールが実測ソースで確認できないため、291・313・332・340・354・361・388・389・390・407・408 ではセッションが絶対座標の MoveEntity で代替する（MoveEntity の形式は 291・313・332・340・354・361・388・389・390・407・408 と 419 で同一） |
| UpdateAttributes | 属性の modifiers 配列は 1.19.20 以降。534 には無い。末尾の tick は 1.16.100 以降（1.14.60・1.16.10・1.16.61 には無い） |
| NetworkChunkPublisherUpdate | saved_chunks 配列は 1.19.20 以降。534 には無い |
| ResourcePacksInfo | has_addons は 1.20.70 以降。resource_pack_links は 1.20.30〜1.21.30。behaviour_packs・force_server_packs は 1.21.20 まで。world_template は 1.21.50 以降。disable_vibrant_visuals は 1.21.90 以降。texture_packs の件数は 26.40 以降だけ varint（それ以前は li16）。scriptingEnabled（has_scripts）とエントリの scripting は 332 以降で、1.8.1（313）には無い（ResourcePacksInfoSerializer_v291。BE-NOTES.md §35）。1.7.1（291）にも無い（_v291 自身。BE-NOTES.md §36） |
| ResourcePackStack | behavior_packs は 1.21.124 まで。has_editor_packs は 1.20.70 以降。game_version にはクライアントの表示名（例：`1.26.50`）を入れる。419 未満は旧形式で、experiments 配列が無く、game_version の前に legacy experiment フラグ（bool）が 1 つある。1.12.1（361）は game_version 自体が無く legacy experiment フラグが末尾（ResourcePackStackSerializer_v313。BE-NOTES.md §31）。1.11.4（354）も同じ（BE-NOTES.md §32）。1.10.1（340）も同じ（BE-NOTES.md §33）。1.9.0（332）も同じ（BE-NOTES.md §34）。1.8.1（313）も同じ（ResourcePackStackSerializer_v313 自身。BE-NOTES.md §35）。1.7.1（291）は legacy experiment フラグ自体が無く 2 つの配列で終わる（ResourcePackStackSerializer_v291。313 より 1 バイト短い。BE-NOTES.md §36） |
| Item（MobEquipment 等） | **ItemV4** は MobEquipment が 1.26.20 以降、MobArmorEquipment が 1.26.30 以降、AddPlayer／AddItemEntity が 1.26.40 以降。ItemV4 は network_id が li16・block_runtime_id が varint・has_stack_id が bool で、air も全項目を書く（extra 長 0）。MobArmorEquipment の 5 個目（body）は 1.21.20 以降で、Java の body（馬・オオカミの鎧）を入れる |
| InventoryTransaction（復号） | 26.30 で legacy_transactions が option 化・アイテムが ItemV4 化・action_type が zigzag32 化。26.40 で transaction_type・actions も option 化。26.60 で item_use_on_entity（hotbar_slot と held_item の間）と item_release（末尾）に hand（u8）が追加。1.14.60（390）には legacy_request_id が無く transaction_type から始まり、アクションも network_ids の無い v291 世代の基本形式（BE-NOTES.md §28）。1.14.41（389）も同一形式（BE-NOTES.md §29）。1.13.3（388）も同一形式（BE-NOTES.md §30）。1.12.1（361）も同一形式（BE-NOTES.md §31）。1.11.4（354）も同一形式（BE-NOTES.md §32）。1.10.1（340）も同一形式（BE-NOTES.md §33）。1.9.0（332）も同一形式（BE-NOTES.md §34）。1.8.1（313）も同一形式（BE-NOTES.md §35）。1.7.1（291）も同一形式（BE-NOTES.md §36） |
| 26.60（2225）の追加分 | StartGame に editor_level_migration_version（u8 mapper、exported_from_editor の直後。83 フィールド）。AddPlayer は links の直後・device_id の前に、AddEntity は末尾に passenger_of_block（option の PassengerOfBlockData。常に「なし」で書く）。LevelChunk の末尾に client_biome_update（bool、false で書く）。新規パケット 8 件（353〜360：matchmaking・stonecutter・set_passenger_of_block・cursor_item_drag・audio_content）はこの実装では使わない。Skin の play_fab_id は 2225 で PlayerRecord の add 側へ移ったが、Skin 形式の分岐は判断待ちのため未対応（BE-NOTES.md §24） |
| SubChunk | バージョン 9 のまま（2026 年 10 月時点の wiki・bedrock-rs で確認）。全バージョンで同じ |
| エンティティ種別 | Bedrock では文字列のため、バージョンごとの数値表は不要。1.21 以降の新エンティティは Java 1.20.x のバックエンドからは出てこない |

`BedrockVersion`（64 プロトコル＋ゲート判定）に境界を集約し、
`BedrockPackets` はバージョン別オーバーロードで符号化する。
従来の無引数オーバーロードは 1.20.50 形のまま残し、既存テストとの
互換性を保っている。

### バージョン別パレットの方針

- **StartGame の正規ブロックパレットは、1.20.50 のもの（14,127
  エントリ）を全バージョンで 1 つだけ送る。** PMMP BedrockData の
  `canonical_block_states.nbt` はバージョンごとに並びが変わる
  （1.20.0 と 1.20.50 では 12,512 エントリがずれている）が、
  StartGame で送ったパレットがそのまま「ランタイム ID＝パレット内の
  順番」として使われる（`block_network_ids_are_hashes=false`）ため、
  送った 1 つのパレットが全バージョンで自己無矛盾になる。バージョン別
  パレットの生成は不要と判断した。
  - 例外は 361（1.12.1）：パレットのワイヤ形式自体が配列形式
    （name／meta／id）で正規パレットの内容を流し込めないため、
    1.12.0 世代の実測レガシーパレット（PMMP BedrockData
    bedrock-1.12.0 タグから生成した 3,311 件）を送る
    （`BedrockLegacyBlockPalette`）。この場合、パレットの並びと
    チャンク翻訳側（正規パレット基準）のランタイム ID の整合は
    未確認である（BE-NOTES.md §31）。
  - 例外は 354（1.11.4）も同じ：配列形式だが要素に id が無く
    （name／meta のみ）、中身も 1.11.0 世代の実測レガシーパレット
    （PMMP BedrockData bedrock-1.11.0 タグから生成した 3,183 件）を
    送る（`BedrockLegacyBlockPalette.load1_11_0()`）。整合は
    未確認である（BE-NOTES.md §32）。
  - 例外は 340（1.10.1）も同じ：形式は name／meta のみの配列だが、
    1.10 系のパレット実データが存在しない（PMMP BedrockData
    bedrock-1.10.0 タグに required_block_states.json／
    block_id_map.json 自体が無い）ため、中身は 1.11.0 世代の
    実測レガシーパレット（3,183 件）を代用する
    （`BedrockLegacyBlockPalette.load1_11_0()`。340 自体の
    実測ではない）。整合は未確認である（BE-NOTES.md §33）。
  - 例外は 332（1.9.0）も同じ：形式は name／meta のみの配列だが、
    1.9 系のパレット実データが存在しない（PMMP BedrockData
    bedrock-1.9.0 タグに required_block_states.json／
    block_id_map.json 自体が無い）ため、中身は 1.11.0 世代の
    実測レガシーパレット（3,183 件）を代用する
    （`BedrockLegacyBlockPalette.load1_11_0()`。332 自体の
    実測ではない）。整合は未確認である（BE-NOTES.md §34）。
  - 例外は 313（1.8.1）も同じ：形式は name／meta のみの配列だが、
    1.8 系のパレット実データが存在しない（PMMP BedrockData
    bedrock-1.8.0 タグに required_block_states.json／
    block_id_map.json 自体が無い）ため、中身は 1.11.0 世代の
    実測レガシーパレット（3,183 件）を代用する
    （`BedrockLegacyBlockPalette.load1_11_0()`。313 自体の
    実測ではない）。整合は未確認である（BE-NOTES.md §35）。
  - 例外は 291（1.7.1）も同じ：形式は name／meta のみの配列だが、
    1.7 系のパレット実データが存在しない（PMMP BedrockData に
    bedrock-1.7.0 タグ自体が存在しない）ため、中身は 1.11.0 世代の
    実測レガシーパレット（3,183 件）を代用する
    （`BedrockLegacyBlockPalette.load1_11_0()`。291 自体の
    実測ではない）。整合は未確認である（BE-NOTES.md §36）。
- **リスク（正直に書く）：** 1.21 以降の新ブロックはこのパレットに無い
  ため、Java 1.20.x のバックエンドからはそもそも出てこない（出てくる
  ことは無い）。`block_pallette_checksum` は 0 のまま、パレットの
  `version` 欄（18100737）は古い値のままである。実機の Bedrock
  クライアントでこのパレットがどう受け取られるかは未確認で、特に
  26.x 系のクライアントで表示がずれる・切断される可能性は残る。
- **バイオーム ID は全バージョンで安定。** PMMP `biome_id_map.json`
  は 1.20.50 と 1.21.0 で完全一致し、1.26.30 でも `pale_garden`
  （193）と `sulfur_caves`（194）の追加だけでずれは無い。バイオーム表
  はバージョン別に用意していない。
- **チャンクのブロックランタイム ID・バイオームは 1.20.50 の表のまま**
  （`BedrockBlockRuntimeIds`・`BedrockBiomeIds`）。Java 1.20.x の
  ブロック状態 26,644 件すべてを写す表は変わらない。

## 構成

## 構成

```
  Bedrock 1.20.50 クライアント (630)
        │  UDP 19132 (RakNet)
        ▼
  BedrockServer (detergentmc-bedrock)
    ├─ RakNetCodec      … Unconnected Ping/Pong、Open Connection 1/2、
    │                      フレームセット、分割フレームの繋ぎ直し、ACK
    ├─ BedrockBatch     … 0xfe＋zlib のゲームパケット・バッチ
    ├─ BedrockLogin     … Login パケット＋JWT チェーンのパース
    ├─ FloodgateId      … XUID→UUID v3、ゲーマータグ→Java 名
    ├─ BedrockPackets   … Text / MovePlayer / StartGame / LevelChunk 等
    ├─ BedrockChunkTranslator … Java チャンク→Bedrock LevelChunk（下記）
    ├─ BedrockBlockRuntimeIds … Java ブロック状態 ID→Bedrock ランタイム ID の表
    ├─ BedrockBlockPalette … 正規ブロックパレット（StartGame 用）
    ├─ BedrockEntityTranslator … Java エンティティ→Bedrock エンティティ（下記）
    ├─ BedrockEntityTypeIds … Java エンティティ種別→Bedrock 識別子の表
    ├─ BedrockItemIds … Java アイテム→Bedrock ネットワーク ID の表
    └─ BedrockSession   … 接続ごとの状態機械（ログイン→リソースパック→プレイ）
        │  Java クライアントとして内部バニラへ接続
        ▼
  JavaBackendConnection … ハンドシェイク→ログイン→設定フェーズ→プレイ
        │  Java のパケット ID は PacketIds から引く（表の二重持ちなし）
        ▼
  内部バニラ (127.0.0.1:25566、offline-mode の信頼済み接続)
```

Java 側のプロキシ（25565）と Bedrock リスナー（19132）は
DetergentServer の起動時に並行して起動する。Bedrock のバインドに
失敗してもサーバー全体は落とさない（警告ログだけ）。

## 設定（`detergentmc.yml`）

```yaml
bedrock:
  enabled: true        # 既定 true。false なら Bedrock リスナーを起動しない
  port: 19132          # 既定 19132（UDP）
  motd: ""             # 既定 ""（空なら server.motd を使う）
  chunk-radius: 6      # 既定 6（1〜12）。プレイヤー周辺に送るチャンクの半径

floodgate:
  prefix: "."          # 既定 "."。Bedrock プレイヤーの Java 名の先頭に付く
```

`max-players` と `server-name` は Java 側の設定を流用する
（MOTD の人数・ワールド名に出す）。

## Floodgate 風の ID

- Bedrock プレイヤーには Java アカウントが無いため、Login パケットの
  JWT チェーンから `displayName`（ゲーマータグ）と `XUID` を抜き出す。
- UUID は **UUID v3**（名前ベース）で、`"detergentmc-bedrock:" + XUID`
  から作る。同じ XUID は常に同じ UUID になる（決定的）。Floodgate 本家の
  生成式とは別物であり、同じ XUID でも Floodgate の UUID とは一致しない。
- Java 名は `prefix + ゲーマータグ` を Java の 16 文字制限に合わせて整形
  する（`[A-Za-z0-9_]` 以外を落とし、16 文字で切り捨てる。既定は `.Steve`）。
- XUID が無い場合（Xbox Live にログインしていないクライアント）は
  ゲーマータグから UUID v3 を作る（`"detergentmc-bedrock:name:" + ゲーマータグ`）。
  XUID ありの場合と名前空間を分けているため衝突しない。

## ログインの流れ（Bedrock 側）

1. Unconnected Ping → Pong（MOTD `MCPE;...`）。サーバーリストに出る。
2. Open Connection Request 1 / 2 → Reply 1 / 2（RakNet バージョン 11、
   セキュリティなし、MTU は要求の長さ＋28 を 576〜1492 に収めたもの）。
3. Connection Request → Connection Request Accepted → New Incoming Connection。
4. Login（0x01）→ Play Status（login_success）＋ Resource Packs Info（空）。
5. Resource Pack Client Response（have all packs）→ Resource Pack Stack（空）。
6. Resource Pack Client Response（completed）＋ Java 側の Join Game →
   Start Game ＋ Play Status（player_spawn）。
7. プレイ：Text / Move Player / Player Auth Input / Command Request を
   Java 側へ翻訳し、Java 側のチャット・テレポートを Bedrock へ送り返す。

暗号化はしない。Server To Client Handshake（0x03）を送らないため、
クライアントは暗号化なしで進む（PocketMine などと同じ）。

## チャンク（世界が見える）

Bedrock クライアントに、スポーン周辺の地形が見える。Java 側（内部
バニラ）から届いたチャンクを Bedrock の LevelChunk に翻訳して送る。

### 仕組み

1. **Java チャンクの受信**：`JavaBackendConnection` が内部バニラからの
   Chunk Data（プレイ）と設定フェーズの Registry Data（`minecraft:worldgen/biome`
   の名前一覧・`minecraft:dimension_type` の min_y/高さ）・Join Game /
   Respawn（次元）を読む。次元が変わると minY・高さが更新される。
2. **翻訳**（`BedrockChunkTranslator`）：Java のセクション（パレット付き
   コンテナ）を読み、ブロック状態 ID を Bedrock ランタイム ID に変換して
   サブチャンク（バージョン 9）＋バイオームのペイロードを組み立てる。
   - **セクションのパレット付きコンテナの Data Array はバージョンで形式が違う。**
     769までは `長さ varint＋long[]`、770は `long[]` だけ（長さは
     bitsPerEntry とエントリ数から計算する。シングルパレット（bpe=0）も770では
     長さ varint（0）が無い）。`readPalettedContainer` は serverProtocol を見て
     770のとき長さ varint を読まない。実バニラ1.21.5のチャンクで確認済み
     （`BedrockMatrixIntegrationTest.matrixAgainst1_21_5` で発覚）。
     minecraft-data の protocol.json は chunkData を不透明な ByteArray として
     扱うため、この変更は protocol.json には現れない。
   - 列は常に次元いっぱい（オーバーワールド 24 サブチャンク Y -4..19、
     ネザー 8、エンド 16）。光データは送らない（Bedrock 側が明るさを計算する）。
   - Java のブロック順序（x | z<<4 | y<<8）を Bedrock の XZY 順
     （y | z<<4 | x<<8）に並べ替える。バイオームも同様。
   - 水没ブロックは層 1 に水（ランタイム ID 9304）を入れた 2 層で送る。
     水・溶岩そのものは通常のブロックとして送る。
   - ブロックエンティティ（チェスト等の中身）は送らない（見た目は普通の
     ブロック）。ボーダーブロックも 0 件。
3. **送信フロー**（`BedrockSession`）：Start Game の直後と、クライアント
   からの RequestChunkRadius（0x45）への応答で ChunkRadiusUpdated（0x46）
   と NetworkChunkPublisherUpdate（0x79、半径はブロック単位＝半径×16）を
   送る。**Java 側から届いたチャンクだけを送る**（届いていないチャンクは
   捏造しない）。送る順序はプレイヤーに近いものから。半径は
   `min(クライアントの要求, bedrock.chunk-radius)`（既定 6、最大 12）。
   プレイヤーが移動してチャンクが変わると、半径を送り直して新しく入った
   チャンクを送る。遠くなったチャンクを明示的に破棄する Unload パケットは
   送っていない（クライアント側の半径更新に任せる）。
4. **ブロック更新**：Java 側の Block Change / Multi Block Change を
   Bedrock の Update Block（0x15）に翻訳する。送り済みのチャンクの
   ものだけ送る（未送信チャンクの更新は送らない）。水没ブロックの
   更新は層 0 と層 1（水）の 2 パケットになる。
5. **分割送信**：LevelChunk 1 個（数〜数十 KB）や StartGame（パレット込み
   で約 2MB・圧縮後約 400KB）は MTU を超えるため、RakNet の分割
   フレーム（split flag 0x10）で送る。受信側の繋ぎ直しは前からある。

### Bedrock ランタイム ID（出典と選んだ形式）

- **正規パレット**：PMMP BedrockData の `canonical_block_states.nbt`
  （タグ `bedrock-1.20.50`、14,127 エントリ、CC0 1.0）をリソースとして
  同梱し、StartGame で全エントリを送る（`BedrockBlockPalette`）。
  ランタイム ID はこのファイル内での順番（offset）である。エントリは
  すべて `version == 18100737`（`CurrentBlockVersion`）であることを
  起動時に検証する。**minecraft-data の `blockStates.json` の並びは
  正規の順番ではないため、ランタイム ID には使っていない。**
- **Java→Bedrock 対応表**：`detergentmc-bedrock/tools/generate.py` が
  minecraft-data の Java 1.20.3 `blocks.json`（26,644 状態）と正規
  パレットを突き合わせて生成した表で、Java 1.20.4 のブロック状態
  **26,644 件すべて**（100%）を Bedrock ランタイム ID に写す
  （バイナリ表 `bedrock_block_runtime_ids_1_20_4.bin`。対応表にない
  ブロックは無いが、別プロトコル（763/764/766）のサーバーでは
  `RegistryRemapper` の対応表で 765 に正規化してから引く）。
  Java にあって Bedrock に無いもの（例：`minecraft:grass_block` は
  Bedrock では `minecraft:grass`、`minecraft:short_grass` は
  `minecraft:tallgrass`）は名前・性質の近い Bedrock ブロックに写す。
  性質が合わないもの（ベッド・バナーの色など）は色情報を落として
  無色のブロックに写す（詳しくは `tools/aliases.py` のコメント）。
  範囲外の状態 ID が来た場合は石（1774）に落とす（落とさない方針）。
- **バイオーム**：Java バイオーム 64 種を Bedrock バイオーム ID に写す
  （`BedrockBiomeIds`。出典は BedrockData `biome_id_map.json` と
  minecraft-data Bedrock 1.20.0 `biomes.json`）。Java 側の
  バイオーム名が分からない場合は平原（plains、ID 1）に落とす。
- **形式の出典**：LevelChunk（0x3a）・RequestChunkRadius（0x45）・
  ChunkRadiusUpdated（0x46）・NetworkChunkPublisherUpdate（0x79）・
  Update Block（0x15）の各パケット形式と、サブチャンク バージョン 9・
  パレット付きストレージの形式は、PrismarineJS minecraft-data
  Bedrock の `protocol.json`（36 バージョン分で確認。核パケットの
  ID・形式は 1.20.0〜26.51 で同じ）、wiki.vg「Bedrock Edition
  level format」、Minecraft Wiki「Pocket Edition level format」、
  Mojang の bedrock-protocol-docs（sub-chunk request ガイド）で確認した。
  1.20.40 だけ見ていた頃の「1.20.50 の記述が無いので 1.20.40 の形式で
  送る」という前提は、今回 36 バージョン分を確認して置き換えた。
- **StartGame の property data**：空の compound は varint NBT で
  `0x0A, 0x00, 0x00`（3 バイト）。従来の 4 バイト書きは誤りだったため
  修正済み。

## エンティティ（他プレイヤー・モブが見える）

Bedrock クライアントに、他プレイヤーとモブが見える。Java 側（内部
バニラ）から届いたエンティティパケットを Bedrock の
AddEntity / AddPlayer / AddItemEntity 等に翻訳して送る。

### 種別対応表と網羅率

- Java 1.20.4 のエンティティ種別 **126 種**（`minecraft:entity_type`。
  出典は ViaVersion Mappings `mapping-1.20.3.json` の `entities`。
  index がレジストリ ID）を Bedrock 識別子に写す
  （`BedrockEntityTypeIds`）。**直接対応 99、改名 14、代替 10、
  出さない 2（interaction・marker）、プレイヤー 1（AddPlayer 経由）**。
  異なる Bedrock 識別子は 113 種（代替の重複を除く）。
  バニラ 1.20.4 は設定フェーズで `minecraft:entity_type` を送らないため、
  この静的な表が唯一の出典である（実物のバニラで確認済み）。
- 改名 14：`zombified_piglin`→`zombie_pigman`、
  `evoker`→`evocation_illager`、`end_crystal`→`ender_crystal`、
  `eye_of_ender`→`eye_of_ender_signal`、`experience_bottle`→`xp_bottle`、
  `experience_orb`→`xp_orb`、`fishing_bobber`→`fishing_hook`、
  `trident`→`thrown_trident`、`firework_rocket`→`fireworks_rocket`、
  `tropical_fish`→`tropicalfish`、`villager`→`villager_v2`、
  `zombie_villager`→`zombie_villager_v2`、`evoker_fangs`→`evocation_fang`、
  `potion`→`splash_potion`。
- 代替 10：`spectral_arrow`→`arrow`、`furnace_minecart`／
  `spawner_minecart`→`minecart`、`giant`→`zombie`、
  `illusioner`→`evocation_illager`、`breeze`→`blaze`（Bedrock 1.20.50
  に breeze が無い）、`wind_charge`→`small_fireball`（同）、
  `block_display`／`item_display`／`text_display`→`armor_stand`
  （text_display の文はネームタグにして出す）。落とす種別は無い
  （interaction・marker 以外は必ず何かが出る）。
- サーバーが 763 / 764 / 766 の場合は `RegistryRemapper` 等で
  765 に正規化してから引く（表の二重持ちなし）。範囲外の種別 ID は
  ブタ（765 の 73）に落とす。
- アイテム：Java 1.20.4 のアイテム **1,312 件**を Bedrock ネットワーク ID
  に写す（`BedrockItemIds`）。**バージョン別**の表を 38 本のバイナリ
  （`bedrock_item_ids_<tag>.bin`、Java 765 順の（network_id・metadata）
  ペア）で持ち、クライアントの Bedrock バージョンごとに読む。出典は
  PMMP BedrockData の `required_item_list.json`（各バージョンのタグ）。
  生成は `detergentmc-bedrock/tools/generate_item_tables.py`。
  変種ブロックアイテム（苗木の種類・ベッドの色など）は、古い Bedrock
  では共有 ID＋metadata、新しい Bedrock では独自 ID で送る。
  網羅率（1,312 件中）：1.7.1 は **893**（68.1%、1.16.210 表を共用。
  291 の世代の PMMP データは bedrock-1.7.0 タグ自体が存在せずランタイム ID の実データが無いため代用で、291 自体の実測ではない）、
  1.8.1 は **893**（68.1%、1.16.210 表を共用。
  313 の世代の PMMP データ（bedrock-1.8.0 タグ）は item_id_map.json すら無くランタイム ID の実データが無いため代用で、313 自体の実測ではない）、
  1.9.0 は **893**（68.1%、1.16.210 表を共用。
  332 の世代の PMMP データ（bedrock-1.9.0 タグ）は item_id_map.json すら無くランタイム ID の実データが無いため代用で、332 自体の実測ではない）、
  1.10.1 は **893**（68.1%、1.16.210 表を共用。
  340 の世代の PMMP データ（bedrock-1.10.0 タグ）は item_id_map.json すら無くランタイム ID の実データが無いため代用で、340 自体の実測ではない）、
  1.11.4 は **893**（68.1%、1.16.210 表を共用。
  354 の世代の PMMP データ（bedrock-1.11.0 タグ）は旧形式のみでランタイム ID の実データが無いため代用で、354 自体の実測ではない）、
  1.12.1 は **893**（68.1%、1.16.210 表を共用。
  361 の世代の PMMP データ（bedrock-1.12.0 タグ）は旧形式のみでランタイム ID の実データが無いため代用で、361 自体の実測ではない）、
  1.13.3 は **893**（68.1%、1.16.210 表を共用。
  388 の世代の PMMP データ（bedrock-1.13.0 タグ）は旧形式のみでランタイム ID の実データが無いため代用で、388 自体の実測ではない）、
  1.14.41 は **893**（68.1%、1.16.210 表を共用。
  389 の世代の PMMP データ（bedrock-1.14.0 タグ）は旧形式のみでランタイム ID の実データが無いため代用で、389 自体の実測ではない）、
  1.14.60 は **893**（68.1%、1.16.210 表を共用。
  PMMP BedrockData に bedrock-1.14.60 タグは無く、bedrock-1.14.0 タグは旧形式のみで 1.16.0 の同名ファイルとも中身が違うため代用で、390 自体の実測ではない）、
  1.16.10 は **893**（68.1%、1.16.210 表を共用。
  407 を共有する 1.16.0 自体の PMMP データは旧形式のみで required_item_list.json が存在しないため代用で、407 自体の実測ではない）、
  1.16.61 は **893**（68.1%、1.16.210 表を共用。
  408 自体の PMMP runtime データ（required_item_list.json）は存在しないため代用で、408 自体の実測ではない）、
  1.16.101 は **893**（68.1%、1.16.210 表を共用。
  1.16.100 自体の PMMP データの実測で、1.16.100 は 419 を共有するリリース版そのもの。ただし 1.16.101 固有のアイテム差分が無いことの直接の証拠は無いため未確認）、
  1.16.201 は **893**（68.1%、1.16.210 表を共用。
  1.16.200 自体の PMMP データの実測で、1.16.200 は 422 を共有するリリース版そのもの。ただし 1.16.201 固有のアイテム差分が無いことの直接の証拠は無いため未確認）、
  1.16.210 は **893**（68.1%、1.16.210 表を共用。
  1.16.210 自体の PMMP データの実測）、
  1.16.221 は **893**（68.1%、新規生成の 1.16.210 代用表。
  1.16.220 自体の PMMP データは無く、431 を共有する 1.16.220 の実測ではない）、
  1.17.2 は **999**（76.1%、新規生成の 1.17.0 表。
  1.17.0 自体の PMMP データの実測）、
  1.17.11 は **1,016**（77.4%、新規生成の 1.17.10 表。
  1.17.10 自体の PMMP データの実測）、
  1.17.34 は **1,020**（77.7%、1.17.40 表を共有。
  1.17.30 自体の PMMP データの実測で候補表がバイト同一）、
  1.17.41 は **1,020**（77.7%、新規生成の 1.17.40 表。
  1.17.41 自体の PMMP データは無く、471 を共有する 1.17.40 の実測）、
  1.18.2 は **1,021**（77.8%、新規生成の 1.18.0 表。
  1.18.1／1.18.2 自体の PMMP データは無く、475 を共有する 1.18.0 の実測）、
  1.18.12 は **1,029**（78.4%、新規生成の 1.18.10 表。
  1.18.11 自体の PMMP データは無く、486 を共有する 1.18.10 の代用）、
  1.18.33 は **1,046**（79.7%、新規生成の 1.18.30 表）、
  1.19.2 は **1,097**（83.6%、新規生成の 1.19.0 表）、
  1.19.11 は **1,098**（83.7%、1.19.41 と同じ表）、
  1.19.20 は **1,098**（83.7%、1.19.41 と同じ表）、
  1.19.22 は **1,098**（83.7%、1.19.41 と同じ表）、
  1.19.31 は **1,098**（83.7%、1.19.41 と同じ表）、
  1.19.41 は **1,098**（83.7%）、
  1.19.51 は **1,125**（85.8%）、
  1.19.62 は **1,132**（86.3%）、
  1.19.70 は **1,153**（87.9%）、
  1.19.83 は **1,204**（91.8%）、
  1.20.0〜1.20.40 は **1,244**（94.8%）、
  1.20.50 は **1,270**、1.20.60〜1.21.0 は **1,272**、
  1.21.20〜1.21.30 は **1,274**、1.21.40 以降は **1,275**。
  写せないものは石（1）に落とす。古いバージョンで無いものは
  1.21 系の凝灰岩ハーフブロック類・銅格子・電球・クラフター・
  ブリーズのスポーンエッグ等で、Java 側に無かったり Bedrock 側に
  まだ無かったりするもの。1.21 以降の新アイテム（トライアルキー等）
  は Java 1.20.x のバックエンドからは出てこない。
  - **負の network_id は有効な番号である。** ブロック系は zigzag32 で
    負の値になる（例：花崗岩 -590）。以前の実装は負の値をすべて石に
    置き換える誤りがあり、約 625 アイテムが石になっていた。今回の作業で
    修正した（未対応は -1 だけ）。
  - **1.26 系のアイテム ID は PMMP と minecraft-data で食い違う。**
    PMMP の 1.26.30 版 `required_item_list.json`（version 2 レジストリ）
    ではダイヤの剣は 347・盾は 387 だが、minecraft-data の
    `items.json`（1.26.51）では 318・358 を載せている。1.20.50 では
    両者は 1,461／1,461 で完全一致するため、食い違いは 1.26 系の
    レジストリ更新で生じたものと考えられる。この表は PMMP 側に従った。
    どちらが実機の Item パケットで正しいかは、実機の Bedrock
    26.x クライアントで未確認。確認できたらこの節を修正する。
  - 従来の静的表（Microsoft Learn「Default Minecraft Item Listings」、
    1.26 世代の並び）は `networkId(int)`・`isMapped(int)` の
    フォールバックとして残してあり、bin が読めないバージョンでは
    そちらを使う。
- アイテム→ブロック：1,312 件のうち **911 件**にブロックのランタイム ID
  を付ける（`BedrockItemBlockIds`。Java のブロック状態表から引く）。
  落ちたブロックアイテムが 3D で見える。代表状態は各ブロックの最初の
  状態 ID（向き等が既定と違う場合がある）。

### Java→Bedrock の翻訳（一覧）

| Java | Bedrock |
|---|---|
| Spawn Entity | AddEntity（属性に体力 1 件） |
| 他プレイヤーの出現 | PlayerList add（空スキン）＋ AddPlayer。gamemode は Player Info Update から（観戦は 6） |
| Spawn Experience Orb | SpawnExperienceOrb（位置＋個数） |
| Entity Metadata | SetEntityData（下記の翻訳） |
| 相対移動・移動＋向き・向きのみ | MoveEntityDelta（他プレイヤーは MovePlayer mode 0） |
| テレポート | MoveEntity（他プレイヤーは MovePlayer mode 2） |
| 速度 | SetEntityMotion |
| 頭の向き | MoveEntityDelta（頭の向きのみ） |
| Remove Entities | RemoveEntity（プレイヤーは PlayerList remove も） |
| Update Attributes（最大体力・移動速度） | UpdateAttributes＋SetEntityData（体力）。※実物のバニラ 1.20.4 は参加直後のパケットに movement_speed しか含まない（count=1）。最大体力は属性が変わったとき（体力増加等）に送られる。体力そのものは Entity Metadata で届く |
| Entity Equipment | MobEquipment＋MobArmorEquipment（蓄積して全部送り直す） |
| Entity Effect | MobEffect（追加・除去） |
| Entity Status（2＝hurt、3＝death） | EntityEvent（hurt_animation／death_animation） |
| Set Passengers | SetEntityLink（乗り・降り） |
| Attach Entity（リード） | SetEntityData の lead_holder_eid |

- **ランタイム ID は Java エンティティ ID と同じ値**（決定的・衝突なし）。
  Remove で解放、切断で全解放する。自分自身（Bedrock プレイヤー本人）は
  エンティティとして送らない。
- **出現が条件付きのもの**：アイテムは中身（スロット）が、絵画は題名が、
  他プレイヤーは名前（Player Info Update）が届いてから送る。
  AddItemEntity / AddPainting は後から中身を変えられないため、条件が
  揃うまで待つ。バニラは出現と同じティックにメタデータを送るため、
  通常はすぐ出る。
- **出現前（Start Game 前）に届いたエンティティパケットは溜めて、
  Start Game の後に送る**（チャンクと同じ方式）。

### メタデータ翻訳（一覧）

- 共通：flags（燃えている・しゃがみ・ダッシュ・泳ぎ・不可視・
  エリトラ滑空）、air、custom name→nametag（＋表示有無）、silent、
  no gravity（重力フラグを外す）、pose（寝る→sleeping）、手の状態、
  体力→health、ポーション色、矢の本数、吸収、ベッド位置、mob flags。
- 種別別：ゾンビ等の赤ちゃん、ブタの鞍、ヒツジの色・毛刈り、
  オオカミの座る・怒る・飼いならし・首輪、ネコの種類・首輪、
  村人の職業、クリーパーの状態・帯電・点火、エンダーマンの持つブロック、
  スライム／マグマキューブの大きさ、ウサギの種類、シュルカーの開き、
  スニッファーの状態、ヤギの角、フグの大きさ、ウィザーの無敵時間、
  馬の種類・鎧の有無、トロッコの表示ブロック、ボートの木材、
  テキストディスプレイの文（ネームタグ化）、TNT の導火線、
  プレイヤーの追加ハート・スコア・スキンパーツ・利き手。
- 通せないもの：766 の成分付きスロット（後ろが読めないためアイテムは
  石表示になる場合がある）、落下ブロックのブロック種別
  （Bedrock の AddEntity に欄が無い）、アイテムフレームの表示アイテム
  （Bedrock に欄が無い。空に見える）、絵画の向きは best-effort、
  earth/wind/water/fire の絵画（1.21 追加）は Kebab にする。

### Bedrock→Java の翻訳（一覧）

- **InventoryTransaction（item use on entity）→ Use Entity**
  （0＝インタラクト、1＝攻撃）。Bedrock プレイヤーがモブを殴れる・
  村人等に話しかけられる。interact-at の座標は使わず、Java の
  interact（action 0）で送る。
- **PlayerAction（しゃがみ開始／終了、ダッシュ開始／終了）→
  Entity Action**（0／1／3／4）。
- Interact パケット（0x21）は無視する。leave_vehicle はしゃがみ経由で
  Java が処理し、攻撃・インタラクトは InventoryTransaction 経由で来る。
- 形式の出典：AddPlayer（0x0c）・AddEntity（0x0d）・RemoveEntity（0x0e）・
  AddItemEntity（0x0f）・MoveEntity（0x12）・MovePlayer（0x13）・
  EntityEvent（0x1b）・MobEffect（0x1c）・UpdateAttributes（0x1d）・
  InventoryTransaction（0x1e）・MobEquipment（0x1f）・
  MobArmorEquipment（0x20）・PlayerAction（0x24）・SetEntityData（0x27）・
  SetEntityMotion（0x28）・SetEntityLink（0x29）・PlayerList（0x3f）・
  SpawnExperienceOrb（0x42）・MoveEntityDelta（0x6f）・AddPainting（0x16）
  の各パケット形式と、MetadataDictionary・Entity Data のキー・
  フラグの並びは、PrismarineJS minecraft-data Bedrock の
  `protocol.json`（**36 バージョン分で確認。核パケットの ID・形式は
  1.20.0〜26.51 で同じ**）と Microsoft Learn「Default Minecraft
  Entity Listings」「Default Minecraft Item Listings」で確認した。
  1.20.40 だけ見ていた頃の前提は、今回 36 バージョン分を確認して
  置き換えた。Java 側の
  Entity Metadata の型番号・index は wiki.vg「Entity metadata」
  （1.20.2）とプロジェクト内のプロキシ翻訳（ChunkTranslator）で確認した。

## 翻訳するもの／しないもの

| 方向 | 通るもの | 落とすもの |
|---|---|---|
| Bedrock → Java | チャット（Text）、コマンド（Text の `/` 始まり・Command Request）、移動（Move Player / Player Auth Input）、チャンク要求（RequestChunkRadius→半径の返信）、**攻撃・インタラクト（InventoryTransaction→Use Entity）、しゃがみ・ダッシュ（PlayerAction→Entity Action）** | インベントリ操作、ブロック破壊・設置、その他すべて |
| Java → Bedrock | チャット（System Chat / Player Chat。送信者名は Player Info Update から解決）、テレポート（Synchronize Player Position → Move Player）、切断、Keep Alive 応答、**チャンク（LevelChunk。スポーン周辺・移動時の読み込み）**、**ブロック更新（Update Block。送り済みチャンクのみ）**、**エンティティ（AddEntity / AddPlayer / AddItemEntity / SpawnExperienceOrb / SetEntityData / 移動・テレポート・装備・エフェクト・乗騎・リード）** | インベントリ、ボスバー、スコアボード、ブロックエンティティの中身、光データ、その他すべて |

Bedrock クライアントにはスポーン周辺の地形が見え、他プレイヤー・モブも
見えて動く。ログイン・スポーン・チャット・移動・チャンク読み込み・
エンティティの出現／移動／消滅／名前・体力の表示・攻撃・操作が
プロトコルレベルで通ることが今回の到達点である。

## セキュリティ（Xbox 署名検証なし）— 必読

**JWT チェーンの署名は検証していない。** `BedrockLogin` はチェーンの形
（JWT があり、末尾の payload に `extraData` があること）だけを確認し、
中身を読み取るだけである。検証状態は `NOT_VERIFIED_TRUSTED`
（開発用の信頼済み扱い）として記録される。

そのため、online-mode のサーバーでこのまま運用すると、XUID と
ゲーマータグを自称するだけの偽クライアントが、他人の XUID になりすまして
参加できてしまう。Floodgate 本来の前提である「Xbox Live の署名で
裏付けられた XUID」が無いためである。

- 身内・開発用のサーバー以外での運用は、Xbox 署名検証（Mojang の公開鍵
  によるチェーン検証）が入るまで推奨しない。
- `server.online-mode: true` でも、Bedrock プレイヤーは Java アカウント
  なしで参加できる（それが Floodgate 風機能の目的である）。Java 側の
  online-mode 認証は Bedrock プレイヤーには適用されない。
- 内部バニラは `127.0.0.1` のみの offline-mode（信頼済み接続）で動くため、
  Bedrock 層を迂回して内部ポートに直接繋がれないことが前提である。
  内部ポート（25566）を外部に公開してはいけない。

## NetherNet対応の設計

Bedrock 26.60（プロトコル 2225、2026-10-27）で RakNet は廃止され、
トランスポートが WebRTC ベースの **NetherNet** に置き換わった。
DetergentMC は **2225 のパケット形式には対応済み**（2026-11-18、
BE-NOTES.md §24）だが、**NetherNet トランスポート（リスナー）は
未実装**である。RakNet 経由で 2225 の Login が来たクライアントには
「パケット形式は対応済み、NetherNet トランスポートは未実装」の
切断メッセージを返す。以下は調査結果と、実装するときの設計案である
（2026-11-18 に再調査し、旧版の誤りを訂正した）。

### NetherNet とは何か（公開資料で確認した範囲）

- **WebRTC ベースのトランスポート**で、Microsoft のシグナリングを
  経由して接続する（GeyserMC の発表、2026-10-08）。ゲームのデータ自体
  は DTLS  over UDP で流れる。
- **Mojang の変更履歴の表現は資料ごとに違う。** 26.50 の変更履歴は
  「NetherNet is now the default transport used by dedicated servers」、
  26.60 の変更履歴は「RakNet is deprecated」とだけ書く。「26.60 の
  クライアントは RakNet で繋がらなくなる」という言い方は GeyserMC の
  発表によるもので、Mojang の公式文言ではない。GeyserMC は
  ビルド 1237 以降で NetherNet に対応し、ビルド 1273（10-26）で
  26.60 に初対応、ビルド 1280（10-31）で RakNet 専用トランスポートを
  廃止した（`transport: nethernet|both`）。BedrockConnect 1.72.0
  （10-27）も NetherNet に対応している。
- クライアントは 26.40 から NetherNet を使え、26.50 で完全に、
  26.60 から必須になる（GeyserMC の解説による）。
- **専用サーバーへの直接接続の仕組み（Mojang 公式の NetherNet HTTP
  Signaling ガイドによる）**：シグナリングは Microsoft の中央
  サービスではなく、**サーバー自身が TCP（既定 19132）で HTTP(S)
  エンドポイントを提供する**。クライアントは `GET /v1/join` で
  サーバー情報 JSON を取り、full ICE（trickle 無効・STUN／TURN 無し・
  host 候補のみ）で作った SDP offer を `POST /v1/join/{networkId}`
  （`application/sdp`、1 回のみ）に送り、サーバーが SDP answer を
  返す。以後は ICE → DTLS → SCTP で直接通信し、データチャンネルは
  Reliable と Unreliable（`maxRetransmits: 0`）の 2 本。answer には
  運営者の P-384 鍵による `a=identity`（自己署名 JWT＋フィンガー
  プリント JWS）が必須で、平文 HTTP では初回確認（TOFU）、HTTPS
  では不要になる。
- **LAN 発見は別経路**：UDP 7551 番ポートで version-7 の広告を出し、
  trickle ICE でシグナルを運ぶ。IP 指定の専用サーバー接続には
  関係しない。Xbox Live／Realms／フレンド参加の WebSocket
  シグナリング（Microsoft のサービス経由）も専用サーバー用途では
  実装不要。
- **フレーミングの違い**：NetherNet では RakNet の `0xFE` バッチ
  マーカーとアプリ層暗号化が無くなり（DTLS が代替。暗号化
  ハンドシェイクの ServerToClientHandshake は使われない）、代わりに
  データチャンネルのメッセージ先頭に 1 バイトの断片化ヘッダが付く。
  バッチ内部の長さ前置・圧縮マーカーを残すかどうかは出典間で
  一致せず**未確認**（実装時に Cloudburst のパイプラインか実機
  キャプチャで裏を取ること）。
- Node.js の実装は npm パッケージ `nethernet`（PrismarineJS、
  MIT、Node 24+）として公開されている。ただしその `Server` は
  HTTP シグナリングのホスティングを実装していない。

### トランスポートを実装しない理由と、実現可能な道筋（2026-11-18 再調査）

- **（旧版の訂正）Java の NetherNet ライブラリは実在する。**
  CloudburstMC/Network の `nethernet` ブランチに Netty 製モジュール
  `netty-transport-nethernet`（＋`netty-external-signaling`、
  Apache-2.0）があり、GeyserMC・WaterdogPE・PowerNukkitX 等が
  実運用している。WebRTC 実装は libdatachannel-java
  （`dev.opencollab:libdatachannel-java-arch-detect`、JNI ネイティブ）。
  フォーク系統の Kas-tle/NetworkCompatible は Maven Central に
  安定版がある（`dev.kastle.netty:netty-transport-nethernet:1.7.0` ＋
  `dev.kastle.webrtc:webrtc-java:1.0.3`）。
- **残る壁は依存ライブラリの追加承認と検証環境である。** WebRTC の
  ネイティブライブラリは必須で、DetergentMC の方針（ダウンロードと
  インストールは先に確認）により未承認のままでは取り込めない。
  また、この VM では JVM の TCP が遮断され実機の 26.60 クライアント
  も無いため、取り込んでも接続検証ができない。動くかどうかを
  確かめる手段が無いまま「対応した」とは言わない。
- そのため現時点では、パケット層の対応＋この設計＋依存の判断待ち
  までを到達点とする。

### 将来実装するときに必要な部品

1. **トランスポート層**：CloudburstMC/Network の
   `netty-transport-nethernet`（または Kas-tle 系の Maven Central 版）
   を依存に追加し（**要・承認**）、その Netty チャンネルから届く
   バッチを `BedrockSession` のゲーム層へ渡す。ICE／DTLS／SCTP は
   ライブラリ側（libdatachannel-java 等のネイティブ）が担う。
   セッション側の最小の切り口は、受信入口をフレームセット単位から
   ペイロード単位へ変え、`handlePayload` 内の RakNet 制御分岐と
   送信フレーミング（`sendFrame`）をトランスポート境界の裏へ
   移すこと（実コードの調査結果は BE-NOTES.md §24）。
   `BedrockBatch` と `handleGamePacket` 以降のゲーム層は
   トランスポート非依存でそのまま使える。
2. **シグナリング**：専用サーバー向けはサーバー自身の HTTP(S)
   シグナリングで、Cloudburst の実装では `NetherNetHTTPServerSignaling`
   と `OperatorIdentity`（P-384 鍵、`identity.pem`）が同梱される。
   サーバーの登録や Microsoft のシグナリングは不要。LAN 発見
   （UDP 7551）と NXS（外部シグナリング）は任意の後付け。
3. **認証**：プレイヤーの identity は SDP offer の `a=identity`
   で届き、ICE／DTLS の前に検証できる（Cloudburst では検証済み
   プレイヤーがチャンネルに紐付く）。Login パケットの JWT チェーン
   自体の形式が変わるという情報は無い（未確認）。暗号化
   ハンドシェイクは踏まないため、`BedrockSession` のログイン後の
   流れは「暗号化なし」の前提で組み直す必要がある。
   Xbox 署名検証（未実装のまま）が入るまでは、なりすましの
   リスクは RakNet 世代と変わらない。
4. **パケット層**：2225 の形式対応は完了済み（この文書の
   「26.60（2225）の追加分」を参照）。残る未確認は NetherNet 上の
   バッチ内部フレーミング（長さ前置・圧縮マーカーの有無）だけで、
   実装時に Cloudburst のパイプラインか実機キャプチャで裏を取る。
5. **検証手順**：実機の Bedrock 26.60／26.62 クライアントで
   「サーバーリスト／直接接続で参加できるか・チャットと移動が
   通るか」を確認する。偽クライアントだけでは不十分で、DTLS まで
   含めた偽実装では実機の挙動は確かめられない。

**工数の見立て**：ライブラリ採用なら、依存の取り込み＋セッション
境界の切り出し＋実機検証が中心になる（自作 WebRTC が前提だった
旧版の「数週間規模」という見立ては、ライブラリ実在の判明により
前提が変わった。正確な見積もりは依存の版と配布形態を決めてから）。

## 残る制限（正直な一覧）

- **本物の Bedrock クライアントでの確認は未実施。** 検証は偽 Bedrock
  クライアント（生 UDP）と本物のバニラ 1.20.4 の統合テストまでで、
  実機の Bedrock（Xbox / スマホ / PC / Switch 等）では一度も試していない。
  特に Start Game の末尾フィールドと RakNet アドレスの符号化は
  ベストエフォートであり、実機で切断される可能性がある。
- **RakNet の信頼性は未完成。** ACK は送るが、NACK への再送はしない。
  分割フレームの繋ぎ直しはある（送信側の分割も今回入れた）。パケットロスが
  多い環境では Login やチャットが届かないことがある。タイムアウトによる
  再送・切断判定も未実装（Connected Ping への応答はある）。
- **暗号化なし。** Bedrock 側の通信は平文である（上記の通り）。
- **Xbox 署名検証なし。** 上記のセキュリティ節を参照。
- **チャンクは送れるが、未検証の部分が多い。** スポーン周辺の地形・
  移動時の読み込み・ブロック更新はプロトコルレベルで確認済みだが、
  実機の Bedrock では未確認。特に StartGame で送る正規パレット
  （約 2MB・分割フレーム送信）は今回初めて入れたもので、実機がどう
  受け取るかは不明。LevelChunk の末尾（border 0・ブロックエンティティ 0）
  やバイオームの並びも公開資料からの実装で、実機では表示が崩れる・
  切断される可能性がある。
- **ブロックエンティティは送らない。** チェスト・看板・ベッド等の
  中身・文字は Bedrock 側に届かず、見た目は普通のブロックになる。
- **光データは送らない。** Bedrock 側が明るさを計算する前提だが、
  実機での見え方は未確認。
- **遠くなったチャンクの Unload は送らない。** クライアント側の半径
  更新に任せている。古いチャンクが画面に残るかどうかは実機で未確認。
- **水・溶岩・葉の扱い**：水没ブロックは層 1 の水で送る。水・溶岩は
  通常のブロックとして送る（流れの表現は Bedrock 側の描画に任せる）。
  葉の透過などは送ったブロック状態の描画に任せ、特別な処理はしない。
- **エンティティは対応済みだが、未検証の部分が多い。** 他プレイヤー・
  モブ・落ちたアイテム・経験値オーブ・矢等の出現・移動・消滅・名前・
  体力・攻撃・操作はプロトコルレベルで確認済みだが、実機の Bedrock
  では未確認。詳しくは下記のエンティティまわりの制限を参照。
- **エンティティまわりの制限：** 落下ブロックのブロック種別は送れない
  （Bedrock の AddEntity に欄が無い。既定のブロックに見える）。
  アイテムフレームの表示アイテムは送れない（空に見える）。
  766 サーバーの成分付きアイテム（エンチャント・名前付き等）は石に見える
  場合がある。絵画の向きは best-effort で、earth/wind/water/fire
  （1.21 追加）は Kebab にする。763 の属性キー番号
  （0＝最大体力）は minecraft-data の並びによる推定で、実物のバニラ
  1.20.1 では未検証。Set Passengers（乗騎）・Attach Entity（リード）の
  パケット形は偽 Java サーバー相手の単体テストで確認したが、実物の
  バニラでは騎乗・リードの発生条件を作れなかったため未検証。
  他プレイヤーのゲームモード変更は Bedrock に反映されない
  （AddPlayer 時のみ）。
- **インベントリ未対応。** アイテムの使用・インベントリ操作は無い。
  ブロックの破壊・設置は Bedrock 側からはできない（Java 側の破壊・
  設置の反映は Update Block で送る）。
- **Start Game の Itemstates が空。** 全アイテムの一覧を送っていないため、
  minecraft-data の注記どおりモバイル・クライアントがクラッシュする
  可能性がある。
- **リソースパックなし。** Bedrock 側にはパックを要求しない（空の
  Resource Packs Info / Stack を送る）。Java 側の DetergentMC パック
  は Bedrock には出ない。
- **対応 Bedrock は 1.18.33〜26.52（プロトコル 503〜2193）で、
  26.60／26.62（2225）は NetherNet のため未対応。**
  26.60／26.62 のクライアントが来た場合は「NetherNet には未対応」
  の切断メッセージを返す（上記の「NetherNet対応の設計」を参照）。
  対応プロトコル以外（504〜526・528〜533・535〜543・546〜553・555・556・558・559・2194・2225 以外の未確認番号）も
  切断する。MotD には 630／1.20.50 を出すため、サーバーリスト上は
  1.20.50 のサーバーに見える。
- **スキンは送らない。** Bedrock のスキン情報は Java 側へ渡していない
  （Java 側では既定スキンになる）。
- **テスト環境の注意：** このサンドボックスでは UDP の送信が禁止
  されているため、UDP 経由のテスト（BedrockServerTest の Ping・
  全体フロー、BedrockIntegrationTest）はスキップされる。ルーティング
  自体は `routingWithoutNetwork` とセッション単体テストで検証している。
  UDP が使える環境ではこれらのテストが実行される。

## テスト

- `detergentmc-bedrock` の単体テスト：Bedrock VarInt、MOTD 生成・パース、
  RakNet（Ping/Pong、Open Connection、フレームセット、分割フレームの
  繋ぎ直し・送信用の分割）、バッチ、Login（JWT チェーン）、Floodgate 式 ID
  （決定的 UUID・名前整形・XUID なし時の扱い）、Text / MovePlayer /
  StartGame（正規パレット付き）/ LevelChunk / ChunkRadius / UpdateBlock の
  符号化、Java チャンク→Bedrock LevelChunk の翻訳（既知の地形パターンで
  デコードして位置・種類を確認）、ランタイム ID 表（26,644 件・重複なし・
  範囲外なし・水没フラグ）、正規パレット（14,127 エントリ）、
  Java バックエンド（偽 Java サーバー相手のログイン〜設定フェーズ〜
  プレイ、Registry Data・Chunk Data・Block Change・Multi Block Change・
  Respawn の読み取り）、セッション（偽 Transport＋偽バックエンドでの
  全フロー、スポーン周辺チャンクの送信・半径要求への返信・ブロック更新・
  移動時の読み込み）、サーバー（UDP ループバック。送信が禁止された
  環境ではスキップ）。
- `detergentmc-core` の統合テスト（`BedrockIntegrationTest`）：偽 Bedrock
  クライアント→本物のバニラ 1.20.4 で、参加（`.Steve`）・
  チャット・移動・**スポーン周辺チャンク（LevelChunk が届き、地形ブロックを
  含むこと）**を確認する。UDP が使える環境でのみ実行される。
- `detergentmc-core` の統合テスト（`BedrockChunkIntegrationTest`）：
  UDP を使わず、Java バックエンドを本物のバニラ 1.20.4 に直接つなげ、
  届いたチャンクを翻訳してスポーン地点の地面ブロックが読めることを
  確認する。UDP が禁止された環境でも実行される。
- `detergentmc-core` の統合テスト（`BedrockEntityIntegrationTest`）：
  UDP を使わず、2 人の偽 Java クライアントを本物のバニラ 1.20.4 に
  参加させ、片方に他方の Player Info Add・Spawn Entity（プレイヤー）・
  Entity Metadata（体力 20）・移動・自分の Update Attributes
  （移動速度 0.1）・切断時の Remove Entities／Player Info Remove が
  届くことを確認する。実物のバニラ 1.20.4 は参加直後の Update
  Attributes に movement_speed しか含まない（count=1。生バイトで
  2026-11-10 に確認。最大体力はこの時点では送られない。体力は
  Entity Metadata で届く）。モブの /summon は偽クライアントが op
  ではないため使えず、モブの Spawn Entity は偽 Java サーバー相手の
  単体テストで確認する。
- `detergentmc-bedrock` のエンティティ単体テスト：
  `BedrockEntityTranslatorTest`（ゾンビ・豚・村人・他プレイヤー・
  落ちたアイテム・経験値オーブ・矢の読み取り、種別対応表の網羅率
  99／14／10／2／1、アイテム表 1,272／1,312、メタデータ翻訳、
  絵画の題名）、`BedrockPacketsEntityTest`（AddEntity／AddPlayer／
  AddItemEntity／RemoveEntity／移動・テレポート・装備・エフェクト・
  属性・オーブ・絵画・PlayerList の符号化を読み直して確認、
  InventoryTransaction／PlayerAction の復号）、
  `BedrockSessionEntityTest`（出現→移動→消滅の一連、他プレイヤーの
  PlayerList＋AddPlayer、名前待ち・アイテム・絵画の出現遅延、
  攻撃・しゃがみ・ダッシュの Bedrock→Java、切断時の解放、
  PLAYING 前の溜め込み）、`JavaBackendConnectionEntityTest`
  （偽 Java サーバー相手に 765 のエンティティパケット全種が
  Listener へ届くことと、Use Entity／Entity Action の送信）。
- `detergentmc-bedrock` のバージョン別単体テスト：
  `BedrockVersionTest`（64 プロトコルの対応表・形式ゲート・
  アイテム表タグ）、`BedrockPacketsVersionTest`（StartGame の
  31 形状を最後まで読み直して確認、ResourcePacksInfo／Stack の
  形状別確認、ItemV4 の往復、MobEquipment／MobArmorEquipment／
  AddItemEntity の形式別確認、InventoryTransaction の V0／V1／V2／V3／V4
  の復号）、`BedrockItemIdsVersionTest`（負の network_id が有効な
  こと、バージョン別 bin 表の読み取り、花崗岩 -590・トウヒの苗木
  (6,1)／(-825,0)・ダイヤの剣 319／347 など）、
  `BedrockSessionVersionTest`（全 63 プロトコルで Login→
  ResourcePacks→Start Game まで進み、送られたパケットがその
  バージョンの形式で読めること。対象外プロトコルと 2225 の切断、
  NetherNet の切断メッセージも確認）。
- `detergentmc-core` の統合テスト（`BedrockMatrixIntegrationTest`）：
  UDP を使わず、代表の Bedrock 6 プロトコル（589・594・630・671・
  685・2193）× 本物のバニラ 1.20.1／1.20.2／1.20.4／1.20.5
  （763／764／765／766）の 24 通りで、参加・スポーン・チャット
  （バニラのログで確認）・移動（切断されないこと）・スポーン地点の
  チャンク読み込み（地面ブロックの読み取り）と、そのバージョンの
  アイテム形式（従来／ItemV4）の読み直しを確認する。1.20.5 行は
  Java 21（`detergentmc.java21.home` か
  `~/workspace/.tools/jdk-21.0.13+9`）が見つからない場合は
  スキップする。
- `detergentmc-core` の配線テスト（`BedrockWiringTest`）：
  `detergentmc.yml` の `bedrock.*` / `floodgate.*` が
  `BedrockConfig` になること。
