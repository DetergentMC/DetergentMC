# 1.19.1／1.19.2クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.19.1／1.19.2クライアント（プロトコル760）は、内部プロトコルが
760／761／762／763／764／765／766／767／768／769／770／771／772／773／774のどのサーバーにも参加できます。内部760のときは
素通し（リレー）、それ以外は760↔761の直接変換のあと、既存の
761↔762の変換と762↔763の変換と763↔764↔765↔…↔774の変換を重ねます。**760は761＋差分のシム（shim）**です。
761とのパケットIDの違いは118件（760にだけある5件を含むと123件）で、ペイロードが違うのは
約10種類のパケットだけです。レジストリ差分はブロック状態+1,784件、
アイテム+34件、音+71件、エンティティ+1件、ブロックエンティティ+2件、
ブロック+39件です。パーティクル・エンチャント・絵画・エフェクト・メニューは
760と761で同一です。バイオームは63件で同じ顔ぶれですが**並びが違います**
（plains は760=1、761=38）。**ダメージ種別は760の codec に存在しません**（1.19.4で追加。
本物の1.19.2サーバーの Join Game codec で確認）。

1.19.1と1.19.2は**同じプロトコル760**で、レジストリも同一です
（minecraft-data の 1.19 と 1.19.2 のレジストリは順番も中身も同一。
ViaVersion に mapping-1.19.1.json／mapping-1.19.2.json は存在しないため、
759の `mapping-1.19.json` を760の対応表の出典に使った）。そのため
V1191Adapter（1.19.1）とV1192Adapter（1.19.2）は同じモジュール
`detergentmc-adapter-v1_19_1` に入っている。

760の仕組みは `docs/PROXY-1.19.3.md`（761）、`docs/PROXY-1.19.4.md`（762）、
`docs/PROXY-1.20.1.md`（763）、逆向きの考え方は `docs/PROXY-REVERSE.md` を参照してください。

全225通り（クライアント760／761／762／763／764／765／766／767／768／769／770／771／772／773／774 × サーバー760／761／762／763／764／
765／766／767／768／769／770／771／772／773／774）の対応マトリクスは `docs/VERSIONS.md` の「1.19.1／1.19.2対応：
全225通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用の15つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 760↔761の差分（要点）

