# 1.9.4クライアント／サーバー（プロトコル110）の対応

DetergentMC に **110（Minecraft 1.9.4／1.9.3）** を追加した記録。
隣接バージョンは 210（1.10.2）で、このツリーでは 210 の1段下として
配線した。210 との差分は**パケットID差分 0 件・形式差分ちょうど
3 件**で、レジストリ差分は 1.10 で追加されたブロック 5 種・
アイテム 6 件・polar_bear・新規サウンド 19 件（名前照合）・
fallingdust。変換経路は 210 の経路を写したものを基本とし、形式
差分 3 件とレジストリ差分を境界（110 の変換表と 110 専用の境界
処理）で実装した。

> **状態（2026-11-19時点）：Stage E 完了。**
> 分割検証の件数は末尾の「検証」節を参照。ProxyIntegrationTest への
> 統合セル追加と実バニラ統合テストは実施済み（新規3セル＋回帰
> 56セルの全59セル合格）。**本物のゲームクライアントでの確認は
> 未実施**（全バージョン共通）。

## 番号対応（重要）

- **プロトコル 110 ＝ Minecraft 1.9.4 と 1.9.3 の2リリース共通**
  （先行調査で両 jar を実測し、Status 応答のプロトコルが両方とも
  110 であることを確認。world_version は 1.9.4 が **184**、
  1.9.3 が **183**）。内部サーバーは代表として **1.9.4** を使う。
- 降下順は 210 → 110 → 109（1.9.2）。109 とは実差分がある
  （update_sign の削除等）ため、110 の次は単純な委譲にはならない。

## 確定した事実（すべて実測）

- **バニラ 1.9.4 サーバーjar**：piston-meta から取得。
  SHA-1 `edbb7b1758af33d365bf835eb9d13de005b1e274`
  （piston-meta の downloads.server.sha1 と一致、照合済み）、
  9,399,053 バイト。1.9.3 は
  `8e897b6b6d784f745332644f4d104f7a6e737ccf`。
  **jar ルートに version.json が無く、pack.mcmeta も data/ も無い
  世代**（210／315 と同じ型）。プロトコルは **110**（実バニラを
  起動し Status 応答で確認）、pack_format は **2**（**この世代に
  データパックは存在しない**）、world_version は **184**
  （起動したワールドの level.dat の DataVersion で確認）、
  内部サーバーは **Java 8 世代**。data-generator は無い。
- **ワイヤ採取（実バニラ 1.9.4、Stage A で自前採取）**：ログイン
  時に圧縮（しきい値 256）が有効。Join Game の dimension は
  **i32** で 20/20 バイト消費（107 の i8 とは違う。109⇔110 の
  差分側の話）。チャンクは 210 と同じ骨格で、末尾はバイオーム
  u8×256＋**blockEntities フィールドあり**（210 と同一。
  blockEntities が付いたのは 110 からで、109 には無い）。
- **Keep Alive は varint**（110⇔210 では KA 変換は不要）。
  実バニラで KA 本体を varint として読み切り、応答（サーバー行き
  0x0b）が受け付けられ、その後のチャットがバニラのログに到達
  することを実ワイヤで確認した。210 以前と同じく、340 以上との
  境界では既存の KA 付け替え（keepAliveTo338Wire 系、110 専用の
  復元表つき）が働く。
- **structure_block のアイテム形は 1.9.4 に存在しない**ことを実測で
  確定した。1.9.4 のクライアント jar にはアイテムモデルファイルが
  あるためファイル有無では決められず、実サーバーで
  `/give <player> structure_block` を実行すると
  「There is no such item with name minecraft:structure_block」に
  なる（対照実験：barrier は成功、magma は同じく存在しない）。

## 210 との差分（確定内容）

### パケット

- **パケット集合・ID は完全一致**（両者 118 件、ID 差分 0）。
  minecraft-data の pc/1.9.4 と pc/1.10 の protocol.json 全面比較
  （全 state・types セクション含む）で確認。PacketIds の 283
  エントリすべてで id110 == id210（`PacketIds110Test` で固定。
  110 に存在するのは 113 件）。

### 形式差分（ちょうど 3 件）

1. **sound_effect の pitch**：110 は **u8**、210 は f32。
   換算は ViaVersion と同じ流儀で、f32→u8 が
   clamp(round(f32 × 63.5), 0, 255)、u8→f32 が u8 / 63.5。
   量子化のため往復は不可逆（誤差は最大で約 0.008）。
