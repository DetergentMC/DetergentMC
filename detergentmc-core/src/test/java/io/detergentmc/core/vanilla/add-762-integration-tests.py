#!/usr/bin/env python3
"""ProxyIntegrationTest に 762（1.19.4）の統合テストを追加する。

- findRealVanilla1194Jar
- Fake762Client（Fake763Client と同じ ID。Join Game 0x28、
  Spawn Position 0x50、Keep Alive 0x23→応答 0x12。
  762・763 とも設定フェーズが無いため beginConfiguration は無い。）
- loginStart762()／chatMessage762()／move762() ヘルパー
  （763 と同じ形式：Login Start は名前＋bool＋UUID、Chat Message は
  署名付き完全形式、移動は 0x14）
- マトリクス 4 件（762クライアント×内部762（1.19.4）／
  内部763（1.20.1）／内部774（1.21.11）、763クライアント×内部762）
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
"""

FAKE_762 = """
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
"""

HELPERS_762 = """
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
"""

TESTS_762 = """
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
"""


def main():
    text = PATH.read_text()
    text = insert_once(text,
                       "    /** 本物のバニラ1.20.4のjarを探す（findRealVanillaJar の別名）。 */",
                       FINDER, "finder")
    text = insert_once(text,
                       "    private static int readVarInt(InputStream in) throws IOException {",
                       FAKE_762, "Fake762Client")
    text = insert_once(text,
                       "    /** 実際の1.20.1クライアントと同じ、署名付きの完全な Chat Message 形式。 */",
                       HELPERS_762, "helpers762")
    # ファイル末尾の閉じ括弧の直前にテストを追加する。
    if text.count("\n}\n") != 1:
        print("末尾アンカー失敗")
        sys.exit(1)
    text = text[:-3] + TESTS_762 + "}\n"
    PATH.write_text(text)
    print("762統合テストを追加しました。")


if __name__ == "__main__":
    main()
