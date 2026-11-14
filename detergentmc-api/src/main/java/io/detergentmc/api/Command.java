package io.detergentmc.api;

/** One command provided by a Bubble. */
public interface Command {

    /** Command name without a leading slash, e.g. {@code "hello"}. */
    String name();

    /** Short description, shown in help. */
    String description();

    /** Runs the command. {@code args} never contains the command name. */
    void execute(CommandSender sender, String[] args);
}
