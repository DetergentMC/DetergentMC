# DetergentMC 1.20.2 実機プレイテスト — チェックリスト

2026-11-08作成。1.20.4サーバーに1.20.2クライアントで参加する実機確認のための
手順と、結果の記録用。既存の `PLAYTEST-1.20.1.md`（1.20.1クライアント用）・
`PLAYTEST-1.20.4.md`（1.20.4クライアント用）は直接いじらず、こちらを
1.20.2専用の手順書とする。

## 0. この手順書の読み方：プロトコル検証済み／実機でしか確認できないもの

DetergentMCの1.20.2対応は、ここに書く項目の多くを**プロトコルレベルでは
すでに検証済み**である（本物のバニラ1.20.4子プロセス＋生ソケットで764
クライアントを装った統合テスト `ProxyIntegrationTest`、
`ProxySession764Test`、`ChunkTranslator764Test` など）。実機テストの目的は、
プロトコルレベルでは見えない「本物のゲームクライアントでの見た目・挙動」を
確かめることである。以下の2つに分けて読むこと。

### A. プロトコルレベルで既に検証済みの項目（実機では「同じことが起きるか」の確認）

- 764クライアントの参加・スポーン・移動・チャット（`ProxyIntegrationTest.fake764ClientJoinsRealVanillaThroughProxy`）
- `/trigger d_hello` / `d_lang_ja` / `d_lang_en` の動作（言語切替と保存を含む）
- サーバーリストのpingでバージョンが **1.20.2**（protocol 764）と表示されること
- 764クライアントへのリソースパックのオファー：設定フェーズの
  Resource Pack Send（0x06、UUIDなし）と、プレイ状態の Resource Pack Send
  （0x42）に、URLとSHA-1が **764用パック（pack_format 18、
  `detergentmc-pack-1.20.2.zip`）** のものであること。
  クライアントの応答（設定 0x05・プレイ 0x27）はバニラのパックのUUID付きで
  バニラへ返されること（`ProxySession764Test`）
- 764用パックのzip内容：`pack.mcmeta` が `pack_format: 18` で、泡テクスチャ
  （16x16 PNG）・泡モデル・スライムボールの `CustomModelData: 1` オーバーライド
  が入っていること（`ResourcePackBuilderTest`）
- 764用パックと763用・765用パックの配信：同じ25567番ポートで3つのURLが
  200を返し、SHA-1が一致すること（`ResourcePackServerTest`、
  `ResourcePackWiringTest`）
- 764＋online-modeの認証：プロキシがMojang認証を完結させ、認証済みUUID・名前で内部バニラへ繋ぐこと（`ProxySession764Test`、偽セッションサーバーでの検証）。認証失敗時は理由付きLogin Disconnectで切断されること
- 設定フェーズの中継（Registry Data はそのまま通す）、Join Game（0x29）の
  素通し、Spawn Position（0x52）でのスポーン、ステータス・Keep Alive・
  移動パケットのID付け替え、チャットの双方向変換
- ネイティブ764リマップ表の配線：ブロック状態 8843（763表なら8827）と
  音 638（763表なら1264、764表なら1270）の変換がプロキシの経路で764表を
  使うこと（`ProxySession764Test`）
- 逆向き（765クライアントの1.20.2サーバーへの参加）：
  `ProxyIntegrationTest.fake765ClientJoinsRealVanilla1202ThroughProxy`
  で、本物のバニラ1.20.2子プロセスにプロキシ（内部764）経由で参加し、
  Join Game受信・スポーン・チャット送信（バニラログに `Rin` のチャット）・
  移動送信まで確認済み。配線は `ServerProtocolWiringTest` で確認済み

### B. 実機でしか確認できない項目（この手順書の本題）

- リソースパックがクライアントの画面で「互換性あり」と表示され、実際に
  ダウンロード・適用されること（プロトコル検証では、クライアントがパックを
  受け取って適用したかどうかまでは見えない）
