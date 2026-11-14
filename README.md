# DetergentMC

> 洗剤のように、きれいに。  
> *Like detergent — keeping every version clean and working.*

DetergentMCは、Minecraftの**独自サーバーソフト**です。Paperのフォークではありません。
バニラ（Mojang公式サーバー）を土台に、DetergentMC独自の層を載せています。

DetergentMC is an **independent Minecraft server software** (not a Paper fork),
built on vanilla (the official Mojang server) with its own layers on top.

## コンセプト / Concept

- **過去のバージョンも、開発し続ける。** 既存のサーバーソフトは新しいバージョンが出ると
  過去バージョンの開発を止めます。DetergentMCは過去バージョンにも新機能や改善を
  届け続けます。最終目標は **Minecraft 1.0 〜 26.4 の全バージョン**です。
- **バージョンをまたいで動くバブル。** 拡張機能は「バブル（Bubble）」と呼びます。
  バブルはバージョンに依存しないDetergentMC APIで書くので、新しいバージョン向けに
  作られたバブルが、古いバージョンのサーバーでも動きます。
- **古いクライアントにも、新しいものを。** 新しいサーバーに古いクライアントが入っても、
  テクスチャ（リソースパック）でブロックを追加したり、ブロックやUIを追加できる設計です。
- **いろいろな言語の人が使えるように。** 日本語と英語を土台に、多言語対応を進めます。
  ドキュメント・設定・コンソール・ゲーム内表示・バブルの文言は言語ファイルで管理します。

## 現状 / Status — 0.1.0 (Minecraft 1.20.4) — 遊べる最小限

MVPスケルトンから、**実際に1.20.4クライアントで遊べる段階**になりました。
プロトコルレベルのクライアントで参加・スポーン・チャット・コマンドを検証済みです。
（サンドボックス環境では本物のゲームクライアントが無かったため、実機クライアントでの
動作確認はまだです。プロトコルレベルの検証内容は下の表の通りです。）

| できること | 状態 |
|---|---|
| Gradleビルド（`./gradlew build`）とテスト | ✅ 動く（820件のテストが全て成功。3件はサンドボックスでUDP送信が禁止されているためスキップ） |
| バニラ1.20.4サーバーとの結合（子プロセスで起動・ログとコンソールで橋渡し） | ✅ 動く |
| バニラサーバーjarの初回起動時ダウンロード（公式から取得・SHA-1検証） | ✅ 動く（jarはリポジトリに含めない） |
| ワールド生成・チャンク送信・ブロックの破壊と設置 | ✅ バニラのものが動く |
| 1.20.4クライアントの参加・スポーン・移動・チャット | ✅ 動く（プロトコル検証済み） |
| **1.20.1サーバー**（`minecraft-version: "1.20.1"` で起動） | ✅ 動く（バニラ1.20.1が起動し `Done` まで進むことを確認。データパック形式15） |
| **1.20.1クライアントの参加**（1.20.4サーバーへ） | ✅ プロキシ経由で参加できる（下の「1.20.1クライアントの参加」を参照）。online-mode・offline-modeの両方に対応。参加・スポーン・移動・チャットはプロトコルレベルで検証済み。実機クライアントでの確認はまだ |
| **1.20.2クライアントの参加**（1.20.4サーバーへ） | ✅ プロキシ経由で参加できる（下の「1.20.2クライアントの参加」を参照）。online-mode・offline-modeの両方に対応。設定フェーズは中継し、Join Gameは素通し。参加・スポーン・移動・チャットはプロトコルレベルで検証済み。実機クライアントでの確認はまだ |
| **1.20.4クライアントの参加**（1.20.2サーバーへ・逆向き） | ✅ プロキシ経由で参加できる（下の「1.20.4クライアントの参加（1.20.2サーバーへ・逆向き）」を参照）。サーバー自体を `minecraft-version: "1.20.2"` で動かし、内部プロトコル764で下向き変換（765→764）する。online-modeは既存のプロキシ認証を流用。参加・スポーン・移動・チャットはプロトコルレベルで検証済み。実機クライアントでの確認はまだ |
| **1.20.5／1.20.6サーバー**（`minecraft-version: "1.20.5"` / `"1.20.6"` で起動） | ✅ 動く（バニラ1.20.5／1.20.6が起動し `Done` まで進むことを確認。データパック形式41。**Java 21 が必要**。`VanillaServerHost` は `detergentmc.java21.home` システムプロパティ→`DETERGENTMC_JAVA21_HOME` 環境変数→現在の Java（21以上のとき）の順番で Java 21 を探す。くわしくは `docs/PROXY-1.20.5.md` の「Java 21」を参照） |
| **1.20.5／1.20.6クライアントの参加**（1.20.4／1.20.2／1.20.1サーバーへ） | ✅ プロキシ経由で参加できる（下の「1.20.5／1.20.6クライアントの参加」を参照）。online-mode・offline-modeの両方に対応。1.20.1サーバーへはプロキシが設定フェーズを合成し、1.20.2／1.20.4サーバーへは設定フェーズを中継して Registry Data の形式だけ変換する。参加・スポーン・移動・チャットはプロトコルレベルで検証済み（1.20.1／1.20.2サーバーへは統合テスト済み、1.20.4サーバーへは単体テストのみ）。実機クライアントでの確認はまだ |
| **Bedrock 1.20.0〜26.51クライアントの参加**（UDP 19132、Geyser 風＋Floodgate 風・内蔵） | ✅ プロトコルレベルで参加できる（下の「Bedrockクライアントの参加」を参照）。Java アカウント不要（XUID→UUID v3、名前は `.`＋ゲーマータグ）。ログイン・スポーン・チャット（双方向）・移動・切断・**チャンク（スポーン周辺の地形が見える）**・**エンティティ（他プレイヤー・モブが見える）**は偽クライアント＋本物のバニラで統合テスト済み（代表の Bedrock 6 プロトコル × 内部サーバー 4 バージョンの 24 通り）。内部サーバー 763／764／765／766 のどれにも参加できる。26.60／26.62 は NetherNet のため未対応。**インベントリは送らない**。**Xbox 署名検証は未実装（なりすましに注意）**。本物の Bedrock クライアントでの確認はまだ |
| **1.20系の相互連携**（クライアント763／764／765／766 × サーバー763／764／765／766の全16通り） | ✅ 全16通りが参加できる（下の「1.20系の相互連携：全16通りの対応マトリクス」を参照）。統合テスト済みは7通り、単体テストのみ・素通しが9通り。実機クライアントでの確認はまだ |
| **763↔764のプレイ・ペイロード変換**（内部764のとき） | ✅ 動く（`ExtraPlayTranslator`／`PlayTranslator` の `*764To763`／`*763To764`。Block Entity Data／Set Slot／Window Items／Entity Equipment／Trade List／Entity Metadataなどを変換。統合テスト `fake763ClientJoinsRealVanilla1202ThroughProxy` で確認） |
| バニラコマンド（`/list` など） | ✅ 動く |
| サンプルバブル HelloBubble（参加時のあいさつ） | ✅ 動く（参加者の言語で表示） |
| ゲーム内コマンド（`/trigger` 経由） | ✅ 動く（下の「ゲーム内コマンド」を参照） |
| 言語切替（日本語⇔英語、プレイヤーごとに保存） | ✅ 動く |
| リソースパック配信（バブルテクスチャのデモ） | ✅ 動く（既定はloopback。外部HTTPS URLの指定も可。下の「リソースパックのデモ」を参照） |
| ファットjar（`java -jar` だけで起動） | ✅ 動く（`build/libs/detergentmc-0.1.0-1.20.4.jar`。下の「起動」を参照） |
| DetergentMC API（バブル・イベント・コマンド・設定・多言語） | ✅ 動く |
| `bubbles/` からのバブル読み込み（`bubble.yml`・依存関係の順序） | ✅ 動く |
| 日本語・英語の言語ファイルとフォールバック | ✅ 動く |
| EULA確認（`eula.txt`） | ✅ あり |

