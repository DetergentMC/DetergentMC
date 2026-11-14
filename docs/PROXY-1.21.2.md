# 1.21.2／1.21.3クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.21.2／1.21.3クライアント（プロトコル768）は、内部プロトコルが
763／764／765／766／767／768のどのサーバーにも参加できます。内部768のときは
素通し（リレー）、それ以外は上向き・下向き変換です。767との違いは大きく、
**既存パケットのIDは145件変わり、新規13件・削除2件**です。レジストリも
大きく変わり、ペールガーデン（ペールオーク・クリイキング）の追加で
ブロック状態が634件増え、ボートが木の種類ごとに20種に分かれ、
データコンポーネントの形式も変わりました。くわしい差分は下の各節を参照
してください。767の仕組みは `docs/PROXY-1.21.md`、766は
`docs/PROXY-1.20.5.md`、逆向きの考え方は `docs/PROXY-REVERSE.md` を参照
してください。

全36通り（クライアント763／764／765／766／767／768 × サーバー763／764／765／
766／767／768）の対応マトリクスは `docs/VERSIONS.md` の「1.21.2対応：
全36通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用・767用・768用の6つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 767→768の差分（要点）

| 分野 | 767→768の差分 | 扱い |
| --- | --- | --- |
| パケットID | 既存222件のうち145件が変わる。新規はプレイの Sync Entity Position（0x20）・Move Minecart（0x31）・Player Rotation（0x43）・Recipe Book Add（0x44）・Recipe Book Remove（0x45）・Recipe Book Settings（0x46）・Set Cursor Item（0x5A）・Set Player Inventory（0x66）と、サーバー行きの Select Bundle Item（0x02）・Tick End（0x0B）・Player Input（0x28）の11件（＋除外した Ping Request 0x23・Legacy Server List Ping 0xFE）。削除は Unlock Recipes と Steer Vehicle（Player Input に置き換え） | 新規・削除パケットは古いクライアントには送らない（落とすか別パケットに直す）。768同士は素通し |
| Login Success | 767の末尾の `strictErrorHandling` bool が768で廃止された | 768→767では `false` を付け足す。768→765以下ではそのまま（765も bool が無い） |
| Join Game／Respawn | SpawnInfo の末尾に `seaLevel` varint が追加された | 768→767では seaLevel（63）を付け足し、逆向きは落とす。Respawn の copyMetadata はそのまま |
| Synchronize Player Position | teleportId が先頭に移動し、dx／dy／dz（f64×3）が追加され、flags が u8→u32 になった（相対フラグに dx／dy／dz／yawDelta が追加） | 768→767では dx／dy／dz と新しいフラグを落とし、teleportId を後ろに戻す。位置そのものは変換できる |
| 移動（サーバー行き） | `onGround` bool が MovementFlags u8（bit0=onGround、bit1=hasHorizontalCollision）に変わった | 768→767では bit0 だけを bool に戻し、hasHorizontalCollision は落とす。逆向きは bit0 だけ立てる |
| Update Time | 末尾に `tickDayTime` bool が追加された | 768→767では落とす。逆向きは `false` を付ける |
| Player Info | `update_list_order` アクション（bit 6）が追加され、プレイヤーごとに `listPriority` varint が付く | 768→767では bit を落とし、per-player の varint を落とす。逆向きは素通し |
| Set Cooldown | 767はアイテムID varint、768はクールダウングループ名（文字列） | 既定のグループ名はアイテム自身のレジストリ名（`minecraft:ender_pearl` 等）。エンダーパール・コーラスフルーツ・ヤギの角の3件だけ対応し、それ以外は落とす（見た目だけの制限） |
| Explosion | 半径・影響ブロック・ノックバック・粒子／サウンド指定の形が変わった | 768の `playerKnockback`・`explosionParticle`・`sound` は767の形に直せないため、既定演出に置き換わる |
| Set Slot State | windowId は767も768も varint（同じ） | 素通し。767の `ContainerID` は u8 だが、Set Slot State だけは例外的に varint のまま |
| ContainerID | 767は u8、**768は varint**。Set Slot／Window Items／Trade List／Window Click／Enchant Item／Close Window の windowId が対象 | windowId は u8⇔varint で変換する。−1（カーソル）は768で5バイトの varint になる。Open Window の windowId は両方とも varint のため変換なし |
| レシピ | Craft Recipe Response／Request、Displayed Recipe は768で形式が変わった（レシピIDが名前→番号、RecipeDisplay 形式） | 変換できないため落とす（Declare Recipes は元々落としている）。手動クラフトは動く |
| Client Information（Settings） | 末尾に `particleStatus` varint（0=all／1=decreased／2=minimal）が追加された | 767→768では `0` を付け足す。768→767では落とす。**付けないと768のバニラが読み取り途中で切断し、落とさないと767のバニラが「1 bytes extra」で切断する**（両方とも実バニラの挙動で確認） |
| ブロック状態 | 26,684→27,318件（+634、削除0）。ペールオーク・クリイキングハートの挿入で約26,662件の番号がずれる | 対応表で変換。768にしかないブロック状態は古いサーバー向けに石へ |
| アイテム | 1,333→1,375件（+42、削除0）。ペールオーク23件・色付きバンドル16件・クリイキングのスポーンエッグ・旗の模様2件 | 対応表で変換。768にしかないアイテムは古いサーバー向けに石へ |
| エンティティ | 130→150件（+22、−2）。`boat`／`chest_boat` が無くなり、木の種類ごとのボート20種（オーク〜ペールオーク、竹のいかだ含む）に分かれた。クリイキングとクリイキング（過渡）が追加 | 対応表で変換。767のボートは768ではオークのボートに、768のボートは767ではボートになる（**木の種類は維持できない**。下の「まだできないこと」を参照）。クリイキングは767向けにブタへ |
| 音 | 1,611→1,636件（+27、−2）。削除は `item.goat_horn.play` と `entity.goat.screaming.horn_break`。追加はクリイキング11件・クリイキングハート8件・スポナー5件・バンドル・ペールハンギングモス等 | 対応表で変換。無い音は既定の石の音へ。ヤギの角の2件は `item.goat_horn.sound.0`／`entity.goat.horn_break` へ |
| パーティクル | 109→111件。`trail`・`block_crumble` の追加 | 対応表で変換。dust は f32×3＋scale から i32色＋scale に、dust_color_transition は f32×6＋scale から i32色×2＋scale に変わる。768にしかない2件は767向けに落とす |
| 属性 | 31→32件。`tempt_range` の追加。9件の名前から接頭辞が外れた（`generic.burning_time`→`burning_time` 等） | Update Attributes を番号で変換する。`tempt_range` は767向けに落とす。属性名の表は protocol.json の Update Attributes キー表を使う（ViaVersion の表とは別物） |
| エンチャント | 42件で同じ顔ぶれ・同じアルファベット順 | 変換なし（identity） |
| データコンポーネント | 57→67種。`fire_resistant` が無くなり、`item_model`・`consumable`・`use_remainder`・`use_cooldown`・`damage_resistant`・`enchantable`・`equippable`・`repairable`・`glider`・`tooltip_style`・`death_protection` の11種が追加。`custom_model_data` は単一の数値から4配列の形式に変わった。`food` は食べる時間・食べた後のアイテム・効果が無くなり（`consumable` へ移動） | スロットの成分は種別ごとに変換する。767の `fire_resistant` は768向けに落とし、768の新11種は767向けに落とす。`food` の効果は768向けに落とす（下の「まだできないこと」を参照）。`custom_model_data` は767の数値を768の colors[0] に入れる（ベストエフォート） |
| ダメージ種別 | 47→49件。`ender_pearl`（8）・`mace_smash`（26）の追加。mace_smash は magic（27）の前に来る（アルファベット順） | Registry Data のエントリ配列を名前で並べ替える。768にしかない2件は767向けに落とす |
| バイオーム | 64件で変わらず（既定のサーバー。`pale_garden` は winter_drop データパックの中にあり、既定では無効のためレジストリに現れない） | バイオームパレットは素通し。winter_drop 有効時のレジストリ内の位置は未確認（範囲外のIDは plains へ） |
| ブロックエンティティ種別 | 44→45件。`creaking_heart`（10）が追加され、ピストン以降が1ずつずれる（vault は 44） | 種別IDを番号で変換する。creaking_heart は767向けに落とす |
| 絵画・旗の模様・トリム・ジュークボックスの歌 | 変わらず（50／43／18／19件） | 変換なし |
| メニュー | 25件で変わらず | 変換なし |

