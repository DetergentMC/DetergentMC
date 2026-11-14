package io.detergentmc.core;

import io.detergentmc.api.BubbleContext;
import io.detergentmc.api.BubbleInfo;
import io.detergentmc.api.CommandRegistry;
import io.detergentmc.api.Config;
import io.detergentmc.api.EventBus;
import io.detergentmc.api.Server;
import io.detergentmc.api.Translator;
import java.nio.file.Path;
import java.util.logging.Logger;

/** The {@link BubbleContext} handed to one Bubble while it is enabled. */
public final class BubbleContextImpl implements BubbleContext {

    private final BubbleInfo info;
    private final Config config;
    private final Translator translator;
    private final EventBus events;
    private final CommandRegistry commands;
    private final Server server;
    private final Path dataFolder;

    public BubbleContextImpl(BubbleInfo info, Config config, Translator translator,
            EventBus events, CommandRegistry commands, Server server, Path dataFolder) {
        this.info = info;
        this.config = config;
        this.translator = translator;
        this.events = events;
        this.commands = commands;
        this.server = server;
        this.dataFolder = dataFolder;
    }

    @Override
    public BubbleInfo info() {
        return info;
    }

    @Override
    public Logger logger() {
        return Logger.getLogger("DetergentMC." + info.id());
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public Translator translator() {
        return translator;
    }

    @Override
    public EventBus events() {
        return events;
    }

    @Override
    public CommandRegistry commands() {
        return commands;
    }

    @Override
    public Server server() {
        return server;
    }

    @Override
    public Path dataFolder() {
        return dataFolder;
    }
}
