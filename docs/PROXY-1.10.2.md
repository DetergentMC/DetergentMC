# 1.10.2クライアント／サーバー（プロトコル210）の対応

DetergentMC に **210（Minecraft 1.10／1.10.1／1.10.2）** を追加した記録。
隣接バージョンは 315（1.11）で、このツリーでは 315 の1段下として配線した。
315 との差分は**パケットID差分 0 件・形式差分 4 件**で、レジストリ差分は
ブロック 17 種・アイテム 19 件・エンティティの新規 4 種＋亜種の独立ID化
9 種。変換経路は 315 の経路を写したものを基本とし、形式差分 4 件と
レジストリ差分を境界（210 の変換表と 210 専用の境界処理）で実装した。

> **状態（2026-11-19時点）：Stage E 完了。**
> 分割検証 4,182 件・失敗 0・エラー 0（スキップ 39 件は環境条件付き
> 自己スキップ）。実バニラ統合テストは新規 3 セル＋回帰 53 セルの
> 計 56 セルすべてが成功した（スキップ 0）。**本物のゲーム
> クライアントでの確認は未実施**（全バージョン共通）。

## 番号対応（重要）

- **プロトコル 210 ＝ Minecraft 1.10／1.10.1／1.10.2 の3リリース共通**。
  3版とも Status 応答のプロトコルは 210 で、サーバー jar のファイル
  一覧（6,763 エントリ）と assets の CRC が完全一致する（先行調査で
  3 jar すべてを実測）。内部サーバーは代表として **1.10.2** を使う。
- **201 という製品版プロトコルは存在しない**（1.9.4 の次は 210）。
  降下順は 315 → 210 → 110（1.9.4）。

## 確定した事実（すべて実測）

- **バニラ 1.10.2 サーバーjar**：piston-meta から取得。
  SHA-1 `3d501b23df53c548254f5e3f66492d178a48db63`（piston-meta の
  downloads.server.sha1 と一致、照合済み）、9,459,897 バイト。
  1.10.1 は `cb4c6f9f51a845b09a8861cdbe0eea3ff6996dee`、
  1.10 は `a96617ffdf5dabbb718ab11a9a68e50545fc5bee`。
  クライアント jar（1.10.2）の SHA-1 は
  `dc8e75ac7274ff6af462b0dcec43c307de668e40`。
  **jar ルートに version.json が無く、pack.mcmeta も data/ も無い世代**
  （315／316／335 と同じ型）。プロトコルは **210**（実バニラを起動し
  Status 応答で確認）、pack_format は **2**（**この世代に
  データパックは存在しない**）、world_version は **512**
  （1.10.1 は 511、1.10 は 510。起動したワールドの level.dat の
  DataVersion で確認）、内部サーバーは **Java 8 世代**。
  data-generator は無い。
- **ワイヤ採取（実バニラ 1.10.2、Stage A）**：Login Success の UUID は
  **文字列**（315 世代と同じ形式）。Join Game は 335／393 形式
  （entityId i32／gameMode u8／dimension i32／difficulty u8／
  maxPlayers u8／levelType 文字列／reducedDebug bool）で 20/20 バイト
  消費。チャンクは 315 と同じ骨格で、末尾はバイオーム u8×256＋
  blockEntities 件数 varint（210 にも blockEntities フィールドが
  あることを実測で確認）。ログイン時に圧縮（しきい値 256）が有効。
- **Keep Alive は varint**（210⇔315 では KA 変換は不要）。実バニラで
  KA 本体を varint として読み切り、応答（サーバー行き 0x0b）が
  受け付けられ、その後のチャットがバニラのログに到達し、2 回目の
  KA サイクルも成立することを実ワイヤで確認した。

## 315 との差分（確定内容）

### パケット

- **パケット集合・ID は完全一致**（両者 118 件、ID 差分 0）。
  minecraft-data の pc/1.10 と pc/1.11 の protocol.json 全面比較
  （全 state・types セクション含む）で確認。PacketIds の 283 エントリ
  すべてで id210 == id315（`PacketIds210Test` で固定）。

### 形式差分（ちょうど 4 件）

