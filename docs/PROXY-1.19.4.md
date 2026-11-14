# 1.19.4クライアント → 1.20系・1.21系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.19.4クライアント（プロトコル762）は、内部プロトコルが
762／763／764／765／766／767／768／769／770／771／772／773／774のどのサーバーにも参加できます。内部762のときは
素通し（リレー）、それ以外は762↔763の直接変換のあと、既存の
763↔764↔765↔…↔774の変換を重ねます。**762は763＋差分のシム（shim）**です。
パケットIDは763と同一（0変更・0追加・0削除）で、ペイロードが違うのは
約10種類のパケットだけです。レジストリ差分はブロック状態+410件、
アイテム+27件、音+20件、パーティクル-1件、バイオーム+1件（cherry_grove）、
ダメージ種別+2件、メニュー-1件（legacy_smithing→smithing）、
ブロックエンティティ種別+1件です。

763の仕組みは `docs/PROXY-1.20.1.md`、767は `docs/PROXY-1.21.md`、774は
`docs/PROXY-1.21.11.md`、逆向きの考え方は `docs/PROXY-REVERSE.md` を参照してください。

全169通り（クライアント762／763／764／765／766／767／768／769／770／771／772／773／774 × サーバー762／763／764／765／
766／767／768／769／770／771／772／773／774）の対応マトリクスは `docs/VERSIONS.md` の「1.19.4対応：
全169通りの対応マトリクス」を参照してください。

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・762用・763用・764用・766用・767用・768用・769用・770用・771用・772用・773用・774用の13つを出す |

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 762↔763の差分（要点）

minecraft-data（`pc/1.19.4/protocol.json`・`pc/1.20/protocol.json`）と
ViaVersion（`mapping-1.19.4.json`・`mapping-1.20.json`）で確認。
検証スクリプトは `detergentmc-protocol/tools/gen-762-tables.py`・
`verify-762-tables.py`・`packet-ids-762.py`・`gen-762-remapper.py`。

