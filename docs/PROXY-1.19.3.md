# 1.19.3クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.19.3クライアント（プロトコル761）は、内部プロトコルが
761／762／763／764／765／766／767／768／769／770／771／772／773／774のどのサーバーにも参加できます。内部761のときは
素通し（リレー）、それ以外は761↔762の直接変換のあと、既存の
762↔763の変換と763↔764↔765↔…↔774の変換を重ねます。**761は762＋差分のシム（shim）**です。
762とのパケットIDの違いは132件（新規2件を含むと134件）で、ペイロードが違うのは
約10種類のパケットだけです。レジストリ差分はブロック状態+493件、
アイテム+42件、音+62件、パーティクル+3件、エンティティ+5件、ブロックエンティティ+2件、
メニュー+1件（legacy_smithingの分離）です。バイオーム・絵画・エンチャント・エフェクトは
761と762で同一です。**ダメージ種別は761の codec に存在しません**（1.19.4で追加。
本物の1.19.3サーバーの Join Game codec で確認）。

763の仕組みは `docs/PROXY-1.20.1.md`、762は `docs/PROXY-1.19.4.md`、767は `docs/PROXY-1.21.md`、774は
`docs/PROXY-1.21.11.md`、逆向きの考え方は `docs/PROXY-REVERSE.md` を参照してください。

全196通り（クライアント761／762／763／764／765／766／767／768／769／770／771／772／773／774 × サーバー761／762／763／764／765／
766／767／768／769／770／771／772／773／774）の対応マトリクスは `docs/VERSIONS.md` の「1.19.3対応：
全196通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・761用・762用・763用・764用・766用・767用・768用・769用・770用・771用・772用・773用・774用の14つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 761↔762の差分（要点）

