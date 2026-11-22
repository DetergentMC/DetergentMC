# 1.11.2クライアント／サーバー（プロトコル316）の対応

DetergentMC に **316（Minecraft 1.11.2）** を追加した記録。隣接バージョンは
335（1.12）で、このツリーでは 335 の1段下として配線した。316 は
340 世代と**ワイヤ形式の骨格が同一**（スロットの itemDamage、メタデータの
i8 型フィールド、チャンクの直接パレット13ビット・crossing 詰め）だが、
1.12 で追加された要素（レシピブック・進捗・コンクリート類・
知識の書・オウム・イリュージョナー）が存在せず、**パケットIDが 335 と
61 件ずれる**。変換は 340 の経路を土台に 316 専用のレジストリ表
（RegistryTables316）を合成し、316 固有の処理は境界での ID 付け替えと
メタデータ型 13〜16 の落下である。

> **状態（2026-11-19時点）：Stage A〜E 完了。**
> 分割検証 4,020 件・失敗 0、実バニラ統合テストは新規 3 セル＋
> 回帰 47 セルの計 50 セルが全合格（スキップ 0）。
> **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。

## 確定した事実（すべて実測）

- **バニラ 1.11.2 サーバーjar**：piston-meta から取得。
  SHA-1 `f00c294a1576e03fddcac777c3cf4c7d404c4ba4`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、9,720,835 バイト。
  **jar ルートに version.json が無く、pack.mcmeta も data/ も無い世代**
  （335／338／340 と同じ型）。プロトコルは **316**（実バニラを起動し
  Status 応答で確認）、pack_format は **3**（**この世代に
  データパックは存在しない**）、world_version は **922**
  （起動したワールドの level.dat で確認）、内部サーバーは
  **Java 8 世代**（version JSON の javaVersion.majorVersion は 8）。
  data-generator は無い。
- **335 とのパケット差分**：minecraft-data の protocol.json
  （pc/1.11 が 316 自身のデータ。data.js が 1.11.2 の protocol と
  registries の両方を pc/1.11 へ解決する）をフラット化比較した。
  パケット総数は 316 が **118 件**・335 が 124 件。335 のみに
  **advancements・select_advancement_tab・unlock_recipes**
  （クライアント行き）と **advancement_tab・crafting_book_data・
  prepare_crafting_grid**（サーバー行き）の **6 件**があり、
  316 のみのパケットは無い。**ID のズレは 61 件**（全て play）。
  **共有パケットのフィールド形式差分は 0 件**。共有する型の差分は
  entityMetadata の項目型のみ（1.12 で型 13＝nbt が追加。316 は
  型 0〜12 のみ）。PacketIds の対応表（全283エントリ）へ 316 列を
  追加し、316 に存在するのは **113 件**。
- **Keep Alive は 316 も varint**（実バニラ 1.11.2 へログインして
  採取し確認。クライアント行き 0x1f のペイロードが varint、
  応答はサーバー行き **0x0b**。応答後にチャットがバニラのログへ
  届くことまで確認）。335／338 と同じ付け替え関数を共用し、
  復元表は 316 専用。
- **レジストリ差分（minecraft-data pc/1.11 ⇔ pc/1.12）**：
  blocks **236 種 ⇔ 254 種**（1.12 の新規はコンクリート 251・
  コンクリートパウダー 252・彩釉テラコッタ 235〜250 の 18 種）、
  items **390 種 ⇔ 410 種**（新規は上記 18 種のアイテム形＋
  鉄塊 452・知識の書 453。`totem`→`totem_of_undying` の改名が
  あるが数値 ID 449 は不変）、entities は minecraft-data 上
  **108 種 ⇔ 110 種**（新規は parrot・illusion_illager）。
  **effects 27・particles 49・biomes 62 は ID⇔名前対応まで完全一致**。
  **共有する名前の数値 ID は blocks／items／entities の全てで一致**
  （最大アイテム ID は両世代とも 2267）。したがって 316 の変換表は
  340 の解決済み表から合成できる（316 に無い分の近似を差し引く）。
  なお pc/1.11 の blocks.json は variations が欠けた不完全な
  Burger 抽出のため、(id,meta) の実在組み合わせは 340 の表を正とする。
