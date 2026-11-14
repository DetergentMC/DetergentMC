# DetergentMC 1.19.4 実機プレイテスト — チェックリスト

2026-11-13作成。1.19.4クライアント（プロトコル762）で1.19.4・1.20系・
1.21系サーバーに参加する実機確認のための手順と、結果の記録用。既存の
`PLAYTEST-1.20.1.md`（1.20.1クライアント用）・`PLAYTEST-1.20.2.md`
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
（1.21.11クライアント用）は直接いじらず、
こちらを1.19.4専用の手順書とする。

## 0. この手順書の読み方：プロトコル検証済み／実機でしか確認できないもの

DetergentMCの1.19.4対応は、ここに書く項目の多くを**プロトコルレベルでは
すでに検証済み**である（本物のバニラ子プロセス＋生ソケットで762クライアントを
装った統合テスト `ProxyIntegrationTest`、`ChunkTranslator762Test`、
`PlayTranslator762Test` など）。実機テストの目的は、プロトコルレベルでは
見えない「本物のゲームクライアントでの見た目・挙動」を確かめることである。
以下の2つに分けて読むこと。

### A. プロトコルレベルで既に検証済みの項目（実機では「同じことが起きるか」の確認）

- 762クライアントの1.19.4サーバー（内部762）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake762ClientJoinsRealVanilla1194ThroughProxy`）。
  素通し（リレー）。Join Game の codec に dimension_type と biome が含まれることを確認
- 762クライアントの1.20.1サーバー（内部763）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake762ClientJoinsRealVanilla1201ThroughProxy`）。
  ログイン成功のあと両方ともすぐプレイ状態へ進む。Join Game の portalCooldown
  を落として送る。Window Click／Set Creative Slot／Update Sign を変換する
- 762クライアントの1.21.11サーバー（内部774）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake762ClientJoinsRealVanilla12111ThroughProxy`）。
  プロキシが774の設定フェーズを消費し（Login Acknowledged＋Client Information を
  サーバーへ送る）、Registry Data（0x07）を保持してから Join Game を762形式に翻訳する
- 763クライアントの1.19.4サーバー（内部762）への参加・スポーン・移動・チャット
  （`ProxyIntegrationTest.fake763ClientJoinsRealVanilla1194ThroughProxy`）。
  762サーバーの Join Game に portalCooldown=0 を補って763形式にしてから送る
- 762↔763のレジストリ差分（ViaVersion `mapping-1.19.4.json` と minecraft-data で確認）：
  パケットID 0変更、ブロック状態 23,725→24,135件（+410）、アイテム 1,228→1,255件
  （+27。陶器の破片の改名4件、music_disc_relic、pitcher_pod、sniffer_egg、
  calibrated_sculk_sensor、トリム型5件、陶器の破片16件）、エンティティ 124件・同じ、
  音 1,454→1,474件（+20）、パーティクル 96→95件（cherry_leaves、egg_crack）、
  エンチャント 39件・同じ、エフェクト 33件・同じ（ただし765は0ベース、762・763は1ベース）、
  バイオーム 63→64件（cherry_grove）、ダメージ種別 42→44件（generic_kill、outside_border）、
  メニュー 25→24件（legacy_smithing→smithing）、ブロックエンティティ種別 40→41件
  （suspicious_sand→brushable_block）、絵画 30件・同じ、楽器 23件・同じ、属性 13件・同じ
- 762用リソースパック（pack_format 13）の生成と配信
  （`ResourcePackBuilder762Test`、`ResourcePackServer762Test`）
- `/trigger d_hello` / `d_lang_ja` / `d_lang_en` の動作（言語切替と保存を含む。
  762クライアントでも同じ。直接 `/hello` と打っても動かない。`/trigger d_hello` を使うこと）

### B. 実機でしか確認できない項目（この手順書の中心）

- 本物の1.19.4クライアントが、1.20.1／1.21.11サーバーの Join Game codec
  （trim の追加フィールド、damage_type の `effects`、cherry_grove 入りの
  バイオーム一覧）を無視して参加できるか
- 1.20.1／1.21.11サーバー側の新しいブロック（sniffer_egg、calibrated_sculk_sensor、
  suspicious_gravel、pitcher_crop／pitcher_plant、チェリー系）が1.19.4クライアントに
  どう見えるか（代替：turtle_egg、sculk_sensor、gravel、wheat、peony、falling_cherry_leaves）
- 新しいアイテム（music_disc_relic、pitcher_pod、sniffer_egg、calibrated_sculk_sensor、
  トリム型5件、陶器の破片16件）が1.19.4クライアントにどう見えるか
  （代替：music_disc_cat、wheat_seeds、turtle_egg、sculk_sensor、sentry_armor_trim_smithing_template、
  pottery_shard_archer）
- 1.19.4の装飾（トリム）が1.20.1／1.21.11サーバーで正しく見えるか
- 看板の表裏（isFrontText）が1.19.4クライアントで正しく見えるか
- 死亡メッセージ（Death Combat Event の entityId を playerId で代入したもの）が
  1.19.4クライアントで正しく出るか
- 1.19.4クライアントに762用パック（pack_format 13）が提示され、適用されるか
- 2人同時参加（他プレイヤーの出現・スキン・チャット）。`named_entity_spawn`（0x03）を
  実バニラ1.19.4が送るかどうかは未確認

## 1. 起動

### 1.1 準備（初回のみ）

```sh
cd ~/workspace/detergentmc
./gradlew build :bubbles:hello-bubble:jar
cp bubbles/hello-bubble/build/libs/hello-bubble-0.1.0-1.20.4.jar run/bubbles/
```

`run/eula.txt` と `run/vanilla/eula.txt` が `eula=true` になっていること。

### 1.2 起動（ファットjar・推奨）

```sh
java -Dfile.encoding=UTF-8 -jar build/libs/detergentmc-0.1.0-1.20.4.jar
```

Gradle経由の `./gradlew :detergentmc-core:runServer` ではコンソール入力が
届かないので、対話するなら上の `java -jar` で起動すること。

起動完了の目安：`Done (...)!` と `バニラサーバーが起動しました (ポート 25565)。`

この手順書の参加テストでは、サーバーはまず **1.19.4** のまま
（`minecraft-version: "1.19.4"`、既定ではないため設定で変える。下の「1.5」を参照）
で、1.19.4クライアントがプロキシ経由で参加する。サーバー自体を
1.20系・1.21系で動かす場合は下の「1.5 サーバーを別バージョンで動かす場合」を参照すること。

### 1.3 ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。**クライアントは必ずここに接続する** |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`127.0.0.1` のみ。直接つながないこと） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用の13つを出す |

起動時に13つのパックが生成される：

| ファイル | 中身 |
| --- | --- |
| `run/data/resource-pack/detergentmc-pack-1.19.4.zip` | 762用（pack_format 13）。プロキシが762クライアントにだけ出す |
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

コンソールに `リソースパックを配信しています: http://127.0.0.1:25567/detergentmc-pack.zip`
が出れば配信開始。762用パックも同じポートの
`http://127.0.0.1:25567/detergentmc-pack-1.19.4.zip` で配信される。

