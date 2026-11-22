# 1.8系クライアント／サーバー（プロトコル47）の対応

DetergentMC に **47（Minecraft 1.8〜1.8.9 の全10リリース）** を
追加した記録。隣接バージョンは 107（1.9）で、このツリーでは
107 の1段下として配線した。47⇔107 はこれまでの降下で最大の
世代差であり、山場は3つある：**①チャンク形式が別物**
（パレット無し、2バイトLE `(数値ID<<4)|メタデータ` の固定詰め、
bitMap は u16、初期チャンクは map_chunk_bulk）、
**②entityMetadata 形式が別物**（先頭バイトが型3bit＋キー5bit の
bitfield、終端 0x7f、型8種）、**③エンティティ座標が i32
固定小数点 ⇔ f64**（spawn_entity_living は 107 で UUID が追加
されている）。対処方法は下記「3つの山場への対処」。

> **状態（2026-11-19時点）：Stage A〜E 完了。**
> 107 の最終 HEAD `284da74` へ競合なしでリベースし、分割検証と
> 実バニラ統合テスト（新規3セル＋回帰68セル＝71セル全合格）を
> 実施した。件数は末尾の「検証」節を参照。Stage E では実ワイヤで
> 2件の不具合（内部47サーバーのコンストラクタ検証漏れ、761→760段の
> Position 素通し）＋統合セルで1件（Keep Alive の i64→varint
> 付け替え未発火）を見つけて修正した。詳細は「検証」節。
> **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。

## 番号対応（重要）

- **プロトコル 47 ＝ Minecraft 1.8〜1.8.9 の全10リリース**
  （1.8／1.8.1／1.8.2／1.8.3／1.8.4／1.8.5／1.8.6／1.8.7／1.8.8／
  1.8.9。先行調査で全10本の実バニラ Status がプロトコル 47 を
  自己申告することを確認）。代表 jar は 1.8.9 とする。
- **DataVersion の概念自体が無い**（1.9 で導入）。level.dat に
  DataVersion フィールドは存在しない。
- 降下順は 107 → 47 → 5（1.7.6〜1.7.10）。47 より下は
  Netty 以前の世代に入っていく。

## 確定した事実（すべて実測）

- **バニラ 1.8.9 サーバーjar**：piston-meta から再検証済み。
  SHA-1 `b58b2ceb36e01bcd8dbf49c8fb66c55a9f0676cd`
  （8,320,755 バイト、クライアント jar の SHA-1 は
  `3870888a6c3d349d3771a3e9d16c9bf5e076b908`）。pack_format は
  **1**、内部サーバーは **Java 8**（version json の
  javaVersion は majorVersion 8）。data-generator は無い。
  minecraft-data の pc/1.8 は Burger 抽出で version.json の
  表記は 1.8.8 だが、1.8.8⇔1.8.9 の jar 差分は 263 クラスの
  バイトコードのみでプロトコルには影響しない。
- **実バニラ 1.8.9 のワイヤ採取**（Stage A で再採取）：
  Status はプロトコル 47 を自己申告。Join Game は 17 バイト
  （dimension は i8、107 と同一形式）。Keep Alive は varint
  で 4/4 バイト消費し、応答（サーバー行き **0x00**。107 の
  0x0b とは ID だけが違う）の後のチャットがバニラのログに
  到達した。単体チャンクの1セクションはちょうど
  **12,288 バイト**（ブロック 8192＋ブロックライト 2048＋
  スカイライト 2048）。map_chunk_bulk の採取バイト列とも
  一致。ログイン時の圧縮しきい値は 256。
- **47⇔107 のパケット差分**（tools/packet-ids-47.py、
  identification は 26 世代の protocol.json で全 284 エントリを
  一意に同定）：プレイ状態で両方に存在するのは **104 件**、
  そのうち **ID が違うのは 96 件**。47 にだけ存在するのは
  ちょうど3件：**map_chunk_bulk（CB 0x26）、set_compression
  （CB 0x46）、update_entity_nbt（CB 0x49）**。107 にだけ
  存在するのは 10 件（advancements 等）。PacketIds 表の
  対象範囲（ステータス状態を除く）での数え方であり、先行調査の
  「ID 差分 94 件」は数え方の範囲が違う参考値。
