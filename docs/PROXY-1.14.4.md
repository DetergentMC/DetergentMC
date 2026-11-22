# 1.14.4クライアント／サーバー（プロトコル498）の対応

DetergentMC に **498（Minecraft 1.14.4）** を追加した記録。隣接バージョンは
573（1.15）で、このツリーでは 573 の1段下として配線した。573／575／578 が
互いに完全一致だったのとは違い、**498 と 573 の間には本物の差分がある**
（パケットID 85件、レジストリ各表、Join Game／チャンク／粒子の形式）。
そのため 498⇔758 の専用変換を実データから作り、既存の
758⇔759⇔…の鎖に重ねた。

## 確定した事実（すべて実測）

- **バニラ 1.14.4 サーバーjar**：piston-meta から取得。
  SHA-1 `3dc3d84a581f14691199cf6831b71ed1296a9fdf`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、35,958,734 バイト。
  `run/vanilla/server-1.14.4.jar`（リポジトリには含めない）。
- **version.json**（実 jar から読み取り）：protocol_version **498**、
  pack_version は **4**（1.14.4 の version.json は resource／data の区別が
  無く単一の値。resource 4 / data 4 と同義）、world_version 1976、
  内部サーバーは **Java 8 世代**。
- **573 とのパケットID差分**：対応表の全281件中、498 に存在するのは
  148 件。573 と ID が違うのは **85 件で、すべてプレイ状態の
  クライアント行き**（サーバー行き・ログイン・ハンドシェイクは全件同値）。
  ずれの起点は acknowledge_player_digging の位置が 0x08（573）→
  0x5c（498）に移ったことで、間の ID がずれている。minecraft-data の
  protocol.json（1.14.4／1.15）同士の比較と、実 jar の照合で確定した
  （`tools/packet-ids-498.py`、`PacketIds498Test` で固定）。
- **レジストリ差分（対 578）**：ブロック状態 11,271（578 は 11,337）、
  ブロック 676（680）、アイテム 877（884）、エンティティ種別 102（103）、
  サウンド 795（810）、パーティクル 58、バイオーム 75（同数だが顔ぶれと
  並びが違う）。表は実バニラ 1.14.4 jar のデータから生成し、
  `tools/verify-498-tables.py` で検証した。なお 758 のバイオーム
  レジストリには旧来の変種バイオーム（hills 等）が無いため、498→758 の
  バイオーム写像では変種は素のバイオームへ寄る（往復で元に戻らない。
  写像表の設計どおり）。
- **形式の主な違い（対 578）**：
  - Join Game／Respawn に hashedSeed と enableRespawnScreen が無い。
    498 形式は {エンティティID, ゲームモード byte, ディメンション int,
    最大プレイヤー byte, レベル種別 string, 描画距離 VarInt,
    デバッグ情報削減 bool}。欠けた分は 0／true を補う。
  - チャンクのバイオームはデータバッファ末尾の 256 件（列ごと int）。
    758 のセクション別コンテナとの間で並べ替える。
  - World Particles の座標が **f32**（578／758 は f64）。
  - Spawn Entity Living の末尾にエンティティメタデータが付く
    （758 には無いため分離し、逆向きは補う）。
  - Game State Change の reason 11（immediate_respawn）は 498 に
    無いため落とす。
  - ログインは 578 と同形式（名前だけの Login Start、文字列 UUID の
    Login Success）。チャットは旧式（JSON＋位置 byte）で、758 からの
    変換では送信者 UUID を落とす。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **アダプタ**：新モジュール `detergentmc-adapter-v1_14_4`
  （V1144Adapter、プロトコル498）。
- **PlayTranslator**：Join Game／Respawn の 498⇔758 変換を追加。
- **ChunkTranslator**：498 セクションを追加。578 と形式が同じ変換は
  写し（レジストリ表は 498 用）、形式が違うものは専用実装
  （バイオーム末尾256件、Spawn Living の末尾メタデータ、
  World Particles の f32 座標＋末尾データ）。
- **ProxySession**：`is498()`／`isServer498()` と 498 の経路一式
  （convertServerPlayOutcomeTo498、translateServerPlayPacket758To498、
  routeServerPlay498、to758ServerPackets498、to758ClientPacket498、
  to498ClientPacket758、Update Light の貯蔵、pack498）。
  ログイン系は 578 と同形式のため既存分岐へ 498 を追加した。
- **ClientAdapter**：最古の対応クライアントが 498 になった
  （`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_14_4`）。versionName(498) は
  "1.14.4"。498 未満は従来どおり断る。
