# 1.20.1クライアント → 1.20.4サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ1.20.4子プロセスの2層構成です。
サーバー内部は常にプロトコル765（1.20.4）で、変換はクライアントとの
境界面だけで行います。

1.20.2クライアント（764）のプロキシについては `docs/PROXY-1.20.2.md`
を参照してください。764には設定フェーズがあるため、763のようにプロキシが
設定フェーズを消費することはなく、中継します。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ） |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 接続の流れ

### 1.20.4クライアント（765）

offline-modeでは、ハンドシェイク後、純粋なTCPバイト中継（リレー）に
切り替わります。online-modeでは、プロキシが先にMojang認証を完結させ
（下の「1.20.1クライアント（763、online-mode）」と同じ仕組み）、
Login Successの後に暗号化パイプライン付きのリレーに切り替わります。
認証済みのクライアントだけが内部バニラへ届くので、online-modeの
1.20.4クライアントは従来通り（Mojangアカウント必須で）参加できます。

### 1.20.1クライアント（763、offline-mode）

1. ハンドシェイクのプロトコルVarIntを763→765に書き換えてバニラへ送る。
2. Login Startは763形式（UUIDの有無を示すbool付き）から765形式に変換する。
   UUIDが無い場合は offline UUID v3（`"OfflinePlayer:"+名前` のMD5）を合成する。
3. ログイン成功後、プロキシが765クライアント役として設定フェーズを消費する。
   Login AcknowledgedとClient Informationをバニラへ合成して送り、
   Registry Dataを受け取って保持し、Finish Configurationを受けたら
   Acknowledge Finish Configurationを返す。
4. バニラが送るJoin Game（0x29）を763向けJoin Game（0x28）に変換する。
   763形式にはdimension codec（NBT）が必要で、保持した
   `minecraft:dimension_type` と `minecraft:worldgen/biome` の
   レジストリから組み立てる（それ以外のレジストリはcodecに入れない）。
5. スポーンは763式。Spawn Position（0x50）が合図で、765のGame Event 13は
   763クライアントに送らない。

### 1.20.1クライアント（763、online-mode）

**参加できます。** プロキシがクライアントとの間でMojang認証を自分で
完結させます（Velocity／BungeeCordの信頼プロキシと同じ考え方）。

1. プロキシはハンドシェイクを内部バニラへすぐには送らず、預かります。
2. Login Startを受けると、プロキシがRSA鍵ペアとverify tokenを作り、
   Encryption Requestをクライアントへ送ります。
3. クライアントのEncryption Response（shared secret＋verify tokenを
   プロキシの公開鍵でRSA暗号化したもの）を復号し、verify tokenを照合します。
4. shared secretでクライアントとのAES/CFB8暗号化を有効化します。
5. serverIdハッシュ（SHA-1）を作り、Mojangセッションサーバーへ
   hasJoinedを照会します（既定：
   `https://sessionserver.mojang.com/session/minecraft/hasJoined`）。
   照会はNettyのイベントループとは別スレッドで行います。
6. 認証成功時のみ、預かったハンドシェイクと、認証済みUUID・名前の
   Login Startを内部バニラへ送ります。認証失敗時は理由付きLogin
   Disconnectを返します。
7. 内部バニラは **offline-mode（信頼済み接続）** で動きます。
   バニラが送るLogin Successにはプロパティが無いため、プロキシが
   hasJoinedで得たプロパティ（主にtextures）をLogin Successに入れて
   クライアントへ渡します。

以前の「暗号化のMITMは素のバニラ相手には原理的に成立しないため参加不可」
という結論は、バニラに認証を任せたまま翻訳しようとした場合の話です。
認証の主体をプロキシに移し、内部バニラを信頼済みのoffline-modeに
することで成立します。

#### 信頼境界と防御

- 内部バニラは `server-ip=127.0.0.1` に束縛され、公開ポートはプロキシ
  （25565）だけです。`server.properties` の `online-mode` は、
  公開側の設定が何であっても常に `false` で書き出されます。
- `ClientProxy` はバニラ側ホストにloopback（127.0.0.1／::1／localhost）
  以外を受け付けません。内部バニラに認証なしで直接届く接続は、
  認証を迂回できてしまうためです。
