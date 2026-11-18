# 1.18クライアント → 1.19系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.18クライアント（プロトコル757）は、内部プロトコルが
757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775の18プロトコルのサーバーを対象にします。
ただし **758（1.18.2）は未対応**で、このツリーに `V1_18_2` はありません
（他人の作業中のため飛ばした。758が終わったら758↔757の合成表をそのときに作る）。
776（26.2）もこのツリーにありません。
内部757のときは素通し（リレー）、内部759のときは757↔759の直接変換、
それ以外は759を経由した連鎖変換です。**757は759＋差分のシム（shim）**です。
759とのパケットIDの違いは**変更123件・新規5件・削除8件**で、
レジストリ差分はブロック状態+1,106件・ブロック+35件・アイテム+51件・
エンティティ+5件・音+118件・パーティクル+5件・エンチャント+1件・
ブロックエンティティ+2件・バイオーム+2件・エフェクト+1件です。
メニュー24件・属性13件は順序も同一で変化なし。改名0件・削除0件です。

1.18は**Java 17**で動き、設定フェーズがありません（764で追加）。
レジストリは Join Game の dimensionCodec NBT で届きます（759と同じ方式）。
pack_format はリソース8・データ8。アイテムはNBT（データコンポーネントではない）。
1.18と1.18.1はプロトコル757・pack_format 8を共有し、違うのは
data version（2860→2865）のみです。

757の仕組みは `docs/PROXY-1.19.md`（759）、`docs/PROXY-1.19.1.md`（760）、
`docs/PROXY-1.19.3.md`（761）、`docs/PROXY-1.19.4.md`（762）、
`docs/PROXY-1.20.1.md`（763）、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。
なお759→757の差分は「758を飛ばして759を隣接にした直接の差分」です
（777のときの776問題と同じ構図で、同じ方針 (b) を取った。
758が終わったら758↔757の合成表をそのときに作る）。

全324通り（クライアント757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775 × サーバー757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775）の対応マトリクスは `docs/VERSIONS.md` の「1.18対応：
全324通りの対応マトリクス」を参照してください（Stage Eで追加予定）。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。757用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の18つを出す（758用・776用は無い） |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 759↔757の差分（要点）

