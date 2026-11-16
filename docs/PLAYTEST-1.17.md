# DetergentMC 1.17 実機プレイテスト — チェックリスト

2026-11-16作成。1.17クライアント（プロトコル755）で1.17・
1.17.1・1.18・1.18.1・1.19・1.19.1・1.19.2・1.19.3・1.19.4・1.20系・1.21系サーバーに参加する
実機確認のための手順と、結果の記録用。既存の
`PLAYTEST-1.17.1.md`（1.17.1クライアント用）・`PLAYTEST-1.18.md`
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
こちらを1.17専用の手順書とする。

## 0. この手順書の読み方：プロトコル検証済み／実機でしか確認できないもの

DetergentMCの1.17対応は、ここに書く項目の多くを**プロトコルレベルでは
すでに検証済み**である（本物のバニラ子プロセス＋生ソケットで755クライアントを
装ったキャプチャ、`ChunkTranslator755Test`、`PlayTranslator755Test`、
`ProxySession755Test` など）。実機テストの目的は、プロトコルレベルでは
見えない「本物のゲームクライアントでの見た目・挙動」を確かめることである。
以下の2つに分けて読むこと。

### A. プロトコルレベルで既に検証済みの項目（実機では「同じことが起きるか」の確認）

- `verify-755-tables.py` は43件中43件通過（パケットID差分・レジストリ差分の表。
  A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）
- パケットID差分（756↔755）：**変更0件・新規0件・削除0件**
  （`PLAY_ENTITY_DESTROY` の内部名改名 `destroy_entity`→`entity_destroy` 1件のみ。IDは同じ 0x3a）。
  `PacketIds.java` のエントリ総数272件、id755>=0 は160件
  （HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 103件＋
  PLAY サーバー行き 48件。CONFIGURATION は0件）。
  minecraft-data・ViaVersion・Wiki の三重照合で不一致0件
- レジストリ差分（756↔755）：**全種0件**。ブロック状態 20,342→20,342件（±0）、
  ブロック 898→898件（±0）、アイテム 1,100→1,100件（±0）、
  エンティティ 113→113件（±0）、音 1,190→1,190件（±0）、
  パーティクル 89→89件（±0）、エンチャント 38→38件（±0）、
  ブロックエンティティ 34→34件（±0）、バイオーム 81→81件（±0）、
  エフェクト 32→32件（±0）。メニュー24件・属性13件は順序も同一で変化なし。
  改名・削除・新規は全種0件。補正も全種0件
- フィールド差分は5件のみ：Set Slot／Window Items／Window Click
  （1.17.1が stateId varint を追加。変換器は stateId 0 を補完／除去）、
  Entity Destroy（755は [entityId varint] 1体、756は [count][entityIds]）、
  Edit Book（全面的に違う。変換器は無く、落とす）
- Join Game codec は756と同一（simulationDistance はどちらにも無く、
  lastDeathLocation もどちらにも無い）。dimensionCodec は dimension_type 4件・
  バイオーム81件（id 0〜175の非連番）で両方同一
- 実バニラ1.17の Join Game 31,555バイト／実バニラ1.17.1の Join Game 31,555バイトの
  生キャプチャ。両 Join Game のバイト比較：差分は9バイトのみ
  （entityId 1バイト＋hashedSeed 8バイト）
- 実バニラ1.17.1のチャンク生キャプチャ（チャンク-21,-17、13,449バイト）の
  往復はバイト完全一致（`cap756-chunk-21-17.bin`。チャンク形式同一が
  protocol.json＋javap で確定しているため、1.17.1の生キャプチャはそのまま
  755形式のチャンクでもある。**1.17専用の生キャプチャは無い**）
- 新規・書き換え755テスト **61件全通過**（Stage B）：`PlayTranslator755Test`（10件）、
  `ChunkTranslator755Test`（13件）、`ProxySession755Test`（11件）、
  `ClientAdapter755Test`（12件）、`ClientJoinFlow755Test`（5件）、
  `ClientProxy755Test`（4件）、`V117AdapterTest`（6件）。
  Stage Aの新規755テスト26件も全通過。
  全体回帰は2,467件通過・29件失敗（失敗は全て環境制約で、755が原因の
  本物の回帰は0件）
