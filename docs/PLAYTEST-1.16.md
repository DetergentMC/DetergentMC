# DetergentMC 1.16.3／1.16.4／1.16.5 実機プレイテスト — チェックリスト

2026-11-16作成。1.16.3クライアント（プロトコル753）と1.16.4／1.16.5クライアント（プロトコル754）で1.16.3・
1.16.4／1.16.5・1.17・1.17.1・1.18・1.18.1・1.19・1.19.1・1.19.2・1.19.3・1.19.4・1.20系・1.21系サーバーに参加する
実機確認のための手順と、結果の記録用。既存の
`PLAYTEST-1.17.md`（1.17クライアント用）・`PLAYTEST-1.17.1.md`
（1.17.1クライアント用）・`PLAYTEST-1.18.md`
（1.18クライアント用）・`PLAYTEST-1.19.md`
（1.19クライアント用）・`PLAYTEST-1.19.1.md`
（1.19.1／1.19.2クライアント用）・`PLAYTEST-1.19.3.md`
（1.19.3クライアント用）・`PLAYTEST-1.19.4.md`
（1.19.4クライアント用）・`PLAYTEST-1.20.1.md`
（1.20.1クライアント用）・`PLAYTEST-1.20.2.md`
（1.20.2クライアント用）・`PLAYTEST-1.20.4.md`
（1.20.4クライアント用）・`PLAYTEST-1.20.5.md`
（1.20.5／1.20.6クライアント用）・`PLAYTEST-1.21.md`
（1.21／1.21.1クライアント用）・`PLAYTEST-1.21.2.md`
（1.21.2／1.21.3クライアント用）・`PLAYTEST-1.21.4.md`
（1.21.4クライアント用）・`PLAYTEST-1.21.5.md`
（1.21.5クライアント用）・`PLAYTEST-1.21.6.md`
（1.21.6クライアント用）・`PLAYTEST-1.21.7.md`
（1.21.7／1.21.8クライアント用）・`PLAYTEST-1.21.9.md`
（1.21.9／1.21.10クライアント用）・`PLAYTEST-1.21.11.md`
（1.21.11クライアント用）・`PLAYTEST-26.1.md`
（26.1クライアント用）・`PLAYTEST-26.3.md`
（26.3クライアント用）は直接いじらず、
こちらを1.16.3／1.16.4／1.16.5専用の手順書とする。
1.16.4と1.16.5はどちらもプロトコル754で、クライアント側の扱いは同じである。
1.16.3はプロトコル753で、754とパケットID・レジストリ・Join Game codec がすべて同一のため、
クライアント側の扱いは754と同じである（753⇔754の翻訳子はすべて恒等写像。`docs/PROXY-1.16.md` の「753↔754の差分」を参照）。

## 0. この手順書の読み方：プロトコル検証済み／実機でしか確認できないもの

DetergentMCの1.16.5対応は、ここに書く項目の多くを**プロトコルレベルでは
すでに検証済み**である（本物のバニラ子プロセス＋生ソケットで754クライアントを
装ったキャプチャ、`ChunkTranslator754Test`、`PlayTranslator754Test`、
`ProxySession754Test` など）。実機テストの目的は、プロトコルレベルでは
見えない「本物のゲームクライアントでの見た目・挙動」を確かめることである。
以下の2つに分けて読むこと。

### A. プロトコルレベルで既に検証済みの項目（実機では「同じことが起きるか」の確認）

- `verify-754-tables.py` は43件中43件通過（パケットID差分・レジストリ差分の表。
  A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）
- パケットID差分（755↔754）：**変更120件・新規0件・削除1件**
  （`PLAY_SCULK_VIBRATION_SIGNAL` の削除1件。754では -1）。
  `PacketIds.java` のエントリ総数272件、id754>=0 は159件
  （HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 102件＋
  PLAY サーバー行き 48件。CONFIGURATION は0件）。
  minecraft-data・ViaVersion・Wiki の三重照合で不一致0件
- レジストリ差分（755↔754）：ブロック状態 20,342→17,112件、
  ブロック 898→763件、アイテム 1,100→976件、
  エンティティ 113→108件、音 1,190→992件、
  パーティクル 89→72件、バイオーム 81→79件、
  ブロックエンティティ 34→33件。メニュー24件・属性13件・エンチャント38件・
  エフェクト32件は同一順序で変化なし。
  改名は grass_path→dirt_path（ブロック・アイテム）と
  block.sweet_berry_bush.pick_berries→item.sweet_berries.pick_from_bush（音）の2件。
  補正はブロック状態46件・アイテム5件・音35件の計86件
- フィールド差分は実質19パケット：Join Game（codec中身のみ）、Respawn（dimension elementのみ）、
  Client Information（disableTextFiltering有無）、Synchronize Player Position（dismountVehicle有無）、
  combat_event→3分割、title→5分割、world_border→6分割、ping／pong⇔transaction、
  entity_destroy（配列⇔単体）、window_click、map_chunk、update_light、explosion（count i32→varint）、
  entity_update_attributes（count i32→varint）、map（trackingPosition有無）、declare_recipes（ワイヤ同一のため変換器不要）、
  tags（4固定→汎用配列＋game_event）、resource_pack_send（forced＋promptMessage追加）、sculk（削除）
- Join Game codec は755とフィールド構成が同一（simulationDistance はどちらにも無く、
  lastDeathLocation もどちらにも無い）。dimensionCodec は dimension_type 4件・
  バイオーム79件（id 0〜173の非連番）で、755は81件（id 0〜175）。
  dimension element（overworld）には754は min_y／height が無く、755は min_y 0・height 256 がある
- 実バニラ1.16.5の Join Game 30,847バイトの生キャプチャ。
  フィールド15件を全てパースし、消費量 30,847／30,847 で完全一致
  （entityId 217、viewDistance 10、maxPlayers 5）
- 実バニラ1.16.5のチャンク生キャプチャ（チャンク-15,-7、10,633バイト）の
  往復はバイト完全一致（`cap754-chunk-x-15-z-7.bin`。754→755→754）
- 新規・書き換え754テスト **128件全通過**（Stage B）：`PlayTranslator754Test`（27件）、
  `ChunkTranslator754Test`（16件）、`ChunkTranslator754ValueTest`（30件）、
  `ProxySession754Test`（11件）、`ProxySession754bTest`（16件）、
  `ClientAdapter754Test`（13件）、`ClientJoinFlow754Test`（5件）、
  `ClientProxy754Test`（4件）、`V1165AdapterTest`（6件）。
  Stage Aの新規754テスト26件も全通過。
  全体回帰は2,624件通過・30件失敗（失敗は全て環境制約で、754が原因の
  本物の回帰は0件）
- 754用リソースパック（pack_format 6、平文 pack.mcmeta）の生成と配信
  （`ResourcePackBuilder754Test` 3件、`ResourcePackServer754Test` 2件、
  `ResourcePackMode754Test` 6件）。**754パックと755パックは内容が違い、sha1 が異なる**
  （754が `89773dc5252afd72e4a662a5df8abc97d2554af0`、755が `74d9d34d0dd377a5a9b0dfe0e39d968d027a356b`）
