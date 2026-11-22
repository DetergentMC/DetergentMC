package io.detergentmc.core.vanilla;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses vanilla (Mojang) server log lines into {@link VanillaLogEvent}s.
 *
 * <p>Real 1.20.4 log lines look like this (the timestamp prefix is stripped
 * before matching):
 * <pre>
 * UUID of player Steve is 11111111-2222-3333-4444-555555555555
 * Steve joined the game
 * Steve left the game
 * &lt;Steve&gt; こんにちは
 * [Not Secure] &lt;Steve&gt; こんにちは
 * Steve issued server command: /hello
 * [Not Secure] [Steve] [DetergentMC] hello
 * Done (1.234s)! For help, type "help"
 * </pre>
 *
 * <p>Unsigned chat is logged by vanilla with a {@code [Not Secure]} prefix
 * ({@code [Not Secure] <Steve> ...}). Offline-mode servers log every chat
 * line that way, so the chat pattern accepts the prefix (it is not part of
 * the message). The same prefix is accepted for the datapack trigger.
 *
 * <p>The parser is stateful in one small way: the {@code UUID of player ...}
 * line arrives just before the matching {@code joined the game} line, so the
 * UUID is remembered per player name and attached to the
 * {@link VanillaLogEvent.PlayerJoined} event. A {@code joined} line seen
 * without a preceding UUID line yields an empty UUID ({@code ""}).
 * Lines that match nothing return {@link Optional#empty()} and are ignored.
 *
 * <p>Pre-1.7 servers (the legacy generation, e.g. 1.6.4) print a different
 * prefix — {@code 2013-09-11 12:00:00 [INFO] } instead of
 * {@code [12:00:00] [Server thread/INFO]: } — and no {@code UUID of player}
 * line, but the {@code Done (...)} / {@code joined the game} /
 * {@code <name> message} bodies are the same, so stripping the old prefix
 * (see {@link #stripPrefix}) is enough to read them. Measured against a
 * real 1.6.4 server log (protocol 78, Stage E): without this, a hosted
 * 1.6.4 backend never reported ready / join / chat events at all.
 *
 * <p>The join record itself also differs by generation. 1.6.x and modern
 * servers print both {@code NAME[/ip:port] logged in with entity id N at
 * (...)} and {@code NAME joined the game}; 1.5.x servers (measured against
 * a real 1.5.2 server log, protocol 61, Stage E) print only the
 * {@code logged in} line — there is no {@code joined the game} line at
 * all. The parser therefore treats the {@code logged in} line as the join
 * event and suppresses the {@code joined the game} line that follows it
 * for the same name, so every generation yields exactly one
 * {@link VanillaLogEvent.PlayerJoined} per join.
 *
 * <p>The 1.2 generation (1.2.5 / 1.2.4, protocol 29, Stage E) prints the
 * same {@code logged in} line but with a space before the address:
 * {@code NAME [/ip:port] logged in with entity id N at (...)} (measured
 * against real 1.2.5 and 1.2.4 server logs). The pattern therefore
 * accepts an optional space there; without it, a hosted 1.2.x backend
 * never reported join events at all (the production GameBridge reads
 * the same parser).
 */
public final class VanillaLogParser {

    private static final Pattern LOG_PREFIX =
            Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[[^\\]]+\\]:\\s?");
    /** Pre-1.7 prefix: {@code 2013-09-11 12:00:00 [INFO] } (no colon after the level). */
    private static final Pattern OLD_LOG_PREFIX =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\[[^\\]]+\\]\\s?");
    private static final Pattern UUID_LINE =
            Pattern.compile("^UUID of player (\\S+) is ([0-9a-fA-F\\-]{36})$");
    private static final Pattern LOGGED_IN =
            Pattern.compile("^(\\S+) ?\\[/[^]]+\\] logged in with entity id \\d+ at \\(.*\\)$");
    private static final Pattern JOINED =
            Pattern.compile("^(\\S+) joined the game$");
    private static final Pattern LEFT =
            Pattern.compile("^(\\S+) left the game$");
    private static final Pattern SERVER_COMMAND =
            Pattern.compile("^(\\S+) issued server command: (.*)$");
    /**
     * A {@code /say} line the datapack's tick function produced, e.g.
     * {@code [Not Secure] [Steve] [DetergentMC] hello} or
     * {@code [Not Secure] [Steve] [DetergentMC] lang en}. The
     * {@code [Not Secure]} prefix is what vanilla logs for unsigned
     * {@code /say} output; it is accepted but not required. Only the
     * exact marker commands match — any other {@code /say} text is
     * ignored, so ordinary chat never becomes a command.
     */
    private static final Pattern DATAPACK_TRIGGER =
            Pattern.compile("^(?:\\[Not Secure\\] )?\\[([^\\]]+)\\] \\[DetergentMC\\] (hello|lang ja|lang en)$");
    private static final Pattern CHAT =
            Pattern.compile("^(?:\\[Not Secure\\] )?<([^>]+)>(?: (.*))?$");

    private final Map<String, String> uuidByName = new HashMap<>();
    /** Names whose join was already announced by a {@code logged in} line. */
    private final java.util.Set<String> joinAnnounced = new java.util.HashSet<>();

    /**
     * Removes the log prefix, if present: the modern
     * {@code [HH:MM:SS] [Server thread/INFO]: } form, or the pre-1.7
     * {@code yyyy-MM-dd HH:mm:ss [INFO] } form.
     */
    public static String stripPrefix(String line) {
        if (line == null) {
            return "";
        }
        Matcher modern = LOG_PREFIX.matcher(line);
        if (modern.find()) {
            return modern.replaceFirst("");
        }
        return OLD_LOG_PREFIX.matcher(line).replaceFirst("");
    }

    /**
     * Parses one log line. Returns {@link Optional#empty()} for lines that
     * are not an event (including the {@code UUID of player ...} line itself,
     * which only supplies the UUID for the following join event).
     */
    public Optional<VanillaLogEvent> parse(String line) {
        String text = stripPrefix(line);
        Matcher matcher;

        matcher = UUID_LINE.matcher(text);
        if (matcher.matches()) {
            uuidByName.put(matcher.group(1), matcher.group(2));
            return Optional.empty();
        }

        if (text.startsWith("Done (") && text.contains("! For help, type \"help\"")) {
            return Optional.of(new VanillaLogEvent.ServerReady());
        }

        matcher = LOGGED_IN.matcher(text);
        if (matcher.matches()) {
            String name = matcher.group(1);
            joinAnnounced.add(name);
            return Optional.of(new VanillaLogEvent.PlayerJoined(
                    name, uuidByName.getOrDefault(name, "")));
        }

        matcher = JOINED.matcher(text);
        if (matcher.matches()) {
            String name = matcher.group(1);
            if (joinAnnounced.remove(name)) {
                // The logged-in line already announced this join (1.6.x and
                // modern servers print both lines; 1.5.x prints only the
                // logged-in line).
                return Optional.empty();
            }
            return Optional.of(new VanillaLogEvent.PlayerJoined(
                    name, uuidByName.getOrDefault(name, "")));
        }

        matcher = LEFT.matcher(text);
        if (matcher.matches()) {
            joinAnnounced.remove(matcher.group(1));
            return Optional.of(new VanillaLogEvent.PlayerLeft(matcher.group(1)));
        }

        matcher = SERVER_COMMAND.matcher(text);
        if (matcher.matches()) {
            return Optional.of(new VanillaLogEvent.ServerCommand(
                    matcher.group(1), matcher.group(2)));
        }

        matcher = DATAPACK_TRIGGER.matcher(text);
        if (matcher.matches()) {
            return Optional.of(new VanillaLogEvent.DatapackTrigger(
                    matcher.group(1), matcher.group(2)));
        }

        matcher = CHAT.matcher(text);
        if (matcher.matches()) {
            String message = matcher.group(2);
            return Optional.of(new VanillaLogEvent.ChatMessage(
                    matcher.group(1), message == null ? "" : message));
        }

        return Optional.empty();
    }
}
