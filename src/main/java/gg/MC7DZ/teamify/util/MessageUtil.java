package gg.MC7DZ.teamify.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
     * @param legacyLine  the main message, in legacy '&' color-code format
     * @param buttonLabel text shown on the clickable button (e.g. "&a[Click to accept]")
     * @param command     the command run when the button is clicked (e.g. "/team join Foo")
     * @param hoverLegacy text shown on hover, in legacy '&' color-code format
     */
    public static void sendClickableInvite(Player player, String legacyLine, String buttonLabel,
                                            String command, String hoverLegacy) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

        Component button = legacy.deserialize(buttonLabel)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(legacy.deserialize(hoverLegacy)));

        Component message = legacy.deserialize(legacyLine)
                .append(Component.space())
                .append(button);

        player.sendMessage(message);
    }

    /** Fallback plain-text send for when a caller only has legacy text handy. */
    public static void send(Player player, String legacyLine) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacyLine));
    }
}
