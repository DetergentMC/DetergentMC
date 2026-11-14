# 26.1クライアント → 1.19系・1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
26.1クライアント（プロトコル775）は、内部プロトコルが
759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775のどのサーバーにも参加できます。内部775のときは
素通し（リレー）、それ以外は上向き・下向き変換です。

26.1は年号ベースの新バージョニングの最初のバージョンです（1.21.11の次が
26.1。「Tiny Takeover」、2026-03-24リリース、データバージョン4786）。
26.1.1（2026-04-01、データ4788）と26.1.2（2026-04-09、データ4790）も
プロトコル775を共有するため、同じアダプター（`detergentmc-adapter-v1_26_1`、
V1261Adapter／V12611Adapter／V12612Adapter）で扱います。
774との違いは大きく、**パケットIDは164件変更・5件新規・0削除**です
（773→774は0変更だったのと対照的）。レジストリはブロック状態+202件、
ブロック+2件（金のタンポポ）、アイテム+1件（金のタンポポ）、音+64件、
データコンポーネント+6件、パーティクル+2件、環境属性+3件、レシピ−1件、
スロット表示+3件です。エンティティ・バイオーム・属性・メニュー・
エンチャント・エフェクト・絵画・ジュークボックス・ダメージ種別・
ダイアログ・ブロックエンティティ種別は774とまったく同じです
（絵画・ダメージ種別・ダイアログ・ブロックエンティティは本物の26.1の
Join Game codec で確認済み。`RegistryOrderCaptureTest.registryOrderOfRealVanilla261`）。
774の仕組みは `docs/PROXY-1.21.11.md`、773は `docs/PROXY-1.21.9.md`、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。

全289通り（クライアント759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775 × サーバー759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775）の対応マトリクスは `docs/VERSIONS.md` の「26.1対応：
全289通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の17つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 774→775の差分（要点）

