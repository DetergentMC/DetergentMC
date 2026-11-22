# 1.14.3クライアント／サーバー（プロトコル490）の対応

DetergentMC に **490（Minecraft 1.14.3）** を追加した記録。隣接バージョンは
498（1.14.4）で、このツリーでは 498 の1段下として配線した。573／575／578 が
互いに完全一致だったのに対し、490 と 498 の間には**小さいが本物の差分が
2件**ある（パケットID 1件、Trade List の形式 1件）。それ以外は実データで
完全一致を証明したため、変換は 498 の経路を共用し、差のある箇所だけ
分岐させた。

## 確定した事実（すべて実測）

- **バニラ 1.14.3 サーバーjar**：piston-meta から取得。
  SHA-1 `d0d0fe2b1dc6ab4c65554cb734270872b72dadd6`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、35,952,401 バイト。
  `run/vanilla/server-1.14.3.jar`（リポジトリには含めない）。
- **version.json**（実 jar から読み取り）：protocol_version **490**、
  pack_version は **4**（1.14.3 の version.json は resource／data の区別が
  無く単一の値。resource 4 / data 4 と同義）、world_version 1968、
  内部サーバーは **Java 8 世代**。
- **498 とのパケットID差分は 1 件だけ**：対応表の全281件を
  minecraft-data の protocol.json（1.14.3／1.14.4）同士の比較と
  全件の対応付け（`tools/packet-ids-490.py`、全281件が一意に対応）
  で確定した。490 に存在するのは 147 件。498 との差は
  **acknowledge_player_digging が 490 に無い**ことだけ
  （498 では 0x5c、573／578／758 では 0x08）。他の 147 件は ID が
  完全一致する（`PacketIds490Test` で固定）。
- **498 とのレジストリ差分はワイヤ上ゼロ**：両方の実 jar で
  data-generator（`--reports`）を実行してダンプを比較した。
  blocks.json はバイト単位で一致。registries.json のワイヤに関わる
  全カテゴリ（ブロック 676、アイテム 877、エンティティ種別 102、
  サウンド 795、バイオーム 75、パーティクル 58、等）が一致した。
  唯一の差分は `memory_module_type`（1.14.4 で
  `minecraft:opened_doors` が protocol_id 16 に追加され 26→27 件）
  だが、メモリモジュールは変換表に現れないためワイヤに影響しない。
  commands.json の差分は `/debug report` リテラルの追加のみ
  （Declare Commands は未実装のため無関係）。
  そのため 490 のレジストリ写像は 498 の表へ委譲する
  （`RegistryRemapper490Test` が全範囲で 498 との一致を固定）。
- **形式の差分は Trade List の 1 件だけ**：498（と 758）の各取引には
  末尾に **demand (i32)** があるが、490 には無い（demand は 1.14.4 で
  追加された）。758→490 では取引ごとに demand を落とし、
  490→758 では demand=0 を補う（`ChunkTranslator490Test` で固定）。
  Join Game／Respawn／チャンク／粒子などの他の形式は 498 と完全一致
  （protocol.json のフィールド定義の全件比較で確認）。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **アダプタ**：新モジュール `detergentmc-adapter-v1_14_3`
  （V1143Adapter、プロトコル490）。
- **PacketIds**：490 列を追加（差分は上記の 1 件のみ）。
- **RegistryRemapper**：490 の写像はすべて 498 の表へ委譲。
- **ChunkTranslator**：Trade List の 490 版
  （`translateTradeList490To758`／`translateTradeList758To490`、
  demand の付け外し）を追加。それ以外は 498 用を共用。
- **ProxySession**：498 の経路を共用し、パケットIDの参照先だけ
  `oldClientProtocol()`／`oldServerProtocol()` で 490 列に切り替える。
  これにより、490 クライアント向けの掘削 ack は「490 に存在しない
  パケットは落とす」既存の経路で自動的に落ち、490 サーバー発の
  不明 ID は素通し側に倒れる。Trade List のケースだけ 490 用変換に
  分岐する。変換状態（次元・保留中の光）は 498 と同じ形式のため
  フィールドを共用し、リソースパックだけ `pack490` を別に持つ。
