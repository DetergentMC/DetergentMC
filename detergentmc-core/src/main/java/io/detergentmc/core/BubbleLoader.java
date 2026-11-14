package io.detergentmc.core;

import io.detergentmc.api.Bubble;
import io.detergentmc.api.BubbleDisableEvent;
import io.detergentmc.api.BubbleEnableEvent;
import io.detergentmc.api.BubbleException;
import io.detergentmc.api.BubbleInfo;
import io.detergentmc.api.CommandRegistry;
import io.detergentmc.api.EventBus;
import io.detergentmc.api.Server;
import io.detergentmc.api.Translator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads Bubbles from a {@code bubbles/} directory.
 *
 * <p>Each Bubble is a jar containing a {@code bubble.yml} at its root.
 * Loading order respects {@code depends} (a Bubble's dependencies are
 * enabled first). A missing dependency or a dependency cycle fails the
 * load with a clear message instead of a half-started server.
 */
public final class BubbleLoader {

    private static final Logger LOG = Logger.getLogger(BubbleLoader.class.getName());

    private final Path bubblesDir;
    private final Path dataDir;
    private final Translator translator;
    private final EventBus events;
    private final CommandRegistry commands;
    private final Server server;

    private final List<BubbleHandle> loaded = new ArrayList<>();

    public BubbleLoader(Path bubblesDir, Path dataDir, Translator translator,
            EventBus events, CommandRegistry commands, Server server) {
        this.bubblesDir = bubblesDir;
        this.dataDir = dataDir;
        this.translator = translator;
        this.events = events;
        this.commands = commands;
        this.server = server;
    }

    /** All Bubbles that are currently loaded, in enable order. */
    public List<BubbleHandle> loaded() {
        return List.copyOf(loaded);
    }

    /** Scans {@code bubbles/} for jars and enables them in dependency order. */
    public void loadAll() throws IOException {
        if (!Files.exists(bubblesDir)) {
            Files.createDirectories(bubblesDir);
            LOG.info("Created bubbles directory: " + bubblesDir.toAbsolutePath());
            return;
        }
        Map<String, Path> byId = new LinkedHashMap<>();
        Map<String, BubbleInfo> infos = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(bubblesDir, "*.jar")) {
            for (Path jar : stream) {
                BubbleInfo info = readInfo(jar);
                if (byId.containsKey(info.id())) {
                    throw new BubbleException("Duplicate Bubble id '" + info.id()
                            + "': " + jar + " and " + byId.get(info.id()));
                }
                byId.put(info.id(), jar);
                infos.put(info.id(), info);
            }
        }
        for (String id : sortedByDependencies(infos)) {
            enable(byId.get(id), infos.get(id));
        }
    }

    /** Disables all loaded Bubbles in reverse order. */
    public void disableAll() {
        List<BubbleHandle> reverse = new ArrayList<>(loaded);
        java.util.Collections.reverse(reverse);
        for (BubbleHandle handle : reverse) {
            try {
                events.fire(new BubbleDisableEvent(handle.info()));
                handle.bubble().onDisable();
                LOG.info(() -> translator.translate("bubble.disabled", handle.info().id()));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to disable Bubble " + handle.info().id(), e);
            } finally {
                try {
                    handle.classLoader().close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
        loaded.clear();
    }

    /** Reads and parses the {@code bubble.yml} inside a Bubble jar. */
    public static BubbleInfo readInfo(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("bubble.yml");
            if (entry == null) {
                throw new BubbleException("Bubble jar has no bubble.yml: " + jar);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Object loaded = new Yaml().load(in);
                if (!(loaded instanceof Map<?, ?> map)) {
                    throw new BubbleException("bubble.yml is not a map: " + jar);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) map;
                return BubbleInfo.parse(data);
            }
        }
    }

    /** Topological order by {@code depends}; fails on missing deps or cycles. */
    static List<String> sortedByDependencies(Map<String, BubbleInfo> infos) {
        List<String> order = new ArrayList<>();
        Map<String, Integer> state = new HashMap<>(); // 0=unvisited, 1=visiting, 2=done
        for (String id : infos.keySet()) {
            visit(id, infos, state, order);
        }
        return order;
    }

    private static void visit(String id, Map<String, BubbleInfo> infos,
            Map<String, Integer> state, List<String> order) {
        int s = state.getOrDefault(id, 0);
        if (s == 2) {
            return;
        }
        if (s == 1) {
            throw new BubbleException("Circular Bubble dependency involving '" + id + "'");
        }
        BubbleInfo info = infos.get(id);
        if (info == null) {
            throw new BubbleException("Bubble depends on missing Bubble '" + id + "'");
        }
        state.put(id, 1);
        for (String dep : info.depends()) {
            visit(dep, infos, state, order);
        }
        state.put(id, 2);
        order.add(id);
    }

    private void enable(Path jar, BubbleInfo info) throws IOException {
        URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toUri().toURL()}, BubbleLoader.class.getClassLoader());
        try {
            Class<?> mainClass = Class.forName(info.mainClass(), true, loader);
            if (!Bubble.class.isAssignableFrom(mainClass)) {
                throw new BubbleException("Bubble main class does not implement Bubble: "
                        + info.mainClass());
            }
            Bubble bubble = (Bubble) mainClass.getDeclaredConstructor().newInstance();
            YamlConfig config = readBubbleConfig(jar);
            Path dataFolder = dataDir.resolve(info.id());
            Files.createDirectories(dataFolder);
            BubbleContextImpl context = new BubbleContextImpl(
                    info, config, translator, events, commands, server, dataFolder);
            bubble.onEnable(context);
            loaded.add(new BubbleHandle(info, bubble, context, loader, jar));
            events.fire(new BubbleEnableEvent(info));
            LOG.info(() -> translator.translate("bubble.enabled", info.id(), info.version()));
        } catch (BubbleException e) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // best effort
            }
            throw e;
        } catch (ReflectiveOperationException e) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // best effort
            }
            throw new BubbleException("Failed to load Bubble '" + info.id() + "': " + e.getMessage(), e);
        }
    }

    private static YamlConfig readBubbleConfig(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("config.yml");
            if (entry == null) {
                return YamlConfig.empty();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return YamlConfig.fromStream(in);
            }
        }
    }
}
