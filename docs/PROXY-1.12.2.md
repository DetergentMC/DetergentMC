# 1.12.2クライアント／サーバー（プロトコル340）の対応

DetergentMC に **340（Minecraft 1.12.2）** を追加した記録。隣接バージョンは
393（1.13）で、このツリーでは 393 の1段下として配線した。340 は
**Flattening 前の最後の世代**で、393 とは小差分では済まない
（パケットID差分95件・形式差分12件・レジストリ全面再編）。

> **状態（2026-11-18時点）：Stage A〜E 完了。** 340 は「検証待ち時間の
> 先行実装」方式で Stage A〜D を先行し、393 の Stage E 完了後に
> 393 の最終 HEAD `becf837` へリベース（競合なし）してから Stage E
> （実バニラ統合テスト＋分割検証）を実施した。統合テストで確認済み
> なのは下記「テスト（Stage E）」に挙げたセルだけである。
> **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。

## 確定した事実（すべて実測）

- **バニラ 1.12.2 サーバーjar**：piston-meta から取得。
  SHA-1 `886945bfb2b978778c3a0288fd7fab09d315b25f`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、30,222,121 バイト。
  **jar ルートに version.json が無く、data-generator も無い世代**。
  プロトコルは **340**、pack_format は resource **3**
  （**この世代にデータパックは存在しない**）、world_version は
  **1343**、内部サーバーは **Java 8 世代**。
  レジストリの ground truth は minecraft-data 1.12（Burger 抽出）と
  実 jar のレジストリクラスからの抽出（音 549 件・モブ 83 種）の
  二本立てで確立し、ワイヤ実物採取（join-game.bin／chunk-sample.bin、
  チャンクはバイト単位でパース一致）でも裏付けた。
- **393 とのパケットID差分は 95 件**：対応表の全282エントリを
  1.12.2 の protocol.json に対応付け、340 に存在するのは **120 件**、
  162 件は存在しない（Trade List、Declare Commands、Face Player、
  Stop Sound、NBT Query 等は 340 に無い）。形式差分は **12 件**
  （craft_recipe_response、map、scoreboard_objective、
  spawn_entity_painting、statistics、tab_complete（双方向）、
  teams、unlock_recipes、world_particles、craft_recipe_request、
  crafting_book_data、＋共有型 entityMetadata／slot の差）。
- **ブロックに状態が無い**：ワイヤ値は **(数値ID&lt;&lt;4)|メタデータ**
  （254 種×16、実在組合せ **1,328 通り**）。758 側のブロック状態との
  対応は意味対応表（wool の meta が色別ブロックに分裂する等）で、
  Burger の variations 記述をパースして構築し、
  `tools/verify-340-tables.py` で生成 Java と JSON の **全件照合**済み。
  例外：torch の記述は1つずれ（正規のワイヤ対応を採用）、anvil の
  記述は破損（damage は meta&gt;&gt;2 で確定、facing は近似）。
- **アイテムは全面振り直し**（410 種、最大 ID 2267、疎）。slot には
  **itemDamage: i16** があり（393 では NBT の Damage へ移行済み）、
  付け替えは SlotTranslator／ChunkTranslator の 340 層が担う。
  1.13 で分裂した子アイテム（魚種・染料・バナー・ベッド・頭・
  スポーンエッグ）は「基底アイテム＋Damage タグ」の規約で表現し、
  ワイヤ変換時に itemDamage へ出し入れする。
- **チャンクの骨格は 393 と同じ**（blockCount 無し・crossing 詰め）
  だが、パレット値が (id&lt;&lt;4)|meta、直接パレットは **13 ビット**、
  **バイオームは u8×256**（393 は i32×256）。
- **entityMetadata の型番号欄は 340 では i8**（393 は varint）。
  変換は二層構成：ChunkTranslator の 340 層は「ハイブリッド形式」
  （404 式エンコーディング＋340 のレジストリ番号）で扱い、
  SlotTranslator の 340 層がハイブリッド⇄実ワイヤ（slot の
  itemDamage、メタデータ型 i8、World Particles の ID i32、
  iconcrack のデータ形式、reddust の引数なし）を変換する。
- **効果 ID は 340 では 1 始まり**（758 の 0 始まり＋1）。758 の
  27 以降（bad_omen 等）は 340 に無いため落とす。
