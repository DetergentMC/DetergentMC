package io.detergentmc.protocol;

/**
 * A resource pack the server can offer to a client. DetergentMC uses
 * resource packs to show new blocks (and their textures) to old clients
 * that do not have those blocks.
 */
public record ResourcePack(
        /** Download URL of the pack (must be served over HTTP(S)). */
        String url,
        /** Lower-case hex SHA-1 of the pack file, or "" if unknown. */
        String sha1,
        /** Whether the client must accept the pack to play. */
        boolean required,
        /** Translation key for the prompt shown to the player. */
        String promptKey
) {
    public ResourcePack {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("ResourcePack url is required");
        }
        if (sha1 == null) {
            sha1 = "";
        }
        if (promptKey == null) {
            promptKey = "";
        }
    }
}
