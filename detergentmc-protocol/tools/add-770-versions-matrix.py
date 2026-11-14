#!/usr/bin/env python3
"""docs/VERSIONS.md に 1.21.5（770）の 8x8=64通りマトリクス節を追加する。

1.21.4のマトリクス表（49セル）をそのまま流用し、各行に770列のセルを
追加し、770行を新設する。既存の49セルは一字一句変えない。
"""
import re
from pathlib import Path

ROOT = Path("/home/hatch/workspace/detergentmc")
P = ROOT / "docs" / "VERSIONS.md"
text = P.read_text(encoding="utf-8")

# 1.21.4マトリクスの表を抜き出す
m = re.search(r"(## 1\.21\.4対応：全49通りの対応マトリクス.*?)\n(### 768→769のレジストリ差分)",
              text, re.DOTALL)
assert m, "1.21.4 matrix section not found"
matrix_sec = m.group(1)

# 表の行を抜き出す（ヘッダ行と区切り行と7行のデータ行）
lines = matrix_sec.split("\n")
hdr_i = next(i for i, l in enumerate(lines) if l.startswith("| クライアント＼サーバー |"))
sep_i = hdr_i + 1
hdr = lines[hdr_i]
sep = lines[sep_i]
rows = []
i = sep_i + 1
while i < len(lines) and lines[i].startswith("| **"):
    rows.append(lines[i])
    i += 1
assert len(rows) == 7, f"expected 7 data rows, got {len(rows)}"

# 770列のセル（行ごと）
COL_770 = {
    763: "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession770Test.join763Against770ConsumesConfigAs770Client`（参加フロー単体テスト）で確認。プロキシが770の Registry Data などを消費して 763 の Join Game に codec を合成。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす。Entity Metadataのvariant系を落とす |",
    764: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす |",
    765: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす |",
    766: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序を並べ替え。Window Click を落とす（HashedSlot）。Set Creative Slot は766⇔770で変換できる。Declare Recipes／Tagsを落とす |",
    767: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は767⇔770で変換できる。Declare Recipes／Tagsを落とす |",
    768: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入）。Window Click を落とす（HashedSlot）。Set Creative Slot は768⇔770で変換できる。Declare Recipes／Tagsを落とす |",
    769: "| ◎ 制限付き（統合テスト済み）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は769⇔770で変換できる。Declare Recipes／Tagsを落とす |",
}

# クライアント番号を行から取り出す
def client_of(row):
    mm = re.match(r"\| \*\*(\d+)", row)
    assert mm, row[:60]
    return int(mm.group(1))

new_hdr = hdr + " 770（1.21.5） |"
new_sep = sep + "---|"
new_rows = []
for row in rows:
    new_rows.append(row + " " + COL_770[client_of(row)])

ROW_770 = (
    "| **770（1.21.5）** "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession770Test.join770Against763SynthesizesConfig`（参加フロー単体テスト）で確認。プロキシが Join Game の codec から設定フェーズを合成。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession770Test.join770Against765RelaysJoinLevel`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の形式と順序を変換。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序を並べ替え。Window Click を落とす（HashedSlot）。Set Creative Slot は770⇔766で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は770⇔767で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は770⇔768で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 制限付き（統合テスト済み）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は770⇔769で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 素通し（リレー・統合テスト済み。1.21.5で確認） |"
)
new_rows.append(ROW_770)

table = "\n".join([new_hdr, new_sep] + new_rows)

