package gg.MC7DZ.teamify.commands;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class TeamAdminCommand implements CommandExecutor {

    private final Teamify plugin;

    public TeamAdminCommand(Teamify plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        if (!sender.hasPermission("teams.admin")) {
            sender.sendMessage(cm.getMessage("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(cm.getPrefix() + cm.color("&7Usage: /teamadmin <delete|reload|forcedisband> [team]"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                cm.reload();
                sender.sendMessage(cm.getPrefix() + cm.color("&aConfig reloaded."));
            }
            case "delete", "forcedisband" -> {
                if (args.length < 2) {
                    sender.sendMessage(cm.getPrefix() + cm.color("&cUsage: /teamadmin " + sub + " <team>"));
                    return true;
                }
                Team team = tm.getTeamByName(args[1]);
                if (team == null) {
                    sender.sendMessage(cm.getPrefix() + cm.color("&cTeam not found."));
                    return true;
                }
                tm.disbandTeam(team);
                sender.sendMessage(cm.getPrefix() + cm.color("&aTeam &f" + args[1] + " &awas force-disbanded."));
            }
            default -> sender.sendMessage(cm.getPrefix() + cm.color("&cUnknown subcommand."));
        }
        return true;
    }
}
