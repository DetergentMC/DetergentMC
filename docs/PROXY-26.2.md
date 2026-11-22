# 26.2クライアント → 1.19系・1.20系・1.21系・26.1サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
26.2クライアント（プロトコル776）は、内部プロトコルが
759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775／776のどのサーバーにも参加できます。内部776のときは
素通し（リレー）、それ以外は上向き・下向き変換です。

26.2は「Chaos Cubed」（データ4903、pack_format リソース88・データ107）。
ホットフィックス無し。バニラSHA-1は
`823e2250d24b3ddac457a60c92a6a941943fcd6a`。
775→776のパケットID変更は**0件**（唯一の違いは play toServer 0x3e の改名
`spectate_entity` → `spectator_action`、同じ位置）。ペイロード差分は
**4件だけ**（本物のバニラ26.2のjarのjavapで確認済み）。

775の仕組みは `docs/PROXY-26.1.md`、逆向きの考え方は
`docs/PROXY-REVERSE.md` を参照してください。

全324通り（クライアント759〜776 × サーバー759〜776）の対応マトリクスは
`docs/VERSIONS.md` の「26.2対応」を参照してください。

## 775→776のペイロード差分（4件）

| # | パケット | 776の違い | プロキシの扱い |
| --- | --- | --- | --- |
| 1 | Join Game（`ClientboundLoginPacket`） | 末尾に `boolean onlineMode` が1つ増える（12番目の引数） | 776→775以下：末尾のboolを落とす。775以下→776：`ProxySession.onlineMode` フィールドの実効値を付け足す |
| 2 | Login Success（`ClientboundLoginFinishedPacket`） | 末尾に `UUID sessionId` が増える | 776→775以下：落とす。775以下→776：ランダムなUUIDを合成して付け足す |
| 3 | Teams（`ClientboundSetPlayerTeamPacket$Parameters`） | ワイヤ順が変わる。775は `displayName, options(1バイト), nametagVisibility, collisionRule, color(ChatFormatting の varint), playerPrefix, playerSuffix`。776は `displayName, playerPrefix, playerSuffix, nametagVisibility, collisionRule, color(Optional<TeamColor> の varint、0=無し・それ以外は序数+1と推定), options(1バイト)`。color は Optional になり、options は int から byte に（ワイヤ上は従来通り1バイト） | 775には Teams 専用の翻訳子が無かった（IDだけの素通し）ため、`ProxySession.java` 内に private static メソッドとして新規実装（`translateTeams775To776`／`translateTeams776To775`）。color の写像：775→776は `RESET(21)→0、それ以外は v+1`。776→775は `0→21、それ以外は v-1` |
| 4 | SpectatorAction（`ServerboundSpectatorActionPacket`、play toServer 0x3e） | 776は varint 1つで、0=観戦停止（空）、それ以外は `entityId+1`。775の `spectate_entity` は varint の `entityId` そのもの | 776→775：0ならパケットを落とす（送らない）、0以外は `entityId=v-1` の `spectate_entity` として送る。775→776：`entityId` を varint `entityId+1` の `spectator_action` として送る。`From776`／`To776` のディスパッチで処理する。0x3e を `spectate_entity` として扱ってはいけない（776では存在しない） |

設定フェーズは775と同形（configuration 20/10パケット、順序・件数とも同一）。

## 実装場所（`ProxySession.java`）

- Join Game：`handleServerJoinGame776` の is775分岐（0x31へ `stripJoinGameOnlineMode` を送る）と `handleServerJoinGame775` の is776分岐（`appendJoinGameOnlineMode(packet.payload(), onlineMode)` を送る）。ヘルパーは `stripResourcePackResponseUuid` 付近の `stripJoinGameOnlineMode`／`appendJoinGameOnlineMode`。
- Login Success：`handleServerLogin` の case 0x02 で776の sessionId を `rewriteLoginSuccessWithProperties` の**前**に剥がす（剥がさないと末尾バイトで例外になり認証済みログインが切断される）。`adjustLoginSuccessPayload` で is776() クライアント向けにランダムUUIDを付け足す。ヘルパーは `stripLoginSuccessSessionId`。
- Teams：`to776ServerPacket` と `to775ServerPacketFrom776` の `case PLAY_TEAMS`。ヘルパーは `translateTeams775To776`／`translateTeams776To775`。
- SpectatorAction：`to776ClientPacket` と `to775ClientPacketFrom776` の `if (packet.id() == 0x3e)`（汎用パスより前。汎用パスは他方にIDが無いパケットを落とすため）。
- リソースパック776：フィールド `pack776`、アクセサ `setPack776`／`pack776()`、`packOverrideForClient()` の V26_2分岐（先頭は `pack776 != null ? pack776 : pack775 … : pack766` の連鎖）。生成器のパッチが作るので、手で先に入れてはいけない（パッチのアンカーが壊れる）。
- 設定フェーズのディスパッチ：`handleServerConfiguration` の776節と `handleClientConfiguration` の is776() 分岐。ハンドラ本体は別担当（未定義のものは下の「未解決の問題」を参照）。

