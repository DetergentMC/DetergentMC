# 1.12クライアント／サーバー（プロトコル335）の対応

DetergentMC に **335（Minecraft 1.12）** を追加した記録。隣接バージョンは
338（1.12.1）で、このツリーでは 338 の1段下として配線した。**335 は
この降下（578→335）の最終バージョン**である。335 は 338／340 と
**ワイヤ形式が完全同一**（共有パケットの形式差分なし）だが、
**パケットIDが 338 と 53 件ずれる**（レシピブック再編）。変換は
340 の経路・表を全て共用し、335 固有の処理は ID の解釈・生成が
起きる境界4点での名前経由の ID 付け替えである。

> **状態（2026-11-18時点）：Stage A〜E 完了。**
> 単体テスト＋実バニラ統合テスト（ProxyIntegrationTest の 335 セル
> 3 件＋回帰 44 件＝47 セル全合格）＋分割検証（3,944 件・失敗 0）
> まで完了した。**本物のゲームクライアントでの確認は未実施**
> （全バージョン共通）。

## 確定した事実（すべて実測）

- **バニラ 1.12 サーバーjar**：piston-meta から取得。
  SHA-1 `8494e844e911ea0d63878f64da9dcc21f53a3463`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、30,202,458 バイト。
  **jar ルートに version.json が無く、pack.mcmeta も data/ も無い世代**
  （338／340 と同じ型）。プロトコルは **335**（実バニラを起動し Status
  応答で確認）、pack_format は resource **3**（**この世代に
  データパックは存在しない**）、world_version は **1139**
  （起動したワールドの level.dat で確認）、内部サーバーは
  **Java 8 世代**（piston-meta の javaVersion は jre-legacy）。
  data-generator は無い（338／340 と同じ）。
- **338 とのパケットID差分は 53 件、すべて機械的なズレ**：
  minecraft-data の protocol.json（pc/1.12 と pc/1.12.1）を
  フラット化比較した。335 のみに **prepare_crafting_grid**
  （サーバー行き 0x01）があり、338 で **craft_recipe_request**
  （サーバー行き 0x12）と **craft_recipe_response**（クライアント行き
  0x2b）が追加された。ズレの内訳は、クライアント行きは 0x2b の
  挿入で 0x2c 以上が +1（36件）、サーバー行きは先頭の削除で
  0x01〜0x11 が −1（17件）。**サーバー行き 0x13 以上は一致する**
  （先頭削除と 0x12 挿入が打ち消し合う）。PacketIds の対応表
  （全283エントリ＝従来282＋prepare_crafting_grid）へ 335 列を
  追加し、機械的規則が全件成立することを引き直し時に照合した
  （tools/packet-ids-335.py）。335 に存在するのは 119 件
  （protocol.json の play 124 件から、表に載らない status 4 件と
  legacy_server_list_ping 1 件を除く）。
- **共有パケットの形式差分は実質なし**：flatten 差分は 8 件
  （difficulty、entity_velocity、game_state_change、player_info、
  spawn_entity、spawn_entity_living、client_command、entity_action）
  あるが、すべて minecraft-data の記述レベルの変更（mapper の注釈や
  vec3i16＝i16×3 の名前付き型）で、ワイヤ形式は同一。変換は不要。
- **Keep Alive は 335 も varint**（338 と同じ。i64 化は 340 から）。
  したがって 335⇔338 では Keep Alive の型付け替えは不要で、
  340 形式との境界では 338 と同じ付け替え（復元表は 335 専用）が効く。
