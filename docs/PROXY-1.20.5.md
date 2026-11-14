# 1.20.5／1.20.6クライアント → 1.20系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.20.5／1.20.6クライアント（プロトコル766）は、内部プロトコルが
763／764／765／766のどのサーバーにも参加できます。内部766のときは
素通し（リレー）、それ以外は下向き変換です。763（1.20.1）・764（1.20.2）
との違いは、**766ではスロットがデータコンポーネント形式になり、
設定フェーズの Registry Data がエントリ配列形式になった**ことです。
くわしい差分は下の各節を参照してください。763の仕組みは
`docs/PROXY-1.20.1.md`、764は `docs/PROXY-1.20.2.md`、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。

全16通り（クライアント763／764／765／766 × サーバー763／764／765／766）の
対応マトリクスは `docs/VERSIONS.md` の「1.20系の相互連携：全16通りの対応
マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用の4つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 接続の流れ

### 1.20.5／1.20.6クライアント（766、offline-mode）

内部プロトコルで流れが分かれます。

**内部766（1.20.5／1.20.6サーバー）のとき：素通し（リレー）。**
ハンドシェイクの直後にリレーへ切り替わり、設定フェーズ・プレイは
生バイトのまま通します。

**内部765（1.20.4サーバー）・内部764（1.20.2サーバー）のとき：
設定フェーズを中継し、Registry Data の形式だけ変換する。**

1. ハンドシェイクのプロトコルVarIntを766→765／764に書き換えてバニラへ送る。
2. Login Startは765・764と同形式（名前＋UUID、bool なし）のため変換しない。
   Login Successは766だけ末尾に `strictErrorHandling` の bool が付くため、
   プロキシが付け直す（766→765では落とし、765→766では足す）。
3. ログイン成功後、クライアントが Login Acknowledged（766は 0x03）を送ると
   両方とも設定フェーズに入る。バニラが送る Registry Data（764／765は
   0x05、1個の名前なし NBT codec）は766形式（0x07、レジストリごとの
   エントリ配列）に変換して中継する。逆向き（内部766のときの764／765
   クライアント）も同じくエントリ配列→codec に変換する。Tags は
   レジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x02→766は 0x03）を中継し、クライアントの
   応答（766は 0x03→バニラ側 0x02）がバニラに届いた時点で両方とも
   プレイ状態に入る。
5. バニラが送るJoin Game（764／765は 0x29）は766の 0x2B へ変換する。
   dimension は番号から文字列に戻す（764／765向けはその逆）。
6. スポーンは766式。Spawn Position（766は 0x56）が合図で、765の
   Game Event 13は766クライアントに送らない。

**内部763（1.20／1.20.1サーバー）のとき：設定フェーズを合成する。**
内部763には設定フェーズが無いため、プロキシが Join Game の codec から
Registry Data（766形式 0x07、レジストリごとのエントリ配列）と
Finish Configuration（766は 0x03）を合成してクライアントへ送る。
クライアントの Finish 応答（766は 0x03）が来たら、預かっていた Join Game
（766は 0x2B）を送ってプレイ状態に入る。合成の間、サーバーから届いた
プレイ状態のパケットは溜めておき、Finish 応答の後にまとめて送る。
この仕組みは764／765クライアントの内部763向け合成と同じ経路で、
766だけ Registry Data の形式が違う（`ProxySession.synthesizingConfig`、
`PlayTranslator.buildRegistryData766` を参照）。
なお、以前は `ClientProxy` のコンストラクタが内部763を拒否していたため、
この見出し事例（766クライアントが1.20サーバーに入る）は構築すらできな
かった。`ProxySession` 側は以前から内部763（設定フェーズの合成）を扱えて
いたので、コンストラクタの検証を763にも広げるだけで解消した
（`ClientProxyTest.internalProtocol763IsAccepted`、
`ProxyIntegrationTest.fake766ClientJoinsRealVanilla1201ThroughProxy`）。
内部763サーバーに渡すリソースパックも763用（pack_format 15）に直した
（`DetergentServer.hostResourcePack()`）。765クライアント用の差し替え
パックは内部763のときも渡す（内部763のバニラが出す15のパックは765クライ
アントには使えないため）。

