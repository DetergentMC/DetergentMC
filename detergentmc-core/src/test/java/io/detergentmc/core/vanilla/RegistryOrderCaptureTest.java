package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.detergentmc.protocol.ClientProxy;
import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PacketIO.Writer;
import io.detergentmc.protocol.PlayTranslator;
import io.detergentmc.protocol.ProxyAuthConfig;
import io.detergentmc.protocol.VarInt;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本物のバニラが設定フェーズで送る Registry Data のエントリ順を
 * そのまま記録し、レジストリ対応表の前提（アルファベット順の仮説）が
 * 正しいことを検証する。
 *
 * <p>1.21（767）のエンチャント・絵画・旗の模様・トリム・ダメージ種別・
 * ジュークボックスの歌は、データ駆動レジストリのためリソース位置の
 * アルファベット順に並ぶと仮定して対応表を作っている。この仮説が
 * 違えば対応表の番号がずれ、変換が壊れる。1.20.5（766）の絵画は
 * コード登録のため古典的な順番だと仮定している。どちらもこのテストで
 * 本物のサーバーから確かめる。
 *
 * <p>クライアントはネイティブ（767＋767、766＋766）で参加するため、
 * プロキシは Registry Data をそのまま中継する。偽クライアントは
 * Select Known Packs に空の一覧で答える（答えないと本物のサーバーが
 * 先に進まない）。
 */
@Timeout(600)
class RegistryOrderCaptureTest {

    @TempDir
    Path tempDir;

    /** バニラ1.21は Java 21 必須のため、起動前に Java 21 の場所を設定しておく。 */
    @BeforeEach
    void ensureJava21() {
        Java21TestSupport.ensureJava21Home();
        Java25TestSupport.ensureJava25Home();
    }

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

    /** Registry Data を記録するだけの偽クライアント（766／767／768／769共通の番号）。
     * 設定フェーズの番号は 767 と 768／769 で同じ。プレイの Keep Alive（768／769 は 0x27、
     * 766／767 は 0x26）と Ping（768／769 は 0x37、766／767 は 0x35）だけが違う。 */
    private static final class CapturingClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        private volatile int state = 0;
        private final int clientProtocol;
        /** 受け取った Registry Data（0x07）のペイロード（到着順）。 */
        final List<byte[]> registryPayloads = new ArrayList<>();

