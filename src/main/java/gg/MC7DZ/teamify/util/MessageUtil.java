package gg.MC7DZ.teamify.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

/**
 * Builds clickable/hoverable chat messages using the Adventure API (bundled
 * with modern Paper), for things like "click to accept this invite" prompts.
 */
public final class MessageUtil {

    private MessageUtil() {
    }

    /**
     * Sends a message followed by a clickable "[Accept]" style prompt that
     * runs the given command when clicked.
     *
     * @param player      who receives the message
     * @param messageComponent  the main message as a Component
     * @param buttonLabelComponent text shown on the clickable button (as a Component)
     * @param command     the command run when the button is clicked (e.g. "/team join Foo")
     * @param hoverComponent text shown on hover (as a Component)
     */
    public static void sendClickableInvite(Player player, Component messageComponent, Component buttonLabelComponent,
                                            String command, Component hoverComponent) {
        Component button = buttonLabelComponent
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(hoverComponent));

        Component message = messageComponent
                .append(Component.space())
                .append(button);

        player.sendMessage(message);
    }

    /** Sends a Component message to the player. */
    public static void send(Player player, Component message) {
        player.sendMessage(message);
    }
}