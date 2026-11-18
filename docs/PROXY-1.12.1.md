# 1.12.1クライアント／サーバー（プロトコル338）の対応

DetergentMC に **338（Minecraft 1.12.1）** を追加した記録。隣接バージョンは
340（1.12.2）で、このツリーでは 340 の1段下として配線した。338 は
340 と**ワイヤ形式がほぼ完全同一**で、差分は Keep Alive の型だけ
（490／485 系の小差分パターン）。変換は 340 の経路・表を全て共用し、
338 固有の処理は Keep Alive の付け替えだけである。

> **状態（2026-11-18時点）：Stage A〜E 完了。**
> 単体テスト＋実バニラ統合テスト（ProxyIntegrationTest の 338 セル
> 3 件＋回帰 41 件＝44 セル全合格）＋分割検証（3,893 件・失敗 0）
> まで完了した。**本物のゲームクライアントでの確認は未実施**
> （全バージョン共通）。

## 確定した事実（すべて実測）

- **バニラ 1.12.1 サーバーjar**：piston-meta から取得。
  SHA-1 `561c7b2d54bae80cc06b05d950633a9ac95da816`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、30,221,337 バイト。
  **jar ルートに version.json が無く、pack.mcmeta も data/ も無い世代**
  （340 と同じ型）。プロトコルは **338**（実バニラを起動し Status
  応答で確認）、pack_format は resource **3**（**この世代に
  データパックは存在しない**）、world_version は **1241**
  （起動したワールドの level.dat で確認）、内部サーバーは
  **Java 8 世代**（piston-meta の javaVersion は jre-legacy）。
  data-generator は無い（340 と同じ）。
- **340 とのパケットID差分は 0 件**：minecraft-data の protocol.json
  （pc/1.12.1 と pc/1.12.2）をフラット化比較し、ID・集合・型が
  全て一致することを確認。さらに PacketIds の対応表（全282エントリ）
  を 1.12.1 の protocol.json から独立に引き直し、**id338 == id340 が
  全件成立**（338 に存在するのは 120 件、162 件は存在しない＝340 と同じ）。
  338 にだけあるパケットは 0 件。
- **形式差分は 1 件だけ：Keep Alive**。338 の keepAliveId は
  **varint**（340 以降は i64）。双方向（クライアント行き・サーバー行き）
  ともこの1件だけで、types セクションも同一。
- **レジストリ差分は無し**：1.12.1 と 1.12.2 の両 jar のファイル一覧が
  完全一致（20,554 エントリ同士で diff 0）、assets/ の再帰 diff も
  0 件（recipes／advancements／lang／blockstates を含む）。数値表の
  ground truth は両版とも minecraft-data 1.12（＝335 のデータ。
  1.12.1／1.12.2 フォルダには protocol.json しか無い）で、内容不変の
  裏付けが取れたため、**340 の変換表（ブロック意味対応表・アイテム表・
  音名表・バイオーム表）をそのまま共用**する。
- **レシピブック再編は 338 の時点で発生済み**：craft_recipe_request
  （サーバー行き 0x12）と craft_recipe_response（クライアント行き
  0x2b）が存在し、prepare_crafting_grid は無い。PREP-340 が
  「1.12⇔1.12.2 の差」と記録していた再編は、実際には 1.12.1 で
  起きていた（335⇔338 比較で判明）。340 との関係では影響しない
  （レシピ系パケットは従来方針どおり落とす）。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **変換は 340 への全面委譲**（401 が 404 の経路を共用した前例と
  同じ流儀）。新規の変換表・変換器は作らない。ProxySession の
  列挙は「追加のみ」で広げ、340 の分岐に 338 を参加させた
  （最古を下げても 340 が脱落しない形）。ClientAdapter の最古は
  338 になり、`supportedClientProtocols()` は先頭 338・2番目 340
  の **32 プロトコル**。単体テスト（`ClientAdapter338Test`）で
  固定した（404／401 で踏んだ最古脱落バグの再発防止）。
- **Keep Alive の付け替えは境界の4点だけ**：
  (1) クライアント行きチェーンの末尾（340 形式まで変換した後、
  338 クライアント向けに i64→varint）、(2) 338 クライアントからの
  応答の入口（varint→i64）、(3) 内部 338 サーバーからの Keep Alive
  （varint→i64 で 340 経路へ）、(4) 内部 338 サーバーへの応答
  （i64→varint）。
- **切り捨て復元**：i64→varint は下位32ビットへの切り捨てになる。
  クライアント方向だけ復元表（セッション内の
  `keepAliveIds338ToClient`）を持ち、切り捨てた元の i64 を
  切り捨て値で引けるようにして、クライアントの応答で元の値へ
  戻す（Acknowledge Player Digging の連番表と同じ考え方）。
  サーバー方向は値の出所が 338 サーバー自身の varint なので
  拡大は常に正確で、復元表は要らない。実バニラの Keep Alive ID は
  通常ランダムな long 値のため、切り捨ては日常的に起きる前提で
  実装した。不正なペイロードは素通しする。
- 実装中に見つけて直した配線漏れ（2件、いずれも 338 単体テストで検出）：
  (1) `handleServerLogin` の Login Success 正規化列挙と 763系
  クライアントのプレイ遷移条件に isServer338() が無く、内部 338
  サーバーの Login Success が旧形式のまま調整処理へ渡って
  例外→切断になっていた。(2) Bedrock の Player Info の
  crypto 有無が**否定列挙**（`!= 340` 等）で、338 が含まれず
  存在しない crypto ブロックを読もうとして名簿が落ちていた
  （401 で踏んだ罠と同型）。