minecraft-data（`pc/1.18/protocol.json`・`pc/1.19/protocol.json`）と
ViaVersion（`mapping-1.18.json`／`mapping-1.19.json`）と
Minecraft Wiki の Packets ページ（1.18、protocol 757、2021-12-09 の改訂版
2772742）の三重照合で確認。不一致0件。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-757.py`・
`verify-757-packet-ids.py`・`verify-757-wiki.py`・`insert-757-java.py`。
件数の定義：変更＝両方にあってIDが違うもの、新規＝757にあって759の
`PacketIds` に無いもの、削除＝759にあって757に無いもの。
写像は `detergentmc-protocol/build/remap-data/packet-ids-757.json`（267件）。
`PacketIds.java` のエントリ総数は267件→272件。id757>=0 は161件
（HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 104件＋
PLAY サーバー行き 48件。CONFIGURATION は0件）。

| 分野 | 759↔757の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ・クライアント行き） | 変更79件・新規4件・削除5件。Join Game 0x23→0x26、Chunk Data 0x1F→0x22、Keep Alive 0x1E→0x21、Ping 0x2D→0x30、Respawn 0x3B→0x3D、Player Info 0x34→0x36、Disconnect 0x17→0x1A。Update Time は両方とも 0x59 で変わらない | `PacketIds` の757列で番号を付け替える |
| パケットID（プレイ・サーバー行き） | 変更44件・新規1件・削除3件。Chat Message（サーバー行き）0x04→`chat` 0x03（署名なしの1パケットに戻る） | 同上 |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一（Login Start 0x00、Login Success 0x02、ハンドシェイク 0x00） | そのまま通す |
| 設定フェーズ | **757にも759にも無い**（764で追加） | プロキシが合成・消費する（759と同じ。`docs/PROXY-1.19.3.md` の「設定フェーズなしクライアントの扱い方」を参照） |
| Join Game | **作り直し**。下の「Join Game／Respawn codec」を参照 | `PlayTranslator.translateJoinGame757To759`／`759To757` で変換する |
| Respawn | **作り直し**。Join Game と同じ2件の変更。`copyMetadata` は末尾の bool（`dataToKeep` は766以降にしか無い。下の「罠」を参照） | `PlayTranslator.translateRespawn757To759`／`759To757` で変換する |
| Join Game codec の中身 | **1.18は dimension_type（4件）＋worldgen/biome（61件）だけ。1.19はそれに `minecraft:chat_type`（8件）が追加**され、バイオームは61→63件。dimension_type は同じ4件だが codec 内の順序が違う（1.18：overworld、overworld_caves、the_nether、the_end。1.19：overworld、the_nether、the_end、overworld_caves） | codec は作り直す（`translateDimensionCodec757To759`／`759To757`）。`minecraft:damage_type` レジストリは1.18・1.19のどちらにも無い（1.19.4で追加） |
| チャット | **1.18は署名なし `chat`（{message string}のみ）。1.19は System Chat／Player Chat 等に分離（署名付き）**。ペイロード形式も違う | 専用の翻訳子で変換する（下の「チャット」を参照）。署名チェーンの検証は境界を越えられない |
| Spawn Entity | 1.18は `spawn_entity_living`（0x02）／`spawn_entity_painting`（0x03）が別パケット。1.19は Spawn Entity（0x00）に統合 | `translateSpawnEntityLiving757ToSpawnEntity759` 等で変換する。1.18に `headPitch` は無く `objectData` は i32（1.19は headPitch＋varint）。headPitch は yaw で代用（best-effort、未検証） |
| Player Info | 1.18と1.19で同一の旧式（action varint。add_player は crypto オプション付き、UUID は無い） | 暗号化情報の補完／除去だけする（`translatePlayerInfo757To759`／`759To757`） |
| Update Time | **IDもペイロードも同一**（757・759とも 0x59） | 変換器なし。番号の付け替えも不要 |
| Set Held Item／Vehicle Move／Held Item Change | ペイロード同一 | 素通し（`translateSetHeldItem757To759` 等。IDだけ付け替える） |
| Sound／Entity Sound／Named Sound | 1.19で末尾に seed i64 が付く | `ChunkTranslator` の757節で変換する |
| World Particles | particleId が i32（1.18）→varint（1.19） | 同上 |
| Entity Effect／Remove Entity Effect | effectId が i8（1.18）→varint（1.19）。1.19の Entity Effect には factorCodec（option NBT）が付く | 同上 |
| Multi Block Change | レコードが varlong（1.18）→varint（1.19） | 同上。レコードの詰め方は未検証 |
| Trade List | inputItem2 が `[option slot]`（1.18）→`slot`（1.19） | 同上 |
| Block Dig／Block Place／Use Item | 1.19で末尾に sequence varint が付く | 757→759は sequence 0 を補完、759→757は剥がす |
| Set Beacon Effect | 1.18は {primary varint, secondary varint}、1.19は {primary option varint, secondary option varint}（-1＝無しは推測。未実測） | 同上 |
| Update Structure Block／World Border | seed／speed が **varlong（1.18）→varint（1.19）** | 同上 |
| Login Start | 1.18は {username}、1.19は {username, signature option} | 757→759は signature option 無しを補完 |
| Enchant Item | 両方 {windowId i8, enchantment i8}（IDだけ変わる） | IDだけ付け替える。i8に収まらないものは落とす |
| Chunk Data | 1.18／1.19で同一（heightmaps NBT、trustEdges、light） | そのまま通す |
| スロット | 1.18／1.19で同一（`[present bool][id varint][count i8][optionalNbt]`、NBT時代でKIND表なし） | そのまま通す |
| Explosion／Block Entity Data／Update Attributes | 1.18／1.19で同一のため変換器なし（意図的な省略） | そのまま通す |
| ブロック状態 | **757=20,342件、759=21,448件。差分+1,106**（新規994＋葉の waterlogged 追加112）。改名0件・削除0件 | 対応表で変換する。759にしかない994件は757向けに代替表のブロックになる |
| ブロック | **757=898件、759=933件。差分+35**（mangrove 一式、mud／packed_mud／mud_bricks、sculk／sculk_vein／sculk_catalyst／sculk_shrieker、reinforced_deepslate、froglight 3種、frogspawn） | 対応表で変換する。代替は下の「代替表」を参照 |
| アイテム | **757=1,101件、759=1,152件。差分+51**（上記ブロックのアイテム＋ボート類、tadpole_bucket、recovery_compass、allay／frog／tadpole／warden のスポーンエッグ、music_disc_5、disc_fragment_5、goat_horn、echo_shard） | 対応表で変換する |
| エンティティ | **757=113件、759=118件。差分+5**（allay、chest_boat、frog、tadpole、warden） | 対応表で変換する。代替：allay→vex、chest_boat→boat、frog／tadpole→axolotl、warden→ravager |
| 音 | **757=1,203件、759=1,321件。差分+118**（allay 7、frog 8、warden 20、sculk 系、mangrove_roots／mud 系、froglight、frogspawn、goat_horn 系、tadpole、music_disc.5、music.overworld 4件、parrot.imitate.warden） | 対応表で変換する。代替は下の「代替表」を参照 |
| パーティクル | **757=88件、759=93件。差分+5**（sonic_boom、sculk_soul、sculk_charge、sculk_charge_pop、shriek） | 対応表で変換する。代替：sonic_boom→explosion、sculk_*→soul、shriek→sweep_attack |
| メニュー | 24件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 属性 | 13件で同じ顔ぶれ・同じ順番（文字列キー。数値表は不要） | 変換なし（identity） |
| エンチャント | **757=38件、759=39件。差分+1**（swift_sneak、759の12番に挿入） | swift_sneak→DROP（-1）、それ以外はシフト |
| ブロックエンティティ種別 | **757=34件、759=36件。差分+2**（sculk_catalyst 34、sculk_shrieker 35） | 759の新規2件→DROP（-1） |
| バイオーム | **757=61件、759=63件。差分+2**（mangrove_swamp 7番、deep_dark 52番） | 対応表で変換する。代替：mangrove_swamp→swamp（757 id 6）、deep_dark→dripstone_caves（757 id 49） |
| エフェクト | **757=32件、759=33件。差分+1**（darkness。ワイヤ上の id は33、末尾） | darkness（33）→DROP、1〜32は恒等。表は作らない（ハードコード） |
| レシピシリアライザ | 21件で同じ | 変換なし |
| ダメージ種別 | **757・759のどちらにも無い**（1.19.4／762で追加） | 対応表は42件の恒等写像プレースホルダ。ワイヤ上の呼び出し元は無い（759と同じ） |
| データコンポーネント種別 | 無し（1.20.5で追加） | N/A |
| 絵画 | 1.18は `minecraft:motive` 26件、1.19は `minecraft:painting_variant` 30件（レジストリ改名＋新規4件 earth／fire／water／wind） | painting の motive 759→757は0（kebab。正しい模様は後の Set Entity Data で来る） |

## 新規／削除パケットの対応（759↔757）

| パケット | 759→757 | 757→759 |
| --- | --- | --- |
| `SPAWN_ENTITY_LIVING`（クライアント行き 0x02） | 757にだけある（新規）。Spawn Entity（player種別以外）を Living に直す | **Spawn Entity へ変換**する（`translateSpawnEntityLiving757ToSpawnEntity759`） |
| `SPAWN_ENTITY_PAINTING`（クライアント行き 0x03） | 757にだけある（新規）。Spawn Entity（painting種別）を Painting に直す | **Spawn Entity へ変換**する（`translateSpawnEntityPainting757ToSpawnEntity759`） |
| `SCULK_VIBRATION_SIGNAL`（クライアント行き 0x05） | 757にだけある（新規）。1.19で削除された | **落とす**（ProxySessionでIDで落とす。変換器なし） |
| `CHAT`（クライアント行き 0x0F） | 757にだけある（新規）。署名なしチャット | **System Chat へ変換**する（`translateChat757ToSystemChat`）。Player Chat にはしない |
| `CHAT_TO_SERVER`（サーバー行き 0x03） | 757にだけある（新規）。署名なしチャット（{message string}のみ） | **Chat Message／Chat Command へ変換**する（`translateChat757ToChatMessage`／`ChatCommand`） |
| `SYSTEM_CHAT`（759ではクライアント行き 0x5F） | **落とす**。757に無い | `translateSystemChat759ToChat` で chat へ変換する（type は varint。overlay bool は760から） |
| `PLAYER_CHAT`（759ではクライアント行き 0x30） | **落とす**（署名付きのため変換できない。`translatePlayerChat759ToChat` は null を返す） | 757に無いため送らない |
| `SHOULD_DISPLAY_CHAT_PREVIEW`（759では 0x4B） | **落とす** | 757に無いため送らない |
| `SERVER_DATA`（759では 0x3F） | **落とす** | 757に無いため送らない |
| `CHAT_PREVIEW`（759ではクライアント行き 0x0C） | **落とす** | 757に無いため送らない |
| `CHAT_MESSAGE`（759ではサーバー行き 0x04） | 757の `chat` 0x03 に変換する（`translateChatMessage759ToChat`） | 上の `CHAT_TO_SERVER` の行の通り |
| `CHAT_COMMAND`（759ではサーバー行き 0x03） | 757の `chat` 0x03 に変換する（`translateChatCommand759ToChat`） | 上の `CHAT_TO_SERVER` の行の通り |
| `CHAT_PREVIEW_TO_SERVER`（759ではサーバー行き 0x05） | **落とす** | 757に無いため送らない |

## Join Game／Respawn codec

情報源：公式 server_mappings.txt（1.18 sha1 `a8fe854e…`、1.19 sha1
`1c1cea17…`、両方 sha1 照合済み）と minecraft-data protocol.json
（mc-1.18／mc-1.19-protocol.json）＋loginPacket.json。
実キャプチャ：実1.18 Join Game 23,986バイト／実1.19 Join Game 26,960バイト。
757→759→757の往復はバイト完全一致、759→757→759はバイオームの
category 追加以外一致。

**Join Game（ClientboundLoginPacket）**：変更2件。
1. `dimensionType`：1.18は DimensionType の値そのものを NBT で送る、
   1.19は ResourceKey（文字列）で送る。minecraft-data protocol.json
   でも1.18は `dimension` が nbt、1.19は `worldType` が string と確認。
2. `lastDeathLocation`（Optional<GlobalPos>）：1.19で新規追加
   （dimensionName string＋location position の Optional）。1.18には無い。
   →759→757では落とす、757→759では absent を書く。
- 変わらないもの：playerId、hardcore、gameType、**previousGameType
  （両方に存在。i8 で -1 が無し。26.3の Optional 化とは違う）**、levels、
  seed、maxPlayers、chunkRadius、simulationDistance、reducedDebugInfo、
  showDeathScreen、isDebug、isFlat。`enforcesSecureChat` は1.19の Login
  には無い（1.19.1／760で追加）。
- `dimensionCodec`（Join Game 内の NBT）：1.18は dimension_type（4件：
  overworld、overworld_caves、the_nether、the_end）＋worldgen/biome
  （61件）。1.19はそれに **`minecraft:chat_type`（8件：chat、system、
  game_info、say_command、msg_command、team_msg_command、emote_command、
  tellraw_command）が追加**。biome は61→63件。dimension_type は同じ
  4件だが codec 内の順序が違う（1.18：overworld、overworld_caves、
  the_nether、the_end。1.19：overworld、the_nether、the_end、
  overworld_caves）。署名付きチャット関連のレジストリ追加が Join Game
  の codec に現れている。

**Respawn（ClientboundRespawnPacket）**：同じ2件の変更
（dimensionType が値→キー、lastDeathLocation Optional の追加）。
それ以外は同一（dimension、seed、playerGameType、previousPlayerGameType、
isDebug、isFlat、keepAllPlayerData）。Respawn の previousGameMode は
u8（Join Game の i8 と違う）。「none」の符号は不明のため範囲チェックせず
そのまま写す。

**Registry Data パケット**：1.18／1.19には存在しない（設定フェーズは
1.20.2／764で追加）。レジストリは Join Game の dimensionCodec NBT で
送られる。

## チャット（757は署名なし）

- **Chat（クライアント行き 0x0F／サーバー行き 0x03）**：1.18は署名なしの
  `chat`（サーバー行きは {message string}のみ）。1.19は System Chat／
  Player Chat／Chat Message／Chat Command に分離（署名付き）。
- 757→759：クライアント行き chat は System Chat へ、サーバー行き chat は
  Chat Message（`translateChat757ToChatMessage`）か Chat Command
  （`translateChat757ToChatCommand`）へ変換する。チャット署名は現在時刻・
  salt 0・空署名・signedPreview=false で補完する（763と同じ簡略化）。
- 759→757：System Chat は chat へ変換する（759の System Chat の type は
  **varint**。overlay bool は760から。live の chat_type "chat" の
  decoration は `chat.type.text`）。Player Chat は落とす（署名付きのため）。
  **つまり757クライアントが759サーバーに参加すると、他プレイヤーの
  チャット（Player Chat）は表示されない**。サーバーからのお知らせ
  （System Chat）は表示される。実機での確認は未実施。
- Chat Preview／Should Display Chat Preview／Server Data／
  Chat Preview To Server は757には無いため落とす。
- **署名チェーンの検証**：境界を越えると署名チェーンはつながらない。
  正直な制限として残す。

## 代替表（759にしかないものを757でどう見せるか）

`RegistryTables757.java`（50表）と `generate-remap-tables.py` の757対応で
作ったもの。改名0件・削除0件のため、代替はすべて「759の新規を757の既存に
落とす」方向。反転表は名前の真値で作る（`invert()` の first-wins では
食い違うため、ブロック状態110件・音20件の補正 `UP_757_CORRECTIONS` が必須。
777では偶然アルファベット順で正解したが、757では通用しない）。

- ブロック：mangrove 系→oak 系（mangrove_propagule→oak_sapling は
  `{"stage": p["stage"]}`、mangrove_leaves→oak_leaves は waterlogged を
  落とす専用関数）。mud／packed_mud／muddy_mangrove_roots→dirt、
  mud_bricks 系→bricks 系、sculk→deepslate、sculk_vein→glow_lichen、
  sculk_catalyst→deepslate、sculk_shrieker→sculk_sensor、
  froglight 3種→glowstone、frogspawn→lily_pad、
  reinforced_deepslate→deepslate、mangrove_roots→oak_planks。
- アイテム：ブロック系は上記と対応。ボート類は対応する boat へ
  （mangrove_boat／mangrove_chest_boat→oak_boat、oak／spruce／birch／
  jungle／acacia／dark_oak の chest_boat→対応する boat）。
  tadpole_bucket→water_bucket、recovery_compass→compass、
  allay_spawn_egg→vex_spawn_egg、frog／tadpole_spawn_egg→
  axolotl_spawn_egg、warden_spawn_egg→ravager_spawn_egg、
  music_disc_5→music_disc_13、disc_fragment_5→music_disc_13、
  goat_horn→music_disc_13（1.18に角笛の類似品が無いためのベストエフォート）、
  echo_shard→amethyst_shard。
- エンティティ：allay→vex、chest_boat→boat、frog→axolotl、
  tadpole→axolotl、warden→ravager。
- 音：allay 系→vex 系、frog 系→axolotl 系、tadpole 系→axolotl 系、
  warden 系→ravager 系、sculk 系→deepslate 系／sculk_sensor 系、
  froglight→block.glass.*、frogspawn→block.slime_block.*、
  mangrove_roots／muddy_mangrove_roots→block.wood.*、
  mud／packed_mud→block.gravel.*（`block.dirt.*` は1.18に存在しないため使わない）、
  mud_bricks→block.stone.*、music_disc.5→music_disc.13、
  music.overworld 4件→dripstone_caves（deep_dark）／lush_caves（swamp）／
  grove（jungle_and_forest）／snowy_slopes（old_growth_taiga）
  （ベストエフォート）、parrot.imitate.warden→parrot.imitate.ravager、
  goat horn_break 系→entity.item.break、item.goat_horn.play／
  sound.0〜7→block.note_block.bass。代替先の存在は1.18の音で全件確認済み。
- パーティクル：sonic_boom→explosion、sculk_soul→soul、
  sculk_charge→soul、sculk_charge_pop→soul、shriek→sweep_attack。
- バイオーム：mangrove_swamp→swamp（757 id 6）、deep_dark→
  dripstone_caves（757 id 49）。範囲外のバイオームだけ plains に落とす。
- ブロック状態：759=21→757=15、逆は757=15→759=15（非対称。テスト済み）。

## 接続の流れ

### 757クライアント → 内部757サーバー

1. ハンドシェイク（757、状態2）。
2. Login Start（名前のみ）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x26、codec 付き）。
5. Spawn Position（0x4B）。チャンク（0x22）・Player Info（0x36）などが届く。

### 757クライアント → 内部759サーバー

1. ハンドシェイク（757、状態2）。
2. Login Start（名前のみ）。プロキシが759形式（名前＋signature option 無し）に直して送る。
3. Login Success。
4. すぐプレイ状態へ。Join Game（759形式 0x23）を757形式（0x26）に変換する。codec は作り直す（chat_type を落とし、dimension_type の順序を1.18式に戻す）。
5. Spawn Position（759形式 0x4A→757形式 0x4B）。リソースパックは757形式で送る（`pack757`、無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 759クライアント → 内部757サーバー

1. ハンドシェイク（759、状態2）。
2. Login Start（名前＋signature option）。プロキシが757形式（名前のみ）に直して送る。
3. Login Success。
4. すぐプレイ状態へ。757サーバーの Join Game（0x26）を759形式（0x23）に変換する。codec は作り直す（chat_type を補い、dimension_type の順序を1.19式にする）。
5. Spawn Position（757形式 0x4B→759形式 0x4A）。

### 757サーバー → 774クライアント

- Join Game は通る（二段変換 757→759→763→774で Join Game 0x30。
  `ProxySession757Test` で確認）。
- Respawn は落とされる（下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。未実施）。

## プレイ中の翻訳

- **チャンク**：ブロック状態（757=20,342／759=21,448／760=21,448／761=23,232／762=23,725／763=24,135）・バイオーム（757=6／759=6／760=6／761=38／762=38／763=39の swamp。mangrove_swamp の代替先）・ブロックエンティティ種別を付け替える。Multi Block Change のレコードは varlong⇔varint で書き直す。
- **スロット・ウィンドウ**：アイテムID（757=1,101／759=1,152／760=1,152／761=1,186／762=1,228／763=1,255）を付け替える。Window Click（757=0x08／759=0x0A／760=0x0B／761=0x0A／762=0x0B／763=0x0B）・Set Creative Slot（757=0x28／759=0x2A／760=0x2B／761=0x2B／762=0x2B／763=0x2B）は757→759方向では変換する（落とさない）。**763〜774→757方向は落とすまま**（多段スロット連鎖、未実装）。
- **エンティティ**：エンティティ種別（allay は757に無い。player は757=111、759=116、pig は757=64、759=67、falling_block は757=27、759=29）を付け替える。
- **音・パーティクル**：音（757=1,203／759=1,321／760=1,321／761=1,392／762=1,454／763=1,474）・パーティクル（757=88／759=93／760=93／761=93／762=96／763=95）の番号を付け替える。1.19の Sound 系パケットの末尾の seed i64 は757向けに落とす。
- **位置・Server Data・Trade List**：上の表の通り。Server Data は757向けに落とす。
- **Spawn Entity／Spawn Entity Living／Painting**：757サーバー→759以上クライアントは Living／Painting を Spawn Entity に直す。759以上サーバー→757クライアントは Spawn Entity を Living／Painting に直す（種別で振り分ける）。757＋757は素通し。

## 送らない・落とすパケット（757向け）

- sculk_vibration_signal、server_data、should_display_chat_preview、chat_preview、chat_preview_to_server（757に無いため。変換器なしでProxySessionがIDで落とす）
- Player Chat（759→757。署名付きのため変換できない）
- 763〜775→757の Join Game／Respawn（未実施のため落とす。下の「対象外」を参照）
- 757サーバー→774クライアントの Respawn（下流で落とす。未実施）
- Window Click／Set Creative Slot（763〜774→757方向。未実装のため落とす）
- swift_sneak のエンチャント（757に無いためパケットごと落とす。`ChunkTranslator` の757節）
- darkness のエフェクト（757に無いため落とす）
- Acknowledge Player Digging（757→759。757は location＋block＋status＋successful、759は sequence のみで再構成不可のため落とす）
- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応（既存の制限と同じ）
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす
- 759に無いブロック状態・アイテム・音・エンティティ：無い（759↔757は新規が759側だけのため）。761以上の新規（bamboo 系・camel など）は760↔761の代替表で処理する

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全バージョン共通。手順書は
  `docs/PLAYTEST-1.18.md`。偽クライアント＋本物のバニラでの Join Game
  キャプチャ（23,986バイト／26,960バイト）と実バニラ1.18の起動確認
  （`Done (190.782s)!`、pack metadataエラー0件）は済み。
  プロキシ経由の統合テスト（Fake757Client）は Stage E で実施中。
- **763〜775→757の Join Game／Respawn は落とすまま**：763形式は759形式＋
  portalCooldown で変換自体は容易だが、763のコーデックのバイオーム順が
  cherry_grove の1.20プロトコルIDに依存し未測定
  （minecraft-data の biomes.json はアルファベット順でプロトコル順ではない。
  1.20のデータジェネレータ実行はJVMのTCP遮断で不可）。
  764〜775は Join Game の dimension をレジストリIDで送り、dimension_type
  レジストリは設定フェーズで別送されるが ProxySession が保持していない。
  777の §21.5と同じ種類の残課題。
- **757サーバー→774クライアントの Respawn は落とされる**：Join Game は
  通るが Respawn は下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。
- **763〜767の内部サーバー＋757クライアントの Join Game／Respawn は引き続き
  落とされる**（777の §21.5と同じ種類）。
- **759／760サーバー＋766以上クライアントのログイン合成ギャップ**：
  757だけ直した（下の「罠」を参照）。759／760サーバーにも同じ隙間があるが
  本タスクの範囲外。
- **Window Click／Set Creative Slot の757行きは未実装**：763〜774→757方向は
  落とすまま。757→759方向は変換する。
- **Gradleテストの実行は未実施**（このVMにGradleが無いため）。
  単体テストはJUnitスタブ＋リフレクション呼び出し／JUnit 5.10.1実jar＋
  自作ランナーで実行した（下の「テスト」を参照）。
- **1.18.1の実バニラ起動は未実施**：1.18のみ実施。protocol・pack_formatは
  同一で world_version のみ違うため未確認のまま。
- **チャットの署名チェーンはバージョン境界を越えられない**：757→759は
  現在時刻・salt 0・空署名・signedPreview=false で補完する。
  実機の759クライアントが757サーバーからのチャットをどう表示するかは未確認。
- **Player Chat 759→757は落とす**：757クライアントが759サーバーに参加すると
  他プレイヤーのチャットが見えない（上の「チャット」を参照）。
  実機での確認が必要。
- **Join Game codec の素通しではない**：757→759では chat_type を補い、
  dimension_type の順序を1.19式にする。759→757では chat_type を落とし、
  順序を1.18式に戻す。バイオーム要素は757→759で持ち越す
  （category は759が無視する）。mangrove_swamp／deep_dark は実1.19の
  Join Game コーデック要素を入れる（名前だけではない）。
  dimension_type 要素は受け手の既定値で置き換える
  （monster_spawn_*／infiniburn の `#` の違い）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  757＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info にプロパティを載せない）。