### 1.4 online-mode／offline-modeの切替方法

`run/detergentmc.yml` の `server.online-mode` を変える。既定は `true`
（プロキシがMojang認証を完結させる）。実機で正規アカウントを使わない
確認（オフラインの動作確認だけ）なら `false` にする。内部バニラは常に
offline-mode（信頼済み接続）で動くため、ここの切替はプロキシ認証の
オン／オフである。

```yaml
server:
  online-mode: false
```

### 1.5 サーバーを別バージョンで動かす場合

`run/detergentmc.yml` の `minecraft-version` を変えて再起動する。
内部プロトコル・データパック形式・バニラに渡すリソースパックが連動して変わる。

| `minecraft-version` | 内部プロトコル | データパック | バニラに渡すリソースパック | 注意 |
| --- | --- | --- | --- | --- |
| `1.19.4` | 762 | 12 | 762用（pack_format 13） | **Java 17**。1.19.4クライアントは素通し |
| `1.20` / `1.20.1` | 763 | 15 | 763用（pack_format 15） | **Java 17**。1.19.4クライアントは762↔763の直接変換 |
| `1.20.2` | 764 | 18 | 764用（pack_format 18） | **Java 17**。プロキシが764の設定フェーズを消費する |
| `1.20.3` / `1.20.4` | 765 | 26 | 765用（pack_format 22） | **Java 17**。プロキシが765の設定フェーズを消費する |
| `1.20.5` / `1.20.6` | 766 | 41 | 766用（pack_format 32） | **Java 21 が必要** |
| `1.21` / `1.21.1` | 767 | 48 | 767用（pack_format 34） | **Java 21 が必要** |
| `1.21.2` / `1.21.3` | 768 | 57 | 768用（pack_format 42） | **Java 21 が必要** |
| `1.21.4` | 769 | 61 | 769用（pack_format 46） | **Java 21 が必要** |
| `1.21.5` | 770 | 71 | 770用（pack_format 55） | **Java 21 が必要** |
| `1.21.6` | 771 | 80 | 771用（pack_format 63） | **Java 21 が必要** |
| `1.21.7` / `1.21.8` | 772 | 81 | 772用（pack_format 64） | **Java 21 が必要** |
| `1.21.9` / `1.21.10` | 773 | 88 | 773用（pack_format 69） | **Java 21 が必要** |
| `1.21.11` | 774 | 94 | 774用（pack_format 75） | **Java 21 が必要**（下の注意を参照） |

