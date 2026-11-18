# 1.16.2／1.16.3／1.16.4／1.16.5クライアント → 1.16系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.16.2クライアント（プロトコル751）と1.16.3クライアント（プロトコル753）と1.16.4／1.16.5クライアント（プロトコル754）は、内部プロトコルが
751／753／754／755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775の23プロトコルのサーバーを対象にします。
**751と753と754はパケットID・レジストリ・Join Game codec がすべて同一で、751⇔753⇔754の翻訳子はすべて恒等写像です**
（下の「753↔754の差分」「751↔753の差分」を参照）。751はこのツリーで最古のプロトコルです。
ただし **758（1.18.2）は未対応**で、このツリーに `V1_18_2` はありません
（他人の作業中のため飛ばした）。
776（26.2）もこのツリーにありません。
内部754のときは素通し（リレー）、内部755のときは754↔755の直接変換、
それ以外は755を経由した連鎖変換です。**754は755とパケットID・レジストリ・
Join Game codec の中身が違います。** パケットID差分は**変更120件・新規0件・
削除1件**、レジストリ差分はブロック状態20,342→17,112件・ブロック898→763件・
アイテム1,100→976件・バイオーム81→79件ほかです（下の「754↔755の差分」を参照）。

1.16.3／1.16.4／1.16.5は**Java 8**で動き、設定フェーズがありません（764で追加）。
レジストリは Join Game の dimensionCodec NBT で届きます（755と同じ方式）。
pack_format はリソース6・データ6。アイテムはNBT（データコンポーネントではない）。
1.16.3はプロトコル753です（world_version 2580。実バニラjarの `version.json` で確認。753ノート §7.3）。
1.16.2はプロトコル751です（world_version 2578。実バニラjarの `version.json` で確認。751ノート §7.2）。
1.16.4と1.16.5はどちらもプロトコル754です（world_version だけが2584と2586で違う。
実バニラjarの `version.json` で確認。754ノート §7.3）。

754の仕組みは `docs/PROXY-1.17.md`（755）、`docs/PROXY-1.17.1.md`（756）、
`docs/PROXY-1.18.md`（757）、逆向きの考え方は `docs/PROXY-REVERSE.md` を参照してください。
なお754→755の差分は「755を隣接にした直接の差分」です
（777のときの776問題・757のときの758問題と同じ構図で、同じ方針 (b) を取った）。

全529通り（クライアント751／753／754／755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775 × サーバー751／753／754／755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775。23×23）の対応マトリクスは `docs/VERSIONS.md` の「1.16.2対応：全529通りの対応マトリクス」を参照してください。751の追加分は45セル（751行23セル＋751列23セル−重複1セル）です。753の追加分は43セル（753行22セル＋753列22セル−重複1セル）でした。

## 対応表（754クライアント × サーバー、逆向きも）

**方向の非対称がこの文書の前提です。逆に書かないこと。**
古いクライアント×新しいサーバーは参加できず、新しいクライアント×古いサーバーは
Join Game が通ります（756・755の Stage E でこの向きの誤りが多かったため、ここで明記する）。
「Join Game が通る」と「遊べる」は別です。754サーバー→755クライアントでは
Join Game は通りますが chat／Set Slot／Window Items／Entity Destroy は届きません
（下の行を参照）。

| クライアント | サーバー | 経路 | 結果 |
| --- | --- | --- | --- |
| 753（1.16.3） | 753（1.16.3） | 素通し（リレー） | 参加できる（`ProxySession753Test.join753Against753IsNative`（`loginTo(753, 753)`）で確認。Join Game は `handleServerJoinGame753`（`ProxySession.java:14152`）の `is753()` 分岐で 0x24 のまま返す。Respawn 0x39・Spawn Position 0x42・chat 0x03もそのまま通る。同テストで確認） |
| 753（1.16.3） | 754（1.16.4／1.16.5） | 754形式のまま（753⇔754は恒等写像） | **参加できる**。Join Game は通る（`handleServerJoinGame754`（`ProxySession.java:14332`）の `if (is754() \|\| is753())` 分岐で 0x24 を直接返す。`ProxySession753Test.directPackets753Against754Convert`（`loginTo(753, 754)`）で確認。Edit Book 0x0c・Set Creative Slot 0x28・Window Click 0x09・Client Information 0x05 も恒等写像で通る。同テストで確認） |
| 753（1.16.3） | 755（1.17） | 755→754の直接変換（753⇔754は恒等写像） | **参加できる**。Join Game は通る（`handleServerJoinGame755`（`ProxySession.java:14598`）の `if (is754() \|\| is753())` 分岐で `translateJoinGame755To754` して 0x24 で返す。**訂正：754の文書はこの分岐を is754() だけと書いていたが、コードは is753() も含む。こちらを正とする**。753クライアント×755サーバーの直接の単体テストは無いため、テスト確認済みとは書かずコード確認として記録する） |
| 753（1.16.3） | 756〜775（1.17.1〜26.1） | 756〜775→…→763の連鎖を経て同じガードに到達 | **参加できない**（サーバー→クライアントの Join Game が `handleServerJoinGame763`（`ProxySession.java:7631`）の `if (is757() \|\| is756() \|\| is755() \|\| is754() \|\| is753())` で落とされる。775は `ProxySession753Test.join775Against753Drops`（`loginTo(753, 775)`）で確認：Join Game 0x31 は落とされる。764／765は Keep Alive が通り Respawn が落とされることを `keepAlive765Against753IsNotDroppedByRespawnGuard`／`keepAlive764Against753IsNotClosedByDisconnectGuard` で確認。756／757／759〜774は775と同型のガードに到達することをコードで確認したが、**各サーバーでの直接テストは無いため推論**。ただし翻訳子としては753→759のクライアント→サーバー変換は `ProxySession753Test.clientPackets753Against759Convert`（`loginTo(753, 759)`）で確認済み（chat 0x03→Chat Message 0x04、Block Dig 0x1B→0x1C、Window Click 0x09→0x0A、Client Information 0x05→0x07、Set Creative Slot 0x28→0x2A。Edit Book／Pong は落とす）。Join Game が落とされるため実機では参加できない） |
| 751（1.16.2） | 751（1.16.2） | 素通し（リレー） | 参加できる（`ProxySession751Test.join751Against751IsNative`（`loginTo(751, 751)`）で確認。Join Game は `handleServerJoinGame751`（`ProxySession.java:14639`）の `is751()` 分岐で 0x24 のまま返す。Respawn 0x39・Spawn Position 0x42・chat 0x03もそのまま通る。同テストで確認） |
| 751（1.16.2） | 753（1.16.3） | 753形式のまま（751⇔753は恒等写像） | **参加できる**。Join Game は通る（`handleServerJoinGame753`（`ProxySession.java:14463`）は `is753()` だけを直接返し、751クライアントは `translateJoinGame753To754` で754形式にして `handleServerPlayFrom754` に委譲し、`handleServerJoinGame754`（`ProxySession.java:14819`）の `if (is754() \|\| is753() \|\| is751())` 分岐で 0x24 を直接返す。Respawn／Spawn Position も `handleServerPlayFrom754` の同じ分岐で 0x39／0x42 を直接返す。コードで確認。`ProxySession751bTest.joinGame753To751IsIdentity`／`respawn753To751IsIdentity`／`position753To751RoundTrips`（`loginTo(751, 753)`）でテスト確認。Edit Book 0x0c・Set Creative Slot 0x28・Window Click 0x09・Client Information 0x05 も恒等写像で通る（`ProxySession751Test.directPackets751Against753Convert`（`loginTo(751, 753)`）で確認）。**訂正：`ProxySession751bTest` のコメントは「`handleServerJoinGame753` の直接返信分岐に is751() がある」と書いているが、コードは `handleServerJoinGame753` が is753() だけを直接返し、is751() があるのは委譲先の `handleServerJoinGame754` のガードである。こちらを正とする**） |
| 751（1.16.2） | 754（1.16.4／1.16.5） | 754形式のまま（751⇔754は恒等写像） | **参加できる**。Join Game は通る（`handleServerJoinGame754`（`ProxySession.java:14819`）の `if (is754() \|\| is753() \|\| is751())` 分岐で 0x24 を直接返す。Respawn／Spawn Position も `handleServerPlayFrom754` の同じ分岐で 0x39／0x42 を直接返す。コードで確認。751クライアント×754サーバーの直接の単体テストは無いため、テスト確認済みとは書かない）。Ping（0x11）は751と754で形式が同一のため751⇔754の段では素通しするが、754→755の段（`to755ServerPacketFrom754`）で落とされる。サーバー→クライアントの汎用パケットの到達は直接テストが無いため未確認。統合テストなし（単体テストのみ）。直接接続の結果は未実施 |
| 751（1.16.2） | 755（1.17） | 755→754の直接変換（751⇔753は恒等写像） | **参加できる**。Join Game は通る（`handleServerJoinGame755`（`ProxySession.java:15085`）の `if (is754() \|\| is753() \|\| is751())` 分岐で `translateJoinGame755To754` して 0x24 で返す。Respawn／Synchronize Player Position も `handleServerPlayFrom755` の同じ分岐で 0x39／0x34 で直接返す。コードで確認。751クライアント×755サーバーの直接の単体テストは無い。754クライアント版は `ProxySession754bTest.joinGame755To754RewritesCodec`／`respawn755To754RewritesDimension` でテスト確認）。Ping／Pong は両方向とも落とす（transaction形式と {id i32} 形式は互換性が無い。`to755ServerPacketFrom754`（`ProxySession.java:14886`）で落とす）。Update Light も落とす（同）。755サーバーからの汎用パケットの到達は直接テストが無いため未確認。統合テストなし（単体テストのみ）。直接接続 (b) は想定通りの切断（`multiplayer.disconnect.outdated_client` with "1.17"。ポート25565、Done 116.894s、Java 16。実測値） |
| 751（1.16.2） | 756〜775（1.17.1〜26.1） | 756〜775→…→763の連鎖を経て同じガードに到達 | **参加できない**（サーバー→クライアントの Join Game が `handleServerJoinGame763`（`ProxySession.java:7899`）の `if (is757() \|\| is756() \|\| is755() \|\| is754() \|\| is753() \|\| is751())` で落とされる。Respawn も `handleServerPlayUp:8871`／`handleServerPlay765From764:9165` の `name == PLAY_RESPAWN && (is757() \|\| is756() \|\| is755() \|\| is754() \|\| is753() \|\| is751())` で落とされる。775は `ProxySession751Test.join775Against751Drops`（`loginTo(751, 775)`）で確認：Join Game 0x31 は落とされる。764／765は Keep Alive が通り Respawn が落とされることを `keepAlive765Against751IsNotDroppedByRespawnGuard`／`keepAlive764Against751IsNotClosedByDisconnectGuard` で確認。756／757／759〜774は775と同型のガードに到達することをコードで確認したが、**各サーバーでの直接テストは無いため推論**。ただし翻訳子としては751→759のクライアント→サーバー変換は `ProxySession751Test.clientPackets751Against759Convert`（`loginTo(751, 759)`）で確認済み（chat 0x03→Chat Message 0x04、Block Dig 0x1B→0x1C、Window Click 0x09→0x0A、Client Information 0x05→0x07、Set Creative Slot 0x28→0x2A。Edit Book／Pong は落とす）。Join Game が落とされるため実機では参加できない） |

| 754（1.16.4／1.16.5） | 754（1.16.4／1.16.5） | 素通し（リレー） | 参加できる（`ProxySession754Test` の `loginTo(754, 754)` を使うテストで確認。Join Game は `handleServerJoinGame754`（`ProxySession.java:13846`）で 0x24 のまま返す） |
| 754（1.16.4／1.16.5） | 755（1.17） | 755→754の直接変換 | **参加できる**。Join Game は通る（`handleServerJoinGame755` の `is754() || is753()` 分岐（`ProxySession.java:14598`。754の文書は is754() だけと書いていたが、コードは is753() も含む。こちらを正とする）。`PlayTranslator.translateJoinGame755To754` で754形式に直して 0x24 で返す。クライアント→サーバー側も変換あり。`ProxySession754Test.directPackets754Against755Convert`（`loginTo(754, 755)`）で確認） |
| 754（1.16.4／1.16.5） | 756（1.17.1） | 756→757→759→763の連鎖を経て `handleServerJoinGame763` に到達 | **参加できない**（サーバー→クライアントの Join Game が `ProxySession.java:7364` の `if (is757() \|\| is756() \|\| is755() \|\| is754())` で落とされる。Respawn も `handleServerPlayUp:8335`／`handleServerPlay765From764:8629` のガードで落とされる。Keep Alive だけは通るが Join Game が無いので参加できない。バグではない。756サーバーでの Join Game 落としの直接テストは無いが、775サーバーでの同型のテストで確認済み。**756サーバーでの直接テストは無いため、この行の「756」部分は推論を含む**） |
| 754（1.16.4／1.16.5） | 757〜775（1.18〜26.1） | 757〜775→…→763の連鎖を経て同じガードに到達 | **参加できない**（同上。775は `ProxySession754Test.join775Against754Drops`（`loginTo(754, 775)`）で確認：Join Game 0x31 は落とされる。764／765は Keep Alive が通り Respawn が落とされることを `keepAlive765Against754IsNotDroppedByRespawnGuard`／`keepAlive764Against754IsNotClosedByDisconnectGuard` で確認。757〜763／766〜774は775と同型のガードに到達することをコードで確認したが、**各サーバーでの直接テストは無いため推論**） |
| 755（1.17） | 754（1.16.4／1.16.5） | 754→755の直接変換 | Join Game は通る。Respawn も通る（`ProxySession754Test.joinAndRespawn754Against755Convert`（`loginTo(755, 754)`）で確認。Join Game 0x24→0x26、359バイト→1,474バイト、Respawn 0x39→0x3D、Spawn Position 0x42→0x4B）。**ただし chat（0x0E）／Set Slot（0x15）／Window Items（0x13）／Entity Destroy（0x36）は755クライアントに届かない（0件。同テストで確認）**。「Join Game が通る」と「遊べる」は別。Ping（0x11）／Update Light（0x23）も落とされる（同テストで確認） |
| 756（1.17.1） | 754（1.16.4／1.16.5） | 754→755→756の連鎖変換 | Join Game は通るはず（**推論**。経路は `handleServerJoinGame754`→755委譲→756の処理で、コードで確認したが、**754サーバー＋756クライアントの直接の単体テストは無いため推論**）。Respawn も通るはず（同じ経路。**直接テストは無いため推論**）。汎用パケットは756サーバー→754クライアントと同じく落とされる見込み（**推論**） |
| 757（1.18／1.18.1） | 754（1.16.4／1.16.5） | 754→755→756→757の連鎖変換 | Join Game は通るはず（**推論**。**直接テストは無いため推論**）。Respawn も通るはず（同じ経路。**直接テストは無いため推論**） |
| 759（1.19） | 754（1.16.4／1.16.5） | 754→755→756→757→759の連鎖変換 | Join Game は通るはず（**推論**。**直接テストは無いため推論**）。Set Slot／Window Items／Chunk Data／Entity Destroy は届く（`ProxySession754Test.serverPackets754Against759Reach`（`loginTo(759, 754)`）で確認。Set Slot 0x15→0x13、Window Items 0x13→0x11、Chunk Data 0x20→0x1F、Entity Destroy 0x36→0x38・`[count=1][42]`。ただし Entity Destroy は count 1 のときだけ。count 2 は754→755の段で落とされる。同テストで確認） |
| 760〜762（1.19.1〜1.19.4） | 754（1.16.4／1.16.5） | 754→…→763の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn も通るはず（**推論**。**直接テストは無いため推論**） |
| 763〜765（1.20／1.20.1〜1.20.4） | 754（1.16.4／1.16.5） | 754→…→763の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn も通るはず（**推論**。755サーバーでの同型の確認からの類推で、**754サーバーでの直接テストは無いため推論**）。クライアント→サーバー側は変換する（`ProxySession754Test.clientPackets763Against754Convert`（`loginTo(763, 754)`）で確認：Chat Message 0x05→chat 0x03、Block Dig 0x1D→0x1B。ただし Window Click／Set Creative Slot／Edit Book は落とされる。同テストで確認） |
| 766〜773／775（1.20.5〜1.21.9／26.1） | 754（1.16.4／1.16.5） | 754→…→763→…の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn は落とされるはず（**推論**。755サーバーでは774でテスト確認・766〜773／775は落としリストに載っていることをコードで確認したが、**754サーバーでの直接テストは無いため推論**） |
| 774（1.21.11） | 754（1.16.4／1.16.5） | 三段変換754→755→756→757→759→763→774 | Join Game は通るはず（**推論**。ノート §14.9には「三段変換で通ることをテストで確認済み」とあるが、**754のテスト内に該当するテスト名（`loginTo(774, 754)` を使うもの）が見つからなかったため、本稿では推論扱いとする**）。Respawn は落とされるはず（**推論**。755サーバーでは `join774Against755ConvertsJoinGameButDropsRespawn` で確認。**754サーバーでの直接テストは無いため推論**） |
| 754（1.16.4／1.16.5） | 753（1.16.3） | 753→754の直接変換（恒等写像） | Join Game は通る（`ProxySession753Test.joinAndRespawn753Against754Convert`（`loginTo(754, 753)`）で確認。Join Game 0x24→0x24、Respawn 0x39→0x39、Spawn Position 0x42→0x42（8バイト、angle無し）。経路は `handleServerJoinGame753`（`ProxySession.java:14152`）で753→754（恒等写像）に変換して `handleServerJoinGame754` に委譲）。**ただし chat（0x0E）／Set Slot（0x15）／Window Items（0x13）／Entity Destroy（0x36）は754クライアントに届かない（0件。同テストで確認）**。Ping（0x11）／Update Light（0x23）も落とされる（同テストで確認） |
| 755（1.17） | 753（1.16.3） | 753→754→755の二段変換 | Join Game は通る（`ProxySession753bTest.joinGame753To755RewritesCodec`（`loginTo(755, 753)`）で確認。Join Game 0x24→0x26（codec書き換え。`translateJoinGame754To755` の出力と一致）。Respawn 0x39→0x3D（`respawn753To755RewritesDimension`）、Spawn Position 0x34→0x38（`position753To755AddsDismountVehicle`。末尾に dismountVehicle=false が1バイト付く）。**ただし chat／Set Slot／Window Items／Entity Destroy は755クライアントに届かない見込み（推論）**。根拠：① 754の同型テスト `ProxySession754Test.joinAndRespawn754Against755Convert` で4件とも0件を確認、② 753→754は恒等写像のため753サーバーの汎用パケットは754サーバーと同一の汎用経路（`handleServerPlayFrom753`→`handleServerPlayFrom754`→`to755ServerPacketFrom754`→`handleServerPlayFrom755`→…→763の汎用経路）をたどる、③ `ProxySession753Test` のヘッダコメントにも同じ落としが記録されている。**753サーバー＋755クライアントでの直接の単体テストは無いため推論**。変換器自体は存在する（`to755ServerPacketFrom754`（`ProxySession.java:14399`）に Set Slot／Window Items／Entity Destroy（count 1のみ）の case があり、chat は素通し＋ID付け替え）が、セッション経由では呼び出し口が無く落とす。Update Light／Ping は変換器が無く `to755ServerPacketFrom754` で落とす（コードで確認） |
| 756（1.17.1） | 753（1.16.3） | 753→754→755→756の連鎖変換 | Join Game は通るはず（**推論**。経路は `handleServerJoinGame753`→754委譲→755→756の処理で、コードで確認したが、**753サーバー＋756クライアントの直接の単体テストは無いため推論**）。Respawn も通るはず（同じ経路。**直接テストは無いため推論**）。汎用パケットは756サーバー→753クライアントと同じく落とされる見込み（**推論**） |
| 757（1.18／1.18.1） | 753（1.16.3） | 753→754→755→756→757の連鎖変換 | Join Game は通るはず（**推論**。**直接テストは無いため推論**）。Respawn も通るはず（同じ経路。**直接テストは無いため推論**） |
| 759（1.19） | 753（1.16.3） | 753→754→755→756→757→759の連鎖変換 | Join Game は通る。Set Slot／Window Items／Chunk Data／Entity Destroy は届く（`ProxySession753Test.serverPackets753Against759Reach`（`loginTo(759, 753)`）で確認。Set Slot 0x15→0x13、Window Items 0x13→0x11、Chunk Data 0x20→0x1F、Entity Destroy 0x36→0x38・`[count=1][42]`。ただし Entity Destroy は count 1 のときだけ。count 2 は754→755の段で落とされる。同テストで確認）。Respawn は通るはず（**推論**。**直接テストは無いため推論**） |
| 760〜762（1.19.1〜1.19.4） | 753（1.16.3） | 753→…→763の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn も通るはず（**推論**。**直接テストは無いため推論**） |
| 763〜765（1.20／1.20.1〜1.20.4） | 753（1.16.3） | 753→…→763の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn も通るはず（**推論**。755サーバーでの同型の確認からの類推で、**753サーバーでの直接テストは無いため推論**）。クライアント→サーバー側は変換する（`ProxySession753Test.clientPackets763Against753Convert`（`loginTo(763, 753)`）で確認：Chat Message 0x05→chat 0x03、Block Dig 0x1D→0x1B。ただし Window Click／Set Creative Slot／Edit Book は落とされる。同テストで確認） |
| 766〜773／775（1.20.5〜1.21.9／26.1） | 753（1.16.3） | 753→…→763→…の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn は落とされるはず（**推論**。755サーバーでは774でテスト確認・766〜773／775は落としリストに載っていることをコードで確認したが、**753サーバーでの直接テストは無いため推論**） |
| 774（1.21.11） | 753（1.16.3） | 三段変換753→754→755→756→757→759→763→774 | Join Game は通るはず（**推論**。**753サーバー＋774クライアントの Join Game 到達は未確認**。754のノート §14.9には確認済みとあるが、753では対応するテスト（`loginTo(774, 753)` を使うもの）が無い。754の §16.9と同じ構図）。Respawn は落とされるはず（**推論**。755サーバーでは `join774Against755ConvertsJoinGameButDropsRespawn` で確認。**753サーバーでの直接テストは無いため推論**） |
| 753（1.16.3） | 751（1.16.2） | 751→753の直接変換（恒等写像） | Join Game は通る（`ProxySession751Test.joinAndRespawn751Against753Convert`（`loginTo(753, 751)`）で確認。Join Game 0x24→0x24、Respawn 0x39→0x39、Spawn Position 0x42→0x42（8バイト、angle無し）。経路は `handleServerJoinGame751`（`ProxySession.java:14639`）で751→753（恒等写像）に変換して `handleServerPlayFrom753` に委譲し、`handleServerJoinGame753`（`ProxySession.java:14463`）の `is753()` 分岐で 0x24 を直接返す）。**ただし chat（0x0E）／Set Slot（0x15）／Window Items（0x13）／Entity Destroy（0x36）は753クライアントに届かない（0件。同テストで確認）**。Ping（0x11）／Update Light（0x23）も落とされる（同テストで確認） |
| 754（1.16.4／1.16.5） | 751（1.16.2） | 751→753→754の二段変換（恒等写像） | Join Game は通るはず（**推論**。経路は `handleServerJoinGame751`→753→`handleServerJoinGame754` の `is754()` 分岐で 0x24 を直接返す。コードで確認したが、**751サーバー＋754クライアントの直接の単体テストは無いため推論**）。Respawn／Spawn Position も通るはず（同じ経路。**直接テストは無いため推論**）。chat／Set Slot／Window Items／Entity Destroy は汎用経路で落とされるはず（753クライアント版は `joinAndRespawn751Against753Convert` でテスト確認。754クライアント版の直接テストは無いため推論）。統合テストなし（単体テストのみ）。直接接続は未実施（(d)は753クライアントのセル。751サーバー＋754クライアントの直接接続は4セルに含まれていない） |
| 755（1.17） | 751（1.16.2） | 751→753→754→755の三段変換 | Join Game は通る（`ProxySession751bTest.joinGame751To755RewritesCodec`（`loginTo(755, 751)`）で確認。Join Game 0x24→0x26（codec書き換え。`translateJoinGame754To755` の出力と一致）。Respawn 0x39→0x3D（`respawn751To755RewritesDimension`）、Spawn Position 0x34→0x38（`position751To755AddsDismountVehicle`。末尾に dismountVehicle=false が1バイト付く）。**ただし chat／Set Slot／Window Items／Entity Destroy は755クライアントに届かない見込み（推論）**。根拠：① 754の同型テスト `ProxySession754Test.joinAndRespawn754Against755Convert` で4件とも0件を確認、② 751→753は恒等写像のため751サーバーの汎用パケットは754サーバーと同一の汎用経路（`handleServerPlayFrom751`→`handleServerPlayFrom753`→`handleServerPlayFrom754`→`to755ServerPacketFrom754`→`handleServerPlayFrom755`→…→763の汎用経路）をたどる、③ 751→753の段では変換器自体が恒等写像で存在する（`to753ServerPacketFrom751`（`ProxySession.java:14690`）に Set Slot／Window Items／Entity Destroy の case があり、chat は素通し＋ID付け替え）が、セッション経由では呼び出し口が無く落とす。**751サーバー＋755クライアントでの直接の単体テストは無いため推論**。Update Light／Ping は変換器が無く `to755ServerPacketFrom754` で落とす（コードで確認） |
| 756（1.17.1） | 751（1.16.2） | 751→753→754→755→756の連鎖変換 | Join Game は通るはず（**推論**。経路は `handleServerJoinGame751`→753→754→755→756の処理で、コードで確認したが、**751サーバー＋756クライアントの直接の単体テストは無いため推論**）。Respawn も通るはず（同じ経路。**直接テストは無いため推論**）。汎用パケットは756サーバー→751クライアントと同じく落とされる見込み（**推論**） |
| 757（1.18／1.18.1） | 751（1.16.2） | 751→753→754→755→756→757の連鎖変換 | Join Game は通るはず（**推論**。**直接テストは無いため推論**）。Respawn も通るはず（同じ経路。**直接テストは無いため推論**） |
| 759（1.19） | 751（1.16.2） | 751→753→754→755→756→757→759の連鎖変換 | Join Game は通る。Set Slot／Window Items／Chunk Data／Entity Destroy は届く（`ProxySession751Test.serverPackets751Against759Reach`（`loginTo(759, 751)`）で確認。Set Slot 0x15→0x13、Window Items 0x13→0x11、Chunk Data 0x20→0x1F、Entity Destroy 0x36→0x38・`[count=1][42]`。ただし Entity Destroy は count 1 のときだけ。count 2 は754→755の段で落とされる。同テストで確認）。Respawn は通るはず（**推論**。**直接テストは無いため推論**） |
| 760〜762（1.19.1〜1.19.4） | 751（1.16.2） | 751→…→763の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn も通るはず（**推論**。**直接テストは無いため推論**） |
| 763〜765（1.20／1.20.1〜1.20.4） | 751（1.16.2） | 751→…→763の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn も通るはず（**推論**。755サーバーでの同型の確認からの類推で、**751サーバーでの直接テストは無いため推論**）。クライアント→サーバー側は変換する（`ProxySession751Test.clientPackets763Against751Convert`（`loginTo(763, 751)`）で確認：Chat Message 0x05→chat 0x03、Block Dig 0x1D→0x1B。ただし Window Click／Set Creative Slot／Edit Book は落とされる。同テストで確認） |
| 766〜773／775（1.20.5〜1.21.9／26.1） | 751（1.16.2） | 751→…→763→…の連鎖変換 | Join Game は通るはず（**推論**。統合テスト未実施のため未確認）。Respawn は落とされるはず（**推論**。755サーバーでは774でテスト確認・766〜773／775は落としリストに載っていることをコードで確認したが、**751サーバーでの直接テストは無いため推論**） |
| 774（1.21.11） | 751（1.16.2） | 三段変換751→753→754→755→756→757→759→763→774 | Join Game は通るはず（**推論**。**751サーバー＋774クライアントの Join Game 到達は未確認**。754のノート §14.9には確認済みとあるが、751では対応するテスト（`loginTo(774, 751)` を使うもの）が無い。754の §16.9・753の §16.9と同じ構図）。Respawn は落とされるはず（**推論**。755サーバーでは `join774Against755ConvertsJoinGameButDropsRespawn` で確認。**751サーバーでの直接テストは無いため推論**） |


## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。751用・753用・754用・755用・756用・757用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の23つを出す（758用・776用は無い） |

754用パックが含まれることはコードで確認済み：`ResourcePackServer.java:182` の
`PACK_PATH_754 = "/detergentmc-pack-1.16.5.zip"`、同 `:1971` の配信登録、
同 `:2486` の `packForProtocol` の754分岐（フォールバック `754→755→756→757→759→760→761→762→763`）。
`ProxySession.pack754FallsBackToPack755`（`loginTo(754, 756)`）で、pack754未設定時は
pack755に落とすこともテスト確認済み。

753用パックも含まれることはコードで確認済み：`ResourcePackServer.java:192` の
`PACK_PATH_753 = "/detergentmc-pack-1.16.3.zip"`、同 `:2630` の `packForProtocol` の753分岐
（フォールバック `753→754→755→756→757→759→760→761→762→763`）。
`ProxySession753Test.pack753FallsBackToPack754`（`loginTo(753, 756)`）で、pack753未設定時は
pack754（pack_format 6。753→754は 6→6 で境界を越えない）に落とすこともテスト確認済み。

751用パックも含まれることはコードで確認済み：`ResourcePackServer.java:201` の
`PACK_PATH_751 = "/detergentmc-pack-1.16.2.zip"`、同 `:2310` の `pack751()`、
同 `:2303` の `url751()`。`ProxySession751Test.pack751FallsBackToPack753`（`loginTo(751, 756)`）で、
pack751未設定時はpack753（pack_format 6。751→753は 6→6 で境界を越えない）に落とすこともテスト確認済み。

バニラの `server.properties` には `network-compression-threshold=-1` を書き、
バニラ側の圧縮を無効化しています（プロキシ⇔バニラ間はloopback）。
クライアント側にも圧縮は有効化しません（Set Compressionを送らない）。
バニラが万一 Set Compression を送ってきた場合、プロキシはクライアントを
切断します（zlibの展開・再圧縮はこの段階では扱いません）。

## 754↔755の差分（要点）

minecraft-data（`pc/1.16.2/protocol.json`・`pc/1.17/protocol.json`）と
ViaVersion（`mapping-1.16.json`・`Protocol1_16_4To1_17.java`）と
Minecraft Wiki の Packets ページ（1.16.5、protocol 754、2021-05-31 の改訂版
2772656）の三重照合で確認。不一致0件。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-754.py`・
`verify-754-packet-ids.py`・`verify-754-wiki.py`・`insert-754-java.py`。
件数の定義：変更＝両方にあってIDが違うもの、新規＝754にあって755の
`PacketIds` に無いもの、削除＝755にあって754に無いもの。
写像は `detergentmc-protocol/build/remap-data/packet-ids-754.json`（272件）。
`PacketIds.java` のエントリ総数は272件のまま（新規エントリ0件）。
id754>=0 は159件（HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 102件＋
PLAY サーバー行き 48件。CONFIGURATION は0件。1.16.5に設定フェーズは無い）。
**注意：minecraft-data の 1.16.5 の protocol は `pc/1.16.2` を指す。`pc/1.16` ではない**
（`pc/1.16/protocol.json` は1.16.0のものでパケットIDが1つずれている。ノート §11.2）。

| 分野 | 755↔754の差分 | 扱い |
| --- | --- | --- |
| パケットID（プレイ・クライアント行き） | **変更97件・新規0件・削除1件**。Join Game 0x26→0x24、Chunk Data 0x22→0x20、Update Time 0x58→0x4e、Respawn 0x3d→0x39、Keep Alive 0x21→0x1f、Entity Destroy 0x3a→0x36、Ping 0x30→0x11（transaction）。削除は `PLAY_SCULK_VIBRATION_SIGNAL`（Sculk Vibration Signal 0x05。1.17で追加され1.16.5には無い。754では -1） | `PacketIds` の754列で番号を付け替える |
| パケットID（プレイ・サーバー行き） | **変更23件・新規0件・削除0件**。Window Click 0x08→0x09、Pong 0x1d→0x07（transaction）。Chat（サーバー行き）は 0x03両方、Keep Alive 応答は 0x0f→0x10、Client Information は 0x05両方 | `PacketIds` の754列で番号を付け替える |
| パケットID（ログイン・ハンドシェイク・ステータス） | 同一（Login Start 0x00、Login Success 0x02、ハンドシェイク 0x00）。CONFIGURATION は両方0件 | そのまま通す |
| 設定フェーズ | **754にも755にも無い**（764で追加） | プロキシが合成・消費する。`ClientJoinFlow` は stage の三項演算子に `clientProtocol == V1_16_5` を追加済み。754クライアント＋775サーバーでは Login Success 後にプロキシが Login Acknowledged＋Client Information を内部クライアント役として送る（`ProxySession754Test.login754Against775SynthesizesConfig` で確認） |
| Join Game | **フィールド構成は同一**（15フィールド。`simulationDistance`・`lastDeathLocation` はどちらにも無い）。違うのは dimensionCodec の中身だけ（下の「Join Game／Respawn codec」を参照） | `PlayTranslator.translateJoinGame754To755`／`755To754` で codec を書き換える |
| Respawn | **フィールド構成は同一**（8フィールド）。違うのは dimension element だけ | `PlayTranslator.translateRespawn754To755`／`755To754` で dimension element を置き換える。previousGameMode は **u8**（Join Game の i8 とは違う。0xFFは255と読まれる） |
| Join Game codec の中身 | **dimension_type は4件・同一順序**（overworld 0、overworld_caves 1、the_nether 2、the_end 3）。**バイオームは754が79件（id 0〜173）、755が81件**（dripstone_caves 174・lush_caves 175 が1.17で追加）。dimension element（overworld）には754は min_y／height が無く、755は min_y 0・height 256 がある。`minecraft:chat_type` はどちらにも無い | codec は作り直す（`translateDimensionCodec754To755`／`755To754`。共有79バイオームは id・element ともバイト同一のためそのまま。755にだけある2件は実バニラ1.17の element で補い、逆向きは名前で落とす） |
| チャット | **754も755も署名なし `chat`**（サーバー行きは {message string}のみ、クライアント行き 0x0E／0x0F）。1.19は System Chat／Player Chat 等に分離（署名付き） | 754↔755は素通し。759以上との間は専用の翻訳子で変換する（下の「チャット」を参照）。署名チェーンの検証は境界を越えられない |
| Spawn Entity | 754も755も `spawn_entity`（0x00）／`spawn_entity_living`（0x02）／`spawn_entity_painting`（0x03）が別パケット | そのまま通す（759以上との間は755の翻訳子を経由する） |
| Player Info | 754は 0x32（crypto無し）、755は 0x36（crypto オプション付き）。IDが違う | ID付け替え＋暗号化情報の補完／除去（755の翻訳子を経由する） |
| Set Slot／Window Items | **フィールド構成は同一**（stateId・carriedItem は無い。756で追加されたもの）。IDは違う（Set Slot 0x15／0x16、Window Items 0x13／0x14） | `ChunkTranslator` の754節でスロットのアイテムIDだけ付け替える |
| Window Click | **フィールド構成が違う**（754は `[action i16][mode][単一スロット]`、755は `[mode][changedSlots][cursorItem]`）。IDは 0x09／0x08。**訂正：minecraft-dataの「1.16.5のmodeはi8」は誤り。javapで両版とも readEnum／writeEnum（varint）を確認**（755のときと同じ罠） | `translateWindowClick754To755`／`755To754`。754→755は changedSlots を空にし cursorItem にする。逆向きは changedSlots を落とし、uid は 0 を補う |
| Entity Destroy | **フィールド構成が違う**（754は `[count][entityIds]` 配列、755は `[entityId varint]` 1体）。IDは 0x36／0x3a。755→756方向とは向きが逆のため注意（755は単体、756は配列） | `translateEntityDestroy754To755`／`755To754`。754→755は count 1 のときだけ単体化。count 0／2以上は変換器が `null` を返す（755の1パケットでは表せないため。分割送信には ProxySession 側の対応が要る） |
| Edit Book | **フィールド構成は同一**（両版とも {new_book, signing, hand}。javapで確認）。755⇔756のような全面的な違いは無い。IDは 0x0c／0x0b | 素通し＋ID付け替え（`directPackets754Against755Convert` で確認）。落とすのは763→754方向のみ |
| Chunk Data | **形式が違う**（下の「チャンク」を参照）。IDは 0x20／0x22 | `ChunkTranslator` の754節で変換する |
| スロット | 754／755で同一（`[present bool][id varint][count i8][optionalNbt]`、NBT時代でKIND表なし） | そのまま通す（アイテムIDの付け替えだけ） |
| Explosion／Entity Update Attributes | count が違う（754は i32、755は varint） | 変換器で count を i32⇔varint に直す |
| Update Light | **フィールドが違う**（754はマスク varint×4＋data restBuffer、755は BitSet 配列＋skyLight／blockLight配列）。IDは 0x23／0x25 | **変換器なしで落とす** |
| ping／pong⇔transaction | 754は transaction（クライアント行き 0x11／サーバー行き 0x07）、755は ping（0x30）／pong（0x1d）。i32のidは i16のactionNumberに往復できない | **両方向とも落とす**（`translatePing755To754`／`translatePong755To754` は null を返す。ViaVersionにも対応付けが無い。接続維持は Keep Alive が担う） |
| combat_event／title／world_border | 754は1パケット、755は分割（combat→3、title→5、world_border→6）。IDは 0x31／0x4f／0x3d で共有 | 754→755は種別フィールドで振り分け、755→754は action フィールドを付けて組み立てる |
| Map | trackingPosition の有無が違う | 変換器で補完／除去する。755→754の `trackingPosition=true` 補完値は**未確認** |
| Resource Pack Send | forced＋promptMessage の有無が違う | 変換器で補完／除去する |
| Declare Recipes | **ワイヤ同一のため変換器不要**（両jarのjavapで確認） | そのまま通す |
| Tags | 754は4固定、755は汎用配列＋game_event | 変換器で直す |
| ブロック状態 | **755=20,342件、754=17,112件**（新ブロック136種・約3,281状態。cauldron は755で4種に分割、rail系は waterlogged を追加） | 変換表で付け替える（`verify-754-tables.py` 43/43） |
| ブロック | **755=898件、754=763件**（新136・改名1：grass_path→dirt_path） | 変換表で付け替える |
| アイテム | **755=1,100件、754=976件**（新125・改名1：同上） | 変換表で付け替える |
| エンティティ | **755=113件、754=108件**（新5：axolotl・glow_item_frame・glow_squid・goat・marker） | 変換表で付け替える |
| 音 | **755=1,190件、754=992件**（新199・改名1：block.sweet_berry_bush.pick_berries→item.sweet_berries.pick_from_bush） | 変換表で付け替える |
| パーティクル | **755=89件、754=72件**（新17） | 変換表で付け替える |
| メニュー | 24件で同じ顔ぶれ・同じ順番 | 変換なし（identity） |
| 属性 | 13件で同じ顔ぶれ・同じ順番（文字列キー。数値表は不要） | 変換なし（identity） |
| エンチャント | **755=38件、754=38件。差分0**（同一順序） | 変換なし（identity） |
| ブロックエンティティ種別 | **755=34件、754=33件**（1.17の新規 sculk_sensor（id 33）が無い。754側は 0〜32 で同一順序） | 755→754は 33→DROP、754→755は 0〜32 恒等 |
| バイオーム | **755=81件、754=79件**（1.17の新規 dripstone_caves（174）・lush_caves（175）が無い。0〜173 は同じ id・同じ名前。隙間95件） | 変換表で付け替える。隙間の id は平原（1）で埋める。754側の代表値に dripstone_caves・lush_caves は使えない |
| エフェクト | **755=32件、754=32件。差分0**（同一順序） | 変換なし（identity） |
| ゲームイベント | 755=45件、754はレジストリ自体が無い | 対応表なし |
| ダメージ種別 | **755・754のどちらにも無い**（1.19.4／762で追加） | 対応表は42件の恒等写像プレースホルダ。ワイヤ上の呼び出し元は無い（755と同じ） |
| データコンポーネント種別 | 無し（1.20.5で追加） | N/A |
| 絵画 | 754も755も `minecraft:motive` 26件で同じ | 変換なし（identity） |

### フィールド差分19パケット（755↔754で ID だけ違う91件とは別）

情報源は `mc-1.16-protocol.json` と `mc-1.17-protocol.json` の全パケット突き合わせ
（スクリプトは `detergentmc-protocol/tools/compare-protocol-754-755.py`）：
同一フィールド132件、IDだけ違う91件（行き68・来23）、**フィールド構成が違う30件＝実質19パケット**、
1.16.5のみ1件（entity 0x2a）、1.17のみ1件（sculk 0x05）、共有typesの差分はparticleDataのみ。
裏付けは公式 `server_mappings.txt` で求めた難読化クラス名での実バニラ両jarの javap と
ViaVersion（`Protocol1_16_4To1_17.java`）。

| パケット | 754 | 755 | 扱い |
| --- | --- | --- | --- |
| Join Game（0x24／0x26） | codec中身のみ違う（バイオーム79件・dimension element に min_y／height 無し） | codec中身のみ違う（バイオーム81件・min_y 0／height 256） | codec を書き換える（上の表を参照） |
| Respawn（0x39／0x3d） | dimension element のみ違う（min_y／height 無し） | dimension element のみ違う（min_y 0／height 256） | dimension element だけ755既定値に直す |
| Client Information（0x05両方） | **6フィールド**（locale／viewDistance i8／chatFlags varint／chatColors／skinParts u8／mainHand varint。disableTextFiltering は無い） | **7フィールド**（末尾に disableTextFiltering） | 754→755は末尾に false を補完（false＝フィルタ有効で1.17バニラクライアントの既定値。**1.16.5には設定が無いため既定値を適用した DetergentMC の判断。ViaVersionに逆向きの補完値は無い**）。755→754は末尾を除去 |
| Synchronize Player Position（0x34／0x38） | dismountVehicle 無し | dismountVehicle あり | 754→755は false を補完（降りない＝乗り続けるのが元の挙動。762⇔761と同じ選択）。755→754は除去 |
| combat_event→3分割（0x31） | 1パケット（action フィールド付き） | enter_combat_event／end_combat_event／death_combat_event に3分割 | 種別フィールドで振り分け／action を付けて組み立て |
| title→5分割（0x4f） | 1パケット | set_title_text／set_title_subtitle／set_title_time／clear_titles／action_bar に5分割 | 同上 |
| world_border→6分割（0x3d） | 1パケット | initialize_world_border／world_border_center／world_border_lerp_size／world_border_size／world_border_warning_delay／world_border_warning_reach に6分割 | 同上。radius＝diameterの値そのまま。action 3のwarning順序は **warningBlocks→warningTime**（protocol.jsonの順序は誤り。javap＋生キャプチャで確認） |
| ping／pong⇔transaction（0x11／0x07 ⇔ 0x30／0x1d） | transaction 形式 | {id i32} 形式 | **両方向とも落とす** |
| Entity Destroy（0x36／0x3a） | `[count][entityIds]` 配列 | `[entityId varint]` 1体 | 754→755は count 1 のときだけ単体化。count 0／2以上は null で落とす |
| Window Click（0x09／0x08） | `[action i16][mode][単一スロット]` | `[mode][changedSlots][cursorItem]` | 上の表を参照。754の単一スロットはカーソルアイテム（実バニラ1.16.5サーバーのハンドラの javap で確認） |
| Chunk Data（0x20／0x22） | `[groundUp bool][bitMap varint][biomesはgroundUpのときだけ]` | `[bitMap varint(i64)][biomes 常時]` | 下の「チャンク」を参照 |
| Update Light（0x23／0x25） | マスク varint×4＋data restBuffer | BitSet 配列＋skyLight／blockLight配列 | **変換器なしで落とす** |
| Explosion（0x1b／0x1c） | count i32 | count varint | count を i32⇔varint に直す |
| Entity Update Attributes（0x58／0x65） | count i32 | count varint | 同上 |
| Map（0x25／0x27） | trackingPosition 無し | trackingPosition あり | 変換器で補完／除去。755→754の補完値は**未確認** |
| Declare Recipes | ワイヤ同一 | ワイヤ同一 | 変換器不要 |
| Tags | 4固定 | 汎用配列＋game_event | 変換器で直す |
| Resource Pack Send（0x38／0x3c） | forced＋promptMessage 無し | forced＋promptMessage あり | 変換器で補完／除去する |
| Sculk Vibration Signal（—／0x05） | 無い（754では -1） | 0x05 | 755→754で落とす |

## 753↔754の差分（要点）

minecraft-data（`pc/1.16.2/protocol.json`。1.16.3の protocol は dataPaths.json で pc/1.16.2 を指す。
`mc-1.16.3-protocol.json` は `mc-1.16-protocol.json` とバイト単位で完全一致、152,123バイト）と
ViaVersion（`Protocol1_16_3To1_16_4.java`・`Protocol1_16_2To1_16_3.java`。両プロトコルとも両側で同じ
`ClientboundPackets1_16_2`／`ServerboundPackets1_16_2` を使う。1.16.3専用の enum は ViaVersion には無い）と
Minecraft Wiki の Packets ページ（1.16.3、protocol 753、2020-10-25 の改訂版 2772553）の三重照合で確認。
不一致0件。検証スクリプトは `detergentmc-protocol/tools/packet-ids-753.py`・
`verify-753-packet-ids.py`・`verify-753-wiki.py`・`insert-753-java.py`。
写像は `detergentmc-protocol/build/remap-data/packet-ids-753.json`（272件、sha1 `514a98ae170f7f4417e2a51314c86ee3f6880aed`）。
**`PacketIds.java` の753列は全272件で754列と同一であることを、packet-ids-753.json との全件照合で確認済み
（不一致0件。本稿作成時に再照合）**。したがって下の「パケットIDの対応表」の754列の値がそのまま753の値になる。

| 分野 | 754↔753の差分 | 扱い |
| --- | --- | --- |
| パケットID（全状態・全方向） | **変更0件・新規0件・削除0件**。Join Game 0x24、Chunk Data 0x20、Respawn 0x39、Keep Alive 0x1f（応答 0x10）、Chat 0x03（サーバー行き）／0x0E（クライアント行き）、Client Information 0x05、Set Player Position 0x12、Login Success 0x02、プレイ切断 0x19。すべて754と同一 | 番号付け替えなし |
| レジストリ（全種） | **差分0件**。実バニラ1.16.3 jarの data-generator レポート（`blocks-1.16.3-reports.json`・`registries-1.16.3.json`）は1.16.5版とバイト単位で完全一致。ブロック状態17,112件・ブロック763件・アイテム976件・エンティティ108件・音992件・パーティクル72件・バイオーム79件・ブロックエンティティ33件・メニュー24件・属性13件・エンチャント38件・エフェクト32件。改名なし | 変換表は恒等写像（`verify-753-tables.py` 43/43） |
| Join Game／Respawn codec | **同一**。LoginPacket は1.16.3も1.16.5も `px` で同じ15フィールド。RespawnPacket は `qp` で同じ8フィールド。previousGameMode は u8 0xFF=255（1.16.5は i8 で -1。ワイヤ上は同じ1バイト）。dimensionCodec は dimension_type 4件・バイオーム79件（id 0〜173）。dimension element（overworld）に min_y／height は無し。実バニラ1.16.3の Join Game 生キャプチャは **30,847バイト**（2回とも同じ。フィールド15件を全てパースし消費量 30,847／30,847 で完全一致。entityId 109／204、viewDistance 10、maxPlayers 5）。1.16.5の実測値とすべて同一 | `translateJoinGame753To754`／`754To753`、`translateRespawn753To754`／`754To753` は恒等写像（ペイロードをそのまま返す） |
| チャンク | **同一**（[groundUp bool][bitMap varint][biomesはgroundUpのときだけ]、non-straddling。実バニラ1.16.3からChunk Data 0x20を482件受信） | `translateChunkData753To754`／`754To753` は恒等写像 |
| Client Information | **同一**（6フィールド。locale／viewDistance i8／chatFlags varint／chatColors／skinParts u8／mainHand varint。disableTextFiltering は無い） | 恒等写像 |
| Entity Destroy | **同一**（[count][entityIds] 配列。count 0／2以上もそのまま通す。754→755のような単体化は無い） | 恒等写像 |
| combat_event／title／world_border | **同一**（両版とも単一ID 0x31／0x4f／0x3d。分割・合成不要） | 変換器なし |
| ping／pong⇔transaction | **同一**（両版とも transaction。クライアント行き 0x11／サーバー行き 0x07） | 付け替え不要。ただし754→755の段では落とす（下の「送らない・落とすパケット」を参照） |
| entity（クライアント行き 0x2a） | 両版とも PacketIds の対象外（1.17で削除。ViaVersionも落としている） | 落とす |
| sculk | 両版とも無し（-1） | — |

### 753列の注記（パケットIDの対応表の読み方）

上の「パケットIDの対応表」は755↔754の表だが、753列は全272件で754列と同一のため、754列の値をそのまま753の値として読んでよい。
主要パケットの753値は `PacketIds.java` の753列（先頭引数）で個別に確認済み：

| パケット | 753 |
| --- | --- |
| Join Game | 0x24 |
| Chunk Data | 0x20 |
| Spawn Position | 0x42 |
| Keep Alive（クライアント行き） | 0x1f |
| Keep Alive応答（サーバー行き） | 0x10 |
| Chat（クライアント行き） | 0x0e |
| Chat（サーバー行き） | 0x03 |
| Client Information（サーバー行き） | 0x05（6フィールド） |
| Set Player Position（サーバー行き） | 0x12 |
| Login Success | 0x02 |
| プレイ切断 | 0x19 |

残りの261件も packet-ids-753.json との全件照合で 753＝754 を確認済み（不一致0件）。
id753>=0 は159件（HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 102件＋PLAY サーバー行き 48件。
CONFIGURATION は0件。1.16.3に設定フェーズは無い）。

## 751↔753の差分（要点）

minecraft-data（`pc/1.16.2/protocol.json`。1.16.2の protocol は dataPaths.json で pc/1.16.2 を指す。
`mc-1.16.2-protocol.json` は `mc-1.16.3-protocol.json` とバイト単位で完全一致、152,123バイト）と
ViaVersion（`Protocol1_16_2To1_16_3.java`・`Protocol1_16_3To1_16_4.java`。両プロトコルとも両側で同じ
`ClientboundPackets1_16_2`／`ServerboundPackets1_16_2` を使う。1.16.2専用の enum は ViaVersion には無い）と
Minecraft Wiki の Packets ページの三重照合で確認。不一致0件。
Wiki は 1.16.2 リリース（2020-08-11）から 1.16.3 リリース（2020-09-10）の間にページを 1.16.2 に更新した
改訂版が無いため、1.16.3の改訂版 2772553（2020-10-25）との突き合わせと、2772546（2020-09-07）の
表が 1.15.2 の表のままであることの陳腐化の裏付けで照合した（751ノート §8.4）。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-751.py`・
`verify-751-packet-ids.py`・`verify-751-wiki.py`・`insert-751-java.py`。
写像は `detergentmc-protocol/build/remap-data/packet-ids-751.json`（272件、sha1 `a5194336c2f0619a539561de0b3e87a9cd1729f3`）。
**`PacketIds.java` の751列は全272件で753列と同一であることを、packet-ids-751.json との全件照合で確認済み
（不一致0件。本稿作成時に再照合）**。したがって下の「パケットIDの対応表」の754列の値がそのまま751の値になる。