## テスト

`ProxySession776Test.java`（`ProxySession773Test.java` を写したもの）。
Join Game（onlineMode の落とし／付け足し、true／false 両方）、Login Success
（sessionId の落とし／付け足し）、SpectatorAction（0の破棄、entityId±1）、
Teams（775→776の並べ替えと color、776→775の逆変換、色なし↔RESET の両方向）の
13テスト。`ChunkTranslator776Test.java`（24件）、`PlayTranslator776Test.java`
（16件）も追加済み。実行は分割検証（`~/workspace/detergentmc/split-verification-776/protocol/` に
XMLをコピー）。2026-11-15の実行結果：プロトコルモジュール全体 **2,327件・0失敗・
0スキップ**（776新規92件を含む）。Bedrock→776は `JavaBackendConnection776Test.java`
18件（単体実行で0失敗。Join Game の次元読み取り・チャット署名チェックサム・
Spawn Position の3件は旧コードで失敗するネガティブコントロール済み）。

## 775→776のレジストリ差分（vv-26.2.json と本物の26.2のjarのダンプで確認）

| 種類 | 775 | 776 | 差分 |
| --- | --- | --- | --- |
| ブロック状態 | 29,873 | 32,366 | +2,493（シンナバー／硫黄系） |
| ブロック | 1,168 | 1,196 | +28 |
| アイテム | 1,506 | 1,537 | +31（music_disc_bounce など） |
| エンティティ | 157 | 158 | +1（sulfur_cube が130番に挿入） |
| 音 | 1,902 | 1,968 | +66 |
| パーティクル | 117 | 125 | +8（geyser 系4、noxious_gas 系2、sulfur_bubbles、sulfur_cube_goo） |
| 属性 | 35 | 40 | +5（air_drag_modifier、below_name_distance、bounciness、friction_modifier、name_tag_distance） |
| データコンポーネント | 110 | 111 | +1（sulfur_cube_content） |
| 統計／メニュー／レシピシリアライザ／スロット表示／環境属性 | 77／25／21／11／48 | 同じ | 0 |
| エンチャント／エフェクト | 43／40 | 同じ | 0 |
| バイオーム | 65 | 66 | +1（sulfur_caves が53番に挿入） |
| ダメージ種別 | 50 | 51 | +1（sulfur_cube_hot が42番に挿入） |
| ジュークボックス | 21 | 22 | +1（bounce が4番に挿入） |
| ブロックエンティティ | 49 | 49 | 件数は同じだが bed（775=25）が無くなり potent_sulfur（776=48）が末尾に追加 |
| argumenttypes | 57 | 57 | 件数は同じだが `color` → `team_color` に改名 |
| sulfur_cube_archetype | （無し） | 12 | 新設のレジストリ |

775の項目が776で欠けるものは7種の数値レジストリに無い（bed はブロックエンティティの
並び替えとして処理）。ブロック状態・ブロック・アイテムは共通項目の並びも変わるため、
名前ベースの写像で対応している。

## Bedrock→776（JavaBackendConnection 経由）

- Join Game は末尾の onlineMode（1バイト）を落としてから 767 形式へ変換する。
  落とさないと `translateJoinGame768To767` が末尾バイトで失敗し、
  `parseJoinGame766` も余分なバイトで失敗して、次元がオーバーワールドに
  フォールバックする（警告ログだけ出て参加自体は続くため、単体テストでは
  ネザーの次元（minY 0・高さ 128）が届くことを確かめる専用テストを入れた）。
  onlineMode=true の Join Game でも処理が続くことを別テストで確認済み。