| まだできないこと（未実装） | 備考 |
|---|---|
| ゲーム内で `/hello` や `/lang` を直接打つこと | バニラ1.20.4はプレイヤーのコマンドをログに出さないため、DetergentMCが拾えません。代わりに `/trigger d_hello` / `/trigger d_lang_ja` / `/trigger d_lang_en` を使います（仕組みはデータパック＋トリガー。下の「ゲーム内コマンド」を参照）。`/hello` と打つとバニラが「Unknown command」と返します |
| 本物のゲームクライアントでの動作確認 | サンドボックスに実機クライアントが無く、プロトコルレベルの検証のみ。参加・スポーン・移動・チャット・コマンドはプロトコルで確認済み |
| online-mode（Mojang認証）での本物の認証サーバーを使った参加確認 | プロキシが認証を完結させる方式で実装済み。偽セッションサーバーでの検証は済み。本物のMojang認証サーバーでの確認はサンドボックスから届かないため未実施で、実機プレイテストの手順書（`docs/PLAYTEST-1.20.1.md` の「5. online-mode」）に残してあります |
| 公開HTTPSでのリソースパック置き場（ホスティング先） | 外部URLを指定する仕組みはできました（`resource-pack.url`）。ただしパックを置く場所はまだ決まっていないため、既定ではloopback配信のままで、外部のプレイヤーには届きません。置き場所が決まったら `detergentmc.yml` に `https://` のURLとSHA-1を書きます |
| 1.20.4以外のバージョン | 1.20.1・1.20.2・1.20.5／1.20.6は追加済み（サーバー起動とクライアント参加のプロトコル変換）。1.16.5以降は未着手（`docs/VERSIONS.md`） |
| 1.20.1クライアントの実機での参加（1.20.4サーバーへ） | Nettyプロキシができ、プロトコルレベルの参加・スポーン・移動・チャットは統合テストで確認済み。ただし本物の1.20.1ゲームクライアントでの確認はまだ。手順書は `docs/PLAYTEST-1.20.1.md` |
| 1.20.2クライアントの実機での参加（1.20.4サーバーへ） | Nettyプロキシができ、プロトコルレベルの参加・スポーン・移動・チャットは統合テストで確認済み。ただし本物の1.20.2ゲームクライアントでの確認はまだ。手順書は `docs/PLAYTEST-1.20.2.md` |
| 1.20.4クライアントの実機での参加（1.20.2サーバーへ・逆向き） | Nettyプロキシができ、プロトコルレベルの参加・スポーン・移動・チャットは統合テストで確認済み（`ProxyIntegrationTest.fake765ClientJoinsRealVanilla1202ThroughProxy`）。ただし本物の1.20.4ゲームクライアントでの確認はまだ。手順書は `docs/PLAYTEST-1.20.2.md` の「8. 逆向き」 |
| Bedrockクライアントの実機での参加 | 内蔵の Bedrock リスナー（UDP 19132）ができ、プロトコルレベルの参加・スポーン・チャット・移動・チャンク・エンティティは統合テストで確認済み（偽クライアント＋本物のバニラ）。ただし本物の Bedrock ゲームクライアントでの確認はまだ。手順書は `docs/PLAYTEST-BEDROCK.md` |
| Bedrockクライアントでのチャンク／エンティティ／インベントリ | チャンクは対応済み（スポーン周辺の地形が見える。`bedrock.chunk-radius` 既定 6）。エンティティも対応済み（他プレイヤー・モブ・落ちたアイテム・経験値オーブが見えて動く。種別は直接 99・改名 14・代替 10。攻撃・操作もできる）。インベントリは未対応（`docs/BEDROCK.md`） |
| BedrockログインのXbox署名検証 | **未実装。** JWT チェーンの署名を検証せず、中身を読むだけ（`NOT_VERIFIED_TRUSTED`）。online-mode でも Bedrock プレイヤーは Java アカウントなしで参加できるが、XUID のなりすましが可能なため、身内・開発用以外での運用は推奨しない（`docs/BEDROCK.md` の「セキュリティ」を参照） |
| 1.20.5／1.20.6クライアントの実機での参加（1.20系サーバーへ） | Nettyプロキシができ、プロトコルレベルの参加・スポーン・移動・チャットは統合テストで確認済み（1.20.1サーバーへ：`fake766ClientJoinsRealVanilla1201ThroughProxy`、1.20.2サーバーへ：`fake766ClientJoinsRealVanilla1202ThroughProxy`）。ただし本物の1.20.5／1.20.6ゲームクライアントでの確認はまだ。手順書は `docs/PLAYTEST-1.20.5.md` |
| 1.20.5／1.20.6クライアントの古いサーバーでのインベントリ操作 | 766のスロットはデータコンポーネント形式のため、Window Click／Set Creative Slot は変換できず落とす。766クライアントが内部763／764／765のサーバーにいるときはインベントリ操作ができない（ネイティブの766⇔766ではリレーのため問題ない）。くわしくは `docs/PROXY-1.20.5.md` を参照 |
| 1.20.2クライアントでのDeclare Recipes／Tags | 変換が未検証のため落としている。レシピ帳・タグが空になるだけで遊べる（`docs/PROXY-1.20.2.md`） |
| 1.20.2クライアントでのEntity Metadataの中身 | 764では素通しのため、スロット・ブロック状態・粒子の中身は765の番号のまま送られる（既知の制限。`docs/PROXY-1.20.2.md`） |
| 1.20.1クライアントでのチャンクのブロック状態IDリマップ | ✅ 動く（765→763の対応表でChunk Data／Block Changeを変換。1.20.3以降の新ブロックは近いブロックへ代替表示。Declare Recipes／Statistics／Tags／World Event IDは未対応） |
| 1.20.1クライアントでのRespawn／次元移動・Boss Bar等 | ✅ 動く（Respawn・Boss Bar・Teams・Advancements・Tab Complete・Explosionを変換。Start Configurationはプロキシがサーバー側で肩代わり。Explosionの765専用パーティクル／サウンド指定は既定演出に置き換え） |
| 1.20.1クライアントのonline-mode参加時に、他のプレイヤーから見たスキンが既定スキンになること | 内部バニラがoffline-modeのためPlayer Info Updateにプロパティを載せない。自分のスキンは正しく表示される。UUID・名前は認証済みのもの（`docs/PROXY-1.20.1.md` の「スキンの扱い」を参照） |
| Bukkit / Paper プラグインの互換性 | **なし。DetergentMC独自APIだけ**です |

