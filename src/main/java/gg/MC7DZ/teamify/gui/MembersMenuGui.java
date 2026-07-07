package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class MembersMenuGui extends GuiHolder {

    private final Teamify plugin;
    private final Team team;
    private final Map<Integer, UUID> slotToMember = new HashMap<>();

    public MembersMenuGui(Teamify plugin, Player viewer, Team team) {
        super(viewer);
        this.plugin = plugin;
        this.team = team;
        build();
    }

    private void build() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.members-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Team Members"));
        int size = cfg.getInt("size", 54);
        boolean showOnline = cfg.getBoolean("show-online-status", true);
        String onlineColor = plugin.getConfigManager().color(cfg.getString("online-name-color", "&a"));
        String offlineColor = plugin.getConfigManager().color(cfg.getString("offline-name-color", "&7"));

        Inventory inv = Bukkit.createInventory(this, size, title);

        int slot = 0;
        for (Map.Entry<UUID, TeamRole> entry : team.getMembers().entrySet()) {
            if (slot >= size) break;
            UUID uuid = entry.getKey();
            TeamRole role = entry.getValue();
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            boolean online = op.isOnline();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(op);
                String color = showOnline ? (online ? onlineColor : offlineColor) : "&f";
                meta.setDisplayName(plugin.getConfigManager().color(color + op.getName()));
                List<String> lore = new ArrayList<>();
                lore.add(plugin.getConfigManager().color("&7Role: &f" + role.name()));
                lore.add(plugin.getConfigManager().color("&7Status: " + (online ? "&aOnline" : "&cOffline")));
                meta.setLore(lore);
                head.setItemMeta(meta);
            }

            inv.setItem(slot, head);
            slotToMember.put(slot, uuid);
            slot++;
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        UUID target = slotToMember.get(slot);
        if (target == null) return;
        Player p = getViewer();
        p.sendMessage(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().color("&7Selected: &f" + Bukkit.getOfflinePlayer(target).getName()));
        // Right-click to promote/demote/kick could be wired here based on the viewer's role.
    }
}
