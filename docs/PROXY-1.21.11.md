# 1.21.11クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.21.11クライアント（プロトコル774）は、内部プロトコルが
763／764／765／766／767／768／769／770／771／772／773／774のどのサーバーにも参加できます。内部774のときは
素通し（リレー）、それ以外は上向き・下向き変換です。773との違いは小さく、
**パケットIDは0変更・0追加・0削除**です。レジストリはアイテム+17件、
エンティティ+4件、音+67件、データコンポーネント+8件、エンチャント+1件
（lunge）、エフェクト+1件（BreathOfTheNautilus）、ダメージ種別+1件
（spear）、統計+1件（nautilus_one_cm）です。ブロック状態・バイオーム・
属性・メニュー・パーティクル・ブロックエンティティ種別・絵画・
ジュークボックスは773とまったく同じです。
773の仕組みは `docs/PROXY-1.21.9.md`、772は `docs/PROXY-1.21.7.md`、771は
`docs/PROXY-1.21.6.md`、770は `docs/PROXY-1.21.5.md`、769は
`docs/PROXY-1.21.4.md`、768は `docs/PROXY-1.21.2.md`、767は
`docs/PROXY-1.21.md`、766は `docs/PROXY-1.20.5.md`、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。

全144通り（クライアント763／764／765／766／767／768／769／770／771／772／773／774 × サーバー763／764／765／
766／767／768／769／770／771／772／773／774）の対応マトリクスは `docs/VERSIONS.md` の「1.21.11対応：
全144通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用・767用・768用・769用・770用・771用・772用・773用・774用の12つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 773→774の差分（要点）

