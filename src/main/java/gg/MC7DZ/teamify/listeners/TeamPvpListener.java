package gg.MC7DZ.teamify.listeners;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class TeamPvpListener implements Listener {
   private final Teamify plugin;

   public TeamPvpListener(Teamify plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player victim) {
         Player damager = this.resolveDamager(event);
         if (damager != null && !damager.equals(victim)) {
            if (!this.plugin.getConfigManager().isFriendlyFireWithinTeam()) {
               Team victimTeam = this.plugin.getTeamManager().getTeamOf(victim.getUniqueId());
               Team damagerTeam = this.plugin.getTeamManager().getTeamOf(damager.getUniqueId());
               if (victimTeam != null && damagerTeam != null) {
                  if (victimTeam.getId().equals(damagerTeam.getId())) {
                     if (!victimTeam.isPvpEnabled()) {
                        event.setCancelled(true);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent event) {
      Player victim = event.getEntity();
      Player killer = victim.getKiller();
      if (killer != null && !killer.equals(victim)) {
         Team killerTeam = this.plugin.getTeamManager().getTeamOf(killer.getUniqueId());
         if (killerTeam != null) {
            Team victimTeam = this.plugin.getTeamManager().getTeamOf(victim.getUniqueId());
            boolean sameTeam = victimTeam != null && victimTeam.getId().equals(killerTeam.getId());
            if (!sameTeam || this.plugin.getConfigManager().isCountTeamKillsEnabled()) {
               killerTeam.addKill(killer.getUniqueId());
               this.plugin.getTeamManager().saveTeam(killerTeam);
            }
         }
      }
   }

   private Player resolveDamager(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player p) {
         return p;
      } else {
         return event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null;
      }
   }
}
