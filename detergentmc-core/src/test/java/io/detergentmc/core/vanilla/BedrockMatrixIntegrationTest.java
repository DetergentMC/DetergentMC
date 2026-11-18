package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.bedrock.BedrockChunkTranslator;
import io.detergentmc.bedrock.BedrockEntityTranslator;
import io.detergentmc.bedrock.BedrockPackets;
import io.detergentmc.bedrock.BedrockVarInt;
import io.detergentmc.bedrock.BedrockVersion;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bedrock × 内部 Java サーバーのマトリクス統合テスト（UDP 不使用）。
 * 代表の Bedrock 6 プロトコル（589・594・630・671・685・2193）×
 * 内部サーバー 15 バージョン（1.20.1〜26.1）の 90 通りについて、
 * 本物のバニラに参加・スポーン・チャット・移動・チャンク読み込みが
 * 通ることを確認する。Bedrock バージョンごとのアイテム翻訳も、
 * そのバージョンの Item 形式（従来／ItemV4）で読み直して検証する。
 * UDP エンドツーエンドはサンドボックスで禁止のため使わない。
 * 1 回のテストは 1 つの内部サーバーで 6 通りをまとめて回す。
 * 1.19系（759〜762）の内部サーバーはこのマトリクスに無い
 * （Bedrock→759〜762 は単体テストのみ）。
 */
@Timeout(600)
class BedrockMatrixIntegrationTest {

    /** マトリクスの Bedrock 側（代表 6 プロトコル）。 */
    private static final int[] BEDROCK_PROTOCOLS = {589, 594, 630, 671, 685, 2193};

    @TempDir
    Path tempDir;