- 755用リソースパック（pack_format 7、平文 pack.mcmeta）の生成と配信
  （`ResourcePackBuilder755Test` 3件、`ResourcePackServer755Test` 2件、
  `ResourcePackMode755Test` 6件）。**755パックと756パックは内容同一で sha1 が等しい**
- 実バニラ1.17の起動確認に成功（`Done (100.329s)!`、pack metadataエラー0件）
- 755サーバー→774クライアントの Join Game は通るが Respawn は落とされる
  （`ProxySession755Test.join774Against755ConvertsJoinGameButDropsRespawn` で確認）
- 755サーバー→756クライアントの Join Game／Respawn は通るが、chat／Set Slot／
  Window Items／Entity Destroy は汎用経路で落とされる
  （`ProxySession755Test.joinAndRespawn755Against756Convert` で確認）
- 755クライアント→756〜775サーバーには参加できない（Join Game／Respawn が
  落とされる。`ProxySession.java:7058`／`:8029`／`:8323` で確認）

### B. 実機でしか確認できない項目（この手順書の中心）

- 本物の1.17クライアントが、1.17サーバーに参加できるか
  （プロトコルレベルでは偽クライアントしか使っていない）
- 本物の1.17クライアントが、1.17.1サーバーに参加できないこと
  （Join Game が落とされるため。バグではない。下の「4.」を参照。
  **確認するのは「参加できないこと自体」である**）
- 1.17クライアントで1.17.1サーバーの stateId 付きウィンドウ操作が
  どう見えるか（参加できないため、この組み合わせでは確認できない。
  逆向き＝1.17.1クライアントが1.17サーバーに参加する「5.」で確かめること）
- 1.17サーバー側の本（Edit Book）が1.17.1クライアントでどう見えるか
  （変換器が無いため落とす。実機で確かめること）
- 1.17クライアントに755用パック（pack_format 7、平文 pack.mcmeta）が
  提示され、適用されるか（実バニラjarのjavapによる根拠のみで、
  実機での受け入れ確認は未実施）
- **2人同時参加（他プレイヤーの出現・スキン・チャット）**。
  偽クライアントでは、直接接続でもプロキシ経由でも、他プレイヤーの
  出現パケットが30秒以内に届かなかった（1.19.3・1.19.1・1.18と同じ）。
  **実機クライアントで2人同時に遊び、相手が見えるかどうかを確かめるのが
  最も重要な残りの検証である**。相手が見えない場合は、ログの文言と一緒に
  報告すること
- 756サーバーに755クライアントが参加できないこと
  （Join Game／Respawn が落とされるため。バグではない。下の「4.」を参照）
- 755サーバーに774クライアントが参加したとき、Join Game は通るが
  Respawn が落とされること（死亡後のリスポーンで確かめる）
- 755サーバーに763〜765クライアントが参加したとき、Join Game／Respawn とも通るはずのこと
  （推論。下の「5.」を参照）。766以上のクライアントでは Join Game は通るはずだが Respawn が
  落とされること（774はテスト確認。下の「5.」を参照）
- 755サーバーに756クライアントが参加したとき、チャット・Set Slot・Window Items・
  Entity Destroy が届かないこと（汎用経路で落とされるため。下の「5.」を参照）
- 1.17クライアントで1.17サーバーのチャンクがどう見えるか
  （755も756も16セクション＋バイオーム配列。光なし）

## 1. 起動

### 1.1 準備（初回のみ）

```sh
cd ~/workspace/detergentmc-755
./gradlew build :bubbles:hello-bubble:jar
cp bubbles/hello-bubble/build/libs/hello-bubble-0.1.0-1.20.4.jar run/bubbles/
```

`run/eula.txt` と `run/vanilla/eula.txt` が `eula=true` になっていること。

### 1.2 起動（ファットjar・推奨）

```sh
cd ~/workspace/detergentmc-755
JAVA_HOME=/home/hatch/workspace/.tools/jdk-17.0.21+9 \
  java -jar build/libs/detergentmc-0.1.0-1.20.4.jar
```

