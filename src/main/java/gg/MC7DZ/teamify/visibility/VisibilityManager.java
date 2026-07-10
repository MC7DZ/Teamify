package gg.MC7DZ.teamify.visibility;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.UUID;

/**
 * Reproduces vanilla's per-scoreboard-team "seeFriendlyInvisibles" option
 * (the same thing /minecraft:team modify &lt;team&gt; seeFriendlyInvisibles
 * true does) but scoped to Teamify's own teams, using a private
 * scoreboard for each player. This same private scoreboard is also used to
 * color teammates' and allies' names (general.colored-names in config.yml).
 *
 * Whether members get the "see through invisibility" effect is a global,
 * server-enforced setting (visibility.see-members-when-invis in config.yml).
 * Allies are NOT granted invisibility sight regardless of config.
 *
 * How it works: every online player gets their own personal Scoreboard
 * (separate from the shared main scoreboard) containing up to two teams:
 *  - "teamify_mates", holding the viewer and their online teammates
 *  - "teamify_allies", holding the online members of the viewer's allies
 * Each team independently gets "can see friendly invisibles" turned on when
 * the matching visibility.* setting is enabled (only for mates now), and a
 * name color (from general.teammate-color / allies-color / enemys-color)
 * when general.colored-names is enabled AND general.color-shows contains
 * TAB and/or NAMETAG. A third "teamify_enemies" team is added the same way
 * for enemy teams (relations.enable-enemies must also be on). Note TAB and
 * NAMETAG are both driven by this same scoreboard team color client-side,
 * so enabling either currently colors both; they can't be shown
 * independently without packet-level tab list edits. Coloring the CHAT
 * channel (team/ally chat) is handled separately in PlayerListener.
 */
public class VisibilityManager {

    private static final String MATES_TEAM_NAME = "teamify_mates";
    private static final String ALLIES_TEAM_NAME = "teamify_allies";
    private static final String ENEMIES_TEAM_NAME = "teamify_enemies";

    private final Teamify plugin;

    public VisibilityManager(Teamify plugin) {
        this.plugin = plugin;
    }

    /** Rebuilds the private visibility/name-color scoreboard for a single player. */
    public void refresh(Player player) {
        Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());

        if (team == null) {
            // Not in a team - nothing special to show, use the shared board.
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }

        boolean seeMembers = plugin.getConfigManager().isSeeMembersWhenInvis();
        boolean coloredNames = plugin.getConfigManager().isColoredNamesEnabled();
        // TAB and NAMETAG are both rendered off the same scoreboard team color
        // client-side, so either one being enabled applies the color here.
        var colorShows = plugin.getConfigManager().getColorShows();
        boolean applyColor = coloredNames && (
                colorShows.contains(gg.MC7DZ.teamify.config.ConfigManager.ColorShow.NAMETAG)
                        || colorShows.contains(gg.MC7DZ.teamify.config.ConfigManager.ColorShow.TAB));
        boolean enemiesEnabled = plugin.getConfigManager().isEnemiesEnabled();

        if (!seeMembers && !applyColor) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard board = manager.getNewScoreboard();

        // Teammates (including the viewer themself).
        org.bukkit.scoreboard.Team matesTeam = board.registerNewTeam(MATES_TEAM_NAME);
        matesTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        matesTeam.setCanSeeFriendlyInvisibles(seeMembers);
        if (applyColor) matesTeam.setColor(plugin.getConfigManager().getTeammateColor());

        addEntrySafe(matesTeam, player.getName());
        for (UUID memberId : team.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) addEntrySafe(matesTeam, member.getName());
        }

        // Allies' members – invisibility sight is NOT granted.
        org.bukkit.scoreboard.Team alliesTeam = board.registerNewTeam(ALLIES_TEAM_NAME);
        alliesTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        // Friendly invisibles are disabled for allies (default is false).
        // alliesTeam.setCanSeeFriendlyInvisibles(false); // uncomment if you want to be explicit
        if (applyColor) alliesTeam.setColor(plugin.getConfigManager().getAlliesColor());

        for (var entry : team.getRelations().entrySet()) {
            if (entry.getValue() != RelationType.ALLY) continue;
            Team allyTeam = plugin.getTeamManager().getTeam(entry.getKey());
            if (allyTeam == null) continue;
            for (UUID memberId : allyTeam.getMembers().keySet()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) addEntrySafe(alliesTeam, member.getName());
            }
        }

        // Enemies' members (color only - there's no "see through invisibility" concept for enemies).
        if (applyColor && enemiesEnabled) {
            org.bukkit.scoreboard.Team enemiesTeam = board.registerNewTeam(ENEMIES_TEAM_NAME);
            enemiesTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                    org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
            enemiesTeam.setColor(plugin.getConfigManager().getEnemysColor());

            for (var entry : team.getRelations().entrySet()) {
                if (entry.getValue() != RelationType.ENEMY) continue;
                Team enemyTeam = plugin.getTeamManager().getTeam(entry.getKey());
                if (enemyTeam == null) continue;
                for (UUID memberId : enemyTeam.getMembers().keySet()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null) addEntrySafe(enemiesTeam, member.getName());
                }
            }
        }

        player.setScoreboard(board);
    }

    private void addEntrySafe(org.bukkit.scoreboard.Team sbTeam, String entry) {
        if (!sbTeam.hasEntry(entry)) {
            sbTeam.addEntry(entry);
        }
    }

    /** Rebuilds the private visibility scoreboard for every online player. */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /**
     * Refreshes a whole team's online members plus every online member of
     * their allies (used after anything that changes who's on a team, or
     * who's allied with whom, so the change is reflected immediately rather
     * than waiting for the periodic refresh task).
     */
    public void refreshTeamAndAllies(Team team) {
        if (team == null) return;
        for (UUID memberId : team.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) refresh(p);
        }
        for (var entry : team.getRelations().entrySet()) {
            if (entry.getValue() == RelationType.ALLY || entry.getValue() == RelationType.ENEMY) {
                Team relatedTeam = plugin.getTeamManager().getTeam(entry.getKey());
                if (relatedTeam == null) continue;
                for (UUID memberId : relatedTeam.getMembers().keySet()) {
                    Player p = Bukkit.getPlayer(memberId);
                    if (p != null) refresh(p);
                }
            }
        }
    }

    /** Resets a player back to the shared main scoreboard (e.g. on quit/disable). */
    public void reset(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }
}