#### Java 21 の注意（1.20.5／1.20.6／1.21／1.21.1／1.21.2／1.21.3／1.21.4／1.21.5／1.21.6／1.21.7／1.21.8／1.21.9／1.21.10／1.21.11サーバーのときだけ）

バニラ1.21.11サーバーは Java 21 を要求する。DetergentMC本体は
Java 17 で動く。`VanillaServerHost` は次の順番で Java 21 を探す。

1. `detergentmc.java21.home` システムプロパティ、
2. `DETERGENTMC_JAVA21_HOME` 環境変数、
3. 現在の `java.home` が Java 21 以上のときはそれ、
4. どれも無いときは現在の Java を使うが、バニラが起動を拒否する。

ファットjarを Java 17 で起動したまま、内部だけ Java 21 にする例：

```sh
DETERGENTMC_JAVA21_HOME=$HOME/workspace/.tools/jdk-21.0.13+9 \
  java -Dfile.encoding=UTF-8 -jar build/libs/detergentmc-0.1.0-1.20.4.jar
```

ファットjar自体を Java 21 で起動してもかまいません（その場合は3. の経路）。

**1.19.4／1.20.1／1.20.2／1.20.3／1.20.4サーバーは Java 17 で動きます。**
Java 21 の指定は要りません。

## 2. 参加（1.19.4クライアント）

1.19.4の本物のゲームクライアントで、プロキシの公開ポート
（既定25565）に接続する。サーバーリストには **1.19.4**（protocol 762）と
表示されるはずである。

参加直後にリソースパックの提示が出る（762用パック pack_format 13）。
「はい」を選ぶとパックが適用される。パックの提示が出ない場合は、
`resource-pack.enabled` が `true` になっていることと、25567番ポートの
配信を確認すること。

## 3. 確認すること（チェック欄）

### 3.1 参加・スポーン（内部762サーバーのとき）

- [ ] 1.19.4クライアントで参加できる（切断されない）
- [ ] スポーン地点の地形が正しく見える（地面・木・水）
- [ ] チャットで発言できる（署名付きチャット。送信できること）
- [ ] 移動できる（歩く・走る・ジャンプ）
- [ ] `/trigger d_hello` で挨拶が出る（直接 `/hello` と打っても動かない。仕様）
- [ ] `/trigger d_lang_ja` / `d_lang_en` で言語が切り替わる

### 3.2 1.19.4の要素（内部762サーバーのとき）

- [ ] 防具の装飾（トリム）が見える。鍛冶台でトリム型＋素材を使って防具を装飾できる
- [ ] 陶器の破片（pottery_shard_archer／prize／arms_up／skull）が手に入る。
  怪しげな砂をブラシで払うと出る
- [ ] 飾り壺（decorated_pot）が置ける。cracked なしの見た目になる
- [ ] 桜の苗木・桜の木は1.19.4には無い（実験的データパックの内容）。無いことを確認する
- [ ] スニッファー・ピッチャープラント・較正されたスカルクセンサーは1.19.4には無い。
  無いことを確認する

### 3.3 1.20.1サーバー（内部763）に参加したとき

- [ ] 1.19.4クライアントで参加できる（切断されない）
- [ ] スポーン地点の地形が正しく見える
- [ ] チャット・移動ができる
- [ ] スニッファーの卵・ピッチャー・怪しげな砂利・較正されたスカルクセンサーが
  どう見えるか記録する（代替：タマゴ・小麦・砂利・スカルクセンサーの予定）
- [ ] music_disc_relic がどう見えるか記録する（代替：music_disc_cat の予定）
- [ ] 看板を置いて文字を書ける。表裏の切り替えが正しく見える
- [ ] 死亡したときに死亡メッセージが正しく出る（entityId の代入値の確認）
- [ ] 鍛冶台で防具の装飾ができる（legacy_smithing→smithing の変換の確認）
- [ ] 進捗（Advancements）が開ける。表示が崩れていない

### 3.4 1.21.11サーバー（内部774）に参加したとき

- [ ] 1.19.4クライアントで参加できる（切断されない）
- [ ] スポーン地点の地形が正しく見える
- [ ] チャット・移動ができる
- [ ] 1.21.11の新しいブロック・アイテムがどう見えるか記録する
  （762↔763の代替のあと、763↔774の変換が重なる）
- [ ] 進捗が開ける

### 3.5 リソースパック

- [ ] 762用パック（pack_format 13）が提示され、「はい」で適用される
- [ ] パック適用後に切断されない
- [ ] 1.20.1サーバー（内部763）のときも762用パックが出る（763用パックではない）
- [ ] 1.21.11サーバー（内部774）のときも762用パックが出る（774用パックではない）

