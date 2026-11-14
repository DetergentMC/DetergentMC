# 1.21.9／1.21.10クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.21.9／1.21.10クライアント（プロトコル773）は、内部プロトコルが
763／764／765／766／767／768／769／770／771／772／773のどのサーバーにも参加できます。内部773のときは
素通し（リレー）、それ以外は上向き・下向き変換です。772との違いは大きく、
**プレイのクライアント行きパケットIDが108件変わり（デバッグ系パケットの挿入で
0x1A以降+4、0x27以降+5）、レジストリはブロック状態+1,725件、アイテム+72件、
エンティティ+2件、音+43件**です。設定フェーズ・ログインのIDは772とほぼ同じで、
新しく Code of Conduct（設定 0x13）／Accept Code of Conduct（設定 0x09）が
増えました。チャンクの形式・チャットの checksum・Player Chat の globalIndex・
スロットの成分種別・属性表・エンチャント表・メニュー表は772とまったく同じです。
いちばんの変化は **銅の時代（The Copper Age）の新規レジストリ**
（棚・銅の道具と防具・銅ゴーレム・銅のチェスト・銅の鎖と `chain`→`iron_chain` の改名）と、
**Code of Conduct を既定のサーバーは送ってこないことの生ソケット確認**
（下の「Code of Conduct」）です。
772の仕組みは `docs/PROXY-1.21.7.md`、771は `docs/PROXY-1.21.6.md`、770は
`docs/PROXY-1.21.5.md`、769は `docs/PROXY-1.21.4.md`、768は `docs/PROXY-1.21.2.md`、767は
`docs/PROXY-1.21.md`、766は `docs/PROXY-1.20.5.md`、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。

全121通り（クライアント763／764／765／766／767／768／769／770／771／772／773 × サーバー763／764／765／
766／767／768／769／770／771／772／773）の対応マトリクスは `docs/VERSIONS.md` の「1.21.9対応：
全121通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用・767用・768用・769用・770用・771用・772用・773用の11つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 772→773の差分（要点）

