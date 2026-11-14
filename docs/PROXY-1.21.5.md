# 1.21.5クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.21.5クライアント（プロトコル770）は、内部プロトコルが
763／764／765／766／767／768／769／770のどのサーバーにも参加できます。内部770のときは
素通し（リレー）、それ以外は上向き・下向き変換です。769との違いは大きく、
**クライアント行きのプレイ117件のIDがすべて1つ下がり（Spawn Entity
Experience Orb の削除による）、サーバー行きのプレイ5件が変わり、新規3件・
削除1件**です。レジストリも769→770ではブロック状態が60件増える程度ですが、
データコンポーネント種別が67→96に増え、チャンクのheightmapがNBTから
直接の配列に変わり、Player ChatにglobalIndexが、チャット送信にchecksumが
付きました。いちばんの変化はスロットで、Window ClickはHashedSlotに、
Set Creative SlotはUntrustedSlotになり、Window Clickはハッシュ算法が
未検証のため変換できず落とします。くわしい差分は下の各節を参照してください。
769の仕組みは `docs/PROXY-1.21.4.md`、768は `docs/PROXY-1.21.2.md`、767は
`docs/PROXY-1.21.md`、766は `docs/PROXY-1.20.5.md`、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。

全64通り（クライアント763／764／765／766／767／768／769／770 × サーバー763／764／765／
766／767／768／769／770）の対応マトリクスは `docs/VERSIONS.md` の「1.21.5対応：
全64通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用・767用・768用・769用・770用の8つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 769→770の差分（要点）

