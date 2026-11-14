# 1.21.4クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.21.4クライアント（プロトコル769）は、内部プロトコルが
763／764／765／766／767／768／769のどのサーバーにも参加できます。内部769のときは
素通し（リレー）、それ以外は上向き・下向き変換です。768との違いは小さく、
**クライアント行きのパケットIDは1件も変わらず、サーバー行きのプレイ26件の
うち24件が変わり、新規3件・削除1件**です。レジストリも768→769では
ブロック状態が557件増える程度で、768のときのような大きな組み替えはありません。
いちばんの変化はバイオームで、768では winter_drop データパックの中にあって
既定では現れなかった `pale_garden` が、1.21.4では通常のバイオームとして
65件目に現れ、plains が 39→40 にずれます（**実バニラ1.21.4から
Registry Data を記録する統合テストで確認済み**）。くわしい差分は下の各節を
参照してください。768の仕組みは `docs/PROXY-1.21.2.md`、767は
`docs/PROXY-1.21.md`、766は `docs/PROXY-1.20.5.md`、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。

全49通り（クライアント763／764／765／766／767／768／769 × サーバー763／764／765／
766／767／768／769）の対応マトリクスは `docs/VERSIONS.md` の「1.21.4対応：
全49通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用・767用・768用・769用の7つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 768→769の差分（要点）

