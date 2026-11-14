#!/usr/bin/env python3
"""ProxySession.java へ Java 1.21.4（プロトコル769）の対応を入れる。

769クライアント／内部769サーバーの分岐と、769↔768の変換ハンドラを追加する。
既存の768の流儀をなぞる：769→768へ一度直してから既存の768→767／766の
処理に通すため、768→767／766のホワイトリストは重複して作らない。

各アンカーはファイル中にちょうど1回だけ現れること。見つからない／
複数ある場合は何も書き込まずに終了する（冪等ではないので2回実行しないこと）。
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol" / "ProxySession.java"

EDITS = [
    ("constructor javadoc", """\
     * @param serverProtocol 内部バニラのプロトコル（763＝1.20.1、
     *                       764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、
     *                       767＝1.21／1.21.1、768＝1.21.2／1.21.3）""",
     """\
     * @param serverProtocol 内部バニラのプロトコル（763＝1.20.1、
     *                       764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、
     *                       767＝1.21／1.21.1、768＝1.21.2／1.21.3、
     *                       769＝1.21.4）"""),
    ("constructor validation", """\
                && serverProtocol != ProtocolVersion.V1_21
                && serverProtocol != ProtocolVersion.V1_21_2) {
            throw new IllegalArgumentException(
                    "serverProtocol must be 763 (1.20.1), 764 (1.20.2), "
                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1) "
                            + "or 768 (1.21.2/1.21.3): "
""",
     """\
                && serverProtocol != ProtocolVersion.V1_21
                && serverProtocol != ProtocolVersion.V1_21_2
                && serverProtocol != ProtocolVersion.V1_21_4) {
            throw new IllegalArgumentException(
                    "serverProtocol must be 763 (1.20.1), 764 (1.20.2), "
                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "
                            + "768 (1.21.2/1.21.3) or 769 (1.21.4): "
"""),
    ("serverProtocol() javadoc", """\
    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1）。 */""",
     """\
    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4）。 */"""),
    ("pack769 field", """\
    /**
     * 768クライアントに送るリソースパック（pack_format 42）。
     * 内部が768でない場合に使う差し替え用。内部が768の場合はバニラが
     * 送ってきたパックをそのまま出すため null のままでよい。
     * 出し分けの基準はクライアントのプロトコルである
     * （{@link #packOverrideForClient()} 参照）。
     */
    private ResourcePack pack768;""",
     """\
    /**
     * 768クライアントに送るリソースパック（pack_format 42）。
     * 内部が768でない場合に使う差し替え用。内部が768の場合はバニラが
     * 送ってきたパックをそのまま出すため null のままでよい。
     * 出し分けの基準はクライアントのプロトコルである
     * （{@link #packOverrideForClient()} 参照）。
     */
    private ResourcePack pack768;

    /**
     * 769クライアントに送るリソースパック（pack_format 46）。
     * 内部が769でない場合に使う差し替え用。内部が769の場合はバニラが
     * 送ってきたパックをそのまま出すため null のままでよい。
     * 出し分けの基準はクライアントのプロトコルである
     * （{@link #packOverrideForClient()} 参照）。
     */
    private ResourcePack pack769;"""),
    ("setPack769/pack769", """\
    /** 768クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack768() {
        return pack768;
    }""",
     """\
    /** 768クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack768() {
        return pack768;
    }

    /** 769クライアントに送るリソースパックを設定する（内部769でない場合に使う）。 */
    public void setPack769(ResourcePack pack769) {
        this.pack769 = pack769;
    }

    /** 769クライアントに送るリソースパック（無ければ null）。 */
    public ResourcePack pack769() {
        return pack769;
    }"""),
    ("packOverrideForClient javadoc", """\
     * 出し分けの基準はクライアントのプロトコルである：768→pack768
     * （pack_format 42）、767→pack767""",
     """\
     * 出し分けの基準はクライアントのプロトコルである：769→pack769
     * （pack_format 46）、768→pack768
     * （pack_format 42）、767→pack767"""),
    ("packOverrideForClient branch", """\
        if (clientProtocol == ProtocolVersion.V1_21_2) {
            return pack768 != null ? pack768
                    : pack767 != null ? pack767 : pack766;
        }""",
     """\
        if (clientProtocol == ProtocolVersion.V1_21_4) {
            return pack769 != null ? pack769
                    : pack768 != null ? pack768
                    : pack767 != null ? pack767 : pack766;
        }
        if (clientProtocol == ProtocolVersion.V1_21_2) {
            return pack768 != null ? pack768
                    : pack767 != null ? pack767 : pack766;
        }"""),
    ("is769/isServer769", """\
    /** 内部が 1.21.2／1.21.3（768）か。 */
    private boolean isServer768() {
        return serverProtocol == ProtocolVersion.V1_21_2;
    }""",
     """\
    /** 内部が 1.21.2／1.21.3（768）か。 */
    private boolean isServer768() {
        return serverProtocol == ProtocolVersion.V1_21_2;
    }

    /** クライアントが 1.21.4（769）か。 */
    private boolean is769() {
        return clientProtocol == ProtocolVersion.V1_21_4;
    }

    /** 内部が 1.21.4（769）か。 */
    private boolean isServer769() {
        return serverProtocol == ProtocolVersion.V1_21_4;
    }"""),
    ("login client list", """\
                if (is764() || is765() || is766() || is767() || is768()) {""",
     """\
                if (is764() || is765() || is766() || is767() || is768() || is769()) {"""),
    ("login comment", """\
                    // 764／765／766／767クライアント：クライアントもバニラもこれから
                    // 設定フェーズに入る。内部765＋764クライアントと、""",
     """\
                    // 764／765／766／767／768／769クライアント：クライアントもバニラもこれから
                    // 設定フェーズに入る。内部765＋764クライアントと、"""),
    ("login 763-branch comment", """\
                // （Login Acknowledged は空、Client Information は
                // 3バージョンで同じフィールド）である。768は Client
                // Information の末尾に particleStatus があるため、
                // 付け足して送る（付けないと768のバニラが切断する）。""",
     """\
                // （Login Acknowledged は空、Client Information は
                // 3バージョンで同じフィールド）である。768／769は Client
                // Information の末尾に particleStatus があるため、
                // 付け足して送る（付けないと768／769のバニラが切断する）。"""),
    ("login clientInformation", """\
                byte[] clientInformation = isServer768()
                        ? PlayTranslator.translateClientInformation767To768(""",
     """\
                byte[] clientInformation = isServer768() || isServer769()
                        ? PlayTranslator.translateClientInformation767To768("""),
    ("synthesized config registry loop", """\
        if (is766() || is767() || is768()) {
            // 766／767／768：レジストリごとに1パケット（エントリ配列）。
            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {
                toClient.add(new MinecraftPacket(0x07, registryPayload));
            }""",
     """\
        if (is766() || is767() || is768() || is769()) {
            // 766／767／768／769：レジストリごとに1パケット（エントリ配列）。
            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {
                if (is769()) {
                    // 769：766→767→768→769へ三段で並べ替える（pale_garden を挿入）。
                    byte[] payload766to767 =
                            PlayTranslator.translateRegistryData766To767(registryPayload);
                    byte[] payload767to768 =
                            PlayTranslator.translateRegistryData767To768(payload766to767);
                    registryPayload =
                            PlayTranslator.translateRegistryData768To769(payload767to768);
                }
                toClient.add(new MinecraftPacket(0x07, registryPayload));
            }"""),
    ("synthesized ackId", """\
        int ackId = is766() || is767() || is768() ? 0x03 : 0x02;""",
     """\
        int ackId = is766() || is767() || is768() || is769() ? 0x03 : 0x02;"""),
    ("synthesized add-pack", """\
            if (is768()) {
                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。""",
     """\
            if (is768() || is769()) {
                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK。769も同ID）。"""),
    ("startConfigurationClientId", """\
        if (is766() || is767() || is768()) {
            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,
                    serverProtocol, clientProtocol).orElse(is768() ? 0x70 : 0x69);
        }""",
     """\
        if (is766() || is767() || is768() || is769()) {
            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,
                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : 0x69);
        }"""),
    ("spawnPositionClientId", """\
        if (is766() || is767() || is768()) {
            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,
                    serverProtocol, clientProtocol).orElse(is768() ? 0x5B : 0x56);
        }""",
     """\
        if (is766() || is767() || is768() || is769()) {
            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,
                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : 0x56);
        }"""),
    ("keepAliveClientId", """\
        if (is766() || is767() || is768()) {
            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,
                    serverProtocol, clientProtocol).orElse(is768() ? 0x27 : 0x26);
        }""",
     """\
        if (is766() || is767() || is768() || is769()) {
            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,
                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x27 : 0x26);
        }"""),
    ("pingClientId", """\
        if (is766() || is767() || is768()) {
            return PacketTranslator.toClientId(PacketName.PLAY_PING,
                    serverProtocol, clientProtocol).orElse(is768() ? 0x37 : 0x35);
        }""",
     """\
        if (is766() || is767() || is768() || is769()) {
            return PacketTranslator.toClientId(PacketName.PLAY_PING,
                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : 0x35);
        }"""),
    ("handleClientPlay dispatch", """\
        // 768クライアント：番号を内部向けに付け替える。内部766／767向けは
        // スロットを768→767へ変換する。内部763／764／765向けは766クライアントと
        // 同じくデータコンポーネントのため落とす。
        if (is768()) {
            return handleClientPlay768(packet);
        }
        // 内部768：763／764／765／766／767クライアントの番号を768向けに付け替える。
        if (isServer768()) {
            return handleClientPlayTo768(packet);
        }""",
     """\
        // 769クライアント：番号を内部向けに付け替える。内部768向けは
        // 769→768へ変換してから送る。内部767／766向けは769→768→767／766へ
        // 二段で変換する。内部763／764／765向けは766クライアントと
        // 同じくデータコンポーネントのため落とす。
        if (is769()) {
            return handleClientPlay769(packet);
        }
        // 内部769：763／764／765／766／767／768クライアントの番号を769向けに付け替える。
        if (isServer769()) {
            return handleClientPlayTo769(packet);
        }
        // 768クライアント：番号を内部向けに付け替える。内部766／767向けは
        // スロットを768→767へ変換する。内部763／764／765向けは766クライアントと
        // 同じくデータコンポーネントのため落とす。
        if (is768()) {
            return handleClientPlay768(packet);
        }
        // 内部768：763／764／765／766／767クライアントの番号を768向けに付け替える。
        if (isServer768()) {
            return handleClientPlayTo768(packet);
        }"""),
    ("handleServerPlay dispatch", """\
        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、
        // Start Configuration は 0x70。
        if (isServer768()) {
            return handleServerPlayFrom768(packet);
        }""",
     """\
        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、
        // Start Configuration は 0x70（768と同じ番号）。
        if (isServer769()) {
            return handleServerPlayFrom769(packet);
        }
        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、
        // Start Configuration は 0x70。
        if (isServer768()) {
            return handleServerPlayFrom768(packet);
        }"""),
    ("handleServerConfiguration 763 branch", """\
        if (is763()) {
            if (isServer768()) {
                return handleServerConfiguration763From768(packet);
            }""",
     """\
        if (is763()) {
            if (isServer769()) {
                return handleServerConfiguration763From769(packet);
            }
            if (isServer768()) {
                return handleServerConfiguration763From768(packet);
            }"""),
    ("handleServerConfiguration 769 branch", """\
        // 768クライアントは設定フェーズを中継する（番号と Registry Data
        // の形式だけ変換する）。
        if (is768()) {""",
     """\
        // 769クライアントは設定フェーズを中継する（番号と Registry Data
        // の形式だけ変換する）。
        if (is769()) {
            if (isServer769()) {
                // 内部769＋769クライアントはネイティブのためここには来ない。
                // 念のためそのまま通す。
                return Outcome.toClient(packet);
            }
            if (isServer768()) {
                return handleServerConfiguration769From768(packet);
            }
            if (isServer767()) {
                return handleServerConfiguration769From767(packet);
            }
            if (isServer766()) {
                return handleServerConfiguration769From766(packet);
            }
            return handleServerConfiguration769(packet, isServer764());
        }
        // 768クライアントは設定フェーズを中継する（番号と Registry Data
        // の形式だけ変換する）。
        if (is768()) {"""),
    ("handleServerConfiguration 767 branch", """\
            if (isServer768()) {
                return handleServerConfiguration767From768(packet);
            }""",
     """\
            if (isServer769()) {
                return handleServerConfiguration767From769(packet);
            }
            if (isServer768()) {
                return handleServerConfiguration767From768(packet);
            }"""),
    ("handleServerConfiguration 766 branch", """\
            if (isServer768()) {
                return handleServerConfiguration766From768(packet);
            }""",
     """\
            if (isServer769()) {
                return handleServerConfiguration766From769(packet);
            }
            if (isServer768()) {
                return handleServerConfiguration766From768(packet);
            }"""),
    ("handleServerConfiguration From769 branch", """\
        // 内部768の場合は番号と Registry Data の形式を変換する。
        if (isServer768()) {
            return handleServerConfigurationFrom768(packet, is764());
        }""",
     """\
        // 内部769の場合は番号と Registry Data の形式を変換する。
        if (isServer769()) {
            return handleServerConfigurationFrom769(packet, is764());
        }
        // 内部768の場合は番号と Registry Data の形式を変換する。
        if (isServer768()) {
            return handleServerConfigurationFrom768(packet, is764());
        }"""),
    ("handleClientConfiguration 769 branch", """\
        // 768クライアント：番号を764／765向けに付け替える。
        if (is768()) {
            return handleClientConfiguration768(packet);
        }""",
     """\
        // 768クライアント：番号を764／765向けに付け替える。
        if (is768()) {
            return handleClientConfiguration768(packet);
        }
        // 769クライアント：番号を764／765向けに付け替える。設定フェーズの
        // 番号・形式は768と同じため768の処理を使い回す。
        if (is769()) {
            return handleClientConfiguration769(packet);
        }"""),
    ("handleClientConfiguration 767+769 branch", """\
        // 767クライアント＋内部768：番号を768向けに付け替える。766クライアントと
        // 同じ（設定フェーズの番号・形式は766＝767）。
        if (is767() && isServer768()) {
            return handleClientConfiguration767To768(packet);
        }""",
     """\
        // 767クライアント＋内部768：番号を768向けに付け替える。766クライアントと
        // 同じ（設定フェーズの番号・形式は766＝767）。
        if (is767() && isServer768()) {
            return handleClientConfiguration767To768(packet);
        }
        // 767クライアント＋内部769：番号を769向けに付け替える。768と
        // 同じ番号・形式のため768向けの処理を使い回す。
        if (is767() && isServer769()) {
            return handleClientConfiguration767To768(packet);
        }"""),
    ("handleClientConfiguration 766+769 branch", """\
        // 766クライアント＋内部768：番号を768向けに付け替える。
        if (is766() && isServer768()) {
            return handleClientConfiguration766To768(packet);
        }""",
     """\
        // 766クライアント＋内部768：番号を768向けに付け替える。
        if (is766() && isServer768()) {
            return handleClientConfiguration766To768(packet);
        }
        // 766クライアント＋内部769：番号を769向けに付け替える。768と
        // 同じ番号・形式のため768向けの処理を使い回す。
        if (is766() && isServer769()) {
            return handleClientConfiguration766To768(packet);
        }"""),
    ("handleClientConfiguration To769 branch", """\
        // 764／765クライアント＋内部768：番号を768向けに付け替える。
        if (isServer768()) {
            return handleClientConfigurationTo768(packet);
        }""",
     """\
        // 764／765クライアント＋内部769：番号を769向けに付け替える。768と
        // 同じ番号・形式のため768向けの処理を使い回す。
        if (isServer769()) {
            return handleClientConfigurationTo769(packet);
        }
        // 764／765クライアント＋内部768：番号を768向けに付け替える。
        if (isServer768()) {
            return handleClientConfigurationTo768(packet);
        }"""),
]

CLIENT_PLAY_769 = r'''
    /**
     * 769クライアント→内部768以下のプレイ状態（クライアント→サーバー）。
     * 769→768へ変換してから {@link #handleClientPlay768} に通す。
     * 内部768の場合は変換した768パケットをそのまま送る（768の処理は
     * 内部768への送出を想定していないため、委譲ではなく直接送る）。
     * 769だけのパケット（Pick Item From Block／Entity、Player Loaded）は
     * 768に相当が無く、Pick Item は世界の情報が無いため変換できないので
     * 落とす。Vehicle Move は末尾の onGround を落とす。
     */
    private Outcome handleClientPlay769(MinecraftPacket packet) {
        MinecraftPacket as768 = to768ClientPacket(packet);
        if (as768 == null) {
            return Outcome.drop();
        }
        if (isServer768()) {
            return Outcome.toServer(as768);
        }
        return handleClientPlay768(as768);
    }

    /**
     * 769クライアントのパケットを768形式にする。768に相当が無いものは
     * null を返し、呼び出し元が落とす（ログ済み）。
     */
    private MinecraftPacket to768ClientPacket(MinecraftPacket packet) {
        // Pick Item From Block（769：0x22）／From Entity（769：0x23）：
        // 769は位置・対象を指定するが、旧形式の Pick Item はスロット番号を
        // 指定するため、世界の情報が無いプロキシでは変換できない。
        if (packet.id() == 0x22 || packet.id() == 0x23) {
            logUntranslatedOnce("PLAY_PICK_ITEM_FROM_"
                    + (packet.id() == 0x22 ? "BLOCK" : "ENTITY"));
            return null;
        }
        // Player Loaded（769：0x2A）：読み込み完了の通知。768のサーバーが
        // 待っていないため無くても進む。
        if (packet.id() == 0x2A) {
            return null;
        }
        // Vehicle Move（769：0x20）：末尾の onGround を落として768形式にする。
        if (packet.id() == 0x20) {
            return new MinecraftPacket(0x20,
                    PlayTranslator.translateVehicleMove769To768(packet.payload()));
        }
        // Window Click（769：0x10）：スロットを769→768へ変換する。
        if (packet.id() == 0x10) {
            return new MinecraftPacket(0x10,
                    ChunkTranslator.translateWindowClick769To768(packet.payload()));
        }
        // Set Creative Slot（769：0x36→768：0x34）：スロットを変換する。
        if (packet.id() == 0x36) {
            return new MinecraftPacket(0x34,
                    ChunkTranslator.translateSetCreativeSlot769To768(packet.payload()));
        }
        // それ以外：番号だけ768向けに付け替える（ペイロードは同形式）。
        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, ProtocolVersion.V1_21_4);
        if (name.isEmpty()) {
            LOG.warning("769向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return null;
        }
        OptionalInt id768 = PacketTranslator.translateId(name.get(),
                ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_2);
        if (id768.isEmpty()) {
            LOG.warning("768に無いプレイ状態のパケット " + name.get() + " を落とします。");
            return null;
        }
        return new MinecraftPacket(id768.getAsInt(), packet.payload());
    }

    /**
     * 763／764／765／766／767／768クライアント→内部769のプレイ状態（クライアント→サーバー）。
     * 名前で分ける。番号だけの付け替えは {@link PacketTranslator#translateId} に任せ、
     * ペイロード変換が要るものだけ明示する。768クライアントは768形式のまま
     * {@link #handleClientPlay768To769} で769へ直す。767クライアントの
     * Window Click／Set Creative Slot は767→768→769へ二段で変換する
     * （766以下はデータコンポーネント形式のため落とす）。
     * Craft Recipe Request／Displayed Recipe はレシピIDが付け替えられないため落とす。
     * 旧形式の Pick Item は769に相当が無いため落とす（769は位置・対象を指定するため
     * スロット番号からは変換できない）。
     * Vehicle Move は769の末尾に onGround が付くため、旧クライアント分は
     * onGround=false を付けて送る（旧クライアントは送ってこないための近似）。
     */
    private Outcome handleClientPlayTo769(MinecraftPacket packet) {
        if (is768()) {
            return handleClientPlay768To769(packet);
        }
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, clientProtocol).orElse(null);
        if (name == null) {
            LOG.warning("769向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return Outcome.drop();
        }
        switch (name) {
            case PLAY_WINDOW_CLICK -> {
                if (is767()) {
                    byte[] payload = ChunkTranslator.translateWindowClick768To769(
                            ChunkTranslator.translateWindowClick767To768(packet.payload()));
                    return Outcome.toServer(new MinecraftPacket(0x10, payload));
                }
                logUntranslatedOnce("PLAY_WINDOW_CLICK");
                return Outcome.drop();
            }
            case PLAY_SET_CREATIVE_SLOT -> {
                if (is767()) {
                    byte[] payload = ChunkTranslator.translateSetCreativeSlot768To769(
                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload()));
                    return Outcome.toServer(new MinecraftPacket(0x36, payload));
                }
                logUntranslatedOnce("PLAY_SET_CREATIVE_SLOT");
                return Outcome.drop();
            }
            case PLAY_SET_PLAYER_POSITION, PLAY_SET_PLAYER_POSITION_AND_ROTATION,
                    PLAY_SET_PLAYER_ROTATION, PLAY_FLYING -> {
                // 768→769の移動パケットは同形式のため、767→768の変換だけでよい。
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
                        name, clientProtocol, ProtocolVersion.V1_21_4);
                if (serverId.isEmpty()) {
                    LOG.warning("769向けに変換できないプレイ状態のパケット（id=0x"
                            + Integer.toHexString(packet.id()) + "）を落とします。");
                    return Outcome.drop();
                }
                return Outcome.toServer(new MinecraftPacket(serverId.getAsInt(), payload));
            }
            case PLAY_STEER_VEHICLE -> {
                byte[] payload = PlayTranslator.translateSteerVehicleToPlayerInput768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x29, payload));
            }
            case PLAY_VEHICLE_MOVE_TO_SERVER -> {
                // 768形式（onGround 無し）に onGround=false を付けて769形式にする。
                byte[] payload = PlayTranslator.translateVehicleMove768To769(packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x20, payload));
            }
            case PLAY_CRAFT_RECIPE_REQUEST, PLAY_DISPLAYED_RECIPE -> {
                logUntranslatedOnce(name);
                return Outcome.drop();
            }
            case PLAY_PICK_ITEM -> {
                // 旧形式の Pick Item（スロット番号）は769に相当が無い。
                logUntranslatedOnce(name);
                return Outcome.drop();
            }
            case PLAY_CHAT_COMMAND -> {
                if (is763() || is764() || is765()) {
                    // 763／764／765の Chat Command は完全な形式（署名付き）のため、
                    // 769の Chat Command Signed（0x06）に送る。
                    return Outcome.toServer(new MinecraftPacket(0x06, packet.payload()));
                }
                // 766／767の符号なし Chat Command は769の 0x05（同形式）。
                return Outcome.toServer(new MinecraftPacket(0x05, packet.payload()));
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
                    return Outcome.toServer(new MinecraftPacket(0x2F, payload));
                }
                if (is764()) {
                    byte[] payload = PlayTranslator.translateResourcePackResponse764To765(
                            packet.payload(), lastSentPack);
                    return Outcome.toServer(new MinecraftPacket(0x2F, payload));
                }
                return Outcome.toServer(new MinecraftPacket(0x2F, packet.payload()));
            }
            case PLAY_CONFIGURATION_ACKNOWLEDGED -> {
                clientState = ClientState.CONFIGURATION;
                serverState = ServerState.CONFIGURATION;
                return Outcome.toServer(new MinecraftPacket(0x0E, packet.payload()));
            }
            case PLAY_CHUNK_BATCH_RECEIVED -> {
                if (is763()) {
                    return Outcome.drop();
                }
                return Outcome.toServer(new MinecraftPacket(0x09, packet.payload()));
            }
            case PLAY_COOKIE_RESPONSE -> {
                return Outcome.drop();
            }
            case PLAY_ENCHANT_ITEM -> {
                byte[] payload = PlayTranslator.translateEnchantItem767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x0F, payload));
            }
            case PLAY_CLOSE_WINDOW_TO_SERVER -> {
                byte[] payload = PlayTranslator.translateCloseWindow767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x11, payload));
            }
            case PLAY_CLIENT_INFORMATION -> {
                // Settings：旧クライアントの 8 フィールド形式に particleStatus=0 を付けて
                // 769 形式にする。付けないと 769 サーバーが読み取り途中で切断する。
                byte[] payload = PlayTranslator.translateClientInformation767To768(
                        packet.payload());
                return Outcome.toServer(new MinecraftPacket(0x0C, payload));
            }
            default -> {
                OptionalInt serverId = PacketTranslator.translateId(
                        name, clientProtocol, ProtocolVersion.V1_21_4);
                if (serverId.isPresent()) {
                    return Outcome.toServer(new MinecraftPacket(serverId.getAsInt(),
                            packet.payload()));
                }
                LOG.warning("769向けに変換できないプレイ状態のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                return Outcome.drop();
            }
        }
    }

    /**
     * 768クライアント→内部769のプレイ状態（クライアント→サーバー）。
     * 768→769へ変換してそのまま送る。768は既にデータコンポーネント形式の
     * ため、767向けの変換（particleStatus 付け足し等）は要らない。
     */
    private Outcome handleClientPlay768To769(MinecraftPacket packet) {
        MinecraftPacket as769 = to769ClientPacket(packet);
        if (as769 == null) {
            return Outcome.drop();
        }
        return Outcome.toServer(as769);
    }

    /**
     * 768クライアントのパケットを769形式にする。769に相当が無いものは
     * null を返し、呼び出し元が落とす（ログ済み）。
     */
    private MinecraftPacket to769ClientPacket(MinecraftPacket packet) {
        // Pick Item（768：0x22）：769には位置・対象指定の Pick Item From
        // Block／Entity しか無いため、スロット番号からは変換できない。
        if (packet.id() == 0x22) {
            logUntranslatedOnce("PLAY_PICK_ITEM");
            return null;
        }
        // Vehicle Move（768：0x20）：末尾に onGround=false を付けて769形式にする。
        if (packet.id() == 0x20) {
            return new MinecraftPacket(0x20,
                    PlayTranslator.translateVehicleMove768To769(packet.payload()));
        }
        // Window Click（768：0x10）：スロットを768→769へ変換する。
        if (packet.id() == 0x10) {
            return new MinecraftPacket(0x10,
                    ChunkTranslator.translateWindowClick768To769(packet.payload()));
        }
        // Set Creative Slot（768：0x34→769：0x36）：スロットを変換する。
        if (packet.id() == 0x34) {
            return new MinecraftPacket(0x36,
                    ChunkTranslator.translateSetCreativeSlot768To769(packet.payload()));
        }
        // それ以外：番号だけ769向けに付け替える（ペイロードは同形式）。
        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.SERVERBOUND, ProtocolVersion.V1_21_2);
        if (name.isEmpty()) {
            LOG.warning("769向けに変換できないプレイ状態のパケット（id=0x"
                    + Integer.toHexString(packet.id()) + "）を落とします。");
            return null;
        }
        OptionalInt id769 = PacketTranslator.translateId(name.get(),
                ProtocolVersion.V1_21_2, ProtocolVersion.V1_21_4);
        if (id769.isEmpty()) {
            LOG.warning("769に無いプレイ状態のパケット " + name.get() + " を落とします。");
            return null;
        }
        return new MinecraftPacket(id769.getAsInt(), packet.payload());
    }
'''

SERVER_PLAY_769 = r'''
    /**
     * 内部769→クライアントのプレイ状態（サーバー→クライアント）。
     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／
     * Start Configuration は768と同ID・同形式のため、768の処理を使い回す。
     * ただし768クライアントへの送出だけは769→768へ直すため、
     * Join Game／Respawn／Disconnect には768向けの分岐がある。
     * それ以外は769→768へ変換してから、768クライアントにはそのまま送り、
     * 767／766クライアントには既存の768→767／766の処理に通す。
     * 763／764／765クライアントは join レベルのみ（768と同じ）。
     */
    private Outcome handleServerPlayFrom769(MinecraftPacket packet) {
        // Start Configuration（769：0x70）。763には設定フェーズもこのパケットも無い。
        if (packet.id() == 0x70) {
            if (is763()) {
                serverState = ServerState.CONFIGURATION;
                reconfiguring = true;
                LOG.info(() -> "プレイ中の Start Configuration を受けました（" + playerName
                        + "）。サーバー側の設定フェーズを肩代わりします。");
                return Outcome.drop();
            }
            int outId = PacketTranslator.translateId(
                    PacketName.PLAY_START_CONFIGURATION, 769, clientProtocol)
                    .orElse(is764() ? 0x65 : 0x67);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Join Game（769：0x2C）。
        if (packet.id() == 0x2C) {
            return handleServerJoinGame769(packet);
        }
        // Spawn Position（769：0x5B）。
        if (packet.id() == 0x5B) {
            advanceFlowSafely(() -> joinFlow.spawned());
            int outId = PacketTranslator.translateId(
                    PacketName.PLAY_SPAWN_POSITION, 769, clientProtocol)
                    .orElse(is764() ? 0x52 : 0x54);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Keep Alive（769：0x27）／Ping（769：0x37）は接続維持のため中継する。
        if (packet.id() == 0x27) {
            int outId = PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,
                    serverProtocol, clientProtocol)
                    .orElse(is763() ? 0x23 : 0x24);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        if (packet.id() == 0x37) {
            int outId = PacketTranslator.toClientId(PacketName.PLAY_PING,
                    serverProtocol, clientProtocol)
                    .orElse(is763() ? 0x32 : 0x33);
            return Outcome.toClient(new MinecraftPacket(outId, packet.payload()));
        }
        // Disconnect（769：0x1D、NBT）。
        if (packet.id() == 0x1D) {
            closed = true;
            if (is763() || is764()) {
                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());
                int outId = is763() ? 0x1A : 0x1B;
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(outId, payload)), false, true);
            }
            if (is766() || is767() || is768()) {
                // 766／767／768クライアント：Disconnect は 0x1D（NBT）のまま。
                return new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);
            }
            return new Outcome(List.of(),
                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);
        }
        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。
        if (packet.id() == 0x4C) {
            if (is768()) {
                // 768クライアント：Respawn は 0x4C（同形式）のまま。
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
        // 768クライアント：769→768へ変換する。
        if (is768()) {
            return handleServerPlay769To768(packet);
        }
        // 767クライアント：769→768へ変換してから768→767へ。
        if (is767()) {
            MinecraftPacket as768 = to768ServerPacket(packet);
            if (as768 == null) {
                return Outcome.drop();
            }
            return handleServerPlay768To767(as768);
        }
        // 766クライアント：769→768へ変換してから768→766へ。
        if (is766()) {
            MinecraftPacket as768 = to768ServerPacket(packet);
            if (as768 == null) {
                return Outcome.drop();
            }
            return handleServerPlay768To766(as768);
        }
        // それ以外は落とす（763／764／765クライアントは join レベルのみ）。
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, 769).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
        } else {
            logUntranslatedOnce(name);
        }
        return Outcome.drop();
    }

    /**
     * Join Game（内部769：0x2C）のクライアント向け変換。
     * 768と同形式のため、768クライアントにはそのまま送る。
     * それ以外は {@link #handleServerJoinGame768} と同じ方針で各クライアントへ送る。
     */
    private Outcome handleServerJoinGame769(MinecraftPacket packet) {
        if (is768()) {
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x2C, packet.payload()));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4B,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }
        return handleServerJoinGame768(packet);
    }

    /**
     * 769サーバーのプレイ状態パケットを768形式にする。768に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 768クライアントへの送出と、767／766クライアントへの二段変換の
     * 第一段で共用する。
     */
    private MinecraftPacket to768ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_4).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata769To768(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot769To768(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem769To768(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory769To768(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems769To768(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown769To768(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment769To768(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList769To768(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow769To768(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction769To768(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity769To768(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect769To768(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect769To768(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound769To768(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles769To768(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect769To768(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect769To768(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent769To768(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes769To768(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData769To768(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange769To768(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange769To768(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData769To768(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo769To768(packet.payload());
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem769To768(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion769To768(packet.payload());
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id768 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_2);
        if (id768.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id768.getAsInt(), translated);
    }

    /**
     * 内部769→768クライアントのプレイ状態（サーバー→クライアント）。
     * 769→768へ変換してそのまま送る。
     */
    private Outcome handleServerPlay769To768(MinecraftPacket packet) {
        MinecraftPacket as768 = to768ServerPacket(packet);
        if (as768 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as768);
    }
'''

CONFIG_769 = r'''
    /**
     * 769クライアント＋内部768：設定フェーズを中継する。
     * 768と769の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ768→769へ並べ替え（pale_garden を挿入）、
     * Tags はレジストリ番号がずれるため落とす。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration769From768(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（768は NBT。769も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：768のエントリ配列を769の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData768To769(packet.payload())));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を769向けに落とします（" + playerName + "）。");
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
     * 769クライアント＋内部767：設定フェーズを中継する。
     * 767と769の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ767→768→769へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration769From767(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（767は NBT。769も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：767のエントリ配列を769の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload767to768 =
                        PlayTranslator.translateRegistryData767To768(packet.payload());
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData768To769(payload767to768)));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を769向けに落とします（" + playerName + "）。");
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
     * 769クライアント＋内部766：設定フェーズを中継する。
     * 766と769の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ766→767→768→769へ三段で並べ替え（jukebox_song は
     * 766に無いため、766側の変換で落とされることは無い）、Tags はレジストリ番号が
     * ずれるため落とし、Custom Report Details（0x0F）／Server Links（0x10）は
     * 766に無いため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration769From766(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(packet); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（766は NBT。769も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：766のエントリ配列を769の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload766to767 =
                        PlayTranslator.translateRegistryData766To767(packet.payload());
                byte[] payload767to768 =
                        PlayTranslator.translateRegistryData767To768(payload766to767);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData768To769(payload767to768)));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を769向けに落とします（" + playerName + "）。");
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
     * 769クライアント＋内部764／765：設定フェーズを中継する。
     * {@link #handleServerConfiguration768} と同じ番号の付け替えで、
     * Registry Data だけ764／765の codec を769のエントリ配列に変換する
     * （765→767→768→769へ三段）。
     *
     * @param from764 サーバーが764の場合 true。false の場合は765
     */
    private Outcome handleServerConfiguration769(MinecraftPacket packet, boolean from764) {
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
            case 0x05 -> { // Registry Data：764／765の codec を769のエントリ配列に変換する
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                List<MinecraftPacket> toClient = new ArrayList<>();
                for (byte[] registryPayload
                        : PlayTranslator.translateRegistryData765To767(packet.payload())) {
                    byte[] payload767to768 =
                            PlayTranslator.translateRegistryData767To768(registryPayload);
                    toClient.add(new MinecraftPacket(0x07,
                            PlayTranslator.translateRegistryData768To769(payload767to768)));
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
                    LOG.info(() -> "レジストリ番号がずれるため Tags を769向けに落とします（" + playerName + "）。");
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x0C, packet.payload()));
            }
            case 0x09 -> { // 765の Tags：レジストリ番号がずれるため落とす（764には無い番号）
                LOG.info(() -> "レジストリ番号がずれるため Tags を769向けに落とします（" + playerName + "）。");
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
     * 763クライアント＋内部769：設定フェーズをプロキシが769クライアント役
     * として消費する。769の設定フェーズ：0x00 Cookie Request、
     * 0x01 Custom Payload、0x02 Disconnect（NBT）、0x03 Finish Configuration、
     * 0x04 Keep Alive、0x05 Ping、0x06 Reset Chat、0x07 Registry Data
     * （エントリ配列）、0x08 Remove Resource Pack、0x09 Add Resource Pack
     * （UUID付き）、0x0A Store Cookie、0x0B Transfer、0x0C Feature Flags、
     * 0x0D Tags、0x0E Select Known Packs、0x0F Custom Report Details、
     * 0x10 Server Links。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す（バニラが待ち受けない
     * ようにする）。Transfer は763クライアントに送る先が無いため落とす。
     * Join Game は769形式で来るので、763向けには
     * {@link #handleServerJoinGame769} で変換する。
     * Registry Data は763クライアント向けの Join Game codec を作るために
     * 768形式に直してから蓄える（769のままではバイオーム plains の番号が
     * ずれるため）。
     */
    private Outcome handleServerConfiguration763From769(MinecraftPacket packet) {
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
            case 0x07 -> { // Registry Data（769はエントリ配列。parseRegistryData が読む）
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 763向けの辞書は768形式で蓄える（769のままでは
                // バイオーム plains の番号がずれる）。
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_2)));
                yield Outcome.drop();
            }
            case 0x08 -> Outcome.drop(); // Remove Resource Pack（763に相当無し）
            case 0x09 -> { // Add Resource Pack（769：UUID付き）：保持して Join Game の後に送る
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
                LOG.warning("769の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 764／765クライアント＋内部769：設定フェーズを中継する。
     * {@link #handleServerConfigurationFrom768} と同じ番号の付け替えで、
     * Registry Data だけ769→768→767→765へ三段で変換する（jukebox_song は落とす）。
     * 763向けの辞書には768形式で蓄える。
     *
     * @param to764 クライアントが764の場合 true。false の場合は765クライアント
     */
    private Outcome handleServerConfigurationFrom769(MinecraftPacket packet, boolean to764) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x00, packet.payload())); // Custom Payload → Plugin Message
            case 0x02 -> { // Disconnect（769は NBT。764は JSON 文字列）
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
            case 0x07 -> { // Registry Data：769のエントリ配列を764／765の codec に変換する
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_2)));
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(packet.payload());
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
            case 0x09 -> { // Add Resource Pack（769：UUID付き）
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
                LOG.warning("769の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 767クライアント＋内部769：設定フェーズを中継する。
     * 767と769の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ769→768→767へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration767From769(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（769は NBT。767も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：769のエントリ配列を767の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(packet.payload());
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を767向けに落とします（" + playerName + "）。");
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
     * 766クライアント＋内部769：設定フェーズを中継する。
     * 766と769の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ769→768→767→766へ三段で並べ替え（jukebox_song は落とす）、
     * Tags はレジストリ番号がずれるため落とし、Custom Report Details
     * （0x0F）／Server Links（0x10）は766に無いため落とす。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration766From769(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(packet); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（769は NBT。766も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：769のエントリ配列を766の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(packet.payload());
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を766向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F, 0x10 -> // Custom Report Details／Server Links（766に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("769の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアントの設定フェーズ（クライアント→バニラ）。
     * 769と768の設定フェーズの番号・形式は同じため、
     * {@link #handleClientConfiguration768} を使い回す。
     */
    private Outcome handleClientConfiguration769(MinecraftPacket packet) {
        return handleClientConfiguration768(packet);
    }

    /**
     * 764／765クライアント＋内部769の設定フェーズ（クライアント→バニラ）。
     * 769の番号は768と同じため {@link #handleClientConfigurationTo768} と同じ。
     */
    private Outcome handleClientConfigurationTo769(MinecraftPacket packet) {
        return handleClientConfigurationTo768(packet);
    }
'''

INSERTS = [
    # (説明, メソッド定義行, 挿入する本文)
    # 挿入位置はそのメソッドの javadoc の先頭まで遡る（javadoc が
    # 新しいメソッドに付かないようにする）。
    ("client-play 769 methods",
     "    private Outcome handleClientPlay768(MinecraftPacket packet) {",
     CLIENT_PLAY_769),
    ("server-play 769 methods",
     "    private Outcome handleServerPlayFrom768(MinecraftPacket packet) {",
     SERVER_PLAY_769),
]

# config 769 methods：handleClientConfigurationTo768 の直後（クラスの末尾）に置く。
# EDITS とは別扱いにする（CONFIG_769 が後で定義されるため）。
CONFIG_ANCHOR = """    private Outcome handleClientConfigurationTo768(MinecraftPacket packet) {
        if (packet.id() == 0x00) { // Settings
            return Outcome.toServer(new MinecraftPacket(0x00,
                    PlayTranslator.translateClientInformation767To768(packet.payload())));
        }
        return handleClientConfigurationTo766(packet);
    }

}"""

CONFIG_REPLACEMENT = """    private Outcome handleClientConfigurationTo768(MinecraftPacket packet) {
        if (packet.id() == 0x00) { // Settings
            return Outcome.toServer(new MinecraftPacket(0x00,
                    PlayTranslator.translateClientInformation767To768(packet.payload())));
        }
        return handleClientConfigurationTo766(packet);
    }
""" + CONFIG_769 + "\n}\n"


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
        print(f"NG: config 769 methods: anchor が {n} 回見つかった（1回のはず）",
              file=sys.stderr)
        sys.exit(1)
    src = src.replace(CONFIG_ANCHOR, CONFIG_REPLACEMENT, 1)
    print("OK: config 769 methods を挿入")
    PATH.write_text(src, encoding="utf-8")
    print("ProxySession.java に 769 の対応を挿入した")


if __name__ == "__main__":
    main()
