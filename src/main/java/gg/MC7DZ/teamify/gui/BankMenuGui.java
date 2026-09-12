package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.listeners.PlayerListener;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import gg.MC7DZ.teamify.util.SoundUtil;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

public class BankMenuGui extends GuiHolder {
   private final Team team;
   private int balanceSlot;
   private int depositSlot;
   private int withdrawSlot;
   private int backButtonSlot = -1;

   public BankMenuGui(Player viewer, Team team) {
      super(viewer);
      this.team = team;
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.bank-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray><bold>Team Bank"));
      int size = cfg.getInt("size", 54);
      ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
      if (itemsCfg != null) {
         this.balanceSlot = itemsCfg.getInt("balance.slot", 22);
         this.depositSlot = itemsCfg.getInt("deposit.slot", 20);
         this.withdrawSlot = itemsCfg.getInt("withdraw.slot", 24);
         this.backButtonSlot = itemsCfg.getInt("back.slot", 45);
      } else {
         this.balanceSlot = 22;
         this.depositSlot = 20;
         this.withdrawSlot = 24;
         this.backButtonSlot = 45;
      }

      Inventory inv = Bukkit.createInventory(this, size, title);
      if (cfg.getBoolean("fill-empty-slots", true)) {
         Material filler;
         try {
            filler = Material.valueOf(cfg.getString("filler-item", "GRAY_STAINED_GLASS_PANE"));
         } catch (IllegalArgumentException e) {
            filler = Material.GRAY_STAINED_GLASS_PANE;
         }

         List<Integer> fillerSlots = cfg.getIntegerList("filler-slots");
         if (fillerSlots != null && !fillerSlots.isEmpty()) {
            this.fillSlots(inv, filler, fillerSlots);
         } else {
            for (int i = 0; i < size; i++) {
               inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
            }
         }
      }

      if (itemsCfg != null && itemsCfg.contains("back")) {
         this.setBackButton(inv, this.backButtonSlot);
      }

      String balanceStr = this.plugin.getEconomyManager().format(this.team.getBankBalance());
      if (itemsCfg != null && itemsCfg.contains("balance")) {
         this.placeConfigItem(inv, this.balanceSlot, itemsCfg.getConfigurationSection("balance"), "amount", balanceStr);
      } else {
         inv.setItem(
            this.balanceSlot,
            GuiItem.simple(
               Material.GOLD_INGOT,
               this.plugin.getConfigManager().color("<gold><bold>Team Bank"),
               this.plugin.getConfigManager().color("<gray>Balance: <white>" + balanceStr)
            )
         );
      }

      if (itemsCfg != null && itemsCfg.contains("deposit")) {
         this.placeConfigItem(inv, this.depositSlot, itemsCfg.getConfigurationSection("deposit"));
      } else {
         inv.setItem(
            this.depositSlot,
            GuiItem.simple(
               Material.LIME_DYE,
               this.plugin.getConfigManager().color("<green>Deposit"),
               this.plugin.getConfigManager().color("<gray>Click and type an amount in chat"),
               this.plugin.getConfigManager().color("<gray>to move money from your balance"),
               this.plugin.getConfigManager().color("<gray>into the team bank.")
            )
         );
      }

      TeamRole role = this.team.getRole(this.getViewer().getUniqueId());
      boolean canWithdraw = role != null
         && this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-withdraw-bank", false)
         && this.getViewer().hasPermission("teams.bank.withdraw");
      if (canWithdraw) {
         if (itemsCfg != null && itemsCfg.contains("withdraw")) {
            this.placeConfigItem(inv, this.withdrawSlot, itemsCfg.getConfigurationSection("withdraw"));
         } else {
            inv.setItem(
               this.withdrawSlot,
               GuiItem.simple(
                  Material.RED_DYE,
                  this.plugin.getConfigManager().color("<red>Withdraw"),
                  this.plugin.getConfigManager().color("<gray>Click and type an amount in chat"),
                  this.plugin.getConfigManager().color("<gray>to move money from the team bank"),
                  this.plugin.getConfigManager().color("<gray>into your balance.")
               )
            );
         }
      } else {
         inv.setItem(
            this.withdrawSlot,
            GuiItem.simple(
               Material.BARRIER,
               this.plugin.getConfigManager().color("<red>Withdraw Locked"),
               this.plugin.getConfigManager().color("<gray>Your role doesn't allow"),
               this.plugin.getConfigManager().color("<gray>withdrawing from the team bank.")
            )
         );
      }

      this.setInventory(inv);
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         new MainMenuGui(p, this.team).open();
      } else if (!this.plugin.getEconomyManager().isEnabled()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("bank-no-economy"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else {
         if (slot == this.depositSlot) {
            p.closeInventory();
            this.plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.BANK_DEPOSIT);
            p.sendMessage(
               this.plugin
                  .getConfigManager()
                  .getPrefix()
                  .append(this.plugin.getConfigManager().color("<green>Type the amount to deposit in chat (or \"cancel\")."))
            );
            SoundUtil.play(p, this.plugin.getConfigManager().getGuiOpenSound());
         } else if (slot == this.withdrawSlot) {
            TeamRole role = this.team.getRole(p.getUniqueId());
            boolean canWithdraw = role != null
               && this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-withdraw-bank", false)
               && p.hasPermission("teams.bank.withdraw");
            if (!canWithdraw) {
               p.sendMessage(this.plugin.getConfigManager().getMessage("bank-cant-withdraw-role"));
               SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
               return;
            }

            p.closeInventory();
            this.plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.BANK_WITHDRAW);
            p.sendMessage(
               this.plugin
                  .getConfigManager()
                  .getPrefix()
                  .append(this.plugin.getConfigManager().color("<green>Type the amount to withdraw in chat (or \"cancel\")."))
            );
            SoundUtil.play(p, this.plugin.getConfigManager().getGuiOpenSound());
         }
      }
   }

   private Material parse(String s, Material fallback) {
      try {
         return Material.valueOf(s.toUpperCase());
      } catch (IllegalArgumentException e) {
         return fallback;
      }
   }
}
