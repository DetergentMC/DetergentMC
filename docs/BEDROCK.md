# Bedrock（統合版）対応：Geyser 風＋Floodgate 風（内蔵）

DetergentMC は Bedrock（Minecraft 統合版）クライアントを、外部プラグイン
なしで内蔵の機能として受け入れる。Geyser 風の翻訳と Floodgate 風の ID
発行を、DetergentMC 自身（`detergentmc-bedrock` モジュール）が行う。
Geyser / Floodgate / Paper / Mojang のコードは一切使っていない。
プロトコル仕様は公開資料（wiki.vg、Minecraft Wiki、wiki.bedrock.dev、
PrismarineJS minecraft-data の Bedrock protocol.json **29 バージョン分**、
PMMP BedrockData、Microsoft Learn、GeyserMC の公開ドキュメント）
から新しく実装した。プロトコル番号は wiki.vg「Bedrock Protocol
version numbers」と minecraft-data の protocol.json で 1 つずつ確認し、
確認できた番号だけを対応表に入れている。

## 対象バージョン

| 項目 | 値 |
|---|---|
| 対象 Bedrock | **1.20.0〜26.51（プロトコル 589〜2193、RakNet 世代の全公式プロトコル 30 個）** |
| 対象外 | **Bedrock 26.60 / 26.62（プロトコル 2225）**。26.60 で RakNet は廃止され、トランスポートが WebRTC ベースの NetherNet に置き換わったため、RakNet 前提のこの実装では繋がらない。来た場合は「NetherNet には未対応」の切断メッセージを返す（下記の「NetherNet対応の設計」を参照） |
| MOTD の表示 | サーバーリストには 630 / 1.20.50 を出す（Unconnected Pong は 1 通りしか書けないため）。Login でクライアントのプロトコルを判定し、そのバージョン用の形式で送る |

### 対応プロトコル一覧（30 個）

| プロトコル | Bedrock | プロトコル | Bedrock | プロトコル | Bedrock |
|---|---|---|---|---|---|
| 589 | 1.20.0 / 1.20.1 | 685 | 1.21.0 / 1.21.1 | 844 | 1.21.111 |
| 594 | 1.20.10〜1.20.15 | 686 | 1.21.2 / 1.21.3 | 859 | 1.21.120 |
| 618 | 1.20.30〜1.20.32 | 712 | 1.21.20 | 898 | 1.21.130 |
| 622 | 1.20.40 / 1.20.41 | 729 | 1.21.30 | 924 | 1.26.0 |
| 630 | 1.20.50 / 1.20.51 | 748 | 1.21.40 | 944 | 1.26.10 |
| 649 | 1.20.60 / 1.20.62 | 766 | 1.21.50 | 975 | 1.26.20 |
| 662 | 1.20.70〜1.20.73 | 776 | 1.21.60 | 1001 | 1.26.30 |
| 671 | 1.20.80 / 1.20.81 | 786 | 1.21.70 | 2168 | 1.26.40 / 1.26.44 |
|  |  | 800 | 1.21.80 | 2169 | 1.26.45 |
|  |  | 818 | 1.21.90 | 2193 | 1.26.50 / 1.26.51 |
|  |  | 819 | 1.21.93 |  |  |
|  |  | 827 | 1.21.100 |  |  |

- 1.21.20（712）と 1.21.30（729）は、以前の資料で 704／712 と
  書かれたものもあるが、wiki.vg と bedrock-rs の両方で 712／729 を
  確認したためこちらを採用している。
- 26.51／26.62 はホットフィックスのため直前のメジャーと同じ
  プロトコル（2193／2225）を共有する。

### Bedrock × 内部サーバーのマトリクス

Bedrock クライアント（上記 30 プロトコル）が、内部 Java サーバー
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
それ以外の Bedrock プロトコル（24 個）は、偽クライアントでの
Login→Start Game までの単体テスト（`BedrockSessionVersionTest`。
全 30 プロトコル）で形式を確認しているが、本物のバニラとの
統合テストはまだ行っていない。

| Bedrock | 763 (1.20.1) | 764 (1.20.2) | 765 (1.20.4) | 766 (1.20.5) |
|---|:-:|:-:|:-:|:-:|
| 589 (1.20.0) | ○ | ○ | ○ | ○ |
| 594 (1.20.10) | ○ | ○ | ○ | ○ |
| 630 (1.20.50) | ○ | ○ | ○ | ○ |
| 671 (1.20.80) | ○ | ○ | ○ | ○ |
| 685 (1.21.0) | ○ | ○ | ○ | ○ |
| 2193 (1.26.50) | ○ | ○ | ○ | ○ |
| その他 24 プロトコル | △ | △ | △ | △ |

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
589〜2193 の全バージョンで同じ**（minecraft-data の protocol.json
29 バージョン分で確認。増えたパケットは末尾の高番号に追加されるだけ）。
違うのは**形式**で、境界は次の通り（すべて protocol.json で確認）。