| 分野 | 769→770の差分 | 扱い |
| --- | --- | --- |
| パケットID | クライアント行きのプレイ117件がすべて1つ下がる（`spawn_entity_experience_orb` 0x02 の削除による。Join Game 0x2C→0x2B、Spawn Position 0x5B→0x5A、Keep Alive 0x27→0x26、Ping 0x37→0x36、Start Configuration 0x70→0x6F、Disconnect 0x1D→0x1C 、Respawn 0x4C→0x4B）。サーバー行きのプレイ5件が変わる（update_sign 0x39→0x3A、arm_animation 0x3A→0x3B、spectate 0x3B→0x3D、block_place 0x3C→0x3E、use_item 0x3D→0x3F）。新規は test_instance_block_status（0x77、クライアント行き）と set_test_block（0x39）・test_instance_block_action（0x3C）の3件。削除は spawn_entity_experience_orb（769の 0x02）。ping_request（0x24）と legacy_server_list_ping（0xFE）は表に入れず、プロキシは落とす（既存の方針） | 汎用の `PacketTranslator.translateId` で番号を付け替える。新規・削除パケットは古いクライアントには送らない（落とす）。770同士は素通し |
| Login Success | 770も769と同じく末尾の `strictErrorHandling` bool が無い（765と同じ形式） | そのまま通す。767向けは `false` を付け足す（769と同じ） |
| Join Game／Respawn | 769と770で形式は同じ（SpawnInfo の末尾の `seaLevel` varint を含む）。番号だけ1つ下がる（Join Game 0x2B、Respawn 0x4B） | 番号だけ付け替える。767向けは seaLevel（63）を落とす（769と同じ） |
| Player Chat（クライアント行き） | 先頭に `globalIndex` varint が増えた（769は senderUuid から始まる） | 769→770ではプロキシのセッションごとのカウンタ（0から）で書き足す。770→769では落とす。カウンタは `ProxySession.chatGlobalIndex`。`message_acknowledgement` が770で globalIndex ベースに変わっているかは未確認（下の「対象外・未実装」を参照） |
| Chat Message／Chat Command Signed（サーバー行き） | 末尾に `checksum` u8 が増えた（769は signature・offset・acknowledged まで）。符号なしの Chat Command（0x05）は変わらない（checksum 無し） | 770→769では末尾の1バイトを落とす（機械的）。769→770では `0x00` を書き足す（**検証済み**。統合テスト `fake769ClientJoinsRealVanilla1215ThroughProxy` で実バニラ1.21.5にチャットが届いた。ViaVersion の `Protocol1_21_4To1_21_5` は1.21.5→1.21.4方向で checksum を読み捨てるだけで、逆向きの値を教えてくれない。checksum の本当の算法は未確認） |
| Heightmap（Chunk Data） | NBT（compound、キーは種別名、値は long[]）から直接の配列（varint 個数＋{type varint、data long[]}）に変わった。chunkData は 769の `buffer` から770の `ByteArray` に型名が変わったが、ワイヤ形式は同じ varint 長＋バイト列と仮定する | NBT↔配列で変換する。type id の順序はバニラの `Heightmap.Types` の宣言順と仮定する（0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、3=OCEAN_FLOOR、4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES）。配列の形式自体は実バニラのチャンクで確認済み（BedrockMatrixIntegrationTest で3件・各37 long を読めた）。type id の順序の仮定は未確認のまま（下の「対象外・未実装」を参照） |
| チャンクセクションの Data Array（Chunk Data） | **パレット付きコンテナの Data Array から長さの varint 接頭辞が無くなった**（769までは `長さ varint＋long[]`、770は `long[]` だけ。長さは bitsPerEntry とエントリ数から計算する）。minecraft-data の protocol.json は chunkData を不透明な ByteArray として扱うため、この変更は protocol.json には現れない | 769→770では長さ varint を読んで検証するが書かない。770→769では長さを読まず、bpe から計算した長さを書く。**実バニラ1.21.5のチャンクで確認済み**（`BedrockMatrixIntegrationTest.matrixAgainst1_21_5` で発覚・修正。長さ varint を読むと 0x33=51 になってセクションが壊れる。Bedrock の `BedrockChunkTranslator.readPalettedContainer` とプロキシの `ChunkTranslator.translatePalettedContainer769To770`／`770To769` の両方を直した。シングルパレット（bpe=0）も770では長さ varint（0）が無い）。なお、heightmap の long[] には長さ varint が残っている（別の箇所のため混同しないこと） |
| Window Click | HashedSlot になった（changedSlots は `{location i16, item option<HashedSlot>}`、cursorItem は `option<HashedSlot>`。HashedSlot は `{itemId、itemCount、components[{type、hash i32}]、removeComponents[{type}]}`） | **変換できず落とす**。ハッシュ算法（i32）が未検証のため、769の Slot から770の HashedSlot は作れない。770クライアントが古いサーバーにいるときも、古いクライアントが770サーバーにいるときも、Window Click は落とす（下の「対象外・未実装」を参照）。`ChunkTranslator.translateWindowClick769To770`／`770To769` は `UnsupportedOperationException` を投げる |
| Set Creative Slot | UntrustedSlot になった（`{itemCount varint、itemId、addedComponentCount、removedComponentCount、components[{type、data ByteArray}]、removeComponents[{type}]}`）。769は Slot（成分は直接のデータ） | 成分を ByteArray のまま写し、種別番号だけ対応表で直す（ハッシュ不要）。両方向とも変換できる |
| スロット（データコンポーネント） | 種別が67→96に増えた。`hide_tooltip`／`hide_additional_tooltip` は `tooltip_display`（`{hideTooltip bool、hiddenComponents varint[]}`）に統合された。`enchantments`／`stored_enchantments`／`trim`／`jukebox_playable`／`can_place_on`／`can_break` は末尾の showTooltip／showInTooltip bool が消えた。`unbreakable` は bool→void、`lore` は型名だけ変わり（`anonOptionalNbt[]`→`anonymousNbt[]`、ワイヤ形式は同じため変換不要）、`dyed_color` は `{色 i32、showTooltip}`→`色 i32`、`attribute_modifiers` は `{配列、showTooltip}`→`配列だけ`、`tool` は末尾に `canDestroyBlocksInCreative` bool が増えた、`instrument` は `registryEntryHolder`→`{hasHolder bool、data}`、`trim` の材料は `assetName`→`assetBase` で `ingredientId` が消え、模様は `templateItemId` が消えた。新種別は weapon（26）・blocks_attacks（33）・potion_duration_scale（43）・provides_trim_material（53）・provides_banner_patterns（56）・break_sound（71）・chicken／cow／pig／wolf_sound variant（86〜88、90）・painting／variant（89）等 | 種別番号の対応表で変換する。770にしかない種別は769向けに落とす（成分ごと消える）。`hide_tooltip`→`tooltip_display{hideTooltip=true、[]}`、`hide_additional_tooltip`→`tooltip_display{hideTooltip=false、hidden=[…]}`。逆向きは `hideTooltip=true`→`hide_tooltip`、`hidden>0`→`hide_additional_tooltip`、両方無し→落とす。`instrument` は769の holder を770の `{hasHolder=true、holder}` に包む。逆向きで `hasHolder=false`（文字列名）のときは成分を落とす（769に文字列形が無い）。`trim` の材料・模様はインラインのときだけ逆向きの ingredient／template のアイテムIDを表から引く（`trimMaterialIngredient769`／`trimPatternTemplate769`） |
| Entity Metadata | variant 系の種別番号がずれた。769は 23=wolf_variant（holder）、24=frog_variant、25=optional_global_pos、26=painting_variant（holder）、27=sniffer_state、28=armadillo_state、29=vector3、30=quaternion。770は 23=cow_variant、24=wolf_variant（素の varint）、25=wolf_sound_variant、26=frog_variant、27=pig_variant、28=chicken_variant、29=optional_global_pos、30=painting_variant（holder）、31=sniffer_state、32=armadillo_state、33=vector3、34=quaternion。wolf_variant は holder→素の varint に変わった | 種別番号を対応表で直す。770にしかない variant（cow／wolf_sound／pig／chicken）は769向けにエントリごと落とす。wolf_variant は769の holder（id+1）を770の素の varint（id）に直し、インラインの holder（0）は落とす。逆向きは varint を holder（id+1）に直す。painting_variant は holder のまま番号だけ直す（インラインの絵画データはそのまま通す） |
| World Particles | `tinted_leaves`（35、データは i32）が `tinted_leaves` の位置に挿入され、34番以降が1つずれる（trail 47→48、block_crumble 111→112）。`firefly`（113、データ無し）が末尾に追加された | 対応表で変換する。770にしかない tinted_leaves／firefly は769向けにパケットごと落とす（落とす前にデータを読む。tinted_leaves は i32 を読み飛ばす。読み飛ばさないと後続が壊れる） |
| ブロック状態 | 27,866→27,914件（+60、−12）。茂み・短い／背の高い枯れ草・サボテンの花・テストブロック・テストインスタンスブロック・ワイルドフラワー（16）・リーフリッター（16）・ホタルブッシュの追加。`creaking_heart` のプロパティが `[active=bool、axis、natural=bool]`（12状態）から `[axis、creaking_heart_state=uprooted/dormant/awake、natural=bool]`（18状態）に変わった | 対応表で変換。770にしかないブロック状態は古いサーバー向けに石へ。creaking_heart は769の `active=true`→awake、`active=false`→dormant（natural は維持）。逆向きは uprooted→`active=false`（769の active は uprooted を表せないため dormant／uprooted の区別は消える）。770の `uprooted` 6件は `BLOCK_STATE_UP_770_CORRECTIONS` で補正する |
| アイテム | 1,385→1,396件（+11）。青・茶の卵、茂み、ホタルブッシュ、短い／背の高い枯れ草、ワイルドフラワー、リーフリッター、サボテンの花、テストブロック、テストインスタンスブロックの追加 | 対応表で変換。770にしかないアイテムは古いサーバー向けに石へ |
| エンティティ | 149→150件（+1）。`potion`（99）が `splash_potion`（99）と `lingering_potion`（100）に分割された（potion は削除） | 対応表で変換。769の potion は770向けに splash_potion へ、770の lingering_potion は769向けに potion へ（`ENTITY_UP_770_CORRECTIONS`） |
| 音 | 1,651→1,702件（+52、−1）。`entity.wolf.howl` が削除され、36件のオオカミ音変種・鉄の5件・リーフリッターの5件・サボテンの花の2件・砂の2件・枯れ茂み／ホタルブッシュの2件などが追加された | 対応表で変換。削除された wolf.howl は769向けに wolf.ambient へ（`SOUND_FALLBACK_770`）。770にしかない音は古いサーバー向けに石を壊す音へ |
| パーティクル | 112→114件。`tinted_leaves`（35）・`firefly`（113）の追加 | 対応表で変換（上の World Particles を参照） |
| 属性 | 32件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エンチャント | 42件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| バイオーム | 65件で同じ顔ぶれ・同じ順番（pale_garden は 39、plains は 40） | 変換なし（identity）。Registry Data のバイオームは769と770で同じ順番のため、並べ替えは不要 |
| ブロックエンティティ種別 | 45→47件。`test_block`（45）・`test_instance_block`（46）が末尾に追加された | 対応表で変換。770にしかない test_block／test_instance_block は769向けにエントリごと落とす（`mapBlockEntityTypeUp770` は 45／46 を DROP） |
| 絵画・旗の模様・トリム・ジュークボックスの歌 | 変わらず | 変換なし。`jukebox_song` は770にもあるため、Registry Data で落とさない（`reorderRegistries` の除外対象に 770 を追加済み。769のときの罠で、770を漏らすと jukebox_song が落とされる） |
| ダメージ種別 | 変わらず | 変換なし（identity） |
| メニュー | 25件で変わらず | 変換なし |
| Client Information（Settings） | 769と同じ9フィールド（末尾に `particleStatus` varint） | そのまま通す。**769のときの罠（768クライアント→769サーバーで particleStatus を落とす実バグ）と同じく、770サーバー向けは落としてはいけない**。770も768／769と同じ9フィールドのため、素通しする |
| Player Info | 769と同じ8ビット（update_hat／update_list_order を含む）。フィールド順は listPriority の後に showHat（minecraft-data に従った仮定。769と同じ） | そのまま通す（identity） |
| Set Held Item／Vehicle Move | 769と同じ形式 | そのまま通す（identity） |
| Spawn Entity Experience Orb | 769の spawn_entity_experience_orb（0x02）が削除され、770では通常の Spawn Entity（type 47 experience_orb）に統合された | 769→770では orb（entityId＋座標＋count i16）を Spawn Entity（新規 UUID、type 47、data=count）に直す。770→769では type 47 の Spawn Entity を orb に直す。**data varint が経験値であることは仮定**（実バニラキャプチャで未確認。count は 0〜32767 に丸める） |
| Trade List | 入力の形式が変わった。769は `{itemId、itemCount、addedComponentCount、components[]}`、770は `{itemId、itemCount、components: ExactComponentMatcher}`（ワイヤ上は同じ「varint 個数＋SlotComponent」だが、成分種別の番号が770式） | 成分種別の番号だけ対応表で直す。なお、768↔769の ItemCost 翻訳子は「count varint＋その個数分の Slot」と読む誤りがあった（768／769のときの実バグ）。770の作業で768↔769の翻訳子も正しい形（`{itemId、itemCount、addedComponentCount、components[]}`）に直し、769のテストも直した。767↔768の翻訳子は同じ誤りのままで、770の範囲外のため直していない（下の「対象外・未実装」を参照） |

