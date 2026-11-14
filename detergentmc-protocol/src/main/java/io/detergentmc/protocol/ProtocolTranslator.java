package io.detergentmc.protocol;

/**
 * The protocol layer's front door: given a connecting client, decide how
 * server content is presented to it.
 *
 * <p>TODO: this is the skeleton. The networking milestone will feed real
 * client connections into this class (protocol detection on handshake,
 * resource-pack delivery, per-client block translation while sending
 * chunks). For now the rules are pure functions, covered by unit tests.
 */
public final class ProtocolTranslator {

    private final BlockTranslator blocks;

    public ProtocolTranslator(BlockTranslator blocks) {
        this.blocks = blocks;
    }

    public ProtocolTranslator() {
        this(new SimpleBlockTranslator());
    }

    /** The block id a client of this protocol should be sent. */
    public String translateBlock(String serverBlockId, int clientProtocolVersion) {
        return blocks.translateBlock(serverBlockId, clientProtocolVersion);
    }

    /**
     * Whether this client needs the DetergentMC resource pack to see new
     * content properly. Old clients always do when the server uses blocks
     * the client does not have.
     */
    public boolean needsResourcePack(int clientProtocolVersion) {
        return clientProtocolVersion < ProtocolVersion.V1_20_4;
    }

    /**
     * The handshake decision for a connecting client: accept as-is,
     * accept with translation, or reject (see {@link ClientAdapter}).
     */
    public ClientAdapter.Handshake decideHandshake(int clientProtocolVersion) {
        return ClientAdapter.decide(clientProtocolVersion);
    }

    /** A fresh join flow for a client of the given protocol. */
    public ClientJoinFlow joinFlow(int clientProtocolVersion) {
        return new ClientJoinFlow(clientProtocolVersion);
    }
}
