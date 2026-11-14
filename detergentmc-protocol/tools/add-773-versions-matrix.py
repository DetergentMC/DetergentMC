#!/usr/bin/env python3
"""docs/VERSIONS.md に 1.21.9／1.21.10（773）の 11x11=121通りマトリクス節を追加する。

1.21.7のマトリクス表（100セル）をそのまま流用し、各行に773列のセルを
追加し、773行を新設する。既存の100セルの内容は一字一句変えない。
冒頭の「1.21.7対応」紹介文の直後に「1.21.9対応」の紹介文を足し、
末尾の「バージョン追加の優先順位」も更新する。
あわせて陳腐化した2つの記述を直す。
- 1.21.6節の「1.21.7以降（772〜）は未対応」→ 772・773とも対応済みに訂正。
- 1.21.7節の「1.21.9以降（773〜）は未対応」→ 1.21.11以降（774〜）に訂正。

注意：このスクリプトは統合テスト（ProxyIntegrationTest の773関係8件と
BedrockMatrixIntegrationTest の1.21.9／1.21.10）が通ることを前提に
「統合テスト済み」と書く。フルビルドで失敗が出たら、文書の該当箇所を
修正してからコミットすること。
"""
import re
from pathlib import Path

ROOT = Path("/home/hatch/workspace/detergentmc")
P = ROOT / "docs" / "VERSIONS.md"
text = P.read_text(encoding="utf-8")

# 1.21.7マトリクスの表を抜き出す
m = re.search(r"(## 1\.21\.7対応：全100通りの対応マトリクス.*?)\n(### 771→772のレジストリ差分)",
              text, re.DOTALL)
assert m, "1.21.7 matrix section not found"
matrix_sec = m.group(1)

# 表の行を抜き出す（ヘッダ行と区切り行と10行のデータ行）
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
assert len(rows) == 10, f"expected 10 data rows, got {len(rows)}"

# 773列のセル（行ごと）
COL_773 = {
    763: "| ◎ 制限付き（統合テスト済み）。`ProxySession773Test.join763Against773ConsumesConfigAs773Client`（参加フロー単体テスト）で確認。プロキシが773の Registry Data などを消費して 763 の Join Game に codec を合成。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす。Entity Metadataのvariant系を落とす |",
    764: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす |",
    765: "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession773Test.join773Against765RelaysJoinLevel`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす |",
    766: "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession773Test.join773Against766TranslatesJoinGame`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序を並べ替え（dialog・jukebox_song は落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は766⇔773で変換できる。Declare Recipes／Tagsを落とす |",
    767: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は767⇔773で変換できる。Declare Recipes／Tagsを落とす |",
    768: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入・dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は768⇔773で変換できる。Declare Recipes／Tagsを落とす |",
    769: "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession773Test.join769Against773TranslatesSlots`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は769⇔773で変換できる。Declare Recipes／Tagsを落とす |",
    770: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は770⇔773で変換できる。Declare Recipes／Tagsを落とす |",
    771: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は771⇔773で変換できる。Declare Recipes／Tagsを落とす |",
    772: "| ◎ 制限付き（統合テスト済み）。`ProxySession773Test.join772Against773TranslatesSlots`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序と形式を変換（銅ゴーレム／マネキンのエンティティはブタへ）。Window Click を落とす（HashedSlot）。Set Creative Slot は772⇔773で変換できる。Declare Recipes／Tagsを落とす |",
}


def client_of(row):
    mm = re.match(r"\| \*\*(\d+)", row)
    assert mm, row[:60]
    return int(mm.group(1))


new_hdr = hdr + " 773（1.21.9／1.21.10） |"
new_sep = sep + "---|"
new_rows = []
for row in rows:
    # 行は「|」で終わるため、列の先頭の「|」は付けない（付けると空セルになる）
    new_rows.append(row + COL_773[client_of(row)][1:])

ROW_773 = (
    "| **773（1.21.9／1.21.10）** "
    "| ◎ 制限付き（統合テスト済み）。`ProxySession773Test.join773Against763SynthesizesConfig`（参加フロー単体テスト）で確認。プロキシが Join Game の codec から設定フェーズを合成。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession773Test.join773Against765RelaysJoinLevel`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession773Test.join773Against766TranslatesJoinGame`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序を並べ替え（dialog・jukebox_song は落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は773⇔766で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は773⇔767で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入）。Window Click を落とす（HashedSlot）。Set Creative Slot は773⇔768で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession773Test.join769Against773TranslatesSlots`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は773⇔769で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は773⇔770で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は773⇔771で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 制限付き（統合テスト済み）。`ProxySession773Test.join773Against772TranslatesSlots`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序と形式を変換（銅ゴーレム／マネキンはブタへ）。Window Click を落とす（HashedSlot）。Set Creative Slot は773⇔772で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 素通し（リレー・統合テスト済み。1.21.9と1.21.10の両方で確認） |"
)

