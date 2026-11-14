package io.detergentmc.bedrock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bedrock（RakNet UDP）の待ち受けサーバー。
 *
 * <p>UDP ソケット 1 個で次のものを扱う：
 * <ul>
 *   <li>Unconnected Ping（0x01）→ Unconnected Pong（0x1c）。MOTD は
 *       {@code MCPE;...} 形式。サーバーリストに DetergentMC が出るのは
 *       この応答のおかげである。</li>
 *   <li>Open Connection Request 1 / 2 → Reply 1 / 2。RakNet バージョンが
 *       11 以外なら Incompatible Protocol を返す。</li>
 *   <li>フレームセット：セッションが無ければ Connection Request（0x09）を
 *       探して {@link BedrockSession} を作り、あればそのセッションへ渡す。
 *       ACK / NACK は受け取るだけで再送はしない。</li>
 * </ul>
 *
 * <p>起動は {@link #start()}、停止は {@link #stop()}。バインドに失敗したら
 * {@link IOException} を投げる。呼び出し側（DetergentServer）は失敗を
 * 警告ログにしてサーバー全体を落とさないこと（Bedrock は任意機能のため）。
 *
 * <p>Java バックエンドの作り方は既定で {@link JavaBackendConnection} を
 * 使う。テストでは {@link BedrockSession.JavaBackendFactory} を差し替える。
 */
public final class BedrockServer {

    private static final Logger LOG = Logger.getLogger(BedrockServer.class.getName());

    /** 受信バッファ（MTU 1492＋余裕）。 */
    private static final int RECEIVE_BUFFER_SIZE = 2048;

    private final BedrockConfig config;
    private final BedrockSession.JavaBackendFactory backendFactory;
    private final long serverGuid = new Random().nextLong();
    private final Map<InetSocketAddress, BedrockSession> sessions = new ConcurrentHashMap<>();
    /** Open Connection Request 2 で交渉した MTU（セッション作成時に渡す）。 */
    private final Map<InetSocketAddress, Integer> negotiatedMtu = new ConcurrentHashMap<>();

    private volatile DatagramSocket socket;
    private volatile Thread listenerThread;
    private volatile boolean running;

    public BedrockServer(BedrockConfig config) {
        this(config, (javaName, javaUuid, listener) -> new JavaBackendConnection(
                config.internalHost(), config.internalPort(), config.serverProtocol(),
                javaName, javaUuid, listener));
    }

    public BedrockServer(BedrockConfig config,
            BedrockSession.JavaBackendFactory backendFactory) {
        this.config = config;
        this.backendFactory = backendFactory;
    }

    /** 待ち受けを開始する。バインドに失敗したら IOException。 */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        try {
            socket = new DatagramSocket(config.port());
        } catch (SocketException e) {
            throw new IOException("Bedrock ポート " + config.port()
                    + " のバインドに失敗しました: " + e.getMessage(), e);
        }
        running = true;
        listenerThread = new Thread(this::listenLoop, "bedrock-raknet-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        LOG.info("Bedrock リスナーを開始しました（UDP " + config.port()
                + "、対象 Bedrock 1.20.0〜26.51 / プロトコル 589〜2193（RakNet））");
    }

    /** 待ち受けを止め、全セッションを閉じる。 */
    public synchronized void stop() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        for (BedrockSession session : List.copyOf(sessions.values())) {
            session.close();
        }
        sessions.clear();
    }

    public boolean isRunning() {
        return running;
    }

    public int port() {
        return config.port();
    }

    /** 実際にバインドされたポート（port 0 で起動したテスト用）。 */
    public int localPort() {
        DatagramSocket current = socket;
        return current == null ? -1 : current.getLocalPort();
    }

    /** 現在の Bedrock セッション数（MOTD の人数に出す）。 */
    public int sessionCount() {
        return sessions.size();
    }

    /** MOTD 文字列（現在のセッション数を反映する）。 */
    public String motd() {
        return BedrockMotd.build(config.motd(), BedrockProtocol.PROTOCOL_1_20_50,
                BedrockProtocol.TARGET_VERSION, sessionCount(), config.maxPlayers(),
                serverGuid, config.serverName(), config.port(), config.port() + 1);
    }

    private void listenLoop() {
        byte[] buffer = new byte[RECEIVE_BUFFER_SIZE];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                if (running) {
                    LOG.warning("Bedrock UDP の受信に失敗しました: " + e.getMessage());
                }
                break;
            }
            byte[] datagram = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), datagram, 0, datagram.length);
            InetSocketAddress remote = new InetSocketAddress(
                    packet.getAddress(), packet.getPort());
            try {
                handleDatagram(remote, datagram);
            } catch (RuntimeException e) {
                LOG.warning("Bedrock データグラムの処理に失敗しました（"
                        + remote + "）: " + e.getMessage());
            }
        }
    }

    /** データグラム 1 個を振り分ける（テストからも呼べる）。 */
    void handleDatagram(InetSocketAddress remote, byte[] datagram) {
        if (datagram == null || datagram.length == 0) {
            return;
        }
        int id = datagram[0] & 0xFF;
        switch (id) {
            case BedrockProtocol.ID_UNCONNECTED_PING -> {
                RakNetCodec.Ping ping = RakNetCodec.parseUnconnectedPing(datagram);
                if (ping != null) {
                    sendTo(remote, RakNetCodec.buildUnconnectedPong(
                            ping.time(), serverGuid, motd()));
                }
            }
            case BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_1 -> {
                RakNetCodec.OpenConnectionRequest1 request =
                        RakNetCodec.parseOpenConnectionRequest1(datagram);
                if (request == null) {
                    return;
                }
                if (request.raknetProtocol() != BedrockProtocol.RAKNET_PROTOCOL_VERSION) {
                    sendTo(remote, RakNetCodec.buildIncompatibleProtocol(serverGuid));
                    return;
                }
                int mtu = RakNetCodec.mtuForRequestLength(request.datagramLength());
                sendTo(remote, RakNetCodec.buildOpenConnectionReply1(serverGuid, mtu));
            }
            case BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_2 -> {
                RakNetCodec.OpenConnectionRequest2 request =
                        RakNetCodec.parseOpenConnectionRequest2(datagram);
                if (request == null) {
                    return;
                }
                int mtu = request.mtu();
                // Request 2 の MTU が小さすぎる場合は 576 に切り上げる。
                if (mtu < 576) {
                    mtu = 576;
                }
                negotiatedMtu.put(remote, mtu);
                sendTo(remote, RakNetCodec.buildOpenConnectionReply2(
                        serverGuid, request.serverAddressBytes(), mtu));
            }
            case BedrockProtocol.ID_ACK, BedrockProtocol.ID_NACK -> {
                // 受信確認。NACK への再送は未実装（制限一覧を参照）。
            }
            default -> {
                if (RakNetCodec.isFrameSet(datagram)) {
                    handleFrameSet(remote, datagram);
                }
                // それ以外は無視する。
            }
        }
    }

    /** フレームセットをセッションへ渡す。セッションが無ければ作る。 */
    private void handleFrameSet(InetSocketAddress remote, byte[] datagram) {
        BedrockSession session = sessions.get(remote);
        if (session != null) {
            session.handleFrameSet(datagram);
            return;
        }
        // セッションが無い場合は、Connection Request を含むフレームセットかだけ見る。
        RakNetCodec.SplitReassembler splits = new RakNetCodec.SplitReassembler();
        List<byte[]> payloads;
        try {
            payloads = RakNetCodec.parseFrameSetPayloads(datagram, splits);
        } catch (RuntimeException e) {
            LOG.warning("未知のアドレスからのフレームセットが読めませんでした（"
                    + remote + "）: " + e.getMessage());
            return;
        }
        for (byte[] payload : payloads) {
            if (payload.length > 0
                    && (payload[0] & 0xFF) == BedrockProtocol.ID_CONNECTION_REQUEST) {
                BedrockSession created = newSession(remote, payload);
                if (created != null) {
                    created.handleFrameSet(datagram);
                }
                return;
            }
        }
        // Connection Request 以外のフレームセットは、セッション無しでは捨てる。
    }

    /** Connection Request からセッションを作る。 */
    private BedrockSession newSession(InetSocketAddress remote, byte[] connectionRequest) {
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(connectionRequest, 1);
        long clientGuid;
        try {
            clientGuid = in.readBigLong();
        } catch (RuntimeException e) {
            return null;
        }
        int mtu = negotiatedMtu.getOrDefault(remote, 1492);
        BedrockSession session = new BedrockSession(remote, clientGuid, serverGuid,
                mtu, new SessionTransport(remote), backendFactory,
                config.serverName(), config.floodgatePrefix(),
                config.chunkRadius(), config.serverProtocol());
        sessions.put(remote, session);
        return session;
    }

    private void sendTo(InetSocketAddress remote, byte[] datagram) {
        DatagramSocket current = socket;
        if (current == null || current.isClosed()) {
            return;
        }
        try {
            current.send(new DatagramPacket(datagram, datagram.length, remote));
        } catch (IOException e) {
            LOG.warning("Bedrock への送信に失敗しました（" + remote + "）: " + e.getMessage());
        }
    }

    /** セッション用の送り口（UDP へ送り、閉じたら一覧から外す）。 */
    private final class SessionTransport implements BedrockSession.Transport {
        private final InetSocketAddress remote;

        SessionTransport(InetSocketAddress remote) {
            this.remote = remote;
        }

        @Override
        public void send(byte[] datagram) {
            sendTo(remote, datagram);
        }

        @Override
        public void onSessionClosed(BedrockSession session) {
            sessions.remove(remote, session);
            negotiatedMtu.remove(remote);
        }
    }

    /** テスト用：セッション一覧のスナップショット。 */
    Map<InetSocketAddress, BedrockSession> sessionsSnapshot() {
        return Map.copyOf(sessions);
    }
}
