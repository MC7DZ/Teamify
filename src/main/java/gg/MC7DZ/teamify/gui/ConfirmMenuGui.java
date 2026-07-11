package gg.MC7DZ.teamify.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConfirmMenuGui extends GuiHolder {

    private final Runnable onConfirm;
    private final Runnable onDeny;
    private int confirmSlot;
    private int denySlot;
    private int backButtonSlot = -1; // Initialize with an invalid slot

    public ConfirmMenuGui(Player viewer, Runnable onConfirm, Runnable onDeny) {
        super(viewer);
        this.onConfirm = onConfirm;
        this.onDeny = onDeny;
        build();
    }

    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.confirm-menu");
        Component title = plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Are you sure?"));
        int size = cfg.getInt("size", 54);
        
        // Load slots from gui.yml items section
        ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
        if (itemsCfg != null) {
            confirmSlot = itemsCfg.getInt("confirm.slot", 20);
            denySlot = itemsCfg.getInt("deny.slot", 24);
            backButtonSlot = itemsCfg.getInt("back.slot", 45);
        } else {
            // Fallback to default hardcoded slots if items section is missing
            confirmSlot = 20;
            denySlot = 24;
            backButtonSlot = 45;
        }

        Inventory inv = Bukkit.createInventory(this, size, title);

        // Fill empty slots if configured
        if (cfg.getBoolean("fill-empty-slots", true)) {
            Material filler;
            try {
                filler = Material.valueOf(cfg.getString("filler-item", "GRAY_STAINED_GLASS_PANE"));
            } catch (IllegalArgumentException e) {
                filler = Material.GRAY_STAINED_GLASS_PANE;
            }
            List<Integer> fillerSlots = cfg.getIntegerList("filler-slots");
            if (fillerSlots != null && !fillerSlots.isEmpty()) {
                fillSlots(inv, filler, fillerSlots);
            } else {
                // Fallback to filling all empty slots if no specific filler-slots are defined
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
                }
            }
        }

        // Handle back button
        if (itemsCfg != null && itemsCfg.contains("back")) {
            setBackButton(inv, backButtonSlot);
        }

        // Confirm and Deny buttons
        if (itemsCfg != null && itemsCfg.contains("confirm")) {
            inv.setItem(confirmSlot, GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("confirm")));
        } else {
            inv.setItem(confirmSlot, GuiItem.simple(Material.LIME_CONCRETE, plugin.getConfigManager().color("<green>Confirm")));
        }
        if (itemsCfg != null && itemsCfg.contains("deny")) {
            inv.setItem(denySlot, GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("deny")));
        } else {
            inv.setItem(denySlot, GuiItem.simple(Material.RED_CONCRETE, plugin.getConfigManager().color("<red>Cancel")));
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            p.closeInventory(); // Simply close the confirmation GUI
            if (onDeny != null) onDeny.run(); // Run deny action if defined for back button
            return;
        }

        if (slot == confirmSlot) {
            getViewer().closeInventory();
            if (onConfirm != null) onConfirm.run();
        } else if (slot == denySlot) {
            getViewer().closeInventory();
            if (onDeny != null) onDeny.run();
        }
    }
}