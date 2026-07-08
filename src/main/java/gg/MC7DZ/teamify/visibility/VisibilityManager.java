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
 * true does) but scoped to Teamify's own teams/allies, using a private
 * scoreboard for each player.
 *
 * Whether members and/or allies are included is a global, server-enforced
 * setting (visibility.see-members-when-invis / see-allies-when-invis in
 * config.yml) - there is no per-team toggle.
 *
 * How it works: every online player gets their own personal Scoreboard
 * (separate from the shared main scoreboard) containing a single team
 * "teamify_vis" whose entries are:
 *  - the viewer themself,
 *  - their online teammates (if visibility.see-members-when-invis is on),
 *  - their online allies' members (if visibility.see-allies-when-invis is on).
 * That team has "can see friendly invisibles" turned on, so the viewer's
 * client renders any invisible entry in that list as visible to them
 * specifically - exactly like vanilla team visibility.
 */
public class VisibilityManager {

    private static final String TEAM_NAME = "teamify_vis";

    private final Teamify plugin;

    public VisibilityManager(Teamify plugin) {
        this.plugin = plugin;
    }

    /** Rebuilds the private visibility scoreboard for a single player. */
    public void refresh(Player player) {
        Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());

        if (team == null) {
            // Not in a team - nothing special to show, use the shared board.
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }

        boolean seeMembers = plugin.getConfigManager().isSeeMembersWhenInvis();
        boolean seeAllies = plugin.getConfigManager().isSeeAlliesWhenInvis();

        if (!seeMembers && !seeAllies) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard board = manager.getNewScoreboard();
        org.bukkit.scoreboard.Team sbTeam = board.registerNewTeam(TEAM_NAME);
        sbTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        sbTeam.setCanSeeFriendlyInvisibles(true);

        addEntrySafe(sbTeam, player.getName());

        if (seeMembers) {
            for (UUID memberId : team.getMembers().keySet()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) addEntrySafe(sbTeam, member.getName());
            }
        }

        if (seeAllies) {
            for (var entry : team.getRelations().entrySet()) {
                if (entry.getValue() != RelationType.ALLY) continue;
                Team allyTeam = plugin.getTeamManager().getTeam(entry.getKey());
                if (allyTeam == null) continue;
                for (UUID memberId : allyTeam.getMembers().keySet()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null) addEntrySafe(sbTeam, member.getName());
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
            if (entry.getValue() != RelationType.ALLY) continue;
            Team allyTeam = plugin.getTeamManager().getTeam(entry.getKey());
            if (allyTeam == null) continue;
            for (UUID memberId : allyTeam.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) refresh(p);
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