`detergentmc.yml` の `minecraft-version` を `1.17` にして起動すると、
内部755サーバーが Java 16 で起動する。`1.17.1` にすると内部756サーバー、
`1.18`／`1.18.1` にすると内部757サーバー、
`1.19` にすると内部759サーバー、`1.19.1`／`1.19.2` にすると内部760サーバー、
`1.19.3` にすると内部761サーバー、`1.19.4` にすると内部762サーバー、
`1.20.1` にすると内部763サーバーになる（切り替えて試すこと）。
**1.17の内部サーバーは Java 16 で動く**（Temurin 16.0.2+7、
`~/.tools/jdk-16.0.2+7`）。**1.18／1.19系のサーバーは Java 17 で動く**。
Java 21／Java 25 は1.17・1.17.1・1.18・1.19系には要らない。
注意：DetergentMC 自体（プロキシ）は JDK 17 で動かす（core／protocolに
sealedクラスがありJDK 16ではコンパイル・実行不可）。内部1.17サーバーだけが
Java 16で起動する（`javaBinaryFor("1.17")` が返すバイナリは
`/home/hatch/.tools/jdk-16.0.2+7/bin/java`）。

### 1.3 接続（実機クライアント）

Minecraft Java Edition 1.17 のクライアントで `127.0.0.1:25565` に接続する。
オフラインモード（`online-mode: false`）の場合は、ランチャーのプロファイルで
オフラインモードを有効にするか、適当な名前で接続すること。

### 1.4 ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。**クライアントは必ずここに接続する** |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`127.0.0.1` のみ。直接つながないこと） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。755用・756用・757用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の20つを出す（758用・776用は無い） |

起動時に20つのパックが生成される：

| ファイル | 中身 |
| --- | --- |
| `run/data/resource-pack/detergentmc-pack-1.17.zip` | 755用（pack_format 7、1.17専用）。プロキシが755クライアントにだけ出す。**756用パックと内容同一で sha1 が等しい** |
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
が出れば配信開始。755用パックも同じポートの
`http://127.0.0.1:25567/detergentmc-pack-1.17.zip` で配信される。
755用パックの pack.mcmeta は平文 `{"pack_format":7}` で、
777の min_format／max_format 形式ではない（1.17には存在しない。
`docs/PROXY-1.17.md` を参照）。

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
**1.17クライアントが参加できるのは 1.17（755）の1行だけ**
（756〜775は Join Game が落とされるため参加できない。下の「4.」「7.」を参照）。

| `minecraft-version` | 内部プロトコル | データパック | バニラに渡すリソースパック | 注意 |
| --- | --- | --- | --- | --- |
| `1.17` | 755 | 7 | 755用（pack_format 7） | 1.17クライアントが参加できる（素通し）。**Java 16** |
| `1.17.1` | 756 | 7 | 756用（pack_format 7） | 1.17クライアントは参加できない（Join Game／Respawn が落とされる） |
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

## 2. 参加（1.17クライアント）

1.17の本物のゲームクライアントで、プロキシの公開ポート
（既定25565）に接続する。サーバーリストには **1.17**（protocol 755）と
表示されるはずである。

参加直後にリソースパックの提示が出る（755用パック pack_format 7）。
「はい」を選ぶとパックが適用される。パックの提示が出ない場合は、
`resource-pack.enabled` が `true` になっていることと、25567番ポートの
配信を確認すること。

## 3. チェックリスト（1.17クライアント → 内部755サーバー）

`minecraft-version` を `1.17` にして起動し、1.17クライアントで接続する。
素通し（リレー）のため、1.17の世界がそのまま見えるはず。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 地面・木・水などの見た目が正しい（ブロック状態の変換が正しい）
- [ ] 移動できる（歩く・走る・泳ぐ・ジャンプ）
- [ ] チャットができる（`/trigger d_hello` で挨拶が出る。直接 `/hello` は動かない）
- [ ] `/trigger d_lang_en` で英語に切り替わる。`/trigger d_lang_ja` で日本語に戻る
- [ ] リソースパックが提示される（pack_format 7）。適用される
- [ ] インベントリを開ける。アイテムを動かせる（Window Click の変換）
- [ ] クリエイティブでアイテムを取れる（Set Creative Slot の変換）
- [ ] 看板を立てて文字を書ける（Update Sign の変換）
- [ ] 死んでリスポーンできる（Respawn の変換）
- [ ] 村人と取引できる（Trade List の変換。inputItem2 の option slot）
- [ ] スカルクセンサーが動く（1.17にあるもの。振動を検知して信号を出す）
- [ ] 2人目が参加したとき、相手が見える（Player Info・出現パケット）。
  **ここが最重要の未確認項目**。相手が見えない・相手が馬に見える・
  姿がおかしい場合は、そのままの様子を報告すること