- **ClientProxy**：内部プロトコルとして 498 を受け入れ、`pack498` を
  セッションへ伝える。
- **リソースパック**：pack_format **4**。`build498()`／
  `detergentmc-pack-1.14.4.zip`、ResourcePackServer の23パック構成、
  ResourcePackMode の `resource-pack.url-498`／`sha1-498`、
  DetergentServer の `resourcePack498()`。内部サーバーが 498 のときは
  ネイティブ扱い（バニラのパックを中継し、DetergentMC のパックは
  差し込まない）。
- **内部サーバー**：VanillaServerHost の Java 8 選択に 1.14.4 を追加。
  DatapackGenerator の pack_format も 4。
- **Bedrock**：JavaBackendConnection に 498 分岐（Join Game は
  parseJoinGame498、Spawn Living は末尾メタデータを読み飛ばす専用
  パース）、BedrockChunkTranslator に translate498（バイオームは
  バッファ末尾256件）、BedrockEntityTranslator に 498 ケース。
- **versionedJars**：`1.14.4` を追加（`DetergentMC-1.14.4-1.jar`）。

## Stage E で見つけて直した配線漏れ（3件）

検証（Stage E）で、Stage B／D の配線漏れが3件見つかった。いずれも
「498 を旧世代クライアントの列挙に足し忘れた」もので、コミット
`e275328` で直した。

1. **ClientJoinFlow** の設定フェーズ無し一覧に 498 が無く、498
   クライアントが存在しない設定フェーズへ入ってしまっていた。
2. **ProxySession.handleServerJoinGame763** と
   **handleServerPlayFrom763** の旧式クライアント列挙に 498 が無く、
   Join Game が 764／765 向けの経路に落ち（ID が 0x26 のまま届く）、
   System Chat とリソースパックのオファーが落とされていた。
3. **ResourcePackBuilder.writePack** の switch に pack_format 4 の
   ケースが無く、`build498()` が IllegalArgumentException
   （"Unknown resource-pack format: 4"）で落ちていた。

また、テスト側の誤りも4件直した（ClientAdapter578Test／
ClientAdapter758Test の並び順の期待値、ChunkTranslator498Test の
バイオーム往復の期待値＝変種バイオームの寄せを考慮する形に、
World Particles テストの読み漏れ、そして統合テストのフェイク
クライアントが 498 形式の Keep Alive（クライアント行き 0x20、
578 は 0x21）に応答しておらず、実バニラ 1.14.4 が参加直後に
タイムアウト切断していた件——応答 ID はどちらも 0x0f で、
フェイク側の応答対象 ID をプロトコルごとに切り替えるよう直した。
本番の素通し経路には問題は無かった）。

## 検証

- 分割検証（フルクリーンビルドはしない）：`split-verification-498/` に
  集計。**重複排除後の総数 3,415 件（成功 3,302、失敗 0、エラー 0、
  スキップ 113）**。スキップは、対象 jar がこのツリーに無い統合セルと
  Bedrock の UDP エンドツーエンド（サンドボックスが UDP 送信を禁止）
  が主因。モジュール別は protocol 2,648、bedrock 351、core 346、
  アダプタ群 67、content 3。
- 統合テスト（実バニラ＋フェイククライアント）：ProxyIntegrationTest
  全クラス（120メソッド）の実行で、新規3セル
  （fake498→実1.14.4、fake498→実1.15、fake573→実1.14.4）と
  573・575・578・758 の回帰14セルの計17セルを含む対象セルが
  すべて成功した（残りは対象 jar が無いためのスキップ）。
- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
  実機確認の手順は `docs/PLAYTEST-1.14.4.md`。

## 残る制限

- 578 と同じ制限を引き継ぐ（Declare Recipes／Statistics／Tags／
  World Event ID は全方向で未実装、など。詳細は `docs/PROXY-1.15.2.md`）。
- バイオームの変種（hills 等）は新しいバージョンへ変換すると素の
  バイオームに寄り、往復で元に戻らない（レジストリ写像の設計どおり）。
- Bedrock→498 は単体テストのみ（サンドボックスが UDP 送信を禁じる
  ためエンドツーエンド未実施）。
- このツリーのプロトコルは 498・573・575・578・758〜776 の計23個。
  組み合わせは理屈の上では 23×23＝529 通りだが、統合テストで
  確認済みなのは実際に走らせたセルだけである。529 通りすべてが
  検証済みだとは言わない。