- 実バニラ1.16.5の起動確認に成功（`Done (62.596s)!`、pack metadataエラー0件）
- 754サーバー→759クライアントの Set Slot／Window Items／Chunk Data／Entity Destroy は届く
  （`ProxySession754Test.serverPackets754Against759Reach` で確認）
- 754サーバー→755クライアントの Join Game／Respawn は通るが、chat／Set Slot／
  Window Items／Entity Destroy は汎用経路で落とされる
  （`ProxySession754Test.joinAndRespawn754Against755Convert` で確認）
- 754クライアント→755サーバーには参加できる（Join Game が通る。
  `ProxySession754Test.directPackets754Against755Convert` で確認）
- 754クライアント→756〜775サーバーには参加できない（Join Game／Respawn が
  落とされる。775は `ProxySession754Test.join775Against754Drops` で確認。
  `ProxySession.java:7364`／`:8335`／`:8629` で確認）

### A-2. プロトコルレベルで既に検証済みの項目（753。実機では「同じことが起きるか」の確認）

- `verify-753-tables.py` は43件中43件通過（本稿作成時に再実行して確認。
  A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）
- パケットID差分（754↔753）：**変更0件・新規0件・削除0件**
  （`PacketIds.java` の753列は全272件で754列と同一。packet-ids-753.json との全件照合で不一致0件。
  id753>=0 は159件。minecraft-data・ViaVersion・Wiki の三重照合で不一致0件）
- レジストリ差分（754↔753）：**差分0件**（実バニラ1.16.3 jarの data-generator レポートは
  1.16.5版とバイト単位で完全一致。ブロック状態17,112件・ブロック763件・アイテム976件・
  エンティティ108件・音992件・パーティクル72件・バイオーム79件・ブロックエンティティ33件。
  メニュー24件・属性13件・エンチャント38件・エフェクト32件は同一順序で変化なし。改名なし。
  補正は全種0件）
- Join Game codec は754と同一（LoginPacket は `px` で同じ15フィールド、RespawnPacket は `qp` で同じ8フィールド。
  previousGameMode は u8 0xFF=255。dimensionCodec は dimension_type 4件・バイオーム79件（id 0〜173）。
  dimension element（overworld）に min_y／height は無し）
- 実バニラ1.16.3の Join Game 30,847バイトの生キャプチャ。
  フィールド15件を全てパースし、消費量 30,847／30,847 で完全一致
  （entityId 109／204、viewDistance 10、maxPlayers 5）
- 実バニラ1.16.3のチャンク生キャプチャ（Chunk Data 0x20を482件受信。Stage A §11.6）
- 新規・書き換え753テスト **79件全通過**（Stage B）：`PlayTranslator753Test`（12件）、
  `ChunkTranslator753Test`（12件）、`ProxySession753Test`（11件）、`ProxySession753bTest`（16件）、
  `ClientAdapter753Test`（13件）、`ClientJoinFlow753Test`（5件）、`ClientProxy753Test`（4件）、
  `V1163AdapterTest`（6件）。Stage Aの新規753テスト26件も全通過。
  全体回帰は2,726件通過・31件失敗（失敗は全て環境制約で、753が原因の
  本物の回帰は0件。旧期待値のずれは0件）
- 753用リソースパック（pack_format 6、平文 pack.mcmeta）の生成と配信
  （`ResourcePackBuilder753Test` 3件、`ResourcePackServer753Test` 2件、
  `ResourcePackMode753Test` 6件）。**753パックと754パックは内容が違い、sha1 が異なる**
  （753が `0adcecd8395d8e33fc7fa6e1d10a4835e9fa9c35`、754が `89773dc5252afd72e4a662a5df8abc97d2554af0`）
- 実バニラ1.16.3の起動確認に成功（`Done (73.036s)!`、pack metadataエラー0件）
- 753サーバー→759クライアントの Set Slot／Window Items／Chunk Data／Entity Destroy は届く
  （`ProxySession753Test.serverPackets753Against759Reach` で確認。ただし Entity Destroy は count 1 のときだけ）
- 753サーバー→754クライアントの Join Game／Respawn は通るが、chat／Set Slot／
  Window Items／Entity Destroy は汎用経路で落とされる
  （`ProxySession753Test.joinAndRespawn753Against754Convert` で確認）
- 753サーバー→755クライアントの Join Game／Respawn／Spawn Position は通る
  （`ProxySession753bTest.joinGame753To755RewritesCodec`／`respawn753To755RewritesDimension`／
  `position753To755AddsDismountVehicle` で確認。Join Game 0x24→0x26、Respawn 0x39→0x3D、Spawn Position 0x34→0x38）
- 753クライアント→754サーバーには参加できる（Join Game が通る。
  `ProxySession753Test.directPackets753Against754Convert` で確認）
- 753クライアント→755サーバーには参加できる（Join Game が通る。コード確認。
  `handleServerJoinGame755` の `if (is754() || is753())` 分岐。753クライアント×755サーバーの
  直接の単体テストは無いため、テスト確認済みとは書かない）
- 753クライアント→756〜775サーバーには参加できない（Join Game／Respawn が
  落とされる。775は `ProxySession753Test.join775Against753Drops` で確認。
  756／757／759〜774は同じガードに到達することをコードで確認したが、各サーバーでの
  直接テストは無いため推論）

### B. 実機でしか確認できない項目（この手順書の中心）

- 本物の1.16.5クライアントが、1.16.5サーバーに参加できるか
  （プロトコルレベルでは偽クライアントしか使っていない）
- 本物の1.16.5クライアントが、1.17サーバーに参加できること
  （Join Game が754形式に直されて届くため。下の「4.」を参照。
  **755サーバーへの参加は「できる」側である。756・755の手順書と向きが違うため注意**）
- 本物の1.16.5クライアントが、1.17.1サーバーに参加できないこと
  （Join Game が落とされるため。バグではない。下の「4.」を参照。
  **確認するのは「参加できないこと自体」である**）
- 1.16.5クライアントで1.17サーバーの stateId 付きウィンドウ操作が
  どう見えるか（参加できるため、この組み合わせでは確認できる。
  逆向き＝1.17クライアントが1.16.5サーバーに参加する「5.」で確かめること）
- 1.16.5サーバー側の本（Edit Book）が1.17クライアントでどう見えるか
  （754⇔755ではフィールド同一のため素通し＋ID付け替え。実機で確かめること）
- 1.16.5クライアントに754用パック（pack_format 6、平文 pack.mcmeta）が
  提示され、適用されるか（実バニラjarのjavapによる根拠のみで、
  実機での受け入れ確認は未実施）
- **2人同時参加（他プレイヤーの出現・スキン・チャット）**。
  偽クライアントでは、直接接続でもプロキシ経由でも、他プレイヤーの
  出現パケットが30秒以内に届かなかった（1.19.3・1.19.1・1.18と同じ）。
  **実機クライアントで2人同時に遊び、相手が見えるかどうかを確かめるのが
  最も重要な残りの検証である**。相手が見えない場合は、ログの文言と一緒に
  報告すること
- 756サーバーに754クライアントが参加できないこと
  （Join Game／Respawn が落とされるため。バグではない。下の「4.」を参照）
