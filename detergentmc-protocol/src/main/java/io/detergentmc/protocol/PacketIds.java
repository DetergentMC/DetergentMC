package io.detergentmc.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * パケットID表：状態×方向×パケット名×プロトコル（5 / 47 / 107 / 108 / 109 / 110 / 210 / 315 / 316 / 335 / 338 / 340 / 393 / 401 / 404 / 477 / 480 / 485 / 490 / 498 / 573 / 575 / 578 / 758 / 759 / 760 / 761 / 762 / 763 / 764 / 765 / 766 / 767 / 768 / 769 / 770 / 771 / 772 / 773 / 774 / 775 / 776）。
 *
 * <p>情報源：
 * <ul>
 *   <li>wiki.vg の Protocol（https://wiki.vg/Protocol）と Protocol History
 *       （https://wiki.vg/Protocol_History）— パケット名と状態遷移の定義。</li>
 *   <li>PrismarineJS minecraft-data の protocol.json（wiki.vg と Mojang の
 *       データ生成器をもとにした公開データ）。1.18.2（758）は
 *       data/pc/1.18.2/protocol.json、1.19（759）は
 *       data/pc/1.19/protocol.json、1.19.1／1.19.2（760）は
 *       data/pc/1.19.2/protocol.json、1.19.3（761）は
 *       data/pc/1.19.3/protocol.json、1.19.4（762）は
 *       data/pc/1.19.4/protocol.json、1.20.1（763）は
 *       data/pc/1.20/protocol.json、1.20.2（764）は
 *       data/pc/1.20.2/protocol.json、1.20.4（765）は 1.20.3 と共通のため
 *       data/pc/1.20.3/protocol.json、1.20.5／1.20.6（766）は
 *       data/pc/1.20.5/protocol.json、1.21／1.21.1（767）は
 *       data/pc/1.21.1/protocol.json、1.21.2／1.21.3（768）は
 *       data/pc/1.21.3/protocol.json、1.21.4（769）は
 *       data/pc/1.21.4/protocol.json、1.21.5（770）は
 *       data/pc/1.21.5/protocol.json、1.21.6（771）は
 *       data/pc/1.21.6/protocol.json、1.21.7／1.21.8（772）は
 *       data/pc/1.21.8/protocol.json、1.21.9／1.21.10（773）は
 *       data/pc/1.21.9/protocol.json、1.21.11（774）は
 *       data/pc/1.21.11/protocol.json、26.1／26.1.1／26.1.2（775）は
 *       data/pc/26.1/protocol.json を使った。26.2（776）は minecraft-data
 *       に無いため、本物のバニラ 26.2 の jar から抽出したパケット順序で作った
 *       mc-26.2-protocol.json を使った（tools/build-mc-26.2-protocol.py）。
 *       minecraft-data には
 *       pc/1.21.7 のフォルダが無い（dataPaths.json に 1.21.7 の項目自体が
 *       無い）ため、同じ 772 の 1.21.8 を使う。minecraft-data には
 *       pc/1.21.10 のフォルダが無い（dataPaths.json に 1.21.10 の項目自体が
 *       無い）ため、同じ 773 の 1.21.9 を使う。minecraft-data には
 *       pc/1.21.2 のフォルダが無い（dataPaths.json に 1.21.2 の項目自体が
 *       無い）ため、同じ 768 の 1.21.3 を使う。どちらを使うかは
 *       data/dataPaths.json の "protocol" 欄で確認した：
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.20/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.20.2/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.20.3/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.20.5/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.1/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.3/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.4/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.5/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.6/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.8/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.21.9/protocol.json
 *       https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/dataPaths.json</li>
 * </ul>
 *
 * <p>対応するプロトコルは 5（1.7.6〜1.7.10）と 47（1.8〜1.8.9）と 107（1.9）と 108（1.9.1）と 109（1.9.2）と 110（1.9.3／1.9.4）と 210（1.10／1.10.1／1.10.2）と 315（1.11）と 316（1.11.1／1.11.2）と 335（1.12）と 338（1.12.1）と 340（1.12.2）と 393（1.13）と 401（1.13.1）と 404（1.13.2）と 477（1.14）と 480（1.14.1）と 485（1.14.2）と 490（1.14.3）と 498（1.14.4）と 573（1.15）と 575（1.15.1）と 578（1.15.2）と 758（1.18.2）と 759（1.19）と 760（1.19.1／1.19.2）と 761（1.19.3）と 762（1.19.4）と 763（1.20.1）と 764（1.20.2）と 765（1.20.4）と
 * 766（1.20.5／1.20.6）と 767（1.21／1.21.1）と 768（1.21.2／1.21.3）と
 * 769（1.21.4）と 770（1.21.5）と 771（1.21.6）と 772（1.21.7／1.21.8）と
 * 773（1.21.9／1.21.10）と
 * 774（1.21.11）と 775（26.1／26.1.1／26.1.2）と 776（26.2）。
 * それ以外のIDはこの表には無い。764のIDはすべて上の
 * protocol.json（1.20.2）から1件ずつ確認した。確認方法：protocol.json
 * の各状態・方向の mapper 表を抜き出し、既存の763／765の値がすべて
 * 再現できる写像（パケット名→minecraft-data名）で764の値を引いた。
 * 写像が間違っていれば既存の値が再現できないので、763／765の再現が
 * 764の値の正しさの裏付けになっている。766も同じ方法で確認した
 * （tools/packet-ids-766.py）：既存の199エントリすべてが
 * minecraft-data の内部名で一意に写像でき、766のIDを引けた。
 * 199件中159件のIDが765と違い、2件（resource_pack_send）が766で削除された。
 * 767も同じ方法で確認した（tools/packet-ids-767.py）：216エントリすべてが
 * minecraft-data の内部名で一意に写像でき、767のIDを引けた。
 * 766とIDが違うものは0件で、767で削除されたものも0件（resource_pack_send
 * の2件は766に続いて -1）。767で新規のパケットは6件（設定の
 * Server Links／Custom Report Details の行き来2件ずつと、プレイの
 * Server Links／Custom Report Details）だけである。
 * 768も同じ方法で確認した（tools/packet-ids-768.py）：222エントリすべてが
 * minecraft-data の内部名で一意に写像でき、768のIDを引けた。
 * 767とIDが違うものは145件で、768で削除されたものは2件
 * （unlock_recipes・steer_vehicle。resource_pack_send の2件は766に続いて
 * -1）。768で新規のパケットは11件（下の「768の要点」を参照）。
 * 769も同じ方法で確認した（tools/packet-ids-769.py）：233エントリすべてが
 * minecraft-data の内部名で一意に写像でき、769のIDを引けた。
 * 768とIDが違うものは24件で、769で削除されたものは1件
 * （pick_item。768で削除された unlock_recipes・steer_vehicle・
 * resource_pack_send の2件は769に続いて -1）。
 * 769で新規のパケットは3件（下の「769の要点」を参照）。
 * 770も同じ方法で確認した（tools/packet-ids-770.py）：236エントリすべてが
 * minecraft-data の内部名で一意に写像でき、770のIDを引けた。
 * 769とIDが違うものは122件で、770で削除されたものは1件
 * （spawn_entity_experience_orb。経験値オーブは 770 では通常の
 * Spawn Entity で送られる。769で削除された pick_item・
 * unlock_recipes・steer_vehicle・resource_pack_send の2件は770に続いて
 * -1）。770で新規のパケットは3件（下の「770の要点」を参照）。
 * 771も同じ方法で確認した（tools/packet-ids-771.py）：239エントリすべてが
 * minecraft-data の内部名で一意に写像でき、771のIDを引けた。
 * 770とIDが違うものは58件で、771で削除されたものは2件
 * （設定の Custom Report Details／Server Links のサーバー行き。
 * 770で削除された spawn_entity_experience_orb・pick_item・
 * unlock_recipes・steer_vehicle・resource_pack_send の2件は771に続いて
 * -1）。771で新規のパケットは8件（下の「771の要点」を参照）。
 * 772も同じ方法で確認した（tools/packet-ids-772.py）：247エントリすべてが
 * minecraft-data の内部名で一意に写像でき、772のIDを引けた（なお771の段落の
 * 「239エントリ」は数え間違いで、スクリプトが数えるエントリは771の時点でも
 * 247件だった）。771とIDが違うものは0件で、772で削除されたものも0件
 * （771で -1 の8件は772に続いて -1）。772で新規のパケットも0件
 * （minecraft-data 1.21.8 の protocol.json は 1.21.6 のものとバイト単位で
 * 同一で、表に載せない ping_request は771と同じ 0x25、legacy_server_list_ping
 * は同じ 0xfe）。つまりパケットIDだけ見れば772は771と完全に同じである。
 * 760も同じ方法で確認した（tools/packet-ids-760.py）：257エントリすべてが
 * minecraft-data の内部名で一意に写像でき、760のIDを引けた。
 * 761とIDが違うものは118件で、760で存在しないものは94件
 * （設定フェーズの全パケット、Login Acknowledged、Player Info Remove、
 * Add／Remove Resource Pack、Chunk Biomes、Bundle Delimiter、Damage Event、
 * Hurt Animation、Chat Session Update、Start Configurationなど。
 * 761で追加された feature_flags（プレイ 0x67）・player_remove・
 * profileless_chat・chat_session_update は760に無い）。
 * 760にだけあるパケットは5件（chat_preview の行き来、message_header、
 * named_sound_effect、should_display_chat_preview。legacy_server_list_ping
 * はハンドシェイク／ステータス系のためこの表の対象外）。
 * 760と761のパケット名の差はプレイ状態のみ（ログイン・ハンドシェイクは同じ）。
 * 758も同じ方法で確認した（tools/packet-ids-758.py）：272エントリすべてが
 * minecraft-data の内部名で一意に写像でき、758のIDを引けた。
 * 759とIDが違うものは123件で、758で存在しないものは111件
 * （設定フェーズの全33件、ログインの3件、プレイの75件。
 * 759で追加された Player Chat／System Chat／Player Info Remove／
 * Damage Event／Hurt Animation／Feature Flags ほかは758に無い）。
 * 758にだけあるパケットは5件（spawn_entity_living、spawn_entity_painting、
 * sculk_vibration_signal、署名なしチャットの行き来。legacy_server_list_ping
 * はハンドシェイク／ステータス系のためこの表の対象外）。
 * 758と759のパケット名の差はプレイ状態のみ（ログイン・ハンドシェイクは同じ）。
 * 758の要点は下の「758の要点」を参照。
 *
 * <p>758の要点（759との違い）：
 * <ul>
 *   <li>設定フェーズは758には無い（1.20.2で導入）。設定フェーズの33件は
 *       すべて -1。レジストリは Join Game の dimension codec で送られる。</li>
 *   <li>チャット署名は758には無い（1.19で導入）。クライアント行き・サーバー行き
 *       とも署名なしの旧式 chat パケット（PLAY_CHAT／PLAY_CHAT_TO_SERVER）
 *       を使う。759の Player Chat／System Chat／Message Header は758に無い。</li>
 *   <li>出現パケットは758では分かれている：Spawn Entity（0x00）・
 *       Spawn Experience Orb（0x01）・Spawn Living Entity（0x02）・
 *       Spawn Painting（0x03）・Named Entity Spawn（0x04、プレイヤー）。
 *       759では Spawn Living Entity と Spawn Painting が Spawn Entity に
 *       統合された。</li>
 *   <li>スカルク振動は758では Sculk Vibration Signal（0x05）の専用パケット。
 *       759で廃止された。</li>
 *   <li>ログイン・ハンドシェイクのIDは759とすべて同じ。</li>
 * </ul>
 *
 * <p>764の要点（765との違い）：
 * <ul>
 *   <li>設定フェーズは764にもある。Registry Data は765と同じく1個の
 *       名前なし NBT codec。Login Start は765と同じく名前＋UUID必須
 *       （763の bool 付き任意UUIDではない）。Join Game も765と同形式
 *       （codec は設定フェーズに移っている）。Respawn・プレイ状態の
 *       Disconnect・Entity Effect も765と同形式のため変換は要らない。</li>
 *   <li>リソースパックは764では単一の resource_pack_send（設定 0x06・
 *       プレイ 0x42、UUIDなし、プロンプトは JSON 文字列）。765の
 *       Add／Remove Resource Pack（UUID付き）は764に無い。</li>
 *   <li>764に無い765のパケット：Reset Score、Set Ticking State、
 *       Step Tick、Add／Remove Resource Pack、Set Slot State
 *       （サーバー行き）。Game Event 13（チャンク待ち開始）も764に無い。</li>
 *   <li>テキストコンポーネントは764では JSON 文字列（763と同じ）、
 *       765では NBT。System Chat・Player Chat・Boss Bar・Teams・
 *       Tab Complete・Advancements などの NBT→JSON 文字列変換は
 *       763向けのものがそのまま764にも使える（ただし Advancements は
 *       764には criteria が無いので、763向けの空 criteria 書き足しは
 *       しないこと）。</li>
 * </ul>
 *
 * <p>766の要点（765との違い）：
 * <ul>
 *   <li>設定フェーズのクライアント行きに Cookie Request（0x00）が先頭に
 *       入り、既存のパケットが1つずつ後ろにずれる。Reset Chat（0x06）、
 *       Store Cookie（0x0a）、Transfer（0x0b）、Select Known Packs
 *       （0x0e）が追加された。サーバー行きにも Cookie Response（0x01）と
 *       Select Known Packs（0x07）が追加された。設定フェーズのIDは
 *       クライアント行き15個・サーバー行き8個になる。</li>
 *   <li>ログインにも Cookie Request（0x05）と Cookie Response（0x04）が
 *       追加された。Login Success のIDは 0x02 のまま変わらないが、
 *       末尾に Strict Error Handling の bool が追加された（ペイロードの
 *       変換は {@link io.detergentmc.protocol.PlayTranslator} 参照）。</li>
 *   <li>単一の resource_pack_send パケットが766で削除された。設定の
 *       resource_pack_send とプレイの resource_pack_send はどちらも
 *       766では -1。766では Add／Remove Resource Pack だけを使う。</li>
 *   <li>プレイのサーバー行きに Chat Command Signed（0x05）が追加され、
 *       Chat Message は 0x05 から 0x06 にずれる。Chat Command（0x04）は
 *       変わらない。Cookie Response（0x11）と Debug Sample Subscription
 *       （0x13）も追加された。プレイのサーバー行きは多くのIDが
 *       後ろにずれる（cookie_request・debug_sample_subscription の挿入分）。</li>
 *   <li>プレイのクライアント行きに Cookie Request（0x16）、Debug Sample
 *       （0x1b）、Store Cookie（0x6b）、Transfer（0x73）、
 *       Set Projectile Power（0x79）が追加された。</li>
 *   <li>ハンドシェイクに Transfer（3）の intent が追加された（wiki.vg
 *       Protocol History）。この表のハンドシェイクは 0x00 のまま。</li>
 *   <li>Ping Request（プレイ状態・サーバー行き。766は 0x21）と Legacy
 *       Server List Ping（0xFE）は766にもあるが、既存の方針どおり
 *       この表には載せていない（プロキシは落とす）。</li>
 * </ul>
 *
 * <p>名前について：minecraft-data での内部名と wiki.vg の表示名が違う
 * ものがある（例：minecraft-data の {@code login} が wiki.vg の
 * Join Game、{@code map_chunk} が Chunk Data and Update Light、
 * {@code kick_disconnect} が Disconnect (play)）。この表の
 * {@link PacketName} は wiki.vg の表示名に寄せてある。
 *
 * <p>767の要点（766との違い）：
 * <ul>
 *   <li>既存パケットのIDは1件も変わらない。766のIDがそのまま767のIDである。
 *       新規は設定フェーズの Server Links（クライアント行き 0x10・サーバー行き
 *       0x09）と Custom Report Details（クライアント行き 0x0f・サーバー行き
 *       0x08）、プレイの Server Links（0x7b）と Custom Report Details
 *       （0x7a）の6件だけである。Ping Request（プレイ状態・サーバー行き。
 *       767は 0x21）は766と同じくこの表には載せていない（プロキシは落とす）。</li>
 *   <li>Login Success のIDは 0x02 のまま変わらず、末尾の Strict Error
 *       Handling の bool も766と同じく付く（768で廃止されたものが767には
 *       まだある）。ペイロードの変換は {@link io.detergentmc.protocol.PlayTranslator} 参照。</li>
 *   <li>Join Game・Respawn・Update Attributes 以外のプレイ状態のパケットは
 *       形式も766と同じ。Update Attributes は属性の並びが変わり
 *       （新規9件が名前順に挿入されて番号がずれる）、modifier の UUID が
 *       文字列になったため、レジストリ番号の変換が要る
 *       （{@link io.detergentmc.protocol.ChunkTranslator} 参照）。</li>
 * </ul>
 *
 * <p>768の要点（767との違い）：
 * <ul>
 *   <li>既存パケットのIDは145件変わる。設定フェーズ・ログインのIDは
 *       変わらず、プレイ状態のIDが大きくずれる（新規パケットの挿入分）。
 *       新規はプレイの Sync Entity Position（0x20）・Move Minecart
 *       （0x31）・Player Rotation（0x43）・Recipe Book Add（0x44）・
 *       Recipe Book Remove（0x45）・Recipe Book Settings（0x46）・
 *       Set Cursor Item（0x5a）・Set Player Inventory（0x66）と、
 *       サーバー行きの Select Bundle Item（0x02）・Tick End（0x0b）・
 *       Player Input（0x28）の11件。削除は Unlock Recipes と
 *       Steer Vehicle（Player Input に置き換え）の2件。Ping Request
 *       （プレイ状態・サーバー行き。768は 0x23）は766／767と同じくこの表には
 *       載せていない（プロキシは落とす）。Legacy Server List Ping
 *       （ハンドシェイク 0xFE）も載せていない。</li>
 *   <li>Login Success のIDは 0x02 のまま変わらないが、末尾の Strict
 *       Error Handling の bool が768で廃止された。767→768では末尾の
 *       bool を1バイト落とし、768→767では false を1バイト足す
 *       （{@link io.detergentmc.protocol.PlayTranslator} 参照）。</li>
 *   <li>ペイロードの形式が変わったものがある（minecraft-data の
 *       proto.yml 1.21.1→1.21.3 の差分で確認）。Synchronize Player
 *       Position（teleportId が先頭に移動し、速度・u32フラグが追加）、
 *       移動パケット（サーバー行きの onGround bool が MovementFlags
 *       ビットフラグに変更）、Set Cooldown（アイテムID→クールダウン
 *       グループの文字列）、Explosion（半径・影響ブロック・ノックバックの
 *       形式が変更）、Update Time（tickDayTime の bool が追加）、
 *       Respawn／Join Game（SpawnInfo に seaLevel が追加）、Player Info
 *       （list_order アクションが追加）、Set Slot State（window_id が
 *       VarInt→ContainerID）、Craft Recipe Response（レシピ名の文字列→
 *       Recipe Display）、Craft Recipe Request／Displayed Recipe
 *       （レシピIDが文字列→VarInt）、Block Place（worldBorderHit の bool
 *       が追加）、Particle（dust／dust_color_transition の色が f32×3→
 *       i32 に変更、trail／block_crumble を追加）、Declare Recipes
 *       （形式が全面変更。プロキシは従来どおり落とす）。
 *       ペイロードの変換は {@link io.detergentmc.protocol.PlayTranslator}
 *       と {@link io.detergentmc.protocol.ChunkTranslator} 参照。</li>
 *   <li>スロットのデータコンポーネント種別が57→67件に増え、大きく
 *       並び替わった。custom_model_data の形式も変更（VarInt→
 *       floats／flags／strings／colors の4配列）、food は
 *       secondsToEat／usingConvertsTo／effects を失い consumable
 *       に分離、fire_resistant は削除された。詳しくは
 *       {@link io.detergentmc.protocol.ChunkTranslator} 参照。</li>
 * </ul>
 *
 * <p>769の要点（768との違い）：
 * <ul>
 *   <li>既存パケットのIDは24件変わる。クライアント行きのIDは1件も
 *       変わらず、サーバー行きのプレイ状態のIDだけがずれる（新規
 *       パケットの挿入分）。新規はサーバー行きの Pick Item From Block
 *       （0x22）・Pick Item From Entity（0x23）・Player Loaded（0x2a）
 *       の3件。削除は Pick Item（ブロック／エンティティからの2つに
 *       分かれた）の1件。Ping Request（プレイ状態・サーバー行き。
 *       769は 0x24）は766／767／768と同じくこの表には載せていない
 *       （プロキシは落とす）。Legacy Server List Ping
 *       （ハンドシェイク 0xFE）も載せていない。</li>
 *   <li>ペイロードの形式が変わったものがある（minecraft-data の
 *       protocol.json 1.21.3→1.21.4 の差分で確認）。Explosion
 *       （playerKnockback が f32×3→f64×3）、Set Held Item
 *       （クライアント行き。slot が i8→VarInt）、Player Info
 *       （update_hat アクション（bit 6）が追加され、update_list_order
 *       は bit 7 へ移動）、World Particles（alwaysShow の bool が
 *       追加）、Vehicle Move（サーバー行き。末尾に onGround の bool
 *       が追加）、Pick Item（ブロック位置／エンティティID＋bool の
 *       2パケットに分割）、trail パーティクル（duration の VarInt が
 *       追加）、banner_patterns 成分（pattern が VarInt→
 *       レジストリエントリホルダーに変更）。ペイロードの変換は
 *       {@link io.detergentmc.protocol.PlayTranslator}
 *       と {@link io.detergentmc.protocol.ChunkTranslator} 参照。</li>
 *   <li>設定フェーズ・ログインのIDと形式は768と同じ。Join Game・
 *       Respawn・Synchronize Player Position・Client Information・
 *       Login Success の形式も768と同じ。</li>
 * </ul>
 *
 * <p>770の要点（769との違い）：
 * <ul>
 *   <li>既存パケットのIDは122件変わる。クライアント行きのプレイ
 *       117件が1つずつ前にずれる（Spawn Experience Orb（0x02）の
 *       削除分）。サーバー行きのプレイは5件だけ変わる（Update Sign
 *       0x39→0x3a、Arm Animation 0x3a→0x3b、Spectate 0x3b→0x3d、
 *       Block Place 0x3c→0x3e、Use Item 0x3d→0x3f）。
 *       新規はクライアント行きの Test Instance Block Status（0x77）と
 *       サーバー行きの Set Test Block（0x39）・Test Instance Block
 *       Action（0x3c）の3件。削除は Spawn Experience Orb（経験値
 *       オーブは 770 では通常の Spawn Entity で送られる）の1件。
 *       Ping Request（プレイ状態・サーバー行き。770は 0x24）は
 *       766／767／768／769と同じくこの表には載せていない
 *       （プロキシは落とす）。Legacy Server List Ping
 *       （ハンドシェイク 0xFE）も載せていない。</li>
 *   <li>ペイロードの形式が変わったものがある（minecraft-data の
 *       protocol.json 1.21.4→1.21.5 の差分で確認）。Chunk Data
 *       （heightmaps が NBT→直接の配列に、chunkData が ByteArray に）、
 *       Player Chat（先頭に globalIndex の VarInt が追加）、
 *       Chat Message／Chat Command Signed（末尾に checksum の u8 が
 *       追加）、Advancements（末尾に showAdvancements の bool が追加）、
 *       Teams（nameTagVisibility・collisionRule が文字列→VarInt の
 *       enum に）、Trade List（取引の入力アイテムが ExactComponentMatcher
 *       に）、Window Click（changedSlots と cursorItem が HashedSlot に）、
 *       Set Creative Slot（UntrustedSlot に）、ItemBlockPredicate
 *       （末尾に components の DataComponentMatchers が追加）、
 *       スロットのデータコンポーネント種別（67→96種に増え、番号も
 *       大きくずれる。hide_additional_tooltip／hide_tooltip は
 *       tooltip_display に統合）、エンティティメタデータの種別
 *       （cow_variant・wolf_sound_variant・pig_variant・
 *       chicken_variant が追加され、wolf_variant はホルダー→VarInt に）、
 *       ArmorTrimMaterial（ingredientId が削除され assetName→assetBase に）、
 *       ArmorTrimPattern（templateItemId が削除）、SlotDisplay の
 *       smithing_trim（pattern が SlotDisplay→レジストリエントリホルダーに）、
 *       Declare Commands の引数種別（resource_selector が追加され
 *       47番以降がずれる）。ペイロードの変換は
 *       {@link io.detergentmc.protocol.PlayTranslator}
 *       と {@link io.detergentmc.protocol.ChunkTranslator} 参照。</li>
 *   <li>設定フェーズ・ログインのIDと形式は769と同じ。Join Game・
 *       Respawn・Synchronize Player Position・Client Information・
 *       Login Success の形式も769と同じ。</li>
 * </ul>
 *
 * <p>771の要点（770との違い）：
 * <ul>
 *   <li>既存パケットのIDは58件変わる。すべてサーバー行きのプレイ
 *       （Change Gamemode（0x04）の挿入で 0x04 以降が1つずつ後ろに
 *       ずれる。ただし Spectate は 0x3d のまま（Test Instance Block
 *       Action が 0x3c→0x3e へ Spectate の後ろに回ったため）。
 *       クライアント行きのプレイ・設定・ログインのIDは変わらない。
 *       新規は設定の Clear Dialog（0x11）・Show Dialog（0x12）・
 *       Custom Click Action（サーバー行き 0x08）、プレイの Tracked
 *       Waypoint（0x83）・Clear Dialog（0x84）・Show Dialog（0x85）・
 *       Change Gamemode（サーバー行き 0x04）・Custom Click Action
 *       （サーバー行き 0x41）の8件。削除は設定の Custom Report
 *       Details／Server Links のサーバー行き2件（771 の
 *       minecraft-data には configuration toServer のこの2件が無い。
 *       クライアント行きの同名パケットは残っている）。
 *       Ping Request（プレイ状態・サーバー行き。771は 0x25）は
 *       766／767／768／769／770と同じくこの表には載せていない
 *       （プロキシは落とす）。Legacy Server List Ping
 *       （ハンドシェイク 0xFE）も載せていない。</li>
 *   <li>ペイロードの形式が変わったものがある（minecraft-data の
 *       protocol.json 1.21.5→1.21.6 の差分で確認）。Update Attributes
 *       （属性に camera_distance・waypoint_transmit_range・
 *       waypoint_receive_range が追加され、camera_distance の挿入で
 *       8番以降が1つずつ後ろにずれる）、Declare Recipes／Set
 *       Container Content あたりのスロットのデータコンポーネント種別は
 *       770と同じ96種・同じ並び（変換は要らない）、エンティティ
 *       メタデータの種別（happy_ghast 関連の追加は無く、770と同じ
 *       並びの見込み。実バニラキャプチャで確認すること）。ペイロードの
 *       変換は {@link io.detergentmc.protocol.PlayTranslator}
 *       と {@link io.detergentmc.protocol.ChunkTranslator} 参照。</li>
 *   <li>設定フェーズ・ログインのIDと形式は770と同じ。Join Game・
 *       Respawn・Synchronize Player Position・Client Information・
 *       Login Success の形式も770と同じ（minecraft-data の
 *       protocol.json 1.21.5→1.21.6 の差分で確認。実バニラキャプチャ
 *       での確認は統合テストで行う）。</li>
 * </ul>

 * <p>772の要点（771との違い）：
 * <ul>
 *   <li>パケットIDは1件も変わらない。新規・削除も無い。minecraft-data の
 *       protocol.json は 1.21.6（771）と 1.21.8（772）でバイト単位で同一
 *       （tools/packet-ids-772.py で確認。247エントリすべてが一意に写像
 *       できた）。Ping Request（プレイ状態・サーバー行き。772は 0x25）も
 *       表には載せていない（プロキシは落とす）。</li>
 *   <li>ペイロードの形式も771と同じ見込み。771→772で変わるのはレジストリ
 *       の番号だけ（アイテム music_disc_lava_chicken の挿入で1256番以降が
 *       1つずれる、音 music_disc.lava_chicken の挿入で939番以降が1つずれる。
 *       ブロック状態・エンティティ・パーティクル・属性・エンチャント・
 *       バイオーム・データコンポーネント種別は変わらない。ジュークボックスの
 *       歌と絵画は実バニラキャプチャで確認する）。ペイロードの変換は
 *       {@link io.detergentmc.protocol.PlayTranslator}
 *       と {@link io.detergentmc.protocol.ChunkTranslator} 参照。</li>
 *   <li>設定フェーズ・ログインのIDと形式は771と同じ。Join Game・
 *       Respawn・Synchronize Player Position・Client Information・
 *       Login Success の形式も771と同じ見込み（実バニラキャプチャでの確認は
 *       統合テストで行う）。</li>
 * </ul>
 *
 * <p>773の要点（772との違い）：
 * <ul>
 *   <li>プレイ状態・クライアント行きのIDが108件変わる。新規パケットは8件
 *       （設定の code_of_conduct／accept_code_of_conduct、プレイの
 *       debug_block_value／debug_chunk_value／debug_entity_value／
 *       debug_event／game_test_highlight_pos／debug_subscription_request）。
 *       debug_block_value〜debug_event（0x1A〜0x1D）の挿入でそれより後の
 *       プレイ・クライアント行きが4つずつ後ろにずれ、さらに
 *       game_test_highlight_pos（0x27）の挿入で0x27以降がもう1つずれる。
 *       削除は1件（debug_sample_subscription。773では同ID 0x16に
 *       debug_subscription_request が入るが、形式が違う（varint 1個から
 *       DebugSubscriptionDataType の配列へ）ため同一パケットの改名ではなく
 *       別パケットとして扱い、PLAY_DEBUG_SAMPLE_SUBSCRIPTION の773は -1
 *       のままにする）。設定・ログイン・ハンドシェイクとプレイ・サーバー
 *       行きのIDは772と同じ。tools/packet-ids-773.py で確認。247エントリ
 *       すべてが一意に写像できた。</li>
 *   <li>ペイロードの形式は772と同じ見込み。772→773で変わるのはレジストリ
 * <p>774の要点（773との違い）：
 *   <ul>
 *     <li>パケットIDは773とまったく同じ。tools/packet-ids-774.py で確認。
 *       255エントリすべてが minecraft-data の内部名で一意に写像でき、
 *       773とIDが違うものは0件、774で削除されたものも0件、774で新規の
 *       パケットも0件（表に載せない ping_request は774でも 0x25、
 *       legacy_server_list_ping は 0xfe）。</li>
 *     <li>ペイロードの形式は773とほぼ同じ。違うのは次のものだけ。
 *       Enchant Item（サーバー行き）の enchantment フィールドが
 *       varint から i8 に変わった（値は 0〜42 のためバイト列は同じ）。
 *       Chat Command Signed の checksum は u8 から i8 に変わったが
 *       バイト列は同じ。新しいデータコンポーネント 8 種
 *       （use_effects／minimum_attack_charge／damage_type／attack_range／
 *       piercing_weapon／kinetic_weapon／swing_animation／
 *       zombie_nautilus/variant）と、エンティティメタデータの新種別
 *       zombie_nautilus_variant（28）・humanoid_arm（38）が増えた。
 *       Spawn Position は773と同じ RespawnData 形式、Client Information
 *       は773と同じ9フィールド、Chunk Batch Received の Ack も同じ。</li>
 *   </ul>
 * <p>775の要点（774との違い）：
 *   <ul>
 *     <li>パケットIDは774から大きくずれた。tools/packet-ids-775.py で確認。
 *       262エントリすべてが minecraft-data の内部名で一意に写像でき
 *       （760だけの5件は内部名を既知のものとして775での不在を確認し -1）、
 *       774とIDが違うものは164件（クライアント行き100・サーバー行き64）、
 *       775で削除されたものは0件、775で新規のパケットは5件（プレイの
 *       Game Rule Values 0x27／Low Disk Space Warning 0x32 のクライアント行きと、
 *       Attack 0x01／Set Game Rule 0x39／Spectate Entity 0x3e のサーバー行き。
 *       表に載せない ping_request は775では 0x26、
 *       legacy_server_list_ping は 0xfe）。</li>
 *     <li>ペイロードの形式は774とほぼ同じ見込み。違うのは次のものだけ
 *       （Stage B/C で PlayTranslator／ChunkTranslator の775対応とともに確認する）。
 *       新しいデータコンポーネント 6 種
 *       （additional_trade_cost／dye／pig/sound_variant／cow/sound_variant／
 *       chicken/sound_variant／cat/sound_variant）と、新しい GameRule 型
 *       （Game Rule Values／Set Game Rule の配列要素）が増えた。</li>
 *   </ul>
 *       の番号（ブロック状態・アイテム・エンティティ・音など。銅の追加と
 *       chain→iron_chain の改名）と上記のパケットIDのずれ。ペイロードの
 *       変換は {@link io.detergentmc.protocol.PlayTranslator}
 *       と {@link io.detergentmc.protocol.ChunkTranslator} 参照。</li>
 * </ul>
 *
 * <p>意図的に入れていないもの：
 * <ul>
 *   <li>Legacy Server List Ping（ハンドシェイク 0xFE）— 古い ping 形式。
 *       どのバージョンの表にも載せていない。</li>
 *   <li>Ping Request（プレイ状態・サーバー行き。764は 0x1D、765は 0x1E、767は 0x21、768は 0x23、769は 0x24、770は 0x24、771は 0x25、772は 0x25、773は 0x25、774は 0x25、775は 0x26）—
 *       クライアント発の ping 要求。763にも無い。プロキシは落とす。</li>
 *   <li>Status 状態（サーバーリストの ping）— 参加フローとは別系統のため
 *       この段階では対象外。</li>
 *   <li>プレイ状態の Registry Data — 763のプレイ状態には存在しない
 *       （minecraft-data 1.20 の play toClient に registry_data は無い）。
 *       763向けのレジストリは Join Game の dimension codec に入れる
 *       （ {@link io.detergentmc.protocol.PlayTranslator} 参照）。765の
 *       設定フェーズの Registry Data をプレイ状態のIDに付け替えて送る
 *       のは誤りなので、この表には載せていない。</li>
 *   <li>Named Entity Spawn — 763の 0x03。765では Spawn Entity に統合
 *       され、バニラが765で送ることは無いので載せていない。</li>
 * </ul>
 *
 * <p>存在しない組み合わせは -1（＝空）。765にだけあるパケット
 * （Chunk Batch、Set Ticking State、Reset Score など）や、763に
 * 相当するものが無いもの（Start Configuration）も -1 で載せてある。
 * 764にだけ無いもの（Reset Score、Set Ticking State、Step Tick、
 * Add／Remove Resource Pack、Set Slot State）も -1 である。
 * 766にだけあるもの（Cookie Request、Transfer、Select Known Packs、
 * Chat Command Signed、Debug Sample、Set Projectile Power、Reset Chat、
 * Store Cookie）も -1 である。766で削除された resource_pack_send
 * （設定・プレイ）は 766 だけ -1 である。
 * -1 のものはプロキシがそのバージョン向けに落とす（「名前が分からない」ではなく
 * 「そのバージョンに存在しない／変換できない」としてログに出る）。
 * なお Boss Bar、Teams、Advancements、Respawn、Explosion、
 * Tab Complete は担当3で変換を実装したため -1 ではない
 * （ {@link io.detergentmc.protocol.ExtraPlayTranslator} 参照）。
 *
 * <p>第2段階で追加したエントリ（「追加分」「追加分2」）は、参加直後〜基本プレイで
 * バニラが送る／受け取るパケットで、ペイロードが両バージョンで同じ
 * もの（ID付け替えのみで通せるもの）と、 {@link PacketTranslator} や
 * {@link io.detergentmc.protocol.PlayTranslator} がペイロード変換する
 * ものに限る。テキストコンポーネントを NBT で送る765と JSON 文字列で
 * 送る763の違いがあるもの（System Chat、Player Chat、Disconnect、
 * Server Data、Action Bar、Playerlist Header、タイトル類、Player Info
 * の displayName、Profileless Chat、Death Combat Event、Scoreboard
 * Objective／Score）は PlayTranslator が変換する。Boss Bar、Teams、
 * Advancements、Respawn、Explosion、Tab Complete は担当3で
 * {@link io.detergentmc.protocol.ExtraPlayTranslator} が変換する。
 * ただしアイテムID・ブロック状態ID・音ID・パーティクルID・レシピ種別など
 * レジストリ番号で送られるものは、1.20.3で追加された分だけ番号がずれる
 * 可能性がある（素通しのため）。チャンクデータの状態IDリマップと合わせて
 * 第3段階以降の課題。
 *
 * <p>注意：この表は「IDの付け替え」だけを扱う。同じ名前でもペイロードの
 * 中身が違うパケットがある（例：チャットの署名まわり、リソースパックの
 * UUID有無、設定フェーズの有無）。IDを付け替えただけでは遊べないので、
 * ペイロードの変換は {@link PacketTranslator} の注意書きとプロキシ担当の
 * 実装で行うこと。
 */
