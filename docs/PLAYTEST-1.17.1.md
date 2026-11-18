# DetergentMC 1.17.1 実機プレイテスト — チェックリスト

2026-11-16作成。1.17.1クライアント（プロトコル756）で1.17.1・
1.18・1.18.1・1.19・1.19.1・1.19.2・1.19.3・1.19.4・1.20系・1.21系サーバーに参加する
実機確認のための手順と、結果の記録用。既存の
`PLAYTEST-1.18.md`（1.18クライアント用）・`PLAYTEST-1.19.md`
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
こちらを1.17.1専用の手順書とする。

## 0. この手順書の読み方：プロトコル検証済み／実機でしか確認できないもの

DetergentMCの1.17.1対応は、ここに書く項目の多くを**プロトコルレベルでは
すでに検証済み**である（本物のバニラ子プロセス＋生ソケットで756クライアントを
装ったキャプチャ、`ChunkTranslator756Test`、`PlayTranslator756Test`、
`ProxySession756Test` など）。実機テストの目的は、プロトコルレベルでは
見えない「本物のゲームクライアントでの見た目・挙動」を確かめることである。
以下の2つに分けて読むこと。

### A. プロトコルレベルで既に検証済みの項目（実機では「同じことが起きるか」の確認）

- `verify-756-tables.py` は43件中43件通過（パケットID差分・レジストリ差分の表。
  A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）
- パケットID差分（757↔756）：変更16件・新規0件・削除1件
  （`PLAY_SIMULATION_DISTANCE` の削除による-1ずれ）。
  `PacketIds.java` のエントリ総数272件、id756>=0 は160件
  （HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 103件＋
  PLAY サーバー行き 48件。CONFIGURATION は0件）。
  minecraft-data・ViaVersion・Wiki の三重照合で不一致0件
- レジストリ差分（757↔756）：ブロック状態 20,342→20,342件（±0）、
  ブロック 898→898件（±0）、アイテム 1,101→1,100件（-1 music_disc_otherside）、
  エンティティ 113→113件（±0）、音 1,203→1,190件（-13）、
  パーティクル 88→89件（+1。barrier／light→block_marker の統合の逆）、
  エンチャント 38→38件（±0）、ブロックエンティティ 34→34件（±0）、
  バイオーム 61→81件（改名11・削除26・新規6）、エフェクト 32→32件（±0）。
  メニュー24件・属性13件は順序も同一で変化なし。
  7種（ブロック状態・ブロック・アイテム・エンティティ・音・パーティクル・
  メニュー）に改名・削除はない（バイオームとパーティクルの統合を除く）
- Join Game codec の変更は simulationDistance の有無だけ
  （756→757は viewDistance と同じ値で補完）と、dimensionCodec の
  バイオーム81件↔61件、dimension_type の element の違い
  （logical_height 256→384、min_y 0→-64）
- 実バニラ1.17.1の Join Game 31,555バイト／実バニラ1.18の Join Game 23,986バイトの
  生キャプチャ。756→757→756の往復はバイト完全一致
  （31,555→23,423→31,555、差分0）
- 実バニラ1.17.1のチャンク生キャプチャ（チャンク-21,-17、13,449バイト）の
  往復はバイト完全一致（`cap756-chunk-21-17.bin`）
- 新規756テスト **65件全通過**：`PlayTranslator756Test`（17件）、
  `ChunkTranslator756Test`（14件）、`ProxySession756Test`（8件）、
  `ClientAdapter756Test`（11件）、`ClientJoinFlow756Test`（5件）、
  `ProtocolVersion756Test`（3件）、`ClientProxy756Test`（4件）、
  `V1171AdapterTest`（6件）。
  全体回帰は2,377件通過・28件失敗（失敗は全て環境制約で、756が原因の
  本物の回帰は0件）
- 756用リソースパック（pack_format 7、平文 pack.mcmeta）の生成と配信
  （`ResourcePackBuilder756Test` 3件、`ResourcePackServer756Test` 2件、
  `ResourcePackMode756Test` 6件）
