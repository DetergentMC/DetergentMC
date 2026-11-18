package io.detergentmc.bedrock;

/**
 * Bedrock クライアントのバージョン（プロトコル番号ごとの形式差分）。
 *
 * <p>プロトコル番号は wiki.vg「Bedrock Protocol version numbers」と
 * PrismarineJS minecraft-data の bedrock protocol.json で 1 つずつ確認した。
 * 各バージョンの StartGame・Resource Packs・Item の形式も minecraft-data
 * の protocol.json（1.16.210 / 1.16.220 / 1.17.0 / 1.17.10 / 1.17.30 / 1.17.40 / 1.18.0 / 1.18.11 / 1.18.30 / 1.19.1 / 1.19.10 / 1.19.20 / 1.19.21 / 1.19.30 / 1.19.40 / 1.19.50 / 1.19.62 / 1.19.70 / 1.19.80 / 1.20.0 / 1.20.10 / 1.20.30 / 1.20.40 / 1.20.61 /
 * 1.20.71 / 1.20.80 / 1.21.0 / 1.21.2 / 1.21.20 / 1.21.30 / 1.21.42 /
 * 1.21.50 / 1.21.60 / 1.21.70 / 1.21.80 / 1.21.90 / 1.21.93 / 1.21.100 /
 * 1.21.111 / 1.21.120 / 1.21.124 / 1.21.130 / 1.26.0 / 1.26.10 / 1.26.20 / 1.26.30 /
 * 1.26.40 / 1.26.45 / 1.26.51 / 1.26.60 の 51 ファイル）から読み取った。1.16.201 の protocol.json はプロトコル 422 のもので、1.16.200 /
 * 1.16.201 が共有する（protocolVersions.json では 422＝1.16.201 の 1 エントリのみ。Wiki「Bedrock Edition 1.16.200」／
 * 「Bedrock Edition 1.16.201」個別ページの infobox も Protocol version 422。minecraft-data に 1.16.200 の
 * protocol.json は無く（HTTP 404）、422 の代表ディレクトリは 1.16.201 のみ。422 の 1 つ下は 419（1.16.101／
 * 1.16.100）だが、minecraft-data に 1.16.101／1.16.100 の protocol.json は無く（HTTP 404）、
 * protocolVersions.json にも 419 のエントリは無いため、419 のパケット形式は CloudburstMC Protocol の
 * v419 コーデック（Bedrock_v419.java）から確認した。BE-NOTES.md §23・§25）。419 の 1 つ下は 408
 * （1.16.20／1.16.21／1.16.40／1.16.42／1.16.50／1.16.60／1.16.61 が共有。protocol.json は
 * 1.16.0 系のいずれにも無く、408 のパケット形式は CloudburstMC Protocol の v408 コーデック
 * （Bedrock_v408.java＝Bedrock_v407 の CODEC の流用）から確認した。BE-NOTES.md §26）。408 の 1 つ下は 407
 * （1.16.0／1.16.1／1.16.1.03／1.16.1.04／1.16.10 が共有。protocol.json は無く、形式は
 * CloudburstMC Protocol の v407 コーデックそのもので確認した。BE-NOTES.md §27）。407 の 1 つ下は 390
 * （1.14.60 のみが使うプロトコル。minecraft-data に 1.14 系の protocol.json は
 * 無く（1.14.0／1.14.20／1.14.30／1.14.60／1.13.0 のいずれも HTTP 404）、
 * protocolVersions.json にも 390 のエントリは無いため、390 のパケット形式は
 * CloudburstMC Protocol の v390 コーデックから確認した。BE-NOTES.md §28）。
 * 390 の 1 つ下は 389（1.14.0／1.14.1／1.14.20／1.14.21／1.14.30／1.14.41 が
 * 共有するプロトコル。protocol.json は無く、protocolVersions.json にも
 * 389 のエントリは無いため、389 のパケット形式は CloudburstMC Protocol の
 * v389 コーデックから確認した。BE-NOTES.md §29）。
 * 389 の 1 つ下は 388（§30）、388 の 1 つ下は 361（§31）。361 の 1 つ下は 354
 * （1.11.0／1.11.1／1.11.2／1.11.3／1.11.4 が共有するプロトコル。
 * protocol.json は無く（1.11.0／1.11.1／1.11.4 は HTTP 404）、
 * protocolVersions.json にも 354 のエントリは無いため、354 のパケット形式は
 * CloudburstMC Protocol の v354 コーデックから確認した。BE-NOTES.md §32）。
 * 354 の 1 つ下は 340（1.10.0／1.10.1 が共有するプロトコル。
 * protocol.json は無く（1.10.0／1.10.1／1.10.2 は HTTP 404）、
 * protocolVersions.json にも 340 のエントリは無いため、340 のパケット形式は
 * CloudburstMC Protocol の v340 コーデックから確認した。BE-NOTES.md §33）。
 * 340 の 1 つ下は 332（1.9.0 のみが使うプロトコル。protocol.json は無く、
 * protocolVersions.json にも 332 のエントリは無いため、332 のパケット形式は
 * CloudburstMC Protocol の v332 コーデックから確認した。BE-NOTES.md §34）。
 * 332 の 1 つ下は 313（1.8.0／1.8.1 が共有するプロトコル。protocol.json は
 * 無く（1.8.0／1.8.1 は HTTP 404）、protocolVersions.json にも 313 の
 * エントリは無いため、313 のパケット形式は CloudburstMC Protocol の
 * v313 コーデックから確認した。BE-NOTES.md §35）。313 の 1 つ下の
 * 312 はベータ 1.8.0.14 の番号（310／311 もベータ番号）で、1.8 世代の
 * リリースプロトコルは 313 が最後（BE-NOTES.md §35）。313 の下は
 * 291（1.7.0／1.7.1 が共有するプロトコル。protocol.json は無く
 * （1.7.0／1.7.1 は HTTP 404）、protocolVersions.json にも 291 の
 * エントリは無いため、291 のパケット形式は CloudburstMC Protocol の
 * v291 コーデック（継承チェーンの最古）から確認した。BE-NOTES.md
 * §36）。当初は BE 1.8 世代を終点としていたが、2026-11-18 に
 * ユーザーが「一番古いところまで」と指示したため 291 へ延長した。
 * 1.16.210 の protocol.json はプロトコル 428 のもので、1.16.210
 * が使う（beta 1.16.210.58〜.61 も 428。429＝beta 1.16.220.50、430＝beta 1.16.220.51。423〜427 は beta
 * 1.16.210 の番号で 426 は Wiki 表にエントリ無し。BE-NOTES.md §21）。1.16.220 の protocol.json はプロトコル 431 のもので、1.16.220 /
 * 1.16.221 が共有する。1.17.0 の protocol.json はプロトコル 440 のもので、1.17.0 /
 * 1.17.1 / 1.17.2 が共有する。1.17.10 の protocol.json はプロトコル 448 のもので、1.17.10 /
 * 1.17.11 が共有する。1.17.30 の protocol.json はプロトコル 465 のもので、1.17.30 /
 * 1.17.32 / 1.17.33 / 1.17.34 が共有する。1.17.40 の protocol.json はプロトコル 471 のもので、1.17.40 /
 * 1.17.41 が共有する。1.18.0 の protocol.json はプロトコル 475 のもので、1.18.0 /
 * 1.18.1 / 1.18.2 が共有する。1.18.11 の protocol.json はプロトコル 486 のもので、1.18.10 /
 * 1.18.11 / 1.18.12 が共有する。1.18.30 の protocol.json はプロトコル 503 のもので、1.18.30 /
 * 1.18.31 / 1.18.32 / 1.18.33 が共有する。1.19.1 の protocol.json はプロトコル 527 のもので、1.19.0 /
 * 1.19.1 / 1.19.2 が共有する。1.19.10 の protocol.json はプロトコル 534 のもので、1.19.10 /
 * 1.19.11 が共有する。1.19.20 の protocol.json はプロトコル 544 のもので、545 のものとバイト同一
 * （1.19.21 / 1.19.22 が共有する 545 と同じ形式）。1.19.21 の protocol.json はプロトコル 545 のもので、1.19.21 /
 * 1.19.22 が共有する。1.19.30 の protocol.json はプロトコル 554 のもので、1.19.30 /
 * 1.19.31 が共有する。1.19.40 の protocol.json はプロトコル 557 のもので、1.19.40 /
 * 1.19.41 が共有する。1.19.50 の protocol.json はプロトコル 560 のもので、1.19.50 /
 * 1.19.51 が共有する。1.19.62 の protocol.json はプロトコル 567 のもので、1.19.60 /
 * 1.19.62 が共有する。1.19.80 の protocol.json はプロトコル 582 のもので、1.19.80 /
 * 1.19.81 / 1.19.83 が共有する。1.26.51 の protocol.json はプロトコル 2193 のもので、1.26.50 /
 * 1.26.51 / 1.26.52 が共有する。形式が変わる境界はすべて実ファイルで確認済みである。
 *
 * <p>26.60（プロトコル 2225）は NetherNet 世代のため RakNet では繋がらない。
 * {@link #NETHERNET_PROTOCOL} を参照。26.60 / 26.62 / 26.63 / 26.65 は
 * すべてプロトコル 2225（Minecraft Wiki「Bedrock Edition 26.65」等で確認）。
 * 2225 のパケット形式自体はこの enum で対応するが、実クライアントは
 * NetherNet（WebRTC）で接続するため、RakNet 側のセッションでは引き続き
 * 専用の切断メッセージを返す（BE-NOTES.md §24）。
 */
