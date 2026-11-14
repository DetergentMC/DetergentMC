# DetergentMC 1.20.4 実機プレイテスト — チェックリスト

2026-11-08作成。プロトコル検証済み（81件テスト成功）だが、本物のゲームクライアントでの確認はまだ。
このファイルはその実機確認のための手順と、結果の記録用。

## 事前に確認済みのこと（2026-11-08 20:24 JST時点、第3段階で再検証）

- `run/eula.txt` と `run/vanilla/eula.txt` は `eula=true` 済み
- `run/detergentmc.yml`：`port: 25565`、`online-mode: true`、`default: ja`
- ポート構成（第2段階で変更）：プロキシが25565、バニラは内部ポート25566
  （`127.0.0.1` のみ）、リソースパック配信は25567。`run/vanilla/server.properties`
  は起動時に書き換えられ、`server-port=25566`、`server-ip=127.0.0.1`、
  `network-compression-threshold=-1`、
  `resource-pack=http://127.0.0.1:25567/detergentmc-pack.zip` になる。
  （このファイルの旧版に書いてあった `server-port=25565` /
  `resource-pack=http://127.0.0.1:25566/...` は第2段階より前の古い状態）
- バニラjar `run/vanilla/server-1.20.4.jar` あり、ワールド `run/vanilla/world/` あり
- サンプルバブル `run/bubbles/hello-bubble-0.1.0-1.20.4.jar` あり
- リソースパック `run/data/resource-pack/detergentmc-pack.zip` あり
- ファットjar起動確認済み（2026-11-08 20:33 JST）：`java -jar
  build/libs/detergentmc-0.1.0-1.20.4.jar` で起動し、プロキシ25565・
  バニラ127.0.0.1:25566・リソースパック25567が待ち受け、`Done (34.933s)!`
  まで到達。`stop` で正常終了（exit 0）。ただしバニラのチャンク保存が
  20秒の停止タイムアウトを超え、子プロセスは強制終了扱いになった
  （サンドボックスの遅さによるもの。既知の挙動で、第3段階の対象外）
- サーバーは現在停止中（25565番ポートに何もいない）。起動してから参加すること

## 1. 起動

推奨はファットjarでの起動（READMEの「起動」を参照）：

```sh
cd ~/workspace/detergentmc
./gradlew build :bubbles:hello-bubble:jar
cp bubbles/hello-bubble/build/libs/hello-bubble-0.1.0-1.20.4.jar run/bubbles/
java -Dfile.encoding=UTF-8 -jar build/libs/detergentmc-0.1.0-1.20.4.jar
```

トラブルシュート用に、各モジュールのjarをクラスパスに並べる旧手順もある
（READMEの「4. 起動（旧手順・トラブルシュート用）」を参照）。
Gradle経由の `runServer` ではコンソール入力が届かないので、対話するなら
上の `java -jar` で起動すること。

起動完了の目安：`Done (...)!` と `バニラサーバーが起動しました (ポート 25565)。`

## 2. 参加

- クライアント：Minecraft: Java Edition **1.20.4**（Java 17）
- アドレス：`localhost:25565`
- 注意：`online-mode: true` のため、正規のMojangアカウントでの参加が前提。
  オフライン（非正規）クライアントで入る場合は、事前に
  `run/detergentmc.yml` と `run/vanilla/server.properties` の
  `online-mode` を両方 `false` にして再起動すること。

## 3. 確認すること（チェック欄）

- [ ] 参加できた。スポーン地点に立てる
- [ ] 参加時に自分の言語（既定は日本語）であいさつが表示された
- [ ] 移動できる。ブロックを壊せる／置ける
- [ ] チャット（`/` なし）が全体に流れる
- [ ] `/list` で参加者一覧が出る
- [ ] `/trigger d_hello` であいさつが表示される
- [ ] `/trigger d_lang_en` で英語に切り替わる → `/trigger d_hello` が英語になる
- [ ] `/trigger d_lang_ja` で日本語に戻る
- [ ] 再参加しても言語設定が引き継がれる
- [ ] リソースパックの適用を求められた。「はい」を選ぶ
- [ ] OPで `/give <自分の名前> minecraft:slimeball{CustomModelData:1} 1` を打ち、
      スライムボールが泡のテクスチャで見える