## ビルド / Build

必要条件：JDK 17以上。

```sh
./gradlew build
```

ビルドすると、実行用のファットjarが `build/libs/detergentmc-0.1.0-1.20.4.jar` にできます
（各モジュールのjarもそれぞれの `build/libs/` にできます）。
Mojangのサーバーjarは**リポジトリに含めません**。初回起動時に公式
（piston-meta）から取得し、SHA-1で検証してから使います。

## 起動 / Run

### 1. 準備

```sh
./gradlew build :bubbles:hello-bubble:jar
cp bubbles/hello-bubble/build/libs/hello-bubble-0.1.0-1.20.4.jar run/bubbles/
```

### 2. 起動（java -jar・推奨）

```sh
java -Dfile.encoding=UTF-8 -jar build/libs/detergentmc-0.1.0-1.20.4.jar
```

プロジェクトのルートで実行すると、作業フォルダは既定で `run/` になります。
別の場所で動かしたい場合は `--workdir <フォルダ>` を付けます。

初回起動時に `run/` に `detergentmc.yml`、`locales/`、`bubbles/`、`eula.txt` が作られます。
`eula.txt` を `eula=true` にしてから、もう一度起動してください（Minecraft EULAへの同意が必要です）。

初回起動は時間がかかります（バニラjarのダウンロード＋ワールド生成で1分以上）。
`Done (...)!` と出て `バニラサーバーが起動しました (ポート 25565)。` と出れば起動完了です。

