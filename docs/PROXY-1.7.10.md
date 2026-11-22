# 1.7.6〜1.7.10クライアント／サーバー（プロトコル5）の対応

DetergentMC に **5（Minecraft 1.7.6〜1.7.10 の全5リリース）** を
追加した記録。隣接バージョンは 47（1.8系）で、このツリーでは
47 の1段下として配線した。47⇔5 はパケット ID 差分が **0件**
という珍しい組み合わせだが、形式差分は60パケットに及び、
大半は系統的な型差分の反復である（下記）。

> **状態（2026-11-20時点）：Stage E 完了。**
> 分割検証の件数は末尾の「検証」節を参照。ProxyIntegrationTest への
> 統合セル追加と実バニラ統合テストは実施済み。**本物の
> ゲームクライアントでの確認は未実施**（全バージョン共通）。

## 番号対応（重要）

- **プロトコル 5 ＝ Minecraft 1.7.6／1.7.7／1.7.8／1.7.9／1.7.10**
  （先行調査で全9リリース（1.7.2〜1.7.10）の実バニラ Status を
  実測し、1.7.2〜1.7.5 がプロトコル 4、1.7.6〜1.7.10 が
  プロトコル 5 であることを確認。1.7.3 は実在する製品版で
  プロトコル 4 に属す）。代表 jar は 1.7.10 とする。
  プロトコル 4 は次段（5 の実装＋UUID 文字列の形と
  named_entity_spawn のプロパティ有無の付け替え）で対応する。
- **DataVersion の概念は無い**（47 と同じ。1.9 で導入）。
- **パケット圧縮の仕組み自体が存在しない**（47 と同じ世代的
  特徴。Set Compression パケット自体が無い）。なお現行
  ProxySession の「play 0x03＝Set Compression なら切断」判定は
  ログイン状態でのみ生 ID で判定しており、1.7 の play 0x03
  （update_time）で誤爆しないことを Stage B3 で確認した
  （play 側の振り分けはパケット名ベース）。
- 降下順は 47 → 5 → 4。4 より下（1.7.2 未満）はフレーミング
  世代そのものが断絶しており（varint 無し・文字列 UTF-16BE・
  状態機械無し）、別フロントエンドの仕事になる。

## 確定した事実（すべて実測）

- **バニラ 1.7.10 サーバーjar**：piston-meta から再検証済み。
  SHA-1 `952438ac4e01b4d115c5fc38f891710c4941df29`
  （9,605,030 バイト、クライアント jar の SHA-1 は
  `e80d9b3bf5085002218d4be59e668bac718abbc6`）。pack_format は
  **1**、内部サーバーは **Java 8**。data-generator は無い。
  1.7.6 のサーバー SHA-1 は
  `41ea7757d4d7f74b95fc1ac20f919a8e521e910c`。
- **実バニラ 1.7.10 のワイヤ採取**（Stage A）：
  - Login Success の UUID はダッシュ付き文字列。
  - Join Game はちょうど **16 バイト**消費（47 の17バイト＝
    末尾の reducedDebugInfo 付きとは1バイト差）。
  - player_info は名前ベース旧形式そのまま。
  - Keep Alive の本体は **4バイト固定（i32）**（47 は varint）。
    ID は双方向とも 0x00。
  - map_chunk_bulk のヘッダは「個数 i16＋データ長 i32 が先頭、
    メタ配列は末尾、addBitMap あり」で確定。
  - チャンクのセクション内部は **種類別グループ化**で確定：
    全セクション分のブロック ID 配列（4096×N）→ メタ nibble
    → ブロックライト → スカイライト → add nibble の順。
  - チャットは約100文字超で切断される（150文字で実測）。
- **47⇔5 のパケット差分**（tools/packet-ids-5.py で確定）：
  共通エントリ **95 件、ID 差分 0 件**。47 にだけ存在するのは
  ちょうど12件（1.8 の追加分：title、world_border、
  resource_pack_send、set_compression（CB／ログイン共）、
  camera、combat_event、difficulty、playerlist_header、
  update_entity_nbt、resource_pack_receive、spectate）。
  5 にだけ存在するパケットは 0 件。名称差分が2件
  （teams→scoreboard_team、entity_update_attributes→
  update_attributes。ID は同一）。
- **スポーン種別 ID は実ワイヤで確定**（Stage A）：
  モブは Creeper 50 〜 Villager 120 の 1.8 と同じ番号で、
  Endermite 67／Guardian 68／Rabbit 101 は 1.7 に存在しない。
  オブジェクトは Boat 1 〜 FishingHook 90 で、ArmorStand 78 は
  存在しない。吊り下げ系（Egg 62、ItemFrame 71、LeashKnot 77、
  FishingHook 90）は実スポーンで採取できなかったため、
  1.7⇔1.8 の継続性と minecraft-data から採用した（未採取と
  明記）。FallingSand の data 詰めは **blockId | (meta << 16)**
  で確定。
