# 1.9クライアント／サーバー（プロトコル107）の対応

DetergentMC に **107（Minecraft 1.9）** を追加した記録。
隣接バージョンは 108（1.9.1）で、このツリーでは 108 の
1段下として配線した。108 との差分は **Join Game の
dimension の型1件だけ**（107 は i8、108 以降は i32。Stage A で
実測により確定。下記）。レジストリ・パケット ID 列は 108 と
完全同一で、変換表は 108 の表を全面委譲する。Respawn の
dimension は両方とも i32 で差分は無い。

> **状態（2026-11-19時点）：Stage E 完了。**
> 分割検証の件数は末尾の「検証」節を参照。ProxyIntegrationTest への
> 統合セル追加と実バニラ統合テストは実施済み。**本物の
> ゲームクライアントでの確認は未実施**（全バージョン共通）。

## 番号対応（重要）

- **プロトコル 107 ＝ Minecraft 1.9 単独**（実バニラ 1.9 を
  起動し、Status 応答のプロトコルが 107 であることを確認。
  1.9.1 は 108、1.9.2 は 109、1.9.3／1.9.4 は 110）。
  world_version は **169**。
- 降下順は 108 → 107 → 47（1.8 系）。47 とは世代差が大きく
  （パケット ID 差分 94 件・形式差分 22 件、チャンク形式・
  entityMetadata 形式・エンティティ座標形式が別物）、
  107⇔108 の1件差分とは対照的である（先行調査 PREP-47）。

## 確定した事実（すべて実測）

- **バニラ 1.9 サーバーjar**：piston-meta から取得。
  SHA-1 `b4d449cf2918e0f3bd8aa18954b916a4d1880f0d`
  （piston-meta の version json の downloads.server.sha1 と
  一致、照合済み）、8,848,015 バイト。**jar ルートに version.json
  が無く、pack.mcmeta も data/ も無い世代**（108／109／110 と
  同じ型）。プロトコルは **107**（実バニラを起動し Status
  応答で確認）、pack_format は **2**（**この世代にデータパック
  は存在しない**）、world_version は **169**（起動したワールド
  の level.dat の DataVersion で確認）、内部サーバーは
  **Java 8 世代**（version json の javaVersion は majorVersion 8）。
  data-generator は無い。
- **107⇔108 の差分は Join Game の dimension 1件だけ（Stage A）**：
  1. **protocol.json 全面比較**：minecraft-data の pc/1.9 と
     pc/1.9.2 の protocol.json を全フィールド比較し、差分は
     `/play/toClient/types/packet_login` の dimension 型
     （i8 ⇔ i32）の **ちょうど1件**だけだった。パケット集合・
     ID・他の全フィールド（Respawn の dimension は両方 i32 を
     含む）は同一。108⇔109 が差分ゼロのため、107 のパケット
     ID 列は 108 と完全一致する（PacketIds は 284 エントリ、
     107 で存在するのは 114 件＝108 と同数）。
  2. **実バニラ 1.9 のワイヤ採取**：Status は
     `{"name":"1.9","protocol":107}` を自己申告。Join Game は
     生バイト 17 バイト（entityId 2252、gameMode 0、
     dimension 0、difficulty 1、maxPlayers 20、levelType
     "default"）で、dimension を i8 として読んで過不足なし
     （i32 で読むと崩れる）。Keep Alive 本体 `fce5b601` を
     varint として 4/4 バイト消費し、応答（サーバー行き
     0x0b）の後のチャットがバニラのログに到達した。
  3. **属性 generic.armorToughness**：1.9 の jar の言語ファイル
     には無く、1.9.1 の jar にはある（108 で追加。言語差分
     14 行のうち属性の追加はこれだけ）。この世代の属性は
     **名前文字列で運ばれ、変換表（RegistryRemapper）に
     属性名の表自体が存在しない**（108／109 の節にも属性関数は
     無く、パケットは ID 付け替えのみで通すのが従来世代一貫の
     扱い）。よって **armorToughness は変換表に影響しない**。