検証スクリプトは `detergentmc-protocol/tools/verify-770-tables.py`
（243件のチェック）。本物のバニラ1.21.5から Registry Data の順番を
そのまま記録する統合テスト（`RegistryOrderCaptureTest` の1.21.5版）は
未実施（下の「テスト」を参照）。
実バニラが設定フェーズで送るレジストリは769と同じ12レジストリ
（banner_pattern／chat_type／damage_type／dimension_type／enchantment／
instrument／jukebox_song／painting_variant／trim_material／trim_pattern／
wolf_variant／worldgen/biome）と仮定する（770での確認は未実施）。
`minecraft:attribute` と `minecraft:block_entity_type` は送られないため、
属性の順番とブロックエンティティ種別の順番は ViaVersion の
mapping-1.21.5.json と jar の data ファイルに依り、ワイヤでの確認は
できていない（769と同じ制限）。

**769→770の連鎖の方針：** 770クライアント→769サーバーの変換は、
770→769へ直してから既存の `handleClientPlay769` に渡す二段方式。
ただし `handleClientPlay769` はクライアントが769であることを前提に
書かれているため、内部769サーバーのときだけは770→769へ直した
パケットを直接バニラへ送り、それ以外（内部763／764／765／766／767／768）の
ときだけ `handleClientPlay769` に渡す。同じ罠はサーバー→クライアント側にも
あり、769サーバーから770クライアントへの Join Game／Respawn／Disconnect は
770向けの分岐を `handleServerPlayFrom769`／`handleServerJoinGame769` に
追加してある（追加しないと768形式の Join Game 0x2C が770クライアントに
届く）。767／766サーバーから770クライアントへは 767→768→769→770 の
四段・三段で変換する。763サーバーから770クライアントへは、合成される
Join Game を769形式で作る（`handleSynthesizedJoinGame` の分岐。
**770を漏らすと768形式の Join Game（0x2C）が770クライアントに届く**）。
設定フェーズの辞書は768形式で作り、770→768へ並べ替えてから
`removePaleGardenBiome` で pale_garden を落とす（763／764／765クライアント向け）。

**設定フェーズの振り分けの注意：** 770クライアント→769サーバーの設定フェーズは、
専用の `ProxySession.handleServerConfiguration770From769`
（Registry Data の769→770並べ替え・Tags 落とし・Cookie／Select Known Packs の
空応答）を通す。振り分けで `is769()` の分岐（`isServer769()` の素通し）だけを
見ると、770サーバーからの Registry Data が764／765向けの消費者に落ちて
770の Registry Data が769クライアントに届かなくなる。
`handleServerConfiguration` では `is769()` の分岐で `isServer770()` を
`isServer769()` と同じく先に見る（769→768の設定フェーズの振り分けの
罠と同じ）。**770の作業中に、振り分けのシフトで `is763()` クライアントの
`isServer768()` 分岐が `isServer769()` に化けて消える実バグがあった**
（763クライアント＋768サーバーで Registry Data が消費されなくなった。
`ProxySession768Test.join763Against768ConsumesConfigAs768Client` で発覚し、
分岐を復旧して `handleServerConfiguration770From768` を新設して直した）。

**Client Information の罠（770クライアント→769サーバー）：**
`handleClientConfiguration769` は「Settings の末尾の particleStatus を
旧サーバー向けに落とす」と書かれているが、770も769と同じ9フィールドの
ため、769サーバー向けは落としてはいけない。770の作業では
`handleClientConfiguration770` を `handleClientConfiguration769` に
委譲し、Settings の変換は769のものを使い回す（9フィールドが同一のため）。

