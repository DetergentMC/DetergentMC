# 1.13.2クライアント／サーバー（プロトコル404）の対応

DetergentMC に **404（Minecraft 1.13.2）** を追加した記録。隣接バージョンは
477（1.14）で、このツリーでは 477 の1段下として配線した。477 と 480 の間が
差分 0 件だったのとは対照的に、**404 と 477 の差分は大きい**（1.13 は
「Flattening」直後の世代で、1.14 で座標の詰め方・Join Game・チャンクの
光の持ち方が変わった）。そのため 404 には専用の変換群を用意し、477 の
経路の共用は形式が一致する部分だけに限った。

## 確定した事実（すべて実測）

- **バニラ 1.13.2 サーバーjar**：piston-meta から取得。
SHA-1 `3737db93722a9e39eeada7c27e7aca28b144ffa7`（piston-meta の
downloads.server.sha1 と一致、照合済み）、33,835,116 バイト。
`run/vanilla/server-1.13.2.jar`（リポジトリには含めない）。
**jar ルートに version.json が無い世代**（1.14 から入った）。
プロトコルは **404**、pack_format は resource **4** / datapack **4**、
内部サーバーは **Java 8 世代**。
- **477 とのパケットID差分**：`tools/packet-ids-404.py` で対応表の
全281エントリを 1.13.2 自身の protocol.json（minecraft-data
pc/1.13.2）に対応付けた。404 に存在するのは **137 件**で、そのうち
**101 件が 477 と ID 相違**、36 件が一致。404 にだけ存在するパケットは
**bed（0x33、PLAY_BED として表に追加）** のみ。逆に 477 にあって
404 に無いものは trade_list、open_book、open_horse_window、
entity_sound_effect、update_light、update_view_position、
update_view_distance、lock_difficulty、set_difficulty（SB）、
update_jigsaw_block など。掘削 ack は 404 にも無い（498 で追加）。
表に無い唯一の 404 パケットは legacy_server_list_ping（0xfe、
どのバージョンでも表管理していない）。
- **レジストリ**：実 jar の data generator（JDK 8 で
`net.minecraft.data.Main --reports`）を ground truth とした
（ViaVersion の mapping-1.13 は改名の正規化や dead coral の欠落があり
そのままでは使えない。sounds／particles のみ Via 由来）。
ブロック 598 種・**ブロック状態 8,599**・アイテム 790（items.json の
protocol_id）。エンティティは minecraft-data の 'mob' 95 件が
レジストリ順（trident が最後尾 94。pig 51・zombie 87 は実サーバー
へのプローブでも確認）。バイオーム 73 件（plains=1）。絵画 26 件は
1.14 と同順（jar のクラス定数プールで確認）。効果は 30 件
（bad_omen／hero_of_the_village は 1.14 追加のため 404 へは落とす）。
ブロックエンティティは 25 件（1.14 の末尾追加7件を除いたもの）。
メニューレジストリは 404 に存在しない（Open Window は文字列種別）。
生成した写像表は `tools/verify-404-tables.py` で **35,377 件全件照合**
済み。
- **形式差分（protocol.json の型ツリー全比較＋実サーバープローブ）**：
- **Join Game**：entityId, gamemode, dimension（i32）, **difficulty（u8）**,
maxPlayers, levelType, reducedDebugInfo。477 の viewDistance は無い。
実バニラの生バイトでも確認済み。
- **Respawn**：dimension, **difficulty**, gamemode, levelType。
- **Server Difficulty**：difficulty のみ（477 は difficultyLocked が付く）。
- **Chunk Data**：**heightmap NBT が無い**。セクションはブロックライト
2048 バイト＋スカイライト 2048 バイト（空光のある次元のみ）を内蔵。
ビット詰めは**ロング境界をまたぐ旧式（crossing）**、直接パレットは 14 ビット。
バイオームはデータバッファ末尾の **256 件 i32**（プローブでバイト確認）。
- **座標の詰め方**：404 は **x26|y12|z26**、477 以降は x26|z26|y12
（1.14 で変更）。position 型の全フィールド（メタデータの 9/10 番含む）
が詰め替え対象。
- **Spawn Entity（オブジェクト系）**：種別は **i8 のオブジェクト表**
（boat=1, item=2, minecart=10, tnt=50, arrow=60, armor_stand=78,
trident=94 など。実サーバーで 21 種を召喚して確認）。pitch が yaw より
先。**生物系は Spawn Entity Living（レジストリ varint＋末尾メタデータ）**
で送る。1.13.2 クライアントは Spawn Entity をオブジェクト表でしか
解釈しないため、758→404 では必ず振り分ける。
- **Open Window**：windowId は u8、種別は**文字列**
（"minecraft:chest" 等。馬は "EntityHorse"）、タイトルは平文文字列、
slotCount（u8）が付く。
- **Map Data**：locked フラグが無い（477 で追加）。
- **Player Block Placement（SB）**：location, direction, hand,
cursor の順で insideBlock が無い（477 は hand が先頭で insideBlock 付き）。
- **エンティティメタデータ**：型 0〜15 は 477 と同じ番号。477 の
16（villagerdata）・17（optvarint）・18（pose）は 404 へ落とす。
粒子のデータ ID は別番号（dust 404=11、block 404=3、
falling_dust 404=20、item 404=27）。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **アダプタ**：新モジュール `detergentmc-adapter-v1_13_2`
（V1132Adapter、プロトコル404）。
- **PacketIds**：404 列を追加し、PLAY_BED を新設（404 のみ 0x33）。
- **RegistryRemapper**：404 専用の生成表（RegistryTables404）。
写像は名前ベースで構築し、改名（sign→oak_sign、rose_red→red_dye 等）、
染料の対応（white_dye→bone_meal 等）、1.14 追加要素の代替
（cat→ocelot、crossbow→bow 等）と落下（lectern・bell・jigsaw・
campfire のブロックエンティティ）を規則化した。
- **PlayTranslator**：Join Game／Respawn の 404 対（difficulty の
合成は normal=2。実際の難易度は Server Difficulty が続く）。
- **ChunkTranslator**：404 節を新設。チャンク対（光の分離・合成を
578 形式 Update Light 経由で実施）、座標詰め替え、スポーン振り分け、
メタデータ（型 0〜15）、粒子、Open Window（文字列表）、
Block Dig（status は varint）・Block Place（フィールド順）など。
- **ProxySession**：`is404()`／`isServer404()` と 404 専用の変換経路
（`convertServerPlayOutcomeTo404`／`routeServerPlay404`／
`to758ServerPackets404`／`to758ClientPacket404`）。404 に無い
パケット（Trade List 等）はガードで落とす。Login Success は
578 と同じ文字列 UUID 形式として扱う。
- **ClientAdapter**：最古の対応クライアントが 404 になった
（`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_13_2`）。versionName(404) は
"1.13.2"。404 未満と未対応帯は従来どおり断る。
- **リソースパック**：pack_format **4**。`build404()`／
`detergentmc-pack-1.13.2.zip`、ResourcePackServer の28パック構成
（packForProtocol の 404 分岐は pack404→pack477→…の順に代替）、
ResourcePackMode の `resource-pack.url-404`／`sha1-404`、
DetergentServer の `resourcePack404()`。
- **内部サーバー**：VanillaServerHost の Java 8 起動対象に "1.13.2" を
追加。DatapackGenerator の pack_format は 4。
- **Bedrock→404**：JavaBackendConnection の旧式分岐に 404 を追加
（Join Game は parseJoinGame404 専用分岐、Spawn Entity は
parseSpawnEntity404 でオブジェクト表経由）。BedrockChunkTranslator に
translate404 を新設（光内蔵・crossing 詰め。スカイライトの有無は
末尾残量の一致で判定）。**単体テストのみ**（統合テストなし）。
- **versionedJars**：1.13.2 を追加（build-numbers.properties に 1.13.2=1）。