- **ClientAdapter**：最古の対応クライアントが 490 になった
  （`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_14_3`）。versionName(490) は
  "1.14.3"。490 未満は従来どおり断る。
- **ClientProxy**：内部プロトコルとして 490 を受け入れ、`pack490` を
  セッションへ伝える。
- **リソースパック**：pack_format **4**（498 と同値）。
  `build490()`／`detergentmc-pack-1.14.3.zip`、ResourcePackServer の
  24パック構成、ResourcePackMode の `resource-pack.url-490`／
  `sha1-490`、DetergentServer の `resourcePack490()`。490 パックが
  無い場合は 498 パック（同じ pack_format 4）にフォールバックする。
  内部サーバーが 490 のときはネイティブ扱い（バニラのパックを中継）。
- **内部サーバー**：VanillaServerHost の Java 8 選択に 1.14.3 を追加。
  DatapackGenerator の pack_format も 4。
- **Bedrock**：JavaBackendConnection の 498 分岐に 490 を追加
  （Join Game は parseJoinGame498 を共用、バイオーム名だけ
  biomeNames490）、BedrockChunkTranslator は translate498 を共用し
  正規化に PROTOCOL_490 ケースを追加、BedrockEntityTranslator にも
  490 ケース（いずれも中身は 498 表への委譲）。
- **versionedJars**：`1.14.3` を追加（`DetergentMC-1.14.3-1.jar`）。

## 検証

- 分割検証（フルクリーンビルドはしない）：`split-verification-490/` に
  集計。**重複排除後の総数 3,477 件（成功 3,364、失敗 0、エラー 0、
  スキップ 113）**。スキップは、対象 jar がこのツリーに無い統合セルと
  Bedrock の UDP エンドツーエンド（サンドボックスが UDP 送信を禁止）
  が主因。モジュール別は protocol 2,694、bedrock 360、core 353、
  アダプタ群 67、content／bubble 3。
- 統合テスト（実バニラ＋フェイククライアント）：ProxyIntegrationTest
  全クラス（123メソッド）の実行で、新規3セル
  （fake490→実1.14.3、fake490→実1.14.4、fake498→実1.14.3）と
  498・573・575・578・758 の回帰17セルの計20セルを含む対象セルが
  すべて成功した（残りは対象 jar が無いためのスキップ）。
- 検証中に直したテスト側の期待値：対応プロトコル一覧や
  ResourcePackMode.decide() の引数を固定していた既存テスト
  33ファイルを、490 が増えた実態に合わせた。また
  ProxySession490Test の掘削 ack テストは、当初「記録の無い ack は
  498 でも落とされる」仕様を知らずに書いたため、掘削→ack の
  正しい手順に直した（本番の 490／498 の分岐そのものは変更なし）。
- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  実機確認の手順は `docs/PLAYTEST-1.14.3.md`。

## 残る制限

- 498 と同じ制限を引き継ぐ（Declare Recipes／Statistics／Tags／
  World Event ID は全方向で未実装、など。詳細は `docs/PROXY-1.14.4.md`）。
- バイオームの変種（hills 等）は新しいバージョンへ変換すると素の
  バイオームに寄り、往復で元に戻らない（498 と同じ、レジストリ写像の
  設計どおり）。
- Bedrock→490 は単体テストのみ（サンドボックスが UDP 送信を禁じる
  ためエンドツーエンド未実施）。
- このツリーのプロトコルは 490・498・573・575・578・758〜776 の
  計24個。組み合わせは理屈の上では 24×24＝576 通りだが、統合テストで
  確認済みなのは実際に走らせたセルだけである。576 通りすべてが
  検証済みだとは言わない。
