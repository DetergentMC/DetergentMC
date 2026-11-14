# 1.19クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.19クライアント（プロトコル759）は、内部プロトコルが
759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774のどのサーバーにも参加できます。内部759のときは
素通し（リレー）、それ以外は759↔760の直接変換のあと、既存の
760↔761の変換と761↔762の変換と762↔763の変換と763↔764↔765↔…↔774の変換を重ねます。**759は760＋差分のシム（shim）**です。
760とのパケットIDの違いは131件で、ペイロードが違うのは
9種類のパケットだけです。レジストリ差分は**全カテゴリ0件（恒等写像）**です。
ブロック状態21,448件、アイテム1,152件、エンティティ118件、音1,321件、
バイオーム63件、パーティクル93件、エンチャント39件、エフェクト33件、
ブロックエンティティ種別36件、メニュー24件、ダメージ種別42件はすべて
759と760で名前も順番も同一です。**ダメージ種別は759の codec に存在しません**（1.19.4で追加。
本物の1.19サーバーの Join Game codec で確認——chat_type・dimension_type・
worldgen/biome の3レジストリだけ。760・761と同じ）。

1.19は**Java 17**で動き、設定フェーズがありません（764で追加）。
レジストリは Join Game の codec で届きます（763と同じ方式）。
pack_format はリソース9・データ10（Minecraft Wiki の pack_format 表。
760と同じ9／10）。アイテムはNBT（データコンポーネントではない）。

759の仕組みは `docs/PROXY-1.19.1.md`（760）、`docs/PROXY-1.19.3.md`（761）、
`docs/PROXY-1.19.4.md`（762）、`docs/PROXY-1.20.1.md`（763）、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。

全256通り（クライアント759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774 × サーバー759／760／761／762／763／764／
765／766／767／768／769／770／771／772／773／774）の対応マトリクスは `docs/VERSIONS.md` の「1.19対応：
全256通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用の16つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 759↔760の差分（要点）

