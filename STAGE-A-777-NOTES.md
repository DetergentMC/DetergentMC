# 777（26.3）Stage A — 作業土台ノート

作成：2026-11-15（Muse02・777担当）／場所：`~/workspace/detergentmc-777`、ローカルブランチ `work/777`
方針：数字は推測しない。piston-meta（公式）の値だけを事実として扱う。Wiki値は使っていない。
GitHubへのpushなし。776・758の作業には触れていない。バニラjar本体・Javaのダウンロード／インストールはしていない（ユーザー確認が必要なため）。

---

## 1. 確認済みの事実

### 1.1 ローカルclone

- リポジトリ：`https://github.com/DetergentMC/DetergentMC.git` を `~/workspace/detergentmc-777` にclone（2026-11-15 09:25 JST）。
- デフォルトブランチは `main`（`origin/HEAD -> origin/main`）。ブランチ `work/777` をローカルに作成済み。pushしていない。
- `main` のHEAD：`c5cc162be0fc7395b419edc143e59c37ee569981`
  - コミット日時：2026-11-15 09:24:59 +0900
  - 件名：「使い終わったワークフロー reset-aibranch.yml を削除」
- トラック対象ファイル数：850件。作業ツリー容量：約25M（`.git` 除く）。`git status` はクリーン。
- 内容は775完了相当：`ProtocolVersion.java` に `V26_1 = 775`（60行目）、`LATEST_KNOWN = V26_1`（63行目）あり。
  `PacketIds.java` は760〜775をカバー。`docs/VERSIONS.md` は17プロトコル・289通りのマトリクスを記載。
- ⚠️ 注意：完了コミット `2a9de46` はこのclone内にオブジェクトとして存在しない。
  `git log 2a9de46` は「unknown revision」。公開スナップショットは履歴が潰されており、ローカルのコミット履歴がないため。
  ただし `origin/26.1` の先頭 `1f628cbe90132ef31f2fa05548ec5536ed4d86c8` の件名は
  「DetergentMC snapshot 9/9: web/ official site (local main 2a9de46, 775 complete)」で、
  `main` と `origin/26.1` の差分は3ファイルのみ（`.github/workflows/pages.yml`、`docs/AI-INSTRUCTIONS.md`、`web/README.md`）。
  → 内容としては775完了のスナップショットであることは確認できたが、ハッシュ `2a9de46` 自体はこのcloneからは検証できない。土台の出所としては「`main` HEAD `c5cc162`（775完了相当の内容）」と記録する。
- AIBranchの先頭（`origin/AIBranch`）：`b9ee63690058a225b622ffbeb52a76112a7fc88a`（2026-11-15 09:16:47 +0900、
  「掲示板にMuse01（統括）を追加、AI-INSTRUCTIONSに連番ルールを追加」）。内容は `AI-INSTRUCTIONS.md` と `STATUS.md` の2ファイルのみ。背景情報と一致。
- 776の痕跡：なし。`RegistryTables776*.java` なし、`detergentmc-adapter-v1_26_2` なし。
  `ProtocolVersion.java`・`PacketIds.java` にも776の記述なし。
  （`776` という文字列のgrepヒットは Bedrockのプロトコル番号など別文脈のもののみで、26.2対応の痕跡ではない）

### 1.2 piston-meta（公式）— 取得日時：2026-11-15 00:25〜00:27 UTC（09:25〜09:27 JST）

出典：`https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
（各バージョンの詳細JSONはマニフェスト内の `url` 先から取得。サーバーjar本体はダウンロードしていない）

- マニフェストの `latest`：release = `26.3`、snapshot = `26.4-snapshot-8`。
- 26.3 のマニフェストエントリ：
  - `id`: `26.3`、`type`: `release`
  - `url`: `https://piston-meta.mojang.com/v1/packages/9f67e10ed013deeaad20ac3c800652765d493408/26.3.json`
  - `time`: `2026-11-10T06:58:51+00:00`（マニフェスト更新時刻。リリース時刻ではない）
  - `releaseTime`: `2026-09-15T11:23:02+00:00`
  - `sha1`（マニフェストエントリのもの）: `9f67e10ed013deeaad20ac3c800652765d493408`
  - `complianceLevel`: `1`
