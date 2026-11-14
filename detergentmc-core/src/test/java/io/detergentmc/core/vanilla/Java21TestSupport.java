package io.detergentmc.core.vanilla;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 統合テスト用の Java 21 検出ヘルパー。{@link VanillaServerHost#javaBinaryFor}
 * が見る {@code detergentmc.java21.home} システムプロパティを、見つかった
 * Java 21 の場所で設定する。
 *
 * <p>探す順序は {@code BedrockMatrixIntegrationTest} と同じ：システム
 * プロパティ→環境変数 {@code DETERGENTMC_JAVA21_HOME}→作業ディレクトリ
 * 相対（テストの作業ディレクトリは detergentmc-core なので
 * ../../.tools/jdk-21.0.13+9 が ~/workspace/.tools/jdk-21.0.13+9
 * になる）→ user.home 相対。テスト用 JVM の user.home は /root になる
 * ことがあるため、user.home だけには頼らない。
 *
 * <p>これが無いと、バニラ1.20.5／1.20.6／1.21／1.21.1（Java 21 必須）の
 * 統合テストは Gradle を Java 17 で動かしている環境で
 * UnsupportedClassVersionError になって起動しない（2026-11-10に確認）。
 */
final class Java21TestSupport {

    private Java21TestSupport() {
    }

    /**
     * Java 21 の home を見つけてシステムプロパティに設定する。
     * すでにプロパティが設定済みの場合や、見つからない場合は何もしない。
     */
    static void ensureJava21Home() {
        if (System.getProperty("detergentmc.java21.home") != null
                && !System.getProperty("detergentmc.java21.home").isBlank()) {
            return;
        }
        String home = findJava21Home();
        if (home != null) {
            System.setProperty("detergentmc.java21.home", home);
        }
    }

    /** Java 21 の home を探す。見つからなければ {@code null}。 */
    static String findJava21Home() {
        String fromProperty = System.getProperty("detergentmc.java21.home");
        if (fromProperty != null && !fromProperty.isBlank()
                && isJavaExecutable(Path.of(fromProperty.strip()))) {
            return fromProperty.strip();
        }
        String fromEnv = System.getenv("DETERGENTMC_JAVA21_HOME");
        if (fromEnv != null && !fromEnv.isBlank()
                && isJavaExecutable(Path.of(fromEnv.strip()))) {
            return fromEnv.strip();
        }
        for (String relative : List.of(
                "../../.tools/jdk-21.0.13+9",
                "../.tools/jdk-21.0.13+9",
                ".tools/jdk-21.0.13+9")) {
            Path candidate = Path.of(relative).toAbsolutePath().normalize();
            if (isJavaExecutable(candidate)) {
                return candidate.toString();
            }
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Path candidate = Path.of(userHome, "workspace", ".tools", "jdk-21.0.13+9");
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
