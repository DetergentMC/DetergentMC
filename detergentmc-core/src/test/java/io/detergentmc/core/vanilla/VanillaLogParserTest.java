package io.detergentmc.core.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class VanillaLogParserTest {

    private static final String PREFIX = "[10:51:02] [Server thread/INFO]: ";

    @Test
    void joinWithUuid() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.empty(), parser.parse(
                PREFIX + "UUID of player Steve is 11111111-2222-3333-4444-555555555555"));
        assertEquals(
                Optional.of(new VanillaLogEvent.PlayerJoined(
                        "Steve", "11111111-2222-3333-4444-555555555555")),
                parser.parse(PREFIX + "Steve joined the game"));
    }

    @Test
    void joinWithoutUuidGivesEmptyUuid() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.PlayerJoined("Alex", "")),
                parser.parse("Alex joined the game"));
    }

    @Test
    void left() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.PlayerLeft("Steve")),
                parser.parse(PREFIX + "Steve left the game"));
    }

    @Test
    void chatInJapanese() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.ChatMessage("たろう", "こんにちは、みんな！")),
                parser.parse(PREFIX + "<たろう> こんにちは、みんな！"));
        assertEquals(Optional.of(new VanillaLogEvent.ChatMessage("Steve", "hello world")),
                parser.parse(PREFIX + "<Steve> hello world"));
    }

    @Test
    void unsignedChatHasNotSecurePrefix() {
        // offline-mode では全チャットが未署名で、バニラは [Not Secure] を付けてログに出す。
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.ChatMessage("Steve", "hello from 1.20.1")),
                parser.parse(PREFIX + "[Not Secure] <Steve> hello from 1.20.1"));
        assertEquals(Optional.of(new VanillaLogEvent.ChatMessage("たろう", "こんにちは")),
                parser.parse(PREFIX + "[Not Secure] <たろう> こんにちは"));
    }

    @Test
    void serverCommand() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.ServerCommand("Steve", "/hello there")),
                parser.parse(PREFIX + "Steve issued server command: /hello there"));
    }

    @Test
    void datapackTriggerHello() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.DatapackTrigger("Steve", "hello")),
                parser.parse(PREFIX + "[Not Secure] [Steve] [DetergentMC] hello"));
    }

    @Test
    void datapackTriggerLang() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.DatapackTrigger("たろう", "lang ja")),
                parser.parse(PREFIX + "[Not Secure] [たろう] [DetergentMC] lang ja"));
        assertEquals(Optional.of(new VanillaLogEvent.DatapackTrigger("Steve", "lang en")),
                parser.parse(PREFIX + "[Not Secure] [Steve] [DetergentMC] lang en"));
    }

    @Test
    void datapackTriggerLinesThatAreNotMarkerCommandsAreIgnored() {
        VanillaLogParser parser = new VanillaLogParser();
        // Vanilla's own trigger-success line: not a command.
        assertEquals(Optional.empty(), parser.parse(
                PREFIX + "[Steve: Triggered [d_hello]]"));
        // A /say without the [DetergentMC] marker: ordinary output, not a command.
        assertEquals(Optional.empty(), parser.parse(
                PREFIX + "[Not Secure] [Steve] hello world"));
        // The marker in plain chat is still just chat.
        assertEquals(Optional.of(new VanillaLogEvent.ChatMessage("Steve", "[DetergentMC] hello")),
                parser.parse(PREFIX + "<Steve> [DetergentMC] hello"));
        // Unknown marker commands must not dispatch.
        assertEquals(Optional.empty(), parser.parse(
                PREFIX + "[Not Secure] [Steve] [DetergentMC] give diamond"));
    }

    @Test
    void doneLineMeansReady() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.ServerReady()),
                parser.parse(PREFIX + "Done (1.234s)! For help, type \"help\""));
    }

    @Test
    void unrelatedLinesAreIgnored() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.empty(), parser.parse(
                PREFIX + "Preparing level \"world\""));
        assertEquals(Optional.empty(), parser.parse(
                PREFIX + "Starting minecraft server version 1.20.4"));
        assertEquals(Optional.empty(), parser.parse(
                "[10:51:02] [Server thread/WARN]: Something odd happened"));
        assertEquals(Optional.empty(), parser.parse(""));
        assertEquals(Optional.empty(), parser.parse(null));
    }

    // ------------------------------------------------------------------
    // Pre-1.7 (legacy generation) log format: the prefix is a full
    // timestamp, "yyyy-MM-dd HH:mm:ss [INFO] ", and there is no
    // "UUID of player" line. Lines below are the real shapes measured
    // on a vanilla 1.6.4 server (protocol 78, Stage E).
    // ------------------------------------------------------------------

    private static final String OLD_PREFIX = "2013-09-11 12:00:00 [INFO] ";

    @Test
    void oldPrefixDoneLineMeansReady() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.ServerReady()),
                parser.parse(OLD_PREFIX + "Done (1.819s)! For help, type \"help\" or \"?\""));
    }

    @Test
    void oldPrefixJoinHasNoUuidLine() {
        VanillaLogParser parser = new VanillaLogParser();
        // The "logged in with entity id" line is the join event; the
        // "joined the game" line that follows it (1.6.x prints both)
        // must not announce the same join twice.
        assertEquals(Optional.of(new VanillaLogEvent.PlayerJoined("Steve", "")),
                parser.parse(OLD_PREFIX
                + "Steve[/127.0.0.1:39856] logged in with entity id 0 at (821.5, 4.0, 987.5)"));
        assertEquals(Optional.empty(),
                parser.parse(OLD_PREFIX + "Steve joined the game"));
        assertEquals(Optional.of(new VanillaLogEvent.PlayerLeft("Steve")),
                parser.parse(OLD_PREFIX + "Steve left the game"));
    }

    @Test
    void oldPrefixJoinOn152HasNoJoinedLine() {
        // Real 1.5.2 server log shape (protocol 61, Stage E): the join
        // is recorded only by the "logged in" line; no "joined the
        // game" line exists in this generation.
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.PlayerJoined("PrepBot", "")),
                parser.parse("2026-11-19 14:08:33 [INFO] "
                + "PrepBot[/127.0.0.1:52858] logged in with entity id 0 at (68.5, 4.0, -689.5)"));
    }

    @Test
    void oldPrefixJoinOn125HasSpaceBeforeAddress() {
        // Real 1.2.5 server log shape (protocol 29, Stage E): the
        // "logged in" line has a space between the name and the
        // [/ip:port] part, unlike 1.5.x and later. The same shape
        // was measured in the real 1.2.4 server log.
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.PlayerJoined("Probe29", "")),
                parser.parse("2026-11-19 20:54:00 [INFO] "
                + "Probe29 [/127.0.0.1:54240] logged in with entity id 0"
                + " at (-5.5, 5.620000004768372, -7.5)"));
        assertEquals(Optional.of(new VanillaLogEvent.PlayerJoined("ProbeBot", "")),
                parser.parse("2026-11-19 22:31:24 [INFO] "
                + "ProbeBot [/127.0.0.1:58688] logged in with entity id 0"
                + " at (0.5, 5.620000004768372, -5.5)"));
    }

    @Test
    void modernLoggedInLineAnnouncesJoinOnceWithUuid() {
        VanillaLogParser parser = new VanillaLogParser();
        parser.parse(PREFIX + "UUID of player Steve is 11111111-2222-3333-4444-555555555555");
        assertEquals(Optional.of(new VanillaLogEvent.PlayerJoined(
                "Steve", "11111111-2222-3333-4444-555555555555")),
                parser.parse(PREFIX
                + "Steve[/127.0.0.1:39856] logged in with entity id 123 at (1.5, 64.0, 2.5)"));
        assertEquals(Optional.empty(),
                parser.parse(PREFIX + "Steve joined the game"));
    }

    @Test
    void oldPrefixChat() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.of(new VanillaLogEvent.ChatMessage("Steve", "hello from 78")),
                parser.parse(OLD_PREFIX + "<Steve> hello from 78"));
    }

    @Test
    void oldPrefixUnrelatedLinesAreIgnored() {
        VanillaLogParser parser = new VanillaLogParser();
        assertEquals(Optional.empty(), parser.parse(
                OLD_PREFIX + "Starting minecraft server version 1.6.4"));
        assertEquals(Optional.empty(), parser.parse(
                "2013-09-11 12:00:00 [WARNING] Something odd happened"));
    }

    @Test
    void uuidDoesNotLeakToAnotherPlayer() {
        VanillaLogParser parser = new VanillaLogParser();
        parser.parse(PREFIX + "UUID of player Steve is 11111111-2222-3333-4444-555555555555");
        assertEquals(Optional.of(new VanillaLogEvent.PlayerJoined("Alex", "")),
                parser.parse(PREFIX + "Alex joined the game"));
        assertTrue(parser.parse(PREFIX + "Steve joined the game")
                .map(event -> ((VanillaLogEvent.PlayerJoined) event).uuid())
                .orElse("").startsWith("11111111"));
    }
}
