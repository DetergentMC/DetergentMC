# 1.21.7／1.21.8クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.21.7／1.21.8クライアント（プロトコル772）は、内部プロトコルが
763／764／765／766／767／768／769／770／771／772のどのサーバーにも参加できます。内部772のときは
素通し（リレー）、それ以外は上向き・下向き変換です。771との違いは小さく、
**パケットIDは247件すべて同一で、レジストリはアイテム・音・絵画・
ジュークボックスの歌が1件ずつ増えるだけ**です。チャンクの形式・チャットの
checksum・Player Chat の globalIndex・スロットの成分種別・属性表は771と
まったく同じです。いちばんの変化は `music_disc_lava_chicken`・`dennis`・
`lava_chicken` の付け替えと、**ダイアログが771にもあったことの訂正**
（771段階の前提「ダイアログは771に無い」は誤りで、771⇔772ではダイアログも
ロケーターバーも Change Gamemode も Custom Click Action もそのまま通す）です。
771の仕組みは `docs/PROXY-1.21.6.md`、770は `docs/PROXY-1.21.5.md`、769は
`docs/PROXY-1.21.4.md`、768は `docs/PROXY-1.21.2.md`、767は `docs/PROXY-1.21.md`、766は
`docs/PROXY-1.20.5.md`、逆向きの考え方は `docs/PROXY-REVERSE.md` を
参照してください。

全100通り（クライアント763／764／765／766／767／768／769／770／771／772 × サーバー763／764／765／
766／767／768／769／770／771／772）の対応マトリクスは `docs/VERSIONS.md` の「1.21.7対応：
全100通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用・767用・768用・769用・770用・771用・772用の10つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 771→772の差分（要点）