| 分野 | 774→775の差分 | 扱い |
| --- | --- | --- |
| パケットID（クライアント行き・プレイ） | **100変更・2追加・0削除**。プレイ 142→144件。新規は `game_rule_values` 0x27、`low_disk_space_warning` 0x32。0x27と0x32への挿入でそれ以降がずれる。Join Game 0x30→0x31、Chunk Data 0x2C→0x2D、Keep Alive 0x2B→0x2C、Ping 0x3B→0x3D、Respawn 0x50→0x52、Add Resource Pack 0x4F→0x51、Spawn Position 0x5F→0x61、Player Info Update 0x44→0x46、Tracked Waypoint 0x88→0x8A、Show Dialog 0x8A→0x8C、Clear Dialog 0x89→0x8B、Update Time 0x6F→0x71、Disconnect 0x20 は変わらない（`mc-26.1-protocol.json` と `PacketIds` の775列で確認） | `PacketTranslator.translateId` で番号を付け替える。`ProxySession` の From775／To775 は生成器が772時代のIDのまま残していた箇所を監査で直した（下の「775パッチを写すときの罠」を参照） |
| パケットID（サーバー行き・プレイ） | **65変更・3追加・0削除**。プレイ 65→68件。新規は `attack` 0x01、`set_game_rule` 0x39、`spectate_entity` 0x3E。0x01への挿入で全て+1、0x39と0x3Eの後でさらに+1。Chat Message 0x08→0x09、Chat Command Signed 0x07→0x08、Window Click 0x11→0x12、Set Creative Slot 0x37→0x38、Vehicle Move 0x21→0x22、Keep Alive 応答 0x1B→0x1C、Ping 応答 0x2C→0x2D、Set Player Position 0x1D→0x1E、Use Entity 0x19→0x1A、Enchant Item 0x10→0x11、Client Command 0x0B→0x0C、Ping Request 0x25→0x26 | 同上。Use Entity は攻撃が `attack` 0x01 に分離されたため、774の Use Entity（mouse==1）は 775 の Attack に、775 の Attack は 774 の Use Entity（mouse==1）に変換する（`PlayTranslator.useEntityAttackTo775`／`translateAttack775ToUseEntity774`）。775 の Attack には sneaking 情報が無いため 774 向けは sneaking=false で送る |
| パケットID（設定・ログイン・状態・ハンドシェイク） | **0変更**。設定フェーズの新規パケットは無い。Registry Data 0x07、Finish Configuration 0x03、Add Resource Pack 0x09、Select Known Packs 0x0E、Code of Conduct 0x13 は774と同じ | 番号付け替えは不要。設定フェーズの辞書は766形式で作り、774向けに8段、775向けに9段並べ替える |
| 新規パケット | **5件**。`game_rule_values`（クライアント行き 0x27）、`low_disk_space_warning`（クライアント行き 0x32、ペイロード無し）、`attack`（サーバー行き 0x01）、`set_game_rule`（サーバー行き 0x39）、`spectate_entity`（サーバー行き 0x3E）。`ping_request`（サーバー行き 0x26）は新規ではなく番号変更（774は 0x25）。レガシーサーバーリスト ping（0xFE）は従来通り表の外 | `game_rule_values`／`set_game_rule`（GameRule）は774に相当が無いため落とす。`low_disk_space_warning` も落とす。`spectate_entity` も落とす。`attack` だけ Use Entity と相互変換する |
| 削除パケット | **0件** | — |
| Login Success | 774と同じく末尾の `strictErrorHandling` bool が無い（765と同じ形式） | そのまま通す。767向けは `false` を付け足す（774と同じ） |
| Join Game／Respawn | 774と775で形式は同じ（LoginData／SpawnInfo。Respawn は末尾の `seaLevel` varint を含む）。番号だけ変わる（Join Game 0x30→0x31、Respawn 0x50→0x52） | 番号だけ付け替える。767向けは seaLevel（63）を落とす（774と同じ） |
| Spawn Position | 774と775とも RespawnData（次元名の文字列＋位置＋yaw＋pitch）形式 | そのまま通す。Bedrock 側の読み取りも RespawnData（774と同じ） |
| Player Chat（クライアント行き） | 先頭に `globalIndex` varint があるのは774・775とも同じ | 素通しする（774→775・775→774とも globalIndex を付けも剥がしもしない） |
| Chat Message／Chat Command Signed（サーバー行き） | 末尾の `checksum` u8 があるのは774・775とも同じ。774の checksum は i8、775は u8 と型名だけ変わるが線形は同一 | 剥がしも付け足しも不要。775の checksum 0x00 は実バニラ26.1に受け入れられることを統合テストで確認済み（`ProxyIntegrationTest.fake775ClientJoinsRealVanilla261ThroughProxy`） |
| Update Time | **作り直し**。774は {time i64、tickDayTime bool、age i64}、775は {clockUpdates array[{id varint、totalTicks varlong、partialTick f32、rate f32}]}。775にも `age` i64 がある（最初の実装で「age を落とす」としたのは誤りで直してある） | `translateUpdateTime774To775`／`775To774` で変換する。time は id 0 の時計の totalTicks、tickDayTime は rate 1.0／0.0。age はそのまま通す。clock id の意味と rate の単位は未検証（javadoc に明記） |
| Use Entity | **作り直し**。774は {target varint、mouse varint、x／y／z（mouse==2 のとき）、hand（mouse==0／2 のとき）}、775は {target varint、hand mapper varint、location lpVec3、sneaking bool}。攻撃は別パケット `attack` 0x01 に分離 | `translateUseEntity774To775`／`775To774` で変換する。774の mouse==1（攻撃）は 775 の Attack に分離する。775→774で mouse==0 の location は (0, 0, 0) で補う（仮定。javadoc に明記） |
| Enchant Item | enchantment が 774の i8 から 775の varint に戻った（windowId は両方 varint） | `translateEnchantItem774To775`／`775To774` で変換する。下向きは i8 の範囲外ならそのまま通す |
| Client Command | 新しい action 2 = request_gamerule_values が追加された | action 2 は774に相当が無いため落とす（`isClientCommandRequestGameRuleValues775` で判定）。action 0／1 はそのまま通す |
| Advancements | アイコンが 774の Slot から 775の ItemStackTemplate（{itemId varint、itemCount varint、addedComponentCount varint、removedComponentCount varint、components、removeComponents}）に変わった | **変換せず落とす**（774↔773と同じ方針。アイコンを誤読して通すより落とす方が安全） |
| SlotDisplay | 8→11件。with_any_potion（2）、only_with_component（3）、dyed_slot_demo（7）が挿入された（minecraft-data は dyed_slot_demo、ViaVersion は dyed と呼ぶ） | KIND 表で変換する。ダイアログ／レシピの中の SlotDisplay は素通しのため、775の新規3種は古いクライアントに未変換で届く（制限として記録） |
| スロット（データコンポーネント） | 種別は104→110件。**6件の新規挿入**（additional_trade_cost 41、dye 43、pig／sound_variant 94、cow／sound_variant 96、chicken／sound_variant 98、cat／sound_variant 106）のため KIND 表で付け替える | KIND 表で変換する。775の新規6種は774向けに落とす |
| Entity Metadata | 型番号は39→43件。cat_sound_variant（22）、cow_sound_variant（24）、pig_sound_variant（29）、chicken_sound_variant（31）が挿入され、旧22→23、23→25、24→26、25→27、26→28、27→30、28（zombie_nautilus_variant）→32、29–38→33–42 にずれる | 型番号を付け替える。775にしかない4種はエントリごと落とす |
| World Particles | 115→117件。pause_mob_growth（50）、reset_mob_growth（51）が挿入され、旧50以降が+2ずれる | 対応表で変換する。新規2種は774向けに entity_effect（21）になる |
| ブロック状態 | 29,671→29,873件（+202）。200件は音符ブロックのトランペット楽器の状態（1381–1580）、残り2件は金のタンポポ（2322）と金のタンポポの鉢（10642） | 対応表で変換する。775にしかない202件は774向けに石（1）になる |
| ブロック | 1,166→1,168件（+2）。golden_dandelion（158）、potted_golden_dandelion（424） | 対応表で変換する |
| アイテム | 1,505→1,506件（+1）。golden_dandelion（230）で、それ以降が+1ずれる | 対応表で変換する。775にしかない1件は774向けに石（1）になる |
| エンティティ | 157件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 音 | 1,838→1,902件（+64）。baby_cat 9、cat_royal 8、baby_chicken 4、chicken_picky 3、cow_moody 4、金のタンポポ use／unuse 2、baby_horse 8、音符ブロックのトランペット×4、baby_pig 5、pig.eat 1、pig_mini 4、pig_big 4、baby_wolf 7。block.stone.break は774の1535→775の1592（+57） | 対応表で変換する。775にしかない64件は774向けに block.stone.break になる |
| 環境属性 | 45→48件。visual／block_light_tint（17）、visual／night_vision_color（20）、visual／ambient_light_color（21）が挿入 | 対応表で変換する |
| レシピ | 22→21件。crafting_special_armordye／crafting_special_mapcloning／crafting_special_tippedarrow が削除され、crafting_dye／crafting_imbue が追加。9件が移動 | 対応表で変換する |
| エンチャント | 43件で同じ顔ぶれ・同じ順番（lunge=21） | 変換なし（identity） |
| エフェクト | 40件で同じ顔ぶれ・同じ順番（BreathOfTheNautilus=39） | 変換なし（identity） |
| ダメージ種別 | 50件で同じ顔ぶれ・同じ順番（spear=37）。本物の26.1の codec で確認済み | 変換なし（identity） |
| 絵画 | 51件で同じ顔ぶれ・同じ順番。本物の26.1の codec で確認済み | 変換なし（identity） |
| ジュークボックスの歌 | 21件で同じ顔ぶれ・同じ順番。本物の26.1の codec で確認済み | 変換なし（identity） |
| バイオーム | 65件で同じ顔ぶれ・同じ順番。本物の26.1の codec で確認済み | 変換なし（identity） |
| 属性 | 35件で同じ顔ぶれ・同じ順番。本物の26.1の codec で確認済み | 変換なし（identity） |
| ブロックエンティティ種別 | 49件で同じ顔ぶれ・同じ順番（shelf=40、copper_golem_statue=48）。本物の26.1の codec で確認済み | 変換なし（identity） |
| 楽器（ヤギの角） | 8件で同じ顔ぶれ・同じ順番。本物の26.1の codec で確認済み。なお minecraft-data の instruments.json にあるトランペット4件は音符ブロックの楽器で、こことは別物 | 変換なし（identity） |
| ダイアログ | 3件で同じ顔ぶれ・同じ順番。本物の26.1の codec で確認済み | 変換なし（identity） |
| メニュー | 25件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 統計 | 77件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| Heightmap（Chunk Data） | 775も直接の配列（varint 個数＋{type varint、data long[]}） | 774と同じ読み方で通す |
| チャンクセクションの Data Array（Chunk Data） | **775も同じ**（長さの varint 接頭辞なし。長さは bitsPerEntry とエントリ数から計算する） | 774と同じ読み方で通す |
| Window Click | HashedSlot のまま（774と同じ） | **変換できず落とす**（774と同じ。ハッシュ算法が未検証のため）。774⇔775でも落とす |
| Set Creative Slot | UntrustedSlot のまま（774と同じ） | `ChunkTranslator.translateSetCreativeSlot774To775`／`775To774` で変換する |
| リソースパック | pack_format はリソース84、データ101（26.1サーバーjar内の version.json の pack resource 84.0／data 101.1 による）。26.1.1／26.1.2も同じ。pack.mcmeta は773／774と同じ整数形式（`{"pack":{"pack_format":84}}`）。1.21.9以降のバニラパックにある min_format／max_format の [84, 0] 形式は、バージョン範囲を宣言するバニラパックのもので、DetergentMCの自作パックには要らない | `detergentmc-pack-26.1.zip`（17つ目）。`resource-pack.url-775`／`sha1-775` で出し分ける |
| バニラjar | 26.1 の SHA-1 は `3872a7f07a1a595e651aef8b058dfc2bb3772f46`（60,417,588バイト、piston-meta）。26.1.1 は `49c8195703ad0ba4f0a4efbccfd85a4a8ca57431`、26.1.2 は `97ccd4c0ed3f81bbb7bfacddd1090b0c56f9bc51` | piston-meta で確認済み。`run/vanilla/server-26.1.jar` 等に置く |

