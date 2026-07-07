package gg.MC7DZ.teamify.commands;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.gui.*;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.team.TeamRole;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Pattern;

public class TeamCommand implements CommandExecutor {

    private final Teamify plugin;

    public TeamCommand(Teamify plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        if (args.length == 0) {
            Team team = tm.getTeamOf(player.getUniqueId());
            if (team == null) {
                player.sendMessage(cm.getMessage("no-team"));
                return true;
            }
            if (!cm.isGuiEnabled()) {
                player.sendMessage(cm.getPrefix() + cm.color("&7Use /team info, /team members, etc."));
                return true;
            }
            new MainMenuGui(plugin, player, team).open();
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "kick" -> handleKick(player, args);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "home" -> handleHome(player);
            case "sethome" -> handleSetHome(player);
            case "chat" -> handleChatToggle(player);
            case "info" -> handleInfo(player);
            case "list" -> new TeamListMenuGui(plugin, player).open();
            case "gui" -> {
                Team team = tm.getTeamOf(player.getUniqueId());
                if (team == null) {
                    player.sendMessage(cm.getMessage("no-team"));
                } else {
                    new MainMenuGui(plugin, player, team).open();
                }
            }
            case "join" -> handleJoin(player, args);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            default -> player.sendMessage(cm.getPrefix() + cm.color("&cUnknown subcommand."));
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        if (tm.isInTeam(player.getUniqueId())) {
            player.sendMessage(cm.getMessage("already-in-team"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team create <name> [tag]"));
            return;
        }

        long remaining = tm.getCreationCooldownRemaining(player.getUniqueId(), cm.getCreationCooldownSeconds());
        if (remaining > 0) {
            player.sendMessage(cm.getMessage("cooldown-active", "time", remaining + "s"));
            return;
        }

        String name = args[1];
        if (name.length() < cm.getMinNameLength() || name.length() > cm.getMaxNameLength()
                || !Pattern.matches(cm.getNameRegex(), name)) {
            player.sendMessage(cm.getMessage("invalid-name"));
            return;
        }
        if (cm.isBlockDuplicateNames() && tm.getTeamByName(name) != null) {
            player.sendMessage(cm.getMessage("invalid-name"));
            return;
        }

        String tag = args.length >= 3 ? args[2] : name.substring(0, Math.min(4, name.length())).toUpperCase();

        Team team = tm.createTeam(name, tag, player.getUniqueId());
        team.setBankBalance(cm.getStartingBalance());
        tm.markCreationCooldown(player.getUniqueId());
        tm.saveTeam(team);

        player.sendMessage(cm.getMessage("team-created", "team", name));
    }

    private void handleInvite(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());

        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-invite", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team invite <player>"));
            return;
        }

