package gg.MC7DZ.teamify.listeners;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.gui.GuiHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent; // Import InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    private final Teamify plugin;

    public GuiListener(Teamify plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder guiHolder)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            // Click was in the player's own inventory, not the GUI itself -
            // still block it (prevents shift-clicking items in) unless it's
            // a plain click with nothing selected.
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        // Editable slots (e.g. the "put your item here" slot in the
        // settings menu) allow normal place/pickup interaction; every other
        // slot stays fully locked like before. Shift-clicks are still
        // blocked everywhere to avoid the auto-move logic skipping our
        // editable-slot check.
        boolean editable = guiHolder.isEditableSlot(slot) && !event.getClick().isShiftClick();
        if (!editable) {
            event.setCancelled(true);
        }
        guiHolder.onClick(slot, event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder guiHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !guiHolder.isEditableSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GuiHolder guiHolder) {
            guiHolder.onGuiClose(); // Call the new method to remove from active GUIs
        }
    }
}