| 分野 | 762↔763の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ） | **0変更・0追加・0削除**。176パケットが同一。Join Game 0x28、Chunk Data 0x24、Resource Pack 0x40、Window Click 0x0B、Set Creative Slot 0x2B、Update Sign 0x2E、Respawn 0x41、Advancements 0x69、Disconnect 0x1A、Spawn Position 0x50、Keep Alive 0x23、Ping 0x32 は762・763とも同一 | 番号付け替えは不要 |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一。Cookie は762・763とも無い（764で追加） | そのまま通す |
| 設定フェーズ | **762にも763にも無い**（764で追加） | プロキシが合成・消費する（下の「設定フェーズなしクライアントの扱い方」を参照） |
| Join Game | 763は末尾に `portalCooldown` varint がある。762は無い | 763→762で落とす。762→763で 0 を補う（`PlayTranslator.translateJoinGame763To762`／`translateJoinGame762To763`） |
| Respawn | 763は末尾に `portalCooldown` varint がある。762は無い | 同上（`ExtraPlayTranslator.translateRespawn763To762`／`762To763`） |
| Death Combat Event | 762は playerId の後に `entityId` i32 がある。763は無い | 763→762で playerId を代入して挿す。762→763で落とす。代入値は未検証の無害化（下の「対象外・未実装」を参照） |
| End Combat Event | 762は duration の後に `entityId` i32 がある。763は無い | 763→762で -1 を挿す。762→763で落とす。-1 は未検証の無害化 |
| Multi Block Change | 762はチャンク座標の後に `suppressLightUpdates` bool がある。763は無い | 763→762で false を挿す。762→763で落とす |
| Chunk Data | 762はブロックエンティティ一覧の後に `trustEdges` bool がある。763は無い | 763→762で true を挿す。762→763で落とす |
| Update Light | 762は chunkZ の後に `trustEdges` bool がある。763は無い | 同上 |
| Open Sign Editor | 763は末尾に `isFrontText` bool がある。762は無い | 763→762で落とす。762→763で true を補う |
| Update Sign（サーバー行き） | 763は位置の後に `isFrontText` bool がある。762は無い | 同上（`ChunkTranslator.translateUpdateSign763To762`／`762To763`） |
| Advancements | 763は各 advancement 値の末尾に `sendsTelemetryData` bool がある。762は無い | 763→762で落とす。762→763で false を補う。criteria はキーだけ、display の flags は i32 の bit 0x01（backgroundTexture）で読む |
| Declare Recipes | smithing の形式が違う（762は旧式 base＋addition＋result、763は transform／trim の template 付き） | 全方向で未対応（素通しもしない。既存の制限と同じ） |
| チャット | Chat Message／Chat Command／Player Chat／System Chat は762・763で同一（署名付きチャット。1.19.1以降） | そのまま通す |
| Login Start／Login Success | 同一（名前＋bool＋任意UUID／UUID＋名前＋プロパティ数） | そのまま通す。ただし内部762サーバーへの Login Start は bool を落としてはいけない（下の「罠」を参照） |
| ブロック状態 | 762=23,725件、763=24,135件（+410）。762にしか無い9件（torchflower_crop[age=2]、decorated_pot の cracked 無し×8）。763にしか無い419件（calibrated_sculk_sensor 384、decorated_pot 16、pitcher_crop 10、suspicious_gravel 4、sniffer_egg 3、pitcher_plant 2） | 対応表で変換。無いものは近い既存ブロックの既定状態に代替する（pitcher_crop→wheat、sniffer_egg→turtle_egg、suspicious_gravel→gravel、calibrated_sculk_sensor→sculk_sensor、pitcher_plant→peony、torchflower_crop[age=2]→同ブロックの age=1）。石への代替は範囲外だけ |
| ブロック | 762=998件、763=1,003件（+5）。763新規は suspicious_gravel、pitcher_crop、pitcher_plant、sniffer_egg、calibrated_sculk_sensor。960件がずれる | 同上（ブロックIDも同じ代替） |
| アイテム | 762=1,228件、763=1,255件（+27）。762にしか無い4件は陶器の破片の改名（pottery_shard_archer／prize／arms_up／skull → archer／prize／arms_up／skull_pottery_sherd）。763新規31件（suspicious_gravel、pitcher_plant、sniffer_egg、calibrated_sculk_sensor、pitcher_pod、music_disc_relic、トリム型5件、陶器の破片16件） | 改名は対応表で写す。新規は近い既存アイテムに代替する（music_disc_relic→music_disc_cat、pitcher_pod→wheat_seeds、sniffer_egg→turtle_egg、calibrated_sculk_sensor→sculk_sensor、suspicious_gravel→gravel、トリム型5件→sentry_armor_trim_smithing_template、破片16件→pottery_shard_archer）。石への代替は範囲外だけ |
| エンティティ | 124件で同じ顔ぶれ・同じ順番（pig=72、sniffer=90、falling_block=36） | 変換なし（identity） |
| 音 | 762=1,454件、763=1,474件（+20）。762にしか無い3件（item.brush.brushing、item.brush.brush_sand_completed、music.overworld.jungle_and_forest）。763新規23件（brush の改名、suspicious_gravel×5、sniffer_egg×3、music_disc.relic、music.overworld の分割7件、amethyst resonate、sign.waxed_interact_fail） | 対応表で変換。改名は名前で写す（brush brushing→brushing.generic、brush_sand_completed→brushing.sand.complete、jungle_and_forest→jungle、逆向きも同様）。無いものは近い音に代替する（amethyst resonate→chime、suspicious_gravel→gravel、music_disc.relic→music_disc.cat、sniffer_egg→タマゴ系、sign.waxed_interact_fail→stone.break）。範囲外は block.stone.break（762=1245／763=1264） |
| パーティクル | 762=96件、763=95件。762にしか無い3件（dripping／falling／landing_cherry_leaves）。763にしか無い2件（cherry_leaves、egg_crack）。データ付きIDは sculk_charge（762=33／763=31）、item（762=42／763=40）、vibration（762=43／763=41）、shriek（762=95／763=93） | 対応表で変換。cherry_leaves（763=29）→falling_cherry_leaves（762=30）。逆向きは3種とも cherry_leaves（763=29）。egg_crack（763=94）→代替なし（0） |
| エンチャント | 39件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| エフェクト | 33件で同じ顔ぶれ・同じ順番。**ただし765は0ベース、762・763は1ベース**（Speed は765=0、762／763=1） | `RegistryRemapper.mapEffect(id765, 762)` は id765+1 を返す。範囲外は Speed（1） |
| バイオーム | 762=63件、763=64件。cherry_grove が763の5番に挿入され、5〜62番が+1ずれる。**plains は762=38、763=39**（minecraft-data の biomes.json は登録順ではなくアルファベット順。1 ではない） | 対応表で変換。cherry_grove（763=5）→meadow（762=31）。範囲外は plains（762=38／763=39） |
| ブロックエンティティ種別 | 762=40件、763=41件。suspicious_sand（762=38）→brushable_block（763=39）の改名。calibrated_sculk_sensor が763の35番に挿入され、sculk_catalyst 35→36、sculk_shrieker 36→37、chiseled_bookshelf 37→38、decorated_pot 39→40 にずれる | 対応表で変換。**calibrated_sculk_sensor（763=35）は sculk_sensor（762=34）へ写す。DROP ではない**。DROP（-1）は範囲外だけ |
| メニュー | 762=25件、763=24件。legacy_smithing（762=20）→smithing（763=20）の改名。762の smithing=21、smoker=22、cartography_table=23、stonecutter=24 は763で1つずつ前にずれる | 対応表で変換。762の legacy_smithing と smithing は両方とも763の smithing（20）へ |
| 絵画 | 30件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| ダメージ種別 | 762=42件、763=44件。generic_kill（763=17）と outside_border（763=30）が763にしか無い。generic は両方とも16 | 対応表で変換。generic_kill・outside_border は generic（16）へ。範囲外は DROP（-1） |
| 属性 | 13件で同じ（文字列キー。数値表は不要） | そのまま通す（765↔763と同じ） |
| 楽器 | 23件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| Entity Metadata の型番号 | 762・763で同一（0=byte、1=int、…、17=particle、18=villager_data、19=optional_unsigned_int、20=pose、21=cat_variant、22=frog_variant、23=optional_global_pos、24=painting_variant、25=sniffer_state、26=vector3、27=quaternion） | variant 系は落とさず通す（765→763の落とし方とは違う。762・763は同じ番号のため） |
| トリム（trim_material／trim_pattern） | 1.19.4の minecraft-data の Join Game サンプルでは空の一覧だが、実バニラ1.19.4はトリムのエントリを送る（763サンプルは10件／16件） | codec は素通しするため表は要らない。実機での確認は未実施 |
| チェリー系ブロック | ViaVersion の 1.19.4 対応表にはチェリー系が載っているが、これは1.19.4の実験的データパック（1.20の内容）のもの。通常の1.19.4サーバー・クライアントには無い | 名前での対応表は通るが、実験的データパック無しの1.19.4では使えない。制限として記録する |

