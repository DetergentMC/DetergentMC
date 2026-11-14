package io.detergentmc.api;

/**
 * A Bubble is one DetergentMC extension.
 *
 * <p>"Bubble" is the DetergentMC word for what other servers call a
 * "plugin": one jar, one {@code bubble.yml}, one idea. Like a soap bubble,
 * a Bubble is small, self-contained, and can float onto any supported
 * Minecraft version - because Bubbles are written against the
 * version-independent DetergentMC API, not against one Minecraft version.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>The BubbleLoader reads {@code bubble.yml} from the jar.</li>
 *   <li>The loader creates the Bubble and calls {@link #onEnable}.</li>
 *   <li>On server stop the loader calls {@link #onDisable}.</li>
 * </ol>
 */
public interface Bubble {

    /**
     * Called when this Bubble is enabled. Register events, commands and
     * configuration here. The context is only valid while the Bubble is
     * enabled.
     */
    void onEnable(BubbleContext context);

    /** Called when this Bubble is disabled. Release resources here. */
    default void onDisable() {
    }
}
