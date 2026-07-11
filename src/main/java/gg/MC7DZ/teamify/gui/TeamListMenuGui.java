package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.team.Team;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeamListMenuGui extends GuiHolder {

    private final Map<Integer, UUID> slotToTeam = new HashMap<>();
    private int backButtonSlot = -1; // Initialize with an invalid slot

    public TeamListMenuGui(Player viewer) {
        super(viewer);
        build();
    }

    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.team-list-menu");
        Component title = plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>All Teams"));
        int size = cfg.getInt("size", 54);
        String sortBy = cfg.getString("sort-by", "LEVEL");
        String itemNameFormat = cfg.getString("item-name-format", "{color}{name} <gray>[{tag}]"); // NEW: Get item name format
        List<String> itemLoreConfig = cfg.getStringList("item-lore");

        List<Team> teams = new ArrayList<>(plugin.getTeamManager().getTeams());
        Comparator<Team> comparator = switch (sortBy) {
            case "MEMBERS" -> Comparator.comparingInt(Team::getSize).reversed();
            case "NAME" -> Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparingInt(Team::getLevel).reversed();
        };
        teams.sort(comparator);

        Inventory inv = Bukkit.createInventory(this, size, title);

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
                    inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
                }
            }
        }

        // Handle back button
        ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
        if (itemsCfg != null && itemsCfg.contains("back")) {
            backButtonSlot = itemsCfg.getInt("back.slot", -1);
            if (backButtonSlot != -1) {
                setBackButton(inv, backButtonSlot);
            }
        }
        if (backButtonSlot != -1) {
            reservedSlots.add(backButtonSlot);
        }

        int slot = 0;
        for (Team team : teams) {
            // Skip reserved slots (back button and, if configured, filler slots)
            while (slot < size && reservedSlots.contains(slot)) {
                slot++;
            }
            if (slot >= size) break;

            // Process item name
            Component displayName = processPlaceholders(itemNameFormat, team);

            List<Component> lore = itemLoreConfig.stream()
                    .map(line -> processPlaceholders(line, team))
                    .collect(Collectors.toList());

            ItemStack item = team.hasCustomItem()
                    ? GuiItem.withOverrides(team.getCustomItem(), displayName, lore)
                    : GuiItem.simple(Material.WHITE_BANNER, displayName, lore.toArray(new Component[0]));
            inv.setItem(slot, item);
            slotToTeam.put(slot, team.getId());
            slot++;
        }
        setInventory(inv);
    }

    private Component processPlaceholders(String text, Team team) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(team.getOwner());
        String ownerName = owner.hasPlayedBefore() ? owner.getName() : "Unknown";
        String createdDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(team.getCreatedAt()));


        return plugin.getConfigManager().color(text
                .replace("{color}", team.getColor().toString()) // NEW: Team color code
                .replace("{name}", team.getName()) // NEW: Team raw name
                .replace("{level}", String.valueOf(team.getLevel()))
                .replace("{members}", String.valueOf(team.getSize()))
                .replace("{tag}", team.getTag())
                .replace("{owner_name}", ownerName)
                .replace("{bank_balance}", plugin.getEconomyManager().format(team.getBankBalance()))
                .replace("{xp}", String.valueOf(team.getXp()))
                .replace("{allies}", String.valueOf(team.getAllyCount()))
                .replace("{homes_count}", String.valueOf(team.getHomes().size()))
                .replace("{kills}", String.valueOf(team.getTotalKills()))
                .replace("{created_date}", createdDate));
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            if (plugin.getTeamManager().isInTeam(p.getUniqueId())) {
                new MainMenuGui(p, plugin.getTeamManager().getTeamOf(p.getUniqueId())).open();
            } else {
                new PlayerSettingsMenuGui(p).open();
            }
            return;
        }

        UUID teamId = slotToTeam.get(slot);
        if (teamId == null) return;
        Team team = plugin.getTeamManager().getTeam(teamId);
        if (team == null) return;

        p.sendMessage(plugin.getConfigManager().getPrefix().append(
                plugin.getConfigManager().color("<aqua>Viewing <white>" + team.getName() +
                        " <gray>| Level " + team.getLevel() + " | " + team.getSize() + " members")));
    }
}