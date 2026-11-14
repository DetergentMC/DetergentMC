# 1.21／1.21.1クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.21／1.21.1クライアント（プロトコル767）は、内部プロトコルが
763／764／765／766／767のどのサーバーにも参加できます。内部767のときは
素通し（リレー）、それ以外は上向き・下向き変換です。766との違いは小さく、
**パケットIDは766と1つも変わらず、新規は設定フェーズとプレイに
Custom Report Details／Server Linksが増えただけ**です。レジストリの多くも
766と同じで、変わったのは主にエンチャントの並び・絵画・旗の模様・
トリム・ダメージ種別の追加と、属性・データコンポーネント種別の追加です。
くわしい差分は下の各節を参照してください。763の仕組みは
`docs/PROXY-1.20.1.md`、764は `docs/PROXY-1.20.2.md`、766は
`docs/PROXY-1.20.5.md`、逆向きの考え方は `docs/PROXY-REVERSE.md` を参照
してください。

全25通り（クライアント763／764／765／766／767 × サーバー763／764／765／
766／767）の対応マトリクスは `docs/VERSIONS.md` の「1.21対応：全25通りの
対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用・767用の5つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 766→767の差分（要点）

| 分野 | 766→767の差分 | 扱い |
| --- | --- | --- |
| パケットID | 既存は0件の変更。新規は設定フェーズ Custom Report Details（0x0F／0x08）・Server Links（0x10／0x09）とプレイ Custom Report Details（0x7A）・Server Links（0x7B）の6件だけ | 新規パケットは古いクライアントには送らない（落とす）。767同士は素通し |
| Login Success | 766も767も末尾に `strictErrorHandling` の bool が付く | 767⇔766は素通し。765以下向けは末尾の bool を1バイト落とす（766と同一） |
| Join Game／Respawn | 766と767で同じ形式（次元は番号） | 767⇔766は素通し。765以下向けは766と同じ変換 |
| ブロック状態 | 26,684件で同じ（新規ブロック・改名なし） | 素通し。チャンクは変換なし |
| アイテム | 1,330→1,333件。音楽ディスク3件の追加だけ（`music_disc_creator`・`music_disc_creator_music_box`・`music_disc_precipice`）。bowl は 848→799 に移動し、799〜847が1ずつずれる | 対応表で変換。767にしかない3件は766向けに石へ |
| エンティティ | 130件で同じ（新規・改名なし） | 素通し |
| 音 | 1,607→1,611件。削除2件（trial_spawner の charge_activate／ambient_charged）と追加6件（ominous_activate／ambient_ominous、音楽ディスク3曲、vault の reject_rewarded_player） | 対応表で変換。無い音は既定の石の音へ |
| パーティクル | 109件で同じ | 素通し |
| 属性 | 22→31件。9件の追加（burning_time、explosion_knockback_resistance、movement_efficiency、oxygen_bonus、water_movement_efficiency、mining_efficiency、sneaking_speed、submerged_mining_speed、sweeping_damage_ratio）。**追加分はアルファベット順に挿入されるため、既存の属性番号がずれる** | Update Attributes を番号で変換する（766⇔767・765⇔767とも対応） |
| エンチャント | 42件で同じ顔ぶれ。**766はコード順、767はアルファベット順のため番号が大きく変わる**（mending 766=40→767=22、sharpness 766=13→767=32） | Registry Data のエントリ配列を名前で並べ替える。スロットのエンチャント成分も番号で変換する |
| データコンポーネント | 56→57種。`jukebox_playable` が 42番に挿入され、42番以降が1ずつずれる | スロットの成分は種別ごとに変換する。`jukebox_playable` は766向けに落とす |
| 絵画 | 30→50件。20件の追加（backyard、baroque、bouquet、cavebird、changing、cotan、endboss、fern、finding、humble、lowmist、meditative、orb、owlemons、passage、pond、prairie_ride、sunflowers、tides、unpacked）。766側の30件には配置可能な26件に加え、コマンド限定の4件（earth・wind・water・fire、1.19から存在）を含む | Registry Data と Entity Metadata（painting_variant）を番号で変換する。767にしかない絵画は766向けに落とす |
| 旗の模様 | 41→43件。flow・guster の追加 | 同上。767にしかない2件は766向けに落とす |
| トリム | 16→18件。bolt・flow の追加 | 同上 |
| ダメージ種別 | 45→47件。campfire・wind_charge の追加 | 同上 |
| ジュークボックスの歌 | 766には無く、767で19件のレジストリが新設 | 767→766ではレジストリごと落とす |