- **107 固有の癖：Chunk Data のゼロ埋め**：107 バックエンドの
  Chunk Data は dataLen に大きなゼロ埋めを含む。Stage A の
  実測では dataLen 184,665 に対しセクション実データ 30,809＋
  バイオーム 256 で、残り **153,600 バイトがすべてゼロ**
  （512 の倍数）だった（先行調査の別サンプルでは 184,320
  バイト）。データ領域の後ろに余分なバイトは無い
  （blockEntities フィールド自体が無い）。108 で修正済みの
  バニラ側の癖である。**対処**：既存のチャンク読み取りは
  dataLen 分を切り出した部分バッファをセクション順次読みし、
  部分バッファの末尾一致を要求しない（末尾一致の要求は
  パケット外側だけ）ため、ゼロ埋めは自然に捨てられる。
  変換器側の追加調整は不要で、単体テスト
  （`ChunkTranslator107Test.chunk107WithZeroPaddingTranslatesTheSame`）
  で「ゼロ埋め有無で変換結果が同一」であることを固定した。
- **Keep Alive は varint**（108⇔107 で KA 変換は不要。
  340 以上との境界では 108 と共用の付け替え＋107 専用の
  復元表が働く）。

## 108 との差分（確定内容）

**Join Game の dimension 型の1件のみ。** パケット集合・
ID 列、フィールド構造（Join Game を除く）、レジストリ
（ブロック 214 種・アイテム 365 種・音 444 件・エンティティ・
バイオーム・パーティクル・効果）はすべて 108 と同一。
検証スクリプトは tools/packet-ids-107.py（protocol.json の
1件差分、id107 列＝id108 列であることを全エントリで確認）。

そのため 110 との関係も 108 と同じになる：クライアント行き
update_sign が 107 にもあり（0x46、正準経路では落とす）、
後続 6 件の ID が 110 より +1 ずれ、Chunk Data に
blockEntities フィールドが無い（107 バックエンドのチャンク
ではブロックエンティティの中身は運ばれない）。近似も 108
（＝110）と同じものを引き継ぐ：magma→netherrack（87）、
nether_wart_block／red_nether_brick→nether_brick（112）、
bone_block→quartz_block（155、メタ保持）、structure_void→
barrier（166）、structure_block のアイテム形→barrier の
アイテム形（166）、polar_bear→ブタ（90）、fallingdust→
explode（0）、1.10 追加音 19 件→汎用音、音 pitch の ×63.5
量子化。

## 実装の形

- 変換経路は 108 の経路を写したもの（ChunkTranslator／
  SlotTranslator の 107 節、ProxySession の 107 系メソッド群）。
  中身は 108 の境界処理（チャンク末尾の blockEntities 無し、
  音 pitch 換算、resource_pack の hash 付け外し、KA 復元表）を
  107 用に写したもので、RegistryRemapper の 107 節は
  全メソッドが 108 の写像へ委譲する。
- **Join Game だけは写しではなく手書き**：PlayTranslator の
  `parseJoinGame107` は dimension を符号付きバイトで読み、
  `translateJoinGame758To107` は 108 形式を経由して dimension
  だけ i8 で書き直し、`translateJoinGame107To758` は 404 形式
  （dimension i32）を合成して 404→758 変換へ渡す。
  ProxySession の `readDimension107FromJoinGame` も i8 読みに
  手当てした（Respawn 側は i32 のまま 108 と同じ）。
  ネザー（dimension −1）の符号拡張が化けないことは単体テスト
  で固定した。
- リソースパックは **pack_format 2**。レイアウトは 1.13 より
  前の旧形式（textures/items/）で、108 と形式番号も同じ。
  フォールバック順は **107 → 108 → 109 → 110 → 210 → 315 →
  316 → 335 → …**。外部設定キーは `resource-pack.url-107`／
  `resource-pack.sha1-107`。
- アダプタモジュールは `detergentmc-adapter-v1_9`。
  内部サーバーは 1.9 の指定でプロトコル 107 として扱い、
  Java 8 で起動する。
- Bedrock→107 の生物スポーンは専用の parseSpawnEntity107／
  parseSpawnEntityLiving107 から **108 のパースへ委譲**する
  （種別表が 108 と完全同一のため。種別番号が違う 498 の
  関数には流さない）。
- Bedrock→107 の Join Game は専用の parseJoinGame107 で読む。
  dimension が i8 の 107 を 340 系の i32 読みに流すと壊れて
  次元が既定のオーバーワールドに落ちるためで、
  JavaBackendConnection107Test で固定した（Stage D 仕上げで
  修正）。

## 検証

