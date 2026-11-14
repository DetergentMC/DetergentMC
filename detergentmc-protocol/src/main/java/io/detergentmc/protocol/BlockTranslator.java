package io.detergentmc.protocol;

/**
 * Translates a server-side block id into the block id a client of a
 * given protocol version can actually display.
 *
 * <p>Example: the server has {@code minecraft:cherry_log} (added in
 * 1.20, protocol 763). A 1.12.2 client (protocol 340) has no cherry
 * wood, so the translator returns {@code minecraft:oak_log} and the
 * content layer adds a cherry texture through a resource pack.
 */
public interface BlockTranslator {

    /**
     * Returns the block id to send to a client of the given protocol
     * version. Returns the input unchanged when the client supports it.
     */
    String translateBlock(String serverBlockId, int clientProtocolVersion);

    /** Whether the client protocol can display the block as-is. */
    boolean supports(String serverBlockId, int clientProtocolVersion);
}