minecraft-data（`pc/1.19/protocol.json`・`pc/1.19.2/protocol.json`）と
ViaVersion（`mapping-1.19.json`）で確認。759の minecraft-data の pc ファイルは
`mcdata760/pc/1.19/` にある（`mcdata759/pc/1.19/` ではない）。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-759.py`・
`gen-759-tables.py`・`gen-759-remapper.py`・`insert-759-packet-ids.py`。
sounds.json は minecraft-data の pc ディレクトリには存在しないため、音の
件数は `mapping-1.19.json`（1,321件）で確認した。

| 分野 | 759↔760の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ） | 760とIDが違うものが131件（759に存在するものは164件／PacketIds の enum 数は262件）。Join Game 0x23→0x25、Spawn Position 0x4a→0x4d、Keep Alive 0x1e→0x20、Chunk Data 0x1f→0x21、Disconnect 0x17→0x19、Player Chat 0x30→0x33、System Chat 0x5f→0x62、Server Data 0x3f→0x42、Trade List 0x25→0x27、Resource Pack 0x3a→0x3d、Chat Message（サーバー行き）0x04→0x05、Chat Command（サーバー行き）0x03→0x04、Set Player Position（サーバー行き）0x13→0x14、Window Click（サーバー行き）0x0a→0x0b、Chat Preview（サーバー行き）0x05→0x06（クライアント行きは両方とも 0x0c） | `PacketIds` の759列で番号を付け替える |
| パケットID（プレイ・760にだけある） | chat_suggestions（0x15）、hide_message（0x18）、message_header（0x32）、message_acknowledgement（サーバー行き 0x03） | 759クライアントには落とす。759サーバーには message_acknowledgement を送らない（落とす） |
| パケットID（プレイ・759にだけある） | legacy_server_list_ping（0xfe、ハンドシェイク・サーバー行き） | 対象外（760と同じ扱い） |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一（Login Start 0x00、Login Success 0x02、Keep Alive のログイン版も同一） | そのまま通す |
| 設定フェーズ | **759にも760にも無い**（764で追加） | プロキシが合成・消費する（760と同じ。`docs/PROXY-1.19.3.md` の「設定フェーズなしクライアントの扱い方」を参照） |
| Join Game | 759と760で同一（entityId、hardcore、gameMode、previousGameMode、worldNames、dimensionCodec、worldType、worldName、hashedSeed、maxPlayers、viewDistance、simulationDistance、reducedDebugInfo、enableRespawnScreen、isDebug、isFlat、death）。portalCooldown はどちらにも無い（763で追加） | そのまま通す（`PlayTranslator.parseJoinGame762` は759の Join Game も読む）。codec は素通しする |
| Join Game codec の中身 | **本物の1.19の codec は chat_type・dimension_type・worldgen/biome の3つだけ**。minecraft:damage_type レジストリは無い（1.19.4で追加） | codec は素通しするため表は要らない。ダメージ種別の対応表は恒等写像として残すが、ワイヤ上の呼び出し元は無い（760・761と同じ） |
| Synchronize Player Position | 759と760で同一 | そのまま通す |
| Server Data | **759は {motd option, icon option, previewsChat bool}、760は {motd option, icon option, previewsChat bool, enforcesSecureChat bool}** | 759→760で enforcesSecureChat=false を補う。760→759で落とす（`PlayTranslator.translateServerData759To760`／`760To759`） |
| Respawn | gamemode が759は u8、760は i8。ワイヤ上は同じ1バイト | そのまま通す（翻訳子なし） |
| Multi Block Change | bool の名前が違うだけ（759は `notTrustEdges`、760は `suppressLightUpdates`）。同じ位置の1バイト | そのまま通す（翻訳子なし）。意味の違いは未確認のため制限として残す |
| Trade List | **trades の件数が759は u8、760は varint**。スロットの中身は同一 | u8⇔varint で書き直す。件数のあとはそのまま通す（`ChunkTranslator.translateTradeList759To760`／`760To759`） |
| System Chat | **759は {content string, type varint}、760は {content string, isActionBar bool}**。759の type 2 がアクションバー | type 2⇔isActionBar true で変換する。760→759は false を type 1（system）にする（`PlayTranslator.translateSystemChat759To760`／`760To759`）。759の type 0（chat）と type 1（system）の区別は760向けに失われる |
| チャット | **759は760と形式が違う**。下の「チャット（759は初代の署名形式）」を参照。Chat Message／Chat Command／Player Chat はすべて初代のインライン署名形式 | 専用の翻訳子で変換する（`PlayTranslator.translateChatMessage759To760` など）。署名チェーンの検証は境界を越えられない（下を参照） |
| Login Start／Login Success | **759の Login Start は {username, signature option {timestamp i64, publicKey buffer, signature buffer}}**（UUID欄そのものが無い）。760は {username, signature option, playerUUID option UUID} | 759→760で playerUUID option=false を補う。760→759で UUID欄を落とす（`PlayTranslator.translateLoginStart759To760`／`760To759`）。下の「Login Start の罠」を参照 |
| Player Info | 759と760で同一の旧式（action varint。add_player は crypto オプション付き、UUID は無い） | 番号だけ付け替える（翻訳子なし） |
| Sound Effect | 759と760で同一（soundId varint） | そのまま通す |
| Explosion | 759と760で同一（x／y／z は f32） | そのまま通す |
| Named Sound Effect | 759と760の両方にある（0x16）。760は 0x17 | 番号だけ付け替える |
| Named Entity Spawn／Spawn Entity | 両方ともある（Named Entity Spawn 0x02、Spawn Entity 0x00）。「761〜763にだけある」という古いコメントは誤り | 番号だけ付け替える |
| Declare Commands の引数種別 | 759と760で同一（mob_effect／item_enchantment／entity_summon） | そのまま通す（760と同じ） |
| Declare Recipes | 759と760で同一（category 無し） | 全方向で未対応（素通しもしない。既存の制限と同じ） |
| ブロック状態 | **759=21,448件、760=21,448件。差分0件（恒等写像）** | 変換なし（`RegistryTables759` は恒等範囲だけ）。blocks.json の `drops` メタデータだけ3件違う（fern、attached_pumpkin_stem、attached_melon_stem）がIDには影響しない |
| ブロック | **759=933件、760=933件。差分0件** | 変換なし |
| アイテム | **759=1,152件、760=1,152件。差分0件** | 変換なし |
| エンティティ | **759=118件、760=118件。差分0件**（player は759=116、760=116、pig は759=67、760=67、falling_block は759=29、760=29） | 変換なし |
| 音 | **759=1,321件、760=1,321件。差分0件**（block.wood.break は759=1278、760=1278） | 変換なし |
| パーティクル | 93件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エンチャント | 39件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エフェクト | 33件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| バイオーム | **759=63件、760=63件。同じ顔ぶれ・同じ順番**（plains は759=1、760=1） | 変換なし（identity）。760↔761では並びが違うが、759↔760では違わない |
| ブロックエンティティ種別 | 36件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| メニュー | 24件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 絵画 | 30件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| ダメージ種別 | **759の codec に存在しない**。760の codec にも存在しない。762=42件 | 対応表は恒等写像として残すが、ワイヤ上の呼び出し元は無い（760・761と同じ） |
| 属性 | 13件で同じ（文字列キー。数値表は不要） | そのまま通す |
| 楽器 | 23件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| Entity Metadata の型番号 | 759・760で同一 | variant 系は落とさず通す |

## チャット（759は初代の署名形式）

**依頼文の「チャットは無署名（署名付きチャットは760で導入された）」は誤りだった**。データと
公式文書で確認したところ、署名付きチャットは **759（1.19）で導入**された。
node-minecraft-protocol の文書（"Starting in Minecraft 1.19, client messages sent to the
server are signed"）、Paper の文書（"Since Minecraft version 1.19, the client now signs any
messages it sends"）、blog.bithole.dev（"Since 1.19, this has been the on-wire structure of
the serverbound chat packet"）の3つで確認した。**760（1.19.1）で変わったのは署名の形式**で、
連鎖ハッシュ（SHA256）＋Message Header パケット＋previousMessages の分離形式になった。
759はこの境界で唯一の初代インライン形式である。既存の `ClientAdapter.java` にあった
「署名付きチャットは760で導入された旧形式」というコメントも、759対応のときに直した。

- **Player Chat**：759は {signedChatContent string, unsignedChatContent option, type varint,
  senderUuid, senderName string, senderTeam option, timestamp, salt, signature buffer}。
  760は {previousSignature option, senderUuid, signature 必須 buffer, plainMessage,
  formattedMessage option, timestamp, salt, previousMessages, unsignedChatContent option,
  filterType..., type, networkName, networkTargetName}。
  759→760は plainMessage に signedChatContent、networkName に senderName、networkTargetName に
  senderTeam を入れ、previousSignature 無し・previousMessages 空（0件）・filterType=0 で送る。
  Message Header は合成しない（761→760と同じ方針）。
  760→759は previousSignature／formattedMessage／previousMessages／filter を落とし、
  plainMessage を signedChatContent、networkName を senderName に入れる。
- **Message Header**：760にだけある（クライアント行き 0x32）。759には無い。
  759クライアントには送らない。760クライアントが759サーバーのチャットを受け取るとき、
  Message Header は合成しないため、実機の760クライアントは759サーバーからのチャットを
  未検証（署名なし）と表示する可能性がある。**本物のクライアントでの確認は未実施**。
- **Chat Message（サーバー行き）**：759は {message, timestamp, salt, signature buffer(varint),
  signedPreview bool}。760は {message, timestamp, salt, signature buffer(varint),
  signedPreview bool, previousMessages [{sender, sig}], lastRejectedMessage option}。
  759→760は previousMessages を空（0件）、lastRejectedMessage を無しで補う。
  760→759は previousMessages／lastRejectedMessage を落とす。引数の署名（argumentSignatures）は
  byteArray のまま通す。
- **Chat Command（サーバー行き）**：759は {command, timestamp, salt, argumentSignatures,
  signedPreview bool}。760は {command, timestamp, salt, argumentSignatures,
  signedPreview bool, previousMessages, lastRejectedMessage option}。Chat Message と同じ扱い。
- **Chat Preview**：759と760で形式は同一（サーバー行き {query i32, message string}、
  クライアント行き {queryId i32, message option}）。番号だけ付け替える（サーバー行き 0x05→0x06、
  クライアント行きは両方とも 0x0c）。
- **Should Display Chat Preview**：759と760で形式は同一（{bool}）。番号だけ付け替える（0x4b→0x4e）。
- **Player Info**：759と760で同一の旧式（action varint。add_player は crypto オプション付き）。
  番号だけ付け替える。760→761のときのような展開は要らない。
- **署名チェーンの検証**：境界を越えると署名チェーンはつながらない。
  previousMessages は空で合成する。チャット報告（chat reporting）は
  760で追加されたが、プロキシ経由での報告の扱いは未確認。**正直な制限として残す**。

## 759↔760の連鎖の方針

- 759クライアントが760サーバーに参加するとき：759→760の直接変換で Join Game（0x23→0x25）・
  チャンク・スロット・エンティティ・音・位置・Server Data（enforcesSecureChat を落とす）・
  チャット（Player Chat／System Chat／Chat Message／Chat Command の初代形式→760形式）を直し、
  IDを760の番号に付け替える。760にだけある chat_suggestions／hide_message／
  message_header を落とす。message_acknowledgement は759サーバーに送らない。
- 760クライアントが759サーバーに参加するとき：760→759の直接変換で Join Game（0x25→0x23。
  codec は素通し。759の codec に damage_type レジストリは無い）・チャンク・位置・
  Server Data（enforcesSecureChat=false を補う）・チャット（Player Chat／System Chat／
  Chat Message／Chat Command の760形式→初代形式）を直す。Login Start は759形式
  （UUID欄なし）に直す。
- 759クライアントが761以上のサーバーに参加するとき：759→760のあと、既存の760→761→762→763→764→…の変換を重ねる。764以上のサーバーにはプロキシが設定フェーズを消費し、759の Join Game に codec を合成する（760と同じ流儀）。
- 761以上のクライアントが759サーバーに参加するとき：既存の変換で763→762→761→760まで戻したあと、760→759の直接変換をする。764以上のクライアントにはプロキシが759サーバーの Join Game codec から設定フェーズを合成する。**合成する設定フェーズに damage_type レジストリは入らない**（759の codec に無いため）。764以上のクライアントが damage_type 無しの Registry Data を受け入れるかは未確認。
- 759＋759は素通し（リレー）。Spawn Entity も Named Entity Spawn も変換しない。**759クライアントが Spawn Entity（player種別）を受け入れるかは、本物のゲームクライアントでの確認が必要**（760・761と同じ）。

## ダメージ種別は759に存在しない

当初は「759のダメージ種別は760の42件と同じ順番」だと仮定して対応表を作っていたが、
本物のバニラ1.19サーバーに偽759クライアントで参加し、Join Game の codec を読むと、
**codec にあるのは chat_type・dimension_type・worldgen/biome の3レジストリだけで、minecraft:damage_type は存在しなかった**
（`RegistryOrderCaptureTest` と同じ流儀の確認を `ProxyIntegrationTest` の Join Game 断言で実施。
codec に dimension_type と biome が含まれることを確認）。
つまり damage_type レジストリは1.19.4で追加されたもので、759にも760にも761にもダメージ種別のID自体が存在しない。ワイヤ上ではダメージ種別は Damage Event パケット（762専用。759クライアントには落とす）と codec の中にしか現れないため、759↔760のダメージ種別変換は実質的に不要である。`RegistryRemapper` の対応表（`mapDamageType760To759`／`mapDamageType759To760`）は恒等写像として残すが、呼び出し元は無い。バイオームは63件で759と760は**並びも同じ**（plains は759=1、760=1）。

## 接続の流れ

### 759クライアント → 内部759サーバー

1. ハンドシェイク（759、状態2）。
2. Login Start（名前＋署名データ無し）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x23、codec 付き）。
5. Spawn Position（0x4a）。チャンク（0x1f）・Player Info（0x34）などが届く。

### 759クライアント → 内部760サーバー

1. ハンドシェイク（759、状態2）。
2. Login Start（名前＋署名データ無し）。プロキシが760形式（名前＋署名データ無し＋UUID無し）に直して送る。
3. Login Success。
4. すぐプレイ状態へ。Join Game（760形式 0x25）を759形式（0x23）に変換する。codec は素通し。
5. Spawn Position（760形式 0x4d→759形式 0x4a）。リソースパックは760形式 0x3dで送り、変換で759の0x3aに付け替える（`pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 759クライアント → 内部774サーバー

