# 1.14クライアント／サーバー（プロトコル477）の対応

DetergentMC に **477（Minecraft 1.14）** を追加した記録。隣接バージョンは
480（1.14.1）で、このツリーでは 480 の1段下として配線した。480 と 485 の
間が差分 0 件だったのに続き、477 と 480 の間も **実データで確定した
差分が 0 件**（パケットID差分 0 件、ワイヤ上のレジストリ差分 0 件、
形式差分 0 件）。変換は 480／485／490／498 の経路をそのまま共用し、
477 を各分岐の列挙に加えただけである。1.14 は 1.14 系の初出だが、
1.14.1 との差はワイヤに現れない範囲（後述の memory_module_type のみ）
だった。

## 確定した事実（すべて実測）

- **バニラ 1.14 サーバーjar**：piston-meta から取得。
  SHA-1 `f1a0073671057f01aa843443fef34330281333ce`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、35,914,001 バイト。
  `run/vanilla/server-1.14.jar`（リポジトリには含めない）。
- **version.json**（実 jar から読み取り）：protocol_version **477**、
  pack_version は **4**（1.14 の version.json は resource／data の区別が
  無く単一の値。resource 4 / data 4 と同義）、world_version 1952、
  内部サーバーは **Java 8 世代**。
- **480 とのパケットID差分は 0 件**：minecraft-data の 1.14 と 1.14.1 の
  protocol.json は **バイト単位で一致**する（どちらも 149,201 バイト、
  平坦化した型比較でも差分 0）。さらに `tools/packet-ids-477.py` で
  対応表の全281エントリをコンセンサス方式で 477 に対応付けた
  （全281件が一意に対応）。結果は **477 列＝480 列が全エントリで一致**。
  477 に存在するのは 147 件で 480 と同数（`PacketIds477Test` で固定）。
  掘削 ack（acknowledge_player_digging）は 477 にも 480 にも 485 にも
  490 にも無く、498 で追加されたもの。表に無い唯一の 477 パケットは
  legacy_server_list_ping（0xfe、旧式 ping で、どのバージョンでも
  表管理していない）。
- **480 とのレジストリ差分はワイヤ上ゼロ**：両方の実 jar で
  data-generator（`java -cp server.jar net.minecraft.data.Main
  --reports`、JDK 8）を実行してダンプを比較した。blocks.json と
  commands.json はバイト単位で一致。registries.json の差は
  **memory_module_type の1カテゴリだけ**（477 は23件、480 は24件。
  480 で `minecraft:cant_reach_walk_target_since` が追加）で、
  memory_module_type は変換表のどこにも現れない（AI の記憶モジュールは
  ワイヤに載らない）ため、ワイヤ上の差分は 0 件。その他のカテゴリは
  件数・並びとも完全一致（block 676、item 877、entity_type 102、
  sound_event 795、biome 75 など。ブロック状態は 11,271 件で 498 と
  同数）。そのため 477 のレジストリ写像は 480 の表へ委譲する
  （`RegistryRemapper477Test` が全範囲で 480 との一致を固定）。
- **形式の差分も 0 件**：protocol.json がバイト一致するため、Join Game／
  Respawn／チャンク／Trade List などの形式はすべて 480 と同一。
  Trade List は demand も canRestock も無い 480／485 形式のまま。
  そのため 477 専用の ChunkTranslator メソッドは作らず、ProxySession の
  Trade List 分岐を `is477() || is480() || is485()` に広げて 485 用変換を
  共用する（`ChunkTranslator477Test` が 485 用変換のまま 477 形式を
  出し入れできることを固定）。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **アダプタ**：新モジュール `detergentmc-adapter-v1_14`
  （V114Adapter、プロトコル477）。