    private static Path findRealVanillaJar(String version) {
        for (String candidate : List.of(
                "run/vanilla/server-" + version + ".jar",
                "../run/vanilla/server-" + version + ".jar",
                "../../run/vanilla/server-" + version + ".jar")) {
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

    /** 偽Mojang：指定バージョンの jar の SHA-1 だけを返す。 */
    private static final class FakeMojang implements AutoCloseable {
        final HttpServer server;
        final String version;

        FakeMojang(Path jarPath, String version) throws Exception {
            this.version = version;
            String jarSha1 = VanillaDownloader.sha1(jarPath);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;
            server.createContext("/manifest.json", exchange -> {
                byte[] body = ("{\"versions\":[{\"id\": \"" + version + "\", \"type\": \"release\", "
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

    /**
     * Java 21 の場所を探す。システムプロパティ→環境変数→作業ディレクトリ
     * 相対（テストの作業ディレクトリは detergentmc-core なので
     * ../../.tools/jdk-21.0.13+9 が ~/workspace/.tools/jdk-21.0.13+9
     * になる）→ user.home 相対の順に見る。テスト用 JVM の user.home は
     * /root になることがあるため、user.home だけには頼らない。
     */
    private static String findJava21Home() {
        String fromProperty = System.getProperty("detergentmc.java21.home");
        if (fromProperty != null && !fromProperty.isBlank()
                && isJavaExecutable(Path.of(fromProperty.strip()))) {
            return fromProperty.strip();
        }
        String fromEnv = System.getenv("DETERGENTMC_JAVA21_HOME");
        if (fromEnv != null && !fromEnv.isBlank()
                && isJavaExecutable(Path.of(fromEnv.strip()))) {
            return fromEnv.strip();
        }
        for (String relative : List.of(
                "../../.tools/jdk-21.0.13+9",
                "../.tools/jdk-21.0.13+9",
                ".tools/jdk-21.0.13+9")) {
            Path candidate = Path.of(relative).toAbsolutePath().normalize();
            if (isJavaExecutable(candidate)) {
                return candidate.toString();
            }
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Path candidate = Path.of(userHome, "workspace", ".tools", "jdk-21.0.13+9");
            if (isJavaExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static boolean isJavaExecutable(Path javaHome) {
        return Files.isExecutable(javaHome.resolve("bin").resolve("java"));
    }

    /** 1 つの組み合わせ（Bedrock プロトコル × 内部サーバー）の結果。 */
    private record ComboResult(int bedrockProtocol, int serverProtocol,
            int groundBlockId, int groundY) {
    }

    /** 現在の組み合わせで待っているチャット（バニラのログで確認する）。 */
    private final AtomicReference<String> expectedChat = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> chatLatch = new AtomicReference<>();

    @Test
    void matrixAgainst1_20_1() throws Exception {
        runMatrix("1.20.1", 763);
    }

    @Test
    void matrixAgainst1_20_2() throws Exception {
        runMatrix("1.20.2", 764);
    }

    @Test
    void matrixAgainst1_20_4() throws Exception {
        runMatrix("1.20.4", 765);
    }

    @Test
    void matrixAgainst1_20_5() throws Exception {
        runMatrix("1.20.5", 766);
    }

    @Test
    void matrixAgainst1_21() throws Exception {
        runMatrix("1.21", 767);
    }

    @Test
    void matrixAgainst1_21_2() throws Exception {
        runMatrix("1.21.2", 768);
    }

    @Test
    void matrixAgainst1_21_4() throws Exception {
        runMatrix("1.21.4", 769);
    }

    @Test
    void matrixAgainst1_21_5() throws Exception {
        runMatrix("1.21.5", 770);
    }

    @Test
    void matrixAgainst1_21_6() throws Exception {
        runMatrix("1.21.6", 771);
    }

    @Test
    void matrixAgainst1_21_7() throws Exception {
        runMatrix("1.21.7", 772);
    }

    @Test
    void matrixAgainst1_21_8() throws Exception {
        runMatrix("1.21.8", 772);
    }

    @Test
    void matrixAgainst1_21_9() throws Exception {
        runMatrix("1.21.9", 773);
    }

    @Test
    void matrixAgainst1_21_10() throws Exception {
        runMatrix("1.21.10", 773);
    }

    @Test
    void matrixAgainst1_21_11() throws Exception {
        runMatrix("1.21.11", 774);
    }

    @Test
    void matrixAgainst26_1() throws Exception {
        runMatrix("26.1", 775);
    }

    @Test
    void matrixAgainst26_2() throws Exception {
        runMatrix("26.2", 776);
    }

    @Test
    void matrixAgainst1_18_2() throws Exception {
        runMatrix("1.18.2", 758);
    }

    /**
     * 内部サーバー 1 台を起動し、Bedrock 6 プロトコル分の組み合わせを回す。
     */
    private void runMatrix(String serverVersion, int serverProtocol) throws Exception {
        Path realJar = findRealVanillaJar(serverVersion);
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-" + serverVersion + ".jar）が無いため"
                        + "スキップします。");
        String java21Home = null;
        boolean java21PropertySetHere = false;
        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768
                || serverProtocol == 769 || serverProtocol == 770
                || serverProtocol == 771 || serverProtocol == 772
                || serverProtocol == 773 || serverProtocol == 774) {
            java21Home = findJava21Home();
            assumeTrue(java21Home != null,
                    "Java 21 が見つからないため " + serverVersion + " 行をスキップします。");
            if (System.getProperty("detergentmc.java21.home") == null
                    || System.getProperty("detergentmc.java21.home").isBlank()) {
                System.setProperty("detergentmc.java21.home", java21Home);
                java21PropertySetHere = true;
            }
        }
        boolean java25PropertySetHere = false;
        if (serverProtocol == 775 || serverProtocol == 776) {
            String java25Home = Java25TestSupport.findJava25Home();
            assumeTrue(java25Home != null,
                    "Java 25 が見つからないため " + serverVersion + " 行をスキップします。");
            if (System.getProperty("detergentmc.java25.home") == null
                    || System.getProperty("detergentmc.java25.home").isBlank()) {
                System.setProperty("detergentmc.java25.home", java25Home);
                java25PropertySetHere = true;
            }
        }
        try {
            Path vanillaDir = tempDir.resolve("vanilla-" + serverVersion);
            Files.createDirectories(vanillaDir);
            Files.copy(realJar, vanillaDir.resolve("server-" + serverVersion + ".jar"));

            int vanillaPort = freePort();
            try (FakeMojang mojang = new FakeMojang(
                    vanillaDir.resolve("server-" + serverVersion + ".jar"), serverVersion)) {
                VanillaServerHost host = new VanillaServerHost(vanillaDir, serverVersion,
                        vanillaPort, false, 10, "DetergentMC test", null, 512,
                        new VanillaDownloader(mojang.manifestUrl()));
                host.addListener(event -> {
                    if (event instanceof VanillaLogEvent.ChatMessage chat
                            && chat.name().equals(".Steve")
                            && chat.message().equals(expectedChat.get())) {
                        CountDownLatch latch = chatLatch.get();
                        if (latch != null) {
                            latch.countDown();
                        }
                    }
                });
                host.start();
                try {
                    // 1.18.2 の起動はこの環境で約460〜520秒かかるため、758 だけ長く待つ。
                    int readyTimeoutMs = serverProtocol == 758 ? 600_000 : 180_000;
                    assertTrue(host.waitForReady(readyTimeoutMs),
                            "バニラ " + serverVersion + " が" + (readyTimeoutMs / 1000) + "秒以内に起動しませんでした。");
                    for (int bedrockProtocol : BEDROCK_PROTOCOLS) {
                        ComboResult result = runCombo(host, vanillaPort, serverVersion,
                                serverProtocol, bedrockProtocol);
                        assertTrue(result.groundBlockId() != 11881,
                                "スポーン地点の地面は空気でないこと（Bedrock " + bedrockProtocol
                                        + " × サーバー " + serverProtocol + "）");
                        assertTrue(result.groundY() >= -64,
                                "スポーン地点に地面があること（Bedrock " + bedrockProtocol
                                        + " × サーバー " + serverProtocol + "）");
                        // 直前の接続が閉じてから次へ（同名プレイヤーの再参加のため間を空ける）。
                        Thread.sleep(1000);
                    }
                } finally {
                    host.stop();
                }
            }
        } finally {
            if (java21PropertySetHere) {
                System.clearProperty("detergentmc.java21.home");
            }
            if (java25PropertySetHere) {
                System.clearProperty("detergentmc.java25.home");
            }
        }
    }

    /** 1 つの組み合わせを回す：参加・スポーン・チャット・移動・チャンク・アイテム。 */
    private ComboResult runCombo(VanillaServerHost host, int vanillaPort,
            String serverVersion, int serverProtocol, int bedrockProtocol)
            throws Exception {
        BedrockVersion version = BedrockVersion.fromProtocol(bedrockProtocol);
        assertNotNull(version, "Bedrock プロトコル " + bedrockProtocol + " が対応表にあること");
        String combo = "Bedrock " + bedrockProtocol + " × サーバー " + serverProtocol
                + "（" + serverVersion + "）";

        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch spawnChunkSeen = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        Map<String, byte[]> chunks = new ConcurrentHashMap<>();
        List<double[]> spawns = new CopyOnWriteArrayList<>();
        List<int[]> dimensions = new CopyOnWriteArrayList<>();
        List<List<String>> biomeLists = new CopyOnWriteArrayList<>();

        JavaBackendConnection.Listener listener = new JavaBackendConnection.Listener() {
            @Override
            public void onJoined(int javaEntityId) {
                joined.countDown();
            }

            @Override
            public void onSpawnPosition(double x, double y, double z) {
                spawns.add(new double[]{x, y, z});
                // スポーン地点のチャンクが先に届いている場合はここで数える
                // （758 など、チャンクがスポーン位置より先に届く順序でも落ちないように）。
                int spawnChunkX = (int) Math.floor(x / 16.0);
                int spawnChunkZ = (int) Math.floor(z / 16.0);
                if (chunks.containsKey(spawnChunkX + "," + spawnChunkZ)) {
                    spawnChunkSeen.countDown();
                }
            }

            @Override
            public void onChunk(byte[] javaChunkPayload) {
                PacketIO.Reader reader = new PacketIO.Reader(javaChunkPayload);
                int chunkX = reader.readInt();
                int chunkZ = reader.readInt();
                chunks.put(chunkX + "," + chunkZ, javaChunkPayload.clone());
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
            public void onDimensionInfo(int minY, int height, List<String> biomeNames) {
                dimensions.add(new int[]{minY, height});
                biomeLists.add(biomeNames);
            }

            @Override
            public void onChat(String sender, String message) {
            }

            @Override
            public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            }

            @Override
            public void onDisconnected(String reason) {
                disconnected.countDown();
            }
        };
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", vanillaPort, serverProtocol, ".Steve", uuid, listener);
        backend.connect();
        try {
            assertTrue(joined.await(30, TimeUnit.SECONDS),
                    combo + "：Java バックエンドがバニラに参加しませんでした。");
            assertTrue(spawnChunkSeen.await(90, TimeUnit.SECONDS),
                    combo + "：スポーン地点のチャンクが届きませんでした。届いたチャンク: "
                            + chunks.keySet());
            assertTrue(dimensions.stream().anyMatch(d -> d[0] == -64 && d[1] == 384),
                    combo + "：オーバーワールドの次元情報が届くこと: " + dimensions);

            // チャット：バニラのログに自分の発言が出ること。
            String chatText = "matrix " + bedrockProtocol + " to " + serverProtocol;
            CountDownLatch chatSeen = new CountDownLatch(1);
            expectedChat.set(chatText);
            chatLatch.set(chatSeen);
            backend.sendChat(chatText);
            assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                    combo + "：チャットがバニラに届きませんでした。");

            // 移動：少し動いても切断されないこと。
            double[] spawn = spawns.get(0);
            backend.sendPosition(spawn[0] + 1.0, spawn[1], spawn[2], 0f, 0f, true);
            backend.sendPosition(spawn[0] + 2.0, spawn[1], spawn[2], 90f, 0f, true);
            Thread.sleep(2000);
            assertFalse(backend.isClosed(), combo + "：移動後に切断されました。");
            assertFalse(disconnected.await(0, TimeUnit.SECONDS),
                    combo + "：移動で切断されました。");

            // チャンク：本物の Java チャンクを Bedrock LevelChunk に翻訳し、
            // スポーン地点の地面ブロックが読めること。
            int spawnChunkX = (int) Math.floor(spawn[0] / 16.0);
            int spawnChunkZ = (int) Math.floor(spawn[2] / 16.0);
            byte[] javaChunk = chunks.get(spawnChunkX + "," + spawnChunkZ);
            assertNotNull(javaChunk, combo + "：スポーン地点のチャンクが取得できませんでした。");
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
                                    spawnChunkX, spawnChunkZ, javaChunk, serverProtocol,
                                    minY, height, biomeNames));
            assertEquals(24, translated.subChunkCount(),
                    combo + "：オーバーワールドは 24 サブチャンク");
            LevelChunkPayloadDecoder decoded = LevelChunkPayloadDecoder.decode(
                    translated.payload(), translated.subChunkCount());
            assertTrue(decoded.runtimeIds.stream().anyMatch(id -> id != 11881),
                    combo + "：チャンクに地形ブロックが含まれていません。");
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

            // アイテム：その Bedrock バージョンの ID・metadata と
            // そのバージョンの Item 形式（従来／ItemV4）で読み直す。
            assertItemRoundTrip(bedrockProtocol, 1, 1, 0, combo); // 石
            assertItemRoundTrip(bedrockProtocol, 2,
                    bedrockProtocol <= 594 ? 1 : -590,
                    bedrockProtocol <= 594 ? 1 : 0, combo); // 花崗岩
            assertItemRoundTrip(bedrockProtocol, 49,
                    bedrockProtocol <= 630 ? 6 : -825,
                    bedrockProtocol <= 630 ? 1 : 0, combo); // トウヒの苗木
            int swordId = switch (bedrockProtocol) {
                case 589, 594 -> 318;
                case 630 -> 319;
                case 671 -> 321;
                case 685 -> 322;
                case 2193 -> 347;
                default -> throw new IllegalStateException("想定外のプロトコル");
            };
            assertItemRoundTrip(bedrockProtocol, 834, swordId, 0, combo); // ダイヤの剣
            return new ComboResult(bedrockProtocol, serverProtocol, groundId, groundY);
        } finally {
            backend.close();
            expectedChat.set(null);
            chatLatch.set(null);
        }
    }

    /** Java スロット → その Bedrock バージョンのアイテム → MobEquipment → 読み直し。 */
    private static void assertItemRoundTrip(int bedrockProtocol, int javaItem765,
            int expectedNetworkId, int expectedMetadata, String combo) {
        BedrockVersion version = BedrockVersion.fromProtocol(bedrockProtocol);
        BedrockEntityTranslator.JavaSlot slot = new BedrockEntityTranslator.JavaSlot(
                true, javaItem765, 1, false, true);
        BedrockEntityTranslator.BedrockItem item =
                BedrockEntityTranslator.toBedrockItem(slot, bedrockProtocol);
        assertEquals(expectedNetworkId, item.networkId(),
                combo + "：Java " + javaItem765 + " の network_id");
        assertEquals(expectedMetadata, item.metadata(),
                combo + "：Java " + javaItem765 + " の metadata");
        assertEquals(1, item.count(), combo + "：個数");
        int expectedBlockRuntimeId = item.blockRuntimeId();
        byte[] payload = BedrockPackets.encodeMobEquipment(42, item, 0, 0, version);
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(payload);
        assertEquals(42, BedrockVarInt.readUnsignedVarInt(in), combo + "：runtime id");
        boolean itemV4 = version.mobEquipmentUsesItemV4();
        if (itemV4) {
            assertEquals(expectedNetworkId, in.readLittleShort(),
                    combo + "：ItemV4 の network_id");
            assertEquals(1, in.readLittleUnsignedShort(), combo + "：ItemV4 の count");
            assertEquals(expectedMetadata, BedrockVarInt.readUnsignedVarInt(in),
                    combo + "：ItemV4 の metadata");
            assertFalse(in.readBoolean(), combo + "：ItemV4 の has_stack_id");
            assertEquals(expectedBlockRuntimeId, BedrockVarInt.readUnsignedVarInt(in),
                    combo + "：ItemV4 の block_runtime_id");
            long extraLength = BedrockVarInt.readUnsignedVarInt(in);
            in.readBytes((int) extraLength);
        } else {
            assertEquals(expectedNetworkId, BedrockVarInt.readZigzag32(in),
                    combo + "：従来 Item の network_id");
            assertEquals(1, in.readLittleUnsignedShort(), combo + "：従来 Item の count");
            assertEquals(expectedMetadata, BedrockVarInt.readUnsignedVarInt(in),
                    combo + "：従来 Item の metadata");
            assertEquals(0, in.readUnsignedByte(), combo + "：has_stack_id は 0");
            assertEquals(expectedBlockRuntimeId, BedrockVarInt.readZigzag32(in),
                    combo + "：従来 Item の block_runtime_id");
            long extraLength = BedrockVarInt.readUnsignedVarInt(in);
            in.readBytes((int) extraLength);
        }
        assertEquals(3, in.remaining(),
                combo + "：slot・selected slot・window id が残ること");
    }
}
