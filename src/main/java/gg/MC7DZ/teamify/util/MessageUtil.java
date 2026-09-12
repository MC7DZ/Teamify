package gg.MC7DZ.teamify.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

public final class MessageUtil {
   private MessageUtil() {
   }

   public static void sendClickableInvite(Player player, Component messageComponent, Component buttonLabelComponent, String command, Component hoverComponent) {
      Component button = buttonLabelComponent.clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(hoverComponent));
      Component message = messageComponent.append(Component.space()).append(button);
      player.sendMessage(message);
   }

   public static void send(Player player, Component message) {
      player.sendMessage(message);
   }
}