| 分野 | 772→773の差分 | 扱い |
| --- | --- | --- |
| パケットID（クライアント行き・プレイ） | **108件変更**。すべてPLAY／CLIENTBOUND。デバッグ系パケットの挿入で 0x1A以降+4、0x27以降+5。Join Game 0x2B→0x30、Chunk Data 0x27→0x2C、Keep Alive 0x26→0x2B、Ping 0x36→0x3B、Start Configuration 0x6F→0x74、Disconnect 0x1C→0x20、Respawn 0x4B→0x50、Add Resource Pack 0x4A→0x4F、Spawn Position 0x5A→0x5F、Player Info Update 0x3F→0x44、Tracked Waypoint 0x83→0x88、Show Dialog 0x85→0x8A、Clear Dialog 0x84→0x89 | 番号付け替えが必要。`PacketIds` の773列（全247エントリ）で確認。`ProxySession` の773分岐は、版の条件だけ変えてIDを772のまま残す罠があるため、送出IDを773の値に直すこと（下の「773パッチを写すときの罠」を参照） |
| パケットID（サーバー行き・プレイ） | **変わらない**（削除されたdebug_sample_subscriptionを除く）。Chat Message 0x08、Chat Command Signed 0x07、Window Click 0x11、Set Creative Slot 0x37、Vehicle Move 0x21、Keep Alive 応答 0x1B、Ping 応答 0x2C、Set Player Position 0x1D、Player Loaded 0x2B、Change Gamemode 0x04、Custom Click Action 0x41も同じ | 番号付け替えは不要。汎用の `PacketTranslator.translateId` は同IDをそのまま通す。773同士は素通し |
| 新規パケット | **8件**。設定の Code of Conduct（クライアント行き 0x13）／Accept Code of Conduct（サーバー行き 0x09）と、プレイの DEBUG_BLOCK_VALUE（0x1a）／DEBUG_CHUNK_VALUE（0x1b）／DEBUG_ENTITY_VALUE（0x1c）／DEBUG_EVENT（0x1d）／GAME_TEST_HIGHLIGHT_POS（0x27）／DEBUG_SUBSCRIPTION_REQUEST（サーバー行き 0x16） | Code of Conduct は下の節を参照。デバッグ系パケットは旧クライアント向けに落とす（ping_request と同じ方針） |
| 削除パケット | **1件**。PLAY_DEBUG_SAMPLE_SUBSCRIPTION（772=0x16。773の0x16は別構造のdebug_subscription_requestなので改名ではなく別エントリ） | 773の0x16は古いクライアント向けに落とす |
| Login Success | 772と同じく末尾の `strictErrorHandling` bool が無い（765と同じ形式） | そのまま通す。767向けは `false` を付け足す（772と同じ） |
| Join Game／Respawn | 772と773で形式は同じ（SpawnInfo の末尾の `seaLevel` varint を含む）。番号だけ変わる（Join Game 0x2B→0x30、Respawn 0x4B→0x50） | 番号を付け替える。767向けは seaLevel（63）を落とす（772と同じ） |
| Player Chat（クライアント行き） | 先頭に `globalIndex` varint があるのは772・773とも同じ | 素通しする（772→773・773→772とも globalIndex を付けも剥がしもしない）。カウンタは `ProxySession.chatGlobalIndex` を772／773で共用する（セッションごと0から数える） |
| Chat Message／Chat Command Signed（サーバー行き） | 末尾の `checksum` u8 があるのは772・773とも同じ | 剥がしも付け足しも不要。772→773・773→772とも番号は同じ（Chat Message 0x08）。**773の checksum 0x00 は実バニラ1.21.9／1.21.10に受け入れられることを統合テストで確認済み**（`ProxyIntegrationTest.fake773ClientJoinsRealVanilla1219ThroughProxy`／`fake773ClientJoinsRealVanilla12110ThroughProxy`） |
| Heightmap（Chunk Data） | 772と同じ直接の配列（varint 個数＋{type varint、data long[]}）。type id の順序は772と同じと仮定する（0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、3=OCEAN_FLOOR、4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES） | 772・773とも直接配列のまま写す（NBT↔配列の変換は772⇔773では不要）。`ChunkTranslator.translateChunkData772To773`／`773To772` |
| チャンクセクションの Data Array（Chunk Data） | **772と同じ**（長さの varint 接頭辞なし。長さは bitsPerEntry とエントリ数から計算する）。シングルパレット（bpe=0）も長さ varint（0）が無い | 772・773とも同じ読み方で通す |
| Window Click | HashedSlot のまま（772と同じ） | **変換できず落とす**（772と同じ。ハッシュ算法が未検証のため）。772⇔773でも落とす |
| Set Creative Slot | UntrustedSlot のまま（772と同じ）。アイテムID・成分種別の付け替えだけで変換できる（`{itemCount varint、itemId、addedComponentCount、removedComponentCount、components[{type、data ByteArray}]、removeComponents[{type}]}`） | `ChunkTranslator.translateSetCreativeSlot772To773`／`773To772` で変換する。772・773とも `tooltip_display` は種別15で同一のため特別処理は不要。銅の道具・防具（773の新規アイテム）は772向けに石（1）になる |
| スロット（データコンポーネント） | 種別は772・773とも96件・同じ番号のため、種別番号の対応表は恒等表。`unbreakable` は772・773とも void、`dyed_color` は772・773とも色 i32 のみ、`enchantments`／`attribute_modifiers`／`trim`／`tool`／`instrument`／`jukebox_playable` は772・773で同じ形式 | 種別番号は恒等表で変換する。成分の中身は対応表で番号を直す（アイテム・エンチャント・属性・音・ジュークボックスの歌）。773にしかない種別は無い（96件とも両方にある）ため、成分を落とすことは無い |
| Entity Metadata | 種別番号は772・773とも同じ（23=cow_variant、24=wolf_variant（素の varint）、25=wolf_sound_variant、26=frog_variant、27=pig_variant、28=chicken_variant、29=optional_global_pos、30=painting_variant、31=sniffer_state、32=armadillo_state、33=vector3、34=quaternion）。wolf_variant は772・773ともただの varint（Holderではない）ため素通し。エンティティ種別は772→773で151→153件（copper_golem 27、mannequin 82） | 種別番号は恒等。エンティティ種別だけ対応表で直す。**銅ゴーレム（27）／マネキン（82）は772に無いため、773→772ではブタ（95）に落とす**。happy_ghast は772の56→773の57 |
| World Particles | 114→115件。`copper_fire_flame` が773の5番に挿入（5番以降が+1）。粒子データの case 番号は773側のIDで書く（`translateParticleData773To772`）。pale_oak_leaves（773の35）はデータ無しの粒子 | 対応表で変換する。773→772で copper_fire_flame（5）は落とす（772に無いため）。pale_oak_leaves は空データで id 35→34 の付け替えを確かめる（`PlayTranslator773Test`） |
| ブロック状態 | 27,946→29,671件（+1,725）。新規は棚12種、銅の松明・壁松明、銅の棒×8、銅の鎖×8＋`chain`→`iron_chain` の改名、銅のランタン×8、銅のチェスト×8、銅ゴーレム像×8、避雷針の露出・風化・酸化・ワックス×7。trial_spawner 27730→29455、vault 27742→29467 | 対応表で変換する。新規ブロック状態は古いサーバー向けに石（1）へ |
| アイテム | 1,416→1,488件（正味+72。新規73、改名1）。棚12件を305〜316に挿入。bookshelf 305→317。`chain`→`iron_chain` の改名（772の378→399）。新規アイテムは銅の道具×5、銅の防具×4、copper_golem_spawn_egg、copper_horse_armor、copper_nugget | 対応表で変換。773にしかないアイテムは古いサーバー向けに石へ。772の oak_boat 827 は773の863（棚の挿入で+36。オークのボート自体は変わらないが番号は変わる） |
| エンティティ | 151→153件（+2）。`copper_golem` が773の27、`mannequin` が773の82に挿入。pig 95→97、happy_ghast 56→57 | 対応表で変換。773→772で copper_golem／mannequin はブタ（95）になる。**Registry Data の entity_type は並べ替えの対象外のため、772サーバーから来た151件（pig=95）のまま773クライアントに届く**（下の「エンティティのレジストリ順序」を参照） |
| 音 | 1,728→1,771件（+43）。銅ゴーレム／チェスト／棚／像の音と `weather.end_flash`。block.stone.break 1442→1484。music_disc.lava_chicken 939→970 | 対応表で変換。773→772で新規の音は block.stone.break（772の1442）に付け替わる |
| パーティクル | 114→115件（+1）。`copper_fire_flame` が773の5番に挿入 | 対応表で変換する（上を参照） |
| 属性 | 35件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エンチャント | 42件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| バイオーム | 65件で同じ顔ぶれ・同じ順番（pale_garden は 39、plains は 40） | 変換なし（identity） |
| ブロックエンティティ種別 | 47→49件（+2）。shelf を773の40番に挿入、copper_golem_statue を773の48番に追加 | 対応表で変換。773の40（shelf）と48（copper_golem_statue）に772の対応は無いためパケットごと落とす |
| 絵画 | 51件で同じ顔ぶれ・同じ順番（dennis=14、kebab=24、pointer=33、pond=34、unpacked=44、wither=50） | 変換なし（identity） |
| 旗の模様・トリム | 変わらず（旗43件・トリム18件） | 変換なし |
| ジュークボックスの歌 | 21件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| ダイアログ（レジストリ） | **3件で同じ**（custom_options 0、quick_actions 1、server_links 2） | 772→旧クライアントでは dialog レジストリを落とす（770以下には無いため）。770サーバーからは来ないため、773クライアントへ**合成注入はしない**（実バニラクライアントが欠けたレジストリを内蔵既定で補うかは未確認のため。772段階と同じ） |
| ダメージ種別 | 49件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| メニュー | 25件で変わらず | 変換なし |
| Client Information（Settings） | 772と同じ9フィールド（末尾に `particleStatus` varint） | そのまま通す。773サーバー向けも particleStatus を落としてはいけない（772段階の罠と同じ。`handleClientConfiguration768` の2条件に isServer773() を追加してある） |
| Player Info | 772と同じ8ビット（update_hat／update_list_order を含む）。フィールド順は listPriority の後に showHat（minecraft-data に従った仮定。772と同じ） | そのまま通す（identity） |
| Set Held Item／Vehicle Move | 772と同じ形式。Vehicle Move は772・773とも末尾に onGround bool 付き | そのまま通す（identity） |
| Spawn Entity Experience Orb | 772・773とも専用パケットは無い（Spawn Entity に統合済み） | 経験値オーブの専用分岐は無い。772・773とも Spawn Entity（type experience_orb）で通す |
| Trade List | 772と同じ形式 | 成分種別の番号だけ対応表で直す（772・773とも96件で同じため実質 identity） |
| ロケーターバー／tracked_waypoint | tracked_waypoint（0x88）は772・773にしか無い（772は0x83）。属性 waypoint_transmit_range／waypoint_receive_range（33／34）と連動する | **772⇔773ではそのまま通す**（番号だけ 0x83⇔0x88 に付け替える）。771以下のクライアント向けに落とす。773同士は素通し（リレー） |
| ダイアログ（771・772・773の要素） | show_dialog（0x8A）・clear_dialog（0x89、プレイ）、show_dialog（0x12）・clear_dialog（0x11、設定フェーズ）。設定フェーズのサーバー行き custom_click_action（0x08）とプレイの custom_click_action（0x41）も771・772・773にある | **771⇔772⇔773ではそのまま通す**（番号だけ 0x85⇔0x8A、0x84⇔0x89 に付け替える）。770以下のクライアント・サーバー向けは落とす。773サーバーへは Custom Click Action を通す |
| Change Gamemode | change_gamemode（0x04、サーバー行き）は771・772・773にある | **772⇔773ではそのまま通す**。770以下のサーバー向けに落とす。773サーバーへは通す |
| エフェクト | 772・773とも同じ顔ぶれ・同じ順番 | 変換なし（identity） |

