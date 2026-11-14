#!/usr/bin/env python3
"""docs/VERSIONS.md に 1.21.7／1.21.8（772）の 10x10=100通りマトリクス節を追加する。

1.21.6のマトリクス表（81セル）をそのまま流用し、各行に772列のセルを
追加し、772行を新設する。既存の81セルの内容は一字一句変えない。
ただし771段階で入った空セル（`| |`）だけは取り除く（セルの内容は
変わらない。表の列数がヘッダと合っていなかったため）。
冒頭の「1.21.6対応」紹介文の直後に「1.21.7対応」の紹介文を足し、
末尾の「バージョン追加の優先順位」も更新する。
"""
import re
from pathlib import Path

ROOT = Path("/home/hatch/workspace/detergentmc")
P = ROOT / "docs" / "VERSIONS.md"
text = P.read_text(encoding="utf-8")

# 1.21.6マトリクスの表を抜き出す
m = re.search(r"(## 1\.21\.6対応：全81通りの対応マトリクス.*?)\n(### 770→771のレジストリ差分)",
              text, re.DOTALL)
assert m, "1.21.6 matrix section not found"
matrix_sec = m.group(1)

# 表の行を抜き出す（ヘッダ行と区切り行と9行のデータ行）
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
assert len(rows) == 9, f"expected 9 data rows, got {len(rows)}"

# 772列のセル（行ごと）
COL_772 = {
    763: "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession772Test.join763Against772ConsumesConfigAs772Client`（参加フロー単体テスト）で確認。プロキシが772の Registry Data などを消費して 763 の Join Game に codec を合成。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす。Entity Metadataのvariant系を落とす |",
    764: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす |",
    765: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす |",
    766: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序を並べ替え（dialog・jukebox_song を落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は766⇔772で変換できる。Declare Recipes／Tagsを落とす |",
    767: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は767⇔772で変換できる。Declare Recipes／Tagsを落とす |",
    768: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入・dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は768⇔772で変換できる。Declare Recipes／Tagsを落とす |",
    769: "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession772Test.join769Against772TranslatesSlots`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は769⇔772で変換できる。Declare Recipes／Tagsを落とす |",
    770: "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は770⇔772で変換できる。Declare Recipes／Tagsを落とす |",
    771: "| ◎ 制限付き（統合テスト済み）。設定フェーズを中継し Registry Data の順序と形式を変換（lava_chicken を9番に挿入、dialog レジストリはそのまま通す）。Window Click を落とす（HashedSlot）。Set Creative Slot は771⇔772で変換できる。Declare Recipes／Tagsを落とす |",
}

# クライアント番号を行から取り出す
def client_of(row):
    mm = re.match(r"\| \*\*(\d+)", row)
    assert mm, row[:60]
    return int(mm.group(1))

# 771段階で入った空セル「| |」を取り除く（セルの内容は変わらない。
# 771行は771ツールが新規作成した行のため空セルは無い）
def strip_empty_cell(row):
    n = row.count("| |")
    assert n <= 1, f"unexpected empty cells: {row[:80]}"
    if n == 1:
        return row.replace(" | | ", " | ", 1)
    return row

new_hdr = hdr + " 772（1.21.7／1.21.8） |"
new_sep = sep + "---|"
new_rows = []
for row in rows:
    # 行は「|」で終わるため、列の先頭の「|」は付けない（付けると空セルになる）
    new_rows.append(strip_empty_cell(row) + COL_772[client_of(row)][1:])

