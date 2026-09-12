package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MainMenuGui extends GuiHolder {
   private final Team team;
   private final Map<Integer, String> slotActions = new HashMap<>();
   private int backButtonSlot = -1;

   public MainMenuGui(Player viewer, Team team) {
      super(viewer);
      this.team = team;
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.main-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Team Menu"));
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
               ItemStack item = GuiItem.fromConfig(
                  this.getViewer(),
                  itemSec,
                  "team",
                  this.team.getName(),
                  "level",
                  String.valueOf(this.team.getLevel()),
                  "members",
                  String.valueOf(this.team.getSize())
               );
               this.setSlotItem(inv, slot, itemSec, item);
               this.slotActions.put(slot, key);
            }
         }
      }

      if (this.plugin.getConfigManager().isPlayerSettingsEnabled()) {
         ConfigurationSection playerSettingsCfg = this.plugin.getGuiConfig().getConfigurationSection("gui.main-menu.items.mysettings");
         if (playerSettingsCfg != null) {
            ItemStack playerSettingsItem = GuiItem.fromConfig(this.getViewer(), playerSettingsCfg);
            int playerSettingsSlot = playerSettingsCfg.getInt("slot", 53);
            this.setSlotItem(inv, playerSettingsSlot, playerSettingsCfg, playerSettingsItem);
            this.slotActions.put(playerSettingsSlot, "mysettings");
         }
      }

      this.setInventory(inv);
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         p.closeInventory();
      } else {
         String action = this.slotActions.get(slot);
         if (action != null) {
            switch (action) {
               case "info":
                  p.closeInventory();
                  p.sendMessage(
                     this.plugin
                        .getConfigManager()
                        .getPrefix()
                        .append(
                           this.plugin
                              .getConfigManager()
                              .color(
                                 "<aqua>Team: <white>"
                                    + this.team.getName()
                                    + " <gray>| <aqua>Level: <white>"
                                    + this.team.getLevel()
                                    + " <gray>| <aqua>Members: <white>"
                                    + this.team.getSize()
                              )
                        )
                  );
                  break;
               case "members":
                  new MembersMenuGui(p, this.team).open();
                  break;
               case "home":
                  p.closeInventory();
                  p.performCommand("team home");
                  break;
               case "relations":
                  new RelationsMenuGui(p, this.team).open();
                  break;
               case "bank":
                  if (!this.plugin.getConfigManager().isBankEnabled()) {
                     p.closeInventory();
                     p.sendMessage(this.plugin.getConfigManager().getMessage("bank-disabled"));
                  } else {
                     new BankMenuGui(p, this.team).open();
                  }
                  break;
               case "settings":
                  new SettingsMenuGui(p, this.team).open();
                  break;
               case "echest":
                  if (!this.plugin.getConfigManager().isEchestEnabled()) {
                     p.closeInventory();
                     p.sendMessage(this.plugin.getConfigManager().getMessage("echest-disabled"));
                  } else {
                     TeamRole role = this.team.getRole(p.getUniqueId());
                     if (role == null || !this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-access-echest", false)) {
                        p.closeInventory();
                        p.sendMessage(this.plugin.getConfigManager().getMessage("not-enough-permission-role"));
                        return;
                     }

                     UUID activeViewerId = EchestMenuGui.getActiveViewer(this.team.getId());
                     if (activeViewerId != null && !activeViewerId.equals(p.getUniqueId())) {
                        Player activeViewer = Bukkit.getPlayer(activeViewerId);
                        String viewerName = activeViewer != null ? activeViewer.getName() : "Unknown";
                        p.closeInventory();
                        p.sendMessage(this.plugin.getConfigManager().getMessage("echest-in-use", "player", viewerName));
                        return;
                     }

                     new EchestMenuGui(p, this.team).open();
                  }
                  break;
               case "teams-list":
                  new TeamListMenuGui(p).open();
                  break;
               case "players-list":
                  if (!this.plugin.getConfigManager().isPlayersListEnabled()) {
                     p.closeInventory();
                     p.sendMessage(this.plugin.getConfigManager().getMessage("players-list-disabled"));
                  } else {
                     new PlayersListMenuGui(p).open();
                  }
                  break;
               case "requests":
                  new RequestsMenuGui(p, this.team).open();
                  break;
               case "mysettings":
                  if (this.plugin.getConfigManager().isPlayerSettingsEnabled()) {
                     new PlayerSettingsMenuGui(p).open();
                  } else {
                     p.closeInventory();
                     p.sendMessage(this.plugin.getConfigManager().getMessage("player-settings-disabled"));
                  }
            }
         }
      }
   }
}
