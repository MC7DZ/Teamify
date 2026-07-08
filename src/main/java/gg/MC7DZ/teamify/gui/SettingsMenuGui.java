package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import gg.MC7DZ.teamify.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class SettingsMenuGui extends GuiHolder {

    private final Teamify plugin;
    private final Team team;

    private int pvpSlot;
    private int colorSlot;
    private int itemSlot;
    private int itemApplySlot;
    private int itemClearSlot;

    public SettingsMenuGui(Teamify plugin, Player viewer, Team team) {
        super(viewer);
        this.plugin = plugin;
        this.team = team;
        build();
    }

    private void build() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.settings-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Team Settings"));
        int size = cfg.getInt("size", 27);
        pvpSlot = cfg.getInt("pvp-toggle-slot", 13);
        colorSlot = cfg.getInt("color-slot", 11);
        itemSlot = cfg.getInt("item-slot", 15);
        itemApplySlot = cfg.getInt("item-apply-slot", 16);
        itemClearSlot = cfg.getInt("item-clear-slot", 20);

        Inventory inv = Bukkit.createInventory(this, size, title);
        inv.setItem(pvpSlot, buildPvpItem(cfg));

        boolean canCustomize = canCustomize();

        if (plugin.getConfigManager().isTeamColorEnabled()) {
            inv.setItem(colorSlot, buildColorItem(cfg, canCustomize));
        }

        if (plugin.getConfigManager().isTeamItemEnabled()) {
            inv.setItem(itemSlot, team.hasCustomItem() ? team.getCustomItem().clone() : buildItemPlaceholder(cfg));
            setEditableSlot(itemSlot, canCustomize);

            inv.setItem(itemApplySlot, GuiItem.simple(
                    parse(cfg.getString("item-apply-material", "LIME_CONCRETE"), Material.LIME_CONCRETE),
                    "&aApply Item",
                    "&7Place an item in the slot to the left,",
                    "&7then click here to make it your",
                    "&7team's icon. You'll get the item",
                    "&7back once it's applied.",
                    "&7Uses the item's custom model data",
                    "&7if it has one."));

            inv.setItem(itemClearSlot, GuiItem.simple(
                    parse(cfg.getString("item-clear-material", "RED_CONCRETE"), Material.RED_CONCRETE),
                    "&cReset Item",
                    "&7Resets your team's icon back",
                    "&7to the default."));
        }

        setInventory(inv);
    }

    private boolean canCustomize() {
        TeamRole role = team.getRole(getViewer().getUniqueId());
        return role != null && plugin.getConfig().getBoolean(
                "roles.permissions." + role.name() + ".can-customize-team", false);
    }

    private ItemStack buildPvpItem(ConfigurationSection cfg) {
        boolean locked = plugin.getConfigManager().isFriendlyFireWithinTeam();

        if (locked) {
            Material mat = parse(cfg.getString("pvp-locked-material", "BARRIER"), Material.BARRIER);
            return GuiItem.simple(mat, "&cPVP Toggle Locked",
                    "&7Friendly fire within teams is forced",
                    "&7ON by the server config.",
                    "&7An admin must set",
                    "&7relations.friendly-fire.within-team",
                    "&7to false to allow this toggle.");
        }

        boolean pvpOn = team.isPvpEnabled();
        Material mat = parse(cfg.getString(pvpOn ? "pvp-on-material" : "pvp-off-material",
                pvpOn ? "LIME_DYE" : "GRAY_DYE"), pvpOn ? Material.LIME_DYE : Material.GRAY_DYE);

        // Example of the glow option - see GuiItem.simple(..., glow, customModelData, lore...)
        return GuiItem.simple(mat, (pvpOn ? "&aTeam PVP: ON" : "&7Team PVP: OFF"), pvpOn, null,
                "&7Click to toggle whether members",
                "&7of your team can hurt each other.");
    }

    private ItemStack buildColorItem(ConfigurationSection cfg, boolean canCustomize) {
        Material mat = parse(cfg.getString("color-material", "WHITE_DYE"), Material.WHITE_DYE);
        List<String> lore = canCustomize
                ? List.of("&7Current: " + team.getColor() + team.getColor().name(),
                          "&7Left-click: next color",
                          "&7Right-click: previous color")
                : List.of("&7Current: " + team.getColor() + team.getColor().name(),
                          "&7Your role can't change this.");
        return GuiItem.simple(mat, "&bTeam Color", lore.toArray(new String[0]));
    }

    private ItemStack buildItemPlaceholder(ConfigurationSection cfg) {
        Material mat = parse(cfg.getString("item-placeholder-material", "ITEM_FRAME"), Material.ITEM_FRAME);
        return GuiItem.simple(mat, "&7Team Item Slot",
                "&7Drop an item here, then click",
                "&7Apply to make it your team's icon.");
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("gui.settings-menu");

        if (slot == pvpSlot) {
            handlePvpToggle(p, cfg);
            return;
        }

        if (slot == colorSlot) {
            handleColorCycle(p, cfg, clickType);
            return;
        }

        if (slot == itemApplySlot) {
            handleApplyItem(p, cfg);
            return;
        }

        if (slot == itemClearSlot) {
            handleClearItem(p, cfg);
            return;
        }

        // Clicks on the editable item slot itself are handled by Bukkit's
        // normal (uncancelled) inventory logic - nothing to do here.
    }

    private void handlePvpToggle(Player p, ConfigurationSection cfg) {
        if (plugin.getConfigManager().isFriendlyFireWithinTeam()) {
            p.sendMessage(plugin.getConfigManager().getMessage("pvp-locked"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        TeamRole role = team.getRole(p.getUniqueId());
        if (role == null || !plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-toggle-pvp", false)) {
            p.sendMessage(plugin.getConfigManager().getMessage("not-enough-permission-role"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        team.setPvpEnabled(!team.isPvpEnabled());
        plugin.getTeamManager().saveTeam(team);

        p.sendMessage(plugin.getConfigManager().getMessage(team.isPvpEnabled() ? "pvp-enabled" : "pvp-disabled"));
        SoundUtil.play(p, plugin.getConfigManager().getGuiSuccessSound());
        getInventory().setItem(pvpSlot, buildPvpItem(cfg));
    }

    private void handleColorCycle(Player p, ConfigurationSection cfg, ClickType clickType) {
        if (!plugin.getConfigManager().isTeamColorEnabled()) {
            p.sendMessage(plugin.getConfigManager().getMessage("team-color-disabled"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }
        if (!canCustomize()) {
            p.sendMessage(plugin.getConfigManager().getMessage("not-enough-permission-role"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        List<String> colors = plugin.getConfigManager().getAvailableTeamColors();
        int current = colors.indexOf(team.getColor().name());
        int next;
        if (current < 0) {
            next = 0;
        } else if (clickType.isRightClick()) {
            next = (current - 1 + colors.size()) % colors.size();
        } else {
            next = (current + 1) % colors.size();
        }

        try {
            ChatColor newColor = ChatColor.valueOf(colors.get(next).toUpperCase());
            team.setColor(newColor);
            plugin.getTeamManager().saveTeam(team);
            p.sendMessage(plugin.getConfigManager().getMessage("team-color-changed", "color", newColor + newColor.name()));
            SoundUtil.play(p, plugin.getConfigManager().getGuiSuccessSound());
            getInventory().setItem(colorSlot, buildColorItem(cfg, true));
        } catch (IllegalArgumentException ex) {
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
        }
    }

    private void handleApplyItem(Player p, ConfigurationSection cfg) {
        if (!plugin.getConfigManager().isTeamItemEnabled()) {
            p.sendMessage(plugin.getConfigManager().getMessage("team-item-disabled"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }
        if (!canCustomize()) {
            p.sendMessage(plugin.getConfigManager().getMessage("not-enough-permission-role"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        ItemStack placed = getInventory().getItem(itemSlot);
        if (placed == null || placed.getType().isAir()) {
            p.sendMessage(plugin.getConfigManager().getMessage("team-item-apply-empty"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        // Build the team's new icon from the placed item's material + custom
        // model data (if it has one) - then hand the original item straight
        // back to the player instead of consuming it.
        ItemStack original = placed.clone();
        ItemStack icon = new ItemStack(placed.getType());
        ItemMeta iconMeta = icon.getItemMeta();
        ItemMeta placedMeta = placed.getItemMeta();
        if (iconMeta != null && placedMeta != null && placedMeta.hasCustomModelData()) {
            iconMeta.setCustomModelData(placedMeta.getCustomModelData());
        }
        if (iconMeta != null) {
            icon.setItemMeta(iconMeta);
        }

        team.setCustomItem(icon);
        plugin.getTeamManager().saveTeam(team);

        getInventory().setItem(itemSlot, buildItemPlaceholder(cfg));
        returnItemToPlayer(p, original);

        p.sendMessage(plugin.getConfigManager().getMessage("team-item-applied"));
        SoundUtil.play(p, plugin.getConfigManager().getGuiSuccessSound());
    }

    private void handleClearItem(Player p, ConfigurationSection cfg) {
        if (!canCustomize()) {
            p.sendMessage(plugin.getConfigManager().getMessage("not-enough-permission-role"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        ItemStack currentlyPlaced = getInventory().getItem(itemSlot);
        if (currentlyPlaced != null && !currentlyPlaced.getType().isAir() && !team.hasCustomItem()) {
            // Nothing applied yet but something is sitting in the slot - hand it back.
            returnItemToPlayer(p, currentlyPlaced.clone());
        }

        team.setCustomItem(null);
        plugin.getTeamManager().saveTeam(team);
        getInventory().setItem(itemSlot, buildItemPlaceholder(cfg));

        p.sendMessage(plugin.getConfigManager().getMessage("team-item-cleared"));
        SoundUtil.play(p, plugin.getConfigManager().getGuiSuccessSound());
    }

    private void returnItemToPlayer(Player p, ItemStack item) {
        var leftover = p.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            p.getWorld().dropItem(p.getLocation(), extra);
        }
    }

    private Material parse(String s, Material fallback) {
        try {
            return Material.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
