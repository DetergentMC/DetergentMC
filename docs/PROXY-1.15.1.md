# 1.15.1クライアント／サーバー（プロトコル575）の対応

DetergentMC に **575（Minecraft 1.15.1）** を追加した記録。隣接バージョンは
578（1.15.2）で、このツリーでは 578 の1段下として配線した。結論から言うと
**575 のワイヤ形式は 578 と完全一致**する。これは推測ではなく、両方の実バニラ
jar から生成したデータで証明した（下記）。そのため変換関数そのものは 578 と
共用し、プロトコル番号が効く箇所すべてに 575 を一級で配線した。

## 確定した事実（すべて実測）

- **バニラ 1.15.1 サーバーjar**：piston-meta から取得。
  SHA-1 `4d1826eebac84847c71a77f9349cc22afd0cf0a1`（piston-data の
  オブジェクトIDと一致、照合済み）、36,164,938 バイト。
  `run/vanilla/server-1.15.1.jar`（リポジトリには含めない）。
- **version.json**：protocol_version **575**、pack_version は **5**
  （1.15.1 の version.json は resource／data の区別が無く単一の値。
  resource 5 / data 5 と同義）、world_version 2227（1.15.2 は 2230）、
  javaVersion は jre-legacy（**Java 8 世代**、1.15.2 と同じ）。
- **578 との差分はゼロ（証明済み）**：
  - 両 jar の data-generator ダンプ（`--reports`）が**全41レジストリで
    完全一致**（エントリ・順序・protocol_id すべて）。blocks.json も一致
    （680ブロック、ブロック状態 11,337）。
  - PrismarineJS minecraft-data の protocol.json（1.15.1 用と 1.15.2 用）が
    **バイト単位で同一**（sha256
    `b29e0a4f55606cdc86f9cef4d919f8fab7fed647e213e0d1da6256274be6dc09`）。
    `tools/packet-ids-575.py` はこの同一性が崩れたら表を作らずに失敗する。
  - よってパケットID表（281件中 575 に存在するのは 148 件、すべて 578 と
    同じ ID）とレジストリ表は 578 のものをそのまま使う。PacketIds には
    575 列（全件 578 列と同値）を、RegistryRemapper には 575⇔758 の
    委譲メソッド（578 表へ委譲）を置き、テストで全範囲一致を固定した。
- 1.15.1 と 1.15.2 の違いはワイヤの外（ワールド生成のシード処理や
  バグ修正など）で、プロトコルには現れない、というのが実測の結論。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **ProxySession**：`is575()`／`isServer575()` を追加し、578 の形式分岐が
  発火する箇所すべて（ログイン処理、Login Success の文字列 UUID 形、
  Join Game、チャンク、チャット等）に 575 を OR で追加。内部サーバー
  検証にも V1_15_1 を追加。変換関数自体は 578 と共用。
- **ClientAdapter**：最古の対応クライアントが 575 になった
  （`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_15_1`）。versionName(575) は
  "1.15.1"。
- **ClientProxy**：内部プロトコルとして 575 を受け入れ、`pack575` を
  セッションへ伝える。
- **アダプタ**：新モジュール `detergentmc-adapter-v1_15_1`
  （V1152Adapter の同型。"1.15.2" 以後の BlockType は無いため
  ブロック集合は 1.15.2 と同一）。
- **リソースパック**：pack_format **5**。`build575()`／
  `detergentmc-pack-1.15.1.zip`、ResourcePackServer の21パック構成、
  ResourcePackMode の `resource-pack.url-575`／`resource-pack.sha1-575`、
  DetergentServer の `resourcePack575()`。内部サーバーが 575 のときは
  578 のときと同じくネイティブ扱い（バニラのパックを中継し、
  DetergentMC のパックは差し込まない）。
- **内部サーバー**：VanillaServerHost の Java 8 選択は "1.15.1" でも
  発火する（578 で入れた仕組みを共用）。DatapackGenerator の
  pack_format も 5。
- **Bedrock**：バックエンドが 575 のとき 578 と同じ分岐を通る
  （JavaBackendConnection の各条件、BedrockChunkTranslator／
  BedrockEntityTranslator の PROTOCOL_575 ケース。レジストリ変換は
  575 用の委譲メソッド経由）。
- **versionedJars**：`1.15.1` を追加（`DetergentMC-1.15.1-1.jar`）。
  build-numbers.properties に `1.15.1=1`。

## 検証

- 単体テスト：575 用の新規テスト（PacketIds575Test＝全281件で 578 列と
  一致を固定、RegistryRemapper575Test＝全範囲で 578 表と一致を固定、
  ProxySession575Test、ClientAdapter575Test、ClientJoinFlow575Test、
  ChunkTranslator575Test、PlayTranslator575Test、
  ResourcePackBuilder／Mode／Server575Test、RealVanilla758Join575Test、
  Bedrock の JavaBackendConnection575Test／BedrockChunkTranslator575Test、
  core の AdapterDiscovery／ServerProtocolWiring／ResourcePackWiring／
  DatapackGenerator／VanillaServerHost の 575 追加分）。
- 統合テスト（実バニラ＋フェイククライアント）：
  fake575→実1.15.1（内部575）、fake575→実1.15.2（内部578）、
  fake578→実1.15.1（内部575）の新規3セルと、578・758 の回帰セル。
  詳細は `split-verification-575/` と `docs/VERSIONS.md` の 575 節。
- **本物のゲームクライアントでの確認は未実施**（578 以下すべてと同じ）。
  実機確認の手順は `docs/PLAYTEST-1.15.1.md`。

## 残る制限

- 578 と同じ制限をすべて引き継ぐ（Declare Recipes／Statistics／Tags／
  World Event ID は全方向で未実装、など。詳細は `docs/PROXY-1.15.2.md`）。
- このツリーのプロトコルは 575・578・758〜776 の計21個。組み合わせは
  理屈の上では 21×21＝441 通りだが、統合テストで確認済みなのは
  実際に走らせたセルだけである。441 通りすべてが検証済みだとは言わない。