| 分野 | 773→774の差分 | 扱い |
| --- | --- | --- |
| パケットID（クライアント行き・プレイ） | **0変更・0追加・0削除**。Join Game 0x30、Chunk Data 0x2C、Keep Alive 0x2B、Ping 0x3B、Start Configuration 0x74、Disconnect 0x20、Respawn 0x50、Add Resource Pack 0x4F、Spawn Position 0x5F、Player Info Update 0x44、Tracked Waypoint 0x88、Show Dialog 0x8A、Clear Dialog 0x89 は773と同一（`mc-1.21.11-protocol.json` と `PacketIds` の774列で確認） | 番号付け替えは不要。ただし `ProxySession` の From774 は生成器が772時代のID（0x6F／0x2B／0x5A等）のまま残していたため、live の773メソッドから再建して直した（下の「774パッチを写すときの罠」を参照） |
| パケットID（サーバー行き・プレイ） | **変わらない**。Chat Message 0x08、Chat Command Signed 0x07、Window Click 0x11、Set Creative Slot 0x37、Vehicle Move 0x21、Keep Alive 応答 0x1B、Ping 応答 0x2C、Set Player Position 0x1D も同じ | 番号付け替えは不要。汎用の `PacketTranslator.translateId` は同IDをそのまま通す。774同士は素通し |
| 新規パケット | **0件** | Code of Conduct（設定 0x13）／Accept Code of Conduct（設定 0x09）は773からあるものをそのまま使う |
| 削除パケット | **0件** | — |
| Login Success | 773と同じく末尾の `strictErrorHandling` bool が無い（765と同じ形式） | そのまま通す。767向けは `false` を付け足す（773と同じ） |
| Join Game／Respawn | 773と774で形式は同じ（LoginData／SpawnInfo。Respawn は末尾の `seaLevel` varint を含む）。番号も同じ（Join Game 0x30、Respawn 0x50） | 番号・形式とも付け替え不要。767向けは seaLevel（63）を落とす（773と同じ） |
| Spawn Position | 773と774とも RespawnData（次元名の文字列＋位置＋yaw＋pitch）形式 | そのまま通す。Bedrock 側の読み取りも RespawnData（773と同じ） |
| Player Chat（クライアント行き） | 先頭に `globalIndex` varint があるのは773・774とも同じ | 素通しする（773→774・774→773とも globalIndex を付けも剥がしもしない）。カウンタは `ProxySession.chatGlobalIndex` を773／774で共用する（セッションごと0から数える） |
| Chat Message／Chat Command Signed（サーバー行き） | 末尾の `checksum` u8 があるのは773・774とも同じ | 剥がしも付け足しも不要。773→774・774→773とも番号は同じ（Chat Message 0x08）。**774の checksum 0x00 は実バニラ1.21.11に受け入れられることを統合テストで確認済み**（`ProxyIntegrationTest.fake774ClientJoinsRealVanilla12111ThroughProxy`）。`stripChatChecksum774`／`appendChatChecksum774` は定義されているがどこからも呼ばれない死にコードで、javadoc の「剥がす／付ける」記述は誤りのため直してある |
| Heightmap（Chunk Data） | 773と同じ直接の配列（varint 個数＋{type varint、data long[]}） | 773・774とも直接配列のまま写す |
| チャンクセクションの Data Array（Chunk Data） | **773と同じ**（長さの varint 接頭辞なし。長さは bitsPerEntry とエントリ数から計算する）。シングルパレット（bpe=0）も長さ varint（0）が無い | 773・774とも同じ読み方で通す |
| Window Click | HashedSlot のまま（773と同じ） | **変換できず落とす**（773と同じ。ハッシュ算法が未検証のため）。773⇔774でも落とす |
| Set Creative Slot | UntrustedSlot のまま（773と同じ） | `ChunkTranslator.translateSetCreativeSlot773To774`／`774To773` で変換する。槍・ノーチラスの防具（774の新規アイテム）は773向けに石（1）になる |
| スロット（データコンポーネント） | 種別は96→104件。**8件の新規挿入**（use_effects 5、minimum_attack_charge 7、damage_type 8、attack_range 30、piercing_weapon 38、kinetic_weapon 39、swing_animation 40、zombie_nautilus/variant 94）のため KIND 表で付け替える。773→774の写像は 0–4→0–4、5→6、6–26→9–29、27–33→31–37、34–86→41–93、87–95→95–103。enchantments は773=10→774=13、jukebox_playable は773=55→774=62 | KIND 表で変換する。774の新規8種は773向けに落とす。成分の中身は対応表で番号を直す（アイテム・エンチャント・ダメージ種別・音・ジュークボックスの歌）。**注意：773節の `translateComponent772To773`／`773To772` の case 16 は `enchantment_glint_override`（bool）だと書いてあるが、773の kind 16 は repair_cost（varint）で 18 が glint（bool）である。774節では直してあるが、772⇔773のコードには同じ取り違えが残っている（下の「対象外・未実装」を参照）** |
| Entity Metadata | 型番号は28番以降が+1ずれる。774は zombie_nautilus_variant を28番に挿入し、humanoid_arm を38番に追加。773の型は0–36、774の型は0–38（773：28=optional_global_pos、29=painting、30=sniffer、31=armadillo、32=copper_golem、33=weathering、34=vector3、35=quaternion、36=resolvable_profile。774：29=optional_global_pos、30=painting、31=sniffer、32=armadillo、33=copper_golem、34=weathering、35=vector3、36=quaternion、37=resolvable_profile、38=humanoid_arm） | 型番号を付け替える。**774にしかない zombie_nautilus_variant（28）と humanoid_arm（38）はエントリごと落とす**。773の variant 系（wolf／painting 等）は773の扱いと同じ |
| World Particles | 115件で同じ顔ぶれ・同じ順番（copper_fire_flame は773・774とも5、trail 49、block_crumble 113、pale_oak_leaves 35、tinted_leaves 36、firefly 114） | 変換なし（identity） |
| ブロック状態 | 29,671件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| アイテム | 1,488→1,505件（+17）。nautilus_spawn_egg 1156、camel_husk_spawn_egg 1173、parched_spawn_egg 1176、zombie_nautilus_spawn_egg 1184、netherite_horse_armor 1259、槍7件（wooden 1296〜netherite 1302）、ノーチラスの防具5件（iron／golden／diamond／netherite／copper_nautilus_armor 1333〜1337）。music_disc.lava_chicken 1304→1316、oak_boat 863 は両版とも同じ | 対応表で変換。774にしかない17件は773向けに石へ（`mapItemUp774`） |
| エンティティ | 153→157件（+4）。camel_husk が774の20、nautilus が774の88、parched が774の97、zombie_nautilus が774の152に挿入。pig 97→100、ghast 56→57、happy_ghast 57→58、falling_block 50→51、copper_golem 27→28、mannequin 82→83 | 対応表で変換。774→773で camel_husk／nautilus／parched／zombie_nautilus はブタ（97）になる。**Registry Data の entity_type は並べ替えの対象外のため、773サーバーから来た153件（pig=97）のまま774クライアントに届く**（下の「エンティティのレジストリ順序」を参照） |
| 音 | 1,771→1,838件（+67）。削除は無し | 対応表で変換。774→773で新規の音は generic（block.stone.break 相当の代替）に付け替わる |
| パーティクル | 115件で同じ | 変換なし（identity） |
| 属性 | 35件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エンチャント | 42→43件。**lunge が774の21番に挿入**（lure は22、mending は23、wind_burst は42になる） | 対応表で変換。774→773で lunge は落とす（DROP） |
| バイオーム | 65件で同じ顔ぶれ・同じ順番（pale_garden は 39、plains は 40） | 変換なし（identity） |
| ブロックエンティティ種別 | 49件で同じ顔ぶれ・同じ順番（shelf=40、copper_golem_statue=48 を含む） | 変換なし（identity） |
| 絵画 | 51件で同じ顔ぶれ・同じ順番（dennis=14、kebab=24、pointer=33、pond=34、unpacked=44、wither=50） | 変換なし（identity） |
| 旗の模様・トリム | 変わらず（旗43件・トリム18件） | 変換なし |
| ジュークボックスの歌 | 21件で同じ顔ぶれ・同じ順番（lava_chicken=9、mall=10、tears=18、wait=19、ward=20） | 変換なし（identity）。**ただし `registryTargetNames` の jukebox_song に 774 が無く、773↔774の Registry Data 変換で NPE になっていた（774対応で発見・修正。下の「罠」を参照）** |
| ダイアログ（レジストリ） | **3件で同じ**（custom_options 0、quick_actions 1、server_links 2） | 773→774・774→773ともそのまま通す。770以下向けは落とす（773と同じ） |
| ダメージ種別 | 49→50件。**spear が774の37番に挿入**（spit は38、wither_skull は49になる） | 対応表で変換。774→773で spear は落とす（DROP） |
| エフェクト | 39→40件。**BreathOfTheNautilus が774の39番に追加**（0–38は同一） | **Entity Effect のエフェクトIDは773⇔774で付け替えず素通しするため、774→773で 39 がそのまま773クライアントに届く**（773クライアントは39を知らない。残る制限として報告する） |
| 統計 | 76→77件。**nautilus_one_cm が22番に挿入** | Declare Statistics 自体が全方向で未対応のため、統計のずれはプレイには影響しない |
| メニュー | 25件で変わらず | 変換なし |
| Client Information（Settings） | 773と同じ9フィールド（末尾に `particleStatus` varint） | そのまま通す。774サーバー向けも particleStatus を落としてはいけない（773段階の罠と同じ） |
| Player Info | 773と同じ8ビット（update_hat／update_list_order を含む）。フィールド順は listPriority の後に showHat | そのまま通す（identity） |
| Set Held Item／Vehicle Move | 773と同じ形式。Vehicle Move は773・774とも末尾に onGround bool 付き | そのまま通す（identity） |
| Spawn Entity Experience Orb | 773・774とも専用パケットは無い（Spawn Entity に統合済み） | 経験値オーブの専用分岐は無い。773・774とも Spawn Entity（type experience_orb）で通す |
| Trade List | 773と同じ形式 | 成分種別の番号だけ対応表で直す |
| ロケーターバー／tracked_waypoint | tracked_waypoint（0x88）は773・774にしか無い（773は0x88）。属性 waypoint_transmit_range／waypoint_receive_range（33／34）と連動する | **773⇔774ではそのまま通す**（番号は同じ 0x88）。772以下のクライアント向けに落とす。774同士は素通し（リレー） |
| ダイアログ（771・772・773・774の要素） | show_dialog（0x8A）・clear_dialog（0x89、プレイ）、show_dialog（0x12）・clear_dialog（0x11、設定フェーズ）。設定フェーズのサーバー行き custom_click_action（0x08）とプレイの custom_click_action（0x41）も771・772・773・774にある | **771⇔772⇔773⇔774ではそのまま通す**（番号は同じ）。770以下のクライアント・サーバー向けは落とす。774サーバーへは Custom Click Action を通す |
| Change Gamemode | change_gamemode（0x04、サーバー行き）は771・772・773・774にある | **773⇔774ではそのまま通す**。770以下のサーバー向けに落とす。774サーバーへは通す |

