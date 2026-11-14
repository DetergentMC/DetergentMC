package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.bedrock.BedrockBatch;
import io.detergentmc.bedrock.BedrockBatch.GamePacket;
import io.detergentmc.bedrock.BedrockConfig;
import io.detergentmc.bedrock.BedrockMotd;
import io.detergentmc.bedrock.BedrockPackets;
import io.detergentmc.bedrock.BedrockProtocol;
import io.detergentmc.bedrock.BedrockServer;
import io.detergentmc.bedrock.BedrockVarInt;
import io.detergentmc.bedrock.RakNetCodec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * 偽 Bedrock クライアント（生 UDP）→ DetergentMC Bedrock 層 →
 * 本物のバニラ 1.20.4 子プロセス、の統合テスト。
 *
 * <p>ログイン（Floodgate 風の {@code .Steve} として参加）→スポーン→
 * チャット（バニラログに現れること）→移動までを確認する。実機の Bedrock
 * クライアントではないが、Java バックエンドが本物のバニラとログイン〜
 * 設定フェーズ〜プレイを話せることがこのテストで検証される。
 *
 * <p>本物のバニラ jar（{@code run/vanilla/server-1.20.4.jar}）が無い環境では
 * スキップする。また UDP 送信が禁止されている環境（このサンドボックスなど）
 * でもスキップする（ルーティング自体は bedrock モジュールの
 * BedrockServerTest#routingWithoutNetwork で検証済み）。
 */
@Timeout(600)
class BedrockIntegrationTest {

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

    private static boolean udpSendWorks() {
        try (DatagramSocket probe = new DatagramSocket(0,
                InetAddress.getByName("127.0.0.1"))) {
            probe.send(new DatagramPacket(new byte[]{0}, 1,
                    new InetSocketAddress("127.0.0.1", 9)));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** 偽Mojang：ProxyIntegrationTest と同じく、既存 jar の SHA-1 だけを返す。 */
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

        private static void send(com.sun.net.httpserver.HttpExchange exchange, byte[] body)
                throws IOException {
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

    /** 偽 Bedrock クライアント（生 UDP）。ProxyIntegrationTest の偽 Java クライアント相当。 */
    private static final class FakeBedrockClient implements AutoCloseable {
        final DatagramSocket socket;
        final InetSocketAddress serverAddress;
        final BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
        volatile boolean closed;
        int sendSequence;
        int messageIndex;
        final long clientGuid = -777777L;
        /** 分割フレームの繋ぎ直し（StartGame など大きいパケット用）。 */
        final RakNetCodec.SplitReassembler splits = new RakNetCodec.SplitReassembler();

        FakeBedrockClient(int serverPort) throws IOException {
            socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
            socket.setSoTimeout(30_000);
            serverAddress = new InetSocketAddress("127.0.0.1", serverPort);
            Thread reader = new Thread(() -> {
                byte[] buffer = new byte[65535];
                while (!closed) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                    } catch (IOException e) {
                        return;
                    }
                    byte[] datagram = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(),
                            datagram, 0, datagram.length);
                    received.add(datagram);
                }
            }, "fake-bedrock-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        void send(byte[] datagram) throws IOException {
            socket.send(new DatagramPacket(datagram, datagram.length, serverAddress));
        }

        void sendFrameSet(byte[] payload) throws IOException {
            send(RakNetCodec.buildFrameSet(sendSequence++ & 0xFFFFFF,
                    RakNetCodec.RELIABILITY_RELIABLE, messageIndex++, payload));
        }

        void sendGamePacket(int id, byte[] payload) throws IOException {
            sendFrameSet(BedrockBatch.encodeOne(id, payload));
        }

        /** 指定 ID のゲームパケットが届くまで待つ。 */
        GamePacket waitForGamePacket(int id, long timeoutMillis) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                byte[] datagram = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (datagram == null) {
                    return null;
                }
                if (RakNetCodec.isFrameSet(datagram)) {
                    for (byte[] payload :
                            RakNetCodec.parseFrameSetPayloads(datagram, splits)) {
                        if (payload.length > 0
                                && (payload[0] & 0xFF) == BedrockProtocol.ID_BATCH) {
                            for (GamePacket packet : BedrockBatch.decode(payload)) {
                                if (packet.id() == id) {
                                    return packet;
                                }
                            }
                        }
                    }
                }
            }
        }

