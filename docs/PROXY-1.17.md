# 1.17クライアント → 1.17系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.17クライアント（プロトコル755）は、内部プロトコルが
755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775の20プロトコルのサーバーを対象にします。
ただし **758（1.18.2）は未対応**で、このツリーに `V1_18_2` はありません
（他人の作業中のため飛ばした。758が終わったら758↔757の合成表をそのときに作る）。
776（26.2）もこのツリーにありません。
内部755のときは素通し（リレー）、内部756のときは755↔756の直接変換、
それ以外は756を経由した連鎖変換です。**755は756とパケットID・レジストリ・
Join Game／Respawn codec がすべて同一**で、違うのは5パケットのフィールド構成だけです。
パケットID差分は**変更0件・新規0件・削除0件**、レジストリ差分も**全種0件**
（ブロック状態20,342件・ブロック898件・アイテム1,100件・
エンティティ113件・音1,190件・パーティクル89件・バイオーム81件ほか、
すべて恒等写像）です。

1.17は**Java 16**で動き、設定フェーズがありません（764で追加）。
レジストリは Join Game の dimensionCodec NBT で届きます（756と同じ方式）。
pack_format はリソース7・データ7。アイテムはNBT（データコンポーネントではない）。
1.17はプロトコル755唯一のバージョンです（1.17.1はプロトコル756）。

755の仕組みは `docs/PROXY-1.17.1.md`（756）、`docs/PROXY-1.18.md`（757）、
`docs/PROXY-1.19.md`（759）、`docs/PROXY-1.19.1.md`（760）、
`docs/PROXY-1.19.3.md`（761）、`docs/PROXY-1.19.4.md`（762）、
`docs/PROXY-1.20.1.md`（763）、逆向きの考え方は `docs/PROXY-REVERSE.md` を参照してください。
なお755→756の差分は「756を隣接にした直接の差分」です
（777のときの776問題・757のときの758問題と同じ構図で、同じ方針 (b) を取った。
758が終わったら758↔757の合成表をそのときに作る）。

全400通り（クライアント755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775 × サーバー755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775）の対応マトリクスは `docs/VERSIONS.md` の「1.17対応：
全400通りの対応マトリクス」を参照してください（Stage E で追加済み）。

## 対応表（755クライアント × サーバー、逆向きも）

**方向の非対称がこの文書の前提です。逆に書かないこと。**
古いクライアント×新しいサーバーは参加できず、新しいクライアント×古いサーバーは
Join Game が通ります（756の Stage E でこの向きの誤りが多かったため、ここで明記する）。

| クライアント | サーバー | 経路 | 結果 |
| --- | --- | --- | --- |
| 755（1.17） | 755（1.17） | 素通し（リレー） | 参加できる（`ProxySession755Test.join755Against755IsNative` で確認） |
| 755（1.17） | 756（1.17.1） | 756→757→759→763の連鎖を経て `handleServerJoinGame763` に到達 | **参加できない**（サーバー→クライアントの Join Game が `ProxySession.java:7058` の `if (is757() \|\| is756() \|\| is755())` で落とされる。Respawn も `handleServerPlayUp:8029`／`handleServerPlay765From764:8323` のガードで落とされる。Keep Alive だけは通るが Join Game が無いので参加できない。バグではない） |
| 755（1.17） | 757〜775（1.18〜26.1） | 757〜775→…→763の連鎖を経て同じガードに到達 | **参加できない**（同上。Keep Alive だけ通ることは `ProxySession755Test.keepAlive765Against755IsNotDroppedByRespawnGuard`／`keepAlive764Against755IsNotClosedByDisconnectGuard` で確認。Join Game は要らない段取りのため、Keep Alive の到達だけ確かめたもの） |
| 756（1.17.1） | 755（1.17） | 755↔756の直接変換 | 参加できる。Join Game／Respawn とも通る（`ProxySession755Test.joinAndRespawn755Against756Convert` で確認。`loginTo(756, 755)`＝クライアント756・サーバー755。テスト名の向きに注意） |
| 757（1.18／1.18.1） | 755（1.17） | 755→756→757の連鎖変換 | Join Game は通るはず（`handleServerPlayFrom755`→`handleServerPlayFrom756` の経路で確認。**755サーバー＋757クライアントの直接の単体テストは無いため推論**）。Respawn も通るはず（同じ経路。**直接テストは無いため推論**） |
| 759〜762（1.19〜1.19.4） | 755（1.17） | 755→756→757→759→…→763の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn も通るはず（`handleServerPlayFrom763` の Respawn 分岐の落としリストに759〜762は無い。コードで確認したが**755サーバーでの直接テストは無いため推論**） |
| 763〜765（1.20／1.20.1〜1.20.4） | 755（1.17） | 755→756→757→759→763の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn も通るはず（`handleServerPlayFrom763` の Respawn 分岐の落としリストに763〜765は無い。コードで確認したが**755サーバーでの直接テストは無いため推論**。756サーバーと同じ下流連鎖による推論） |
| 766〜775（1.20.5〜26.1） | 755（1.17） | 766〜775→…→757→756の連鎖変換 | Join Game は通るはず（**推論**。774は `ProxySession755Test.join774Against755ConvertsJoinGameButDropsRespawn` で確認：Join Game 0x30が Finish Configuration 後に届く）。Respawn は下流（`handleServerPlayFrom763` の Respawn 分岐）で落とされる（774は同テストで確認。766／767／768／769／770／771／772／773／775は落としリストに載っていることをコードで確認したが、**755サーバーでの直接テストは無いため推論**） |
| 774（1.21.11） | 755（1.17） | 三段変換755→756→757→759→763→774 | Join Game は通るが **Respawn は落とされる**（死亡後のリスポーンで確かめること。`ProxySession755Test.join774Against755ConvertsJoinGameButDropsRespawn` で確認） |

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。755用・756用・757用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の20つを出す（758用・776用は無い） |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 756↔755の差分（要点）