- 26.2 のマニフェストエントリ：
  - `id`: `26.2`、`type`: `release`
  - `url`: `https://piston-meta.mojang.com/v1/packages/f28ad96ed5d65e2438da836144a3de15bb9cafe7/26.2.json`
  - `time`: `2026-11-10T06:57:30+00:00`
  - `releaseTime`: `2026-06-16T12:03:33+00:00`
  - `sha1`: `f28ad96ed5d65e2438da836144a3de15bb9cafe7`
  - `complianceLevel`: `1`
- 26.3 のバージョンJSON（`26.3.json`）：
  - `id`: `26.3`、`type`: `release`、`time` / `releaseTime`: `2026-09-15T11:23:02+00:00`
  - `javaVersion`: `component: java-runtime-epsilon`、`majorVersion: 25` → 26.3サーバーはJava 25を要求（公式JSONで確認済み）
  - `downloads.server`: sha1 `33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c`、size `62294556` バイト、
    url `https://piston-data.mojang.com/v1/objects/33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c/server.jar`
  - `downloads.client`: sha1 `e877b6a07acd633fb3bb475002175cec036e7b87`、size `41483720` バイト、
    url `https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar`
  - `assetIndex.id`: `34`
- 26.2 のバージョンJSON（`26.2.json`）：
  - `id`: `26.2`、`type`: `release`、`time` / `releaseTime`: `2026-06-16T12:03:33+00:00`
  - `javaVersion`: `component: java-runtime-epsilon`、`majorVersion: 25`
  - `downloads.server`: sha1 `823e2250d24b3ddac457a60c92a6a941943fcd6a`、size `60894273` バイト、
    url `https://piston-data.mojang.com/v1/objects/823e2250d24b3ddac457a60c92a6a941943fcd6a/server.jar`
  - `downloads.client`: sha1 `2dc72797acbc1b63fc16a11c4ac393605f453754`、size `39193383` バイト、
    url `https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar`
  - `assetIndex.id`: `32`
- ⚠️ piston-metaのJSONにはプロトコル番号は含まれない。「26.3 = プロトコル777」はこのデータからは確認できない。
  777という番号は現時点では「作業名（未確認）」として扱う。実バニラjarから実測して初めて確定する。

### 1.3 Javaの現状（このVM）

- `which java`：なし。`java -version`：実行不可。`~/.tools`：なし。`/usr/lib/jvm`：なし。`gradle`：なし。
- → このVMにはJavaもGradleもない。ビルド・テストは現状できない。インストールはしていない（ユーザー確認が必要）。
- Temurin 25 の入手方法（公式・2026-11-15確認。メタデータのみ取得し、バイナリ本体はダウンロードしていない）：
  - 公式ページ：`https://adoptium.net/temurin/releases/?version=25`
  - API（最新25 Hotspot JDK、Linux x64）：`https://api.adoptium.net/v3/assets/latest/25/hotspot?os=linux&architecture=x64&image_type=jdk`
  - 2026-11-15時点の最新：`jdk-25.0.5+8`（AI-INSTRUCTIONS記載の `Temurin 25.0.5+8` と一致）
  - パッケージ：`OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz`、141,341,188バイト
  - sha256：`68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb`
  - ダウンロードリンク：`https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz`
  - 想定コマンド（未実行・ユーザー確認後に実施）：
    ```sh
    mkdir -p ~/.tools && cd ~/.tools
    curl -LO https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz
    echo '68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb  OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz' | sha256sum -c -
    tar xzf OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz
    export JAVA_HOME="$HOME/.tools/jdk-25.0.5+8" PATH="$JAVA_HOME/bin:$PATH"
    java -version
    ```

---