## 762↔763の連鎖の方針

762の変換は **762↔763の隣接リンク1本**だけを作り、あとは既存の鎖に
重ねる。762↔774の直通の翻訳器は作らない。

- クライアント762 → サーバー763／764／765／…／774：`ProxySession.handleClientPlay` の
  先頭で `to763ClientPacket`（Window Click／Set Creative Slot／Update Sign だけ
  762→763へ直す。IDは同一のため番号は変えない）を通してから、以降は763クライアント
  扱いの分岐に任せる。
- サーバー763／764／765／…／774 → クライアント762：各 From ハンドラは762を
  763扱い（`is763Like()`）で763形式のパケットを作り、最後に
  `handleServerPlay` のラッパー `convertServerPlayOutcomeTo762` が
  `translateServerPlayPacket763To762`（27種類の分岐。Join Game／Respawn／
  Advancements／Chunk Data／Block Change／Multi Block Change／Block Entity Data／
  Set Slot／Window Items／Set Cooldown／Entity Equipment／Trade List／Open Window／
  Block Action／Spawn Entity／Entity Metadata／Sound Effect／Entity Sound Effect／
  Stop Sound／World Particles／Entity Effect／Damage Event／Update Light／
  Open Sign Editor／Death Combat Event／End Combat Event）で763→762へ直す。
  それ以外はそのまま通す（762・763で形式が同じため）。
- 内部762サーバー → クライアント：`handleServerPlayRouted` の
  `isServer762()` 分岐で `to763ServerPacket`（上と逆向きの27種類）を通して
  763形式にしてから `handleServerPlayFrom763` に渡す。Join Game は
  `PlayTranslator.translateJoinGame762To763`（portalCooldown=0 を補う）で
  763形式にする。
