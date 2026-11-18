# 1.13クライアント／サーバー（プロトコル393）の対応

DetergentMC に **393（Minecraft 1.13）** を追加した記録。隣接バージョンは
401（1.13.1）で、このツリーでは 401 の1段下として配線した。

> **状態（2026-11-18）：Stage A〜E 完了。** 401 の最終 HEAD
> （`369099e`）へリベースした上で、実バニラ統合テスト＋分割検証を
> 実施済み（総数 3,871 件・失敗 0、統合セルは新規3件＋回帰35件の
> 計38件がすべて成功）。本物のゲームクライアントでの確認のみ
> 未実施。以下の「確定した事実」は実データ照合と実バニラ統合で
> 確認済みのもの。

## 確定した事実（すべて実測）

- **バニラ 1.13 サーバーjar**：piston-meta から取得。
  SHA-1 `d0caafb8438ebd206f99930cfaecfa6c9a13dca0`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、32,649,675 バイト。
  **jar ルートに version.json が無い世代**（1.13.1 と同じ）。
  プロトコルは **393**、pack_format は resource **4** / datapack **4**、
  world_version（level.dat の DataVersion）は **1519**
  （401 の 1628 とは異なる）、内部サーバーは **Java 8 世代**。
- **401 とのパケットID差分は 0 件**：`tools/packet-ids-393.py` で
  対応表の全282エントリを 1.13 自身の protocol.json に対応付け、
  全件が 401 と同じ ID になることを確認した。393 に存在するのは
  **138 件**（bed 0x33 を含む）。表に無い唯一のパケットは
  legacy_server_list_ping（どのバージョンでも表管理していない）。
- **401 との形式差分は edit_book のみ**：protocol.json の型ツリー
  全比較で、相違は edit_book 末尾の `hand`（varint）だけだった。
  これは 1.13.1 で追加されたもので 393 には無い。変換経路では
  edit_book は名前引きで落とされるため、専用処理は不要。
- **slot 型は 401 と同一の旧形式**（itemId が i16、-1 で空）。
  401 の slot 変換機械をそのまま共用する。
- **レジストリ差分は実在（サンゴまわり）**：ブロック **593 種・
  ブロック状態 8,582**（401／404 は 598 種・8,599）、アイテム
  **785**（401／404 は 790）。1.13.1 で dead coral 5 種が追加され、
  併せて tnt の `unstable`、coral 5 種と conduit の `waterlogged`
  プロパティが追加された（状態 +17 件）。そのためブロック／
  アイテム表は 393 専用に生成した（401／404 への恒等委譲は不可）。
  写像は「393⇔404 の名前対応（両 jar の data-generator ダンプ）」と
  検証済みの 404⇔758 表の合成で作り、`tools/verify-393-tables.py`
  で **35,350 件全件照合**済み。404→393 では dead coral は対応する
  生きた coral に寄せる。エンティティ（mob 95）・バイオーム
  （73）・効果（30 名）の順序は 404 と同一。絵画は実 jar の
  定数プールから 26 件を抽出し、最大 ID 25（テーブル不要）。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- 393 は 401 のような「404 経路への相乗り」ではなく、**専用の
  変換群一式**を持つ（RegistryTables393、ChunkTranslator の
  393 節、PlayTranslator の Join Game／Respawn 委譲＋
  JoinGame393 レコード、SlotTranslator の 393 入口）。
- ProxySession は 393 クライアント／393 サーバーの両方向を配線。
  最古プロトコルを 393 に下げた際、**401 と 404 が
  `ClientAdapter.supportedClientProtocols()` に残る**ことを
  単体テストで固定した（404／401 で踏んだ脱落バグの再発防止）。
- 実装中に Stage A の取りこぼしを発見して修正：PacketIds の
  `supportedProtocols()`／`isSupported()` に 393 が無く、ID 表の
  列があるのに名前引きが空を返していた。
- リソースパックは 393 専用（pack_format 4、
  `detergentmc-pack-1.13.zip`）。393 パックが無い構成では
  401 パック→404 パックの順に代替する。外部モードは
  `resource-pack.url-393`／`sha1-393`。
- Bedrock→393：チャンクはパースを 404 と共用し、正規化は
  `input.serverProtocol()` で 393 専用表に振り分ける。アイテムは
  404 と番号が違うため専用の腕を用意した。Spawn Entity Living は
  ワイヤ形式が 498 と同じでも種別番号が違うため、専用の
  `parseSpawnEntityLiving393` で読む。
  - 既存の疑い（393 では直していない）：`parseSpawnEntityLiving498`
    は 498 の番号で正規化するが、JavaBackendConnection は
    404／401 バックエンドもそこへ流している。404 と 498 では
    エンティティ番号が 95 件中 89 件で異なるため、404／401 の
    生物スポーン種別がずれている可能性がある。別途確認が必要。

## テスト（Stage E 完了時点）

- 分割検証（`split-verification-393/`）の総数は重複排除後
  **3,871 件（成功 3,758、失敗 0、エラー 0、スキップ 113）**。
  内訳：protocol 3,002 件、bedrock 414 件（スキップ 2 は
  サンドボックスの UDP 制限）、core 385 件（うち
  ProxyIntegrationTest 141 件）、adapter 67 件、content 3 件。
  スキップ 113 件は jar の無いバージョンの統合セル＋Bedrock UDP
  で従来どおり。
- 実バニラ統合セルは新規3件（fake393→実バニラ1.13、
  fake393→実バニラ1.13.1、fake401→実バニラ1.13）と回帰35件の
  計 **38 件がすべて成功**（スキップ0）。ProxyIntegrationTest の
  全141メソッドも分割実行で全件結果あり（成功 59、スキップ 82、
  失敗 0）。
- リベース（401 最終 HEAD `369099e` の上へ9コミット）で崩れた
  ものは無く、最古プロトコルの一覧脱落対策（401 と 404 が
  `ClientAdapter.supportedClientProtocols()` に残ること）も
  `ClientAdapter393Test` で引き続き固定されている。

## 制限

- **本物のゲームクライアントでの確認は未実施**
  （全バージョン共通）。
- **Bedrock→393** は単体テストのみ（サンドボックスが UDP 送信を
  禁じるためエンドツーエンド確認は未実施）。
- 404 と共通の制限は `docs/PROXY-1.13.2.md` を参照
  （PLAY_BED は落とす、Trade List／Entity Sound Effect は
  393 に存在しない、チャンクの光は近似合成など）。
