package io.detergentmc.core;

import io.detergentmc.api.Server;
import io.detergentmc.api.VersionAdapter;

/** The core's implementation of the version-independent {@link Server} view. */
public final class CoreServer implements Server {

    public static final String DETERGENT_VERSION = "0.1.0";

    private final VersionAdapter adapter;

    public CoreServer(VersionAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public String detergentVersion() {
        return DETERGENT_VERSION;
    }

    @Override
    public String minecraftVersion() {
        return adapter.minecraftVersion();
    }

    @Override
    public int protocolVersion() {
        return adapter.protocolVersion();
    }

    @Override
    public VersionAdapter adapter() {
        return adapter;
    }
}
