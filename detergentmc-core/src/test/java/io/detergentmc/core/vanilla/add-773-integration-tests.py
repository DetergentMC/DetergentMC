#!/usr/bin/env python3
"""ProxyIntegrationTest に 773（1.21.9／1.21.10）の統合テストを追加する。

- findRealVanilla1219Jar / findRealVanilla12110Jar
- Fake773Client（Fake772Client と同じ設定フェーズの ID。
  プレイは 773 の ID：Join Game 0x30、Spawn Position 0x5F、
  Keep Alive 0x2B→応答 0x1B、Ping 0x3B→応答 0x2C。
  Client Information は 773 形式（772 と同じ9フィールド）。
  Set Player Position は 0x1D（772 と同じ）。
  設定フェーズで Code of Conduct（0x13）が来たら Accept（0x09、空）を
  返す備えを入れる。既定のオフラインバニラ 1.21.9 は CoC を送って
  こないことは 2026-11-12 の生ソケットプローブで確認済み
  （tools/probe1219-coc.py／probe1219-coc2.py）。）
- clientInformation773() ヘルパー（772 と同じ9フィールド）
- chatMessage773() ヘルパー（772 と同じ、末尾の checksum 0x00）
- マトリクス 8 件（773クライアント×内部773（1.21.9・1.21.10）／
  内部772（1.21.7・1.21.8）／内部771（1.21.6）／内部763（1.20.1）、
  772クライアント×内部773（1.21.9・1.21.10））
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
    /** 本物のバニラ1.21.9のjarを探す。無ければ内部773の統合テストはスキップする。 */
    private static Path findRealVanilla1219Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.9.jar",
                "../run/vanilla/server-1.21.9.jar",
                "../../run/vanilla/server-1.21.9.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 本物のバニラ1.21.10のjarを探す。無ければ内部773の統合テストはスキップする。 */
    private static Path findRealVanilla12110Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.21.10.jar",
                "../run/vanilla/server-1.21.10.jar",
                "../../run/vanilla/server-1.21.10.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }
"""

HELPERS = """
    /** 773の設定フェーズの Client Information（0x00）。772と同じ9フィールド。 */
    private static byte[] clientInformation773() {
        return clientInformation772();
    }

    /** 773の Chat Message（0x08）。772と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage773(String message) {
        return chatMessage772(message);
    }
"""