2. **named_sound_effect の pitch**：同上（u8 ⇔ f32、同じ換算）。
3. **resource_pack_receive（サーバー行き）**：110 では先頭に
   **hash（string）** があり、210 では result (varint) のみ。
   758 形式→110 では空文字列の hash を先頭に補い、110→758 形式
   では hash を読み捨てる。**実バニラ 1.9.4 が空 hash を受け付ける
   ことは Stage E で実測確認済み**（fake210→実バニラ 1.9.4 の
   統合セルで、210 形式の応答を送った後に Keep Alive の往復と
   2通目のチャット到達・接続維持を確認。バニラは切断しなかった）。

これ以外の形式は 210 と同一：spawn_entity_living の種別は両者
とも u8、title の action 番号も同一（actionbar は 315 で新設
されたため、この世代では PLAY_CHAT の position=2 で代替する
210 の扱いをそのまま使う）、collect に pickupItemCount は
両者とも無い、block_place のカーソルは両者とも i8、スロット
形式・メタデータ型（i8、型 0–12）も同一。

### レジストリ

実 jar からの再抽出（`detergentmc-protocol/tools/extract-110-registries.py`、
javap による登録箇所の解析）で ground truth を作り、210 側は
210 実装が実 jar から作った抽出結果と比較した（ローカルの
minecraft-data の pc/1.10 は blocks.json／entities.json が古い
ため使えない。pc/1.9 側は健全で、実 jar の lang と突き合わせ
済み）。**共有要素の数値 ID 差分はすべて 0** で、差分は 1.10 の
追加分だけだった。

- **ブロック 214 種（110）⇔ 219 種（210）**。210 の純追加は
  magma（213）、nether_wart_block（214）、red_nether_brick（215）、
  bone_block（216）、structure_void（217）の 5 種のみ。
- **アイテム 365 種（110）⇔ 371 種（210）**。差分は数値 ID で
  {213, 214, 215, 216, 217, 255} の 6 件（ブロック 5 種のアイテム
  形＋structure_block のアイテム形 255。structure_block の
  アイテム形は 1.10 で追加された。ブロック自体は 1.9 にもある）。
- **エンティティ 60 登録（110）⇔ 210**。差分は polar_bear（102）
  の追加のみ。
- **音 444 件（110）⇔ 463 件（210）**。登録順が違うため数値 ID は
  そのままでは対応せず、**名前で照合**して合成した。1.10 の追加
  19 件（block.enchantment_table.use、husk 4 件、polar_bear 6 件、
  stray 4 件、wither_skeleton 4 件）は 110 には無い。
- **パーティクル 46 件（110）⇔ 47 件（210）**。差分は
  fallingdust（46）の追加のみ。
- **ブロックエンティティ 23 件・バイオーム 62 件・効果 27 件**：
  210 と完全一致（名前・順序とも）。

## 近似方針（110 に無いもの）

ViaVersion と同じ「落とすか近いもので代用する」方針。根拠つきで
列挙する。いずれも新→110 方向の近似で、110→新方向では 110 の
要素はそのまま対応する要素へ戻る。

- **magma（213）→ netherrack（87）**。どちらもネザーの地形
  ブロックで、見た目が最も近い素朴な候補。
- **nether_wart_block（214）→ nether_brick（112）**、
  **red_nether_brick（215）→ nether_brick（112）**。同系の
  ネザーレンガ系で代用する（色の違いは失われる）。
- **bone_block（216）→ quartz_block（155）**。白系の塊ブロック
  として最も近い。メタデータ（軸方向）は保持する。
- **structure_void（217）→ barrier（166）**。どちらも不可視の
  構造用ブロックで、役割が最も近い。
- 上記ブロックの**アイテム形**も同じ対応で寄せる。
  **structure_block のアイテム形（255）→ barrier のアイテム形
  （166）**（アイテム形自体が 1.9.4 に存在しないため。ブロック
  設置済みの structure_block はワイヤ上はブロック ID 255 として
  そのまま扱える）。
- **polar_bear → ブタ（90）**（この世代の新規モブの常套代用。
  316 世代の parrot／illusion_illager、210 の 1.11 新規モブと
  同じ方針）。
- **fallingdust → explode（0）**。落下系の表現に近い既存
  パーティクルが無いため、無難な既定値へ寄せる。
- **1.10 で追加された音 19 件 → 汎用音**（block.stone.break の
  110 側インデックス 364）。音名フォールバック表（758 名前空間
  との名前照合）経由で解決できない場合の既定値。