- **PacketIds**：477 列を追加（480 列と全件一致）。
- **RegistryRemapper**：477 の写像はすべて 480 の表へ委譲。
- **ChunkTranslator**：477 専用の変換は追加なし（485 用を共用）。
- **ProxySession**：498 の経路を共用し、パケットIDの参照先を
  `oldClientProtocol()`／`oldServerProtocol()` の5分岐
  （477／480／485／490／498）で切り替える。`is477()`／`isServer477()` を
  各列挙（ログイン、Login Success、旧式クライアント群、リソースパック
  提示、System Chat、内部サーバー経路など）に加え、Trade List の
  分岐は 485 用変換を共用。リソースパックだけ `pack477` を別に持つ
  （提示先の決定は pack477→pack480→pack485→pack490 の順にフォールバック）。
- **ClientAdapter**：最古の対応クライアントが 477 になった
  （`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_14`）。versionName(477) は
  "1.14"。477 未満（476 以下、478〜479・481〜484 などの未対応帯も含む）は
  従来どおり断る。
- **ClientProxy**：内部プロトコルとして 477 を受け入れ、`pack477` を
  セッションへ伝える。
- **リソースパック**：pack_format **4**（480／485／490／498 と同値）。
  `build477()`／`detergentmc-pack-1.14.zip`、ResourcePackServer の
  27パック構成、ResourcePackMode の `resource-pack.url-477`／
  `sha1-477`、DetergentServer の `resourcePack477()`。477 パックが
  無い場合は 480 パック（同じ pack_format 4）にフォールバックする。
  内部サーバーが 477 のときはネイティブ扱い（バニラのパックを中継）。
- **内部サーバー**：VanillaServerHost の Java 8 選択に 1.14 を追加。
  DatapackGenerator の pack_format も 4。
- **Bedrock**：JavaBackendConnection の 480 分岐に 477 を追加
  （Join Game は parseJoinGame498 を共用、バイオーム名は
  biomeNames477＝biomeNames480 へ委譲）、BedrockChunkTranslator は
  translate498 の経路を共用し正規化に PROTOCOL_477 ケースを追加、
  BedrockEntityTranslator にも 477 ケース（いずれも中身は 480／485 表
  への委譲）。
- **versionedJars**：`1.14` を追加（`DetergentMC-1.14-1.jar`）。

## 検証

- 分割検証（フルクリーンビルドはしない）：`split-verification-477/` に
  集計。**重複排除後の総数 3,665 件（成功 3,552、失敗 0、エラー 0、
  スキップ 113）**。スキップは、対象 jar がこのツリーに無い統合セルと
  Bedrock の UDP エンドツーエンド（サンドボックスが UDP 送信を禁止）
  が主因。モジュール別は protocol 2,834、bedrock 387、core 374、
  アダプタ群 67、content／bubble 3。
- 統合テスト（実バニラ＋フェイククライアント）：新規3セル
  （fake477→実1.14、fake477→実1.14.1、fake480→実1.14）と
  480・485・490・498・573・575・578・758 の回帰セルを含む
  `split-verification-477/run-integration-477.sh` の29セルが
  すべて成功した（スキップ0）。ProxyIntegrationTest 全メソッド
  （132件）の分割実行も行い、すべて成功または対象 jar 不在による
  スキップだった。
- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  実機確認の手順は `docs/PLAYTEST-1.14.md`。

## 残る制限

- 480 と同じ制限を引き継ぐ（Declare Recipes／Statistics／Tags／
  World Event ID は全方向で未実装、など。詳細は `docs/PROXY-1.14.1.md`）。
- Trade List の canRestock は 477→新形式で isRegularVillager の値で
  代用するため、村人の補充可否の表示が実物と違う場合がある
  （485 用変換の近似をそのまま引き継ぐ）。
- バイオームの変種（hills 等）は新しいバージョンへ変換すると素の
  バイオームに寄り、往復で元に戻らない（480 と同じ、レジストリ写像の
  設計どおり）。
- Bedrock→477 は単体テストのみ（サンドボックスが UDP 送信を禁じる
  ためエンドツーエンド未実施）。
- このツリーのプロトコルは 477・480・485・490・498・573・575・578・
  758〜776 の計27個。組み合わせは理屈の上では 27×27＝729 通りだが、
  統合テストで確認済みなのは実際に走らせたセルだけである。729 通り
  すべてが検証済みだとは言わない。
