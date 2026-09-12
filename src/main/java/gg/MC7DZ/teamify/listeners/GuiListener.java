package gg.MC7DZ.teamify.listeners;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.gui.GuiHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GuiListener implements Listener {
   private final Teamify plugin;

   public GuiListener(Teamify plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getInventory().getHolder() instanceof GuiHolder guiHolder) {
         int slot = event.getRawSlot();
         if (slot >= 0 && slot < event.getInventory().getSize()) {
            boolean editable = guiHolder.isEditableSlot(slot) && !event.getClick().isShiftClick();
            if (!editable) {
               event.setCancelled(true);
            }

            if (!guiHolder.isHiddenSlot(slot)) {
               guiHolder.onClick(slot, event.getClick());
            }
         } else {
            if (event.getClick().isShiftClick()) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof GuiHolder guiHolder) {
         int topSize = event.getView().getTopInventory().getSize();

         for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !guiHolder.isEditableSlot(rawSlot)) {
               event.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getInventory().getHolder() instanceof GuiHolder guiHolder) {
         guiHolder.onGuiClose();
      }
   }
}
