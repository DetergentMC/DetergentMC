package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.VarInt;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本物のバニラ1.20.4が送る Update Attributes（0x71）のキー文字列が
 * {@code minecraft:generic.movement_speed} のような接頭辞付きであることを
 * 確認する回帰テスト。プロキシの 765↔767 属性変換はこの形式を前提に
 * している（平文だと本物のサーバーからの属性が全部落とされる）。
 * 2026-11-10に本物のバニラ1.20.4から受け取って確認した。
 */
class AttributeKeyCaptureTest {

    @TempDir
    Path tempDir;

    /** 1.20.4 は Java 17 でも動くが、他の統合テストと同じ作法にしておく。 */
    @BeforeEach
    void ensureJava21() {
        Java21TestSupport.ensureJava21Home();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Path findRealVanilla1204Jar() {
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

    private static byte[] handshakePayload(int protocol, int nextState, int port) {
        Writer writer = new Writer();
        writer.writeVarInt(protocol);
        writer.writeString("127.0.0.1");
        writer.writeShort(port);
        writer.writeVarInt(nextState);
        return writer.bytes();
    }

    private static byte[] loginStart(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeUuid(UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
        return writer.bytes();
    }

    /** 765 の Client Information は8フィールド。particleStatus は766から。 */
    private static byte[] clientInformation() {
        Writer writer = new Writer();
        writer.writeString("en_US");
        writer.writeByte(8);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeByte(0x7F);
        writer.writeVarInt(1);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        return writer.bytes();
    }

    /** 最小の生クライアント：受け取ったパケットを全部ためる。 */
    private static final class RawClient implements Closeable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;

        RawClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "scratch-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    int length = readVarInt(in);
                    byte[] frame = readExact(in, length);
                    byte[] lengthBytes = VarInt.encode(length);
                    byte[] all = new byte[lengthBytes.length + frame.length];
                    System.arraycopy(lengthBytes, 0, all, 0, lengthBytes.length);
                    System.arraycopy(frame, 0, all, lengthBytes.length, frame.length);
                    MinecraftPacket packet = MinecraftPacket.decodeOne(all, 0).packet();
                    // 765 の設定フェーズの ID（766／767 と違うものがある）：
                    // Keep Alive 0x03、Ping 0x04、Finish 0x02、Registry Data 0x05、
                    // Tags 0x09。応答は Keep Alive→0x03、Ping→0x04、Finish→0x02。
                    // プレイ：Keep Alive 0x24→0x15、Ping 0x33→0x24。
                    if (packet.id() == 0x03 && state == 1) { // 設定 Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x03, response.bytes()));
                    } else if (packet.id() == 0x04 && state == 1) { // 設定 Ping
                        Writer response = new Writer();
                        response.writeLong(new Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x04, response.bytes()));
                    } else if (packet.id() == 0x02 && state == 1) { // Finish Configuration
                        sendPacket(new MinecraftPacket(0x02, new byte[0]));
                        state = 2;
                    } else if (packet.id() == 0x24 && state == 2) { // プレイ Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x15, response.bytes()));
                    } else if (packet.id() == 0x33 && state == 2) { // プレイ Ping
                        Writer response = new Writer();
                        response.writeLong(new Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x24, response.bytes()));
                    }
                    received.add(packet);
                }
            } catch (IOException e) {
                // 切断。
            }
        }

        private static int readVarInt(InputStream input) throws IOException {
            int value = 0;
            int position = 0;
            while (true) {
                int b = input.read();
                if (b == -1) {
                    throw new IOException("EOF");
                }
                value |= (b & 0x7F) << position;
                if ((b & 0x80) == 0) {
                    return value;
                }
                position += 7;
                if (position >= 35) {
                    throw new IOException("VarInt too long");
                }
            }
        }

        private static byte[] readExact(InputStream input, int length) throws IOException {
            byte[] result = new byte[length];
            int off = 0;
            while (off < length) {
                int n = input.read(result, off, length - off);
                if (n == -1) {
                    throw new IOException("EOF");
                }
                off += n;
            }
            return result;
        }

        /** Login Acknowledged を送る前に呼ぶ。 */
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

    /** 偽Mojang：マニフェストとバージョンJSONだけを返す。 */
    private static final class FakeMojang implements Closeable {
        private final HttpServer server;
        private final String base;
        private final String version;

        FakeMojang(Path jar, String version) throws IOException {
            this.version = version;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            String sha1 = VanillaDownloader.sha1(jar);
            base = "http://127.0.0.1:" + server.getAddress().getPort();
            server.createContext("/manifest.json", exchange -> {
                String body = "{\"latest\":{\"release\":\"" + version + "\",\"snapshot\":\""
                        + version + "\"},\"versions\":[{\"id\":\"" + version
                        + "\",\"type\":\"release\",\"url\":\"" + base + "/version.json\",\"time\":\""
                        + "2024-01-01T00:00:00+00:00\",\"releaseTime\":\"2024-01-01T00:00:00+00:00\"}]}";
                send(exchange, body.getBytes(StandardCharsets.UTF_8));
            });
            server.createContext("/version.json", exchange -> {
                String body = "{\"id\":\"" + version + "\",\"type\":\"release\",\"downloads\":{\"server\":"
                        + "{\"sha1\":\"" + sha1 + "\",\"size\":0,\"url\":\"" + base + "/server.jar\"}},"
                        + "\"javaVersion\":{\"component\":\"java-runtime-alpha\",\"majorVersion\":17}}";
                send(exchange, body.getBytes(StandardCharsets.UTF_8));
            });
            server.createContext("/server.jar", exchange -> {
                send(exchange, Files.readAllBytes(jar));
            });
            server.start();
        }

        String manifestUrl() {
            return base + "/manifest.json";
        }

        private static void send(com.sun.net.httpserver.HttpExchange exchange, byte[] body)
                throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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

    @Test
    void captureUpdateAttributesKeysFromRealVanilla1204() throws Exception {
        Path realJar = findRealVanilla1204Jar();
        assumeTrue(realJar != null, "本物のバニラ 1.20.4 jar が無いためスキップします。");
        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));
        int vanillaPort = freePort();
        try (FakeMojang mojang = new FakeMojang(vanillaDir.resolve("server-1.20.4.jar"), "1.20.4")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                try (RawClient client = new RawClient(vanillaPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(765, 2, vanillaPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart("Capture")));
                    assertNotNull(client.waitForId(0x02, 30_000), "Login Success が届きませんでした。");
                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0]));
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation()));
                    // まず Join Game（765では 0x29）が届くか確認する。
                    MinecraftPacket joinGame = client.waitForId(0x29, 60_000);
                    assertNotNull(joinGame, "Join Game（0x29）が届きませんでした。");
                    // Update Attributes（765では 0x71）を受け取る。
                    MinecraftPacket attrs = client.waitForId(0x71, 60_000);
                    assertNotNull(attrs, "Update Attributes（0x71）が届きませんでした。");
                    Reader reader = new Reader(attrs.payload());
                    reader.readVarInt(); // entityId
                    int count = reader.readVarInt();
                    assertTrue(count >= 1, "属性が1件以上あること");
                    List<String> keys = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        String key = reader.readString();
                        keys.add(key);
                        double value = reader.readDouble();
                        int modCount = reader.readVarInt();
                        assertTrue(value == value, "値が NaN でないこと: " + key);
                        for (int j = 0; j < modCount; j++) {
                            reader.readBytes(16);
                            reader.readDouble();
                            reader.readByte();
                        }
                    }
                    // 本物の1.20.4は接頭辞付きの識別子を送る。
                    assertTrue(keys.stream().allMatch(k -> k.startsWith("minecraft:")),
                            "属性のキーはすべて minecraft: 付きであること: " + keys);
                    assertTrue(keys.contains("minecraft:generic.movement_speed"),
                            "移動速度の属性が含まれること: " + keys);
                }
            } finally {
                host.stop();
            }
        }
    }
}
