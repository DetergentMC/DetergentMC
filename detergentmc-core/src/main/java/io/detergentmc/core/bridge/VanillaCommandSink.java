package io.detergentmc.core.bridge;

/**
 * Sends one raw command line to the vanilla server console (for example
 * {@code "tellraw Steve {\"text\":\"hi\"}"} or {@code "stop"}).
 *
 * <p>The production implementation is {@code VanillaServerHost::sendCommand}.
 * Tests substitute a fake that just records the lines. The gameplay bridge
 * depends only on this small interface, never on the host itself, so it can
 * be tested without a vanilla server process.
 */
@FunctionalInterface
public interface VanillaCommandSink {

    void sendCommand(String vanillaCommand);
}
