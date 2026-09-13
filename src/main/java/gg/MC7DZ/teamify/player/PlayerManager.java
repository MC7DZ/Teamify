package gg.MC7DZ.teamify.player;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.storage.DatabaseManager;
import gg.MC7DZ.teamify.storage.StorageType;
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
   private final StorageType storageType;
   private DatabaseManager db;

   public PlayerManager(Teamify plugin) {
      this.plugin = plugin;
      File dataFolder = new File(plugin.getDataFolder(), "data");
      if (!dataFolder.exists() && !dataFolder.mkdirs()) {
         plugin.getLogger().severe("Could not create data folder: " + dataFolder.getPath());
      }

      this.playerFile = new File(dataFolder, "players_list.yml");
      this.storageType = StorageType.fromConfig(plugin.getConfigManager().getStorageType());

      if (this.storageType != StorageType.YAML) {
         // Reuse the TeamManager's database connection/tables when it already opened one,
         // otherwise open our own (e.g. if TeamManager failed to connect and fell back to YAML,
         // we still try independently here).
         this.db = new DatabaseManager(plugin, this.storageType);

         try {
            this.db.connect();
         } catch (Exception e) {
            plugin.getLogger()
               .severe("Failed to connect to " + this.storageType + " storage for players, falling back to YAML: " + e.getMessage());
            this.db = null;
         }
      }

      this.loadPlayers();
   }

   private boolean usingDatabase() {
      return this.db != null;
   }

   public void shutdown() {
      if (this.db != null) {
         this.db.close();
      }
   }

   public void loadPlayers() {
      this.players.clear();

      if (this.usingDatabase()) {
         for (Map.Entry<UUID, Object[]> entry : this.db.loadAllPlayers().entrySet()) {
            Object[] data = entry.getValue();
            String name = (String) data[0];
            boolean hidden = (boolean) data[1];
            this.players.put(entry.getKey(), new PlayerData(entry.getKey(), name, hidden));
         }

         this.plugin.getLogger().info("Loaded " + this.players.size() + " player data entries.");
         return;
      }

      if (!this.playerFile.exists()) {
         try {
            this.playerFile.getParentFile().mkdirs();
            this.playerFile.createNewFile();
         } catch (IOException e) {
            this.plugin.getLogger().severe("Could not create players_list.yml file: " + e.getMessage());
         }
      }

      this.playerDataConfig = YamlConfiguration.loadConfiguration(this.playerFile);
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
      if (this.usingDatabase()) {
         for (PlayerData playerData : this.players.values()) {
            this.db.upsertPlayer(playerData.getUuid(), playerData.getName(), playerData.isHidden());
         }

         this.plugin.getLogger().info("Saved " + this.players.size() + " player data entries.");
         return;
      }

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
