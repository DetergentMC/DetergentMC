# 1.15.2クライアント／サーバー（プロトコル578）の対応

DetergentMC に **578（Minecraft 1.15.2）** を追加した記録。隣接バージョンは
758（1.18.2）で、間の 579〜757 はこのツリーには存在しない（別担当の作業場所で
736 以下を進行中）。そのため 578⇔758 の一段は、ViaVersion のような中間の
段階変換ではなく、実データから作った直接の対応表でつないでいる。

## 確定した事実（すべて実測）

- **バニラ 1.15.2 サーバーjar**：piston-meta から取得。
  SHA-1 `bb2b6b1aefcd70dfd1892149ac3a215f6c636b07`（照合済み）、
  36,175,593 バイト。`run/vanilla/server-1.15.2.jar`（リポジトリには含めない）。
- **version.json**：protocol_version **578**、pack_version は
  resource **5** / data **5**、world_version 2230、javaVersion は
  jre-legacy（**Java 8 世代**）。
- **レジストリ件数**（jar の data-generator ダンプで実測）：
  ブロック状態 11,337（758 は 20,342）、ブロック 680、アイテム 884、
  エンティティ 103、音 810、パーティクル 62、バイオーム 75、
  ブロックエンティティ 33、エンチャント 37、メニュー 23、エフェクト 32、
  絵画（motive）26。
- **絵画とエフェクトは表を持たない**：motive 26 件・mob_effect 32 件は
  578 と 758 で名前も順序も完全一致（実測）。そのまま通す。
- **パケットID**：表全体 281 件中、578 に存在するのは 148 件。
  758 と ID が違うのは 83 件。578 にだけあるのは 8 件
  （Spawn Entity Weather 0x02、Confirm Transaction 0x13、Entity 0x2c、
  Combat Event 0x33、World Border 0x3e、Title 0x50、
  Confirm Transaction（サーバー行き）0x07、Crafting Book Data 0x1d）。
  設定フェーズのパケットは 578 には無い。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 578↔758 の世代差（要点）

578 は 1.16 より前の世代で、758 とは構造から違うものが多い。

- **Join Game**：entityId i32、gamemode u8（hardcore はビット3）、
  dimension **i32**（0=overworld、-1=the_nether、1=the_end）、hashedSeed i64、
  maxPlayers **u8**、levelType 文字列、viewDistance varint、
  reducedDebugInfo、enableRespawnScreen。全 26 バイト（実バニラの
  キャプチャで確認）。codec も worldNames も無い。
  578→758 では同梱リソース `codec-758.bin`（実バニラ 1.18.2 の Join Game
  から採取した既定 codec、23,595 バイト）で 758 形式を合成する。
- **チャンク**：セクションはビットマスク方式（16 節、高さ 256 固定）、
  バイオームは 1024 個の int 配列、ブロックエンティティは x/y/z/id 入りの
  NBT そのもの。光データはチャンクに無く、別パケット（Update Light）。
  ビット詰めは**ロング境界をまたぐ旧方式**（heightmap が 9bit×256＝
  ちょうど 36 ロングになることで実測確認。758 の非またぎ方式とは逆）。
  heightmap の値は絶対 Y（758 は次元 minY 基準。overworld では +64 ずれる。
  nether/end はずれなし）。
- **Update Light**：マスクは単一 varint、光配列は長さ前置き。
  578 のビット j（0=真下、1〜16=セクション、17=真上）は 758 のビット j+4
  に対応する。578 クライアント向けには、758 チャンク末尾の光を抜いて
  Update Light を別途合成し、逆方向はチャンク到着までバッファする。
- **Login Success**：UUID は**文字列**（758 はバイナリのみ。**properties フィールド自体が無い**＝22バイト。properties が付くのは 759 から。実バニラ 1.18.2 の実パケットで確認した。従来の758対応はこの前提を誤っており、758サーバーからの Login Success を内部形式へ正規化する変換が存在しなかった。今回 `translateLoginSuccess758To765`（properties 0件を付加）を追加して修正した）。
  Login Start は名前のみ（758 と同じ）。
- **チャット**：クライアント行きは {message JSON, position i8} で
  送信者 UUID が無い（758 は末尾に UUID。578 化では落とし、758 化では
  nil UUID を補う）。署名付きチャットは 759 からなので 578 には無い。
- **出現パケットが5種に分かれる**：Spawn Entity（758 と同形式、種別だけ
  変換）、Spawn Entity Living（同形式）、Named Entity Spawn（578 は末尾に
  メタデータ付き → 758 化では分離して Entity Metadata として送る）、
  Spawn Entity Painting（絵画 26 件が同一順序のため素通し）、
  Spawn Entity Weather（758 に無い → lightning_bolt の Spawn Entity に
  変換。逆も同じ）。
