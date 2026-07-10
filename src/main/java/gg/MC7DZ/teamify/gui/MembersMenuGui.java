package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MembersMenuGui extends GuiHolder {

    private final Team team;
    private final Map<Integer, UUID> slotToMember = new HashMap<>();
    private int backButtonSlot = -1; // Initialize with an invalid slot

    public MembersMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        build();
    }

    @Override // Added @Override annotation
    protected void build() { // Changed to protected
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.members-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Team Members"));
        int size = cfg.getInt("size", 54);
        boolean showOnline = cfg.getBoolean("show-online-status", true);
        String onlineColor = plugin.getConfigManager().color(cfg.getString("online-name-color", "&a"));
        String offlineColor = plugin.getConfigManager().color(cfg.getString("offline-name-color", "&7"));
        String itemNameFormat = cfg.getString("item-name-format", "{color}{name}");
        List<String> itemLoreConfig = cfg.getStringList("item-lore");
        String offlineHeadTexture = cfg.getString("offline-head-texture");

        Inventory inv = Bukkit.createInventory(this, size, titleComponent(title));

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
                // Only reserve (skip) filler slots when filler-slots is explicitly
                // defined. If fill-empty-slots is true but no filler-slots list is
                // given, every slot gets filled anyway and items overwrite the filler.
                reservedSlots.addAll(fillerSlots);
            } else {
                // Fallback to filling all empty slots if no specific filler-slots are defined
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

        // Sort members: online first, then by name
        List<Map.Entry<UUID, TeamRole>> sortedMembers = new ArrayList<>(team.getMembers().entrySet());
        sortedMembers.sort(Comparator
                .comparing((Map.Entry<UUID, TeamRole> e) -> Bukkit.getOfflinePlayer(e.getKey()).isOnline(), Comparator.reverseOrder())
                .thenComparing(e -> {
                    String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
                    return n != null ? n : "";
                }, String.CASE_INSENSITIVE_ORDER));

        int slot = 0;
        for (Map.Entry<UUID, TeamRole> entry : sortedMembers) {
            // Skip reserved slots (back button and, if configured, filler slots)
            while (slot < size && reservedSlots.contains(slot)) {
                slot++;
            }
            if (slot >= size) break;

            UUID uuid = entry.getKey();
            TeamRole role = entry.getValue();
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            boolean online = op.isOnline();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                boolean useMirror = offlineHeadTexture == null || offlineHeadTexture.isEmpty()
                        || offlineHeadTexture.equalsIgnoreCase("mirror");
                if (!online && !useMirror) {
                    applyOfflineTexture(meta, offlineHeadTexture);
                } else {
                    meta.setPlayerProfile(Bukkit.createProfile(uuid));
                }
                String color = showOnline ? (online ? onlineColor : offlineColor) : "&f";
                String statusColor = online ? "&a" : "&c";
                String status = online ? "Online" : "Offline";
                String playerName = op.getName() != null ? op.getName() : "Unknown";

                String displayName = plugin.getConfigManager().color(itemNameFormat
                        .replace("{color}", color)
                        .replace("{name}", playerName)
                        .replace("{role}", role.name())
                        .replace("{kills}", String.valueOf(team.getKills(uuid)))
                        .replace("{status_color}", statusColor)
                        .replace("{status}", status));

                List<String> lore = new ArrayList<>();
                for (String line : itemLoreConfig) {
                    String processedLine = line
                            .replace("{color}", color)
                            .replace("{name}", playerName)
                            .replace("{role}", role.name())
                            .replace("{kills}", String.valueOf(team.getKills(uuid)))
                            .replace("{status_color}", statusColor)
                            .replace("{status}", status);
                    lore.add(plugin.getConfigManager().color(processedLine));
                }

                meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(displayName)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                            .deserialize(line)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                }
                meta.lore(loreComponents);
                head.setItemMeta(meta);
            }

            inv.setItem(slot, head);
            slotToMember.put(slot, uuid);
            slot++;
        }

        setInventory(inv);
    }

    /**
     * Applies a base64-encoded skin texture (same format used by the
     * offline-head-texture config, i.e. a base64 blob decoding to
     * {"textures":{"SKIN":{"url":"..."}}}) to a player head's SkullMeta.
     */
    private void applyOfflineTexture(SkullMeta meta, String base64Texture) {
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        org.bukkit.profile.PlayerTextures textures = profile.getTextures();
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Texture);
            String decodedString = new String(decodedBytes);
            JsonObject json = JsonParser.parseString(decodedString).getAsJsonObject();
            String textureUrl = json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            textures.setSkin(new java.net.URL(textureUrl));
        } catch (Exception e) {
            System.err.println("Error decoding or parsing offline head texture: " + e.getMessage());
        }
        profile.setTextures(textures);
        meta.setPlayerProfile(profile);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            new MainMenuGui(p, team).open(); // Go back to MainMenuGui
            return;
        }

        UUID target = slotToMember.get(slot);
        if (target == null) return;
        new MemberActionsMenuGui(p, team, target).open();
    }
}