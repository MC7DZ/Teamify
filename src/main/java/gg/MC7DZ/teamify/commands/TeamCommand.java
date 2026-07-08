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

        if (!player.hasPermission("teams.command." + sub)) {
            player.sendMessage(cm.getMessage("no-permission"));
            return true;
        }

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
            case "transfer" -> handleTransfer(player, args);
            case "settings" -> handleSettings(player);
            case "pvp" -> handlePvpToggle(player);
            case "allychat" -> handleAllyChatToggle(player);
            case "allyinvite" -> handleAllyInvite(player, args);
            case "allyleave" -> handleAllyLeave(player, args);
            case "bank" -> handleBank(player, args);
            case "reload" -> handleReload(player);
            default -> player.sendMessage(cm.getPrefix() + cm.color("&cUnknown subcommand."));
        }
        return true;
    }

    private void handleSettings(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        if (!cm.isGuiEnabled()) {
            player.sendMessage(cm.getPrefix() + cm.color("&7Use /team pvp to toggle team PVP."));
            return;
        }
        new SettingsMenuGui(plugin, player, team).open();
    }

    private void handlePvpToggle(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        if (cm.isFriendlyFireWithinTeam()) {
            player.sendMessage(cm.getMessage("pvp-locked"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-toggle-pvp", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        team.setPvpEnabled(!team.isPvpEnabled());
        tm.saveTeam(team);
        player.sendMessage(cm.getMessage(team.isPvpEnabled() ? "pvp-enabled" : "pvp-disabled"));
    }

    private void handleReload(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        if (!player.hasPermission("teams.admin")) {
            player.sendMessage(cm.getMessage("no-permission"));
            return;
        }
        cm.reload();
        plugin.getEconomyManager().setup();
        player.sendMessage(cm.getMessage("config-reloaded"));
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
        try {
            team.setColor(org.bukkit.ChatColor.valueOf(cm.getDefaultTeamColorName().toUpperCase()));
        } catch (IllegalArgumentException ignored) {
        }
        tm.markCreationCooldown(player.getUniqueId());
        tm.saveTeam(team);
        plugin.getVisibilityManager().refresh(player);

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
            gg.MC7DZ.teamify.util.MessageUtil.sendClickableInvite(
                    onlineTarget,
                    cm.getMessage("invite-received", "team", team.getName()),
                    "&a&l[Click to Accept]",
                    "/team join " + team.getName(),
                    "&7Click to join &b" + team.getName());
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
            plugin.getVisibilityManager().refreshTeamAndAllies(team);
            player.sendMessage(cm.getPrefix() + cm.color("&aYou joined &b" + team.getName() + "&a!"));
            for (UUID memberId : team.getMembers().keySet()) {
                if (memberId.equals(player.getUniqueId())) continue;
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) member.sendMessage(cm.getMessage("player-joined-broadcast", "player", player.getName()));
            }
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
            player.sendMessage(cm.getMessage("player-not-in-team"));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(cm.getMessage("cant-kick-yourself"));
            return;
        }
        if (target.getUniqueId().equals(team.getOwner())) {
            player.sendMessage(cm.getMessage("cant-kick-owner"));
            return;
        }

        boolean isOwner = player.getUniqueId().equals(team.getOwner());
        TeamRole targetRole = team.getRole(target.getUniqueId());
        if (!isOwner && targetRole.getWeight() >= role.getWeight()) {
            player.sendMessage(cm.getMessage("cant-kick-higher-rank"));
            return;
        }

        tm.removeMember(team, target.getUniqueId());
        tm.saveTeam(team);
        plugin.getVisibilityManager().refreshTeamAndAllies(team);
        Player kickedOnline = target.getPlayer();
        if (kickedOnline != null) {
            plugin.getVisibilityManager().refresh(kickedOnline);
            kickedOnline.sendMessage(cm.getMessage("kicked-from-team", "team", team.getName(), "player", player.getName()));
        }
        player.sendMessage(cm.getMessage("player-kicked", "player", args[1]));
        for (UUID memberId : team.getMembers().keySet()) {
            if (memberId.equals(player.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(cm.getMessage("player-kicked-broadcast", "player", args[1], "kicker", player.getName()));
        }
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
        plugin.getVisibilityManager().refreshTeamAndAllies(team);
        plugin.getVisibilityManager().refresh(player);
        player.sendMessage(cm.getMessage("left-team", "team", team.getName()));
        for (UUID memberId : team.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(cm.getMessage("player-left-broadcast", "player", player.getName()));
        }
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
            var memberIds = new java.util.ArrayList<>(team.getMembers().keySet());
            String teamName = team.getName();
            tm.disbandTeam(team);
            for (UUID memberId : memberIds) {
                Player p = Bukkit.getPlayer(memberId);
                if (p == null) continue;
                plugin.getVisibilityManager().refresh(p);
                if (!memberId.equals(player.getUniqueId())) {
                    p.sendMessage(cm.getMessage("team-disbanded-member", "team", teamName));
                }
            }
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
        if (!cm.isHomeCommandEnabled()) {
            player.sendMessage(cm.getMessage("command-disabled"));
            return;
        }
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        Location home = team.getHome(0);
        if (home == null) {
            player.sendMessage(cm.getMessage("no-home-set"));
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
        if (!cm.isSetHomeCommandEnabled()) {
            player.sendMessage(cm.getMessage("command-disabled"));
            return;
        }
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

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(cm.getMessage("cant-promote-yourself"));
            return;
        }
        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(cm.getMessage("player-not-in-team"));
            return;
        }

        TeamRole current = team.getRole(target.getUniqueId());
        if (current == TeamRole.LEADER) {
            player.sendMessage(cm.getMessage("already-highest-rank"));
            return;
        }
        TeamRole next = current.next();

        // Nobody, including the owner, can promote a player to a rank that is
        // equal to or higher than their own rank.
        if (next.getWeight() >= role.getWeight()) {
            player.sendMessage(cm.getMessage("cant-promote-own-rank"));
            return;
        }

        team.setRole(target.getUniqueId(), next);
        tm.saveTeam(team);
        player.sendMessage(cm.getMessage("player-promoted", "player", args[1], "role", next.name()));

        Player targetOnline = target.getPlayer();
        if (targetOnline != null) {
            targetOnline.sendMessage(cm.getMessage("promoted-notify", "role", next.name(), "team", team.getName()));
        }
        for (UUID memberId : team.getMembers().keySet()) {
            if (memberId.equals(player.getUniqueId()) || memberId.equals(target.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(cm.getMessage("promoted-broadcast", "player", args[1], "role", next.name()));
        }
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

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(cm.getMessage("cant-demote-yourself"));
            return;
        }
        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(cm.getMessage("player-not-in-team"));
            return;
        }
        if (target.getUniqueId().equals(team.getOwner())) {
            player.sendMessage(cm.getMessage("cant-demote-owner"));
            return;
        }

        TeamRole current = team.getRole(target.getUniqueId());
        if (current.getWeight() >= role.getWeight()) {
            player.sendMessage(cm.getMessage("cant-demote-higher-rank"));
            return;
        }

        TeamRole newRole = current.previous();
        team.setRole(target.getUniqueId(), newRole);
        tm.saveTeam(team);
        player.sendMessage(cm.getMessage("player-demoted", "player", args[1], "role", newRole.name()));

        Player targetOnline = target.getPlayer();
        if (targetOnline != null) {
            targetOnline.sendMessage(cm.getMessage("demoted-notify", "role", newRole.name(), "team", team.getName()));
        }
        for (UUID memberId : team.getMembers().keySet()) {
            if (memberId.equals(player.getUniqueId()) || memberId.equals(target.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(cm.getMessage("demoted-broadcast", "player", args[1], "role", newRole.name()));
        }
    }

    private void handleTransfer(Player player, String[] args) {
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
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team transfer <player>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(cm.getPrefix() + cm.color("&cYou already own this team."));
            return;
        }
        if (!team.isMember(target.getUniqueId())) {
            player.sendMessage(cm.getMessage("player-not-in-team"));
            return;
        }

        Runnable doTransfer = () -> {
            // Swap places: target becomes LEADER and the new owner, the old
            // owner takes on whatever rank the target used to hold.
            TeamRole targetOldRole = team.getRole(target.getUniqueId());
            team.setRole(target.getUniqueId(), TeamRole.LEADER);
            team.setRole(player.getUniqueId(), targetOldRole);
            team.setOwner(target.getUniqueId());
            tm.saveTeam(team);

            player.sendMessage(cm.getMessage("ownership-transferred", "player", args[1], "role", targetOldRole.name()));

            Player targetOnline = target.getPlayer();
            if (targetOnline != null) {
                targetOnline.sendMessage(cm.getMessage("ownership-transferred-notify", "team", team.getName()));
            }
            for (UUID memberId : team.getMembers().keySet()) {
                if (memberId.equals(player.getUniqueId()) || memberId.equals(target.getUniqueId())) continue;
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) p.sendMessage(cm.getMessage("ownership-transferred-broadcast", "player", args[1]));
            }
        };

        if (cm.isTransferConfirmationRequired() && cm.isGuiEnabled()) {
            new ConfirmMenuGui(plugin, player, doTransfer, () ->
                    player.sendMessage(cm.getPrefix() + cm.color("&7Ownership transfer cancelled."))).open();
        } else {
            doTransfer.run();
        }
    }

    private void handleBank(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        if (!cm.isBankEnabled()) {
            player.sendMessage(cm.getMessage("bank-disabled"));
            return;
        }
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-access-bank", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }

        if (args.length < 2) {
            if (cm.isGuiEnabled()) {
                new BankMenuGui(plugin, player, team).open();
            } else {
                player.sendMessage(cm.getMessage("bank-balance", "amount", plugin.getEconomyManager().format(team.getBankBalance())));
            }
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "balance" -> player.sendMessage(cm.getMessage("bank-balance", "amount", plugin.getEconomyManager().format(team.getBankBalance())));
            case "deposit" -> {
                if (args.length < 3) {
                    player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team bank deposit <amount>"));
                    return;
                }
                bankDeposit(player, team, args[2]);
            }
            case "withdraw" -> {
                if (args.length < 3) {
                    player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team bank withdraw <amount>"));
                    return;
                }
                bankWithdraw(player, team, args[2]);
            }
            default -> player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team bank <deposit|withdraw|balance> [amount]"));
        }
    }

    /**
     * Deposits money from the player's personal Vault balance into their
     * team's bank. Any team member with can-access-bank may deposit.
     */
    public void bankDeposit(Player player, Team team, String amountStr) {
        ConfigManager cm = plugin.getConfigManager();
        double amount = parsePositiveAmount(player, amountStr);
        if (Double.isNaN(amount)) return;

        if (!plugin.getEconomyManager().isEnabled()) {
            player.sendMessage(cm.getMessage("bank-no-economy"));
            return;
        }
        if (!plugin.getEconomyManager().has(player, amount)) {
            player.sendMessage(cm.getMessage("bank-insufficient-player-funds"));
            return;
        }

        double maxBalance = cm.getMaxBalance();
        if (maxBalance > 0 && team.getBankBalance() >= maxBalance) {
            player.sendMessage(cm.getMessage("bank-max-balance"));
            return;
        }

        if (!plugin.getEconomyManager().withdrawPlayer(player, amount)) {
            player.sendMessage(cm.getMessage("bank-transaction-failed"));
            return;
        }

        double deposited = team.deposit(amount, maxBalance);
        double leftover = amount - deposited;
        if (leftover > 0) {
            // Bank hit its cap mid-deposit - refund whatever didn't fit.
            plugin.getEconomyManager().depositPlayer(player, leftover);
        }
        plugin.getTeamManager().saveTeam(team);

        player.sendMessage(cm.getMessage("bank-deposit-success",
                "amount", plugin.getEconomyManager().format(deposited),
                "balance", plugin.getEconomyManager().format(team.getBankBalance())));

        for (UUID memberId : team.getMembers().keySet()) {
            if (memberId.equals(player.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                p.sendMessage(cm.getMessage("bank-deposit-broadcast",
                        "player", player.getName(),
                        "amount", plugin.getEconomyManager().format(deposited)));
            }
        }
    }

    /**
     * Withdraws money from the team bank into the player's personal Vault
     * balance. Requires both the Bukkit permission "teams.bank.withdraw" and
     * the role permission can-withdraw-bank.
     */
    public void bankWithdraw(Player player, Team team, String amountStr) {
        ConfigManager cm = plugin.getConfigManager();

        if (!player.hasPermission("teams.bank.withdraw")) {
            player.sendMessage(cm.getMessage("no-permission"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-withdraw-bank", false)) {
            player.sendMessage(cm.getMessage("bank-cant-withdraw-role"));
            return;
        }

        double amount = parsePositiveAmount(player, amountStr);
        if (Double.isNaN(amount)) return;

        if (!plugin.getEconomyManager().isEnabled()) {
            player.sendMessage(cm.getMessage("bank-no-economy"));
            return;
        }
        if (team.getBankBalance() < amount) {
            player.sendMessage(cm.getMessage("bank-insufficient-team-funds"));
            return;
        }

        if (!team.withdraw(amount)) {
            player.sendMessage(cm.getMessage("bank-insufficient-team-funds"));
            return;
        }
        if (!plugin.getEconomyManager().depositPlayer(player, amount)) {
            // Roll back the team bank debit if paying the player somehow failed.
            team.deposit(amount);
            player.sendMessage(cm.getMessage("bank-transaction-failed"));
            return;
        }
        plugin.getTeamManager().saveTeam(team);

        player.sendMessage(cm.getMessage("bank-withdraw-success",
                "amount", plugin.getEconomyManager().format(amount),
                "balance", plugin.getEconomyManager().format(team.getBankBalance())));

        for (UUID memberId : team.getMembers().keySet()) {
            if (memberId.equals(player.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                p.sendMessage(cm.getMessage("bank-withdraw-broadcast",
                        "player", player.getName(),
                        "amount", plugin.getEconomyManager().format(amount)));
            }
        }
    }

    /** Parses a positive numeric amount, messaging the player and returning NaN on any error. */
    private double parsePositiveAmount(Player player, String raw) {
        ConfigManager cm = plugin.getConfigManager();
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            player.sendMessage(cm.getMessage("bank-invalid-amount"));
            return Double.NaN;
        }
        if (amount <= 0 || !Double.isFinite(amount)) {
            player.sendMessage(cm.getMessage("bank-invalid-amount"));
            return Double.NaN;
        }
        return amount;
    }

    private void handleAllyChatToggle(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();
        if (!cm.isAlliesEnabled()) {
            player.sendMessage(cm.getMessage("allies-disabled"));
            return;
        }
        if (!cm.isAllyChatEnabled()) {
            player.sendMessage(cm.getMessage("ally-chat-disabled"));
            return;
        }
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        plugin.getPlayerListener().toggleAllyChat(player.getUniqueId());
        boolean nowOn = plugin.getPlayerListener().isAllyChatToggled(player.getUniqueId());
        player.sendMessage(cm.getMessage(nowOn ? "ally-chat-enabled" : "ally-chat-disabled-toggle"));
    }

    private void handleAllyInvite(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        if (!cm.isAlliesEnabled()) {
            player.sendMessage(cm.getMessage("allies-disabled"));
            return;
        }
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-manage-relations", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team allyinvite <team>"));
            return;
        }

        Team target = tm.getTeamByName(args[1]);
        if (target == null) {
            player.sendMessage(cm.getMessage("ally-invalid-team"));
            return;
        }
        if (target.getId().equals(team.getId())) {
            player.sendMessage(cm.getMessage("ally-cant-self"));
            return;
        }
        if (tm.areAllied(team, target)) {
            player.sendMessage(cm.getMessage("already-allied", "team", target.getName()));
            return;
        }
        if (team.getRelation(target.getId()) == gg.MC7DZ.teamify.team.RelationType.ENEMY) {
            player.sendMessage(cm.getMessage("already-enemies"));
            return;
        }
        int maxAllies = cm.getMaxAllies();
        if (maxAllies > 0 && team.getAllyCount() >= maxAllies) {
            player.sendMessage(cm.getMessage("max-allies-reached"));
            return;
        }

        TeamManager.AllyInviteResult result = tm.requestAlly(team, target, cm.isMutualAllianceRequired());
        if (result == TeamManager.AllyInviteResult.ALLY_ADDED) {
            plugin.getVisibilityManager().refreshTeamAndAllies(team);
            plugin.getVisibilityManager().refreshTeamAndAllies(target);
            for (UUID memberId : team.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) p.sendMessage(cm.getMessage("ally-added", "team", target.getName()));
            }
            for (UUID memberId : target.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) p.sendMessage(cm.getMessage("ally-added", "team", team.getName()));
            }
        } else {
            player.sendMessage(cm.getMessage("ally-invite-sent", "team", target.getName()));
            for (UUID memberId : target.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) {
                    gg.MC7DZ.teamify.util.MessageUtil.sendClickableInvite(
                            p,
                            cm.getMessage("ally-invite-received", "team", team.getName()),
                            "&a&l[Click to Accept]",
                            "/team allyinvite " + team.getName(),
                            "&7Click to accept the alliance with &b" + team.getName());
                }
            }
        }
    }

    private void handleAllyLeave(Player player, String[] args) {
        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        if (!cm.isAlliesEnabled()) {
            player.sendMessage(cm.getMessage("allies-disabled"));
            return;
        }
        Team team = tm.getTeamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage(cm.getMessage("no-team"));
            return;
        }
        TeamRole role = team.getRole(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-manage-relations", false)) {
            player.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + cm.color("&cUsage: /team allyleave <team>"));
            return;
        }
        Team target = tm.getTeamByName(args[1]);
        if (target == null) {
            player.sendMessage(cm.getMessage("ally-invalid-team"));
            return;
        }
        if (!tm.areAllied(team, target)) {
            player.sendMessage(cm.getMessage("not-allied", "team", target.getName()));
            return;
        }

        tm.removeAlly(team, target);
        plugin.getVisibilityManager().refreshTeamAndAllies(team);
        plugin.getVisibilityManager().refreshTeamAndAllies(target);
        for (UUID memberId : team.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(cm.getMessage("ally-removed", "team", target.getName()));
        }
        for (UUID memberId : target.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) p.sendMessage(cm.getMessage("ally-removed", "team", team.getName()));
        }
    }

}