TABLE = "\n".join([new_hdr, new_sep] + new_rows + [ROW_773])

SECTION = """## 1.21.9対応：全121通りの対応マトリクス（2026-11-12）

クライアント763／764／765／766／767／768／769／770／771／772／773 × サーバー（内部プロトコル）763／764／
765／766／767／768／769／770／771／772／773の全121通りが参加できる。1.21.7対応の100通りに、773クライアントと
773サーバーを加えたものである。見出し事例は「1.21.9／1.21.10のサーバー（内部773）に
1.20系・1.21クライアントが入る」「1.20系・1.21サーバーに1.21.9／1.21.10クライアントが
入る」こと。

凡例は1.20系のマトリクスと同じ（◎＝統合テスト済み、○＝素通しで統合テストなし、
△＝制限付き、×＝できない。現在×は無い）。

""" + TABLE + """

検証の区別（まとめ）：

- **統合テスト済み（61件）**：1.21.7対応の53件（上を参照）に加え、
  773関係の8件（773→1219、773→12110、773→1217、773→1218、773→1216、773→1201、
  772→1219、772→12110。`ProxyIntegrationTest` の8テスト。いずれも
  参加・スポーン・チャット・移動まで確認）。773→1219／12110ではチャット checksum
  0x00 が実バニラ1.21.9／1.21.10に受け入れられることも確認済み。
- **単体テスト済み・統合テストなし**：773→763、773→766、773→765、769→773は
  `ProxySession773Test` の参加フロー単体テストで確認。773↔764／767／768／770／771と
  763／764／765／766／767／768／769／770／771→773は、`ClientAdapter` の全組み合わせ判定テストと
  翻訳子（`ChunkTranslator773Test`／`PlayTranslator773Test`）の単体テストで確認。
- **素通し（リレー）・統合テストなし**：763→763、764→764、766→766。
  リレーはバイト列をそのまま通すため変換の誤りが入りようが無く、765→765と
  767→767と768→768と769→769と770→770と771→771と772→772と773→773の統合テストで経路自体は検証済み。
- **Bedrock→773**：`JavaBackendConnection773Test`（単体テスト10件）に加え、
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_9`／`matrixAgainst1_21_10`（Bedrock 6 プロトコル ×
  内部773の統合テスト。参加・スポーン・チャット・移動・チャンク・アイテム）で
  統合テスト済み（2026-11-12）。
  内部763〜772の60通りと合わせて66通り。
- **実機でしか確認できないもの（全121通り共通）**：本物のゲームクライアントでの
  参加・見た目・操作感、本物のMojang認証サーバーでのonline-mode参加、
  リソースパックの「互換性あり」表示と適用、泡テクスチャの目での確認。
  手順書は `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／
  `PLAYTEST-1.20.5.md`／`PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／
  `PLAYTEST-1.21.4.md`／`PLAYTEST-1.21.5.md`／`PLAYTEST-1.21.6.md`／
  `PLAYTEST-1.21.7.md`／`PLAYTEST-1.21.9.md` を参照。

### 772→773のレジストリ差分（2026-11-12、ViaVersion 対応表と実バニラ Registry Data で確認）

- **ブロック状態**：27,946→29,671件（+1,725）。新規は棚12種、銅の松明・壁松明、
  銅の棒×8、銅の鎖×8＋`chain`→`iron_chain` の改名、銅のランタン×8、銅のチェスト×8、
  銅ゴーレム像×8、避雷針の露出・風化・酸化・ワックス×7。trial_spawner 27730→29455、
  vault 27742→29467。
- **アイテム**：1,416→1,488件（正味+72。新規73、改名1）。棚12件を305〜316に挿入
  （acacia_shelf〜warped_shelf）。bookshelf 305→317。`chain`→`iron_chain` の改名
  （772の378→399）。新規アイテムは銅の道具×5、銅の防具×4、copper_golem_spawn_egg、
  copper_horse_armor、copper_nugget。
- **エンティティ**：151→153件（+2）。`copper_golem` が773の27、`mannequin` が773の82に
  挿入。pig 95→97、happy_ghast 56→57。
- **音**：1,728→1,771件（+43）。銅ゴーレム／チェスト／棚／像の音と `weather.end_flash`。
  block.stone.break 1442→1484。music_disc.lava_chicken 939→970。
- **パーティクル**：114→115件（+1）。`copper_fire_flame` が773の5番に挿入。
- **属性**：35件で同じ顔ぶれ・同じ順番。
- **エンチャント**：42件で同じ顔ぶれ・同じ順番。
- **データコンポーネント種別**：96件で同じ顔ぶれ・同じ順番。
- **絵画**：51件で同じ顔ぶれ・同じ順番（dennis=14、kebab=24、pointer=33、pond=34、
  unpacked=44、wither=50）。
- **旗の模様**：43件で同じ。**トリム**：18件で同じ。
- **ダメージ種別**：49件で同じ顔ぶれ・同じ順番。
- **バイオーム**：65件で同じ顔ぶれ・同じ順番。
- **ブロックエンティティ種別**：47→49件（+2）。shelf を773の40番に挿入、
  copper_golem_statue を773の48番に追加。
- **ジュークボックスの歌**：21件で同じ顔ぶれ・同じ順番。
- **ダイアログ**：3件で同じ（custom_options 0、quick_actions 1、server_links 2）。
- **メニュー**：25件で同じ。
- **パケットID**：772→773で変更108件（すべてPLAY／CLIENTBOUND）、新規8件、削除1件。
  Join Game 0x2B→0x30、Chunk Data 0x27→0x2C、Keep Alive 0x26→0x2B、Ping 0x36→0x3B、
  Start Configuration 0x6F→0x74、Disconnect 0x1C→0x20、Respawn 0x4B→0x50、
  Add Resource Pack 0x4A→0x4F、Spawn Position 0x5A→0x5F、Player Info Update 0x3F→0x44、
  Tracked Waypoint 0x83→0x88、Show Dialog 0x85→0x8A、Clear Dialog 0x84→0x89。
  プレイ・サーバー行きIDは772→773で不変（削除されたdebug_sample_subscriptionを除く）。
  新規は設定の Code of Conduct（0x13）／Accept Code of Conduct（0x09）と、
  デバッグ系4件・game_test_highlight_pos・debug_subscription_request。
- **チャンク**：heightmap は772と同じ直接配列。セクションのブロック状態・バイオーム・
  ブロックエンティティ種別を付け替える。
- **チャット**：サーバー行きの Chat Message／Chat Command Signed の末尾の
  `checksum` u8 は772・773ともある（付け外し不要）。773→1219／12110の
  統合テストで checksum 0x00 が実バニラ1.21.9／1.21.10に受け入れられることも確認済み。
- **Code of Conduct**：実バニラ1.21.9は既定のオフラインサーバーでは Code of Conduct
  （設定 0x13）を送ってこない（2026-11-12の生ソケットプローブ
  `detergentmc-protocol/tools/probe1219-coc.py`／`probe1219-coc2.py` で確認）。
  そのため古いクライアントでも設定フェーズで止まらない。773クライアントが Accept
  （0x09）を送ったときは、プロキシは773サーバーへそのまま通す
  （`ProxySession.handleClientConfiguration773`）。CoC を送るサーバーに古い
  クライアントが入った場合、プロキシは CoC を落とすだけで accept を代理で返さないため、
  サーバーが accept を待つ設計なら設定フェーズで止まる（未確認の制限。`Outcome` に
  サーバーへの合成送信が無いため、この段階では直していない）。
- 遠い組み合わせ（763／764／765／766／767／768／769／770／771↔773）は、772を経由する合成表で作る
  （`detergentmc-protocol/tools/generate-remap-tables.py --target 773`）。
  実行時は合成済みの1つの表で直接引く二段変換ではない（1.20系の相互連携と
  同じ方針）。逆向きは `RegistryRemapper.invert()` で反転して使い回す。

### まだできないこと（1.21.9対応・正直な一覧）

- **Window Click（773が関わる全方向）**：773の HashedSlot は成分のハッシュ値
  （i32）だけを持ち、元の Slot に戻せない。ハッシュの算法が未確認のため全方向で
  落とす（772⇔773も含む）。773クライアントが内部773以外のサーバーにいるとき、773サーバーにいる
  772以下のクライアントは、インベントリ操作（クリック）ができない。Set Creative
  Slot は UntrustedSlot→Slot で変換できる（773⇔772／771／770／769／768／767／766）。
- **Set Creative Slot（773クライアント→内部763／764／765）**：773の UntrustedSlot は
  データコンポーネント形式のため変換できず落とす（772と同じ制限）。
- **ロケーターバー／tracked_waypoint（773）**：tracked_waypoint（0x88）は772・773にしか
  無いため、771以下のクライアントには送らない。773クライアントが771以下のサーバーに
  いるときはロケーターバーは表示されない。772⇔773ではそのまま通す。
- **ダイアログ（773）**：show_dialog／clear_dialog（プレイ 0x8A／0x89、設定フェーズ
  0x12／0x11）は772・773にしかないため、771以下のクライアント・サーバーには送らない。
  772⇔773ではそのまま通す。**minecraft:dialog レジストリは771サーバーからは来ないため、773クライアントへ
  合成注入しない**。実バニラクライアントが欠けたレジストリを内蔵既定で補うかは
  未確認。ダイアログを表示するサーバー側の仕組み（Bubble のダイアログAPI）は未実装。
- **Change Gamemode（773）**：change_gamemode（0x04）は771・772・773にしかないため、770以下のサーバー
  には送らない。773クライアントが770以下のサーバーにいるときはゲームモード変更の
  サーバー行きパケットが届かない。772⇔773ではそのまま通す。
- **Custom Click Action（773）**：custom_click_action_to_server（プレイ 0x41・設定フェーズ
  0x08）は771・772・773サーバーへ通すが、770以下のサーバーには送らない。773クライアントが770以下の
  サーバーにいるときは、ダイアログやサーバーリンクのクリックがサーバーに届かない。
- **エンティティのレジストリ順序（772→773）**：Registry Data の entity_type は
  並べ替えの対象外のため、772サーバーから来た151件（pig=95）のまま773クライアントに
  届く。Spawn Entity の種別ID自体は773へ翻訳される（pig=97）ため、実機クライアントでは
  エンティティの表示がずれる可能性がある。772→773の一段だけ並べ替えを足しても
  合成経路（内部763→…→772→773）では正しくならず、全体の直しは全ペアにまたがる
  再設計になるため、773専任の範囲では直さない。`PlayTranslator773Test` に素通しを
  明記するテストを入れてある。同じ問題はエンティティ順が変わる既存のペア
  （767→768、769→770、770→771）にもある。
- **銅ゴーレム／マネキン（773→772）**：copper_golem（773の27）／mannequin（773の82）は
  772に無いため、772向けにはブタ（95）に落とす。772→773では銅ゴーレム像・棚の
  ブロックエンティティ（shelf 773の40／copper_golem_statue 773の48）は
  パケットごと落とす。
- **1.21.9の新要素で通せないもの（773サーバーに772以下のクライアントがいる場合）**：
  銅の道具・防具は772以下のクライアントでは石に見える（アイテムのフォールバック）。
  銅ゴーレム／マネキンはブタに見える。銅のチェスト・棚・銅のランタン・銅の鎖等の
  新ブロックは石に見える。銅の音は block.stone.break になる。デバッグ系パケット
  （debug_block_value 等）は旧クライアント向けに落とす。
- **Code of Conduct（773）**：既定のサーバーは送ってこないため通常は問題にならない。
  CoC を送るサーバーに古いクライアントが入った場合、プロキシは CoC を落とすだけで
  accept を代理で返さないため、サーバーが accept を待つ設計なら設定フェーズで止まる
  （未確認の制限。上の「Code of Conduct」を参照）。
- **Declare Recipes／Statistics／Tags／World Event ID**：全方向で未対応。
  レシピ帳・タグが空になるだけで遊べる。
- **ブロックエンティティの crafter／trial_spawner**：パケットごと落とす。
  766→765方向では vault も落とす（1.20系と同じ）。shelf／copper_golem_statue は
  773にしか無く、772向けには落とす。
- **Entity Metadata の中身**：763向けは variant 系エントリを落とす。
  764では素通しのためスロット・ブロック状態・粒子の中身は未変換のまま残る。
  773にしかないもの（銅ゴーレム等）は古いサーバー向けに落とす・代替する。
- **Explosion の773専用パーティクル／サウンド指定**：既定演出に置き換わる
  （766→763方向と同じ）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  764／765／766／767／768／769／770／771／772／773＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **本物のゲームクライアントでの確認は未実施**：全121通り共通。手順書は
  `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／`PLAYTEST-1.20.5.md`／
  `PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／`PLAYTEST-1.21.4.md`／
  `PLAYTEST-1.21.5.md`／`PLAYTEST-1.21.6.md`／`PLAYTEST-1.21.7.md`／`PLAYTEST-1.21.9.md`。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの
  検証のみ。
- **764サーバー→773クライアントはプレイ状態のパケットが765形式のまま送られる**：
  既存の制限（772クライアントでも同じ）。
- **From766 の Disconnect／Respawn が770／771／772クライアントでフォールバックに落ちる**：
  既存の不具合（1155adfにもあった。773分岐だけ追加し、770〜772は直していない）。
- **From769／768 の Respawn で is770()→0x4B（正しくは0x4C）**：既存の不具合。
- **1.21.11以降（774〜）は未対応**：プロキシは773より新しいプロトコルを
  ログイン時に断る。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。

"""

