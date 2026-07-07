package gg.MC7DZ.teamify.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as belonging to Teamify so the GuiListener knows
 * to intercept clicks, and lets us route clicks back to the right menu
 * without needing a giant if/else chain of raw inventory titles.
 */
public abstract class GuiHolder implements InventoryHolder {

    private final Player viewer;
    private Inventory inventory;

    public GuiHolder(Player viewer) {
        this.viewer = viewer;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Player getViewer() {
        return viewer;
    }

    /**
     * Called by GuiListener when a slot in this inventory is clicked.
     */
    public abstract void onClick(int slot, org.bukkit.event.inventory.ClickType clickType);

    public void open() {
        viewer.openInventory(getInventory());
    }
}
