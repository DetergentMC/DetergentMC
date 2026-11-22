package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.protocol.ClientProxy;
import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.VarInt;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Surfactant転送の受け口（バッチ4）の実バニラ統合テスト。
 * {@code forwarding.secret} を設定した DetergentMC（ClientProxy＋
 * 実バニラ1.20.4）の前段に立つ偽クライアントが、Surfactantの代わりに
 * 転送確認へ署名付きで応答し、実際に参加できることを確かめる。
 * あわせて、内部バニラの usercache が名前由来の offline UUID のままに
 * なること（素性の採用範囲の限界）も記録する。
 * 署名を改ざんした応答は切断され、参加できないことも確かめる。
 *
 * <p>本物のバニラjar（{@code run/vanilla/server-1.20.4.jar}）が無い
 * 環境ではスキップする（偽Mojangが既存jarのSHA-1を返すだけ）。
 */
@Timeout(600)
class ProxyForwardingIntegrationTest {

    private static final String SECRET = "integration-secret";
    private static final UUID REAL_UUID =
            UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String NAME = "FwdAlex";

    @TempDir
    Path tempDir;

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

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** テスト側で独立に署名して転送ワイヤを作る（Surfactant v1形式）。 */
    private static byte[] forwardingWire(String secret, String json) throws Exception {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(jsonBytes);
        byte[] wire = new byte[1 + signature.length + jsonBytes.length];
        wire[0] = 1;
        System.arraycopy(signature, 0, wire, 1, signature.length);
        System.arraycopy(jsonBytes, 0, wire, 1 + signature.length, jsonBytes.length);
        return wire;
    }

    private static String forwardingJson() {
        return "{\"address\":\"198.51.100.23:40000\",\"uuid\":\"" + REAL_UUID
                + "\",\"name\":\"" + NAME + "\",\"properties\":[{"
                + "\"name\":\"textures\",\"value\":\"fwd-texture-value\","
                + "\"signature\":\"fwd-sig\"}]}";
    }

