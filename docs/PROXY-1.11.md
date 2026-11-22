# 1.11クライアント／サーバー（プロトコル315）の対応

DetergentMC に **315（Minecraft 1.11）** を追加した記録。隣接バージョンは
316（1.11.1／1.11.2）で、このツリーでは 316 の1段下として配線した。
315 は 316 と**ワイヤ形式・パケットIDが完全同一**で、レジストリ差分は
**アイテム1件だけ**（iron_nugget は 1.11.1 で追加されたため 315 には
無い）。そのため変換経路は 316 の経路を写したものを基本とし、
アイテム写像だけ 315 専用表（RegistryRemapper の 315 表）を通す。

> **状態（2026-11-19時点）：Stage A〜E 完了。**
> 分割検証 4,101 件・失敗 0、実バニラ統合テストは新規 3 セル＋
> 回帰 50 セルの計 53 セルが全合格（スキップ 0）。
> **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。

## 番号対応の訂正（重要）

当初の想定（315＝1.11.1、301＝1.11）は誤りだった。先行調査と本実装の
Stage A で、実バニラの Status 応答とハンドシェイクハンドラの
バイトコードにより次を確定した。

- **プロトコル 315 ＝ Minecraft 1.11（製品版）**。
- **1.11.1 と 1.11.2 はどちらもプロトコル 316**。既存の 316 実装は
  両版をカバーする（内部サーバーは 1.11.2 のままでよい）。
- 301 は 1.11 ではなくスナップショット 16w32a。スナップショットは
  降下対象外（製品版のみ）のため対象外。315 の次は 210（1.10.2）。

## 確定した事実（すべて実測）

- **バニラ 1.11 サーバーjar**：piston-meta から取得。
  SHA-1 `48820c84cb1ed502cb5b2fe23b8153d5e4fa61c0`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、9,713,666 バイト。
  **jar ルートに version.json が無く、pack.mcmeta も data/ も無い世代**
  （316／335 と同じ型）。プロトコルは **315**（実バニラを起動し
  Status 応答で確認）、pack_format は **3**（**この世代に
  データパックは存在しない**）、world_version は **819**
  （起動したワールドの level.dat の DataVersion で確認。316 は
  1.11.1 が 921、1.11.2 が 922）、内部サーバーは **Java 8 世代**。
  data-generator は無い。
- **316 とのパケット差分は 0 件**。3つの独立した方法で確認した：
  (1) 1.11.1⇔1.11.2 のプロトコル登録クラス（es＋es$1..4）の
  バイトコードが完全一致し、両 jar で異なる 19 クラスにパケット
  クラスは含まれない（315⇔316 の同一性の前提）。
  (2) minecraft-data の pc/1.11（315 自身の Burger 抽出、
  version.json は 315）と pc/1.11.2 の protocol.json の全表照合で
  一致し、PacketIds の全 283 エントリで id315 == id316
  （tools/packet-ids-315.py。PacketIds315Test でも全件固定）。
  (3) 実バニラ 1.11 へのワイヤ採取：Status で protocol 315、
  Login Success は UUID 文字列形式、Join Game（0x23）を
  316／393 形式で読んで 17/17 バイト消費、Keep Alive（0x1f、
  varint）へサーバー行き **0x0b** で応答して接続維持、応答前後の
  チャットがバニラのログへ到達。
  なお、この世代はログイン時に圧縮（しきい値 256）が有効になる。
  Keep Alive の間隔は約 2 秒と短い（Stage E の待ち時間設定で注意）。
- **レジストリ差分は items のみ**：両 jar から抽出した音イベント
  **493 件が対象まで完全一致**、エンティティ登録 **81 件が一致**
  （pig は両方とも 90）、ブロックは 236 種で一致（315 自身の
  Burger データでも最大 ID 255・236 種）。items は
  **iron_nugget（数値ID 452）が 316（1.11.1）で追加**され、
  315 には無い（451 は欠番。315 の Burger データでは
  449＝`totem`、450＝`shulker_shell`、452 は不在）。
  **totem → totem_of_undying の改名**も 316 で起きたが数値ID 449 は
  不変で、Java 側の名前引きは音名と Bedrock の ID 表だけのため
  ワイヤには無関係。
- **Join Game／Respawn の形式は 316 と同一**（393 形式とも一致。
  実バニラ 1.11 の Join Game で 17/17 バイト消費を確認）。

## 315 専用の扱い（境界の差分）

- **iron_nugget の近似**：758 側の iron_nugget を 315 へ送る場合は
  金塊（371）に寄せる（315 に存在しないため）。315→758 は 316 の
  表と同一（315 のアイテム集合は 316 の部分集合で、番号の
  振り直しは無い）。RegistryRemapper315Test が「758 のどの
  アイテムも 315 向けには 452 にならない」ことを全件走査で固定。
- それ以外の近似（コンクリート→羊毛、彩釉テラコッタ→色付き
  堅焼き粘土、parrot／illusion_illager→pig、bed ブロック
  エンティティの落下、進捗・レシピブック系 6 パケットの落下、
  メタデータ型 13〜16 の落下）は **316 からそのまま継承**する
  （詳細は docs/PROXY-1.11.2.md）。

## 発見した既存の問題（316 側・315 の Stage E で修正済み）