- 泡テクスチャが**目で見て**泡に見えること（スライムボールが泡の絵になるか）
- スポーン地点の風景・チャンク・ブロックが正しく表示されること
  （1.20.3以降の新ブロックは近いブロックへ代替表示される。目で見て確認する）
- 次元移動（ネザー／エンド）で画面に何が起きるか（Respawn は764と765で
  同形式のため変換なしで通す。実機で正しく切り替わるか確認する）
- レシピ帳が空になること（Declare Recipes を落としているため。実機での
  見た目を記録する）
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

この手順書の参加テストでは、サーバーは **1.20.4** のまま
（`minecraft-version: "1.20.4"`、既定）で、1.20.2クライアントがプロキシ経由で
参加する。`minecraft-version: "1.20.2"` でサーバー自体を1.20.2で動かす
こともできるが、そちらは別の確認（下の「1.5 サーバーを1.20.2で動かす場合」）
で、データパック形式の要確認事項があるため分けて記録すること。

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
が出れば配信開始。764用パックも同じポートの
`http://127.0.0.1:25567/detergentmc-pack-1.20.2.zip` で配信される。

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

- `true`：正規のMojangアカウントでの参加が前提。1.20.2・1.20.1・1.20.4の
  クライアントとも、このままで参加できる。1.20.2はプロキシが認証を
  完結させる（下の「5. online-mode」を参照）。
- `false`：オフライン（非正規）クライアントでも参加できる。

### 1.5 サーバーを1.20.2で動かす場合（別確認）

`run/detergentmc.yml` の `minecraft-version: "1.20.2"` にすると、サーバー
自体がバニラ1.20.2で動く（アダプター `detergentmc-adapter-v1_20_2`）。
この場合の確認項目は別枠で記録すること：

- [x] バニラ1.20.2サーバーが `Done` まで起動する（**実起動で確認済み**。
      下の「実起動確認の記録（2026-11-09）」を参照）
- [ ] 1.20.2クライアントがプロキシ経由で参加できる（実機での確認はまだ。
      プロトコルレベルでは、765クライアントの参加は
      `ProxyIntegrationTest.fake765ClientJoinsRealVanilla1202ThroughProxy`
      で確認済み。764クライアントの内部764への参加は素通し（リレー）で、
      1.20.4サーバーへの765クライアントの素通しと同じ経路のため、
      別途の統合テストはまだ無い）
- [ ] `/trigger d_hello` が動く（データパックの読み込み確認）
- [ ] コンソールにデータパックの警告（「古いバージョン向け」等）が出ていないか記録する

**注意：** データパック形式は解決済み。`DatapackGenerator` は 1.20.2 に
`pack_format: 18` を出す（Minecraft Wiki：15 は 1.20〜1.20.1、
18 は 1.20.2）。それでも起動時のログにデータパック関連の警告が
出ていないか確認し、出ていたら文言を「6. 結果の記録」に残すこと。

#### 実起動確認の記録（2026-11-09）

`minecraft-version: "1.20.2"` でのサーバー自体の実起動を確認済み
（これまで「実起動は未検証」だった項目）。`run/` の1.20.4用ワールド・
設定を壊さないよう、別ディレクトリ `/tmp/detergentmc-1202-boot` で実施。
起動コマンド（初回は `eula.txt` が作られて終了するので、`eula=true`
にしてから再度起動）：

```sh
java -Dfile.encoding=UTF-8 -jar build/libs/detergentmc-0.1.0-1.20.4.jar \
  --workdir /tmp/detergentmc-1202-boot
```

確認に使った設定（`/tmp/detergentmc-1202-boot/detergentmc.yml` の差分。
既定ポートと被らないよう 26565／26566／26567 にずらした）：

```yaml
minecraft-version: "1.20.2"
server:
  port: 26565
  internal-port: 26566
  online-mode: false   # 起動確認のみのため offline。online-modeでの参加は別確認
resource-pack:
  port: 26567
```