- **音の名前集合**：1.7.10 のアセットインデックスから
  **229 件**を取得（1.8.9 の 246 件との差は guardian 13＋
  rabbit 4＋item.fireCharge.use の 17 件で説明が付く）。
  tools/sounds-5.json に保存。

## 形式差分と対処

形式差分は60パケットに及ぶが、大半は次の系統差分の反復：

1. **entityId が i32（5）⇔ varint（47）**（約20パケット横断）。
   SlotTranslator の 5 経路で個別に付け替える。
2. **ブロック座標がバラ数値3つ ⇔ 64ビット詰め込み**。
   y の幅が u8／i16／i32 の3種あり、パケットごとに違う
   （writePosition5 の kind で吸収）。
3. **チャンクの中身が別形式**：1.7 は u8 の ID 配列＋nibble 群
   （種類別グループ化、zlib 圧縮）、47 は u16 LE 固定詰め。
   bulk のヘッダ配置も違う。ChunkTranslator に 5 専用経路を
   新設（translateChunkData5To758／translateChunkData758To5／
   splitChunkBulk5）。ブロック ID は最大175で add 配列は
   実質常に0。
4. **player_info が名前ベース旧形式**、**named_entity_spawn が
   UUID 文字列＋名前＋プロパティを持つ**。ProxySession は
   セッション内の playerInfo5ByUuid 表（UUID→名前・プロパティ）
   を保持し、named spawn の合成に使う。名前が分からない
   プレイヤーは名前 "" で送る（フォールバック）。
5. **Keep Alive が i32（5）⇔ varint（47）**。ID は 0x00 同士。
   専用の keepAliveTo5Wire／keepAliveFrom5Wire で i64⇔i32 を
   付け替え、応答時はセッションの復元表で元の i64 に戻す。
   （既存の keepAliveTo338Wire は 338 の ID で照合するため
   47／5 の 0x00 には一致しない。47 側の同経路は 47 の
   Stage E で専用ヘルパーにより修正・確認済み。5 の経路は
   本 Stage E の統合セル fake47→実 1.7.10／fake5→実 1.8.9 で
   両方向とも実ワイヤ確認済み。）
6. **Join Game は末尾1バイト差のみ**（1.7 は16バイト、
   reducedDebugInfo 無し）。PlayTranslator.parseJoinGame5 を
   専用に用意。Respawn は同一形式のため 47 の関数へ委譲。

流用できたもの：**entityMetadata のヘッダ形式は 47 と完全同一**
（bitfield・終端 0x7f・型8種。値のバイト幅も 47 のワイヤ幅で
統一）、座標の i32 固定小数点も 47 と共通。スロット骨格は
同一だが **NBT が gzip 付き compressedNbt（5）⇔ 生 optionalNbt
（47）** で、SlotTranslator の 5 経路に gzip 変換層を1枚入れた。
アイテムの damage は 47 側の表現では NBT の "Damage" タグに
載せ替える（401 系で確立した流儀）。

**内部表現の設計判断**：5 のハイブリッド形式は 47 の
ハイブリッド形式と完全同一（レイアウトもレジストリ番号も
47 番号）にし、47⇔5 の番号付け替えはワイヤ段でのみ
合成表（mapItem758To5 ∘ mapItem47To758 等）で行う。
これにより 47 用に検証済みの変換器をそのまま再利用できる。

## レジストリと近似

- 共有名の数値 ID 差分は **0**（ブロック 176⇔198種、
  アイテム 303⇔336種）。変換表は 47 の完成表を土台に
  名前照合で合成した（tools/gen-5-tables.py、検証は
  tools/verify-5-tables.py）。
- **近似（1.8 の追加分 → 1.7 の代替）**：5種の木のドア→
  wooden_door、5種のフェンス→fence、5種のフェンスゲート→
  fence_gate、red_sandstone→sandstone、red_sandstone_stairs→
  sandstone_stairs、stone_slab2→stone_slab（メタ 0→1／8→9）、
  double_stone_slab2→double_stone_slab、
  daylight_detector_inverted→daylight_detector、
  standing_banner→standing_sign、wall_banner→wall_sign、
  iron_trapdoor→trapdoor。アイテム側はドア／フェンス類→
  同名の 1.7 品、armor_stand→item_frame、barrier→glass、
  mutton→chicken、cooked_mutton→cooked_chicken、rabbit→
  chicken、cooked_rabbit→cooked_chicken、rabbit_stew→
  mushroom_stew、rabbit_foot→feather、rabbit_hide→leather、
  prismarine→quartz_block、prismarine_shard→quartz、
  prismarine_crystals→glowstone_dust、sea_lantern→glowstone、
  スライムブロック（アイテム）→sponge。