- **形式差分**：チャンク、entityMetadata、エンティティ座標
  （スポーン系・テレポート・相対移動のデルタ i8 化を含む）、
  スポーン系への UUID 追加（107 側）、ウィンドウクリック／
  装備／タイトル等の細部を含む大きな差分。救いも確定済み：
  **スロット形式は 47⇔107 で完全同一**、**Join Game
  （dimension は両者 i8）と Respawn（dimension は両者 i32）は
  同形式**、**Keep Alive は両者 varint**（ID 付け替えのみ）。
- **レジストリ**：ブロック 198⇔214 種、アイテム 336⇔365 種で、
  **共有名の数値ID差分は 0**（1.9 の追加分だけが差分）。
  音は 47 に数値形式が無く名前文字列で運ばれる（47 の音名の
  正解表は Mojang 公式 1.8.9 アセットインデックスの
  sounds.json、ハッシュ
  `5e06ca070067486427a3167ade2ffe01623e5591`）。

## 3つの山場への対処

1. **チャンク**：ChunkTranslator に 47 専用経路を新設。
   758→47 はセクションごとに 4096 個の u16 LE
   （mapBlockState758To47 の値）＋ブロックライト 2048＋
   スカイライト 2048（次元がネザーでない場合）を書き、
   bitMap は **u16** で書く。47→758 は 12,288 バイト固定長
   セクションとして読み、Update Light を合成する。
   **map_chunk_bulk は `splitChunkBulk47` で単体チャンクへ
   分割**してから通常経路へ流す（bulk のメタは
   skyLightSent＋個数 varint＋チャンクごとの (x, z, bitMap
   u16) で、addBitMap は 47 には無い）。ブロックエンティティは
   47 のチャンクには載らないため落とす。
2. **entityMetadata**：SlotTranslator の 47 層で bitfield
   変換を行う。正準形（index u8＋型 varint＋値）⇔ ワイヤ形
   （先頭バイト `(型<<5)|(index&0x1F)`、終端 0xFF⇔0x7F）。
   型の対応は ChunkTranslator の型変換表が担う（758→47：
   varint→i32、chat 系→string、slot→slot、boolean→byte、
   position は i32×3 へ展開、particle／nbt／optuuid 等は
   落とす。47→758 は逆向き）。変換は型ベースでインデックスは
   保持する（エンティティ種別ごとのインデックス意味の世代間
   ずれは 107 世代と同じ忠実度）。
3. **座標と UUID**：SlotTranslator の 47 層でスポーン系・
   テレポートの座標を f64 ⇔ i32 固定小数点（×32、丸め＋
   クランプ）に付け替える。相対移動のデルタは i16 ⇔ i8
   （÷128）。spawn_entity_living の UUID は 47 向きでは
   落とし、758 向きではゼロ埋めする。named_entity_spawn は
   47 にだけ currentItem（i16）が付くため、47 向きでは 0 を
   書き、47 からは読み飛ばす。

## 近似と落としたもの

- **ブロック近似（1.9 追加 16 種）**：end_rod→torch（メタ写像
  [5,5,4,3,2,1]）、chorus 系・purpur 系・end_bricks→end_stone、
  beetroots→crops（メタ [0,2,5,7]）、grass_path→grass、
  end_gateway→obsidian、repeating／chain コマンドブロック→
  command_block（メタ保持）、frosted_ice→ice、
  structure_block→barrier。
- **アイテム近似（1.9 追加 29 種）**：ブロック系はブロック
  近似に従う。end_crystal→eye_of_ender、chorus_fruit 系→
  apple、beetroot→carrot、beetroot_seeds→seeds、
  beetroot_soup→mushroom_stew、dragon_breath→glass_bottle、
  splash／lingering ポーション→potion、spectral／tipped
  arrow→arrow、shield→banner、elytra→leather_chestplate、
  1.9 のボート 5 種→boat。
- **エンティティ**：shulker→ブタ（90）。**バイオーム**：
  the_void（127）→plains（1）。**パーティクル**：
  dragonbreath／endRod／damageIndicator／sweepAttack→
  explode（0）。fallingdust は 47 に存在しない。