- 実バニラ1.17.1の起動確認に成功（`Done (115.618s)!`、pack metadataエラー0件）
- 756サーバー→774クライアントの Join Game は通る（三段変換
  756→757→759→763→774で Join Game。`ProxySession756Test` で確認）

### B. 実機でしか確認できない項目（この手順書の中心）

- 本物の1.17.1クライアントが、1.17.1サーバー・1.18サーバーに参加できるか
  （プロトコルレベルでは偽クライアントしか使っていない）
- 本物の1.17.1クライアントが、1.18サーバーの Join Game codec
  （simulationDistance 付き、バイオーム61件）を無視して参加できるか
- 1.18サーバー側の新しいアイテム（music_disc_otherside）が
  1.17.1クライアントにどう見えるか（代替：music_disc_13）
- 1.18サーバー側の新しい音（block.growing_plant.crop、item.bundle.* 3件、
  music.overworld.* 8件、music_disc.otherside）が1.17.1クライアントで
  どう聞こえるか（代替：block.crop.break、entity.item.pickup、music.game、
  music_disc.13）
- 1.18サーバー側の新しいパーティクル（block_marker）が1.17.1クライアントで
  どう見えるか（代替：barrier）
- 1.18サーバー側の新しいバイオーム6件（meadow、grove、snowy_slopes、
  frozen_peaks、jagged_peaks、stony_peaks）が1.17.1クライアントにどう見えるか
  （代替：plains、snowy_taiga、snowy_tundra、snowy_mountains、mountains）
- 1.18サーバー側の改名されたバイオーム11件が1.17.1クライアントにどう見えるか
  （例：snowy_plains→snowy_tundra、windswept_hills→mountains、
  windswept_forest→wooded_mountains）
- **他プレイヤーのチャットが見えるか**：Player Chat（759→756）は
  署名付きのため落とす（翻訳子として。なお現状は Join Game が落とされるため
  756クライアントは759サーバーに参加できない）。仮に参加できた場合、他プレイヤーの
  チャットは表示されないはず。サーバーからのお知らせ
  （System Chat→chat）は表示される。実機で確かめること
- 1.17.1クライアントに756用パック（pack_format 7、平文 pack.mcmeta）が
  提示され、適用されるか（実バニラjarのjavapによる根拠のみで、
  実機での受け入れ確認は未実施）
- **2人同時参加（他プレイヤーの出現・スキン・チャット）**。
  偽クライアントでは、直接接続でもプロキシ経由でも、他プレイヤーの
  出現パケットが30秒以内に届かなかった（1.19.3・1.19.1・1.18と同じ）。
  **実機クライアントで2人同時に遊び、相手が見えるかどうかを確かめるのが
  最も重要な残りの検証である**。相手が見えない場合は、ログの文言と一緒に
  報告すること
- 763〜775のサーバーに756クライアントが参加できないこと
  （Join Game／Respawn が落とされるため。バグではない。下の「7.」を参照）
- 756サーバーに774クライアントが参加したとき、Join Game は通るが
  Respawn が落とされること（死亡後のリスポーンで確かめる）
- 756サーバーに763〜765クライアントが参加したとき、Join Game／Respawn とも通ること
  （下の「6.」を参照）。766以上のクライアントでは Join Game は通るが Respawn が
  落とされること（下の「6.」を参照）
- 1.17.1クライアントで1.18サーバーのチャンクがどう見えるか
  （756は16セクション＋バイオーム配列、757は24セクション＋パレット＋光。
  757→756では光が落ちる。暗い場所がどう見えるかを確かめること）

## 1. 起動

### 1.1 準備（初回のみ）

```sh
cd ~/workspace/detergentmc-756
./gradlew build :bubbles:hello-bubble:jar
cp bubbles/hello-bubble/build/libs/hello-bubble-0.1.0-1.20.4.jar run/bubbles/
```

`run/eula.txt` と `run/vanilla/eula.txt` が `eula=true` になっていること。

### 1.2 起動（ファットjar・推奨）

```sh
cd ~/workspace/detergentmc-756
JAVA_HOME=/home/hatch/workspace/.tools/jdk-17.0.21+9 \
  java -jar build/libs/detergentmc-0.1.0-1.20.4.jar
```