SECTION = """## 1.21.5対応：全64通りの対応マトリクス（2026-11-11）

クライアント763／764／765／766／767／768／769／770 × サーバー（内部プロトコル）763／764／
765／766／767／768／769／770の全64通りが参加できる。1.21.4対応の49通りに、770クライアントと
770サーバーを加えたものである。見出し事例は「1.21.5のサーバー（内部770）に
1.20系・1.21クライアントが入る」「1.20系・1.21サーバーに1.21.5クライアントが
入る」こと。

凡例は1.20系のマトリクスと同じ（◎＝統合テスト済み、○＝素通しで統合テストなし、
△＝制限付き、×＝できない。現在×は無い）。

""" + table + """

検証の区別（まとめ）：

- **統合テスト済み（44件）**：1.21.4対応の41件（上を参照）に加え、
  770関係の3件（770→1215、770→1214、769→1215。
  `ProxyIntegrationTest` の3テスト。いずれも
  参加・スポーン・チャット・移動まで確認）。770→1215ではチャット checksum
  0x00 が実バニラ1.21.5に受け入れられることも確認済み（769クライアント→
  770サーバーのチャット送信）。
- **単体テスト済み・統合テストなし**：770→763、770→765、763→770は
  `ProxySession770Test` の参加フロー単体テストで確認。770↔764／766／767／768と
  763／764／765／766／767／768→770は、`ClientAdapter` の全組み合わせ判定テストと
  翻訳子（`ChunkTranslator770Test`／`PlayTranslator770Test`）の単体テストで確認。
- **素通し（リレー）・統合テストなし**：763→763、764→764、766→766。
  リレーはバイト列をそのまま通すため変換の誤りが入りようが無く、765→765と
  767→767と768→768と769→769と770→770の統合テストで経路自体は検証済み。
- **Bedrock→770**：Bedrock 6 プロトコル（589／594／630／671／685／2193）×
  内部770（1.21.5）の6通りを `BedrockMatrixIntegrationTest.matrixAgainst1_21_5` で
  統合テストする（参加・スポーン・チャット・移動・チャンク・アイテム）。
  内部763／764／765／766／767／768／769の42通りと合わせて48通り。
- **実機でしか確認できないもの（全64通り共通）**：本物のゲームクライアントでの
  参加・見た目・操作感、本物のMojang認証サーバーでのonline-mode参加、
  リソースパックの「互換性あり」表示と適用、泡テクスチャの目での確認。
  手順書は `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／
  `PLAYTEST-1.20.5.md`／`PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／
  `PLAYTEST-1.21.4.md`／`PLAYTEST-1.21.5.md` を参照。

### 769→770のレジストリ差分（2026-11-11、ViaVersion 対応表と本物の Registry Data で確認）

- **ブロック状態**：27,866→27,914件（+60、−12）。`creaking_heart` のプロパティが
  `[active=bool,axis,natural=bool]`（12状態）から
  `[axis,creaking_heart_state=uprooted/dormant/awake,natural=bool]`（18状態）に変わった。
  bush・short_dry_grass・tall_dry_grass・cactus_flower・test_block・
  test_instance_block・wildflowers・leaf_litter・firefly_bush の追加。
- **アイテム**：1,385→1,396件（+11、削除0）。blue_egg・brown_egg・bush・
  firefly_bush・short_dry_grass・tall_dry_grass・wildflowers・leaf_litter・
  cactus_flower・test_block・test_instance_block の追加。
- **エンティティ**：149→150件（+1、削除0）。`potion` が `splash_potion` 99 と
  `lingering_potion` 100 に分裂。
- **音**：1,651→1,702件（+52、−1）。`entity.wolf.howl` の削除
  （`entity.wolf.ambient` にフォールバック）。追加はオオカミの鳴き声バリエーション
  36件、鉄5件、leaf_litter 5件、cactus_flower 2件、砂2件、
  deadbush／firefly_bush 2件。
- **パーティクル**：112→114件。`tinted_leaves` が 35 に挿入（i32データ付き）、
  `firefly` が 113 に追加。35番以降が1つずれる（trail は 47→48、
  block_crumble は 111→112）。
- **属性**：32件で同じ顔ぶれ・同じ順番。
- **エンチャント**：42件で同じ顔ぶれ・同じ順番。
- **データコンポーネント種別**：67→96件（+31、−2）。`hide_additional_tooltip`／
  `hide_tooltip` が `tooltip_display` に統合。unbreakable は bool から void に、
  lore は型名だけの変更（ワイヤは同じ）。enchantments 等の末尾の showTooltip
  bool が消え、tool に canDestroyBlocksInCreative が増え、instrument／
  provides_trim_material が container 化し、dyed_color が container から i32 に。
- **絵画**：50件で同じ。**旗の模様**：43件で同じ。**トリム**：18件で同じ。
- **ダメージ種別**：49件で同じ顔ぶれ・同じ順番。
- **バイオーム**：65件で同じ顔ぶれ・同じ順番。
- **ブロックエンティティ種別**：45→47件。`test_block` 45、`test_instance_block` 46 を追加。
- **ジュークボックスの歌**：19件で同じ。**メニュー**：25件で同じ。
- **パケットID**：クライアント行きは117件変更（`spawn_entity_experience_orb`
  0x02 の削除で全て1つずれる）、新規1件（`test_instance_block_status` 0x77）。
  サーバー行きは5件変更、新規2件（`set_test_block` 0x39、
  `test_instance_block_action` 0x3c）。ハンドシェイク／ログイン／設定フェーズの
  IDは変わらない。
- **チャンク**：heightmap が NBT から直接配列に変更（`{type varint, data long[]}`
  の配列）。type id の順序はバニラの `Heightmap.Types` の列挙順
  （0=WORLD_SURFACE_WG、1=WORLD_SURFACE、2=OCEAN_FLOOR_WG、3=OCEAN_FLOOR、
  4=MOTION_BLOCKING、5=MOTION_BLOCKING_NO_LEAVES）と仮定している（実バニラの
  チャンクでの確認はできていない）。chunkData は varint 長さ＋バイト列で同じ形式。
- **チャット**：サーバー行きの Chat Message／Chat Command Signed の末尾に
  `checksum` u8 が増えた。クライアント行きの Player Chat の先頭に
  `globalIndex` varint が増えた（プロキシがセッションごとのカウンタで書き足す）。
- 遠い組み合わせ（763／764／765／766／767／768↔770）は、769を経由する合成表で作る
  （`detergentmc-protocol/tools/generate-remap-tables.py --target 770`）。
  実行時は合成済みの1つの表で直接引く二段変換ではない（1.20系の相互連携と
  同じ方針）。逆向きは `RegistryRemapper.invert()` で反転して使い回す。

### まだできないこと（1.21.5対応・正直な一覧）

- **Window Click（770が関わる全方向）**：770の HashedSlot は成分のハッシュ値
  （i32）だけを持ち、元の Slot に戻せない。ハッシュの算法が未確認のため全方向で
  落とす。770クライアントが内部770以外のサーバーにいるとき、770サーバーにいる
  769以下のクライアントは、インベントリ操作（クリック）ができない。Set Creative
  Slot は UntrustedSlot→Slot で変換できる（770⇔769／768／767／766）。
- **Set Creative Slot（770クライアント→内部763／764／765）**：770の UntrustedSlot は
  データコンポーネント形式のため変換できず落とす（769と同じ制限）。
- **Test Instance Block（770）**：`set_test_block`／`test_instance_block_action`／
  `test_instance_block_status` は770にしか無いため落とす。テストブロックは
  1.21.5の実験的機能。
- **Pick Item（770↔769）**：770の pick_item_from_block／pick_item_from_entity は
  769と同じ形式のため素通しする。旧バージョン（763／764／765／766／767／768）向けは、
  769と同じく位置・エンティティ指定とスロット番号指定の違いのため落とす。
- **Player Loaded（770）**：770クライアントが送る player_loaded（0x2A、空）は
  古いサーバーには送らない。統合テストでは参加・スポーン・チャット・移動が
  player_loaded 無しで動いたが、770サーバーが player_loaded を前提に
  処理を進めるかどうかは未確認。
- **チャット checksum（769→770）**：0x00 を書き足す。統合テスト
  （`fake769ClientJoinsRealVanilla1215ThroughProxy`）で実バニラ1.21.5に
  受け入れられることを確認済み。checksum の本当の算法は未確認のため、
  署名付きチャットや online-mode での挙動は別途確認が必要。
- **Player Chat の globalIndex（769→770）**：プロキシのセッションごとの
  カウンタ（0から）で書き足す。実バニラが globalIndex の連番を厳密に
  検証するかは未確認。`message_acknowledgement` が770で globalIndex
  ベースに変わっているかも未確認（770クライアントのチャット履歴の
  既読確認がずれる可能性がある）。
- **Heightmap の type id 順序（769↔770）**：バニラの `Heightmap.Types` の列挙順と
  仮定している。実バニラのチャンクで確認はできていない（偽クライアントは
  チャンクを復号しないため、統合テストでは検証されていない）。
- **creaking_heart の uprooted（769↔770）**：769の active=true→770の awake、
  active=false→dormant。770の uprooted→769の active=false（dormant と区別しない）。
  uprooted の状態は769では再現できない（ベストエフォート）。
- **ポーションの分裂（769↔770）**：`potion` 99 が `splash_potion` 99 と
  `lingering_potion` 100 に分裂。769→770では両方とも splash_potion 99 にする。
  770の lingering_potion 100→769の potion 99。区別が失われる。
- **スポーンエンティティの経験値オーブ（769↔770）**：770の spawn_entity
  （type=47 experience_orb）の data varint を XP 値と仮定して 769の
  spawn_entity_experience_orb の count に直す。実バニラでの確認はできていない。
- **オオカミの鳴き声バリエーション・寒冷／温暖変種（770→769）**：wolf_sound_variant・
  cow_variant・pig_variant・chicken_variant は770にしか無いため、メタデータの
  エントリごと落とす。色バリエーションの wolf_variant は769と770で同じ9色のため
  番号はそのまま通す。
- **Player Info の showHat／listPriority の順番（769→768）**：769のときと同じ仮定
  （minecraft-data の順番）。
- **ボートの木の種類**：768と同じ制限。767のボートは769／770では常にオークのボートに、
  769／770のボートは767では常に（木の種類を問わず）ボートになる。
  variant（木の種類）をエンティティ種別に直す変換は未実装。
- **food の効果（767→768→769→770）**：金リンゴ等の食べたときの効果は768／769／770向けに
  落とす。767の food の効果は amplifier／duration を持たないため、768／769／770の
  `consumable` の apply_effects 形式へは変換できない。
- **custom_model_data（767→768→769→770）**：768と同じ制限。767の単一の数値を
  768／769／770の colors[0] に入れるだけのベストエフォート。floats／flags／strings は
  空になる。
- **Set Cooldown（770のグループ名）**：768と同じく、エンダーパール・コーラスフルーツ・
  ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす
  （クールダウンの表示だけの制限）。
- **レシピパケット（770⇔769）**：Craft Recipe Response／Request、
  Displayed Recipe はレシピIDの形式が変わり変換できないため落とす。
  手動クラフトは動く。
- **Declare Recipes／Statistics／Tags／World Event ID**：全方向で未対応。
  レシピ帳・タグが空になるだけで遊べる。
- **ブロックエンティティの crafter／trial_spawner**：パケットごと落とす。
  766→765方向では vault も落とす（1.20系と同じ）。test_block／test_instance_block は
  770にしか無く、769向けには落とす。
- **Entity Metadata の中身**：763向けは variant 系エントリを落とす。
  764では素通しのためスロット・ブロック状態・粒子の中身は未変換のまま残る。
  770にしかないもの（tinted_leaves・firefly 粒子等）は古いサーバー向けに落とす。
- **Explosion の770専用パーティクル／サウンド指定**：既定演出に置き換わる
  （766→763方向と同じ）。
- **1.21.5の新要素で通せないもの（770サーバーに769以下のクライアントがいる場合）**：
  寒冷・温暖変種のモブは769以下のクライアントでは通常のモブに見える（変種エントリを
  落とす）。リーフリッター・ワイルドフラワー・茂み・ホタルブッシュ・サボテンの花・
  枯れ草は769以下のクライアントでは石に見える（ブロック状態のフォールバック）。
  新しいスポーンエッグ（blue_egg・brown_egg）は石になる。テストブロックは
  表示されない（ブロックエンティティを落とす）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  764／765／766／767／768／769／770＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **本物のゲームクライアントでの確認は未実施**：全64通り共通。手順書は
  `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／`PLAYTEST-1.20.5.md`／
  `PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／`PLAYTEST-1.21.4.md`／
  `PLAYTEST-1.21.5.md`。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの
  検証のみ。
- **1.21.6以降（771〜）は未対応**：プロキシは770より新しいプロトコルを
  ログイン時に断る。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。

"""

anchor = "## バニラとの結び方（TODO・方針）"
assert anchor in text
text = text.replace(anchor, SECTION + anchor, 1)
P.write_text(text, encoding="utf-8")
print("inserted 1.21.5 matrix section")
print("new rows added:", len(new_rows))
