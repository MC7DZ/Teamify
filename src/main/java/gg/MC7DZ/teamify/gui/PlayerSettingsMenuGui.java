package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
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

public class PlayerSettingsMenuGui extends GuiHolder {

    private final Map<Integer, String> slotActions = new HashMap<>();
    private int backButtonSlot = -1;

    public PlayerSettingsMenuGui(Player viewer) {
        super(viewer);
        build();
    }

    @Override
    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.player-settings-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Player Settings"));
        int size = cfg.getInt("size", 27); // Default size for player settings

        Inventory inv = Bukkit.createInventory(this, size, titleComponent(title));

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
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, GuiItem.simple(filler, " "));
                }
            }
        }

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSec = items.getConfigurationSection(key);
                int slot = itemSec.getInt("slot");

                if (key.equals("back")) {
                    backButtonSlot = slot;
                    ConfigurationSection backButtonCfg = plugin.getGuiConfig().getConfigurationSection("gui.back-button");
                    if (backButtonCfg != null) {
                        setBackButton(inv, backButtonSlot,
                                plugin.getConfigManager().color(backButtonCfg.getString("name", "&cBack")),
                                backButtonCfg.getStringList("lore"));
                    }
                } else {
                    ItemStack item = GuiItem.fromConfig(itemSec);
                    inv.setItem(slot, item);
                    slotActions.put(slot, key);
                }
            }
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            p.closeInventory();
            // If the player was in a team, open the MainMenuGui, otherwise just close.
            if (plugin.getTeamManager().isInTeam(p.getUniqueId())) {
                new MainMenuGui(p, plugin.getTeamManager().getTeamOf(p.getUniqueId())).open();
            }
            return;
        }

        String action = slotActions.get(slot);
        if (action == null) return;

        // Handle specific player settings actions here
        switch (action) {
            // Example: Toggle a setting
            // case "toggle-visibility":
            //     // Logic to toggle player visibility setting
            //     break;
            default:
                // No specific action for this item yet
                break;
        }
    }
}