## 接続の流れ

774と同じ（設定フェーズあり）。ハンドシェイク→ログイン（Login Start／Login Success／Login Acknowledged）→設定フェーズ（Registry Data／Select Known Packs／Finish Configuration）→プレイ。775クライアントは設定フェーズで Code of Conduct（0x13）が来たら Accept（0x09、空）を返す。ネイティブ 775＋775 ではプロキシが Select Known Packs をそのまま中継するため、答えないと本物の26.1サーバーが先に進まない。

内部775サーバーは Java 25 が要る（下の「Java 25」を参照）。プロキシ認証は
既存の仕組み（プロキシが Mojang の hasJoined を済ませ、内部バニラは
offline-mode の信頼済み接続）を使う。

## プレイ中の翻訳

774→775は一段、775→773は775→774→773の二段、775→763は775→774→773→772→771→770→769→768→767→766→765→764→763の十二段で変換する。逆向きも同じ段数。Join Game／Respawn／Disconnect／Spawn Position／Keep Alive／Ping は `ProxySession` の From775／From774 ハンドラで番号を付け替え、スロット・チャンク・エンティティ・メタデータ等は `ChunkTranslator`／`PlayTranslator` の 775 表で変換する。

## 送らない・落とすパケット（775向け）

- Advancements（774→775・775→774とも。アイコンが Slot から ItemStackTemplate に変わったため）
- GameRule パケット（`game_rule_values` 0x27／`set_game_rule` 0x39。774に相当が無いため）
- `low_disk_space_warning` 0x32（774に相当が無いため）
- `spectate_entity` 0x3E（774に相当が無いため）
- Client Command の action 2（request_gamerule_values。774に相当が無いため）
- Window Click（HashedSlot のため。774と同じ）
- Declare Recipes／Statistics／Tags／World Event ID（全方向で未実装のため落とす。774と同じ）
- 775にしかない Entity Metadata の型（cat／cow／pig／chicken の sound_variant）はエントリごと落とす
- 775にしかないデータコンポーネント6種は落とす

