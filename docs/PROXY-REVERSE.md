# 逆向き対応：1.20.4クライアント → 1.20.2サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
これまでの上向き対応（763／764クライアント → 1.20.4サーバー）では
サーバー内部は常にプロトコル765でした。逆向き対応では
`minecraft-version: "1.20.2"` でサーバー自体を1.20.2で動かし、
**内部プロトコルが764**になります。プロキシはクライアントのプロトコルと
内部プロトコルを比べて振り分けます。くわしい参加パターン表は下の
「参加パターン表」を参照してください。1.20系の相互連携（内部763・766を
含む全16通り）は `docs/VERSIONS.md` の「1.20系の相互連携：全16通りの対応
マトリクス」と `docs/PROXY-1.20.5.md` を参照してください。

上向きの仕組みは `docs/PROXY-1.20.1.md`（763→765）と
`docs/PROXY-1.20.2.md`（764→765）を参照してください。

## 内部プロトコルの可変化（設計の要点）

- **決める場所は1か所だけ。** `ProtocolVersion.serverProtocolFor(String)` が
  `minecraft-version` から内部プロトコルを決めます（`"1.20"`／`"1.20.1"` → 763、
  `"1.20.2"` → 764、`"1.20.3"`／`"1.20.4"` → 765、`"1.20.5"`／`"1.20.6"` → 766、
  未知の文字列 → 765＋警告ログ）。
  `DetergentServer.serverProtocol()` がその値を保持し、プロキシ構築時に
  `ClientProxy` へ渡します（`DetergentServer.createClientProxy()`）。
  プロキシもセッションも、内部プロトコルを765と決め打ちしません。
  内部763も `ClientProxy` が受け付ける（2026-11-09に検証を広げた。
  くわしくは `docs/VERSIONS.md` の「内部763サーバーが動くようになった経緯」を参照）。
- **振り分けは比較だけ。** `ProxySession` はクライアントのプロトコルと
  内部プロトコルを比べ、同じなら素通し（リレー）、古ければ上向き変換、
  新しければ下向き変換に切り替えます。方向の判定にバージョン表は要りません。
- **リソースパックもクライアント基準。** `ResourcePackServer.packForProtocol(int)`
  と `ProxySession.packOverrideForClient()` はクライアントのプロトコルで
  出し分けます（765→pack_format 22、764→18、763→15、766→32）。内部バージョンで
  決めてはいけません（内部764でも765クライアントには22のパックを出す）。
  `server.properties` に書くパックだけは内部バージョンに合わせます
  （`DetergentServer.hostResourcePack()`：内部763なら763用パック、
  内部764なら764用パック、内部766なら766用パック、それ以外は765用パック。
  素通しのネイティブクライアントに内部バージョンに合うパックが届くようにするため）。
- **データパック形式も内部バージョンに合わせます。**
  `DatapackGenerator.packFormatFor`：1.20.2 → 18、1.20.4 → 26。

## 参加パターン表

内部プロトコルが764（`minecraft-version: "1.20.2"`）のとき：

| クライアント | プロトコル | 動き |
|---|---|---|
| 1.20.2 | 764 | 素通し（リレー）。内部と同じため変換なし |
| 1.20.4／1.20.3 | 765 | 下向き変換（765→764）。設定フェーズは764形式へ変換して中継 |
| 1.20.1 | 763 | 763→764の直接変換（合成表）。765を経由する二段変換はしない |
| 1.20.5／1.20.6 | 766 | 下向き変換（766→764）。設定フェーズは764形式へ変換して中継（Registry Data の形式変換あり） |

内部プロトコルが765（`minecraft-version: "1.20.4"`、既定）のときは従来通り：

| クライアント | プロトコル | 動き |
|---|---|---|
| 1.20.4／1.20.3 | 765 | 素通し（リレー） |
| 1.20.2 | 764 | 上向き変換（764→765）。設定フェーズは中継 |
| 1.20.1 | 763 | 上向き変換（763→765）。設定フェーズはプロキシが消費 |
| 1.20.5／1.20.6 | 766 | 下向き変換（766→765）。設定フェーズは中継（Registry Data の形式変換あり） |

