package gg.MC7DZ.teamify.listeners;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Handles the team-internal PVP toggle.
 *
 * The config option relations.friendly-fire.within-team is the master
 * switch: if it's true, friendly fire within a team is always allowed
 * and the per-team /team pvp toggle is locked (ignored). Only when
 * that config value is false does each team's individual pvpEnabled
 * flag get consulted to decide whether members can hurt each other.
 */
public class TeamPvpListener implements Listener {

    private final Teamify plugin;

    public TeamPvpListener(Teamify plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player damager = resolveDamager(event);
        if (damager == null || damager.equals(victim)) return;

        // Global config forces friendly fire on: never touch this case, the per-team toggle is locked.
        if (plugin.getConfigManager().isFriendlyFireWithinTeam()) return;

        Team victimTeam = plugin.getTeamManager().getTeamOf(victim.getUniqueId());
        Team damagerTeam = plugin.getTeamManager().getTeamOf(damager.getUniqueId());

        if (victimTeam == null || damagerTeam == null) return;
        if (!victimTeam.getId().equals(damagerTeam.getId())) return;

        if (!victimTeam.isPvpEnabled()) {
            event.setCancelled(true);
        }
    }

    /**
     * Credits a team kill whenever a player is killed by another player
     * (directly or via a projectile). Kills are tracked per-member on
     * the killer's team and persisted immediately.
     * <p>
     * Killing your own teammate only counts if kills.count-team-kills is
     * enabled in config.yml - by default those don't inflate the team's
     * kill count.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        Team killerTeam = plugin.getTeamManager().getTeamOf(killer.getUniqueId());
        if (killerTeam == null) return;

        Team victimTeam = plugin.getTeamManager().getTeamOf(victim.getUniqueId());
        boolean sameTeam = victimTeam != null && victimTeam.getId().equals(killerTeam.getId());
        if (sameTeam && !plugin.getConfigManager().isCountTeamKillsEnabled()) return;

        killerTeam.addKill(killer.getUniqueId());
        plugin.getTeamManager().saveTeam(killerTeam);
    }

    private Player resolveDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            return p;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
