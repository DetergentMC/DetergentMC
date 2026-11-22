# 1.13.1クライアント／サーバー（プロトコル401）の対応

DetergentMC に **401（Minecraft 1.13.1）** を追加した記録。隣接バージョンは
404（1.13.2）で、このツリーでは 404 の1段下として配線した。404 と 477 の
差分が大きかったのとは対照的に、**401 と 404 の差分は形式1件だけ**
（スロットの符号化）。パケットID・レジストリ・その他の形式はすべて
同一のため、401 の実装は「404 の経路をそのまま共用し、境界でスロットの
符号化だけを付け替える」形にした。

## 確定した事実（すべて実測）

- **バニラ 1.13.1 サーバーjar**：piston-meta から取得。
SHA-1 `fe123682e9cb30031eae351764f653500b7396c9`（piston-meta の
downloads.server.sha1 と一致、照合済み）、33,832,589 バイト。
`run/vanilla/server-1.13.1.jar`（リポジトリには含めない）。
**jar ルートに version.json が無い世代**（404 と同じ）。
プロトコルは **401**（実バニラの Status 応答でも `"protocol":401` を
確認）、pack_format は **4（単一値）**、world_version（DataVersion）は
**1628**、内部サーバーは **Java 8 世代**。
- **404 とのパケットID差分は 0 件**：`tools/packet-ids-401.py` で
対応表の全282エントリを 1.13.1 自身の protocol.json（minecraft-data
pc/1.13.1）に対応付け、全件一意に写像でき、**id401 == id404 が
全エントリで一致**した。404 だけの新規パケットだった bed（0x33）も
401 に存在する。表に無い唯一のパケットは legacy_server_list_ping
（0xfe、どのバージョンでも表管理していない）。
- **protocol.json の全比較**：1.13.1 と 1.13.2 の protocol.json を
平坦化して全比較したところ、パケットの mapper 表と全パケットの
フィールド構成は完全一致し、**唯一の差分は共有型 `slot` の定義**だった：
  - 401（旧形式）：`itemId: i16`（**-1 = 空**）→ `itemCount: i8` →
    `nbtData: optionalNbt`
  - 404（現行形式）：`present: bool` → `itemId: varint` →
    `itemCount: i8` → `nbtData: optionalNbt`
- **レジストリ**：両 jar の data generator 出力（blocks 598 種・
ブロック状態 **8,599**・items 790・commands・生成データツリー全体）を
ログを除いて全比較し、**バイト単位で完全一致**した。したがって
アイテムID・ブロック状態ID等の数値は 404 と同一で、生成表は 404 の
ものをそのまま使う。なお 1.13 世代の reports には registries.json が
無く、エンティティ・サウンド・粒子等の ID 表はダンプ同士の直接比較が
できない（404 のときと同じ事情）。裏付けは「protocol.json の完全一致
（メタデータ・粒子の型定義を含む）＋生成ダンプの完全一致＋Stage E の
実サーバー統合テスト」の組み合わせによる。
- **i16 の範囲**：旧形式の itemId は i16。404 空間のアイテムIDは
最大 789 で全件 i16 に収まるため、通常の変換で範囲外になることは
ない。範囲外の値が来た場合は落とさず石（1）に置き換えて警告する
（変換表の代替方針と同じ）。

## ポート構成

従来と同じ。Java プロキシ公開 25565（TCP）、内部バニラ 25566
（127.0.0.1 のみ）、リソースパック 25567、Bedrock UDP 19132。

## 配線の要点

- **アダプタ**：新モジュール `detergentmc-adapter-v1_13_1`
（V1131Adapter、プロトコル401）。
- **PacketIds**：401 列を追加（404 列と全件同値）。PLAY_BED は
401 にも存在する（0x33）。
- **SlotTranslator（新設）**：401⇔404 のスロット符号化だけを
付け替える。対象はスロットを含む全パケット：
  - クライアントbound：Window Items、Set Slot、Entity Equipment、
    Entity Metadata（型6＝スロット、型15＝粒子データ内のアイテム
    スロットを含む）、World Particles（アイテム粒子）、
    Advancements（表示アイコン）、Spawn Entity Living／
    Named Entity Spawn の末尾メタデータ。
  - サーバーbound：Window Click、Set Creative Slot。
  - Declare Recipes は 763 段で落とされるため対象外（レシピ内の
    ingredient スロットも同様）。Edit Book は 404 経路で名前で
    落とされるため対象外。Unlock Recipes はスロットを含まない
    （文字列のみ）ことを確認済み。
  - NBT は再解釈せずバイト列のまま写す（位置で切り取る）。