- ただし、同一マシン上の別プロセス・別ユーザーが127.0.0.1:25566へ
  直接つなぐことは、この仕組みだけでは防げません（バニラに
  Forwarding Secretのような仕組みはありません）。内部ポートを
  外部に公開しないこと、サーバーを動かすマシンを他人と共有しないことが
  前提です。

#### 設定

`detergentmc.yml` の `server.online-mode: true`（既定）が、プロキシ認証の
オン／オフです。`server.session-server-url` は照会先で、空なら既定の
Mojangセッションサーバーを使います。テストでローカルの偽サーバーを
指定するとき以外は変えないでください。

#### スキンの扱い（制限）

- 自分のスキンは、クライアント自身のMojangセッションから表示されるため、
  正しく表示されます（Login Successのプロパティも付けています）。
- 他のプレイヤーから見たスキンは、内部バニラ（offline-mode）が
  Player Info Updateにプロパティを載せないため、既定スキン
  （Steve／Alex）で表示される場合があります。プロキシがPlayer Info
  Updateへプロパティを注入するのは未実装です（今後の課題）。
  UUIDと名前は認証済みのものが使われるため、なりすましはできません。

### ステータス（サーバーリストのping）

763クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 765→763、name "1.20.4"→"1.20.1"）。
descriptionやplayersは書き換えません。

## プレイ中の翻訳

- **ID付け替えのみ：** Keep Alive、移動（Position / Position and Rotation /
  Look / Flying など）、ほとんどのプレイ中パケット。
  ID表は `PacketIds.java`（763/765の対応表）を参照。
- **ペイロード変換あり：** チャット、Join Game、System Chat / Player Chat /
  Disconnect / Server Data / Action Bar / Playerlist Header / Title Text /
  Player Info Update（displayNameのみ）、リソースパック、Respawn
  （copyMetadata の位置だけ違う）、Boss Bar / Teams / Tab Complete /
  Advancements（NBTの表示名・ツールチップ等をJSON文字列に変換）、
  Explosion（共通部分だけ通し、765だけの末尾は読み飛ばす）。
- **次元移動：** バニラは Respawn（プレイ状態）＋チャンク＋位置同期で
  次元を変える。設定フェーズは経由しないので、Respawn の変換だけで
  足りる。1.20.2の設定フェーズ追加で Respawn の dimension が VarInt
  に変わったわけではない（文字列のまま。copyMetadata の位置だけ移動）。
- **プレイ中の Start Configuration：** 763には設定フェーズが無いので、
  クライアントには何も送らず、プロキシがサーバー側の設定フェーズを
  肩代わりする（Registry Data を保持し直し、Finish Configuration で
  Acknowledge を返す）。その間クライアントから届いたプレイ状態の
  パケットは溜めておき、終わった後にまとめて送る。
- **チャット（サーバー行き）：** 実際の1.20.1クライアントは、1.20.4と
  同じ署名付きの完全な形式で送ってきます。その形式はそのまま通します。
  文字列だけの形式が来た場合のみ、765形式（メッセージ＋タイムスタンプ＋
  salt＋空の署名）を合成します。**署名は検証しません（offline想定）。**
- **チャット（クライアント行き）：** 765の署名フィールド等を落とし、
  763形式（NBTチャット文言→JSON文字列）に変換します。
- **リソースパック：** 設定フェーズでバニラが送るAdd Resource Packは
  保持し、Join Gameの後に763向けプレイのResource Pack Send（0x40）
  として送ります。クライアントの応答（0x24）は765形式（0x28＋UUID）
  に戻してバニラへ送ります。763クライアントにはpack_format 15の
  `detergentmc-pack-1.20.1.zip`、765クライアントにはpack_format 22の
  パックを出し分けます（URLとSHA-1だけを置き換え、UUID・forced・
  プロンプトはバニラのパックのものを使う）。外部配信時は
  `resource-pack.url-763` / `sha1-763` で763用パックを指定します
  （任意・未指定時は765用パックを出す）。

## 送らない・落とすパケット（763向け）

763に相当するものが無い、または変換が未検証のパケットは落とします。

- Chunk Batch Finished / Start、Ping Response、Reset Score（ただし
  objective指定ありのものは763の Scoreboard Score（削除）に変換する）、
  Set Ticking State、Step Tick
- 設定フェーズのPlugin Message、Feature Flags、Tags
- Game Event 13（スポーン合図の765式。763はSpawn Positionを使う）

