# 1.17.1クライアント → 1.18系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.17.1クライアント（プロトコル756）は、内部プロトコルが
756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775の19プロトコルのサーバーを対象にします。
ただし **758（1.18.2）は未対応**で、このツリーに `V1_18_2` はありません
（他人の作業中のため飛ばした。758が終わったら758↔757の合成表をそのときに作る）。
776（26.2）もこのツリーにありません。
内部756のときは素通し（リレー）、内部757のときは756↔757の直接変換、
それ以外は757を経由した連鎖変換です。**756は757から simulation_distance
（0x57）を1パケットだけ引いたシム（shim）**です。
757とのパケットIDの違いは**変更16件・新規0件・削除1件**で、
レジストリ差分はブロック状態±0件・ブロック±0件・アイテム+1件・
エンティティ±0件・音+13件・パーティクル-1件・
バイオーム-20件（改名11・削除26・新規6）です。
メニュー24件・属性13件・エンチャント38件・ブロックエンティティ34件・
エフェクト32件は順序も同一で変化なし。7種（ブロック状態・ブロック・
アイテム・エンティティ・音・パーティクル・メニュー）に改名・削除はありません
（バイオームとパーティクルの統合を除く）。

1.17.1は**Java 16**で動き、設定フェーズがありません（764で追加）。
レジストリは Join Game の dimensionCodec NBT で届きます（757と同じ方式）。
pack_format はリソース7・データ7。アイテムはNBT（データコンポーネントではない）。
1.17.1はプロトコル756唯一のバージョンです（1.17はプロトコル755で
このツリーに無く、`serverProtocolFor("1.17")` は765にフォールバックします。
推測で755の値を入れてはいけません）。

756の仕組みは `docs/PROXY-1.18.md`（757）、`docs/PROXY-1.19.md`（759）、
`docs/PROXY-1.19.1.md`（760）、`docs/PROXY-1.19.3.md`（761）、
`docs/PROXY-1.19.4.md`（762）、`docs/PROXY-1.20.1.md`（763）、
逆向きの考え方は `docs/PROXY-REVERSE.md` を参照してください。
なお756→757の差分は「758を飛ばして757を隣接にした直接の差分」です
（777のときの776問題・757のときの758問題と同じ構図で、同じ方針 (b) を取った。
758が終わったら758↔757の合成表をそのときに作る）。

全361通り（クライアント756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775 × サーバー756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775）の対応マトリクスは `docs/VERSIONS.md` の「1.17.1対応：
全361通りの対応マトリクス」を参照してください。

## 対応表（756クライアント × サーバー、逆向きも）

| クライアント | サーバー | 経路 | 結果 |
| --- | --- | --- | --- |
| 756（1.17.1） | 756（1.17.1） | 素通し（リレー） | 参加できる |
| 756（1.17.1） | 757（1.18／1.18.1） | 756↔757の直接変換 | 参加できる |
| 756（1.17.1） | 759〜775（1.19〜26.1） | 756→757→759→…の連鎖変換 | **参加できない**（サーバー→クライアントの Join Game／Respawn が落とされるため。759〜762は763へ直されて `handleServerJoinGame763` の is756() 分岐で落とされ、763〜775も各 Join Game ハンドラの is756() 分岐で落とされる。バグではない） |
| 757（1.18／1.18.1） | 756（1.17.1） | 757↔756の直接変換 | 参加できる |
| 759（1.19） | 756（1.17.1） | 759→757→756の連鎖変換 | 参加できるはず（統合テスト未実施のため未確認） |
| 760／761／762（1.19.1〜1.19.4） | 756（1.17.1） | 760／761／762→759→757→756の連鎖変換 | 参加できるはず（統合テスト未実施のため未確認） |
| 763〜765（1.20／1.20.1〜1.20.4） | 756（1.17.1） | 763〜765→…→757→756の連鎖変換 | Join Game は通る。Respawn も通る（`handleServerPlayFrom763` の Respawn 分岐の落としリストに763〜765は無い。コードで確認） |
| 766〜775（1.20.5〜26.1） | 756（1.17.1） | 766〜775→…→757→756の連鎖変換 | Join Game は通る。Respawn は下流（`handleServerPlayFrom763` の Respawn 分岐）で落とされる（774は `ProxySession756Test.join774Against756ConvertsJoinGameButDropsRespawn` で確認） |
| 774（1.21.11） | 756（1.17.1） | 三段変換756→757→759→763→774 | Join Game は通るが **Respawn は落とされる**（死亡後のリスポーンで確かめること） |

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。756用・757用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の19つを出す（758用・776用は無い） |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 757↔756の差分（要点）