結果：

- バニラ1.20.2サーバーが `Done (89.395s)!` まで起動した。
  DetergentMCの起動から `Done` まで約119秒（初回起動でバニラjarの
  ダウンロード＋展開を含む。2回目以降はjar再利用のため短くなる見込み）。
  ログ：`Starting minecraft server version 1.20.2`、
  `Starting Minecraft server on 127.0.0.1:26566`、
  `Done (89.395s)! For help, type "help"`。
- データパックは起動時に自動で読み込まれた
  （`Found new data pack file/detergentmc.zip, loading it automatically`）。
  生成された `pack.mcmeta` は `pack_format: 18`。
  **データパック関連の警告は出なかった。**
- 内部プロトコルは `minecraft-version` から決まる
  （`ProtocolVersion.serverProtocolFor`：1.20.2 → 764、1.20.4 → 765）。
- コンソールの `stop` でプロキシ→バニラの順に止まり、バニラは全次元を
  保存して終了コード 0 で終了した（`All dimensions are saved`、
  `Vanilla server stopped (exit code 0)`）。
- ログの要点（障害ではない）：
  - `Yggdrasil Key Fetcher/ERROR: Failed to request yggdrasil public key`
    （`api.minecraftservices.com` へのSSLが通らない）。
    offline-modeでの起動確認のため起動は止まらず、実害なし。
    online-modeでの参加確認時は改めて見ること。
  - `**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!` は、今回の確認で
    `online-mode: false` にしたための想定内の警告。

まだ未検証（この確認では見ていないこと）：

- 1.20.2クライアントの参加（プロキシ経由・直接とも）。プロキシの内部
  プロトコル配線は逆向き対応（第4担当）で完了し、765クライアントの参加は
  プロトコルレベルで検証済み
  （`ProxyIntegrationTest.fake765ClientJoinsRealVanilla1202ThroughProxy`）。
  実機クライアントでの参加は下の「8. 逆向き」を参照。
- `/trigger d_hello` の動作（データパックの読み込み自体は確認済みだが、
  実際にクライアントから打つ確認はまだ）。
- online-mode（`online-mode: true`）での起動・参加。

## 2. 参加（1.20.2クライアント）

- クライアント：Minecraft: Java Edition **1.20.2**（Java 17）
- アドレス：`localhost:25565`
- **25566（バニラの内部ポート）に直接つながないこと。** 25566に直接つなぐと
  1.20.2クライアントは弾かれる（プロキシを通す必要がある）。
- サーバーリストでバージョンが **1.20.2** と表示されることを確認する
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
- [ ] サーバーリストでバージョンが **1.20.2** と表示される

### 3.2 リソースパック（実機でしか確認できない部分が中心）

- [ ] 参加時にリソースパックの適用を求められた。「はい」を選ぶ
- [ ] パックの画面で「互換性あり」と表示される（764用パックは pack_format 18
      なので、互換性があるはず。もし「互換性なし」「古い」等の表示が出たら
      その文言を記録すること。**763用パック（pack_format 15）を764に
      出していた旧版の表示とは別**）
- [ ] パックの適用後、切断されずに遊び続けられる
- [ ] （確認用）サーバーを止めた状態で `run/data/resource-pack/` を見て、
      `detergentmc-pack-1.20.2.zip` があること。`unzip -p
      detergentmc-pack-1.20.2.zip pack.mcmeta` で `pack_format: 18` が
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

Respawn は764と765で同形式のため変換なしで通す。プロトコルレベルでは
次元移動の通り道はできているが、実機で正しく切り替わるか確認する：

- [ ] ネザーポータルに入ったときの挙動を記録する（画面・メッセージ・切断の有無）
- [ ] （OPの場合）`/execute in minecraft:the_nether run tp <自分の名前> ~ ~ ~`
      などで次元移動を試みたときの挙動を記録する