**ProxySession770Test で見つかった770漏れ（6件、すべて修正済み）：**
770の変換は「769の分岐に770を足す」形で入れたが、769の分岐にはない
場所・769の分岐にない新しい分岐が6か所あり、テストで発覚した。
(1) `handleSynthesizedJoinGame`：770クライアントの分岐が無く、else 分岐
（765形式・0x29）に落ちていた。770は769と同形式だが番号は 0x2B のため、
専用の分岐を追加した。(2) `handleServerJoinGame763`：同じく770分岐が無く
else 分岐（0x29）に落ちていた。770分岐（0x2B、Add Resource Pack は 0x4A）
を追加した。(3) `handleServerJoinGame`（764／765サーバー）：770分岐が無く、
763分岐（0x28）に落ちていた。770分岐（0x2B、Add Resource Pack は 0x4A）を
追加した。(4) `handleServerConfiguration`：`is769()`／`is768()` の分岐に
`isServer770()` が無く、769／768クライアント＋770サーバーの Registry Data が
汎用ハンドラに落ちて転送されなかった。`isServer770()` 分岐を追加し、
`handleServerConfiguration769From770`（770→769一段）・
`handleServerConfiguration768From770`（770→769→768二段）を新設した。
(5) `handleServerPlayUp`（765サーバー）：条件に770が無く、Keep Alive／Ping／
Disconnect／Respawn／Add Resource Pack が汎用経路に落ちていた（Respawn の
ペイロードが765形式のまま、Disconnect が JSON のままになる）。条件に770を
追加し、Ping 0x36／Disconnect 0x1C／Respawn 0x4B／Add Resource Pack 0x4A
に直した（Keep Alive は 0x26 のままで正しい）。(6) `handleClientConfiguration768`：
Settings（0x00）の条件に `!isServer770()` が無く、770サーバー向けの Settings が
8フィールドに落とされ、Finish ack が 0x02 に付け替えられていた。条件に
`isServer770()` を追加した（769サーバー向けは従来通り）。

### なぜ「並べ替え」が必要か

769／770 の設定フェーズの Registry Data は、レジストリごとの
エントリ配列形式です（1個の NBT codec ではありません）。各エントリは
「名前＋NBT」で、番号は配列の位置そのものです。バイオームの並びが
769（65件、pale_garden=39・plains=40）と770（65件、同じ）で同じため、
バイオームの並べ替えは不要ですが、ブロック状態・アイテム・
エンティティ・音・パーティクルの並びが変わるため、配列をそのまま中継すると
番号がずれ、別のブロック・アイテムになってしまいます。プロキシは
Registry Data を受け取ったら、エントリを名前で並べ替え、送る側の正しい
順番に直してから送ります。送る側に無いエントリ（770の新ブロック・新アイテム等）は、
769向けには**落とす**のではなく、名前だけのエントリ（データなし）として送り、
番号をずらさないようにします
（770→769の逆向きは、769に無いエントリを 770の位置に挿入する）。
`PlayTranslator.translateRegistryData769To770`／`770To769` を参照してください。

## 接続の流れ

### 1.21.5クライアント（770、offline-mode）

内部プロトコルで流れが分かれます。

**内部770（1.21.5サーバー）のとき：素通し（リレー）。**
ハンドシェイクの直後にリレーへ切り替わり、設定フェーズ・プレイは
生バイトのまま通します。設定フェーズの Select Known Packs（0x0E）も
そのままクライアントへ届くため、クライアントは空の一覧で答える必要が
あります（本物の1.21.5クライアントは答えます。偽クライアントのテスト
`Fake770Client` も答えます）。

**内部769（1.21.4サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**

1. ハンドシェイクのプロトコルVarIntを770→769に書き換えてバニラへ送る。
2. Login Startは769と同形式（名前＋UUID、bool なし）のため変換しない。
   Login Successは769も770も末尾の `strictErrorHandling` bool が無いため
   そのまま通す。
3. ログイン成功後、クライアントが Login Acknowledged（770は 0x03）を送ると
   両方とも設定フェーズに入る。Client Information（770は 0x00）は末尾の
   particleStatus を落とさず769形式のままバニラへ送る（770も9フィールドの
   ため、そのまま通す）。バニラが送る Registry Data（769は 0x07、
   エントリ配列）は770の順序に並べ替え（新ブロック・新アイテムを挿入）して中継する。
   Custom Report Details（0x0F）・Server Links（0x10）は769と770で番号が
   同じため中継する。Tags はレジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x03→770も 0x03）を中継し、クライアントの
   応答（0x03）がバニラに届いた時点で両方ともプレイ状態に入る。
5. バニラが送るJoin Game（769は 0x2C）は770の 0x2B へ番号だけ付け替える
   （形式は同じ）。スロットを含むパケット（Set Slot／Window Items／
   Entity Equipment など）は769→770へ変換する
   （`ChunkTranslator` の770変換を参照）。
6. スポーンは770式。Spawn Position（770は 0x5A）が合図で、769の
   Game Event 13は770クライアントに送らない。

**内部768（1.21.2／1.21.3サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
769のときと同じ流れで、Registry Data は768→769→770の二段で並べ替える。
jukebox_song は768にあるため、770クライアントは受け取る。

**内部767（1.21／1.21.1サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
768のときと同じ流れで、Registry Data は767→768→769→770の三段で並べ替える。
jukebox_song は767にあるため、770クライアントは受け取る
（766サーバーは送らない）。

**内部766（1.20.5／1.20.6サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
767のときと同じ流れで、Registry Data は766→767→768→769→770の四段で並べ替える。
jukebox_song は766に無いため、770クライアントは受け取らない
（766サーバーは送らない）。

**内部765（1.20.4サーバー）・内部764（1.20.2サーバー）のとき：
設定フェーズを中継し、Registry Data の形式と順序を変換する。**

1. ハンドシェイクのプロトコルVarIntを770→765／764に書き換えてバニラへ送る。
2. Login Startは765・764と同形式のため変換しない。Login Successは
   770に strictErrorHandling が無いため、そのまま765／764形式で通す。
3. ログイン成功後、クライアントが Login Acknowledged（770は 0x03）を送ると
   両方とも設定フェーズに入る。Client Information は particleStatus を
   落としてからバニラへ送る。バニラが送る Registry Data（764／765は
   0x05、1個の名前なし NBT codec）は、770のエントリ配列形式（0x07）に
   変換し、さらに770の順序に並べ替えて中継する。逆向き（内部770のときの
   764／765クライアント）も同じくエントリ配列→codec に変換する。Tags は
   レジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x02→770は 0x03）を中継し、クライアントの
   応答（770は 0x03→バニラ側 0x02）がバニラに届いた時点で両方とも
   プレイ状態に入る。
