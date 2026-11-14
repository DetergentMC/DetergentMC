# DetergentMC 1.20.1 実機プレイテスト — チェックリスト

2026-11-08作成。1.20.4サーバーに1.20.1クライアントで参加する実機確認のための
手順と、結果の記録用。既存の `PLAYTEST-1.20.4.md`（1.20.4クライアント用）は
直接いじらず、こちらを1.20.1専用の手順書とする。

## 0. この手順書の読み方：プロトコル検証済み／実機でしか確認できないもの

DetergentMCの1.20.1対応は、ここに書く項目の多くを**プロトコルレベルでは
すでに検証済み**である（本物のバニラ1.20.4子プロセス＋生ソケットで763
クライアントを装った統合テスト `ProxyIntegrationTest`、偽バニラ相手の
`ClientProxyTest`、`ProxySessionTest`）。実機テストの目的は、プロトコル
レベルでは見えない「本物のゲームクライアントでの見た目・挙動」を確かめる
ことである。以下の2つに分けて読むこと。

### A. プロトコルレベルで既に検証済みの項目（実機では「同じことが起きるか」の確認）

- 763クライアントの参加・スポーン・移動・チャット（`ProxyIntegrationTest`）
- `/trigger d_hello` / `d_lang_ja` / `d_lang_en` の動作（言語切替と保存を含む）
- サーバーリストのpingでバージョンが **1.20.1**（protocol 763）と表示されること
- 763クライアントへのリソースパックのオファー：Join Game直後にプレイ状態の
  Resource Pack Send（0x40）が送られ、URLとSHA-1が **763用パック
  （pack_format 15、`detergentmc-pack-1.20.1.zip`）** のものであること。
  クライアントの応答（0x24）はバニラのパックのUUID付きでバニラへ返される
  こと（`ProxySessionTest.configAddResourcePackIsSentAs763PackAfterJoinGame`）
- 763用パックのzip内容：`pack.mcmeta` が `pack_format: 15` で、泡テクスチャ
  （16x16 PNG）・泡モデル・スライムボールの `CustomModelData: 1` オーバーライド
  が入っていること（`ResourcePackBuilderTest`）
- 763用パックと765用・764用パックの配信：同じ25567番ポートで3つのURLが200を返し、
  SHA-1が一致すること（`ResourcePackServerTest`、`ResourcePackWiringTest`）
- 763＋online-modeの認証：プロキシがMojang認証を完結させ、認証済みUUID・名前で内部バニラへ繋ぐこと（`ProxySessionAuthTest`、`ClientProxyAuthTest`、偽セッションサーバーでの検証）。認証失敗時は理由付きLogin Disconnectで切断されること
- チャンクのブロック状態IDリマップ（`RegistryRemapper`／`ChunkTranslator`、26644件）と、Respawn／次元移動・Boss Bar・Teams・Advancements・Tab Complete・Explosion・Start Configurationの変換（コミット `0741b66`、テスト341件）
- ステータス・Keep Alive・移動パケットのID付け替え、チャットの双方向変換

### B. 実機でしか確認できない項目（この手順書の本題）

- リソースパックがクライアントの画面で「互換性あり」と表示され、実際に
  ダウンロード・適用されること（プロトコル検証では、クライアントがパックを
  受け取って適用したかどうかまでは見えない）
- 泡テクスチャが**目で見て**泡に見えること（スライムボールが泡の絵になるか）
- スポーン地点の風景・チャンク・ブロックが正しく表示されること
  （1.20.3以降の新ブロックは近いブロックへ代替表示される。目で見て確認する）
- 次元移動（ネザー／エンド）で画面に何が起きるか（Respawn変換は
  プロトコルレベルで実装済み。実機で正しく切り替わるか確認する）
- online-modeでのMojang認証の成否（実アカウントが必要。下の「5. online-mode」を参照）
- 体感の遅延・切断の有無、スキンの表示、サウンドなど

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

### 1.3 ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。**クライアントは必ずここに接続する** |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`127.0.0.1` のみ。直接つながないこと） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用の3つを出す |

起動時に3つのパックが生成される：

| ファイル | 中身 |
| --- | --- |
| `run/data/resource-pack/detergentmc-pack.zip` | 765用（pack_format 22）。`server.properties` の `resource-pack` に書かれる |
| `run/data/resource-pack/detergentmc-pack-1.20.1.zip` | 763用（pack_format 15）。プロキシが763クライアントにだけ出す |
| `run/data/resource-pack/detergentmc-pack-1.20.2.zip` | 764用（pack_format 18）。プロキシが764クライアントにだけ出す |