- [ ] 次元移動を試みたあと、元の世界に戻って遊び続けられるか記録する

### 3.5 1.20.3以降の新ブロック（目で見て確認）

チャンクのブロック状態IDリマップはネイティブ764表で実装済みで、1.20.3以降の
新ブロック（crafter等）は764クライアントで近いブロックへ代替表示される：

- [ ] スポーン周辺のブロックがおかしく表示されていないか（欠けたテクスチャ・
      違うブロックに見える等）。おかしいものがあれば座標と見た目を記録する
- [ ] （OPの場合）`/give <自分の名前> minecraft:crafter 1` でcrafterを持ち、
      置いたときの見た目を記録する（crafting_tableに見えるはず）

### 3.6 レシピ帳・タグ（落としているものの実機での見た目）

Declare Recipes と Tags は764向けに落としている（変換が未検証のため）。
遊ぶうえでの見た目を記録する：

- [ ] レシピ帳を開いたときの表示を記録する（空になるはず）
- [ ] タグに依存する表示（例：特定のブロックの分類表示）で気づいたことがあれば記録する

## 4. わざと失敗するはずのもの（バグではない）

- `/hello` や `/lang` を直接打つとバニラが「Unknown command」と返す。
  仕様。`/trigger d_hello` などを使うこと。
- リソースパックは `127.0.0.1` 配信のみ。同じマシンからの参加でのみ届く。
  外部プレイヤーには届かない（外部配信は `resource-pack.url` /
  `url-763` / `url-764` に `https://` のURLを指定する仕組みがあるが、
  置き場所は未定）。
- レシピ帳が空になる（Declare Recipes を落としているため。仕様）。
  タグも空になる（Tags を落としているため。仕様）。

## 5. online-mode（認証成功時の確認項目）

プロキシがMojang認証を自分で完結させる方式で、764＋online-modeの参加が
できるようになった。認証はプロキシが行い、内部バニラはoffline-modeの
信頼済み接続として受け取る。`server.session-server-url` は既定では空
（＝本物のMojangセッションサーバー）で、変える必要はない。

### 5.1 確認項目（正規アカウントの1.20.2クライアントで実施）

- [ ] `online-mode: true`（`session-server-url` は空のまま）で正規の
      Mojangアカウントの1.20.2クライアントが参加できる
- [ ] 参加したプレイヤーのUUID・名前が正規アカウントのものになる
      （バニラのログの `UUID of player ...` 行で確認）
- [ ] 非正規（オフライン）クライアントでは参加できず、
      「Mojang認証に失敗しました」と出て切断される
- [ ] 参加・スポーン・移動・チャット・`/trigger d_hello` がonline-modeでも通る
      （3.1と同じ項目）
- [ ] リソースパック（764用）が適用される
- [ ] 1.20.4クライアントと1.20.1クライアントのonline-mode参加に影響がない
      （回帰確認：同じく正規アカウントで参加できる）
- [ ] 自分のスキンが正しく表示される。別の正規アカウントのプレイヤーから
      見ると自分のスキンが既定スキンになる場合がある（既知の制限。記録すること）
- [ ] サーバーを止めた状態で `run/vanilla/server.properties` を見て
      `online-mode=false`・`server-ip=127.0.0.1` であること

## 6. 結果の記録（ここに書く）

- 日時：
- 使ったクライアント（1.20.2・正規／オフライン。逆向きの場合は1.20.4）：
- サーバーの online-mode（true／false）：
- サーバーの minecraft-version（1.20.4／1.20.2）：
- 通った項目：
- 通らなかった項目と、そのときの画面・ログの文言：
- リソースパックの表示（互換性あり／なし・文言）：
- 泡テクスチャの見た目：
- 次元移動で起きたこと：
- 新ブロックの表示で気づいたこと：
- レシピ帳・タグの表示で気づいたこと：
- データパック関連の警告（サーバーを1.20.2で動かした場合）：
- その他気づいたこと：