- **レジストリ差分は無し**：1.12 と 1.12.1 の両 jar の **assets/ の
  再帰 diff が 0 件**（jar エントリ総数は難読化クラス名の差で
  20,551 対 20,554 だが、データ系は同一）。数値表の ground truth は
  minecraft-data 1.12（**335 自身のデータ**。pc/1.12 の version.json
  は version 335 を名乗る）で、blocks 254 種・items 410 種・
  entities 110 種・(id,meta) 実在 1,328 通り。340 が流用してきた表は
  335 本人の裏付けになるため、**340 の変換表（ブロック意味対応表・
  アイテム表・音名表・バイオーム表）をそのまま共用**する。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **変換は 340 への全面委譲＋境界4点の ID 付け替え**。このコード
  ベースの変換段はすべて名前経由（PacketIds.nameFor → 変換 →
  PacketIds.id）で ID を解釈するため、338 と番号がずれる 335 でも
  段の内部は無改造で済む。付け替えが必要なのは ID を直接解釈・
  生成する境界だけ：
  (1) 335 クライアントの入口（ID を 335→340 へ付け替えてから
  340 経路へ。Prepare Crafting Grid は届け先が無いため落とす）、
  (2) 335 クライアント向けの最終段（Keep Alive を varint へ
  付け替えてから ID を 340→335 へ。335 に無いパケットは落とす）、
  (3) 内部 335 サーバーの入口（ID を 335→340 へ付け替えてから
  340 経路へ）、(4) 内部 335 サーバー向けの最終段（340 形式まで
  変換した後、Keep Alive を varint へ付け替えてから ID を
  340→335 へ）。
- **Keep Alive の付け替えは 338 の関数を共用**：照合する番号は
  クライアント行き 0x1f が 335・338 で同一、サーバー行きは
  付け替え後に 340 番号（0x0b）になるため、そのまま効く。
  i64→varint の切り捨て復元表は 335 専用
  （`keepAliveIds335ToClient`）を持つ。
- ProxySession の意味的な列挙（ログイン正規化・参加フロー・
  旧クライアント／旧サーバーの括り）へ is335()／isServer335() を
  「追加のみ」で広げた。ClientAdapter の最古は 335 になり、
  `supportedClientProtocols()` は先頭 335・2番目 338・3番目 340 の
  **33 プロトコル**。単体テスト（`ClientAdapter335Test`）で固定した
  （404／401／338 で踏んだ最古脱落バグの再発防止。338 は OLDEST の
  差し替えで脱落しやすいため明示列挙を維持した）。
- リソースパックは 335 専用（pack_format **3**、
  `detergentmc-pack-1.12.zip`）。中身は 338／340 と同じ旧レイアウト
  で、pack.mcmeta の説明文だけ 1.12 用に分けた。フォールバック順は
  **335 → 338 → 340 → 393 → …**（形式・レイアウトが同一の 338 が
  最初の代替）。外部モードは `resource-pack.url-335`／`sha1-335`。
- Bedrock→335：チャンクは 340 の専用パース（translate340）を共用
  （形式が完全同一）。正規化の意味対応表も 340 と共用。
  Spawn Entity／Living は専用の入口 parseSpawnEntity335／
  parseSpawnEntityLiving335 から 340 のパースへ委譲する
  （**種別がずれる疑いのある 498 の関数には流さない**）。
  JavaBackendConnection の 340 分岐には 335 を追加で参加させ、
  Player Info の crypto 有無の**否定列挙**にも 335 を入れた
  （338／401 と同型の罠）。
- 落とすパケット：Prepare Crafting Grid（335 のみ・届け先なし）、
  Craft Recipe Request／Response と Unlock Recipes・Crafting Book
  Data・Statistics（340 と同じ方針。Craft Recipe Response は
  758→340 段でも落とす設計で、335 の最終段でも落とす）。

## テスト（Stage E 完了時点）

- Stage E は 338 の Stage D 起点だったブランチを 338 最終 HEAD
  （`67deaa6`）へ**競合ゼロでリベース**してから実施した。
  リベース後の単体テストが失敗 0 のままであることと、
  ClientAdapter335Test の固定（一覧先頭＝335・338／340／393／
  401／404 の残存・計 33 プロトコル）が崩れていないことを
  確認してから統合へ進んだ。
- 分割検証（Gradle、モジュール単位。`split-verification-335/` に
  XML をコピーし aggregate.py で集計）：**重複排除後 3,944 件・
  失敗 0・エラー 0**（スキップ 37 は従来の条件付きテスト分：
  bedrock の UDP 2 件＋core の条件付き 35 件）。モジュール別：
  protocol **3,139 件**、bedrock **441 件**、core **294 件**
  （うち統合セル 47 件）、adapter **67 件**、content **3 件**、
  いずれも失敗 0。338 時点（3,926 件）から新規 48 件。
  実行中にランタイム再起動で 1 回中断したが、XML 保存済みの
  範囲から再開し、全テストクラス（482 クラス）の XML が
  そろっていることを照合して欠落なしで完走した。
