package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team; // Import Team
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
        Component title = plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Player Settings"));
        int size = cfg.getInt("size", 54); // Updated default size to 54
        
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

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSec = items.getConfigurationSection(key);
                int slot = itemSec.getInt("slot");

                if (key.equals("back")) {
                    backButtonSlot = slot;
                    setBackButton(inv, backButtonSlot);
                } else {
                    ItemStack item = GuiItem.fromConfig(getViewer(), itemSec);
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
            Team team = plugin.getTeamManager().getTeamOf(p.getUniqueId());
            if (team != null) { // Null check added here
                new MainMenuGui(p, team).open();
            }
            return;
        }

        String action = slotActions.get(slot);
        if (action == null) return;

        switch (action) {
            case "players-list" -> {
                if (!plugin.getConfigManager().isPlayersListEnabled()) {
                    p.closeInventory();
                    p.sendMessage(plugin.getConfigManager().getMessage("players-list-disabled"));
                } else {
                    new PlayersListMenuGui(p).open();
                }
            }
            case "teams-list" -> {
                if (!plugin.getConfigManager().isListCommandEnabled()) {
                    p.closeInventory();
                    p.sendMessage(plugin.getConfigManager().getMessage("command-disabled"));
                } else {
                    new TeamListMenuGui(p).open();
                }
            }
            case "personal-requests" -> new PersonalRequestsMenuGui(p).open();
            default -> {
                // No specific action for this item yet
            }
        }
    }
}