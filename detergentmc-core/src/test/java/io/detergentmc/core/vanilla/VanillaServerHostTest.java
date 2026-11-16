package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ResourcePack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VanillaServerHostTest {

    @TempDir
    Path tempDir;

    private static Properties readProperties(Path dir) throws Exception {
        Properties properties = new Properties();
        try (InputStreamReader in = new InputStreamReader(
                Files.newInputStream(dir.resolve("server.properties")), StandardCharsets.UTF_8)) {
            properties.load(in);
        }
        return properties;
    }

    @Test
    void internalVanillaIsAlwaysOfflineEvenWhenProxyAuthIsOn() throws Exception {
        // 【担当1】公開側 online-mode=true でも、内部バニラは常に
        // offline-mode=false（信頼済み接続）。Mojang認証はプロキシが行う。
        VanillaServerHost.writeServerProperties(tempDir, 25566, true, 10,
                "DetergentMC 1.20.4", null);
        Properties properties = readProperties(tempDir);
        assertEquals("false", properties.getProperty("online-mode"));
        assertEquals("127.0.0.1", properties.getProperty("server-ip"));
    }

    @Test
    void writesServerProperties() throws Exception {
        VanillaServerHost.writeServerProperties(tempDir, 25566, false, 10,
                "DetergentMC 1.20.4", null);
        Properties properties = readProperties(tempDir);
        assertEquals("25566", properties.getProperty("server-port"));
        assertEquals("false", properties.getProperty("online-mode"));
        assertEquals("10", properties.getProperty("max-players"));
        assertEquals("DetergentMC 1.20.4", properties.getProperty("motd"));
        assertEquals("0", properties.getProperty("spawn-protection"));
        assertEquals("false", properties.getProperty("enable-command-block"));
        assertFalse(properties.containsKey("resource-pack"));
    }

    @Test
    void writesLoopbackOnlyAndDisablesCompression() throws Exception {
        // プロキシ構成：バニラは127.0.0.1のみに束縛し、圧縮は無効化する。
        VanillaServerHost.writeServerProperties(tempDir, 25566, false, 10,
                "DetergentMC 1.20.4", null);
        Properties properties = readProperties(tempDir);
        assertEquals("127.0.0.1", properties.getProperty("server-ip"));
        assertEquals("-1", properties.getProperty("network-compression-threshold"));
    }

    @Test
    void writesResourcePackWhenPresent() throws Exception {
        ResourcePack pack = new ResourcePack(
                "https://example.invalid/pack.zip",
                "0123456789abcdef0123456789abcdef01234567", false, "");
        VanillaServerHost.writeServerProperties(tempDir, 25565, true, 20,
                "Test", pack);
        Properties properties = readProperties(tempDir);
        assertEquals("https://example.invalid/pack.zip",
                properties.getProperty("resource-pack"));
        assertEquals("0123456789abcdef0123456789abcdef01234567",
                properties.getProperty("resource-pack-sha1"));
    }

    @Test
    void removesStaleResourcePackKeysWhenPackIsDisabled() throws Exception {
        ResourcePack pack = new ResourcePack(
                "https://example.invalid/pack.zip",
                "0123456789abcdef0123456789abcdef01234567", false, "");
        VanillaServerHost.writeServerProperties(tempDir, 25565, true, 20,
                "Test", pack);
        assertTrue(readProperties(tempDir).containsKey("resource-pack"));
        VanillaServerHost.writeServerProperties(tempDir, 25565, true, 20,
                "Test", null);
        Properties properties = readProperties(tempDir);
        assertFalse(properties.containsKey("resource-pack"));
        assertFalse(properties.containsKey("resource-pack-sha1"));
    }

    @Test
    void overwritesManagedKeysButKeepsOtherKeys() throws Exception {
        Files.writeString(tempDir.resolve("server.properties"),
                "server-port=1234\ndifficulty=hard\nview-distance=12\n",
                StandardCharsets.UTF_8);
        VanillaServerHost.writeServerProperties(tempDir, 25565, true, 20,
                "DetergentMC 1.20.4", null);
        Properties properties = readProperties(tempDir);
        assertEquals("25565", properties.getProperty("server-port"));
        assertEquals("hard", properties.getProperty("difficulty"));
        assertEquals("12", properties.getProperty("view-distance"));
    }

    @Test
    void writesEulaTrue() throws Exception {
        VanillaServerHost.writeEula(tempDir);
        List<String> lines = Files.readAllLines(tempDir.resolve("eula.txt"));
        assertTrue(lines.contains("eula=true"));
    }

    @Test
    void sendCommandBeforeStartFails() {
        VanillaServerHost host = new VanillaServerHost(
                tempDir, "1.20.4", 25565, true, 20, "Test", null);
        assertFalse(host.isRunning());
        try {
            host.sendCommand("list");
            throw new AssertionError("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected: the vanilla server is not running.
        }
        // stop() on a host that never started is a no-op.
        host.stop();
    }

    @Test
    void javaBinaryFor1204UsesCurrentJavaHome() {
        String expected = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.20.4"));
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.20.1"));
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.20.2"));
        // 1.18／1.18.1 は Java 17 を要求し（version.json 実測）、DetergentMC 自身も
        // Java 17 で動くため、Java 25／Java 21 の分岐には入らず現在の Java を使う。
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.18"));
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.18.1"));
        // 1.17.1 は Java 16 を要求する（version.json 実測）。Java 16 専用の分岐は
        // 無く、DetergentMC 自身が動く Java 17（16以上）で 1.17.1 サーバーは
        // 起動できるため、Java 25／Java 21 の分岐には入らず現在の Java を使う。
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.17.1"));
        // 1.17 も Java 16 を要求する（version.json 実測。1.17.1 と同じ）。
        // Java 16 専用の分岐は無く、現在の Java を使う（1.17.1 と同じ判断）。
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.17"));
        // 1.16.5／1.16.4 は Java 8 を要求する（piston-meta majorVersion 8 実測）。
        // javaBinaryFor には Java 8 用の分岐は無い（Java 21／25 の最低
        // バージョン分岐しかない）。Java 8 向けバイトコードは新しい JVM
        // でも動くため、現在の Java を使う。実起動の確認は Stage D で行う。
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.16.5"));
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.16.4"));
        // 1.16.3 も Java 8 を要求する（piston-meta majorVersion 8 実測）。
        // javaBinaryFor には Java 8 用の分岐は無い（Java 21／25 の最低
        // バージョン分岐しかない）。Java 8 向けバイトコードは新しい JVM
        // でも動くため、現在の Java を使う。実起動の確認は Stage D で行う。
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.16.3"));
    }

    @Test
    void javaBinaryFor261PrefersConfiguredJava25Home() {
        String original = System.getProperty("detergentmc.java25.home");
        System.setProperty("detergentmc.java25.home", "/opt/example-jdk-25");
        try {
            assertEquals(Path.of("/opt/example-jdk-25", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("26.1"));
            assertEquals(Path.of("/opt/example-jdk-25", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("26.1.1"));
            assertEquals(Path.of("/opt/example-jdk-25", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("26.1.2"));
            // 1.20.4 には Java 25 の指定は使わない。
            assertEquals(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.20.4"));
        } finally {
            if (original == null) {
                System.clearProperty("detergentmc.java25.home");
            } else {
                System.setProperty("detergentmc.java25.home", original);
            }
        }
    }

    @Test
    void javaBinaryFor261FallsBackToCurrentJavaWhenNoJava25Configured() {
        // 環境変数 DETERGENTMC_JAVA25_HOME が設定済みの環境では、
        // フォールバックの期待値が変わるためその場合だけスキップする。
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getenv("DETERGENTMC_JAVA25_HOME") == null
                        || System.getenv("DETERGENTMC_JAVA25_HOME").isBlank(),
                "DETERGENTMC_JAVA25_HOME が設定済みのためスキップします。");
        String original = System.getProperty("detergentmc.java25.home");
        System.clearProperty("detergentmc.java25.home");
        try {
            // テスト用 JVM は Java 17（25未満）のため、警告を出して現在の Java へ
            // フォールバックする。
            assertEquals(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("26.1"));
        } finally {
            if (original == null) {
                System.clearProperty("detergentmc.java25.home");
            } else {
                System.setProperty("detergentmc.java25.home", original);
            }
        }
    }
}
