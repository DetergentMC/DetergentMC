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
    void javaBinaryFor1152PrefersConfiguredJava8Home() {
        String original = System.getProperty("detergentmc.java8.home");
        System.setProperty("detergentmc.java8.home", "/opt/example-jdk-8");
        try {
            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.15.2"));
            // 1.15 も同じ Java 8 世代。
            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.15"));
            // 1.15.1 も同じ Java 8 世代。
            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.15.1"));
            // 1.14.4 も同じ Java 8 世代。
            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.14.4"));
            // 1.14.3 も同じ Java 8 世代。
            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.14.3"));
            // 1.14.2 も同じ Java 8 世代。
            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.14.2"));
            // 1.14.1 も同じ Java 8 世代。
            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.14.1"));
            // 1.20.4 には Java 8 の指定は使わない。
            assertEquals(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.20.4"));
        } finally {
            if (original == null) {
                System.clearProperty("detergentmc.java8.home");
            } else {
                System.setProperty("detergentmc.java8.home", original);
            }
        }
    }

    @Test
    void javaBinaryFor1204UsesCurrentJavaHome() {
        String expected = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.20.4"));
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.20.1"));
        assertEquals(expected, VanillaServerHost.javaBinaryFor("1.20.2"));
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
            // 26.2 も Java 25 が必要（26.1 と同じ）。
            assertEquals(Path.of("/opt/example-jdk-25", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("26.2"));
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
