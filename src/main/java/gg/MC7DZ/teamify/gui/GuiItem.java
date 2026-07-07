package gg.MC7DZ.teamify.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack simple(Material material, String name, String... lore) {
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
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String applyPlaceholders(String input, String... placeholders) {
        String result = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return result;
    }
}