検証スクリプトは `detergentmc-protocol/tools/verify-767-tables.py`
（9群のチェック）と `verify-reverse-tables.py`。本物のバニラ1.21／
1.20.5から Registry Data の順番をそのまま記録する統合テスト
（`RegistryOrderCaptureTest`）で、エンチャント・絵画・旗の模様・
トリム・ダメージ種別・ジュークボックスの歌の順番が仮説通りであることを
確かめている。

### なぜ「並べ替え」が必要か

766／767 の設定フェーズの Registry Data は、レジストリごとの
エントリ配列形式です（1個の NBT codec ではありません）。各エントリは
「名前＋NBT」で、番号は配列の位置そのものです。エンチャントの並びが
766（コード順）と767（アルファベット順）で違うため、配列をそのまま
中継すると番号がずれ、mending が別のエンチャントになってしまいます。
プロキシは Registry Data を受け取ったら、エントリを名前で並べ替え、
送る側の正しい順番に直してから送ります。送る側に無いエントリ
（767の新絵画など）は、766向けには**落とす**のではなく、名前だけの
エントリ（データなし）として送り、番号をずらさないようにします。
逆向き（767→766）では、766に無いエントリは落とします。
`PlayTranslator.translateRegistryData766To767`／`767To766`／
`765To767`／`767To765` を参照してください。

## 接続の流れ

### 1.21／1.21.1クライアント（767、offline-mode）

内部プロトコルで流れが分かれます。

**内部767（1.21／1.21.1サーバー）のとき：素通し（リレー）。**
ハンドシェイクの直後にリレーへ切り替わり、設定フェーズ・プレイは
生バイトのまま通します。設定フェーズの Select Known Packs（0x0E）も
そのままクライアントへ届くため、クライアントは空の一覧で答える必要が
あります（本物の1.21クライアントは答えます。偽クライアントのテスト
`Fake767Client` も答えます）。

**内部766（1.20.5／1.20.6サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**

1. ハンドシェイクのプロトコルVarIntを767→766に書き換えてバニラへ送る。
2. Login Startは766と同形式（名前＋UUID、bool なし）のため変換しない。
   Login Successは766も767も末尾に `strictErrorHandling` の bool が
   付くため、そのまま通す。
3. ログイン成功後、クライアントが Login Acknowledged（767は 0x03）を送ると
   両方とも設定フェーズに入る。バニラが送る Registry Data（766は 0x07、
   エントリ配列）は767の順序に並べ替えて中継する（上の「なぜ並べ替えが
   必要か」を参照）。jukebox_song は766に無いため、767クライアントは
   受け取らない（766サーバーは送らない）。Custom Report Details（0x0F）・
   Server Links（0x10）は766に無いため、767クライアントには送らない。
   Tags はレジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x03→767も 0x03）を中継し、クライアントの
   応答（0x03）がバニラに届いた時点で両方ともプレイ状態に入る。
5. バニラが送るJoin Game（766は 0x2B）は767の 0x2B へそのまま通す
   （形式が同じため変換なし）。スロットを含むパケット（Set Slot／
   Window Items／Entity Equipment など）は766→767へ変換する
   （`ChunkTranslator` の767変換を参照）。
6. スポーンは767式。Spawn Position（767は 0x56）が合図で、765の
   Game Event 13は767クライアントに送らない。

**内部765（1.20.4サーバー）・内部764（1.20.2サーバー）のとき：
設定フェーズを中継し、Registry Data の形式と順序を変換する。**

1. ハンドシェイクのプロトコルVarIntを767→765／764に書き換えてバニラへ送る。
2. Login Startは765・764と同形式のため変換しない。Login Successは
   767の末尾の `strictErrorHandling` bool を落として765／764形式にする。
3. ログイン成功後、クライアントが Login Acknowledged（767は 0x03）を送ると
   両方とも設定フェーズに入る。バニラが送る Registry Data（764／765は
   0x05、1個の名前なし NBT codec）は、767のエントリ配列形式（0x07）に
   変換し、さらに767の順序に並べ替えて中継する。逆向き（内部767のときの
   764／765クライアント）も同じくエントリ配列→codec に変換する。Tags は
   レジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x02→767は 0x03）を中継し、クライアントの
   応答（767は 0x03→バニラ側 0x02）がバニラに届いた時点で両方とも
   プレイ状態に入る。
5. バニラが送るJoin Game（764／765は 0x29）は767の 0x2B へ変換する。
   dimension は番号から文字列に戻す（764／765向けはその逆）。