- リソースパックは 338 専用（pack_format **3**、
  `detergentmc-pack-1.12.1.zip`）。中身は 340 と同じ旧レイアウト
  （テクスチャは `textures/items/`、layer0 は
  `detergentmc:items/bubble`、custom_model_data は無いため
  スライムボールのオーバーライド無し）で、pack.mcmeta の説明文
  だけ 1.12.1 用に分けた（writePack の mcmeta 差し替え口を追加）。
  フォールバック順は **338 → 340 → 393 → …**（形式・レイアウトが
  同一の 340 が最初の代替）。外部モードは
  `resource-pack.url-338`／`sha1-338`。
- Bedrock→338：チャンクは 340 の専用パース（translate340）を
  共用（形式が完全同一）。正規化の意味対応表も 340 と共用。
  Spawn Entity／Living は専用の入口 parseSpawnEntity338／
  parseSpawnEntityLiving338 から 340 のパースへ委譲する
  （**種別がずれる疑いのある 498 の関数には流さない**）。
  JavaBackendConnection の 340 分岐には 338 を追加で参加させた。
- 落とすパケットは 340 と同じ（Unlock Recipes、Craft Recipe
  Request／Response、Crafting Book Data、Statistics。338 に
  存在しないパケットは ID 表の -1 で自動的に落ちる）。

## テスト（Stage E 完了時点）

- 分割検証（Gradle、モジュール単位。`split-verification-338/` に
  XML をコピーし aggregate.py で集計）：**重複排除後 3,893 件・
  失敗 0・エラー 0**（スキップ 39 は従来の条件付きテスト分：
  bedrock の UDP 2 件＋core の条件付き 37 件）。モジュール別：
  protocol **3,101 件**、bedrock **432 件**、core **290 件**
  （うち統合セル 44 件）、adapter **67 件**、content **3 件**、
  いずれも失敗 0。
- 338 の新規単体テスト：Stage C の 25 件（ClientAdapter338Test＝
  最古・一覧固定、PacketIds338Test＝id338==id340 の全件照合と
  存在 120 件、ClientJoinFlow338Test、ProtocolVersion338Test、
  ProxySession338Test＝340 経路共用と Keep Alive 往復・切り捨て
  復元）と、Stage D の 22 件（ResourcePackBuilder／Mode／Server の
  338 テスト 9 件、Bedrock の 338 テスト 9 件、AdapterDiscovery の
  1.12.1 発見 1 件、ほか既存テストの 32 プロトコル化に伴う更新）。
- **実バニラ統合テスト（Stage E）**：ProxyIntegrationTest に
  338 セル 3 件を追加し、回帰 41 件と合わせて **44 セル全合格**
  （340 の Stage E と同じ選定方式。ProxyIntegrationTest 全
  147 メソッドのうち、配置した jar で実行可能な 44 件を実行）。
  新規セルはいずれも参加→チャット→**Keep Alive の往復**→
  2 通目のチャット到達まで確認し、varint⇔i64 の付け替えが
  実ワイヤで通ることを裏付けた（応答値がバニラのチャレンジと
  一致しなければバニラが切断するため、2 通目の到達が形式変換の
  正しさの証拠になる）：
  - fake338 → 実バニラ 1.12.1（内部 338）：338 同士の素通し＋
    varint Keep Alive の往復。
  - fake338 → 実バニラ 1.12.2（内部 340）：サーバー側 i64 を
    クライアント境界で varint 化し、応答は復元表で元の i64 へ。
  - fake340 → 実バニラ 1.12.1（内部 338）：サーバー側 varint を
    クライアント境界で i64 化し、応答を varint へ戻す。
  実バニラ jar は 1.12.1 が SHA-1 `561c7b2d…`（piston-meta 照合
  済み）、1.12.2 が SHA-1 `886945bf…`（340 の Stage E と同じ）の
  ものを使用。検証中に Gradle のジャーナルロック競合（並行
  ビルドとの GRADLE_USER_HOME 共有）とランタイム再起動で
  2 回中断したが、いずれも XML 保存済みの範囲から再開し、
  全セルのメソッド名・結果を照合して欠落なしで完走した。

## 制限

- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  確認済みなのは上記の単体テストと実バニラ統合セル（偽クライアント
  経由）だけである。
- **データパックが無いため trigger ブリッジが使えない**（340 から
  引き継ぐ既知の課題）：言語切替・hello 等の既存の仕掛けは
  338 バックエンドではそのまま動かない。代替方式は未定。
- Keep Alive の長時間セッションでの安定性は実機確認が特に
  重要：varint の ID は int 範囲に収まるが、サーバー（他
  バージョン）由来の i64 ID は切り捨て＋復元で往復させる設計
  のため。短時間の往復は Stage E の統合セルで実ワイヤ確認済み
  （PLAYTEST-1.12.1.md 参照）。
- その他の制限は 340 と同一（スポーンエッグの EntityTag は
  運ばない、ベッド／バナー／頭は代表色・代表種への近似、
  anvil の facing は近似、レシピ系と Statistics は落とす）。
- Bedrock→338 は単体テストのみ（統合テストは未実施）。
