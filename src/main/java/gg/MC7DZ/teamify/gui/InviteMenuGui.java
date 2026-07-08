package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
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
import java.util.Map;
import java.util.UUID;

/**
 * Shows a single pending invite the player has received, with
 * accept/deny buttons. If a player has multiple invites, the command
 * layer opens one instance per invite or lets them pick a team by name.
 */
public class InviteMenuGui extends GuiHolder {

    private final Teamify plugin;
    private final Team team;
    private int acceptSlot;
    private int denySlot;

    public InviteMenuGui(Teamify plugin, Player viewer, Team team) {
        super(viewer);
        this.plugin = plugin;
        this.team = team;
        build();
    }

    private void build() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.invite-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Pending Invites"));
        int size = cfg.getInt("size", 27);

        Material acceptMat;
        Material denyMat;
        try {
            acceptMat = Material.valueOf(cfg.getString("accept-material", "LIME_DYE"));
        } catch (IllegalArgumentException e) {
            acceptMat = Material.LIME_DYE;
        }
        try {
            denyMat = Material.valueOf(cfg.getString("deny-material", "RED_DYE"));
        } catch (IllegalArgumentException e) {
            denyMat = Material.RED_DYE;
        }

        acceptSlot = size / 2 - 2;
        denySlot = size / 2 + 2;

        Inventory inv = Bukkit.createInventory(this, size, title);
        inv.setItem(size / 2, GuiItem.simple(Material.PAPER, "&bInvite from &f" + team.getName(),
                "&7Click accept or deny below."));
        inv.setItem(acceptSlot, GuiItem.simple(acceptMat, "&aAccept"));
        inv.setItem(denySlot, GuiItem.simple(denyMat, "&cDeny"));
        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();
        UUID uuid = p.getUniqueId();

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
            p.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().color("&aYou joined &b" + team.getName() + "&a!"));
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
            p.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().color("&7You declined the invite from &f" + team.getName() + "&7."));
        }
    }
}