| パケット | 形式の境界 |
|---|---|
| StartGame | hardcore は 1.20.80 以降。level_id 前の識別子は 1.21.0〜1.21.80 で 3 つ（server／world／scenario）、1.21.90〜1.21.130 で 4 つ（＋owner）。itemstates は 1.21.40 まで。movement_authority は 1.21.80 まで。tick_death_systems は 1.21.100〜1.21.120 のみ。1.26.0 以降は識別子が末尾に移動し has_server_join_info が付く。編集者向け項目と is_chat_logging は 1.26.30 のみ（is_chat_logging は 1.26.40 でまた無くなる） |
| ResourcePacksInfo | has_addons は 1.20.70 以降。resource_pack_links は 1.20.30〜1.21.30。behaviour_packs・force_server_packs は 1.21.20 まで。world_template は 1.21.50 以降。disable_vibrant_visuals は 1.21.90 以降。texture_packs の件数は 26.40 以降だけ varint（それ以前は li16） |
| ResourcePackStack | behavior_packs は 1.21.120 まで。has_editor_packs は 1.20.70 以降。game_version にはクライアントの表示名（例：`1.26.50`）を入れる |
| Item（MobEquipment 等） | **ItemV4** は MobEquipment が 1.26.20 以降、MobArmorEquipment が 1.26.30 以降、AddPlayer／AddItemEntity が 1.26.40 以降。ItemV4 は network_id が li16・block_runtime_id が varint・has_stack_id が bool で、air も全項目を書く（extra 長 0）。MobArmorEquipment の 5 個目（body）は 1.21.20 以降で、Java の body（馬・オオカミの鎧）を入れる |
| InventoryTransaction（復号） | 26.30 で legacy_transactions が option 化・アイテムが ItemV4 化・action_type が zigzag32 化。26.40 で transaction_type・actions も option 化 |
| SubChunk | バージョン 9 のまま（2026 年 10 月時点の wiki・bedrock-rs で確認）。全バージョンで同じ |
| エンティティ種別 | Bedrock では文字列のため、バージョンごとの数値表は不要。1.21 以降の新エンティティは Java 1.20.x のバックエンドからは出てこない |

`BedrockVersion`（30 プロトコル＋ゲート判定）に境界を集約し、
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
   - **26.1（775）／26.2（776）のセクションは blockCount の後に 2 バイトの
     追加フィールドがある**（実バニラ 26.1／26.2 のスポーンチャンクをダンプして
     確認。値は変わる：26.2 のセクション 0 で 14。内容は未確認のため
     `BedrockChunkTranslator` は読み飛ばす。770〜774 には無い）。これを
     読まないとセクションがずれ、全セクションが壊れる（2026-11-15、
     `matrixAgainst26_1` が 9 件失敗の残り 1 件だった原因）。
   - **BitStorage の詰め方は旧式（1 long あたり 64 / bits 個、long の境界を
     またがない）**。long 数は `(entryCount + perLong - 1) / perLong`
     （perLong = 64 / bits）。2026-11-14 に「連続詰め
     （`(entryCount * bits + 63) / 64`）」に変えたが誤診だった：実バニラ
     1.20.1（763）のワイヤは b=5 で 342 long（=旧式）であり、26.2 の
     チャンクも旧式でなければ読めない（新式ではセクション 9 で壊れる）。
     接頭辞あり（758〜769）ではワイヤの長さを見て両方式に対応する。
     なお Java 側の `ChunkTranslator` は同じ 11-14 の新式の仮定を共有して
     おり、ブロック値の誤訳の疑いがある（別途調査が必要。Java の統合テストは
     Join Game の到達だけを見るため気付かない）。
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
  Bedrock の `protocol.json`（29 バージョン分で確認。核パケットの
  ID・形式は 1.20.0〜26.51 で同じ）、wiki.vg「Bedrock Edition
  level format」、Minecraft Wiki「Pocket Edition level format」、
  Mojang の bedrock-protocol-docs（sub-chunk request ガイド）で確認した。
  1.20.40 だけ見ていた頃の「1.20.50 の記述が無いので 1.20.40 の形式で
  送る」という前提は、今回 29 バージョン分を確認して置き換えた。
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
  に写す（`BedrockItemIds`）。**バージョン別**の表を 25 個のバイナリ
  （`bedrock_item_ids_<tag>.bin`、Java 765 順の（network_id・metadata）
  ペア）で持ち、クライアントの Bedrock バージョンごとに読む。出典は
  PMMP BedrockData の `required_item_list.json`（各バージョンのタグ）。
  生成は `detergentmc-bedrock/tools/generate_item_tables.py`。
  変種ブロックアイテム（苗木の種類・ベッドの色など）は、古い Bedrock
  では共有 ID＋metadata、新しい Bedrock では独自 ID で送る。
  網羅率（1,312 件中）：1.20.0〜1.20.40 は **1,244**（94.8%）、
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
  `protocol.json`（**29 バージョン分で確認。核パケットの ID・形式は
  1.20.0〜26.51 で同じ**）と Microsoft Learn「Default Minecraft
  Entity Listings」「Default Minecraft Item Listings」で確認した。
  1.20.40 だけ見ていた頃の前提は、今回 29 バージョン分を確認して
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
DetergentMC は **26.60／26.62 を「調査済み・未対応」とし、実装は
していない**。来たクライアントには「NetherNet には未対応」の
切断メッセージを返す。以下は調査結果と、将来実装するときの設計案である。

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
- **仕組み（bedrock-protocol の PR #774・#806・#828/#830、
  2026-08〜09）**：LAN への広告は UDP 7551 番ポートで version-7
  の広告を出す。直接接続は TCP でのシグナリングの後、UDP で
  ゲームを流す（WebRTC／DTLS）。HTTP でのシグナリングによる
  直接接続は 2026-09-22 時点ではまだ公開で対応していない。
  Node.js の実装は PrismarineJS/node-nethernet（GitHub の
  ブランチ指定・ピン留めなし）がある。