minecraft-data（`pc/1.19.3/protocol.json`・`pc/1.19.4/protocol.json`）と
ViaVersion（`mapping-1.19.3.json`・`mapping-1.19.4.json`）で確認。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-761.py`・
`gen-761-tables.py`・`verify-761-tables.py`・`gen-761-remapper.py`・
`gen-761-chunk.py`・`insert-761-packet-ids.py`。

| 分野 | 761↔762の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ・クライアント行き） | 762とIDが違うものが132件（新規2件を含むと134件）。761に存在するものは107件。Join Game 0x28→0x24、Spawn Entity 0x01→0x00、Chat Session Update 0x06→0x20、Resource Pack 0x40→0x3c、Keep Alive 0x23→0x1f、Chunk Data 0x24→0x20、Respawn 0x41→0x3D、Advancements 0x69→0x65、Ping 0x32→0x2E、Spawn Position 0x50→0x4C、Disconnect 0x1A→0x17、Window Click（サーバー行き）0x0B→0x0A。Set Creative Slot 0x2B、Update Sign 0x2E、Chat Message 0x05 は3バージョンとも同一 | `PacketIds` の761列で番号を付け替える。サーバー行きは27件のIDが変わる（追加・削除なし） |
| パケットID（プレイ・クライアント行き・762にだけある） | bundle_delimiter、chunk_biomes、damage_event、hurt_animation（1.19.4で追加） | 761クライアントには落とす。bundle_delimiter は761の0x00（Spawn Entity）と衝突するため、素通しすると誤読される。必ず落とすこと |
| パケットID（761にだけある） | named_entity_spawn（761=0x02、762=0x03、763=0x03、764以降は無い）、feature_flags（761=0x67、762=0x6b、763=0x6b、764以降は無い）。`PLAY_NAMED_ENTITY_SPAWN`・`PLAY_FEATURE_FLAGS` を `PacketIds` に新規追加した（全14プロトコルで衝突なし） | Spawn Entity（player種別）⇔ Named Entity Spawn の相互変換をする。Feature Flags はIDだけ付け替える（形式は3バージョンとも同一） |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一 | そのまま通す |
| 設定フェーズ | **761にも762にも無い**（764で追加） | プロキシが合成・消費する（762と同じ。`docs/PROXY-1.19.4.md` の「設定フェーズなしクライアントの扱い方」を参照） |
| Join Game | 761と762で同一（entityId、hardcore、gameMode、previousGameMode、worldNames、dimensionCodec、worldType、worldName、hashedSeed、maxPlayers、viewDistance、simulationDistance、reducedDebugInfo、enableRespawnScreen、isDebug、isFlat、death）。portalCooldown はどちらにも無い（763で追加） | そのまま通す（`PlayTranslator.parseJoinGame762` は761の Join Game も読む）。codec は素通しする |
| Join Game codec の中身 | **本物の1.19.3の codec は chat_type・dimension_type・worldgen/biome の3つだけ**。minecraft:damage_type レジストリは無い（1.19.4で追加）。バイオームは63件で762と同一（plains=38、cherry_grove 無し） | codec は素通しするため表は要らない。ダメージ種別の対応表は恒等写像として残すが、ワイヤ上の呼び出し元は無い（下の「ダメージ種別は761に存在しない」を参照） |
| Synchronize Player Position | **761は末尾に `dismountVehicle` bool がある**。762／763／764には無い | 762→761で false を補う。761→762で落とす（`PlayTranslator.translatePosition762To761`／`761To762`） |
| Server Data | **761は motd が [option, string]、icon が [option, string]**（base64の data URL 文字列）。762以降は motd が string、icon が [option, buffer]（PNGバイト列）。762形式には末尾に `enforcesSecureChat` bool もある | 762→761で motd を [option, string] に、icon を data URL 文字列に書き直す。761→762で逆向きに直す（`PlayTranslator.translateServerData762To761`／`761To762`）。icon 文字列は data URL の接頭辞があっても無くても Base64 として読む。読めない icon は無しにする |
| Respawn／Advancements／Death／End Combat | 761と762で同一（portalCooldown はどちらにも無い） | そのまま通す |
| Multi Block Change | 761と762の両方に `suppressLightUpdates` bool がある（1.20で削除） | そのまま通す（762⇔761では付け外し不要） |
| Chunk Data | 761と762の両方に `trustEdges` bool がある（1.20で削除） | そのまま通す。ブロック状態・バイオーム・ブロックエンティティ種別だけ付け替える |
| Update Light | 761と762の両方に `trustEdges` bool がある（1.20で削除） | そのまま通す |
| Open Sign Editor／Update Sign | 761と762で同一（位置＋4テキスト。isFrontText は1.20で追加） | そのまま通す |
| Declare Recipes | smithing の形式が違う（761は旧式 base＋addition＋result、762は legacy_smithing／smithing の分離） | 全方向で未対応（素通しもしない。既存の制限と同じ） |
| チャット | Chat Message／Chat Command／Player Chat／System Chat は761・762・763で同一（署名付きチャット。1.19.1以降） | そのまま通す。署名は落とさない（762⇔761では形式が同じため） |
| Login Start／Login Success | 同一（名前＋bool＋任意UUID／UUID＋名前＋プロパティ数） | そのまま通す。ただし内部761サーバーへの Login Start は bool を落としてはいけない（762と同じ罠） |
| ブロック状態 | 761=23,232件、762=23,725件（+493）。762にしか無い493件はすべて cherry 系（cherry_leaves など）・decorated_pot・suspicious_sand・torchflower・pink_petals・sniffer 関連。761にしか無いものは無い（762は純粋な追加） | 対応表で変換。無いものは近い既存ブロックの既定状態に代替する（cherry_*→oak_*、suspicious_sand→sand、torchflower→dandelion、torchflower_crop→wheat、pink_petals→peony、decorated_pot→flower_pot）。石への代替は範囲外だけ |
| ブロック | 761=972件、762=998件（+26）。762新規は cherry 系26件（cherry_log など）・suspicious_sand・decorated_pot・torchflower・torchflower_crop・pink_petals | 同上（ブロックIDも同じ代替） |
| アイテム | 761=1,186件、762=1,228件（+42）。762新規42件は cherry 系・sniffer_spawn_egg・torchflower・pink_petals・torchflower_seeds・brush・鍛冶型テンプレート11件・陶片4件・cherry_boat／chest_boat・cherry_sign／hanging_sign・decorated_pot・suspicious_sand | 対応表で変換。新規は近い既存アイテムに代替する（cherry_*→oak_*、sniffer_spawn_egg→turtle_spawn_egg、torchflower→dandelion、pink_petals→peony、torchflower_seeds→wheat_seeds、brush→stick、鍛冶型11件→netherite_ingot（netherite_upgrade）／iron_ingot（trim）、陶片4件→brick、cherry_boat／chest_boat→oak_boat／oak_chest_boat、cherry_sign／hanging_sign→oak_sign／oak_hanging_sign、decorated_pot→flower_pot、suspicious_sand→sand）。石への代替は範囲外だけ |
| エンティティ | 761=119件、762=124件（+5）。762新規は block_display・interaction・item_display・sniffer・text_display。共通の並びは同じではない（762のリストはアルファベット順、761はレジストリ順。pig は761=68、762=72、player は761=117、762=122、falling_block は761=30、762=36） | 対応表で変換。762にしか無い5種はブタ（761=68）へ写す（プロジェクトの既定の代替先。761クライアントではブタに見える）。範囲外もブタへ |
| 音 | 761=1,392件、762=1,454件（+62）。共通の並びも同じではない（挿入で番号がずれる）。762新規は cherry_wood 系・cherry_sapling・cherry_leaves・pink_petals・cherry hanging sign・cherry door／trapdoor／button／pressure_plate・cherry fence gate・decorated_pot・suspicious_sand・music.overworld.cherry_grove・intentionally_empty・brush・sniffer 系 | 対応表で変換。無いものは近い音に代替する（cherry_wood→block.wood.*、cherry_sapling／cherry_leaves／pink_petals→block.grass.*、cherry hanging sign→block.hanging_sign.*、cherry door／trapdoor／button／pressure_plate→block.wooden_door／trapdoor／button／pressure_plate.*、cherry fence gate→block.fence_gate.*、decorated_pot→block.stone.*（shatter だけ block.glass.break）、suspicious_sand→block.sand.*、music.overworld.cherry_grove→music.overworld.jungle_and_forest、intentionally_empty→block.stone.break、brush→block.sand.break、sniffer→ブタの音（pig.step／ambient／hurt／death。eat→entity.generic.eat、drop_seed→entity.chicken.egg））。範囲外は block.stone.break（761=1183） |
| パーティクル | 761=93件、762=96件（+3）。762新規は dripping／falling／landing_cherry_leaves。データ付きIDは sculk_charge（761=30／762=33）、item（761=39／762=42）、vibration（761=40／762=43）、shriek（761=92／762=95） | 対応表で変換。cherry_leaves 系3種は white_ash（761=79。パラメータ無しの粒子で見た目が一番近い）へ |
| エンチャント | 39件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エフェクト | 33件で同じ顔ぶれ・同じ順番 | 変換なし（identity）。ただし765は0ベース、761・762は1ベース（Speed は765=0、761／762=1） |
| バイオーム | 761=63件、762=63件。同じ顔ぶれ・同じ順番（plains は両方とも38。cherry_grove はどちらにも無い。1.20で追加） | 変換なし（identity） |
| ブロックエンティティ種別 | 761=38件、762=40件（+2）。762新規は suspicious_sand・decorated_pot | 対応表で変換。suspicious_sand・decorated_pot は DROP（-1。761に相当するものが無い。flower_pot にはブロックエンティティが無い） |
| メニュー | 761=24件、762=25件。762は legacy_smithing（20）と smithing（21）の分離。761の smithing（20・旧2スロット）は762の legacy_smithing（20）へ写す。762の smithing（21・新3スロット）は761に相当するものが無いため、旧 smithing（20）へ写す（多対一。762→763と同じ流儀）。761の smoker（21）→762の smoker（22）、cartography（22）→23、stonecutter（23）→24（identity ではない） | 対応表で変換。逆向きは改名を先に処理する（`rename_first_rev`） |
| 絵画 | 30件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| ダメージ種別 | **761の codec に存在しない**。762=42件（arrow=0、bad_respawn_point=1、…、wither_skull=41） | 対応表は恒等写像として残すが、ワイヤ上の呼び出し元は無い（下の「ダメージ種別は761に存在しない」を参照） |
| 属性 | 13件で同じ（文字列キー。数値表は不要） | そのまま通す（762↔763と同じ） |
| 楽器 | 23件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| Entity Metadata の型番号 | 761・762で同一（0=byte、1=int、…、17=particle、18=villager_data、19=optional_unsigned_int、20=pose、21=cat_variant、22=frog_variant、23=optional_global_pos、24=painting_variant、25=sniffer_state、26=vector3、27=quaternion） | variant 系は落とさず通す（765→763の落とし方とは違う。761・762は同じ番号のため） |
| トリム（trim_material／trim_pattern） | 1.19.3の codec にもトリムは入っている（763サンプルは10件／16件） | codec は素通しするため表は要らない。実機での確認は未実施 |

## 761↔762の連鎖の方針

- 761クライアントが762サーバーに参加するとき：761→762の直接変換で Join Game（0x24）・チャンク・スロット・エンティティ・音・位置（dismountVehicle を落とす）・Server Data を直し、IDを762の番号に付け替える。762にだけある Damage Event／Bundle Delimiter／Chunk Biomes／Hurt Animation は落とす。Spawn Entity（player種別）は Named Entity Spawn（0x02）の逆向き、すなわち762の Spawn Entity のまま通す（761サーバー側の話は下を参照）。
- 762クライアントが761サーバーに参加するとき：762→761の直接変換で Join Game（0x28→0x24。codec は素通し。762の codec にある damage_type レジストリは761クライアントにそのまま届く。未知のレジストリを無視するはずだが未確認）・チャンク（trustEdges は両方にあるため付け外し不要）・位置（dismountVehicle=false を補う）・Server Data を直す。
- 761クライアントが763以上のサーバーに参加するとき：761→762のあと、既存の762→763→764→…の変換を重ねる。764以上のサーバーにはプロキシが設定フェーズを消費し、761の Join Game に codec を合成する（762と同じ流儀）。
- 763以上のクライアントが761サーバーに参加するとき：既存の変換で763→762まで戻したあと、762→761の直接変換をする。764以上のクライアントにはプロキシが761サーバーの Join Game codec から設定フェーズを合成する。**合成する設定フェーズに damage_type レジストリは入らない**（761の codec に無いため）。764以上のクライアントが damage_type 無しの Registry Data を受け入れるかは未確認。
- 761＋761は素通し（リレー）。Spawn Entity も Named Entity Spawn も変換しない。**761クライアントが Spawn Entity（player種別）を受け入れるかは、本物のゲームクライアントでの確認が必要**（下の「named_entity_spawn」を参照）。

## ダメージ種別は761に存在しない

当初は「761のダメージ種別は762の42件と同じ順番」だと仮定して対応表を作っていた（minecraft-data に1.19.3の `loginPacket.json` が無かったため）。本物のバニラ1.19.3サーバーに偽761クライアントで参加し、Join Game の codec を読むと、**codec にあるのは chat_type・dimension_type・worldgen/biome の3レジストリだけで、minecraft:damage_type は存在しなかった**（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1193`）。つまり damage_type レジストリは1.19.4で追加されたもので、761にはダメージ種別のID自体が存在しない。ワイヤ上ではダメージ種別は Damage Event パケット（762専用。761クライアントには落とす）と codec の中にしか現れないため、761↔762のダメージ種別変換は実質的に不要である。`RegistryRemapper` の対応表（`mapDamageType762To761`／`mapDamageType761To762`）は恒等写像として残すが、呼び出し元は無い。バイオームは63件で762と同一（plains=38、cherry_grove 無し）であることも同じテストで確認した。