- クライアント → 内部762サーバー：`handleClientPlay` の `isServer762()`
  分岐で、まずクライアントのパケットを763形式に直し（762／763は既に763形式、
  764／765は `handleClientPlayTo763` で直す）、次に `to762ClientPacket`
  （Window Click／Set Creative Slot／Update Sign だけ763→762へ直す）で送る。
- 762↔762はネイティブで素通し（リレー）。変換をかけてはいけない（かけると
  762形式のパケットが壊れる）。

`is762()`／`is763()`／`is763Like()`（クライアント側）、`isServer762()`／
`isServer763()`／`isServer763Like()`（サーバー側）は `ProxySession` の
private ヘルパー。`is763()` は `is763Like()` の中で使うため残してある。

## 設定フェーズなしクライアントの扱い方（761・760・759担当へ）

762は設定フェーズが無い。763と同じく、**プロキシが設定フェーズを合成・消費する**
方式で扱う。761・760・759も設定フェーズが無いため、この節の手順をそのまま
流用できる。新しいバージョンを追加するときは、まずこの節のチェックリストを
なぞること。

### クライアントが設定フェーズなし・サーバーが設定フェーズあり（762→764〜774）

1. ログイン成功のあと、クライアント側はすぐプレイ状態へ進む。
   サーバー側は設定フェーズに入る。プロキシはサーバー側のクライアント役として
   Login Acknowledged（0x03）と Client Information（0x00）を送る
   （`handleServerLogin` の末尾の分岐。762クライアントも763クライアントも同じ）。
   Client Information の長さは `PlayTranslator.clientInformationPayload()` と同じ。
2. サーバーの設定フェーズのパケットは `handleServerConfiguration` で消費する。
   762クライアントは `is763Like()` の分岐に入り、内部プロトコルごとに
   `handleServerConfiguration763From764`／`763From766`／…／`763From774`／
   `handleServerConfiguration763`（内部765）に分かれる。
   Registry Data（**765は設定フェーズの 0x05**。0x07 は Add Resource Pack。
   0x02 が Finish Configuration、0x03 は Keep Alive。間違えやすいので注意）は
   `PlayTranslator.parseRegistryData` で読んで `registries` に保持し、
   クライアントには送らない。
3. Finish Configuration が来たらサーバー側はプレイ状態へ進む。
   サーバーが Join Game を送ってきたら、保持したレジストリから
   `handleSynthesizedJoinGame` が Join Game の codec を合成するのではなく、
   サーバーの Join Game をクライアント向けに翻訳する（764／765は
   `handleServerJoinGame`、766〜774は各 From ハンドラの Join Game 分岐）。
   762クライアント向けは763形式の Join Game を作り、最後に
   `convertServerPlayOutcomeTo762` が portalCooldown を落として762形式にする。
4. 設定フェーズ中にサーバーから来た Add Resource Pack は `pendingPacks` に
   溜め、Join Game のあとに 0x40（旧式オファー、UUIDなし）で送る
   （`handleServerJoinGame763` の is762() 分岐と汎用 `handleServerJoinGame`）。
   762用パックは `packFor763LikeClient()`（pack762 が無ければ pack763）で選ぶ。

### クライアントが設定フェーズなし・サーバーも設定フェーズなし（762↔763、763↔762）

- ログイン成功のあと、両方ともすぐプレイ状態へ進む。Login Acknowledged も
  Client Information も送ってはいけない
  （`handleServerLogin` の `is763Like() && isServer763Like()` 分岐）。
  送るとサーバーがプレイ状態のパケットと誤読する。
- Join Game はそのまま 0x28 で送る。762サーバーの Join Game は
  `to763ServerPacket` で portalCooldown=0 を補って763形式にしてから
  `handleServerJoinGame763` に渡す。763クライアント向けは763形式のまま、
  762クライアント向けはラッパーが portalCooldown を落とす。

### クライアントが設定フェーズあり・サーバーが設定フェーズなし（764〜774→762）

- ログイン成功のあと、プロキシは設定フェーズを**合成**する
  （`handleServerLogin` の `isServer763Like()` 分岐で `synthesizingConfig = true`、
  サーバー側はプレイ状態のまま）。クライアントの Login Acknowledged
  （`handleClientLogin` の 0x03）はプロキシが消費し、サーバーへは送らない
  （`isServer763Like()` 分岐。送ると762サーバーが誤読する）。