| 分野 | 753↔751の差分 | 扱い |
| --- | --- | --- |
| パケットID（全状態・全方向） | **変更0件・新規0件・削除0件**。Join Game 0x24、Chunk Data 0x20、Respawn 0x39、Keep Alive 0x1f（応答 0x10）、Chat 0x03（サーバー行き）／0x0E（クライアント行き）、Client Information 0x05、Set Player Position 0x12、Login Success 0x02、プレイ切断 0x19。すべて753と同一 | 番号付け替えなし |
| レジストリ（全種） | **差分0件**。実バニラ1.16.2 jarの data-generator レポート（`blocks-1.16.2-reports.json`・`registries-1.16.2.json`）は1.16.3版とバイト単位で完全一致。ブロック状態17,112件・ブロック763件・アイテム976件・エンティティ108件・音992件・パーティクル72件・バイオーム79件・ブロックエンティティ33件・メニュー24件・属性13件・エンチャント38件・エフェクト32件。改名なし | 変換表は恒等写像（`verify-751-tables.py` 43/43） |
| Join Game／Respawn codec | **同一**。LoginPacket は1.16.2も1.16.3も `px` で同じ15フィールド。RespawnPacket は `qp` で同じ8フィールド。previousGameMode は u8 0xFF=255（1.16.3は i8 で -1。ワイヤ上は同じ1バイト）。dimensionCodec は dimension_type 4件・バイオーム79件（id 0〜173）。dimension element（overworld）に min_y／height は無し。実バニラ1.16.2の Join Game 生キャプチャは **30,847バイト**（2回とも同じ。フィールド15件を全てパースし消費量 30,847／30,847 で完全一致。entityId 131／306、viewDistance 10、maxPlayers 20）。1.16.3の実測値とすべて同一 | `translateJoinGame751To753`／`753To751`、`translateRespawn751To753`／`753To751` は恒等写像（ペイロードをそのまま返す） |
| チャンク | **同一**（[groundUp bool][bitMap varint][biomesはgroundUpのときだけ]、non-straddling。実バニラ1.16.2からChunk Data 0x20を462件受信） | `translateChunkData751To753`／`753To751` は恒等写像 |
| Client Information | **同一**（6フィールド。locale／viewDistance i8／chatFlags varint／chatColors／skinParts u8／mainHand varint。disableTextFiltering は無い） | 恒等写像 |
| Entity Destroy | **同一**（[count][entityIds] 配列。count 0／2以上もそのまま通す。754→755のような単体化は無い） | 恒等写像 |
| combat_event／title／world_border | **同一**（両版とも単一ID 0x31／0x4f／0x3d。分割・合成不要） | 変換器なし |
| ping／pong⇔transaction | **同一**（両版とも transaction。クライアント行き 0x11／サーバー行き 0x07） | 付け替え不要。ただし754→755の段では落とす（下の「送らない・落とすパケット」を参照） |
| entity（クライアント行き 0x2a） | 両版とも PacketIds の対象外（1.17で削除。ViaVersionも落としている） | 落とす |
| sculk | 両版とも無し（-1） | — |

### 751列の注記（パケットIDの対応表の読み方）

上の「パケットIDの対応表」は755↔754の表だが、751列は全272件で753列と同一のため、754列の値をそのまま751の値として読んでよい。
主要パケットの751値は `PacketIds.java` の751列（先頭引数）で個別に確認済み：

| パケット | 751 |
| --- | --- |
| Join Game | 0x24 |
| Chunk Data | 0x20 |
| Spawn Position | 0x42 |
| Keep Alive（クライアント行き） | 0x1f |
| Keep Alive応答（サーバー行き） | 0x10 |
| Chat（クライアント行き） | 0x0e |
| Chat（サーバー行き） | 0x03 |
| Client Information（サーバー行き） | 0x05（6フィールド） |
| Set Player Position（サーバー行き） | 0x12 |
| Login Success | 0x02 |
| プレイ切断 | 0x19 |

残りの261件も packet-ids-751.json との全件照合で 751＝753 を確認済み（不一致0件）。
id751>=0 は159件（HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 102件＋PLAY サーバー行き 48件。
CONFIGURATION は0件。1.16.2に設定フェーズは無い）。

## 736↔751の差分（要点）

minecraft-data（`pc/1.16.1/protocol.json`。1.16.1の protocol は dataPaths.json で pc/1.16.1 を指す。
`mc-1.16.1-protocol.json` は `mc-1.16.2-protocol.json` とバイト単位で完全一致せず、153,046バイト）と
ViaVersion（`Protocol1_16To1_16_1.java`・`Protocol1_16_1To1_16_2.java`。ViaVersion に 1.16.1 専用の
enum は無く、両側とも `ClientboundPackets1_16`／`ServerboundPackets1_16` を使う。1.16.1 は ViaVersion
としても 1.16 と同じパケットID）と Minecraft Wiki の Packets ページの三重照合で確認。不一致0件。
Wiki は 1.16.1 リリース（2020-06-24）から 1.16.2 リリース（2020-08-11）の間にページを 1.16.1 に更新した
改訂版が無いため、1.16.3の改訂版 2772553（2020-10-25）との差分の突き合わせと、2772541（2020-07-31）の
表が 1.15.2 の表のままであることの陳腐化の裏付けで照合した（736ノート §8.4）。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-736.py`・
`verify-736-packet-ids.py`・`verify-736-wiki.py`・`insert-736-java.py`。
写像は `detergentmc-protocol/build/remap-data/packet-ids-736.json`（272件、sha1 `805ba8f74546e34bb14a9d16ecf12c52e7705bde`）。
**751↔753は変更0件だったのに対し、751↔736はパケットIDが大きく変わる**（下の表を参照）。

| 分野 | 751↔736の差分 | 扱い |
| --- | --- | --- |
| パケットID（全状態・全方向） | **変更62件・新規0件・削除1件**（`PLAY_DISPLAYED_RECIPE`。751で追加された displayed_recipe は736に無い）。PLAY／クライアント行き46件・PLAY／サーバー行き16件。Join Game 0x25、Chunk Data 0x21、Respawn 0x3a、Keep Alive 0x20（応答 0x10）、Ping 0x12（transaction）／Pong 0x07、Chat 0x03（サーバー行き）／0x0E（クライアント行き）、Client Information 0x05、Set Player Position 0x12、Synchronize Player Position 0x35、Login Success 0x02、プレイ切断 0x1a、Update Time 0x4e（変わらず） | 番号付け替え（62件）。`PacketIds.java` の736列は全272件で packet-ids-736.json と一致（不一致0件。本稿作成時に再照合） |
| レジストリ（全種） | ブロック状態17,112→17,104件（順向き非恒等 **12,381件**）、ブロック763件（同一順序）、アイテム976→975件（非恒等 **249件**）、エンティティ108→107件（非恒等 **47件**）、音992→985件（非恒等 **405件**）、パーティクル72件・バイオーム79件・ブロックエンティティ33件・メニュー24件・属性13件・エンチャント38件・エフェクト32件は同一順序で変化なし。piglin_brute系の削除（アイテム `piglin_brute_spawn_egg`、エンティティ `piglin_brute`、音7件）＋道具・hoe・染料の並び替え。補正はブロック状態4件のみ | 変換表は `RegistryTables736.java`（`verify-736-tables.py` 43/43） |
| Join Game／Respawn codec | **大きく違う**。Join Game は **14フィールドで `isHardcore` が無い**（1.16.2で追加された15フィールド目）。**dimension は NBT ではなく文字列**（"minecraft:overworld"）。**dimensionCodec は `{"dimension": [...]}` だけ**（dimension_type 4件。minecraft:dimension_type／minecraft:worldgen/biome のレジストリは無い。バイオームは Join Game には入っていない）。dimension element のキーは14種の和集合。min_y／height は無い。maxPlayers は **u8**（751は varint）。hardcore は gameMode バイトの bit 3。Respawn も **8フィールドで dimension は文字列**。実バニラ1.16.1の Join Game 生キャプチャは **1,159バイト**（2回とも、消費量完全一致。entityId 138／245。751の30,847バイトと大違い） | `translateJoinGame736To751`／`751To736`、`translateRespawn736To751`／`751To736`、`translateDimensionCodec736To751`／`751To736` で codec を作り直す（往復バイト一致：736→751→736で1,159バイト、751→736→751で30,847バイト） |
| チャンク | **形式が違う**。736は [x i32][z i32][groundUp bool][ignoreOldData bool][bitMap varint][heightmaps NBT][biomes（groundUpのときだけ i32×1024・件数接頭辞なし）][chunkData][blockEntities]。751は ignoreOldData が無く、biomes は [count varint][varint×1024]。両版とも BitStorage は **non-straddling**（BPE 5の Data Array長342で確定。straddling式だと320）。実バニラ1.16.1から Chunk Data 0x21を470件（1回目・7,374,478バイト）／462件（2回目・7,404,458バイト）受信 | `translateChunkData736To751`／`751To736` で形式を直す。ignoreOldData は751→736で true を補う（751チャンクは常に全チャンクのため）。groundUp=false の平原補完はしない（751の biomes は groundUp のときだけで、書くと実バニラ1.16.2クライアントが誤読して壊れる。736ノート §14.4） |
| Client Information | **同一**（6フィールド。locale／viewDistance i8／chatFlags varint／chatColors／skinParts u8／mainHand varint。disableTextFiltering は無い） | 恒等写像 |
| Entity Destroy | **同一**（[count][entityIds] 配列。count 0／2以上もそのまま通す。754→755のような単体化は無い） | 恒等写像 |
| combat_event／title／world_border | **同一**（両版とも単一ID 0x32／0x4f／0x3d。分割は755（1.17）での変更で、751は分割されていない。振り分け・組み立ては不要） | 恒等写像 |
| ping／pong⇔transaction | **同一**（両版とも transaction。クライアント行き 0x12／サーバー行き 0x07） | 付け替え不要。ただし754→755の段では落とす（下の「送らない・落とすパケット」を参照） |
| recipe_book／displayed_recipe | 736は `crafting_book_data`（0x1e）、751は `recipe_book`（0x1e）で改名。`displayed_recipe`（751：0x1f）は736に無い | `translateRecipeBook736To751`／`751To736`、`translateDisplayedRecipe736To751`／`751To736`。751→736は type=0 の crafting_book_data に復元する（変換。落としではない）。736→751の recipe_book は非可逆（crafting_book_data type=1は4冊分だが751の recipe_book は1冊分しか持てない。開いている本を優先順で1冊選ぶ。736ノート §14.6） |
| Multi Block Change | 736は0x0f（[chunkX][chunkZ][records]）、751は0x3b（セクションごと） | `translateMultiBlockChange736To751List` でセクションごとに分割。751→736は1対1 |
| Update Light | 両版でフィールド同一（minecraft-data・javap・ViaVersion未登録の3系統で確認） | 恒等写像（素通し） |
| entity（クライアント行き 0x2b） | PacketIds の対象外（1.17で削除。ViaVersionも落としている） | 落とす |
| sculk | 両版とも無し（-1） | — |

### 736列の注記（パケットIDの対応表の読み方）

上の「パケットIDの対応表」は755↔754の表だが、736列は751列と62件違うため、ここに736の主要パケットを
別に書く。以下のIDはすべて `PacketIds.java` の736列と packet-ids-736.json で確認済み：

| パケット | 736 | 751 |
| --- | --- | --- |
| Join Game | 0x25 | 0x24 |
| Chunk Data | 0x21 | 0x20 |
| Respawn | 0x3a | 0x39 |
| Spawn Position | 0x42 | 0x42 |
| Keep Alive（クライアント行き） | 0x20 | 0x1f |
| Keep Alive応答（サーバー行き） | 0x10 | 0x10 |
| Ping（クライアント行き） | 0x12 | 0x11 |
| Pong（サーバー行き） | 0x07 | 0x07 |
| Chat（クライアント行き） | 0x0e | 0x0e |
| Chat（サーバー行き） | 0x03 | 0x03 |
| Client Information（サーバー行き） | 0x05（6フィールド） | 0x05（6フィールド） |
| Set Player Position（サーバー行き） | 0x12 | 0x12 |
| Synchronize Player Position（クライアント行き） | 0x35 | 0x34 |
| Login Success | 0x02 | 0x02 |
| プレイ切断 | 0x1a | 0x19 |
| Update Time | 0x4e | 0x4e |
| Update Light | 0x24 | 0x23 |
| Entity Destroy | 0x37 | 0x36 |
| Multi Block Change | 0x0f | 0x3b |
| Window Click（サーバー行き） | 0x09 | 0x09 |
| Set Creative Slot（サーバー行き） | 0x27 | 0x28 |
| Edit Book（サーバー行き） | 0x0c | 0x0c |
| Recipe Book（サーバー行き） | 0x1e（crafting_book_data） | 0x1e（recipe_book） |
| Displayed Recipe（サーバー行き） | -1（無し） | 0x1f |

残りの分も packet-ids-736.json との全件照合で確認済み（不一致0件）。
id736>=0 は158件（HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 102件＋PLAY サーバー行き 47件。
CONFIGURATION は0件。1.16.1に設定フェーズは無い）。
**736は今のツリー内最古のプロトコル**（736より古いものは無い）。736クライアント×756以上サーバーは
Join Game が通らないため参加できない（下の「接続の流れ」を参照）。

## 735↔736の差分（要点）

**差分は無い**。パケットIDは変更0件・新規0件・削除0件、レジストリは全種で恒等写像
（非恒等0件・補正0件）。735は736と「同じ版」と言ってよい。翻訳子はすべて恒等写像で、
735⇔736の変換はID付け替え＋素通しだけである。ただし 735 は 736 とは別プロトコルで、
Join Game／Respawn／Chunk Data の codec・形式は736と同じ（751とは大きく違う）。

minecraft-data は 1.16 の protocol.json が pc/1.16.2 とバイト同一の誤写で、実jarとは
食い違うため使っていない（736ノート §8.2・735ノート §8.1・§13.1で `cmp` 確認。
mc-1.16-protocol.json と mc-1.16.2-protocol.json はバイト同一（152,123バイト）。
mc-1.16.1-protocol.json は153,046バイトで別物）。実jarの `ConnectionProtocol`
登録順（javap＋Mojang mappings）で実測した。ViaVersion（`Protocol1_16To1_16_1.java`。
両側とも 1.16 enum（ID同一。付け替え無しの空プロトコル）。`Protocol1_16_1To1_16_2`
は旧側が 1.16 enum・新側が 1.16.2 enum（736→751 で変わることの証拠））と
Minecraft Wiki の Packets ページ（1.16.3の改訂版 2772553（2020-10-25）と
2772541（2020-07-31）・2772533の陳腐化の裏付け）の三重照合で確認。不一致0件。
検証スクリプトは `detergentmc-protocol/tools/packet-ids-735.py`・
`verify-735-packet-ids.py`・`verify-735-wiki.py`・`insert-735-java.py`。
写像は `detergentmc-protocol/build/remap-data/packet-ids-735.json`（272件、sha1 `d6ef129fccd905031c7c58e828e92f1a44ba1b59`）。

| 分野 | 735↔736の差分 | 扱い |
| --- | --- | --- |
| パケットID（全状態・全方向） | **変更0件・新規0件・削除0件**（全272件で同一。Join Game 0x25、Chunk Data 0x21、Respawn 0x3a、Keep Alive 0x20（応答 0x10）、Ping 0x12（transaction）／Pong 0x07、Chat 0x03（サーバー行き）／0x0E（クライアント行き）、Client Information 0x05、Set Player Position 0x12、Synchronize Player Position 0x35、Login Success 0x02、プレイ切断 0x1a、Update Time 0x4e） | 付け替え不要。`PacketIds.java` の735列は全272件で packet-ids-735.json と一致（不一致0件。親が再計算して確認） |
| レジストリ（全種） | **すべて恒等写像**（1.16と1.16.1のblocks／registriesレポートはバイト同一）。ブロック状態17,104件、ブロック763件、アイテム975件、エンティティ107件、音985件、パーティクル72件、バイオーム79件（174件一覧。gapの100はplainsで埋まる）、ブロックエンティティ33件、メニュー24件、属性13件、エンチャント38件、エフェクト32件、ダメージ種別42件はすべて同一順序で変化なし。**非恒等0件・補正0件** | 変換表は `RegistryTables735.java`（`verify-735-tables.py` 43/43）。写像は恒等写像のため `RegistryRemapper` の735分岐は736の表を経由する（`Maps735` ホルダ方式。外側static初期化子は0バイト増） |
| Join Game／Respawn codec | **同一**。Join Game は **14フィールドで `isHardcore` が無い**（1.16.2で追加された15フィールド目）。**dimension は NBT ではなく文字列**（"minecraft:overworld"）。**dimensionCodec は `{"dimension": [...]}` だけ**（dimension 4件。minecraft:dimension_type／minecraft:worldgen/biome のレジストリは無い。バイオームは Join Game には入っていない）。dimension element のキーは14種の和集合。min_y／height は無い。maxPlayers は **u8**（751は varint）。hardcore は gameMode バイトの bit 3。Respawn も **8フィールドで dimension は文字列**（実バニラ1.16 jarと1.16.1 jarの `ClientboundRespawnPacket`（pp）`#write` のjavapは**バイト同一**。さらに両jarの**全21,480クラスをsha1比較**したところ、異なるのは `o.class`（`net.minecraft.DetectedVersion`。バージョン文字列・world 2566／2567・protocol 735／736のみ）1件だけ）。実バニラ1.16の Join Game 生キャプチャは **1,159バイト**（消費量完全一致。entityId 352。1.16.1の1,159バイトと同一） | `translateJoinGame735To736`／`736To735`、`translateRespawn735To736`／`736To735`、`translateDimensionCodec735To736`／`736To735` はすべて恒等写像（往復バイト一致：735→736→735で1,159バイト、736→735→736で1,159バイト） |
| チャンク | **形式が同一**。735は [x i32][z i32][groundUp bool][ignoreOldData bool][bitMap varint][heightmaps NBT][biomes（groundUpのときだけ i32×1024・件数接頭辞なし）][chunkData][blockEntities]。両版とも BitStorage は **non-straddling**（`adj` クラスが1.16／1.16.1でバイト同一（sha1 `de84c41a…` 一致）。BPE 5の Data Array長342は柱2の実キャプチャ照合で確認）。実バニラ1.16から Chunk Data 0x21を462パケット（7,611,671バイト）受信。生キャプチャ3件（-3,-15／-3,-14／-3,-13、各13,011／13,011／13,015バイト）を736形式として全パースし、3件とも消費量＝ペイロード長で完全一致（groundUp=true、ignoreOldData=true、bitMap 0x000f、biomes 1024件全て24（i32）） | `translateChunkData735To736`／`736To735` は恒等写像（`cap735-chunk-x--3-z--15.bin`（13,011バイト、sha1 `b0226d53…`）と流用の736キャプチャ（15,083バイト）の両方で往復バイト一致） |
| Client Information | **同一**（6フィールド。locale／viewDistance i8／chatFlags varint／chatColors／skinParts u8／mainHand varint。disableTextFiltering は無い。736とバイト同一・11バイト） | 恒等写像 |
| Entity Destroy | **同一**（[count][entityIds] 配列。count 0／2以上もそのまま通す。754→755のような単体化は無い） | 恒等写像 |
| combat_event／title／world_border | **同一**（両版とも単一ID 0x32／0x4f／0x3d。分割は755（1.17）での変更で、751は分割されていない。振り分け・組み立ては不要） | 恒等写像 |
| ping／pong⇔transaction | **同一**（両版とも transaction。クライアント行き 0x12／サーバー行き 0x07。`{windowId i8, actionNumber i16, accepted bool}`。736⇔751で落としていたのは751側が `{id i32}` だったため。735⇔736では落とさない） | 恒等写像（素通し） |
| recipe_book／displayed_recipe | 両版とも `crafting_book_data`（0x1e）で同一。`displayed_recipe` は両版とも -1（無し） | 変換器なし（両版同一のため不要） |
| Multi Block Change | **同一**（735・736とも0x0f（[chunkX][chunkZ][records]）。751は0x3b（セクションごと）） | 恒等写像 |
| Update Light | 両版でフィールド同一（実バニラ1.16 jarの `ow` クラスが1.16.1とバイト同一。ViaVersion `Protocol1_16To1_16_1` は空プロトコルで登録なし。mc-1.16.1定義（736 §14.4）の3系統で確認） | 恒等写像（素通し） |
| entity（クライアント行き 0x2b） | PacketIds の対象外（1.17で削除。ViaVersionも落としている） | 落とす（736の0x2bと同じ判断） |
| sculk | 両版とも無し（-1） | — |