検証スクリプトは `detergentmc-protocol/tools/verify-774-tables.py`
（773段階の `verify-773-tables.py` を写したもの）。
本物のバニラ1.21.11から Registry Data の順番をそのまま記録する統合テスト
（`RegistryOrderCaptureTest.registryOrderOfRealVanilla12111`）で
エンチャント lunge=21・ダメージ種別 spear=37 等を確認した。

**エンティティのレジストリ順序（773→774の既知の制限）：**
`PlayTranslator` の `registryIdMapper`／`registryTargetNames` が並べ替えを
扱うのは enchantment、painting_variant、banner_pattern、trim_pattern、
jukebox_song、damage_type の6レジストリのみで、entity_type、particle_type、
block_entity_type、biome、block、item、sound 等は素通しする。そのため
773サーバーから来た153件（pig=97）のまま774クライアントに届く。
Spawn Entity の種別ID自体は774へ翻訳される（pig=100）ため、実機クライアントでは
エンティティの表示がずれる可能性がある。773専任の範囲では直さない。
`PlayTranslator774Test` に素通しを明記するテストを入れてある。同じ問題は
エンティティ順が変わる既存のペア（767→768、769→770、770→771、772→773）にもある。

**Code of Conduct（774）：**
773と同じ扱い。実バニラ1.21.11は既定のオフラインサーバーでは Code of Conduct
（設定 0x13）を送ってこない（`ProxyIntegrationTest.fake774ClientJoinsRealVanilla12111ThroughProxy`
で参加まで進んだ。`Fake774Client` は 0x13 が来たら Accept（0x09、空）を返す備えがある）。
774クライアントが Accept（0x09）を送ったときは、プロキシは774サーバーへ
そのまま通す（`ProxySession.handleClientConfiguration774` に 0x09 分岐を
追加。旧サーバー向けは落とす）。CoC を送るサーバーに古いクライアントが
入った場合、プロキシは CoC を落とすだけで accept を代理で返さないため、
サーバーが accept を待つ設計なら設定フェーズで止まる（未確認の制限。
`Outcome` にサーバーへの合成送信が無いため、この段階では直していない）。