- Login Success と Teams は JavaBackendConnection では読まない（Player Info Update の
  ビットセットだけ読む）ため、776でも特別な扱いは要らない。
- チャット署名チェックサム、Spawn Position の RespawnData、Client Information の
  particleStatus、属性番号（movement_speed 26、camera_distance 11）などに 776 条件を追加。
- BedrockChunkTranslator／BedrockEntityTranslator は 776→765 の正規化
  （ブロック状態・アイテム・エンティティ・パーティクル・属性名）。
  776の属性名は 775 の接頭辞付き名ではなく裸のレジストリ名
  （air_drag_modifier 0、below_name_distance 6、bounciness 9、
  friction_modifier 17、name_tag_distance 27）。

## 内部776サーバーの起動確認

2026-11-15に本物のバニラ26.2のjar（`run/vanilla/server-26.2.jar`）を
Temurin 25.0.5+8 で単独起動し、`Starting minecraft server version 26.2` と
`Done (21.830s)!` まで到達することを確認済み。プロキシ経由の起動は
Stage E の統合テスト（`ProxyIntegrationTest` の5セル）で確認予定。
なお `VanillaServerHost.javaBinaryFor` は 26.2 を Java 25 必須リストに
入れていなかったため、Stage D の抜けとして追加済み（コミット `7244659`）。

## 未解決の問題（2026-11-15時点）

- 設定フェーズのハンドラ8件は別担当が追加済み（`handleServerConfiguration776From774`／
  `776From773`／`776From772`／`776From771`、`774From776`／`772From776`／`771From776`／
  `770From776`）。`ChunkTranslator.java` の duplicate case label も修正済み。
- 既存バグ（775からあるもの。776の作業では直していない）：
  `handleClientConfiguration775` の Custom Click Action 連鎖が `isServer775()` を
  含まないため、ネイティブの775＋775で Custom Click Action が落とされる。
  コメントは「771・772・773・774・775サーバーへはそのまま送り」と書いてあるのに
  連鎖は `isServer774()` で終わっている。776版は同じ罠を踏まないよう
  `|| isServer776()` を手で足した。
- 775の潜在バグ（776では回避済み・775側は未修正）：`mapMetadataType774To775` が
  774の wolf／chicken／zombie／global_pos／painting の型番号を誤って返す。
  776の翻訳子は775派生に書き直して回避したが、775側そのものは本作業の範囲外のため
  直していない。親の判断が必要。
- 本物のゲームクライアントでの確認は未実施（全バージョン共通）。

## 776パッチを写すときの罠

775の `docs/PROXY-26.1.md`「775パッチを写すときの罠」に加え、776で新たに見つかったもの。

### 12. 生成器の shift() は 772 をずらさない（frozen-772）

`shift()` は 775→776、774→775、773→774 をずらすが、772→773 はずらさない
（`772To773`→`772To774` の崩壊を防ぐプレースホルダ保護のため、772 が凍結される）。
そのため、772 を含む識別子が1段ずれただけの、存在しない名前になる：

- `to772ServerPacketFrom773` → `to772ServerPacketFrom774`（`handleServerPlayFrom776` の
  is767／is766分岐。コメントの「776→775→774→773→772→…」通り as773 ステップが欠落する）。
  直し方：`as773 = to773ServerPacketFrom774(as774)`（null チェック付き）を挿し、
  `as772 = to772ServerPacketFrom773(as773)` にする。検出方法：全
  `toNNNServerPacketFromMMM` 呼び出しについて `NNN == MMM-1` を確かめる
  （1段ずつの変換しか存在しない）。
- `772To775` → `772To776`（`773To776` にはならない）。`773To775` → `774To776`。
  結果として `handleServerPlay773To776` が生成されず、From772の is776分岐が
  未定義参照になる。直し方：775のときの `SERVER_PLAY_775_TO` と同じ要領で手で補う
  （`to774 = handleServerPlay773To774(packet); return handleServerPlay774To776(to774…)` の三段）。
  検出方法：コンパイルエラー一覧と、`handleServerPlay*To776` の定義一覧を
  `*To775` の定義一覧と突き合わせる（776では773だけ欠ける）。

### 13. FIX の部分置換（fragment sub）は括弧を壊す

FIX の old／new が式の一部分だけ（連鎖の途中2行など）だと、ネストを1段増やす
置換で開き括弧だけ増えて閉じ括弧が足りなくなる。776で2件起きた：