- [ ] 本に文字を書けるか（Edit Book。1.17形式 {new_book, signing, hand}。
  変換器が無いため、1.17.1クライアントから書いた本がどう見えるかも含めて確かめること。
  下の「5.」も参照）
- [ ] エンティティが消える場面（モブのデスポーン等）でおかしな挙動が無いか
  （Entity Destroy は755だけ [entityId] 1体のため）

## 4. 1.17クライアント → 内部756／757／759〜775サーバー（参加できない）

`minecraft-version` を `1.17.1`／`1.18`／`1.19`／`1.19.1`／`1.19.3`／`1.19.4`／
`1.20.1`／`1.20.2`／`1.20.4`／`1.20.5`／`1.21`／`1.21.11`／`26.1` にして起動し、
1.17クライアントで接続する。**参加できない**。サーバー→クライアントの
Join Game／Respawn が落とされるため（756サーバーは756→757→759→763の連鎖を経て
`handleServerJoinGame763` の is755() 分岐で落とされる。757〜775サーバーも同じガードに
到達する。バグではない。`docs/VERSIONS.md` の1.17マトリクスを参照。
マトリクスは `docs/VERSIONS.md` の「1.17対応：全400通りの対応マトリクス」を参照。
**確認するのは「参加できないこと自体」である。参加できる前提のチェックリストにしないこと。**

- [ ] 接続しても Join Game が届かず、参加できない（切断されるか、ローディングのまま進まない）
- [ ] プロキシログに Join Game の落としが記録されるか
- [ ] （参考）1.17.1以降の新規要素（music_disc_otherside、block_marker、
  meadow・grove・snowy_slopes・frozen_peaks・jagged_peaks・stony_peaks、
  マングローブ・泥・スカルク・フロッグライト・アレイ・カエル・ウォーデン・
  竹・吊り看板・ラクダ・桜・スニッファーといった1.19系の新規要素）は、
  755クライアントからはこの組み合わせでは確認できない（参加できないため）。
  新規要素の見え方は、755サーバーに新しいクライアントで参加する「5.」の逆向きで確かめること
- [ ] 他プレイヤーのチャット（Player Chat 759→755は落とす）も、参加できない
  ためこの組み合わせでは確認できない。翻訳子としての落としは単体テストで確認済み
  （756と同じ扱い。755での直接の単体テストは無いため未確認の部分もある）

## 5. チェックリスト（1.17.1／1.18／1.19／1.20.1／1.21.11クライアント → 内部755サーバー）

`minecraft-version` を `1.17` にして起動し、1.17.1／1.18／1.19／
1.20.1／1.21.11クライアントで接続する。逆向き（新しいクライアントが
古いサーバーに参加する）の確認。**方向を逆に書かないこと。**
新しいクライアント×古いサーバーは Join Game が通るが、Respawn と汎用パケットに制限がある。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 1.17の世界が正しく見えるか（ブロック状態・バイオームの逆向き変換。レジストリ差分は0件のため見た目は同じはず）
- [ ] 1.17.1クライアントで1.17サーバーのチャットが見えるか
  （**見えないはず**。755サーバー→756クライアントの chat は汎用経路で落とされる。
  `ProxySession755Test.joinAndRespawn755Against756Convert` で確認。
  サーバーからのお知らせも同じく届かない。実機で確かめること）
- [ ] 1.17.1クライアントで自分のチャットがサーバーに届くか
  （届くはず。クライアント→サーバーは変換する。Chat Message 0x04→chat 0x03。
  `ProxySession755Test.clientPackets759Against755Convert` で確認）
