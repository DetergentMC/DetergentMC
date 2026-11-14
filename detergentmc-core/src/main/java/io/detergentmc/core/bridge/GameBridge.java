package io.detergentmc.core.bridge;

import io.detergentmc.api.EventBus;
import io.detergentmc.api.PlayerChatEvent;
import io.detergentmc.api.PlayerJoinEvent;
import io.detergentmc.api.PlayerLeaveEvent;
import io.detergentmc.core.LocaleManager;
import io.detergentmc.core.SimpleCommandRegistry;
import io.detergentmc.core.SimpleEventBus;
import io.detergentmc.core.vanilla.VanillaLogEvent;
import io.detergentmc.core.vanilla.VanillaLogListener;
import io.detergentmc.core.vanilla.VanillaServerHost;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Bridges vanilla log events into DetergentMC API events, commands and
 * players.
 *
 * <ul>
 *   <li>Join -&gt; creates/registers a {@link VanillaPlayer} and fires
 *       {@link PlayerJoinEvent} (so Bubbles such as HelloBubble react).
 *       Vanilla already broadcasts the join message, so nothing is
 *       re-sent to chat.</li>
 *   <li>Leave -&gt; fires {@link PlayerLeaveEvent} and unregisters the
 *       player.</li>
 *   <li>Chat -&gt; fires {@link PlayerChatEvent} (so Bubbles can react).
 *       Vanilla already delivers the chat, so it is not re-sent.</li>
 *   <li>Command -&gt; when the command name is registered with DetergentMC
 *       ({@code /hello}, {@code /lang}, ...), dispatches it with the
 *       player as sender; the reply reaches that player via tellraw.
 *       Commands DetergentMC does not know are ignored entirely, so
 *       vanilla's own "unknown command" reply is left alone.</li>
 *   <li>Datapack trigger -&gt; the in-game equivalent of the matching
 *       command ({@code /trigger d_hello} is {@code /hello},
 *       {@code /trigger d_lang_en} is {@code /lang en}): dispatched
 *       exactly like a typed command, with the player as sender.
 *       Vanilla already printed the trigger's own success message, so
 *       the bridge sends nothing extra beyond the command's reply.</li>
 *   <li>Ready -&gt; logs that the vanilla server is ready.</li>
 * </ul>
 *
 * <p>The bridge talks to vanilla only through a {@link VanillaCommandSink},
 * so tests can substitute a fake that records the lines instead of a real
 * server process. The {@link VanillaServerHost} itself is never modified.
 */
public final class GameBridge implements VanillaLogListener {

    private static final Logger LOG = Logger.getLogger(GameBridge.class.getName());

    private final EventBus events;
    private final SimpleCommandRegistry commands;
    private final LocaleManager translator;
    private final PlayerRegistry players;
    private final Path dataDir;
    /** Vanilla listen port, for the ready message; {@code null} when unknown. */
    private final Integer port;

    /**
     * Creates the bridge backed by a real vanilla host. This is the
     * constructor DetergentServer uses.
     */
    public GameBridge(VanillaServerHost host, SimpleEventBus events,
            SimpleCommandRegistry commands, LocaleManager translator,
            PlayerRegistry players, Path dataDir) {
        this(host == null ? null : host::sendCommand, events, commands,
                translator, players, dataDir, null);
        if (host == null) {
            throw new IllegalArgumentException("vanilla host is required");
        }
    }

    /** Same as {@link #GameBridge(VanillaServerHost, SimpleEventBus, SimpleCommandRegistry, LocaleManager, PlayerRegistry, Path)}, but with the vanilla port for the ready message. */
    public GameBridge(VanillaServerHost host, SimpleEventBus events,
            SimpleCommandRegistry commands, LocaleManager translator,
            PlayerRegistry players, Path dataDir, int port) {
        this(host == null ? null : host::sendCommand, events, commands,
                translator, players, dataDir, port);
        if (host == null) {
            throw new IllegalArgumentException("vanilla host is required");
        }
    }

    /**
     * Creates the bridge backed by an explicit command sink (a fake in
     * tests, {@code VanillaServerHost::sendCommand} in production).
     */
    public GameBridge(VanillaCommandSink sink, SimpleEventBus events,
            SimpleCommandRegistry commands, LocaleManager translator,
            PlayerRegistry players, Path dataDir) {
        this(sink, events, commands, translator, players, dataDir, null);
    }