public enum BedrockVersion {
    // 291 は 1.7.0 / 1.7.1 で共有するプロトコル。displayName は
    // 共有する最新バージョンの 1.7.1。共有の確認は Wiki 個別ページの
    // infobox で行った：Bedrock Edition 1.7.0 は Protocol version
    // 291（2018-10-15 iOS／2018-10-16 その他）、Bedrock Edition
    // 1.7.1 も Protocol version 291（2018-11-07、Windows 10／
    // Xbox One のみ。本文に「compatible with 1.7.0 servers」の
    // 記述あり）。Wiki の Protocol version 一覧表では v1.7.1 に
    // 291 が付き v1.7.0 は空欄だが、infobox が正（313 のときと
    // 同じ食い違い方）。1.7.2／1.7.3／1.7.4 の個別ページは
    // 存在しない（HTTP 404、Wiki API でも missing）。
    // protocol.json は無く（minecraft-data の data/bedrock/1.7.0／
    // 1.7.1 はどちらも HTTP 404 で、ディレクトリ自体が無い）、
    // protocolVersions.json（58 件）にも 291／1.7 系のエントリは
    // 無い。そのため 291 のパケット形式は CloudburstMC Protocol
    // （ブランチ 3.0、コミット 7163a6ae…）の v291 コーデックから
    // 確認した。v291 は継承チェーンの最古（親を持たないルート
    // コーデック）で、VERSIONS.md でも v291＝1.7.0。
    // 登録パケットは 116 件（最大 ID 117、欠番は 116 のみ。
    // 最後は 117 ScriptCustomEvent）。PlayerAuthInput（144）は
    // 291 には存在しない（313 にも無く、388 で新規登録）。
    // LevelSoundEvent（123）も 291 には無い（313 で新規）。
    // 313 との形式の差分は 4 点（いずれも Cloudburst の
    // 実効シリアライザの実測）：
    // (1) StartGame の LevelSettings は _v291 の純粋な 31 フィールド
    // で、313 が末尾に追加した fromWorldTemplate／
    // fromLockedWorldTemplate の 2 bool が無いため、313 より
    // 2 バイト短い（startGameUsesV313LevelSettings() は 313 のみ）。
    // パレットは 313 と同じ legacy 配列形式（VarUnsignedInt 件数＋
    // 各エントリ name 文字列／meta ShortLE、ID 無し）が 291 にも
    // 存在する（hasStartGameLegacyPaletteArray() <= 361）。
    // (2) ResourcePackStack は _v291 形式（末尾の legacy experiment
    // bool が無い）で、313 より 1 バイト短い
    // （resourcePackStackUsesV291Format() <= 291）。gameVersion は
    // 291 にも 313 にも無い（resourcePackStackHasGameVersion()
    // >= 388）。ResourcePacksInfo は _v291 が 313 でも実効で
    // 同一形式（scripting 無し）。
    // (3) AddEntity の第 3 フィールドは identifier 文字列ではなく
    // 旧来の数値タイプ ID（VarUnsignedInt。AddEntitySerializer_v291。
    // addEntityUsesLegacyTypeId() <= 291）。数値の対応表は
    // BedrockLegacyEntityTypeIds（出典は PocketMine-MP 3.4.0 の
    // EntityIds.php。対応付けの無い identifier は 291 に送らない）。
    // (4) UpdateTrade は 313 で tradeTier が追加されるが、この実装は
    // UpdateTrade を送受信しないため記録のみ。
    // (5) NetworkChunkPublisherUpdate（121）は 313 で新規登録された
    // パケットで 291 には存在しないため、セッションは 291 に送らない
    // （hasNetworkChunkPublisherUpdate() は 313 以降）。
    // InventoryTransaction はシリアライザ自体が _v291 で 313 と同一
    // （transactionHasLegacyRequestId() >= 407 のため 291 は 407〜
    // と同じく requestId 無し）。ヘルパーの readSource は v291 では
    // Base のままで、UNTRACKED_INTERACTION_UI（100）の追加 varint を
    // 読まない（v313 ヘルパーは読む）。この実装の V0 読み飛ばしは
    // Base 準拠（100 では何も読まない）ため 291 とは一致する
    // （313 以降との食い違いは §36 に記録のみ）。
    // その他の核パケットは 313 と同一形式：LevelChunk は
    // FullChunkDataSerializer_v291、AddPlayer は _v291、
    // PlayerList のエントリは直書き 5 要素（playerListUsesLegacyEntry()
    // <= 361）、AddPainting は BlockPosition 形式
    // （addPaintingUsesBlockCoordinates() <= 354）、SetEntityData は
    // _v291、MoveEntityDelta は _v291（セッションは 313 以下と
    // 同じく絶対座標の MoveEntity で代替。スケール未確認）。
    // TypeMap はチェーン伝搬の実測で ENTITY_FLAGS 61 件（0〜60）、
    // ENTITY_DATA は index 0〜87（88 件。39 は INTERACT_TEXT で
    // 313 と同じ）、PARTICLE_TYPES 45 件（0〜44）、SOUND_EVENTS
    // 235 件（最大 239）、ENTITY_EVENTS 54 件（最大 72）、
    // COMMAND_PARAMS 13 件（最大 41）、LEVEL_EVENTS のスカラー
    // 72 件（最大 3510）、GAME_RULE_TYPES 3 件。いずれも定義の差で、
    // この実装のバイト列は変わらない（ただしこの実装が書く
    // ENTITY_FLAGS は最大 index 63、ENTITY_DATA は index 89／92 を
    // 書く場合があり、291 の定義範囲（0〜60／0〜87）の外になる。
    // ワイヤ形式自体は不変。§36 に記録）。
    // Skin は 291 でもヘルパー形式自体が存在せず（チェーン
    // v291→Base に readSkin／writeSkin が無い）、PlayerList
    // エントリの直書き 5 要素が唯一の形式。writeEmptySkin の形式は
    // 判断待ちのため 291 でも従来通り（記録のみ）。
    // ブロックパレットは 313 と同じ 1.11.0 世代の実測パレット
    // （3,183 件）を代用する（291 自体の実測ではない。パレットの
    // 並びとチャンク翻訳側との整合は未確認＝判断待ちのまま）。
    // アイテム表は 313〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、291 については代用である：PMMP BedrockData に
    // bedrock-1.7.0 タグ自体が存在せず（git/ref/tags は 404。
    // 純粋な bedrock-* 形式の最古タグは bedrock-1.6.1 で、その中身も
    // creativeitems.json と recipes.json のみでランタイム ID の
    // 実データは無い）、1.7 系のランタイム ID の実データは
    // 存在しない。表の中身は bedrock-1.16.100 タグの実測で、
    // 291 自体の実測ではない。291 のランタイム ID 割り当て
    // そのものは未確認。exact=624／shared=236／legacy=33／
    // fallback=419（いずれも 1_16_210 表自体の内訳）。
    // BE-NOTES.md §36。
    // 291 の 1 つ下は 290 で、開発版 1.7.0.3 の番号。リリースでは
    // 282（1.6.2。一覧表。出典は未確認）が 1 つ下で、その下も
    // 274（1.5.3）、261（1.4.4）、223（1.2.13.60）等が一覧表に
    // あるが、今回確認した範囲では 291 より下で出典のある最古の
    // リリースプロトコルは 70（0.14.3。minecraft-data の
    // data/bedrock/0.14/protocol.json が実在）。次の一手は
    // ユーザーの判断待ち（BE-NOTES.md §36）。
    V1_7_1(291, "1.7.1"),
    // 313 は 1.8.0 / 1.8.1 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.8.1（340 の V1_10_1 と同じ流儀）。
    // 共有の確認は Wiki 個別ページの infobox で行った：Bedrock
    // Edition 1.8.0 は Protocol version 313（2018-12-11 リリース）、
    // Bedrock Edition 1.8.1 も Protocol version 313（313 回に
    // 自分の目で確認）。Wiki の Protocol version 一覧表（Bedrock の
    // 表）では v1.8.1 の行に 313 が付き、v1.8.0 の行は空欄だが、
    // 個別ページの infobox が正。minecraft-data に 1.8 系の
    // protocol.json は無い（1.8.0／1.8.1 は 313 回に curl で
    // HTTP 404 を確認。対照の 1.16.201 は 200）。
    // protocolVersions.json にも 313 のエントリは無い。
    // パケット形式の根拠は CloudburstMC Protocol の v313 コーデック
    // （Bedrock_v313.java は Bedrock_v291 の CODEC を土台に
    // protocolVersion(313)／minecraftVersion("1.8.0")、helper は
    // 新規 BedrockCodecHelper_v313。VERSIONS.md で
    // Bedrock_v313＝1.8.0。BE-NOTES.md §35）。
    // 332 との差分（この実装が読み書きする範囲）：ワイヤ形式の
    // 差は 2 点だけ。StartGame の LevelSettings が _v332 の
    // 30 フィールドではなく、_v291 の writeLevelSettings
    // （31 フィールド）に fromWorldTemplate／
    // fromLockedWorldTemplate の 2 bool を追加した
    // StartGameSerializer_v313（33 フィールド）で、放送モードは
    // bool 3 つ（xbl／platform／xblIntent。値が NO_MULTI_PLAY の
    // とき false）と zigzag32 の組になるため、313 の StartGame は
    // 332 より 3 バイト長い（startGameUsesV291LevelSettings()）。
    // ResourcePacksInfo は _v291 が実効で scriptingEnabled bool と
    // エントリの scripting bool が無く、空の一覧では 332 より
    // 1 バイト短い（resourcePacksInfoHasScripting() >= 332）。
    // トップレベル（_v291 serialize）は 313 も 332 も同一。
    // その他の核パケットは同一形式：LevelChunk は
    // FullChunkDataSerializer_v291（subChunkCount 無し）、
    // AddPainting は _v291 で _v332 と実装が同一（差は package 名と
    // クラス名のみ）
    // （BlockPosition。addPaintingUsesBlockCoordinates() の
    // <= 354 に 313 も含まれる）、PlayerList は _v291 の
    // 旧直書きエントリ、AddPlayer は _v291（buildPlatform 無し）、
    // AddEntity は _v313（332 も _v313）、ResourcePackStack は
    // _v313（gameVersion 無し）、Transaction は _v291 基本形式、
    // Text は _v291（CHAT の書き方は _v332 と同一）。
    // PlayerAuthInput（144）は 313 にもパケット自体が存在しない
    // （388 で新規登録）。LevelSoundEvent（123）は 332 で
    // 新規登録のため 313 には無い。
    // 313 の型マップ実効値：ENTITY_FLAGS 71 件（0〜70。332 と同じ）、
    // ENTITY_DATA index 94 件（0〜93。332 との差は 94〜98 の有無と
    // index 39＝INTERACT_TEXT の有無だけだが、この実装はどちらも
    // 使わない）、PARTICLE_TYPES 55 件（0〜54。332 と同じ）、
    // SOUND_EVENTS 251 件（最大 255。332 と同じ）、ENTITY_EVENTS
    // 55 件（最大 73。332 と同じ）、COMMAND_PARAMS 13 件
    // （最大 43。FILE_PATH は 332 で新規のため 313 には無い）が
    // AvailableCommands は符号化していないため無関係。
    // LEVEL_EVENTS はスカラー 73 件（332 は 75 件。BLOCK＋12／＋13
    // は 332 で新規）。パケット母集団は Cloudburst 登録ベース
    // 121 件・最大 ID 122・欠番 [116]、従来方式（protocol.json
    // 由来）では 122 件・最大 128・欠番 [37, 116, 123, 124, 125, 126]
    // （332 の集合から v332 の新規登録 {123} を除いた逆算で、
    // 1.8.x 自体の protocol.json を実測したものではない）。
    // ヘルパー（BedrockCodecHelper_v313）は readSource のみを
    // 上書きし（UNTRACKED_INTERACTION_UI の扱い）、332 の継承
    // チェーンにも含まれるため 313 と 332 で共通。readItem／
    // writeItem は v291 ヘルパーのもの（v332 ヘルパーは同じ形に
    // 上書き。nbtSize == -1 のネットワーク NBT 処理だけが
    // v332 側にある定義のみの差で、この実装のアイテム読み書きは
    // 自前コードのため無関係）。
    // ブロックパレットは形式こそ 332 と同じ name／meta 配列だが、
    // PMMP BedrockData bedrock-1.8.0 タグには
    // required_block_states.json／block_id_map.json 自体が無く
    // （ツリー 4 ファイル：LICENSE／README.md／
    // creativeitems.json／recipes.json のみ）、1.8 系のパレット
    // 実データは存在しない。そのため中身は 1.11.0 世代の実測
    // パレット（bedrock_block_palette_1_11_0.bin、3,183 件、
    // BedrockLegacyBlockPalette.load1_11_0()）を代用する。
    // 313 自体の実測ではない。ランタイム ID の割り当てと
    // チャンク翻訳側との整合は未確認。
    // Skin は 313 にもヘルパー形式自体が存在せず（v313 ヘルパーの
    // 継承チェーン v313→v291→Base に readSkin／writeSkin の
    // 上書きが無い）、PlayerList／PlayerSkin は 332 と同じ
    // 旧直書き 5 要素。Skin フィールド数の並びは
    // 313＝直書き 5 要素／332＝直書き 5 要素／340＝直書き 5 要素／
    // 354＝直書き 5 要素／361＝直書き 5 要素／388＝12／389＝12／
    // 390＝16。writeEmptySkin の形式は判断待ちのため 313 でも
    // 従来通り（記録のみ）。
    // MoveEntityDelta は _v291 形式だが、332 以下と同じく
    // セッションが絶対座標の MoveEntity で代替する
    // （スケール未確認のため）。
    // アイテム表は 340〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、313 については代用である：PMMP BedrockData の
    // bedrock-1.8.0 タグは item_id_map.json すら無く
    // （creativeitems.json 1,169 件・SHA-1 1405c0a8…は旧来の
    // 数値 ID でランタイム ID ではない）、required_item_list.json
    // は無い。1.8 系のランタイム ID の実データは存在しない。
    // 表の中身は bedrock-1.16.100 タグの実測で、313 自体の
    // 実測ではない。313 のランタイム ID 割り当てそのものは
    // 未確認。exact=624／shared=236／legacy=33／fallback=419
    // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §35）。
    // 313 の 1 つ下は 312 で、ベータ 1.8.0.14 の番号（Wiki
    // 「Bedrock Edition beta 1.8.0.14」個別ページ：Type＝Beta、
    // Protocol version 312。311＝beta 1.8.0.10、310＝beta 1.8.0.8。
    // 292〜309 は Wiki 一覧表に行が無い）。1.8 世代のリリース
    // プロトコルは 313 が最後（BE-NOTES.md §35）。当初はここを
    // 終点としていたが、2026-11-18 にユーザーが「一番古いところ
    // まで」と指示したため、その下の 291 へ延長した
    // （BE-NOTES.md §36）。
    V1_8_1(313, "1.8.1"),
    // 332 は 1.9.0 のみが使うプロトコル（共有は無い）。displayName は
    // 1.9.0。共有が無いことの確認は Wiki 個別ページの infobox で
    // 行った：Bedrock Edition 1.9.0 は Protocol version 332
    // （2019-02-05 リリース。332 回に自分の目で確認）。
    // 1.9.1／1.9.2／1.9.3／1.9.4 は個別ページ自体が存在しない
    // （Wiki API で missing、332 を共有する他の 1.9.x リリースは
    // 確認できなかった）。1.9.0 のベータ（v1.9.0.0＝330、
    // v1.9.0.2＝331）は 332 ではない。minecraft-data に 1.9 系の
    // protocol.json は無い（1.9.0 は 332 回に curl で HTTP 404 を
    // 確認。対照の 1.16.201 は 200）。protocolVersions.json にも
    // 332 のエントリは無い。
    // パケット形式の根拠は CloudburstMC Protocol の v332 コーデック
    // （Bedrock_v332.java は Bedrock_v313 の CODEC を土台に
    // protocolVersion(332)／minecraftVersion("1.9.0")、helper は
    // 新規 BedrockCodecHelper_v332。VERSIONS.md で
    // Bedrock_v332＝1.9.0。BE-NOTES.md §34）。
    // 340 との差分（この実装が読み書きする範囲）：ワイヤ形式に
    // 差は無い。StartGame は StartGameSerializer_v332＋トップ
    // レベル _v291 で 340 と同一（LevelSettings 30 フィールド、
    // パレットは name／meta のみの配列形式、itemstates 無し、
    // onlySpawningV1Villagers 無し。_v332 は 332 世代自身の
    // シリアライザ）。LevelChunk は FullChunkDataSerializer_v291
    // で同一（subChunkCount 無し）。AddPainting は _v332 で位置が
    // BlockPosition のため同一（addPaintingUsesBlockCoordinates()
    // の <= 354 に 332 も含まれる）。PlayerList は _v291 の
    // 旧直書きエントリで同一、AddPlayer は _v291
    // （buildPlatform 無し）、AddEntity は _v313、
    // ResourcePacksInfo は _v332、ResourcePackStack は _v313
    // （gameVersion 無し）、Transaction は _v291 基本形式、
    // いずれも 340 と同一。PlayerAuthInput（144）は 332 にも
    // パケット自体が存在しない（388 で新規登録）。
    // 定義のみの差が 4 点ある：ENTITY_FLAGS は 71 件（0〜70）で
    // 340 との差は 71〜79 の有無だけ（この実装が書くフラグは
    // key 0 で最大 63、FLAGS_2 で最大 48 のためワイヤに現れない）。
    // ENTITY_DATA は index 99 件（0〜98）で、index 39 は
    // INTERACT_TEXT（340 は HAS_NPC に replace）と 99〜101 の
    // 有無が違う（この実装は 39 も 99〜101 も使わない）。
    // COMMAND_PARAMS は 14 件（最大 45。FILE_PATH は 15 で、
    // 340 の shift(15, -1) で 14 になる）だが AvailableCommands は
    // 符号化していないため無関係。SOUND_EVENTS は 251 件
    // （最大 255）だが LevelSoundEvent は送っていないため
    // 無関係。LEVEL_EVENTS は 340 と同じマップ（スカラー 75 件）。
    // 332 の型マップ実効値：ENTITY_FLAGS 71 件（0〜70）、
    // ENTITY_DATA index 99 件（0〜98）、PARTICLE_TYPES 55 件
    // （0〜54。340 と同じ）、LEVEL_EVENTS スカラー 75 件
    // （340 と同じマップ）、SOUND_EVENTS 251 件（最大 255。
    // 340 は 253 件）、ENTITY_EVENTS 55 件（340 と同じ）、
    // COMMAND_PARAMS 14 件（最大 45）。パケット母集団は
    // Cloudburst 登録ベース 122 件・最大 ID 123・欠番 [116]、
    // 従来方式（protocol.json 由来）では 123 件・最大 128・欠番
    // [37, 116, 124, 125, 126]（340 の集合から v340 の新規登録
    // {124, 125} を除いた逆算で、1.9.0 自体の protocol.json を
    // 実測したものではない）。
    // ヘルパー（BedrockCodecHelper_v332）には shield の
    // blockingTicks も ItemUse の blockDefinition runtimeId も
    // 無い（どちらも v340 ヘルパーで追加）。この実装には
    // 全バージョン通じて対応する読み書きが無いため、332 向けの
    // 分岐は生まれない。
    // ブロックパレットは形式こそ 340 と同じ name／meta 配列だが、
    // PMMP BedrockData bedrock-1.9.0 タグには
    // required_block_states.json／block_id_map.json 自体が無く
    // （ツリー 5 ファイル：LICENSE／README.md／
    // banner-patterns.json／creativeitems.json／recipes.json
    // のみ）、1.9 系のパレット実データは存在しない。そのため
    // 中身は 1.11.0 世代の実測パレット
    // （bedrock_block_palette_1_11_0.bin、3,183 件、
    // BedrockLegacyBlockPalette.load1_11_0()）を代用する。
    // 332 自体の実測ではない。ランタイム ID の割り当てと
    // チャンク翻訳側との整合は未確認。
    // Skin は 332 にもヘルパー形式自体が存在せず（v332 ヘルパーの
    // 継承チェーン v332→v313→v291→Base に readSkin／writeSkin の
    // 上書きが無い）、PlayerList／PlayerSkin は 340 と同じ
    // 旧直書き 5 要素。Skin フィールド数の並びは
    // 332＝直書き 5 要素／340＝直書き 5 要素／354＝直書き 5 要素／
    // 361＝直書き 5 要素／388＝12／389＝12／390＝16。
    // writeEmptySkin の形式は判断待ちのため 332 でも従来通り
    // （記録のみ）。
    // MoveEntityDelta は _v291 形式だが、340 以下と同じく
    // セッションが絶対座標の MoveEntity で代替する
    // （スケール未確認のため）。
    // アイテム表は 340〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、332 については代用である：PMMP BedrockData の
    // bedrock-1.9.0 タグは item_id_map.json すら無く
    // （creativeitems.json 1,222 件・SHA-1 f21f9a0c…は旧来の
    // 数値 ID でランタイム ID ではない）、required_item_list.json
    // は無い。1.9 系のランタイム ID の実データは存在しない。
    // 表の中身は bedrock-1.16.100 タグの実測で、332 自体の
    // 実測ではない。332 のランタイム ID 割り当てそのものは
    // 未確認。exact=624／shared=236／legacy=33／fallback=419
    // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §34）。
    // 332 と 340 の間の 333〜339 は Wiki の Protocol version
    // 一覧表（Bedrock の表）に行が無い番号帯（332 の次は 340）。
    // 332 の 1 つ下は 313（1.8.0）。
    V1_9_0(332, "1.9.0"),
    // 340 は 1.10.0 / 1.10.1 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.10.1（354 の V1_11_4 と同じ流儀）。
    // 共有の確認は Wiki 個別ページの infobox で行った：1.10.0／
    // 1.10.1 はいずれも Protocol version 340（340 回に自分の目で
    // 確認。1.10.1 は Windows 10 専用のホットフィックスで、
    // 1.10.2 は存在しない）。minecraft-data に 1.10 系の
    // protocol.json は無い（1.10.0／1.10.1／1.10.2 は 340 回に
    // curl で HTTP 404 を確認。対照の 1.16.201 は 200）。
    // protocolVersions.json にも 340 のエントリは無い。
    // パケット形式の根拠は CloudburstMC Protocol の v340 コーデック
    // （Bedrock_v340.java は Bedrock_v332 の CODEC を土台に
    // protocolVersion(340)／minecraftVersion("1.10.0")、helper は
    // 新規 BedrockCodecHelper_v340。VERSIONS.md で
    // Bedrock_v340＝1.10.0。BE-NOTES.md §33）。
    // 354 との差分（この実装が読み書きする範囲）：ワイヤ形式に
    // 差は無い。StartGame は StartGameSerializer_v332＋トップ
    // レベル _v291 で 354 と同一（LevelSettings 30 フィールド、
    // パレットは name／meta のみの配列形式、itemstates 無し、
    // onlySpawningV1Villagers 無し）。LevelChunk は
    // FullChunkDataSerializer_v291 で同一（subChunkCount 無し）。
    // AddPainting は _v332 で位置が BlockPosition のため同一
    // （addPaintingUsesBlockCoordinates() の <= 354 に 340 も
    // 含まれる）。PlayerList は _v291 の旧直書きエントリで同一、
    // AddPlayer は _v291（buildPlatform 無し）、AddEntity は
    // _v313、ResourcePacksInfo は _v332、ResourcePackStack は
    // _v313（gameVersion 無し）、Transaction は _v291 基本形式、
    // いずれも 354 と同一。PlayerAuthInput（144）は 340 にも
    // パケット自体が存在しない（388 で新規登録）。
    // 定義のみの差が 2 点ある：ENTITY_FLAGS は 80 件（0〜79）で、
    // 74〜79 は 354 と番号がずれる（354 が shift(74, 1)＋
    // insert(74, BLOCKED_USING_DAMAGED_SHIELD)＋insert 6 件。
    // 例：SLEEPING は 340＝74／354＝75）。この実装が書くフラグは
    // 最大 63（FLAG_EATING）までなのでワイヤに現れない。
    // ENTITY_DATA は型 106 件／index 102 件（0〜101）で、354 との
    // 差は index 102（TRADE_EXPERIENCE）が無いことだけ
    // （0〜101 は同一番号）。この実装は 102 を使わない。
    // 340 の型マップ実効値：ENTITY_FLAGS 80 件（0〜79）、
    // ENTITY_DATA 型 106 件／index 102 件（0〜101）、
    // PARTICLE_TYPES 55 件（0〜54。354 と同じ）、LEVEL_EVENTS
    // スカラー 75 件（354 は 76。差は 2022
    // PARTICLE_KNOCKBACK_ROAR の有無のみ）、SOUND_EVENTS 253 件
    // （0〜257、欠番 187・195〜198。354 は 271 件）、
    // ENTITY_EVENTS 55 件（354 と同じ）、COMMAND_PARAMS 14 件
    // （最大 44。354 と同じ）。パケット母集団は Cloudburst
    // 登録ベース 124 件・最大 ID 125・欠番 [116]、従来方式
    // （protocol.json 由来）では 125 件・最大 128・欠番
    // [37, 116, 126]（354 の集合から v354 の新規登録
    // {130, 131} を除いた逆算で、1.10.0 自体の protocol.json を
    // 実測したものではない）。
    // ブロックパレットは形式こそ 354 と同じ name／meta 配列だが、
    // PMMP BedrockData bedrock-1.10.0 タグには
    // required_block_states.json／block_id_map.json 自体が無く
    // （ツリー 5 ファイル：LICENSE／README.md／
    // banner-patterns.json／creativeitems.json／recipes.json
    // のみ）、1.10 系のパレット実データは存在しない。そのため
    // 中身は 1.11.0 世代の実測パレット
    // （bedrock_block_palette_1_11_0.bin、3,183 件、
    // BedrockLegacyBlockPalette.load1_11_0()）を代用する。
    // 340 自体の実測ではない。ランタイム ID の割り当てと
    // チャンク翻訳側との整合は未確認。
    // Skin は 340 にもヘルパー形式自体が存在せず（v340 ヘルパーの
    // 継承チェーン v340→v332→v313→v291→Base に readSkin／
    // writeSkin の上書きが無い）、PlayerList／PlayerSkin は
    // 354 と同じ旧直書き 5 要素。Skin フィールド数の並びは
    // 340＝直書き 5 要素／354＝直書き 5 要素／361＝直書き 5 要素／
    // 388＝12／389＝12／390＝16。writeEmptySkin の形式は
    // 判断待ちのため 340 でも従来通り（記録のみ）。
    // MoveEntityDelta は _v291 形式だが、354 以下と同じく
    // セッションが絶対座標の MoveEntity で代替する
    // （スケール未確認のため）。
    // アイテム表は 354〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、340 については代用である：PMMP BedrockData の
    // bedrock-1.10.0 タグは item_id_map.json すら無く
    // （creativeitems.json 1,254 件・SHA-1 59a9da08…は旧来の
    // 数値 ID でランタイム ID ではない）、ランタイム ID の
    // 実データは 1.10 系には存在しない。表の中身は
    // bedrock-1.16.100 タグの実測で、340 自体の実測ではない。
    // 340 のランタイム ID 割り当てそのものは未確認。
    // exact=624／shared=236／legacy=33／fallback=419（いずれも
    // 1_16_210 表自体の内訳）。BE-NOTES.md §33）。
    // 340 と 354 の間の 341〜349 は Wiki の Protocol version
    // 一覧表に行が無い番号帯。350〜353 は 1.11.0 ベータ
    // （§32.2 で実測）。1.10.0 ベータ（1.10.0.3／1.10.0.4）は
    // 一覧表で 340 のグループに属する。
    // 340 の 1 つ下は 332（1.9.0）。
    V1_10_1(340, "1.10.1"),
    // 354 は 1.11.0 / 1.11.1 / 1.11.2 / 1.11.3 / 1.11.4 で共有する
    // プロトコル。displayName は共有するうち最新の 1.11.4（361 の
    // V1_12_1 と同じ流儀）。共有の確認は Wiki 個別ページの infobox で
    // 行った：1.11.0〜1.11.4 はいずれも Protocol version 354
    // （354 回に自分の目で確認）。minecraft-data に 1.11 系の
    // protocol.json は無い（1.11.0／1.11.1／1.11.4 は 354 回に curl で
    // HTTP 404 を確認。対照の 1.16.201 は 200）。
    // protocolVersions.json にも 354 のエントリは無い。
    // パケット形式の根拠は CloudburstMC Protocol の v354 コーデック
    // （Bedrock_v354.java は Bedrock_v340 の CODEC を土台に
    // protocolVersion(354)／minecraftVersion("1.11.0")。
    // VERSIONS.md で Bedrock_v354＝1.11.0。BE-NOTES.md §32）。
    // 361 との差分（v361 コーデックが v354 に対して行った変更のうち、
    // この実装が読み書きする範囲）：StartGame の実効シリアライザは
    // StartGameSerializer_v332 で、LevelSettings は 30 フィールド
    // （onlySpawningV1Villagers は 361 で追加。
    // hasStartGameOnlySpawningV1Villagers() >= 361）、ブロック
    // パレットは配列形式だが要素は name String／meta ShortLE のみで
    // id ShortLE が無い（hasStartGameLegacyPaletteEntryId() は
    // 361 のみ真）、itemDefinitions（itemstates）自体が無い
    // （hasItemstates() の下限を 361 に変更）。LevelChunk は
    // FullChunkDataSerializer_v291 で subChunkCount とキャッシュ可否
    // Boolean が無い（hasLevelChunkSubChunkCount() >= 361）。
    // AddPainting は AddPaintingSerializer_v332 で位置が
    // BlockPosition（addPaintingUsesBlockCoordinates() に
    // <= 354 を追加。vec3f 化は 361）。PlayerList は
    // PlayerListSerializer_v291 で 361 と同一（旧直書きエントリ）。
    // ResourcePackStack は _v313 で 361 と同一。AddPlayer は _v291 で
    // 361 と同一。PlayerAuthInput（144）は 354 にもパケット自体が
    // 存在しない（388 で新規登録）。354 の型マップ実効値：
    // ENTITY_FLAGS 87 件（0〜86）、ENTITY_DATA index 103 件
    // （0〜102、型は 107 件。index 40 は SKIN_ID で、361 の NPC_DATA
    // とは定義のみの差）、PARTICLE_TYPES 55 件（0〜54）、
    // LEVEL_EVENTS スカラー 76 件、SOUND_EVENTS 271 件（361 と同じ）、
    // ENTITY_EVENTS 55 件（361 と同じ）。パケット母集団は
    // Cloudburst 登録ベース 126 件・最大 ID 131・欠番
    // [116, 126, 127, 128, 129]、従来方式（protocol.json 由来）では
    // 127 件・最大 131・欠番 [37, 116, 126, 129]。
    // ブロックパレットの中身は PMMP BedrockData bedrock-1.11.0 タグの
    // 実測（required_block_states.json 460 名・(名前, メタ) 3,183 件、
    // SHA-1 425f624d…と block_id_map.json 460 件、SHA-1 95f9de11…
    // ＝1.12.0 とバイト同一）から生成した
    // bedrock_block_palette_1_11_0.bin（83,378 バイト、
    // SHA-1 15ded0d9…。BedrockLegacyBlockPalette を参照）。
    // 1.12.0 のパレット（3,311 件）とは中身が違うため共用できない。
    // ランタイム ID の割り当てとチャンク翻訳側との整合は未確認。
    // Skin は 354 にもヘルパー形式自体が存在せず（v354 ヘルパーの
    // 継承チェーン v340→v332→v313→v291→Base に readSkin／writeSkin
    // の上書きが無い）、PlayerList／PlayerSkin は 361 と同じ旧直書き
    // 5 要素。Skin フィールド数の並びは 354＝直書き 5 要素／361＝
    // 直書き 5 要素／388＝12／389＝12／390＝16。writeEmptySkin の
    // 形式は判断待ちのため 354 でも従来通り（記録のみ）。
    // MoveEntityDelta は _v291 形式だが、361 以下と同じくセッションが
    // 絶対座標の MoveEntity で代替する（スケール未確認のため）。
    // アイテム表は 361〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、354 については代用である：PMMP BedrockData の
    // bedrock-1.11.0 タグは旧形式（ツリー 11 エントリ、
    // item_id_map.json 229 件／SHA-1 9aa7d0f2…、block_id_map.json
    // 460 件／SHA-1 95f9de11…、required_item_list.json は無い）しか
    // なく、ランタイム ID の実データは 1.11 系には存在しない。
    // 表の中身は bedrock-1.16.100 タグの実測で、354 自体の実測では
    // ない。354 のランタイム ID 割り当てそのものは未確認。
    // exact=624／shared=236／legacy=33／fallback=419（いずれも
    // 1_16_210 表自体の内訳）。BE-NOTES.md §32）。
    // 354 と 361 の間の 355〜359 は Wiki の Protocol version 一覧表に
    // 行が無い番号帯（360＝ベータ 1.12.0.2、361＝ベータ 1.12.0.3
    // 以降。1.11 系ベータは 350〜353 と 354 を使用。355〜359 の
    // 個別の内訳は未確認）。
    // 354 の 1 つ下は 340（1.10.0）。
    V1_11_4(354, "1.11.4"),
    // 361 は 1.12.0 / 1.12.1 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.12.1（388 の V1_13_3 と同じ流儀）。
    // 共有の確認は Wiki 個別ページの infobox で行った：1.12.0／1.12.1 は
    // いずれも Protocol version 361（361 回に自分の目で確認）。
    // minecraft-data に 1.12 系の protocol.json は無い（1.12.0／1.12.1 は
    // 361 回に curl で HTTP 404 を確認。対照の 1.16.201 は 200）。
    // パケット形式の根拠は CloudburstMC Protocol の v361 コーデック
    // （Bedrock_v361.java は Bedrock_v354 の CODEC を土台に
    // protocolVersion(361)／minecraftVersion("1.12.0")。
    // VERSIONS.md で Bedrock_v361＝1.12.0。BE-NOTES.md §31）。
    // 388 との差分（v388 コーデックが v361 に対して行った変更のうち、
    // この実装が読み書きする範囲）：StartGame は
    // StartGameSerializer_v361 で、LevelSettings は 31 フィールド
    // （末尾の vanillaVersion 文字列は 388 で新規。
    // hasStartGameVanillaVersion() >= 388）、authoritativeMovementMode
    // 自体が無い（hasMovementAuthority() に下限 388 を追加）、
    // blockPalette は単一 NBT タグではなく配列形式（件数 VarInt＋
    // 要素ごとに name String／meta ShortLE／id ShortLE）。
    // パレットの中身は PMMP BedrockData bedrock-1.12.0 タグの実測
    // （required_block_states.json 460 名・(名前, メタ) 3,311 件と
    // block_id_map.json 460 件から生成した
    // bedrock_block_palette_1_12_0.bin、92,944 バイト、
    // SHA-1 3553c5c9…。BedrockLegacyBlockPalette を参照）。
    // ランタイム ID の割り当てとチャンク翻訳側との整合は未確認。
    // AddPlayer は AddPlayerSerializer_v291 で末尾の buildPlatform
    // （IntLE）が無い（addPlayerHasBuildPlatform() >= 388）。
    // PlayerList は PlayerListSerializer_v291 で、add エントリが旧直書き
    // 形式（skinId／skinData／capeData／geometryName／geometryData を
    // エントリに埋め込み、buildPlatform／ヘルパー Skin／teacher／host
    // は無い。verified は両世代とも無し。remove は uuid のみで同一。
    // playerListUsesLegacyEntry() <= 361）。ResourcePackStack は
    // ResourcePackStackSerializer_v313 で末尾の gameVersion 文字列が
    // 無い（resourcePackStackHasGameVersion() >= 388）。
    // PlayerAuthInput（144）は 361 にはパケット自体が存在しない
    // （388 で新規登録）。Interact は LEAVE_VEHICLE の mousePosition が
    // 388 で追加されたが、この実装は受信しても無視するため分岐なし。
    // SetEntityData／AddItemEntity／MobEffect／UpdateAttributes は
    // 両世代とも _v291 シリアライザが実効で、差は ENTITY_DATA の
    // index 107〜112 と ENTITY_FLAGS の 88〜91 の追加分だけ（既存の
    // 番号は 1 件もずれない）ため分岐なし。361 の型マップ実効値：
    // ENTITY_FLAGS 88 件（0〜87）、ENTITY_DATA index 107 件（0〜106、
    // 型は 111 件）、PARTICLE_TYPES 58 件（388 と同一）、
    // LEVEL_EVENTS スカラー 77 件、SOUND_EVENTS 271 件。
    // パケット母集団は Cloudburst 登録ベース 132 件・最大 ID 136・
    // 欠番 [16, 116, 127, 128]、従来方式（protocol.json 由来）では
    // 133 件・最大 136・欠番 [16, 37, 116]。
    // Skin は 361 にはヘルパー形式自体が存在せず（BedrockCodecHelper_v361
    // の継承チェーンに readSkin／writeSkin の上書きが無い）、PlayerList／
    // PlayerSkin は旧直書きを使う。Skin フィールド数の並びは
    // 361＝直書き 5 要素／388＝12／389＝12／390＝16。writeEmptySkin の
    // 形式は判断待ちのため 361 でも従来通り（記録のみ）。
    // MoveEntityDelta は _v291 形式（flags が Byte）だが、408 以下と
    // 同じくセッションが絶対座標の MoveEntity で代替する
    // （スケール未確認のため）。
    // アイテム表は 388〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、361 については代用である：PMMP BedrockData の
    // bedrock-1.12.0 タグは旧形式（ツリー 11 エントリ、
    // item_id_map.json 689 件／SHA-1 ec406ec7…、block_id_map.json
    // 460 件／SHA-1 95f9de11…、required_block_states は .json のみで
    // .nbt は無い）しかなく、ランタイム ID の実データは 1.12 系には
    // 存在しない。表の中身は bedrock-1.16.100 タグの実測で、361 自体の
    // 実測ではない。361 のランタイム ID 割り当てそのものは未確認。
    // exact=624／shared=236／legacy=33／fallback=419（いずれも
    // 1_16_210 表自体の内訳）。BE-NOTES.md §31）。
    // なお BE-NOTES.md §30.3 の updateSerializer 一覧（SetEntityData→
    // v388 等）は実ファイルの Bedrock_v388.java と食い違う（該当する
    // updateSerializer は無く、いずれも _v291 系が両世代の実効）。
    // §31 で訂正を記録した。
    // 361 の 1 つ下は 354（1.11.0）。361 と 388 の間の 362〜387 は
    // リリース版の無い番号帯（370・371・385・386・387 は 1.13.0 の
    // ベータが使ったことを Wiki infobox で確認。362〜369 の個別の
    // 内訳は未確認）。
    V1_12_1(361, "1.12.1"),
    // 388 は 1.13.0 / 1.13.1 / 1.13.2 / 1.13.3 で共有するプロトコル。
    // displayName は共有するうち最新の 1.13.3（389 の V1_14_41 と同じ流儀）。
    // protocolVersions.json に 388 のエントリは無く（422 未満のエントリは
    // 100・82・70 のみ。BE-NOTES.md §29）、minecraft-data に 1.13 系の
    // protocol.json も無い（1.13.0／1.13.1 は 388 回に curl で HTTP 404 を
    // 再確認。対照の 1.16.201 は 200）。
    // 共有の確認は Wiki 個別ページの infobox で行った：1.13.0／1.13.1／
    // 1.13.2／1.13.3 はいずれも Protocol version 388（388 回に自分の目で
    // 確認。1.13.2／1.13.3 は iOS・iPadOS 専用ホットフィックス）。
    // パケット形式の根拠は CloudburstMC Protocol の v388 コーデック
    // （Bedrock_v388.java は Bedrock_v361 の CODEC に Explode の登録解除
    // （ID 23 は同じビルダーで TickSync が引き継いだ）、新規登録 9 件
    // （TickSync 23、EducationSettings 137、Emote 138、
    // MultiplayerSettings 139、SettingsCommand 140、AnvilDamage 141、
    // CompletedUsingItem 142、NetworkSettings 143、PlayerAuthInput 144）、
    // updateSerializer 21 件、ENTITY_FLAGS への 88〜91 挿入、
    // ENTITY_DATA への 107〜112 挿入などの TypeMap 変更を行ったもの。
    // VERSIONS.md で Bedrock_v388＝1.13.0。BE-NOTES.md §30）。
    // 389 との差分（v389 コーデックが v388 に対して行った変更のうち、
    // この実装が読み書きする範囲）：登録・解除は無くパケット集合は同一
    // （従来方式で 141 件・最大 ID 144・欠番 [16, 37, 116]）。
    // 変更は PARTICLE_TYPES の差し替え（388 は 58 件＝0〜57 でチェーン
    // 由来のまま、389 は DRIP_HONEY を 28 へ挿入して 28 以後を +1 し、
    // 59〜67 を挿入して 68 件）と、それに伴う LEVEL_EVENTS の
    // 再バインド、ENTITY_DATA の AREA_EFFECT_CLOUD_PARTICLE
    // トランスフォーマ差し替え、Event／LevelEvent／LevelEventGeneric の
    // シリアライザ更新だけ。この実装は LevelEvent／LevelSoundEvent／
    // Event を符号化せず、メタデータは ENTITY_DATA の ID／値のペアを
    // そのまま送るため、送受信範囲にワイヤ差は無い。全述語の評価も
    // 389 と完全一致する（閾値が 388 と 389 の間に落ちる述語は無い。
    // 最小の >= 閾値は playerListAddHasVerified の 390）。
    // 核パケットは 389 と同一形式：StartGame
    // （StartGameSerializer_v388、LevelSettings 32 フィールド）、
    // AddPlayer（AddPlayerSerializer_v388、25 フィールド）、
    // AddEntity（AddEntitySerializer_v313、15 フィールド）、
    // PlayerList（PlayerListSerializer_v388。add に verified 無し、
    // remove は uuid のみ）、ResourcePacksInfo（v332 世代）、
    // ResourcePackStack（ResourcePackStackSerializer_v388）、
    // LevelChunk（LevelChunkSerializer_v361）、
    // InventoryTransaction（v291 世代の基本形式で legacy_request_id 無し）。
    // ENTITY_DATA（113 件＝0〜112）／ENTITY_FLAGS（92 件＝0〜91）は
    // v388 が定義し 389 がそのまま継承するため、388 では自世代の定義に
    // なるが分岐は不要。Skin も 389 と同じ BedrockCodecHelper_v388 で
    // 12 フィールドだが、writeEmptySkin の形式は全バージョン共通の
    // 判断待ちのため 388 でも従来通り（記録のみ）。
    // MoveEntityDelta は MoveEntityDeltaSerializer_v388 が 388 自身の
    // ものだが、389 と同じくセッションが絶対座標の MoveEntity で
    // 代替する（スケール未確認のため）。
    // 388 と 389 の間に番号は無い（連続）。388 の 1 つ下は 361
    // （1.12.0）で、間の 362〜387 はリリース版の無い番号帯
    // （370・371・385・386・387 は 1.13.0 のベータが使ったことを
    // Wiki infobox で確認。362〜369 の個別の内訳は未確認）。
    // アイテム表は 389〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、388 については代用である：PMMP BedrockData の
    // bedrock-1.13.0 タグは旧形式（ツリー 14 エントリ、item_id_map.json
    // 696 件／SHA-1 5cac37ca…、block_id_map.json 465 件／
    // SHA-1 1ab642fe…、required_block_states.nbt 294,991 バイト／
    // SHA-1 a057f7a5…）しかなく、ランタイム ID の実データは 1.13 系には
    // 存在しない。表の中身は bedrock-1.16.100 タグの実測で、388 自体の
    // 実測ではない。388 のランタイム ID 割り当てそのものは未確認。
    // exact=624／shared=236／legacy=33／fallback=419（いずれも
    // 1_16_210 表自体の内訳）。BE-NOTES.md §30）。
    V1_13_3(388, "1.13.3"),
    // 389 は 1.14.0 / 1.14.1 / 1.14.20 / 1.14.21 / 1.14.30 / 1.14.41 で
    // 共有するプロトコル（1.14.0.12 は PS4 専用ホットフィックスで同じ 389）。
    // displayName は共有するうち最新の 1.14.41（390 の V1_14_60 と同じ流儀）。
    // protocolVersions.json に 389 のエントリは無く、minecraft-data に
    // 1.14 系の protocol.json も無い（1.14.0／1.14.20／1.14.30／
    // 1.14.60／1.13.0 のいずれも HTTP 404。BE-NOTES.md §28・§29）。
    // 共有の確認は Wiki 個別ページの infobox で行った：1.14.1／1.14.20／
    // 1.14.30 は Protocol version 389（389 回に再確認）、1.14.41 は
    // Wiki「Bedrock Protocol version numbers」の Entry で 389、
    // 1.14.0.12（PS4 専用）も 389。1.14.0／1.14.21 は §28 で確認済み。
    // Cloudburst VERSIONS.md の Bedrock_v389＝「1.14.0 - 1.14.50」は
    // Wiki と食い違う：「Bedrock Edition 1.14.50」の個別ページは
    // 存在せず（389 回の検索でも現れるのは beta 1.14.0.50 と
    // Minecraft Education 1.14.50 だけ）、389 を共有する最新リリースは
    // 1.14.41 のため displayName は 1.14.41 とした。
    // パケット形式の根拠は CloudburstMC Protocol の v389 コーデック
    // （Bedrock_v389.java は Bedrock_v388 の CODEC に PARTICLE_TYPES の
    // 差し替え（28 の shift＋DRIP_HONEY 挿入、59〜67 の挿入）と
    // ENTITY_DATA の再バインド、Event／LevelEvent／LevelEventGeneric の
    // シリアライザ更新だけを行ったもの。VERSIONS.md で Bedrock_v389＝
    // 1.14.0 - 1.14.50。BE-NOTES.md §29）。
    // 390 との差分（v390 コーデックが v389 に対して行った変更のうち、
    // この実装が読み書きする範囲）：登録・解除は無くパケット集合は
    // 同一。PlayerList の add に trustedSkin（verified）の末尾ブロックが
    // 390 で追加された（389 の実効 PlayerListSerializer_v388 には無い。
    // playerListAddHasVerified() を参照）。remove はどちらも uuid のみで、
    // playerListRemoveHasVerified()／addPaintingUsesBlockCoordinates()
    // の境界は 389 でも従来通り（どちらも偽）で変えていない。
    // Skin のヘルパー形式は 389 が 12 フィールド（BedrockCodecHelper_v388）、
    // 390 が 16 フィールド（armSize／skinColor／personaPieces／
    // tintColors を追加）だが、writeEmptySkin の形式は全バージョン
    // 共通の判断待ちのため 389 でも従来通り（記録のみ）。
    // PlayerSkinPacket は v390 で末尾に trustedSkin が追加されたが、
    // この実装は PlayerSkinPacket を送受信しないため対象外。
    // StartGame（StartGameSerializer_v388、LevelSettings 32 フィールド）、
    // AddPlayer（25 フィールド）、AddEntity（15 フィールド）、
    // ResourcePacksInfo、ResourcePackStack、LevelChunk、
    // InventoryTransaction（v291 世代の基本形式で legacy_request_id
    // 無し）は 390 と同一形式で、分岐は 390 と同じ述語のまま。
    // ENTITY_DATA／ENTITY_FLAGS／PARTICLE_TYPES は v389 の定義を
    // 390 がそのまま継承するため、メタデータの分岐も不要。
    // MoveEntityDelta は 390 と同じくセッションが絶対座標の
    // MoveEntity で代替する（スケール未確認のため）。
    // 389 と 390 の間に番号は無い（連続）。389 の 1 つ下は 388（1.13.0）。
    // アイテム表は 390・407〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、389 については代用である：PMMP BedrockData に
    // bedrock-1.14.60 タグは無く、bedrock-1.14.0 タグは旧形式
    // （item_id_map.json 702 件・block_id_map.json 469 件）しかなく、
    // ランタイム ID の実データは 1.14 系には存在しない。表の中身は
    // bedrock-1.16.100 タグの実測で、389 自体の実測ではない。
    // 389 のランタイム ID 割り当てそのものは未確認。
    // exact=624／shared=236／legacy=33／fallback=419（いずれも
    // 1_16_210 表自体の内訳）。BE-NOTES.md §29）。
    V1_14_41(389, "1.14.41"),
    // 390 は 1.14.60 のみが使うプロトコル（共有するリリース版は無い）。
    // displayName は 1.14.60 そのもの。
    // protocolVersions.json に 390 のエントリは無く、minecraft-data に
    // 1.14 系の protocol.json も無い（1.14.0／1.14.20／1.14.30／
    // 1.14.60／1.13.0 のいずれも HTTP 404。BE-NOTES.md §28）。
    // 共有の確認は Wiki 個別ページの infobox で行った：1.14.60 は
    // Protocol version 390、1.14.0／1.14.1／1.14.20／1.14.21／
    // 1.14.30／1.14.41 はいずれも 389（別プロトコル）。
    // 「Bedrock Edition 1.14.50」の個別ページは存在しない。
    // パケット形式の根拠は CloudburstMC Protocol の v390 コーデック
    // （Bedrock_v390.java は Bedrock_v389 の CODEC に PlayerList／
    // PlayerSkin のシリアライザ更新とヘルパー差し替えだけを行ったもの。
    // VERSIONS.md で Bedrock_v390＝1.14.60。BE-NOTES.md §28）。
    // 407 との差分（v407 コーデックが v390 に対して行った変更のうち、
    // この実装が読み書きする範囲）：新規パケット 12 件（145〜156）と
    // VideoStreamConnect（126）の登録解除はこの実装では使わない。
    // StartGame は v407 追加分（spawn_biome_type・custom_biome_name・
    // education_production_id・limited_world_width／height・
    // is_new_nether・force_experimental_gameplay・末尾の
    // server_authoritative_inventory）が 390 には無い
    // （hasStartGame407Fields() を参照。edu_edition_offers は 390 が
    // Boolean・407 が VarInt だが、書く値 0 はどちらも 0x00 で
    // バイト同一のため分岐しない）。InventoryTransaction は 390 に
    // legacy_request_id が無く、アクションに network_ids も無い
    // （transactionHasLegacyRequestId() を参照）。PlayerList の
    // remove に verified 配列は無く、AddPainting の coordinates は
    // vec3f（どちらも 422・428 の protocol.json とは記述が食い違う
    // 境界で、playerListRemoveHasVerified()／
    // addPaintingUsesBlockCoordinates() の下限を参照）。
    // メタデータは ENTITY_FLAGS の 86〜91 が 407 で +1 ずれるが、
    // この実装が書くフラグは key 0 が 63 以下、key 92（FLAGS_2）が
    // 相対ビット 0・2・11 とスニッファー状態の 40〜48 で、ずれる
    // 範囲（key 0 の 86〜91、FLAGS_2 では相対 22〜27）と重ならず、
    // ENTITY_DATA の既存キーにもずれが無いため分岐不要。
    // MoveEntityDelta は 407 と同じ VarInt 差分形式で、セッションは
    // 絶対座標の MoveEntity で代替する（スケール未確認のため）。
    // 391〜406 はベータ版の番号（407 のコメントを参照）。
    // 390 の 1 つ下は 389（1.14.0〜1.14.41 が共有。Cloudburst の
    // VERSIONS.md では Bedrock_v389＝1.14.0 - 1.14.50）。
    // アイテム表は 407〜431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用するが、390 については代用である：PMMP BedrockData に
    // bedrock-1.14.60 タグは無く、bedrock-1.14.0 タグは旧形式
    // （item_id_map.json 702 件・block_id_map.json 469 件）しか
    // なく、1.16.0 の同名ファイル（807 件・553 件）とも中身が違う。
    // ランタイム ID の実データは 1.14 系には存在しないため、表の
    // 中身は bedrock-1.16.100 タグの実測で、390 自体の実測では
    // ない。390 のランタイム ID 割り当てそのものは未確認。
    // exact=624／shared=236／legacy=33／fallback=419（いずれも
    // 1_16_210 表自体の内訳）。BE-NOTES.md §28）。
    V1_14_60(390, "1.14.60"),
    // 407 は 1.16.0 / 1.16.1 / 1.16.1.03 / 1.16.1.04 / 1.16.10 で
    // 共有するプロトコル。
    // displayName は共有するうち最新の 1.16.10（408 の V1_16_61 と同じ流儀）。
    // protocolVersions.json に 407 のエントリは無く、minecraft-data に
    // 1.16.0 系の protocol.json も無い（HTTP 404。BE-NOTES.md §23・§26）。
    // 共有は Wiki「Bedrock Edition 1.16.0」「Bedrock Edition 1.16.1」
    // 「Bedrock Edition 1.16.1.03」「Bedrock Edition 1.16.1.04」
    // 「Bedrock Edition 1.16.10」個別ページの infobox（いずれも Protocol
    // version 407）で確認した（§26 の 408 回の確認を §27 で再確認。
    // 1.16.1.03／1.16.1.04 は PS4 専用ホットフィックスで、独立した
    // 個別ページがある）。
    // パケット形式の根拠は CloudburstMC Protocol の v407 コーデック
    // （Bedrock_v407.java そのもの。VERSIONS.md で Bedrock_v407＝
    // 1.16.0 - 1.16.10。BE-NOTES.md §27）。
    // 408 はこの v407 の CODEC をそのまま流用していたため
    // （Bedrock_v408.java は protocolVersion と minecraftVersion だけを
    // 変えたもの）、407 と 408 の実効定義は同一で、この実装が読み書き
    // する範囲では形式差分は無い。419 との差分は 408 のコメントと
    // §26 を参照（StartGame の experiments 無し・ブロックパレットの
    // リストタグ形式・ResourcePackStack の旧形式・末尾 tick 無し・
    // MoveEntityDelta の MoveEntity 代替は 407 も同じ）。
    // 407 と 408 の間に番号は無い（連続）。なお 1.16.20 のベータ
    // （beta 1.16.20.52）は 407 のままだった（§26 で infobox 確認）。
    // 407 の 1 つ下は 390（1.14.60。Cloudburst の VERSIONS.md で
    // Bedrock_v407 の下は Bedrock_v390＝1.14.60）。391〜406 は
    // ベータ版の番号（Wiki「Protocol version」表で 391〜396＝
    // beta 1.15.0.11〜1.15.0.56、400〜406＝beta 1.16.0.51〜
    // 1.16.0.63。397〜399 は表に無く未確認。BE-NOTES.md §28）。
    // アイテム表は 408・419・422・428・431 と同じ
    // bedrock_item_ids_1_16_210.bin を共用するが、407 についても
    // 代用である：PMMP BedrockData の bedrock-1.16.0 タグは 407 を
    // 共有するリリース版 1.16.0 自体のデータだが、旧形式
    // （item_id_map.json 等）しかなく required_item_list.json は無い。
    // 表の中身は bedrock-1.16.100 タグ（419 のリリース版）の実測で、
    // 407 自体の実測ではない。block_id_map.json と item_id_map.json が
    // bedrock-1.16.0 タグと bedrock-1.16.100 タグでバイト同一である
    // ことは確認済みだが、407 のランタイム ID 割り当てそのものは
    // 未確認。exact=624／shared=236／legacy=33／fallback=419。
    // BE-NOTES.md §27）。
    V1_16_10(407, "1.16.10"),
    // 408 は 1.16.20 / 1.16.21 / 1.16.40 / 1.16.42 / 1.16.50 / 1.16.60 /
    // 1.16.61 で共有するプロトコル。
    // displayName は共有するうち最新の 1.16.61（419 の V1_16_101 と同じ流儀）。
    // protocolVersions.json に 408 のエントリは無く、minecraft-data に
    // 1.16.0 系の protocol.json も無い（1.16.0／1.16.1／1.16.10／1.16.20／
    // 1.16.40／1.16.100 のいずれも HTTP 404。BE-NOTES.md §23・§26）。
    // 共有は Wiki「Bedrock Edition 1.16.20」「Bedrock Edition 1.16.21」
    // 「Bedrock Edition 1.16.40」「Bedrock Edition 1.16.42」
    // 「Bedrock Edition 1.16.50」「Bedrock Edition 1.16.60」
    // 「Bedrock Edition 1.16.61」個別ページの infobox（いずれも Protocol
    // version 408）で確認した。1.16.0／1.16.1／1.16.1.03／1.16.1.04／
    // 1.16.10 は 407 で、408 とは別プロトコル（infobox で確認。
    // §25 の「1.16.61〜1.16.0 の共有」という見込みは誤りだった）。
    // パケット形式の根拠は CloudburstMC Protocol の v408 コーデック
    // （Bedrock_v408.java は Bedrock_v407 の CODEC をそのまま使い、
    // protocolVersion と minecraftVersion だけを変えたもの。VERSIONS.md で
    // Bedrock_v408＝1.16.20。BE-NOTES.md §26）。
    // 419 との差分（v419 コーデックが v408 に対して行った変更のうち、
    // この実装が読み書きする範囲）：新規パケット 6 件（157〜162）は
    // この実装では使わない。StartGame は experiments 2 フィールドが無く、
    // ブロックパレットが単一 NBT リストタグ形式で、movement_authority は
    // Boolean 形式だがコードは 0（client＝false）しか書かないため
    // zigzag32 の 0 とバイト同一で分岐不要。itemDefinitions の要素は
    // componentBased が無い 2 フィールドだが、この実装は itemstates を
    // 常に空で送るためワイヤに現れない。ResourcePackStack は legacy
    // experiment フラグ（bool）＋末尾の gameVersion という旧形式。
    // MovePlayer／SetEntityData／UpdateAttributes は末尾の tick が無い。
    // MoveEntityDelta は VarInt 差分形式だが差分のスケールが実測ソースで
    // 確認できないため、408 では絶対座標の MoveEntity で代替する。
    // ContainerClose の serverInitiated・ItemStackResponse の status 形式・
    // COMMAND_PARAMS の shift・SOUND_EVENTS の 317 差し替えと 318 挿入・
    // LEVEL_EVENTS の 1900〜1903 挿入は、いずれもこの実装が読み書き
    // しない範囲か番号ずれの無い変更のため影響しない（ENTITY_DATA は
    // v407 の定義を 419 もそのまま使うためメタデータ辞書も同一）。
    // PlayerAuthInput と MovePlayer の復号は先頭部分しか読まないため
    // 末尾の tick／delta の有無に影響されない。409〜418 は 1.16.100 の
    // ベータ版の番号（beta 1.16.100.50＝409 と beta 1.16.100.59＝418 を
    // 各ベータページの infobox で確認。中間の番号の個別対応は未確認）。
    // なお 1.16.20 のベータ（beta 1.16.20.52）は 407 のままだった
    // （infobox で確認）。
    // 408 の 1 つ下は 407（1.16.10 等）。
    // アイテム表は 419・422・428・431 と同じ bedrock_item_ids_1_16_210.bin
    // を共用するが、408 については代用である：PMMP BedrockData に
    // 408 を共有するバージョンの required_item_list.json は存在しない
    // （bedrock-1.16.20／1.16.40 タグ自体が無く、bedrock-1.16.0 タグは
    // 407 のバージョンのもので旧形式の item_id_map.json しか無い）。
    // 表の中身は bedrock-1.16.100 タグ（419 のリリース版）の実測で、
    // 408 自体の実測ではない。block_id_map.json と item_id_map.json が
    // bedrock-1.16.0 タグと bedrock-1.16.100 タグでバイト同一であることは
    // 確認済みだが、408 のランタイム ID 割り当てそのものは未確認。
    // exact=624／shared=236／legacy=33／fallback=419。BE-NOTES.md §26）。
    V1_16_61(408, "1.16.61"),
    // 419 は 1.16.100 / 1.16.101 で共有するプロトコル。
    // displayName は共有するうち最新の 1.16.101（422 の V1_16_201 と同じ流儀）。
    // protocolVersions.json に 419 のエントリは無く、minecraft-data に
    // 1.16.100／1.16.101 の protocol.json も無い（どちらも HTTP 404。
    // BE-NOTES.md §23）。共有は Wiki「Bedrock Edition 1.16.100」
    // 「Bedrock Edition 1.16.101」個別ページの infobox（どちらも Protocol
    // version 419。1.16.101 は「1.16.100 を実行する全 Bedrock プラットフォーム
    // と互換」と明記）で確認した。パケット形式の根拠は CloudburstMC Protocol
    // の v419 コーデック（bedrock-codec の Bedrock_v419.java。VERSIONS.md で
    // Bedrock_v419＝1.16.100。BE-NOTES.md §25）。
    // 422 との差分は filter_text_packet（163）が無いことだけ（419 は
    // 159 パケット・最大 ID 162・欠番 16・37・116）。v422 コーデックは v419 に
    // 対して FilterText（163）の追加・ResourcePacksInfo のシリアライザ更新・
    // ItemStackRequest／ItemStackResponse の形式変更だけで、それ以外は
    // v419 のシリアライザをそのまま継承する。ResourcePacksInfo の差分は
    // リソースパックエントリ末尾の raytracing フラグ（v422 の
    // ResourcePacksInfoSerializer_v422）だけで、この実装は空の一覧しか
    // 書かないためワイヤに現れない。ItemStackRequest／ItemStackResponse は
    // この実装では読み書きしない。StartGame は StartGameSerializer_v419 を
    // 419・422 で共用し、フィールド順・型が 422 の protocol.json の
    // 57 フィールドと一致する（§25 で突き合わせ済み）。AddPainting の
    // coordinates と PlayerList の verified は、§25 では protocol <= 428 の
    // 既存の分岐で 419 も扱われるとしていたが、§28 で v390 コーデック
    // （AddPaintingSerializer_v361・PlayerListSerializer_v390 がどちらも
    // v419 まで未上書き）を実読したところ、419 の coordinates は vec3f で
    // remove に verified 配列は無いため、述語の下限を 422 に改めた
    // （addPaintingUsesBlockCoordinates()／playerListRemoveHasVerified()
    // を参照）。420（1.16.200.51）・421（1.16.200.52）は
    // ベータ版の番号。419 の 1 つ下は 408（1.16.61 等）。
    // アイテム表は 422・428・431 と同じ bedrock_item_ids_1_16_210.bin を
    // 共用する（PMMP bedrock-1.16.100 タグの required_item_list.json から
    // 再生成した候補表が既存の表とバイト同一。bedrock-1.16.100 タグは
    // 419 を共有するリリース版 1.16.100 自体のデータなので他バージョン
    // からの代用ではない。ただし bedrock-1.16.101 タグは存在しないため、
    // 1.16.101 固有のアイテム差分が無いことの直接の証拠は無い（未確認）。
    // exact=624／shared=236／legacy=33／fallback=419。BE-NOTES.md §25）。
    V1_16_101(419, "1.16.101"),
    // 422 は 1.16.200 / 1.16.201 で共有するプロトコル。
    // displayName は共有するうち最新の 1.16.201（431 の V1_16_221 と同じ流儀）。
    // protocolVersions.json では 422＝1.16.201 のみだが、Wiki「Bedrock Edition
    // 1.16.200」「Bedrock Edition 1.16.201」個別ページの infobox も Protocol
    // version 422（BE-NOTES.md §22）。minecraft-data に 1.16.200 の
    // protocol.json は無く（HTTP 404）、422 の代表ディレクトリは
    // 1.16.201 のみ。423〜427 は beta 1.16.210 の番号（426 は Wiki 表に
    // エントリ無しで未確認）、429・430 は beta 1.16.220 の番号。
    // 422 の 1 つ下は 419（1.16.101／1.16.100）。428 との差分は追加 1 件
    // （164 debug_renderer）・型変更 52 件。StartGame は 57 フィールドで
    // 428（59 フィールド）と rewind_history_size・
    // server_authoritative_block_breaking の 2 フィールドだけ違う
    // （hasStartGameRewindFields() を参照）。spawn は 428 の
    // player_position と改名だけでワイヤ同一。runtime_entity_id は
    // AddPlayer 等で 422 が varint・428 が varint64 だが、どちらも
    // 符号なし LEB128 で値が 32 ビットに収まる限りバイト同一のため分岐不要
    // （BedrockVarInt.writeUnsignedVarInt。BE-NOTES.md §22）。
    // アイテム表は 428 と同じ bedrock_item_ids_1_16_210.bin を共用する
    // （bedrock-1.16.200 タグの required_item_list.json から再生成した
    // 候補表が既存の表とバイト同一。1.16.200 は 422 を共有するリリース版
    // そのものなので他バージョンからの代用ではない。ただし 1.16.201 固有の
    // アイテム差分が無いことの直接の証拠は無い（bedrock-1.16.201 タグは
    // 存在しない）ため未確認。exact=624／shared=236／legacy=33／
    // fallback=419。BE-NOTES.md §22）。
    V1_16_201(422, "1.16.201"),
    // 428 は 1.16.210 のみで共有するリリース版は無い（protocolVersions.json
    // では 428＝1.16.210 の 1 件のみ。Wiki「Bedrock Edition 1.16.210」
    // 個別ページの infobox も Protocol version 428。beta 1.16.210.58〜.61
    // も 428 を共有するがリリース版は 1.16.210 のみ。BE-NOTES.md §21）。
    // 428 の 1 つ下は 422（1.16.201）。429＝beta 1.16.220.50、430＝beta
    // 1.16.220.51。423〜427 は beta 1.16.210 の番号（426 は Wiki 表に
    // エントリ無しで未確認）。
    // 431 との差分は追加 0・削除 0・リネーム 0・型変更 60 件。StartGame は
    // 59 フィールドで 431 と block_palette（428）／block_properties（431）
    // の改名 1 件だけ違う（定義は JSON 完全一致）。
    // アイテム表は 431 と同じ bedrock_item_ids_1_16_210.bin を共用する
    // （1.16.210 自体の実測データで、428 では代用ではない。431 回は
    // 1.16.220 の代用だった。exact=624／shared=236／legacy=33／
    // fallback=419。BE-NOTES.md §21）。
    V1_16_210(428, "1.16.210"),
    // 431 は 1.16.220 / 1.16.221 で共有するプロトコル。
    // displayName は共有するうち最新の 1.16.221（440 の V1_17_2 と同じ流儀）。
    // protocolVersions.json では 431＝1.16.220 のみだが、Wiki「Bedrock Edition
    // 1.16.220」「Bedrock Edition 1.16.221」個別ページの infobox も Protocol
    // version 431（beta 1.16.220.52 も 431。BE-NOTES.md §20）。minecraft-data に
    // 1.16.221 の protocol.json は無く（HTTP 404）、431 の代表ディレクトリは
    // 1.16.220 のみ。432〜439 にエントリは無い（433・434・435・437 は Beta
    // 1.16.230／1.17.0 の番号、432・436・438・439 は Wiki 表にエントリ無しで
    // 未確認。BE-NOTES.md §20）。
    // 431 の 1 つ下は 428（1.16.210）。440 との差分は追加 3 件（165
    // sync_entity_property・166 add_volume_entity・167 remove_volume_entity）・
    // 型変更 11 件。StartGame は 59 フィールドで 440 と engine（末尾の 60
    // フィールド目）1 件だけ違う（hasStartGameEngine() を参照）。
    // ResourcePacksInfo は 440 と JSON 完全一致（4 フィールドで
    // force_server_packs は無い。hasForceServerPacks() を参照）。
    // add_player・add_entity・set_entity_data も 431＝440 で JSON 完全一致。
    // アイテム表は bedrock-1.16.210 タグから生成した
    // bedrock_item_ids_1_16_210.bin を使う（bedrock-1.16.220 タグは存在
    // しないため 1.16.210 の代用。1.16.220 自体の実測ではない。
    // exact=624／shared=236／legacy=33／fallback=419。1_17_0 の表とは
    // 369/1312 行が違うため共用できない。554 が 1_19_40 の表を共用するのと
    // 同じく、ファイル名はデータの出所（1.16.210）に合わせた。
    // BE-NOTES.md §20 参照）。protocol.json の根拠は data/bedrock/1.16.220/。
    V1_16_221(431, "1.16.221"),
    // 440 は 1.17.0 / 1.17.1 / 1.17.2 で共有するプロトコル。
    // displayName は共有するうち最新の 1.17.2（448 の V1_17_11 と同じ流儀）。
    // protocolVersions.json では 440＝1.17.0 のみだが、Wiki「Bedrock Edition
    // 1.17.0」「Bedrock Edition 1.17.1」「Bedrock Edition 1.17.2」個別ページの
    // infobox も Protocol version 440（BE-NOTES.md §19）。minecraft-data に
    // 1.17.1／1.17.2 の protocol.json は無く（HTTP 404）、440 の代表ディレクトリは
    // 1.17.0 のみ。441〜447 にエントリは無い（441 は Beta 1.17.10.21 の番号、
    // 442〜447 は Wiki 表にエントリ無しで未確認。BE-NOTES.md §19）。
    // 440 の 1 つ下は 431（1.16.220）。448 との差分は追加 2 件（168
    // simulation_type・169 npc_dialogue）・型変更 10 件。StartGame は 60
    // フィールドで 448 と JSON 完全一致。ResourcePacksInfo の
    // force_server_packs は 448 で新規（440 には無い。hasForceServerPacks()
    // を参照）。protocol.json の根拠は data/bedrock/1.17.0/。
    V1_17_2(440, "1.17.2"),
    // 448 は 1.17.10 / 1.17.11 で共有するプロトコル。
    // displayName は共有するうち最新の 1.17.11（471 の V1_17_41 と同じ流儀）。
    // protocolVersions.json では 448＝1.17.10 のみだが、Wiki「Bedrock Edition
    // 1.17.10」「Bedrock Edition 1.17.11」個別ページの infobox も Protocol
    // version 448（BE-NOTES.md §18）。minecraft-data に 1.17.11 の
    // protocol.json は無く（HTTP 404）、448 の代表ディレクトリは 1.17.10
    // のみ。449〜464 にエントリは無い（453・455・456・459・462・464 は
    // Beta 1.17.20／1.17.30 の番号、残り 10 番号は Wiki 表にエントリ無しで
    // 未確認。BE-NOTES.md §18）。
    // 448 の 1 つ下は 440（1.17.0）。448 と 465 の StartGame は
    // edu_resource_uri（465 で新規、448 には無い）1 フィールドだけ違う
    // （60 フィールド対 61 フィールド。hasStartGameEduResourceUri() を参照）。
    // add_player・add_entity・set_entity_data は 448＝465 で JSON 完全一致。
    // 465 で新規の edu_uri_resource_packet・create_photo・
    // update_subchunk_blocks・photo_info_request（170〜173）は 448 には無く、
    // 現行コードはどれも送らないため分岐不要。protocol.json の
    // 根拠は data/bedrock/1.17.10/。
    V1_17_11(448, "1.17.11"),
    // 465 は 1.17.30 / 1.17.32 / 1.17.33 / 1.17.34 で共有するプロトコル。
    // displayName は共有するうち最新の 1.17.34（471 の V1_17_41 と同じ流儀）。
    // protocolVersions.json では 465＝1.17.30 のみだが、Wiki「Bedrock Edition
    // 1.17.30」「Bedrock Edition 1.17.32」「Bedrock Edition 1.17.33」
    // 「Bedrock Edition 1.17.34」個別ページの infobox も Protocol
    // version 465（BE-NOTES.md §17）。1.17.31 はサーバーのみの
    // ホットフィックスでクライアントページは無い。
    // 465 の 1 つ下は 448（1.17.10）。466〜470 にエントリは無い
    // （Wiki 表に 466〜470 の Bedrock バージョンは無く、個別の理由は
    // 未確認。472・473 と同じ扱い。BE-NOTES.md §17）。
    // 465 と 471 の StartGame は JSON 完全一致（61 フィールド。
    // block_pallete_checksum はどちらにも無い）。add_player・
    // add_entity・set_entity_data も 465＝471 で JSON 完全一致。
    // 471 で新規の subchunk・subchunk_request（174・175）は 465 には無く、
    // packet_animate・ItemStackRequest の型も違うが、現行コードは
    // どれも読まない・書かないため分岐不要。protocol.json の
    // 根拠は data/bedrock/1.17.30/。
    V1_17_34(465, "1.17.34"),
    // 471 は 1.17.40 / 1.17.41 で共有するプロトコル。
    // displayName は共有するうち最新の 1.17.41（475 の V1_18_2 と同じ流儀）。
    // protocolVersions.json では 471＝1.17.40 のみだが、Wiki「Bedrock Edition
    // 1.17.40」「Bedrock Edition 1.17.41」個別ページの infobox も Protocol
    // version 471（BE-NOTES.md §16）。
    // 471 の 1 つ下は 465（1.17.30）。472〜474 にエントリは無い
    // （474 は Beta 1.18.0.20／1.18.0.21 の番号、472・473 は Wiki 表に
    // エントリ無しで未確認。BE-NOTES.md §16）。
    // 471 と 475 の StartGame の差分は末尾の block_pallete_checksum の
    // 1 フィールドのみ（471 は 61 フィールド、475 は 62 フィールド。
    // hasStartGameBlockPaletteChecksum() を参照）。add_player・
    // add_entity・set_entity_data は 471＝475 で JSON 完全一致。
    // protocol.json の根拠は data/bedrock/1.17.40/。
    V1_17_41(471, "1.17.41"),
    // 475 は 1.18.0 / 1.18.1 / 1.18.2 で共有するプロトコル。
    // displayName は共有するうち最新の 1.18.2（486 の V1_18_12 と同じ流儀）。
    // protocolVersions.json では 475＝1.18.0 のみだが、Wiki「Bedrock Edition
    // 1.18.0」「Bedrock Edition 1.18.1」「Bedrock Edition 1.18.2」個別ページの
    // infobox も Protocol version 475（BE-NOTES.md §15）。
    // 475 の 1 つ下は 471（1.17.40）。472〜474 にエントリは無い。
    // 475 と 486 の StartGame は JSON 完全一致（62 フィールド、seed・
    // dimension ともに zigzag32）。add_player は 21 フィールドで、device_os
    // の型だけ違う（475 は li32、486 は li32 ベースの DeviceOS mapper で
    // ワイヤ同一）。LevelChunk は 486 で highest_subchunk_count の switch
    // が挿入されるが、現行コードの送る形では分岐不要。protocol.json の
    // 根拠は data/bedrock/1.18.0/。
    V1_18_2(475, "1.18.2"),
    // 486 は 1.18.10 / 1.18.11 / 1.18.12 で共有するプロトコル。
    // displayName は共有するうち最新の 1.18.12（503 の V1_18_33 と同じ流儀）。
    // protocolVersions.json では 486＝1.18.11 のみだが、Wiki「Bedrock Edition
    // 1.18.10」「Bedrock Edition 1.18.12」個別ページの infobox も Protocol
    // version 486（BE-NOTES.md §14）。
    // 486 の 1 つ下は 475（1.18.0）。476〜485 にエントリは無い
    // （Wiki 表では Beta 番号のため飛ばす：476＝Beta 1.18.10.21／
    // 1.18.0.23、477＝Beta 1.18.10.22、479＝Beta 1.18.10.24、
    // 485＝Beta 1.18.10.27。478・480〜484 は表にエントリ無しで未確認。
    // BE-NOTES.md §15）。
    // 486 と 503 の StartGame の差分は seed（486 は zigzag32、503 は lu64）
    // と dimension（486 は zigzag32、503 は mapper。ただし overworld＝0 は
    // ワイヤ同一のため分岐不要。hasStartGameLu64Seed() を参照）。add_player
    // は 486 が 21 フィールドで entity_id_self（zigzag64、503 の
    // unique_entity_id と型は同じで名称のみ違う）・gamemode 無し、503 が
    // 22 フィールドで unique_entity_id・gamemode 有り（hasAddPlayerGamemode()
    // を参照）。protocol.json の根拠は data/bedrock/1.18.11/。
    V1_18_12(486, "1.18.12"),
    // 503 は 1.18.30 / 1.18.31 / 1.18.32 / 1.18.33 で共有するプロトコル。
    // displayName は共有するうち最新の 1.18.33（527 の V1_19_2 と同じ流儀）。
    // 503 の 1 つ下は 486（1.18.11）。504〜526 にエントリは無い
    // （512・514・516・524・526 は Preview 1.19.0 の番号、残りは Wiki 表に
    // エントリ無し。BE-NOTES.md §13）。
    // 503 と 527 の StartGame の差分は property_data・world_template_id
    // の有無とチェックサム欄の綴り（503 は block_pallete_checksum（単 l）、
    // 527 は block_pallette_checksum（二重 l）。hasStartGame527Fields() を
    // 参照）。add_player・add_entity・set_entity_data は 503＝527 で
    // JSON 完全一致（旧形式）。protocol.json の根拠は data/bedrock/1.18.30/。
    V1_18_33(503, "1.18.33"),
    // 527 は 1.19.0 / 1.19.1 / 1.19.2 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.19.2（534 の V1_19_11 と同じ流儀）。
    // 527 の 1 つ下は 526（Preview 1.19.0.68）のため飛ばし、次のリリース・
    // プロトコルは 503（1.18.30）。528〜533 にエントリは無い
    // （530・532・533 は Preview 1.19.10.20／.21／.22 の番号、
    // 528・529・531 はスキップされた番号。BE-NOTES.md §12）。
    // 527 と 534 の StartGame の差分は editor_world の 1 フィールドのみ
    // （permission_level の型も違う。hasEditorWorld()・
    // hasPermissionLevelMapper() を参照）。add_player は 527 が旧形式
    // （能力系を直書き、unique_entity_id／runtime_entity_id）、
    // add_entity は 527 に body_yaw が無い
    // （hasAddPlayerAbilitiesArray()・hasAddEntityBodyYaw() を参照）。
    // protocol.json の根拠は data/bedrock/1.19.1/。
    V1_19_2(527, "1.19.2"),
    // 534 は 1.19.10 / 1.19.11 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.19.11（545 の V1_19_22 と同じ流儀）。
    // 534 の 1 つ下は 527（1.19.1）。528〜533 にエントリは無い
    // （530・532・533 は Preview 1.19.10.20／.21／.22 の番号、
    // 528・529・531 はスキップされた番号。BE-NOTES.md §11）。
    // 534 と 544 の StartGame の差分は 5 フィールド
    // （persona_disabled・custom_skins_disabled・chat_restriction_level・
    // disable_player_interactions・client_side_generation は 544 で追加。
    // hasStartGame544Fields() を参照）。protocol.json の根拠は
    // data/bedrock/1.19.10/。
    V1_19_11(534, "1.19.11"),
    // 544 は 1.19.20 のみで共有するプロトコル。Preview 1.19.20.24 も 544。
    // 544 の 1 つ下は 534（1.19.10）。535〜543 にエントリは無い
    // （542・543 は Preview 1.19.20.20／.22／.23 の番号、535〜541 は
    // スキップされた番号。BE-NOTES.md §10）。
    // 544 の protocol.json は 545 のものとバイト同一（10,836 行・
    // 280,171 バイト・SHA-1 60acb22c…）。StartGame・ResourcePacks・
    // Item・Transaction は 545 と JSON 完全一致。add_player・
    // add_entity・set_entity_data に properties（EntityProperties）は
    // 544・545 ともに無い。
    // protocol.json の根拠は data/bedrock/1.19.20/。
    V1_19_20(544, "1.19.20"),
    // 545 は 1.19.21 / 1.19.22 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.19.22（554 の V1_19_31 と同じ流儀）。
    // 545 の 1 つ下は 544（1.19.20）。546〜553 にエントリは無い
    // （546〜550 は Wiki 表にもエントリ無し、551〜553 は Preview
    // 1.19.30.20／.22／.25 の番号。BE-NOTES.md §9）。
    // 545 と 554 の StartGame・ResourcePacks・Item・Transaction は
    // JSON 完全一致。add_player・add_entity・set_entity_data に
    // properties（EntityProperties）は 545・554 ともに無い。
    // protocol.json の根拠は data/bedrock/1.19.21/。
    V1_19_22(545, "1.19.22"),
    // 554 は 1.19.30 / 1.19.31 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.19.31（557 の V1_19_41 と同じ流儀）。
    // 554 の 1 つ下は 545（1.19.21）。554 と 557 の StartGame・
    // ResourcePacks・Item・Transaction は JSON 完全一致。add_player・
    // add_entity・set_entity_data にだけ 557 で追加された properties
    // （EntityProperties）があり、554 では書かない（BE-NOTES.md §8）。
    // protocol.json の根拠は data/bedrock/1.19.30/。
    V1_19_31(554, "1.19.31"),
    // 557 は 1.19.40 / 1.19.41 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.19.41（560 の V1_19_51 と同じ流儀）。
    // 557 の 1 つ下は 554（1.19.30）。555 は Preview 1.19.40.22 の番号、
    // 556 は Bedrock 欄にエントリが無いため飛ばした（BE-NOTES.md §7）。
    // 557 と 560 の StartGame・ResourcePacks・Item・Transaction は
    // JSON 完全一致。protocol.json の根拠は data/bedrock/1.19.40/。
    V1_19_41(557, "1.19.41"),
    // 560 は 1.19.50 / 1.19.51 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.19.51（582 の V1_19_83 と同じ流儀）。
    // 560 の 1 つ下は 557（1.19.40）。561〜566 はベータ／プレビュー版の
    // 番号（561 は Preview 1.19.60.20）のため飛ばした（BE-NOTES.md §6）。
    // 560 と 567 の StartGame の差分は emote_chat_muted の 1 フィールドのみ
    // （Java コードは 567 以降でこのフィールドを書く。534 回で
    // hasEmoteChatMuted() を追加し、560 までは書かないようにした。
    // それ以前は無条件に書いていたため、544〜560 の実ワイヤは 1 バイト
    // 多かった。BE-NOTES.md §11 参照）。ResourcePacks・Item・
    // Transaction は JSON 完全一致。protocol.json の根拠は data/bedrock/1.19.50/。
    V1_19_51(560, "1.19.51"),
    // 567 は 1.19.60 / 1.19.62 で共有するプロトコル。displayName は
    // 共有するうち最新の 1.19.62（582 の V1_19_83 と同じ流儀）。
    // 567 の 1 つ下は 568（1.19.63）だが、minecraft-data に
    // 1.19.63 の protocol.json は存在しないため飛ばした（BE-NOTES.md §5）。
    // 567 と 575 の StartGame・ResourcePacks・Item・Transaction は
    // JSON 完全一致。protocol.json の根拠は data/bedrock/1.19.62/。
    V1_19_62(567, "1.19.62"),
    // 575 は 1.19.70（2023-03-14）。582 との StartGame の差分は
    // created_in_editor／exported_from_editor／
    // block_network_ids_are_hashes の 3 フィールドのみ（BE-NOTES.md §4）。
    // 575 の 1 つ下は 568（1.19.63）だが、minecraft-data に
    // 1.19.63 の protocol.json は存在しない（version.json のみ）。
    // 下側の比較は 567（1.19.62）で代用した。
    V1_19_70(575, "1.19.70"),
    // 582 は 1.19.80 / 1.19.81 / 1.19.83 の 3 バージョンで共有する
    // プロトコル。Login が運ぶのはプロトコル番号だけで、クライアントの
    // マイナーバージョンは区別できないため、fromProtocol(582) は
    // 最初に一致した V1_19_83 を返す。582 の基準 protocol.json は
    // minecraft-data の data/bedrock/1.19.80/protocol.json
    // （1.19.83 のディレクトリは存在しない）。
    V1_19_83(582, "1.19.83"),
    V1_20_0(589, "1.20.0"),
    V1_20_10(594, "1.20.10"),
    V1_20_30(618, "1.20.30"),
    V1_20_40(622, "1.20.40"),
    V1_20_50(630, "1.20.50"),
    V1_20_60(649, "1.20.60"),
    V1_20_70(662, "1.20.70"),
    V1_20_80(671, "1.20.80"),
    V1_21_0(685, "1.21.0"),
    V1_21_2(686, "1.21.2"),
    V1_21_20(712, "1.21.20"),
    V1_21_30(729, "1.21.30"),
    V1_21_40(748, "1.21.40"),
    V1_21_50(766, "1.21.50"),
    V1_21_60(776, "1.21.60"),
    V1_21_70(786, "1.21.70"),
    V1_21_80(800, "1.21.80"),
    V1_21_90(818, "1.21.90"),
    V1_21_93(819, "1.21.93"),
    V1_21_100(827, "1.21.100"),
    V1_21_111(844, "1.21.111"),
    V1_21_120(859, "1.21.120"),
    // 860 は 1.21.124。1.21.120（859）の protocol.json とバイト同一の
    // ホットフィックス（2025-11-21。コンテンツ追加なし）だが、プロトコル
    // 番号が変わるため enum には別エントリとして載せる。
    V1_21_124(860, "1.21.124"),
    V1_21_130(898, "1.21.130"),
    V1_26_0(924, "1.26.0"),
    V1_26_10(944, "1.26.10"),
    V1_26_20(975, "1.26.20"),
    V1_26_30(1001, "1.26.30"),
    V1_26_40(2168, "1.26.40"),
    V1_26_45(2169, "1.26.45"),
    // 2193 は 1.26.50 / 1.26.51 / 1.26.52 の 3 バージョンで共有する
    // プロトコル。Login が運ぶのはプロトコル番号だけで、クライアントの
    // マイナーバージョンは区別できないため、fromProtocol(2193) は
    // 最初に一致した V1_26_50 を返す（下記の fromProtocol を参照）。
    // 述語はすべてプロトコル番号で判定するため、3 バージョンの
    // ワイヤ形式は同一で、V1_26_52 は V1_26_50 と同じ判定になる。
    // アイテム対応表も 1.26.30 のものを共用する（itemTableTag を参照）。
    V1_26_50(2193, "1.26.50"),
    V1_26_52(2193, "1.26.52"),
    // 2225 は 1.26.60 / 1.26.62 / 1.26.63 / 1.26.65 の 4 バージョンで
    // 共有するプロトコル（protocolVersions.json では 2225＝1.26.60 の
    // 1 件のみ。Wiki「Bedrock Edition 26.60」「Bedrock Edition 26.62」
    // 「Bedrock Edition 26.63」「Bedrock Edition 26.65」個別ページの
    // infobox もすべて Protocol version 2225。26.61／26.64 の個別ページは
    // 存在しない。BE-NOTES.md §24）。2193 と同じく、fromProtocol(2225) は
    // 最初に一致した V1_26_60 を返す。
    // 2193 との差分は追加 8 件（353 clientbound_matchmaking_state・
    // 354／355 stonecutter_set_recipe・356 serverbound_matchmaking_cancel・
    // 357 set_passenger_of_block・358 serverbound_cursor_item_drag・
    // 359／360 audio_content）・削除 0・リネーム 0・型変更 23 件。
    // StartGame は 83 フィールドで editor_level_migration_version
    // （u8 mapper）が exported_from_editor の直後に挿入される
    // （hasEditorLevelMigrationVersion() を参照）。add_player は
    // passenger_of_block（option）が device_id の前に、add_entity は
    // 末尾に挿入される（hasPassengerOfBlock() を参照）。level_chunk は
    // 末尾に client_biome_update（bool）が付く
    // （hasClientBiomeUpdate() を参照）。InventoryTransaction の
    // item_use_on_entity は hotbar_slot と held_item の間に hand
    // （u8 mapper）が挿入される（transactionHasHand() を参照）。
    // 核パケットのうち login・play_status・resource_packs_info・
    // resource_pack_stack・resource_pack_client_response・text・
    // set_entity_data・move_player・player_auth_input は 2193 と
    // JSON 完全一致。Skin は play_fab_id が消え、PlayerRecord の add 側へ
    // 移ったが、writeEmptySkin の Skin 形式の分岐は判断待ちのため
    // 2225 でも従来の書き方のまま（BE-NOTES.md §18.7／§24）。
    // アイテム表は 2193 と同じ bedrock_item_ids_1_26_30.bin を共用する
    // （itemTableTag を参照。PMMP BedrockData に bedrock-1.26.40 以降の
    // タグは無く、1.26.60 自体の実測ではない）。
    // トランスポートは NetherNet 世代で RakNet では繋がらない。パケット
    // 形式はこの enum で対応するが、BedrockSession は RakNet 経由の
    // 2225 ログインを引き続き切断する（NetherNet リスナーは未実装。
    // BE-NOTES.md §24）。protocol.json の根拠は data/bedrock/1.26.60/。
    V1_26_60(2225, "1.26.60"),
    V1_26_65(2225, "1.26.65");