minecraft-data（`pc/1.19.2/protocol.json`・`pc/1.19.3/protocol.json`）と
ViaVersion（`mapping-1.19.json`・`mapping-1.19.3.json`）で確認。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-760.py`・
`gen-760-tables.py`・`verify-760-tables.py`・`gen-760-remapper.py`・
`gen-760-chunk.py`・`insert-760-packet-ids.py`。

| 分野 | 760↔761の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ） | 761とIDが違うものが118件（760にだけある5件を含むと123件）。760に存在するものは168件（PacketIds の enum 数）。Join Game 0x25→0x24、Spawn Position 0x4d→0x4c、Keep Alive 0x20→0x1f、Chunk Data 0x21→0x20、Disconnect 0x19→0x17、Player Chat 0x33→0x31、Player Info Update 0x37→0x36、Resource Pack 0x3d→0x3c、Synchronize Player Position 0x39→0x38、Set Player Position（サーバー行き）0x14→0x13、Window Click（サーバー行き）0x0b→0x0a、Chat Message 0x05 は両方とも同一 | `PacketIds` の760列で番号を付け替える |
| パケットID（プレイ・761にだけある） | feature_flags（761=0x67）、player_remove（0x35）、profileless_chat（0x18）、chat_session_update（0x20）。設定フェーズのパケット（21件＋12件）も761にだけある（760には設定フェーズが無い） | 760クライアントには落とす。Chat Session Update は760サーバーへの Login Start に鍵が無いため落とす（下の「チャット」を参照） |
| パケットID（760にだけある） | chat_preview（クライアント行き 0x0c・サーバー行き 0x06）、named_sound_effect（0x17）、message_header（0x32）、should_display_chat_preview（0x4e） | 761以上には落とす（named_sound_effect は761の Sound Effect に変換する。message_header／chat_preview は落とす） |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一 | そのまま通す |
| 設定フェーズ | **760にも761にも無い**（764で追加） | プロキシが合成・消費する（761と同じ。`docs/PROXY-1.19.3.md` の「設定フェーズなしクライアントの扱い方」を参照） |
| Join Game | 760と761で同一（entityId、hardcore、gameMode、previousGameMode、worldNames、dimensionCodec、worldType、worldName、hashedSeed、maxPlayers、viewDistance、simulationDistance、reducedDebugInfo、enableRespawnScreen、isDebug、isFlat、death）。portalCooldown はどちらにも無い（763で追加） | そのまま通す（`PlayTranslator.parseJoinGame762` は760の Join Game も読む）。codec は素通しする |
| Join Game codec の中身 | **本物の1.19.2の codec は chat_type・dimension_type・worldgen/biome の3つだけ**。minecraft:damage_type レジストリは無い（1.19.4で追加）。バイオームは63件で761と同一の顔ぶれだが**並びが違う**（plains は760=1、761=38。meadow は760=29、761=31） | codec は素通しするため表は要らない。ダメージ種別の対応表は恒等写像として残すが、ワイヤ上の呼び出し元は無い（761と同じ）。バイオームの対応表は並びの違いを反映する（`RegistryTables760` の BIOME 対応表。`RegistryOrderCaptureTest.registryOrderOfRealVanilla1192` で本物の1.19.2で確認） |
| Synchronize Player Position | 760と761で同一（dismountVehicle bool は両方にある） | そのまま通す |
| Server Data | **760は末尾に `previewsChat` bool がある**。761には無い | 761→760で false を補う。760→761で落とす（`PlayTranslator.translateServerData761To760`／`760To761`） |
| Respawn／Advancements／Death／End Combat | 760と761で同一（portalCooldown はどちらにも無い） | そのまま通す |
| Explosion | **760の x／y／z は f32、761は f64**。radius・オフセット・motion は両方とも f32 | f32⇔f64で書き直す（`PlayTranslator.translateExplosion761To760`／`760To761`） |
| Hide Message | **760は {signature buffer}、761は {id varint, signature 256バイト（id==0のとき）}** | 761→760は id!=0 のとき落とす（null を返す）。760→761は署名が256バイトでないとき落とす（`PlayTranslator.translateHideMessage761To760`／`760To761`） |
| Sound Effect | **760は soundId varint、761は ItemSoundHolder**（レジストリエントリホルダー：varint id または ItemSoundEvent 名） | 761→760でホルダーを varint id に直す（インライン名は `soundIdForName760` で引く。無ければ汎用音 stone.break 760=1112）。760→761で id をホルダーに直す（`ChunkTranslator.translateSoundHolder761To760`／`760To761`） |
| Entity Sound Effect | **761は soundId varint のあと soundEvent スイッチ（0のとき {resource string, range option f32}）がある**。760は soundId varint だけ | 761→760で soundEvent を落とす。760→761で soundEvent=0（無し）にする（`ChunkTranslator.translateEntitySoundHolder761To760`／`760To761`） |
| Named Sound Effect | 760にだけある（0x17。soundId varint＋カテゴリ＋位置＋音量＋ピッチ） | 761向けに Sound Effect（ホルダー0＋名前＋range無し）に変換する（`ChunkTranslator.translateNamedSoundEffect760To761`） |
| チャット | **760は761と形式が違う**。下の「チャット（760は761と別形式）」を参照。Chat Message／Chat Command／Player Chat／Message Header／Player Info はすべて旧式 | 専用の翻訳子で変換する（`PlayTranslator.translateChatMessage760To761` など）。署名チェーンの検証は境界を越えられない（下を参照） |
| Login Start／Login Success | **760の Login Start は {username, signature option {timestamp i64, publicKey buffer, signature buffer}, playerUUID option UUID}**。761は {username, playerUUID option}（署名は Chat Session Update に移った） | プロキシは認証済みの名前・UUID から Login Start を作り直すため、クライアントの署名データは内部サーバーに届かない（既存の763と同じ扱い）。760→761／762／763へは `translateLoginStart760To765`／`760To763`、761／762／763→760へは `translateLoginStart765To760`（下の「Login Start の罠」を参照） |
| Declare Commands の引数種別 | **760は mob_effect／item_enchantment／entity_summon、761は gamemode／resource_key／resource_or_tag_key** | どのバージョン間でも変換していない（763↔764でも素通し）。制限として文書に残す |
| Declare Recipes | 761は crafting shapeless／shaped に `category` varint、special recipe に `minecraft_simple_recipe_format` を追加 | 全方向で未対応（素通しもしない。既存の制限と同じ） |
| ブロック状態 | 760=21,448件、761=23,232件（+1,784）。761にしか無い1,784件はすべて bamboo 系・hanging sign 系・chiseled_bookshelf・piglin_head・camel 関連。760にしか無いものは無い（761は純粋な追加）。21,426件が移動した | 対応表で変換。無いものは近い既存ブロックの既定状態に代替する（bamboo_*→oak_*、hanging sign→同じ木の sign、chiseled_bookshelf→bookshelf、piglin_head→zombie_head）。石への代替は範囲外だけ |
| ブロック | 760=933件、761=972件（+39）。761新規は bamboo_* 16件、hanging sign 18件、chiseled_bookshelf、piglin_head／wall_head、stripped_bamboo_block | 同上（ブロックIDも同じ代替） |
| アイテム | 760=1,152件、761=1,186件（+34）。761新規34件は bamboo 系、bamboo_raft／chest_raft、hanging sign、camel_spawn_egg、ender_dragon／iron_golem／snow_golem／wither_spawn_egg、piglin_head | 対応表で変換。新規は近い既存アイテムに代替する（bamboo_raft→oak_boat、bamboo_chest_raft→oak_chest_boat、camel_spawn_egg→horse_spawn_egg、ender_dragon_spawn_egg→dragon_egg、iron_golem_spawn_egg→iron_ingot、snow_golem_spawn_egg→snowball、wither_spawn_egg→nether_star、piglin_head→zombie_head）。石への代替は範囲外だけ |
| エンティティ | 760=118件、761=119件（+1）。761新規は camel。共通の並びは同じではない（挿入で番号がずれる。pig は760=67、761=68、player は760=116、761=117、falling_block は760=29、761=30） | 対応表で変換。camel は馬（760=40）へ写す。範囲外もブタへ |
| 音 | 760=1,321件、761=1,392件（+71）。共通の並びも同じではない（挿入で番号がずれる）。761新規は bamboo_wood 系、bamboo door／button／fence_gate／pressure_plate／trapdoor／hanging_sign、chiseled_bookshelf、hanging_sign、nether_wood*、note_block imitate×6、camel×11 | 対応表で変換。無いものは近い音に代替する（bamboo_wood→wood、bamboo door／button／fence_gate／pressure_plate／trapdoor→wooden_*、hanging_sign／chiseled_bookshelf／nether_wood→wood、note_block imitate×6→harp、camel→horse（dash→gallop、dash_ready／sit／stand→ambient、step_sand→step））。範囲外は stone.break（760=1112） |
| パーティクル | 93件で同じ顔ぶれ・同じ順番。データ付きIDは sculk_charge=30、item=39、vibration=40、shriek=92（両方とも同一） | 変換なし（identity） |
| エンチャント | 39件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エフェクト | 33件で同じ顔ぶれ・同じ順番 | 変換なし（identity）。ただし765は0ベース、760・761は1ベース（Speed は765=0、760／761=1） |
| バイオーム | 760=63件、761=63件。同じ顔ぶれだが**並びが違う**（plains は760=1、761=38。meadow は760=29、761=31） | 対応表で変換（3範囲＋55オーバーライド）。本物の1.19.2の codec で plains=1 を確認（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1192`） |
| ブロックエンティティ種別 | 760=36件、761=38件（+2）。761新規は chiseled_bookshelf・hanging_sign | 対応表で変換。chiseled_bookshelf・hanging_sign は DROP（-1。760に相当するものが無い） |
| メニュー | 24件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 絵画 | 30件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| ダメージ種別 | **760の codec に存在しない**。761の codec にも存在しない。762=42件 | 対応表は恒等写像として残すが、ワイヤ上の呼び出し元は無い（761と同じ） |
| 属性 | 13件で同じ（文字列キー。数値表は不要） | そのまま通す（762↔763と同じ） |
| 楽器 | 23件で同じ顔ぶれ・同じ順番。ただし761の note_block 新楽器（zombie／skeleton／creeper／dragon／wither_skeleton／piglin／custom_head）は760では harp | 対応表で変換。新楽器は harp へ（`STATE_PROP_FALLBACK_761_TO_760`） |
| Entity Metadata の型番号 | 760・761で同一（0=byte、1=int、…、17=particle、18=villager_data、19=optional_unsigned_int、20=pose、21=cat_variant、22=frog_variant、23=optional_global_pos、24=painting_variant、25=sniffer_state、26=vector3、27=quaternion） | variant 系は落とさず通す（761・762は同じ番号のため） |

