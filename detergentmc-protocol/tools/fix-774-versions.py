#!/usr/bin/env python3
"""VERSIONS.md を 774（1.21.11）対応へ更新する。

- 冒頭サマリーに 1.21.11 行を追加（144通り、Bedrock 72通り）
- 「774〜未対応」3箇所を「775〜未対応」へ
- 末尾の「次は1.21.11（774）」を更新
- 末尾に「## 1.21.11対応：全144通りの対応マトリクス」節を追加
  （773表の11行を流用し、774列と774行を追加）
"""
import re
import sys

PATH = "/home/hatch/workspace/detergentmc/docs/VERSIONS.md"

with open(PATH, encoding="utf-8") as f:
    text = f.read()
orig = text

# --- 1. 冒頭サマリー ---
old = (
    "  「1.21.9対応：全121通りの対応マトリクス」を参照）。\n"
    "  Bedrockは6プロトコル × 内部11サーバーの全66通り。"
)
new = (
    "  「1.21.9対応：全121通りの対応マトリクス」を参照）。\n"
    "  **1.21.11対応**：クライアント763／764／765／766／767／768／769／770／771／772／773／774 ×\n"
    "  サーバー763／764／765／766／767／768／769／770／771／772／773／774の全144通りが参加できる（下の\n"
    "  「1.21.11対応：全144通りの対応マトリクス」を参照）。\n"
    "  Bedrockは6プロトコル × 内部12サーバーの全72通り。"
)
assert text.count(old) == 1, f"top summary anchor count={text.count(old)}"
text = text.replace(old, new)

# --- 2. 1511-1512行（1.21.7節の制限） ---
old = (
    "  1.21.7対応（下の節）で772を、1.21.9対応で773を追加済み。1.21.11以降（774〜）は\n"
    "  未対応：プロキシは773より新しいプロトコルをログイン時に断る。"
)
new = (
    "  1.21.7対応（下の節）で772を、1.21.9対応で773を、1.21.11対応で774を\n"
    "  追加済み。26.1以降（775〜）は未対応：プロキシは774より新しいプロトコルを\n"
    "  ログイン時に断る。"
)
assert text.count(old) == 1, f"1511 anchor count={text.count(old)}"
text = text.replace(old, new)

# --- 3. 「1.21.11以降（774〜）は未対応」2箇所（1728・1916行） ---
old = (
    "- **1.21.11以降（774〜）は未対応**：プロキシは773より新しいプロトコルを\n"
    "  ログイン時に断る。"
)
new = (
    "- **26.1以降（775〜）は未対応**：プロキシは774より新しいプロトコルを\n"
    "  ログイン時に断る。"
)
assert text.count(old) == 2, f"775 anchor count={text.count(old)}"
text = text.replace(old, new)

# --- 4. 末尾の優先順位メモ ---
old = "追加済み、1.21.6も追加済み、1.21.7も追加済み、1.21.9も追加済み**。\n次は1.21.11（774）。"
new = "追加済み、1.21.6も追加済み、1.21.7も追加済み、1.21.9も追加済み、1.21.11も追加済み**。\n次は26.1（775）。"
assert text.count(old) == 1, f"eof anchor count={text.count(old)}"
text = text.replace(old, new)

# --- 5. 773表の抽出 ---
marker = "## 1.21.9対応：全121通りの対応マトリクス（2026-11-12）"
idx = text.index(marker)
section = text[idx:]
lines = section.split("\n")
header_i = next(i for i, ln in enumerate(lines) if ln.startswith("| クライアント＼サーバー |"))
sep_i = header_i + 1
assert lines[sep_i].startswith("|---"), lines[sep_i][:40]
rows = []
i = sep_i + 1
while i < len(lines) and re.match(r"^\| \*\*\d{3}", lines[i]):
    rows.append(lines[i])
    i += 1
assert len(rows) == 11, f"773 rows={len(rows)}"
header = lines[header_i]
sep = lines[sep_i]
# クライアント番号（行頭）を抽出
row_client = [re.match(r"^\| \*\*(\d{3})", r).group(1) for r in rows]
assert row_client == ["763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773"], row_client

