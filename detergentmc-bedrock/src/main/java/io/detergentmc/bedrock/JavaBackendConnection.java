package io.detergentmc.bedrock;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIds;
import io.detergentmc.protocol.PlayTranslator;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Bedrock プレイヤーを Java 側に繋げるための、最小限の Java クライアント。
 *
 * <p>内部バニラ（127.0.0.1 のみ、offline-mode の信頼済み接続）に対して、
 * Java クライアントとして振る舞う：ハンドシェイク→ログイン→（764 以上は
 * 設定フェーズ）→プレイ。Floodgate 式の UUID・名前
 * （{@link FloodgateId}）でログインするため、Java アカウントは要らない。
 *
 * <p>Java 側のパケット ID はすべて {@link PacketIds} から引く（Java 側の
 * 対応表の二重持ちはしない）。圧縮・暗号化は扱わない：内部バニラは
 * {@code network-compression-threshold=-1} で圧縮せず、offline-mode のため
 * 暗号化の要求も送ってこない。万一 Set Compression が送られてきたら
 * 接続を切る（既存のプロキシと同じ方針）。
 *
 * <p>翻訳するのは次のものだけ（これ以外は落とすか無視する）：
 * <ul>
 *   <li>Java → Bedrock：Join Game（参加完了の合図）、Spawn Position
 *       （スポーン位置）、System Chat / Player Chat（チャット文）、
 *       Synchronize Player Position（テレポート）、Disconnect、
 *       Keep Alive（応答する）、Chunk Data・Block Change・
 *       Multi Block Change（チャンクとブロック更新。次元情報は
 *       設定フェーズの Registry Data／763 は Join Game の codec から読む）、
 *       Chunk Batch Finished への応答（Chunk Batch Received。
 *       1.21.9 ではこれを返さないと最初のバッチのあとチャンクが来ない）、
 *       エンティティ一式（Spawn Entity・Metadata・移動・テレポート・
 *       速度・頭の向き・装備・エフェクト・属性・乗客・リード・
 *       Player Info Update／Remove）。</li>
 *   <li>Bedrock → Java：Chat Message（チャット）、Chat Command
 *       （{@code /} 始まり）、Set Player Position and Rotation（移動）、
 *       Use Entity（攻撃・インタラクト）、Entity Action（しゃがみ・ダッシュ）。</li>
 * </ul>
 * インベントリは Bedrock 側に送らない（スコープ外）。
 */
public final class JavaBackendConnection implements JavaBackend {

    private static final Logger LOG = Logger.getLogger(JavaBackendConnection.class.getName());

    /** Java 側への出来事を Bedrock セッションへ伝える窓口。 */
    public interface Listener {
        /** Join Game を受け取り、プレイに入った。 */
        void onJoined(int javaEntityId);

        /** Spawn Position を受け取った。 */
        void onSpawnPosition(double x, double y, double z);

        /** 他のプレイヤー／サーバーのチャット文が届いた。 */
        void onChat(String sender, String message);

        /** サーバーが位置を同期させた（テレポート）。 */
        void onTeleport(double x, double y, double z, float yaw, float pitch);

        /** Java 側から切断された（理由つき）。 */
        void onDisconnected(String reason);

        /** Java の Chunk Data を受け取った（生ペイロードのまま渡す）。 */
        default void onChunk(byte[] javaChunkPayload) {
        }

        /** Java の Block Change を受け取った（ブロック座標と状態ID）。 */
        default void onBlockChange(int x, int y, int z, int stateId) {
        }

        /** 次元情報（minY・高さ・バイオーム名の一覧）が決まった。 */
        default void onDimensionInfo(int minY, int height, java.util.List<String> biomeNames) {
        }

        /** 設定フェーズの Registry Data が届いた（レジストリ名＋名前の順序）。 */
        default void onRegistry(String registry, java.util.List<String> names) {
        }

        // ---- エンティティ（BedrockEntityTranslator が読んだ結果）----

        /** Spawn Entity を受け取った。 */
        default void onSpawnEntity(BedrockEntityTranslator.SpawnEntity spawn) {
        }

        /** Spawn Experience Orb を受け取った。 */
        default void onSpawnExperienceOrb(BedrockEntityTranslator.SpawnExperienceOrb orb) {
        }

        /** Entity Metadata を受け取った。 */
        default void onEntityMetadata(BedrockEntityTranslator.JavaMetadata metadata) {
        }

        /** Remove Entities（Entity Destroy）を受け取った。 */
        default void onRemoveEntities(java.util.List<Integer> entityIds) {
        }

        /** Entity Velocity を受け取った。 */
        default void onEntityVelocity(BedrockEntityTranslator.EntityVelocity velocity) {
        }

        /** Entity Teleport を受け取った。 */
        default void onEntityTeleport(BedrockEntityTranslator.EntityTeleport teleport) {
        }

        /** Relative Entity Move を受け取った。 */
        default void onEntityMove(BedrockEntityTranslator.EntityMove move) {
        }

        /** Entity Move + Look を受け取った。 */
        default void onEntityMoveLook(BedrockEntityTranslator.EntityMoveLook move) {
        }

        /** Entity Look を受け取った。 */
        default void onEntityLook(BedrockEntityTranslator.EntityLook look) {
        }

        /** Entity Head Rotation を受け取った。 */
        default void onEntityHeadRotation(BedrockEntityTranslator.EntityHeadRotation rotation) {
        }

        /** Entity Equipment を受け取った。 */
        default void onEntityEquipment(BedrockEntityTranslator.EntityEquipment equipment) {
        }

        /** Entity Effect を受け取った。 */
        default void onEntityEffect(BedrockEntityTranslator.EntityEffect effect) {
        }

        /** Remove Entity Effect を受け取った。 */
        default void onRemoveEntityEffect(BedrockEntityTranslator.RemoveEntityEffect effect) {
        }

        /** Entity Update Attributes を受け取った。 */
        default void onUpdateAttributes(BedrockEntityTranslator.UpdateAttributes attributes) {
        }

        /** Set Passengers を受け取った。 */
        default void onSetPassengers(BedrockEntityTranslator.SetPassengers passengers) {
        }

        /** Attach Entity（リード）を受け取った。 */
        default void onAttachEntity(BedrockEntityTranslator.AttachEntity attach) {
        }

        /** Player Info Update の add player を受け取った（名前＋ゲームモード）。 */
        default void onPlayerInfoAdd(java.util.UUID uuid, String name, int gamemode) {
        }

        /** Player Info Update の update gamemode を受け取った。 */
        default void onPlayerInfoGamemode(java.util.UUID uuid, int gamemode) {
        }

        /** Player Info Remove を受け取った。 */
        default void onPlayerInfoRemove(java.util.List<java.util.UUID> uuids) {
        }
    }

    private final String host;
    private final int port;
    private final int serverProtocol;
    private final String javaName;
    private final UUID javaUuid;
    private final Listener listener;

    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile boolean closed;
    private volatile boolean joined;
    private Thread readerThread;
    /** Player Info Update で集めた UUID→名前の対応表（チャット送信者用）。 */
    private final Map<UUID, String> playerNames = new HashMap<>();
    /** Player Info Update で集めた UUID→ゲームモードの対応表。 */
    private final Map<UUID, Integer> playerGamemodes = new HashMap<>();
    /** 自分の Java エンティティ ID（Join Game で決まる。Use Entity 等の送信用）。 */
    private volatile int selfEntityId = -1;
    /** Spawn Entity で集めた Java エンティティ ID→765 種別（Metadata の解釈用）。 */
    private final Map<Integer, Integer> entityTypes = new HashMap<>();
    /** 次元種別の名前→[minY, height]（Registry Data／763 の codec から読む）。 */
    private final Map<String, int[]> dimensionTypes = new HashMap<>();
    /** 次元種別の番号→名前（766／767／768／769／770／771／772／773／774／775 の Join Game／Respawn の番号解決用）。 */
    private final List<String> dimensionNamesById = new ArrayList<>();
    /** バイオームの番号→名前（Registry Data／763 の codec から読む）。 */
    private List<String> biomeNames = List.of();
    /** 現在の次元（Join Game／Respawn で決まる）。 */
    private String currentDimension = "minecraft:overworld";
    /** 現在の次元の minY・高さ（既定はオーバーワールド）。 */
    private int dimensionMinY = -64;
    private int dimensionHeight = 384;

