# 1.15クライアント／サーバー（プロトコル573）の対応

DetergentMC に **573（Minecraft 1.15）** を追加した記録。隣接バージョンは
575（1.15.1）で、このツリーでは 575 の1段下として配線した。結論から言うと
**573 のワイヤ形式は 575／578 と完全一致**する。これは推測ではなく、
実バニラ 1.15 jar と 1.15.1 jar の両方から生成したデータで証明した
（下記）。そのため変換関数そのものは 578 と共用し、プロトコル番号が
効く箇所すべてに 573 を一級で配線した。

## 確定した事実（すべて実測）

- **バニラ 1.15 サーバーjar**：piston-meta から取得。
  SHA-1 `e9f105b3c5c7e85c7b445249a93362a22f62442d`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、36,164,465 バイト。
  `run/vanilla/server-1.15.jar`（リポジトリには含めない）。
- **version.json**：protocol_version **573**、pack_version は **5**
  （1.15 の version.json は resource／data の区別が無く単一の値。
  resource 5 / data 5 と同義）、world_version 2225（1.15.1 は 2227、
  1.15.2 は 2230）、javaVersion は jre-legacy（**Java 8 世代**、
  1.15.1／1.15.2 と同じ）。
- **575 との差分はゼロ（証明済み）**：
  - 実バニラ 1.15 jar と 1.15.1 jar の data-generator ダンプ
    （`--reports` の registries.json／blocks.json／commands.json）が
    **バイト単位で完全一致**（全41レジストリ、680ブロック、
    ブロック状態 11,337）。
  - PrismarineJS minecraft-data の protocol.json（1.15 用と 1.15.1 用）が
    **バイト単位で同一**（sha256
    `b29e0a4f55606cdc86f9cef4d919f8fab7fed647e213e0d1da6256274be6dc09`。
    1.15.2 用とも同一）。`tools/packet-ids-573.py` はこの同一性が
    崩れたら表を作らずに失敗する。
  - よってパケットID表（281件中 573 に存在するのは 148 件、すべて
    575／578 と同じ ID）とレジストリ表は 578 のものをそのまま使う。
    PacketIds には 573 列（全件 575 列と同値）を、RegistryRemapper には
    573⇔758 の委譲メソッド（578 表へ委譲）を置き、テストで全範囲一致を
    固定した。
- 1.15 と 1.15.1 の違いはワイヤの外（バグ修正など）で、プロトコルには
  現れない、というのが実測の結論。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **ProxySession**：`is573()`／`isServer573()` を追加し、578 の形式分岐が
  発火する箇所すべて（ログイン処理、Login Success の文字列 UUID 形、
  Join Game、チャンク、チャット等）に 573 を OR で追加。内部サーバー
  検証にも V1_15 を追加。変換関数自体は 578 と共用。
- **ClientAdapter**：最古の対応クライアントが 573 になった
  （`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_15`）。versionName(573) は
  "1.15"。573 未満（574〜757 の未対応帯を含む）は従来どおり断る。
- **ClientProxy**：内部プロトコルとして 573 を受け入れ、`pack573` を
  セッションへ伝える。
- **アダプタ**：新モジュール `detergentmc-adapter-v1_15`
  （V1151Adapter の同型。"1.15.1" 以後の BlockType は無いため
  ブロック集合は 1.15.1／1.15.2 と同一）。
- **リソースパック**：pack_format **5**。`build573()`／
  `detergentmc-pack-1.15.zip`、ResourcePackServer の22パック構成、
  ResourcePackMode の `resource-pack.url-573`／`resource-pack.sha1-573`、
  DetergentServer の `resourcePack573()`。内部サーバーが 573 のときは
  575／578 のときと同じくネイティブ扱い（バニラのパックを中継し、
  DetergentMC のパックは差し込まない）。
- **内部サーバー**：VanillaServerHost の Java 8 選択は "1.15" でも
  発火する（578 で入れた仕組みを共用）。DatapackGenerator の
  pack_format も 5。
- **Bedrock**：バックエンドが 573 のとき 578 と同じ分岐を通る
  （JavaBackendConnection の各条件、BedrockChunkTranslator／
  BedrockEntityTranslator の PROTOCOL_573 ケース。レジストリ変換は
  573 用の委譲メソッド経由）。
- **versionedJars**：`1.15` を追加（`DetergentMC-1.15-1.jar`）。
  build-numbers.properties に `1.15=1`。

## 検証

- 単体テスト：573 用の新規テスト（PacketIds573Test＝全281件で 575 列と
  一致を固定、RegistryRemapper573Test＝全範囲で 578 表と一致を固定、
  ProxySession573Test、ClientAdapter573Test、ClientJoinFlow573Test、
  ChunkTranslator573Test、PlayTranslator573Test、
  ResourcePackBuilder／Mode／Server573Test、RealVanilla758Join573Test、
  Bedrock の JavaBackendConnection573Test／BedrockChunkTranslator573Test、
  core の AdapterDiscovery／ServerProtocolWiring／ResourcePackWiring／
  DatapackGenerator／VanillaServerHost の 573 追加分）。
- 統合テスト（実バニラ＋フェイククライアント）：
  fake573→実1.15（内部573）、fake573→実1.15.1（内部575）、
  fake575→実1.15（内部573）の新規3セルと、575・578・758 の回帰セル。
  詳細は `split-verification-573/` と `docs/VERSIONS.md` の 573 節。
- **本物のゲームクライアントでの確認は未実施**（575 以下すべてと同じ）。
  実機確認の手順は `docs/PLAYTEST-1.15.md`。

## 残る制限

- 578 と同じ制限をすべて引き継ぐ（Declare Recipes／Statistics／Tags／
  World Event ID は全方向で未実装、など。詳細は `docs/PROXY-1.15.2.md`）。
- このツリーのプロトコルは 573・575・578・758〜776 の計22個。
  組み合わせは理屈の上では 22×22＝484 通りだが、統合テストで
  確認済みなのは実際に走らせたセルだけである。484 通りすべてが
  検証済みだとは言わない。