- **音**：758 の数値 ID → 47 の名前への写像は 700 組、
  逆向きは 199 組を用意した。47 に対応音が無い 758 音は
  **落とす**。カテゴリは 47 に無いため落とし（逆向きは 0 を
  合成）、pitch は f32→u8 に量子化される。
- **落とすもの**：オフハンド装備（47 にオフハンドが無い）、
  メタデータの particle／nbt／optuuid／不在の optposition・
  optvarint、update_entity_nbt（47 専用パケット。正準形に
  対応が無いため落とす）、Block Place の heldItem（47 向きは
  空スロットを合成）、Open Window の shulker_box タイプと
  「EntityHorse」。Open Window のタイトルは平文抽出して
  JSON テキストで包み直す近似。
- **Teleport Confirm の合成**：47 クライアントは Teleport
  Confirm を送らないため、758 側バックエンドに対しては
  プロキシが teleportId を抜き出して合成しサーバーへ返す
  （47 向きの Position は teleportId を落とす）。Stage E で
  実ワイヤ確認した：fake47→実バニラ 1.9 の統合セルで、47
  クライアントへ届く Position が teleportId を落とした
  33 バイトの 47 形式であることをバイト長で固定し、合成は
  同じコード経路（`convertServerPlayOutcomeTo47`）を単体テスト
  （`ProxySession47Test`）で「toServer に Teleport Confirm が
  1件合成される」ことまで固定した。ただし合成した確認を
  バニラ側がどう扱ったかは外部から観測できない（バニラに
  ログ・切断等の痕跡が出ない）ため、裏付けは「剥離と合成が
  同一経路であること＋参加・移動・チャットの完走」に拠る。
  本物のゲームクライアントでの確認は未実施。

## 実装の形

- RegistryRemapper に 47 節（`RegistryTables47` 系と音名表
  `RegistryTables47Snd`）。ブロック実体とダメージ種別の
  47 関数は数値 ID がワイヤに無いため 110 へ委譲する。
  メニュー写像は無い（110 と同じ）。
- PacketIds に id47 列を先頭に追加し、47 専用パケット3件
  （PLAY_MAP_CHUNK_BULK／PLAY_SET_COMPRESSION／
  PLAY_UPDATE_ENTITY_NBT）を新設。ProtocolVersion の
  serverProtocolFor は "1.8"〜"1.8.9" を V1_8 へ写す。
- リソースパックは **pack_format 1**。レイアウトは 1.13 より
  前の旧形式。フォールバック順は **47 → 107 → 108 → 109 →
  110 → 210 → …**。外部設定キーは `resource-pack.url-47`／
  `resource-pack.sha1-47`。
- アダプタモジュールは `detergentmc-adapter-v1_8`。
  内部サーバーは 1.8〜1.8.9 の指定でプロトコル 47 として
  扱い、Java 8 で起動する（VanillaServerHost の Java 8 対象に
  全10リリースを追加）。
- Bedrock→47：スポーンは専用の parseSpawnEntity47／
  parseSpawnEntityLiving47／parseNamedEntitySpawn47／
  parseSpawnExperienceOrb47（固定小数点座標・UUID 無し・
  currentItem 読み飛ばし。**種別番号が違う 498 用関数には
  流さない**）。Join Game は parseJoinGame47（107 へ委譲。
  dimension i8 は実測で同一と確認）。チャンクは専用の
  translate47（固定詰めパース、スカイライトは2回試行で確定）
  と normalizeBlockState／normalizeEntityType／normalizeItem
  の PROTOCOL_47 ケース。初期チャンクの Map Chunk Bulk は
  splitChunkBulk47 で分割して流す。

## 検証

- 分割検証 `split-verification-47/`（重複排除後の集計、Stage E
  最終コードでの再実行）：**4,556 件・合格 4,519・失敗 0・
  エラー 0・スキップ 37**。内訳は単体 4,485 件（protocol
  3,703 件、bedrock 458 件、core 254 件、adapters＋content
  70 件）と実バニラ統合 71 件。スキップ 37 件は環境条件付き
  自己スキップ（Stage A〜D 時点の 39 件から、jar 配置が揃った
  ことで 2 件が実行に回った）。最古プロトコル脱落の罠は
  `ClientAdapter47Test` で固定した（一覧の先頭が 47、107・
  108・109・110・210・315・316 が残り、計 41 プロトコル）。