- CONFIG のネストした Registry Data 式：2行の fragment sub が開き括弧を1つ増やし
  閉じを増やさなかった。直し方：式全体（`translateRegistryData774To775(` の行から
  `;` まで）を old／new にし、深い行を +8 インデントして末尾に `)` を1つ足す。
- CLIENT_PLAY_776 の SetCreativeSlot 連鎖：`772To774` → `773To774(772To773(…))` の
  展開で開き括弧が1つ増えたが、連鎖末尾の閉じ括弧は sub の範囲外だったため1つ不足し
  `')' expected` になった。直し方：末尾の行に `)` を1つ足す。
  775のときの `gen-775-proxysession.py:555` は sub の old／new に連鎖末尾の
  `packet.payload())))))));` まで含めており、この罠を踏んでいない。

原則：ネストを増やす FIX sub は old／new に式全体（末尾の閉じ括弧と `;` まで）を
含めること。含められない場合は手直し後に括弧の数を数え、コンパイルで確かめること。

### 14. `toNNNClientPacket` の明示IDチェックは shift() ではずれない

罠2の別例。`to776ClientPacket`／`to775ClientPacketFrom776` の中の
`if (packet.id() == 0x11)`／`0x37` といった明示IDチェックは、生成器が775のIDのまま
写す（774のIDが残る）。776で見つかった実バグ3件：

- `to776ClientPacket` の Window Click チェック `0x11` → `0x12` が正しい
  （774は0x11、775／776は0x12）。生成物のままだと775の Enchant Item（0x11）を
  Window Click と誤って落とし、本物の Window Click が汎用パスへ素通しする。
- `to776ClientPacket` の Set Creative Slot チェック `0x37` → `0x38` が正しい
  （774は0x37、775／776は0x38）。生成物のままだと775の Update Command Block Minecart
  （0x37）をスロットと誤訳し、本物の Set Creative Slot が `ChunkTranslator` 変換を
  迂回する。
- `to775ClientPacketFrom776` の Set Creative Slot 送出 `0x37` → `0x38` が正しい。
  生成物のままだと776の Set Creative Slot が775サーバーに Update Command Block Minecart
  として届く。

検出方法：`packet-ids-776.json` の id775／id776 と、To776／From776 の全 ID を
1件ずつ突き合わせる。Use Entity／Enchant Item の case が `to776ClientPacket` に
無いのは正しい（775／776で同ID・同形式のため汎用パスでよい）。

### 15. `FIX_EDITS_FULL` の old は先行する編集の適用後の状態で書く

FIX_EDITS_FULL の各 sub はリストの順に適用される。`chatGlobalIndex field` の old は
pack フィールド編集**前**の状態で書かれていたが、pack フィールド編集（index 3）が
先に走る（index 30）ため、old が合わず失敗する。直し方：old に pack776ブロックを
含めた、編集適用後のテキストを書く（775のパッチも `chatGlobalIndex` の old に
pack775ブロックを含めていた）。

### 16. Respawn／Disconnect の手直しは生成器が拾わない

生成器は From774／From766 の Respawn に is776分岐を作らない（775のときも
From774の Respawn is775分岐はコミット `82cd300f` で、From766のものはコミット
`56d7afac` で手で復旧している）。776でも同じ2箇所に is776 Respawn 分岐（0x52）を
手で足した。同じく Disconnect 連鎖 `if (is773() || is774() || is775())` は
From766／From775／From773 の3箇所にあり、生成器は is776 を足さないため手で
`|| is776()` を足し、コメントも「773／774／775／776クライアント」に直した。
検出方法：パッチ適用後に `branch-count-776.py` でメソッドごとの isXXX() 出現回数を
HEAD と数え比べる（罠1）。ただし数え比べは**回数**しか見ないため、Respawn のような
「分岐そのものが無い」欠落は回数に現れない場合がある。Respawn／Disconnect のような
既知の手直し箇所は、775のコミット履歴と突き合わせて個別に確認すること。

### 17. config ハンドラの生成漏れは「775の手直し分」が原因

