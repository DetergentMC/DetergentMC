package io.detergentmc.core.vanilla;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 統合テスト用の Java 8 検出ヘルパー。{@link VanillaServerHost#javaBinaryFor}
 * が見る {@code detergentmc.java8.home} システムプロパティを、見つかった
 * Java 8 の場所で設定する。
 *
 * <p>探す順序は {@link Java25TestSupport} と同じ：システム
 * プロパティ→環境変数 {@code DETERGENTMC_JAVA8_HOME}→作業ディレクトリ
 * 相対（テストの作業ディレクトリは detergentmc-core なので
 * ../../.tools/jdk-8 が ~/workspace/.tools/jdk-8 になる）→
 * user.home 相対。
 *
 * <p>これが無いと、バニラ1.15.2（Java 8 必須）の統合テストは
 * Gradle を Java 17 で動かしている環境で起動しない。
 */
final class Java8TestSupport {

    private Java8TestSupport() {
    }

    /**
     * Java 8 の home を見つけてシステムプロパティに設定する。
     * すでにプロパティが設定済みの場合や、見つからない場合は何もしない。
     */
    static void ensureJava8Home() {
        if (System.getProperty("detergentmc.java8.home") != null
                && !System.getProperty("detergentmc.java8.home").isBlank()) {
            return;
        }
        String home = findJava8Home();
        if (home != null) {
            System.setProperty("detergentmc.java8.home", home);
        }
    }

    /** Java 8 の home を探す。見つからなければ {@code null}。 */
    static String findJava8Home() {
        String fromProperty = System.getProperty("detergentmc.java8.home");
        if (fromProperty != null && !fromProperty.isBlank()
                && isJavaExecutable(Path.of(fromProperty.strip()))) {
            return fromProperty.strip();
        }
        String fromEnv = System.getenv("DETERGENTMC_JAVA8_HOME");
        if (fromEnv != null && !fromEnv.isBlank()
                && isJavaExecutable(Path.of(fromEnv.strip()))) {
            return fromEnv.strip();
        }
        for (String relative : List.of(
                "../../.tools/jdk-8",
                "../.tools/jdk-8",
                ".tools/jdk-8")) {
            Path candidate = Path.of(relative).toAbsolutePath().normalize();
            if (isJavaExecutable(candidate)) {
                return candidate.toString();
            }
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Path candidate = Path.of(userHome, "workspace", ".tools", "jdk-8");
            if (isJavaExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static boolean isJavaExecutable(Path javaHome) {
        return Files.isExecutable(javaHome.resolve("bin").resolve("java"));
    }
}