コンソールに `リソースパックを配信しています: http://127.0.0.1:25567/detergentmc-pack.zip`
が出れば配信開始。763用パックも同じポートの
`http://127.0.0.1:25567/detergentmc-pack-1.20.1.zip` で配信される。

### 1.4 online-mode／offline-modeの切替方法

切り替えは `run/detergentmc.yml` の `server.online-mode` だけを見る。
`run/vanilla/server.properties` の `online-mode` は起動時に常に `false`
で上書きされる（内部バニラ＝プロキシからの信頼済み接続）。手動で
`true` に書き換えないこと。

```yaml
# run/detergentmc.yml
server:
  online-mode: true    # 既定。プロキシがMojang認証を行う
```

- `true`：正規のMojangアカウントでの参加が前提。1.20.1・1.20.4の
  両クライアントとも、このままで参加できる。1.20.1はプロキシが認証を
  完結させる（下の「5. online-mode」を参照）。
- `false`：オフライン（非正規）クライアントでも参加できる。

## 2. 参加（1.20.1クライアント）

- クライアント：Minecraft: Java Edition **1.20.1**（Java 17）
- アドレス：`localhost:25565`
- **25566（バニラの内部ポート）に直接つながないこと。** 25566に直接つなぐと
  1.20.1クライアントは弾かれる（プロキシを通す必要がある）。
- サーバーリストでバージョンが **1.20.1** と表示されることを確認する
  （プロトコルレベルでは検証済み。実機での表示確認が目的）。

## 3. 確認すること（チェック欄）

### 3.1 基本（プロトコル検証済み・実機で再確認）

- [ ] 参加できた。スポーン地点に立てる
- [ ] 参加時に自分の言語（既定は日本語）であいさつが表示された
- [ ] 移動できる。ブロックを壊せる／置ける
- [ ] チャット（`/` なし）が全体に流れる
- [ ] `/list` で参加者一覧が出る
- [ ] `/trigger d_hello` であいさつが表示される
- [ ] `/trigger d_lang_en` で英語に切り替わる → `/trigger d_hello` が英語になる
- [ ] `/trigger d_lang_ja` で日本語に戻る
- [ ] 再参加しても言語設定が引き継がれる
- [ ] サーバーリストでバージョンが **1.20.1** と表示される

### 3.2 リソースパック（実機でしか確認できない部分が中心）

- [ ] 参加時にリソースパックの適用を求められた。「はい」を選ぶ
- [ ] パックの画面で「互換性あり」と表示される（763用パックは pack_format 15
      なので、互換性があるはず。もし「互換性なし」「古い」等の表示が出たら
      その文言を記録すること。**旧版（pack_format 22のみ）の既知の表示とは別**）
- [ ] パックの適用後、切断されずに遊び続けられる
- [ ] （確認用）サーバーを止めた状態で `run/data/resource-pack/` を見て、
      `detergentmc-pack-1.20.1.zip` があること。`unzip -p
      detergentmc-pack-1.20.1.zip pack.mcmeta` で `pack_format: 15` が
      出ること

### 3.3 泡テクスチャ（実機でしか確認できない）

OPで次のコマンドを打つ（1.20.4ではNBT形式。`[custom_model_data=1]` の形式は
1.20.5以降なので使わないこと）：

```
/give <自分の名前> minecraft:slimeball{CustomModelData:1} 1
```

- [ ] スライムボールが泡のテクスチャ（水色の丸い泡・白いハイライト）で見える
- [ ] 普通のスライムボール（CustomModelDataなし）は従来の見た目のまま
- [ ] 泡を持って振っても、インベントリでも泡に見える

### 3.4 次元移動（実機での挙動を記録する項目）

Respawnパケットの変換はプロトコルレベルで実装済み（コミット `0741b66`、テスト341件）。
実機で正しく切り替わるか確認する：

- [ ] ネザーポータルに入って、正しくネザーに移動できる
- [ ] （OPの場合）`/execute in minecraft:the_nether run tp <自分の名前> ~ ~ ~`
      などで次元移動を試みたとき、正しく移動できる
- [ ] 次元移動を試みたあと、元の世界に戻って遊び続けられる
- [ ] 移動時に画面・メッセージ・切断の有無など、気づいたことを記録する

### 3.5 1.20.3以降の新ブロック（目で見て確認）

チャンクのブロック状態IDリマップは実装済みで、1.20.3以降の新ブロック
（crafter等）は763クライアントで近いブロックへ代替表示される：

- [ ] スポーン周辺のブロックがおかしく表示されていないか（欠けたテクスチャ・
      違うブロックに見える等）。おかしいものがあれば座標と見た目を記録する
- [ ] （OPの場合）`/give <自分の名前> minecraft:crafter 1` でcrafterを持ち、
      置いたときの見た目を記録する（crafting_tableに見えるはず）