## 3b. 1.20.1クライアントでの参加（未実施・プロトコルレベルでは検証済み）

プロキシ経由の参加・スポーン・移動・チャットは、偽763クライアントの統合テスト
（`ProxyIntegrationTest`）で本物のバニラ相手に確認済み。ここでは本物の
1.20.1ゲームクライアントで同じことを確かめる。

### 準備

1. `run/detergentmc.yml` で `online-mode: false` にする（763＋online-modeは
   参加できない。`true` のままだと理由付きで切断される）。
2. サーバーを起動する（上の「1. 起動」）。
3. Minecraft: Java Edition **1.20.1** のクライアントで `localhost:25565`
   に参加する。**25566（バニラの内部ポート）に直接つながないこと。**

### 確認すること（チェック欄）

- [ ] 参加できた。スポーン地点に立てる
- [ ] 移動できる。ブロックを壊せる／置ける
- [ ] チャット（`/` なし）が全体に流れる
- [ ] `/trigger d_hello` であいさつが表示される
- [ ] サーバーリストでバージョンが **1.20.1** と表示される
- [ ] リソースパックの適用を求められたら「はい」を選ぶ（デモパックは
      pack_format 22 のため「互換性なし」と表示される可能性がある。その場合は
      その文言を記録すること）

### わざと失敗するはずのもの（1.20.1・バグではない）

- `online-mode: true` に戻して1.20.1クライアントで参加すると、
  「1.20.1クライアントは online-mode では参加できません。
  online-mode を false にしてください。」と出て切断される。仕様。
- ネザー／エンドへの移動（Respawnパケット未対応）。行こうとすると
  どうなるか、起きたことを記録すること。
- 1.20.3以降の新ブロック（crafter等）は正しく表示されない可能性がある。
  チャンクのブロック状態IDリマップは未実装。

### 結果の記録（ここに書く）

- 日時：
- 使ったクライアント（1.20.1・正規／オフライン）：
- 通った項目：
- 通らなかった項目と、そのときの画面・ログの文言：
- 気づいたこと：

## 4. わざと失敗するはずのもの（バグではない）

- `/hello` や `/lang` を直接打つとバニラが「Unknown command」と返す。
  仕様。`/trigger d_hello` などを使うこと。
- リソースパックは `127.0.0.1` 配信のみ。同じマシンからの参加でのみ届く。
  外部プレイヤーには届かない（未実装）。

## 5. 結果の記録（ここに書く）

- 日時：
- 使ったクライアント／アカウント種別（正規／オフライン）：
- 通った項目：
- 通らなかった項目と、そのときの画面・ログの文言：
- 気づいたこと：

## 6. 終了

コンソールで `stop`。バニラサーバーも一緒に止まること。

## 注意：データパックの件は解決済み（2026-11-08 13:53 JSTに検証）

`run/vanilla/world/datapacks/` が空だったのは、ワールドが作られたのが
2026-11-08 02:04 UTC（11:04 JST）で、データパック生成のコード
（`DatapackGenerator` / `DetergentServer.generateDatapack()`）が入ったのが
その後の 03:00 UTC（12:00 JST）だったため。生成コードが入ってからの
サーバー起動はまだ一度もないので、空なのは当然の古い状態で、バグではない。
`VanillaServerHost` はデータパックを消さないことも確認済み。

ビルド済みjarで生成だけを別フォルダで検証済み：`detergentmc.zip`
（1,247バイト）ができ、中身は `pack.mcmeta`（pack_format 26）と
`tick.json` / `load.json`、`tick.mcfunction` / `load.mcfunction` の
計5ファイルで、zipの破損なし。`tick.mcfunction` には
`d_hello` / `d_lang_ja` / `d_lang_en` の3トリガーが入っている。

起動時の確認：コンソールに `データパックを生成しました: run/vanilla/world/datapacks/detergentmc.zip`
が出るはず。もし出ずに `/trigger d_hello` が効かない場合は、そのときの
コンソールの `データパックの生成に失敗しました…` の文言を記録すること。