## チャット（760は761と別形式）

**依頼文の「761と同一形式のため素通し可」は誤りだった**。データで確認したところ、
760のチャットは761と構造が違う。760が署名付きチャットを導入した最初のバージョンで、
761で形式が変わった。760はこの境界で唯一の旧式である。

- **Player Chat**：760は {previousSignature option, senderUuid, signature 必須 buffer,
  plainMessage, formattedMessage option, timestamp, salt, previousMessages, unsignedChatContent option,
  filterType..., type, networkName, networkTargetName}。761は {senderUuid, index varint,
  signature option 256バイト buffer, plainMessage, timestamp, salt, previousMessages,...}。
  761→760は Message Header を分離せず、previousMessages を空（0件）で送る。
  760→761は index をプロキシが送信者ごとに数えて補う（`ProxySession.chatIndexBySender760`。
  全体で1つのカウンタにすると761クライアントの送信者ごとのチェーン検証に合わない）。
- **Message Header**：760にだけある（クライアント行き 0x32。{previousSignature option,
  senderUuid, signature, messageHash}）。761の Player Chat からは Message Header を合成しない。
  そのため本物の760クライアントは、761以上のサーバーからのチャットを未検証（署名なし）と
  表示する可能性がある。**本物のクライアントでの確認は未実施**。