    /** NetherNet 世代のプロトコル番号（26.60 / 26.62 / 26.63 / 26.65）。RakNet では繋がらない。 */
    public static final int NETHERNET_PROTOCOL = 2225;

    private final int protocol;
    private final String displayName;

    BedrockVersion(int protocol, String displayName) {
        this.protocol = protocol;
        this.displayName = displayName;
    }

    /** プロトコル番号。 */
    public int protocol() {
        return protocol;
    }

    /** 最初にそのプロトコルを使った Bedrock のバージョン名。 */
    public String displayName() {
        return displayName;
    }

    /**
     * プロトコル番号からバージョンを引く。対応外なら null。
     * 同じプロトコルを共有するバージョンがある場合（2193＝1.26.50 /
     * 1.26.51 / 1.26.52）は、最初に宣言されたもの（V1_26_50）を返す。
     */
    public static BedrockVersion fromProtocol(int protocol) {
        for (BedrockVersion v : values()) {
            if (v.protocol == protocol) {
                return v;
            }
        }
        return null;
    }

    /** 対応しているプロトコル番号か。 */
    public static boolean isSupportedProtocol(int protocol) {
        return fromProtocol(protocol) != null;
    }

    /** NetherNet 世代（26.60 / 26.62 / 26.63 / 26.65）のプロトコルか。 */
    public static boolean isNetherNetProtocol(int protocol) {
        return protocol == NETHERNET_PROTOCOL;
    }

