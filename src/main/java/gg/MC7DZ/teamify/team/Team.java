package gg.MC7DZ.teamify.team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class Team {
   private final UUID id;
   private String name;
   private String tag;
   private String description;
   private UUID owner;
   private final Map<UUID, TeamRole> members = new LinkedHashMap<>();
   private final Map<UUID, Integer> kills = new HashMap<>();
   private final Map<UUID, RelationType> relations = new HashMap<>();
   private final Map<Integer, Location> homes = new HashMap<>();
   private final List<UUID> pendingInvites = new ArrayList<>();
   private final List<UUID> pendingAllyInvites = new ArrayList<>();
   private final List<UUID> pendingJoinRequests = new ArrayList<>();
   private double bankBalance;
   private double creationCostPaid = 0.0;
   private int level = 1;
   private long xp = 0L;
   private boolean teamChatToggleDefault = false;
   private boolean pvpEnabled = false;
   private long createdAt;
   private ChatColor color = ChatColor.WHITE;
   private ItemStack customItem;
   private ItemStack[] echestContents = new ItemStack[54];

   public Team(UUID id, String name, String tag, UUID owner) {
      this.id = id;
      this.name = name;
      this.tag = tag;
      this.owner = owner;
      this.createdAt = System.currentTimeMillis();
      this.members.put(owner, TeamRole.LEADER);
   }

   public UUID getId() {
      return this.id;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getTag() {
      return this.tag;
   }

   public void setTag(String tag) {
      this.tag = tag;
   }

   public String getDescription() {
      return this.description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public UUID getOwner() {
      return this.owner;
   }

   public void setOwner(UUID owner) {
      this.owner = owner;
   }

   public Map<UUID, TeamRole> getMembers() {
      return this.members;
   }

   public void addMember(UUID uuid, TeamRole role) {
      this.members.put(uuid, role);
   }

   public void removeMember(UUID uuid) {
      this.members.remove(uuid);
   }

   public boolean isMember(UUID uuid) {
      return this.members.containsKey(uuid);
   }

   public TeamRole getRole(UUID uuid) {
      return this.members.get(uuid);
   }

   public void setRole(UUID uuid, TeamRole role) {
      this.members.put(uuid, role);
   }

   public Map<UUID, Integer> getKills() {
      return this.kills;
   }

   public int getKills(UUID uuid) {
      return this.kills.getOrDefault(uuid, 0);
   }

   public void setKills(UUID uuid, int amount) {
      this.kills.put(uuid, amount);
   }

   public void addKill(UUID uuid) {
      this.kills.merge(uuid, 1, Integer::sum);
   }

   public int getTotalKills() {
      int total = 0;

      for (int amount : this.kills.values()) {
         total += amount;
      }

      return total;
   }

   public Map<UUID, RelationType> getRelations() {
      return this.relations;
   }

   public RelationType getRelation(UUID otherTeamId) {
      return this.relations.getOrDefault(otherTeamId, RelationType.NEUTRAL);
   }

   public void setRelation(UUID otherTeamId, RelationType type) {
      if (type == RelationType.NEUTRAL) {
         this.relations.remove(otherTeamId);
      } else {
         this.relations.put(otherTeamId, type);
      }
   }

   public Map<Integer, Location> getHomes() {
      return this.homes;
   }

   public Location getHome(int index) {
      return this.homes.get(index);
   }

   public void setHome(int index, Location loc) {
      this.homes.put(index, loc);
   }

   public List<UUID> getPendingInvites() {
      return this.pendingInvites;
   }

   public void addInvite(UUID uuid) {
      if (!this.pendingInvites.contains(uuid)) {
         this.pendingInvites.add(uuid);
      }
   }

   public void removeInvite(UUID uuid) {
      this.pendingInvites.remove(uuid);
   }

   public boolean hasInvite(UUID uuid) {
      return this.pendingInvites.contains(uuid);
   }

   public List<UUID> getPendingAllyInvites() {
      return this.pendingAllyInvites;
   }

   public void addAllyInvite(UUID teamId) {
      if (!this.pendingAllyInvites.contains(teamId)) {
         this.pendingAllyInvites.add(teamId);
      }
   }

   public void removeAllyInvite(UUID teamId) {
      this.pendingAllyInvites.remove(teamId);
   }

   public boolean hasAllyInvite(UUID teamId) {
      return this.pendingAllyInvites.contains(teamId);
   }

   public List<UUID> getPendingJoinRequests() {
      return this.pendingJoinRequests;
   }

   public void addJoinRequest(UUID player) {
      if (!this.pendingJoinRequests.contains(player)) {
         this.pendingJoinRequests.add(player);
      }
   }

   public void removeJoinRequest(UUID player) {
      this.pendingJoinRequests.remove(player);
   }

   public boolean hasJoinRequest(UUID player) {
      return this.pendingJoinRequests.contains(player);
   }

   public int getAllyCount() {
      int count = 0;

      for (RelationType type : this.relations.values()) {
         if (type == RelationType.ALLY) {
            count++;
         }
      }

      return count;
   }

   public double getBankBalance() {
      return this.bankBalance;
   }

   public void setBankBalance(double bankBalance) {
      this.bankBalance = bankBalance;
   }

   public double getCreationCostPaid() {
      return this.creationCostPaid;
   }

   public void setCreationCostPaid(double creationCostPaid) {
      this.creationCostPaid = creationCostPaid;
   }

   public double deposit(double amount, double maxBalance) {
      if (amount <= 0.0) {
         return 0.0;
      }

      double room = maxBalance > 0.0 ? Math.max(0.0, maxBalance - this.bankBalance) : amount;
      double toAdd = Math.min(amount, room);
      this.bankBalance += toAdd;
      return toAdd;
   }

   public void deposit(double amount) {
      this.bankBalance += amount;
   }

   public boolean withdraw(double amount) {
      if (!(amount <= 0.0) && !(this.bankBalance < amount)) {
         this.bankBalance -= amount;
         return true;
      } else {
         return false;
      }
   }

   public ChatColor getColor() {
      return this.color == null ? ChatColor.WHITE : this.color;
   }

   public void setColor(ChatColor color) {
      this.color = color == null ? ChatColor.WHITE : color;
   }

   public String getColoredName() {
      return this.getColor() + this.name;
   }

   public ItemStack getCustomItem() {
      return this.customItem;
   }

   public void setCustomItem(ItemStack customItem) {
      this.customItem = customItem;
   }

   public boolean hasCustomItem() {
      return this.customItem != null;
   }

   public int getLevel() {
      return this.level;
   }

   public void setLevel(int level) {
      this.level = level;
   }

   public long getXp() {
      return this.xp;
   }

   public void setXp(long xp) {
      this.xp = xp;
   }

   public void addXp(long amount) {
      this.xp += amount;
   }

   public boolean isTeamChatToggleDefault() {
      return this.teamChatToggleDefault;
   }

   public void setTeamChatToggleDefault(boolean teamChatToggleDefault) {
      this.teamChatToggleDefault = teamChatToggleDefault;
   }

   public boolean isPvpEnabled() {
      return this.pvpEnabled;
   }

   public void setPvpEnabled(boolean pvpEnabled) {
      this.pvpEnabled = pvpEnabled;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public void setCreatedAt(long createdAt) {
      this.createdAt = createdAt;
   }

   public int getSize() {
      return this.members.size();
   }

   public ItemStack[] getEchestContents() {
      return this.echestContents;
   }

   public void setEchestContents(ItemStack[] contents) {
      ItemStack[] full = new ItemStack[54];
      if (contents != null) {
         System.arraycopy(contents, 0, full, 0, Math.min(contents.length, 54));
      }

      this.echestContents = full;
   }
}