minecraft-data（`pc/1.17.1/protocol.json`・`pc/1.18/protocol.json`）と
ViaVersion（`mapping-1.17.json`／`mapping-1.18.json`）と
Minecraft Wiki の Packets ページ（1.17.1、protocol 756、2021-09-29 の改訂版
2772712）の三重照合で確認。不一致0件。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-756.py`・
`verify-756-packet-ids.py`・`verify-756-wiki.py`・`insert-756-java.py`。
件数の定義：変更＝両方にあってIDが違うもの、新規＝756にあって757の
`PacketIds` に無いもの、削除＝757にあって756に無いもの。
写像は `detergentmc-protocol/build/remap-data/packet-ids-756.json`（272件）。
`PacketIds.java` のエントリ総数は272件のまま（新規エントリ0件）。
id756>=0 は160件（HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 103件＋
PLAY サーバー行き 48件。CONFIGURATION は0件）。

| 分野 | 757↔756の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ・クライアント行き） | 変更16件・新規0件・削除1件。Join Game 0x26両方、Chunk Data 0x22両方、Keep Alive 0x21両方、Ping 0x30両方、Respawn 0x3d両方、Player Info 0x36両方、Disconnect 0x1A両方。Update Time は 0x59→0x58、Tags は 0x67→0x66 | `PacketIds` の756列で番号を付け替える。変更16件はすべて0x57の削除による-1ずれ（0x58〜0x68） |
| パケットID（プレイ・サーバー行き） | 変更0件・新規0件・削除0件。Chat（サーバー行き）0x03両方、Keep Alive 応答 0x0F両方、Window Click 0x08両方、Set Creative Slot 0x28両方 | そのまま通す |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一（Login Start 0x00、Login Success 0x02、ハンドシェイク 0x00） | そのまま通す |
| 設定フェーズ | **756にも757にも無い**（764で追加） | プロキシが合成・消費する（757と同じ。`docs/PROXY-1.19.3.md` の「設定フェーズなしクライアントの扱い方」を参照） |
| Join Game | **simulationDistance の有無だけ**。下の「Join Game／Respawn codec」を参照 | `PlayTranslator.translateJoinGame756To757`／`757To756` で変換する |
| Respawn | **フィールド同一**（dimension element の置き換えのみ） | `PlayTranslator.translateRespawn756To757`／`757To756` で変換する |
| Join Game codec の中身 | **dimension_type は4件・同一順序**（overworld 0、overworld_caves 1、the_nether 2、the_end 3）。**バイオームは756が81件↔757が61件**（756は id 0〜175 の非連番、757は id 0〜60 の連番）。`minecraft:chat_type` はどちらにも無い | codec は作り直す（`translateDimensionCodec756To757`／`757To756`）。`minecraft:damage_type` レジストリは1.17・1.18のどちらにも無い（1.19.4で追加） |
| チャット | **756も757も署名なし `chat`**（サーバー行きは {message string}のみ、クライアント行き 0x0F）。1.19は System Chat／Player Chat 等に分離（署名付き） | 756↔757は素通し。759以上との間は専用の翻訳子で変換する（下の「チャット」を参照）。署名チェーンの検証は境界を越えられない |
| Spawn Entity | 756も757も `spawn_entity`（0x00）／`spawn_entity_living`（0x02）／`spawn_entity_painting`（0x03）が別パケット | そのまま通す（759以上との間は757の翻訳子を経由する） |
| Player Info | 756と757で同一の旧式（action varint。add_player は crypto オプション付き、UUID は無い） | 暗号化情報の補完／除去だけする（757の翻訳子を経由する） |
| Update Time | IDだけ変わる（757は 0x59、756は 0x58）。ペイロード同一 | 変換器なし。番号の付け替えだけ |
| Set Held Item／Vehicle Move／Held Item Change | ペイロード同一（IDも同一） | 素通し |
| Sound／Entity Sound／Named Sound | ペイロード同一（IDだけ 0x5D→0x5C、0x5C→0x5B のずれ） | IDだけ付け替える |
| World Particles | ペイロード同一（0x24両方） | 素通し |
| Entity Effect／Remove Entity Effect | ペイロード同一（effectId は両方 i8。1.19の varint 化とは違う） | IDだけ付け替える（Entity Effect は 0x65→0x64） |
| Multi Block Change | ペイロード同一（レコードは両方 varlong。1.19の varint 化とは違う） | 素通し |
| Trade List | ペイロード同一（inputItem2 は両方 `[option slot]`。1.19の `slot` 化とは違う） | 素通し |
| Block Dig／Block Place／Use Item | ペイロード同一（sequence は両方無し。759で追加） | 素通し |
| Set Beacon Effect | ペイロード同一（両方 {primary varint, secondary varint}） | 素通し |
| Update Structure Block／World Border | seed／speed は両方 varlong | 素通し |
| Login Start | 両方 {username} のみ | そのまま通す |
| Enchant Item | 両方 {windowId i8, enchantment i8}（IDだけ変わる。0x07両方） | IDだけ付け替える。i8に収まらないものは落とす |
| Chunk Data | **形式が違う**（下の「チャンク」を参照）。IDは 0x22両方 | `ChunkTranslator` の756節で変換する |
| スロット | 756／757で同一（`[present bool][id varint][count i8][optionalNbt]`、NBT時代でKIND表なし） | そのまま通す |
| Explosion／Block Entity Data／Update Attributes | 756／757で同一のため変換器なし（意図的な省略） | そのまま通す。ただし Block Entity Data は素通しではなく `translateTileEntityData756To757` を使う（action u8→varint、NBTの id／x／y／z 剥がし） |
| ブロック状態 | **756=20,342件、757=20,342件。差分0**（文字列は完全一致・同一順序） | 変換なし（identity） |
| ブロック | **756=898件、757=898件。差分0**（同一順序） | 変換なし（identity） |
| アイテム | **756=1,100件、757=1,101件。差分+1**（music_disc_otherside、757 id 1027） | 対応表で変換する。代替：music_disc_otherside→music_disc_13 |
| エンティティ | **756=113件、757=113件。差分0** | 変換なし（identity） |
| 音 | **756=1,190件、757=1,203件。差分+13**（block.growing_plant.crop、item.bundle.* 3件、music.overworld.* 8件、music_disc.otherside） | 対応表で変換する。代替は下の「代替表」を参照 |
| パーティクル | **756=89件、757=88件。差分-1**（1.17の `barrier`（2）と `light`（3）が1.18の `block_marker`（3）に統合） | 対応表で変換する。代替：block_marker→barrier |
| メニュー | 24件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 属性 | 13件で同じ顔ぶれ・同じ順番（文字列キー。数値表は不要） | 変換なし（identity） |
| エンチャント | **756=38件、757=38件。差分0**（同一順序。757ノートの「1.18は39件」は誤りで、実測は両方38件） | 変換なし（identity） |
| ブロックエンティティ種別 | **756=34件、757=34件。差分0**（同一順序） | 変換なし（identity） |
| バイオーム | **756=81件、757=61件。改名11・削除26・新規6** | 対応表で変換する。代替は下の「代替表」を参照 |
| エフェクト | **756=32件、757=32件。差分0**（同一順序） | 変換なし（identity） |
| レシピシリアライザ | 21件で同じ | 変換なし |
| ダメージ種別 | **756・757のどちらにも無い**（1.19.4／762で追加） | 対応表は42件の恒等写像プレースホルダ。ワイヤ上の呼び出し元は無い（757と同じ） |
| データコンポーネント種別 | 無し（1.20.5で追加） | N/A |
| 絵画 | 756も757も `minecraft:motive` 26件で同じ | 変換なし（identity） |

## パケットIDの対応表（757↔756、確認したものだけ）

出典は `detergentmc-protocol/build/remap-data/packet-ids-756.json`（272件）。
変更16件はすべて PLAY／クライアント行きで、0x57 `simulation_distance` の
削除による-1ずれ（757-ID 0x58〜0x68 が -1）。0x00〜0x56（87件）は変わらない。
サーバー行き・LOGIN・CONFIGURATION・HANDSHAKINGは変更なし。
ここに無いパケットのIDは未確認のため書かない。

### 変更16件（757 → 756）

| パケット | 757 | 756 |
| --- | --- | --- |
| `PLAY_SET_TITLE_SUBTITLE` | 0x58（88） | 0x57（87） |
| `PLAY_SET_TITLE_TEXT` | 0x5A（90） | 0x59（89） |
| `PLAY_SET_TITLE_TIME` | 0x5B（91） | 0x5A（90） |
| `PLAY_UPDATE_TIME` | 0x59（89） | 0x58（88） |
| `PLAY_PLAYERLIST_HEADER` | 0x5F（95） | 0x5E（94） |
| `PLAY_ENTITY_TELEPORT` | 0x62（98） | 0x61（97） |
| `PLAY_ENTITY_EFFECT` | 0x65（101） | 0x64（100） |
| `PLAY_ENTITY_SOUND_EFFECT` | 0x5C（92） | 0x5B（91） |
| `PLAY_SOUND_EFFECT` | 0x5D（93） | 0x5C（92） |
| `PLAY_STOP_SOUND` | 0x5E（94） | 0x5D（93） |
| `PLAY_NBT_QUERY_RESPONSE` | 0x60（96） | 0x5F（95） |
| `PLAY_COLLECT` | 0x61（97） | 0x60（96） |
| `PLAY_ADVANCEMENTS` | 0x63（99） | 0x62（98） |
| `PLAY_ENTITY_UPDATE_ATTRIBUTES` | 0x64（100） | 0x63（99） |
| `PLAY_DECLARE_RECIPES` | 0x66（102） | 0x65（101） |
| `PLAY_TAGS` | 0x67（103） | 0x66（102） |

### 削除1件

| パケット | 757 | 756 |
| --- | --- | --- |
| `PLAY_SIMULATION_DISTANCE` | 0x57（87） | -1（756に無い。1.18で追加された描画距離通知） |

### 主要パケット（両方で同一のもの、確認済み）

| パケット | 757 | 756 |
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
| Login Start | 0x00 | 0x00 |
| Login Success | 0x02 | 0x02 |
| Login Set Compression | 0x03 | 0x03 |

## Join Game／Respawn codec

情報源：公式 server_mappings.txt（1.17.1 sha1 `f6cae1c5…`、1.18 sha1
`a8fe854e…`、両方 sha1 照合済み）と minecraft-data protocol.json
（mc-1.17.1／mc-1.18-protocol.json）。
実キャプチャ：実1.17.1 Join Game 31,555バイト／実1.18 Join Game 23,986バイト。
756→757→756の往復はバイト完全一致（31,555→23,423→31,555、差分0）。

**Join Game（ClientboundLoginPacket）**：変更は `simulationDistance` の
有無だけ。1.18は `viewDistance` の後に `simulationDistance` を追加する。
756→757の simulationDistance は viewDistance と同じ値にする
（実バニラ1.18も sim＝view＝10を送る）。
`dimension` は両方とも NBT 値。`lastDeathLocation` はどちらにも無い。
- 変わらないもの：playerId、hardcore、gameType、previousGameType
  （両方に存在。i8 で -1 が無し。26.3の Optional 化とは違う）、levels、
  seed、maxPlayers、chunkRadius、reducedDebugInfo、showDeathScreen、
  isDebug、isFlat。
- `dimensionCodec`（Join Game 内の NBT）：`minecraft:dimension_type` は
  4件（overworld 0、overworld_caves 1、the_nether 2、the_end 3。1.18と
  同一順序）。`minecraft:worldgen/biome` は756が81件（id 0〜175の非連番。
  `biomes-1.17.json` の（id, name）と完全一致）↔757が61件（id 0〜60連番）。
  `minecraft:chat_type` はどちらにも無い。
- dimension_type の element は overworld／overworld_caves で違う
  （logical_height 256→384、min_y 0→-64）ため、受信側の既定値で置き換える。
  バイオームは756 elementの depth・scale を756→757で落とし、757→756で
  実バニラ1.17.1の値を足す（順序も再現）。それ以外の値の違い
  （savannaのtemperature等）はそのまま引き継ぐ。
- **罠（実バグとして修正済み）**：wooded_hills／wooded_mountainsの衝突。
  756 id 18（wooded_hills）と34（wooded_mountains）が両方757 id 20
  （windswept_forest）に写るが、逆写像は34を指す。先勝ちだと18のelementが残り、
  往復でwooded_mountainsがwooded_hillsのelementに化けた（7バイト短くなった）。
  **逆写像が指す正規の元を優先する規則**に変えて解決した。
  756→757の衝突20組のうち19組は先勝ちで正規だが、id 20だけ例外。
  757→756の衝突4組は先勝ちでも正規だが同じ規則に統一した。
  埋め表も両方向とも**実バニラelementの全件表（61件／81件）**に変えた。
  入力が欠けていても出力は常に完全なcodecになる。
  `Map.of` は10件制限のため `Map.ofEntries` を使用すること。

**Respawn（ClientboundRespawnPacket）**：フィールド同一。dimension elementの
置き換えのみ。copyMetadataは末尾で両方同一、dataToKeepはどちらにも無い
（757の§13.4の罠を踏まないことをprotocol.jsonで確認）。
Respawn の previousGameMode は u8（Join Game の i8 と違う）。
「none」の符号は不明のため範囲チェックせずそのまま写す。

**Registry Data パケット**：1.17.1／1.18には存在しない（設定フェーズは
1.20.2／764で追加）。レジストリは Join Game の dimensionCodec NBT で
送られる。

## チャンク（756⇔757）

情報源：protocol.json＋実バニラ1.17.1生キャプチャ
（チャンク-21,-17、13,449バイト。テストリソース
`cap756-chunk-21-17.bin`）＋両jarのjavap。実キャプチャの往復は
バイト完全一致。

- **756**：16セクション（y 0〜15）＋bitMap＋heightmaps NBT＋
  **バイオーム int[1024]**＋光なし。
- **757**：24セクション（y -4〜19）＋セクションごとのバイオームパレット＋
  光内包（trustEdges＋マスク＋配列）。
- **BitStorageは両方ともnon-straddling**（BPE 5のData Array長は342。
  straddling式だと320）。756節はnon-straddlingで実装した。
  757節のstraddling前提は直していない（判断待ち。下の「対象外」を参照）。
- バイオームは756配列の [64s, 64s+64) がセクションsに対応
  （javapで `ChunkBiomeContainer` のindex式を確認）。
  bitMapに無いセクションのバイオームも配列の値をパレットに直す。
  範囲外（y -4〜-1・16〜19）は情報が無いためplains補完のまま。
- ブロックエンティティ：756は名前付きNBTにid／x／y／zを含む
  （生キャプチャで確認）。757はpackedXZ／y／type＋id等なしNBT。
  Tile Entity Dataの757→756でNBTにidを付けられない（パケットに種別が
  無いため。ベストエフォートでx／y／zのみ付与）。
- Update Lightのマスクはビットi＝セクション(i-1)。1.17は18ビット、
  1.18は26ビット。マスクは±4ずらす。
- メタデータ型番号は1.17／1.18で同一（0〜18。slot=6、ブロック状態=13、
  粒子=15）。757節の1.19番号付けは使えない（判断待ちのまま）。
- 757→756のChunk Dataは**光を落とす**（光抽出の配線なし。下の「対象外」を参照）。
  756→757のChunk Dataは光を合成しない（756に光が無いため。空の光を送る）。

## チャット（756は署名なし）

- **Chat（クライアント行き 0x0F／サーバー行き 0x03）**：1.17.1も1.18も
  署名なしの `chat`（サーバー行きは {message string}のみ）。
  756↔757は素通し。1.19は System Chat／Player Chat／Chat Message／
  Chat Command に分離（署名付き）。
- 756→759：756→757は素通しで、757→759の翻訳子が Chat Message
  （`translateChat757ToChatMessage`）か Chat Command
  （`translateChat757ToChatCommand`）へ変換する。チャット署名は現在時刻・
  salt 0・空署名・signedPreview=false で補完する（757と同じ簡略化）。
- 759→756：System Chat は chat へ変換する（759の System Chat の type は
  **varint**。overlay bool は760から。live の chat_type "chat" の
  decoration は `chat.type.text`）。Player Chat は落とす（署名付きのため）。
  **つまり（仮に756クライアントが759サーバーに参加できた場合）他プレイヤーの
  チャット（Player Chat）は表示されない**。サーバーからのお知らせ
  （System Chat）は表示される。なお現状はサーバー→クライアントの Join Game が
  落とされるため756クライアントは759サーバーに参加できない（上の対応表を参照）。
  実機での確認は未実施。
- Chat Preview／Should Display Chat Preview／Server Data／
  Chat Preview To Server は756には無いため落とす。
- **署名チェーンの検証**：境界を越えると署名チェーンはつながらない。
  正直な制限として残す。

## 代替表（757にしかないものを756でどう見せるか）

`RegistryTables756.java`（50表）と `generate-remap-tables.py` の756対応で
作ったもの。改名はバイオーム11件のみ、削除はバイオーム26件とパーティクル
統合1件のみのため、代替はすべて「757の新規を756の既存に落とす」方向。
反転表は名前の真値で作る（`invert()` の first-wins では食い違うため、
パーティクル1件・バイオーム26件の補正 `PARTICLE_UP_756_CORRECTIONS`
（`[[3, 3]]`）・`BIOME_UP_756_CORRECTIONS` が必須。
756ではブロック状態・音の補正は0件。757ではブロック状態110件・音20件が
必要だったが、756では不要）。

- アイテム：music_disc_otherside→music_disc_13（唯一の新規アイテム）。
- 音（13件）：block.growing_plant.crop→block.crop.break、
  item.bundle.drop_contents／insert／remove_one→entity.item.pickup、
  8× music.overworld.*→music.game（1.17には `music.overworld.*` が無い）、
  music_disc.otherside→music_disc.13（ドット形式。1.17 id 617）。
- パーティクル：block_marker→barrier（統合の片方。もう片方 `light` は
  反転表の補正で対応）。
- バイオーム：改名11件は名前の対応（下の一覧）。新規6件は地形の類似性による
  best-effort の代替（meadow→plains、grove→snowy_taiga、
  snowy_slopes→snowy_tundra、frozen_peaks→snowy_mountains、
  jagged_peaks→mountains、stony_peaks→mountains）。
  削除26件は `REVERSE_BIOME_FALLBACK_756` で既定の類似バイオームへ
  （例：mountains→windswept_hills、desert_hills→desert、jungle_hills→jungle）。
  範囲外のバイオームだけ plains に落とす。

### バイオームの改名11件（757 → 756）

出典は Minecraft Wiki「Java Edition 1.18」の "Some biomes have been renamed"
（2026-11-16取得）。順向きは757の名前→756の名前。

| 757（1.18）の名前 | 756（1.17.1）の名前 |
| --- | --- |
| snowy_plains | snowy_tundra |
| old_growth_birch_forest | tall_birch_forest |
| old_growth_pine_taiga | giant_tree_taiga |
| old_growth_spruce_taiga | giant_spruce_taiga |
| sparse_jungle | jungle_edge |
| stony_shore | stone_shore |
| windswept_hills | mountains |
| windswept_forest | wooded_mountains |
| windswept_gravelly_hills | gravelly_mountains |
| windswept_savanna | shattered_savanna |
| wooded_badlands | wooded_badlands_plateau |

## Client Information（0x05両方）

- 756の `disableTextFiltering` →757の `enableTextFiltering`（**反転**）。
- `enableServerListing` は757→756では落とし、756→757では **trueで補完**する。
  **この既定値は推論による選択で未確認**（実バニラ1.18クライアントの送信値は
  未確認）。実機での確認が必要。
- 763→756の Client Information は素通し（763→756変換器なし。
  末尾フィールド差分は未対応）。

## 接続の流れ

### 756クライアント → 内部756サーバー

1. ハンドシェイク（756、状態2）。
2. Login Start（名前のみ）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x26、codec 付き）。
5. Spawn Position（0x4B）。チャンク（0x22）・Player Info（0x36）などが届く。

### 756クライアント → 内部757サーバー

1. ハンドシェイク（756、状態2）。
2. Login Start（名前のみ）。プロキシが757形式（名前のみ）に直して送る。
   ペイロードは同一のため素通しに近い。
3. Login Success。
4. すぐプレイ状態へ。Join Game（756形式 0x26）を757形式（0x26）に変換する。
   simulationDistance を viewDistance と同じ値で補い、codec を作り直す
   （バイオーム81件→61件、dimension_type の element を1.18式にする）。
5. Spawn Position（0x4B両方）。リソースパックは756形式で送る（`pack756`、
   無ければ `pack757`、無ければ `pack759`、無ければ `pack760`、無ければ
   `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 757クライアント → 内部756サーバー

