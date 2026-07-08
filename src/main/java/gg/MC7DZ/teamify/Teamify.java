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
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.visibility.VisibilityManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Teamify extends JavaPlugin {

    private static Teamify instance;

    private ConfigManager configManager;
    private TeamManager teamManager;
    private PlayerListener playerListener;
    private VisibilityManager visibilityManager;
    private EconomyManager economyManager;
    private TeamCommand teamCommand;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.teamManager = new TeamManager(this);
        this.teamManager.loadAll();
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

        this.playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamPvpListener(this), this);

        long interval = configManager.getConfig().getLong("general.autosave-interval-minutes", 5) * 60 * 20L;
        if (interval > 0) {
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    () -> teamManager.saveAll(), interval, interval);
        }

        // Periodically rebuild every online player's "see invisible
        // teammates/allies" scoreboard, in case something changed without
        // going through one of the explicit refresh call sites (plugin
        // reload, /team join accepted via GUI click, etc).
        getServer().getScheduler().runTaskTimer(this,
                () -> visibilityManager.refreshAll(), 40L, 100L);

        getLogger().info("Teamify has been enabled with " + teamManager.getTeams().size() + " teams loaded.");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.saveAll();
        }
        if (visibilityManager != null) {
            for (var player : getServer().getOnlinePlayers()) {
                visibilityManager.reset(player);
            }
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
