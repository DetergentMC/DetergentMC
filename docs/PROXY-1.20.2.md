# 1.20.2クライアント → 1.20.4サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ1.20.4子プロセスの2層構成です。
サーバー内部は常にプロトコル765（1.20.4）で、変換はクライアントとの
境界面だけで行います。

763（1.20.1）との最大の違いは、**764には設定フェーズがある**ことです。
そのため763のようにプロキシが設定フェーズを消費することはなく、
設定フェーズは中継します。くわしい差分は下の各節を参照してください。
763の仕組みは `docs/PROXY-1.20.1.md` を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用の3つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 接続の流れ

### 1.20.2クライアント（764、offline-mode）

1. ハンドシェイクのプロトコルVarIntを764→765に書き換えてバニラへ送る。
2. Login Startは765と同形式（名前＋UUID、bool なし）のため変換しない。
3. ログイン成功後、クライアントが Login Acknowledged（0x03）を送ると
   両方とも設定フェーズに入る。プロキシは設定フェーズを消費しない。
   Registry Data は765と同じ1個の名前なし NBT codec のためそのまま
   通す（内容はプロキシも保持する）。Finish Configuration（0x02）を
   中継し、クライアントの応答（0x02）がバニラに届いた時点で両方とも
   プレイ状態に入る。
4. バニラが送るJoin Game（0x29）は764でも同じ0x29・同形式のため変換なし
   で通す。763のような codec の埋め込みは不要。
5. スポーンは764式。Spawn Position（0x52）が合図で、765のGame Event 13は
   764クライアントに送らない。

### 1.20.2クライアント（764、online-mode）

**参加できます。** 763と同じく、プロキシがクライアントとの間でMojang認証を
自分で完結させます（Velocity／BungeeCordの信頼プロキシと同じ考え方）。
手順は763と同一です（`docs/PROXY-1.20.1.md` の
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
  クライアントへ渡します。

信頼境界と防御、スキンの扱い（他プレイヤーから見たスキンが既定スキンに
なる場合がある制限を含む）は763と同じです（`docs/PROXY-1.20.1.md` の
「信頼境界と防御」「スキンの扱い」を参照）。

#### 設定

`detergentmc.yml` の `server.online-mode: true`（既定）が、プロキシ認証の
オン／オフです。`server.session-server-url` は照会先で、空なら既定の
Mojangセッションサーバーを使います。テストでローカルの偽サーバーを
指定するとき以外は変えないでください。

### ステータス（サーバーリストのping）

764クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 765→764、name "1.20.4"→"1.20.2"）。
descriptionやplayersは書き換えません。

## プレイ中の翻訳

- **ID付け替えのみ：** Keep Alive、移動（Position / Position and Rotation /
  Look / Flying など）、ほとんどのプレイ中パケット。
  ID表は `PacketIds.java`（764/765の対応表）を参照。
- **ペイロード変換あり：** チャット、System Chat / Player Chat /
  Disconnect（764は JSON 文字列のため、765の NBT を JSON 文字列へ変換する。
  1.20.3からの NBT 化の前で、764は765と同じ NBT ではない）/ Server Data /
  Action Bar / Playerlist Header / Title Text / Player Info Update
  （displayNameのみ）、リソースパック、Respawn（764と765で同形式のため
  変換なし）、Boss Bar / Teams / Tab Complete / Advancements
  （764には criteria が無いので、763向けの空 criteria 書き足しはしない。
  NBTの表示名・ツールチップ等をJSON文字列に変換）、Explosion
  （共通部分だけ通し、765だけの末尾は読み飛ばす）。
- **次元移動：** バニラは Respawn（プレイ状態）＋チャンク＋位置同期で
  次元を変える。Respawn は764と765で同形式のため変換なしで通す。
- **プレイ中の Start Configuration：** 764には設定フェーズがあるため、
  そのまま中継する（763のような肩代わりはしない）。バニラはクライアントの
  応答（Configuration Acknowledged、0x0B）を待ってから設定フェーズの
  パケットを送るので、プロキシはここでは状態を変えない。応答が来たときに
  両方とも設定フェーズに入る。
- **チャット（サーバー行き）：** 764と765で同形式のためそのまま通す。
- **チャット（クライアント行き）：** 765の署名フィールド等を落とし、
  764形式（NBTチャット文言→JSON文字列）に変換します。763向けと同じ変換です。
