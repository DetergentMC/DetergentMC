package io.detergentmc.bedrock;

/**
 * Bedrock プロトコルの定数（対象バージョンとパケット ID）。
 *
 * <p>情報源（公開資料のみ。Geyser / Floodgate / Paper / Mojang のコードは
 * 一切読んでいないし、コピーもしていない）：
 * <ul>
 *   <li>Bedrock プロトコル番号：wiki.vg「Bedrock Protocol version numbers」
 *       （Minecraft Wiki にも同じ表がある）。<b>1.20.50 / 1.20.51 = 630</b>、
 *       1.20.40 = 622。</li>
 *   <li>パケット ID と各パケットの形式：PrismarineJS minecraft-data の
 *       Bedrock 1.20.40 プロトコル記述（packet_login 0x01、packet_play_status
 *       0x02、packet_text 0x09、packet_move_player 0x13、packet_start_game
 *       0x0b、packet_player_auth_input 0x90 など）と、Minecraft Wiki の
 *       Bedrock Login Sequence。1.20.40 と 1.20.50 でここで使う核パケットの
 *       ID は同じである（Login / Play Status / Text / Move Player /
 *       Start Game / Resource Packs Info / Stack / Client Response は
 *       1.20.x を通じて変わっていない）。</li>
 *   <li>RakNet のオフライン・ハンドシェイク：wiki.bedrock.dev「RakNet
 *       Protocol」と Minecraft Wiki「RakNet」。Unconnected Ping 0x01 /
 *       Pong 0x1c、Open Connection Request 1 0x05 / Reply 1 0x06 /
 *       Request 2 0x07 / Reply 2 0x08、Connection Request 0x09 /
 *       Request Accepted 0x10 / New Incoming Connection 0x13、
 *       Connected Ping 0x00 / Pong 0x03、Disconnect 0x15、ACK 0xc0 /
 *       NACK 0xa0、フレームセット 0x80〜0x8f。</li>
 * </ul>
 *
 * <p>対象バージョンの選択：<b>Bedrock 1.7.1〜26.52（プロトコル 291〜2193、
 * RakNet 世代）</b>を RakNet で受け入れる。プロトコル 2225（26.60〜26.65）は
 * パケット形式のみ対応し、トランスポートが NetherNet（WebRTC ベース）の
 * ため RakNet では受け入れず、切断メッセージを返す。バージョンごとの
 * 形式差分は {@link BedrockVersion} にまとめてあり、プロトコル番号と
 * 各パケットの形式は wiki.vg と PrismarineJS minecraft-data の
 * protocol.json（62 バージョン分）で 1 つずつ確認した（419・408・407・390・389・388・361・354・340・332・313・291 は
 * protocol.json が存在しないため CloudburstMC Protocol の v419・v408・
 * v407・v390・v389・v388・v361・v354・v340・v332・v313・v291 コーデックで確認した。BE-NOTES.md §25・§26・§27・§28・§29・§30・§31・§32・§33・§34・§35・§36）。Bedrock 26.60
 * （2026年10月）で RakNet は廃止され NetherNet に置き換わったため、
 * 26.60 以降の実クライアントを受け入れるには NetherNet リスナーが
 * 別途必要になる（くわしくは docs/BEDROCK.md「NetherNet対応の設計」と
 * BE-NOTES.md §24 を参照）。
 */
public final class BedrockProtocol {

    private BedrockProtocol() {
    }

    /** 対象の Bedrock バージョン名（MOTD にも出す）。 */
    public static final String TARGET_VERSION = "1.20.50";

    /** Bedrock 1.20.50 / 1.20.51 のプロトコル番号。 */
    public static final int PROTOCOL_1_20_50 = 630;

    /** Bedrock 1.20.40 のプロトコル番号。核パケットの形式は 630 と同じため受け入れる。 */
    public static final int PROTOCOL_1_20_40 = 622;