内部プロトコルが763（`minecraft-version: "1.20.1"`）のとき：

| クライアント | プロトコル | 動き |
|---|---|---|
| 1.20.1 | 763 | 素通し（リレー）。内部と同じため変換なし |
| 1.20.2 | 764 | 下向き変換（764→763）。設定フェーズはプロキシが Join Game の codec から合成 |
| 1.20.4／1.20.3 | 765 | 下向き変換（765→763）。設定フェーズはプロキシが Join Game の codec から合成 |
| 1.20.5／1.20.6 | 766 | 下向き変換（766→763）。設定フェーズはプロキシが Join Game の codec から合成（Registry Data は766のエントリ配列形式） |

内部プロトコルが766（`minecraft-version: "1.20.5"`／`"1.20.6"`）のとき：

| クライアント | プロトコル | 動き |
|---|---|---|
| 1.20.5／1.20.6 | 766 | 素通し（リレー）。内部と同じため変換なし |
| 1.20.4／1.20.3 | 765 | 上向き変換（765→766）。設定フェーズは中継（Registry Data の形式変換あり） |
| 1.20.2 | 764 | 上向き変換（764→766）。設定フェーズは中継（Registry Data の形式変換あり） |
| 1.20.1 | 763 | 上向き変換（763→766）。設定フェーズはプロキシが766クライアント役として消費 |

全16通りの対応マトリクス（できる／制限付き・検証の区別つき）は
`docs/VERSIONS.md` の「1.20系の相互連携：全16通りの対応マトリクス」を参照。

## 下向き変換（765→764）の設計：反転による再利用

765→764の変換は、**上向き（764→765）の対応表を反転して使い回す**方針です。
手書きの新規対応表は作っていません。具体的には次の通りです。

- **レジストリ番号**：`RegistryRemapper` の上向き表（764→765）を起動時に
  反転し、765→764の逆引き表を作ります。ブロック状態26644件のうち
  変わるのは改名1件（`grass` → `short_grass`、番号2005は同じ）と
  新ブロック55種・2369状態のみで、残りは同一番号です。アイテム1312件は
  改名1件＋新57件、エンティティは breeze／wind_charge の2件増、
  音は54件増、パーティクルは6件増です（`docs/VERSIONS.md` の差分表を参照）。
  検証スクリプト `detergentmc-protocol/tools/verify-reverse-tables.py`
  で、反転表が上向き表と互いに逆向きであることを確認します。
- **チャンク・ブロック状態**：`ChunkTranslator` の `*764To765` メソッドと
  対になる `*765To764` メソッドは、同じ表を逆向きに引く実装です。
  パレットはシングル／間接／直接すべて変換します。
- **プレイ中の表示系**：`PlayTranslator` の `*764To765` メソッドと対になる
  `*765To764` メソッドは、テキストコンポーネントの向きだけが違います
  （765の NBT → 764の JSON 文字列、またはその逆）。
  `JsonToNbt` は JSON 文字列→NBT の変換を担います。
- **ID付け替え**：`PacketTranslator` の764／765対応表を逆向きに引きます。
  パケットIDの対応は方向で変わらないため、同じ表が両方向に使えます。

この方針により、**新しいバージョンを追加するときの新規対応表の作成が
不要になります**。上向きの表が1つあれば、下向きはその反転で済みます。
くわしい見通しは下の「将来のバージョン追加で開発量がどう減るか」を参照してください。

## 接続の流れ（765クライアント → 内部764、offline-mode）

1. ハンドシェイクのプロトコルVarIntを765→764に書き換えてバニラへ送ります
   （`ProxySession.resolveHandshake`：クライアントと内部が違う場合に
   内部プロトコルへ書き換える）。
2. Login Startは765と764で同形式（名前＋UUID）のため変換しません。
3. ログイン成功後、クライアントが Login Acknowledged（0x03）を送ると
   両方とも設定フェーズに入ります。バニラ（764）が送る Registry Data
   （0x05）は765形式（0x05）へ変換して中継します。内容はプロキシも保持します。
   Add Resource Pack は764の設定フェーズの Resource Pack Send（0x06、
   UUIDなし）を765の Add（0x07、UUID付き）に変換します。Tags は
   レジストリ番号がずれるため落とします。