- サーバーが Join Game（762：0x28）を送ってきたら `to763ServerPacket` で
  763形式にしてから `handleSynthesizedJoinGame` が設定フェーズ
  （Registry Data＋Finish Configuration）を合成してクライアントへ送る。
  そのあと Join Game をクライアント向けに翻訳して送る。

### Join Game codec の流用方法（761・760・759担当へ）

- 762・763のレジストリは **Join Game の codec**（名前付き NBT）で送られる。
  設定フェーズの Registry Data ではない。`PlayTranslator.parseJoinGame763`／
  `parseJoinGame762` が codec を読む。`registriesFromJoinGameCodec` が
  codec からレジストリ一覧を作る。
- 762クライアントに送る codec は、**サーバーの codec をそのまま写す**
  （763クライアントへの素通しと同じ扱い）。サーバーが774なら774の codec
  （trim の追加フィールド、damage_type の `effects` フィールド、cherry_grove
  入りの64件のバイオーム一覧など）が762クライアントに届く。Mojang の codec
  は普通は未知のフィールドを無視するが、**本物の762クライアントでの確認は
  未実施**である。761・760・759も同じ扱いになる。
- 762サーバーの codec は `to763ServerPacket` の Join Game 変換でそのまま
  763形式に写す（codec の中身は変えない）。763クライアントへはそのまま、
  764以上のクライアントへは `handleSynthesizedJoinGame` が codec から
  Registry Data を合成する。
- Damage Type の `effects` フィールドは763の codec にはあるが762の codec
  には無い。762サーバーから来た codec には無いため、762→763方向では
  codec に `effects` は付かない。763クライアントは `effects` 無しの
  damage_type を受け取る（Mojang の codec は任意フィールドのはずだが未確認）。

### 新しい設定フェーズなしバージョンを追加するときのチェックリスト

1. `PacketIds` に新列を追加し、763とIDが同じことを確認する
   （`packet-ids-7xx.py` で minecraft-data の protocol.json と突き合わせる）。
2. `ProtocolVersion` に新プロトコルと `serverProtocolFor("x.y.z")` を追加する。
   Java のバージョンを確認する（1.19.4は Java 17。`VanillaServerHost.javaBinaryFor`
   の Java 21 必須リストに入れてはいけない）。
3. `ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL` を更新し、`decide()` と
   `differences()` に新バージョンの分岐を追加する。`differences()` の末尾の
   ガードは**新バージョンと763の両方**を通すこと（762のときに763を弾いて
   NOT_SUPPORTED にする不具合があった。下の「罠」を参照）。
4. `ClientJoinFlow` に新バージョンの設定フェーズなしステージ一覧を追加する。
5. `ProxySession` に `is7xx()` と `is763Like()` への追加をする。
   既存の `is763()` 呼び出しのうち、設定フェーズの合成・消費やパケットIDの
   選択に関するものは `is763Like()` に置き換える（762のときは83か所を機械的に
   置き換えた。`git diff` で名前だけ変わったことを確認すること）。
   ペイロードの変換だけは新バージョン専用の分岐にする。
6. `to763ClientPacket`（クライアント→サーバー）と
   `translateServerPlayPacket763To762`（サーバー→クライアント）の
   対応物を作る。ペイロードが違うパケットだけを列挙し、それ以外は通す。
7. 内部サーバー側も忘れずに配線する：`handleServerPlayRouted` の
   `isServer7xx()` 分岐（`to763ServerPacket` で763形式にしてから
   `handleServerPlayFrom763` へ）、`handleClientPlay` の `isServer7xx()` 分岐
   （763形式にしてから新バージョン形式へ直す）、`handleServerLogin` の
   合成分岐と直行分岐、`handleClientLogin` の Login Acknowledged 消費、
   `completeAuthentication` の Login Start 変換、`ProxySession` コンストラクタの
   serverProtocol 検証リスト。**クライアント側だけ直してサーバー側を忘れると、
   内部762サーバーへの参加が壊れる**（762のときに統合テストで発覚）。
8. `ResourcePackBuilder`／`ResourcePackServer`／`ResourcePackMode`／
   `DetergentServer`／`detergentmc.yml` に新バージョン用パックを追加する。
   `ResourcePackMode.decide()` のオーバーロードは引数が多いため、
   既存テストの呼び出しも `null, null`（762用）でずらすこと
   （762のときに767〜774のテスト32件が壊れた。下の「罠」を参照）。