- **実 jar からの抽出**：音は **493 件**（340 は 549 件。316 の音名は
  全て 340 の一覧の部分集合で改名なし。汎用フォールバック音の
  316 での index は 397＝block.stone.break）、エンティティ登録は
  **81 件**（340 は 83 件。差分は illusion_illager 37 と parrot 105
  のみで、共有分の番号は一致。pig は両世代とも 90）。
  ブロックエンティティは **23 種**（340 の 24 種から bed が無い。
  bed は末尾 index 23 で、0〜22 は不変）。絵画は 26 種で
  1.12 と同一（jar 内 enum の文字列で照合。DonkeyKong を含む）。
- **Join Game／Respawn の形式は 393 と完全一致**（実バニラ 1.11.2 の
  Join Game を採取し 20/20 バイト消費を確認。difficulty を持ち
  viewDistance を持たない）。Login Success は UUID 文字列形式
  （335 世代と同じ）。

## 316 に無い要素の扱い（近似方針）

316 方向（新しいサーバー→316 クライアント／内部 316 サーバー）では、
1.12 新規要素を次の通り近似する。色メタデータの順序（0=白…15=黒）は
両世代で共通。

- コンクリート（251、meta=色）→ 羊毛（35、同色）
- コンクリートパウダー（252、meta=色）→ 羊毛（35、同色）
- 彩釉テラコッタ（235+色、meta=向き）→ 色付き堅焼き粘土
  （159、meta=色）。色は保ち、向きは失う
- アイテム形のコンクリート／パウダー → 羊毛アイテム 35
  （スロット分割表は魚・頭蓋骨・旗・ベッド・染料のみを対象とする
  340 世代の既存仕様のため、アイテム形では色が落ちる。継承した制限）
- 彩釉テラコッタのアイテム形（235〜250）→ アイテム 159（damage=色）
- 知識の書（453）→ 本（340）
- 鉄塊（452）は 1.11.1 で追加され 316 自身に存在するため同一に通す
  （当初は 1.12 追加分と誤って金塊へ寄せていた。315 の Stage E で
  変換表・生成／検証スクリプト・単体テストを修正済み）
- parrot（105）・illusion_illager（37）→ pig（90）
- 1.12 のみの音 → 汎用音（316 の 397＝block.stone.break）
- ブロックエンティティ bed → 送らない（DROP）
- 進捗（Advancements）・レシピブック系の 6 パケット → 送らない
  （316 にプロトコル自体が無い）
- メタデータのハイブリッド型 13（blockid）・14（optblockid）・
  15（nbt）・16（particle）→ 316 の実ワイヤ向けに落とす
  （316 のワイヤ型は 0〜12 のみ）

逆方向（316→新しい側）では、316 に存在しない (id,meta) 範囲
（ブロック 235〜252 相当）→ stone フォールバックになる。

## ポート構成

他のバージョンと同じ（プロキシ 25565、内部バニラ 25566、
リソースパック 25567、Bedrock UDP 19132）。内部 1.11.2 サーバーは
Java 8（`detergentmc.java8.home`）で起動する。

## 配線の要点

- 変換表は `RegistryTables316`（＋分割表 BsFwd1–15／BsRev1–3／Snd）。
  340 の解決済み表（tables-340.json）から合成し、生成時に
  31,478 件を照合した（tools/gen-316-tables.py、
  tools/verify-316-tables.py）。音名フォールバックは 758→316 が
  827 ペア、316→758 が 117 ペア。
- ChunkTranslator／SlotTranslator／PlayTranslator に 316 節を追加
  （340 節の複製＋改名。SlotTranslator のメタデータ書き出しだけは
  型 13〜16 を落とす形に組み替えた）。
- ProxySession に is316()／isServer316()、Keep Alive 復元表、
  316 クライアント分岐・内部 316 サーバー分岐を追加。
  Keep Alive の付け替えは 338 用関数を共用する（316 の
  クライアント行き 0x1f／サーバー行き 0x0b は 338 と一致）。
