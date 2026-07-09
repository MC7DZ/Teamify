package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.config.ConfigManager;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamManager;
import gg.MC7DZ.teamify.team.TeamRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shows every pending request aimed at the viewer's team in one place:
 * - join requests from players wanting to join (player heads, sent via
 *   /team joinrequest)
 * - alliance requests from other teams wanting to ally with this one (eye
 *   of ender, sent via /team allyinvite)
 * <p>
 * Left-click accepts an entry, right-click denies it. Both item templates
 * (material/name/lore) are fully configurable from gui.yml's
 * gui.requests-menu section.
 */
public class RequestsMenuGui extends GuiHolder {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Team team;
    private final Map<Integer, UUID> slotToJoinRequest = new HashMap<>();
    private final Map<Integer, UUID> slotToAllyRequest = new HashMap<>();
    private int backButtonSlot = -1;

    public RequestsMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        build();
    }

    @Override
    protected void build() {
        slotToJoinRequest.clear();
        slotToAllyRequest.clear();

        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.requests-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Requests"));
        int size = cfg.getInt("size", 54);

        Inventory inv = Bukkit.createInventory(this, size, titleComponent(title));

        Set<Integer> reservedSlots = new HashSet<>();
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
        if (backButtonSlot != -1) reservedSlots.add(backButtonSlot);

        int slot = 0;

        ConfigurationSection joinItemCfg = cfg.getConfigurationSection("join-request-item");
        for (UUID requester : new ArrayList<>(team.getPendingJoinRequests())) {
            while (slot < size && reservedSlots.contains(slot)) slot++;
            if (slot >= size) break;

            OfflinePlayer op = Bukkit.getOfflinePlayer(requester);
            String playerName = op.getName() != null ? op.getName() : "Unknown";

            inv.setItem(slot, buildJoinRequestItem(joinItemCfg, requester, playerName));
            slotToJoinRequest.put(slot, requester);
            slot++;
        }

        ConfigurationSection allyItemCfg = cfg.getConfigurationSection("ally-request-item");
        for (UUID otherId : new ArrayList<>(team.getPendingAllyInvites())) {
            while (slot < size && reservedSlots.contains(slot)) slot++;
            if (slot >= size) break;

            Team other = plugin.getTeamManager().getTeam(otherId);
            if (other == null) continue;

            inv.setItem(slot, buildAllyRequestItem(allyItemCfg, other));
            slotToAllyRequest.put(slot, otherId);
            slot++;
        }

        setInventory(inv);
    }

    private ItemStack buildJoinRequestItem(ConfigurationSection cfg, UUID requester, String playerName) {
        Material mat = Material.PLAYER_HEAD;
        if (cfg != null) {
            try {
                mat = Material.valueOf(cfg.getString("material", "PLAYER_HEAD").toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String name = cfg != null ? cfg.getString("name", "&a&l{player}") : "&a&l{player}";
        List<String> lore = cfg != null ? cfg.getStringList("lore") : new ArrayList<>();
        boolean glow = cfg != null && cfg.getBoolean("glow", false);

        name = plugin.getConfigManager().color(name.replace("{player}", playerName));
        List<String> processedLore = new ArrayList<>();
        for (String line : lore) {
            processedLore.add(plugin.getConfigManager().color(line.replace("{player}", playerName)));
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (mat == Material.PLAYER_HEAD && meta instanceof SkullMeta skullMeta) {
                skullMeta.setPlayerProfile(Bukkit.createProfile(requester));
            }
            applyNameAndLore(meta, name, processedLore);
            item.setItemMeta(meta);
            if (glow) applyGlow(item);
        }
        return item;
    }

    private ItemStack buildAllyRequestItem(ConfigurationSection cfg, Team other) {
        Material mat = Material.ENDER_EYE;
        if (cfg != null) {
            try {
                mat = Material.valueOf(cfg.getString("material", "ENDER_EYE").toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String nameFormat = cfg != null ? cfg.getString("name", "&b&l{team}") : "&b&l{team}";
        List<String> loreFormat = cfg != null ? cfg.getStringList("lore") : new ArrayList<>();
        boolean glow = cfg != null && cfg.getBoolean("glow", false);

        String name = applyTeamPlaceholders(nameFormat, other);
        List<String> lore = new ArrayList<>();
        for (String line : loreFormat) {
            lore.add(applyTeamPlaceholders(line, other));
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyNameAndLore(meta, name, lore);
            item.setItemMeta(meta);
            if (glow) applyGlow(item);
        }
        return item;
    }

    private void applyNameAndLore(ItemMeta meta, String name, List<String> lore) {
        meta.displayName(toComponent(name));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(toComponent(line));
        }
        meta.lore(loreComponents);
    }

    private Component toComponent(String legacyText) {
        return LEGACY.deserialize(legacyText == null ? "" : legacyText)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    private void applyGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private String applyTeamPlaceholders(String text, Team other) {
        return plugin.getConfigManager().color(text
                .replace("{team}", other.getColoredName())
                .replace("{tag}", other.getTag())
                .replace("{level}", String.valueOf(other.getLevel()))
                .replace("{members}", String.valueOf(other.getSize())));
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();

        if (slot == backButtonSlot) {
            new MainMenuGui(p, team).open();
            return;
        }

        ConfigManager cm = plugin.getConfigManager();
        TeamManager tm = plugin.getTeamManager();

        UUID requester = slotToJoinRequest.get(slot);
        if (requester != null) {
            handleJoinRequestClick(p, cm, tm, requester, clickType);
            return;
        }

        UUID otherId = slotToAllyRequest.get(slot);
        if (otherId != null) {
            handleAllyRequestClick(p, cm, tm, otherId, clickType);
        }
    }

    private void handleJoinRequestClick(Player p, ConfigManager cm, TeamManager tm, UUID requester, ClickType clickType) {
        TeamRole role = team.getRole(p.getUniqueId());
        if (role == null || !plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-invite", false)) {
            p.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }

        OfflinePlayer op = Bukkit.getOfflinePlayer(requester);
        String playerName = op.getName() != null ? op.getName() : "Unknown";

        if (clickType.isRightClick()) {
            team.removeJoinRequest(requester);
            tm.saveTeam(team);
            p.sendMessage(cm.getPrefix() + cm.color("&7You denied the join request from &f" + playerName + "&7."));
            Player online = op.getPlayer();
            if (online != null) online.sendMessage(cm.getMessage("join-request-denied", "team", team.getName()));
            new RequestsMenuGui(p, team).open();
            return;
        }
        if (!clickType.isLeftClick()) return;

        team.removeJoinRequest(requester);
        if (tm.isInTeam(requester)) {
            tm.saveTeam(team);
            p.sendMessage(cm.getPrefix() + cm.color("&cThat player already joined another team."));
            new RequestsMenuGui(p, team).open();
            return;
        }
        int maxMembers = cm.getMaxMembers();
        if (maxMembers > 0 && team.getSize() >= maxMembers) {
            tm.saveTeam(team);
            p.sendMessage(cm.getMessage("team-full"));
            new RequestsMenuGui(p, team).open();
            return;
        }

        tm.addMember(team, requester, TeamRole.MEMBER);
        tm.saveTeam(team);
        plugin.getVisibilityManager().refreshTeamAndAllies(team);

        for (UUID memberId : team.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) continue;
            if (memberId.equals(requester)) {
                member.sendMessage(cm.getMessage("join-request-accepted", "team", team.getName()));
            } else {
                member.sendMessage(cm.getMessage("player-joined-broadcast", "player", playerName));
            }
        }
        new RequestsMenuGui(p, team).open();
    }

    private void handleAllyRequestClick(Player p, ConfigManager cm, TeamManager tm, UUID otherId, ClickType clickType) {
        if (!cm.isAlliesEnabled()) {
            p.sendMessage(cm.getMessage("allies-disabled"));
            return;
        }
        TeamRole role = team.getRole(p.getUniqueId());
        if (role == null || !plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-manage-relations", false)) {
            p.sendMessage(cm.getMessage("not-enough-permission-role"));
            return;
        }

        Team other = tm.getTeam(otherId);
        if (other == null) {
            team.removeAllyInvite(otherId);
            tm.saveTeam(team);
            new RequestsMenuGui(p, team).open();
            return;
        }

        if (clickType.isRightClick()) {
            team.removeAllyInvite(otherId);
            tm.saveTeam(team);
            p.sendMessage(cm.getPrefix() + cm.color("&7You denied the alliance request from &f" + other.getName() + "&7."));
            new RequestsMenuGui(p, team).open();
            return;
        }
        if (!clickType.isLeftClick()) return;

        team.removeAllyInvite(otherId);
        other.removeAllyInvite(team.getId());
        tm.setAllied(team, other);

        plugin.getVisibilityManager().refreshTeamAndAllies(team);
        plugin.getVisibilityManager().refreshTeamAndAllies(other);
        for (UUID memberId : team.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) online.sendMessage(cm.getMessage("ally-added", "team", other.getName()));
        }
        for (UUID memberId : other.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) online.sendMessage(cm.getMessage("ally-added", "team", team.getName()));
        }
        new RequestsMenuGui(p, team).open();
    }
}