    // ---- StartGame の形式 ----

    /** hardcore フィールドがある（1.20.80 以降）。 */
    public boolean hasHardcore() {
        return protocol >= 671;
    }

    /**
     * level_id の前に server/world/scenario の識別子文字列がある
     * （1.21.0〜1.21.80）。1.21.90 以降は owner_identifier が加わる。
     * 注意：この述語は「識別子 3 つだけの形式」を意味する。1.21.90 以降は
     * server/world/scenario の 3 つも level_id の前にあるが、owner を含む
     * 4 識別子形式になるためこの述語は false で、代わりに
     * {@link #hasOwnerIdentifier()} が true になる（BedrockPackets の
     * if/else 分岐を参照）。859 / 860 の protocol.json がバイト同一で
     * 4 識別子とも level_id の前にあることと矛盾しない。
     */
    public boolean hasPreLevelIdentifiers() {
        return protocol >= 685 && protocol <= 800;
    }

    /** level_id の前に owner_identifier もある（1.21.90〜1.21.130）。 */
    public boolean hasOwnerIdentifier() {
        return protocol >= 818 && protocol <= 898;
    }

    /**
     * movement_authority フィールドがある（1.13.0 以降、1.21.80 まで）。
     * 361 の StartGame（StartGameSerializer_v361）にはこのフィールド
     * 自体が無く、StartGameSerializer_v388 が新規に追加した
     * （BE-NOTES.md §31）。
     */
    public boolean hasMovementAuthority() {
        return protocol >= 388 && protocol <= 800;
    }

