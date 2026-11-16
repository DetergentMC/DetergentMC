# 1.16.3／1.16.4／1.16.5クライアント → 1.16系サーバー（プロキシ）

DetergentMCは、公開ポートで待ち受けるNettyプロキシ（`ClientProxy`）と、
127.0.0.1の内部ポートで動くバニラ子プロセスの2層構成です。
1.16.3クライアント（プロトコル753）と1.16.4／1.16.5クライアント（プロトコル754）は、内部プロトコルが
753／754／755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775の22プロトコルのサーバーを対象にします。
**753と754はパケットID・レジストリ・Join Game codec がすべて同一で、753⇔754の翻訳子はすべて恒等写像です**
（下の「753↔754の差分」を参照）。753はこのツリーで最古のプロトコルです。
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
1.16.4と1.16.5はどちらもプロトコル754です（world_version だけが2584と2586で違う。
実バニラjarの `version.json` で確認。754ノート §7.3）。

754の仕組みは `docs/PROXY-1.17.md`（755）、`docs/PROXY-1.17.1.md`（756）、
`docs/PROXY-1.18.md`（757）、逆向きの考え方は `docs/PROXY-REVERSE.md` を参照してください。
なお754→755の差分は「755を隣接にした直接の差分」です
（777のときの776問題・757のときの758問題と同じ構図で、同じ方針 (b) を取った）。

全484通り（クライアント753／754／755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775 × サーバー753／754／755／756／757／759／760／761／762／763／764／765／766／767／768／769／770／771／772／773／774／775。22×22）の対応マトリクスは `docs/VERSIONS.md` の「1.16対応」節を参照してください。753の追加分は43セル（753行22セル＋753列22セル−重複1セル）です。
**本稿作成時点で `docs/VERSIONS.md` は Stage E の別担当が編集中のため、節名・行数は未確認**です。
マトリクスが未追加の場合は、対応表は本稿の表を正としてください。

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

## ポート構成

| ポート | 既定 | 役割 |
| --- | --- | --- |
| `server.port` | 25565 | プロキシの公開ポート。クライアントはここに接続する |
| `server.internal-port` | 25566 | バニラの待ち受けポート（`server-ip=127.0.0.1` に束縛） |
| `resource-pack.port` | 25567 | リソースパック配信用（loopbackのみ）。753用・754用・755用・756用・757用・759用・760用・761用・762用・763用・764用・765用・766用・767用・768用・769用・770用・771用・772用・773用・774用・775用の22つを出す（758用・776用は無い） |

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

## リソースパック（pack754）

pack_format はリソース6・データ6。`detergentmc-pack-1.16.3.zip`（22つ目。753用）と
`detergentmc-pack-1.16.5.zip`（21つ目。754用）。
pack.mcmeta は平文 `{"pack":{"pack_format":6,"description":"DetergentMC resource pack"}}`
（上の「罠」を参照）。`resource-pack.url-754`／`sha1-754` で出し分ける。
フォールバックは `754→755→756→757→759→760→761→762→763`（pack755 は format 7。
754はツリー内最古のため古いフォールバックが存在せず、隣接する新しいパックに落とす。
754→755はpack_format 6→7の境界を越える）。
753のフォールバックは `753→754→755→756→757→759→760→761→762→763`
（753→754は pack_format 6→6 で境界を越えない）。
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
