package io.detergentmc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point: {@code java -jar detergentmc-core.jar [--workdir <dir>]}.
 *
 * <p>On first start in a working directory, DetergentMC writes a default
 * {@code detergentmc.yml}, language files, and an {@code eula.txt}.
 * Like the vanilla server, it will NOT start until the operator has read
 * the Minecraft EULA and set {@code eula=true} in {@code eula.txt}.
 */
public final class Main {

    private static final String EULA_URL = "https://www.minecraft.net/en-us/eula";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Path workdir = Path.of("run");
        boolean showVersion = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                case "--version", "-v" -> showVersion = true;
                case "--workdir" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("--workdir needs a directory");
                        System.exit(2);
                    }
                    workdir = Path.of(args[++i]);
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    printHelp();
                    System.exit(2);
                }
            }
        }
        if (showVersion) {
            System.out.println("DetergentMC " + CoreServer.DETERGENT_VERSION + " (Minecraft 1.20.4, MVP skeleton)");
            return;
        }

        Files.createDirectories(workdir);
        Path eulaFile = workdir.resolve("eula.txt");
        if (!Files.exists(eulaFile)) {
            Files.writeString(eulaFile, """
                    # By changing the setting below to TRUE you are indicating your agreement to
                    # the Minecraft EULA (https://www.minecraft.net/en-us/eula).
                    # eula.txt を TRUE に変更することで、Minecraft EULAへの同意を示します。
                    eula=false
                    """);
            System.out.println("Created eula.txt. Please read the Minecraft EULA (" + EULA_URL + ")");
            System.out.println("and set eula=true in eula.txt, then start DetergentMC again.");
            System.out.println("eula.txt を作成しました。Minecraft EULAを読んで eula=true にしてから、もう一度起動してください。");
            return;
        }
        List<String> eulaLines = Files.readAllLines(eulaFile);
        boolean accepted = eulaLines.stream()
                .map(String::trim)
                .anyMatch(line -> line.equals("eula=true"));
        if (!accepted) {
            System.out.println("You must agree to the Minecraft EULA (" + EULA_URL + ")");
            System.out.println("and set eula=true in eula.txt before starting DetergentMC.");
            System.out.println("起動するには、Minecraft EULAに同意して eula.txt を eula=true にしてください。");
            return;
        }

        DetergentServer server = DetergentServer.create(workdir);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "detergentmc-shutdown"));
        server.start();
    }

    private static void printHelp() {
        System.out.println("DetergentMC " + CoreServer.DETERGENT_VERSION + " - Minecraft 1.20.4 (MVP skeleton)");
        System.out.println();
        System.out.println("Usage: java -jar detergentmc-core.jar [--workdir <dir>]");
        System.out.println("  --workdir <dir>  Working directory (default: run/)");
        System.out.println("  --version, -v    Print the version and exit");
        System.out.println("  --help, -h       Show this help");
        System.out.println();
        System.out.println("On first start, DetergentMC creates detergentmc.yml, locales/,");
        System.out.println("bubbles/ and eula.txt in the working directory.");
    }
}
