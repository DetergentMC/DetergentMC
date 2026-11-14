package io.detergentmc.bubble.hello;

import io.detergentmc.api.Bubble;
import io.detergentmc.api.BubbleContext;
import io.detergentmc.api.Command;
import io.detergentmc.api.CommandSender;
import io.detergentmc.api.PlayerJoinEvent;
import io.detergentmc.api.Subscribe;

/**
 * HelloBubble - the sample Bubble for DetergentMC.
 *
 * <p>It does three small things, each showing one piece of the API:
 * <ul>
 *   <li>logs a message when enabled / disabled,</li>
 *   <li>registers a {@code /hello} command that greets the sender in
 *       the sender's own language,</li>
 *   <li>listens for {@link PlayerJoinEvent} and logs the join message.</li>
 * </ul>
 *
 * <p>Copy this Bubble to start your own: change {@code bubble.yml}
 * (id, name, main class), rename the class, and write your own
 * {@link Bubble#onEnable} logic.
 */
public class HelloBubble implements Bubble {

    private BubbleContext context;

    @Override
    public void onEnable(BubbleContext context) {
        this.context = context;
        context.logger().info(() -> context.translator().translate(
                "bubble.enabled", context.info().id(), context.info().version()));

        context.commands().register(new Command() {
            @Override
            public String name() {
                return "hello";
            }

            @Override
            public String description() {
                return "Greets you in your language / あなたの言語であいさつします";
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                String target = args.length > 0 ? String.join(" ", args) : sender.name();
                sender.sendMessage(context.translator().translate(
                        "command.hello", sender.locale(), target));
            }
        });

        context.events().register(this);
    }

    @Subscribe
    public void onPlayerJoin(PlayerJoinEvent event) {
        context.logger().info(() -> context.translator().translate(
                "player.join", event.player().locale(), event.player().name()));
        // Welcome only the joining player themself (never broadcast):
        // vanilla already announces the join to everyone.
        event.player().sendMessage(context.translator().translate(
                "player.join", event.player().locale(), event.player().name()));
    }

    @Override
    public void onDisable() {
        if (context != null) {
            context.logger().info(() -> context.translator().translate(
                    "bubble.disabled", context.info().id()));
        }
    }
}