1. ハンドシェイク（757、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。756サーバーの Join Game（0x26）を757形式（0x26）に
   変換する。simulationDistance を補い、codec を作り直す（バイオーム61件→
   81件、dimension_type の element を1.17.1式にする）。
5. Spawn Position（0x4B両方）。

### 756サーバー → 774クライアント

- Join Game は通る（三段変換 756→757→759→763→774で Join Game。
  `ProxySession756Test` で確認）。
- Respawn は落とされる（下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。未実施）。

## プレイ中の翻訳

- **チャンク**：ブロック状態（756=20,342／757=20,342で同一）・バイオーム
  （756=81／757=61）・ブロックエンティティ種別（34件で同一）を付け替える。
  756のバイオーム配列 [64s, 64s+64) を757のセクションごとのパレットに直す。
  757→756では光を落とす。
- **スロット・ウィンドウ**：アイテムID（756=1,100／757=1,101／759=1,152）を
  付け替える。Window Click（756=0x08／757=0x08／759=0x0A）・
  Set Creative Slot（756=0x28／757=0x28／759=0x2A）は756⇔757方向では
  変換する（落とさない）。**763→756方向は落とすまま**（未実装）。
- **エンティティ**：エンティティ種別は756=113／757=113で同一のため付け替えなし。
  Spawn Entity／Living／Painting（0x00／0x02／0x03）は両方で同一のため素通し。