- **Join Game／Respawn は 393 とスキーマ完全一致**（ワイヤ実物で
  確認済み）。PlayTranslator の 340 実装は 393 の実装へ委譲する。
  Keep Alive も i64 で不変。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- 340 は**専用の変換群一式**を持つ（RegistryTables340＋
  RegistryTables340Snd（音名表の分離クラス）、ChunkTranslator の
  340 節、SlotTranslator の 340 層、PlayTranslator の
  JoinGame340）。音名フォールバックは「758→340 は改名対＋対応の
  無い音の近似、340→758 は真の改名対のみ」の原則で生成
  （近似対を逆転させると block.stone.break が block.tuff.break に
  化けるため）。
- ProxySession は 340 クライアント／340 サーバーの両方向を配線。
  最古プロトコルを 340 に下げた際、**393 と 401 と 404 が
  `ClientAdapter.supportedClientProtocols()` に残る**ことを
  単体テスト（`ClientAdapter340Test`）で固定した
  （404 Stage E で踏んだ脱落バグの再発防止）。ProxySession の
  旧クライアント列挙にも is340() を追加で参加させた。
- 落とすパケット（他バージョンと同方針）：Unlock Recipes、
  Craft Recipe Request／Response、Crafting Book Data、
  Statistics（名前⇄ID 表が無いため）。340 に存在しない
  パケットは ID 表の -1 で自動的に落ちる。
- リソースパックは 340 専用（pack_format **3**、
  `detergentmc-pack-1.12.2.zip`）。**中身も旧レイアウト**
  （テクスチャは `textures/items/`、layer0 は
  `detergentmc:items/bubble`）。1.12 には custom_model_data が
  無いため、スライムボールのオーバーライドは付けない。
  340 パックが無い構成では 393 パックの順に代替する（形式が
  違うため最終手段）。外部モードは `resource-pack.url-340`／
  `sha1-340`。
- Bedrock→340：チャンクは専用パース（translate340。直接
  パレット13ビットと u8 バイオームのため 404 と共用不可）、
  正規化は意味対応表。Spawn Entity／Living は専用の
  parseSpawnEntity340／parseSpawnEntityLiving340 で読む
  （**種別がずれる疑いのある 498 の関数には流さない**）。

## テスト（Stage E 完了時点）

- 単体テスト（Gradle、リベース後に再実行）：protocol モジュール
  **3,067 件・失敗 0**（340 の新規テストは Stage C の 56 件＋
  Stage D のリソースパック 9 件を含む）、bedrock **423 件・失敗 0**
  （スキップ 2 は従来分）、core **386 件・失敗 0**（スキップは
  従来の条件付きテストと jar 不在時の統合テスト）。
- 統合テスト（ProxyIntegrationTest）：340 の新規セル 3 件
  （fake340→実バニラ 1.12.2、fake340→実バニラ 1.13、
  fake393→実バニラ 1.12.2）と回帰 38 セルの **計 41 セルを
  実行し全合格**（スキップ 0＝全セル実走）。実バニラ 1.12.2 は
  prep で照合済みの SHA-1
  `886945bfb2b978778c3a0288fd7fab09d315b25f` の jar を使用。
  この実行で、チャンクの直接パレット13ビットと entityMetadata
  型番号（i8）も実ワイヤで裏付けられた（実サーバーのチャンク・
  スポーンが偽クライアントまで到達し、参加・チャット・移動が
  バニラログで確認できた）。ProxyIntegrationTest の全 144 メソッド
  のうち今回実行したのはこの 41 セルだけである。
- 分割検証（`split-verification-340/`、XML コピー＋aggregate.py）：
  重複排除後 **3,846 件・失敗 0・エラー 0**（スキップ 37 は
  従来の条件付きテスト）。全テストクラスの XML が揃っている
  ことを確認済み（ProxyIntegrationTest 本体はセル別 XML で代替）。
- 最古プロトコル脱落チェック：393・401・404 が
  `supportedClientProtocols()` に残っていることを
  `ClientAdapter340Test` で固定し、リベース後も合格。

## 制限

- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  確認済みなのは上記の統合セルと単体テストだけである。
- **データパックが無いため trigger ブリッジが使えない**：
  言語切替・hello 等の既存の仕掛けは 340 バックエンドでは
  そのまま動かない。代替方式は未定の課題として残す
  （プロトコル変換自体は本対応の主眼どおり動作する）。
- スポーンエッグの EntityTag は運ばない（常に pig 扱いの近似）。
  ベッド／バナー／頭は 1.12 では色・種別がブロックエンティティ
  側にあるため、代表色・代表種への近似になる。anvil の facing は
  近似。レシピ系と Statistics は落とす（上記）。
- entityMetadata の型番号対応は Burger の protocol.json を
  ground truth とし、Stage E の統合セルで実ワイヤでも確認した。
