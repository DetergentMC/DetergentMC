#!/usr/bin/env python3
"""ProxyIntegrationTest に 767（1.21／1.21.1）の統合テストを追加する。

- findRealVanilla1205Jar／121Jar／1211Jar
- Fake767Client（Fake766Client と同じ番号＋Select Known Packs 0x0E に
  空の一覧 0x07 で答える。ネイティブ 767＋767 ではプロキシが
  Select Known Packs をそのまま中継するため、答えないと
  本物の 1.21 サーバーが先に進まない）
- マトリクス 9 件（767クライアント×内部763/764/765/766/767、
  763/764/765/766クライアント×内部767。1.21.1 は 767→767 で確認）
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent
for _ in range(8):
    ROOT = ROOT.parent
PATH = ROOT / ("detergentmc-core/src/test/java/io/detergentmc/core/vanilla/"
               "ProxyIntegrationTest.java")


def replace_once(text, old, new):
    if text.count(old) != 1:
        print(f"REPLACE 失敗（{text.count(old)}件）: {old[:90]!r}")
        sys.exit(1)
    return text.replace(old, new, 1)


def insert_once(text, anchor, block):
    if text.count(anchor) != 1:
        print(f"ANCHOR 失敗（{text.count(anchor)}件）: {anchor[:90]!r}")
        sys.exit(1)
    return text.replace(anchor, block + "\n" + anchor, 1)


FINDERS = """
    /** 本物のバニラ1.20.4のjarを探す（findRealVanillaJar の別名）。 */
    private static Path findRealVanilla1204Jar() {
        return findRealVanillaJar();
    }

    /** 本物のバニラ1.20.5のjarを探す。無ければ内部766の統合テストはスキップする。 */
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

    /** 本物のバニラ1.21のjarを探す。無ければ内部767の統合テストはスキップする。 */
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

    /** 本物のバニラ1.21.1のjarを探す。無ければ 1.21.1 の統合テストはスキップする。 */
    private static Path findRealVanilla1211Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.1.jar",
                "../run/vanilla/server-1.21.1.jar",
                "../../run/vanilla/server-1.21.1.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }
"""

FAKE767 = """
    /** 生ソケットの767クライアント。設定フェーズ・プレイのパケットIDは766と同じ。
     * 766との違いは Select Known Packs（0x0E）に空の一覧で答えること。
     * ネイティブ 767＋767 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21 サーバーが先に進まない。 */
    private static final class Fake767Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake767Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-767-client-reader");
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
                        sendPacket(new MinecraftPacket(0x18, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x35) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x27, response.bytes()));
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


def matrix_test(*, name, doc, client_class, handshake, internal, version,
                jar, player, chat, join_id, spawn_id, chat_id, move_id,
                login_start, client_info, chat_fn, move_fn, registry_assert):
    """設定フェーズありのクライアント（764／765／766／767）のテストを生成する。"""
    return f"""
    /**
     * {doc}
     */
    @Test
    void {name}() throws Exception {{
        Path realJar = findRealVanilla{jar}Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-{version}.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-{version}.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-{version}.jar"), "{version}")) {{
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "{version}", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {{
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("{player}")) {{
                    joined.countDown();
                }}
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("{player}")
                        && chat.message().equals("{chat}")) {{
                    chatSeen.countDown();
                }}
            }});
            host.start();
            try {{
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, ProxyAuthConfig.defaults(), null, null, {internal});
                assertEquals({internal}, proxy.internalProtocol());
                proxy.start();
                try ({client_class} client = new {client_class}(proxyPort)) {{
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload({handshake}, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, {login_start}("{player}")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, {client_info}()));

                    MinecraftPacket joinGame = client.waitForId({join_id}, 30_000);
                    assertTrue(joinGame != null, "Join Game（{join_id}）が届きませんでした。");
{registry_assert}
                    MinecraftPacket spawn = client.waitForId({spawn_id}, 30_000);
                    assertTrue(spawn != null, "Spawn Position（{spawn_id}）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket({chat_id},
                            {chat_fn}("{chat}")));
                    client.sendPacket(new MinecraftPacket({move_id}, {move_fn}()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに {player} の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                }} finally {{
                    proxy.stop();
                }}
            }} finally {{
                host.stop();
            }}
        }}
    }}
"""