`detergentmc.yml` の `minecraft-version` を `1.17.1` にして起動すると、
内部756サーバーが Java 16 で起動する。`1.18`／`1.18.1` にすると内部757サーバー、
`1.19` にすると内部759サーバー、`1.19.1`／`1.19.2` にすると内部760サーバー、
`1.19.3` にすると内部761サーバー、`1.19.4` にすると内部762サーバー、
`1.20.1` にすると内部763サーバーになる（切り替えて試すこと）。
**1.17.1の内部サーバーは Java 16 で動く**（Temurin 16.0.2+7、
`~/.tools/jdk-16.0.2+7`）。**1.18／1.19系のサーバーは Java 17 で動く**。
Java 21／Java 25 は1.17.1・1.18・1.19系には要らない。
注意：DetergentMC 自体（プロキシ）は JDK 17 で動かす（core／protocolに
sealedクラスがありJDK 16ではコンパイル・実行不可）。内部1.17.1サーバーだけが
Java 16で起動する（`javaBinaryFor("1.17.1")` が返すバイナリは
`/home/hatch/.tools/jdk-16.0.2+7/bin/java`）。

### 1.3 接続（実機クライアント）

Minecraft Java Edition 1.17.1 のクライアントで `127.0.0.1:25565` に接続する。
オフラインモード（`online-mode: false`）の場合は、ランチャーのプロファイルで
オフラインモードを有効にするか、適当な名前で接続すること。

### 1.4 ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。**クライアントは必ずここに接続する** |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`127.0.0.1` のみ。直接つながないこと） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。756用・757用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の19つを出す（758用・776用は無い） |

起動時に19つのパックが生成される：

| ファイル | 中身 |
| --- | --- |
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
が出れば配信開始。756用パックも同じポートの
`http://127.0.0.1:25567/detergentmc-pack-1.17.1.zip` で配信される。
756用パックの pack.mcmeta は平文 `{"pack_format":7}` で、
777の min_format／max_format 形式ではない（1.17.1には存在しない。
`docs/PROXY-1.17.1.md` を参照）。

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
**1.17.1クライアントが参加できるのは 1.17.1（756）と 1.18（757）の2行だけ**
（759〜775は Join Game が落とされるため参加できない。下の「5.」「7.」を参照）。
763以上のサーバーには参加できない（Join Game／Respawn が落とされるため。
バグではない。下の「7.」を参照）。

| `minecraft-version` | 内部プロトコル | データパック | バニラに渡すリソースパック | 注意 |
| --- | --- | --- | --- | --- |
| `1.17.1` | 756 | 7 | 756用（pack_format 7） | 1.17.1クライアントが参加できる（素通し）。**Java 16** |
| `1.18` / `1.18.1` | 757 | 8 | 757用（pack_format 8） | 1.17.1クライアントが参加できる（一段変換）。**Java 17** |
| `1.19` | 759 | 10 | 759用（pack_format 9） | 1.17.1クライアントは参加できない（Join Game／Respawn が落とされる） |
| `1.19.1` / `1.19.2` | 760 | 10 | 760用（pack_format 9） | 同上（参加できない） |
| `1.19.3` | 761 | 10 | 761用（pack_format 12） | 同上（参加できない） |
| `1.19.4` | 762 | 12 | 762用（pack_format 13） | 同上（参加できない） |
| `1.20` / `1.20.1` | 763 | 15 | 763用（pack_format 15） | 1.17.1クライアントは参加できない（Join Game／Respawn が落とされる） |
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

## 2. 参加（1.17.1クライアント）

1.17.1の本物のゲームクライアントで、プロキシの公開ポート
（既定25565）に接続する。サーバーリストには **1.17.1**（protocol 756）と
表示されるはずである。

参加直後にリソースパックの提示が出る（756用パック pack_format 7）。
「はい」を選ぶとパックが適用される。パックの提示が出ない場合は、
`resource-pack.enabled` が `true` になっていることと、25567番ポートの
配信を確認すること。

## 3. チェックリスト（1.17.1クライアント → 内部756サーバー）