minecraft-data（`pc/1.17/protocol.json`・`pc/1.17.1/protocol.json`）と
ViaVersion（`mapping-1.17.json`）と
Minecraft Wiki の Packets ページ（1.17、protocol 755、2021-06-25 の改訂版
2772680）の三重照合で確認。不一致0件。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-755.py`・
`verify-755-packet-ids.py`・`verify-755-wiki.py`・`insert-755-java.py`。
件数の定義：変更＝両方にあってIDが違うもの、新規＝755にあって756の
`PacketIds` に無いもの、削除＝756にあって755に無いもの。
写像は `detergentmc-protocol/build/remap-data/packet-ids-755.json`（272件）。
`PacketIds.java` のエントリ総数は272件のまま（新規エントリ0件）。
id755>=0 は160件（HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 103件＋
PLAY サーバー行き 48件。CONFIGURATION は0件）。

| 分野 | 756↔755の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ・クライアント行き） | **変更0件・新規0件・削除0件**。Join Game 0x26両方、Chunk Data 0x22両方、Keep Alive 0x21両方、Ping 0x30両方、Respawn 0x3d両方、Player Info 0x36両方、Disconnect 0x1A両方、Update Time 0x58両方 | `PacketIds` の755列で番号を付け替える（値は756と同じ）。唯一の違いは内部名の改名1件：`destroy_entity`（1.17）→`entity_destroy`（1.17.1）、IDは同じ 0x3a |
| パケットID（プレイ・サーバー行き） | **変更0件・新規0件・削除0件**。Chat（サーバー行き）0x03両方、Keep Alive 応答 0x0F両方、Window Click 0x08両方、Set Creative Slot 0x28両方 | そのまま通す |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一（Login Start 0x00、Login Success 0x02、ハンドシェイク 0x00） | そのまま通す |
| 設定フェーズ | **755にも756にも無い**（764で追加） | プロキシが合成・消費する（756と同じ。`docs/PROXY-1.19.3.md` の「設定フェーズなしクライアントの扱い方」を参照） |
| Join Game | **フィールド同一**（下の「Join Game／Respawn codec」を参照）。simulationDistance はどちらにも無い | `PlayTranslator.translateJoinGame755To756`／`756To755` で変換する（パースで検証してからペイロードをそのまま返す） |
| Respawn | **フィールド同一**（dimension element の置き換えのみ） | `PlayTranslator.translateRespawn755To756`／`756To755` で変換する |
| Join Game codec の中身 | **dimension_type は4件・同一順序**（overworld 0、overworld_caves 1、the_nether 2、the_end 3）。**バイオームは755も756も81件**（id 0〜175の非連番）。`minecraft:chat_type` はどちらにも無い | codec は作り直さない（`translateDimensionCodec755To756`／`756To755` はそのまま返す）。`minecraft:damage_type` レジストリは1.17・1.17.1のどちらにも無い（1.19.4で追加） |
| チャット | **755も756も署名なし `chat`**（サーバー行きは {message string}のみ、クライアント行き 0x0F）。1.19は System Chat／Player Chat 等に分離（署名付き） | 755↔756は素通し。759以上との間は専用の翻訳子で変換する（下の「チャット」を参照）。署名チェーンの検証は境界を越えられない |
| Spawn Entity | 755も756も `spawn_entity`（0x00）／`spawn_entity_living`（0x02）／`spawn_entity_painting`（0x03）が別パケット | そのまま通す（759以上との間は756の翻訳子を経由する） |
| Player Info | 755と756で同一の旧式（action varint。add_player は crypto オプション付き、UUID は無い） | 暗号化情報の補完／除去だけする（756の翻訳子を経由する） |
| Set Slot／Window Items／Window Click | **フィールド構成が違う**（下の「フィールド差分5件」を参照）。IDは同一（0x16／0x14／0x08） | `ChunkTranslator` の755節で stateId 0 を補完／除去する |
| Entity Destroy | **フィールド構成が違う**（755は [entityId varint] 1体、756は [count][entityIds]）。IDは同一（0x3a） | `ChunkTranslator` の755節で単体⇔count 1の配列に直す |
| Edit Book | **フィールドが全面的に違う**（下の「フィールド差分5件」を参照）。IDは同一（0x0B） | 変換器なしで落とす |
| Chunk Data | **形式が同一**（下の「チャンク」を参照）。IDは 0x22両方 | `ChunkTranslator` の755節で変換する（素通し相当） |
| スロット | 755／756で同一（`[present bool][id varint][count i8][optionalNbt]`、NBT時代でKIND表なし） | そのまま通す |
| Explosion／Block Entity Data／Update Attributes | 755／756で同一のため変換器なし（意図的な省略） | そのまま通す。ただし Block Entity Data は素通しではなく `translateTileEntityData756To757` を使う（action u8→varint、NBTの id／x／y／z 剥がし。756と同じ） |
| ブロック状態 | **755=20,342件、756=20,342件。差分0**（文字列は完全一致・同一順序） | 変換なし（identity） |
| ブロック | **755=898件、756=898件。差分0**（同一順序） | 変換なし（identity） |
| アイテム | **755=1,100件、756=1,100件。差分0** | 変換なし（identity） |
| エンティティ | **755=113件、756=113件。差分0** | 変換なし（identity） |
| 音 | **755=1,190件、756=1,190件。差分0** | 変換なし（identity） |
| パーティクル | **755=89件、756=89件。差分0** | 変換なし（identity） |
| メニュー | 24件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 属性 | 13件で同じ顔ぶれ・同じ順番（文字列キー。数値表は不要） | 変換なし（identity） |
| エンチャント | **755=38件、756=38件。差分0**（同一順序） | 変換なし（identity） |
| ブロックエンティティ種別 | **755=34件、756=34件。差分0**（同一順序） | 変換なし（identity） |
| バイオーム | **755=81件、756=81件。差分0**（id 0〜175の非連番。両方同じ） | 変換なし（identity）。隙間の id は平原（1）で埋める |
| エフェクト | **755=32件、756=32件。差分0**（同一順序） | 変換なし（identity） |
| レシピシリアライザ | 21件で同じ | 変換なし |
| ダメージ種別 | **755・756のどちらにも無い**（1.19.4／762で追加） | 対応表は42件の恒等写像プレースホルダ。ワイヤ上の呼び出し元は無い（756と同じ） |
| データコンポーネント種別 | 無し（1.20.5で追加） | N/A |
| 絵画 | 755も756も `minecraft:motive` 26件で同じ | 変換なし（identity） |

### フィールド差分5件（756↔755で ID は同一・構成だけ違う）

情報源は minecraft-data protocol.json（`mc-1.17-protocol.json`／`mc-1.17.1-protocol.json`）の
全パケット突き合わせ（両方173件。名前の違いは改名1件のみ）に加え、
公式 `server_mappings.txt` で求めた難読化クラス名での実バニラ両jarの javap と、
ViaVersion（`Protocol1_17To1_17_1.java`、`ItemPacketRewriter1_17_1.java`）の三重照合。
トップレベル types の差分は0件。**フィールド構成の差分は全パケット中でこの5件だけ**。

| パケット | 755 | 756 | 扱い |
| --- | --- | --- | --- |
| Set Slot（0x16、クライアント行き） | {windowId i8, slot i16, item} | {windowId i8, **stateId varint**, slot i16, item} | 755→756は stateId 0 を挿入、756→755は除去（`translateSetSlot755To756`／`756To755`。補完値 0 は ViaVersion と同じ） |
| Window Items（0x14、クライアント行き） | {windowId u8, count i16, items, } | {windowId u8, **stateId varint**, count **varint**, items, **carriedItem slot**} | 755→756は stateId 0＋carriedItem（absent スロット）を補い、count を i16→varint に直す。756→755は逆（`translateWindowItems755To756`／`756To755`） |
| Window Click（0x08、サーバー行き） | {windowId u8, slot i16, button i8, mode varint, slots, carriedItem} | {windowId u8, **stateId varint**, slot i16, button i8, mode varint, slots, carriedItem} | 755→756は stateId 0 を挿入、756→755は除去（`translateWindowClick755To756`／`756To755`）。mode は両方 varint（minecraft-data の「1.17で i8」は誤り。javap -c で両方 `writeEnum` を確認） |
| Entity Destroy（0x3a、クライアント行き） | `[entityId varint]` 1体 | `[count varint][entityIds]` | 755→756は count 1 の配列に、756→755は単体に直す（`translateEntityDestroy755To756`／`756To755`）。756→755で count 0／2以上は変換器が `null` を返す（755の1パケットでは表せないため。分割送信には ProxySession 側の対応が要る） |
| Edit Book（0x0b、サーバー行き） | {new_book slot, signing bool, hand varint} | {hand varint, pages array\<string\>, title option\<string\>} | **変換器なしで落とす**（全面的に違うため。ViaVersion の変換は NBT へのページ／タイトル／著者の格納が必要で、単純な付け替えでは済まない） |

## パケットIDの対応表（756↔755、確認したものだけ）

出典は `detergentmc-protocol/build/remap-data/packet-ids-755.json`（272件）。
**変更0件・新規0件・削除0件**。サーバー行き・LOGIN・CONFIGURATION・HANDSHAKINGも変更なし。
ここに無いパケットのIDは未確認のため書かない。

### 主要パケット（両方で同一のもの、確認済み）

| パケット | 756 | 755 |
| --- | --- | --- |
| Join Game（`login`） | 0x26 | 0x26 |
| Chunk Data（`map_chunk`） | 0x22 | 0x22 |
| Respawn | 0x3D | 0x3D |
| Spawn Position | 0x4B | 0x4B |
| Keep Alive（クライアント行き） | 0x21 | 0x21 |
| Keep Alive（サーバー行き・応答） | 0x0F | 0x0F |
| Ping | 0x30 | 0x30 |
| Chat（クライアント行き） | 0x0F | 0x0F |
| Chat（サーバー行き） | 0x03 | 0x03 |
| Player Info（`player_info`） | 0x36 | 0x36 |
| Disconnect（`kick_disconnect`） | 0x1A | 0x1A |
| Resource Pack Send | 0x3C | 0x3C |
| Client Information（`settings`、サーバー行き） | 0x05 | 0x05 |
| Spawn Entity | 0x00 | 0x00 |
| Spawn Entity Living | 0x02 | 0x02 |
| Spawn Entity Painting | 0x03 | 0x03 |
| Named Entity Spawn | 0x04 | 0x04 |
| Block Entity Data（`tile_entity_data`） | 0x0A | 0x0A |
| Update Light | 0x25 | 0x25 |
| Multi Block Change | 0x3F | 0x3F |
| Set Slot | 0x16 | 0x16 |
| Window Items | 0x14 | 0x14 |
| Trade List | 0x28 | 0x28 |
| Entity Metadata | 0x4D | 0x4D |
| World Particles | 0x24 | 0x24 |
| Named Sound Effect | 0x19 | 0x19 |
| Explosion | 0x1C | 0x1C |
| Block Change | 0x0C | 0x0C |
| Block Action | 0x0B | 0x0B |
| Open Window | 0x2E | 0x2E |
| Set Cooldown | 0x17 | 0x17 |
| Entity Equipment | 0x50 | 0x50 |
| Window Click（サーバー行き） | 0x08 | 0x08 |
| Set Creative Slot（サーバー行き） | 0x28 | 0x28 |
| Enchant Item（サーバー行き） | 0x07 | 0x07 |
| Block Dig（サーバー行き） | 0x1A | 0x1A |
| Block Place（サーバー行き） | 0x2E | 0x2E |
| Use Item（サーバー行き） | 0x2F | 0x2F |
| Update Sign（サーバー行き） | 0x2B | 0x2B |
| Set Beacon Effect（サーバー行き） | 0x24 | 0x24 |
| Update Structure Block（サーバー行き） | 0x2A | 0x2A |
| Edit Book（サーバー行き） | 0x0B | 0x0B |
| Entity Destroy（`entity_destroy`／`destroy_entity`） | 0x3A | 0x3A |
| Update Time | 0x58 | 0x58 |
| Login Start | 0x00 | 0x00 |
| Login Success | 0x02 | 0x02 |
| Login Set Compression | 0x03 | 0x03 |

## Join Game／Respawn codec

情報源：公式 server_mappings.txt（1.17 sha1 `84d80036…`、1.17.1 sha1
`f6cae1c5…`、両方 sha1 照合済み）と minecraft-data protocol.json
（mc-1.17／mc-1.17.1-protocol.json）。
実キャプチャ：実1.17 Join Game 31,555バイト／実1.17.1 Join Game 31,555バイト。
両 Join Game のバイト比較：差分は **9 バイトのみ**（entityId の 1 バイト＝349 vs 374、
hashedSeed の 8 バイト）。それ以外はバイト同一（dimensionCodec 全体を含む）。

**Join Game（ClientboundLoginPacket）**：**変更なし**。LoginPacket は両方 `ra`、
RespawnPacket は両方 `rv`。クラスブロックは行数も内容も完全一致
（Login 40 行、Respawn 23 行）。`simulationDistance` はどちらにも無く、
`lastDeathLocation` もどちらにも無い。
- フィールド：playerId、seed、hardcore、gameType、previousGameType
  （i8 で -1 が無し。26.3の Optional 化とは違う）、levels、
  registryHolder、dimensionType、dimension、maxPlayers、chunkRadius、reducedDebugInfo、
  showDeathScreen、isDebug、isFlat。`dimension` は両方とも NBT 値。
- `dimensionCodec`（Join Game 内の NBT）：`minecraft:dimension_type` は
  4件（overworld 0、overworld_caves 1、the_nether 2、the_end 3。1.17と
  1.17.1で同一順序）。`minecraft:worldgen/biome` は755も756も81件（id 0〜175の非連番。
  `biomes-1.17.json` の（id, name）と完全一致）。`minecraft:chat_type` はどちらにも無い。
- dimension element（overworld）：logical_height 256、min_y 0、height 256
  （1.18 の 384／-64 とは違う）。
- Join Game フィールド実測値（両バージョンで entityId・seed 以外同一）：isHardcore false、
  gameMode 0、previousGameMode -1、worldNames 3 件（overworld／the_nether／the_end）、
  worldName minecraft:overworld、maxPlayers 5、**viewDistance 10**
  （server.properties=10 と一致。756ノートの「1.17.1 は viewDistance=4」の
  怪しい挙動とは違う。1.17では10だった）、reducedDebugInfo false、enableRespawnScreen true、isDebug false、
  isFlat false。
- **罠（756との違い）**：1.17.1の Join Game の viewDistance=4 は1.17では再現しなかった。
  値はそのまま引き継いでいる。理由は未確認。

**Respawn（ClientboundRespawnPacket）**：フィールド同一。dimension elementの
置き換えのみ。copyMetadataは末尾で両方同一、dataToKeepはどちらにも無い
（757の§13.4の罠を踏まないことをprotocol.jsonで確認）。
Respawn の previousGameMode は u8（Join Game の i8 と違う）。
「none」の符号は不明のため範囲チェックせずそのまま写す。

**Registry Data パケット**：1.17／1.17.1には存在しない（設定フェーズは
1.20.2／764で追加）。レジストリは Join Game の dimensionCodec NBT で
送られる。

## チャンク（755⇔756）

情報源：protocol.json＋実バニラ1.17.1生キャプチャ
（チャンク-21,-17、13,449バイト。テストリソース
`cap756-chunk-21-17.bin`）＋両jarのjavap。実キャプチャの往復は
バイト完全一致。**1.17専用の生キャプチャは無く、Stage Eで実バニラ1.17から
取るのが本来の筋であることも記録する**（ChunkTranslator755Test の javadoc に明記）。

- **755も756も同一**：16セクション（y 0〜15）＋bitMap＋heightmaps NBT＋
  **バイオーム int[1024]**＋光なし。
- **BitStorageは両方ともnon-straddling**（BPE 5のData Array長は342。
  straddling式だと320）。755節・756節はnon-straddlingで実装した。
  757節のstraddling前提は直していない（判断待ち。下の「対象外」を参照）。
- バイオームは755配列の [64s, 64s+64) がセクションsに対応
  （javapで `ChunkBiomeContainer` のindex式を確認。756と同じ）。
  bitMapに無いセクションのバイオームも配列の値をそのまま使う。
- ブロックエンティティ：755も756も名前付きNBTにid／x／y／zを含む
  （生キャプチャで確認）。
- Update Lightのマスクはビットi＝セクション(i-1)。1.17は18ビット、
  1.18は26ビット。マスクは±4ずらす。
- メタデータ型番号は1.17／1.17.1で同一（0〜18。slot=6、ブロック状態=13、
  粒子=15）。757節の1.19番号付けは使えない（判断待ちのまま）。
- 757→755のChunk Dataは**光を落とす**（光抽出の配線なし。下の「対象外」を参照）。
  755→757のChunk Dataは光を合成しない（755に光が無いため。空の光を送る）。
- チャンク形式同一が protocol.json＋javap で確定しているため、1.17.1の生キャプチャ
  `cap756-chunk-21-17.bin` はそのまま755形式のチャンクでもある
  （`ChunkTranslator755Test` の javadoc に記録済み）。

## チャット（755は署名なし）

- **Chat（クライアント行き 0x0F／サーバー行き 0x03）**：1.17も1.17.1も
  署名なしの `chat`（サーバー行きは {message string}のみ）。
  755↔756は素通し。1.19は System Chat／Player Chat／Chat Message／
  Chat Command に分離（署名付き）。
- 755→759：755→756は素通しで、756→757→759の翻訳子が Chat Message
  （`translateChat757ToChatMessage`）か Chat Command
  （`translateChat757ToChatCommand`）へ変換する。チャット署名は現在時刻・
  salt 0・空署名・signedPreview=false で補完する（756と同じ簡略化）。
  テスト確認：`ProxySession755Test.clientPackets755Against759Convert`
  （chat 0x03→Chat Message 0x04）。
- 759→755：Chat Message は chat へ変換する（`ProxySession755Test.clientPackets759Against755Convert`。
  Chat Message 0x04→chat 0x03。署名は捨てられる）。Player Chat は落とす（署名付きのため。
  ただし現状は755クライアントは759サーバーに参加できないため、この組み合わせでは
  実機で確認できない。翻訳子としての落としは756と同じ扱い）。
- **方向Bの制限（テスト確認）**：755サーバー→756クライアントでは、Join Game／Respawn／
  Chunk Data は届くが、**chat（0x0F）／Set Slot（0x16）／Window Items（0x14）／
  Entity Destroy（0x3a）は汎用経路で落とされる**
  （`ProxySession755Test.joinAndRespawn755Against756Convert` で確認。
  変換器自体は存在するが、ProxySession からの呼び出し口が無いためセッション経由では落とす。
  756サーバー→755クライアントの汎用パケットも同じく落とす。
  `ProxySession755Test.entityDestroy756Against755Drops` で確認）。
- **署名チェーンの検証**：境界を越えると署名チェーンはつながらない。
  正直な制限として残す。

## 代替表（755にしかないもの・756にしかないもの）

**不要。すべて恒等写像のため代替表は無い。** `RegistryTables755.java`（50表）と
`generate-remap-tables.py` の755対応で作ったもの。改名・削除・新規は全種0件のため、
代替はすべて「無し」。補正も全種0件（`maps755-corrections.json` は全種0件）。
反転表は名前の真値と一致する（`verify-755-tables.py` 43/43で確認）。

- アイテム：差分0件のため代替なし。
- 音（1,190件で同一）：代替なし。
- パーティクル（89件で同一）：代替なし。
- バイオーム（81件で同一。id 0〜175の非連番）：代替なし。隙間の id は平原（1）で埋める。
- エフェクト・ブロックエンティティ種別：表を作らずハードコードする
  （恒等写像。DROP なし）。ダメージ種別は 42 件の恒等写像プレースホルダ。

## Client Information（0x05両方）

- 755の Client Information は**7フィールド**（locale／viewDistance i8／chatFlags varint／
  chatColors／skinParts u8／mainHand varint／**disableTextFiltering**。末尾は false。
  allowServerListings無し）。756も同じ7フィールドのため755↔756は素通し
  （`translateClientInformation755To756`／`756To755` はそのまま返す）。
- 755→759の Client Information は末尾が757形式になる
  （`ProxySession755Test.clientPackets755Against759Convert` で確認：
  enableTextFiltering に反転、enableServerListing を true で補完）。
- `enableServerListing` は756→755では落とし、755→756では **trueで補完**する。
  **この既定値は推論による選択で未確認**（実バニラ1.17クライアントの送信値は
  未確認）。実機での確認が必要。
- 763→755の Client Information は素通し（763→755変換器なし。
  末尾フィールド差分は未対応）。

## 接続の流れ

### 755クライアント → 内部755サーバー

1. ハンドシェイク（755、状態2）。
2. Login Start（名前のみ）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x26、codec 付き）。
5. Spawn Position（0x4B）。チャンク（0x22）・Player Info（0x36）などが届く。

### 755クライアント → 内部756サーバー（参加できない）

1. ハンドシェイク（755、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ進むが、サーバー→クライアントの Join Game が
   `handleServerJoinGame763` の is755() 分岐（`ProxySession.java:7058`）で落とされる。
   Respawn も落とされる。Keep Alive だけは通るが Join Game が無いので参加できない。
   **確認するのは「参加できないこと自体」である**（`docs/PLAYTEST-1.17.md` の「4.」を参照）。
   参加できる前提のチェックリストにしないこと。

### 756クライアント → 内部755サーバー

1. ハンドシェイク（756、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。755サーバーの Join Game（0x26）を756形式（0x26）に
   変換する（`handleServerPlayFrom755`→`to756ServerPacketFrom755`→
   `handleServerPlayFrom756`。ペイロードはそのまま返す。codec は作り直さない）。
5. Spawn Position（0x4B両方）。リソースパックは755形式で送る（`pack755`、
   無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、無ければ `pack760`、
   無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 755サーバー → 774クライアント

- Join Game は通る（三段変換 755→756→757→759→763→774で Join Game。
  `ProxySession755Test.join774Against755ConvertsJoinGameButDropsRespawn` で確認。
  Login Success 後に合成設定フェーズに入り、Registry Data＋Finish Configuration が送られ、
  Finish Configuration の応答で Join Game 0x30 が届く）。
- Respawn は落とされる（下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。同テストで確認）。

## プレイ中の翻訳

- **チャンク**：ブロック状態（755=20,342／756=20,342で同一）・バイオーム
  （755=81／756=81で同一）・ブロックエンティティ種別（34件で同一）を付け替える必要はない
  （恒等写像）。755→756のChunk Dataは素通し相当。756→757では光を落とす。
- **スロット・ウィンドウ**：アイテムID（755=1,100／756=1,100で同一）の付け替えは不要。
  755⇔756方向では Set Slot／Window Items／Window Click を stateId 0 の補完／除去で
  変換する（落とさない）。755クライアントのWindow Clickは755→756→757→759の三段で
  759の0x0Aに届く（stateId 0付き。テストで確認）。
  **763→755方向は落とすまま**（未実装。`ProxySession755Test.windowClick763Against755Drops` で確認）。
- **エンティティ**：エンティティ種別は755=113／756=113で同一のため付け替えなし。
  Spawn Entity／Living／Painting（0x00／0x02／0x03）は両方で同一のため素通し。
  Entity Destroy は755⇔756の直接変換では単体⇔count 1の配列に直すが、
  セッション経由では755サーバー→756クライアントで落とされる（上の「チャット」を参照）。
  756サーバー→755クライアントでも落とされる。
- **音・パーティクル**：音（755=1,190／756=1,190）・パーティクル
  （755=89／756=89）は同一のため付け替えなし。1.19の Sound 系パケットの
  末尾の seed i64 は755向けに落とす（756の翻訳子を経由する）。
- **位置・Server Data・Trade List**：上の表の通り。Server Data は755向けに落とす。
- **Client Information**：755⇔756は素通し（7フィールド同一）。763→755は素通し。
- **クライアント→サーバー（方向B、テスト確認）**：759クライアントの Chat Message 0x04→chat 0x03、
  Block Dig 0x28→0x1A（`ProxySession755Test.clientPackets759Against755Convert`。
  `loginTo(759, 755)`＝クライアント759・サーバー755）。774クライアントの
  Chat Message 0x08→chat 0x03、Block Dig 0x28→0x1A（同 `join774Against755ConvertsJoinGameButDropsRespawn`）。
  755クライアントの chat 0x03→Chat Message 0x04、Block Dig 0x1A→0x1C
  （`ProxySession755Test.clientPackets755Against759Convert`。sequence は 0 を補う）。

## 送らない・落とすパケット（755向け）

- 756〜775サーバー→755クライアントの Join Game／Respawn（上の対応表を参照。バグではない）
- 756〜775サーバー→755クライアントの汎用パケット（`handleServerPlayFrom763` の汎用経路の
  落としリストに755がある。763→755の汎用パケット変換が未実装のため。
  chat／Set Slot／Window Items／Entity Destroy はテストで確認）
- 755サーバー→756クライアントの chat／Set Slot／Window Items／Entity Destroy
  （汎用経路で落とす。変換器はあるが呼び出し口が無い。上の「チャット」を参照）
- 755サーバー→766〜775クライアントの Respawn（下流で落とす。774はテスト確認）
- Player Chat（759→755。署名付きのため変換できない。ただし現状は755クライアントは
  759サーバーに参加できないため実機では確認できない）
- server_data、should_display_chat_preview、chat_preview、chat_preview_to_server（755に無いため。変換器なしでProxySessionがIDで落とす）
- Window Click／Set Creative Slot（763→755方向。未実装のため落とす）
- Edit Book（全方向。変換器がどこにも無いため落とす。素通ししない）
- 757→755のChunk Dataの光（光抽出の配線なし。チャンク自体は届くが光が無い）
- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応（既存の制限と同じ）
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす
- 756→755の Entity Destroy の count 0／2以上（変換器が null を返す。755の1パケットでは表せないため。
  分割送信には ProxySession 側の対応が要る。呼び出し口が無いため現状は汎用経路で落とす扱い）

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全バージョン共通。手順書は
  `docs/PLAYTEST-1.17.md`。偽クライアント＋本物のバニラでの Join Game
  キャプチャ（31,555バイト／31,555バイト）と実バニラ1.17の起動確認
  （`Done (100.329s)!`、pack metadataエラー0件）は済み。
  プロキシ経由の統合テスト（Fake755Client）は Stage E で実施中（下の「統合テスト」を参照）。
- **763〜775→755の Join Game／Respawn は落とすまま**：763形式は759形式＋
  portalCooldown で変換自体は容易だが、763のコーデックのバイオーム順が
  cherry_grove の1.20プロトコルIDに依存し未測定
  （minecraft-data の biomes.json はアルファベット順でプロトコル順ではない。
  1.20のデータジェネレータ実行はJVMのTCP遮断で不可）。
  764〜775は Join Game の dimension をレジストリIDで送り、dimension_type
  レジストリは設定フェーズで別送されるが ProxySession が保持していない。
  777の §21.5と同じ種類の残課題。
- **755サーバー→774クライアントの Respawn は落とされる**：Join Game は
  通るが Respawn は下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。
- **763〜767の内部サーバー＋755クライアントの Join Game／Respawn は引き続き
  落とされる**（777の §21.5と同じ種類）。
- **759／760サーバー＋766以上クライアントのログイン合成ギャップ**：
  755だけ直した（下の「罠」を参照）。759／760サーバーにも同じ隙間があるが
  本タスクの範囲外。
- **Window Click／Set Creative Slot の755行きは未実装**：763→755方向は
  落とすまま。755⇔756方向は変換する。
- **光の抽出の配線なし**：`ChunkTranslator.extractUpdateLightFromChunkData757`
  自体は動作確認済み（テストで756形式Update Lightが返ることを検証）が、
  呼び出し口が無い。757サーバー→755クライアントのChunk Dataは
  `From755→From756→From757→…→From763` の連鎖で汎用経路に落とされるため、
  `to756ClientPacketFrom755` にはChunk Dataは決して来ない。配線には763→757の
  サーバー側変換（756と同じ既知のギャップ）が必要。推測で無理に繋げていない。
- **Client Information の763→755は素通し**：末尾フィールド差分は未対応。
- **Gradleテストの実行は未実施**（このVMにGradleが無いため）。
  単体テストはJUnitスタブ＋リフレクション呼び出し／JUnit 5.10.1実jar＋
  自作ランナーで実行した（下の「テスト」を参照）。
- **1.17の実バニラ起動は実施済み**（Done 100.329s）。1.17クライアントでのパック受け入れ・
  実機プレイは未実施。
- **チャットの署名チェーンはバージョン境界を越えられない**：755→759は
  現在時刻・salt 0・空署名・signedPreview=false で補完する。
  実機の759クライアントが755サーバーからのチャットをどう表示するかは未確認。
- **Player Chat 759→755は落とす**：翻訳子としては759サーバーからの Player Chat を
  755形式に変換できないため落とす（上の「チャット」を参照）。なお現状は Join Game が
  落とされるため755クライアントは759サーバーに参加できない。
  実機での確認が必要。
- **Join Game codec の素通しではない**：755→756ではペイロードをそのまま返すが、
  パースで検証してから返す（`translateJoinGame755To756`）。バイオーム81件→81件、
  dimension_type の element は両方同一のため作り直さない。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  755＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info にプロパティを載せない）。
- **外部モードで pack755 が無い場合**：755クライアントには756パック
  （pack_format 7）が出る。755と756はどちらも pack_format 7 のため形式は変わらない。
  javadoc／コメントに記録済み。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrock→755**：`JavaBackendConnection` の serverProtocol 755 を756扱いにした
  （Login Success→即プレイ、Join Game 755、Respawn 755→756→759連鎖、
  チャット送受信、Player Info、Living／Painting ディスパッチ、
  Client Information 切り出し）。チャンクは755のワイヤ形式（16セクション＋
  bitMap＋名前付きheightmaps＋バイオームint[1024]・BitStorage non-straddling）
  で読む。755固有の追加は Entity Destroy の単体varint読み（`parseRemoveEntity755`）と
  Respawn連鎖755→756→759の2点だけ。Bedrock 側の統合テストはまだ無い。Bedrock のインベントリは引き続き未対応。
- **770〜775のチャンク読みの不整合の疑い**：`BedrockChunkTranslator` と
  Java側 `ChunkTranslator` の770〜775読みは「1 short＋straddling」だが、
  実バニラ26.1の生キャプチャ＋バイトコードは「2 shorts＋non-straddling」を示す。
  755は正しい形式で実装し、770〜775は触っていない（Muse01／ユーザーの判断待ち）。
  Stage B の ChunkTranslator 757節も straddling 前提で、実バニラ1.18
  （non-straddling）と食い違うことが756の実測で確定した（同じく判断待ち）。
- **メタデータ型番号の1.19付け**：757節は1.19の型番号で書かれているが、
  1.17／1.17.1は0〜18で同一のため757節の番号付けは755⇔756では使えない。
  判断待ち（本タスクでは触っていない）。
- **`RegistryRemapper` の `ITEM_REVERSE_MAP` と `ITEM_763_TO_765` の食い違い**：
  ボート・レコード・絵画で値が違う（Bedrock側の755分岐で発見。756のときと
  同じ箇所。`RegistryRemapper` 自体は触っていない）。
  プロキシ側の `mapItemReverse` 経路（755／756／757／759〜762クライアント）にも
  影響する疑い。修正は統括側（Muse01／ユーザー）の判断待ち。
- **mapItemReverse の757／759／760／761／762分岐の逆順合成の不具合**：
  隣接表を逆順に合成しており正しい合成と724/1101件食い違う（実測）。
  755分岐・756分岐だけ正しい順序（最も内側から合成）。唯一の呼び出し元は
  `ChunkTranslator.java:529`。修正は統括側（Muse01／ユーザー）の判断待ち。
- **775節の `experience_orb = 47` の古いリテラルは直していない**
  （Muse01／ユーザーの判断待ち）。
- `VanillaServerHost.stop()` で `IllegalThreadStateException: process hasn't exited`
  が出ることがある（全バージョン共通の既存の問題。直していない。
  実バニラ1.17の起動確認でも再現した）。
