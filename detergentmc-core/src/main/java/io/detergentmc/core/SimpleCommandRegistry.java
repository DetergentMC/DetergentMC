package io.detergentmc.core;

import io.detergentmc.api.Command;
import io.detergentmc.api.CommandRegistry;
import io.detergentmc.api.CommandSender;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Simple in-memory {@link CommandRegistry}. */
public final class SimpleCommandRegistry implements CommandRegistry {

    private final Map<String, Command> commands = new HashMap<>();

    @Override
    public void register(Command command) {
        String name = command.name().toLowerCase();
        if (commands.containsKey(name)) {
            throw new IllegalArgumentException("Command already registered: " + name);
        }
        commands.put(name, command);
    }

    @Override
    public Optional<Command> find(String name) {
        return Optional.ofNullable(commands.get(name.toLowerCase()));
    }

    @Override
    public boolean dispatch(CommandSender sender, String commandLine) {
        String line = commandLine == null ? "" : commandLine.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            return false;
        }
        String[] parts = line.split("\\s+");
        Optional<Command> command = find(parts[0]);
        if (command.isEmpty()) {
            return false;
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        command.get().execute(sender, args);
        return true;
    }
}
