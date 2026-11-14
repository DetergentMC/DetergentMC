package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.bedrock.BedrockEntityTranslator;
import io.detergentmc.bedrock.JavaBackendConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bedrock エンティティ統合テスト：2 人の偽 Java クライアントを本物の
 * バニラ 1.20.4 に参加させ、片方（A）の JavaBackendConnection に
 * もう片方（B）の Player Info Add・Spawn Entity（プレイヤー）・
 * Entity Metadata・移動・Update Attributes・Remove が届くことを確認する。
 * UDP は使わない（Bedrock 側のパケット化は BedrockSessionEntityTest で確認済み）。
 *
 * <p>実物のバニラ 1.20.4 の Update Attributes についての確認済みの挙動
 * （2026-11-10・生バイトで確認）：参加直後に届くパケットは count=1 で
 * {@code minecraft:generic.movement_speed} だけを含む（プレイヤー自身も
 * 移動速度 0.1 のみ）。最大体力は参加時のこのパケットには含まれない
 * （体力は Entity Metadata の体力で届く）。そのためここでは
 * 移動速度 0.1（バニラの既定値。float の 0.1 を double に広げた
 * 0.10000000149011612）でパケット形の正しさを確認する。最大体力の
 * 抜き出し自体は偽 Java サーバー相手の
 * {@code JavaBackendConnectionEntityTest}（max_health 30.0）で確認する。
 *
 * <p>モブ（/summon）は偽クライアントが op ではないため使えない。モブの
 * Spawn Entity は偽 Java サーバー相手の
 * {@code JavaBackendConnectionEntityTest} で実物と同じ形のパケットで確認し、
 * ここでは報告に明記する。
 *
 * <p>テストは offline-mode のバニラで動かすため、ログイン時に送った UUID
 * は使われず、名前から決まる OfflinePlayer UUID
 * （{@code "OfflinePlayer:" + 名前} の UUID v3）になる。UUID の比較は
 * その値で行う（2026-11-10 に Player Info Add が届かない失敗から判明）。
 */
@Timeout(600)
class BedrockEntityIntegrationTest {

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

    /** 偽Mojang：既存 jar の SHA-1 だけを返す。 */
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

    /** A 側で B の出来事をためる Listener。 */
    private static final class PlayerEvents implements JavaBackendConnection.Listener {
        final CountDownLatch joined = new CountDownLatch(1);
        final int[] selfId = new int[1];
        final List<Object[]> playerInfoAdds = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.SpawnEntity> spawns = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.JavaMetadata> metadatas = new CopyOnWriteArrayList<>();
        final List<BedrockEntityTranslator.UpdateAttributes> attributes =
                new CopyOnWriteArrayList<>();
        final List<Integer> movedEntityIds = new CopyOnWriteArrayList<>();
        final List<List<Integer>> removes = new CopyOnWriteArrayList<>();
        final List<List<UUID>> playerInfoRemoves = new CopyOnWriteArrayList<>();

        @Override
        public void onJoined(int javaEntityId) {
            selfId[0] = javaEntityId;
            joined.countDown();
        }

        @Override
        public void onSpawnPosition(double x, double y, double z) {
        }

        @Override
        public void onChat(String sender, String message) {
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
        }

        @Override
        public void onDisconnected(String reason) {
        }

        @Override
        public void onPlayerInfoAdd(UUID uuid, String name, int gamemode) {
            playerInfoAdds.add(new Object[]{uuid, name, gamemode});
        }

        @Override
        public void onSpawnEntity(BedrockEntityTranslator.SpawnEntity spawn) {
            spawns.add(spawn);
        }

        @Override
        public void onEntityMetadata(BedrockEntityTranslator.JavaMetadata metadata) {
            metadatas.add(metadata);
        }

        @Override
        public void onUpdateAttributes(BedrockEntityTranslator.UpdateAttributes attrs) {
            attributes.add(attrs);
        }

        @Override
        public void onEntityMove(BedrockEntityTranslator.EntityMove move) {
            movedEntityIds.add(move.entityId());
        }

        @Override
        public void onEntityMoveLook(BedrockEntityTranslator.EntityMoveLook move) {
            movedEntityIds.add(move.entityId());
        }

        @Override
        public void onEntityLook(BedrockEntityTranslator.EntityLook look) {
            movedEntityIds.add(look.entityId());
        }

        @Override
        public void onEntityTeleport(BedrockEntityTranslator.EntityTeleport teleport) {
            movedEntityIds.add(teleport.entityId());
        }

        @Override
        public void onRemoveEntities(List<Integer> entityIds) {
            removes.add(entityIds);
        }

        @Override
        public void onPlayerInfoRemove(List<UUID> uuids) {
            playerInfoRemoves.add(uuids);
        }
    }

