# 1.21.6クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.21.6クライアント（プロトコル771）は、内部プロトコルが
763／764／765／766／767／768／769／770／771のどのサーバーにも参加できます。内部771のときは
素通し（リレー）、それ以外は上向き・下向き変換です。770との違いは小さく、
**クライアント行きのパケットIDは1件も変わらず、サーバー行きのプレイ58件が
1つずれるだけ**です（Change Gamemode 0x04 の挿入による）。レジストリは
770→771ではブロック状態が32件増える程度で、チャンクの形式・チャットの
checksum・Player Chat の globalIndex・スロットの成分種別は770と
まったく同じです。いちばんの変化は新規パケット8件（ロケーターバー・
ダイアログ・Change Gamemode・Custom Click Action）と、レジストリの
`minecraft:dialog` 新規3件、属性の35件化、ジュークボックスの歌の20件化、
そして音の `blow_out` の衝突です。くわしい差分は下の各節を参照してください。
770の仕組みは `docs/PROXY-1.21.5.md`、769は `docs/PROXY-1.21.4.md`、768は
`docs/PROXY-1.21.2.md`、767は `docs/PROXY-1.21.md`、766は
`docs/PROXY-1.20.5.md`、逆向きの考え方は `docs/PROXY-REVERSE.md` を
参照してください。

全81通り（クライアント763／764／765／766／767／768／769／770／771 × サーバー763／764／765／
766／767／768／769／770／771）の対応マトリクスは `docs/VERSIONS.md` の「1.21.6対応：
全81通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用・767用・768用・769用・770用・771用の9つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 770→771の差分（要点）