## 2. 775のStage A雛形ファイル一覧（`work/777`＝`main` HEAD時点）

トップレベルに `tools/` はない。ツール類は `detergentmc-protocol/tools/`（全210ファイル）にある。
`gen-775-tables.py` は存在しない（775の表生成は `generate-remap-tables.py`＋`insert-775-*.py` 等で行われた模様）。
`build-numbers.properties` はルートにない（AI-INSTRUCTIONSには「ルートにある」と書かれているが、公開スナップショットには含まれていない）。

| ファイル | 行数 | サイズ |
|---|---|---|
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProtocolVersion.java` | 195行 | 8,390 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/PacketIds.java` | 1,366行 | 111,840 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775.java` | 298行 | 13,173 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775ComposedUpA.java` | 542行 | 42,743 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775ComposedUpB.java` | 328行 | 21,922 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down763.java` | 234行 | 20,079 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down763BlockState.java` | 468行 | 47,372 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down764.java` | 234行 | 20,011 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down764BlockState.java` | 462行 | 46,471 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down765.java` | 210行 | 17,426 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down765BlockState.java` | 441行 | 43,679 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down766.java` | 192行 | 15,659 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down766BlockState.java` | 437行 | 43,072 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down767.java` | 192行 | 15,489 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down767BlockState.java` | 437行 | 43,072 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down768.java` | 166行 | 12,751 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down768BlockState.java` | 355行 | 34,488 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down769.java` | 159行 | 12,131 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down769BlockState.java` | 287行 | 27,597 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down770.java` | 148行 | 10,746 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down770BlockState.java` | 278行 | 26,703 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down771.java` | 141行 | 9,795 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down771BlockState.java` | 274行 | 26,248 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down772.java` | 140行 | 9,720 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down772BlockState.java` | 274行 | 26,248 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down773.java` | 112行 | 6,967 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Down773BlockState.java` | 58行 | 3,879 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775Expanded.java` | 563行 | 32,012 bytes |
| `detergentmc-protocol/src/main/java/io/detergentmc/protocol/RegistryTables775SoundNames.java` | 3,215行 | 199,836 bytes |
| `detergentmc-protocol/tools/packet-ids-775.py` | 233行 | 11,903 bytes |
| `detergentmc-protocol/tools/verify-775-tables.py` | 996行 | 54,031 bytes |
| `detergentmc-protocol/tools/generate-remap-tables.py` | 4,900行 | 277,912 bytes |
| `detergentmc-protocol/tools/insert-775-java.py` | 1,097行 | 66,775 bytes |
| `detergentmc-protocol/tools/insert-775-tables.py` | 295行 | 13,878 bytes |
| `docs/PROXY-26.1.md` | 286行 | 31,807 bytes |
| `docs/PLAYTEST-26.1.md` | 387行 | 23,169 bytes |
| `settings.gradle` | 25行 | 852 bytes |

775関連のその他（参考）：`detergentmc-protocol/tools/` 内に `gen-775-proxysession.py`、`gen-775-translators.py`、
`make-775-proxysession.py`、`make-775-translators.py`、`patch-*-775.py`、`fix-775-*.py`、`split-775-composed-up.py`、
`move-775-expanded-tables.py`、`update-775-clientproxy-tests.py`、`update-775-too-new-tests.py` など（Stage B/C用）。
テスト類：`PacketIds775Test.java`、`ProtocolVersion775Test.java`、`ClientAdapter775Test.java`、
`ClientJoinFlow775Test.java`、`ClientProxy775Test.java`、`ChunkTranslator775Test.java`、
`ResourcePackBuilder775Test.java`、`ResourcePackMode775Test.java`、`ResourcePackServer775Test.java`、
`JavaBackendConnection775Test.java`（bedrock側）。

---

## 3. 776問題 — 選択肢a/b/cの意味と次の一手

Stage Aの常套手順は「隣のバージョン（776）との差分表を作る」だが、776はGitHubスナップショットに一切なく、
このVMにもない。776の作業はユーザーのローカルmainでStage B/C途中。ここが最大の壁。