# 冒頭の紹介文に 1.21.9 を足す
intro_old = """  **1.21.7対応**：クライアント763／764／765／766／767／768／769／770／771／772 ×
  サーバー763／764／765／766／767／768／769／770／771／772の全100通りが参加できる（下の
  「1.21.7対応：全100通りの対応マトリクス」を参照）。
  Bedrockは6プロトコル × 内部10サーバーの全60通り。"""
intro_new = """  **1.21.7対応**：クライアント763／764／765／766／767／768／769／770／771／772 ×
  サーバー763／764／765／766／767／768／769／770／771／772の全100通りが参加できる（下の
  「1.21.7対応：全100通りの対応マトリクス」を参照）。
  **1.21.9対応**：クライアント763／764／765／766／767／768／769／770／771／772／773 ×
  サーバー763／764／765／766／767／768／769／770／771／772／773の全121通りが参加できる（下の
  「1.21.9対応：全121通りの対応マトリクス」を参照）。
  Bedrockは6プロトコル × 内部11サーバーの全66通り。"""
assert text.count(intro_old) == 1, "intro anchor not found"
text = text.replace(intro_old, intro_new, 1)

# 末尾の優先順位を更新する
tail_old = """**1.20.1はステップBで追加済み、1.20.2も追加済み、1.20.5／1.20.6も
追加済み、1.21／1.21.1も追加済み、1.21.2／1.21.3も追加済み、1.21.4も
追加済み、1.21.5も追加済み、1.21.6も追加済み、1.21.7も追加済み**。
次は1.21.9（773）。"""
tail_new = """**1.20.1はステップBで追加済み、1.20.2も追加済み、1.20.5／1.20.6も
追加済み、1.21／1.21.1も追加済み、1.21.2／1.21.3も追加済み、1.21.4も
追加済み、1.21.5も追加済み、1.21.6も追加済み、1.21.7も追加済み、1.21.9も追加済み**。
次は1.21.11（774）。"""
assert text.count(tail_old) == 1, "tail anchor not found"
text = text.replace(tail_old, tail_new, 1)