6. スポーンは767式。Spawn Position（767は 0x56）が合図で、765の
   Game Event 13は767クライアントに送らない。

**内部763（1.20／1.20.1サーバー）のとき：設定フェーズを合成する。**
内部763には設定フェーズが無いため、プロキシが Join Game の codec から
Registry Data（767形式 0x07、レジストリごとのエントリ配列）と
Finish Configuration（767は 0x03）を合成してクライアントへ送る。
クライアントの Finish 応答（767は 0x03）が来たら、預かっていた Join Game
（767は 0x2B）を送ってプレイ状態に入る。合成の間、サーバーから届いた
プレイ状態のパケットは溜めておき、Finish 応答の後にまとめて送る。
この仕組みは764／765／766クライアントの内部763向け合成と同じ経路で、
767だけ Registry Data の形式が違う（`ProxySession.synthesizingConfig`、
`PlayTranslator.buildRegistryData766` を参照。767の Registry Data も
766と同じエントリ配列形式のため、同じビルダーを使う）。

### 1.21／1.21.1クライアント（767、online-mode）

**参加できます。** 763／764／765／766と同じく、プロキシがクライアントとの間で
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
  クライアントへ渡します（767形式への strict bool の付け直しも行う）。

信頼境界と防御、スキンの扱い（他プレイヤーから見たスキンが既定スキンに
なる場合がある制限を含む）は763と同じです（`docs/PROXY-1.20.1.md` の
「信頼境界と防御」「スキンの扱い」を参照）。

#### 設定

`detergentmc.yml` の `server.online-mode: true`（既定）が、プロキシ認証の
オン／オフです。`server.session-server-url` は照会先で、空なら既定の
Mojangセッションサーバーを使います。テストでローカルの偽サーバーを
指定するとき以外は変えないでください。

### ステータス（サーバーリストのping）

767クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 内部→767、name 内部の表示→
"1.21"）。descriptionやplayersは書き換えません。

## プレイ中の翻訳

- **ID付け替えのみ：** Keep Alive（プレイは767の 0x26⇔0x18）、移動
  （Set Player Position は767の 0x1a。`PacketIds` の767列で引く汎用経路）、
  ほとんどのプレイ中パケット。ID表は `PacketIds.java`（767列）を参照。
  766→767で既存パケットのIDは1つも変わらないため、766クライアントと
  767クライアントの番号付け替えは同じ表で済む。
- **ペイロード変換あり：** チャット（767の Chat Message 0x06⇔内部 0x05。
  同形式のため番号だけ付け替える）、Chat Command（767は符号なし 0x04
  と符号付き 0x05 に分かれている。符号なしは文字列だけのため、完全な形式に
  合成して内部の 0x04 へ送る。符号付きはそのまま 0x04 へ送る）、
  Disconnect（767は NBT。763／764向けは JSON 文字列に変換する）、
  Respawn（dimension 番号のため変換する。767は 0x47）、
  Start Configuration（767は 0x69。763には無いため、内部763のときは
  プロキシがサーバー側で肩代わりする。764／765／766には中継する）。
- **スロット（データコンポーネント）：** 766と同じデータコンポーネント形式の
  ため、766⇔767のスロットは変換できる（`ChunkTranslator` の767変換）。
  Set Slot／Window Items／Set Cooldown／Entity Equipment／Trade List／
  Open Window／Block Action／Spawn Entity／Sound Effect／Entity Sound Effect／
  Stop Sound／World Particles／Entity Effect／Remove Entity Effect／
  Damage Event（767にしかないダメージ種別は落とす）／Entity Metadata／
  Update Attributes／Window Click／Set Creative Slot を変換する。
  変換のやり方は次の通り。
  - アイテムID・エンチャントID・属性ID・成分種別IDの番号ずれを対応表で直す。
  - `attribute_modifiers` は767で modifier の UUID が無くなったため、
    766→767では16バイトを落とし、767→766では名前から決定的なUUIDを合成する。
  - `jukebox_playable`（767の 42番）は766に無いため、767→766では成分ごと
    落とす（スロットの added／removed の件数を減らす）。
  - `trim` の767にしかない模様（bolt・flow）・`banner_patterns` の
    767にしかない模様（flow・guster）・`instrument` の767にしかない音は、
    成分ごと（旗の模様はその層だけ）落とす。
  - それ以外の成分は中身を読み飛ばしてそのまま写す（不透明コピー）。
  - 763／764／765向けは NBT 形式のため、Window Click／Set Creative Slot は
    **変換できず落とす**（766と同じ制限）。落としたクリックはサーバーに
    届かないため、767クライアントが内部763／764／765のサーバーにいるときは
    インベントリ操作ができない制限がある（ネイティブの767⇔767・766⇔767では
    リレーのため問題ない）。