- `build-numbers.properties`／`versionedJars` への1.17追加はできない
  （このVMのツリー内にファイルが無いため。ローカルmainの同ファイル待ち）。
- Tile Entity Dataの757→756でNBTにidを付けられない（ベストエフォート）、
  `enableServerListing=true` の既定値は推論で未確認（コードに記録）。

## 755のときに見つかった罠（後続の担当へ）

- **`handleServerPlay` はハンドラ結果を763形式とみなす**（757のStage Bで
  見つけた実バグ。755でも同じ）：
  `handleServerPlay` は、ハンドラの結果を763形式とみなして759〜762向けに
  変換し直す。そのため `handleServerPlayFrom755` から直接756形式のパケットを
  返すと、IDが763の別パケットと誤読されて壊れる。実測で確認した誤読
  （756形式を763と誤読したもの）：
  Keep Alive 0x21→Hurt Animation、Ping 0x30→Open Window、
  Join Game 0x26→World Particles、Spawn 0x4B→World Border Warning Reach。
  Disconnect 0x1Aは763でも Disconnect のため誤読されないが委譲に統一。
  対策は、755→756へ変換したら `handleServerPlayFrom756` に委譲し、
  763形式で返して呼び出し側の変換に任せること。直接756形式で返す
  `handleServerPlay755To756` は作らないこと。新しいプロトコルを追加するときは、
  ハンドラが返す形式が呼び出し側の想定と一致しているか確認すること。
