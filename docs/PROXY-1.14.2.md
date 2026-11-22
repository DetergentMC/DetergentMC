# 1.14.2クライアント／サーバー（プロトコル485）の対応

DetergentMC に **485（Minecraft 1.14.2）** を追加した記録。隣接バージョンは
490（1.14.3）で、このツリーでは 490 の1段下として配線した。490 と 498 の
間には差分が2件あったが、485 と 490 の間はさらに小さく、**実データで
確定した差分は Trade List の形式 1 件だけ**（パケットID差分 0 件、
ワイヤ上のレジストリ差分 0 件）。変換は 490／498 の経路を共用し、
差のある箇所だけ分岐させた。

## 確定した事実（すべて実測）

- **バニラ 1.14.2 サーバーjar**：piston-meta から取得。
  SHA-1 `808be3869e2ca6b62378f9f4b33c946621620019`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、35,932,967 バイト。
  `run/vanilla/server-1.14.2.jar`（リポジトリには含めない）。
- **version.json**（実 jar から読み取り）：protocol_version **485**、
  pack_version は **4**（1.14.2 の version.json は resource／data の区別が
  無く単一の値。resource 4 / data 4 と同義）、world_version 1963、
  内部サーバーは **Java 8 世代**。
- **490 とのパケットID差分は 0 件**：minecraft-data には 1.14.2 の
  フォルダが無いため、1.14.1 の protocol.json と、ViaVersion の
  `Protocol1_14_1To1_14_2` が空プロトコル（＝480 と 485 はパケット
  集合・形式とも同一）であること、1.14.3 の protocol.json との
  マッピング比較（差分 0 件）を突き合わせ、対応表の全281件を
  コンセンサス方式で 485 に対応付けた（`tools/packet-ids-485.py`、
  全281件が一意に対応）。485 に存在するのは 147 件で、490 と
  完全一致する（`PacketIds485Test` で固定）。掘削 ack
  （acknowledge_player_digging）は 485 にも 490 にも無く、
  498 で追加されたもの。
- **490 とのレジストリ差分はワイヤ上ゼロ**：両方の実 jar で
  data-generator（`java -cp server.jar net.minecraft.data.Main
  --reports`、JDK 8）を実行してダンプを比較した。blocks.json は
  バイト単位で一致。registries.json の差分は 3 カテゴリ
  （memory_module_type 24→26、recipe_serializer 19→20、
  sensor_type 9→10）だけで、いずれも RegistryRemapper の変換表に
  現れないためワイヤに影響しない。commands.json の差分は
  `/gamerule disableRaids` リテラルの追加のみ（Declare Commands は
  未実装のため無関係）。そのため 485 のレジストリ写像は 490 の表へ
  委譲する（`RegistryRemapper485Test` が全範囲で 490 との一致を固定）。
- **形式の差分は Trade List の 1 件だけ**：490（と 498／758）の
  Trade List には末尾に **canRestock (bool)** があるが、485 には無い
  （canRestock は 1.14.3 で追加された。demand (i32) は 1.14.4 で
  追加されたため 485／490 ともに無い）。758→485 では取引ごとの
  demand と末尾の canRestock を落とし、485→758 では demand=0 と
  canRestock を補う。canRestock の補完値は ViaVersion の
  1.14.2→1.14.3 変換（`Protocol1_14_2To1_14_3` の Merchant Offers
  書き換え）と同じく isRegularVillager の値で代用する（近似。
  `ChunkTranslator485Test` で固定）。Join Game／Respawn／チャンク／
  粒子などの他の形式は 490 と完全一致。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **アダプタ**：新モジュール `detergentmc-adapter-v1_14_2`
  （V1142Adapter、プロトコル485）。
- **PacketIds**：485 列を追加（490 列と全件一致）。
- **RegistryRemapper**：485 の写像はすべて 490 の表へ委譲。
- **ChunkTranslator**：Trade List の 485 版
  （`translateTradeList485To758`／`translateTradeList758To485`、
  demand と canRestock の付け外し）を追加。それ以外は 490 用を共用。