### 735列の注記（パケットIDの対応表の読み方）

735列は736列と全272件で同一のため、上の「736列の注記」の表がそのまま735の表になる
（Join Game 0x25、Chunk Data 0x21、Respawn 0x3a、Spawn Position 0x42、
Keep Alive 0x20、Keep Alive応答 0x10、Ping 0x12、Pong 0x07、Chat 0x0e／0x03、
Client Information 0x05（6フィールド）、Set Player Position 0x12、
Synchronize Player Position 0x35、Login Success 0x02、プレイ切断 0x1a、
Update Time 0x4e、Update Light 0x24、Entity Destroy 0x37、Multi Block Change 0x0f、
Window Click 0x09、Set Creative Slot 0x27、Edit Book 0x0c、
Recipe Book 0x1e（crafting_book_data）、Displayed Recipe -1（無し））。
id735>=0 は158件（HANDSHAKE 1件＋LOGIN 8件＋PLAY クライアント行き 102件＋PLAY サーバー行き 47件。
CONFIGURATION は0件。1.16に設定フェーズは無い）。
**735は今のツリー内最古のプロトコル**（735より古いものは無い）。735クライアント×756以上サーバーは
Join Game が通らないため参加できない（下の「接続の流れ」を参照）。

## パケットIDの対応表（755↔754、確認したものだけ）

出典は `detergentmc-protocol/build/remap-data/packet-ids-754.json`（272件）。
**変更120件・新規0件・削除1件**（sculk 0x05）。ここに無いパケットのIDは未確認のため書かない。
以下のIDはすべて `PacketIds.java` で再確認済み（先頭の引数が754列）。

### 主要パケット（確認済み）

| パケット | 755 | 754 |
| --- | --- | --- |
| Join Game（`login`） | 0x26 | 0x24 |
| Chunk Data（`map_chunk`） | 0x22 | 0x20 |
| Respawn | 0x3D | 0x39 |
| Spawn Position | 0x4B | 0x42 |
| Keep Alive（クライアント行き） | 0x21 | 0x1F |
| Keep Alive（サーバー行き・応答） | 0x0F | 0x10 |
| Ping（クライアント行き） | 0x30 | 0x11（transaction） |
| Pong（サーバー行き） | 0x1D | 0x07（transaction） |
| Chat（クライアント行き） | 0x0F | 0x0E |
| Chat（サーバー行き） | 0x03 | 0x03 |
| Player Info（`player_info`） | 0x36 | 0x32 |
| Disconnect（`kick_disconnect`） | 0x1A | 0x19 |
| Entity Status | 0x1B | 0x1A |
| Resource Pack Send | 0x3C | 0x38 |
| Client Information（`settings`、サーバー行き） | 0x05 | 0x05 |
| Spawn Entity | 0x00 | 0x00 |
| Spawn Entity Living | 0x02 | 0x02 |
| Spawn Entity Painting | 0x03 | 0x03 |
| Named Entity Spawn | 0x04 | 0x04 |
| Block Entity Data（`tile_entity_data`） | 0x0A | 0x09 |
| Update Light | 0x25 | 0x23 |
| Multi Block Change | 0x3F | 0x3B |
| Set Slot | 0x16 | 0x15 |
| Window Items | 0x14 | 0x13 |
| Trade List | 0x28 | 0x26 |
| Entity Metadata | 0x4D | 0x44 |
| World Particles | 0x24 | 0x22 |
| Named Sound Effect | 0x19 | 0x18 |
| Explosion | 0x1C | 0x1B |
| Block Change | 0x0C | 0x0B |
| Block Action | 0x0B | 0x0A |
| Open Window | 0x2E | 0x2D |
| Set Cooldown | 0x17 | 0x16 |
| Entity Equipment | 0x50 | 0x47 |
| Synchronize Player Position | 0x38 | 0x34 |
| Window Click（サーバー行き） | 0x08 | 0x09 |
| Set Creative Slot（サーバー行き） | 0x28 | 0x28 |
| Enchant Item（サーバー行き） | 0x07 | 0x08 |
| Block Dig（サーバー行き） | 0x1A | 0x1B |
| Block Place（サーバー行き） | 0x2E | 0x2E |
| Use Item（サーバー行き） | 0x2F | 0x2F |
| Update Sign（サーバー行き） | 0x2B | 0x2B |
| Set Beacon Effect（サーバー行き） | 0x24 | 0x24 |
| Update Structure Block（サーバー行き） | 0x2A | 0x2A |
| Edit Book（サーバー行き） | 0x0B | 0x0C |
| Entity Destroy（`entity_destroy`／`destroy_entity`） | 0x3A | 0x36 |
| Update Time | 0x58 | 0x4E |
| Login Start | 0x00 | 0x00 |
| Login Success | 0x02 | 0x02 |
| Login Set Compression | 0x03 | 0x03 |

## Join Game／Respawn codec

情報源：公式 server_mappings.txt（1.16.5 server.txt sha1 `41285beda6…`、client.txt sha1 `374c6b7895…`、両方 sha1 照合済み）と minecraft-data protocol.json
（pc/1.16.2／pc/1.17）。
実キャプチャ：実1.16.5 Join Game 30,847バイト／実1.17 Join Game 31,555バイト。
差 708 バイトはバイオーム2件と dimension element の min_y／height の有無によるもの。

**Join Game（ClientboundLoginPacket）**：**フィールド構成は同一**。LoginPacket は1.16.5が `px`、1.17が `ra`。
RespawnPacket は1.16.5が `qp`、1.17が `rv`。`simulationDistance` はどちらにも無く、
`lastDeathLocation` もどちらにも無い。
- フィールド：playerId、seed、hardcore、gameType、previousGameType、levels、
  registryHolder、dimensionType、dimension、maxPlayers、chunkRadius、reducedDebugInfo、
  showDeathScreen、isDebug、isFlat。`dimension` は両方とも NBT 値。
  minecraft-data では previousGameMode の型だけが違う（1.16 は u8、1.17 は i8。
  ワイヤ上は同じ 1 バイト。フィールド名・順序は同一の 15 フィールド）。
- `dimensionCodec`（Join Game 内の NBT）：`minecraft:dimension_type` は
  4件（overworld 0、overworld_caves 1、the_nether 2、the_end 3。1.16.5と
  1.17で同一順序）。`minecraft:worldgen/biome` は754が79件（id 0〜173）、755が81件（id 0〜175）。
  `minecraft:chat_type` はどちらにも無い。
- dimension element（overworld）：logical_height 256。754には min_y／height が無く、
  755には min_y 0・height 256 がある。
- Join Game フィールド実測値（実バニラ1.16.5の生キャプチャ。2回の実行で同じ30,847バイト）：
  entityId 217（2回目は224）、isHardcore false、gameMode 0、previousGameMode -1、
  worldNames 3 件（overworld／the_nether／the_end）、worldName minecraft:overworld、
  maxPlayers 5、**viewDistance 10**（server.properties=10 と一致）、reducedDebugInfo false、
  enableRespawnScreen true、isDebug false、isFlat false。
  フィールド15件を全てパースし、消費量 30,847／30,847 で完全一致。

**Respawn（ClientboundRespawnPacket）**：フィールド同一（8フィールド）。
dimension elementの置き換えのみ。copyMetadataは末尾で両方同一、dataToKeepはどちらにも無い。
Respawn の previousGameMode は u8（Join Game の i8 と違う）。
「none」の符号は不明のため範囲チェックせずそのまま写す。

**Registry Data パケット**：1.16.5／1.17には存在しない（設定フェーズは
1.20.2／764で追加）。レジストリは Join Game の dimensionCodec NBT で
送られる。

## チャンク（754⇔755）

情報源：protocol.json＋実バニラ1.16.5生キャプチャ
（チャンク-15,-7、10,633バイト。テストリソース
`cap754-chunk-x-15-z-7.bin`）＋両jarのjavap。実キャプチャの往復は
バイト完全一致（754→755→754）。

- **754**：16セクション（y 0〜15）＋**groundUp bool**＋bitMap＋heightmaps NBT＋
  **バイオーム varint[1024]（groundUpのときだけ）**＋光なし。
  **755**：16セクション＋bitMap＋heightmaps NBT＋**バイオーム int[1024] 常時**＋光なし。
- **BitStorageは両方ともnon-straddling**（BPE 5のData Array長は342。
  straddling式だと320）。754節・755節はnon-straddlingで実装した。
  757節のstraddling前提は直していない（判断待ち。下の「対象外」を参照）。
- 754→755でgroundUp=falseのとき、755のbiomesは平原（1）で補完。
  1.17に部分更新パケットが無いため、bitMapに無いセクションは755側で空になる（制約）。
- バイオームは754配列の [64s, 64s+64) がセクションsに対応
  （javapで `ChunkBiomeContainer` のindex式を確認。755と同じ）。
  bitMapに無いセクションのバイオームも配列の値をそのまま使う。
- ブロックエンティティ：754も755も名前付きNBTにid／x／y／zを含む
  （生キャプチャで確認）。
- 755→754でsculk_sensorのブロックエンティティは落とす（1.16.5に無い種別33）。754→755は全て通す。
- Update Lightのマスクはビットi＝セクション(i-1)。1.16.5は18ビット、
  1.18は26ビット。マスクは±4ずらす。
- メタデータ型番号は1.16.5／1.17で同一（0〜18。slot=6、ブロック状態=13、
  粒子=15）。757節の1.19番号付けは使えない（判断待ちのまま）。
- 757→754のChunk Dataは**光を落とす**（光抽出の配線なし。下の「対象外」を参照）。
  754→757のChunk Dataは光を合成しない（754に光が無いため。空の光を送る）。
- チャンク形式の違いが protocol.json＋javap＋生キャプチャで確定しているため、
  1.17.1の生キャプチャ `cap756-chunk-21-17.bin` は755形式のチャンクであって
  754形式ではない（754専用の生キャプチャは `cap754-chunk-x-15-z-7.bin` を使うこと）。

## チャット（754は署名なし）

- **Chat（クライアント行き 0x0E／サーバー行き 0x03）**：1.16.5も1.17も
  署名なしの `chat`（サーバー行きは {message string}のみ）。
  754↔755は素通し。1.19は System Chat／Player Chat／Chat Message／
  Chat Command に分離（署名付き）。
- 754→759：754→755は素通しで、755→756→757→759の翻訳子が Chat Message
  （`translateChat757ToChatMessage`）か Chat Command
  （`translateChat757ToChatCommand`）へ変換する。チャット署名は現在時刻・
  salt 0・空署名・signedPreview=false で補完する（755と同じ簡略化）。
  テスト確認：`ProxySession754Test.clientPackets754Against759Convert`
  （chat 0x03→Chat Message 0x04）。
- 759→754：Chat Message は chat へ変換する（`ProxySession754Test.clientPackets759Against754Convert`。
  Chat Message 0x04→chat 0x03。署名は捨てられる）。Player Chat は落とす（署名付きのため。
  ただし現状は754クライアントは759サーバーに参加できないため、この組み合わせでは
  実機で確認できない。翻訳子としての落としは755と同じ扱い）。
- **方向Bの制限（テスト確認）**：754サーバー→755クライアントでは、Join Game／Respawn／
  Spawn Position は届くが、**chat（0x0E）／Set Slot（0x15）／Window Items（0x13）／
  Entity Destroy（0x36）は汎用経路で落とされる**
  （`ProxySession754Test.joinAndRespawn754Against755Convert` で確認。
  変換器自体は存在するが、ProxySession からの呼び出し口が無いためセッション経由では落とす。
  756サーバー→754クライアントの汎用パケットも同じく落とす。ノート §14.9）。
- **署名チェーンの検証**：境界を越えると署名チェーンはつながらない。
  正直な制限として残す。

## 代替表（754にしかないもの・755にしかないもの）

`RegistryTables754.java`（50表。ブロック状態の下向き表だけ `RegistryTables754BlockState.java` に分割。
1ファイルだと `<clinit>` が64KBを超えるため）で作ったもの。
改名・削除・新規は上の表の通り。補正はブロック状態46件・アイテム5件・音35件の計86件。
反転表は名前の真値と一致する（`verify-754-tables.py` 43/43で確認）。

- アイテム：755→754は新125件を代替表で付け替える。改名は grass_path→dirt_path の1件。
- 音（755=1,190件、754=992件）：755→754は新199件を代替表（`SOUND_FALLBACK_754`）で付け替える。
  754→755は改名1件（item.sweet_berries.pick_from_bush→block.sweet_berry_bush.pick_berries）。
- パーティクル（755=89件、754=72件）：新17件を代替表で付け替える。
- バイオーム（755=81件、754=79件）：dripstone_caves・lush_caves は754に無いため順向きは平原（1）へ。
  隙間の id は平原（1）で埋める。バイオーム表はワイヤ id 空間（176 件）で作ること
  （`biomes-1.16.json` は79件で id が0〜173の非連番。位置リストで作ると誤写像になる）。
- ブロック状態：大釜の反転真値は patch_cauldron_truth で上書き
  （754 cauldron[level=N]：N=0→755 の裸の cauldron、N=1〜3→755 の water_cauldron[level=N]）。
- エフェクト・ブロックエンティティ種別：エフェクトは32/32同一順序のため恒等写像。
  ブロックエンティティは 755→754 は 33（sculk_sensor）→DROP、754→755 は 0〜32 恒等。
- ダメージ種別は 42 件の恒等写像プレースホルダ。

## Client Information（0x05両方）

- 754の Client Information は**6フィールド**（locale／viewDistance i8／chatFlags varint／
  chatColors／skinParts u8／mainHand varint。**disableTextFiltering は無い**）。
  755は同じ7フィールドの末尾に disableTextFiltering（755の Client Information は7フィールド）。
- 754→755は末尾に disableTextFiltering=false を補完する
  （`translateClientInformation754To755`。`directPackets754Against755Convert` で確認。
  ペイロード長が1バイト増える）。755→754は末尾を除去する。
- 754→759の Client Information は末尾が757形式になる
  （`ProxySession754Test.clientPackets754Against759Convert` で確認：
  enableTextFiltering に反転、enableServerListing を true で補完）。
- `enableServerListing` は759→754では落とし、754→759では **trueで補完**する。
  **この既定値は推論による選択で未確認**（実バニラ1.16.5クライアントの送信値は
  未確認）。実機での確認が必要。
- 753の Client Information も**6フィールド**で754と同一（`mc-1.16.3-protocol.json` の `packet_settings` で確認。Stage D §15.2）。
  753⇔754は恒等写像。753→755は754経由で末尾に disableTextFiltering=false を補完する
  （`translateClientInformation754To755`。753→755の直接の単体テストは無いため、経路の記述はコード確認）。
  753→759では末尾が757形式になる（`ProxySession753Test.clientPackets753Against759Convert` で確認：
  enableTextFiltering に反転、enableServerListing を true で補完）。
- 763→754の Client Information は素通し（763→754変換器なし。
  末尾フィールド差分は未対応。`ProxySession754bTest.clientInformation763To754FallsBackToPassthrough` で確認）。

## 接続の流れ

### 754クライアント → 内部754サーバー

1. ハンドシェイク（754、状態2）。
2. Login Start（名前のみ）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x24、codec 付き）。
5. Spawn Position（0x42）。チャンク（0x20）・Player Info（0x32）などが届く。

### 754クライアント → 内部755サーバー（参加できる）

1. ハンドシェイク（754、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ。755サーバーの Join Game（0x26）を754形式（0x24）に
   変換する（`handleServerJoinGame755` の is754() 分岐→
   `PlayTranslator.translateJoinGame755To754`。codec は作り直す）。
5. Spawn Position（0x42）。リソースパックは754形式で送る（`pack754`、
   無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 753クライアント → 内部754サーバー（参加できる）

1. ハンドシェイク（753、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。754サーバーの Join Game（0x24）をそのまま753形式（0x24）で返す
   （`handleServerJoinGame754` の `is753()` 分岐。codec は恒等写像のため書き換えない）。
5. Spawn Position（0x42）。リソースパックは753形式で送る（`pack753`、
   無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 753クライアント → 内部755サーバー（参加できる）

1. ハンドシェイク（753、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ。755サーバーの Join Game（0x26）を754形式（0x24）に
   変換する（`handleServerJoinGame755` の `is753()` 分岐→
   `PlayTranslator.translateJoinGame755To754`。codec は作り直す）。
5. Spawn Position（0x42）。リソースパックは753形式で送る（同上）。

### 755クライアント → 内部753サーバー

1. ハンドシェイク（755、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。753サーバーの Join Game（0x24）を755形式（0x26）に
   変換する（`handleServerJoinGame753`→753→754（恒等写像）→`handleServerPlayFrom754`→
   `translateJoinGame754To755`→`handleServerPlayFrom755`。codec は作り直す）。
5. Spawn Position（0x4B。753の0x42に angle 0.0f を補う）。
   リソースパックは755形式で送る（`pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 751クライアント → 内部751サーバー

1. ハンドシェイク（751、状態2）。
2. Login Start（名前のみ）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x24、codec 付き）。
5. Spawn Position（0x42）。チャンク（0x20）・Player Info（0x32）などが届く。

### 751クライアント → 内部753サーバー（参加できる）

1. ハンドシェイク（751、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。753サーバーの Join Game（0x24）をそのまま751形式（0x24）で返す
   （`handleServerJoinGame753` は is753() だけを直接返し、751クライアントは
   `translateJoinGame753To754` で754形式にして `handleServerPlayFrom754` に委譲し、
   `handleServerJoinGame754` の `is754() || is753() || is751()` 分岐で 0x24 を直接返す。
   codec は恒等写像のため書き換えない。上の対応表を参照）。
5. Spawn Position（0x42）。リソースパックは751形式で送る（`pack751`、
   無ければ `pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 751クライアント → 内部754サーバー（参加できる）

1. ハンドシェイク（751、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。754サーバーの Join Game（0x24）をそのまま751形式（0x24）で返す
   （`handleServerJoinGame754` の `is751()` 分岐。codec は恒等写像のため書き換えない。
   751クライアント×754サーバーの直接の単体テストは無いため、テスト確認済みとは書かない）。
5. Spawn Position（0x42）。リソースパックは751形式で送る（同上）。

### 751クライアント → 内部755サーバー（参加できる）

1. ハンドシェイク（751、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ。755サーバーの Join Game（0x26）を754形式（0x24）に
   変換する（`handleServerJoinGame755` の `is751()` 分岐→
   `PlayTranslator.translateJoinGame755To754`。codec は作り直す）。
5. Spawn Position（0x42）。リソースパックは751形式で送る（同上）。

### 751クライアント → 内部756サーバー（参加できない）

1. ハンドシェイク（751、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ進むが、サーバー→クライアントの Join Game が
   `handleServerJoinGame763` の is751() 分岐（`ProxySession.java:7899`）で落とされる。
   Respawn も落とされる。Keep Alive だけは通るが Join Game が無いので参加できない。
   **確認するのは「参加できないこと自体」である**（`docs/PLAYTEST-1.16.md` の「4.」を参照）。
   参加できる前提のチェックリストにしないこと。

### 736クライアント → 内部736サーバー

1. ハンドシェイク（736、状態2）。
2. Login Start（名前のみ）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x25、codec 付き。1,159バイト）。
5. Spawn Position（0x42）。チャンク（0x21）・Player Info（0x33）などが届く。

### 736クライアント → 内部751サーバー（参加できる）

1. ハンドシェイク（736、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。751サーバーの Join Game（0x24）を736形式（0x25）に
   変換する（`handleServerJoinGame751` の `is736()` 分岐→
   `PlayTranslator.translateJoinGame751To736`。isHardcore を除去し、dimension を NBT から文字列に直し、
   codec から dimension_type／biome のレジストリを除去する。`ProxySession736bTest.joinGame751To736RewritesCodec` で
   セッションレベルで確認済み）。
