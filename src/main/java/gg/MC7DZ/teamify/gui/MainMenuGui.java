package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class MainMenuGui extends GuiHolder {

    private final Teamify plugin;
    private final Team team;
    private final Map<Integer, String> slotActions = new HashMap<>();

    public MainMenuGui(Teamify plugin, Player viewer, Team team) {
        super(viewer);
        this.plugin = plugin;
        this.team = team;
        build();
    }

    private void build() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.main-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Team Menu"));
        int size = cfg.getInt("size", 27);

        Inventory inv = Bukkit.createInventory(this, size, title);

        if (cfg.getBoolean("fill-empty-slots", true)) {
            Material filler;
            try {
                filler = Material.valueOf(cfg.getString("filler-item", "GRAY_STAINED_GLASS_PANE"));
            } catch (IllegalArgumentException e) {
                filler = Material.GRAY_STAINED_GLASS_PANE;
            }
            ItemStack fillItem = GuiItem.simple(filler, " ");
            for (int i = 0; i < size; i++) {
                inv.setItem(i, fillItem);
            }
        }

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSec = items.getConfigurationSection(key);
                int slot = itemSec.getInt("slot");
                ItemStack item = GuiItem.fromConfig(itemSec,
                        "team", team.getName(),
                        "level", String.valueOf(team.getLevel()),
                        "members", String.valueOf(team.getSize()));
                inv.setItem(slot, item);
                slotActions.put(slot, key);
            }
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        String action = slotActions.get(slot);
        if (action == null) return;
        Player p = getViewer();

        switch (action) {
            case "info" -> {
                p.closeInventory();
                p.sendMessage(plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().color("&bTeam: &f" + team.getName() +
                                " &7| &bLevel: &f" + team.getLevel() +
                                " &7| &bMembers: &f" + team.getSize()));
            }
            case "members" -> new MembersMenuGui(plugin, p, team).open();
            case "home" -> {
                p.closeInventory();
                p.performCommand("team home");
            }
            case "relations" -> new RelationsMenuGui(plugin, p, team).open();
            case "bank" -> {
                if (!plugin.getConfigManager().isBankEnabled()) {
                    p.closeInventory();
                    p.sendMessage(plugin.getConfigManager().getMessage("bank-disabled"));
                } else {
                    new BankMenuGui(plugin, p, team).open();
                }
            }
            case "settings" -> new SettingsMenuGui(plugin, p, team).open();
        }
    }
}