5. バニラが送るJoin Game（764／765は 0x29）は770の 0x2B へ変換する。
   dimension は番号から文字列に戻す（764／765向けはその逆）。
6. スポーンは770式。Spawn Position（770は 0x5A）が合図で、765の
   Game Event 13は770クライアントに送らない。

**内部763（1.20／1.20.1サーバー）のとき：設定フェーズを合成する。**
内部763には設定フェーズが無いため、プロキシが Join Game の codec から
Registry Data（770形式 0x07、レジストリごとのエントリ配列）と
Finish Configuration（770は 0x03）を合成してクライアントへ送る。
Registry Data は766形式で作ってから 766→767→768→769→770 の四段で並べ替え、
新ブロック・新アイテムを挿入する。クライアントの Finish 応答（770は 0x03）が
来たら、預かっていた Join Game（770は 0x2B）を送ってプレイ状態に入る。
合成の間、サーバーから届いたプレイ状態のパケットは溜めておき、Finish 応答の
後にまとめて送る。この仕組みは764／765／766／767／768／769クライアントの
内部763向け合成と同じ経路で、770だけ Registry Data の形式が違う
（`ProxySession.synthesizingConfig`、`PlayTranslator.buildRegistryData766` を
参照。770の Registry Data も769と同じエントリ配列形式のため、同じビルダーを使う）。

### 1.21.5クライアント（770、online-mode）

**参加できます。** 763／764／765／766／767／768／769と同じく、プロキシがクライアントとの間で
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
  クライアントへ渡します（770形式への strict bool の付け直しは不要。
  770には strictErrorHandling が無い）。

信頼境界と防御、スキンの扱い（他プレイヤーから見たスキンが既定スキンに
なる場合がある制限を含む）は763と同じです（`docs/PROXY-1.20.1.md` の
「信頼境界と防御」「スキンの扱い」を参照）。

#### 設定

`detergentmc.yml` の `server.online-mode: true`（既定）が、プロキシ認証の
オン／オフです。`server.session-server-url` は照会先で、空なら既定の
Mojangセッションサーバーを使います。テストでローカルの偽サーバーを
指定するとき以外は変えないでください。

### ステータス（サーバーリストのping）

770クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 内部→770、name 内部の表示→
"1.21.5"）。descriptionやplayersは書き換えません。

## プレイ中の翻訳

- **ID付け替えのみ：** Keep Alive（プレイは770の 0x26⇔0x1A）、移動
  （Set Player Position は770の 0x1C）、ほとんどのプレイ中パケット。ID表は
  `PacketIds.java`（770列）を参照。769→770でクライアント行きのプレイ117件・
  サーバー行きのプレイ5件のIDが変わるため、汎用の `PacketTranslator.translateId` で
  番号を付け替える。
- **ペイロード変換あり：** Player Chat（770は先頭に globalIndex。769は
  senderUuid から始まる。`PlayTranslator.translatePlayerChat769To770`／
  `770To769`）、Chat Message／Chat Command Signed（770は末尾に checksum u8。
  `stripChatChecksum770`／`appendChatChecksum770`）、
  Disconnect（770は NBT。763／764向けは JSON 文字列に変換する）、
  Respawn（dimension 番号のため変換する。770は 0x4B）、
  Start Configuration（770は 0x6F。763には無いため、内部763のときは
  プロキシがサーバー側で肩代わりする。764／765／766／767／768／769には中継する）、
  Synchronize Player Position（770は 0x42。769と同形式のためそのまま通す）、
  Set Cooldown（770はグループ名の文字列。769と同じ）、
  Player Input（770は 0x29。769の 0x29 と同じ番号・同じ形式）、
  Set Slot State（番号だけ付け替える。ペイロードはそのまま）。
- **Test Instance Block（770の新パケット）：** test_instance_block_status
  （0x77、クライアント行き）は770にしかないため、古いクライアントには送らない。
  set_test_block（0x39）・test_instance_block_action（0x3C、サーバー行き）も
  770にしかないため、古いサーバーには送らない。両方向とも落とす
  （下の「対象外・未実装」を参照）。
- **Pick Item（769の新パケット）：** pick_item_from_block（0x22、位置＋
  includeData）と pick_item_from_entity（0x23、エンティティID＋includeData）は、
  769と同じく770にもあるため、769⇔770ではそのまま通す（番号は同じ）。
  旧バージョンの pick_item（スロット番号）とは別物で、プロキシがワールドを
  知らないためブロック・エンティティからスロットへは変換できない。
  763／764／765／766／767／768向けは落とす（下の「対象外・未実装」を参照）。
  player_loaded（0x2A、空）は769／770にあり、両方向とも通す。
- **スロット（データコンポーネント）：** 769と770でデータコンポーネントの
  種別番号が変わるため、769⇔770のスロットは種別ごとの変換で直す
  （`ChunkTranslator` の770変換）。Set Slot／Window Items／Set Cooldown／
  Entity Equipment／Trade List／Open Window／Block Action／Spawn Entity／
  Sound Effect／Entity Sound Effect／Stop Sound／World Particles／
  Entity Effect／Remove Entity Effect／Damage Event（770にしかないダメージ種別は
  落とす）／Entity Metadata／Update Attributes／Window Click／
  Set Creative Slot を変換する。
  変換のやり方は次の通り。
  - アイテムID・エンチャントID・属性ID・成分種別IDの番号ずれを対応表で直す。
  - `enchantments`／`stored_enchantments` は769の末尾 showTooltip を落とす。
    逆向きは `true` を書き足す。
  - `attribute_modifiers` は769の末尾 showTooltip を落とす。逆向きは `true`
    を書き足す。**769の翻訳子は showTooltip を読まずに落としていた実バグが
    あり（768↔769の `translateAttributeModifiers768To769`／`769To768`）、
    770の作業で直した**（769のテストも showTooltip 付きに直した）。
  - `food`・`consumable`・`use_remainder` は769と770で同じ形式のため、
    中身の番号だけ直す。
  - `custom_model_data` は769と770で同じ4配列の形式のためそのまま通す。
  - `jukebox_playable`・`trim`・`banner_patterns`・`instrument` は769と770で
    番号が同じためそのまま通す（音の番号だけ対応表で直す。`trim` の材料は
    `ingredientId` が消え、模様は `templateItemId` が消えたため、逆向きの
    インラインだけ表から引く）。
  - `tool` は770の末尾 `canDestroyBlocksInCreative` を落とす。逆向きは
    `false` を書き足す。
  - それ以外の成分は中身を読み飛ばしてそのまま写す（不透明コピー）。
  - 770にしかない成分種別（weapon・blocks_attacks・potion_duration_scale・
    provides_trim_material・provides_banner_patterns・break_sound・
    chicken／cow／pig／wolf_sound variant・painting／variant 等）は769向けに
    **落とす**（成分ごと消える）。
  - 763／764／765向けは NBT 形式のため、Window Click／Set Creative Slot は
    **変換できず落とす**（769と同じ制限）。落としたクリックはサーバーに
    届かないため、770クライアントが内部763／764／765のサーバーにいるときは
    インベントリ操作ができない制限がある（ネイティブの770⇔770・769⇔770では
    リレーのため問題ない）。
  - **Window Click は769⇔770でも変換できず落とす**（HashedSlot のため。
    上の「769→770の差分（要点）」を参照）。Set Creative Slot は
    UntrustedSlot のため769⇔770でも変換できる。
