package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.protocol.ClientProxy;
import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.ProxyAuthConfig;
import io.detergentmc.protocol.VarInt;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本物のバニラ1.20.4子プロセスに対するプロキシ統合テスト。
 *
 * <p>生ソケットで763クライアントを装い、ハンドシェイク→ログイン→
 * Join Game 受信→スポーン→チャット送信（バニラログに現れること）→
 * 移動送信までを確認する。実機の1.20.1クライアントではないが、
 * バニラが実際に送る設定フェーズ（Registry Data の NBT codec 形式など）
 * をプロキシが消費できることがこのテストで検証される。
 *
 * <p>本物のバニラjar（{@code run/vanilla/server-1.20.4.jar}）が無い環境では
 * スキップする。jar自体はダウンロードせず、偽Mojang（ローカルHTTP）が
 * 既存jarのSHA-1を返すだけである。
 */
@Timeout(600)
class ProxyIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * バニラ1.20.5／1.20.6／1.21／1.21.1は Java 21 必須のため、起動前に
     * Java 21 の場所をシステムプロパティに設定しておく。見つからなければ
     * VanillaServerHost が従来通り警告を出して Java 17 で起動しようとして
     * 失敗する（その場合はテストがタイムアウトで失敗する）。
     */
    @BeforeEach
    void ensureJava21() {
        Java21TestSupport.ensureJava21Home();
        Java25TestSupport.ensureJava25Home();
    }

    /** 既存のバニラjarを探す。無ければこのテストはスキップする。 */
    private static Path findRealVanillaJar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.20.4.jar",
                "../run/vanilla/server-1.20.4.jar",
                "../../run/vanilla/server-1.20.4.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.20.1のjarを探す。無ければ内部763の統合テストはスキップする。 */
    private static Path findRealVanilla1201Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.20.1.jar",
                "../run/vanilla/server-1.20.1.jar",
                "../../run/vanilla/server-1.20.1.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.20.2のjarを探す。無ければ逆向きの統合テストはスキップする。 */
    private static Path findRealVanilla1202Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.20.2.jar",
                "../run/vanilla/server-1.20.2.jar",
                "../../run/vanilla/server-1.20.2.jar",
                "/tmp/detergentmc-1202-boot/vanilla/server-1.20.2.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }



    /** 本物のバニラ1.19.3のjarを探す。無ければ内部761の統合テストはスキップする。 */
    private static Path findRealVanilla1193Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.19.3.jar",
                "../run/vanilla/server-1.19.3.jar",
                "../../run/vanilla/server-1.19.3.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.19.4のjarを探す。無ければ内部762の統合テストはスキップする。 */
    private static Path findRealVanilla1194Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.19.4.jar",
                "../run/vanilla/server-1.19.4.jar",
                "../../run/vanilla/server-1.19.4.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.19.1のjarを探す。無ければ内部760の統合テストはスキップする。 */
    private static Path findRealVanilla1191Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.19.1.jar",
                "../run/vanilla/server-1.19.1.jar",
                "../../run/vanilla/server-1.19.1.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.19.2のjarを探す。無ければ内部760の統合テストはスキップする。 */
    private static Path findRealVanilla1192Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.19.2.jar",
                "../run/vanilla/server-1.19.2.jar",
                "../../run/vanilla/server-1.19.2.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.19のjarを探す。無ければ内部759の統合テストはスキップする。 */
    private static Path findRealVanilla119Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.19.jar",
                "../run/vanilla/server-1.19.jar",
                "../../run/vanilla/server-1.19.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.18のjarを探す。無ければ内部757の統合テストはスキップする。 */
    private static Path findRealVanilla118Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.18.jar",
                "../run/vanilla/server-1.18.jar",
                "../../run/vanilla/server-1.18.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.17.1のjarを探す。無ければ内部756の統合テストはスキップする。 */
    private static Path findRealVanilla1171Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.17.1.jar",
                "../run/vanilla/server-1.17.1.jar",
                "../../run/vanilla/server-1.17.1.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.20.4のjarを探す（findRealVanillaJar の別名）。 */
    private static Path findRealVanilla1204Jar() {
        return findRealVanillaJar();
    }

    /** 本物のバニラ1.20.5のjarを探す。無ければ内部766の統合テストはスキップする。 */
    private static Path findRealVanilla1205Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.20.5.jar",
                "../run/vanilla/server-1.20.5.jar",
                "../../run/vanilla/server-1.20.5.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.21のjarを探す。無ければ内部767の統合テストはスキップする。 */
    private static Path findRealVanilla121Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.jar",
                "../run/vanilla/server-1.21.jar",
                "../../run/vanilla/server-1.21.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.21.1のjarを探す。無ければ 1.21.1 の統合テストはスキップする。 */
    private static Path findRealVanilla1211Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.1.jar",
                "../run/vanilla/server-1.21.1.jar",
                "../../run/vanilla/server-1.21.1.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }


    /** 本物のバニラ1.21.2のjarを探す。無ければ内部768の統合テストはスキップする。 */
    private static Path findRealVanilla1212Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.2.jar",
                "../run/vanilla/server-1.21.2.jar",
                "../../run/vanilla/server-1.21.2.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.21.3のjarを探す。無ければ 1.21.3 の統合テストはスキップする。 */
    private static Path findRealVanilla1213Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.3.jar",
                "../run/vanilla/server-1.21.3.jar",
                "../../run/vanilla/server-1.21.3.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }


    /** 本物のバニラ1.21.4のjarを探す。無ければ内部769の統合テストはスキップする。 */
    private static Path findRealVanilla1214Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.4.jar",
                "../run/vanilla/server-1.21.4.jar",
                "../../run/vanilla/server-1.21.4.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }


    /** 本物のバニラ1.21.5のjarを探す。無ければ内部770の統合テストはスキップする。 */
    private static Path findRealVanilla1215Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.5.jar",
                "../run/vanilla/server-1.21.5.jar",
                "../../run/vanilla/server-1.21.5.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }


    /** 本物のバニラ1.21.6のjarを探す。無ければ内部771の統合テストはスキップする。 */
    private static Path findRealVanilla1216Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.6.jar",
                "../run/vanilla/server-1.21.6.jar",
                "../../run/vanilla/server-1.21.6.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private static Path findRealVanilla1217Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.7.jar",
                "../run/vanilla/server-1.21.7.jar",
                "../../run/vanilla/server-1.21.7.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private static Path findRealVanilla1218Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.8.jar",
                "../run/vanilla/server-1.21.8.jar",
                "../../run/vanilla/server-1.21.8.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }


    /** 本物のバニラ1.21.9のjarを探す。無ければ内部773の統合テストはスキップする。 */
    private static Path findRealVanilla1219Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.9.jar",
                "../run/vanilla/server-1.21.9.jar",
                "../../run/vanilla/server-1.21.9.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.21.10のjarを探す。無ければ内部773の統合テストはスキップする。 */
    private static Path findRealVanilla12110Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.10.jar",
                "../run/vanilla/server-1.21.10.jar",
                "../../run/vanilla/server-1.21.10.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.21.11のjarを探す。無ければ内部774の統合テストはスキップする。 */
    private static Path findRealVanilla12111Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.11.jar",
                "../run/vanilla/server-1.21.11.jar",
                "../../run/vanilla/server-1.21.11.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private static Path findRealVanilla261Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-26.1.jar",
                "../run/vanilla/server-26.1.jar",
                "../../run/vanilla/server-26.1.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }


    /** 偽Mojang：マニフェストとバージョンJSONだけを返し、既存jarのSHA-1を名乗る。 */
    private static final class FakeMojang implements AutoCloseable {
        final HttpServer server;
        final Path jarPath;
        final String version;

        FakeMojang(Path jarPath) throws Exception {
            this(jarPath, "1.20.4");
        }

        FakeMojang(Path jarPath, String version) throws Exception {
            this.jarPath = jarPath;
            this.version = version;
            String jarSha1 = io.detergentmc.core.vanilla.VanillaDownloader.sha1(jarPath);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;
            server.createContext("/manifest.json", exchange -> {
                byte[] body = ("{\"versions\":[{\"id\": \"" + version + "\", \"type\": \"release\", "
                        + "\"url\": \"" + base + "/version.json\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                send(exchange, body, "application/json");
            });
            server.createContext("/version.json", exchange -> {
                byte[] body = ("{\"downloads\": {\"server\": {\"sha1\": \"" + jarSha1
                        + "\", \"size\": " + Files.size(jarPath)
                        + ", \"url\": \"" + base + "/server.jar\"}}}")
                        .getBytes(StandardCharsets.UTF_8);
                send(exchange, body, "application/json");
            });
            server.createContext("/server.jar", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                exchange.sendResponseHeaders(200, Files.size(jarPath));
                try (OutputStream out = exchange.getResponseBody()) {
                    Files.copy(jarPath, out);
                }
            });
            server.start();
        }

        String manifestUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/manifest.json";
        }

        private static void send(com.sun.net.httpserver.HttpExchange exchange,
                byte[] body, String contentType) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    /** 生ソケットの763クライアント。受信フレームをバックグラウンドで読み続ける。 */
    private static final class Fake763Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        Fake763Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-763-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    // 実機クライアントと同じく Keep Alive（0x23）に応答する。
                    // 応答しないとバニラが30秒でタイムアウト切断する。
                    if (packet.id() == 0x23) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x12, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }


        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


    /** 生ソケットの762クライアント。763と同じく設定フェーズが無い。 */
    private static final class Fake762Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        Fake762Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-762-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    // 実機クライアントと同じく Keep Alive（0x23）に応答する。
                    // 応答しないとバニラが30秒でタイムアウト切断する。
                    if (packet.id() == 0x23) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x12, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int current = in.read();
            if (current < 0) {
                throw new EOFException("VarIntの読み取り中にストリームが終わりました。");
            }
            value |= (current & 0x7F) << (7 * position);
            position++;
            if (position > 5) {
                throw new IOException("VarIntが長すぎます。");
            }
            if ((current & 0x80) == 0) {
                return value;
            }
        }
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(bytes, read, length - read);
            if (n < 0) {
                throw new EOFException("ストリームが途中で終わりました（"
                        + read + "/" + length + "バイト）。");
            }
            read += n;
        }
        return bytes;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static byte[] handshakePayload(int protocol, int nextState, int port) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(port);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    private static byte[] loginStart763(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }


    /** 生ソケットの757クライアント。設定フェーズが無い（1.20.2／764で追加）。 */
    private static final class Fake757Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        Fake757Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-757-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    // 実機クライアントと同じく Keep Alive（0x21）に応答する。
                    // 応答しないとバニラが30秒でタイムアウト切断する。
                    if (packet.id() == 0x21) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x0f, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** 生ソケットの756クライアント。設定フェーズが無い（1.20.2／764で追加）。 */
    private static final class Fake756Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        Fake756Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-756-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    // 実機クライアントと同じく Keep Alive（0x21）に応答する。
                    // 応答しないとバニラが30秒でタイムアウト切断する。
                    if (packet.id() == 0x21) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x0f, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** 生ソケットの759クライアント。760と同じく設定フェーズが無い。。761と同じく設定フェーズが無い。 */
    private static final class Fake759Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        Fake759Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-759-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    // 実機クライアントと同じく Keep Alive（0x1e）に応答する。
                    // 応答しないとバニラが30秒でタイムアウト切断する。
                    if (packet.id() == 0x1e) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x11, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** 生ソケットの760クライアント。761と同じく設定フェーズが無い。 */
    private static final class Fake760Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        Fake760Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-760-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    // 実機クライアントと同じく Keep Alive（0x20）に応答する。
                    // 応答しないとバニラが30秒でタイムアウト切断する。
                    if (packet.id() == 0x20) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x12, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** 生ソケットの761クライアント。762と同じく設定フェーズが無い。 */
    private static final class Fake761Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        Fake761Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-761-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    // 実機クライアントと同じく Keep Alive（0x1F）に応答する。
                    // 応答しないとバニラが30秒でタイムアウト切断する。
                    if (packet.id() == 0x1F) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x11, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static byte[] loginStart762(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    /** 実際の1.19.4クライアントと同じ、署名付きの完全な Chat Message 形式。 */
    private static byte[] chatMessage762(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeLong(System.currentTimeMillis());
        writer.writeLong(0L); // salt
        writer.writeBoolean(false); // 署名なし（offline想定。検証はしない）
        writer.writeVarInt(0); // offset
        writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged
        return writer.bytes();
    }

    private static byte[] move762() {
        Writer writer = new Writer();
        writer.writeDouble(0.5);
        writer.writeDouble(64.0);
        writer.writeDouble(0.5);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    private static byte[] loginStart761(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    /** 実際の1.19.3クライアントと同じ、署名付きの完全な Chat Message 形式。 */
    private static byte[] chatMessage761(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeLong(System.currentTimeMillis());
        writer.writeLong(0L); // salt
        writer.writeBoolean(false); // 署名なし（offline想定。検証はしない）
        writer.writeVarInt(0); // offset
        writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged
        return writer.bytes();
    }

    private static byte[] move761() {
        Writer writer = new Writer();
        writer.writeDouble(0.5);
        writer.writeDouble(64.0);
        writer.writeDouble(0.5);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** 760の Login Start：名前＋署名データ無し＋任意UUID。 */
    private static byte[] loginStart760(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false); // 署名データ無し
        writer.writeBoolean(true); // UUID あり
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    /** 実際の1.19.1／1.19.2クライアントと同じ、760形式の Chat Message。 */
    private static byte[] chatMessage760(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeLong(System.currentTimeMillis());
        writer.writeLong(0L); // salt
        writer.writeByteArray(new byte[0]); // 署名なし（offline想定。検証はしない）
        writer.writeBoolean(false); // signedPreview
        writer.writeVarInt(0); // previousMessages 0件
        writer.writeBoolean(false); // lastRejectedMessage 無し
        return writer.bytes();
    }

    private static byte[] move760() {
        Writer writer = new Writer();
        writer.writeDouble(0.5);
        writer.writeDouble(64.0);
        writer.writeDouble(0.5);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** 759の Login Start：名前＋署名データ無し（UUID欄そのものが無い）。 */
    private static byte[] loginStart759(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false); // 署名データ無し
        return writer.bytes();
    }

    /** 実際の1.19クライアントと同じ、759形式の Chat Message（初代インライン署名形式）。 */
    private static byte[] chatMessage759(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeLong(System.currentTimeMillis());
        writer.writeLong(0L); // salt
        writer.writeByteArray(new byte[0]); // 署名なし（offline想定。検証はしない）
        writer.writeBoolean(false); // signedPreview
        return writer.bytes();
    }

    private static byte[] move759() {
        Writer writer = new Writer();
        writer.writeDouble(0.5);
        writer.writeDouble(64.0);
        writer.writeDouble(0.5);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** 1.18の Login Start（0x00）。{username} のみ（署名オプションは1.19から）。 */
    private static byte[] loginStart757(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 1.18の Client Information（0x05）。765と同じ8フィールド。 */
    private static byte[] clientInformation757() {
        Writer writer = new Writer();
        writer.writeString("ja_JP");
        writer.writeByte(8); // 描画距離
        writer.writeVarInt(0); // チャットモード：全て表示
        writer.writeBoolean(true); // チャットカラー
        writer.writeUnsignedByte(0x7F); // 表示するスキンパーツ
        writer.writeVarInt(1); // メインハンド：右
        writer.writeBoolean(false); // テキストフィルタリング無効
        writer.writeBoolean(false); // サーバーリストへの表示を許可しない
        return writer.bytes();
    }

    /** 1.18の Chat（0x03）。署名なしでメッセージ文字列のみ。 */
    private static byte[] chatMessage757(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        return writer.bytes();
    }

    /** 1.18の Set Player Position（0x11）。759と同じ形式。 */
    private static byte[] move757() {
        return move759();
    }

    /** 1.17.1の Login Start（0x00）。{username} のみ（署名オプションは1.19から）。 */
    private static byte[] loginStart756(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /**
     * 1.17.1の Client Information（0x05）。7フィールド。
     * mc-1.17.1-protocol.json の packet_settings で確認：
     * locale／viewDistance／chatFlags／chatColors／skinParts／mainHand／
     * disableTextFiltering。757の8フィールド目（allowServerListings）は無い。
     */
    private static byte[] clientInformation756() {
        Writer writer = new Writer();
        writer.writeString("ja_JP");
        writer.writeByte(8); // 描画距離
        writer.writeVarInt(0); // チャットモード：全て表示
        writer.writeBoolean(true); // チャットカラー
        writer.writeUnsignedByte(0x7F); // 表示するスキンパーツ
        writer.writeVarInt(1); // メインハンド：右
        writer.writeBoolean(false); // テキストフィルタリング無効
        return writer.bytes();
    }

    /** 1.17.1の Chat（0x03）。署名なしでメッセージ文字列のみ。 */
    private static byte[] chatMessage756(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        return writer.bytes();
    }

    /** 1.17.1の Set Player Position（0x11）。757と同じ形式。 */
    private static byte[] move756() {
        return move759();
    }

    /** 実際の1.20.1クライアントと同じ、署名付きの完全な Chat Message 形式。 */
    private static byte[] chatMessage763(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        writer.writeLong(System.currentTimeMillis());
        writer.writeLong(0L); // salt
        writer.writeBoolean(false); // 署名なし（offline想定。検証はしない）
        writer.writeVarInt(0); // offset
        writer.writeBytes(new byte[]{0, 0, 0}); // acknowledged
        return writer.bytes();
    }

    private static byte[] move763() {
        Writer writer = new Writer();
        writer.writeDouble(0.5);
        writer.writeDouble(64.0);
        writer.writeDouble(0.5);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** 生ソケットの765クライアント。763とは違い、設定フェーズを自分で話す。 */
    private static final class Fake765Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;

        Fake765Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-765-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x03) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x03, response.bytes()));
                        } else if (packet.id() == 0x02) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x02, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x07) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x24) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x15, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static byte[] loginStart765(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    /** 生ソケットの764クライアント。765とは違い、設定フェーズのパケットIDが764のもの。 */
    private static final class Fake764Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x05）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake764Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-764-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x05) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x03) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x03, response.bytes()));
                        } else if (packet.id() == 0x02) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x02, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x06) { // Resource Pack Send（設定）
                            Writer response = new Writer();
                            response.writeVarInt(3); // accepted（764は結果のみ）
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x24) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x14, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** 764の Login Start：765と同じ形式（名前＋UUID、bool なし）。 */
    private static byte[] loginStart764(String name) {
        return loginStart765(name);
    }

    /** 764の設定フェーズの Client Information（0x00）。765と同じ形式。 */
    private static byte[] clientInformation764() {
        return clientInformation765();
    }

    /** 764の Chat Message（0x05）。763・765と同じ署名付きの完全な形式。 */
    private static byte[] chatMessage764(String message) {
        return chatMessage763(message);
    }

    /** 764の Set Player Position（0x16）。 */
    private static byte[] move764() {
        return move763();
    }

    /** 設定フェーズの Client Information（0x00）。 */
    private static byte[] clientInformation765() {
        Writer writer = new Writer();
        writer.writeString("ja_JP");
        writer.writeByte(8); // 描画距離
        writer.writeVarInt(0); // チャットモード：全て表示
        writer.writeBoolean(true); // チャットカラー
        writer.writeUnsignedByte(0x7F); // 表示するスキンパーツ
        writer.writeVarInt(1); // メインハンド：右
        writer.writeBoolean(false); // テキストフィルタリング無効
        writer.writeBoolean(false); // サーバーリストへの表示を許可しない
        return writer.bytes();
    }


    /** 768の設定フェーズの Client Information（0x00）。767形式に particleStatus=0（all）を付けたもの。 */
    private static byte[] clientInformation768() {
        byte[] base = clientInformation766();
        Writer writer = new Writer();
        writer.writeBytes(base);
        writer.writeVarInt(0); // particleStatus: all
        return writer.bytes();
    }


    /** 769の設定フェーズの Client Information（0x00）。767形式に particleStatus=0（all）を付けたもの。 */
    private static byte[] clientInformation769() {
        byte[] base = clientInformation766();
        Writer writer = new Writer();
        writer.writeBytes(base);
        writer.writeVarInt(0); // particleStatus: all
        return writer.bytes();
    }


    /** 770の設定フェーズの Client Information（0x00）。769と同じ9フィールド。 */
    private static byte[] clientInformation770() {
        byte[] base = clientInformation766();
        Writer writer = new Writer();
        writer.writeBytes(base);
        writer.writeVarInt(0); // particleStatus: all
        return writer.bytes();
    }

    /** 770の Chat Message（0x07）。769形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage770(String message) {
        byte[] base = chatMessage763(message);
        Writer writer = new Writer();
        writer.writeBytes(base);
        writer.writeByte(0); // checksum（0x00。受け入れられるかは本物のバニラで検証）
        return writer.bytes();
    }


    /** 771の設定フェーズの Client Information（0x00）。770と同じ9フィールド。 */
    private static byte[] clientInformation771() {
        return clientInformation770();
    }

    /** 771の Chat Message（0x08）。770と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage771(String message) {
        return chatMessage770(message);
    }

    /** 772の設定フェーズの Client Information（0x00）。771と同じ9フィールド。 */
    private static byte[] clientInformation772() {
        return clientInformation771();
    }

    /** 772の Chat Message（0x08）。771と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage772(String message) {
        return chatMessage771(message);
    }


    /** 773の設定フェーズの Client Information（0x00）。772と同じ9フィールド。 */
    private static byte[] clientInformation773() {
        return clientInformation772();
    }

    /** 773の Chat Message（0x08）。772と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage773(String message) {
        return chatMessage772(message);
    }

    /** 774の設定フェーズの Client Information（0x00）。773と同じ9フィールド。 */
    private static byte[] clientInformation774() {
        return clientInformation773();
    }

    /** 774の Chat Message（0x08）。773と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage774(String message) {
        return chatMessage773(message);
    }

    /** 775の設定フェーズの Client Information（0x00）。774と同じ9フィールド。 */
    private static byte[] clientInformation775() {
        return clientInformation774();
    }

    /** 775の Chat Message（0x09）。774と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage775(String message) {
        return chatMessage774(message);
    }


    /** 765の Chat Message（0x05）。763と同じ署名付きの完全な形式。 */
    private static byte[] chatMessage765(String message) {
        return chatMessage763(message);
    }

    /** 765の Set Player Position（0x17）。 */
    private static byte[] move765() {
        return move763();
    }

    /** 766の Login Start：765と同じ形式（名前＋UUID、bool なし）。 */
    private static byte[] loginStart766(String name) {
        return loginStart765(name);
    }

    /** 766の設定フェーズの Client Information（0x00）。765と同じ形式。 */
    private static byte[] clientInformation766() {
        return clientInformation765();
    }

    /** 766の Chat Message（0x06）。763・765と同じ署名付きの完全な形式。 */
    private static byte[] chatMessage766(String message) {
        return chatMessage763(message);
    }

    /** 766の Set Player Position（0x1a）。 */
    private static byte[] move766() {
        return move763();
    }

    /** 生ソケットの766クライアント。設定フェーズ・プレイのパケットIDは766のもの。 */
    private static final class Fake766Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake766Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-766-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x26) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x18, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x35) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x27, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


    /** 生ソケットの767クライアント。設定フェーズ・プレイのパケットIDは766と同じ。
     * 766との違いは Select Known Packs（0x0E）に空の一覧で答えること。
     * ネイティブ 767＋767 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21 サーバーが先に進まない。 */
    private static final class Fake767Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake767Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-767-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x26) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x18, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x35) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x27, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


    /** 生ソケットの768クライアント。設定フェーズの番号は767と同じ。
     * プレイの番号が767と大きく変わる（Join Game 0x2C、Spawn Position 0x5B、
     * Keep Alive 0x27→応答 0x1A、Ping 0x37→応答 0x29）。
     * Client Information は 768 形式（末尾に particleStatus）で送る。
     * ネイティブ 768＋768 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21.2 サーバーが先に進まない。 */
    private static final class Fake768Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake768Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-768-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x27) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1A, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x37) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x29, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


    /** 生ソケットの769クライアント。設定フェーズ・プレイの番号は768と同じ。
     * （Join Game 0x2C、Spawn Position 0x5B、Keep Alive 0x27→応答 0x1A、
     * Ping 0x37→応答 0x29。Client Information は 769 形式（末尾に
     * particleStatus）で送る。）
     * ネイティブ 769＋769 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21.4 サーバーが先に進まない。 */
    private static final class Fake769Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake769Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-769-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x27) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1A, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x37) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x29, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


    /** 生ソケットの770クライアント。設定フェーズの番号は769と同じ。
     * （Join Game 0x2B、Spawn Position 0x5A、Keep Alive 0x26→応答 0x1A、
     * Ping 0x36→応答 0x2B。Client Information は 770 形式（末尾に
     * particleStatus）で送る。Chat Message は末尾に checksum 0x00 を付ける。）
     * ネイティブ 770＋770 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21.5 サーバーが先に進まない。 */
    private static final class Fake770Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake770Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-770-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x26) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1A, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x36) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x2B, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


    /** 生ソケットの771クライアント。設定フェーズの番号は770と同じ。
     * （Join Game 0x2B、Spawn Position 0x5A、Keep Alive 0x26→応答 0x1B、
     * Ping 0x36→応答 0x2C。Client Information は 771 形式（770 と同じ
     * 9フィールド）で送る。Chat Message は 0x08、末尾に checksum 0x00 を付ける。
     * Set Player Position は 0x1D。）
     * ネイティブ 771＋771 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21.6 サーバーが先に進まない。 */
    private static final class Fake771Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake771Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-771-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x26) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1B, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x36) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x2C, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


    private static final class Fake772Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake772Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-772-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x26) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1B, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x36) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x2C, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }


    /** 生ソケットの773クライアント。設定フェーズの番号は772と同じ。
     * （Join Game 0x30、Spawn Position 0x5F、Keep Alive 0x2B→応答 0x1B、
     * Ping 0x3B→応答 0x2C。Client Information は 773 形式（772 と同じ
     * 9フィールド）で送る。Chat Message は 0x08、末尾に checksum 0x00 を付ける。
     * Set Player Position は 0x1D。）
     * 設定フェーズで Code of Conduct（0x13）が来たら Accept（0x09、空）を
     * 返す。既定のオフラインバニラ 1.21.9 は CoC を送ってこないが、
     * CoC を送るサーバーに対する備えである。
     * ネイティブ 773＋773 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21.9 サーバーが先に進まない。 */
    private static final class Fake773Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake773Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-773-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        } else if (packet.id() == 0x13) { // Code of Conduct
                            // Accept Code of Conduct（0x09、空ペイロード）を返す。
                            sendPacket(new MinecraftPacket(0x09, new byte[0]));
                        }
                    } else if (state == 2 && packet.id() == 0x2B) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1B, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x3B) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x2C, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** 生ソケットの774クライアント。設定フェーズの番号は773と同じ。
     * （Join Game 0x30、Spawn Position 0x5F、Keep Alive 0x2B→応答 0x1B、
     * Ping 0x3B→応答 0x2C。Client Information は 774 形式（772 と同じ
     * 9フィールド）で送る。Chat Message は 0x08、末尾に checksum 0x00 を付ける。
     * Set Player Position は 0x1D。）
     * 設定フェーズで Code of Conduct（0x13）が来たら Accept（0x09、空）を
     * 返す。既定のオフラインバニラ 1.21.11 は CoC を送ってこないが、
     * CoC を送るサーバーに対する備えである。
     * ネイティブ 774＋774 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21.11 サーバーが先に進まない。 */
    private static final class Fake774Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake774Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-774-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        } else if (packet.id() == 0x13) { // Code of Conduct
                            // Accept Code of Conduct（0x09、空ペイロード）を返す。
                            sendPacket(new MinecraftPacket(0x09, new byte[0]));
                        }
                    } else if (state == 2 && packet.id() == 0x2B) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1B, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x3B) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x2C, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /** 生ソケットの775クライアント。設定フェーズの番号は773と同じ。
     * （Join Game 0x31、Spawn Position 0x61、Keep Alive 0x2C→応答 0x1C、
     * Ping 0x3D→応答 0x2D。Client Information は 775 形式（774 と同じ
     * 9フィールド）で送る。Chat Message は 0x09、末尾に checksum 0x00 を付ける。
     * Set Player Position は 0x1E。）
     * 設定フェーズで Code of Conduct（0x13）が来たら Accept（0x09、空）を
     * 返す。既定のオフラインバニラ 1.21.11 は CoC を送ってこないが、
     * CoC を送るサーバーに対する備えである。
     * ネイティブ 775＋775 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 26.1 サーバーが先に進まない。 */
    private static final class Fake775Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake775Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-775-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) { // Registry Data（設定フェーズ）
                            receivedRegistryData = true;
                        }
                        if (packet.id() == 0x04) { // 設定フェーズの Keep Alive
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) { // 設定フェーズの Ping
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) { // Add Resource Pack
                            UUID uuid = new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0); // 知っているパックは無い
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        } else if (packet.id() == 0x13) { // Code of Conduct
                            // Accept Code of Conduct（0x09、空ペイロード）を返す。
                            sendPacket(new MinecraftPacket(0x09, new byte[0]));
                        }
                    } else if (state == 2 && packet.id() == 0x2C) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1C, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x3D) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x2D, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                closed = true;
            }
        }

        private MinecraftPacket readPacket() throws IOException {
            int length = readVarInt(in);
            byte[] frame = readExact(in, length);
            byte[] lengthBytes = VarInt.encode(length);
            byte[] all = new byte[lengthBytes.length + frame.length];
            System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
            System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
            return MinecraftPacket.decodeOne(all, 0).packet();
        }

        /** Login Acknowledged を送る前に呼ぶ（設定フェーズのパケットが先に届く競合を防ぐ）。 */
        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        /** 指定IDのパケットが届くまで待つ（他のパケットは捨てる）。 */
        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        boolean isClosed() {
            return closed || socket.isClosed();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static boolean contains(byte[] bytes, String text) {
        byte[] needle = text.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i + needle.length <= bytes.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    void fake763ClientJoinsRealVanillaThroughProxy() throws Exception {
        Path realJar = findRealVanillaJar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(vanillaDir.resolve("server-1.20.4.jar"))) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false);
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（763形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（763形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage763("hello from 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 765クライアントの回帰テスト。プロキシ経由で本物のバニラに参加し、
     * Join Game・スポーン・チャット・移動が素通し（リレー）で動くことを確認する。
     */
    @Test
    void fake765ClientJoinsRealVanillaThroughProxy() throws Exception {
        Path realJar = findRealVanillaJar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(vanillaDir.resolve("server-1.20.4.jar"))) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Alex")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Alex")
                        && chat.message().equals("hello from 1.20.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false);
                proxy.start();
                try (Fake765Client client = new Fake765Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(765, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart765("Alex")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation765()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（765形式 0x29）が届きませんでした。");

                    MinecraftPacket spawn = client.waitForId(0x54, 30_000);
                    assertTrue(spawn != null, "Spawn Position（765形式 0x54）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage765("hello from 1.20.4")));
                    client.sendPacket(new MinecraftPacket(0x17, move765()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Alex の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 764クライアントの統合テスト。プロキシ経由で本物のバニラに参加し、
     * Join Game・スポーン・チャット・移動が動くことを確認する。
     * 設定フェーズはプロキシが中継する（763のような codec 埋め込みは無い）。
     */
    @Test
    void fake764ClientJoinsRealVanillaThroughProxy() throws Exception {
        Path realJar = findRealVanillaJar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(vanillaDir.resolve("server-1.20.4.jar"))) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Noah")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Noah")
                        && chat.message().equals("hello from 1.20.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false);
                proxy.start();
                try (Fake764Client client = new Fake764Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(764, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart764("Noah")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation764()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（764形式 0x29）が届きませんでした。");
                    // 764の Join Game には codec が無い（レジストリは設定フェーズの
                    // Registry Data で届く）。代わりに次元名が入っていることと、
                    // 設定フェーズで Registry Data を受け取ったことを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x05）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x52, 30_000);
                    assertTrue(spawn != null, "Spawn Position（764形式 0x52）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage764("hello from 1.20.2")));
                    client.sendPacket(new MinecraftPacket(0x16, move764()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Noah の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 逆向き対応の統合テスト：偽765クライアントがプロキシ経由で本物の
     * バニラ1.20.2（内部764）に参加し、Join Game・スポーン・チャット・
     * 移動が下向き変換（765→764）で動くことを確認する。設定フェーズは
     * 764の Registry Data などをプロキシが765形式へ変換して中継する。
     * プロキシは内部プロトコル764で構築する（9引数版コンストラクタ）。
     */
    @Test
    void fake765ClientJoinsRealVanilla1202ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1202Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.2.jar"), "1.20.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Rin")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Rin")
                        && chat.message().equals("hello from 1.20.4 to 1.20.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, 764);
                assertEquals(764, proxy.internalProtocol());
                proxy.start();
                try (Fake765Client client = new Fake765Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(765, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart765("Rin")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation765()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（765形式 0x29）が届きませんでした。");
                    // 764の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x54, 30_000);
                    assertTrue(spawn != null, "Spawn Position（765形式 0x54）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage765("hello from 1.20.4 to 1.20.2")));
                    client.sendPacket(new MinecraftPacket(0x17, move765()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Rin の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 1.20系相互連携の見出し事例：偽766クライアント（1.20.5／1.20.6）が
     * プロキシ経由で本物のバニラ1.20.1（内部763）に参加する。内部763には
     * 設定フェーズが無いため、プロキシが Join Game の codec から
     * Registry Data（766形式 0x07）と Finish Configuration を合成して送る。
     * Join Game・スポーン・チャット・移動が下向き変換（766→763）で動く
     * ことを確認する。プロキシは内部プロトコル763で構築する（10引数版
     * コンストラクタ。以前は ClientProxy が763を拒否していた）。
     */
    @Test
    void fake766ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.20.5 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake766Client client = new Fake766Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(766, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（766形式 0x2B）が届きませんでした。");
                    // 766の Join Game に codec は無い（レジストリは合成した
                    // 設定フェーズの Registry Data で届く）。合成された
                    // Registry Data を受け取ったことを確認する。
                    assertTrue(client.receivedRegistryData,
                            "合成された設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（766形式 0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.20.5 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽766クライアント（1.20.5／1.20.6）がプロキシ経由で本物の
     * バニラ1.20.2（内部764）に参加し、Join Game・スポーン・チャット・
     * 移動が下向き変換（766→764）で動くことを確認する。設定フェーズは
     * 764の Registry Data などをプロキシが766形式へ変換して中継する。
     */
    @Test
    void fake766ClientJoinsRealVanilla1202ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1202Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.2.jar"), "1.20.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.20.5 to 1.20.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 764);
                assertEquals(764, proxy.internalProtocol());
                proxy.start();
                try (Fake766Client client = new Fake766Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(766, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Sora")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（766形式 0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（766形式 0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.20.5 to 1.20.2")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Sora の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽763クライアント（1.20.1）がプロキシ経由で本物のバニラ1.20.2
     * （内部764）に参加し、Join Game・スポーン・チャット・移動が
     * 763→764の直接変換で動くことを確認する（763→765→764 の二段変換は
     * しない。プレイ・ペイロード変換は担当3の ExtraPlayTranslator／
     * PlayTranslator による）。設定フェーズはプロキシが764クライアント役
     * として消費する。
     */
    @Test
    void fake763ClientJoinsRealVanilla1202ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1202Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.2.jar"), "1.20.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.20.1 to 1.20.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, 764);
                assertEquals(764, proxy.internalProtocol());
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（763形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（763形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage763("hello from 1.20.1 to 1.20.2")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽767クライアント（1.21／1.21.1）がプロキシ経由で本物のバニラ1.21
     * （内部767）に参加し、Join Game・スポーン・チャット・移動が素通しで
     * 動くことを確認する。設定フェーズは 767 の Registry Data などを
     * プロキシがそのまま中継する（Select Known Packs への返事も含む）。
     */
    @Test
    void fake767ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽767クライアントがプロキシ経由で本物のバニラ1.21.1（内部767）に
     * 参加する。1.21.1 もプロトコル767のため 1.21 と同じ流れで動くこと。
     */
    @Test
    void fake767ClientJoinsRealVanilla1211ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1211Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.1.jar"), "1.21.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.21 to 1.21.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Sora")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.21.1")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Sora の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽767クライアントがプロキシ経由で本物のバニラ1.20.5（内部766）に
     * 参加し、Join Game・スポーン・チャット・移動が 766→767 の上向き変換で
     * 動くことを確認する。設定フェーズは 766 の Registry Data を
     * 767 の順序に並べ替えて中継する。
     */
    @Test
    void fake767ClientJoinsRealVanilla1205ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1205Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.5.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.5.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.5.jar"), "1.20.5")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.5", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Rin")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Rin")
                        && chat.message().equals("hello from 1.21 to 1.20.5")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 766);
                assertEquals(766, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Rin")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.20.5")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Rin の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽766クライアント（1.20.5／1.20.6）がプロキシ経由で本物のバニラ1.21
     * （内部767）に参加し、Join Game・スポーン・チャット・移動が 767→766 の
     * 下向き変換で動くことを確認する。設定フェーズは 767 の Registry Data を
     * 766 の順序に並べ替えて中継する（jukebox_song は落とす）。
     */
    @Test
    void fake766ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.20.5 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake766Client client = new Fake766Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(766, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Hana")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.20.5 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽767クライアントがプロキシ経由で本物のバニラ1.20.4（内部765）に
     * 参加し、Join Game・スポーン・チャット・移動が 765→767 の上向き変換で
     * 動くことを確認する。設定フェーズは 765 の codec 形式 Registry Data を
     * 767 のエントリ配列形式に変換して中継する。
     */
    @Test
    void fake767ClientJoinsRealVanilla1204ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1204Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.4.jar"), "1.20.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Aoi")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Aoi")
                        && chat.message().equals("hello from 1.21 to 1.20.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 765);
                assertEquals(765, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Aoi")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.20.4")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Aoi の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽765クライアント（1.20.3／1.20.4）がプロキシ経由で本物のバニラ1.21
     * （内部767）に参加し、Join Game・スポーン・チャット・移動が 767→765 の
     * 下向き変換で動くことを確認する。設定フェーズは 767 の Registry Data を
     * 765 の codec 形式に変換して中継する。
     */
    @Test
    void fake765ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Rin")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Rin")
                        && chat.message().equals("hello from 1.20.4 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake765Client client = new Fake765Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(765, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart765("Rin")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation765()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x29）が届きませんでした。");
                    // 767の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x54, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x54）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage765("hello from 1.20.4 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x17, move765()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Rin の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽767クライアントがプロキシ経由で本物のバニラ1.20.2（内部764）に
     * 参加し、Join Game・スポーン・チャット・移動が 764→767 の上向き変換で
     * 動くことを確認する。設定フェーズは 764 の Registry Data を
     * 767 のエントリ配列形式に変換して中継する。
     */
    @Test
    void fake767ClientJoinsRealVanilla1202ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1202Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.2.jar"), "1.20.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.21 to 1.20.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 764);
                assertEquals(764, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Sora")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.20.2")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Sora の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽764クライアント（1.20.2）がプロキシ経由で本物のバニラ1.21
     * （内部767）に参加し、Join Game・スポーン・チャット・移動が 767→764 の
     * 下向き変換で動くことを確認する。設定フェーズは 767 の Registry Data を
     * 764 の codec 形式に変換して中継する。
     */
    @Test
    void fake764ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Aoi")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Aoi")
                        && chat.message().equals("hello from 1.20.2 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake764Client client = new Fake764Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(764, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart764("Aoi")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation764()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x29）が届きませんでした。");
                    // 764の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x05）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x52, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x52）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage764("hello from 1.20.2 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x16, move764()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Aoi の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽763クライアント（1.20.1）がプロキシ経由で本物のバニラ1.21
     * （内部767）に参加する。内部767には設定フェーズがあり、プロキシは
     * 767の Registry Data などを消費して 763 の Join Game（0x28）に
     * codec を合成する。Join Game・スポーン・チャット・移動が
     * 767→763 の下向き変換で動くことを確認する。
     */
    @Test
    void fake763ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.20.1 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（763形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（763形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage763("hello from 1.20.1 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽768クライアント（1.21.2／1.21.3）がプロキシ経由で本物のバニラ1.21.2
     * （内部768）に参加し、Join Game・スポーン・チャット・移動が素通しで
     * 動くことを確認する。設定フェーズは 768 の Registry Data などを
     * プロキシがそのまま中継する（Select Known Packs への返事も含む）。
     */
    @Test
    void fake768ClientJoinsRealVanilla1212ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1212Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.2.jar"), "1.21.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.2 to 1.21.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 768);
                assertEquals(768, proxy.internalProtocol());
                proxy.start();
                try (Fake768Client client = new Fake768Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(768, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation768()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.2 to 1.21.2")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽768クライアントがプロキシ経由で本物のバニラ1.21.3（内部768）に
     * 参加する。1.21.3 もプロトコル768のため 1.21.2 と同じ流れで動くこと。
     */
    @Test
    void fake768ClientJoinsRealVanilla1213ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1213Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.3.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.3.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.3.jar"), "1.21.3")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.3", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.21.2 to 1.21.3")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 768);
                assertEquals(768, proxy.internalProtocol());
                proxy.start();
                try (Fake768Client client = new Fake768Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(768, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Sora")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation768()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.2 to 1.21.3")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Sora の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽768クライアントがプロキシ経由で本物のバニラ1.21（内部767）に
     * 参加し、Join Game・スポーン・チャット・移動が 767→768 の上向き変換で
     * 動くことを確認する。設定フェーズは 767 の Registry Data を
     * 768 の順序と形式に変換して中継する。
     */
    @Test
    void fake768ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Rin")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Rin")
                        && chat.message().equals("hello from 1.21.2 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake768Client client = new Fake768Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(768, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Rin")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation768()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.2 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Rin の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽767クライアント（1.21／1.21.1）がプロキシ経由で本物のバニラ1.21.2
     * （内部768）に参加し、Join Game・スポーン・チャット・移動が 768→767 の
     * 下向き変換で動くことを確認する。設定フェーズは 768 の Registry Data を
     * 767 の順序と形式に変換して中継する。
     */
    @Test
    void fake767ClientJoinsRealVanilla1212ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1212Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.2.jar"), "1.21.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.21 to 1.21.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 768);
                assertEquals(768, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Hana")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.21.2")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽768クライアントがプロキシ経由で本物のバニラ1.20.5（内部766）に
     * 参加し、Join Game・スポーン・チャット・移動が 766→768 の上向き変換で
     * 動くことを確認する。設定フェーズは 766 の Registry Data を
     * 768 の順序と形式に変換して中継する。
     */
    @Test
    void fake768ClientJoinsRealVanilla1205ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1205Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.5.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.5.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.5.jar"), "1.20.5")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.5", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Aoi")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Aoi")
                        && chat.message().equals("hello from 1.21.2 to 1.20.5")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 766);
                assertEquals(766, proxy.internalProtocol());
                proxy.start();
                try (Fake768Client client = new Fake768Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(768, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Aoi")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation768()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.2 to 1.20.5")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Aoi の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽766クライアント（1.20.5／1.20.6）がプロキシ経由で本物のバニラ1.21.2
     * （内部768）に参加し、Join Game・スポーン・チャット・移動が 768→766 の
     * 下向き変換で動くことを確認する。設定フェーズは 768 の Registry Data を
     * 766 の順序と形式に変換して中継する。
     */
    @Test
    void fake766ClientJoinsRealVanilla1212ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1212Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.2.jar"), "1.21.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.20.5 to 1.21.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 768);
                assertEquals(768, proxy.internalProtocol());
                proxy.start();
                try (Fake766Client client = new Fake766Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(766, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.20.5 to 1.21.2")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽768クライアントがプロキシ経由で本物のバニラ1.20.4（内部765）に
     * 参加し、Join Game・スポーン・チャット・移動が 765→768 の上向き変換で
     * 動くことを確認する。設定フェーズは 765 の codec 形式 Registry Data を
     * 768 のエントリ配列形式に変換して中継する。
     */
    @Test
    void fake768ClientJoinsRealVanilla1204ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1204Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.4.jar"), "1.20.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.21.2 to 1.20.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 765);
                assertEquals(765, proxy.internalProtocol());
                proxy.start();
                try (Fake768Client client = new Fake768Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(768, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Sora")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation768()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.2 to 1.20.4")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Sora の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽765クライアント（1.20.3／1.20.4）がプロキシ経由で本物のバニラ1.21.2
     * （内部768）に参加し、Join Game・スポーン・チャット・移動が 768→765 の
     * 下向き変換で動くことを確認する。設定フェーズは 768 の Registry Data を
     * 765 の codec 形式に変換して中継する。
     */
    @Test
    void fake765ClientJoinsRealVanilla1212ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1212Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.2.jar"), "1.21.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Rin")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Rin")
                        && chat.message().equals("hello from 1.20.4 to 1.21.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 768);
                assertEquals(768, proxy.internalProtocol());
                proxy.start();
                try (Fake765Client client = new Fake765Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(765, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart765("Rin")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation765()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x29）が届きませんでした。");
                    // 767の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x54, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x54）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage765("hello from 1.20.4 to 1.21.2")));
                    client.sendPacket(new MinecraftPacket(0x17, move765()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Rin の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽768クライアントがプロキシ経由で本物のバニラ1.20.2（内部764）に
     * 参加し、Join Game・スポーン・チャット・移動が 764→768 の上向き変換で
     * 動くことを確認する。設定フェーズは 764 の Registry Data を
     * 768 のエントリ配列形式に変換して中継する。
     */
    @Test
    void fake768ClientJoinsRealVanilla1202ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1202Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.2.jar"), "1.20.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Aoi")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Aoi")
                        && chat.message().equals("hello from 1.21.2 to 1.20.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 764);
                assertEquals(764, proxy.internalProtocol());
                proxy.start();
                try (Fake768Client client = new Fake768Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(768, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Aoi")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation768()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.2 to 1.20.2")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Aoi の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽764クライアント（1.20.2）がプロキシ経由で本物のバニラ1.21.2
     * （内部768）に参加し、Join Game・スポーン・チャット・移動が 768→764 の
     * 下向き変換で動くことを確認する。設定フェーズは 768 の Registry Data を
     * 764 の codec 形式に変換して中継する。
     */
    @Test
    void fake764ClientJoinsRealVanilla1212ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1212Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.2.jar"), "1.21.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.20.2 to 1.21.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 768);
                assertEquals(768, proxy.internalProtocol());
                proxy.start();
                try (Fake764Client client = new Fake764Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(764, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart764("Sora")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation764()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x29）が届きませんでした。");
                    // 764の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x05）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x52, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x52）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage764("hello from 1.20.2 to 1.21.2")));
                    client.sendPacket(new MinecraftPacket(0x16, move764()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Sora の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽763クライアント（1.20.1）がプロキシ経由で本物のバニラ1.21.2
     * （内部768）に参加する。内部768には設定フェーズがあり、プロキシは
     * 768の Registry Data などを消費して 763 の Join Game（0x28）に
     * codec を合成する。Join Game・スポーン・チャット・移動が
     * 768→763 の下向き変換で動くことを確認する。
     */
    @Test
    void fake763ClientJoinsRealVanilla1212ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1212Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.2.jar"), "1.21.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.20.1 to 1.21.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 768);
                assertEquals(768, proxy.internalProtocol());
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（763形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（763形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage763("hello from 1.20.1 to 1.21.2")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽768クライアント（1.21.2／1.21.3）がプロキシ経由で本物のバニラ1.20.1
     * （内部763）に参加する。内部763には設定フェーズが無いため、プロキシは
     * 763 の Join Game の codec から 768 向けの設定フェーズ（Registry Data
     * 0x07・Finish Configuration 0x03）を合成する。Join Game・スポーン・
     * チャット・移動が 763→768 の上向き変換で動くことを確認する。
     */
    @Test
    void fake768ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.2 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake768Client client = new Fake768Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(768, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");
                    // 768の Login Success は strictErrorHandling が無い。末尾に
                    // 余分な bool が無いこと（UUID・名前・属性を読んだ後に余りが無いこと）
                    // を確かめる。Login Success は UUID が先、名前が後。
                    PacketIO.Reader loginReader =
                            new PacketIO.Reader(loginSuccess.payload());
                    loginReader.readUuid();
                    assertEquals("Yuki", loginReader.readString());
                    assertEquals(0, loginReader.readVarInt());
                    assertEquals(0, loginReader.remaining());

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation768()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.2 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽769クライアント（1.21.4）がプロキシ経由で本物のバニラ1.21.4
     * （内部769）に参加し、Join Game・スポーン・チャット・移動が素通しで
     * 動くことを確認する。設定フェーズは 769 の Registry Data などを
     * プロキシがそのまま中継する（Select Known Packs への返事も含む）。
     */
    @Test
    void fake769ClientJoinsRealVanilla1214ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1214Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.4.jar"), "1.21.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.4 to 1.21.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 769);
                assertEquals(769, proxy.internalProtocol());
                proxy.start();
                try (Fake769Client client = new Fake769Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(769, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation769()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.4 to 1.21.4")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }


    /**
     * 偽770クライアント（1.21.5）がプロキシ経由で本物のバニラ1.21.5
     * （内部770）に参加し、Join Game・スポーン・チャット・移動が素通しで
     * 動くことを確認する。設定フェーズは 770 の Registry Data などを
     * プロキシがそのまま中継する（Select Known Packs への返事も含む）。
     * チャットの checksum 0x00 が本物の 1.21.5 サーバーに受け入れられるかも
     * このテストで検証する（切断されれば失敗する）。
     */
    @Test
    void fake770ClientJoinsRealVanilla1215ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1215Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.5.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.5.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.5.jar"), "1.21.5")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.5", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.5 to 1.21.5")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 770);
                assertEquals(770, proxy.internalProtocol());
                proxy.start();
                try (Fake770Client client = new Fake770Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(770, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation770()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage770("hello from 1.21.5 to 1.21.5")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽770クライアントがプロキシ経由で本物のバニラ1.21.4（内部769）に
     * 参加し、Join Game・スポーン・チャット・移動が 770→769 の下向き変換で
     * 動くことを確認する。チャットの checksum は769向けに落とす。
     * 設定フェーズは 769 の Registry Data を 770 の順序と形式に変換する。
     */
    @Test
    void fake770ClientJoinsRealVanilla1214ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1214Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.4.jar"), "1.21.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.5 to 1.21.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 769);
                assertEquals(769, proxy.internalProtocol());
                proxy.start();
                try (Fake770Client client = new Fake770Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(770, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation770()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage770("hello from 1.21.5 to 1.21.4")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽769クライアントがプロキシ経由で本物のバニラ1.21.5（内部770）に
     * 参加し、Join Game・スポーン・チャット・移動が 769→770 の上向き変換で
     * 動くことを確認する。チャットの checksum はプロキシが 0x00 で補う。
     * 設定フェーズは 770 の Registry Data を 769 の順序と形式に変換する。
     */
    @Test
    void fake769ClientJoinsRealVanilla1215ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1215Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.5.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.5.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.5.jar"), "1.21.5")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.5", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.4 to 1.21.5")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 770);
                assertEquals(770, proxy.internalProtocol());
                proxy.start();
                try (Fake769Client client = new Fake769Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(769, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation769()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.4 to 1.21.5")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }


    /**
     * 偽771クライアント（1.21.6）がプロキシ経由で本物のバニラ1.21.6
     * （内部771）に参加し、Join Game・スポーン・チャット・移動が素通しで
     * 動くことを確認する。設定フェーズは 771 の Registry Data などを
     * プロキシがそのまま中継する（Select Known Packs への返事も含む）。
     * チャットの checksum 0x00 が本物の 1.21.6 サーバーに受け入れられることは
     * 2026-11-12 の生ソケットプローブで事前に確認済み。
     */
    @Test
    void fake771ClientJoinsRealVanilla1216ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1216Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.6.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.6.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.6.jar"), "1.21.6")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.6", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.6 to 1.21.6")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 771);
                assertEquals(771, proxy.internalProtocol());
                proxy.start();
                try (Fake771Client client = new Fake771Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(771, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation771()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage771("hello from 1.21.6 to 1.21.6")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }


    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.9（内部773）に
     * 参加し、Join Game・スポーン・チャット・移動がネイティブで動くことを
     * 確認する。チャットの checksum（0x00）は773にもあるためそのまま。
     * 実バニラ1.21.9は Code of Conduct を送ってこない（既定の
     * オフラインサーバー。2026-11-12の生ソケットプローブで確認済み）。
     */
    @Test
    void fake773ClientJoinsRealVanilla1219ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1219Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.9.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.9.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.9.jar"), "1.21.9")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.9", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.9 to 1.21.9")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 773);
                assertEquals(773, proxy.internalProtocol());
                proxy.start();
                try (Fake773Client client = new Fake773Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(773, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation773()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（773形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（773形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage773("hello from 1.21.9 to 1.21.9")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.10（内部773）に
     * 参加する。1.21.9 と同じテストで、1.21.9／1.21.10 のレジストリ同一性
     * （プロトコル773を共有）もあわせて確認する。
     */
    @Test
    void fake773ClientJoinsRealVanilla12110ThroughProxy() throws Exception {
        Path realJar = findRealVanilla12110Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.10.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.10.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.10.jar"), "1.21.10")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.10", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.10 to 1.21.10")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 773);
                assertEquals(773, proxy.internalProtocol());
                proxy.start();
                try (Fake773Client client = new Fake773Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(773, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation773()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（773形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（773形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage773("hello from 1.21.10 to 1.21.10")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽774クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。参加・スポーン・チャット・移動が 774 ネイティブで動くこと。
     * パケットIDは773と同一（Join Game 0x30／Spawn Position 0x5F／
     * Keep Alive 0x2B／Ping 0x3B／Chat Message 0x08＋末尾 checksum）。
     */
    @Test
    void fake774ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
        Path realJar = findRealVanilla12111Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.11.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.11.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.11.jar"), "1.21.11")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.11", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.11 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
                proxy.start();
                try (Fake774Client client = new Fake774Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(774, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation774()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（774形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（774形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage774("hello from 1.21.11 to 1.21.11")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽775クライアントがプロキシ経由で本物のバニラ26.1（内部775）に
     * 参加する。参加・スポーン・チャット・移動が動くこと。
     */
    @Test
    void fake775ClientJoinsRealVanilla261ThroughProxy() throws Exception {
        Path realJar = findRealVanilla261Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-26.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-26.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-26.1.jar"), "26.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "26.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 26.1 to 26.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 775);
                assertEquals(775, proxy.internalProtocol());
                proxy.start();
                try (Fake775Client client = new Fake775Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(775, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation775()));

                    MinecraftPacket joinGame = client.waitForId(0x31, 30_000);
                    assertTrue(joinGame != null, "Join Game（775形式 0x31）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x61, 30_000);
                    assertTrue(spawn != null, "Spawn Position（775形式 0x61）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x09,
                            chatMessage775("hello from 26.1 to 26.1")));
                    client.sendPacket(new MinecraftPacket(0x1E, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽775クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。参加・スポーン・チャット・移動が動くこと。
     */
    @Test
    void fake775ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
        Path realJar = findRealVanilla12111Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.11.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.11.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.11.jar"), "1.21.11")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.11", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 26.1 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
                proxy.start();
                try (Fake775Client client = new Fake775Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(775, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation775()));

                    MinecraftPacket joinGame = client.waitForId(0x31, 30_000);
                    assertTrue(joinGame != null, "Join Game（775形式 0x31）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x61, 30_000);
                    assertTrue(spawn != null, "Spawn Position（775形式 0x61）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x09,
                            chatMessage775("hello from 26.1 to 1.21.11")));
                    client.sendPacket(new MinecraftPacket(0x1E, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽775クライアントがプロキシ経由で本物のバニラ1.21.10（内部773）に
     * 参加する。参加・スポーン・チャット・移動が動くこと。
     */
    @Test
    void fake775ClientJoinsRealVanilla12110ThroughProxy() throws Exception {
        Path realJar = findRealVanilla12110Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.10.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.10.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.10.jar"), "1.21.10")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.10", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 26.1 to 1.21.10")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 773);
                assertEquals(773, proxy.internalProtocol());
                proxy.start();
                try (Fake775Client client = new Fake775Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(775, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation775()));

                    MinecraftPacket joinGame = client.waitForId(0x31, 30_000);
                    assertTrue(joinGame != null, "Join Game（775形式 0x31）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x61, 30_000);
                    assertTrue(spawn != null, "Spawn Position（775形式 0x61）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x09,
                            chatMessage775("hello from 26.1 to 1.21.10")));
                    client.sendPacket(new MinecraftPacket(0x1E, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽775クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。参加・スポーン・チャット・移動が動くこと。
     */
    @Test
    void fake775ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 26.1 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake775Client client = new Fake775Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(775, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation775()));

                    MinecraftPacket joinGame = client.waitForId(0x31, 30_000);
                    assertTrue(joinGame != null, "Join Game（775形式 0x31）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x61, 30_000);
                    assertTrue(spawn != null, "Spawn Position（775形式 0x61）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x09,
                            chatMessage775("hello from 26.1 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x1E, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽774クライアントがプロキシ経由で本物のバニラ26.1（内部775）に
     * 参加する。参加・スポーン・チャット・移動が動くこと。
     */
    @Test
    void fake774ClientJoinsRealVanilla261ThroughProxy() throws Exception {
        Path realJar = findRealVanilla261Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-26.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-26.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-26.1.jar"), "26.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "26.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.11 to 26.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 775);
                assertEquals(775, proxy.internalProtocol());
                proxy.start();
                try (Fake774Client client = new Fake774Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(774, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation774()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（774形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（774形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage774("hello from 1.21.11 to 26.1")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽774クライアントがプロキシ経由で本物のバニラ1.21（内部767）に
     * 参加する。Join Game・スポーン・チャット・移動が 767→774 の上向き変換
     * （767→768→769→770→771→772→773→774 の七段）で動くこと。
     * 設定フェーズは 767 の Registry Data を 774 の順序と形式に変換する。
     */
    @Test
    void fake774ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.11 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake774Client client = new Fake774Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(774, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation774()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（774形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（774形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage774("hello from 1.21.11 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽767クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。Join Game・スポーン・チャット・移動が 774→767 の下向き変換
     * （774→773→772→771→770→769→768→767 の七段）で動くこと。
     * 設定フェーズは 774 の Registry Data を 767 の順序と形式に変換する。
     */
    @Test
    void fake767ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
        Path realJar = findRealVanilla12111Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.11.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.11.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.11.jar"), "1.21.11")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.11", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（767形式 0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（767形式 0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.21.11")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽763クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。端同士（763×774）の上向き変換。Join Game の codec に
     * dimension_type と biome が含まれ、スポーン・チャット・移動が動くこと。
     */
    @Test
    void fake763ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
        Path realJar = findRealVanilla12111Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.11.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.11.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.11.jar"), "1.21.11")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.11", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.20.1 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（763形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（763形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage763("hello from 1.20.1 to 1.21.11")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽774クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。端同士（774×763）の下向き変換。内部763には設定フェーズが
     * 無いため、プロキシは 763 の Join Game の codec から 774 向けの
     * 設定フェーズ（Registry Data 0x07・Finish Configuration 0x03）を
     * 合成する。774にしか無い要素（槍・ノーチラス等）は 763 に無いため
     * レジストリには現れない。
     */
    @Test
    void fake774ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.11 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake774Client client = new Fake774Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(774, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation774()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（774形式 0x30）が届きませんでした。");
                    // 774の Join Game に codec は無い（レジストリは合成した
                    // 設定フェーズの Registry Data で届く）。合成された
                    // Registry Data を受け取ったことを確認する。
                    assertTrue(client.receivedRegistryData,
                            "合成された設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（774形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage774("hello from 1.21.11 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.7（内部772）に
     * 参加し、Join Game・スポーン・チャット・移動が 773→772 の下向き変換で
     * 動くことを確認する。チャットの checksum は772・773ともあるため
     * そのまま。設定フェーズは 772 の Registry Data を 773 の順序と形式に
     * 変換する。Join Game と Spawn Position は 773 の番号（0x30／0x5F）
     * で届く。
     */
    @Test
    void fake773ClientJoinsRealVanilla1217ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1217Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.7.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.7.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.7.jar"), "1.21.7")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.7", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.9 to 1.21.7")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 772);
                assertEquals(772, proxy.internalProtocol());
                proxy.start();
                try (Fake773Client client = new Fake773Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(773, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation773()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（773形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（773形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage773("hello from 1.21.9 to 1.21.7")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.8（内部772）に
     * 参加する。1.21.7 と同じテストで、1.21.7／1.21.8 のレジストリ同一性
     * もあわせて確認する。
     */
    @Test
    void fake773ClientJoinsRealVanilla1218ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1218Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.8.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.8.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.8.jar"), "1.21.8")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.8", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.9 to 1.21.8")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 772);
                assertEquals(772, proxy.internalProtocol());
                proxy.start();
                try (Fake773Client client = new Fake773Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(773, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation773()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（773形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（773形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage773("hello from 1.21.9 to 1.21.8")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.6（内部771）に
     * 参加し、Join Game・スポーン・チャット・移動が 773→771 の下向き変換で
     * 動くことを確認する。チャットの checksum は771・773ともあるため
     * そのまま。設定フェーズは 771 の Registry Data を 773 の順序と形式に
     * 変換する。
     */
    @Test
    void fake773ClientJoinsRealVanilla1216ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1216Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.6.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.6.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.6.jar"), "1.21.6")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.6", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.9 to 1.21.6")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 771);
                assertEquals(771, proxy.internalProtocol());
                proxy.start();
                try (Fake773Client client = new Fake773Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(773, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation773()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（773形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（773形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage773("hello from 1.21.9 to 1.21.6")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加し、Join Game・スポーン・チャット・移動が 773→763 の下向き変換で
     * 動くことを確認する。内部763には設定フェーズが無いため、プロキシが
     * 773クライアント向けに Registry Data（dimension_type・biome）と
     * Finish Configuration を合成する。チャットの checksum は763には
     * 無いためプロキシが剥がす。
     */
    @Test
    void fake773ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.9 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake773Client client = new Fake773Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(773, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation773()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（773形式 0x30）が届きませんでした。");
                    // 773の Join Game に codec は無い（レジストリは合成した
                    // 設定フェーズの Registry Data で届く）。合成された
                    // Registry Data を受け取ったことを確認する。
                    assertTrue(client.receivedRegistryData,
                            "合成された設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（773形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage773("hello from 1.21.9 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.9（内部773）に
     * 参加し、Join Game・スポーン・チャット・移動が 772→773 の上向き変換で
     * 動くことを確認する。チャットの checksum は772・773ともあるため
     * そのまま。設定フェーズは 773 の Registry Data を 772 の順序と形式に
     * 変換する。実バニラ1.21.9は Code of Conduct を送ってこないため、
     * 古いクライアントでも設定フェーズで止まらない（2026-11-12の
     * 生ソケットプローブで確認済み）。
     */
    @Test
    void fake772ClientJoinsRealVanilla1219ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1219Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.9.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.9.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.9.jar"), "1.21.9")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.9", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.7 to 1.21.9")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 773);
                assertEquals(773, proxy.internalProtocol());
                proxy.start();
                try (Fake772Client client = new Fake772Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(772, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation772()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（772形式 0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（772形式 0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage772("hello from 1.21.7 to 1.21.9")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.10（内部773）に
     * 参加する。1.21.9 と同じテストで、1.21.9／1.21.10 のレジストリ同一性
     * もあわせて確認する。
     */
    @Test
    void fake772ClientJoinsRealVanilla12110ThroughProxy() throws Exception {
        Path realJar = findRealVanilla12110Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.10.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.10.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.10.jar"), "1.21.10")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.10", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.7 to 1.21.10")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 773);
                assertEquals(773, proxy.internalProtocol());
                proxy.start();
                try (Fake772Client client = new Fake772Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(772, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation772()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（772形式 0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（772形式 0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage772("hello from 1.21.7 to 1.21.10")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.7（内部772）に
     * 参加し、Join Game・スポーン・チャット・移動がネイティブで動くことを
     * 確認する。チャットの checksum（0x00）は772にもあるためそのまま。
     * 1.21.7／1.21.8 の checksum 未検証の仮定はこのテスト自体が検証する。
     */
    @Test
    void fake772ClientJoinsRealVanilla1217ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1217Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.7.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.7.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.7.jar"), "1.21.7")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.7", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.7 to 1.21.7")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 772);
                assertEquals(772, proxy.internalProtocol());
                proxy.start();
                try (Fake772Client client = new Fake772Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(772, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation772()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage772("hello from 1.21.7 to 1.21.7")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.8（内部772）に
     * 参加する。1.21.7 と同じテストで、1.21.7／1.21.8 のレジストリ同一性
     * （RegistryOrderCaptureTest と同じ仮定）もあわせて確認する。
     */
    @Test
    void fake772ClientJoinsRealVanilla1218ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1218Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.8.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.8.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.8.jar"), "1.21.8")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.8", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.8 to 1.21.8")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 772);
                assertEquals(772, proxy.internalProtocol());
                proxy.start();
                try (Fake772Client client = new Fake772Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(772, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation772()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage772("hello from 1.21.8 to 1.21.8")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.6（内部771）に
     * 参加し、Join Game・スポーン・チャット・移動が 772→771 の下向き変換で
     * 動くことを確認する。チャットの checksum は771・772ともあるため
     * そのまま。設定フェーズは 771 の Registry Data を 772 の順序と形式に
     * 変換する（lava_chicken を9番に挿入、dialog レジストリはそのまま通す）。
     */
    @Test
    void fake772ClientJoinsRealVanilla1216ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1216Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.6.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.6.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.6.jar"), "1.21.6")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.6", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.7 to 1.21.6")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 771);
                assertEquals(771, proxy.internalProtocol());
                proxy.start();
                try (Fake772Client client = new Fake772Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(772, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation772()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage772("hello from 1.21.7 to 1.21.6")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽771クライアントがプロキシ経由で本物のバニラ1.21.7（内部772）に
     * 参加し、Join Game・スポーン・チャット・移動が 771→772 の上向き変換で
     * 動くことを確認する。設定フェーズは 772 の Registry Data を 771 の
     * 順序と形式に変換する（lava_chicken を落とす、dialog レジストリは
     * そのまま通す）。
     */
    @Test
    void fake771ClientJoinsRealVanilla1217ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1217Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.7.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.7.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.7.jar"), "1.21.7")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.7", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.6 to 1.21.7")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 772);
                assertEquals(772, proxy.internalProtocol());
                proxy.start();
                try (Fake771Client client = new Fake771Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(771, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation771()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage771("hello from 1.21.6 to 1.21.7")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.5（内部770）に
     * 参加し、Join Game・スポーン・チャット・移動が 772→770 の下向き変換で
     * 動くことを確認する。チャットの checksum は770・772ともあるため
     * そのまま。設定フェーズは 770 の Registry Data を 772 の順序と形式に
     * 変換する（dialog レジストリは770には無いため落とす）。
     */
    @Test
    void fake772ClientJoinsRealVanilla1215ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1215Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.5.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.5.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.5.jar"), "1.21.5")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.5", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.7 to 1.21.5")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 770);
                assertEquals(770, proxy.internalProtocol());
                proxy.start();
                try (Fake772Client client = new Fake772Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(772, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation772()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage772("hello from 1.21.7 to 1.21.5")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加し、Join Game・スポーン・チャット・移動が 772→763 の下向き変換で
     * 動くことを確認する。内部763には設定フェーズが無いため、プロキシが
     * 772クライアント向けに Registry Data（dimension_type・biome）と
     * Finish Configuration を合成する。チャットの checksum は763には
     * 無いためプロキシが剥がす。
     */
    @Test
    void fake772ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.7 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake772Client client = new Fake772Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(772, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation772()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（772形式 0x2B）が届きませんでした。");
                    // 772の Join Game に codec は無い（レジストリは合成した
                    // 設定フェーズの Registry Data で届く）。合成された
                    // Registry Data を受け取ったことを確認する。
                    assertTrue(client.receivedRegistryData,
                            "合成された設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（772形式 0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage772("hello from 1.21.7 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽771クライアントがプロキシ経由で本物のバニラ1.21.5（内部770）に
     * 参加し、Join Game・スポーン・チャット・移動が 771→770 の下向き変換で
     * 動くことを確認する。チャットの checksum は770にもあるためそのまま
     * 番号だけ付け替える。設定フェーズは 770 の Registry Data を 771 の
     * 順序と形式に変換する（dialog レジストリは合成しない）。
     */
    @Test
    void fake771ClientJoinsRealVanilla1215ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1215Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.5.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.5.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.5.jar"), "1.21.5")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.5", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.6 to 1.21.5")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 770);
                assertEquals(770, proxy.internalProtocol());
                proxy.start();
                try (Fake771Client client = new Fake771Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(771, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation771()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage771("hello from 1.21.6 to 1.21.5")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽770クライアントがプロキシ経由で本物のバニラ1.21.6（内部771）に
     * 参加し、Join Game・スポーン・チャット・移動が 770→771 の上向き変換で
     * 動くことを確認する。チャットの checksum はプロキシがそのまま通す。
     * 設定フェーズは 771 の Registry Data を 770 の順序と形式に変換する
     * （dialog レジストリは落とす）。
     */
    @Test
    void fake770ClientJoinsRealVanilla1216ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1216Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.6.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.6.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.6.jar"), "1.21.6")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.6", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.5 to 1.21.6")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 771);
                assertEquals(771, proxy.internalProtocol());
                proxy.start();
                try (Fake770Client client = new Fake770Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(770, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation770()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5A, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5A）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage770("hello from 1.21.5 to 1.21.6")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽769クライアントがプロキシ経由で本物のバニラ1.21.2（内部768）に
     * 参加し、Join Game・スポーン・チャット・移動が 768→769 の上向き変換で
     * 動くことを確認する。設定フェーズは 768 の Registry Data を
     * 769 の順序と形式に変換して中継する（pale_garden を挿入する）。
     */
    @Test
    void fake769ClientJoinsRealVanilla1212ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1212Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.2.jar"), "1.21.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.21.4 to 1.21.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 768);
                assertEquals(768, proxy.internalProtocol());
                proxy.start();
                try (Fake769Client client = new Fake769Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(769, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Sora")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation769()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.4 to 1.21.2")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Sora の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽768クライアント（1.21.2／1.21.3）がプロキシ経由で本物のバニラ1.21.4
     * （内部769）に参加し、Join Game・スポーン・チャット・移動が 769→768 の
     * 下向き変換で動くことを確認する。設定フェーズは 769 の Registry Data を
     * 768 の順序と形式に変換して中継する（pale_garden を落とす）。
     */
    @Test
    void fake768ClientJoinsRealVanilla1214ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1214Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.4.jar"), "1.21.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Rin")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Rin")
                        && chat.message().equals("hello from 1.21.2 to 1.21.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 769);
                assertEquals(769, proxy.internalProtocol());
                proxy.start();
                try (Fake768Client client = new Fake768Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(768, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Rin")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation768()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.2 to 1.21.4")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Rin の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽769クライアントがプロキシ経由で本物のバニラ1.21（内部767）に
     * 参加し、Join Game・スポーン・チャット・移動が 767→769 の上向き変換で
     * 動くことを確認する。設定フェーズは 767 の Registry Data を
     * 769 の順序と形式に変換して中継する。
     */
    @Test
    void fake769ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Aoi")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Aoi")
                        && chat.message().equals("hello from 1.21.4 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake769Client client = new Fake769Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(769, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Aoi")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation769()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.4 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Aoi の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽767クライアント（1.21／1.21.1）がプロキシ経由で本物のバニラ1.21.4
     * （内部769）に参加し、Join Game・スポーン・チャット・移動が 769→767 の
     * 下向き変換で動くことを確認する。設定フェーズは 769 の Registry Data を
     * 767 の順序と形式に変換して中継する（pale_garden を落とす）。
     */
    @Test
    void fake767ClientJoinsRealVanilla1214ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1214Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.4.jar"), "1.21.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.21 to 1.21.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 769);
                assertEquals(769, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Hana")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.21.4")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽769クライアントがプロキシ経由で本物のバニラ1.20.5（内部766）に
     * 参加し、Join Game・スポーン・チャット・移動が 766→769 の上向き変換で
     * 動くことを確認する。設定フェーズは 766 の Registry Data を
     * 769 の順序と形式に変換して中継する。
     */
    @Test
    void fake769ClientJoinsRealVanilla1205ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1205Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.5.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.5.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.5.jar"), "1.20.5")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.5", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.4 to 1.20.5")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 766);
                assertEquals(766, proxy.internalProtocol());
                proxy.start();
                try (Fake769Client client = new Fake769Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(769, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation769()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.4 to 1.20.5")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽766クライアント（1.20.5／1.20.6）がプロキシ経由で本物のバニラ1.21.4
     * （内部769）に参加し、Join Game・スポーン・チャット・移動が 769→766 の
     * 下向き変換で動くことを確認する。設定フェーズは 769 の Registry Data を
     * 766 の順序と形式に変換して中継する（pale_garden を落とす）。
     */
    @Test
    void fake766ClientJoinsRealVanilla1214ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1214Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.4.jar"), "1.21.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.20.5 to 1.21.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 769);
                assertEquals(769, proxy.internalProtocol());
                proxy.start();
                try (Fake766Client client = new Fake766Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(766, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Sora")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.20.5 to 1.21.4")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Sora の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽769クライアントがプロキシ経由で本物のバニラ1.20.4（内部765）に
     * 参加し、Join Game・スポーン・チャット・移動が 765→769 の上向き変換で
     * 動くことを確認する。設定フェーズは 765 の codec 形式 Registry Data を
     * 769 のエントリ配列形式に変換して中継する。
     */
    @Test
    void fake769ClientJoinsRealVanilla1204ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1204Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.4.jar"), "1.20.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Rin")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Rin")
                        && chat.message().equals("hello from 1.21.4 to 1.20.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 765);
                assertEquals(765, proxy.internalProtocol());
                proxy.start();
                try (Fake769Client client = new Fake769Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(769, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Rin")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation769()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.4 to 1.20.4")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Rin の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽765クライアント（1.20.3／1.20.4）がプロキシ経由で本物のバニラ1.21.4
     * （内部769）に参加し、Join Game・スポーン・チャット・移動が 769→765 の
     * 下向き変換で動くことを確認する。設定フェーズは 769 の Registry Data を
     * 765 の codec 形式に変換して中継する（pale_garden を落とす）。
     */
    @Test
    void fake765ClientJoinsRealVanilla1214ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1214Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.4.jar"), "1.21.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Aoi")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Aoi")
                        && chat.message().equals("hello from 1.20.4 to 1.21.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 769);
                assertEquals(769, proxy.internalProtocol());
                proxy.start();
                try (Fake765Client client = new Fake765Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(765, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart765("Aoi")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation765()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x29）が届きませんでした。");
                    // 767の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x54, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x54）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage765("hello from 1.20.4 to 1.21.4")));
                    client.sendPacket(new MinecraftPacket(0x17, move765()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Aoi の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽769クライアントがプロキシ経由で本物のバニラ1.20.2（内部764）に
     * 参加し、Join Game・スポーン・チャット・移動が 764→769 の上向き変換で
     * 動くことを確認する。設定フェーズは 764 の Registry Data を
     * 769 のエントリ配列形式に変換して中継する。
     */
    @Test
    void fake769ClientJoinsRealVanilla1202ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1202Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.2.jar"), "1.20.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.21.4 to 1.20.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 764);
                assertEquals(764, proxy.internalProtocol());
                proxy.start();
                try (Fake769Client client = new Fake769Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(769, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Hana")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation769()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.4 to 1.20.2")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽764クライアント（1.20.2）がプロキシ経由で本物のバニラ1.21.4
     * （内部769）に参加し、Join Game・スポーン・チャット・移動が 769→764 の
     * 下向き変換で動くことを確認する。設定フェーズは 769 の Registry Data を
     * 764 の codec 形式に変換して中継する（pale_garden を落とす）。
     */
    @Test
    void fake764ClientJoinsRealVanilla1214ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1214Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.4.jar"), "1.21.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.20.2 to 1.21.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 769);
                assertEquals(769, proxy.internalProtocol());
                proxy.start();
                try (Fake764Client client = new Fake764Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(764, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart764("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation764()));

                    MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x29）が届きませんでした。");
                    // 764の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x05）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x52, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x52）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage764("hello from 1.20.2 to 1.21.4")));
                    client.sendPacket(new MinecraftPacket(0x16, move764()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽763クライアント（1.20.1）がプロキシ経由で本物のバニラ1.21.4
     * （内部769）に参加する。内部769には設定フェーズがあり、プロキシは
     * 769の Registry Data などを消費して 763 の Join Game（0x28）に
     * codec を合成する。Join Game・スポーン・チャット・移動が
     * 769→763 の下向き変換で動くことを確認する。
     */
    @Test
    void fake763ClientJoinsRealVanilla1214ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1214Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.4.jar"), "1.21.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.20.1 to 1.21.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 769);
                assertEquals(769, proxy.internalProtocol());
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（763形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（763形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage763("hello from 1.20.1 to 1.21.4")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽769クライアント（1.21.4）がプロキシ経由で本物のバニラ1.20.1
     * （内部763）に参加する。内部763には設定フェーズが無いため、プロキシは
     * 763 の Join Game の codec から 769 向けの設定フェーズ（Registry Data
     * 0x07・Finish Configuration 0x03）を合成する。Join Game・スポーン・
     * チャット・移動が 763→769 の上向き変換で動くことを確認する。
     */
    @Test
    void fake769ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.4 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake769Client client = new Fake769Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(769, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");
                    // 769の Login Success は strictErrorHandling が無い。末尾に
                    // 余分な bool が無いこと（UUID・名前・属性を読んだ後に余りが無いこと）
                    // を確かめる。Login Success は UUID が先、名前が後。
                    PacketIO.Reader loginReader =
                            new PacketIO.Reader(loginSuccess.payload());
                    loginReader.readUuid();
                    assertEquals("Yuki", loginReader.readString());
                    assertEquals(0, loginReader.readVarInt());
                    assertEquals(0, loginReader.remaining());

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation769()));

                    MinecraftPacket joinGame = client.waitForId(0x2C, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x2C）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5B, 30_000);
                    assertTrue(spawn != null, "Spawn Position（0x5B）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x07,
                            chatMessage766("hello from 1.21.4 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x1C, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }
    /**
     * 偽762クライアントがプロキシ経由で本物のバニラ1.19.4（内部762）に
     * 参加する。参加・スポーン・チャット・移動が 762 ネイティブで動くこと。
     * パケットIDは763と同一（Join Game 0x28／Spawn Position 0x50／
     * Keep Alive 0x23／Chat Message 0x05／移動 0x14）。
     * 1.19.4は Java 17 で起動する（Java 21 は不要）。
     */
    @Test
    void fake762ClientJoinsRealVanilla1194ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1194Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.4.jar"), "1.19.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.19.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 762);
                assertEquals(762, proxy.internalProtocol());
                proxy.start();
                try (Fake762Client client = new Fake762Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(762, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart762("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（762形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（762形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage762("hello from 1.19.4")));
                    client.sendPacket(new MinecraftPacket(0x14, move762()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽762クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。Join Game・スポーン・チャット・移動が 762↔763 の直接変換
     * （パケットIDは0件変更）で動くこと。両方とも設定フェーズが無いため、
     * ログインはすぐプレイ状態へ進む。
     */
    @Test
    void fake762ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.19.4 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake762Client client = new Fake762Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(762, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart762("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（762形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（762形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage762("hello from 1.19.4 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x14, move762()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽762クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。内部774の設定フェーズはプロキシが774クライアント役として
     * 消費し、Join Game の codec として762に送り直す。762↔763の直接変換の
     * あと、既存の763↔764↔…↔774の変換を重ねる。
     */
    @Test
    void fake762ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
        Path realJar = findRealVanilla12111Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.11.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.11.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.11.jar"), "1.21.11")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21.11", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.19.4 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
                proxy.start();
                try (Fake762Client client = new Fake762Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(762, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart762("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（762形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（762形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage762("hello from 1.19.4 to 1.21.11")));
                    client.sendPacket(new MinecraftPacket(0x14, move762()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽763クライアントがプロキシ経由で本物のバニラ1.19.4（内部762）に
     * 参加する。内部762には設定フェーズが無いため、ログインはすぐプレイ
     * 状態へ進む。762サーバーの Join Game は portalCooldown=0 を足して
     * 763形式に直してから763クライアントへ送る。
     */
    @Test
    void fake763ClientJoinsRealVanilla1194ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1194Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.4.jar"), "1.19.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Ren")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Ren")
                        && chat.message().equals("hello from 1.20.1 to 1.19.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 762);
                assertEquals(762, proxy.internalProtocol());
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Ren")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（763形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（763形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage763("hello from 1.20.1 to 1.19.4")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Ren の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }
    /**
     * 偽761クライアントがプロキシ経由で本物のバニラ1.19.3（内部761）に
     * 参加する。参加・スポーン・チャット・移動が 761 ネイティブで動くこと。
     * パケットIDは762と違う（Join Game 0x24／Spawn Position 0x4C／
     * Keep Alive 0x1F／Chat Message 0x05／移動 0x13）。
     * 1.19.3は Java 17 で起動する（Java 21 は不要）。
     */
    @Test
    void fake761ClientJoinsRealVanilla1193ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1193Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.3.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.3.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.3.jar"), "1.19.3")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.3", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.19.3")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 761);
                assertEquals(761, proxy.internalProtocol());
                proxy.start();
                try (Fake761Client client = new Fake761Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(761, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart761("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x24, 30_000);
                    assertTrue(joinGame != null, "Join Game（761形式 0x24）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4C, 30_000);
                    assertTrue(spawn != null, "Spawn Position（761形式 0x4C）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage761("hello from 1.19.3")));
                    client.sendPacket(new MinecraftPacket(0x13, move761()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽761クライアントがプロキシ経由で本物のバニラ1.19.4（内部762）に
     * 参加する。761↔762 の変換（Join Game 0x24／Spawn Position 0x4C、
     * パケットID 132 件変更）で動くこと。
     */
    @Test
    void fake761ClientJoinsRealVanilla1194ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1194Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.4.jar"), "1.19.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.19.3 to 1.19.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 762);
                assertEquals(762, proxy.internalProtocol());
                proxy.start();
                try (Fake761Client client = new Fake761Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(761, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart761("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x24, 30_000);
                    assertTrue(joinGame != null, "Join Game（761形式 0x24）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4C, 30_000);
                    assertTrue(spawn != null, "Spawn Position（761形式 0x4C）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage761("hello from 1.19.3 to 1.19.4")));
                    client.sendPacket(new MinecraftPacket(0x13, move761()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽761クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。761→762→763 の合成変換で動くこと。両方とも設定フェーズが
     * 無いため、ログインはすぐプレイ状態へ進む。
     */
    @Test
    void fake761ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.19.3 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake761Client client = new Fake761Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(761, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart761("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x24, 30_000);
                    assertTrue(joinGame != null, "Join Game（761形式 0x24）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4C, 30_000);
                    assertTrue(spawn != null, "Spawn Position（761形式 0x4C）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage761("hello from 1.19.3 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x13, move761()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽762クライアントがプロキシ経由で本物のバニラ1.19.3（内部761）に
     * 参加する。762→761 の逆向き変換で動くこと。762サーバーの Join Game
     * は portalCooldown が無いため、そのまま761形式に直して送る。
     */
    @Test
    void fake762ClientJoinsRealVanilla1193ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1193Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.3.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.3.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.3.jar"), "1.19.3")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.3", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Ren")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Ren")
                        && chat.message().equals("hello from 1.19.4 to 1.19.3")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 761);
                assertEquals(761, proxy.internalProtocol());
                proxy.start();
                try (Fake762Client client = new Fake762Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(762, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart762("Ren")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（762形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（762形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage762("hello from 1.19.4 to 1.19.3")));
                    client.sendPacket(new MinecraftPacket(0x14, move762()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Ren の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽763クライアントがプロキシ経由で本物のバニラ1.19.3（内部761）に
     * 参加する。763→762→761 の合成変換で動くこと。761サーバーの Join Game
     * は portalCooldown=0 を足して763形式に直してから763クライアントへ送る。
     */
    @Test
    void fake763ClientJoinsRealVanilla1193ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1193Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.3.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.3.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.3.jar"), "1.19.3")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.3", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Aoi")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Aoi")
                        && chat.message().equals("hello from 1.20.1 to 1.19.3")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 761);
                assertEquals(761, proxy.internalProtocol());
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Aoi")));

                    MinecraftPacket joinGame = client.waitForId(0x28, 30_000);
                    assertTrue(joinGame != null, "Join Game（763形式 0x28）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x50, 30_000);
                    assertTrue(spawn != null, "Spawn Position（763形式 0x50）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage763("hello from 1.20.1 to 1.19.3")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Aoi の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽760クライアントがプロキシ経由で本物のバニラ1.19.1（内部760）に
     * 参加する。760↔760 のネイティブ経路。Join Game 0x25／Spawn Position 0x4d、
     * Chat Message の760形式（署名バッファ＋signedPreview＋previousMessages＋
     * lastRejectedMessage）で動くこと。
     */
    @Test
    void fake760ClientJoinsRealVanilla1191ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1191Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.1.jar"), "1.19.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.19.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 760);
                assertEquals(760, proxy.internalProtocol());
                proxy.start();
                try (Fake760Client client = new Fake760Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(760, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart760("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x25, 30_000);
                    assertTrue(joinGame != null, "Join Game（760形式 0x25）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4D, 30_000);
                    assertTrue(spawn != null, "Spawn Position（760形式 0x4D）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage760("hello from 1.19.1")));
                    client.sendPacket(new MinecraftPacket(0x14, move760()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽760クライアントがプロキシ経由で本物のバニラ1.19.2（内部760）に
     * 参加する。1.19.1 と同じプロトコル760のため、1.19.1 と同じ流れで動くこと。
     */
    @Test
    void fake760ClientJoinsRealVanilla1192ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1192Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.2.jar"), "1.19.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.19.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 760);
                assertEquals(760, proxy.internalProtocol());
                proxy.start();
                try (Fake760Client client = new Fake760Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(760, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart760("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x25, 30_000);
                    assertTrue(joinGame != null, "Join Game（760形式 0x25）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4D, 30_000);
                    assertTrue(spawn != null, "Spawn Position（760形式 0x4D）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage760("hello from 1.19.2")));
                    client.sendPacket(new MinecraftPacket(0x14, move760()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽760クライアントがプロキシ経由で本物のバニラ1.19.3（内部761）に
     * 参加する。760↔761 の変換（Join Game 0x25／Spawn Position 0x4D、
     * パケットID 118 件変更、Player Chat／Player Info の旧式形式）で動くこと。
     */
    @Test
    void fake760ClientJoinsRealVanilla1193ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1193Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.3.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.3.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.3.jar"), "1.19.3")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.3", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.19.1 to 1.19.3")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 761);
                assertEquals(761, proxy.internalProtocol());
                proxy.start();
                try (Fake760Client client = new Fake760Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(760, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart760("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x25, 30_000);
                    assertTrue(joinGame != null, "Join Game（760形式 0x25）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4D, 30_000);
                    assertTrue(spawn != null, "Spawn Position（760形式 0x4D）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage760("hello from 1.19.1 to 1.19.3")));
                    client.sendPacket(new MinecraftPacket(0x14, move760()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽760クライアントがプロキシ経由で本物のバニラ1.19.4（内部762）に
     * 参加する。760→761→762 の合成変換で動くこと。
     */
    @Test
    void fake760ClientJoinsRealVanilla1194ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1194Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.4.jar"), "1.19.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.19.1 to 1.19.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 762);
                assertEquals(762, proxy.internalProtocol());
                proxy.start();
                try (Fake760Client client = new Fake760Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(760, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart760("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x25, 30_000);
                    assertTrue(joinGame != null, "Join Game（760形式 0x25）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4D, 30_000);
                    assertTrue(spawn != null, "Spawn Position（760形式 0x4D）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage760("hello from 1.19.1 to 1.19.4")));
                    client.sendPacket(new MinecraftPacket(0x14, move760()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽760クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。760→761→762→763 の合成変換で動くこと。両方とも設定フェーズが
     * 無いため、ログインはすぐプレイ状態へ進む。
     */
    @Test
    void fake760ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.19.1 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake760Client client = new Fake760Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(760, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart760("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x25, 30_000);
                    assertTrue(joinGame != null, "Join Game（760形式 0x25）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4D, 30_000);
                    assertTrue(spawn != null, "Spawn Position（760形式 0x4D）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage760("hello from 1.19.1 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x14, move760()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }


    /**
     * 偽759クライアントがプロキシ経由で本物のバニラ1.19（内部759）に
     * 参加する。設定フェーズなしでログイン→プレイへ直接進むこと。
     */
    @Test
    void fake759ClientJoinsRealVanilla119ThroughProxy() throws Exception {
        Path realJar = findRealVanilla119Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.jar"), "1.19")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.19")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 759);
                assertEquals(759, proxy.internalProtocol());
                proxy.start();
                try (Fake759Client client = new Fake759Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(759, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart759("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x23, 30_000);
                    assertTrue(joinGame != null, "Join Game（759形式 0x23）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4a, 30_000);
                    assertTrue(spawn != null, "Spawn Position（759形式 0x4a）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x04,
                            chatMessage759("hello from 1.19")));
                    client.sendPacket(new MinecraftPacket(0x13, move759()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽759クライアントがプロキシ経由で本物のバニラ1.19.1（内部760）に
     * 参加する。759→760 の直接変換で動くこと。
     */
    @Test
    void fake759ClientJoinsRealVanilla1191ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1191Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.1.jar"), "1.19.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.19 to 1.19.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 760);
                assertEquals(760, proxy.internalProtocol());
                proxy.start();
                try (Fake759Client client = new Fake759Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(759, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart759("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x23, 30_000);
                    assertTrue(joinGame != null, "Join Game（759形式 0x23）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4a, 30_000);
                    assertTrue(spawn != null, "Spawn Position（759形式 0x4a）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x04,
                            chatMessage759("hello from 1.19 to 1.19.1")));
                    client.sendPacket(new MinecraftPacket(0x13, move759()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽759クライアントがプロキシ経由で本物のバニラ1.19.2（内部760）に
     * 参加する。1.19.1 と同じプロトコル760のため、同じ流れで動くこと。
     */
    @Test
    void fake759ClientJoinsRealVanilla1192ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1192Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.2.jar"), "1.19.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.19 to 1.19.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 760);
                assertEquals(760, proxy.internalProtocol());
                proxy.start();
                try (Fake759Client client = new Fake759Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(759, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart759("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x23, 30_000);
                    assertTrue(joinGame != null, "Join Game（759形式 0x23）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4a, 30_000);
                    assertTrue(spawn != null, "Spawn Position（759形式 0x4a）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x04,
                            chatMessage759("hello from 1.19 to 1.19.2")));
                    client.sendPacket(new MinecraftPacket(0x13, move759()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽759クライアントがプロキシ経由で本物のバニラ1.19.3（内部761）に
     * 参加する。759→760→761 の合成変換で動くこと。
     */
    @Test
    void fake759ClientJoinsRealVanilla1193ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1193Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.3.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.3.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.3.jar"), "1.19.3")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.3", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.19 to 1.19.3")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 761);
                assertEquals(761, proxy.internalProtocol());
                proxy.start();
                try (Fake759Client client = new Fake759Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(759, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart759("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x23, 30_000);
                    assertTrue(joinGame != null, "Join Game（759形式 0x23）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4a, 30_000);
                    assertTrue(spawn != null, "Spawn Position（759形式 0x4a）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x04,
                            chatMessage759("hello from 1.19 to 1.19.3")));
                    client.sendPacket(new MinecraftPacket(0x13, move759()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽759クライアントがプロキシ経由で本物のバニラ1.19.4（内部762）に
     * 参加する。759→760→761→762 の合成変換で動くこと。
     */
    @Test
    void fake759ClientJoinsRealVanilla1194ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1194Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.4.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.4.jar"), "1.19.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.19 to 1.19.4")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 762);
                assertEquals(762, proxy.internalProtocol());
                proxy.start();
                try (Fake759Client client = new Fake759Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(759, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart759("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x23, 30_000);
                    assertTrue(joinGame != null, "Join Game（759形式 0x23）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4a, 30_000);
                    assertTrue(spawn != null, "Spawn Position（759形式 0x4a）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x04,
                            chatMessage759("hello from 1.19 to 1.19.4")));
                    client.sendPacket(new MinecraftPacket(0x13, move759()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽759クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。759→760→761→762→763 の合成変換で動くこと。
     */
    @Test
    void fake759ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.19 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake759Client client = new Fake759Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(759, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart759("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x23, 30_000);
                    assertTrue(joinGame != null, "Join Game（759形式 0x23）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4a, 30_000);
                    assertTrue(spawn != null, "Spawn Position（759形式 0x4a）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x04,
                            chatMessage759("hello from 1.19 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x13, move759()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽760クライアントがプロキシ経由で本物のバニラ1.19（内部759）に
     * 参加する。760→759 の逆向き変換（Login Start からUUID欄を落とす）で動くこと。
     */
    @Test
    void fake760ClientJoinsRealVanilla119ThroughProxy() throws Exception {
        Path realJar = findRealVanilla119Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.jar"), "1.19")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Ren")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Ren")
                        && chat.message().equals("hello from 1.19.1 to 1.19")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 759);
                assertEquals(759, proxy.internalProtocol());
                proxy.start();
                try (Fake760Client client = new Fake760Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(760, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart760("Ren")));

                    MinecraftPacket joinGame = client.waitForId(0x25, 30_000);
                    assertTrue(joinGame != null, "Join Game（760形式 0x25）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4D, 30_000);
                    assertTrue(spawn != null, "Spawn Position（760形式 0x4D）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage760("hello from 1.19.1 to 1.19")));
                    client.sendPacket(new MinecraftPacket(0x14, move760()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Ren の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽761クライアントがプロキシ経由で本物のバニラ1.19.1（内部760）に
     * 参加する。761→760 の逆向き変換（Login Start を760形式へ直す）で動くこと。
     */
    @Test
    void fake761ClientJoinsRealVanilla1191ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1191Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.1.jar"), "1.19.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Ren")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Ren")
                        && chat.message().equals("hello from 1.19.3 to 1.19.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 760);
                assertEquals(760, proxy.internalProtocol());
                proxy.start();
                try (Fake761Client client = new Fake761Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(761, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart761("Ren")));

                    MinecraftPacket joinGame = client.waitForId(0x24, 30_000);
                    assertTrue(joinGame != null, "Join Game（761形式 0x24）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4C, 30_000);
                    assertTrue(spawn != null, "Spawn Position（761形式 0x4C）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage761("hello from 1.19.3 to 1.19.1")));
                    client.sendPacket(new MinecraftPacket(0x13, move761()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Ren の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽761クライアントがプロキシ経由で本物のバニラ1.19.2（内部760）に
     * 参加する。1.19.1 と同じプロトコル760のため、1.19.1 と同じ流れで動くこと。
     */
    @Test
    void fake761ClientJoinsRealVanilla1192ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1192Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.2.jar"), "1.19.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Ren")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Ren")
                        && chat.message().equals("hello from 1.19.3 to 1.19.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 760);
                assertEquals(760, proxy.internalProtocol());
                proxy.start();
                try (Fake761Client client = new Fake761Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(761, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart761("Ren")));

                    MinecraftPacket joinGame = client.waitForId(0x24, 30_000);
                    assertTrue(joinGame != null, "Join Game（761形式 0x24）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4C, 30_000);
                    assertTrue(spawn != null, "Spawn Position（761形式 0x4C）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x05,
                            chatMessage761("hello from 1.19.3 to 1.19.2")));
                    client.sendPacket(new MinecraftPacket(0x13, move761()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Ren の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽757クライアントがプロキシ経由で本物のバニラ1.18（内部757）に
     * 参加する。757→757 の素通しで動くこと。
     */
    @Test
    void fake757ClientJoinsRealVanilla118ThroughProxy() throws Exception {
        Path realJar = findRealVanilla118Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.18.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.18.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.18.jar"), "1.18")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.18", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.18")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 757);
                assertEquals(757, proxy.internalProtocol());
                proxy.start();
                try (Fake757Client client = new Fake757Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(757, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart757("Steve")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（757形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    // 設定パケットは Join Game 受信後に送ること。Login Success
                    // 直後に送ると1.18サーバーが切断する（Stage D §14.2.1の実測）。
                    client.sendPacket(new MinecraftPacket(0x05, clientInformation757()));

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（757形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage757("hello from 1.18")));
                    client.sendPacket(new MinecraftPacket(0x11, move757()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽757クライアントがプロキシ経由で本物のバニラ1.19（内部759）に
     * 参加する。757→759 の直接変換で動くこと。
     */
    @Test
    void fake757ClientJoinsRealVanilla119ThroughProxy() throws Exception {
        Path realJar = findRealVanilla119Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.jar"), "1.19")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.18 to 1.19")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 759);
                assertEquals(759, proxy.internalProtocol());
                proxy.start();
                try (Fake757Client client = new Fake757Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(757, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart757("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（757形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    // 設定パケットは Join Game 受信後に送ること。Login Success
                    // 直後に送ると1.18サーバーが切断する（Stage D §14.2.1の実測）。
                    client.sendPacket(new MinecraftPacket(0x05, clientInformation757()));

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（757形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage757("hello from 1.18 to 1.19")));
                    client.sendPacket(new MinecraftPacket(0x11, move757()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽757クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。757→759→760→761→762→763 の合成変換で動くこと。
     */
    @Test
    void fake757ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1201Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.20.1.jar"), "1.20.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.18 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake757Client client = new Fake757Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(757, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart757("Hana")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（757形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    // 設定パケットは Join Game 受信後に送ること。Login Success
                    // 直後に送ると1.18サーバーが切断する（Stage D §14.2.1の実測）。
                    client.sendPacket(new MinecraftPacket(0x05, clientInformation757()));

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（757形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage757("hello from 1.18 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x11, move757()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽759クライアントがプロキシ経由で本物のバニラ1.18（内部757）に
     * 参加する。759→757 の逆向き変換で動くこと。
     */
    @Test
    void fake759ClientJoinsRealVanilla118ThroughProxy() throws Exception {
        Path realJar = findRealVanilla118Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.18.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.18.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.18.jar"), "1.18")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.18", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Ren")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Ren")
                        && chat.message().equals("hello from 1.19 to 1.18")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 757);
                assertEquals(757, proxy.internalProtocol());
                proxy.start();
                try (Fake759Client client = new Fake759Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(759, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart759("Ren")));

                    MinecraftPacket joinGame = client.waitForId(0x23, 30_000);
                    assertTrue(joinGame != null, "Join Game（759形式 0x23）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4a, 30_000);
                    assertTrue(spawn != null, "Spawn Position（759形式 0x4a）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x04,
                            chatMessage759("hello from 1.19 to 1.18")));
                    client.sendPacket(new MinecraftPacket(0x13, move759()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Ren の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽756クライアントがプロキシ経由で本物のバニラ1.17.1（内部756）に
     * 参加する。756→756 の素通しで動くこと。
     */
    @Test
    void fake756ClientJoinsRealVanilla1171ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1171Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.17.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.17.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.17.1.jar"), "1.17.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.17.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.17.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 756);
                assertEquals(756, proxy.internalProtocol());
                proxy.start();
                try (Fake756Client client = new Fake756Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(756, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart756("Steve")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（756形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    // 設定パケットは Join Game 受信後に送ること。Login Success
                    // 直後に送ると1.18サーバーが切断する（757 Stage E §15.2の実測）。
                    // 1.17.1でも同じ順序にする。
                    client.sendPacket(new MinecraftPacket(0x05, clientInformation756()));

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（756形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage756("hello from 1.17.1")));
                    client.sendPacket(new MinecraftPacket(0x11, move756()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽756クライアントがプロキシ経由で本物のバニラ1.18（内部757）に
     * 参加する。756→757 の直接変換で動くこと。
     */
    @Test
    void fake756ClientJoinsRealVanilla118ThroughProxy() throws Exception {
        Path realJar = findRealVanilla118Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.18.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.18.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.18.jar"), "1.18")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.18", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.17.1 to 1.18")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 757);
                assertEquals(757, proxy.internalProtocol());
                proxy.start();
                try (Fake756Client client = new Fake756Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(756, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart756("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（756形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    // 設定パケットは Join Game 受信後に送ること。Login Success
                    // 直後に送ると1.18サーバーが切断する（757 Stage E §15.2の実測）。
                    client.sendPacket(new MinecraftPacket(0x05, clientInformation756()));

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（756形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage756("hello from 1.17.1 to 1.18")));
                    client.sendPacket(new MinecraftPacket(0x11, move756()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽756クライアントがプロキシ経由で本物のバニラ1.19（内部759）に
     * 参加する。756→757→759 の連鎖変換で動くこと。
     */
    @Test
    void fake756ClientJoinsRealVanilla119ThroughProxy() throws Exception {
        Path realJar = findRealVanilla119Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.19.jar"), "1.19")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.17.1 to 1.19")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 759);
                assertEquals(759, proxy.internalProtocol());
                proxy.start();
                try (Fake756Client client = new Fake756Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(756, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart756("Hana")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（756形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    // 設定パケットは Join Game 受信後に送ること。Login Success
                    // 直後に送ると1.18サーバーが切断する（757 Stage E §15.2の実測）。
                    client.sendPacket(new MinecraftPacket(0x05, clientInformation756()));

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（756形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage756("hello from 1.17.1 to 1.19")));
                    client.sendPacket(new MinecraftPacket(0x11, move756()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Hana の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 偽757クライアントがプロキシ経由で本物のバニラ1.17.1（内部756）に
     * 参加する。757→756 の逆向き変換で動くこと。
     */
    @Test
    void fake757ClientJoinsRealVanilla1171ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1171Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.17.1.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.17.1.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.17.1.jar"), "1.17.1")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.17.1", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Ren")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Ren")
                        && chat.message().equals("hello from 1.18 to 1.17.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 756);
                assertEquals(756, proxy.internalProtocol());
                proxy.start();
                try (Fake757Client client = new Fake757Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(757, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart757("Ren")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（757形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    // 設定パケットは Join Game 受信後に送ること。Login Success
                    // 直後に送ると1.18サーバーが切断する（757 Stage E §15.2の実測）。
                    client.sendPacket(new MinecraftPacket(0x05, clientInformation757()));

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（756形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage757("hello from 1.18 to 1.17.1")));
                    client.sendPacket(new MinecraftPacket(0x11, move757()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Ren の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }


}
