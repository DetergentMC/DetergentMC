package io.detergentmc.core.bridge;

import io.detergentmc.api.Command;
import io.detergentmc.api.CommandSender;
import io.detergentmc.api.Translator;
import java.util.Locale;

/**
 * The built-in {@code /lang <ja|en>} command: switches the sender's own
 * language and persists the choice in the {@link PlayerLocaleStore}, so it
 * survives restarts. Only players can use it; the console has no
 * per-player language to switch.
 */
public final class LangCommand implements Command {

    private final Translator translator;
    private final PlayerRegistry players;

    public LangCommand(Translator translator, PlayerRegistry players) {
        if (translator == null) {
            throw new IllegalArgumentException("translator is required");
        }
        if (players == null) {
            throw new IllegalArgumentException("player registry is required");
        }
        this.translator = translator;
        this.players = players;
    }

    @Override
    public String name() {
        return "lang";
    }

    @Override
    public String description() {
        return "Switches your language / あなたの言語を切り替えます (ja / en)";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof PlayerCommandSender playerSender)) {
            sender.sendMessage(translator.translate(
                    "command.lang.usage", sender.locale()));
            return;
        }
        if (args == null || args.length != 1) {
            // Translate explicitly: sendMessage(key) with a single String
            // argument would resolve to the plain-text overload and send
            // the key itself, not the translated message.
            sender.sendMessage(translator.translate("command.lang.usage", sender.locale()));
            return;
        }
        Locale locale = PlayerLocaleStore.parseSupported(args[0]);
        if (locale == null) {
            sender.sendMessage(translator.translate(
                    "command.lang.unknown", sender.locale(), args[0]));
            return;
        }
        VanillaPlayer player = playerSender.player();
        players.localeStore().setLocale(player.name(), locale);
        player.setLocale(locale);
        // The confirmation itself is sent in the newly chosen language.
        sender.sendMessage(translator.translate("command.lang.set", locale, locale.getLanguage()));
    }
}