- **Chat Message（サーバー行き）**：760は {message, timestamp, salt, signature buffer(varint),
  signedPreview bool, previousMessages [{sender, sig}], lastRejectedMessage option}。
  761は {message, timestamp, salt, signature option bool, offset varint, acknowledged 3バイト}。
  760→761は署名を256バイトのものだけ残し、offset を previousMessages の件数に、
  acknowledged を {0,0,0}（未確認）で合成する。761→760は署名を必須バッファに、
  signedPreview／lastRejectedMessage を落とす。
- **Chat Command（サーバー行き）**：760は {command, timestamp, salt, argumentSignatures,
  signedPreview bool, previousMessages, lastRejectedMessage option}。761は {command, timestamp,
  salt, argumentSignatures, messageCount varint, acknowledged buffer[3]}。
  760→761は署名を256バイトのものだけ残し、messageCount を previous の件数に、
  acknowledged を {0,0,0} で合成する。761→760は signedPreview／lastRejectedMessage を落とす。
- **previousMessages の形式**：760は {messageSender UUID, messageSignature buffer(varint)} の配列。
  761／763は {id varint, signature 256バイト（id==0のとき）} の配列。両方向とも
  形式が違うため、760→761は256バイトの署名だけを id=0 のエントリとして残し、
  761→760は previousMessages を空にする。
- **Player Info**：760は旧式の action varint（0 add_player／1 update_game_mode／
  2 update_latency／3 update_display_name／4 remove_player。add_player は crypto オプション付き）。
  761はビットフラグ（add_player／initialize_chat／update_game_mode／update_listed／
  update_latency／update_display_name）。760→761は action 0 を2つの761パケットに分ける
  （add_player＋initialize_chat の 0x03、update_game_mode＋update_latency＋update_display_name の 0x34）。
  761→760はフラグごとに1つの760パケットに分ける（update_listed は落とす。
  initialize_chat だけの更新は落とす）。chat session の UUID は760の crypto には無いため、
  761向けにはプレイヤー UUID を合成する。
- **Chat Session Update**（761クライアント→760サーバー）：落とす。760サーバーは
  Login Start の署名データから鍵を受け取るが、プロキシは Login Start を作り直すため
  署名データは届かない（既存の763と同じ扱い）。
- **署名チェーンの検証**：境界を越えると署名チェーンはつながらない。
  acknowledged ビットセットは {0,0,0}（未確認）で合成する。チャット報告（chat reporting）は
  760で追加されたが、プロキシ経由での報告の扱いは未確認。**正直な制限として残す**。

## 760↔761の連鎖の方針