- [ ] 1.17.1クライアントでインベントリのクリックができるか
  （**できないはず**。755サーバー→756クライアントの Set Slot／Window Items は
  汎用経路で落とされる。変換器はあるが呼び出し口が無い。実機で確かめること）
- [ ] 1.17.1クライアントでエンティティの消滅が正しく見えるか
  （**おかしくなるはず**。755サーバー→756クライアントの Entity Destroy は
  汎用経路で落とされる。モブが消えずに残って見える可能性がある。実機で確かめること）
- [ ] 1.17.1クライアントで死んでリスポーンできるか
  （できるはず。Respawn は通る。`ProxySession755Test.joinAndRespawn755Against756Convert` で確認）
- [ ] 774クライアントで死んでリスポーンするとどうなるか
  （Respawn は落とされるため、リスポーンできない／切断されるはず。
  `ProxySession755Test.join774Against755ConvertsJoinGameButDropsRespawn` で確認。実機で確かめること）
- [ ] 763〜765クライアントで死んでリスポーンできるか
  （できるはず。`handleServerPlayFrom763` の Respawn 分岐の落としリストに763〜765は無い。
  **755サーバーでの直接テストは無いため推論**。実機で確かめること）
- [ ] 766以上のクライアントで死んでリスポーンするとどうなるか
  （Respawn は落とされるはず。774はテスト確認、766／767／768／769／770／771／772／773／775は
  落としリストに載っていることをコードで確認。**755サーバーでの直接テストは無いため推論**。
  実機で確かめること）
- [ ] 763以上のクライアントでインベントリのクリックができるか
  （できないはず。Window Click／Set Creative Slot の755行きは未実装のため落とす。
  `ProxySession755Test.windowClick763Against755Drops` で確認）
- [ ] 1.17.1クライアントで本に文字を書けるか
  （できないはず。Edit Book は変換器がどこにも無く落とす。実機で確かめること）
- [ ] 2人目が参加したとき、相手が見えるか

## 6. わざと失敗するはずのもの（バグではない）

- 1.17クライアントで1.17.1〜26.1（756〜775）のサーバーに参加できない
  （756〜775→755の Join Game／Respawn が落とされるため）
- 1.17クライアントで1.18.2（758）のサーバーに参加できない
  （758は未対応でプロキシが断る）
- 26.2（776）のクライアントは参加できない（プロキシが断る。776は未対応）
- 26.4以降のクライアントは参加できない（プロキシが断る）
- 1.19サーバーで他プレイヤーのチャットが見えない
  （Player Chat 759→755は落とすため。そもそも参加できないため実機では確認できない）
- 1.17.1クライアントで1.17サーバーに参加したとき、サーバーからのチャットが見えない、
  インベントリのクリックができない、エンティティの消滅が見えない
  （755サーバー→756クライアントの chat／Set Slot／Window Items／Entity Destroy が
  汎用経路で落とされるため）
- 774クライアントで755サーバーに参加したとき、死んでリスポーンできない
  （Respawn 755→774は落とされるため）
- 763以上のクライアントから755サーバーへのインベントリのクリックができない
  （Window Click／Set Creative Slot の755行きは未実装のため落とす）
- 本に文字を書けない（Edit Book は変換器がどこにも無く、全方向で落とす）
- レシピ帳・タグが空（Declare Recipes／Tags は未対応のため）
- `/hello` の直接入力ができない（`/trigger d_hello` を使う）

## 7. online-mode（認証成功時の確認項目）

### 7.1 確認項目（正規アカウントの1.17クライアントで実施）

- [ ] 正規アカウントで参加できる
- [ ] 他プレイヤーから見たスキンが正しく表示される（online-modeのとき）
- [ ] 認証失敗時（別アカウントでのなりすまし等）に理由付きで切断される
- [ ] online-modeの1.17クライアントとoffline-modeの1.17クライアントが同じサーバーで遊べる

## 8. 結果の記録