## 7. 終了

コンソールで `stop`。バニラサーバーも一緒に止まること。

## 8. 逆向き：1.20.4クライアントで1.20.2サーバーに参加する

`minecraft-version: "1.20.2"` でサーバー自体を1.20.2で動かし、
**1.20.4クライアント**で参加する、逆向きの実機確認である。
プロトコルレベルでは検証済み
（`ProxyIntegrationTest.fake765ClientJoinsRealVanilla1202ThroughProxy`：
偽765クライアントが本物のバニラ1.20.2にプロキシ経由で参加し、
Join Game・スポーン・チャット・移動まで確認済み）。
実機テストの目的は、本物の1.20.4ゲームクライアントでの見た目・挙動を
確かめることである。設計は `docs/PROXY-REVERSE.md` を参照。

### 8.1 起動（サーバーを1.20.2で動かす）

`run/` の1.20.4用ワールド・設定を壊さないよう、別ディレクトリでの起動を推奨する
（「1.5」の実起動確認と同じ方法）。既定ポートと被らないようポートをずらすこと。

```sh
java -Dfile.encoding=UTF-8 -jar build/libs/detergentmc-0.1.0-1.20.4.jar \
  --workdir /tmp/detergentmc-1202-boot
```

`detergentmc.yml` の差分：

```yaml
minecraft-version: "1.20.2"
server:
  port: 26565
  internal-port: 26566
  online-mode: false   # はじめは offline で確認する。online-modeは「8.4」を参照
resource-pack:
  port: 26567
```

起動完了の目安：`Done (...)!` と `バニラサーバーが起動しました (ポート 26565)。`
内部プロトコルは764になる（起動ログの `Internal server protocol: 764` で確認）。

### 8.2 参加（1.20.4クライアント）

- クライアント：Minecraft: Java Edition **1.20.4**（Java 17）
- アドレス：`localhost:26565`（ずらしたポートの場合。既定ポートなら `localhost:25565`）
- **26566（バニラの内部ポート）に直接つながないこと。** 直接つなぐと
  1.20.4クライアントは弾かれる（プロキシを通す必要がある）。
- サーバーリストでバージョンが **1.20.4** と表示されることを確認する
  （プロトコルレベルでは検証済み。実機での表示確認が目的）。

### 8.3 確認すること（チェック欄）

#### 8.3.1 基本（プロトコル検証済み・実機で再確認）

- [ ] 参加できた。スポーン地点に立てる
- [ ] 参加時に自分の言語（既定は日本語）であいさつが表示された
- [ ] 移動できる。ブロックを壊せる／置ける
- [ ] チャット（`/` なし）が全体に流れる
- [ ] `/list` で参加者一覧が出る
- [ ] `/trigger d_hello` であいさつが表示される
- [ ] `/trigger d_lang_en` で英語に切り替わる → `/trigger d_hello` が英語になる
- [ ] `/trigger d_lang_ja` で日本語に戻る
- [ ] サーバーリストでバージョンが **1.20.4** と表示される

#### 8.3.2 リソースパック（実機でしか確認できない部分が中心）

内部764でも765クライアントには pack_format 22 の765用パックを出す
（クライアント基準の出し分け）。バニラの `server.properties` には
764用パックが書かれているが、プロキシが765用に差し替える。

- [ ] 参加時にリソースパックの適用を求められた。「はい」を選ぶ
- [ ] パックの画面で「互換性あり」と表示される（765用パックは pack_format 22
      なので、互換性があるはず。もし「互換性なし」「古い」等の表示が出たら
      その文言を記録すること）
- [ ] パックの適用後、切断されずに遊び続けられる
- [ ] 泡テクスチャが目で見て泡に見える（`/give <自分の名前>
      minecraft:slimeball{CustomModelData:1} 1` で確認。「3.3」と同じ手順）

#### 8.3.3 次元移動・新ブロック（実機での挙動を記録する項目）