- **Update Attributes（766⇔767・765⇔767）**：属性キーが番号のため、
  対応表で変換する。765向けは名前へ直す（`horse.jump_strength` は
  767の `generic.jump_strength` へ）。767にしかない属性は落とす。
  765の属性のキーはワイヤ上で `minecraft:generic.max_health` のような
  接頭辞付きである（本物のバニラ1.20.4から受け取って確認済み、
  2026-11-10。`AttributeKeyCaptureTest` が毎回確認する）。接頭辞なしで
  比較すると本物のサーバーからの属性が全部落とされるため、変換では
  接頭辞の有無の両方を受け付ける。
  なお、**765⇔766の Update Attributes は未対応のまま**（765は名前、
  766は番号のため、従来は中継しなかった）。767の実装で 766⇔767 と
  765⇔767 は対応したが、765⇔766の組み合わせだけは未対応のため、
  765クライアントが766サーバーにいるときの体力・移動速度の更新は
  通らない（既存の制限）。
- **チャンク・ブロック状態IDリマップ：** 766↔767はブロック状態が完全に
  同じため、**チャンクの変換は不要**（素通し）。763／764／765向けは
  766と同じ対応表（763↔767・764↔767・765↔767の合成表）を使う
  （`RegistryRemapper` の767用対応表、`ChunkTranslator` の767変換）。
  766→767の差分は小さい：ブロック状態は0件、アイテムは音楽ディスク3件、
  エンティティは0件、音は2件の削除と6件の追加、パーティクルは0件、
  属性は9件の追加、データコンポーネント種別は1件の追加。くわしくは上の
  「766→767の差分（要点）」を参照。検証スクリプトは
  `detergentmc-protocol/tools/verify-767-tables.py`
  （9群のチェック）と `verify-reverse-tables.py`。
- **リソースパック：** 767クライアントには pack_format **34** の
  `detergentmc-pack-1.21.zip`（1.21と1.21.1で共用）を出す。763用
  パック（15）・764用パック（18）・765用パック（22）・766用パック（32）
  とは別物。外部配信時は `resource-pack.url-767` / `sha1-767` で767用パックを
  指定する（任意・未指定時は765用パックを出す。763／764／766用と同一方針）。
  loopback時は5つのパックを同じ25567番ポートで配信する。
  設定フェーズの Add Resource Pack（767は 0x09、UUID付き）とプレイ状態の
  Add Resource Pack は、内部763／764向けは UUID を外して送る。

## 送らない・落とすパケット（767向け）

767に相当するものが無い、または変換が未検証のパケットは落とします。

- Window Click／Set Creative Slot（763／764／765向け。データコンポーネント
  と NBT 形式のあいだの変換は未実装。766⇔767では変換できる）
- Declare Recipes／Statistics／Tags／World Event ID（763／764／765／766向けと
  同じく未対応。レシピ帳・タグが空になるだけで遊べる）
- ブロックエンティティの crafter／trial_spawner（パケットごと落とす。
  766→765方向では vault も落とす。767も同じ）
- Entity Metadata の variant 系（763向けはエントリごと落とす。
  764では素通し。766／767では絵画・オオカミの variant を番号で変換するが、
  767にしかない絵画は落とす）
- Explosion の765専用パーティクル／サウンド指定（既定演出に置き換える。
  766→763方向と同じ）
- Game Event 13（スポーン合図の765式。767はSpawn Positionを使う）
- Custom Report Details／Server Links（設定フェーズ 0x0F／0x10・プレイ
  0x7A／0x7B。767にしかないため、古いクライアントには送らない）
- jukebox_song の Registry Data（767にしかないため、766以下のクライアントには送らない）
- Configuration Acknowledged／Chunk Batch Received（内部763には無いため落とす）

## 対象外・未実装（正直な一覧）

- **Window Click／Set Creative Slot の変換（767→763／764／765）**：766と同じく
  未実装。767クライアントが古いサーバーにいるときのインベントリ操作が
  できない（上の「プレイ中の翻訳」を参照）。766⇔767では変換できる。
- **Update Attributes の 765⇔766**：未対応のまま（上の「プレイ中の翻訳」を参照）。
- **Declare Recipes／Tags／Statistics／World Event ID**：未対応。
- **Entity Metadata のスロット・ブロック状態・粒子の中身**：764では素通しの
  ため未変換のまま残る（763向けは variant 系を落とす）。
