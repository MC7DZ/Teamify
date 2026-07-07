package gg.MC7DZ.teamify.team;

import org.bukkit.Location;

import java.util.*;

public class Team {

    private final UUID id;
    private String name;
    private String tag;
    private String description;
    private UUID owner;

    private final Map<UUID, TeamRole> members = new LinkedHashMap<>();
    private final Map<UUID, RelationType> relations = new HashMap<>();
    private final Map<Integer, Location> homes = new HashMap<>();
    private final List<UUID> pendingInvites = new ArrayList<>();

    private double bankBalance;
    private int level = 1;
    private long xp = 0;
    private boolean teamChatToggleDefault = false;
    private long createdAt;

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

    public double getBankBalance() { return bankBalance; }
    public void setBankBalance(double bankBalance) { this.bankBalance = bankBalance; }
    public void deposit(double amount) { this.bankBalance += amount; }
    public boolean withdraw(double amount) {
        if (bankBalance < amount) return false;
        bankBalance -= amount;
        return true;
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = xp; }
    public void addXp(long amount) { this.xp += amount; }

    public boolean isTeamChatToggleDefault() { return teamChatToggleDefault; }
    public void setTeamChatToggleDefault(boolean teamChatToggleDefault) { this.teamChatToggleDefault = teamChatToggleDefault; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public int getSize() { return members.size(); }
}