4. Finish Configuration（0x02）を中継し、クライアントの応答（0x02）が
   バニラに届いた時点で両方ともプレイ状態に入ります。
5. バニラが送るJoin Game（764の0x29）は765の0x29へ変換します。764と765で
   形式は同じ（codec は両方とも設定フェーズに移っている）ため、
   ペイロードはそのまま通します（`PlayTranslator.translateJoinGame764To765`）。
6. スポーンは765式。Spawn Position（765は 0x54）が合図です。

### online-mode

765＋online-modeの参加も、763／764と同じくプロキシ認証を流用します
（既存のプロキシ認証の仕組みをそのまま使う。新規の認証経路は作っていません）。
プロキシがクライアントとの間でMojang認証を完結させ、認証済みUUID・名前で
内部バニラ（offline-mode・信頼済み接続）へ繋ぎます。くわしくは
`docs/PROXY-1.20.1.md` の「1.20.1クライアント（763、online-mode）」を参照してください。

### ステータス（サーバーリストのping）

765クライアントからのステータス要求はバニラへ中継し、応答JSONの
`version` だけを書き換えます（protocol 764→765、name "1.20.2"→"1.20.4"）。
内部のプロトコルによらず、クライアントの番号・表示バージョンにします。

## 送らない・落とすパケット（765向け・内部764）

内部764から765クライアントへ送るとき、変換が無いものは落とします。

- Declare Recipes（shaped レシピのフィールド順が違う）と Tags
  （レジストリ番号がずれる）。どちらも無くても遊べる
  （レシピ帳・タグが空になるだけ）。上向きの764向けと同じ方針。
- 設定フェーズの Tags（同じ理由）。

## 今回直したもの（第4担当・統合時に判明したバグ）

逆向き対応の統合時に、既存コードの前提が1.20.3からの仕様変更と食い違う
箇所が2つ見つかり、最小限の修正で直しました。

- **プレイ中の Disconnect（765→764）**：上向き処理では「764は765と同じ
  NBT のため変換なし」として素通ししていましたが、764の Disconnect は
  **JSON 文字列**です（テキストコンポーネントの NBT 化は1.20.3から。
  1.20.2以前は JSON 文字列）。素通しすると764クライアントが切断理由を
  誤読します。下向き処理（`translateDisconnect764To765` が JSON 文字列を
  読む）と矛盾していたことも、バグの証拠です。修正：上向きでは常に
  `translateDisconnect765To763`（NBT→JSON 文字列）を使い、764にも763にも
  JSON 文字列を送るようにしました。テスト
  `ProxySession764Test.playDisconnectIsTranslatedToStringFor764` を
  更新しました。
- **プレイ中の Disconnect（764→763、内部764のとき）**：同じ誤った前提で
  `translateDisconnect765To763`（NBT 読み）を764の JSON 文字列に掛けて
  いたため、先頭バイトをタグ種別と誤読して例外になり、パケットが落とされて
  いました。修正：764も763も JSON 文字列のため、そのまま通すようにしました。
  テスト `ProxySessionInternal764Test.up763PlayDisconnectPassesThroughAsJsonString`
  を追加しました。
- **スロットのNBT読み（`ExtraPlayTranslator.skipSlot`／`copySlotRaw`）**：
  スロットの NBT を bool＋NBT として読んでいましたが、実際のワイヤ形式は
  bool なしです（名前なし NBT そのもので、先頭1バイトが 0x00（TAG_End）
  なら「無し」。wiki.vg の Slot Data、2024-02版の旧形式）。
  NBT 付きスロットでは型バイトを bool と誤読して1バイトずれ、後続が誤読
  されていました（NBT 無しの場合だけ偶然同じ結果になっていた）。
  影響範囲は Advancements のアイコン（`copySlotRaw`）と Explosion の
  アイテム粒子（`skipSlot`）です。修正：`readAnonymousNbt()` だけを読む
  ようにし、同じ読み方の `ChunkTranslator` のスロット処理と一致させました。
  テスト `ExtraPlayTranslatorTest.skipSlotWithNbtConsumesExactlyOneSlot`／
  `skipSlotWithoutNbtAndEmptySlot`／`advancementsIconWithNbtIsCopiedVerbatim`
  を追加しました。旧実装では新しいテスト2件が失敗することを確認済みです。