- **ProxySession**：`is401()`／`isServer401()` を追加し、404 の分岐
すべてに並べて配線した（ログイン開始・Login Success・参加確定・
掘削 ack・リソースパック提示・Join Game・プレイ状態の全経路）。
クライアント 401 は入口で旧→現行に付け替えてから 404 経路へ入れ、
出口は 404 形式へ変換した後に現行→旧へ付け替える。内部サーバー
401 も対称（入口で旧→現行、出口で現行→旧）。
- **ClientAdapter**：最古の対応クライアントが 401 になった
（`OLDEST_SUPPORTED_CLIENT_PROTOCOL = V1_13_1`）。
**404 は supportedClientProtocols() に明示的に残した**（404 のときに
477 で踏んだ「旧・最古の脱落」地雷の回避。テストで固定済み）。
versionName(401) は "1.13.1"。401 未満と未対応帯は従来どおり断る。
- **リソースパック**：pack_format **4**。`build401()`／
`detergentmc-pack-1.13.1.zip`、ResourcePackServer の29パック構成
（packForProtocol の 401 分岐は pack401→pack404→pack477→…の順に
代替）、ResourcePackMode の `resource-pack.url-401`／`sha1-401`、
DetergentServer の `resourcePack401()`。
- **内部サーバー**：VanillaServerHost の Java 8 起動対象に "1.13.1" を
追加。DatapackGenerator の pack_format は 4。
- **Bedrock→401**：JavaBackendConnection の旧式分岐すべてに 401 を
追加（Join Game は parseJoinGame404 を共用、Spawn Entity は
parseSpawnEntity404 を共用）。BedrockChunkTranslator は translate404
へ委譲（チャンクにスロットは含まれない）。BedrockEntityTranslator の
正規化（エンティティ・アイテム・ブロック状態）は 404 の分岐を共用。
**単体テストのみ**（統合テストなし）。
- **versionedJars**：1.13.1 を追加（build-numbers.properties に 1.13.1=1）。

## 実装中に見つけて直した問題

- **Bedrock の Player Info の crypto 有無の負列挙に 401 が欠落**：
`recordPlayerInfo760` の withCrypto 判定が「758・578・…・404 でない」
という負の列挙で、401 が「crypto あり」側に倒れ、実在しない crypto
ブロックを読もうとしてプレイヤー一覧ごと捨てられていた。401 を
負列挙に追加（単体テスト JavaBackendConnection401Test で発見）。

## 検証

- 分割検証（`split-verification-401/`）：総数 **3,792 件**
（成功 3,679・失敗 0・エラー 0・スキップ 113）。内訳は
protocol 2,936、bedrock 405、core 381、adapter 67、content 3。
スキップはこのツリーに jar が無いバージョンの統合セルと
Bedrock UDP のエンドツーエンド（従来どおり）。
集計方法は従来どおり（フルクリーンビルドは行わない）。
- 実バニラ統合（`split-verification-401/run-integration-401.sh`）：
新規 3 セル（fake401→実1.13.1、fake401→実1.13.2、fake404→実1.13.1）と
回帰 32 セル（404・477・480・485・490・498・573・575・578・758 系）の
**計 35 セルすべて成功**（スキップ 0）。ProxyIntegrationTest
全138メソッドの分割実行も失敗 0（実行できたものは成功、
jar が無いものはスキップ）。
- 検証中の環境事象：分割検証の途中で VM 再起動が2回あり、
run-all を再開して完走した（再開可能な設計のため欠落なし）。
pit の最終バッチは抽出正規表現がフェイククライアントの補助メソッド
3件（テストではない）を拾ったための「No tests found」で、
実テストは全件実行済み。
- **本物のゲームクライアントでの確認は未実施**（従来どおり）。

## 残る制限

- 404 から引き継ぐ制限はすべてそのまま（PLAY_BED を落とす、
Trade List 等が無い、チャンク光の近似、メタデータ型 16〜18 と
効果 ID 30／31 を落とす等。docs/PROXY-1.13.2.md 参照）。
- スロットの NBT は検証せずバイト列のまま写す。旧形式の count は
i8 のため、現行形式から来た大きな count はそのまま i8 で書く
（現行形式でも count は i8 なので実質同一）。
- i16 範囲外のアイテムID（404 のレジストリには存在しない値）は
石に代替される。
- Bedrock→401 は単体テストのみ。二人プレイの見え方はフェイク
クライアントでは確認できない（実機プレイテスト項目）。