- **外部モードで pack757 が無い場合**：757クライアントには759パック
  （pack_format 9）が出る。1.18クライアントは新しい形式のパックを
  警告付きで読む。javadoc／コメントに記録済み。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrock→757**：`JavaBackendConnection` の serverProtocol 757 を759扱いにした
  （Login Success→即プレイ、Join Game 757、Respawn、チャット送受信、
  Player Info、Living／Painting ディスパッチ、Client Information 切り出し）。
  チャンクは757のワイヤ形式（セクション short 1個・heightmap 名前付きNBT・
  Data Array に長さ接頭辞・BitStorage non-straddling）で読む。
  Bedrock 側の統合テストはまだ無い。Bedrock のインベントリは引き続き未対応。
- **770〜775のチャンク読みの不整合の疑い**：`BedrockChunkTranslator` と
  Java側 `ChunkTranslator` の770〜775読みは「1 short＋straddling」だが、
  実バニラ26.1の生キャプチャ＋バイトコードは「2 shorts＋non-straddling」を示す。
  757は正しい形式で実装し、770〜775は触っていない（Muse01／ユーザーの判断待ち）。
  Stage B の ChunkTranslator 757節も straddling 前提で、実バニラ1.18
  （non-straddling）と食い違う疑いがある（同じく判断待ち）。