- **Stage E（2026-11-19）**：47 の6コミットを 107 最終 HEAD
  `284da74` へ**競合なし**でリベース。ProxyIntegrationTest に
  新規3セルを追加し、107 の Stage E の68セルと合わせて
  **71 セル全合格**（メソッド名レベルの最終監査済み）：
  - fake47→実バニラ 1.8.9（内部47。Keep Alive 応答 ID 0x00、
    ネイティブなリソースパック応答の往復を含む）
  - fake47→実バニラ 1.9（内部107。47 クライアントへ届く
    Position が teleportId を落とした 33 バイトであることを
    実ワイヤで固定＝Teleport Confirm 合成経路の確認）
  - fake107→実バニラ 1.8.9（内部47。プロキシが空 hash を
    前置したリソースパック応答を実バニラ 1.8.9 が受け付け、
    Keep Alive 往復と2通目のチャット到達まで完走）
  - 回帰68セル（fake335→実バニラ 1.13 は既知の負荷フレーク
    で2回失敗したが、無変更の107ツリーでの対照実験でも
    同セルが失敗したため環境要因と切り分け、3回目に合格）
  - Stage E で見つけて直した不具合は3件：①ProxySession の
    コンストラクタ検証に内部47サーバーが無く接続が例外で
    落ちた（配線列挙漏れ）。②761→760段で Position が素通し
    され 761 専用の末尾バイトが残り、47 向け Position が
    758 形式のまま届いて Teleport Confirm も合成されなかった。
    ③Keep Alive の varint⇔i64 付け替えが 338 形式の ID で
    判定されており ID 0x00 の 47 では4境界とも発火せず、
    内部47サーバー向け応答が i64 のまま届いて実バニラ
    1.8.9 が DecoderException で切断した（47 専用の
    keepAliveTo47Wire／keepAliveFrom47Wire を新設し、
    `ProxySession47Test` の8件で①〜③を固定）。なお KA 修正の
    影響範囲は 47 の境界だけのため、統合セルの再実行は
    47 関係3セル＋フレーク1セルに限り、残り67セルは先行
    結果を採用した（単体検証は全件を最終コードで再実行）。

- Stage A では実バニラ 1.8.9 を起動して Status を確認し、
  Join Game／チャンク／チャンクバルク／Keep Alive の
  ワイヤ採取と protocol.json・レジストリの全面比較で差分を
  確定した（tools/packet-ids-47.py、tools/gen-47-tables.py、
  tools/verify-47-tables.py が検証スクリプト）。

## 残る制限

- **本物のゲームクライアントでの確認は未実施**（全バージョン
  共通）。Teleport Confirm 合成経路（47 クライアント × 新しい
  バックエンド）は偽クライアント＋実バニラの範囲で確認済み
  だが、実機でのテレポート挙動は未確認。
- **データパックが無い世代のため trigger ブリッジ（言語切替・
  hello 等）は 47 バックエンドでは使えない**（1.12 以前から
  引き継ぐ既知の課題、代替は未定）。
- **チャンクのブロックエンティティが 47 のチャンクでは
  運ばれない**（看板の文字・チェストの中身等は欠落する。
  Block Entity Data パケット自体は変換する）。
- resource_pack_receive の空 hash は、Stage E で実バニラ
  1.8.9 が受け付けることを確認済み（47 の応答形式は 107 と
  同じ hash＋result で、fake107→1.8.9 セルではプロキシが
  空 hash を前置して転送し、Keep Alive 往復と2通目の
  チャット到達まで完走した）。
- メタデータ変換は型ベースでインデックス保持のため、
  エンティティ種別ごとのインデックス意味が世代間でずれる
  項目は 107 世代と同じ忠実度に留まる。
- 近似の損失：1.9 追加ブロック 16 種・アイテム 29 種の
  見た目と機能、shulker の姿（ブタ代用）、音の欠落
  （対応名が無い 758 音）と pitch 量子化、相対移動デルタの
  量子化（÷128）。
- Declare Recipes／Statistics／Tags／World Event ID は全方向で
  未実装（落とす）のまま（全バージョン共通）。