| 分野 | 771→772の差分 | 扱い |
| --- | --- | --- |
| パケットID（クライアント行き） | **変わらない**。Join Game 0x2B、Spawn Position 0x5A、Keep Alive 0x26、Ping 0x36、Start Configuration 0x6F、Disconnect 0x1C、Respawn 0x4B、Chunk Data 0x27、Player Chat 0x3A、Add Resource Pack 0x4A。設定フェーズ・ログインのIDも771と同じ | 番号付け替えは不要。プレイのクライアント行きは771形式のまま通す（`to772ServerPacket` はペイロード変換だけをする） |
| パケットID（サーバー行き） | **変わらない**。Chat Message 0x08、Chat Command Signed 0x07、Chat Command 0x06、Window Click 0x11、Set Creative Slot 0x37、Vehicle Move 0x21、Keep Alive 応答 0x1B、Ping 応答 0x2C、Pick Item From Block 0x23／From Entity 0x24、Player Loaded 0x2B、Set Test Block 0x3A、Set Player Position 0x1D。Change Gamemode 0x04、Custom Click Action 0x41も同じ | 番号付け替えは不要。汎用の `PacketTranslator.translateId` は同IDをそのまま通す。772同士は素通し |
| 新規パケット | **無い**。771→772で新規・削除・ID変更のパケットは無い（247件すべて同一） | 771の流用で済む。`ClientAdapter.differences(772)` は「新規・削除・ID変更のパケットは無い」と返す |
| Login Success | 771と同じく末尾の `strictErrorHandling` bool が無い（765と同じ形式） | そのまま通す。767向けは `false` を付け足す（771と同じ） |
| Join Game／Respawn | 771と772で形式は同じ（SpawnInfo の末尾の `seaLevel` varint を含む）。番号も同じ（Join Game 0x2B、Respawn 0x4B） | 番号だけ付け替える必要も無い。767向けは seaLevel（63）を落とす（771と同じ） |
| Player Chat（クライアント行き） | 先頭に `globalIndex` varint があるのは771・772とも同じ | 素通しする（771→772・772→771とも globalIndex を付けも剥がしもしない）。カウンタは `ProxySession.chatGlobalIndex` を771／772で共用する（セッションごと0から数える。770サーバーからの player_chat に付ける） |
| Chat Message／Chat Command Signed（サーバー行き） | 末尾の `checksum` u8 があるのは771・772とも同じ | 剥がしも付け足しも不要。771→772・772→771とも番号は同じ（Chat Message 0x08）。**772の checksum 0x00 は実バニラ1.21.7／1.21.8に受け入れられることを統合テストで確認済み**（`ProxyIntegrationTest.fake772ClientJoinsRealVanilla1217ThroughProxy`／`fake772ClientJoinsRealVanilla1218ThroughProxy`。771段階では1.21.6の実バニラプローブで事前確認していたが、772では事前プローブはせず統合テスト自体が検証になっている） |
| Heightmap（Chunk Data） | 771と同じ直接の配列（varint 個数＋{type varint、data long[]}）。type id の順序は771と同じと仮定する（0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、3=OCEAN_FLOOR、4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES） | 771・772とも直接配列のまま写す（NBT↔配列の変換は771⇔772では不要）。`ChunkTranslator.translateChunkData771To772`／`772To771` |
| チャンクセクションの Data Array（Chunk Data） | **771と同じ**（長さの varint 接頭辞なし。長さは bitsPerEntry とエントリ数から計算する）。シングルパレット（bpe=0）も長さ varint（0）が無い | 771・772とも同じ読み方で通す |
| Window Click | HashedSlot のまま（771と同じ） | **変換できず落とす**（771と同じ。ハッシュ算法が未検証のため）。771⇔772でも落とす。`ChunkTranslator.translateWindowClick771To772`／`772To771` は `UnsupportedOperationException` を投げる |
| Set Creative Slot | UntrustedSlot のまま（771と同じ）。アイテムID・成分種別の付け替えだけで変換できる（`{itemCount varint、itemId、addedComponentCount、removedComponentCount、components[{type、data ByteArray}]、removeComponents[{type}]}`） | `ChunkTranslator.translateSetCreativeSlot771To772`／`772To771` で変換する。771・772とも `tooltip_display` は種別15で同一のため特別処理は不要。music_disc_lava_chicken（772の1256）は771向けに石（1）になる |
| スロット（データコンポーネント） | 種別は771・772とも96件・同じ番号のため、種別番号の対応表は恒等表。`unbreakable` は771・772とも void、`dyed_color` は771・772とも色 i32 のみ、`enchantments`／`attribute_modifiers`／`trim`／`tool`／`instrument`／`jukebox_playable` は771・772で同じ形式 | 種別番号は恒等表で変換する。成分の中身は対応表で番号を直す（アイテム・エンチャント・属性・音・ジュークボックスの歌）。772にしかない種別は無い（96件とも両方にある）ため、成分を落とすことは無い。`jukebox_playable` は holder>0 のとき曲IDを付け替える（lava_chicken は771向けに曲ID 0（"11"）で代用する） |
| Entity Metadata | 種別番号は771・772とも同じ（23=cow_variant、24=wolf_variant（素の varint）、25=wolf_sound_variant、26=frog_variant、27=pig_variant、28=chicken_variant、29=optional_global_pos、30=painting_variant、31=sniffer_state、32=armadillo_state、33=vector3、34=quaternion）。wolf_variant は771・772ともただの varint（Holderではない）ため素通し。エンティティ種別は771・772とも151件で同じ | 種別番号は恒等。エンティティ種別だけ対応表で直す（実質 identity。happy_ghast は771・772とも56）。**絵画 variant だけが例外**：dennis（772の14）は771に無いため、772→771では painting_variant のメタデータエントリごと落とす。771→772では 14 以降が +1 ずれる |
| World Particles | 771・772で同一（tinted_leaves 35、trail 48、block_crumble 112も両方にある。粒子データの case 番号は771側のID） | 対応表で変換する（実質 identity） |
| ブロック状態 | 27,946件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| アイテム | 1,415→1,416件（+1、−0）。`music_disc_lava_chicken` が772の1256に挿入 | 対応表で変換。772にしかないアイテムは古いサーバー向けに石へ。771の oak_boat 827 は772でも827（変わらない） |
| エンティティ | 151件で同じ顔ぶれ・同じ順番。`happy_ghast` は771・772とも56 | 変換なし（identity）。Bedrock の正規化では happy_ghast はブタ代替になる（合成表のため。別記） |
| 音 | 1,727→1,728件（+1、−0）。`music_disc.lava_chicken` が772の939に挿入 | 対応表で変換。772→771で music_disc.lava_chicken（939）は **block.stone.break（771の1441）に付け替わる**（数値表の OVERRIDES）。771→772では 939 以降が +1 ずれる |
| パーティクル | 114件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 属性 | 35件で同じ顔ぶれ・同じ順番。`camera_distance` は771・772とも8番、`generic.movement_speed` は771・772とも22 | 変換なし（identity）。**注意：** 771節を写した当初は「camera_distance が 8番に挿入され、movement_speed が 21→22 になる」という記述が JavaBackendConnection772Test に入っていたが、771→772では属性表は変わらないため直してある |
| エンチャント | 42件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| バイオーム | 65件で同じ顔ぶれ・同じ順番（pale_garden は 39、plains は 40） | 変換なし（identity） |
| ブロックエンティティ種別 | 47件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 絵画 | 50→51件。`minecraft:dennis` が772の14番に挿入（kebab 24、wither 50）。**実バニラ1.21.7／1.21.8のキャプチャで確認済み**（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1217`／`registryOrderOfRealVanilla1218`） | 対応表で変換。772→771で dennis（14）は落とす（Registry Data の並べ替えでは落とし、painting_variant 成分では kebab（0）で代用し、Entity Metadata ではエントリごと落とす）。771→772で 14 以降は +1 ずれる |
| 旗の模様・トリム | 変わらず（旗43件・トリム18件） | 変換なし |
| ジュークボックスの歌 | 20→21件。`minecraft:lava_chicken` が772の9番に挿入（mall 10、tears 18、wait 19、ward 20）。**実バニラ1.21.7／1.21.8のキャプチャで確認済み**（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1217`／`registryOrderOfRealVanilla1218`） | 対応表で変換。772→771で lava_chicken（9）は落とす（Registry Data の並べ替えでは落とし、jukebox_playable 成分では曲ID 0（"11"）で代用する）。771→772で 9 以降は +1 ずれる |
| ダイアログ（レジストリ） | **3件で同じ**（custom_options 0、quick_actions 1、server_links 2）。**771段階の前提の訂正**：ダイアログは771にもある（設定フェーズ 0x12／0x11、プレイ 0x85／0x84）。771段階で「770向けに落とす」としたのは正しいが、「771には無い」は誤りだった。771⇔772では dialog レジストリはそのまま通す | 771→旧クライアントでは dialog レジストリを落とす（770以下には無いため）。770サーバーからは来ないため、772クライアントへ**合成注入はしない**（実バニラクライアントが欠けたレジストリを内蔵既定で補うかは未確認のため。771段階と同じ） |
| ダメージ種別 | 49件で同じ顔ぶれ・同じ順番。**実バニラ1.21.7／1.21.8のキャプチャで確認済み**（ender_pearl 8、mace_smash 26、magic 27、wither_skull 48） | 変換なし（identity） |
| メニュー | 25件で変わらず | 変換なし |
| Client Information（Settings） | 771と同じ9フィールド（末尾に `particleStatus` varint） | そのまま通す。**770のときの罠（768クライアント→769サーバーで particleStatus を落とす実バグ）と同じく、772サーバー向けは落としてはいけない**。771も772も同じ9フィールドのため、素通しする |
| Player Info | 771と同じ8ビット（update_hat／update_list_order を含む）。フィールド順は listPriority の後に showHat（minecraft-data に従った仮定。771と同じ） | そのまま通す（identity） |
| Set Held Item／Vehicle Move | 771と同じ形式。Vehicle Move は771・772とも末尾に onGround bool 付き | そのまま通す（identity） |
| Spawn Entity Experience Orb | 771・772とも専用パケットは無い（Spawn Entity に統合済み） | 経験値オーブの専用分岐は無い。771・772とも Spawn Entity（type experience_orb）で通す |
| Trade List | 771と同じ形式 | 成分種別の番号だけ対応表で直す（771・772とも96件で同じため実質 identity） |
| ロケーターバー／tracked_waypoint | tracked_waypoint（0x83）は771・772にしか無い。属性 waypoint_transmit_range／waypoint_receive_range（33／34）と連動する | **771⇔772ではそのまま通す**（771段階の「落とす」は誤り）。770以下のクライアント向けに落とす。772同士は素通し（リレー） |
| ダイアログ（771・772の要素） | show_dialog（0x85）・clear_dialog（0x84、プレイ）、show_dialog（0x12）・clear_dialog（0x11、設定フェーズ）。設定フェーズのサーバー行き custom_click_action（0x08）とプレイの custom_click_action（0x41）も771・772にある | **771⇔772ではそのまま通す**（771段階の「落とす」は誤り）。770以下のクライアント・サーバー向けは落とす。772サーバーへは Custom Click Action を通す（サーバーがダイアログのクリックを処理する）。772クライアント＋770サーバーではダイアログは表示されない |
| Change Gamemode | change_gamemode（0x04、サーバー行き）は771・772にある | **771⇔772ではそのまま通す**（771段階の「落とす」は誤り）。770以下のサーバー向けに落とす。772サーバーへは通す |
| エフェクト | Unluck→BadLuck の名前変更のみ（38件、番号は変わらない） | 変換なし（identity）。771の Unluck（番号は同じ）が772では BadLuck と呼ばれるだけ |