`minecraft-version` を `1.17.1` にして起動し、1.17.1クライアントで接続する。
素通し（リレー）のため、1.17.1の世界がそのまま見えるはず。

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
- [ ] スカルクセンサーが動く（1.17.1にあるもの。振動を検知して信号を出す）
- [ ] 2人目が参加したとき、相手が見える（Player Info・出現パケット）。
  **ここが最重要の未確認項目**。相手が見えない・相手が馬に見える・
  姿がおかしい場合は、そのままの様子を報告すること
- [ ] music_disc_otherside が無いことを確認する
  （クリエイティブの一覧に出ない。1.18の要素で1.17.1には無い）
- [ ] block_marker のパーティクルが無いことを確認する
  （1.18の要素。1.17.1には barrier／light がある）
- [ ] meadow・grove・snowy_slopes・frozen_peaks・jagged_peaks・stony_peaks の
  バイオームが無いことを確認する（1.18の要素で1.17.1には無い。
  代わりに snowy_tundra・mountains・wooded_mountains などがある）

## 4. チェックリスト（1.17.1クライアント → 内部757サーバー）

`minecraft-version` を `1.18` にして起動し、1.17.1クライアントで接続する。
一段変換（756↔757）。1.18の新要素が代替でどう見えるかが中心。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 自分のチャットがサーバーに届く（`/trigger d_hello` で挨拶が出る。
  756の chat が757の chat と同一形式のため素通し）
- [ ] 他プレイヤーのチャットが見えるか（756↔757はどちらも署名なし chat のため
  表示されるはず。**実機で確かめること**）
- [ ] サーバーからのお知らせ（System Chat）が表示されるか
  （1.18にも System Chat は無いため chat のまま表示されるはず）
- [ ] music_disc_otherside がジュークボックスでどう鳴るか（代替：music_disc_13）
- [ ] block.growing_plant.crop の音がどう聞こえるか（代替：block.crop.break）
- [ ] item.bundle.* の音がどう聞こえるか（代替：entity.item.pickup）
- [ ] music.overworld.* の音がどう聞こえるか（代替：music.game）
- [ ] block_marker のパーティクルがどう見えるか（代替：barrier）
- [ ] meadow バイオームがどう見えるか（代替：plains。
  `/locate biome minecraft:meadow` で探す）
- [ ] grove バイオームがどう見えるか（代替：snowy_taiga。
  `/locate biome minecraft:grove` で探す）
- [ ] snowy_slopes バイオームがどう見えるか（代替：snowy_tundra。
  `/locate biome minecraft:snowy_slopes` で探す）
- [ ] frozen_peaks・jagged_peaks・stony_peaks バイオームがどう見えるか
  （代替：snowy_mountains・mountains。
  `/locate biome minecraft:frozen_peaks` などで探す）
- [ ] snowy_plains バイオームがどう見えるか（代替：snowy_tundra。
  1.17.1では snowy_tundra という名前）
- [ ] windswept_hills・windswept_forest バイオームがどう見えるか
  （代替：mountains・wooded_mountains。1.17.1では旧名）
- [ ] 1.18サーバーのチャンクがどう見えるか（757は24セクション＋光内包。
  756向けに光が落ちる。洞窟・夜の暗さがどう見えるかを確かめること）
- [ ] インベントリのクリックができるか（756⇔757方向は変換するためできるはず）
- [ ] 死んでリスポーンできるか（Respawn の変換。simulationDistance は落とす）
- [ ] 2人目が参加したとき、相手が見えるか

## 5. 1.17.1クライアント → 内部759／760／761／762サーバー（参加できない）

`minecraft-version` を `1.19`／`1.19.1`／`1.19.3`／`1.19.4` にして起動し、
1.17.1クライアントで接続する。**参加できない**。サーバー→クライアントの
Join Game／Respawn が落とされるため（759〜762は763へ直されて
`handleServerJoinGame763` の is756() 分岐で落とされる。バグではない。
`docs/VERSIONS.md` の1.17.1マトリクスを参照）。確認するのは「参加できない
こと自体」である。