| 分野 | 768→769の差分 | 扱い |
| --- | --- | --- |
| パケットID | クライアント行きは0件の変更。サーバー行きのプレイは24件が変わる（ping_request 0x23→0x24、craft_recipe_request 0x24→0x25、abilities 0x25→0x26、block_dig 0x26→0x27、entity_action 0x27→0x28、player_input 0x28→0x29、pong 0x29→0x2B、recipe_book 0x2A→0x2C、displayed_recipe 0x2B→0x2D、name_item 0x2C→0x2E、resource_pack_receive 0x2D→0x2F、advancement_tab 0x2E→0x30、select_trade 0x2F→0x31、set_beacon_effect 0x30→0x32、held_item_slot 0x31→0x33、update_command_block 0x32→0x34、update_command_block_minecart 0x33→0x35、set_creative_slot 0x34→0x36、update_jigsaw_block 0x35→0x37、update_structure_block 0x36→0x38、update_sign 0x37→0x39、arm_animation 0x38→0x3A、spectate 0x39→0x3B、block_place 0x3A→0x3C、use_item 0x3B→0x3D）。新規は pick_item_from_block（0x22）・pick_item_from_entity（0x23）・player_loaded（0x2A）の3件。削除は pick_item（768の 0x22）。ping_request（0x24）と legacy_server_list_ping（0xFE）は表に入れず、プロキシは落とす（既存の方針） | 汎用の `PacketTranslator.translateId` で番号を付け替える。新規・削除パケットは古いクライアントには送らない（落とす）。769同士は素通し |
| Login Success | 769も768と同じく末尾の `strictErrorHandling` bool が無い（765と同じ形式） | そのまま通す。767向けは `false` を付け足す（768と同じ） |
| Join Game／Respawn | 768と769で形式は同じ（SpawnInfo の末尾の `seaLevel` varint を含む） | そのまま通す。767向けは seaLevel（63）を落とす（768と同じ） |
| Set Held Item（クライアント行き） | スロットが i8→varint に変わった（ID は 0x63 のまま） | 768→769ではバイトを varint に直し、逆向きは varint をバイトに戻す。サーバー行きの Held Item Slot は両方とも i16 のため変換なし |
| Player Info | `update_hat` アクション（bit 6、プレイヤーごとの `showHat` bool）が追加され、`update_list_order` が bit 6→bit 7 に移動した（`listPriority` varint はそのまま） | 768→769では bit 6 を bit 7 に移す。769→768では bit 7 を bit 6 に移し、showHat bool を落とす。per-player のフィールド順は minecraft-data の protocol.json（listPriority の varint が先、showHat の bool が後）に従う。**この順番は wiki.vg の旧ページでは確認できず、minecraft-data の表に従った仮定である**（下の「対象外・未実装」を参照） |
| World Particles | `alwaysShow` bool が `longDistance` の後に挿入された | 768→769では `false` を挿入し、769→768では落とす。768に無い `pale_oak_leaves` 粒子（769の 34）は769→768で落とす |
| Vehicle Move（サーバー行き） | 末尾に `onGround` bool が追加された（ID は 0x20 のまま） | 768→769では `false` を付け足す（近似。768クライアントは送らないため）。769→768では落とす |
| Explosion | `playerKnockback` が vec3f（f32×3）→vec3f64（f64×3）になった | 768→769では f32 を f64 に、逆向きは f64 を f32 に直す。粒子／サウンド指定は768と同じ形のため、そのまま変換する |
| trail 粒子 | 末尾に `duration` varint が追加された | 768→769では 20ティック（1秒）を入れる（近似）。769→768では落とす |
| BannerPatternLayer.pattern | minecraft-data の型名が `ItemSoundHolder` から registryEntryHolder に変わった（ワイヤ形式は同じ varint ホルダー。0＝インラインの BannerPattern NBT、それ以外は ID＋1） | 768のときと同じ形で変換する。インラインの模様データはそのまま通す（ベストエフォート） |
| Client Information（Settings） | 768と同じ9フィールド（末尾に `particleStatus` varint） | 767→769では `0` を付け足す。769→767では落とす。**付けないと769のバニラが読み取り途中で切断し、落とさないと767のバニラが「1 bytes extra」で切断する**（768と同じ。実バニラの挙動で確認） |
| ブロック状態 | 27,318→27,866件（+557、−9）。樹脂（resin）系・開いた／閉じたアイブロッサムの追加で約27,000件の番号がずれる。`creaking_heart` のプロパティが `[axis,creaking=disabled/dormant/active]`（9状態）から `[active=bool,axis,natural=bool]`（12状態）に変わった | 対応表で変換。769にしかないブロック状態は古いサーバー向けに石へ。creaking_heart は768の `creaking` を769の `active` と `natural` に分けるベストエフォート（下の「対象外・未実装」を参照） |
| アイテム | 1,375→1,385件（+10、削除0）。樹脂の塊・樹脂レンガ・開いたアイブロッサム等の追加 | 対応表で変換。769にしかないアイテムは古いサーバー向けに石へ |
| エンティティ | 150→149件（+0、−1）。`creaking_transient` が削除された（creaking は 29 のまま） | 対応表で変換。768の creaking_transient は769向けに creaking へ、769→768の preimage が無いため769の存在しないエンティティはブタへ。エンティティIDは creaking_transient（768の 30）より後ろが1つずれる（落下ブロック 50→49、額縁 71→70、オークのボート 85→84、絵画 89→88、ペールオークのボート 90→89、ブタ 95→94） |
| 音 | 1,636→1,651件（+15、削除0）。追加は entity.creaking.twitch、block.eyeblossom の5件、block.resin の4件、block.resin_bricks の5件 | 対応表で変換。769にしかない音は古いサーバー向けに石を壊す音へ |
| パーティクル | 111→112件。`pale_oak_leaves` が 34 に挿入され、34番以降が1つずれる（block_crumble は 110→111） | 対応表で変換。769にしかない pale_oak_leaves は古いサーバー向けに落とす（粒子ごと消える） |
| 属性 | 32件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エンチャント | 42件で同じ顔ぶれ・同じアルファベット順 | 変換なし（identity） |
| データコンポーネント | 67種で同じ顔ぶれ・同じ順番 | 種別ごとの変換は768と同じ。768→769では中身の番号（アイテム・エンチャント・属性・音・旗の模様等）だけ対応表で直す |
| バイオーム | 64→65件。`pale_garden` が 39 に入り、plains が 40 にずれる。768では winter_drop データパックが無効のため 64 件・plains は 39 だったが、**1.21.4では通常のバイオームとして現れる**（実バニラから Registry Data を記録する統合テスト `RegistryOrderCaptureTest.registryOrderOfRealVanilla1214` で確認済み） | Registry Data のエントリ配列を名前で並べ替える。768→769では pale_garden を名前だけのエントリ（データなし）として 39 に挿入し、769→768では落として 40 番以降を1つ戻す。バイオームパレットは番号で変換する |
| ブロックエンティティ種別 | 45件で同じ顔ぶれ・同じ順番（creaking_heart は 10、trial_spawner は 43、vault は 44） | 変換なし（identity） |
| 絵画・旗の模様・トリム・ジュークボックスの歌 | 変わらず（50／43／18／19件） | 変換なし |
| ダメージ種別 | 49件で同じ顔ぶれ・同じ順番（ender_pearl 8、mace_smash 26、magic 27） | 変換なし（identity） |
| メニュー | 25件で変わらず | 変換なし |

