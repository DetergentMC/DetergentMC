package io.detergentmc.legacy;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The legacy frontend listener: accepts pre-Netty clients on its own
 * port (separate from the core's modern port, so the verified Netty
 * path is never touched) and hands each connection to a
 * {@link LegacySession}.
 */
public final class LegacyFrontendServer implements Closeable {

    private static final Logger LOG = Logger.getLogger(LegacyFrontendServer.class.getName());

    private final LegacyFrontendConfig config;
    private final KeyPair keyPair = LegacyCrypto.generateKeyPair();
    private final AtomicInteger online = new AtomicInteger();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    public LegacyFrontendServer(LegacyFrontendConfig config) {
        this.config = config;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(config.port()));
        running = true;
        acceptThread = new Thread(this::acceptLoop, "legacy-frontend-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        StringBuilder versions = new StringBuilder();
        for (int protocol : LegacyProtocol.supportedLegacyProtocols()) {
            if (versions.length() > 0) {
                versions.append("・");
            }
            versions.append("プロトコル").append(protocol)
                    .append(" / ").append(LegacyProtocol.versionName(protocol));
        }
        LOG.info("レガシーフロントエンド（" + versions + "）を "
                + config.port() + " 番ポートで開始しました（モード: "
                + config.mode() + "）");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                online.incrementAndGet();
                Thread session = new Thread(
                        new LegacySession(config, keyPair, socket, online::get,
                                online::decrementAndGet),
                        "legacy-frontend-session");
                session.setDaemon(true);
                session.start();
            } catch (IOException e) {
                if (running) {
                    LOG.log(Level.FINE, "legacy accept failed", e);
                }
            }
        }
    }

    public int onlineCount() {
        return online.get();
    }

    public int port() {
        return config.port();
    }

    /** The actually bound port (differs from config when port 0 was used). */
    public int boundPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : config.port();
    }

    public boolean isRunning() {
        return running;
    }

    /** Stops listening (same as {@link #close()}). */
    public void stop() {
        close();
    }

    @Override
    public synchronized void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }
}
