#!/usr/bin/env python3
"""ProxyIntegrationTest に 758（1.18.2）の統合テストを追加する。

- findRealVanilla1182Jar
- Fake758Client（設定フェーズなし。Join Game 0x26、
  Spawn Position 0x4b、Keep Alive 0x21→応答 0x0f。
  旧式チャット：クライアント行き 0x0f／サーバー行き 0x03。）
- loginStart758()／chatMessage758()／move758() ヘルパー
  （758 は Login Start が名前だけ、Chat Message は {message} だけ、
  移動は Player Position 0x11）
- マトリクス 6 件（758クライアント×内部758（1.18.2）／
  内部759（1.19）／内部763（1.20.1）／内部774（1.21.11）、
  759クライアント×内部758、763クライアント×内部758）

注意：1.18.2サーバーのセルは waitForReady を 300 秒にする。
Stage D で 1.18.2 のスポーン準備に 278 秒かかったため、
762 のときの 180 秒では足りないおそれがある。
1.19／1.20.1／1.21.11のセルも同じく 300 秒にする
（776と並行で動く環境では 1.19 サーバーの起動が 180 秒を超えた実績がある）。
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


FINDER = """
    /** 本物のバニラ1.18.2のjarを探す。無ければ内部758の統合テストはスキップする。 */
    private static Path findRealVanilla1182Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.18.2.jar",
                "../run/vanilla/server-1.18.2.jar",
                "../../run/vanilla/server-1.18.2.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }
"""

FAKE_758 = """
    /** 生ソケットの758クライアント。設定フェーズが無い。旧式チャット。 */
    private static final class Fake758Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        Fake758Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-758-client-reader");
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
"""

HELPERS_758 = """
    /** 758の Login Start：名前だけ（bool・UUID は無い）。 */
    private static byte[] loginStart758(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        return writer.bytes();
    }

    /** 758の旧式チャット：{message} だけ（署名なし）。「/」で始まるとコマンドになる。 */
    private static byte[] chatMessage758(String message) {
        Writer writer = new Writer();
        writer.writeString(message);
        return writer.bytes();
    }

    /** 758の移動：Player Position（0x11）。 */
    private static byte[] move758() {
        Writer writer = new Writer();
        writer.writeDouble(0.5);
        writer.writeDouble(64.0);
        writer.writeDouble(0.5);
        writer.writeBoolean(true);
        return writer.bytes();
    }
