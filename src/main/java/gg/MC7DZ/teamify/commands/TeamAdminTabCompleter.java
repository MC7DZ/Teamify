package gg.MC7DZ.teamify.commands;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TeamAdminTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("delete", "forcedisband", "reload");

    private final Teamify plugin;

    public TeamAdminTabCompleter(Teamify plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("teams.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("delete") || sub.equals("forcedisband")) {
                List<String> names = plugin.getTeamManager().getTeams().stream()
                        .map(Team::getName)
                        .collect(Collectors.toList());
                return filter(names, args[1]);
            }
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