- 754サーバーに774クライアントが参加したとき、Join Game は通るはずだが
  Respawn が落とされるはずのこと（**推論**。754サーバーでの直接テストは無い。
  死亡後のリスポーンで確かめること。下の「5.」を参照）
- 754サーバーに763〜765クライアントが参加したとき、Join Game／Respawn とも通るはずのこと
  （**推論**。下の「5.」を参照）。766以上のクライアントでは Join Game は通るはずだが Respawn が
  落とされるはずのこと（**推論**。下の「5.」を参照）
- 754サーバーに755クライアントが参加したとき、チャット・Set Slot・Window Items・
  Entity Destroy が届かないこと（汎用経路で落とされるため。下の「5.」を参照）
- 1.16.5クライアントで1.16.5サーバーのチャンクがどう見えるか
  （754は groundUp＋bitMap＋バイオーム varint[1024]（groundUpのときだけ）。光なし）

### B-2. 実機でしか確認できない項目（753。1.16.3クライアント中心）

- 本物の1.16.3クライアントが、1.16.3サーバーに参加できるか
  （プロトコルレベルでは偽クライアントしか使っていない）
- 本物の1.16.3クライアントが、1.16.5サーバーに参加できること
  （753⇔754は恒等写像のため。`directPackets753Against754Convert` で確認）
- 本物の1.16.3クライアントが、1.17サーバーに参加できること
  （Join Game が754形式に直されて届くため。下の「4.」を参照。
  **755サーバーへの参加は「できる」側である。756・755の手順書と向きが違うため注意**）
- 本物の1.16.3クライアントが、1.17.1サーバーに参加できないこと
  （Join Game が落とされるため。バグではない。下の「4.」を参照。
  **確認するのは「参加できないこと自体」である**）
- 1.16.3クライアントで1.16.3サーバーのチャンクがどう見えるか
  （753は754と同じ形式。groundUp＋bitMap＋バイオーム varint[1024]（groundUpのときだけ）。光なし）
- 1.16.3クライアントに753用パック（pack_format 6、平文 pack.mcmeta）が
  提示され、適用されるか（実バニラjarのjavapによる根拠のみで、
  実機での受け入れ確認は未実施。753パックと754パックは内容が違うため、1.16.3クライアントには
  753用パックが出ることを確かめること）
- 753サーバーに755クライアントが参加したとき、チャット・Set Slot・Window Items・
  Entity Destroy が届かない見込みのこと（**推論**。754サーバーでの同型の確認からの類推で、
  **753サーバーでの直接テストは無いため推論**。下の「5.」を参照）
- 753サーバーに774クライアントが参加したとき、Join Game は通るはずだが
  Respawn が落とされるはずのこと（**推論**。753サーバーでの直接テストは無い。
  死亡後のリスポーンで確かめること。下の「5.」を参照）

## 1. 起動

### 1.1 準備（初回のみ）

```sh
cd ~/workspace/detergentmc-753
./gradlew build :bubbles:hello-bubble:jar
cp bubbles/hello-bubble/build/libs/hello-bubble-0.1.0-1.20.4.jar run/bubbles/
```

`run/eula.txt` と `run/vanilla/eula.txt` が `eula=true` になっていること。

### 1.2 起動（ファットjar・推奨）

```sh
cd ~/workspace/detergentmc-753
JAVA_HOME=/home/hatch/workspace/.tools/jdk-17.0.21+9 \
  java -jar build/libs/detergentmc-0.1.0-1.20.4.jar
```

`detergentmc.yml` の `minecraft-version` を `1.16.3` にして起動すると、
内部753サーバーが Java 8 で起動する。`1.16.5` にすると内部754サーバーが Java 8 で起動し、
`1.16.4` にしても同じ内部754サーバー
（world_version だけが2584と2586で違う）。`1.17` にすると内部755サーバー、
`1.17.1` にすると内部756サーバー、`1.18`／`1.18.1` にすると内部757サーバー、
`1.19` にすると内部759サーバー、`1.19.1`／`1.19.2` にすると内部760サーバー、
`1.19.3` にすると内部761サーバー、`1.19.4` にすると内部762サーバー、
`1.20.1` にすると内部763サーバーになる（切り替えて試すこと）。
**1.16.5の内部サーバーは Java 8 で動く**（Temurin 1.8.0_512-b09、
`~/.tools/jdk8u512-b09`）。**1.17の内部サーバーは Java 16 で動く**。
Java 21／Java 25 は1.16.5・1.17・1.17.1・1.18・1.19系には要らない。
注意：DetergentMC 自体（プロキシ）は JDK 17 で動かす（core／protocolに
sealedクラスがありJDK 8ではコンパイル・実行不可）。内部1.16.5サーバーだけが
Java 8で起動する（`javaBinaryFor("1.16.5")` が返すバイナリは
`/home/hatch/workspace/.int754d-p3/fake-jdk8-home/bin/java`）。
**罠：ドライバをJDK 17＋`-Djava.home=$HOME/.tools/jdk8u512-b09` で起動する
755の段取りは JDK 8では動かない**（JDK 17はJVM起動時に `$java.home/conf/security` を読むが
JDK 8のhomeには無く即死する。ノート §15.3.5。fake home を使うこと）。

### 1.3 接続（実機クライアント）

Minecraft Java Edition 1.16.3（または1.16.4／1.16.5）のクライアントで `127.0.0.1:25565` に接続する。
オフラインモード（`online-mode: false`）の場合は、ランチャーのプロファイルで
オフラインモードを有効にするか、適当な名前で接続すること。

### 1.4 ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。**クライアントは必ずここに接続する** |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`127.0.0.1` のみ。直接つながないこと） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。753用・754用・755用・756用・757用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の22つを出す（758用・776用は無い） |

起動時に22つのパックが生成される：

| ファイル | 中身 |
| --- | --- |
| `run/data/resource-pack/detergentmc-pack-1.16.3.zip` | 753用（pack_format 6、1.16.3専用）。プロキシが753クライアントにだけ出す。**754用パックと内容が違い、sha1 が異なる** |

| ファイル | 中身 |
| --- | --- |
| `run/data/resource-pack/detergentmc-pack-1.16.5.zip` | 754用（pack_format 6、1.16.4と1.16.5で共用）。プロキシが754クライアントにだけ出す。**755用パックと内容が違い、sha1 が異なる** |
| `run/data/resource-pack/detergentmc-pack-1.17.zip` | 755用（pack_format 7、1.17専用）。プロキシが755クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.17.1.zip` | 756用（pack_format 7、1.17.1専用）。プロキシが756クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.18.zip` | 757用（pack_format 8、1.18と1.18.1で共用）。プロキシが757クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.19.zip` | 759用（pack_format 9、1.19専用）。プロキシが759クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.19.1.zip` | 760用（pack_format 9、1.19.1と1.19.2で共用）。プロキシが760クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.19.3.zip` | 761用（pack_format 12、1.19.3専用）。プロキシが761クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.19.4.zip` | 762用（pack_format 13、1.19.4専用）。プロキシが762クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack.zip` | 765用（pack_format 22）。`server.properties` の `resource-pack` に書かれる（内部765のとき） |
| `run/data/resource-pack/detergentmc-pack-1.20.1.zip` | 763用（pack_format 15）。プロキシが763クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.20.2.zip` | 764用（pack_format 18）。プロキシが764クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.20.5.zip` | 766用（pack_format 32、1.20.5と1.20.6で共用）。プロキシが766クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.21.zip` | 767用（pack_format 34、1.21と1.21.1で共用）。プロキシが767クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.21.2.zip` | 768用（pack_format 42、1.21.2と1.21.3で共用）。プロキシが768クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.21.4.zip` | 769用（pack_format 46、1.21.4専用）。プロキシが769クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.21.5.zip` | 770用（pack_format 55、1.21.5専用）。プロキシが770クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.21.6.zip` | 771用（pack_format 63、1.21.6専用）。プロキシが771クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.21.7.zip` | 772用（pack_format 64、1.21.7と1.21.8で共用）。プロキシが772クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.21.9.zip` | 773用（pack_format 69、1.21.9と1.21.10で共用）。プロキシが773クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.21.11.zip` | 774用（pack_format 75、1.21.11専用）。プロキシが774クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-26.1.zip` | 775用（pack_format 84、26.1と26.1.1と26.1.2で共用）。プロキシが775クライアントにだけ出す |

