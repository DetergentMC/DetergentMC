package io.detergentmc.bedrock;

import io.detergentmc.bedrock.BedrockBatch.GamePacket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Bedrock クライアント 1 接続分の状態機械。
 *
 * <p>流れ（minecraft-data Bedrock 1.20.40「Login Sequence」）：
 * <ol>
 *   <li>RakNet 接続完了（New Incoming Connection）→ログイン待ち。</li>
 *   <li>Login パケット → JWT チェーンから displayName / XUID を抜き、
 *       {@link FloodgateId} で Java の UUID・名前にして
 *       {@link JavaBackend}（内部バニラへの Java クライアント）を開く。
 *       同時に Play Status（login_success）と Resource Packs Info（空）を送る。
 *       暗号化のハンドシェイクは送らない（暗号化なしで進む）。</li>
 *   <li>Resource Pack Client Response：have all packs（3）→ Resource Pack
 *       Stack（空）を送る。completed（4）→ Start Game＋Play Status
 *       （player_spawn）を送る（Java 側の Join Game がまだなら、Join Game
 *       を待ってから送る）。</li>
 *   <li>プレイ：Bedrock の Text / Move Player / Player Auth Input /
 *       Command Request を Java 側へ翻訳し、Java 側のチャット・
 *       テレポートを Bedrock へ送り返す。チャンクは Java 側から届いた
 *       ものだけを翻訳して送る（{@link BedrockChunkTranslator}）。
 *       RequestChunkRadius に応答し、スポーン周辺から近い順に送る。
 *       遠いチャンクは送らない（Unload パケットは送らない）。</li>
 * </ol>
 *
 * <p>UDP の送受信そのものは {@link Transport}（BedrockServer が実装する）に
 * 任せ、このクラスはバイト列の処理だけをする。テストでは偽の Transport と
 * 偽の JavaBackend に差し替えられる。
 */
public final class BedrockSession implements JavaBackendConnection.Listener {

    private static final Logger LOG = Logger.getLogger(BedrockSession.class.getName());

    /** StartGame に送る正規ブロックパレット（読めなければ null＝空で送る）。 */
    private static final BedrockBlockPalette BLOCK_PALETTE = loadBlockPalette();

    private static BedrockBlockPalette loadBlockPalette() {
        try {
            return BedrockBlockPalette.load();
        } catch (IOException e) {
            LOG.warning("Bedrock 正規パレットが読めませんでした（StartGame は空のパレットで送ります）: "
                    + e.getMessage());
            return null;
        }
    }

    /** UDP への送り口。 */
    public interface Transport {
        /** クライアントへデータグラムを送る。 */
        void send(byte[] datagram) throws IOException;

        /** セッションが閉じたことをサーバーへ知らせる。 */
        void onSessionClosed(BedrockSession session);
    }

    /** Java バックエンドの作り口（テストで差し替えられる）。 */
    public interface JavaBackendFactory {
        JavaBackend create(String javaName, UUID javaUuid,
                JavaBackendConnection.Listener listener);
    }

    /** セッションの状態。 */
    public enum State {
        /** RakNet 接続中（New Incoming Connection 待ち）。 */
        RAKNET_HANDSHAKE,
        /** ログイン待ち。 */
        WAIT_LOGIN,
        /** ログイン済み（リソースパックのやり取り中）。 */
        LOGGED_IN,
        /** プレイ中（Start Game 送信済み）。 */
        PLAYING,
        /** 閉じた。 */
        CLOSED
    }

    private final InetSocketAddress remoteAddress;
    private final long clientGuid;
    private final long serverGuid;
    private final int mtu;
    private final Transport transport;
    private final JavaBackendFactory backendFactory;
    private final String worldName;
    private final String floodgatePrefix;
    /** Bedrock へ送るチャンクの半径（チャンク単位）。 */
    private final int chunkRadius;
    /** Java バックエンドのプロトコル番号（チャンクの正規化用）。 */
    private final int serverProtocol;
    /** このクライアントの Bedrock バージョン（Login で決まる。決まる前は 1.20.50）。 */
    private volatile BedrockVersion bedrockVersion = BedrockVersion.V1_20_50;

    /** このクライアントの Bedrock バージョン。 */
    BedrockVersion bedrockVersion() {
        return bedrockVersion;
    }

    /** このクライアントの Bedrock プロトコル番号。 */
    int bedrockProtocol() {
        return bedrockVersion.protocol();
    }

    private volatile State state = State.RAKNET_HANDSHAKE;
    private volatile JavaBackend backend;
    private volatile String javaName = "";
    private volatile UUID javaUuid;
    private volatile int javaEntityId = 1;
    private volatile boolean javaJoined;
    private volatile boolean resourcePackCompleted;
    private volatile boolean startGameSent;
    private volatile double spawnX = 0.5;
    private volatile double spawnY = 64.0;
    private volatile double spawnZ = 0.5;
    private volatile boolean spawnKnown;

    /** プレイヤーの現在位置（Bedrock 側の入力とテレポートで更新）。 */
    private volatile double playerX = 0.5;
    private volatile double playerY = 64.0;
    private volatile double playerZ = 0.5;
    /** プレイヤーのいるチャンク。まだ分からなければスポーンのチャンク。 */
    private volatile int playerChunkX = 0;
    private volatile int playerChunkZ = 0;
    /** クライアントが要求したチャンク半径（未要求なら -1）。 */
    private volatile int requestedChunkRadius = -1;
    /** Java 側から届いたチャンク（翻訳済み、チャンク座標→内容）。 */
    private final Map<Long, BedrockChunkTranslator.TranslatedChunk> receivedChunks =
            new HashMap<>();
    /** Bedrock へ送り済みのチャンク座標。 */
    private final Set<Long> sentChunks = new HashSet<>();
    /** 現在の次元の minY・高さ・バイオーム名（既定はオーバーワールド）。 */
    private volatile int dimensionMinY = -64;
    private volatile int dimensionHeight = 384;
    private volatile List<String> biomeNames = List.of();

    /** Java エンティティ ID→状態（Bedrock へ送った／送る予定のエンティティ）。 */
    private final Map<Integer, EntityState> entities = new HashMap<>();
    /** Player Info Update で集めた UUID→名前・ゲームモード。 */
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Map<UUID, Integer> playerGamemodes = new HashMap<>();
    /** 名前がまだ届いていない他プレイヤーの Java エンティティ ID。 */
    private final Set<Integer> pendingPlayers = new HashSet<>();
    /** PLAYING 前に届いたエンティティ向けパケット（Start Game 後に送る）。 */
    private final List<GamePacket> pendingEntityPackets = new ArrayList<>();