## まだできないこと（正直な一覧）

- **763↔764のプレイ・ペイロード変換は実装済み（2026-11-09に解消）。**
  1.20系の相互連携の作業で `*764To763`／`*763To764` の変換メソッドを実装し、
  内部764のとき763クライアント向けに NBT 付きパケット（Block Entity Data／
  Set Slot／Window Items／Entity Equipment／Trade List／Entity Metadata など）を
  変換して送るようになった（`ExtraPlayTranslator`／`PlayTranslator`）。
  統合テスト `ProxyIntegrationTest.fake763ClientJoinsRealVanilla1202ThroughProxy`
  で確認済み。以下に残る制限は引き続き未対応。
- **766クライアントの古いサーバーでの Window Click／Set Creative Slot**：
  766のスロットはデータコンポーネント形式のため変換できず落とす。
  766クライアントが内部763／764／765のサーバーにいるときはインベントリ操作が
  できない（くわしくは `docs/PROXY-1.20.5.md` を参照）。
- **Declare Recipes／Tags**：上向き・下向きとも落とします（上の節を参照）。
  Statistics／World Event ID も未対応です。
- **ブロックエンティティの crafter／trial_spawner**：パケットごと落とします。
- **Entity Metadata の variant 系**：763向けはエントリごと落とします。
  764では素通しのため、スロット・ブロック状態・粒子の中身は未変換のまま残ります。
- **Explosion の765専用パーティクル／サウンド指定**：既定演出に置き換わります。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  上向きと同じ制限です。
- **本物のゲームクライアントでの確認は未実施**：下の「検証の範囲」を参照。
- **本物のMojang認証サーバーでの確認は未実施**：偽セッションサーバーでの
  検証のみです。
- **1.16.5／1.12.2／1.8.8は未対応**：プロキシは763・764・765・766以外を
  ログイン時に断ります。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  ありますが、パックを置く場所はまだ決まっていません。既定はloopback配信です。

## 検証の範囲

### 検証済み（プロトコルレベル）

- **統合テスト**：生ソケットの偽765クライアントで本物のバニラ1.20.2子プロセスに
  対し、プロキシ（内部764）経由で参加→Join Game受信→スポーン→チャット送信
  （バニラログに `Rin` のチャット）→移動送信まで確認
  （`ProxyIntegrationTest.fake765ClientJoinsRealVanilla1202ThroughProxy`）。
  既存の統合テスト3件（763・764・765が1.20.4サーバーに参加）も回帰確認済みで、
  壊れていません。1.20系の相互連携の作業で統合テストは7件になった
  （766→763：`fake766ClientJoinsRealVanilla1201ThroughProxy`、
  766→764：`fake766ClientJoinsRealVanilla1202ThroughProxy`、
  763→764：`fake763ClientJoinsRealVanilla1202ThroughProxy` を追加）。
  全16通りの対応マトリクスは `docs/VERSIONS.md` を参照。
- **単体テスト**：`RegistryRemapperUp764Test`／`ChunkTranslatorUp764Test`／
  `PlayTranslatorUp764Test`／`ExtraPlayTranslatorUp764Test`／
  `ClientAdapterCrossVersionTest`／`ProxySessionInternal764Test`／
  `ResourcePackSelectionTest`／`ProtocolVersionTest`／`JsonToNbtTest`
  （逆向きの変換・振り分け・出し分け）。
  検証スクリプト `detergentmc-protocol/tools/verify-reverse-tables.py` で
  反転表の整合性を確認します。
- **配線テスト**：`ServerProtocolWiringTest` で、DetergentServer から
  ClientProxy への内部プロトコル（764／765）と765用パックの受け渡し、
  およびバニラに渡すパック（内部764なら764用、内部765なら765用）を
  確認します。
- **サーバー自体の実起動**：`minecraft-version: "1.20.2"` でバニラ1.20.2
  サーバーが `Done (89.395s)!` まで起動することを別ディレクトリ
  （`/tmp/detergentmc-1202-boot`）で確認済み（2026-11-09）。
  データパックは pack_format 18 で警告なし、終了コード0。
  くわしくは `docs/PLAYTEST-1.20.2.md` の「1.5」を参照。