public final class PacketIds {

    private PacketIds() {
    }

    /** 接続の状態。 */
    public enum State {
        HANDSHAKING,
        LOGIN,
        CONFIGURATION,
        PLAY
    }

    /** パケットの向き。 */
    public enum Direction {
        /** サーバーからクライアントへ。 */
        CLIENTBOUND,
        /** クライアントからサーバーへ。 */
        SERVERBOUND
    }

    /**
     * パケット名。1つの名前は1つの状態と1つの方向に属する。
     * IDはプロトコルごとに違い、存在しない組み合わせは -1（＝空）になる。
     */
    public enum PacketName {

        // ---- Handshaking（サーバー行きのみ） ----
        /** Handshake。両バージョンとも 0x00。 */
        HANDSHAKE(State.HANDSHAKING, Direction.SERVERBOUND, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),

        // ---- Login：クライアント行き（サーバー→クライアント） ----
        /** Disconnect (login)。 */
        LOGIN_DISCONNECT(State.LOGIN, Direction.CLIENTBOUND, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        /** Encryption Request。 */
        LOGIN_ENCRYPTION_REQUEST(State.LOGIN, Direction.CLIENTBOUND, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01),
        /** Login Success。 */
        LOGIN_SUCCESS(State.LOGIN, Direction.CLIENTBOUND, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02),
        /** Set Compression。 */
        LOGIN_SET_COMPRESSION(State.LOGIN, Direction.CLIENTBOUND, -1, -1, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03),
        /** Login Plugin Request。 */
        LOGIN_PLUGIN_REQUEST(State.LOGIN, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
                0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
                0x04, 0x04, 0x04, 0x04),
        /** Cookie Request (login)。766で追加。ログイン時のクッキー要求。 */
        LOGIN_COOKIE_REQUEST(State.LOGIN, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05),

        // ---- Login：サーバー行き（クライアント→サーバー） ----
        /** Login Start。 */
        LOGIN_START(State.LOGIN, Direction.SERVERBOUND, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        /** Encryption Response。 */
        LOGIN_ENCRYPTION_RESPONSE(State.LOGIN, Direction.SERVERBOUND, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01),
        /** Login Plugin Response。 */
        LOGIN_PLUGIN_RESPONSE(State.LOGIN, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x02, 0x02, 0x02),
        /** Login Acknowledged。765（1.20.2以降）で追加。763には無い。 */
        LOGIN_ACKNOWLEDGED(State.LOGIN, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x03,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03),
        /** Cookie Response (login)。766で追加。 */
        LOGIN_COOKIE_RESPONSE(State.LOGIN, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04),

        // ---- Configuration：クライアント行き（サーバー→クライアント。765のみ。763には状態自体が無い） ----
        /** Cookie Request (configuration)。766で追加。 */
        CONFIG_COOKIE_REQUEST(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        /** Clientbound Plugin Message (configuration)。 */
        CONFIG_PLUGIN_MESSAGE(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x00, 0x00, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01),
        /** Disconnect (configuration)。 */
        CONFIG_DISCONNECT(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x01, 0x01, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02),
        /** Finish Configuration。 */
        CONFIG_FINISH_CONFIGURATION(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03),
        /** Clientbound Keep Alive (configuration)。 */
        CONFIG_KEEP_ALIVE(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x03, 0x03, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04),
        /** Ping (configuration)。 */
        CONFIG_PING(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x04, 0x04, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05),
        /** Registry Data。 */
        CONFIG_REGISTRY_DATA(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x05, 0x05, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07),
        /** Reset Chat。766で追加。チャット履歴のリセット。 */
        CONFIG_RESET_CHAT(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06),
        /** Remove Resource Pack (configuration)。UUID付き。765のみ。764は単一の resource_pack_send を使う。 */
        CONFIG_REMOVE_RESOURCE_PACK(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x06, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08),
        /** Add Resource Pack (configuration)。UUID付き。765のみ。764は単一の resource_pack_send を使う。 */
        CONFIG_ADD_RESOURCE_PACK(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x07, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09),
        /** Store Cookie (configuration)。766で追加。 */
        CONFIG_STORE_COOKIE(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a),
        /** Transfer (configuration)。766で追加。別サーバーへの転送。 */
        CONFIG_TRANSFER(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b),
        /** Resource Pack Send (configuration)。764のみの単一パケット（UUIDなし）。
         * 765は Add／Remove Resource Pack に分かれている。763には設定フェーズ自体が無い。
         * ペイロードは URL＋SHA-1＋forced＋任意プロンプト（JSON 文字列）で、763の
         * プレイ状態の旧式オファー（{@link #PLAY_RESOURCE_PACK}）と同じ形。 */
        CONFIG_RESOURCE_PACK_SEND(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x06, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Feature Flags。 */
        CONFIG_FEATURE_FLAGS(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x07, 0x08, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c),
        /** Update Tags (configuration)。 */
        CONFIG_TAGS(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x08, 0x09, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d),
        /** Select Known Packs (configuration)。766で追加。既知のデータパックの選択。 */
        CONFIG_SELECT_KNOWN_PACKS(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e),
        /** Custom Report Details (configuration)。767で追加。クラッシュレポート用の詳細情報の要求。 */
        CONFIG_CUSTOM_REPORT_DETAILS(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f),
        /** Server Links (configuration)。767で追加。サーバーが提示するリンク一覧。 */
        CONFIG_SERVER_LINKS(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10),
        /** Clear Dialog。771で追加（ダイアログを閉じる）。 */
        CONFIG_CLEAR_DIALOG(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11),
        /** Show Dialog。771で追加（ダイアログを表示する）。 */
        CONFIG_SHOW_DIALOG(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12),
        /** Code of Conduct。773で追加（設定フェーズの行動規範の表示）。 */
        CONFIG_CODE_OF_CONDUCT(State.CONFIGURATION, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x13, 0x13, 0x13, 0x13),

        // ---- Configuration：サーバー行き（クライアント→サーバー。765のみ） ----
        /** Client Information (configuration)。 */
        CONFIG_CLIENT_INFORMATION(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        /** Cookie Response (configuration)。766で追加。 */
        CONFIG_COOKIE_RESPONSE(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01),
        /** Serverbound Plugin Message (configuration)。 */
        CONFIG_PLUGIN_MESSAGE_TO_SERVER(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x01, 0x01, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02),
        /** Acknowledge Finish Configuration。 */
        CONFIG_ACKNOWLEDGE_FINISH_CONFIGURATION(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03,
                0x03),
        /** Serverbound Keep Alive (configuration)。 */
        CONFIG_KEEP_ALIVE_TO_SERVER(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x03, 0x03, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04),
        /** Pong (configuration)。 */
        CONFIG_PONG(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x04, 0x04, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05),
        /** Resource Pack Response (configuration)。UUID付き。 */
        CONFIG_RESOURCE_PACK_RESPONSE(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x05, 0x05, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06),
        /** Select Known Packs (configuration, serverbound)。766で追加。 */
        CONFIG_SELECT_KNOWN_PACKS_TO_SERVER(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07),
        /** Custom Report Details (configuration, serverbound)。767で追加。クラッシュレポート用の詳細情報の応答。 */
        CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x08, 0x08, 0x08, 0x08, -1, -1, -1, -1, -1, -1),
        /** Server Links (configuration, serverbound)。767で追加。サーバーリンクへの応答。 */
        CONFIG_SERVER_LINKS_TO_SERVER(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, 0x09, 0x09, 0x09, 0x09, -1, -1, -1, -1, -1, -1),
        /** Custom Click Action。771で追加（minecraft:custom クリックイベントの送信）。 */
        CONFIG_CUSTOM_CLICK_ACTION_TO_SERVER(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08),
        /** Accept Code of Conduct。773で追加（行動規範への同意）。 */
        CONFIG_ACCEPT_CODE_OF_CONDUCT(State.CONFIGURATION, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x09, 0x09, 0x09, 0x09),

        // ---- Play：クライアント行き（サーバー→クライアント） ----
        /** Join Game（minecraft-data 名：login）。 */
        PLAY_JOIN_GAME(State.PLAY, Direction.CLIENTBOUND, 0x01, 0x01, 0x01, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23,
                0x23, 0x23, 0x23, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25, 0x26, 0x26, 0x26, 0x25, 0x25, 0x24,
                0x24, 0x24, 0x26, 0x26, 0x26, 0x26, 0x23, 0x25, 0x24, 0x28, 0x28, 0x29, 0x29, 0x2b, 0x2b, 0x2c, 0x2c,
                0x2b, 0x2b, 0x2b, 0x30, 0x30, 0x31, 0x31),
        /** Disconnect (play)。 */
        PLAY_DISCONNECT(State.PLAY, Direction.CLIENTBOUND, 0x40, 0x40, 0x40, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a,
                0x1a, 0x1a, 0x1a, 0x1b, 0x1b, 0x1b, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1b, 0x1b, 0x1b, 0x1a, 0x1a, 0x19,
                0x19, 0x19, 0x1a, 0x1a, 0x1a, 0x1a, 0x17, 0x19, 0x17, 0x1a, 0x1A, 0x1B, 0x1B, 0x1d, 0x1d, 0x1d, 0x1d,
                0x1c, 0x1c, 0x1c, 0x20, 0x20, 0x20, 0x20),
        /** Set Default Spawn Position。 */
        PLAY_SPAWN_POSITION(State.PLAY, Direction.CLIENTBOUND, 0x05, 0x05, 0x05, 0x43, 0x43, 0x43, 0x43, 0x43, 0x43,
                0x43, 0x45, 0x46, 0x46, 0x49, 0x49, 0x49, 0x4d, 0x4d, 0x4d, 0x4d, 0x4d, 0x4e, 0x4e, 0x4e, 0x42, 0x42,
                0x42, 0x42, 0x42, 0x4b, 0x4b, 0x4b, 0x4b, 0x4a, 0x4d, 0x4c, 0x50, 0x50, 0x52, 0x54, 0x56, 0x56, 0x5b,
                0x5b, 0x5a, 0x5a, 0x5a, 0x5f, 0x5f, 0x61, 0x61),
        /** Game Event。765ではイベント13（チャンク待ち開始）がスポーンの合図。763ではデフォルトスポーン位置パケットが合図になる（ClientAdapter参照）。 */
        PLAY_GAME_EVENT(State.PLAY, Direction.CLIENTBOUND, 0x2b, 0x2b, 0x2b, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e,
                0x1e, 0x1e, 0x1e, 0x20, 0x20, 0x20, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1f, 0x1f, 0x1f, 0x1e, 0x1e, 0x1d,
                0x1d, 0x1d, 0x1e, 0x1e, 0x1e, 0x1e, 0x1b, 0x1d, 0x1c, 0x1f, 0x1F, 0x20, 0x20, 0x22, 0x22, 0x23, 0x23,
                0x22, 0x22, 0x22, 0x26, 0x26, 0x26, 0x26),
        /** Clientbound Keep Alive (play)。 */
        PLAY_KEEP_ALIVE(State.PLAY, Direction.CLIENTBOUND, 0x00, 0x00, 0x00, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f,
                0x1f, 0x1f, 0x1f, 0x21, 0x21, 0x21, 0x20, 0x20, 0x20, 0x20, 0x20, 0x21, 0x21, 0x21, 0x20, 0x20, 0x1f,
                0x1f, 0x1f, 0x21, 0x21, 0x21, 0x21, 0x1e, 0x20, 0x1f, 0x23, 0x23, 0x24, 0x24, 0x26, 0x26, 0x27, 0x27,
                0x26, 0x26, 0x26, 0x2b, 0x2b, 0x2c, 0x2c),
        /** System Chat Message。 */
        PLAY_SYSTEM_CHAT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x5f, 0x62, 0x60, 0x64,
                0x64, 0x67, 0x69, 0x6c, 0x6c, 0x73, 0x73, 0x72, 0x72, 0x72, 0x77, 0x77, 0x79, 0x79),
        /** Chat Message。758 にだけある（署名なしの旧式チャット）。
            759 以降は Player Chat／System Chat に分かれた。 */
        PLAY_CHAT(State.PLAY, Direction.CLIENTBOUND, 0x02, 0x02, 0x02, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f,
                0x0f, 0x0f, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0f, 0x0f, 0x0f, 0x0e, 0x0e, 0x0e, 0x0e,
                0x0e, 0x0f, 0x0f, 0x0f, 0x0f, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Player Chat Message。 */
        PLAY_PLAYER_CHAT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x30, 0x33, 0x31, 0x35,
                0x35, 0x37, 0x37, 0x39, 0x39, 0x3b, 0x3b, 0x3a, 0x3a, 0x3a, 0x3f, 0x3f, 0x41, 0x41),
        /** Message Header。760 にだけある（署名付きチャットの署名部）。
            761 以降は Player Chat に統合。Player Chat の直前に送る。 */
        PLAY_MESSAGE_HEADER(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x32, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Chunk Data and Update Light（minecraft-data 名：map_chunk）。 */
        PLAY_CHUNK_DATA(State.PLAY, Direction.CLIENTBOUND, 0x21, 0x21, 0x21, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x22, 0x22, 0x22, 0x21, 0x21, 0x21, 0x21, 0x21, 0x22, 0x22, 0x22, 0x21, 0x21, 0x20,
                0x20, 0x20, 0x22, 0x22, 0x22, 0x22, 0x1f, 0x21, 0x20, 0x24, 0x24, 0x25, 0x25, 0x27, 0x27, 0x28, 0x28,
                0x27, 0x27, 0x27, 0x2c, 0x2c, 0x2d, 0x2d),
        /** Chunk Data Bulk（クライアント行き）。47 にだけある
            （1.9 で廃止され、初期チャンクは個別の Chunk Data で届く
            ようになった）。47 バックエンドからはこれを個別チャンクへ
            分解して正準形に直す。 */
        PLAY_MAP_CHUNK_BULK(State.PLAY, Direction.CLIENTBOUND, 0x26, 0x26, 0x26, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Set Compression（プレイ状態・クライアント行き）。47 に
            だけある（ログイン状態の LOGIN_SET_COMPRESSION とは別）。
            47 ではプレイ中にも圧縮しきい値の変更が来うる。 */
        PLAY_SET_COMPRESSION(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x46, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Block Change。 */
        PLAY_BLOCK_CHANGE(State.PLAY, Direction.CLIENTBOUND, 0x23, 0x23, 0x23, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
                0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0c, 0x0c, 0x0c, 0x0b, 0x0b,
                0x0b, 0x0b, 0x0b, 0x0c, 0x0c, 0x0c, 0x0c, 0x09, 0x09, 0x09, 0x0a, 0x0A, 0x09, 0x09, 0x09, 0x09, 0x09,
                0x09, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08),
        /** Player Info Update。 */
        PLAY_PLAYER_INFO_UPDATE(State.PLAY, Direction.CLIENTBOUND, 0x38, 0x38, 0x38, 0x2d, 0x2d, 0x2d, 0x2d, 0x2d,
                0x2d, 0x2d, 0x2d, 0x2e, 0x2e, 0x30, 0x30, 0x30, 0x33, 0x33, 0x33, 0x33, 0x33, 0x34, 0x34, 0x34, 0x33,
                0x33, 0x32, 0x32, 0x32, 0x36, 0x36, 0x36, 0x36, 0x34, 0x37, 0x36, 0x3a, 0x3A, 0x3C, 0x3C, 0x3e, 0x3e,
                0x40, 0x40, 0x3f, 0x3f, 0x3f, 0x44, 0x44, 0x46, 0x46),
        /** Should Display Chat Preview。760 にだけある（1.19.3 で廃止）。
            クライアント行き。プレビュー表示の可否を送る。 */
        PLAY_SHOULD_DISPLAY_CHAT_PREVIEW(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x4b,
                0x4e, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Player Info Remove。 */
        PLAY_PLAYER_INFO_REMOVE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x35, 0x39,
                0x39, 0x3B, 0x3B, 0x3d, 0x3d, 0x3f, 0x3f, 0x3e, 0x3e, 0x3e, 0x43, 0x43, 0x45, 0x45),
        /** Resource Pack Send。763のプレイ状態での旧式オファー（UUIDなし）。765には無い。 */
        PLAY_RESOURCE_PACK(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x48, 0x32, 0x32, 0x32, 0x32, 0x32, 0x32, 0x32,
                0x33, 0x34, 0x34, 0x37, 0x37, 0x37, 0x39, 0x39, 0x39, 0x39, 0x39, 0x3a, 0x3a, 0x3a, 0x39, 0x39, 0x38,
                0x38, 0x38, 0x3c, 0x3c, 0x3c, 0x3c, 0x3a, 0x3d, 0x3c, 0x40, 0x40, 0x42, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1),
        /** Add Resource Pack (play)。UUID付き。765のみ。 */
        PLAY_ADD_RESOURCE_PACK(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x44, 0x46, 0x46, 0x4b, 0x4b, 0x4a, 0x4a, 0x4a, 0x4f, 0x4f, 0x51, 0x51),
        /** Remove Resource Pack (play)。UUID付き。765のみ。 */
        PLAY_REMOVE_RESOURCE_PACK(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x43, 0x45, 0x45, 0x4a, 0x4a, 0x49, 0x49, 0x49, 0x4e, 0x4e, 0x50, 0x50),
        /** Clientbound Plugin Message (play)。 */
        PLAY_PLUGIN_MESSAGE(State.PLAY, Direction.CLIENTBOUND, 0x3f, 0x3f, 0x3f, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
                0x18, 0x18, 0x18, 0x18, 0x19, 0x19, 0x19, 0x18, 0x18, 0x18, 0x18, 0x18, 0x19, 0x19, 0x19, 0x18, 0x18,
                0x17, 0x17, 0x17, 0x18, 0x18, 0x18, 0x18, 0x15, 0x16, 0x15, 0x17, 0x17, 0x18, 0x18, 0x19, 0x19, 0x19,
                0x19, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18),
        /** Synchronize Player Position（サーバー→クライアントの位置同期）。 */
        PLAY_SYNCHRONIZE_PLAYER_POSITION(State.PLAY, Direction.CLIENTBOUND, 0x08, 0x08, 0x08, 0x2e, 0x2e, 0x2e, 0x2e,
                0x2e, 0x2e, 0x2e, 0x2e, 0x2f, 0x2f, 0x32, 0x32, 0x32, 0x35, 0x35, 0x35, 0x35, 0x35, 0x36, 0x36, 0x36,
                0x35, 0x35, 0x34, 0x34, 0x34, 0x38, 0x38, 0x38, 0x38, 0x36, 0x39, 0x38, 0x3c, 0x3C, 0x3E, 0x3E, 0x40,
                0x40, 0x42, 0x42, 0x41, 0x41, 0x41, 0x46, 0x46, 0x48, 0x48),

        // ---- Play：クライアント行き（追加分：第2段階。ペイロードが両バージョンで同じものと、PlayTranslator が変換するもの） ----
        /** Spawn Entity。ペイロードは両バージョンで同じ。 */
        PLAY_SPAWN_ENTITY(State.PLAY, Direction.CLIENTBOUND, 0x0e, 0x0e, 0x0e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01),
        /** Spawn Entity Living。758 にだけある（生き物の出現）。
            759 以降は Spawn Entity に統合。 */
        PLAY_SPAWN_ENTITY_LIVING(State.PLAY, Direction.CLIENTBOUND, 0x0f, 0x0f, 0x0f, 0x03, 0x03, 0x03, 0x03, 0x03,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x02,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1),
        /** Spawn Entity Painting。758 にだけある（絵画の出現）。
            759 以降は Spawn Entity に統合。 */
        PLAY_SPAWN_ENTITY_PAINTING(State.PLAY, Direction.CLIENTBOUND, 0x10, 0x10, 0x10, 0x04, 0x04, 0x04, 0x04, 0x04,
                0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x03,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1),
        /** Sculk Vibration Signal。758 にだけある（スカルク振動の信号）。
            759 で廃止され、振動は Game Event などで送られる。 */
        PLAY_SCULK_VIBRATION_SIGNAL(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x05, 0x05, 0x05, 0x05, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Named Entity Spawn。758〜763 にある（プレイヤーの出現）。764 以降の Spawn Entity とは別パケット。 */
        PLAY_NAMED_ENTITY_SPAWN(State.PLAY, Direction.CLIENTBOUND, 0x0c, 0x0c, 0x0c, 0x05, 0x05, 0x05, 0x05, 0x05,
                0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x04,
                0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x02, 0x02, 0x02, 0x03, 0x03, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1),
        /** Difficulty。u8＋boolで同じ。 */
        PLAY_DIFFICULTY(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x41, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d,
                0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0e, 0x0e, 0x0e, 0x0d, 0x0d, 0x0d,
                0x0d, 0x0d, 0x0e, 0x0e, 0x0e, 0x0e, 0x0b, 0x0b, 0x0b, 0x0c, 0x0C, 0x0B, 0x0B, 0x0b, 0x0b, 0x0b, 0x0b,
                0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a),
        /** Chunk Biomes。 */
        PLAY_CHUNK_BIOMES(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x0d, 0x0D,
                0x0E, 0x0E, 0x0e, 0x0e, 0x0e, 0x0e, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d),
        /** Declare Commands。brigadier の木構造は両バージョンで同じ。 */
        PLAY_DECLARE_COMMANDS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12, 0x12, 0x12, 0x11, 0x11, 0x10, 0x10, 0x10, 0x12,
                0x12, 0x12, 0x12, 0x0f, 0x0f, 0x0e, 0x10, 0x10, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x10, 0x10, 0x10,
                0x10, 0x10, 0x10, 0x10),
        /** Unload Chunk。chunkZ＋chunkX（i32）で同じ。 */
        PLAY_UNLOAD_CHUNK(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d,
                0x1d, 0x1d, 0x1d, 0x1f, 0x1f, 0x1f, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1e, 0x1e, 0x1e, 0x1d, 0x1d, 0x1c,
                0x1c, 0x1c, 0x1d, 0x1d, 0x1d, 0x1d, 0x1a, 0x1c, 0x1b, 0x1e, 0x1E, 0x1F, 0x1F, 0x21, 0x21, 0x22, 0x22,
                0x21, 0x21, 0x21, 0x25, 0x25, 0x25, 0x25),
        /** Update Light。 */
        PLAY_UPDATE_LIGHT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x24, 0x24, 0x24, 0x24, 0x24, 0x25, 0x25, 0x25, 0x24, 0x24, 0x23, 0x23, 0x23, 0x25, 0x25,
                0x25, 0x25, 0x22, 0x24, 0x23, 0x27, 0x27, 0x28, 0x28, 0x2a, 0x2a, 0x2b, 0x2b, 0x2a, 0x2a, 0x2a, 0x2f,
                0x2f, 0x30, 0x30),
        /** Abilities。i8＋f32＋f32で同じ。 */
        PLAY_ABILITIES(State.PLAY, Direction.CLIENTBOUND, 0x39, 0x39, 0x39, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b,
                0x2b, 0x2c, 0x2c, 0x2e, 0x2e, 0x2e, 0x31, 0x31, 0x31, 0x31, 0x31, 0x32, 0x32, 0x32, 0x31, 0x31, 0x30,
                0x30, 0x30, 0x32, 0x32, 0x32, 0x32, 0x2f, 0x31, 0x30, 0x34, 0x34, 0x36, 0x36, 0x38, 0x38, 0x3a, 0x3a,
                0x39, 0x39, 0x39, 0x3e, 0x3e, 0x40, 0x40),
        /** Entity Destroy。VarInt配列で同じ。 */
        PLAY_ENTITY_DESTROY(State.PLAY, Direction.CLIENTBOUND, 0x13, 0x13, 0x13, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
                0x30, 0x31, 0x32, 0x32, 0x35, 0x35, 0x35, 0x37, 0x37, 0x37, 0x37, 0x37, 0x38, 0x38, 0x38, 0x37, 0x37,
                0x36, 0x36, 0x36, 0x3a, 0x3a, 0x3a, 0x3a, 0x38, 0x3b, 0x3a, 0x3e, 0x3E, 0x40, 0x40, 0x42, 0x42, 0x47,
                0x47, 0x46, 0x46, 0x46, 0x4b, 0x4b, 0x4d, 0x4d),
        /** Remove Entity Effect。VarInt×2で同じ。 */
        PLAY_REMOVE_ENTITY_EFFECT(State.PLAY, Direction.CLIENTBOUND, 0x1e, 0x1e, 0x1e, 0x31, 0x31, 0x31, 0x31, 0x31,
                0x31, 0x31, 0x32, 0x33, 0x33, 0x36, 0x36, 0x36, 0x38, 0x38, 0x38, 0x38, 0x38, 0x39, 0x39, 0x39, 0x38,
                0x38, 0x37, 0x37, 0x37, 0x3b, 0x3b, 0x3b, 0x3b, 0x39, 0x3c, 0x3b, 0x3f, 0x3F, 0x41, 0x41, 0x43, 0x43,
                0x48, 0x48, 0x47, 0x47, 0x47, 0x4c, 0x4c, 0x4e, 0x4e),
        /** Entity Head Rotation。VarInt＋i8で同じ。 */
        PLAY_ENTITY_HEAD_ROTATION(State.PLAY, Direction.CLIENTBOUND, 0x19, 0x19, 0x19, 0x34, 0x34, 0x34, 0x34, 0x34,
                0x34, 0x34, 0x35, 0x36, 0x36, 0x39, 0x39, 0x39, 0x3b, 0x3b, 0x3b, 0x3b, 0x3b, 0x3c, 0x3c, 0x3c, 0x3b,
                0x3b, 0x3a, 0x3a, 0x3a, 0x3e, 0x3e, 0x3e, 0x3e, 0x3c, 0x3f, 0x3e, 0x42, 0x42, 0x44, 0x46, 0x48, 0x48,
                0x4d, 0x4d, 0x4c, 0x4c, 0x4c, 0x51, 0x51, 0x53, 0x53),
        /** Multi Block Change。 */
        PLAY_MULTI_BLOCK_CHANGE(State.PLAY, Direction.CLIENTBOUND, 0x22, 0x22, 0x22, 0x10, 0x10, 0x10, 0x10, 0x10,
                0x10, 0x10, 0x10, 0x10, 0x10, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x10, 0x10, 0x10, 0x0f,
                0x0f, 0x3b, 0x3b, 0x3b, 0x3f, 0x3f, 0x3f, 0x3f, 0x3d, 0x40, 0x3f, 0x43, 0x43, 0x45, 0x47, 0x49, 0x49,
                0x4e, 0x4e, 0x4d, 0x4d, 0x4d, 0x52, 0x52, 0x54, 0x54),
        /** Server Data。765は motd が NBT、763は JSON 文字列。PlayTranslator が変換する。 */
        PLAY_SERVER_DATA(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3f, 0x42, 0x41, 0x45,
                0x45, 0x47, 0x49, 0x4b, 0x4b, 0x50, 0x50, 0x4f, 0x4f, 0x4f, 0x54, 0x54, 0x56, 0x56),
        /** Action Bar。765は NBT、763は JSON 文字列。PlayTranslator が変換する。 */
        PLAY_ACTION_BAR(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x4f, 0x4f, 0x4f, 0x4f, 0x4f, 0x41, 0x41, 0x41, 0x41, 0x40, 0x43,
                0x42, 0x46, 0x46, 0x48, 0x4A, 0x4c, 0x4c, 0x51, 0x51, 0x50, 0x50, 0x50, 0x55, 0x55, 0x57, 0x57),
        /** World Border Center。f64×2で同じ。 */
        PLAY_WORLD_BORDER_CENTER(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x42, 0x42, 0x42, 0x42,
                0x41, 0x44, 0x43, 0x47, 0x47, 0x49, 0x4B, 0x4d, 0x4d, 0x52, 0x52, 0x51, 0x51, 0x51, 0x56, 0x56, 0x58,
                0x58),
        /** World Border Lerp Size。f64×2＋VarLongで同じ。 */
        PLAY_WORLD_BORDER_LERP_SIZE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x43, 0x43, 0x43, 0x43,
                0x42, 0x45, 0x44, 0x48, 0x48, 0x4A, 0x4C, 0x4e, 0x4e, 0x53, 0x53, 0x52, 0x52, 0x52, 0x57, 0x57, 0x59,
                0x59),
        /** World Border Size。f64で同じ。 */
        PLAY_WORLD_BORDER_SIZE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x44, 0x44, 0x44, 0x44,
                0x43, 0x46, 0x45, 0x49, 0x49, 0x4B, 0x4D, 0x4f, 0x4f, 0x54, 0x54, 0x53, 0x53, 0x53, 0x58, 0x58, 0x5a,
                0x5a),
        /** World Border Warning Delay。VarIntで同じ。 */
        PLAY_WORLD_BORDER_WARNING_DELAY(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x45, 0x45, 0x45,
                0x45, 0x44, 0x47, 0x46, 0x4a, 0x4A, 0x4C, 0x4E, 0x50, 0x50, 0x55, 0x55, 0x54, 0x54, 0x54, 0x59, 0x59,
                0x5b, 0x5b),
        /** World Border Warning Reach。VarIntで同じ。 */
        PLAY_WORLD_BORDER_WARNING_REACH(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x46, 0x46, 0x46,
                0x46, 0x45, 0x48, 0x47, 0x4b, 0x4B, 0x4D, 0x4F, 0x51, 0x51, 0x56, 0x56, 0x55, 0x55, 0x55, 0x5a, 0x5a,
                0x5c, 0x5c),
        /** Held Item Slot。VarIntで同じ。 */
        PLAY_HELD_ITEM_SLOT(State.PLAY, Direction.CLIENTBOUND, 0x09, 0x09, 0x09, 0x37, 0x37, 0x37, 0x37, 0x37, 0x37,
                0x37, 0x39, 0x3a, 0x3a, 0x3d, 0x3d, 0x3d, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f, 0x40, 0x40, 0x40, 0x3f, 0x3f,
                0x3f, 0x3f, 0x3f, 0x48, 0x48, 0x48, 0x48, 0x47, 0x4a, 0x49, 0x4d, 0x4D, 0x4F, 0x51, 0x53, 0x53, 0x63,
                0x63, 0x62, 0x62, 0x62, 0x67, 0x67, 0x69, 0x69),
        /** Update View Position（Set Center Chunk）。VarInt×2で同じ。 */
        PLAY_UPDATE_VIEW_POSITION(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x40, 0x40, 0x40, 0x40, 0x40, 0x41, 0x41, 0x41, 0x40, 0x40, 0x40, 0x40, 0x40, 0x49,
                0x49, 0x49, 0x49, 0x48, 0x4b, 0x4a, 0x4e, 0x4E, 0x50, 0x52, 0x54, 0x54, 0x58, 0x58, 0x57, 0x57, 0x57,
                0x5c, 0x5c, 0x5e, 0x5e),
        /** Update View Distance。VarIntで同じ。 */
        PLAY_UPDATE_VIEW_DISTANCE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x41, 0x41, 0x41, 0x41, 0x41, 0x42, 0x42, 0x42, 0x41, 0x41, 0x41, 0x41, 0x41, 0x4a,
                0x4a, 0x4a, 0x4a, 0x49, 0x4c, 0x4b, 0x4f, 0x4F, 0x51, 0x53, 0x55, 0x55, 0x59, 0x59, 0x58, 0x58, 0x58,
                0x5d, 0x5d, 0x5f, 0x5f),
        /** Entity Velocity。VarInt＋i16×3で同じ。 */
        PLAY_ENTITY_VELOCITY(State.PLAY, Direction.CLIENTBOUND, 0x12, 0x12, 0x12, 0x3b, 0x3b, 0x3b, 0x3b, 0x3b, 0x3b,
                0x3b, 0x3d, 0x3e, 0x3e, 0x41, 0x41, 0x41, 0x45, 0x45, 0x45, 0x45, 0x45, 0x46, 0x46, 0x46, 0x46, 0x46,
                0x46, 0x46, 0x46, 0x4f, 0x4f, 0x4f, 0x4f, 0x4f, 0x52, 0x50, 0x54, 0x54, 0x56, 0x58, 0x5a, 0x5a, 0x5f,
                0x5f, 0x5e, 0x5e, 0x5e, 0x63, 0x63, 0x65, 0x65),
        /** Experience。f32＋VarInt＋VarIntで同じ。 */
        PLAY_EXPERIENCE(State.PLAY, Direction.CLIENTBOUND, 0x1f, 0x1f, 0x1f, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d,
                0x3f, 0x40, 0x40, 0x43, 0x43, 0x43, 0x47, 0x47, 0x47, 0x47, 0x47, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48,
                0x48, 0x48, 0x51, 0x51, 0x51, 0x51, 0x51, 0x54, 0x52, 0x56, 0x56, 0x58, 0x5A, 0x5c, 0x5c, 0x61, 0x61,
                0x60, 0x60, 0x60, 0x65, 0x65, 0x67, 0x67),
        /** Update Health。f32＋VarInt＋f32で同じ。 */
        PLAY_UPDATE_HEALTH(State.PLAY, Direction.CLIENTBOUND, 0x06, 0x06, 0x06, 0x3e, 0x3e, 0x3e, 0x3e, 0x3e, 0x3e,
                0x3e, 0x40, 0x41, 0x41, 0x44, 0x44, 0x44, 0x48, 0x48, 0x48, 0x48, 0x48, 0x49, 0x49, 0x49, 0x49, 0x49,
                0x49, 0x49, 0x49, 0x52, 0x52, 0x52, 0x52, 0x52, 0x55, 0x53, 0x57, 0x57, 0x59, 0x5B, 0x5d, 0x5d, 0x62,
                0x62, 0x61, 0x61, 0x61, 0x66, 0x66, 0x68, 0x68),
        /** Simulation Distance。VarIntで同じ。 */
        PLAY_SIMULATION_DISTANCE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x57, 0x57, 0x57, 0x5a,
                0x58, 0x5c, 0x5C, 0x5E, 0x60, 0x62, 0x62, 0x69, 0x69, 0x68, 0x68, 0x68, 0x6d, 0x6d, 0x6f, 0x6f),
        /** Set Title Subtitle。765は NBT、763は JSON 文字列。PlayTranslator が変換する。 */
        PLAY_SET_TITLE_SUBTITLE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x4f, 0x4f, 0x4f, 0x4f, 0x4f, 0x57, 0x57, 0x58, 0x58,
                0x58, 0x5b, 0x59, 0x5d, 0x5D, 0x5F, 0x61, 0x63, 0x63, 0x6a, 0x6a, 0x69, 0x69, 0x69, 0x6e, 0x6e, 0x70,
                0x70),
        /** Set Title Text。765は NBT、763は JSON 文字列。PlayTranslator が変換する。 */
        PLAY_SET_TITLE_TEXT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x4f, 0x4f, 0x4f, 0x4f, 0x4f, 0x59, 0x59, 0x5a, 0x5a, 0x5a,
                0x5d, 0x5b, 0x5f, 0x5F, 0x61, 0x63, 0x65, 0x65, 0x6c, 0x6c, 0x6b, 0x6b, 0x6b, 0x70, 0x70, 0x72, 0x72),
        /** Set Title Time。i32×3で同じ。 */
        PLAY_SET_TITLE_TIME(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x4f, 0x4f, 0x4f, 0x4f, 0x4f, 0x5a, 0x5a, 0x5b, 0x5b, 0x5b,
                0x5e, 0x5c, 0x60, 0x60, 0x62, 0x64, 0x66, 0x66, 0x6d, 0x6d, 0x6c, 0x6c, 0x6c, 0x71, 0x71, 0x73, 0x73),
        /** Update Time。i64×2＋boolで同じ。 */
        PLAY_UPDATE_TIME(State.PLAY, Direction.CLIENTBOUND, 0x03, 0x03, 0x03, 0x44, 0x44, 0x44, 0x44, 0x44, 0x44,
                0x44, 0x46, 0x47, 0x47, 0x4a, 0x4a, 0x4a, 0x4e, 0x4e, 0x4e, 0x4e, 0x4e, 0x4f, 0x4f, 0x4f, 0x4e, 0x4e,
                0x4e, 0x4e, 0x4e, 0x58, 0x58, 0x59, 0x59, 0x59, 0x5c, 0x5a, 0x5e, 0x5E, 0x60, 0x62, 0x64, 0x64, 0x6b,
                0x6b, 0x6a, 0x6a, 0x6a, 0x6f, 0x6f, 0x71, 0x71),
        /** Playerlist Header。765は NBT×2、763は JSON 文字列×2。PlayTranslator が変換する。 */
        PLAY_PLAYERLIST_HEADER(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x47, 0x48, 0x48, 0x48, 0x47, 0x47, 0x47,
                0x47, 0x49, 0x4a, 0x4a, 0x4e, 0x4e, 0x4e, 0x53, 0x53, 0x53, 0x53, 0x53, 0x54, 0x54, 0x54, 0x53, 0x53,
                0x53, 0x53, 0x53, 0x5e, 0x5e, 0x5f, 0x5f, 0x60, 0x63, 0x61, 0x65, 0x65, 0x68, 0x6A, 0x6d, 0x6d, 0x74,
                0x74, 0x73, 0x73, 0x73, 0x78, 0x78, 0x7a, 0x7a),
        /** Entity Teleport。f64×3＋i8×2＋boolで同じ。 */
        PLAY_ENTITY_TELEPORT(State.PLAY, Direction.CLIENTBOUND, 0x18, 0x18, 0x18, 0x4a, 0x4a, 0x4a, 0x49, 0x49, 0x49,
                0x49, 0x4b, 0x4c, 0x4c, 0x50, 0x50, 0x50, 0x56, 0x56, 0x56, 0x56, 0x56, 0x57, 0x57, 0x57, 0x56, 0x56,
                0x56, 0x56, 0x56, 0x61, 0x61, 0x62, 0x62, 0x63, 0x66, 0x64, 0x68, 0x68, 0x6B, 0x6D, 0x70, 0x70, 0x77,
                0x77, 0x76, 0x76, 0x76, 0x7b, 0x7b, 0x7d, 0x7d),
        /** Entity Effect。VarInt＋VarInt＋i8＋VarInt＋i8＋任意NBTで同じ。 */
        PLAY_ENTITY_EFFECT(State.PLAY, Direction.CLIENTBOUND, 0x1d, 0x1d, 0x1d, 0x4c, 0x4c, 0x4c, 0x4b, 0x4b, 0x4b,
                0x4b, 0x4e, 0x4f, 0x4f, 0x53, 0x53, 0x53, 0x59, 0x59, 0x59, 0x59, 0x59, 0x5a, 0x5a, 0x5a, 0x59, 0x59,
                0x59, 0x59, 0x59, 0x64, 0x64, 0x65, 0x65, 0x66, 0x69, 0x68, 0x6c, 0x6C, 0x6E, 0x72, 0x76, 0x76, 0x7d,
                0x7d, 0x7d, 0x7d, 0x7d, 0x82, 0x82, 0x84, 0x84),
        /** Test Instance Block Status。770で追加（テストインスタンスブロックの状態通知）。 */
        PLAY_TEST_INSTANCE_BLOCK_STATUS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, 0x77, 0x77, 0x77, 0x7c, 0x7c, 0x7e, 0x7e),
        /** Tracked Waypoint。771で追加（ロケーターバーのプレイヤー位置表示）。 */
        PLAY_TRACKED_WAYPOINT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, 0x83, 0x83, 0x88, 0x88, 0x8a, 0x8a),
        /** Clear Dialog。771で追加（ダイアログを閉じる）。 */
        PLAY_CLEAR_DIALOG(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, 0x84, 0x84, 0x89, 0x89, 0x8b, 0x8b),
        /** Show Dialog。771で追加（ダイアログを表示する）。 */
        PLAY_SHOW_DIALOG(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, 0x85, 0x85, 0x8a, 0x8a, 0x8c, 0x8c),

        // ---- Play：クライアント行き（追加分2：実機バニラとの統合テストで見つかった不足分） ----
        /** Bundle Delimiter。ペイロード無し。 */
        PLAY_BUNDLE_DELIMITER(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        /** Spawn Experience Orb。 */
        PLAY_SPAWN_EXPERIENCE_ORB(State.PLAY, Direction.CLIENTBOUND, 0x11, 0x11, 0x11, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x02, -1, -1, -1, -1, -1, -1, -1),
        /** Animation。 */
        PLAY_ANIMATION(State.PLAY, Direction.CLIENTBOUND, 0x0b, 0x0b, 0x0b, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06,
                0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x05, 0x05, 0x05,
                0x05, 0x05, 0x06, 0x06, 0x06, 0x06, 0x03, 0x03, 0x03, 0x04, 0x04, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02),
        /** Statistics。統計IDはレジストリ番号のため、新統計は番号がずれる可能性がある。 */
        PLAY_STATISTICS(State.PLAY, Direction.CLIENTBOUND, 0x37, 0x37, 0x37, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
                0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x06, 0x06, 0x06,
                0x06, 0x06, 0x07, 0x07, 0x07, 0x07, 0x04, 0x04, 0x04, 0x05, 0x05, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03),
        /** Acknowledge Player Digging。 */
        PLAY_ACKNOWLEDGE_PLAYER_DIGGING(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x5c, 0x08, 0x08, 0x08, 0x07, 0x07, 0x07, 0x07, 0x07, 0x08, 0x08,
                0x08, 0x08, 0x05, 0x05, 0x05, 0x06, 0x06, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x04, 0x04, 0x04, 0x04,
                0x04, 0x04, 0x04),
        /** Block Break Animation。 */
        PLAY_BLOCK_BREAK_ANIMATION(State.PLAY, Direction.CLIENTBOUND, 0x25, 0x25, 0x25, 0x08, 0x08, 0x08, 0x08, 0x08,
                0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x09, 0x09, 0x09, 0x08,
                0x08, 0x08, 0x08, 0x08, 0x09, 0x09, 0x09, 0x09, 0x06, 0x06, 0x06, 0x07, 0x07, 0x06, 0x06, 0x06, 0x06,
                0x06, 0x06, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05),
        /** Block Entity Data。種別はレジストリ番号のため、新種別は番号がずれる可能性がある。 */
        PLAY_BLOCK_ENTITY_DATA(State.PLAY, Direction.CLIENTBOUND, 0x35, 0x35, 0x35, 0x09, 0x09, 0x09, 0x09, 0x09,
                0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x0a, 0x0a, 0x0a, 0x09,
                0x09, 0x09, 0x09, 0x09, 0x0a, 0x0a, 0x0a, 0x0a, 0x07, 0x07, 0x07, 0x08, 0x08, 0x07, 0x07, 0x07, 0x07,
                0x07, 0x07, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06),
        /** Block Action。 */
        PLAY_BED(State.PLAY, Direction.CLIENTBOUND, 0x0a, 0x0a, 0x0a, 0x2f, 0x2f, 0x2f, 0x2f, 0x2f, 0x2f, 0x2f, 0x2f,
                0x30, 0x30, 0x33, 0x33, 0x33, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Block Action。 */
        PLAY_BLOCK_ACTION(State.PLAY, Direction.CLIENTBOUND, 0x24, 0x24, 0x24, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a,
                0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0b, 0x0b, 0x0b, 0x0a, 0x0a,
                0x0a, 0x0a, 0x0a, 0x0b, 0x0b, 0x0b, 0x0b, 0x08, 0x08, 0x08, 0x09, 0x09, 0x08, 0x08, 0x08, 0x08, 0x08,
                0x08, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07),
        /** Boss Bar。765はタイトルが NBT、763は JSON 文字列。担当3で変換を実装（ExtraPlayTranslator）。 */
        PLAY_BOSS_BAR(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c,
                0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0d, 0x0d, 0x0d, 0x0c, 0x0c, 0x0c, 0x0c,
                0x0c, 0x0d, 0x0d, 0x0d, 0x0d, 0x0a, 0x0a, 0x0a, 0x0b, 0x0B, 0x0A, 0x0A, 0x0a, 0x0a, 0x0a, 0x0a, 0x09,
                0x09, 0x09, 0x09, 0x09, 0x09, 0x09),
        /** Chunk Batch Finished。765のみ。 */
        PLAY_CHUNK_BATCH_FINISHED(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x0C, 0x0C, 0x0c, 0x0c, 0x0c, 0x0c, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b),
        /** Chunk Batch Start。765のみ。 */
        PLAY_CHUNK_BATCH_START(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x0D, 0x0D, 0x0d, 0x0d, 0x0d, 0x0d, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c),
        /** Clear Titles。boolで同じ。 */
        PLAY_CLEAR_TITLES(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x4f, 0x4f, 0x4f, 0x4f, 0x4f, 0x10, 0x10, 0x10, 0x10, 0x0d,
                0x0d, 0x0c, 0x0e, 0x0E, 0x0F, 0x0F, 0x0f, 0x0f, 0x0f, 0x0f, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e),
        /** Chat Preview。760 にだけある（1.19.3 でチャットプレビュー廃止）。
            クライアント行き。サーバーはプレビューの内容を送る。 */
        PLAY_CHAT_PREVIEW(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x0c, 0x0c, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Tab Complete。765はツールチップが NBT、763は JSON 文字列。担当3で変換を実装（ExtraPlayTranslator）。 */
        PLAY_TAB_COMPLETE(State.PLAY, Direction.CLIENTBOUND, 0x3a, 0x3a, 0x3a, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e,
                0x0e, 0x0e, 0x0e, 0x0e, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x11, 0x11, 0x11, 0x10, 0x10,
                0x0f, 0x0f, 0x0f, 0x11, 0x11, 0x11, 0x11, 0x0e, 0x0e, 0x0d, 0x0f, 0x0F, 0x10, 0x10, 0x10, 0x10, 0x10,
                0x10, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f),
        /** Close Window。u8で同じ。 */
        PLAY_CLOSE_WINDOW(State.PLAY, Direction.CLIENTBOUND, 0x2e, 0x2e, 0x2e, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12,
                0x12, 0x12, 0x12, 0x12, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x14, 0x14, 0x14, 0x13, 0x13,
                0x12, 0x12, 0x12, 0x13, 0x13, 0x13, 0x13, 0x10, 0x10, 0x0f, 0x11, 0x11, 0x12, 0x12, 0x12, 0x12, 0x12,
                0x12, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11),
        /** Window Items。アイテムIDはレジストリ番号のため、新アイテムは番号がずれる可能性がある。 */
        PLAY_WINDOW_ITEMS(State.PLAY, Direction.CLIENTBOUND, 0x30, 0x30, 0x30, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14,
                0x14, 0x14, 0x14, 0x14, 0x15, 0x15, 0x15, 0x14, 0x14, 0x14, 0x14, 0x14, 0x15, 0x15, 0x15, 0x14, 0x14,
                0x13, 0x13, 0x13, 0x14, 0x14, 0x14, 0x14, 0x11, 0x11, 0x10, 0x12, 0x12, 0x13, 0x13, 0x13, 0x13, 0x13,
                0x13, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12),
        /** Set Container Property。 */
        PLAY_SET_CONTAINER_PROPERTY(State.PLAY, Direction.CLIENTBOUND, 0x31, 0x31, 0x31, 0x15, 0x15, 0x15, 0x15, 0x15,
                0x15, 0x15, 0x15, 0x15, 0x15, 0x16, 0x16, 0x16, 0x15, 0x15, 0x15, 0x15, 0x15, 0x16, 0x16, 0x16, 0x15,
                0x15, 0x14, 0x14, 0x14, 0x15, 0x15, 0x15, 0x15, 0x12, 0x12, 0x11, 0x13, 0x13, 0x14, 0x14, 0x14, 0x14,
                0x14, 0x14, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13),
        /** Set Slot。アイテムIDの注意は Window Items と同じ。 */
        PLAY_SET_SLOT(State.PLAY, Direction.CLIENTBOUND, 0x2f, 0x2f, 0x2f, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16,
                0x16, 0x16, 0x16, 0x17, 0x17, 0x17, 0x16, 0x16, 0x16, 0x16, 0x16, 0x17, 0x17, 0x17, 0x16, 0x16, 0x15,
                0x15, 0x15, 0x16, 0x16, 0x16, 0x16, 0x13, 0x13, 0x12, 0x14, 0x14, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15,
                0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14),
        /** Cookie Request (play)。766で追加。 */
        PLAY_COOKIE_REQUEST(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x16, 0x16, 0x16, 0x16, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15),
        /** Set Cooldown。アイテムIDの注意は Window Items と同じ。 */
        PLAY_SET_COOLDOWN(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17,
                0x17, 0x17, 0x17, 0x18, 0x18, 0x18, 0x17, 0x17, 0x17, 0x17, 0x17, 0x18, 0x18, 0x18, 0x17, 0x17, 0x16,
                0x16, 0x16, 0x17, 0x17, 0x17, 0x17, 0x14, 0x14, 0x13, 0x15, 0x15, 0x16, 0x16, 0x17, 0x17, 0x17, 0x17,
                0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16),
        /** Chat Suggestions。 */
        PLAY_CHAT_SUGGESTIONS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x15, 0x14, 0x16,
                0x16, 0x17, 0x17, 0x18, 0x18, 0x18, 0x18, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17),
        /** Damage Event。 */
        PLAY_DAMAGE_EVENT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x18, 0x18,
                0x19, 0x19, 0x1a, 0x1a, 0x1a, 0x1a, 0x19, 0x19, 0x19, 0x19, 0x19, 0x19, 0x19),
        /** Debug Sample。766で追加。デバッグ用のサンプルデータ。 */
        PLAY_DEBUG_SAMPLE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x1b, 0x1b, 0x1b, 0x1b, 0x1a, 0x1a, 0x1a, 0x1e, 0x1e, 0x1e, 0x1e),
        /** Debug Block Value。773で追加（デバッグ購読のブロック値）。 */
        PLAY_DEBUG_BLOCK_VALUE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1a, 0x1a, 0x1a, 0x1a),
        /** Debug Chunk Value。773で追加（デバッグ購読のチャンク値）。 */
        PLAY_DEBUG_CHUNK_VALUE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1b, 0x1b, 0x1b, 0x1b),
        /** Debug Entity Value。773で追加（デバッグ購読のエンティティ値）。 */
        PLAY_DEBUG_ENTITY_VALUE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1c, 0x1c, 0x1c, 0x1c),
        /** Debug Event。773で追加（デバッグ購読のイベント）。 */
        PLAY_DEBUG_EVENT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, 0x1d, 0x1d, 0x1d, 0x1d),
        /** Game Test Highlight Pos。773で追加（ゲームテストの位置ハイライト）。 */
        PLAY_GAME_TEST_HIGHLIGHT_POS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x27, 0x27, 0x28, 0x28),
        /** Hide Message。 */
        PLAY_HIDE_MESSAGE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x18, 0x16, 0x19,
                0x19, 0x1A, 0x1A, 0x1c, 0x1c, 0x1c, 0x1c, 0x1b, 0x1b, 0x1b, 0x1f, 0x1f, 0x1f, 0x1f),
        /** Profileless Chat。765は NBT、763は JSON 文字列。PlayTranslator が変換する。 */
        PLAY_PROFILELESS_CHAT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x18, 0x1b,
                0x1B, 0x1C, 0x1C, 0x1e, 0x1e, 0x1e, 0x1e, 0x1d, 0x1d, 0x1d, 0x21, 0x21, 0x21, 0x21),
        /** Entity Status。i32＋i8で同じ。 */
        PLAY_ENTITY_STATUS(State.PLAY, Direction.CLIENTBOUND, 0x1a, 0x1a, 0x1a, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b,
                0x1b, 0x1b, 0x1b, 0x1b, 0x1c, 0x1c, 0x1c, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1c, 0x1c, 0x1c, 0x1b, 0x1b,
                0x1a, 0x1a, 0x1a, 0x1b, 0x1b, 0x1b, 0x1b, 0x18, 0x1a, 0x19, 0x1c, 0x1C, 0x1D, 0x1D, 0x1f, 0x1f, 0x1f,
                0x1f, 0x1e, 0x1e, 0x1e, 0x22, 0x22, 0x22, 0x22),
        /** Explosion。765の末尾（block_interaction_type・粒子・音）は763に無い。担当3で共通部分だけ通す（ExtraPlayTranslator）。 */
        PLAY_EXPLOSION(State.PLAY, Direction.CLIENTBOUND, 0x27, 0x27, 0x27, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c,
                0x1c, 0x1c, 0x1c, 0x1e, 0x1e, 0x1e, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1d, 0x1d, 0x1d, 0x1c, 0x1c, 0x1b,
                0x1b, 0x1b, 0x1c, 0x1c, 0x1c, 0x1c, 0x19, 0x1b, 0x1a, 0x1d, 0x1D, 0x1E, 0x1E, 0x20, 0x20, 0x21, 0x21,
                0x20, 0x20, 0x20, 0x24, 0x24, 0x24, 0x24),
        /** Synchronize Entity Position。768で追加。エンティティの位置・速度・向きの同期。 */
        PLAY_SYNC_ENTITY_POSITION(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x20, 0x20, 0x1f, 0x1f, 0x1f, 0x23, 0x23, 0x23, 0x23),
        /** Move Minecart。768で追加。トロッコの移動補間。 */
        PLAY_MOVE_MINECART(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x31, 0x31, 0x30, 0x30, 0x30, 0x35, 0x35, 0x37, 0x37),
        /** Player Rotation。768で追加。サーバーからの強制回転。 */
        PLAY_PLAYER_ROTATION(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x43, 0x43, 0x42, 0x42, 0x42, 0x47, 0x47, 0x49, 0x49),
        /** Set Cursor Item。768で追加。カーソルに持つアイテムの設定。 */
        PLAY_SET_CURSOR_ITEM(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x5a, 0x5a, 0x59, 0x59, 0x59, 0x5e, 0x5e, 0x60, 0x60),
        /** Set Player Inventory。768で追加。プレイヤーインベントリの1スロットの設定。 */
        PLAY_SET_PLAYER_INVENTORY(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x66, 0x66, 0x65, 0x65, 0x65, 0x6a, 0x6a, 0x6c, 0x6c),
        /** Open Horse Window。 */
        PLAY_OPEN_HORSE_WINDOW(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x20, 0x20, 0x20, 0x1f, 0x1f, 0x1e, 0x1e, 0x1e, 0x1f, 0x1f,
                0x1f, 0x1f, 0x1c, 0x1e, 0x1d, 0x20, 0x20, 0x21, 0x21, 0x23, 0x23, 0x24, 0x24, 0x23, 0x23, 0x23, 0x28,
                0x28, 0x29, 0x29),
        /** Hurt Animation。 */
        PLAY_HURT_ANIMATION(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x21, 0x21,
                0x22, 0x22, 0x24, 0x24, 0x25, 0x25, 0x24, 0x24, 0x24, 0x29, 0x29, 0x2a, 0x2a),
        /** Initialize World Border。 */
        PLAY_INITIALIZE_WORLD_BORDER(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x20, 0x20, 0x20,
                0x20, 0x1d, 0x1f, 0x1e, 0x22, 0x22, 0x23, 0x23, 0x25, 0x25, 0x26, 0x26, 0x25, 0x25, 0x25, 0x2a, 0x2a,
                0x2b, 0x2b),
        /** World Event。 */
        PLAY_WORLD_EVENT(State.PLAY, Direction.CLIENTBOUND, 0x28, 0x28, 0x28, 0x21, 0x21, 0x21, 0x21, 0x21, 0x21,
                0x21, 0x21, 0x21, 0x21, 0x23, 0x23, 0x23, 0x22, 0x22, 0x22, 0x22, 0x22, 0x23, 0x23, 0x23, 0x22, 0x22,
                0x21, 0x21, 0x21, 0x23, 0x23, 0x23, 0x23, 0x20, 0x22, 0x21, 0x25, 0x25, 0x26, 0x26, 0x28, 0x28, 0x29,
                0x29, 0x28, 0x28, 0x28, 0x2d, 0x2d, 0x2e, 0x2e),
        /** World Particles。パーティクルIDはレジストリ番号のため、新パーティクルは番号がずれる可能性がある。 */
        PLAY_WORLD_PARTICLES(State.PLAY, Direction.CLIENTBOUND, 0x2a, 0x2a, 0x2a, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22,
                0x22, 0x22, 0x22, 0x22, 0x24, 0x24, 0x24, 0x23, 0x23, 0x23, 0x23, 0x23, 0x24, 0x24, 0x24, 0x23, 0x23,
                0x22, 0x22, 0x22, 0x24, 0x24, 0x24, 0x24, 0x21, 0x23, 0x22, 0x26, 0x26, 0x27, 0x27, 0x29, 0x29, 0x2a,
                0x2a, 0x29, 0x29, 0x29, 0x2e, 0x2e, 0x2f, 0x2f),
        /** Map。 */
        PLAY_MAP(State.PLAY, Direction.CLIENTBOUND, 0x34, 0x34, 0x34, 0x24, 0x24, 0x24, 0x24, 0x24, 0x24, 0x24, 0x24,
                0x24, 0x24, 0x26, 0x26, 0x26, 0x26, 0x26, 0x26, 0x26, 0x26, 0x27, 0x27, 0x27, 0x26, 0x26, 0x25, 0x25,
                0x25, 0x27, 0x27, 0x27, 0x27, 0x24, 0x26, 0x25, 0x29, 0x29, 0x2A, 0x2A, 0x2c, 0x2c, 0x2d, 0x2d, 0x2c,
                0x2c, 0x2c, 0x31, 0x31, 0x33, 0x33),
        /** Trade List。アイテムIDの注意は Window Items と同じ。 */
        PLAY_TRADE_LIST(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x27, 0x27, 0x27, 0x27, 0x27, 0x28, 0x28, 0x28, 0x27, 0x27, 0x26, 0x26, 0x26, 0x28, 0x28, 0x28,
                0x28, 0x25, 0x27, 0x26, 0x2a, 0x2A, 0x2B, 0x2B, 0x2d, 0x2d, 0x2e, 0x2e, 0x2d, 0x2d, 0x2d, 0x32, 0x32,
                0x34, 0x34),
        /** Rel Entity Move。 */
        PLAY_REL_ENTITY_MOVE(State.PLAY, Direction.CLIENTBOUND, 0x15, 0x15, 0x15, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25,
                0x25, 0x26, 0x26, 0x26, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x29, 0x29, 0x29, 0x28, 0x28,
                0x27, 0x27, 0x27, 0x29, 0x29, 0x29, 0x29, 0x26, 0x28, 0x27, 0x2b, 0x2B, 0x2C, 0x2C, 0x2e, 0x2e, 0x2f,
                0x2f, 0x2e, 0x2e, 0x2e, 0x33, 0x33, 0x35, 0x35),
        /** Entity Move Look。 */
        PLAY_ENTITY_MOVE_LOOK(State.PLAY, Direction.CLIENTBOUND, 0x17, 0x17, 0x17, 0x26, 0x26, 0x26, 0x26, 0x26, 0x26,
                0x26, 0x27, 0x27, 0x27, 0x29, 0x29, 0x29, 0x29, 0x29, 0x29, 0x29, 0x29, 0x2a, 0x2a, 0x2a, 0x29, 0x29,
                0x28, 0x28, 0x28, 0x2a, 0x2a, 0x2a, 0x2a, 0x27, 0x29, 0x28, 0x2c, 0x2C, 0x2D, 0x2D, 0x2f, 0x2f, 0x30,
                0x30, 0x2f, 0x2f, 0x2f, 0x34, 0x34, 0x36, 0x36),
        /** Entity Look。 */
        PLAY_ENTITY_LOOK(State.PLAY, Direction.CLIENTBOUND, 0x16, 0x16, 0x16, 0x27, 0x27, 0x27, 0x27, 0x27, 0x27,
                0x27, 0x28, 0x28, 0x28, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2b, 0x2b, 0x2b, 0x2a, 0x2a,
                0x29, 0x29, 0x29, 0x2b, 0x2b, 0x2b, 0x2b, 0x28, 0x2a, 0x29, 0x2d, 0x2D, 0x2E, 0x2E, 0x30, 0x30, 0x32,
                0x32, 0x31, 0x31, 0x31, 0x36, 0x36, 0x38, 0x38),
        /** Vehicle Move。 */
        PLAY_VEHICLE_MOVE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, 0x29, 0x29, 0x29, 0x29, 0x29, 0x29, 0x29,
                0x29, 0x29, 0x29, 0x2b, 0x2b, 0x2b, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2d, 0x2d, 0x2d, 0x2c, 0x2c, 0x2b,
                0x2b, 0x2b, 0x2c, 0x2c, 0x2c, 0x2c, 0x29, 0x2b, 0x2a, 0x2e, 0x2E, 0x2F, 0x2F, 0x31, 0x31, 0x33, 0x33,
                0x32, 0x32, 0x32, 0x37, 0x37, 0x39, 0x39),
        /** Open Book。 */
        PLAY_OPEN_BOOK(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x2d, 0x2d, 0x2d, 0x2d, 0x2d, 0x2e, 0x2e, 0x2e, 0x2d, 0x2d, 0x2c, 0x2c, 0x2c, 0x2d, 0x2d, 0x2d,
                0x2d, 0x2a, 0x2c, 0x2b, 0x2f, 0x2F, 0x30, 0x30, 0x32, 0x32, 0x34, 0x34, 0x33, 0x33, 0x33, 0x38, 0x38,
                0x3a, 0x3a),
        /** Open Window。タイトルは両バージョンとも JSON 文字列。 */
        PLAY_OPEN_WINDOW(State.PLAY, Direction.CLIENTBOUND, 0x2d, 0x2d, 0x2d, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13,
                0x13, 0x13, 0x13, 0x13, 0x14, 0x14, 0x14, 0x2e, 0x2e, 0x2e, 0x2e, 0x2e, 0x2f, 0x2f, 0x2f, 0x2e, 0x2e,
                0x2d, 0x2d, 0x2d, 0x2e, 0x2e, 0x2e, 0x2e, 0x2b, 0x2d, 0x2c, 0x30, 0x30, 0x31, 0x31, 0x33, 0x33, 0x35,
                0x35, 0x34, 0x34, 0x34, 0x39, 0x39, 0x3b, 0x3b),
        /** Open Sign Editor。 */
        PLAY_OPEN_SIGN_EDITOR(State.PLAY, Direction.CLIENTBOUND, 0x36, 0x36, 0x36, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a,
                0x2a, 0x2a, 0x2a, 0x2a, 0x2c, 0x2c, 0x2c, 0x2f, 0x2f, 0x2f, 0x2f, 0x2f, 0x30, 0x30, 0x30, 0x2f, 0x2f,
                0x2e, 0x2e, 0x2e, 0x2f, 0x2f, 0x2f, 0x2f, 0x2c, 0x2e, 0x2d, 0x31, 0x31, 0x32, 0x32, 0x34, 0x34, 0x36,
                0x36, 0x35, 0x35, 0x35, 0x3a, 0x3a, 0x3c, 0x3c),
        /** Ping。i32で同じ。 */
        PLAY_PING(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, 0x12, 0x12, 0x11, 0x11, 0x11, 0x30, 0x30, 0x30, 0x30, 0x2d, 0x2f,
                0x2e, 0x32, 0x32, 0x33, 0x33, 0x35, 0x35, 0x37, 0x37, 0x36, 0x36, 0x36, 0x3b, 0x3b, 0x3d, 0x3d),
        /** Ping Response。765のみ（763クライアントは Ping Request を送らない）。 */
        PLAY_PING_RESPONSE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x34,
                0x34, 0x36, 0x36, 0x38, 0x38, 0x37, 0x37, 0x37, 0x3c, 0x3c, 0x3e, 0x3e),
        /** Craft Recipe Response。 */
        PLAY_CRAFT_RECIPE_RESPONSE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x2b, 0x2b, 0x2d, 0x2d, 0x2d, 0x30, 0x30, 0x30, 0x30, 0x30, 0x31, 0x31, 0x31, 0x30, 0x30, 0x2f, 0x2f,
                0x2f, 0x31, 0x31, 0x31, 0x31, 0x2e, 0x30, 0x2f, 0x33, 0x33, 0x35, 0x35, 0x37, 0x37, 0x39, 0x39, 0x38,
                0x38, 0x38, 0x3d, 0x3d, 0x3f, 0x3f),
        /** End Combat Event。 */
        PLAY_END_COMBAT_EVENT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x32, 0x32, 0x31, 0x31, 0x31, 0x33, 0x33, 0x33, 0x33,
                0x31, 0x34, 0x32, 0x36, 0x36, 0x38, 0x38, 0x3a, 0x3a, 0x3c, 0x3c, 0x3b, 0x3b, 0x3b, 0x40, 0x40, 0x42,
                0x42),
        /** Enter Combat Event。ペイロード無し。 */
        PLAY_ENTER_COMBAT_EVENT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x32, 0x32, 0x31, 0x31, 0x31, 0x34, 0x34, 0x34, 0x34,
                0x32, 0x35, 0x33, 0x37, 0x37, 0x39, 0x39, 0x3b, 0x3b, 0x3d, 0x3d, 0x3c, 0x3c, 0x3c, 0x41, 0x41, 0x43,
                0x43),
        /** Death Combat Event。765はメッセージが NBT、763は JSON 文字列。PlayTranslator が変換する。 */
        PLAY_DEATH_COMBAT_EVENT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x32, 0x32, 0x31, 0x31, 0x31, 0x35, 0x35, 0x35, 0x35,
                0x33, 0x36, 0x34, 0x38, 0x38, 0x3A, 0x3A, 0x3c, 0x3c, 0x3e, 0x3e, 0x3d, 0x3d, 0x3d, 0x42, 0x42, 0x44,
                0x44),
        /** Face Player。 */
        PLAY_FACE_PLAYER(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x31,
                0x31, 0x31, 0x34, 0x34, 0x34, 0x34, 0x34, 0x35, 0x35, 0x35, 0x34, 0x34, 0x33, 0x33, 0x33, 0x37, 0x37,
                0x37, 0x37, 0x35, 0x38, 0x37, 0x3b, 0x3B, 0x3D, 0x3D, 0x3f, 0x3f, 0x41, 0x41, 0x40, 0x40, 0x40, 0x45,
                0x45, 0x47, 0x47),
        /** Unlock Recipes。768で削除され、Recipe Book Add／Remove／Settings に分かれた。 */
        PLAY_UNLOCK_RECIPES(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x30, 0x31,
                0x31, 0x34, 0x34, 0x34, 0x36, 0x36, 0x36, 0x36, 0x36, 0x37, 0x37, 0x37, 0x36, 0x36, 0x35, 0x35, 0x35,
                0x39, 0x39, 0x39, 0x39, 0x37, 0x3a, 0x39, 0x3d, 0x3D, 0x3F, 0x3F, 0x41, 0x41, -1, -1, -1, -1, -1, -1,
                -1, -1, -1),
        /** Recipe Book Add。768で追加（Unlock Recipes の後継）。 */
        PLAY_RECIPE_BOOK_ADD(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x44, 0x44, 0x43, 0x43, 0x43, 0x48, 0x48, 0x4a, 0x4a),
        /** Recipe Book Remove。768で追加。 */
        PLAY_RECIPE_BOOK_REMOVE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x45, 0x45, 0x44, 0x44, 0x44, 0x49, 0x49, 0x4b, 0x4b),
        /** Recipe Book Settings。768で追加。 */
        PLAY_RECIPE_BOOK_SETTINGS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x46, 0x46, 0x45, 0x45, 0x45, 0x4a, 0x4a, 0x4c, 0x4c),
        /** Reset Score。765のみ。763の Scoreboard Score（削除）に変換する（PlayTranslator 参照）。 */
        PLAY_RESET_SCORE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x42, 0x44, 0x44, 0x49, 0x49, 0x48, 0x48, 0x48, 0x4d, 0x4d, 0x4f, 0x4f),
        /** Respawn。765と763で copyMetadata の位置が違う。担当3で変換を実装（ExtraPlayTranslator）。 */
        PLAY_RESPAWN(State.PLAY, Direction.CLIENTBOUND, 0x07, 0x07, 0x07, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33,
                0x34, 0x35, 0x35, 0x38, 0x38, 0x38, 0x3a, 0x3a, 0x3a, 0x3a, 0x3a, 0x3b, 0x3b, 0x3b, 0x3a, 0x3a, 0x39,
                0x39, 0x39, 0x3d, 0x3d, 0x3d, 0x3d, 0x3b, 0x3e, 0x3d, 0x41, 0x41, 0x43, 0x45, 0x47, 0x47, 0x4c, 0x4c,
                0x4b, 0x4b, 0x4b, 0x50, 0x50, 0x52, 0x52),
        /** Select Advancement Tab。 */
        PLAY_SELECT_ADVANCEMENT_TAB(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x36,
                0x37, 0x37, 0x3a, 0x3a, 0x3a, 0x3c, 0x3c, 0x3c, 0x3c, 0x3c, 0x3d, 0x3d, 0x3d, 0x3c, 0x3c, 0x3c, 0x3c,
                0x3c, 0x40, 0x40, 0x40, 0x40, 0x3e, 0x41, 0x40, 0x44, 0x44, 0x46, 0x48, 0x4a, 0x4a, 0x4f, 0x4f, 0x4e,
                0x4e, 0x4e, 0x53, 0x53, 0x55, 0x55),
        /** Camera。 */
        PLAY_CAMERA(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x43, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x38,
                0x39, 0x39, 0x3c, 0x3c, 0x3c, 0x3e, 0x3e, 0x3e, 0x3e, 0x3e, 0x3f, 0x3f, 0x3f, 0x3e, 0x3e, 0x3e, 0x3e,
                0x3e, 0x47, 0x47, 0x47, 0x47, 0x46, 0x49, 0x48, 0x4c, 0x4C, 0x4E, 0x50, 0x52, 0x52, 0x57, 0x57, 0x56,
                0x56, 0x56, 0x5b, 0x5b, 0x5d, 0x5d),
        /** Scoreboard Display Objective。 */
        PLAY_SCOREBOARD_DISPLAY_OBJECTIVE(State.PLAY, Direction.CLIENTBOUND, 0x3d, 0x3d, 0x3d, 0x38, 0x38, 0x38, 0x38,
                0x38, 0x38, 0x38, 0x3a, 0x3b, 0x3b, 0x3e, 0x3e, 0x3e, 0x42, 0x42, 0x42, 0x42, 0x42, 0x43, 0x43, 0x43,
                0x43, 0x43, 0x43, 0x43, 0x43, 0x4c, 0x4c, 0x4c, 0x4c, 0x4c, 0x4f, 0x4d, 0x51, 0x51, 0x53, 0x55, 0x57,
                0x57, 0x5c, 0x5c, 0x5b, 0x5b, 0x5b, 0x60, 0x60, 0x62, 0x62),
        /** Entity Metadata。メタデータの型は両バージョンで同じ。 */
        PLAY_ENTITY_METADATA(State.PLAY, Direction.CLIENTBOUND, 0x1c, 0x1c, 0x1c, 0x39, 0x39, 0x39, 0x39, 0x39, 0x39,
                0x39, 0x3b, 0x3c, 0x3c, 0x3f, 0x3f, 0x3f, 0x43, 0x43, 0x43, 0x43, 0x43, 0x44, 0x44, 0x44, 0x44, 0x44,
                0x44, 0x44, 0x44, 0x4d, 0x4d, 0x4d, 0x4d, 0x4d, 0x50, 0x4e, 0x52, 0x52, 0x54, 0x56, 0x58, 0x58, 0x5d,
                0x5d, 0x5c, 0x5c, 0x5c, 0x61, 0x61, 0x63, 0x63),
        /** Update Entity NBT（クライアント行き）。47 にだけある
            （1.9 で廃止）。エンティティの NBT を丸ごと差し替える
            パケットで、正準形（758）に対応物が無いため変換では
            落とす。 */
        PLAY_UPDATE_ENTITY_NBT(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x49, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Attach Entity。 */
        PLAY_ATTACH_ENTITY(State.PLAY, Direction.CLIENTBOUND, 0x1b, 0x1b, 0x1b, 0x3a, 0x3a, 0x3a, 0x3a, 0x3a, 0x3a,
                0x3a, 0x3c, 0x3d, 0x3d, 0x40, 0x40, 0x40, 0x44, 0x44, 0x44, 0x44, 0x44, 0x45, 0x45, 0x45, 0x45, 0x45,
                0x45, 0x45, 0x45, 0x4e, 0x4e, 0x4e, 0x4e, 0x4e, 0x51, 0x4f, 0x53, 0x53, 0x55, 0x57, 0x59, 0x59, 0x5e,
                0x5e, 0x5d, 0x5d, 0x5d, 0x62, 0x62, 0x64, 0x64),
        /** Entity Equipment。アイテムIDの注意は Window Items と同じ。 */
        PLAY_ENTITY_EQUIPMENT(State.PLAY, Direction.CLIENTBOUND, 0x04, 0x04, 0x04, 0x3c, 0x3c, 0x3c, 0x3c, 0x3c, 0x3c,
                0x3c, 0x3e, 0x3f, 0x3f, 0x42, 0x42, 0x42, 0x46, 0x46, 0x46, 0x46, 0x46, 0x47, 0x47, 0x47, 0x47, 0x47,
                0x47, 0x47, 0x47, 0x50, 0x50, 0x50, 0x50, 0x50, 0x53, 0x51, 0x55, 0x55, 0x57, 0x59, 0x5b, 0x5b, 0x60,
                0x60, 0x5f, 0x5f, 0x5f, 0x64, 0x64, 0x66, 0x66),
        /** Scoreboard Objective。765は表示名が NBT、763は JSON 文字列。numberFormat（765のみ）は落とす。PlayTranslator が変換する。 */
        PLAY_SCOREBOARD_OBJECTIVE(State.PLAY, Direction.CLIENTBOUND, 0x3b, 0x3b, 0x3b, 0x3f, 0x3f, 0x3f, 0x3f, 0x3f,
                0x3f, 0x3f, 0x41, 0x42, 0x42, 0x45, 0x45, 0x45, 0x49, 0x49, 0x49, 0x49, 0x49, 0x4a, 0x4a, 0x4a, 0x4a,
                0x4a, 0x4a, 0x4a, 0x4a, 0x53, 0x53, 0x53, 0x53, 0x53, 0x56, 0x54, 0x58, 0x58, 0x5A, 0x5C, 0x5e, 0x5e,
                0x64, 0x64, 0x63, 0x63, 0x63, 0x68, 0x68, 0x6a, 0x6a),
        /** Set Passengers。 */
        PLAY_SET_PASSENGERS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40,
                0x42, 0x43, 0x43, 0x46, 0x46, 0x46, 0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 0x4b, 0x4b, 0x4b, 0x4b, 0x4b, 0x4b,
                0x4b, 0x4b, 0x54, 0x54, 0x54, 0x54, 0x54, 0x57, 0x55, 0x59, 0x59, 0x5B, 0x5D, 0x5f, 0x5f, 0x65, 0x65,
                0x64, 0x64, 0x64, 0x69, 0x69, 0x6b, 0x6b),
        /** Teams。765は表示名・接頭辞・接尾辞が NBT、763は JSON 文字列。担当3で変換を実装（ExtraPlayTranslator）。 */
        PLAY_TEAMS(State.PLAY, Direction.CLIENTBOUND, 0x3e, 0x3e, 0x3e, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x43, 0x44, 0x44, 0x47, 0x47, 0x47, 0x4b, 0x4b, 0x4b, 0x4b, 0x4b, 0x4c, 0x4c, 0x4c, 0x4c, 0x4c, 0x4c,
                0x4c, 0x4c, 0x55, 0x55, 0x55, 0x55, 0x55, 0x58, 0x56, 0x5a, 0x5A, 0x5C, 0x5E, 0x60, 0x60, 0x67, 0x67,
                0x66, 0x66, 0x66, 0x6b, 0x6b, 0x6d, 0x6d),
        /** Scoreboard Score。765は表示名が NBT、763には表示名が無い。numberFormat（765のみ）は落とす。PlayTranslator が変換する。 */
        PLAY_SCOREBOARD_SCORE(State.PLAY, Direction.CLIENTBOUND, 0x3c, 0x3c, 0x3c, 0x42, 0x42, 0x42, 0x42, 0x42, 0x42,
                0x42, 0x44, 0x45, 0x45, 0x48, 0x48, 0x48, 0x4c, 0x4c, 0x4c, 0x4c, 0x4c, 0x4d, 0x4d, 0x4d, 0x4d, 0x4d,
                0x4d, 0x4d, 0x4d, 0x56, 0x56, 0x56, 0x56, 0x56, 0x59, 0x57, 0x5b, 0x5B, 0x5D, 0x5F, 0x61, 0x61, 0x68,
                0x68, 0x67, 0x67, 0x67, 0x6c, 0x6c, 0x6e, 0x6e),
        /** Entity Sound Effect。音IDはレジストリ番号のため、新しい音は番号がずれる可能性がある。 */
        PLAY_ENTITY_SOUND_EFFECT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x50, 0x50, 0x50, 0x50, 0x50, 0x51, 0x51, 0x51, 0x50, 0x50, 0x50, 0x50, 0x50, 0x5b,
                0x5b, 0x5c, 0x5c, 0x5c, 0x5f, 0x5d, 0x61, 0x61, 0x63, 0x65, 0x67, 0x67, 0x6e, 0x6e, 0x6d, 0x6d, 0x6d,
                0x72, 0x72, 0x74, 0x74),
        /** Sound Effect。音IDの注意は Entity Sound Effect と同じ。 */
        PLAY_SOUND_EFFECT(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, 0x47, 0x47, 0x47, 0x46, 0x46, 0x46, 0x46,
                0x48, 0x49, 0x49, 0x4d, 0x4d, 0x4d, 0x51, 0x51, 0x51, 0x51, 0x51, 0x52, 0x52, 0x52, 0x51, 0x51, 0x51,
                0x51, 0x51, 0x5c, 0x5c, 0x5d, 0x5d, 0x5d, 0x60, 0x5e, 0x62, 0x62, 0x64, 0x66, 0x68, 0x68, 0x6f, 0x6f,
                0x6e, 0x6e, 0x6e, 0x73, 0x73, 0x75, 0x75),
        /** Named Sound Effect。760 にだけある（名前で音を鳴らす）。
            761 以降は Sound Effect の名前付きホルダーへ統合。 */
        PLAY_NAMED_SOUND_EFFECT(State.PLAY, Direction.CLIENTBOUND, 0x29, 0x29, 0x29, 0x19, 0x19, 0x19, 0x19, 0x19,
                0x19, 0x19, 0x19, 0x19, 0x19, 0x1a, 0x1a, 0x1a, 0x19, 0x19, 0x19, 0x19, 0x19, 0x1a, 0x1a, 0x1a, 0x19,
                0x19, 0x18, 0x18, 0x18, 0x19, 0x19, 0x19, 0x19, 0x16, 0x17, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1),
        /** Start Configuration。プレイ中に設定フェーズへ戻る要求。763に相当するパケットは無いため -1 のまま。担当3でプロキシがサーバー側の設定フェーズを肩代わりする（ProxySession の再設定処理を参照）。 */
        PLAY_START_CONFIGURATION(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x65, 0x67, 0x69, 0x69, 0x70, 0x70, 0x6f, 0x6f, 0x6f, 0x74, 0x74, 0x76, 0x76),
        /** Stop Sound。 */
        PLAY_STOP_SOUND(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x4c,
                0x4c, 0x4c, 0x52, 0x52, 0x52, 0x52, 0x52, 0x53, 0x53, 0x53, 0x52, 0x52, 0x52, 0x52, 0x52, 0x5d, 0x5d,
                0x5e, 0x5e, 0x5e, 0x61, 0x5f, 0x63, 0x63, 0x66, 0x68, 0x6a, 0x6a, 0x71, 0x71, 0x70, 0x70, 0x70, 0x75,
                0x75, 0x77, 0x77),
        /** Store Cookie (play)。766で追加。 */
        PLAY_STORE_COOKIE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x6b, 0x6b, 0x72, 0x72, 0x71, 0x71, 0x71, 0x76, 0x76, 0x78, 0x78),
        /** NBT Query Response。 */
        PLAY_NBT_QUERY_RESPONSE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x1d, 0x1d, 0x1d, 0x54, 0x54, 0x54, 0x54, 0x54, 0x55, 0x55, 0x55, 0x54, 0x54, 0x54, 0x54, 0x54, 0x5f,
                0x5f, 0x60, 0x60, 0x61, 0x64, 0x62, 0x66, 0x66, 0x69, 0x6B, 0x6e, 0x6e, 0x75, 0x75, 0x74, 0x74, 0x74,
                0x79, 0x79, 0x7b, 0x7b),
        /** Collect。 */
        PLAY_COLLECT(State.PLAY, Direction.CLIENTBOUND, 0x0d, 0x0d, 0x0d, 0x49, 0x49, 0x49, 0x48, 0x48, 0x48, 0x48,
                0x4a, 0x4b, 0x4b, 0x4f, 0x4f, 0x4f, 0x55, 0x55, 0x55, 0x55, 0x55, 0x56, 0x56, 0x56, 0x55, 0x55, 0x55,
                0x55, 0x55, 0x60, 0x60, 0x61, 0x61, 0x62, 0x65, 0x63, 0x67, 0x67, 0x6A, 0x6C, 0x6f, 0x6f, 0x76, 0x76,
                0x75, 0x75, 0x75, 0x7a, 0x7a, 0x7c, 0x7c),
        /** Set Ticking State。765のみ。 */
        PLAY_SET_TICKING_STATE(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x6E, 0x71, 0x71, 0x78, 0x78, 0x78, 0x78, 0x78, 0x7d, 0x7d, 0x7f, 0x7f),
        /** Step Tick。765のみ。 */
        PLAY_STEP_TICK(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x6F,
                0x72, 0x72, 0x79, 0x79, 0x79, 0x79, 0x79, 0x7e, 0x7e, 0x80, 0x80),
        /** Transfer (play)。766で追加。別サーバーへの転送。 */
        PLAY_TRANSFER(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x73, 0x73, 0x7a, 0x7a, 0x7a, 0x7a, 0x7a, 0x7f, 0x7f, 0x81, 0x81),
        /** Advancements。表示情報が NBT のため、担当3で変換を実装（ExtraPlayTranslator）。763では criteria が必須のため空配列を書く。 */
        PLAY_ADVANCEMENTS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x4c, 0x4d, 0x4d,
                0x51, 0x51, 0x51, 0x57, 0x57, 0x57, 0x57, 0x57, 0x58, 0x58, 0x58, 0x57, 0x57, 0x57, 0x57, 0x57, 0x62,
                0x62, 0x63, 0x63, 0x64, 0x67, 0x65, 0x69, 0x69, 0x6C, 0x70, 0x74, 0x74, 0x7b, 0x7b, 0x7b, 0x7b, 0x7b,
                0x80, 0x80, 0x82, 0x82),
        /** Entity Update Attributes。 */
        PLAY_ENTITY_UPDATE_ATTRIBUTES(State.PLAY, Direction.CLIENTBOUND, 0x20, 0x20, 0x20, 0x4b, 0x4b, 0x4b, 0x4a,
                0x4a, 0x4a, 0x4a, 0x4d, 0x4e, 0x4e, 0x52, 0x52, 0x52, 0x58, 0x58, 0x58, 0x58, 0x58, 0x59, 0x59, 0x59,
                0x58, 0x58, 0x58, 0x58, 0x58, 0x63, 0x63, 0x64, 0x64, 0x65, 0x68, 0x66, 0x6a, 0x6A, 0x6D, 0x71, 0x75,
                0x75, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x81, 0x81, 0x83, 0x83),
        /** Feature Flags。761〜763 はプレイ状態、764 以降は設定フェーズ。 */
        PLAY_FEATURE_FLAGS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x67, 0x6b, 0x6b,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Declare Recipes。レシピ種別はレジストリ番号のため、新種別は番号がずれる可能性がある。 */
        PLAY_DECLARE_RECIPES(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x54, 0x54, 0x54, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5b, 0x5b, 0x5b, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x65,
                0x65, 0x66, 0x66, 0x67, 0x6a, 0x69, 0x6d, 0x6D, 0x6F, 0x73, 0x77, 0x77, 0x7e, 0x7e, 0x7e, 0x7e, 0x7e,
                0x83, 0x83, 0x85, 0x85),
        /** Tags。 */
        PLAY_TAGS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x55, 0x55,
                0x55, 0x5b, 0x5b, 0x5b, 0x5b, 0x5b, 0x5c, 0x5c, 0x5c, 0x5b, 0x5b, 0x5b, 0x5b, 0x5b, 0x66, 0x66, 0x67,
                0x67, 0x68, 0x6b, 0x6a, 0x6e, 0x6E, 0x70, 0x74, 0x78, 0x78, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x84, 0x84,
                0x86, 0x86),
        /** Set Projectile Power。766で追加。発射物の威力。 */
        PLAY_SET_PROJECTILE_POWER(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x79, 0x79, 0x80, 0x80, 0x80, 0x80, 0x80, 0x85, 0x85, 0x87, 0x87),
        /** Custom Report Details (play)。767で追加。クラッシュレポート用の詳細情報の要求。 */
        PLAY_CUSTOM_REPORT_DETAILS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x7a, 0x81, 0x81, 0x81, 0x81, 0x81, 0x86, 0x86, 0x88, 0x88),
        /** Server Links (play)。767で追加。サーバーが提示するリンク一覧。 */
        PLAY_SERVER_LINKS(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x7b, 0x82, 0x82, 0x82, 0x82, 0x82, 0x87, 0x87, 0x89, 0x89),

        // ---- Play：サーバー行き（クライアント→サーバー） ----
        /** Chat Message。758 にだけある（署名なしの旧式チャット）。
            759 以降は Chat Message（署名付き）／Chat Command に分かれた。 */
        PLAY_CHAT_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x01, 0x01, 0x01, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x03, 0x02, 0x02, 0x02, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1),
        /** Chat Message。763は旧式、765は署名付き。766は 0x06（Chat Command Signed が 0x05 に入ったため）。 */
        PLAY_CHAT_MESSAGE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x04, 0x05, 0x05, 0x05,
                0x05, 0x05, 0x05, 0x06, 0x06, 0x07, 0x07, 0x07, 0x08, 0x08, 0x08, 0x08, 0x09, 0x09),
        /** Chat Command。 */
        PLAY_CHAT_COMMAND(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x03, 0x04, 0x04, 0x04,
                0x04, 0x04, 0x04, 0x04, 0x04, 0x05, 0x05, 0x05, 0x06, 0x06, 0x06, 0x06, 0x07, 0x07),
        /** Chat Command Signed。766で追加。署名付きチャットコマンド。
         * 765の Chat Message（0x05）が 766では Chat Command Signed（0x05）に
         * なり、Chat Message は 0x06 にずれる。 */
        PLAY_CHAT_COMMAND_SIGNED(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x05, 0x05, 0x06, 0x06, 0x06, 0x07, 0x07, 0x07, 0x07, 0x08, 0x08),
        /** Serverbound Keep Alive (play)。 */
        PLAY_KEEP_ALIVE_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x00, 0x00, 0x00, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
                0x0b, 0x0b, 0x0c, 0x0b, 0x0b, 0x0e, 0x0e, 0x0e, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x10,
                0x10, 0x10, 0x10, 0x10, 0x0f, 0x0f, 0x0f, 0x0f, 0x11, 0x12, 0x11, 0x12, 0x12, 0x14, 0x15, 0x18, 0x18,
                0x1a, 0x1a, 0x1a, 0x1b, 0x1b, 0x1b, 0x1b, 0x1c, 0x1c),
        /** Set Player Position。 */
        PLAY_SET_PLAYER_POSITION(State.PLAY, Direction.SERVERBOUND, 0x04, 0x04, 0x04, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c,
                0x0c, 0x0c, 0x0e, 0x0d, 0x0d, 0x10, 0x10, 0x10, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12,
                0x12, 0x12, 0x12, 0x12, 0x11, 0x11, 0x11, 0x11, 0x13, 0x14, 0x13, 0x14, 0x14, 0x16, 0x17, 0x1a, 0x1a,
                0x1c, 0x1c, 0x1c, 0x1d, 0x1d, 0x1d, 0x1d, 0x1e, 0x1e),
        /** Set Player Position and Rotation。 */
        PLAY_SET_PLAYER_POSITION_AND_ROTATION(State.PLAY, Direction.SERVERBOUND, 0x06, 0x06, 0x06, 0x0d, 0x0d, 0x0d,
                0x0d, 0x0d, 0x0d, 0x0d, 0x0f, 0x0e, 0x0e, 0x11, 0x11, 0x11, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12,
                0x12, 0x13, 0x13, 0x13, 0x13, 0x13, 0x12, 0x12, 0x12, 0x12, 0x14, 0x15, 0x14, 0x15, 0x15, 0x17, 0x18,
                0x1b, 0x1b, 0x1d, 0x1d, 0x1d, 0x1e, 0x1e, 0x1e, 0x1e, 0x1f, 0x1f),
        /** Set Player Rotation。 */
        PLAY_SET_PLAYER_ROTATION(State.PLAY, Direction.SERVERBOUND, 0x05, 0x05, 0x05, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e,
                0x0e, 0x0e, 0x10, 0x0f, 0x0f, 0x12, 0x12, 0x12, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x14,
                0x14, 0x14, 0x14, 0x14, 0x13, 0x13, 0x13, 0x13, 0x15, 0x16, 0x15, 0x16, 0x16, 0x18, 0x19, 0x1c, 0x1c,
                0x1e, 0x1e, 0x1e, 0x1f, 0x1f, 0x1f, 0x1f, 0x20, 0x20),
        /** Resource Pack Response (play)。763はUUIDなし、765はUUID付き。 */
        PLAY_RESOURCE_PACK_RESPONSE(State.PLAY, Direction.SERVERBOUND, -1, -1, 0x19, 0x16, 0x16, 0x16, 0x16, 0x16,
                0x16, 0x16, 0x18, 0x18, 0x18, 0x1d, 0x1d, 0x1d, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x20,
                0x20, 0x21, 0x21, 0x21, 0x21, 0x21, 0x21, 0x21, 0x23, 0x24, 0x24, 0x24, 0x24, 0x27, 0x28, 0x2b, 0x2b,
                0x2d, 0x2f, 0x2f, 0x30, 0x30, 0x30, 0x30, 0x31, 0x31),
        /** Serverbound Plugin Message (play)。 */
        PLAY_PLUGIN_MESSAGE_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x17, 0x17, 0x17, 0x09, 0x09, 0x09, 0x09,
                0x09, 0x09, 0x09, 0x0a, 0x09, 0x09, 0x0a, 0x0a, 0x0a, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0b,
                0x0b, 0x0b, 0x0b, 0x0b, 0x0b, 0x0a, 0x0a, 0x0a, 0x0a, 0x0c, 0x0d, 0x0c, 0x0d, 0x0D, 0x0F, 0x10, 0x12,
                0x12, 0x14, 0x14, 0x14, 0x15, 0x15, 0x15, 0x15, 0x16, 0x16),
        /** Client Information (play)。 */
        PLAY_CLIENT_INFORMATION(State.PLAY, Direction.SERVERBOUND, 0x15, 0x15, 0x15, 0x04, 0x04, 0x04, 0x04, 0x04,
                0x04, 0x04, 0x05, 0x04, 0x04, 0x04, 0x04, 0x04, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05,
                0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x07, 0x08, 0x07, 0x08, 0x08, 0x09, 0x09, 0x0a, 0x0a,
                0x0c, 0x0c, 0x0c, 0x0d, 0x0d, 0x0d, 0x0d, 0x0e, 0x0e),

        // ---- Play：サーバー行き（追加分：第2段階。ペイロードが両バージョンで同じもの） ----
        /** Teleport Confirm。VarIntで同じ。 */
        PLAY_TELEPORT_CONFIRM(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        /** Query Block NBT。VarInt＋Positionで同じ。 */
        PLAY_QUERY_BLOCK_NBT(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x02, 0x02),
        /** Select Bundle Item。768で追加。バンドルの選択中アイテムの変更。 */
        PLAY_SELECT_BUNDLE_ITEM(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x03, 0x03),
        /** Set Difficulty。u8で同じ。 */
        PLAY_SET_DIFFICULTY_TO_SERVER(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03,
                0x03, 0x03, 0x03, 0x04, 0x04),
        /** Message Acknowledgement。VarIntで同じ。 */
        PLAY_MESSAGE_ACKNOWLEDGEMENT(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x03,
                0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x04, 0x04, 0x04, 0x05, 0x05, 0x05, 0x05, 0x06, 0x06),
        /** Change Gamemode。771で追加（ゲームモード変更の要求）。 */
        PLAY_CHANGE_GAMEMODE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, 0x04, 0x04, 0x04, 0x04, 0x05, 0x05),
        /** Chat Session Update。UUID＋i64＋バッファ×2で同じ。 */
        PLAY_CHAT_SESSION_UPDATE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x20,
                0x06, 0x06, 0x06, 0x06, 0x07, 0x07, 0x08, 0x08, 0x08, 0x09, 0x09, 0x09, 0x09, 0x0a, 0x0a),
        /** Chunk Batch Received。チャンクバッチへの応答。763にはチャンクバッチが無い。764と765は同ID。 */
        PLAY_CHUNK_BATCH_RECEIVED(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x07, 0x07, 0x08, 0x08, 0x09, 0x09, 0x09, 0x0a, 0x0a, 0x0a, 0x0a, 0x0b, 0x0b),
        /** Client Command。VarIntで同じ。 */
        PLAY_CLIENT_COMMAND(State.PLAY, Direction.SERVERBOUND, 0x16, 0x16, 0x16, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03,
                0x03, 0x04, 0x03, 0x03, 0x03, 0x03, 0x03, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04,
                0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x06, 0x07, 0x06, 0x07, 0x07, 0x08, 0x08, 0x09, 0x09, 0x0a,
                0x0a, 0x0a, 0x0b, 0x0b, 0x0b, 0x0b, 0x0c, 0x0c),
        /** Tick End。768で追加。クライアント側のティック終了の空パケット。 */
        PLAY_TICK_END(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x0b, 0x0b, 0x0b, 0x0c, 0x0c, 0x0c, 0x0c, 0x0d, 0x0d),
        /** Tab Complete。VarInt＋文字列で同じ。 */
        /** Tab Complete (serverbound)。 */
        PLAY_TAB_COMPLETE_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x14, 0x14, 0x14, 0x01, 0x01, 0x01, 0x01, 0x01,
                0x01, 0x01, 0x02, 0x01, 0x01, 0x05, 0x05, 0x05, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06,
                0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x08, 0x09, 0x08, 0x09, 0x09, 0x0A, 0x0A, 0x0b, 0x0b,
                0x0d, 0x0d, 0x0d, 0x0e, 0x0e, 0x0e, 0x0e, 0x0f, 0x0f),
        /** Chat Preview。760 にだけある（1.19.3 でチャットプレビュー廃止）。
            サーバー行き。クライアントがプレビューを要求する。 */
        PLAY_CHAT_PREVIEW_TO_SERVER(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x05, 0x06, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Configuration Acknowledged。Start Configuration への応答。763には設定フェーズが無い。764と765は同ID。 */
        PLAY_CONFIGURATION_ACKNOWLEDGED(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x0B, 0x0B, 0x0c, 0x0c, 0x0e, 0x0e, 0x0e, 0x0f, 0x0f, 0x0f, 0x0f, 0x10, 0x10),
        /** Enchant Item。i8×2で同じ。 */
        PLAY_ENCHANT_ITEM(State.PLAY, Direction.SERVERBOUND, 0x11, 0x11, 0x11, 0x06, 0x06, 0x06, 0x06, 0x06, 0x06,
                0x06, 0x07, 0x06, 0x06, 0x07, 0x07, 0x07, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08,
                0x08, 0x08, 0x08, 0x07, 0x07, 0x07, 0x07, 0x09, 0x0a, 0x09, 0x0a, 0x0A, 0x0C, 0x0C, 0x0d, 0x0d, 0x0f,
                0x0f, 0x0f, 0x10, 0x10, 0x10, 0x10, 0x11, 0x11),
        /** Window Click。スロット形式は両バージョンで同じ。 */
        PLAY_WINDOW_CLICK(State.PLAY, Direction.SERVERBOUND, 0x0e, 0x0e, 0x0e, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
                0x07, 0x08, 0x07, 0x07, 0x08, 0x08, 0x08, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09, 0x09,
                0x09, 0x09, 0x09, 0x08, 0x08, 0x08, 0x08, 0x0a, 0x0b, 0x0a, 0x0b, 0x0B, 0x0D, 0x0D, 0x0e, 0x0e, 0x10,
                0x10, 0x10, 0x11, 0x11, 0x11, 0x11, 0x12, 0x12),
        /** Close Window。u8で同じ。 */
        /** Close Window (serverbound)。 */
        PLAY_CLOSE_WINDOW_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x0d, 0x0d, 0x0d, 0x08, 0x08, 0x08, 0x08, 0x08,
                0x08, 0x08, 0x09, 0x08, 0x08, 0x09, 0x09, 0x09, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a,
                0x0a, 0x0a, 0x0a, 0x0a, 0x09, 0x09, 0x09, 0x09, 0x0b, 0x0c, 0x0b, 0x0c, 0x0C, 0x0E, 0x0E, 0x0f, 0x0f,
                0x11, 0x11, 0x11, 0x12, 0x12, 0x12, 0x12, 0x13, 0x13),
        /** Set Slot State。765のみ。764のクライアントは送らない。 */
        PLAY_SET_SLOT_STATE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x0F, 0x10, 0x10, 0x12, 0x12, 0x12, 0x13, 0x13, 0x13, 0x13, 0x14, 0x14),
        /** Cookie Response (play)。766で追加。 */
        PLAY_COOKIE_RESPONSE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, 0x11, 0x11, 0x13, 0x13, 0x13, 0x14, 0x14, 0x14, 0x14, 0x15, 0x15),
        /** Debug Sample Subscription。766で追加。デバッグサンプルの購読。 */
        PLAY_DEBUG_SAMPLE_SUBSCRIPTION(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x13, 0x13, 0x15, 0x15, 0x15, 0x16, 0x16, -1, -1, -1, -1),
        /** Debug Subscription Request。773で追加（debug_sample_subscription の後継。形式が違うため別エントリ）。 */
        PLAY_DEBUG_SUBSCRIPTION_REQUEST(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x16, 0x16, 0x17, 0x17),
        /** Edit Book。 */
        PLAY_EDIT_BOOK(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x0b,
                0x0b, 0x0b, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0b, 0x0b,
                0x0b, 0x0b, 0x0d, 0x0e, 0x0d, 0x0e, 0x0E, 0x10, 0x11, 0x14, 0x14, 0x16, 0x16, 0x16, 0x17, 0x17, 0x17,
                0x17, 0x18, 0x18),
        /** Query Entity NBT。VarInt＋VarIntで同じ。 */
        PLAY_QUERY_ENTITY_NBT(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x0c, 0x0c, 0x0c, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0c,
                0x0c, 0x0c, 0x0c, 0x0e, 0x0f, 0x0e, 0x0f, 0x0F, 0x11, 0x12, 0x15, 0x15, 0x17, 0x17, 0x17, 0x18, 0x18,
                0x18, 0x18, 0x19, 0x19),
        /** Use Entity。 */
        PLAY_USE_ENTITY(State.PLAY, Direction.SERVERBOUND, 0x02, 0x02, 0x02, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a,
                0x0b, 0x0a, 0x0a, 0x0d, 0x0d, 0x0d, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e,
                0x0e, 0x0e, 0x0d, 0x0d, 0x0d, 0x0d, 0x0f, 0x10, 0x0f, 0x10, 0x10, 0x12, 0x13, 0x16, 0x16, 0x18, 0x18,
                0x18, 0x19, 0x19, 0x19, 0x19, 0x1a, 0x1a),
        /** Generate Structure。Position＋VarInt＋boolで同じ。 */
        PLAY_GENERATE_STRUCTURE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0e, 0x0e, 0x0e, 0x0e,
                0x10, 0x11, 0x10, 0x11, 0x11, 0x13, 0x14, 0x17, 0x17, 0x19, 0x19, 0x19, 0x1a, 0x1a, 0x1a, 0x1a, 0x1b,
                0x1b),
        /** Lock Difficulty。boolで同じ。 */
        PLAY_LOCK_DIFFICULTY(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x11, 0x11, 0x11, 0x11, 0x11, 0x10, 0x10,
                0x10, 0x10, 0x12, 0x13, 0x12, 0x13, 0x13, 0x15, 0x16, 0x19, 0x19, 0x1b, 0x1b, 0x1b, 0x1c, 0x1c, 0x1c,
                0x1c, 0x1d, 0x1d),
        /** Player Abilities (serverbound)。i8＋f32＋f32で同じ。 */
        PLAY_ABILITIES_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x13, 0x13, 0x13, 0x12, 0x12, 0x12, 0x12, 0x12,
                0x12, 0x12, 0x13, 0x13, 0x13, 0x17, 0x17, 0x17, 0x19, 0x19, 0x19, 0x19, 0x19, 0x19, 0x19, 0x19, 0x1a,
                0x1a, 0x1a, 0x1a, 0x1a, 0x19, 0x19, 0x19, 0x19, 0x1b, 0x1c, 0x1b, 0x1c, 0x1C, 0x1F, 0x20, 0x23, 0x23,
                0x25, 0x26, 0x26, 0x27, 0x27, 0x27, 0x27, 0x28, 0x28),
        /** Flying。boolで同じ。 */
        PLAY_FLYING(State.PLAY, Direction.SERVERBOUND, 0x03, 0x03, 0x03, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f,
                0x0d, 0x0c, 0x0c, 0x0f, 0x0f, 0x0f, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x15, 0x15, 0x15,
                0x15, 0x15, 0x14, 0x14, 0x14, 0x14, 0x16, 0x17, 0x16, 0x17, 0x17, 0x19, 0x1A, 0x1d, 0x1d, 0x1f, 0x1f,
                0x1f, 0x20, 0x20, 0x20, 0x20, 0x21, 0x21),
        /** Vehicle Move。f64×3＋f32×2で同じ。769は末尾に onGround の bool が追加。 */
        PLAY_VEHICLE_MOVE_TO_SERVER(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10,
                0x10, 0x11, 0x10, 0x10, 0x13, 0x13, 0x13, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x16, 0x16,
                0x16, 0x16, 0x16, 0x15, 0x15, 0x15, 0x15, 0x17, 0x18, 0x17, 0x18, 0x18, 0x1A, 0x1B, 0x1e, 0x1e, 0x20,
                0x20, 0x20, 0x21, 0x21, 0x21, 0x21, 0x22, 0x22),
        /** Steer Boat。bool＋f32×2で同じ。 */
        PLAY_STEER_BOAT(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12,
                0x11, 0x11, 0x14, 0x14, 0x14, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16, 0x17, 0x17, 0x17, 0x17,
                0x17, 0x16, 0x16, 0x16, 0x16, 0x18, 0x19, 0x18, 0x19, 0x19, 0x1B, 0x1C, 0x1f, 0x1f, 0x21, 0x21, 0x21,
                0x22, 0x22, 0x22, 0x22, 0x23, 0x23),
        /** Pick Item。VarIntで同じ。769でブロック／エンティティからの2つに分かれて削除。 */
        PLAY_PICK_ITEM(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x15,
                0x15, 0x15, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x18, 0x18, 0x18, 0x18, 0x18, 0x17, 0x17,
                0x17, 0x17, 0x19, 0x1a, 0x19, 0x1a, 0x1A, 0x1C, 0x1D, 0x20, 0x20, 0x22, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Pick Item From Block。769で追加（Pick Item の後継）。ブロック位置＋bool。 */
        PLAY_PICK_ITEM_FROM_BLOCK(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, 0x22, 0x22, 0x23, 0x23, 0x23, 0x23, 0x24, 0x24),
        /** Pick Item From Entity。769で追加（Pick Item の後継）。エンティティID＋bool。 */
        PLAY_PICK_ITEM_FROM_ENTITY(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, 0x23, 0x23, 0x24, 0x24, 0x24, 0x24, 0x25, 0x25),
        /** Player Loaded。769で追加。クライアントの読み込み完了を知らせる空パケット。 */
        PLAY_PLAYER_LOADED(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x2a, 0x2a, 0x2b, 0x2b, 0x2b, 0x2b, 0x2c, 0x2c),
        /** Set Test Block。770で追加（テストブロックの設置）。 */
        PLAY_SET_TEST_BLOCK(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, 0x39, 0x3a, 0x3a, 0x3a, 0x3a, 0x3c, 0x3c),
        /** Test Instance Block Action。770で追加（テストインスタンスブロックへの操作）。 */
        PLAY_TEST_INSTANCE_BLOCK_ACTION(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, 0x3c, 0x3e, 0x3e, 0x3e, 0x3e, 0x41, 0x41),
        /** Craft Recipe Request。VarInt＋文字列＋boolで同じ。 */
        PLAY_CRAFT_RECIPE_REQUEST(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x12,
                0x12, 0x16, 0x16, 0x16, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x19, 0x19, 0x19, 0x19, 0x19,
                0x18, 0x18, 0x18, 0x18, 0x1a, 0x1b, 0x1a, 0x1b, 0x1B, 0x1E, 0x1F, 0x22, 0x22, 0x24, 0x25, 0x25, 0x26,
                0x26, 0x26, 0x26, 0x27, 0x27),
        /** Block Dig。VarInt＋Position＋u8で同じ。 */
        PLAY_BLOCK_DIG(State.PLAY, Direction.SERVERBOUND, 0x07, 0x07, 0x07, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13,
                0x14, 0x14, 0x14, 0x18, 0x18, 0x18, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1b, 0x1b, 0x1b,
                0x1b, 0x1b, 0x1a, 0x1a, 0x1a, 0x1a, 0x1c, 0x1d, 0x1c, 0x1d, 0x1D, 0x20, 0x21, 0x24, 0x24, 0x26, 0x27,
                0x27, 0x28, 0x28, 0x28, 0x28, 0x29, 0x29),
        /** Entity Action。VarInt＋VarInt＋VarIntで同じ。 */
        PLAY_ENTITY_ACTION(State.PLAY, Direction.SERVERBOUND, 0x0b, 0x0b, 0x0b, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14,
                0x14, 0x15, 0x15, 0x15, 0x19, 0x19, 0x19, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1c, 0x1c,
                0x1c, 0x1c, 0x1c, 0x1b, 0x1b, 0x1b, 0x1b, 0x1d, 0x1e, 0x1d, 0x1e, 0x1E, 0x21, 0x22, 0x25, 0x25, 0x27,
                0x28, 0x28, 0x29, 0x29, 0x29, 0x29, 0x2a, 0x2a),
        /** Steer Vehicle。f32＋f32＋u8で同じ。768で Player Input に置き換えられ削除。 */
        PLAY_STEER_VEHICLE(State.PLAY, Direction.SERVERBOUND, 0x0c, 0x0c, 0x0c, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15,
                0x15, 0x16, 0x16, 0x16, 0x1a, 0x1a, 0x1a, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1d, 0x1d,
                0x1d, 0x1d, 0x1d, 0x1c, 0x1c, 0x1c, 0x1c, 0x1e, 0x1f, 0x1e, 0x1f, 0x1F, 0x22, 0x23, 0x26, 0x26, -1,
                -1, -1, -1, -1, -1, -1, -1, -1),
        /** Player Input。768で追加（Steer Vehicle の後継）。入力のビットフラグ u8。 */
        PLAY_PLAYER_INPUT(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x28, 0x29, 0x29, 0x2a, 0x2a, 0x2a, 0x2a, 0x2b, 0x2b),
        /** Pong。i32で同じ。 */
        PLAY_PONG(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, 0x07, 0x07, 0x07, 0x07, 0x07, 0x1d, 0x1d, 0x1d, 0x1d, 0x1f, 0x20,
                0x1f, 0x20, 0x20, 0x23, 0x24, 0x27, 0x27, 0x29, 0x2b, 0x2b, 0x2c, 0x2c, 0x2c, 0x2c, 0x2d, 0x2d),
        /** Recipe Book。 */
        PLAY_RECIPE_BOOK(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x20,
                0x21, 0x21, 0x21, 0x21, 0x24, 0x25, 0x28, 0x28, 0x2a, 0x2c, 0x2c, 0x2d, 0x2d, 0x2d, 0x2d, 0x2e, 0x2e),
        /** Displayed Recipe。 */
        PLAY_DISPLAYED_RECIPE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x21,
                0x22, 0x22, 0x22, 0x22, 0x25, 0x26, 0x29, 0x29, 0x2b, 0x2d, 0x2d, 0x2e, 0x2e, 0x2e, 0x2e, 0x2f, 0x2f),
        /** Name Item。文字列で同じ。 */
        PLAY_NAME_ITEM(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1c,
                0x1c, 0x1c, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1f, 0x1f, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x22, 0x23, 0x23, 0x23, 0x23, 0x26, 0x27, 0x2a, 0x2a, 0x2c, 0x2e, 0x2e, 0x2f, 0x2f, 0x2f,
                0x2f, 0x30, 0x30),
        /** Advancement Tab。VarInt＋任意文字列で同じ。 */
        PLAY_ADVANCEMENT_TAB(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x19, 0x19,
                0x19, 0x1e, 0x1e, 0x1e, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x21, 0x21, 0x22, 0x22, 0x22,
                0x22, 0x22, 0x22, 0x22, 0x24, 0x25, 0x25, 0x25, 0x25, 0x28, 0x29, 0x2c, 0x2c, 0x2e, 0x30, 0x30, 0x31,
                0x31, 0x31, 0x31, 0x32, 0x32),
        /** Select Trade。VarIntで同じ。 */
        PLAY_SELECT_TRADE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x1f,
                0x1f, 0x1f, 0x21, 0x21, 0x21, 0x21, 0x21, 0x21, 0x21, 0x21, 0x22, 0x22, 0x23, 0x23, 0x23, 0x23, 0x23,
                0x23, 0x23, 0x25, 0x26, 0x26, 0x26, 0x26, 0x29, 0x2A, 0x2d, 0x2d, 0x2f, 0x31, 0x31, 0x32, 0x32, 0x32,
                0x32, 0x33, 0x33),
        /** Set Beacon Effect。任意VarInt×2で同じ。 */
        PLAY_SET_BEACON_EFFECT(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                0x20, 0x20, 0x20, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x23, 0x23, 0x24, 0x24, 0x24, 0x24,
                0x24, 0x24, 0x24, 0x26, 0x27, 0x27, 0x27, 0x27, 0x2A, 0x2B, 0x2e, 0x2e, 0x30, 0x32, 0x32, 0x33, 0x33,
                0x33, 0x33, 0x34, 0x34),
        /** Held Item Slot (serverbound)。VarIntで同じ。 */
        PLAY_HELD_ITEM_SLOT_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x09, 0x09, 0x09, 0x17, 0x17, 0x17, 0x17,
                0x17, 0x17, 0x17, 0x1a, 0x1a, 0x1a, 0x21, 0x21, 0x21, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23,
                0x24, 0x24, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25, 0x27, 0x28, 0x28, 0x28, 0x28, 0x2B, 0x2C, 0x2f,
                0x2f, 0x31, 0x33, 0x33, 0x34, 0x34, 0x34, 0x34, 0x35, 0x35),
        /** Update Command Block。 */
        PLAY_UPDATE_COMMAND_BLOCK(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x22, 0x22, 0x22, 0x24, 0x24, 0x24, 0x24, 0x24, 0x24, 0x24, 0x24, 0x25, 0x25, 0x26, 0x26, 0x26,
                0x26, 0x26, 0x26, 0x26, 0x28, 0x29, 0x29, 0x29, 0x29, 0x2C, 0x2D, 0x30, 0x30, 0x32, 0x34, 0x34, 0x35,
                0x35, 0x35, 0x35, 0x36, 0x36),
        /** Update Command Block Minecart。 */
        PLAY_UPDATE_COMMAND_BLOCK_MINECART(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, 0x23, 0x23, 0x23, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25, 0x25, 0x26, 0x26, 0x27, 0x27,
                0x27, 0x27, 0x27, 0x27, 0x27, 0x29, 0x2a, 0x2a, 0x2a, 0x2A, 0x2D, 0x2E, 0x31, 0x31, 0x33, 0x35, 0x35,
                0x36, 0x36, 0x36, 0x36, 0x37, 0x37),
        /** Set Creative Slot。スロット形式は両バージョンで同じ。 */
        PLAY_SET_CREATIVE_SLOT(State.PLAY, Direction.SERVERBOUND, 0x10, 0x10, 0x10, 0x18, 0x18, 0x18, 0x18, 0x18,
                0x18, 0x18, 0x1b, 0x1b, 0x1b, 0x24, 0x24, 0x24, 0x26, 0x26, 0x26, 0x26, 0x26, 0x26, 0x26, 0x26, 0x27,
                0x27, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x2a, 0x2b, 0x2b, 0x2b, 0x2B, 0x2E, 0x2F, 0x32, 0x32,
                0x34, 0x36, 0x36, 0x37, 0x37, 0x37, 0x37, 0x38, 0x38),
        /** Update Jigsaw Block。 */
        PLAY_UPDATE_JIGSAW_BLOCK(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, 0x27, 0x27, 0x27, 0x27, 0x27, 0x27, 0x27, 0x27, 0x28, 0x28, 0x29, 0x29, 0x29, 0x29,
                0x29, 0x29, 0x29, 0x2b, 0x2c, 0x2c, 0x2c, 0x2C, 0x2F, 0x30, 0x33, 0x33, 0x35, 0x37, 0x37, 0x38, 0x38,
                0x38, 0x38, 0x3a, 0x3a),
        /** Update Structure Block。 */
        PLAY_UPDATE_STRUCTURE_BLOCK(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, 0x25, 0x25, 0x25, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x29, 0x29, 0x2a, 0x2a, 0x2a,
                0x2a, 0x2a, 0x2a, 0x2a, 0x2c, 0x2d, 0x2d, 0x2d, 0x2D, 0x30, 0x31, 0x34, 0x34, 0x36, 0x38, 0x38, 0x39,
                0x39, 0x39, 0x39, 0x3b, 0x3b),
        /** Update Sign。位置＋表裏bool＋4行の文字列で同じ。 */
        PLAY_UPDATE_SIGN(State.PLAY, Direction.SERVERBOUND, 0x12, 0x12, 0x12, 0x19, 0x19, 0x19, 0x19, 0x19, 0x19,
                0x19, 0x1c, 0x1c, 0x1c, 0x26, 0x26, 0x26, 0x29, 0x29, 0x29, 0x29, 0x29, 0x29, 0x29, 0x29, 0x2a, 0x2a,
                0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2d, 0x2e, 0x2e, 0x2e, 0x2E, 0x31, 0x32, 0x35, 0x35, 0x37,
                0x39, 0x3a, 0x3b, 0x3b, 0x3b, 0x3b, 0x3d, 0x3d),
        PLAY_UPDATE_SIGN_CLIENTBOUND(State.PLAY, Direction.CLIENTBOUND, 0x33, 0x33, 0x33, 0x46, 0x46, 0x46, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Arm Animation。VarIntで同じ。 */
        PLAY_ARM_ANIMATION(State.PLAY, Direction.SERVERBOUND, 0x0a, 0x0a, 0x0a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a,
                0x1a, 0x1d, 0x1d, 0x1d, 0x27, 0x27, 0x27, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2a, 0x2b, 0x2b,
                0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2e, 0x2f, 0x2f, 0x2f, 0x2F, 0x32, 0x33, 0x36, 0x36, 0x38,
                0x3a, 0x3b, 0x3c, 0x3c, 0x3c, 0x3c, 0x3f, 0x3f),
        /** Spectate。UUIDで同じ。 */
        PLAY_SPECTATE(State.PLAY, Direction.SERVERBOUND, -1, -1, 0x18, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1e,
                0x1e, 0x1e, 0x28, 0x28, 0x28, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2c, 0x2c, 0x2d, 0x2d,
                0x2d, 0x2d, 0x2d, 0x2d, 0x2d, 0x2f, 0x30, 0x30, 0x30, 0x30, 0x33, 0x34, 0x37, 0x37, 0x39, 0x3b, 0x3d,
                0x3d, 0x3d, 0x3d, 0x3d, 0x40, 0x40),
        /** Block Place。 */
        PLAY_BLOCK_PLACE(State.PLAY, Direction.SERVERBOUND, 0x08, 0x08, 0x08, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c,
                0x1c, 0x1f, 0x1f, 0x1f, 0x29, 0x29, 0x29, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2d, 0x2d,
                0x2e, 0x2e, 0x2e, 0x2e, 0x2e, 0x2e, 0x2e, 0x30, 0x31, 0x31, 0x31, 0x31, 0x34, 0x35, 0x38, 0x38, 0x3a,
                0x3c, 0x3e, 0x3f, 0x3f, 0x3f, 0x3f, 0x42, 0x42),
        /** Use Item。VarInt＋VarInt＋f32×3で同じ。 */
        PLAY_USE_ITEM(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x20,
                0x20, 0x20, 0x2a, 0x2a, 0x2a, 0x2d, 0x2d, 0x2d, 0x2d, 0x2d, 0x2d, 0x2d, 0x2d, 0x2e, 0x2e, 0x2f, 0x2f,
                0x2f, 0x2f, 0x2f, 0x2f, 0x2f, 0x31, 0x32, 0x32, 0x32, 0x32, 0x35, 0x36, 0x39, 0x39, 0x3b, 0x3d, 0x3f,
                0x40, 0x40, 0x40, 0x40, 0x43, 0x43),
        /** Custom Click Action。771で追加（minecraft:custom クリックイベントの送信）。 */
        PLAY_CUSTOM_CLICK_ACTION_TO_SERVER(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x41, 0x41, 0x41, 0x41, 0x44, 0x44),
        /** Game Rule Values。775で追加（ゲームルールの値を送る。values: GameRule の配列）。 */
        PLAY_GAME_RULE_VALUES(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x27, 0x27),
        /** Low Disk Space Warning。775で追加（ディスク容量不足の警告。ペイロード無し）。 */
        PLAY_LOW_DISK_SPACE_WARNING(State.PLAY, Direction.CLIENTBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x32, 0x32),
        /** Attack。775で追加（エンティティへの攻撃。entityId: VarInt）。 */
        PLAY_ATTACK(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, 0x01, 0x01),
        /** Set Game Rule。775で追加（ゲームルールの変更。entries: GameRule の配列）。 */
        PLAY_SET_GAME_RULE(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x39, 0x39),
        /** Spectate Entity。775で追加（エンティティの観戦。entityId: VarInt）。 */
        PLAY_SPECTATE_ENTITY(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3e, -1),
        PLAY_SPECTATOR_ACTION(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x3e),
        /** Spawn Entity Weather。578 にだけある（落雷の出現）。
            758 以降は Spawn Entity に統合。 */
        PLAY_SPAWN_ENTITY_WEATHER(State.PLAY, Direction.CLIENTBOUND, 0x2c, 0x2c, 0x2c, 0x02, 0x02, 0x02, 0x02, 0x02,
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Transaction（クライアント行き）。578 にだけある（旧式の
            Window Confirmation。windowId＋action＋accepted）。
            758 以降はプレイ状態では使われない。 */
        PLAY_TRANSACTION(State.PLAY, Direction.CLIENTBOUND, 0x32, 0x32, 0x32, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
                0x11, 0x11, 0x11, 0x11, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0x12, 0x13, 0x13, 0x13, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Entity。578 にだけある（entityId だけを持つパケット）。 */
        PLAY_ENTITY(State.PLAY, Direction.CLIENTBOUND, 0x14, 0x14, 0x14, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28,
                0x25, 0x25, 0x25, 0x27, 0x27, 0x27, 0x2b, 0x2b, 0x2b, 0x2b, 0x2b, 0x2c, 0x2c, 0x2c, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Combat Event。578 にだけある（戦闘の開始・終了・死亡を
            1パケットに統合）。758 以降は Enter／End／Death に分割。 */
        PLAY_COMBAT_EVENT(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x42, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c, 0x2c,
                0x2c, 0x2d, 0x2d, 0x2f, 0x2f, 0x2f, 0x32, 0x32, 0x32, 0x32, 0x32, 0x33, 0x33, 0x33, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** World Border。578 にだけある（アクションで分岐する単一
            パケット版）。758 以降は Initialize と個別 setter に分割。 */
        PLAY_WORLD_BORDER(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x44, 0x35, 0x35, 0x35, 0x35, 0x35, 0x35, 0x35,
                0x37, 0x38, 0x38, 0x3b, 0x3b, 0x3b, 0x3d, 0x3d, 0x3d, 0x3d, 0x3d, 0x3e, 0x3e, 0x3e, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Title。578 にだけある（アクションで分岐する単一パケット版）。
            758 以降は Set Title Text／Subtitle／Time に分割。 */
        PLAY_TITLE(State.PLAY, Direction.CLIENTBOUND, -1, -1, 0x45, 0x45, 0x45, 0x45, 0x45, 0x45, 0x45, 0x45, 0x47,
                0x48, 0x48, 0x4b, 0x4b, 0x4b, 0x4f, 0x4f, 0x4f, 0x4f, 0x4f, 0x50, 0x50, 0x50, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Transaction（サーバー行き）。578 にだけある（旧式の
            Window Confirmation への応答）。 */
        PLAY_TRANSACTION_TO_SERVER(State.PLAY, Direction.SERVERBOUND, 0x0f, 0x0f, 0x0f, 0x05, 0x05, 0x05, 0x05, 0x05,
                0x05, 0x05, 0x06, 0x05, 0x05, 0x06, 0x06, 0x06, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Crafting Book Data。578 にだけある（レシピ本の表示レシピと
            開閉状態を1パケットに統合）。758 以降は分割された。 */
        PLAY_CRAFTING_BOOK_DATA(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x17, 0x17,
                0x17, 0x1b, 0x1b, 0x1b, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
        /** Prepare Crafting Grid（サーバー行き）。335 にだけある
            （クラフト台グリッドへの事前配置）。338 のレシピブック再編で
            消え、代わりに Craft Recipe Request／Response が入った。
            338 以降に届け先が無いため変換では落とす。 */
        PLAY_PREPARE_CRAFTING_GRID(State.PLAY, Direction.SERVERBOUND, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x01,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1);

        private final State state;
        private final Direction direction;
        private final int id4;
        private final int id5;
        private final int id47;
        private final int id107;
        private final int id108;
        private final int id109;
        private final int id110;
        private final int id210;
        private final int id315;
        private final int id316;
        private final int id335;
        private final int id338;
        private final int id340;
        private final int id393;
        private final int id401;
        private final int id404;
        private final int id477;
        private final int id480;
        private final int id485;
        private final int id490;
        private final int id498;
        private final int id573;
        private final int id575;
        private final int id578;
        private final int id735;
        private final int id736;
        private final int id751;
        private final int id753;
        private final int id754;
        private final int id755;
        private final int id756;
        private final int id757;
        private final int id758;
        private final int id759;
        private final int id760;
        private final int id761;
        private final int id762;
        private final int id763;
        private final int id764;
        private final int id765;
        private final int id766;
        private final int id767;
        private final int id768;
        private final int id769;
        private final int id770;
        private final int id771;
        private final int id772;
        private final int id773;
        private final int id774;
        private final int id775;
        private final int id776;

        PacketName(State state, Direction direction, int id4, int id5, int id47, int id107, int id108, int id109, int id110, int id210, int id315, int id316, int id335, int id338, int id340, int id393, int id401, int id404, int id477, int id480, int id485, int id490, int id498, int id573, int id575, int id578, int id735, int id736, int id751, int id753, int id754, int id755, int id756, int id757, int id758, int id759, int id760, int id761, int id762, int id763, int id764, int id765, int id766, int id767, int id768, int id769, int id770, int id771, int id772, int id773, int id774, int id775, int id776) {
            this.state = state;
            this.direction = direction;
            this.id4 = id4;
            this.id5 = id5;
            this.id47 = id47;
            this.id107 = id107;
            this.id108 = id108;
            this.id109 = id109;
            this.id110 = id110;
            this.id210 = id210;
            this.id315 = id315;
            this.id316 = id316;
            this.id335 = id335;
            this.id338 = id338;
            this.id340 = id340;
            this.id393 = id393;
            this.id401 = id401;
            this.id404 = id404;
            this.id477 = id477;
            this.id480 = id480;
            this.id485 = id485;
            this.id490 = id490;
            this.id498 = id498;
            this.id573 = id573;
            this.id575 = id575;
            this.id578 = id578;
            this.id735 = id735;
            this.id736 = id736;
            this.id751 = id751;
            this.id753 = id753;
            this.id754 = id754;
            this.id755 = id755;
            this.id756 = id756;
            this.id757 = id757;
            this.id758 = id758;
            this.id759 = id759;
            this.id760 = id760;
            this.id761 = id761;
            this.id762 = id762;
            this.id763 = id763;
            this.id764 = id764;
            this.id765 = id765;
            this.id766 = id766;
            this.id767 = id767;
            this.id768 = id768;
            this.id769 = id769;
            this.id770 = id770;
            this.id771 = id771;
            this.id772 = id772;
            this.id773 = id773;
            this.id774 = id774;
            this.id775 = id775;
            this.id776 = id776;
        }

        /** このパケットが属する状態。 */
        public State state() {
            return state;
        }

        /** このパケットの向き。 */
        public Direction direction() {
            return direction;
        }

        /** 4（1.7.2〜1.7.5 の全4リリース共通）でのID。存在しない場合は -1。**4 の ID は 5 と完全同一**（packet-ids-4.py 参照：minecraft-data pc/1.7 は 1.7.10＝プロトコル 5 専用で 4 用の個別データは無く、Stage A の実ワイヤ採取で観測した ID 集合が 5 の列の部分集合であること、二人採取で named_entity_spawn の ID（0x0c）一致を確認済み）。4⇔5 の差分はワイヤ形式の2件だけ：Login Success と named_entity_spawn の UUID 文字列がダッシュ無し32文字（5 はダッシュ付き36文字）、named_entity_spawn のプロパティ配列が 4 には無い。 */
        public int id4() {
            return id4;
        }

        /** 5（1.7.6〜1.7.10 の全5リリース共通）でのID。存在しない場合は -1。packet-ids-5.py で、5 自身の protocol.json（minecraft-data pc/1.7、抽出元は 1.7.10）から引き直し、全287エントリが既存列からの名前同定で一意に定まることを照合済み。**5 と 47 の両方に存在するパケットは表の範囲（status を除く）で95件、そのうち ID が違うのは0件**（1.8 は ID 配置を変えずに12件を追加しただけ）。47 にだけあるパケットは12件（login compress、camera、combat_event、difficulty、playerlist_header、resource_pack_send、play の set_compression、title、update_entity_nbt、world_border、resource_pack_receive、spectate）で、5 ではすべて -1。5 にだけあるパケットは0件。map_chunk_bulk は 5 にも 0x26 で存在する。5 で名前が違うパケットは47と同じ2件（teams → scoreboard_team 0x3e、entity_update_attributes → update_attributes 0x20）。ワイヤ形式の差分は60パケットに及ぶが大半は系統的な型差分（entityId i32⇔varint、ブロック座標のバラ数値⇔64bit詰め、Keep Alive i32⇔varint）で、チャンクの中身（u8 ID配列＋nibble群、bulk のヘッダ配置）と player_info／named_entity_spawn の形が別物（{@link io.detergentmc.protocol.ChunkTranslator} 参照）。**4／5 にはパケット圧縮の仕組み自体が無い**。Keep Alive は i32 で、ID は 47 と同じ 0x00。 */
        public int id5() {
            return id5;
        }

        /** 47（1.8〜1.8.9 の全10リリース共通）でのID。存在しない場合は -1。packet-ids-47.py で、47 自身の protocol.json（minecraft-data pc/1.8、抽出元は 1.8.8。1.8.8 と 1.8.9 はクラス263件のバイトコード差のみでプロトコル同一）から引き直し、既存284エントリすべてが既存列からの名前同定で一意に定まることを照合済み。47 と 107 の両方に存在するパケットは表の範囲（status を除く）で104件、そのうち ID が違うのは96件（1.9 で play の ID 配置がほぼ全面組み替えになったため）。47 にだけあるパケットは3件（map_chunk_bulk 0x26、プレイ状態の set_compression 0x46、update_entity_nbt 0x49）で、本表に新規エントリとして追加した。47 で名前が違うパケットは2件（teams → scoreboard_team 0x3e、entity_update_attributes → update_attributes 0x20）。ワイヤ形式の差分は22件あり、特にチャンク形式（パレット無し・2バイトLE固定詰め・map_chunk_bulk）、entityMetadata 形式（bitfield ヘッダ・終端 0x7f・型8種）、エンティティ座標（i32 固定小数点）が別物（{@link io.detergentmc.protocol.ChunkTranslator} 参照）。Keep Alive は 107 と同じく varint で、応答 ID は 0x00。 */
        public int id47() {
            return id47;
        }

        /** 107（1.9）でのID。存在しない場合は -1。108（1.9.1）・109（1.9.2）と全パケット完全一致（ID差分ゼロ）。packet-ids-107.py で、107 自身の protocol.json（minecraft-data pc/1.9）と pc/1.9.2 の全面比較（差分は packet_login の dimension の型 i8⇔i32 ただ1件、パケット集合・ID・Respawn を含む他の全フィールド型は完全一致）と、実バニラ 1.9 へのワイヤ採取（ステータス応答 protocol=107、Join Game は 17 バイトで dimension を i8 として過不足なく消費、Chunk Data は dataLen 内に末尾ゼロ埋めを含み blockEntities 無し、Keep Alive は varint で読み切り＋応答 0x0b＋応答後のチャットがバニラログに到達）で裏付けた。108 は 109 と差分ゼロのため、107 の ID 表は 108 とも一致する。ワイヤ形式差分は Join Game の dimension の型ただ1件で、変換側で吸収する（{@link io.detergentmc.protocol.PlayTranslator} 参照）。レジストリも 108 と完全同一（属性 generic.armorToughness が 108 で追加されるが、名前文字列の差であり ID には影響しない）。 */
        public int id107() {
            return id107;
        }

        /** 108（1.9.1）でのID。存在しない場合は -1。109（1.9.2）と全パケット完全一致（差分ゼロ）。packet-ids-108.py で実 jar 同士を全面比較し（エントリ一覧 6,354 件が完全一致、非クラスファイルは CRC まで全て一致、差分のあるクラス 11 件の文字列定数の差分は版数文字列とパケットバッファのエラーメッセージ文言1件だけ）、実バニラ 1.9.1 へのワイヤ採取でも裏付けた（ステータス応答 protocol=108、Join Game は 20 バイトで dimension を i32 として過不足なく消費、Chunk Data の末尾に blockEntities 無し、Keep Alive は varint で読み切り＋応答 0x0b＋応答後のチャットがバニラログに到達）。minecraft-data に 1.9.1 の項は無いため、プロトコルの ground truth は 109 自身の protocol.json（pc/1.9.2）と上記ワイヤ実測。レジストリも 109 と完全同一。 */
        public int id108() {
            return id108;
        }

        /** 109（1.9.2）でのID。存在しない場合は -1。110 との差分は play／クライアント行きだけ：update_sign（0x46）が 109 にだけあり、それより後ろの6件（sound_effect 0x47、playerlist_header 0x48、collect 0x49、entity_teleport 0x4a、entity_update_attributes 0x4b、entity_effect 0x4c）が 110 より +1 ずれる。それ以外のステート・方向は 110 と完全一致。packet-ids-109.py で、109 自身の protocol.json（minecraft-data pc/1.9.2、version.json が version: 109 を明示）と 110 の protocol.json（pc/1.9.4）という独立した2源を全面比較し（総数 119⇔118、片側のみ＝update_sign 1件、ID差分＝6件、types 節完全一致、フィールド構造差分＝map_chunk と update_sign の2件だけ）、実バニラ 1.9.2 へのワイヤ採取（ステータス応答 protocol=109、Join Game の dimension i32 を 20/20 バイト消費、チャンク末尾に blockEntities が無く残量＝バイオーム 256 バイトちょうど、Keep Alive は varint で読み切り＋応答 0x0b＋応答後のチャットがバニラログに到達）でも裏付けた。レジストリは 110 と完全同一（dataPaths で 1.9.2／1.9.4 とも全レジストリが pc/1.9 に解決し、両 jar の en_US.lang の差分は /stopsound のコマンド文言5行だけ）。109 と 110 のワイヤ形式差分は map_chunk の blockEntities（110 で追加）の1件だけ（{@link io.detergentmc.protocol.RegistryRemapper} 参照）。 */
        public int id109() {
            return id109;
        }

        /** 110（1.9.4／1.9.3）でのID。存在しない場合は -1。110 のIDは全件 210 と同一（差分0件）。packet-ids-110.py で、110 自身の protocol.json（minecraft-data pc/1.9.4、version.json が version: 110 を明示）と 210 の protocol.json という独立した2源の ID 表が全ステート・全方向で完全一致すること（パケット総数118件、片側のみ0件、ID差分0件）を照合し、実バニラ 1.9.4 へのワイヤ採取（ステータス応答 protocol=110、ログイン〜Join Game 20/20バイト〜Keep Alive varint 読み切り＋応答 0x0b〜応答後のチャットがバニラログに到達）でも裏付けた。110 と 210 の差分はワイヤ形式3件（sound_effect／named_sound_effect の pitch が u8、resource_pack_receive の先頭に hash がある）とレジストリだけ（{@link io.detergentmc.protocol.RegistryRemapper} 参照）。なお 110 は 1.9.4 だけの番号ではない：1.9.3 も同じく 110（実バニラ 1.9.3 のステータス応答で確認済み）。 */
        public int id110() {
            return id110;
        }

        /** 210（1.10／1.10.1／1.10.2）でのID。存在しない場合は -1。210 のIDは全件 315 と同一（差分0件）。packet-ids-210.py で、210 自身の protocol.json（minecraft-data pc/1.10、dataPaths で 1.10／1.10.1／1.10.2 の全版が指す Burger 抽出）と 315 の protocol.json という独立した2源の ID 表が全ステート・全方向で完全一致すること（パケット総数118件、片側のみ0件、ID差分0件）を照合し、実バニラ 1.10.2 へのワイヤ採取（ステータス応答 protocol=210、ログイン〜Join Game〜Keep Alive varint〜チャット到達）でも裏付けた。210 と 315 の差分はワイヤ形式4件（spawn_entity_living の type が u8、title の action 番号、collect の pickupItemCount 欠落、block_place のカーソルが i8）とレジストリだけ（{@link io.detergentmc.protocol.RegistryRemapper} 参照）。なお 210 は 1.10 だけの番号ではない：1.10／1.10.1／1.10.2 の3リリース共通で、201 という製品版プロトコルは存在しない。 */
        public int id210() {
            return id210;
        }

        /** 315（1.11）でのID。存在しない場合は -1。315 のIDは全件 316 と同一（差分0件）。packet-ids-315.py で、315 自身の protocol.json（minecraft-data pc/1.11、version.json が 315 を明示する Burger 抽出）と 316 の protocolComments.json（wiki.vg 由来）という独立した2源の ID 表が全ステート・全方向で完全一致すること（パケット総数118件、片側のみ0件、ID差分0件）を照合し、実バニラ 1.11 へのワイヤ採取（ログイン〜Join Game〜Keep Alive varint〜チャット到達、316 と同じ ID・形式）でも裏付けた。315 と 316 の差分はレジストリ側だけ（iron_nugget の追加と totem の改名。{@link io.detergentmc.protocol.RegistryRemapper} 参照）。なお 315 は 1.11.1 ではない：1.11.1 と 1.11.2 はどちらもプロトコル 316（実バニラ 1.11.1 のステータス応答で確認済み）。 */
        public int id315() {
            return id315;
        }

        /** 316（1.11.2）でのID。存在しない場合は -1。335 との差分は 1.12 で追加された進捗（Advancements）とレシピブックの6件（クライアント行き advancements／select_advancement_tab／unlock_recipes、サーバー行き advancement_tab／crafting_book_data／prepare_crafting_grid）が 316 に無いことで、ID差分は61件。共有パケットのワイヤ形式は全件同一（唯一の型差分は entityMetadataItem に 1.12 で型13＝NBT が増えたこと）。packet-ids-316.py で 1.11 の protocol.json（minecraft-data の data.js で 1.11.2 が指す、316 自身のデータ）から独立に引き直し、不変条件（335のみ＝6件ちょうど、316のみ＝0件、ID差分＝61件）の全件成立を照合済み。 */
        public int id316() {
            return id316;
        }

        /** 335（1.12）でのID。存在しない場合は -1。338 との差分はレシピブック再編の1点に集中する：335 にだけ Prepare Crafting Grid（サーバー行き 0x01）があり、338 で追加された Craft Recipe Request／Response は 335 に無い。ID差分は53件で全て機械的（クライアント行きは 0x2b への挿入で36件が -1ずれ、サーバー行きは先頭の削除で 0x01〜0x11 の17件が +1ずれ）。packet-ids-335.py で 1.12 の protocol.json から独立に引き直し、機械規則の全件成立を照合済み。 */
        public int id335() {
            return id335;
        }

        /** 338（1.12.1）でのID。存在しない場合は -1。338 のIDは全件 340 と同一（packet-ids-338.py で 1.12.1 の protocol.json から独立に引き直して検証済み）。340 との形式差分は Keep Alive の ID 型（varint）だけ。 */
        public int id338() {
            return id338;
        }

        /** 340（1.12.2）でのID。存在しない場合は -1。340 は Flattening 前の世代で、393 と共通する120件中95件のIDが393と違う（packet-ids-340.py で検証済み）。 */
        public int id340() {
            return id340;
        }

        /** 393（1.13）でのID。存在しない場合は -1。393 のIDは全件 401 と同一（packet-ids-393.py で検証済み）。 */
        public int id393() {
            return id393;
        }

        /** 401（1.13.1）でのID。存在しない場合は -1。401 のIDは全件 404 と同一（packet-ids-401.py で検証済み）。 */
        public int id401() {
            return id401;
        }

        /** 404（1.13.2）でのID。存在しない場合は -1。 */
        public int id404() {
            return id404;
        }

        /** 477（1.14）でのID。存在しない場合は -1。 */
        public int id477() {
            return id477;
        }

        /** 480（1.14.1）でのID。存在しない場合は -1。 */
        public int id480() {
            return id480;
        }

        /** 485（1.14.2）でのID。存在しない場合は -1。 */
        public int id485() {
            return id485;
        }

        /** 490（1.14.3）でのID。存在しない場合は -1。 */
        public int id490() {
            return id490;
        }

        /** 498（1.14.4）でのID。存在しない場合は -1。 */
        public int id498() {
            return id498;
        }

        /** 573（1.15）でのID。存在しない場合は -1。 */
        public int id573() {
            return id573;
        }

        /** 575（1.15.1）でのID。存在しない場合は -1。 */
        public int id575() {
            return id575;
        }

        /** 578（1.15.2）でのID。存在しない場合は -1。 */
        /** 735（1.16）でのID。存在しない場合は -1。 */
        public int id735() {
            return id735;
        }

        /** 736（1.16.1）でのID。存在しない場合は -1。 */
        public int id736() {
            return id736;
        }

        /** 751（1.16.2）でのID。存在しない場合は -1。 */
        public int id751() {
            return id751;
        }

        /** 753（1.16.3）でのID。存在しない場合は -1。 */
        public int id753() {
            return id753;
        }

        /** 754（1.16.4／1.16.5）でのID。存在しない場合は -1。 */
        public int id754() {
            return id754;
        }

        /** 755（1.17）でのID。存在しない場合は -1。 */
        public int id755() {
            return id755;
        }

        /** 756（1.17.1）でのID。存在しない場合は -1。 */
        public int id756() {
            return id756;
        }

        /** 757（1.18／1.18.1）でのID。存在しない場合は -1。 */
        public int id757() {
            return id757;
        }
        public int id578() {
            return id578;
        }

        /** 758（1.18.2）でのID。存在しない場合は -1。 */
        public int id758() {
            return id758;
        }

        /** 759（1.19）でのID。存在しない場合は -1。 */
        public int id759() {
            return id759;
        }

        /** 760（1.19.1／1.19.2）でのID。存在しない場合は -1。 */
        public int id760() {
            return id760;
        }

        /** 761（1.19.3）でのID。存在しない場合は -1。 */
        public int id761() {
            return id761;
        }

        /** 762（1.19.4）でのID。存在しない場合は -1。 */
        public int id762() {
            return id762;
        }

        /** 763（1.20.1）でのID。存在しない場合は -1。 */
        public int id763() {
            return id763;
        }

        /** 764（1.20.2）でのID。存在しない場合は -1。 */
        public int id764() {
            return id764;
        }

        /** 765（1.20.4）でのID。存在しない場合は -1。 */
        public int id765() {
            return id765;
        }

        /** 766（1.20.5／1.20.6）でのID。存在しない場合は -1。 */
        public int id766() {
            return id766;
        }

        /** 767（1.21／1.21.1）でのID。存在しない場合は -1。 */
        public int id767() {
            return id767;
        }

        /** 768（1.21.2／1.21.3）でのID。存在しない場合は -1。 */
        public int id768() {
            return id768;
        }

        /** 769（1.21.4）でのID。存在しない場合は -1。 */
        public int id769() {
            return id769;
        }

        /** 770（1.21.5）でのID。存在しない場合は -1。 */
        public int id770() {
            return id770;
        }

        /** 771（1.21.6）でのID。存在しない場合は -1。 */
        public int id771() {
            return id771;
        }

        /** 772（1.21.7／1.21.8）でのID。存在しない場合は -1。 */
        public int id772() {
            return id772;
        }

        /** 773（1.21.9／1.21.10）でのID。存在しない場合は -1。 */
        public int id773() {
            return id773;
        }

        /** 774（1.21.11）でのID。存在しない場合は -1。 */
        public int id774() {
            return id774;
        }

        /** 775（26.1／26.1.1／26.1.2）でのID。存在しない場合は -1。 */
        public int id775() {
            return id775;
        }

        /** 776（26.2）でのID。存在しない場合は -1。 */
        public int id776() {
            return id776;
        }

        /** 指定プロトコルでのID。存在しない場合は -1。 */
        public int idFor(int protocol) {
            if (protocol == ProtocolVersion.V1_7_2) {
                return id4;
            }
            if (protocol == ProtocolVersion.V1_7_10) {
                return id5;
            }
            if (protocol == ProtocolVersion.V1_8) {
                return id47;
            }
            if (protocol == ProtocolVersion.V1_9) {
                return id107;
            }
            if (protocol == ProtocolVersion.V1_9_1) {
                return id108;
            }
            if (protocol == ProtocolVersion.V1_9_2) {
                return id109;
            }
            if (protocol == ProtocolVersion.V1_9_4) {
                return id110;
            }
            if (protocol == ProtocolVersion.V1_10_2) {
                return id210;
            }
            if (protocol == ProtocolVersion.V1_11) {
                return id315;
            }
            if (protocol == ProtocolVersion.V1_11_2) {
                return id316;
            }
            if (protocol == ProtocolVersion.V1_12) {
                return id335;
            }
            if (protocol == ProtocolVersion.V1_12_1) {
                return id338;
            }
            if (protocol == ProtocolVersion.V1_12_2) {
                return id340;
            }
            if (protocol == ProtocolVersion.V1_13) {
                return id393;
            }
            if (protocol == ProtocolVersion.V1_13_1) {
                return id401;
            }
            if (protocol == ProtocolVersion.V1_13_2) {
                return id404;
            }
            if (protocol == ProtocolVersion.V1_14) {
                return id477;
            }
            if (protocol == ProtocolVersion.V1_14_1) {
                return id480;
            }
            if (protocol == ProtocolVersion.V1_14_2) {
                return id485;
            }
            if (protocol == ProtocolVersion.V1_14_3) {
                return id490;
            }
            if (protocol == ProtocolVersion.V1_14_4) {
                return id498;
            }
            if (protocol == ProtocolVersion.V1_15) {
                return id573;
            }
            if (protocol == ProtocolVersion.V1_15_1) {
                return id575;
            }
            if (protocol == ProtocolVersion.V1_15_2) {
                return id578;
            }
            if (protocol == ProtocolVersion.V1_16) {
                return id735;
            }
            if (protocol == ProtocolVersion.V1_16_1) {
                return id736;
            }
            if (protocol == ProtocolVersion.V1_16_2) {
                return id751;
            }
            if (protocol == ProtocolVersion.V1_16_3) {
                return id753;
            }
            if (protocol == ProtocolVersion.V1_16_5) {
                return id754;
            }
            if (protocol == ProtocolVersion.V1_17) {
                return id755;
            }
            if (protocol == ProtocolVersion.V1_17_1) {
                return id756;
            }
            if (protocol == ProtocolVersion.V1_18) {
                return id757;
            }
            if (protocol == ProtocolVersion.V1_18_2) {
                return id758;
            }
            if (protocol == ProtocolVersion.V1_19) {
                return id759;
            }
            if (protocol == ProtocolVersion.V1_19_1) {
                return id760;
            }
            if (protocol == ProtocolVersion.V1_19_3) {
                return id761;
            }
            if (protocol == ProtocolVersion.V1_19_4) {
                return id762;
            }
            if (protocol == ProtocolVersion.V1_20_1) {
                return id763;
            }
            if (protocol == ProtocolVersion.V1_20_2) {
                return id764;
            }
            if (protocol == ProtocolVersion.V1_20_4) {
                return id765;
            }
            if (protocol == ProtocolVersion.V1_20_5) {
                return id766;
            }
            if (protocol == ProtocolVersion.V1_21) {
                return id767;
            }
            if (protocol == ProtocolVersion.V1_21_2) {
                return id768;
            }
            if (protocol == ProtocolVersion.V1_21_4) {
                return id769;
            }
            if (protocol == ProtocolVersion.V1_21_5) {
                return id770;
            }
            if (protocol == ProtocolVersion.V1_21_6) {
                return id771;
            }
            if (protocol == ProtocolVersion.V1_21_7) {
                return id772;
            }
            if (protocol == ProtocolVersion.V1_21_9) {
                return id773;
            }
            if (protocol == ProtocolVersion.V1_21_11) {
                return id774;
            }
            if (protocol == ProtocolVersion.V26_1) {
                return id775;
            }
            if (protocol == ProtocolVersion.V26_2) {
                return id776;
            }
            return -1;
        }
    }

    /** この表が知っているプロトコル番号の一覧。 */
    public static List<Integer> supportedProtocols() {
        return List.of(ProtocolVersion.V1_7_2, ProtocolVersion.V1_7_10, ProtocolVersion.V1_8, ProtocolVersion.V1_9, ProtocolVersion.V1_9_1, ProtocolVersion.V1_9_2, ProtocolVersion.V1_9_4, ProtocolVersion.V1_10_2, ProtocolVersion.V1_11, ProtocolVersion.V1_11_2, ProtocolVersion.V1_12, ProtocolVersion.V1_12_1, ProtocolVersion.V1_12_2, ProtocolVersion.V1_13, ProtocolVersion.V1_13_1, ProtocolVersion.V1_13_2, ProtocolVersion.V1_14, ProtocolVersion.V1_14_1, ProtocolVersion.V1_14_2, ProtocolVersion.V1_14_3, ProtocolVersion.V1_14_4, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2, ProtocolVersion.V1_16, ProtocolVersion.V1_16_1, ProtocolVersion.V1_16_2, ProtocolVersion.V1_16_3, ProtocolVersion.V1_16_5, ProtocolVersion.V1_17, ProtocolVersion.V1_17_1, ProtocolVersion.V1_18, ProtocolVersion.V1_18_2, ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1, ProtocolVersion.V1_20_2, ProtocolVersion.V1_20_4, ProtocolVersion.V1_20_5, ProtocolVersion.V1_21, ProtocolVersion.V1_21_2, ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11, ProtocolVersion.V26_1, ProtocolVersion.V26_2);
    }

    /** この表が知っているプロトコルか。 */
    public static boolean isSupported(int protocol) {
        return protocol == ProtocolVersion.V1_7_2
                || protocol == ProtocolVersion.V1_7_10
                || protocol == ProtocolVersion.V1_8
                || protocol == ProtocolVersion.V1_9
                || protocol == ProtocolVersion.V1_9_1
                || protocol == ProtocolVersion.V1_9_2
                || protocol == ProtocolVersion.V1_9_4
                || protocol == ProtocolVersion.V1_10_2
                || protocol == ProtocolVersion.V1_11
                || protocol == ProtocolVersion.V1_11_2
                || protocol == ProtocolVersion.V1_12
                || protocol == ProtocolVersion.V1_12_1
                || protocol == ProtocolVersion.V1_12_2
                || protocol == ProtocolVersion.V1_13
                || protocol == ProtocolVersion.V1_13_1
                || protocol == ProtocolVersion.V1_13_2
                || protocol == ProtocolVersion.V1_14
                || protocol == ProtocolVersion.V1_14_1
                || protocol == ProtocolVersion.V1_14_2
                || protocol == ProtocolVersion.V1_14_3
                || protocol == ProtocolVersion.V1_14_4
                || protocol == ProtocolVersion.V1_15
                || protocol == ProtocolVersion.V1_15_1
                || protocol == ProtocolVersion.V1_15_2
                || protocol == ProtocolVersion.V1_16
                || protocol == ProtocolVersion.V1_16_1
                || protocol == ProtocolVersion.V1_16_2
                || protocol == ProtocolVersion.V1_16_3
                || protocol == ProtocolVersion.V1_16_5
                || protocol == ProtocolVersion.V1_17
                || protocol == ProtocolVersion.V1_17_1
                || protocol == ProtocolVersion.V1_18
                || protocol == ProtocolVersion.V1_18_2
                || protocol == ProtocolVersion.V1_19
                || protocol == ProtocolVersion.V1_19_1
                || protocol == ProtocolVersion.V1_19_3
                || protocol == ProtocolVersion.V1_19_4
                || protocol == ProtocolVersion.V1_20_1
                || protocol == ProtocolVersion.V1_20_2
                || protocol == ProtocolVersion.V1_20_4
                || protocol == ProtocolVersion.V1_20_5
                || protocol == ProtocolVersion.V1_21
                || protocol == ProtocolVersion.V1_21_2
                || protocol == ProtocolVersion.V1_21_4
                || protocol == ProtocolVersion.V1_21_5
                || protocol == ProtocolVersion.V1_21_6
                || protocol == ProtocolVersion.V1_21_7
                || protocol == ProtocolVersion.V1_21_9
                || protocol == ProtocolVersion.V1_21_11
                || protocol == ProtocolVersion.V26_1
                || protocol == ProtocolVersion.V26_2;
    }

    /**
     * パケットのIDを引く。
     *
     * @return 対応するID。次の場合は空：名前と状態・方向の組み合わせが
     *         合わない場合、そのプロトコルにそのパケットが存在しない場合
     *         （例：763のCONFIGURATION、763のLOGIN_ACKNOWLEDGED）、
     *         未対応のプロトコルの場合
     */
    public static OptionalInt id(PacketName name, State state, Direction direction, int protocol) {
        if (name == null || state == null || direction == null) {
            return OptionalInt.empty();
        }
        if (name.state() != state || name.direction() != direction) {
            return OptionalInt.empty();
        }
        int id = name.idFor(protocol);
        return id < 0 ? OptionalInt.empty() : OptionalInt.of(id);
    }

    /** {@link #id(PacketName, State, Direction, int)} の簡易版（名前から状態・方向を取る）。 */
    public static OptionalInt id(PacketName name, int protocol) {
        if (name == null) {
            return OptionalInt.empty();
        }
        int id = name.idFor(protocol);
        return id < 0 ? OptionalInt.empty() : OptionalInt.of(id);
    }

    /**
     * ワイヤ上のIDからパケット名を逆引きする。
     *
     * @return 見つかった名前。同じ状態・方向・プロトコルでそのIDを持つ
     *         パケットが無い場合は空
     */
    public static Optional<PacketName> nameFor(int id, State state, Direction direction, int protocol) {
        if (state == null || direction == null || !isSupported(protocol)) {
            return Optional.empty();
        }
        for (PacketName name : PacketName.values()) {
            if (name.state() == state && name.direction() == direction
                    && name.idFor(protocol) == id) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    /**
     * ある状態・方向・プロトコルで使えるパケット名の一覧（ID順）。
     * 対応しない組み合わせ（例：763のCONFIGURATION）は空の一覧になる。
     */
    public static List<PacketName> names(State state, Direction direction, int protocol) {
        List<PacketName> result = new ArrayList<>();
        if (state == null || direction == null || !isSupported(protocol)) {
            return List.copyOf(result);
        }
        for (PacketName name : PacketName.values()) {
            if (name.state() == state && name.direction() == direction
                    && name.idFor(protocol) >= 0) {
                result.add(name);
            }
        }
        result.sort((a, b) -> Integer.compare(a.idFor(protocol), b.idFor(protocol)));
        return List.copyOf(result);
    }
}