- **Respawn／Disconnect ガードの `&&`／`||` 優先順位バグ**（755 Stage B統合時に修正した本物のバグ）：
  `handleServerPlayUp` と `handleServerPlay765From764` のガードが
  `if (name == PacketName.PLAY_RESPAWN && is757() || is756() || is755())` と書かれていた。
  `&&` は `||` より強いため、756／755クライアントでは Respawn 以外の全パケットが落とされ
  （765側）、764側では全パケットが Disconnect 扱いで切断されていた（Disconnect側も同じ形が1か所）。
  756のStage Bで `|| is756()` を付け足したときに括弧を付け忘れたのが原因で、755担当が同じ形を踏襲したため
  755にも広がっていた。3か所とも `&& (is757() || is756() || is755())` に修正し、コメントに罠を記録した。
  回帰テスト2件を `ProxySession755Test` に追加
  （`keepAlive765Against755IsNotDroppedByRespawnGuard`／
  `keepAlive764Against755IsNotClosedByDisconnectGuard`）。
  他の `|| is755()` 74か所は `||` の連鎖か括弧付き三項演算子のため安全であることを
  grep で確認済み。新しいガードを書くときは括弧を付けること。
- **Respawn の copyMetadata**（757の§13.4の罠。755では踏んでいない）：
  SpawnInfo755／756 は `dataToKeep` を末尾に置いていたが、実レイアウトは
  1.17が {dimension NBT, worldName, seed, gameMode u8, previousGameMode u8,
  isDebug, isFlat, **copyMetadata bool**}（minecraft-data protocol.jsonで確認）。
  dataToKeep は766以降にしか無い。755では protocol.json で末尾のフィールドを
  実測してから書いたためこの罠を踏んでいない。
  新しいバージョンの Respawn を写すときは、minecraft-data の protocol.json
  で末尾のフィールドを実測すること。