## 対象外・未実装（正直な一覧）

- 本物のゲームクライアントでの確認は未実施（全バージョン共通）。偽プロトコルクライアント＋本物のバニラでの統合テストのみ。
- 775の Attack パケットには sneaking 情報が無いため、775→774の攻撃変換は sneaking=false で送る。
- Update Time の clock id の意味と rate の単位は未検証。
- Use Entity の mouse==0 の location は (0, 0, 0) で補う（仮定）。
- SlotDisplay はダイアログ／レシピの中では素通しのため、775の新規3種（with_any_potion／only_with_component／dyed_slot_demo）は古いクライアントに未変換で届く。
- Entity Effect の BreathOfTheNautilus（39）は774→773で素通しされる（774と同じ制限）。
- Registry Data の entity_type は並べ替えの対象外のため、774サーバーから来た157件（pig=100）のまま775クライアントに届く（774と同じ制限。775も157件で同一のため実害は無い）。
- チャットの署名チェーンはバージョン境界を越えられない（全バージョン共通の制限）。
- `handleClientPlayTo773` はクライアント→サーバー方向のチャット checksum が無い（774で直したものと同じ欠落が773に残っている。775の範囲外）。
- 773節の `translateComponent772To773`／`773To772` の case 16 は repair_cost（varint）を enchantment_glint_override（bool）と取り違えている（774節では直してある。775の範囲外）。
- ENTITY_EFFECT_PARTICLE_773（=20）は誤りで、正しくは21（entity_effect）。775の作業で 774 の方（20→21）は直したが、773の方は775の範囲外のため直していない。775の新規粒子2種のフォールバックは正しい21を使っている。