### 実装しない理由と、実現可能な道筋

- **Java の NetherNet ライブラリが無い。** 使える公開実装は
  Node.js のものだけで、Java 側に持ち込むには WebRTC（ICE-lite／
  DTLS）の実装を Java で書くか、Node.js を子プロセスとして
  橋渡しするかのどちらかになる。どちらも数週間規模の作業である。
- **この環境では検証できない。** 実機の 26.60 クライアントが無く、
  サンドボックスでは UDP 送信も禁止されている。動くかどうかを
  確かめる手段が無いまま実装しても、偽物になるだけである。
- そのため、今回は無理に「対応した」と偽らず、設計だけを残す。

### 将来実装するときに必要な部品

1. **トランスポート層**：NetherNet の接続受け入れ（UDP 7551 の
   広告＋TCP シグナリング＋DTLS ハンドシェイク）。Java で書くなら
   ICE-lite と DTLS-SRTP の実装、Node.js を使うなら
   `node-nethernet` を子プロセスで動かし、ゲームパケットを
   `detergentmc-bedrock` との間でやり取りする橋渡し。
   `node-nethernet` は GitHub ブランチ指定のため、使うなら
   コミットをピン留めして取り込むこと。
2. **シグナリング**：サーバーの登録が要るかどうか（Microsoft の
   シグナリング経由で、サーバーをどう公開するか）を、GeyserMC／
   BedrockConnect の実装と Mojang の変更履歴で再確認する。
   LAN 内だけなら広告のみで済む可能性がある。
3. **認証**：NetherNet 経由でも Login パケットの JWT チェーンは
   同じ形で来る想定だが、トランスポート層での暗号化（DTLS）が
   入る分、セッション確立の順序が RakNet 世代と変わる。
   Xbox 署名検証（未実装のまま）が入るまでは、なりすましの
   リスクは RakNet 世代と変わらない。
4. **パケット層の流用**：NetherNet の上で流れるゲームパケット
   （バッチ・Login・StartGame 等）は RakNet 世代と同じ形式のはず
   （プロトコル 2225 の `protocol.json` で確認すること）。
   `BedrockSession`・`BedrockPackets` はそのまま使える見込みで、
   変えるのはトランスポート（`BedrockServer`・`RakNetCodec` の
   代わりに NetherNet 層を入れる）だけのはずである。
5. **検証手順**：実機の Bedrock 26.60／26.62 クライアントで
   「LAN に出るか・参加できるか・チャットと移動が通るか」を
   確認する。偽クライアントだけでは不十分で、DTLS まで含めた
   偽実装では実機の挙動は確かめられない。

**工数の見立て**：トランスポート層の実装と実機検証込みで、
数週間規模。Node.js の橋渡しなら短くなるが、子プロセスの
運用（起動・終了・障害時の切り離し）が別途要る。

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
- **対応 Bedrock は 1.20.0〜26.51（プロトコル 589〜2193）で、
  26.60／26.62（2225）は NetherNet のため未対応。**
  26.60／26.62 のクライアントが来た場合は「NetherNet には未対応」
  の切断メッセージを返す（上記の「NetherNet対応の設計」を参照）。
  対応プロトコル以外（588 以下・2194・2225 以外の未確認番号）も
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
  `BedrockVersionTest`（30 プロトコルの対応表・形式ゲート・
  アイテム表タグ）、`BedrockPacketsVersionTest`（StartGame の
  10 形状を最後まで読み直して確認、ResourcePacksInfo／Stack の
  形状別確認、ItemV4 の往復、MobEquipment／MobArmorEquipment／
  AddItemEntity の形式別確認、InventoryTransaction の V1／V2／V3
  の復号）、`BedrockItemIdsVersionTest`（負の network_id が有効な
  こと、バージョン別 bin 表の読み取り、花崗岩 -590・トウヒの苗木
  (6,1)／(-825,0)・ダイヤの剣 319／347 など）、
  `BedrockSessionVersionTest`（全 30 プロトコルで Login→
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