- **`RegistryRemapper` の `ITEM_REVERSE_MAP` と `ITEM_763_TO_765` の食い違い**：
  ボート・レコード・絵画で値が違う（Bedrock側の757分岐で発見。oak_boat→
  jungle_boat 779、music_disc_cat→1163、painting→883 という誤りを正しい値
  773／1162／880 に修正済み。`RegistryRemapper` 自体は触っていない）。
  プロキシ側の `mapItemReverse` 経路（757／759〜762クライアント）にも
  影響する疑い。修正は統括側（Muse01／ユーザー）の判断待ち。
- **775節の `experience_orb = 47` の古いリテラルは直していない**
  （Muse01／ユーザーの判断待ち）。
- `VanillaServerHost.stop()` で `IllegalThreadStateException: process hasn't exited`
  が出ることがある（全バージョン共通の既存の問題。直していない）。
- `build-numbers.properties`／`versionedJars` への1.18追加はできない
  （このVMのツリー内にファイルが無いため。ローカルmainの同ファイル待ち）。
- メタデータ型22〜24（frog variant／global pos／painting variant、1.19追加分。
  759→757で落とす）、Spawn Entity の headPitch＝yaw 代用、vibration origin の
  補完、Multi Block Change のレコード詰めは未検証（コードに best-effort と記録）。

