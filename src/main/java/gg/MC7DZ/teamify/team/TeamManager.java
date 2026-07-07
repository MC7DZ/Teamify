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
        File f = new File(dataFolder, team.getId() + ".yml");
        if (f.exists()) f.delete();
    }

    public void addMember(Team team, UUID player, TeamRole role) {
        team.addMember(player, role);
        memberToTeam.put(player, team.getId());
    }

    public void removeMember(Team team, UUID player) {
        team.removeMember(player);
        memberToTeam.remove(player);
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
                UUID id = UUID.fromString(file.getName().replace(".yml", ""));
                String name = cfg.getString("name");
                String tag = cfg.getString("tag");
                UUID owner = UUID.fromString(cfg.getString("owner"));

                Team team = new Team(id, name, tag, owner);
                team.setDescription(cfg.getString("description"));
                team.setBankBalance(cfg.getDouble("bank-balance", 0.0));
                team.setLevel(cfg.getInt("level", 1));
                team.setXp(cfg.getLong("xp", 0));
                team.setCreatedAt(cfg.getLong("created-at", System.currentTimeMillis()));

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

                teamsById.put(id, team);
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
        cfg.set("name", team.getName());
        cfg.set("tag", team.getTag());
        cfg.set("owner", team.getOwner().toString());
        cfg.set("description", team.getDescription());
        cfg.set("bank-balance", team.getBankBalance());
        cfg.set("level", team.getLevel());
        cfg.set("xp", team.getXp());
        cfg.set("created-at", team.getCreatedAt());

        for (Map.Entry<UUID, TeamRole> e : team.getMembers().entrySet()) {
            cfg.set("members." + e.getKey(), e.getValue().name());
        }
        for (Map.Entry<UUID, RelationType> e : team.getRelations().entrySet()) {
            cfg.set("relations." + e.getKey(), e.getValue().name());
        }
        for (Map.Entry<Integer, Location> e : team.getHomes().entrySet()) {
            cfg.set("homes." + e.getKey(), e.getValue());
        }

        try {
            cfg.save(new File(dataFolder, team.getId() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save team " + team.getName() + ": " + e.getMessage());
        }
    }
}
