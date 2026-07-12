package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.listeners.PlayerListener;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import gg.MC7DZ.teamify.util.SoundUtil;
import net.kyori.adventure.text.Component;
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
import java.util.stream.Collectors;

public class SettingsMenuGui extends GuiHolder {

    private final Team team;

    private int pvpSlot;
    private int colorSlot;
    private int itemSlot;
    private int itemApplySlot;
    private int itemClearSlot;
    private int descriptionSlot;
    private int tagChangeSlot; // New slot for tag change
    private int backButtonSlot = -1; // Initialize with an invalid slot

    // Kept around so the click handlers can rebuild items with the same
    // config-driven name/lore/material the player originally saw, instead of
    // each handler reaching back up through the settings-menu section itself.
    private ConfigurationSection itemsCfg;

    public SettingsMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        build();
    }

    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.settings-menu");
        Component title = plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Team Settings"));
        int size = cfg.getInt("size", 54);
        
        // Load slots from gui.yml items section
        itemsCfg = cfg.getConfigurationSection("items");
        if (itemsCfg != null) {
            pvpSlot = itemsCfg.getInt("pvp-toggle.slot", 20);
            colorSlot = itemsCfg.getInt("color.slot", 22);
            descriptionSlot = itemsCfg.getInt("description.slot", 24);
            tagChangeSlot = itemsCfg.getInt("tag-change.slot", 29); // Load new tag change slot
            itemSlot = itemsCfg.getInt("item-input.slot", 31);
            itemApplySlot = itemsCfg.getInt("item-apply.slot", 30);
            itemClearSlot = itemsCfg.getInt("item-clear.slot", 32);
            backButtonSlot = itemsCfg.getInt("back.slot", 45);
        } else {
            // Fallback to default hardcoded slots if items section is missing
            pvpSlot = 20;
            colorSlot = 22;
            descriptionSlot = 24;
            tagChangeSlot = 29; // Default for new tag change slot
            itemSlot = 31;
            itemApplySlot = 30;
            itemClearSlot = 32;
            backButtonSlot = 45;
        }


        Inventory inv = Bukkit.createInventory(this, size, title);

        // Fill empty slots if configured
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
            } else {
                // Fallback to filling all empty slots if no specific filler-slots are defined
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
                }
            }
        }

        // Handle back button
        if (itemsCfg != null && itemsCfg.contains("back")) {
            setBackButton(inv, backButtonSlot);
        }

        setSlotItem(inv, pvpSlot, itemsCfg != null ? itemsCfg.getConfigurationSection("pvp-toggle") : null, buildPvpItem());

        boolean canCustomize = canCustomize();

        if (plugin.getConfigManager().isTeamColorEnabled()) {
            setSlotItem(inv, colorSlot, itemsCfg != null ? itemsCfg.getConfigurationSection("color") : null, buildColorItem(canCustomize));
        }

        if (plugin.getConfigManager().isTeamDescriptionEnabled()) {
            setSlotItem(inv, descriptionSlot, itemsCfg != null ? itemsCfg.getConfigurationSection("description") : null, buildDescriptionItem());
        }

        // Build Tag Change Item
        if (itemsCfg != null && itemsCfg.contains("tag-change")) {
            setSlotItem(inv, tagChangeSlot, itemsCfg.getConfigurationSection("tag-change"),
                    buildTagChangeItem(itemsCfg.getConfigurationSection("tag-change"), canCustomize));
        }

        if (plugin.getConfigManager().isTeamItemEnabled()) {
            // Do NOT explicitly set an item in itemSlot during build(). It should remain empty (air) by default.
            // setEditableSlot is still called to allow players to place items.
            setEditableSlot(itemSlot, canCustomize);

            // Use item-apply and item-clear from gui.yml
            if (itemsCfg != null) {
                placeConfigItem(inv, itemApplySlot, itemsCfg.getConfigurationSection("item-apply"));
                placeConfigItem(inv, itemClearSlot, itemsCfg.getConfigurationSection("item-clear"));
            }
        }

        setInventory(inv);
    }

    private boolean canCustomize() {
        TeamRole role = team.getRole(getViewer().getUniqueId());
        return role != null && plugin.getConfig().getBoolean(
                "roles.permissions." + role.name() + ".can-customize-team", false);
    }

    private boolean canEditDescription() {
        TeamRole role = team.getRole(getViewer().getUniqueId());
        return role != null && plugin.getConfig().getBoolean(
                "roles.permissions." + role.name() + ".can-edit-description", false);
    }

    private boolean canChangeTag() {
        TeamRole role = team.getRole(getViewer().getUniqueId());
        // Assuming only LEADER can change the tag for now, or add a specific permission
        return role == TeamRole.LEADER; // Or check a specific permission like "can-customize-team"
    }

    private ItemStack buildDescriptionItem() {
        ConfigurationSection itemCfg = itemsCfg != null ? itemsCfg.getConfigurationSection("description") : null;
        boolean hasDescription = team.getDescription() != null && !team.getDescription().isEmpty();
        String current = hasDescription ? team.getDescription() : "<gray>(none set)";

        ItemStack item = GuiItem.fromConfig(getViewer(), itemCfg, "description", current);
        if (!canEditDescription()) {
            appendLoreLine(item, plugin.getConfigManager().color("<gray>Your role can't change this."));
        }
        return item;
    }

    private ItemStack buildPvpItem() {
        ConfigurationSection itemCfg = itemsCfg != null ? itemsCfg.getConfigurationSection("pvp-toggle") : null;
        boolean locked = plugin.getConfigManager().isFriendlyFireWithinTeam();

        if (locked) {
            return buildStateItem(itemCfg, "locked",
                    "BARRIER", Material.BARRIER,
                    "<red><bold>PVP Toggle Locked",
                    List.of(
                            "<gray>Friendly fire within teams is forced",
                            "<gray>ON by the server config.",
                            "<gray>An admin must set",
                            "<gray>relations.friendly-fire.within-team",
                            "<gray>to false to allow this toggle."
                    ), "pvp_status", "<red>LOCKED");
        }

        boolean pvpOn = team.isPvpEnabled();
        String status = pvpOn ? "<green>ON" : "<gray>OFF";

        if (pvpOn) {
            return buildStateItem(itemCfg, "on", "LIME_DYE", Material.LIME_DYE,
                    null, null, "pvp_status", status);
        } else {
            return buildStateItem(itemCfg, "off", "GRAY_DYE", Material.GRAY_DYE,
                    null, null, "pvp_status", status);
        }
    }

    /**
     * Builds a state-specific variant of a toggle item (e.g. "on"/"off"/"locked").
     * Looks for {statePrefix}-material / {statePrefix}-name / {statePrefix}-lore
     * in the item's config section, falling back to the base material/name/lore
     * of that section, and finally to the hardcoded defaults passed in.
     *
     * @param itemCfg          the item's config section (e.g. items.pvp-toggle)
     * @param statePrefix      "on", "off", or "locked"
     * @param fallbackMaterial fallback material name if nothing is configured
     * @param fallbackMaterialEnum fallback Material if the configured/base material fails to parse
     * @param fallbackName     fallback MiniMessage name if nothing is configured (null to use base name)
     * @param fallbackLore     fallback lore lines if nothing is configured (null to use base lore)
     * @param placeholders     placeholders applied to name/lore, same pairs as GuiItem#fromConfig
     */
    private ItemStack buildStateItem(ConfigurationSection itemCfg, String statePrefix,
                                      String fallbackMaterial, Material fallbackMaterialEnum,
                                      String fallbackName, List<String> fallbackLore,
                                      String... placeholders) {
        String matStr = itemCfg != null ? itemCfg.getString(statePrefix + "-material", itemCfg.getString("material", fallbackMaterial)) : fallbackMaterial;
        Material mat = parse(matStr, fallbackMaterialEnum);

        String name = itemCfg != null ? itemCfg.getString(statePrefix + "-name", itemCfg.getString("name", fallbackName)) : fallbackName;
        if (name == null) name = "<white>Item";

        List<String> lore;
        if (itemCfg != null && itemCfg.contains(statePrefix + "-lore")) {
            lore = itemCfg.getStringList(statePrefix + "-lore");
        } else if (itemCfg != null && itemCfg.contains("lore")) {
            lore = itemCfg.getStringList("lore");
        } else {
            lore = fallbackLore != null ? fallbackLore : List.of();
        }

        name = applyPlaceholders(name, placeholders);
        List<Component> loreComponents = new java.util.ArrayList<>();
        for (String line : lore) {
            loreComponents.add(plugin.getConfigManager().color(applyPlaceholders(line, placeholders))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }

        boolean glow = itemCfg != null && itemCfg.getBoolean(statePrefix + "-glow", itemCfg.getBoolean("glow", false));

        ItemStack item = GuiItem.simple(mat, plugin.getConfigManager().color(name)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false), loreComponents.toArray(new Component[0]));

        if (glow) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    private String applyPlaceholders(String input, String... placeholders) {
        String result = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return result;
    }

    private ItemStack buildColorItem(boolean canCustomize) {
        ConfigurationSection itemCfg = itemsCfg != null ? itemsCfg.getConfigurationSection("color") : null;
        String currentColor = miniColorTag(team.getColor());

        ItemStack item = GuiItem.fromConfig(getViewer(), itemCfg, "color", currentColor);
        if (!canCustomize) {
            appendLoreLine(item, plugin.getConfigManager().color("<gray>Your role can't change this."));
        }
        return item;
    }

    /**
     * Wraps a color's name in a MiniMessage color tag matching that same
     * color (e.g. AQUA -> "<aqua>AQUA</aqua>"), instead of embedding the
     * legacy §-code ChatColor.toString() directly into a MiniMessage
     * template - which fails to parse and forces a noisy legacy-fallback
     * path (see ConfigManager#color). The 16 standard ChatColor names map
     * 1:1 onto MiniMessage's built-in named colors.
     */
    private String miniColorTag(ChatColor c) {
        String tag = c.name().toLowerCase();
        return "<" + tag + ">" + c.name() + "</" + tag + ">";
    }

    /** Appends an extra lore line to an already-built item (e.g. a permission notice). */
    private void appendLoreLine(ItemStack item, Component line) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.hasLore() ? new java.util.ArrayList<>(meta.lore()) : new java.util.ArrayList<>();
        lore.add(line);
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private ItemStack buildTagChangeItem(ConfigurationSection itemCfg, boolean canCustomize) {
        Material mat = parse(itemCfg.getString("material", "NAME_TAG"), Material.NAME_TAG);
        Component name = plugin.getConfigManager().color(itemCfg.getString("name", "<aqua>Change Team Tag"));
        List<Component> lore = new java.util.ArrayList<>();

        for (String line : itemCfg.getStringList("lore")) {
            lore.add(plugin.getConfigManager().color(line
                    .replace("{tag}", team.getTag())
                    .replace("{min_tag_length}", String.valueOf(plugin.getConfigManager().getMinTagLength()))
                    .replace("{max_tag_length}", String.valueOf(plugin.getConfigManager().getMaxTagLength()))
            ));
        }

        if (!canChangeTag()) {
            lore.add(plugin.getConfigManager().color("<red>Your role can't change this."));
        }

        return GuiItem.simple(mat, name, lore.toArray(new Component[0]));
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Player p = getViewer();
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.settings-menu");

        if (slot == backButtonSlot) {
            new MainMenuGui(p, team).open(); // Go back to MainMenuGui
            return;
        }

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

        if (slot == descriptionSlot) {
            handleDescriptionClick(p);
            return;
        }

        if (slot == tagChangeSlot) {
            handleTagChange(p);
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
        setSlotItem(getInventory(), pvpSlot, itemsCfg != null ? itemsCfg.getConfigurationSection("pvp-toggle") : null, buildPvpItem());
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
            p.sendMessage(plugin.getConfigManager().getMessage("team-color-changed", "color", miniColorTag(newColor)));
            SoundUtil.play(p, plugin.getConfigManager().getGuiSuccessSound());
            setSlotItem(getInventory(), colorSlot, itemsCfg != null ? itemsCfg.getConfigurationSection("color") : null, buildColorItem(true));
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
        // Check if the placed item is null or air
        if (placed == null || placed.getType().isAir()) {
            p.sendMessage(plugin.getConfigManager().getMessage("team-item-apply-empty"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        // Get the old custom item before setting the new one
        ItemStack oldCustomItem = team.getCustomItem();

        // Build the team's new icon from the placed item's material + custom
        // model data (if it has one)
        ItemStack icon = new ItemStack(placed.getType());
        ItemMeta iconMeta = icon.getItemMeta();
        ItemMeta placedMeta = placed.getItemMeta();
        if (iconMeta != null && placedMeta != null && placedMeta.hasCustomModelData()) {
            iconMeta.setCustomModelData(placedMeta.getCustomModelData());
        }
        if (iconMeta != null) {
            icon.setItemMeta(iconMeta);
        }

        // Set the new custom item for the team
        team.setCustomItem(icon);
        plugin.getTeamManager().saveTeam(team);

        // Clear the slot in the GUI (set to air)
        getInventory().setItem(itemSlot, null); // Set to null to make it air

        // Return the item the player just placed into the slot to their inventory
        returnItemToPlayer(p, placed.clone());

        // The old custom item is NOT returned to the player; it is consumed/lost.
        if (oldCustomItem != null) {
            returnItemToPlayer(p, oldCustomItem);
        }

        p.sendMessage(plugin.getConfigManager().getMessage("team-item-applied"));
        SoundUtil.play(p, plugin.getConfigManager().getGuiSuccessSound());
    }

    private void handleClearItem(Player p, ConfigurationSection cfg) {
        if (!canCustomize()) {
            p.sendMessage(plugin.getConfigManager().getMessage("not-enough-permission-role"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        ItemStack oldCustomItem = team.getCustomItem(); // Get the current custom item

        team.setCustomItem(null); // Clear the custom item
        plugin.getTeamManager().saveTeam(team);
        getInventory().setItem(itemSlot, null); // Reset the slot in the GUI to air

        // The old item is NOT returned to the player, it is consumed/lost.
        // if (oldCustomItem != null) {
        //     returnItemToPlayer(p, oldCustomItem);
        // }

        p.sendMessage(plugin.getConfigManager().getMessage("team-item-cleared"));
        SoundUtil.play(p, plugin.getConfigManager().getGuiSuccessSound());
    }

    private void handleDescriptionClick(Player p) {
        if (!plugin.getConfigManager().isTeamDescriptionEnabled()) {
            p.sendMessage(plugin.getConfigManager().getMessage("team-description-disabled"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }
        if (!canEditDescription()) {
            p.sendMessage(plugin.getConfigManager().getMessage("not-enough-permission-role"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        p.closeInventory();
        plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.TEAM_DESCRIPTION);
        p.sendMessage(plugin.getConfigManager().getMessage("team-description-prompt"));
        SoundUtil.play(p, plugin.getConfigManager().getGuiOpenSound());
    }

    private void handleTagChange(Player p) {
        if (!canChangeTag()) {
            p.sendMessage(plugin.getConfigManager().getMessage("not-enough-permission-role"));
            SoundUtil.play(p, plugin.getConfigManager().getGuiErrorSound());
            return;
        }

        p.closeInventory();
        plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.TEAM_TAG);
        p.sendMessage(plugin.getConfigManager().getMessage("team-tag-prompt")); // You'll need to add this message to your messages.yml
        SoundUtil.play(p, plugin.getConfigManager().getGuiOpenSound());
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