316 の変換表は iron_nugget を「1.12 で追加されたアイテム」として
扱い、316 向けにも金塊へ近似していた（RegistryRemapper316Test の
断言もその前提）。実際には iron_nugget は 1.11.1 で追加され
316 自身に存在するため、316 の表は 452 を 452 のまま通すべきで
ある。315 の実装（Stage A〜D）では 316 の検証済み挙動を変えない
ため直さなかったが、315 の Stage E で修正した：生成スクリプト
（gen-316-tables.py）の近似から 452 を外して表を再生成し、
検証スクリプト（verify-316-tables.py）と RegistryRemapper316Test
の断言を同一通過に更新、316 の統合セルも回帰に含めて再実行し
全合格を確認した。315 の表はこの問題と独立に正しく構築してあり
（758→315 は 452 を金塊へ寄せる上書きが引き続き効く）、影響を
受けない。

## ポート構成

他のバージョンと同じ（プロキシ 25565、内部バニラ 25566、
リソースパック 25567、Bedrock UDP 19132）。内部 1.11 サーバーは
Java 8（`detergentmc.java8.home`）で起動する。

## 配線の要点

- RegistryRemapper に 315 の定数・変換メソッドを追加。アイテム
  以外の対応表は 316 の配列をそのまま使い、アイテムだけ
  `ITEM_758_TO_315_MAP` を専用構築（316 の表の複製＋「452 は
  金塊 371 へ」の上書き）。315→758 のアイテム表は 316 と同一。
- ChunkTranslator／SlotTranslator／PlayTranslator に 315 節を追加
  （316 節の複製＋改名。tools/gen-315-translators.py）。ブロック
  エンティティ名の表は 315 でも同一のため RegistryTables316 を
  そのまま参照する。PlayTranslator の Join Game／Respawn は
  316 と同じく 393 へ委譲。
- ProxySession に is315()／isServer315()、Keep Alive 復元表、
  315 クライアント分岐・内部 315 サーバー分岐を追加
  （316 の経路の複製。tools/patch-315-proxysession.py）。
  コンストラクタの検証列挙にも V1_11 を追加（316 の Stage C で
  踏んだ不整合の再発防止）。共有列挙 23 件へ is315()／
  isServer315() を追加。
- ClientAdapter の OLDEST を 315 に下げ、
  supportedClientProtocols() には 316 を明示で残した
  （最古脱落の罠対策）。「古すぎる」文言の最小版も
  1.11（315）に更新。
- リソースパックは pack_format **3**・旧レイアウト。
  フォールバック順は **315 → 316 → 335 → 338 → 340 → 393 → …**。
  外部パック設定は `resource-pack.url-315`／
  `resource-pack.sha1-315`。
- アダプタモジュール `detergentmc-adapter-v1_11`
  （V1_11Adapter、protocolVersion 315）。versionedJars の
  バージョン一覧と build-numbers.properties に 1.11 を追加
  （`DetergentMC-1.11-1.jar` の生成を確認済み）。
- Bedrock→315 は parseSpawnEntity315／parseSpawnEntityLiving315
  から 316 のパースへ委譲する（種別番号が違う 498 用関数には
  流さない）。チャンク変換は 316 と同じ経路（PROTOCOL_315 を
  316 と同じ case に追加）。

## テスト（Stage A〜D 時点）

- 分割検証（split-verification-315/）：**4,134 件・失敗 0・
  エラー 0**（スキップ 192 件は core の統合テスト自己スキップ
  190 件と bedrock の UDP テスト 2 件で、従来と同じ内訳）。
  315 専用テストは RegistryRemapper315Test、PacketIds315Test
  （id315 == id316 の全件一致を含む）、ChunkTranslator315Test、
  SlotTranslator315Test、PlayTranslator315Test、
  ProxySession315Test、ClientAdapter315Test、
  ProtocolVersion315Test（`serverProtocolFor("1.11.1") == 316`
  の訂正も固定）、ClientJoinFlow315Test、ResourcePack の
  Builder／Mode／Server 315 テスト、AdapterDiscoveryTest の
  1.11 発見、BedrockChunkTranslator315Test。
- **最古プロトコル脱落の罠**は ClientAdapter315Test で固定した：
  supportedClientProtocols() の先頭が 315、続いて 316・335・
  338・340 が残り、計 **35 プロトコル**。
- **Stage E（実バニラ統合テスト）完了**。315 セル 3 件
  （fake315→実バニラ 1.11、fake315→実バニラ 1.11.2、
  fake316→実バニラ 1.11）を追加し、316 の Stage E の選定
  50 セルと合わせて 53 セル全合格（スキップ 0）。新規 3 セル
  とも参加→チャット→Keep Alive（0x1f、varint、応答 0x0b）→
  応答後の 2 通目のチャットがバニラログへ到達するまで実ワイヤ
  で確認した。回帰セルのうち fake335→実バニラ 1.13 が初回に
  Keep Alive 応答後のチャット到達の制限時間超過で失敗したが、
  再実行で合格した（316 の Stage E でも同セルが同じ事象で
  初回失敗→再実行合格。負荷時のタイミング由来と判断し、
  本書に記録する）。Keep Alive の間隔が約 2 秒と短い世代だが、
  セルの待ち時間は 316 と同じ設定で足りた。

## 制限

- 実バニラ統合テストは上記の選定 53 セルで確認済み。それ以外の
  組み合わせ（理屈の上では 1,225 通り）は未実行。
- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
- **データパックが無い世代のため、trigger ブリッジ
  （`/trigger d_hello`、言語切替など）が 315 バックエンドでは
  使えない**（316／335／338／340 から引き継ぐ既知の課題。
  代替手段は未定で、今回のスコープ外）。
- 316 から継承する近似の制限（アイテム形コンクリートの色落ち、
  彩釉テラコッタの向き落ち、parrot／illusion_illager の pig 代用）
  はそのまま残る。
- Bedrock→315 は単体テストのみ（実 Bedrock クライアント未確認）。
- 316 側の iron_nugget 近似の不正確さ（上記「発見した既存の
  問題」）は 315 の Stage E で修正済み。