- **Update Attributes（769⇔770・768⇔770・767⇔770・766⇔770・765⇔770）**：属性キーが番号のため、
  対応表で変換する。765向けは名前へ直す（`generic.burning_time` は
  770の `burning_time` へ）。770にしかない属性は無い（32件で同じ）。
  765の属性のキーはワイヤ上で `minecraft:generic.max_health` のような
  接頭辞付きである（本物のバニラ1.20.4から受け取って確認済み、
  2026-11-10。`AttributeKeyCaptureTest` が毎回確認する）。接頭辞なしで
  比較すると本物のサーバーからの属性が全部落とされるため、変換では
  接頭辞の有無の両方を受け付ける。
- **チャンク・ブロック状態IDリマップ：** 769↔770はブロック状態が60件増え、
  約27,000件の番号がずれるため、**チャンクの変換が必要**（766↔767は変換不要
  だった）。バイオームは769と770で同じため、バイオームパレットは番号で変換しない
  （identity）。763／764／765／766／767／768向けは
  合成表（763↔770・764↔770・765↔770・766↔770・767↔770・768↔770）を使う
  （`RegistryRemapper` の770用対応表）。
  769→770の差分は小さい：ブロック状態は60件の追加・12件の削除、アイテムは11件の追加、
  エンティティは1件の追加（potion の分割）、音は52件の追加・1件の削除、
  パーティクルは2件の追加、属性・エンチャント・バイオーム・ダメージ種別は変わらない。
  くわしくは上の「769→770の差分（要点）」を参照。
  検証スクリプトは `detergentmc-protocol/tools/verify-770-tables.py`
  （243件のチェック）。
- **Heightmap（Chunk Data）の変換：** 769の NBT（compound）から770の直接の配列へ、
  逆向きは配列から NBT へ直す（`ChunkTranslator.translateChunkData769To770`／
  `770To769`）。type id の順序はバニラの `Heightmap.Types` の宣言順と仮定する
  （0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、3=OCEAN_FLOOR、
  4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES）。**実バニラキャプチャでの
  確認は未実施**（統合テストで確認する）。
- **Set Cursor Item／Set Player Inventory（769／770にしかないパケット）：**
  770の Set Cursor Item（0x59、スロット1個）は767の Set Slot（window −1）に、
  Set Player Inventory（0x65、スロットID＋スロット）は767の Set Slot
  （window 0）に直して送る（769と同じ）。逆向き（767クライアント→770サーバー）は
  Set Slot の window −1／0 をそのまま中継する（770は Set Slot もあるため
  変換は不要）。
- **リソースパック：** 770クライアントには pack_format **55** の
  `detergentmc-pack-1.21.5.zip`（1.21.5専用）を出す。763用
  パック（15）・764用パック（18）・765用パック（22）・766用パック（32）・
  767用パック（34）・768用パック（42）・769用パック（46）とは別物。外部配信時は
  `resource-pack.url-770` / `sha1-770` で770用パックを
  指定する（任意・未指定時は765用パックを出す。763／764／766／767／768／769用と同一方針）。
  loopback時は8つのパックを同じ25567番ポートで配信する。
  設定フェーズの Add Resource Pack（770は 0x09、UUID付き）とプレイ状態の
  Add Resource Pack は、内部763／764向けは UUID を外して送る。
- **データパック：** 1.21.5には pack_format **71** を出す
  （Minecraft Wiki の pack format 表：71 は 1.21.5、61 は 1.21.4、57 は
  1.21.2〜1.21.3、48 は 1.21〜1.21.1）。

## 送らない・落とすパケット（770向け）

770に相当するものが無い、または変換が未検証のパケットは落とします。

- Window Click（全方向。HashedSlot のハッシュ算法が未検証のため。
  769⇔770でも落とす。763／764／765向けは NBT 形式のため落とす。766／767／768⇔770では
  HashedSlot のため落とす）
- Set Creative Slot（763／764／765向け。データコンポーネント
  と NBT 形式のあいだの変換は未実装。766／767／768／769⇔770では変換できる）
- Test Instance Block Status／Set Test Block／Test Instance Block Action
  （770にしかないため、古いクライアント・古いサーバーには送らない）
- Pick Item（763／764／765／766／767／768向け。ブロック・エンティティからスロットへの
  変換は未実装。769⇔770では通す）
- Player Loaded（769／770にしかないため、古いサーバーには送らない）
- Declare Recipes／Statistics／Tags／World Event ID（763／764／765／766／767／768／769向けと
  同じく未対応。レシピ帳・タグが空になるだけで遊べる）
- Craft Recipe Response／Craft Recipe Request／Displayed Recipe
  （769⇔770のあいだ。769のときと同じく、レシピIDの形式が変わり変換できない。
  手動クラフトは動く）
- Recipe Book Add／Remove／Settings、Sync Entity Position、Move Minecart、
  Player Rotation（769にしかないため、古いクライアントには送らない。
  770にも無いため、770クライアントには送らない）
- Select Bundle Item／Tick End（769にしかないサーバー行きパケット。
  古いサーバーには送らない。770クライアントは送らない）
- ブロックエンティティの crafter／trial_spawner（パケットごと落とす。
  766→765方向では vault も落とす。770も同じ。test_block／test_instance_block は
  769向けにエントリごと落とす）
- Entity Metadata の variant 系（763向けはエントリごと落とす。
  764では素通し。766／767／768／769／770では絵画・オオカミの variant を番号で変換するが、
  770にしかないもの（cow／pig／chicken／wolf_sound）は落とす）