検証スクリプトは `detergentmc-protocol/tools/verify-768-tables.py`
（9群・171件のチェック）と `verify-reverse-tables.py`。本物のバニラ1.21.2／
1.21.3から Registry Data の順番をそのまま記録する統合テスト
（`RegistryOrderCaptureTest`）で、エンチャント・絵画・旗の模様・
トリム・ダメージ種別・ジュークボックスの歌・楽器・バイオームの順番が仮説通りであることを
確かめている（mace_smash=26／magic=27 を含む。バイオームは64件・plains=39で、
pale_garden は既定では現れない。楽器は8件・admire=0・yearn=7）。
実バニラが設定フェーズで送るのは12レジストリだけ（banner_pattern／chat_type／
damage_type／dimension_type／enchantment／instrument／jukebox_song／
painting_variant／trim_material／trim_pattern／wolf_variant／worldgen/biome）。
`minecraft:attribute` と `minecraft:block_entity_type` は送られないため、属性の
順番（burning_time=7／tempt_range=30 等）とブロックエンティティ種別の順番
（creaking_heart=10／vault=44）は ViaVersion の mapping-1.21.2.json と jar の
data ファイルに依り、ワイヤでの確認はできていない。

**設定フェーズの振り分けの注意：** 767クライアント→768サーバーの設定フェーズは、
専用の `ProxySession.handleClientConfiguration767To768`
（中身は `handleClientConfiguration766To768` と同じ）を通す。766と767の
設定フェーズの番号・形式は同じため、内容は766→768と同じでよい。ただし振り分けで
`is767()` を先に見ると、767向けの古い処理（764／765向けの番号付け替え）に落ちて
Finish Ack（0x03）が 0x02 に付け替えられたり、Settings に particleStatus が
付かなかったりして、768サーバーとの設定フェーズが壊れる。
`handleClientConfiguration` では `is767() && isServer768()` を `is767()` より
先に見る（プレイ状態の `handleClientPlay` はもともと `isServer768()` が先）。