TESTS_763 = """
    /**
     * 偽763クライアント（1.20.1）がプロキシ経由で本物のバニラ1.21
     * （内部767）に参加する。内部767には設定フェーズがあり、プロキシは
     * 767の Registry Data などを消費して 763 の Join Game（0x28）に
     * codec を合成する。Join Game・スポーン・チャット・移動が
     * 767→763 の下向き変換で動くことを確認する。
     */
    @Test
    void fake763ClientJoinsRealVanilla121ThroughProxy() throws Exception {
        Path realJar = findRealVanilla121Jar();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-1.21.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-1.21.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-1.21.jar"), "1.21")) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "1.21", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Hana")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Hana")
                        && chat.message().equals("hello from 1.20.1 to 1.21")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, ProxyAuthConfig.defaults(), null, null, 767);
                assertEquals(767, proxy.internalProtocol());
                proxy.start();
                try (Fake763Client client = new Fake763Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(763, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart763("Hana")));

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
                            chatMessage763("hello from 1.20.1 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x14, move763()));

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
"""

REGISTRY_767 = """                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");
"""
REGISTRY_765 = """                    // 767の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");
"""
REGISTRY_764 = """                    // 764の Join Game に codec は無い（レジストリは設定フェーズの
                    // Registry Data で届く）。次元名が入っていることを確認する。
                    assertTrue(contains(joinGame.payload(), "minecraft:overworld"),
                            "Join Game に dimension 名（minecraft:overworld）が含まれていること");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x05）を受け取っていること");
"""


