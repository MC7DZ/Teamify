package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PlayerSettingsMenuGui extends GuiHolder {
   private final Map<Integer, String> slotActions = new HashMap<>();
   private int backButtonSlot = -1;

   public PlayerSettingsMenuGui(Player viewer) {
      super(viewer);
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.player-settings-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Player Settings"));
      int size = cfg.getInt("size", 54);
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

      ConfigurationSection items = cfg.getConfigurationSection("items");
      if (items != null) {
         for (String key : items.getKeys(false)) {
            ConfigurationSection itemSec = items.getConfigurationSection(key);
            int slot = itemSec.getInt("slot");
            if (key.equals("back")) {
               this.backButtonSlot = slot;
               this.setBackButton(inv, this.backButtonSlot);
            } else {
               ItemStack item = GuiItem.fromConfig(this.getViewer(), itemSec);
               this.setSlotItem(inv, slot, itemSec, item);
               this.slotActions.put(slot, key);
            }
         }
      }

      this.setInventory(inv);
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         p.closeInventory();
         Team team = this.plugin.getTeamManager().getTeamOf(p.getUniqueId());
         if (team != null) {
            new MainMenuGui(p, team).open();
         }
      } else {
         String action = this.slotActions.get(slot);
         if (action != null) {
            switch (action) {
               case "players-list":
                  if (!this.plugin.getConfigManager().isPlayersListEnabled()) {
                     p.closeInventory();
                     p.sendMessage(this.plugin.getConfigManager().getMessage("players-list-disabled"));
                  } else {
                     new PlayersListMenuGui(p).open();
                  }
                  break;
               case "teams-list":
                  if (!this.plugin.getConfigManager().isListCommandEnabled()) {
                     p.closeInventory();
                     p.sendMessage(this.plugin.getConfigManager().getMessage("command-disabled"));
                  } else {
                     new TeamListMenuGui(p).open();
                  }
                  break;
               case "personal-requests":
                  new PersonalRequestsMenuGui(p).open();
            }
         }
      }
   }
}
