package gg.MC7DZ.teamify.commands;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TeamTabCompleter implements TabCompleter {
   private static final Set<String> PLAYER_ARG_SUBS = Set.of("invite", "kick", "promote", "demote", "transfer");
   private final Teamify plugin;

   public TeamTabCompleter(Teamify plugin) {
      this.plugin = plugin;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (sender instanceof Player player) {
         TeamManager tm = this.plugin.getTeamManager();
         if (args.length == 1) {
            return this.filter(TeamCommandAccess.getAvailable(this.plugin, player), args[0]);
         }

         if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (PLAYER_ARG_SUBS.contains(sub)) {
               Team team = tm.getTeamOf(player.getUniqueId());
               if (team == null) {
                  return Collections.emptyList();
               }

               if (sub.equals("invite")) {
                  List<String> names = Bukkit.getOnlinePlayers()
                     .stream()
                     .filter(p -> !tm.isInTeam(p.getUniqueId()))
                     .<String>map(Player::getName)
                     .collect(Collectors.toList());
                  return this.filter(names, args[1]);
               }

               List<String> names = team.getMembers()
                  .keySet()
                  .stream()
                  .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList());
               return this.filter(names, args[1]);
            }

            if (sub.equals("join")) {
               List<String> names = tm.getTeams().stream().filter(t -> t.hasInvite(player.getUniqueId())).map(Team::getName).collect(Collectors.toList());
               return this.filter(names, args[1]);
            }

            if (sub.equals("joinrequest")) {
               List<String> names = tm.getTeams().stream().map(Team::getName).collect(Collectors.toList());
               return this.filter(names, args[1]);
            }

            if (sub.equals("allyinvite")) {
               Team team = tm.getTeamOf(player.getUniqueId());
               if (team == null) {
                  return Collections.emptyList();
               }

               List<String> names = tm.getTeams().stream().filter(t -> !t.getId().equals(team.getId())).map(Team::getName).collect(Collectors.toList());
               return this.filter(names, args[1]);
            }

            if (sub.equals("allyleave")) {
               Team team = tm.getTeamOf(player.getUniqueId());
               if (team == null) {
                  return Collections.emptyList();
               }

               List<String> names = team.getRelations()
                  .entrySet()
                  .stream()
                  .filter(e -> e.getValue() == RelationType.ALLY)
                  .map(e -> tm.getTeam(e.getKey()))
                  .filter(Objects::nonNull)
                  .map(Team::getName)
                  .collect(Collectors.toList());
               return this.filter(names, args[1]);
            }

            if (sub.equals("create")) {
               return List.of("<name>");
            }

            if (sub.equals("bank")) {
               return this.filter(List.of("deposit", "withdraw", "balance"), args[1]);
            }

            if (sub.equals("description")) {
               return this.filter(List.of("clear"), args[1]);
            }
         }

         if (args.length == 3 && args[0].equalsIgnoreCase("bank")) {
            return List.of("<amount>");
         } else {
            return args.length == 3 && args[0].equalsIgnoreCase("create") ? List.of("<tag>") : Collections.emptyList();
         }
      } else {
         return Collections.emptyList();
      }
   }

   private List<String> filter(List<String> options, String input) {
      String lower = input.toLowerCase();
      return options.stream()
         .filter(Objects::nonNull)
         .filter(s -> s.toLowerCase().startsWith(lower))
         .sorted(String.CASE_INSENSITIVE_ORDER)
         .collect(Collectors.toList());
   }
}