ROW_772 = (
    "| **772（1.21.7／1.21.8）** "
    "| ◎ 制限付き（統合テスト済み）。`ProxySession772Test.join772Against763SynthesizesConfig`（参加フロー単体テスト）で確認。プロキシが Join Game の codec から設定フェーズを合成。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession772Test.join772Against765RelaysJoinLevel`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは合成しない。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。`ProxySession772Test.join772Against766TranslatesJoinGame`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の順序を並べ替え（dialog・jukebox_song は落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は772⇔766で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は772⇔767で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入）。Window Click を落とす（HashedSlot）。Set Creative Slot は772⇔768で変換できる。Declare Recipes／Tagsを落とす "
    "| △ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は772⇔769で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 制限付き（統合テスト済み）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は772⇔770で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 制限付き（統合テスト済み）。設定フェーズを中継し Registry Data の順序と形式を変換（lava_chicken を落とす、dialog レジストリはそのまま通す）。Window Click を落とす（HashedSlot）。Set Creative Slot は772⇔771で変換できる。Declare Recipes／Tagsを落とす "
    "| ◎ 素通し（リレー・統合テスト済み。1.21.7と1.21.8の両方で確認） |"
)
new_rows.append(ROW_772)

table = "\n".join([new_hdr, new_sep] + new_rows)

