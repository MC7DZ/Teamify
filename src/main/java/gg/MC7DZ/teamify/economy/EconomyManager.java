package gg.MC7DZ.teamify.economy;

import gg.MC7DZ.teamify.Teamify;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {
   private final Teamify plugin;
   private Economy economy;

   public EconomyManager(Teamify plugin) {
      this.plugin = plugin;
      this.setup();
   }

   public boolean setup() {
      if (!this.plugin.getConfig().getBoolean("integrations.vault.enabled", true)) {
         this.economy = null;
         return false;
      }

      if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
         this.economy = null;
         return false;
      }

      try {
         RegisteredServiceProvider<Economy> provider = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
         this.economy = provider == null ? null : (Economy)provider.getProvider();
      } catch (Throwable t) {
         this.economy = null;
      }

      return this.economy != null;
   }

   public boolean isEnabled() {
      return this.economy != null;
   }

   public double getBalance(OfflinePlayer player) {
      return this.economy == null ? 0.0 : this.economy.getBalance(player);
   }

   public boolean has(OfflinePlayer player, double amount) {
      return this.economy == null ? false : this.economy.has(player, amount);
   }

   public boolean withdrawPlayer(OfflinePlayer player, double amount) {
      return this.economy == null ? false : this.economy.withdrawPlayer(player, amount).transactionSuccess();
   }

   public boolean depositPlayer(OfflinePlayer player, double amount) {
      return this.economy == null ? false : this.economy.depositPlayer(player, amount).transactionSuccess();
   }

   public String format(double amount) {
      if (this.economy != null) {
         try {
            return this.economy.format(amount);
         } catch (Throwable ignored) {
         }
      }

      return String.format("%.2f", amount);
   }
}