| 日付 | クライアント | サーバー | 結果 | 備考 |
| --- | --- | --- | --- | --- |
| （未実施） | 1.17 | 1.17（755） | — | — |
| （未実施） | 1.17 | 1.17.1（756） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17 | 1.18（757） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17 | 1.18.1（757） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17 | 1.19（759） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17 | 1.19.1（760） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17 | 1.19.3（761） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17 | 1.19.4（762） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17 | 1.20.1（763） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17.1（756） | 1.17（755） | — | Join Game／Respawn は通るが chat／Set Slot／Window Items／Entity Destroy は届かない |
| （未実施） | 1.18（757） | 1.17（755） | — | Join Game／Respawn は通るはず（推論） |
| （未実施） | 1.19（759） | 1.17（755） | — | Join Game／Respawn は通るはず（推論） |
| （未実施） | 1.21.11（774） | 1.17（755） | — | Join Game は通るが Respawn は落とされる |
| （未実施） | 1.17 | 1.17（755）2人同時 | — | 最重要：相手が見えるか |

結果を報告するときは、通った項目・通らなかった項目に加え、画面の文言・
ログの文言も一緒に報告すること（特に切断された場合のメッセージと、
相手が見えなかった場合の様子、チャットの警告が出た場合の文言）。

## 9. 終了

サーバーを止めるときはコンソールで `stop` を入力する。
`run/data/` のワールドデータは残るため、次回も同じワールドで遊べる。
注意：`VanillaServerHost.stop()` で `IllegalThreadStateException: process hasn't exited`
が出ることがある（全バージョン共通の既存の問題。直していない。
実バニラ1.17の起動確認でも再現した。プロセスはその後 destroy される）。

## 付録A：実バニラ1.17の起動確認（実施済み・実測値）

`docs/PROXY-1.17.md` の「Java 16」と Stage D §15.3.4の記録。
実バニラ1.17を `VanillaServerHost` 経由で起動したときの実測値：

- 環境：`run/check-117/vanilla`（`run/` は `.gitignore` 対象。
  jarは `run/vanilla/server-1.17.jar` のコピー、sha1照合済み・
  `download()` が「Reusing vanilla server jar (SHA-1 matches)」で再利用）、
  ポート25574（起動前に空きを確認、25570〜25574にリスナーなし）、
  online-mode=false、eula=true、ヒープ1024MB、Java 16.0.2+7
- **1回目で Done まで到達**：`Starting minecraft server version 1.17`
  （21:39:19）→ **`Done (100.329s)!`**（21:41:03）。
  `waitForReady` 600秒で **READY=true、ELAPSED-SECS=146**
  （ドライバ起動からDoneまで）。ログの再確認で Done 1件・
  `Error reading pack metadata` 0件・pack関連のWARN／ERROR 0件
- データパック：生成した `world/datapacks/detergentmc.zip` のmcmetaは平文
  `{"pack":{"pack_format":7,"description":"DetergentMC"}}`（5エントリ、
  複数形 functions／tags/functions）。ログに
  `Found new data pack file/detergentmc.zip` 1件。
  `server.properties` は server-port=25574・server-ip=127.0.0.1・
  online-mode=false・network-compression-threshold=-1・spawn-protection=0
  （max-players=10、motd=DetergentMC 1.17）
- 停止：Doneの3秒後に `Stopping the server`→`Saving worlds` まで正常に進行。
  ただし既知の `VanillaServerHost.stop()` の問題が再現：
  20秒以内にプロセスが終了せず `Vanilla server did not stop in 20s; destroying it`
  → `STOP-THREW IllegalThreadStateException: process hasn't exited`
  （EXIT-CODE=null）。全バージョン共通の問題で、**直していない**（報告のみ）。
  ポート25574は閉じ、check-117のプロセス残存なし
- 補足（Java 16の実現方法）：ドライバ自体はJDK 17で動かす必要があった
  （core／protocolにsealedクラスがありJDK 16ではコンパイル・実行不可）。
  そのためドライバをJDK 17＋`-Djava.home=$HOME/.tools/jdk-16.0.2+7` で起動し、
  `javaBinaryFor("1.17")` が返すバイナリを
  `/home/hatch/.tools/jdk-16.0.2+7/bin/java` にした（ドライバ出力 `JAVA-BIN=` で
  確認済み）。本番コードの変更は無い