# --- 6. 774列のセル（既存11行のサーバー774列） ---
COL774 = {
    "763": "◎ 制限付き（統合テスト済み）。`ProxyIntegrationTest.fake763ClientJoinsRealVanilla12111ThroughProxy`（参加・スポーン・チャット・移動まで確認）。プロキシが774の Registry Data などを消費して 763 の Join Game に codec を合成。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす。Entity Metadataのvariant系を落とす",
    "764": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす",
    "765": "△ 制限付き（単体テストのみ・統合テストなし）。`ProxySession773Test.join773Against765RelaysJoinLevel`（参加フロー単体テスト）で確認。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす",
    "766": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序を並べ替え（dialog・jukebox_song は落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は766⇔774で変換できる。Declare Recipes／Tagsを落とす",
    "767": "◎ 制限付き（統合テスト済み）。`ProxyIntegrationTest.fake767ClientJoinsRealVanilla12111ThroughProxy`（参加・スポーン・チャット・移動まで確認）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は767⇔774で変換できる。Declare Recipes／Tagsを落とす",
    "768": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入）。Window Click を落とす（HashedSlot）。Set Creative Slot は768⇔774で変換できる。Declare Recipes／Tagsを落とす",
    "769": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は769⇔774で変換できる。Declare Recipes／Tagsを落とす",
    "770": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は770⇔774で変換できる。Declare Recipes／Tagsを落とす",
    "771": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリはそのまま通す）。Window Click を落とす（HashedSlot）。Set Creative Slot は771⇔774で変換できる。Declare Recipes／Tagsを落とす",
    "772": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（銅ゴーレム／マネキンはブタへ）。Window Click を落とす（HashedSlot）。Set Creative Slot は772⇔774で変換できる。Declare Recipes／Tagsを落とす",
    "773": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（槍・ノーチラス等は石・ブタへ）。Window Click を落とす（HashedSlot）。Set Creative Slot は773⇔774で変換できる。Declare Recipes／Tagsを落とす",
}

# --- 7. 774行のセル（クライアント774） ---
def t774(cell):
    return cell

ROW774 = {
    "763": "◎ 制限付き（統合テスト済み）。`ProxyIntegrationTest.fake774ClientJoinsRealVanilla1201ThroughProxy`（参加・スポーン・チャット・移動まで確認）。プロキシが Join Game の codec から設定フェーズを合成する（dialog レジストリは合成しない）",
    "764": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "765": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の形式と順序を変換。dialog レジストリは落とす。Window Click／Set Creative Slot を落とす（データコンポーネント）。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "766": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序を並べ替え（dialog・jukebox_song は落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は774⇔766で変換できる。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "767": "◎ 制限付き（統合テスト済み）。`ProxyIntegrationTest.fake774ClientJoinsRealVanilla121ThroughProxy`（参加・スポーン・チャット・移動まで確認）。設定フェーズを中継し Registry Data の順序と形式を変換。Window Click を落とす（HashedSlot）。Set Creative Slot は774⇔767で変換できる。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "768": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（pale_garden を挿入）。Window Click を落とす（HashedSlot）。Set Creative Slot は774⇔768で変換できる。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "769": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は774⇔769で変換できる。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "770": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリは落とす）。Window Click を落とす（HashedSlot）。Set Creative Slot は774⇔770で変換できる。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "771": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（dialog レジストリはそのまま通す）。Window Click を落とす（HashedSlot）。Set Creative Slot は774⇔771で変換できる。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "772": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（銅ゴーレム／マネキンはブタへ）。Window Click を落とす（HashedSlot）。Set Creative Slot は774⇔772で変換できる。Declare Recipes／Tagsを落とす。槍・ノーチラス等は石・ブタへ",
    "773": "△ 制限付き（単体テストのみ・統合テストなし）。設定フェーズを中継し Registry Data の順序と形式を変換（槍・ノーチラス等は石・ブタへ）。Window Click を落とす（HashedSlot）。Set Creative Slot は774⇔773で変換できる。Declare Recipes／Tagsを落とす",
    "774": "◎ 素通し（リレー・統合テスト済み。1.21.11で確認）",
}

# --- 8. 新しい表を組み立てる ---
def strip_trailing_pipe(line):
    assert line.endswith(" |"), line[-40:]
    return line[:-2]

new_header = strip_trailing_pipe(header) + " | 774（1.21.11） |"
new_sep = sep + "---|"  # sep は "|" で終わるので "---|" を足して1列増やす
new_rows = []
for r, c in zip(rows, row_client):
    new_rows.append(strip_trailing_pipe(r) + " | " + COL774[c] + " |")
servers = ["763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773", "774"]
row774 = "| **774（1.21.11）** | " + " | ".join(ROW774[s] for s in servers) + " |"
new_rows.append(row774)
table = [new_header, new_sep] + new_rows
for ln in table:
    # クライアント列 + サーバー12列 = 13セル
    assert ln.count("|") == 14, f"column count wrong: {ln.count('|')} in {ln[:80]}"

