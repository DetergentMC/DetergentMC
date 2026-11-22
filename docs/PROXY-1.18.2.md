# 1.18.2クライアント → 1.19系・1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.18.2クライアント（プロトコル758）は、内部プロトコルが
758／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775のどのサーバーにも参加できます。内部758のときは
素通し（リレー）、それ以外は758↔759の直接変換のあと、既存の
759↔760↔761↔762↔763↔764↔…↔775の変換を重ねます。**758は759の
ひとつ手前のバージョンで、パケットIDが161件中123件違う**ため、
762（763とIDが同一）のようなシムではなく、IDの付け替えを伴う
直接変換が要ります。レジストリ差分はブロック状態-1,106件、
アイテム-51件、音-118件、パーティクル-5件、ブロックエンティティ種別-2件、
バイオーム-2件（mangrove_swamp・deep_dark）、絵画-4件、
エンチャント-1件（swift_sneak）、エフェクト-1件（Darkness）です。
1.19の新要素（マングローブ・泥・スカルク・カエル・ウォーデン・
アレイなど）は、近い既存のものに代替します。

759の仕組みは `docs/PROXY-1.19.md`、762は `docs/PROXY-1.19.4.md`、
767は `docs/PROXY-1.21.md`、774は `docs/PROXY-1.21.11.md`、
逆向きの考え方は `docs/PROXY-REVERSE.md` を参照してください。

全324通り（クライアント758／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775 × サーバー758／759／760／761／762／763／764／765／
766／767／768／769／770／771／772／773／774／775）の対応マトリクスは `docs/VERSIONS.md` の「1.18.2対応：
全324通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。758用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の18つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 758↔759の差分（要点）

minecraft-data（`pc/1.18/protocol.json`・`pc/1.19/protocol.json`）と
ViaVersion（`mapping-1.18.json`・`mapping-1.19.json`）で確認。
検証スクリプトは `detergentmc-protocol/tools/gen-758-tables.py`・
`verify-758-tables.py`・`packet-ids-758.py`・`gen-758-remapper.py`。
レジストリの順序が本物のバニラ1.18.2と一致することは、jar の
data-generator ダンプ（reports/registries.json・reports/blocks.json）で
全カテゴリ確認済み。

