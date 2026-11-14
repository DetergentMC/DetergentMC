#!/usr/bin/env python3
"""ProxySession.java へ Java 1.21.9／1.21.10（プロトコル773）の対応を入れる。

772パッチ（patch-proxysession-772.py）を1段ずらしたもの。生成元は
tools/gen-773-proxysession.py で、EDITS・3ブロック・INSERTS を機械的に
ずらし、773特有の箇所だけ手で直した。

773の要点（772からの差分）:
- クライアント行きのプレイはデバッグ系パケットの挿入でずれる。
  debug_block_value 0x1A・debug_chunk_value 0x1B・debug_entity_value 0x1C・
  debug_event 0x1D の挿入で 0x1A 以降が+4、game_test_highlight_pos 0x27 の
  挿入で 0x27 以降がさらに+1（計+5）。
  Join Game 0x2B→0x30、Spawn Position 0x5A→0x5F、Chunk Data 0x27→0x2C、
  Keep Alive 0x26→0x2B、Ping 0x36→0x3B、Start Configuration 0x6F→0x74、
  Disconnect 0x1C→0x20、Respawn 0x4B→0x50、Add Resource Pack 0x4A→0x4F。
  新規のデバッグ系5件と game_test_highlight_pos は旧クライアント向けに落とす。
- サーバー行きのプレイは番号が変わらない。debug_sample_subscription
  （0x16）は773で削除され、同じ 0x16 に debug_subscription_request が
  入る（形式が違うため別パケットとして扱い、旧サーバー向けに落とす）。
- 設定フェーズも番号が変わらない。新規: Code of Conduct（config 0x13）と
  Accept Code of Conduct（config 0x09）。旧クライアント・旧サーバー向けに落とす。
- チャットの checksum（u8）は770・771・772・773ともあるため、773⇔772では
  付け足しも剥がしもししない（番号だけ付け替える）。770⇔771の変換器は
  そのまま（769には checksum が無い）。
- Vehicle Move の onGround は769・770・771・772・773ともある（768に無い）。
  773⇔772では素通しする。
- Window Click は771・772・773とも HashedSlot のため落とす。Set Creative Slot は
  772・773とも UntrustedSlot のため、アイテム／成分を付け替えるだけ。
- Player Chat は先頭に globalIndex が770・771・772・773ともあるため素通しする。
  769サーバーからの player_chat にはプロキシが 0 から数えて付ける
  （770／771／772／773クライアント共用の chatGlobalIndex）。
- 設定フェーズは772と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 772→773 が要る（minecraft:dialog は
  772・773ともある。jukebox_song・絵画は同一。銅ゴーレム像などの
  ブロックエンティティは Registry Data には出ない）。
  Show Dialog（config 0x12）／Clear Dialog（0x11）は旧クライアント向けに
  落とす。Custom Click Action（config 0x08）は773サーバーへ通し、
  旧サーバー向けに落とす。Custom Report Details／Server Links
  （config サーバー行き）は772で削除されたため、773サーバー向けも落とす。
- 経験値オーブは770・771・772・773とも通常の Spawn Entity（type experience_orb）。
  772の to769ServerPacketFrom772 の専用分岐は773→772では使わない。
- ハッピーガスト（773: 57）は772では 56 に写る（同じ種別）。
  771以前ではガスト（55）に代替する（invert ではブタになるが、見た目が
  近いガストを選ぶ。メタデータの個体差はプロキシが種別を追跡しないため残る）。
- ロケーターバー（tracked_waypoint 0x83→0x88）／ダイアログ（show_dialog 0x85→0x8A／
  clear_dialog 0x84→0x89）は772・773にだけあるため、771以前のクライアント向けに落とす。
- 内部773サーバー＋773クライアントはネイティブ（素通し）。

使い方: repo ルートで `python3 detergentmc-protocol/tools/patch-proxysession-773.py`
（--check で差分確認）。全アンカーは一意でなければならない。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ProxySession.java"


EDITS = [

    ('constructor javadoc',
     '     * @param serverProtocol 内部バニラのプロトコル（763＝1.20.1、\n     *                       764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、\n     *                       767＝1.21／1.21.1、768＝1.21.2／1.21.3、\n     *                       769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7）',
     '     * @param serverProtocol 内部バニラのプロトコル（763＝1.20.1、\n     *                       764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、\n     *                       767＝1.21／1.21.1、768＝1.21.2／1.21.3、\n     *                       769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10）'),
    ('constructor validation',
     '                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6\n                && serverProtocol != ProtocolVersion.V1_21_7) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5), 771 (1.21.6) or 772 (1.21.7): "\n',
     '                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6\n                && serverProtocol != ProtocolVersion.V1_21_7\n                && serverProtocol != ProtocolVersion.V1_21_9) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7) or 773 (1.21.9/1.21.10): "\n'),
    ('serverProtocol() javadoc',
     '    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7）。 */',
     '    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10）。 */'),
    ('pack773 field',
     '    /**\n     * 768クライアントに送るリソースパック（pack_format 42）。\n     * 内部が768でない場合に使う差し替え用。内部が768の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack768;\n\n    /**\n     * 769クライアントに送るリソースパック（pack_format 46）。\n     * 内部が769でない場合に使う差し替え用。内部が769の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack769;\n\n    /**\n     * 770クライアントに送るリソースパック（pack_format 55）。\n     * 内部が770でない場合に使う差し替え用。内部が770の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack770;\n\n    /**\n     * 771クライアントに送るリソースパック（pack_format 63）。\n     * 内部が771でない場合に使う差し替え用。内部が771の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack771;\n\n    /**\n     * 772クライアントに送るリソースパック（pack_format 64）。\n     * 内部が772でない場合に使う差し替え用。内部が772の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack772;',
     '    /**\n     * 768クライアントに送るリソースパック（pack_format 42）。\n     * 内部が768でない場合に使う差し替え用。内部が768の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack768;\n\n    /**\n     * 769クライアントに送るリソースパック（pack_format 46）。\n     * 内部が769でない場合に使う差し替え用。内部が769の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack769;\n\n    /**\n     * 770クライアントに送るリソースパック（pack_format 55）。\n     * 内部が770でない場合に使う差し替え用。内部が770の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack770;\n\n    /**\n     * 771クライアントに送るリソースパック（pack_format 63）。\n     * 内部が771でない場合に使う差し替え用。内部が771の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack771;\n\n    /**\n     * 772クライアントに送るリソースパック（pack_format 64）。\n     * 内部が772でない場合に使う差し替え用。内部が772の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack772;\n\n    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;'),
    ('setPack773/pack773',
     '    /** 768クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack768() {\n        return pack768;\n    }\n\n    /** 769クライアントに送るリソースパックを設定する（内部769でない場合に使う）。 */\n    public void setPack769(ResourcePack pack769) {\n        this.pack769 = pack769;\n    }\n\n    /** 769クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack769() {\n        return pack769;\n    }\n\n    /** 770クライアントに送るリソースパックを設定する（内部770でない場合に使う）。 */\n    public void setPack770(ResourcePack pack770) {\n        this.pack770 = pack770;\n    }\n\n    /** 770クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack770() {\n        return pack770;\n    }\n\n    /** 771クライアントに送るリソースパックを設定する（内部771でない場合に使う）。 */\n    public void setPack771(ResourcePack pack771) {\n        this.pack771 = pack771;\n    }\n\n    /** 771クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack771() {\n        return pack771;\n    }\n\n    /** 772クライアントに送るリソースパックを設定する（内部772でない場合に使う）。 */\n    public void setPack772(ResourcePack pack772) {\n        this.pack772 = pack772;\n    }\n\n    /** 772クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack772() {\n        return pack772;\n    }',
     '    /** 768クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack768() {\n        return pack768;\n    }\n\n    /** 769クライアントに送るリソースパックを設定する（内部769でない場合に使う）。 */\n    public void setPack769(ResourcePack pack769) {\n        this.pack769 = pack769;\n    }\n\n    /** 769クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack769() {\n        return pack769;\n    }\n\n    /** 770クライアントに送るリソースパックを設定する（内部770でない場合に使う）。 */\n    public void setPack770(ResourcePack pack770) {\n        this.pack770 = pack770;\n    }\n\n    /** 770クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack770() {\n        return pack770;\n    }\n\n    /** 771クライアントに送るリソースパックを設定する（内部771でない場合に使う）。 */\n    public void setPack771(ResourcePack pack771) {\n        this.pack771 = pack771;\n    }\n\n    /** 771クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack771() {\n        return pack771;\n    }\n\n    /** 772クライアントに送るリソースパックを設定する（内部772でない場合に使う）。 */\n    public void setPack772(ResourcePack pack772) {\n        this.pack772 = pack772;\n    }\n\n    /** 772クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack772() {\n        return pack772;\n    }\n\n    /** 773クライアントに送るリソースパックを設定する（内部773でない場合に使う）。 */\n    public void setPack773(ResourcePack pack773) {\n        this.pack773 = pack773;\n    }\n\n    /** 773クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack773() {\n        return pack773;\n    }'),
    ('packOverrideForClient javadoc',
     '     * 出し分けの基準はクライアントのプロトコルである：772→pack772\n     * （pack_format 64）、771→pack771\n     * （pack_format 63）、770→pack770\n     * （pack_format 55）、769→pack769\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767',
     '     * 出し分けの基準はクライアントのプロトコルである：773→pack773\n     * （pack_format 69）、772→pack772\n     * （pack_format 64）、771→pack771\n     * （pack_format 63）、770→pack770\n     * （pack_format 55）、769→pack769\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767'),
    ('packOverrideForClient branch',
     '        if (clientProtocol == ProtocolVersion.V1_21_7) {\n            return pack772 != null ? pack772\n                    : pack771 != null ? pack771\n                    : pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_6) {\n            return pack771 != null ? pack771\n                    : pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_5) {\n            return pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_4) {\n            return pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_2) {\n            return pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }',
     '        if (clientProtocol == ProtocolVersion.V1_21_9) {\n            return pack773 != null ? pack773\n                    : pack772 != null ? pack772\n                    : pack771 != null ? pack771\n                    : pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_7) {\n            return pack772 != null ? pack772\n                    : pack771 != null ? pack771\n                    : pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_6) {\n            return pack771 != null ? pack771\n                    : pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_5) {\n            return pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_4) {\n            return pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_2) {\n            return pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }'),
    ('is773/isServer773',
     '    /** 内部が 1.21.2／1.21.3（768）か。 */\n    private boolean isServer768() {\n        return serverProtocol == ProtocolVersion.V1_21_2;\n    }\n\n    /** クライアントが 1.21.4（769）か。 */\n    private boolean is769() {\n        return clientProtocol == ProtocolVersion.V1_21_4;\n    }\n\n    /** 内部が 1.21.4（769）か。 */\n    private boolean isServer769() {\n        return serverProtocol == ProtocolVersion.V1_21_4;\n    }\n\n    /** クライアントが 1.21.5（770）か。 */\n    private boolean is770() {\n        return clientProtocol == ProtocolVersion.V1_21_5;\n    }\n\n    /** 内部が 1.21.5（770）か。 */\n    private boolean isServer770() {\n        return serverProtocol == ProtocolVersion.V1_21_5;\n    }\n\n    /** クライアントが 1.21.6（771）か。 */\n    private boolean is771() {\n        return clientProtocol == ProtocolVersion.V1_21_6;\n    }\n\n    /** 内部が 1.21.6（771）か。 */\n    private boolean isServer771() {\n        return serverProtocol == ProtocolVersion.V1_21_6;\n    }\n\n    /** クライアントが 1.21.7（772）か。 */\n    private boolean is772() {\n        return clientProtocol == ProtocolVersion.V1_21_7;\n    }\n\n    /** 内部が 1.21.7（772）か。 */\n    private boolean isServer772() {\n        return serverProtocol == ProtocolVersion.V1_21_7;\n    }',
     '    /** 内部が 1.21.2／1.21.3（768）か。 */\n    private boolean isServer768() {\n        return serverProtocol == ProtocolVersion.V1_21_2;\n    }\n\n    /** クライアントが 1.21.4（769）か。 */\n    private boolean is769() {\n        return clientProtocol == ProtocolVersion.V1_21_4;\n    }\n\n    /** 内部が 1.21.4（769）か。 */\n    private boolean isServer769() {\n        return serverProtocol == ProtocolVersion.V1_21_4;\n    }\n\n    /** クライアントが 1.21.5（770）か。 */\n    private boolean is770() {\n        return clientProtocol == ProtocolVersion.V1_21_5;\n    }\n\n    /** 内部が 1.21.5（770）か。 */\n    private boolean isServer770() {\n        return serverProtocol == ProtocolVersion.V1_21_5;\n    }\n\n    /** クライアントが 1.21.6（771）か。 */\n    private boolean is771() {\n        return clientProtocol == ProtocolVersion.V1_21_6;\n    }\n\n    /** 内部が 1.21.6（771）か。 */\n    private boolean isServer771() {\n        return serverProtocol == ProtocolVersion.V1_21_6;\n    }\n\n    /** クライアントが 1.21.7（772）か。 */\n    private boolean is772() {\n        return clientProtocol == ProtocolVersion.V1_21_7;\n    }\n\n    /** 内部が 1.21.7（772）か。 */\n    private boolean isServer772() {\n        return serverProtocol == ProtocolVersion.V1_21_7;\n    }\n\n    /** クライアントが 1.21.9（773）か。 */\n    private boolean is773() {\n        return clientProtocol == ProtocolVersion.V1_21_9;\n    }\n\n    /** 内部が 1.21.9（773）か。 */\n    private boolean isServer773() {\n        return serverProtocol == ProtocolVersion.V1_21_9;\n    }'),
    ('login client list',
     '                if (is764() || is765() || is766() || is767() || is768() || is769() || is770() || is771() || is772()) {',
     '                if (is764() || is765() || is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {'),
    ('login comment',
     '                    // 764／765／766／767／768／769／770／771／772クライアント：クライアントもバニラもこれから\n                    // 設定フェーズに入る。内部765＋764クライアントと、',
     '                    // 764／765／766／767／768／769／770／771／772／773クライアント：クライアントもバニラもこれから\n                    // 設定フェーズに入る。内部765＋764クライアントと、'),
    ('login 763-branch comment',
     '                // （Login Acknowledged は空、Client Information は\n                // 3バージョンで同じフィールド）である。768／769／770／771／772は Client\n                // Information の末尾に particleStatus があるため、\n                // 付け足して送る（付けないと768／769／770／771／772のバニラが切断する）。',
     '                // （Login Acknowledged は空、Client Information は\n                // 3バージョンで同じフィールド）である。768／769／770／771／772／773は Client\n                // Information の末尾に particleStatus があるため、\n                // 付け足して送る（付けないと768／769／770／771／772／773のバニラが切断する）。'),
    ('login clientInformation',
     '                byte[] clientInformation = isServer768() || isServer769() || isServer770() || isServer771() || isServer772()\n                        ? PlayTranslator.translateClientInformation767To768(',
     '                byte[] clientInformation = isServer768() || isServer769() || isServer770() || isServer771() || isServer772() || isServer773()\n                        ? PlayTranslator.translateClientInformation767To768('),
    ('synthesized config registry loop',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {\n            // 766／767／768／769／770／771／772：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is771()) {\n                    // 771：766→767→768→769→770→771へ五段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                }\n                if (is772()) {\n                    // 772：766→767→768→769→770→771→772へ六段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                }\n                toClient.add(new MinecraftPacket(0x07, registryPayload));\n            }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\n            // 766／767／768／769／770／771／772／773：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is772()) {\n                    // 772：766→767→768→769→770→771→772へ六段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                }\n                if (is773()) {\n                    // 773：766→767→768→769→770→771→772→773へ七段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                }\n                toClient.add(new MinecraftPacket(0x07, registryPayload));\n            }'),
    ('synthesized ackId',
     '        int ackId = is766() || is767() || is768() || is769() || is770() || is771() || is772() ? 0x03 : 0x02;',
     '        int ackId = is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() ? 0x03 : 0x02;'),
    ('synthesized add-pack',
     '            if (is768() || is769() || is770() || is771() || is772()) {\n                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。772は 0x4A。\n                toClient.add(new MinecraftPacket(is770() || is771() || is772() ? 0x4A : 0x4B,',
     '            if (is768() || is769() || is770() || is771() || is772() || is773()) {\n                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。773は 0x4A。\n                toClient.add(new MinecraftPacket(is770() || is771() || is772() || is773() ? 0x4A : 0x4B,'),
    ('startConfigurationClientId',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : is770() || is771() || is772() ? 0x6F : 0x69);\n        }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : is770() || is771() || is772() || is773() ? 0x6F : 0x69);\n        }'),
    ('spawnPositionClientId',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : is770() || is771() || is772() ? 0x5A : 0x56);\n        }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : is770() || is771() || is772() || is773() ? 0x5A : 0x56);\n        }'),
    ('keepAliveClientId',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x27 : 0x26);\n        }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x27 : 0x26);\n        }'),
    ('pingClientId',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_PING,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : is770() || is771() || is772() ? 0x36 : 0x35);\n        }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_PING,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : is770() || is771() || is772() || is773() ? 0x36 : 0x35);\n        }'),
    ('handleClientPlay dispatch',
     '        // 772クライアント：番号を内部向けに付け替える。内部771向けは\n        // 772→771へ変換してから送る。内部768／767向けは772→771→770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is772()) {\n            return handleClientPlay772(packet);\n        }\n        // 内部772：763／764／765／766／767／768／769／770／771クライアントの番号を772向けに付け替える。\n        if (isServer772()) {\n            return handleClientPlayTo772(packet);\n        }\n        // 771クライアント：番号を内部向けに付け替える。内部770向けは\n        // 771→770へ変換してから送る。内部768／767向けは771→770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is771()) {\n            return handleClientPlay771(packet);\n        }\n        // 内部771：763／764／765／766／767／768／769／770クライアントの番号を771向けに付け替える。\n        if (isServer771()) {\n            return handleClientPlayTo771(packet);\n        }\n        // 770クライアント：番号を内部向けに付け替える。内部769向けは\n        // 770→769へ変換してから送る。内部768／767向けは770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is770()) {\n            return handleClientPlay770(packet);\n        }\n        // 内部770：763／764／765／766／767／768／769クライアントの番号を770向けに付け替える。\n        if (isServer770()) {\n            return handleClientPlayTo770(packet);\n        }\n        // 769クライアント：番号を内部向けに付け替える。内部768向けは\n        // 769→768へ変換してから送る。内部767／766向けは769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is769()) {\n            return handleClientPlay769(packet);\n        }\n        // 内部769：763／764／765／766／767／768クライアントの番号を769向けに付け替える。\n        if (isServer769()) {\n            return handleClientPlayTo769(packet);\n        }\n        // 768クライアント：番号を内部向けに付け替える。内部766／767向けは\n        // スロットを768→767へ変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is768()) {\n            return handleClientPlay768(packet);\n        }\n        // 内部768：763／764／765／766／767クライアントの番号を768向けに付け替える。\n        if (isServer768()) {\n            return handleClientPlayTo768(packet);\n        }',
     '        // 773クライアント：番号を内部向けに付け替える。内部772向けは\n        // 773→772へ変換してから送る。内部768／767向けは773→772→771→770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is773()) {\n            return handleClientPlay773(packet);\n        }\n        // 内部773：763／764／765／766／767／768／769／770／771／772クライアントの番号を773向けに付け替える。\n        if (isServer773()) {\n            return handleClientPlayTo773(packet);\n        }\n        // 772クライアント：番号を内部向けに付け替える。内部771向けは\n        // 772→771へ変換してから送る。内部768／767向けは772→771→770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is772()) {\n            return handleClientPlay772(packet);\n        }\n        // 内部772：763／764／765／766／767／768／769／770／771クライアントの番号を772向けに付け替える。\n        if (isServer772()) {\n            return handleClientPlayTo772(packet);\n        }\n        // 771クライアント：番号を内部向けに付け替える。内部770向けは\n        // 771→770へ変換してから送る。内部768／767向けは771→770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is771()) {\n            return handleClientPlay771(packet);\n        }\n        // 内部771：763／764／765／766／767／768／769／770クライアントの番号を771向けに付け替える。\n        if (isServer771()) {\n            return handleClientPlayTo771(packet);\n        }\n        // 770クライアント：番号を内部向けに付け替える。内部769向けは\n        // 770→769へ変換してから送る。内部768／767向けは770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is770()) {\n            return handleClientPlay770(packet);\n        }\n        // 内部770：763／764／765／766／767／768／769クライアントの番号を770向けに付け替える。\n        if (isServer770()) {\n            return handleClientPlayTo770(packet);\n        }\n        // 769クライアント：番号を内部向けに付け替える。内部768向けは\n        // 769→768へ変換してから送る。内部767／766向けは769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is769()) {\n            return handleClientPlay769(packet);\n        }\n        // 内部769：763／764／765／766／767／768クライアントの番号を769向けに付け替える。\n        if (isServer769()) {\n            return handleClientPlayTo769(packet);\n        }\n        // 768クライアント：番号を内部向けに付け替える。内部766／767向けは\n        // スロットを768→767へ変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is768()) {\n            return handleClientPlay768(packet);\n        }\n        // 内部768：763／764／765／766／767クライアントの番号を768向けに付け替える。\n        if (isServer768()) {\n            return handleClientPlayTo768(packet);\n        }'),
    ('handleServerPlay dispatch',
     '        // 内部772：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（771と同じ）。\n        if (isServer772()) {\n            return handleServerPlayFrom772(packet);\n        }\n        // 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（770と同じ）。\n        if (isServer771()) {\n            return handleServerPlayFrom771(packet);\n        }\n        // 内部770：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（769から -1）。\n        if (isServer770()) {\n            return handleServerPlayFrom770(packet);\n        }\n        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70（768と同じ番号）。\n        if (isServer769()) {\n            return handleServerPlayFrom769(packet);\n        }\n        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70。\n        if (isServer768()) {\n            return handleServerPlayFrom768(packet);\n        }',
     '        // 内部773：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（772と同じ）。\n        if (isServer773()) {\n            return handleServerPlayFrom773(packet);\n        }\n        // 内部772：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（771と同じ）。\n        if (isServer772()) {\n            return handleServerPlayFrom772(packet);\n        }\n        // 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（770と同じ）。\n        if (isServer771()) {\n            return handleServerPlayFrom771(packet);\n        }\n        // 内部770：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（769から -1）。\n        if (isServer770()) {\n            return handleServerPlayFrom770(packet);\n        }\n        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70（768と同じ番号）。\n        if (isServer769()) {\n            return handleServerPlayFrom769(packet);\n        }\n        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70。\n        if (isServer768()) {\n            return handleServerPlayFrom768(packet);\n        }'),
    ('handleServerConfiguration 763 branch',
     '        if (is763()) {\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }',
     '        if (is763()) {\n            if (isServer773()) {\n                return handleServerConfiguration763From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }'),
    ('handleServerConfiguration 773 branch',
     '        // 772クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is772()) {',
     '        // 773クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is773()) {\n            if (isServer773()) {\n                // 内部773＋773クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration773From772(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration773From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration773From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration773From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration773From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration773From766(packet);\n            }\n            return handleServerConfiguration773(packet, isServer764());\n        }\n        // 772クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is772()) {'),
    ('handleServerConfiguration 767 branch',
     '            if (isServer772()) {\n                return handleServerConfiguration767From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration767From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration767From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration767From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration767From768(packet);\n            }',
     '            if (isServer773()) {\n                return handleServerConfiguration767From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration767From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration767From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration767From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration767From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration767From768(packet);\n            }'),
    ('handleServerConfiguration 766 branch',
     '            if (isServer772()) {\n                return handleServerConfiguration766From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration766From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration766From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration766From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration766From768(packet);\n            }',
     '            if (isServer773()) {\n                return handleServerConfiguration766From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration766From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration766From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration766From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration766From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration766From768(packet);\n            }'),
    ('handleServerConfiguration From773 branch',
     '        // 内部772の場合は番号と Registry Data の形式を変換する。\n        if (isServer772()) {\n            return handleServerConfigurationFrom772(packet, is764());\n        }\n        // 内部771の場合は番号と Registry Data の形式を変換する。\n        if (isServer771()) {\n            return handleServerConfigurationFrom771(packet, is764());\n        }\n        // 内部770の場合は番号と Registry Data の形式を変換する。\n        if (isServer770()) {\n            return handleServerConfigurationFrom770(packet, is764());\n        }\n        // 内部769の場合は番号と Registry Data の形式を変換する。\n        if (isServer769()) {\n            return handleServerConfigurationFrom769(packet, is764());\n        }\n        // 内部768の場合は番号と Registry Data の形式を変換する。\n        if (isServer768()) {\n            return handleServerConfigurationFrom768(packet, is764());\n        }',
     '        // 内部773の場合は番号と Registry Data の形式を変換する。\n        if (isServer773()) {\n            return handleServerConfigurationFrom773(packet, is764());\n        }\n        // 内部772の場合は番号と Registry Data の形式を変換する。\n        if (isServer772()) {\n            return handleServerConfigurationFrom772(packet, is764());\n        }\n        // 内部771の場合は番号と Registry Data の形式を変換する。\n        if (isServer771()) {\n            return handleServerConfigurationFrom771(packet, is764());\n        }\n        // 内部770の場合は番号と Registry Data の形式を変換する。\n        if (isServer770()) {\n            return handleServerConfigurationFrom770(packet, is764());\n        }\n        // 内部769の場合は番号と Registry Data の形式を変換する。\n        if (isServer769()) {\n            return handleServerConfigurationFrom769(packet, is764());\n        }\n        // 内部768の場合は番号と Registry Data の形式を変換する。\n        if (isServer768()) {\n            return handleServerConfigurationFrom768(packet, is764());\n        }'),
    ('handleClientConfiguration 773 branch',
     '        // 768クライアント：番号を764／765向けに付け替える。\n        if (is768()) {\n            return handleClientConfiguration768(packet);\n        }\n        // 769クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は768と同じため768の処理を使い回す。\n        if (is769()) {\n            return handleClientConfiguration769(packet);\n        }\n        // 770クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は769と同じため769の処理を使い回す。\n        if (is770()) {\n            return handleClientConfiguration770(packet);\n        }\n        // 771クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は770と同じため770の処理を使い回す。\n        if (is771()) {\n            return handleClientConfiguration771(packet);\n        }\n        // 772クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は771と同じため771の処理を使い回す。\n        if (is772()) {\n            return handleClientConfiguration772(packet);\n        }',
     '        // 768クライアント：番号を764／765向けに付け替える。\n        if (is768()) {\n            return handleClientConfiguration768(packet);\n        }\n        // 769クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は768と同じため768の処理を使い回す。\n        if (is769()) {\n            return handleClientConfiguration769(packet);\n        }\n        // 770クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は769と同じため769の処理を使い回す。\n        if (is770()) {\n            return handleClientConfiguration770(packet);\n        }\n        // 771クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は770と同じため770の処理を使い回す。\n        if (is771()) {\n            return handleClientConfiguration771(packet);\n        }\n        // 772クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は771と同じため771の処理を使い回す。\n        if (is772()) {\n            return handleClientConfiguration772(packet);\n        }\n        // 773クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は772と同じため772の処理を使い回す。\n        if (is773()) {\n            return handleClientConfiguration773(packet);\n        }'),
    ('handleClientConfiguration 767+773 branch',
     '        // 767クライアント＋内部768：番号を768向けに付け替える。766クライアントと\n        // 同じ（設定フェーズの番号・形式は766＝767）。\n        if (is767() && isServer768()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer769()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer770()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer771()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部772：番号を772向けに付け替える。771と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer772()) {\n            return handleClientConfiguration767To768(packet);\n        }',
     '        // 767クライアント＋内部768：番号を768向けに付け替える。766クライアントと\n        // 同じ（設定フェーズの番号・形式は766＝767）。\n        if (is767() && isServer768()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer769()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer770()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer771()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部772：番号を772向けに付け替える。771と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer772()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部773：番号を773向けに付け替える。772と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer773()) {\n            return handleClientConfiguration767To768(packet);\n        }'),
    ('handleClientConfiguration 766+773 branch',
     '        // 766クライアント＋内部768：番号を768向けに付け替える。\n        if (is766() && isServer768()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer769()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer770()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer771()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部772：番号を772向けに付け替える。771と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer772()) {\n            return handleClientConfiguration766To768(packet);\n        }',
     '        // 766クライアント＋内部768：番号を768向けに付け替える。\n        if (is766() && isServer768()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer769()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer770()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer771()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部772：番号を772向けに付け替える。771と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer772()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部773：番号を773向けに付け替える。772と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer773()) {\n            return handleClientConfiguration766To768(packet);\n        }'),
    ('handleClientConfiguration To773 branch',
     '        // 764／765クライアント＋内部772：番号を772向けに付け替える。771と\n        // 同じ番号・形式のため771向けの処理を使い回す。\n        if (isServer772()) {\n            return handleClientConfigurationTo772(packet);\n        }\n        // 764／765クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため770向けの処理を使い回す。\n        if (isServer771()) {\n            return handleClientConfigurationTo771(packet);\n        }\n        // 764／765クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため769向けの処理を使い回す。\n        if (isServer770()) {\n            return handleClientConfigurationTo770(packet);\n        }\n        // 764／765クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (isServer769()) {\n            return handleClientConfigurationTo769(packet);\n        }\n        // 764／765クライアント＋内部768：番号を768向けに付け替える。\n        if (isServer768()) {\n            return handleClientConfigurationTo768(packet);\n        }',
     '        // 764／765クライアント＋内部773：番号を773向けに付け替える。772と\n        // 同じ番号・形式のため772向けの処理を使い回す。\n        if (isServer773()) {\n            return handleClientConfigurationTo773(packet);\n        }\n        // 764／765クライアント＋内部772：番号を772向けに付け替える。771と\n        // 同じ番号・形式のため771向けの処理を使い回す。\n        if (isServer772()) {\n            return handleClientConfigurationTo772(packet);\n        }\n        // 764／765クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため770向けの処理を使い回す。\n        if (isServer771()) {\n            return handleClientConfigurationTo771(packet);\n        }\n        // 764／765クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため769向けの処理を使い回す。\n        if (isServer770()) {\n            return handleClientConfigurationTo770(packet);\n        }\n        // 764／765クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (isServer769()) {\n            return handleClientConfigurationTo769(packet);\n        }\n        // 764／765クライアント＋内部768：番号を768向けに付け替える。\n        if (isServer768()) {\n            return handleClientConfigurationTo768(packet);\n        }'),
    ('chatGlobalIndex field',
     '    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;\n\n    /**\n     * 770／771／772クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;',
     '    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;\n\n    /**\n     * 770／771／772／773クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;'),
    ('handleServerPlayFrom769 773 branch',
     '        // 772クライアント：769→770→771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay769To772(packet);\n        }\n        // 771クライアント：769→770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay769To771(packet);\n        }\n        // 770クライアント：769→770へ変換する。\n        if (is770()) {\n            return handleServerPlay769To770(packet);\n        }\n        // 768クライアント：769→768へ変換する。\n        if (is768()) {\n            return handleServerPlay769To768(packet);\n        }',
     '        // 773クライアント：769→770→771→772→773へ変換する。\n        if (is773()) {\n            return handleServerPlay769To773(packet);\n        }\n        // 772クライアント：769→770→771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay769To772(packet);\n        }\n        // 771クライアント：769→770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay769To771(packet);\n        }\n        // 770クライアント：769→770へ変換する。\n        if (is770()) {\n            return handleServerPlay769To770(packet);\n        }\n        // 768クライアント：769→768へ変換する。\n        if (is768()) {\n            return handleServerPlay769To768(packet);\n        }'),
    ('handleServerPlayFrom769 Respawn 773',
     '        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n        if (packet.id() == 0x4C) {\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {\n                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is768()) {',
     '        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n        if (packet.id() == 0x4C) {\n            if (is773()) {\n                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {\n                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is768()) {'),
    ('handleServerPlayFrom769 Disconnect 773',
     '            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768()) {\n                // 766／767／768クライアント：Disconnect は 0x1D（NBT）のまま。',
     '            if (is773()) {\n                // 773クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768()) {\n                // 766／767／768クライアント：Disconnect は 0x1D（NBT）のまま。'),
    ('handleServerJoinGame769 773 branch',
     '        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is768()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));',
     '        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is768()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));'),
    ('handleServerPlayFrom768 Respawn 773',
     '        // Respawn（768：0x4C）。\n        if (packet.id() == 0x4C) {\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {\n                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is769()) {',
     '        // Respawn（768：0x4C）。\n        if (packet.id() == 0x4C) {\n            if (is773()) {\n                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {\n                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is769()) {'),
    ('handleServerPlayFrom768 Disconnect 773',
     '            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is769()) {\n                // 766／767／769クライアント：Disconnect は 0x1D（NBT）のまま。',
     '            if (is773()) {\n                // 773クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is769()) {\n                // 766／767／769クライアント：Disconnect は 0x1D（NBT）のまま。'),
    ('handleServerPlayFrom768 773 branch',
     '        // 772クライアント：スロット等は768→769→770→771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay768To772(packet);\n        }\n        // 771クライアント：スロット等は768→769→770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay768To771(packet);\n        }\n        // 770クライアント：スロット等は768→769→770へ変換する。\n        if (is770()) {\n            return handleServerPlay768To770(packet);\n        }\n        // 769クライアント：スロット等は768→769へ変換する。\n        if (is769()) {\n            return handleServerPlay768To769(packet);\n        }',
     '        // 773クライアント：スロット等は768→769→770→771→772→773へ変換する。\n        if (is773()) {\n            return handleServerPlay768To773(packet);\n        }\n        // 772クライアント：スロット等は768→769→770→771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay768To772(packet);\n        }\n        // 771クライアント：スロット等は768→769→770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay768To771(packet);\n        }\n        // 770クライアント：スロット等は768→769→770へ変換する。\n        if (is770()) {\n            return handleServerPlay768To770(packet);\n        }\n        // 769クライアント：スロット等は768→769へ変換する。\n        if (is769()) {\n            return handleServerPlay768To769(packet);\n        }'),
    ('handleServerJoinGame768 773 branch',
     '        if (is772()) {\n            // 772クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            // 771クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            // 770クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {\n            // 769クライアント：Join Game は768と同形式のためそのまま通す。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));',
     '        if (is773()) {\n            // 773クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            // 772クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            // 771クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            // 770クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {\n            // 769クライアント：Join Game は768と同形式のためそのまま通す。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));'),
    ('handleServerPlayFrom766 773 branch',
     '        // 772クライアント：スロット等は766／767→768→769→770→771→772へ変換する。\n        // 内部766は 766→767→768→769→770→771→772 の六段、内部767は 767→768→769→770→771→772 の五段で送る。\n        if (is772()) {\n            return isServer766() ? handleServerPlay766To772(packet)\n                    : handleServerPlay767To772(packet);\n        }\n        // 771クライアント：スロット等は766／767→768→769→770→771へ変換する。\n        // 内部766は 766→767→768→769→770→771 の五段、内部767は 767→768→769→770→771 の四段で送る。\n        if (is771()) {\n            return isServer766() ? handleServerPlay766To771(packet)\n                    : handleServerPlay767To771(packet);\n        }\n        // 770クライアント：スロット等は766／767→768→769→770へ変換する。\n        // 内部766は 766→767→768→769→770 の四段、内部767は 767→768→769→770 の三段で送る。\n        if (is770()) {\n            return isServer766() ? handleServerPlay766To770(packet)\n                    : handleServerPlay767To770(packet);\n        }\n        // 769クライアント：スロット等は766／767→768→769へ変換する。\n        // 内部766は 766→767→768→769 の三段、内部767は 767→768→769 の二段で送る。\n        if (is769()) {\n            return isServer766() ? handleServerPlay766To769(packet)\n                    : handleServerPlay767To769(packet);\n        }',
     '        // 773クライアント：スロット等は766／767→768→769→770→771→772→773へ変換する。\n        // 内部766は 766→767→768→769→770→771→772→773 の七段、内部767は 767→768→769→770→771→772→773 の六段で送る。\n        if (is773()) {\n            return isServer766() ? handleServerPlay766To773(packet)\n                    : handleServerPlay767To773(packet);\n        }\n        // 772クライアント：スロット等は766／767→768→769→770→771→772へ変換する。\n        // 内部766は 766→767→768→769→770→771→772 の六段、内部767は 767→768→769→770→771→772 の五段で送る。\n        if (is772()) {\n            return isServer766() ? handleServerPlay766To772(packet)\n                    : handleServerPlay767To772(packet);\n        }\n        // 771クライアント：スロット等は766／767→768→769→770→771へ変換する。\n        // 内部766は 766→767→768→769→770→771 の五段、内部767は 767→768→769→770→771 の四段で送る。\n        if (is771()) {\n            return isServer766() ? handleServerPlay766To771(packet)\n                    : handleServerPlay767To771(packet);\n        }\n        // 770クライアント：スロット等は766／767→768→769→770へ変換する。\n        // 内部766は 766→767→768→769→770 の四段、内部767は 767→768→769→770 の三段で送る。\n        if (is770()) {\n            return isServer766() ? handleServerPlay766To770(packet)\n                    : handleServerPlay767To770(packet);\n        }\n        // 769クライアント：スロット等は766／767→768→769へ変換する。\n        // 内部766は 766→767→768→769 の三段、内部767は 767→768→769 の二段で送る。\n        if (is769()) {\n            return isServer766() ? handleServerPlay766To769(packet)\n                    : handleServerPlay767To769(packet);\n        }'),
    ('handleServerPlayFrom763 ResourcePack 773',
     '            PendingPack pack = parseResourcePack764(packet.payload());\n            lastSentPack = pack;\n            if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {\n                return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : is770() || is771() || is772() ? 0x4A : 0x46,',
     '            PendingPack pack = parseResourcePack764(packet.payload());\n            lastSentPack = pack;\n            if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {\n                return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : is770() || is771() || is772() || is773() ? 0x4A : 0x46,'),
    ('handleServerPlayFrom763 Disconnect 773',
     '            if (is772()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is771()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is770()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1D, payload)), false, true);\n            }',
     '            if (is773()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is772()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is771()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is770()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1D, payload)), false, true);\n            }'),
    ('handleServerPlayFrom763 Respawn 773',
     '        if (name == PacketName.PLAY_RESPAWN) {\n            if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {',
     '        if (name == PacketName.PLAY_RESPAWN) {\n            if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {'),
    ('handleServerPlayFrom763 Advancements 773',
     '        if (name == PacketName.PLAY_ADVANCEMENTS) {\n            if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {',
     '        if (name == PacketName.PLAY_ADVANCEMENTS) {\n            if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {'),
    ('handleServerPlayFrom763 final drop 773',
     '        // 766／767／768／769／770／771／772向け：チャット・表示系は763が JSON 文字列・766が NBT のため、\n        // JSON のまま送ると誤読される。チャンク・ブロック状態・スロット・\n        // エンティティも766の変換が要る。Join Game／Spawn Position／\n        // Keep Alive／Ping／Respawn 以外は未対応のため落とす\n        // （766のプレイ状態の変換は別担当の対応表待ち）。\n        if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {',
     '        // 766／767／768／769／770／771／772／773向け：チャット・表示系は763が JSON 文字列・766が NBT のため、\n        // JSON のまま送ると誤読される。チャンク・ブロック状態・スロット・\n        // エンティティも766の変換が要る。Join Game／Spawn Position／\n        // Keep Alive／Ping／Respawn 以外は未対応のため落とす\n        // （766のプレイ状態の変換は別担当の対応表待ち）。\n        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773()) {'),
    ('handleServerPlayFrom770 773 branch',
     '        // 772クライアント：771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay771To772(packet);\n        }\n        // 771クライアント：770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay770To771(packet);\n        }\n        // 769クライアント：770→769へ変換する。\n        if (is769()) {\n            return handleServerPlay770To769(packet);\n        }',
     '        // 773クライアント：772→773へ変換する。\n        if (is773()) {\n            return handleServerPlay772To773(packet);\n        }\n        // 772クライアント：771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay771To772(packet);\n        }\n        // 771クライアント：770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay770To771(packet);\n        }\n        // 769クライアント：770→769へ変換する。\n        if (is769()) {\n            return handleServerPlay770To769(packet);\n        }'),
    ('handleServerPlayFrom770 Respawn 773',
     '        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is768() || is769()) {',
     '        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is773()) {\n                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is768() || is769()) {'),
    ('handleServerPlayFrom770 Disconnect 773',
     '            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。',
     '            if (is773()) {\n                // 773クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。'),
    ('handleServerJoinGame770 773 branch',
     '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n',
     '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n'),
    ('handleServerConfiguration 770 branch isServer773',
     '            if (isServer772()) {\n                return handleServerConfiguration771From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration770From771(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration770From769(packet);\n            }',
     '            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration771From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration770From771(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration770From769(packet);\n            }'),
    ('handleServerConfiguration 769 branch isServer773',
     '            if (isServer772()) {\n                return handleServerConfiguration769From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration769From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration769From770(packet);\n            }',
     '            if (isServer773()) {\n                return handleServerConfiguration769From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration769From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration769From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration769From770(packet);\n            }'),
    ('handleServerConfiguration 768 branch isServer773',
     '            if (isServer772()) {\n                return handleServerConfiguration768From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration768From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration768From770(packet);\n            }',
     '            if (isServer773()) {\n                return handleServerConfiguration768From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration768From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration768From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration768From770(packet);\n            }'),
    ('handleClientConfiguration 773-server guard',
     '        if (synthesizingConfig) {\n            return handleSynthesizedClientConfig(packet);\n        }\n        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771() && !is772()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 772サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。772も同じ）。772クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer772() && !is772() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n',
     '        if (synthesizingConfig) {\n            return handleSynthesizedClientConfig(packet);\n        }\n        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771() && !is772() && !is773()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 772サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。772も同じ）。772クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer772() && !is772() && !is771() && !is773()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 773サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。773も同じ）。773クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer773() && !is773() && !is772() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n'),
    ('handleServerPlayFrom771 773 branch',
     '        // 772クライアント：771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay771To772(packet);\n        }\n        // 770クライアント：771→770へ変換する。\n        if (is770()) {\n            return handleServerPlay771To770(packet);\n        }',
     '        // 773クライアント：772→773へ変換する。\n        if (is773()) {\n            return handleServerPlay772To773(packet);\n        }\n        // 772クライアント：771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay771To772(packet);\n        }\n        // 770クライアント：771→770へ変換する。\n        if (is770()) {\n            return handleServerPlay771To770(packet);\n        }'),
    ('handleServerPlayFrom771 Respawn 773',
     '// Respawn（771：0x4B。770（0x4C）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {',
     '// Respawn（771：0x4B。770（0x4C）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is773()) {\n                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {'),
    ('handleServerPlayFrom771 Disconnect 773',
     '            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769() || is770()) {\n                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。',
     '            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is773()) {\n                // 773クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769() || is770()) {\n                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。'),
    ('handleServerJoinGame771 773 branch',
     '    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {',
     '    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {'),
    ('handleServerConfiguration 771 branch isServer773',
     '            if (isServer772()) {\n                return handleServerConfiguration771From772(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration771From769(packet);\n            }',
     '            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration771From772(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration771From769(packet);\n            }'),
    ('handleServerPlayFrom772 773 branch',
     '        // 770クライアント：772→771へ変換してから771→770へ。\n        if (is770()) {\n            MinecraftPacket as771 = to771ServerPacketFrom772(packet);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            return handleServerPlay771To770(as771);\n        }',
     '        // 773クライアント：772→773へ変換する。\n        if (is773()) {\n            return handleServerPlay772To773(packet);\n        }\n        // 770クライアント：772→771へ変換してから771→770へ。\n        if (is770()) {\n            MinecraftPacket as771 = to771ServerPacketFrom772(packet);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            return handleServerPlay771To770(as771);\n        }'),
    ('handleServerPlayFrom772 Respawn 773',
     '        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x4B) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {',
     '        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x4B) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {'),
    ('handleServerPlayFrom772 Disconnect 773',
     '            if (is771() || is772()) {\n                // 771／772クライアント：Disconnect は 0x1C（NBT）のまま。\n',
     '            if (is771() || is772() || is773()) {\n                // 771／772／773クライアント：Disconnect は 0x1C（NBT）のまま。\n'),
    ('handleServerJoinGame772 773 branch',
     '    private Outcome handleServerJoinGame772(MinecraftPacket packet) {\n        if (is771()) {',
     '    private Outcome handleServerJoinGame772(MinecraftPacket packet) {\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {'),
    ('handleServerConfiguration 772 branch isServer773',
     '            if (isServer769()) {\n                return handleServerConfiguration772From769(packet);\n            }',
     '            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration772From769(packet);\n            }'),
]

CLIENT_PLAY_773 = r'''
    /**
     * 773クライアント→内部772以下のプレイ状態（クライアント→サーバー）。
     * 773→772へ変換してから {@link #handleClientPlay772} に通す。
     * 内部772の場合は変換した772パケットをそのまま送る（772の処理は
     * 内部772への送出を想定していないため、委譲ではなく直接送る）。
     * Pick Item From Block／Entity、Player Loaded は772にもあり同形式のため
     * そのまま通す。Vehicle Move は770・771・772・773とも末尾に onGround があるため
     * そのまま通す。Window Click は771・772・773とも HashedSlot のため落とす。
     * Set Creative Slot は UntrustedSlot のままアイテム／成分を付け替える。
     * Change Gamemode／Custom Click Action は772に無いため落とす。
     */
    private Outcome handleClientPlay773(MinecraftPacket packet) {
        MinecraftPacket as772 = to772ClientPacketFrom773(packet);
        if (as772 == null) {
            return Outcome.drop();
        }
        if (isServer772()) {
            return Outcome.toServer(as772);
        }
        return handleClientPlay772(as772);
    }

    /**
     * 773クライアントのパケットを772形式にする。772に相当が無いものは
     * null を返し、呼び出し元が落とす（ログ済み）。
     */
    private MinecraftPacket to772ClientPacketFrom773(MinecraftPacket packet) {

        // Window Click（773：0x11）：771・772・773とも HashedSlot のため
        // 落とす（ハッシュの計算方法は未確定）。
        if (packet.id() == 0x11) {
            logUntranslatedOnce("PLAY_WINDOW_CLICK");
            return null;
        }
        // Set Creative Slot（773：0x37→772：0x37）：
        // UntrustedSlot のままアイテム／成分を付け替える。
        if (packet.id() == 0x37) {
            return new MinecraftPacket(0x37,
                    ChunkTranslator.translateSetCreativeSlot773To772(packet.payload()));
        }
        // Chat Message／Chat Command Signed：末尾の checksum（u8）は
        // 770・771・772・773ともあるため剥がさない。番号だけ付け替える（下の generic 経路）。
        // それ以外：番号だけ772向けに付け替える（ペイロードは同形式）。
        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, ProtocolVersion.V1_21_9);
        if (name.isEmpty()) {
            LOG.warning("773向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return null;
        }
        OptionalInt id772 = PacketTranslator.translateId(name.get(),
                ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_7);
        if (id772.isEmpty()) {
            LOG.warning("772に無いプレイ状態のパケット " + name.get() + " を落とします。");
            return null;
        }
        return new MinecraftPacket(id772.getAsInt(), packet.payload());
    }

    /**
     * 763／764／765／766／767／768／769／770／771／772クライアント→内部773のプレイ状態（クライアント→サーバー）。
     * 名前で分ける。番号だけの付け替えは {@link PacketTranslator#translateId} に任せ、
     * ペイロード変換が要るものだけ明示する。772クライアントは772形式のまま
     * {@link #handleClientPlay772To773} で773へ直す。767クライアントの
     * Set Creative Slot は767→768→769→770→771→772→773へ六段で変換する
     * （Window Click は HashedSlot のため落とす。766以下はデータコンポーネント形式のため落とす）。
     * Craft Recipe Request／Displayed Recipe はレシピIDが付け替えられないため落とす。
     * 旧形式の Pick Item は773に相当が無いため落とす（773は位置・対象を指定するため
     * スロット番号からは変換できない）。
     * Vehicle Move は773の末尾に onGround が付くため、旧クライアント分は
     * onGround=false を付けて送る（旧クライアントは送ってこないための近似）。
     */
    private Outcome handleClientPlayTo773(MinecraftPacket packet) {
        if (is772()) {
            return handleClientPlay772To773(packet);
        }
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, clientProtocol).orElse(null);
        if (name == null) {
            LOG.warning("773向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return Outcome.drop();
        }
        switch (name) {
            case PLAY_WINDOW_CLICK -> {
                // HashedSlot のハッシュ計算方法が未確定のため、773向けは落とす。
                logUntranslatedOnce("PLAY_WINDOW_CLICK");
                return Outcome.drop();
            }
            case PLAY_SET_CREATIVE_SLOT -> {
                if (is767()) {
                    byte[] payload = ChunkTranslator.translateSetCreativeSlot772To773(
                            ChunkTranslator.translateSetCreativeSlot771To772(
                                    ChunkTranslator.translateSetCreativeSlot770To771(
                                            ChunkTranslator.translateSetCreativeSlot769To770(
                                                    ChunkTranslator.translateSetCreativeSlot768To769(
                                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload()))))));
                    return Outcome.toServer(new MinecraftPacket(0x37, payload));
                }
                logUntranslatedOnce("PLAY_SET_CREATIVE_SLOT");
                return Outcome.drop();
            }
            case PLAY_SET_PLAYER_POSITION, PLAY_SET_PLAYER_POSITION_AND_ROTATION,
                    PLAY_SET_PLAYER_ROTATION, PLAY_FLYING -> {
                // 772→773の移動パケットは同形式のため、768→769の変換だけでよい（768／769／770／771／772／773は同形式）。
                byte[] payload = switch (name) {
                    case PLAY_SET_PLAYER_POSITION ->
                            PlayTranslator.translatePlayerPosition767To768(packet.payload());
                    case PLAY_SET_PLAYER_POSITION_AND_ROTATION ->
                            PlayTranslator.translatePlayerPositionLook767To768(packet.payload());
                    case PLAY_SET_PLAYER_ROTATION ->
                            PlayTranslator.translatePlayerLook767To768(packet.payload());
                    default -> PlayTranslator.translatePlayerFlying767To768(packet.payload());
                };
                OptionalInt serverId = PacketTranslator.translateId(
                        name, clientProtocol, ProtocolVersion.V1_21_9);
                if (serverId.isEmpty()) {
                    LOG.warning("773向けに変換できないプレイ状態のパケット（id=0x"
                            + Integer.toHexString(packet.id()) + "）を落とします。");
                    return Outcome.drop();
                }
                return Outcome.toServer(new MinecraftPacket(serverId.getAsInt(), payload));
            }
            case PLAY_STEER_VEHICLE -> {
                byte[] payload = PlayTranslator.translateSteerVehicleToPlayerInput768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x2a, payload));
            }
            case PLAY_VEHICLE_MOVE_TO_SERVER -> {
                // 769は onGround 付き（770・771・772・773と同形式）。768以下は onGround が
                // 無いため onGround=false を補う。
                byte[] payload = is769()
                        ? packet.payload()
                        : PlayTranslator.translateVehicleMove768To769(packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x21, payload));
            }
            case PLAY_CRAFT_RECIPE_REQUEST, PLAY_DISPLAYED_RECIPE -> {
                logUntranslatedOnce(name);
                return Outcome.drop();
            }
            case PLAY_PICK_ITEM -> {
                // 旧形式の Pick Item（スロット番号）は773に相当が無い。
                logUntranslatedOnce(name);
                return Outcome.drop();
            }
            case PLAY_CHAT_COMMAND -> {
                if (is763() || is764() || is765()) {
                    // 763／764／765の Chat Command は完全な形式（署名付き）のため、
                    // 773の Chat Command Signed（0x07）に送る。
                    return Outcome.toServer(new MinecraftPacket(0x07, packet.payload()));
                }
                // 766／767の符号なし Chat Command は773の 0x06（同形式）。
                return Outcome.toServer(new MinecraftPacket(0x06, packet.payload()));
            }
            case PLAY_RESOURCE_PACK_RESPONSE -> {
                if (lastSentPack == null) {
                    LOG.warning("送っていないリソースパックへの応答（id=0x"
                            + Integer.toHexString(packet.id()) + "）を落とします。");
                    return Outcome.drop();
                }
                if (is763()) {
                    byte[] payload = PlayTranslator.translateResourcePackResponse763To765(
                            packet.payload(), lastSentPack);
                    return Outcome.toServer(new MinecraftPacket(0x30, payload));
                }
                if (is764()) {
                    byte[] payload = PlayTranslator.translateResourcePackResponse764To765(
                            packet.payload(), lastSentPack);
                    return Outcome.toServer(new MinecraftPacket(0x30, payload));
                }
                return Outcome.toServer(new MinecraftPacket(0x30, packet.payload()));
            }
            case PLAY_CONFIGURATION_ACKNOWLEDGED -> {
                clientState = ClientState.CONFIGURATION;
                serverState = ServerState.CONFIGURATION;
                return Outcome.toServer(new MinecraftPacket(0x0f, packet.payload()));
            }
            case PLAY_CHUNK_BATCH_RECEIVED -> {
                if (is763()) {
                    return Outcome.drop();
                }
                return Outcome.toServer(new MinecraftPacket(0x0a, packet.payload()));
            }
            case PLAY_COOKIE_RESPONSE -> {
                return Outcome.drop();
            }
            case PLAY_ENCHANT_ITEM -> {
                byte[] payload = PlayTranslator.translateEnchantItem767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x10, payload));
            }
            case PLAY_CLOSE_WINDOW_TO_SERVER -> {
                byte[] payload = PlayTranslator.translateCloseWindow767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x12, payload));
            }
            case PLAY_CLIENT_INFORMATION -> {
                // Settings：768以下の 8 フィールド形式に particleStatus=0 を付けて
                // 773 形式にする。付けないと 773 サーバーが読み取り途中で切断する。
                // 769は 9 フィールド形式（particleStatus 付き）のためそのまま送る。
                byte[] payload = is769() ? packet.payload()
                        : PlayTranslator.translateClientInformation767To768(
                                packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x0d, payload));
            }
            default -> {
                OptionalInt serverId = PacketTranslator.translateId(
                        name, clientProtocol, ProtocolVersion.V1_21_9);
                if (serverId.isPresent()) {
                    return Outcome.toServer(new MinecraftPacket(serverId.getAsInt(),
                            packet.payload()));
                }
                LOG.warning("773向けに変換できないプレイ状態のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                return Outcome.drop();
            }
        }
    }

    /**
     * 772クライアント→内部773のプレイ状態（クライアント→サーバー）。
     * 772→773へ変換してそのまま送る。772は既にデータコンポーネント形式の
     * ため、767向けの変換（particleStatus 付け足し等）は要らない。
     */
    private Outcome handleClientPlay772To773(MinecraftPacket packet) {
        MinecraftPacket as773 = to773ClientPacket(packet);
        if (as773 == null) {
            return Outcome.drop();
        }
        return Outcome.toServer(as773);
    }

    /**
     * 772クライアントのパケットを773形式にする。773に相当が無いものは
     * null を返し、呼び出し元が落とす（ログ済み）。
     */
    private MinecraftPacket to773ClientPacket(MinecraftPacket packet) {

        // Window Click（772：0x11）：771・772・773とも HashedSlot で、コンポーネントの
        // ハッシュ計算方法が未確定のため落とす。
        if (packet.id() == 0x11) {
            logUntranslatedOnce("PLAY_WINDOW_CLICK");
            return null;
        }
        // Set Creative Slot（772：0x37→773：0x37）：
        // UntrustedSlot のままアイテム／成分を付け替える。
        if (packet.id() == 0x37) {
            return new MinecraftPacket(0x37,
                    ChunkTranslator.translateSetCreativeSlot772To773(packet.payload()));
        }
        // Chat Message／Chat Command Signed：末尾の checksum（u8）は
        // 770・771・772・773ともあるため付け足しは要らない。番号だけ付け替える（下の generic 経路）。
        // それ以外：番号だけ773向けに付け替える（ペイロードは同形式）。
        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, ProtocolVersion.V1_21_7);
        if (name.isEmpty()) {
            LOG.warning("773向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return null;
        }
        OptionalInt id773 = PacketTranslator.translateId(name.get(),
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9);
        if (id773.isEmpty()) {
            LOG.warning("773に無いプレイ状態のパケット " + name.get() + " を落とします。");
            return null;
        }
        return new MinecraftPacket(id773.getAsInt(), packet.payload());
    }
'''

SERVER_PLAY_773 = r'''
    /**
     * 内部773→クライアントのプレイ状態（サーバー→クライアント）。
     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／
     * Start Configuration は772と同形式（ID も同じ）のため、772の処理を使い回す。
     * ただし772クライアントへの送出だけは773→772へ直すため、
     * Join Game／Respawn／Disconnect には772向けの分岐がある。
     * それ以外は773→772へ変換してから、772クライアントにはそのまま送り、
     * 767／766クライアントには既存の772→771／766の処理に通す。
     * 763／764／765クライアントは join レベルのみ（772と同じ）。
     */
    private Outcome handleServerPlayFrom773(MinecraftPacket packet) {
        // Start Configuration（773：0x6F）。763には設定フェーズもこのパケットも無い。
        if (packet.id() == 0x6F) {
            if (is763()) {
                serverState = ServerState.CONFIGURATION;
                reconfiguring = true;
                LOG.info(() -> "プレイ中の Start Configuration を受けました（" + playerName
                        + "）。サーバー側の設定フェーズを肩代わりします。");
                return Outcome.drop();
            }
            int outId = PacketTranslator.translateId(
                    PacketName.PLAY_START_CONFIGURATION, 773, clientProtocol)
                    .orElse(is764() ? 0x65 : 0x67);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Join Game（773：0x2B）。
        if (packet.id() == 0x2B) {
            return handleServerJoinGame773(packet);
        }
        // Spawn Position（773：0x5A）。
        if (packet.id() == 0x5A) {
            advanceFlowSafely(() -> joinFlow.spawned());
            int outId = PacketTranslator.translateId(
                    PacketName.PLAY_SPAWN_POSITION, 773, clientProtocol)
                    .orElse(is764() ? 0x52 : 0x54);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Keep Alive（773：0x26）／Ping（773：0x36）は接続維持のため中継する。
        if (packet.id() == 0x26) {
            int outId = PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,
                    serverProtocol, clientProtocol)
                    .orElse(is763() ? 0x23 : 0x24);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        if (packet.id() == 0x36) {
            int outId = PacketTranslator.toClientId(PacketName.PLAY_PING,
                    serverProtocol, clientProtocol)
                    .orElse(is763() ? 0x32 : 0x33);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Disconnect（773：0x1C、NBT）。
        if (packet.id() == 0x1C) {
            closed = true;
            if (is763() || is764()) {
                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());
                int outId = is763() ? 0x1A : 0x1B;
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(outId, payload)), false, true);
            }
            if (is771() || is772() || is773()) {
                // 771／772／773クライアント：Disconnect は 0x1C（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);
            }
            if (is766() || is767() || is768() || is769() || is770()) {
                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }
            return new Outcome(List.of(),
                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);
        }
        // Respawn（773：0x4B。772（0x4B）と同形式のためそのまま送る）。
        if (packet.id() == 0x4B) {
            if (is772()) {
                // 772クライアント：Respawn は 0x4B（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));
            }
            byte[] payload767 = PlayTranslator.translateRespawn768To767(
                    packet.payload(), registries);
            if (is766() || is767()) {
                return Outcome.toClient(new MinecraftPacket(0x47, payload767));
            }
            byte[] payload;
            int outId;
            if (is763()) {
                payload = PlayTranslator.translateRespawn766To763(payload767, registries);
                outId = 0x41;
            } else if (is764()) {
                payload = PlayTranslator.translateRespawn766To764(payload767, registries);
                outId = 0x43;
            } else {
                payload = PlayTranslator.translateRespawn766To765(payload767, registries);
                outId = 0x45;
            }
            return Outcome.toClient(new MinecraftPacket(outId, payload));
        }
        // 772クライアント：773→772へ変換する。
        if (is772()) {
            return handleServerPlay773To772(packet);
        }
        // 767クライアント：773→772→771→770→769→768へ変換してから769→768へ。
        if (is767()) {
            MinecraftPacket as772 = to772ServerPacketFrom773(packet);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as770 = to770ServerPacketFrom771(as771);
            if (as770 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as769 = to769ServerPacketFrom770(as770);
            if (as769 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as768 = to768ServerPacket(as769);
            if (as768 == null) {
                return Outcome.drop();
            }
            return handleServerPlay768To767(as768);
        }
        // 766クライアント：773→772→771→770→769→768へ変換してから769→767へ。
        if (is766()) {
            MinecraftPacket as772 = to772ServerPacketFrom773(packet);
            if (as772 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as771 = to771ServerPacketFrom772(as772);
            if (as771 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as770 = to770ServerPacketFrom771(as771);
            if (as770 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as769 = to769ServerPacketFrom770(as770);
            if (as769 == null) {
                return Outcome.drop();
            }
            MinecraftPacket as768 = to768ServerPacket(as769);
            if (as768 == null) {
                return Outcome.drop();
            }
            return handleServerPlay768To766(as768);
        }
        // それ以外は落とす（763／764／765クライアントは join レベルのみ）。
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, 773).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
        } else {
            logUntranslatedOnce(name);
        }
        return Outcome.drop();
    }

    /**
     * Join Game（内部773：0x2B）のクライアント向け変換。
     * 772と同形式のため、772クライアントにはそのまま送る。
     * それ以外は {@link #handleServerJoinGame772} と同じ方針で各クライアントへ送る。
     */
    private Outcome handleServerJoinGame773(MinecraftPacket packet) {
        if (is772()) {
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x2B, packet.payload()));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4A,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }
        return handleServerJoinGame772(packet);
    }

    /**
     * 773サーバーのプレイ状態パケットを772形式にする。772に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 772クライアントへの送出と、767／766クライアントへの二段変換の
     * 第一段で共用する。
     */
    private MinecraftPacket to772ServerPacketFrom773(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_9).orElse(null);
        // experience_orb：771・772・773とも通常の Spawn Entity
        // （type experience_orb）のため、専用の分岐は要らない
        // （下の PLAY_SPAWN_ENTITY 経路で通す）。
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata773To772(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot773To772(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem773To772(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory773To772(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems773To772(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown773To772(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment773To772(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList773To772(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow773To772(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction773To772(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity773To772(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect773To772(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect773To772(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound773To772(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles773To772(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect773To772(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect773To772(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent773To772(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes773To772(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData773To772(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange773To772(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange773To772(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData773To772(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo773To772(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat773To772(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem773To772(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion773To772(packet.payload());
            // ロケーターバー／ダイアログは772にもあるためそのまま通す（同形式）。
            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG ->
                    packet.payload();
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id772 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_7);
        if (id772.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id772.getAsInt(), translated);
    }

    /**
     * 内部773→772クライアントのプレイ状態（サーバー→クライアント）。
     * 773→772へ変換してそのまま送る。
     */
    private Outcome handleServerPlay773To772(MinecraftPacket packet) {
        MinecraftPacket as772 = to772ServerPacketFrom773(packet);
        if (as772 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as772);
    }
'''

CONFIG_773 = r'''
    /**
     * 773クライアント＋内部772：設定フェーズを中継する。
     * 772と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ772→773へ並べ替え、
     * Tags はレジストリ番号がずれるため落とす。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration773From772(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（772は NBT。773も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：772のエントリ配列を773の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData772To773(packet.payload())));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（772：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 773クライアント＋内部768：設定フェーズを中継する。
     * 768と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ768→769→770→771→772→773へ四段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration773From768(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（768は NBT。773も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：768のエントリ配列を773の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData772To773(
                                PlayTranslator.translateRegistryData771To772(
                                        PlayTranslator.translateRegistryData770To771(
                                                PlayTranslator.translateRegistryData769To770(
                                                        PlayTranslator.translateRegistryData768To769(packet.payload())))))));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（768：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("768の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 773クライアント＋内部767：設定フェーズを中継する。
     * 767と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ767→768→769→770→771→772→773へ五段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration773From767(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（767は NBT。773も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：767のエントリ配列を773の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload767to768 =
                        PlayTranslator.translateRegistryData767To768(packet.payload());
                byte[] payload768to769 =
                        PlayTranslator.translateRegistryData768To769(payload767to768);
                byte[] payload769to770 =
                        PlayTranslator.translateRegistryData769To770(payload768to769);
                byte[] payload770to771 =
                        PlayTranslator.translateRegistryData770To771(payload769to770);
                byte[] payload771to772 =
                        PlayTranslator.translateRegistryData771To772(payload770to771);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData772To773(payload771to772)));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（767：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("767の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 773クライアント＋内部766：設定フェーズを中継する。
     * 766と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ766→767→768→769→770→771→772→773へ六段で並べ替え（jukebox_song は
     * 766に無いため、766側の変換で落とされることは無い）、Tags はレジストリ番号が
     * ずれるため落とし、Custom Report Details（0x0F）／Server Links（0x10）は
     * 766に無いため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration773From766(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(packet); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（766は NBT。773も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：766のエントリ配列を773の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload766to767 =
                        PlayTranslator.translateRegistryData766To767(packet.payload());
                byte[] payload767to768 =
                        PlayTranslator.translateRegistryData767To768(payload766to767);
                byte[] payload768to769 =
                        PlayTranslator.translateRegistryData768To769(payload767to768);
                byte[] payload769to770 =
                        PlayTranslator.translateRegistryData769To770(payload768to769);
                byte[] payload770to771 =
                        PlayTranslator.translateRegistryData770To771(payload769to770);
                byte[] payload771to772 =
                        PlayTranslator.translateRegistryData771To772(payload770to771);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData772To773(payload771to772)));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（766：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F, 0x10 -> // Custom Report Details／Server Links（766に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("766の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 773クライアント＋内部764／765：設定フェーズを中継する。
     * {@link #handleServerConfiguration772} と同じ番号の付け替えで、
     * Registry Data だけ764／765の codec を773のエントリ配列に変換する
     * （765→767→768→769→770→771→772→773へ六段）。
     *
     * @param from764 サーバーが764の場合 true。false の場合は765
     */
    private Outcome handleServerConfiguration773(MinecraftPacket packet, boolean from764) {
        return switch (packet.id()) {
            case 0x00 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Plugin Message → Custom Payload
            case 0x01 -> { // Disconnect（764は JSON 文字列、765／766は NBT）
                byte[] payload = from764
                        ? PlayTranslator.translateDisconnect764To765(packet.payload())
                        : packet.payload();
                yield closeWithPacket(new MinecraftPacket(0x02, payload));
            }
            case 0x02 -> // Finish Configuration
                    Outcome.toClient(new MinecraftPacket(0x03, new byte[0]));
            case 0x03 -> Outcome.toClient(new MinecraftPacket(0x04, packet.payload())); // Keep Alive
            case 0x04 -> Outcome.toClient(new MinecraftPacket(0x05, packet.payload())); // Ping
            case 0x05 -> { // Registry Data：764／765の codec を773のエントリ配列に変換する
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                List<MinecraftPacket> toClient = new ArrayList<>();
                for (byte[] registryPayload
                        : PlayTranslator.translateRegistryData765To767(packet.payload())) {
                    byte[] payload767to768 =
                            PlayTranslator.translateRegistryData767To768(registryPayload);
                    byte[] payload768to769 =
                            PlayTranslator.translateRegistryData768To769(payload767to768);
                    byte[] payload769to770 =
                            PlayTranslator.translateRegistryData769To770(payload768to769);
                    byte[] payload770to771 =
                            PlayTranslator.translateRegistryData770To771(payload769to770);
                    byte[] payload771to772 =
                            PlayTranslator.translateRegistryData771To772(payload770to771);
                    toClient.add(new MinecraftPacket(0x07,
                            PlayTranslator.translateRegistryData772To773(payload771to772)));
                }
                yield new Outcome(List.of(), toClient, false, false);
            }
            case 0x06 -> { // 764の Add Resource Pack（UUIDなし）／765の Remove Resource Pack
                if (from764) {
                    PendingPack pack = parseResourcePack764(packet.payload());
                    lastSentPack = pack;
                    yield Outcome.toClient(new MinecraftPacket(0x09,
                            PlayTranslator.translateConfigAddResourcePack764To765(
                                    pack, packOverrideForClient())));
                }
                yield Outcome.toClient(new MinecraftPacket(0x08, packet.payload()));
            }
            case 0x07 -> { // 764の Feature Flags／765の Add Resource Pack（UUID付き）
                if (from764) {
                    yield Outcome.toClient(new MinecraftPacket(0x0C, packet.payload()));
                }
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x08 -> { // 764の Tags／765の Feature Flags
                if (from764) {
                    LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x0C, packet.payload()));
            }
            case 0x09 -> { // 765の Tags：レジストリ番号がずれるため落とす（764には無い番号）
                LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            default -> {
                LOG.warning((from764 ? "764" : "765") + "の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 763クライアント＋内部773：設定フェーズをプロキシが773クライアント役
     * として消費する。773の設定フェーズ：0x00 Cookie Request、
     * 0x01 Custom Payload、0x02 Disconnect（NBT）、0x03 Finish Configuration、
     * 0x04 Keep Alive、0x05 Ping、0x06 Reset Chat、0x07 Registry Data
     * （エントリ配列）、0x08 Remove Resource Pack、0x09 Add Resource Pack
     * （UUID付き）、0x0A Store Cookie、0x0B Transfer、0x0C Feature Flags、
     * 0x0D Tags、0x0E Select Known Packs、0x0F Custom Report Details、
     * 0x10 Server Links。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す（バニラが待ち受けない
     * ようにする）。Transfer は763クライアントに送る先が無いため落とす。
     * Join Game は773形式で来るので、763向けには
     * {@link #handleServerJoinGame773} で変換する。
     * Registry Data は763クライアント向けの Join Game codec を作るために
     * 768形式に直してから蓄える（773のままではバイオーム pale_garden／
     * plains の番号がずれるため）。
     */
    private Outcome handleServerConfiguration763From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.drop(); // Custom Payload（brand 等）
            case 0x02 -> { // Disconnect（NBT）
                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());
                yield closeWithPacket(new MinecraftPacket(0x00, payload));
            }
            case 0x03 -> { // Finish Configuration
                serverState = ServerState.PLAY;
                if (!reconfiguring) {
                    // 参加時の設定フェーズ：そのまま終わる。
                    yield Outcome.toServer(new MinecraftPacket(0x03, new byte[0]));
                }
                // プレイ中の再設定が終わった。溜めたクライアントパケットを送り、
                // 再設定中に来たリソースパックがあれば763向けに送る。
                reconfiguring = false;
                List<MinecraftPacket> toServer = new ArrayList<>();
                toServer.add(new MinecraftPacket(0x03, new byte[0]));
                toServer.addAll(queuedClientPackets);
                queuedClientPackets.clear();
                List<MinecraftPacket> toClient = new ArrayList<>();
                while (!pendingPacks.isEmpty()) {
                    PendingPack pack = pendingPacks.removeFirst();
                    lastSentPack = pack;
                    toClient.add(new MinecraftPacket(0x40,
                            PlayTranslator.buildPlayResourcePack763(pack, pack763)));
                }
                yield new Outcome(toServer, toClient, false, false);
            }
            case 0x04 -> Outcome.toServer(packet); // Keep Alive（応答は同ID）
            case 0x05 -> Outcome.toServer(packet); // Ping→Pong は同ID・同形式
            case 0x06 -> Outcome.drop(); // Reset Chat（763に相当無し）
            case 0x07 -> { // Registry Data（773はエントリ配列。parseRegistryData が読む）
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 763向けの辞書は768形式で蓄える（773のままでは
                // バイオーム pale_garden／plains の番号がずれる）。
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_6)));
                yield Outcome.drop();
            }
            case 0x08 -> Outcome.drop(); // Remove Resource Pack（763に相当無し）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）：保持して Join Game の後に送る
                pendingPacks.add(PlayTranslator.parseConfigAddResourcePack(packet.payload()));
                yield Outcome.drop();
            }
            case 0x0A -> Outcome.drop(); // Store Cookie（763に相当無し）
            case 0x0B -> { // Transfer：763クライアントに送る先が無いため落とす
                LOG.info(() -> "763クライアントに送れない Transfer を落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0C, 0x0D -> Outcome.drop(); // Feature Flags / Tags
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F, 0x10 -> Outcome.drop(); // Custom Report Details／Server Links（763に相当無し）
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 764／765クライアント＋内部773：設定フェーズを中継する。
     * {@link #handleServerConfigurationFrom772} と同じ番号の付け替えで、
     * Registry Data だけ773→772→771→770→769→768→767→765へ六段で変換する（jukebox_song は落とす）。
     * 763向けの辞書には768形式で蓄える。
     *
     * @param to764 クライアントが764の場合 true。false の場合は765クライアント
     */
    private Outcome handleServerConfigurationFrom773(MinecraftPacket packet, boolean to764) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x00, packet.payload())); // Custom Payload → Plugin Message
            case 0x02 -> { // Disconnect（773は NBT。764は JSON 文字列）
                byte[] payload = to764
                        ? PlayTranslator.translateDisconnect765To763(packet.payload())
                        : packet.payload();
                yield closeWithPacket(new MinecraftPacket(0x01, payload));
            }
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(new MinecraftPacket(0x02, new byte[0]));
            case 0x04 -> Outcome.toClient(new MinecraftPacket(0x03, packet.payload())); // Keep Alive
            case 0x05 -> Outcome.toClient(new MinecraftPacket(0x04, packet.payload())); // Ping
            case 0x06 -> Outcome.drop(); // Reset Chat（764／765に相当無し）
            case 0x07 -> { // Registry Data：773のエントリ配列を764／765の codec に変換する
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_6)));
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(payload773to772);
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(payload772to771);
                byte[] payload770to769 =
                        PlayTranslator.translateRegistryData770To769(payload771to770);
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(payload770to769);
                byte[] payload768to767 =
                        PlayTranslator.translateRegistryData768To767(payload769to768);
                byte[] codec = payload768to767 == null ? null
                        : PlayTranslator.translateRegistryData767To765(payload768to767);
                if (codec == null) {
                    // jukebox_song：764／765に存在しないため落とす。
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x05, codec));
            }
            case 0x08 -> { // Remove Resource Pack：765だけに送る（764に相当無し）
                if (to764) {
                    LOG.info(() -> "764に無い Remove Resource Pack を落とします（" + playerName + "）。");
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x06, packet.payload()));
            }
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                if (to764) {
                    yield Outcome.toClient(new MinecraftPacket(0x06,
                            PlayTranslator.buildConfigResourcePack764(pack, pack764)));
                }
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, pack765)));
            }
            case 0x0A -> Outcome.drop(); // Store Cookie（764／765に相当無し）
            case 0x0B -> { // Transfer：764／765クライアントに送る先が無いため落とす
                LOG.info(() -> (to764 ? "764" : "765")
                        + "クライアントに送れない Transfer を落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0C -> // Feature Flags
                    Outcome.toClient(new MinecraftPacket(to764 ? 0x07 : 0x08, packet.payload()));
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を"
                        + (to764 ? "764" : "765") + "向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F, 0x10 -> // Custom Report Details／Server Links（764／765に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 767クライアント＋内部773：設定フェーズを中継する。
     * 767と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772→771→770→769→768→767へ五段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration767From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。767も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を767の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(payload773to772);
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(payload772to771);
                if (payload771to770 == null) {
                    yield Outcome.drop();
                }
                byte[] payload770to769 =
                        PlayTranslator.translateRegistryData770To769(payload771to770);
                if (payload770to769 == null) {
                    yield Outcome.drop();
                }
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(payload770to769);
                if (payload769to768 == null) {
                    yield Outcome.drop();
                }
                byte[] payload768to767 =
                        PlayTranslator.translateRegistryData768To767(payload769to768);
                if (payload768to767 == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload768to767));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を767向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 766クライアント＋内部773：設定フェーズを中継する。
     * 766と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772→771→770→769→768→767→766へ六段で並べ替え（jukebox_song は落とす）、
     * Tags はレジストリ番号がずれるため落とし、Custom Report Details
     * （0x0F）／Server Links（0x10）は766に無いため落とす。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration766From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(packet); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。766も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を766の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(payload773to772);
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(payload772to771);
                if (payload771to770 == null) {
                    yield Outcome.drop();
                }
                byte[] payload770to769 =
                        PlayTranslator.translateRegistryData770To769(payload771to770);
                if (payload770to769 == null) {
                    yield Outcome.drop();
                }
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(payload770to769);
                if (payload769to768 == null) {
                    yield Outcome.drop();
                }
                byte[] payload768to767 =
                        PlayTranslator.translateRegistryData768To767(payload769to768);
                if (payload768to767 == null) {
                    yield Outcome.drop();
                }
                byte[] payload767to766 =
                        PlayTranslator.translateRegistryData767To766(payload768to767);
                if (payload767to766 == null) {
                    // jukebox_song：766に存在しないため落とす。
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload767to766));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を766向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F, 0x10 -> // Custom Report Details／Server Links（766に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 773クライアント＋内部772以下の設定フェーズ（クライアント→バニラ）。
     * 773の番号は772と同じため、ほとんど {@link #handleClientConfiguration772}
     * と同じ。ただし Custom Click Action（0x08）は771・772・773だけのパケットのため、
     * 771・772・773サーバーへはそのまま送り、旧サーバー向けは落とす。
     */
    private Outcome handleClientConfiguration773(MinecraftPacket packet) {
        if (packet.id() == 0x08) {
            // Custom Click Action（771・772・773のみ。770では Custom Report Details と同ID）。
            // 771・772・773サーバーへはそのまま送り、旧サーバー向けは落とす。
            if (isServer772() || isServer771() || isServer772()) {
                return Outcome.toServer(packet);
            }
            return Outcome.drop();
        }
        return handleClientConfiguration772(packet);
    }

    /**
     * 764／765クライアント＋内部773の設定フェーズ（クライアント→バニラ）。
     * 773の番号は772と同じため {@link #handleClientConfigurationTo772} と同じ。
     */
    private Outcome handleClientConfigurationTo773(MinecraftPacket packet) {
        return handleClientConfigurationTo772(packet);
    }

    /**
     * 773クライアント＋内部769：設定フェーズを中継する。
     * 769と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ769→770→771→772→773へ三段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration773From769(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（769は NBT。773も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：769のエントリ配列を773の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload769to770 =
                        PlayTranslator.translateRegistryData769To770(packet.payload());
                byte[] payload770to771 =
                        PlayTranslator.translateRegistryData770To771(payload769to770);
                byte[] payload771to772 =
                        PlayTranslator.translateRegistryData771To772(payload770to771);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData772To773(payload771to772)));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（769：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("769の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 773クライアント＋内部770：設定フェーズを中継する。
     * 770と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ770→771→772→773へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration773From770(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（770は NBT。773も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：770のエントリ配列を773の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload770to771 =
                        PlayTranslator.translateRegistryData770To771(packet.payload());
                byte[] payload771to772 =
                        PlayTranslator.translateRegistryData771To772(payload770to771);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData772To773(payload771to772)));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（770：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("770の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 772クライアント＋内部773：設定フェーズを中継する。
     * 772と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772へ並べ替え（minecraft:dialog は772にもあるため通し、
     * jukebox_song の lava_chicken は落とす）、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773とも
     * 同ID・同形式のため素通しする。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration772From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。772も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を772の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload = PlayTranslator.translateRegistryData773To772(packet.payload());
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を772向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部773：設定フェーズを中継する。
     * 769と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772→771→770→769へ三段で並べ替え（minecraft:dialog と
     * jukebox_song の lava_chicken は落とす）、Tags はレジストリ番号がずれるため
     * 落とす。Clear Dialog（0x11）／Show Dialog（0x12）は769に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration769From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。769も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を769の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 769向けの辞書は769形式で蓄える（773と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_6));
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(payload773to772);
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(payload772to771);
                if (payload771to770 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData770To769(payload771to770);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を769向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（769に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 768クライアント＋内部773：設定フェーズを中継する。
     * 768と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772→771→770→769→768へ四段で並べ替え（pale_garden を
     * 落として plains を39に戻す）、Tags はレジストリ番号がずれるため
     * 落とす。Clear Dialog（0x11）／Show Dialog（0x12）は768に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration768From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。768も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を768の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 768向けの辞書は768形式で蓄える（773のままでは
                // バイオーム pale_garden／plains の番号がずれる）。
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_5)));
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(payload773to772);
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(payload772to771);
                if (payload771to770 == null) {
                    yield Outcome.drop();
                }
                byte[] payload770to769 =
                        PlayTranslator.translateRegistryData770To769(payload771to770);
                if (payload770to769 == null) {
                    yield Outcome.drop();
                }
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(payload770to769);
                if (payload769to768 == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload769to768));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を768向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（768に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

'''

SERVER_PLAY_773_TO = r'''
    /**
     * 772サーバーのプレイ状態パケットを773形式にする。773に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 773クライアントへの送出と、769／768／767／766クライアントへの
     * 多段変換の最終段で共用する。経験値オーブは770・771・772・773とも通常の
     * Spawn Entity（type experience_orb）のため、専用の分岐は要らない。
     */
    private MinecraftPacket to773ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_7).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata772To773(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot772To773(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem772To773(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory772To773(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems772To773(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown772To773(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment772To773(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList772To773(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow772To773(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction772To773(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity772To773(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect772To773(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect772To773(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound772To773(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles772To773(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect772To773(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect772To773(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent772To773(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes772To773(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData772To773(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange772To773(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange772To773(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData772To773(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo772To773(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat772To773(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem772To773(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion772To773(packet.payload());
            // ロケーターバー／ダイアログは772にもあるためそのまま通す（同形式）。
            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG ->
                    packet.payload();
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id773 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9);
        if (id773.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id773.getAsInt(), translated);
    }

    /**
     * 内部772→773クライアントのプレイ状態（サーバー→クライアント）。
     * 772→773へ変換してそのまま送る。
     */
    private Outcome handleServerPlay772To773(MinecraftPacket packet) {
        MinecraftPacket as773 = to773ServerPacket(packet);
        if (as773 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as773);
    }

    /**
     * 内部770→773クライアントのプレイ状態（サーバー→クライアント）。
     * 769→771→772→773へ二段で変換する。
     */
    private Outcome handleServerPlay769To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay769To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }

    /**
     * 内部769→773クライアントのプレイ状態（サーバー→クライアント）。
     * 768→771→772→773へ二段で変換する。
     */
    private Outcome handleServerPlay768To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay768To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }

    /**
     * 内部768→773クライアントのプレイ状態（サーバー→クライアント）。
     * 767→771→772→773へ二段で変換する。
     */
    private Outcome handleServerPlay767To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay767To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }

    /**
     * 内部767→773クライアントのプレイ状態（サーバー→クライアント）。
     * 766→771→772→773へ二段で変換する。
     */
    private Outcome handleServerPlay766To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay766To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }
'''

INSERTS = [
    # (説明, メソッド定義行, 挿入する本文)
    # 挿入位置はそのメソッドの javadoc の先頭まで遡る（javadoc が
    # 新しいメソッドに付かないようにする）。
    ("client-play 773 methods",
     "    private Outcome handleClientPlay772(MinecraftPacket packet) {",
     CLIENT_PLAY_773),
    ("server-play 773 methods",
     "    private Outcome handleServerPlayFrom772(MinecraftPacket packet) {",
     SERVER_PLAY_773),
    ("server-play 772-to-773 methods",
     "    private Outcome handleServerPlayFrom773(MinecraftPacket packet) {",
     SERVER_PLAY_773_TO),
]









# config 773 methods：handleClientConfigurationTo772 の直後（クラスの末尾）に置く。
# EDITS とは別扱いにする（CONFIG_773 が後で定義されるため）。
CONFIG_ANCHOR = """            default -> {
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }


}"""

CONFIG_REPLACEMENT = """            default -> {
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
""" + CONFIG_773 + "\n}\n"


def apply(src, pairs):
    for name, old, new in pairs:
        n = src.count(old)
        if n != 1:
            print(f"NG: {name}: anchor が {n} 回見つかった（1回のはず）", file=sys.stderr)
            return None
        src = src.replace(old, new, 1)
        print(f"OK: {name}")
    return src


def insert_before_method(src, name, signature, block):
    """メソッド定義行の javadoc 先頭まで遡り、その前に block を挿入する。"""
    lines = src.split("\n")
    idx = [i for i, line in enumerate(lines) if line == signature]
    if len(idx) != 1:
        print(f"NG: {name}: メソッド定義行が {len(idx)} 回見つかった（1回のはず）",
              file=sys.stderr)
        return None
    i = idx[0]
    # javadoc ブロック（    /** ...     */）の先頭まで遡る。
    j = i - 1
    if j >= 0 and lines[j] == "     */":
        j -= 1
        while j >= 0 and lines[j].startswith("     *"):
            j -= 1
        if j >= 0 and lines[j] == "    /**":
            i = j
    block_lines = block.rstrip("\n").split("\n")
    lines[i:i] = block_lines + [""]
    print(f"OK: {name} を挿入")
    return "\n".join(lines)














def main():
    src = PATH.read_text(encoding="utf-8")
    out = apply(src, EDITS)
    if out is None:
        sys.exit(1)
    src = out
    for name, signature, block in INSERTS:
        out = insert_before_method(src, name, signature, block)
        if out is None:
            sys.exit(1)
        src = out
    n = src.count(CONFIG_ANCHOR)
    if n != 1:
        print(f"NG: config 773 methods: anchor が {n} 回見つかった（1回のはず）",
              file=sys.stderr)
        sys.exit(1)
    src = src.replace(CONFIG_ANCHOR, CONFIG_REPLACEMENT, 1)
    print("OK: config 773 methods を挿入")
    PATH.write_text(src, encoding="utf-8")
    print("ProxySession.java に 773 の対応を挿入した")


if __name__ == "__main__":
    main()