検証スクリプトは `detergentmc-protocol/tools/verify-772-tables.py`
（771段階の `verify-771-tables.py` を写したもの。blow_out の衝突は772には無い）。
本物のバニラ1.21.7／1.21.8から Registry Data の順番をそのまま記録する統合テスト
（`RegistryOrderCaptureTest` の1.21.7／1.21.8版）は**実施済み**（下の「テスト」を参照）。
実バニラが設定フェーズで送るレジストリは21レジストリで、771と同じ顔ぶれ。
`minecraft:attribute` と `minecraft:block_entity_type` は送られないため、
属性の順番とブロックエンティティ種別の順番は minecraft-data と jar の data ファイルに依る
（属性は実バニラキャプチャで35件を確認済み）。

**771→772の連鎖の方針：** 772クライアント→771サーバーの変換は、
772→771へ直してから既存の `handleClientPlay771` に渡す二段方式。
ただし `handleClientPlay771` はクライアントが771であることを前提に
書かれているため、内部771サーバーのときだけは772→771へ直した
パケットを直接バニラへ送り、それ以外（内部763／764／765／766／767／768／769／770）の
ときだけ `handleClientPlay771` に渡す。同じ罠はサーバー→クライアント側にも
あり、771サーバーから772クライアントへの Join Game／Respawn／Disconnect は
772向けの分岐を `handleServerPlayFrom771`／`handleServerJoinGame771` に
追加してある。767／766サーバーから772クライアントへは 767→768→769→770→771→772 の
六段・五段で変換する。763サーバーから772クライアントへは、合成される
Join Game を768形式（769／770／771／772と同形式、番号 0x2B）で作る
（`handleSynthesizedJoinGame` の分岐。**772を漏らすと765形式の Join Game
（0x29）が772クライアントに届く**。771段階の漏れ修正（8fa47ab）の772版として
直してある）。
設定フェーズの辞書は766形式で作り（`PlayTranslator.buildRegistryData766`）、
772クライアント向けには766→767→768→769→770→771→772へ六段で並べ替え、
771クライアント向けには五段で並べ替える（`sendSynthesizedConfigIfReady`）。