# --- 9. 新しい節 ---
section_text = """
## 1.21.11対応：全144通りの対応マトリクス（2026-11-12）

クライアント763／764／765／766／767／768／769／770／771／772／773／774 × サーバー（内部プロトコル）763／764／
765／766／767／768／769／770／771／772／773／774の全144通りが参加できる。1.21.9対応の121通りに、774クライアントと
774サーバーを加えたものである。見出し事例は「1.21.11のサーバー（内部774）に
1.20系・1.21クライアントが入る」「1.20系・1.21サーバーに1.21.11クライアントが
入る」こと。パケットIDは773と同一（0変更・0追加・0削除）のため、773の翻訳の多くが
そのまま流用できる（くわしくは `docs/PROXY-1.21.11.md`）。

凡例は1.20系のマトリクスと同じ（◎＝統合テスト済み、○＝素通しで統合テストなし、
△＝制限付き、×＝できない。現在×は無い）。

""" + "\n".join(table) + """

検証の区別（まとめ）：

- **統合テスト済み（66件）**：1.21.9対応の61件（上を参照）に加え、
  774関係の5件（774→12111、774→121、767→12111、763→12111、774→1201。
  `ProxyIntegrationTest` の5テスト。いずれも
  参加・スポーン・チャット・移動まで確認）。774→12111ではチャット checksum
  0x00 が実バニラ1.21.11に受け入れられることも確認済み。
- **単体テスト済み・統合テストなし**：774↔764／765／766／768／769／770／771／772／773は、
  `ClientAdapter` の全組み合わせ判定テストと
  翻訳子（`ChunkTranslator774Test`／`PlayTranslator774Test`）の単体テストで確認。
  774↔763は端同士の統合テスト済み（上の5件）。
- **素通し（リレー）・統合テストなし**：763→763、764→764、766→766。
  リレーはバイト列をそのまま通すため変換の誤りが入りようが無く、765→765と
  767→767と768→768と769→769と770→770と771→771と772→772と773→773と774→774の統合テストで経路自体は検証済み。
- **Bedrock→774**：`JavaBackendConnection774Test`（単体テスト）に加え、
  `BedrockMatrixIntegrationTest.matrixAgainst1_21_11`（Bedrock 6 プロトコル ×
  内部774の統合テスト。参加・スポーン・チャット・移動・チャンク・アイテム）で
  統合テスト済み（2026-11-12）。
  内部763〜773の66通りと合わせて72通り。なお `matrixAgainst1_21_9`／
  `matrixAgainst1_21_10` は773のビルドで失敗していた（AssertionFailedError）ため、
  774のビルドで通るかはフルクリーンビルドの結果で確認する。
- **実機でしか確認できないもの（全144通り共通）**：本物のゲームクライアントでの
  参加・見た目・操作感、本物のMojang認証サーバーでのonline-mode参加、
  リソースパックの「互換性あり」表示と適用、泡テクスチャの目での確認。
  手順書は `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／
  `PLAYTEST-1.20.5.md`／`PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／
  `PLAYTEST-1.21.4.md`／`PLAYTEST-1.21.5.md`／`PLAYTEST-1.21.6.md`／
  `PLAYTEST-1.21.7.md`／`PLAYTEST-1.21.9.md`／`PLAYTEST-1.21.11.md`。

### 773→774のレジストリ差分（2026-11-12、ViaVersion 対応表と実バニラ Registry Data で確認）

- **パケットID**：0変更・0追加・0削除。プレイ状態のIDは773と同一（`PacketIds.java` の
  774列と `mc-1.21.11-protocol.json` で確認）。773の翻訳はIDの面ではそのまま使える。
- **ブロック状態**：29,671件・同一（773と774で1件も変わらない）。
- **アイテム**：1,488→1,505件（+17）。木・石・銅・鉄・金・ダイヤ・ネザライトの槍7件、
  鉄・金・ダイヤ・ネザライト・銅のノーチラス防具5件、スポーンエッグ4件
  （nautilus／camel_husk／parched／zombie_nautilus）、ネザライトの馬鎧1件。
  挿入で後ろがずれる（例：music_disc.lava_chicken は773の1304→774の1316）。
- **エンティティ**：153→157件（+4）。camel_husk（774の20）、nautilus（88）、
  parched（97）、zombie_nautilus（152）。挿入で後ろがずれる（pig 97→100、
  ghast 56→57、happy_ghast 57→58、falling_block 50→51）。
- **音**：1,771→1,838件（+67）。削除は無い。
- **データコンポーネント**：96→104件（新規8件）。use_effects（774の5）、
  minimum_attack_charge（7）、damage_type（8）、attack_range（30）、
  piercing_weapon（38）、kinetic_weapon（39）、swing_animation（40）、
  zombie_nautilus/variant（94）。773の kind 5 は6へ、6〜26は9〜29へ、
  27〜33は31〜37へ、34〜86は41〜93へ、87〜95は95〜103へずれる。
- **エンチャント**：42→43件。lunge が774の21に挿入され、後ろが1つずれる
  （mending 22→23、wind_burst 41→42）。
- **エフェクト**：39→40件。BreathOfTheNautilus が774の39に末尾追加。
- **ダメージ種別**：49→50件。spear が774の37に挿入され、後ろが1つずれる
  （spit 37→38、wither_skull 48→49）。
- **統計**：76→77件。nautilus_one_cm が774の22に挿入。
- **絵画 51件・ジュークボックス 21件・ダイアログ 3件・属性 35件・バイオーム 65件**は同じ。
- **メタデータ種別番号**：zombie_nautilus_variant が774の28に挿入され、
  humanoid_arm が774の38に末尾追加。773の28〜36は774で29〜37になる。
- **pack_format**：リソース 69→75、データ 88→94（Minecraft Wiki の pack_format 表）。
  774用パックは `detergentmc-pack-1.21.11.zip`（12個目）。
- **バニラ1.21.11サーバーjarのSHA-1**：`64bb6d763bed0a9f1d632ec347938594144943ed`
  （`run/vanilla/server-1.21.11.jar`）。Java 21 が必要。

### まだできないこと（1.21.11対応・正直な一覧）

- **Window Click（774が関わる全方向）**：774の HashedSlot は成分のハッシュ値
  （i32）だけを持ち、元の Slot に戻せない。ハッシュの算法が未確認のため全方向で
  落とす（773⇔774も含む）。774クライアントが内部774以外のサーバーにいるとき、774サーバーにいる
  773以下のクライアントは、インベントリ操作（クリック）ができない。Set Creative
  Slot は UntrustedSlot→Slot で変換できる（774⇔773／772と、774クライアント→771／770／769／768／767／766サーバー。
  771以下のクライアント→774サーバーは鎖が無いため落とす。773段階と同じ）。
- **Set Creative Slot（774クライアント→内部763／764／765）**：774の UntrustedSlot は
  データコンポーネント形式のため変換できず落とす（773と同じ制限）。
- **ロケーターバー／tracked_waypoint（774）**：tracked_waypoint（0x88）は773・774にしか
  無いため、772以下のクライアントには送らない。774クライアントが772以下のサーバーに
  いるときはロケーターバーは表示されない。773⇔774ではそのまま通す。
- **ダイアログ（774）**：show_dialog／clear_dialog（プレイ 0x8A／0x89、設定フェーズ
  0x12／0x11）は771・772・773・774にしかないため、770以下のクライアント・サーバーには送らない。
  771⇔772⇔773⇔774ではそのまま通す。**minecraft:dialog レジストリは770サーバーからは来ないため、774クライアントへ
  合成注入しない**。実バニラクライアントが欠けたレジストリを内蔵既定で補うかは
  未確認。ダイアログを表示するサーバー側の仕組み（Bubble のダイアログAPI）は未実装。
- **Change Gamemode（774）**：change_gamemode（0x04）は771・772・773・774にしかないため、770以下のサーバー
  には送らない。774クライアントが770以下のサーバーにいるときはゲームモード変更の
  サーバー行きパケットが届かない。773⇔774ではそのまま通す。
- **Custom Click Action（774）**：custom_click_action_to_server（プレイ 0x41・設定フェーズ
  0x08）は771・772・773・774サーバーへ通すが、770以下のサーバーには送らない。774クライアントが770以下の
  サーバーにいるときは、ダイアログやサーバーリンクのクリックがサーバーに届かない。
- **エンティティのレジストリ順序（773→774）**：Registry Data の entity_type は
  並べ替えの対象外のため、773サーバーから来た153件（pig=97）のまま774クライアントに
  届く。Spawn Entity の種別ID自体は774へ翻訳される（pig=100）ため、実機クライアントでは
  エンティティの表示がずれる可能性がある。773で未解決のまま引き継ぐ。`PlayTranslator774Test` に素通しを
  明記するテストを入れてある。同じ問題はエンティティ順が変わる既存のペア
  （767→768、769→770、770→771、772→773）にもある。
- **槍・ノーチラス等（774→773）**：木・石・銅・鉄・金・ダイヤ・ネザライトの槍7件は
  773に無いため、773向けには石（1）に落とす。nautilus／camel_husk／parched／
  zombie_nautilus は773に無いため、773向けにはブタ（97）に落とす。ノーチラスの
  防具5件とネザライトの馬鎧は石に落とす。
- **1.21.11の新要素で通せないもの（774サーバーに773以下のクライアントがいる場合）**：
  槍・ノーチラスの防具は773以下のクライアントでは石に見える（アイテムのフォールバック）。
  ノーチラス・camel_husk・parched・zombie_nautilus はブタに見える。
  新しいデータコンポーネント8件（use_effects 等）は落とす。Entity Metadata の
  zombie_nautilus_variant／humanoid_arm エントリは落とす。エフェクト
  BreathOfTheNautilus（39）は774→773で素通しのため、773クライアントには
  未知のエフェクトIDが届く（表示は未確認）。デバッグ系パケット
  （debug_block_value 等）は旧クライアント向けに落とす。
- **Code of Conduct（774）**：既定のサーバーは送ってこないため通常は問題にならない。
  CoC を送るサーバーに古いクライアントが入った場合、プロキシは CoC を落とすだけで
  accept を代理で返さないため、サーバーが accept を待つ設計なら設定フェーズで止まる
  （未確認の制限。`docs/PROXY-1.21.11.md` の「Code of Conduct」を参照）。
- **Declare Recipes／Statistics／Tags／World Event ID**：全方向で未対応。
  レシピ帳・タグが空になるだけで遊べる。
- **ブロックエンティティの crafter／trial_spawner**：パケットごと落とす。
  766→765方向では vault も落とす（1.20系と同じ）。shelf／copper_golem_statue は
  773・774にしか無く、772向けには落とす。
- **Entity Metadata の中身**：763向けは variant 系エントリを落とす。
  764では素通しのためスロット・ブロック状態・粒子の中身は未変換のまま残る。
  774にしかないもの（zombie_nautilus_variant／humanoid_arm）は古いサーバー向けに落とす。
- **Explosion の774専用パーティクル／サウンド指定**：既定演出に置き換わる
  （766→763方向と同じ）。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  764／765／766／767／768／769／770／771／772／773／774＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info Updateにプロパティを載せない）。
- **本物のゲームクライアントでの確認は未実施**：全144通り共通。手順書は
  `docs/PLAYTEST-1.20.1.md`／`PLAYTEST-1.20.2.md`／`PLAYTEST-1.20.5.md`／
  `PLAYTEST-1.21.md`／`PLAYTEST-1.21.2.md`／`PLAYTEST-1.21.4.md`／
  `PLAYTEST-1.21.5.md`／`PLAYTEST-1.21.6.md`／`PLAYTEST-1.21.7.md`／`PLAYTEST-1.21.9.md`／
  `PLAYTEST-1.21.11.md`。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの
  検証のみ。
- **764サーバー→774クライアントはプレイ状態のパケットが765形式のまま送られる**：
  既存の制限（773クライアントでも同じ）。
- **774で直した既存の不具合（記録）**：`handleServerPlayFrom770` の is772() 分岐が
  一段短かった問題（770→771のレジストリ付け替えが飛んでいた）を `handleServerPlay770To771` 経由の
  二段に直した。`handleServerPlayFrom766` の Disconnect／Respawn に770／771／772／773／774分岐を
  足した。769向け `reorderRegistries` の対象定数が770／771／772になっていたのを769に直した。
  `handleClientConfiguration774` の重複した `isServer772()` を直した。770の Disconnect 0x1C／
  Respawn 0x4B を From774／From773／From772／From771 の4箇所で直した。
- **773報告の訂正（記録）**：773報告で「From769／768 の Respawn で is770()→0x4B（正しくは0x4C）」
  と書かれていたが、770の Respawn は0x4Bが正しい（`PacketIds.java` と `mc-1.21.5-protocol.json` で確認）。
  不具合ではない。
- **repair_cost の写し間違い（773⇔774では直した・772⇔773では残る）**：773の kind 16 は
  repair_cost（varint）だが、770〜773の `translateComponent*To*` では
  enchantment_glint_override（bool）と書かれていた。774向け生成器で773⇔774の両方向を
  直した（`ChunkTranslator774Test` で確認）。772⇔773のコードはまだ直していない
  （repair_cost が2以上のスロットが壊れる。テストが無いため未確認）。
- **26.1以降（775〜）は未対応**：プロキシは774より新しいプロトコルを
  ログイン時に断る。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
"""

if not text.endswith("\n"):
    text += "\n"
text += section_text

assert text != orig
with open(PATH, "w", encoding="utf-8") as f:
    f.write(text)
print("OK: VERSIONS.md updated")
print(f"new section table rows: {len(table)} (header+sep+12 rows)")