| 分野 | 758↔759の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ） | **161件中123件が異なる**。272エントリのうち758に無いものは111件（設定フェーズの33件と759以降の追加）。758にだけある5件（spawn_entity_living 0x02、spawn_entity_painting 0x03、sculk_vibration_signal 0x05、chat 0x0f、chat_to_server 0x03）。Join Game 0x26、Chunk Data 0x22、Resource Pack 0x3c、Window Click 0x08、Set Creative Slot 0x28、Respawn 0x3d、Advancements 0x63、Disconnect 0x1a、Spawn Position 0x4b、Keep Alive 0x21、Ping 0x30 は758の番号（759とは違う） | 番号を付け替える（`PacketIds` の758列） |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一。Cookie は758・759とも無い（764で追加） | そのまま通す |
| 設定フェーズ | **758にも759にも無い**（764で追加） | プロキシが合成・消費する（762のときの節を参照） |
| Join Game | 758は16フィールド。759は dimension を文字列（worldType）で送り、末尾に `death`（死亡地点の有無＋位置）が増える。758は dimension を NBT の要素で送り、death は無い。codec は dimension_type 4件＋worldgen/biome 61件（758）／63件（759） | `PlayTranslator.translateJoinGame759To758`／`758To759`（`parseJoinGame758` で読む。759→758で death を落とし、worldType から codec 内の dimension_type 要素へ変換する。758→759で death は absent（false）を補う） |
| Respawn | 759は dimension を文字列（worldType）で送り、death（死亡地点の有無）がある。758は dimension を NBT 要素で送り、death は無い | `translateRespawn759To758`／`758To759`（registries の dimension_type から名前⇔要素を変換する。759→758で death を落とす。758→759で death は absent（false）を補う） |
| Login Start／Login Success | 758の Login Start は**名前だけ**（bool・UUID は無い）。759は名前＋bool＋任意UUID。Login Success は758も759も UUID＋名前＋プロパティ数だが、758クライアント向けは properties 配列を落とす | 758→759で false＋UUID を補う。759→758で bool・UUID を落とす（`translateLoginStart758To759`／`759To758`）。758→760／763／765への直通の変換も `PlayTranslator` にある |
| チャット | 758は署名なしの旧式チャット（クライアント行き 0x0f／サーバー行き 0x03、{message} だけ）。759は署名付き（Player Chat／System Chat／Chat Message／Chat Command）。758の 0x03 は「/」で始まればコマンド | 758→759：`translateChat758ToChatMessage759`（署名は空、timestamp・salt を補う）／`translateChat758ToChatCommand759`（「/」を落とす）。759→758：`translatePlayerChat759To758`／`translateSystemChat759To758`（旧式 chat 0x0f へ） |
| 出現パケット | 758では分かれている：Spawn Entity（0x00）・Spawn Entity Living（0x02）・Spawn Entity Painting（0x03）・Named Entity Spawn（0x04）。759では Spawn Entity（0x00）に統合され、プレイヤーは Named Entity Spawn（0x02）のまま | 759→758：プレイヤーは Named Entity Spawn（758：0x04）、生体は Spawn Entity Living（758：0x02）、それ以外は Spawn Entity（0x00）。758→759は逆向き（`ChunkTranslator.translateSpawnEntity759To758`／`758To759`）。Spawn Entity は758で headPitch 無し・objectData は i32（headPitch は yaw で代用）。falling_block は758=27／759=29、player は758=111／759=116 |
| 絵画 | 758は Spawn Entity Painting（0x03）でバリアントを直接送る。759は Spawn Entity＋Entity Metadata（index 8 の painting_variant）で送る。758の絵画は26件、759は30件（earth／fire／water／wind は759で追加。759の25〜28） | 759→758：Spawn Entity を**保留**し、Entity Metadata（index 8）のバリアント到着時に Spawn Entity Painting を送る（メタデータ位置は推定。`ProxySession` の保留表。Destroy Entities で掃除）。758に無いバリアント（759の25〜28）は落とす（DROP）。758→759は逆向き（バリアントを Entity Metadata に載せる） |
| Sculk Vibration Signal | 758にだけある専用パケット（0x05）。759で廃止され、振動は Game Event などで送られる | 759→758は Game Event などから合成しない（落とす）。758→759は落とす |
| Block Dig／Block Place／Use Item | 758には末尾の sequence が無い。759は末尾に sequence varint がある。サーバーからの Block Break Progress（ack）は758が旧式（block＋successful）、759が sequenceId だけ | 758→759：プロキシが sequence を振って付ける。掘削のものは Status→Location→Face の順で記録しておく。759→758：掘削の ack だけ旧式 ack に合成する（block=0、successful は完了した掘削だけ true）。掘削以外（Block Place／Use Item）の sequence には758側の ack が無いため落とす |
| Trade List | 758の inputItem2 は option slot（bool＋slot の2段）。759は slot | `ChunkTranslator.translateTradeList759To758`／`758To759`（AsOption／FromOption で bool を2個読む／書く） |
| Set Beacon Effect | 758は素の varint×2、759は option varint×2（-1 は「無し」） | `to759ClientPacket758` で付け外しする |
| Update Structure Block | seed だけ varlong（758）→varint（759）に直す | 同上 |
| Multi Block Change | レコードは758が varlong、759が varint | `ChunkTranslator.translateMultiBlockChange759To758`／`758To759` |
| Window Click | 758→759で専用の翻訳がある（`ChunkTranslator`） | 落とさない（変換する）。766クライアントが古いサーバーにいるときの「落とす」とは違う |
| Update Sign／Open Sign | isFrontText は758・759とも無い（763で追加） | そのまま通す |
| Player Info | 758には crypto フィールドが無い。759は Login Success のあとに crypto を持つエントリがある | 758→759で crypto を読まない。759→758で落とす（`translatePlayerInfo759To758`／`758To759`） |
| ブロック状態 | 759=21,448件、758=20,342件（-1,106）。759で葉に waterlogged が追加されたため、プロパティを1個だけ落とす／足す一致も使う | 対応表で変換。無いものは近い既存ブロックの既定状態に代替する（下の表を参照）。石への代替は範囲外だけ |
| ブロック | 759=933件、758=898件（-35） | 同上（ブロックIDも同じ代替） |
| アイテム | 759=1,152件、758=1,101件（-51） | 同上。music_disc_5／disc_fragment_5→music_disc_cat、echo_shard→amethyst_shard、goat_horn→music_disc_cat、recovery_compass→compass、tadpole_bucket→axolotl_bucket |
| エンティティ | 759=118件、758=113件（-5）。pig は759=67／758=64、player は759=116／758=111、falling_block は759=29／758=27 | 対応表で変換。アレイ→ヴェックス、カエル→ウサギ、オタマジャクシ→ウーパールーパー、ウォーデン→アイアンゴーレム、チェスト付きボート→同じ木のボート |
| 音 | 759=1,321件、758=1,203件（-118） | 対応表で変換。スカルク系→スカルクセンサー系、泥系→土・石レンガ系、フロッグライト→ガラス系、カエルの卵→スライムブロック系、アレイ→ヴェックス、カエル→ウサギ、オタマジャクシ→ウーパールーパー、ウォーデン→アイアンゴーレム、新しいバイオームの音楽→汎用・洞窟の音楽。範囲外は block.stone.break（758の番号） |
| パーティクル | 759=93件、758=88件（-5）。24番以降が5件ずれる。sculk_charge 系・shriek は759にしか無い | 対応表で変換。sculk_charge 系→soul、shriek→vibration、sonic_boom→explosion。範囲外は ambient（0） |
| エンチャント | 759=39件、758=38件。swift_sneak が759で追加 | 対応表は作らない（swift_sneak は758に無いため、範囲外は落とすか既定へ） |
| エフェクト | 759=33件、758=32件。Darkness が759で追加 | 対応表は作らない（Darkness は758に無い）。758・759とも1ベース（765の0ベースとは違う） |
| バイオーム | 759=63件、758=61件。mangrove_swamp・deep_dark が759で追加 | 対応表で変換。mangrove_swamp→swamp、deep_dark→dripstone_caves。範囲外は plains（758=1／759=1） |
| ブロックエンティティ種別 | 759=36件、758=34件。スカルク系ブロックエンティティが759で追加 | 対応表で変換。スカルク系ブロックエンティティ→スカルクセンサー。範囲外はパケットごと落とす（DROP） |
| メニュー | 24件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 絵画 | 759=30件、758=26件。earth／fire／water／wind は759で追加（759の25〜28） | 対応表で変換（`RegistryRemapper.mapPaintingVariant759To758`）。759の25〜28は DROP |
| ダメージ種別 | 42件の恒等写像。758・759とも Join Game の codec に存在しない（1.19.4で追加） | 対応表は恒等写像として残すが、ワイヤ上の呼び出し元は無い |
| Entity Metadata の型番号 | 758・759で同一（0=byte、1=int、…、24=painting_variant、25=sniffer_state は759にしか無い） | variant 系は落とさず通す（絵画のバリアントだけ保留方式で扱う） |
| 属性 | 13件で同じ（文字列キー。数値表は不要） | そのまま通す |
| 楽器 | 23件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 1.19の新ブロックの代替 | マングローブ系→オーク系、泥→土、泥レンガ→レンガ、スカルク→深層岩、フロッグライト→グロウストーン、カエルの卵→スイレンの葉 | 上のブロック状態・ブロックの表を参照 |