### なぜ「並べ替え」が必要か

767／768 の設定フェーズの Registry Data は、レジストリごとの
エントリ配列形式です（1個の NBT codec ではありません）。各エントリは
「名前＋NBT」で、番号は配列の位置そのものです。ダメージ種別の並びが
767（47件）と768（49件、ender_pearl と mace_smash を挿入）で違うため、
配列をそのまま中継すると番号がずれ、ender_pearl が別のダメージ種別に
なってしまいます。プロキシは Registry Data を受け取ったら、エントリを
名前で並べ替え、送る側の正しい順番に直してから送ります。送る側に無いエントリ
（768の新ダメージ種別など）は、767向けには**落とす**のではなく、名前だけの
エントリ（データなし）として送り、番号をずらさないようにします。
逆向き（768→767）では、767に無いエントリは落とします。
`PlayTranslator.translateRegistryData767To768`／`768To767`／
`765To768`／`768To765` を参照してください。

## 接続の流れ

### 1.21.2／1.21.3クライアント（768、offline-mode）

内部プロトコルで流れが分かれます。

**内部768（1.21.2／1.21.3サーバー）のとき：素通し（リレー）。**
ハンドシェイクの直後にリレーへ切り替わり、設定フェーズ・プレイは
生バイトのまま通します。設定フェーズの Select Known Packs（0x0E）も
そのままクライアントへ届くため、クライアントは空の一覧で答える必要が
あります（本物の1.21.2クライアントは答えます。偽クライアントのテスト
`Fake768Client` も答えます）。

**内部767（1.21／1.21.1サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**

1. ハンドシェイクのプロトコルVarIntを768→767に書き換えてバニラへ送る。
2. Login Startは767と同形式（名前＋UUID、bool なし）のため変換しない。
   Login Successは767の末尾の `strictErrorHandling` bool を落として768形式にする。
3. ログイン成功後、クライアントが Login Acknowledged（768は 0x03）を送ると
   両方とも設定フェーズに入る。Client Information（768は 0x00）は末尾の
   particleStatus を落として767形式にしてからバニラへ送る。
   バニラが送る Registry Data（767は 0x07、エントリ配列）は768の順序に
   並べ替えて中継する（上の「なぜ並べ替えが必要か」を参照）。Custom Report
   Details（0x0F）・Server Links（0x10）は767と768で番号が同じため中継する。
   Tags はレジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x03→768も 0x03）を中継し、クライアントの
   応答（0x03）がバニラに届いた時点で両方ともプレイ状態に入る。