**773→774の連鎖の方針：** 774クライアント→773サーバーの変換は、
774→773へ直してから既存の `handleClientPlay773` に渡す二段方式。
ただし `handleClientPlay773` はクライアントが773であることを前提に
書かれているため、内部773サーバーのときだけは774→773へ直した
パケットを直接バニラへ送り、それ以外（内部763／764／765／766／767／768／769／770／771／772）の
ときだけ `handleClientPlay773` に渡す。同じ罠はサーバー→クライアント側にも
あり、773サーバーから774クライアントへの Join Game／Respawn／Disconnect は
774向けの分岐を `handleServerPlayFrom773`／`handleServerJoinGame773` に
追加してある。767／766サーバーから774クライアントへは 767→768→769→770→771→772→773→774 の
七段・八段で変換する（`handleServerPlayFrom766` の is774() 分岐が内部766・767の両方を扱う。
`handleServerPlayFrom767` は `handleServerPlayFrom766` に委譲する）。
763サーバーから774クライアントへは、合成される Join Game を768形式（769／770／771／772／773／774と同形式、
番号 0x30）で作る（`handleSynthesizedJoinGame` の分岐。**774を漏らすと765形式の Join Game
（0x29）が774クライアントに届く**。772段階の漏れ修正（8fa47ab）の774版として
直してある）。
設定フェーズの辞書は766形式で作り（`PlayTranslator.buildRegistryData766`）、
774クライアント向けには766→767→768→769→770→771→772→773→774へ八段で並べ替え、
773クライアント向けには七段で並べ替える（`sendSynthesizedConfigIfReady`）。

**設定フェーズの振り分けの注意：** 774クライアント→772サーバーの設定フェーズは、
専用の `ProxySession.handleServerConfiguration774From772`
（Registry Data の772→773→774二段並べ替え・Tags 落とし・Cookie／Select Known Packs の
空応答・dialog 素通し）を通す。774クライアント→771サーバーは
`handleServerConfiguration774From771`（771→772→773→774三段）を通す。
振り分けで `is772()` の分岐だけを見ていると、772サーバーの Registry Data が
774クライアントに772形式のまま届く。774→773の設定フェーズは、dialog（0x11／0x12）が
772・773・774とも同ID・同形式のためそのまま通す。設定フェーズの Custom Click Action
0x08 も773・774とも同じためそのまま通す。

**774パッチを写すときの罠（774段階で実際に踏んだもの）：**
- `shift()` は 773→774 しか写さない。**生成器が772→773の変更説明を773→774の
  変更だと読み違える罠**。`gen-774-translators.py` の初版は、773節のコメントを
  版番号だけで写したため、KIND 表の注記・パーティクル節（copper_fire_flame は
  773・774とも5なのに「773で5に挿入」と書いた）・メタデータの型番号（773と774で
  28番以降が+1ずれるのに773の番号のまま）・jukebox／dialog フィルタの版ずれ
  （772773→773774、773772→774773 の一段ずれ）など、系統的な誤りを含んでいた。
  773節の本文を `shift()` で機械的にずらすのではなく、**773→774の差分表
  （vv-1.21.11.json・mc-1.21.11-protocol.json・enchantments-1.21.11.json）から
  一文ずつ確かめて書き直す**こと。監査で直したのは `tools/gen-774-translators.py`
  の CHUNK_STRING_FIXES／CHUNK_METHOD_REPLACEMENTS／PLAY_774_SECTION 全体
  （コミット 70ad2d6）。
- **生成器が多段変換の途中の段を落とす罠（774段階でも再発）。**
  生成器は773の分岐を版番号だけで写すため、新しい版への一段目の変換を
  丸ごと落とす。`ProxySession` の is774() 分岐では次のものが落ちていた
  （コミット be6978d で手書きの `tools/fix-774-proxysession-live.py` により直した）。
  - `handleServerPlay772To774`／`handleServerPlay771To774`（手書きメソッド）が
    無かった。`handleServerPlayFrom772`／`From771` の is774() 分岐が呼んでいた。
  - `handleServerConfiguration774From772`／`774From771`／`771From774`／`770From774`／
    `772From774`（手書きメソッド）が無かった。is774() の設定分岐が
    `handleServerConfiguration774From773` を呼ぶ場所すら無く、
    is772() 分岐は `773From774` を誤って呼び、is773() 分岐は isServer774() を
    欠き、is772() 分岐は `if (isServer773())` の重複ブロックを持っていた。
  - `handleServerPlayFrom774` のディスパッチIDが772時代のままだった
    （Start Configuration 0x6F→0x74、Join Game 0x2B→0x30、Spawn Position
    0x5A→0x5F、Keep Alive 0x26→0x2B、Ping 0x36→0x3B、Disconnect 0x1C→0x20、
    Respawn 0x4B→0x50、Add Resource Pack 0x4A→0x4F）。774は773と同一IDのため、
    live の773メソッド（`handleServerPlayFrom773`）から再建した。
    From774 の末尾には is772／is771／is770／is769／is768 クライアント向けの
    変換チェーンも無かったため追加した。
  - `handleServerPlayFrom770` の is772() 分岐が一段短い（`handleServerPlay771To772`
    を770形式に直接当てる。773報告にもあった既存の不具合）を、774作業で
    `handleServerPlay770To771` 経由の二段に直した。
  - `handleServerPlayFrom766` の Disconnect／Respawn に 770／771／772／773／774
    クライアント向けの分岐が無く、フォールバック（765形式 0x1B／0x45）に
    落ちていた。773／774向け（0x20／0x50）と770／771／772向け（0x1C／0x4B）を追加した。
  - `handleServerConfiguration770From772` が無く、is770() 分岐が `771From772` を
    呼んで一段短かった。
  - Disconnect／Respawn の 770 の扱い：766〜769 と同じグループに入れて
    Disconnect 0x1D／Respawn 0x4C にしていたが、**770 は Disconnect 0x1C・
    Respawn 0x4B が正しい**（`PacketIds` と `mc-1.21.5-protocol.json` で確認）。
    From774／From773／From772／From771 の4か所を直した。
  - `reorderRegistries` の target 定数が 769From772／769From773／769From774 で
    間違っていた（V1_21_5／V1_21_6／V1_21_7 のはずが V1_21_4＝769）。
    `handleClientConfiguration774` の条件に `isServer772()` の重複があった。
  - **773報告の誤り訂正**：773報告の「From769／768のRespawnでis770()→0x4B
    （正しくは0x4C）」は誤り。770 Respawn は 0x4B が正しいため、From769／768
    の 0x4B は直さないこと。
  - なお、古いメソッドの doc コメントの「五段」等の段数表記は系統的に緩い
    （774From768 は六段の連鎖なのに「五段」と書いてある）。段数はコメントで
    なく translate 呼び出しの回数で数えること。
