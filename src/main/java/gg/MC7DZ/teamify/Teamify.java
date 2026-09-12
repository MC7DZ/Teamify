package gg.MC7DZ.teamify;

import gg.MC7DZ.teamify.commands.TeamAdminCommand;
import gg.MC7DZ.teamify.commands.TeamAdminTabCompleter;
import gg.MC7DZ.teamify.commands.TeamCommand;
import gg.MC7DZ.teamify.commands.TeamTabCompleter;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.economy.EconomyManager;
import gg.MC7DZ.teamify.listeners.GuiListener;
import gg.MC7DZ.teamify.listeners.PlayerListener;
import gg.MC7DZ.teamify.listeners.TeamPvpListener;
import gg.MC7DZ.teamify.placeholder.TeamifyExpansion;
import gg.MC7DZ.teamify.player.PlayerManager;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.update.ModrinthUpdateChecker;
import gg.MC7DZ.teamify.visibility.VisibilityManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Teamify extends JavaPlugin {
   private static Teamify instance;
   private ConfigManager configManager;
   private TeamManager teamManager;
   private PlayerManager playerManager;
   private PlayerListener playerListener;
   private VisibilityManager visibilityManager;
   private EconomyManager economyManager;
   private TeamCommand teamCommand;
   private FileConfiguration guiConfig;
   private ModrinthUpdateChecker updateChecker;

   public void onEnable() {
      instance = this;
      this.saveDefaultConfig();
      this.saveDefaultGuiConfig();
      this.configManager = new ConfigManager(this);
      this.teamManager = new TeamManager(this);
      this.teamManager.loadAll();
      this.playerManager = new PlayerManager(this);
      this.visibilityManager = new VisibilityManager(this);
      this.economyManager = new EconomyManager(this);
      if (this.economyManager.isEnabled()) {
         this.getLogger().info("Hooked into Vault for team bank deposits/withdrawals.");
      } else {
         this.getLogger().info("Vault not found (or disabled) - team bank deposit/withdraw against player balances will be unavailable.");
      }

      this.teamCommand = new TeamCommand(this);
      this.getCommand("team").setExecutor(this.teamCommand);
      this.getCommand("team").setTabCompleter(new TeamTabCompleter(this));
      this.getCommand("teamadmin").setExecutor(new TeamAdminCommand(this));
      this.getCommand("teamadmin").setTabCompleter(new TeamAdminTabCompleter(this));
      this.registerPlaceholderApi();
      this.playerListener = new PlayerListener(this);
      this.getServer().getPluginManager().registerEvents(this.playerListener, this);
      this.getServer().getPluginManager().registerEvents(new GuiListener(this), this);
      this.getServer().getPluginManager().registerEvents(new TeamPvpListener(this), this);
      long interval = this.configManager.getConfig().getLong("general.autosave-interval-minutes", 5L) * 60L * 20L;
      if (interval > 0L) {
         this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            this.teamManager.saveAll();
            this.playerManager.savePlayers();
         }, interval, interval);
      }

      this.getServer().getScheduler().runTaskTimer(this, () -> this.visibilityManager.refreshAll(), 40L, 100L);
      this.updateChecker = new ModrinthUpdateChecker(this);
      this.updateChecker.check();
      this.getLogger().info("Teamify has been enabled with " + this.teamManager.getTeams().size() + " teams loaded.");
   }

   public void onDisable() {
      if (this.teamManager != null) {
         this.teamManager.saveAll();
      }

      if (this.playerManager != null) {
         this.playerManager.savePlayers();
      }

      if (this.visibilityManager != null) {
         for (Player player : this.getServer().getOnlinePlayers()) {
            this.visibilityManager.reset(player);
         }
      }

      this.getLogger().info("Teamify has been disabled.");
   }

   private void registerPlaceholderApi() {
      if (!this.configManager.isPlaceholderApiEnabled()) {
         this.getLogger().info("PlaceholderAPI integration disabled in config.yml, skipping.");
      } else if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
         this.getLogger().info("PlaceholderAPI not found - %teamify_...% placeholders will be unavailable.");
      } else {
         try {
            new TeamifyExpansion(this).register();
            this.getLogger().info("Hooked into PlaceholderAPI (%teamify_bank%, %teamify_kills%, etc).");
         } catch (Throwable t) {
            this.getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
         }
      }
   }

   public void saveDefaultGuiConfig() {
      File guiFile = new File(this.getDataFolder(), "gui.yml");
      if (!guiFile.exists()) {
         this.saveResource("gui.yml", false);
      }

      this.loadAndMergeGuiConfig(guiFile);
   }

   public void reloadGuiConfig() {
      File guiFile = new File(this.getDataFolder(), "gui.yml");
      this.loadAndMergeGuiConfig(guiFile);
   }

   private void loadAndMergeGuiConfig(File guiFile) {
      this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);

      try (InputStream defStream = this.getResource("gui.yml")) {
         if (defStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            boolean added = this.mergeMissingKeys(this.guiConfig, defaults);
            this.guiConfig.setDefaults(defaults);
            if (added) {
               try {
                  this.guiConfig.save(guiFile);
                  this.getLogger()
                     .info("gui.yml was missing some options added in a newer version - filled them in with defaults, existing customizations were kept.");
               } catch (IOException e) {
                  this.getLogger().warning("Failed to save updated gui.yml: " + e.getMessage());
               }
            }
         }
      } catch (Exception ignored) {
         this.getLogger().warning("Failed to load default gui.yml: " + ignored.getMessage());
      }
   }

   private boolean mergeMissingKeys(ConfigurationSection target, ConfigurationSection source) {
      boolean changed = false;

      for (String key : source.getKeys(false)) {
         if (!target.contains(key)) {
            target.set(key, source.get(key));
            changed = true;
         } else if (source.isConfigurationSection(key)
            && target.isConfigurationSection(key)
            && this.mergeMissingKeys(target.getConfigurationSection(key), source.getConfigurationSection(key))) {
            changed = true;
         }
      }

      return changed;
   }

   public FileConfiguration getGuiConfig() {
      return this.guiConfig;
   }

   public static Teamify getInstance() {
      return instance;
   }

   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   public ModrinthUpdateChecker getUpdateChecker() {
      return this.updateChecker;
   }

   public TeamManager getTeamManager() {
      return this.teamManager;
   }

   public PlayerManager getPlayerManager() {
      return this.playerManager;
   }

   public PlayerListener getPlayerListener() {
      return this.playerListener;
   }

   public VisibilityManager getVisibilityManager() {
      return this.visibilityManager;
   }

   public EconomyManager getEconomyManager() {
      return this.economyManager;
   }

   public TeamCommand getTeamCommand() {
      return this.teamCommand;
   }
}