## Stage E で見つけて直した実バグ

- **ClientAdapter.supportedClientProtocols() から 477 が脱落**：
最古プロトコルを 404 に下げた際、一覧の先頭を OLDEST 定数に置き換えた
だけでは旧・最古の 477 が一覧から消え、477 クライアントが参加できなく
なっていた。477 を明示的に列挙して復旧（今後の降下でも、最古を下げる
たびに旧・最古を明示追加すること）。
- **ProxySession の旧クライアント列挙に 404 が欠落（3箇所）**：
handleServerJoinGame763 の先頭分岐、プレイ状態 Resource Pack の分岐、
handleServerPlayFrom763 の汎用フォールスルーの内側列挙に 404 が無く、
Join Game が 765 形式のまま段変換に入って化け、System Chat 等が
落とされていた。各列挙に 404 を追加。
- **BedrockChunkTranslator.translate404 が存在しない blockCount を
読んでいた**：1.13.2 のセクションに blockCount は無い（1.14 から）。
実バニラの末尾照合でずれが判明し、読みを削除。
- **チャンクのビット詰めを non-crossing と誤認**：404 のセクションは
1.15 以前の旧式 BitArray（long 境界をまたぐ crossing）。最初の実装は
1.16 以降の non-crossing で読み書きしており、bits=4 のセクションでは
両方式が一致するためスーパーフラットのプローブと単体テストでは
検出できなかった。自然地形チャンク（bits=5・パレット20件・
320 long＝4096×5/64 ちょうど）でパレット参照が範囲外になり、
実バニラ統合（fake477→実1.13.2）で接続が切れる形で発覚。
ChunkTranslator の 404 セクション読み書きと Bedrock の translate404 を
crossing に統一し、テスト側で独立実装した crossing 詰めによる回帰
テストと、実バニラ採取パケット列のリプレイテストを追加した。