**設定フェーズの振り分けの注意：** 772クライアント→770サーバーの設定フェーズは、
専用の `ProxySession.handleServerConfiguration772From770`
（Registry Data の770→771→772二段並べ替え・Tags 落とし・Cookie／Select Known Packs の
空応答・dialog 落とし）を通す。振り分けで `is770()` の分岐（`isServer770()` の素通し）だけを
見ていると、770サーバーの Registry Data が772クライアントに770形式のまま届く。
772→771の設定フェーズは、dialog（0x11／0x12）が771・772とも同ID・同形式のため
そのまま通す（771段階の「落とす」は誤り。`PacketIds` で確認済み。設定フェーズの
Custom Click Action 0x08 も771・772とも同じためそのまま通す）。

**771段階の翻訳器を写すときの罠（772段階で実際に踏んだもの）：**
- `shift()` は 770→771 と 771→772 しか写さない（769以下は動かない）。
  771パッチの EDITS を写すとき、REPLACE 由来の項目はメソッド名の付け替えや
  分岐の置き換えになっており、手直ししないと分岐が消える（Join Game の
  is771() 分岐、Disconnect の is770() 分岐、合成設定フェーズの五段分岐が
  実際に消えかけた）。771版の `gen-772-proxysession.py` の FIX_EDITS_FULL／
  FIX_BLOCKS で直してある。