検証スクリプトは `detergentmc-protocol/tools/verify-773-tables.py`
（772段階の `verify-772-tables.py` を写したもの。350チェック全て合格）。
本物のバニラ1.21.9／1.21.10から Registry Data の順番をそのまま記録する統合テスト
（`RegistryOrderCaptureTest` の1.21.9／1.21.10版）は**未実施**（773では
ViaVersion の `mapping-1.21.9.json` と minecraft-data の表で確認した。1.21.10は
ViaVersion の対応表が無いため 1.21.9 の表を両方に使う）。
実バニラが設定フェーズで送るレジストリは21レジストリで、772と同じ顔ぶれ。
`minecraft:attribute` と `minecraft:block_entity_type` は送られないため、
属性の順番とブロックエンティティ種別の順番は minecraft-data と jar の data ファイルに依る。

**エンティティのレジストリ順序（772→773の既知の制限）：**
`PlayTranslator` の `registryIdMapper`／`registryTargetNames` が並べ替えを
扱うのは enchantment、painting_variant、banner_pattern、trim_pattern、
jukebox_song、damage_type の6レジストリのみで、entity_type、particle_type、
block_entity_type、biome、block、item、sound 等は素通しする。そのため
772サーバーから来た151件（pig=95）のまま773クライアントに届く。
Spawn Entity の種別ID自体は773へ翻訳される（pig=97）ため、実機クライアントでは
エンティティの表示がずれる可能性がある。772→773の一段だけ並べ替えを足しても
合成経路（内部763→…→772→773）では正しくならず、全体の直しは全ペアにまたがる
再設計になるため、773専任の範囲では直さない。`PlayTranslator773Test` に
素通しを明記するテストを入れてある。同じ問題はエンティティ順が変わる既存のペア
（767→768、769→770、770→771）にもある。

**Code of Conduct（773の新規パケット）：**
実バニラ1.21.9は既定のオフラインサーバーでは Code of Conduct（設定 0x13）を
送ってこない。2026-11-12の生ソケットプローブ
（`detergentmc-protocol/tools/probe1219-coc.py`／`probe1219-coc2.py`：偽773クライアントで
実バニラ1.21.9に直接接続）で確認した。2回の接続とも 0x13 は来ず、Finish
Configuration は accept なしで届いた（初回は14.49秒、2回目は3.64秒で、
初回の遅れは初回接続の温まり）。Acknowledge を送ると Join Game（0x30）が
届き、サーバーログに "Probe2 joined the game" が出た。Accept Code of Conduct
（設定サーバー行き 0x09）は minecraft-data の protocol.json で空ペイロードと
確認済み。そのため古いクライアントでも設定フェーズで止まらない。
773クライアントが Accept（0x09）を送ったときは、プロキシは773サーバーへ
そのまま通す（`ProxySession.handleClientConfiguration773` に 0x09 分岐を
追加。旧サーバー向けは落とす）。CoC を送るサーバーに古いクライアントが
入った場合、プロキシは CoC を落とすだけで accept を代理で返さないため、
サーバーが accept を待つ設計なら設定フェーズで止まる（未確認の制限。
`Outcome` にサーバーへの合成送信が無いため、この段階では直していない）。