- **音・パーティクル**：音（756=1,190／757=1,203／759=1,321）・パーティクル
  （756=89／757=88／759=93）の番号を付け替える。1.19の Sound 系パケットの
  末尾の seed i64 は756向けに落とす（757の翻訳子を経由する）。
- **位置・Server Data・Trade List**：上の表の通り。Server Data は756向けに落とす。
- **Client Information**：756⇔757は反転＋補完で変換する。763→756は素通し。

## 送らない・落とすパケット（756向け）

- simulation_distance（756に無いため。変換器なしでProxySessionがIDで落とす）
- Player Chat（759→756。署名付きのため変換できない）
- server_data、should_display_chat_preview、chat_preview、chat_preview_to_server（756に無いため。変換器なしでProxySessionがIDで落とす）
- 763〜775→756の Join Game／Respawn（未実施のため落とす。下の「対象外」を参照）
- 756サーバー→774クライアントの Respawn（下流で落とす。未実施）
- Window Click／Set Creative Slot（763→756方向。未実装のため落とす）
- 757→756のChunk Dataの光（光抽出の配線なし。チャンク自体は届くが光が無い）
- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応（既存の制限と同じ）
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす
- 756に無いアイテム・音・パーティクル・バイオーム：代替表で既存のものに落とす
  （music_disc_otherside→music_disc_13、block_marker→barrier、
  新規バイオーム6件→類似バイオーム）。パケットごと落とすものは無い

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全バージョン共通。手順書は
  `docs/PLAYTEST-1.17.1.md`。偽クライアント＋本物のバニラでの Join Game
  キャプチャ（31,555バイト／23,986バイト）と実バニラ1.17.1の起動確認
  （`Done (115.618s)!`、pack metadataエラー0件）は済み。
  プロキシ経由の統合テスト（Fake756Client）は Stage E で実施中。
