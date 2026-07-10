package gg.MC7DZ.teamify.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import gg.MC7DZ.teamify.Teamify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

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
 * <p>
 * Uses Paper/Adventure's Component-based ItemMeta#displayName(Component) and
 * ItemMeta#lore(List&lt;Component&gt;) instead of the deprecated legacy
 * String-based setDisplayName/setLore. '&' color codes from config.yml are
 * translated into Components via LegacyComponentSerializer, and italics is
 * explicitly disabled to match legacy default rendering (Adventure defaults
 * lore/names to italic otherwise).
 */
public final class GuiItem {

    private GuiItem() {}

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** Converts a legacy '&'-coded string into a non-italic Component. */
    private static Component toComponent(String legacyText) {
        return LEGACY.deserialize(legacyText == null ? "" : legacyText)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    private static List<Component> toComponentLore(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(toComponent(line));
        }
        return out;
    }

    public static ItemStack fromConfig(Player viewer, ConfigurationSection section, String... placeholders) {
        if (section == null) {
            return new ItemStack(Material.BARRIER);
        }
        Material mat;
        try {
            mat = Material.valueOf(section.getString("material", "STONE").toUpperCase());
        } catch (IllegalArgumentException ex) {
            Teamify.getInstance().getLogger().warning("Invalid material '" + section.getString("material") + "' in GUI config. Defaulting to STONE.");
            mat = Material.STONE;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name", "&fItem");
            name = applyPlaceholders(name, placeholders);
            meta.displayName(toComponent(name));

            List<String> lore = section.getStringList("lore");
            List<String> processed = new ArrayList<>();
            for (String line : lore) {
                processed.add(applyPlaceholders(line, placeholders));
            }
            meta.lore(toComponentLore(processed));

            if (section.contains("custom-model-data")) {
                meta.setCustomModelData(section.getInt("custom-model-data"));
            }

            // Handle mirror-skin-head for PLAYER_HEAD
            if (mat == Material.PLAYER_HEAD && meta instanceof SkullMeta) {
                if (section.getBoolean("mirror-skin-head", false)) {
                    ((SkullMeta) meta).setOwningPlayer(viewer);
                } else {
                    // Optional "texture" (base64 skin data, same format as back-button)
                    String texture = section.getString("texture");
                    if (texture != null && !texture.isEmpty()) {
                        applyTexture((SkullMeta) meta, texture);
                    }
                }
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
            meta.displayName(toComponent(name));
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>(List.of(lore));
                meta.lore(toComponentLore(lines));
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
     * Creates a player head with a custom texture.
     * @param base64Texture The base64 encoded JSON string containing the texture URL.
     * @param name The display name of the item.
     * @param lore The lore lines of the item.
     * @return An ItemStack representing the custom player head.
     */
    public static ItemStack playerHead(String base64Texture, String name, String... lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        applyTexture(meta, base64Texture);

        // Apply name and lore
        meta.displayName(toComponent(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>(List.of(lore));
            meta.lore(toComponentLore(lines));
        }
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Applies a base64-encoded skin texture (same format used by the
     * back-button config, i.e. a base64 blob decoding to
     * {"textures":{"SKIN":{"url":"..."}}}) to a player head's SkullMeta.
     */
    private static void applyTexture(SkullMeta meta, String base64Texture) {
        if (base64Texture == null || base64Texture.trim().isEmpty()) {
            Teamify.getInstance().getLogger().warning("Attempted to apply empty or null texture to player head.");
            return;
        }

        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID()); // Random UUID
        PlayerTextures textures = profile.getTextures();
        String decodedString = null;

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Texture.trim());
            decodedString = new String(decodedBytes, StandardCharsets.UTF_8);

            if (Teamify.getInstance().getConfigManager().isDebug()) {
                Teamify.getInstance().getLogger().fine("Decoded texture string: " + decodedString);
            }

            JsonObject json = JsonParser.parseString(decodedString).getAsJsonObject();

            if (!json.has("textures") || !json.getAsJsonObject("textures").has("SKIN") || !json.getAsJsonObject("textures").getAsJsonObject("SKIN").has("url")) {
                Teamify.getInstance().getLogger().warning("Texture JSON is missing 'textures.SKIN.url' field. Raw JSON: " + decodedString);
                return;
            }

            String textureUrl = json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            textures.setSkin(new URL(textureUrl));
        } catch (IllegalArgumentException e) {
            Teamify.getInstance().getLogger().warning("Invalid Base64 string for player head texture: " + base64Texture + " - " + e.getMessage());
            return;
        } catch (JsonSyntaxException e) {
            Teamify.getInstance().getLogger().warning("Invalid JSON syntax for player head texture. Raw string: " + decodedString + " - " + e.getMessage());
            return;
        } catch (MalformedURLException e) {
            Teamify.getInstance().getLogger().warning("Malformed URL for player head texture. URL: " + (decodedString != null ? decodedString : "N/A") + " - " + e.getMessage());
            return;
        } catch (Exception e) {
            Teamify.getInstance().getLogger().log(Level.WARNING, "Error decoding or parsing player head texture. Raw string: " + decodedString + " - " + e.getMessage(), e);
            return;
        }

        profile.setTextures(textures);
        meta.setPlayerProfile(profile);
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
            meta.displayName(toComponent(name));
            meta.lore(toComponentLore(lore));
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