## 検証

- 分割検証（`split-verification-404/`）：総数 **3,732 件**
（成功 3,619・失敗 0・エラー 0・スキップ 113）。内訳は
protocol 2,888、bedrock 396、core 378、adapter 67、content 3。
スキップはこのツリーに jar が無いバージョンの統合セルと
Bedrock UDP のエンドツーエンド（従来どおり）。
集計方法は従来どおり（フルクリーンビルドは行わない）。
- 実バニラ統合（`split-verification-404/run-integration-404.sh`）：
新規 3 セル（fake404→実1.13.2、fake404→実1.14、fake477→実1.13.2）と
回帰 29 セル（477・480・485・490・498・573・575・578・758 系）の
**計 32 セルすべて成功**。ProxyIntegrationTest 全135メソッドの
分割実行も失敗 0（実行できたものは成功、jar が無いものはスキップ）。
- 検証中の環境事象：core バッチで BedrockMatrixIntegrationTest の
バニラ 1.20.4 起動が1度だけ180秒タイムアウトしたが、単体再実行と
クラス全体の再実行で成功し、一過性と判断した（コード変更なし）。
- 生成表の照合：`tools/verify-404-tables.py` で 35,377 件全件一致。
- **本物のゲームクライアントでの確認は未実施**（従来どおり）。

## 残る制限

- **PLAY_BED（404 専用パケット）は 758 形式に相当物が無いため落とす**
（ベッドで寝る演出は再現されない）。
- 404 に無いパケットは 404 向けに落とす：Trade List、Entity Sound
Effect、Open Book、Open Horse Window、Update Light（光はチャンクに
内蔵されるため不要）、Update View Position／Distance。
逆向きでは Edit Book、Crafting Book Data、Confirm Transaction を落とす。
- Open Window：馬ウィンドウ（"EntityHorse"）は 758 形式に送れないため
落とす。404 専用メニュー（blast_furnace・grindstone・lectern・loom・
smoker・cartography_table・stonecutter）は 404 向けに落とす。
- チャンクの光：758→404 では Update Light からセクション内蔵光へ
移し替える。光データが無いセクションのスカイライトは「最上位
セクションより上は 0xFF、それ以外は 0x00」の近似。404→758 では
heightmap を空で合成する（従来の旧世代と同じ扱い）。
- メタデータ型 16〜18（villagerdata・optvarint・pose）は 404 へ落とす。
効果 ID 30／31（bad_omen・hero_of_the_village）は 404 へ落とす。
Game State Change の reason 11 は 404 に無いため落とす。
- Trade List の canRestock 近似など、477 以前から引き継ぐ制限は
そのまま（docs/PROXY-1.14.md 参照）。
- **既知の別件（今回直していない）**：498 系の Multi Block Change 変換は
実バニラ 1.14.4 の形式（chunkX／chunkZ i32＋水平位置レコード）と
違う可能性がある（sectionPosition 形式は 1.16.2 以降）。404 の
Multi Block Change は実形式に合わせて専用変換を実装したが、
498 経路の見直しは別件とする。
- Bedrock→404 は単体テストのみ。二人プレイの見え方はフェイク
クライアントでは確認できない（実機プレイテスト項目）。
