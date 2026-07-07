package gg.MC7DZ.teamify.listeners;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final Teamify plugin;
    private final Set<UUID> teamChatToggled = new HashSet<>();

    public PlayerListener(Teamify plugin) {
        this.plugin = plugin;
    }

    public boolean isTeamChatToggled(UUID uuid) {
        return teamChatToggled.contains(uuid);
    }

    public void toggleTeamChat(UUID uuid) {
        if (!teamChatToggled.add(uuid)) {
            teamChatToggled.remove(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfigManager().isTeamChatEnabled()) return;
        Player player = event.getPlayer();
        if (!teamChatToggled.contains(player.getUniqueId())) return;

        Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (team == null) {
            teamChatToggled.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        String format = plugin.getConfigManager().color(plugin.getConfigManager().getTeamChatFormat());
        String role = team.getRole(player.getUniqueId()).name();
        String message = format
                .replace("{role}", role)
                .replace("{player}", player.getName())
                .replace("{message}", event.getMessage());

        for (UUID memberId : team.getMembers().keySet()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null) {
                member.sendMessage(message);
            }
        }
    }
}