    /**
     * StartGame に rewind_history_size（zigzag32）と
     * server_authoritative_block_breaking（bool）がある（1.16.210 以降）。
     * 422（1.16.201）の StartGame は 57 フィールドでこの 2 フィールドが無く、
     * movement_authority の直後が current_tick。428 は 59 フィールドで
     * movement_authority の直後にこの 2 フィールドがある（BE-NOTES.md §22）。
     * movement_authority の値 2 の名称は 422 が server_v2_rewind・428 が
     * server_with_rewind だが、コードは 0（client）を書くため分岐不要。
     */
    public boolean hasStartGameRewindFields() {
        return protocol >= 428;
    }

    /**
     * StartGame の LevelSettings に experiments（配列）と
     * experiments_previously_toggled（bool）がある（1.16.100 以降）。
     * 408 の実効シリアライザ（CloudburstMC Protocol の
     * StartGameSerializer_v407）にはこの 2 フィールドが無く、gamerules の
     * 直後が bonus_chest。v419 で StartGameSerializer_v419 が挿入した
     * （BE-NOTES.md §26）。
     */
    public boolean hasStartGameExperiments() {
        return protocol >= 419;
    }

    /**
     * StartGame のブロックパレットが blockProperties 形式（件数＋
     * name／states の配列）である（1.16.100 以降）。408 は旧形式で、
     * パレット全体を単一の NBT リストタグ（TAG_List of TAG_Compound）
     * として送る（CloudburstMC Protocol の StartGameSerializer_v388 が
     * helper.writeTag で書く形式。BE-NOTES.md §26）。
     */
    public boolean hasStartGameBlockProperties() {
        return protocol >= 419;
    }