- **registryTargetNames の jukebox_song に 774 が無いと NPE になる罠。**
  `registryIdMapper` には 773774／774773 の jukebox 対応があっても、
  `registryTargetNames` の jukebox_song に `case 774` が無く、
  `reorderRegistryEntries` で `targetNames` が null になり NPE になった。
  773・774で曲は同一（21件）のため、773 の表
  （`RegistryTables773.JUKEBOX_SONG_773_NAMES`）を 774 にも使う。
  `PlayTranslator774Test.registryData773To774JukeboxSongIdentity` で発見し、
  `PlayTranslator.java` と生成器の両方を直した（コミット 5a17f8c）。
  774クライアント→773サーバー（逆も）の参加で、設定フェーズの Registry Data
  に jukebox_song が含まれるとプロキシが NPE で落ちていた。
- **Bedrock 側の javadoc だけ 774 済みで条件が漏れる罠。**
  `BedrockEntityTranslator.parseUpdateAttributes` は javadoc に 774 と
  書いてあったが、属性キーの番号分岐と modifier UUID の文字列分岐に 774 が
  無く、774サーバーからの Update Attributes を誤って読んでいた。
  `JavaBackendConnection774Test` の2件の失敗で発覚し、3か所を直した
  （コミット fabf899）。
- **Join Game の `is773()` 分岐に 774 が無い罠（統合テストで発覚）。**
  `handleServerJoinGame766`／`handleServerJoinGame763`／汎用
  `handleServerJoinGame`（内部764／765）／`handleSynthesizedJoinGame`
  （内部763の合成設定フェーズ）の4か所に `is774()` 分岐が無く、774クライアントが
  764／765向けの 0x29（765形式）に落ちていた。バニラ側は参加済みと表示するのに、
  偽774クライアントが Join Game 0x30 を待ち続けてタイムアウトした
  （`fake774ClientJoinsRealVanilla121ThroughProxy`／
  `fake774ClientJoinsRealVanilla1201ThroughProxy` の失敗）。
  773と774の Join Game は番号（0x30）も形式も同一のため、`is773()` を
  `is773() || is774()` にするだけで直る（`tools/fix-774-joingame-chat.py`）。
  768／769／770／771／772／773の Join Game ハンドラには 774 分岐があった
  （patch-proxysession-774.py が追加済み）ため、漏れたのは763／764／765／766の
  4ハンドラだけだった。**Join Game のような専用ハンドラは、統合テストが無いと
  漏れに気づけない**。ClientAdapter の全組み合わせ判定テストや翻訳子の単体テストは
  通っていた（Join Game のクライアント別分岐は単体テストの範囲外だった）。
- **クライアント→サーバーのチャットに checksum を付け忘れる罠（統合テストで発覚）。**
  `handleClientPlayTo774` に PLAY_CHAT_MESSAGE／PLAY_CHAT_COMMAND_SIGNED の
  分岐が無く、既定分岐が番号だけ 0x06→0x08 に付け替えて素通ししていた。
  770から付いた末尾の checksum（u8）が無いため、774バニラがチャットを
  読み取れず、チャットが届かなかった
  （`fake767ClientJoinsRealVanilla12111ThroughProxy`／
  `fake763ClientJoinsRealVanilla12111ThroughProxy` の失敗。参加・スポーンは通っていた）。
  763〜769クライアントには `appendChatChecksum770` で 0x00 を付けて送る
  （`tools/fix-774-joingame-chat.py`）。同じ欠けは `handleClientPlayTo773` にも
  ある（767／763クライアント→773サーバーのチャットも同じ理屈で届かないはず・
  未検証）。774専任のため773側は直さず、制限一覧に記録する。
- **patch-proxysession-774.py には `--check` 分岐が無い。**
  `--check` 付きで実行するとそのまま適用される。パッチスクリプトの
  `--check` 挙動を鵜呑みにせず、適用前に必ず `git diff` で内容を確認すること
  （773段階の `patch-proxysession-773.py` も同じ罠があった）。
- **shift() が写さないもの。** pack_format数値（リソース 69→75、データ 88→94）は
  明示的に直す。`ResourcePackBuilder.java` の 773 の javadoc に残っていた
  「pack_format 64」のコピペ誤り2か所も 774作業で直した。
  `PACK_FORMAT_1_21_11`（アンダースコア形）と `sha1774`（大文字Sも）は
  shift 対象外。