- **リソースパック：** 設定フェーズでバニラが送るAdd Resource Packは
  764の設定フェーズの Resource Pack Send（0x06、UUIDなし）に変換して
  送ります。プレイ状態の Add Resource Pack（765、UUID付き）は764の
  Resource Pack Send（0x42）に変換します。クライアントの応答
  （設定 0x05・プレイ 0x27、結果のみ）は765形式（UUID＋結果）に戻して
  バニラへ送ります。764クライアントにはpack_format 18の
  `detergentmc-pack-1.20.2.zip`、763クライアントにはpack_format 15の
  パック、765クライアントにはpack_format 22のパックを出し分けます
  （URLとSHA-1だけを置き換え、UUID・forced・プロンプトはバニラの
  パックのものを使う）。外部配信時は `resource-pack.url-764` /
  `sha1-764` で764用パックを指定します（任意・未指定時は765用パックを出す）。

## 送らない・落とすパケット（764向け）

764に相当するものが無い、または変換が未検証のパケットは落とします。

- Declare Recipes（shaped レシピのフィールド順が765と764で違うため、
  そのまま通すと壊れる）と Tags（レジストリ番号がずれるため）。
  どちらも無くても遊べる（レシピ帳・タグが空になるだけ）
- 設定フェーズの Tags（同じ理由）
- Remove Resource Pack（764に相当が無い）
- Game Event 13（スポーン合図の765式。764はSpawn Positionを使う）

なお、Respawn、Boss Bar、Teams、Advancements、Tab Complete、Explosion
は変換して通す（763向けと同じ変換。Respawn は764では変換なし）。
Start Configuration は中継する（上の「プレイ中の翻訳」を参照）。

なお、Window Items／Set Slot／Trade List／Entity Equipment など
アイテムIDをレジストリ番号で送るパケットは、チャンクと同様に
`ChunkTranslator` で765→764に変換します（下の「チャンク・ブロック状態ID
リマップとレジストリ番号変換」を参照）。

## チャンク・ブロック状態IDリマップとレジストリ番号変換

- **対象パケット：** Chunk Data、Block Change、Multi Block Change、Block Entity Data、Set Slot、Window Items、Set Cooldown、Entity Equipment、Trade List、Open Window、Block Action、Spawn Entity、Sound Effect、Entity Sound Effect、Stop Sound、World Particles、Entity Effect、Remove Entity Effect（765→764）。サーバー行きは Window Click、Set Creative Slot のスロットのみ変換する。765クライアントは素通しのまま。
- **ネイティブ764表を使う。** `RegistryRemapper` の764用対応表（16テーブル）と
  `ChunkTranslator` の `*765To764` メソッドを使う。763向け表と764向け表は
  ブロック状態17,104件・音245件が異なるため、763向け変換の流用では誤変換に
  なる（例：ブロック状態 8843 は763表で 8827、764表で 8843。音 638 は
  763表で 1264、764表で 1270）。担当4で配線を764用に差し替えた。
  アイテム・エンティティ・パーティクル・ブロック・メニューは763と764で
  登録が同一のため、そこの変換結果は763向けと同じになる。
- **チャンク：** パレットはシングル／間接／直接すべて変換する（直接パレットは BitStorage 形式で詰め直す）。高さマップは765と同じ名前なし NBT のため書き直さない（763だけが名前付き NBT）。光データはそのまま写す。変換できないブロック状態は最も近い代替ブロックへフォールバックし、警告ログを出す。
- **代替ブロック：** 763向けと同じ（crafter→crafting_table、trial_spawner→spawner。tuff 系→stone 系、chiseled_copper 系→copper_block、copper_door→iron_door、copper_trapdoor→iron_trapdoor、copper_grate→iron_bars、copper_bulb→glowstone。向き等のプロパティは可能な限り保持する）。
- **レジストリ変換：** アイテム（1312件）、エンティティ（126件）、音（1539件→764は1484件まで）、パーティクル（101件）、ブロック（1058件）、メニュー（25件）、エフェクト（764も765も0ベースのため変換しない）の対応付け。breeze→blaze、wind_charge→small_fireball、trial_key→tripwire_hook、breeze_spawn_egg→blaze_spawn_egg。音は名前で代替する（例：breeze.*→blaze.*、trial_spawner.*→stone.* 等）。ブロックエンティティの crafter／trial_spawner は NBT が種別固有のためパケットごと落とす。Entity Metadata は764では素通しする（variant 系エントリを落とす763向け変換は使わない。ただしスロット・ブロック状態・粒子の中身は未変換のまま残る既知の制限）。
- **対応表の出所：** ViaVersion Mappings（`mapping-1.20.2.json`）と minecraft-data を名前で突き合わせたもの。生成は `detergentmc-protocol/tools/generate-remap-tables.py --target 764`（または `both`）で再現できる。
- **未対応（今後の課題）：** Declare Recipes、Tags、Statistics、World Event ID、Entity Metadata のスロット・ブロック状態・粒子の中身。本物のゲームクライアントでの確認は未実施。