## named_entity_spawn（0x02）

minecraft-data には named_entity_spawn が1.19.3／1.19.4／1.20のプレイ（クライアント行き）に載っているが、実バニラ1.20.1はプレイヤーの出現に送ってこない（`docs/PROXY-1.19.4.md`）。実バニラ1.19.3が送るかどうかを、2人の偽761クライアントの統合テストで確かめようとしたが、**本物の1.19.3サーバーは偽クライアントには他プレイヤーの出現パケットも Player Info Update（ADD_PLAYER）も送ってこなかった**（直接接続でもプロキシ経由でも同じ。動物の Spawn Entity は届く。Client Information と繰り返しの移動パケットを送っても変わらなかった）。そのため named_entity_spawn を本物の1.19.3が送るかどうかは、偽クライアントでは確かめられない。2人プレイの統合テストは削除した（偽クライアントでは通らないため）。プロキシの変換自体は単体テストで確かめている（`ProxySession761Test.spawnEntityPlayer763ToNamedEntitySpawn761`：763サーバーの Spawn Entity（player）は761クライアントに Named Entity Spawn（0x02）で送る。`spawnEntityPlayer761ServerTo761Client`／`namedEntitySpawn761ServerTo761Client`：761＋761は素通し）。実機クライアントで2人同時に遊び、相手が見えるかどうかを確かめるのが残りの検証である（`docs/PLAYTEST-1.19.3.md`）。