**772→773の連鎖の方針：** 773クライアント→772サーバーの変換は、
773→772へ直してから既存の `handleClientPlay772` に渡す二段方式。
ただし `handleClientPlay772` はクライアントが772であることを前提に
書かれているため、内部772サーバーのときだけは773→772へ直した
パケットを直接バニラへ送り、それ以外（内部763／764／765／766／767／768／769／770／771）の
ときだけ `handleClientPlay772` に渡す。同じ罠はサーバー→クライアント側にも
あり、772サーバーから773クライアントへの Join Game／Respawn／Disconnect は
773向けの分岐を `handleServerPlayFrom772`／`handleServerJoinGame772` に
追加してある。767／766サーバーから773クライアントへは 767→768→769→770→771→772→773 の
七段・六段で変換する。763サーバーから773クライアントへは、合成される
Join Game を768形式（769／770／771／772／773と同形式、番号 0x30）で作る
（`handleSynthesizedJoinGame` の分岐。**773を漏らすと765形式の Join Game
（0x29）が773クライアントに届く**。772段階の漏れ修正（8fa47ab）の773版として
直してある）。
設定フェーズの辞書は766形式で作り（`PlayTranslator.buildRegistryData766`）、
773クライアント向けには766→767→768→769→770→771→772→773へ七段で並べ替え、
772クライアント向けには六段で並べ替える（`sendSynthesizedConfigIfReady`）。

**設定フェーズの振り分けの注意：** 773クライアント→771サーバーの設定フェーズは、
専用の `ProxySession.handleServerConfiguration773From771`
（Registry Data の771→772→773二段並べ替え・Tags 落とし・Cookie／Select Known Packs の
空応答・dialog 素通し）を通す。振り分けで `is771()` の分岐（`isServer771()` の素通し）だけを
見ていると、771サーバーの Registry Data が773クライアントに771形式のまま届く。
773→772の設定フェーズは、dialog（0x11／0x12）が771・772・773とも同ID・同形式のため
そのまま通す。設定フェーズの Custom Click Action 0x08 も772・773とも同じためそのまま通す。

**773パッチを写すときの罠（773段階で実際に踏んだもの）：**
- `shift()` は 772→773 しか写さない。版の条件（`is773()`）だけ変えて
  パケットIDを772のまま残す。プレイ・クライアント行きIDが108件変わる版では、
  分岐を写すたびに送出IDを773の値に直す必要がある。`ProxySession773Test` の
  10件→4件→1件→0件の失敗で発覚し、`tools/fix-773-proxysession-ids.py`／
  `-2.py`／`-3.py`（計43件）で直してある。直したのは次のものである。
  - `handleServerPlayFrom773` のディスパッチID全7件（0x6F→0x74、0x2B→0x30、
    0x5A→0x5F、0x26→0x2B、0x36→0x3B、0x1C→0x20、0x4B→0x50）と
    Disconnect 出力の773分離（0x20）、Respawn に is773() 分岐追加（0x50）、
    Join Game に無かった is773() 分岐を763／766／汎用に新設、
    Disconnect／Respawn の is773() 分岐5本ずつ、Client Information は
    773サーバー向けも particleStatus を落とさない、Custom Click Action の
    `isServer772() || isServer771() || isServer772()` の重複を
    `isServer773() || isServer772() || isServer771()` に修正、設定フェーズの
    ダイアログ case 0x11, 0x12 を `handleServerConfiguration773From772` に追加、
    Add Resource Pack の773は0x4F、clientId フォールバック4件、
    From766 の Disconnect／Respawn に is773() 分岐追加（0x20／0x50）。
  - `handleSynthesizedJoinGame` に is773() 分岐新設（預かった Join Game の
    番号が0x29に落ちていた）、From773 Respawn に is770()／is768()／is769() 分岐追加（0x4C）、
    `handleServerPlayUp`（764／765サーバー→上位クライアント）の gate に is773() を
    追加し Keep Alive 0x2B／Ping 0x3B／Disconnect 0x20／Respawn 0x50／
    Add Resource Pack 0x4F、`handleServerPlayFrom773` の末尾に 771／770／769／768
    クライアント向けの変換チェーンを追加（773サーバーからの Set Slot 等が
    落とされていた）。
  - `gen-773-proxysession.py` を HEAD から再実行するとこれらの修正が消える。
    生成器自体の修正は未着手のため、再実行時は fix-773-proxysession-ids(-2/-3).py
    も再適用すること。
- **生成器が多段変換の途中の段を落とす罠（773段階で単体テストから発覚）。**
  生成器は772の分岐を版番号だけで写すため、新しい版への一段目の変換を
  丸ごと落とすことがある。`ProxySession773Test` の `join773Against771`／
  `join771Against773`（music_disc_mall 1256→1305 のはずが 1304 になった）と
  `join771Against773`（クリエイティブが落とされた）で発覚した。
  修正は `tools/fix-773-proxysession-chains.py` と
  `tools/fix-773-config-chains.py` に記録してある。
  - プレイ（サーバー→クライアント）：`handleServerPlayFrom771`／`From770` の
    is773() 分岐が `handleServerPlay772To773(packet)` を直接呼んでいた。
    771・770形式のパケットを772→773の翻訳器に渡すためレジストリIDが一段ずれる。
    新設した `handleServerPlay771To773`（771→772→773の二段）と
    `handleServerPlay770To773`（770→771→772→773の三段）へ向け直した。
    769／768／767／766の `handleServerPlayNNNTo773` はもとから正しい二段連鎖。
  - プレイ（クライアント→サーバー）：`handleClientPlayTo773` に is771() の
    振り分けが無く、771クライアントの Set Creative Slot が switch の
    creative case（is767() しか扱わない）に落ちて落とされていた。772段階の
    `handleClientPlayTo772` は先頭で is771() を `handleClientPlay771To772` へ
    振っていたが、生成器が is771()→is772() にずらしたため振り分け自体が消えた。
    新設した `handleClientPlay771To773`（771→772→773の二段）へ振るようにした。
    770／769／768クライアントのクリエイティブが落とされるのは772段階と同じ
    既存の扱い（鎖があるのは767と771／772だけ）。
  - 設定フェーズ（サーバー→クライアント）：771／770クライアントの isServer773()
    分岐が `handleServerConfiguration772From773` を直接呼んでいた。
    新設した `handleServerConfiguration771From773`（773→772→771の二段。
    dialog は771にもあるため素通し）と `handleServerConfiguration770From773`
    （773→772→771→770の三段。dialog は770に無いため落とす）へ向け直した。
    771クライアントのジュークボックスは20件（lava_chicken を落とす）、
    770クライアントは19件（lava_chicken と tears を落とす）になる。
    769／768／767／766クライアントにはもとから正しい多段ハンドラがある。
  - なお `handleServerPlayFrom770` の is772() 分岐
    （`handleServerPlay771To772(packet)` を770形式に直接当てる）は HEAD
    （1155adf）からある既存の不具合で、770サーバー→772クライアントの
    スロット等が一段ずれる。772クライアント向けの経路のため773の作業では
    直していない（`docs/VERSIONS.md` の「まだできないこと」を参照）。
