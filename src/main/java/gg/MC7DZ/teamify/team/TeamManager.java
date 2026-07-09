package gg.MC7DZ.teamify.team;

import gg.MC7DZ.teamify.Teamify;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    public Collection<Team> getTeams() {
        return teamsById.values();
    }

    public Team getTeam(UUID id) {
        return teamsById.get(id);
    }

    public Team getTeamByName(String name) {
        for (Team t : teamsById.values()) {
            if (t.getName().equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    public Team getTeamOf(UUID player) {
        UUID teamId = memberToTeam.get(player);
        return teamId == null ? null : teamsById.get(teamId);
    }

    public boolean isInTeam(UUID player) {
        return memberToTeam.containsKey(player);
    }

    public Team createTeam(String name, String tag, UUID owner) {
        Team team = new Team(UUID.randomUUID(), name, tag, owner);
        teamsById.put(team.getId(), team);
        memberToTeam.put(owner, team.getId());
        return team;
    }

    public void disbandTeam(Team team) {
        for (UUID member : team.getMembers().keySet()) {
            memberToTeam.remove(member);
        }
        teamsById.remove(team.getId());
        disbandCooldowns.put(team.getOwner(), System.currentTimeMillis());
        File f = teamFile(team);
        if (f.exists()) f.delete();
    }

    public void addMember(Team team, UUID player, TeamRole role) {
        team.addMember(player, role);
        memberToTeam.put(player, team.getId());
        // A player can only be in one team at a time, so any join requests
        // they sent to other teams are no longer meaningful - drop them so
        // they don't linger forever in those teams' request menus.
        for (Team other : teamsById.values()) {
            if (other.getId().equals(team.getId())) continue;
            if (other.hasJoinRequest(player)) {
                other.removeJoinRequest(player);
                saveTeam(other);
            }
        }
    }

    public void removeMember(Team team, UUID player) {
        team.removeMember(player);
        memberToTeam.remove(player);
    }

    // ---------------- Alliances ----------------

    /**
     * Sends (or, if the target already sent one to us, accepts) an alliance
     * request between two teams.
     *
     * @return ALLY_ADDED if an alliance was formed immediately, INVITE_SENT if
     *         a request was queued awaiting the other team's acceptance.
     */
    public enum AllyInviteResult { ALLY_ADDED, INVITE_SENT }

    public AllyInviteResult requestAlly(Team from, Team to, boolean mutualRequired) {
        if (!mutualRequired || from.hasAllyInvite(to.getId())) {
            // Either mutual confirmation isn't required, or the other team
            // already asked us first -> form the alliance immediately.
            setAllied(from, to);
            from.removeAllyInvite(to.getId());
            to.removeAllyInvite(from.getId());
            return AllyInviteResult.ALLY_ADDED;
        }
        to.addAllyInvite(from.getId());
        saveTeam(to);
        return AllyInviteResult.INVITE_SENT;
    }

    public void setAllied(Team a, Team b) {
        a.setRelation(b.getId(), RelationType.ALLY);
        b.setRelation(a.getId(), RelationType.ALLY);
        saveTeam(a);
        saveTeam(b);
    }

    public void removeAlly(Team a, Team b) {
        a.setRelation(b.getId(), RelationType.NEUTRAL);
        b.setRelation(a.getId(), RelationType.NEUTRAL);
        saveTeam(a);
        saveTeam(b);
    }

    public boolean areAllied(Team a, Team b) {
        return a.getRelation(b.getId()) == RelationType.ALLY;
    }

    public long getCreationCooldownRemaining(UUID player, int cooldownSeconds) {
        Long last = creationCooldowns.get(player);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldownSeconds - elapsed);
    }

    public void markCreationCooldown(UUID player) {
        creationCooldowns.put(player, System.currentTimeMillis());
    }

    public long getDisbandCooldownRemaining(UUID player, int cooldownSeconds) {
        Long last = disbandCooldowns.get(player);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldownSeconds - elapsed);
    }

    // ---------------- Persistence (YAML based) ----------------

    public void loadAll() {
        if (dataFolder.listFiles() == null) return;
        for (File file : Objects.requireNonNull(dataFolder.listFiles((d, n) -> n.endsWith(".yml")))) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                String name = cfg.getString("name");
                String tag = cfg.getString("tag");
                UUID owner = UUID.fromString(cfg.getString("owner"));

                // Team files are now named after the team's name rather than
                // its UUID. The id is stored inside the file; for files saved
                // by an older version (named <uuid>.yml with no "id" field),
                // fall back to reading the id from the filename instead.
                UUID id;
                String idStr = cfg.getString("id");
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
                team.setXp(cfg.getLong("xp", 0));
                team.setCreatedAt(cfg.getLong("created-at", System.currentTimeMillis()));
                team.setPvpEnabled(cfg.getBoolean("pvp-enabled", true));

                String colorName = cfg.getString("color");
                if (colorName != null) {
                    try {
                        team.setColor(org.bukkit.ChatColor.valueOf(colorName));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                Object rawItem = cfg.get("custom-item");
                if (rawItem instanceof org.bukkit.inventory.ItemStack itemStack) {
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
                        memberToTeam.put(memberId, id);
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
                    org.bukkit.inventory.ItemStack[] echest = team.getEchestContents();
                    for (String key : echestSec.getKeys(false)) {
                        try {
                            int idx = Integer.parseInt(key);
                            Object raw = echestSec.get(key);
                            if (idx >= 0 && idx < echest.length && raw instanceof org.bukkit.inventory.ItemStack item) {
                                echest[idx] = item;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                teamsById.put(id, team);

                // Migrate an old <uuid>.yml file to the new <name>.yml naming
                // on next load, so it doesn't get re-read as a stray file.
                File expected = teamFile(team);
                if (!file.equals(expected)) {
                    file.delete();
                    saveTeam(team);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load team file " + file.getName() + ": " + ex.getMessage());
            }
        }
    }

    public void saveAll() {
        for (Team team : teamsById.values()) {
            saveTeam(team);
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
        for (UUID id : team.getPendingAllyInvites()) allyInvites.add(id.toString());
        cfg.set("pending-ally-invites", allyInvites);

        List<String> joinRequests = new ArrayList<>();
        for (UUID id : team.getPendingJoinRequests()) joinRequests.add(id.toString());
        cfg.set("pending-join-requests", joinRequests);

        for (Map.Entry<UUID, TeamRole> e : team.getMembers().entrySet()) {
            cfg.set("members." + e.getKey(), e.getValue().name());
        }
        for (Map.Entry<UUID, RelationType> e : team.getRelations().entrySet()) {
            cfg.set("relations." + e.getKey(), e.getValue().name());
        }
        for (Map.Entry<Integer, Location> e : team.getHomes().entrySet()) {
            cfg.set("homes." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Integer> e : team.getKills().entrySet()) {
            cfg.set("kills." + e.getKey(), e.getValue());
        }

        // Echest contents - only write the array if at least one slot is
        // non-empty, keeping team files clean for teams that never use it.
        org.bukkit.inventory.ItemStack[] echest = team.getEchestContents();
        boolean anyEchestItem = false;
        for (org.bukkit.inventory.ItemStack item : echest) {
            if (item != null) { anyEchestItem = true; break; }
        }
        if (anyEchestItem) {
            for (int i = 0; i < echest.length; i++) {
                if (echest[i] != null) {
                    cfg.set("echest." + i, echest[i]);
                }
            }
        }

        try {
            cfg.save(teamFile(team));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save team " + team.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Renames a team's data file to match its current name (call this after
     * changing a team's name, if a rename feature is ever added) - deletes
     * the old file and re-saves under the new one.
     */
    public void renameTeamFile(Team team, String oldName) {
        File oldFile = new File(dataFolder, sanitizeFileName(oldName) + ".yml");
        if (oldFile.exists()) oldFile.delete();
        saveTeam(team);
    }

    /** The file a team is stored in: <sanitized team name>.yml */
    private File teamFile(Team team) {
        return new File(dataFolder, sanitizeFileName(team.getName()) + ".yml");
    }

    /** Strips anything that isn't filesystem-safe from a team name for use as a filename. */
    private String sanitizeFileName(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