なお、2人プレイの統合テストでは、**オフラインモードのバニラは Login Start の UUID を無視して、名前から決まるオフライン UUID を使う**ことが分かった（`UUID.nameUUIDFromBytes("OfflinePlayer:" + name)`）。Player Info や出現パケットの UUID を照合するときは、ログインUUIDとオフラインUUIDの両方で見ること。テストでは `waitForPlayerInfoAdd`／`waitForPlayerSpawn` が両方を受け付ける。

## 接続の流れ

### 761クライアント → 内部761サーバー

1. ハンドシェイク（761、状態2）。
2. Login Start（名前＋bool＋UUID）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x24、codec 付き）。
5. Spawn Position（0x4C）。チャンク（0x20）・Player Info（0x36）などが届く。

### 761クライアント → 内部763サーバー

1. ハンドシェイク（761、状態2）。
2. Login Start（名前＋bool＋UUID）。
3. Login Success。
4. すぐプレイ状態へ。Join Game（763形式 0x28、portalCooldown 付き）を761形式（0x24、portalCooldown を落とす）に変換する。codec は素通し。
5. Spawn Position（763形式 0x50→761形式 0x4C）。リソースパックは763形式 0x40で送り、変換で761の0x3cに付け替える（`pack761`、無ければ `pack762`、無ければ `pack763`）。

