package io.detergentmc.core.bridge;

import io.detergentmc.api.CommandSender;
import io.detergentmc.api.Translator;
import java.util.Locale;

/** A player as a {@link CommandSender}: replies go back to that player via tellraw. */
public final class PlayerCommandSender implements CommandSender {

    private final VanillaPlayer player;
    private final Translator translator;

    public PlayerCommandSender(VanillaPlayer player, Translator translator) {
        if (player == null) {
            throw new IllegalArgumentException("player is required");
        }
        if (translator == null) {
            throw new IllegalArgumentException("translator is required");
        }
        this.player = player;
        this.translator = translator;
    }

    /** The player behind this sender. */
    public VanillaPlayer player() {
        return player;
    }

    @Override
    public String name() {
        return player.name();
    }

    @Override
    public Locale locale() {
        return player.locale();
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(message);
    }

    @Override
    public void sendMessage(String key, Object... args) {
        player.sendMessage(translator.translate(key, player.locale(), args));
    }
}