## 4. わざと失敗するはずのもの（バグではない）

- 1.19.4クライアントで、1.20.1／1.21.11サーバーの新しいブロック
  （スニッファーの卵、較正されたスカルクセンサー、怪しげな砂利、
  ピッチャークロップ、チェリーの葉）は、近い既存のブロックに代替される。
  壊れているのではなく、**代替の仕様**である。見た目を記録すること。
- 1.19.4クライアントで、1.20.1／1.21.11サーバーの新しいアイテム
  （music_disc_relic、pitcher_pod、sniffer_egg、calibrated_sculk_sensor、
  新しいトリム型5件、新しい陶器の破片16件）は、近い既存のアイテムに代替される。
  同じく代替の仕様である。
- 1.19.4クライアントで、1.20.1／1.21.11サーバーの cherry_grove バイオームは
  meadow に代替される。桜のバイオームが草原に見えるのは仕様である。
- 直接 `/hello` と打っても動かない。`/trigger d_hello` を使うこと（仕様）。
- Declare Recipes／Statistics／Tags／World Event は全方向で未対応のため、
  レシピブックの表示が欠ける場合がある（既存の制限）。

## 5. online-mode（認証成功時の確認項目）

- [ ] 正規アカウントで参加できる
- [ ] 他プレイヤーから見た自分のスキンが正しく見えるか記録する
  （763＋online-modeで他プレイヤーのスキンが既定になる場合がある既知の制限。
  762でも同じ制限があるか確認する）
- [ ] 2人同時参加で、相手の出現・移動・チャットが見えるか
- [ ] 2人同時参加で、相手の名前がタブリストに出るか

## 6. 結果の記録（ここに書く）

- 日時：
- クライアント：1.19.4（本物）
- サーバー：1.19.4（内部762）／1.20.1（内部763）／1.21.11（内部774）のどれか
- 参加：○／×（×の場合の切断メッセージ：
- スポーン・地形：○／×（
- チャット・移動：○／×（
- トリム：○／×（
- 看板：○／×（
- 死亡メッセージ：○／×（
- リソースパック（762用）：○／×（
- 新しいブロックの見た目（1.20.1／1.21.11サーバー）：
- 新しいアイテムの見た目（1.20.1／1.21.11サーバー）：
- 2人同時参加：○／×（
- その他気づいたこと：

## 7. 終了

1. クライアントを切断する。
2. サーバーを止める（コンソールで `stop`、または Ctrl+C）。
3. 結果をこのファイルの「6. 結果の記録」に書く。

## 8. 別バージョンのサーバーへの参加（1.19.4対応のマトリクス）

1.19.4クライアントで参加できるサーバーは次の通り（`minecraft-version` で切り替える）。

| サーバー | 内部プロトコル | 参加 | 備考 |
| --- | --- | --- | --- |
| 1.19.4 | 762 | ○（統合テスト済み） | 素通し |
| 1.20／1.20.1 | 763 | ○（統合テスト済み） | 762↔763の直接変換 |
| 1.20.2 | 764 | △ | プロキシが設定フェーズを消費する。統合テストはまだ無い |
| 1.20.3／1.20.4 | 765 | △ | 同上 |
| 1.20.5／1.20.6 | 766 | △ | 同上。Java 21 が必要 |
| 1.21／1.21.1 | 767 | △ | 同上。Java 21 が必要 |
| 1.21.2／1.21.3 | 768 | △ | 同上。Java 21 が必要 |
| 1.21.4 | 769 | △ | 同上。Java 21 が必要 |
| 1.21.5 | 770 | △ | 同上。Java 21 が必要 |
| 1.21.6 | 771 | △ | 同上。Java 21 が必要 |
| 1.21.7／1.21.8 | 772 | △ | 同上。Java 21 が必要 |
| 1.21.9／1.21.10 | 773 | △ | 同上。Java 21 が必要 |
| 1.21.11 | 774 | ○（統合テスト済み） | プロキシが設定フェーズを消費する。Java 21 が必要 |

○=統合テスト済み、△=単体テストのみで統合テストなし、×=できない。

## 付録：外部配信（`resource-pack.url-762` / `sha1-762`）の設計

`detergentmc.yml` の `resource-pack.url-762` / `sha1-762` に外部URLを書くと、
762クライアントにはそのURLのパックが出る。書かない場合は762用パック
（pack_format 13）がloopback（127.0.0.1:25567）で配信される。
外部URLを書くときは pack_format 13 のパックを用意すること。
763用パック（pack_format 15）を762クライアントに出しても、
本物の1.19.4クライアントが適用するかは未確認である。
