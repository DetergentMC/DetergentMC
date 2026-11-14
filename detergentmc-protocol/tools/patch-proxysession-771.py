#!/usr/bin/env python3
"""ProxySession.java へ Java 1.21.6（プロトコル771）の対応を入れる。

770パッチ（patch-proxysession-770.py）を1段ずらしたもの。生成元は
tools/gen-771-proxysession.py で、EDITS・3ブロック・INSERTS を機械的に
ずらし、771特有の箇所だけ手で直した。

771の要点（770からの差分）:
- クライアント行きのプレイ・設定・ログインのIDは770と変わらない。
  Join Game 0x2B、Spawn Position 0x5A、Chunk Data 0x27、Keep Alive 0x26、
  Ping 0x36、Start Configuration 0x6F、Disconnect 0x1C、Respawn 0x4B、
  Add Resource Pack 0x4A。
- サーバー行きは Change Gamemode 0x04 の挿入で 0x04 以降が+1ずれる。
  Chat Message 0x07→0x08、Chat Command Signed 0x06→0x07、
  Chat Command 0x05→0x06、Window Click 0x10→0x11、Vehicle Move 0x20→0x21、
  Set Creative Slot 0x36→0x37、Keep Alive 応答 0x1A→0x1B、Ping 応答 0x2B→0x2C。
  新規: Change Gamemode 0x04、Custom Click Action 0x41（共に落とす）。
- チャットの checksum（u8）は770・771ともあるため、771⇔770では
  付け足しも剥がしもししない（番号だけ付け替える）。769⇔770の変換器は
  そのまま（769には checksum が無い）。
- Vehicle Move の onGround は769・770・771ともある（768に無い）。
  771⇔770では素通しする。
- Window Click は771・770とも HashedSlot のため落とす。Set Creative Slot は
  771・770とも UntrustedSlot のため、アイテム／成分を付け替えるだけ。
- Player Chat は先頭に globalIndex が770・771ともあるため素通しする。
  769サーバーからの player_chat にはプロキシが 0 から数えて付ける
  （770／771クライアント共用の chatGlobalIndex）。
- 設定フェーズは770と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 770→771 が要る（minecraft:dialog は
  770には無く771にだけある。jukebox_song は tears の挿入で17番以降が+1）。
  Show Dialog（config 0x12）／Clear Dialog（0x11）は旧クライアント向けに
  落とす。Custom Click Action（config 0x08）は771サーバーへ通し、
  旧サーバー向けに落とす。Custom Report Details／Server Links
  （config サーバー行き）は771では削除されたため、771サーバー向けは落とす。
- 経験値オーブは770・771とも通常の Spawn Entity（type experience_orb）。
  770の to769ServerPacketFrom770 の専用分岐は771→770では使わない。
- ハッピーガスト（771: 56）は770ではガスト（55）に代替する。
  （770の invert ではブタ(94)になるが、見た目が近いガストを選ぶ。
  メタデータの個体差はプロキシが種別を追跡しないため残る。）
- ロケーターバー（tracked_waypoint 0x83）／ダイアログ（show_dialog 0x85／
  clear_dialog 0x84）は770に無いため、旧クライアント向けに落とす。
- 内部771サーバー＋771クライアントはネイティブ（素通し）。

使い方: repo ルートで `python3 detergentmc-protocol/tools/patch-proxysession-771.py`
（--check で差分確認）。全アンカーは一意でなければならない。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ProxySession.java"


EDITS = [

    ('constructor javadoc',
     '     * @param serverProtocol 内部バニラのプロトコル（763＝1.20.1、\n     *                       764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、\n     *                       767＝1.21／1.21.1、768＝1.21.2／1.21.3、\n     *                       769＝1.21.4、770＝1.21.5）',
     '     * @param serverProtocol 内部バニラのプロトコル（763＝1.20.1、\n     *                       764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、\n     *                       767＝1.21／1.21.1、768＝1.21.2／1.21.3、\n     *                       769＝1.21.4、770＝1.21.5、771＝1.21.6）'),
    ('constructor validation',
     '                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4) or 770 (1.21.5): "\n',
     '                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5) or 771 (1.21.6): "\n'),
    ('serverProtocol() javadoc',
     '    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5）。 */',
     '    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6）。 */'),
    ('pack771 field',
     '    /**\n     * 768クライアントに送るリソースパック（pack_format 42）。\n     * 内部が768でない場合に使う差し替え用。内部が768の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack768;\n\n    /**\n     * 769クライアントに送るリソースパック（pack_format 46）。\n     * 内部が769でない場合に使う差し替え用。内部が769の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack769;\n\n    /**\n     * 770クライアントに送るリソースパック（pack_format 55）。\n     * 内部が770でない場合に使う差し替え用。内部が770の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack770;',
     '    /**\n     * 768クライアントに送るリソースパック（pack_format 42）。\n     * 内部が768でない場合に使う差し替え用。内部が768の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack768;\n\n    /**\n     * 769クライアントに送るリソースパック（pack_format 46）。\n     * 内部が769でない場合に使う差し替え用。内部が769の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack769;\n\n    /**\n     * 770クライアントに送るリソースパック（pack_format 55）。\n     * 内部が770でない場合に使う差し替え用。内部が770の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack770;\n\n    /**\n     * 771クライアントに送るリソースパック（pack_format 63）。\n     * 内部が771でない場合に使う差し替え用。内部が771の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack771;'),
    ('setPack771/pack771',
     '    /** 768クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack768() {\n        return pack768;\n    }\n\n    /** 769クライアントに送るリソースパックを設定する（内部769でない場合に使う）。 */\n    public void setPack769(ResourcePack pack769) {\n        this.pack769 = pack769;\n    }\n\n    /** 769クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack769() {\n        return pack769;\n    }\n\n    /** 770クライアントに送るリソースパックを設定する（内部770でない場合に使う）。 */\n    public void setPack770(ResourcePack pack770) {\n        this.pack770 = pack770;\n    }\n\n    /** 770クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack770() {\n        return pack770;\n    }',
     '    /** 768クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack768() {\n        return pack768;\n    }\n\n    /** 769クライアントに送るリソースパックを設定する（内部769でない場合に使う）。 */\n    public void setPack769(ResourcePack pack769) {\n        this.pack769 = pack769;\n    }\n\n    /** 769クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack769() {\n        return pack769;\n    }\n\n    /** 770クライアントに送るリソースパックを設定する（内部770でない場合に使う）。 */\n    public void setPack770(ResourcePack pack770) {\n        this.pack770 = pack770;\n    }\n\n    /** 770クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack770() {\n        return pack770;\n    }\n\n    /** 771クライアントに送るリソースパックを設定する（内部771でない場合に使う）。 */\n    public void setPack771(ResourcePack pack771) {\n        this.pack771 = pack771;\n    }\n\n    /** 771クライアントに送るリソースパック（無ければ null）。 */\n    public ResourcePack pack771() {\n        return pack771;\n    }'),
    ('packOverrideForClient javadoc',
     '     * 出し分けの基準はクライアントのプロトコルである：770→pack770\n     * （pack_format 55）、769→pack769\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767',
     '     * 出し分けの基準はクライアントのプロトコルである：771→pack771\n     * （pack_format 63）、770→pack770\n     * （pack_format 55）、769→pack769\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767'),
    ('packOverrideForClient branch',
     '        if (clientProtocol == ProtocolVersion.V1_21_5) {\n            return pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_4) {\n            return pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_2) {\n            return pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }',
     '        if (clientProtocol == ProtocolVersion.V1_21_6) {\n            return pack771 != null ? pack771\n                    : pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_5) {\n            return pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_4) {\n            return pack769 != null ? pack769\n                    : pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }\n        if (clientProtocol == ProtocolVersion.V1_21_2) {\n            return pack768 != null ? pack768\n                    : pack767 != null ? pack767 : pack766;\n        }'),
    ('is771/isServer771',
     '    /** 内部が 1.21.2／1.21.3（768）か。 */\n    private boolean isServer768() {\n        return serverProtocol == ProtocolVersion.V1_21_2;\n    }\n\n    /** クライアントが 1.21.4（769）か。 */\n    private boolean is769() {\n        return clientProtocol == ProtocolVersion.V1_21_4;\n    }\n\n    /** 内部が 1.21.4（769）か。 */\n    private boolean isServer769() {\n        return serverProtocol == ProtocolVersion.V1_21_4;\n    }\n\n    /** クライアントが 1.21.5（770）か。 */\n    private boolean is770() {\n        return clientProtocol == ProtocolVersion.V1_21_5;\n    }\n\n    /** 内部が 1.21.5（770）か。 */\n    private boolean isServer770() {\n        return serverProtocol == ProtocolVersion.V1_21_5;\n    }',
     '    /** 内部が 1.21.2／1.21.3（768）か。 */\n    private boolean isServer768() {\n        return serverProtocol == ProtocolVersion.V1_21_2;\n    }\n\n    /** クライアントが 1.21.4（769）か。 */\n    private boolean is769() {\n        return clientProtocol == ProtocolVersion.V1_21_4;\n    }\n\n    /** 内部が 1.21.4（769）か。 */\n    private boolean isServer769() {\n        return serverProtocol == ProtocolVersion.V1_21_4;\n    }\n\n    /** クライアントが 1.21.5（770）か。 */\n    private boolean is770() {\n        return clientProtocol == ProtocolVersion.V1_21_5;\n    }\n\n    /** 内部が 1.21.5（770）か。 */\n    private boolean isServer770() {\n        return serverProtocol == ProtocolVersion.V1_21_5;\n    }\n\n    /** クライアントが 1.21.6（771）か。 */\n    private boolean is771() {\n        return clientProtocol == ProtocolVersion.V1_21_6;\n    }\n\n    /** 内部が 1.21.6（771）か。 */\n    private boolean isServer771() {\n        return serverProtocol == ProtocolVersion.V1_21_6;\n    }'),
    ('login client list',
     '                if (is764() || is765() || is766() || is767() || is768() || is769() || is770()) {',
     '                if (is764() || is765() || is766() || is767() || is768() || is769() || is770() || is771()) {'),
    ('login comment',
     '                    // 764／765／766／767／768／769／770クライアント：クライアントもバニラもこれから\n                    // 設定フェーズに入る。内部765＋764クライアントと、',
     '                    // 764／765／766／767／768／769／770／771クライアント：クライアントもバニラもこれから\n                    // 設定フェーズに入る。内部765＋764クライアントと、'),
    ('login 763-branch comment',
     '                // （Login Acknowledged は空、Client Information は\n                // 3バージョンで同じフィールド）である。768／769／770は Client\n                // Information の末尾に particleStatus があるため、\n                // 付け足して送る（付けないと768／769／770のバニラが切断する）。',
     '                // （Login Acknowledged は空、Client Information は\n                // 3バージョンで同じフィールド）である。768／769／770／771は Client\n                // Information の末尾に particleStatus があるため、\n                // 付け足して送る（付けないと768／769／770／771のバニラが切断する）。'),
    ('login clientInformation',
     '                byte[] clientInformation = isServer768() || isServer769() || isServer770()\n                        ? PlayTranslator.translateClientInformation767To768(',
     '                byte[] clientInformation = isServer768() || isServer769() || isServer770() || isServer771()\n                        ? PlayTranslator.translateClientInformation767To768('),
    ('synthesized config registry loop',
     '        if (is766() || is767() || is768() || is769() || is770()) {\n            // 766／767／768／769／770：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is770()) {\n                    // 770：766→767→768→769→770へ四段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                }\n                toClient.add(new MinecraftPacket(0x07, registryPayload));\n            }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771()) {\n            // 766／767／768／769／770／771：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is771()) {\n                    // 771：766→767→768→769→770→771へ五段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                }\n                toClient.add(new MinecraftPacket(0x07, registryPayload));\n            }'),
    ('synthesized ackId',
     '        int ackId = is766() || is767() || is768() || is769() || is770() ? 0x03 : 0x02;',
     '        int ackId = is766() || is767() || is768() || is769() || is770() || is771() ? 0x03 : 0x02;'),
    ('synthesized add-pack',
     '            if (is768() || is769() || is770()) {\n                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。770は 0x4A。',
     '            if (is768() || is769() || is770() || is771()) {\n                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。771は 0x4A。'),
    ('startConfigurationClientId',
     '        if (is766() || is767() || is768() || is769() || is770()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : is770() ? 0x6F : 0x69);\n        }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : is770() || is771() ? 0x6F : 0x69);\n        }'),
    ('spawnPositionClientId',
     '        if (is766() || is767() || is768() || is769() || is770()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : is770() ? 0x5A : 0x56);\n        }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : is770() || is771() ? 0x5A : 0x56);\n        }'),
    ('keepAliveClientId',
     '        if (is766() || is767() || is768() || is769() || is770()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x27 : 0x26);\n        }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x27 : 0x26);\n        }'),
    ('pingClientId',
     '        if (is766() || is767() || is768() || is769() || is770()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_PING,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : is770() ? 0x36 : 0x35);\n        }',
     '        if (is766() || is767() || is768() || is769() || is770() || is771()) {\n            return PacketTranslator.toClientId(PacketName.PLAY_PING,\n                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : is770() || is771() ? 0x36 : 0x35);\n        }'),
    ('handleClientPlay dispatch',
     '        // 770クライアント：番号を内部向けに付け替える。内部769向けは\n        // 770→769へ変換してから送る。内部768／767向けは770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is770()) {\n            return handleClientPlay770(packet);\n        }\n        // 内部770：763／764／765／766／767／768／769クライアントの番号を770向けに付け替える。\n        if (isServer770()) {\n            return handleClientPlayTo770(packet);\n        }\n        // 769クライアント：番号を内部向けに付け替える。内部768向けは\n        // 769→768へ変換してから送る。内部767／766向けは769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is769()) {\n            return handleClientPlay769(packet);\n        }\n        // 内部769：763／764／765／766／767／768クライアントの番号を769向けに付け替える。\n        if (isServer769()) {\n            return handleClientPlayTo769(packet);\n        }\n        // 768クライアント：番号を内部向けに付け替える。内部766／767向けは\n        // スロットを768→767へ変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is768()) {\n            return handleClientPlay768(packet);\n        }\n        // 内部768：763／764／765／766／767クライアントの番号を768向けに付け替える。\n        if (isServer768()) {\n            return handleClientPlayTo768(packet);\n        }',
     '        // 771クライアント：番号を内部向けに付け替える。内部770向けは\n        // 771→770へ変換してから送る。内部768／767向けは771→770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is771()) {\n            return handleClientPlay771(packet);\n        }\n        // 内部771：763／764／765／766／767／768／769／770クライアントの番号を771向けに付け替える。\n        if (isServer771()) {\n            return handleClientPlayTo771(packet);\n        }\n        // 770クライアント：番号を内部向けに付け替える。内部769向けは\n        // 770→769へ変換してから送る。内部768／767向けは770→769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is770()) {\n            return handleClientPlay770(packet);\n        }\n        // 内部770：763／764／765／766／767／768／769クライアントの番号を770向けに付け替える。\n        if (isServer770()) {\n            return handleClientPlayTo770(packet);\n        }\n        // 769クライアント：番号を内部向けに付け替える。内部768向けは\n        // 769→768へ変換してから送る。内部767／766向けは769→768→767／766へ\n        // 二段で変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is769()) {\n            return handleClientPlay769(packet);\n        }\n        // 内部769：763／764／765／766／767／768クライアントの番号を769向けに付け替える。\n        if (isServer769()) {\n            return handleClientPlayTo769(packet);\n        }\n        // 768クライアント：番号を内部向けに付け替える。内部766／767向けは\n        // スロットを768→767へ変換する。内部763／764／765向けは766クライアントと\n        // 同じくデータコンポーネントのため落とす。\n        if (is768()) {\n            return handleClientPlay768(packet);\n        }\n        // 内部768：763／764／765／766／767クライアントの番号を768向けに付け替える。\n        if (isServer768()) {\n            return handleClientPlayTo768(packet);\n        }'),
    ('handleServerPlay dispatch',
     '        // 内部770：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（769から -1）。\n        if (isServer770()) {\n            return handleServerPlayFrom770(packet);\n        }\n        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70（768と同じ番号）。\n        if (isServer769()) {\n            return handleServerPlayFrom769(packet);\n        }\n        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70。\n        if (isServer768()) {\n            return handleServerPlayFrom768(packet);\n        }',
     '        // 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（770と同じ）。\n        if (isServer771()) {\n            return handleServerPlayFrom771(packet);\n        }\n        // 内部770：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（769から -1）。\n        if (isServer770()) {\n            return handleServerPlayFrom770(packet);\n        }\n        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70（768と同じ番号）。\n        if (isServer769()) {\n            return handleServerPlayFrom769(packet);\n        }\n        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70。\n        if (isServer768()) {\n            return handleServerPlayFrom768(packet);\n        }'),
    ('handleServerConfiguration 763 branch',
     '        if (is763()) {\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }',
     '        if (is763()) {\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }'),
    ('handleServerConfiguration 771 branch',
     '        // 770クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is770()) {',
     '        // 771クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is771()) {\n            if (isServer771()) {\n                // 内部771＋771クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration771From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration771From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration771From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration771From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration771From766(packet);\n            }\n            return handleServerConfiguration771(packet, isServer764());\n        }\n        // 770クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is770()) {'),
    ('handleServerConfiguration 767 branch',
     '            if (isServer770()) {\n                return handleServerConfiguration767From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration767From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration767From768(packet);\n            }',
     '            if (isServer771()) {\n                return handleServerConfiguration767From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration767From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration767From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration767From768(packet);\n            }'),
    ('handleServerConfiguration 766 branch',
     '            if (isServer770()) {\n                return handleServerConfiguration766From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration766From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration766From768(packet);\n            }',
     '            if (isServer771()) {\n                return handleServerConfiguration766From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration766From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration766From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration766From768(packet);\n            }'),
    ('handleServerConfiguration From771 branch',
     '        // 内部770の場合は番号と Registry Data の形式を変換する。\n        if (isServer770()) {\n            return handleServerConfigurationFrom770(packet, is764());\n        }\n        // 内部769の場合は番号と Registry Data の形式を変換する。\n        if (isServer769()) {\n            return handleServerConfigurationFrom769(packet, is764());\n        }\n        // 内部768の場合は番号と Registry Data の形式を変換する。\n        if (isServer768()) {\n            return handleServerConfigurationFrom768(packet, is764());\n        }',
     '        // 内部771の場合は番号と Registry Data の形式を変換する。\n        if (isServer771()) {\n            return handleServerConfigurationFrom771(packet, is764());\n        }\n        // 内部770の場合は番号と Registry Data の形式を変換する。\n        if (isServer770()) {\n            return handleServerConfigurationFrom770(packet, is764());\n        }\n        // 内部769の場合は番号と Registry Data の形式を変換する。\n        if (isServer769()) {\n            return handleServerConfigurationFrom769(packet, is764());\n        }\n        // 内部768の場合は番号と Registry Data の形式を変換する。\n        if (isServer768()) {\n            return handleServerConfigurationFrom768(packet, is764());\n        }'),
    ('handleClientConfiguration 771 branch',
     '        // 768クライアント：番号を764／765向けに付け替える。\n        if (is768()) {\n            return handleClientConfiguration768(packet);\n        }\n        // 769クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は768と同じため768の処理を使い回す。\n        if (is769()) {\n            return handleClientConfiguration769(packet);\n        }\n        // 770クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は769と同じため769の処理を使い回す。\n        if (is770()) {\n            return handleClientConfiguration770(packet);\n        }',
     '        // 768クライアント：番号を764／765向けに付け替える。\n        if (is768()) {\n            return handleClientConfiguration768(packet);\n        }\n        // 769クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は768と同じため768の処理を使い回す。\n        if (is769()) {\n            return handleClientConfiguration769(packet);\n        }\n        // 770クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は769と同じため769の処理を使い回す。\n        if (is770()) {\n            return handleClientConfiguration770(packet);\n        }\n        // 771クライアント：番号を764／765向けに付け替える。設定フェーズの\n        // 番号・形式は770と同じため770の処理を使い回す。\n        if (is771()) {\n            return handleClientConfiguration771(packet);\n        }'),
    ('handleClientConfiguration 767+771 branch',
     '        // 767クライアント＋内部768：番号を768向けに付け替える。766クライアントと\n        // 同じ（設定フェーズの番号・形式は766＝767）。\n        if (is767() && isServer768()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer769()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer770()) {\n            return handleClientConfiguration767To768(packet);\n        }',
     '        // 767クライアント＋内部768：番号を768向けに付け替える。766クライアントと\n        // 同じ（設定フェーズの番号・形式は766＝767）。\n        if (is767() && isServer768()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer769()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer770()) {\n            return handleClientConfiguration767To768(packet);\n        }\n        // 767クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is767() && isServer771()) {\n            return handleClientConfiguration767To768(packet);\n        }'),
    ('handleClientConfiguration 766+771 branch',
     '        // 766クライアント＋内部768：番号を768向けに付け替える。\n        if (is766() && isServer768()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer769()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer770()) {\n            return handleClientConfiguration766To768(packet);\n        }',
     '        // 766クライアント＋内部768：番号を768向けに付け替える。\n        if (is766() && isServer768()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer769()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer770()) {\n            return handleClientConfiguration766To768(packet);\n        }\n        // 766クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (is766() && isServer771()) {\n            return handleClientConfiguration766To768(packet);\n        }'),
    ('handleClientConfiguration To771 branch',
     '        // 764／765クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため769向けの処理を使い回す。\n        if (isServer770()) {\n            return handleClientConfigurationTo770(packet);\n        }\n        // 764／765クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (isServer769()) {\n            return handleClientConfigurationTo769(packet);\n        }\n        // 764／765クライアント＋内部768：番号を768向けに付け替える。\n        if (isServer768()) {\n            return handleClientConfigurationTo768(packet);\n        }',
     '        // 764／765クライアント＋内部771：番号を771向けに付け替える。770と\n        // 同じ番号・形式のため770向けの処理を使い回す。\n        if (isServer771()) {\n            return handleClientConfigurationTo771(packet);\n        }\n        // 764／765クライアント＋内部770：番号を770向けに付け替える。769と\n        // 同じ番号・形式のため769向けの処理を使い回す。\n        if (isServer770()) {\n            return handleClientConfigurationTo770(packet);\n        }\n        // 764／765クライアント＋内部769：番号を769向けに付け替える。768と\n        // 同じ番号・形式のため768向けの処理を使い回す。\n        if (isServer769()) {\n            return handleClientConfigurationTo769(packet);\n        }\n        // 764／765クライアント＋内部768：番号を768向けに付け替える。\n        if (isServer768()) {\n            return handleClientConfigurationTo768(packet);\n        }'),
    ('synthesized add-pack 771 id',
     '            if (is768() || is769() || is770() || is771()) {\n                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。771は 0x4A。\n                toClient.add(new MinecraftPacket(is770() ? 0x4A : 0x4B,',
     '            if (is768() || is769() || is770() || is771()) {\n                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。771は 0x4A。\n                toClient.add(new MinecraftPacket(is770() || is771() ? 0x4A : 0x4B,'),
    ('chatGlobalIndex field',
     '    /**\n     * 771クライアントに送るリソースパック（pack_format 63）。\n     * 内部が771でない場合に使う差し替え用。内部が771の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack771;\n\n    /**\n     * 770クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;',
     '    /**\n     * 771クライアントに送るリソースパック（pack_format 63）。\n     * 内部が771でない場合に使う差し替え用。内部が771の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack771;\n\n    /**\n     * 770／771クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;'),
    ('handleServerPlayFrom769 771 branch',
     '        // 770クライアント：769→770へ変換する。\n        if (is770()) {\n            return handleServerPlay769To770(packet);\n        }\n        // 768クライアント：769→768へ変換する。\n        if (is768()) {\n            return handleServerPlay769To768(packet);\n        }',
     '        // 771クライアント：769→770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay769To771(packet);\n        }\n        // 770クライアント：769→770へ変換する。\n        if (is770()) {\n            return handleServerPlay769To770(packet);\n        }\n        // 768クライアント：769→768へ変換する。\n        if (is768()) {\n            return handleServerPlay769To768(packet);\n        }'),
    ('handleServerPlayFrom769 Respawn 771',
     '        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n        if (packet.id() == 0x4C) {\n            if (is770()) {\n                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is768()) {',
     '        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n        if (packet.id() == 0x4C) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {\n                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is768()) {'),
    ('handleServerPlayFrom769 Disconnect 771',
     '            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768()) {\n                // 766／767／768クライアント：Disconnect は 0x1D（NBT）のまま。',
     '            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768()) {\n                // 766／767／768クライアント：Disconnect は 0x1D（NBT）のまま。'),
    ('handleServerJoinGame769 771 branch',
     '        if (is770()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is768()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));',
     '        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is768()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));'),
    ('handleServerPlayFrom768 Respawn 771',
     '        // Respawn（768：0x4C）。\n        if (packet.id() == 0x4C) {\n            if (is770()) {\n                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is769()) {',
     '        // Respawn（768：0x4C）。\n        if (packet.id() == 0x4C) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {\n                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is769()) {'),
    ('handleServerPlayFrom768 Disconnect 771',
     '            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is769()) {\n                // 766／767／769クライアント：Disconnect は 0x1D（NBT）のまま。',
     '            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is769()) {\n                // 766／767／769クライアント：Disconnect は 0x1D（NBT）のまま。'),
    ('handleServerPlayFrom768 771 branch',
     '        // 770クライアント：スロット等は768→769→770へ変換する。\n        if (is770()) {\n            return handleServerPlay768To770(packet);\n        }\n        // 769クライアント：スロット等は768→769へ変換する。\n        if (is769()) {\n            return handleServerPlay768To769(packet);\n        }',
     '        // 771クライアント：スロット等は768→769→770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay768To771(packet);\n        }\n        // 770クライアント：スロット等は768→769→770へ変換する。\n        if (is770()) {\n            return handleServerPlay768To770(packet);\n        }\n        // 769クライアント：スロット等は768→769へ変換する。\n        if (is769()) {\n            return handleServerPlay768To769(packet);\n        }'),
    ('handleServerJoinGame768 771 branch',
     '        if (is770()) {\n            // 770クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {\n            // 769クライアント：Join Game は768と同形式のためそのまま通す。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));',
     '        if (is771()) {\n            // 771クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            // 770クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {\n            // 769クライアント：Join Game は768と同形式のためそのまま通す。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));'),
    ('handleServerPlayFrom766 771 branch',
     '        // 770クライアント：スロット等は766／767→768→769→770へ変換する。\n        // 内部766は 766→767→768→769→770 の四段、内部767は 767→768→769→770 の三段で送る。\n        if (is770()) {\n            return isServer766() ? handleServerPlay766To770(packet)\n                    : handleServerPlay767To770(packet);\n        }\n        // 769クライアント：スロット等は766／767→768→769へ変換する。\n        // 内部766は 766→767→768→769 の三段、内部767は 767→768→769 の二段で送る。\n        if (is769()) {\n            return isServer766() ? handleServerPlay766To769(packet)\n                    : handleServerPlay767To769(packet);\n        }',
     '        // 771クライアント：スロット等は766／767→768→769→770→771へ変換する。\n        // 内部766は 766→767→768→769→770→771 の五段、内部767は 767→768→769→770→771 の四段で送る。\n        if (is771()) {\n            return isServer766() ? handleServerPlay766To771(packet)\n                    : handleServerPlay767To771(packet);\n        }\n        // 770クライアント：スロット等は766／767→768→769→770へ変換する。\n        // 内部766は 766→767→768→769→770 の四段、内部767は 767→768→769→770 の三段で送る。\n        if (is770()) {\n            return isServer766() ? handleServerPlay766To770(packet)\n                    : handleServerPlay767To770(packet);\n        }\n        // 769クライアント：スロット等は766／767→768→769へ変換する。\n        // 内部766は 766→767→768→769 の三段、内部767は 767→768→769 の二段で送る。\n        if (is769()) {\n            return isServer766() ? handleServerPlay766To769(packet)\n                    : handleServerPlay767To769(packet);\n        }'),
    ('handleServerPlayFrom763 ResourcePack 771',
     '            PendingPack pack = parseResourcePack764(packet.payload());\n            lastSentPack = pack;\n            if (is766() || is767() || is768() || is769() || is770()) {\n                return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : is770() ? 0x4A : 0x46,',
     '            PendingPack pack = parseResourcePack764(packet.payload());\n            lastSentPack = pack;\n            if (is766() || is767() || is768() || is769() || is770() || is771()) {\n                return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : is770() || is771() ? 0x4A : 0x46,'),
    ('handleServerPlayFrom763 Disconnect 771',
     '            if (is770()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1D, payload)), false, true);\n            }',
     '            if (is771()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is770()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1D, payload)), false, true);\n            }'),
    ('handleServerPlayFrom763 Respawn 771',
     '        if (name == PacketName.PLAY_RESPAWN) {\n            if (is766() || is767() || is768() || is769() || is770()) {',
     '        if (name == PacketName.PLAY_RESPAWN) {\n            if (is766() || is767() || is768() || is769() || is770() || is771()) {'),
    ('handleServerPlayFrom763 Advancements 771',
     '        if (name == PacketName.PLAY_ADVANCEMENTS) {\n            if (is766() || is767() || is768() || is769() || is770()) {',
     '        if (name == PacketName.PLAY_ADVANCEMENTS) {\n            if (is766() || is767() || is768() || is769() || is770() || is771()) {'),
    ('handleServerPlayFrom763 final drop 771',
     '        // 766／767／768／769／770向け：チャット・表示系は763が JSON 文字列・766が NBT のため、\n        // JSON のまま送ると誤読される。チャンク・ブロック状態・スロット・\n        // エンティティも766の変換が要る。Join Game／Spawn Position／\n        // Keep Alive／Ping／Respawn 以外は未対応のため落とす\n        // （766のプレイ状態の変換は別担当の対応表待ち）。\n        if (is766() || is767() || is768() || is769() || is770()) {',
     '        // 766／767／768／769／770／771向け：チャット・表示系は763が JSON 文字列・766が NBT のため、\n        // JSON のまま送ると誤読される。チャンク・ブロック状態・スロット・\n        // エンティティも766の変換が要る。Join Game／Spawn Position／\n        // Keep Alive／Ping／Respawn 以外は未対応のため落とす\n        // （766のプレイ状態の変換は別担当の対応表待ち）。\n        if (is766() || is767() || is768() || is769() || is770() || is771()) {'),
    ('handleServerPlayFrom770 771 branch',
     '        // 769クライアント：770→769へ変換する。\n        if (is769()) {\n            return handleServerPlay770To769(packet);\n        }',
     '        // 771クライアント：770→771へ変換する。\n        if (is771()) {\n            return handleServerPlay770To771(packet);\n        }\n        // 769クライアント：770→769へ変換する。\n        if (is769()) {\n            return handleServerPlay770To769(packet);\n        }'),
    ('handleServerPlayFrom770 Respawn 771',
     '        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is769()) {',
     '        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is769()) {'),
    ('handleServerPlayFrom770 Disconnect 771',
     '            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。',
     '            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。'),
    ('handleServerJoinGame770 771 branch',
     '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is769()) {',
     '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {'),
    ('handleServerConfiguration 770 branch isServer771',
     '            if (isServer769()) {\n                return handleServerConfiguration770From769(packet);\n            }',
     '            if (isServer771()) {\n                return handleServerConfiguration770From771(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration770From769(packet);\n            }'),
    ('handleServerConfiguration 769 branch isServer771',
     '            if (isServer770()) {\n                return handleServerConfiguration769From770(packet);\n            }',
     '            if (isServer771()) {\n                return handleServerConfiguration769From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration769From770(packet);\n            }'),
    ('handleServerConfiguration 768 branch isServer771',
     '            if (isServer770()) {\n                return handleServerConfiguration768From770(packet);\n            }',
     '            if (isServer771()) {\n                return handleServerConfiguration768From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration768From770(packet);\n            }'),
    ('handleClientConfiguration 771-server guard',
     '        if (synthesizingConfig) {\n            return handleSynthesizedClientConfig(packet);\n        }',
     '        if (synthesizingConfig) {\n            return handleSynthesizedClientConfig(packet);\n        }\n        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }'),
]

CLIENT_PLAY_771 = r'''
    /**
     * 771クライアント→内部770以下のプレイ状態（クライアント→サーバー）。
     * 771→770へ変換してから {@link #handleClientPlay770} に通す。
     * 内部770の場合は変換した770パケットをそのまま送る（770の処理は
     * 内部770への送出を想定していないため、委譲ではなく直接送る）。
     * Pick Item From Block／Entity、Player Loaded は770にもあり同形式のため
     * そのまま通す。Vehicle Move は770・771とも末尾に onGround があるため
     * そのまま通す。Window Click は771・770とも HashedSlot のため落とす。
     * Set Creative Slot は UntrustedSlot のままアイテム／成分を付け替える。
     * Change Gamemode／Custom Click Action は770に無いため落とす。
     */
    private Outcome handleClientPlay771(MinecraftPacket packet) {
        MinecraftPacket as770 = to770ClientPacketFrom771(packet);
        if (as770 == null) {
            return Outcome.drop();
        }
        if (isServer770()) {
            return Outcome.toServer(as770);
        }
        return handleClientPlay770(as770);
    }

    /**
     * 771クライアントのパケットを770形式にする。770に相当が無いものは
     * null を返し、呼び出し元が落とす（ログ済み）。
     */
    private MinecraftPacket to770ClientPacketFrom771(MinecraftPacket packet) {
        // Vehicle Move（771：0x21→770：0x20）：770・771とも末尾に onGround が
        // あるため形式は同一。そのまま通す。
        if (packet.id() == 0x21) {
            return new MinecraftPacket(0x20,
                    PlayTranslator.translateVehicleMove771To770(packet.payload()));
        }
        // Window Click（771：0x11）：771・770とも HashedSlot のため
        // 落とす（ハッシュの計算方法は未確定）。
        if (packet.id() == 0x11) {
            logUntranslatedOnce("PLAY_WINDOW_CLICK");
            return null;
        }
        // Set Creative Slot（771：0x37→770：0x36）：
        // UntrustedSlot のままアイテム／成分を付け替える。
        if (packet.id() == 0x37) {
            return new MinecraftPacket(0x36,
                    ChunkTranslator.translateSetCreativeSlot771To770(packet.payload()));
        }
        // Chat Message／Chat Command Signed：末尾の checksum（u8）は
        // 770・771ともあるため剥がさない。番号だけ付け替える（下の generic 経路）。
        // それ以外：番号だけ770向けに付け替える（ペイロードは同形式）。
        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, ProtocolVersion.V1_21_6);
        if (name.isEmpty()) {
            LOG.warning("771向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return null;
        }
        OptionalInt id770 = PacketTranslator.translateId(name.get(),
                ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_5);
        if (id770.isEmpty()) {
            LOG.warning("770に無いプレイ状態のパケット " + name.get() + " を落とします。");
            return null;
        }
        return new MinecraftPacket(id770.getAsInt(), packet.payload());
    }

    /**
     * 763／764／765／766／767／768／769／770クライアント→内部771のプレイ状態（クライアント→サーバー）。
     * 名前で分ける。番号だけの付け替えは {@link PacketTranslator#translateId} に任せ、
     * ペイロード変換が要るものだけ明示する。770クライアントは770形式のまま
     * {@link #handleClientPlay770To771} で771へ直す。767クライアントの
     * Set Creative Slot は767→768→769→770→771へ四段で変換する
     * （Window Click は HashedSlot のため落とす。766以下はデータコンポーネント形式のため落とす）。
     * Craft Recipe Request／Displayed Recipe はレシピIDが付け替えられないため落とす。
     * 旧形式の Pick Item は771に相当が無いため落とす（771は位置・対象を指定するため
     * スロット番号からは変換できない）。
     * Vehicle Move は771の末尾に onGround が付くため、旧クライアント分は
     * onGround=false を付けて送る（旧クライアントは送ってこないための近似）。
     */
    private Outcome handleClientPlayTo771(MinecraftPacket packet) {
        if (is770()) {
            return handleClientPlay770To771(packet);
        }
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, clientProtocol).orElse(null);
        if (name == null) {
            LOG.warning("771向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return Outcome.drop();
        }
        switch (name) {
            case PLAY_WINDOW_CLICK -> {
                // HashedSlot のハッシュ計算方法が未確定のため、771向けは落とす。
                logUntranslatedOnce("PLAY_WINDOW_CLICK");
                return Outcome.drop();
            }
            case PLAY_SET_CREATIVE_SLOT -> {
                if (is767()) {
                    byte[] payload = ChunkTranslator.translateSetCreativeSlot770To771(
                            ChunkTranslator.translateSetCreativeSlot769To770(
                                    ChunkTranslator.translateSetCreativeSlot768To769(
                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload()))));
                    return Outcome.toServer(new MinecraftPacket(0x37, payload));
                }
                logUntranslatedOnce("PLAY_SET_CREATIVE_SLOT");
                return Outcome.drop();
            }
            case PLAY_SET_PLAYER_POSITION, PLAY_SET_PLAYER_POSITION_AND_ROTATION,
                    PLAY_SET_PLAYER_ROTATION, PLAY_FLYING -> {
                // 770→771の移動パケットは同形式のため、768→769の変換だけでよい（768／769／770／771は同形式）。
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
                        name, clientProtocol, ProtocolVersion.V1_21_6);
                if (serverId.isEmpty()) {
                    LOG.warning("771向けに変換できないプレイ状態のパケット（id=0x"
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
                // 769は onGround 付き（770・771と同形式）。768以下は onGround が
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
                // 旧形式の Pick Item（スロット番号）は771に相当が無い。
                logUntranslatedOnce(name);
                return Outcome.drop();
            }
            case PLAY_CHAT_COMMAND -> {
                if (is763() || is764() || is765()) {
                    // 763／764／765の Chat Command は完全な形式（署名付き）のため、
                    // 771の Chat Command Signed（0x07）に送る。
                    return Outcome.toServer(new MinecraftPacket(0x07, packet.payload()));
                }
                // 766／767の符号なし Chat Command は771の 0x06（同形式）。
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
                // 771 形式にする。付けないと 771 サーバーが読み取り途中で切断する。
                // 769は 9 フィールド形式（particleStatus 付き）のためそのまま送る。
                byte[] payload = is769() ? packet.payload()
                        : PlayTranslator.translateClientInformation767To768(
                                packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x0d, payload));
            }
            default -> {
                OptionalInt serverId = PacketTranslator.translateId(
                        name, clientProtocol, ProtocolVersion.V1_21_6);
                if (serverId.isPresent()) {
                    return Outcome.toServer(new MinecraftPacket(serverId.getAsInt(),
                            packet.payload()));
                }
                LOG.warning("771向けに変換できないプレイ状態のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                return Outcome.drop();
            }
        }
    }

    /**
     * 770クライアント→内部771のプレイ状態（クライアント→サーバー）。
     * 770→771へ変換してそのまま送る。770は既にデータコンポーネント形式の
     * ため、767向けの変換（particleStatus 付け足し等）は要らない。
     */
    private Outcome handleClientPlay770To771(MinecraftPacket packet) {
        MinecraftPacket as771 = to771ClientPacket(packet);
        if (as771 == null) {
            return Outcome.drop();
        }
        return Outcome.toServer(as771);
    }

    /**
     * 770クライアントのパケットを771形式にする。771に相当が無いものは
     * null を返し、呼び出し元が落とす（ログ済み）。
     */
    private MinecraftPacket to771ClientPacket(MinecraftPacket packet) {
        // Vehicle Move（770：0x20→771：0x21）：770・771とも末尾に onGround が
        // あるため形式は同一。そのまま通す。
        if (packet.id() == 0x20) {
            return new MinecraftPacket(0x21,
                    PlayTranslator.translateVehicleMove770To771(packet.payload()));
        }
        // Window Click（770：0x10）：771は HashedSlot で、コンポーネントの
        // ハッシュ計算方法が未確定のため落とす。
        if (packet.id() == 0x10) {
            logUntranslatedOnce("PLAY_WINDOW_CLICK");
            return null;
        }
        // Set Creative Slot（770：0x36→771：0x37）：
        // UntrustedSlot のままアイテム／成分を付け替える。
        if (packet.id() == 0x36) {
            return new MinecraftPacket(0x37,
                    ChunkTranslator.translateSetCreativeSlot770To771(packet.payload()));
        }
        // Chat Message／Chat Command Signed：末尾の checksum（u8）は
        // 770・771ともあるため付け足しは要らない。番号だけ付け替える（下の generic 経路）。
        // それ以外：番号だけ771向けに付け替える（ペイロードは同形式）。
        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, ProtocolVersion.V1_21_5);
        if (name.isEmpty()) {
            LOG.warning("771向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return null;
        }
        OptionalInt id771 = PacketTranslator.translateId(name.get(),
                ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6);
        if (id771.isEmpty()) {
            LOG.warning("771に無いプレイ状態のパケット " + name.get() + " を落とします。");
            return null;
        }
        return new MinecraftPacket(id771.getAsInt(), packet.payload());
    }
'''

SERVER_PLAY_771 = r'''
    /**
     * 内部771→クライアントのプレイ状態（サーバー→クライアント）。
     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／
     * Start Configuration は770と同形式（ID も同じ）のため、770の処理を使い回す。
     * ただし770クライアントへの送出だけは771→770へ直すため、
     * Join Game／Respawn／Disconnect には770向けの分岐がある。
     * それ以外は771→770へ変換してから、770クライアントにはそのまま送り、
     * 767／766クライアントには既存の770→769／766の処理に通す。
     * 763／764／765クライアントは join レベルのみ（770と同じ）。
     */
    private Outcome handleServerPlayFrom771(MinecraftPacket packet) {
        // Start Configuration（771：0x6F）。763には設定フェーズもこのパケットも無い。
        if (packet.id() == 0x6F) {
            if (is763()) {
                serverState = ServerState.CONFIGURATION;
                reconfiguring = true;
                LOG.info(() -> "プレイ中の Start Configuration を受けました（" + playerName
                        + "）。サーバー側の設定フェーズを肩代わりします。");
                return Outcome.drop();
            }
            int outId = PacketTranslator.translateId(
                    PacketName.PLAY_START_CONFIGURATION, 771, clientProtocol)
                    .orElse(is764() ? 0x65 : 0x67);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Join Game（771：0x2B）。
        if (packet.id() == 0x2B) {
            return handleServerJoinGame771(packet);
        }
        // Spawn Position（771：0x5A）。
        if (packet.id() == 0x5A) {
            advanceFlowSafely(() -> joinFlow.spawned());
            int outId = PacketTranslator.translateId(
                    PacketName.PLAY_SPAWN_POSITION, 771, clientProtocol)
                    .orElse(is764() ? 0x52 : 0x54);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Keep Alive（771：0x26）／Ping（771：0x36）は接続維持のため中継する。
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
        // Disconnect（771：0x1C、NBT）。
        if (packet.id() == 0x1C) {
            closed = true;
            if (is763() || is764()) {
                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());
                int outId = is763() ? 0x1A : 0x1B;
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(outId, payload)), false, true);
            }
            if (is771()) {
                // 771クライアント：Disconnect は 0x1C（NBT）のまま。
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
        // Respawn（771：0x4B。770（0x4C）と同形式のため770の変換を使い回す）。
        if (packet.id() == 0x4B) {
            if (is770()) {
                // 770クライアント：Respawn は 0x4C（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x4C, packet.payload()));
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
        // 770クライアント：771→770へ変換する。
        if (is770()) {
            return handleServerPlay771To770(packet);
        }
        // 767クライアント：771→770→769→768へ変換してから768→767へ。
        if (is767()) {
            MinecraftPacket as770 = to770ServerPacketFrom771(packet);
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
        // 766クライアント：771→770→769→768へ変換してから768→766へ。
        if (is766()) {
            MinecraftPacket as770 = to770ServerPacketFrom771(packet);
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
                Direction.CLIENTBOUND, 771).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
        } else {
            logUntranslatedOnce(name);
        }
        return Outcome.drop();
    }

    /**
     * Join Game（内部771：0x2B）のクライアント向け変換。
     * 770と同形式のため、770クライアントにはそのまま送る。
     * それ以外は {@link #handleServerJoinGame770} と同じ方針で各クライアントへ送る。
     */
    private Outcome handleServerJoinGame771(MinecraftPacket packet) {
        if (is770()) {
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
        return handleServerJoinGame770(packet);
    }

    /**
     * 771サーバーのプレイ状態パケットを770形式にする。770に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 770クライアントへの送出と、767／766クライアントへの二段変換の
     * 第一段で共用する。
     */
    private MinecraftPacket to770ServerPacketFrom771(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_6).orElse(null);
        // experience_orb：771・770とも通常の Spawn Entity
        // （type experience_orb）のため、専用の分岐は要らない
        // （下の PLAY_SPAWN_ENTITY 経路で通す）。
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata771To770(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot771To770(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem771To770(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory771To770(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems771To770(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown771To770(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment771To770(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList771To770(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow771To770(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction771To770(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity771To770(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect771To770(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect771To770(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound771To770(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles771To770(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect771To770(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect771To770(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent771To770(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes771To770(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData771To770(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange771To770(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange771To770(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData771To770(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo771To770(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat771To770(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem771To770(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion771To770(packet.payload());
            // 771だけのパケット（ロケーターバー／ダイアログ）は770に無いため落とす。
            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG -> null;
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id770 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_5);
        if (id770.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id770.getAsInt(), translated);
    }

    /**
     * 内部771→770クライアントのプレイ状態（サーバー→クライアント）。
     * 771→770へ変換してそのまま送る。
     */
    private Outcome handleServerPlay771To770(MinecraftPacket packet) {
        MinecraftPacket as770 = to770ServerPacketFrom771(packet);
        if (as770 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as770);
    }
'''

CONFIG_771 = r'''
    /**
     * 771クライアント＋内部770：設定フェーズを中継する。
     * 770と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ770→771へ並べ替え、
     * Tags はレジストリ番号がずれるため落とす。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From770(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（770は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：770のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData770To771(packet.payload())));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
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
     * 771クライアント＋内部768：設定フェーズを中継する。
     * 768と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ768→769→770→771へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From768(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（768は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：768のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData770To771(
                                PlayTranslator.translateRegistryData769To770(
                                        PlayTranslator.translateRegistryData768To769(packet.payload())))));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
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
     * 771クライアント＋内部767：設定フェーズを中継する。
     * 767と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ767→768→769→770→771へ三段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From767(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（767は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：767のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload767to768 =
                        PlayTranslator.translateRegistryData767To768(packet.payload());
                byte[] payload768to769 =
                        PlayTranslator.translateRegistryData768To769(payload767to768);
                byte[] payload769to770 =
                        PlayTranslator.translateRegistryData769To770(payload768to769);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData770To771(payload769to770)));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
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
     * 771クライアント＋内部766：設定フェーズを中継する。
     * 766と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ766→767→768→769→770→771へ四段で並べ替え（jukebox_song は
     * 766に無いため、766側の変換で落とされることは無い）、Tags はレジストリ番号が
     * ずれるため落とし、Custom Report Details（0x0F）／Server Links（0x10）は
     * 766に無いため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From766(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(packet); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（766は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：766のエントリ配列を771の順序に並べ替える
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
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData770To771(payload769to770)));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
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
     * 771クライアント＋内部764／765：設定フェーズを中継する。
     * {@link #handleServerConfiguration770} と同じ番号の付け替えで、
     * Registry Data だけ764／765の codec を771のエントリ配列に変換する
     * （765→767→768→769→770→771へ五段）。
     *
     * @param from764 サーバーが764の場合 true。false の場合は765
     */
    private Outcome handleServerConfiguration771(MinecraftPacket packet, boolean from764) {
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
            case 0x05 -> { // Registry Data：764／765の codec を771のエントリ配列に変換する
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
                    toClient.add(new MinecraftPacket(0x07,
                            PlayTranslator.translateRegistryData770To771(payload769to770)));
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
                    LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x0C, packet.payload()));
            }
            case 0x09 -> { // 765の Tags：レジストリ番号がずれるため落とす（764には無い番号）
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
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
     * 763クライアント＋内部771：設定フェーズをプロキシが771クライアント役
     * として消費する。771の設定フェーズ：0x00 Cookie Request、
     * 0x01 Custom Payload、0x02 Disconnect（NBT）、0x03 Finish Configuration、
     * 0x04 Keep Alive、0x05 Ping、0x06 Reset Chat、0x07 Registry Data
     * （エントリ配列）、0x08 Remove Resource Pack、0x09 Add Resource Pack
     * （UUID付き）、0x0A Store Cookie、0x0B Transfer、0x0C Feature Flags、
     * 0x0D Tags、0x0E Select Known Packs、0x0F Custom Report Details、
     * 0x10 Server Links。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す（バニラが待ち受けない
     * ようにする）。Transfer は763クライアントに送る先が無いため落とす。
     * Join Game は771形式で来るので、763向けには
     * {@link #handleServerJoinGame771} で変換する。
     * Registry Data は763クライアント向けの Join Game codec を作るために
     * 768形式に直してから蓄える（771のままではバイオーム pale_garden／
     * plains の番号がずれるため）。
     */
    private Outcome handleServerConfiguration763From771(MinecraftPacket packet) {
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
            case 0x07 -> { // Registry Data（771はエントリ配列。parseRegistryData が読む）
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 763向けの辞書は768形式で蓄える（771のままでは
                // バイオーム pale_garden／plains の番号がずれる）。
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_4)));
                yield Outcome.drop();
            }
            case 0x08 -> Outcome.drop(); // Remove Resource Pack（763に相当無し）
            case 0x09 -> { // Add Resource Pack（771：UUID付き）：保持して Join Game の後に送る
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
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 764／765クライアント＋内部771：設定フェーズを中継する。
     * {@link #handleServerConfigurationFrom770} と同じ番号の付け替えで、
     * Registry Data だけ771→770→769→768→767→765へ三段で変換する（jukebox_song は落とす）。
     * 763向けの辞書には768形式で蓄える。
     *
     * @param to764 クライアントが764の場合 true。false の場合は765クライアント
     */
    private Outcome handleServerConfigurationFrom771(MinecraftPacket packet, boolean to764) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x00, packet.payload())); // Custom Payload → Plugin Message
            case 0x02 -> { // Disconnect（771は NBT。764は JSON 文字列）
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
            case 0x07 -> { // Registry Data：771のエントリ配列を764／765の codec に変換する
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_4)));
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(packet.payload());
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
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 767クライアント＋内部771：設定フェーズを中継する。
     * 767と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→770→769→768→767へ四段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration767From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。767も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を767の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(packet.payload());
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
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 766クライアント＋内部771：設定フェーズを中継する。
     * 766と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→770→769→768→767→766へ五段で並べ替え（jukebox_song は落とす）、
     * Tags はレジストリ番号がずれるため落とし、Custom Report Details
     * （0x0F）／Server Links（0x10）は766に無いため落とす。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration766From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(packet); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。766も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を766の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(packet.payload());
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
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 771クライアントの設定フェーズ（クライアント→バニラ）。
     * 771と770の設定フェーズの番号・形式は同じため、
     * {@link #handleClientConfiguration770} を使い回す。
     */
    /**
     * 771クライアント＋内部770以下の設定フェーズ（クライアント→バニラ）。
     * 771の番号は770と同じため、ほとんど {@link #handleClientConfiguration770}
     * と同じ。ただし Custom Click Action（0x08）は771だけのパケットのため、
     * 771サーバーへはそのまま送り、旧サーバー向けは落とす。
     */
    private Outcome handleClientConfiguration771(MinecraftPacket packet) {
        if (packet.id() == 0x08) {
            // Custom Click Action（771のみ。770では Custom Report Details と同ID）。
            // 771サーバーへはそのまま送り、旧サーバー向けは落とす。
            if (isServer771()) {
                return Outcome.toServer(packet);
            }
            return Outcome.drop();
        }
        return handleClientConfiguration770(packet);
    }

    /**
     * 764／765クライアント＋内部771の設定フェーズ（クライアント→バニラ）。
     * 771の番号は770と同じため {@link #handleClientConfigurationTo770} と同じ。
     */
    private Outcome handleClientConfigurationTo771(MinecraftPacket packet) {
        return handleClientConfigurationTo770(packet);
    }

    /**
     * 771クライアント＋内部769：設定フェーズを中継する。
     * 769と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ769→770→771へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From769(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（769は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：769のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload769to770 =
                        PlayTranslator.translateRegistryData769To770(packet.payload());
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData770To771(payload769to770)));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
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
     * 770クライアント＋内部771：設定フェーズを中継する。
     * 770と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→770へ並べ替え（minecraft:dialog は770に無いため
     * 落とし、jukebox_song の tears は落とす）、Tags はレジストリ番号が
     * ずれるため落とす。Show Dialog（0x11）／Clear Dialog（0x12）は
     * 770に無いため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration770From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。770も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を770の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload = PlayTranslator.translateRegistryData771To770(packet.payload());
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を770向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（770に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部771：設定フェーズを中継する。
     * 769と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→770→769へ二段で並べ替え（minecraft:dialog と
     * jukebox_song の tears は落とす）、Tags はレジストリ番号がずれるため
     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は769に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration769From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。769も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を769の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 769向けの辞書は769形式で蓄える（771と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_4));
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(packet.payload());
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
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 768クライアント＋内部771：設定フェーズを中継する。
     * 768と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→770→769→768へ三段で並べ替え（pale_garden を
     * 落として plains を39に戻す）、Tags はレジストリ番号がずれるため
     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は768に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration768From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。768も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を768の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 768向けの辞書は768形式で蓄える（771のままでは
                // バイオーム pale_garden／plains の番号がずれる）。
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_2)));
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(packet.payload());
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
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

'''

SERVER_PLAY_771_TO = r'''
    /**
     * 770サーバーのプレイ状態パケットを771形式にする。771に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 771クライアントへの送出と、769／768／767／766クライアントへの
     * 多段変換の最終段で共用する。経験値オーブは770・771とも通常の
     * Spawn Entity（type experience_orb）のため、専用の分岐は要らない。
     */
    private MinecraftPacket to771ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_5).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata770To771(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot770To771(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem770To771(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory770To771(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems770To771(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown770To771(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment770To771(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList770To771(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow770To771(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction770To771(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity770To771(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect770To771(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect770To771(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound770To771(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles770To771(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect770To771(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect770To771(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent770To771(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes770To771(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData770To771(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange770To771(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange770To771(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData770To771(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo770To771(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat770To771(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem770To771(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion770To771(packet.payload());
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id771 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6);
        if (id771.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id771.getAsInt(), translated);
    }

    /**
     * 内部770→771クライアントのプレイ状態（サーバー→クライアント）。
     * 770→771へ変換してそのまま送る。
     */
    private Outcome handleServerPlay770To771(MinecraftPacket packet) {
        MinecraftPacket as771 = to771ServerPacket(packet);
        if (as771 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as771);
    }

    /**
     * 内部769→771クライアントのプレイ状態（サーバー→クライアント）。
     * 769→770→771へ二段で変換する。
     */
    private Outcome handleServerPlay769To771(MinecraftPacket packet) {
        Outcome to770 = handleServerPlay769To770(packet);
        if (to770.toClient().isEmpty()) {
            return to770;
        }
        return handleServerPlay770To771(to770.toClient().get(0));
    }

    /**
     * 内部768→771クライアントのプレイ状態（サーバー→クライアント）。
     * 768→770→771へ二段で変換する。
     */
    private Outcome handleServerPlay768To771(MinecraftPacket packet) {
        Outcome to770 = handleServerPlay768To770(packet);
        if (to770.toClient().isEmpty()) {
            return to770;
        }
        return handleServerPlay770To771(to770.toClient().get(0));
    }

    /**
     * 内部767→771クライアントのプレイ状態（サーバー→クライアント）。
     * 767→770→771へ二段で変換する。
     */
    private Outcome handleServerPlay767To771(MinecraftPacket packet) {
        Outcome to770 = handleServerPlay767To770(packet);
        if (to770.toClient().isEmpty()) {
            return to770;
        }
        return handleServerPlay770To771(to770.toClient().get(0));
    }

    /**
     * 内部766→771クライアントのプレイ状態（サーバー→クライアント）。
     * 766→770→771へ二段で変換する。
     */
    private Outcome handleServerPlay766To771(MinecraftPacket packet) {
        Outcome to770 = handleServerPlay766To770(packet);
        if (to770.toClient().isEmpty()) {
            return to770;
        }
        return handleServerPlay770To771(to770.toClient().get(0));
    }
'''

INSERTS = [
    # (説明, メソッド定義行, 挿入する本文)
    # 挿入位置はそのメソッドの javadoc の先頭まで遡る（javadoc が
    # 新しいメソッドに付かないようにする）。
    ("client-play 771 methods",
     "    private Outcome handleClientPlay770(MinecraftPacket packet) {",
     CLIENT_PLAY_771),
    ("server-play 771 methods",
     "    private Outcome handleServerPlayFrom770(MinecraftPacket packet) {",
     SERVER_PLAY_771),
    ("server-play 770-to-771 methods",
     "    private Outcome handleServerPlayFrom771(MinecraftPacket packet) {",
     SERVER_PLAY_771_TO),
]





# config 771 methods：handleClientConfigurationTo770 の直後（クラスの末尾）に置く。
# EDITS とは別扱いにする（CONFIG_771 が後で定義されるため）。
CONFIG_ANCHOR = """    private Outcome handleClientConfigurationTo770(MinecraftPacket packet) {
        return handleClientConfigurationTo769(packet);
    }

}"""

CONFIG_REPLACEMENT = """    private Outcome handleClientConfigurationTo770(MinecraftPacket packet) {
        return handleClientConfigurationTo769(packet);
    }
""" + CONFIG_771 + "\n}\n"


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
        print(f"NG: config 771 methods: anchor が {n} 回見つかった（1回のはず）",
              file=sys.stderr)
        sys.exit(1)
    src = src.replace(CONFIG_ANCHOR, CONFIG_REPLACEMENT, 1)
    print("OK: config 771 methods を挿入")
    PATH.write_text(src, encoding="utf-8")
    print("ProxySession.java に 771 の対応を挿入した")


if __name__ == "__main__":
    main()