- **771段階のフォローアップ修正コミット（8fa47ab）を写し忘れる罠。**
  772の生成器は771の本体パッチ（cdceaf7）から写したため、8fa47ab で
  771に追加された分岐には772の対応物が無かった。初回のフルビルドの単体
  テスト5件の失敗で発覚し、すべて直してある（下の一覧）。次のバージョン
  （773）では、本体パッチだけでなく直前のフォローアップ修正コミットも
  写し元に含めること。見つかった漏れは次の5件である。
  - `handleClientConfiguration` の2つのガード：771サーバー向けは
    `!is772()` が無く772クライアントの Custom Click Action（設定 0x08）を
    落とし、772サーバー向けは `!is771()` が無く771クライアントの 0x08 を
    落としていた。`handleClientConfiguration771` の 0x08 分岐も
    `isServer771()` だけで772サーバー向けを落としていた。3か所とも
    771・772両方を許す形に直した。
  - `handleServerConfiguration772From771`：dialog（設定 0x11／0x12）の
    case が無く、771サーバーからの Show／Clear Dialog を落としていた
    （`handleServerConfiguration771From772` 側だけ直してあり、逆向きを
    見落としていた）。0x11／0x12 の素通しを追加した。
  - `handleServerPlayFrom772` の Respawn：771版にある is770()／
    is768()・is769() の 0x4C 分岐が無く、770／769／768クライアントに
    765形式の 0x45 を返していた（771段階の8fa47abで追加された分岐と
    同じ漏れ）。0x4C 分岐を2つ追加した。
  - `handleServerPlayFrom772` の末尾の委譲：is770()／is769()／is768() への
    委譲が無く、770／769／768クライアントには特別扱いの7パケット以外が
    すべて落とされていた。772→771へ直してから
    `handleServerPlay771To770`／`771To769`／`771To768` に渡す二段の委譲を
    追加した。
  - `ClientAdapter.differences(int, int)` の後半のクライアント一覧：
    `V1_21_7` が無く、`differences(772, 764)` が NOT_SUPPORTED を返して
    いた（前半の一覧と後続の分岐には772があった）。一覧に V1_21_7 を追加した。
- 歴史の入ったバージョン一覧（`V1_21_2` の並び、`pack769` の並び、
  `pack_format 63`／`55`）は shift で欠ける。772側は771の NEW を残したまま
  772要素を**追加**する形で手書きしてある（`gen-772-patches.py` の FIXES）。
- 771→772で変わらないはずのもの（属性表・Chat Message の番号・oak_boat の番号・
  ハッピーガストの番号）を「771段階の新要素」と読み違えて、コメントだけ
  771→772のずれだと書くミスが `JavaBackendConnection772Test` と
  `ProxySession772Test` のシフト生成で出た。直してある。

## 接続の流れ

771の流れ（`docs/PROXY-1.21.6.md` の「接続の流れ」）と同じ。772はパケットIDが
771と変わらないため、ハンドシェイク／ログイン／設定フェーズ／プレイの番号は
すべて771のまま読める。違うのは次の3点だけである。

- **設定フェーズの Registry Data の並べ替えが六段になる**：772クライアント向けは
  771の五段（767→768→769→770→771）に772（771→772）を足した六段で並べ替える。
  771クライアント向けは五段のまま。
- **772→770の設定フェーズは専用ハンドラ**：`handleServerConfiguration772From770`
  （Registry Data の770→771→772二段並べ替え。Tags 落とし、Cookie／Select Known Packs の
  空応答、dialog 落とし）。
- **ダイアログは771⇔772で通す**：設定フェーズの Show Dialog（0x12）／Clear Dialog（0x11）、
  プレイの Show Dialog（0x85）／Clear Dialog（0x84）、Tracked Waypoint（0x83）、
  Change Gamemode（0x04）、Custom Click Action（プレイ 0x41・設定 0x08）は
  771⇔772でそのまま通す。770以下向けだけ落とす。

## プレイ中の翻訳

771の翻訳（`docs/PROXY-1.21.6.md` の「プレイ中の翻訳」）と同じ。772はパケットIDが
771と変わらないため、番号の付け替えは無く、ペイロードの付け替えだけである。
付け替えるのは次のものだけである。

- **アイテム**：1256（music_disc_lava_chicken）の挿入で 1256 以降が +1 ずれる。
  772→771で 1256 は石（1）になる。