    public JavaBackendConnection(String host, int port, int serverProtocol,
            String javaName, UUID javaUuid, Listener listener) {
        this.host = host;
        this.port = port;
        this.serverProtocol = serverProtocol;
        this.javaName = javaName;
        this.javaUuid = javaUuid;
        this.listener = listener;
    }

    /** 内部バニラに接続し、ログイン〜プレイ開始までの流れを別スレッドで進める。 */
    @Override
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(30_000);
        out = socket.getOutputStream();
        readerThread = new Thread(this::run, "bedrock-java-backend-" + javaName);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public boolean isJoined() {
        return joined;
    }

    @Override
    public boolean isClosed() {
        return closed || socket == null || socket.isClosed();
    }

    /** チャット文を Java 側へ送る（署名なしの Chat Message 形式）。 */
    @Override
    public synchronized void sendChat(String message) {
        if (isClosed() || !joined) {
            return;
        }
        try {
            PacketIO.Writer writer = new PacketIO.Writer();
            if (serverProtocol == 757) {
                // 757 のチャット（サーバー行き 0x03）は署名なしの {message} だけ。
                // 1.19 で Chat Message／Chat Command に分離される前の形式。
                // minecraft-data mc-1.18-protocol.json で確認。
                writer.writeString(message);
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.PLAY_CHAT_TO_SERVER, serverProtocol)
                                .orElseThrow(),
                        writer.bytes()));
                return;
            }
            writer.writeString(message);
            writer.writeLong(System.currentTimeMillis());
            writer.writeLong(0L); // salt
            if (serverProtocol == 760) {
                // 760の Chat Message は {message, timestamp, salt, 署名バッファ,
                // signedPreview, previousMessages, lastRejectedMessage}。
                writer.writeByteArray(new byte[0]); // 署名なし
                writer.writeBoolean(false); // signedPreview
                writer.writeVarInt(0); // previousMessages 0件
                writer.writeBoolean(false); // lastRejectedMessage 無し
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.PLAY_CHAT_MESSAGE, serverProtocol)
                                .orElseThrow(),
                        writer.bytes()));
                return;
            }
            if (serverProtocol == 759) {
                // 759の Chat Message は {message, timestamp, salt, 署名バッファ,
                // signedPreview}（previousMessages／lastRejectedMessage は760で追加された）。
                writer.writeByteArray(new byte[0]); // 署名なし
                writer.writeBoolean(false); // signedPreview
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.PLAY_CHAT_MESSAGE, serverProtocol)
                                .orElseThrow(),
                        writer.bytes()));
                return;
            }
            writer.writeBoolean(false); // 署名なし（offline 想定）
            writer.writeVarInt(0); // offset
            writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged
            if (serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772
                    || serverProtocol == 773 || serverProtocol == 774
                    || serverProtocol == 775) {
                // 770／771／772／773／774／775の Chat Message は末尾に checksum u8 が増えた。
                // プロキシは770／771／772／773／774／775クライアントからの Chat Message の末尾1バイトを
                // checksum として落とすため、ここで付けないと acknowledged の
                // 末尾バイトが削られて壊れる。値は 0x00（771までの実バニラで確認済み：統合テストと
                // 実バニラ1.21.6のプローブ。1.21.7／1.21.8／1.21.9／1.21.10／1.21.11／26.1は未確認）。
                writer.writeByte((byte) 0);
            }
            send(new MinecraftPacket(
                    PacketIds.id(PacketIds.PacketName.PLAY_CHAT_MESSAGE, serverProtocol)
                            .orElseThrow(),
                    writer.bytes()));
        } catch (IOException | RuntimeException e) {
            LOG.warning("Bedrock からのチャット送信に失敗しました: " + e.getMessage());
        }
    }

    /** コマンド（{@code /} を除いた文字列）を Java 側へ送る。 */
    @Override
    public synchronized void sendCommand(String command) {
        if (isClosed() || !joined) {
            return;
        }
        try {
            PacketIO.Writer writer = new PacketIO.Writer();
            if (serverProtocol == 757) {
                // 757 には Chat Command パケットが無い（1.19 で分離）。
                // コマンドは "/" 付きのチャット（0x03）として送る。
                writer.writeString("/" + command);
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.PLAY_CHAT_TO_SERVER, serverProtocol)
                                .orElseThrow(),
                        writer.bytes()));
                return;
            }
            writer.writeString(command);
            if (serverProtocol == 760) {
                // 760の Chat Command は {command, timestamp, salt,
                // argumentSignatures, signedPreview, previousMessages,
                // lastRejectedMessage}。
                writer.writeLong(System.currentTimeMillis());
                writer.writeLong(0L); // salt
                writer.writeVarInt(0); // argumentSignatures 0件
                writer.writeBoolean(false); // signedPreview
                writer.writeVarInt(0); // previousMessages 0件
                writer.writeBoolean(false); // lastRejectedMessage 無し
            }
            if (serverProtocol == 759) {
                // 759の Chat Command は {command, timestamp, salt,
                // argumentSignatures, signedPreview}（previousMessages／
                // lastRejectedMessage は760で追加された）。
                writer.writeLong(System.currentTimeMillis());
                writer.writeLong(0L); // salt
                writer.writeVarInt(0); // argumentSignatures 0件
                writer.writeBoolean(false); // signedPreview
            }
            send(new MinecraftPacket(
                    PacketIds.id(PacketIds.PacketName.PLAY_CHAT_COMMAND, serverProtocol)
                            .orElseThrow(),
                    writer.bytes()));
        } catch (IOException | RuntimeException e) {
            LOG.warning("Bedrock からのコマンド送信に失敗しました: " + e.getMessage());
        }
    }

    /** 移動を Java 側へ送る（位置＋向き）。 */
    @Override
    public synchronized void sendPosition(double x, double y, double z,
            float yaw, float pitch, boolean onGround) {
        if (isClosed() || !joined) {
            return;
        }
        try {
            PacketIO.Writer writer = new PacketIO.Writer();
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(yaw);
            writer.writeFloat(pitch);
            writer.writeBoolean(onGround);
            send(new MinecraftPacket(
                    PacketIds.id(PacketIds.PacketName.PLAY_SET_PLAYER_POSITION_AND_ROTATION,
                                    serverProtocol)
                            .orElseThrow(),
                    writer.bytes()));
        } catch (IOException | RuntimeException e) {
            LOG.warning("Bedrock からの移動送信に失敗しました: " + e.getMessage());
        }
    }

    /** Use Entity（攻撃／インタラクト）を Java 側へ送る。 */
    @Override
    public synchronized void sendUseEntity(int entityId, int action, int hand) {
        if (isClosed() || !joined) {
            return;
        }
        try {
            PacketIO.Writer writer = new PacketIO.Writer();
            writer.writeVarInt(entityId);
            writer.writeVarInt(action);
            if (action == 0) { // interact：手
                writer.writeVarInt(hand);
            }
            // action == 2（interact at）は送らない。
            send(new MinecraftPacket(
                    PacketIds.id(PacketIds.PacketName.PLAY_USE_ENTITY, serverProtocol)
                            .orElseThrow(),
                    writer.bytes()));
        } catch (IOException | RuntimeException e) {
            LOG.warning("Bedrock からの Use Entity 送信に失敗しました: " + e.getMessage());
        }
    }

    /** Entity Action（自分自身の姿勢）を Java 側へ送る。 */
    @Override
    public synchronized void sendEntityAction(int action) {
        if (isClosed() || !joined || selfEntityId < 0) {
            return;
        }
        try {
            PacketIO.Writer writer = new PacketIO.Writer();
            writer.writeVarInt(selfEntityId);
            writer.writeVarInt(action);
            writer.writeVarInt(0); // jump boost
            send(new MinecraftPacket(
                    PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_ACTION, serverProtocol)
                            .orElseThrow(),
                    writer.bytes()));
        } catch (IOException | RuntimeException e) {
            LOG.warning("Bedrock からの Entity Action 送信に失敗しました: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // 閉じるときの失敗は無視する。
        }
    }

    private void send(MinecraftPacket packet) throws IOException {
        out.write(packet.encode());
        out.flush();
    }

    /** ログイン→設定フェーズ→プレイの読み取りループ。 */
    private void run() {
        try {
            InputStream in = socket.getInputStream();
            // ハンドシェイク（次状態＝ログイン）。
            PacketIO.Writer handshake = new PacketIO.Writer();
            handshake.writeVarInt(serverProtocol);
            handshake.writeString(host);
            handshake.writeUnsignedShort(port);
            handshake.writeVarInt(2);
            send(new MinecraftPacket(0x00, handshake.bytes()));
            // ログイン開始。759 は名前＋署名オプションのみ（UUID 無し）、760 は名前＋署名データ無し＋UUID あり、761・762・763 は名前＋bool＋UUID、764 以上は名前＋UUID。
            PacketIO.Writer loginStart = new PacketIO.Writer();
            loginStart.writeString(javaName);
            if (serverProtocol == 759) {
                // 759 の Login Start は {username, 署名オプション} のみで
                // UUID フィールドが無い。
                loginStart.writeBoolean(false); // 署名なし
            } else {
                if (serverProtocol == 760) {
                    loginStart.writeBoolean(false); // 署名データ無し
                    loginStart.writeBoolean(true); // UUID あり
                } else if (serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {
                    loginStart.writeBoolean(true);
                }
                loginStart.writeUuid(javaUuid);
            }
            send(new MinecraftPacket(0x00, loginStart.bytes()));

            int state = 0; // 0=ログイン、1=設定フェーズ、2=プレイ
            MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
            byte[] buffer = new byte[8192];
            while (!closed) {
                int read = in.read(buffer);
                if (read < 0) {
                    throw new EOFException("内部バニラが接続を閉じました。");
                }
                for (MinecraftPacket packet : decoder.feed(buffer, 0, read)) {
                    state = handleServerPacket(packet, state);
                }
            }
        } catch (IOException | RuntimeException e) {
            if (!closed) {
                LOG.warning("Java バックエンド（" + javaName + "）が切れました: " + e.getMessage());
            }
        } finally {
            closed = true;
            if (!joined) {
                listener.onDisconnected("Java サーバーへの接続に失敗しました。");
            }
        }
    }

    /**
     * サーバーからのパケット 1 個を処理し、次の状態を返す。
     * パッケージ・プライベート：単体テストが偽 Java サーバーなしで呼ぶ。
     */
    int handleServerPacket(MinecraftPacket packet, int state) throws IOException {
        int id = packet.id();
        if (state == 0) { // ログイン
            if (id == PacketIds.id(PacketIds.PacketName.LOGIN_SET_COMPRESSION, serverProtocol)
                    .orElse(-1)) {
                throw new IOException("内部バニラが圧縮を要求しました（未対応）。");
            }
            if (id == PacketIds.id(PacketIds.PacketName.LOGIN_SUCCESS, serverProtocol)
                    .orElse(-1)) {
                if (serverProtocol == 757 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {
                    // 757・759・760・761・762・763 には設定フェーズが無い：このままプレイに入る。
                    return 2;
                }
                // 764 以上：Login Acknowledged を送り、設定フェーズに入る。
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.LOGIN_ACKNOWLEDGED, serverProtocol)
                                .orElseThrow(),
                        new byte[0]));
                sendClientInformation();
                return 1;
            }
            if (id == PacketIds.id(PacketIds.PacketName.LOGIN_DISCONNECT, serverProtocol)
                    .orElse(-1)) {
                listener.onDisconnected("ログインが拒否されました: "
                        + extractTextComponent(packet.payload()));
                close();
                return state;
            }
            return state;
        }
        if (state == 1) { // 設定フェーズ
            if (id == PacketIds.id(PacketIds.PacketName.CONFIG_KEEP_ALIVE, serverProtocol)
                    .orElse(-1)) {
                long keepAliveId = new PacketIO.Reader(packet.payload()).readLong();
                PacketIO.Writer response = new PacketIO.Writer();
                response.writeLong(keepAliveId);
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.CONFIG_KEEP_ALIVE_TO_SERVER,
                                        serverProtocol)
                                .orElseThrow(),
                        response.bytes()));
                return state;
            }
            if (id == PacketIds.id(PacketIds.PacketName.CONFIG_ADD_RESOURCE_PACK, serverProtocol)
                    .orElse(-1)) {
                // 追加パック（765: 0x07、766／767／768／769／770／771／772／773／774／775: 0x09）。UUID を読んで accepted（3）で返す。
                UUID packId = new PacketIO.Reader(packet.payload()).readUuid();
                PacketIO.Writer response = new PacketIO.Writer();
                response.writeUuid(packId);
                response.writeVarInt(3);
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.CONFIG_RESOURCE_PACK_RESPONSE,
                                        serverProtocol)
                                .orElseThrow(),
                        response.bytes()));
                return state;
            }
            if (id == PacketIds.id(PacketIds.PacketName.CONFIG_RESOURCE_PACK_SEND, serverProtocol)
                    .orElse(-1)) {
                // 764 の単一パック送付（UUID なし）。結果だけ accepted（3）で返す。
                PacketIO.Writer response = new PacketIO.Writer();
                response.writeVarInt(3);
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.CONFIG_RESOURCE_PACK_RESPONSE,
                                        serverProtocol)
                                .orElseThrow(),
                        response.bytes()));
                return state;
            }
            if (id == PacketIds.id(PacketIds.PacketName.CONFIG_SELECT_KNOWN_PACKS,
                            serverProtocol)
                    .orElse(-1)) {
                // 766（1.20.5）で新設。サーバーが知っているデータパックの
                // 一覧を送り、クライアントの知っているパックを聞いてくる。
                // 返事をしないとサーバーが先に進まず、Finish Configuration が
                // 来ない（内部 766 サーバーの参加が止まる原因だった）。
                // 知っているパックは無い（空の一覧）として返す。サーバーは
                // その場合、必要な Registry Data をすべて送ってくる。
                // 中身は String×3 の配列だが、空なら VarInt 0 の 1 バイトだけ。
                PacketIO.Writer response = new PacketIO.Writer();
                response.writeVarInt(0);
                send(new MinecraftPacket(
                        PacketIds.id(
                                        PacketIds.PacketName.CONFIG_SELECT_KNOWN_PACKS_TO_SERVER,
                                        serverProtocol)
                                .orElseThrow(),
                        response.bytes()));
                return state;
            }
            if (id == PacketIds.id(PacketIds.PacketName.CONFIG_PING, serverProtocol)
                    .orElse(-1)) {
                // 設定フェーズの Ping（766 以外にもある）。ID をそのまま返す。
                int pingId = new PacketIO.Reader(packet.payload()).readInt();
                PacketIO.Writer response = new PacketIO.Writer();
                response.writeInt(pingId);
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.CONFIG_PONG, serverProtocol)
                                .orElseThrow(),
                        response.bytes()));
                return state;
            }
            if (id == PacketIds.id(PacketIds.PacketName.CONFIG_FINISH_CONFIGURATION, serverProtocol)
                    .orElse(-1)) {
                send(new MinecraftPacket(
                        PacketIds.id(
                                        PacketIds.PacketName.CONFIG_ACKNOWLEDGE_FINISH_CONFIGURATION,
                                        serverProtocol)
                                .orElseThrow(),
                        new byte[0]));
                return 2;
            }
            if (id == PacketIds.id(PacketIds.PacketName.CONFIG_REGISTRY_DATA, serverProtocol)
                    .orElse(-1)) {
                parseRegistryData(packet.payload());
                return state;
            }
            if (id == PacketIds.id(PacketIds.PacketName.CONFIG_DISCONNECT, serverProtocol)
                    .orElse(-1)) {
                listener.onDisconnected("設定フェーズで切断されました: "
                        + extractTextComponent(packet.payload()));
                close();
                return state;
            }
            // それ以外の設定パケットは無視する。
            return state;
        }
        // プレイ
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_KEEP_ALIVE, serverProtocol)
                .orElse(-1)) {
            long keepAliveId = new PacketIO.Reader(packet.payload()).readLong();
            PacketIO.Writer response = new PacketIO.Writer();
            response.writeLong(keepAliveId);
            send(new MinecraftPacket(
                    PacketIds.id(PacketIds.PacketName.PLAY_KEEP_ALIVE_TO_SERVER, serverProtocol)
                            .orElseThrow(),
                    response.bytes()));
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_JOIN_GAME, serverProtocol)
                .orElse(-1)) {
            int entityId = new PacketIO.Reader(packet.payload()).readInt();
            selfEntityId = entityId;
            try {
                parseJoinGame(packet.payload());
            } catch (RuntimeException e) {
                LOG.warning("Join Game の次元が読めませんでした（既定のオーバーワールドで進みます）: "
                        + e.getMessage());
            }
            joined = true;
            listener.onJoined(entityId);
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_POSITION, serverProtocol)
                .orElse(-1)) {
            try {
                PacketIO.Reader reader = new PacketIO.Reader(packet.payload());
                if (serverProtocol == 773 || serverProtocol == 774
                        || serverProtocol == 775) {
                    // 1.21.9／1.21.10（773）と1.21.11（774）と26.1（775）：Spawn Position は RespawnData
                    // （次元名の文字列＋位置＋yaw＋pitch）に変わった。先頭の
                    // 次元名を読み飛ばしてから位置を読む。772以下は位置が先頭
                    // （＋角度）のためそのまま読める。
                    reader.readString();
                }
                long packed = reader.readPositionRaw();
                int x = (int) (packed >> 38);
                int y = (int) (packed << 52 >> 52);
                int z = (int) (packed << 26 >> 38);
                listener.onSpawnPosition(x + 0.5, y, z + 0.5);
            } catch (RuntimeException e) {
                LOG.warning("Spawn Position が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_CHAT, serverProtocol)
                .orElse(-1)) {
            // 757 のチャット（クライアント行き 0x0F）：{message string, position i8,
            // sender UUID}。1.19 で System Chat／Player Chat に分離される前の形式。
            // minecraft-data mc-1.18-protocol.json で確認。
            try {
                PacketIO.Reader reader = new PacketIO.Reader(packet.payload());
                String json = reader.readString();
                reader.readByte(); // position（0=chat、1=system、2=game info）
                java.util.UUID sender = reader.readUuid();
                String text = extractTextComponent(
                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (text != null && !text.isEmpty()) {
                    String senderName = playerNames.getOrDefault(sender, "");
                    listener.onChat(senderName, text);
                }
            } catch (RuntimeException e) {
                LOG.warning("Chat が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_SYSTEM_CHAT, serverProtocol)
                .orElse(-1)) {
            String text = extractTextComponent(packet.payload());
            if (text != null && !text.isEmpty()) {
                listener.onChat("", text);
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_PLAYER_CHAT, serverProtocol)
                .orElse(-1)) {
            PlayerChat chat = serverProtocol == 759
                    ? extractPlayerChat759(packet.payload())
                    : serverProtocol == 760
                    ? extractPlayerChat760(packet.payload())
                    : extractPlayerChat(packet.payload());
            if (chat != null) {
                String sender = playerNames.getOrDefault(chat.senderUuid(), "");
                listener.onChat(sender, chat.message());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION, serverProtocol)
                .orElse(-1)) {
            PacketIO.Reader reader = new PacketIO.Reader(packet.payload());
            int teleportId;
            double x;
            double y;
            double z;
            float yaw;
            float pitch;
            if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770
                    || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773
                    || serverProtocol == 774 || serverProtocol == 775) {
                // 768／769／770／771／772／773／774／775：teleportId が先頭、dx／dy／dz が追加、flags は u32。
                // 769／770／771／772／773／774／775 の Synchronize Player Position は 768 と同形。
                teleportId = reader.readVarInt();
                x = reader.readDouble();
                y = reader.readDouble();
                z = reader.readDouble();
                reader.readDouble(); // dx
                reader.readDouble(); // dy
                reader.readDouble(); // dz
                yaw = reader.readFloat();
                pitch = reader.readFloat();
                reader.readInt(); // flags（u32）
            } else {
                x = reader.readDouble();
                y = reader.readDouble();
                z = reader.readDouble();
                yaw = reader.readFloat();
                pitch = reader.readFloat();
                reader.readByte(); // flags
                teleportId = reader.readVarInt();
            }
            PacketIO.Writer confirm = new PacketIO.Writer();
            confirm.writeVarInt(teleportId);
            try {
                send(new MinecraftPacket(
                        PacketIds.id(PacketIds.PacketName.PLAY_TELEPORT_CONFIRM, serverProtocol)
                                .orElseThrow(),
                        confirm.bytes()));
            } catch (IOException e) {
                LOG.warning("Teleport Confirm の送信に失敗しました: " + e.getMessage());
            }
            listener.onTeleport(x, y, z, yaw, pitch);
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_DISCONNECT, serverProtocol)
                .orElse(-1)) {
            listener.onDisconnected(extractTextComponent(packet.payload()));
            close();
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_PLAYER_INFO_UPDATE, serverProtocol)
                .orElse(-1)) {
            // プレイヤー名・ゲームモードの対応表（チャット送信者・AddPlayer 用）。
            // 759・760 は旧式（action 付き）、761以上はビットセット形式
            // （0x01 add player、0x04 gamemode 等）。
            // 769 は update_hat（0x40）と update_list_order（0x80）が増えた。
            // 757 も 759・760 と同じ旧式（action 付き）。
            if (serverProtocol == 757 || serverProtocol == 759 || serverProtocol == 760) {
                recordPlayerInfo760(packet.payload());
            } else {
                recordPlayerInfo(packet.payload());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_PLAYER_INFO_REMOVE, serverProtocol)
                .orElse(-1)) {
            try {
                PacketIO.Reader reader = new PacketIO.Reader(packet.payload());
                int count = reader.readVarInt();
                List<UUID> uuids = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    uuids.add(reader.readUuid());
                }
                for (UUID uuid : uuids) {
                    playerNames.remove(uuid);
                    playerGamemodes.remove(uuid);
                }
                listener.onPlayerInfoRemove(uuids);
            } catch (RuntimeException e) {
                LOG.warning("Player Info Remove が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_RESPAWN, serverProtocol)
                .orElse(-1)) {
            try {
                PacketIO.Reader reader = new PacketIO.Reader(packet.payload());
                String dimension;
                if (serverProtocol == 757) {
                    // 757 の Respawn の次元は DimensionType の値そのもの（NBT）。
                    // 759 形式（次元名の文字列）に直してから読む。
                    // translateRespawn757To759 は Stage B の実測済み変換。
                    byte[] payload759 =
                            PlayTranslator.translateRespawn757To759(packet.payload());
                    dimension = new PacketIO.Reader(payload759).readString();
                } else if (serverProtocol == 766 || serverProtocol == 767
                        || serverProtocol == 768 || serverProtocol == 769
                        || serverProtocol == 770 || serverProtocol == 771
                        || serverProtocol == 772 || serverProtocol == 773
                        || serverProtocol == 774 || serverProtocol == 775) {
                    // 766〜775：Respawn の次元は番号（dimensionNamesById で名前解決）。
                    // 775 の dimension 値は 774 と同一前提（774→775 はペイロード変更 0 件のため）。
                    // 実バニラ 26.1 の Registry Data での確認は Stage E で行う。
                    int dimensionId = reader.readVarInt();
                    dimension = dimensionId >= 0 && dimensionId < dimensionNamesById.size()
                            ? dimensionNamesById.get(dimensionId) : "minecraft:overworld";
                } else {
                    dimension = reader.readString();
                }
                setDimension(dimension);
            } catch (RuntimeException e) {
                LOG.warning("Respawn の次元が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_CHUNK_DATA, serverProtocol)
                .orElse(-1)) {
            listener.onChunk(packet.payload());
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_CHUNK_BATCH_FINISHED, serverProtocol)
                .orElse(-1)) {
            // チャンクバッチの受領応答（Chunk Batch Received、f32 の chunksPerTick）。
            // 実バニラ1.21.9で確認：Ack を返さないと最初のバッチ（9チャンク）の
            // あとチャンクが送られてこない。768〜772も同じ手順が正しいため、
            // パケットが存在する版（764〜）では送る。763には無い。
            PacketIds.id(PacketIds.PacketName.PLAY_CHUNK_BATCH_RECEIVED, serverProtocol)
                    .ifPresent(receivedId -> {
                        try {
                            PacketIO.Writer response = new PacketIO.Writer();
                            response.writeFloat(2.0f);
                            send(new MinecraftPacket(receivedId, response.bytes()));
                        } catch (IOException e) {
                            LOG.warning("Chunk Batch Received が送れませんでした: "
                                    + e.getMessage());
                        }
                    });
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_BLOCK_CHANGE, serverProtocol)
                .orElse(-1)) {
            try {
                PacketIO.Reader reader = new PacketIO.Reader(packet.payload());
                long packed = reader.readPositionRaw();
                int stateId = reader.readVarInt();
                int x = (int) (packed >> 38);
                int y = (int) (packed << 52 >> 52);
                int z = (int) (packed << 26 >> 38);
                listener.onBlockChange(x, y, z, stateId);
            } catch (RuntimeException e) {
                LOG.warning("Block Change が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_MULTI_BLOCK_CHANGE, serverProtocol)
                .orElse(-1)) {
            try {
                parseMultiBlockChange(packet.payload());
            } catch (RuntimeException e) {
                LOG.warning("Multi Block Change が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        // ---- エンティティ（読めないパケットは落とし、読み取りスレッドは止めない）----
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_ENTITY, serverProtocol)
                .orElse(-1)) {
            try {
                BedrockEntityTranslator.SpawnEntity spawn =
                        BedrockEntityTranslator.parseSpawnEntity(packet.payload(), serverProtocol);
                entityTypes.put(spawn.entityId(), spawn.type765());
                listener.onSpawnEntity(spawn);
            } catch (RuntimeException e) {
                LOG.warning("Spawn Entity が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_ENTITY_LIVING, serverProtocol)
                .orElse(-1)) {
            // 757 のモブスポーン（0x02）。1.19 で Spawn Entity（0x00）に統合された。
            try {
                BedrockEntityTranslator.SpawnEntity spawn =
                        BedrockEntityTranslator.parseSpawnEntityLiving(
                                packet.payload(), serverProtocol);
                entityTypes.put(spawn.entityId(), spawn.type765());
                listener.onSpawnEntity(spawn);
            } catch (RuntimeException e) {
                LOG.warning("Spawn Entity Living が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_ENTITY_PAINTING, serverProtocol)
                .orElse(-1)) {
            // 757 の絵画スポーン（0x03）。1.19 で Spawn Entity（0x00）に統合された。
            // 1.18 の絵画には variant のメタデータが無いため、題名はこのパケットの
            // title から合成メタデータで渡す（そうしないと Bedrock 側に送られない）。
            try {
                BedrockEntityTranslator.SpawnPainting painting =
                        BedrockEntityTranslator.parseSpawnEntityPainting(packet.payload());
                long packed = painting.positionRaw();
                int x = (int) (packed >> 38);
                int y = (int) (packed << 52 >> 52);
                int z = (int) (packed << 26 >> 38);
                BedrockEntityTranslator.SpawnEntity spawn =
                        new BedrockEntityTranslator.SpawnEntity(painting.entityId(),
                                painting.uuid(), 69, // 69 = 765 の絵画の種別ID
                                x + 0.5, y + 0.5, z + 0.5,
                                0.0f, 0.0f, 0.0f, painting.direction(),
                                0.0, 0.0, 0.0);
                entityTypes.put(spawn.entityId(), spawn.type765());
                listener.onSpawnEntity(spawn);
                listener.onEntityMetadata(BedrockEntityTranslator.paintingMetadata(
                        painting.entityId(),
                        BedrockEntityTranslator.paintingId757To765(painting.title757())));
            } catch (RuntimeException e) {
                LOG.warning("Spawn Entity Painting が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_EXPERIENCE_ORB, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onSpawnExperienceOrb(BedrockEntityTranslator.parseSpawnExperienceOrb(
                        packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Spawn Experience Orb が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_DESTROY, serverProtocol)
                .orElse(-1)) {
            try {
                List<Integer> removed = BedrockEntityTranslator.parseRemoveEntities(
                        packet.payload());
                for (int removedId : removed) {
                    entityTypes.remove(removedId);
                }
                listener.onRemoveEntities(removed);
            } catch (RuntimeException e) {
                LOG.warning("Remove Entities が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_METADATA, serverProtocol)
                .orElse(-1)) {
            // parseMetadata は途中で止まっても途中までを返す（complete=false）。
            // 種別は Spawn Entity で覚えたものを使う（無ければ共通項目だけ読む）。
            try {
                int entityId = new PacketIO.Reader(packet.payload()).readVarInt();
                int type765 = entityTypes.getOrDefault(entityId, -1);
                BedrockEntityTranslator.JavaMetadata metadata =
                        BedrockEntityTranslator.parseMetadata(
                                packet.payload(), serverProtocol, type765);
                if (metadata != null) {
                    listener.onEntityMetadata(metadata);
                }
            } catch (RuntimeException e) {
                LOG.warning("Entity Metadata が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_VELOCITY, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onEntityVelocity(BedrockEntityTranslator.parseVelocity(
                        packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Entity Velocity が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_TELEPORT, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onEntityTeleport(BedrockEntityTranslator.parseTeleport(
                        packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Entity Teleport が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_REL_ENTITY_MOVE, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onEntityMove(BedrockEntityTranslator.parseRelativeMove(
                        packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Relative Entity Move が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_MOVE_LOOK, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onEntityMoveLook(BedrockEntityTranslator.parseMoveLook(
                        packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Entity Move Look が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_LOOK, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onEntityLook(BedrockEntityTranslator.parseLook(packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Entity Look が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_HEAD_ROTATION, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onEntityHeadRotation(BedrockEntityTranslator.parseHeadRotation(
                        packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Entity Head Rotation が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_EQUIPMENT, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onEntityEquipment(BedrockEntityTranslator.parseEntityEquipment(
                        packet.payload(), serverProtocol));
            } catch (RuntimeException e) {
                LOG.warning("Entity Equipment が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_EFFECT, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onEntityEffect(BedrockEntityTranslator.parseEntityEffect(
                        packet.payload(), serverProtocol));
            } catch (RuntimeException e) {
                LOG.warning("Entity Effect が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_REMOVE_ENTITY_EFFECT, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onRemoveEntityEffect(BedrockEntityTranslator.parseRemoveEntityEffect(
                        packet.payload(), serverProtocol));
            } catch (RuntimeException e) {
                LOG.warning("Remove Entity Effect が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_UPDATE_ATTRIBUTES, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onUpdateAttributes(BedrockEntityTranslator.parseUpdateAttributes(
                        packet.payload(), serverProtocol));
            } catch (RuntimeException e) {
                LOG.warning("Entity Update Attributes が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_SET_PASSENGERS, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onSetPassengers(BedrockEntityTranslator.parseSetPassengers(
                        packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Set Passengers が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        if (id == PacketIds.id(PacketIds.PacketName.PLAY_ATTACH_ENTITY, serverProtocol)
                .orElse(-1)) {
            try {
                listener.onAttachEntity(BedrockEntityTranslator.parseAttachEntity(
                        packet.payload()));
            } catch (RuntimeException e) {
                LOG.warning("Attach Entity が読めませんでした: " + e.getMessage());
            }
            return state;
        }
        // インベントリなどは送らない（スコープ外）。
        return state;
    }

    /** Multi Block Change をほどいて onBlockChange を 1 件ずつ呼ぶ。 */
    private void parseMultiBlockChange(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        long sectionPos = reader.readLong();
        // Section Pos：x 22bit（42〜63）／z 22bit（20〜41）／y 20bit（0〜19）。
        int sectionX = signExtend((int) (sectionPos >> 42), 22);
        int sectionZ = signExtend((int) ((sectionPos >> 20) & 0x3FFFFF), 22);
        int sectionY = signExtend((int) (sectionPos & 0xFFFFF), 20);
        int count = reader.readVarInt();
        for (int i = 0; i < count; i++) {
            int record = reader.readVarInt();
            int stateId = record >>> 12;
            int local = record & 0xFFF;
            int x = sectionX * 16 + (local & 15);
            int z = sectionZ * 16 + ((local >> 4) & 15);
            int y = sectionY * 16 + ((local >> 8) & 15);
            listener.onBlockChange(x, y, z, stateId);
        }
    }

    /** {@code bits} ビットの符号付き値に符号拡張する。 */
    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }

    /** Join Game から次元を読み、現在の次元を決める。 */
    private void parseJoinGame(byte[] payload) {
        if (serverProtocol == 757) {
            // 757 の Join Game：dimension は DimensionType の値そのもの（NBT）、
            // lastDeathLocation は無い（Stage A §11.6。parseJoinGame757 は
            // Stage B の実測済みパーサ）。759 形式に直すと codec のバイオームが
            // 759 順（63 件）になってチャンクの 757 番号（61 件）とずれるため、
            // 757 の codec をそのまま使い、バイオーム名は 757 順で覚える。
            PlayTranslator.JoinGame757 join = PlayTranslator.parseJoinGame757(payload);
            Map<String, Nbt.Tag.CompoundTag> registries =
                    PlayTranslator.registriesFromJoinGameCodec(join.dimensionCodec());
            Nbt.Tag.CompoundTag dimensionRegistry = registries.get("minecraft:dimension_type");
            if (dimensionRegistry != null) {
                applyDimensionRegistryCodec(dimensionRegistry);
            }
            Nbt.Tag.CompoundTag biomeRegistry = registries.get("minecraft:worldgen/biome");
            if (biomeRegistry != null) {
                applyBiomeRegistryCodec(biomeRegistry);
            }
            setDimension(dimensionNameFromElement(join.dimension(), dimensionRegistry));
            return;
        }
        if (serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762) {
            // 759・760・761・762 の Join Game は763と同形式だが、末尾の portalCooldown が無い。
            // 759 の Join Game は760と完全同一（minecraft-data protocol.json
            // 1.19 / 1.19.2 で確認）なので parseJoinGame762 で読める。
            // parseJoinGame762 は762形式で読み、portalCooldown=0 の JoinGame763 にする。
            PlayTranslator.JoinGame763 join = PlayTranslator.parseJoinGame762(payload);
            Map<String, Nbt.Tag.CompoundTag> registries =
                    PlayTranslator.registriesFromJoinGameCodec(join.codec());
            Nbt.Tag.CompoundTag dimensionRegistry = registries.get("minecraft:dimension_type");
            if (dimensionRegistry != null) {
                applyDimensionRegistryCodec(dimensionRegistry);
            }
            Nbt.Tag.CompoundTag biomeRegistry = registries.get("minecraft:worldgen/biome");
            if (biomeRegistry != null) {
                applyBiomeRegistryCodec(biomeRegistry);
            }
            setDimension(join.worldType());
            return;
        }
        if (serverProtocol == 763) {
            PlayTranslator.JoinGame763 join = PlayTranslator.parseJoinGame763(payload);
            Map<String, Nbt.Tag.CompoundTag> registries =
                    PlayTranslator.registriesFromJoinGameCodec(join.codec());
            Nbt.Tag.CompoundTag dimensionRegistry = registries.get("minecraft:dimension_type");
            if (dimensionRegistry != null) {
                applyDimensionRegistryCodec(dimensionRegistry);
            }
            Nbt.Tag.CompoundTag biomeRegistry = registries.get("minecraft:worldgen/biome");
            if (biomeRegistry != null) {
                applyBiomeRegistryCodec(biomeRegistry);
            }
            setDimension(join.worldType());
            return;
        }
        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768
                || serverProtocol == 769 || serverProtocol == 770
                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773
                || serverProtocol == 774 || serverProtocol == 775) {
            // 768／769／770／771／772／773／774／775 の Join Game は767形式に seaLevel を補ったもののため、
            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。
            // 769／770／771／772／773／774／775 の Join Game は 768 と同形のため同じ変換が使える。
            // 775 は 774 とペイロード変更 0 件のため translateJoinGame768To767 をそのまま使う。
            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769
                    || serverProtocol == 770 || serverProtocol == 771 || serverProtocol == 772
                    || serverProtocol == 773 || serverProtocol == 774
                    || serverProtocol == 775
                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;
            PlayTranslator.JoinGame766 join = PlayTranslator.parseJoinGame766(payload767);
            String dimension = join.dimensionId() >= 0
                    && join.dimensionId() < dimensionNamesById.size()
                    ? dimensionNamesById.get(join.dimensionId()) : "minecraft:overworld";
            setDimension(dimension);
            return;
        }
        PlayTranslator.JoinGame765 join = PlayTranslator.parseJoinGame765(payload);
        setDimension(join.worldType());
    }

    /**
     * 757 の Join Game／Respawn の dimension（DimensionType の値そのものの NBT）から
     * 次元名を引く。codec の dimension_type レジストリの各エントリの element と
     * 構造比較し、一致したものの name を返す。一致しなければオーバーワールド。
     * （PlayTranslator.dimensionName757 と同じ考え方。あちらは private のため
     * ここで同じ照合をする。）
     */
    private static String dimensionNameFromElement(Nbt.Tag dimension,
            Nbt.Tag.CompoundTag dimensionRegistry) {
        if (dimension == null || dimensionRegistry == null) {
            return "minecraft:overworld";
        }
        if (dimensionRegistry.getList("value").isPresent()) {
            for (Nbt.Tag entry : dimensionRegistry.getList("value").get().value()) {
                if (!(entry instanceof Nbt.Tag.CompoundTag compound)) {
                    continue;
                }
                String name = compound.getString("name").orElse(null);
                Nbt.Tag element = compound.getCompound("element").orElse(null);
                if (name != null && element != null && element.equals(dimension)) {
                    return name;
                }
            }
        }
        return "minecraft:overworld";
    }

    /**
     * 設定フェーズの Registry Data を読む。765の新形式（ペイロード全体が
     * 1個の名前なし NBT で、ルートのキーがレジストリ名）は
     * {@link PlayTranslator#parseRegistryData(byte[])} が読む。
     * 届いたレジストリはすべて {@link Listener#onRegistry} へ渡す。
     * dimension_type と worldgen/biome は次元情報にも反映する。
     */
    private void parseRegistryData(byte[] payload) {
        try {
            Map<String, Nbt.Tag.CompoundTag> registries =
                    PlayTranslator.parseRegistryData(payload);
            for (Map.Entry<String, Nbt.Tag.CompoundTag> entry : registries.entrySet()) {
                String registry = entry.getKey();
                Nbt.Tag.CompoundTag codec = entry.getValue();
                List<String> names = registryNames(codec);
                listener.onRegistry(registry, List.copyOf(names));
                if (registry.equals("minecraft:worldgen/biome")) {
                    applyBiomeRegistryCodec(codec);
                } else if (registry.equals("minecraft:dimension_type")) {
                    applyDimensionRegistryCodec(codec);
                }
            }
            updateDimensionInfo();
        } catch (RuntimeException e) {
            LOG.warning("Registry Data が読めませんでした: " + e.getMessage());
        }
    }

    /** レジストリ codec の value 一覧から、id 順の名前の列を作る。 */
    static List<String> registryNames(Nbt.Tag.CompoundTag registry) {
        List<String> names = new ArrayList<>();
        registry.getList("value").ifPresent(list -> {
            for (Nbt.Tag entry : list.value()) {
                if (!(entry instanceof Nbt.Tag.CompoundTag compound)) {
                    continue;
                }
                String name = compound.getString("name").orElse(null);
                int id = intTag(compound, "id", -1);
                if (name == null || id < 0) {
                    continue;
                }
                while (names.size() <= id) {
                    names.add("");
                }
                names.set(id, name);
            }
        });
        return names;
    }

    /** 763 の Join Game の codec に入る dimension_type レジストリを適用する。 */
    private void applyDimensionRegistryCodec(Nbt.Tag.CompoundTag registry) {
        dimensionNamesById.clear();
        registry.getList("value").ifPresent(list -> {
            for (Nbt.Tag entry : list.value()) {
                if (!(entry instanceof Nbt.Tag.CompoundTag compound)) {
                    continue;
                }
                String name = compound.getString("name").orElse(null);
                int id = intTag(compound, "id", -1);
                Nbt.Tag.CompoundTag element = compound.getCompound("element").orElse(null);
                if (name == null || id < 0 || element == null) {
                    continue;
                }
                while (dimensionNamesById.size() <= id) {
                    dimensionNamesById.add("minecraft:overworld");
                }
                dimensionNamesById.set(id, name);
                int minY = intTag(element, "min_y", -64);
                int height = intTag(element, "height", 384);
                dimensionTypes.put(name, new int[]{minY, height});
            }
        });
    }

    /** 763 の Join Game の codec に入る worldgen/biome レジストリを適用する。 */
    private void applyBiomeRegistryCodec(Nbt.Tag.CompoundTag registry) {
        List<String> names = new ArrayList<>();
        registry.getList("value").ifPresent(list -> {
            for (Nbt.Tag entry : list.value()) {
                if (!(entry instanceof Nbt.Tag.CompoundTag compound)) {
                    continue;
                }
                String name = compound.getString("name").orElse(null);
                int id = intTag(compound, "id", -1);
                if (name == null || id < 0) {
                    continue;
                }
                while (names.size() <= id) {
                    names.add("minecraft:plains");
                }
                names.set(id, name);
            }
        });
        if (!names.isEmpty()) {
            biomeNames = List.copyOf(names);
        }
    }

    private static int intTag(Nbt.Tag.CompoundTag compound, String name, int fallback) {
        return compound.get(name)
                .filter(t -> t instanceof Nbt.Tag.IntTag)
                .map(t -> ((Nbt.Tag.IntTag) t).value())
                .orElse(fallback);
    }

    /** 現在の次元を決め、minY・高さを更新してセッションへ伝える。 */
    private void setDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            dimension = "minecraft:overworld";
        }
        currentDimension = dimension;
        int[] type = dimensionTypes.get(dimension);
        if (type != null) {
            dimensionMinY = type[0];
            dimensionHeight = type[1];
        } else {
            dimensionMinY = -64;
            dimensionHeight = 384;
        }
        updateDimensionInfo();
    }

    /** 決まっている次元情報をセッションへ伝える（変わったときだけ）。 */
    private void updateDimensionInfo() {
        listener.onDimensionInfo(dimensionMinY, dimensionHeight, biomeNames);
    }

    /**
     * プレイヤー一覧（Player Info Update）を読む。763〜766 共通の
     * ビットセット形式：0x01 add player、0x02 init chat、0x04 gamemode、
     * 0x08 listed、0x10 latency、0x20 display name。add player は
     * UUID→名前・ゲームモードを記録して {@link Listener#onPlayerInfoAdd} を
     * 呼び、gamemode 更新は {@link Listener#onPlayerInfoGamemode} を呼ぶ。
     * display name は 761・762・763 が JSON 文字列、764 以上が任意 NBT。
     * 読めない一覧は無視する（チャット名が空になるだけ）。
     */
    /**
     * 759・760の Player Info（旧式、action 付き）からプレイヤー名・ゲームモードを
     * 対応表に入れる。action は 0=add_player、1=update_game_mode、
     * 2=update_latency、3=update_display_name、4=remove_player。
     * display name は JSON 文字列（761・762・763 と同じ）。
     */
    private void recordPlayerInfo760(byte[] payload) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            int action = reader.readVarInt();
            int count = reader.readVarInt();
            for (int i = 0; i < count; i++) {
                UUID uuid = reader.readUuid();
                switch (action) {
                    case 0 -> { // add_player
                        String name = reader.readString();
                        int propertyCount = reader.readVarInt();
                        for (int j = 0; j < propertyCount; j++) {
                            reader.readString(); // プロパティ名
                            reader.readString(); // 値
                            if (reader.readBoolean()) {
                                reader.readString(); // 署名
                            }
                        }
                        int gamemode = reader.readVarInt();
                        reader.readVarInt(); // ping
                        if (reader.readBoolean()) {
                            reader.readString(); // display name（JSON）
                        }
                        if (serverProtocol != 757 && reader.readBoolean()) {
                            // 757 の add_player には crypto（公開鍵・署名）が無い
                            // （1.19 で追加。minecraft-data mc-1.18／mc-1.19 の
                            // packet_player_info で確認）。
                            // 760 の crypto は {timestamp, 公開鍵, 署名} で UUID は無い
                            //（minecraft-data 1.19.2 packet_player_info）。
                            reader.readLong(); // 有効期限
                            reader.readByteArray(); // 公開鍵
                            reader.readByteArray(); // 署名
                        }
                        playerNames.put(uuid, name);
                        playerGamemodes.put(uuid, gamemode);
                        listener.onPlayerInfoAdd(uuid, name, gamemode);
                    }
                    case 1 -> { // update_game_mode
                        int gamemode = reader.readVarInt();
                        playerGamemodes.put(uuid, gamemode);
                        listener.onPlayerInfoGamemode(uuid, gamemode);
                    }
                    case 2 -> { // update_latency
                        reader.readVarInt(); // ping
                    }
                    case 3 -> { // update_display_name
                        if (reader.readBoolean()) {
                            reader.readString(); // display name（JSON）
                        }
                    }
                    case 4 -> { // remove_player
                        playerNames.remove(uuid);
                        playerGamemodes.remove(uuid);
                        listener.onPlayerInfoRemove(java.util.List.of(uuid));
                    }
                    default -> throw new IllegalStateException("未知の Player Info action: " + action);
                }
            }
        } catch (RuntimeException e) {
            // 読めない一覧は無視する（チャット名が空になるだけ）。
        }
    }

    private void recordPlayerInfo(byte[] payload) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            int actions = reader.readVarInt();
            // 769 の update_hat（0x40）と update_list_order（0x80）も読む。
            // 読まないと後続のビットの並びがずれ、名前・ゲームモードが壊れる。
            for (int bit = 0x01; bit <= 0x80; bit <<= 1) {
                if ((actions & bit) == 0) {
                    continue;
                }
                int count = reader.readVarInt();
                for (int i = 0; i < count; i++) {
                    switch (bit) {
                        case 0x01 -> { // add player
                            UUID uuid = reader.readUuid();
                            String name = reader.readString();
                            int propertyCount = reader.readVarInt();
                            for (int j = 0; j < propertyCount; j++) {
                                reader.readString(); // プロパティ名
                                reader.readString(); // 値
                                if (reader.readBoolean()) {
                                    reader.readString(); // 署名
                                }
                            }
                            int gamemode = reader.readVarInt();
                            reader.readVarInt(); // ping
                            if (reader.readBoolean()) {
                                if (serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {
                                    reader.readString(); // display name（JSON）
                                } else {
                                    reader.readAnonymousNbt(); // display name
                                }
                            }
                            playerNames.put(uuid, name);
                            playerGamemodes.put(uuid, gamemode);
                            listener.onPlayerInfoAdd(uuid, name, gamemode);
                        }
                        case 0x02 -> { // init chat
                            reader.readUuid();
                            if (reader.readBoolean()) {
                                reader.readUuid(); // chat session UUID
                                reader.readLong(); // 有効期限
                                reader.readByteArray(); // 公開鍵
                                reader.readByteArray(); // 署名
                            }
                        }
                        case 0x04 -> { // update gamemode
                            UUID uuid = reader.readUuid();
                            int gamemode = reader.readVarInt();
                            playerGamemodes.put(uuid, gamemode);
                            listener.onPlayerInfoGamemode(uuid, gamemode);
                        }
                        case 0x08 -> { // update listed
                            reader.readUuid();
                            reader.readBoolean();
                        }
                        case 0x10 -> { // update latency
                            reader.readUuid();
                            reader.readVarInt(); // ping
                        }
                        case 0x20 -> { // update display name
                            reader.readUuid();
                            if (reader.readBoolean()) {
                                if (serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {
                                    reader.readString(); // display name（JSON）
                                } else {
                                    reader.readAnonymousNbt(); // display name
                                }
                            }
                        }
                        case 0x40 -> { // update hat（769 で新設）
                            reader.readUuid();
                            reader.readBoolean(); // showHat（帽子の表示）
                        }
                        case 0x80 -> { // update list order（769 で新設）
                            reader.readUuid();
                            reader.readVarInt(); // listPriority
                        }
                        default -> throw new IllegalStateException("未知の Player Info action: " + bit);
                    }
                }
            }
        } catch (RuntimeException e) {
            // 読めない一覧は無視する（チャット名が空になるだけ）。
        }
    }

    /** ログイン後の Client Information（設定フェーズ）を送る。 */
    private void sendClientInformation() throws IOException {
        send(new MinecraftPacket(
                PacketIds.id(PacketIds.PacketName.CONFIG_CLIENT_INFORMATION, serverProtocol)
                        .orElseThrow(),
                clientSettingsPayload(serverProtocol)));
    }

    /**
     * Client Information（設定）のペイロードを作る。757／759 の形式は同一で
     * {locale, viewDistance i8, chatMode varint, chatColors bool, skinParts u8,
     * mainHand varint, enableTextFiltering bool, allowServerListings bool}
     * （minecraft-data mc-1.18／mc-1.19-protocol.json の packet_settings で確認）。
     * particleStatus（末尾の varint）は 768 から（777 の前例と同じ）。
     * 757／759〜763 には設定フェーズが無いため、このペイロードは 764 以上でのみ
     * 送られる（757 も 759 と同じく送らない。サーバーは既定値を使う）。
     */
    static byte[] clientSettingsPayload(int serverProtocol) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("en_US");
        writer.writeByte(8); // view distance
        writer.writeVarInt(0); // chat mode: 全て表示
        writer.writeBoolean(true); // chat colors
        writer.writeByte(0x7f); // skin parts
        writer.writeVarInt(1); // main hand: 右
        writer.writeBoolean(false); // text filtering
        writer.writeBoolean(true); // allow server listings
        if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770
                || serverProtocol == 771 || serverProtocol == 772 || serverProtocol == 773
                || serverProtocol == 774 || serverProtocol == 775) {
            // 768／769／770／771／772／773／774／775 は末尾に particleStatus varint がある（0=all）。
            // 付けないと 1.21.2／1.21.3／1.21.4／1.21.5／1.21.6／1.21.7／1.21.8／1.21.9／1.21.10／1.21.11／26.1 のバニラが読み取り途中で切断する。
            // 775 は 774 とペイロード変更 0 件のため同じ形式（実バニラ 26.1 での確認は Stage E）。
            writer.writeVarInt(0);
        }
        return writer.bytes();
    }

    /**
     * テキストコンポーネント（System Chat / Disconnect）のペイロードから
     * 表示文を抜く。765 は NBT、760 / 761 / 762 / 763 / 764 は JSON 文字列。
     * 読めない場合はペイロードをそのまま文字列化して返す（空にはしない）。
     */
    static String extractTextComponent(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        try {
            if (payload[0] == Nbt.COMPOUND) {
                Nbt.ReadResult result = Nbt.readAnonymous(payload, 0);
                if (result.tag() instanceof Nbt.Tag.CompoundTag compound) {
                    String text = compound.getString("text").orElse(null);
                    if (text != null) {
                        return text;
                    }
                    // {"translate": "..."} 型は translate キーをそのまま返す。
                    String translate = compound.getString("translate").orElse(null);
                    if (translate != null) {
                        return translate;
                    }
                    // "extra" だけのコンポーネントは extra の text を繋げる。
                    if (compound.getList("extra").isPresent()) {
                        StringBuilder out = new StringBuilder();
                        for (Nbt.Tag child : compound.getList("extra").get().value()) {
                            if (child instanceof Nbt.Tag.CompoundTag childCompound) {
                                childCompound.getString("text").ifPresent(out::append);
                            } else if (child instanceof Nbt.Tag.StringTag stringTag) {
                                out.append(stringTag.value());
                            }
                        }
                        return out.toString();
                    }
                }
                return "";
            }
            // JSON 文字列（760 / 761 / 762 / 763 / 764）。"text" の値を抜く。
            String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            String text = BedrockLogin.extractString(json, "text");
            return text == null ? json : text;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Player Chat（765）のペイロードから送信者 UUID と平文メッセージを抜く。
     * 形式：送信者 UUID＋index VarInt＋署名の有無（あれば 256 バイト）＋
     * メッセージ文字列＋…。読めなければ null。
     */
    record PlayerChat(UUID senderUuid, String message) {
    }

    /**
     * 759の Player Chat から送信者と本文を抜く。759は署名付きチャットが
     * 導入された最初の形式で、本文・送信者・署名が1パケットに直列する
     * （{signedChatContent, unsignedChatContent option, type, senderUuid,
     * senderName, senderTeam option, timestamp, salt, signature}）。
     * 760は連鎖ハッシュ＋Message Header付きの別形式。
     */
    static PlayerChat extractPlayerChat759(byte[] payload) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            String message = reader.readString(); // signedChatContent
            if (reader.readBoolean()) {
                reader.readString(); // unsignedChatContent
            }
            reader.readVarInt(); // type
            UUID sender = reader.readUuid();
            reader.readString(); // senderName
            if (reader.readBoolean()) {
                reader.readString(); // senderTeam
            }
            reader.readLong(); // timestamp
            reader.readLong(); // salt
            reader.readByteArray(); // 署名
            return new PlayerChat(sender, message);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 760の Player Chat から送信者と本文を抜く。760は Message Header と
     * Player Chat が分かれており、Player Chat の先頭は previousSignature
     * option＋送信者UUID＋署名（必須バッファ）＋本文である。
     */
    static PlayerChat extractPlayerChat760(byte[] payload) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            if (reader.readBoolean()) {
                reader.readByteArray(); // previousSignature
            }
            UUID sender = reader.readUuid();
            reader.readByteArray(); // 署名
            return new PlayerChat(sender, reader.readString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    static PlayerChat extractPlayerChat(byte[] payload) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            UUID sender = reader.readUuid();
            reader.readVarInt();
            if (reader.readBoolean()) {
                reader.readBytes(256); // 署名
            }
            return new PlayerChat(sender, reader.readString());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