    /**
     * StartGame のブロックパレットがレガシー配列形式（件数 VarInt＋
     * 要素ごとの並び）である（1.12.1 以下）。361 の実効シリアライザ
     * （CloudburstMC Protocol の StartGameSerializer_v361）がこの形式で、
     * 388 で単一 NBT タグ形式に変わった（BE-NOTES.md §31）。
     * 要素の並びは 361 が name String／meta ShortLE／id ShortLE、
     * 354 が name String／meta ShortLE のみ
     * （{@link #hasStartGameLegacyPaletteEntryId()} を参照）。
     * パレットの中身は {@link BedrockLegacyBlockPalette} を参照。
     */
    public boolean hasStartGameLegacyPaletteArray() {
        return protocol <= 361;
    }

    /**
     * StartGame のレガシー配列形式パレットの要素に id（ShortLE）が
     * ある（361 のみ）。354 の実効シリアライザ
     * （CloudburstMC Protocol の StartGameSerializer_v291 がトップ
     * レベルを担う）は要素を name String／meta ShortLE のみで書き、
     * StartGameSerializer_v361 が id を追加した（BE-NOTES.md §32）。
     * この述語は {@link #hasStartGameLegacyPaletteArray()} が真の
     * 範囲でのみ意味を持つ。
     */
    public boolean hasStartGameLegacyPaletteEntryId() {
        return protocol == 361;
    }

    /**
     * StartGame の LevelSettings に onlySpawningV1Villagers
     * （only spawn v1 villagers）がある（1.12.0 以降）。354 の実効
     * シリアライザ（CloudburstMC Protocol の
     * StartGameSerializer_v332）の LevelSettings は 30 フィールドで
     * この項目が無く、StartGameSerializer_v361 が 31 フィールド目
     * として追加した（BE-NOTES.md §32）。
     */
    public boolean hasStartGameOnlySpawningV1Villagers() {
        return protocol >= 361;
    }

    /**
     * LevelChunk の先頭に subChunkCount（VarInt）とキャッシュ可否
     * （Boolean）がある（1.12.0 以降）。354 の実効シリアライザ
     * （CloudburstMC Protocol の FullChunkDataSerializer_v291）は
     * chunkX／chunkZ（zigzag32）と payload のみで、
     * FullChunkDataSerializer_v361 がこの 2 つを追加した
     * （BE-NOTES.md §32）。
     */
    public boolean hasLevelChunkSubChunkCount() {
        return protocol >= 361;
    }

    /**
     * StartGame の v407 追加分がある（1.16.0 以降）。390 の実効
     * シリアライザ（CloudburstMC Protocol の StartGameSerializer_v388）に
     * は次のフィールドが無く、StartGameSerializer_v407 が追加した：
     * LevelSettings の seed 直後の spawn_biome_type（li16）と
     * custom_biome_name（string）、edu_features_enabled 直後の
     * education_production_id（string）、末尾の limited_world_width
     * （li32）・limited_world_height（li32）・is_new_nether（bool）・
     * force_experimental_gameplay、およびトップレベル末尾の
     * server_authoritative_inventory（bool）。この実装が書く
     * experimental gameplay override は force_experimental_gameplay の
     * 「未指定」に当たる 1 バイトで、同じゲートで出し分ける。
     * edu_edition_offers は 390 が Boolean・407 が VarInt と型が違うが、
     * 書く値 0 はどちらも 0x00 でバイト同一のため、この述語では
     * 分岐しない（BedrockPackets.encodeStartGame を参照。
     * BE-NOTES.md §28）。
     */
    public boolean hasStartGame407Fields() {
        return protocol >= 407;
    }

    /**
     * StartGame の LevelSettings 末尾に vanillaVersion（gameVersion）
     * 文字列がある（1.13.0 以降）。361 の LevelSettings は 31 フィールドで
     * onlySpawningV1Villagers が末尾であり、StartGameSerializer_v388 が
     * 32 フィールド目として末尾に追加した（BE-NOTES.md §31）。
     */
    public boolean hasStartGameVanillaVersion() {
        return protocol >= 388;
    }

    /**
     * StartGame の LevelSettings が _v291 のレイアウトである
     * （313 のみ）。313 の実効シリアライザ
     * （CloudburstMC Protocol の StartGameSerializer_v313）は
     * StartGameSerializer_v291 の writeLevelSettings
     * （31 フィールド）に fromWorldTemplate／
     * fromLockedWorldTemplate の 2 bool を追加した 33 フィールドで、
     * 332 の StartGameSerializer_v332（30 フィールド）とは
     * confirmedPlatformLockedContent の位置、放送モード 3 つ
     * （xblBroadcast／platformBroadcast／xblIntent。値が
     * NO_MULTI_PLAY のとき false になる bool と zigzag32 の組）の
     * 位置、trustingPlayers の有無が違う。この実装の値では
     * 313 の LevelSettings 末尾寄りの区間は 332 より 3 バイト
     * 長くなる（BE-NOTES.md §35）。
     */
    public boolean startGameUsesV291LevelSettings() {
        return protocol <= 313;
    }

    /**
     * StartGame の LevelSettings が _v313 のレイアウトである
     * （313 のみ）。_v291 の 31 フィールドに fromWorldTemplate／
     * fromLockedWorldTemplate の 2 bool を追加した 33 フィールドで、
     * 291 は _v291 の純粋な 31 フィールドになる
     * （StartGameSerializer_v291。BE-NOTES.md §36）。
     */
    public boolean startGameUsesV313LevelSettings() {
        return protocol == 313;
    }

    /**
     * ResourcePacksInfo に scriptingEnabled（bool）とエントリごとの
     * scripting（bool）がある（332 以降）。313 の実効シリアライザ
     * （CloudburstMC Protocol の ResourcePacksInfoSerializer_v291）
     * にはどちらも無く、ResourcePacksInfoSerializer_v332 が追加した。
     * この実装が書く空の一覧では 313 が 332 より 1 バイト短くなる
     * （BE-NOTES.md §35）。
     */
    public boolean resourcePacksInfoHasScripting() {
        return protocol >= 332;
    }

    /**
     * ResourcePackStack に experiments 配列と experiments_previously_toggled
     * がある（1.16.100 以降）。408 は旧形式で、gameVersion の前に
     * legacy experiment フラグ（bool）が 1 つあるだけ
     * （ResourcePackStackSerializer_v313／_v388。BE-NOTES.md §26）。
     */
    public boolean hasResourcePackStackExperiments() {
        return protocol >= 419;
    }

    /**
     * ResourcePackStack の末尾に gameVersion 文字列がある（1.13.0 以降）。
     * 361 の実効シリアライザ（CloudburstMC Protocol の
     * ResourcePackStackSerializer_v313）は legacy experiment フラグが
     * 末尾で、ResourcePackStackSerializer_v388 が gameVersion を末尾に
     * 追加した（BE-NOTES.md §31）。
     */
    public boolean resourcePackStackHasGameVersion() {
        return protocol >= 388;
    }

    /**
     * ResourcePackStack が _v291 の形式である（291 のみ）。
     * ResourcePackStackSerializer_v291 は forcedToAccept と
     * behavior／resource の 2 配列だけで、末尾の legacy experiment
     * フラグ（bool）は ResourcePackStackSerializer_v313 が
     * 追加した（BE-NOTES.md §36）。
     */
    public boolean resourcePackStackUsesV291Format() {
        return protocol <= 291;
    }

    /**
     * MovePlayer の末尾に tick（varint）がある（1.16.100 以降）。
     * MovePlayerSerializer_v419 が v291 に末尾追加した（BE-NOTES.md §26）。
     */
    public boolean hasMovePlayerTick() {
        return protocol >= 419;
    }

    /**
     * SetEntityData の末尾に tick（varint）がある（1.16.100 以降）。
     * SetEntityDataSerializer_v419 が v291 に末尾追加した（BE-NOTES.md §26）。
     */
    public boolean hasSetEntityDataTick() {
        return protocol >= 419;
    }

    /**
     * UpdateAttributes の末尾に tick（varint）がある（1.16.100 以降）。
     * UpdateAttributesSerializer_v419 が v291 に末尾追加した（BE-NOTES.md §26）。
     */
    public boolean hasUpdateAttributesTick() {
        return protocol >= 419;
    }

    /**
     * MoveEntityDelta の位置が FloatLE の絶対座標である（1.16.100 以降）。
     * 408 は VarInt の差分値（delta）形式だが、差分のスケールが公開された
     * 実測ソースで確認できないため、408 ではセッションが絶対座標の
     * MoveEntity で代替する（MoveEntityDeltaSerializer_v291／_v419、
     * BE-NOTES.md §26）。
     */
    public boolean moveEntityDeltaUsesAbsolutePosition() {
        return protocol >= 419;
    }

    /**
     * itemstates フィールドがある（1.12.0〜1.21.40）。下限の 361 は
     * §32 で追加した：354 の実効シリアライザ（CloudburstMC Protocol の
     * StartGameSerializer_v291 がトップレベルを担う）には
     * itemDefinitions 自体が無く、StartGameSerializer_v361 が追加した
     * （BE-NOTES.md §32）。
     */
    public boolean hasItemstates() {
        return protocol >= 361 && protocol <= 748;
    }

    /** tick_death_systems フィールドがある（1.21.100〜1.21.124 のみ）。 */
    public boolean hasTickDeathSystems() {
        return protocol >= 827 && protocol <= 860;
    }