5. バニラが送るJoin Game（767は 0x2B）は768の 0x2C へ変換する
   （seaLevel 63 を付ける）。スロットを含むパケット（Set Slot／
   Window Items／Entity Equipment など）は767→768へ変換する
   （`ChunkTranslator` の768変換を参照）。
6. スポーンは768式。Spawn Position（768は 0x5B）が合図で、767の
   Game Event 13は768クライアントに送らない。

**内部766（1.20.5／1.20.6サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
767のときと同じ流れで、Registry Data は766→767→768の合成表で並べ替える。
jukebox_song は766に無いため、768クライアントは受け取らない
（766サーバーは送らない）。

**内部765（1.20.4サーバー）・内部764（1.20.2サーバー）のとき：
設定フェーズを中継し、Registry Data の形式と順序を変換する。**

1. ハンドシェイクのプロトコルVarIntを768→765／764に書き換えてバニラへ送る。
2. Login Startは765・764と同形式のため変換しない。Login Successは
   768に strictErrorHandling が無いため、そのまま765／764形式で通す。
3. ログイン成功後、クライアントが Login Acknowledged（768は 0x03）を送ると
   両方とも設定フェーズに入る。Client Information は particleStatus を
   落としてからバニラへ送る。バニラが送る Registry Data（764／765は
   0x05、1個の名前なし NBT codec）は、768のエントリ配列形式（0x07）に
   変換し、さらに768の順序に並べ替えて中継する。逆向き（内部768のときの
   764／765クライアント）も同じくエントリ配列→codec に変換する。Tags は
   レジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x02→768は 0x03）を中継し、クライアントの
   応答（768は 0x03→バニラ側 0x02）がバニラに届いた時点で両方とも
   プレイ状態に入る。
5. バニラが送るJoin Game（764／765は 0x29）は768の 0x2C へ変換する。
   dimension は番号から文字列に戻す（764／765向けはその逆）。
6. スポーンは768式。Spawn Position（768は 0x5B）が合図で、765の
   Game Event 13は768クライアントに送らない。

**内部763（1.20／1.20.1サーバー）のとき：設定フェーズを合成する。**
内部763には設定フェーズが無いため、プロキシが Join Game の codec から
Registry Data（768形式 0x07、レジストリごとのエントリ配列）と
Finish Configuration（768は 0x03）を合成してクライアントへ送る。
クライアントの Finish 応答（768は 0x03）が来たら、預かっていた Join Game
（768は 0x2C）を送ってプレイ状態に入る。合成の間、サーバーから届いた
プレイ状態のパケットは溜めておき、Finish 応答の後にまとめて送る。
この仕組みは764／765／766／767クライアントの内部763向け合成と同じ経路で、
768だけ Registry Data の形式が違う（`ProxySession.synthesizingConfig`、
`PlayTranslator.buildRegistryData766` を参照。768の Registry Data も
767と同じエントリ配列形式のため、同じビルダーを使う）。

### 1.21.2／1.21.3クライアント（768、online-mode）

**参加できます。** 763／764／765／766／767と同じく、プロキシがクライアントとの間で
Mojang認証を自分で完結させます（Velocity／BungeeCordの信頼プロキシと
同じ考え方）。手順は763と同一です（`docs/PROXY-1.20.1.md` の
「1.20.1クライアント（763、online-mode）」を参照）。

- プロキシはハンドシェイクを内部バニラへすぐには送らず、預かります。
- Login Startを受けると、プロキシがRSA鍵ペアとverify tokenを作り、
  Encryption Requestをクライアントへ送ります。
- クライアントのEncryption Responseを復号し、verify tokenを照合します。
- shared secretでクライアントとのAES/CFB8暗号化を有効化します。
- serverIdハッシュ（SHA-1）を作り、Mojangセッションサーバーへ
  hasJoinedを照会します（既定：
  `https://sessionserver.mojang.com/session/minecraft/hasJoined`）。
  照会はNettyのイベントループとは別スレッドで行います。
- 認証成功時のみ、預かったハンドシェイクと、認証済みUUID・名前の
  Login Startを内部バニラへ送ります。認証失敗時は理由付きLogin
  Disconnectを返します。
- 内部バニラは **offline-mode（信頼済み接続）** で動きます。
  バニラが送るLogin Successにはプロパティが無いため、プロキシが
  hasJoinedで得たプロパティ（主にtextures）をLogin Successに入れて
  クライアントへ渡します（768形式への strict bool の付け直しは不要。
  768には strictErrorHandling が無い）。