1. ハンドシェイク（759、状態2）。
2. Login Start、Login Success。
3. プロキシが774の設定フェーズを消費する（Select Known Packs に空で答える、Registry Data を保持する、Finish Configuration に答える、Login Acknowledged を送る）。
4. 774の Join Game（0x30）を763形式（0x28）に直し、さらに762形式・761形式・760形式・759形式（0x23）に変換する。codec は763向けに合成したもの（dialog レジストリは合成しない）。
5. Spawn Position（774形式 0x5F→759形式 0x4a）。

### 760クライアント → 内部759サーバー

1. ハンドシェイク（760、状態2）。
2. Login Start（名前＋署名データ無し＋任意UUID）。プロキシが759形式（名前＋署名データ無し）に直して送る。
3. Login Success。
4. すぐプレイ状態へ。759サーバーの Join Game（0x23）を760形式（0x25）に変換する。codec は素通し。
5. Spawn Position（759形式 0x4a→760形式 0x4d）。

## プレイ中の翻訳

- **チャンク**：ブロック状態（759=21,448／760=21,448／761=23,232／762=23,725／763=24,135）・バイオーム（759=1／760=1／761=38／762=38／763=39の plains）・ブロックエンティティ種別を付け替える。Multi Block Change の bool は759・760・761・762のすべてにあるため、762⇔761⇔760⇔759では付け外し不要。763との間では付ける／落とす。
- **スロット・ウィンドウ**：アイテムID（759=1,152／760=1,152／761=1,186／762=1,228／763=1,255）を付け替える。Window Click（759=0x0A／760=0x0B／761=0x0A／762=0x0B／763=0x0B）・Set Creative Slot（759=0x2A／760=0x2B／761=0x2B／762=0x2B／763=0x2B）は変換する（落とさない）。
- **エンティティ**：エンティティ種別（player は759=116／760=116／761=117／762=122／763=122、falling_block は759=29／760=29／761=30／762=36／763=36、pig は759=67／760=67／761=68／762=72／763=72）を付け替える。
- **音・パーティクル**：音（759=1,321／760=1,321／761=1,392／762=1,454／763=1,474）・パーティクル（759=93／760=93／761=93／762=96／763=95）の番号を付け替える。
- **位置・Server Data・Trade List**：上の表の通り。
- **Spawn Entity／Named Entity Spawn**：759サーバー→760以上クライアントは Named Entity Spawn を Spawn Entity（player種別）に直す。760以上サーバー→759クライアントは Spawn Entity（player種別）を Named Entity Spawn（0x02）に直す。759＋759は素通し。

