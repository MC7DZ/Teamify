package gg.MC7DZ.teamify.economy;

import gg.MC7DZ.teamify.Teamify;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Thin wrapper around Vault's Economy service. The team bank stores its own
 * balance (see {@link gg.MC7DZ.teamify.team.Team#getBankBalance()}) - this
 * class only handles moving money between a *player's* personal balance and
 * the team bank via a Vault-registered economy plugin (e.g. EssentialsX).
 * <p>
 * If Vault (or an economy plugin behind it) isn't installed, {@link #isEnabled()}
 * returns false and every operation is a safe no-op.
 */
public class EconomyManager {

    private final Teamify plugin;
    private Economy economy;

    public EconomyManager(Teamify plugin) {
        this.plugin = plugin;
        setup();
    }

    /** Re-attempts to hook into Vault's Economy service (e.g. after a reload). */
    public boolean setup() {
        if (!plugin.getConfig().getBoolean("integrations.vault.enabled", true)) {
            economy = null;
            return false;
        }
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return false;
        }
        try {
            RegisteredServiceProvider<Economy> provider = plugin.getServer()
                    .getServicesManager().getRegistration(Economy.class);
            economy = provider == null ? null : provider.getProvider();
        } catch (Throwable t) {
            economy = null;
        }
        return economy != null;
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (economy == null) return 0;
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.has(player, amount);
    }

    /** Withdraws from the player's personal Vault balance. Returns true on success. */
    public boolean withdrawPlayer(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** Deposits into the player's personal Vault balance. Returns true on success. */
    public boolean depositPlayer(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (economy != null) {
            try {
                return economy.format(amount);
            } catch (Throwable ignored) {
            }
        }
        return String.format("%.2f", amount);
    }
}
