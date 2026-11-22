package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import io.detergentmc.legacy.LegacyBackendLogin;
import io.detergentmc.legacy.LegacyConnection;
import io.detergentmc.legacy.LegacyFrontendConfig;
import io.detergentmc.legacy.LegacyFrontendServer;
import io.detergentmc.legacy.LegacyPacketIO.LegacyPacket;
import io.detergentmc.legacy.LegacyProtocol;
import io.detergentmc.legacy.LegacyWire;
import io.detergentmc.protocol.ClientProxy;
import io.detergentmc.protocol.ProxyAuthConfig;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * レガシーフロントエンド（プロトコル78＝1.6.4、74＝1.6.2、73＝1.6.1、
 * 61＝1.5.2、60＝1.5.1／1.5、51＝1.4.7／1.4.6、49＝1.4.5／1.4.4、
 * 旧47＝1.4.2、39＝1.3.2／1.3.1、29＝1.2.5／1.2.4、28＝1.2.3／1.2.2／
 * 1.2.1、23＝1.1、22＝1.0.0）の実バニラ統合テスト
 * （78 の Stage E で新設、74／73 の Stage E で拡張、61／60 の Stage E で
 * 1.5 世代を追加、51／49 の Stage E で 1.4 世代を追加、旧47 の Stage E で
 * 1.4.2 を追加、39 の Stage E で 1.3.2／1.3.1 を追加、29 の Stage E で
 * 1.2.5／1.2.4 を追加、28／23／22 の Stage E で 1.2.3／1.1／1.0.0 を追加）。
 * ⚠️ ここでいう「旧47」は Netty 以前の 1.4.2 であり、Netty 世代の
 * プロトコル47（1.8）とは別物である。
 *
 * <p>偽レガシークライアントは本物のレガシークライアントのワイヤ手順を
 * そのまま写した {@link LegacyBackendLogin} を使い、フロントエンドの
 * クライアント側に対してハンドシェイク→RSA＋AES/CFB8暗号化→ログインを
 * 行う（29／28／23／22 世代は暗号化が無く平文ログイン）。ブリッジ
 * モードでは背後に本物の {@link ClientProxy} と本物のバニラ
 * （1.7.10／1.8.9）を置き、ダイレクトモードでは本物のバニラ
 * （1.6.4／1.6.2／1.6.1／1.5.2／1.5.1／1.5／1.4.7／1.4.5／1.4.2／
 * 1.3.2／1.3.1／1.2.5／1.2.4／1.2.3／1.1／1.0.0）へ直接つなぐ。
 * 参加とチャット到達はバニラのログ（{@link VanillaLogEvent}）で
 * 裏付ける。1.4 世代・1.3 世代・1.2 世代・1.1・1.0.0 の
 * ログも旧接頭辞＋ {@code logged in} 行＋ {@code <名前>} チャットの形で、
 * 実バニラ 1.4.7／1.4.5／1.4.2／1.3.2／1.3.1／1.2.5／1.2.4／
 * 1.2.3／1.1／1.0.0 の server.log で実測確認済み（1.1／1.0.0 の
 * 起動完了行は {@code Done (…ns)! For help, type "help" or "?"} の形で、
 * 参加行は名前とアドレスの間に空白がある 1.2 世代と同じ形）。
 *
 * <p>本物のバニラjar（{@code run/vanilla/server-<version>.jar}）が無い
 * 環境ではスキップする。本物のレガシーゲームクライアントでの確認は
 * 未実施（docs/PLAYTEST-1.6.4.md、docs/PLAYTEST-1.6.2.md、
 * docs/PLAYTEST-1.6.1.md、docs/PLAYTEST-1.5.2.md、
 * docs/PLAYTEST-1.5.1.md、docs/PLAYTEST-1.4.7.md、
 * docs/PLAYTEST-1.4.5.md、docs/PLAYTEST-1.4.2.md、
 * docs/PLAYTEST-1.3.2.md、docs/PLAYTEST-1.2.5.md、
 * docs/PLAYTEST-1.2.3.md、docs/PLAYTEST-1.1.md、
 * docs/PLAYTEST-1.0.md）。
 */