        CapturingClient(int port, int clientProtocol) throws IOException {
            this.clientProtocol = clientProtocol;
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "capture-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        private void readLoop() {
            try {
                while (true) {
                    MinecraftPacket packet = readPacket();
                    if (state == 1) {
                        if (packet.id() == 0x07) {
                            synchronized (registryPayloads) {
                                registryPayloads.add(packet.payload().clone());
                            }
                        }
                        if (packet.id() == 0x04) {
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x04, response.bytes()));
                        } else if (packet.id() == 0x05) {
                            Writer response = new Writer();
                            response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                            sendPacket(new MinecraftPacket(0x05, response.bytes()));
                        } else if (packet.id() == 0x03) {
                            sendPacket(new MinecraftPacket(0x03, new byte[0]));
                            state = 2;
                        } else if (packet.id() == 0x09) {
                            java.util.UUID uuid =
                                    new PacketIO.Reader(packet.payload()).readUuid();
                            Writer response = new Writer();
                            response.writeUuid(uuid);
                            response.writeVarInt(3);
                            sendPacket(new MinecraftPacket(0x06, response.bytes()));
                        } else if (packet.id() == 0x0E) { // Select Known Packs
                            Writer response = new Writer();
                            response.writeVarInt(0);
                            sendPacket(new MinecraftPacket(0x07, response.bytes()));
                        }
                    } else if (state == 2 && packet.id() == keepAliveId()) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(keepAliveResponseId(), response.bytes()));
                    } else if (state == 2 && packet.id() == pingId()) {
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(pingResponseId(), response.bytes()));
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

        /** プレイの Keep Alive（クライアント行き）。774 は 0x2B、768／769 は 0x27、766／767 は 0x26、760 は 0x20。 */
        private int keepAliveId() {
            return clientProtocol == 774 ? 0x2B
                    : clientProtocol == 768 || clientProtocol == 769 ? 0x27
                    : clientProtocol == 760 ? 0x20 : 0x26;
        }

        /** プレイの Keep Alive への応答（サーバー行き）。774 は 0x1B、768／769／770 は 0x1A、766／767 は 0x18、760 は 0x12。 */
        private int keepAliveResponseId() {
            return clientProtocol == 774 ? 0x1B
                    : clientProtocol == 768 || clientProtocol == 769 || clientProtocol == 770
                    ? 0x1A
                    : clientProtocol == 760 ? 0x12 : 0x18;
        }

        /** プレイの Ping（クライアント行き）。774 は 0x3B、768／769 は 0x37、770 は 0x36、766／767 は 0x35、760 は 0x2F。 */
        private int pingId() {
            return clientProtocol == 774 ? 0x3B
                    : clientProtocol == 768 || clientProtocol == 769 ? 0x37
                    : clientProtocol == 770 ? 0x36
                    : clientProtocol == 760 ? 0x2F : 0x35;
        }

        /** プレイの Ping への応答（サーバー行き）。774 は 0x2C、768／769 は 0x29、770 は 0x2B、766／767 は 0x27、760 は 0x20。 */
        private int pingResponseId() {
            return clientProtocol == 774 ? 0x2C
                    : clientProtocol == 768 || clientProtocol == 769 ? 0x29
                    : clientProtocol == 770 ? 0x2B
                    : clientProtocol == 760 ? 0x20 : 0x27;
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
        int shift = 0;
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("ストリームが途切れました。");
            }
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt が長すぎます。");
            }
        }
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] bytes = in.readNBytes(length);
        if (bytes.length < length) {
            throw new IOException("ストリームが途切れました。");
        }
        return bytes;
    }

    private static int freePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
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
        writer.writeUuid(java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return writer.bytes();
    }

    private static byte[] clientInformation() {
        return clientInformation(false);
    }

    private static byte[] clientInformation(boolean particleStatus) {
        // Client Information は 765／766／767 は 8 フィールド
        // （particleStatus は無い）。9 フィールド送ると 767 のバニラが
        // 「1 bytes extra」で切断する（2026-11-10に確認）。
        // 768／769 は逆に 9 フィールド必須で、8 フィールドのまま送ると
        // バニラが読み取り途中で切断する（proto.yml 1.21.3 の
        // packet_common_settings に particleStatus がある）。
        Writer writer = new Writer();
        writer.writeString("en_US");
        writer.writeByte(8);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeByte(0x7F);
        writer.writeVarInt(1);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        if (particleStatus) {
            writer.writeVarInt(0); // particleStatus: all
        }
        return writer.bytes();
    }

    /** 記録された Registry Data をレジストリ名→ID順の名前一覧にまとめる。 */
    private static Map<String, List<String>> orderedRegistries(List<byte[]> payloads) {
        Map<String, List<String>> result = new HashMap<>();
        for (byte[] payload : payloads) {
            Map<String, Nbt.Tag.CompoundTag> parsed = PlayTranslator.parseRegistryData(payload);
            for (Map.Entry<String, Nbt.Tag.CompoundTag> entry : parsed.entrySet()) {
                List<String[]> rows = new ArrayList<>();
                entry.getValue().getList("value").ifPresent(list -> {
                    for (Nbt.Tag tag : list.value()) {
                        if (tag instanceof Nbt.Tag.CompoundTag compound) {
                            String name = compound.getString("name").orElse(null);
                            int id = compound.get("id")
                                    .filter(t -> t instanceof Nbt.Tag.IntTag)
                                    .map(t -> ((Nbt.Tag.IntTag) t).value())
                                    .orElse(-1);
                            if (name != null && id >= 0) {
                                rows.add(new String[]{String.valueOf(id), name});
                            }
                        }
                    }
                });
                rows.sort(Comparator.comparingInt(r -> Integer.parseInt(r[0])));
                List<String> names = new ArrayList<>();
                for (String[] row : rows) {
                    names.add(row[1]);
                }
                result.put(entry.getKey(), names);
            }
        }
        return result;
    }

    /** 本物のバニラにプロキシ経由で参加し、設定フェーズの Registry Data を集める。 */
    private Map<String, List<String>> capture(String version, String jarName,
            int clientProtocol, int serverProtocol) throws Exception {
        Path realJar = switch (version) {
            case "1.21" -> findRealVanilla121Jar();
            case "1.21.2" -> findRealVanilla1212Jar();
            case "1.21.3" -> findRealVanilla1213Jar();
            case "1.21.4" -> findRealVanilla1214Jar();
            case "1.21.5" -> findRealVanilla1215Jar();
            case "1.21.6" -> findRealVanilla1216Jar();
            case "1.21.7" -> findRealVanilla1217Jar();
            case "1.21.8" -> findRealVanilla1218Jar();
            case "1.21.11" -> findRealVanilla12111Jar();
            case "26.1" -> findRealVanilla261Jar();
            default -> findRealVanilla1205Jar();
        };
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-" + version + ".jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve(jarName));

        int vanillaPort = freePort();
        int proxyPort = freePort();

        try (FakeMojangForCapture mojang = new FakeMojangForCapture(
                vanillaDir.resolve(jarName), version)) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, version, vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, serverProtocol);
                proxy.start();
                try (CapturingClient client = new CapturingClient(proxyPort, clientProtocol)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(clientProtocol, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart("Capture")));
                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");
                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0]));
                    client.sendPacket(new MinecraftPacket(0x00,
                            clientInformation(clientProtocol == 768 || clientProtocol == 769
                                    || clientProtocol == 770 || clientProtocol == 771
                                    || clientProtocol == 772 || clientProtocol == 774
                                    || clientProtocol == 775)));
                    int joinGameId = clientProtocol == 775 ? 0x31
                            : clientProtocol == 774 ? 0x30
                            : clientProtocol == 768 || clientProtocol == 769 ? 0x2C : 0x2B;
                    MinecraftPacket joinGame = client.waitForId(joinGameId, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x"
                            + Integer.toHexString(joinGameId) + "）が届きませんでした。");
                    assertFalse(client.isClosed(), "参加後に接続が切れていないこと");
                    synchronized (client.registryPayloads) {
                        return orderedRegistries(List.copyOf(client.registryPayloads));
                    }
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /** 偽Mojang：マニフェストとバージョンJSONだけを返し、既存jarのSHA-1を名乗る。 */
    private static final class FakeMojangForCapture implements AutoCloseable {
        final com.sun.net.httpserver.HttpServer server;
        final Path jarPath;
        final String version;

        FakeMojangForCapture(Path jarPath, String version) throws Exception {
            this.jarPath = jarPath;
            this.version = version;
            String jarSha1 = io.detergentmc.core.vanilla.VanillaDownloader.sha1(jarPath);
            server = com.sun.net.httpserver.HttpServer.create(
                    new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;
            server.createContext("/manifest.json", exchange -> {
                byte[] body = ("{\"versions\":[{\"id\": \"" + version + "\", \"type\": \"release\", "
                        + "\"url\": \"" + base + "/version.json\"}]}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                send(exchange, body);
            });
            server.createContext("/version.json", exchange -> {
                byte[] body = ("{\"downloads\": {\"server\": {\"sha1\": \"" + jarSha1
                        + "\", \"size\": " + Files.size(jarPath)
                        + ", \"url\": \"" + base + "/server.jar\"}}}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
    void registryOrderOfRealVanilla121() throws Exception {
        Map<String, List<String>> registries = capture("1.21", "server-1.21.jar", 767, 767);

        List<String> enchantments = registries.get("minecraft:enchantment");
        assertNotNullList(enchantments, "minecraft:enchantment");
        assertEquals(42, enchantments.size(), "1.21 のエンチャントは 42 件: " + enchantments);
        assertEquals("minecraft:aqua_affinity", enchantments.get(0));
        assertEquals("minecraft:breach", enchantments.get(4));
        assertEquals("minecraft:density", enchantments.get(6));
        assertEquals("minecraft:mending", enchantments.get(22));
        assertEquals("minecraft:protection", enchantments.get(27));
        assertEquals("minecraft:sharpness", enchantments.get(32));
        assertEquals("minecraft:sweeping_edge", enchantments.get(36));
        assertEquals("minecraft:wind_burst", enchantments.get(41));

        List<String> paintings = registries.get("minecraft:painting_variant");
        assertNotNullList(paintings, "minecraft:painting_variant");
        assertEquals(50, paintings.size(), "1.21 の絵画は 50 件: " + paintings);
        assertEquals("minecraft:kebab", paintings.get(23));
        assertEquals("minecraft:pointer", paintings.get(32));
        assertEquals("minecraft:pond", paintings.get(33));
        assertEquals("minecraft:unpacked", paintings.get(43));
        assertEquals("minecraft:wither", paintings.get(49));

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(43, banners.size(), "1.21 の旗の模様は 43 件: " + banners);
        assertEquals("minecraft:flow", banners.get(11));
        assertEquals("minecraft:flower", banners.get(12));
        assertEquals("minecraft:guster", banners.get(16));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(18, trims.size(), "1.21 のトリムは 18 件: " + trims);
        assertEquals("minecraft:bolt", trims.get(0));
        assertEquals("minecraft:flow", trims.get(4));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(47, damages.size(), "1.21 のダメージ種別は 47 件: " + damages);
        assertEquals("minecraft:campfire", damages.get(3));
        assertEquals("minecraft:wind_charge", damages.get(44));

        List<String> songs = registries.get("minecraft:jukebox_song");
        assertNotNullList(songs, "minecraft:jukebox_song");
        assertEquals(19, songs.size(), "1.21 のジュークボックスの歌は 19 件: " + songs);
        assertTrue(songs.contains("minecraft:precipice"));
        assertTrue(songs.contains("minecraft:creator"));
    }

    @Test
    void registryOrderOfRealVanilla1212() throws Exception {
        assertRegistryOrder1212Like(capture("1.21.2", "server-1.21.2.jar", 768, 768), "1.21.2");
    }

    @Test
    void registryOrderOfRealVanilla1213() throws Exception {
        // 1.21.3 もプロトコル768のため、1.21.2 とレジストリの順番が
        // 同じであることを確かめる（768 対応の前提）。
        assertRegistryOrder1212Like(capture("1.21.3", "server-1.21.3.jar", 768, 768), "1.21.3");
    }

    /**
     * 本物の 1.21.2／1.21.3 の Registry Data の順番を検証する。
     * エンチャント・絵画・旗の模様・トリム・ジュークボックスの歌は
     * 767 と同じ顔ぶれ・同じ順番。ダメージ種別は ender_pearl（8）と
     * mace_smash（26）が増え、mace_smash は magic（27）の前に来る
     * （アルファベット順。jar の damage_type JSON の並びでも確認済み）。
     * バイオームは 767 と同じ 64 件・同じ順番。pale_garden は
     * winter_drop データパックの中にあり、既定では無効のため
     * レジストリに現れない。plains は 39 のまま。
     */
    private static void assertRegistryOrder1212Like(Map<String, List<String>> registries,
            String version) {
        List<String> enchantments = registries.get("minecraft:enchantment");
        assertNotNullList(enchantments, "minecraft:enchantment");
        assertEquals(42, enchantments.size(), version + " のエンチャントは 42 件: " + enchantments);
        assertEquals("minecraft:aqua_affinity", enchantments.get(0));
        assertEquals("minecraft:mending", enchantments.get(22));
        assertEquals("minecraft:wind_burst", enchantments.get(41));

        List<String> paintings = registries.get("minecraft:painting_variant");
        assertNotNullList(paintings, "minecraft:painting_variant");
        assertEquals(50, paintings.size(), version + " の絵画は 50 件: " + paintings);
        assertEquals("minecraft:kebab", paintings.get(23));
        assertEquals("minecraft:wither", paintings.get(49));

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(43, banners.size(), version + " の旗の模様は 43 件: " + banners);
        assertEquals("minecraft:flow", banners.get(11));
        assertEquals("minecraft:flower", banners.get(12));
        assertEquals("minecraft:guster", banners.get(16));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(18, trims.size(), version + " のトリムは 18 件: " + trims);
        assertEquals("minecraft:bolt", trims.get(0));
        assertEquals("minecraft:flow", trims.get(4));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(49, damages.size(), version + " のダメージ種別は 49 件: " + damages);
        assertEquals("minecraft:ender_pearl", damages.get(8));
        assertEquals("minecraft:mace_smash", damages.get(26));
        assertEquals("minecraft:magic", damages.get(27));
        assertEquals("minecraft:wither_skull", damages.get(48));

        List<String> songs = registries.get("minecraft:jukebox_song");
        assertNotNullList(songs, "minecraft:jukebox_song");
        assertEquals(19, songs.size(), version + " のジュークボックスの歌は 19 件: " + songs);
        assertTrue(songs.contains("minecraft:precipice"));

        List<String> biomes = registries.get("minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        // 既定のバニラ1.21.2／1.21.3はバイオームを64件送る。pale_garden は
        // winter_drop が無効のため含まれない（2026-11-11に実バニラから確認）。
        assertEquals(64, biomes.size(), version + " のバイオームは 64 件: " + biomes);
        assertFalse(biomes.contains("minecraft:pale_garden"));
        assertEquals("minecraft:plains", biomes.get(39));

        // attribute／block_entity_type／instrument は送られてきた場合だけ
        // 位置を検証する（送らないバージョン・送るバージョンがあるため、
        // 無いこと自体は失敗にしない）。
        // 観測（2026-11-11、実バニラ1.21.2／1.21.3）：送られるのは12レジストリ
        // （banner_pattern／chat_type／damage_type／dimension_type／enchantment／
        // instrument／jukebox_song／painting_variant／trim_material／trim_pattern／
        // wolf_variant／worldgen/biome）。instrument は送られるため下の assert は
        // 実行され、8件・admire=0・yearn=7 が確認済み。attribute と
        // block_entity_type は送られないため、そちらの順番は ViaVersion の
        // mapping-1.21.2.json と jar の data ファイルに依る（ワイヤ未確認）。
        List<String> attributes = registries.get("minecraft:attribute");
        if (attributes != null) {
            assertEquals(32, attributes.size(), version + " の属性は 32 件: " + attributes);
            // 768 は一部の属性名から接頭辞が外れた（generic.burning_time →
            // burning_time 等。protocol.json の Update Attributes キー表と同じ並び）。
            assertEquals("minecraft:burning_time", attributes.get(7));
            assertEquals("minecraft:tempt_range", attributes.get(30));
            assertEquals("minecraft:water_movement_efficiency", attributes.get(31));
        }
        List<String> blockEntities = registries.get("minecraft:block_entity_type");
        if (blockEntities != null) {
            assertEquals(45, blockEntities.size(),
                    version + " のブロックエンティティ種別は 45 件: " + blockEntities);
            assertEquals("minecraft:creaking_heart", blockEntities.get(10));
            assertEquals("minecraft:vault", blockEntities.get(44));
        }
        List<String> instruments = registries.get("minecraft:instrument");
        if (instruments != null) {
            assertEquals(8, instruments.size(), version + " の楽器は 8 件: " + instruments);
            assertEquals("minecraft:admire_goat_horn", instruments.get(0));
            assertEquals("minecraft:yearn_goat_horn", instruments.get(7));
        }
    }

    /**
     * 本物の 1.21.4 の Registry Data の順番を検証する。
     * エンチャント・絵画・旗の模様・トリム・ダメージ種別・ジュークボックスの歌は
     * 768 と同じ顔ぶれ・同じ順番。バイオームは pale_garden が通常の
     * バイオームとして現れる。768 では winter_drop データパックが
     * 無効のため 64 件・plains は 39 だったが、1.21.4 では 65 件になり
     * pale_garden が 39、plains が 40 にずれる（アルファベット順。
     * pale_garden &lt; plains）。これが 768↔769 のバイオーム対応表
     * （39 番以降を 1 つずらす）の前提である。
     */
    private static void assertRegistryOrder1214Like(Map<String, List<String>> registries,
            String version) {
        List<String> enchantments = registries.get("minecraft:enchantment");
        assertNotNullList(enchantments, "minecraft:enchantment");
        assertEquals(42, enchantments.size(), version + " のエンチャントは 42 件: " + enchantments);
        assertEquals("minecraft:aqua_affinity", enchantments.get(0));
        assertEquals("minecraft:mending", enchantments.get(22));
        assertEquals("minecraft:wind_burst", enchantments.get(41));

        List<String> paintings = registries.get("minecraft:painting_variant");
        assertNotNullList(paintings, "minecraft:painting_variant");
        assertEquals(50, paintings.size(), version + " の絵画は 50 件: " + paintings);
        assertEquals("minecraft:kebab", paintings.get(23));
        assertEquals("minecraft:wither", paintings.get(49));

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(43, banners.size(), version + " の旗の模様は 43 件: " + banners);
        assertEquals("minecraft:flow", banners.get(11));
        assertEquals("minecraft:flower", banners.get(12));
        assertEquals("minecraft:guster", banners.get(16));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(18, trims.size(), version + " のトリムは 18 件: " + trims);
        assertEquals("minecraft:bolt", trims.get(0));
        assertEquals("minecraft:flow", trims.get(4));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(49, damages.size(), version + " のダメージ種別は 49 件: " + damages);
        assertEquals("minecraft:ender_pearl", damages.get(8));
        assertEquals("minecraft:mace_smash", damages.get(26));
        assertEquals("minecraft:magic", damages.get(27));
        assertEquals("minecraft:wither_skull", damages.get(48));

        List<String> songs = registries.get("minecraft:jukebox_song");
        assertNotNullList(songs, "minecraft:jukebox_song");
        assertEquals(19, songs.size(), version + " のジュークボックスの歌は 19 件: " + songs);
        assertTrue(songs.contains("minecraft:precipice"));

        List<String> biomes = registries.get("minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        // 既定のバニラ1.21.4はバイオームを65件送る。pale_garden が
        // 通常のバイオームとして 39 に現れ、plains は 40 にずれる。
        assertEquals(65, biomes.size(), version + " のバイオームは 65 件: " + biomes);
        assertEquals("minecraft:pale_garden", biomes.get(39));
        assertEquals("minecraft:plains", biomes.get(40));

        // attribute／block_entity_type／instrument は送られてきた場合だけ
        // 位置を検証する（送らないバージョン・送るバージョンがあるため、
        // 無いこと自体は失敗にしない）。
        List<String> attributes = registries.get("minecraft:attribute");
        if (attributes != null) {
            assertEquals(32, attributes.size(), version + " の属性は 32 件: " + attributes);
            assertEquals("minecraft:burning_time", attributes.get(7));
            assertEquals("minecraft:tempt_range", attributes.get(30));
            assertEquals("minecraft:water_movement_efficiency", attributes.get(31));
        }
        List<String> blockEntities = registries.get("minecraft:block_entity_type");
        if (blockEntities != null) {
            assertEquals(45, blockEntities.size(),
                    version + " のブロックエンティティ種別は 45 件: " + blockEntities);
            assertEquals("minecraft:creaking_heart", blockEntities.get(10));
            assertEquals("minecraft:vault", blockEntities.get(44));
        }
        List<String> instruments = registries.get("minecraft:instrument");
        if (instruments != null) {
            assertEquals(8, instruments.size(), version + " の楽器は 8 件: " + instruments);
            assertEquals("minecraft:admire_goat_horn", instruments.get(0));
            assertEquals("minecraft:yearn_goat_horn", instruments.get(7));
        }
    }

    @Test
    void registryOrderOfRealVanilla1215() throws Exception {
        assertRegistryOrder1215Like(capture("1.21.5", "server-1.21.5.jar", 770, 770), "1.21.5");
    }

    @Test
    void registryOrderOfRealVanilla1216() throws Exception {
        assertRegistryOrder1216Like(capture("1.21.6", "server-1.21.6.jar", 771, 771), "1.21.6");
    }

    @Test
    void registryOrderOfRealVanilla1217() throws Exception {
        assertRegistryOrder1217Like(capture("1.21.7", "server-1.21.7.jar", 772, 772), "1.21.7");
    }

    @Test
    void registryOrderOfRealVanilla1218() throws Exception {
        assertRegistryOrder1217Like(capture("1.21.8", "server-1.21.8.jar", 772, 772), "1.21.8");
    }

    @Test
    void registryOrderOfRealVanilla12111() throws Exception {
        assertRegistryOrder12111Like(
                capture("1.21.11", "server-1.21.11.jar", 774, 774), "1.21.11");
    }

    @Test
    void registryOrderOfRealVanilla261() throws Exception {
        assertRegistryOrder261Like(
                capture("26.1", "server-26.1.jar", 775, 775), "26.1");
    }

    /**
     * 26.1 の並び。774→775 のレジストリ差分（ViaVersion mapping-26.1.json と
     * minecraft-data 26.1 で確認）では、エンチャント・絵画・旗の模様・
     * トリム・ダメージ種別・ジュークボックスの歌・バイオーム・属性・
     * ブロックエンティティ種別・楽器（ヤギの角）・ダイアログのいずれも
     * 774 と同一の顔ぶれ・同じ順番のはずである。変わるのはブロック状態
     * （+202）・ブロック（+2：金のタンポポ）・アイテム（+1：金のタンポポ）・
     * 音（+64：トランペットの音符ブロック等）・データコンポーネント（+6）・
     * パーティクル（+2）・環境属性（+3）・レシピ（−1）・スロット表示（+3）で、
     * これらは Join Game の codec ではなく Registry Data（設定フェーズ）の
     * 並びには現れない。なお minecraft-data の instruments.json にある
     * トランペット 4 件は音符ブロックの楽器で、ここの minecraft:instrument
     * （ヤギの角 8 件）とは別物である。
     *
     * <p>このテストはその「同一のはず」を本物の 26.1 の codec で確かめる
     * ためのもので、もし 26.1 で絵画・ダメージ種別等が変わっていたら
     * ここが失敗して真の並びを教えてくれる。その場合はこの期待値と
     * RegistryRemapper の 775 側の小レジストリ表を直すこと。
     */
    private static void assertRegistryOrder261Like(Map<String, List<String>> registries,
            String version) {
        List<String> enchantments = registries.get("minecraft:enchantment");
        assertNotNullList(enchantments, "minecraft:enchantment");
        assertEquals(43, enchantments.size(), version + " のエンチャントは 43 件: " + enchantments);
        assertEquals("minecraft:aqua_affinity", enchantments.get(0));
        assertEquals("minecraft:luck_of_the_sea", enchantments.get(20));
        assertEquals("minecraft:lunge", enchantments.get(21));
        assertEquals("minecraft:lure", enchantments.get(22));
        assertEquals("minecraft:mending", enchantments.get(23));
        assertEquals("minecraft:wind_burst", enchantments.get(42));

        List<String> paintings = registries.get("minecraft:painting_variant");
        assertNotNullList(paintings, "minecraft:painting_variant");
        assertEquals(51, paintings.size(), version + " の絵画は 51 件: " + paintings);
        assertEquals("minecraft:dennis", paintings.get(14));
        assertEquals("minecraft:kebab", paintings.get(24));
        assertEquals("minecraft:wither", paintings.get(50));

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(43, banners.size(), version + " の旗の模様は 43 件: " + banners);
        assertEquals("minecraft:flow", banners.get(11));
        assertEquals("minecraft:flower", banners.get(12));
        assertEquals("minecraft:guster", banners.get(16));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(18, trims.size(), version + " のトリムは 18 件: " + trims);
        assertEquals("minecraft:bolt", trims.get(0));
        assertEquals("minecraft:flow", trims.get(4));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(50, damages.size(), version + " のダメージ種別は 50 件: " + damages);
        assertEquals("minecraft:ender_pearl", damages.get(8));
        assertEquals("minecraft:mace_smash", damages.get(26));
        assertEquals("minecraft:magic", damages.get(27));
        assertEquals("minecraft:spear", damages.get(37));
        assertEquals("minecraft:spit", damages.get(38));
        assertEquals("minecraft:wither_skull", damages.get(49));

        List<String> songs = registries.get("minecraft:jukebox_song");
        assertNotNullList(songs, "minecraft:jukebox_song");
        assertEquals(21, songs.size(), version + " のジュークボックスの歌は 21 件: " + songs);
        assertEquals("minecraft:lava_chicken", songs.get(9));
        assertEquals("minecraft:mall", songs.get(10));
        assertEquals("minecraft:tears", songs.get(18));
        assertEquals("minecraft:wait", songs.get(19));
        assertEquals("minecraft:ward", songs.get(20));

        List<String> biomes = registries.get("minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        assertEquals(65, biomes.size(), version + " のバイオームは 65 件: " + biomes);
        assertEquals("minecraft:pale_garden", biomes.get(39));
        assertEquals("minecraft:plains", biomes.get(40));

        List<String> attributes = registries.get("minecraft:attribute");
        if (attributes != null) {
            assertEquals(35, attributes.size(), version + " の属性は 35 件: " + attributes);
            assertEquals("minecraft:burning_time", attributes.get(7));
            assertEquals("minecraft:camera_distance", attributes.get(8));
            assertEquals("minecraft:tempt_range", attributes.get(31));
            assertEquals("minecraft:water_movement_efficiency", attributes.get(32));
            assertEquals("minecraft:waypoint_transmit_range", attributes.get(33));
            assertEquals("minecraft:waypoint_receive_range", attributes.get(34));
        }
        List<String> blockEntities = registries.get("minecraft:block_entity_type");
        if (blockEntities != null) {
            assertEquals(49, blockEntities.size(),
                    version + " のブロックエンティティ種別は 49 件: " + blockEntities);
            assertEquals("minecraft:creaking_heart", blockEntities.get(10));
            assertEquals("minecraft:shelf", blockEntities.get(40));
            assertEquals("minecraft:vault", blockEntities.get(45));
            assertEquals("minecraft:test_block", blockEntities.get(46));
            assertEquals("minecraft:test_instance_block", blockEntities.get(47));
            assertEquals("minecraft:copper_golem_statue", blockEntities.get(48));
        }
        List<String> instruments = registries.get("minecraft:instrument");
        if (instruments != null) {
            assertEquals(8, instruments.size(), version + " の楽器は 8 件: " + instruments);
            assertEquals("minecraft:admire_goat_horn", instruments.get(0));
            assertEquals("minecraft:yearn_goat_horn", instruments.get(7));
        }
        List<String> dialogs = registries.get("minecraft:dialog");
        assertNotNullList(dialogs, "minecraft:dialog");
        assertEquals(3, dialogs.size(), version + " のダイアログは 3 件: " + dialogs);
        assertEquals("minecraft:custom_options", dialogs.get(0));
        assertEquals("minecraft:quick_actions", dialogs.get(1));
        assertEquals("minecraft:server_links", dialogs.get(2));
    }

    /**
     * 1.21.11 の並び。1.21.7／1.21.8（1217Like）との違いはエンチャント
     * lunge（21 に挿入で lure は 22、mending は 23、wind_burst は 42 になる）
     * とダメージ種別 spear（37 に挿入で spit は 38、wither_skull は 49 になる）
     * だけ。絵画・ジュークボックス・バイオーム・属性・ブロックエンティティ
     * （shelf=40・copper_golem_statue=48 を含む 49 件）・楽器・ダイアログは
     * 773 と同じ。
     */
    private static void assertRegistryOrder12111Like(Map<String, List<String>> registries,
            String version) {
        List<String> enchantments = registries.get("minecraft:enchantment");
        assertNotNullList(enchantments, "minecraft:enchantment");
        assertEquals(43, enchantments.size(), version + " のエンチャントは 43 件: " + enchantments);
        assertEquals("minecraft:aqua_affinity", enchantments.get(0));
        assertEquals("minecraft:luck_of_the_sea", enchantments.get(20));
        assertEquals("minecraft:lunge", enchantments.get(21));
        assertEquals("minecraft:lure", enchantments.get(22));
        assertEquals("minecraft:mending", enchantments.get(23));
        assertEquals("minecraft:wind_burst", enchantments.get(42));

        List<String> paintings = registries.get("minecraft:painting_variant");
        assertNotNullList(paintings, "minecraft:painting_variant");
        assertEquals(51, paintings.size(), version + " の絵画は 51 件: " + paintings);
        assertEquals("minecraft:dennis", paintings.get(14));
        assertEquals("minecraft:kebab", paintings.get(24));
        assertEquals("minecraft:wither", paintings.get(50));

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(43, banners.size(), version + " の旗の模様は 43 件: " + banners);
        assertEquals("minecraft:flow", banners.get(11));
        assertEquals("minecraft:flower", banners.get(12));
        assertEquals("minecraft:guster", banners.get(16));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(18, trims.size(), version + " のトリムは 18 件: " + trims);
        assertEquals("minecraft:bolt", trims.get(0));
        assertEquals("minecraft:flow", trims.get(4));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(50, damages.size(), version + " のダメージ種別は 50 件: " + damages);
        assertEquals("minecraft:ender_pearl", damages.get(8));
        assertEquals("minecraft:mace_smash", damages.get(26));
        assertEquals("minecraft:magic", damages.get(27));
        assertEquals("minecraft:spear", damages.get(37));
        assertEquals("minecraft:spit", damages.get(38));
        assertEquals("minecraft:wither_skull", damages.get(49));

        List<String> songs = registries.get("minecraft:jukebox_song");
        assertNotNullList(songs, "minecraft:jukebox_song");
        assertEquals(21, songs.size(), version + " のジュークボックスの歌は 21 件: " + songs);
        assertEquals("minecraft:lava_chicken", songs.get(9));
        assertEquals("minecraft:mall", songs.get(10));
        assertEquals("minecraft:tears", songs.get(18));
        assertEquals("minecraft:wait", songs.get(19));
        assertEquals("minecraft:ward", songs.get(20));

        List<String> biomes = registries.get("minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        assertEquals(65, biomes.size(), version + " のバイオームは 65 件: " + biomes);
        assertEquals("minecraft:pale_garden", biomes.get(39));
        assertEquals("minecraft:plains", biomes.get(40));

        List<String> attributes = registries.get("minecraft:attribute");
        if (attributes != null) {
            assertEquals(35, attributes.size(), version + " の属性は 35 件: " + attributes);
            assertEquals("minecraft:burning_time", attributes.get(7));
            assertEquals("minecraft:camera_distance", attributes.get(8));
            assertEquals("minecraft:tempt_range", attributes.get(31));
            assertEquals("minecraft:water_movement_efficiency", attributes.get(32));
            assertEquals("minecraft:waypoint_transmit_range", attributes.get(33));
            assertEquals("minecraft:waypoint_receive_range", attributes.get(34));
        }
        List<String> blockEntities = registries.get("minecraft:block_entity_type");
        if (blockEntities != null) {
            assertEquals(49, blockEntities.size(),
                    version + " のブロックエンティティ種別は 49 件: " + blockEntities);
            assertEquals("minecraft:creaking_heart", blockEntities.get(10));
            assertEquals("minecraft:shelf", blockEntities.get(40));
            assertEquals("minecraft:vault", blockEntities.get(45));
            assertEquals("minecraft:test_block", blockEntities.get(46));
            assertEquals("minecraft:test_instance_block", blockEntities.get(47));
            assertEquals("minecraft:copper_golem_statue", blockEntities.get(48));
        }
        List<String> instruments = registries.get("minecraft:instrument");
        if (instruments != null) {
            assertEquals(8, instruments.size(), version + " の楽器は 8 件: " + instruments);
            assertEquals("minecraft:admire_goat_horn", instruments.get(0));
            assertEquals("minecraft:yearn_goat_horn", instruments.get(7));
        }
        List<String> dialogs = registries.get("minecraft:dialog");
        assertNotNullList(dialogs, "minecraft:dialog");
        assertEquals(3, dialogs.size(), version + " のダイアログは 3 件: " + dialogs);
        assertEquals("minecraft:custom_options", dialogs.get(0));
        assertEquals("minecraft:quick_actions", dialogs.get(1));
        assertEquals("minecraft:server_links", dialogs.get(2));
    }


    /**
     * 本物の 1.21.6 の Registry Data の順番を検証する。
     * エンチャント・絵画・旗の模様・トリム・ダメージ種別・バイオーム・
     * ブロックエンティティ種別・楽器は 770 と同じ顔ぶれ・同じ順番。
     * 変わるのはジュークボックスの歌（tears が 17番に挿入されて 20 件）、
     * 属性（camera_distance が 8番に挿入、waypoint_transmit_range／
     * waypoint_receive_range が末尾に追加されて 35 件）、
     * 新規の minecraft:dialog（3 件）だけである。
     */

    /**
     * 本物の 1.21.5 の Registry Data の順番を検証する。
     * エンチャント・絵画・旗の模様・トリム・ダメージ種別・ジュークボックスの歌・
     * バイオームは 769 と同じ顔ぶれ・同じ順番。変わるのはブロックエンティティ種別
     * だけで、test_block（45）と test_instance_block（46）が末尾に追加されて 47 件になる。
     */
    private static void assertRegistryOrder1215Like(Map<String, List<String>> registries,
            String version) {
        List<String> enchantments = registries.get("minecraft:enchantment");
        assertNotNullList(enchantments, "minecraft:enchantment");
        assertEquals(42, enchantments.size(), version + " のエンチャントは 42 件: " + enchantments);
        assertEquals("minecraft:aqua_affinity", enchantments.get(0));
        assertEquals("minecraft:mending", enchantments.get(22));
        assertEquals("minecraft:wind_burst", enchantments.get(41));

        List<String> paintings = registries.get("minecraft:painting_variant");
        assertNotNullList(paintings, "minecraft:painting_variant");
        assertEquals(50, paintings.size(), version + " の絵画は 50 件: " + paintings);
        assertEquals("minecraft:kebab", paintings.get(23));
        assertEquals("minecraft:wither", paintings.get(49));

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(43, banners.size(), version + " の旗の模様は 43 件: " + banners);
        assertEquals("minecraft:flow", banners.get(11));
        assertEquals("minecraft:flower", banners.get(12));
        assertEquals("minecraft:guster", banners.get(16));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(18, trims.size(), version + " のトリムは 18 件: " + trims);
        assertEquals("minecraft:bolt", trims.get(0));
        assertEquals("minecraft:flow", trims.get(4));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(49, damages.size(), version + " のダメージ種別は 49 件: " + damages);
        assertEquals("minecraft:ender_pearl", damages.get(8));
        assertEquals("minecraft:mace_smash", damages.get(26));
        assertEquals("minecraft:magic", damages.get(27));
        assertEquals("minecraft:wither_skull", damages.get(48));

        List<String> songs = registries.get("minecraft:jukebox_song");
        assertNotNullList(songs, "minecraft:jukebox_song");
        assertEquals(19, songs.size(), version + " のジュークボックスの歌は 19 件: " + songs);
        assertTrue(songs.contains("minecraft:precipice"));

        List<String> biomes = registries.get("minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        assertEquals(65, biomes.size(), version + " のバイオームは 65 件: " + biomes);
        assertEquals("minecraft:pale_garden", biomes.get(39));
        assertEquals("minecraft:plains", biomes.get(40));

        List<String> attributes = registries.get("minecraft:attribute");
        if (attributes != null) {
            assertEquals(32, attributes.size(), version + " の属性は 32 件: " + attributes);
            assertEquals("minecraft:burning_time", attributes.get(7));
            assertEquals("minecraft:tempt_range", attributes.get(30));
            assertEquals("minecraft:water_movement_efficiency", attributes.get(31));
        }
        List<String> blockEntities = registries.get("minecraft:block_entity_type");
        if (blockEntities != null) {
            assertEquals(47, blockEntities.size(),
                    version + " のブロックエンティティ種別は 47 件: " + blockEntities);
            assertEquals("minecraft:creaking_heart", blockEntities.get(10));
            assertEquals("minecraft:vault", blockEntities.get(44));
            assertEquals("minecraft:test_block", blockEntities.get(45));
            assertEquals("minecraft:test_instance_block", blockEntities.get(46));
        }
        List<String> instruments = registries.get("minecraft:instrument");
        if (instruments != null) {
            assertEquals(8, instruments.size(), version + " の楽器は 8 件: " + instruments);
            assertEquals("minecraft:admire_goat_horn", instruments.get(0));
            assertEquals("minecraft:yearn_goat_horn", instruments.get(7));
        }
    }

    @Test
    void registryOrderOfRealVanilla1205() throws Exception {
        Map<String, List<String>> registries = capture("1.20.5", "server-1.20.5.jar", 766, 766);

        // 本物の1.20.5が設定フェーズで送るのは8レジストリだけ
        // （banner_pattern、chat_type、damage_type、dimension_type、
        // trim_material、trim_pattern、wolf_variant、worldgen/biome）。
        // enchantment と painting_variant は1.20.5ではコード登録のため
        // Registry Data では送られてこない。766側の並びは ViaVersion
        // mapping-1.20.5.json（リスト順＝ID順）で確認しており、
        // エンチャント42件（protection 0、density 37、breach 38、
        // wind_burst 39、mending 40、vanishing_curse 41）と
        // 絵画30件（配置可能な26件＋コマンド限定の earth・wind・water・
        // fire、donkey_kong は 29）は RegistryRemapper767TablesTest
        // で検証している。

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(41, banners.size(), "1.20.5 の旗の模様は 41 件: " + banners);
        assertEquals("minecraft:flower", banners.get(11));
        assertFalse(banners.contains("minecraft:flow"));
        assertFalse(banners.contains("minecraft:guster"));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(16, trims.size(), "1.20.5 のトリムは 16 件: " + trims);
        assertEquals("minecraft:coast", trims.get(0));
        assertFalse(trims.contains("minecraft:bolt"));
        assertFalse(trims.contains("minecraft:flow"));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(45, damages.size(), "1.20.5 のダメージ種別は 45 件: " + damages);
        assertFalse(damages.contains("minecraft:campfire"));
        assertFalse(damages.contains("minecraft:wind_charge"));

        assertTrue(!registries.containsKey("minecraft:jukebox_song"),
                "1.20.5 に jukebox_song レジストリは無いこと");
    }

    private static void assertRegistryOrder1216Like(Map<String, List<String>> registries,
            String version) {
        List<String> enchantments = registries.get("minecraft:enchantment");
        assertNotNullList(enchantments, "minecraft:enchantment");
        assertEquals(42, enchantments.size(), version + " のエンチャントは 42 件: " + enchantments);
        assertEquals("minecraft:aqua_affinity", enchantments.get(0));
        assertEquals("minecraft:mending", enchantments.get(22));
        assertEquals("minecraft:wind_burst", enchantments.get(41));

        List<String> paintings = registries.get("minecraft:painting_variant");
        assertNotNullList(paintings, "minecraft:painting_variant");
        assertEquals(50, paintings.size(), version + " の絵画は 50 件: " + paintings);
        assertEquals("minecraft:kebab", paintings.get(23));
        assertEquals("minecraft:wither", paintings.get(49));

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(43, banners.size(), version + " の旗の模様は 43 件: " + banners);
        assertEquals("minecraft:flow", banners.get(11));
        assertEquals("minecraft:flower", banners.get(12));
        assertEquals("minecraft:guster", banners.get(16));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(18, trims.size(), version + " のトリムは 18 件: " + trims);
        assertEquals("minecraft:bolt", trims.get(0));
        assertEquals("minecraft:flow", trims.get(4));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(49, damages.size(), version + " のダメージ種別は 49 件: " + damages);
        assertEquals("minecraft:ender_pearl", damages.get(8));
        assertEquals("minecraft:mace_smash", damages.get(26));
        assertEquals("minecraft:magic", damages.get(27));
        assertEquals("minecraft:wither_skull", damages.get(48));

        List<String> songs = registries.get("minecraft:jukebox_song");
        assertNotNullList(songs, "minecraft:jukebox_song");
        assertEquals(20, songs.size(), version + " のジュークボックスの歌は 20 件: " + songs);
        assertEquals("minecraft:tears", songs.get(17));
        assertEquals("minecraft:wait", songs.get(18));
        assertEquals("minecraft:ward", songs.get(19));

        List<String> biomes = registries.get("minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        assertEquals(65, biomes.size(), version + " のバイオームは 65 件: " + biomes);
        assertEquals("minecraft:pale_garden", biomes.get(39));
        assertEquals("minecraft:plains", biomes.get(40));

        List<String> attributes = registries.get("minecraft:attribute");
        if (attributes != null) {
            assertEquals(35, attributes.size(), version + " の属性は 35 件: " + attributes);
            assertEquals("minecraft:burning_time", attributes.get(7));
            assertEquals("minecraft:camera_distance", attributes.get(8));
            assertEquals("minecraft:tempt_range", attributes.get(31));
            assertEquals("minecraft:water_movement_efficiency", attributes.get(32));
            assertEquals("minecraft:waypoint_transmit_range", attributes.get(33));
            assertEquals("minecraft:waypoint_receive_range", attributes.get(34));
        }
        List<String> blockEntities = registries.get("minecraft:block_entity_type");
        if (blockEntities != null) {
            assertEquals(47, blockEntities.size(),
                    version + " のブロックエンティティ種別は 47 件: " + blockEntities);
            assertEquals("minecraft:creaking_heart", blockEntities.get(10));
            assertEquals("minecraft:vault", blockEntities.get(44));
            assertEquals("minecraft:test_block", blockEntities.get(45));
            assertEquals("minecraft:test_instance_block", blockEntities.get(46));
        }
        List<String> instruments = registries.get("minecraft:instrument");
        if (instruments != null) {
            assertEquals(8, instruments.size(), version + " の楽器は 8 件: " + instruments);
            assertEquals("minecraft:admire_goat_horn", instruments.get(0));
            assertEquals("minecraft:yearn_goat_horn", instruments.get(7));
        }
        List<String> dialogs = registries.get("minecraft:dialog");
        assertNotNullList(dialogs, "minecraft:dialog");
        assertEquals(3, dialogs.size(), version + " のダイアログは 3 件: " + dialogs);
        assertEquals("minecraft:custom_options", dialogs.get(0));
        assertEquals("minecraft:quick_actions", dialogs.get(1));
        assertEquals("minecraft:server_links", dialogs.get(2));
    }

    /**
     * 1.21.7／1.21.8 の並び。1216 との違いは絵画 dennis（14 に挿入で
     * kebab は 24、wither は 50 になる）とジュークボックス lava_chicken
     * （9 に挿入で mall は 10、tears は 18、wait は 19、ward は 20 になる）
     * だけ。1.21.8 は同じ assert で同一性も確かめる。
     */
    private static void assertRegistryOrder1217Like(Map<String, List<String>> registries,
            String version) {
        List<String> enchantments = registries.get("minecraft:enchantment");
        assertNotNullList(enchantments, "minecraft:enchantment");
        assertEquals(42, enchantments.size(), version + " のエンチャントは 42 件: " + enchantments);
        assertEquals("minecraft:aqua_affinity", enchantments.get(0));
        assertEquals("minecraft:mending", enchantments.get(22));
        assertEquals("minecraft:wind_burst", enchantments.get(41));

        List<String> paintings = registries.get("minecraft:painting_variant");
        assertNotNullList(paintings, "minecraft:painting_variant");
        assertEquals(51, paintings.size(), version + " の絵画は 51 件: " + paintings);
        assertEquals("minecraft:dennis", paintings.get(14));
        assertEquals("minecraft:kebab", paintings.get(24));
        assertEquals("minecraft:wither", paintings.get(50));

        List<String> banners = registries.get("minecraft:banner_pattern");
        assertNotNullList(banners, "minecraft:banner_pattern");
        assertEquals(43, banners.size(), version + " の旗の模様は 43 件: " + banners);
        assertEquals("minecraft:flow", banners.get(11));
        assertEquals("minecraft:flower", banners.get(12));
        assertEquals("minecraft:guster", banners.get(16));

        List<String> trims = registries.get("minecraft:trim_pattern");
        assertNotNullList(trims, "minecraft:trim_pattern");
        assertEquals(18, trims.size(), version + " のトリムは 18 件: " + trims);
        assertEquals("minecraft:bolt", trims.get(0));
        assertEquals("minecraft:flow", trims.get(4));

        List<String> damages = registries.get("minecraft:damage_type");
        assertNotNullList(damages, "minecraft:damage_type");
        assertEquals(49, damages.size(), version + " のダメージ種別は 49 件: " + damages);
        assertEquals("minecraft:ender_pearl", damages.get(8));
        assertEquals("minecraft:mace_smash", damages.get(26));
        assertEquals("minecraft:magic", damages.get(27));
        assertEquals("minecraft:wither_skull", damages.get(48));

        List<String> songs = registries.get("minecraft:jukebox_song");
        assertNotNullList(songs, "minecraft:jukebox_song");
        assertEquals(21, songs.size(), version + " のジュークボックスの歌は 21 件: " + songs);
        assertEquals("minecraft:lava_chicken", songs.get(9));
        assertEquals("minecraft:mall", songs.get(10));
        assertEquals("minecraft:tears", songs.get(18));
        assertEquals("minecraft:wait", songs.get(19));
        assertEquals("minecraft:ward", songs.get(20));

        List<String> biomes = registries.get("minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        assertEquals(65, biomes.size(), version + " のバイオームは 65 件: " + biomes);
        assertEquals("minecraft:pale_garden", biomes.get(39));
        assertEquals("minecraft:plains", biomes.get(40));

        List<String> attributes = registries.get("minecraft:attribute");
        if (attributes != null) {
            assertEquals(35, attributes.size(), version + " の属性は 35 件: " + attributes);
            assertEquals("minecraft:burning_time", attributes.get(7));
            assertEquals("minecraft:camera_distance", attributes.get(8));
            assertEquals("minecraft:tempt_range", attributes.get(31));
            assertEquals("minecraft:water_movement_efficiency", attributes.get(32));
            assertEquals("minecraft:waypoint_transmit_range", attributes.get(33));
            assertEquals("minecraft:waypoint_receive_range", attributes.get(34));
        }
        List<String> blockEntities = registries.get("minecraft:block_entity_type");
        if (blockEntities != null) {
            assertEquals(47, blockEntities.size(),
                    version + " のブロックエンティティ種別は 47 件: " + blockEntities);
            assertEquals("minecraft:creaking_heart", blockEntities.get(10));
            assertEquals("minecraft:vault", blockEntities.get(44));
            assertEquals("minecraft:test_block", blockEntities.get(45));
            assertEquals("minecraft:test_instance_block", blockEntities.get(46));
        }
        List<String> instruments = registries.get("minecraft:instrument");
        if (instruments != null) {
            assertEquals(8, instruments.size(), version + " の楽器は 8 件: " + instruments);
            assertEquals("minecraft:admire_goat_horn", instruments.get(0));
            assertEquals("minecraft:yearn_goat_horn", instruments.get(7));
        }
        List<String> dialogs = registries.get("minecraft:dialog");
        assertNotNullList(dialogs, "minecraft:dialog");
        assertEquals(3, dialogs.size(), version + " のダイアログは 3 件: " + dialogs);
        assertEquals("minecraft:custom_options", dialogs.get(0));
        assertEquals("minecraft:quick_actions", dialogs.get(1));
        assertEquals("minecraft:server_links", dialogs.get(2));
    }

    @Test
    void registryOrderOfRealVanilla1214() throws Exception {
        assertRegistryOrder1214Like(capture("1.21.4", "server-1.21.4.jar", 769, 769), "1.21.4");
    }

    /**
     * 本物のバニラ1.19.3の Join Game codec の中身を検証する。1.19.3には
     * minecraft-data の loginPacket.json が無いため、761のダメージ種別は
     * 762（1.19.4）の42件と同じ順番だと仮定して対応表を作っていたが、
     * 本物の1.19.3サーバーの codec を読むとそもそも
     * {@code minecraft:damage_type} レジストリが存在しないことが分かった
     * （1.19.3の codec は chat_type・dimension_type・worldgen/biome の
     * 3つだけ。damage_type レジストリは 1.19.4 で追加された）。
     * つまりダメージ種別の順番の仮定は検証の対象ではなく、そもそも
     * 761にはダメージ種別のIDが存在しない。ワイヤ上でもダメージ種別は
     * Damage Event パケット（762専用・761クライアントには落とす）と
     * codec の中にしか現れないため、761↔762のダメージ種別変換は
     * 実質的に不要である（RegistryRemapper の対応表は恒等写像のまま
     * 残すが、呼び出し元は無い）。
     *
     * <p>1.19.3には設定フェーズが無いため、レジストリは Registry Data
     * ではなく Join Game の codec の中に届く。偽クライアントは
     * プロキシ経由で参加し（761＋761のネイティブ）、Join Game（0x24）
     * を読んで codec を取り出す。プロキシは Join Game を素通しするため、
     * ここで読む codec は本物の1.19.3サーバーのものである。
     */
    @Test
    void registryOrderOfRealVanilla1193() throws Exception {
        Nbt.Tag.CompoundTag codec = captureJoinGameCodec1193();

        assertEquals(java.util.Set.of("minecraft:chat_type", "minecraft:dimension_type",
                "minecraft:worldgen/biome"), codec.value().keySet(),
                "1.19.3 の Join Game codec は3レジストリだけ");
        assertTrue(codec.getCompound("minecraft:damage_type").isEmpty(),
                "1.19.3 の codec に damage_type レジストリは無いこと"
                        + "（damage_type は 1.19.4 で追加。761のダメージ種別は存在しない）");

        List<String> biomes = orderedNames(codec, "minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        assertEquals(63, biomes.size(), "1.19.3 のバイオームは 63 件: " + biomes);
        assertFalse(biomes.contains("minecraft:cherry_grove"),
                "1.19.3 に cherry_grove バイオームは無いこと（1.20で追加）");
        assertEquals("minecraft:plains", biomes.get(38));
    }

    /** 本物のバニラ1.19.3にプロキシ経由で参加し、Join Game の codec を返す。 */
    private Nbt.Tag.CompoundTag captureJoinGameCodec1193() throws Exception {
        Path realJar = findRealVanilla1193Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.3.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla-1193");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.3.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();

        try (FakeMojangForCapture mojang = new FakeMojangForCapture(
                vanillaDir.resolve("server-1.19.3.jar"), "1.19.3")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.3", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 761);
                proxy.start();
                try (CapturingClient client = new CapturingClient(proxyPort, 761)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(761, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart761("Capture")));
                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");
                    MinecraftPacket joinGame = client.waitForId(0x24, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x24）が届きませんでした。");
                    assertFalse(client.isClosed(), "参加後に接続が切れていないこと");
                    PlayTranslator.JoinGame763 parsed =
                            PlayTranslator.parseJoinGame762(joinGame.payload());
                    assertTrue(parsed.codec() != null, "Join Game に codec が含まれていること");
                    return parsed.codec();
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    /**
     * 本物のバニラ1.19.2の Join Game codec の中身を検証する。1.19.2にも
     * {@code minecraft:damage_type} レジストリは存在しない
     * （1.19.2の codec は chat_type・dimension_type・worldgen/biome の
     * 3つだけ。damage_type レジストリは 1.19.4 で追加された）。
     *
     * <p>バイオームの並びは 761 とは異なり、minecraft-data の biomes.json
     * どおり plains が 1 番であること（761では 38 番）を本物のサーバーで
     * 確認する。これが RegistryTables760 の BIOME 対応表の根拠である。
     */
    @Test
    void registryOrderOfRealVanilla1192() throws Exception {
        Nbt.Tag.CompoundTag codec = captureJoinGameCodec1192();

        assertEquals(java.util.Set.of("minecraft:chat_type", "minecraft:dimension_type",
                "minecraft:worldgen/biome"), codec.value().keySet(),
                "1.19.2 の Join Game codec は3レジストリだけ");
        assertTrue(codec.getCompound("minecraft:damage_type").isEmpty(),
                "1.19.2 の codec に damage_type レジストリは無いこと"
                        + "（damage_type は 1.19.4 で追加。760のダメージ種別は存在しない）");

        List<String> biomes = orderedNames(codec, "minecraft:worldgen/biome");
        assertNotNullList(biomes, "minecraft:worldgen/biome");
        assertEquals(63, biomes.size(), "1.19.2 のバイオームは 63 件: " + biomes);
        assertFalse(biomes.contains("minecraft:cherry_grove"),
                "1.19.2 に cherry_grove バイオームは無いこと（1.20で追加）");
        assertEquals("minecraft:plains", biomes.get(1),
                "1.19.2 の plains は 1 番（761では 38 番）");
    }

    /** 本物のバニラ1.19.2にプロキシ経由で参加し、Join Game の codec を返す。 */
    private Nbt.Tag.CompoundTag captureJoinGameCodec1192() throws Exception {
        Path realJar = findRealVanilla1192Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.19.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla-1192");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.19.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();

        try (FakeMojangForCapture mojang = new FakeMojangForCapture(
                vanillaDir.resolve("server-1.19.2.jar"), "1.19.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.19.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(), null, null, 760);
                proxy.start();
                try (CapturingClient client = new CapturingClient(proxyPort, 760)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(760, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart760("Capture")));
                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");
                    MinecraftPacket joinGame = client.waitForId(0x25, 30_000);
                    assertTrue(joinGame != null, "Join Game（0x25）が届きませんでした。");
                    assertFalse(client.isClosed(), "参加後に接続が切れていないこと");
                    PlayTranslator.JoinGame763 parsed =
                            PlayTranslator.parseJoinGame762(joinGame.payload());
                    assertTrue(parsed.codec() != null, "Join Game に codec が含まれていること");
                    return parsed.codec();
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

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

    /** 760の Login Start：名前＋署名データ無し＋任意UUID。 */
    private static byte[] loginStart760(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(false); // 署名データ無し
        writer.writeBoolean(true); // UUID あり
        writer.writeUuid(java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return writer.bytes();
    }

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

    /** 761の Login Start：名前＋UUIDの有無（true）＋UUID。762／763 と同じ形。 */
    private static byte[] loginStart761(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeBoolean(true);
        writer.writeUuid(java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return writer.bytes();
    }

    /** Join Game codec の1レジストリから、ID順に並べたエントリ名の一覧を返す。 */
    private static List<String> orderedNames(Nbt.Tag.CompoundTag codec, String registryName) {
        return codec.getCompound(registryName)
                .flatMap(registry -> registry.getList("value"))
                .map(list -> {
                    List<String[]> rows = new ArrayList<>();
                    for (Nbt.Tag tag : list.value()) {
                        if (tag instanceof Nbt.Tag.CompoundTag compound) {
                            String name = compound.getString("name").orElse(null);
                            int id = compound.get("id")
                                    .filter(t -> t instanceof Nbt.Tag.IntTag)
                                    .map(t -> ((Nbt.Tag.IntTag) t).value())
                                    .orElse(-1);
                            if (name != null && id >= 0) {
                                rows.add(new String[]{String.valueOf(id), name});
                            }
                        }
                    }
                    rows.sort(Comparator.comparingInt(r -> Integer.parseInt(r[0])));
                    List<String> names = new ArrayList<>();
                    for (String[] row : rows) {
                        names.add(row[1]);
                    }
                    return names;
                })
                .orElse(null);
    }

    private static void assertNotNullList(List<String> list, String registry) {
        assertTrue(list != null, registry + " の Registry Data が届いていません。");
    }
}