- **763〜775→756の Join Game／Respawn は落とすまま**：763形式は759形式＋
  portalCooldown で変換自体は容易だが、763のコーデックのバイオーム順が
  cherry_grove の1.20プロトコルIDに依存し未測定
  （minecraft-data の biomes.json はアルファベット順でプロトコル順ではない。
  1.20のデータジェネレータ実行はJVMのTCP遮断で不可）。
  764〜775は Join Game の dimension をレジストリIDで送り、dimension_type
  レジストリは設定フェーズで別送されるが ProxySession が保持していない。
  777の §21.5と同じ種類の残課題。
- **756サーバー→774クライアントの Respawn は落とされる**：Join Game は
  通るが Respawn は下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。
- **763〜767の内部サーバー＋756クライアントの Join Game／Respawn は引き続き
  落とされる**（777の §21.5と同じ種類）。
- **759／760サーバー＋766以上クライアントのログイン合成ギャップ**：
  756だけ直した（下の「罠」を参照）。759／760サーバーにも同じ隙間があるが
  本タスクの範囲外。
- **Window Click／Set Creative Slot の756行きは未実装**：763→756方向は
  落とすまま。756⇔757方向は変換する。
- **光の抽出の配線なし**：`ChunkTranslator.extractUpdateLightFromChunkData757`
  自体は動作確認済み（テストで756形式Update Lightが返ることを検証）が、
  呼び出し口が無い。757サーバー→756クライアントのChunk Dataは
  `From757→From759→…→From763` の連鎖で汎用経路に落とされるため、
  `to756ClientPacketFrom757` にはChunk Dataは決して来ない。配線には763→757の
  サーバー側変換（757と同じ既知のギャップ）が必要。推測で無理に繋げていない。
- **Client Information の763→756は素通し**：末尾フィールド差分は未対応。
- **Gradleテストの実行は未実施**（このVMにGradleが無いため）。
  単体テストはJUnitスタブ＋リフレクション呼び出し／JUnit 5.10.1実jar＋
  自作ランナーで実行した（下の「テスト」を参照）。
- **1.17（755）の実バニラ起動は未実施**：1.17.1のみ実施。755は次のループ。
- **チャットの署名チェーンはバージョン境界を越えられない**：756→759は
  現在時刻・salt 0・空署名・signedPreview=false で補完する。
  実機の759クライアントが756サーバーからのチャットをどう表示するかは未確認。
- **Player Chat 759→756は落とす**：翻訳子としては759サーバーからの Player Chat を
  756形式に変換できないため落とす（上の「チャット」を参照）。なお現状は Join Game が
  落とされるため756クライアントは759サーバーに参加できない。
  実機での確認が必要。
- **Join Game codec の素通しではない**：756→757では simulationDistance を
  補い、バイオーム81件→61件にし、dimension_type の element を1.18式にする。
  757→756では simulationDistance を落とし、バイオーム61件→81件にし、
  element を1.17.1式にする。バイオーム要素は756→757で持ち越す。
  dimension_type 要素は受け手の既定値で置き換える
  （logical_height／min_y の違い）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  756＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info にプロパティを載せない）。
- **外部モードで pack756 が無い場合**：756クライアントには757パック
  （pack_format 8）が出る。1.17.1クライアントは新しい形式のパックを
  警告付きで読む。javadoc／コメントに記録済み。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrock→756**：`JavaBackendConnection` の serverProtocol 756 を757扱いにした
  （Login Success→即プレイ、Join Game 756、Respawn 756→757→759連鎖、
  チャット送受信、Player Info、Living／Painting ディスパッチ、
  Client Information 切り出し）。チャンクは756のワイヤ形式（16セクション＋
  bitMap＋名前付きheightmaps＋バイオームint[1024]・BitStorage non-straddling）
  で読む。Bedrock 側の統合テストはまだ無い。Bedrock のインベントリは引き続き未対応。
- **770〜775のチャンク読みの不整合の疑い**：`BedrockChunkTranslator` と
  Java側 `ChunkTranslator` の770〜775読みは「1 short＋straddling」だが、
  実バニラ26.1の生キャプチャ＋バイトコードは「2 shorts＋non-straddling」を示す。
  756は正しい形式で実装し、770〜775は触っていない（Muse01／ユーザーの判断待ち）。
  Stage B の ChunkTranslator 757節も straddling 前提で、実バニラ1.18
  （non-straddling）と食い違うことが今回の実測で確定した（同じく判断待ち）。
- **メタデータ型番号の1.19付け**：757節は1.19の型番号で書かれているが、
  1.17／1.18は0〜18で同一のため757節の番号付けは756⇔757では使えない。
  判断待ち（本タスクでは触っていない）。
- **`RegistryRemapper` の `ITEM_REVERSE_MAP` と `ITEM_763_TO_765` の食い違い**：
  ボート・レコード・絵画で値が違う（Bedrock側の756分岐で発見。757のときと
  同じ箇所。`RegistryRemapper` 自体は触っていない）。
  プロキシ側の `mapItemReverse` 経路（756／757／759〜762クライアント）にも
  影響する疑い。修正は統括側（Muse01／ユーザー）の判断待ち。
- **mapItemReverse の757／759／760／761／762分岐の逆順合成の不具合**：
  隣接表を逆順に合成しており正しい合成と724/1101件食い違う（実測）。
  756分岐だけ正しい順序（最も内側から合成）。唯一の呼び出し元は
  `ChunkTranslator.java:529`。修正は統括側（Muse01／ユーザー）の判断待ち。
- **775節の `experience_orb = 47` の古いリテラルは直していない**
  （Muse01／ユーザーの判断待ち）。
- `VanillaServerHost.stop()` で `IllegalThreadStateException: process hasn't exited`
  が出ることがある（全バージョン共通の既存の問題。直していない。
  実バニラ1.17.1の起動確認でも再現した）。
- `build-numbers.properties`／`versionedJars` への1.17.1追加はできない
  （このVMのツリー内にファイルが無いため。ローカルmainの同ファイル待ち）。
