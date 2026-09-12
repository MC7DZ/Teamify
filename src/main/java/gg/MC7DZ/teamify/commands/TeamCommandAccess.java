package gg.MC7DZ.teamify.commands;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;

public final class TeamCommandAccess {
   public static final List<String> ALL_SUBCOMMANDS = List.of(
      "create",
      "invite",
      "kick",
      "leave",
      "disband",
      "home",
      "sethome",
      "chat",
      "info",
      "list",
      "gui",
      "join",
      "joinrequest",
      "requests",
      "promote",
      "demote",
      "transfer",
      "settings",
      "pvp",
      "allychat",
      "allyinvite",
      "allyleave",
      "bank",
      "description",
      "echest",
      "reload"
   );
   public static final List<String> NO_TEAM_SUBCOMMANDS = List.of("create", "join", "joinrequest", "list");

   private TeamCommandAccess() {
   }

   public static List<String> getAvailable(Teamify plugin, Player player) {
      Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
      List<String> result = new ArrayList<>();
      if (team == null) {
         for (String sub : NO_TEAM_SUBCOMMANDS) {
            if (hasAccess(plugin, player, sub, null)) {
               result.add(sub);
            }
         }

         return result;
      } else {
         for (String sub : ALL_SUBCOMMANDS) {
            if (hasAccess(plugin, player, sub, team)) {
               result.add(sub);
            }
         }

         return result;
      }
   }

   public static boolean hasAccess(Teamify plugin, Player player, String sub, Team team) {
      ConfigManager cm = plugin.getConfigManager();
      if (team == null) {
         team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
      }

      if (!player.hasPermission("teams.command." + sub)) {
         return false;
      }

      if (team == null) {
         return switch (sub) {
            case "create", "join" -> true;
            case "joinrequest" -> cm.isSendJoinRequestEnabled();
            case "list" -> cm.isListCommandEnabled();
            default -> false;
         };
      } else {
         TeamRole role = team.getRole(player.getUniqueId());
         boolean isOwner = player.getUniqueId().equals(team.getOwner());

         return switch (sub) {
            case "create" -> false;
            case "join" -> true;
            case "joinrequest" -> false;
            case "requests" -> true;
            case "info", "gui", "settings", "pvp", "leave", "disband" -> true;
            case "list" -> cm.isListCommandEnabled();
            case "home" -> cm.isHomeCommandEnabled();
            case "sethome" -> cm.isSetHomeCommandEnabled() && rolePerm(plugin, role, "can-set-home");
            case "invite" -> rolePerm(plugin, role, "can-invite");
            case "kick" -> rolePerm(plugin, role, "can-kick");
            case "promote", "demote" -> rolePerm(plugin, role, "can-promote");
            case "transfer" -> isOwner;
            case "chat" -> cm.isTeamChatEnabled();
            case "allyinvite", "allyleave" -> cm.isAlliesEnabled() && rolePerm(plugin, role, "can-manage-relations");
            case "bank" -> cm.isBankEnabled() && rolePerm(plugin, role, "can-access-bank");
            case "description" -> cm.isTeamDescriptionEnabled() && rolePerm(plugin, role, "can-edit-description");
            case "echest" -> cm.isEchestEnabled() && rolePerm(plugin, role, "can-access-echest");
            case "allychat" -> cm.isAlliesEnabled() && cm.isAllyChatEnabled();
            case "reload" -> player.hasPermission("teams.admin");
            default -> true;
         };
      }
   }

   private static boolean rolePerm(Teamify plugin, TeamRole role, String permKey) {
      return role == null ? false : plugin.getConfig().getBoolean("roles.permissions." + role.name() + "." + permKey, false);
   }
}
