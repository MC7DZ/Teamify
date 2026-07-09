package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.RelationType;
import gg.MC7DZ.teamify.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class RelationsMenuGui extends GuiHolder {

    private final Team team;
    private final Map<Integer, UUID> slotToTeam = new HashMap<>();
    private int backButtonSlot = -1; // Initialize with an invalid slot

    public RelationsMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        build();
    }

    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.relations-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Allies"));
        int size = cfg.getInt("size", 54);
        
        Material allyMat = parse(cfg.getString("ally-material", "LIME_WOOL"), Material.LIME_WOOL);
        String itemNameFormat = cfg.getString("item-name-format", "{name}");
        List<String> itemLoreConfig = cfg.getStringList("item-lore");

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

        int slot = 0;
        for (var entry : team.getRelations().entrySet()) {
            // Skip reserved slots (back button and, if configured, filler slots)
            while (slot < size && reservedSlots.contains(slot)) {
                slot++;
            }
            if (slot >= size) break;

            UUID otherId = entry.getKey();
            RelationType type = entry.getValue();
            // Only allies are shown here - enemies/neutral relations still
            // exist under the hood (PVP, /team allyinvite, etc) but aren't
            // surfaced in this menu.
            if (type != RelationType.ALLY) continue;
            Team other = plugin.getTeamManager().getTeam(otherId);
            if (other == null) continue;

            String displayName = plugin.getConfigManager().color(itemNameFormat
                    .replace("{name}", other.getColoredName())
                    .replace("{tag}", other.getTag())
                    .replace("{level}", String.valueOf(other.getLevel()))
                    .replace("{members}", String.valueOf(other.getSize()))
                    .replace("{online}", String.valueOf(countVisibleOnline(other)))
                    .replace("{relation}", type.name()));

            List<String> lore = new ArrayList<>();
            for (String line : itemLoreConfig) {
                String processedLine = line
                        .replace("{name}", other.getColoredName())
                        .replace("{tag}", other.getTag())
                        .replace("{level}", String.valueOf(other.getLevel()))
                        .replace("{members}", String.valueOf(other.getSize()))
                        .replace("{online}", String.valueOf(countVisibleOnline(other)))
                        .replace("{relation}", type.name());
                lore.add(plugin.getConfigManager().color(processedLine));
            }

            ItemStack item = other.hasCustomItem()
                    ? GuiItem.withOverrides(other.getCustomItem(), displayName, lore)
                    : GuiItem.simple(allyMat, displayName, lore.toArray(new String[0]));
            inv.setItem(slot, item);
            slotToTeam.put(slot, otherId);
            slot++;
        }

        setInventory(inv);
    }

    /** Counts how many members of an allied team are currently online. */
    private int countVisibleOnline(Team allyTeam) {
        int count = 0;
        for (UUID memberId : allyTeam.getMembers().keySet()) {
            if (Bukkit.getOfflinePlayer(memberId).isOnline()) count++;
        }
        return count;
    }

    private Material parse(String s, Material fallback) {
        try {
            return Material.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            new MainMenuGui(p, team).open(); // Go back to MainMenuGui
            return;
        }

        UUID otherId = slotToTeam.get(slot);
        if (otherId == null) return;
        Team other = plugin.getTeamManager().getTeam(otherId);
        if (other == null) return;

        gg.MC7DZ.teamify.config.ConfigManager cm = plugin.getConfigManager();
        gg.MC7DZ.teamify.team.TeamManager tm = plugin.getTeamManager();

        if (!cm.isAlliesEnabled()) {
            p.sendMessage(cm.getMessage("allies-disabled"));
            return;
        }
        gg.MC7DZ.teamify.team.TeamRole role = team.getRole(p.getUniqueId());
        if (!plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-manage-relations", false)) {
            p.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }
        if (!tm.areAllied(team, other)) {
            p.sendMessage(cm.getMessage("not-allied", "team", other.getName()));
            return;
        }

        Runnable doAllyLeave = () -> {
            tm.removeAlly(team, other);
            plugin.getVisibilityManager().refreshTeamAndAllies(team);
            plugin.getVisibilityManager().refreshTeamAndAllies(other);
            for (UUID memberId : team.getMembers().keySet()) {
                Player online = Bukkit.getPlayer(memberId);
                if (online != null) online.sendMessage(cm.getMessage("ally-removed", "team", other.getName()));
            }
            for (UUID memberId : other.getMembers().keySet()) {
                Player online = Bukkit.getPlayer(memberId);
                if (online != null) online.sendMessage(cm.getMessage("ally-removed", "team", team.getName()));
            }
            new RelationsMenuGui(p, team).open();
        };

        new ConfirmMenuGui(p, doAllyLeave, () -> new RelationsMenuGui(p, team).open()).open();
    }
}