信頼境界と防御、スキンの扱い（他プレイヤーから見たスキンが既定スキンに
なる場合がある制限を含む）は763と同じです（`docs/PROXY-1.20.1.md` の
「信頼境界と防御」「スキンの扱い」を参照）。

#### 設定

`detergentmc.yml` の `server.online-mode: true`（既定）が、プロキシ認証の
オン／オフです。`server.session-server-url` は照会先で、空なら既定の
Mojangセッションサーバーを使います。テストでローカルの偽サーバーを
指定するとき以外は変えないでください。

### ステータス（サーバーリストのping）

768クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 内部→768、name 内部の表示→
"1.21.2"）。descriptionやplayersは書き換えません。

## プレイ中の翻訳

- **ID付け替えのみ：** Keep Alive（プレイは768の 0x27⇔0x1A）、移動
  （Set Player Position は768の 0x1C）、ほとんどのプレイ中パケット。ID表は
  `PacketIds.java`（768列）を参照。767→768で既存パケットのIDは145件変わるため、
  汎用の `PacketTranslator.translateId` で番号を付け替える。
- **ペイロード変換あり：** チャット（768の Chat Message 0x07⇔内部 0x06。
  同形式のため番号だけ付け替える）、Chat Command（768は符号なし 0x05
  と符号付き 0x06 に分かれている。符号なしは文字列だけのため、完全な形式に
  合成して内部の 0x04 へ送る。符号付きはそのまま 0x04 へ送る）、
  Disconnect（768は NBT。763／764向けは JSON 文字列に変換する）、
  Respawn（dimension 番号のため変換する。768は 0x4C）、
  Start Configuration（768は 0x70。763には無いため、内部763のときは
  プロキシがサーバー側で肩代わりする。764／765／766／767には中継する）、
  Synchronize Player Position（768は 0x42。teleportId の位置・dx／dy／dz・
  flags の幅が変わる）、Set Cooldown（768はグループ名の文字列）、
  Player Input（768は 0x28。767の Steer Vehicle とは別形式で、方向は
  1バイトのビットフラグ。sprint は767に無いため落とす）、
  Set Slot State（番号だけ付け替える。ペイロードはそのまま）。
- **スロット（データコンポーネント）：** 768は767とデータコンポーネントの
  種別番号が大きく変わるため、767⇔768のスロットは種別ごとに変換する
  （`ChunkTranslator` の768変換）。Set Slot／Window Items／Set Cooldown／
  Entity Equipment／Trade List／Open Window／Block Action／Spawn Entity／
  Sound Effect／Entity Sound Effect／Stop Sound／World Particles／
  Entity Effect／Remove Entity Effect／Damage Event（768にしかないダメージ種別は
  落とす）／Entity Metadata／Update Attributes／Window Click／
  Set Creative Slot を変換する。
  変換のやり方は次の通り。
  - アイテムID・エンチャントID・属性ID・成分種別IDの番号ずれを対応表で直す。
  - `attribute_modifiers` は767と768で同じ形式（UUID は文字列）。
    属性IDだけ対応表で直す（`tempt_range` は767向けに落とす）。
  - `food` は768で食べる時間・食べた後のアイテム・効果が無くなったため、
    767→768ではそれらを落とす。768→767では既定値（食べる時間 1.6秒・
    食べた後のアイテム無し・効果無し）を入れる。
  - `custom_model_data` は767の単一の数値を768の colors[0] に入れる。
    逆向きは colors[0]（無ければ floats[0]）を数値に戻す。
  - `jukebox_playable` は767と768で同じ番号のためそのまま通す。
  - `trim`・`banner_patterns`・`instrument` は767と768で番号が同じため
    そのまま通す。
  - 768にしかない成分11種（`item_model`・`consumable`・`use_remainder`・
    `use_cooldown`・`damage_resistant`・`enchantable`・`equippable`・
    `repairable`・`glider`・`tooltip_style`・`death_protection`）は
    767向けに落とす。767の `fire_resistant` は768向けに落とす。
  - それ以外の成分は中身を読み飛ばしてそのまま写す（不透明コピー）。
  - 763／764／765向けは NBT 形式のため、Window Click／Set Creative Slot は
    **変換できず落とす**（767と同じ制限）。落としたクリックはサーバーに
    届かないため、768クライアントが内部763／764／765のサーバーにいるときは
    インベントリ操作ができない制限がある（ネイティブの768⇔768・767⇔768では
    リレーのため問題ない）。