- **difflib の replace が鎖の末尾を置き換えて版を落とす罠。**
  773段階と同じ。鎖の edit は手書きで「末尾に774行を足す」こと。純粋な append
  （new773.startswith(old773)）の場合は suffix だけを shift するのが最も安全。
- **`optvarint` は `varint` の別名。** minecraft-data の型名
  `optional_unsigned_int`／`optional_block_state` は線形ではただの varint
  で、bool 接頭辞は無い。メタデータの型番号を読むときは protocol.json の
  `entityMetadataEntry` の番号表で確認すること（773：16=particle、17=particles、
  18=villager_data、19=optional_unsigned_int、28=optional_global_pos、29=painting、
  30=sniffer、31=armadillo、32=copper_golem_state、33=weathering、34=vector3、
  35=quaternion、36=resolvable_profile。774は zombie_nautilus_variant を28に挿入し
  humanoid_arm を38に追加）。

## 接続の流れ

773の流れ（`docs/PROXY-1.21.9.md` の「接続の流れ」）と同じ。774はパケットIDが
773と同一のため、ハンドシェイク／ログイン／設定フェーズ／プレイの番号は
773の表で読む。違うのは次の3点だけである。

- **プレイのクライアント行きIDは773と同一**：Join Game 0x30、Chunk Data 0x2C、
  Keep Alive 0x2B、Ping 0x3B、Start Configuration 0x74、Disconnect 0x20、
  Respawn 0x50、Add Resource Pack 0x4F、Spawn Position 0x5F、Player Info Update 0x44。
  設定フェーズ・ログインのIDも773と同じ（Code of Conduct 0x13／Accept 0x09）。
- **設定フェーズの Registry Data の並べ替えが八段になる**：774クライアント向けは
  773の七段（767→768→769→770→771→772→773）に774（773→774）を足した八段で並べ替える。
  773クライアント向けは七段のまま。
- **774→772／771の設定フェーズは専用ハンドラ**：`handleServerConfiguration774From772`
  （Registry Data の772→773→774二段並べ替え。Tags 落とし、Cookie／Select Known Packs の
  空応答、dialog は素通し）、`handleServerConfiguration774From771`
  （771→772→773→774三段。dialog は素通し）。
- **ダイアログは771⇔772⇔773⇔774で通す**：設定フェーズの Show Dialog（0x12）／Clear Dialog（0x11）、
  プレイの Show Dialog（0x8A）／Clear Dialog（0x89）、Tracked Waypoint（0x88）、
  Change Gamemode（0x04）、Custom Click Action（プレイ 0x41・設定 0x08）は
  771⇔772⇔773⇔774でそのまま通す。770以下向けだけ落とす。Tracked Waypoint だけは
  773・774にしか無いため772向けも落とす。

## プレイ中の翻訳

773の翻訳（`docs/PROXY-1.21.9.md` の「プレイ中の翻訳」）と同じ。774はパケットIDが
773と同一のため、番号の付け替えは不要で、ペイロードの付け替えだけがある。
付け替えるのは次のものである。

- **ブロック状態**：29,671件で同一のため付け替えなし。
- **アイテム**：music_disc.lava_chicken 1304→1316、槍7件・ノーチラスの防具5件・
  スポーンエッグ4件・ネザライトの馬鎧1件は774にしか無いため773向けに石（1）へ。
  oak_boat 863 は同一。末尾 1487→1504（+17）。
- **音**：774の新規67件は773向けに generic（block.stone.break 相当の代替）になる。
- **エンティティ**：camel_husk 20・nautilus 88・parched 97・zombie_nautilus 152 の
  挿入で pig 97→100、ghast 56→57、happy_ghast 57→58、falling_block 50→51、
  copper_golem 27→28、mannequin 82→83。774→773で新エンティティ4件はブタ（97）になる。
- **エンチャント**：lunge の挿入で mending 22→23、wind_burst 41→42。774→773で
  lunge は落とす。
- **ダメージ種別**：spear の挿入で spit 37→38、wither_skull 48→49。774→773で
  spear は落とす。
- **データコンポーネント**：KIND 表で 10→13（enchantments）、55→62（jukebox_playable）
  等に付け替える。774の新規8種は773向けに落とす。
- **エンティティメタデータ**：型番号を28番以降+1に付け替える。zombie_nautilus_variant（28）と
  humanoid_arm（38）はエントリごと落とす。painting は773=29→774=30。
- **パーティクル**：115件で同じ。
- **絵画**：51件で同じ（dennis=14、kebab=24、pointer=33、pond=34、unpacked=44、wither=50）。
- **ジュークボックスの歌**：21件で同じ。

それ以外（バイオーム・属性・メニュー・ブロックエンティティ種別）は773⇔774で
identity である。

## 送らない・落とすパケット（774向け）