## 758↔759の連鎖の方針

758の変換は **758↔759の隣接リンク1本**だけを作り、あとは既存の鎖に
重ねる。758↔763の直通の翻訳器は作らない。

- クライアント758 → サーバー759／760／761／762／763／…／775：`ProxySession.handleClientPlay` の
  先頭で `to759ClientPacket758`（Chat／Block Dig／Block Place／Use Item／
  Set Beacon Effect／Update Structure Block だけ758→759へ直す。IDは
  `PacketIds` の758列→759列に付け替える）を通してから、以降は759クライアント
  扱いの分岐に任せる。758クライアントは 758→759→760→761→762→763 へ
  段階変換して送る。
- サーバー759／760／…／775 → クライアント758：各 From ハンドラは758を
  759扱い（`is759Like()`）で759形式のパケットを作り、最後に
  `handleServerPlay` のラッパー `convertServerPlayOutcomeTo758` が
  `translateServerPlayPacket759To758`（Join Game／Respawn／Player Info／
  Chunk Data／Block Change／Multi Block Change／Block Entity Data／
  Set Slot／Window Items／Set Cooldown／Entity Equipment／Trade List／
  Open Window／Block Action／Spawn Entity／Entity Metadata／Sound Effect／
  Entity Sound Effect／Named Sound Effect／Stop Sound／World Particles／
  Entity Effect／Player Chat／System Chat／Block Break Progress の ack 合成など）で759→758へ直す。
  それ以外はそのまま通す（IDだけ758列に付け替える）。
  サーバーからは 763→762→761→760→759→758 へ五段で変換して返す。