## 757のときに見つかった罠（後続の担当へ）

- **`handleServerPlay` はハンドラ結果を763形式とみなす**（Stage B で見つけた実バグ）：
  `handleServerPlay` は、ハンドラの結果を763形式とみなして759〜762向けに
  変換し直す。そのため `handleServerPlayFrom757` から直接759形式のパケットを
  返すと、IDが763の別パケットと誤読されて壊れる。実測で確認した誤読：
  Join Game 759形式 0x23→763の Keep Alive と誤読され **0x1Eで返った**、
  Keep Alive 759形式 0x1E→763の Unload Chunk、Ping 0x2D→Entity Look、
  Spawn Position 0x4A→World Border Warning Delay、Disconnect 0x17→
  Plugin Message、Respawn 0x3B→Face Player。
  対策は、757→759へ変換したら `handleServerPlayFrom759` に委譲し、
  763形式で返して呼び出し側の変換に任せること。直接759形式で返す
  `handleServerPlay757To759` は削除した。新しいプロトコルを追加するときは、
  ハンドラが返す形式が呼び出し側の想定と一致しているか確認すること。
- **Respawn の copyMetadata バグ**（Stage B で見つけた実バグ）：
  SpawnInfo757／759 は `dataToKeep` を末尾に置いていたが、実レイアウトは
  1.18が {dimension NBT, worldName, seed, gameMode u8, previousGameMode u8,
  isDebug, isFlat, **copyMetadata bool**}、1.19が {…, isFlat,
  **copyMetadata bool**, death option}（minecraft-data protocol.jsonで確認）。
  dataToKeep は766以降にしか無い。旧コードは copyMetadata のバイトを
  dataToKeep と誤読し、copyMetadata=true のときや death があるときに
  Respawn を壊していた。`readSpawnInfo757`／`759`、
  `writeSpawnInfo757`／`759`、`translateRespawn757To759`／`759To757` を
  修正し、テストも `respawnDataToKeepPreserved`→
  `respawnCopyMetadataPreserved` に直した。
  新しいバージョンの Respawn を写すときは、minecraft-data の protocol.json
  で末尾のフィールドを実測すること。