- 760クライアントが761サーバーに参加するとき：760→761の直接変換で Join Game（0x25）・チャンク・スロット・エンティティ・音・位置・Server Data（previewsChat を落とす）・チャット（Message Header／Player Chat／Player Info の旧式→新式）を直し、IDを761の番号に付け替える。760にだけある chat_preview／message_header／should_display_chat_preview は落とす。Chat Session Update は落とす。
- 761クライアントが760サーバーに参加するとき：761→760の直接変換で Join Game（0x24→0x25。codec は素通し。760の codec に damage_type レジストリは無い）・チャンク・位置・Server Data（previewsChat=false を補う）・チャット（Player Chat／Player Info の新式→旧式）を直す。Login Start は760形式（署名オプション付き）に直す。
- 760クライアントが762以上のサーバーに参加するとき：760→761のあと、既存の761→762→763→764→…の変換を重ねる。764以上のサーバーにはプロキシが設定フェーズを消費し、760の Join Game に codec を合成する（761と同じ流儀）。
- 762以上のクライアントが760サーバーに参加するとき：既存の変換で763→762→761まで戻したあと、761→760の直接変換をする。764以上のクライアントにはプロキシが760サーバーの Join Game codec から設定フェーズを合成する。**合成する設定フェーズに damage_type レジストリは入らない**（760の codec に無いため）。764以上のクライアントが damage_type 無しの Registry Data を受け入れるかは未確認。
- 760＋760は素通し（リレー）。Spawn Entity も Named Entity Spawn も変換しない。**760クライアントが Spawn Entity（player種別）を受け入れるかは、本物のゲームクライアントでの確認が必要**（761と同じ）。

## ダメージ種別は760に存在しない

当初は「761のダメージ種別は762の42件と同じ順番」だと仮定して対応表を作っていたが、
本物のバニラ1.19.3サーバーに偽761クライアントで参加し、Join Game の codec を読むと、
**codec にあるのは chat_type・dimension_type・worldgen/biome の3レジストリだけで、minecraft:damage_type は存在しなかった**
（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1193`）。760も同じで、
本物のバニラ1.19.2サーバーに偽760クライアントで参加し、Join Game の codec を読むと、
**codec にあるのは同じ3レジストリだけで、minecraft:damage_type は存在しなかった**
（`RegistryOrderCaptureTest.registryOrderOfRealVanilla1192`）。つまり damage_type レジストリは1.19.4で追加されたもので、760にも761にもダメージ種別のID自体が存在しない。ワイヤ上ではダメージ種別は Damage Event パケット（762専用。760クライアントには落とす）と codec の中にしか現れないため、760↔761のダメージ種別変換は実質的に不要である。`RegistryRemapper` の対応表（`mapDamageType761To760`／`mapDamageType760To761`）は恒等写像として残すが、呼び出し元は無い。バイオームは63件で761と同一の顔ぶれだが**並びが違う**（plains は760=1、761=38）ことも同じテストで確認した。

## 接続の流れ

### 760クライアント → 内部760サーバー

1. ハンドシェイク（760、状態2）。
2. Login Start（名前＋署名データ無し＋任意UUID）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x25、codec 付き）。
5. Spawn Position（0x4D）。チャンク（0x21）・Player Info（0x37）などが届く。

### 760クライアント → 内部761サーバー

1. ハンドシェイク（760、状態2）。
2. Login Start（名前＋署名データ無し＋任意UUID）。
3. Login Success。
4. すぐプレイ状態へ。Join Game（761形式 0x24）を760形式（0x25）に変換する。codec は素通し。
5. Spawn Position（761形式 0x4C→760形式 0x4D）。リソースパックは761形式 0x3cで送り、変換で760の0x3dに付け替える（`pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 760クライアント → 内部774サーバー

1. ハンドシェイク（760、状態2）。
2. Login Start、Login Success。
3. プロキシが774の設定フェーズを消費する（Select Known Packs に空で答える、Registry Data を保持する、Finish Configuration に答える、Login Acknowledged を送る）。
4. 774の Join Game（0x30）を763形式（0x28）に直し、さらに761形式（0x24）・760形式（0x25）に変換する。codec は763向けに合成したもの（dialog レジストリは合成しない）。
5. Spawn Position（774形式 0x5F→760形式 0x4D）。

### 761クライアント → 内部760サーバー

1. ハンドシェイク（761、状態2）。
2. Login Start（名前＋bool＋UUID）。
3. Login Success。
4. すぐプレイ状態へ。760サーバーの Join Game（0x25）を761形式（0x24）に変換する。codec は素通し。
5. Spawn Position（760形式 0x4D→761形式 0x4C）。

## プレイ中の翻訳

