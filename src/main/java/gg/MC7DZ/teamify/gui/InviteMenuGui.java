package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
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

/**
 * Shows a single pending invite the player has received, with
 * accept/deny buttons. If a player has multiple invites, the command
 * layer opens one instance per invite or lets them pick a team by name.
 */
public class InviteMenuGui extends GuiHolder {

    private final Team team;
    private int acceptSlot;
    private int denySlot;
    private int backButtonSlot = -1; // Initialize with an invalid slot

    public InviteMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        build();
    }

    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.invite-menu");
        Component title = plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Pending Invites"));
        int size = cfg.getInt("size", 54);
        
        // Load slots from gui.yml items section
        ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
        if (itemsCfg != null) {
            acceptSlot = itemsCfg.getInt("accept.slot", 30);
            denySlot = itemsCfg.getInt("deny.slot", 32);
            backButtonSlot = itemsCfg.getInt("back.slot", 45);
        } else {
            // Fallback to default hardcoded slots if items section is missing
            acceptSlot = 30;
            denySlot = 32;
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

        // Central item for invite details
        if (itemsCfg != null && itemsCfg.contains("invite-info")) {
            inv.setItem(itemsCfg.getInt("invite-info.slot", 22), GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("invite-info"), "team", team.getName()));
        } else {
            inv.setItem(22, GuiItem.simple(Material.PAPER, plugin.getConfigManager().color("<aqua>Invite from <white>" + team.getName()),
                    plugin.getConfigManager().color("<gray>Click accept or deny below.")));
        }

        // Accept and Deny buttons
        if (itemsCfg != null && itemsCfg.contains("accept")) {
            inv.setItem(acceptSlot, GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("accept")));
        } else {
            inv.setItem(acceptSlot, GuiItem.simple(Material.LIME_DYE, plugin.getConfigManager().color("<green>Accept")));
        }
        if (itemsCfg != null && itemsCfg.contains("deny")) {
            inv.setItem(denySlot, GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("deny")));
        } else {
            inv.setItem(denySlot, GuiItem.simple(Material.RED_DYE, plugin.getConfigManager().color("<red>Deny")));
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();
        UUID uuid = p.getUniqueId();

        if (slot == backButtonSlot) {
            p.closeInventory(); // Just close, as there's no specific "previous" GUI for invites
            return;
        }

        if (slot == acceptSlot) {
            p.closeInventory();
            if (!team.hasInvite(uuid)) {
                p.sendMessage(plugin.getConfigManager().getMessage("invite-received"));
                return;
            }
            if (plugin.getTeamManager().isInTeam(uuid)) {
                p.sendMessage(plugin.getConfigManager().getMessage("already-in-team"));
                return;
            }
            int maxMembers = plugin.getConfigManager().getMaxMembers();
            if (maxMembers > 0 && team.getSize() >= maxMembers) {
                p.sendMessage(plugin.getConfigManager().getMessage("team-full"));
                return;
            }
            team.removeInvite(uuid);
            plugin.getTeamManager().addMember(team, uuid, TeamRole.MEMBER);
            plugin.getTeamManager().saveTeam(team);
            plugin.getVisibilityManager().refreshTeamAndAllies(team);
            p.sendMessage(plugin.getConfigManager().getPrefix().append(
                    plugin.getConfigManager().color("<green>You joined <aqua>" + team.getName() + "<green>!")));
            for (UUID memberId : team.getMembers().keySet()) {
                if (memberId.equals(uuid)) continue;
                Player member = org.bukkit.Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(plugin.getConfigManager().getMessage("player-joined-broadcast", "player", p.getName()));
                }
            }
        } else if (slot == denySlot) {
            p.closeInventory();
            team.removeInvite(uuid);
            p.sendMessage(plugin.getConfigManager().getPrefix().append(
                    plugin.getConfigManager().color("<gray>You declined the invite from <white>" + team.getName() + "<gray>.")));
        }
    }
}