- **Entity Metadata の型番号は旧式**（0〜18）：6=slot、13=blockstate、
  15=particle。758 も同じ旧式番号（下の「見つかった罠」参照）。
- **ウィンドウ系の差**：Set Slot は 758 に stateId が増えた。Window Items は
  578 が windowId u8＋count i16 で carried item 無し。Entity Equipment は
  578 が1スロット1パケット（758 はリスト → 578 化ではパケットを分ける）。
  Window Click は 758 の changedSlots を 578 では再現できないため、
  クリックしたスロット1件だけを近似として送る。
- **Multi Block Change**：578 は recordCount varint＋varlong レコード、
  758 は suppressLight bool＋配列。**Block Entity Data**：578 は
  position＋action u8＋id 入り NBT、758 は position＋type varint＋NBT
  （578 化では action を 0 で補う＝制限あり）。
- **タイトル／戦闘／ワールドボーダー**：578 は統合パケット（Title 0x50、
  Combat Event 0x33、World Border 0x3e）で、758 の分割パケットと相互に
  組み替える。World Border の INITIALIZE は警告時間と警告ブロックの
  順序が 578 と 758 で逆（変換で入れ替える）。
- **Resource Pack Send**：578 は {url, hash} のみ（forced／prompt は 1.17 から）。
- **Edit Book**：578 は本アイテムのスロット＋署名フラグ（タイトルは本の
  NBT 内）。758→578 方向は本アイテムを合成できないため落とす。
- **Crafting Book Data／Confirm Transaction**：758 に対応物が無いため落とす。

## レジストリ変換の方針

- 対応表は `detergentmc-protocol/tools/gen-578-tables.py` が実データ
  （jar のダンプ＋ViaVersion の mapping-1.15／mapping-1.18）から生成し、
  `verify-578-tables.py` で全 37,955 件を検証済み。
- 名前一致を基本に、改名（grass_path↔dirt_path、zombie_pigman↔
  zombified_piglin、barrier↔block_marker、バイオーム名変更 10 件）を
  個別表でつなぐ。
- 578 に無い 758 要素の代替：深層岩系→石系、クリムゾン／ワープド木材→
  オーク系、銅系→鉄系、ネザライト装備→ダイヤ装備、axolotl→tropical_fish、
  goat→sheep、glow_squid→squid、piglin→zombie_pigman、marker→
  area_effect_cloud、smithing→anvil、sculk_sensor→DROP など。
- 特殊ケース：**大釜**は level を保持（578 cauldron[level>0] ⇔
  758 water_cauldron[level]、level=0 ⇔ cauldron）。**壁**は bool ⇔
  none/low/tall を変換（up は両方 bool でそのまま）。**ジグソー**は
  facing ⇔ orientation を変換。
- 音は名前ベースの代替も併用（353 件をルールで解決、46 件は
  block.stone.break に代替）。

## 接続の流れ

578 は 758 の1段下として配線した（`ProxySession`）。

- **578クライアント**：受信パケットはまず 578→758 変換を受け、
  既存の 758→759→…→763 の段階変換に乗る。送信は逆順で、最後に
  758→578 変換を受ける。
- **内部578サーバー**：サーバーのパケットは 578→758 に登ってから
  既存の 758 経路（`routeServerPlay758`）に入る。クライアント→578
  サーバーは既存の下り段階変換の最後に 758→578 を足す。
- ログインは 758 と同じ扱い（設定フェーズなし、Login Start は名前のみ）。

## リソースパック

- 578 用パックは pack_format **5**、`detergentmc-pack-1.15.2.zip`
  （20 個目）。設定キーは `resource-pack.url-578`／`resource-pack.sha1-578`。
- 内部サーバーが 1.15.2 のとき、578 クライアントにはバニラ側のパック
  中継が優先され、DetergentMC のパックは差し込まない（従来方針と同じ）。

## 内部サーバーとしての 1.15.2

- `minecraft-version: "1.15.2"` で内部プロトコル 578 を選ぶ。
- 1.15.2 は **Java 8** が必要。`VanillaServerHost.javaBinaryFor` に
  Java 8 の選択を追加した（`detergentmc.java8.home` システムプロパティ →
  `DETERGENTMC_JAVA8_HOME` 環境変数 → 現在の java.home が Java 8 の場合）。
  Temurin 8 は `~/workspace/.tools/jdk-8/` に配置。
- データパック形式は 5（`DatapackGenerator.PACK_FORMAT_1_15_2`）。

## Bedrock → 578

- `JavaBackendConnection` に 578 分岐を追加（ログインは名前のみ、
  設定フェーズなし、チャットは旧式、Player Info は旧式、Join Game は
  数値次元で minY 0／高さ 256／バイオームは組み込み 75 件、
  Respawn は次元 i32、出現は 758 と同形式＋種別の事前変換）。