- **チャンク**：ブロック状態（760=21,448／761=23,232／762=23,725／763=24,135）・バイオーム（760=1／761=38／762=38／763=39の plains）・ブロックエンティティ種別（chiseled_bookshelf・hanging_sign は760向けに落とす）を付け替える。trustEdges は760・761・762のすべてにあるため、762⇔761⇔760では付け外し不要。763との間では付ける／落とす。
- **スロット・ウィンドウ**：アイテムID（760=1,152／761=1,186／762=1,228／763=1,255）を付け替える。Window Click（760=0x0B／761=0x0A／762=0x0B／763=0x0B）・Set Creative Slot（760=0x2B／761=0x2B／762=0x2B／763=0x2B）は変換する（落とさない）。
- **エンティティ**：エンティティ種別（player は760=116／761=117／762=122／763=122、falling_block は760=29／761=30／762=36／763=36、pig は760=67／761=68／762=72／763=72）を付け替える。761にしか無い camel は760クライアントでは馬に見える。
- **音・パーティクル**：音（760=1,321／761=1,392／762=1,454／763=1,474）・パーティクル（760=93／761=93／762=96／763=95）の番号を付け替える。camel の声は馬の声へ。
- **位置・Server Data・Explosion**：上の表の通り。
- **Spawn Entity／Named Entity Spawn**：760サーバー→761以上クライアントは Named Entity Spawn を Spawn Entity（player種別）に直す。761以上サーバー→760クライアントは Spawn Entity（player種別）を Named Entity Spawn（0x02）に直す。760＋760は素通し。

## 送らない・落とすパケット（760向け）

- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応
  （既存の制限と同じ）。
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす。
  chiseled_bookshelf・hanging_sign（761=37／8）は760向けに落とす。
- ダメージ種別が範囲外の場合：Damage Event をパケットごと落とす。
  Damage Event 自体が762専用のため、760クライアントには常に落とす。
- 761にだけある feature_flags／player_remove／profileless_chat／chat_session_update：
  760クライアントには落とす。
- 760にだけある chat_preview／message_header／should_display_chat_preview：
  761以上には落とす（named_sound_effect だけは761の Sound Effect に変換する）。
- 760に無いブロック状態・アイテム・音・エンティティ：近い既存のものに代替する（上の表を参照）。
  石・ブタ・plains への代替は範囲外だけ。
- 761→760で bamboo_* は oak_* へ、hanging sign は同じ木の sign へ、
  chiseled_bookshelf は bookshelf へ、piglin_head は zombie_head へ、
  camel は馬へ、bamboo_raft は oak_boat へ。

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全225通り共通。手順書は
  `docs/PLAYTEST-1.19.1.md`。偽クライアント＋本物のバニラでの統合テストは
  7セル（760→760（1.19.1／1.19.2）、760→761、760→762、760→763、761→760（1.19.1／1.19.2））で参加・スポーン・チャット・移動まで確認済み。
- **チャットの署名チェーンは境界を越えられない**：761→760は previousMessages を空にし、
  Message Header を合成しない。実機の760クライアントが761以上のサーバーからの
  チャットを未検証と表示する可能性がある。本物のクライアントでの確認が必要。
- **Chat Session Update は760サーバーに届かない**：プロキシは Login Start を作り直すため、
  761以上のクライアントの鍵は760サーバーに届かない。760サーバー側のチャット署名検証が
  どう動くかは未確認。
- **Declare Commands の引数種別は変換しない**：760の mob_effect／item_enchantment／
  entity_summon と761の gamemode／resource_key／resource_or_tag_key の違いは、
  どのバージョン間でも変換していない。コマンドの引数補完がずれる可能性がある。
- **764以上のクライアントが760サーバーに参加するときの設定フェーズ合成**：
  760の codec に damage_type レジストリが無いため、合成する設定フェーズに
  damage_type が入らない。764以上のクライアントが受け入れるかは未確認。
- **Join Game codec の素通し**：761サーバーの codec（bamboo 入りのブロック一覧）が
  760クライアントにそのまま届く。Mojang の codec は未知のブロックを無視するはずだが未確認。
  774サーバーの codec（trim の追加フィールド、damage_type の `effects`、cherry_grove 入りの
  バイオーム一覧）も760クライアントにそのまま届く。同じく未確認。
- **761にしか無いエンティティ（camel）は760クライアントでは馬に見える**：
  代替先が馬のため。bamboo 系のブロック・アイテムも、760クライアントでは
  既存のブロック・アイテムに見える（上の表の代替先を参照）。