- **774＋755ログイン修正**（757の§13.7.3の755版。Stage B で見つけた実バグ）：
  774クライアントが755サーバーに繋がると、プレイに入れなかった。
  `isServer763Like()` は761／762／763だけで、Login Success で「両方設定
  フェーズに入る」分岐に入り状態が変わらず、クライアントの Login
  Acknowledged が755サーバーに誤送されていた（755サーバーは 0x03 を
  play chat と誤読する）。`ProxySession.java` の3箇所の条件に
  `isServer755()`／`is755()` を追加した。範囲は755だけに絞った。
  759／760サーバーにも同じ隙間があるが、本タスクの範囲外として記録する。
- **設定フェーズなし**：755にも756にも設定フェーズは無い。
  `ClientJoinFlow` は stage の三項演算子に `|| clientProtocol == V1_17` が
  無いと、755が設定フェーズの stage を誤って取る。新しいプロトコルを
  追加するときは、stage の分岐のすべての組み合わせに漏れが無いか確認すること。
- **署名なしチャット**：1.17の chat は署名なし（{message string}のみ）。
  759の System Chat の type は **varint**（overlay bool は760から）。
  live の chat_type "chat" の decoration は `chat.type.text`
  （`chat.type.announcement` ではない）。"system" に decoration は無く、
  chat は空。1.17の serverbound packet_chat は {message string} のみ。
  依頼文の記述はデータで再確認すること。
