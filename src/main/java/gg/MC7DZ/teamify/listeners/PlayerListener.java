package gg.MC7DZ.teamify.listeners;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    /** What the player's next chat message should be interpreted as. */
    public enum PendingInputType { BANK_DEPOSIT, BANK_WITHDRAW, TEAM_DESCRIPTION, TEAM_TAG }

    private final Teamify plugin;
    private final Set<UUID> teamChatToggled = new HashSet<>();
    private final Set<UUID> allyChatToggled = new HashSet<>();
    private final Map<UUID, PendingInputType> pendingInput = new HashMap<>();

    public PlayerListener(Teamify plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // New logins (and reconnecting members) need their "see invisible
        // teammates/allies" scoreboard built right away rather than waiting
        // for the periodic refresh task.
        plugin.getVisibilityManager().refresh(event.getPlayer());
        plugin.getPlayerManager().updatePlayerData(event.getPlayer()); // Update player data on join
        plugin.getPlayerManager().savePlayers(); // Persist to data/players_list.yml right away
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teamChatToggled.remove(event.getPlayer().getUniqueId());
        allyChatToggled.remove(event.getPlayer().getUniqueId());
        pendingInput.remove(event.getPlayer().getUniqueId());
        plugin.getPlayerManager().updatePlayerData(event.getPlayer()); // Update player data on quit
        plugin.getPlayerManager().savePlayers(); // Persist to data/players_list.yml right away
    }

    /** Marks the player's very next chat message as input for a GUI flow (e.g. bank amount). */
    public void awaitInput(UUID uuid, PendingInputType type) {
        pendingInput.put(uuid, type);
    }

    public void cancelPendingInput(UUID uuid) {
        pendingInput.remove(uuid);
    }

    public boolean isTeamChatToggled(UUID uuid) {
        return teamChatToggled.contains(uuid);
    }

    public void toggleTeamChat(UUID uuid) {
        if (!teamChatToggled.add(uuid)) {
            teamChatToggled.remove(uuid);
        }
    }

    public boolean isAllyChatToggled(UUID uuid) {
        return allyChatToggled.contains(uuid);
    }

    public void toggleAllyChat(UUID uuid) {
        if (!allyChatToggled.add(uuid)) {
            allyChatToggled.remove(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Team team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        PendingInputType pending = pendingInput.remove(player.getUniqueId());
        if (pending != null) {
            event.setCancelled(true);
            String message = plainMessage.trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (message.equalsIgnoreCase("cancel")) {
                    player.sendMessage(plugin.getConfigManager().getPrefix().append(plugin.getConfigManager().color("<gray>Cancelled.")));
                    return;
                }
                if (team == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-team"));
                    return;
                }
                switch (pending) {
                    case BANK_DEPOSIT -> plugin.getTeamCommand().bankDeposit(player, team, message);
                    case BANK_WITHDRAW -> plugin.getTeamCommand().bankWithdraw(player, team, message);
                    case TEAM_DESCRIPTION -> plugin.getTeamCommand().setDescription(player, team, message);
                    case TEAM_TAG -> {
                        String newTag = message;
                        int minTagLength = plugin.getConfigManager().getMinTagLength();
                        int maxTagLength = plugin.getConfigManager().getMaxTagLength();
                        boolean allowColorCodes = plugin.getConfigManager().isAllowColorCodes();

                        if (newTag.length() < minTagLength || newTag.length() > maxTagLength) {
                            player.sendMessage(plugin.getConfigManager().getMessage("invalid-tag-length",
                                    "min", String.valueOf(minTagLength),
                                    "max", String.valueOf(maxTagLength)));
                            return;
                        }

                        if (!allowColorCodes) {
                            newTag = newTag.replaceAll("(?i)&([0-9a-fk-or])", ""); // Strip color codes
                        }

                        team.setTag(newTag);
                        plugin.getTeamManager().saveTeam(team);
                        player.sendMessage(plugin.getConfigManager().getMessage("team-tag-changed", "tag", newTag));
                    }
                }
            });
            return;
        }

        if (plugin.getConfigManager().isTeamChatEnabled() && teamChatToggled.contains(player.getUniqueId())) {
            if (team == null) {
                teamChatToggled.remove(player.getUniqueId());
            } else {
                event.setCancelled(true);
                sendTeamChat(player, team, plainMessage);
                return;
            }
        }

        if (plugin.getConfigManager().isAlliesEnabled() && plugin.getConfigManager().isAllyChatEnabled()
                && allyChatToggled.contains(player.getUniqueId())) {
            if (team == null) {
                allyChatToggled.remove(player.getUniqueId());
            } else {
                event.setCancelled(true);
                sendAllyChat(player, team, plainMessage);
            }
        }
    }

    /** Whether general.color-shows contains CHAT (and colored-names is on at all). */
    private boolean isChatColorEnabled() {
        return plugin.getConfigManager().isColoredNamesEnabled()
                && plugin.getConfigManager().isColorShown(gg.MC7DZ.teamify.config.ConfigManager.ColorShow.CHAT);
    }

    private void sendTeamChat(Player player, Team team, String message) {
        String formatString = plugin.getConfigManager().getTeamChatFormat();
        String role = team.getRole(player.getUniqueId()).name();
        
        Component playerNameComponent;
        if (isChatColorEnabled()) {
            playerNameComponent = plugin.getConfigManager().color(plugin.getConfigManager().getTeammateColor().toString() + player.getName());
        } else {
            playerNameComponent = Component.text(player.getName());
        }

        Component out = plugin.getConfigManager().color(formatString
                .replace("{role}", role)
                .replace("{player}", playerNameComponent.insertion()) // Use insertion to represent the component in string replacement
                .replace("{message}", message));

        for (UUID memberId : team.getMembers().keySet()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null) {
                member.sendMessage(out);
            }
        }
    }

    private void sendAllyChat(Player player, Team team, String message) {
        String formatString = plugin.getConfigManager().getAllyChatFormat();
        String role = team.getRole(player.getUniqueId()).name();
        boolean chatColor = isChatColorEnabled();

        Component teammateNameComponent;
        if (chatColor) {
            teammateNameComponent = plugin.getConfigManager().color(plugin.getConfigManager().getTeammateColor().toString() + player.getName());
        } else {
            teammateNameComponent = Component.text(player.getName());
        }
        
        Component allyNameComponent;
        if (chatColor) {
            allyNameComponent = plugin.getConfigManager().color(plugin.getConfigManager().getAlliesColor().toString() + player.getName());
        } else {
            allyNameComponent = Component.text(player.getName());
        }

        Component outForTeam = plugin.getConfigManager().color(formatString
                .replace("{role}", role)
                .replace("{player}", teammateNameComponent.insertion())
                .replace("{team}", team.getName())
                .replace("{message}", message));
        
        Component outForAllies = plugin.getConfigManager().color(formatString
                .replace("{role}", role)
                .replace("{player}", allyNameComponent.insertion())
                .replace("{team}", team.getName())
                .replace("{message}", message));

        // Send to own team members...
        for (UUID memberId : team.getMembers().keySet()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null) member.sendMessage(outForTeam);
        }
        // ...and to every allied team's members.
        for (var entry : team.getRelations().entrySet()) {
            if (entry.getValue() != RelationType.ALLY) continue;
            Team allyTeam = plugin.getTeamManager().getTeam(entry.getKey());
            if (allyTeam == null) continue;
            for (UUID memberId : allyTeam.getMembers().keySet()) {
                Player member = plugin.getServer().getPlayer(memberId);
                if (member != null) member.sendMessage(outForAllies);
            }
        }
    }
}