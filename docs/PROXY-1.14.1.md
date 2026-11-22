# 1.14.1クライアント／サーバー（プロトコル480）の対応

DetergentMC に **480（Minecraft 1.14.1）** を追加した記録。隣接バージョンは
485（1.14.2）で、このツリーでは 485 の1段下として配線した。485 と 490 の
間には形式差分が1件あったが、480 と 485 の間は **実データで確定した
差分が 0 件**（パケットID差分 0 件、ワイヤ上のレジストリ差分 0 件、
形式差分 0 件）。変換は 485／490／498 の経路をそのまま共用し、
480 を各分岐の列挙に加えただけである。

## 確定した事実（すべて実測）

- **バニラ 1.14.1 サーバーjar**：piston-meta から取得。
  SHA-1 `ed76d597a44c5266be2a7fcd77a8270f1f0bc118`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、35,927,691 バイト。
  `run/vanilla/server-1.14.1.jar`（リポジトリには含めない）。
- **version.json**（実 jar から読み取り）：protocol_version **480**、
  pack_version は **4**（1.14.1 の version.json は resource／data の区別が
  無く単一の値。resource 4 / data 4 と同義）、world_version 1957、
  内部サーバーは **Java 8 世代**。
- **485 とのパケットID差分は 0 件**：minecraft-data には 1.14.1 自身の
  protocol.json があるため、それを入力に `tools/packet-ids-480.py` で
  対応表の全281エントリをコンセンサス方式で 480 に対応付けた
  （全281件が一意に対応）。結果は **480 列＝485 列が全エントリで一致**。
  480 に存在するのは 147 件で 485 と同数（`PacketIds480Test` で固定）。
  掘削 ack（acknowledge_player_digging）は 480 にも 485 にも 490 にも
  無く、498 で追加されたもの。表に無い唯一の 480 パケットは
  legacy_server_list_ping（0xfe、旧式 ping で、どのバージョンでも
  表管理していない）。
- **485 とのレジストリ差分はワイヤ上ゼロ**：両方の実 jar で
  data-generator（`java -cp server.jar net.minecraft.data.Main
  --reports`、JDK 8）を実行してダンプを比較した。blocks.json と
  commands.json はバイト単位で一致。registries.json も全36カテゴリで
  件数・並びとも完全一致（biome 75、block 676、item 877、
  entity_type 102、sound_event 795 など）。485 のときに差があった
  memory_module_type／recipe_serializer／sensor_type も、480 と 485 の
  間では差が無い。そのため 480 のレジストリ写像は 485 の表へ委譲する
  （`RegistryRemapper480Test` が全範囲で 485 との一致を固定）。
- **形式の差分も 0 件**：minecraft-data の protocol.json を平坦化して
  1.14.1 と 1.14.3 を比べると差は正確に1件だけ（Trade List に
  canRestock が 1.14.3 で追加）で、これは 485 との差として既知のもの。
  つまり 480 の Trade List 形式は 485 と同一（demand も canRestock も
  無い）。そのため 480 専用の ChunkTranslator メソッドは作らず、
  ProxySession の Trade List 分岐を `is480() || is485()` に広げて
  485 用変換を共用する（`ChunkTranslator480Test` が 485 用変換のまま
  480 形式を出し入れできることを固定）。Join Game／Respawn／チャンク／
  粒子などの他の形式も 485 と完全一致。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **アダプタ**：新モジュール `detergentmc-adapter-v1_14_1`
  （V1141Adapter、プロトコル480）。
- **PacketIds**：480 列を追加（485 列と全件一致）。
- **RegistryRemapper**：480 の写像はすべて 485 の表へ委譲。
- **ChunkTranslator**：480 専用の変換は追加なし（485 用を共用）。
- **ProxySession**：498 の経路を共用し、パケットIDの参照先を
  `oldClientProtocol()`／`oldServerProtocol()` の4分岐
  （480／485／490／498）で切り替える。`is480()`／`isServer480()` を
  各列挙（ログイン、Login Success、旧式クライアント群、リソースパック
  提示、System Chat、内部サーバー経路など）に加え、Trade List の
  分岐は 485 用変換を共用。リソースパックだけ `pack480` を別に持つ
  （提示先の決定は pack480→pack485→pack490 の順にフォールバック）。