コンソールコマンド: `help` / `bubbles` / `hello` / `lang <ja|en>` /
`vanilla <コマンド>` / `stop`（バブルのコマンドも使えます）。
`vanilla <コマンド>` はバニラサーバーにそのまま転送します（例：`vanilla list`）。

#### ポート構成

| ポート | 既定 | 役割 |
|---|---|---|
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`127.0.0.1` のみ。直接つながないこと） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。765用・763用・764用・766用の4つを出す |
| `bedrock.port` | 19132 | Bedrock（統合版）用UDPポート。Bedrockクライアントはここに接続する |
| `bedrock.chunk-radius` | 6 | Bedrockクライアントへ送るチャンクの半径（1〜12） |

クライアントは必ず **25565（プロキシ）** に接続します。バニラは内部ポートで
動いているので、25566に直接つないでも1.20.1クライアントは弾かれます。
プロキシの仕組み（設定フェーズの消費、Join Game変換、暗号化・圧縮の扱い）は
`docs/PROXY-1.20.1.md` を参照してください。

#### ファットjarに含まれるもの・含まれないもの

- **含まれるもの：** DetergentMCの全モジュール（api / core / adapter-v1_20_4 /
  adapter-v1_20_1 / adapter-v1_20_2 / adapter-v1_20_5 / protocol / bedrock /
  content）と依存ライブラリの SnakeYAML。`Main-Class` は
  `io.detergentmc.core.Main` です。クラスパス指定は不要です。各アダプターの
  ServiceLoader登録は1ファイルにまとめてあります（`mergeVersionAdapterServices`）。
- **含まれないもの：**
  - **バニラサーバーjar** — Mojangの公式サーバーjarは含みません。初回起動時に
    公式（piston-meta）から取得し、SHA-1で検証してから使います。
    取得済みのjarは作業フォルダの `vanilla/server-1.20.4.jar` に置かれます。
  - **hello-bubble** — サンプルバブルはファットjarに含みません。使う場合は
    上の「1. 準備」の通り、別途ビルドしたjarを作業フォルダの `bubbles/` に置いてください。
    バブルを入れなくてもサーバー自体は起動します。

### 3. 起動（Gradle経由）

```sh
./gradlew :detergentmc-core:runServer
```

**注意：** Gradle経由の `runServer` ではコンソール入力がサーバーに届きません。
対話的にコマンドを打ちたい場合は、上の `java -jar` で起動してください。

### 4. 起動（旧手順・トラブルシュート用）

ファットjarが使えない場合に限り、各モジュールのjarをクラスパスに並べて直接起動できます。
通常は「2. 起動（java -jar・推奨）」を使ってください。

```sh
./gradlew build :bubbles:hello-bubble:jar
cp bubbles/hello-bubble/build/libs/hello-bubble-0.1.0-1.20.4.jar run/bubbles/
CP="detergentmc-core/build/libs/detergentmc-core-0.1.0-1.20.4.jar:\
detergentmc-api/build/libs/detergentmc-api-0.1.0-1.20.4.jar:\
detergentmc-protocol/build/libs/detergentmc-protocol-0.1.0-1.20.4.jar:\
detergentmc-content/build/libs/detergentmc-content-0.1.0-1.20.4.jar:\
detergentmc-adapter-v1_20_4/build/libs/detergentmc-adapter-v1_20_4-0.1.0-1.20.4.jar:\
detergentmc-adapter-v1_20_1/build/libs/detergentmc-adapter-v1_20_1-0.1.0-1.20.4.jar:\
detergentmc-adapter-v1_20_2/build/libs/detergentmc-adapter-v1_20_2-0.1.0-1.20.4.jar:\
$HOME/.gradle/caches/modules-2/files-2.1/org.yaml/snakeyaml/2.2/*/snakeyaml-2.2.jar"
java -Dfile.encoding=UTF-8 -cp "$CP" io.detergentmc.core.Main --workdir run
```

### 設定

`run/detergentmc.yml` の主な項目：

```yaml
language:
  default: ja          # サーバーの既定言語（ja / en）
server:
  port: 25565          # ゲームポート（プロキシが待ち受ける公開ポート）
  internal-port: 25566 # バニラが待ち受ける内部ポート（127.0.0.1のみ）
  online-mode: true    # Mojang認証（trueが既定。認証はプロキシが行う）
  session-server-url: "" # 認証の照会先。空なら既定のMojangセッションサーバー
  motd: "DetergentMC 1.20.4"
vanilla:
  memory-mb: 1024      # バニラサーバーに渡すメモリ
resource-pack:
  enabled: true
  port: 25567          # パック配信用ポート（loopbackのみ）。765用・763用・764用の3つを出す
  required: false      # trueにすると未導入のクライアントは入れない
  url: ""              # 空ならloopback配信（既定）。外部配信は https:// のURLを指定
  sha1: ""             # urlを使うときに必須。ホストしたzipのSHA-1（40桁の16進数）
  url-763: ""          # 763用パックの外部URL（任意）。空なら763にも765用パックを出す
  sha1-763: ""         # 763用パックのSHA-1（40桁の16進数）
  url-764: ""          # 764用パックの外部URL（任意）。空なら764にも765用パックを出す
  sha1-764: ""         # 764用パックのSHA-1（40桁の16進数）
  prompt-key: ""       # 空なら既定のプロンプトを使う
```