- [ ] ネザーポータルに入ったときの挙動を記録する（画面・メッセージ・切断の有無）
- [ ] （OPの場合）`/execute in minecraft:the_nether run tp <自分の名前> ~ ~ ~`
      などで次元移動を試みたときの挙動を記録する
- [ ] スポーン周辺のブロックがおかしく表示されていないか（欠けたテクスチャ・
      違うブロックに見える等）。おかしいものがあれば座標と見た目を記録する
- [ ] 1.20.3以降の新ブロック（crafter等）を持ち、置いたときの見た目を記録する
      （1.20.2サーバーには存在しないため、そもそも持てないはず。その挙動を記録する）
- [ ] レシピ帳を開いたときの表示を記録する（Declare Recipes を落としているため空になるはず）

### 8.4 online-mode（逆向き）

- [ ] `online-mode: true`（`session-server-url` は空のまま）で正規の
      Mojangアカウントの1.20.4クライアントが参加できる
- [ ] 参加したプレイヤーのUUID・名前が正規アカウントのものになる
      （バニラのログの `UUID of player ...` 行で確認）
- [ ] 非正規（オフライン）クライアントでは参加できず、
      「Mojang認証に失敗しました」と出て切断される
- [ ] 参加・スポーン・移動・チャット・`/trigger d_hello` がonline-modeでも通る

### 8.5 結果の記録（逆向き分・ここに書く）

- 日時：
- 使ったクライアント（1.20.4・正規／オフライン）：
- サーバーの online-mode（true／false）：
- サーバーの minecraft-version（1.20.2）：
- 通った項目：
- 通らなかった項目と、そのときの画面・ログの文言：
- リソースパックの表示（互換性あり／なし・文言）：
- 泡テクスチャの見た目：
- 次元移動で起きたこと：
- 新ブロックの表示で気づいたこと：
- レシピ帳の表示で気づいたこと：
- その他気づいたこと：

## 付録：外部配信（`resource-pack.url-764` / `sha1-764`）の設計

loopback配信（既定）では764用パックも自動生成・配信されるので、この設定は
不要である。外部のプレイヤーに764用パックを届けたい場合だけ使う：

```yaml
resource-pack:
  url: "https://example.com/packs/detergentmc-pack.zip"
  sha1: "0123456789abcdef0123456789abcdef01234567"  # 765用パックのSHA-1
  url-763: "https://example.com/packs/detergentmc-pack-1.20.1.zip"
  sha1-763: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"  # 763用パックのSHA-1
  url-764: "https://example.com/packs/detergentmc-pack-1.20.2.zip"
  sha1-764: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"  # 764用パックのSHA-1
```

- `url-764` / `sha1-764` は任意。両方空なら764クライアントにも765用パックを
  出す（1.20.2には「互換性なし」と表示される）。
- `url-764` は `https://` で始まるものだけが使われる。`http://` などは
  警告ログを出して無視され、764クライアントには765用パックを出す。
- `sha1-764` はホストした764用zipそのもののSHA-1（40桁の16進数）。
  `sha1sum detergentmc-pack-1.20.2.zip` で確認した値を書く。DetergentMCは
  形だけを検証し、ホストされたファイルをダウンロードして照合することはしない
  （765用と同じ方針）。値が違う場合、クライアント側でパックが拒否される。
  ファイルを差し替えたら `sha1-764` も一緒に更新すること。
- `url-764` / `sha1-764` の不備は、765用パックの判定には影響しない。
  `required: true` の起動可否も765用パックだけで決まる。
- **763用パック（pack_format 15）を `url-764` に書いてはいけない。**
  1.20.2クライアントには「互換性なし」と表示される。
  764用パックの作り方：loopbackで一度起動して生成された
  `run/data/resource-pack/detergentmc-pack-1.20.2.zip` をホストに置き、
  そのSHA-1を `sha1-764` に書く。