### 761クライアント → 内部774サーバー

1. ハンドシェイク（761、状態2）。
2. Login Start、Login Success。
3. プロキシが774の設定フェーズを消費する（Select Known Packs に空で答える、Registry Data を保持する、Finish Configuration に答える、Login Acknowledged を送る）。
4. 774の Join Game（0x30）を763形式（0x28）に直し、さらに761形式（0x24）に変換する。codec は763向けに合成したもの（dialog レジストリは合成しない）。
5. Spawn Position（774形式 0x5F→761形式 0x4C）。

### 763クライアント → 内部761サーバー

1. ハンドシェイク（763、状態2）。
2. Login Start（名前＋bool＋UUID）。
3. Login Success。
4. すぐプレイ状態へ。761サーバーの Join Game（0x24）を763形式（0x28）に変換する（portalCooldown=0 を補う）。codec は素通し。
5. Spawn Position（761形式 0x4C→763形式 0x50）。

## プレイ中の翻訳

- **チャンク**：ブロック状態（761=23,232／762=23,725／763=24,135）・バイオーム（761=38／762=38／763=39の plains）・ブロックエンティティ種別（suspicious_sand／decorated_pot は761向けに落とす）を付け替える。trustEdges は761・762の両方にあるため、762⇔761では付け外し不要。763との間では付ける／落とす。
- **スロット・ウィンドウ**：アイテムID（761=1,186／762=1,228／763=1,255）を付け替える。Window Click（761=0x0A／762=0x0B／763=0x0B）・Set Creative Slot（3バージョンとも0x2B）は変換する（落とさない）。
- **エンティティ**：エンティティ種別（player は761=117／762=122／763=122、falling_block は761=30／762=36／763=36、pig は761=68／762=72／763=72）を付け替える。762にしか無い display 系・sniffer・text_display は761クライアントではブタに見える。
- **音・パーティクル**：音（761=1,392／762=1,454／763=1,474）・パーティクル（761=93／762=96／763=95）の番号を付け替える。cherry_leaves 系の粒子は white_ash へ。
- **位置・Server Data**：上の表の通り。
- **Spawn Entity／Named Entity Spawn**：761サーバー→762以上クライアントは Named Entity Spawn を Spawn Entity（player種別）に直す。762以上サーバー→761クライアントは Spawn Entity（player種別）を Named Entity Spawn（0x02）に直す。761＋761は素通し。