| 分野 | 770→771の差分 | 扱い |
| --- | --- | --- |
| パケットID（クライアント行き） | **変わらない**。Join Game 0x2B、Spawn Position 0x5A、Keep Alive 0x26、Ping 0x36、Start Configuration 0x6F、Disconnect 0x1C、Respawn 0x4B、Chunk Data 0x27、Player Chat 0x3A、Add Resource Pack 0x4A。設定フェーズ・ログインのIDも770と同じ | 番号付け替えは不要。プレイのクライアント行きは770形式のまま通す（`to771ServerPacket` はペイロード変換だけをする） |
| パケットID（サーバー行き） | **58件が1つずれる**（Change Gamemode 0x04 の挿入で 0x04 以降が+1。ただし Spectate は 0x3d のまま。Test Instance Block Action が 0x3c→0x3e へ Spectate の後ろに回った）。Chat Message 0x07→0x08、Chat Command Signed 0x06→0x07、Chat Command 0x05→0x06、Window Click 0x10→0x11、Set Creative Slot 0x36→0x37、Vehicle Move 0x20→0x21、Keep Alive 応答 0x1A→0x1B、Ping 応答 0x2B→0x2C、Pick Item From Block 0x22→0x23／From Entity 0x23→0x24、Player Loaded 0x2A→0x2B、Set Test Block 0x39→0x3A、Set Player Position 0x1C→0x1D | 汎用の `PacketTranslator.translateId` で番号を付け替える。771同士は素通し |
| 新規パケット（8件） | 設定フェーズ：show_dialog（0x12、クライアント行き）・clear_dialog（0x11、クライアント行き）・custom_click_action_to_server（0x08、サーバー行き）。プレイ：tracked_waypoint（0x83、クライアント行き）・show_dialog（0x85、クライアント行き）・clear_dialog（0x84、クライアント行き）・change_gamemode（0x04、サーバー行き）・custom_click_action_to_server（0x41、サーバー行き） | 旧クライアント・旧サーバー向けは落とす（下の「送らない・落とすパケット」を参照） |
| 削除パケット（2件） | 設定フェーズのサーバー行き：custom_report_details_to_server（770の 0x08）・server_links_to_server（770の 0x09）。クライアント行きの同名パケット（0x0F／0x10）は残っている。Steer Vehicle（770の 0x29）は削除され、Player Input（771は 0x2a）に置換された | 771クライアントは 0x08／0x09 を送ってこない（0x08 は Custom Click Action）。770クライアント→771サーバーのときは 0x08／0x09 を落とす。Steer Vehicle は Player Input へ直す |
| Login Success | 770と同じく末尾の `strictErrorHandling` bool が無い（765と同じ形式） | そのまま通す。767向けは `false` を付け足す（770と同じ） |
| Join Game／Respawn | 770と771で形式は同じ（SpawnInfo の末尾の `seaLevel` varint を含む）。番号も同じ（Join Game 0x2B、Respawn 0x4B） | 番号だけ付け替える必要も無い。767向けは seaLevel（63）を落とす（770と同じ） |
| Player Chat（クライアント行き） | 先頭に `globalIndex` varint があるのは770・771とも同じ | 素通しする（770→771・771→770とも globalIndex を付けも剥がしもしない）。カウンタは `ProxySession.chatGlobalIndex` を770／771で共用する（セッションごと0から数える。769サーバーからの player_chat に付ける） |
| Chat Message／Chat Command Signed（サーバー行き） | 末尾の `checksum` u8 があるのは770・771とも同じ | 剥がしも付け足しも不要。番号だけ付け替える（Chat Message 0x08⇔0x07）。**注意：** 770節を写した当初は Chat 0x07／0x06 を検査して checksum を剥がす分岐が入っていたが、771の Chat は 0x08／0x07 のため、Chat Command（0x06）の末尾を誤って剥がしてしまう。771では明示の chat 分岐を両方向とも削除し、汎用経路（`PacketIds.nameFor`＋`translateId`）に任せてある |
| Heightmap（Chunk Data） | 770と同じ直接の配列（varint 個数＋{type varint、data long[]}）。type id の順序は770と同じと仮定する（0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、3=OCEAN_FLOOR、4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES）。**実バニラ1.21.6のチャンクで確認済み**（2026-11-12の生ソケットプローブで、770形式のパーサでチャンク (26, -54) が完全に読めた：heightmaps=[(1,37),(5,37),(4,37)]、sections=24、blockEntities=0、lightBytes=8238） | 770・771とも直接配列のまま写す（NBT↔配列の変換は770⇔771では不要）。`ChunkTranslator.translateChunkData770To771`／`771To770` |
| チャンクセクションの Data Array（Chunk Data） | **770と同じ**（長さの varint 接頭辞なし。長さは bitsPerEntry とエントリ数から計算する）。シングルパレット（bpe=0）も長さ varint（0）が無い | 770・771とも同じ読み方で通す。769⇔770の変換器を写すときは、長さ varint の読み書きを入れないこと |
| Window Click | HashedSlot のまま（770と同じ） | **変換できず落とす**（770と同じ。ハッシュ算法が未検証のため）。`ChunkTranslator.translateWindowClick770To771`／`771To770` は `UnsupportedOperationException` を投げる |
| Set Creative Slot | UntrustedSlot のまま（770と同じ）。アイテムID・成分種別の付け替えだけで変換できる（`{itemCount varint、itemId、addedComponentCount、removedComponentCount、components[{type、data ByteArray}]、removeComponents[{type}]}`）。**注意：** 769⇔770の変換器（Slot⇔UntrustedSlot）を写すと誤る。770⇔771は UntrustedSlot 同士のため、added成分の ByteArray の中身を種別ごとに付け替える | `ChunkTranslator.translateSetCreativeSlot770To771`／`771To770` で変換する。770・771とも `tooltip_display` は種別15で同一のため特別処理は不要 |
| スロット（データコンポーネント） | 種別は770・771とも96件・同じ番号のため、種別番号の対応表は恒等表。`unbreakable` は770・771とも void、`dyed_color` は770・771とも色 i32 のみ、`enchantments`／`attribute_modifiers`／`trim`／`tool`／`instrument`／`jukebox_playable` は770・771で同じ形式。**注意：** 769→770で形式が変わった成分ヘルパー（unbreakable・dyed_color・enchantments・attribute_modifiers・trim・tool・instrument・jukebox_playable）を単純シフトすると誤る。770・771は同形式のため、770節の置き換えは「そのまま写す」形で手書きしてある | 種別番号は恒等表で変換する。成分の中身は対応表で番号を直す（アイテム・エンチャント・属性・音・ジュークボックスの歌）。770にしかない種別は無い（96件とも両方にある）ため、成分を落とすことは無い。`jukebox_playable` は holder>0 のとき曲IDを付け替える（tears は770向けに曲ID 0（"11"）で代用する） |
| Entity Metadata | 種別番号は770・771とも同じ（23=cow_variant、24=wolf_variant（素の varint）、25=wolf_sound_variant、26=frog_variant、27=pig_variant、28=chicken_variant、29=optional_global_pos、30=painting_variant、31=sniffer_state、32=armadillo_state、33=vector3、34=quaternion）。wolf_variant は770・771ともただの varint（Holderではない）ため素通し。エンティティ種別は happy_ghast（771の56）が増えただけ | 種別番号は恒等（`mapMetadataType770To771`／`771To770` は恒等）。エンティティ種別だけ対応表で直す。771→770で happy_ghast（56）は **ghast（770の55）に代替する**（逆向き表の invert ではブタ（94）になるが、それよりまし）。メタデータの個体差はプロキシがエンティティ種別を追跡しないため ghast レイアウトとのずれが残る（下の「対象外・未実装」を参照） |
| World Particles | 770・771で同一（tinted_leaves 35、firefly 113も両方にある。粒子データの case 番号は770側のID：dust_pillar 108、sculk_charge 37、item 46、vibration 47、shriek 102、trail 48、block_crumble 112） | 対応表で変換する（実質 identity）。770→769の落としロジックは771→770では不要 |
| ブロック状態 | 27,914→27,946件（+32、−0）。**dried_ghast のみ**（旧状態は同一、新32件は末尾） | 対応表で変換。771にしかないブロック状態は古いサーバー向けに石へ（`mapBlockState771To765` など） |
| アイテム | 1,396→1,415件（+19、−0）。乾燥ガスト・ハッピーガストのスポーンエッグ・ハーネスなど | 対応表で変換。771にしかないアイテムは古いサーバー向けに石へ。770の oak_boat 810 は771では827（+17） |
| エンティティ | 150→151件（+1、−0）。`happy_ghast`（771の56）が増えただけ。ghast は771でも55 | 対応表で変換。771→770で happy_ghast は ghast に代替（上の Entity Metadata を参照）。Bedrock の正規化では happy_ghast はブタ代替になる（合成表のため。別記） |
| 音 | 1,702→1,727件（+29、−4）。**`blow_out` の衝突**：770の `entity.puffer_fish.ambient`（1179）と `entity.puffer_fish.blow_out`（1180）が両方771の `entity.puffer_fish.blow_out`（1204）へ写る。`invert()` は先行勝ちで ambient（1179）を選ぶ。数値表の OVERRIDES {816,840},{817,839} は別の音（puffer_fish ではない）。名前レベルでも770の ambient と blow_out が両方771の blow_out へ写り、逆向きは ambient が選ばれる | 対応表で変換。771→770で blow_out（1204）は ambient（1179）に戻る（元の blow_out（1180）には戻らない。フグの「膨らむ音」と「周囲音」が771→770では区別できなくなる） |
| パーティクル | 114件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 属性 | 32→35件。`camera_distance` が771の8番に挿入、`waypoint_transmit_range`（33）・`waypoint_receive_range`（34）が末尾に追加された | 対応表で変換。771→770で camera_distance（8）・waypoint_transmit_range（33）・waypoint_receive_range（34）は落とす（`mapAttribute771To770` は DROP）。Update Attributes で落とした属性はクライアントに届かないだけ（パケット自体は通る） |
| エンチャント | 42件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| バイオーム | 65件で同じ顔ぶれ・同じ順番（pale_garden は 39、plains は 40） | 変換なし（identity）。Registry Data のバイオームは770と771で同じ順番のため、並べ替えは不要 |
| ブロックエンティティ種別 | 47件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 絵画・旗の模様・トリム | 変わらず（絵画50件・旗43件・トリム18件） | 変換なし |
| ジュークボックスの歌 | 19→20件。`minecraft:tears` が771の17番に挿入（wait 18、ward 19） | 対応表で変換。771→770で tears（17）は落とす（Registry Data の並べ替えでは落とし、jukebox_playable 成分では曲ID 0（"11"）で代用する）。770→771で範囲外は0で代用する |
| ダイアログ（新規レジストリ） | **新規 `minecraft:dialog`（3件：custom_options 0、quick_actions 1、server_links 2）**。770には無い | 771→旧クライアントでは dialog レジストリを落とす。770サーバーからは来ないため、771クライアントへ**合成注入はしない**（実バニラクライアントが欠けたレジストリを内蔵既定で補うかは未確認のため。生ペイロードは `/tmp/vanilla1216/reg-dialog.bin`（835バイト）に残してある） |
| ダメージ種別 | **770と同じ49種・同じ並び**。**実バニラ1.21.6のキャプチャで確認済み**（2026-11-12。770の表と771キャプチャが完全一致。ender_pearl 8、mace_smash 26、magic 27、wither_skull 48） | 変換なし（identity）。ステージ1の仮定が実バニラで確認された |
| メニュー | 25件で変わらず | 変換なし |
| Client Information（Settings） | 770と同じ9フィールド（末尾に `particleStatus` varint） | そのまま通す。**770のときの罠（768クライアント→769サーバーで particleStatus を落とす実バグ）と同じく、771サーバー向けは落としてはいけない**。770も771も同じ9フィールドのため、素通しする |
| Player Info | 770と同じ8ビット（update_hat／update_list_order を含む）。フィールド順は listPriority の後に showHat（minecraft-data に従った仮定。770と同じ） | そのまま通す（identity） |
| Set Held Item／Vehicle Move | 770と同じ形式。Vehicle Move は770・771とも末尾に onGround bool 付き（onGround は769で追加された）。**注意：** 770節を写した当初は onGround を落とす分岐が入っていたが、770・771ともあるため素通しに直してある。768（・767以下）クライアント→770サーバーのときは onGround が補われない**770段階の既存の不具合**があり、771のスコープ外のため直していない（下の「対象外・未実装」を参照）。771の To771 分岐では正しく直してある（is769() は素通し、それ以外は `translateVehicleMove768To769` で onGround=false を補う） | そのまま通す（identity）。771→770／770→771とも ID だけ付け替える |
| Spawn Entity Experience Orb | 770・771とも専用パケットは無い（Spawn Entity に統合済み） | 経験値オーブの専用分岐は無い。770・771とも Spawn Entity（type experience_orb）で通す |
| Trade List | 770と同じ形式 | 成分種別の番号だけ対応表で直す（770・771とも96件で同じため実質 identity） |
| ロケーターバー／tracked_waypoint（771の新要素） | 新規パケット tracked_waypoint（0x83、クライアント行き）。属性 waypoint_transmit_range／waypoint_receive_range（33／34）と連動する | 旧クライアント向けに落とす。771同士は素通し（リレー） |
| ダイアログ（771の新要素） | 新規パケット show_dialog（0x85）・clear_dialog（0x84、プレイ）、show_dialog（0x12）・clear_dialog（0x11、設定フェーズ）。設定フェーズのサーバー行き custom_click_action（0x08）とプレイの custom_click_action（0x41）も新規 | 旧クライアント・旧サーバー向けは落とす。771サーバーへは Custom Click Action を通す（サーバーがダイアログのクリックを処理する）。771クライアント＋770サーバーではダイアログは表示されない |
| Change Gamemode（771の新要素） | 新規パケット change_gamemode（0x04、サーバー行き） | 770に相当が無いため旧サーバー向けに落とす。771サーバーへは通す |
| ハッピーガスト／乾燥ガスト／ハーネス（771の新要素） | ハッピーガストは新エンティティ（771の56）。乾燥ガストは新ブロック状態（+32）。ハーネスは新アイテム（+19に含む） | ハッピーガストは770ではガストに代替（メタデータの個体差あり）。乾燥ガストはブロック状態の付け替えで通す。ハーネスはアイテムの付け替えで通す。771にしかないものは古いサーバー向けに石へ |