9. `detergentmc-adapter-v1_19_4` のように新アダプターモジュールを作り、
   `META-INF/services/io.detergentmc.api.VersionAdapter` を置く。
   `BlockType.since()` で新バージョン以降のブロックを落とす。
10. `VanillaDownloader` は piston-meta から動的に解決するため変更不要。
    バニラjarの SHA-1 は piston-meta で確認し、jar は `run/vanilla/` に置いて
    リポジトリには入れない（`.gitignore` 済み）。

## 接続の流れ

### 762クライアント → 内部763サーバー

1. ハンドシェイク（762）→ ログイン（Login Start：名前＋bool＋UUID）。
2. Login Success（0x02）→ 両方ともすぐプレイ状態へ（Login Acknowledged なし）。
3. Join Game（763：0x28、portalCooldown 付き）→ 762（0x28、portalCooldown を落とす）。
4. Spawn Position（0x50）、Keep Alive（0x23→応答 0x12）、Ping（0x32）。
5. チャット（0x05、署名付き完全形式）はそのまま通す。移動（0x14）もそのまま通す。

### 762クライアント → 内部774サーバー

1. ハンドシェイク（762）→ ログイン（Login Start：名前＋bool＋UUID。
   プロキシが bool を落として765形式にして774サーバーへ送る）。
2. Login Success（0x02）→ プロキシが Login Acknowledged（0x03）と
   Client Information（0x00）を774サーバーへ送り、設定フェーズに入る。
   クライアント側はプレイ状態のまま。
3. Registry Data（774：0x07）をプロキシが消費し、内容を保持する。
   Finish Configuration（774：0x03）でサーバー側はプレイ状態へ。
4. Join Game（774：0x30）→ 763形式に翻訳してから762（0x28、portalCooldown を落とす）。
   設定フェーズ中に来た Add Resource Pack は Join Game のあとに 0x40（762用）で送る。

### 763クライアント → 内部762サーバー

1. ハンドシェイク（763）→ ログイン（Login Start：名前＋bool＋UUID。
   内部762サーバーへは bool 付きのまま送る）。
2. Login Success（0x02）→ 両方ともすぐプレイ状態へ（Login Acknowledged なし）。
3. Join Game（762：0x28）→ portalCooldown=0 を補って763形式にしてから
   763クライアントへ 0x28 で送る。

## プレイ中の翻訳

- チャンク：ブロック状態とバイオームを対応表で変換する。762→763では
  decorated_pot の cracked 付き16件を cracked 無しの同 facing に写す。
  763→762では calibrated_sculk_sensor 384件を sculk_sensor の既定状態に写す。
  `trustEdges` は762にだけあるため、763→762で挿し、762→763で落とす。
- ブロックエンティティ：suspicious_sand（762=38）↔brushable_block（763=39）の
  改名と、calibrated_sculk_sensor（763=35）→sculk_sensor（762=34）の代替。
  範囲外だけ落とす（DROP）。
- スロット：NBT形式（762・763とも名前付き NBT）。アイテムIDだけ対応表で
  付け替える。陶器の破片の改名4件に注意（archer／prize／arms_up／skull）。
- Window Click／Set Creative Slot：762↔763で変換する（落とさない）。
  766クライアントが古いサーバーにいるときの「落とす」とは違う。
- Update Sign：isFrontText を付け外しする。
- Entity Metadata：762・763で型番号が同一のため、variant 系も含めて通す。
  765→763の「variant 系を落とす」とは違う。
- 音・パーティクル・エフェクト：対応表で変換する。音は名前での改名を先に見る。
  エフェクトは765が0ベース・762／763が1ベースのため +1 する。
- ダメージイベント：generic_kill・outside_border（763にしか無い）は
  generic（16）へ。範囲外はパケットごと落とす（DROP）。

## 送らない・落とすパケット（762向け）

- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応
  （既存の制限と同じ）。
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす。
- ダメージ種別が範囲外の場合：Damage Event をパケットごと落とす。
- 762に無いパーティクル egg_crack（763=94）：代替なし（0）で送る。
- 762に無いブロック状態・アイテム・音：近い既存のものに代替する（上の表を参照）。
  石・ブタ・plains への代替は範囲外だけ。