- **音**：939（music_disc.lava_chicken）の挿入で 939 以降が +1 ずれる。
  772→771で 939 は block.stone.break（1441）になる。
- **絵画**：14（dennis）の挿入で 14 以降が +1 ずれる。772→771で dennis は
  Registry Data では落とし、painting_variant 成分では kebab（0）で代用し、
  Entity Metadata ではエントリごと落とす。
- **ジュークボックスの歌**：9（lava_chicken）の挿入で 9 以降が +1 ずれる。
  772→771で lava_chicken は Registry Data では落とし、jukebox_playable 成分では
  曲ID 0（"11"）で代用する。
- **エフェクト**：Unluck→BadLuck の名前変更のみ。番号は変わらないため変換なし。

それ以外（ブロック状態・エンティティ・バイオーム・パーティクル・属性・
エンチャント・メニュー・データコンポーネント）は771⇔772で identity である。

## 送らない・落とすパケット（772向け）

| パケット | 向き | 理由 |
| --- | --- | --- |
| Window Click（0x11） | 772→771／771→772／772→770以下 | HashedSlot のハッシュ算法が未検証のため、全方向で落とす。771⇔772でも落とす |
| Set Creative Slot（0x37） | 772→763／764／765 | UntrustedSlot はデータコンポーネント形式のため、763／764／765向けに変換できず落とす。771⇔772・772⇔770／769／768／767／766では変換できる |
| Tracked Waypoint（0x83） | 772→770以下 | 771・772にしか無いため。771⇔772では通す |
| Show Dialog（0x85）／Clear Dialog（0x84） | 772→770以下 | 771・772にしか無いため。771⇔772では通す |
| Show Dialog（設定 0x12）／Clear Dialog（設定 0x11） | 772→770以下 | 771・772にしか無いため。771⇔772では通す |
| Change Gamemode（0x04） | 772→770以下 | 771・772にしか無いため。771⇔772では通す |
| Custom Click Action（プレイ 0x41・設定 0x08） | 772→770以下 | 771・772にしか無いため。771⇔772では通す |
| dialog レジストリ（minecraft:dialog） | 772→770以下 | 770以下には無いため落とす。771⇔772では通す |
| Declare Recipes／Statistics／Tags／World Event ID | 全方向 | 未対応（771と同じ） |
| crafter／trial_spawner（Block Entity Data） | 全方向 | パケットごと落とす（771と同じ） |
| vault（Block Entity Data） | 766→765方向 | 771と同じ |
| test_block／test_instance_block | 772→771以下 | 771・772にしか無いため落とす |

## 対象外・未実装（正直な一覧）

771の「対象外・未実装」と同じものに加え、772では次のものが残る。

