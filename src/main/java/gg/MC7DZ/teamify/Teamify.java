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
import gg.MC7DZ.teamify.player.PlayerManager; // Import PlayerManager
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.update.ModrinthUpdateChecker;
import gg.MC7DZ.teamify.visibility.VisibilityManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Teamify extends JavaPlugin {

    private static Teamify instance;

    private ConfigManager configManager;
    private TeamManager teamManager;
    private PlayerManager playerManager; // Declare PlayerManager
    private PlayerListener playerListener;
    private VisibilityManager visibilityManager;
    private EconomyManager economyManager;
    private TeamCommand teamCommand;
    private FileConfiguration guiConfig;
    private ModrinthUpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveDefaultGuiConfig(); // Save default gui.yml
        this.configManager = new ConfigManager(this);
        this.teamManager = new TeamManager(this);
        this.teamManager.loadAll();
        this.playerManager = new PlayerManager(this); // Initialize PlayerManager
        this.visibilityManager = new VisibilityManager(this);
        this.economyManager = new EconomyManager(this);
        if (economyManager.isEnabled()) {
            getLogger().info("Hooked into Vault for team bank deposits/withdrawals.");
        } else {
            getLogger().info("Vault not found (or disabled) - team bank deposit/withdraw against player balances will be unavailable.");
        }

        this.teamCommand = new TeamCommand(this);
        getCommand("team").setExecutor(teamCommand);
        getCommand("team").setTabCompleter(new TeamTabCompleter(this));
        getCommand("teamadmin").setExecutor(new TeamAdminCommand(this));
        getCommand("teamadmin").setTabCompleter(new TeamAdminTabCompleter(this));

        registerPlaceholderApi();

        this.playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamPvpListener(this), this);

        long interval = configManager.getConfig().getLong("general.autosave-interval-minutes", 5) * 60 * 20L;
        if (interval > 0) {
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    () -> {
                        teamManager.saveAll();
                        playerManager.savePlayers(); // Save player data during autosave
                    }, interval, interval);
        }

        // Periodically rebuild every online player's "see invisible
        // teammates/allies" scoreboard, in case something changed without
        // going through one of the explicit refresh call sites (plugin
        // reload, /team join accepted via GUI click, etc).
        getServer().getScheduler().runTaskTimer(this,
                () -> visibilityManager.refreshAll(), 40L, 100L);

        this.updateChecker = new ModrinthUpdateChecker(this);
        this.updateChecker.check();

        getLogger().info("Teamify has been enabled with " + teamManager.getTeams().size() + " teams loaded.");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.saveAll();
        }
        if (playerManager != null) { // Save player data on disable
            playerManager.savePlayers();
        }
        if (visibilityManager != null) {
            for (var player : getServer().getOnlinePlayers()) {
                visibilityManager.reset(player);
            }
        }
        getLogger().info("Teamify has been disabled.");
    }

    /**
     * Registers the "teamify" PlaceholderAPI expansion (e.g. %teamify_bank%)
     * if PlaceholderAPI is installed and the integration is enabled in
     * config.yml. Safe to call even when PlaceholderAPI isn't present -
     * this only touches PlaceholderAPI classes once we've confirmed the
     * plugin is loaded, so there's no hard compile/runtime dependency.
     */
    private void registerPlaceholderApi() {
        if (!configManager.isPlaceholderApiEnabled()) {
            getLogger().info("PlaceholderAPI integration disabled in config.yml, skipping.");
            return;
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found - %teamify_...% placeholders will be unavailable.");
            return;
        }
        try {
            new TeamifyExpansion(this).register();
            getLogger().info("Hooked into PlaceholderAPI (%teamify_bank%, %teamify_kills%, etc).");
        } catch (Throwable t) {
            getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
        }
    }

    public void saveDefaultGuiConfig() {
        File guiFile = new File(getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            saveResource("gui.yml", false);
        }
        loadAndMergeGuiConfig(guiFile);
    }

    public void reloadGuiConfig() {
        File guiFile = new File(getDataFolder(), "gui.yml");
        loadAndMergeGuiConfig(guiFile);
    }

    /**
     * Loads gui.yml from disk and adds any keys present in the bundled
     * default (new items/options shipped in a plugin update) that are
     * missing from the on-disk file - without touching anything the admin
     * already has, so edited names/lore/materials/slots/hide flags survive
     * restarts and reloads. This used to force-overwrite gui.yml with
     * saveResource("gui.yml", true) on every startup, silently discarding
     * every customization each time the server restarted.
     * Note: since this saves the file back through Bukkit's YamlConfiguration
     * writer, any comments in gui.yml won't survive a merge that actually
     * adds new keys (a YamlConfiguration limitation) - but existing values
     * are always preserved either way.
     */
    private void loadAndMergeGuiConfig(File guiFile) {
        this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        try (InputStream defStream = getResource("gui.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));
                boolean added = mergeMissingKeys(this.guiConfig, defaults);
                this.guiConfig.setDefaults(defaults);
                if (added) {
                    try {
                        this.guiConfig.save(guiFile);
                        getLogger().info("gui.yml was missing some options added in a newer version - filled them in with defaults, existing customizations were kept.");
                    } catch (IOException e) {
                        getLogger().warning("Failed to save updated gui.yml: " + e.getMessage());
                    }
                }
            }
        } catch (Exception ignored) {
            getLogger().warning("Failed to load default gui.yml: " + ignored.getMessage());
        }
    }

    /**
     * Recursively copies keys that exist in {@code source} but not in
     * {@code target} into {@code target}. Existing keys/values in target are
     * never touched or overwritten - only genuinely missing ones are added.
     * Returns true if anything was added.
     */
    private boolean mergeMissingKeys(org.bukkit.configuration.ConfigurationSection target,
                                      org.bukkit.configuration.ConfigurationSection source) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            if (!target.contains(key)) {
                target.set(key, source.get(key));
                changed = true;
            } else if (source.isConfigurationSection(key) && target.isConfigurationSection(key)) {
                if (mergeMissingKeys(target.getConfigurationSection(key), source.getConfigurationSection(key))) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    public static Teamify getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ModrinthUpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public PlayerManager getPlayerManager() { // Add getter for PlayerManager
        return playerManager;
    }

    public PlayerListener getPlayerListener() {
        return playerListener;
    }

    public VisibilityManager getVisibilityManager() {
        return visibilityManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public TeamCommand getTeamCommand() {
        return teamCommand;
    }
}