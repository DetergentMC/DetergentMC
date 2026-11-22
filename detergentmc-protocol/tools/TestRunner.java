package io.detergentmc.protocol;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 最小の JUnit 5 実行器（リフレクションで @Test を呼ぶ）。
 *
 * <p>Gradle の compileTestJava が他担当の壊れたテストファイル
 * （ClientProxy775Test／ClientProxy776Test／ResourcePackServer759Test）で
 * 止まるため、対象クラスのみを javac でコンパイルしてこのランナーで実行する。
 * @BeforeEach／@AfterEach があれば呼ぶ。@TempDir やパラメータ化テストには
 * 未対応（対象クラスは使っていない）。
 * クラスごとに JUnit XML を出力ディレクトリに書く。
 */
public class TestRunner {

    private static final Class<? extends Annotation> TEST =
            org.junit.jupiter.api.Test.class.asSubclass(Annotation.class);
    private static final Class<? extends Annotation> BEFORE_EACH =
            org.junit.jupiter.api.BeforeEach.class.asSubclass(Annotation.class);
    private static final Class<? extends Annotation> AFTER_EACH =
            org.junit.jupiter.api.AfterEach.class.asSubclass(Annotation.class);

    private record Result(String name, String status, double seconds, String message, String trace) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("使い方: TestRunner <出力ディレクトリ> <クラス名...>");
            System.exit(2);
        }
        Path outDir = Path.of(args[0]);
        Files.createDirectories(outDir);
        int totalTests = 0;
        int totalFailures = 0;
        int totalErrors = 0;
        int totalSkipped = 0;
        for (int a = 1; a < args.length; a++) {
            String className = args[a];
            Class<?> clazz = Class.forName(className);
            List<Method> tests = new ArrayList<>();
            List<Method> beforeEach = new ArrayList<>();
            List<Method> afterEach = new ArrayList<>();
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isAnnotationPresent(TEST)) {
                    tests.add(m);
                } else if (m.isAnnotationPresent(BEFORE_EACH)) {
                    beforeEach.add(m);
                } else if (m.isAnnotationPresent(AFTER_EACH)) {
                    afterEach.add(m);
                }
            }
            tests.sort(Comparator.comparing(Method::getName));
            List<Result> results = new ArrayList<>();
            for (Method test : tests) {
                long start = System.nanoTime();
                Object instance = clazz.getDeclaredConstructor().newInstance();
                String status = "passed";
                String message = null;
                String trace = null;
                try {
                    for (Method m : beforeEach) {
                        m.setAccessible(true);
                        m.invoke(instance);
                    }
                    test.setAccessible(true);
                    test.invoke(instance);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof org.opentest4j.TestAbortedException) {
                        status = "skipped";
                        message = String.valueOf(cause.getMessage());
                    } else if (cause instanceof AssertionError) {
                        status = "failure";
                        message = String.valueOf(cause.getMessage());
                        trace = stackTrace(cause);
                    } else {
                        status = "error";
                        message = String.valueOf(cause);
                        trace = stackTrace(cause);
                    }
                } catch (Throwable e) {
                    status = "error";
                    message = String.valueOf(e);
                    trace = stackTrace(e);
                } finally {
                    try {
                        for (Method m : afterEach) {
                            m.setAccessible(true);
                            m.invoke(instance);
                        }
                    } catch (Throwable ignored) {
                        // 後処理の失敗は無視する（本体の結果を優先）。
                    }
                }
                double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
                results.add(new Result(test.getName(), status, seconds, message, trace));
            }
            int failures = (int) results.stream().filter(r -> r.status().equals("failure")).count();
            int errors = (int) results.stream().filter(r -> r.status().equals("error")).count();
            int skipped = (int) results.stream().filter(r -> r.status().equals("skipped")).count();
            double time = results.stream().mapToDouble(Result::seconds).sum();
            totalTests += results.size();
            totalFailures += failures;
            totalErrors += errors;
            totalSkipped += skipped;
            writeXml(outDir, className, results, failures, errors, skipped, time);
            System.out.printf("%s: tests=%d failures=%d errors=%d skipped=%d time=%.3fs%n",
                    className, results.size(), failures, errors, skipped, time);
            for (Result r : results) {
                if (!r.status().equals("passed")) {
                    System.out.printf("  %s %s: %s%n", r.status().toUpperCase(), r.name(),
                            r.message() == null ? "" : r.message());
                }
            }
        }
        System.out.printf("TOTAL: tests=%d failures=%d errors=%d skipped=%d%n",
                totalTests, totalFailures, totalErrors, totalSkipped);
        System.exit(totalFailures + totalErrors > 0 ? 1 : 0);
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void writeXml(Path outDir, String className, List<Result> results,
                                 int failures, int errors, int skipped, double time) throws Exception {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(
                "<testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" errors=\"%d\" skipped=\"%d\" time=\"%.3f\">%n",
                escape(className), results.size(), failures, errors, skipped, time));
        for (Result r : results) {
            sb.append(String.format(
                    "  <testcase classname=\"%s\" name=\"%s\" time=\"%.3f\">%n",
                    escape(className), escape(r.name()), r.seconds()));
            if (r.status().equals("failure")) {
                sb.append(String.format("    <failure message=\"%s\">%s</failure>%n",
                        escape(r.message()), escape(r.trace())));
            } else if (r.status().equals("error")) {
                sb.append(String.format("    <error message=\"%s\">%s</error>%n",
                        escape(r.message()), escape(r.trace())));
            } else if (r.status().equals("skipped")) {
                sb.append(String.format("    <skipped message=\"%s\"/>%n", escape(r.message())));
            }
            sb.append("  </testcase>\n");
        }
        sb.append("</testsuite>\n");
        Files.writeString(outDir.resolve("TEST-" + simple + ".xml"), sb.toString(),
                StandardCharsets.UTF_8);
    }
}