    /**
     * 末尾に has_server_join_info・server_join_info・識別子 4 つがある
     * （26.0 以降）。1.21.130 までは識別子は level_id の前にある。
     */
    public boolean hasJoinInfoTail() {
        return protocol >= 924;
    }

    /** server_editor_connection_policy 等の編集者向け項目がある（26.30 以降）。 */
    public boolean hasEditorPolicy() {
        return protocol >= 1001;
    }

    /** is_chat_logging フィールドがある（26.30 のみ。26.40 で消えた）。 */
    public boolean hasChatLogging() {
        return protocol == 1001;
    }

    /** StartGame に editor_world がある（1.19.10 以降）。527 には無い。 */
    public boolean hasEditorWorld() {
        return protocol >= 534;
    }

    /**
     * StartGame の seed が lu64 である（1.18.30 以降）。486 は zigzag32。
     * dimension は 486 が zigzag32、503 が mapper だが、overworld＝0 は
     * ワイヤ同一のため分岐しない（BedrockPackets.encodeStartGame を参照）。
     */
    public boolean hasStartGameLu64Seed() {
        return protocol >= 503;
    }

    /**
     * StartGame に block_pallete_checksum（lu64）がある（1.18.0 以降）。
     * 471 には無い（61 フィールド、末尾は engine）。475 は 62 フィールドで
     * 末尾が block_pallete_checksum（単 l）。527 は block_pallette_checksum
     * （二重 l）だが型・位置は同じ（BedrockPackets.encodeStartGame を参照）。
     */
    public boolean hasStartGameBlockPaletteChecksum() {
        return protocol >= 475;
    }

    /**
     * StartGame に property_data・world_template_id がある（1.19.0 以降）。
     * 503 には無く、チェックサム欄は block_pallete_checksum（単 l）。
     * 527 は property_data（nbt）・block_pallette_checksum（lu64、二重 l）・
     * world_template_id（uuid）。チェックサム自体（lu64）は 503・527 ともに
     * あるため、この述語は property_data と world_template_id の書き込み
     * だけを分岐させる（BedrockPackets.encodeStartGame を参照）。
     */
    public boolean hasStartGame527Fields() {
        return protocol >= 527;
    }

    /**
     * StartGame に engine（string）がある（1.17.0 以降）。431 には無い
     * （431 の packet_start_game は 59 フィールドで末尾が
     * server_authoritative_inventory、440 は 60 フィールドで末尾が
     * engine。440〜2193 の protocol.json にはすべて engine があることを
     * 実ファイルで確認済み。BedrockPackets.encodeStartGame を参照）。
     */
    public boolean hasStartGameEngine() {
        return protocol >= 440;
    }

    /**
     * MetadataDictionary の key 123 が goat_horn_count である（1.17.0 以降）。
     * 431 では key 123 は base_runtime_id（431 の辞書は 125 エントリで
     * 120=freezing_effect_strength・121=buoyancy_data・122=goat_horn_count・
     * 123=base_runtime_id・124=define_properties・125=update_properties、
     * 440 の辞書は 124 エントリで 120=base_runtime_id・
     * 121=freezing_effect_strength・122=buoyancy_data・123=goat_horn_count・
     * 124=update_properties。BedrockEntityTranslator.toBedrockData を参照）。
     */
    public boolean hasGoatHornCountKey() {
        return protocol >= 440;
    }

    /**
     * AddPainting の coordinates が BlockCoordinates である（422・428 で確認）。
     * 431 以降は vec3f（lf32 × 3）。BlockCoordinates は x zigzag32・y varint・
     * z zigzag32 で、vec3f とはワイヤ形式が違う。422・428 の packet_add_painting は
     * 5 フィールドで、coordinates 以外は同じ（順序も同じ）。
     * 422（1.16.201）も BlockCoordinates であることを protocol.json で確認済み
     * （BE-NOTES.md §22）。下限の 422 は §28 で追加した：390〜419 の実効
     * シリアライザ（CloudburstMC Protocol の AddPaintingSerializer_v361、
     * v419 まで未上書き）は coordinates を vec3f で書くため、コーデックが
     * 根拠のバージョン（390・407・408・419）は vec3f 側に倒す。422・428 に
     * ついては protocol.json と Cloudburst コーデックで記述が食い違うが、
     * protocol.json の直接測定（§21・§22）を優先して従来どおり
     * BlockCoordinates とする（出典の食い違いは判断待ち。BE-NOTES.md §28）。
     * 354 側は §32 で追加した：354 の実効シリアライザ
     * （CloudburstMC Protocol の AddPaintingSerializer_v332）も
     * coordinates を BlockPosition で書き、vec3f 化は
     * AddPaintingSerializer_v361 の変更である。したがって 354 は
     * ブロック座標側、355〜419 は vec3f 側になる（BE-NOTES.md §32）。
     * BedrockPackets.encodeAddPainting を参照。
     */
    public boolean addPaintingUsesBlockCoordinates() {
        return protocol <= 354 || (protocol >= 422 && protocol <= 428);
    }

    /**
     * AddEntity の第 3 フィールドが identifier 文字列ではなく旧来の
     * 数値タイプ ID（VarUnsignedInt）である（291 のみ）。
     * AddEntitySerializer_v291 が数値で書き、v313 が identifier
     * 文字列に変えた（BE-NOTES.md §36）。数値の対応表は
     * BedrockLegacyEntityTypeIds。
     */
    public boolean addEntityUsesLegacyTypeId() {
        return protocol <= 291;
    }

    /**
     * NetworkChunkPublisherUpdate（121）が存在する（313 以降）。
     * CloudburstMC Protocol では v313 で新規登録されたパケットで、
     * v291 の登録 116 件（最大 ID 117）には無い。291 には送らない
     * （BE-NOTES.md §36）。対応範囲に 292〜312 のプロトコルは
     * 無いため、291 より大きいことは 313 以降と同値である。
     */
    public boolean hasNetworkChunkPublisherUpdate() {
        return protocol > 291;
    }

    /**
     * PlayerList（PlayerRecords）の verified が remove でも存在する（422・428 で確認）。
     * 431 以降は verified が type による switch で、add のときのみ存在し
     * remove のときは無い。422・428 は add・remove ともに records_count 個の
     * bool 配列。422 の PlayerRecords も 428 と JSON 完全一致であることを
     * protocol.json で確認済み（BE-NOTES.md §22）。
     * 下限の 422 は §28 で追加した：390〜419 の実効シリアライザ
     * （CloudburstMC Protocol の PlayerListSerializer_v390、v419 まで
     * 未上書き）は add の末尾にだけ trustedSkin の bool ブロックを持ち、
     * remove は uuid のみのため、コーデックが根拠のバージョン
     * （390・407・408・419）は verified を書かない。§25〜§27 では
     * protocol <= 428 の分岐で 407〜419 も書いていたが、v390 コーデックの
     * 実読に基づき §28 で改めた。422・428 については protocol.json と
     * Cloudburst コーデックで記述が食い違うが、protocol.json の直接測定を
     * 優先して従来どおりとする（出典の食い違いは判断待ち。
     * BE-NOTES.md §28）。
     * BedrockPackets.encodePlayerListRemove を参照。
     */
    public boolean playerListRemoveHasVerified() {
        return protocol >= 422 && protocol <= 428;
    }

    /**
     * PlayerList の add に verified（trustedSkin）の末尾ブロックがある
     * （1.14.60 以降）。390 の PlayerListSerializer_v390 が、本体を全
     * エントリ分書いた後に trustedSkin の bool をエントリ数分まとめて
     * 書く形を導入した。389 の実効シリアライザ
     * （PlayerListSerializer_v388）は本体のみで trustedSkin 自体が
     * 無いため、389 向けに verified を書くと 1 バイト余る。
     * この実装は add を 1 件だけ送るため、本体の直後に verified を
     * 書く形が 390 以降では v390 の分離ブロックとバイト同一になる
     * （BE-NOTES.md §29。BedrockPackets.encodePlayerListAdd を参照）。
     */
    public boolean playerListAddHasVerified() {
        return protocol >= 390;
    }

    /**
     * PlayerList の add エントリが旧直書き形式である（1.12.1 以下）。
     * 361 の実効シリアライザ（CloudburstMC Protocol の
     * PlayerListSerializer_v291）は、uuid／entityId／name の後に
     * skinId（String）／skinData（ByteArray）／capeData（ByteArray）／
     * geometryName（String）／geometryData（String）をエントリ内に
     * 埋め込み、その後に xuid／platformChatId が続く。388 で
     * PlayerListSerializer_v388 が xuid／platformChatId／buildPlatform／
     * ヘルパー Skin／teacher／host の形式に変えた（BE-NOTES.md §31。
     * BedrockPackets.encodePlayerListAdd を参照）。
     */
    public boolean playerListUsesLegacyEntry() {
        return protocol <= 361;
    }

    /**
     * StartGame の permission_level が PermissionLevel 型（u8 mapper）
     * である（1.19.10 以降）。527 は zigzag32。
     */
    public boolean hasPermissionLevelMapper() {
        return protocol >= 534;
    }

    /**
     * StartGame の 544 追加分（persona_disabled・custom_skins_disabled・
     * chat_restriction_level・disable_player_interactions・
     * client_side_generation）がある（1.19.20 以降）。534 には無い。
     */
    public boolean hasStartGame544Fields() {
        return protocol >= 544;
    }

    /** StartGame に edu_resource_uri がある（1.17.30 以降）。448 には無い。 */
    public boolean hasStartGameEduResourceUri() {
        return protocol >= 465;
    }

    /** StartGame に emote_chat_muted がある（1.19.60 以降）。560 までは無い。 */
    public boolean hasEmoteChatMuted() {
        return protocol >= 567;
    }

    /**
     * StartGame の 582 追加分（created_in_editor・exported_from_editor・
     * block_network_ids_are_hashes）がある（1.19.80 以降）。575 までは無い。
     */
    public boolean hasStartGame582Fields() {
        return protocol >= 582;
    }

    /** StartGame に server_controlled_sound がある（1.20.0 以降）。582 までは無い。 */
    public boolean hasServerControlledSound() {
        return protocol >= 589;
    }

    /**
     * StartGame に editor_level_migration_version（u8 mapper。0＝legacy、
     * 1＝editor_achievements）がある（26.60 以降）。2193 には無い。
     * 位置は exported_from_editor の直後・day_cycle_stop_time の前
     * （2225 の packet_start_game は 83 フィールドで 18 番目。
     * BE-NOTES.md §24。BedrockPackets.encodeStartGame を参照）。
     */
    public boolean hasEditorLevelMigrationVersion() {
        return protocol >= 2225;
    }

    // ---- Resource Packs Info の形式 ----

    /** has_addons フィールドがある（1.20.70 以降）。 */
    public boolean hasAddons() {
        return protocol >= 662;
    }

    /** force_server_packs フィールドがある（1.17.10〜1.21.20）。440 には無い（448 で新規）。 */
    public boolean hasForceServerPacks() {
        return protocol >= 448 && protocol <= 712;
    }

    /** behaviour_packs の一覧がある（1.21.20 まで）。 */
    public boolean hasBehaviourPacks() {
        return protocol <= 712;
    }

    /** resource_pack_links の一覧がある（1.20.30〜1.21.30）。 */
    public boolean hasResourcePackLinks() {
        return protocol >= 618 && protocol <= 729;
    }

    /** world_template（UUID＋バージョン）がある（1.21.50 以降）。 */
    public boolean hasWorldTemplate() {
        return protocol >= 766;
    }

    /** disable_vibrant_visuals フィールドがある（1.21.90 以降）。 */
    public boolean hasDisableVibrantVisuals() {
        return protocol >= 818;
    }

    /** texture_packs の件数が varint（26.40 以降）。それ以前は li16。 */
    public boolean resourcePacksCountIsVarint() {
        return protocol >= 2168;
    }

    // ---- Resource Pack Stack の形式 ----

    /** behavior_packs の一覧がある（1.21.124 まで）。 */
    public boolean hasBehaviorPacksInStack() {
        return protocol <= 860;
    }

    /** has_editor_packs フィールドがある（1.20.70 以降）。 */
    public boolean hasEditorPacksInStack() {
        return protocol >= 662;
    }

    // ---- Item の形式（各パケットごと） ----

    /** MobEquipment のアイテムが ItemV4（26.20 以降）。 */
    public boolean mobEquipmentUsesItemV4() {
        return protocol >= 975;
    }

    /** MobArmorEquipment のアイテムが ItemV4（26.30 以降）。 */
    public boolean mobArmorUsesItemV4() {
        return protocol >= 1001;
    }

    /** AddItemEntity・AddPlayer のアイテムが ItemV4（26.40 以降）。 */
    public boolean addItemUsesItemV4() {
        return protocol >= 2168;
    }

    /** MobArmorEquipment に body（5 個目）がある（1.21.20 以降）。 */
    public boolean mobArmorHasBodySlot() {
        return protocol >= 712;
    }

    /** AddPlayer・AddEntity・SetEntityData に properties（EntityProperties）がある（1.19.40 以降）。 */
    public boolean hasEntityProperties() {
        return protocol >= 557;
    }

    /**
     * AddPlayer に gamemode がある（1.18.30 以降）。486 には無い。
     * 486 の entity_id_self は 503 の unique_entity_id と型（zigzag64）が
     * 同じで名称のみ違うため分岐しない（BedrockPackets.encodeAddPlayer を参照）。
     */
    public boolean hasAddPlayerGamemode() {
        return protocol >= 503;
    }

    /**
     * AddPlayer の末尾に buildPlatform（IntLE）がある（1.13.0 以降）。
     * 361 の実効シリアライザ（CloudburstMC Protocol の
     * AddPlayerSerializer_v291）は deviceId が末尾で、
     * AddPlayerSerializer_v388 が buildPlatform を末尾に追加した
     * （BE-NOTES.md §31）。
     */
    public boolean addPlayerHasBuildPlatform() {
        return protocol >= 388;
    }

    /**
     * AddPlayer に abilities（AbilityLayers 配列）がある（1.19.10 以降）。
     * 527 は能力系を直書きする旧形式（flags・command_permission・
     * action_permissions・permission_level・custom_stored_permissions・
     * user_id）で、unique_entity_id／runtime_entity_id を使う。
     */
    public boolean hasAddPlayerAbilitiesArray() {
        return protocol >= 534;
    }

    /** AddEntity に body_yaw がある（1.19.10 以降）。527 には無い。 */
    public boolean hasAddEntityBodyYaw() {
        return protocol >= 534;
    }

    /**
     * AddPlayer・AddEntity に passenger_of_block（option の
     * PassengerOfBlockData）がある（26.60 以降）。2193 には無い。
     * AddPlayer では links の直後・device_id の前、AddEntity では末尾
     * （links の直後）。この実装はブロック乗車を送らないため常に
     * 「なし」（option の false）で書く（BE-NOTES.md §24。
     * BedrockPackets.encodeAddPlayer／encodeAddEntity を参照）。
     */
    public boolean hasPassengerOfBlock() {
        return protocol >= 2225;
    }

    // ---- UpdateAttributes / NetworkChunkPublisherUpdate の形式 ----

    /** UpdateAttributes の属性に modifiers 配列がある（1.19.20 以降）。534 には無い。 */
    public boolean hasAttributeModifiers() {
        return protocol >= 544;
    }

    /** NetworkChunkPublisherUpdate に saved_chunks 配列がある（1.19.20 以降）。534 には無い。 */
    public boolean hasSavedChunks() {
        return protocol >= 544;
    }

    /**
     * LevelChunk の末尾に client_biome_update（bool）がある（26.60 以降）。
     * 2193 には無い（2225 の packet_level_chunk は 9 フィールドで末尾。
     * BE-NOTES.md §24。BedrockPackets.encodeLevelChunk を参照）。
     */
    public boolean hasClientBiomeUpdate() {
        return protocol >= 2225;
    }

    // ---- InventoryTransaction（クライアント→サーバー）の形式 ----

    /**
     * 取引の先頭に legacy_request_id（zigzag32）と legacy_transactions が
     * ある（1.16.0 以降）。390 の実効シリアライザ（CloudburstMC Protocol の
     * InventoryTransactionSerializer_v291）にはどちらも無く、パケットは
     * transaction_type から始まる。legacy_request_id は
     * InventoryTransactionSerializer_v407 が追加した。390 のアクション列も
     * v291 世代の基本形式で、先行する network_ids（bool）や
     * stack_network_id は無い（Base ヘルパーの readInventoryActions。
     * BE-NOTES.md §28。BedrockPackets.decodeInventoryTransaction を参照）。
     */
    public boolean transactionHasLegacyRequestId() {
        return protocol >= 407;
    }

    /** 取引の legacy_transactions が option 形式（26.30 以降）。 */
    public boolean transactionLegacyIsOption() {
        return protocol >= 1001;
    }

    /** 取引のアイテムが ItemV4（26.30 以降）。 */
    public boolean transactionUsesItemV4() {
        return protocol >= 1001;
    }

    /** transaction_type・actions が option 形式（26.40 以降）。 */
    public boolean transactionFieldsAreOptions() {
        return protocol >= 2168;
    }

    /**
     * 取引の item_use_on_entity・item_release に hand（u8 mapper。
     * 0＝main_hand、1＝off_hand）がある（26.60 以降）。2193 には無い。
     * item_use_on_entity では hotbar_slot と held_item の間、
     * item_release では末尾（head_pos の直後）。この実装が読むのは
     * item_use_on_entity だけのため、読み取りの分岐はそちらだけに効く
     * （BE-NOTES.md §24。BedrockPackets.decodeInventoryTransaction を参照）。
     */
    public boolean transactionHasHand() {
        return protocol >= 2225;
    }

    // ---- アイテム対応表の読み込み ----

