package gg.MC7DZ.teamify.gui;

import gg.MC7DZ.teamify.listeners.PlayerListener;
import gg.MC7DZ.teamify.team.Team;
import gg.MC7DZ.teamify.team.TeamRole;
import gg.MC7DZ.teamify.util.SoundUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SettingsMenuGui extends GuiHolder {
   private final Team team;
   private int pvpSlot;
   private int colorSlot;
   private int itemSlot;
   private int itemApplySlot;
   private int itemClearSlot;
   private int descriptionSlot;
   private int tagChangeSlot;
   private int backButtonSlot = -1;
   private ConfigurationSection itemsCfg;

   public SettingsMenuGui(Player viewer, Team team) {
      super(viewer);
      this.team = team;
      this.build();
   }

   @Override
   protected void build() {
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.settings-menu");
      Component title = this.plugin.getConfigManager().color(cfg.getString("title", "<dark_gray>Team Settings"));
      int size = cfg.getInt("size", 54);
      this.itemsCfg = cfg.getConfigurationSection("items");
      if (this.itemsCfg != null) {
         this.pvpSlot = this.itemsCfg.getInt("pvp-toggle.slot", 20);
         this.colorSlot = this.itemsCfg.getInt("color.slot", 22);
         this.descriptionSlot = this.itemsCfg.getInt("description.slot", 24);
         this.tagChangeSlot = this.itemsCfg.getInt("tag-change.slot", 29);
         this.itemSlot = this.itemsCfg.getInt("item-input.slot", 31);
         this.itemApplySlot = this.itemsCfg.getInt("item-apply.slot", 30);
         this.itemClearSlot = this.itemsCfg.getInt("item-clear.slot", 32);
         this.backButtonSlot = this.itemsCfg.getInt("back.slot", 45);
      } else {
         this.pvpSlot = 20;
         this.colorSlot = 22;
         this.descriptionSlot = 24;
         this.tagChangeSlot = 29;
         this.itemSlot = 31;
         this.itemApplySlot = 30;
         this.itemClearSlot = 32;
         this.backButtonSlot = 45;
      }

      Inventory inv = Bukkit.createInventory(this, size, title);
      if (cfg.getBoolean("fill-empty-slots", true)) {
         Material filler;
         try {
            filler = Material.valueOf(cfg.getString("filler-item", "GRAY_STAINED_GLASS_PANE"));
         } catch (IllegalArgumentException e) {
            filler = Material.GRAY_STAINED_GLASS_PANE;
         }

         List<Integer> fillerSlots = cfg.getIntegerList("filler-slots");
         if (fillerSlots != null && !fillerSlots.isEmpty()) {
            this.fillSlots(inv, filler, fillerSlots);
         } else {
            for (int i = 0; i < size; i++) {
               inv.setItem(i, GuiItem.simple(filler, Component.text(" ")));
            }
         }
      }

      if (this.itemsCfg != null && this.itemsCfg.contains("back")) {
         this.setBackButton(inv, this.backButtonSlot);
      }

      this.setSlotItem(inv, this.pvpSlot, this.itemsCfg != null ? this.itemsCfg.getConfigurationSection("pvp-toggle") : null, this.buildPvpItem());
      boolean canCustomize = this.canCustomize();
      if (this.plugin.getConfigManager().isTeamColorEnabled()) {
         this.setSlotItem(inv, this.colorSlot, this.itemsCfg != null ? this.itemsCfg.getConfigurationSection("color") : null, this.buildColorItem(canCustomize));
      }

      if (this.plugin.getConfigManager().isTeamDescriptionEnabled()) {
         this.setSlotItem(
            inv, this.descriptionSlot, this.itemsCfg != null ? this.itemsCfg.getConfigurationSection("description") : null, this.buildDescriptionItem()
         );
      }

      if (this.itemsCfg != null && this.itemsCfg.contains("tag-change")) {
         this.setSlotItem(
            inv,
            this.tagChangeSlot,
            this.itemsCfg.getConfigurationSection("tag-change"),
            this.buildTagChangeItem(this.itemsCfg.getConfigurationSection("tag-change"), canCustomize)
         );
      }

      if (this.plugin.getConfigManager().isTeamItemEnabled()) {
         this.setEditableSlot(this.itemSlot, canCustomize);
         if (this.itemsCfg != null) {
            this.placeConfigItem(inv, this.itemApplySlot, this.itemsCfg.getConfigurationSection("item-apply"));
            this.placeConfigItem(inv, this.itemClearSlot, this.itemsCfg.getConfigurationSection("item-clear"));
         }
      }

      this.setInventory(inv);
   }

   private boolean canCustomize() {
      TeamRole role = this.team.getRole(this.getViewer().getUniqueId());
      return role != null && this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-customize-team", false);
   }

   private boolean canEditDescription() {
      TeamRole role = this.team.getRole(this.getViewer().getUniqueId());
      return role != null && this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-edit-description", false);
   }

   private boolean canChangeTag() {
      TeamRole role = this.team.getRole(this.getViewer().getUniqueId());
      return role == TeamRole.LEADER;
   }

   private ItemStack buildDescriptionItem() {
      ConfigurationSection itemCfg = this.itemsCfg != null ? this.itemsCfg.getConfigurationSection("description") : null;
      boolean hasDescription = this.team.getDescription() != null && !this.team.getDescription().isEmpty();
      String current = hasDescription ? this.team.getDescription() : "<gray>(none set)";
      ItemStack item = GuiItem.fromConfig(this.getViewer(), itemCfg, "description", current);
      if (!this.canEditDescription()) {
         this.appendLoreLine(item, this.plugin.getConfigManager().color("<gray>Your role can't change this."));
      }

      return item;
   }

   private ItemStack buildPvpItem() {
      ConfigurationSection itemCfg = this.itemsCfg != null ? this.itemsCfg.getConfigurationSection("pvp-toggle") : null;
      boolean locked = this.plugin.getConfigManager().isFriendlyFireWithinTeam();
      if (locked) {
         return this.buildStateItem(
            itemCfg,
            "locked",
            "BARRIER",
            Material.BARRIER,
            "<red><bold>PVP Toggle Locked",
            List.of(
               "<gray>Friendly fire within teams is forced",
               "<gray>ON by the server config.",
               "<gray>An admin must set",
               "<gray>relations.friendly-fire.within-team",
               "<gray>to false to allow this toggle."
            ),
            "pvp_status",
            "<red>LOCKED"
         );
      }

      boolean pvpOn = this.team.isPvpEnabled();
      String status = pvpOn ? "<green>ON" : "<gray>OFF";
      return pvpOn
         ? this.buildStateItem(itemCfg, "on", "LIME_DYE", Material.LIME_DYE, null, null, "pvp_status", status)
         : this.buildStateItem(itemCfg, "off", "GRAY_DYE", Material.GRAY_DYE, null, null, "pvp_status", status);
   }

   private ItemStack buildStateItem(
      ConfigurationSection itemCfg,
      String statePrefix,
      String fallbackMaterial,
      Material fallbackMaterialEnum,
      String fallbackName,
      List<String> fallbackLore,
      String... placeholders
   ) {
      String matStr = itemCfg != null ? itemCfg.getString(statePrefix + "-material", itemCfg.getString("material", fallbackMaterial)) : fallbackMaterial;
      Material mat = this.parse(matStr, fallbackMaterialEnum);
      String name = itemCfg != null ? itemCfg.getString(statePrefix + "-name", itemCfg.getString("name", fallbackName)) : fallbackName;
      if (name == null) {
         name = "<white>Item";
      }

      List<String> lore;
      if (itemCfg != null && itemCfg.contains(statePrefix + "-lore")) {
         lore = itemCfg.getStringList(statePrefix + "-lore");
      } else if (itemCfg != null && itemCfg.contains("lore")) {
         lore = itemCfg.getStringList("lore");
      } else {
         lore = fallbackLore != null ? fallbackLore : List.of();
      }

      name = this.applyPlaceholders(name, placeholders);
      List<Component> loreComponents = new ArrayList<>();

      for (String line : lore) {
         loreComponents.add(this.plugin.getConfigManager().color(this.applyPlaceholders(line, placeholders)).decoration(TextDecoration.ITALIC, false));
      }

      boolean glow = itemCfg != null && itemCfg.getBoolean(statePrefix + "-glow", itemCfg.getBoolean("glow", false));
      ItemStack item = GuiItem.simple(
         mat, this.plugin.getConfigManager().color(name).decoration(TextDecoration.ITALIC, false), loreComponents.toArray(new Component[0])
      );
      if (glow) {
         ItemMeta meta = item.getItemMeta();
         if (meta != null) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
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
      ConfigurationSection itemCfg = this.itemsCfg != null ? this.itemsCfg.getConfigurationSection("color") : null;
      String currentColor = this.miniColorTag(this.team.getColor());
      ItemStack item = GuiItem.fromConfig(this.getViewer(), itemCfg, "color", currentColor);
      if (!canCustomize) {
         this.appendLoreLine(item, this.plugin.getConfigManager().color("<gray>Your role can't change this."));
      }

      return item;
   }

   private String miniColorTag(ChatColor c) {
      String tag = c.name().toLowerCase();
      return "<" + tag + ">" + c.name() + "</" + tag + ">";
   }

   private void appendLoreLine(ItemStack item, Component line) {
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
         lore.add(line);
         meta.lore(lore);
         item.setItemMeta(meta);
      }
   }

   private ItemStack buildTagChangeItem(ConfigurationSection itemCfg, boolean canCustomize) {
      Material mat = this.parse(itemCfg.getString("material", "NAME_TAG"), Material.NAME_TAG);
      Component name = this.plugin.getConfigManager().color(itemCfg.getString("name", "<aqua>Change Team Tag"));
      List<Component> lore = new ArrayList<>();

      for (String line : itemCfg.getStringList("lore")) {
         lore.add(
            this.plugin
               .getConfigManager()
               .color(
                  line.replace("{tag}", this.team.getTag())
                     .replace("{min_tag_length}", String.valueOf(this.plugin.getConfigManager().getMinTagLength()))
                     .replace("{max_tag_length}", String.valueOf(this.plugin.getConfigManager().getMaxTagLength()))
               )
         );
      }

      if (!this.canChangeTag()) {
         lore.add(this.plugin.getConfigManager().color("<red>Your role can't change this."));
      }

      return GuiItem.simple(mat, name, lore.toArray(new Component[0]));
   }

   @Override
   public void onClick(int slot, ClickType clickType) {
      Player p = this.getViewer();
      ConfigurationSection cfg = this.plugin.getGuiConfig().getConfigurationSection("gui.settings-menu");
      if (slot == this.backButtonSlot) {
         new MainMenuGui(p, this.team).open();
      } else if (slot == this.pvpSlot) {
         this.handlePvpToggle(p, cfg);
      } else if (slot == this.colorSlot) {
         this.handleColorCycle(p, cfg, clickType);
      } else if (slot == this.itemApplySlot) {
         this.handleApplyItem(p, cfg);
      } else if (slot == this.itemClearSlot) {
         this.handleClearItem(p, cfg);
      } else if (slot == this.descriptionSlot) {
         this.handleDescriptionClick(p);
      } else if (slot == this.tagChangeSlot) {
         this.handleTagChange(p);
      }
   }

   private void handlePvpToggle(Player p, ConfigurationSection cfg) {
      if (this.plugin.getConfigManager().isFriendlyFireWithinTeam()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("pvp-locked"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else {
         TeamRole role = this.team.getRole(p.getUniqueId());
         if (role != null && this.plugin.getConfig().getBoolean("roles.permissions." + role.name() + ".can-toggle-pvp", false)) {
            this.team.setPvpEnabled(!this.team.isPvpEnabled());
            this.plugin.getTeamManager().saveTeam(this.team);
            p.sendMessage(this.plugin.getConfigManager().getMessage(this.team.isPvpEnabled() ? "pvp-enabled" : "pvp-disabled"));
            SoundUtil.play(p, this.plugin.getConfigManager().getGuiSuccessSound());
            this.setSlotItem(
               this.getInventory(), this.pvpSlot, this.itemsCfg != null ? this.itemsCfg.getConfigurationSection("pvp-toggle") : null, this.buildPvpItem()
            );
         } else {
            p.sendMessage(this.plugin.getConfigManager().getMessage("not-enough-permission-role"));
            SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
         }
      }
   }

   private void handleColorCycle(Player p, ConfigurationSection cfg, ClickType clickType) {
      if (!this.plugin.getConfigManager().isTeamColorEnabled()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("team-color-disabled"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else if (!this.canCustomize()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("not-enough-permission-role"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else {
         List<String> colors = this.plugin.getConfigManager().getAvailableTeamColors();
         int current = colors.indexOf(this.team.getColor().name());
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
            this.team.setColor(newColor);
            this.plugin.getTeamManager().saveTeam(this.team);
            p.sendMessage(this.plugin.getConfigManager().getMessage("team-color-changed", "color", this.miniColorTag(newColor)));
            SoundUtil.play(p, this.plugin.getConfigManager().getGuiSuccessSound());
            this.setSlotItem(
               this.getInventory(), this.colorSlot, this.itemsCfg != null ? this.itemsCfg.getConfigurationSection("color") : null, this.buildColorItem(true)
            );
         } catch (IllegalArgumentException ex) {
            SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
         }
      }
   }

   private void handleApplyItem(Player p, ConfigurationSection cfg) {
      if (!this.plugin.getConfigManager().isTeamItemEnabled()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("team-item-disabled"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else if (!this.canCustomize()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("not-enough-permission-role"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else {
         ItemStack placed = this.getInventory().getItem(this.itemSlot);
         if (placed != null && !placed.getType().isAir()) {
            ItemStack oldCustomItem = this.team.getCustomItem();
            ItemStack icon = new ItemStack(placed.getType());
            ItemMeta iconMeta = icon.getItemMeta();
            ItemMeta placedMeta = placed.getItemMeta();
            if (iconMeta != null && placedMeta != null && placedMeta.hasCustomModelData()) {
               iconMeta.setCustomModelData(placedMeta.getCustomModelData());
            }

            if (iconMeta != null) {
               icon.setItemMeta(iconMeta);
            }

            this.team.setCustomItem(icon);
            this.plugin.getTeamManager().saveTeam(this.team);
            this.getInventory().setItem(this.itemSlot, null);
            this.returnItemToPlayer(p, placed.clone());
            if (oldCustomItem != null) {
               this.returnItemToPlayer(p, oldCustomItem);
            }

            p.sendMessage(this.plugin.getConfigManager().getMessage("team-item-applied"));
            SoundUtil.play(p, this.plugin.getConfigManager().getGuiSuccessSound());
         } else {
            p.sendMessage(this.plugin.getConfigManager().getMessage("team-item-apply-empty"));
            SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
         }
      }
   }

   private void handleClearItem(Player p, ConfigurationSection cfg) {
      if (!this.canCustomize()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("not-enough-permission-role"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else {
         ItemStack oldCustomItem = this.team.getCustomItem();
         this.team.setCustomItem(null);
         this.plugin.getTeamManager().saveTeam(this.team);
         this.getInventory().setItem(this.itemSlot, null);
         p.sendMessage(this.plugin.getConfigManager().getMessage("team-item-cleared"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiSuccessSound());
      }
   }

   private void handleDescriptionClick(Player p) {
      if (!this.plugin.getConfigManager().isTeamDescriptionEnabled()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("team-description-disabled"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else if (!this.canEditDescription()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("not-enough-permission-role"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else {
         p.closeInventory();
         this.plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.TEAM_DESCRIPTION);
         p.sendMessage(this.plugin.getConfigManager().getMessage("team-description-prompt"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiOpenSound());
      }
   }

   private void handleTagChange(Player p) {
      if (!this.canChangeTag()) {
         p.sendMessage(this.plugin.getConfigManager().getMessage("not-enough-permission-role"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiErrorSound());
      } else {
         p.closeInventory();
         this.plugin.getPlayerListener().awaitInput(p.getUniqueId(), PlayerListener.PendingInputType.TEAM_TAG);
         p.sendMessage(this.plugin.getConfigManager().getMessage("team-tag-prompt"));
         SoundUtil.play(p, this.plugin.getConfigManager().getGuiOpenSound());
      }
   }

   private void returnItemToPlayer(Player p, ItemStack item) {
      HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(new ItemStack[]{item});

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
