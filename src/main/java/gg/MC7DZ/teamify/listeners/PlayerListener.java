package gg.MC7DZ.teamify.listeners;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {
   private final Teamify plugin;
   private final Set<UUID> teamChatToggled = new HashSet<>();
   private final Set<UUID> allyChatToggled = new HashSet<>();
   private final Map<UUID, PlayerListener.PendingInputType> pendingInput = new HashMap<>();

   public PlayerListener(Teamify plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.plugin.getVisibilityManager().refresh(event.getPlayer());
      this.plugin.getPlayerManager().updatePlayerData(event.getPlayer());
      this.plugin.getPlayerManager().savePlayers();
      this.notifyUpdateIfAvailable(event.getPlayer());
   }

   private void notifyUpdateIfAvailable(Player player) {
      if (this.plugin.getConfigManager().isUpdateCheckEnabled() && this.plugin.getConfigManager().isUpdateCheckNotifyOps()) {
         if (this.plugin.getUpdateChecker() != null && this.plugin.getUpdateChecker().isUpdateAvailable()) {
            if (player.isOp() || player.hasPermission("teamify.admin")) {
               player.sendMessage(
                  this.plugin
                     .getConfigManager()
                     .getPrefix()
                     .append(
                        this.plugin
                           .getConfigManager()
                           .color(
                              "<yellow>A new Teamify update is available: <white>"
                                 + this.plugin.getUpdateChecker().getLatestVersion()
                                 + " <yellow>(running <white>"
                                 + this.plugin.getDescription().getVersion()
                                 + "<yellow>)."
                           )
                     )
               );
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.teamChatToggled.remove(event.getPlayer().getUniqueId());
      this.allyChatToggled.remove(event.getPlayer().getUniqueId());
      this.pendingInput.remove(event.getPlayer().getUniqueId());
      this.plugin.getPlayerManager().updatePlayerData(event.getPlayer());
      this.plugin.getPlayerManager().savePlayers();
   }

   public void awaitInput(UUID uuid, PlayerListener.PendingInputType type) {
      this.pendingInput.put(uuid, type);
   }

   public void cancelPendingInput(UUID uuid) {
      this.pendingInput.remove(uuid);
   }

   public boolean isTeamChatToggled(UUID uuid) {
      return this.teamChatToggled.contains(uuid);
   }

   public void toggleTeamChat(UUID uuid) {
      if (!this.teamChatToggled.add(uuid)) {
         this.teamChatToggled.remove(uuid);
      }
   }

   public boolean isAllyChatToggled(UUID uuid) {
      return this.allyChatToggled.contains(uuid);
   }

   public void toggleAllyChat(UUID uuid) {
      if (!this.allyChatToggled.add(uuid)) {
         this.allyChatToggled.remove(uuid);
      }
   }

   @EventHandler
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      Team team = this.plugin.getTeamManager().getTeamOf(player.getUniqueId());
      String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
      PlayerListener.PendingInputType pending = this.pendingInput.remove(player.getUniqueId());
      if (pending != null) {
         event.setCancelled(true);
         String message = plainMessage.trim();
         this.plugin
            .getServer()
            .getScheduler()
            .runTask(
               this.plugin,
               () -> {
                  if (message.equalsIgnoreCase("cancel")) {
                     player.sendMessage(this.plugin.getConfigManager().getPrefix().append(this.plugin.getConfigManager().color("<gray>Cancelled.")));
                  } else if (team == null) {
                     player.sendMessage(this.plugin.getConfigManager().getMessage("no-team"));
                  } else {
                     switch (pending) {
                        case BANK_DEPOSIT:
                           this.plugin.getTeamCommand().bankDeposit(player, team, message);
                           break;
                        case BANK_WITHDRAW:
                           this.plugin.getTeamCommand().bankWithdraw(player, team, message);
                           break;
                        case TEAM_DESCRIPTION:
                           this.plugin.getTeamCommand().setDescription(player, team, message);
                           break;
                        case TEAM_TAG:
                           String newTag = message;
                           int minTagLength = this.plugin.getConfigManager().getMinTagLength();
                           int maxTagLength = this.plugin.getConfigManager().getMaxTagLength();
                           boolean allowColorCodes = this.plugin.getConfigManager().isAllowColorCodes();
                           if (newTag.length() < minTagLength || newTag.length() > maxTagLength) {
                              player.sendMessage(
                                 this.plugin
                                    .getConfigManager()
                                    .getMessage("invalid-tag-length", "min", String.valueOf(minTagLength), "max", String.valueOf(maxTagLength))
                              );
                              return;
                           }

                           if (!allowColorCodes) {
                              newTag = newTag.replaceAll("(?i)&([0-9a-fk-or])", "");
                           }

                           team.setTag(newTag);
                           this.plugin.getTeamManager().saveTeam(team);
                           player.sendMessage(this.plugin.getConfigManager().getMessage("team-tag-changed", "tag", newTag));
                     }
                  }
               }
            );
      } else {
         if (this.plugin.getConfigManager().isTeamChatEnabled() && this.teamChatToggled.contains(player.getUniqueId())) {
            if (team != null) {
               event.setCancelled(true);
               this.sendTeamChat(player, team, plainMessage);
               return;
            }

            this.teamChatToggled.remove(player.getUniqueId());
         }

         if (this.plugin.getConfigManager().isAlliesEnabled()
            && this.plugin.getConfigManager().isAllyChatEnabled()
            && this.allyChatToggled.contains(player.getUniqueId())) {
            if (team == null) {
               this.allyChatToggled.remove(player.getUniqueId());
            } else {
               event.setCancelled(true);
               this.sendAllyChat(player, team, plainMessage);
            }
         }
      }
   }

   private boolean isChatColorEnabled() {
      return this.plugin.getConfigManager().isColoredNamesEnabled() && this.plugin.getConfigManager().isColorShown(ConfigManager.ColorShow.CHAT);
   }

   private void sendTeamChat(Player player, Team team, String message) {
      String formatString = this.plugin.getConfigManager().getTeamChatFormat();
      String role = team.getRole(player.getUniqueId()).name();
      Component playerNameComponent;
      if (this.isChatColorEnabled()) {
         playerNameComponent = this.plugin.getConfigManager().color(this.plugin.getConfigManager().getTeammateColor().toString() + player.getName());
      } else {
         playerNameComponent = Component.text(player.getName());
      }

      Component out = this.plugin
         .getConfigManager()
         .color(formatString.replace("{role}", role).replace("{player}", playerNameComponent.insertion()).replace("{message}", message));

      for (UUID memberId : team.getMembers().keySet()) {
         Player member = this.plugin.getServer().getPlayer(memberId);
         if (member != null) {
            member.sendMessage(out);
         }
      }
   }

   private void sendAllyChat(Player player, Team team, String message) {
      String formatString = this.plugin.getConfigManager().getAllyChatFormat();
      String role = team.getRole(player.getUniqueId()).name();
      boolean chatColor = this.isChatColorEnabled();
      Component teammateNameComponent;
      if (chatColor) {
         teammateNameComponent = this.plugin.getConfigManager().color(this.plugin.getConfigManager().getTeammateColor().toString() + player.getName());
      } else {
         teammateNameComponent = Component.text(player.getName());
      }

      Component allyNameComponent;
      if (chatColor) {
         allyNameComponent = this.plugin.getConfigManager().color(this.plugin.getConfigManager().getAlliesColor().toString() + player.getName());
      } else {
         allyNameComponent = Component.text(player.getName());
      }

      Component outForTeam = this.plugin
         .getConfigManager()
         .color(
            formatString.replace("{role}", role)
               .replace("{player}", teammateNameComponent.insertion())
               .replace("{team}", team.getName())
               .replace("{message}", message)
         );
      Component outForAllies = this.plugin
         .getConfigManager()
         .color(
            formatString.replace("{role}", role)
               .replace("{player}", allyNameComponent.insertion())
               .replace("{team}", team.getName())
               .replace("{message}", message)
         );

      for (UUID memberId : team.getMembers().keySet()) {
         Player member = this.plugin.getServer().getPlayer(memberId);
         if (member != null) {
            member.sendMessage(outForTeam);
         }
      }

      for (Entry<UUID, RelationType> entry : team.getRelations().entrySet()) {
         if (entry.getValue() == RelationType.ALLY) {
            Team allyTeam = this.plugin.getTeamManager().getTeam(entry.getKey());
            if (allyTeam != null) {
               for (UUID memberId : allyTeam.getMembers().keySet()) {
                  Player member = this.plugin.getServer().getPlayer(memberId);
                  if (member != null) {
                     member.sendMessage(outForAllies);
                  }
               }
            }
         }
      }
   }

   public enum PendingInputType {
      BANK_DEPOSIT,
      BANK_WITHDRAW,
      TEAM_DESCRIPTION,
      TEAM_TAG;
   }
}
