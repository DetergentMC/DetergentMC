#!/usr/bin/env python3
"""docs/VERSIONS.md に 1.21.6（771）の 9x9=81通りマトリクス節を追加する。

1.21.5のマトリクス表（64セル）をそのまま流用し、各行に771列のセルを
追加し、771行を新設する。既存の64セルは一字一句変えない。
冒頭の「1.21.5対応」紹介文の直後に「1.21.6対応」の紹介文を足し、
末尾の「バージョン追加の優先順位」も更新する。
"""
import re
from pathlib import Path

ROOT = Path("/home/hatch/workspace/detergentmc")
P = ROOT / "docs" / "VERSIONS.md"
text = P.read_text(encoding="utf-8")

# 1.21.5マトリクスの表を抜き出す
m = re.search(r"(## 1\.21\.5対応：全64通りの対応マトリクス.*?)\n(### 769→770のレジストリ差分)",
              text, re.DOTALL)
assert m, "1.21.5 matrix section not found"
matrix_sec = m.group(1)

# 表の行を抜き出す（ヘッダ行と区切り行と8行のデータ行）
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
assert len(rows) == 8, f"expected 8 data rows, got {len(rows)}"

# 771列のセル（行ごと）
COL_771 = {
    763: "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession771Test.join763Against771ConsumesConfigAs771Client`（参加フロー単体テスト）で確認。プロキシが771の Registry Data などを消費して 763 の Join Game に codec を合成。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす。Entity Metadataのvariant系を落とす |",
    764: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす |",
    765: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす |",
    766: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序を並べ替え（dialog・jukebox_song を落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は766⇔771で変換できる。Declare Recipes／Tagsを落とす |",
    767: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は767⇔771で変換できる。Declare Recipes／Tagsを落とす |",
    768: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入・dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は768⇔771で変換できる。Declare Recipes／Tagsを落とす |",
    769: "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession771Test.join769Against771TranslatesSlots`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は769⇔771で変換できる。Declare Recipes／Tagsを落とす |",
    770: "| ◎ 制限付き（統合テスト済み）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は770⇔771で変換できる。Declare Recipes／Tagsを落とす |",
}

# クライアント番号を行から取り出す
def client_of(row):
    mm = re.match(r"\| \*\*(\d+)", row)
    assert mm, row[:60]
    return int(mm.group(1))

new_hdr = hdr + " 771（1.21.6） |"
new_sep = sep + "---|"
new_rows = []
for row in rows:
    new_rows.append(row + " " + COL_771[client_of(row)])