    /** バニラを起動してホストを返す（呼び出し側で stop する）。 */
    private VanillaServerHost startVanilla(Path vanillaDir, int vanillaPort,
            CountDownLatch joined) throws Exception {
        Path realJar = findRealVanillaJar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));
        FakeMojang mojang = new FakeMojang(vanillaDir.resolve("server-1.20.4.jar"));
        VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                false, 10, "DetergentMC test", null, 512,
                new VanillaDownloader(mojang.manifestUrl()));
        host.addListener(event -> {
            if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                    && joinedEvent.name().equals(NAME)) {
                joined.countDown();
            }
        });
        host.start();
        assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
        return host;
    }

    @Test
    void forwardedPlayerJoinsRealVanilla() throws Exception {
        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        Path vanillaDir = tempDir.resolve("vanilla-ok");
        VanillaServerHost host = startVanilla(vanillaDir, vanillaPort, joined);
        try {
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false);
            proxy.setForwardingSecret(SECRET);
            proxy.start();
            try (Fake765Client client = new Fake765Client(proxyPort)) {
                client.sendPacket(new MinecraftPacket(0x00,
                        handshakePayload(765, 2, proxyPort)));
                client.sendPacket(new MinecraftPacket(0x00, loginStart765(NAME)));

                // 転送確認が来て、Surfactantの代わりに署名付きで応える。
                MinecraftPacket request = client.waitForId(0x04, 30_000);
                assertNotNull(request, "Login Plugin Request（0x04）が届きませんでした。");
                Reader requestReader = new Reader(request.payload());
                int transactionId = requestReader.readVarInt();
                assertEquals("surfactant:forwarding", requestReader.readString());
                Writer response = new Writer();
                response.writeVarInt(transactionId);
                response.writeBoolean(true);
                response.writeBytes(forwardingWire(SECRET, forwardingJson()));
                client.sendPacket(new MinecraftPacket(0x02, response.bytes()));

                // Login Success：UUIDはバニラの offline UUID、プロパティは
                // 転送されたもの（DetergentMC層の採用範囲）。
                MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                assertNotNull(loginSuccess, "Login Success（0x02）が届きませんでした。");
                Reader successReader = new Reader(loginSuccess.payload());
                assertEquals(offlineUuid(NAME), successReader.readUuid(),
                        "内部バニラは名前由来の offline UUID を割り当てるはず");
                assertEquals(NAME, successReader.readString());
                assertEquals(1, successReader.readVarInt(),
                        "転送されたプロパティが Login Success に入るはず");
                assertEquals("textures", successReader.readString());
                assertEquals("fwd-texture-value", successReader.readString());

                client.beginConfiguration();
                client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                client.sendPacket(new MinecraftPacket(0x00, clientInformation765()));

                MinecraftPacket joinGame = client.waitForId(0x29, 30_000);
                assertNotNull(joinGame, "Join Game（765形式 0x29）が届きませんでした。");
                assertTrue(joined.await(30, TimeUnit.SECONDS),
                        "バニラログに " + NAME + " の参加が現れませんでした。");

                // 採用範囲の記録：バニラの usercache は offline UUID が鍵で、
                // 本物のUUIDは入らない（DetergentMC層の素性とは別物）。
                String usercache = waitForUsercache(vanillaDir);
                assertTrue(usercache.contains(NAME), "usercache に名前が入るはず");
                assertTrue(usercache.contains(offlineUuid(NAME).toString()),
                        "usercache のUUIDは offline UUID のはず: " + usercache);
                assertFalse(usercache.contains(REAL_UUID.toString()),
                        "usercache に本物のUUIDは入らない（採用範囲の限界）");
            } finally {
                proxy.stop();
            }
        } finally {
            host.stop();
        }
    }

    @Test
    void tamperedForwardingCannotJoinRealVanilla() throws Exception {
        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        Path vanillaDir = tempDir.resolve("vanilla-ng");
        VanillaServerHost host = startVanilla(vanillaDir, vanillaPort, joined);
        try {
            ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false);
            proxy.setForwardingSecret(SECRET);
            proxy.start();
            try (Fake765Client client = new Fake765Client(proxyPort)) {
                client.sendPacket(new MinecraftPacket(0x00,
                        handshakePayload(765, 2, proxyPort)));
                client.sendPacket(new MinecraftPacket(0x00, loginStart765(NAME)));
                MinecraftPacket request = client.waitForId(0x04, 30_000);
                assertNotNull(request, "Login Plugin Request（0x04）が届きませんでした。");
                int transactionId = new Reader(request.payload()).readVarInt();
                Writer response = new Writer();
                response.writeVarInt(transactionId);
                response.writeBoolean(true);
                response.writeBytes(forwardingWire("wrong-secret", forwardingJson()));
                client.sendPacket(new MinecraftPacket(0x02, response.bytes()));

                MinecraftPacket disconnect = client.waitForId(0x00, 30_000);
                assertNotNull(disconnect, "Login Disconnect（0x00）が届くはずです。");
                String text = new String(disconnect.payload(), StandardCharsets.UTF_8);
                assertTrue(text.contains("Surfactant"),
                        "切断理由に Surfactant の案内が入るはず: " + text);
                assertFalse(joined.await(5, TimeUnit.SECONDS),
                        "署名が違う接続がバニラへ参加できてはいけない");
            } finally {
                proxy.stop();
            }
        } finally {
            host.stop();
        }
    }

    private static String waitForUsercache(Path vanillaDir) throws Exception {
        Path usercache = vanillaDir.resolve("usercache.json");
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.isRegularFile(usercache)) {
                String content = Files.readString(usercache);
                if (content.contains(NAME)) {
                    return content;
                }
            }
            Thread.sleep(500);
        }
        return Files.isRegularFile(usercache) ? Files.readString(usercache) : "";
    }

    // ------------------------------------------------------------------
    // 偽Mojang・偽クライアント（ProxyIntegrationTest と同じ流儀）
    // ------------------------------------------------------------------

    /** 偽Mojang：マニフェストとバージョンJSONだけを返し、既存jarのSHA-1を名乗る。 */
    private static final class FakeMojang implements AutoCloseable {
        final HttpServer server;
        final Path jarPath;

        FakeMojang(Path jarPath) throws Exception {
            this.jarPath = jarPath;
            String jarSha1 = VanillaDownloader.sha1(jarPath);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;
            server.createContext("/manifest.json", exchange -> {
                byte[] body = ("{\"versions\":[{\"id\": \"1.20.4\", \"type\": \"release\", "
                        + "\"url\": \"" + base + "/version.json\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                send(exchange, body);
            });
            server.createContext("/version.json", exchange -> {
                byte[] body = ("{\"downloads\": {\"server\": {\"sha1\": \"" + jarSha1
                        + "\", \"size\": " + Files.size(jarPath)
                        + ", \"url\": \"" + base + "/server.jar\"}}}")
                        .getBytes(StandardCharsets.UTF_8);
                send(exchange, body);
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
                byte[] body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
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

    /** 生ソケットの765クライアント（ProxyIntegrationTest の Fake765Client と同じ作り）。 */
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
                            response.writeLong(new Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x03, response.bytes()));
                        } else if (packet.id() == 0x02) { // Finish Configuration
                            sendPacket(new MinecraftPacket(0x02, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x07) { // Add Resource Pack
                            UUID uuid = new Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3); // accepted
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == 0x24) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new Reader(packet.payload()).readLong());
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

        void beginConfiguration() {
            state = 1;
        }

        synchronized void sendPacket(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

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
                throw new IOException("VarIntの読み取り中にストリームが終わりました。");
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
                throw new IOException("ストリームが途中で終わりました。");
            }
            read += n;
        }
        return bytes;
    }

    private static byte[] handshakePayload(int protocol, int nextState, int port) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("localhost");
        writer.writeUnsignedShort(port);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    private static byte[] loginStart765(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        return writer.bytes();
    }

    private static byte[] clientInformation765() {
        Writer writer = new Writer();
        writer.writeString("ja_JP");
        writer.writeByte(8);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeUnsignedByte(0x7F);
        writer.writeVarInt(1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        return writer.bytes();
    }
}