- **774＋757ログイン修正**（Stage B で見つけた実バグ）：
  774クライアントが757サーバーに繋がると、プレイに入れなかった。
  `isServer763Like()` は761／762／763だけで、Login Success で「両方設定
  フェーズに入る」分岐に入り状態が変わらず、クライアントの Login
  Acknowledged が757サーバーに誤送されていた（757サーバーは 0x03 を
  play chat と誤読する）。`ProxySession.java` の2箇所の条件に
  `|| isServer757()` を追加した。範囲は757だけに絞った。
  759／760サーバーにも同じ隙間があるが、本タスクの範囲外として記録する。
- **設定フェーズなし**：757にも759にも設定フェーズは無い。
  `ClientJoinFlow` は stage の三項演算子に `|| clientProtocol == V1_18` が
 無いと、757が設定フェーズの stage を誤って取る。新しいプロトコルを
  追加するときは、stage の分岐のすべての組み合わせに漏れが無いか確認すること。
- **署名なしチャット**：1.18の chat は署名なし（{message string}のみ）。
  759の System Chat の type は **varint**（overlay bool は760から）。
  live の chat_type "chat" の decoration は `chat.type.text`
  （`chat.type.announcement` ではない）。"system" に decoration は無く、
  chat は空。1.18の serverbound packet_chat は {message string} のみ。
  依頼文の記述はデータで再確認すること。
- **pack.mcmeta は平文形式**：1.18サーバーjarのjavapで確認。
  `SharedConstants.RESOURCE_PACK_FORMAT = 8`、`PackMetadataSectionSerializer.fromJson`
  は `description` と `pack_format`（int）のみ読む。jar内4,650クラス中
  `min_format`／`max_format` を含むクラスは0件。
  正しい形式は plain int形式
  `{"pack":{"pack_format":8,"description":"DetergentMC resource pack"}}`。
  777の min/max 形式は1.18には存在しない（777ノート §23.1の罠は1.18では
  当たらない）。1.18クライアントでのパック受け入れの実機確認は未実施。
