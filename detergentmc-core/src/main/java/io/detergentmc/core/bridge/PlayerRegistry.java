package io.detergentmc.core.bridge;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks the players currently online: name -&gt; {@link VanillaPlayer}.
 * A player is created on join and removed on leave. The locale a player
 * starts with comes from the {@link PlayerLocaleStore} (their saved
 * {@code /lang} choice), falling back to the server default.
 */
public final class PlayerRegistry {

    private final PlayerLocaleStore localeStore;
    private final Locale defaultLocale;
    private final VanillaCommandSink sink;
    private final ConcurrentMap<String, VanillaPlayer> online = new ConcurrentHashMap<>();

    public PlayerRegistry(PlayerLocaleStore localeStore, Locale defaultLocale,
            VanillaCommandSink sink) {
        if (localeStore == null) {
            throw new IllegalArgumentException("locale store is required");
        }
        if (sink == null) {
            throw new IllegalArgumentException("command sink is required");
        }
        this.localeStore = localeStore;
        this.defaultLocale = defaultLocale == null ? localeStore.defaultLocale() : defaultLocale;
        this.sink = sink;
    }

    public PlayerLocaleStore localeStore() {
        return localeStore;
    }

    public Locale defaultLocale() {
        return defaultLocale;
    }

    /**
     * Returns the online player with this name, creating (and registering)
     * one when this is the first time the name is seen. {@code uuidText} is
     * the Mojang UUID from the vanilla log, or blank when it was not seen;
     * in that case a deterministic offline UUID is derived from the name.
     */
    public VanillaPlayer getOrCreate(String name, String uuidText) {
        VanillaPlayer existing = online.get(name);
        if (existing != null) {
            return existing;
        }
        UUID uuid = VanillaPlayer.resolveUuid(name, uuidText);
        VanillaPlayer created = new VanillaPlayer(name, uuid, localeStore.localeFor(name), sink);
        VanillaPlayer raced = online.putIfAbsent(name, created);
        return raced != null ? raced : created;
    }

    /** The online player with this name, if any. */
    public Optional<VanillaPlayer> find(String name) {
        return Optional.ofNullable(online.get(name));
    }

    /** Removes the player with this name. Returns the removed player, if any. */
    public Optional<VanillaPlayer> remove(String name) {
        return Optional.ofNullable(online.remove(name));
    }

    /** A snapshot of every player currently online. */
    public Collection<VanillaPlayer> onlinePlayers() {
        return List.copyOf(online.values());
    }

    /** Number of players currently online. */
    public int onlineCount() {
        return online.size();
    }
}