## 実装の形

- 変換経路は 210 の経路を写したもの（ChunkTranslator／
  SlotTranslator の 110 節、PlayTranslator の JoinGame110、
  ProxySession の 110 系メソッド群）。形式差分は音の pitch
  換算（ChunkTranslator の translateSoundEffect110／
  translateNamedSoundEffect110）と resource_pack_receive の
  hash 付け外し（ProxySession の stripResourcePackHash110／
  prependResourcePackHash110）だけを境界で実装した。
- リソースパックは **pack_format 2**。レイアウトは 1.13 より
  前の旧形式（textures/items/）で、210 と形式番号も同じ。
  フォールバック順は **110 → 210 → 315 → 316 → 335 → …**。
  外部設定キーは `resource-pack.url-110`／
  `resource-pack.sha1-110`。
- アダプタモジュールは `detergentmc-adapter-v1_9_4`（代表
  バージョン 1.9.4）。内部サーバーは 1.9.4／1.9.3 のいずれの
  指定でもプロトコル 110 として扱い、Java 8 で起動する。
- Bedrock→110 の生物スポーンは専用の parseSpawnEntity110／
  parseSpawnEntityLiving110 から **210 のパースへ委譲**する
  （110 の種別表は 210 から polar_bear を除いたもので、最も
  近い。種別番号が違う 498 の関数には流さない）。

## 検証

- 分割検証 `split-verification-110/`（重複排除後の集計）：
  **4,266 件・失敗 0・エラー 0**（合格 4,227 件。内訳：
  protocol 3,439 件、bedrock 447 件、core 310 件（うち統合セル
  59 件）、adapters＋content 70 件。スキップ 39 件は環境条件付き
  自己スキップで、210 の分割検証と同じ内訳）。
  最古プロトコル脱落の罠は `ClientAdapter110Test` で固定した
  （一覧の先頭が 110、210・315・316 が残り、計 37 プロトコル。
  Stage E で XML を消して再実行し、新鮮な結果で合格を確認）。
- **Stage E（実バニラ統合テスト）完了**：ProxyIntegrationTest の
  統合セルは新規 3 セル（fake110→実バニラ 1.9.4、fake110→
  実バニラ 1.10.2、fake210→実バニラ 1.9.4）＋回帰 56 セル
  （210 の Stage E の選定をすべて含む）の**全 59 セルが合格**
  （スキップ 0。各セルの XML をメソッド名と照合済み、欠け無し）。
  新規 3 セルとも、参加→チャット→Keep Alive（0x1f、varint、
  応答 0x0b）→応答後の 2 通目のチャットがバニラログに到達する
  までを実ワイヤで確認した。fake210→実バニラ 1.9.4 では
  あわせて Resource Pack Response（210 形式）を送り、プロキシが
  補う空 hash を実バニラ 1.9.4 が受け付けることも確認した
  （応答後も接続が維持され、2 通目のチャットが到達）。
  回帰セル 1 件（fake335→実バニラ 1.13）が初回のみ Keep Alive
  応答後のチャット到達の制限時間超過で失敗し、再実行で合格した
  （210／315／316 の Stage E でも同セルに同じ事象。負荷時の
  タイミング由来と判断）。
- Stage A では実バニラ 1.9.4 を起動して Status／ログイン／
  チャンク／Keep Alive／チャット到達を自前採取で確認し、
  `/give` の対照実験で structure_block のアイテム形の不存在を
  確定した。

## 残る制限

- **本物のゲームクライアントでの確認は未実施**（全バージョン
  共通）。形式差分 3 件の見え方（音のピッチ感、リソースパック
  応答の流れ）、近似の見え方、Keep Alive の長時間セッションでの
  安定性、2 人同時参加時の相互表示が実機確認の重点
  （docs/PLAYTEST-1.9.4.md）。
- データパックが無い世代のため trigger ブリッジ（言語切替・
  hello 等）は 110 バックエンドでは使えない（210／315／316
  から引き継ぐ既知の課題、代替は未定）。
- 近似の損失：1.10 追加ブロック 5 種の見た目（上記の代用）、
  polar_bear の姿（ブタ代用）、fallingdust の表現、1.10 追加音
  19 件（汎用音）、音 pitch の量子化誤差。
- Declare Recipes／Statistics／Tags／World Event ID は全方向で
  未実装（落とす）のまま（全バージョン共通）。
