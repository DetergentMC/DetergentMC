package io.detergentmc.core.vanilla;

/** Receives events parsed from the vanilla server log. */
public interface VanillaLogListener {

    void onLogEvent(VanillaLogEvent event);
}