- **反転表の first-wins の罠（最重要）**：`RegistryRemapper.invert()` と
  `invert_java` は最初に見つかった preimage を採用する。757ではそれが
  名前の真値と食い違う。例：757の `block.stone.break` の759側 preimage は
  `block.stone.break` 本体と `block.mud_bricks.break`（759 id 661）等があり、
  アルファベット順で mud_bricks（m）が stone（s）より先のため、invert は
  誤って mud_bricks を選ぶ。777では偶然アルファベット順で正解したが、
  757では通用しない。対策：反転表は `build_inverse`（同名 preimage 優先→
  relaxed→初出）で名前の真値を作り、`invert()` の結果との差分を
  **UP_757_CORRECTIONS** として出す。verify では (a) `*_757_TO_759_*` 表＝
  名前の真値、(b) invert(順向き)＋補正＝名前の真値、の両方を確認する。
  補正はブロック状態110件・音20件・他は0件。
- **プロパティ順序の違い**：1.18と1.19で chest／trapped_chest／
  piston_head／moving_piston の状態文字列のプロパティ順序が違う
  （例：1.18 `chest[facing=east,type=left,waterlogged=false]`、
  1.19 `chest[type=left,facing=east,waterlogged=false]`）。計84件。
  対策：`gen-757-tables.py` で vv の blockstates 文字列のプロパティを
  ソートして正規化してから `build_pair` に渡す。共有の
  `generate-remap-tables.py` の `build_blockstates` 自体は触らない。
- **葉の waterlogged 追加**：1.19で葉8種に `waterlogged` プロパティが
  追加（224件）。`build_blockstates` の drop-1-prop で対応済み
  （1.19の waterlogged true／false 両方が1.18の同状態へ）。
  逆変換では `_relaxed_match`（relaxed_props `{"waterlogged": "false"}`）
  で waterlogged=false 側を選ぶこと。
- **`serverProtocolFor("1.18.2")` はフォールバック**：1.18.2はプロトコル758で
  このツリーに無いため、`serverProtocolFor("1.18.2")` は765を返す
  （警告ログあり）。推測で758の値を入れてはいけない。758が来たときの
  追従課題として残す。
- **`ClientAdapter` の三項演算子の置き換えミス**：最初の編集で
  `V26_1 ? "84"` を `V1_18 ? "8"` に置き換えてしまい、775の "84" が
  消えるところだった。すぐ先頭への追加（`V1_18 ? "8" : V26_1 ? "84" : ...`）
  に直し、git diff で "84" が残っていることを確認した。
  三項演算子の編集は置き換えではなく先頭への追加にすること。
- **エフェクトの番号付け**：§11.8の「darkness（759の32番）」は0ベース表記で、
  ワイヤ上の id は33（registries.json 実測：1.18は1〜32、1.19は1〜33・
  darkness=33）。`mapEffect759To757` は33→DROP、1〜32は恒等で実装した。
  0ベース／1ベースの取り違えに注意。
- **759の minecraft-data は `mcdata760/pc/1.19/` にある**：`mcdata759/pc/1.19/`
  ではない。blocks.json は辞書ではなくリスト。sounds.json は minecraft-data
  の pc ディレクトリには存在しないため、音は ViaVersion の
  `mapping-1.19.json` で確認すること。
- **パケットIDスクリプトの再実行の罠**：`packet-ids-757.py` は
  `PacketIds.java` を17引数版として読む。757列挿入後（18引数版）に
  再実行すると正規表現が一致せず0件になり、空の写像 `{}` を
  `packet-ids-757.json` に書き出してしまう（2026-11-15に最終確認の
  再実行で実際に発生）。対処：`git checkout -- PacketIds.java` で原本に戻し、
  `packet-ids-757.py` を再実行して JSON を復旧（267件・変更123件・新規0件・
  削除8件・新規パケット5件と一致を確認）、`insert-757-java.py` を再実行して
  挿入し直し、sha256 が挿入前のファイルとバイト単位で一致することを確認した。
  再発防止：3スクリプトに件数ガードを追加した。`packet-ids-757.py` は
  エントリが267件でなければ終了コード1で書き出さない。
  `verify-757-packet-ids.py`・`verify-757-wiki.py` は
  `packet-ids-757.json` が267件でなければ終了コード1で照合しない。
- **`muse.edit` は短い old_text が複数箇所にマッチすると最初の1件を置き換える**：
  Stage B で `packFor763LikeClient` の中にプロキシのコードを誤挿入しそうになった。
  必ず十分な前後文脈で一意にすること。
- **757は759の土台の上に積む**：757（1.18）の対応は759の759↔760変換を流用する。
  ただし759↔757のチャットは760↔759のチャットとは別形式（署名なし chat）のため、
  759の翻訳子をそのまま流用せず、757専用の翻訳子を書くこと。
- **portalCooldown は763から、seaLevel は769から**（777ノート §19.1の
  「768からある seaLevel」は誤り。759の SpawnInfo にどちらも無い）。
  **763（1.20）の Join Game の dimension は文字列**（varint 化は764から。
  763形式は759形式＋末尾 portalCooldown varint）。
  **chat_message の checksum は771（1.21.5）から**（1.21.4まで無し、
  1.21.5以降あり）。

## Java 17（内部757サーバーを動かすとき）

