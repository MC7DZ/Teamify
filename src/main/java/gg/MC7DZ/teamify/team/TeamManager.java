package gg.MC7DZ.teamify.team;

import gg.MC7DZ.teamify.Teamify;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public class TeamManager {
   private final Teamify plugin;
   private final Map<UUID, Team> teamsById = new HashMap<>();
   private final Map<UUID, UUID> memberToTeam = new HashMap<>();
   private final Map<UUID, Long> creationCooldowns = new HashMap<>();
   private final Map<UUID, Long> disbandCooldowns = new HashMap<>();
   private File dataFolder;

   public TeamManager(Teamify plugin) {
      this.plugin = plugin;
      this.dataFolder = new File(plugin.getDataFolder(), "teams");
      if (!this.dataFolder.exists()) {
         this.dataFolder.mkdirs();
      }
   }

   public Collection<Team> getTeams() {
      return this.teamsById.values();
   }

   public Team getTeam(UUID id) {
      return this.teamsById.get(id);
   }

   public Team getTeamByName(String name) {
      for (Team t : this.teamsById.values()) {
         if (t.getName().equalsIgnoreCase(name)) {
            return t;
         }
      }

      return null;
   }

   public Team getTeamOf(UUID player) {
      UUID teamId = this.memberToTeam.get(player);
      return teamId == null ? null : this.teamsById.get(teamId);
   }

   public boolean isInTeam(UUID player) {
      return this.memberToTeam.containsKey(player);
   }

   public List<Team> getPendingTeamInvites(UUID playerUuid) {
      List<Team> invites = new ArrayList<>();

      for (Team team : this.teamsById.values()) {
         if (team.hasInvite(playerUuid)) {
            invites.add(team);
         }
      }

      return invites;
   }

   public Team createTeam(String name, String tag, UUID owner) {
      Team team = new Team(UUID.randomUUID(), name, tag, owner);
      this.teamsById.put(team.getId(), team);
      this.memberToTeam.put(owner, team.getId());
      return team;
   }

   public void disbandTeam(Team team) {
      for (UUID member : team.getMembers().keySet()) {
         this.memberToTeam.remove(member);
      }

      this.teamsById.remove(team.getId());
      this.disbandCooldowns.put(team.getOwner(), System.currentTimeMillis());
      File f = this.teamFile(team);
      if (f.exists()) {
         f.delete();
      }
   }

   public void addMember(Team team, UUID player, TeamRole role) {
      team.addMember(player, role);
      this.memberToTeam.put(player, team.getId());

      for (Team other : this.teamsById.values()) {
         if (!other.getId().equals(team.getId()) && other.hasJoinRequest(player)) {
            other.removeJoinRequest(player);
            this.saveTeam(other);
         }
      }
   }

   public void removeMember(Team team, UUID player) {
      team.removeMember(player);
      this.memberToTeam.remove(player);
   }

   public TeamManager.AllyInviteResult requestAlly(Team from, Team to, boolean mutualRequired) {
      if (mutualRequired && !from.hasAllyInvite(to.getId())) {
         to.addAllyInvite(from.getId());
         this.saveTeam(to);
         return TeamManager.AllyInviteResult.INVITE_SENT;
      } else {
         this.setAllied(from, to);
         from.removeAllyInvite(to.getId());
         to.removeAllyInvite(from.getId());
         return TeamManager.AllyInviteResult.ALLY_ADDED;
      }
   }

   public void setAllied(Team a, Team b) {
      a.setRelation(b.getId(), RelationType.ALLY);
      b.setRelation(a.getId(), RelationType.ALLY);
      this.saveTeam(a);
      this.saveTeam(b);
   }

   public void removeAlly(Team a, Team b) {
      a.setRelation(b.getId(), RelationType.NEUTRAL);
      b.setRelation(a.getId(), RelationType.NEUTRAL);
      this.saveTeam(a);
      this.saveTeam(b);
   }

   public boolean areAllied(Team a, Team b) {
      return a.getRelation(b.getId()) == RelationType.ALLY;
   }

   public long getCreationCooldownRemaining(UUID player, int cooldownSeconds) {
      Long last = this.creationCooldowns.get(player);
      if (last == null) {
         return 0L;
      }

      long elapsed = (System.currentTimeMillis() - last) / 1000L;
      return Math.max(0L, cooldownSeconds - elapsed);
   }

   public void markCreationCooldown(UUID player) {
      this.creationCooldowns.put(player, System.currentTimeMillis());
   }

   public long getDisbandCooldownRemaining(UUID player, int cooldownSeconds) {
      Long last = this.disbandCooldowns.get(player);
      if (last == null) {
         return 0L;
      }

      long elapsed = (System.currentTimeMillis() - last) / 1000L;
      return Math.max(0L, cooldownSeconds - elapsed);
   }

   public void loadAll() {
      if (this.dataFolder.listFiles() != null) {
         for (File file : Objects.requireNonNull(this.dataFolder.listFiles((d, n) -> n.endsWith(".yml")))) {
            try {
               YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
               String name = cfg.getString("name");
               String tag = cfg.getString("tag");
               UUID owner = UUID.fromString(cfg.getString("owner"));
               String idStr = cfg.getString("id");
               UUID id;
               if (idStr != null) {
                  id = UUID.fromString(idStr);
               } else {
                  id = UUID.fromString(file.getName().replace(".yml", ""));
               }

               Team team = new Team(id, name, tag, owner);
               team.setDescription(cfg.getString("description"));
               team.setBankBalance(cfg.getDouble("bank-balance", 0.0));
               team.setCreationCostPaid(cfg.getDouble("creation-cost-paid", 0.0));
               team.setLevel(cfg.getInt("level", 1));
               team.setXp(cfg.getLong("xp", 0L));
               team.setCreatedAt(cfg.getLong("created-at", System.currentTimeMillis()));
               team.setPvpEnabled(cfg.getBoolean("pvp-enabled", false));
               String colorName = cfg.getString("color");
               if (colorName != null) {
                  try {
                     team.setColor(ChatColor.valueOf(colorName));
                  } catch (IllegalArgumentException ignored) {
                  }
               }

               if (cfg.get("custom-item") instanceof ItemStack itemStack) {
                  team.setCustomItem(itemStack);
               }

               for (String uuidStr : cfg.getStringList("pending-ally-invites")) {
                  try {
                     team.addAllyInvite(UUID.fromString(uuidStr));
                  } catch (IllegalArgumentException ignored) {
                  }
               }

               for (String uuidStr : cfg.getStringList("pending-join-requests")) {
                  try {
                     team.addJoinRequest(UUID.fromString(uuidStr));
                  } catch (IllegalArgumentException ignored) {
                  }
               }

               team.getMembers().clear();
               ConfigurationSection membersSec = cfg.getConfigurationSection("members");
               if (membersSec != null) {
                  for (String key : membersSec.getKeys(false)) {
                     UUID memberId = UUID.fromString(key);
                     TeamRole role = TeamRole.valueOf(membersSec.getString(key));
                     team.getMembers().put(memberId, role);
                     this.memberToTeam.put(memberId, id);
                  }
               }

               ConfigurationSection relSec = cfg.getConfigurationSection("relations");
               if (relSec != null) {
                  for (String key : relSec.getKeys(false)) {
                     team.getRelations().put(UUID.fromString(key), RelationType.valueOf(relSec.getString(key)));
                  }
               }

               ConfigurationSection homesSec = cfg.getConfigurationSection("homes");
               if (homesSec != null) {
                  for (String key : homesSec.getKeys(false)) {
                     Location loc = homesSec.getLocation(key);
                     team.getHomes().put(Integer.parseInt(key), loc);
                  }
               }

               ConfigurationSection killsSec = cfg.getConfigurationSection("kills");
               if (killsSec != null) {
                  for (String key : killsSec.getKeys(false)) {
                     try {
                        team.setKills(UUID.fromString(key), killsSec.getInt(key));
                     } catch (IllegalArgumentException ignored) {
                     }
                  }
               }

               ConfigurationSection echestSec = cfg.getConfigurationSection("echest");
               if (echestSec != null) {
                  ItemStack[] echest = team.getEchestContents();

                  for (String key : echestSec.getKeys(false)) {
                     try {
                        int idx = Integer.parseInt(key);
                        if (idx >= 0 && idx < echest.length && echestSec.get(key) instanceof ItemStack item) {
                           echest[idx] = item;
                        }
                     } catch (NumberFormatException ignored) {
                     }
                  }
               }

               this.teamsById.put(id, team);
               File expected = this.teamFile(team);
               if (!file.equals(expected)) {
                  file.delete();
                  this.saveTeam(team);
               }
            } catch (Exception ex) {
               this.plugin.getLogger().warning("Failed to load team file " + file.getName() + ": " + ex.getMessage());
            }
         }
      }
   }

   public void saveAll() {
      for (Team team : this.teamsById.values()) {
         this.saveTeam(team);
      }
   }

   public void saveTeam(Team team) {
      YamlConfiguration cfg = new YamlConfiguration();
      cfg.set("id", team.getId().toString());
      cfg.set("name", team.getName());
      cfg.set("tag", team.getTag());
      cfg.set("owner", team.getOwner().toString());
      cfg.set("description", team.getDescription());
      cfg.set("bank-balance", team.getBankBalance());
      cfg.set("level", team.getLevel());
      cfg.set("xp", team.getXp());
      cfg.set("created-at", team.getCreatedAt());
      cfg.set("pvp-enabled", team.isPvpEnabled());
      cfg.set("color", team.getColor().name());
      if (team.hasCustomItem()) {
         cfg.set("custom-item", team.getCustomItem());
      }

      List<String> allyInvites = new ArrayList<>();

      for (UUID id : team.getPendingAllyInvites()) {
         allyInvites.add(id.toString());
      }

      cfg.set("pending-ally-invites", allyInvites);
      List<String> joinRequests = new ArrayList<>();

      for (UUID id : team.getPendingJoinRequests()) {
         joinRequests.add(id.toString());
      }

      cfg.set("pending-join-requests", joinRequests);

      for (Entry<UUID, TeamRole> e : team.getMembers().entrySet()) {
         cfg.set("members." + e.getKey(), e.getValue().name());
      }

      for (Entry<UUID, RelationType> e : team.getRelations().entrySet()) {
         cfg.set("relations." + e.getKey(), e.getValue().name());
      }

      for (Entry<Integer, Location> e : team.getHomes().entrySet()) {
         cfg.set("homes." + e.getKey(), e.getValue());
      }

      for (Entry<UUID, Integer> e : team.getKills().entrySet()) {
         cfg.set("kills." + e.getKey(), e.getValue());
      }

      ItemStack[] echest = team.getEchestContents();
      boolean anyEchestItem = false;

      for (ItemStack item : echest) {
         if (item != null) {
            anyEchestItem = true;
            break;
         }
      }

      if (anyEchestItem) {
         for (int i = 0; i < echest.length; i++) {
            if (echest[i] != null) {
               cfg.set("echest." + i, echest[i]);
            }
         }
      }

      try {
         cfg.save(this.teamFile(team));
      } catch (IOException e) {
         this.plugin.getLogger().warning("Failed to save team " + team.getName() + ": " + e.getMessage());
      }
   }

   public void renameTeamFile(Team team, String oldName) {
      File oldFile = new File(this.dataFolder, this.sanitizeFileName(oldName) + ".yml");
      if (oldFile.exists()) {
         oldFile.delete();
      }

      this.saveTeam(team);
   }

   private File teamFile(Team team) {
      return new File(this.dataFolder, this.sanitizeFileName(team.getName()) + ".yml");
   }

   private String sanitizeFileName(String name) {
      return name == null ? "unnamed" : name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
   }

   public enum AllyInviteResult {
      ALLY_ADDED,
      INVITE_SENT;
   }
}