- [ ] 接続しても Join Game が届かず、参加できない（切断されるか、ローディングのまま進まない）
- [ ] プロキシログに Join Game の落としが記録されるか
- [ ] （参考）マングローブ・泥・スカルク・フロッグライト・アレイ・カエル・
  ウォーデン・竹・吊り看板・ラクダ・桜・スニッファーといった1.19系の新規要素は、
  756クライアントからはこの組み合わせでは確認できない（参加できないため）。
  新規要素の見え方は、756サーバーに1.19系クライアントで参加する「6.」の逆向きで確かめること
- [ ] 他プレイヤーのチャット（Player Chat 759→756は落とす）も、参加できない
  ためこの組み合わせでは確認できない。翻訳子としての落としは単体テストで確認済み

## 6. チェックリスト（1.18／1.19／1.20.1／1.21.11クライアント → 内部756サーバー）

`minecraft-version` を `1.17.1` にして起動し、1.18／1.19／
1.20.1／1.21.11クライアントで接続する。逆向き（新しいクライアントが
古いサーバーに参加する）の確認。

- [ ] 参加できる（切断されない）。スポーン地点に立つ
- [ ] 1.17.1の世界が正しく見えるか（ブロック状態・バイオームの逆向き変換）
- [ ] 1.18クライアントで1.17.1サーバーのチャットが見えるか
  （どちらも署名なし chat のため表示されるはず）
- [ ] 1.19クライアントで1.17.1サーバーのチャットが見えるか
  （System Chat への変換。Player Chat は756側に無いため落とす）
- [ ] インベントリのクリックができるか（763以上のクライアントからは
  Window Click／Set Creative Slot が落とされるためできない。
  757〜762クライアントからは未確認）
- [ ] 774クライアントで死んでリスポーンするとどうなるか
  （Respawn は落とされるため、リスポーンできない／切断されるはず。
  実機で確かめること）
- [ ] 763以上のクライアントで参加できないこと（Join Game／Respawn が
  落とされるため。バグではない。下の「7.」を参照）
- [ ] 2人目が参加したとき、相手が見えるか

## 7. わざと失敗するはずのもの（バグではない）

- 1.17.1クライアントで1.20.1〜26.1（763〜775）のサーバーに参加できない
  （763〜775→756の Join Game／Respawn が落とされるため）
- 1.17.1クライアントで1.18.2（758）のサーバーに参加できない
  （758は未対応でプロキシが断る）
- 26.2（776）のクライアントは参加できない（プロキシが断る。776は未対応）
- 26.4以降のクライアントは参加できない（プロキシが断る）
- 1.19サーバーで他プレイヤーのチャットが見えない
  （Player Chat 759→756は落とすため）
- 1.18サーバーで music_disc_otherside が music_disc_13 に聞こえる、
  block_marker のパーティクルが barrier に見える、meadow・grove・
  snowy_slopes・frozen_peaks・jagged_peaks・stony_peaks が plains・
  snowy_taiga・snowy_tundra・snowy_mountains・mountains に見える、
  music.overworld.* の音が music.game に聞こえる（757にしか無いため）
- 1.18サーバーで snowy_plains・windswept_hills・windswept_forest などが
  snowy_tundra・mountains・wooded_mountains に見える（1.18で改名されたため）
- 763以上のクライアントから756サーバーへのインベントリのクリックができない
  （Window Click／Set Creative Slot の756行きは未実装のため落とす）
- 774クライアントで756サーバーに参加したとき、死んでリスポーンできない
  （Respawn 756→774は落とされるため）
- レシピ帳・タグが空（Declare Recipes／Tags は未対応のため）
- `/hello` の直接入力ができない（`/trigger d_hello` を使う）

## 8. online-mode（認証成功時の確認項目）

### 8.1 確認項目（正規アカウントの1.17.1クライアントで実施）

- [ ] 正規アカウントで参加できる
- [ ] 他プレイヤーから見たスキンが正しく表示される（online-modeのとき）
- [ ] 認証失敗時（別アカウントでのなりすまし等）に理由付きで切断される
- [ ] online-modeの1.17.1クライアントとoffline-modeの1.17.1クライアントが同じサーバーで遊べる