1. **spawn_entity_living の種別**：210 は **u8**、315 は varint。
   境界で読み書き幅を付け替える（210 のモブ ID は全て 255 以下）。
2. **title の action 番号**：210 は 0=title／1=subtitle／**2=times**／
   3=clear／4=reset。315 は actionbar（2）が新設され times が 3 へ。
   758 側の PLAY_ACTION_BAR は 210 では **PLAY_CHAT の position=2**
   として送り、逆向きは position=2 のチャットを PLAY_ACTION_BAR へ
   戻す。
3. **collect**：315 で末尾に **pickupItemCount (varint)** が追加。
   210→新方向では 1 を補い、新→210 では落とす。
4. **block_place のカーソル**：210 は **i8×3（0–15 の格子）**、315 は
   f32×3（0.0–1.0）。換算は f32→i8 が clamp(round(f×15), 0, 15)、
   i8→f32 が i8/15.0。

### レジストリ

実 jar からの再抽出（`detergentmc-protocol/tools/extract-210-registries.py`、
javap による登録箇所の解析）で ground truth を作った。**ローカルの
minecraft-data の pc/1.10 の blocks.json は古く 213–217 の5種が欠落し、
entities.json も 1.9 のコピーのままで polar_bear 等が欠落している**
ため、そのままでは使えない（items.json は影響なし）。

- **ブロック 219 種（210）⇔ 236 種（315）**。315 の純追加は
  observer（218）とシュルカーボックス 16 種（219–234）の 17 種のみ。
  共有ブロックの数値 ID 差分は 0。ブロック状態は (ID<<4)|メタデータ
  の 4,096 通りが上限（210 側）。
- **アイテム 371 種（210）⇔ 390 種（315）**。追加はシュルカーボックス
  16 種のアイテム形＋observer＋shulker_shell（450）＋totem（449）。
  数値 ID 空間は 316 と同じ旧世代の形（最大 2267、レコード）。
- **エンティティ**：1.11 の新規モブは evocation_illager（34）、
  vex（35）、vindication_illager（36）、llama（103）。新規オブジェクトは
  evocation_fangs（79）、llama_spit（68）。亜種の独立 ID 化は 9 種
  （elder_guardian 4、wither_skeleton 5、stray 6、husk 23、
  zombie_villager 27、skeleton_horse 28、zombie_horse 29、
  donkey 31、mule 32）で、210 では基本種＋メタデータ亜種だった。
  210 の登録名は旧 PascalCase（PigZombie 等）で、抽出で正規名へ
  対応付けた。共有名の数値 ID は 316 と全件一致を検証済み。
- **音 463 件（210）**（315 は 493 件。1.11 の追加 31 件は汎用
  フォールバックへ）。210 のみの `entity.experience_orb.touch` は
  新側の `entity.experience_orb.pickup` へ改名対応。
- **パーティクル 47 件**：抽出結果が minecraft-data pc/1.10 の
  particles.json と完全一致。共有パーティクルの数値 ID は 316 と
  同一のため 316 の表を土台に合成した。
- **バイオーム 62 件・効果 27 件**：1.11 と完全一致（名前→ID）。
- **ブロックエンティティ 23 件**：登録順は 316 とほぼ同じだが
  210 には note_block と flower_pot があり（316 の名前一覧には
  無い既存の欠落）、trapped_chest（chest と共用）と shulker_box は
  無い。NBT の id は名前空間なしの旧名（例：`Furnace`）で書く。

## 近似方針（210 に無いもの）

ViaVersion と同じ「落とすか近いもので代用する」方針。根拠つきで
列挙する。

- **シュルカーボックス（ブロック 219–234）→ チェスト（54）**。
  向きメタ 0／1 は北向き（2）に寄せ、それ以外はメタを保持。色は失う。
  アイテム形もチェスト（54）。
  - **無色シュルカーボックスの 316 側の欠落を 210 側で是正**：
    316 の表は 758 の無色 shulker_box を（旧世代では
    purple_shulker_box＝229 と同一物のため名前照合に失敗し）
    石へフォールバックしていた。210 の表では無色分を明示的に
    チェスト（54<<4|2）へ寄せた。316 側の表自体の修正は別途。
  - なお 316 のブロック状態表はシュルカーボックス全色の向きを
    758 側の単一状態へ潰しているため、210→新方向では向きが
    復元できない（316 から継承する制限）。
