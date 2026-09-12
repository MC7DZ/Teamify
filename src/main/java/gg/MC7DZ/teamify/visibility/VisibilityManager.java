package gg.MC7DZ.teamify.visibility;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import java.util.EnumSet;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public class VisibilityManager {
   private static final String MATES_TEAM_NAME = "teamify_mates";
   private static final String MATES_VISIBLE_NAMES_SUFFIX = "_visible_names";
   private static final String MATES_HIDDEN_NAMES_SUFFIX = "_hidden_names";
   private static final String ALLIES_TEAM_NAME = "teamify_allies";
   private static final String ENEMIES_TEAM_NAME = "teamify_enemies";
   private final Teamify plugin;

   public VisibilityManager(Teamify plugin) {
      this.plugin = plugin;
   }

   public void refresh(Player viewer) {
      Team team = this.plugin.getTeamManager().getTeamOf(viewer.getUniqueId());
      if (team == null) {
         viewer.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
      } else {
         boolean seeMembers = this.plugin.getConfigManager().isSeeMembersWhenInvis();
         boolean coloredNames = this.plugin.getConfigManager().isColoredNamesEnabled();
         EnumSet<ConfigManager.ColorShow> colorShows = this.plugin.getConfigManager().getColorShows();
         boolean applyColor = coloredNames && (colorShows.contains(ConfigManager.ColorShow.NAMETAG) || colorShows.contains(ConfigManager.ColorShow.TAB));
         boolean enemiesEnabled = this.plugin.getConfigManager().isEnemiesEnabled();
         boolean hideNamesWhenInvisible = this.plugin.getConfigManager().isHideNamesWhenInvisible();
         OptionStatus visibleNameTagVisibility = OptionStatus.NEVER;
         if (colorShows.contains(ConfigManager.ColorShow.NAMETAG)) {
            visibleNameTagVisibility = OptionStatus.ALWAYS;
         }

         if (!seeMembers && !applyColor && !hideNamesWhenInvisible) {
            viewer.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
         } else {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
               Scoreboard board = manager.getNewScoreboard();
               if (hideNamesWhenInvisible) {
                  org.bukkit.scoreboard.Team matesVisibleNamesTeam = board.registerNewTeam("teamify_mates_visible_names");
                  matesVisibleNamesTeam.setOption(Option.NAME_TAG_VISIBILITY, visibleNameTagVisibility);
                  matesVisibleNamesTeam.setCanSeeFriendlyInvisibles(seeMembers);
                  if (applyColor) {
                     matesVisibleNamesTeam.setColor(this.plugin.getConfigManager().getTeammateColor());
                  }

                  org.bukkit.scoreboard.Team matesHiddenNamesTeam = board.registerNewTeam("teamify_mates_hidden_names");
                  matesHiddenNamesTeam.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.NEVER);
                  matesHiddenNamesTeam.setCanSeeFriendlyInvisibles(seeMembers);
                  if (applyColor) {
                     matesHiddenNamesTeam.setColor(this.plugin.getConfigManager().getTeammateColor());
                  }

                  if (viewer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                     this.addEntrySafe(matesHiddenNamesTeam, viewer.getName());
                  } else {
                     this.addEntrySafe(matesVisibleNamesTeam, viewer.getName());
                  }

                  for (UUID memberId : team.getMembers().keySet()) {
                     Player member = Bukkit.getPlayer(memberId);
                     if (member != null && !member.equals(viewer)) {
                        if (member.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                           this.addEntrySafe(matesHiddenNamesTeam, member.getName());
                        } else {
                           this.addEntrySafe(matesVisibleNamesTeam, member.getName());
                        }
                     }
                  }
               } else {
                  org.bukkit.scoreboard.Team matesTeam = board.registerNewTeam("teamify_mates");
                  matesTeam.setOption(Option.NAME_TAG_VISIBILITY, visibleNameTagVisibility);
                  matesTeam.setCanSeeFriendlyInvisibles(seeMembers);
                  if (applyColor) {
                     matesTeam.setColor(this.plugin.getConfigManager().getTeammateColor());
                  }

                  this.addEntrySafe(matesTeam, viewer.getName());

                  for (UUID memberId : team.getMembers().keySet()) {
                     Player member = Bukkit.getPlayer(memberId);
                     if (member != null && !member.equals(viewer)) {
                        this.addEntrySafe(matesTeam, member.getName());
                     }
                  }
               }

               org.bukkit.scoreboard.Team alliesTeam = board.registerNewTeam("teamify_allies");
               alliesTeam.setOption(Option.NAME_TAG_VISIBILITY, visibleNameTagVisibility);
               alliesTeam.setCanSeeFriendlyInvisibles(false);
               if (applyColor) {
                  alliesTeam.setColor(this.plugin.getConfigManager().getAlliesColor());
               }

               for (Entry<UUID, RelationType> entry : team.getRelations().entrySet()) {
                  if (entry.getValue() == RelationType.ALLY) {
                     Team allyTeam = this.plugin.getTeamManager().getTeam(entry.getKey());
                     if (allyTeam != null) {
                        for (UUID memberId : allyTeam.getMembers().keySet()) {
                           Player member = Bukkit.getPlayer(memberId);
                           if (member != null) {
                              this.addEntrySafe(alliesTeam, member.getName());
                           }
                        }
                     }
                  }
               }

               if (applyColor && enemiesEnabled) {
                  org.bukkit.scoreboard.Team enemiesTeam = board.registerNewTeam("teamify_enemies");
                  enemiesTeam.setOption(Option.NAME_TAG_VISIBILITY, visibleNameTagVisibility);
                  enemiesTeam.setCanSeeFriendlyInvisibles(false);
                  enemiesTeam.setColor(this.plugin.getConfigManager().getEnemysColor());

                  for (Entry<UUID, RelationType> entry : team.getRelations().entrySet()) {
                     if (entry.getValue() == RelationType.ENEMY) {
                        Team enemyTeam = this.plugin.getTeamManager().getTeam(entry.getKey());
                        if (enemyTeam != null) {
                           for (UUID memberId : enemyTeam.getMembers().keySet()) {
                              Player member = Bukkit.getPlayer(memberId);
                              if (member != null) {
                                 this.addEntrySafe(enemiesTeam, member.getName());
                              }
                           }
                        }
                     }
                  }
               }

               viewer.setScoreboard(board);
            }
         }
      }
   }

   private void addEntrySafe(org.bukkit.scoreboard.Team sbTeam, String entry) {
      if (!sbTeam.hasEntry(entry)) {
         sbTeam.addEntry(entry);
      }
   }

   public void refreshAll() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.refresh(player);
      }
   }

   public void refreshTeamAndAllies(Team team) {
      if (team != null) {
         for (UUID memberId : team.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
               this.refresh(p);
            }
         }

         for (Entry<UUID, RelationType> entry : team.getRelations().entrySet()) {
            if (entry.getValue() == RelationType.ALLY || entry.getValue() == RelationType.ENEMY) {
               Team relatedTeam = this.plugin.getTeamManager().getTeam(entry.getKey());
               if (relatedTeam != null) {
                  for (UUID memberId : relatedTeam.getMembers().keySet()) {
                     Player p = Bukkit.getPlayer(memberId);
                     if (p != null) {
                        this.refresh(p);
                     }
                  }
               }
            }
         }
      }
   }

   public void reset(Player player) {
      ScoreboardManager manager = Bukkit.getScoreboardManager();
      if (manager != null) {
         player.setScoreboard(manager.getMainScoreboard());
      }
   }
}