## 参加方法 / Joining

- **サーバーアドレス：** `localhost` または `127.0.0.1`（同じマシンから）
- **ポート：** `25565`（既定）
- **クライアント：** Minecraft: Java Edition **1.20.4**、Java 17。
  `minecraft-version: "1.20.1"` で起動したサーバーには **1.20.1** クライアントで参加します。
  `minecraft-version: "1.20.2"` で起動したサーバーには **1.20.2** クライアント
  （素通し）と **1.20.4** クライアント（下向き変換・逆向き対応）で参加できます。
  `minecraft-version: "1.20.5"` / `"1.20.6"` で起動したサーバーには
  **1.20.5／1.20.6** クライアントで参加します（Java 21 が必要。下の
  「1.20.5／1.20.6クライアントの参加」を参照）。どのサーバーにも
  1.20.1／1.20.2／1.20.3／1.20.4／1.20.5／1.20.6のクライアントが
  プロキシ経由で参加できます（下の「1.20系の相互連携」を参照）。
- **EULA：** サーバー側の `eula.txt` を `eula=true` にすること（初回のみ）

### 1.20.1クライアントの参加（1.20.4サーバーへ）

公開ポート25565のNettyプロキシが、1.20.1クライアント（プロトコル763）の
パケットを1.20.4（765）に翻訳してバニラへ送ります。参加・スポーン・移動・
チャットは、偽763クライアントによる統合テストで本物のバニラ相手に確認済みです
（`ProxyIntegrationTest`）。ただし本物の1.20.1ゲームクライアントでの確認はまだです。

**条件：online-mode・offline-modeの両方に対応。** `run/detergentmc.yml` の
`online-mode: true`（既定）のとき、プロキシがMojang認証を完結させ、
認証済みの接続だけを内部バニラ（offline-mode・信頼済み接続）へ繋ぎます。
`online-mode: false` のときは認証なしで参加できます。
765（1.20.4）クライアントはonline-modeのままで従来通り参加できます。

**できるようになったこと：** チャンクのブロック状態IDリマップ（1.20.3以降の新ブロックは
近いブロックへ代替表示）、Respawn／次元移動・Boss Bar・Teams・Advancements・Tab Complete・
Explosionの変換、リソースパックは763用（pack_format 15）と765用（pack_format 22）を
出し分け。くわしくは `docs/PROXY-1.20.1.md` を参照してください。**残る制限：**
Declare Recipes／Statistics／Tags／World Event IDは未対応。ブロックエンティティの
crafter／trial_spawnerはパケットごと落とす。くわしくは `docs/PROXY-1.20.1.md` を参照してください。

マルチプレイ → サーバーを追加 → アドレスに `localhost:25565` と入力して参加します。
参加すると、あなたの言語であいさつが表示されます（既定は日本語）。

### 1.20.2クライアントの参加（1.20.4サーバーへ）

公開ポート25565のNettyプロキシが、1.20.2クライアント（プロトコル764）の
パケットを1.20.4（765）に翻訳してバニラへ送ります。763（1.20.1）との
最大の違いは、764には設定フェーズがあるため、プロキシは設定フェーズを
中継することです（763のように消費しない）。Join Gameは変換なしで通し、
スポーンは Spawn Position（0x52）が合図です。参加・スポーン・移動・
チャットは、偽764クライアントによる統合テストで本物のバニラ相手に確認済みです
（`ProxyIntegrationTest.fake764ClientJoinsRealVanillaThroughProxy`）。
ただし本物の1.20.2ゲームクライアントでの確認はまだです。

**条件：online-mode・offline-modeの両方に対応。** `run/detergentmc.yml` の
`online-mode: true`（既定）のとき、プロキシがMojang認証を完結させ、
認証済みの接続だけを内部バニラ（offline-mode・信頼済み接続）へ繋ぎます。
`online-mode: false` のときは認証なしで参加できます。

**できるようになったこと：** チャンクのブロック状態IDリマップは763向け表とは
別のネイティブ764表で変換（ブロック状態17,104件・音245件が763表と違うため、
763向けの流用では誤変換になる）、Respawn（764と765で同形式のため変換なし）／
次元移動・Boss Bar・Teams・Advancements・Tab Complete・Explosionの変換、
リソースパックは764用（pack_format 18）と763用（pack_format 15）と765用
（pack_format 22）を出し分け。くわしくは `docs/PROXY-1.20.2.md` を参照してください。
**残る制限：** Declare Recipes／Tagsは落とす（レシピ帳・タグが空になるだけ）。
Entity Metadataは764では素通しのため、スロット・ブロック状態・粒子の中身は
未変換のまま残る。くわしくは `docs/PROXY-1.20.2.md` を参照してください。

マルチプレイ → サーバーを追加 → アドレスに `localhost:25565` と入力して参加します。
クライアントは Minecraft: Java Edition **1.20.2** です。