## 送らない・落とすパケット（761向け）

- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応
  （既存の制限と同じ）。
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす。
  suspicious_sand・decorated_pot（762=38／39）は761向けに落とす。
- ダメージ種別が範囲外の場合：Damage Event をパケットごと落とす。
  Damage Event 自体が762専用のため、761クライアントには常に落とす。
- 762にだけある bundle_delimiter／chunk_biomes／damage_event／hurt_animation：
  761クライアントには落とす（bundle_delimiter は761の Spawn Entity（0x00）と
  IDが衝突するため、素通しは禁止）。
- 761に無いブロック状態・アイテム・音・エンティティ：近い既存のものに代替する（上の表を参照）。
  石・ブタ・plains への代替は範囲外だけ。
- 763→761で cherry_leaves（763=29）は falling_cherry_leaves（762=30）を経て
  white_ash（761=79）へ。762→761で dripping／falling／landing_cherry_leaves は
  すべて white_ash（761=79）へ。

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全196通り共通。手順書は
  `docs/PLAYTEST-1.19.3.md`。偽クライアント＋本物のバニラでの統合テストは
  5セル（761→761、761→762、761→763、762→761、763→761）で参加・スポーン・チャット・移動まで確認済み。
- **named_entity_spawn を本物の1.19.3が送るかは未確認**：上の節を参照。
  761クライアントが Spawn Entity（player種別）を受け入れるかどうかも、
  本物のクライアントでの確認が必要。
- **764以上のクライアントが761サーバーに参加するときの設定フェーズ合成**：
  761の codec に damage_type レジストリが無いため、合成する設定フェーズに
  damage_type が入らない。764以上のクライアントが受け入れるかは未確認。
- **Join Game codec の素通し**：762サーバーの codec（damage_type レジストリ付き）が
  761クライアントにそのまま届く。Mojang の codec は未知のレジストリを無視するはずだが未確認。
  774サーバーの codec（trim の追加フィールド、damage_type の `effects`、cherry_grove 入りの
  バイオーム一覧）も761クライアントにそのまま届く。同じく未確認。
- **762にしか無いエンティティ（display 系・sniffer・text_display）は761クライアントではブタに見える**：
  代替先がブタのため。スニッファーの卵・桜の木なども、761クライアントでは
  既存のブロック・アイテムに見える（上の表の代替先を参照）。
- **suspicious_sand・decorated_pot のブロックエンティティは761向けに落とす**：
  761に相当するものが無いため。ブロック自体は sand・flower_pot に代替されるが、
  中身（アイテム）は表示されない。