    /** NetherNet 世代のプロトコル番号（Bedrock 26.60 / 26.62 / 26.63 / 26.65）。 */
    public static final int NETHERNET_PROTOCOL = BedrockVersion.NETHERNET_PROTOCOL;

    /** この実装が受け入れる Bedrock プロトコル番号。 */
    public static boolean isSupportedProtocol(int protocol) {
        return BedrockVersion.isSupportedProtocol(protocol);
    }

    /** NetherNet 世代（26.60 / 26.62 / 26.63 / 26.65）のプロトコルか。 */
    public static boolean isNetherNetProtocol(int protocol) {
        return BedrockVersion.isNetherNetProtocol(protocol);
    }

    // ---- RakNet（UDP）----

    /** 既定の Bedrock（RakNet）ポート。 */
    public static final int DEFAULT_PORT = 19132;

    /** RakNet プロトコルバージョン（オフライン・ハンドシェイク用。11 固定）。 */
    public static final int RAKNET_PROTOCOL_VERSION = 11;

    /** RakNet の Magic（オフライン・メッセージ識別子）。 */
    public static final byte[] RAKNET_MAGIC = {
            (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00,
            (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
            (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78
    };

    public static final int ID_UNCONNECTED_PING = 0x01;
    public static final int ID_UNCONNECTED_PONG = 0x1c;
    public static final int ID_OPEN_CONNECTION_REQUEST_1 = 0x05;
    public static final int ID_OPEN_CONNECTION_REPLY_1 = 0x06;
    public static final int ID_OPEN_CONNECTION_REQUEST_2 = 0x07;
    public static final int ID_OPEN_CONNECTION_REPLY_2 = 0x08;
    public static final int ID_CONNECTED_PING = 0x00;
    public static final int ID_CONNECTED_PONG = 0x03;
    public static final int ID_CONNECTION_REQUEST = 0x09;
    public static final int ID_CONNECTION_REQUEST_ACCEPTED = 0x10;
    public static final int ID_NEW_INCOMING_CONNECTION = 0x13;
    public static final int ID_DISCONNECTION_NOTIFICATION = 0x15;
    public static final int ID_INCOMPATIBLE_PROTOCOL = 0x19;
    public static final int ID_ACK = 0xc0;
    public static final int ID_NACK = 0xa0;
    /** フレームセット（0x80〜0x8f）。実際には 0x84 を使う。 */
    public static final int ID_FRAME_SET = 0x84;

    // ---- Bedrock ゲームパケット（バッチ 0xfe の中身）----

    /** ゲームパケットのバッチ（0xfe＋zlib 圧縮されたパケット列）。 */
    public static final int ID_BATCH = 0xfe;

    public static final int PACKET_LOGIN = 0x01;
    public static final int PACKET_PLAY_STATUS = 0x02;
    public static final int PACKET_SERVER_TO_CLIENT_HANDSHAKE = 0x03;
    public static final int PACKET_CLIENT_TO_SERVER_HANDSHAKE = 0x04;
    public static final int PACKET_DISCONNECT = 0x05;
    public static final int PACKET_RESOURCE_PACKS_INFO = 0x06;
    public static final int PACKET_RESOURCE_PACK_STACK = 0x07;
    public static final int PACKET_RESOURCE_PACK_CLIENT_RESPONSE = 0x08;
    public static final int PACKET_TEXT = 0x09;
    public static final int PACKET_SET_TIME = 0x0a;
    public static final int PACKET_START_GAME = 0x0b;
    public static final int PACKET_ADD_PLAYER = 0x0c;
    /** Add Entity（0x0d）。 */
    public static final int PACKET_ADD_ENTITY = 0x0d;
    /** Remove Entity（0x0e）。 */
    public static final int PACKET_REMOVE_ENTITY = 0x0e;
    /** Add Item Entity（0x0f）。 */
    public static final int PACKET_ADD_ITEM_ENTITY = 0x0f;
    /** Move Entity（0x12）。 */
    public static final int PACKET_MOVE_ENTITY = 0x12;
    /** Entity Event（0x1b）。 */
    public static final int PACKET_ENTITY_EVENT = 0x1b;
    /** Mob Effect（0x1c）。 */
    public static final int PACKET_MOB_EFFECT = 0x1c;
    /** Update Attributes（0x1d）。 */
    public static final int PACKET_UPDATE_ATTRIBUTES = 0x1d;
    /** Inventory Transaction（0x1e）。 */
    public static final int PACKET_INVENTORY_TRANSACTION = 0x1e;
    /** Mob Equipment（0x1f）。 */
    public static final int PACKET_MOB_EQUIPMENT = 0x1f;
    /** Mob Armor Equipment（0x20）。 */
    public static final int PACKET_MOB_ARMOR_EQUIPMENT = 0x20;
    /** Interact（0x21）。 */
    public static final int PACKET_INTERACT = 0x21;
    /** Player Action（0x24）。 */
    public static final int PACKET_PLAYER_ACTION = 0x24;
    /** Set Entity Data（0x27）。 */
    public static final int PACKET_SET_ENTITY_DATA = 0x27;
    /** Set Entity Motion（0x28）。 */
    public static final int PACKET_SET_ENTITY_MOTION = 0x28;
    /** Set Entity Link（0x29）。 */
    public static final int PACKET_SET_ENTITY_LINK = 0x29;
    /** Player List（0x3f）。 */
    public static final int PACKET_PLAYER_LIST = 0x3f;
    /** Spawn Experience Orb（0x42）。 */
    public static final int PACKET_SPAWN_EXPERIENCE_ORB = 0x42;
    /** Move Entity Delta（0x6f）。 */
    public static final int PACKET_MOVE_ENTITY_DELTA = 0x6f;
    /** Add Painting（0x16）。 */
    public static final int PACKET_ADD_PAINTING = 0x16;
    public static final int PACKET_MOVE_PLAYER = 0x13;
    /** Update Block（0x15）。 */
    public static final int PACKET_UPDATE_BLOCK = 0x15;
    /** Level Chunk（0x3a）。 */
    public static final int PACKET_LEVEL_CHUNK = 0x3a;
    /** Request Chunk Radius（0x45）。 */
    public static final int PACKET_REQUEST_CHUNK_RADIUS = 0x45;
    /** Chunk Radius Updated（0x46）。 */
    public static final int PACKET_CHUNK_RADIUS_UPDATED = 0x46;
    public static final int PACKET_COMMAND_REQUEST = 0x4d;
    /** Network Chunk Publisher Update（0x79）。 */
    public static final int PACKET_NETWORK_CHUNK_PUBLISHER_UPDATE = 0x79;
    /** Client Cache Status（0x81）。 */
    public static final int PACKET_CLIENT_CACHE_STATUS = 0x81;
    /** サーバー権威移動のときクライアントが送る移動入力パケット。 */
    public static final int PACKET_PLAYER_AUTH_INPUT = 0x90;
    /** Update SubChunk Blocks（0xac）。 */
    public static final int PACKET_UPDATE_SUB_CHUNK_BLOCKS = 0xac;
    /** SubChunk（0xae）。 */
    public static final int PACKET_SUB_CHUNK = 0xae;
    /** SubChunk Request（0xaf）。 */
    public static final int PACKET_SUB_CHUNK_REQUEST = 0xaf;

    // ---- Update Block ----

    /** Update Block の flags：隣接ブロックへ通知。 */
    public static final int UPDATE_BLOCK_FLAG_NEIGHBORS = 1;
    /** Update Block の flags：ネットワークへ送る（クライアントに表示する）。 */
    public static final int UPDATE_BLOCK_FLAG_NETWORK = 2;

    // ---- Move Entity ----

    /** Move Entity の flags：bit 0＝接地、bit 1＝テレポート。 */
    public static final int MOVE_ENTITY_FLAG_ON_GROUND = 1;
    /** Move Entity の flags：bit 1＝テレポート。 */
    public static final int MOVE_ENTITY_FLAG_TELEPORT = 2;

    // ---- Move Entity Delta ----

    /** Move Entity Delta の flags（lu16 ビット）：x がある。 */
    public static final int MOVE_DELTA_HAS_X = 1;
    /** Move Entity Delta の flags：y がある。 */
    public static final int MOVE_DELTA_HAS_Y = 2;
    /** Move Entity Delta の flags：z がある。 */
    public static final int MOVE_DELTA_HAS_Z = 4;
    /** Move Entity Delta の flags：pitch がある。 */
    public static final int MOVE_DELTA_HAS_ROT_X = 8;
    /** Move Entity Delta の flags：yaw がある。 */
    public static final int MOVE_DELTA_HAS_ROT_Y = 16;
    /** Move Entity Delta の flags：head yaw がある。 */
    public static final int MOVE_DELTA_HAS_ROT_Z = 32;
    /** Move Entity Delta の flags：接地。 */
    public static final int MOVE_DELTA_ON_GROUND = 64;
    /** Move Entity Delta の flags：テレポート。 */
    public static final int MOVE_DELTA_TELEPORT = 128;

    // ---- Set Entity Link ----

    /** Set Entity Link の type：リンク解除。 */
    public static final int ENTITY_LINK_REMOVE = 0;
    /** Set Entity Link の type：騎乗（rider）。 */
    public static final int ENTITY_LINK_RIDER = 1;

    // ---- Mob Effect ----

    /** Mob Effect の event：追加。 */
    public static final int MOB_EFFECT_ADD = 1;
    /** Mob Effect の event：更新。 */
    public static final int MOB_EFFECT_UPDATE = 2;
    /** Mob Effect の event：除去。 */
    public static final int MOB_EFFECT_REMOVE = 3;

    // ---- Entity Event ----

    /** Entity Event：hurt animation（Java の Entity Status 2 に対応）。 */
    public static final int ENTITY_EVENT_HURT = 2;
    /** Entity Event：death animation（Java の Entity Status 3 に対応）。 */
    public static final int ENTITY_EVENT_DEATH = 3;

    // ---- Player Action ----

    /** Player Action：スプリント開始。 */
    public static final int PLAYER_ACTION_START_SPRINT = 9;
    /** Player Action：スプリント終了。 */
    public static final int PLAYER_ACTION_STOP_SPRINT = 10;
    /** Player Action：しゃがみ開始。 */
    public static final int PLAYER_ACTION_START_SNEAK = 11;
    /** Player Action：しゃがみ終了。 */
    public static final int PLAYER_ACTION_STOP_SNEAK = 12;

    // ---- Inventory Transaction ----

    /** Inventory Transaction の transaction_type：item use on entity。 */
    public static final int TRANSACTION_ITEM_USE_ON_ENTITY = 3;
    /** item use on entity の action_type：インタラクト。 */
    public static final int TRANSACTION_ACTION_INTERACT = 0;
    /** item use on entity の action_type：攻撃。 */
    public static final int TRANSACTION_ACTION_ATTACK = 1;

    // ---- Play Status ----

    /** ログイン成功（Play Status 0）。 */
    public static final int PLAY_STATUS_LOGIN_SUCCESS = 0;
    /** スポーン（Play Status 3）。Start Game の後に送る。 */
    public static final int PLAY_STATUS_PLAYER_SPAWN = 3;

    // ---- Text ----

    /** TextPacket の type：chat（クライアントからのチャットは常にこれ）。 */
    public static final int TEXT_TYPE_CHAT = 1;

    // ---- Resource Pack Client Response ----

    /** パックを拒否。 */
    public static final int RESOURCE_PACK_RESPONSE_REFUSED = 1;
    /** パック送信要求。 */
    public static final int RESOURCE_PACK_RESPONSE_SEND_PACKS = 2;
    /** 全パック所持。 */
    public static final int RESOURCE_PACK_RESPONSE_HAVE_ALL_PACKS = 3;
    /** 完了。 */
    public static final int RESOURCE_PACK_RESPONSE_COMPLETED = 4;
}