- 内部758サーバー → クライアント：`handleServerPlayRouted` の
  `isServer758()` 分岐で `to759ServerPacket`（上と逆向き）を通して
  759形式にしてから `handleServerPlayFrom759` に渡す。Join Game は
  `PlayTranslator.translateJoinGame758To759` で759形式にする。
- クライアント → 内部758サーバー：`handleClientPlay` の `isServer758()` 分岐で、
  まずクライアントのパケットを759形式に直し、次に `to758ClientPacket`
  で送る。Login Start は758（名前だけ）から内部の形式へ変換する。
- 758↔758はネイティブで素通し（リレー）。変換をかけてはいけない（かけると
  758形式のパケットが壊れる）。

`is758()`／`is759()`／`is759Like()`（クライアント側）、`isServer758()`／
`isServer759()`／`isServer759Like()`（サーバー側）は `ProxySession` の
private ヘルパー。758クライアントは `is759Like()` に含めた。

## 設定フェーズなしクライアントの扱い方

758は設定フェーズが無い。759と同じく、**プロキシが設定フェーズを合成・消費する**
方式で扱う。762のときの節（`docs/PROXY-1.19.4.md` の「設定フェーズなし
クライアントの扱い方」）をそのまま流用できる。758特有の注意だけここに書く。

- 758クライアント → 内部764〜775：ログイン成功のあと、クライアント側はすぐ
  プレイ状態へ進む。サーバー側は設定フェーズに入る。プロキシはサーバー側の
  クライアント役として Login Acknowledged（0x03）と Client Information（0x00）を送る。
  サーバーの設定フェーズのパケットは `handleServerConfiguration` で消費する。
  758クライアントは `is759Like()` の分岐に入る。
- 758クライアント → 内部759〜763：ログイン成功のあと、両方ともすぐプレイ状態へ進む。
  Login Acknowledged も Client Information も送ってはいけない。
  Join Game は758の 0x26 で送る（759は 0x23、763は 0x28）。
- 764〜775クライアント → 内部758サーバー：ログイン成功のあと、プロキシは設定フェーズを
  **合成**する。クライアントの Login Acknowledged はプロキシが消費し、サーバーへは送らない。
  サーバーが Join Game（758：0x26）を送ってきたら `to759ServerPacket` で759形式にしてから
  `handleSynthesizedJoinGame` が設定フェーズを合成してクライアントへ送る。
- Join Game codec の流用：758・759のレジストリは **Join Game の codec**（名前付き NBT）で
  送られる。758の codec は dimension_type 4件＋worldgen/biome 61件のみ。
  758クライアントに送る codec は、**サーバーの codec をそのまま写す**
  （763クライアントへの素通しと同じ扱い）。サーバーが775なら775の codec
  （trim の追加フィールド、damage_type の `effects`、cherry_grove 入りの
  バイオーム一覧など）が758クライアントに届く。Mojang の codec は普通は
  未知のフィールドを無視するが、**本物の758クライアントでの確認は未実施**である。

## 接続の流れ

### 758クライアント → 内部758サーバー

1. ハンドシェイク（758）→ ログイン（Login Start：名前だけ）。
2. Login Success（0x02）→ 両方ともすぐプレイ状態へ（Login Acknowledged なし）。
3. Join Game（758：0x26、16フィールド、codec は dimension_type 4件＋biome 61件）。
4. Spawn Position（0x4b）、Keep Alive（0x21→応答 0x0f）、Ping（0x30）。
5. チャット（0x03、{message} だけ）はそのまま通す。移動（0x11）もそのまま通す。

### 758クライアント → 内部759サーバー

1. ハンドシェイク（758）→ ログイン（Login Start：名前だけ。
   プロキシが false＋UUID を補って759形式にしてサーバーへ送る）。
2. Login Success（0x02）→ 両方ともすぐプレイ状態へ（Login Acknowledged なし）。
3. Join Game（759：0x23）→ 758（0x26）に翻訳して送る。
4. Spawn Position（0x4b）、Keep Alive（0x21→応答 0x0f）。
5. チャット（0x03、{message} だけ）は759の署名付き Chat Message（0x04、署名は空）に
   変換されてサーバーへ届く。「/」で始まれば Chat Command（0x03、「/」を落とす）。
   移動（0x11）はそのまま通す。

### 758クライアント → 内部774サーバー

1. ハンドシェイク（758）→ ログイン（Login Start：名前だけ。
   プロキシが 774形式（名前＋bool＋UUID）に直して774サーバーへ送る）。