- Explosion の770専用パーティクル／サウンド指定（既定演出に置き換える。
  766→763方向と同じ）
- Game Event 13（スポーン合図の765式。770はSpawn Positionを使う）
- Custom Report Details／Server Links（設定フェーズ 0x0F／0x10・プレイ
  0x81／0x82。770にはあるが、766以下のクライアントには送らない。
  767にはプレイの 0x7A／0x7B があるため番号を付け替えて送る）
- jukebox_song の Registry Data（770にしかないため、766以下のクライアントには送らない）
- Configuration Acknowledged／Chunk Batch Received（内部763には無いため落とす）
- tinted_leaves／firefly 粒子（770にしかないため、古いクライアントには送らない。粒子ごと消える）

## 対象外・未実装（正直な一覧）

- **Window Click の変換（770→全方向）**：770の HashedSlot は成分ごとに
  hash i32 を持ち、ハッシュ算法が未検証のため、769の Slot から770の
  HashedSlot は作れない。770クライアントが古いサーバーにいるときも、
  古いクライアントが770サーバーにいるときも、Window Click は落とす。
  落としたクリックはサーバーに届かないため、770が絡む組み合わせでは
  インベントリ操作ができない制限がある（ネイティブの770⇔770では
  リレーのため問題ない）。ハッシュ算法が判明すれば変換できる見込み。
- **Set Creative Slot の変換（770→763／764／765）**：769と同じく
  未実装。770クライアントが古いサーバーにいるときのクリエイティブ
  インベントリ操作ができない（上の「プレイ中の翻訳」を参照）。766／767／768／769⇔770では変換できる。
- **Test Instance Block（両方向）**：770の test_instance_block_status／
  set_test_block／test_instance_block_action はテストブロック専用の
  パケットで、古いバージョンに相当するものが無いため落とす。
  テストブロック・テストインスタンスブロックの操作は770同士でしかできない。
- **Pick Item（763／764／765／766／767／768向け）**：769のときと同じく、
  770の pick_item_from_block／pick_item_from_entity は位置・エンティティを指定し、
  旧バージョンの pick_item はスロット番号を指定するため、プロキシがワールドを
  知らない限り変換できない。1.21.5クライアントが古いサーバーのブロックを中クリックしても、
  古いクライアントが1.21.5サーバーのブロックを中クリックしても、
  アイテムは手に入らない（見た目だけの制限ではなく、操作が無効になる制限）。
- **Player Loaded（770）**：770クライアントが送る player_loaded（0x2A、空）は
  古いサーバーには送らない。770サーバーが player_loaded を前提に
  処理を進めるかどうかは未確認だが、統合テストでは参加・スポーン・
  チャット・移動が player_loaded 無しで動いたため、参加の障害には
  なっていない（下の「テスト」を参照）。
- **チャット checksum（769→770）**：769→770では `0x00` を書き足す。
  **検証済み**：統合テスト `fake769ClientJoinsRealVanilla1215ThroughProxy`
  で、769クライアントのチャットがプロキシ経由で実バニラ1.21.5に届き、
  バニラログに現れた（切断は無い）。0x00 が受け入れられる。なお、checksum の
  本当の算法は未確認のため、署名付きチャットや online-mode での挙動は
  別途確認が必要。
  770→769では末尾の1バイトを落とすだけのため問題ない。
- **Player Chat の globalIndex（769→770）**：プロキシのセッションごとの
  カウンタ（0から）で書き足す。実バニラが globalIndex の連番を厳密に
  検証するかは未確認。`message_acknowledgement` が770で globalIndex
  ベースに変わっているかも未確認（770クライアントのチャット履歴の
  既読確認がずれる可能性がある）。
- **Heightmap の type id 順序（769↔770）**：バニラの `Heightmap.Types` の
  宣言順と仮定する（0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、
  3=OCEAN_FLOOR、4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES）。
  配列の形式自体は実バニラのチャンクで確認済み（BedrockMatrixIntegrationTest で
  3件・各37 long を読めた）が、type id と種別名の対応の仮定は未確認のまま。
  順序が違うとチャンクの高さ情報がずれ、地形の表示が壊れる。
- **Spawn Entity Experience Orb の data（769↔770）**：770の Spawn Entity の
  `data` varint が経験値であることは仮定。実バニラキャプチャでの確認は
  未実施。違えば経験値オーブの数がずれる。
- **creaking_heart の uprooted（769↔770）**：770の `creaking_heart_state` は
  uprooted／dormant／awake の3値で、769の `active`（bool）は uprooted を
  表せないため、770→769では uprooted を `active=false`（dormant 相当）に
  落とす。uprooted の心臓が769では dormant に見える（見た目だけの制限）。
  実バニラでの creaking_heart の挙動は未確認。
- **Player Info の showHat／listPriority の順番（770→769）**：769と同じ仮定。
  per-player のフィールド順は minecraft-data の protocol.json（listPriority の
  varint が先、showHat の bool が後）に従った。wiki.vg の旧ページでは確認できず、
  実バニラ1.21.5の Player Info パケットで両ビットが同時に立つ例は
  未確認。順番が逆だと Player Info の読み取りがずれ、プレイヤーリストが
  壊れるため、実機での確認が必要（統合テストでは hat＋list_order 付きの
  Player Info を770→769へ変換する単体テストで検証済みだが、実バニラの
  パケット順は未確認）。
- **BannerPatternLayer のインライン模様（770→769）**：769のときと同じく、
  インラインの BannerPattern NBT はそのまま通すベストエフォート。
- **ポーションのID（769↔770）**：ポーションの対応表はコードベースのどこにも無く、
  768↔769でも同じ扱い（不透明コピー）のため、769↔770でも同じ扱い。
  1.21.5で新しいポーションは無いため、番号がずれていないと仮定している
  が、ワイヤでの確認はできていない。
- **ボートの木の種類**：769と同じ制限。767のボートは770では常にオークのボートに、
  770のボートは767では常に（木の種類を問わず）ボートになる。ボートの
  variant（木の種類）をエンティティ種別に直す変換は未実装。
  767のボート variant の並び（OAK〜BAMBOO、PALE_OAK=9）はバニラの
  `Boat$Type` の宣言順からの推測で、実機未確認。
- **food の効果（767→768→769→770）**：769のときと同じ制限。金リンゴ等の食べたときの
  効果は769／770向けに落とす（767の food の効果は amplifier／duration を
  持たないため、768／769／770の `consumable` の apply_effects 形式へは変換できない）。