    /**
     * バージョン別アイテム対応表（{@code bedrock_item_ids_<tag>.bin}）の
     * タグ。1.20.50 は BedrockItemIds.java の表と同じ世代のため
     * {@code 1_20_50} を返す。
     */
    public String itemTableTag() {
        return switch (protocol) {
            // 291 は 1.7.1。アイテム表は 313〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、291 については
            // 代用である（291 自体の実測ではない）：PMMP BedrockData に
            // bedrock-1.7.0 タグ自体が存在せず（git/ref/tags は 404。
            // 純粋な bedrock-* 形式の最古タグは bedrock-1.6.1 で、
            // その中身も creativeitems.json と recipes.json のみ）、
            // required_item_list.json も item_id_map.json も無い。
            // 1.7 系のランタイム ID の実データは存在しない。
            // 表の中身は bedrock-1.16.100 タグの実測で、291 の
            // ランタイム ID 割り当てそのものは未確認。世代が大きく
            // 離れるため、291 のクライアントがこの表の ID をどう
            // 解釈するかは実機未確認である。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §36 参照）。
            case 291 -> "1_16_210";
            // 313 は 1.8.1。アイテム表は 332〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、313 については
            // 代用である（313 自体の実測ではない）：PMMP BedrockData の
            // bedrock-1.8.0 タグは item_id_map.json すら無く
            // （ツリー 4 ファイルのみ。creativeitems.json は旧来の
            // 数値 ID でランタイム ID ではない）、required_item_list.json
            // は無い。1.8 系のランタイム ID の実データは存在しない。
            // 表の中身は bedrock-1.16.100 タグの実測で、313 の
            // ランタイム ID 割り当てそのものは未確認。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §35 参照）。
            case 313 -> "1_16_210";
            // 332 は 1.9.0。アイテム表は 340〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、332 については
            // 代用である（332 自体の実測ではない）：PMMP BedrockData の
            // bedrock-1.9.0 タグは item_id_map.json すら無く
            // （ツリー 5 ファイルのみ。creativeitems.json は旧来の
            // 数値 ID でランタイム ID ではない）、required_item_list.json
            // は無い。1.9 系のランタイム ID の実データは存在しない。
            // 表の中身は bedrock-1.16.100 タグの実測で、332 の
            // ランタイム ID 割り当てそのものは未確認。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §34 参照）。
            case 332 -> "1_16_210";
            // 340 は 1.10.1。アイテム表は 354〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、340 については
            // 代用である（340 自体の実測ではない）：PMMP BedrockData の
            // bedrock-1.10.0 タグは item_id_map.json すら無く
            // （ツリー 5 ファイルのみ。creativeitems.json は旧来の
            // 数値 ID でランタイム ID ではない）、required_item_list.json
            // は無い。1.10 系のランタイム ID の実データは存在しない。
            // 表の中身は bedrock-1.16.100 タグの実測で、340 の
            // ランタイム ID 割り当てそのものは未確認。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §33 参照）。
            case 340 -> "1_16_210";
            // 354 は 1.11.4。アイテム表は 361〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、354 については
            // 代用である（354 自体の実測ではない）：PMMP BedrockData の
            // bedrock-1.11.0 タグは旧形式の item_id_map.json（229 件）・
            // block_id_map.json（460 件）しかなく required_item_list.json
            // は無い。1.11 系のランタイム ID の実データは存在しない。
            // 表の中身は bedrock-1.16.100 タグの実測で、354 の
            // ランタイム ID 割り当てそのものは未確認。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §32 参照）。
            case 354 -> "1_16_210";
            // 361 は 1.12.1。アイテム表は 388〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、361 については
            // 代用である（361 自体の実測ではない）：PMMP BedrockData の
            // bedrock-1.12.0 タグは旧形式の item_id_map.json（689 件）・
            // block_id_map.json（460 件）しかなく required_item_list.json
            // は無い。1.12 系のランタイム ID の実データは存在しない。
            // 表の中身は bedrock-1.16.100 タグの実測で、361 の
            // ランタイム ID 割り当てそのものは未確認。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §31 参照）。
            case 361 -> "1_16_210";
            // 388 は 1.13.3。アイテム表は 389〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、388 については
            // 代用である（388 自体の実測ではない）：PMMP BedrockData の
            // bedrock-1.13.0 タグは旧形式の item_id_map.json（696 件）・
            // block_id_map.json（465 件）しかなく required_item_list.json
            // は無い。1.13 系のランタイム ID の実データは存在しない。
            // 表の中身は bedrock-1.16.100 タグの実測で、388 の
            // ランタイム ID 割り当てそのものは未確認。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §30 参照）。
            case 388 -> "1_16_210";
            // 389 は 1.14.41。アイテム表は 390・407〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、389 については
            // 代用である（389 自体の実測ではない）：PMMP BedrockData に
            // bedrock-1.14.60 タグは存在せず、389 の世代の唯一のタグ
            // bedrock-1.14.0 も旧形式の item_id_map.json（702 件）・
            // block_id_map.json（469 件）しかなく required_item_list.json
            // は無い。1.14 系のランタイム ID の実データは存在しない。
            // 表の中身は bedrock-1.16.100 タグの実測で、389 の
            // ランタイム ID 割り当てそのものは未確認。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §29 参照）。
            case 389 -> "1_16_210";
            // 390 は 1.14.60。アイテム表は 407〜431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、390 については
            // 代用である（390 自体の実測ではない）：PMMP BedrockData に
            // bedrock-1.14.60 タグは存在せず、bedrock-1.14.0 タグは
            // 旧形式の item_id_map.json（702 件）・block_id_map.json
            // （469 件）しかなく required_item_list.json は無い。しかも
            // その 2 ファイルは bedrock-1.16.0 タグの同名ファイル
            // （807 件・553 件）と中身が違い、1.14 系のランタイム ID の
            // 実データは存在しない。表の中身は bedrock-1.16.100 タグの
            // 実測で、390 のランタイム ID 割り当てそのものは未確認。
            // exact=624／shared=236／legacy=33／fallback=419
            // （いずれも 1_16_210 表自体の内訳）。BE-NOTES.md §28 参照）。
            case 390 -> "1_16_210";
            // 407 は 1.16.10。アイテム表は 408・419・422・428・431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、407 についても
            // 代用である（407 自体の実測ではない）：PMMP BedrockData の
            // bedrock-1.16.0 タグは 407 を共有するリリース版 1.16.0 自体の
            // データだが旧形式の item_id_map.json しかなく、
            // required_item_list.json は無い。表の中身は
            // bedrock-1.16.100 タグの実測。block_id_map.json と
            // item_id_map.json が bedrock-1.16.0 タグと bedrock-1.16.100
            // タグでバイト同一であることは確認済みだが、407 のランタイム ID
            // 割り当てそのものは未確認。exact=624／shared=236／legacy=33／
            // fallback=419。BE-NOTES.md §27 参照）。
            case 407 -> "1_16_210";
            // 408 は 1.16.61。アイテム表は 419・422・428・431 と同じ
            // bedrock_item_ids_1_16_210.bin を使うが、408 については
            // 代用である（408 自体の実測ではない）：PMMP BedrockData に
            // bedrock-1.16.20／1.16.40 タグは存在せず、bedrock-1.16.0 タグは
            // 407 のバージョンのもので required_item_list.json を含まない
            // （旧形式の item_id_map.json のみ）。表の中身は
            // bedrock-1.16.100 タグの実測。block_id_map.json と
            // item_id_map.json が bedrock-1.16.0 タグと bedrock-1.16.100
            // タグでバイト同一であることは確認済みだが、408 のランタイム ID
            // 割り当てそのものは未確認。exact=624／shared=236／legacy=33／
            // fallback=419。BE-NOTES.md §26 参照）。
            case 408 -> "1_16_210";
            // 419 は 1.16.101。アイテム表は 422・428・431 と同じ
            // bedrock_item_ids_1_16_210.bin を使う（PMMP bedrock-1.16.100
            // タグの required_item_list.json から再生成した候補表が既存の
            // 表とバイト同一。bedrock-1.16.100 タグは 419 を共有する
            // リリース版 1.16.100 自体のデータなので他バージョンからの
            // 代用ではない。ただし bedrock-1.16.101 タグは存在しないため
            // 1.16.101 固有のアイテム差分が無いことの直接の証拠は無い
            // （未確認）。exact=624／shared=236／legacy=33／fallback=419。
            // BE-NOTES.md §25 参照）。
            case 419 -> "1_16_210";
            // 422 は 1.16.201。アイテム表は 428 と同じ
            // bedrock_item_ids_1_16_210.bin を使う（bedrock-1.16.200 タグの
            // required_item_list.json から再生成した候補表が既存の表と
            // バイト同一。1.16.200 は 422 を共有するリリース版そのものなので
            // 他バージョンからの代用ではない。ただし bedrock-1.16.201 タグは
            // 存在しないため 1.16.201 固有のアイテム差分が無いことの直接の
            // 証拠は無い（未確認）。exact=624／shared=236／legacy=33／
            // fallback=419。BE-NOTES.md §22 参照）。
            case 422 -> "1_16_210";
            // 428 は 1.16.210。アイテム表は 431 と同じ
            // bedrock_item_ids_1_16_210.bin を使う（1.16.210 自体の実測
            // データで、428 では代用ではない。exact=624／shared=236／
            // legacy=33／fallback=419。BE-NOTES.md §21 参照）。
            case 428 -> "1_16_210";
            // 431 は 1.16.221。アイテム表は bedrock-1.16.210 タグから生成した
            // bedrock_item_ids_1_16_210.bin を使う（bedrock-1.16.220 タグは
            // 存在しないため 1.16.210 の代用。1.16.220 自体の実測ではない。
            // exact=624／shared=236／legacy=33／fallback=419。1_17_0 の表とは
            // 369/1312 行が違うため共用できない。554 が 1_19_40 の表を共用
            // するのと同じく、ファイル名はデータの出所（1.16.210）に合わせた。
            // BE-NOTES.md §20 参照）。
            case 431 -> "1_16_210";
            // 440 は 1.17.2。アイテム表は bedrock-1.17.0 タグから生成した
            // bedrock_item_ids_1_17_0.bin を使う（1.17.0 自体の実測データ。
            // exact=730／shared=236／legacy=33／fallback=313。1_17_10 の表とは
            // 17/1312 行、1_17_40 の表とは 47/1312 行が違うため共用できない。
            // BE-NOTES.md §19 参照）。
            case 440 -> "1_17_0";
            // 448 は 1.17.11。アイテム表は bedrock-1.17.10 タグから生成した
            // bedrock_item_ids_1_17_10.bin を使う（1.17.10 自体の実測データ。
            // exact=747／shared=236／legacy=33／fallback=296。1_17_40 の表とは
            // 30/1312 行、1_18_0 の表とは 8/1312 行が違うため共用できない。
            // BE-NOTES.md §18 参照）。
            case 448 -> "1_17_10";
            // 465 は 1.17.34。アイテム表は bedrock-1.17.40 タグから生成した
            // bedrock_item_ids_1_17_40.bin を共用する（bedrock-1.17.30 タグ
            // の required_item_list.json から同じ手順で構築した候補表が
            // 1_17_40 の表とバイト同一。1.17.30 自体の実測データで、代用では
            // ない。1_18_0 の表とは 26/1312 行が違うため共用できない。
            // 860 が 859 の表を共用するのと同じ扱い。BE-NOTES.md §17 参照）。
            case 465 -> "1_17_40";
            // 471 は 1.17.41。アイテム表は bedrock-1.17.40 タグから生成した
            // bedrock_item_ids_1_17_40.bin を使う（1.17.40 自体の実測データ。
            // bedrock-1.17.41 タグは存在しないため、1.17.41 自体のレジストリが
            // 1.17.40 と同一かは実測できない（未確認）。1.17.40 のデータを 471
            // 全体に使う。1_18_0 の表とは 26/1312 行が違うため共用できない。
            // BE-NOTES.md §16 参照）。
            case 471 -> "1_17_40";
            // 475 は 1.18.2。アイテム表は bedrock-1.18.0 タグから生成した
            // bedrock_item_ids_1_18_0.bin を使う（1.18.0 自体の実測データ。
            // bedrock-1.18.1／bedrock-1.18.2 タグは存在しないため、1.18.1／
            // 1.18.2 自体のレジストリが 1.18.0 と同一かは実測できない
            // （未確認）。1.18.0 のデータを 475 全体に使う。1_18_10 の表とは
            // 37/1312 行が違うため共用できない。BE-NOTES.md §15 参照）。
            case 475 -> "1_18_0";
            // 486 は 1.18.12。アイテム表は bedrock-1.18.10 タグから生成した
            // bedrock_item_ids_1_18_10.bin を使う。1.18.11 自体の PMMP
            // BedrockData は存在しない（bedrock-1.18.11／bedrock-1.18.12
            // タグ無し、4 ファイルとも HTTP 404）が、1.18.10 は 486 を
            // 共有するリリース版のため、そのタグのデータを代用する
            // （1.18.11 自体の実測ではない点に注意。545 回の代用と同じ扱い。
            // BE-NOTES.md §14 参照）。1_18_30 の表とは 28/1312 行が違うため
            // 共用できない。
            case 486 -> "1_18_10";
            // 503 は 1.18.33。アイテム表は bedrock-1.18.30 タグから生成した
            // bedrock_item_ids_1_18_30.bin を使う（1_19_0 の表とは
            // 64/1312 行、1_19_40 の表とは 65/1312 行が違うため共用できない。
            // BE-NOTES.md §13 参照）。
            case 503 -> "1_18_30";
            // 527 は 1.19.2。アイテム表は bedrock-1.19.0 タグから生成した
            // bedrock_item_ids_1_19_0.bin を使う（1.19.1 自体の PMMP
            // BedrockData タグは存在しないが、1.19.1 は The Wild Update
            // （1.19.0）の Nintendo Switch 版のバージョン番号で、プロトコル
            // 527 は 1.19.0 リリースそのもののプロトコル（サーバーバージョン
            // 1.19.1.01）のため、bedrock-1.19.0 タグのデータは 527 の
            // リリース自体のデータであり、545 回のような「代用」ではない。
            // 1_19_40 の表とは 3/1312 行が違うため共用できない。
            // BE-NOTES.md §12 参照）。
            case 527 -> "1_19_0";
            // 534 は 1.19.11。アイテム表は 557 と同じ
            // bedrock_item_ids_1_19_40.bin を使う（bedrock-1.19.10 タグの
            // required_item_list.json が 1.19.20／1.19.30／1.19.40 のものと
            // バイト同一で、候補表もバイト同一。今回は 1.19.10 自体の実測。
            // 860 が 859 の表を共用するのと同じ扱い。BE-NOTES.md §11 参照）。
            case 534 -> "1_19_40";
            // 544 は 1.19.20。アイテム表は 557 と同じ
            // bedrock_item_ids_1_19_40.bin を使う（bedrock-1.19.20 タグの
            // required_item_list.json が 1.19.30／1.19.40 のものとバイト同一で、
            // 候補表もバイト同一。今回は 1.19.20 自体の実測。
            // 860 が 859 の表を共用するのと同じ扱い。BE-NOTES.md §10 参照）。
            case 544 -> "1_19_40";
            // 545 は 1.19.22。アイテム表は 557 と同じ
            // bedrock_item_ids_1_19_40.bin を使う。1.19.21 自体の
            // PMMP BedrockData は存在しない（bedrock-1.19.21 タグ無し、
            // 4 ファイルとも HTTP 404）が、直近のタグ付きデータである
            // 1.19.20（544）の required_item_list.json は 1.19.30／
            // 1.19.40 のものとバイト同一で、同じ手順で生成した候補表も
            // 1_19_40 の表とバイト同一（1.19.21 は 1.19.20 のホット
            // フィックスで、公開変更履歴に新規アイテム・ブロックの追加は
            // 無い。1.19.21 自体の実測ではない点に注意。BE-NOTES.md §9）。
            // 860 が 859 の表を共用するのと同じ扱い。
            case 545 -> "1_19_40";
            // 554 は 1.19.31。アイテム表は 557 と同じ
            // bedrock_item_ids_1_19_40.bin を使う（1.19.30 と 1.19.40 の
            // required_item_list.json がバイト同一で、候補表もバイト同一。
            // 860 が 859 の表を共用するのと同じ扱い。BE-NOTES.md §8 参照）。
            case 554 -> "1_19_40";
            // 557 は 1.19.41。アイテム表は bedrock-1.19.40 タグから生成した
            // bedrock_item_ids_1_19_40.bin を使う（1.19.50 の表とは
            // 44/1312 行が違うため共用できない。BE-NOTES.md §7 参照）。
            case 557 -> "1_19_40";
            // 560 は 1.19.51。アイテム表は bedrock-1.19.50 タグから生成した
            // bedrock_item_ids_1_19_50.bin を使う（1.19.60 の表とは
            // 164/1312 行が違うため共用できない。BE-NOTES.md §6 参照）。
            case 560 -> "1_19_50";
            // 567 は 1.19.62。アイテム表は bedrock-1.19.60 タグから生成した
            // bedrock_item_ids_1_19_60.bin を使う（1.19.70 の表とは
            // 401/1312 行が違うため共用できない。BE-NOTES.md §5 参照）。
            case 567 -> "1_19_60";
            // 575 は 1.19.70。アイテム表は bedrock-1.19.70 タグから生成した
            // bedrock_item_ids_1_19_70.bin を使う（1.19.80 の表とは
            // 64/1312 行が違うため共用できない。BE-NOTES.md §4 参照）。
            case 575 -> "1_19_70";
            // 582 は 1.19.83。アイテム表は bedrock-1.19.80 タグから生成した
            // bedrock_item_ids_1_19_80.bin を使う（1.20.0 の表とは
            // 450/1312 行が違うため共用できない。BE-NOTES.md §3 参照）。
            case 582 -> "1_19_80";
            case 589 -> "1_20_0";
            case 594 -> "1_20_10";
            case 618 -> "1_20_30";
            case 622 -> "1_20_40";
            case 630 -> "1_20_50";
            case 649 -> "1_20_60";
            case 662 -> "1_20_70";
            case 671 -> "1_20_80";
            case 685, 686 -> "1_21_0";
            case 712 -> "1_21_20";
            case 729 -> "1_21_30";
            case 748 -> "1_21_40";
            case 766 -> "1_21_50";
            case 776 -> "1_21_60";
            case 786 -> "1_21_70";
            case 800 -> "1_21_80";
            case 818 -> "1_21_90";
            case 819, 827 -> "1_21_93";
            case 844 -> "1_21_111";
            // 860 は 1.21.124。protocol.json が 1.21.120（859）とバイト同一の
            // ホットフィックスで、アイテム ID に差分が無いことを再生成の
            // バイト一致で確認したため 1.21.120 の表を共用する。
            case 859, 860 -> "1_21_120";
            case 898 -> "1_21_130";
            case 924 -> "1_26_0";
            case 944 -> "1_26_10";
            case 975 -> "1_26_20";
            // 2193 は 1.26.50 / 1.26.51 / 1.26.52 で共有。26.52 用の
            // bedrock_item_ids_1_26_52.bin は無いため 1.26.30 の表を共用する。
            // 2225（1.26.60〜1.26.65）も同じ表を共用する。PMMP BedrockData
            // のタグは bedrock-1.26.30 が最後で 1.26.40 以降は存在せず、
            // 1.26.60 自体の required_item_list.json は取得できないため、
            // 2225 のアイテム対応は 1.26.30 の表の流用であり 1.26.60 自体の
            // 実測ではない（BE-NOTES.md §24）。
            case 1001, 2168, 2169, 2193, 2225 -> "1_26_30";
            default -> null;
        };
    }
}