FAKE773 = """
    /** 生ソケットの773クライアント。設定フェーズの番号は772と同じ。
     * （Join Game 0x30、Spawn Position 0x5F、Keep Alive 0x2B→応答 0x1B、
     * Ping 0x3B→応答 0x2C。Client Information は 773 形式（772 と同じ
     * 9フィールド）で送る。Chat Message は 0x08、末尾に checksum 0x00 を付ける。
     * Set Player Position は 0x1D。）
     * 設定フェーズで Code of Conduct（0x13）が来たら Accept（0x09、空）を
     * 返す。既定のオフラインバニラ 1.21.9 は CoC を送ってこないが、
     * CoC を送るサーバーに対する備えである。
     * ネイティブ 773＋773 ではプロキシが Select Known Packs をそのまま
     * 中継するため、答えないと本物の 1.21.9 サーバーが先に進まない。 */
    private static final class Fake773Client implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;
        /** 0=ログイン、1=設定フェーズ、2=プレイ。 */
        private volatile int state = 0;
        /** 設定フェーズで Registry Data（0x07）を受け取ったか。 */
        private volatile boolean receivedRegistryData;

        Fake773Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(30_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "fake-773-client-reader");
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
                        } else if (packet.id() == 0x13) { // Code of Conduct
                            // Accept Code of Conduct（0x09、空ペイロード）を返す。
                            sendPacket(new MinecraftPacket(0x09, new byte[0]));
                        }
                    } else if (state == 2 && packet.id() == 0x2B) { // プレイの Keep Alive
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x1B, response.bytes()));
                    } else if (state == 2 && packet.id() == 0x3B) { // プレイの Ping
                        Writer response = new Writer();
                        response.writeLong(new PacketIO.Reader(packet.payload()).readLong());
                        sendPacket(new MinecraftPacket(0x2C, response.bytes()));
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


def make_test(*, javadoc, method, finder, jar, version, internal,
              client_class, client_protocol, client_info, chat_fn,
              join_hex, join_dec, spawn_hex, spawn_dec, chat, short_proxy, synth):
    proxy_args = (
        "null, null, ProxyAuthConfig.defaults(), null, null, null, 763);"
        if short_proxy else
        "null, null, ProxyAuthConfig.defaults(),\n                        "
        "null, null, null, null, null, null, " + str(internal) + ");")
    join_assert = f'assertTrue(joinGame != null, "Join Game（{join_dec}形式 {join_hex}）が届きませんでした。");'
    registry_assert = (
        '                    assertTrue(client.receivedRegistryData,\n'
        '                            "合成された設定フェーズで Registry Data（0x07）を受け取っていること");'
        if synth else
        '                    assertTrue(client.receivedRegistryData,\n'
        '                            "設定フェーズで Registry Data（0x07）を受け取っていること");')
    join_comment = (
        '                    // 773の Join Game に codec は無い（レジストリは合成した\n'
        '                    // 設定フェーズの Registry Data で届く）。合成された\n'
        '                    // Registry Data を受け取ったことを確認する。\n'
        if synth else '')
    return f"""
{javadoc}
    @Test
    void {method}() throws Exception {{
        Path realJar = {finder}();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/{jar}）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("{jar}"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("{jar}"), "{version}")) {{
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "{version}", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {{
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Yuki")) {{
                    joined.countDown();
                }}
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("{chat}")) {{
                    chatSeen.countDown();
                }}
            }});
            host.start();
            try {{
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        {proxy_args}
                assertEquals({internal}, proxy.internalProtocol());
                proxy.start();
                try ({client_class} client = new {client_class}(proxyPort)) {{
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload({client_protocol}, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, {client_info}()));

                    MinecraftPacket joinGame = client.waitForId({join_hex}, 30_000);
                    {join_assert}
{join_comment}{registry_assert}

                    MinecraftPacket spawn = client.waitForId({spawn_hex}, 30_000);
                    assertTrue(spawn != null, "Spawn Position（{spawn_dec}形式 {spawn_hex}）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            {chat_fn}("{chat}")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Yuki の参加が現れませんでした。");
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


def build_tests():
    tests = []
    c773 = dict(client_class="Fake773Client", client_protocol=773,
                client_info="clientInformation773", chat_fn="chatMessage773",
                join_hex="0x30", join_dec="773", spawn_hex="0x5F", spawn_dec="773")
    c772 = dict(client_class="Fake772Client", client_protocol=772,
                client_info="clientInformation772", chat_fn="chatMessage772",
                join_hex="0x2B", join_dec="772", spawn_hex="0x5A", spawn_dec="772")
    tests.append(make_test(
        javadoc="""    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.9（内部773）に
     * 参加し、Join Game・スポーン・チャット・移動がネイティブで動くことを
     * 確認する。チャットの checksum（0x00）は773にもあるためそのまま。
     * 実バニラ1.21.9は Code of Conduct を送ってこない（既定の
     * オフラインサーバー。2026-11-12の生ソケットプローブで確認済み）。
     */""",
        method="fake773ClientJoinsRealVanilla1219ThroughProxy",
        finder="findRealVanilla1219Jar", jar="server-1.21.9.jar", version="1.21.9",
        internal=773, chat="hello from 1.21.9 to 1.21.9",
        short_proxy=False, synth=False, **c773))
    tests.append(make_test(
        javadoc="""    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.10（内部773）に
     * 参加する。1.21.9 と同じテストで、1.21.9／1.21.10 のレジストリ同一性
     * （プロトコル773を共有）もあわせて確認する。
     */""",
        method="fake773ClientJoinsRealVanilla12110ThroughProxy",
        finder="findRealVanilla12110Jar", jar="server-1.21.10.jar", version="1.21.10",
        internal=773, chat="hello from 1.21.10 to 1.21.10",
        short_proxy=False, synth=False, **c773))
    tests.append(make_test(
        javadoc="""    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.7（内部772）に
     * 参加し、Join Game・スポーン・チャット・移動が 773→772 の下向き変換で
     * 動くことを確認する。チャットの checksum は772・773ともあるため
     * そのまま。設定フェーズは 772 の Registry Data を 773 の順序と形式に
     * 変換する。Join Game と Spawn Position は 773 の番号（0x30／0x5F）
     * で届く。
     */""",
        method="fake773ClientJoinsRealVanilla1217ThroughProxy",
        finder="findRealVanilla1217Jar", jar="server-1.21.7.jar", version="1.21.7",
        internal=772, chat="hello from 1.21.9 to 1.21.7",
        short_proxy=False, synth=False, **c773))
    tests.append(make_test(
        javadoc="""    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.8（内部772）に
     * 参加する。1.21.7 と同じテストで、1.21.7／1.21.8 のレジストリ同一性
     * もあわせて確認する。
     */""",
        method="fake773ClientJoinsRealVanilla1218ThroughProxy",
        finder="findRealVanilla1218Jar", jar="server-1.21.8.jar", version="1.21.8",
        internal=772, chat="hello from 1.21.9 to 1.21.8",
        short_proxy=False, synth=False, **c773))
    tests.append(make_test(
        javadoc="""    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.6（内部771）に
     * 参加し、Join Game・スポーン・チャット・移動が 773→771 の下向き変換で
     * 動くことを確認する。チャットの checksum は771・773ともあるため
     * そのまま。設定フェーズは 771 の Registry Data を 773 の順序と形式に
     * 変換する。
     */""",
        method="fake773ClientJoinsRealVanilla1216ThroughProxy",
        finder="findRealVanilla1216Jar", jar="server-1.21.6.jar", version="1.21.6",
        internal=771, chat="hello from 1.21.9 to 1.21.6",
        short_proxy=False, synth=False, **c773))
    tests.append(make_test(
        javadoc="""    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加し、Join Game・スポーン・チャット・移動が 773→763 の下向き変換で
     * 動くことを確認する。内部763には設定フェーズが無いため、プロキシが
     * 773クライアント向けに Registry Data（dimension_type・biome）と
     * Finish Configuration を合成する。チャットの checksum は763には
     * 無いためプロキシが剥がす。
     */""",
        method="fake773ClientJoinsRealVanilla1201ThroughProxy",
        finder="findRealVanilla1201Jar", jar="server-1.20.1.jar", version="1.20.1",
        internal=763, chat="hello from 1.21.9 to 1.20.1",
        short_proxy=True, synth=True, **c773))
    tests.append(make_test(
        javadoc="""    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.9（内部773）に
     * 参加し、Join Game・スポーン・チャット・移動が 772→773 の上向き変換で
     * 動くことを確認する。チャットの checksum は772・773ともあるため
     * そのまま。設定フェーズは 773 の Registry Data を 772 の順序と形式に
     * 変換する。実バニラ1.21.9は Code of Conduct を送ってこないため、
     * 古いクライアントでも設定フェーズで止まらない（2026-11-12の
     * 生ソケットプローブで確認済み）。
     */""",
        method="fake772ClientJoinsRealVanilla1219ThroughProxy",
        finder="findRealVanilla1219Jar", jar="server-1.21.9.jar", version="1.21.9",
        internal=773, chat="hello from 1.21.7 to 1.21.9",
        short_proxy=False, synth=False, **c772))
    tests.append(make_test(
        javadoc="""    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.10（内部773）に
     * 参加する。1.21.9 と同じテストで、1.21.9／1.21.10 のレジストリ同一性
     * もあわせて確認する。
     */""",
        method="fake772ClientJoinsRealVanilla12110ThroughProxy",
        finder="findRealVanilla12110Jar", jar="server-1.21.10.jar", version="1.21.10",
        internal=773, chat="hello from 1.21.7 to 1.21.10",
        short_proxy=False, synth=False, **c772))
    return "".join(tests)


def main():
    text = PATH.read_text()
    # 1. findRealVanilla1219Jar / findRealVanilla12110Jar（FakeMojang の javadoc の直前）
    anchor_finder = """    /** 偽Mojang：マニフェストとバージョンJSONだけを返し、既存jarのSHA-1を名乗る。 */"""
    text = insert_once(text, anchor_finder, FINDERS, "finder")
    # 2. clientInformation773 / chatMessage773（765の Chat Message の javadoc の直前）
    anchor_helper = """    /** 765の Chat Message（0x05）。763と同じ署名付きの完全な形式。 */"""
    text = insert_once(text, anchor_helper, HELPERS, "helpers")
    # 3. Fake773Client（contains ヘルパーの直前）
    anchor_fake = """    private static boolean contains(byte[] bytes, String text) {"""
    text = insert_once(text, anchor_fake, FAKE773, "fake773")
    # 4. 8件のテスト（fake772ClientJoinsRealVanilla1217ThroughProxy の javadoc の直前）
    anchor_tests = """    /**
     * 偽772クライアントがプロキシ経由で本物のバニラ1.21.7（内部772）に
     * 参加し、Join Game・スポーン・チャット・移動がネイティブで動くことを
     * 確認する。チャットの checksum（0x00）は772にもあるためそのまま。
     * 1.21.7／1.21.8 の checksum 未検証の仮定はこのテスト自体が検証する。
     */"""
    text = insert_once(text, anchor_tests, build_tests(), "tests")
    PATH.write_text(text)
    print("inserted 773 integration tests into ProxyIntegrationTest.java")


if __name__ == "__main__":
    main()