生成器の CONFIG_776 ブロックには `776From775`／`776From770`／`776From769`／
`776From768`／`776From767`／`776From766` しかない。`776From774`／`776From773`／
`776From772`／`776From771` が無いのは、775版の対応する4メソッド
（`775From773`／`775From772`／`775From771`／`775From770`）が775のパッチ生成後に
手で追加されたもので、パッチファイルに存在しないため shift の元が無いからである。
逆方向も同じで、`774From776`／`772From776`／`771From776`／`770From776` が無い
（`775From776` は `774From775` の shift で出来ている）。
これらは設定フェーズ担当の作業として、ディスパッチが参照する名前を最終報告に
列挙すること。生成器で無理に作ろうとしないこと。

### 18. Custom Click Action の連鎖はネイティブを見落とす

`handleClientConfiguration776` の Custom Click Action 連鎖は
`isServer772() || isServer771() || isServer772() || isServer773() || isServer774() || isServer775()`
で終わり、`isServer776()` が無かった。コメントは「771・772・773・774・775・776サーバーへは
そのまま送り」と書いてあるのに連鎖が1つ足りない。
`handleClientConfiguration` の先頭にネイティブの素通しは無く、776クライアント＋776サーバーでも
`handleClientConfiguration776` に到達するため、ネイティブの Custom Click Action が落とされる
実バグになる。直し方：`|| isServer776()` を足す。
なお `isServer772()` の重複は775版からの既存のもので、無害のためそのままにしている。
原則：設定フェーズのサーバー版リストは、そのハンドラ自身の版を含むこと
（ネイティブ到達を忘れないこと）。

### 19. 生成器の Respawn 編集名は1つずれている

ADD／FIX の編集名 `handleServerPlayFromNNN Respawn` が実際に変更するのは
`handleServerPlayFrom(NNN+1)` の Respawn である（NNN<775の場合）。例えば
`handleServerPlayFrom774 Respawn 775` は From774 ではなく From773 の Respawn を
変更する。12240行目の Respawn は `handleServerPlay774To773` ではなく
`handleServerPlayFrom773` の中にある。編集名だけを信じて場所を特定しないこと。

### 20. Down*BlockState の OVERRIDES が 64KB `<clinit>` 制限を超える

775の罠4（ComposedUp の分割）と同じ型の罠だが、776では Down*BlockState の
OVERRIDES が先に当たった。775の Down763 は 63,036 バイトで 3,348 件の上書きがあり、
制限（65,535 バイト）まで 2,499 バイトしか余裕が無かった。776→763 は 5,854 件の
上書きがあるため、763〜772 の10ファイルが `code too large` でコンパイルできない
（773／774 は 2,716 件で通る）。

直し方：`split-776-down-blockstate.py` で OVERRIDES を 2,500 件以下の塊に分割し、
部品クラス `RegistryTables776Down{s}BlockStateOverrides{A,B,C}`（26クラス）に置き、
本体クラスはフィールドを `RegistryRemapper.concatTables(...)` で定義する。
`concatTables(int[][]... parts)` は RegistryRemapper に追加済み（`invert()` の後ろ）。

注意：別クラスの static メソッドを非修飾で呼んではいけない。
`concatTables(...)` だけ書くとコンパイルできない（非修飾の呼び出しは
現在のクラス内でしか解決されない）。必ず `RegistryRemapper.concatTables(...)` と
修飾すること。置換で `static final int[][]` の接頭辞が重複しないことにも注意。
`verify-776-tables.py` は concat 形式を解決するように更新済み。

### 21. SoundNames の表も 64KB `<clinit>` 制限を超える

775では音名の表は 3,131 ペアで 60,038 バイトと制限内だったが、776では
3,989 ペアになり制限を超えた。直し方：`split-776-sound-names.py` で
A（17表・1,490ペア：776_TO_775〜776_TO_769 と小さい表）と
B（6表・2,499ペア：776_TO_768〜776_TO_764）に分割し、
RegistryRemapper の import と verify-776-tables.py の TABLES_FILES を A／B に更新する。
罠20と同じく、表が増えるたびに `<clinit>` のサイズを `javap -c` で測ること。
`insert-776-tables.py` のテンプレートに「776→763 のブロック状態は 32,366 件と
件数／上書きも 5,854 件ある」「776（26.1）」といった 776 専用の文言が
全 Down*BlockState の javadoc に紛れ込んでいたため、テンプレートを `{suffix}` と
件数なしの文言に直し、生成済みの16ファイルも修正した。テンプレートの文言は
次のバージョンで使い回すことを前提に書くこと。