- 763→762で cherry_leaves（763=29）は falling_cherry_leaves（762=30）へ。
  762→763で dripping／falling／landing_cherry_leaves はすべて cherry_leaves（763=29）へ。

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全169通り共通。手順書は
  `docs/PLAYTEST-1.19.4.md`。偽クライアント＋本物のバニラでの統合テストは
  4セル（762→762、762→763、762→774、763→762）で参加・スポーン・チャット・
  移動まで確認済み。
- **Death Combat Event／End Combat Event の entityId の代入値**：762→763で
  落とした entityId を、763→762で playerId（Death）／-1（End）で補っている。
  無害な代入だが、本物の762クライアントでの挙動は未確認。
- **Join Game codec の素通し**：774サーバーの codec（trim の追加フィールド、
  damage_type の `effects`、cherry_grove 入りのバイオーム一覧）が762クライアントに
  そのまま届く。Mojang の codec は未知のフィールドを無視するはずだが未確認。
  763クライアントへの素通しと同じ扱い。
- **チェリー系ブロック**：1.19.4の通常のサーバー・クライアントには無い
  （実験的データパックの内容）。対応表は名前で通るが、実験的データパック無しでは使えない。
- **トリムの codec**：1.19.4の minecraft-data サンプルでは空だが、実バニラは
  エントリを送る。codec は素通しのため表は要らないが、実機での確認は未実施。
- **named_entity_spawn（0x03）**：minecraft-data にはあるが、実バニラ1.20.1は
  プレイヤーの出現に送ってこない。実バニラ1.19.4が送るかは未確認。
  2人同時の統合テストはまだ無い。
- **Declare Recipes の smithing 形式の違い**：762の旧式と763の template 付きは
  変換しない（全方向で未対応）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  762＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **外部モードで pack762 が無い場合**：762クライアントには763パック
  （pack_format 15）が出る。本物の1.19.4クライアントが format 15 のパックを
  適用するかは未確認。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrock→762**：`JavaBackendConnection` の serverProtocol 762 を763扱いに
  した（Login Start の bool、Login Success の設定フェーズなし、Join Game の
  portalCooldown=0、Player Info の表示名 JSON）。Bedrock 側の統合テストは
  まだ無い。Bedrock のインベントリは引き続き未対応。

## 762のときに見つかった罠（後続の担当へ）

- **`handleServerPlayFrom763` の765へのフォールスルー**：762クライアント向けに
  Respawn／Advancements／Resource Pack／Disconnect だけ分岐を足しても、
  Chunk Data などは末尾の765分岐に落ちて765形式・765のID（Chunk Data 0x25）で
  出てしまう。ラッパーは765のIDを763の名前で引くため WORLD_EVENT と誤認して
  素通しする。762クライアント向けは `is762()`（のちに `is763Like()` へ拡張）の
  素通し分岐を765分岐の**前**に入れること。同じ罠は `handleClientPlayTo763` にも
  あり、762クライアントの Window Click（0x0B）が765の Configuration
  Acknowledged と誤認されて落とされ、Update Sign（0x2E）が
  Update Command Block Minecart（765の0x2E→763の0x2A）に化け、Set Creative Slot
  （0x2B）が Set Beacon Effect（765の0x2B→763の0x27）に化けた。
  `handleClientPlayTo763` の先頭に `is763Like()` の素通し分岐を入れること。
- **内部762サーバーへの参加はクライアント側だけでは足りない**：
  `handleServerPlayRouted` に `isServer762()` 分岐が無いと、762サーバーの
  Join Game（0x28）が765扱いの `handleServerPlayUp` に落ちて誤読される。
  `handleClientPlay` にも `isServer762()` 分岐が無いと、763クライアントの
  パケットが763→765変換の末尾スイッチに落ちて765形式で762サーバーへ送られる。
  さらに `handleServerLogin` の合成分岐・直行分岐、`handleClientLogin` の
  Login Acknowledged 消費、`completeAuthentication` の Login Start 変換、
  `ProxySession` コンストラクタの serverProtocol 検証リストも762を入れること。
  Login Start の bool は、762サーバーへは落としてはいけない
  （`isServer763()` だけを見ると762サーバーで bool が落ちる）。
- **`ClientAdapter.differences()` の末尾ガード**：`OLDEST_SUPPORTED_CLIENT_PROTOCOL`
  を762に下げると、`clientProtocol != OLDEST` のガードが763を弾いて
  NOT_SUPPORTED にする。末尾の763リストへは762と763の両方を通すこと。