- **pack.mcmeta は平文形式**：1.17サーバーjarのjavapで確認。
  `SharedConstants` は protocol 755・world 2724・pack リソース7／データ7。
  `PackMetadataSectionSerializer.fromJson` は `description` と `pack_format`
  （int）のみ読む。jar内24,130クラス中 `min_format`／`max_format` を含む
  クラスは0件、`pack_format` を含むクラスは1件のみ。
  正しい形式は plain int形式
  `{"pack":{"pack_format":7,"description":"DetergentMC resource pack"}}`。
  777の min/max 形式は1.17には存在しない（756の罠と同じ）。
  1.17クライアントでのパック受け入れの実機確認は未実施。
- **反転表の first-wins の罠**：`RegistryRemapper.invert()` と
  `invert_java` は最初に見つかった preimage を採用する。755では補正は全種0件で、
  invert＋補正＝名前の真値がそのまま成り立つ（756ではパーティクル1件・バイオーム26件の
  補正が必要だったが、755では不要）。verify では (a) `*_755_TO_756_*` 表＝名前の真値、
  (b) invert(順向き)＋補正＝名前の真値、の両方を確認する。
- **バイオーム表はワイヤ id 空間（176 件）で作ること**：`biomes-1.17.json` は81件で
  id が0〜175の非連番（1.18は61件・0〜60連番）。順向き表・反転表ともワイヤ id 空間の
  176 件（id 0〜175。隙間の id は平原（1））で作る。位置リストで作ると誤写像になる
  （755の生成時に書き込み前検証で発覚し作り直した）。
  根拠：実バニラ1.17のライブキャプチャで dimensionCodec の
  `minecraft:worldgen/biome` が81件・id 0〜175の非連番であることを確認済み。