        int maxMembers = cm.getMaxMembers();
        if (maxMembers > 0 && team.getSize() >= maxMembers) {
            player.sendMessage(cm.getMessage("team-full"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (tm.isInTeam(target.getUniqueId())) {
            player.sendMessage(cm.getPrefix() + cm.color("&cThat player is already in a team."));
            return;
        }

        team.addInvite(target.getUniqueId());
        player.sendMessage(cm.getMessage("invite-sent", "player", args[1]));

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(cm.getMessage("invite-received", "team", team.getName()));
        }
    }

    private void handleJoin(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        if (tm.isInTeam(player.getUniqueId())) {
            player.sendMessage(cm.getMessage("already-in-team"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team join <name>"));
            return;
        }
        Team team = tm.getTeamByName(args[1]);
        if (team == null || !team.hasInvite(player.getUniqueId())) {
            player.sendMessage(cm.getPrefix() + cm.color("&cNo pending invite from that team."));
            return;
        }
        if (cm.isGuiEnabled()) {
            new InviteMenuGui(plugin, player, team).open();
        } else {
            team.removeInvite(player.getUniqueId());
            tm.addMember(team, player.getUniqueId(), TeamRole.MEMBER);
            tm.saveTeam(team);
            player.sendMessage(cm.getPrefix() + cm.color("&aYou joined &b" + team.getName() + "&a!"));
        }
    }

    private void handleKick(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-kick", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team kick <player>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(cm.getPrefix() + cm.color("&cThat player is not in your team."));
            return;
        }
        tm.removeMember(team, target.getUniqueId());
        tm.saveTeam(team);
        player.sendMessage(cm.getMessage("player-kicked", "player", args[1]));
    }

    private void handleLeave(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        if (team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(cm.getPrefix() + cm.color("&cTransfer ownership or disband instead of leaving."));
            return;
        }
        tm.removeMember(team, player.getUniqueId());
        tm.saveTeam(team);
        player.sendMessage(cm.getPrefix() + cm.color("&7You left &f" + team.getName() + "&7."));
    }

    private void handleDisband(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        if (!team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }

        Runnable doDisband = () -> {
            tm.disbandTeam(team);
            player.sendMessage(cm.getMessage("team-disbanded"));
        };

        if (cm.isDisbandConfirmationRequired() && cm.isGuiEnabled()) {
            new ConfirmMenuGui(plugin, player, doDisband, () ->
                    player.sendMessage(cm.getPrefix() + cm.color("&7Disband cancelled."))).open();
        } else {
            doDisband.run();
        }
    }

    private void handleHome(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        Location home = team.getHome(0);
        if (home == null) {
            player.sendMessage(cm.getPrefix() + cm.color("&cYour team has no home set."));
            return;
        }
        int delay = cm.getHomeTeleportDelay();
        if (delay <= 0) {
            player.teleport(home);
            return;
        }
        player.sendMessage(cm.getPrefix() + cm.color("&7Teleporting in " + delay + " seconds..."));
        Location startLoc = player.getLocation();
        boolean cancelOnMove = cm.isCancelOnMove();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelOnMove && player.getLocation().distanceSquared(startLoc) > 1.0) {
                player.sendMessage(cm.getPrefix() + cm.color("&cTeleport cancelled - you moved."));
                return;
            }
            player.teleport(home);
        }, delay * 20L);
    }

    private void handleSetHome(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-set-home", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        team.setHome(0, player.getLocation());
        tm.saveTeam(team);
        player.sendMessage(cm.getPrefix() + cm.color("&aTeam home set!"));
    }

    private void handleChatToggle(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        if (!cm.isTeamChatEnabled()) {
            player.sendMessage(cm.getPrefix() + cm.color("&cTeam chat is disabled."));
            return;
        }
        plugin.getPlayerListener().toggleTeamChat(player.getUniqueId());
        boolean nowOn = plugin.getPlayerListener().isTeamChatToggled(player.getUniqueId());
        player.sendMessage(cm.getPrefix() + cm.color(nowOn ? "&aTeam chat enabled." : "&7Team chat disabled."));
    }

    private void handleInfo(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        player.sendMessage(cm.getPrefix() + cm.color("&bTeam: &f" + team.getName() +
                " &7| &bTag: &f" + team.getTag() +
                " &7| &bLevel: &f" + team.getLevel() +
                " &7| &bMembers: &f" + team.getSize()));
    }

    private void handlePromote(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-promote", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team promote <player>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(cm.getPrefix() + cm.color("&cThat player is not in your team."));
            return;
        }
        TeamRole current = team.getRole(target.getUniqueId());
        team.setRole(target.getUniqueId(), current.next());
        tm.saveTeam(team);
        player.sendMessage(cm.getPrefix() + cm.color("&a" + args[1] + " promoted to " + current.next().name() + "."));
    }

    private void handleDemote(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-promote", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team demote <player>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(cm.getPrefix() + cm.color("&cThat player is not in your team."));
            return;
        }
        TeamRole current = team.getRole(target.getUniqueId());
        team.setRole(target.getUniqueId(), current.previous());
        tm.saveTeam(team);
        player.sendMessage(cm.getPrefix() + cm.color("&a" + args[1] + " demoted to " + current.previous().name() + "."));
    }
}