## 送らない・落とすパケット（759向け）

- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応
  （既存の制限と同じ）。
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす。
- ダメージ種別が範囲外の場合：Damage Event をパケットごと落とす。
  Damage Event 自体が762専用のため、759クライアントには常に落とす。
- 760にだけある chat_suggestions／hide_message／message_header：
  759クライアントには落とす。
- 759サーバーには message_acknowledgement を送らない（759に無いため落とす）。
- 761にだけある feature_flags／player_remove／profileless_chat／chat_session_update：
  759クライアントには落とす。
- 759に無いブロック状態・アイテム・音・エンティティ：無い（759↔760は全カテゴリ同一）。
  761以上の新規（bamboo 系・camel など）は760↔761の代替表で処理する。

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全256通り共通。手順書は
  `docs/PLAYTEST-1.19.md`。偽クライアント＋本物のバニラでの統合テストは
  7セル（759→759（1.19）、759→760（1.19.1／1.19.2）、759→761、759→762、759→763、760→759（1.19））で参加・スポーン・チャット・移動まで確認済み。
- **チャットの署名チェーンは境界を越えられない**：759→760は previousMessages を空にし、
  Message Header を合成しない。実機の760クライアントが759サーバーからの
  チャットを未検証と表示する可能性がある。本物のクライアントでの確認が必要。
  759クライアントが760以上のサーバーからのチャットをどう表示するかも未確認。
