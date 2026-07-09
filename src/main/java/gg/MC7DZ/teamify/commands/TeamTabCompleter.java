package gg.MC7DZ.teamify.commands;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.team.TeamRole;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class TeamTabCompleter implements TabCompleter {

    // subcommands that take a player name as their argument
    private static final Set<String> PLAYER_ARG_SUBS = Set.of("invite", "kick", "promote", "demote", "transfer");

    private final Teamify plugin;

    public TeamTabCompleter(Teamify plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        TeamManager tm = plugin.getTeamManager();

        if (args.length == 1) {
            return filter(TeamCommandAccess.getAvailable(plugin, player), args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if (PLAYER_ARG_SUBS.contains(sub)) {
                Team team = tm.getTeamOf(player.getUniqueId());
                if (team == null) return Collections.emptyList();

                if (sub.equals("invite")) {
                    // suggest online players not already in a team
                    List<String> names = Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !tm.isInTeam(p.getUniqueId()))
                            .map(Player::getName)
                            .collect(Collectors.toList());
                    return filter(names, args[1]);
                } else {
                    // kick/promote/demote: suggest current team's members
                    List<String> names = team.getMembers().keySet().stream()
                            .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    return filter(names, args[1]);
                }
            }

            if (sub.equals("join")) {
                // suggest teams that have invited this player
                List<String> names = tm.getTeams().stream()
                        .filter(t -> t.hasInvite(player.getUniqueId()))
                        .map(Team::getName)
                        .collect(Collectors.toList());
                return filter(names, args[1]);
            }

            if (sub.equals("joinrequest")) {
                // suggest all team names (player isn't in a team yet)
                List<String> names = tm.getTeams().stream()
                        .map(Team::getName)
                        .collect(Collectors.toList());
                return filter(names, args[1]);
            }

            if (sub.equals("allyinvite")) {
                Team team = tm.getTeamOf(player.getUniqueId());
                if (team == null) return Collections.emptyList();
                // suggest any other team: pending invites to us first, otherwise all teams
                List<String> names = tm.getTeams().stream()
                        .filter(t -> !t.getId().equals(team.getId()))
                        .map(Team::getName)
                        .collect(Collectors.toList());
                return filter(names, args[1]);
            }

            if (sub.equals("allyleave")) {
                Team team = tm.getTeamOf(player.getUniqueId());
                if (team == null) return Collections.emptyList();
                List<String> names = team.getRelations().entrySet().stream()
                        .filter(e -> e.getValue() == gg.MC7DZ.teamify.team.RelationType.ALLY)
                        .map(e -> tm.getTeam(e.getKey()))
                        .filter(Objects::nonNull)
                        .map(Team::getName)
                        .collect(Collectors.toList());
                return filter(names, args[1]);
            }

            if (sub.equals("create")) {
                return List.of("<name>");
            }

            if (sub.equals("bank")) {
                return filter(List.of("deposit", "withdraw", "balance"), args[1]);
            }

            if (sub.equals("description")) {
                return filter(List.of("clear"), args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("bank")) {
            return List.of("<amount>");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return List.of("<tag>");
        }

        return Collections.emptyList();
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