### (a) 776完了待ち

- 意味：776のStage B/Cが終わり、776の成果物（`RegistryTables776*.java`、`PacketIds` の776列、776アダプタ等）が
  公開スナップショットかユーザーからの受け渡しで手に入るまで、777のStage Aを待つ。
- 次の一手：待つだけ。待っている間にできるのは、piston-metaの値の記録（本ノートで済み）と、
  775雛形の読み込み・777用スクリプトの雛形作り（776差分に依存しない部分のみ）。
- 欠点：776の完了時期は不明。777が止まる。
- 向いている場合：776との差分が小さい見込みで、776表を反転・流用するのが圧倒的に楽な場合。

### (b) 775を隣接として777表を直接作る

- 意味：776を飛ばし、775↔777の差分表を直接作る。`RegistryTables777Down775`（775→777の対応）と
  `RegistryTables777Down763〜774`（全下位バージョンへのDown表）を、775と777の実バニラjarの実測から生成する。
  775→777の差分は「775→776」と「776→777」の合成になる。
- 次の一手（ユーザー決定後）：
  1. 26.3のserver.jar（sha1 `33680f5f…`、62,294,556バイト）を piston-data から取得し、sha1を照合する。
     ※ jar本体のダウンロードはユーザー確認が必要（本ノート作成時点では未取得）。
  2. 26.1のserver.jarも同様に取得し（775の実測値の再確認用）、両jarからレジストリ・パケットIDを実測する。
  3. `generate-remap-tables.py` と `packet-ids-775.py` を777用に複製・調整し、775↔777差分表を生成する。
  4. `verify-777-tables.py` を作り、検証が通るまでStage Bに進まない。
- 欠点：776が後から来たとき、777のDown表と776の表の整合（777→776の反転が775経由の合成と一致するか）を
  別途確認する必要がある。776の担当者と共有ファイル（`RegistryRemapper.java` 等）で競合する可能性。
- 向いている場合：777を早く進めたい場合。775→777の差分が実測で取れるなら技術的には可能。

### (c) 776の作業成果物を担当者からもらう

- 意味：776担当（ユーザーのローカルmain）から、776のStage A成果物だけを一時的に受け取る。
  受け取るもの：`RegistryTables776*.java`、`PacketIds.java` の776列、776用の `verify-776-tables.py` など
  （776のStage B/Cの途中ファイルは不要。Stage Aの表だけでよい）。
- 次の一手（ユーザー決定後）：ユーザーが776のStage AファイルをこのVMに渡す（ファイルコピー／別ブランチ／一時的な共有方法はユーザーが決める）。
  受け取ったら `work/777` に776表を置き、776↔777の差分表を常套手順通りに作る。
- 欠点：776のStage Aが「完了」しているかは担当者（ユーザー）しか分からない。途中の表をもらうと、後で776側が変わったときに777側も追従が必要。
- 向いている場合：776のStage Aが実質できていて、共有だけが未済の場合。

### 推奨（Muse02の判断）

- (b) か (c)。(a) は776の完了時期が読めないため、777を止めるだけになる。
- (c) が一番きれい（常套手順通りで、後からの整合確認が要らない）。ただし776のStage Aの出来具合次第。
- (b) はユーザー確認（jar取得）とJava導入さえ済めば、776を待たずに自力で進められる。
- 最終決定はユーザーに委ねる（第5節の「決めてもらうこと」参照）。

---

## 4. 掲示板STATUS.mdへの追記案（pushしない。案だけ）

AIBranchのSTATUS.mdに、777の行を追加する案。実際の追記・pushはユーザーが行う。