5. Spawn Position（0x42）。リソースパックは736形式で送る（`pack736`、
   無ければ `pack751`、無ければ `pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 736クライアント → 内部753サーバー（参加できる）

1. ハンドシェイク（736、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。753サーバーの Join Game（0x24）を736形式（0x25）に
   変換する（`handleServerJoinGame753`→754形式にして `handleServerPlayFrom754` に委譲し、
   `handleServerJoinGame754` の `is736()` 分岐で 754→753→751→736の連鎖で 0x25 を直接返す。
   `ProxySession736bTest.joinGame753To736Chain` でセッションレベルで確認済み）。
5. Spawn Position（0x42）。リソースパックは736形式で送る（同上）。

### 736クライアント → 内部754サーバー（参加できる）

1. ハンドシェイク（736、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。754サーバーの Join Game（0x24）を736形式（0x25）に
   変換する（`handleServerJoinGame754` の `is736()` 分岐→
   `translateJoinGame754To753`→`translateJoinGame753To751`→`translateJoinGame751To736`。
   codec は作り直す。**コード確認**。736クライアント×754サーバーの直接の単体テストは無いため、
   テスト確認済みとは書かない）。
5. Spawn Position（0x42）。リソースパックは736形式で送る（同上）。

### 736クライアント → 内部755サーバー（参加できる）

1. ハンドシェイク（736、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ。755サーバーの Join Game（0x26）を736形式（0x25）に
   変換する（`handleServerJoinGame755` の `is736()` 分岐→
   `translateJoinGame755To754`→`translateJoinGame754To753`→`translateJoinGame753To751`→
   `translateJoinGame751To736`。codec は作り直す。**コード確認**。736クライアント×755サーバーの
   直接の単体テストは無いため、テスト確認済みとは書かない）。
5. Spawn Position（0x42）。リソースパックは736形式で送る（同上）。

### 736クライアント → 内部756サーバー（参加できない）

1. ハンドシェイク（736、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ進むが、サーバー→クライアントの Join Game が
   `handleServerJoinGame756` に旧クライアント分岐が無く、757→759→…→763の連鎖を経て
   `handleServerJoinGame763` のガード（`ProxySession.java:8233`。
   `if (is757() || is756() || is755() || is754() || is753() || is751() || is736())` で落とす）で落とされる。
   Respawn も落とされる（`ProxySession.java:9204`・`9498` のガード）。
   クライアント→サーバー方向のパケットは 736→751→753→754→755→756の連鎖で756サーバーに届くが、
   Join Game が無いので参加できない。**確認するのは「参加できないこと自体」である**
   （`docs/PLAYTEST-1.16.md` の「4-2.」を参照）。参加できる前提のチェックリストにしないこと。
   775サーバーでは `ProxySession736Test.join775Against736Drops` で落としをテスト確認。
   756／757／759〜774サーバーは同じガードに到達することをコードで確認したが、各サーバーでの
   直接テストは無いため推論。

### 751クライアント → 内部736サーバー

1. ハンドシェイク（751、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。736サーバーの Join Game（0x25）を751形式（0x24）に
   変換する（`handleServerJoinGame736`→`translateJoinGame736To751`→`handleServerPlayFrom751`→
   `handleServerJoinGame751` の `is751()` 分岐で 0x24 を直接返す。codec は作り直す。
   `ProxySession736Test.joinAndRespawn736Against751Convert` でセッションレベルで確認済み）。
   Respawn（0x3a→0x39）・Spawn Position（0x42、そのまま）も同じテストで確認済み。
   ただし chat／Set Slot／Window Items／Entity Destroy は751クライアントに届かない
   （`handleServerPlayFrom763` の汎用経路で落とす。同じテストで chat の落としを確認。
   Set Slot は変換器レベルのみ確認。上の「送らない・落とすパケット」を参照）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは751形式で送る（`pack751`、無ければ `pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 753クライアント → 内部736サーバー

1. ハンドシェイク（753、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。736サーバーの Join Game（0x25）を753形式（0x24）に
   変換するはず（`handleServerJoinGame736`→751→753（恒等写像）→`handleServerPlayFrom753`→
   `handleServerJoinGame753` の `is753()` 分岐で 0x24 を直接返す。**推論**。
   736サーバー＋753クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは753形式で送る（`pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 754クライアント → 内部736サーバー

1. ハンドシェイク（754、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。736サーバーの Join Game（0x25）を754形式（0x24）に
   変換するはず（`handleServerJoinGame736`→751→753→754の連鎖で
   `handleServerJoinGame754` の `is754()` 分岐で 0x24 を直接返す。**推論**。
   736サーバー＋754クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは754形式で送る（`pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 755クライアント → 内部736サーバー

1. ハンドシェイク（755、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。736サーバーの Join Game（0x25）を755形式（0x26）に
   変換するはず（`handleServerJoinGame736`→751→753→754→755の連鎖で
   `handleServerJoinGame755` の `is755()` 分岐で 0x26 を直接返す。codec は作り直す。**推論**。
   736サーバー＋755クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x4B。736の0x42に angle 0.0f を補う）。
   リソースパックは755形式で送る（`pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 756クライアント → 内部736サーバー

1. ハンドシェイク（756、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。736サーバーの Join Game（0x25）を756形式（0x26）に
   変換するはず（`handleServerJoinGame736`→751→753→754→755→756の連鎖で
   `handleServerJoinGame756` の `is756()` 分岐で 0x26 を直接返す。**推論**。
   736サーバー＋756クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x4B。736の0x42に angle 0.0f を補う）。
   リソースパックは756形式で送る（`pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 736サーバー → 774クライアント

- Join Game は通るはず（736→751→753→754→755→756→757→759→763→774の連鎖。
  **直接テストは無いため推論**。736ノート §14.9で到達は未確認とされている。上の対応表を参照）。
- Respawn は落とされるはず（下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。
  **直接テストは無いため推論**）。
- 759クライアントには Set Slot／Window Items／Chunk Data／Entity Destroy が届く
  （`ProxySession736Test.serverPackets736Against759Reach` で確認。ただし Entity Destroy は count 1 のときだけ）。

### 735クライアント → 内部735サーバー

1. ハンドシェイク（735、状態2）。
2. Login Start（名前のみ）。
3. Login Success（UUID＋名前＋プロパティ数）。
4. すぐプレイ状態へ（設定フェーズなし）。Join Game（0x25、codec 付き。1,159バイト。
   素通し。`ProxySession735Test.join735Against735IsNative` で経路を確認済み）。
5. Spawn Position（0x42）。チャンク（0x21）・Player Info（0x33）などが届く。

### 735クライアント → 内部736サーバー（参加できる）

1. ハンドシェイク（735、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。736サーバーの Join Game（0x25）を735形式（0x25）に
   変換する（`handleServerJoinGame736` は `is736()` だけを直接返し、735クライアントは
   736→751変換（`translateJoinGame736To751`）を経て `handleServerPlayFrom751` に委譲し、
   `handleServerJoinGame751` の `is736() || is735()` 分岐で 0x25 を直接返す。
   736→751→736の往復で元の736形式に戻る（735⇔736は同一形式）。
   `ProxySession735bTest.joinGame736To735IsIdentity` でセッションレベルで確認済み）。
   Respawn（0x3a→0x3a）・Spawn Position（0x42）も同じく `respawn736To735IsIdentity`／
   `position736To735IsIdentity` で確認済み。
5. Spawn Position（0x42）。リソースパックは735形式で送る（`pack735`、
   無ければ `pack736`、無ければ `pack751`、無ければ `pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 735クライアント → 内部751サーバー（参加できる）

1. ハンドシェイク（735、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。751サーバーの Join Game（0x24）を735形式（0x25）に
   変換する（`handleServerJoinGame751` の `is736() || is735()` 分岐→
   `PlayTranslator.translateJoinGame751To736`。isHardcore を除去し、dimension を NBT から文字列に直し、
   codec から dimension_type／biome のレジストリを除去する。**コード確認**。735クライアント×751サーバーの
   直接の単体テストは無いため、テスト確認済みとは書かない。ただしこの `is735()` 分岐自体は
   `ProxySession735bTest.joinGame736To735IsIdentity`（736サーバー→735クライアント。736→751→735の経路）で
   実行されている）。
5. Spawn Position（0x42）。リソースパックは735形式で送る（同上）。

### 735クライアント → 内部753サーバー（参加できる）

1. ハンドシェイク（735、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。753サーバーの Join Game（0x24）を735形式（0x25）に
   変換する（`handleServerJoinGame753`→754形式にして `handleServerPlayFrom754` に委譲し、
   `handleServerJoinGame754` の `is736() || is735()` 分岐で 754→753→751→736の連鎖で 0x25 を直接返す。
   `ProxySession735bTest.joinGame753To735Chain` でセッションレベルで確認済み）。
   Respawn（0x39→0x3a）・Spawn Position（0x42）も `respawn753To735Chain`／
   `position753To735RoundTrips` で確認済み。
5. Spawn Position（0x42）。リソースパックは735形式で送る（同上）。

### 735クライアント → 内部754サーバー（参加できる）

1. ハンドシェイク（735、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ（設定フェーズなし）。754サーバーの Join Game（0x24）を735形式（0x25）に
   変換する（`handleServerJoinGame754` の `is736() || is735()` 分岐→
   `translateJoinGame754To753`→`translateJoinGame753To751`→`translateJoinGame751To736`。
   codec は作り直す。**コード確認**。735クライアント×754サーバーの直接の単体テストは無いため、
   テスト確認済みとは書かない）。
5. Spawn Position（0x42）。リソースパックは735形式で送る（同上）。

### 735クライアント → 内部755サーバー（参加できる）

1. ハンドシェイク（735、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ。755サーバーの Join Game（0x26）を735形式（0x25）に
   変換する（`handleServerJoinGame755` の `is736() || is735()` 分岐→
   `translateJoinGame755To754`→`translateJoinGame754To753`→`translateJoinGame753To751`→
   `translateJoinGame751To736`。codec は作り直す。**コード確認**。735クライアント×755サーバーの
   直接の単体テストは無いため、テスト確認済みとは書かない）。
5. Spawn Position（0x42）。リソースパックは735形式で送る（同上）。

### 735クライアント → 内部756サーバー（参加できない）

1. ハンドシェイク（735、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ進むが、サーバー→クライアントの Join Game が
   `handleServerJoinGame756` に旧クライアント分岐が無く、757→759→…→763の連鎖を経て
   `handleServerJoinGame763` のガード（`ProxySession.java:8447`。
   `if (is757() || is756() || is755() || is754() || is753() || is751() || is736() || is735())` で落とす）で落とされる。
   Respawn も落とされる（`ProxySession.java:9305`・`9418` のガード）。
   クライアント→サーバー方向のパケットは 735→736→751→753→754→755→756の連鎖で756サーバーに届くが、
   Join Game が無いので参加できない。**確認するのは「参加できないこと自体」である**
   （`docs/PLAYTEST-1.16.md` の「4-3.」を参照）。参加できる前提のチェックリストにしないこと。
   775サーバーでは `ProxySession735Test.join775Against735Drops` で落としをテスト確認。
   756／757／759〜774サーバーは同じガードに到達することをコードで確認したが、各サーバーでの
   直接テストは無いため推論。

### 736クライアント → 内部735サーバー

1. ハンドシェイク（736、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。735サーバーの Join Game（0x25）を736形式（0x25）に
   変換する（`handleServerJoinGame735`→`handleServerPlayFrom736`→
   `handleServerJoinGame736` の `is736()` 分岐で 0x25 を直接返す。735⇔736は恒等写像のため
   ペイロードは変わらない。codec は作り直さない。
   `ProxySession735Test.joinAndRespawn735Against736Convert` でセッションレベルで確認済み）。
   Respawn（0x3a→0x3a）・Spawn Position（0x42、そのまま）も同じテストで確認済み。
   ただし chat／Set Slot／Window Items／Entity Destroy は736クライアントに届かない
   （`handleServerPlayFrom763` の汎用経路で落とす。同じテストで chat の落としを確認。
   Set Slot は変換器レベルのみ確認。上の「送らない・落とすパケット」を参照）。
   Ping（0x12）・Update Light（0x24）も736クライアントには落とす（同じテストで確認。
   735⇔736の段では素通しだが、754⇔755の段で落とされる）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは736形式で送る（`pack736`、無ければ `pack751`、無ければ `pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 751クライアント → 内部735サーバー

1. ハンドシェイク（751、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。735サーバーの Join Game（0x25）を751形式（0x24）に
   変換するはず（`handleServerJoinGame735`→`handleServerPlayFrom736`→`handleServerJoinGame736`→
   `translateJoinGame736To751`→`handleServerPlayFrom751`→
   `handleServerJoinGame751` の `is751()` 分岐で 0x24 を直接返す。codec は作り直す。**推論**。
   735サーバー＋751クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは751形式で送る（`pack751`、無ければ `pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 753クライアント → 内部735サーバー

1. ハンドシェイク（753、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。735サーバーの Join Game（0x25）を753形式（0x24）に
   変換するはず（`handleServerJoinGame735`→736→751→753（恒等写像）→`handleServerPlayFrom753`→
   `handleServerJoinGame753` の `is753()` 分岐で 0x24 を直接返す。**推論**。
   735サーバー＋753クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは753形式で送る（`pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 754クライアント → 内部735サーバー

1. ハンドシェイク（754、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。735サーバーの Join Game（0x25）を754形式（0x24）に
   変換するはず（`handleServerJoinGame735`→736→751→753→754の連鎖で
   `handleServerJoinGame754` の `is754()` 分岐で 0x24 を直接返す。**推論**。
   735サーバー＋754クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは754形式で送る（`pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 755クライアント → 内部735サーバー

1. ハンドシェイク（755、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。735サーバーの Join Game（0x25）を755形式（0x26）に
   変換するはず（`handleServerJoinGame735`→736→751→753→754→755の連鎖で
   `handleServerJoinGame755` の `is755()` 分岐で 0x26 を直接返す。codec は作り直す。**推論**。
   735サーバー＋755クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x4B。735の0x42に angle 0.0f を補う）。
   リソースパックは755形式で送る（`pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 756クライアント → 内部735サーバー

1. ハンドシェイク（756、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。735サーバーの Join Game（0x25）を756形式（0x26）に
   変換するはず（`handleServerJoinGame735`→736→751→753→754→755→756の連鎖で
   `handleServerJoinGame756` の `is756()` 分岐で 0x26 を直接返す。**推論**。
   735サーバー＋756クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x4B。735の0x42に angle 0.0f を補う）。
   リソースパックは756形式で送る（`pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 735サーバー → 774クライアント

- Join Game は通るはず（735→736→751→753→754→755→756→757→759→763→774の連鎖。
  **直接テストは無いため推論**。735サーバー→774クライアントの Join Game 到達は未確認（735ノート §14.9）。上の対応表を参照）。
- Respawn は落とされるはず（下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。
  **直接テストは無いため推論**）。
- 759クライアントには Set Slot／Window Items／Chunk Data／Entity Destroy が届く
  （`ProxySession735Test.serverPackets735Against759Reach` で確認。ただし Entity Destroy は count 1 のときだけ。
  count 2 は754→755の段で落とす。同テストで確認）。

### 753クライアント → 内部751サーバー

1. ハンドシェイク（753、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。751サーバーの Join Game（0x24）を753形式（0x24）に
   変換する（`handleServerJoinGame751`→751→753（恒等写像）→`handleServerPlayFrom753`→
   `handleServerJoinGame753` の `is753()` 分岐で 0x24 を直接返す。codec は変わらない）。
   ただし chat／Set Slot／Window Items／Entity Destroy は753クライアントに届かない
   （`ProxySession751Test.joinAndRespawn751Against753Convert` で確認。上の対応表を参照）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは753形式で送る（`pack753`、無ければ `pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 754クライアント → 内部751サーバー

1. ハンドシェイク（754、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。751サーバーの Join Game（0x24）を754形式（0x24）に
   変換するはず（`handleServerJoinGame751`→751→753（恒等写像）→`handleServerPlayFrom753`→
   `handleServerJoinGame754` の `is754()` 分岐で 0x24 を直接返す。**推論**。
   751サーバー＋754クライアントの直接の単体テストは無いため推論）。
5. Spawn Position（0x42。angle は無いまま）。
   リソースパックは754形式で送る（`pack754`、無ければ `pack755`、無ければ `pack756`、無ければ `pack757`、無ければ `pack759`、
   無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、無ければ `pack763`）。

### 755クライアント → 内部751サーバー

1. ハンドシェイク（755、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。751サーバーの Join Game（0x24）を755形式（0x26）に
   変換する（`handleServerJoinGame751`→751→753（恒等写像）→`handleServerPlayFrom754`→
   `translateJoinGame754To755`→`handleServerPlayFrom755`。codec は作り直す）。