- Tile Entity Dataの757→756でNBTにidを付けられない（ベストエフォート）、
  実バニラ1.17.1のJoin GameのviewDistance=4（理由は未確認）、
  `enableServerListing=true` の既定値は推論で未確認（コードに記録）。

## 756のときに見つかった罠（後続の担当へ）

- **`handleServerPlay` はハンドラ結果を763形式とみなす**（757のStage Bで
  見つけた実バグ。756でも同じ）：
  `handleServerPlay` は、ハンドラの結果を763形式とみなして759〜762向けに
  変換し直す。そのため `handleServerPlayFrom756` から直接757形式のパケットを
  返すと、IDが763の別パケットと誤読されて壊れる。実測で確認した誤読
  （756形式を763と誤読したもの）：
  Keep Alive 0x21→Hurt Animation、Ping 0x30→Open Window、
  Join Game 0x26→World Particles、Spawn 0x4B→World Border Warning Reach。
  Disconnect 0x1Aは763でも Disconnect のため誤読されないが委譲に統一。
  対策は、756→757へ変換したら `handleServerPlayFrom757` に委譲し、
  763形式で返して呼び出し側の変換に任せること。直接757形式で返す
  `handleServerPlay756To757` は作らないこと。新しいプロトコルを追加するときは、
  ハンドラが返す形式が呼び出し側の想定と一致しているか確認すること。
- **Respawn の copyMetadata**（757の§13.4の罠。756では踏んでいない）：
  SpawnInfo756／757 は `dataToKeep` を末尾に置いていたが、実レイアウトは
  1.17が {dimension NBT, worldName, seed, gameMode u8, previousGameMode u8,
  isDebug, isFlat, **copyMetadata bool**}、1.18が {…, isFlat,
  **copyMetadata bool**, death option}（minecraft-data protocol.jsonで確認）。
  dataToKeep は766以降にしか無い。756では protocol.json で末尾のフィールドを
  実測してから書いたためこの罠を踏んでいない。
  新しいバージョンの Respawn を写すときは、minecraft-data の protocol.json
  で末尾のフィールドを実測すること。
- **774＋756ログイン修正**（757の§13.7.3の756版。Stage B で見つけた実バグ）：
  774クライアントが756サーバーに繋がると、プレイに入れなかった。
  `isServer763Like()` は761／762／763だけで、Login Success で「両方設定
  フェーズに入る」分岐に入り状態が変わらず、クライアントの Login
  Acknowledged が756サーバーに誤送されていた（756サーバーは 0x03 を
  play chat と誤読する）。`ProxySession.java` の3箇所の条件に
  `isServer756()`／`is756()` を追加した。範囲は756だけに絞った。
  759／760サーバーにも同じ隙間があるが、本タスクの範囲外として記録する。
- **設定フェーズなし**：756にも757にも設定フェーズは無い。
  `ClientJoinFlow` は stage の三項演算子に `|| clientProtocol == V1_17_1` が
  無いと、756が設定フェーズの stage を誤って取る。新しいプロトコルを
  追加するときは、stage の分岐のすべての組み合わせに漏れが無いか確認すること。
- **署名なしチャット**：1.17.1の chat は署名なし（{message string}のみ）。
  759の System Chat の type は **varint**（overlay bool は760から）。
  live の chat_type "chat" の decoration は `chat.type.text`
  （`chat.type.announcement` ではない）。"system" に decoration は無く、
  chat は空。1.17.1の serverbound packet_chat は {message string} のみ。
  依頼文の記述はデータで再確認すること。
- **pack.mcmeta は平文形式**：1.17.1サーバーjarのjavapで確認。
  `SharedConstants` は protocol 756・world 2730・pack リソース7／データ7。
  `PackMetadataSectionSerializer.fromJson` は `description` と `pack_format`
  （int）のみ読む。jar内24,129クラス中 `min_format`／`max_format` を含む
  クラスは0件、`pack_format` を含むクラスは1件のみ。
  正しい形式は plain int形式
  `{"pack":{"pack_format":7,"description":"DetergentMC resource pack"}}`。
  777の min/max 形式は1.17.1には存在しない（757の罠と同じ）。
  1.17.1クライアントでのパック受け入れの実機確認は未実施。
- **反転表の first-wins の罠**：`RegistryRemapper.invert()` と
  `invert_java` は最初に見つかった preimage を採用する。756ではパーティクル
  1件（`light`→`block_marker`、補正 `[[3, 3]]`）とバイオーム26件が
  名前の真値と食い違う。対策：反転表は `build_inverse`（同名 preimage 優先→
  relaxed→初出）で名前の真値を作り、`invert()` の結果との差分を
  **補正**として出す。verify では (a) `*_756_TO_757_*` 表＝名前の真値、
  (b) invert(順向き)＋補正＝名前の真値、の両方を確認する。
  ブロック状態・音の補正は756では0件。
- **バイオームの反転表は `invert()` だけでは作れない**：順向き表の区間表には
  新規6バイオームの代替先（例：`snowy_slopes`→`snowy_tundra`）が、上書き表には
  改名（例：`snowy_plains`→`snowy_tundra`）がある。`invert()` は区間表を先に
  処理するため、`snowy_tundra`（756 id 12）の preimage に `snowy_slopes`
  （757 id 30）を選んでしまう。名前の真値は改名元の `snowy_plains`（757 id 3）。
  残り25件は1.17で削除されたバイオームで、`invert()` は preimage が無いため
  平原に落ちるが、名前の真値は `REVERSE_BIOME_FALLBACK_756` の代替先。
  計26件を `BIOME_UP_756_CORRECTIONS` として補正する。
- **プロパティ順序の違い**：1.17と1.18で chest／trapped_chest／
  piston_head／moving_piston の状態文字列のプロパティ順序が違う
  （757と同じ構図）。対策：`gen-756-tables.py` で vv の blockstates 文字列の
  プロパティをソートして正規化してから `build_pair` に渡す。共有の
  `generate-remap-tables.py` の `build_blockstates` 自体は触らない。
- **`serverProtocolFor("1.17")` はフォールバック**：1.17はプロトコル755で
  このツリーに無いため、`serverProtocolFor("1.17")` は765を返す
  （警告ログあり）。推測で755の値を入れてはいけない。755が来たときの
  追従課題として残す。
- **`ClientAdapter` の三項演算子の置き換えミス**：先頭への追加にすること。
  置き換えると他のプロトコルの値（775の "84" など）が消える。
  三項演算子の編集は置き換えではなく先頭への追加にすること。
- **static 初期化子の 64KB 制限**：`RegistryRemapper` の `<clinit>` は
  756追加前で64,669バイト（残り866バイト）。756の表を外側の static
  初期化子に入れると `code too large` でコンパイルできない。
  756の展開済み表は `private static final class Maps756` にまとめた。
  755以降も同じホルダ方式にすること。
