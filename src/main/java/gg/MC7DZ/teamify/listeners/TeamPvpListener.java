package gg.MC7DZ.teamify.listeners;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

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