- **chiseled_bookshelf・hanging_sign のブロックエンティティは760向けに落とす**：
  760に相当するものが無いため。ブロック自体は bookshelf・sign に代替されるが、
  中身（本・文字）は表示されない。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  760＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **外部モードで pack760 が無い場合**：760クライアントには761パック
  （pack_format 12）が出る。本物の1.19.1クライアントが format 12 のパックを
  適用するかは未確認。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrock→760**：`JavaBackendConnection` の serverProtocol 760 を761扱いにした
  （Login Start の760形式、Login Success の設定フェーズなし、Join Game の
  portalCooldown=0、Player Info の旧式 action、Player Chat の760形式）。
  Bedrock 側の統合テストはまだ無い。Bedrock のインベントリは引き続き未対応。Bedrock クライアントから
  760サーバーを見たとき、ブロック状態は761の番号のまま765として通す
  （761と同じ近似。ブロックによっては違うブロックに見える）。

## 760のときに見つかった罠（後続の担当へ）

- **依頼文の「761と同一形式のため素通し可」は誤り**：760のチャットは761と構造が違う。
  760の Player Chat は Message Header と分離した旧式、Chat Command は signedPreview／
  lastRejectedMessage 付き、previousMessages は UUID ベース。依頼文の記述は
  データで再確認すること。760はこの境界で唯一の旧式である。
- **Login Start の罠（Stage E で見つけた実バグ）**：763系クライアント（761／762／763）と
  764以上のクライアントが内部760サーバーに参加するとき、プロキシは765形式の
  Login Start（{name, uuid}）をそのまま760サーバーに送っていたが、760サーバーは
  {name, signature option, playerUUID option} を期待するため誤読する。
  対策は `translateLoginStart765To760` で760形式に直してから送ること
  （`ProxySession.handleClientLogin` の `isServer760()` 分岐）。
  760クライアント→761以上のサーバーも逆向きに `translateLoginStart760To765` で直すこと。
- **`ResourcePackBuilder.writePack` の switch に760の case が無かった**（Stage E で見つけた実バグ）：
  `PACK_FORMAT_1_19_1 -> PACK_MCMETA_760` の case が無く、`build760` が
  `IllegalArgumentException: Unknown resource-pack format: 9` を投げた。
  新しい pack_format を追加するときは `writePack` の switch も確認すること。
- **`handleServerPlayFrom763` の PLAY_RESOURCE_PACK 分岐に760が無かった**（Stage E で見つけた実バグ）：
  `if (is762() || is761())` だけだったため、760クライアントに765式の Add Resource Pack
  （0x44）が届いていた。760は旧式の offer（0x3d）で送ること。
  新しいプロトコルを追加するときは `is762() || is761()` のような分岐をすべて確認すること。
- **`JavaBackendConnection.recordPlayerInfo760` の crypto の読みすぎ**（Stage E で見つけた実バグ）：
  760の add_player の crypto は {timestamp i64, publicKey ByteArray, signature ByteArray} で、
  UUID は無い。余計に `readUuid()` するとパーサーがずれる。
  minecraft-data の `pc/1.19.2/protocol.json` の packet_player_info で確認すること。
- **`ClientAdapter.versionName(760)` の欠落**（Stage E で見つけた実バグ）：
  760の分岐が無く null を返していたため、`rewriteStatusJson` が760クライアント向けに
  バージョン名を書き換えなかった。新しいプロトコルを追加するときは `versionName()` も確認すること。
- **`detergentmc-core/build.gradle` の runtimeOnly に新モジュールを入れ忘れた**（Stage E で見つけた実バグ）：
  `detergentmc-adapter-v1_19_1` を `settings.gradle` に入れただけでは、
  コアの実行時クラスパスにもファットjarにもアダプターが含まれない。
  `DetergentServer.selectAdapter("1.19.1")` が実行時に失敗する。
  新しいアダプターモジュールを作ったら、`detergentmc-core/build.gradle` の
  `runtimeOnly` と `testImplementation`／`testRuntimeOnly` の両方に追加すること。
  `AdapterDiscoveryTest.discoversThe1191Adapter`／`discoversThe1192Adapter` が
  この見落としを検出した。
- **`PacketIds.isSupported()` はハードコード**：760を追加するときは
  `V1_19_1` を `supportedProtocols()` と `isSupported()` の両方に入れること。入れ忘れると
  `PacketIds*Test` が14件失敗する。
- **機械的なテストの流用は値が違うためできない**：761のテストファイルを760に流用すると、
  ブロック状態（761→762は+493、760→761は+1,784）・アイテム（+42と+34）・
  エンティティ（+5と+1）・音（+62と+71）の数がすべて違う。760のテストは手書きで
  正しい760の値を使うこと（`PacketIds760Test`・`RegistryRemapper760Test`・
  `ChunkTranslator760Test`・`PlayTranslator760Test`・`ProxySession760Test`）。