- **Update Attributes（767⇔768・766⇔768・765⇔768）**：属性キーが番号のため、
  対応表で変換する。765向けは名前へ直す（`generic.burning_time` は
  768の `burning_time` へ）。768にしかない属性（`tempt_range`）は落とす。
  765の属性のキーはワイヤ上で `minecraft:generic.max_health` のような
  接頭辞付きである（本物のバニラ1.20.4から受け取って確認済み、
  2026-11-10。`AttributeKeyCaptureTest` が毎回確認する）。接頭辞なしで
  比較すると本物のサーバーからの属性が全部落とされるため、変換では
  接頭辞の有無の両方を受け付ける。
- **チャンク・ブロック状態IDリマップ：** 767↔768はブロック状態が634件増え、
  約26,662件の番号がずれるため、**チャンクの変換が必要**（766↔767は変換不要
  だった）。バイオームは既定のサーバーでは767と同じ64件・同じ順番のため、
  バイオームパレットは素通し（`ChunkTranslator` の768変換。winter_drop 有効時だけ
  範囲外のIDを plains へ落とす）。763／764／765／766向けは合成表（763↔768・764↔768・
  765↔768・766↔768）を使う（`RegistryRemapper` の768用対応表）。
  768→767の差分は大きい：ブロック状態は634件の追加、アイテムは42件の追加、
  エンティティはボートの分割（22件の追加・2件の削除）、音は27件の追加と2件の削除、
  パーティクルは2件の追加、属性は1件の追加と9件の改名、データコンポーネント種別は
  11件の追加と1件の削除。くわしくは上の「767→768の差分（要点）」を参照。
  検証スクリプトは `detergentmc-protocol/tools/verify-768-tables.py`
  （9群・171件のチェック）と `verify-reverse-tables.py`。
- **Set Cursor Item／Set Player Inventory（768にしかないパケット）：**
  768の Set Cursor Item（0x5A、スロット1個）は767の Set Slot（window −1）に、
  Set Player Inventory（0x66、スロットID＋スロット）は767の Set Slot
  （window 0）に直して送る。逆向き（767クライアント→768サーバー）は
  Set Slot の window −1／0 をそのまま中継する（768は Set Slot もあるため
  変換は不要）。
- **リソースパック：** 768クライアントには pack_format **42** の
  `detergentmc-pack-1.21.2.zip`（1.21.2と1.21.3で共用）を出す。763用
  パック（15）・764用パック（18）・765用パック（22）・766用パック（32）・
  767用パック（34）とは別物。外部配信時は `resource-pack.url-768` / `sha1-768` で768用パックを
  指定する（任意・未指定時は765用パックを出す。763／764／766／767用と同一方針）。
  loopback時は6つのパックを同じ25567番ポートで配信する。
  設定フェーズの Add Resource Pack（768は 0x09、UUID付き）とプレイ状態の
  Add Resource Pack は、内部763／764向けは UUID を外して送る。
- **データパック：** 1.21.2／1.21.3には pack_format **57** を出す
  （Minecraft Wiki の data pack 形式表：57 は 1.21.2〜1.21.3、48 は
  1.21〜1.21.1）。

## 送らない・落とすパケット（768向け）

768に相当するものが無い、または変換が未検証のパケットは落とします。

- Window Click／Set Creative Slot（763／764／765向け。データコンポーネント
  と NBT 形式のあいだの変換は未実装。766／767⇔768では変換できる）
- Declare Recipes／Statistics／Tags／World Event ID（763／764／765／766／767向けと
  同じく未対応。レシピ帳・タグが空になるだけで遊べる）
- Craft Recipe Response／Craft Recipe Request／Displayed Recipe
  （767⇔768のあいだ。レシピIDの形式が変わり変換できない。手動クラフトは動く）
- Recipe Book Add／Remove／Settings、Sync Entity Position、Move Minecart、
  Player Rotation（768にしかないため、古いクライアントには送らない）
- Select Bundle Item／Tick End（768にしかないサーバー行きパケット。
  古いサーバーには送らない）
- ブロックエンティティの crafter／trial_spawner（パケットごと落とす。
  766→765方向では vault も落とす。768も同じ。creaking_heart は767向けに落とす）
- Entity Metadata の variant 系（763向けはエントリごと落とす。
  764では素通し。766／767／768では絵画・オオカミの variant を番号で変換するが、
  768にしかないものは落とす）