### 未検証（実機でしか確認できないもの）

- 本物の1.20.4ゲームクライアントでの1.20.2サーバーへの参加。
  手順書は `docs/PLAYTEST-1.20.2.md` の「8. 逆向き：1.20.4クライアントで
  1.20.2サーバーに参加する」を参照。
- 本物のMojang認証サーバーでの765＋online-mode参加。
- リソースパックがクライアントの画面で「互換性あり」と表示され、実際に
  ダウンロード・適用されること。泡テクスチャの目での確認。
- 次元移動・新ブロックの表示・レシピ帳の見た目などは、実機プレイテストで
  記録する（`docs/PLAYTEST-1.20.2.md`）。

## 将来のバージョン追加で開発量がどう減るか（見通し）

逆向き対応で得た仕組みにより、次のバージョン追加では開発量が減ります。
理由は次の3つです。

1. **対応表は1方向だけ作ればよい。** 新しいバージョン X と既存の最新版 Y の
   差分表（X→Y）を1つ作れば、逆向き（Y→X）はその反転で済みます。
   765→764で手書きの新規表を作らなかったのと同じやり方です。
   新バージョン追加時の「新規対応表の作成」が、片方向分だけになります。
   表の生成は `detergentmc-protocol/tools/generate-remap-tables.py` で
   再現でき、反転の正しさは `verify-reverse-tables.py` で検証できます。
2. **方向判定はクライアントと内部の比較だけ。** プロキシの振り分けは
   「同じ→素通し、古い→上向き、新しい→下向き」の比較だけで決まります。
   新しいバージョンが増えても、振り分けの条件分岐は増えません。
   増えるのは対応表とパケットID表だけで、振り分けの仕組み自体は触りません。
3. **内部プロトコルは設定1つで変わる。** `minecraft-version` を変えるだけで
   内部プロトコル・データパック形式・バニラに渡すリソースパックが
   連動して変わります（`ProtocolVersion.serverProtocolFor`、
   `DetergentServer.hostResourcePack()`、`createClientProxy()`）。
   新しい内部バージョンの追加は、ここに1行（バージョン文字列→プロトコル番号）
   を足すことが中心になります。

ただし、減らない部分もあります。パケットID表（`PacketIds`）はバージョンごとに
別表が必要ですし、設定フェーズの有無のような構造的な差分（763には設定フェーズが
無い、など）はバージョンごとに個別の対応が必要です。テキストコンポーネントの
NBT 化（1.20.3）のような形式の変わり目も、その都度の対応が必要です。
対応表の反転が効くのは、あくまで「同じ構造で番号・中身だけが違う」部分です。

## テスト

- `detergentmc-protocol`：`ProtocolVersionTest`（内部プロトコルの決定）、
  `RegistryRemapperUp764Test`／`ChunkTranslatorUp764Test`／
  `PlayTranslatorUp764Test`／`ExtraPlayTranslatorUp764Test`（反転による
  下向き変換）、`JsonToNbtTest`、`ClientAdapterCrossVersionTest`
  （同じ・古い・新しいの振り分け）、`ProxySessionInternal764Test`
  （内部764への参加パターン：764素通し・765下向き・763→764直接変換）、
  `ResourcePackSelectionTest`（クライアント基準の出し分け）。
- `detergentmc-core`：`ServerProtocolWiringTest`（DetergentServer から
  ClientProxy への配線）、
  `ProxyIntegrationTest.fake765ClientJoinsRealVanilla1202ThroughProxy`
  （本物のバニラ1.20.2子プロセス＋生ソケット765クライアント。参加・スポーン・
  チャット・移動を確認）。既存の統合テスト3件も回帰テストとして残っています。
  1.20系の相互連携で追加した統合テスト3件
  （`fake766ClientJoinsRealVanilla1201ThroughProxy`／
  `fake766ClientJoinsRealVanilla1202ThroughProxy`／
  `fake763ClientJoinsRealVanilla1202ThroughProxy`）は
  `docs/PROXY-1.20.5.md` を参照。