### 1.20.5／1.20.6クライアント（766、online-mode）

**参加できます。** 763／764／765と同じく、プロキシがクライアントとの間で
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
  クライアントへ渡します（766形式への strict bool の付け直しも行う）。

信頼境界と防御、スキンの扱い（他プレイヤーから見たスキンが既定スキンに
なる場合がある制限を含む）は763と同じです（`docs/PROXY-1.20.1.md` の
「信頼境界と防御」「スキンの扱い」を参照）。

#### 設定

`detergentmc.yml` の `server.online-mode: true`（既定）が、プロキシ認証の
オン／オフです。`server.session-server-url` は照会先で、空なら既定の
Mojangセッションサーバーを使います。テストでローカルの偽サーバーを
指定するとき以外は変えないでください。

### ステータス（サーバーリストのping）

766クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 内部→766、name 内部の表示→
"1.20.5"）。descriptionやplayersは書き換えません。

## プレイ中の翻訳

- **ID付け替えのみ：** Keep Alive（プレイは766の 0x26⇔0x18）、移動
  （Set Player Position は766の 0x1a。`PacketIds` の766列で引く汎用経路）、
  ほとんどのプレイ中パケット。ID表は `PacketIds.java`（766列）を参照。
- **ペイロード変換あり：** チャット（766の Chat Message 0x06⇔内部 0x05。
  同形式のため番号だけ付け替える）、Chat Command（766は符号なし 0x04
  と符号付き 0x05 に分かれている。符号なしは文字列だけのため、完全な形式に
  合成して内部の 0x04 へ送る。符号付きはそのまま 0x04 へ送る）、
  Disconnect（766は NBT。763／764向けは JSON 文字列に変換する）、
  Respawn（dimension 番号のため変換する。766は 0x47）、
  Start Configuration（766は 0x69。763には無いため、内部763のときは
  プロキシがサーバー側で肩代わりする。764／765には中継する）。
- **スロット（データコンポーネント）：** 766のスロットは NBT ではなく
  データコンポーネント形式のため、Window Click（766は 0x0E）と
  Set Creative Slot（766は 0x32）は**変換できず落とす**。落としたクリックは
  サーバーに届かないため、766クライアントが内部763／764／765のサーバーに
  いるときはインベントリ操作ができない制限がある（ネイティブの766⇔766では
  リレーのため問題ない）。Set Slot／Window Items などサーバー→クライアント
  方向のスロットは、IDと個数だけ通し、NBT・コンポーネントは落とす
  （`ChunkTranslator` の766変換を参照）。
- **チャンク・ブロック状態IDリマップ：** 765↔766対応表（逆向きは反転）と、
  763↔766・764↔766の合成表を使う（`RegistryRemapper` の766用対応表、
  `ChunkTranslator` の766変換）。765→766の差分は小さい：ブロック状態は
  trial_spawner の ominous 系（765の 26638〜26643→766の 26644〜26649）
  と vault の追加が中心。アイテムは scute→turtle_scute、
  trial_key→ominous_trial_key の改名など。音は
  entity.generic.wind_burst→wind_charge.wind_burst など。パーティクルは
  ambient_entity_effect→entity_effect、gust_emitter→gust_emitter_large。
  検証スクリプトは `detergentmc-protocol/tools/verify-766-tables.py`
  （87チェック）と `verify-reverse-tables.py`。
- **リソースパック：** 766クライアントには pack_format **32** の
  `detergentmc-pack-1.20.5.zip`（1.20.5と1.20.6で共用）を出す。763用
  パック（15）・764用パック（18）・765用パック（22）とは別物。
  外部配信時は `resource-pack.url-766` / `sha1-766` で766用パックを指定する
  （任意・未指定時は765用パックを出す。763／764用と同一方針）。
  loopback時は4つのパックを同じ25567番ポートで配信する。
  設定フェーズの Add Resource Pack（766は 0x09、UUID付き）とプレイ状態の
  Add Resource Pack は、内部763／764向けは UUID を外して送る。

## 送らない・落とすパケット（766向け）

766に相当するものが無い、または変換が未検証のパケットは落とします。

- Window Click／Set Creative Slot（データコンポーネントのため変換できず。
  上の「プレイ中の翻訳」を参照）