検証スクリプトは `detergentmc-protocol/tools/verify-771-tables.py`
（2026-11-12に注記で確認した blow_out の衝突を含む）。
本物のバニラ1.21.6から Registry Data の順番をそのまま記録する統合テスト
（`RegistryOrderCaptureTest` の1.21.6版）は**実施済み**（下の「テスト」を参照）。
実バニラが設定フェーズで送るレジストリは21レジストリで、770と同じ12レジストリに
加えて `minecraft:dialog`（3件）・`minecraft:chat_type`（7件）・
`minecraft:dimension_type`（4件）・`minecraft:wolf_variant`（9件）・
`minecraft:wolf_sound_variant`（7件）・`minecraft:pig_variant`（3件）・
`minecraft:frog_variant`（3件）・`minecraft:cat_variant`（11件）・
`minecraft:cow_variant`（3件）・`minecraft:chicken_variant`（3件）・
`minecraft:test_environment`（1件）・`minecraft:test_instance`（1件）が
含まれる（2026-11-12の生ソケットプローブで確認。設定フェーズでバニラが送る
パケットは 0x01 Plugin Message、0x0c Feature Flags、0x0d Tags、0x0e Select Known Packs、
0x07 Registry Data×21、0x03 Finish Configuration。Server Links／Custom Report Details は
既定では送ってこない）。
`minecraft:attribute` と `minecraft:block_entity_type` は送られないため、
属性の順番とブロックエンティティ種別の順番は minecraft-data と jar の data ファイルに依る
（属性は実バニラキャプチャで35件を確認済み）。

**770→771の連鎖の方針：** 771クライアント→770サーバーの変換は、
771→770へ直してから既存の `handleClientPlay770` に渡す二段方式。
ただし `handleClientPlay770` はクライアントが770であることを前提に
書かれているため、内部770サーバーのときだけは771→770へ直した
パケットを直接バニラへ送り、それ以外（内部763／764／765／766／767／768／769）の
ときだけ `handleClientPlay770` に渡す。同じ罠はサーバー→クライアント側にも
あり、770サーバーから771クライアントへの Join Game／Respawn／Disconnect は
771向けの分岐を `handleServerPlayFrom770`／`handleServerJoinGame770` に
追加してある（追加しないと768形式の Join Game 0x2C が771クライアントに
届く）。767／766サーバーから771クライアントへは 767→768→769→770→771 の
五段・四段で変換する。763サーバーから771クライアントへは、合成される
Join Game を770形式で作る（`handleSynthesizedJoinGame` の分岐。
**771を漏らすと765形式の Join Game（0x29）が771クライアントに届く**）。
設定フェーズの辞書は768形式で作り、771→768へ並べ替えてから
`removePaleGardenBiome` で pale_garden を落とす（763／764／765クライアント向け）。

**設定フェーズの振り分けの注意：** 771クライアント→770サーバーの設定フェーズは、
専用の `ProxySession.handleServerConfiguration771From770`
（Registry Data の770→771並べ替え・Tags 落とし・Cookie／Select Known Packs の
空応答・dialog 落とし）を通す。振り分けで `is770()` の分岐（`isServer770()` の素通し）だけを
見ると、771サーバーからの Registry Data が764／765向けの消費者に落ちて
771の Registry Data が770クライアントに届かなくなる。
`handleServerConfiguration` では `is770()` の分岐で `isServer771()` を
`isServer770()` と同じく先に見る（770→769の設定フェーズの振り分けの
罠と同じ）。**771の作業中に、振り分けのシフトで `is763()` クライアントの
`isServer769()` 分岐が `isServer770()` に化けて消える実バグがあった**
（763クライアント＋769サーバーで Registry Data が消費されなくなった。
`ProxySession771Test.join763Against771ConsumesConfigAs771Client` で発覚し、
分岐を復旧して `handleServerConfiguration771From769` を新設して直した）。

**Client Information の罠（771クライアント→770サーバー）：**
`handleClientConfiguration770` は「Settings の末尾の particleStatus を
旧サーバー向けに落とす」と書かれているが、771も770と同じ9フィールドの
ため、770サーバー向けは落としてはいけない。771の作業では
`handleClientConfiguration771` を `handleClientConfiguration770` に
委譲し、Settings の変換は770のものを使い回す（9フィールドが同一のため）。

**ProxySession771Test で見つかった771漏れ（9件、すべて修正済み）：**
771の変換は「770の分岐に771を足す」形で入れたが、770の分岐にはない
場所・770の分岐にない新しい分岐が9か所あり、テストで発覚した。
(1) `handleSynthesizedJoinGame`：771クライアントの分岐が無く、else 分岐
（765形式・0x29）に落ちていた。771分岐（0x2B）を追加した。(2) `handleServerJoinGame763`：
同じく771分岐が無く else 分岐（0x29）に落ちていた。771分岐（0x2B、Add Resource Pack は 0x4A）
を追加した。(3) `handleServerJoinGame`（764／765サーバー）：771分岐が無く
763分岐（0x28）に落ちていた。771分岐（0x2B、Add Resource Pack は 0x4A）を
追加した。(4) `handleServerJoinGame766`：**770段階ではこの分岐自体が無く、
770クライアントも 0x29（764／765形式）に落ちていた既存バグ**。
`is770() || is771()` 分岐を新設した（766の Join Game は767形式のため
`translateJoinGame767To768` で 0x2B＋Add Pack 0x4A）。(5) `handleServerPlayUp`
（765サーバー）：条件に771が無く、Keep Alive／Ping／Disconnect／Respawn／
Add Resource Pack が汎用経路に落ちていた。条件に771を追加し、Ping 0x36／
Disconnect 0x1C／Respawn 0x4B／Add Resource Pack 0x4A に直した
（Keep Alive は 0x26 のままで正しい）。(6) `handleClientConfiguration768`：
Settings（0x00）の条件に `!isServer771()` が無く、771サーバー向けの Settings が
8フィールドに落とされ、Finish ack が 0x02 に付け替えられていた。条件に
`isServer771()` を追加した（770サーバー向けは従来通り）。(7) `handleServerPlayFrom771`
の Respawn：`is768() || is769()` → 0x4C 分岐が無く、765形式の 0x45 に
落ちていた。分岐を追加した。(8) `handleServerPlayFrom770` の Respawn：
**770段階の既存バグ**（768クライアントは765形式の 0x45 に落ちていた）。
`if (is769())` を `if (is768() || is769())` に直した（同一行の修正のため
771と一緒に直した）。(9) `handleServerPlayFrom771` の一般分岐：`is769()`／
`is768()` 分岐が無く、771サーバー＋769／768クライアントの一般プレイが
落とされていた。`handleServerPlay771To769`（771→770→handleServerPlay770To769）と
`handleServerPlay771To768`（771→770→769→handleServerPlay769To768）を新設した。
なお `handleServerPlayFrom770` の一般分岐に `is768()` が無いのは770段階の
意図的な既存挙動として残す（770サーバー＋768クライアントの一般プレイは
落とされる。771では変更しない）。

**その他の771の実バグ（テストで発覚、修正済み）：**
`ClientAdapter.differences(int,int)` の第2のガード（serverProtocol が
763／766／767／768／769／770／771以外の場合のクライアント許可リスト）に
V1_21_6 が無く、`differences(771, 764)` と `differences(771, 765)` が
NOT_SUPPORTED を返していた。`decide(771, 764)` は受け入れるのに differences
だけが拒否する不整合。`ClientAdapter.java` に `&& clientProtocol != ProtocolVersion.V1_21_6`
を追加して修正した。