ROW_771 = (
    "| **771（1.21.6）** "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession771Test.join771Against763SynthesizesConfig`（参加フロー単体テスト）で確認。プロキシが Join Game の codec から設定フェーズを合成。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession771Test.join771Against765RelaysJoinLevel`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession771Test.join771Against766TranslatesJoinGame`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序を並べ替え（dialog・jukebox_song は落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は771⇔766で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は771⇔767で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入）。Window Click を落とす（HashedSlot）。Set Creative Slot は771⇔768で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は771⇔769で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 制限付き（統合テスト済み）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は771⇔770で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 素通し（リレー・統合テスト済み。1.21.6で確認） |"
)
new_rows.append(ROW_771)

table = "\n".join([new_hdr, new_sep] + new_rows)

SECTION = """## 1.21.6対応：全81通りの対応マトリクス（2026-11-12）

クライアント763／764／765／766／767／768／769／770／771 × サーバー（内部プロトコル）763／764／
765／766／767／768／769／770／771の全81通りが参加できる。1.21.5対応の64通りに、771クライアントと
771サーバーを加えたものである。見出し事例は「1.21.6のサーバー（内部771）に
1.20系・1.21クライアントが入る」「1.20系・1.21サーバーに1.21.6クライアントが
入る」こと。

凡例は1.20系のマトリクスと同じ（◎＝統合テスト済み、○＝素通しで統合テストなし、
△＝制限付き、×＝できない。現在×は無い）。

""" + table + """

検証の区別（まとめ）：

- **統合テスト済み（47件）**：1.21.5対応の44件（上を参照）に加え、
  771関係の3件（771→1216、771→1215、770→1216。
  `ProxyIntegrationTest` の3テスト。いずれも
  参加・スポーン・チャット・移動まで確認）。771→1216ではチャット checksum
  0x00 が実バニラ1.21.6に受け入れられることも確認済み（2026-11-12の
  生ソケットプローブでも事前に確認済み）。
- **単体テスト済み・統合テストなし**：771→763、771→766、771→765、763→771、769→771は
  `ProxySession771Test` の参加フロー単体テストで確認。771↔764／767／768と
  763／764／765／766／767／768→771は、`ClientAdapter` の全組み合わせ判定テストと
  翻訳子（`ChunkTranslator771Test`／`PlayTranslator771Test`）の単体テストで確認。
- **素通し（リレー）・統合テストなし**：763→763、764→764、766→766。
  リレーはバイト列をそのまま通すため変換の誤りが入りようが無く、765→765と
  767→767と768→768と769→769と770→770と771→771の統合テストで経路自体は検証済み。
- **Bedrock→771**：`JavaBackendConnection771Test`（単体テスト10件）に加え、
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_6`（Bedrock 6 プロトコル ×
  内部771の統合テスト。参加・スポーン・チャット・移動・チャンク・アイテム）で
  統合テスト済み（2026-11-12）。Join Game 771の形式が768と同一かの未確認分も
  ここで検証できた。
  内部763／764／765／766／767／768／769／770の48通りと合わせて54通り。
- **実機でしか確認できないもの（全81通り共通）**：本物のゲームクライアントでの
  参加・見た目・操作感、本物のMojang認証サーバーでのonline-mode参加、
  リソースパックの「互換性あり」表示と適用、泡テクスチャの目での確認。
  手順書は `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／
  `PLAYTEST-1.20.5.md`／`PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／
  `PLAYTEST-1.21.4.md`／`PLAYTEST-1.21.5.md`／`PLAYTEST-1.21.6.md` を参照。

### 770→771のレジストリ差分（2026-11-12、実バニラ Registry Data で確認）

- **ブロック状態**：27,914→27,946件（+32、−0）。`dried_ghast` のみ。
- **アイテム**：1,396→1,415件（+19、−0）。乾燥ガスト・ハッピーガストのスポーンエッグ・
  ハーネスなど。
- **エンティティ**：150→151件（+1、−0）。`happy_ghast`（771の56）が増えただけ。
  ghast は771でも55。
- **音**：1,702→1,727件（+29、−4）。**`blow_out` の衝突**：770の
  `entity.puffer_fish.ambient`（1179）と `entity.puffer_fish.blow_out`（1180）が
  両方771の `entity.puffer_fish.blow_out`（1204）へ写る。`invert()` は先行勝ちで
  ambient（1179）を選ぶため、771→770では blow_out（1204）が ambient（1179）に
  戻る（元の blow_out（1180）には戻らない）。名前レベルでも770の ambient と blow_out が
  両方771の blow_out へ写り、逆向きは ambient が選ばれる。数値表の OVERRIDES
  {816,840},{817,839} は別の音で、この衝突とは関係ない。
- **パーティクル**：114件で同じ顔ぶれ・同じ順番。
- **属性**：32→35件。`camera_distance` が771の8番に挿入、
  `waypoint_transmit_range`（33）・`waypoint_receive_range`（34）が末尾に追加された。
- **エンチャント**：42件で同じ顔ぶれ・同じ順番。
- **データコンポーネント種別**：96件で同じ顔ぶれ・同じ順番。
- **絵画**：50件で同じ。**旗の模様**：43件で同じ。**トリム**：18件で同じ。
- **ダメージ種別**：49件で同じ顔ぶれ・同じ順番。**実バニラ1.21.6のキャプチャで確認済み**
  （2026-11-12。ender_pearl 8、mace_smash 26、magic 27、wither_skull 48）。
- **バイオーム**：65件で同じ顔ぶれ・同じ順番。
- **ブロックエンティティ種別**：47件で同じ顔ぶれ・同じ順番。
- **ジュークボックスの歌**：19→20件。`minecraft:tears` が771の17番に挿入
  （wait 18、ward 19）。
- **ダイアログ**：新規 `minecraft:dialog`（3件：custom_options 0、quick_actions 1、
  server_links 2）。
- **メニュー**：25件で同じ。
- **パケットID**：クライアント行きは変わらない（Join Game 0x2B、Spawn Position 0x5A、
  Keep Alive 0x26、Ping 0x36、Start Configuration 0x6F、Disconnect 0x1C、
  Respawn 0x4B）。サーバー行きは58件変更（`change_gamemode` 0x04 の挿入で
  0x04 以降が1つずれる。Chat Message 0x07→0x08、Keep Alive 応答 0x1A→0x1B、
  Ping 応答 0x2B→0x2C、Set Player Position 0x1C→0x1D、Window Click 0x10→0x11、
  Set Creative Slot 0x36→0x37）。新規8件（設定フェーズの show_dialog 0x12／
  clear_dialog 0x11／custom_click_action_to_server 0x08、プレイの tracked_waypoint 0x83／
  show_dialog 0x85／clear_dialog 0x84／change_gamemode 0x04／
  custom_click_action_to_server 0x41）。削除2件（設定フェーズのサーバー行き
  custom_report_details 0x08／server_links 0x09）。Steer Vehicle は削除され
  Player Input（771は 0x2a）に置換された。ハンドシェイク／ログイン／
  設定フェーズのクライアント行きIDは変わらない。
- **チャンク**：heightmap は770と同じ直接配列。**セクションのパレット付き
  コンテナの Data Array から長さの varint 接頭辞が無い**のも770と同じ
  （770で廃止されたまま）。実バニラ1.21.6のチャンクで確認済み
  （2026-11-12の生ソケットプローブで、770形式のパーサでチャンク (26, -54) が
  完全に読めた）。
- **チャット**：サーバー行きの Chat Message／Chat Command Signed の末尾の
  `checksum` u8 は770・771ともある（付け外し不要）。クライアント行きの Player Chat の
  先頭の `globalIndex` varint は770・771ともある（素通し）。
- 遠い組み合わせ（763／764／765／766／767／768／769↔771）は、770を経由する合成表で作る
  （`detergentmc-protocol/tools/generate-remap-tables.py --target 771`）。
  実行時は合成済みの1つの表で直接引く二段変換ではない（1.20系の相互連携と
  同じ方針）。逆向きは `RegistryRemapper.invert()` で反転して使い回す。

### まだできないこと（1.21.6対応・正直な一覧）

- **Window Click（771が関わる全方向）**：771の HashedSlot は成分のハッシュ値
  （i32）だけを持ち、元の Slot に戻せない。ハッシュの算法が未確認のため全方向で
  落とす。771クライアントが内部771以外のサーバーにいるとき、771サーバーにいる
  770以下のクライアントは、インベントリ操作（クリック）ができない。Set Creative
  Slot は UntrustedSlot→Slot で変換できる（771⇔770／769／768／767／766）。
- **Set Creative Slot（771クライアント→内部763／764／765）**：771の UntrustedSlot は
  データコンポーネント形式のため変換できず落とす（770と同じ制限）。
- **ロケーターバー／tracked_waypoint（771）**：tracked_waypoint（0x83）は771にしか
  無いため、古いクライアントには送らない。771クライアントが770以下のサーバーに
  いるときはロケーターバーは表示されない。
- **ダイアログ（771）**：show_dialog／clear_dialog（プレイ 0x85／0x84、設定フェーズ
  0x12／0x11）は771にしかないため、古いクライアント・古いサーバーには送らない。
  **minecraft:dialog レジストリは770サーバーからは来ないため、771クライアントへ
  合成注入しない**。実バニラクライアントが欠けたレジストリを内蔵既定で補うかは
  未確認。ダイアログを表示するサーバー側の仕組み（Bubble のダイアログAPI）は未実装。
- **Change Gamemode（771）**：change_gamemode（0x04）は771にしかないため、古いサーバー
  には送らない。771クライアントが770以下のサーバーにいるときはゲームモード変更の
  サーバー行きパケットが届かない。
- **Custom Click Action（771）**：custom_click_action_to_server（プレイ 0x41・設定フェーズ
  0x08）は771サーバーへ通すが、古いサーバーには送らない。771クライアントが770以下の
  サーバーにいるときは、ダイアログやサーバーリンクのクリックがサーバーに届かない。
- **ハッピーガスト（771→770）**：happy_ghast（771の56）は770では ghast（55）に代替する。
  見た目はガストになる。メタデータの個体差が残る（プロキシはエンティティ種別を
  追跡しないため、happy_ghast のメタデータを ghast のレイアウトに直せない）。
  Bedrock の正規化では happy_ghast はブタ代替になる（合成表のため）。
- **音の blow_out の衝突（770⇔771）**：770の `entity.puffer_fish.ambient`（1179）と
  `entity.puffer_fish.blow_out`（1180）が両方771の `entity.puffer_fish.blow_out`
  （1204）へ写る。771→770では blow_out（1204）が ambient（1179）に戻る
  （元の blow_out（1180）には戻らない）。フグの「膨らむ音」と「周囲音」が
  771→770では区別できなくなる。
- **Test Instance Block（771）**：`set_test_block`／`test_instance_block_action`／
  `test_instance_block_status` は770・771にしか無いため落とす。テストブロックは
  1.21.5／1.21.6の実験的機能。
- **Pick Item（771↔770）**：771の pick_item_from_block／pick_item_from_entity は
  770と同じ形式のため素通しする。旧バージョン（763／764／765／766／767／768）向けは、
  770と同じく位置・エンティティ指定とスロット番号指定の違いのため落とす。
- **Player Loaded（771）**：771クライアントが送る player_loaded（0x2B、空）は
  古いサーバーには送らない。統合テストでは参加・スポーン・チャット・移動が
  player_loaded 無しで動いたが、771サーバーが player_loaded を前提に
  処理を進めるかどうかは未確認。
- **チャット checksum（770⇔771）**：770・771とも末尾に checksum u8 があるため付け外しは
  不要。checksum の本当の算法は未確認のため、署名付きチャットや online-mode での
  挙動は別途確認が必要。
- **Player Chat の globalIndex（770⇔771）**：770・771とも先頭に globalIndex があるため
  素通しする。プロキシのセッションごとのカウンタ（0から）は769サーバーからの
  player_chat に付ける。実バニラが globalIndex の連番を厳密に
  検証するかは未確認。`message_acknowledgement` が771で globalIndex
  ベースに変わっているかも未確認（771クライアントのチャット履歴の
  既読確認がずれる可能性がある）。
- **Heightmap の type id 順序（770⇔771）**：バニラの `Heightmap.Types` の列挙順と
  仮定している。実バニラ1.21.6のチャンクで配列の形式は確認済みだが、type id と
  種別名の対応の仮定は未確認のまま（偽クライアントはチャンクを復号しないため、
  統合テストでは検証されていない）。
- **Vehicle Move の onGround（768クライアント→770サーバー）**：**770段階の既存の
  不具合**。`handleClientPlayTo770` の Vehicle Move 分岐は素通しのため、768クライアントの
  onGround 無しペイロードが770サーバーへそのまま送られる。771のスコープ外のため
  直していない。771の To771 分岐では正しく直してある。
- **From770 の一般プレイ分岐に is768() が無い（770段階の既存の挙動）**：770サーバー＋
  768クライアントの一般プレイは落とされる。771では変更しない（771の From771 分岐には
  is768()／is769() を追加してある）。
- **ネイティブ771同士の再設定はセッションが追跡しない**：771同士は Login Success 後に
  即プレイ状態になり、セッションは再設定を追跡しない（770のネイティブ同士と同じ）。
  Start Configuration（0x6F）はそのまま中継され、設定フェーズの Custom Click Action
  （0x08）はプレイ状態の Chat Message（0x08）と番号が同じためバイト列のままサーバーへ
  届く（サーバーは設定状態で読むため正しく解釈する）。
- **creaking_heart の uprooted（770⇔771）**：770と同じ制限。770⇔771ではそのまま通す。
- **ポーションの分裂（770⇔771）**：770と同じ制限。`potion` 99 が `splash_potion` 99 と
  `lingering_potion` 100 に分裂したまま。770⇔771ではそのまま通す。
- **オオカミの鳴き声バリエーション・寒冷／温暖変種（771→770）**：wolf_sound_variant・
  cow_variant・pig_variant・chicken_variant は770・771ともあるためそのまま通す。
  happy_ghast だけが771にしか無い（ghast に代替）。
- **Player Info の showHat／listPriority の順番（770→769）**：770のときと同じ仮定
  （minecraft-data の順番）。
- **ボートの木の種類**：770と同じ制限。767のボートは770／771では常にオークのボートに、
  770／771のボートは767では常に（木の種類を問わず）ボートになる。
  variant（木の種類）をエンティティ種別に直す変換は未実装。
- **food の効果（767→768→769→770→771）**：金リンゴ等の食べたときの効果は768／769／770／771向けに
  落とす。767の food の効果は amplifier／duration を持たないため、768／769／770／771の
  `consumable` の apply_effects 形式へは変換できない。
- **custom_model_data（767→768→769→770→771）**：770と同じ制限。767の単一の数値を
  768／769／770／771の colors[0] に入れるだけのベストエフォート。floats／flags／strings は
  空になる。
- **Set Cooldown（771のグループ名）**：770と同じく、エンダーパール・コーラスフルーツ・
  ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす
  （クールダウンの表示だけの制限）。
- **レシピパケット（771⇔770）**：Craft Recipe Response／Request、
  Displayed Recipe はレシピIDの形式が変わり変換できないため落とす。
  手動クラフトは動く。
- **Declare Recipes／Statistics／Tags／World Event ID**：全方向で未対応。
  レシピ帳・タグが空になるだけで遊べる。
- **ブロックエンティティの crafter／trial_spawner**：パケットごと落とす。
  766→765方向では vault も落とす（1.20系と同じ）。test_block／test_instance_block は
  771にしか無く、770向けには落とす。
- **Entity Metadata の中身**：763向けは variant 系エントリを落とす。
  764では素通しのためスロット・ブロック状態・粒子の中身は未変換のまま残る。
  771にしかないもの（happy_ghast 等）は古いサーバー向けに落とす・代替する。
- **Explosion の771専用パーティクル／サウンド指定**：既定演出に置き換わる
  （766→763方向と同じ）。
- **1.21.6の新要素で通せないもの（771サーバーに770以下のクライアントがいる場合）**：
  ハッピーガストは770以下のクライアントではガストに見える（代替）。
  乾燥ガストは770以下のクライアントでは石に見える（ブロック状態のフォールバック）。
  ハーネス・乾燥ガストのアイテムは石になる。ロケーターバー・ダイアログは
  表示されない（パケットを落とす）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  764／765／766／767／768／769／770／771＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **本物のゲームクライアントでの確認は未実施**：全81通り共通。手順書は
  `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／`PLAYTEST-1.20.5.md`／
  `PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／`PLAYTEST-1.21.4.md`／
  `PLAYTEST-1.21.5.md`／`PLAYTEST-1.21.6.md`。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの
  検証のみ。
- **1.21.7以降（772〜）は未対応**：プロキシは771より新しいプロトコルを
  ログイン時に断る。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。

"""

# 冒頭の紹介文に 1.21.6 を足す
intro_old = """  **1.21.5対応**：クライアント763／764／765／766／767／768／769／770 ×
  サーバー763／764／765／766／767／768／769／770の全64通りが参加できる（下の
  「1.21.5対応：全64通りの対応マトリクス」を参照）。
  Bedrockは6プロトコル × 内部8サーバーの全48通り。"""
intro_new = """  **1.21.5対応**：クライアント763／764／765／766／767／768／769／770 ×
  サーバー763／764／765／766／767／768／769／770の全64通りが参加できる（下の
  「1.21.5対応：全64通りの対応マトリクス」を参照）。
  **1.21.6対応**：クライアント763／764／765／766／767／768／769／770／771 ×
  サーバー763／764／765／766／767／768／769／770／771の全81通りが参加できる（下の
  「1.21.6対応：全81通りの対応マトリクス」を参照）。
  Bedrockは6プロトコル × 内部9サーバーの全54通り。"""
assert text.count(intro_old) == 1, "intro anchor not found"
text = text.replace(intro_old, intro_new, 1)

# 末尾の優先順位を更新する
tail_old = """**1.20.1はステップBで追加済み、1.20.2も追加済み、1.20.5／1.20.6も
追加済み、1.21／1.21.1も追加済み、1.21.2／1.21.3も追加済み、1.21.4も
追加済み、1.21.5も追加済み**。
次は1.21.6（771）。"""
tail_new = """**1.20.1はステップBで追加済み、1.20.2も追加済み、1.20.5／1.20.6も
追加済み、1.21／1.21.1も追加済み、1.21.2／1.21.3も追加済み、1.21.4も
追加済み、1.21.5も追加済み、1.21.6も追加済み**。
次は1.21.7（772）。"""
assert text.count(tail_old) == 1, "tail anchor not found"
text = text.replace(tail_old, tail_new, 1)

anchor = "## バニラとの結び方（TODO・方針）"
assert anchor in text
text = text.replace(anchor, SECTION + anchor, 1)
P.write_text(text, encoding="utf-8")
print("inserted 1.21.6 matrix section")
print("new rows added:", len(new_rows))