    private static boolean waitFor(long timeoutSeconds, java.util.function.BooleanSupplier cond)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return cond.getAsBoolean();
    }

    /**
     * オフライン・モードのバニラが使う UUID。ログイン時に送った UUID は
     * 無視され、{@code "OfflinePlayer:" + 名前} の UUID v3（MD5）が使われる
     * （{@code UUID.nameUUIDFromBytes} と同じ）。Player Info Add・
     * Spawn Entity・Player Info Remove の比較はこの UUID で行う。
     */
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** 失敗メッセージ用に Player Info Add の一覧を UUID・名前付きで表す。 */
    private static String formatPlayerInfoAdds(List<Object[]> adds) {
        StringBuilder sb = new StringBuilder("[");
        for (Object[] e : adds) {
            sb.append("(").append(e[0]).append(", ").append(e[1])
                    .append(", gamemode=").append(e[2]).append("), ");
        }
        return sb.append("]").toString();
    }

    @Test
    void realVanillaSecondPlayerIsVisibleToFirst() throws Exception {
        Path realJar = findRealVanillaJar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.20.4.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.20.4.jar"));

        int vanillaPort = freePort();
        UUID uuidA = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID uuidB = UUID.fromString("33333333-4444-5555-6666-777777777777");
        PlayerEvents eventsA = new PlayerEvents();
        PlayerEvents eventsB = new PlayerEvents();

        try (FakeMojang mojang = new FakeMojang(vanillaDir.resolve("server-1.20.4.jar"))) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.20.4", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");

                JavaBackendConnection clientA = new JavaBackendConnection(
                        "127.0.0.1", vanillaPort, 765, ".Steve", uuidA, eventsA);
                clientA.connect();
                assertTrue(eventsA.joined.await(30, TimeUnit.SECONDS),
                        "クライアント A がバニラに参加しませんでした。");
                int selfIdA = eventsA.selfId[0];
                assertTrue(selfIdA > 0, "A のエンティティ ID が決まること");

                // 自分の Update Attributes（移動速度 0.1）が届くこと。
                // 実物のバニラのパケット形の確認にもなる。生バイトで確認済み：
                // 参加直後のパケットは count=1・movement_speed のみで、
                // 最大体力は含まれない（上の javadoc を参照）。
                assertTrue(waitFor(30, () -> eventsA.attributes.stream()
                                .anyMatch(a -> a.entityId() == selfIdA
                                        && a.movementSpeed() != null
                                        && Math.abs(a.movementSpeed() - 0.10000000149011612) < 0.001)),
                        "A 自身の Update Attributes（移動速度 0.1）が届きませんでした。届いた属性: "
                                + eventsA.attributes);

                JavaBackendConnection clientB = new JavaBackendConnection(
                        "127.0.0.1", vanillaPort, 765, ".Alex", uuidB, eventsB);
                clientB.connect();
                try {
                    assertTrue(eventsB.joined.await(30, TimeUnit.SECONDS),
                            "クライアント B がバニラに参加しませんでした。");
                    int selfIdB = eventsB.selfId[0];
                    assertTrue(selfIdB > 0 && selfIdB != selfIdA,
                            "B のエンティティ ID は A と違うこと");

                    // A に B の Player Info Add（名前・生存）が届くこと。
                    // オフライン・モードでは B の UUID は OfflinePlayer UUID になる。
                    UUID offlineUuidB = offlineUuid(".Alex");
                    assertTrue(waitFor(60, () -> eventsA.playerInfoAdds.stream()
                                    .anyMatch(e -> offlineUuidB.equals(e[0]) && ".Alex".equals(e[1]))),
                            "A に B の Player Info Add が届きませんでした。届いた一覧: "
                                    + formatPlayerInfoAdds(eventsA.playerInfoAdds));

                    // A に B の Spawn Entity（プレイヤー 124）が届くこと。
                    assertTrue(waitFor(60, () -> eventsA.spawns.stream()
                                    .anyMatch(s -> s.entityId() == selfIdB
                                            && s.type765() == 124
                                            && offlineUuidB.equals(s.uuid()))),
                            "A に B の Spawn Entity（プレイヤー）が届きませんでした。届いた出現: "
                                    + eventsA.spawns);

                    // A に B の Entity Metadata（体力 20）が届くこと。
                    assertTrue(waitFor(60, () -> eventsA.metadatas.stream()
                                    .anyMatch(m -> m.entityId() == selfIdB
                                            && m.health() != null
                                            && Math.abs(m.health() - 20.0f) < 0.001f)),
                            "A に B の Entity Metadata（体力 20）が届きませんでした。");

                    // B が動くと A に移動パケットが届くこと。
                    // B のスポーン位置は A が見た B の出現位置を使う。
                    BedrockEntityTranslator.SpawnEntity spawnOfB = eventsA.spawns.stream()
                            .filter(s -> s.entityId() == selfIdB).findFirst().orElseThrow();
                    double baseX = spawnOfB.x();
                    double baseY = spawnOfB.y();
                    double baseZ = spawnOfB.z();
                    for (int i = 1; i <= 6; i++) {
                        clientB.sendPosition(baseX + i * 0.5, baseY, baseZ, 0, 0, true);
                        Thread.sleep(500);
                    }
                    assertTrue(waitFor(30, () -> eventsA.movedEntityIds.contains(selfIdB)),
                            "B の移動が A に届きませんでした。届いた移動: " + eventsA.movedEntityIds);

                    // B が切断すると A に Remove Entities と Player Info Remove が届くこと。
                    clientB.close();
                    assertTrue(waitFor(30, () -> eventsA.removes.stream()
                                    .anyMatch(list -> list.contains(selfIdB))),
                            "A に B の Remove Entities が届きませんでした。届いた削除: "
                                    + eventsA.removes);
                    assertTrue(waitFor(30, () -> eventsA.playerInfoRemoves.stream()
                                    .anyMatch(list -> list.contains(offlineUuid(".Alex")))),
                            "A に B の Player Info Remove が届きませんでした。");
                } finally {
                    clientB.close();
                }
            } finally {
                host.stop();
            }
        }
    }
}