**生成器の注記：** 771の翻訳器と ProxySession は生成器
（`detergentmc-protocol/tools/gen-771-translators.py`、
`gen-771-proxysession.py`）で770節をシフトして作ったが、上記の F1〜F9 の
手直しは生成器の出力に含まれていない（生成器を再実行して作り直したパッチは
live と一致しない）。生成器の docstring に手直し箇所の一覧を注記してある。
完全な統合（FIX_EDITS_FULL への取り込み＋再現検証）は後続作業。

### なぜ「並べ替え」が必要か

770／771 の設定フェーズの Registry Data は、レジストリごとの
エントリ配列形式です（1個の NBT codec ではありません）。各エントリは
「名前＋NBT」で、番号は配列の位置そのものです。バイオームの並びが
770（65件、pale_garden=39・plains=40）と771（65件、同じ）で同じため、
バイオームの並べ替えは不要ですが、ブロック状態・アイテム・
エンティティ・音・属性・ジュークボックスの歌の並びが変わるため、配列をそのまま中継すると
番号がずれ、別のブロック・アイテムになってしまいます。プロキシは
Registry Data を受け取ったら、エントリを名前で並べ替え、送る側の正しい
順番に直してから送ります。送る側に無いエントリ（771の新ブロック・新アイテム等）は、
770向けには**落とす**のではなく、名前だけのエントリ（データなし）として送り、
番号をずらさないようにします
（771→770の逆向きは、770に無いエントリを 771の位置に挿入する）。
`PlayTranslator.translateRegistryData770To771`／`771To770` を参照してください。

## 接続の流れ

### 1.21.6クライアント（771、offline-mode）

内部プロトコルで流れが分かれます。

**内部771（1.21.6サーバー）のとき：素通し（リレー）。**
ハンドシェイクの直後にリレーへ切り替わり、設定フェーズ・プレイは
生バイトのまま通します。設定フェーズの Select Known Packs（0x0E）も
そのままクライアントへ届くため、クライアントは空の一覧で答える必要が
あります（本物の1.21.6クライアントは答えます。偽クライアントのテスト
`Fake771Client` も答えます）。

**内部770（1.21.5サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**

1. ハンドシェイクのプロトコルVarIntを771→770に書き換えてバニラへ送る。
2. Login Startは770と同形式（名前＋UUID、bool なし）のため変換しない。
   Login Successは770も771も末尾の `strictErrorHandling` bool が無いため
   そのまま通す。
3. ログイン成功後、クライアントが Login Acknowledged（771は 0x03）を送ると
   両方とも設定フェーズに入る。Client Information（771は 0x00）は末尾の
   particleStatus を落とさず770形式のままバニラへ送る（771も9フィールドの
   ため、そのまま通す）。バニラが送る Registry Data（770は 0x07、
   エントリ配列）は771の順序に並べ替え（新ブロック・新アイテムを挿入）して中継する。
   Custom Report Details（0x0F）・Server Links（0x10）は770と771で番号が
   同じため中継する。Tags はレジストリ番号がずれるため落とす。
   dialog レジストリは770サーバーからは来ないため、771クライアントへは
   合成注入しない（上の表の「ダイアログ」を参照）。
4. Finish Configuration（バニラ側 0x03→771も 0x03）を中継し、クライアントの
   応答（0x03）がバニラに届いた時点で両方ともプレイ状態に入る。
5. バニラが送るJoin Game（770は 0x2B）は771の 0x2B のまま通す
   （形式は同じ・番号も同じ）。スロットを含むパケット（Set Slot／Window Items／
   Entity Equipment など）は770→771へ変換する
   （`ChunkTranslator` の771変換を参照）。
6. スポーンは771式。Spawn Position（771は 0x5A）が合図で、770の
   Game Event 13は771クライアントに送らない。

**内部769（1.21.4サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
770のときと同じ流れで、Registry Data は769→770→771の二段で並べ替える。
jukebox_song は769にあるため、771クライアントは受け取る（tears は落とす）。
dialog レジストリは合成しない。

**内部768（1.21.2／1.21.3サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
769のときと同じ流れで、Registry Data は768→769→770→771の三段で並べ替える。
jukebox_song は768にあるため、771クライアントは受け取る。

**内部767（1.21／1.21.1サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
768のときと同じ流れで、Registry Data は767→768→769→770→771の四段で並べ替える。
jukebox_song は767にあるため、771クライアントは受け取る
（766サーバーは送らない）。

**内部766（1.20.5／1.20.6サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
767のときと同じ流れで、Registry Data は766→767→768→769→770→771の五段で並べ替える。
jukebox_song は766に無いため、771クライアントは受け取らない
（766サーバーは送らない）。

**内部765（1.20.4サーバー）・内部764（1.20.2サーバー）のとき：
設定フェーズを中継し、Registry Data の形式と順序を変換する。**

1. ハンドシェイクのプロトコルVarIntを771→765／764に書き換えてバニラへ送る。
2. Login Startは765・764と同形式のため変換しない。Login Successは
   771に strictErrorHandling が無いため、そのまま765／764形式で通す。
3. ログイン成功後、クライアントが Login Acknowledged（771は 0x03）を送ると
   両方とも設定フェーズに入る。Client Information は particleStatus を
   落としてからバニラへ送る。バニラが送る Registry Data（764／765は
   0x05、1個の名前なし NBT codec）は、771のエントリ配列形式（0x07）に
   変換し、さらに771の順序に並べ替えて中継する。逆向き（内部771のときの
   764／765クライアント）も同じくエントリ配列→codec に変換する。Tags は
   レジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x02→771は 0x03）を中継し、クライアントの
   応答（771は 0x03→バニラ側 0x02）がバニラに届いた時点で両方とも
   プレイ状態に入る。
5. バニラが送るJoin Game（764／765は 0x29）は771の 0x2B へ変換する。
   dimension は番号から文字列に戻す（764／765向けはその逆）。
6. スポーンは771式。Spawn Position（771は 0x5A）が合図で、765の
   Game Event 13は771クライアントに送らない。

**内部763（1.20／1.20.1サーバー）のとき：設定フェーズを合成する。**
内部763には設定フェーズが無いため、プロキシが Join Game の codec から
Registry Data（771形式 0x07、レジストリごとのエントリ配列）と
Finish Configuration（771は 0x03）を合成してクライアントへ送る。
Registry Data は766形式で作ってから 766→767→768→769→770→771 の五段で並べ替え、
新ブロック・新アイテムを挿入する。クライアントの Finish 応答（771は 0x03）が
来たら、預かっていた Join Game（771は 0x2B）を送ってプレイ状態に入る。
合成の間、サーバーから届いたプレイ状態のパケットは溜めておき、Finish 応答の
後にまとめて送る。この仕組みは764／765／766／767／768／769／770クライアントの
内部763向け合成と同じ経路で、771だけ Registry Data の形式が違う
（`ProxySession.synthesizingConfig`、`PlayTranslator.buildRegistryData766` を
参照。771の Registry Data も770と同じエントリ配列形式のため、同じビルダーを使う）。
dialog レジストリは合成しない。

### 1.21.6クライアント（771、online-mode）

**参加できます。** 763／764／765／766／767／768／769／770と同じく、プロキシがクライアントとの間で
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
  クライアントへ渡します（771形式への strict bool の付け直しは不要。
  771には strictErrorHandling が無い）。

信頼境界と防御、スキンの扱い（他プレイヤーから見たスキンが既定スキンに
なる場合がある制限を含む）は763と同じです（`docs/PROXY-1.20.1.md` の
「信頼境界と防御」「スキンの扱い」を参照）。

#### 設定

