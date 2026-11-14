package io.detergentmc.core;

import io.detergentmc.api.Bubble;
import io.detergentmc.api.BubbleContext;
import io.detergentmc.api.BubbleInfo;
import java.net.URLClassLoader;
import java.nio.file.Path;

/** One loaded Bubble: its metadata, instance, context and classloader. */
public final class BubbleHandle {

    private final BubbleInfo info;
    private final Bubble bubble;
    private final BubbleContext context;
    private final URLClassLoader classLoader;
    private final Path jarFile;

    public BubbleHandle(BubbleInfo info, Bubble bubble, BubbleContext context,
            URLClassLoader classLoader, Path jarFile) {
        this.info = info;
        this.bubble = bubble;
        this.context = context;
        this.classLoader = classLoader;
        this.jarFile = jarFile;
    }

    public BubbleInfo info() {
        return info;
    }

    public Bubble bubble() {
        return bubble;
    }

    public BubbleContext context() {
        return context;
    }

    public URLClassLoader classLoader() {
        return classLoader;
    }

    public Path jarFile() {
        return jarFile;
    }
}
