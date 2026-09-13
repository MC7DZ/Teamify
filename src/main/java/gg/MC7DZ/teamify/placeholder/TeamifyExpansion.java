package gg.MC7DZ.teamify.placeholder;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class TeamifyExpansion extends PlaceholderExpansion {
   private final Teamify plugin;

   public TeamifyExpansion(Teamify plugin) {
      this.plugin = plugin;
   }

   @NotNull
   public String getIdentifier() {
      return "teamify";
   }

   @NotNull
   public String getAuthor() {
      return "MC7DZ";
   }

   @NotNull
   public String getVersion() {
      return this.plugin.getDescription().getVersion();
   }

   public boolean persist() {
      return true;
   }

   public String onRequest(OfflinePlayer player, @NotNull String params) {
      if (player == null) {
         return "";
      }

      UUID uuid = player.getUniqueId();
      Team team = this.plugin.getTeamManager().getTeamOf(uuid);
      switch (params.toLowerCase()) {
         case "bank":
         case "bank_balance":
            if (team == null) {
               return this.plugin.getEconomyManager().format(0.0);
            }

            return this.plugin.getEconomyManager().format(team.getBankBalance());
         case "bank_raw":
         case "bank_balance_raw":
            return team == null ? "0" : String.valueOf(team.getBankBalance());
         case "team":
         case "team_name":
            return team == null ? "" : team.getName();
         case "tag":
            return team == null ? "" : team.getTag();
         case "team_colored":
         case "team_name_colored":
            return team == null ? "" : this.plugin.getConfigManager().colorTeamTextLegacy(team.getColorFormat(), team.getColor(), team.getName());
         case "tag_colored":
            return team == null ? "" : this.plugin.getConfigManager().colorTeamTextLegacy(team.getColorFormat(), team.getColor(), team.getTag());
         case "level":
            return team == null ? "0" : String.valueOf(team.getLevel());
         case "xp":
            return team == null ? "0" : String.valueOf(team.getXp());
         case "kills":
            return team == null ? "0" : String.valueOf(team.getTotalKills());
         case "kills_player":
            return team == null ? "0" : String.valueOf(team.getKills(uuid));
         case "members":
         case "member_count":
            return team == null ? "0" : String.valueOf(team.getSize());
         case "owner":
            if (team == null) {
               return "";
            }

            OfflinePlayer owner = this.plugin.getServer().getOfflinePlayer(team.getOwner());
            return owner.getName() == null ? "Unknown" : owner.getName();
         case "role":
            if (team == null) {
               return "";
            }

            TeamRole role = team.getRole(uuid);
            return role == null ? "" : role.name();
         case "pvp":
         case "pvp_status":
            if (team == null) {
               return "";
            }

            return team.isPvpEnabled() ? "Enabled" : "Disabled";
         case "has_team":
            return team != null ? "true" : "false";
         default:
            return null;
      }
   }
}