- 分割検証 `split-verification-107/`（重複排除後の集計）：
  **4,522 件・失敗 0・エラー 0**（内訳：protocol 3,672 件、
  bedrock 458 件、core 322 件（うち統合セル 68 件）、
  adapters＋content 70 件。スキップ 39 件は環境条件付き
  自己スキップで、108 の分割検証と同じ内訳）。最古プロトコル
  脱落の罠は `ClientAdapter107Test` で固定した（一覧の先頭が
  107、108・109・110・210・315・316・335・338・340 が残り、
  計 40 プロトコル。Stage E のリベース後にも 107 系単体テスト
  14 クラス・87 件を新鮮な実行で確認し、失敗 0）。
- **Stage E 完了**：ProxyIntegrationTest に 107 の新規 3 セル
  （fake107→実バニラ 1.9、fake107→実バニラ 1.9.1、
  fake108→実バニラ 1.9）＋回帰 65 セル（108 の Stage E の
  選定をすべて含む）の **68 セルすべてが合格**（スキップ 0、
  各セルの XML をメソッド名と照合済み）。新規 3 セルとも
  参加→チャット→Keep Alive（0x1f、varint、応答 0x0b）→
  応答後の2通目のチャットがバニラログに到達、まで実ワイヤで
  確認した。Join Game の dimension 付け替えは、fake107→
  実バニラ 1.9.1 ではクライアント受信分を parseJoinGame107
  （i8）で、fake108→実バニラ 1.9 では parseJoinGame108
  （i32）で検証し、両方向とも次元が化けないことを確認した。
  Stage E は 108 最終 HEAD `86a7473` へのリベース（107 の
  5 コミット、**競合なし**）の上で実施した。実行中に
  ランタイム再起動が1回あり、統合スクリプトがセル 46 の
  途中で停止したが、XML 有無判定の再開機能でセル 46 から
  再開・完走させた（欠落・二重実行なし）。
- **resource_pack_receive の空 hash は実バニラ 1.9 が
  受け付けることを確認した**：fake108→実バニラ 1.9 セルで、
  108 形式（ダミー hash＋result）の Resource Pack Response を
  送信 → プロキシはクライアント側境界で hash を読み捨て、
  107 向けに空文字列の hash を先頭へ補って転送 → バニラは
  切断せず、その後の Keep Alive 往復と2通目のチャット到達・
  接続維持を確認した。1.9.4／1.9.2／1.9.1 に続いて 1.9 でも
  解消した。
- 回帰セル fake335→実バニラ 1.13 が初回に「Keep Alive
  応答後のチャット到達の制限時間超過」で失敗した（108 の
  Stage E でも同セル・同型の失敗が繰り返し記録されており、
  対照実験で環境要因と確認済みの既知事象）。再実行で
  合格し、上記の 68 セル全合格に含まれる。
- Stage A では実バニラ 1.9 を起動して Status を確認し、
  Join Game／チャンク／Keep Alive のワイヤ採取と
  protocol.json 全面比較で差分1件を確定した
  （tools/packet-ids-107.py が検証スクリプト）。

## 残る制限

- **本物のゲームクライアントでの確認は未実施**（全バージョン
  共通）。
- **チャンクのブロックエンティティが 107 では運ばれない**
  （108 から引き継ぐ世代的な制限。看板の文字・チェストの
  中身・バナーの模様等は 107 が関わるチャンクでは欠落する）。
- **クライアント行き update_sign が正準経路で落ちる**ため、
  107 サーバーで看板を書き換えても新クライアントには
  ライブ更新が届かない（108 から引き継ぐ制限）。
- resource_pack_receive の空 hash は実バニラ 1.9 が受け付ける
  ことを Stage E で確認済み（「検証」節を参照）。
- データパックが無い世代のため trigger ブリッジ（言語切替・
  hello 等）は 107 バックエンドでは使えない（108／109／110／
  210／315／316 から引き継ぐ既知の課題、代替は未定）。
- 属性 `generic.armorToughness` は 107 に存在しない。属性
  パケットはこの世代では名前表を持たず ID 付け替えのみで
  通すため、108 以上のバックエンドから 107 クライアントへ
  armorToughness を含む属性更新が届いた場合の扱いは
  従来世代と同一（素通し）である。
- 近似の損失：1.10 追加ブロック 5 種の見た目（108 と同じ
  代用）、polar_bear の姿（ブタ代用）、fallingdust の表現、
  1.10 追加音 19 件（汎用音）、音 pitch の量子化誤差。
- Declare Recipes／Statistics／Tags／World Event ID は全方向で
  未実装（落とす）のまま（全バージョン共通）。
