package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.util.SoundUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class GuiHolder implements InventoryHolder {
   private static final Set<GuiHolder> activeGuis = new HashSet<>();
   protected final Teamify plugin = Teamify.getInstance();
   private final Player viewer;
   private Inventory inventory;
   private final Set<Integer> editableSlots = new HashSet<>();
   private final Set<Integer> hiddenSlots = new HashSet<>();

   public GuiHolder(Player viewer) {
      this.viewer = viewer;
      activeGuis.add(this);
   }

   public Inventory getInventory() {
      return this.inventory;
   }

   protected void setInventory(Inventory inventory) {
      this.inventory = inventory;
   }

   public Player getViewer() {
      return this.viewer;
   }

   protected void setEditableSlot(int slot, boolean editable) {
      if (editable) {
         this.editableSlots.add(slot);
      } else {
         this.editableSlots.remove(slot);
      }
   }

   public boolean isEditableSlot(int slot) {
      return this.editableSlots.contains(slot);
   }

   public boolean isHiddenSlot(int slot) {
      return this.hiddenSlots.contains(slot);
   }

   protected void setSlotItem(Inventory inv, int slot, ConfigurationSection itemCfg, ItemStack item) {
      if (itemCfg != null && itemCfg.getBoolean("hide", false)) {
         this.hiddenSlots.add(slot);
      } else {
         this.hiddenSlots.remove(slot);
         inv.setItem(slot, item);
      }
   }

   protected void placeConfigItem(Inventory inv, int slot, ConfigurationSection itemCfg, String... placeholders) {
      this.setSlotItem(inv, slot, itemCfg, GuiItem.fromConfig(this.getViewer(), itemCfg, placeholders));
   }

   public abstract void onClick(int slot, ClickType clickType);

   protected abstract void build();

   public void open() {
      this.viewer.openInventory(this.getInventory());
      SoundUtil.play(this.viewer, this.plugin.getConfigManager().getGuiOpenSound());
   }

   public void rebuild() {
      if (this.viewer.isOnline() && this.viewer.getOpenInventory().getTopInventory().getHolder() == this) {
         this.viewer.closeInventory();
         this.hiddenSlots.clear();
         this.build();
         this.open();
      }
   }

   public void onGuiClose() {
      activeGuis.remove(this);
   }

   public static Set<GuiHolder> getActiveGuis() {
      return activeGuis;
   }

   protected void setBackButton(Inventory inv, int slot) {
      ConfigurationSection backButtonCfg = this.plugin.getGuiConfig().getConfigurationSection("gui.back-button");
      if (backButtonCfg != null) {
         this.placeConfigItem(inv, slot, backButtonCfg);
      }
   }

   protected void fillSlots(Inventory inv, Material fillerMaterial, List<Integer> slots) {
      ItemStack fillItem = GuiItem.simple(fillerMaterial, Component.text(" "));

      for (int slot : slots) {
         inv.setItem(slot, fillItem);
      }
   }
}
