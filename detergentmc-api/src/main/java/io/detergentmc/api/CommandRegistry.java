package io.detergentmc.api;

import java.util.Optional;

/** Registers commands and dispatches typed command lines to them. */
public interface CommandRegistry {

    /** Registers a command. Throws if the name is already taken. */
    void register(Command command);

    /** Finds a command by name. */
    Optional<Command> find(String name);

    /**
     * Dispatches a command line such as {@code "hello Steve"}.
     * Returns {@code true} if a command handled it.
     */
    boolean dispatch(CommandSender sender, String commandLine);
}
