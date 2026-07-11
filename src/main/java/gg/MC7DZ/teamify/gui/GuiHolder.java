package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.util.SoundUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Marks an inventory as belonging to Teamify so the GuiListener knows
 * to intercept clicks, and lets us route clicks back to the right menu
 * without needing a giant if/else chain of raw inventory titles.
 */
public abstract class GuiHolder implements InventoryHolder {

    private static final Set<GuiHolder> activeGuis = new HashSet<>(); // Track all active GUI instances

    protected final Teamify plugin = Teamify.getInstance(); // Access plugin instance
    private final Player viewer;
    private Inventory inventory;

    // Slots the player is allowed to freely place/remove items in (e.g. an
    // "insert an item here" slot in the settings menu). Every other slot in
    // this GUI stays fully click-locked, same as before.
    private final Set<Integer> editableSlots = new HashSet<>();

    public GuiHolder(Player viewer) {
        this.viewer = viewer;
        activeGuis.add(this); // Add this GUI to the set of active GUIs
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

    /**
     * Subclasses must implement this to build their inventory content.
     */
    protected abstract void build();

    public void open() {
        viewer.openInventory(getInventory());
        SoundUtil.play(viewer, plugin.getConfigManager().getGuiOpenSound());
    }

    /**
     * Rebuilds the GUI content and re-opens it for the viewer.
     * Useful for refreshing GUIs after config changes.
     */
    public void rebuild() {
        // Ensure the viewer is still online and viewing this GUI
        if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == this) {
            viewer.closeInventory(); // Close to prevent inventory glitches during rebuild
            build(); // Rebuild the content
            open(); // Re-open the inventory
        }
    }

    /**
     * Called when the GUI is closed, to remove it from the active GUIs set.
     */
    public void onGuiClose() {
        activeGuis.remove(this);
    }

    /**
     * Returns the set of all currently active GuiHolder instances.
     */
    public static Set<GuiHolder> getActiveGuis() {
        return activeGuis;
    }

    /**
     * Adds a standard back button to the specified slot in the inventory.
     * @param inv The inventory to add the button to.
     * @param slot The slot where the button should be placed.
     */
    protected void setBackButton(Inventory inv, int slot) {
        ConfigurationSection backButtonCfg = plugin.getGuiConfig().getConfigurationSection("gui.back-button");
        if (backButtonCfg == null) return;

        inv.setItem(slot, GuiItem.fromConfig(getViewer(), backButtonCfg));
    }

    /**
     * Fills specified slots in the inventory with a filler item.
     * @param inv The inventory to fill.
     * @param fillerMaterial The material of the filler item.
     * @param slots The list of slots to fill.
     */
    protected void fillSlots(Inventory inv, Material fillerMaterial, List<Integer> slots) {
        ItemStack fillItem = GuiItem.simple(fillerMaterial, Component.text(" "));
        for (int slot : slots) {
            inv.setItem(slot, fillItem);
        }
    }
}