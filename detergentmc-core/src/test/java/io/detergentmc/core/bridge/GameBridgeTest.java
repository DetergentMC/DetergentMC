package io.detergentmc.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.api.Command;
import io.detergentmc.api.CommandSender;
import io.detergentmc.api.PlayerChatEvent;
import io.detergentmc.api.PlayerJoinEvent;
import io.detergentmc.api.PlayerLeaveEvent;
import io.detergentmc.api.Subscribe;
import io.detergentmc.core.LocaleManager;
import io.detergentmc.core.SimpleCommandRegistry;
import io.detergentmc.core.SimpleEventBus;
import io.detergentmc.core.vanilla.VanillaLogEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameBridgeTest {

    @TempDir
    Path tempDir;

    private final List<String> sent = new ArrayList<>();
    private LocaleManager translator;
    private SimpleEventBus events;
    private SimpleCommandRegistry commands;
    private PlayerRegistry players;
    private GameBridge bridge;
    private final List<Object> fired = new ArrayList<>();

    static class Listener {
        final List<Object> fired;

        Listener(List<Object> fired) {
            this.fired = fired;
        }

        @Subscribe
        public void onJoin(PlayerJoinEvent event) {
            fired.add(event);
        }

        @Subscribe
        public void onLeave(PlayerLeaveEvent event) {
            fired.add(event);
        }

        @Subscribe
        public void onChat(PlayerChatEvent event) {
            fired.add(event);
        }
    }

    @BeforeEach
    void setUp() {
        translator = new LocaleManager(Locale.JAPANESE, tempDir);
        translator.loadBaseLanguages();
        events = new SimpleEventBus();
        events.register(new Listener(fired));
        commands = new SimpleCommandRegistry();
        // Same /hello behaviour as HelloBubble.
        commands.register(new Command() {
            @Override
            public String name() {
                return "hello";
            }

            @Override
            public String description() {
                return "hello";
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                String target = args.length > 0 ? String.join(" ", args) : sender.name();
                sender.sendMessage(translator.translate("command.hello", sender.locale(), target));
            }
        });
        Path dataDir = tempDir.resolve("data");
        PlayerLocaleStore store = new PlayerLocaleStore(
                dataDir.resolve("player-locales.properties"), Locale.JAPANESE);
        players = new PlayerRegistry(store, Locale.JAPANESE, sent::add);
        bridge = new GameBridge(sent::add, events, commands, translator, players, dataDir);
    }

    @Test
    void joinFiresEventAndRegistersPlayer() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));

        assertEquals(1, fired.size());
        assertTrue(fired.get(0) instanceof PlayerJoinEvent);
        PlayerJoinEvent join = (PlayerJoinEvent) fired.get(0);
        assertEquals("Steve", join.player().name());
        assertEquals(UUID.nameUUIDFromBytes(
                "OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8)), join.player().uuid());
        assertEquals(Locale.JAPANESE, join.player().locale());
        assertEquals(1, players.onlineCount());
        assertTrue(sent.isEmpty(), "join must not be re-broadcast");
    }

    @Test
    void joinKeepsRealMojangUuid() {
        UUID real = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Alex", real.toString()));

        PlayerJoinEvent join = (PlayerJoinEvent) fired.get(0);
        assertEquals(real, join.player().uuid());
    }

    @Test
    void leaveFiresEventAndUnregistersPlayer() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        bridge.onLogEvent(new VanillaLogEvent.PlayerLeft("Steve"));

        assertEquals(2, fired.size());
        assertTrue(fired.get(1) instanceof PlayerLeaveEvent);
        assertEquals("Steve", ((PlayerLeaveEvent) fired.get(1)).player().name());
        assertEquals(0, players.onlineCount());
        assertTrue(players.find("Steve").isEmpty());
    }

    @Test
    void leaveOfUnknownPlayerFiresNothing() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerLeft("Ghost"));
        assertTrue(fired.isEmpty());
    }

    @Test
    void chatFiresEventButIsNotResent() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        fired.clear();
        bridge.onLogEvent(new VanillaLogEvent.ChatMessage("Steve", "こんにちは"));

        assertEquals(1, fired.size());
        assertTrue(fired.get(0) instanceof PlayerChatEvent);
        PlayerChatEvent chat = (PlayerChatEvent) fired.get(0);
        assertEquals("Steve", chat.player().name());
        assertEquals("こんにちは", chat.message());
        assertTrue(sent.isEmpty(), "chat must not be re-sent by the bridge");
    }

    @Test
    void helloCommandIsDispatchedToThePlayerViaTellraw() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        sent.clear();
        bridge.onLogEvent(new VanillaLogEvent.ServerCommand("Steve", "/hello"));

        assertEquals(1, sent.size());
        assertEquals(
                "tellraw Steve {\"text\":\"こんにちは、Steveさん！ DetergentMCへようこそ。\"}",
                sent.get(0));
    }

    @Test
    void datapackTriggerHelloDispatchesLikeTypedCommand() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        sent.clear();
        bridge.onLogEvent(new VanillaLogEvent.DatapackTrigger("Steve", "hello"));

        assertEquals(1, sent.size());
        assertEquals(
                "tellraw Steve {\"text\":\"こんにちは、Steveさん！ DetergentMCへようこそ。\"}",
                sent.get(0));
    }

    @Test
    void datapackTriggerLangSwitchesLanguageAndNextHelloFollows() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        sent.clear();
        bridge.onLogEvent(new VanillaLogEvent.DatapackTrigger("Steve", "lang en"));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("Language set to en."),
                "confirmation must be in the new language: " + sent.get(0));
        assertEquals(Locale.ENGLISH, players.find("Steve").orElseThrow().locale());

        // The next trigger now greets in English, and switching back works.
        sent.clear();
        bridge.onLogEvent(new VanillaLogEvent.DatapackTrigger("Steve", "hello"));
        assertEquals(
                "tellraw Steve {\"text\":\"Hello, Steve! Welcome to DetergentMC.\"}",
                sent.get(0));
        sent.clear();
        bridge.onLogEvent(new VanillaLogEvent.DatapackTrigger("Steve", "lang ja"));
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("言語をjaに設定しました。"),
                "switching back must confirm in Japanese: " + sent.get(0));
        assertEquals(Locale.JAPANESE, players.find("Steve").orElseThrow().locale());
    }

    @Test
    void unknownCommandIsIgnored() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        sent.clear();
        fired.clear();
        bridge.onLogEvent(new VanillaLogEvent.ServerCommand("Steve", "/give Steve diamond 64"));

        assertTrue(sent.isEmpty(), "unknown commands must not produce a reply");
        assertTrue(fired.isEmpty());
    }

    @Test
    void langCommandSwitchesLanguageAndPersists() throws Exception {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        bridge.onLogEvent(new VanillaLogEvent.ServerCommand("Steve", "/lang en"));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("Language set to en."),
                "confirmation must be in the new language: " + sent.get(0));
        assertEquals(Locale.ENGLISH, players.find("Steve").orElseThrow().locale());

        // A fresh store on the same file reads the choice back (restart).
        PlayerLocaleStore reloaded = new PlayerLocaleStore(
                tempDir.resolve("data/player-locales.properties"), Locale.JAPANESE);
        assertEquals(Locale.ENGLISH, reloaded.localeFor("Steve"));

        // And the next /hello now greets in English.
        sent.clear();
        bridge.onLogEvent(new VanillaLogEvent.ServerCommand("Steve", "/hello"));
        assertEquals(
                "tellraw Steve {\"text\":\"Hello, Steve! Welcome to DetergentMC.\"}",
                sent.get(0));
        assertTrue(Files.exists(tempDir.resolve("data/player-locales.properties")));
    }

    @Test
    void langCommandRejectsUnsupportedLanguage() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        sent.clear();
        bridge.onLogEvent(new VanillaLogEvent.ServerCommand("Steve", "/lang fr"));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("対応していない言語です"),
                "unsupported language must get an error: " + sent.get(0));
        assertEquals(Locale.JAPANESE, players.find("Steve").orElseThrow().locale());
    }

    @Test
    void langCommandWithoutArgumentShowsUsage() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        sent.clear();
        bridge.onLogEvent(new VanillaLogEvent.ServerCommand("Steve", "/lang"));

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("使い方: /lang <ja|en>"),
                "missing argument must show usage: " + sent.get(0));
    }

    @Test
    void serverReadyDoesNotThrow() {
        bridge.onLogEvent(new VanillaLogEvent.ServerReady());
        assertTrue(fired.isEmpty());
        assertTrue(sent.isEmpty());
    }

    @Test
    void onlinePlayersReflectsJoinsAndLeaves() {
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Steve", ""));
        bridge.onLogEvent(new VanillaLogEvent.PlayerJoined("Alex", ""));
        assertEquals(2, players.onlinePlayers().size());
        bridge.onLogEvent(new VanillaLogEvent.PlayerLeft("Steve"));
        assertEquals(1, players.onlinePlayers().size());
        assertEquals("Alex", players.onlinePlayers().iterator().next().name());
    }
}