- **Multi Block Change の bool の意味の違いは未確認**：759は `notTrustEdges`、760は
  `suppressLightUpdates`。同じ位置の1バイトとしてそのまま通しているが、
  意味が違う場合の挙動は本物のクライアントでの確認が必要。
- **System Chat の type 0／type 1 の区別は760向けに失われる**：759の type 0（chat）と
  type 1（system）はどちらも760の isActionBar=false になる。
- **Chat Session Update は759サーバーに届かない**：プロキシは Login Start を作り直すため、
  760以上のクライアントの鍵は759サーバーに届かない。759サーバー側のチャット署名検証が
  どう動くかは未確認。
- **Declare Commands の引数種別は変換しない**：759と760は同じ（mob_effect／
  item_enchantment／entity_summon）だが、761の gamemode／resource_key／
  resource_or_tag_key との違いは、どのバージョン間でも変換していない。
  コマンドの引数補完がずれる可能性がある。
- **764以上のクライアントが759サーバーに参加するときの設定フェーズ合成**：
  759の codec に damage_type レジストリが無いため、合成する設定フェーズに
  damage_type が入らない。764以上のクライアントが受け入れるかは未確認。
- **Join Game codec の素通し**：761サーバーの codec（bamboo 入りのブロック一覧）が
  759クライアントにそのまま届く。Mojang の codec は未知のブロックを無視するはずだが未確認。
  774サーバーの codec（trim の追加フィールド、damage_type の `effects`、cherry_grove 入りの
  バイオーム一覧）も759クライアントにそのまま届く。同じく未確認。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  759＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **外部モードで pack759 が無い場合**：759クライアントには760パック
  （pack_format 9）が出る。759と760は同じ format 9 のため、実質的に同じパック。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrock→759**：`JavaBackendConnection` の serverProtocol 759 を760扱いにした
  （Login Start の759形式、Login Success の設定フェーズなし、Join Game の
  portalCooldown=0、Player Info の旧式 action、Player Chat の759形式、Chat Message／
  Chat Command の初代インライン署名形式）。
  Bedrock 側の統合テストはまだ無い。Bedrock のインベントリは引き続き未対応。Bedrock クライアントから
  759サーバーを見たとき、ブロック状態は761の番号のまま765として通す
  （760・761と同じ近似。ブロックによっては違うブロックに見える）。