- Declare Recipes／Statistics／Tags／World Event ID（763／764向けと同じく
  未対応。レシピ帳・タグが空になるだけで遊べる）
- ブロックエンティティの crafter／trial_spawner（パケットごと落とす。
  766→765方向では vault も落とす）
- Entity Metadata の variant 系（763向けはエントリごと落とす。
  764では素通し）
- Explosion の765専用パーティクル／サウンド指定（既定演出に置き換える。
  766→763方向）
- Game Event 13（スポーン合図の765式。766はSpawn Positionを使う）
- Cookie Response／Select Known Packs（サーバー側が送ってこないため、
  クライアントから来たら落とす）
- Configuration Acknowledged／Chunk Batch Received（内部763には無いため落とす）

## 対象外・未実装（正直な一覧）

- **Window Click／Set Creative Slot の変換：** 766のデータコンポーネント形式と
  763／764／765の NBT 形式のあいだの変換は未実装。766クライアントが古い
  サーバーにいるときのインベントリ操作ができない（上の「プレイ中の翻訳」を参照）。
- **Declare Recipes／Tags／Statistics／World Event ID：** 未対応。
- **Entity Metadata のスロット・ブロック状態・粒子の中身：** 764では素通しの
  ため未変換のまま残る（763向けは variant 系を落とす）。
- **766＋online-modeの他プレイヤーから見たスキン：** 763と同じ制限。
  UUID・名前は認証済みのものが使われる。
- **実機クライアントでの確認：** 未実施。生ソケットで766クライアントを装った
  統合テスト（下の「テスト」を参照）と、プロキシの単体テスト
  （`ProxySession766Test`）で検証しています。
- **内部766サーバーの統合テスト：** 未実施。内部766のバニラを統合テストで
  起動するには Java 21 が要る（下の「Java 21」を参照）。バニラ1.20.5／
  1.20.6サーバー自体の実起動（`Done` まで）は別途確認済み
  （`docs/VERSIONS.md` の「起動確認の記録」を参照）。

## Java 21（内部766サーバーを動かすとき）

バニラ1.20.5／1.20.6サーバーは Java 21 を要求します（Minecraft Wiki の
1.20.5 記事：Minimum Java version Java SE 21）。DetergentMC本体は
Java 17 で動きます。`VanillaServerHost` は内部バージョンが1.20.5／1.20.6の
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

`docs/PLAYTEST-1.20.5.md` を参照してください。

## テスト

- `detergentmc-protocol`：`PacketIds766Test`（766のID表）、
  `ClientAdapter766Test`（766の受け入れ判定・全内部プロトコルとの振り分け）、
  `ClientJoinFlow766Test`（766の参加フロー）、
  `RegistryRemapper766Test`（765↔766対応表・合成表・往復）、
  `ChunkTranslator766Test`（765↔766変換・スロットのコンポーネント扱い）、
  `ProxySession766Test`（766のプロキシ参加フロー：内部763への設定フェーズ合成、
  内部764／765への設定フェーズ中継、内部766の素通し、763クライアントの内部766への
  参加、online-mode認証フロー、Keep Alive／Pingの中継）。
  検証スクリプト `detergentmc-protocol/tools/verify-766-tables.py`
  （87チェック）と `verify-reverse-tables.py`。
- `detergentmc-core`：`ServerProtocolWiringTest`（内部766の配線・766用パックの
  受け渡し）、`VanillaServerHostTest`（Java 21 の選択）、
  `ProxyIntegrationTest.fake766ClientJoinsRealVanilla1201ThroughProxy`
  （偽766クライアントが本物のバニラ1.20.1に参加。設定フェーズの合成・
  Join Game・スポーン・チャット・移動を確認）、
  `ProxyIntegrationTest.fake766ClientJoinsRealVanilla1202ThroughProxy`
  （偽766クライアントが本物のバニラ1.20.2に参加）、
  `ProxyIntegrationTest.fake763ClientJoinsRealVanilla1202ThroughProxy`
  （偽763クライアントが本物のバニラ1.20.2に参加。763↔764のプレイ・
  ペイロード変換を含む）。既存の統合テスト4件（763・764・765が1.20.4サーバーに
  参加、765が1.20.2サーバーに参加）も回帰テストとして残っています。