## 対象外・未実装（正直な一覧）

- **Declare Recipes／Tags：** 上の「送らない・落とすパケット」を参照。レシピ帳・タグが空になるだけで遊べる。
- **Statistics／World Event ID：** 763向けと同じく未対応。今後の課題。
- **ブロックエンティティの crafter／trial_spawner：** NBT が種別固有のためパケットごと落とす（代替は危険）。
- **Entity Metadata の中身：** 764では素通しのため、スロット・ブロック状態・粒子の中身は765の番号のまま送られる。variant 系エントリは落とさない（763向けとは違う）。
- **764＋online-modeの他プレイヤーから見たスキン：** 763と同じ制限。UUID・名前は認証済みのものが使われる。
- **実機クライアントでの確認：** 未実施。生ソケットで764クライアントを装った統合テスト（`ProxyIntegrationTest.fake764ClientJoinsRealVanillaThroughProxy`）と、プロキシの単体テスト（`ProxySession764Test`）で検証しています。
- **リソースパックの764向け変換：** 764クライアントにはpack_format 18の `detergentmc-pack-1.20.2.zip` を出す。763用パック（15）とは別物。
- **1.20.2サーバーへの1.20.4クライアント参加：** 逆向き対応として実装済み
  （`docs/PROXY-REVERSE.md` を参照）。内部プロトコルが764のとき、765クライアントは
  下向き変換（765→764）で参加できる。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.20.2.md` を参照してください。

## テスト

- `detergentmc-protocol`：`PacketIds764Test`（764のID表）、
  `ClientAdapter764Test`（764の受け入れ判定）、
  `ClientJoinFlow764Test`（764の参加フロー）、
  `RegistryRemapper764Test`（764用対応表）、
  `ChunkTranslator764Test`（765→764変換19種＋サーバー行き2種）、
  `PacketTranslator764Test`、`ExtraPlayTranslator764Test`、
  `ProxySession764Test`（764のプロキシ参加フロー：設定フェーズ中継、
  Join Game素通し、ネイティブ764リマップ表の配線、764用リソースパックの
  出し分け、online-mode認証フロー）、`ResourcePackBuilderTest`・
  `ResourcePackServerTest`・`ResourcePackModeTest`（764用パックの生成・
  配信・出し分け）。
- `detergentmc-core`：`ResourcePackWiringTest`・`ResourcePackExternalTest`
  （loopback・外部配信での764用パック）、
  `ProxyIntegrationTest.fake764ClientJoinsRealVanillaThroughProxy`
  （本物のバニラ子プロセス＋生ソケット764クライアント。参加・スポーン・
  チャット・移動を確認）。`fake763ClientJoinsRealVanillaThroughProxy`
  と `fake765ClientJoinsRealVanillaThroughProxy` の回帰テストもあり。
  逆向きの統合テスト
  （`fake765ClientJoinsRealVanilla1202ThroughProxy`：偽765クライアントが
  本物のバニラ1.20.2に参加）は `docs/PROXY-REVERSE.md` を参照。

## 1.20系の相互連携（2026-11-09）

764クライアントは1.20.4サーバー（内部765）だけでなく、1.20.1サーバー
（内部763）・1.20.5／1.20.6サーバー（内部766）・1.20.2サーバー（内部764）
にも参加できる。内部763への参加はプロキシが Join Game の codec から
設定フェーズを合成する（766→763と同一経路で統合テスト済み。764→763の参加
フロー全体の単体テストはなし）。内部766への参加は設定フェーズを中継し
Registry Data の形式だけ変換する（単体テストのみ）。内部764への参加は
素通し（リレー）。
全16通り（クライアント763／764／765／766 × サーバー763／764／765／766）の
対応マトリクスは `docs/VERSIONS.md` の「1.20系の相互連携：全16通りの対応
マトリクス」を参照。766クライアントの仕組みは `docs/PROXY-1.20.5.md` を参照。