        /** 指定した先頭バイトのデータグラムが届くまで待つ。 */
        byte[] waitForFirstByte(int firstByte, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                byte[] datagram = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (datagram == null) {
                    return null;
                }
                if (datagram.length > 0 && (datagram[0] & 0xFF) == firstByte) {
                    return datagram;
                }
            }
        }

        @Override
        public void close() {
            closed = true;
            socket.close();
        }
    }

    /** Login パケットのペイロードを作る（JWT は TestJwt と同じ作りの簡易版）。 */
    private static byte[] loginPayload() {
        String header = base64Url("{\"alg\":\"ES384\",\"x5u\":\"https://example.invalid/key\"}");
        String extra = base64Url("{\"extraData\":{\"displayName\":\"Steve\","
                + "\"XUID\":\"2535461234567890\","
                + "\"identity\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"}}");
        String jwt = header + "." + extra + ".c2ln";
        String chain = "{\"chain\":[\"" + jwt + "\"]}";
        BedrockVarInt.ByteWriter tokens = new BedrockVarInt.ByteWriter();
        BedrockVarInt.writeString(tokens, chain);
        BedrockVarInt.writeString(tokens, header + "." + base64Url("{}") + ".c2ln");
        BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
        out.writeBigInt(630);
        byte[] tokenBytes = tokens.bytes();
        BedrockVarInt.writeUnsignedVarInt(out, tokenBytes.length);
        out.writeBytes(tokenBytes);
        return out.bytes();
    }

    private static String base64Url(String text) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void fakeBedrockClientJoinsRealVanilla() throws Exception {
        Path realJar = findRealVanillaJar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");
        assumeTrue(udpSendWorks(),
                "この環境では UDP 送信が禁止されているためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(vanillaDir.resolve("server-1.20.4.jar"))) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals(".Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals(".Steve")
                        && chat.message().equals("hello from bedrock")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
            BedrockConfig config = new BedrockConfig(true, 0, "DetergentMC test", ".",
                    10, "DetergentMC test", "127.0.0.1", vanillaPort, 765,
                    BedrockConfig.DEFAULT_CHUNK_RADIUS);
            BedrockServer bedrock = new BedrockServer(config);
            bedrock.start();
            try (FakeBedrockClient client = new FakeBedrockClient(bedrock.localPort())) {
                // Unconnected Ping → Pong（MOTD）。
                BedrockVarInt.ByteWriter ping = new BedrockVarInt.ByteWriter();
                ping.writeByte(BedrockProtocol.ID_UNCONNECTED_PING);
                ping.writeBigLong(777L);
                ping.writeBytes(BedrockProtocol.RAKNET_MAGIC);
                ping.writeBigLong(client.clientGuid);
                client.send(ping.bytes());
                byte[] pong = client.waitForFirstByte(
                        BedrockProtocol.ID_UNCONNECTED_PONG, 10_000);
                assertNotNull(pong, "Unconnected Pong が返りませんでした。");
                BedrockVarInt.ByteReader pongReader =
                        new BedrockVarInt.ByteReader(pong, 1);
                pongReader.readBigLong();
                pongReader.readBigLong();
                pongReader.readBytes(16);
                int motdLength = pongReader.readBigUnsignedShort();
                String motd = new String(pongReader.readBytes(motdLength),
                        StandardCharsets.UTF_8);
                assertNotNull(BedrockMotd.parse(motd), "MOTD: " + motd);

                // Open Connection 1 / 2。
                BedrockVarInt.ByteWriter request1 = new BedrockVarInt.ByteWriter();
                request1.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_1);
                request1.writeBytes(BedrockProtocol.RAKNET_MAGIC);
                request1.writeByte(11);
                for (int i = 0; i < 200; i++) {
                    request1.writeByte(0);
                }
                client.send(request1.bytes());
                assertNotNull(client.waitForFirstByte(
                                BedrockProtocol.ID_OPEN_CONNECTION_REPLY_1, 10_000),
                        "Open Connection Reply 1 が返りませんでした。");
                BedrockVarInt.ByteWriter request2 = new BedrockVarInt.ByteWriter();
                request2.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_2);
                request2.writeBytes(BedrockProtocol.RAKNET_MAGIC);
                request2.writeBytes(RakNetCodec.encodeAddress(
                        new InetSocketAddress("127.0.0.1", bedrock.localPort())));
                request2.writeByte(0x05);
                request2.writeByte(0xdc);
                request2.writeBigLong(client.clientGuid);
                client.send(request2.bytes());
                assertNotNull(client.waitForFirstByte(
                                BedrockProtocol.ID_OPEN_CONNECTION_REPLY_2, 10_000),
                        "Open Connection Reply 2 が返りませんでした。");

                // Connection Request → Accepted、New Incoming Connection。
                BedrockVarInt.ByteWriter connectionRequest = new BedrockVarInt.ByteWriter();
                connectionRequest.writeByte(BedrockProtocol.ID_CONNECTION_REQUEST);
                connectionRequest.writeBigLong(client.clientGuid);
                connectionRequest.writeBigLong(1000L);
                connectionRequest.writeBoolean(false);
                client.sendFrameSet(connectionRequest.bytes());
                Thread.sleep(500);
                BedrockVarInt.ByteWriter incoming = new BedrockVarInt.ByteWriter();
                incoming.writeByte(BedrockProtocol.ID_NEW_INCOMING_CONNECTION);
                incoming.writeBytes(RakNetCodec.encodeAddress(
                        new InetSocketAddress("127.0.0.1", bedrock.localPort())));
                incoming.writeBigLong(1000L);
                incoming.writeBigLong(2000L);
                client.sendFrameSet(incoming.bytes());

                // Login → Play Status（login_success）＋Resource Packs Info。
                client.sendGamePacket(BedrockProtocol.PACKET_LOGIN, loginPayload());
                GamePacket playStatus = client.waitForGamePacket(
                        BedrockProtocol.PACKET_PLAY_STATUS, 30_000);
                assertNotNull(playStatus, "Play Status（login_success）が届きませんでした。");
                assertEquals(0,
                        new BedrockVarInt.ByteReader(playStatus.payload()).readBigInt());
                assertNotNull(client.waitForGamePacket(
                                BedrockProtocol.PACKET_RESOURCE_PACKS_INFO, 30_000),
                        "Resource Packs Info が届きませんでした。");

                // リソースパックのやり取り→Start Game→スポーン。
                client.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                        new byte[]{3});
                assertNotNull(client.waitForGamePacket(
                                BedrockProtocol.PACKET_RESOURCE_PACK_STACK, 30_000),
                        "Resource Pack Stack が届きませんでした。");
                client.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                        new byte[]{4});
                GamePacket startGame = client.waitForGamePacket(
                        BedrockProtocol.PACKET_START_GAME, 30_000);
                assertNotNull(startGame, "Start Game が届きませんでした。");
                assertNotNull(client.waitForGamePacket(
                                BedrockProtocol.PACKET_PLAY_STATUS, 30_000),
                        "Play Status（player_spawn）が届きませんでした。");

                // チャンク要求を送り、スポーン周辺の LevelChunk が届くこと。
                BedrockVarInt.ByteWriter radiusRequest = new BedrockVarInt.ByteWriter();
                BedrockVarInt.writeZigzag32(radiusRequest, 6);
                radiusRequest.writeByte(8);
                client.sendGamePacket(BedrockProtocol.PACKET_REQUEST_CHUNK_RADIUS,
                        radiusRequest.bytes());
                GamePacket levelChunk = client.waitForGamePacket(
                        BedrockProtocol.PACKET_LEVEL_CHUNK, 90_000);
                assertNotNull(levelChunk, "LevelChunk が届きませんでした。");
                BedrockVarInt.ByteReader chunkReader =
                        new BedrockVarInt.ByteReader(levelChunk.payload());
                int chunkX = BedrockVarInt.readZigzag32(chunkReader);
                int chunkZ = BedrockVarInt.readZigzag32(chunkReader);
                int subChunkCount = (int) BedrockVarInt.readUnsignedVarInt(chunkReader);
                assertEquals(24, subChunkCount, "オーバーワールドは 24 サブチャンク");
                assertEquals(false, chunkReader.readBoolean(), "blob キャッシュは使わない");
                long payloadLength = BedrockVarInt.readUnsignedVarInt(chunkReader);
                byte[] payload = chunkReader.readBytes((int) payloadLength);
                LevelChunkPayloadDecoder decoded =
                        LevelChunkPayloadDecoder.decode(payload, subChunkCount);
                // 座標はスポーン付近（半径 6＋余裕）であること。
                assertTrue(Math.abs(chunkX) <= 8 && Math.abs(chunkZ) <= 8,
                        "スポーン付近のチャンクであること: (" + chunkX + ", " + chunkZ + ")");
                // 空気（11881）以外に地形ブロックが少なくとも 1 種類含まれていること。
                assertTrue(decoded.runtimeIds.stream().anyMatch(id -> id != 11881),
                        "チャンクに地形ブロックが含まれていません: " + decoded.runtimeIds);

                // チャットと移動を送る。
                client.sendGamePacket(BedrockProtocol.PACKET_TEXT,
                        BedrockPackets.encodeTextChat("Steve", "hello from bedrock"));
                client.sendGamePacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                        BedrockPackets.encodeMovePlayerTeleport(1L, 8.5f, 65.0f, 8.5f, 0f, 0f));

                assertTrue(joined.await(30, TimeUnit.SECONDS),
                        "バニラログに .Steve の参加が現れませんでした。");
                assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                        "バニラログに Bedrock からのチャットが現れませんでした。");
            } finally {
                bedrock.stop();
            }
            } finally {
                host.stop();
            }
        }
    }
}