`detergentmc.yml` の `server.online-mode: true`（既定）が、プロキシ認証の
オン／オフです。`server.session-server-url` は照会先で、空なら既定の
Mojangセッションサーバーを使います。テストでローカルの偽サーバーを
指定するとき以外は変えないでください。

### ステータス（サーバーリストのping）

771クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 内部→771、name 内部の表示→
"1.21.6"）。descriptionやplayersは書き換えません。

## プレイ中の翻訳

- **ID付け替えのみ：** Keep Alive（プレイは771の 0x26⇔0x1B）、移動
  （Set Player Position は771の 0x1D）、ほとんどのプレイ中パケット。ID表は
  `PacketIds.java`（771列）を参照。770→771でクライアント行きのプレイは
  変わらず、サーバー行きのプレイ58件のIDが変わるため、汎用の `PacketTranslator.translateId` で
  番号を付け替える。
- **ペイロード変換あり：** Player Chat（770・771とも先頭に globalIndex。
  素通し。`PlayTranslator.translatePlayerChat770To771`／`771To770`）、
  Chat Message／Chat Command Signed（770・771とも末尾に checksum u8。
  付け外しは不要。番号だけ付け替える）、
  Disconnect（771は NBT。763／764向けは JSON 文字列に変換する）、
  Respawn（dimension 番号のため変換する。771は 0x4B）、
  Start Configuration（771は 0x6F。763には無いため、内部763のときは
  プロキシがサーバー側で肩代わりする。764／765／766／767／768／769／770には中継する）、
  Synchronize Player Position（771は 0x42。770と同形式のためそのまま通す）、
  Set Cooldown（771はグループ名の文字列。770と同じ）、
  Player Input（771は 0x2a。770の 0x29 と同じ形式。Steer Vehicle は771で削除され
  Player Input に置換された）、
  Set Slot State（番号だけ付け替える。ペイロードはそのまま）。
- **ロケーターバー／tracked_waypoint（771の新パケット）：** tracked_waypoint
  （0x83、クライアント行き）は771にしかないため、古いクライアントには送らない。
  771同士は素通し。
- **ダイアログ（771の新パケット）：** show_dialog（0x85）・clear_dialog（0x84、
  プレイ）・show_dialog（0x12）・clear_dialog（0x11、設定フェーズ）は771に
  しかないため、古いクライアントには送らない。custom_click_action_to_server
  （プレイ 0x41・設定フェーズ 0x08）は771サーバーへ通す。古いサーバーには送らない。
  （下の「対象外・未実装」を参照）。
- **Change Gamemode（771の新パケット）：** change_gamemode（0x04、サーバー行き）は
  771にしかないため、古いサーバーには送らない。771サーバーへは通す。
- **Test Instance Block（770の新パケット）：** test_instance_block_status
  （0x77、クライアント行き）は770・771にあり、古いクライアントには送らない。
  set_test_block（771は 0x3A）・test_instance_block_action（771は 0x3E、サーバー行き）も
  770・771にしかないため、古いサーバーには送らない。両方向とも落とす
  （下の「対象外・未実装」を参照）。
- **Pick Item（769の新パケット）：** pick_item_from_block（771は 0x23、位置＋
  includeData）と pick_item_from_entity（771は 0x24、エンティティID＋includeData）は、
  770と同じく771にもあるため、770⇔771ではそのまま通す（番号は+1）。
  旧バージョンの pick_item（スロット番号）とは別物で、プロキシがワールドを
  知らないためブロック・エンティティからスロットへは変換できない。
  763／764／765／766／767／768向けは落とす（下の「対象外・未実装」を参照）。
  player_loaded（771は 0x2B、空）は770／771にあり、両方向とも通す。
- **スロット（データコンポーネント）：** 770と771でデータコンポーネントの
  種別番号が同じため、770⇔771のスロットは中身の番号だけ対応表で直す
  （`ChunkTranslator` の771変換）。Set Slot／Window Items／Set Cooldown／
  Entity Equipment／Trade List／Open Window／Block Action／Spawn Entity／
  Sound Effect／Entity Sound Effect／Stop Sound／World Particles／
  Entity Effect／Remove Entity Effect／Damage Event（771にしかないダメージ種別は
  無い。49種で同じ）／Entity Metadata／Update Attributes／Window Click／
  Set Creative Slot を変換する。
  変換のやり方は次の通り。
  - アイテムID・エンチャントID・属性ID・成分種別IDの番号ずれを対応表で直す。
  - `enchantments`／`stored_enchantments`・`attribute_modifiers`・`trim`・`tool`・
    `instrument`・`jukebox_playable` は770と771で同じ形式のため、中身の番号だけ直す。
    770→769の変換器とは違い、末尾の showTooltip の付け外しは無い。
  - `jukebox_playable` は holder>0 のとき曲IDを付け替える（tears は770向けに
    曲ID 0（"11"）で代用する。逆向きは範囲外も0で代用する）。
  - `food`・`consumable`・`use_remainder` は770と771で同じ形式のため、
    中身の番号だけ直す。
  - `custom_model_data` は770と771で同じ4配列の形式のためそのまま通す。
  - `trim`・`banner_patterns`・`instrument` は770と771で番号が同じためそのまま通す
    （音の番号だけ対応表で直す）。
  - `break_sound`（種別71）・`blocks_attacks`（種別33）は音 Holder を
    対応表で直す（770の ambient 1179／blow_out 1180 は771の blow_out 1204 へ。
    逆向きは 1204→1179）。
  - `provides_trim_material`（種別53）・`painting_variant`（種別89）・
    `chicken_variant`（種別86）など variant 系は同じ並びのためそのまま通す。
  - 770にしかない成分種別は無いため、成分を落とすことは無い（770→769で落としていた
    weapon・blocks_attacks・potion_duration_scale・provides_trim_material・
    provides_banner_patterns・break_sound・variant 系は770・771ともある）。
  - 763／764／765向けは NBT 形式のため、Window Click／Set Creative Slot は
    **変換できず落とす**（770と同じ制限）。落としたクリックはサーバーに
    届かないため、771クライアントが内部763／764／765のサーバーにいるときは
    インベントリ操作ができない制限がある（ネイティブの771⇔771・770⇔771では
    リレーのため問題ない）。
  - **Window Click は770⇔771でも変換できず落とす**（HashedSlot のため。
    上の「770→771の差分（要点）」を参照）。Set Creative Slot は
    UntrustedSlot のため770⇔771でも変換できる。
- **Update Attributes（770⇔771・769⇔771・768⇔771・767⇔771・766⇔771・765⇔771）**：属性キーが番号のため、
  対応表で変換する。771→770で camera_distance（8）・waypoint_transmit_range（33）・
  waypoint_receive_range（34）は落とす（パケット自体は通る。落とした属性は
  クライアントに届かないだけ）。765向けは名前へ直す（`generic.burning_time` は
  771の `burning_time` へ）。771にしかない属性はこの3件だけ（35件のうち32件は770と同じ）。
  765の属性のキーはワイヤ上で `minecraft:generic.max_health` のような
  接頭辞付きである（本物のバニラ1.20.4から受け取って確認済み、
  2026-11-10。`AttributeKeyCaptureTest` が毎回確認する）。接頭辞なしで
  比較すると本物のサーバーからの属性が全部落とされるため、変換では
  接頭辞の有無の両方を受け付ける。
- **チャンク・ブロック状態IDリマップ：** 770↔771はブロック状態が32件増え、
  約27,000件の番号がずれるため、**チャンクの変換が必要**（766↔767は変換不要
  だった）。バイオームは770と771で同じため、バイオームパレットは番号で変換しない
  （identity）。763／764／765／766／767／768／769向けは
  合成表（763↔771・764↔771・765↔771・766↔771・767↔771・768↔771・769↔771）を使う
  （`RegistryRemapper` の771用対応表）。
  770→771の差分は小さい：ブロック状態は32件の追加（dried_ghast のみ）、アイテムは19件の追加、
  エンティティは1件の追加（happy_ghast）、音は29件の追加・4件の削除、
  パーティクル・エンチャント・バイオーム・ダメージ種別は変わらない、
  属性は3件の追加、ジュークボックスの歌は1件の追加（tears）、
  ダイアログは新規3件。
  くわしくは上の「770→771の差分（要点）」を参照。
  検証スクリプトは `detergentmc-protocol/tools/verify-771-tables.py`。