なお、Respawn、Boss Bar、Teams、Advancements、Tab Complete、Explosion
は変換して通すようになった（担当3）。Explosion は765だけの末尾
（block_interaction_type・粒子・音）を落として共通部分だけ通すため、
サーバー指定の粒子と音は763クライアントの既定の演出になる。
Advancements は765に無い criteria を空配列で補う。
Start Configuration はクライアントに送らず、プロキシがサーバー側の
設定フェーズを肩代わりする（上の「プレイ中の翻訳」を参照）。

なお、Window Items／Set Slot／Trade List／Entity Equipment など
アイテムIDをレジストリ番号で送るパケットは、チャンクと同様に
`ChunkTranslator` で765→763に変換します（下の「チャンク・ブロック状態ID
リマップとレジストリ番号変換」を参照）。

## チャンク・ブロック状態IDリマップとレジストリ番号変換

- **対象パケット：** Chunk Data、Block Change、Multi Block Change、Block Entity Data、Set Slot、Window Items、Set Cooldown、Entity Equipment、Trade List、Open Window、Block Action、Spawn Entity、Entity Metadata、Sound Effect、Entity Sound Effect、Stop Sound、World Particles、Entity Effect、Remove Entity Effect（765→763）。サーバー行きは Window Click、Set Creative Slot のスロットのみ変換する。765クライアントは素通しのまま。
- **チャンク：** パレットはシングル／間接／直接すべて変換する（直接パレットは BitStorage 形式で詰め直す）。高さマップは名前なし NBT から名前付き NBT（ルート名は空文字）に変換する。光データはそのまま写す。変換できないブロック状態は最も近い代替ブロックへフォールバックし、警告ログを出す。
- **代替ブロック：** crafter→crafting_table、trial_spawner→spawner。tuff 系→stone 系（slab／stairs／wall は stone_slab／stone_stairs／stone_brick_wall、tuff_bricks→stone_bricks、polished_tuff→tuff、chiseled_tuff 系→chiseled_stone_bricks）、chiseled_copper 系→copper_block、copper_door→iron_door、copper_trapdoor→iron_trapdoor、copper_grate→iron_bars、copper_bulb→glowstone。向き等のプロパティは可能な限り保持する。
- **レジストリ変換：** アイテム（1312件）、エンティティ（126件）、音（1539件）、パーティクル（101件）、ブロック（1058件）、メニュー（25件）、エフェクト（＋1シフト）の全エントリを対応付けた。breeze→blaze、wind_charge→small_fireball、trial_key→tripwire_hook、breeze_spawn_egg→blaze_spawn_egg。音は名前で代替する（例：breeze.*→blaze.*、trial_spawner.*→stone.* 等）。ブロックエンティティの crafter／trial_spawner は NBT が種別固有のためパケットごと落とす。Entity Metadata の variant 系（猫・狼・カエル・絵画）はエントリごと落とす。
- **対応表の出所：** ViaVersion Mappings（`mapping-1.20.json`／`mapping-1.20.3.json`）と minecraft-data を名前で突き合わせたもの。生成は `detergentmc-protocol/tools/generate-remap-tables.py` で再現できる。
- **未対応（今後の課題）：** Declare Recipes、Statistics、Tags、World Event ID、エンティティメタデータの index（代替エンティティでは意味が違う可能性あり）。本物のゲームクライアントでの確認は未実施。

## 対象外・未実装（正直な一覧）

- **Declare Recipes／Statistics／Tags／World Event ID：** レジストリ番号の
  対応表が未作成のため未対応。今後の課題。
- **ブロックエンティティの crafter／trial_spawner：** NBT が種別固有のため
  パケットごと落とす（代替は危険）。
- **763＋online-modeの他プレイヤーから見たスキン：** 上の「スキンの扱い」を
  参照。UUID・名前は認証済みのものが使われる。
- **実機クライアントでの確認：** 未実施。生ソケットで763クライアントを
  装った統合テスト（`ProxyIntegrationTest`）と、偽バニラに対する
  プロキシ結合テスト（`ClientProxyTest`）で検証しています。
- **リソースパックの763向け変換：** 763クライアントにはpack_format 15の
  `detergentmc-pack-1.20.1.zip`、765クライアントにはpack_format 22の
  パックを出し分ける（URLとSHA-1だけを置き換える）。