- **プレイ中の Settings（Client Information）に particleStatus を二重に付ける罠。**
  `handleClientPlayTo773`／`To772`／`To771` の switch は「768以下は8フィールド」
  として `is769()` のときだけ素通ししていたが、particleStatus があるのは
  768（1.21.3）からで、767以下が8フィールド、768以上が9フィールドである
  （minecraft-data の `packet_common_settings` で確認。設定フェーズ側の
  コードは正しく「768 は末尾に particleStatus がある」と書いている）。
  768／770クライアントがプレイ中に設定を変更して Settings を送り直すと、
  9フィールドにさらにもう1個付け足され10フィールドの不正なパケットになっていた。
  `tools/fix-773-client-information.py` で条件を直した（To773／To772 は
  768／769／770を素通し、To771 は768／769を素通し、To770 は768を素通し。
  To769／To768 に到達するのは767以下のため無条件の付け足しで正しい）。
- **difflib の replace が鎖の末尾を置き換えて版を落とす罠。**
  鎖の末尾に1行足す差分（old772の末尾行 `... V1_21_6) {` → new772の
  `... V1_21_6` ＋ `... V1_21_7) {`）を difflib は replace（1行→2行）とみなし、
  shift（771→772、772→773）はその2行を `V1_21_7`／`V1_21_9` に写すため、
  old773（＝new772）の `V1_21_6`／`V1_21_7` の2行が `V1_21_7`／`V1_21_9` に
  置き換わって **V1_21_6 が落ちる**。`ClientAdapter.decide()`／`differences()`、
  `ClientProxy` の internalProtocol validation で実際に起き、単体テスト45件が
  失敗した。鎖の edit は手書きで「末尾に773行を足す」こと。純粋な append
  （new772.startswith(old772)）の場合は suffix だけを shift するのが最も安全
  （resourcepackの2回目のスクリプトで採用）。
- **shift() が写さないもの。** pack_format数値（64→69、81→88）は明示的に直す。
  `PACK_FORMAT_1_21_7`（アンダースコア形）と `sha1772`／`configuredSha1772`
  （大文字Sも）は shift 対象外。`NNN＝X.Y.Z`（＝形）は保護されるが、
  「1.21.4（769）」のような `X.Y.Z（NNN）` 形は版だけずれて番号が残り壊れる。
  「か」「と」の鎖は771を落とす（「・」「／」「⇔」の鎖は末尾追加が効く）。
  数値IDの中の版番号も素の replace では壊す（ブロック状態ID 27730 の中の
  "773" を "774" にして 27740 にする）。版番号の置換は正規表現
  `(?<!\d)773(?!\d)` 等の lookaround で「数字列の一部でない」ときだけ行うこと。
  逆に lookaround 方式でも、版番号ではない数値（765側の oak_boat ID `773`）を
  `774` にしてしまうため、数値IDを含むファイルでは生成後に grep で確認すること。
- 歴史の入ったバージョン一覧（`V1_21_2` の並び、`pack769` の並び、
  `pack_format 64`／`55`）は shift で欠ける。773側は772の NEW を残したまま
  773要素を**追加**する形で手書きしてある。
- 772→773で変わらないはずのもの（属性表・Chat Message の番号・oak_boat の番号・
  ハッピーガストの番号）を「772段階の新要素」と読み違えて、コメントだけ
  772→773のずれだと書くミスが `JavaBackendConnection773Test` のシフト生成で出た。
  直してある。
- **Spawn Position の形式変更（773）：Bedrock 側が読めず座標が化ける罠。**
  773の Spawn Position は RespawnData（次元名の文字列＋位置＋yaw＋pitch）に
  なった。772までは位置が先頭だったため、従来通り先頭8バイトを位置として
  読むと次元名（`minecraft:overworld`）のバイト列が座標になり、スポーン地点の
  チャンクが合わず `BedrockMatrixIntegrationTest.matrixAgainst1_21_9`／
  `matrixAgainst1_21_10` が「スポーン地点のチャンクが届きませんでした」で
  失敗した（チャンク自体は届いていた。9チャンク）。修正：773は次元名を
  読み飛ばしてから位置を読む（`JavaBackendConnection.java`）。
  プロキシは Spawn Position の中身を読まず番号だけ付け替えるため影響なし。
  実バニラ1.21.9への生ソケットプローブ（`tools/probe1219-chunks.py`）と
  minecraft-data の protocol.json（1.21.9：`packet_spawn_position` は
  `"RespawnData"`、`RespawnData`＝[globalPos: GlobalPos, yaw: f32,
  pitch: f32]、GlobalPos＝[dimensionName: string, location: position]）で確認済み。
- **Chunk Batch Received の Ack を返さないと1.21.9はチャンクを止める罠。**
  実バニラ1.21.9へのプローブで確認：Ack 無しでは最初のバッチ（9チャンク）の
  あと75秒何も送られてこない。Ack（Chunk Batch Received 0x0A、f32 2.0）を
  返すと次のバッチが来る（75秒で213チャンク・103バッチ）。修正：
  `JavaBackendConnection` が Chunk Batch Finished（773は0x0B）を受けたら
  Chunk Batch Received を返す。パケットが存在する版（764〜）では送る。
  763には無い。768〜772は Ack 無しでも送られてくるが、手順として返すのが
  正しいため共通化してある。