### 1.20.4クライアントの参加（1.20.2サーバーへ・逆向き）

サーバー自体を `minecraft-version: "1.20.2"` で動かすと、内部プロトコルは764に
なります。公開ポート25565のNettyプロキシが、1.20.4クライアント（プロトコル765）の
パケットを1.20.2（764）に下向き変換してバニラへ送ります。参加・スポーン・移動・
チャットは、偽765クライアントによる統合テストで本物のバニラ1.20.2相手に確認済みです
（`ProxyIntegrationTest.fake765ClientJoinsRealVanilla1202ThroughProxy`）。
ただし本物の1.20.4ゲームクライアントでの確認はまだです。

**条件：online-mode・offline-modeの両方に対応。** 既存のプロキシ認証を流用し、
プロキシがMojang認証を完結させ、認証済みの接続だけを内部バニラ
（offline-mode・信頼済み接続）へ繋ぎます。

**できるようになったこと：** 下向き変換（765→764）は上向きの対応表を反転して
使い回すため、手書きの新規対応表は作っていません。チャンクのブロック状態IDリマップ・
プレイ中の表示系・リソースパックの出し分け（クライアント基準：765→pack_format 22、
764→18、763→15）を変換します。763クライアントは763→764の直接変換で参加できます。
くわしくは `docs/PROXY-REVERSE.md` を参照してください。
**残る制限：** 763↔764のプレイ・ペイロード変換は1.20系の相互連携の作業で
実装済み（`ExtraPlayTranslator`／`PlayTranslator` の `*764To763`／`*763To764`。
統合テスト `fake763ClientJoinsRealVanilla1202ThroughProxy` で確認）。
Declare Recipes／Tagsも落とします。
くわしくは `docs/PROXY-REVERSE.md` の「まだできないこと」を参照してください。

サーバーの起動方法と実機プレイテストの手順は `docs/PLAYTEST-1.20.2.md` の
「8. 逆向き」を参照してください（別ディレクトリでの起動を推奨）。

### 1.20.5／1.20.6クライアントの参加（1.20系サーバーへ）

公開ポート25565のNettyプロキシが、1.20.5／1.20.6クライアント（プロトコル766）の
パケットを内部プロトコルに翻訳してバニラへ送ります。参加・スポーン・移動・
チャットは、偽766クライアントによる統合テストで本物のバニラ1.20.1・1.20.2相手に
確認済みです（`ProxyIntegrationTest.fake766ClientJoinsRealVanilla1201ThroughProxy`、
`fake766ClientJoinsRealVanilla1202ThroughProxy`）。
ただし本物の1.20.5／1.20.6ゲームクライアントでの確認はまだです。

**条件：online-mode・offline-modeの両方に対応。** 既存のプロキシ認証を流用し、
プロキシがMojang認証を完結させ、認証済みの接続だけを内部バニラ
（offline-mode・信頼済み接続）へ繋ぎます。

**できるようになったこと：** 内部763（1.20.1サーバー）のときはプロキシが
Join Game の codec から設定フェーズ（Registry Data・Finish Configuration）を
合成して送る。内部764／765のときは設定フェーズを中継し、Registry Data の形式
（764／765は codec NBT、766はエントリ配列）だけ変換する。内部766のときは
素通し（リレー）。チャンクのブロック状態IDリマップは765↔766対応表（逆向きは反転）
と763↔766・764↔766の合成表で変換し、リソースパックは766用（pack_format 32）と
763用（15）・764用（18）・765用（22）を出し分ける。くわしくは
`docs/PROXY-1.20.5.md` を参照してください。
**残る制限：** Window Click／Set Creative Slot は766のデータコンポーネント形式のため
変換できず落とす（古いサーバーではインベントリ操作ができない）。Declare Recipes／
Tagsも落とす。くわしくは `docs/PROXY-1.20.5.md` の「対象外・未実装」を参照してください。

**内部766サーバーには Java 21 が必要です。** バニラ1.20.5／1.20.6サーバーは
Java 21 を要求します。`VanillaServerHost` は `detergentmc.java21.home`
システムプロパティ→`DETERGENTMC_JAVA21_HOME` 環境変数→現在の Java
（21以上のとき）の順番で Java 21 を探します。ファットjar自体を Java 21 で
起動してもかまいません。くわしくは `docs/PROXY-1.20.5.md` の「Java 21」を
参照してください。

サーバーの起動方法と実機プレイテストの手順は `docs/PLAYTEST-1.20.5.md` を
参照してください。

### Bedrockクライアントの参加（1.20.0〜26.51）

UDP 19132 の内蔵 Bedrock リスナー（`detergentmc-bedrock` モジュール、
Geyser 風＋Floodgate 風）が、Bedrock 1.20.0〜26.51
（プロトコル 589〜2193、RakNet 世代の全公式プロトコル 30 個）の
クライアントを受け入れる。Login のプロトコル番号でクライアントの
バージョンを判定し、StartGame・ResourcePacks・Item（ItemV4）・
InventoryTransaction の形式をバージョンごとに使い分ける（`BedrockVersion`）。
アイテムはバージョン別の表（PMMP BedrockData から生成した 25 個の bin）で読む。
RakNet の Unconnected Ping に MOTD（`MCPE;...`）で応答し、Open Connection・
Login・リソースパック（空）・Start Game を経て、内部バニラへ Java
プレイヤーとして繋げる。Java アカウントは不要：XUID から UUID v3 を
決定的に生成し、名前は `floodgate.prefix`（既定 `.`）＋ゲーマータグを
16 文字に整形したもの（例：`.Steve`）になる。

