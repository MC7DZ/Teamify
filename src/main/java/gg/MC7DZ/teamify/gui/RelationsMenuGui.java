package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class RelationsMenuGui extends GuiHolder {

    private final Teamify plugin;
    private final Team team;
    private final Map<Integer, UUID> slotToTeam = new HashMap<>();

    public RelationsMenuGui(Teamify plugin, Player viewer, Team team) {
        super(viewer);
        this.plugin = plugin;
        this.team = team;
        build();
    }

    private void build() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.relations-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Allies & Enemies"));
        int size = cfg.getInt("size", 45);

        Material allyMat = parse(cfg.getString("ally-material", "LIME_WOOL"), Material.LIME_WOOL);
        Material enemyMat = parse(cfg.getString("enemy-material", "RED_WOOL"), Material.RED_WOOL);
        Material neutralMat = parse(cfg.getString("neutral-material", "GRAY_WOOL"), Material.GRAY_WOOL);

        Inventory inv = Bukkit.createInventory(this, size, title);

        int slot = 0;
        for (var entry : team.getRelations().entrySet()) {
            if (slot >= size) break;
            UUID otherId = entry.getKey();
            RelationType type = entry.getValue();
            Team other = plugin.getTeamManager().getTeam(otherId);
            if (other == null) continue;

            Material mat = switch (type) {
                case ALLY -> allyMat;
                case ENEMY -> enemyMat;
                default -> neutralMat;
            };

            ItemStack item = GuiItem.simple(mat,
                    "&f" + other.getName(),
                    "&7Relation: " + type.name(),
                    "&7Level: " + other.getLevel());
            inv.setItem(slot, item);
            slotToTeam.put(slot, otherId);
            slot++;
        }

        setInventory(inv);
    }

    private Material parse(String s, Material fallback) {
        try {
            return Material.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        UUID otherId = slotToTeam.get(slot);
        if (otherId == null) return;
        Team other = plugin.getTeamManager().getTeam(otherId);
        if (other == null) return;
        getViewer().sendMessage(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().color("&7Relation with &f" + other.getName() +
                        "&7: &f" + team.getRelation(otherId).name()));
    }
}