## 4. わざと失敗するはずのもの（バグではない）

- `/hello` や `/lang` を直接打つとバニラが「Unknown command」と返す。
  仕様。`/trigger d_hello` などを使うこと。
- リソースパックは `127.0.0.1` 配信のみ。同じマシンからの参加でのみ届く。
  外部プレイヤーには届かない（外部配信は `resource-pack.url` /
  `url-763` に `https://` のURLを指定する仕組みがあるが、置き場所は未定）。
- 1.20.3以降の新ブロックは、チャンクのブロック状態IDリマップで近いブロックへ
  代替表示される（3.5参照）。表示ずれが残るのは、Declare Recipes／Statistics／
  Tags／World Event IDが未対応で落とされること、ブロックエンティティの
  crafter／trial_spawnerがパケットごと落とされること、Entity Metadataの
  variant系がエントリごと落とされること、Explosionの765専用パーティクル／
  サウンド指定が既定演出に置き換わること（既知の制限。バグではない）。

## 5. online-mode（認証成功時の確認項目）

プロキシがMojang認証を自分で完結させる方式で、763＋online-modeの参加が
できるようになった。認証はプロキシが行い、内部バニラはoffline-modeの
信頼済み接続として受け取る。`server.session-server-url` は既定では空
（＝本物のMojangセッションサーバー）で、変える必要はない。

### 5.1 確認項目（正規アカウントの1.20.1クライアントで実施）

- [ ] `online-mode: true`（`session-server-url` は空のまま）で正規の
      Mojangアカウントの1.20.1クライアントが参加できる
- [ ] 参加したプレイヤーのUUID・名前が正規アカウントのものになる
      （バニラのログの `UUID of player ...` 行で確認）
- [ ] 非正規（オフライン）クライアントでは参加できず、
      「Mojang認証に失敗しました」と出て切断される
- [ ] 参加・スポーン・移動・チャット・`/trigger d_hello` がonline-modeでも通る
      （3.1と同じ項目）
- [ ] リソースパック（763用）が適用される
- [ ] 1.20.4クライアントのonline-mode参加に影響がない（回帰確認：
      同じく正規アカウントで参加できる）
- [ ] 自分のスキンが正しく表示される。別の正規アカウントのプレイヤーから
      見ると自分のスキンが既定スキンになる場合がある（既知の制限。記録すること）
- [ ] サーバーを止めた状態で `run/vanilla/server.properties` を見て
      `online-mode=false`・`server-ip=127.0.0.1` であること

## 6. 結果の記録（ここに書く）

- 日時：
- 使ったクライアント（1.20.1・正規／オフライン）：
- サーバーの online-mode（true／false）：
- 通った項目：
- 通らなかった項目と、そのときの画面・ログの文言：
- リソースパックの表示（互換性あり／なし・文言）：
- 泡テクスチャの見た目：
- 次元移動で起きたこと：
- 新ブロックの表示で気づいたこと：
- その他気づいたこと：

## 7. 終了

コンソールで `stop`。バニラサーバーも一緒に止まること。

## 付録：外部配信（`resource-pack.url-763` / `sha1-763`）の設計

loopback配信（既定）では763用パックも自動生成・配信されるので、この設定は
不要である。外部のプレイヤーに763用パックを届けたい場合だけ使う：

```yaml
resource-pack:
  url: "https://example.com/packs/detergentmc-pack.zip"
  sha1: "0123456789abcdef0123456789abcdef01234567"  # 765用パックのSHA-1
  url-763: "https://example.com/packs/detergentmc-pack-1.20.1.zip"
  sha1-763: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"  # 763用パックのSHA-1
```

- `url-763` / `sha1-763` は任意。両方空なら763クライアントにも765用パックを
  出す（1.20.1には「互換性なし」と表示される）。
- `url-763` は `https://` で始まるものだけが使われる。`http://` などは
  警告ログを出して無視され、763クライアントには765用パックを出す。
- `sha1-763` はホストした763用zipそのもののSHA-1（40桁の16進数）。
  `sha1sum detergentmc-pack-1.20.1.zip` で確認した値を書く。DetergentMCは
  形だけを検証し、ホストされたファイルをダウンロードして照合することはしない
  （765用と同じ方針）。値が違う場合、クライアント側でパックが拒否される。
  ファイルを差し替えたら `sha1-763` も一緒に更新すること。
- `url-763` / `sha1-763` の不備は、765用パックの判定には影響しない。
  `required: true` の起動可否も765用パックだけで決まる。
- 763用パックの作り方：loopbackで一度起動して生成された
  `run/data/resource-pack/detergentmc-pack-1.20.1.zip` をホストに置き、
  そのSHA-1を `sha1-763` に書く。