- **Heightmap（Chunk Data）の変換：** 770・771とも直接の配列のため、変換は不要
  （そのまま写す）。type id の順序はバニラの `Heightmap.Types` の宣言順と仮定する
  （0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、3=OCEAN_FLOOR、
  4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES）。**実バニラ1.21.6のチャンクで
  確認済み**（2026-11-12のプローブで、770形式のパーサでチャンク (26, -54) が
  完全に読めた。type id 1・4・5 を確認。type id と種別名の対応の仮定は未確認のまま）。
- **Set Cursor Item／Set Player Inventory（770／771にしかないパケット）：**
  771の Set Cursor Item（0x59、スロット1個）は767の Set Slot（window −1）に、
  Set Player Inventory（0x65、スロットID＋スロット）は767の Set Slot
  （window 0）に直して送る（770と同じ）。逆向き（767クライアント→771サーバー）は
  Set Slot の window −1／0 をそのまま中継する（771は Set Slot もあるため
  変換は不要）。
- **リソースパック：** 771クライアントには pack_format **63** の
  `detergentmc-pack-1.21.6.zip`（1.21.6専用）を出す。763用
  パック（15）・764用パック（18）・765用パック（22）・766用パック（32）・
  767用パック（34）・768用パック（42）・769用パック（46）・770用パック（55）とは別物。外部配信時は
  `resource-pack.url-771` / `sha1-771` で771用パックを
  指定する（任意・未指定時は765用パックを出す。763／764／766／767／768／769／770用と同一方針）。
  loopback時は9つのパックを同じ25567番ポートで配信する。
  設定フェーズの Add Resource Pack（771は 0x09、UUID付き）とプレイ状態の
  Add Resource Pack は、内部763／764向けは UUID を外して送る。
- **データパック：** 1.21.6には pack_format **80** を出す
  （Minecraft Wiki の pack format 表：80 は 1.21.6、71 は 1.21.5、61 は 1.21.4、57 は
  1.21.2〜1.21.3、48 は 1.21〜1.21.1）。

## 送らない・落とすパケット（771向け）

771に相当するものが無い、または変換が未検証のパケットは落とします。

- Window Click（全方向。HashedSlot のハッシュ算法が未検証のため。
  770⇔771でも落とす。763／764／765向けは NBT 形式のため落とす。766／767／768／769⇔771では
  HashedSlot のため落とす）
- Set Creative Slot（763／764／765向け。データコンポーネント
  と NBT 形式のあいだの変換は未実装。766／767／768／769／770⇔771では変換できる）
- ロケーターバー／tracked_waypoint（771にしかないため、古いクライアントには送らない）
- ダイアログ show_dialog／clear_dialog（771にしかないため、古いクライアント・
  古いサーバーには送らない。設定フェーズの 0x11／0x12 も同じ）
- Change Gamemode／Custom Click Action（771にしかないため、古いサーバーには送らない。
  771サーバーへは通す）
- Custom Report Details／Server Links（設定フェーズのサーバー行き 0x08／0x09。
  771では削除されたため、771クライアントは送ってこない。770クライアント→771サーバー
  では落とす。クライアント行きの 0x0F／0x10 は771にもあるため中継する）
- Test Instance Block Status／Set Test Block／Test Instance Block Action
  （770・771にしかないため、古いクライアント・古いサーバーには送らない）
- Pick Item（763／764／765／766／767／768向け。ブロック・エンティティからスロットへの
  変換は未実装。769／770⇔771では通す）
- Player Loaded（770／771にしかないため、古いサーバーには送らない）
- Declare Recipes／Statistics／Tags／World Event ID（763／764／765／766／767／768／769／770向けと
  同じく未対応。レシピ帳・タグが空になるだけで遊べる）
- Craft Recipe Response／Craft Recipe Request／Displayed Recipe
  （770⇔771のあいだ。770のときと同じく、レシピIDの形式が変わり変換できない。
  手動クラフトは動く）
- Recipe Book Add／Remove／Settings、Sync Entity Position、Move Minecart、
  Player Rotation（770にしかないため、古いクライアントには送らない。
  771にも無いため、771クライアントには送らない）
- Select Bundle Item／Tick End（770にしかないサーバー行きパケット。
  古いサーバーには送らない。771クライアントは送らない）
- ブロックエンティティの crafter／trial_spawner（パケットごと落とす。
  766→765方向では vault も落とす。771も同じ。test_block／test_instance_block は
  770向けにエントリごと落とす）
- Entity Metadata の variant 系（763向けはエントリごと落とす。
  764では素通し。766／767／768／769／770／771では絵画・オオカミの variant を番号で変換するが、
  771にしかないもの（happy_ghast）は ghast に代替する。メタデータの個体差は残る）
- Explosion の771専用パーティクル／サウンド指定（既定演出に置き換える。
  766→763方向と同じ）
- Game Event 13（スポーン合図の765式。771はSpawn Positionを使う）
- Custom Report Details／Server Links（設定フェーズ 0x0F／0x10・プレイ
  0x81／0x82。771にはあるが、766以下のクライアントには送らない。
  767にはプレイの 0x7A／0x7B があるため番号を付け替えて送る）
- jukebox_song の Registry Data（771にしかないため、766以下のクライアントには送らない。
  tears は770向けに落とす）
- minecraft:dialog の Registry Data（771にしかないため、770以下のクライアントには送らない。
  770サーバーからは来ないため771クライアントへ合成しない）
- Configuration Acknowledged／Chunk Batch Received（内部763には無いため落とす）
- tinted_leaves／firefly 粒子（770・771にしかないため、古いクライアントには送らない。粒子ごと消える）

## 対象外・未実装（正直な一覧）

- **Window Click の変換（771→全方向）**：771の HashedSlot は成分ごとに
  hash i32 を持ち、ハッシュ算法が未検証のため、770の Slot から771の
  HashedSlot は作れない。771クライアントが古いサーバーにいるときも、
  古いクライアントが771サーバーにいるときも、Window Click は落とす。
  落としたクリックはサーバーに届かないため、771が絡む組み合わせでは
  インベントリ操作ができない制限がある（ネイティブの771⇔771では
  リレーのため問題ない）。ハッシュ算法が判明すれば変換できる見込み。
- **Set Creative Slot の変換（771→763／764／765）**：770と同じく
  未実装。771クライアントが古いサーバーにいるときのクリエイティブ
  インベントリ操作ができない（上の「プレイ中の翻訳」を参照）。766／767／768／769／770⇔771では変換できる。
- **ロケーターバー／tracked_waypoint（771）**：tracked_waypoint（0x83）は
  771にしかないため、古いクライアントには送らない。ロケーターバーは
  1.21.6の新機能で、771クライアントが770以下のサーバーにいるときは
  表示されない（サーバーが送らないため）。771同士では素通し。
- **ダイアログ（771）**：show_dialog／clear_dialog（プレイ 0x85／0x84、
  設定フェーズ 0x12／0x11）は771にしかないため、古いクライアント・
  古いサーバーには送らない。771クライアントが770以下のサーバーにいるときは
  ダイアログは表示されない。custom_click_action（プレイ 0x41・設定フェーズ
  0x08）は771サーバーへ通す。**minecraft:dialog レジストリは770サーバーからは
  来ないため、771クライアントへ合成注入しない**。実バニラクライアントが
  欠けたレジストリを内蔵既定で補うかは未確認。ダイアログを表示するサーバー側の
  仕組み（Bubble のダイアログAPI）は未実装。