- **Declare Recipes の smithing 形式の違い**：761の旧式と762の legacy_smithing／smithing の
  分離は変換しない（全方向で未対応）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  761＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **外部モードで pack761 が無い場合**：761クライアントには762パック
  （pack_format 13）が出る。本物の1.19.3クライアントが format 13 のパックを
  適用するかは未確認。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrock→761**：`JavaBackendConnection` の serverProtocol 761 を762扱いにした
  （Login Start の bool、Login Success の設定フェーズなし、Join Game の
  portalCooldown=0、Player Info の表示名 JSON）。Bedrock 側の統合テストは
  まだ無い。Bedrock のインベントリは引き続き未対応。Bedrock クライアントから
  761サーバーを見たとき、ブロック状態は762の番号のまま765として通す
  （762と同じ近似。ブロックによっては違うブロックに見える）。

## 761のときに見つかった罠（後続の担当へ）

- **サーバー側のハンドラはクライアントのプロトコル番号でパケットを出す**：
  `handleServerPlayFrom763` などのサーバー側ハンドラは、送るパケットのIDを
  `PacketTranslator.translateId`／`toClientId` でクライアントのプロトコルに
  合わせて出す。761クライアント向けには761の番号（例：Chunk Data 0x20）で
  出してしまうが、呼び出し側の763→762→761変換は763形式の入力を前提とするため、
  761の番号のパケットは763の番号として誤読される（Chunk Data 0x20 が Open Horse
  Window と誤認され、Server Data 0x45 が Respawn と誤認されて落とされる）。
  対策は `clientProtocolForServerPackets()`（761クライアントには763を返す）を作り、
  サーバー→クライアントの送出はすべて763形式で行い、変換に任せること。
  対象は3つのIDヘルパー（`spawnPositionClientId`／`keepAliveClientId`／`pingClientId` の
  763Like 経路）、From763 の Respawn／Advancements／フォールスルー、
  `handleServerPlay765From764` の clientId、`handleServerPlayUp`、
  From766／768／769／770／771／772／773／774 の Keep Alive／Ping／Spawn Position
  （24箇所）。`handleServerPlay765From764` の双子のハンドラは、クライアントが
  常に765のため変更していない。760／759でも同じ罠に注意すること。
- **`PacketIds.isSupported()` はハードコード**：761を追加するときは
  `V1_19_3` を `isSupported()` にも入れること。入れ忘れると
  `PacketIds761Test` が4件失敗する。
- **oak_leaves の番号はバージョンごとに違う**：`oak_leaves[distance=1,persistent=false,waterlogged=false]` は
  761=217、762=236。テストの期待値を書くときは、使うバージョンの対応表で確認すること。
- **メニューの逆向きは改名を先に処理する**：761の smoker（21）→762の smoker（22）、
  cartography（22）→23、stonecutter（23）→24。identity ではない。
  生成器は `rename_first_rev` で改名を先に処理する。
- **ChunkTranslator の生成器は javadoc の境界を越えて削除する**：
  `gen-761-chunk.py` の delete_method の正規表現は `/**...*/` を越えて
  マッチするため、非貪欲なパターンに直すこと。760／759でも同じ生成器を使う場合は注意。
- **`ResourcePackMode.decide()` の引数ずれ（762の罠の再発）**：
  761の `(url761, sha1761)` 挿入で、762〜774のテストの `decide()` 呼び出しが
  1ペアずれる。オーバーロードの引数を増やすときは既存テストの呼び出しも
  全部ずらすこと（`null, null` を挿す）。`ResourcePackMode*Test.java` の
  59箇所をスクリプトで直した（8引数以上の呼び出しに `"", ""` を2番目の引数の後に挿す）。
- **`ResourcePackServer` の BuiltPack コンストラクタは上書きしない**：
  14パック用のコンストラクタを追加するとき、13パック用のコンストラクタを
  その場で書き換えると `ResourcePackServer762Test` が壊れる（引数の数が合わなくなる）。
  13パック用はそのまま残し、14パック用を別コンストラクタとして追加すること。