- **custom_model_data（767→768→769→770）**：769のときと同じ制限。767の単一の数値を
  769／770の colors[0] に入れるだけのベストエフォート。floats／flags／strings は
  空になる。
- **Set Cooldown（770のグループ名）**：769と同じく、エンダーパール・コーラスフルーツ・
  ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす
  （クールダウンの表示だけの制限）。
- **Trade List の ItemCost（767↔768）**：767↔768の翻訳子は「count varint＋その個数分の
  Slot」と読む誤りのままで、770の範囲外のため直していない。768↔769と769↔770は
  770の作業で正しい形に直した。
- **Declare Recipes／Tags／Statistics／World Event ID**：未対応。
- **Entity Metadata のスロット・ブロック状態・粒子の中身**：764では素通しの
  ため未変換のまま残る（763向けは variant 系を落とす）。
- **770＋online-modeの他プレイヤーから見たスキン**：763と同じ制限。
  UUID・名前は認証済みのものが使われる。
- **実機クライアントでの確認**：未実施。生ソケットで770クライアントを装った
  統合テスト（下の「テスト」を参照）と、プロキシの単体テスト
  （`ProxySession770Test`）で検証しています。
- **内部770サーバーの統合テスト**：実施済み（下の「テスト」を参照）。
  本物のバニラ1.21.5サーバー自体の実起動（`Done` まで）も確認済み。
- **1.21.6以降（771〜）は未対応**：プロキシは770より新しいプロトコルを
  ログイン時に断る（「新しすぎます」）。

## Java 21（内部770サーバーを動かすとき）

バニラ1.21.5サーバーは Java 21 を要求します（Minecraft Wiki の
1.21.5 記事：Minimum Java version Java SE 21）。DetergentMC本体は
Java 17 で動きます。`VanillaServerHost` は内部バージョンが1.21.5の
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

`docs/PLAYTEST-1.21.5.md` を参照してください。

## テスト

- `detergentmc-protocol`：`PacketIds770Test`（770のID表：122件の変更・
  新規3件・削除1件）、`ClientAdapter770Test`（770の受け入れ判定・
  全内部プロトコルとの振り分け）、`ClientJoinFlow770Test`（770の参加フロー）、
  `RegistryRemapper770Test`（769↔770対応表・合成表・往復）、
  `ChunkTranslator770Test`（769↔770変換・heightmap の NBT↔配列・スロットの成分変換
  （tooltip_display・enchantments の showTooltip）・Set Creative Slot の
  UntrustedSlot・Window Click の投げ・tinted_leaves／firefly のドロップ・
  Entity Metadata の variant・Spawn Entity Experience Orb）、
  `PlayTranslator770Test`（Player Chat の globalIndex・チャット checksum の
  付け外し・Registry Data の identity・jukebox_song の維持）、
  `ProtocolVersion770Test`（1.21.5→770）、
  `ResourcePackBuilder770Test`（pack_format 55）、
  `ResourcePackMode770Test`（url-770／sha1-770）、
  `ResourcePackServer770Test`（8パックの配信）、
  `ProxySession770Test`（770のプロキシ参加フロー10件：770クライアント×内部770の
  素通し、770クライアント×内部769のスロット変換、769クライアント×内部770の
  スロット逆変換、770クライアント×内部763の設定フェーズ合成、
  763クライアント×内部770の設定フェーズ消費、770クライアントの Client Information
  （particleStatus 維持／767向けに落とす）、Registry Data の770→769並べ替え
  （バイオーム65件維持）・770→768並べ替え（pale_garden 落とし）、
  770クライアント×内部765の join レベル変換。内部764／766／767／768との
  参加フロー単体テストは無いが、`ClientAdapter` の全組み合わせ判定と翻訳子の
  単体テストでカバーしている）。
  検証スクリプト `detergentmc-protocol/tools/verify-770-tables.py`
  （243件のチェック）。
- `detergentmc-core`：`ServerProtocolWiringTest`（内部770の配線・770用パックの
  受け渡し、768の配線テストも追加）、`VanillaServerHostTest`（Java 21 の選択）、
  `ProxyIntegrationTest.fake770ClientJoinsRealVanilla1215ThroughProxy`
  （偽770クライアントが本物のバニラ1.21.5に参加）、
  `ProxyIntegrationTest.fake770ClientJoinsRealVanilla1214ThroughProxy`
  （偽770クライアントが本物のバニラ1.21.4に参加）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla1215ThroughProxy`
  （偽769クライアントが本物のバニラ1.21.5に参加）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla1212ThroughProxy`
  （偽769クライアントが本物のバニラ1.21.2に参加、回帰）。
  770クライアント×内部768／767／766／764／763サーバーの統合テストは無い
  （単体テストのみ。`ProxySession770Test` の参加フローと `ClientAdapter` の
  全組み合わせ判定でカバーしている）。
  `RegistryOrderCaptureTest.registryOrderOfRealVanilla1215`（本物のバニラ1.21.5から
  Registry Data の順番を記録し、対応表の仮説を検証する。
  バイオームは65件・pale_garden=39・plains=40。
  mace_smash=26／magic=27 を含む。楽器は8件・admire=0・yearn=7）。
  既存の統合テスト（1.21.4対応の14件）も回帰テストとして残っています。
- `detergentmc-bedrock`：`JavaBackendConnection770Test`（Bedrock→内部770の
  参加・Select Known Packs／Ping・Respawn の次元番号・Update Attributes の
  770形式・スロットの770正規化・チャンク heightmap の配列・メタデータの770種別・
  770専用パーティクルの読み飛ばし・`sendChat` の checksum 0x00 付け足し）。
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_5`（Bedrock 6 プロトコル ×
  内部770の統合テスト。ここで見つかった実バグは2件：(1) Bedrock からの
  チャット送信が770の checksum を付けず、プロキシが acknowledged の末尾を
  削って壊していた（`JavaBackendConnection.sendChat` で770のとき 0x00 を
  付け足すよう修正）。(2) チャンクセクションの Data Array の長さ varint
  接頭辞が770で無くなっていた（上の表の「チャンクセクションの Data Array」を
  参照。`BedrockChunkTranslator.readPalettedContainer` を修正）。
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_4`（Bedrock 6 プロトコル ×
  内部769の統合テスト。プロキシの769→770チャンク変換と Bedrock の770読みの
  両方を検証する回帰テスト）。
