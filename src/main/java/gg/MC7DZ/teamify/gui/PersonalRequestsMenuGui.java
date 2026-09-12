package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PersonalRequestsMenuGui extends GuiHolder {
   private final Map<Integer, UUID> slotToTeamId = new HashMap<>();
   private int backButtonSlot = -1;

   public PersonalRequestsMenuGui(Player viewer) {
      super(viewer);
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.personal-requests-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Personal Requests"));
      int size = cfg.getInt("size", 54);
      Inventory inv = Bukkit.createInventory(this, size, title);
      Set<Integer> reservedSlots = new HashSet<>();
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
            reservedSlots.addAll(fillerSlots);
         } else {
            for (int i = 0; i < size; i++) {
               inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
            }
         }
      }

      ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
      if (itemsCfg != null && itemsCfg.contains("back")) {
         this.backButtonSlot = itemsCfg.getInt("back.slot", -1);
         if (this.backButtonSlot != -1) {
            this.setBackButton(inv, this.backButtonSlot);
            reservedSlots.add(this.backButtonSlot);
         }
      }

      List<Team> invites = this.plugin.getTeamManager().getPendingTeamInvites(this.getViewer().getUniqueId());
      int currentSlot = 0;

      for (Team team : invites) {
         while (currentSlot < size && reservedSlots.contains(currentSlot)) {
            currentSlot++;
         }

         if (currentSlot >= size) {
            break;
         }

         Component itemName = this.plugin.getConfigManager().color("<aqua>Invite from <white>" + team.getName());
         List<Component> itemLore = new ArrayList<>();
         itemLore.add(this.plugin.getConfigManager().color("<gray>Click to accept or deny."));
         ItemStack inviteItem;
         if (team.hasCustomItem()) {
            inviteItem = GuiItem.withOverrides(team.getCustomItem(), itemName, itemLore);
         } else {
            inviteItem = GuiItem.simple(Material.PAPER, itemName, itemLore.toArray(new Component[0]));
         }

         inv.setItem(currentSlot, inviteItem);
         this.slotToTeamId.put(currentSlot, team.getId());
         currentSlot++;
      }

      this.setInventory(inv);
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      if (slot == this.backButtonSlot) {
         new PlayerSettingsMenuGui(p).open();
      } else {
         UUID teamId = this.slotToTeamId.get(slot);
         if (teamId != null) {
            Team team = this.plugin.getTeamManager().getTeam(teamId);
            if (team != null) {
               new InviteMenuGui(p, team).open();
            } else {
               p.sendMessage(this.plugin.getConfigManager().getMessage("team-not-found"));
               this.rebuild();
            }
         }
      }
   }
}
