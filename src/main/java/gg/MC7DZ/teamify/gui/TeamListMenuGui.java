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

import java.util.*;

public class TeamListMenuGui extends GuiHolder {

    private final Teamify plugin;
    private final Map<Integer, UUID> slotToTeam = new HashMap<>();

    public TeamListMenuGui(Teamify plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
        build();
    }

    private void build() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.team-list-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8All Teams"));
        int size = cfg.getInt("size", 54);
        String sortBy = cfg.getString("sort-by", "LEVEL");

        List<Team> teams = new ArrayList<>(plugin.getTeamManager().getTeams());
        Comparator<Team> comparator = switch (sortBy) {
            case "MEMBERS" -> Comparator.comparingInt(Team::getSize).reversed();
            case "NAME" -> Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER);
            case "CLAIMS" -> Comparator.comparingInt(t -> t.getClaimedChunks().size());
            default -> Comparator.comparingInt(Team::getLevel).reversed();
        };
        teams.sort(comparator);

        Inventory inv = Bukkit.createInventory(this, size, title);
        int slot = 0;
        for (Team team : teams) {
            if (slot >= size) break;
            ItemStack item = GuiItem.simple(Material.WHITE_BANNER,
                    "&b" + team.getName() + " &7[" + team.getTag() + "]",
                    "&7Level: &f" + team.getLevel(),
                    "&7Members: &f" + team.getSize(),
                    "&7Claims: &f" + team.getClaimedChunks().size());
            inv.setItem(slot, item);
            slotToTeam.put(slot, team.getId());
            slot++;
        }
        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        UUID teamId = slotToTeam.get(slot);
        if (teamId == null) return;
        Team team = plugin.getTeamManager().getTeam(teamId);
        if (team == null) return;
        getViewer().sendMessage(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().color("&bViewing &f" + team.getName() +
                        " &7| Level " + team.getLevel() + " | " + team.getSize() + " members"));
    }
}