## 接続の流れ

772の流れ（`docs/PROXY-1.21.7.md` の「接続の流れ」）と同じ。773はパケットIDが
772と大きく変わるため、ハンドシェイク／ログイン／設定フェーズ／プレイの番号は
773の表で読む。違うのは次の4点だけである。

- **プレイのクライアント行きIDが108件変わる**：Join Game 0x30、Chunk Data 0x2C、
  Keep Alive 0x2B、Ping 0x3B、Start Configuration 0x74、Disconnect 0x20、
  Respawn 0x50、Add Resource Pack 0x4F、Spawn Position 0x5F、Player Info Update 0x44。
  設定フェーズ・ログインのIDは772と同じ（Code of Conduct 0x13／Accept 0x09 が新規）。
- **設定フェーズの Registry Data の並べ替えが七段になる**：773クライアント向けは
  772の六段（767→768→769→770→771→772）に773（772→773）を足した七段で並べ替える。
  772クライアント向けは六段のまま。
- **773→771の設定フェーズは専用ハンドラ**：`handleServerConfiguration773From771`
  （Registry Data の771→772→773二段並べ替え。Tags 落とし、Cookie／Select Known Packs の
  空応答、dialog は素通し）。
- **ダイアログは771⇔772⇔773で通す**：設定フェーズの Show Dialog（0x12）／Clear Dialog（0x11）、
  プレイの Show Dialog（0x8A）／Clear Dialog（0x89）、Tracked Waypoint（0x88）、
  Change Gamemode（0x04）、Custom Click Action（プレイ 0x41・設定 0x08）は
  771⇔772⇔773でそのまま通す。770以下向けだけ落とす。Tracked Waypoint だけは
  772・773にしか無いため771向けも落とす。

## プレイ中の翻訳

772の翻訳（`docs/PROXY-1.21.7.md` の「プレイ中の翻訳」）と同じ。773はパケットIDが
772と変わるため、番号の付け替えとペイロードの付け替えの両方がある。
付け替えるのは次のものである。

- **パケットID**：プレイのクライアント行き108件。Join Game 0x2B→0x30、Chunk Data
  0x27→0x2C、Keep Alive 0x26→0x2B、Ping 0x36→0x3B、Start Configuration 0x6F→0x74、
  Disconnect 0x1C→0x20、Respawn 0x4B→0x50、Add Resource Pack 0x4A→0x4F、Spawn Position
  0x5A→0x5F、Player Info Update 0x3F→0x44、Tracked Waypoint 0x83→0x88、Show Dialog
  0x85→0x8A、Clear Dialog 0x84→0x89。プレイのサーバー行きは変わらない。
- **ブロック状態**：27,946→29,671件（+1,725）。trial_spawner 27730→29455、vault 27742→29467。
  新規ブロック状態は石（1）へ。
- **アイテム**：棚12件の挿入で bookshelf 305→317、354→367（+13）。chain 378→iron_chain 399
  （改名）。末尾1415→1487（+72）。
- **音**：銅チェストの音6件の挿入で block.stone.break 1442→1484、music_disc.lava_chicken
  939→970。773→772で新規の音は block.stone.break（1442）になる。
- **エンティティ**：銅ゴーレム 27・マネキン 82 の挿入で pig 95→97、happy_ghast 56→57。
  773→772で銅ゴーレム／マネキンはブタ（95）になる。
- **パーティクル**：copper_fire_flame 5 の挿入で pale_oak_leaves 34→35、trail 48→49、
  block_crumble 112→113。
- **絵画**：51件で同じ（dennis=14、kebab=24、pointer=33、pond=34、unpacked=44、wither=50）。
- **ジュークボックスの歌**：21件で同じ。

それ以外（バイオーム・属性・エンチャント・メニュー・データコンポーネント）は772⇔773で
identity である。

## 送らない・落とすパケット（773向け）

| パケット | 向き | 理由 |
| --- | --- | --- |
| Window Click（0x11） | 773→772／772→773／773→771以下 | HashedSlot のハッシュ算法が未検証のため、全方向で落とす。772⇔773でも落とす |
| Set Creative Slot（0x37） | 773→763／764／765 | UntrustedSlot はデータコンポーネント形式のため、763／764／765向けに変換できず落とす。772⇔773・773⇔771／770／769／768／767／766では変換できる |
| Tracked Waypoint（0x88） | 773→771以下 | 772・773にしか無いため。772⇔773では通す |
| Show Dialog（0x8A）／Clear Dialog（0x89） | 773→770以下 | 771・772・773にしか無いため。771⇔772⇔773では通す |
| Show Dialog（設定 0x12）／Clear Dialog（設定 0x11） | 773→770以下 | 771・772・773にしか無いため。771⇔772⇔773では通す |
| Change Gamemode（0x04） | 773→770以下 | 771・772・773にしか無いため。772⇔773では通す |
| Custom Click Action（プレイ 0x41・設定 0x08） | 773→770以下 | 771・772・773にしか無いため。772⇔773では通す |
| dialog レジストリ（minecraft:dialog） | 773→770以下 | 770以下には無いため落とす。771⇔772⇔773では通す |
| Code of Conduct（設定 0x13） | 773サーバー→772以下 | 772以下には無いため落とす。773同士では通す（既定のサーバーは送ってこない） |
| デバッグ系パケット（DEBUG_BLOCK_VALUE 0x1a／DEBUG_CHUNK_VALUE 0x1b／DEBUG_ENTITY_VALUE 0x1c／DEBUG_EVENT 0x1d／GAME_TEST_HIGHLIGHT_POS 0x27／DEBUG_SUBSCRIPTION_REQUEST 0x16） | 773→772以下 | 773にしか無いため落とす |
| Declare Recipes／Statistics／Tags／World Event ID | 全方向 | 未対応（772と同じ） |
| crafter／trial_spawner（Block Entity Data） | 全方向 | パケットごと落とす（772と同じ） |
| vault（Block Entity Data） | 766→765方向 | 772と同じ |
| shelf／copper_golem_statue（Block Entity Data） | 773→772以下 | 773にしか無いため落とす |
| test_block／test_instance_block | 773→769以下 | 770・771・772・773にしか無いため落とす |

