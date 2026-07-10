package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.team.TeamRole;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Opened when a viewer clicks a member's head in {@link MembersMenuGui}.
 * Presents kick/promote/demote/transfer options (as configured), each of
 * which routes through {@link ConfirmMenuGui} before anything happens.
 */
public class MemberActionsMenuGui extends GuiHolder {

    private final Team team;
    private final UUID targetId;

    private int backButtonSlot = -1;
    private int kickSlot = -1;
    private int promoteSlot = -1;
    private int demoteSlot = -1;
    private int transferSlot = -1;

    public MemberActionsMenuGui(Player viewer, Team team, UUID targetId) {
        super(viewer);
        this.team = team;
        this.targetId = targetId;
        build();
    }

    @Override
    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.member-actions-menu");
        int size = cfg.getInt("size", 54);

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        TeamRole targetRole = team.getRole(targetId);

        String title = plugin.getConfigManager().color(
                cfg.getString("title", "&8&lManage {player}").replace("{player}", targetName));

        Inventory inv = Bukkit.createInventory(this, size, titleComponent(title));

        // Fill empty slots if configured
        java.util.Set<Integer> reservedSlots = new java.util.HashSet<>();
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
                reservedSlots.addAll(fillerSlots);
            } else {
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, GuiItem.simple(filler, " "));
                }
            }
        }

        ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
        if (itemsCfg != null) {
            if (itemsCfg.contains("back")) {
                backButtonSlot = itemsCfg.getInt("back.slot", -1);
                if (backButtonSlot != -1) {
                    ConfigurationSection backButtonData = plugin.getGuiConfig().getConfigurationSection("gui.back-button");
                    if (backButtonData != null) {
                        setBackButton(inv, backButtonSlot,
                                plugin.getConfigManager().color(backButtonData.getString("name", "&cBack")),
                                backButtonData.getStringList("lore"));
                    }
                }
            }
            if (itemsCfg.contains("kick")) {
                kickSlot = itemsCfg.getInt("kick.slot", -1);
                inv.setItem(kickSlot, GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("kick"),
                        "player", targetName, "role", targetRole.name()));
            }
            if (itemsCfg.contains("promote")) {
                promoteSlot = itemsCfg.getInt("promote.slot", -1);
                inv.setItem(promoteSlot, GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("promote"),
                        "player", targetName, "role", targetRole.name()));
            }
            if (itemsCfg.contains("demote")) {
                demoteSlot = itemsCfg.getInt("demote.slot", -1);
                inv.setItem(demoteSlot, GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("demote"),
                        "player", targetName, "role", targetRole.name()));
            }
            if (itemsCfg.contains("transfer")) {
                transferSlot = itemsCfg.getInt("transfer.slot", -1);
                inv.setItem(transferSlot, GuiItem.fromConfig(getViewer(), itemsCfg.getConfigurationSection("transfer"),
                        "player", targetName, "role", targetRole.name()));
            }
        }

        setInventory(inv);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            new MembersMenuGui(p, team).open();
            return;
        }
        if (slot == kickSlot) {
            tryKick(p);
        } else if (slot == promoteSlot) {
            tryPromote(p);
        } else if (slot == demoteSlot) {
            tryDemote(p);
        } else if (slot == transferSlot) {
            tryTransfer(p);
        }
    }

    // ---- Kick ----

    private void tryKick(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        TeamRole role = team.getRole(p.getUniqueId());

        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-kick", false)) {
            p.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (!team.isMember(targetId)) {
            p.sendMessage(cm.getMessage("player-not-in-team"));
            return;
        }
        if (targetId.equals(p.getUniqueId())) {
            p.sendMessage(cm.getMessage("cant-kick-yourself"));
            return;
        }
        if (targetId.equals(team.getOwner())) {
            p.sendMessage(cm.getMessage("cant-kick-owner"));
            return;
        }
        boolean isOwner = p.getUniqueId().equals(team.getOwner());
        TeamRole targetRole = team.getRole(targetId);
        if (!isOwner && targetRole.getWeight() >= role.getWeight()) {
            p.sendMessage(cm.getMessage("cant-kick-higher-rank"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        Runnable doKick = () -> {
            tm.removeMember(team, targetId);
            tm.saveTeam(team);
            plugin.getVisibilityManager().refreshTeamAndAllies(team);
            Player kickedOnline = target.getPlayer();
            if (kickedOnline != null) {
                plugin.getVisibilityManager().refresh(kickedOnline);
                kickedOnline.sendMessage(cm.getMessage("kicked-from-team", "team", team.getName(), "player", p.getName()));
            }
            p.sendMessage(cm.getMessage("player-kicked", "player", targetName));
            for (UUID memberId : team.getMembers().keySet()) {
                if (memberId.equals(p.getUniqueId())) continue;
                Player online = Bukkit.getPlayer(memberId);
                if (online != null) online.sendMessage(cm.getMessage("player-kicked-broadcast", "player", targetName, "kicker", p.getName()));
            }
            new MembersMenuGui(p, team).open();
        };

        new ConfirmMenuGui(p, doKick, () -> new MemberActionsMenuGui(p, team, targetId).open()).open();
    }

    // ---- Promote ----

    private void tryPromote(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        TeamRole role = team.getRole(p.getUniqueId());

        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-promote", false)) {
            p.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (targetId.equals(p.getUniqueId())) {
            p.sendMessage(cm.getMessage("cant-promote-yourself"));
            return;
        }
        if (!team.isMember(targetId)) {
            p.sendMessage(cm.getMessage("player-not-in-team"));
            return;
        }

        TeamRole current = team.getRole(targetId);
        if (current == TeamRole.LEADER) {
            p.sendMessage(cm.getMessage("already-highest-rank"));
            return;
        }
        TeamRole next = current.next();
        if (next.getWeight() >= role.getWeight()) {
            p.sendMessage(cm.getMessage("cant-promote-own-rank"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        Runnable doPromote = () -> {
            team.setRole(targetId, next);
            tm.saveTeam(team);
            p.sendMessage(cm.getMessage("player-promoted", "player", targetName, "role", next.name()));
            Player targetOnline = target.getPlayer();
            if (targetOnline != null) {
                targetOnline.sendMessage(cm.getMessage("promoted-notify", "role", next.name(), "team", team.getName()));
            }
            for (UUID memberId : team.getMembers().keySet()) {
                if (memberId.equals(p.getUniqueId()) || memberId.equals(targetId)) continue;
                Player online = Bukkit.getPlayer(memberId);
                if (online != null) online.sendMessage(cm.getMessage("promoted-broadcast", "player", targetName, "role", next.name()));
            }
            new MembersMenuGui(p, team).open();
        };

        new ConfirmMenuGui(p, doPromote, () -> new MemberActionsMenuGui(p, team, targetId).open()).open();
    }

    // ---- Demote ----

    private void tryDemote(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        TeamRole role = team.getRole(p.getUniqueId());

        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-promote", false)) {
            p.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (targetId.equals(p.getUniqueId())) {
            p.sendMessage(cm.getMessage("cant-demote-yourself"));
            return;
        }
        if (!team.isMember(targetId)) {
            p.sendMessage(cm.getMessage("player-not-in-team"));
            return;
        }
        if (targetId.equals(team.getOwner())) {
            p.sendMessage(cm.getMessage("cant-demote-owner"));
            return;
        }

        TeamRole current = team.getRole(targetId);
        if (current.getWeight() >= role.getWeight()) {
            p.sendMessage(cm.getMessage("cant-demote-higher-rank"));
            return;
        }

        TeamRole newRole = current.previous();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        Runnable doDemote = () -> {
            team.setRole(targetId, newRole);
            tm.saveTeam(team);
            p.sendMessage(cm.getMessage("player-demoted", "player", targetName, "role", newRole.name()));
            Player targetOnline = target.getPlayer();
            if (targetOnline != null) {
                targetOnline.sendMessage(cm.getMessage("demoted-notify", "role", newRole.name(), "team", team.getName()));
            }
            for (UUID memberId : team.getMembers().keySet()) {
                if (memberId.equals(p.getUniqueId()) || memberId.equals(targetId)) continue;
                Player online = Bukkit.getPlayer(memberId);
                if (online != null) online.sendMessage(cm.getMessage("demoted-broadcast", "player", targetName, "role", newRole.name()));
            }
            new MembersMenuGui(p, team).open();
        };

        new ConfirmMenuGui(p, doDemote, () -> new MemberActionsMenuGui(p, team, targetId).open()).open();
    }

    // ---- Transfer ----

    private void tryTransfer(Player p) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        if (!team.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (targetId.equals(p.getUniqueId())) {
            p.sendMessage(cm.getPrefix() + cm.color("&cYou already own this team."));
            return;
        }
        if (!team.isMember(targetId)) {
            p.sendMessage(cm.getMessage("player-not-in-team"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        Runnable doTransfer = () -> {
            TeamRole targetOldRole = team.getRole(targetId);
            team.setRole(targetId, TeamRole.LEADER);
            team.setRole(p.getUniqueId(), targetOldRole);
            team.setOwner(targetId);
            tm.saveTeam(team);

            p.sendMessage(cm.getMessage("ownership-transferred", "player", targetName, "role", targetOldRole.name()));
            Player targetOnline = target.getPlayer();
            if (targetOnline != null) {
                targetOnline.sendMessage(cm.getMessage("ownership-transferred-notify", "team", team.getName()));
            }
            for (UUID memberId : team.getMembers().keySet()) {
                if (memberId.equals(p.getUniqueId()) || memberId.equals(targetId)) continue;
                Player online = Bukkit.getPlayer(memberId);
                if (online != null) online.sendMessage(cm.getMessage("ownership-transferred-broadcast", "player", targetName));
            }
            new MembersMenuGui(p, team).open();
        };

        new ConfirmMenuGui(p, doTransfer, () -> new MemberActionsMenuGui(p, team, targetId).open()).open();
    }
}