| パケット | 向き | 理由 |
| --- | --- | --- |
| Window Click（0x11） | 774→773／773→774／774→772以下 | HashedSlot のハッシュ算法が未検証のため、全方向で落とす。773⇔774でも落とす |
| Set Creative Slot（0x37） | 774→763／764／765 | UntrustedSlot はデータコンポーネント形式のため、763／764／765向けに変換できず落とす。773⇔774・774⇔772／771／770／769／768／767／766では変換できる |
| Tracked Waypoint（0x88） | 774→772以下 | 773・774にしか無いため。773⇔774では通す |
| Show Dialog（0x8A）／Clear Dialog（0x89） | 774→770以下 | 771・772・773・774にしか無いため。771⇔772⇔773⇔774では通す |
| Show Dialog（設定 0x12）／Clear Dialog（設定 0x11） | 774→770以下 | 771・772・773・774にしか無いため。771⇔772⇔773⇔774では通す |
| Change Gamemode（0x04） | 774→770以下 | 771・772・773・774にしか無いため。773⇔774では通す |
| Custom Click Action（プレイ 0x41・設定 0x08） | 774→770以下 | 771・772・773・774にしか無いため。773⇔774では通す |
| dialog レジストリ（minecraft:dialog） | 774→770以下 | 770以下には無いため落とす。771⇔772⇔773⇔774では通す |
| Code of Conduct（設定 0x13） | 774サーバー→773以下 | 773以下には無いため落とす。774同士では通す（既定のサーバーは送ってこない） |
| デバッグ系パケット（DEBUG_BLOCK_VALUE 0x1a／DEBUG_CHUNK_VALUE 0x1b／DEBUG_ENTITY_VALUE 0x1c／DEBUG_EVENT 0x1d／GAME_TEST_HIGHLIGHT_POS 0x27／DEBUG_SUBSCRIPTION_REQUEST 0x16） | 774→772以下 | 773・774にしか無いため落とす。773⇔774では通す |
| Declare Recipes／Statistics／Tags／World Event ID | 全方向 | 未対応（773と同じ） |
| crafter／trial_spawner（Block Entity Data） | 全方向 | パケットごと落とす（773と同じ） |
| vault（Block Entity Data） | 766→765方向 | 773と同じ |
| Entity Effect の BreathOfTheNautilus（39） | 774→773以下 | エフェクトIDを付け替えず素通しするため、773クライアントには39がそのまま届く。773クライアントは39を知らない |
| use_effects／minimum_attack_charge／damage_type／attack_range／piercing_weapon／kinetic_weapon／swing_animation／zombie_nautilus/variant（データコンポーネント） | 774→773以下 | 774にしか無い成分種別のため落とす |
| zombie_nautilus_variant／humanoid_arm（Entity Metadata） | 774→773以下 | 774にしか無いメタデータ型のためエントリごと落とす |
| 槍・ノーチラスの防具・スポーンエッグ・ネザライトの馬鎧（アイテム） | 774→773以下 | 774にしか無いアイテムのため石（1）に落とす |
| camel_husk／nautilus／parched／zombie_nautilus（エンティティ） | 774→773以下 | 774にしか無いエンティティのためブタ（97）に落とす |

## 対象外・未実装（正直な一覧）

773の「対象外・未実装」と同じものに加え、774では次のものが残る。

- **Window Click（774が関わる全方向）**：773⇔774でも落とす。774クライアントが内部774以外のサーバーにいるとき、774サーバーにいる773以下のクライアントは、インベントリ操作（クリック）ができない。Set Creative Slot は UntrustedSlot→Slot で変換できる（774⇔773／772／771／770／769／768／767／766）。
- **Set Creative Slot（774クライアント→内部763／764／765）**：774の UntrustedSlot はデータコンポーネント形式のため変換できず落とす（773と同じ制限）。
- **ロケーターバー（774→772以下）**：773・774にしか無いため、772以下のクライアント・サーバーには送らない。**ダイアログ・Change Gamemode・Custom Click Action（774→770以下）**：771・772・773・774にしか無いため、770以下のクライアント・サーバーには送らない。
- **エンティティのレジストリ順序（773→774）**：Registry Data の entity_type は素通しするため、773サーバーから来た153件（pig=97）のまま774クライアントに届く。Spawn Entity の種別ID自体は774へ翻訳される（pig=100）ため、実機クライアントではエンティティの表示がずれる可能性がある。774の範囲では直さない（上の「エンティティのレジストリ順序」を参照）。
- **槍・ノーチラスの防具・スポーンエッグ・ネザライトの馬鎧（774→773）**：774にしか無い17件のアイテムは773向けに石に見える。camel_husk／nautilus／parched／zombie_nautilus はブタに見える。use_effects 等の新規8成分と zombie_nautilus_variant／humanoid_arm のメタデータは落とす。
- **Entity Effect の BreathOfTheNautilus（774→773）**：エフェクトIDを付け替えず素通しするため、774→773で 39 がそのまま773クライアントに届く。773クライアントは39を知らない。逆向き（773→774）は 0–38 が同一のため問題ない。
- **1.21.11の新要素で通せないもの（774サーバーに773以下のクライアントがいる場合）**：槍・ノーチラスの防具は773以下のクライアントでは石に見える（アイテムのフォールバック）。camel_husk／nautilus／parched／zombie_nautilus はブタに見える。新規8成分は落とす。ロケーターバーは772以下のクライアントでは表示されない。ダイアログ・Change Gamemode・Custom Click Action は770以下のクライアントでは表示されない・届かない（パケットを落とす）。
- **Code of Conduct（774）**：既定のサーバーは送ってこないため通常は問題にならない。CoC を送るサーバーに古いクライアントが入った場合、プロキシは CoC を落とすだけで accept を代理で返さないため、サーバーが accept を待つ設計なら設定フェーズで止まる（未確認の制限）。
- **772⇔773の translateComponent の case 16 の取り違え（既存の不具合）**：`translateComponent772To773`／`773To772` の case 16 は `enchantment_glint_override`（bool）だと書いてあるが、773の kind 16 は repair_cost（varint）で 18 が glint（bool）である。repair_cost が2以上のアイテムで破損する可能性がある。774節（`translateComponent773To774`／`774To773`）では直してある。772⇔773は774の範囲外のため直していない。
- **773→772のメタデータの型番号（既存の不具合）**：`mapMetadataType773To772` は恒等で、`translateMetadataValue773To772` は772の番号で読むため、773の型16番以降がずれる。774の範囲外のため直していない。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：764／765／766／767／768／769／770／771／772／773／774＋online-modeも同じ制限（内部バニラがoffline-modeのため Player Info Updateにプロパティを載せない）。
- **本物のゲームクライアントでの確認は未実施**：全144通り共通。手順書は `docs/PLAYTEST-1.21.11.md`。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの検証のみ。
- **26.1以降（775〜）は未対応**：プロキシは774より新しいプロトコルをログイン時に断る。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みはあるが、パックを置く場所はまだ決まっていない。既定はloopback配信。