    /**
     * Bedrock へ送った（または送る予定の）エンティティ 1 個の状態。
     * Bedrock の runtime id は Java エンティティ ID と同じ値を使う
     * （決定的・衝突なし。Remove 時に解放、切断時に全解放）。
     */
    private static final class EntityState {
        final int javaId;
        final int type765;
        final UUID uuid;
        String name; // プレイヤーのみ
        int gamemode; // プレイヤーのみ（Java: 0 生存、1 クリエ、2 アドベン、3 観戦）
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        float headYaw;
        boolean onGround;
        /** 出現時の速度（AddEntity／AddItemEntity 用）。 */
        float velX;
        float velY;
        float velZ;
        /** Spawn Entity の objectData（絵画の向き等）。 */
        int objectData;
        float health = 20;
        float maxHealth = 20;
        /** Bedrock へ AddEntity／AddPlayer 等を送ったかどうか。 */
        boolean spawned;
        /** 蓄積した装備（Java スロット番号→中身）。 */
        final Map<Integer, BedrockEntityTranslator.JavaSlot> equipment = new HashMap<>();
        /** リードの持ち主（Java エンティティ ID。-1＝なし）。 */
        int leadHolder = -1;
        /** 乗せている乗客（Java エンティティ ID の集合）。 */
        final Set<Integer> passengers = new HashSet<>();
        /** このエンティティが乗っている乗り物（Java エンティティ ID。-1＝なし）。 */
        int vehicle = -1;

        EntityState(int javaId, int type765, UUID uuid) {
            this.javaId = javaId;
            this.type765 = type765;
            this.uuid = uuid;
        }

        boolean isPlayer() {
            return type765 == 124;
        }

        boolean isItem() {
            return type765 == 55;
        }

        boolean isPainting() {
            return type765 == 69;
        }

        /** Java のゲームモードを Bedrock の gamemode に直す（観戦は 6）。 */
        int bedrockGamemode() {
            return switch (gamemode) {
                case 1 -> 1; // クリエイティブ
                case 2 -> 2; // アドベンチャー
                case 3 -> 6; // スペクテイター
                default -> 0; // サバイバル
            };
        }
    }

    private int sendSequence;
    private int messageIndex;
    private int splitId;
    private final RakNetCodec.SplitReassembler splits = new RakNetCodec.SplitReassembler();
    private volatile boolean closed;

    public BedrockSession(InetSocketAddress remoteAddress, long clientGuid, long serverGuid,
            int mtu, Transport transport, JavaBackendFactory backendFactory, String worldName,
            String floodgatePrefix, int chunkRadius, int serverProtocol) {
        this.remoteAddress = remoteAddress;
        this.clientGuid = clientGuid;
        this.serverGuid = serverGuid;
        this.mtu = mtu;
        this.transport = transport;
        this.backendFactory = backendFactory;
        this.worldName = worldName;
        this.floodgatePrefix = floodgatePrefix == null ? "." : floodgatePrefix;
        this.chunkRadius = chunkRadius;
        this.serverProtocol = serverProtocol;
    }

    /** チャンク半径の既定値だけを使うコンストラクタ（テスト用）。 */
    public BedrockSession(InetSocketAddress remoteAddress, long clientGuid, long serverGuid,
            int mtu, Transport transport, JavaBackendFactory backendFactory, String worldName,
            String floodgatePrefix) {
        this(remoteAddress, clientGuid, serverGuid, mtu, transport, backendFactory, worldName,
                floodgatePrefix, BedrockConfig.DEFAULT_CHUNK_RADIUS, 765);
    }

    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    public long clientGuid() {
        return clientGuid;
    }

    public State state() {
        return state;
    }

    public String javaName() {
        return javaName;
    }

    public UUID javaUuid() {
        return javaUuid;
    }

    public boolean isClosed() {
        return closed;
    }

    /** フレームセット・データグラム 1 個を処理する。 */
    public void handleFrameSet(byte[] datagram) {
        if (closed) {
            return;
        }
        try {
            int sequence = RakNetCodec.frameSetSequenceNumber(datagram);
            List<byte[]> payloads = RakNetCodec.parseFrameSetPayloads(datagram, splits);
            // ACK を返す（受信確認。再送はしない）。
            if (sequence >= 0) {
                sendRaw(RakNetCodec.buildAck(sequence));
            }
            for (byte[] payload : payloads) {
                handlePayload(payload);
            }
        } catch (RuntimeException | IOException e) {
            LOG.warning("Bedrock セッション（" + remoteAddress + "）の処理に失敗しました: "
                    + e.getMessage());
        }
    }