## 付録B：偽755クライアントでの参加手順（直接接続・プロキシ迂回）

実機クライアントの代わりに、Pythonソケットの偽755クライアントで
本物のバニラ1.17に直接接続する手順。`ProxyIntegrationTest.java` の
`Fake756Client` が雛形。プロキシ経由の4セルは Stage E の統合テストで
実施中（`docs/PROXY-1.17.md` の「統合テスト」を参照）。
**直接接続は実施済み**：(a) 偽755→実1.17は通過（Join Game 0x26＝31,555バイト、Chunk Data 0x22＝462パケット・6,592,432バイト、チャット到達、Keep Alive応答2回、移動後も接続維持。`run/capture-755/result-a-direct.json`）。**プロキシ経由はこのVMでは実行できなかった**（(a)(b)を試行し、JVMのTCP遮断でクライアントが正規パケット0件でEOF。`docs/PROXY-1.17.md` の「統合テスト」を参照）。

### B.1 手順

1. 実バニラ1.17を `VanillaServerHost` 経由で起動する（付録Aの環境。
   ポート25574、online-mode=false、eula=true、ヒープ1024MB、Java 16.0.2+7）。
   `Done (100.329s)!` まで待つ
2. Pythonソケットでハンドシェイクを送る（protocol 755、状態2）
3. Login Start を送る（usernameのみ。例：`Steve`）
4. Login Success（0x02）を待つ（30秒以内）
5. Join Game（0x26）を待つ（30秒以内）。**期待される結果：実バニラ1.17の
   Join Game は31,555バイト**（直接接続 (a) で確認済み。`run/capture-755/result-a-direct.json`）
6. **Client Information（0x05）は Join Game 受信後に送ること**。
   Login Success 直後に送ると切断する可能性があるため（1.18での実測。757 §15.2。
   1.17での再現は未確認）。7フィールドで、末尾は disableTextFiltering=false、
   allowServerListings無し
7. Spawn Position（0x4B）を待つ（30秒以内）
8. チャット（サーバー行き 0x03、{message string}のみ）を送る
9. 移動パケット（0x11）を送る
10. 接続が維持されていることを確認する

### B.2 確認項目と期待される結果

| 確認項目 | 期待される結果 | 状態 |
| --- | --- | --- |
| Join Game（0x26） | 31,555バイト。dimensionCodec に dimension_type 4件・biome 81件 | **確認済み**（直接接続 (a)。`run/capture-755/result-a-direct.json`） |
| スポーン（Spawn Position 0x4B） | 0x4Bが届く | **確認済み**（直接接続 (a)。spawn_received=true） |
| チャンク（Chunk Data 0x22） | チャンクが届く。16セクション＋bitMap＋heightmaps NBT＋バイオームint[1024]、光なし、BitStorage non-straddling | **確認済み**（直接接続 (a)。462パケット・6,592,432バイト。1.17専用の生キャプチャは無いが、1.17.1の生キャプチャの往復はバイト完全一致） |
| チャット | サーバーに届き、ブロードキャスト（0x0F）が返る | **確認済み**（直接接続 (a)。サーバーログ `<Steve> hello from 1.17`） |
| 移動（0x11） | 移動後も接続維持 | **確認済み**（直接接続 (a)。still_connected_after_move=true） |
| 755→756→755の往復 | Join Game がバイト完全一致（31,555→31,555、差分は entityId・seed のみ） | **確認済み**（直接接続 (a) で Join Game 31,555バイトを確認。往復のバイト比較は Stage B のライブキャプチャで確認済み。差分は9バイトのみ） |

### B.3 実ゲームクライアントでの確認は未実施

上記の偽クライアントでの確認は、あくまでプロトコルレベルの検証である。
本物の1.17ゲームクライアントでの参加・プレイ・2人同時参加は
**未実施**である。実機での確認はこの手順書の §2〜§5 のチェックリストで
行うこと。

## 付録C：外部配信（`resource-pack.url-755` / `sha1-755`）の設計

`detergentmc.yml` の `resource-pack.url-755` / `sha1-755` に外部URLとSHA-1を
書くと、755クライアントにはそのパックを出す（既定はloopback配信）。