- **`ResourcePackMode.decide()` の引数ずれ**：762の `(url762, sha1762)` 挿入で、
  767〜774のテストの `decide()` 呼び出しが1ペアずれて、767のパックが766の
  枠に入るなど32件が壊れた。オーバーロードの引数を増やすときは既存テストの
  呼び出しも全部ずらすこと（`null, null` を挿す）。
- **765の設定フェーズの番号**：Registry Data は **0x05**、Finish Configuration は
  **0x02**、Keep Alive は 0x03、Add Resource Pack は 0x07。0x07 を Registry Data
  と間違えるとレジストリが保持されず、0x03 を Finish と間違えると Keep Alive が
  返るだけになる。
- **plains のバイオームID**：minecraft-data の biomes.json はアルファベット順のため、
  plains は762=38、763=39。1 ではない。`RegistryRemapper` の
  `PLAINS_BIOME_762`／`PLAINS_BIOME_763` を 1 にすると、チャンクの plains が
  bamboo_jungle（1）になる。
- **calibrated_sculk_sensor は DROP ではない**：ブロックエンティティ種別は
  sculk_sensor（762=34）へ写す。DROP（-1）は範囲外だけ。
- **エフェクトは765が0ベース**：`mapEffect(id765, 762)` は id765+1。Speed は
  765=0→762=1、Slowness は765=1→762=2。762・763同士は1ベースで同一だが、
  765経由の dispatch では +1 が要る。
- **生成器の similarity フォールバック**：`gen-762-tables.py` の表は
  「名前が無ければ石」ではなく、近い既存ブロック・アイテム・音への代替を
  優先する（pitcher_crop→wheat、music_disc_relic→cat、cherry_leaves→
  falling_cherry_leaves など）。テストの期待値を書くときは生成器の
  `BLOCK_FALLBACK_763_TO_762`／`ITEM_FALLBACK_763_TO_762`／
  `SOUND_RENAME_763_TO_762` を読むこと。`verify-762-tables.py` が通ることが
  表の正しさの証拠。
- **765→763の Entity Metadata の型番号の取り違え（既存の不具合・762では直していない）**：
  既存の `translateMetadataValue` は766（1.20.5）の番号で765由来の型18番以降を
  読んでいる。誤読した場合はパケットごと素通しに落ちるため壊れないが、
  18番以降の型は変換されない。762節の `translateMetadataValue762` は
  762／763の正しい番号で書いてあるため、こちらを写さないこと。

## Java 17（内部762サーバーを動かすとき）

1.19.4は **Java 17** で動く。Java 21 は不要。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.19.4は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.19.4` を指定すると、内部762サーバーが Java 17 で起動する。
バニラ1.19.4サーバーjarのSHA-1は `8f3112a1049751cc472ec13e397eade5336ca7ae`
（piston-meta。`run/vanilla/server-1.19.4.jar`）。

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.19.4.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  `PacketIds762Test`（7件）、`ProtocolVersion762Test`（3件）、
  `ClientAdapter762Test`（10件）、`ClientJoinFlow762Test`（4件）、
  `RegistryRemapper762Test`（15件）、`ChunkTranslator762Test`（28件）、
  `PlayTranslator762Test`（11件）、`ProxySession762Test`（15件）、
  `ResourcePackBuilder762Test`（4件）、`ResourcePackMode762Test`（5件）、
  `ResourcePackServer762Test`（2件）。計104件。
- 配線テスト（`detergentmc-core`）：`ServerProtocolWiringTest`（1.19.4→762）、
  `ResourcePackWiringTest`（762パックの配信）、`AdapterDiscoveryTest`（V1194Adapter）。
- 統合テスト（`ProxyIntegrationTest`）：偽762クライアント→本物のバニラ1.19.4
  （762ネイティブ）、偽762クライアント→本物のバニラ1.20.1（763）、
  偽762クライアント→本物のバニラ1.21.11（774）、偽763クライアント→本物の
  バニラ1.19.4（762）。いずれも参加・スポーン・チャット・移動まで確認。
- 既存テストの更新：762を受け入れるようになったため、762拒否のテストは761に
  ずらした（`ClientAdapter.decide(762` → `decide(761` など23ファイル）。
  対応プロトコル一覧の断言には762を先頭に追加した（13ファイル）。
  `ResourcePackMode767Test`〜`ResourcePackMode774Test` の `decide()` 呼び出しは
  762用に `null, null` を挿した（40件）。
