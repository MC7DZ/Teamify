package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
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

public class MainMenuGui extends GuiHolder {

    private final Team team;
    private final Map<Integer, String> slotActions = new HashMap<>();
    private int backButtonSlot = -1; // Initialize with an invalid slot

    public MainMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        build();
    }

    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.main-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Team Menu"));
        int size = cfg.getInt("size", 54);

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
                // Fallback to filling all empty slots if no specific filler-slots are defined
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
                }
                else {
                    ItemStack item = GuiItem.fromConfig(getViewer(), itemSec,
                            "team", team.getName(),
                            "level", String.valueOf(team.getLevel()),
                            "members", String.valueOf(team.getSize()));
                    inv.setItem(slot, item);
                    slotActions.put(slot, key);
                }
            }
        }

        // Add the "My Settings" head
        if (plugin.getConfigManager().isPlayerSettingsEnabled()) {
            ConfigurationSection playerSettingsCfg = plugin.getGuiConfig().getConfigurationSection("gui.main-menu.items.mysettings");
            if (playerSettingsCfg != null) {
                ItemStack playerSettingsItem = GuiItem.fromConfig(getViewer(), playerSettingsCfg);
                int playerSettingsSlot = playerSettingsCfg.getInt("slot", 53); // Default to 53 if not specified
                inv.setItem(playerSettingsSlot, playerSettingsItem);
                slotActions.put(playerSettingsSlot, "mysettings");
            }
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            p.closeInventory();
            return;
        }

        String action = slotActions.get(slot);
        if (action == null) return;

        switch (action) {
            case "info" -> {
                p.closeInventory();
                p.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().color("&bTeam: &f" + team.getName() +
                                " &7| &bLevel: &f" + team.getLevel() +
                                " &7| &bMembers: &f" + team.getSize()));
            }
            case "members" -> new MembersMenuGui(p, team).open();
            case "home" -> {
                p.closeInventory();
                p.performCommand("team home");
            }
            case "relations" -> new RelationsMenuGui(p, team).open();
            case "bank" -> {
                if (!plugin.getConfigManager().isBankEnabled()) {
                    p.closeInventory();
                    p.sendMessage(plugin.getConfigManager().getMessage("bank-disabled"));
                } else {
                    new BankMenuGui(p, team).open();
                }
            }
            case "settings" -> new SettingsMenuGui(p, team).open();
            case "echest" -> {
                if (!plugin.getConfigManager().isEchestEnabled()) {
                    p.closeInventory();
                    p.sendMessage(plugin.getConfigManager().getMessage("echest-disabled"));
                } else {
                    TeamRole role = team.getRole(p.getUniqueId());
                    if (role == null || !plugin.getConfig().getBoolean(
                            "roles.permissions." + role.name() + ".can-access-echest", false)) {
                        p.closeInventory();
                        p.sendMessage(plugin.getConfigManager().getMessage("not-enough-permission-role"));
                        return;
                    }
                    java.util.UUID activeViewerId = EchestMenuGui.getActiveViewer(team.getId());
                    if (activeViewerId != null && !activeViewerId.equals(p.getUniqueId())) {
                        org.bukkit.entity.Player activeViewer = org.bukkit.Bukkit.getPlayer(activeViewerId);
                        String viewerName = activeViewer != null
                                ? activeViewer.getName()
                                : plugin.getConfigManager().color("&7Unknown");
                        p.closeInventory();
                        p.sendMessage(plugin.getConfigManager().getMessage("echest-in-use", "player", viewerName));
                        return;
                    }
                    new EchestMenuGui(p, team).open();
                }
            }
            case "teams-list" -> new TeamListMenuGui(p).open();
            case "players-list" -> {
                if (!plugin.getConfigManager().isPlayersListEnabled()) {
                    p.closeInventory();
                    p.sendMessage(plugin.getConfigManager().getMessage("players-list-disabled")); // Assuming a message for disabled feature
                } else {
                    new PlayersListMenuGui(p).open();
                }
            }
            case "requests" -> new RequestsMenuGui(p, team).open();
            case "mysettings" -> {
                if (plugin.getConfigManager().isPlayerSettingsEnabled()) {
                    new PlayerSettingsMenuGui(p).open();
                } else {
                    p.closeInventory();
                    p.sendMessage(plugin.getConfigManager().getMessage("player-settings-disabled"));
                }
            }
        }
    }
}