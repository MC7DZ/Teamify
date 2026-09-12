package gg.MC7DZ.teamify.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

public class ConfirmMenuGui extends GuiHolder {
   private final Runnable onConfirm;
   private final Runnable onDeny;
   private int confirmSlot;
   private int denySlot;
   private int backButtonSlot = -1;

   public ConfirmMenuGui(Player viewer, Runnable onConfirm, Runnable onDeny) {
      super(viewer);
      this.onConfirm = onConfirm;
      this.onDeny = onDeny;
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.confirm-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Are you sure?"));
      int size = cfg.getInt("size", 54);
      ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
      if (itemsCfg != null) {
         this.confirmSlot = itemsCfg.getInt("confirm.slot", 20);
         this.denySlot = itemsCfg.getInt("deny.slot", 24);
         this.backButtonSlot = itemsCfg.getInt("back.slot", 45);
      } else {
         this.confirmSlot = 20;
         this.denySlot = 24;
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

      if (itemsCfg != null && itemsCfg.contains("confirm")) {
         this.placeConfigItem(inv, this.confirmSlot, itemsCfg.getConfigurationSection("confirm"));
      } else {
         inv.setItem(this.confirmSlot, GuiItem.simple(Material.LIME_CONCRETE, this.plugin.getConfigManager().color("<green>Confirm")));
      }

      if (itemsCfg != null && itemsCfg.contains("deny")) {
         this.placeConfigItem(inv, this.denySlot, itemsCfg.getConfigurationSection("deny"));
      } else {
         inv.setItem(this.denySlot, GuiItem.simple(Material.RED_CONCRETE, this.plugin.getConfigManager().color("<red>Cancel")));
      }

      this.setInventory(inv);
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         p.closeInventory();
         if (this.onDeny != null) {
            this.onDeny.run();
         }
      } else {
         if (slot == this.confirmSlot) {
            this.getViewer().closeInventory();
            if (this.onConfirm != null) {
               this.onConfirm.run();
            }
         } else if (slot == this.denySlot) {
            this.getViewer().closeInventory();
            if (this.onDeny != null) {
               this.onDeny.run();
            }
         }
      }
   }
}
