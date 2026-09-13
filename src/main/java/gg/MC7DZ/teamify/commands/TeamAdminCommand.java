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

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      ConfigManager cm = this.plugin.getConfigManager();
      TeamManager tm = this.plugin.getTeamManager();
      if (!sender.hasPermission("teams.admin")) {
         sender.sendMessage(cm.getMessage("no-permission"));
         return true;
      }

      if (args.length == 0) {
         sender.sendMessage(cm.getPrefix().append(cm.color("<gray>Usage: /teamadmin <delete|reload|forcedisband> [team]")));
         return true;
      }

      String sub = args[0].toLowerCase();
      switch (sub) {
         case "reload":
            cm.reload();
            this.plugin.reloadUpdateConfig();
            if (this.plugin.getUpdateNotifier() != null) {
               this.plugin.getUpdateNotifier().check();
            }

            boolean economyHooked = this.plugin.getEconomyManager().setup();
            sender.sendMessage(
               cm.getPrefix()
                  .append(cm.color("<green>Config reloaded." + (economyHooked ? " <gray>(Vault economy hooked)" : " <gray>(Vault economy not available)")))
            );
            break;
         case "delete":
         case "forcedisband":
            if (args.length < 2) {
               sender.sendMessage(cm.getPrefix().append(cm.color("<red>Usage: /teamadmin " + sub + " <team>")));
               return true;
            }

            Team team = tm.getTeamByName(args[1]);
            if (team == null) {
               sender.sendMessage(cm.getPrefix().append(cm.color("<red>Team not found.")));
               return true;
            }

            tm.disbandTeam(team);
            sender.sendMessage(cm.getPrefix().append(cm.color("<green>Team <white>" + args[1] + " <green>was force-disbanded.")));
            break;
         default:
            sender.sendMessage(cm.getPrefix().append(cm.color("<red>Unknown subcommand.")));
      }

      return true;
   }
}
