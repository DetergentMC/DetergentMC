package io.detergentmc.core.vanilla;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 統合テスト用の Java 25 検出ヘルパー。{@link VanillaServerHost#javaBinaryFor}
 * が見る {@code detergentmc.java25.home} システムプロパティを、見つかった
 * Java 25 の場所で設定する。
 *
 * <p>探す順序は {@link Java21TestSupport} と同じ：システム
 * プロパティ→環境変数 {@code DETERGENTMC_JAVA25_HOME}→作業ディレクトリ
 * 相対（テストの作業ディレクトリは detergentmc-core なので
 * ../../.tools/jdk-25.0.5+8 が ~/workspace/.tools/jdk-25.0.5+8
 * になる）→ user.home 相対。テスト用 JVM の user.home は /root になる
 * ことがあるため、user.home だけには頼らない。
 *
 * <p>これが無いと、バニラ26.1／26.1.1／26.1.2（Java 25 必須）の
 * 統合テストは Gradle を Java 17 で動かしている環境で
 * UnsupportedClassVersionError になって起動しない。
 */
final class Java25TestSupport {

    private Java25TestSupport() {
    }

    /**
     * Java 25 の home を見つけてシステムプロパティに設定する。
     * すでにプロパティが設定済みの場合や、見つからない場合は何もしない。
     */
    static void ensureJava25Home() {
        if (System.getProperty("detergentmc.java25.home") != null
                && !System.getProperty("detergentmc.java25.home").isBlank()) {
            return;
        }
        String home = findJava25Home();
        if (home != null) {
            System.setProperty("detergentmc.java25.home", home);
        }
    }

    /** Java 25 の home を探す。見つからなければ {@code null}。 */
    static String findJava25Home() {
        String fromProperty = System.getProperty("detergentmc.java25.home");
        if (fromProperty != null && !fromProperty.isBlank()
                && isJavaExecutable(Path.of(fromProperty.strip()))) {
            return fromProperty.strip();
        }
        String fromEnv = System.getenv("DETERGENTMC_JAVA25_HOME");
        if (fromEnv != null && !fromEnv.isBlank()
                && isJavaExecutable(Path.of(fromEnv.strip()))) {
            return fromEnv.strip();
        }
        for (String relative : List.of(
                "../../.tools/jdk-25.0.5+8",
                "../.tools/jdk-25.0.5+8",
                ".tools/jdk-25.0.5+8")) {
            Path candidate = Path.of(relative).toAbsolutePath().normalize();
            if (isJavaExecutable(candidate)) {
                return candidate.toString();
            }
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Path candidate = Path.of(userHome, "workspace", ".tools", "jdk-25.0.5+8");
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