コンソールに `リソースパックを配信しています: http://127.0.0.1:25567/detergentmc-pack.zip`
が出れば配信開始。753用パックも同じポートの
`http://127.0.0.1:25567/detergentmc-pack-1.16.3.zip` で配信される。
754用パックも同じポートの
`http://127.0.0.1:25567/detergentmc-pack-1.16.5.zip` で配信される。
753用・754用パックの pack.mcmeta は平文 `{"pack_format":6}` で、
777の min_format／max_format 形式ではない（1.16.5には存在しない。
`docs/PROXY-1.16.md` を参照）。

### 1.5 online-mode／offline-modeの切替方法

`run/detergentmc.yml` の `server.online-mode` を変える。既定は `true`
（プロキシがMojang認証を完結させる）。実機で正規アカウントを使わない
確認（オフラインの動作確認だけ）なら `false` にする。内部バニラは常に
offline-mode（信頼済み接続）で動くため、ここの切替はプロキシ認証の
オン／オフである。

```yaml
server:
  online-mode: false
```

### 1.6 サーバーを別バージョンで動かす場合

`run/detergentmc.yml` の `minecraft-version` を変えて再起動する。
内部プロトコル・データパック形式・バニラに渡すリソースパックが連動して変わる。
**1.16.5クライアントが参加できるのは 1.16.3（753）と 1.16.5（754）と 1.17（755）の3行だけ**
（756〜775は Join Game が落とされるため参加できない。下の「4.」「7.」を参照）。
**1.16.3クライアントが参加できるのも同じ3行だけ**（753・754・755）。

| `minecraft-version` | 内部プロトコル | データパック | バニラに渡すリソースパック | 注意 |
| --- | --- | --- | --- | --- |
| `1.16.3` | 753 | 6 | 753用（pack_format 6） | 1.16.3クライアントが参加できる（素通し）。**Java 8** |
| `1.16.4` / `1.16.5` | 754 | 6 | 754用（pack_format 6） | 1.16.5クライアントが参加できる（素通し）。**Java 8** |
| `1.17` | 755 | 7 | 755用（pack_format 7） | 1.16.5クライアントが参加できる（754形式に直して返す）。**Java 16** |
| `1.17.1` | 756 | 7 | 756用（pack_format 7） | 1.16.3／1.16.5クライアントは参加できない（Join Game／Respawn が落とされる） |
| `1.18` / `1.18.1` | 757 | 8 | 757用（pack_format 8） | 同上（参加できない） |
| `1.19` | 759 | 10 | 759用（pack_format 9） | 同上（参加できない） |
| `1.19.1` / `1.19.2` | 760 | 10 | 760用（pack_format 9） | 同上（参加できない） |
| `1.19.3` | 761 | 10 | 761用（pack_format 12） | 同上（参加できない） |
| `1.19.4` | 762 | 12 | 762用（pack_format 13） | 同上（参加できない） |
| `1.20` / `1.20.1` | 763 | 15 | 763用（pack_format 15） | 同上（参加できない） |
| `1.20.2` | 764 | 18 | 764用（pack_format 18） | 同上（参加できない） |
| `1.20.3` / `1.20.4` | 765 | 26 | 765用（pack_format 22） | 同上（参加できない） |
| `1.20.5` / `1.20.6` | 766 | 41 | 766用（pack_format 32） | 同上（参加できない）。**Java 21 が必要** |
| `1.21` / `1.21.1` | 767 | 48 | 767用（pack_format 34） | 同上（参加できない）。**Java 21 が必要** |
| `1.21.2` / `1.21.3` | 768 | 57 | 768用（pack_format 42） | 同上（参加できない）。**Java 21 が必要** |
| `1.21.4` | 769 | 61 | 769用（pack_format 46） | 同上（参加できない）。**Java 21 が必要** |
| `1.21.5` | 770 | 71 | 770用（pack_format 55） | 同上（参加できない）。**Java 21 が必要** |
| `1.21.6` | 771 | 80 | 771用（pack_format 63） | 同上（参加できない）。**Java 21 が必要** |
| `1.21.7` / `1.21.8` | 772 | 81 | 772用（pack_format 64） | 同上（参加できない）。**Java 21 が必要** |
| `1.21.9` / `1.21.10` | 773 | 88 | 773用（pack_format 69） | 同上（参加できない）。**Java 21 が必要** |
| `1.21.11` | 774 | 94 | 774用（pack_format 75） | 同上（参加できない）。**Java 21 が必要** |
| `26.1` / `26.1.1` / `26.1.2` | 775 | 101 | 775用（pack_format 84） | 同上（参加できない）。**Java 25 が必要** |

## 2. 参加（1.16.5クライアント）

1.16.5の本物のゲームクライアントで、プロキシの公開ポート
（既定25565）に接続する。サーバーリストには **1.16.3**（protocol 753）／**1.16.5**（protocol 754）と
表示されるはずである。

参加直後にリソースパックの提示が出る（753用／754用パック pack_format 6）。
「はい」を選ぶとパックが適用される。パックの提示が出ない場合は、
`resource-pack.enabled` が `true` になっていることと、25567番ポートの
配信を確認すること。

## 3. チェックリスト（1.16.3／1.16.5クライアント → 内部753／754サーバー）