- **エンティティ近似**：endermite→pig、guardian→squid、
  rabbit→pig、armor_stand→pig（いずれも 47 番号上での付け替え）。
- **落とすもの**：PLAY_MAP は双方向とも落とす（1.7 の地図
  バルク形式を検証できなかったため。制限として明記）。
  world_particles の data 部は 5 向けで落とし、ハイブリッド
  向けでは代表値を合成する。タイトル系は 47 と同様に
  チャットへ落とす。リソースパック応答は 1.7 に存在しない
  ため、申し出は MC|RPack プラグインメッセージで一方的に
  送る（下記）。
- **minecraft-data pc/1.7 の罠**：blocks.json に 1.8 追加の
  iron_trapdoor（167）が混入している（実 1.7.10 クライアント
  jar の言語ファイルに tile.iron_trapdoor が無いことで確認）。
  生成・検証スクリプトで明示的に除外した。entities.json は
  mob／object の番号空間が混在しているためスポーン種別表は
  実ワイヤで確定した（上記）。

## リソースパック（プロトコル5）

- pack_format は **1**（47 と同じ値）。フォールバックは
  5→47→107→… の順（ResourcePackMode／ResourcePackServer に
  pack5 を配線、設定キーは `resource-pack.url-5`／
  `resource-pack.sha1-5`）。
- 配送経路が 47 と違う：1.7 には PLAY_RESOURCE_PACK が無い
  ため、ProxySession が申し出を **MC|RPack プラグイン
  メッセージ**（データ部＝URL の UTF-8 バイト列）へ変換して
  送る。1.7 世代に sha1 の仕組みは無いが、設定キーは流儀に
  合わせて sha1-5 を用意した。
- **Stage E で実ワイヤ確認済み**：5 のプラグインメッセージ
  形式は「チャンネル文字列＋データ長（i16）＋データ」で、
  実バニラ 1.7.10 自身が送る MC|RPack（長さ 48 の URL）が
  まったく同じ形式で届くことを統合セル fake5→実 1.7.10 で
  確認した。プロキシの変換（47 形式＝長さ無し ⇔ 5 形式＝
  i16 長付き）も fake47→実 1.7.10／fake5→実 1.8.9 の両セルで
  実ワイヤ確認済み。申し出は一方通行（1.7 にリソースパック
  応答パケットは無い）。

## Bedrock → 5

- Join Game は末尾1バイト差があるため、47 のパーサへ無条件に
  合流させず **parseJoinGame5 専用分岐**を JavaBackendConnection
  に用意した（498 用関数には流していない）。
- チャンクは BedrockChunkTranslator.translate5 が 5 形式を
  展開・47 形式へ詰め替えてから 47 経路へ委譲する（ブロック
  値は 47 と同じ番号空間）。Map Chunk Bulk は splitChunkBulk5
  で分割する。
- スポーン系は parseSpawnEntity5／parseSpawnEntityLiving5／
  parseNamedEntitySpawn5／parseSpawnExperienceOrb5 を用意
  （Named は UUID 文字列＋プロパティを読む 5 専用形式）。
- Player Info は名前ベースのため recordPlayerInfo5 で
  オフライン UUID を合成して記録する（ゲームモードは
  パケットに無いため 0 で近似）。
- Respawn の次元は i8 で読む専用分岐を用意した（578 系の
  i32 読みへは流さない）。

## Stage E で見つけた実バグ（修正済み）

1. **サーバー行きのワイヤ付け替えが 5 バックエンドで一切効いて
   いなかった**：ProxySession の内部5分岐が、サーバー行きパケット
   にも `SlotTranslator.clientboundTo5`（CLIENTBOUND 名前解決）を
   誤って適用していた。名前が引けない／別パケットに解決される
   ため、移動（Player Position）の stance 合成、スロット形式、
   ブロック座標のバラ数値化、プラグインメッセージの i16 長付け、
   entityId の i32 化がすべて素通りになり、47 クライアントの
   移動を受けた実バニラ 1.7.10 が DecoderException で切断した。
   サーバー行き専用の `SlotTranslator.serverboundTo5`
   （serverboundFrom5 の逆変換）を新設して差し替えた。あわせて
   腕アニメーション（ハイブリッドは空ペイロード）向けに、Join
   Game 先頭の entityId を ProxySession が捕捉して合成する
   （未捕捉時は落とす）。単体テスト（ProxySession5Test）と
   統合セル fake47→実 1.7.10 で固定・実ワイヤ確認済み。
