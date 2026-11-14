package io.detergentmc.core.vanilla;

/**
 * One event parsed from a line of the vanilla server log.
 *
 * <p>The vanilla server itself owns the world, chunks, blocks and player
 * movement. DetergentMC only hosts it, so these events are the bridge the
 * gameplay code will listen to: a player joining, leaving, chatting, or
 * issuing a command, and the moment the vanilla server is ready.
 */
public sealed interface VanillaLogEvent
        permits VanillaLogEvent.PlayerJoined, VanillaLogEvent.PlayerLeft,
                VanillaLogEvent.ChatMessage, VanillaLogEvent.ServerCommand,
                VanillaLogEvent.DatapackTrigger, VanillaLogEvent.ServerReady {

    /**
     * A player joined the game. {@code uuid} is the Mojang UUID from the
     * {@code UUID of player ...} log line, or {@code ""} when it was not
     * seen (for example when the log line was read on its own).
     */
    record PlayerJoined(String name, String uuid) implements VanillaLogEvent {
    }

    /** A player left the game. */
    record PlayerLeft(String name) implements VanillaLogEvent {
    }

    /** A player said something in chat (Japanese chat included). */
    record ChatMessage(String name, String message) implements VanillaLogEvent {
    }

    /**
     * A player issued a command on the vanilla server.
     * {@code commandLine} is the full line as the vanilla server logged it,
     * including the leading {@code /} (for example {@code "/hello"}).
     */
    record ServerCommand(String player, String commandLine) implements VanillaLogEvent {
    }

    /**
     * A player fired a DetergentMC datapack trigger ({@code /trigger
     * d_hello} etc.). Vanilla 1.20.4 does not log player commands, so the
     * datapack's tick function turns the trigger into a
     * {@code /say [DetergentMC] ...} line, which <em>does</em> reach the
     * log as {@code [Not Secure] [name] [DetergentMC] hello}. This event
     * is the in-game equivalent of the player typing the command:
     * {@code command} is the command line without the leading
     * {@code /} (for example {@code "hello"} or {@code "lang en"}).
     */
    record DatapackTrigger(String player, String command) implements VanillaLogEvent {
    }

    /** The vanilla server printed {@code Done (...)} and is ready to play. */
    record ServerReady() implements VanillaLogEvent {
    }
}