**できること（プロトコルレベルで確認済み）：** ログイン、スポーン、
チャット（双方向）、移動、切断、**チャンク（スポーン周辺の地形が見える。
Java 1.20.4 のブロック状態 26,644 件すべてを Bedrock ランタイム ID に
写す。移動時の新しいチャンクの読み込みとブロック更新の反映も含む）**、
**エンティティ（他プレイヤー・モブ・落ちたアイテム・経験値オーブ・
矢等の表示と移動・消滅、名前と体力の表示、攻撃・インタラクト・
しゃがみ・ダッシュの操作。種別は直接 99・改名 14・代替 10、アイテムは
バージョン別（1.20.0〜1.20.40 は 1,244／1,312 件、1.20.50 は 1,270 件、
1.21.40 以降は 1,275 件））**。
偽 Bedrock クライアントによる統合テストで本物のバニラ 1.20.4 相手に
確認済み（`BedrockIntegrationTest`、`BedrockChunkIntegrationTest`、
`BedrockEntityIntegrationTest`）。代表の Bedrock 6 プロトコル
（589・594・630・671・685・2193）× 本物のバニラ 1.20.1／1.20.2／
1.20.4／1.20.5 の 24 通りも統合テストで確認済み
（`BedrockMatrixIntegrationTest`）。Bedrock クライアントは内部サーバー
763／764／765／766 のどれにも参加できる。
Java 側のパケット ID は `PacketIds` から引くため、Java 側の対応表は
二重に持たない。

**できないこと（正直な一覧）：** インベントリは送らない。
ブロックエンティティの中身（チェスト等）は送らない。
落下ブロックのブロック種別・アイテムフレームの表示アイテムは送れない。
766 サーバーの成分付きアイテムは石に見える場合がある。
**Xbox 署名検証は未実装**
（JWT チェーンの署名を検証せず中身を読むだけ。`NOT_VERIFIED_TRUSTED`。
online-mode でのなりすましに注意し、身内・開発用以外では使わないこと）。
暗号化なし。NACK への再送なし。Start Game の Itemstates は空。
対応は 1.20.0〜26.51（プロトコル 589〜2193）。26.60／26.62（2225）は
NetherNet のため未対応（設計のみ `docs/BEDROCK.md` に記載）。
**本物の Bedrock クライアントでの確認はまだ。** くわしくは
`docs/BEDROCK.md`、実機手順は `docs/PLAYTEST-BEDROCK.md` を参照。

### 1.20系の相互連携：全16通りの対応マトリクス

クライアント763／764／765／766 × サーバー763／764／765／766の全16通りが
参加できます。凡例：**◎**＝統合テスト済み（本物のバニラ＋偽クライアントで
参加・スポーン・チャット・移動まで確認）、**○**＝できる（素通し・統合テストなし）、
**△**＝制限付きでできる（落とすパケットや変換できない操作あり）。
くわしい制限と検証の区別は `docs/VERSIONS.md` の
「1.20系の相互連携：全16通りの対応マトリクス」を参照してください。

| クライアント＼サーバー | 763（1.20／1.20.1） | 764（1.20.2） | 765（1.20.3／1.20.4） | 766（1.20.5／1.20.6） |
|---|---|---|---|---|
| **763** | ○ 素通し | ◎ 制限付き | ◎ 制限付き | △ 制限付き（単体テストのみ） |
| **764** | △ 制限付き（合成経路は統合テスト済み） | ○ 素通し | ◎ 制限付き | △ 制限付き（単体テストのみ） |
| **765** | △ 制限付き（合成経路は統合テスト済み） | ◎ 制限付き | ◎ 素通し | △ 制限付き（単体テストのみ） |
| **766** | ◎ 制限付き | ◎ 制限付き | △ 制限付き（単体テストのみ） | ○ 素通し |

## ゲーム内コマンド / In-game commands

バニラ1.20.4はプレイヤーのコマンドをログに出さないため、DetergentMCのコマンドは
`/hello` のような形では直接打てません。代わりに、起動時に自動生成される
データパック（`vanilla/world/datapacks/detergentmc.zip`）が用意する
**トリガー**を使います。トリガーは参加時に自動で有効になります。

| ゲーム内で打つもの | 効果 |
|---|---|
| `/trigger d_hello` | あいさつを表示します（あなたの言語で） |
| `/trigger d_lang_ja` | 表示を日本語に切り替えます |
| `/trigger d_lang_en` | 表示を英語に切り替えます |
| `/list` | バニラのコマンド。参加中のプレイヤー一覧を表示します |
| チャット（`/` なしで打つ） | そのまま全体チャットに流れます |

言語の切り替えはプレイヤーごとに保存され、次回参加時も引き継がれます。
（保存先：`run/data/player-locales.properties`）

## リソースパックのデモ / Resource pack demo

起動時に、泡のテクスチャ入りのリソースパックが自動生成・配信されます。
参加時にパックの適用を求められたら「はい」を選んでください。