2. Login Success（0x02）→ プロキシが Login Acknowledged（0x03）と
   Client Information（0x00）を774サーバーへ送り、設定フェーズに入る。
   クライアント側はプレイ状態のまま。
3. Registry Data（774：0x07）をプロキシが消費し、内容を保持する。
   Finish Configuration（774：0x03）でサーバー側はプレイ状態へ。
4. Join Game（774：0x30）→ 759形式を経て758（0x26）に翻訳する。
   設定フェーズ中に来た Add Resource Pack は Join Game のあとに 0x3c（758用）で送る。

### 759クライアント → 内部758サーバー

1. ハンドシェイク（759）→ ログイン（Login Start：名前＋bool＋UUID。
   内部758サーバーへは名前だけにして送る）。
2. Login Success（0x02）→ 両方ともすぐプレイ状態へ（Login Acknowledged なし）。
3. Join Game（758：0x26）→ 759形式（0x23）に直してから759クライアントへ送る。
4. 759の署名付きチャット（0x04）は758サーバー向けに旧式チャット {message}（0x03）に直す。

## プレイ中の翻訳

- チャンク：ブロック状態とバイオームを対応表で変換する。758→759では
  マングローブ系をオーク系に、泥を土に、スカルクを深層岩に写す。
  759→758では水没した葉の waterlogged を落とす。
  `trustEdges` は758・759ともに同じ位置にあるため付け外しは不要。
- ブロックエンティティ：スカルク系ブロックエンティティ（759）→スカルクセンサー（758）。
  範囲外だけ落とす（DROP）。
- スロット：NBT形式（758・759とも名前付き NBT）。アイテムIDだけ対応表で
  付け替える。music_disc_5／disc_fragment_5→music_disc_cat に注意。
- Window Click：758↔759で変換する（落とさない）。
- Entity Metadata：758・759で型番号が同一のため、variant 系も含めて通す。
  ただし絵画のバリアント（index 8）だけは保留方式で扱う。
- 音・パーティクル・エフェクト：対応表で変換する。音は名前での改名を先に見る。
  エフェクトは758・759とも1ベース（765の0ベースとは違う）。
- ダメージイベント：758・759とも codec にダメージ種別が無いため、Damage Event は
  変換しない（そのまま通す）。

## 送らない・落とすパケット（758向け）

- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応
  （既存の制限と同じ）。
- 759にだけあるパケット（Player Chat の署名部、Message Header など）は
  758クライアントには送れないため、旧式 chat（0x0f）に変換するか落とす。
- Sculk Vibration Signal（758にだけある）：759側からは合成しない。
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす。
- 759の絵画25〜28（earth／fire／water／wind）：758に無いため落とす（DROP）。
- 758に無いブロック状態・アイテム・音：近い既存のものに代替する（上の表を参照）。
  石・ブタ・plains への代替は範囲外だけ。
- Block Break Progress の ack：掘削以外の sequence は758側の ack が無いため落とす。

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全324通り共通。手順書は
  `docs/PLAYTEST-1.18.2.md`。偽クライアント＋本物のバニラでの統合テストは
  6セル（758→758、758→759、758→763、758→774、759→758、763→758）を
  `ProxyIntegrationTest` に追加し、TCP が許可された環境（親の環境、2026-11-15）
  で実行して参加・スポーン・チャット・移動まで確認済み。
- **絵画のメタデータ位置は推定**：759の Entity Metadata で絵画バリアントが
  index 8 に来ることを前提に Spawn Entity Painting を送る。実バニラ1.19の
  絵画のメタデータが index 8 以外の位置に来る場合、絵画が出ないか誤った
  バリアントになる。本物の758クライアントでの確認が必要。
- **Join Game codec の素通し**：775サーバーの codec（trim の追加フィールド、
  damage_type の `effects`、cherry_grove 入りのバイオーム一覧）が758クライアントに
  そのまま届く。Mojang の codec は未知のフィールドを無視するはずだが未確認。
  763クライアントへの素通しと同じ扱い。
- **named_entity_spawn（0x04）**：minecraft-data にはあるが、実バニラ1.19は
  プレイヤーの出現に送ってこない。実バニラ1.18.2が送るかは未確認。
  2人同時の統合テストはまだ無い。
- **Bedrock→758の絵画**：Spawn Entity Painting を送らないため、Bedrock側に
  絵画は表示されない（制限として残す）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  758＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **外部モードで pack758 が無い場合**：758クライアントには759パック
  （pack_format 9）が出る。本物の1.18.2クライアントが format 9 のパックを
  適用するかは未確認。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrockのインベントリは引き続き未対応**。

