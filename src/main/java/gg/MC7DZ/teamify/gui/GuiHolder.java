package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashSet;
import java.util.Set;

/**
 * Marks an inventory as belonging to Teamify so the GuiListener knows
 * to intercept clicks, and lets us route clicks back to the right menu
 * without needing a giant if/else chain of raw inventory titles.
 */
public abstract class GuiHolder implements InventoryHolder {

    private final Player viewer;
    private Inventory inventory;

    // Slots the player is allowed to freely place/remove items in (e.g. an
    // "insert an item here" slot in the settings menu). Every other slot in
    // this GUI stays fully click-locked, same as before.
    private final Set<Integer> editableSlots = new HashSet<>();

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

    /** Marks a slot as editable (see {@link #editableSlots}). */
    protected void setEditableSlot(int slot, boolean editable) {
        if (editable) editableSlots.add(slot);
        else editableSlots.remove(slot);
    }

    public boolean isEditableSlot(int slot) {
        return editableSlots.contains(slot);
    }

    /**
     * Called by GuiListener when a slot in this inventory is clicked.
     */
    public abstract void onClick(int slot, org.bukkit.event.inventory.ClickType clickType);

    public void open() {
        viewer.openInventory(getInventory());
        SoundUtil.play(viewer, Teamify.getInstance().getConfigManager().getGuiOpenSound());
    }
}
