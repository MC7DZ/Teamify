package gg.MC7DZ.teamify.player;

import gg.MC7DZ.teamify.Teamify;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {

    private final Teamify plugin;
    private final File playerFile;
    private FileConfiguration playerDataConfig;
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public PlayerManager(Teamify plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().severe("Could not create data folder: " + dataFolder.getPath());
        }
        this.playerFile = new File(dataFolder, "players_list.yml");
        loadPlayers();
    }

    public void loadPlayers() {
        if (!playerFile.exists()) {
            try {
                playerFile.getParentFile().mkdirs();
                playerFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create players_list.yml file: " + e.getMessage());
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerFile);
        players.clear(); // Drop stale in-memory entries so file deletions/edits actually apply

        if (playerDataConfig.isConfigurationSection("players")) {
            for (String uuidString : playerDataConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                String name = playerDataConfig.getString("players." + uuidString + ".name");
                // "hidden: true" on any entry here excludes that player from
                // the /team players-list GUI, without affecting anything else.
                boolean hidden = playerDataConfig.getBoolean("players." + uuidString + ".hidden", false);
                players.put(uuid, new PlayerData(uuid, name, hidden));
            }
        }
        plugin.getLogger().info("Loaded " + players.size() + " player data entries.");
    }

    public void savePlayers() {
        playerDataConfig.set("players", null); // Clear existing data
        for (PlayerData playerData : players.values()) {
            String uuidString = playerData.getUuid().toString();
            playerDataConfig.set("players." + uuidString + ".name", playerData.getName());
            playerDataConfig.set("players." + uuidString + ".hidden", playerData.isHidden());
        }
        try {
            playerDataConfig.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save players_list.yml file: " + e.getMessage());
        }
        plugin.getLogger().info("Saved " + players.size() + " player data entries.");
    }

    public PlayerData getPlayerData(UUID uuid) {
        return players.get(uuid);
    }

    public void updatePlayerData(OfflinePlayer offlinePlayer) {
        PlayerData playerData = players.get(offlinePlayer.getUniqueId());
        if (playerData == null) {
            playerData = new PlayerData(offlinePlayer.getUniqueId(), offlinePlayer.getName());
            players.put(offlinePlayer.getUniqueId(), playerData);
        } else {
            // Update name if it has changed
            if (!playerData.getName().equals(offlinePlayer.getName())) {
                playerData.setName(offlinePlayer.getName());
            }
        }
        // Mark as online/offline if needed, but this is for persistent data
    }

    public Map<UUID, PlayerData> getAllPlayers() {
        return players;
    }
}