2. **Teleport Confirm の合成が 5／47 バックエンドへ漏れていた**：
   47／5 クライアント向け変換が落とした teleportId の確認応答を
   763 形式（SB 0x00＋varint）で合成してサーバー行きへ直接
   送っていたが、Teleport Confirm が存在しない 5 バックエンド
   では SB 0x00 が Keep Alive（i32）と解釈され、実バニラ
   1.7.10 が DecoderException で切断した（47 バックエンドでも
   誤った Keep Alive 応答として届いていた）。合成はバック
   エンドのプロトコルに Teleport Confirm がある場合だけにし、
   ID もバックエンドのプロトコルで引くよう直した。
3. **47 クライアントの Login Success が 765 形式のまま届いて
   いた**（申し送りの確認で実バグと確定）：`adjustLoginSuccess
   Payload` の文字列 UUID 変換チェーンに `is47()` が入って
   おらず、47 クライアントがバイナリ UUID＋プロパティ列の
   765 形式を受け取っていた。47 の Stage E では fake47 が
   Login Success を解釈しなかったため見えなかった。`is47()`
   を追加し、単体テスト（ProxySession47Test）と統合セル
   fake47→実 1.7.10 の厳密パースで固定・実ワイヤ確認済み。

## Stage C で見つけた実バグ（修正済み）

生成直後の RegistryTables5Snd.REV（1.7 の音名→758 の逆引き）
が **空表**になっていた。原因は生成器のアンカーずれで、
検証器の all(...) チェックが空表でも素通りしていた。影響は
「1.7 の音名（dig.stone 等）が新しいクライアントへ未変換で
届く」こと。47 の REV を 1.7 の音集合で絞った **189 件**で
再生成し、生成器・検証器に空表チェックを追加した。

## 検証

- 分割検証（split-verification-5/、Stage E 最終コードで
  新鮮に再実行）：全モジュール合計 **4,601 件・合格 4,564・
  失敗 0・スキップ 37**（重複排除後の集計。統合セル74件を
  含む）。Stage A〜D 時点で唯一失敗していた bedrock の
  JavaBackendConnectionEntityTest（765 向け）は今回の実行
  では再現しなかった（土台でも出る環境フレークと判断）。
  スキップ 37 件は従来どおりの内訳（Bedrock UDP・マトリクス
  統合・キャプチャ系）。
- 実バニラ統合テスト（Stage E）：ProxyIntegrationTest に
  新規3セル（fake5→実 1.7.10、fake5→実 1.8.9、fake47→実
  1.7.10）を追加し、47 の Stage E の全71セルを回帰として
  合わせた **74 セルすべてが合格**。各セルで参加→チャット→
  Keep Alive 応答→応答後の2通目のチャット到達まで確認した。
  Keep Alive の i32⇔varint 付け替えは fake47→実 1.7.10
  （47 応答 varint ⇔ バニラ i32）と fake5→実 1.8.9 の
  両方向で実ワイヤ確認済み。MC|RPack の配送と符号化も
  3セルで実ワイヤ確認済み（上記「リソースパック」節）。
  回帰セル fake335→実 1.13 は初回だけタイミング失敗し、
  再実行で合格した（従来から知られる環境要因のパターンと
  同一）。
- 47 最終 HEAD（`67f073d`）へのリベースは競合1件
  （ProxySession の白リスト・Keep Alive ヘルパー挿入位置）
  を意味を保って解消した。
- 本物のゲームクライアントでの確認は未実施。

## 残る制限

- PLAY_MAP を双方向とも落としている。
- 5 バックエンドへのブロック設置は heldItem を運べない
  （ハイブリッドに無いため空スロットで送る。設置自体は
  サーバー側の所持アイテムで行われる）。
- 5 バックエンドへのクライアント設定は difficulty を 0 で
  送る（ハイブリッドに無い。専用サーバーでは不使用）。
- 5 バックエンドへの腕アニメーションはアニメーション種別を
  腕振り（0）で送る（ハイブリッドに種別が無い）。
- 1.7 バニラのチャット上限（約100文字）のため、新しい
  クライアントから長いチャットを 5 バックエンドへ送ると
  プロキシのバックエンド接続が切断される場合がある。
- データパックが無い世代のため、47 以下と同様にトリガー
  ブリッジ（言語切替・hello 等）は 5 バックエンドでは
  使えない。
- Egg／ItemFrame／LeashKnot／FishingHook のオブジェクト ID は
  実ワイヤ未採取（継続性＋minecraft-data から採用）。
- multi_block_change のレコードビット順は protodef で確認し
  実ワイヤ未採取。