- **observer（ブロック・アイテム 218）→ ドロッパー（158）**。
  メタデータのレイアウト（向き＋powered）が同一のためメタは保持。
- **totem（アイテム 449）→ 金リンゴ（322）**。
  **shulker_shell（450）→ プリズマリンシャード（409）**。
  **iron_nugget（452）→ 金塊（371）**（210 は 1.11.1 より前のため
  315 と同じ扱い）。
- **1.11 の新規モブ（evocation_illager／vex／vindication_illager／
  llama）→ ブタ（90）**（316 世代の parrot／illusion_illager と
  同じ方針）。新規オブジェクト（evocation_fangs／llama_spit）は
  **落とす**。
- **亜種の畳み込み（315→210）**：husk→ゾンビ（54）、stray→
  スケルトン（51）、wither_skeleton→スケルトン（51）、
  elder_guardian→ガーディアン（68）、zombie_villager→ゾンビ（54）、
  skeleton_horse／zombie_horse／donkey／mule→ウマ（100）。
  210 のワイヤでは亜種の区別は失われる（メタデータでの亜種表現は
  行わない）。

## 実装の形

- 変換経路は 315 の経路を写したもの（ChunkTranslator／SlotTranslator
  の 210 節、PlayTranslator の JoinGame210、ProxySession の 210 系
  メソッド群）。スロット形式は 315 と完全同一（itemId i16＋count i8＋
  itemDamage i16＋NBT）。メタデータ型は i8 で型 0–12 のみ（型 13 の
  NBT は 1.12 の追加のため、新→210 では型 13–16 を落とす。316 から
  継承）。
- リソースパックは **pack_format 2**。レイアウトは 1.13 より前の
  旧形式（textures/items/）で、315（format 3）と形式番号だけが違う。
  フォールバック順は **210 → 315 → 316 → 335 → …**。外部設定キーは
  `resource-pack.url-210`／`resource-pack.sha1-210`。
- アダプタモジュールは `detergentmc-adapter-v1_10_2`（代表バージョン
  1.10.2）。内部サーバーは 1.10／1.10.1／1.10.2 のいずれの指定でも
  プロトコル 210 として扱い、Java 8 で起動する。
- Bedrock→210 の生物スポーンは専用の parseSpawnEntity210／
  parseSpawnEntityLiving210 から 315 のパースへ委譲する
  （種別番号が違う 498 の関数には流さない）。living の種別は
  210 では u8、315 では varint だが、210 のモブ ID は全て 128 未満で
  varint でも 1 バイトのため読みは一致する。

## 残る制限

- **Stage E 完了**：ProxyIntegrationTest の統合セルは新規 3 件
  （fake210→実バニラ 1.10.2、fake210→実バニラ 1.11、
  fake315→実バニラ 1.10.2）＋回帰 53 件（315 の Stage E の選定を
  すべて含む）の計 56 件すべてが成功した。新規 3 セルとも参加→
  チャット→Keep Alive（0x1f、varint、応答 0x0b）→応答後の
  2 通目のチャットがバニラログへ到達するまで実ワイヤで確認した。
  回帰のうち fake335→実バニラ 1.13 と fake393→実バニラ 1.13 は
  初回のみ負荷時のタイミング（Keep Alive 応答後のチャット到達と
  Join Game 到達の制限時間超過）で失敗し、再実行で合格した
  （315／316 の Stage E でも同セルに同じ事象）。
- **本物のゲームクライアントでの確認は未実施**（全バージョン共通）。
- データパックが無い世代のため trigger ブリッジ（言語切替・hello 等）
  は 210 バックエンドでは使えない（315／316 から引き継ぐ既知の
  課題、代替は未定）。
- 近似の損失：シュルカーボックスの色と向き（一部）、observer の
  機能（ドロッパー表示）、亜種の区別、1.11 新規モブの姿（ブタ代用）。
- Declare Recipes／Statistics／Tags／World Event ID は全方向で
  未実装（落とす）のまま（全バージョン共通）。
