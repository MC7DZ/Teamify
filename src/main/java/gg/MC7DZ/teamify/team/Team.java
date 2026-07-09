package gg.MC7DZ.teamify.team;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.*;

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
    private long xp = 0;
    private boolean teamChatToggleDefault = false;
    private boolean pvpEnabled = true;
    private long createdAt;

    // Customization: a chat/scoreboard color for the team, and a custom
    // "team item" (used as the team's icon in GUIs / lists). Both are
    // player-configurable from the settings menu.
    private ChatColor color = ChatColor.WHITE;
    private ItemStack customItem;

    // Shared team ender chest contents. Always sized to a full 54-slot
    // inventory internally regardless of the configured usable slot count,
    // so shrinking config.yml's echest.slots never discards items - they're
    // just inaccessible until the limit is raised again.
    private ItemStack[] echestContents = new ItemStack[54];

    public Team(UUID id, String name, String tag, UUID owner) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.owner = owner;
        this.createdAt = System.currentTimeMillis();
        this.members.put(owner, TeamRole.LEADER);
    }

    public UUID getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }

    public Map<UUID, TeamRole> getMembers() { return members; }

    public void addMember(UUID uuid, TeamRole role) { members.put(uuid, role); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }
    public TeamRole getRole(UUID uuid) { return members.get(uuid); }
    public void setRole(UUID uuid, TeamRole role) { members.put(uuid, role); }

    // ---- Kills ----
    public Map<UUID, Integer> getKills() { return kills; }
    public int getKills(UUID uuid) { return kills.getOrDefault(uuid, 0); }
    public void setKills(UUID uuid, int amount) { kills.put(uuid, amount); }
    public void addKill(UUID uuid) { kills.merge(uuid, 1, Integer::sum); }

    /** Sum of kills across every current member of the team. */
    public int getTotalKills() {
        int total = 0;
        for (int amount : kills.values()) total += amount;
        return total;
    }

    public Map<UUID, RelationType> getRelations() { return relations; }
    public RelationType getRelation(UUID otherTeamId) {
        return relations.getOrDefault(otherTeamId, RelationType.NEUTRAL);
    }
    public void setRelation(UUID otherTeamId, RelationType type) {
        if (type == RelationType.NEUTRAL) {
            relations.remove(otherTeamId);
        } else {
            relations.put(otherTeamId, type);
        }
    }

    public Map<Integer, Location> getHomes() { return homes; }
    public Location getHome(int index) { return homes.get(index); }
    public void setHome(int index, Location loc) { homes.put(index, loc); }

    public List<UUID> getPendingInvites() { return pendingInvites; }
    public void addInvite(UUID uuid) { if (!pendingInvites.contains(uuid)) pendingInvites.add(uuid); }
    public void removeInvite(UUID uuid) { pendingInvites.remove(uuid); }
    public boolean hasInvite(UUID uuid) { return pendingInvites.contains(uuid); }

    // Pending alliance requests: team IDs of teams that have requested an
    // alliance with this team and are awaiting acceptance.
    public List<UUID> getPendingAllyInvites() { return pendingAllyInvites; }
    public void addAllyInvite(UUID teamId) { if (!pendingAllyInvites.contains(teamId)) pendingAllyInvites.add(teamId); }
    public void removeAllyInvite(UUID teamId) { pendingAllyInvites.remove(teamId); }
    public boolean hasAllyInvite(UUID teamId) { return pendingAllyInvites.contains(teamId); }

    // Pending join requests: UUIDs of players (not currently in any team)
    // who have asked to join this team via /team joinrequest and are
    // awaiting a leader/officer to accept or deny them.
    public List<UUID> getPendingJoinRequests() { return pendingJoinRequests; }
    public void addJoinRequest(UUID player) { if (!pendingJoinRequests.contains(player)) pendingJoinRequests.add(player); }
    public void removeJoinRequest(UUID player) { pendingJoinRequests.remove(player); }
    public boolean hasJoinRequest(UUID player) { return pendingJoinRequests.contains(player); }

    public int getAllyCount() {
        int count = 0;
        for (RelationType type : relations.values()) {
            if (type == RelationType.ALLY) count++;
        }
        return count;
    }

    public double getBankBalance() { return bankBalance; }
    public void setBankBalance(double bankBalance) { this.bankBalance = bankBalance; }

    /** How much real money (Vault) this team's owner actually paid to create it, used to calculate disband refunds. */
    public double getCreationCostPaid() { return creationCostPaid; }
    public void setCreationCostPaid(double creationCostPaid) { this.creationCostPaid = creationCostPaid; }

    /**
     * Adds money to the bank, optionally capped at {@code maxBalance}
     * (0/negative means unlimited). Returns the amount actually deposited
     * (may be less than requested if the cap was hit).
     */
    public double deposit(double amount, double maxBalance) {
        if (amount <= 0) return 0;
        double room = maxBalance > 0 ? Math.max(0, maxBalance - bankBalance) : amount;
        double toAdd = Math.min(amount, room);
        bankBalance += toAdd;
        return toAdd;
    }

    public void deposit(double amount) { this.bankBalance += amount; }

    public boolean withdraw(double amount) {
        if (amount <= 0 || bankBalance < amount) return false;
        bankBalance -= amount;
        return true;
    }

    // ---- Customization ----
    public ChatColor getColor() { return color == null ? ChatColor.WHITE : color; }
    public void setColor(ChatColor color) { this.color = color == null ? ChatColor.WHITE : color; }

    /** Team name prefixed with its custom color, ready to drop into a message. */
    public String getColoredName() { return getColor() + name; }

    /** The custom icon representing this team, or null if none was set. */
    public ItemStack getCustomItem() { return customItem; }
    public void setCustomItem(ItemStack customItem) { this.customItem = customItem; }
    public boolean hasCustomItem() { return customItem != null; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = xp; }
    public void addXp(long amount) { this.xp += amount; }

    public boolean isTeamChatToggleDefault() { return teamChatToggleDefault; }
    public void setTeamChatToggleDefault(boolean teamChatToggleDefault) { this.teamChatToggleDefault = teamChatToggleDefault; }

    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public int getSize() { return members.size(); }

    // ---- Echest ----
    /** Always a 54-length array (nulls = empty slots). */
    public ItemStack[] getEchestContents() { return echestContents; }
    public void setEchestContents(ItemStack[] contents) {
        ItemStack[] full = new ItemStack[54];
        if (contents != null) {
            System.arraycopy(contents, 0, full, 0, Math.min(contents.length, 54));
        }
        this.echestContents = full;
    }
}