@Timeout(900)
class LegacyFrontendIntegrationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void ensureJavaHomes() {
        Java8TestSupport.ensureJava8Home();
        Java21TestSupport.ensureJava21Home();
        Java25TestSupport.ensureJava25Home();
    }

    // ------------------------------------------------------------------
    // Bridge mode: legacy client -> frontend -> core proxy -> real vanilla
    // ------------------------------------------------------------------

    @Test
    void legacy78ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_6_4, "78");
    }

    @Test
    void legacy78ClientJoinsRealVanilla189ThroughFrontendBridge() throws Exception {
        bridgeCell("1.8.9", 47, LegacyProtocol.V1_6_4, "78");
    }

    @Test
    void legacy74ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_6_2, "74");
    }

    @Test
    void legacy73ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_6_1, "73");
    }

    @Test
    void legacy61ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_5_2, "61");
    }

    @Test
    void legacy60ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_5_1, "60");
    }

    @Test
    void legacy51ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_4_7, "51");
    }

    @Test
    void legacy49ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_4_5, "49");
    }

    @Test
    void legacy47OldClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_4_2, "47old");
    }

    @Test
    void legacy39ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_3_2, "39");
    }

    @Test
    void legacy29ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        // 29（1.2.5）。クライアント面は平文ログイン（暗号化境界
        // より下）。フロントエンドが平文で受けて合成プロトコル5
        // クライアントとしてコアへつなぐ経路を通す。
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_2_5, "29");
    }

    @Test
    void legacy28ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        // 28（1.2.3）。29 との差分は 0xCA player_abilities が
        // 無いことだけ（実測）。平文ログインで受けて合成
        // プロトコル5クライアントとしてコアへつなぐ経路を通す。
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_2_3, "28");
    }

    @Test
    void legacy23ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        // 23（1.1）。ログインは seed（I64）入りの過渡形で平文。
        // バックエンド（1.7.10）のセクション形式チャンクは
        // フロントエンドがキューボイド形式へ再配置して23の
        // クライアントへ渡す：ログイン直後のチャンク群を読み切り、
        // Keep Alive 応答後のチャットまで通ることが、チャンク
        // 到達と無切断の裏付けになる。
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_1, "23");
    }

    @Test
    void legacy22ClientJoinsRealVanilla1710ThroughFrontendBridge() throws Exception {
        // 22（1.0.0）。ログインは 23 から levelType 文字列が
        // 消えた形で平文。チャンクは 23 と同じキューボイド形式。
        // DetergentMC の対応範囲の最古世代のブリッジ経路を通す。
        bridgeCell("1.7.10", 5, LegacyProtocol.V1_0, "22");
    }

    private void bridgeCell(String backendVersion, int internalProtocol,
            int legacyProtocol, String label) throws Exception {
        Path realJar = findRealVanillaJar(backendVersion);
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-" + backendVersion + ".jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-" + backendVersion + ".jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        String chatFirst = "hello from " + label + " to " + backendVersion;
        String chatSecond = "still alive " + label + " to " + backendVersion + " after keep alive";
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);
        CountDownLatch chatAfterKeepAlive = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-" + backendVersion + ".jar"), backendVersion)) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, backendVersion, vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals(chatFirst)) {
                    chatSeen.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals(chatSecond)) {
                    chatAfterKeepAlive.countDown();
                }
            });
            host.start();
            try {
                assertTrue(host.waitForReady(600_000), "バニラが600秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, internalProtocol);
                assertEquals(internalProtocol, proxy.internalProtocol());
                proxy.start();
                try {
                    LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                            0, "127.0.0.1", proxyPort, "DetergentMC test", 20);
                    try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
                        frontend.start();
                        LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                                "127.0.0.1", frontend.boundPort(), "Steve", legacyProtocol);
                        assertEquals(0x01, login.loginResponse().id(),
                                "ブリッジのログイン応答（Join Game変換）は 0x01 のはずです。");
                        LegacyConnection client = login.connection();

                        client.sendPacket(new LegacyPacket(0x03, List.of(chatFirst)));
                        assertTrue(joined.await(30, TimeUnit.SECONDS),
                                "バニラログに Steve の参加が現れませんでした。");
                        assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                                "バニラログにチャットが現れませんでした。");

                        LegacyPacket keepAlive = readUntil(client, 0x00, 120_000);
                        client.sendPacket(new LegacyPacket(0x00,
                                List.of(keepAlive.intField(0))));
                        client.sendPacket(new LegacyPacket(0x03, List.of(chatSecond)));
                        assertTrue(chatAfterKeepAlive.await(30, TimeUnit.SECONDS),
                                "Keep Alive 応答後のチャットがバニラへ届きませんでした。");
                        client.close();
                    }
                } finally {
                    proxy.stop();
                }
            } finally {
                host.stop();
            }
        }
    }

    // ------------------------------------------------------------------
    // Direct mode: legacy client -> frontend -> real legacy vanilla
    // ------------------------------------------------------------------

    @Test
    void legacy78ClientJoinsRealVanilla164ThroughFrontendDirect() throws Exception {
        directCell("1.6.4", LegacyProtocol.V1_6_4, "78");
    }

    @Test
    void legacy74ClientJoinsRealVanilla162ThroughFrontendDirect() throws Exception {
        directCell("1.6.2", LegacyProtocol.V1_6_2, "74");
    }

    @Test
    void legacy73ClientJoinsRealVanilla161ThroughFrontendDirect() throws Exception {
        directCell("1.6.1", LegacyProtocol.V1_6_1, "73");
    }

    @Test
    void legacy61ClientJoinsRealVanilla152ThroughFrontendDirect() throws Exception {
        directCell("1.5.2", LegacyProtocol.V1_5_2, "61");
    }

    @Test
    void legacy60ClientJoinsRealVanilla151ThroughFrontendDirect() throws Exception {
        directCell("1.5.1", LegacyProtocol.V1_5_1, "60");
    }

    @Test
    void legacy60ClientJoinsRealVanilla15ThroughFrontendDirect() throws Exception {
        // 60 は 1.5 も受ける（61⇔60 差分ゼロの実測に基づく）。
        directCell("1.5", LegacyProtocol.V1_5_1, "60");
    }

    @Test
    void legacy51ClientJoinsRealVanilla147ThroughFrontendDirect() throws Exception {
        directCell("1.4.7", LegacyProtocol.V1_4_7, "51");
    }

    @Test
    void legacy49ClientJoinsRealVanilla145ThroughFrontendDirect() throws Exception {
        directCell("1.4.5", LegacyProtocol.V1_4_5, "49");
    }

    @Test
    void legacy47OldClientJoinsRealVanilla142ThroughFrontendDirect() throws Exception {
        // 旧47（1.4.2、Netty 47 とは別物）。このセルは 39 の作業で
        // 判明した DataWatcher スロットの plain 形式（NBT 無し）の
        // 移植後初の実バニラ経路でもある：実 1.4.2 がログイン直後に
        // 送るチャンク・スポーン系パケット列をフロントエンドが
        // 最後まで解析し続けられることが、参加・チャット・Keep
        // Alive 応答後のチャット到達で裏付けられる。ただし参加直後の
        // バニラ送信にスロット入りメタデータが含まれるかはバニラの
        // スポーン事情次第で、このセル単体では「スロット項目の
        // 解析そのもの」を直接は観測していない（単体テスト
        // watcherSlot47OldCarriesNoNbt がバイト列で固定している）。
        directCell("1.4.2", LegacyProtocol.V1_4_2, "47old");
    }

    @Test
    void legacy39ClientJoinsRealVanilla132ThroughFrontendDirect() throws Exception {
        // 39（1.3.2）。暗号化ログイン（RSA＋AES/CFB8、offline でも
        // 必須）込み。1.3 世代のログは「joined the game」行が無く
        // 「logged in」行のみで、VanillaLogParser の 51 Stage E 修正
        // （logged in 行を参加として扱う）がこの世代でも効くことが
        // 参加検出の前提になる。
        directCell("1.3.2", LegacyProtocol.V1_3_2, "39");
    }

    @Test
    void legacy39ClientJoinsRealVanilla131ThroughFrontendDirect() throws Exception {
        // 1.3.1⇔1.3.2 は登録・スキーマ・レジストリすべて差分ゼロ
        // （A〜D で両 jar の機械抽出と比較済み）。同じ 39 の表で
        // 実バニラ 1.3.1 へも通ることを実地で裏付ける。
        directCell("1.3.1", LegacyProtocol.V1_3_2, "39");
    }

    @Test
    void legacy29ClientJoinsRealVanilla125ThroughFrontendDirect() throws Exception {
        // 29（1.2.5）。暗号化境界より下で、クライアント面・
        // バックエンド面とも平文ログイン。チャンクはバルクが無く
        // pre_chunk（0x32）で予告してから単体 chunk_data（0x33）で
        // 届く方式：ログイン直後の 0x32 群と 0x33 群をフロント
        // エンド越しに読み切り、Keep Alive 応答後のチャットまで
        // 通ることが、チャンク到達と無切断の裏付けになる。
        // 1.2 世代のログも「logged in」行のみで、VanillaLogParser
        // の logged-in 対応が参加検出の前提になる。
        directCell("1.2.5", LegacyProtocol.V1_2_5, "29");
    }

    @Test
    void legacy29ClientJoinsRealVanilla124ThroughFrontendDirect() throws Exception {
        // 1.2.4⇔1.2.5 は登録・スキーマ・レジストリすべて差分ゼロ
        // （両 jar の機械抽出と比較済みで、ログイン探査でも
        // 1.2.4＝29 を実測）。同じ 29 の表で実バニラ 1.2.4 へも
        // 通ることを実地で裏付ける。1.2.4 の jar は piston-meta
        // に無いため、Omniarchive 経由で入手し公式 .exe と
        // バイト一致を確認したものを run/vanilla へ手動配置する
        // （SHA-1 は docs/PROXY-1.2.5.md）。
        directCell("1.2.4", LegacyProtocol.V1_2_5, "29");
    }

    @Test
    void legacy28ClientJoinsRealVanilla123ThroughFrontendDirect() throws Exception {
        // 28（1.2.3）。平文ログイン。1.2.3 の jar は piston-meta
        // に無いため、Omniarchive 経由で入手し公式 .exe と
        // バイト一致を確認したものを run/vanilla へ手動配置する
        // （SHA-1 は docs/PROXY-1.2.3.md）。1.2 世代のログは
        // 「logged in」行のみで、名前とアドレスの間に空白がある
        // 形——VanillaLogParser の 29 Stage E 修正が参加検出の
        // 前提になる。
        directCell("1.2.3", LegacyProtocol.V1_2_3, "28");
    }

    @Test
    void legacy23ClientJoinsRealVanilla11ThroughFrontendDirect() throws Exception {
        // 23（1.1）。平文ログイン（seed 入りの過渡形）。チャンクは
        // キューボイド形式：ログイン直後のチャンク群をフロント
        // エンド越しに読み切り、Keep Alive 応答後のチャットまで
        // 通ることが、チャンク到達と無切断の裏付けになる。
        // 1.1 の jar も手動配置（SHA-1 は docs/PROXY-1.1.md）。
        // ログ形式は 1.2 世代と同じ（旧接頭辞＋空白あり
        // 「logged in」行）ことを実バニラ 1.1 の server.log で
        // 実測確認済み。
        directCell("1.1", LegacyProtocol.V1_1, "23");
    }

    @Test
    void legacy22ClientJoinsRealVanilla100ThroughFrontendDirect() throws Exception {
        // 22（1.0.0）。平文ログイン（levelType 無し）。チャンクは
        // 23 と同じキューボイド形式。対応範囲の最古サーバーへの
        // ダイレクト経路を通す。1.0.0 の jar も手動配置
        // （SHA-1 は docs/PROXY-1.0.md）。起動完了行は
        // 「Done (…ns)!」の形で、VanillaLogParser の ServerReady
        // 判定（"Done (" 始まり＋help 行）がそのまま効くことを
        // 実バニラ 1.0.0 の server.log で実測確認済み。
        directCell("1.0.0", LegacyProtocol.V1_0, "22");
    }

    private void directCell(String backendVersion, int legacyProtocol, String label)
            throws Exception {
        Path realJar = findRealVanillaJar(backendVersion);
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-" + backendVersion + ".jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-" + backendVersion + ".jar"));

        int vanillaPort = freePort();
        String chatFirst = "hello from " + label + " direct to " + backendVersion;
        String chatSecond = "still alive " + label + " direct after keep alive";
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);
        CountDownLatch chatAfterKeepAlive = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-" + backendVersion + ".jar"), backendVersion)) {
            VanillaServerHost host = new VanillaServerHost(vanillaDir, backendVersion, vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {
                    joined.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals(chatFirst)) {
                    chatSeen.countDown();
                }
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals(chatSecond)) {
                    chatAfterKeepAlive.countDown();
                }
            });
            host.start();
            try {
                // 1.6.x のログは旧形式（yyyy-MM-dd HH:mm:ss [INFO]）で、
                // VanillaLogParser の旧形式対応が無いと Ready が立たない。
                assertTrue(host.waitForReady(600_000),
                        "バニラ" + backendVersion + "が600秒以内に起動しませんでした。");
                LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                        0, "127.0.0.1", vanillaPort, "DetergentMC test", 20, backendVersion);
                try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
                    frontend.start();
                    // フロントエンドのバックエンド側も、実バニラに対して
                    // RSA＋AES/CFB8 の暗号化ログインを行う（offline でも必須）。
                    LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                            "127.0.0.1", frontend.boundPort(), "Steve", legacyProtocol);
                    assertEquals(0x01, login.loginResponse().id(),
                            "ダイレクトのログイン応答は 0x01 のはずです。");
                    LegacyConnection client = login.connection();

                    client.sendPacket(new LegacyPacket(0x03, List.of(chatFirst)));
                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラ" + backendVersion + "のログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラ" + backendVersion + "のログにチャットが現れませんでした。");

                    LegacyPacket keepAlive = readUntil(client, 0x00, 120_000);
                    client.sendPacket(new LegacyPacket(0x00,
                            List.of(keepAlive.intField(0))));
                    client.sendPacket(new LegacyPacket(0x03, List.of(chatSecond)));
                    assertTrue(chatAfterKeepAlive.await(30, TimeUnit.SECONDS),
                            "Keep Alive 応答後のチャットがバニラ" + backendVersion + "へ届きませんでした。");
                    client.close();
                }
            } finally {
                host.stop();
            }
        }
    }

    // ------------------------------------------------------------------
    // Legacy ping (section 1) against the real frontend
    // ------------------------------------------------------------------

    @Test
    void legacyPingAnsweredInSection1Form() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            // 本物の1.6.4クライアントは常に完全形（FE 01 の後に
            // MC|PingHost プラグインメッセージ）を送り、§1 応答を受ける。
            // 73 の Stage B で「§1 は完全形にだけ返す」癖に合わせて以降、
            // このセルも完全形で送る（78 の Stage E 当時は素の FE 01 で
            // §1 が返っていた。実クライアントの挙動としては完全形が正）。
            String payload = pingPayload(frontend.boundPort(), true, 78);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("78", fields[1], "§1応答のプロトコル番号は78のはずです。");
            assertEquals("1.6.4", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacyPingBareFormAnsweredInOldForm() throws Exception {
        // 素の FE 01 には旧形式で返す（最古対応世代＝実バニラ1.6.1の
        // 挙動に合わせた設計。PROXY-1.6.1.md 参照）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false, 78);
            // 現在人数はピングセッション自身を含む数え方で 1 になる
            // （レガシーモジュールの E2E テストと同じ観測値）。
            assertEquals("Ping MOTD§1§20", payload,
                    "素の FE 01 には旧形式（MOTD§現在人数§最大人数）が返るはずです。");
        }
    }

    @Test
    void legacy74PingAnsweredInSection1Form() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.6.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 74);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("74", fields[1], "§1応答のプロトコル番号は74のはずです。");
            assertEquals("1.6.2", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy73PingFullFormAnsweredInSection1Form() throws Exception {
        // 73 の癖その1：完全な PingHost 形式には §1 を返す。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.6.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 73);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("73", fields[1], "§1応答のプロトコル番号は73のはずです。");
            assertEquals("1.6.1", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy61PingAnsweredInSection1Form() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.5.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 61);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("61", fields[1], "§1応答のプロトコル番号は61のはずです。");
            assertEquals("1.5.2", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy60PingAnsweredInSection1Form() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.5.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 60);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("60", fields[1], "§1応答のプロトコル番号は60のはずです。");
            assertEquals("1.5.1", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy51PingAnsweredInSection1Form() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.4.7");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 51);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("51", fields[1], "§1応答のプロトコル番号は51のはずです。");
            assertEquals("1.4.7", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy49PingAnsweredInSection1Form() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.4.5");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 49);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("49", fields[1], "§1応答のプロトコル番号は49のはずです。");
            assertEquals("1.4.5", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy47OldPingAnsweredInSection1Form() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.4.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 47);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("47", fields[1], "§1応答のプロトコル番号は旧47のはずです。");
            assertEquals("1.4.2", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy39PingAnsweredInSection1Form() throws Exception {
        // 完全形には §1 で返す。実バニラ 1.3.2 は完全形に接続
        // リセットで応えるが、フロントエンドは従来どおり「完全形が
        // 来れば §1、来なければ旧形式」の互換動作を保つ
        // （docs/PROXY-1.3.2.md）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.3.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 39);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("39", fields[1], "§1応答のプロトコル番号は39のはずです。");
            assertEquals("1.3.2", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy39PingBareFormAnsweredInOldForm() throws Exception {
        // 39 の文書化された挙動：素の FE 01 には旧形式で返す
        // （実バニラ 1.3.2 と同じ応答形。docs/PROXY-1.3.2.md）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.3.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false, 39);
            assertEquals("Ping MOTD§1§20", payload,
                    "素の FE 01 には旧形式（MOTD§現在人数§最大人数）が返るはずです。");
        }
    }

    @Test
    void legacy29PingAnsweredInSection1Form() throws Exception {
        // 完全形には §1 で返す（フロントエンドの互換動作。
        // 実バニラ 1.2.5 自身のピングは旧形式のみで、§1 形式は
        // 旧47 から — docs/PROXY-1.2.5.md）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.2.5");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 29);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("29", fields[1], "§1応答のプロトコル番号は29のはずです。");
            assertEquals("1.2.5", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy29PingBareFormAnsweredInOldForm() throws Exception {
        // 29 の文書化された挙動：素の FE 01 には旧形式で返す
        // （実バニラ 1.2.5 と同じ応答形。この世代のピングは
        // 旧形式のみ。docs/PROXY-1.2.5.md）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.2.5");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false, 29);
            assertEquals("Ping MOTD§1§20", payload,
                    "素の FE 01 には旧形式（MOTD§現在人数§最大人数）が返るはずです。");
        }
    }

    @Test
    void legacy28PingAnsweredInSection1Form() throws Exception {
        // 完全形には §1 で返す（フロントエンドの互換動作。
        // 実バニラ 1.2.3 自身のピングは旧形式のみ）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.2.3");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 28);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("28", fields[1], "§1応答のプロトコル番号は28のはずです。");
            assertEquals("1.2.3", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy28PingBareFormAnsweredInOldForm() throws Exception {
        // 28 の文書化された挙動：素の FE 01 には旧形式で返す
        // （実バニラ 1.2.3 と同じ応答形。この世代のピングは
        // 旧形式のみ。docs/PROXY-1.2.3.md）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.2.3");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false, 28);
            assertEquals("Ping MOTD§1§20", payload,
                    "素の FE 01 には旧形式（MOTD§現在人数§最大人数）が返るはずです。");
        }
    }

    @Test
    void legacy23PingAnsweredInSection1Form() throws Exception {
        // 完全形には §1 で返す（フロントエンドの互換動作。
        // 実バニラ 1.1 自身のピングは旧形式のみ）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 23);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("23", fields[1], "§1応答のプロトコル番号は23のはずです。");
            assertEquals("1.1", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy23PingBareFormAnsweredInOldForm() throws Exception {
        // 23 の文書化された挙動：素の FE 01 には旧形式で返す
        // （実バニラ 1.1 と同じ応答形。docs/PROXY-1.1.md）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false, 23);
            assertEquals("Ping MOTD§1§20", payload,
                    "素の FE 01 には旧形式（MOTD§現在人数§最大人数）が返るはずです。");
        }
    }

    @Test
    void legacy22PingAnsweredInSection1Form() throws Exception {
        // 完全形には §1 で返す（フロントエンドの互換動作。
        // 実バニラ 1.0.0 自身のピングは旧形式のみ）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.0.0");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true, 22);
            String[] fields = payload.split("\0", -1);
            assertEquals(6, fields.length, "§1応答のフィールド数が違います: " + payload);
            assertEquals("§1", fields[0]);
            assertEquals("22", fields[1], "§1応答のプロトコル番号は22のはずです。");
            assertEquals("1.0.0", fields[2]);
            assertEquals("Ping MOTD", fields[3]);
            assertEquals("20", fields[5], "§1応答の最大人数が違います。");
        }
    }

    @Test
    void legacy22PingBareFormAnsweredInOldForm() throws Exception {
        // 22 の文書化された挙動：素の FE 01 には旧形式で返す
        // （実バニラ 1.0.0 と同じ応答形。docs/PROXY-1.0.md）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.0.0");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false, 22);
            assertEquals("Ping MOTD§1§20", payload,
                    "素の FE 01 には旧形式（MOTD§現在人数§最大人数）が返るはずです。");
        }
    }

    @Test
    void legacy73PingBareFormAnsweredInOldForm() throws Exception {
        // 73 の癖その2：素の FE 01 には旧形式を返す（実バニラ1.6.1と
        // 同じ挙動。1.6.1の実クライアントは常に完全形を送るので、
        // 実害はない）。
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", freePort(), "Ping MOTD", 20, "1.6.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false, 73);
            assertEquals("Ping MOTD§1§20", payload,
                    "素の FE 01 には旧形式（MOTD§現在人数§最大人数）が返るはずです。");
        }
    }

    /**
     * レガシーピングを送って応答文字列を読む。完全形は本物の1.6.x
     * クライアントと同じく FE 01 の後に MC|PingHost プラグイン
     * メッセージ（プロトコル番号・ホスト名・ポート）を続ける。
     */
    private static String pingPayload(int port, boolean fullForm, int pingProtocol)
            throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(5_000);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream d = new java.io.DataOutputStream(bos);
            d.writeByte(0xFE);
            d.writeByte(0x01);
            if (fullForm) {
                d.writeByte(0xFA);
                writeLegacyString(d, "MC|PingHost");
                java.io.ByteArrayOutputStream pb = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream pd = new java.io.DataOutputStream(pb);
                pd.writeByte(pingProtocol);
                writeLegacyString(pd, "127.0.0.1");
                pd.writeInt(port);
                pd.flush();
                d.writeShort(pb.size());
                d.write(pb.toByteArray());
            }
            d.flush();
            socket.getOutputStream().write(bos.toByteArray());
            socket.getOutputStream().flush();
            byte[] response = socket.getInputStream().readAllBytes();
            assertTrue(response.length > 2, "ピング応答が空です。");
            assertEquals(0xFF, response[0] & 0xFF, "ピング応答は 0xFF キックのはずです。");
            LegacyWire.Reader reader = new LegacyWire.Reader(
                    Arrays.copyOfRange(response, 1, response.length));
            return reader.string();
        }
    }

    private static void writeLegacyString(java.io.DataOutputStream d, String s)
            throws IOException {
        d.writeShort(s.length());
        d.writeChars(s);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** 指定IDのパケットが来るまで読み続ける（他は読み捨てる）。 */
    private static LegacyPacket readUntil(LegacyConnection connection, int id,
            long timeoutMillis) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            LegacyPacket packet;
            try {
                packet = connection.readPacket();
            } catch (EOFException e) {
                fail("Keep Alive を待つ間に接続が切れました: " + e.getMessage());
                return null;
            }
            if (packet == null) {
                continue;
            }
            if (packet.id() == 0xFF) {
                fail("Keep Alive を待つ間にキックされました: " + packet.field(0));
            }
            if (packet.id() == id) {
                return packet;
            }
        }
        fail("レガシーパケット 0x" + Integer.toHexString(id) + " が "
                + timeoutMillis + "ms 以内に届きませんでした。");
        return null;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** 本物のバニラjarを探す。無ければ呼び出し側がスキップする。 */
    private static Path findRealVanillaJar(String version) {
        String name = "server-" + version + ".jar";
        for (String candidate : List.of(
                "run/vanilla/" + name,
                "../run/vanilla/" + name,
                "../../run/vanilla/" + name)) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /**
     * 偽Mojang：ローカルHTTPでバージョン清单を返し、既存jarのSHA-1を
     * 広告するだけの最小実装（{@link ProxyIntegrationTest} の同名クラスと
     * 同じ形。あちらは private のため複製している）。
     */
    private static final class FakeMojang implements AutoCloseable {
        final HttpServer server;
        final Path jarPath;
        final String version;

        FakeMojang(Path jarPath, String version) throws Exception {
            this.jarPath = jarPath;
            this.version = version;
            String jarSha1 = VanillaDownloader.sha1(jarPath);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;
            server.createContext("/manifest.json", exchange -> {
                byte[] body = ("{\"versions\":[{\"id\": \"" + version + "\", \"type\": \"release\", "
                        + "\"url\": \"" + base + "/version.json\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                send(exchange, body, "application/json");
            });
            server.createContext("/version.json", exchange -> {
                byte[] body = ("{\"downloads\": {\"server\": {\"sha1\": \"" + jarSha1
                        + "\", \"size\": " + Files.size(jarPath)
                        + ", \"url\": \"" + base + "/server.jar\"}}}")
                        .getBytes(StandardCharsets.UTF_8);
                send(exchange, body, "application/json");
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

        private static void send(com.sun.net.httpserver.HttpExchange exchange, byte[] body,
                String contentType) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
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
}