    /** フレームの中身（RakNet 制御パケットかゲームバッチ）1 個を処理する。 */
    void handlePayload(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            return;
        }
        int id = payload[0] & 0xFF;
        switch (id) {
            case BedrockProtocol.ID_CONNECTED_PING -> {
                // Connected Ping：時刻（i64 BE）を読んで Pong を返す。
                if (payload.length >= 9) {
                    long pingTime = new BedrockVarInt.ByteReader(payload, 1).readBigLong();
                    sendFrame(RakNetCodec.buildConnectedPong(pingTime, System.currentTimeMillis()),
                            RakNetCodec.RELIABILITY_UNRELIABLE);
                }
            }
            case BedrockProtocol.ID_CONNECTED_PONG -> { /* 応答。何もしない。 */ }
            case BedrockProtocol.ID_CONNECTION_REQUEST -> {
                // Connection Request：GUID＋時刻＋セキュリティ有無。
                if (payload.length >= 18) {
                    BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(payload, 1);
                    long guid = in.readBigLong();
                    long pingTime = in.readBigLong();
                    sendFrame(RakNetCodec.buildConnectionRequestAccepted(
                                    remoteAddress, pingTime, System.currentTimeMillis()),
                            RakNetCodec.RELIABILITY_RELIABLE);
                    LOG.fine("Bedrock 接続要求（GUID=" + guid + "）を受け付けました。");
                }
            }
            case BedrockProtocol.ID_NEW_INCOMING_CONNECTION -> {
                state = State.WAIT_LOGIN;
                LOG.fine("Bedrock RakNet 接続が確立しました: " + remoteAddress);
            }
            case BedrockProtocol.ID_DISCONNECTION_NOTIFICATION -> close();
            case BedrockProtocol.ID_BATCH -> {
                try {
                    for (GamePacket packet : BedrockBatch.decode(payload)) {
                        handleGamePacket(packet.id(), packet.payload());
                    }
                } catch (IOException e) {
                    LOG.warning("Bedrock バッチの展開に失敗しました: " + e.getMessage());
                }
            }
            default -> { /* 知らない RakNet 制御パケットは無視する。 */ }
        }
    }

    /** ゲームパケット 1 個を処理する。 */
    void handleGamePacket(int id, byte[] payload) throws IOException {
        switch (id) {
            case BedrockProtocol.PACKET_LOGIN -> handleLogin(payload);
            case BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE -> {
                if (payload.length < 1) {
                    return;
                }
                int status = payload[0] & 0xFF;
                if (status == BedrockProtocol.RESOURCE_PACK_RESPONSE_HAVE_ALL_PACKS
                        || status == BedrockProtocol.RESOURCE_PACK_RESPONSE_SEND_PACKS) {
                    // パックは無いので Stack（空）を送る。
                    sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_STACK,
                            BedrockPackets.encodeResourcePackStack(bedrockVersion));
                } else if (status == BedrockProtocol.RESOURCE_PACK_RESPONSE_COMPLETED) {
                    resourcePackCompleted = true;
                    maybeSendStartGame();
                }
            }
            case BedrockProtocol.PACKET_TEXT -> {
                if (backend == null || !javaJoined) {
                    return;
                }
                BedrockPackets.TextMessage text = BedrockPackets.decodeText(payload);
                if (text.type() != BedrockProtocol.TEXT_TYPE_CHAT) {
                    return;
                }
                String message = text.message();
                if (message.startsWith("/")) {
                    backend.sendCommand(message.substring(1));
                } else {
                    backend.sendChat(message);
                }
            }
            case BedrockProtocol.PACKET_MOVE_PLAYER -> {
                if (backend == null || !javaJoined) {
                    return;
                }
                BedrockPackets.MovePlayer move = BedrockPackets.decodeMovePlayer(payload);
                backend.sendPosition(move.x(), move.y(), move.z(),
                        move.yaw(), move.pitch(), move.onGround());
                updatePlayerPosition(move.x(), move.y(), move.z());
            }
            case BedrockProtocol.PACKET_PLAYER_AUTH_INPUT -> {
                if (backend == null || !javaJoined) {
                    return;
                }
                BedrockPackets.PlayerAuthInput input =
                        BedrockPackets.decodePlayerAuthInput(payload);
                backend.sendPosition(input.x(), input.y(), input.z(),
                        input.yaw(), input.pitch(), true);
                updatePlayerPosition(input.x(), input.y(), input.z());
            }
            case BedrockProtocol.PACKET_COMMAND_REQUEST -> {
                if (backend == null || !javaJoined) {
                    return;
                }
                String command = BedrockPackets.decodeCommandRequest(payload);
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                backend.sendCommand(command);
            }
            case BedrockProtocol.PACKET_CLIENT_TO_SERVER_HANDSHAKE -> {
                // 暗号化なしのため、何もしない。
            }
            case BedrockProtocol.PACKET_REQUEST_CHUNK_RADIUS -> {
                if (state != State.PLAYING) {
                    return;
                }
                BedrockPackets.ChunkRadiusRequest request =
                        BedrockPackets.decodeRequestChunkRadius(payload);
                requestedChunkRadius = Math.max(0, request.radius());
                try {
                    sendChunkRadiusFlow();
                } catch (IOException e) {
                    LOG.warning("チャンク半径の応答に失敗しました: " + e.getMessage());
                }
            }
            case BedrockProtocol.PACKET_CLIENT_CACHE_STATUS -> {
                // ブロブキャッシュは使わない（常にフルチャンクで送る）。無視する。
            }
            case BedrockProtocol.PACKET_INVENTORY_TRANSACTION -> {
                // 攻撃・インタラクト（item use on entity）だけを Java へ送る。
                // インベントリ操作そのものは今回のスコープ外。
                if (backend == null || !javaJoined) {
                    return;
                }
                BedrockPackets.InventoryTransactionUse use =
                        BedrockPackets.decodeInventoryTransaction(payload, bedrockVersion);
                if (use == null) {
                    return;
                }
                if (use.actionType()
                        != BedrockProtocol.TRANSACTION_ACTION_INTERACT
                        && use.actionType() != BedrockProtocol.TRANSACTION_ACTION_ATTACK) {
                    return;
                }
                // entityRuntimeId は Java エンティティ ID（決定的に割り振っている）。
                backend.sendUseEntity((int) use.entityRuntimeId(), use.actionType(), 0);
            }
            case BedrockProtocol.PACKET_PLAYER_ACTION -> {
                // しゃがみ・ダッシュだけを Java の Entity Action へ送る。
                if (backend == null || !javaJoined) {
                    return;
                }
                BedrockPackets.PlayerAction action =
                        BedrockPackets.decodePlayerAction(payload);
                if (action == null) {
                    return;
                }
                int javaAction = switch (action.action()) {
                    case BedrockProtocol.PLAYER_ACTION_START_SNEAK -> 0;
                    case BedrockProtocol.PLAYER_ACTION_STOP_SNEAK -> 1;
                    case BedrockProtocol.PLAYER_ACTION_START_SPRINT -> 3;
                    case BedrockProtocol.PLAYER_ACTION_STOP_SPRINT -> 4;
                    default -> -1;
                };
                if (javaAction >= 0) {
                    backend.sendEntityAction(javaAction);
                }
            }
            case BedrockProtocol.PACKET_INTERACT -> {
                // Interact（0x21）は無視する：leave_vehicle はしゃがみ経由で
                // Java が処理し、攻撃・インタラクトは InventoryTransaction 経由。
            }
            case BedrockProtocol.PACKET_DISCONNECT -> close();
            default -> { /* インベントリ等は今回のスコープ外で落とす。 */ }
        }
    }

    /** Login パケットを処理し、Java バックエンドを開く。 */
    private void handleLogin(byte[] payload) throws IOException {
        if (state != State.WAIT_LOGIN && state != State.LOGGED_IN) {
            return;
        }
        BedrockLogin.LoginInfo login = BedrockLogin.parse(payload);
        if (BedrockProtocol.isNetherNetProtocol(login.protocolVersion())) {
            disconnect("Unsupported Bedrock protocol: " + login.protocolVersion()
                    + " (Bedrock 26.60+ uses NetherNet; the packet format is supported,"
                    + " but the NetherNet transport is not implemented yet)");
            return;
        }
        if (!BedrockProtocol.isSupportedProtocol(login.protocolVersion())) {
            disconnect("Unsupported Bedrock protocol: " + login.protocolVersion()
                    + " (supported: 291-2193 over RakNet; 2225 needs NetherNet)");
            return;
        }
        bedrockVersion = BedrockVersion.fromProtocol(login.protocolVersion());
        javaName = FloodgateId.javaNameFor(login, floodgatePrefix);
        javaUuid = FloodgateId.uuidFor(login);
        LOG.info("Bedrock ログイン: " + login.displayName() + " (XUID=" + login.xuid()
                + ") → Java 名 " + javaName + " / UUID " + javaUuid
                + " [" + login.verificationStatus() + "] Bedrock "
                + bedrockVersion.displayName() + "（プロトコル "
                + login.protocolVersion() + "）");
        backend = backendFactory.create(javaName, javaUuid, this);
        try {
            backend.connect();
        } catch (IOException e) {
            disconnect("Java サーバーへの接続に失敗しました。");
            return;
        }
        state = State.LOGGED_IN;
        sendGamePacket(BedrockProtocol.PACKET_PLAY_STATUS,
                BedrockPackets.encodePlayStatus(BedrockProtocol.PLAY_STATUS_LOGIN_SUCCESS));
        sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACKS_INFO,
                BedrockPackets.encodeResourcePacksInfo(bedrockVersion));
    }

    /** Start Game を送れる条件が揃ったら送る。 */
    private void maybeSendStartGame() throws IOException {
        if (startGameSent || !resourcePackCompleted || !javaJoined) {
            return;
        }
        BedrockPackets.StartGameInfo info = new BedrockPackets.StartGameInfo(
                javaEntityId,
                spawnKnown ? (float) spawnX : 0.5f,
                spawnKnown ? (float) spawnY : 64.0f,
                spawnKnown ? (float) spawnZ : 0.5f,
                worldName, bedrockVersion.displayName());
        sendGamePacket(BedrockProtocol.PACKET_START_GAME,
                BedrockPackets.encodeStartGame(info, BLOCK_PALETTE, bedrockVersion));
        sendGamePacket(BedrockProtocol.PACKET_PLAY_STATUS,
                BedrockPackets.encodePlayStatus(BedrockProtocol.PLAY_STATUS_PLAYER_SPAWN));
        startGameSent = true;
        state = State.PLAYING;
        // スポーン周辺のチャンク表示を始める（Java 側から既に届いている分を近い順に送る）。
        sendChunkRadiusFlow();
        // PLAYING 前に届いていたエンティティパケットを送る。
        List<GamePacket> pending;
        synchronized (this) {
            pending = new ArrayList<>(pendingEntityPackets);
            pendingEntityPackets.clear();
        }
        for (GamePacket packet : pending) {
            try {
                sendGamePacket(packet.id(), packet.payload());
            } catch (IOException e) {
                LOG.warning("溜めていたエンティティパケットの送信に失敗しました: " + e.getMessage());
            }
        }
    }

    /** 実効チャンク半径（クライアントの要求と設定の小さい方）。 */
    private int effectiveChunkRadius() {
        if (requestedChunkRadius < 0) {
            return chunkRadius;
        }
        return Math.min(requestedChunkRadius, chunkRadius);
    }

    /**
     * ChunkRadiusUpdated＋NetworkChunkPublisherUpdate を送り、届いている
     * チャンクのうち半径内のものを近い順に送る。クライアントが
     * ChunkRadiusUpdated を送ってきたときと、プレイ開始・移動で
     * プレイヤーのチャンクが変わったときに呼ぶ。
     */
    private synchronized void sendChunkRadiusFlow() throws IOException {
        if (state != State.PLAYING) {
            return;
        }
        int radius = effectiveChunkRadius();
        sendGamePacket(BedrockProtocol.PACKET_CHUNK_RADIUS_UPDATED,
                BedrockPackets.encodeChunkRadiusUpdated(radius));
        if (bedrockVersion.hasNetworkChunkPublisherUpdate()) {
            // 291 にはこのパケット自体が無い（313 で新規登録。
            // BE-NOTES.md §36）。
            sendGamePacket(BedrockProtocol.PACKET_NETWORK_CHUNK_PUBLISHER_UPDATE,
                    BedrockPackets.encodeNetworkChunkPublisherUpdate(
                            (int) Math.floor(playerX), (int) Math.floor(playerY),
                            (int) Math.floor(playerZ), radius * 16, bedrockVersion));
        }
        flushChunks();
    }

    /** 届いているチャンクのうち、半径内の未送信のものを近い順に送る。 */
    private synchronized void flushChunks() throws IOException {
        if (state != State.PLAYING) {
            return;
        }
        int radius = effectiveChunkRadius();
        List<BedrockChunkTranslator.TranslatedChunk> pending = new ArrayList<>();
        for (BedrockChunkTranslator.TranslatedChunk chunk : receivedChunks.values()) {
            long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
            if (sentChunks.contains(key)) {
                continue;
            }
            if (Math.abs(chunk.chunkX() - playerChunkX) > radius
                    || Math.abs(chunk.chunkZ() - playerChunkZ) > radius) {
                continue;
            }
            pending.add(chunk);
        }
        pending.sort(Comparator.comparingLong(chunk ->
                (long) (chunk.chunkX() - playerChunkX) * (chunk.chunkX() - playerChunkX)
                        + (long) (chunk.chunkZ() - playerChunkZ)
                                * (chunk.chunkZ() - playerChunkZ)));
        for (BedrockChunkTranslator.TranslatedChunk chunk : pending) {
            sendGamePacket(BedrockProtocol.PACKET_LEVEL_CHUNK,
                    BedrockPackets.encodeLevelChunk(chunk.chunkX(), chunk.chunkZ(),
                            chunk.subChunkCount(), chunk.payload(), bedrockVersion));
            sentChunks.add(chunkKey(chunk.chunkX(), chunk.chunkZ()));
        }
    }

    /** プレイヤー位置を更新し、チャンクが変わったら表示を更新する。 */
    private void updatePlayerPosition(double x, double y, double z) {
        int chunkX = (int) Math.floor(x / 16.0);
        int chunkZ = (int) Math.floor(z / 16.0);
        boolean moved = chunkX != playerChunkX || chunkZ != playerChunkZ;
        playerX = x;
        playerY = y;
        playerZ = z;
        playerChunkX = chunkX;
        playerChunkZ = chunkZ;
        if (!moved || state != State.PLAYING) {
            return;
        }
        try {
            sendChunkRadiusFlow();
        } catch (IOException e) {
            LOG.warning("移動時のチャンク送信に失敗しました: " + e.getMessage());
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** ゲームパケット 1 個をバッチにして reliable フレームで送る。 */
    void sendGamePacket(int id, byte[] payload) throws IOException {
        sendFrame(BedrockBatch.encodeOne(id, payload), RakNetCodec.RELIABILITY_RELIABLE);
    }

    /** 複数のゲームパケットを 1 個のバッチにして送る。 */
    void sendGamePackets(List<GamePacket> packets) throws IOException {
        sendFrame(BedrockBatch.encode(packets), RakNetCodec.RELIABILITY_RELIABLE);
    }

    /** ペイロードをフレームセットにして送る。大きいものは MTU で分割する。 */
    private synchronized void sendFrame(byte[] payload, int reliability) throws IOException {
        int split = splitId++ & 0xFFFF;
        List<byte[]> datagrams = RakNetCodec.buildFrameSets(
                sendSequence, reliability, messageIndex, payload, mtu, split);
        for (byte[] datagram : datagrams) {
            sendSequence = (sendSequence + 1) & 0xFFFFFF;
            messageIndex++;
            sendRaw(datagram);
        }
    }

    private void sendRaw(byte[] datagram) throws IOException {
        if (!closed) {
            transport.send(datagram);
        }
    }

    /** Bedrock クライアントへ切断パケットを送って閉じる。 */
    public void disconnect(String message) {
        if (closed) {
            return;
        }
        try {
            sendGamePacket(BedrockProtocol.PACKET_DISCONNECT,
                    BedrockPackets.encodeDisconnect(message));
        } catch (IOException ignored) {
            // 送れなくても閉じる。
        }
        close();
    }

    /** セッションを閉じる（Java バックエンドも閉じる）。 */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        state = State.CLOSED;
        // エンティティの状態を全解放する（ランタイム ID は Java エンティティ ID
        // と同じ値なので、解放そのものはマップを捨てるだけ）。
        synchronized (this) {
            entities.clear();
            playerNames.clear();
            playerGamemodes.clear();
            pendingPlayers.clear();
            pendingEntityPackets.clear();
        }
        if (backend != null) {
            backend.close();
            backend = null;
        }
        transport.onSessionClosed(this);
    }

    // ---- JavaBackendConnection.Listener ----

    @Override
    public void onJoined(int javaEntityId) {
        this.javaEntityId = javaEntityId;
        this.javaJoined = true;
        try {
            maybeSendStartGame();
        } catch (IOException e) {
            LOG.warning("Start Game の送信に失敗しました: " + e.getMessage());
            close();
        }
    }

    @Override
    public void onSpawnPosition(double x, double y, double z) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.spawnKnown = true;
        this.playerX = x;
        this.playerY = y;
        this.playerZ = z;
        this.playerChunkX = (int) Math.floor(x / 16.0);
        this.playerChunkZ = (int) Math.floor(z / 16.0);
    }

    @Override
    public void onChat(String sender, String message) {
        try {
            if (sender == null || sender.isEmpty()) {
                // システム文（"<Steve> hello" のように文中に名前が入っている）は
                // raw（type 0）でそのまま出す。
                BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
                out.writeByte(0); // raw
                out.writeBoolean(false);
                BedrockVarInt.writeString(out, message);
                BedrockVarInt.writeString(out, "");
                BedrockVarInt.writeString(out, "");
                sendGamePacket(BedrockProtocol.PACKET_TEXT, out.bytes());
            } else {
                sendGamePacket(BedrockProtocol.PACKET_TEXT,
                        BedrockPackets.encodeTextChat(sender, message));
            }
        } catch (IOException e) {
            LOG.warning("Bedrock へのチャット送信に失敗しました: " + e.getMessage());
        }
    }

    @Override
    public void onTeleport(double x, double y, double z, float yaw, float pitch) {
        try {
            sendGamePacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                    BedrockPackets.encodeMovePlayerTeleport(javaEntityId,
                            (float) x, (float) y, (float) z, pitch, yaw, bedrockVersion));
        } catch (IOException e) {
            LOG.warning("Bedrock へのテレポート送信に失敗しました: " + e.getMessage());
        }
        updatePlayerPosition(x, y, z);
    }

    @Override
    public void onDimensionInfo(int minY, int height, List<String> biomeNames) {
        this.dimensionMinY = minY;
        this.dimensionHeight = height;
        this.biomeNames = biomeNames == null ? List.of() : List.copyOf(biomeNames);
    }

    @Override
    public void onChunk(byte[] javaChunkPayload) {
        BedrockChunkTranslator.TranslatedChunk translated;
        try {
            translated = BedrockChunkTranslator.translate(
                    new BedrockChunkTranslator.ChunkInput(0, 0, javaChunkPayload,
                            serverProtocol, dimensionMinY, dimensionHeight, biomeNames));
        } catch (RuntimeException e) {
            LOG.warning("Java チャンクの翻訳に失敗したため落とします: " + e.getMessage());
            return;
        }
        synchronized (this) {
            receivedChunks.put(chunkKey(translated.chunkX(), translated.chunkZ()), translated);
        }
        try {
            flushChunks();
        } catch (IOException e) {
            LOG.warning("チャンクの送信に失敗しました: " + e.getMessage());
        }
    }

    @Override
    public void onBlockChange(int x, int y, int z, int stateId) {
        if (state != State.PLAYING) {
            return;
        }
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        synchronized (this) {
            if (!sentChunks.contains(chunkKey(chunkX, chunkZ))) {
                return;
            }
        }
        BedrockChunkTranslator.BlockChange change =
                BedrockChunkTranslator.translateBlockChange(stateId, serverProtocol);
        try {
            sendGamePacket(BedrockProtocol.PACKET_UPDATE_BLOCK,
                    BedrockPackets.encodeUpdateBlock(x, y, z, change.runtimeId(), 0));
            if (change.waterlogged()) {
                sendGamePacket(BedrockProtocol.PACKET_UPDATE_BLOCK,
                        BedrockPackets.encodeUpdateBlock(x, y, z,
                                BedrockChunkTranslator.WATER_RUNTIME_ID, 1));
            }
        } catch (IOException e) {
            LOG.warning("Update Block の送信に失敗しました: " + e.getMessage());
        }
    }

    @Override
    public void onDisconnected(String reason) {
        disconnect(reason == null || reason.isEmpty() ? "Disconnected." : reason);
    }

    // ---- エンティティ（JavaBackendConnection.Listener）----

    /** エンティティの状態を取る（無ければ null）。 */
    private EntityState entityState(int javaId) {
        synchronized (this) {
            return entities.get(javaId);
        }
    }

    /** エンティティ向けのパケットを送る。PLAYING 前なら溜めて Start Game 後に送る。 */
    private void sendEntityPacket(int id, byte[] payload) {
        synchronized (this) {
            if (state != State.PLAYING) {
                pendingEntityPackets.add(new GamePacket(id, payload));
                return;
            }
        }
        try {
            sendGamePacket(id, payload);
        } catch (IOException e) {
            LOG.warning("エンティティパケットの送信に失敗しました: " + e.getMessage());
        }
    }

    /** 複数のエンティティ向けパケットを送る。 */
    private void sendEntityPackets(List<GamePacket> packets) {
        for (GamePacket packet : packets) {
            sendEntityPacket(packet.id(), packet.payload());
        }
    }

    /**
     * エンティティの出現パケットを送る。プレイヤーは名前が決まってから、
     * アイテムは中身（スロット）が、絵画は題名がメタデータで届いてから
     * 送る（AddItemEntity／AddPainting は後から中身を変えられないため）。
     * 条件が揃わなければ何もせず、後で {@link #onEntityMetadata} や
     * {@link #onPlayerInfoAdd} から再度呼ぶ。
     */
    private void sendSpawn(EntityState state,
            BedrockEntityTranslator.JavaMetadata meta) {
        if (state.isPlayer()) {
            if (state.name == null) {
                synchronized (this) {
                    pendingPlayers.add(state.javaId);
                }
                return;
            }
            List<GamePacket> packets = new ArrayList<>();
            packets.add(new GamePacket(BedrockProtocol.PACKET_PLAYER_LIST,
                    BedrockPackets.encodePlayerListAdd(state.uuid, state.javaId, state.name,
                            bedrockVersion)));
            packets.add(new GamePacket(BedrockProtocol.PACKET_ADD_PLAYER,
                    BedrockPackets.encodeAddPlayer(state.uuid, state.name, state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            state.pitch, state.yaw, state.headYaw,
                            state.bedrockGamemode(),
                            BedrockEntityTranslator.defaultData(state.type765),
                            bedrockVersion)));
            state.spawned = true;
            sendEntityPackets(packets);
            return;
        }
        if (state.isItem()) {
            BedrockEntityTranslator.JavaSlot slot = meta == null ? null : meta.itemSlot();
            if (slot == null || !slot.present()) {
                return; // 中身が届くまで送らない。
            }
            state.spawned = true;
            sendEntityPacket(BedrockProtocol.PACKET_ADD_ITEM_ENTITY,
                    BedrockPackets.encodeAddItemEntity(state.javaId,
                            BedrockEntityTranslator.toBedrockItem(slot, bedrockProtocol()),
                            (float) state.x, (float) state.y, (float) state.z,
                            state.velX, state.velY, state.velZ,
                            BedrockEntityTranslator.defaultData(state.type765),
                            bedrockVersion));
            return;
        }
        if (state.isPainting()) {
            if (meta == null) {
                return; // 題名はメタデータで届くまで送らない（後から変えられないため）。
            }
            String variant = meta.paintingVariant();
            String title = variant == null
                    ? "Kebab" : BedrockEntityTranslator.paintingTitle(variant);
            state.spawned = true;
            sendEntityPacket(BedrockProtocol.PACKET_ADD_PAINTING,
                    BedrockPackets.encodeAddPainting(state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            state.objectData, title, bedrockVersion));
            return;
        }
        String type = BedrockEntityTypeIds.bedrockTypeFor(state.type765, serverProtocol);
        if (type == null) {
            // interaction／marker 等、Bedrock に出さないもの。
            return;
        }
        if (bedrockVersion.addEntityUsesLegacyTypeId()
                && BedrockLegacyEntityTypeIds.legacyIdFor(type) < 0) {
            // 291 は AddEntity のタイプが数値 ID のため、対応表に無い
            // エンティティ（1.7 より後の追加分等）は送らない
            // （BE-NOTES.md §36）。
            return;
        }
        state.spawned = true;
        sendEntityPacket(BedrockProtocol.PACKET_ADD_ENTITY,
                BedrockPackets.encodeAddEntity(state.javaId, type,
                        (float) state.x, (float) state.y, (float) state.z,
                        state.pitch, state.yaw, state.headYaw,
                        state.velX, state.velY, state.velZ,
                        state.health, state.maxHealth,
                        BedrockEntityTranslator.defaultData(state.type765),
                        bedrockVersion));
    }

    @Override
    public void onSpawnEntity(BedrockEntityTranslator.SpawnEntity spawn) {
        if (spawn.entityId() == javaEntityId) {
            return; // 自分自身はエンティティとして送らない。
        }
        EntityState state = new EntityState(spawn.entityId(), spawn.type765(), spawn.uuid());
        state.x = spawn.x();
        state.y = spawn.y();
        state.z = spawn.z();
        state.yaw = spawn.yaw();
        state.pitch = spawn.pitch();
        state.headYaw = spawn.headPitch();
        state.velX = (float) spawn.velX();
        state.velY = (float) spawn.velY();
        state.velZ = (float) spawn.velZ();
        state.objectData = spawn.objectData();
        if (state.isPlayer()) {
            synchronized (this) {
                state.name = playerNames.get(spawn.uuid());
                state.gamemode = playerGamemodes.getOrDefault(spawn.uuid(), 0);
            }
        }
        synchronized (this) {
            entities.put(state.javaId, state);
        }
        sendSpawn(state, null);
    }

    @Override
    public void onSpawnExperienceOrb(BedrockEntityTranslator.SpawnExperienceOrb orb) {
        // 経験値オーブはランタイム ID を持たない（位置＋個数だけ）。
        sendEntityPacket(BedrockProtocol.PACKET_SPAWN_EXPERIENCE_ORB,
                BedrockPackets.encodeSpawnExperienceOrb(
                        (float) orb.x(), (float) orb.y(), (float) orb.z(), orb.count()));
    }

    @Override
    public void onEntityMetadata(BedrockEntityTranslator.JavaMetadata metadata) {
        if (metadata.entityId() == javaEntityId) {
            return; // 自分自身は送らない。
        }
        EntityState state = entityState(metadata.entityId());
        if (state == null) {
            return;
        }
        if (!state.spawned) {
            // アイテム・絵画は出現を試みる。プレイヤーは名前待ち。
            if (state.isItem() || state.isPainting()) {
                sendSpawn(state, metadata);
            }
            return;
        }
        if (metadata.health() != null) {
            state.health = metadata.health();
        }
        BedrockEntityTranslator.BedrockEntityData data =
                BedrockEntityTranslator.toBedrockData(metadata, state.type765, bedrockVersion);
        if (state.leadHolder != -1) {
            data.addLong(BedrockEntityTranslator.BedrockEntityData.KEY_LEAD_HOLDER_EID,
                    state.leadHolder);
        }
        sendEntityPacket(BedrockProtocol.PACKET_SET_ENTITY_DATA,
                BedrockPackets.encodeSetEntityData(state.javaId, data, bedrockVersion));
        // 体力が変わったら属性も更新する。
        if (metadata.health() != null) {
            sendEntityPacket(BedrockProtocol.PACKET_UPDATE_ATTRIBUTES,
                    BedrockPackets.encodeUpdateAttributes(
                            state.javaId, state.health, state.maxHealth, bedrockVersion));
        }
    }

    @Override
    public void onRemoveEntities(List<Integer> entityIds) {
        for (int id : entityIds) {
            if (id == javaEntityId) {
                continue;
            }
            EntityState state;
            synchronized (this) {
                state = entities.remove(id);
                pendingPlayers.remove(id);
            }
            if (state == null) {
                continue;
            }
            // 乗客・乗り物のリンクを外す（Bedrock は乗り物の消滅で外すが、
            // 念のため明示する）。
            for (int passengerId : state.passengers) {
                sendEntityPacket(BedrockProtocol.PACKET_SET_ENTITY_LINK,
                        BedrockPackets.encodeSetEntityLink(id, passengerId,
                                BedrockProtocol.ENTITY_LINK_REMOVE));
                EntityState passenger = entityState(passengerId);
                if (passenger != null) {
                    passenger.vehicle = -1;
                }
            }
            if (state.vehicle != -1) {
                sendEntityPacket(BedrockProtocol.PACKET_SET_ENTITY_LINK,
                        BedrockPackets.encodeSetEntityLink(state.vehicle, id,
                                BedrockProtocol.ENTITY_LINK_REMOVE));
                EntityState vehicle = entityState(state.vehicle);
                if (vehicle != null) {
                    vehicle.passengers.remove(id);
                }
            }
            if (!state.spawned) {
                continue;
            }
            sendEntityPacket(BedrockProtocol.PACKET_REMOVE_ENTITY,
                    BedrockPackets.encodeRemoveEntity(id));
            if (state.isPlayer() && state.uuid != null) {
                sendEntityPacket(BedrockProtocol.PACKET_PLAYER_LIST,
                        BedrockPackets.encodePlayerListRemove(state.uuid, bedrockVersion));
            }
        }
    }

    @Override
    public void onEntityVelocity(BedrockEntityTranslator.EntityVelocity velocity) {
        EntityState state = entityState(velocity.entityId());
        if (state == null || !state.spawned) {
            return;
        }
        sendEntityPacket(BedrockProtocol.PACKET_SET_ENTITY_MOTION,
                BedrockPackets.encodeSetEntityMotion(state.javaId,
                        velocity.velX(), velocity.velY(), velocity.velZ()));
    }

    @Override
    public void onEntityTeleport(BedrockEntityTranslator.EntityTeleport teleport) {
        EntityState state = entityState(teleport.entityId());
        if (state == null) {
            return;
        }
        state.x = teleport.x();
        state.y = teleport.y();
        state.z = teleport.z();
        state.yaw = teleport.yaw();
        state.pitch = teleport.pitch();
        state.headYaw = teleport.yaw();
        state.onGround = teleport.onGround();
        if (!state.spawned) {
            return;
        }
        if (state.isPlayer()) {
            sendEntityPacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                    BedrockPackets.encodeMovePlayerEntity(state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            state.pitch, state.yaw, state.headYaw, 2, state.onGround,
                            bedrockVersion));
        } else {
            sendEntityPacket(BedrockProtocol.PACKET_MOVE_ENTITY,
                    BedrockPackets.encodeMoveEntity(state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            BedrockEntityTranslator.angleToByte(state.yaw),
                            BedrockEntityTranslator.angleToByte(state.pitch),
                            BedrockEntityTranslator.angleToByte(state.headYaw),
                            state.onGround, true));
        }
    }

    /**
     * 非プレイヤーエンティティの移動を送る。419 以降は MoveEntityDelta
     * （FloatLE 絶対座標）。419 未満の MoveEntityDelta は VarInt 差分形式で
     * 差分のスケールが実測ソースで確認できないため、絶対座標の MoveEntity
     * で代替する（MoveEntity の形式は 408 と 419 で同一。BE-NOTES.md §26）。
     * state の位置・向きは呼び出し側で更新済みのものを使う。
     */
    private void sendEntityMoveDelta(EntityState state, int flags,
            Float x, Float y, Float z, Integer rotX, Integer rotY, Integer rotZ) {
        if (bedrockVersion.moveEntityDeltaUsesAbsolutePosition()) {
            sendEntityPacket(BedrockProtocol.PACKET_MOVE_ENTITY_DELTA,
                    BedrockPackets.encodeMoveEntityDelta(state.javaId, flags,
                            x, y, z, rotX, rotY, rotZ));
        } else {
            sendEntityPacket(BedrockProtocol.PACKET_MOVE_ENTITY,
                    BedrockPackets.encodeMoveEntity(state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            BedrockEntityTranslator.angleToByte(state.yaw),
                            BedrockEntityTranslator.angleToByte(state.pitch),
                            BedrockEntityTranslator.angleToByte(state.headYaw),
                            state.onGround, false));
        }
    }

    @Override
    public void onEntityMove(BedrockEntityTranslator.EntityMove move) {
        EntityState state = entityState(move.entityId());
        if (state == null) {
            return;
        }
        state.x += move.dx();
        state.y += move.dy();
        state.z += move.dz();
        state.onGround = move.onGround();
        if (!state.spawned) {
            return;
        }
        if (state.isPlayer()) {
            sendEntityPacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                    BedrockPackets.encodeMovePlayerEntity(state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            state.pitch, state.yaw, state.headYaw, 0, state.onGround,
                            bedrockVersion));
        } else {
            int flags = BedrockProtocol.MOVE_DELTA_HAS_X | BedrockProtocol.MOVE_DELTA_HAS_Y
                    | BedrockProtocol.MOVE_DELTA_HAS_Z
                    | (state.onGround ? BedrockProtocol.MOVE_DELTA_ON_GROUND : 0);
            sendEntityMoveDelta(state, flags,
                    (float) state.x, (float) state.y, (float) state.z,
                    null, null, null);
        }
    }

    @Override
    public void onEntityMoveLook(BedrockEntityTranslator.EntityMoveLook move) {
        EntityState state = entityState(move.entityId());
        if (state == null) {
            return;
        }
        state.x += move.dx();
        state.y += move.dy();
        state.z += move.dz();
        state.yaw = move.yaw();
        state.pitch = move.pitch();
        state.headYaw = move.yaw();
        state.onGround = move.onGround();
        if (!state.spawned) {
            return;
        }
        if (state.isPlayer()) {
            sendEntityPacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                    BedrockPackets.encodeMovePlayerEntity(state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            state.pitch, state.yaw, state.headYaw, 0, state.onGround,
                            bedrockVersion));
        } else {
            int flags = BedrockProtocol.MOVE_DELTA_HAS_X | BedrockProtocol.MOVE_DELTA_HAS_Y
                    | BedrockProtocol.MOVE_DELTA_HAS_Z
                    | BedrockProtocol.MOVE_DELTA_HAS_ROT_X | BedrockProtocol.MOVE_DELTA_HAS_ROT_Y
                    | BedrockProtocol.MOVE_DELTA_HAS_ROT_Z
                    | (state.onGround ? BedrockProtocol.MOVE_DELTA_ON_GROUND : 0);
            sendEntityMoveDelta(state, flags,
                    (float) state.x, (float) state.y, (float) state.z,
                    BedrockEntityTranslator.angleToByte(state.pitch),
                    BedrockEntityTranslator.angleToByte(state.yaw),
                    BedrockEntityTranslator.angleToByte(state.headYaw));
        }
    }

    @Override
    public void onEntityLook(BedrockEntityTranslator.EntityLook look) {
        EntityState state = entityState(look.entityId());
        if (state == null) {
            return;
        }
        state.yaw = look.yaw();
        state.pitch = look.pitch();
        state.headYaw = look.yaw();
        state.onGround = look.onGround();
        if (!state.spawned) {
            return;
        }
        if (state.isPlayer()) {
            sendEntityPacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                    BedrockPackets.encodeMovePlayerEntity(state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            state.pitch, state.yaw, state.headYaw, 0, state.onGround,
                            bedrockVersion));
        } else {
            int flags = BedrockProtocol.MOVE_DELTA_HAS_ROT_X
                    | BedrockProtocol.MOVE_DELTA_HAS_ROT_Y
                    | BedrockProtocol.MOVE_DELTA_HAS_ROT_Z
                    | (state.onGround ? BedrockProtocol.MOVE_DELTA_ON_GROUND : 0);
            sendEntityMoveDelta(state, flags,
                    null, null, null,
                    BedrockEntityTranslator.angleToByte(state.pitch),
                    BedrockEntityTranslator.angleToByte(state.yaw),
                    BedrockEntityTranslator.angleToByte(state.headYaw));
        }
    }

    @Override
    public void onEntityHeadRotation(BedrockEntityTranslator.EntityHeadRotation rotation) {
        EntityState state = entityState(rotation.entityId());
        if (state == null) {
            return;
        }
        state.headYaw = rotation.headYaw();
        if (!state.spawned) {
            return;
        }
        if (state.isPlayer()) {
            sendEntityPacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                    BedrockPackets.encodeMovePlayerEntity(state.javaId,
                            (float) state.x, (float) state.y, (float) state.z,
                            state.pitch, state.yaw, state.headYaw, 0, state.onGround,
                            bedrockVersion));
        } else {
            int flags = BedrockProtocol.MOVE_DELTA_HAS_ROT_Z
                    | (state.onGround ? BedrockProtocol.MOVE_DELTA_ON_GROUND : 0);
            sendEntityMoveDelta(state, flags,
                    null, null, null, null, null,
                    BedrockEntityTranslator.angleToByte(state.headYaw));
        }
    }

    @Override
    public void onEntityEquipment(BedrockEntityTranslator.EntityEquipment equipment) {
        EntityState state = entityState(equipment.entityId());
        if (state == null) {
            return;
        }
        for (BedrockEntityTranslator.EntityEquipmentEntry entry : equipment.entries()) {
            state.equipment.put(entry.slot(), entry.item());
        }
        if (!state.spawned) {
            return;
        }
        // Java の 6（body：馬・オオカミの鎧）は Bedrock 1.21.20 以降の
        // 5 個目の body 枠に送り、それより古い Bedrock には送れないので無いものとする。
        sendEntityPacket(BedrockProtocol.PACKET_MOB_ARMOR_EQUIPMENT,
                BedrockPackets.encodeMobArmorEquipment(state.javaId,
                        BedrockEntityTranslator.toBedrockItem(state.equipment.get(5),
                                bedrockProtocol()),
                        BedrockEntityTranslator.toBedrockItem(state.equipment.get(4),
                                bedrockProtocol()),
                        BedrockEntityTranslator.toBedrockItem(state.equipment.get(3),
                                bedrockProtocol()),
                        BedrockEntityTranslator.toBedrockItem(state.equipment.get(2),
                                bedrockProtocol()),
                        BedrockEntityTranslator.toBedrockItem(state.equipment.get(6),
                                bedrockProtocol()),
                        bedrockVersion));
        sendEntityPacket(BedrockProtocol.PACKET_MOB_EQUIPMENT,
                BedrockPackets.encodeMobEquipment(state.javaId,
                        BedrockEntityTranslator.toBedrockItem(state.equipment.get(0),
                                bedrockProtocol()), 0, 0, bedrockVersion));
        sendEntityPacket(BedrockProtocol.PACKET_MOB_EQUIPMENT,
                BedrockPackets.encodeMobEquipment(state.javaId,
                        BedrockEntityTranslator.toBedrockItem(state.equipment.get(1),
                                bedrockProtocol()), 1, 0, bedrockVersion));
    }

    @Override
    public void onEntityEffect(BedrockEntityTranslator.EntityEffect effect) {
        EntityState state = entityState(effect.entityId());
        if (state == null || !state.spawned) {
            return;
        }
        // effect765 は 0 始まり、Bedrock は 1 始まり（+1）。
        sendEntityPacket(BedrockProtocol.PACKET_MOB_EFFECT,
                BedrockPackets.encodeMobEffect(state.javaId,
                        BedrockProtocol.MOB_EFFECT_ADD,
                        effect.effect765() + 1, effect.amplifier(),
                        effect.showParticles(), effect.duration()));
    }

    @Override
    public void onRemoveEntityEffect(BedrockEntityTranslator.RemoveEntityEffect effect) {
        EntityState state = entityState(effect.entityId());
        if (state == null || !state.spawned) {
            return;
        }
        sendEntityPacket(BedrockProtocol.PACKET_MOB_EFFECT,
                BedrockPackets.encodeMobEffect(state.javaId,
                        BedrockProtocol.MOB_EFFECT_REMOVE,
                        effect.effect765() + 1, 0, false, 0));
    }

    @Override
    public void onUpdateAttributes(BedrockEntityTranslator.UpdateAttributes attributes) {
        EntityState state = entityState(attributes.entityId());
        if (state == null) {
            return;
        }
        if (attributes.maxHealth() != null && attributes.maxHealth() > 0) {
            state.maxHealth = attributes.maxHealth().floatValue();
        }
        if (!state.spawned) {
            return;
        }
        sendEntityPacket(BedrockProtocol.PACKET_UPDATE_ATTRIBUTES,
                BedrockPackets.encodeUpdateAttributes(
                        state.javaId, state.health, state.maxHealth, bedrockVersion));
    }

    @Override
    public void onSetPassengers(BedrockEntityTranslator.SetPassengers passengers) {
        EntityState vehicle = entityState(passengers.vehicleId());
        if (vehicle == null) {
            return;
        }
        Set<Integer> next = new HashSet<>(passengers.passengerIds());
        // 外れた乗客。
        for (int oldId : new HashSet<>(vehicle.passengers)) {
            if (next.contains(oldId)) {
                continue;
            }
            vehicle.passengers.remove(oldId);
            EntityState rider = entityState(oldId);
            if (rider != null) {
                rider.vehicle = -1;
            }
            if (vehicle.spawned
                    && (oldId == javaEntityId || (rider != null && rider.spawned))) {
                sendEntityPacket(BedrockProtocol.PACKET_SET_ENTITY_LINK,
                        BedrockPackets.encodeSetEntityLink(vehicle.javaId, oldId,
                                BedrockProtocol.ENTITY_LINK_REMOVE));
            }
        }
        // 新しい乗客。
        for (int newId : next) {
            if (vehicle.passengers.contains(newId)) {
                continue;
            }
            vehicle.passengers.add(newId);
            EntityState rider = entityState(newId);
            if (rider != null) {
                rider.vehicle = vehicle.javaId;
            }
            if (vehicle.spawned
                    && (newId == javaEntityId || (rider != null && rider.spawned))) {
                sendEntityPacket(BedrockProtocol.PACKET_SET_ENTITY_LINK,
                        BedrockPackets.encodeSetEntityLink(vehicle.javaId, newId,
                                BedrockProtocol.ENTITY_LINK_RIDER));
            }
        }
    }

    @Override
    public void onAttachEntity(BedrockEntityTranslator.AttachEntity attach) {
        if (attach.entityId() == javaEntityId) {
            // 自分がリードで繋がれた／外れた：自分の Entity Data で送る。
            BedrockEntityTranslator.BedrockEntityData data =
                    new BedrockEntityTranslator.BedrockEntityData();
            data.addLong(BedrockEntityTranslator.BedrockEntityData.KEY_LEAD_HOLDER_EID,
                    attach.vehicleId());
            sendEntityPacket(BedrockProtocol.PACKET_SET_ENTITY_DATA,
                    BedrockPackets.encodeSetEntityData(javaEntityId, data, bedrockVersion));
            return;
        }
        EntityState state = entityState(attach.entityId());
        if (state == null) {
            return;
        }
        state.leadHolder = attach.vehicleId();
        if (!state.spawned) {
            return;
        }
        BedrockEntityTranslator.BedrockEntityData data =
                new BedrockEntityTranslator.BedrockEntityData();
        data.addLong(BedrockEntityTranslator.BedrockEntityData.KEY_LEAD_HOLDER_EID,
                attach.vehicleId());
        sendEntityPacket(BedrockProtocol.PACKET_SET_ENTITY_DATA,
                BedrockPackets.encodeSetEntityData(state.javaId, data, bedrockVersion));
    }

    @Override
    public void onPlayerInfoAdd(UUID uuid, String name, int gamemode) {
        synchronized (this) {
            playerNames.put(uuid, name);
            playerGamemodes.put(uuid, gamemode);
        }
        // 名前待ちの他プレイヤーがいたら出現させる。
        List<EntityState> flushed = new ArrayList<>();
        synchronized (this) {
            for (int id : pendingPlayers) {
                EntityState state = entities.get(id);
                if (state != null && state.isPlayer() && uuid.equals(state.uuid)) {
                    state.name = name;
                    state.gamemode = gamemode;
                    flushed.add(state);
                }
            }
            for (EntityState state : flushed) {
                pendingPlayers.remove(state.javaId);
            }
        }
        for (EntityState state : flushed) {
            sendSpawn(state, null);
        }
    }

    @Override
    public void onPlayerInfoGamemode(UUID uuid, int gamemode) {
        synchronized (this) {
            playerGamemodes.put(uuid, gamemode);
            for (EntityState state : entities.values()) {
                if (state.isPlayer() && uuid.equals(state.uuid)) {
                    state.gamemode = gamemode;
                }
            }
        }
        // Bedrock は他プレイヤーのゲームモードを後から変えられない
        // （AddPlayer 時のみ）。記録だけ更新する。
    }

    @Override
    public void onPlayerInfoRemove(List<UUID> uuids) {
        for (UUID uuid : uuids) {
            synchronized (this) {
                playerNames.remove(uuid);
                playerGamemodes.remove(uuid);
            }
            Integer targetId = null;
            synchronized (this) {
                for (EntityState state : entities.values()) {
                    if (state.isPlayer() && uuid.equals(state.uuid)) {
                        targetId = state.javaId;
                        break;
                    }
                }
            }
            if (targetId != null) {
                onRemoveEntities(List.of(targetId));
            }
        }
    }
}