- **Window Click の mode は両方 varint**：minecraft-data の「Window Click の mode は
  1.17で i8」は誤り。javap -c で両バージョンとも `writeEnum`（varint の enum 序数）を確認。
  mode は両方向とも varint のまま写す（変換器のjavadocに記録済み）。
- **`serverProtocolFor("1.17")` は755を返す**：Stage A で `"1.17"` → `V1_17` を追加済み。
  756のときはフォールバック（765を返す）だったが、755では正しい値が入った。
  推測で値を入れてはいけない、という罠は踏んでいない（実バニラjarの `version.json` で確認）。
- **`ClientAdapter` の三項演算子の置き換えミス**：先頭への追加にすること。
  置き換えると他のプロトコルの値（775の "84" など）が消える。
  三項演算子の編集は置き換えではなく先頭への追加にすること。
- **static 初期化子の 64KB 制限**：`RegistryRemapper` の `<clinit>` は
  755追加前で64,669バイト（残り866バイト）。755の表を外側の static
  初期化子に入れると `code too large` でコンパイルできない。
  755の展開済み表は `private static final class Maps755` にまとめた。
  754以降も同じホルダ方式にすること。
- **1.17のjarはfat jar**：`META-INF/MANIFEST.MF` の `Main-Class` は
  `net.minecraft.server.Main` で、`net.minecraft.data.Main` がjar内に直接
  入っている。1.18のjarは bundler形式（`Main-Class: net.minecraft.bundler.Main`）
  だが、1.17はbundler形式ではない。そのため757の前例（サーバーを一度起動
  してlibrariesを取得し、内側jar＋libraries全jarをclasspathにする段取り）は
  1.17では不要で、jarをそのままclasspathにして直接実行できる。
- **minecraft-dataの1.17のprotocolは `pc/1.17`**：`data/dataPaths.json` で、
  1.17の `protocol` は `pc/1.17` を指す（`pc/1.17.1` ではない）。
  `blocks`・`items`・`enchantments`・`biomes`・`effects`・`sounds`・
  `particles`・`entities` は `pc/1.17` を指す。`pc/1.17/protocol.json`
  （155,058バイト）と `pc/1.17.1/protocol.json`（155,791バイト）は内容が違う
  ため、1.17用にはpc/1.17のものを使うこと。
- **ViaVersion mapping-1.17.1.json は無い**：ViaVersionのMappingsリポジトリには
  `mapping-1.17.1.json` が無く、`mapping-1.17.json` がある。代替として
  `mapping-1.17.json` を `vv-1.17.json` として保存した。内容の照合：
  blockstates 20,342件・blocks 898件・items 1,100件・sounds 1,190件・
  particles 89件・entities 113件で、実バニラjarの報告書と全件一致。
- **755は756の土台の上に積む**：755（1.17）の対応は756の756↔757変換を流用する。
  ただし755↔756のチャンクは756↔757のチャンクとは別形式（16セクション＋
  bitMap＋バイオーム配列 vs 24セクション＋パレット＋光）のため、756の翻訳子を
  そのまま流用せず、755専用の翻訳子を書くこと。
- **portalCooldown は763から、seaLevel は769から**（777ノート §19.1の
  「768からある seaLevel」は誤り。756の SpawnInfo にどちらも無い）。
  **763（1.20）の Join Game の dimension は文字列**（varint 化は764から。
  763形式は759形式＋末尾 portalCooldown varint）。
  **chat_message の checksum は771（1.21.5）から**（1.21.4まで無し、
  1.21.5以降あり）。
- **1.17の Join Game の viewDistance=10**：実バニラ1.17のJoin Gameの
  viewDistanceは10（server.propertiesは10）。生バイトで確認済み。
  756ノートの「1.17.1 は viewDistance=4」とは違う。`enableServerListing=true` の既定値と
  同じく、推論で決めた値はコードに「未確認」と記録すること。
- **755分岐の mapItemReverse は正しい合成順序**：上向き（クライアント→765）のため、
  新しい隣接段（755→756）は最も内側（最初に適用）に入れること。下向きとは逆で、外側に巻くのは
  誤り。757／759／760／761／762 の分岐は外側に巻く形で作られており、隣接表を
  逆順に合成している既知の不具合がある（756 §12.5 の4）。755 分岐だけは正しい順序で
  合成すること（Check は正しい合成との全件一致 1,100 件＋往復 1,100 件＋真値アンカー 2 件で検証し、不一致 0 件）。

## Java 16（内部755サーバーを動かすとき）