## 9. 結果の記録

| 日付 | クライアント | サーバー | 結果 | 備考 |
| --- | --- | --- | --- | --- |
| （未実施） | 1.17.1 | 1.17.1（756） | — | — |
| （未実施） | 1.17.1 | 1.18（757） | — | — |
| （未実施） | 1.17.1 | 1.18.1（757） | — | 1.18.1はプロトコル757を共有 |
| （未実施） | 1.17.1 | 1.19（759） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17.1 | 1.19.1（760） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17.1 | 1.19.3（761） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17.1 | 1.19.4（762） | — | 参加できない（Join Game／Respawn が落とされる） |
| （未実施） | 1.17.1 | 1.20.1（763） | — | 参加できないはず（Join Game／Respawn が落とされる） |
| （未実施） | 1.18（757） | 1.17.1（756） | — | — |
| （未実施） | 1.19（759） | 1.17.1（756） | — | — |
| （未実施） | 1.21.11（774） | 1.17.1（756） | — | Join Game は通るが Respawn は落とされる |
| （未実施） | 1.17.1 | 1.17.1（756）2人同時 | — | 最重要：相手が見えるか |

結果を報告するときは、通った項目・通らなかった項目に加え、画面の文言・
ログの文言も一緒に報告すること（特に切断された場合のメッセージと、
相手が見えなかった場合の様子、チャットの警告が出た場合の文言）。

## 10. 終了

サーバーを止めるときはコンソールで `stop` を入力する。
`run/data/` のワールドデータは残るため、次回も同じワールドで遊べる。
注意：`VanillaServerHost.stop()` で `IllegalThreadStateException: process hasn't exited`
が出ることがある（全バージョン共通の既存の問題。直していない。
実バニラ1.17.1の起動確認でも再現した。プロセスはその後 destroy される）。

## 付録A：実バニラ1.17.1の起動確認（実施済み・実測値）

`docs/PROXY-1.17.1.md` の「Java 16」と Stage D §15.3.4の記録。
実バニラ1.17.1を `VanillaServerHost` 経由で起動したときの実測値：

- 環境：`run/check-1171/vanilla`（`run/` は `.gitignore` 対象。
  jarは `run/vanilla/server-1.17.1.jar` のコピー、sha1照合済み・
  `download()` が「Reusing vanilla server jar (SHA-1 matches)」で再利用）、
  ポート25573（起動前に空きを確認、25570〜25573にリスナーなし）、
  online-mode=false、eula=true、ヒープ1024MB、Java 16.0.2+7
- **1回目で Done まで到達**：`Starting minecraft server version 1.17.1`
  （18:34:39 UTC）→ **`Done (115.618s)!`**（18:36:39 UTC）。
  `waitForReady` 600秒で **READY=true、ELAPSED-SECS=166**
  （ドライバ起動からDoneまで）。ログの再確認で Done 1件・
  `Error reading pack metadata` 0件・pack関連のWARN／ERROR 0件
- データパック：生成した `world/datapacks/detergentmc.zip` のmcmetaは平文
  `{"pack":{"pack_format":7,"description":"DetergentMC"}}`（5エントリ、
  複数形 functions／tags/functions）。ログに
  `Found new data pack file/detergentmc.zip` 1件。
  `server.properties` は server-port=25573・server-ip=127.0.0.1・
  online-mode=false・network-compression-threshold=-1・spawn-protection=0
- 停止：Doneの3秒後に `Stopping the server`→`Saving worlds` まで正常に進行。
  ただし既知の `VanillaServerHost.stop()` の問題が再現：
  20秒以内にプロセスが終了せず `Vanilla server did not stop in 20s; destroying it`
  → `STOP-THREW IllegalThreadStateException: process hasn't exited`
  （EXIT-CODE=null）。全バージョン共通の問題で、**直していない**（報告のみ）。
  ポート25573は閉じ、check-1171のプロセス残存なし