- **`ClientAdapter.differences()` の分岐の挿入位置**：`if (clientProtocol == ProtocolVersion.V1_19_4)` が
  3箇所あるため、`muse.edit` は最初の1件にだけ当たる。`decide()` に差分の分岐を
  入れてしまったことがある。挿入後はコンパイルとテストで確認すること。
- **ダメージ種別の仮定は検証で覆った**：当初は「761のダメージ種別は762の42件と
  同じ順番」と仮定していたが、本物の1.19.3の codec には damage_type レジストリ自体が
  無かった。仮定は検証で覆ることがあるため、対応表の前提は本物のサーバーで
  確かめること（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1193`）。
- **2人プレイの統合テストは偽クライアントの限界がある**：本物の1.19.3サーバーは
  偽クライアントには他プレイヤーの出現パケットも Player Info Update（ADD_PLAYER）も
  送ってこなかった（直接接続でもプロキシ経由でも同じ。Client Information と
  繰り返しの移動パケットを送っても変わらなかった。動物の Spawn Entity は届く）。
  2人プレイの統合テストは削除した。出現の見え方は実機クライアントで
  確かめること。なお、オフラインモードのバニラは Login Start の UUID を
  無視して名前から決まるオフライン UUID を使う場合があるため、Player Info や
  出現パケットの UUID を照合するときはログインUUIDとオフラインUUIDの両方で
  見ること（削除したテストでは両方で照合しても届かなかった）。

## Java 17（内部761サーバーを動かすとき）

1.19.3は **Java 17** で動く。Java 21 は不要。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.19.3は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.19.3` を指定すると、内部761サーバーが Java 17 で起動する。
バニラ1.19.3サーバーjarのSHA-1は `c9df48efed58511cdd0213c56b9013a7b5c9ac1f`
（piston-meta。47,162,712バイト。`run/vanilla/server-1.19.3.jar`）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.19.3.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  `PacketIds761Test`（8件）、`ProtocolVersion761Test`（3件）、
  `RegistryRemapper761Test`（15件）、`ChunkTranslator761Test`（28件）、
  `PlayTranslator761Test`（6件）、`ClientAdapter761Test`（11件）、
  `ClientJoinFlow761Test`（5件）、`ProxySession761Test`（20件。
  761サーバー→761クライアントの Spawn Entity／Named Entity Spawn の
  素通し2件を含む）、`ResourcePackBuilder761Test`（5件）、
  `ResourcePackMode761Test`（5件）、`ResourcePackServer761Test`（2件）。計108件。
- 配線テスト（`detergentmc-core`）：`ServerProtocolWiringTest`（1.19.3→761）、
  `ResourcePackWiringTest`（761パックの配信）、`AdapterDiscoveryTest`（V1193Adapter）、
  `JavaBackendConnection761Test`（3件）、`DatapackWiringTest`（1.19.3は pack_format 10）。
- 検証テスト（`detergentmc-core`）：`RegistryOrderCaptureTest.registryOrderOfRealVanilla1193`
  （本物の1.19.3の codec に damage_type が無いこと、バイオーム63件・plains=38・
  cherry_grove 無しを確認）。
- 統合テスト（`ProxyIntegrationTest`）：偽761クライアント→本物のバニラ1.19.3
  （761ネイティブ）、偽761クライアント→本物のバニラ1.19.4（762）、
  偽761クライアント→本物のバニラ1.20.1（763）、偽762クライアント→本物の
  バニラ1.19.3（761）、偽763クライアント→本物のバニラ1.19.3（761）。
  いずれも参加・スポーン・チャット・移動まで確認。2人プレイの統合テストは
  偽クライアントでは他プレイヤーのパケットが届かないため削除した
  （上の「named_entity_spawn」を参照）。
- 既存テストの更新：761を受け入れるようになったため、761拒否のテストは760に
  ずらした（`ClientAdapter.decide(761` → `decide(760` など）。
  対応プロトコル一覧の断言には761を先頭に追加した。
  `ResourcePackMode767Test`〜`ResourcePackMode774Test` の `decide()` 呼び出しは
  761用に `null, null` を挿した（59件）。