5. Spawn Position（0x4B。751の0x42に angle 0.0f を補う）。
   リソースパックは755形式で送る（`pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 754クライアント → 内部756サーバー（参加できない）

1. ハンドシェイク（754、状態2）。
2. Login Start（名前のみ）。
3. Login Success。
4. すぐプレイ状態へ進むが、サーバー→クライアントの Join Game が
   `handleServerJoinGame763` の is754() 分岐（`ProxySession.java:7364`）で落とされる。
   Respawn も落とされる。Keep Alive だけは通るが Join Game が無いので参加できない。
   **確認するのは「参加できないこと自体」である**（`docs/PLAYTEST-1.16.md` の「4.」を参照）。
   参加できる前提のチェックリストにしないこと。

### 755クライアント → 内部754サーバー

1. ハンドシェイク（755、状態2）。
2. Login Start（名前のみ）。そのまま通す。
3. Login Success。
4. すぐプレイ状態へ。754サーバーの Join Game（0x24）を755形式（0x26）に
   変換する（`handleServerJoinGame754`→`translateJoinGame754To755`→
   `handleServerPlayFrom755`。codec は作り直す。359バイト→1,474バイトに膨らむ）。
5. Spawn Position（0x4B。754の0x42に angle 0.0f を補う）。
   リソースパックは755形式で送る（`pack755`、無ければ `pack756`、無ければ `pack757`、
   無ければ `pack759`、無ければ `pack760`、無ければ `pack761`、無ければ `pack762`、
   無ければ `pack763`）。

### 754サーバー → 774クライアント

- Join Game は通るはず（三段変換 754→755→756→757→759→763→774。
  **直接テストは無いため推論**。上の対応表を参照）。
- Respawn は落とされるはず（下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。
  755サーバーではテスト確認。**754サーバーでの直接テストは無いため推論**）。

## プレイ中の翻訳

- **チャンク**：ブロック状態（754=17,112／755=20,342）・バイオーム
  （754=79／755=81）・ブロックエンティティ種別（754=33／755=34）を付け替える
  （変換表あり）。754→755のChunk Dataは groundUp／bitMap／biomes の形式を直す。
  755→754では sculk_sensor のブロックエンティティを落とす。
- **スロット・ウィンドウ**：アイテムID（754=976／755=1,100）の付け替えをする。
  754⇔755方向では Set Slot／Window Items はフィールド同一のため ID 付け替えだけ。
  754クライアントのWindow Clickは754→755→756→757→759の連鎖で
  759の0x0Aに届く（stateId 0付き。テストで確認）。
  **763→754方向は落とすまま**（未実装。`ProxySession754Test.clientPackets763Against754Convert` で確認）。
- **エンティティ**：エンティティ種別は754=108／755=113のため付け替えあり。
  Spawn Entity／Living／Painting（0x00／0x02／0x03）は両方で同一のため素通し。
  Entity Destroy は754⇔755の直接変換では配列⇔単体に直すが、
  セッション経由では754サーバー→755クライアントで落とされる（上の「チャット」を参照）。
  756サーバー→754クライアントでも落とされる。
  754→759では Entity Destroy が届く（count 1 のときだけ。757→759の段の隙間は
  フォローアップで直した。`serverPackets754Against759Reach` で確認）。
- **音・パーティクル**：音（754=992／755=1,190）・パーティクル
  （754=72／755=89）は付け替えあり。1.19の Sound 系パケットの
  末尾の seed i64 は754向けに落とす（755の翻訳子を経由する）。
- **位置・Server Data・Trade List**：上の表の通り。Server Data は754向けに落とす。
- **Client Information**：754⇔755は末尾の disableTextFiltering の補完／除去。763→754は素通し。
- **クライアント→サーバー（方向B、テスト確認）**：759クライアントの Chat Message 0x04→chat 0x03、
  Block Dig 0x28→0x1B（`ProxySession754Test.clientPackets759Against754Convert`。
  `loginTo(759, 754)`＝クライアント759・サーバー754）。763クライアントの
  Chat Message 0x05→chat 0x03、Block Dig 0x1D→0x1B（同 `clientPackets763Against754Convert`）。
  754クライアントの chat 0x03→Chat Message 0x04、Block Dig 0x1B→0x1C
  （`ProxySession754Test.clientPackets754Against759Convert`。sequence は 0 を補う）。

## 送らない・落とすパケット（754向け）

- 756〜775サーバー→754クライアントの Join Game／Respawn（上の対応表を参照。バグではない）
- 756〜775サーバー→754クライアントの汎用パケット（`handleServerPlayFrom763` の汎用経路の
  落としリストに754がある。763→754の汎用パケット変換が未実装のため。
  chat／Set Slot／Window Items／Entity Destroy は754サーバー→755クライアントでテスト確認。
  756サーバー→754クライアントの汎用パケットも落とすまま。ノート §14.9）
- 754サーバー→755クライアントの chat／Set Slot／Window Items／Entity Destroy
  （汎用経路で落とす。変換器はあるが呼び出し口が無い。上の「チャット」を参照）
- 754サーバー→766〜775クライアントの Respawn（下流で落とす。774は755サーバーでテスト確認。
  **754サーバーでの直接テストは無いため推論**）
- Player Chat（759→754。署名付きのため変換できない。ただし現状は754クライアントは
  759サーバーに参加できないため実機では確認できない）
- server_data、should_display_chat_preview、chat_preview、chat_preview_to_server（754に無いため。変換器なしでProxySessionがIDで落とす）
- Window Click／Set Creative Slot／Edit Book（763→754方向。未実装のため落とす）
- Edit Book（763→754方向のみ落とす。754⇔755ではフィールド同一のため素通し＋ID付け替え）
- ping／pong（754↔755両方向。transaction形式と {id i32} 形式は互換性が無いため落とす）
- Update Light（全方向。マスク形式が違い変換器が無いため落とす）
- Sculk Vibration Signal（755→754。754に無いため落とす）
- entity（クライアント行き 0x2a。1.17で削除され PacketIds の対象外。ViaVersionも落としている）
- 757→754のChunk Dataの光（光抽出の配線なし。チャンク自体は届くが光が無い）
- Declare Recipes／Statistics／Tags／World Event ID：全方向で未対応（既存の制限と同じ）
- ブロックエンティティ種別が範囲外の場合：Block Entity Data をパケットごと落とす
- 754→755の Entity Destroy の count 0／2以上（変換器が null を返す。754の1パケットでは表せないため。
  分割送信には ProxySession 側の対応が要る。呼び出し口が無いため現状は汎用経路で落とす扱い）
- **753向けも同じ**：753⇔754は恒等写像のため、上の754向けの落としは753向けにもそのまま当てはまる
  （753サーバー→754／755クライアントの chat／Set Slot／Window Items／Entity Destroy は汎用経路で落とす。
  753サーバー→755クライアント分は直接の単体テストが無いため推論。上の対応表を参照）。
  763〜775→753の Join Game／Respawn は落とすまま。Ping 763+→753は落とす（From763のみ。
  766／775等の From*ハンドラには754と同様の既存の隙間がある）。entity（753のみ 0x2a）は落とす
  （PacketIds の対象外のまま）。763→753の Window Click／Set Creative Slot／Edit Book は落とす
- **751向けも同じ**：751⇔753は恒等写像のため、上の754向けの落としは751向けにもそのまま当てはまる
  （751サーバー→753クライアントの chat／Set Slot／Window Items／Entity Destroy は汎用経路で落とす。
  `ProxySession751Test.joinAndRespawn751Against753Convert` でテスト確認。751サーバー→755クライアント分は
  直接の単体テストが無いため推論。上の対応表を参照）。
  763〜775→751の Join Game／Respawn は落とすまま。Ping 763+→751は落とす（From763のみ。
  766／775等の From*ハンドラには754と同様の既存の隙間がある）。entity（751のみ 0x2a）は落とす
  （PacketIds の対象外のまま）。763→751の Window Click／Set Creative Slot／Edit Book は落とす
- **736向けも同じ**：736⇔751はパケットIDが62件変わるが、落としの構図は751向けと同じ
  （736サーバー→751クライアントの chat／Set Slot／Window Items／Entity Destroy は汎用経路で落とす。
  chat の落としは `ProxySession736Test.joinAndRespawn736Against751Convert` でテスト確認。
  Set Slot は変換器レベルのみ確認で、セッション経由では落とされる。上の「接続の流れ」を参照）。
  763〜775→736の Join Game／Respawn は落とすまま（`handleServerJoinGame763` のガード `ProxySession.java:8233`。
  775は `ProxySession736Test.join775Against736Drops` でテスト確認。756／757／759〜774は同じガードに
  到達することをコードで確認）。Ping 763+→736は落とす（`ProxySession736bTest.ping763To736Drops` で確認。
  From763のみ。766／775等の From*ハンドラには754と同様の既存の隙間がある）。entity（736のみ 0x2b）は落とす
  （PacketIds の対象外のまま）。763→736の Window Click／Set Creative Slot／Edit Book は落とす
  （`ProxySession736Test.clientPackets763Against736Convert` で確認。Chat Message と Block Dig は通る）
- **736サーバー→751〜755クライアントの汎用パケットも落とす**：736サーバー→751クライアントの
  chat は `ProxySession736Test.joinAndRespawn736Against751Convert` で落としを確認。
  753／754／755クライアント分は直接の単体テストが無いため推論（751と同じ非対称性）。
  ただし759クライアントには Set Slot／Window Items／Chunk Data／Entity Destroy が届く
  （`ProxySession736Test.serverPackets736Against759Reach` で確認。Entity Destroy は count 1 のときだけ）

## 対象外・未実装（正直な一覧）

- **本物のゲームクライアントでの確認は未実施**：全バージョン共通。手順書は
  `docs/PLAYTEST-1.16.md`。偽クライアント＋本物のバニラでの Join Game
  キャプチャ（30,847バイト）と実バニラ1.16.5の起動確認
  （`Done (62.596s)!`、pack metadataエラー0件）は済み。
  プロキシ経由の統合テスト（Fake754Client）は Stage E で実施中（下の「統合テスト」を参照）。
- **763〜775→754の Join Game／Respawn は落とすまま**：763形式は759形式＋
  portalCooldown で変換自体は容易だが、763のコーデックのバイオーム順が
  cherry_grove の1.20プロトコルIDに依存し未測定
  （minecraft-data の biomes.json はアルファベット順でプロトコル順ではない。
  1.20のデータジェネレータ実行はJVMのTCP遮断で不可）。
  764〜775は Join Game の dimension をレジストリIDで送り、dimension_type
  レジストリは設定フェーズで別送されるが ProxySession が保持していない。
  777の §21.5と同じ種類の残課題。
- **754サーバー→774クライアントの Respawn は落とされるはず**：Join Game は
  通るはずだが Respawn は下流の `handleServerPlayFrom763` の Respawn 分岐で落とす。
  **754サーバーでの直接テストは無いため推論**。
- **763〜767の内部サーバー＋754クライアントの Join Game／Respawn は引き続き
  落とされる**（777の §21.5と同じ種類）。
- **759／760サーバー＋766以上クライアントのログイン合成ギャップ**：
  754だけ直した（下の「罠」を参照）。759／760サーバーにも同じ隙間があるが
  本タスクの範囲外。
- **Window Click／Set Creative Slot／Edit Book の754行きは未実装**：763→754方向は
  落とすまま。754⇔755方向は変換する。
- **光の抽出の配線なし**：`ChunkTranslator.extractUpdateLightFromChunkData757`
  自体は動作確認済み（テストで756形式Update Lightが返ることを検証）が、
  呼び出し口が無い。757サーバー→754クライアントのChunk Dataは
  `From754→From755→From756→From757→…→From763` の連鎖で汎用経路に落とされるため、
  `to755ClientPacketFrom754` にはChunk Dataは決して来ない。配線には763→757の
  サーバー側変換（755と同じ既知のギャップ）が必要。推測で無理に繋げていない。
- **Client Information の763→754は素通し**：末尾フィールド差分は未対応。
- **Gradleテストの実行は未実施**（このVMにGradleが無いため）。
  単体テストはJUnitスタブ＋リフレクション呼び出し／JUnit 5.10.1実jar＋
  自作ランナーで実行した（下の「テスト」を参照）。
- **1.16.5の実バニラ起動は実施済み**（Done 62.596s）。1.16.5クライアントでのパック受け入れ・
  実機プレイは未実施。
- **チャットの署名チェーンはバージョン境界を越えられない**：754→759は
  現在時刻・salt 0・空署名・signedPreview=false で補完する。
  実機の759クライアントが754サーバーからのチャットをどう表示するかは未確認。
- **Player Chat 759→754は落とす**：翻訳子としては759サーバーからの Player Chat を
  754形式に変換できないため落とす（上の「チャット」を参照）。なお現状は Join Game が
  落とされるため754クライアントは759サーバーに参加できない。
  実機での確認が必要。
- **Join Game codec の作り直し**：754→755ではペイロードをそのまま返さず、
  codec を書き換える（`translateJoinGame754To755`）。バイオーム79件→81件、
  dimension element に min_y／height を足す。
- **763＋online-mode時に他プレイヤーから見たスキンが既定スキンになる場合がある**：
  754＋online-modeも同じ制限（内部バニラがoffline-modeのため
  Player Info にプロパティを載せない）。
- **外部モードで pack754 が無い場合**：754クライアントには755パック
  （pack_format 7）が出る。754はツリー内最古のため古いフォールバックが存在せず、
  隣接する新しいパックに落とす（757→759（8→9）の前例と同じ決まり通り。
  754→755はpack_format 6→7の境界を越える）。
  javadoc／コメントに記録済み。
- **公開HTTPSでのリソースパック置き場は未定**：外部URLを指定する仕組みは
  あるが、パックを置く場所はまだ決まっていない。既定はloopback配信。
- **Bedrock→754**：`JavaBackendConnection` の serverProtocol 754 を755扱いにした
  （Login Success→即プレイ、Join Game 754、Respawn 754→755→756→757→759連鎖、
  チャット送受信、Player Info、Living／Painting ディスパッチ、
  Client Information 切り出し）。チャンクは754のワイヤ形式（groundUp＋bitMap varint＋
  groundUpのときだけバイオーム varint[1024]・BitStorage non-straddling）
  で読む。754固有の追加は Entity Destroy の配列読み（`parseRemoveEntity754`）と
  Respawn連鎖754→755→756→757→759の2点だけ。Bedrock 側の統合テストはまだ無い。Bedrock のインベントリは引き続き未対応。
- **770〜775のチャンク読みの不整合の疑い**：`BedrockChunkTranslator` と
  Java側 `ChunkTranslator` の770〜775読みは「1 short＋straddling」だが、
  実バニラ26.1の生キャプチャ＋バイトコードは「2 shorts＋non-straddling」を示す。
  754は正しい形式で実装し、770〜775は触っていない（Muse01／ユーザーの判断待ち）。
  Stage B の ChunkTranslator 757節も straddling 前提で、実バニラ1.18
  （non-straddling）と食い違うことが756の実測で確定した（同じく判断待ち）。
- **メタデータ型番号の1.19付け**：757節は1.19の型番号で書かれているが、
  1.16.5／1.17は0〜18で同一のため757節の番号付けは754⇔755では使えない。
  判断待ち（本タスクでは触っていない）。
- **`RegistryRemapper` の `ITEM_REVERSE_MAP` と `ITEM_763_TO_765` の食い違い**：
  ボート・レコード・絵画で値が違う（Bedrock側の754分岐で発見。755のときと
  同じ箇所。`RegistryRemapper` 自体は触っていない）。
  プロキシ側の `mapItemReverse` 経路（754／755／756／757／759〜762クライアント）にも
  影響する疑い。修正は統括側（Muse01／ユーザー）の判断待ち。
- **mapItemReverse の757／759／760／761／762分岐の逆順合成の不具合**：
  隣接表を逆順に合成しており正しい合成と724/1101件食い違う（実測）。
  754分岐・755分岐・756分岐だけ正しい順序（最も内側から合成）。唯一の呼び出し元は
  `ChunkTranslator.java:529`。修正は統括側（Muse01／ユーザー）の判断待ち。
- **775節の `experience_orb = 47` の古いリテラルは直していない**
  （Muse01／ユーザーの判断待ち）。
- `VanillaServerHost.stop()` で `IllegalThreadStateException: process hasn't exited`
  が出ることがある（全バージョン共通の既存の問題。直していない。
  実バニラ1.16.5の起動確認では destroy後5秒以内に終了し exit code 143、STOP-THREWなし。
  755では `IllegalThreadStateException` が再現した）。
- `build-numbers.properties`／`versionedJars` への1.16.5追加はできない
  （このVMのツリー内にファイルが無いため。ローカルmainの同ファイル待ち）。
- Tile Entity Dataの757→756でNBTにidを付けられない（ベストエフォート）、
  `enableServerListing=true` の既定値は推論で未確認（コードに記録）。
- **753サーバー＋774クライアントの Join Game 到達は未確認**：754のノート §14.9には確認済みとあるが、
  753では対応するテスト（`loginTo(774, 753)` を使うもの）が無い。754の §16.9と同じ構図で、
  本稿では推論扱いとする。
- **内部753サーバー側の2引数分岐なし**：`differences(756, 753)` 等は NOT_SUPPORTED を返す
  （Stage B §14.2）。Stage Eのマトリクスで必要なら追加する。
- **756／757クライアント×753サーバーの Join Game／Respawn は直接テスト無し**（推論。上の対応表を参照）。
- **751サーバー＋774クライアントの Join Game 到達は未確認**：754のノート §14.9には確認済みとあるが、
  751では対応するテスト（`loginTo(774, 751)` を使うもの）が無い。754の §16.9・753の §16.9と同じ構図で、
  本稿では推論扱いとする。
- **内部751サーバー側の2引数分岐なし**：`differences(756, 751)` 等は NOT_SUPPORTED を返す
  （Stage B §14.2）。Stage Eのマトリクスで必要なら追加する。
- **756／757クライアント×751サーバーの Join Game／Respawn は直接テスト無し**（推論。上の対応表を参照）。
- **736サーバー＋774クライアントの Join Game 到達は未確認**：751・753と同じ構図で、
  対応するテスト（`loginTo(774, 736)` を使うもの）が無い。736ノート §14.9でも未確認とされている。
  本稿では推論扱いとする。
- **内部736サーバー側の2引数分岐なし**：`differences(756, 736)` 等は NOT_SUPPORTED を返す
  （Stage B §14.2）。Stage Eのマトリクスで必要なら追加する。
- **756／757クライアント×736サーバーの Join Game／Respawn は直接テスト無し**（推論。上の対応表を参照）。
- **736→751の recipe_book は非可逆**：crafting_book_data type=1は4冊分だが751の recipe_book は1冊分しか
  持てない。開いている本を優先順（crafting→smelting→blasting→smoking）で1冊選ぶ（736ノート §14.6）。
- **Unlock Recipes は736→759に届かない**：736→751の変換自体は正しいが、`to757ServerPacketFrom756` と
  `to759ServerPacketFrom757` に PLAY_UNLOCK_RECIPES の case が無く両段とも default で落とす既存ギャップ
  （756／757担当の範囲。736ノート §14.6）。
- **World Particles は736→759に届くが755形式のまま**：`to756ServerPacketFrom755` に case が無く素通しされ、
  756→757段の変換器が誤読して素通しするため、759クライアントには755形式・755の値のまま届く。
  実バニラ759クライアントは誤読する。既存ギャップ（755／756担当の範囲。736ノート §14.6）。
- **Update Light は736サーバー→751クライアントでは754⇔755の段で落とされる**（既存テストでカバー済み。
  736ノート §14.6）。

## 754のときに見つかった罠（後続の担当へ）

- **`handleServerPlay` はハンドラ結果を763形式とみなす**（757のStage Bで
  見つけた実バグ。754でも同じ）：
  `handleServerPlay` は、ハンドラの結果を763形式とみなして759〜762向けに
  変換し直す。そのため `handleServerPlayFrom754` から直接755形式のパケットを
  返すと、IDが763の別パケットと誤読されて壊れる。対策は、754→755へ変換したら
  `handleServerPlayFrom755` に委譲し、763形式で返して呼び出し側の変換に任せること。
  直接755形式で返す `handleServerPlay754To755` は作らないこと。新しいプロトコルを
  追加するときは、ハンドラが返す形式が呼び出し側の想定と一致しているか確認すること。
- **Respawn／Disconnect ガードの `&&`／`||` 優先順位バグ**（755 Stage B統合時に修正した本物のバグ。
  754でも同じ形を踏襲しないこと）：
  `handleServerPlayUp` と `handleServerPlay765From764` のガードが
  `if (name == PacketName.PLAY_RESPAWN && is757() || is756() || is755())` と書かれていた。
  `&&` は `||` より強いため、756／755クライアントでは Respawn 以外の全パケットが落とされ
  （765側）、764側では全パケットが Disconnect 扱いで切断されていた。3か所とも
  `&& (is757() || is756() || is755() || is754())` に修正し、コメントに罠を記録した。
  回帰テスト2件を `ProxySession754Test` に追加
  （`keepAlive765Against754IsNotDroppedByRespawnGuard`／
  `keepAlive764Against754IsNotClosedByDisconnectGuard`）。
  新しいガードを書くときは括弧を付けること。
- **Respawn の copyMetadata**（757の§13.4の罠。754では踏んでいない）：
  SpawnInfo754／755 は `dataToKeep` を末尾に置いていたが、実レイアウトは
  1.16.5が {dimensionType、dimension、seed、playerGameType、previousPlayerGameType、
  isDebug、isFlat、**keepAllPlayerData bool**}（minecraft-data protocol.jsonで確認）。
  dataToKeep は766以降にしか無い。754では protocol.json で末尾のフィールドを
  実測してから書いたためこの罠を踏んでいない。
  新しいバージョンの Respawn を写すときは、minecraft-data の protocol.json
  で末尾のフィールドを実測すること。
- **774＋754ログイン修正**（757の§13.7.3の754版。Stage B で見つけた実バグ）：
  774クライアントが754サーバーに繋がると、プレイに入れなかった。
  `isServer763Like()` は761／762／763だけで、Login Success で「両方設定
  フェーズに入る」分岐に入り状態が変わらず、クライアントの Login
  Acknowledged が754サーバーに誤送されていた（754サーバーは 0x03 を
  play chat と誤読する）。`ProxySession.java` の3箇所の条件に
  `isServer754()`／`is754()` を追加した。範囲は754だけに絞った。
  759／760サーバーにも同じ隙間があるが、本タスクの範囲外として記録する。
- **設定フェーズなし**：754にも755にも設定フェーズは無い。
  `ClientJoinFlow` は stage の三項演算子に `|| clientProtocol == V1_16_5` が
  無いと、754が設定フェーズの stage を誤って取る。新しいプロトコルを
  追加するときは、stage の分岐のすべての組み合わせに漏れが無いか確認すること。
- **署名なしチャット**：1.16.5の chat は署名なし（{message string}のみ）。
  759の System Chat の type は **varint**（overlay bool は760から）。
  live の chat_type "chat" の decoration は `chat.type.text`
  （`chat.type.announcement` ではない）。"system" に decoration は無く、
  chat は空。1.16.5の serverbound packet_chat は {message string} のみ。
  依頼文の記述はデータで再確認すること。
- **pack.mcmeta は平文形式**：1.16.5サーバーjarのjavapで確認。
  `SharedConstants` は protocol 754・world 2586・pack リソース6／データ6。
  `PackMetadataSectionSerializer.fromJson` は `description` と `pack_format`
  （int）のみ読む。jar内21,451クラス中 `min_format`／`max_format` を含む
  クラスは0件、`pack_format` を含むクラスは1件のみ。
  正しい形式は plain int形式
  `{"pack":{"pack_format":6,"description":"DetergentMC resource pack"}}`。
  777の min/max 形式は1.16.5には存在しない（755・756の罠と同じ）。
  1.16.5クライアントでのパック受け入れの実機確認は未実施。
- **反転表の first-wins の罠**：`RegistryRemapper.invert()` と
  `invert_java` は最初に見つかった preimage を採用する。754では補正が
  ブロック状態46件・アイテム5件・音35件あり、invert＋補正＝名前の真値が
  成り立つことを verify で確認する（755では補正は全種0件だったが、754では0件ではない）。
- **バイオーム表はワイヤ id 空間（176 件）で作ること**：`biomes-1.16.json` は79件で
  id が0〜173の非連番（1.17は81件・0〜175非連番）。順向き表・反転表ともワイヤ id 空間の
  176 件（id 0〜175。隙間の id は平原（1））で作る。位置リストで作ると誤写像になる
  （755の生成時に書き込み前検証で発覚し作り直した）。
  根拠：実バニラ1.16.5のライブキャプチャで dimensionCodec の
  `minecraft:worldgen/biome` が79件・id 0〜173の非連番であることを確認済み。
- **Window Click の mode は両方 varint**：minecraft-data の「Window Click の mode は
  1.16.5で i8」は誤り。javap -c で両バージョンとも `writeEnum`（varint の enum 序数）を確認。
  mode は両方向とも varint のまま写す（変換器のjavadocに記録済み）。
- **`serverProtocolFor("1.16.5")` は754を返す**：Stage A で `"1.16.5"`／`"1.16.4"` → `V1_16_5` を追加済み。
  推測で値を入れてはいけない、という罠は踏んでいない（実バニラjarの `version.json` で確認）。
- **`ClientAdapter` の三項演算子の置き換えミス**：先頭への追加にすること。
  置き換えると他のプロトコルの値（775の "84" など）が消える。
  三項演算子の編集は置き換えではなく先頭への追加にすること。
- **static 初期化子の 64KB 制限**：`RegistryRemapper` の `<clinit>` は
  754追加前で64,669バイト（残り866バイト）。754の表を外側の static
  初期化子に入れると `code too large` でコンパイルできない。
  754の展開済み表は `private static final class Maps754` にまとめた。
  さらに `RegistryTables754` も1ファイルでは64KBを超えるため、ブロック状態の
  下向き表だけ `RegistryTables754BlockState.java` に分けた（残り857バイトしかない）。
  753以降も同じホルダ方式・分割方式にすること。
- **1.16.5のjarはfat jar**：`META-INF/MANIFEST.MF` の `Main-Class` は
  `net.minecraft.server.Main` で、`net.minecraft.data.Main` がjar内に直接
  入っている。1.18のjarは bundler形式（`Main-Class: net.minecraft.bundler.Main`）
  だが、1.16.5はbundler形式ではない。そのため757の前例（サーバーを一度起動
  してlibrariesを取得し、内側jar＋libraries全jarをclasspathにする段取り）は
  1.16.5では不要で、jarをそのままclasspathにして直接実行できる。
- **ViaVersion mapping-1.16.json は 1.16.0 時点のもので 1.16.5 と食い違う**
  （実測）：items 975 vs registries 976（piglin_brute_spawn_egg 無し）、
  entities 107 vs 108（piglin_brute（id 61）無し）、sounds 985 vs 992、
  blockstates 17,104 vs reports 17,112（計8状態不足）。実バニラ registries／reports
  での補正が要る。生の mapping をそのまま使わないこと。
- **minecraft-data の pc/1.16 は 1.16.0 のもの**：1.16.5の protocol・blocks・items・
  biomes・entities は `pc/1.16.2`、enchantments は `pc/1.16.4`、sounds・particles は
  `pc/1.16` を指す（dataPaths.jsonで確認）。`pc/1.16/protocol.json` は1.16.0のもので
  パケットIDが0x0f以降1つずれている。`pc/1.16/blocks.json` は404（存在しない）。

## Java 8（内部754サーバーを動かすとき）

1.16.4／1.16.5は **Java 8** で動く。Java 16／Java 17／Java 21／Java 25 は内部サーバーには要らない。
`VanillaServerHost.javaBinaryFor` の Java 21 必須リストに1.16.5は入っていない
（1.20.5／1.20.6／1.21〜1.21.11だけが Java 21）。`minecraft-version` に
`1.16.5` を指定すると、内部754サーバーが Java 8 で起動する。
`javaBinaryFor` には Java 8 必須リストは無く、Java 8 は既定分岐
（現在の `java.home` をそのまま使う）で、DetergentMC 自身も Java 17 で動くため、
追加は不要。ただしドライバ自体はJDK 17で動かす必要があった（core／protocolに
sealedクラスがありJDK 8ではコンパイル・実行不可）。そのため実バニラ1.16.5の
起動確認ではドライバをJDK 17＋fake home（JDK 17ツリー全体をシンボリックリンクし、
`bin/java` だけを実JDK 8バイナリへのリンクに差し替えたもの）で起動し、
`javaBinaryFor("1.16.5")` が返すバイナリを
`/home/hatch/workspace/.int754d-p3/fake-jdk8-home/bin/java` にした
（ドライバ出力 `JAVA-BIN=` で確認済み）。本番コードの変更は無い。
**罠：755の段取り（ドライバをJDK 17＋`-Djava.home=$HOME/.tools/jdk8u512-b09`）は
JDK 8では動かない**（JDK 17はJVM起動時に `$java.home/conf/security` を読むが
JDK 8のhomeには無く即死する。ノート §15.3.5）。
Temurin 8（1.8.0_512-b09）は `~/.tools/jdk8u512-b09` に導入済み（Adoptium公式のみ）。
バニラ1.16.5サーバーjarのSHA-1は
`1b557e7b033b583cd9f66746b7a9ab1ec1673ced`
（piston-meta。37,962,360バイト。`run/vanilla/server-1.16.5.jar`）。
バニラ1.16.4サーバーjarのSHA-1は
`35139deedbd5182953cf1caa23835da59ca3d7cd`
（piston-meta。37,961,464バイト。`run/vanilla/server-1.16.4.jar`）。
バニラ1.16.3サーバーjarのSHA-1は
`f02f4473dbf152c23d7d484952121db0b36698cb`
（piston-meta。37,942,656バイト。`run/vanilla/server-1.16.3.jar`）。
バニラ1.16.2サーバーjarのSHA-1は
`c5f6fb23c3876461d46ec380421e42b289789530`
（piston-meta。37,942,337バイト。`run/vanilla/server-1.16.2.jar`）。

## リソースパック（pack754）