def main():
    text = PATH.read_text(encoding="utf-8")
    if "Fake767Client" in text:
        print("既に追加済みのため何もしません。")
        return

    text = insert_once(text,
        "    /** 偽Mojang：マニフェストとバージョンJSONだけを返し、既存jarのSHA-1を名乗る。 */",
        FINDERS)
    text = insert_once(text,
        "    private static boolean contains(byte[] bytes, String text) {",
        FAKE767)

    tests = []
    tests.append(matrix_test(
        name="fake767ClientJoinsRealVanilla121ThroughProxy",
        doc="偽767クライアント（1.21／1.21.1）がプロキシ経由で本物のバニラ1.21\n"
            "     * （内部767）に参加し、Join Game・スポーン・チャット・移動が素通しで\n"
            "     * 動くことを確認する。設定フェーズは 767 の Registry Data などを\n"
            "     * プロキシがそのまま中継する（Select Known Packs への返事も含む）。",
        client_class="Fake767Client", handshake=767, internal=767, version="1.21",
        jar="121", player="Yuki", chat="hello from 1.21 to 1.21",
        join_id="0x2B", spawn_id="0x56", chat_id="0x06", move_id="0x1a",
        login_start="loginStart766", client_info="clientInformation766",
        chat_fn="chatMessage766", move_fn="move766", registry_assert=REGISTRY_767))
    tests.append(matrix_test(
        name="fake767ClientJoinsRealVanilla1211ThroughProxy",
        doc="偽767クライアントがプロキシ経由で本物のバニラ1.21.1（内部767）に\n"
            "     * 参加する。1.21.1 もプロトコル767のため 1.21 と同じ流れで動くこと。",
        client_class="Fake767Client", handshake=767, internal=767, version="1.21.1",
        jar="1211", player="Sora", chat="hello from 1.21 to 1.21.1",
        join_id="0x2B", spawn_id="0x56", chat_id="0x06", move_id="0x1a",
        login_start="loginStart766", client_info="clientInformation766",
        chat_fn="chatMessage766", move_fn="move766", registry_assert=REGISTRY_767))
    tests.append(matrix_test(
        name="fake767ClientJoinsRealVanilla1205ThroughProxy",
        doc="偽767クライアントがプロキシ経由で本物のバニラ1.20.5（内部766）に\n"
            "     * 参加し、Join Game・スポーン・チャット・移動が 766→767 の上向き変換で\n"
            "     * 動くことを確認する。設定フェーズは 766 の Registry Data を\n"
            "     * 767 の順序に並べ替えて中継する。",
        client_class="Fake767Client", handshake=767, internal=766, version="1.20.5",
        jar="1205", player="Rin", chat="hello from 1.21 to 1.20.5",
        join_id="0x2B", spawn_id="0x56", chat_id="0x06", move_id="0x1a",
        login_start="loginStart766", client_info="clientInformation766",
        chat_fn="chatMessage766", move_fn="move766", registry_assert=REGISTRY_767))
    tests.append(matrix_test(
        name="fake766ClientJoinsRealVanilla121ThroughProxy",
        doc="偽766クライアント（1.20.5／1.20.6）がプロキシ経由で本物のバニラ1.21\n"
            "     * （内部767）に参加し、Join Game・スポーン・チャット・移動が 767→766 の\n"
            "     * 下向き変換で動くことを確認する。設定フェーズは 767 の Registry Data を\n"
            "     * 766 の順序に並べ替えて中継する（jukebox_song は落とす）。",
        client_class="Fake766Client", handshake=766, internal=767, version="1.21",
        jar="121", player="Hana", chat="hello from 1.20.5 to 1.21",
        join_id="0x2B", spawn_id="0x56", chat_id="0x06", move_id="0x1a",
        login_start="loginStart766", client_info="clientInformation766",
        chat_fn="chatMessage766", move_fn="move766", registry_assert=REGISTRY_767))
    tests.append(matrix_test(
        name="fake767ClientJoinsRealVanilla1204ThroughProxy",
        doc="偽767クライアントがプロキシ経由で本物のバニラ1.20.4（内部765）に\n"
            "     * 参加し、Join Game・スポーン・チャット・移動が 765→767 の上向き変換で\n"
            "     * 動くことを確認する。設定フェーズは 765 の codec 形式 Registry Data を\n"
            "     * 767 のエントリ配列形式に変換して中継する。",
        client_class="Fake767Client", handshake=767, internal=765, version="1.20.4",
        jar="1204", player="Aoi", chat="hello from 1.21 to 1.20.4",
        join_id="0x2B", spawn_id="0x56", chat_id="0x06", move_id="0x1a",
        login_start="loginStart766", client_info="clientInformation766",
        chat_fn="chatMessage766", move_fn="move766", registry_assert=REGISTRY_767))
    tests.append(matrix_test(
        name="fake765ClientJoinsRealVanilla121ThroughProxy",
        doc="偽765クライアント（1.20.3／1.20.4）がプロキシ経由で本物のバニラ1.21\n"
            "     * （内部767）に参加し、Join Game・スポーン・チャット・移動が 767→765 の\n"
            "     * 下向き変換で動くことを確認する。設定フェーズは 767 の Registry Data を\n"
            "     * 765 の codec 形式に変換して中継する。",
        client_class="Fake765Client", handshake=765, internal=767, version="1.21",
        jar="121", player="Rin", chat="hello from 1.20.4 to 1.21",
        join_id="0x29", spawn_id="0x54", chat_id="0x05", move_id="0x17",
        login_start="loginStart765", client_info="clientInformation765",
        chat_fn="chatMessage765", move_fn="move765", registry_assert=REGISTRY_765))
    tests.append(matrix_test(
        name="fake767ClientJoinsRealVanilla1202ThroughProxy",
        doc="偽767クライアントがプロキシ経由で本物のバニラ1.20.2（内部764）に\n"
            "     * 参加し、Join Game・スポーン・チャット・移動が 764→767 の上向き変換で\n"
            "     * 動くことを確認する。設定フェーズは 764 の Registry Data を\n"
            "     * 767 のエントリ配列形式に変換して中継する。",
        client_class="Fake767Client", handshake=767, internal=764, version="1.20.2",
        jar="1202", player="Sora", chat="hello from 1.21 to 1.20.2",
        join_id="0x2B", spawn_id="0x56", chat_id="0x06", move_id="0x1a",
        login_start="loginStart766", client_info="clientInformation766",
        chat_fn="chatMessage766", move_fn="move766", registry_assert=REGISTRY_767))
    tests.append(matrix_test(
        name="fake764ClientJoinsRealVanilla121ThroughProxy",
        doc="偽764クライアント（1.20.2）がプロキシ経由で本物のバニラ1.21\n"
            "     * （内部767）に参加し、Join Game・スポーン・チャット・移動が 767→764 の\n"
            "     * 下向き変換で動くことを確認する。設定フェーズは 767 の Registry Data を\n"
            "     * 764 の codec 形式に変換して中継する。",
        client_class="Fake764Client", handshake=764, internal=767, version="1.21",
        jar="121", player="Aoi", chat="hello from 1.20.2 to 1.21",
        join_id="0x29", spawn_id="0x52", chat_id="0x05", move_id="0x16",
        login_start="loginStart764", client_info="clientInformation764",
        chat_fn="chatMessage764", move_fn="move764", registry_assert=REGISTRY_764))
    tests.append(TESTS_763)

    # クラスの閉じ括弧の直前にテストを追加する（最後の "}" を置き換える）。
    if not text.rstrip().endswith("}"):
        print("ファイル末尾の想定が違います。")
        sys.exit(1)
    text = text.rstrip()
    assert text.endswith("}")
    text = text[:-1].rstrip() + "\n" + "".join(tests) + "}\n"
    PATH.write_text(text, encoding="utf-8")
    print(f"ProxyIntegrationTest に {len(tests)} 件のテストを追加しました。")


if __name__ == "__main__":
    main()