泡の見た目を確認するには、オペレーター権限で次のコマンドを打ちます
（1.20.4ではNBT形式です。`[custom_model_data=1]` の形式は1.20.5以降です）。

```
/give <あなたの名前> minecraft:slimeball{CustomModelData:1} 1
```

スライムボールが泡のテクスチャで表示されれば成功です。
これはデモです。本物のカスタムブロック（新しいブロックIDの追加）は未実装で、
見た目だけの置き換えです。

### 配信方法：loopback（既定）と外部HTTPS

既定では、パックは生成して `127.0.0.1` でのみHTTP配信します
（`resource-pack.url` が空の場合）。同じマシンからの参加でのみ届きます。

外部のプレイヤーにも届けるには、`detergentmc.yml` に公開URLを指定します。
**公開する置き場所はまだ決まっていません。** 決まるまでは既定のloopbackのままです。
置き場所が決まったら、次のように設定します。

```yaml
resource-pack:
  enabled: true
  required: false
  url: "https://example.com/packs/detergentmc-pack.zip"
  sha1: "0123456789abcdef0123456789abcdef01234567"  # ホストしたzipのSHA-1
  prompt-key: ""  # 空なら既定のプロンプト
```

- `url` は **`https://` で始まるものだけ**が外部配信として使われます。
  `http://` は拒否され、警告ログを出してloopback配信に切り替わります。
  （Minecraftのクライアントは、loopback以外のパックを平文HTTPでは受け取りません。）
- `url` を指定したときは、ローカルHTTPサーバーは起動しません。
  設定した `url` と `sha1` がそのまま `server.properties`
  （`resource-pack` / `resource-pack-sha1`）に書き込まれます。
- `sha1` はホストしたzipファイルそのもののSHA-1です。
  `sha1sum detergentmc-pack.zip` で確認した値を書いてください。
  デモパックを使う場合は、一度loopbackで起動して生成された
  `data/resource-pack/detergentmc-pack.zip` をホストに置き、そのSHA-1を書きます。
- DetergentMCは `sha1` の**形（40桁の16進数）だけ**を検証します。
  ホストされたファイルをダウンロードして照合することはしません。
  ホストしたファイルと違うパック（自作パックや新しいビルド）でも使えるようにするためです。
  `sha1` がホストされたファイルと違う場合、クライアント側でパックが拒否されます。
  ファイルを差し替えたら `sha1` も一緒に更新してください。
- `sha1` が空・不正な場合：`required: false` なら警告ログを出してパックなしで起動し、
  `required: true` ならサーバーを起動しません。
  loopback配信でポートが使えない・パック生成に失敗した場合も同じ扱いです
  （`required: false` なら警告ログ＋パックなしで続行、
  `required: true` なら起動を止めます）。失敗を黙って無視することはしません。

**注意：** 既定のloopback配信では、同じマシンからの参加でのみパックが届きます。
外部のプレイヤーには届きません（上の「まだできないこと」を参照）。

## バブルの作り方 / Writing a Bubble

1. `bubbles/hello-bubble/` をコピーして、新しいフォルダを作ります。
2. `bubble.yml` を書き換えます（`id`・`name`・`main`）。

```yaml
id: my-bubble
name: MyBubble
version: 1.0.0
description: My first Bubble.
authors: [YourName]
main: com.example.MyBubble
depends: []
```

3. `io.detergentmc.api.Bubble` を実装します。

```java
public class MyBubble implements Bubble {
    @Override
    public void onEnable(BubbleContext context) {
        context.logger().info("Hello from MyBubble!");
        context.commands().register(new MyCommand());
    }
}
```

4. ビルドして、できたjarをサーバーの `bubbles/` に入れます。

詳しくは `bubbles/hello-bubble/src/main/java/io/detergentmc/bubble/hello/HelloBubble.java`
と `docs/ARCHITECTURE.md` を見てください。

## モジュール構成 / Modules

```
detergentmc-api             バージョンに依存しない公開API（依存なし）
detergentmc-core            起動・バブルローダー・言語・設定・イベント・バニラ結合・ゲーム内ブリッジ
detergentmc-adapter-v1_20_4  1.20.4用アダプター（対応表のみ）
detergentmc-adapter-v1_20_1  1.20.1用アダプター（対応表のみ）
detergentmc-adapter-v1_20_2  1.20.2用アダプター（対応表のみ）
detergentmc-adapter-v1_20_5  1.20.5用・1.20.6用アダプター（プロトコル766を共有、1モジュールに2アダプター）
detergentmc-protocol        古いクライアント向け変換・リソースパック（クライアントアダプター含む）
detergentmc-bedrock         Bedrock（統合版）用UDPリスナー・RakNet・Floodgate風ID・Java翻訳（内蔵）
detergentmc-content         カスタムブロック・カスタムUIのデータモデル
bubbles/hello-bubble        サンプルバブル
docs/                       設計とバージョン追加方法
```

## ライセンス / License

DetergentMC自身のコードは MIT License です（`LICENSE` を参照）。
Minecraft本体・バニラサーバーjar・Mojangの素材はMojang/Microsoftのものであり、
Minecraft EULA（https://www.minecraft.net/en-us/eula）が適用されます。