1.17は **Java 16** で動く。Java 17／Java 21／Java 25 は内部サーバーには要らない。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.17は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.17` を指定すると、内部755サーバーが Java 16 で起動する。
`javaBinaryFor` には Java 16 必須リストは無く、Java 16 は既定分岐
（現在の `java.home` をそのまま使う）で、DetergentMC 自身も Java 17 で動くため、
追加は不要。ただしドライバ自体はJDK 17で動かす必要があった（core／protocolに
sealedクラスがありJDK 16ではコンパイル・実行不可）。そのため実バニラ1.17の
起動確認ではドライバをJDK 17＋`-Djava.home=$HOME/.tools/jdk-16.0.2+7` で起動し、
`javaBinaryFor("1.17")` が返すバイナリを
`/home/hatch/.tools/jdk-16.0.2+7/bin/java` にした（ドライバ出力 `JAVA-BIN=` で
確認済み）。本番コードの変更は無い。
Temurin 16.0.2+7 は `~/.tools/jdk-16.0.2+7` に導入済み（Adoptium公式のみ）。
バニラ1.17サーバーjarのSHA-1は
`0a269b5f2c5b93b1712d0f5dc43b6182b9ab254e`
（piston-meta。43,621,201バイト。`run/vanilla/server-1.17.jar`）。
バニラ1.17.1サーバーjarのSHA-1は
`a16d67e5807f57fc4e550299cf20226194497dc2`
（piston-meta。43,626,592バイト。`run/vanilla/server-1.17.1.jar`）。

## リソースパック（pack755）

pack_format はリソース7・データ7。`detergentmc-pack-1.17.zip`（20つ目）。
pack.mcmeta は平文 `{"pack":{"pack_format":7,"description":"DetergentMC resource pack"}}`
（上の「罠」を参照）。`resource-pack.url-755`／`sha1-755` で出し分ける。
フォールバックは `755→756→757→759→760→761→762→763`（pack756 は format 7。
755はツリー内最古のため古いフォールバックが存在せず、755と756はどちらも
pack_format 7のため落としても形式は変わらない）。
**755パックと756パックは内容が同一**（同じpack_format 7・同じデモアセット・
zipタイムスタンプ固定）のため **sha1が等しく、ファイル名のみ異なる**。
テストでは `assertEquals(built.sha1(), built756.sha1())` とし、757／759とは
異なることを確認している。

## Bedrock→755

- チャンクのワイヤ形式（実バニラ1.17.1の生キャプチャ＋javapで実測し、755でも同一であることを
  protocol.json＋javap で確認）：
  **16セクション**（bitMap付き）、**heightmap は名前付き・空名のNBT**、
  **バイオームは varint[1024]**、**BitStorage は non-straddling**
  （BPE 5のData Array長342）。757の「24セクション・セクションごとの
  バイオームパレット・光内包」とは違う。バージョンごとに実測で決める方針通り。
- 755正規化：755→756は恒等＋756連鎖で765に正規化する。エンティティ・
  アイテム・エフェクト（1ベース→0ベース）・パーティクル（barrier 2／light 3→
  block_marker 3、データ無しで読み飛ばし）・絵画（donkey_kong 25→29）も
  同じ正規化で扱う。
- 755固有の追加は2点だけ：Entity Destroy の単体varint読み（`parseRemoveEntity755`。
  755だけ `[entityId varint]` 1体。756の `[count][entityIds]` として読むと壊れるため）と
  Respawn連鎖755→756→759。
- 今回見つけて直したバグはない（`airBlocks()` の二重変換バグ修正は756のときに
  既に入っていることを確認。Javaの空気＝状態0で埋める）。
- 副次的知見：設定パケットは Join Game 受信後に送ること。Login Success
  直後に送ると1.17サーバーが切断する（ログイン状態のままのため）。
  756と同じ実測。

## 統合テスト（Fake755Client＋4セル）

`detergentmc-core/src/test/java/io/detergentmc/core/vanilla/ProxyIntegrationTest.java`
に `Fake755Client`＋4セルを追加済み（Stage E。`fake755ClientJoinsRealVanilla117ThroughProxy`（内部755）、
`fake755ClientJoinsRealVanilla1171ThroughProxy`（内部756）、
`fake755ClientJoinsRealVanilla118ThroughProxy`（内部757）、
`fake756ClientJoinsRealVanilla117ThroughProxy`（内部755）。javac通過）。
**プロキシ経由の実行はこのVMではできなかったため、「統合テストが通った」とは言えない。**

| セル | 内容 | 結果 |
| --- | --- | --- |
| (a) | 偽755クライアント→実バニラ1.17（内部755）、プロキシ経由 | **未実施（環境制約）**。プロキシは25571でlisten開始したが、Pythonクライアントは正規パケット0件でEOF。生ソケットでサンドボックスのポリシー文 `muse: Other TCP connections is turned off for this assistant. To...`（193バイト）を受信。JVM→バニラのTCPがインターセプトされた。バニラ（25570、Done 91.989s）のログに接続なし。`run/capture-755/result-a-proxy.json`・`proxy-a.log` |
| (b) | 偽755クライアント→実バニラ1.17.1（内部756）、プロキシ経由 | **未実施（環境制約）**。(a) と同一の失敗モードを確認。プロキシ25573でlisten、クライアント即EOF・0件。バニラ（25572、Done 102.695s）のログにjoin 0件。`run/capture-755/result-b-proxy.json`・`proxy-b.log` |
| (c) | 偽755クライアント→実バニラ1.18（内部757）、プロキシ経由 | 未試行（(a)(b)で失敗原因が共通の環境制約と確定したため。777・757・756と同じ段取り） |
| (d) | 偽756クライアント→実バニラ1.17（内部755）、プロキシ経由 | 未試行（同上） |

直接接続（プロキシ迂回）の結果は実施済み：(a) 偽755→実1.17は**通過**（Join Game 0x26＝31,555バイト、
Chunk Data 0x22＝462パケット・6,592,432バイト、チャット到達（サーバーログ `<Steve> hello from 1.17`）、
Keep Alive応答2回、移動後も接続維持、圧縮しきい値256。ポート25564、Done 106.324s。
`run/capture-755/result-a-direct.json`）、(b) 偽755→実1.17.1・(c) 偽755→実1.18・(d) 偽756→実1.17は
いずれも想定通りの切断（`multiplayer.disconnect.incompatible` with "1.17.1"／"1.18"／"1.17"。
ポート25565／25566／25567、Done 86.577s／273.201s／132.811s）。
なお Client Information は Login Success 直後ではなく **Join Game 受信後に送る
こと**（756 §15.2の罠を踏襲。1.18では Login Success 直後に送るとサーバーが
デコード例外で切断することを実測済み。1.17での切断再現は未確認）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.17.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  新規755テスト **26件全通過**（Stage A）：`ProtocolVersion755Test`（3件）、
  `ClientAdapter755Test`（7件）、`RegistryRemapper755Test`（16件）。
  新規・書き換え755テスト **61件全通過**（Stage B）：`PlayTranslator755Test`（10件）、
  `ChunkTranslator755Test`（13件）、`ProxySession755Test`（11件。統合時の回帰2件を含む）、
  `ClientAdapter755Test`（12件。受け入れ側へ書き換え）、`ClientJoinFlow755Test`（5件）、
  `ClientProxy755Test`（4件）、`V117AdapterTest`（6件）。
  回帰：`PlayTranslator756Test` 17件、`ChunkTranslator756Test` 14件、
  `ProxySession756Test` 8件も全通過。
  `detergentmc-adapter-v1_17`：`V117AdapterTest` 6件中6件通過。
  core：`VanillaServerHostTest` 11件中11件通過（1.17アサーション含む）。
- 全体回帰（detergentmc-protocol＋adapter-v1_17、Stage B統合時）：
  **2,467件通過・29件失敗**。失敗は全て環境制約で、755が原因の
  本物の回帰は **0件**：
  - ResourcePackServerのローカルHTTP -1が19件（JVMのTCP遮断。HEADベースラインと同種）。
  - ソケットタイムアウト8件（ClientProxyTest 5件、ClientProxyAuthTest 3件。JVMのTCP遮断）。
  - SessionServerClientのbase URL未設定2件。
- `verify-755-tables.py` は **43件中43件通過**を維持
  （A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）。
- Stage D：`ResourcePackBuilder755Test`（3件）、`ResourcePackServer755Test`（2件）、
  `ResourcePackMode755Test`（6件）、コア配線4件全通過（新規含む）。
  新規755テスト15件の内訳は **13通過・2失敗**（失敗2件はともにHTTP GET -1のみで、
  それ以前のpack755生成・URL・sha1・packForProtocol・hostResourcePack・ClientProxy配線の
  アサーションはすべて通過）。
  全スイート（Stage D対象10クラス、165件）：144件通過・21件失敗。失敗21件はすべて環境制約
  （HTTP GET -1が14件、ソケットnullが7件）。
- Bedrock：`BedrockChunkTranslatorTest` に755件5件、`JavaBackendConnection755Test`
  新規36件。**755テスト：50通過／7失敗**（担当テスト57件中。失敗は全てソケット系7件で、
  JVMループバックTCP遮断の環境制約と切り分け済み。チャンク755の5件は全通過、
  接続755の非ソケット29件は全通過）。
  全bedrockスイート：391件中331件通過、58件失敗、2件中断。58件は全てソケット系テスト
  （759〜775・756・757・755のログイン／チャット／スポーン等、同じ
  「偽サーバーがacceptしない」モード）で、非ソケットの失敗は0件。回帰なし。
- 内部サーバー：`DatapackGeneratorTest` 14件＋`VanillaServerHostTest` 11件＝
  25件全通過（新規 `packFormatFor117Is7` を含む）。
- 実バニラ1.17の起動確認：`VanillaServerHost` 経由で実バニラ1.17を起動し、
  `Done (100.329s)!` まで確認。データパックの `Error reading pack metadata` は0件。
- ライブキャプチャ（偽クライアント→実バニラ1.17／1.17.1、プロキシ迂回）：
  Join Game 31,555バイト（1.17）／31,555バイト（1.17.1）。両 Join Game の
  バイト比較：差分は9バイトのみ（entityId 1バイト＋hashedSeed 8バイト）。
- Gradleテストは未実施（Gradleが無いため `./gradlew` は実行していない。
  上記の件数はJUnitスタブ＋リフレクション呼び出し／JUnit 5.10.1実jar＋
  自作ランナーによる実行）。