## 759のときに見つかった罠（後続の担当へ）

- **依頼文の「チャットは無署名（署名付きチャットは760で導入された）」は誤り**：署名付き
  チャットは759（1.19）で導入された。760で変わったのは形式（連鎖ハッシュ＋Message Header）。
  依頼文の記述はデータと公式文書で再確認すること。759はこの境界で唯一の初代形式である。
  既存コメントの「署名付きチャットは760で導入された旧形式」も誤りだったため直した。
- **Login Start の罠（Stage E で見つけた実バグ）**：759クライアントが内部760サーバーに
  参加するとき、`ProxySession.handleClientLogin` の `is759()` 分岐に `isServer760()` の
  場合が無く、765形式の Login Start（{name, UUID}）がそのまま760サーバーに送られていた。
  760サーバーは {name, signature option, playerUUID option} を期待するため UUID の
  バイト列を署名データとして誤読し、DecoderException（readerIndex(16) + length(95)
  exceeds writerIndex(22)）で切断した。対策は `translateLoginStart759To760` で760形式に
  直してから送ること。新しいプロトコルを追加するときは、Login Start の分岐の
  すべての組み合わせ（クライアント側×サーバー側）に漏れが無いか確認すること。
  `completeAuthentication` 側は既に `isServer760()` を扱っていたため修正不要だった。
- **`handleServerJoinGame763` に759が無かった**（Stage E で見つけた実バグ）：
  `if (is762() || is761() || is760())` だけだったため、759クライアントの Join Game が
  765既定の 0x29 で返され、変換チェーンで誤ったID（0x24）になっていた。
  `is759()` を追加し、763形式のまま返して四段変換に任せること。
- **`handleServerPlayFrom763` の末尾に759が無かった**（Stage E で見つけた実バグ）：
  `if (is761() || is760())` だけだったため、759クライアント向けのパケットが
  `translateId(763→759)` で付け替えられ、変換チェーンで誤読されていた。
  `is759()` を追加し、763形式のまま返すこと。
- **Login Success 処理に内部759が無かった**（Stage E で見つけた実バグ）：
  `is763Like() && (isServer763Like() || isServer760())` だけだったため、設定フェーズの無い
  クライアント→内部759サーバーで誤って CONFIGURATION 状態に入り、Login Acknowledged＋
  Client Information を設定フェーズの無い759サーバーへ送っていた。
  `isServer759()` を追加すること。
- **`ClientAdapter.differences(759, server)` に V1_19 の分岐が無かった**（Stage E で見つけた実バグ）：
  2引数の `differences()` は汎用の763向け文言にフォールスルーしていた。新しいプロトコルを
  追加するときは、2引数の `differences()` にも専用の分岐を入れること。