- 335 の新規テスト：Stage C の 29 件（ClientAdapter335Test＝
  最古・一覧固定（先頭335・2番目338・計33）、PacketIds335Test＝
  ID差分53件の機械的規則の全件照合と存在 119 件、
  ClientJoinFlow335Test、ProtocolVersion335Test、
  ProxySession335Test＝340 経路共用・ID 付け替え往復
  （Update Time の 0x47→0x46、Keep Alive 応答の 0x0C）・
  切り捨て復元・Prepare Crafting Grid の破棄）と、Stage D の
  19 件（ResourcePackBuilder／Mode／Server の 335 テスト 9 件、
  Bedrock の 335 テスト 9 件、AdapterDiscovery の 1.12 発見 1 件、
  ほか既存テストの 33 プロトコル化に伴う更新）。
- 実装中に見つけて直した不具合（2件）：(1) ResourcePackMode の
  Decision.packForProtocol に 335 分岐を入れる際、338 分岐の
  ヘッダを置換で消してしまい、338 クライアントへ本体パックが
  返るようになっていた（338／335 の Mode テストで検出、338 分岐を
  復旧）。(2) ResourcePackMode の互換コンストラクタ委譲は
  canonical へ直接委譲する 5 件だけが引数増の対象で、中間
  コンストラクタへの委譲へも一律に足すと件数がずれる
  （コンパイルエラーで検出、対象を 5 件に限定）。
- **実バニラ統合テスト（Stage E）**：ProxyIntegrationTest に
  335 セル 3 件を追加し、回帰 44 件（338 の Stage E の選定を
  すべて含む）と合わせて **47 セル全合格**（338 の Stage E と
  同じ選定方式。ProxyIntegrationTest 全 150 メソッドのうち、
  配置した jar で実行可能な 47 件を実行し、各セルの XML を
  メソッド名と照合済み）。新規セルの内容：
  - fake335 → 実バニラ 1.12（内部 335）：335 同士の素通し。
    参加→チャット→**Keep Alive の往復**（0x1f／応答 0x0c、
    varint）→2 通目のチャット到達まで確認。
  - fake335 → 実バニラ 1.13（内部 393）：Flattening を跨ぐ
    下向き変換。サーバー側 393 の Keep Alive（i64）を
    クライアント境界で varint 化し、応答は 335 専用の復元表で
    元の i64 に戻すこと。往復後の 2 通目のチャット到達まで
    確認し、付け替えと復元が実ワイヤで通ることを裏付けた
    （応答値がバニラのチャレンジと一致しなければバニラが
    切断するため、2 通目の到達が正しさの証拠になる）。
  - fake393 → 実バニラ 1.12（内部 335）：意味対応表を逆向きに
    通す経路。335 側のサーバー行き ID のずれ（Chat 0x03、
    移動 0x0e、Keep Alive 応答 0x0c）の付け替えもあわせて確認。
  実バニラ jar は 1.12 が SHA-1 `8494e844…`（piston-meta 照合
  済み）、1.13 が SHA-1 `d0caafb8…`（393 の Stage E と同じ）の
  ものを使用。

## 制限

- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  確認済みなのは上記の単体テストと実バニラ統合セル（偽クライアント
  経由）だけである。
- **データパックが無いため trigger ブリッジが使えない**（340／338
  から引き継ぐ既知の課題）：言語切替・hello 等の既存の仕掛けは
  335 バックエンドではそのまま動かない。代替方式は未定で、
  今回のスコープ外。
- Keep Alive の長時間セッションでの安定性は実機確認が特に
  重要：varint の ID は int 範囲に収まるが、サーバー（他
  バージョン）由来の i64 ID は切り捨て＋復元で往復させる設計
  のため。短時間の往復は Stage E の統合セルで実ワイヤ確認済み
  （PLAYTEST-1.12.md 参照）。338 と同じ観点。
- その他の制限は 340 と同一（スポーンエッグの EntityTag は
  運ばない、ベッド／バナー／頭は代表色・代表種への近似、
  anvil の facing は近似、レシピ系と Statistics は落とす）。
- Bedrock→335 は単体テストのみ（統合テストは未実施）。