- Explosion の768専用パーティクル／サウンド指定（既定演出に置き換える。
  766→763方向と同じ）
- Game Event 13（スポーン合図の765式。768はSpawn Positionを使う）
- Custom Report Details／Server Links（設定フェーズ 0x0F／0x10・プレイ
  0x81／0x82。768にはあるが、766以下のクライアントには送らない。
  767にはプレイの 0x7A／0x7B があるため番号を付け替えて送る）
- jukebox_song の Registry Data（768にしかないため、766以下のクライアントには送らない）
- Configuration Acknowledged／Chunk Batch Received（内部763には無いため落とす）

## 対象外・未実装（正直な一覧）

- **Window Click／Set Creative Slot の変換（768→763／764／765）**：767と同じく
  未実装。768クライアントが古いサーバーにいるときのインベントリ操作が
  できない（上の「プレイ中の翻訳」を参照）。766／767⇔768では変換できる。
- **ボートの木の種類**：767のボートは768では常にオークのボートに、
  768のボートは767では常に（木の種類を問わず）ボートになる。ボートの
  variant（木の種類）をエンティティ種別に直す変換は未実装。
  767のボート variant の並び（OAK〜BAMBOO、PALE_OAK=9）はバニラの
  `Boat$Type` の宣言順からの推測で、実機未確認。
- **バイオーム（winter_drop）**：既定の1.21.2／1.21.3サーバーはバイオームを
  767と同じ64件しか送らず、`pale_garden` は winter_drop データパックが
  無効のためレジストリに現れない。バイオームパレットは素通し。
  サーバーで winter_drop を有効にした場合、pale_garden のレジストリ内の
  位置は未確認で、範囲外のバイオームIDは plains に落とすだけの対処に
  なっている（見た目だけの制限）。
- **food の効果（767→768）**：金リンゴ等の食べたときの効果は768向けに
  落とす。767の food の効果は amplifier／duration を持たないため、
  768の `consumable` の apply_effects 形式へは変換できない。
- **custom_model_data（767→768）**：767の単一の数値を768の colors[0] に
  入れるだけのベストエフォート。floats／flags／strings は空になる。
- **Set Cooldown（768のグループ名）**：エンダーパール・コーラスフルーツ・
  ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす
  （クールダウンの表示だけの制限）。
- **Declare Recipes／Tags／Statistics／World Event ID**：未対応。
- **Entity Metadata のスロット・ブロック状態・粒子の中身**：764では素通しの
  ため未変換のまま残る（763向けは variant 系を落とす）。
- **768＋online-modeの他プレイヤーから見たスキン**：763と同じ制限。
  UUID・名前は認証済みのものが使われる。
- **実機クライアントでの確認**：未実施。生ソケットで768クライアントを装った
  統合テスト（下の「テスト」を参照）と、プロキシの単体テスト
  （`ProxySession768Test`）で検証しています。
- **内部768サーバーの統合テスト**：実施済み（下の「テスト」を参照）。
  本物のバニラ1.21.2／1.21.3サーバー自体の実起動（`Done` まで）も確認済み。
- **1.21.4以降（769〜）は未対応**：プロキシは768より新しいプロトコルを
  ログイン時に断る（「新しすぎます」）。

## Java 21（内部768サーバーを動かすとき）

バニラ1.21.2／1.21.3サーバーは Java 21 を要求します（Minecraft Wiki の
1.21.2 記事：Minimum Java version Java SE 21）。DetergentMC本体は
Java 17 で動きます。`VanillaServerHost` は内部バージョンが1.21.2／1.21.3の
とき、次の順番で Java 21 を探します。

1. `detergentmc.java21.home` システムプロパティ、
2. `DETERGENTMC_JAVA21_HOME` 環境変数、
3. 現在の `java.home` が Java 21 以上のときはそれ（サーバー全体を Java 21 で
   動かしている場合）、
4. どれも無いときは現在の Java を使うが、バニラが起動を拒否する（ログに
   Java 21 が必要だと警告を出す）。