```markdown
- 777（26.3）— Stage A準備中。担当：Muse02。場所：`work/777`（ローカル、`~/workspace/detergentmc-777`）。
  土台は公開スナップショット main HEAD `c5cc162`（775完了相当の内容）。
  piston-meta確認済み（2026-11-15）：26.3はrelease、releaseTime 2026-09-15、Java 25、
  server.jar sha1 `33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c`（62,294,556バイト）。
  776不在のため隣接表の方針は未決定（(a)776完了待ち／(b)775を隣接に直接作る／(c)776成果物をもらう、のいずれか要決定）。
  プロトコル777・pack_formatは実バニラjarからの実測待ち（未確認）。
```

---

## 5. 検証済み／未検証・保留

### 検証済み（このノートで確認できたこと）

- ✅ 公開スナップショットをcloneし、`work/777` をローカルに作成（HEAD `c5cc162`、850ファイル、クリーン）。pushなし。
- ✅ piston-metaから26.2・26.3のエントリと各バージョンJSONを取得。26.3はrelease、releaseTime 2026-09-15、Java 25。
  server.jar／client.jarのsha1・size・urlを記録（第1.2節）。jar本体は未ダウンロード。
- ✅ 775のStage A雛形ファイルの棚卸し（第2節）。`gen-775-tables.py` がないこと、`build-numbers.properties` がないことも確認。
- ✅ AIBranch先頭 `b9ee636` が掲示板専用（2ファイルのみ）であることを確認。
- ✅ 776の痕跡が公開スナップショットにないことを確認。
- ✅ このVMにJava・Gradleがないことを確認。Temurin 25.0.5+8の公式入手方法（URL・サイズ・sha256）を記録。
- ✅ 26.3の `javaVersion.majorVersion: 25` を公式JSONで確認（AI-INSTRUCTIONSの「777はJava 25（要確認）」の要確認が一つ解消）。

### 未検証・保留（誰が何を用意すれば進むか）

- ⏳ プロトコル番号777：未確認。piston-metaには載っていない。実バニラ26.3 jarからの実測が必要。
  → ユーザーがjar取得を承認すれば、Muse02が実測する。
- ⏳ pack_format（リソース／データ）：未確認。実バニラ26.3 jarからの実測が必要。→ 同上。
- ⏳ 26.3 server.jarの取得とsha1照合：未実施。ユーザー承認が必要（jar本体のダウンロード）。
  → 承認後、Muse02が `https://piston-data.mojang.com/v1/objects/33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c/server.jar` を取得し照合する。
- ⏳ 26.1 server.jarの取得と775実測値の再確認：未実施。→ 同上（(b) を選んだ場合に必要）。
- ⏳ Java 25の導入：未実施。このVMにJavaなし。ユーザー承認が必要。
  → 承認後、Muse02が第1.3節の手順でTemurin 25.0.5+8を導入し `java -version` で確認する。
- ⏳ 776隣接表の方針：未決定。選択肢(a)/(b)/(c)はユーザーの決定待ち。→ ユーザーが決める（下記）。
- ⏳ 775→777差分表・777用検証スクリプト：未着手。方針決定＋jar取得＋Java導入の後に着手。
- ⏳ ビルド・テスト：未実施。Javaがないため現状不可。Java導入後に `./gradlew` 系で確認する。
- ⏳ 実機（本物のゲームクライアント）での確認：未実施（DetergentMC全体の方針として、どのバージョンも未実施）。

### この土台だけでも使えること

- 775雛形の読み込みと777用スクリプトの雛形作り（776差分に依存しない部分）。
- piston-metaの値を使った文書・STATUS.md案の準備（本ノート）。

---

## 6. ユーザーに決めてもらうこと

1. **776問題の方針**：(a)776完了待ち／(b)775を隣接に777表を直接作る／(c)776のStage A成果物をもらう、のどれか。
2. **バニラjarの取得**：26.3（＋(b)なら26.1）のserver.jarを piston-data から取得してよいか。
3. **Java 25の導入**：Temurin 25.0.5+8をこのVMに入れてよいか（第1.3節の手順）。
4. **STATUS.mdへの追記**：第4節の案文で掲示板に追記してよいか（追記・pushはユーザーが行う前提）。