# 陳腐化した記述を直す（1.21.6節の「1.21.7以降は未対応」）
stale_772_old = """- **1.21.7以降（772〜）は未対応**：プロキシは771より新しいプロトコルを
  ログイン時に断る。"""
stale_772_new = """- **1.21.7以降（772〜）は対応済み**：この節を書いた時点では未対応だったが、
  1.21.7対応（下の節）で772を、1.21.9対応で773を追加済み。1.21.11以降（774〜）は
  未対応：プロキシは773より新しいプロトコルをログイン時に断る。"""
assert text.count(stale_772_old) == 1, "stale 772 anchor not found"
text = text.replace(stale_772_old, stale_772_new, 1)

# 陳腐化した記述を直す（1.21.7節の「1.21.9以降は未対応」）
stale_773_old = """- **1.21.9以降（773〜）は未対応**：プロキシは772より新しいプロトコルを
  ログイン時に断る。"""
stale_773_new = """- **1.21.11以降（774〜）は未対応**：プロキシは773より新しいプロトコルを
  ログイン時に断る。"""
assert text.count(stale_773_old) == 1, "stale 773 anchor not found"
text = text.replace(stale_773_old, stale_773_new, 1)

anchor = "## バニラとの結び方（TODO・方針）"
assert anchor in text
text = text.replace(anchor, SECTION + anchor, 1)
P.write_text(text, encoding="utf-8")
print("inserted 1.21.9 matrix section")
print("new rows added:", len(new_rows))
