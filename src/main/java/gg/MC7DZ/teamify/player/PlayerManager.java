package gg.MC7DZ.teamify.player;

import gg.MC7DZ.teamify.Teamify;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

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
      this.loadPlayers();
   }

   public void loadPlayers() {
      if (!this.playerFile.exists()) {
         try {
            this.playerFile.getParentFile().mkdirs();
            this.playerFile.createNewFile();
         } catch (IOException e) {
            this.plugin.getLogger().severe("Could not create players_list.yml file: " + e.getMessage());
         }
      }

      this.playerDataConfig = YamlConfiguration.loadConfiguration(this.playerFile);
      this.players.clear();
      if (this.playerDataConfig.isConfigurationSection("players")) {
         for (String uuidString : this.playerDataConfig.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidString);
            String name = this.playerDataConfig.getString("players." + uuidString + ".name");
            boolean hidden = this.playerDataConfig.getBoolean("players." + uuidString + ".hidden", false);
            this.players.put(uuid, new PlayerData(uuid, name, hidden));
         }
      }

      this.plugin.getLogger().info("Loaded " + this.players.size() + " player data entries.");
   }

   public void savePlayers() {
      this.playerDataConfig.set("players", null);

      for (PlayerData playerData : this.players.values()) {
         String uuidString = playerData.getUuid().toString();
         this.playerDataConfig.set("players." + uuidString + ".name", playerData.getName());
         this.playerDataConfig.set("players." + uuidString + ".hidden", playerData.isHidden());
      }

      try {
         this.playerDataConfig.save(this.playerFile);
      } catch (IOException e) {
         this.plugin.getLogger().severe("Could not save players_list.yml file: " + e.getMessage());
      }

      this.plugin.getLogger().info("Saved " + this.players.size() + " player data entries.");
   }

   public PlayerData getPlayerData(UUID uuid) {
      return this.players.get(uuid);
   }

   public void updatePlayerData(OfflinePlayer offlinePlayer) {
      PlayerData playerData = this.players.get(offlinePlayer.getUniqueId());
      if (playerData == null) {
         playerData = new PlayerData(offlinePlayer.getUniqueId(), offlinePlayer.getName());
         this.players.put(offlinePlayer.getUniqueId(), playerData);
      } else if (!playerData.getName().equals(offlinePlayer.getName())) {
         playerData.setName(offlinePlayer.getName());
      }
   }

   public Map<UUID, PlayerData> getAllPlayers() {
      return this.players;
   }
}