- **1.20.1アダプターの他の方向：** 1.20.1サーバーへの1.20.4クライアント
  参加などは対象外です。

## 手動テスト手順（実機クライアント）

1. `run/detergentmc.yml` で `online-mode: true`（既定）のままにする。
   offline-modeで動かす場合は `false` にする（その場合は763も765も
   認証なしで参加できる）。
2. サーバーを起動する（`./gradlew run` 相当の手順。README参照）。
3. Minecraft: Java Edition 1.20.1のクライアントで `localhost:25565` に参加する。
4. スポーンし、移動・チャットができることを確認する。
5. `/trigger d_hello` が動くことを確認する。
6. online-modeがtrueのとき、正規のMojangアカウントで参加できることと、
   別人の名前でのなりすまし参加ができないことを確認する。
   offline-mode（`online-mode: false`）のときも参加できることを確認する。

## テスト

- `detergentmc-protocol`：`NbtTest`、`PlayTranslatorTest`、
  `ProxySessionTest`、`PacketIdsTest`、`ClientProxyTest`
  （偽バニラとの結合：参加フロー、ステータス書き換え、765リレー、
  未対応プロトコル切断）、`MojangAuthTest`（serverIdハッシュ・
  RSA／AES/CFB8の往復）、`SessionServerClientTest`（ローカルの
  偽セッションサーバーへのhasJoined照会）、`ProxySessionAuthTest`
  （763・765のプロキシ認証フロー、認証失敗・verify token不一致）、
  `ClientProxyAuthTest`（偽バニラ＋偽セッションサーバーとのNetty結合：
  763 online-mode参加・765 online-mode参加と暗号化リレー・認証失敗時の
  切断・loopback以外への接続拒否）。本物のMojang認証サーバーへの到達は
  サンドボックスでは遮断される場合があるため、認証の成否は偽サーバーで
  検証している。
- `detergentmc-core`：`VanillaServerHostTest`（loopback束縛・圧縮無効化・
  内部バニラは常にoffline-mode）、
  `VanillaLogParserTest`（`[Not Secure]` 付きチャット行の解析）、
  `ProxyIntegrationTest`（本物のバニラ子プロセス＋生ソケット763クライアント。
  バニラjarが無い環境ではスキップ。第3段階で765クライアントの回帰テスト
  `fake765ClientJoinsRealVanillaThroughProxy` を追加：プロキシ経由の
  参加・スポーン・チャット・移動が素通しで動くことを確認）

統合テストで見つかった実機由来の修正（2026-11-08）：

- Player Info Update の chat_session は option 型で先頭に bool がある。
  読み忘れると後続が1バイトずれて変換が例外で落ちる。
- バニラが参加時に送る Window Items、Entity Status、Unlock Recipes、
  Entity Metadata、Declare Recipes、Tags 等がID表に無く落とされていた。
  追加してID付け替えで通すようにした（アイテムID等のレジストリ番号の
  ずれは残る）。
- Profileless Chat、Death Combat Event、Scoreboard Objective／Score、
  Reset Score（→763の Scoreboard Score（削除））の変換を追加した。
- `[Not Secure] <Steve> ...` のチャット行を `VanillaLogParser` が
  拾えていなかった（offline-mode では全チャットが未署名でこの形式に
  なる）。`GameBridge` のチャット連携にも影響するため修正した。

## 1.20系の相互連携（2026-11-09）

763クライアントは1.20.4サーバー（内部765）だけでなく、1.20.2サーバー
（内部764）・1.20.5／1.20.6サーバー（内部766）・1.20.1サーバー（内部763）
にも参加できる。内部764への参加は763→764の直接変換で、プレイ・ペイロード変換
（`*764To763`／`*763To764`）も実装済み（統合テスト
`ProxyIntegrationTest.fake763ClientJoinsRealVanilla1202ThroughProxy`）。
内部766への参加はプロキシが766クライアント役として設定フェーズを消費する
（単体テストのみ）。内部763への参加は素通し（リレー）。
全16通り（クライアント763／764／765／766 × サーバー763／764／765／766）の
対応マトリクスは `docs/VERSIONS.md` の「1.20系の相互連携：全16通りの対応
マトリクス」を参照。766クライアントの仕組みは `docs/PROXY-1.20.5.md` を参照。