SECTION = """## 1.21.7対応：全100通りの対応マトリクス（2026-11-12）

クライアント763／764／765／766／767／768／769／770／771／772 × サーバー（内部プロトコル）763／764／
765／766／767／768／769／770／771／772の全100通りが参加できる。1.21.6対応の81通りに、772クライアントと
772サーバーを加えたものである。見出し事例は「1.21.7／1.21.8のサーバー（内部772）に
1.20系・1.21クライアントが入る」「1.20系・1.21サーバーに1.21.7／1.21.8クライアントが
入る」こと。

凡例は1.20系のマトリクスと同じ（◎＝統合テスト済み、○＝素通しで統合テストなし、
△＝制限付き、×＝できない。現在×は無い）。

""" + table + """

検証の区別（まとめ）：

- **統合テスト済み（53件）**：1.21.6対応の47件（上を参照）に加え、
  772関係の6件（772→1217、772→1218、772→1216、771→1217、772→1215、772→1201。
  `ProxyIntegrationTest` の6テスト。いずれも
  参加・スポーン・チャット・移動まで確認）。772→1217／1218ではチャット checksum
  0x00 が実バニラ1.21.7／1.21.8に受け入れられることも確認済み。
- **単体テスト済み・統合テストなし**：772→763、772→766、772→765、763→772、769→772は
  `ProxySession772Test` の参加フロー単体テストで確認。772↔764／767／768と
  763／764／765／766／767／768／770→772は、`ClientAdapter` の全組み合わせ判定テストと
  翻訳子（`ChunkTranslator772Test`／`PlayTranslator772Test`）の単体テストで確認。
- **素通し（リレー）・統合テストなし**：763→763、764→764、766→766。
  リレーはバイト列をそのまま通すため変換の誤りが入りようが無く、765→765と
  767→767と768→768と769→769と770→770と771→771と772→772の統合テストで経路自体は検証済み。
- **Bedrock→772**：`JavaBackendConnection772Test`（単体テスト10件）に加え、
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_7`／`matrixAgainst1_21_8`（Bedrock 6 プロトコル ×
  内部772の統合テスト。参加・スポーン・チャット・移動・チャンク・アイテム）で
  統合テスト済み（2026-11-12）。
  内部763／764／765／766／767／768／769／770／771の54通りと合わせて60通り。
- **実機でしか確認できないもの（全100通り共通）**：本物のゲームクライアントでの
  参加・見た目・操作感、本物のMojang認証サーバーでのonline-mode参加、
  リソースパックの「互換性あり」表示と適用、泡テクスチャの目での確認。
  手順書は `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／
  `PLAYTEST-1.20.5.md`／`PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／
  `PLAYTEST-1.21.4.md`／`PLAYTEST-1.21.5.md`／`PLAYTEST-1.21.6.md`／
  `PLAYTEST-1.21.7.md` を参照。

### 771→772のレジストリ差分（2026-11-12、実バニラ Registry Data で確認）

dennis・lava_chicken の位置と 1.21.7／1.21.8 の同一性は
`RegistryOrderCaptureTest.registryOrderOfRealVanilla1217`／
`registryOrderOfRealVanilla1218` で実バニラ1.21.7／1.21.8 の Registry Data から
確認する（このステージで追加）。minecraft:item／minecraft:sound_event／
minecraft:mob_effect は Registry Data で送られない組み込みレジストリのため、
アイテム1256・音939・エフェクトの改名は minecraft-data の表による（771段階と同じ）。

- **ブロック状態**：27,946件で同じ顔ぶれ・同じ順番。
- **アイテム**：1,415→1,416件（+1、−0）。`music_disc_lava_chicken` が772の1256に挿入。
- **エンティティ**：151件で同じ顔ぶれ・同じ順番。`happy_ghast` は771・772とも56。
- **音**：1,727→1,728件（+1、−0）。`music_disc.lava_chicken` が772の939に挿入。
- **パーティクル**：114件で同じ顔ぶれ・同じ順番。
- **属性**：35件で同じ顔ぶれ・同じ順番。`camera_distance` は771・772とも8番、
  `generic.movement_speed` は771・772とも22。
- **エンチャント**：42件で同じ顔ぶれ・同じ順番。
- **データコンポーネント種別**：96件で同じ顔ぶれ・同じ順番。
- **絵画**：50→51件。`minecraft:dennis` が772の14番に挿入
  （kebab 24、wither 50）。
- **旗の模様**：43件で同じ。**トリム**：18件で同じ。
- **ダメージ種別**：49件で同じ顔ぶれ・同じ順番。
- **バイオーム**：65件で同じ顔ぶれ・同じ順番。
- **ブロックエンティティ種別**：47件で同じ顔ぶれ・同じ順番。
- **ジュークボックスの歌**：20→21件。`minecraft:lava_chicken` が772の9番に挿入
  （mall 10、tears 18、wait 19、ward 20）。
- **ダイアログ**：3件で同じ（custom_options 0、quick_actions 1、server_links 2）。
  **771段階の前提の訂正**：ダイアログは771にもある（設定フェーズ 0x12／0x11、
  プレイ 0x85／0x84）。771段階で「770向けに落とす」としたのは正しいが、
  「771には無い」は誤りだった。771⇔772ではダイアログもロケーターバーも
  Change Gamemode も Custom Click Action もそのまま通す。
- **メニュー**：25件で同じ。
- **パケットID**：771→772で変更なし（247件すべて同一）。Join Game 0x2B、
  Spawn Position 0x5A、Keep Alive 0x26、Ping 0x36、Start Configuration 0x6F、
  Disconnect 0x1C、Respawn 0x4B、Chat Message 0x08、Window Click 0x11、
  Set Creative Slot 0x37、Keep Alive 応答 0x1B、Ping 応答 0x2C、
  Set Player Position 0x1D。IDの付け替えは不要。
- **チャンク**：heightmap は771と同じ直接配列。セクションのパレット付き
  コンテナの Data Array から長さの varint 接頭辞が無いのも771と同じ
  （770で廃止されたまま）。
- **チャット**：サーバー行きの Chat Message／Chat Command Signed の末尾の
  `checksum` u8 は771・772ともある（付け外し不要）。クライアント行きの Player Chat の
  先頭の `globalIndex` varint は771・772ともある（素通し）。772→1217／1218の
  統合テストで checksum 0x00 が実バニラ1.21.7／1.21.8に受け入れられることも確認済み。
- 遠い組み合わせ（763／764／765／766／767／768／769／770↔772）は、771を経由する合成表で作る
  （`detergentmc-protocol/tools/generate-remap-tables.py --target 772`）。
  実行時は合成済みの1つの表で直接引く二段変換ではない（1.20系の相互連携と
  同じ方針）。逆向きは `RegistryRemapper.invert()` で反転して使い回す。

### まだできないこと（1.21.7対応・正直な一覧）

- **Window Click（772が関わる全方向）**：772の HashedSlot は成分のハッシュ値
  （i32）だけを持ち、元の Slot に戻せない。ハッシュの算法が未確認のため全方向で
  落とす（771⇔772も含む）。772クライアントが内部772以外のサーバーにいるとき、772サーバーにいる
  771以下のクライアントは、インベントリ操作（クリック）ができない。Set Creative
  Slot は UntrustedSlot→Slot で変換できる（772⇔771／770／769／768／767／766）。
- **Set Creative Slot（772クライアント→内部763／764／765）**：772の UntrustedSlot は
  データコンポーネント形式のため変換できず落とす（771と同じ制限）。
- **ロケーターバー／tracked_waypoint（772）**：tracked_waypoint（0x83）は771・772にしか
  無いため、770以下のクライアントには送らない。772クライアントが770以下のサーバーに
  いるときはロケーターバーは表示されない。771⇔772ではそのまま通す。
- **ダイアログ（772）**：show_dialog／clear_dialog（プレイ 0x85／0x84、設定フェーズ
  0x12／0x11）は771・772にしかないため、770以下のクライアント・サーバーには送らない。
  771⇔772ではそのまま通す。**minecraft:dialog レジストリは770サーバーからは来ないため、772クライアン��へ
  合成注入しない**。実バニラクライアントが欠けたレジストリを内蔵既定で補うかは
  未確認。ダイアログを表示するサーバー側の仕組み（Bubble のダイアログAPI）は未実装。
- **Change Gamemode（772）**：change_gamemode（0x04）は771・772にしかないため、770以下のサーバー
  には送らない。772クライアントが770以下のサーバーにいるときはゲームモード変更の
  サーバー行きパケットが届かない。771⇔772ではそのまま通す。
- **Custom Click Action（772）**：custom_click_action_to_server（プレイ 0x41・設定フェーズ
  0x08）は771・772サーバーへ通すが、770以下のサーバーには送らない。772クライアントが770以下の
  サーバーにいるときは、ダイアログやサーバーリンクのクリックがサーバーに届かない。
- **絵画 dennis（772→771）**：dennis（772の14）は771に無いため、772→771では絵画の
  Entity Metadata エントリごと落とす（絵画自体はスポーンするが絵柄は既定になる）。
  771→772では 14 以降の絵画が +1 ずれる（kebab は 771の23 → 772の24）。
  ジュークボックスの lava_chicken（772の9）は771→772では 9番に挿入され、
  772→771ではジュークボックスの歌 "11"（0）に付け替わる（コンポーネントの
  holder 付け替え）。音の music_disc.lava_chicken（772の939）は771に無いため、
  771→772では 939 以降の音が +1 ずれる。772→771では music_disc.lava_chicken
  （939）が block.stone.break（1441）に付け替わる。アイテム music_disc_lava_chicken
  （772の1256）は772→771では石（1）に落とす。
- **ハッピーガスト（772→771）**：happy_ghast（771・772とも56）はそのまま通す。
  Bedrock の正規化では happy_ghast はブタ代替になる（合成表のため）。
- **Test Instance Block（772）**：`set_test_block`／`test_instance_block_action`／
  `test_instance_block_status` は770・771・772にしか無いため落とす。テストブロックは
  1.21.5／1.21.6／1.21.7の実験的機能。
- **Pick Item（772↔771）**：772の pick_item_from_block／pick_item_from_entity は
  771と同じ形式のため素通しする。旧バージョン（763／764／765／766／767／768）向けは、
  771と同じく位置・エンティティ指定とスロット番号指定の違いのため落とす。
- **Player Loaded（772）**：772クライアントが送る player_loaded（0x2B、空）は
  771と同じく古いサーバーには送らない。統合テストでは参加・スポーン・チャット・移動が
  player_loaded 無しで動いたが、772サーバーが player_loaded を前提に
  処理を進めるかどうかは未確認。
- **チャット checksum（771⇔772）**：771・772とも末尾に checksum u8 があるため付け外しは
  不要。checksum の本当の算法は未確認のため、署名付きチャットや online-mode での
  挙動は別途確認が必要。
- **Player Chat の globalIndex（771⇔772）**：771・772とも先頭に globalIndex があるため
  素通しする。プロキシのセッションごとのカウンタ（0から）は770サーバーからの
  player_chat に付ける。実バニラが globalIndex の連番を厳密に
  検証するかは未確認。
- **Heightmap の type id 順序（771⇔772）**：バニラの `Heightmap.Types` の列挙順と
  仮定している。type id と種別名の対応の仮定は未確認のまま（偽クライアントはチャンクを復号しないため、
  統合テストでは検証されていない）。
- **Vehicle Move の onGround（768クライアント→770サーバー）**：**770段階の既存の
  不具合**。`handleClientPlayTo770` の Vehicle Move 分岐は素通しのため、768クライアントの
  onGround 無しペイロードが770サーバーへそのまま送られる。772のスコープ外のため
  直していない。772の To772 分岐では正しく直してある。
- **From770 の一般プレイ分岐に is768() が無い（770段階の既存の挙動）**：770サーバー＋
  768クライアントの一般プレイは落とされる。772では変更しない（772の From772 分岐には
  is768()／is769() を追加してある）。
- **ネイティブ772同士の再設定はセッションが追跡しない**：772同士は Login Success 後に
  即プレイ状態になり、セッションは再設定を追跡しない（771のネイティブ同士と同じ）。
  Start Configuration（0x6F）はそのまま中継され、設定フェーズの Custom Click Action
  （0x08）はプレイ状態の Chat Message（0x08）と番号が同じためバイト列のままサーバーへ
  届く（サーバーは設定状態で読むため正しく解釈する）。
- **creaking_heart の uprooted（771⇔772）**：771と同じ制限。771⇔772ではそのまま通す。
- **ポーションの分裂（771⇔772）**：771と同じ制限。`potion` 99 が `splash_potion` 99 と
  `lingering_potion` 100 に分裂したまま。771⇔772ではそのまま通す。
- **オオカミの鳴き声バリエーション・寒冷／温暖変種（772→771）**：wolf_sound_variant・
  cow_variant・pig_variant・chicken_variant は771・772ともあるためそのまま通す。
- **Player Info の showHat／listPriority の順番（771→770）**：771のときと同じ仮定
  （minecraft-data の順番）。
- **ボートの木の種類**：771と同じ制限。767のボートは771／772では常にオークのボートに、
  771／772のボートは767では常に（木の種類を問わず）ボートになる。
  variant（木の種類）をエンティティ種別に直す変換は未実装。
- **food の効果（767→768→769→770→771→772）**：金リンゴ等の食べたときの効果は768／769／770／771／772向けに
  落とす。767の food の効果は amplifier／duration を持たないため、768／769／770／771／772の
  `consumable` の apply_effects 形式へは変換できない。
- **custom_model_data（767→768→769→770→771→772）**：771と同じ制限。767の単一の数値を
  768／769／770／771／772の colors[0] に入れるだけのベストエフォート。floats／flags／strings は
  空になる。
- **Set Cooldown（772のグループ名）**：771と同じく、エンダーパール・コーラスフルーツ・
  ヤギの角の3件だけ対応し、それ以外のアイテムの Set Cooldown は落とす
  （クールダウンの表示だけの制限）。
- **レシピパケット（772⇔771）**：Craft Recipe Response／Request、
  Displayed Recipe はレシピIDの形式が変わり変換できないため落とす。
  手動クラフトは動く。
- **Declare Recipes／Statistics／Tags／World Event ID**：全方向で未対応。
  レシピ帳・タグが空になるだけで遊べる。
- **ブロックエンティティの crafter／trial_spawner**：パケットごと落とす。
  766→765方向では vault も落とす（1.20系と同じ）。test_block／test_instance_block は
  772にしか無く、771向けには落とす。
- **Entity Metadata の中身**：763向けは variant 系エントリを落とす。
  764では素通しのためスロット・ブロック状態・粒子の中身は未変換のまま残る。
  772にしかないもの（music_disc_lava_chicken 等）は古いサーバー向けに落とす・代替する。
- **Explosion の772専用パーティクル／サウンド指定**：既定演出に置き換わる
  （766→763方向と同じ）。
- **1.21.7の新要素で通せないもの（772サーバーに771以下のクライアントがいる場合）**：
  music_disc_lava_chicken は771以下のクライアントでは石に見える（アイテムのフォールバック）。
  lava_chicken のジュークボックスの歌は "11" になる。dennis の絵画は絵柄が既定になる。
  ロケーターバー・ダイアログ・Change Gamemode・Custom Click Action は
  770以下のクライアントでは表示されない・届かない（パケットを落とす）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  764／765／766／767／768／769／770／771／772＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **本物のゲームクライアントでの確認は未実施**：全100通り共通。手順書は
  `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／`PLAYTEST-1.20.5.md`／
  `PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／`PLAYTEST-1.21.4.md`／
  `PLAYTEST-1.21.5.md`／`PLAYTEST-1.21.6.md`／`PLAYTEST-1.21.7.md`。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの
  検証のみ。
- **1.21.9以降（773〜）は未対応**：プロキシは772より新しいプロトコルを
  ログイン時に断る。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。

"""

