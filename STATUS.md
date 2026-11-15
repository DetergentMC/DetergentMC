# DetergentMC 掲示板（AIBranch）

今やっていることの全体像を置く場所。コードは置かない。
コードの合流（マージ）は依頼主が行う。終わったものから順にmainへ入れる。

最終更新：2026-11-15 09:41 JST

---

## 参加しているAI

- **Muse01（統括）**：依頼主の横にいるAI。全体の調整と合流を担当。名前は依頼主がつけた。
- **Muse02（777担当）**：2026-11-15に参加。777（26.3）のStage A準備中。
  場所：`~/workspace/detergentmc-777`、ブランチ `work/777`。
- Muse03〜：空き。手伝いに来たAIは、空いている一番小さい番号を名乗ること
  （Muse03、Muse04、…）。名前が決まったらここに書き足すこと。
- AI-INSTRUCTIONS.md（手伝うAIへの指示書）はMuse01以外触ってはいけない。
  変更が必要ならMuse01に伝えること。

---

## Muse01による認証

- **AI-INSTRUCTIONS.md：承認（2026-11-15）**
  - Muse01が全文に目を通し、内容を確認した。
  - 確認した範囲：プロジェクト概要と方針、現在の状況（775完了・776・758作業中）、
    バージョンとプロトコル番号の表、作業環境とブランチのルール、Stage A〜Eの進め方、
    鉄則10か条、バージョンごとの注意点、jar命名とビルド番号、完了報告のフォーマット、
    やってはいけないこと。
  - 見つかった誤り：1件。「フルビルド1時間47分ほどかかる（775の実績）」は誤りで、
    1時間47分は774のフルクリーンビルドの実績。775は分割検証で完了したため、
    フルビルドの実績はない。→「（774の実績）」に修正済み。
  - それ以外に事実の誤りは見つからなかった。承認する。
  - 認証の記録は AI-INSTRUCTIONS.md の冒頭にも書いてある。
- **STAGE-A-777-NOTES.md：未認証（Muse01はまだ目を通していない）**
  - Muse02が作った777のStage A作業土台ノート。Muse01は内容をまだ確認していない。

---

## 進行中の作業

### 777（26.3）— Stage A準備中

- 担当：Muse02。場所：ブランチ `work/777`（`~/workspace/detergentmc-777`）
- 状態：piston-meta（公式）で26.3を確認済み（release、2026-09-15、Java 25要求）。
  ただし「26.3＝プロトコル777」はpiston-metaからは確認できないため、現時点では作業名扱い。
  作業ノート：`STAGE-A-777-NOTES.md`（AIBranchに置いてある）
- 土台は main HEAD `c5cc162`（775完了相当）。776の痕跡はなし。
- 決めてもらうこと（ユーザー決定待ち）：
  776問題の方針（a. 776完了待ち／b. 775を隣接として777表を直接作る／c. 776の成果物をもらう）、
  バニラjarの取得可否、Java 25（Temurin 25.0.5+8）の導入可否

### 776（26.2）— Stage B/C（翻訳子・プロキシ配線）の途中

- 場所：`main` ブランチ（ローカル `~/workspace/detergentmc`）
- 状態：レジストリ表・ProtocolVersion・PacketIds・アダプタはコミット済み
  （`374a213`、`dae7e58`）。翻訳子・プロキシ配線の作業中で、未コミット変更が多数ある。
- 関係するパッケージ・モジュール：
  - `detergentmc-adapter-v1_26_2`（新規モジュール）
  - `io.detergentmc.protocol`（PacketIds、ProtocolVersion、RegistryRemapper、
    ClientAdapter、ClientJoinFlow、ClientProxy）
  - `io.detergentmc.core`（DetergentServer、vanilla/DatapackGenerator）
  - ResourcePackBuilder、`detergentmc.yml`
- 完了条件：統合テスト＋文書＋324通りマトリクス（18プロトコル）

### 758（1.18.2）— Stage B/C（翻訳子・プロキシ配線）の途中

- 場所：ブランチ `work/1.18`（worktree `~/workspace/detergentmc-1.18`）
- 状態：PacketIds・テスト追加済み
  （`508cec6`、`003e9da`、`3e5cd59`、`a276535`、`9ebaeba`）。
  ClientAdapter・ClientJoinFlowの758対応を追加済み。ツリーはクリーン。
- 関係するパッケージ・モジュール：
  - `detergentmc-adapter-v1_18_2`（新規モジュール）
  - `io.detergentmc.protocol`（PacketIds758、ClientAdapter、ClientJoinFlow など）
- 完了条件：統合テスト＋文書＋324通りマトリクス（18プロトコル）
- 757（1.18／1.18.1）は758の完了後に始める

---

## 完了したもの

- **775（26.1）**：`2a9de46`、17プロトコル、289通り、テスト2,866件（0失敗・3スキップ）
- **jar命名**：`versionedJars` タスク＋`build-numbers.properties`
  （`9a9d01f`、`0742361`）。28バージョン分を `DetergentMC-<バージョン>-<ビルド番号>.jar` で作る。
  ビルド番号はバージョンごとに独立。
- **他のAI向け指示書**：`AI-INSTRUCTIONS.md`（`1f1bd13`、GitHubのmainにもpush済み）
- **Webサイト**：https://detergentmc.github.io/DetergentMC/ で公開中（GitHub Pages＋Actions）
- **GitHubブランチ**：28本（main＋バージョン27本）。すべてmainのスナップショットと同じ内容。

---

## これから

- **ビルド1のリリース**：775の状態で今作るか、776が終わるまで待つか、未決定
- **Webサイトのダウンロードページ**：バージョンごとのjarリンクを並べる（リリース後に作成）
- **次のバージョン候補**：757（1.18／1.18.1）。777はMuse02が担当中。
  - 空いているバージョンから選んで作っていく。始める前に依頼主（またはMuse01）に確認すること。

---

## ブランチ一覧

| ブランチ | 内容 |
|---|---|
| `main` | 776の作業中＋完了済みのすべて |
| `work/1.18` | 758の作業中 |
| `work/777` | 777の作業中（Muse02） |
| `AIBranch`（このブランチ） | 掲示板。`STATUS.md` と `AI-INSTRUCTIONS.md`、Muse02の作業ノート `STAGE-A-777-NOTES.md`。コードは置かない |
| バージョンブランチ28本 | mainのスナップショットと同じ内容（各バージョン完了時点のツリーではない） |

---

## ルール

- 他の担当者の作業中のブランチ・worktreeには触らない
- 776と758は同じファイル（ClientAdapter、ClientJoinFlow、ClientProxyなど）を編集中のため、
  コードを今このブランチに合流させると衝突する。合流は各作業の完了後に行う
- 掲示板の更新はこのブランチで。進捗があったらここに書き足す
- 掲示板のファイルを更新するときは、他の人の書き込みを消さないこと。
  更新前に最新の内容を確認し、自分の書き込みだけを足すこと。
  （2026-11-15にMuse01のpushがMuse02の書き込みを上書きしてしまったため、書き戻した）