- チャンクは `BedrockChunkTranslator` に 578 専用の読み取りパスを追加
  （マスク方式・またぎ詰め・バイオーム配列）。ブロック状態は
  578→758→…→763→765 と段階表を合成して正規化する。
- エンティティ種別・アイテムも 578→758 表経由で 765 に正規化する。

## 見つかった罠（後続の担当へ）

- **既存 758 コードのメタデータ型番号が 761 式だった**：合流済みの
  `ChunkTranslator.translateMetadataValue758`／`skipMetadataValue758` は
  javadoc で「758・759同一」と言いながら 1.19.3 以降の型番号
  （2=varlong、7=slot、14=blockstate…）を使っている。実測の 758／759／578
  は旧式番号（6=slot、13=blockstate、15=particle）。578 の変換は正しい
  旧式番号で実装した。既存側は今回の範囲外として**直していない**
  （統合テストは Join Game 到達までしか見ていないため、実害の有無は
  未確認。別途確認が必要）。
- **578 の直接パレット（14bit）の詰め方式**：セクションのビット詰めは
  heightmap と同じ BitArray クラスを使う前提で「またぎ方式」とした。
  フラットワールドの実測では bits=4（64 の約数）しか得られず、
  直接パレットでの境界またぎは実測で切り分けられていない。
- **バイオームのセル順序**：578 の 1024 配列と 758 のセクション別
  コンテナのセル順序が同じ前提で変換している。実測の両キャプチャとも
  バイオーム一様だったため、混在セルでの順序は未検証。
- **ProxySession のサーバープロトコル検証**：コンストラクタの許可
  リストに 578 が無く、内部 578 ではセッション自体が作れなかった
  （テストで発見して修正済み）。新しいプロトコルを足すときは
  このリストも見ること。

## 送らない・落とすパケット（578関係）

- Crafting Book Data／Confirm Transaction（578→758）：対応物が無い。
- Edit Book（758→578）：本アイテムの NBT を合成できない。
- Ping（758→578）：578 に無い。
- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応
  （既存の制限と同じ）。

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**（全組み合わせ共通）。
  検証はフェイククライアント＋実バニラの統合テストと単体テストのみ。
- **2人プレイの可視性は検証できない**：実バニラはフェイククライアントに
  Player Info／出現パケットを送らないため（従来と同じ制限）。
- **764以上のクライアント → 内部578サーバー**：設定フェーズの合成は
  761〜763 サーバー向けしか無く、758 と同じく汎用経路になる
  （758 でも統合テスト未実施の領域。578 も同じ扱い）。
- Block Entity Data の 758→578 では action バイトを 0 で補う。
- Window Click の changedSlots は近似（クリックしたスロットのみ）。
- Update Jigsaw の 578→758 は name／jointType を固定値で補う近似。
- チャンクの光は 578 では別パケットのため、チャンクより先に光が
  届いた場合はバッファ（上限 512 件）から合成する。

## テスト

- 単体テスト：PacketIds578Test、ProtocolVersion578Test、
  ClientAdapter578Test、ClientJoinFlow578Test、RegistryRemapper578Test、
  ChunkTranslator578Test、PlayTranslator578Test、ProxySession578Test、
  ResourcePackBuilder578Test、ResourcePackMode578Test、
  ResourcePackServer578Test、RealVanilla758Join578Test（実 codec＋
  実形式 Login Success での 578 参加。protocol）、
  AdapterDiscoveryTest／ServerProtocolWiringTest／ResourcePackWiringTest／
  DatapackGeneratorTest／VanillaServerHostTest への追加（core）、
  JavaBackendConnection578Test／BedrockChunkTranslator578Test（bedrock）。
- 統合テスト（ProxyIntegrationTest）：フェイク578クライアント→
  実バニラ 1.15.2、フェイク578クライアント→実バニラ 1.18.2、
  フェイク758クライアント→実バニラ 1.15.2 の3セル（いずれも合格）。
  あわせて 758 関係の回帰セル（フェイク758→1.18.2／1.19／1.20.1、
  フェイク759→1.18.2、フェイク763→1.18.2）も実バニラで再実行し合格
  （Login Success 修正の確認を含む）。この worktree には他のバージョンの
  バニラ jar が無いため、残りの ProxyIntegrationTest はスキップされた。
- 検証は分割検証のみ（フルクリーンビルドはしない）。結果 XML は
  `split-verification-578/` に集計する。集計結果（重複排除後）：
  **3,178件・合格3,037・失敗0・エラー0・スキップ141**。スキップの内訳は
  Bedrock の UDP 系（サンドボックスが UDP 送信を禁じる）・キャプチャ系と、
  バニラ jar が無いための ProxyIntegrationTest 103件。
