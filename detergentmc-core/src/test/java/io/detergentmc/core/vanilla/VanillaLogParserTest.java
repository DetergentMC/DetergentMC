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