## Java 25（内部775サーバーを動かすとき）

26.1／26.1.1／26.1.2のバニラサーバーは Java 25 が要る。DetergentMC自体は
Java 17 で動く。`VanillaServerHost.javaBinaryFor` は 26.1／26.1.1／26.1.2 のとき
Java 25 を選ぶ。探す順序は `detergentmc.java25.home` システムプロパティ→
`DETERGENTMC_JAVA25_HOME` 環境変数→現在の `java.home`（Java 25 以上のとき）。
この環境では Temurin 25.0.5+8 を `~/workspace/.tools/jdk-25.0.5+8/` に置いてある。
統合テストは `Java25TestSupport.ensureJava25Home()` が同じ場所を自動で見つける。

```sh
DETERGENTMC_JAVA25_HOME=$HOME/workspace/.tools/jdk-25.0.5+8 \
JAVA_HOME=$HOME/workspace/.tools/jdk-17.0.21+9 \
./gradlew :detergentmc-core:test --tests "*ProxyIntegrationTest.fake775*"
```

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-26.1.md` を参照。

## テスト

- `detergentmc-protocol`：2,231件・0失敗（`PacketIds775Test` 7件、`ProtocolVersion775Test` 3件、`ResourcePackBuilder775Test`、`ResourcePackMode775Test`、`ResourcePackServer775Test` を含む）。`verify-775-tables.py` は422件のチェックがすべて合格。
- `detergentmc-bedrock`：282件・0失敗・2スキップ（UDP基準）。新規の `JavaBackendConnection775Test` は14件・0失敗。
- `detergentmc-core`（配線）：100件・0失敗（`AdapterDiscoveryTest`／`BedrockWiringTest`／`DatapackWiringTest`／`ResourcePackExternalTest`／`ResourcePackWiringTest`／`ServerProtocolWiringTest`／`DatapackGeneratorTest`）。
- 統合テスト（`ProxyIntegrationTest`）：偽775クライアント→本物のバニラ26.1／1.21.11／1.21.10／1.20.1、偽774クライアント→本物のバニラ26.1の5セルが全て成功。`RegistryOrderCaptureTest.registryOrderOfRealVanilla261` も成功（本物の26.1の codec で小レジストリの並びを確認）。
- 分割検証のXMLは `split-verification-775/` にある（フルクリーンビルドは使わないこと。環境の再起動で殺されるため）。

## 775パッチを写すときの罠

774の `docs/PROXY-1.21.11.md` にある罠に加え、775で新たに見つかったもの。
次のバージョン（776）をやるときはここから始めること。

### 1. 生成器の shift() は if-chain の末尾を落とす

775の ProxySession パッチ（`gen-775-proxysession.py`）は、From*／JoinGame の
if-chain を「1段ずらす」形で書き換えた際に、末尾の `is773()` 分岐を落とした。
落としたのは9箇所：`handleServerJoinGame770／771／772` の is773() 分岐（Join Game 0x30／AddPack 0x4F）、
`handleServerPlayFrom768／769／770／772／773` の Respawn の is773() 分岐（0x50）、
`sendSynthesizedConfigIfReady` の is773() 七段並べ替え。
773クライアントが768／769／770／772サーバーにいると Respawn が765形式の 0x45 で送られ、
`ProxySession773Test` の2件が失敗して発覚した（期待 0x50、実際 0x45）。
JoinGame の方は汎用ハンドラへの迂回で今は通っていたが、欠落自体が誤り。
`detergentmc-protocol/tools/fix-proxysession-775-respawn.py` で9箇所を復元し、
あわせて From774 の Respawn に無かった is775() 分岐（0x52）も追加した（パッチの追加漏れ）。
**対策**：パッチ適用後に `4b51d25`（前バージョン完了時）と `isXXX()` の出現回数を
メソッドごとに数え比べること（`branch-diff-all.py` の要領）。数が減っている
メソッドは全て欠落の疑いがある。

### 2. パケットIDは shift() ではずれない

774はパケットIDが0変更だったため、生成器は `0x..` リテラルを shift しない。
775は164件変わるため、From775／To775 の dispatch チェックと送出IDは
`packet-ids-775.json` と突き合わせて手で直す必要があった。
監査で見つけた漏れ：To775 の Set Creative Slot が 0x37 のままだった（正しくは 0x38）、
From775 の Respawn が is774 分岐しか無かった、From775 の Disconnect が is770 を
0x1D 組に入れていた。`detergentmc-protocol/tools/fix-patch-775-ids.py` で直した。
**対策**：パッチ適用前に、生成されたパッチの A／B リスト（From775 の dispatch チェック、
To775 の送出ID）を `packet-ids-775.json` と1件ずつ突き合わせること。

### 3. 粒子フォールバックの off-by-one（773／774の旧バグ）

`ENTITY_EFFECT_PARTICLE_773`／`774` は20だったが、entity_effect は773以降
21である（20は end_rod）。774には新規粒子が無かったためフォールバックが
当たらず、バグが表面化しなかった。775の新規粒子2種（pause_mob_growth 50、
reset_mob_growth 51）で初めて当たるようになり、合成表（21）とオラクル（20）が
食い違って `verify-775-tables.py` の D 項が11件失敗した。
`ENTITY_EFFECT_PARTICLE_774` を20→21に直し、`UP_775_FALLBACKS` の particles も
20→21に直した。773の方（20）は775の範囲外のため直していない（上の「対象外」を参照）。

### 4. ComposedUp は 64KB の `<clinit>` 制限に当たる

`RegistryTables775ComposedUp.java`（154表・1,073行）は `<clinit>` が64KBを
超えて "code too large" でコンパイルできなかった（774の140表・940行は入った）。
`detergentmc-protocol/tools/split-775-composed-up.py` で A（763–768、84表）と
B（769–773、70表）に分割した。次も表が増えるなら分割が要る。

### 5. `packet-ids-775.py` の終端子バグ

パッチスクリプトの replace 関数が終端子に `m.group(19)`（16番目のID）を使っていたが、
正しくは `m.group(20)` で、全262行が `..., 0x40)0x3d` のように壊れた。
`fix-packet-ids-775-tail.py` で直した。16列（759–774）＋775列＝17引数のため、
グループ番号は1つずれている。次に列を増やすときは終端子の番号を確認すること。

### 6. ClientAdapter の pack_format に 774 が無かった

`pack_format` の三項演算子に V1_21_11 → "75" が無く、774クライアントが "32"（766の値）を
もらっていた。775の作業で V26_1 → "84" を足すときに一緒に直した。
**対策**：新しいプロトコルを追加するときは、前のプロトコルの値が入っているかも確認すること。

### 7. 位置引数オーバーロードの無言の束縛替え（最も危険）

`ResourcePackMode.decide()` と `ResourcePackServer` のコンストラクタは
全String／全BuiltPackの位置引数だけのオーバーロード群である。
引数を1組増やすと、古い呼び出しがコンパイルエラーを出さずに別オーバーロードへ
無言で束縛され直す（url-760 の値が url-775 に、url-759 の値が url-760 に入った）。
`ResourcePackMode759Test`／`760Test` の8件が失敗して発覚した。
`detergentmc-protocol/tools/fix-775-mode-tests.py` でテスト側に空ペアを挿入して直した。
**対策**：引数を増やしたら、全ての呼び出し側の実引数の意味を1件ずつ確認すること。
コンパイラは助けてくれない。値がスロットごとに違うテスト（URL／SHA-1を
バージョンごとに変える）だけがこの罠を検出できる。

### 8. 生成器の中間ファイルが無いと再生成できない

`detergentmc-protocol/build/remap-data/` の中間ファイル（maps773.json 等）は
build ディレクトリの clean で消える。775の作業開始時に無くなっていたため、
`RegistryTables774*.java` の RANGES／OVERRIDES リテラルから maps774.json を
再構成した（`/tmp/mc775/reconstruct-774.py`）。中間ファイルはコミットされないため、
次のバージョンでも同じ手間が要る。生成に必要な入力（ViaVersion の mapping JSON と
minecraft-data の各 JSON）は `detergentmc-protocol/build/remap-data/` に残っているが、
build ディレクトリは clean で消えることに注意。

### 9. `/tmp` は再起動で消える

環境の再起動で `/tmp/mc775/`（生成器のバックアップ・検証スクリプト・
中間ファイル）が消えた。生き残ったのはリポジトリ内の tools／と
`~/workspace/tmp775/` に移したものだけ。作業中のスクリプトと中間ファイルは
リポジトリの tools／か `~/workspace/` 配下に置くこと。

### 10. BitStorage は long の境界をまたいで連続して詰める（全バージョンの旧バグ）

旧式の `perLong = 64 / bits; expected = (entryCount + perLong - 1) / perLong` は
bits が 64 を割り切らないとき（3・5・6・7・9…）に long 数を多めに数える。
間接パレットでは Data Array を読みすぎて後続のコンテナがずれて読めなくなり、
直接パレットではまたぐエントリの上位ビットを落として誤った値を返す。
発覚：Bedrockマトリクス26.1で、3 ビットのバイオーム（正しくは 3 long、旧式は
4 long 読んだ）が `パレット長が不正です: 17` で失敗した。実バニラ26.1のチャンクが
初めてこの条件を踏んだ。単体テストの `TestJavaChunk` も同じ旧式で詰めていたため、
単体テストだけでは見つからなかった（作る側と読む側が同じ誤りを共有していた）。
修正：`BedrockChunkTranslator.readPalettedContainer`（long 数＋unpack）、
`ChunkTranslator` の long 数計算 34箇所＋`remapDirectEntries*` 22個
（unpack／repack とも）、`TestJavaChunk.writePalettedContainer`、
`gen-775-translators.py` のテンプレート 4箇所。15 ビットの直接パレットでは
4096 エントリ中 767 個が long をまたぐ。
テスト：`ChunkTranslator775Test`（4件。間接 5 ビット／3 ビット、直接 15 ビット。
旧コードで4件とも失敗、新コードで成功することを確認）。既存の
`chunkDataDirectPalette` 3件（763／764／Up764）はテストデータ自体が
1024 long（旧式）だったため 960 long に直した。
**対策**：チャンクのテストデータは実バニラの詰め方（連続詰め）で作ること。
Data Array の long 数は `(entryCount * bits + 63) / 64` で数えること。
生成器の shift 連鎖（`gen-774-translators.py` 等）にも同じ旧式が残っているため、
次のバージョンで生成し直すときはこの修正を再適用すること。

### 11. handleClientConfiguration768 は 774／775 サーバーへの Settings を 8 フィールドに削っていた

769〜775 クライアントの設定フェーズは `handleClientConfiguration768` 経由で
767 形式に落とすが、最初の条件 `!isServer768() && ... && !isServer773()` に
774／775 が無く、774／775 サーバー向けでも 9 フィールドの Settings
（particleStatus 付き）が 8 フィールドに削られていた。774／775 のバニラは
Client Information のデコードに失敗して切断する。
発覚：偽775→実バニラ1.21.11 と偽774→実バニラ26.1 が「Join Game が届かない」で
失敗し、デバッグダンプで Settings が 13 バイト（8 フィールド）になっているのを
確認した。769〜773 クライアントが 774／775 サーバーに入るときも同じ欠陥だった。
修正：`ProxySession.handleClientConfiguration768` の2条件に is774()／is775() を
追加（+7/−4）。再実行で5件とも成功。
**対策**：設定フェーズの条件リストは「新しいプロトコルを追加するときは末尾も
確認すること」。この種の欠陥は単体テストでは見つからず、統合テスト
（偽クライアント→実バニラ）だけが検出できる。