この環境では `~/workspace/.tools/jdk-21.0.13+9/` を使います。ファットjarを
Java 17 で起動したまま、内部だけ Java 21 にする場合は、例えば
`DETERGENTMC_JAVA21_HOME=$HOME/workspace/.tools/jdk-21.0.13+9` を付けて
起動します。ファットjar自体を Java 21 で起動してもかまいません（その場合は
3. の経路で Java 21 が使われます）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.21.2.md` を参照してください。

## テスト

- `detergentmc-protocol`：`PacketIds768Test`（768のID表：145件の変更・
  新規11件・削除2件）、`ClientAdapter768Test`（768の受け入れ判定・
  全内部プロトコルとの振り分け）、`ClientJoinFlow768Test`（768の参加フロー）、
  `RegistryRemapper768Test`（767↔768対応表・合成表・往復）、
  `RegistryRemapper768TablesTest`（絵画・旗の模様・トリム・ダメージ種別の表）、
  `ChunkTranslator768Test`（767↔768変換・スロットの成分変換・チャンクの
  バイオーム（素通し・範囲外は plains へ）・Update Attributes・ボートの出現）、
  `PlayTranslator768Test`（Login Success／Join Game／Respawn／
  Synchronize Player Position／移動／Update Time／Player Info／
  Client Information の768別名・Registry Data の並べ替え・
  Player Input⇔Steer Vehicle）、
  `ProxySession768Test`（768のプロキシ参加フロー：内部763への設定フェーズ合成、
  内部764／765／766／767への設定フェーズ中継、内部768の素通し、763／764／765／766／
  767クライアントの内部768への参加、Registry Data の並べ替え、
  Client Information の particleStatus 変換）。
  検証スクリプト `detergentmc-protocol/tools/verify-768-tables.py`
  （9群・171件のチェック）と `verify-reverse-tables.py`。
- `detergentmc-core`：`ServerProtocolWiringTest`（内部768の配線・768用パックの
  受け渡し）、`VanillaServerHostTest`（Java 21 の選択）、
  `ProxyIntegrationTest.fake768ClientJoinsRealVanilla1212ThroughProxy`
  （偽768クライアントが本物のバニラ1.21.2に参加）、
  `ProxyIntegrationTest.fake768ClientJoinsRealVanilla1213ThroughProxy`
  （偽768クライアントが本物のバニラ1.21.3に参加）、
  `ProxyIntegrationTest.fake768ClientJoinsRealVanilla121ThroughProxy`
  （偽768クライアントが本物のバニラ1.21に参加）、
  `ProxyIntegrationTest.fake767ClientJoinsRealVanilla1212ThroughProxy`
  （偽767クライアントが本物のバニラ1.21.2に参加）、
  `ProxyIntegrationTest.fake768ClientJoinsRealVanilla1205ThroughProxy`
  （偽768クライアントが本物のバニラ1.20.5に参加）、
  `ProxyIntegrationTest.fake766ClientJoinsRealVanilla1212ThroughProxy`
  （偽766クライアントが本物のバニラ1.21.2に参加）、
  `ProxyIntegrationTest.fake768ClientJoinsRealVanilla1204ThroughProxy`
  （偽768クライアントが本物のバニラ1.20.4に参加）、
  `ProxyIntegrationTest.fake765ClientJoinsRealVanilla1212ThroughProxy`
  （偽765クライアントが本物のバニラ1.20.4に参加）、
  `ProxyIntegrationTest.fake768ClientJoinsRealVanilla1202ThroughProxy`
  （偽768クライアントが本物のバニラ1.20.2に参加）、
  `ProxyIntegrationTest.fake764ClientJoinsRealVanilla1212ThroughProxy`
  （偽764クライアントが本物のバニラ1.21.2に参加）、
  `ProxyIntegrationTest.fake763ClientJoinsRealVanilla1212ThroughProxy`
  （偽763クライアントが本物のバニラ1.21.2に参加）、
  `ProxyIntegrationTest.fake768ClientJoinsRealVanilla1201ThroughProxy`
  （偽768クライアントが本物のバニラ1.20.1に参加）。
  `RegistryOrderCaptureTest.registryOrderOfRealVanilla1212`／
  `registryOrderOfRealVanilla1213`（本物のバニラ1.21.2／1.21.3から
  Registry Data の順番を記録し、対応表の仮説を検証する。
  mace_smash=26／magic=27 を含む。バイオームは64件・plains=39で、
  pale_garden は既定では現れない）。
  既存の統合テスト（1.21対応の16件）も回帰テストとして残っています。
- `detergentmc-bedrock`：`JavaBackendConnection768Test`（Bedrock→内部768の
  参加・Select Known Packs／Ping・Respawn の次元番号・Update Attributes の
  768形式・スロットの768正規化・ボートの分割・クリイキングの代替）。
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_2`（Bedrock 6 プロトコル ×
  内部768の統合テスト）。
