#!/usr/bin/env python3
"""774: ProxyIntegrationTest／RegistryOrderCaptureTest／BedrockMatrixIntegrationTest に
774 の統合テストを追加する。

- ProxyIntegrationTest：findRealVanilla12111Jar、clientInformation774／
  chatMessage774、Fake774Client（Fake773Client を抽出して 773→774 に置換）、
  偽774クライアント→実バニラ1.21.11／1.21（内部767）／1.20.1（内部763）の
  3テスト＋偽767／偽763クライアント→実バニラ1.21.11（内部774）の2テスト。
  パケットIDは774＝773のため Fake774Client の本体は Fake773Client と同一。
- RegistryOrderCaptureTest：findRealVanilla12111Jar、CapturingClient の
  774用 Keep Alive／Ping 番号、capture() の 1.21.11 分岐と Join Game 0x30、
  registryOrderOfRealVanilla12111（lunge=21・spear=37 等を実サーバーで検証）。
- BedrockMatrixIntegrationTest：matrixAgainst1_21_11 と runMatrix の Java 21
  条件への 774 追加。

全て exact-match（出現回数1を検証）で挿入する。失敗したら何も書き換えない
（各パートは検証してから書き込む）。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
PIT = ROOT / "detergentmc-core/src/test/java/io/detergentmc/core/vanilla/ProxyIntegrationTest.java"
ROC = ROOT / "detergentmc-core/src/test/java/io/detergentmc/core/vanilla/RegistryOrderCaptureTest.java"
BMI = ROOT / "detergentmc-core/src/test/java/io/detergentmc/core/vanilla/BedrockMatrixIntegrationTest.java"


def replace_once(path, old, new, label):
    src = path.read_text()
    n = src.count(old)
    assert n == 1, f"{label}: アンカーの出現回数が {n}（期待 1）"
    path.write_text(src.replace(old, new, 1))
    print(f"[{label}] OK")


# ----------------------------------------------------------------------
# ProxyIntegrationTest
# ----------------------------------------------------------------------

FIND_774 = '''    /** 本物のバニラ1.21.11のjarを探す。無ければ内部774の統合テストはスキップする。 */
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


'''

replace_once(PIT,
             '''        return null;
    }

    /** 偽Mojang：マニフェストとバージョンJSONだけを返し、既存jarのSHA-1を名乗る。 */''',
             '''        return null;
    }

''' + FIND_774 + '''    /** 偽Mojang：マニフェストとバージョンJSONだけを返し、既存jarのSHA-1を名乗る。 */''',
             "pit-find12111")

CLIENT_HELPERS_774 = '''    /** 774の設定フェーズの Client Information（0x00）。773と同じ9フィールド。 */
    private static byte[] clientInformation774() {
        return clientInformation773();
    }

    /** 774の Chat Message（0x08）。773と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage774(String message) {
        return chatMessage773(message);
    }

'''

replace_once(PIT,
             '''    /** 773の Chat Message（0x08）。772と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage773(String message) {
        return chatMessage772(message);
    }
''',
             '''    /** 773の Chat Message（0x08）。772と同じ形式＋末尾の checksum（0x00）。 */
    private static byte[] chatMessage773(String message) {
        return chatMessage772(message);
    }

''' + CLIENT_HELPERS_774,
             "pit-client-helpers-774")


def insert_fake774_client():
    src = PIT.read_text()
    start = src.index("    /** 生ソケットの773クライアント。設定フェーズの番号は772と同じ。")
    end_marker = "    }\n\n    private static boolean contains(byte[] bytes, String text) {"
    end = src.index(end_marker, start) + len("    }\n")
    block = src[start:end]
    assert "private static final class Fake773Client" in block
    new = block
    new = new.replace("設定フェーズの番号は772と同じ", "設定フェーズの番号は773と同じ")
    new = new.replace("773クライアント", "774クライアント")
    new = new.replace("Fake773Client", "Fake774Client")
    new = new.replace("fake-773-client-reader", "fake-774-client-reader")
    new = new.replace("773 形式", "774 形式")
    new = new.replace("773＋773", "774＋774")
    new = new.replace("1.21.9", "1.21.11")
    # 773 は「設定フェーズの番号は773と同じ」の1か所だけ残る（正しい記述）。
    assert new.count("773") == 1 and "設定フェーズの番号は773と同じ" in new, \
        "Fake774Client の 773 出現が想定外"
    # 772 は「Client Information は 774 形式（772 と同じ 9フィールド）」の1か所だけ。
    assert new.count("772") == 1, f"Fake774Client の 772 出現回数={new.count('772')}"
    assert new.count("774") >= 3
    PIT.write_text(src[:end] + "\n" + new + src[end:])
    print("[pit-fake774client] OK")


insert_fake774_client()


TESTS_774 = '''    /**
     * 偽774クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。参加・スポーン・チャット・移動が 774 ネイティブで動くこと。
     * パケットIDは773と同一（Join Game 0x30／Spawn Position 0x5F／
     * Keep Alive 0x2B／Ping 0x3B／Chat Message 0x08＋末尾 checksum）。
     */
    @Test
    void fake774ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
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
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.11 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
                proxy.start();
                try (Fake774Client client = new Fake774Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(774, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation774()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（774形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（774形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage774("hello from 1.21.11 to 1.21.11")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

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
     * 偽774クライアントがプロキシ経由で本物のバニラ1.21（内部767）に
     * 参加する。Join Game・スポーン・チャット・移動が 767→774 の上向き変換
     * （767→768→769→770→771→772→773→774 の七段）で動くこと。
     * 設定フェーズは 767 の Registry Data を 774 の順序と形式に変換する。
     */
    @Test
    void fake774ClientJoinsRealVanilla121ThroughProxy() throws Exception {
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
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21.11 to 1.21")) {
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
                try (Fake774Client client = new Fake774Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(774, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation774()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（774形式 0x30）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（774形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage774("hello from 1.21.11 to 1.21")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

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
     * 偽767クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。Join Game・スポーン・チャット・移動が 774→767 の下向き変換
     * （774→773→772→771→770→769→768→767 の七段）で動くこと。
     * 設定フェーズは 774 の Registry Data を 767 の順序と形式に変換する。
     */
    @Test
    void fake767ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
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
                        && joinedEvent.name().equals("Yuki")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Yuki")
                        && chat.message().equals("hello from 1.21 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
                proxy.start();
                try (Fake767Client client = new Fake767Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(767, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation766()));

                    MinecraftPacket joinGame = client.waitForId(0x2B, 30_000);
                    assertTrue(joinGame != null, "Join Game（767形式 0x2B）が届きませんでした。");
                    assertTrue(client.receivedRegistryData,
                            "設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x56, 30_000);
                    assertTrue(spawn != null, "Spawn Position（767形式 0x56）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x06,
                            chatMessage766("hello from 1.21 to 1.21.11")));
                    client.sendPacket(new MinecraftPacket(0x1a, move766()));

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
     * 偽763クライアントがプロキシ経由で本物のバニラ1.21.11（内部774）に
     * 参加する。端同士（763×774）の上向き変換。Join Game の codec に
     * dimension_type と biome が含まれ、スポーン・チャット・移動が動くこと。
     */
    @Test
    void fake763ClientJoinsRealVanilla12111ThroughProxy() throws Exception {
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
                        && chat.message().equals("hello from 1.20.1 to 1.21.11")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, 774);
                assertEquals(774, proxy.internalProtocol());
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
                            chatMessage763("hello from 1.20.1 to 1.21.11")));
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

    /**
     * 偽774クライアントがプロキシ経由で本物のバニラ1.20.1（内部763）に
     * 参加する。端同士（774×763）の下向き変換。内部763には設定フェーズが
     * 無いため、プロキシは 763 の Join Game の codec から 774 向けの
     * 設定フェーズ（Registry Data 0x07・Finish Configuration 0x03）を
     * 合成する。774にしか無い要素（槍・ノーチラス等）は 763 に無いため
     * レジストリには現れない。
     */
    @Test
    void fake774ClientJoinsRealVanilla1201ThroughProxy() throws Exception {
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
                        && chat.message().equals("hello from 1.21.11 to 1.20.1")) {
                    chatSeen.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(180_000), "バニラが180秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, ProxyAuthConfig.defaults(), null, null, null, 763);
                assertEquals(763, proxy.internalProtocol());
                proxy.start();
                try (Fake774Client client = new Fake774Client(proxyPort)) {
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload(774, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart766("Yuki")));

                    MinecraftPacket loginSuccess = client.waitForId(0x02, 30_000);
                    assertTrue(loginSuccess != null, "Login Success（0x02）が届きませんでした。");

                    client.beginConfiguration();
                    client.sendPacket(new MinecraftPacket(0x03, new byte[0])); // Login Acknowledged
                    client.sendPacket(new MinecraftPacket(0x00, clientInformation774()));

                    MinecraftPacket joinGame = client.waitForId(0x30, 30_000);
                    assertTrue(joinGame != null, "Join Game（774形式 0x30）が届きませんでした。");
                    // 774の Join Game に codec は無い（レジストリは合成した
                    // 設定フェーズの Registry Data で届く）。合成された
                    // Registry Data を受け取ったことを確認する。
                    assertTrue(client.receivedRegistryData,
                            "合成された設定フェーズで Registry Data（0x07）を受け取っていること");

                    MinecraftPacket spawn = client.waitForId(0x5F, 30_000);
                    assertTrue(spawn != null, "Spawn Position（774形式 0x5F）が届きませんでした。");

                    // チャットと移動を送る。
                    client.sendPacket(new MinecraftPacket(0x08,
                            chatMessage774("hello from 1.21.11 to 1.20.1")));
                    client.sendPacket(new MinecraftPacket(0x1D, move766()));

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

'''

replace_once(PIT,
             '''    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.7（内部772）に''',
             TESTS_774 + '''    /**
     * 偽773クライアントがプロキシ経由で本物のバニラ1.21.7（内部772）に''',
             "pit-tests-774")

print("ProxyIntegrationTest: ALL OK")

# ----------------------------------------------------------------------
# RegistryOrderCaptureTest
# ----------------------------------------------------------------------

FIND_774_ROC = '''    private static Path findRealVanilla12111Jar() {
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

'''

replace_once(ROC,
             '''    private static Path findRealVanilla1205Jar() {''',
             FIND_774_ROC + '''    private static Path findRealVanilla1205Jar() {''',
             "roc-find12111")

replace_once(ROC,
             '''        /** プレイの Keep Alive（クライアント行き）。768／769 は 0x27、766／767 は 0x26。 */
        private int keepAliveId() {
            return clientProtocol == 768 || clientProtocol == 769 ? 0x27 : 0x26;
        }''',
             '''        /** プレイの Keep Alive（クライアント行き）。774 は 0x2B、768／769 は 0x27、766／767 は 0x26。 */
        private int keepAliveId() {
            return clientProtocol == 774 ? 0x2B
                    : clientProtocol == 768 || clientProtocol == 769 ? 0x27 : 0x26;
        }''',
             "roc-keepalive-id")

replace_once(ROC,
             '''        /** プレイの Keep Alive への応答（サーバー行き）。768／769／770 は 0x1A、766／767 は 0x18。 */
        private int keepAliveResponseId() {
            return clientProtocol == 768 || clientProtocol == 769 || clientProtocol == 770
                    ? 0x1A : 0x18;
        }''',
             '''        /** プレイの Keep Alive への応答（サーバー行き）。774 は 0x1B、768／769／770 は 0x1A、766／767 は 0x18。 */
        private int keepAliveResponseId() {
            return clientProtocol == 774 ? 0x1B
                    : clientProtocol == 768 || clientProtocol == 769 || clientProtocol == 770
                    ? 0x1A : 0x18;
        }''',
             "roc-keepalive-response")

replace_once(ROC,
             '''        /** プレイの Ping（クライアント行き）。768／769 は 0x37、770 は 0x36、766／767 は 0x35。 */
        private int pingId() {
            return clientProtocol == 768 || clientProtocol == 769 ? 0x37
                    : clientProtocol == 770 ? 0x36 : 0x35;
        }''',
             '''        /** プレイの Ping（クライアント行き）。774 は 0x3B、768／769 は 0x37、770 は 0x36、766／767 は 0x35。 */
        private int pingId() {
            return clientProtocol == 774 ? 0x3B
                    : clientProtocol == 768 || clientProtocol == 769 ? 0x37
                    : clientProtocol == 770 ? 0x36 : 0x35;
        }''',
             "roc-ping-id")

replace_once(ROC,
             '''        /** プレイの Ping への応答（サーバー行き）。768／769 は 0x29、770 は 0x2B、766／767 は 0x27。 */
        private int pingResponseId() {
            return clientProtocol == 768 || clientProtocol == 769 ? 0x29
                    : clientProtocol == 770 ? 0x2B : 0x27;
        }''',
             '''        /** プレイの Ping への応答（サーバー行き）。774 は 0x2C、768／769 は 0x29、770 は 0x2B、766／767 は 0x27。 */
        private int pingResponseId() {
            return clientProtocol == 774 ? 0x2C
                    : clientProtocol == 768 || clientProtocol == 769 ? 0x29
                    : clientProtocol == 770 ? 0x2B : 0x27;
        }''',
             "roc-ping-response")

replace_once(ROC,
             '''            case "1.21.8" -> findRealVanilla1218Jar();''',
             '''            case "1.21.8" -> findRealVanilla1218Jar();
            case "1.21.11" -> findRealVanilla12111Jar();''',
             "roc-capture-switch")

replace_once(ROC,
             '''                    client.sendPacket(new MinecraftPacket(0x00,
                            clientInformation(clientProtocol == 768 || clientProtocol == 769
                                    || clientProtocol == 770 || clientProtocol == 771
                                    || clientProtocol == 772)));
                    int joinGameId = clientProtocol == 768 || clientProtocol == 769 ? 0x2C : 0x2B;''',
             '''                    client.sendPacket(new MinecraftPacket(0x00,
                            clientInformation(clientProtocol == 768 || clientProtocol == 769
                                    || clientProtocol == 770 || clientProtocol == 771
                                    || clientProtocol == 772 || clientProtocol == 774)));
                    int joinGameId = clientProtocol == 774 ? 0x30
                            : clientProtocol == 768 || clientProtocol == 769 ? 0x2C : 0x2B;''',
             "roc-capture-joingame")

ROC_12111 = '''    @Test
    void registryOrderOfRealVanilla12111() throws Exception {
        assertRegistryOrder12111Like(
                capture("1.21.11", "server-1.21.11.jar", 774, 774), "1.21.11");
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

'''

replace_once(ROC,
             '''    @Test
    void registryOrderOfRealVanilla1218() throws Exception {
        assertRegistryOrder1217Like(capture("1.21.8", "server-1.21.8.jar", 772, 772), "1.21.8");
    }
''',
             '''    @Test
    void registryOrderOfRealVanilla1218() throws Exception {
        assertRegistryOrder1217Like(capture("1.21.8", "server-1.21.8.jar", 772, 772), "1.21.8");
    }

''' + ROC_12111,
             "roc-test-12111")

print("RegistryOrderCaptureTest: ALL OK")

# ----------------------------------------------------------------------
# BedrockMatrixIntegrationTest
# ----------------------------------------------------------------------

replace_once(BMI,
             '''    @Test
    void matrixAgainst1_21_10() throws Exception {
        runMatrix("1.21.10", 773);
    }
''',
             '''    @Test
    void matrixAgainst1_21_10() throws Exception {
        runMatrix("1.21.10", 773);
    }

    @Test
    void matrixAgainst1_21_11() throws Exception {
        runMatrix("1.21.11", 774);
    }
''',
             "bmi-matrix-12111")

replace_once(BMI,
             '''                || serverProtocol == 771 || serverProtocol == 772
                || serverProtocol == 773) {''',
             '''                || serverProtocol == 771 || serverProtocol == 772
                || serverProtocol == 773 || serverProtocol == 774) {''',
             "bmi-java21-condition")

print("BedrockMatrixIntegrationTest: ALL OK")
print("ALL PARTS OK")