## 758のときに見つかった罠（後続の担当へ）

- **Trade List の inputItem2**：758の inputItem2 は option slot（bool＋slot の2段）。
  759は slot。AsOption／FromOption で bool を2個読む／書くこと。素の slot で読むと
  2個目の bool が次のフィールドに食い込む。
- **translateParticle758 の switch**：写像後IDではなく**元ID**で switch すること。
  ずれたID（falling_dust 等）や759にしか無い粒子（sculk_charge・shriek）で
  データの読み飛ばしが壊れる。代替先は ViaVersion 表通り。
- **Block Dig の読み順**：Status→Location→Face の順。Face を先に読むと位置がずれる。
- **to762ServerPacket の Named Entity Spawn**：Named Entity Spawn を Spawn Entity に
  変換する際、ID が Named Entity Spawn（0x03）のままだったのを Spawn Entity
  （762：0x01）に修正した。758＋759サーバーでプレイヤー出現が壊れていた。
  新しいバージョンの出現パケットを追加するときは、変換後の ID を確認すること。
- **リソースパック申し出の分岐漏れ**：`handleServerPlayFrom763` の旧式オファー分岐に
  758 を追加しないと、758クライアントにパックが出ない（0x40→758 の 0x3c へ変換される）。
  新しいバージョンのパックを追加するときは、この分岐も確認すること。
- **pack758 の一時的な代替**：Stage D の途中まで pack758 が無く、`packFor763LikeClient()` と
  `packForProtocol(758)` は759用パック（pack_format 9）で代替していた。パック追加の
  コミットで代替が外れたことを、統合テストで確認すること。
- **Login Success の properties 配列**：758クライアント向けは properties 配列を落とす。
  落とさないと758クライアントが Login Success を誤読する。
- **1.18.2サーバーの起動の遅さ**：スポーン準備に 278 秒かかった実績がある。
  統合テストの `waitForReady` は 1.18.2サーバーのセルだけ 300 秒にすること
  （762のときの 180 秒では足りないおそれがある）。

## Java 17（内部758サーバーを動かすとき）

1.18.2は **Java 17** で動く。Java 21 は不要。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.18.2は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.18.2` を指定すると、内部758サーバーが Java 17 で起動する。
バニラ1.18.2サーバーjarのSHA-1は `c8f83c5655308435b3dcf03c06d9fe8740a77469`
（piston-meta。46,592,587バイト。`run/vanilla/server-1.18.2.jar`）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.18.2.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  `PacketIds758Test`（8件）、`ProtocolVersion758Test`（3件）、
  `ClientAdapter758Test`（10件）、`ClientJoinFlow758Test`（5件）、
  `RegistryRemapper758Test`（12件）、`ChunkTranslator758Test`（27件）、
  `PlayTranslator758Test`（18件）、`ProxySession758Test`（21件）、
  `ResourcePackBuilder758Test`（3件）、`ResourcePackMode758Test`（6件）、
  `ResourcePackServer758Test`（3件）。計116件。
- 配線テスト（`detergentmc-core`）：`ServerProtocolWiringTest`（1.18.2→758）、
  `ResourcePackWiringTest`（758パックの配信）、`AdapterDiscoveryTest`（V1182Adapter）。
- Bedrock（`detergentmc-bedrock`）：`BedrockEntityTranslator758Test`（7件）、
  `JavaBackendConnection758Test`（10件）。
- 統合テスト（`ProxyIntegrationTest`）：偽758クライアント→本物のバニラ1.18.2
  （758ネイティブ）、偽758クライアント→本物のバニラ1.19（759）、
  偽758クライアント→本物のバニラ1.20.1（763）、
  偽758クライアント→本物のバニラ1.21.11（774）、偽759クライアント→本物の
  バニラ1.18.2（758）、偽763クライアント→本物のバニラ1.18.2（758）。
  いずれも参加・スポーン・チャット・移動まで確認する設計。
  TCP が許可された環境（775の94件を通したのと同じ環境、2026-11-15）で
  実行し、6件とも通った。`docs/VERSIONS.md` の6セルは ◎／○ に直してある。
- 既存テストの更新：758を受け入れるようになったため、758拒否のテストは757に
  ずらした（`ClientAdapter.decide(758` → `decide(757` など）。
  対応プロトコル一覧の断言には758を先頭に追加した。