`minecraft-version` を `1.16.3` にして起動し、1.16.3クライアントで接続する。
素通し（リレー）のため、1.16.3の世界がそのまま見えるはず。
`1.16.5` にして1.16.5クライアントで接続しても同じ（754同士の素通し）。
1.16.3クライアントで1.16.5サーバーに接続しても、753⇔754は恒等写像のため同じ世界が見えるはず。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 地面・木・水などの見た目が正しい（ブロック状態の変換が正しい）
- [ ] 移動できる（歩く・走る・泳ぐ・ジャンプ）
- [ ] チャットができる（`/trigger d_hello` で挨拶が出る。直接 `/hello` は動かない）
- [ ] `/trigger d_lang_en` で英語に切り替わる。`/trigger d_lang_ja` で日本語に戻る
- [ ] リソースパックが提示される（pack_format 6）。適用される
- [ ] インベントリを開ける。アイテムを動かせる（Window Click の変換）
- [ ] クリエイティブでアイテムを取れる（Set Creative Slot の変換）
- [ ] 看板を立てて文字を書ける（Update Sign の変換）
- [ ] 死んでリスポーンできる（Respawn の変換）
- [ ] 村人と取引できる（Trade List の変換）
- [ ] スカルクセンサーが無いこと（1.17で追加されたもの。1.16.5には無い）
- [ ] 1.16.3クライアントで1.16.5サーバーに参加できる（753⇔754は恒等写像。`directPackets753Against754Convert` で確認）
- [ ] 1.16.3クライアントで1.16.3サーバーに参加できる（素通し。`join753Against753IsNative` で確認）
- [ ] 2人目が参加したとき、相手が見える（Player Info・出現パケット）。
  **ここが最重要の未確認項目**。相手が見えない・相手が馬に見える・
  姿がおかしい場合は、そのままの様子を報告すること
- [ ] 本に文字を書けるか（Edit Book。1.16.5形式 {new_book, signing, hand}。
  変換器は無く、1.17クライアントから書いた本がどう見えるかも含めて確かめること。
  下の「5.」も参照）
- [ ] エンティティが消える場面（モブのデスポーン等）でおかしな挙動が無いか
  （Entity Destroy は754は [count][entityIds] 配列のため）

## 4. 1.16.3／1.16.5クライアント → 内部753／755／756／757／759〜775サーバー

`minecraft-version` を `1.16.3`／`1.17`／`1.17.1`／`1.18`／`1.19`／`1.19.1`／`1.19.3`／`1.19.4`／
`1.20.1`／`1.20.2`／`1.20.4`／`1.20.5`／`1.21`／`1.21.11`／`26.1` にして起動し、
1.16.3クライアントで接続する。**1.16.3（753）・1.16.5（754）・1.17（755）に参加でき、756〜775は参加できない**。
753サーバーは Join Game をそのまま返す（恒等写像）。755サーバーは Join Game を754形式に直して返す（`docs/PROXY-1.16.md` の対応表を参照）。
756〜775サーバーはサーバー→クライアントの Join Game／Respawn が落とされるため
（756サーバーは756→757→759→763の連鎖を経て
`handleServerJoinGame763` の is754() 分岐で落とされる。757〜775サーバーも同じガードに
到達する。バグではない。`docs/VERSIONS.md` の1.16マトリクスを参照。
マトリクスは Stage E で追加中）。
**確認するのは「755には参加でき、756以上には参加できないこと自体」である。
参加できる前提のチェックリストにしないこと。**

- [ ] 1.16.3サーバーに参加できる（Join Game が届き、スポーン地点に立つ。素通し）
- [ ] 1.16.5サーバーに参加できる（Join Game が届き、スポーン地点に立つ。753⇔754は恒等写像）
- [ ] 1.17サーバーに参加できる（Join Game が届き、スポーン地点に立つ）
- [ ] 1.17サーバーでチャット・インベントリ・リスポーンができるか
  （754→755の直接変換。Window Click は754→755→756→757→759の連鎖で759の0x0Aに届く。
  実機で確かめること）
- [ ] 1.17サーバーで1.17の新規要素（スカルクセンサー、グロウベリー、ウーパールーパー、
  ヤギ、輝くイカ、発光する額縁）がどう見えるか
  （754クライアントには無い要素。代替表で付け替えられるが、実機で確かめること）
- [ ] 1.17.1以降のサーバーに接続しても Join Game が届かず、参加できない（切断されるか、ローディングのまま進まない）
- [ ] プロキシログに Join Game の落としが記録されるか
- [ ] （参考）1.17.1以降の新規要素（music_disc_otherside、block_marker、
  meadow・grove・snowy_slopes・frozen_peaks・jagged_peaks・stony_peaks、
  マングローブ・泥・スカルク・フロッグライト・アレイ・カエル・ウォーデン・
  竹・吊り看板・ラクダ・桜・スニッファーといった1.19系の新規要素）は、
  753／754クライアントからはこの組み合わせでは確認できない（参加できないため）。
  新規要素の見え方は、754サーバーに新しいクライアントで参加する「5.」の逆向きで確かめること
- [ ] 他プレイヤーのチャット（Player Chat 759→754は落とす）も、参加できない
  ためこの組み合わせでは確認できない。翻訳子としての落としは単体テストで確認済み

## 5. チェックリスト（1.17／1.17.1／1.18／1.19／1.20.1／1.21.11クライアント → 内部753／754サーバー）

`minecraft-version` を `1.16.3`／`1.16.5` にして起動し、1.17／1.17.1／1.18／1.19／
1.20.1／1.21.11クライアントで接続する。逆向き（新しいクライアントが
古いサーバーに参加する）の確認。**方向を逆に書かないこと。**
新しいクライアント×古いサーバーは Join Game が通るが、Respawn と汎用パケットに制限がある。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 1.16.5の世界が正しく見えるか（ブロック状態・バイオームの逆向き変換。1.17の新規ブロックは代替表で付け替わる）
- [ ] 1.17クライアントで1.16.5サーバーのチャットが見えるか
  （**見えないはず**。754サーバー→755クライアントの chat は汎用経路で落とされる。
  `ProxySession754Test.joinAndRespawn754Against755Convert` で確認。
  サーバーからのお知らせも同じく届かない。実機で確かめること）
- [ ] 1.17クライアントで自分のチャットがサーバーに届くか
  （届くはず。クライアント→サーバーは変換する。Chat Message 0x04→chat 0x03。
  `ProxySession754Test.clientPackets759Against754Convert` で確認）
- [ ] 1.17クライアントでインベントリのクリックができるか
  （**できないはず**。754サーバー→755クライアントの Set Slot／Window Items は
  汎用経路で落とされる。変換器はあるが呼び出し口が無い。実機で確かめること）
- [ ] 1.17クライアントでエンティティの消滅が正しく見えるか
  （**おかしくなるはず**。754サーバー→755クライアントの Entity Destroy は
  汎用経路で落とされる。モブが消えずに残って見える可能性がある。実機で確かめること）
- [ ] 1.17クライアントで死んでリスポーンできるか
  （できるはず。Respawn は通る。`ProxySession754Test.joinAndRespawn754Against755Convert` で確認）
- [ ] 1.19クライアントで1.16.5サーバーの Set Slot／Window Items／Chunk Data／Entity Destroy が届くか
  （届くはず。`ProxySession754Test.serverPackets754Against759Reach` で確認。
  ただし Entity Destroy は count 1 のときだけ。count 2 は落とされる。実機で確かめること）
- [ ] 1.17クライアントで1.16.3サーバーの Join Game／Respawn／Spawn Position が届くか
  （届くはず。`ProxySession753bTest.joinGame753To755RewritesCodec`／`respawn753To755RewritesDimension`／
  `position753To755AddsDismountVehicle` で確認。実機で確かめること）
- [ ] 1.17クライアントで1.16.3サーバーのチャットが見えるか
  （**見えないはず**。**推論**。754サーバーでは `joinAndRespawn754Against755Convert` で確認。
  **753サーバーでの直接テストは無いため推論**。実機で確かめること）