- **`ClientAdapter.supportedClientProtocols()` はハードコード**（Stage D で見つけた実バグ）：
  OLDEST_SUPPORTED_CLIENT_PROTOCOL を760から759に変えただけでは、760が一覧から
  漏れて全ての760クライアントが拒否された。古い最古バージョンを明示的に残すこと。
- **`ResourcePackMode.decide()` の最終オーバーロードは委譲で残す**（Stage D の罠）：
  759の `(url759, sha1759)` 追加で最終オーバーロードの引数が34個になった。古い最終
  オーバーロード（url760／sha1760まで）を null で委譲する形で残さないと、
  `ResourcePackMode760Test` がコンパイルできなくなる。
- **pack_format 9 は759と760で共有**：`PACK_FORMAT_1_19 = 9` は `PACK_FORMAT_1_19_1` の
  別名で、`ResourcePackBuilder.writePack` の switch に759の case を別に書くと
  重複ラベルでコンパイルエラーになる。759は760の case を流用すること。
- **759の minecraft-data は `mcdata760/pc/1.19/` にある**：`mcdata759/pc/1.19/` ではない。
  blocks.json は辞書ではなくリスト。sounds.json は minecraft-data の pc ディレクトリには
  存在しないため、音は ViaVersion の `mapping-1.19.json` で確認すること。
- **`muse.edit` は短い old_text が複数箇所にマッチすると最初の1件を置き換える**：
  Stage B で `packFor763LikeClient` の中にプロキシのコードを誤挿入しそうになった。
  必ず十分な前後文脈で一意にすること。
- **759は760の土台の上に積む**：759（1.19）の対応は760の760↔761変換を流用する。
  760↔761の差分（特にチャットの旧式形式・Player Info の旧式形式・Sound Effect の
  varint id・Explosion の f32）は759でも同じ流儀で扱うこと。ただし759↔760の
  チャットは760↔761のチャットとは別形式（初代インライン署名形式）なので、
  760の翻訳子をそのまま流用せず、759専用の翻訳子を書くこと。

## Java 17（内部759サーバーを動かすとき）

1.19は **Java 17** で動く。Java 21 は不要。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.19は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.19` を指定すると、内部759サーバーが Java 17 で起動する。
バニラ1.19サーバーjarのSHA-1は `e00c4052dac1d59a1188b2aa9d5a87113aaf1122`
（piston-meta。45,538,778バイト。`run/vanilla/server-1.19.jar`）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.19.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  `PacketIds759Test`（9件）、`ProtocolVersion759Test`（3件）、
  `RegistryRemapper759Test`（11件）、`ChunkTranslator759Test`（5件）、
  `PlayTranslator759Test`（21件）、`ClientAdapter759Test`（13件）、
  `ClientJoinFlow759Test`（5件）、`ProxySession759Test`（27件）、
  `ResourcePackBuilder759Test`（2件）、`ResourcePackMode759Test`（6件）、
  `ResourcePackServer759Test`（2件）。計104件。
- 単体テスト（`detergentmc-bedrock`）：`JavaBackendConnection759Test`（6件）。
- 統合テスト（`ProxyIntegrationTest`）：偽759クライアント→本物のバニラ1.19
  （759ネイティブ）、偽759クライアント→本物のバニラ1.19.1（760）、
  偽759クライアント→本物のバニラ1.19.2（760）、偽759クライアント→本物の
  バニラ1.19.3（761）、偽759クライアント→本物のバニラ1.19.4（762）、
  偽759クライアント→本物のバニラ1.20.1（763）、偽760クライアント→本物の
  バニラ1.19（759）。いずれも参加・スポーン・チャット・移動まで確認。
  2人プレイの統合テストは偽クライアントでは他プレイヤーのパケットが届かないため
  書いていない（761・760と同じ理由）。
- 既存テストの更新：759を受け入れるようになったため、759拒否のテストは758に
  ずらした（`ClientAdapter.decide(759` → `decide(758` など）。
  対応プロトコル一覧の断言には759を先頭に追加した。
  `ResourcePackMode.decide()` は最終オーバーロードに `(url759, sha1759)` を
  追加し、古い最終オーバーロードは null で委譲する形にしたため、既存の
  760〜774テストの `decide()` 呼び出しはそのまま動く（762・761・760の罠の再発を避けた）。
