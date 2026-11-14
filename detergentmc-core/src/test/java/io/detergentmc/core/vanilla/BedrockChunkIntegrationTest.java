package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.bedrock.BedrockChunkTranslator;
import io.detergentmc.bedrock.JavaBackendConnection;
import io.detergentmc.protocol.PacketIO;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * UDP を使わない Bedrock チャンク統合テスト：Java バックエンドを本物のバニラ
 * 1.20.4 に直接つなげ、届いた Java チャンクを {@link BedrockChunkTranslator}
 * で Bedrock LevelChunk に翻訳し、スポーン地点の地面ブロックが読めることを
 * 確認する。UDP が禁止されている環境でも動く（BedrockIntegrationTest は
 * UDP 送信が必要でサンドボックスではスキップされる）。
 */
@Timeout(600)
class BedrockChunkIntegrationTest {

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

    /** 偽Mojang：BedrockIntegrationTest と同じく、既存 jar の SHA-1 だけを返す。 */
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

    @Test
    void realVanillaChunkTranslatesToBedrockLevelChunk() throws Exception {
        Path realJar = findRealVanillaJar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch spawnChunkSeen = new CountDownLatch(1);
        Map<String, byte[]> chunks = new ConcurrentHashMap<>();
        List<double[]> spawns = new CopyOnWriteArrayList<>();
        List<int[]> dimensions = new CopyOnWriteArrayList<>();
        List<List<String>> biomeLists = new CopyOnWriteArrayList<>();

        try (FakeMojang mojang = new FakeMojang(vanillaDir.resolve("server-1.20.4.jar"))) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");

                JavaBackendConnection.Listener listener =
                        new JavaBackendConnection.Listener() {
                            @Override
                            public void onJoined(int javaEntityId) {
                                joined.countDown();
                            }

                            @Override
                            public void onSpawnPosition(double x, double y, double z) {
                                spawns.add(new double[]{x, y, z});
                            }

                            @Override
                            public void onChunk(byte[] javaChunkPayload) {
                                PacketIO.Reader reader =
                                        new PacketIO.Reader(javaChunkPayload);
                                int chunkX = reader.readInt();
                                int chunkZ = reader.readInt();
                                chunks.put(chunkX + "," + chunkZ, javaChunkPayload.clone());
                                // スポーン位置が分かっていれば、そのチャンクが来たか見る。
                                if (!spawns.isEmpty()) {
                                    double[] spawn = spawns.get(0);
                                    int spawnChunkX = (int) Math.floor(spawn[0] / 16.0);
                                    int spawnChunkZ = (int) Math.floor(spawn[2] / 16.0);
                                    if (chunkX == spawnChunkX && chunkZ == spawnChunkZ) {
                                        spawnChunkSeen.countDown();
                                    }
                                }
                            }

                            @Override
                            public void onDimensionInfo(int minY, int height,
                                    List<String> biomeNames) {
                                dimensions.add(new int[]{minY, height});
                                biomeLists.add(biomeNames);
                            }

                            @Override
                            public void onChat(String sender, String message) {
                                // このテストでは使わない。
                            }

                            @Override
                            public void onTeleport(double x, double y, double z,
                                    float yaw, float pitch) {
                                // このテストでは使わない。
                            }

                            @Override
                            public void onDisconnected(String reason) {
                                // このテストでは使わない。
                            }
                        };
                UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
                JavaBackendConnection backend = new JavaBackendConnection(
                        "127.0.0.1", vanillaPort, 765, ".Steve", uuid, listener);
                backend.connect();
                try {
                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "Java バックエンドがバニラに参加しませんでした。");
                    assertTrue(spawnChunkSeen.await(90, TimeUnit.SECONDS),
                            "スポーン地点のチャンクが届きませんでした。届いたチャンク: "
                                    + chunks.keySet());
                    // オーバーワールドの次元情報（minY -64・高さ 384）が届いていること。
                    assertTrue(dimensions.stream()
                                    .anyMatch(d -> d[0] == -64 && d[1] == 384),
                            "オーバーワールドの次元情報が届くこと: " + dimensions);

                    double[] spawn = spawns.get(0);
                    int spawnChunkX = (int) Math.floor(spawn[0] / 16.0);
                    int spawnChunkZ = (int) Math.floor(spawn[2] / 16.0);
                    byte[] javaChunk = chunks.get(spawnChunkX + "," + spawnChunkZ);
                    assertNotNull(javaChunk, "スポーン地点のチャンクが取得できませんでした。");
                    int minY = -64;
                    int height = 384;
                    List<String> biomeNames = null;
                    for (int i = dimensions.size() - 1; i >= 0; i--) {
                        if (dimensions.get(i)[0] == -64 && dimensions.get(i)[1] == 384) {
                            minY = dimensions.get(i)[0];
                            height = dimensions.get(i)[1];
                            biomeNames = biomeLists.get(i);
                            break;
                        }
                    }
                    BedrockChunkTranslator.TranslatedChunk translated =
                            BedrockChunkTranslator.translate(
                                    new BedrockChunkTranslator.ChunkInput(
                                            spawnChunkX, spawnChunkZ, javaChunk, 765,
                                            minY, height, biomeNames));
                    assertEquals(24, translated.subChunkCount(),
                            "オーバーワールドは 24 サブチャンク");
                    LevelChunkPayloadDecoder decoded = LevelChunkPayloadDecoder.decode(
                            translated.payload(), translated.subChunkCount());
                    // チャンクに地形ブロック（空気以外）が含まれていること。
                    assertTrue(decoded.runtimeIds.stream().anyMatch(id -> id != 11881),
                            "チャンクに地形ブロックが含まれていません: " + decoded.runtimeIds);
                    // スポーン地点の列で、一番上の空気でないブロックが地表であること。
                    int localX = (int) Math.floor(spawn[0]) & 15;
                    int localZ = (int) Math.floor(spawn[2]) & 15;
                    int groundY = -1;
                    int groundId = 11881;
                    for (int y = 319; y >= -64; y--) {
                        int sectionY = Math.floorDiv(y, 16);
                        int yLocal = Math.floorMod(y, 16);
                        int id = decoded.blockAt(sectionY, localX, yLocal, localZ);
                        if (id != 11881) {
                            groundY = y;
                            groundId = id;
                            break;
                        }
                    }
                    assertTrue(groundY >= -64,
                            "スポーン地点の列に地面がありません（スポーン: "
                                    + spawn[0] + ", " + spawn[1] + ", " + spawn[2] + "）。");
                    assertTrue(groundId != 11881,
                            "スポーン地点の地面ブロックが空気ではありません: y=" + groundY
                                    + " id=" + groundId);
                } finally {
                    backend.close();
                }
            } finally {
                host.stop();
            }
        }
    }
}
