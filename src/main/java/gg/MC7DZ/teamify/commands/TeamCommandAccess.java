package gg.MC7DZ.teamify.commands;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Central place that decides whether a player is currently allowed to use a
 * given /team subcommand. Kept separate from TeamCommand so the tab
 * completer can reuse the exact same logic to hide subcommands the player
 * has no access to.
 */
public final class TeamCommandAccess {

    /** Every subcommand the plugin exposes, in suggestion order. */
    public static final List<String> ALL_SUBCOMMANDS = List.of(
            "create", "invite", "kick", "leave", "disband", "home", "sethome",
            "chat", "info", "list", "gui", "join", "promote", "demote", "transfer",
            "settings", "pvp", "allychat", "allyinvite", "allyleave",
            "bank", "reload"
    );

    /** Subcommands usable by a player who is not currently in a team. */
    public static final List<String> NO_TEAM_SUBCOMMANDS = List.of("create", "join", "list");

    private TeamCommandAccess() {
    }

    /**
     * Returns the list of subcommands the player is allowed to see/use right now.
     */
    public static List<String> getAvailable(Teamify plugin, Player player) {
        Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        List<String> result = new ArrayList<>();

        if (team == null) {
            for (String sub : NO_TEAM_SUBCOMMANDS) {
                if (hasAccess(plugin, player, sub, null)) result.add(sub);
            }
            return result;
        }

        for (String sub : ALL_SUBCOMMANDS) {
            if (hasAccess(plugin, player, sub, team)) result.add(sub);
        }
        return result;
    }

    /**
     * Checks whether the player currently has access to a specific subcommand.
     * `team` may be null (resolved internally if omitted) for convenience.
     */
    public static boolean hasAccess(Teamify plugin, Player player, String sub, Team team) {
        ConfigManager cm = plugin.getConfigManager();
        if (team == null) {
            team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        }

        // Bukkit permission node lets admins outright deny a subcommand
        // regardless of everything else below.
        if (!player.hasPermission("teams.command." + sub)) {
            return false;
        }

        if (team == null) {
            return NO_TEAM_SUBCOMMANDS.contains(sub);
        }

        TeamRole role = team.getRole(player.getUniqueId());
        boolean isOwner = player.getUniqueId().equals(team.getOwner());

        return switch (sub) {
            case "create" -> false; // already in a team
            case "join" -> true; // harmless to always show; handler validates invites
            case "list", "info", "gui", "settings", "pvp", "leave", "disband" -> true;
            case "home" -> cm.isHomeCommandEnabled();
            case "sethome" -> cm.isSetHomeCommandEnabled() && rolePerm(plugin, role, "can-set-home");
            case "invite" -> rolePerm(plugin, role, "can-invite");
            case "kick" -> rolePerm(plugin, role, "can-kick");
            case "promote", "demote" -> rolePerm(plugin, role, "can-promote");
            case "transfer" -> isOwner;
            case "chat" -> cm.isTeamChatEnabled();
            case "allyinvite", "allyleave" -> cm.isAlliesEnabled() && rolePerm(plugin, role, "can-manage-relations");
            case "bank" -> cm.isBankEnabled() && rolePerm(plugin, role, "can-access-bank");
            case "allychat" -> cm.isAlliesEnabled() && cm.isAllyChatEnabled();
            case "reload" -> player.hasPermission("teams.admin");
            default -> true;
        };
    }

    private static boolean rolePerm(Teamify plugin, TeamRole role, String permKey) {
        if (role == null) return false;
        return plugin.getConfig().getBoolean("roles.permissions." + role.name() + "." + permKey, false);
    }
}
