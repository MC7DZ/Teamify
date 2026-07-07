package gg.MC7DZ.teamify;

import gg.MC7DZ.teamify.commands.TeamAdminCommand;
import gg.MC7DZ.teamify.commands.TeamAdminTabCompleter;
import gg.MC7DZ.teamify.commands.TeamCommand;
import gg.MC7DZ.teamify.commands.TeamTabCompleter;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.listeners.GuiListener;
import gg.MC7DZ.teamify.listeners.PlayerListener;
import gg.MC7DZ.teamify.team.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Teamify extends JavaPlugin {

    private static Teamify instance;

    private ConfigManager configManager;
    private TeamManager teamManager;
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.teamManager = new TeamManager(this);
        this.teamManager.loadAll();

        getCommand("team").setExecutor(new TeamCommand(this));
        getCommand("team").setTabCompleter(new TeamTabCompleter(this));
        getCommand("teamadmin").setExecutor(new TeamAdminCommand(this));
        getCommand("teamadmin").setTabCompleter(new TeamAdminTabCompleter(this));

        this.playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        long interval = configManager.getConfig().getLong("general.autosave-interval-minutes", 5) * 60 * 20L;
        if (interval > 0) {
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    () -> teamManager.saveAll(), interval, interval);
        }

        getLogger().info("Teamify has been enabled with " + teamManager.getTeams().size() + " teams loaded.");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.saveAll();
        }
        getLogger().info("Teamify has been disabled.");
    }

    public static Teamify getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public PlayerListener getPlayerListener() {
        return playerListener;
    }
}