774で直した既存の不具合（773報告にあったもの）：
- `handleServerPlayFrom770` の is772() 分岐が一段短い → `handleServerPlay770To771` 経由の二段に直した。
- `handleServerPlayFrom766` の Disconnect／Respawn が770／771／772／773／774クライアントでフォールバックに落ちる → 773／774向け（0x20／0x50）と770／771／772向け（0x1C／0x4B）を追加した。
- `handleServerConfiguration770From772` が無く、is770() 分岐が `771From772` を呼んで一段短かった → 新設した。
- `reorderRegistries` の target 定数が 769From772／769From773／769From774 で間違っていた（V1_21_4＝769 に直した）。
- `handleClientConfiguration774` の条件の `isServer772()` の重複を直した。
- Disconnect／Respawn の 770 の扱い（0x1D／0x4C ではなく 0x1C／0x4B）を4か所で直した。
- 773報告の「From769／768のRespawnでis770()→0x4B（正しくは0x4C）」は誤りだったため、直さない（0x4B が正しい）。

## Java 21（内部774サーバーを動かすとき）

773と同じ。バニラ1.21.11は Java 21 が必要で、DetergentMC自体は Java 17 で
動く。`VanillaServerHost` は `detergentmc.java21.home` システムプロパティ、
`JAVA_HOME`、`java.home`、既定の場所の順に Java 21 を探す。統合テストは
`Java21TestSupport.ensureJava21Home()` で Java 21 を確認してから起動する。
1.21.11のjarのSHA-1は `64bb6d763bed0a9f1d632ec347938594144943ed`（piston-meta確認済み、
`run/vanilla/server-1.21.11.jar`）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.21.11.md` を参照。

## テスト

- `ClientAdapter774Test`（ClientAdapter の774受け入れ・REJECT_TOO_NEWを775へ・
  `differences(774)`）
- `ClientJoinFlow774Test`（設定フェーズの有無。774は773と同じく設定フェーズあり）
- `ChunkTranslator774Test`（14件。チャンクの同一、アイテムの槍の挿入・
  新規アイテムの石への代替、成分の KIND ずれと新規8種の落とし、
  メタデータ型のずれと新規2種の落とし、Spawn Entity の pig 97→100・
  nautilus のブタ代替）
- `PlayTranslator774Test`（13件。Registry Data のエンチャント lunge の挿入・
  ダメージ種別 spear の挿入・絵画／曲／バイオームの同一・
  エンティティの素通し、Remapper の抜き打ち）
- `RegistryRemapper774Test`（773→774の対応表と逆向き）
- `PacketIds774Test`／`ProtocolVersion774Test`（パケットIDの0変更・プロトコル番号）
- `ResourcePackBuilder774Test`（774用パック pack_format 75）
- `ResourcePackMode774Test`／`ResourcePackServer774Test`（url-774／sha1-774、12パック配信）
- `ResourcePackWiringTest`（12パック化）
- `JavaBackendConnection774Test`（Bedrock→内部774。参加・Player Info・
  チャット checksum・属性・スロット・ブロック状態・エンティティ・
  エフェクト・Spawn Position・Chunk Batch Ack）
- `ProxyIntegrationTest` の774関係5テスト（774→12111、774→121、767→12111、
  763→12111、774→1201。いずれも参加・スポーン・チャット・移動まで確認）
- `RegistryOrderCaptureTest.registryOrderOfRealVanilla12111`
  （実バニラ1.21.11の Registry Data の順番。enchantment lunge=21・
  damage spear=37 等を確認）
- `BedrockMatrixIntegrationTest.matrixAgainst1_21_11`
  （Bedrock 6 プロトコル × 内部774。参加・スポーン・チャット・移動・チャンク・アイテム）
- `BedrockMatrixIntegrationTest.matrixAgainst1_21_9`／`matrixAgainst1_21_10` も
  774のビルドで PASSED（773のビルドでは AssertionFailedError で失敗していた。
  Bedrock→773の統合テストが通っていないという773の未解決は、774のビルドで解消）

テスト総数は最終ビルドで確定する（`./gradlew clean build` の結果を参照）。
