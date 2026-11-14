#!/usr/bin/env python3
"""ProxyIntegrationTest に 770（1.21.5）の統合テストを追加する。

- findRealVanilla1215Jar
- Fake770Client（Fake769Client と同じ設定フェーズの ID。
  プレイは 770 の ID：Join Game 0x2B、Spawn Position 0x5A、
  Keep Alive 0x26→応答 0x1A、Ping 0x36→応答 0x2B（Pong）。
  Client Information は 770 形式（末尾に particleStatus）で送る。）
- clientInformation770() ヘルパー（769 形式と同じ9フィールド）
- chatMessage770() ヘルパー（769 形式＋末尾の checksum 0x00。
  0x00 が本物の 1.21.5 サーバーに受け入れられるかは未検証。
  このテストが通れば受け入れられることが確認できる。）
- マトリクス 3 件（770クライアント×内部770（1.21.5）／内部769（1.21.4）、
  769クライアント×内部770（1.21.5））
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent
for _ in range(8):
    ROOT = ROOT.parent
PATH = ROOT / ("detergentmc-core/src/test/java/io/detergentmc/core/vanilla/"
               "ProxyIntegrationTest.java")


def replace_once(text, old, new, label=""):
    if text.count(old) != 1:
        print(f"REPLACE 失敗（{text.count(old)}件）{label}: {old[:90]!r}")
        sys.exit(1)
    return text.replace(old, new, 1)


def insert_once(text, anchor, block, label=""):
    if text.count(anchor) != 1:
        print(f"ANCHOR 失敗（{text.count(anchor)}件）{label}: {anchor[:90]!r}")
        sys.exit(1)
    return text.replace(anchor, block + "\n" + anchor, 1)


FINDERS = """
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
"""

HELPERS = """
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
"""

FAKE770 = """
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
"""

TESTS = """
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
                        null, null, ProxyAuthConfig.defaults(),
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
                        null, null, ProxyAuthConfig.defaults(),
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
                        null, null, ProxyAuthConfig.defaults(),
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
"""


def main():
    text = PATH.read_text()
    # 1. findRealVanilla1215Jar（findRealVanilla1214Jar の直後）
    anchor_finder = """    /** 偽Mojang：マニフェストとバージョンJSONだけを返し、既存jarのSHA-1を名乗る。 */"""
    text = insert_once(text, anchor_finder, FINDERS, "finder")
    # 2. clientInformation770 / chatMessage770（clientInformation769 の直後）
    anchor_helper = """    /** 765の Chat Message（0x05）。763と同じ署名付きの完全な形式。 */"""
    text = insert_once(text, anchor_helper, HELPERS, "helpers")
    # 3. Fake770Client（Fake769Client の直後、「contains」ヘルパーの直前）
    anchor_fake = """    private static boolean contains(byte[] bytes, String text) {"""
    text = insert_once(text, anchor_fake, FAKE770, "fake770")
    # 4. 3件のテスト（fake769ClientJoinsRealVanilla1212ThroughProxy の javadoc の直前）
    anchor_tests = """    /**
     * 偽769クライアントがプロキシ経由で本物のバニラ1.21.2（内部768）に
     * 参加し、Join Game・スポーン・チャット・移動が 768→769 の上向き変換で
     * 動くことを確認する。設定フェーズは 768 の Registry Data を
     * 769 の順序と形式に変換して中継する（pale_garden を挿入する）。
     */"""
    text = insert_once(text, anchor_tests, TESTS, "tests")
    PATH.write_text(text)
    print("inserted 770 integration tests into ProxyIntegrationTest.java")


if __name__ == "__main__":
    main()