## 対象外・未実装（正直な一覧）

772の「対象外・未実装」と同じものに加え、773では次のものが残る。

- **Window Click（773が関わる全方向）**：772⇔773でも落とす。773クライアントが内部773以外のサーバーにいるとき、773サーバーにいる772以下のクライアントは、インベントリ操作（クリック）ができない。Set Creative Slot は UntrustedSlot→Slot で変換できる（773⇔772／771／770／769／768／767／766）。
- **Set Creative Slot（773クライアント→内部763／764／765）**：773の UntrustedSlot はデータコンポーネント形式のため変換できず落とす（772と同じ制限）。
- **ロケーターバー（773→771以下）**：772・773にしか無いため、771以下のクライアント・サーバーには送らない。**ダイアログ・Change Gamemode・Custom Click Action（773→770以下）**：771・772・773にしか無いため、770以下のクライアント・サーバーには送らない。
- **エンティティのレジストリ順序（772→773）**：Registry Data の entity_type は素通しするため、772サーバーから来た151件（pig=95）のまま773クライアントに届く。Spawn Entity の種別ID自体は773へ翻訳される（pig=97）ため、実機クライアントではエンティティの表示がずれる可能性がある。773専任の範囲では直さない（上の「エンティティのレジストリ順序」を参照）。
- **銅ゴーレム／マネキン（773→772）**：copper_golem（773の27）／mannequin（773の82）は772に無いためブタ（95）になる。銅ゴーレム像・棚のブロックエンティティ（shelf 40／copper_golem_statue 48）はパケットごと落とす。
- **1.21.9の新要素で通せないもの（773サーバーに772以下のクライアントがいる場合）**：銅の道具・防具は772以下のクライアントでは石に見える（アイテムのフォールバック）。銅ゴーレム／マネキンはブタに見える。銅のチェスト・棚・銅のランタン・銅の鎖等の新ブロックは石に見える。銅の音は block.stone.break になる。デバッグ系パケットは旧クライアント向けに落とす。
- **Code of Conduct（773）**：既定のサーバーは送ってこないため通常は問題にならない。CoC を送るサーバーに古いクライアントが入った場合、プロキシは CoC を落とすだけで accept を代理で返さないため、サーバーが accept を待つ設計なら設定フェーズで止まる（未確認の制限）。
- **Test Instance Block（773）**：`set_test_block`／`test_instance_block_action`／`test_instance_block_status` は770・771・772・773にしか無いため落とす。
- **Pick Item（773↔772）**：773の pick_item_from_block／pick_item_from_entity は772と同じ形式のため素通しする。旧バージョン（763／764／765／766／767／768）向けは、772と同じく位置・エンティティ指定とスロット番号指定の違いのため落とす。
- **Player Loaded（773）**：773クライアントが送る player_loaded（0x2B、空）は772と同じく古いサーバーには送らない。統合テストでは参加・スポーン・チャット・移動が player_loaded 無しで動いたが、773サーバーが player_loaded を前提に処理を進めるかどうかは未確認。
- **チャット checksum（772⇔773）**：772・773とも末尾に checksum u8 があるため付け外しは不要。checksum の本当の算法は未確認のため、署名付きチャットや online-mode での挙動は別途確認が必要。
- **Player Chat の globalIndex（772⇔773）**：772・773とも先頭に globalIndex があるため素通しする。プロキシのセッションごとのカウンタ（0から）は771サーバーからの player_chat に付ける。実バニラが globalIndex の連番を厳密に検証するかは未確認。
- **Heightmap の type id 順序（772⇔773）**：バニラの `Heightmap.Types` の列挙順と仮定している。type id と種別名の対応の仮定は未確認のまま（偽クライアントはチャンクを復号しないため、統合テストでは検証されていない）。
- **Vehicle Move の onGround（768クライアント→770サーバー）**：**770段階の既存の不具合**。`handleClientPlayTo770` の Vehicle Move 分岐は素通しのため、768クライアントの onGround 無しペイロードが770サーバーへそのまま送られる。773のスコープ外のため直していない。
- **From770 の一般プレイ分岐に is768() が無い（770段階の既存の挙動）**：770サーバー＋768クライアントの一般プレイは落とされる。773では変更しない。
- **764サーバー→773クライアントはプレイ状態のパケットが765形式のまま送られる**：既存の制限（772クライアントでも同じ）。
- **From766 の Disconnect／Respawn が770／771／772クライアントでフォールバックに落ちる**：既存の不具合（1155adfにもあった。773分岐だけ追加し、770〜772は直していない）。
- **From769／768 の Respawn で is770()→0x4B（正しくは0x4C）**：既存の不具合。
- **ネイティブ773同士の再設定はセッションが追跡しない**：773同士は Login Success 後に即プレイ状態になり、セッションは再設定を追跡しない（772のネイティブ同士と同じ）。Start Configuration（0x74）はそのまま中継され、設定フェーズの Custom Click Action（0x08）はプレイ状態の Chat Message（0x08）と番号が同じためバイト列のままサーバーへ届く（サーバーは設定状態で読むため正しく解釈する）。
- **creaking_heart の uprooted（772⇔773）**：772と同じ制限。772⇔773ではそのまま通す。
- **ポーションの分裂（772⇔773）**：772と同じ制限。`potion` 99 が `splash_potion` 99 と `lingering_potion` 100 に分裂したまま。772⇔773ではそのまま通す。
- **ボートの木の種類**：772と同じ制限。767のボートは772／773では常にオークのボートに、772／773のボートは767では常に（木の種類を問わず）ボートになる。variant（木の種類）をエンティティ種別に直す変換は未実装。
- **food の効果（767→768→769→770→771→772→773）**：金リンゴ等の食べたときの効果は768／769／770／771／772／773向けに落とす。767の food の効果は amplifier／duration を持たないため、768／769／770／771／772／773の `consumable` の apply_effects 形式へは変換できない。
- **custom_model_data（767→768→769→770→771→772→773）**：772と同じ制限。767の単一の数値を768／769／770／771／772／773の colors[0] に入れるだけのベストエフォート。floats／flags／strings は空になる。
- **Set Cooldown（773のグループ名）**：772と同じく、エンダーパール・コーラスフルーツ・ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす（クールダウンの表示だけの制限）。
- **レシピパケット（773⇔772）**：Craft Recipe Response／Request、Displayed Recipe はレシピIDの形式が変わり変換できないため落とす。手動クラフトは動く。
- **Declare Recipes／Statistics／Tags／World Event ID**：全方向で未対応。レシピ帳・タグが空になるだけで遊べる。
- **ブロックエンティティの crafter／trial_spawner**：パケットごと落とす。766→765方向では vault も落とす（1.20系と同じ）。shelf／copper_golem_statue は773にしか無く、772向けには落とす。
- **Entity Metadata の中身**：763向けは variant 系エントリを落とす。764では素通しのためスロット・ブロック状態・粒子の中身は未変換のまま残る。773にしかないもの（銅ゴーレム等）は古いサーバー向けに落とす・代替する。
- **Explosion の773専用パーティクル／サウンド指定**：既定演出に置き換わる（766→763方向と同じ）。
- **1.21.9の新要素で通せないもの（773サーバーに772以下のクライアントがいる場合）**：銅の道具・防具は772以下のクライアントでは石に見える（アイテムのフォールバック）。銅ゴーレム／マネキンはブタに見える。銅のチェスト・棚・銅のランタン・銅の鎖等の新ブロックは石に見える。銅の音は block.stone.break になる。ロケーターバーは771以下のクライアントでは表示されない。ダイアログ・Change Gamemode・Custom Click Action は770以下のクライアントでは表示されない・届かない（パケットを落とす）。デバッグ系パケットは旧クライアント向けに落とす。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：764／765／766／767／768／769／770／771／772／773＋online-modeも同じ制限（内部バニラがoffline-modeのため Player Info Updateにプロパティを載せない）。
- **本物のゲームクライアントでの確認は未実施**：全121通り共通。手順書は `docs/PLAYTEST-1.21.9.md`。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの検証のみ。
- **1.21.11以降（774〜）は未対応**：プロキシは773より新しいプロトコルをログイン時に断る。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みはあるが、パックを置く場所はまだ決まっていない。既定はloopback配信。