- **ClientAdapter**：最古の対応クライアントが 480 になった
  （`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_14_1`）。versionName(480) は
  "1.14.1"。480 未満は従来どおり断る。
- **ClientProxy**：内部プロトコルとして 480 を受け入れ、`pack480` を
  セッションへ伝える。
- **リソースパック**：pack_format **4**（485／490／498 と同値）。
  `build480()`／`detergentmc-pack-1.14.1.zip`、ResourcePackServer の
  26パック構成、ResourcePackMode の `resource-pack.url-480`／
  `sha1-480`、DetergentServer の `resourcePack480()`。480 パックが
  無い場合は 485 パック（同じ pack_format 4）にフォールバックする。
  内部サーバーが 480 のときはネイティブ扱い（バニラのパックを中継）。
- **内部サーバー**：VanillaServerHost の Java 8 選択に 1.14.1 を追加。
  DatapackGenerator の pack_format も 4。
- **Bedrock**：JavaBackendConnection の 485 分岐に 480 を追加
  （Join Game は parseJoinGame498 を共用、バイオーム名だけ
  biomeNames480）、BedrockChunkTranslator は translate498 の経路を
  共用し正規化に PROTOCOL_480 ケースを追加、BedrockEntityTranslator
  にも 480 ケース（いずれも中身は 485 表への委譲）。
- **versionedJars**：`1.14.1` を追加（`DetergentMC-1.14.1-1.jar`）。

## 検証中に見つけて直した既存バグ

- **Bedrock→498 の送信形式が壊れていた**：485 の Stage D で使った
  `patch-485-bedrock.py` が、JavaBackendConnection の3分岐
  （sendChat、sendCommand、Login Start の旧式フォーマット分岐）から
  `serverProtocol == 498` を誤って落としていた（置換文字列が
  `… || 573 || 498 || 490` を `… || 573 || 490 || 485` に変えたため）。
  この状態では Bedrock から内部 498 サーバーへ、署名付きチャット形式
  （761以降の形式）や誤った Login Start が送られていた。480 の
  Stage D で 498 を復帰させて 480 を追加した（現在は
  `758 || 578 || 575 || 573 || 498 || 490 || 485 || 480`）。
  既存テストで検出できなかったのは、Bedrock→498 が単体テストのみで、
  当該送信パスを厳密に見るテストが無かったため。

## 検証

- 分割検証（フルクリーンビルドはしない）：`split-verification-480/` に
  集計。**重複排除後の総数 3,601 件（成功 3,488、失敗 0、エラー 0、
  スキップ 113）**。スキップは、対象 jar がこのツリーに無い統合セルと
  Bedrock の UDP エンドツーエンド（サンドボックスが UDP 送信を禁止）
  が主因。モジュール別は protocol 2,786、bedrock 378、core 367、
  アダプタ群 67、content／bubble 3。
- 統合テスト（実バニラ＋フェイククライアント）：新規3セル
  （fake480→実1.14.1、fake480→実1.14.2、fake485→実1.14.1）と
  485・490・498・573・575・578・758 の回帰セルを含む
  `split-verification-480/run-integration-480.sh` の26セルが
  すべて成功した（スキップ0）。ProxyIntegrationTest 全メソッド
  （129件）の分割実行も行い、すべて成功または対象 jar 不在による
  スキップだった。
- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  実機確認の手順は `docs/PLAYTEST-1.14.1.md`。

## 残る制限

- 485 と同じ制限を引き継ぐ（Declare Recipes／Statistics／Tags／
  World Event ID は全方向で未実装、など。詳細は `docs/PROXY-1.14.2.md`）。
- Trade List の canRestock は 480→新形式で isRegularVillager の値で
  代用するため、村人の補充可否の表示が実物と違う場合がある
  （485 用変換の近似をそのまま引き継ぐ）。
- バイオームの変種（hills 等）は新しいバージョンへ変換すると素の
  バイオームに寄り、往復で元に戻らない（485 と同じ、レジストリ写像の
  設計どおり）。
- Bedrock→480 は単体テストのみ（サンドボックスが UDP 送信を禁じる
  ためエンドツーエンド未実施）。
- このツリーのプロトコルは 480・485・490・498・573・575・578・758〜776 の
  計26個。組み合わせは理屈の上では 26×26＝676 通りだが、統合テストで
  確認済みなのは実際に走らせたセルだけである。676 通りすべてが
  検証済みだとは言わない。