- [ ] 1.17クライアントで1.16.3サーバーのインベントリのクリックができるか
  （**できないはず**。**推論**。754サーバーでは `joinAndRespawn754Against755Convert` で確認。
  **753サーバーでの直接テストは無いため推論**。実機で確かめること）
- [ ] 1.19クライアントで1.16.3サーバーの Set Slot／Window Items／Chunk Data／Entity Destroy が届くか
  （届くはず。`ProxySession753Test.serverPackets753Against759Reach` で確認。
  ただし Entity Destroy は count 1 のときだけ。count 2 は落とされる。実機で確かめること）
- [ ] 774クライアントで死んでリスポーンするとどうなるか
  （Respawn は落とされるはず。**推論**。755サーバーでは `join774Against755ConvertsJoinGameButDropsRespawn` で確認。
  **754／753サーバーでの直接テストは無いため推論**。実機で確かめること）
- [ ] 763〜765クライアントで死んでリスポーンできるか
  （できるはず。**推論**。755サーバーでの同型の確認からの類推で、**754／753サーバーでの直接テストは無いため推論**。
  実機で確かめること）
- [ ] 766以上のクライアントで死んでリスポーンするとどうなるか
  （Respawn は落とされるはず。**推論**。774は755サーバーでテスト確認、766／767／768／769／770／771／772／773／775は
  落としリストに載っていることをコードで確認。**754／753サーバーでの直接テストは無いため推論**。
  実機で確かめること）
- [ ] 763以上のクライアントでインベントリのクリックができるか
  （できないはず。Window Click／Set Creative Slot／Edit Book の754行きは未実装のため落とす。
  `ProxySession754Test.clientPackets763Against754Convert` で確認）
- [ ] 1.17クライアントで本に文字を書けるか
  （できるはず。Edit Book は754⇔755ではフィールド同一のため素通し＋ID付け替え。
  `ProxySession754Test.directPackets754Against755Convert` で確認。実機で確かめること）
- [ ] 2人目が参加したとき、相手が見えるか

## 6. わざと失敗するはずのもの（バグではない）

- 1.16.5クライアントで1.17.1〜26.1（756〜775）のサーバーに参加できない
  （756〜775→754の Join Game／Respawn が落とされるため）
- 1.16.3クライアントで1.17.1〜26.1（756〜775）のサーバーに参加できない
  （756〜775→753の Join Game／Respawn が落とされるため）
- 1.16.5クライアントで1.18.2（758）のサーバーに参加できない
  （758は未対応でプロキシが断る）
- 26.2（776）のクライアントは参加できない（プロキシが断る。776は未対応）
- 26.4以降のクライアントは参加できない（プロキシが断る）
- 1.19サーバーで他プレイヤーのチャットが見えない
  （Player Chat 759→754は落とすため。そもそも参加できないため実機では確認できない）
- 1.17クライアントで1.16.5サーバーに参加したとき、サーバーからのチャットが見えない、
  インベントリのクリックができない、エンティティの消滅が見えない
  （754サーバー→755クライアントの chat／Set Slot／Window Items／Entity Destroy が
  汎用経路で落とされるため）
- 1.17クライアントで1.16.3サーバーに参加したとき、サーバーからのチャットが見えない、
  インベントリのクリックができない、エンティティの消滅が見えないはず
  （**推論**。754サーバーではテスト確認。753サーバーでの直接テストは無いため推論）
- 774クライアントで754サーバーに参加したとき、死んでリスポーンできないはず
  （Respawn 754→774は落とされるはず。**推論**）
- 763以上のクライアントから754サーバーへのインベントリのクリックができない
  （Window Click／Set Creative Slot／Edit Book の754行きは未実装のため落とす）
- ping／pong が通らない（transaction形式と {id i32} 形式は互換性が無いため落とす。
  接続維持は Keep Alive が担う）
- Update Light が通らない（マスク形式が違い変換器が無いため落とす）
- 本に文字を書けない（763以上のクライアントからは Edit Book の754行きが無く落とす）
- レシピ帳・タグが空（Declare Recipes／Tags は未対応のため）
- `/hello` の直接入力ができない（`/trigger d_hello` を使う）

## 7. online-mode（認証成功時の確認項目）

### 7.1 確認項目（正規アカウントの1.16.3／1.16.5クライアントで実施）

- [ ] 正規アカウントで参加できる
- [ ] 他プレイヤーから見たスキンが正しく表示される（online-modeのとき）
- [ ] 認証失敗時（別アカウントでのなりすまし等）に理由付きで切断される
- [ ] online-modeの1.16.5クライアントとoffline-modeの1.16.5クライアントが同じサーバーで遊べる

## 8. 結果の記録

| 日付 | クライアント | サーバー | 結果 | 備考 |
| --- | --- | --- | --- | --- |
| （未実施） | 1.16.5 | 1.16.5（754） | — | — |
| （未実施） | 1.16.3 | 1.16.3（753） | — | — |
| （未実施） | 1.16.3 | 1.16.5（754） | — | 参加できる（753⇔754は恒等写像） |
| （未実施） | 1.16.3 | 1.17（755） | — | 参加できる（Join Game が754形式に直される） |
| （未実施） | 1.16.3 | 1.17.1（756） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.16.5 | 1.16.3（753） | — | Join Game／Respawn は通るが chat／Set Slot／Window Items／Entity Destroy は届かない |
| （未実施） | 1.16.5 | 1.17（755） | — | 参加できる（Join Game が754形式に直される） |
| （未実施） | 1.16.5 | 1.17.1（756） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.16.5 | 1.18（757） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.16.5 | 1.18.1（757） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.16.5 | 1.19（759） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.16.5 | 1.19.1（760） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.16.5 | 1.19.3（761） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.16.5 | 1.19.4（762） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.16.5 | 1.20.1（763） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17（755） | 1.16.5（754） | — | Join Game／Respawn は通るが chat／Set Slot／Window Items／Entity Destroy は届かない |
| （未実施） | 1.17（755） | 1.16.3（753） | — | Join Game／Respawn／Spawn Position は通るが chat／Set Slot／Window Items／Entity Destroy は届かない見込み（推論） |
| （未実施） | 1.19（759） | 1.16.3（753） | — | Join Game は通るはず（推論）。Set Slot／Window Items／Chunk Data／Entity Destroy は届く |
| （未実施） | 1.17.1（756） | 1.16.5（754） | — | Join Game／Respawn は通るはず（推論） |
| （未実施） | 1.18（757） | 1.16.5（754） | — | Join Game／Respawn は通るはず（推論） |
| （未実施） | 1.19（759） | 1.16.5（754） | — | Join Game は通るはず（推論）。Set Slot／Window Items／Chunk Data／Entity Destroy は届く |
| （未実施） | 1.21.11（774） | 1.16.5（754） | — | Join Game は通るはず（推論）が Respawn は落とされるはず（推論） |
| （未実施） | 1.21.11（774） | 1.16.3（753） | — | Join Game は通るはず（推論）が Respawn は落とされるはず（推論。753サーバーでの直接テストは無い） |
| （未実施） | 1.16.5 | 1.16.5（754）2人同時 | — | 最重要：相手が見えるか |
| （未実施） | 1.16.3 | 1.16.3（753）2人同時 | — | 最重要：相手が見えるか |

