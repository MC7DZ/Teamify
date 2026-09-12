package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.util.SoundUtil;
import java.util.HashMap;
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

public class EchestMenuGui extends GuiHolder {
   private static final int SIZE = 54;
   private static final Map<UUID, UUID> activeViewers = new HashMap<>();
   private final Team team;
   private final int usableSlots;

   public static UUID getActiveViewer(UUID teamId) {
      UUID viewerId = activeViewers.get(teamId);
      if (viewerId == null) {
         return null;
      }

      Player viewer = Bukkit.getPlayer(viewerId);
      if (viewer != null && viewer.isOnline()) {
         return viewerId;
      }

      activeViewers.remove(teamId);
      return null;
   }

   public EchestMenuGui(Player viewer, Team team) {
      super(viewer);
      this.team = team;
      this.usableSlots = this.plugin.getConfigManager().getEchestSlots();
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.echest-menu");
      Component title = this.plugin
         .getConfigManager()
         .color(cfg != null ? cfg.getString("title", "<dark_gray><bold>Team Enderchest") : "<dark_gray><bold>Team Enderchest");

      Material lockedFiller;
      try {
         lockedFiller = Material.valueOf(
            (cfg != null ? cfg.getString("locked-filler-item", "GRAY_STAINED_GLASS_PANE") : "GRAY_STAINED_GLASS_PANE").toUpperCase()
         );
      } catch (IllegalArgumentException e) {
         lockedFiller = Material.GRAY_STAINED_GLASS_PANE;
      }

      Inventory inv = Bukkit.createInventory(this, 54, title);
      ItemStack[] contents = this.team.getEchestContents();

      for (int i = 0; i < 54; i++) {
         if (i < this.usableSlots) {
            this.setEditableSlot(i, true);
            inv.setItem(i, contents[i]);
         } else {
            this.setEditableSlot(i, false);
            inv.setItem(i, GuiItem.simple(lockedFiller, Component.text(" ")));
         }
      }

      this.setInventory(inv);
   }

   private void syncContentsToTeam() {
      Inventory inv = this.getInventory();
      if (inv != null) {
         ItemStack[] contents = this.team.getEchestContents();

         for (int i = 0; i < this.usableSlots; i++) {
            contents[i] = inv.getItem(i);
         }

         this.team.setEchestContents(contents);
         this.plugin.getTeamManager().saveTeam(this.team);
      }
   }

   @Override
   public void open() {
      activeViewers.put(this.team.getId(), this.getViewer().getUniqueId());
      this.getViewer().openInventory(this.getInventory());
      SoundUtil.play(this.getViewer(), "block.ender_chest.open");
   }

   @Override
   public void onGuiClose() {
      this.syncContentsToTeam();
      activeViewers.remove(this.team.getId(), this.getViewer().getUniqueId());
      SoundUtil.play(this.getViewer(), "block.ender_chest.close");
      super.onGuiClose();
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
   }
}