1.18は **Java 17** で動く。Java 21 は不要。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.18は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.18` を指定すると、内部757サーバーが Java 17 で起動する。
`javaBinaryFor` には Java 17 必須リストは無く、Java 17 は既定分岐
（現在の `java.home` をそのまま使う）で、DetergentMC 自身も Java 17 で動くため、
追加は不要。バニラ1.18サーバーjarのSHA-1は
`3cf24a8694aca6267883b17d934efacc5e44440d`
（piston-meta。46,323,386バイト。`run/vanilla/server-1.18.jar`）。
1.18.1は SHA-1 `125e5adf40c659fd3bce3e66e67a16bb49ecc1b9`
（46,324,407バイト。`run/vanilla/server-1.18.1.jar`）。

## リソースパック（pack757）

pack_format はリソース8・データ8。`detergentmc-pack-1.18.zip`（18つ目）。
pack.mcmeta は平文 `{"pack":{"pack_format":8,"description":"DetergentMC resource pack"}}`
（上の「罠」を参照）。`resource-pack.url-757`／`sha1-757` で出し分ける。
フォールバックは `757→759→760→761→762→763`（pack759 は format 9。
757はツリー内最古のため古いフォールバックが存在せず、1.18クライアントは
新しい形式のパックを警告付きで読む）。

## Bedrock→757

- チャンクのワイヤ形式（実バニラ1.18の生キャプチャ＋javapで実測）：
  **セクションは short 1個**（blockCountのみ、fluidCount無し）、
  **heightmap は名前付き・空名のNBT**、**Data Array に長さ接頭辞あり**、
  **BitStorage は non-straddling**。777の「2 shorts＋non-straddling」とは違う。
  バージョンごとに実測で決める方針通り。
- 新発見：**blockCount は水・溶岩・水没ブロックを二重に数える**
  （`LevelChunkSection` の再計算がブロックと流体の両方で+1。バイトコードで確認）。
  これで blockCount 7560（＞4096）や差分165／204／479が水・溶岩の個数と
  完全一致することを説明できる。
- 未解明：生キャプチャ3/6チャンクの末尾7バイトの余り（セクション配列の
  末尾の空セクションが1バイト足りない形）。翻訳器は高さから24セクションだけ
  読むため読まないバイトで、実害はない。
- 副次的知見：設定パケットは Join Game 受信後に送ること。Login Success
  直後に送ると1.18サーバーが切断する（ログイン状態のままのため）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.18.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  新規757テスト **81件全通過**：`PlayTranslator757Test`（45件）、
  `ChunkTranslator757Test`（9件）、`ProxySession757Test`（4件）、
  `ClientAdapter757Test`（11件）、`ClientJoinFlow757Test`（5件）、
  `ProtocolVersion757Test`（3件）、`ClientProxy757Test`（4件）。
  `detergentmc-adapter-v1_18`：`V118AdapterTest` 6件中6件通過。
  core：`VanillaServerHostTest` 11件中11件通過（1.18の2アサーション含む）。
- 全体回帰（detergentmc-protocol 201クラス）：
  **2,286件通過・30件失敗**。失敗は全て環境制約で、757が原因の
  本物の回帰は **0件**：
  - ソケットタイムアウト8件（ClientProxyTest 5件、ClientProxyAuthTest 3件。
    JVMのTCP遮断。HEADベースラインでも同じ）。
  - ResourcePackServerのローカルHTTP -1が17件（JVMのTCP遮断）。
  - SessionServerClientのbase URL未設定5件。
- 旧期待値18件の修正：PacketIds／ClientAdapterのプロトコル一覧の期待値を
  [759..775]→[757, 759..775]へ（ClientAdapter764Test、ClientAdapterTest、
  PacketIds759〜775Test 15件、PacketIdsTest）。777のStage B §27と同じ修正。
- `verify-757-tables.py` は **43件中43件通過**を維持
  （A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）。
- Stage D：`ResourcePackBuilder757Test`（3件）、`ResourcePackServer757Test`（2件）、
  `ResourcePackMode757Test`（6件）、コア配線51件全通過（新規4件含む）。
  全スイート2,983件中2,856通過・失敗1件・中断126件。初回実行の60失敗は
  全てハーネス起因と切り分け済み（ServiceLoader登録ファイルのflatコピー上書き、
  リソース未コピー。修正後アダプタ26件・Bedrock・BubbleLoaderは全通過）。
  残る1件は `ProxyIntegrationTest#fake759ClientJoinsRealVanilla119ThroughProxy`
  の終了時レース（`VanillaServerHost.stop` で `process hasn't exited`）で、
  失敗→失敗→通過と再現性がなくフレーキー。実バニラプロセスの終了タイミングの
  問題で、柱1の変更範囲外。
- Bedrock：`BedrockChunkTranslatorTest` に757件4件、`JavaBackendConnection757Test`
  新規25件。**757テスト：29通過／7失敗**。失敗は全てソケットテスト
  （ハンドシェイクが届かない）。既存の775ソケットテストも4/4同じ失敗で、
  JVMループバックTCP遮断の環境制約と切り分け済み。
  全bedrockスイート：265通過／44失敗／2スキップ。44件は全てソケットテスト
  （757〜775・765・766全バージョンで同じ失敗）で、非ソケットの失敗は0件。回帰なし。
- 内部サーバー：`DatapackGeneratorTest` 12件＋`VanillaServerHostTest` 11件＝
  23件全通過（新規 `packFormatFor118Is8` を含む）。
- 実バニラ1.18の起動確認：`VanillaServerHost` 経由で実バニラ1.18を起動し、
  `Done (190.782s)!` まで確認。データパックの `Error reading pack metadata` は0件。
- ライブキャプチャ（偽クライアント→実バニラ1.18／1.19、プロキシ迂回）：
  Join Game 23,986バイト（1.18）／26,960バイト（1.19）。757→759→757の
  往復はバイト完全一致、759→757→759はバイオームの category 追加以外一致。
- Gradleテストは未実施（Gradleが無いため `./gradlew` は実行していない。
  上記の件数はJUnitスタブ＋リフレクション呼び出し／JUnit 5.10.1実jar＋
  自作ランナーによる実行）。
