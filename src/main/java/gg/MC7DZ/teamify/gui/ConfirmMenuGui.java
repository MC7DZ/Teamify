package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ConfirmMenuGui extends GuiHolder {

    private final Teamify plugin;
    private final Runnable onConfirm;
    private final Runnable onDeny;
    private int confirmSlot;
    private int denySlot;

    public ConfirmMenuGui(Teamify plugin, Player viewer, Runnable onConfirm, Runnable onDeny) {
        super(viewer);
        this.plugin = plugin;
        this.onConfirm = onConfirm;
        this.onDeny = onDeny;
        build();
    }

    private void build() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.confirm-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Are you sure?"));
        int size = cfg.getInt("size", 27);
        confirmSlot = cfg.getInt("confirm-slot", 11);
        denySlot = cfg.getInt("deny-slot", 15);

        Material confirmMat;
        Material denyMat;
        try {
            confirmMat = Material.valueOf(cfg.getString("confirm-material", "LIME_CONCRETE"));
        } catch (IllegalArgumentException e) {
            confirmMat = Material.LIME_CONCRETE;
        }
        try {
            denyMat = Material.valueOf(cfg.getString("deny-material", "RED_CONCRETE"));
        } catch (IllegalArgumentException e) {
            denyMat = Material.RED_CONCRETE;
        }

        Inventory inv = Bukkit.createInventory(this, size, title);
        inv.setItem(confirmSlot, GuiItem.simple(confirmMat, "&aConfirm"));
        inv.setItem(denySlot, GuiItem.simple(denyMat, "&cCancel"));
        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == confirmSlot) {
            getViewer().closeInventory();
            if (onConfirm != null) onConfirm.run();
        } else if (slot == denySlot) {
            getViewer().closeInventory();
            if (onDeny != null) onDeny.run();
        }
    }
}