pack_format はリソース6・データ6。`detergentmc-pack-1.16.2.zip`（23つ目。751用）と
`detergentmc-pack-1.16.3.zip`（22つ目。753用）と
`detergentmc-pack-1.16.5.zip`（21つ目。754用）。
pack.mcmeta は平文 `{"pack":{"pack_format":6,"description":"DetergentMC resource pack"}}`
（上の「罠」を参照）。`resource-pack.url-754`／`sha1-754` で出し分ける。
フォールバックは `754→755→756→757→759→760→761→762→763`（pack755 は format 7。
754→755はpack_format 6→7の境界を越える）。
753のフォールバックは `753→754→755→756→757→759→760→761→762→763`
（753→754は pack_format 6→6 で境界を越えない）。
751のフォールバックは `751→753→754→755→756→757→759→760→761→762→763`
（751はツリー内最古のため古いフォールバックが存在せず、隣接する新しいパックに落とす。
751→753は pack_format 6→6 で境界を越えない）。
**754パックと755パックは内容が違う**（mcmetaのpack_formatが6と7で違うため）。
sha1は754が `89773dc5252afd72e4a662a5df8abc97d2554af0`、755が
`74d9d34d0dd377a5a9b0dfe0e39d968d027a356b`（756と同一）。
テストでは `assertNotEquals` で違うことを確認している。
**753パックと754パックも内容が違う**（mcmetaのdescriptionが違うため。753は
`"DetergentMC resource pack (1.16.3)"`、754は汎用の `"DetergentMC resource pack"`）。
sha1は753が `0adcecd8395d8e33fc7fa6e1d10a4835e9fa9c35`、754が
`89773dc5252afd72e4a662a5df8abc97d2554af0`。テストでは `assertNotEquals` で違うことを
確認している（753ノート §15.1.4。親がドライバで独立に再計算し、builder算出値と一致することを
確認済み。zipは1,179バイト）。
**751パックと753パックも内容が違う**（mcmetaのdescriptionが違うため。751は
`"DetergentMC resource pack (1.16.2)"`、753は `"DetergentMC resource pack (1.16.3)"`）。
sha1は751が `64cc34ed0946836657fbe026fe25a9ef2ad729a7`、753が
`0adcecd8395d8e33fc7fa6e1d10a4835e9fa9c35`。テストでは `assertNotEquals` で違うことを
確認している（751ノート §15.1.4。親がドライバで独立に再計算し、builder算出値と一致することを
確認済み。zipは1,179バイト）。

## Bedrock→754

- チャンクのワイヤ形式（実バニラ1.16.5の生キャプチャ＋javapで実測）：
  **16セクション**（groundUp＋bitMap付き）、**heightmap は名前付きNBT**、
  **バイオームは varint[1024]（groundUpのときだけ）**、**BitStorage は non-straddling**
  （BPE 5のData Array長342）。757の「24セクション・セクションごとの
  バイオームパレット・光内包」とは違う。バージョンごとに実測で決める方針通り。
- 754正規化：754→755は恒等＋755連鎖で765に正規化する。エンティティ・
  アイテム・エフェクト（1ベース→0ベース）・パーティクル（barrier 2→
  block_marker 3。754にlightは無い）・絵画（donkey_kong 25→29）も
  同じ正規化で扱う。
- 754固有の追加は2点だけ：Entity Destroy の配列読み（`parseRemoveEntity754`。
  754だけ `[count varint][entityIds]` 配列。755の `[entityId varint]` 単体として読むと壊れるため）と
  Respawn連鎖754→755→756→757→759。
- 今回見つけて直したバグはない（`airBlocks()` の二重変換バグ修正は756のときに
  既に入っていることを確認。Javaの空気＝状態0で埋める）。
- 副次的知見：設定パケットは Join Game 受信後に送ること。Login Success
  直後に送ると1.16.5サーバーが切断する（ログイン状態のままのため）。
  755と同じ実測。754では Login Success 直後に Client Information を送ると
  サーバーの状態遷移と競合し DecoderException（Index: 5, Size: 3）で切断される
  ことを実測済み（ノート §14.4）。

## Bedrock→753

- 753⇔754はパケットID差分0件・レジストリ差分0件のため、翻訳子はすべて恒等写像＋754連鎖
  （Stage B §14.3・§14.4）。753固有の追加は Entity Destroy の配列読み（`parseRemoveEntity753`。
  753も `[count varint][entityIds]` 配列。754と同じ）と Respawn連鎖753→754→755→756→757→759の2点だけ。
  Client Information は753も**6フィールド**（`mc-1.16.3-protocol.json` の `packet_settings` で確認。754と同じ）。
  チャンクは754のワイヤ形式（groundUp＋bitMap varint＋groundUpのときだけバイオーム varint[1024]・
  BitStorage non-straddling）で読む。実キャプチャ照合は `cap754-chunk-x-15-z-7.bin`（10,633バイト）を
  753形式バイトとして使用し PASS（16サブチャンク、ペイロード6,997バイト、fullyConsumed=true、
  Java 50（deep_frozen_ocean）が1024セル全て Bedrock 47 に変換。不一致0件）。
- テスト：`BedrockChunkTranslatorTest` の753件5件、`JavaBackendConnection753Test` 新規36件。
  `BedrockChunkTranslatorTest`＋`JavaBackendConnection753Test` は **67件中60通過・7失敗**
  （失敗は全てソケット系で、JVMループバックTCP遮断の環境制約。753件5件は全通過、
  接続753の非ソケット29件は全通過）。

## Bedrock→751

- 751⇔753はパケットID差分0件・レジストリ差分0件のため、翻訳子はすべて恒等写像＋753連鎖
  （Stage B §14.3・§14.4）。751固有の追加は Entity Destroy の配列読み（`parseRemoveEntity751`。
  751も `[count varint][entityIds]` 配列。753と同じ）と Respawn連鎖751→753→754→755→756→757→759の2点だけ。
  Client Information は751も**6フィールド**（`mc-1.16.2-protocol.json` の `packet_settings` で確認。753と同じ）。
  チャンクは753のワイヤ形式（groundUp＋bitMap varint＋groundUpのときだけバイオーム varint[1024]・
  BitStorage non-straddling）で読む。実キャプチャ照合は `cap754-chunk-x-15-z-7.bin`（10,633バイト）を
  751形式バイトとして使用し PASS（16サブチャンク、ペイロード6,997バイト、fullyConsumed=true、
  Java 50（deep_frozen_ocean）が1024セル全て Bedrock 47 に変換。不一致0件）。
- テスト：`BedrockChunkTranslatorTest` の751件5件、`JavaBackendConnection751Test` 新規36件。
  `BedrockChunkTranslatorTest`＋`JavaBackendConnection751Test` は **72件中65通過・7失敗**
  （失敗は全てソケット系で、JVMループバックTCP遮断の環境制約。751件5件は全通過、
  接続751の非ソケット29件は全通過）。

## Bedrock→736

- 736⇔751はパケットIDが62件変わる・レジストリも違うため、翻訳子は736固有の追加が多い
  （Stage B §14.4・§14.6、Stage D §15.2）。`BedrockChunkTranslator` は `normalizeBlockState` に
  736→751連鎖（`mapBlockState736To765`）、チャンク頭部の736専用分岐（groundUp＋ignoreOldData読捨て＋
  bitMap＋heightmaps＋**biomes i32×1024・count無し**）、16セクション読み・non-straddlingに736追加。
  `BedrockEntityTranslator` は entity／item／effect／particleの736正規化、`paintingId736To765`、
  `parseRemoveEntity736`、Spawn／Effect／slot／metadata／NBT読みに736追加。
  `JavaBackendConnection` は Join Game 0x25（`parseJoinGame736`→codecを751形式へ戻してフル79バイオーム再構築）、
  Respawn 0x3a（dimensionは文字列）、Player Info 0x33、Entity Destroy 0x37（配列）、
  Multi Block Change 0x0f（736形式）、Client Information 6フィールド、chat 0x0E／0x03。
  Client Information は736も**6フィールド**（751とバイト同一・11バイト）。
  チャンクは16セクション・non-straddling（BPE 5の Data Array長342）。
  テストで見つけた実バグ1件を修正：`skipParticle` に736の barrier（2）早期returnが無く、
  765の block_marker としてブロック状態 varint を誤読して終端0xFFを消費していた。
- アンカー（プローブ実測。向きは736→751）：ブロック 4731→6779、14890→18511、5145→7406、9227→12517、
  17103→20724。エンティティ 61→76（736の61は751の62。piglin_bruteは736に無い）、60→74、59→73、29→42、55→69。
  アイテム oak_boat 667→773、music_disc_cat **909**→1162（751の910ではない）、grass_path 372→463。
  パーティクル 1→1、2→3、3→2、72→0。絵画 25→29。
  注意：Stage A §12.4のアンカー（chain 4731→4729等）は751→736向きの値。736→751向きは
  4731→4735、14890→14898、5145→5149、9227→9231、17103→17111。736→751の非恒等件数は
  ブロック12,373・アイテム248・エンティティ46（12,381／249／47は751→736向き）。
- 実キャプチャ照合は **PASS**：`cap736-chunk-x--17-z--23.bin`（15,083バイト、-17/-23、groundUp、
  ignoreOldData、bitMap 0x1f、バイオーム全plains）→ 16サブチャンク、fullyConsumed、border 0、
  blockEntities 0、subChunkY 0〜15、バイオーム1024セル照合・不一致0件。
  Join Game `joingame-1.16.1.bin`（1,159バイト、entityId 138、overworld、maxPlayers 20）も通過。
  バイオーム一覧は174件（ocean@0、plains@1、desert@2、mountains@3、gapの100はplainsで埋まる）。
- テスト：`BedrockChunkTranslatorTest` の736件5件、`JavaBackendConnection736Test` 新規36件。
  `BedrockChunkTranslatorTest`＋`JavaBackendConnection736Test` は **79件中72通過・7失敗**
  （失敗は全てソケット系で、JVMループバックTCP遮断の環境制約。736件5件は全通過、
  接続736の非ソケット29件は全通過）。
  全bedrockスイート：557件中469通過・86失敗・2中断（失敗86件は全て同じソケット起因。
  753／751／754／755／756／757で各7件、736で7件、他バージョンと共通テストで残り。
  中断2件はUDP前提のassumption失敗で753時と同じ）。**736が原因の回帰は0件**。

## Bedrock→735

- 735⇔736はパケットID・レジストリとも差分0件のため、翻訳子は736のものを流用する形で
  735固有の追加は少ない（Stage B §14.3・§14.4、Stage D §15.2）。`BedrockChunkTranslator` は
  `normalizeBlockState` に735→736連鎖（`mapBlockState735To765`）、チャンク頭部の735専用分岐
  （groundUp＋ignoreOldData読捨て＋bitMap＋heightmaps＋**biomes i32×1024・count無し**）、
  16セクション読み・non-straddlingに735追加。
  `BedrockEntityTranslator` は entity／item／effect／particleの735正規化、`paintingId735To765`、
  `parseRemoveEntity735`、Spawn／Effect／slot／metadata／NBT読みに735追加。
  `JavaBackendConnection` は Join Game 0x25（`parseJoinGame735`→codecを736形式へ戻してフル79バイオーム再構築）、
  Respawn 0x3a（dimensionは文字列）、Player Info 0x33、Entity Destroy 0x37（配列）、
  Multi Block Change 0x0f（735形式）、Client Information 6フィールド、chat 0x0E／0x03。
  Client Information は735も**6フィールド**（736とバイト同一・11バイト）。
  チャンクは16セクション・non-straddling（BPE 5の Data Array長342）。
- アンカー（プローブ実測。735⇔736は恒等写像のため736向きの値と同じ）：ブロック 4731→6779、14890→18511、5145→7406、9227→12517、
  17103→20724。エンティティ 61→76（735の61は751の62。piglin_bruteは735に無い）、60→74、59→73、29→42、55→69。
  アイテム oak_boat 667→773、music_disc_cat **909**→1162（751の910ではない）、grass_path 372→463。
  パーティクル 1→1、2→3、3→2、72→0。絵画 25→29。
- 実キャプチャ照合は **PASS**：`cap735-chunk-x--3-z--15.bin`（13,011バイト、-3/-15、groundUp、
  ignoreOldData、bitMap 0x0f、バイオーム全24）→ 16サブチャンク、fullyConsumed、border 0、
  blockEntities 0、subChunkY 0〜15、バイオーム1024セル照合・不一致0件。
  Join Game `cap735-joingame-1.16.bin`（1,159バイト、entityId 352、overworld、maxPlayers 20）も通過。
  バイオーム一覧は174件（ocean@0、plains@1、desert@2、mountains@3、gapの100はplainsで埋まる）。
  ※ Stage B §14.4ではBPE 5セクションの生キャプチャ確認は未実施と記録したが、柱2の実キャプチャ照合で
  BPE 5のData Array長342を確認済み（735ノート §15.2）。
- テスト：`BedrockChunkTranslatorTest` の735件6件、`JavaBackendConnection735Test` 新規40件。
  `BedrockChunkTranslatorTest`＋`JavaBackendConnection735Test` を含む735対象148件は
  **134通過・14失敗**（失敗は全てソケット系（`expected: not <null>`）で、JVMループバックTCP遮断の環境制約。
  内訳は735系7件＋736系7件。`waitForServer` で偽サーバーがacceptしない）。
  全bedrockスイート：603件中508通過・93失敗・2中断（失敗93件は全て同じソケット起因。
  中断2件はUDP前提のassumption失敗で736時と同じ）。**735が原因の回帰は0件**。

## 統合テスト（Fake754Client＋4セル）

`detergentmc-core/src/test/java/io/detergentmc/core/vanilla/ProxyIntegrationTest.java`
に `Fake754Client`（同ファイル:964。Keep Alive 0x1f に 0x10 で応答する）と
4セルを追加済み（11,762行・106テスト→12,233行・110テスト、+471行・削除0行。
javac通過は下の「テスト」を参照）。
**プロキシ経由の4セルはこのVMでは実行できておらず、「統合テストが通った」とは
言えない。** 直接接続（プロキシ迂回）の4セルは実行済みで、結果は下の表の通り。
なお §11.6 の偽クライアント→実バニラ1.16.5の直接接続は Stage A で実施済みで、
Join Game 30,847バイト・チャンク-15,-7は10,633バイトを確認している。
Stage E の直接接続 (a) は Join Game 30,847バイトで Stage A の値と完全一致した
（entityId は今回110で、§11.6の270とはワールド違い）。

| セル | 内容 | 結果 |
| --- | --- | --- |
| (a) | 偽754クライアント→実バニラ1.16.5（内部754）、プロキシ経由。直接接続も実施 | プロキシ経由：**失敗（環境制約）**。プロキシは25571でlisten開始（PROXY-READY）。バニラ1.16.5は25570で Done 62.404s で起動。Pythonクライアントは正規パケット0件でEOF（`EOF while reading 107 bytes (got 3)`）。生ソケットで再確認したところ、受信バイトはサンドボックスのポリシー文 `muse: Other TCP connections is turned off for this assistant. To...`（193バイト）で、JVM→バニラのTCPがインターセプトされた。バニラログにjoin 0件。プロキシログにはポリシー文の断片による `PLAY_LOGIN_START` の仮配線WARNINGあり（757・755・756と同じもの）。直接接続：**通過**。ポート25564、Done 75.825s（Java 8）。Join Game 0x24＝**30,847バイト**（ペイロード。フレーム30,851バイト）、Spawn 受信、Chunk Data 0x20＝**441パケット・5,065,124バイト**（ペイロード。フレーム5,066,459バイト）、チャット到達（サーバーログ `<Steve> hello from 1.16.5` で確認）、Keep Alive応答1回、移動後も接続維持。圧縮しきい値256。クライアント側の経過時間14.293秒。結果JSONは `/tmp/capture-754/result-a-direct.json`（バックアップ `run/capture-754/`） |
| (b) | 偽754クライアント→実バニラ1.17（内部755）、プロキシ経由。直接接続も実施 | プロキシ経由：**失敗（環境制約）**。(a)と同一の失敗モード。プロキシ25573でlisten（PROXY-READY）、バニラ1.17は25572で Done 87.303s で起動。クライアントは即EOF・0件（`EOF while reading 1 bytes (got 0)`）。プロキシログにはポリシー文の断片をパケットIDとして読んだWARNING（id=0x75／0x3e／0x73＝"u>/s"）あり。バニラログにjoin 0件。直接接続：**想定通りの切断**。ポート25565、Done 82.812s（Java 16）。`multiplayer.disconnect.incompatible` with "1.17"。ログインで切断 |
| (c) | 偽754クライアント→実バニラ1.18（内部757）、プロキシ経由。直接接続も実施 | プロキシ経由：**未試行**（(a)(b)の失敗原因が共通の環境制約と確定したため。755 §16.5と同じ段取り）。直接接続：**想定通りの切断**。ポート25566、Done 164.385s（Java 17）。`multiplayer.disconnect.incompatible` with "1.18"。ログインで切断 |
| (d) | 偽755クライアント→実バニラ1.16.5（内部754）、プロキシ経由。直接接続も実施 | プロキシ経由：**未試行**（同上）。直接接続：**想定通りの切断**。ポート25567、Done 65.021s（Java 8）。`multiplayer.disconnect.incompatible` with "1.16.5"。ログインで切断 |

## 統合テスト（753。Fake753Client＋4セル）

`ProxyIntegrationTest.java` に `Fake753Client`（同ファイル:1065。Keep Alive 0x1f に 0x10 で応答する）と
`findRealVanilla1163Jar()`（同ファイル:238。jarが無ければスキップ）と4セルを追加済み
（12,233行・110テスト→12,705行・114テスト、+472行・削除0行。javac通過は下の「テスト」を参照）。
パケットIDは `PacketIds.java` の753列と packet-ids-753.json で確認済み（Join Game 0x24、Spawn 0x42、
Chunk Data 0x20、Keep Alive 0x1f→応答0x10、Chat 0x03、Client Information 0x05（6フィールド）、
Set Player Position 0x12、Login Success 0x02、プレイ切断 0x19。753は754と全272件でIDが同一のため、
Fake754Client と同じ値になる。Fake753Client のjavadocにも明記）。
**プロキシ経由の4セルはこのVMでは実行できておらず、「統合テストが通った」とは言えない。**
直接接続（プロキシ迂回）の4セルは実行済みで、結果は下の表の通り。
なお切断メッセージは754のときの `multiplayer.disconnect.incompatible` ではなく
`outdated_client`／`outdated_server` だった（実測値。原因は未確認）。

| セル | 内容 | 結果 |
| --- | --- | --- |
| (a) | 偽753クライアント→実バニラ1.16.3（内部753）、プロキシ経由。直接接続も実施 | プロキシ経由：**失敗（環境制約）**。プロキシは25571でlisten開始（PROXY-READY）。バニラ1.16.3は25570で Done 71.378s で起動。Pythonクライアントは正規パケット0件でEOF（`EOF while reading 107 bytes (got 3)`、受信ID 117／62／115）。生ソケットで再確認したところ、受信バイトはサンドボックスのポリシー文 `muse: Other TCP connections is turned off for this assistant. To...`（193バイト）で、JVM→バニラのTCPがインターセプトされた。バニラログにjoin 0件。プロキシログにはポリシー文の断片による `PLAY_LOGIN_START` の仮配線WARNINGあり（754・757・756と同じもの）。直接接続：**通過**。ポート25564、Done 71.787s（Java 8）。Join Game 0x24＝**30,847バイト**（ペイロード。消費量30,847で完全一致。Stage A §11.6と同一。entityId 197）、Spawn 0x42 受信、Chunk Data 0x20＝**462パケット・5,466,970バイト**（ペイロード）、チャット到達（クライアント側で `hello from 1.16.3` を受信。サーバーログ `<Steve> hello from 1.16.3` でも確認）、Keep Alive応答2回、移動後も接続維持。圧縮しきい値256。クライアント側の経過時間25.31秒。結果JSONは `/tmp/capture-753/result-a-direct.json`（バックアップ `run/capture-753/`） |
| (b) | 偽753クライアント→実バニラ1.17（内部755）、プロキシ経由。直接接続も実施 | プロキシ経由：**失敗（環境制約）**。(a)と同一の失敗モード。プロキシ25573でlisten（PROXY-READY）、バニラ1.17は25572で Done 102.653s で起動。クライアントは即EOF・0件（`EOF while reading 1 bytes (got 0)`）。プロキシログにはポリシー文の断片をパケットIDとして読んだWARNING（id=0x75／0x3e／0x73）あり。バニラログにjoin 0件。直接接続：**想定通りの切断**。ポート25565、Done 91.188s（Java 16）。`multiplayer.disconnect.outdated_client` with "1.17"。ログインで切断 |
| (c) | 偽753クライアント→実バニラ1.18（内部757）、プロキシ経由。直接接続も実施 | プロキシ経由：**未試行**（(a)(b)の失敗原因が共通の環境制約と確定したため。754 §16.5と同じ段取り）。直接接続：**想定通りの切断**。ポート25566、Done 173.497s（Java 17）。`multiplayer.disconnect.outdated_client` with "1.18"。ログインで切断 |
| (d) | 偽754クライアント→実バニラ1.16.3（内部753）、プロキシ経由。直接接続も実施 | プロキシ経由：**未試行**（同上）。直接接続：**想定通りの切断**。ポート25567、Done 68.168s（Java 8）。`multiplayer.disconnect.outdated_server` with "1.16.3"。ログインで切断 |

## 統合テスト（751。Fake751Client＋4セル）

`ProxyIntegrationTest.java` に `Fake751Client`（同ファイル:1080。Keep Alive 0x1f に 0x10 で応答する）と
`findRealVanilla1162Jar()`（同ファイル:253。jarが無ければスキップ）と4セルを追加済み
（12,705行・114テスト→13,175行・118テスト、+470行・削除0行。javac通過（親が `~/workspace/int751e.sh` で再実行して終了コード0・`=== ALL COMPILE OK ===` を確認）。
パケットIDは `PacketIds.java` の751列と packet-ids-751.json で確認済み（Join Game 0x24、Spawn 0x42、
Chunk Data 0x20、Keep Alive 0x1f→応答0x10、Chat 0x03、Client Information 0x05（6フィールド）、
Set Player Position 0x12、Login Success 0x02、プレイ切断 0x19。751は753と全272件でIDが同一のため、
Fake753Client と同じ値になる。Fake751Client のjavadocにも明記）。
**プロキシ経由の4セルのうち (a)(b) は環境制約で失敗、(c)(d) は未試行で、「統合テストが通った」とは言えない。**
直接接続（プロキシ迂回）の4セルはすべて実施済みで、結果は下の表の通り。
切断メッセージは (b)(c) が `multiplayer.disconnect.outdated_client`、(d) が `multiplayer.disconnect.outdated_server` だった（実測値。753と同じ。754のときは `multiplayer.disconnect.incompatible` だった。原因は未確認）。