## Java 21（内部773サーバーを動かすとき）

772と同じ。バニラ1.21.9／1.21.10は Java 21 が必要で、DetergentMC自体は Java 17 で
動く。`VanillaServerHost` は `detergentmc.java21.home` システムプロパティ、
`JAVA_HOME`、`java.home`、既定の場所の順に Java 21 を探す。統合テストは
`Java21TestSupport.ensureJava21Home()` で Java 21 を確認してから起動する。
1.21.9のjarのSHA-1は `11e54c2081420a4d49db3007e66c80a22579ff2a`、
1.21.10は `95495a7f485eedd84ce928cef5e223b757d2f764`（piston-meta確認済み、
`run/vanilla/server-1.21.9.jar`／`server-1.21.10.jar`）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.21.9.md` を参照。

## テスト

- `ClientAdapter773Test`（ClientAdapter の773受け入れ・REJECT_TOO_NEWを774へ・
  `differences(773)` の「新規パケット8件、ID変更108件／銅ゴーレム」）
- `ClientJoinFlow773Test`（設定フェーズの有無。773は772と同じく設定フェーズあり）
- `ChunkTranslator773Test`（ブロック状態 trial_spawner 27730→29455・vault 27742→29467、
  アイテムの棚12件の挿入・chain→iron_chain の改名、音・パーティクル・絵画・
  Spawn Entity happy_ghast 56→57・銅ゴーレム／マネキンのブタ代替）
- `PlayTranslator773Test`（ジュークボックス21件・dialog レジストリの素通し・
  エンティティ／パーティクル／ブロックエンティティ Registry Data の素通し・
  chat checksum の strip／append）
- `ProxySession773Test`（773↔772・773↔771・773↔770・773↔766・773↔765・769↔773・773↔763の参加フロー、
  dialog／Change Gamemode／Custom Click Action／Tracked Waypoint の中継、
  設定フェーズの Custom Click Action／dialog の中継。18件）
- `RegistryRemapper773Test`（772→773の対応表と逆向き）
- `PacketIds773Test`／`ProtocolVersion773Test`（パケットIDの108件変更・新規8件・削除1件・プロトコル番号）
- `ResourcePackBuilder773Test`（773用パック pack_format 69）
- `ResourcePackMode773Test`／`ResourcePackServer773Test`（url-773／sha1-773、11パック配信）
- `ResourcePackWiringTest`（11パック化）
- `JavaBackendConnection773Test`（Bedrock→内部773。単体テスト12件。Spawn Position
  の RespawnData 読みと Chunk Batch Received の Ack の2件を追加）
- `ProxyIntegrationTest` の773関係8テスト（773→1219、773→12110、773→1217、773→1218、
  773→1216、773→1201、772→1219、772→12110。いずれも参加・スポーン・チャット・移動まで確認）
- `BedrockMatrixIntegrationTest.matrixAgainst1_21_9`／`matrixAgainst1_21_10`
  （Bedrock 6 プロトコル × 内部773。参加・スポーン・チャット・移動・チャンク・アイテム）

テスト総数は最終ビルドで確定する（`./gradlew clean build` の結果を参照）。