- **ProxySession**：490／498 の経路を共用し、パケットIDの参照先だけ
  `oldClientProtocol()`／`oldServerProtocol()` の3分岐で 485 列に
  切り替える。Trade List のケースだけ 485 用変換に分岐する。
  変換状態（次元・保留中の光）は 490 と同じ形式のためフィールドを
  共用し、リソースパックだけ `pack485` を別に持つ。
- **ClientAdapter**：最古の対応クライアントが 485 になった
  （`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_14_2`）。versionName(485) は
  "1.14.2"。485 未満は従来どおり断る。
- **ClientProxy**：内部プロトコルとして 485 を受け入れ、`pack485` を
  セッションへ伝える。
- **リソースパック**：pack_format **4**（490／498 と同値）。
  `build485()`／`detergentmc-pack-1.14.2.zip`、ResourcePackServer の
  25パック構成、ResourcePackMode の `resource-pack.url-485`／
  `sha1-485`、DetergentServer の `resourcePack485()`。485 パックが
  無い場合は 490 パック（同じ pack_format 4）にフォールバックする。
  内部サーバーが 485 のときはネイティブ扱い（バニラのパックを中継）。
- **内部サーバー**：VanillaServerHost の Java 8 選択に 1.14.2 を追加。
  DatapackGenerator の pack_format も 4。
- **Bedrock**：JavaBackendConnection の 490 分岐に 485 を追加
  （Join Game は parseJoinGame498 を共用、バイオーム名だけ
  biomeNames485）、BedrockChunkTranslator は translate498 を共用し
  正規化に PROTOCOL_485 ケースを追加、BedrockEntityTranslator にも
  485 ケース（いずれも中身は 490 表への委譲）。
- **versionedJars**：`1.14.2` を追加（`DetergentMC-1.14.2-1.jar`）。

## 検証

- 分割検証（フルクリーンビルドはしない）：`split-verification-485/` に
  集計。**重複排除後の総数 3,539 件（成功 3,426、失敗 0、エラー 0、
  スキップ 113）**。スキップは、対象 jar がこのツリーに無い統合セルと
  Bedrock の UDP エンドツーエンド（サンドボックスが UDP 送信を禁止）
  が主因。モジュール別は protocol 2,740、bedrock 369、core 360、
  アダプタ群 67、content／bubble 3。
- 統合テスト（実バニラ＋フェイククライアント）：ProxyIntegrationTest
  全クラス（126メソッド）の実行で、新規3セル
  （fake485→実1.14.2、fake485→実1.14.3、fake490→実1.14.2）と
  490・498・573・575・578・758 の回帰セルを含む
  `split-verification-485/run-integration-485.sh` の23セルが
  すべて成功した（スキップ0）。
- 検証中に直したテスト側の期待値：変換ツールで 490 用テストから
  生成した 485 用テストのうち、実態と違う期待値（490 とのパケット
  ID差分が1件ある前提、掘削 ack が 490 に届く前提、ResourcePackMode
  の引数位置、ResourcePackServer の25パック構成など）を実データに
  合わせて直した。本番コードの分岐は変更なし。また Bedrock の
  チャンク／エンティティ正規化はケース順が 490→498→573 のため
  複製ツールが 498 ケースを巻き込んで重複したので、重複を除去し
  normalizeItem へ正しく追加した（Stage D コミットに記録）。
- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  実機確認の手順は `docs/PLAYTEST-1.14.2.md`。

## 残る制限

- 490 と同じ制限を引き継ぐ（Declare Recipes／Statistics／Tags／
  World Event ID は全方向で未実装、など。詳細は `docs/PROXY-1.14.3.md`）。
- Trade List の canRestock は 485→新形式で isRegularVillager の値で
  代用するため、村人の補充可否の表示が実物と違う場合がある（近似）。
- バイオームの変種（hills 等）は新しいバージョンへ変換すると素の
  バイオームに寄り、往復で元に戻らない（490 と同じ、レジストリ写像の
  設計どおり）。
- Bedrock→485 は単体テストのみ（サンドボックスが UDP 送信を禁じる
  ためエンドツーエンド未実施）。
- このツリーのプロトコルは 485・490・498・573・575・578・758〜776 の
  計25個。組み合わせは理屈の上では 25×25＝625 通りだが、統合テストで
  確認済みなのは実際に走らせたセルだけである。625 通りすべてが
  検証済みだとは言わない。
