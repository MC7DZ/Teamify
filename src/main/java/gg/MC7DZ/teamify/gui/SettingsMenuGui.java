package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.listeners.PlayerListener;
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

    private final Team team;

    private int pvpSlot;
    private int colorSlot;
    private int itemSlot;
    private int itemApplySlot;
    private int itemClearSlot;
    private int descriptionSlot;
    private int tagChangeSlot; // New slot for tag change
    private int backButtonSlot = -1; // Initialize with an invalid slot

    public SettingsMenuGui(Player viewer, Team team) {
        super(viewer);
        this.team = team;
        build();
    }

    protected void build() {
        ConfigurationSection cfg = plugin.getGuiConfig().getConfigurationSection("gui.settings-menu");
        String title = plugin.getConfigManager().color(cfg.getString("title", "&8Team Settings"));
        int size = cfg.getInt("size", 54);
        
        // Load slots from gui.yml items section
        ConfigurationSection itemsCfg = cfg.getConfigurationSection("items");
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


        Inventory inv = Bukkit.createInventory(this, size, titleComponent(title));

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
                    inv.setItem(i, GuiItem.simple(filler, " "));
                }
            }
        }

        // Handle back button
        if (itemsCfg != null && itemsCfg.contains("back")) {
            ConfigurationSection backButtonData = plugin.getGuiConfig().getConfigurationSection("gui.back-button");
            if (backButtonData != null) {
                setBackButton(inv, backButtonSlot,
                        plugin.getConfigManager().color(backButtonData.getString("name", "&cBack")),
                        backButtonData.getStringList("lore"));
            }
        }

        inv.setItem(pvpSlot, buildPvpItem(cfg));

        boolean canCustomize = canCustomize();

        if (plugin.getConfigManager().isTeamColorEnabled()) {
            inv.setItem(colorSlot, buildColorItem(cfg, canCustomize));
        }

        if (plugin.getConfigManager().isTeamDescriptionEnabled()) {
            inv.setItem(descriptionSlot, buildDescriptionItem(cfg));
        }

        // Build Tag Change Item
        if (itemsCfg != null && itemsCfg.contains("tag-change")) {
            inv.setItem(tagChangeSlot, buildTagChangeItem(itemsCfg.getConfigurationSection("tag-change"), canCustomize));
        }

        if (plugin.getConfigManager().isTeamItemEnabled()) {
            // Do NOT explicitly set an item in itemSlot during build(). It should remain empty (air) by default.
            // setEditableSlot is still called to allow players to place items.
            setEditableSlot(itemSlot, canCustomize);

            // Use item-apply and item-clear from gui.yml
            if (itemsCfg != null) {
                inv.setItem(itemApplySlot, GuiItem.fromConfig(itemsCfg.getConfigurationSection("item-apply")));
                inv.setItem(itemClearSlot, GuiItem.fromConfig(itemsCfg.getConfigurationSection("item-clear")));
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

    private ItemStack buildDescriptionItem(ConfigurationSection cfg) {
        Material mat = parse(cfg.getString("description-material", "WRITABLE_BOOK"), Material.WRITABLE_BOOK);
        boolean hasDescription = team.getDescription() != null && !team.getDescription().isEmpty();
        String current = hasDescription ? team.getDescription() : "&7(none set)";
        if (canEditDescription()) {
            return GuiItem.simple(mat, "&bTeam Description",
                    "&7Current: &f" + current,
                    "&7Click to type a new description",
                    "&7in chat.");
        }
        return GuiItem.simple(mat, "&bTeam Description",
                "&7Current: &f" + current,
                "&7Your role can't change this.");
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

    private ItemStack buildTagChangeItem(ConfigurationSection itemCfg, boolean canCustomize) {
        Material mat = parse(itemCfg.getString("material", "NAME_TAG"), Material.NAME_TAG);
        String name = plugin.getConfigManager().color(itemCfg.getString("name", "&bChange Team Tag"));
        List<String> lore = new java.util.ArrayList<>();

        for (String line : itemCfg.getStringList("lore")) {
            lore.add(plugin.getConfigManager().color(line
                    .replace("{tag}", team.getTag())
                    .replace("{min_tag_length}", String.valueOf(plugin.getConfigManager().getMinTagLength()))
                    .replace("{max_tag_length}", String.valueOf(plugin.getConfigManager().getMaxTagLength()))
            ));
        }

        if (!canChangeTag()) {
            lore.add(plugin.getConfigManager().color("&cYour role can't change this."));
        }

        return GuiItem.simple(mat, name, lore.toArray(new String[0]));
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