| セル | 内容 | 結果 |
| --- | --- | --- |
| (a) | 偽751クライアント→実バニラ1.16.2（内部751）、プロキシ経由。直接接続も実施 | プロキシ経由：**失敗（環境制約）**。プロキシは25571でlisten開始（PROXY-READY）。バニラ1.16.2は25570で Done 69.303s で起動。Pythonクライアントは正規パケット0件でEOF（`EOF while reading 107 bytes (got 3)`、受信ID 117／62／115）。生ソケットで再確認したところ、受信バイトはサンドボックスのポリシー文 `muse: Other TCP connections is turned off for this assistant. To...`（193バイト）で、JVM→バニラのTCPがインターセプトされた。バニラログにjoin 0件。プロキシログにはポリシー文の断片による `PLAY_LOGIN_START` の仮配線WARNINGあり（753・754と同じもの）。直接接続：**通過**。ポート25564、Done 67.086s（Java 8）。Join Game 0x24＝**30,847バイト**（ペイロード。フレーム30,851バイト。Stage A §11.6と同一）、Spawn 0x42 受信、Chunk Data 0x20＝**462パケット・4,785,500バイト**（ペイロード。フレーム4,786,886バイト）、チャット到達（クライアント側で `hello from 1.16.2` を受信。サーバーログ `<Steve> hello from 1.16.2` でも確認）、Keep Alive応答1回、移動後も接続維持。圧縮しきい値256。クライアント側の経過時間17.292秒。結果JSONは `/tmp/capture-751/result-a-direct.json` |
| (b) | 偽751クライアント→実バニラ1.17（内部755）、プロキシ経由。直接接続も実施 | プロキシ経由：**失敗（環境制約）**。(a)と同一の失敗モード。プロキシ25573でlisten（PROXY-READY）、バニラ1.17は25572で Done 71.074s で起動。クライアントは即EOF・0件（`EOF while reading 1 bytes (got 0)`）。生ソケットの再確認は今回は0バイト返却だったが、プロキシログにポリシー文の断片をパケットIDとして読んだWARNING（id=0x75／0x3e／0x73）があり、同じインターセプトと確定。バニラログにjoin 0件。直接接続：**想定通りの切断**。ポート25565、Done 116.894s（Java 16）。`multiplayer.disconnect.outdated_client` with "1.17"。ログインで切断 |
| (c) | 偽751クライアント→実バニラ1.18（内部757）、プロキシ経由。直接接続も実施 | プロキシ経由：**未試行**（(a)(b)の失敗原因が共通の環境制約と確定したため。753 §16.5と同じ段取り）。直接接続：**想定通りの切断**。ポート25566、Done 207.698s（Java 17）。`multiplayer.disconnect.outdated_client` with "1.18"。ログインで切断 |
| (d) | 偽753クライアント→実バニラ1.16.2（内部751）、プロキシ経由。直接接続も実施 | プロキシ経由：**未試行**（同上）。直接接続：**想定通りの切断**。ポート25567、Done 86.316s（Java 8）。`multiplayer.disconnect.outdated_server` with "1.16.2"。ログインで切断 |

## 統合テスト（736。Fake736Client＋4セル）

`ProxyIntegrationTest.java` に `Fake736Client` と4セルを追加した
（751のときは `Fake751Client`＋4セルで 12,705行・114テスト→13,175行・118テスト、+470行・削除0行。
736は 13,175行・118テスト→13,646行・122テスト、+471行・削除0行。javac通過を確認）。
パケットIDは `PacketIds.java` の736列と packet-ids-736.json で確認済み（Join Game 0x25、Spawn 0x42、
Chunk Data 0x21、Keep Alive 0x20→応答0x10、Chat 0x03、Client Information 0x05（6フィールド）、
Set Player Position 0x12、Login Success 0x02、プレイ切断 0x1a。751とは62件違うため、
Fake751Client の値をそのまま流用してはいけない）。
**プロキシ経由の4セルはこのVMでは実行できておらず、「統合テストが通った」とは言えない**
（777・757・756・755・754・753・751のStage Eと同じ環境制約、JVMのTCP遮断）。
直接接続（プロキシ迂回）の4セルは実行済み。結果JSONは `run/capture-736/`（`result-{a,b,c,d}-direct.json`、
`result-{a,b}-proxy.json`）にある。

| セル | 内容 | 結果 |
| --- | --- | --- |
| (a) | 偽736クライアント→実バニラ1.16.1（内部736）、プロキシ経由。直接接続も実施 | プロキシ経由：**失敗（環境制約）**。プロキシ25580でlisten（PROXY-READY、internal=736）、バニラは Done 81.480s で起動。クライアントは正規パケット0件でEOF（`EOF while reading 107 bytes (got 3)`、受信ID 117／62／115＝ポリシー文の断片）。生ソケット再確認で受信193バイトはサンドボックスのポリシー文（`muse: Other TCP connections is turned off for this assistant... other_tcp from Deny to Ask`）。バニラログにjoinなし。直接接続：**通過**。Join Game 0x25＝1,159バイト（フレーム1,162。Stage Aの実測1,159と同一）、Spawn 0x42、Chunk Data 0x21＝441パケット・7,179,549バイト、チャット到達（サーバーログ `<Steve> hello from 1.16.1` でも確認）、Keep Alive応答1回、移動後も接続維持。圧縮しきい値256。バニラ Done 76.801s（Java 8） |
| (b) | 偽736クライアント→実バニラ1.17（内部755）、プロキシ経由。直接接続も実施 | プロキシ経由：**失敗（環境制約）**。(a)と同一の失敗モード。プロキシ25581でlisten、クライアント即EOF・0件。バニラ Done 86.722s、ログにjoinなし。直接接続：**想定通りの切断**。`multiplayer.disconnect.outdated_client` with "1.17"。ログインで切断。バニラ Done 102.092s（Java 16） |
| (c) | 偽736クライアント→実バニラ1.18（内部757）、プロキシ経由。直接接続も実施 | プロキシ経由：**未試行**（(a)(b)の失敗原因が共通の環境制約と確定したため。751 §16.5と同じ段取り）。直接接続：**想定通りの切断**。`multiplayer.disconnect.outdated_client` with "1.18"。ログインで切断。バニラ Done 189.396s（Java 17） |
| (d) | 偽751クライアント→実バニラ1.16.1（内部736）、プロキシ経由。直接接続も実施 | プロキシ経由：**未試行**（同上）。直接接続：**想定通りの切断**。`multiplayer.disconnect.outdated_server` with "1.16.1"。ログインで切断。バニラ Done 76.401s（Java 8） |

## 統合テスト（735。Fake735Client＋4セル）

`ProxyIntegrationTest.java` に `Fake735Client` と4セルを追加した
（736のときは `Fake736Client`＋4セルで 13,175行・118テスト→13,646行・122テスト、+471行・削除0行。
735は 13,646行・122テスト→14,117行・126テスト、+471行・+4テスト。javacは文書担当が軽い確認として再実行し、main 272ファイル・test 318ファイルとも終了コード0・エラー0件を確認。
`findRealVanilla116Jar()` は同ファイル:280。jarが無ければスキップ）。
パケットIDは `PacketIds.java` の735列と packet-ids-735.json で確認済み（Join Game 0x25、Spawn 0x42、
Chunk Data 0x21、Keep Alive 0x20→応答0x10、Chat 0x03、Client Information 0x05（6フィールド）、
Set Player Position 0x12、Login Success 0x02、プレイ切断 0x1a。735⇔736は全272件で同一のため、
Fake736Client の値をそのまま流用してよい。735と736で違うのはプロトコル番号（735／736）と
バージョン文字列（"1.16"／"1.16.1"）だけ）。
**プロキシ経由の4セルはこのVMでは実行できておらず、「統合テストが通った」とは言えない**
（777・757・756・755・754・753・751・736のStage Eと同じ環境制約、JVMのTCP遮断）。
直接接続（プロキシ迂回）の4セルも未実施で、結果は**結果待ち**（別担当が実施する）。

| セル | 内容 | 結果 |
| --- | --- | --- |
| (a) | 偽735クライアント→実バニラ1.16（内部735）、プロキシ経由。直接接続も実施 | プロキシ経由：**未実施**。直接接続：**未実施**。Stage Aでは偽735クライアントで実バニラ1.16に直接接続済み（Join Game 0x25＝1,159バイト、Chunk Data 0x21＝462パケット・7,611,671バイト、チャット到達、Done 73.824s。735ノート §11.6）。Stage Eの4セルとしての再実施は結果待ち |
| (b) | 偽735クライアント→実バニラ1.16.1（内部736）、プロキシ経由。直接接続も実施 | プロキシ経由：**未実施**。直接接続：**未実施**。想定は直接接続で**通過**（735⇔736は同一のため。Stage Aの1,159バイトと同一のはず）。結果待ち |
| (c) | 偽735クライアント→実バニラ1.18（内部757）、プロキシ経由。直接接続も実施 | プロキシ経由：**未実施**。直接接続：**未実施**。直接接続の想定は**想定通りの切断**（`multiplayer.disconnect.outdated_client` with "1.18"。736の(c)と同じ）。**注意：このセルのテスト（`fake735ClientJoinsRealVanilla118ThroughProxy`）は Join Game 0x25 の到達・Spawn・チャットを期待する書き方だが、方向の非対称（735クライアント×757サーバーは `handleServerJoinGame763` のガードで落とされる）と食い違う。プロキシ経由で参加できるはずが無いため、実行すれば Join Game のタイムアウトで失敗する見込み。Java担当への申し送り（この文書の担当は直していない。実行結果も未確認）** |
| (d) | 偽736クライアント→実バニラ1.16（内部735）、プロキシ経由。直接接続も実施 | プロキシ経由：**未実施**。直接接続：**未実施**。直接接続の想定は**想定通りの切断**（`multiplayer.disconnect.outdated_server` with "1.16"。736の(d)と同じ）。結果待ち |

## 手動テスト手順（実機クライアント）

`docs/PLAYTEST-1.16.md` を参照。

## テスト

- 単体テスト（`detergentmc-protocol/src/test/java/io/detergentmc/protocol/`）：
  新規754テスト **26件全通過**（Stage A）：`ProtocolVersion754Test`（3件）、
  `ClientAdapter754Test`（7件）、`RegistryRemapper754Test`（16件）。
  新規・書き換え754テスト **128件全通過**（Stage B）：`PlayTranslator754Test`（27件）、
  `ChunkTranslator754Test`（16件）、`ChunkTranslator754ValueTest`（30件）、
  `ProxySession754Test`（11件）、`ProxySession754bTest`（16件）、
  `ClientAdapter754Test`（13件。受け入れ側へ書き換え）、`ClientJoinFlow754Test`（5件）、
  `ClientProxy754Test`（4件）、`V1165AdapterTest`（6件）。
  回帰：`PlayTranslator755Test` 10件、`PlayTranslator756Test` 17件、`ChunkTranslator755Test` 13件、
  `ChunkTranslator756Test` 14件、`ProxySession755Test` 11件、`ProxySession756Test` 8件も全通過。
  `detergentmc-adapter-v1_16_5`：`V1165AdapterTest` 6件中6件通過。
  core：`VanillaServerHostTest` 11件中11件通過（1.16.5／1.16.4アサーション含む）。
- 全体回帰（detergentmc-protocol＋adapter-v1_16_5／v1_17、Stage B統合時）：
  **2,624件通過・30件失敗**。失敗は全て環境制約で、754が原因の
  本物の回帰は **0件**：
  - ResourcePackServerのローカルHTTP -1が20件（JVMのTCP遮断。HEADベースラインと同種）。
  - ソケットタイムアウト8件（ClientProxyTest 5件、ClientProxyAuthTest 3件。JVMのTCP遮断）。
  - SessionServerClientのbase URL未設定2件。
- `verify-754-tables.py` は **43件中43件通過**を維持
  （A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）。
- Stage D：`ResourcePackBuilder754Test`（3件）、`ResourcePackServer754Test`（2件）、
  `ResourcePackMode754Test`（6件）、コア配線4件全通過（新規含む）。
  新規754テスト15件の内訳は **13通過・2失敗**（失敗2件はともにHTTP GET -1のみで、
  それ以前のpack754生成・URL・sha1・packForProtocol・hostResourcePack・ClientProxy配線の
  アサーションはすべて通過）。
  全スイート（Stage D対象14クラス、208件）：186件通過・22件失敗。失敗22件はすべて環境制約
  （HTTP GET -1が15件、ソケットnullが7件）。
- Bedrock：`BedrockChunkTranslatorTest` に754件5件、`JavaBackendConnection754Test`
  新規36件。**754テスト：55通過／7失敗**（担当テスト62件中。失敗は全てソケット系7件で、
  JVMループバックTCP遮断の環境制約と切り分け済み。チャンク754の5件は全通過、
  接続754の非ソケット29件は全通過）。
  全bedrockスイート：432件中365件通過、65件失敗、2件中断。65件は全てソケット系テスト
  （759〜775・756・757・755・754のログイン／チャット／スポーン等、同じ
  「偽サーバーがacceptしない」モード）で、非ソケットの失敗は0件。回帰なし。
- 内部サーバー：`DatapackGeneratorTest` 15件＋`VanillaServerHostTest` 11件＝
  26件全通過（新規 `packFormatFor1165Is6` を含む）。
- 実バニラ1.16.5の起動確認：`VanillaServerHost` 経由で実バニラ1.16.5を起動し、
  `Done (62.596s)!` まで確認。データパックの `Error reading pack metadata` は0件。
- ライブキャプチャ（偽クライアント→実バニラ1.16.5、プロキシ迂回）：
  Join Game 30,847バイト。チャンク-15,-7は10,633バイト。
- Gradleテストは未実施（Gradleが無いため `./gradlew` は実行していない。
  上記の件数はJUnitスタブ＋リフレクション呼び出し／JUnit 5.10.1実jar＋
  自作ランナーによる実行）。

## テスト（753）

- 単体テスト：新規・書き換え753テスト **79件全通過**（Stage B）：`PlayTranslator753Test`（12件）、
  `ChunkTranslator753Test`（12件）、`ProxySession753Test`（11件）、`ProxySession753bTest`（16件）、
  `ClientAdapter753Test`（13件。受け入れ側へ書き換え）、`ClientJoinFlow753Test`（5件）、
  `ClientProxy753Test`（4件）、`V1163AdapterTest`（6件）。
  Stage Aの新規753テスト26件も全通過。
  全体回帰は2,726件通過・31件失敗（失敗は全て環境制約で、753が原因の
  本物の回帰は **0件**。旧期待値のずれは0件）。
- `verify-753-tables.py` は **43件中43件通過**を維持（本稿作成時に再実行して確認）。
- Stage D：`ResourcePackBuilder753Test`（3件）、`ResourcePackServer753Test`（2件）、
  `ResourcePackMode753Test`（6件）、`JavaBackendConnection753Test`（36件。非ソケット29件は全通過、
  ソケット7件は環境制約で失敗）、`BedrockChunkTranslatorTest` の753件5件、
  `DatapackGeneratorTest` 16件（新規 `packFormatFor1163Is6` を含む）、
  `VanillaServerHostTest` 11件（1.16.3アサーション含む）。
  Stage Dの対象13クラス・223件では200件通過・23件失敗（失敗は全て環境制約）。
- 実バニラ1.16.3の起動確認：`VanillaServerHost` 経由で実バニラ1.16.3を起動し、
  `Done (73.036s)!` まで確認。データパックの `Error reading pack metadata` は0件。
- ライブキャプチャ（偽クライアント→実バニラ1.16.3、プロキシ迂回。Stage A）：
  Join Game 30,847バイト。Chunk Data 0x20を482件受信。
- Gradleテストは未実施（Gradleが無いため。753も754と同じ）。

## テスト（751）

- 単体テスト：新規・書き換え751テスト **79件全通過**（Stage B）：`PlayTranslator751Test`（12件）、
  `ChunkTranslator751Test`（12件）、`ProxySession751Test`（11件）、`ProxySession751bTest`（16件）、
  `ClientAdapter751Test`（13件。受け入れ側へ書き換え）、`ClientJoinFlow751Test`（5件）、
  `ClientProxy751Test`（4件）、`V1162AdapterTest`（6件）。
  Stage Aの新規751テスト26件も全通過。
  全体回帰は2,834件通過・32件失敗（失敗は全て環境制約で、751が原因の
  本物の回帰は **0件**。旧期待値のずれは0件）。
- `verify-751-tables.py` は **43件中43件通過**を維持（本稿作成時に再実行して確認）。
- Stage D：`ResourcePackBuilder751Test`（3件）、`ResourcePackServer751Test`（2件）、
  `ResourcePackMode751Test`（6件）、`JavaBackendConnection751Test`（36件。非ソケット29件は全通過、
  ソケット7件は環境制約で失敗）、`BedrockChunkTranslatorTest` の751件5件、
  `DatapackGeneratorTest` 17件（新規 `packFormatFor1162Is6` を含む）、
  `VanillaServerHostTest` 11件（1.16.2アサーション含む）。
  Stage Dの対象13クラス・233件では209件通過・24件失敗（失敗は全て環境制約）。
- 実バニラ1.16.2の起動確認：`VanillaServerHost` 経由で実バニラ1.16.2を起動し、
  `Done (78.819s)!` まで確認。データパックの `Error reading pack metadata` は0件。
- ライブキャプチャ（偽クライアント→実バニラ1.16.2、プロキシ迂回。Stage A）：
  Join Game 30,847バイト。Chunk Data 0x20を462件受信。
- Gradleテストは未実施（Gradleが無いため。751も753と同じ）。

## テスト（736）

- 単体テスト：新規・書き換え736テスト **87件全通過**（Stage B）：`PlayTranslator736Test`（16件）、
  `ChunkTranslator736Test`（16件）、`ProxySession736Test`（11件）、`ProxySession736bTest`（16件）、
  `ClientAdapter736Test`（13件。受け入れ側へ書き換え）、`ClientJoinFlow736Test`（5件）、
  `ClientProxy736Test`（4件）、`V1161AdapterTest`（6件）。
  フォローアップ55件も全通過（`ChunkTranslator736FollowUpTest` 37件、`PlayTranslator736FollowUpTest` 13件、
  `ProxySession736FollowUpTest` 5件）。
  Stage Aの新規736テスト26件も全通過（`ProtocolVersion736Test` 3件、`ClientAdapter736Test` 7件、
  `RegistryRemapper736Test` 16件）。
  全体回帰は2,981件通過・33件失敗（失敗は全て環境制約で、736が原因の
  本物の回帰は **0件**。旧期待値のずれは0件）。
- `verify-736-tables.py` は **43件中43件通過**を維持（本稿作成時に再実行して確認）。
- Stage D：`ResourcePackBuilder736Test`（3件）、`ResourcePackServer736Test`（2件。うち1件は
  HTTP GET -1のみで失敗）、`ResourcePackMode736Test`（6件）、`JavaBackendConnection736Test`（36件。
  非ソケット29件は全通過、ソケット7件は環境制約で失敗）、`BedrockChunkTranslatorTest` の736件5件、
  `DatapackGeneratorTest` 18件（新規 `packFormatFor1161Is5` を含む）、
  `VanillaServerHostTest` 11件（1.16.1アサーション含む）。
  Stage Dの対象29件（`DatapackGeneratorTest`＋`VanillaServerHostTest`）は全通過。
  736パックは **24パック**（751の23・753の22とは違う。数え直しで確認）、
  736パックのsha1は `2e94e010af9bd8c088627d1c520efebfef21e06d`（1,179バイト）。
- 実バニラ1.16.1の起動確認：`VanillaServerHost` 経由で実バニラ1.16.1を起動し、
  `Done (72.926s)!` まで確認。データパックの `Error reading pack metadata` は0件。
- ライブキャプチャ（偽クライアント→実バニラ1.16.1、プロキシ迂回。Stage A）：
  Join Game **1,159バイト**（751の30,847バイトと大違い。codecが大きく違う）。
  Chunk Data 0x21を470件（1回目）／462件（2回目）受信。
- Gradleテストは未実施（Gradleが無いため。736も751と同じ）。

## テスト（735）

- 単体テスト：新規735テスト **26件全通過**（Stage A）：`ProtocolVersion735Test`（3件）、
  `ClientAdapter735Test`（7件）、`RegistryRemapper735Test`（16件）。
  新規・書き換え735テスト **90件全通過**（Stage B）：`PlayTranslator735Test`（16件）、
  `ChunkTranslator735Test`（19件）、`ProxySession735Test`（11件）、`ProxySession735bTest`（16件）、
  `ClientAdapter735Test`（13件。受け入れ側へ書き換え。Stage Aの7件→13件）、`ClientJoinFlow735Test`（5件）、
  `ClientProxy735Test`（4件）、`V116AdapterTest`（6件）。
  フォローアップは不要（735⇔736はレジストリ差分0件・フィールド同一のため、値付け替え変換器はそもそも不要。
  735ノート §14.6）。
  全体回帰はStage B時点で3,127件中3,093件通過・34件失敗（失敗は全て環境制約で、735が原因の
  本物の回帰は **0件**。旧期待値のずれは0件）。
- `verify-735-tables.py` は **43件中43件通過**を維持（本稿作成時に再実行して確認）。
- Stage D：`ResourcePackBuilder735Test`（3件）、`ResourcePackServer735Test`（2件。うち1件は
  HTTP GET -1のみで失敗）、`ResourcePackMode735Test`（6件）、`JavaBackendConnection735Test`（40件。
  非ソケット33件は全通過、ソケット7件は環境制約で失敗）、`BedrockChunkTranslatorTest` の735件6件、
  `DatapackGeneratorTest` 19件（新規 `packFormatFor116Is5` を含む）、
  `VanillaServerHostTest` 11件（1.16アサーション含む）。
  Stage Dの対象テストはパック系90件中71通過・19失敗（失敗は全てHTTP GET -1の環境制約）、
  データパック＋735ガード5クラス62/62全通過、Bedrock 735系148件中134通過・14失敗
  （失敗は全てソケットnullの環境制約）。
  735パックは **25パック**（736の24・751の23・753の22とは違う。数え直しで確認）、
  735パックのsha1は `8f99c96b9472b92b9788261c7cfdbf17f9a1127f`（4エントリ・mcmeta 75バイト）。
  全体回帰はStage D時点で3,138件中3,103件通過・35件失敗（失敗は全て環境制約で、735が原因の
  本物の回帰は **0件**）。
- Stage E：`ProxyIntegrationTest.java` に `Fake735Client`＋4セルを追加（13,646行・122テスト→
  14,117行・126テスト、+471行・+4テスト。javacは文書担当が軽い確認として再実行し、main 272ファイル・
  test 318ファイルとも終了コード0・エラー0件を確認）。
  プロキシ経由の実行は未実施で、「統合テストが通った」とは言えない。
  （c）セルのテストは Join Game 到達を期待する書き方だが方向の非対称と食い違う
  （上の「統合テスト（735）」の注意を参照。Java担当への申し送り）。
- 実バニラ1.16の起動確認：`VanillaServerHost` 経由で実バニラ1.16を起動し、
  `Done (60.197s)!` まで確認。データパックの `Error reading pack metadata` は0件。
  停止は既知の20秒問題が再現し、exit code 137（736は143。直していない。報告のみ）。
- ライブキャプチャ（偽クライアント→実バニラ1.16、プロキシ迂回。Stage A）：
  Join Game **1,159バイト**（1.16.1の1,159バイトと同一。751の30,847バイトと大違い）。
  Chunk Data 0x21を462パケット（7,611,671バイト）受信。同期位置 -95.5, 81.0, 198.5。
- Gradleテストは未実施（Gradleが無いため。735も736と同じ）。