- **767＋online-modeの他プレイヤーから見たスキン**：763と同じ制限。
  UUID・名前は認証済みのものが使われる。
- **766サーバー→767クライアントの jukebox_song レジストリ**：766サーバーには
  jukebox_song レジストリが無いため、767クライアントは受け取らない。
  766サーバーは `jukebox_playable` を送らないため、遊ぶ上では問題ない。
- **実機クライアントでの確認**：未実施。生ソケットで767クライアントを装った
  統合テスト（下の「テスト」を参照）と、プロキシの単体テスト
  （`ProxySession767Test`）で検証しています。
- **内部767サーバーの統合テスト**：実施済み（下の「テスト」を参照）。
  本物のバニラ1.21／1.21.1サーバー自体の実起動（`Done` まで）も確認済み。
- **1.21.2以降（768〜）は未対応**：プロキシは767より新しいプロトコルを
  ログイン時に断る（「新しすぎます」）。

## Java 21（内部767サーバーを動かすとき）

バニラ1.21／1.21.1サーバーは Java 21 を要求します（Minecraft Wiki の
1.21 記事：Minimum Java version Java SE 21）。DetergentMC本体は
Java 17 で動きます。`VanillaServerHost` は内部バージョンが1.21／1.21.1の
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

`docs/PLAYTEST-1.21.md` を参照してください。

## テスト

- `detergentmc-protocol`：`PacketIds767Test`（767のID表）、
  `ClientAdapter767Test`（767の受け入れ判定・全内部プロトコルとの振り分け）、
  `ClientJoinFlow767Test`（767の参加フロー）、
  `RegistryRemapper767Test`（766↔767対応表・合成表・往復）、
  `RegistryRemapper767TablesTest`（絵画・旗の模様・トリム・ダメージ種別の表）、
  `ChunkTranslator767Test`（766↔767変換・スロットの成分変換・Update Attributes）、
  `PlayTranslator767Test`（Login Success／Join Game／Respawn の767別名・
  Registry Data の並べ替え）、
  `ProxySession767Test`（767のプロキシ参加フロー：内部763への設定フェーズ合成、
  内部764／765／766への設定フェーズ中継、内部767の素通し、763／764／765／766
  クライアントの内部767への参加、Registry Data の並べ替え）。
  検証スクリプト `detergentmc-protocol/tools/verify-767-tables.py`
  （9群のチェック）と `verify-reverse-tables.py`。
- `detergentmc-core`：`ServerProtocolWiringTest`（内部767の配線・767用パックの
  受け渡し）、`VanillaServerHostTest`（Java 21 の選択）、
  `ProxyIntegrationTest.fake767ClientJoinsRealVanilla121ThroughProxy`
  （偽767クライアントが本物のバニラ1.21に参加）、
  `ProxyIntegrationTest.fake767ClientJoinsRealVanilla1211ThroughProxy`
  （偽767クライアントが本物のバニラ1.21.1に参加）、
  `ProxyIntegrationTest.fake767ClientJoinsRealVanilla1205ThroughProxy`
  （偽767クライアントが本物のバニラ1.20.5に参加）、
  `ProxyIntegrationTest.fake766ClientJoinsRealVanilla121ThroughProxy`
  （偽766クライアントが本物のバニラ1.21に参加）、
  `ProxyIntegrationTest.fake767ClientJoinsRealVanilla1204ThroughProxy`
  （偽767クライアントが本物のバニラ1.20.4に参加）、
  `ProxyIntegrationTest.fake765ClientJoinsRealVanilla121ThroughProxy`
  （偽765クライアントが本物のバニラ1.21に参加）、
  `ProxyIntegrationTest.fake767ClientJoinsRealVanilla1202ThroughProxy`
  （偽767クライアントが本物のバニラ1.20.2に参加）、
  `ProxyIntegrationTest.fake764ClientJoinsRealVanilla121ThroughProxy`
  （偽764クライアントが本物のバニラ1.21に参加）、
  `ProxyIntegrationTest.fake763ClientJoinsRealVanilla121ThroughProxy`
  （偽763クライアントが本物のバニラ1.21に参加）。
  `RegistryOrderCaptureTest`（本物のバニラ1.21／1.20.5から Registry Data の
  順番を記録し、対応表の仮説を検証する）。
  既存の統合テスト（1.20系の7件）も回帰テストとして残っています。
- `detergentmc-bedrock`：`JavaBackendConnection767Test`（Bedrock→内部767の
  参加・Select Known Packs／Ping・Respawn の次元番号・Update Attributes の
  767形式・スロットの767正規化）。
