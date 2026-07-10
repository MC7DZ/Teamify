package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.player.PlayerData;
import gg.MC7DZ.teamify.team.Team; // Import Team
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayersListMenuGui extends GuiHolder {

    private final Map<Integer, UUID> slotToPlayer = new HashMap<>();
    private int backButtonSlot = -1;

    public PlayersListMenuGui(Player viewer) {
        super(viewer);
        build();
    }

    @Override
    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.players-list-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Players List"));
        int size = cfg.getInt("size", 54);
        String onlineItemNameFormat = cfg.getString("online-item-name-format", "&a{player_name} &7(Online)");
        String offlineItemNameFormat = cfg.getString("offline-item-name-format", "&c{player_name} &7(Offline)");
        List<String> onlineItemLoreConfig = cfg.getStringList("online-item-lore");
        List<String> offlineItemLoreConfig = cfg.getStringList("offline-item-lore");
        String offlineHeadTexture = cfg.getString("offline-head-texture"); // Get offline head texture

        Inventory inv = Bukkit.createInventory(this, size, titleComponent(title));

        // Get all player data, excluding those marked hidden in players_list.yml
        List<PlayerData> allPlayers = plugin.getPlayerManager().getAllPlayers().values().stream()
                .filter(pd -> !pd.isHidden())
                .collect(Collectors.toList());

        // Sort players: online first, then by name
        allPlayers.sort(Comparator
                .comparing((PlayerData p) -> Bukkit.getOfflinePlayer(p.getUuid()).isOnline(), Comparator.reverseOrder())
                .thenComparing(PlayerData::getName, String.CASE_INSENSITIVE_ORDER));

        // Fill empty slots if configured
        java.util.Set<Integer> reservedSlots = new java.util.HashSet<>();
        if (cfg.getBoolean("fill-empty-slots", true)) {
            Material filler;
            try {
                filler = Material.valueOf(cfg.getString("filler-item", "GRAY_STAINED_GLASS_PANE"));
            } catch (IllegalArgumentException e) {
                filler = Material.GRAY_STAINED_GLASS_PANE;
            }
            List<Integer> fillerSlots = cfg.getIntegerList("filler-slots");
            if (fillerSlots != null && !fillerSlots.isEmpty()) {
                fillSlots(inv, filler, fillerSlots);
                reservedSlots.addAll(fillerSlots);
            } else {
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, GuiItem.simple(filler, " "));
                }
            }
        }

        // Handle back button
        ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
        if (itemsCfg != null && itemsCfg.contains("back")) {
            backButtonSlot = itemsCfg.getInt("back.slot", -1);
            if (backButtonSlot != -1) {
                ConfigurationSection backButtonData = plugin.getGuiConfig().getConfigurationSection("gui.back-button");
                if (backButtonData != null) {
                    setBackButton(inv, backButtonSlot,
                            plugin.getConfigManager().color(backButtonData.getString("name", "&cBack")),
                            backButtonData.getStringList("lore"));
                }
            }
        }
        if (backButtonSlot != -1) {
            reservedSlots.add(backButtonSlot);
        }

        int slot = 0;
        for (PlayerData playerData : allPlayers) {
            while (slot < size && reservedSlots.contains(slot)) {
                slot++;
            }
            if (slot >= size) break;

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerData.getUuid());
            boolean isOnline = offlinePlayer.isOnline();

            String displayName = isOnline
                    ? plugin.getConfigManager().color(onlineItemNameFormat.replace("{player_name}", playerData.getName()))
                    : plugin.getConfigManager().color(offlineItemNameFormat.replace("{player_name}", playerData.getName()));

            List<String> lore = isOnline
                    ? onlineItemLoreConfig.stream().map(line -> processPlaceholders(line, playerData, isOnline)).collect(Collectors.toList())
                    : offlineItemLoreConfig.stream().map(line -> processPlaceholders(line, playerData, isOnline)).collect(Collectors.toList());

            ItemStack item;
            boolean useMirror = offlineHeadTexture == null || offlineHeadTexture.isEmpty()
                    || offlineHeadTexture.equalsIgnoreCase("mirror");
            if (!isOnline && !useMirror) {
                item = GuiItem.playerHead(offlineHeadTexture, displayName, lore.toArray(new String[0]));
            } else {
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
                meta.setPlayerProfile(Bukkit.createProfile(offlinePlayer.getUniqueId(), offlinePlayer.getName()));
                playerHead.setItemMeta(meta);
                item = GuiItem.withOverrides(playerHead, displayName, lore);
            }
            inv.setItem(slot, item);
            slotToPlayer.put(slot, playerData.getUuid());
            slot++;
        }
        setInventory(inv);
    }

    private String processPlaceholders(String text, PlayerData playerData, boolean isOnline) {
        return plugin.getConfigManager().color(text
                .replace("{player_name}", playerData.getName())
                .replace("{player_uuid}", playerData.getUuid().toString())
                .replace("{online_status}", isOnline ? "&aOnline" : "&cOffline"));
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            // Check if the player is in a team before opening MainMenuGui
            Team team = plugin.getTeamManager().getTeamOf(p.getUniqueId()); // Get team here
            if (team != null) { // Null check added here
                new MainMenuGui(p, team).open();
            } else {
                // If not in a team, go back to PlayerSettingsMenuGui
                new PlayerSettingsMenuGui(p).open();
            }
            return;
        }

        UUID targetPlayerId = slotToPlayer.get(slot);
        if (targetPlayerId == null) return;

        if (targetPlayerId.equals(p.getUniqueId())) {
            p.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().color("&cYou can't invite yourself."));
            return;
        }

        gg.MC7DZ.teamify.team.Team team = plugin.getTeamManager().getTeamOf(p.getUniqueId());
        if (team == null) {
            p.sendMessage(plugin.getConfigManager().getMessage("no-team"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerId);
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        Runnable doInvite = () -> {
            plugin.getTeamCommand().invitePlayerToTeam(p, team, target);
            new PlayersListMenuGui(p).open();
        };

        p.sendMessage(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().color("&bInvite &f" + targetName + " &bto your team?"));
        new ConfirmMenuGui(p, doInvite, () -> new PlayersListMenuGui(p).open()).open();
    }
}