- **1.17.1のjarはfat jar**：`META-INF/MANIFEST.MF` の `Main-Class` は
  `net.minecraft.server.Main` で、`net.minecraft.data.Main` がjar内に直接
  入っている。1.18のjarは bundler形式（`Main-Class: net.minecraft.bundler.Main`）
  だが、1.17.1はbundler形式ではない。そのため757の前例（サーバーを一度起動
  してlibrariesを取得し、内側jar＋libraries全jarをclasspathにする段取り）は
  1.17.1では不要で、jarをそのままclasspathにして直接実行できる。
- **minecraft-dataの1.17.1のprotocolは `pc/1.17.1`**：`data/dataPaths.json` で、
  1.17.1の `protocol` は `pc/1.17.1` を指す（`pc/1.17` ではない）。
  `blocks`・`items`・`enchantments`・`biomes`・`effects`・`sounds`・
  `particles`・`entities` は `pc/1.17` を指す。`pc/1.17.1/protocol.json`
  （155,791バイト）と `pc/1.17/protocol.json`（155,058バイト）は内容が違う
  ため、1.17.1用にはpc/1.17.1のものを使うこと。
- **ViaVersion mapping-1.17.1.json は無い**：ViaVersionのMappingsリポジトリには
  `mapping-1.17.1.json` が無く、`mapping-1.17.json` がある。代替として
  `mapping-1.17.json` を `vv-1.17.json` として保存した。内容の照合：
  blockstates 20,342件・blocks 898件・items 1,100件・sounds 1,190件・
  particles 89件・entities 113件で、実バニラjarの報告書と全件一致。
- **バイオームのidは非連番**：`biomes-1.17.json` は81件で id が0〜175の非連番
  （1.18は61件・0〜60連番）。1.17のbiomes.jsonのidは旧来の数値バイオームID
  （例：dripstone_caves=174、lush_caves=175）で、レジストリのprotocol_id順とは
  別物。RANGES形式は「区間＋上書き」で非連番の写像も表せるため、新しいコード
  構造は不要。順向き表は757側61件のRANGES＋OVERRIDES、反転表は756側176件
  （id 0〜175。隙間の id は平原（757 id 1）で埋める）。
  根拠：実バニラ1.17.1のライブキャプチャで dimensionCodec の
  `minecraft:worldgen/biome` が81件・id 0〜175の非連番であることを確認済み。
- **756は757の土台の上に積む**：756（1.17.1）の対応は757の757↔759変換を流用する。
  ただし756↔757のチャンクは757↔759のチャンクとは別形式（16セクション＋
  bitMap＋バイオーム配列 vs 24セクション＋パレット＋光）のため、757の翻訳子を
  そのまま流用せず、756専用の翻訳子を書くこと。
- **portalCooldown は763から、seaLevel は769から**（777ノート §19.1の
  「768からある seaLevel」は誤り。757の SpawnInfo にどちらも無い）。
  **763（1.20）の Join Game の dimension は文字列**（varint 化は764から。
  763形式は759形式＋末尾 portalCooldown varint）。
  **chat_message の checksum は771（1.21.5）から**（1.21.4まで無し、
  1.21.5以降あり）。
- **1.17.1の Join Game の viewDistance=4**：実バニラ1.17.1のJoin Gameの
  viewDistanceは4（server.propertiesは10）。生バイトで確認済みだが理由は
  未確認で、値はそのまま引き継いでいる。`enableServerListing=true` の既定値と
  同じく、推論で決めた値はコードに「未確認」と記録すること。

## Java 16（内部756サーバーを動かすとき）