    /** Sink-backed constructor with the vanilla port for the ready message. */
    public GameBridge(VanillaCommandSink sink, SimpleEventBus events,
            SimpleCommandRegistry commands, LocaleManager translator,
            PlayerRegistry players, Path dataDir, Integer port) {
        if (sink == null) {
            throw new IllegalArgumentException("command sink is required");
        }
        if (events == null) {
            throw new IllegalArgumentException("event bus is required");
        }
        if (commands == null) {
            throw new IllegalArgumentException("command registry is required");
        }
        if (translator == null) {
            throw new IllegalArgumentException("translator is required");
        }
        if (players == null) {
            throw new IllegalArgumentException("player registry is required");
        }
        if (dataDir == null) {
            throw new IllegalArgumentException("data directory is required");
        }
        this.events = events;
        this.commands = commands;
        this.translator = translator;
        this.players = players;
        this.dataDir = dataDir;
        this.port = port;
        // The built-in /lang command lives here so it is available as soon
        // as the bridge is wired up, in game and from the console.
        if (commands.find("lang").isEmpty()) {
            commands.register(new LangCommand(translator, players));
        }
    }

    /** The data directory holding {@code player-locales.properties}. */
    public Path dataDir() {
        return dataDir;
    }

    public PlayerRegistry players() {
        return players;
    }

    @Override
    public void onLogEvent(VanillaLogEvent event) {
        if (event instanceof VanillaLogEvent.PlayerJoined joined) {
            onPlayerJoined(joined);
        } else if (event instanceof VanillaLogEvent.PlayerLeft left) {
            onPlayerLeft(left);
        } else if (event instanceof VanillaLogEvent.ChatMessage chat) {
            onChat(chat);
        } else if (event instanceof VanillaLogEvent.ServerCommand command) {
            onServerCommand(command);
        } else if (event instanceof VanillaLogEvent.DatapackTrigger trigger) {
            onDatapackTrigger(trigger);
        } else if (event instanceof VanillaLogEvent.ServerReady) {
            onServerReady();
        }
    }

    private void onPlayerJoined(VanillaLogEvent.PlayerJoined joined) {
        VanillaPlayer player = players.getOrCreate(joined.name(), joined.uuid());
        LOG.info(() -> translator.translate("player.join", player.name()));
        events.fire(new PlayerJoinEvent(player));
    }

    private void onPlayerLeft(VanillaLogEvent.PlayerLeft left) {
        players.find(left.name()).ifPresent(player -> {
            events.fire(new PlayerLeaveEvent(player));
            players.remove(left.name());
            LOG.info(() -> translator.translate("player.leave", left.name()));
        });
    }

    private void onChat(VanillaLogEvent.ChatMessage chat) {
        VanillaPlayer player = players.getOrCreate(chat.name(), "");
        events.fire(new PlayerChatEvent(player, chat.message()));
    }

    private void onServerCommand(VanillaLogEvent.ServerCommand command) {
        String line = command.commandLine() == null ? "" : command.commandLine().trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            return;
        }
        String name = line.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (commands.find(name).isEmpty()) {
            // Not a DetergentMC command: leave it to vanilla entirely.
            return;
        }
        VanillaPlayer player = players.getOrCreate(command.player(), "");
        PlayerCommandSender sender = new PlayerCommandSender(player, translator);
        commands.dispatch(sender, line);
    }

    /**
     * A datapack trigger fired: dispatch the matching command exactly as
     * if the player had typed it ({@code /trigger d_hello} -&gt;
     * {@code /hello}, {@code /trigger d_lang_en} -&gt; {@code /lang en}).
     * The reply reaches the player via tellraw, in their own language;
     * the trigger's own success message is vanilla's, so nothing extra
     * is sent.
     */
    private void onDatapackTrigger(VanillaLogEvent.DatapackTrigger trigger) {
        onServerCommand(new VanillaLogEvent.ServerCommand(
                trigger.player(), "/" + trigger.command()));
    }

    private void onServerReady() {
        if (port != null) {
            // Pass the port as a String: MessageFormat would otherwise
            // render an int with locale grouping ("25,565" in Japanese).
            LOG.info(() -> translator.translate("vanilla.ready", String.valueOf(port)));
        } else {
            LOG.info(() -> translator.translate("vanilla.ready-simple"));
        }
    }
}