- **Change Gamemode（771）**：change_gamemode（0x04）は771にしかないため、
  古いサーバーには送らない。771クライアントが770以下のサーバーにいるときは
  ゲームモード変更のサーバー行きパケットが届かない（従来の Set Player Game Mode
  パケットは無いため、771クライアントはゲームモードを変更できない。
  771サーバーへは通す）。
- **Custom Click Action（771）**：custom_click_action_to_server（プレイ 0x41・
  設定フェーズ 0x08）は771サーバーへ通すが、古いサーバーには送らない。
  771クライアントが770以下のサーバーにいるときは、ダイアログやサーバーリンクの
  クリックがサーバーに届かない。
- **ハッピーガスト（771→770）**：happy_ghast（771の56）は770では ghast（55）に
  代替する。見た目はガストになる。**メタデータの個体差が残る**：プロキシは
  エンティティ種別を追跡しないため、happy_ghast のメタデータ（stays_still 等）を
  ghast のレイアウトに直せない。Bedrock の正規化では happy_ghast はブタ代替になる
  （合成表のため。`JavaBackendConnection771Test` で確認）。
- **音の blow_out の衝突（770⇔771）**：770の `entity.puffer_fish.ambient`（1179）と
  `entity.puffer_fish.blow_out`（1180）が両方771の `entity.puffer_fish.blow_out`
  （1204）へ写る。`invert()` は先行勝ちで ambient（1179）を選ぶため、
  771→770では blow_out（1204）が ambient（1179）に戻る。元の blow_out（1180）には
  戻らない。フグの「膨らむ音」と「周囲音」が771→770では区別できなくなる
  （見た目ではなく音の制限。数値表の OVERRIDES {816,840},{817,839} は別の音で、
  この衝突とは関係ない）。
- **Test Instance Block（両方向）**：770・771の test_instance_block_status／
  set_test_block／test_instance_block_action はテストブロック専用の
  パケットで、古いバージョンに相当するものが無いため落とす。
  テストブロック・テストインスタンスブロックの操作は770・771同士でしかできない。
- **Pick Item（763／764／765／766／767／768向け）**：770のときと同じく、
  771の pick_item_from_block／pick_item_from_entity は位置・エンティティを指定し、
  旧バージョンの pick_item はスロット番号を指定するため、プロキシがワールドを
  知らない限り変換できない。1.21.6クライアントが古いサーバーのブロックを中クリックしても、
  古いクライアントが1.21.6サーバーのブロックを中クリックしても、
  アイテムは手に入らない（見た目だけの制限ではなく、操作が無効になる制限）。
- **Player Loaded（771）**：771クライアントが送る player_loaded（0x2B、空）は
  古いサーバーには送らない。771サーバーが player_loaded を前提に
  処理を進めるかどうかは未確認だが、統合テストでは参加・スポーン・
  チャット・移動が player_loaded 無しで動いたため、参加の障害には
  なっていない（下の「テスト」を参照）。
- **チャット checksum（770⇔771）**：770・771とも末尾に checksum u8 があるため、
  付け外しは不要。checksum の本当の算法は未確認のため、署名付きチャットや
  online-mode での挙動は別途確認が必要。
  769→770では `0x00` を書き足す（**検証済み**：統合テスト
  `fake769ClientJoinsRealVanilla1215ThroughProxy` で、769クライアントのチャットが
  プロキシ経由で実バニラ1.21.5に届いた。0x00 が受け入れられる）。
  771→770・770→771では番号だけ付け替える。
- **Player Chat の globalIndex（770⇔771）**：770・771とも先頭に globalIndex があるため
  素通しする。プロキシのセッションごとのカウンタ（0から）は769サーバーからの
  player_chat に付ける。実バニラが globalIndex の連番を厳密に
  検証するかは未確認。`message_acknowledgement` が771で globalIndex
  ベースに変わっているかも未確認（771クライアントのチャット履歴の
  既読確認がずれる可能性がある）。
- **Heightmap の type id 順序（770⇔771）**：バニラの `Heightmap.Types` の
  宣言順と仮定する（0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、
  3=OCEAN_FLOOR、4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES）。
  配列の形式自体は実バニラ1.21.6のチャンクで確認済み（2026-11-12のプローブ）が、
  type id と種別名の対応の仮定は未確認のまま。
  順序が違うとチャンクの高さ情報がずれ、地形の表示が壊れる。
- **Vehicle Move の onGround（768クライアント→770サーバー）**：**770段階の既存の
  不具合**。`handleClientPlayTo770` の Vehicle Move 分岐は
  `translateVehicleMove769To770`（素通し）を呼ぶため、768（・767以下）クライアントの
  onGround 無しペイロードが770サーバーへそのまま送られる（770サーバーは末尾の
  onGround を読み取ろうとして失敗するはず）。771のスコープ外のため直していない。
  771の To771 分岐では正しく直してある（is769() は素通し、それ以外は
  `translateVehicleMove768To769` で onGround=false を補う）。
- **From770 の一般プレイ分岐に is768() が無い（770段階の既存の挙動）**：
  770サーバー＋768クライアントの一般プレイは落とされる。771では変更しない
  （771の From771 分岐には is768()／is769() を追加してある）。
- **ネイティブ771同士の再設定はセッションが追跡しない**：771同士は Login Success 後に
  即プレイ状態になり、セッションは再設定を追跡しない（770のネイティブ同士と同じ）。
  Start Configuration（0x6F）はそのまま中継され、設定フェーズの Custom Click Action
  （0x08）はプレイ状態の Chat Message（0x08）と番号が同じためバイト列のままサーバーへ
  届く（サーバーは設定状態で読むため正しく解釈する）。
  `ProxySession771Test.configCustomClickAction771RelayedNativelyTo771Server` で確認。
- **creaking_heart の uprooted（770⇔771）**：770と同じ制限（771の
  `creaking_heart_state` は uprooted／dormant／awake の3値で、769の `active`
  （bool）は uprooted を表せないため、770→769では uprooted を `active=false`
  （dormant 相当）に落とす。770⇔771ではそのまま通す）。
- **Player Info の showHat／listPriority の順番（771→770）**：770と同じ仮定。
  per-player のフィールド順は minecraft-data の protocol.json（listPriority の
  varint が先、showHat の bool が後）に従った。wiki.vg の旧ページでは確認できず、
  実バニラ1.21.6の Player Info パケットで両ビットが同時に立つ例は
  未確認。順番が逆だと Player Info の読み取りがずれ、プレイヤーリストが
  壊れるため、実機での確認が必要（統合テストでは hat＋list_order 付きの
  Player Info を771→770へ変換する単体テストで検証済みだが、実バニラの
  パケット順は未確認）。
- **BannerPatternLayer のインライン模様（771→770）**：770のときと同じく、
  インラインの BannerPattern NBT はそのまま通すベストエフォート。
- **ポーションのID（770⇔771）**：ポーションの対応表はコードベースのどこにも無く、
  769↔770でも同じ扱い（不透明コピー）のため、770↔771でも同じ扱い。
  1.21.6で新しいポーションは無いため、番号がずれていないと仮定している
  が、ワイヤでの確認はできていない。
- **ボートの木の種類**：770と同じ制限。767のボートは771では常にオークのボートに、
  771のボートは767では常に（木の種類を問わず）ボートになる。ボートの
  variant（木の種類）をエンティティ種別に直す変換は未実装。
  767のボート variant の並び（OAK〜BAMBOO、PALE_OAK=9）はバニラの
  `Boat$Type` の宣言順からの推測で、実機未確認。
- **food の効果（767→768→769→770→771）**：770のときと同じ制限。金リンゴ等の食べたときの
  効果は768／769／770／771向けに落とす（767の food の効果は amplifier／duration を
  持たないため、768／769／770／771の `consumable` の apply_effects 形式へは変換できない）。