- **Window Click（772が関わる全方向）**：771⇔772でも落とす。772クライアントが内部772以外のサーバーにいるとき、772サーバーにいる771以下のクライアントは、インベントリ操作（クリック）ができない。Set Creative Slot は UntrustedSlot→Slot で変換できる（772⇔771／770／769／768／767／766）。
- **Set Creative Slot（772クライアント→内部763／764／765）**：772の UntrustedSlot はデータコンポーネント形式のため変換できず落とす（771と同じ制限）。
- **ロケーターバー・ダイアログ・Change Gamemode・Custom Click Action（772→770以下）**：771・772にしか無いため、770以下のクライアント・サーバーには送らない。771⇔772では通す。
- **絵画 dennis・ジュークボックス lava_chicken・音 lava_chicken（772→771）**：dennis の絵画は絵柄が既定になる（Entity Metadata のエントリごと落とす）。lava_chicken のジュークボックスの歌は "11" になる。music_disc.lava_chicken の音は block.stone.break になる。アイテム music_disc_lava_chicken は石になる。
- **ハッピーガスト（772→771）**：happy_ghast（771・772とも56）はそのまま通す。Bedrock の正規化では happy_ghast はブタ代替になる（合成表のため）。
- **Test Instance Block（772）**：`set_test_block`／`test_instance_block_action`／`test_instance_block_status` は770・771・772にしか無いため落とす。
- **Pick Item（772↔771）**：772の pick_item_from_block／pick_item_from_entity は771と同じ形式のため素通しする。旧バージョン（763／764／765／766／767／768）向けは、771と同じく位置・エンティティ指定とスロット番号指定の違いのため落とす。
- **Player Loaded（772）**：772クライアントが送る player_loaded（0x2B、空）は771と同じく古いサーバーには送らない。統合テストでは参加・スポーン・チャット・移動が player_loaded 無しで動いたが、772サーバーが player_loaded を前提に処理を進めるかどうかは未確認。
- **チャット checksum（771⇔772）**：771・772とも末尾に checksum u8 があるため付け外しは不要。checksum の本当の算法は未確認のため、署名付きチャットや online-mode での挙動は別途確認が必要。
- **Player Chat の globalIndex（771⇔772）**：771・772とも先頭に globalIndex があるため素通しする。プロキシのセッションごとのカウンタ（0から）は770サーバーからの player_chat に付ける。実バニラが globalIndex の連番を厳密に検証するかは未確認。`message_acknowledgement` が772で globalIndex ベースに変わっているかも未確認（772クライアントのチャット履歴の既読確認がずれる可能性がある）。
- **Heightmap の type id 順序（771⇔772）**：バニラの `Heightmap.Types` の列挙順と仮定している。実バニラ1.21.7／1.21.8のチャンクで配列の形式は確認済みだが、type id と種別名の対応の仮定は未確認のまま（偽クライアントはチャンクを復号しないため、統合テストでは検証されていない）。
- **Vehicle Move の onGround（768クライアント→770サーバー）**：**770段階の既存の不具合**。`handleClientPlayTo770` の Vehicle Move 分岐は素通しのため、768クライアントの onGround 無しペイロードが770サーバーへそのまま送られる。772のスコープ外のため直していない。772の To772 分岐では正しく直してある。
- **From770 の一般プレイ分岐に is768() が無い（770段階の既存の挙動）**：770サーバー＋768クライアントの一般プレイは落とされる。772では変更しない（772の From772 分岐には is768()／is769() を追加してある）。
- **ネイティブ772同士の再設定はセッションが追跡しない**：772同士は Login Success 後に即プレイ状態になり、セッションは再設定を追跡しない（771のネイティブ同士と同じ）。Start Configuration（0x6F）はそのまま中継され、設定フェーズの Custom Click Action（0x08）はプレイ状態の Chat Message（0x08）と番号が同じためバイト列のままサーバーへ届く（サーバーは設定状態で読むため正しく解釈する）。
- **creaking_heart の uprooted（771⇔772）**：771と同じ制限。771⇔772ではそのまま通す。
- **ポーションの分裂（771⇔772）**：771と同じ制限。`potion` 99 が `splash_potion` 99 と `lingering_potion` 100 に分裂したまま。771⇔772ではそのまま通す。
- **ボートの木の種類**：771と同じ制限。767のボートは771／772では常にオークのボートに、771／772のボートは767では常に（木の種類を問わず）ボートになる。variant（木の種類）をエンティティ種別に直す変換は未実装。
- **food の効果（767→768→769→770→771→772）**：金リンゴ等の食べたときの効果は768／769／770／771／772向けに落とす。767の food の効果は amplifier／duration を持たないため、768／769／770／771／772の `consumable` の apply_effects 形式へは変換できない。
- **custom_model_data（767→768→769→770→771→772）**：771と同じ制限。767の単一の数値を768／769／770／771／772の colors[0] に入れるだけのベストエフォート。floats／flags／strings は空になる。
- **Set Cooldown（772のグループ名）**：771と同じく、エンダーパール・コーラスフルーツ・ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす（クールダウンの表示だけの制限）。
- **レシピパケット（772⇔771）**：Craft Recipe Response／Request、Displayed Recipe はレシピIDの形式が変わり変換できないため落とす。手動クラフトは動く。
- **Declare Recipes／Statistics／Tags／World Event ID**：全方向で未対応。レシピ帳・タグが空になるだけで遊べる。
- **ブロックエンティティの crafter／trial_spawner**：パケットごと落とす。766→765方向では vault も落とす（1.20系と同じ）。test_block／test_instance_block は772にしか無く、771向けには落とす。
- **Entity Metadata の中身**：763向けは variant 系エントリを落とす。764では素通しのためスロット・ブロック状態・粒子の中身は未変換のまま残る。772にしかないもの（music_disc_lava_chicken 等）は古いサーバー向けに落とす・代替する。
- **Explosion の772専用パーティクル／サウンド指定**：既定演出に置き換わる（766→763方向と同じ）。
- **1.21.7の新要素で通せないもの（772サーバーに771以下のクライアントがいる場合）**：music_disc_lava_chicken は771以下のクライアントでは石に見える（アイテムのフォールバック）。lava_chicken のジュークボックスの歌は "11" になる。dennis の絵画は絵柄が既定になる。ロケーターバー・ダイアログ・Change Gamemode・Custom Click Action は770以下のクライアントでは表示されない・届かない（パケットを落とす）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：764／765／766／767／768／769／770／771／772＋online-modeも同じ制限（内部バニラがoffline-modeのため Player Info Updateにプロパティを載せない）。
- **本物のゲームクライアントでの確認は未実施**：全100通り共通。手順書は `docs/PLAYTEST-1.21.7.md`。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの検証のみ。
- **1.21.9以降（773〜）は未対応**：プロキシは772より新しいプロトコルをログイン時に断る。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みはあるが、パックを置く場所はまだ決まっていない。既定はloopback配信。