1.17.1は **Java 16** で動く。Java 17／Java 21／Java 25 は内部サーバーには要らない。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.17.1は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.17.1` を指定すると、内部756サーバーが Java 16 で起動する。
`javaBinaryFor` には Java 16 必須リストは無く、Java 16 は既定分岐
（現在の `java.home` をそのまま使う）で、DetergentMC 自身も Java 17 で動くため、
追加は不要。ただしドライバ自体はJDK 17で動かす必要があった（core／protocolに
sealedクラスがありJDK 16ではコンパイル・実行不可）。そのため実バニラ1.17.1の
起動確認ではドライバをJDK 17＋`-Djava.home=$HOME/.tools/jdk-16.0.2+7` で起動し、
`javaBinaryFor("1.17.1")` が返すバイナリを
`/home/hatch/.tools/jdk-16.0.2+7/bin/java` にした（ドライバ出力 `JAVA-BIN=` で
確認済み）。本番コードの変更は無い。
Temurin 16.0.2+7 は `~/.tools/jdk-16.0.2+7` に導入済み（Adoptium公式のみ）。
バニラ1.17.1サーバーjarのSHA-1は
`a16d67e5807f57fc4e550299cf20226194497dc2`
（piston-meta。43,626,592バイト。`run/vanilla/server-1.17.1.jar`）。
1.17のjarは取得していない（755は次のループ）。

## リソースパック（pack756）

pack_format はリソース7・データ7。`detergentmc-pack-1.17.1.zip`（19つ目）。
pack.mcmeta は平文 `{"pack":{"pack_format":7,"description":"DetergentMC resource pack"}}`
（上の「罠」を参照）。`resource-pack.url-756`／`sha1-756` で出し分ける。
フォールバックは `756→757→759→760→761→762→763`（pack757 は format 8。
756はツリー内最古のため古いフォールバックが存在せず、1.17.1クライアントは
新しい形式のパックを警告付きで読む）。

## Bedrock→756

- チャンクのワイヤ形式（実バニラ1.17.1の生キャプチャ＋javapで実測）：
  **16セクション**（bitMap付き）、**heightmap は名前付き・空名のNBT**、
  **バイオームは varint[1024]**、**BitStorage は non-straddling**
  （BPE 5のData Array長342）。757の「24セクション・セクションごとの
  バイオームパレット・光内包」とは違う。バージョンごとに実測で決める方針通り。
- 756正規化：756→757は恒等＋757連鎖で765に正規化する。エンティティ・
  アイテム・エフェクト（1ベース→0ベース）・パーティクル（barrier 2／light 3→
  block_marker 3、データ無しで読み飛ばし）・絵画（donkey_kong 25→29）も
  同じ正規化で扱う。
- 今回見つけて直したバグ：**`airBlocks()` の二重変換**。不在セクションを
  BedrockランタイムID（11881）で埋めていたため、`writeSubChunk`（Java状態IDを
  期待し765化＋ランタイムID化する）で二重変換され、不在セクションが 4229・
  2層になっていた。Javaの空気（状態0）で埋めるよう修正。これ以前からある
  潜在バグで、757では全節が必ず存在するため発現しなかった。
  `while (blockSections.size() < sectionCount)` のフォールバック経路
  （全プロトコル共通）も同じ修正で直っている。
- 副次的知見：設定パケットは Join Game 受信後に送ること。Login Success
  直後に送ると1.17.1サーバーが切断する（ログイン状態のままのため）。
  757と同じ実測。

## 統合テスト（Fake756Client＋4セル）

`detergentmc-core/src/test/java/io/detergentmc/core/vanilla/ProxyIntegrationTest.java`
に `Fake756Client`＋4セルを追加済み（10,824行・98テスト→11,291行・102テスト、+467行）。
`Fake757Client` が雛形。設定フェーズなし。プレイの Keep Alive 0x21 に 0x0F で
応答。ヘルパーは `loginStart756`（usernameのみ）／`clientInformation756`
（7フィールド。末尾 disableTextFiltering=false、allowServerListings無し）／
`chatMessage756`（文字列のみ）／`move756`。`findRealVanilla1171Jar()` を追加。
いずれのセルもjarが無ければ `assumeTrue` でスキップする（775・757の前例と同じ）。
javacは `~/workspace/int756d.sh` で確認済み（main 253ファイル＋テスト300ファイル、
終了コード0）。

4セル：

| セル | 内容 | プロキシ経由 | 直接接続 |
| --- | --- | --- | --- |
| (a) | `fake756ClientJoinsRealVanilla1171ThroughProxy`（内部756） | 未実施（環境制約。試行して失敗を確認） | **通過**（Join Game 0x26＝31,555バイト、Chunk Data 0x22＝462パケット・5,939,706バイト、チャット到達、移動後も接続維持） |
| (b) | `fake756ClientJoinsRealVanilla118ThroughProxy`（内部757） | 未実施（環境制約。試行して失敗を確認） | **想定通りの切断**（`multiplayer.disconnect.incompatible` with "1.18"） |
| (c) | `fake756ClientJoinsRealVanilla119ThroughProxy`（内部759） | 未試行（失敗原因が共通の環境制約と確定したため） | **想定通りの切断**（同上 with "1.19"） |
| (d) | `fake757ClientJoinsRealVanilla1171ThroughProxy`（内部756） | 未試行（同上） | **想定通りの切断**（同上 with "1.17.1"） |

**プロキシ経由の4セルはこのVMでは実行できておらず、「統合テストが通った」とは
言えない**。原因はJVMのTCP遮断（サンドボックス other_tcp がDeny）。(a)はプロキシ
25591でlisten開始（PROXY-READY）したがPythonクライアントは正規パケット0件でEOF
（受信したのはサンドボックスのポリシー文 `muse: Other TCP connections is turned
off for this assistant. To...` 193バイト）。(b)も同一の失敗モード（プロキシログに
`handleServerLogin` の想定外パケット id=0x75／0x3e／0x73＝ポリシー文の断片 "u>/s"）。
777・757のStage Eと同じ環境制約。プロキシ経由の実行には、ユーザーが
Muse設定で other_tcp を Deny→Ask に変えるか、ループバックTCPが通る環境での
実行が必要。Stage Eを「完了」と呼ぶにはプロキシ経由の(a)を最低限通すことが必要。
結果JSONは `/tmp/capture-756/`（`result-a/b/c/d-direct.json`、`result-a/b-proxy.json`）。
バックアップは `run/capture-756/`（`run/` はgitignore済み）。

なお Client Information は Login Success 直後ではなく **Join Game 受信後に送る
こと**（757 §15.2の罠を踏襲。1.18では Login Success 直後に送るとサーバーが
デコード例外で切断することを実測済み。1.17.1での切断再現は未確認）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.17.1.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  新規756テスト **65件全通過**：`PlayTranslator756Test`（17件）、
  `ChunkTranslator756Test`（14件）、`ProxySession756Test`（8件）、
  `ClientAdapter756Test`（11件）、`ClientJoinFlow756Test`（5件）、
  `ProtocolVersion756Test`（3件）、`ClientProxy756Test`（4件）、
  `V1171AdapterTest`（6件）。
  `detergentmc-adapter-v1_17_1`：`V1171AdapterTest` 6件中6件通過。
  core：`VanillaServerHostTest` 11件中11件通過（1.17.1の2アサーション含む）。
- 全体回帰（detergentmc-protocol）：
  **2,377件通過・28件失敗**。失敗は全て環境制約で、756が原因の
  本物の回帰は **0件**：
  - ソケットタイムアウト8件（ClientProxyTest 5件、ClientProxyAuthTest 3件。
    JVMのTCP遮断。HEADベースラインでも同じ）。
  - ResourcePackServerのローカルHTTP -1が18件（JVMのTCP遮断）。
  - SessionServerClientのbase URL未設定2件。
- 旧期待値16件の修正：PacketIds／ClientAdapterのプロトコル一覧の期待値を
  [757, 759..775]→[756, 757, 759..775]へ（ClientAdapter764Test、ClientAdapterTest、
  PacketIds759〜775Test 15件、PacketIdsTest）。777のStage B §27と同じ修正。
- `verify-756-tables.py` は **43件中43件通過**を維持
  （A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）。
- Stage D：`ResourcePackBuilder756Test`（3件）、`ResourcePackServer756Test`（2件）、
  `ResourcePackMode756Test`（6件）、コア配線4件全通過（新規含む）。
  全スイート3,039件中2,802通過・105失敗・132中断。失敗105件の内訳：
  83件ループバックTCP遮断、2件ループバックHTTPの `IOException`、
  18件 `discoversThe*Adapter`（自作ハーネスの欠陥。flatコピーで各アダプタの
  `META-INF/services` が上書きされ、最終的に `V1171Adapter` しか残らなかった。
  親の統合検証で18/18通過を確認し、ハーネス起因と確定）、2件チャンク系
  （`airBlocks()` バグによるもので、柱2で修正済み。親の統合検証で通過を確認）。
- Bedrock：`BedrockChunkTranslatorTest` に756件5件、`JavaBackendConnection756Test`
  新規32件。**756テスト：43通過／7失敗**。失敗は全てソケットテスト
  （ハンドシェイクが届かない）。既存の775ソケットテストも同じ失敗で、
  JVMループバックTCP遮断の環境制約と切り分け済み。
  全bedrockスイート：350件中297件通過、51件失敗、2件中断。51件は全てソケットテスト
  （756〜775・759〜761・765のログイン／チャット／スポーン／設定フロー、同じ
  「偽サーバーがacceptしない」モード）で、非ソケットの失敗は0件。回帰なし。
- 内部サーバー：`DatapackGeneratorTest` 13件＋`VanillaServerHostTest` 11件＝
  24件全通過（新規 `packFormatFor1171Is7` を含む）。
- 実バニラ1.17.1の起動確認：`VanillaServerHost` 経由で実バニラ1.17.1を起動し、
  `Done (115.618s)!` まで確認。データパックの `Error reading pack metadata` は0件。
- ライブキャプチャ（偽クライアント→実バニラ1.17.1／1.18、プロキシ迂回）：
  Join Game 31,555バイト（1.17.1）／23,986バイト（1.18）。756→757→756の
  往復はバイト完全一致。
- Gradleテストは未実施（Gradleが無いため `./gradlew` は実行していない。
  上記の件数はJUnitスタブ＋リフレクション呼び出し／JUnit 5.10.1実jar＋
  自作ランナーによる実行）。