結果を報告するときは、通った項目・通らなかった項目に加え、画面の文言・
ログの文言も一緒に報告すること（特に切断された場合のメッセージと、
相手が見えなかった場合の様子、チャットの警告が出た場合の文言）。

## 9. 終了

サーバーを止めるときはコンソールで `stop` を入力する。
`run/data/` のワールドデータは残るため、次回も同じワールドで遊べる。
注意：`VanillaServerHost.stop()` で `IllegalThreadStateException: process hasn't exited`
が出ることがある（全バージョン共通の既存の問題。直していない。
実バニラ1.16.5の起動確認では destroy後5秒以内に終了し exit code 143、STOP-THREWなし。
755では `IllegalThreadStateException` が再現した。プロセスはその後 destroy される）。

## 付録A：実バニラ1.16.5の起動確認（実施済み・実測値）

`docs/PROXY-1.16.md` の「Java 8」と Stage D §15.3.4の記録。
実バニラ1.16.5を `VanillaServerHost` 経由で起動したときの実測値：

- 環境：`run/check-1165/vanilla`（`run/` は `.gitignore` 対象。
  jarは `run/vanilla/server-1.16.5.jar` のコピー、sha1照合済み・
  `download()` が「Reusing vanilla server jar (SHA-1 matches)」で再利用）、
  ポート25574（起動前に空きを確認、25570〜25574にリスナーなし）、
  online-mode=false、eula=true、ヒープ1024MB、Java 8（Temurin 1.8.0_512-b09）
- **1回目で Done まで到達**：`Starting minecraft server version 1.16.5`
  （01:23:47）→ **`Done (62.596s)!`**（01:24:51）。
  `waitForReady` 600秒で **READY=true、ELAPSED-SECS=102**
  （ドライバ起動からDoneまで）。ログの再確認で Done 1件・
  `Error reading pack metadata` 0件・pack関連のWARN／ERROR 0件
- データパック：生成した `world/datapacks/detergentmc.zip` のmcmetaは平文
  `{"pack":{"pack_format":6,"description":"DetergentMC"}}`（5エントリ、
  複数形 functions／tags/functions）。ログに
  `Found new data pack file/detergentmc.zip` 1件。
  `server.properties` は server-port=25574・server-ip=127.0.0.1・
  online-mode=false・network-compression-threshold=-1・spawn-protection=0
  （max-players=10、motd=DetergentMC 1.16.5）
- 停止：Doneの3秒後に `Stopping the server`→`Saving worlds` まで正常に進行。
  ただし既知の `VanillaServerHost.stop()` の問題が再現：
  20秒以内にプロセスが終了せず `Vanilla server did not stop in 20s; destroying it`
  → destroy後5秒以内に終了し **exit code 143、STOP-THREWなし**
  （755は `IllegalThreadStateException: process hasn't exited`、EXIT-CODE=null）。
  全バージョン共通の問題で、**直していない**（報告のみ）。
  ポート25574は閉じ、check-1165のプロセス残存なし
- 補足（Java 8の実現方法）：ドライバ自体はJDK 17で動かす必要があった
  （core／protocolにsealedクラスがありJDK 8ではコンパイル・実行不可）。
  そのためドライバをJDK 17＋fake home（JDK 17ツリー全体をシンボリックリンクし、
  `bin/java` だけを実JDK 8バイナリへのリンクに差し替えたもの）で起動し、
  `javaBinaryFor("1.16.5")` が返すバイナリを
  `/home/hatch/workspace/.int754d-p3/fake-jdk8-home/bin/java` にした（ドライバ出力 `JAVA-BIN=` で
  確認済み）。本番コードの変更は無い

## 付録A-2：実バニラ1.16.3の起動確認（実施済み・実測値）

`docs/PROXY-1.16.md` の「Java 8」と Stage D §15.3.4の記録。
実バニラ1.16.3を `VanillaServerHost` 経由で起動したときの実測値：

- 環境：`run/check-1163/vanilla`（`run/` は `.gitignore` 対象。
  jarは `run/vanilla/server-1.16.3.jar` のコピー、sha1照合済み・
  `download()` が「Reusing vanilla server jar (SHA-1 matches)」で再利用）、
  ポート25574（起動前に空きを確認、25570〜25574にリスナーなし）、
  online-mode=false、eula=true、ヒープ1024MB、Java 8（Temurin 1.8.0_512-b09）
- **1回目で Done まで到達**：`Starting minecraft server version 1.16.3`
  （03:36:45）→ **`Done (73.036s)!`**（03:38:01）。
  `waitForReady` 600秒で **READY=true、ELAPSED-SECS=114**
  （ドライバ起動からDoneまで。サーバー内時間73.036sとの差はJVM起動＋resolve等）。
  ログの再確認で Done 1件・`Error reading pack metadata` 0件・pack関連のWARN／ERROR 0件
  （WARN計9件は online-mode警告4件＋teleportのAmbiguity警告5件でパック無関係。ERROR計0件）
- データパック：生成した `world/datapacks/detergentmc.zip` のmcmetaは平文
  `{"pack":{"pack_format":6,"description":"DetergentMC"}}`（5エントリ、
  複数形 functions／tags/functions）。ログに
  `Found new data pack file/detergentmc.zip` 1件、
  `Reloading ResourceManager: Default, detergentmc.zip` あり
- `server.properties` は server-port=25574・server-ip=127.0.0.1・
  online-mode=false・network-compression-threshold=-1・spawn-protection=0
  （max-players=10、motd=DetergentMC 1.16.3、gamemode=survival、difficulty=easy、level-name=world）
- 停止：Doneの3秒後に `Stopping the server`→`Saving worlds` まで正常に進行。
  ただし既知の `VanillaServerHost.stop()` の20秒問題が再現：
  `Vanilla server did not stop in 20s; destroying it`。
  754と違い destroy後5秒以内に終了し **exit code 143、STOP-THREWなし**
  （754と同じ挙動）。全バージョン共通の問題で、**直していない**（報告のみ）。
  ポート25574は閉じ、check-1163のプロセス残存なし
- 子プロセスの実コマンドラインは `/home/hatch/workspace/.int754d-p3/fake-jdk8-home/bin/java -Xmx1024M -jar server-1.16.3.jar` で、
  該当バイナリの `-version` は `1.8.0_512`（Java 8で起動したことを確認。1.16.3のログ自体にJVMバージョン行はない）

## 付録B：偽754クライアントでの参加手順（直接接続・プロキシ迂回）

実機クライアントの代わりに、Pythonソケットの偽754クライアントで
本物のバニラ1.16.5に直接接続する手順。`ProxyIntegrationTest.java` の
`Fake754Client`（同ファイル:964。Keep Alive 0x1f に 0x10 で応答する）が雛形。
プロキシ経由の4セルは Stage E の統合テストで実施中（`docs/PROXY-1.16.md` の「統合テスト」を参照）。
**直接接続 (a) は実行済み**（下の表。ポート25564、Done 75.825s、Java 8）。
Join Game 30,847バイトは Stage A の実測（§11.6）と完全一致した。

### B.1 手順