検証スクリプトは `detergentmc-protocol/tools/verify-769-tables.py`
（207件のチェック）と `verify-reverse-tables.py`。本物のバニラ1.21.4から
Registry Data の順番をそのまま記録する統合テスト
（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1214`）で、
エンチャント・絵画・旗の模様・トリム・ダメージ種別・ジュークボックスの歌・
楽器・バイオームの順番が仮説通りであることを確かめている
（バイオームは65件・pale_garden=39・plains=40。
mace_smash=26／magic=27 を含む。楽器は8件・admire=0・yearn=7）。
実バニラが設定フェーズで送るのは768と同じ12レジストリだけ
（banner_pattern／chat_type／damage_type／dimension_type／enchantment／
instrument／jukebox_song／painting_variant／trim_material／trim_pattern／
wolf_variant／worldgen/biome）。
`minecraft:attribute` と `minecraft:block_entity_type` は送られないため、
属性の順番とブロックエンティティ種別の順番は ViaVersion の
mapping-1.21.4.json と jar の data ファイルに依り、ワイヤでの確認は
できていない（768と同じ制限）。

**768→769の連鎖の方針：** 769クライアント→768サーバーの変換は、
769→768へ直してから既存の `handleClientPlay768` に渡す二段方式。
ただし `handleClientPlay768` はクライアントが768であることを前提に
書かれているため、内部768サーバーのときだけは769→768へ直した
パケットを直接バニラへ送り、それ以外（内部763／764／765／766／767）の
ときだけ `handleClientPlay768` に渡す。同じ罠はサーバー→クライアント側にも
あり、768サーバーから769クライアントへの Join Game／Respawn／Disconnect は
769向けの分岐を `handleServerPlayFrom768`／`handleServerJoinGame768` に
追加してある（追加しないと765形式の Join Game 0x29 が769クライアントに
届く）。766／767サーバーから769クライアントへは 766→767→768→769 の
三段・二段で変換する。763サーバーから769クライアントへは、合成される
Join Game を768形式で作る（`handleSynthesizedJoinGame` の分岐。
**769を漏らすと765形式の Join Game（0x29）が769クライアントに届く実バグが
あったため、769を明示的に追加した**）。

**設定フェーズの振り分けの注意：** 768クライアント→769サーバーの設定フェーズは、
専用の `ProxySession.handleServerConfiguration768From769`
（Registry Data の769→768並べ替え・Tags 落とし・Cookie／Select Known Packs の
空応答）を通す。振り分けで `is768()` の分岐（`isServer768()` の素通し）だけを
見ると、769サーバーからの Registry Data が764／765向けの消費者に落ちて
769の Registry Data が768クライアントに届かなくなる。
`handleServerConfiguration` では `is768()` の分岐で `isServer769()` を
`isServer768()` と同じく先に見る（767→768の設定フェーズの振り分けの
罠と同じ）。

**Client Information の罠（768クライアント→769サーバー）：**
`handleClientConfiguration768` は「Settings の末尾の particleStatus を
旧サーバー向けに落とす」と書かれているが、769も768と同じ9フィールドの
ため、769サーバー向けは落としてはいけない。落とすと769のバニラが
Client Information の読み取り途中で切断し、Join Game が届かなくなる
（統合テスト `fake768ClientJoinsRealVanilla1214ThroughProxy` で発覚した
実バグ。`isServer768()` だけを見て `isServer769()` を漏らしていた）。
同じ罠はプレイ状態の `handleClientPlay768` にもあり、下の分岐はサーバーが
768以下の前提で書かれている（Chat 0x05、Settings の particleStatus 落とし、
Keep Alive 0x18 等）。768クライアント→769サーバーのプレイ状態は
`handleClientPlayTo769`→`handleClientPlay768To769`（768→769の番号付け替え＋
Vehicle Move／Window Click／Set Creative Slot の変換）を通す。
`handleClientPlay768` の先頭にも `isServer769()` の早期分岐を置き、
振り分けの順番が変わっても769サーバーへ誤変換したパケットを送らないように
してある。

### なぜ「並べ替え」が必要か

768／769 の設定フェーズの Registry Data は、レジストリごとの
エントリ配列形式です（1個の NBT codec ではありません）。各エントリは
「名前＋NBT」で、番号は配列の位置そのものです。バイオームの並びが
768（64件、plains=39）と769（65件、pale_garden=39・plains=40）で違うため、
配列をそのまま中継すると番号がずれ、plains が別のバイオームに
なってしまいます。プロキシは Registry Data を受け取ったら、エントリを
名前で並べ替え、送る側の正しい順番に直してから送ります。送る側に無いエントリ
（769の pale_garden）は、768向けには**落とす**のではなく、名前だけの
エントリ（データなし）として送り、番号をずらさないようにします
（768→769の逆向きは、768に無い pale_garden を 39 に挿入する）。
`PlayTranslator.translateRegistryData768To769`／`769To768`／
`765To768`／`768To765` を参照してください。

## 接続の流れ

### 1.21.4クライアント（769、offline-mode）

内部プロトコルで流れが分かれます。

**内部769（1.21.4サーバー）のとき：素通し（リレー）。**
ハンドシェイクの直後にリレーへ切り替わり、設定フェーズ・プレイは
生バイトのまま通します。設定フェーズの Select Known Packs（0x0E）も
そのままクライアントへ届くため、クライアントは空の一覧で答える必要が
あります（本物の1.21.4クライアントは答えます。偽クライアントのテスト
`Fake769Client` も答えます）。

**内部768（1.21.2／1.21.3サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**

1. ハンドシェイクのプロトコルVarIntを769→768に書き換えてバニラへ送る。
2. Login Startは768と同形式（名前＋UUID、bool なし）のため変換しない。
   Login Successは768も769も末尾の `strictErrorHandling` bool が無いため
   そのまま通す。
3. ログイン成功後、クライアントが Login Acknowledged（769は 0x03）を送ると
   両方とも設定フェーズに入る。Client Information（769は 0x00）は末尾の
   particleStatus を落として768形式にしてからバニラへ送る（768も9フィールドの
   ため、実際はそのまま通す）。バニラが送る Registry Data（768は 0x07、
   エントリ配列）は769の順序に並べ替え（pale_garden を挿入）して中継する。
   Custom Report Details（0x0F）・Server Links（0x10）は768と769で番号が
   同じため中継する。Tags はレジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x03→769も 0x03）を中継し、クライアントの
   応答（0x03）がバニラに届いた時点で両方ともプレイ状態に入る。
5. バニラが送るJoin Game（768は 0x2C）は769の 0x2C へそのまま通す
   （形式は同じ）。スロットを含むパケット（Set Slot／Window Items／
   Entity Equipment など）は768→769へ変換する
   （`ChunkTranslator` の769変換を参照）。
6. スポーンは769式。Spawn Position（769は 0x5B）が合図で、768の
   Game Event 13は769クライアントに送らない。

**内部767（1.21／1.21.1サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
768のときと同じ流れで、Registry Data は767→768→769の二段で並べ替える。
jukebox_song は767にあるため、769クライアントは受け取る
（766サーバーは送らない）。

**内部766（1.20.5／1.20.6サーバー）のとき：設定フェーズを中継し、
Registry Data の順序とスロットを変換する。**
767のときと同じ流れで、Registry Data は766→767→768→769の三段で並べ替える。
jukebox_song は766に無いため、769クライアントは受け取らない
（766サーバーは送らない）。

**内部765（1.20.4サーバー）・内部764（1.20.2サーバー）のとき：
設定フェーズを中継し、Registry Data の形式と順序を変換する。**

1. ハンドシェイクのプロトコルVarIntを769→765／764に書き換えてバニラへ送る。
2. Login Startは765・764と同形式のため変換しない。Login Successは
   769に strictErrorHandling が無いため、そのまま765／764形式で通す。
3. ログイン成功後、クライアントが Login Acknowledged（769は 0x03）を送ると
   両方とも設定フェーズに入る。Client Information は particleStatus を
   落としてからバニラへ送る。バニラが送る Registry Data（764／765は
   0x05、1個の名前なし NBT codec）は、769のエントリ配列形式（0x07）に
   変換し、さらに769の順序に並べ替えて中継する。逆向き（内部769のときの
   764／765クライアント）も同じくエントリ配列→codec に変換する。Tags は
   レジストリ番号がずれるため落とす。
4. Finish Configuration（バニラ側 0x02→769は 0x03）を中継し、クライアントの
   応答（769は 0x03→バニラ側 0x02）がバニラに届いた時点で両方とも
   プレイ状態に入る。
5. バニラが送るJoin Game（764／765は 0x29）は769の 0x2C へ変換する。
   dimension は番号から文字列に戻す（764／765向けはその逆）。
6. スポーンは769式。Spawn Position（769は 0x5B）が合図で、765の
   Game Event 13は769クライアントに送らない。

**内部763（1.20／1.20.1サーバー）のとき：設定フェーズを合成する。**
内部763には設定フェーズが無いため、プロキシが Join Game の codec から
Registry Data（769形式 0x07、レジストリごとのエントリ配列）と
Finish Configuration（769は 0x03）を合成してクライアントへ送る。
Registry Data は766形式で作ってから 766→767→768→769 の三段で並べ替え、
pale_garden を 39 に挿入する。クライアントの Finish 応答（769は 0x03）が
来たら、預かっていた Join Game（769は 0x2C）を送ってプレイ状態に入る。
合成の間、サーバーから届いたプレイ状態のパケットは溜めておき、Finish 応答の
後にまとめて送る。この仕組みは764／765／766／767／768クライアントの
内部763向け合成と同じ経路で、769だけ Registry Data の形式が違う
（`ProxySession.synthesizingConfig`、`PlayTranslator.buildRegistryData766` を
参照。769の Registry Data も768と同じエントリ配列形式のため、同じビルダーを使う）。

### 1.21.4クライアント（769、online-mode）

**参加できます。** 763／764／765／766／767／768と同じく、プロキシがクライアントとの間で
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
  クライアントへ渡します（769形式への strict bool の付け直しは不要。
  769には strictErrorHandling が無い）。

信頼境界と防御、スキンの扱い（他プレイヤーから見たスキンが既定スキンに
なる場合がある制限を含む）は763と同じです（`docs/PROXY-1.20.1.md` の
「信頼境界と防御」「スキンの扱い」を参照）。

#### 設定

`detergentmc.yml` の `server.online-mode: true`（既定）が、プロキシ認証の
オン／オフです。`server.session-server-url` は照会先で、空なら既定の
Mojangセッションサーバーを使います。テストでローカルの偽サーバーを
指定するとき以外は変えないでください。

### ステータス（サーバーリストのping）

769クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 内部→769、name 内部の表示→
"1.21.4"）。descriptionやplayersは書き換えません。

## プレイ中の翻訳

- **ID付け替えのみ：** Keep Alive（プレイは769の 0x27⇔0x1A）、移動
  （Set Player Position は769の 0x1C）、ほとんどのプレイ中パケット。ID表は
  `PacketIds.java`（769列）を参照。768→769でサーバー行きのプレイ24件の
  IDが変わるため、汎用の `PacketTranslator.translateId` で番号を付け替える。
  クライアント行きのIDは1件も変わらない。
- **ペイロード変換あり：** チャット（769の Chat Message 0x07⇔内部 0x06。
  同形式のため番号だけ付け替える）、Chat Command（769は符号なし 0x05
  と符号付き 0x06 に分かれている。符号なしは文字列だけのため、完全な形式に
  合成して内部の 0x04 へ送る。符号付きはそのまま 0x04 へ送る）、
  Disconnect（769は NBT。763／764向けは JSON 文字列に変換する）、
  Respawn（dimension 番号のため変換する。769は 0x4C）、
  Start Configuration（769は 0x70。763には無いため、内部763のときは
  プロキシがサーバー側で肩代わりする。764／765／766／767／768には中継する）、
  Synchronize Player Position（769は 0x42。768と同形式のためそのまま通す）、
  Set Cooldown（769はグループ名の文字列。768と同じ）、
  Player Input（769は 0x29。768の 0x28 とは番号が違うが、方向は
  1バイトのビットフラグで同じ形式）、
  Set Slot State（番号だけ付け替える。ペイロードはそのまま）。
- **Pick Item（769の新パケット）：** pick_item_from_block（0x22、位置＋
  includeData）と pick_item_from_entity（0x23、エンティティID＋includeData）は、
  旧バージョンの pick_item（スロット番号）とは別物で、プロキシがワールドを
  知らないためブロック・エンティティからスロットへは変換できない。
  両方向とも落とす（下の「対象外・未実装」を参照）。
  player_loaded（0x2A、空）は769にしかないため、古いサーバーには送らない。
- **スロット（データコンポーネント）：** 768と769でデータコンポーネントの
  種別番号は同じため、768⇔769のスロットは種別ごとの変換をそのまま使い、
  中身の番号（アイテム・エンチャント・属性・音・旗の模様等）だけ対応表で直す
  （`ChunkTranslator` の769変換）。Set Slot／Window Items／Set Cooldown／
  Entity Equipment／Trade List／Open Window／Block Action／Spawn Entity／
  Sound Effect／Entity Sound Effect／Stop Sound／World Particles／
  Entity Effect／Remove Entity Effect／Damage Event（769にしかないダメージ種別は
  落とす）／Entity Metadata／Update Attributes／Window Click／
  Set Creative Slot を変換する。
  変換のやり方は次の通り。
  - アイテムID・エンチャントID・属性ID・成分種別IDの番号ずれを対応表で直す。
  - `attribute_modifiers` は768と769で同じ形式（UUID は文字列）。
    属性IDだけ対応表で直す（768→769では変わらないためそのまま）。
  - `food`・`consumable`・`use_remainder` は768と769で同じ形式のため、
    中身の番号だけ直す（768→767のときのような効果の落としは無い）。
  - `custom_model_data` は768と769で同じ4配列の形式のためそのまま通す。
  - `jukebox_playable`・`trim`・`banner_patterns`・`instrument` は768と769で
    番号が同じためそのまま通す（音の番号だけ対応表で直す）。
  - それ以外の成分は中身を読み飛ばしてそのまま写す（不透明コピー）。
  - 763／764／765向けは NBT 形式のため、Window Click／Set Creative Slot は
    **変換できず落とす**（768と同じ制限）。落としたクリックはサーバーに
    届かないため、769クライアントが内部763／764／765のサーバーにいるときは
    インベントリ操作ができない制限がある（ネイティブの769⇔769・768⇔769では
    リレーのため問題ない）。
- **Update Attributes（768⇔769・767⇔769・766⇔769・765⇔769）**：属性キーが番号のため、
  対応表で変換する。765向けは名前へ直す（`generic.burning_time` は
  769の `burning_time` へ）。769にしかない属性は無い（32件で同じ）。
  765の属性のキーはワイヤ上で `minecraft:generic.max_health` のような
  接頭辞付きである（本物のバニラ1.20.4から受け取って確認済み、
  2026-11-10。`AttributeKeyCaptureTest` が毎回確認する）。接頭辞なしで
  比較すると本物のサーバーからの属性が全部落とされるため、変換では
  接頭辞の有無の両方を受け付ける。
- **チャンク・ブロック状態IDリマップ：** 768↔769はブロック状態が557件増え、
  約27,000件の番号がずれるため、**チャンクの変換が必要**（766↔767は変換不要
  だった）。バイオームは768と769で1件（pale_garden）だけ違うため、
  バイオームパレットは番号で変換する（pale_garden は768向けに plains へ、
  768の plains（39）は769向けに 40 へ）。763／764／765／766／767向けは
  合成表（763↔769・764↔769・765↔769・766↔769・767↔769）を使う
  （`RegistryRemapper` の769用対応表）。
  769→768の差分は小さい：ブロック状態は557件の追加・9件の削除、アイテムは10件の追加、
  エンティティは1件の削除、音は15件の追加、パーティクルは1件の追加、属性・エンチャント・
  データコンポーネント・ブロックエンティティ種別・ダメージ種別は変わらない。
  くわしくは上の「768→769の差分（要点）」を参照。
  検証スクリプトは `detergentmc-protocol/tools/verify-769-tables.py`
  （207件のチェック）と `verify-reverse-tables.py`。
- **Set Cursor Item／Set Player Inventory（768／769にしかないパケット）：**
  769の Set Cursor Item（0x5A、スロット1個）は767の Set Slot（window −1）に、
  Set Player Inventory（0x66、スロットID＋スロット）は767の Set Slot
  （window 0）に直して送る（768と同じ）。逆向き（767クライアント→769サーバー）は
  Set Slot の window −1／0 をそのまま中継する（769は Set Slot もあるため
  変換は不要）。
- **リソースパック：** 769クライアントには pack_format **46** の
  `detergentmc-pack-1.21.4.zip`（1.21.4専用）を出す。763用
  パック（15）・764用パック（18）・765用パック（22）・766用パック（32）・
  767用パック（34）・768用パック（42）とは別物。外部配信時は
  `resource-pack.url-769` / `sha1-769` で769用パックを
  指定する（任意・未指定時は765用パックを出す。763／764／766／767／768用と同一方針）。
  loopback時は7つのパックを同じ25567番ポートで配信する。
  設定フェーズの Add Resource Pack（769は 0x09、UUID付き）とプレイ状態の
  Add Resource Pack は、内部763／764向けは UUID を外して送る。
- **データパック：** 1.21.4には pack_format **61** を出す
  （Minecraft Wiki の pack format 表：61 は 1.21.4、57 は
  1.21.2〜1.21.3、48 は 1.21〜1.21.1）。

## 送らない・落とすパケット（769向け）

769に相当するものが無い、または変換が未検証のパケットは落とします。

- Window Click／Set Creative Slot（763／764／765向け。データコンポーネント
  と NBT 形式のあいだの変換は未実装。766／767／768⇔769では変換できる）
- Pick Item（両方向。ブロック・エンティティからスロットへの変換は未実装）
- Player Loaded（769にしかないため、古いサーバーには送らない）
- Declare Recipes／Statistics／Tags／World Event ID（763／764／765／766／767／768向けと
  同じく未対応。レシピ帳・タグが空になるだけで遊べる）
- Craft Recipe Response／Craft Recipe Request／Displayed Recipe
  （768⇔769のあいだ。768のときと同じく、レシピIDの形式が変わり変換できない。
  手動クラフトは動く）
- Recipe Book Add／Remove／Settings、Sync Entity Position、Move Minecart、
  Player Rotation（768にしかないため、古いクライアントには送らない。
  769にも無いため、769クライアントには送らない）
- Select Bundle Item／Tick End（768にしかないサーバー行きパケット。
  古いサーバーには送らない。769クライアントは送らない）
- ブロックエンティティの crafter／trial_spawner（パケットごと落とす。
  766→765方向では vault も落とす。769も同じ。creaking_heart は767向けに落とす）
- Entity Metadata の variant 系（763向けはエントリごと落とす。
  764では素通し。766／767／768／769では絵画・オオカミの variant を番号で変換するが、
  769にしかないものは落とす）
- Explosion の769専用パーティクル／サウンド指定（既定演出に置き換える。
  766→763方向と同じ）
- Game Event 13（スポーン合図の765式。769はSpawn Positionを使う）
- Custom Report Details／Server Links（設定フェーズ 0x0F／0x10・プレイ
  0x81／0x82。769にはあるが、766以下のクライアントには送らない。
  767にはプレイの 0x7A／0x7B があるため番号を付け替えて送る）
- jukebox_song の Registry Data（769にしかないため、766以下のクライアントには送らない）
- Configuration Acknowledged／Chunk Batch Received（内部763には無いため落とす）
- pale_oak_leaves 粒子（769にしかないため、古いクライアントには送らない。粒子ごと消える）

## 対象外・未実装（正直な一覧）

- **Window Click／Set Creative Slot の変換（769→763／764／765）**：768と同じく
  未実装。769クライアントが古いサーバーにいるときのインベントリ操作が
  できない（上の「プレイ中の翻訳」を参照）。766／767／768⇔769では変換できる。
- **Pick Item（両方向）**：769の pick_item_from_block／pick_item_from_entity は
  位置・エンティティを指定し、旧バージョンの pick_item はスロット番号を
  指定するため、プロキシがワールドを知らない限り変換できない。
  1.21.4クライアントが古いサーバーのブロックを中クリックしても、
  古いクライアントが1.21.4サーバーのブロックを中クリックしても、
  アイテムは手に入らない（見た目だけの制限ではなく、操作が無効になる制限）。
- **Player Loaded（769）**：769クライアントが送る player_loaded（0x2A、空）は
  古いサーバーには送らない。769サーバーが player_loaded を前提に
  処理を進めるかどうかは未確認だが、統合テストでは参加・スポーン・
  チャット・移動が player_loaded 無しで動いたため、参加の障害には
  なっていない（下の「テスト」を参照）。
- **creaking_heart の natural フラグ（768↔769）**：768の `creaking` は
  3値（disabled／dormant／active）で、769の `active`（bool）と
  `natural`（bool）の2値に分ける。disabled＝プレイヤー設置（クリーキングを
  呼ばない）、dormant／active＝自然生成（呼ぶ）と推測して写した
  ベストエフォート。769の `active=true, natural=false`（3状態）の逆向きは
  768の active にする。実バニラでの creaking_heart の挙動は未確認。
- **trail 粒子の duration（768→769）**：768には duration が無いため、
  768→769では 20ティック（1秒）を入れる近似。実機未確認。
- **Vehicle Move の onGround（768→769）**：768クライアントは onGround を
  送らないため、768→769では `false` を付け足す近似。実機未確認。
- **Player Info の showHat／listPriority の順番（769→768）**：per-player の
  フィールド順は minecraft-data の protocol.json（listPriority の varint が先、
  showHat の bool が後）に従った。wiki.vg の旧ページでは確認できず、
  実バニラ1.21.4の Player Info パケットで両ビットが同時に立つ例は
  未確認。順番が逆だと Player Info の読み取りがずれ、プレイヤーリストが
  壊れるため、実機での確認が必要（統合テストでは hat＋list_order 付きの
  Player Info を769→767へ変換する単体テストで検証済みだが、実バニラの
  パケット順は未確認）。
- **BannerPatternLayer のインライン模様（769→768）**：768のときと同じく、
  インラインの BannerPattern NBT はそのまま通すベストエフォート。
- **ポーションのID（768↔769）**：ポーションの対応表はコードベースのどこにも無く、
  767↔768でも同じ扱い（不透明コピー）のため、768↔769でも同じ扱い。
  1.21.4で新しいポーションは無いため、番号がずれていないと仮定している
  が、ワイヤでの確認はできていない。
- **ボートの木の種類**：768と同じ制限。767のボートは769では常にオークのボートに、
  769のボートは767では常に（木の種類を問わず）ボートになる。ボートの
  variant（木の種類）をエンティティ種別に直す変換は未実装。
  767のボート variant の並び（OAK〜BAMBOO、PALE_OAK=9）はバニラの
  `Boat$Type` の宣言順からの推測で、実機未確認。
- **food の効果（767→768→769）**：768のときと同じ制限。金リンゴ等の食べたときの
  効果は768／769向けに落とす（767の food の効果は amplifier／duration を
  持たないため、768／769の `consumable` の apply_effects 形式へは変換できない）。
- **custom_model_data（767→768→769）**：768のときと同じ制限。767の単一の数値を
  768／769の colors[0] に入れるだけのベストエフォート。floats／flags／strings は
  空になる。
- **Set Cooldown（769のグループ名）**：768と同じく、エンダーパール・コーラスフルーツ・
  ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす
  （クールダウンの表示だけの制限）。
- **Declare Recipes／Tags／Statistics／World Event ID**：未対応。
- **Entity Metadata のスロット・ブロック状態・粒子の中身**：764では素通しの
  ため未変換のまま残る（763向けは variant 系を落とす）。
- **769＋online-modeの他プレイヤーから見たスキン**：763と同じ制限。
  UUID・名前は認証済みのものが使われる。
- **実機クライアントでの確認**：未実施。生ソケットで769クライアントを装った
  統合テスト（下の「テスト」を参照）と、プロキシの単体テスト
  （`ProxySession769Test`）で検証しています。
- **内部769サーバーの統合テスト**：実施済み（下の「テスト」を参照）。
  本物のバニラ1.21.4サーバー自体の実起動（`Done` まで）も確認済み。
- **1.21.5以降（770〜）は未対応**：プロキシは769より新しいプロトコルを
  ログイン時に断る（「新しすぎます」）。

## Java 21（内部769サーバーを動かすとき）

バニラ1.21.4サーバーは Java 21 を要求します（Minecraft Wiki の
1.21.4 記事：Minimum Java version Java SE 21）。DetergentMC本体は
Java 17 で動きます。`VanillaServerHost` は内部バージョンが1.21.4の
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

`docs/PLAYTEST-1.21.4.md` を参照してください。

## テスト

- `detergentmc-protocol`：`PacketIds769Test`（769のID表：24件の変更・
  新規3件・削除1件）、`ClientAdapter769Test`（769の受け入れ判定・
  全内部プロトコルとの振り分け）、`ClientJoinFlow769Test`（769の参加フロー）、
  `RegistryRemapper769Test`（768↔769対応表・合成表・往復）、
  `RegistryRemapper769TablesTest`（絵画・旗の模様・トリム・ダメージ種別の表）、
  `ChunkTranslator769Test`（768↔769変換・スロットの成分変換・チャンクの
  バイオーム（pale_garden の挿入・除去）・Update Attributes・ボートの出現・
  creaking_heart の変換）、
  `PlayTranslator769Test`（Player Info の hat／list_order・Set Held Item の
  i8⇔varint・Vehicle Move の onGround・Registry Data の pale_garden 挿入・除去）、
  `ProxySession769Test`（769のプロキシ参加フロー：内部763への設定フェーズ合成、
  内部764／765／766／767／768への設定フェーズ中継、内部769の素通し、763／764／765／766／
  767／768クライアントの内部769への参加、Registry Data の pale_garden 挿入・除去、
  Client Information の particleStatus 変換、764サーバー×769クライアントの
  join レベル変換、768クライアント→769サーバーへの Client Information の
  particleStatus 維持）。
  検証スクリプト `detergentmc-protocol/tools/verify-769-tables.py`
  （207件のチェック）と `verify-reverse-tables.py`。
- `detergentmc-core`：`ServerProtocolWiringTest`（内部769の配線・769用パックの
  受け渡し）、`VanillaServerHostTest`（Java 21 の選択）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla1214ThroughProxy`
  （偽769クライアントが本物のバニラ1.21.4に参加）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla1212ThroughProxy`
  （偽769クライアントが本物のバニラ1.21.2に参加）、
  `ProxyIntegrationTest.fake768ClientJoinsRealVanilla1214ThroughProxy`
  （偽768クライアントが本物のバニラ1.21.4に参加）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla121ThroughProxy`
  （偽769クライアントが本物のバニラ1.21に参加）、
  `ProxyIntegrationTest.fake767ClientJoinsRealVanilla1214ThroughProxy`
  （偽767クライアントが本物のバニラ1.21.4に参加）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla1205ThroughProxy`
  （偽769クライアントが本物のバニラ1.20.5に参加）、
  `ProxyIntegrationTest.fake766ClientJoinsRealVanilla1214ThroughProxy`
  （偽766クライアントが本物のバニラ1.20.5に参加）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla1204ThroughProxy`
  （偽769クライアントが本物のバニラ1.20.4に参加）、
  `ProxyIntegrationTest.fake765ClientJoinsRealVanilla1214ThroughProxy`
  （偽765クライアントが本物のバニラ1.20.4に参加）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla1202ThroughProxy`
  （偽769クライアントが本物のバニラ1.20.2に参加）、
  `ProxyIntegrationTest.fake764ClientJoinsRealVanilla1214ThroughProxy`
  （偽764クライアントが本物のバニラ1.20.2に参加）、
  `ProxyIntegrationTest.fake763ClientJoinsRealVanilla1214ThroughProxy`
  （偽763クライアントが本物のバニラ1.21.4に参加）、
  `ProxyIntegrationTest.fake769ClientJoinsRealVanilla1201ThroughProxy`
  （偽769クライアントが本物のバニラ1.20.1に参加）。
  `RegistryOrderCaptureTest.registryOrderOfRealVanilla1214`（本物のバニラ1.21.4から
  Registry Data の順番を記録し、対応表の仮説を検証する。
  バイオームは65件・pale_garden=39・plains=40。
  mace_smash=26／magic=27 を含む。楽器は8件・admire=0・yearn=7）。
  既存の統合テスト（1.21.2対応の12件）も回帰テストとして残っています。
- `detergentmc-bedrock`：`JavaBackendConnection769Test`（Bedrock→内部769の
  参加・Select Known Packs／Ping・Respawn の次元番号・Update Attributes の
  769形式・スロットの769正規化・ボートの分割・クリイキングの代替）。
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_4`（Bedrock 6 プロトコル ×
  内部769の統合テスト）。