- リソースパックは pack_format **3**・旧レイアウト。
  フォールバック順は **316 → 335 → 338 → 340 → 393 → …**
  （ResourcePackMode／ResourcePackServer／ProxySession の
  packFor763LikeClient）。外部パック設定は
  `resource-pack.url-316`／`resource-pack.sha1-316`。
- アダプタモジュール `detergentmc-adapter-v1_11_2`
  （V1112Adapter、protocolVersion 316）。versionedJars の
  バージョン一覧と build-numbers.properties に 1.11.2 を追加。
- Bedrock→316 は 340 の専用パース（parseSpawnEntity316／
  parseSpawnEntityLiving316 は 340 のパースへ委譲。種別番号が
  違う 498 用関数には流さない）と、チャンク変換の 340 経路
  （正規化は (id,meta) の意味対応。316 が送りうる値は 340 の表と
  完全一致するため正確）を共用する。

## テスト（Stage E 完了時点）

- 分割検証（split-verification-316/）：**4,020 件・失敗 0・
  エラー 0**（成功 3,981、スキップ 39）。内訳は protocol 3,211 件
  （316 専用テストとして RegistryRemapper316Test 11 件、
  PacketIds316Test 5 件、ChunkTranslator316Test 11 件、
  SlotTranslator316Test 10 件、PlayTranslator316Test 4 件、
  ProxySession316Test 13 件、ClientAdapter316Test、
  ProtocolVersion316Test、ClientJoinFlow316Test、
  ResourcePackBuilder／Mode／Server の 316 テストを含む）、
  bedrock 441 件、core 298 件（うち ProxyIntegrationTest の
  セル 50 件）、adapter 67 件、content 3 件。スキップ 39 件は
  環境条件付き分（bedrock の UDP テスト 2 件、core の Bedrock
  統合・キャプチャ系 37 件）で、従来と同じ内訳である。
  Stage A〜D 時点の総数（4,053 件）との差は、ProxyIntegrationTest
  の数え方の違い（クラス一括の 150 件→実行したセル 50 件）と、
  adapter 分の XML を今回の実行で取り直したことによる。
- 実バニラ統合テスト（ProxyIntegrationTest）：新規 3 セル
  （fake316→実バニラ 1.11.2、fake316→実バニラ 1.12、
  fake335→実バニラ 1.11.2）と回帰 47 セル（335 の Stage E の
  選定をすべて含む）の **計 50 セルが全合格・スキップ 0**。
  新規 3 セルとも、参加→チャット→Keep Alive（0x1f、varint、
  応答は 316 の 0x0b）→応答後の 2 通目のチャット到達まで
  実ワイヤで確認した（応答値がバニラのチャレンジと一致しなければ
  バニラが切断する構造のため、これが付け替えと復元表の正しさの
  裏付けになる）。回帰セルのうち fake335→実バニラ 1.13 の
  1 件は初回実行で Keep Alive 応答後のチャット到達が制限時間に
  間に合わず失敗したが、再実行で合格した（負荷時のタイミング
  由来と判断。335／338 の Stage E でも同セルは合格している）。
- **最古プロトコル脱落の罠**は ClientAdapter316Test で固定した：
  supportedClientProtocols() の先頭が 316、続いて 335・338・340 が
  残り、計 **34 プロトコル**。
- Stage C のテストで発見した不整合：ProxySession コンストラクタの
  serverProtocol 検証リストに V1_11_2 が無く、内部 316 サーバーを
  構築できなかった（修正済み）。

## 制限

- 実バニラ統合テストは Stage E で実施済み（上記「テスト」参照）。
  ただし実行したのは選定した 50 セルだけで、全組み合わせを
  検証したわけではない。
- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  特にタブリスト・チャンクの見え方は実機確認が要る。
- **データパックが無い世代のため、trigger ブリッジ
  （`/trigger d_hello`、言語切替など）が 316 バックエンドでは
  使えない**（335／338／340 から引き継ぐ既知の課題。代替手段は
  未定で、今回のスコープ外）。
- アイテム形のコンクリート／コンクリートパウダーは色が落ちる
  （340 世代から継承した制限。上記「近似方針」参照）。
- 彩釉テラコッタは向きが落ちる。parrot／illusion_illager は
  pig で代用する。
- Bedrock→316 は単体テストのみ（実 Bedrock クライアント未確認）。