# 冒頭の紹介文に 1.21.7 を足す
intro_old = """  **1.21.6対応**：クライアント763／764／765／766／767／768／769／770／771 ×
  サーバー763／764／765／766／767／768／769／770／771の全81通りが参加できる（下の
  「1.21.6対応：全81通りの対応マトリクス」を参照）。
  Bedrockは6プロトコル × 内部9サーバーの全54通り。"""
intro_new = """  **1.21.6対応**：クライアント763／764／765／766／767／768／769／770／771 ×
  サーバー763／764／765／766／767／768／769／770／771の全81通りが参加できる（下の
  「1.21.6対応：全81通りの対応マトリクス」を参照）。
  **1.21.7対応**：クライアント763／764／765／766／767／768／769／770／771／772 ×
  サーバー763／764／765／766／767／768／769／770／771／772の全100通りが参加できる（下の
  「1.21.7対応：全100通りの対応マトリクス」を参照）。
  Bedrockは6プロトコル × 内部10サーバーの全60通り。"""
assert text.count(intro_old) == 1, "intro anchor not found"
text = text.replace(intro_old, intro_new, 1)

# 末尾の優先順位を更新する
tail_old = """**1.20.1はステップBで追加済み、1.20.2も追加済み、1.20.5／1.20.6も
追加済み、1.21／1.21.1も追加済み、1.21.2／1.21.3も追加済み、1.21.4も
追加済み、1.21.5も追加済み、1.21.6も追加済み**。
次は1.21.7（772）。"""
tail_new = """**1.20.1はステップBで追加済み、1.20.2も追加済み、1.20.5／1.20.6も
追加済み、1.21／1.21.1も追加済み、1.21.2／1.21.3も追加済み、1.21.4も
追加済み、1.21.5も追加済み、1.21.6も追加済み、1.21.7も追加済み**。
次は1.21.9（773）。"""
assert text.count(tail_old) == 1, "tail anchor not found"
text = text.replace(tail_old, tail_new, 1)

anchor = "## バニラとの結び方（TODO・方針）"
assert anchor in text
text = text.replace(anchor, SECTION + anchor, 1)
P.write_text(text, encoding="utf-8")
print("inserted 1.21.7 matrix section")
print("new rows added:", len(new_rows))