- **`ResourcePackMode.decide()` の引数ずれ（762・761の罠の再発）**：
  760の `(url760, sha1760)` 挿入で、761〜774のテストの `decide()` 呼び出しが
  1ペアずれる。オーバーロードの引数を増やすときは既存テストの呼び出しも
  全部ずらすこと。760では最終オーバーロードの末尾に `(url760, sha1760)` を
  追加し、古い最終オーバーロードは null で委譲する形にした。
- **ChunkTranslator の生成器は javadoc の境界を越えて削除する**：
  `gen-760-chunk.py` の delete_method の正規表現は `/**...*/` を越えて
  マッチするため、非貪欲なパターンに直すこと（761と同じ罠）。
- **759は760の土台の上に積む**：759（1.19）の対応は760の760↔761変換を流用する。
  760↔761の差分（特にチャットの旧式形式・Player Info の旧式形式・Sound Effect の
  varint id・Explosion の f32）は759でも同じ流儀で扱うこと。760の対応表の
  前提（bamboo 系の代替先・バイオームの並び・camel→馬）は本物の1.19.2で確認済みのため、
  759の担当は760の表を信頼してよい。

## Java 17（内部760サーバーを動かすとき）

1.19.1／1.19.2は **Java 17** で動く。Java 21 は不要。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.19.1／1.19.2は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.19.1` または `1.19.2` を指定すると、内部760サーバーが Java 17 で起動する。
バニラ1.19.1サーバーjarのSHA-1は `8399e1211e95faa421c1507b322dbeae86d604df`
（piston-meta。45,609,936バイト。`run/vanilla/server-1.19.1.jar`）。
バニラ1.19.2サーバーjarのSHA-1は `f69c284232d7c7580bd89a5a4931c3581eae1378`
（piston-meta。45,609,512バイト。`run/vanilla/server-1.19.2.jar`）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.19.1.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  `PacketIds760Test`（9件）、`ProtocolVersion760Test`（3件）、
  `RegistryRemapper760Test`（12件）、`ChunkTranslator760Test`（29件）、
  `PlayTranslator760Test`（30件）、`ClientAdapter760Test`（12件）、
  `ClientJoinFlow760Test`（5件）、`ProxySession760Test`（21件）、
  `ResourcePackBuilder760Test`（2件）、`ResourcePackMode760Test`（5件）、
  `ResourcePackServer760Test`（2件）。計130件。
- 単体テスト（`detergentmc-bedrock`）：`JavaBackendConnection760Test`（5件）。
- 配線テスト（`detergentmc-core`）：`ServerProtocolWiringTest`（1.19.1／1.19.2→760、2件）、
  `ResourcePackWiringTest`（760パックの配信・ホスト、2件）、`AdapterDiscoveryTest`（V1191Adapter／V1192Adapter、2件）、
  `DatapackWiringTest`（1.19.1／1.19.2は pack_format 10、1件）。計7件。
- 検証テスト（`detergentmc-core`）：`RegistryOrderCaptureTest.registryOrderOfRealVanilla1192`
  （本物の1.19.2の codec に damage_type が無いこと、バイオーム63件・plains=1・
  cherry_grove 無しを確認）。
- 統合テスト（`ProxyIntegrationTest`）：偽760クライアント→本物のバニラ1.19.1
  （760ネイティブ）、偽760クライアント→本物のバニラ1.19.2（760ネイティブ）、
  偽760クライアント→本物のバニラ1.19.3（761）、偽760クライアント→本物の
  バニラ1.19.4（762）、偽760クライアント→本物のバニラ1.20.1（763）、
  偽761クライアント→本物のバニラ1.19.1（760）、偽761クライアント→本物の
  バニラ1.19.2（760）。いずれも参加・スポーン・チャット・移動まで確認。
  2人プレイの統合テストは偽クライアントでは他プレイヤーのパケットが届かないため
  書いていない（761と同じ理由）。
- 既存テストの更新：760を受け入れるようになったため、760拒否のテストは759に
  ずらした（`ClientAdapter.decide(760` → `decide(759` など）。
  対応プロトコル一覧の断言には760を先頭に追加した。
  `ResourcePackMode.decide()` は最終オーバーロードの末尾に `(url760, sha1760)` を
  追加し、古い最終オーバーロードは null で委譲する形にしたため、既存の
  761〜774テストの `decide()` 呼び出しはそのまま動く（762・761の罠の再発を避けた）。