## Java 21（内部772サーバーを動かすとき）

771と同じ。バニラ1.21.7／1.21.8は Java 21 が必要で、DetergentMC自体は Java 17 で
動く。`VanillaServerHost` は `detergentmc.java21.home` システムプロパティ、
`JAVA_HOME`、`java.home`、既定の場所の順に Java 21 を探す。統合テストは
`Java21TestSupport.ensureJava21Home()` で Java 21 を確認してから起動する。
1.21.7のjarのSHA-1は `05e4b48fbc01f0385adb74bcff9751d34552486c`、
1.21.8は `6bce4ef400e4efaa63a13d5e6f6b500be969ef81`（piston-meta確認済み、
`run/vanilla/server-1.21.7.jar`／`server-1.21.8.jar`）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.21.7.md` を参照。

## テスト

- `ClientAdapter772Test`（ClientAdapter の772受け入れ・REJECT_TOO_NEWを773へ・
  `differences(772)` の「新規・削除・ID変更のパケットは無い（247件すべて771と同一）」）
- `ClientJoinFlow772Test`（設定フェーズの有無。772は771と同じく設定フェーズあり）
- `ChunkTranslator772Test`（アイテム1256・音939・絵画14・ジュークボックス9の付け替え、
  ブロック状態・バイオーム・パーティクル・属性・エンチャント・メニューの identity）
- `PlayTranslator772Test`（ジュークボックス21件・dialog レジストリの素通し・
  chat checksum の strip／append）
- `ProxySession772Test`（772↔771・772↔766・772↔765・769↔772・772↔763の参加フロー、
  dialog／Change Gamemode／Custom Click Action／Tracked Waypoint の中継、
  設定フェーズの Custom Click Action／dialog の中継）
- `RegistryRemapper772Test`（771→772の対応表と逆向き）
- `PacketIds772Test`／`ProtocolVersion772Test`（パケットIDの同一性・プロトコル番号）
- `ResourcePackBuilder772Test`（772用パック pack_format 64）
- `ResourcePackMode772Test`／`ResourcePackServer772Test`（url-772／sha1-772、10パック配信）
- `ResourcePackWiringTest`（10パック化・`hostResourcePackIs772PackFor1217Server`／
  `hostResourcePackIs772PackFor1218Server`）
- `JavaBackendConnection772Test`（Bedrock→内部772。単体テスト10件）
- `RegistryOrderCaptureTest.registryOrderOfRealVanilla1217`／`registryOrderOfRealVanilla1218`
  （実バニラ1.21.7／1.21.8の Registry Data で dennis 14・lava_chicken 9・
  1.21.7／1.21.8の同一性を確認）
- `ProxyIntegrationTest` の772関係6テスト（772→1217、772→1218、772→1216、
  771→1217、772→1215、772→1201。いずれも参加・スポーン・チャット・移動まで確認）
- `BedrockMatrixIntegrationTest.matrixAgainst1_21_7`／`matrixAgainst1_21_8`
  （Bedrock 6 プロトコル × 内部772。参加・スポーン・チャット・移動・チャンク・アイテム）

テスト総数は最終ビルドで確定する（`./gradlew clean build` の結果を参照）。