- 補足（Java 16の実現方法）：ドライバ自体はJDK 17で動かす必要があった
  （core／protocolにsealedクラスがありJDK 16ではコンパイル・実行不可）。
  そのためドライバをJDK 17＋`-Djava.home=$HOME/.tools/jdk-16.0.2+7` で起動し、
  `javaBinaryFor("1.17.1")` が返すバイナリを
  `/home/hatch/.tools/jdk-16.0.2+7/bin/java` にした（ドライバ出力 `JAVA-BIN=` で
  確認済み）。本番コードの変更は無い

## 付録B：偽756クライアントでの参加手順（直接接続・プロキシ迂回）

実機クライアントの代わりに、Pythonソケットの偽756クライアントで
本物のバニラ1.17.1に直接接続する手順。`ProxyIntegrationTest.java` の
`Fake757Client` が雛形。プロキシ経由の4セルは Stage E の統合テストで
実施する（`docs/PROXY-1.17.1.md` の「統合テスト」を参照）。
**プロキシ経由の実行はこのVMでは未実施**（JVMのTCP遮断の見込み）。

### B.1 手順

1. 実バニラ1.17.1を `VanillaServerHost` 経由で起動する（付録Aの環境。
   ポート25573、online-mode=false、eula=true、ヒープ1024MB、Java 16.0.2+7）。
   `Done (115.618s)!` まで待つ
2. Pythonソケットでハンドシェイクを送る（protocol 756、状態2）
3. Login Start を送る（usernameのみ。例：`Steve`）
4. Login Success（0x02）を待つ（30秒以内）
5. Join Game（0x26）を待つ（30秒以内）。**確認済み：実バニラ1.17.1の
   Join Game は31,555バイト**（dimensionCodec に dimension_type 4件・
   biome 81件。設定フェーズが無いため codec が Join Game にインラインで入る）
6. **Client Information（0x05）は Join Game 受信後に送ること**。
   Login Success 直後に送ると切断する可能性があるため（1.18での実測。757 §15.2。
   1.17.1での再現は未確認）。7フィールドで、末尾は disableTextFiltering=false、
   allowServerListings無し
7. Spawn Position（0x4B）を待つ（30秒以内）
8. チャット（サーバー行き 0x03、{message string}のみ）を送る
9. 移動パケット（0x11）を送る
10. 接続が維持されていることを確認する

### B.2 確認項目と期待される結果

| 確認項目 | 期待される結果 | 状態 |
| --- | --- | --- |
| Join Game（0x26） | 31,555バイト。dimensionCodec に dimension_type 4件・biome 81件 | **確認済み**（ライブキャプチャ） |
| スポーン（Spawn Position 0x4B） | 0x4Bが届く | **確認済み**（直接接続 (a)。spawn_received=true） |
| チャンク（Chunk Data 0x22） | チャンクが届く。実キャプチャはチャンク-21,-17で13,449バイト。16セクション＋bitMap＋heightmaps NBT＋バイオームint[1024]、光なし、BitStorage non-straddling | **確認済み**（直接接続 (a) で Chunk Data 0x22＝462パケット・5,939,706バイトを受信。生キャプチャの往復もバイト完全一致） |
| チャット | サーバーに届き、ブロードキャスト（0x0F）が返る | **確認済み**（直接接続 (a)。サーバーログ `<Steve> hello from 1.17.1` で確認） |
| 移動（0x11） | 移動後も接続維持 | **確認済み**（直接接続 (a)。still_connected_after_move=true） |
| 756→757→756の往復 | Join Game がバイト完全一致（31,555→23,423→31,555、差分0） | **確認済み** |

### B.3 実ゲームクライアントでの確認は未実施

上記の偽クライアントでの確認は、あくまでプロトコルレベルの検証である。
本物の1.17.1ゲームクライアントでの参加・プレイ・2人同時参加は
**未実施**である。実機での確認はこの手順書の §2〜§6 のチェックリストで
行うこと。

## 付録C：外部配信（`resource-pack.url-756` / `sha1-756`）の設計

`detergentmc.yml` の `resource-pack.url-756` / `sha1-756` に外部URLとSHA-1を
書くと、756クライアントにはそのパックを出す（既定はloopback配信）。