1. 実バニラ1.16.5を `VanillaServerHost` 経由で起動する（付録Aの環境。
   ポート25574、online-mode=false、eula=true、ヒープ1024MB、Java 8）
2. Pythonソケットでハンドシェイクを送る（protocol 754、状態2）
3. Login Start を送る（usernameのみ。例：`Steve`）
4. Login Success（0x02）を待つ（30秒以内）
5. Join Game（0x24）を待つ（30秒以内）。**期待される結果：30,847バイト（ペイロード。フレーム30,851バイト。直接接続 (a) の実測）**
6. **Client Information（0x05）は Join Game 受信後に送ること**。
   Login Success 直後に送ると切断する可能性があるため（1.16.5での実測。ノート §14.4。
   DecoderException（Index: 5, Size: 3）で切断される）。
   6フィールドで、末尾は disableTextFiltering 無し
7. Spawn Position（0x42）を待つ（30秒以内）
8. チャット（サーバー行き 0x03、{message string}のみ）を送る
9. 移動パケット（0x12）を送る
10. 接続が維持されていることを確認する

### B.2 確認項目と期待される結果（直接接続 (a) の実測値。2026-11-16）

| 確認項目 | 期待される結果 | 状態 |
| --- | --- | --- |
| Join Game（0x24） | 30,847バイト（ペイロード。フレーム30,851バイト）。Stage A の実測（§11.6）と完全一致 | **実施済み・通過** |
| スポーン（Spawn Position 0x42） | 0x42が届く | **実施済み・通過**（`spawn_received: true`） |
| チャンク（Chunk Data 0x20） | 441パケット・5,065,124バイト（ペイロード。フレーム5,066,459バイト）。groundUp＋bitMap＋heightmaps NBT＋バイオーム varint[1024]（groundUpのときだけ）、光なし、BitStorage non-straddling | **実施済み・通過** |
| チャット | サーバーに届き、ブロードキャスト（0x0E）が返る。サーバーログ `<Steve> hello from 1.16.5` で確認 | **実施済み・通過** |
| 移動（0x12） | 移動後も接続維持（Keep Alive応答1回、圧縮しきい値256、クライアント側の経過時間14.293秒） | **実施済み・通過** |
| 754→755→754の往復 | Join Game が書き換わる（codec のバイオーム79件⇔81件、dimension element の min_y／height の有無） | **未実施**（直接接続では確かめられない。単体テスト `joinAndRespawn754Against755Convert`／`ProxySession754bTest.joinGame754To755RewritesCodec` で変換器レベルは確認済み） |

参考（Stage A で実施済みの測定）：実バニラ1.16.5の Join Game は **30,847バイト**（§11.6。2回の実行で同じバイト数。entityId 217／224、viewDistance 10）。チャンク-15,-7は10,633バイト（groundUp=true、bitMap 0x000f、biomes 1024件全て50）。Stage E の直接接続 (a) の Join Game も30,847バイトで一致した（entityId は今回110で、§11.6の270とはワールド違い）。結果JSONは `/tmp/capture-754/result-a-direct.json`（バックアップ `run/capture-754/`）。

### B.3 実ゲームクライアントでの確認は未実施

上記の偽クライアントでの確認は、あくまでプロトコルレベルの検証である。
本物の1.16.5ゲームクライアントでの参加・プレイ・2人同時参加は
**未実施**である。実機での確認はこの手順書の §2〜§5 のチェックリストで
行うこと。

## 付録B-2：偽753クライアントでの直接接続（実施済み・実測値）

実機クライアントの代わりに、Pythonソケットの偽753クライアントで
本物のバニラ1.16.3に直接接続する手順。`ProxyIntegrationTest.java` の
`Fake753Client`（同ファイル:1065。Keep Alive 0x1f に 0x10 で応答する）が雛形。
**直接接続 (a) は実施済みで、下の表は実測値である**（結果JSONは
`/tmp/capture-753/result-a-direct.json` と `run/capture-753/result-a-direct.json`）。
(b)(c)(d)も実施済みで、いずれもバージョン不一致による切断を確認した
（§16.4参照）。754の直接接続の実測値を753の結果として流用していない。

### B-2.1 手順

1. 実バニラ1.16.3を `VanillaServerHost` 経由で起動する（付録A-2の環境。
   ポート25574、online-mode=false、eula=true、ヒープ1024MB、Java 8）
2. Pythonソケットでハンドシェイクを送る（protocol 753、状態2）
3. Login Start を送る（usernameのみ。例：`Steve`）
4. Login Success（0x02）を待つ（30秒以内）
5. Join Game（0x24）を待つ（30秒以内）。**期待される結果：30,847バイト（ペイロード。Stage A の実測。§11.6）**
6. **Client Information（0x05）は Join Game 受信後に送ること**。
   Login Success 直後に送ると切断する可能性があるため（1.16.5での実測。754ノート §14.4。
   DecoderException（Index: 5, Size: 3）で切断される）。
   6フィールドで、末尾は disableTextFiltering 無し
7. Spawn Position（0x42）を待つ（30秒以内）
8. チャット（サーバー行き 0x03、{message string}のみ）を送る
9. 移動パケット（0x12）を送る
10. 接続が維持されていることを確認する

### B-2.2 確認項目と実測結果（実施済み）

| 確認項目 | 実測結果 | 状態 |
| --- | --- | --- |
| Join Game（0x24） | 30,847バイト（ペイロード。消費量30,847で完全一致。Stage A の実測（§11.6）と同一。entityId 197） | **実施済み・通過** |
| スポーン（Spawn Position 0x42） | 0x42が届いた（Join Game の後に受信） | **実施済み・通過** |
| チャンク（Chunk Data 0x20） | 462パケット・5,466,970バイト（ペイロード）。groundUp＋bitMap＋heightmaps NBT＋バイオーム varint[1024]（groundUpのときだけ）、光なし、BitStorage non-straddling | **実施済み・通過** |
| チャット | サーバーに届き、ブロードキャスト（0x0E）が返った（クライアント側で `hello from 1.16.3` を受信。サーバーログでも `<Steve> hello from 1.16.3` を確認） | **実施済み・通過** |
| 移動（0x12） | 移動後も接続維持（Keep Alive応答2回、圧縮しきい値256、クライアント側の経過時間25.31秒） | **実施済み・通過** |
| 753→754→753の往復 | Join Game が書き換わらない（753⇔754は恒等写像。codec のバイオーム79件・dimension element の min_y／height 無しは両版とも同じ） | **未実施**（直接接続では確かめられない。単体テスト `joinAndRespawn753Against754Convert` で変換器レベルは確認済み） |

### B-2.3 実ゲームクライアントでの確認は未実施

上記の偽クライアントでの確認は、あくまでプロトコルレベルの検証である。
本物の1.16.3ゲームクライアントでの参加・プレイ・2人同時参加は
**未実施**である。実機での確認はこの手順書の §2〜§5 のチェックリストで
行うこと。

## 付録C：外部配信（`resource-pack.url-754` / `sha1-754`）の設計

`detergentmc.yml` の `resource-pack.url-754` / `sha1-754` に外部URLとSHA-1を
書くと、754クライアントにはそのパックを出す（既定はloopback配信）。
753クライアントには `resource-pack.url-753` / `sha1-753` を使う（既定はloopback配信）。
