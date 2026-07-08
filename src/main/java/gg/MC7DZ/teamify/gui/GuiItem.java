package gg.MC7DZ.teamify.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds ItemStacks from config sections, e.g.:
 * item:
 *   slot: 10
 *   material: BOOK
 *   name: "&bHello"
 *   lore: ["line1", "line2"]
 *   # Optional - lets a resource pack swap the texture per-item. Not read
 *   # by every GUI item builder (only where the calling code passes it
 *   # through), but supported here as the shared building block:
 *   custom-model-data: 1001
 *   # Optional - adds an enchant glint without showing an enchantment.
 *   glow: false
 */
public final class GuiItem {

    private GuiItem() {}

    public static ItemStack fromConfig(ConfigurationSection section, String... placeholders) {
        if (section == null) {
            return new ItemStack(Material.BARRIER);
        }
        Material mat;
        try {
            mat = Material.valueOf(section.getString("material", "STONE").toUpperCase());
        } catch (IllegalArgumentException ex) {
            mat = Material.STONE;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name", "&fItem");
            name = applyPlaceholders(name, placeholders);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = section.getStringList("lore");
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(ChatColor.translateAlternateColorCodes('&', applyPlaceholders(line, placeholders)));
            }
            meta.setLore(colored);

            if (section.contains("custom-model-data")) {
                meta.setCustomModelData(section.getInt("custom-model-data"));
            }

            item.setItemMeta(meta);

            if (section.getBoolean("glow", false)) {
                applyGlow(item);
            }
        }
        return item;
    }

    public static ItemStack simple(Material material, String name, String... lore) {
        return simple(material, name, false, null, lore);
    }

    /**
     * Same as {@link #simple(Material, String, String...)} but with optional
     * glow (enchant glint, no visible enchantment) and custom model data
     * (for resource-pack texture overrides).
     */
    public static ItemStack simple(Material material, String name, boolean glow, Integer customModelData, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore.length > 0) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(colored);
            }
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            item.setItemMeta(meta);
        }
        if (glow) {
            applyGlow(item);
        }
        return item;
    }

    /**
     * Clones an existing item (e.g. a team's custom icon) and overrides its
     * display name/lore, keeping the material/custom-model-data intact.
     * Used to show a team's custom item in menus with contextual lore.
     */
    public static ItemStack withOverrides(ItemStack base, String name, List<String> lore) {
        ItemStack item = base.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(colored);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Adds an enchant glint to an item without showing any enchantment in the lore. */
    private static void applyGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private static String applyPlaceholders(String input, String... placeholders) {
        String result = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return result;
    }
}