"""

TESTS_758 = """
    /**
     * 偽758クライアントがプロキシ経由で本物のバニラ1.18.2（内部758）に
     * 参加する。参加・スポーン・チャット・移動が 758 ネイティブで動くこと。
     * パケットIDは Join Game 0x26／Spawn Position 0x4b／Keep Alive 0x21／
     * Chat Message 0x03／移動 0x11。1.18.2は Java 17 で起動する（Java 21 は不要）。
     * スポーン準備に 278 秒かかった実績があるため waitForReady は 300 秒。
     */
    @Test
    void fake758ClientJoinsRealVanilla1182ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1182Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.18.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.18.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.18.2.jar"), "1.18.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.18.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("hello from 1.18.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(300_000), "バニラが300秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 758);
                assertEquals(758, proxy.internalProtocol());
                proxy.start();
                try (Fake758Client client = new Fake758Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(758, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart758("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（758形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（758形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage758("hello from 1.18.2")));
                    client.sendPacket(new MinecraftPacket(0x11, move758()));

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
     * 偽758クライアントがプロキシ経由で本物のバニラ1.19（内部759）に
     * 参加する。758↔759 の直接変換で動くこと。両方とも設定フェーズが無く、
     * ログインはすぐプレイ状態へ進む。758の旧式チャット {message} は
     * 759の署名付き Chat Message（0x04）に変換されてサーバーへ届く。
     */
    @Test
    void fake758ClientJoinsRealVanilla119ThroughProxy() throws Exception {
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
                        && chat.message().equals("hello from 1.18.2 to 1.19")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(300_000), "バニラが300秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 759);
                assertEquals(759, proxy.internalProtocol());
                proxy.start();
                try (Fake758Client client = new Fake758Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(758, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart758("Yuki")));

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（758形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（758形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage758("hello from 1.18.2 to 1.19")));
                    client.sendPacket(new MinecraftPacket(0x11, move758()));

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
     * 偽758クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。758↔759 の直接変換のあと、既存の759↔760↔761↔762↔763の
     * 変換を重ねる。両方とも設定フェーズが無いため、ログインはすぐ
     * プレイ状態へ進む。
     */
    @Test
    void fake758ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
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
                        && chat.message().equals("hello from 1.18.2 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(300_000), "バニラが300秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake758Client client = new Fake758Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(758, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart758("Hana")));

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（758形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（758形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage758("hello from 1.18.2 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x11, move758()));

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
     * 偽758クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。内部774の設定フェーズはプロキシが774クライアント役として
     * 消費し、Join Game の codec として758に送り直す。758↔759の直接変換の
     * あと、既存の759↔…↔774の変換を重ねる。
     */
    @Test
    void fake758ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
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
                        && joinedEvent.name().equals("Sora")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Sora")
                        && chat.message().equals("hello from 1.18.2 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(300_000), "バニラが300秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
                proxy.start();
                try (Fake758Client client = new Fake758Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(758, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart758("Sora")));

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（758形式 0x26）が届きませんでした。");
                    assertTrue(contains(joinGame.payload(), "minecraft:dimension_type"),
                            "Join Game の codec に dimension_type が含まれていること");
                    assertTrue(contains(joinGame.payload(), "minecraft:worldgen/biome"),
                            "Join Game の codec に biome が含まれていること");

                    MinecraftPacket spawn = client.waitForId(0x4b, 30_000);
                    assertTrue(spawn != null, "Spawn Position（758形式 0x4b）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage758("hello from 1.18.2 to 1.21.11")));
                    client.sendPacket(new MinecraftPacket(0x11, move758()));

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
     * 偽759クライアントがプロキシ経由で本物のバニラ1.18.2（内部758）に
     * 参加する。内部758には設定フェーズが無いため、ログインはすぐプレイ
     * 状態へ進む。758サーバーの Join Game は 759形式に直してから
     * 759クライアントへ送る。759の署名付きチャットは758サーバー向けに
     * 旧式チャット {message} に直す。
     * スポーン準備に 278 秒かかった実績があるため waitForReady は 300 秒。
     */
    @Test
    void fake759ClientJoinsRealVanilla1182ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1182Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.18.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.18.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.18.2.jar"), "1.18.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.18.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Ren")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Ren")
                        && chat.message().equals("hello from 1.19 to 1.18.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(300_000), "バニラが300秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 758);
                assertEquals(758, proxy.internalProtocol());
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
                            chatMessage759("hello from 1.19 to 1.18.2")));
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
     * 偽763クライアントがプロキシ経由で本物のバニラ1.18.2（内部758）に
     * 参加する。763→762→761→760→759→758 の合成変換で動くこと。
     * 内部758には設定フェーズが無いため、ログインはすぐプレイ状態へ進む。
     * スポーン準備に 278 秒かかった実績があるため waitForReady は 300 秒。
     */
    @Test
    void fake763ClientJoinsRealVanilla1182ThroughProxy() throws Exception {
        Path realJar = findRealVanilla1182Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.18.2.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.18.2.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.18.2.jar"), "1.18.2")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.18.2", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Aoi")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Aoi")
                        && chat.message().equals("hello from 1.20.1 to 1.18.2")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(300_000), "バニラが300秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 758);
                assertEquals(758, proxy.internalProtocol());
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
                            chatMessage763("hello from 1.20.1 to 1.18.2")));
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
"""


def main():
    text = PATH.read_text()
    text = insert_once(text,
                       "    /** 本物のバニラ1.20.4のjarを探す（findRealVanillaJar の別名）。 */",
                       FINDER, "finder")
    text = insert_once(text,
                       "    private static int readVarInt(InputStream in) throws IOException {",
                       FAKE_758, "Fake758Client")
    text = insert_once(text,
                       "    /** 実際の1.20.1クライアントと同じ、署名付きの完全な Chat Message 形式。 */",
                       HELPERS_758, "helpers758")
    # ファイル末尾の閉じ括弧の直前にテストを追加する。
    if text.count("\n}\n") != 1:
        print("末尾アンカー失敗")
        sys.exit(1)
    text = text[:-3] + TESTS_758 + "}\n"
    PATH.write_text(text)
    print("758統合テストを追加しました。")


if __name__ == "__main__":
    main()
