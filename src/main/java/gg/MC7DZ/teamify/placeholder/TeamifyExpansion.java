package gg.MC7DZ.teamify.placeholder;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Registers PlaceholderAPI placeholders under the "teamify" identifier, e.g.
 * %teamify_bank%, %teamify_bank_balance%, %teamify_team%, %teamify_kills%.
 * <p>
 * This is what was previously missing: {@code integrations.placeholderapi.enabled}
 * existed in config.yml, but nothing ever created or registered a
 * PlaceholderExpansion, so any %teamify_...% placeholder (used in scoreboards,
 * tab lists, holograms, etc.) was left completely unparsed by PlaceholderAPI
 * and printed back out literally instead of showing the team's money.
 */
public class TeamifyExpansion extends PlaceholderExpansion {

    private final Teamify plugin;

    public TeamifyExpansion(Teamify plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "teamify";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MC7DZ";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Keeps the expansion registered across /papi reload. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        UUID uuid = player.getUniqueId();
        Team team = plugin.getTeamManager().getTeamOf(uuid);

        switch (params.toLowerCase()) {
            case "bank", "bank_balance" -> {
                if (team == null) return plugin.getEconomyManager().format(0);
                return plugin.getEconomyManager().format(team.getBankBalance());
            }
            case "bank_raw", "bank_balance_raw" -> {
                return team == null ? "0" : String.valueOf(team.getBankBalance());
            }
            case "team", "team_name" -> {
                return team == null ? "" : team.getName();
            }
            case "tag" -> {
                return team == null ? "" : team.getTag();
            }
            case "level" -> {
                return team == null ? "0" : String.valueOf(team.getLevel());
            }
            case "xp" -> {
                return team == null ? "0" : String.valueOf(team.getXp());
            }
            case "kills" -> {
                return team == null ? "0" : String.valueOf(team.getTotalKills());
            }
            case "kills_player" -> {
                return team == null ? "0" : String.valueOf(team.getKills(uuid));
            }
            case "members", "member_count" -> {
                return team == null ? "0" : String.valueOf(team.getSize());
            }
            case "owner" -> {
                if (team == null) return "";
                OfflinePlayer owner = plugin.getServer().getOfflinePlayer(team.getOwner());
                return owner.getName() == null ? "Unknown" : owner.getName();
            }
            case "role" -> {
                if (team == null) return "";
                TeamRole role = team.getRole(uuid);
                return role == null ? "" : role.name();
            }
            case "pvp", "pvp_status" -> {
                if (team == null) return "";
                return team.isPvpEnabled() ? "Enabled" : "Disabled";
            }
            case "has_team" -> {
                return team != null ? "true" : "false";
            }
            default -> {
                return null; // Unknown placeholder - let PlaceholderAPI show it's invalid.
            }
        }
    }
}