- **custom_model_data（767→768→769→770→771）**：770のときと同じ制限。767の単一の数値を
  768／769／770／771の colors[0] に入れるだけのベストエフォート。floats／flags／strings は
  空になる。
- **Set Cooldown（771のグループ名）**：770と同じく、エンダーパール・コーラスフルーツ・
  ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす
  （クールダウンの表示だけの制限）。
- **Trade List の ItemCost（767↔768）**：767↔768の翻訳子は「count varint＋その個数分の
  Slot」と読む誤りのままで、771の範囲外のため直していない。768↔769と769↔770と770↔771は
  正しい形に直してある。
- **Declare Recipes／Tags／Statistics／World Event ID**：未対応。
- **Entity Metadata のスロット・ブロック状態・粒子の中身**：764では素通しの
  ため未変換のまま残る（763向けは variant 系を落とす）。
- **771＋online-modeの他プレイヤーから見たスキン**：763と同じ制限。
  UUID・名前は認証済みのものが使われる。
- **実機クライアントでの確認**：未実施。生ソケットで771クライアントを装った
  統合テスト（下の「テスト」を参照）と、プロキシの単体テスト
  （`ProxySession771Test`）で検証しています。
- **内部771サーバーの統合テスト**：実施済み（下の「テスト」を参照）。
  本物のバニラ1.21.6サーバー自体の実起動（`Done` まで）も確認済み。
- **1.21.7以降（772〜）は未対応**：プロキシは771より新しいプロトコルを
  ログイン時に断る（「新しすぎます」）。

## Java 21（内部771サーバーを動かすとき）

バニラ1.21.6サーバーは Java 21 を要求します（Minecraft Wiki の
1.21.6 記事：Minimum Java version Java SE 21）。DetergentMC本体は
Java 17 で動きます。`VanillaServerHost` は内部バージョンが1.21.6の
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

`docs/PLAYTEST-1.21.6.md` を参照してください。

## テスト

- `detergentmc-protocol`：`PacketIds771Test`（771のID表：58件の変更・
  新規8件・削除2件）、`ClientAdapter771Test`（771の受け入れ判定・
  全内部プロトコルとの振り分け）、`ClientJoinFlow771Test`（771の参加フロー）、
  `RegistryRemapper771Test`（770↔771対応表・合成表・往復・blow_out の衝突）、
  `ChunkTranslator771Test`（770↔771変換・heightmap の直接配列・スロットの成分変換
  （jukebox_playable の tears・break_sound・tooltip_display）・Set Creative Slot の
  UntrustedSlot・Window Click の投げ・Entity Metadata の variant・
  Spawn Entity の happy_ghast→ghast 代替・Explosion の f64 knockback・
  Update Attributes の waypoint／camera_distance の落とし）、
  `PlayTranslator771Test`（Player Chat の globalIndex 素通し・チャット checksum の
  付け外し（道具として残る）・Registry Data の identity・jukebox_song の tears 挿入・
  dialog レジストリの落とし）、
  `ProtocolVersion771Test`（1.21.6→771）、
  `ResourcePackBuilder771Test`（pack_format 63）、
  `ResourcePackMode771Test`（url-771／sha1-771）、
  `ResourcePackServer771Test`（9パックの配信）、
  `ProxySession771Test`（771のプロキシ参加フロー12件：771クライアント×内部771の
  素通し、771クライアント×内部770のスロット変換、770クライアント×内部771の
  スロット逆変換、771クライアント×内部763の設定フェーズ合成、
  763クライアント×内部771の設定フェーズ消費、771クライアント×内部766の
  Join Game 変換、771クライアント×内部765の join レベル変換、
  769クライアント×内部771のスロット逆変換、771クライアントの Custom Click Action
  （770サーバー向けに落とす・ネイティブ771同士で中継する）、770クライアントの
  Custom Report Details／Server Links（771サーバー向けに落とす）、
  Registry Data の771→770並べ替え（バイオーム65件維持））。
  プロトコルモジュールは1,309件→**1,416件**（0失敗。ステージ2開始時から+107件）。
  検証スクリプト `detergentmc-protocol/tools/verify-771-tables.py`。
- `detergentmc-core`：`ServerProtocolWiringTest`（内部771の配線・771用パックの
  受け渡し）、`AdapterDiscoveryTest`（V1216Adapter の ServiceLoader 発見。
  ステージ1では settings.gradle にしか無かったため、core の build.gradle に
  runtimeOnly／testImplementation／testRuntimeOnly の
  detergentmc-adapter-v1_21_6 を追加した）、`VanillaServerHostTest`（Java 21 の選択）、
  `ResourcePackWiringTest`（9パックの配信・771用パックのバニラ渡し。
  770段階では8パック分のテストが無かったため、771で9パック分を新設した。
  `ClientProxy771Test` という単体テストは無い——771の ClientProxy 構築は
  `ClientProxyTest` の検証と統合テストでの構築で確認している）、
  `ProxyIntegrationTest.fake771ClientJoinsRealVanilla1216ThroughProxy`
  （偽771クライアントが本物のバニラ1.21.6に参加）、
  `ProxyIntegrationTest.fake771ClientJoinsRealVanilla1215ThroughProxy`
  （偽771クライアントが本物のバニラ1.21.5に参加）、
  `ProxyIntegrationTest.fake770ClientJoinsRealVanilla1216ThroughProxy`
  （偽770クライアントが本物のバニラ1.21.6に参加）、
  `ProxyIntegrationTest.fake770ClientJoinsRealVanilla1215ThroughProxy`
  （偽770クライアントが本物のバニラ1.21.5に参加、回帰）。
  771クライアント×内部769／768／767／766／764／763サーバーの統合テストは無い
  （単体テストのみ。`ProxySession771Test` の参加フローと `ClientAdapter` の
  全組み合わせ判定でカバーしている）。
  `RegistryOrderCaptureTest.registryOrderOfRealVanilla1216`（本物のバニラ1.21.6から
  Registry Data の順番を記録し、対応表の仮説を検証する。**実施済み**。
  ジュークボックスの歌は20件（tears=17・wait=18・ward=19）、属性は35件
  （camera_distance=8・waypoint_transmit_range=33・waypoint_receive_range=34）、
  dialog は3件（custom_options=0・quick_actions=1・server_links=2）、
  ダメージ種別は49件・770と同じ並び、エンチャントは42件、絵画は50件、
  旗の模様は43件、トリムは18件、バイオームは65件（pale_garden=39・plains=40）、
  ブロックエンティティ種別は47件、楽器は8件（admire=0・yearn=7）。
  既存の統合テスト（`ProxyIntegrationTest` の fake* 42件）も回帰テストとして残っています。
- `detergentmc-bedrock`：`JavaBackendConnection771Test`（Bedrock→内部771の
  参加・Join Game 0x2B・Player Info 0x3F（hat／listOrder ビット）・
  Chat Message 0x08＋checksum 0x00・Client Information 0x00・
  Update Attributes の771専用属性表（movement_speed は770の21→22、
  camera_distance 8 は無視される）・スロットの771正規化（771新アイテム619→石1、
  oak_boat 827→765の773）・ブロック状態の771正規化（trial_spawner 27730→26638、
  乾燥ガスト 13826→石1）・エンティティの771正規化（ブタ95→73、ガスト55→42、
  ハッピーガスト56→ブタ73）。10件・0失敗）。
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_6`（Bedrock 6 プロトコル ×
  内部771の統合テスト）。10件・0失敗）。
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_6`（Bedrock 6 プロトコル ×
  内部771の統合テスト。参加・スポーン・チャット・移動・チャンク・アイテム）。
  **実施済み**（2026-11-12。Join Game 771の形式が768と同一かの未確認分も
  ここで検証できた——Bedrock バックエンドが771の Join Game を正しく読めた）。
