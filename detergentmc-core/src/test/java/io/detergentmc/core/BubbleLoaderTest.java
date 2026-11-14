package io.detergentmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.adapter.v1_20_4.V1204Adapter;
import io.detergentmc.api.BubbleException;
import io.detergentmc.api.BubbleInfo;
import io.detergentmc.api.CommandSender;
import io.detergentmc.bubble.hello.HelloBubble;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test of the Bubble loader: builds a real HelloBubble jar in
 * a temp directory, loads it, runs its /hello command, then disables it.
 */
class BubbleLoaderTest {

    static final class CapturingSender implements CommandSender {
        private final Locale locale;
        private final List<String> messages = new ArrayList<>();

        CapturingSender(Locale locale) {
            this.locale = locale;
        }

        @Override
        public Locale locale() {
            return locale;
        }

        @Override
        public String name() {
            return "Steve";
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        List<String> messages() {
            return messages;
        }
    }

    private static void addClassBytes(JarOutputStream jar, String resourceName) throws IOException {
        try (InputStream in = HelloBubble.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + resourceName);
            }
            jar.putNextEntry(new JarEntry(resourceName));
            in.transferTo(jar);
            jar.closeEntry();
        }
    }

    /** Builds a HelloBubble jar (classes + bubble.yml) in the given dir. */
    static Path buildHelloBubbleJar(Path dir) throws IOException {
        Path jarFile = dir.resolve("hello-bubble-1.0.0.jar");
        try (OutputStream out = Files.newOutputStream(jarFile);
             JarOutputStream jar = new JarOutputStream(out)) {
            // HelloBubble + its anonymous Command class ($1).
            addClassBytes(jar, "io/detergentmc/bubble/hello/HelloBubble.class");
            for (int i = 1; i <= 9; i++) {
                String inner = "io/detergentmc/bubble/hello/HelloBubble$" + i + ".class";
                if (HelloBubble.class.getClassLoader().getResource(inner) == null) {
                    break;
                }
                addClassBytes(jar, inner);
            }
            try (InputStream in = HelloBubble.class.getResourceAsStream("/bubble.yml")) {
                if (in == null) {
                    throw new IllegalStateException("bubble.yml not on test classpath");
                }
                jar.putNextEntry(new JarEntry("bubble.yml"));
                in.transferTo(jar);
                jar.closeEntry();
            }
        }
        return jarFile;
    }

    @Test
    void loadsHelloBubbleJarAndRunsHelloCommand(@TempDir Path tmp) throws IOException {
        Path bubblesDir = tmp.resolve("bubbles");
        Files.createDirectories(bubblesDir);
        buildHelloBubbleJar(bubblesDir);

        LocaleManager translator = new LocaleManager(Locale.JAPANESE, tmp.resolve("locales"));
        translator.loadBaseLanguages();
        SimpleEventBus events = new SimpleEventBus();
        SimpleCommandRegistry commands = new SimpleCommandRegistry();
        CoreServer server = new CoreServer(new V1204Adapter());
        BubbleLoader loader = new BubbleLoader(
                bubblesDir, tmp.resolve("data"), translator, events, commands, server);

        loader.loadAll();
        assertEquals(1, loader.loaded().size());
        assertEquals("hello-bubble", loader.loaded().get(0).info().id());

        CapturingSender sender = new CapturingSender(Locale.JAPANESE);
        assertTrue(commands.dispatch(sender, "hello"));
        assertEquals(1, sender.messages().size());
        assertTrue(sender.messages().get(0).contains("こんにちは"),
                "expected a Japanese greeting, got: " + sender.messages().get(0));

        assertTrue(commands.dispatch(sender, "hello Taro"));
        assertTrue(sender.messages().get(1).contains("Taro"));

        loader.disableAll();
        assertEquals(0, loader.loaded().size());
    }

    @Test
    void rejectsJarWithoutBubbleYml(@TempDir Path tmp) throws IOException {
        Path bubblesDir = tmp.resolve("bubbles");
        Files.createDirectories(bubblesDir);
        Path emptyJar = bubblesDir.resolve("empty.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(emptyJar))) {
            jar.putNextEntry(new JarEntry("nothing.txt"));
            jar.closeEntry();
        }
        assertThrows(BubbleException.class, () -> BubbleLoader.readInfo(emptyJar));
    }

    @Test
    void ordersDependenciesAndRejectsMissingOnes() {
        BubbleInfo a = new BubbleInfo("a", "A", "1.0.0", "", List.of(), "example.A", List.of("b"));
        BubbleInfo b = new BubbleInfo("b", "B", "1.0.0", "", List.of(), "example.B", List.of());
        assertEquals(List.of("b", "a"),
                BubbleLoader.sortedByDependencies(Map.of("a", a, "b", b)));

        BubbleInfo broken = new BubbleInfo("c", "C", "1.0.0", "", List.of(),
                "example.C", List.of("missing"));
        assertThrows(BubbleException.class,
                () -> BubbleLoader.sortedByDependencies(Map.of("c", broken)));

        BubbleInfo x = new BubbleInfo("x", "X", "1.0.0", "", List.of(), "example.X", List.of("y"));
        BubbleInfo y = new BubbleInfo("y", "Y", "1.0.0", "", List.of(), "example.Y", List.of("x"));
        assertThrows(BubbleException.class,
                () -> BubbleLoader.sortedByDependencies(Map.of("x", x, "y", y)));
    }
}
