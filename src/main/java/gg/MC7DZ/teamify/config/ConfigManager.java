package gg.MC7DZ.teamify.config;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.gui.GuiHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Thin wrapper around the plugin's config.yml giving convenient
 * typed accessors for the most commonly used settings.
 */
public class ConfigManager {

    private final Teamify plugin;
    private YamlConfiguration langConfig;
    private YamlConfiguration guiConfig;
    private String loadedLanguage;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ConfigManager(Teamify plugin) {
        this.plugin = plugin;
        loadLanguage();
        loadGuiConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        loadLanguage();
        loadGuiConfig();
        // GUI classes read from Teamify#getGuiConfig(), which is a separate
        // FileConfiguration instance from the one loaded above - reload it
        // too, or edits to gui.yml would never show up until a server restart.
        plugin.reloadGuiConfig();

        // After reloading configs, refresh all currently open GUIs
        for (GuiHolder gui : GuiHolder.getActiveGuis()) {
            gui.rebuild();
        }
    }

    public Component getPrefix() {
        return miniMessage.deserialize(getConfig().getString("general.prefix", "<#55FFFF><bold>TEAMIFY</bold> <dark_gray>»</dark_gray> <reset>"));
    }

    /**
     * Loads (or reloads) the messages language file from the /languages folder,
     * based on the "general.language" config option (e.g. "en", "ar").
     * Falls back to bundling the "en" file if the configured one doesn't exist.
     */
    private void loadLanguage() {
        String language = getConfig().getString("general.language", "en");
        File languagesFolder = new File(plugin.getDataFolder(), "languages");
        if (!languagesFolder.exists()) languagesFolder.mkdirs();

        // Copy bundled language files on first run so admins can see/edit them.
        for (String lang : new String[]{"en", "ar"}) {
            File langFile = new File(languagesFolder, lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream in = plugin.getResource("languages/" + lang + ".yml")) {
                    if (in != null) {
                        plugin.saveResource("languages/" + lang + ".yml", false);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to save default language file " + lang + ".yml: " + ex.getMessage());
                }
            }
        }

        File selected = new File(languagesFolder, language + ".yml");
        if (!selected.exists()) {
            plugin.getLogger().warning("Language '" + language + "' not found in /languages, defaulting to 'en'.");
            language = "en";
            selected = new File(languagesFolder, "en.yml");
        }

        if (selected.exists()) {
            this.langConfig = YamlConfiguration.loadConfiguration(selected);
            // Merge defaults from the jar in case the on-disk file is missing new keys.
            try (InputStream defStream = plugin.getResource("languages/" + language + ".yml")) {
                if (defStream != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(defStream, StandardCharsets.UTF_8));
                    this.langConfig.setDefaults(defaults);
                }
            } catch (Exception ignored) {
            }
        } else {
            this.langConfig = null;
        }
        this.loadedLanguage = language;
    }

    private void loadGuiConfig() {
        File guiFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        try (InputStream defStream = plugin.getResource("gui.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));
                this.guiConfig.setDefaults(defaults);
            }
        } catch (Exception ignored) {
            plugin.getLogger().warning("Failed to load default gui.yml: " + ignored.getMessage());
        }
    }

    public String getLoadedLanguage() {
        return loadedLanguage;
    }

    public Component getMessage(String path) {
        String raw = null;
        if (langConfig != null) {
            raw = langConfig.getString("messages." + path);
        }
        if (raw == null) {
            raw = "<red>Missing message: " + path;
        }
        return getPrefix().append(color(raw));
    }

    public Component getMessage(String path, String... replacements) {
        String msg = null;
        if (langConfig != null) {
            msg = langConfig.getString("messages." + path);
        }
        if (msg == null) {
            msg = "<red>Missing message: " + path;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return getPrefix().append(color(msg));
    }

    /**
     * Converts a single legacy formatting char (the char that follows '&' or '§')
     * into its MiniMessage tag equivalent, or null if it isn't a recognised code.
     */
    private static String legacyCharToTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    /**
     * Rewrites any legacy '&' or '§' colour codes in the input into their
     * equivalent MiniMessage tags, so the whole string can be handed to a
     * single MiniMessage instance without it throwing on mixed legacy/MiniMessage
     * syntax. Anything that isn't a recognised legacy code (including a lone
     * trailing '&' or '§') is left untouched.
     */
    private String legacyToMiniMessage(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < input.length()) {
                String tag = legacyCharToTag(input.charAt(i + 1));
                if (tag != null) {
                    sb.append(tag);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public Component color(String s) {
        if (s == null) return Component.empty();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Attempting to color string: '" + s + "'");
        }

        // First, try to deserialize directly as MiniMessage
        try {
            Component result = miniMessage.deserialize(s);
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("MiniMessage direct deserialize result: '" + LegacyComponentSerializer.legacyAmpersand().serialize(result) + "'");
            }
            return result;
        } catch (Exception ex) {
            // If direct deserialization fails, log and try with legacy conversion
            plugin.getLogger().warning("Failed to parse MiniMessage directly for '" + s + "': " + ex.getMessage());
        }

        // Fallback to legacy conversion and then MiniMessage deserialization
        String normalized = legacyToMiniMessage(s);
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("After legacyToMiniMessage conversion: '" + normalized + "'");
        }
        try {
            Component result = miniMessage.deserialize(normalized);
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("MiniMessage after legacy conversion result: '" + LegacyComponentSerializer.legacyAmpersand().serialize(result) + "'");
            }
            return result;
        } catch (Exception ex) {
            // Last resort: something in the string still isn't valid MiniMessage
            // (e.g. a stray '<'/'>' from a player-chosen name or team tag).
            // Never let a bad string take down a listener - fall back to treating
            // it as plain legacy text instead of crashing the GUI.
            plugin.getLogger().warning("Failed to parse colored text after legacy conversion '" + s + "', falling back to legacy parsing: " + ex.getMessage());
            Component result = LegacyComponentSerializer.legacyAmpersand().deserialize(normalized.replace('\u00A7', '&'));
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Legacy fallback result: '" + LegacyComponentSerializer.legacyAmpersand().serialize(result) + "'");
            }
            return result;
        }
    }

    // ---- General ----
    public boolean isListCommandEnabled() { return getConfig().getBoolean("general.enable-list-command", true); }
    public boolean isPlayersListEnabled() { return getConfig().getBoolean("general.enable-players-list", false); }
    public boolean isPlayerSettingsEnabled() { return getConfig().getBoolean("general.enable-player-settings", true); }
    public boolean isDebug() { return getConfig().getBoolean("general.debug", false); }
    public boolean isUpdateCheckEnabled() { return getConfig().getBoolean("update-checker.enabled", true); }
    public boolean isUpdateCheckNotifyOps() { return getConfig().getBoolean("update-checker.notify-ops-on-join", true); }
    public String getUpdateCheckModrinthId() { return getConfig().getString("update-checker.modrinth-id", ""); }
    public boolean isColoredNamesEnabled() { return getConfig().getBoolean("general.colored-names", true); }
    public ChatColor getTeammateColor() { return parseChatColor(getConfig().getString("general.teammate-color", "<green>"), ChatColor.GREEN); }
    public ChatColor getAlliesColor() { return parseChatColor(getConfig().getString("general.allies-color", "<blue>"), ChatColor.BLUE); }
    public ChatColor getEnemysColor() { return parseChatColor(getConfig().getString("general.enemys-color", "<white>"), ChatColor.WHITE); }

    /** Which of CHAT / TAB / NAMETAG the relation colors above should be applied to. */
    public enum ColorShow { CHAT, TAB, NAMETAG }

    public java.util.EnumSet<ColorShow> getColorShows() {
        java.util.List<String> raw = getConfig().getStringList("general.color-shows");
        if (raw.isEmpty()) raw = java.util.List.of("NAMETAG");
        java.util.EnumSet<ColorShow> shows = java.util.EnumSet.noneOf(ColorShow.class);
        for (String entry : raw) {
            try {
                shows.add(ColorShow.valueOf(entry.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Unknown value in color-shows - skip it rather than error out.
            }
        }
        return shows;
    }

    public boolean isColorShown(ColorShow show) { return getColorShows().contains(show); }

    private ChatColor parseChatColor(String name, ChatColor fallback) {
        if (name == null) return fallback;
        // Try to parse as a direct ChatColor name first
        try {
            return ChatColor.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // Not a direct ChatColor name, try to extract from MiniMessage
        }

        // Attempt to extract a NamedTextColor from MiniMessage and map to ChatColor
        Component component = miniMessage.deserialize(name);
        TextColor textColor = component.color();
        if (textColor != null && textColor instanceof NamedTextColor) {
            NamedTextColor namedTextColor = (NamedTextColor) textColor;
            // Map NamedTextColor to ChatColor
            if (namedTextColor.equals(NamedTextColor.BLACK)) return ChatColor.BLACK;
            if (namedTextColor.equals(NamedTextColor.DARK_BLUE)) return ChatColor.DARK_BLUE;
            if (namedTextColor.equals(NamedTextColor.DARK_GREEN)) return ChatColor.DARK_GREEN;
            if (namedTextColor.equals(NamedTextColor.DARK_AQUA)) return ChatColor.DARK_AQUA;
            if (namedTextColor.equals(NamedTextColor.DARK_RED)) return ChatColor.DARK_RED;
            if (namedTextColor.equals(NamedTextColor.DARK_PURPLE)) return ChatColor.DARK_PURPLE;
            if (namedTextColor.equals(NamedTextColor.GOLD)) return ChatColor.GOLD;
            if (namedTextColor.equals(NamedTextColor.GRAY)) return ChatColor.GRAY;
            if (namedTextColor.equals(NamedTextColor.DARK_GRAY)) return ChatColor.DARK_GRAY;
            if (namedTextColor.equals(NamedTextColor.BLUE)) return ChatColor.BLUE;
            if (namedTextColor.equals(NamedTextColor.GREEN)) return ChatColor.GREEN;
            if (namedTextColor.equals(NamedTextColor.AQUA)) return ChatColor.AQUA;
            if (namedTextColor.equals(NamedTextColor.RED)) return ChatColor.RED;
            if (namedTextColor.equals(NamedTextColor.LIGHT_PURPLE)) return ChatColor.LIGHT_PURPLE;
            if (namedTextColor.equals(NamedTextColor.YELLOW)) return ChatColor.YELLOW;
            if (namedTextColor.equals(NamedTextColor.WHITE)) return ChatColor.WHITE;
        }
        return fallback;
    }

    // ---- Team creation ----
    public int getMinNameLength() { return getConfig().getInt("team-creation.min-name-length", 3); }
    public int getMaxNameLength() { return getConfig().getInt("team-creation.max-name-length", 16); }
    public int getMinTagLength() { return getConfig().getInt("team-creation.min-tag-length", 2); }
    public int getMaxTagLength() { return getConfig().getInt("team-creation.max-tag-length", 5); }
    public boolean isAllowColorCodes() { return getConfig().getBoolean("team-creation.allow-color-codes", true); }
    public String getNameRegex() { return getConfig().getString("team-creation.name-regex", "^[a-zA-Z0-9_]+$"); }
    public int getCreationCooldownSeconds() { return getConfig().getInt("team-creation.creation-cooldown-seconds", 300); }
    public boolean isBlockDuplicateNames() { return getConfig().getBoolean("team-creation.block-duplicate-names", true); }
    public double getCreationCost() { return getConfig().getDouble("team-creation.creation-cost", 0.0); }

    // ---- Team limits ----
    public int getMaxMembers() { return getConfig().getInt("team-limits.max-members", 20); }
    public int getMaxHomes() { return getConfig().getInt("team-limits.max-homes", 1); }
    public int getMaxAllies() { return getConfig().getInt("team-limits.max-allies", 5); }
    public int getMaxEnemies() { return getConfig().getInt("team-limits.max-enemies", 10); }
    public int getMaxDescriptionLength() { return getConfig().getInt("team-limits.max-description-length", 100); }

    // ---- Home ----
    public boolean isHomeCommandEnabled() { return getConfig().getBoolean("home.enable-home", false); }
    public boolean isSetHomeCommandEnabled() { return getConfig().getBoolean("home.enable-sethome", false); }
    public int getHomeTeleportDelay() { return getConfig().getInt("home.teleport-delay-seconds", 3); }
    public boolean isCancelOnMove() { return getConfig().getBoolean("home.cancel-on-move", true); }
    public int getHomeCooldownSeconds() { return getConfig().getInt("home.cooldown-seconds", 30); }
    public double getTeleportCost() { return getConfig().getDouble("home.teleport-cost", 0.0); }

    // ---- Chat ----
    public boolean isTeamChatEnabled() { return getConfig().getBoolean("chat.enable-team-chat", true); }
    public String getTeamChatFormat() { return getConfig().getString("chat.team-chat-format", "<dark_gray>[<#55FFFF>Team<dark_gray>] <gray>{role} <white>{player}<dark_gray>: <white>{message}"); }

    // ---- Join requests ----
    public boolean isSendJoinRequestEnabled() { return getConfig().getBoolean("join-requests.send-join-request", true); }

    // ---- Relations ----
    public boolean isAlliesEnabled() { return getConfig().getBoolean("relations.enable-allies", true); }
    public boolean isEnemiesEnabled() { return getConfig().getBoolean("relations.enable-enemies", true); }
    public boolean isMutualAllianceRequired() { return getConfig().getBoolean("relations.mutual-alliance-required", true); }
    public boolean isFriendlyFireWithinTeam() { return getConfig().getBoolean("relations.friendly-fire.within-team", true); }
    public boolean isAllyChatEnabled() { return getConfig().getBoolean("relations.enable-ally-chat", true); }
    public int getAllyInviteExpireSeconds() { return getConfig().getInt("relations.ally-invite-expire-seconds", 60); }
    public String getAllyChatFormat() { return getConfig().getString("relations.ally-chat-format", "<dark_gray>[<green>Ally<dark_gray>] <gray>{team} <gray>{role} <white>{player}<dark_gray>: <white>{message}"); }

    // ---- Visibility (invisible members/allies) ----
    // These are global, server-enforced settings (per-team toggling is not supported).
    public boolean isSeeMembersWhenInvis() { return getConfig().getBoolean("visibility.see-members-when-invis.default", false); }
    public boolean isHideNamesWhenInvisible() { return getConfig().getBoolean("visibility.hide-names-when-invisible", false); }

    // ---- Bank ----
    public boolean isBankEnabled() { return getConfig().getBoolean("bank.enable-team-bank", true); }

    // ---- Team Enderchest ----
    public boolean isEchestEnabled() { return getConfig().getBoolean("echest.enable-echest", false); }
    /** Number of usable slots in the shared team echest, clamped to a single 54-slot inventory. */
    public int getEchestSlots() {
        int slots = getConfig().getInt("echest.slots", 54);
        return Math.max(1, Math.min(54, slots));
    }
    public String getEchestFillerItem() { return getConfig().getString("echest.filler-item", "GRAY_STAINED_GLASS_PANE"); }
    public double getStartingBalance() { return getConfig().getDouble("bank.starting-balance", 0.0); }
    public double getMaxBalance() { return getConfig().getDouble("bank.max-balance", 1000000.0); }

    // ---- Integrations ----
    public boolean isPlaceholderApiEnabled() { return getConfig().getBoolean("integrations.placeholderapi.enabled", true); }

    // ---- Kills ----
    public boolean isCountTeamKillsEnabled() { return getConfig().getBoolean("kills.count-team-kills", false); }

    // ---- Team customization (color & custom item) ----
    public boolean isTeamColorEnabled() { return getConfig().getBoolean("team-customization.enable-color", true); }
    public boolean isTeamItemEnabled() { return getConfig().getBoolean("team-customization.enable-custom-item", true); }
    public boolean isTeamDescriptionEnabled() { return getConfig().getBoolean("team-customization.enable-description", true); }
    public boolean isShowDescriptionInList() { return getConfig().getBoolean("team-customization.show-description-in-list", true); }
    public String getDefaultTeamColorName() { return getConfig().getString("team-customization.default-color", "WHITE"); }
    public java.util.List<String> getAvailableTeamColors() {
        java.util.List<String> colors = getConfig().getStringList("team-customization.available-colors");
        return colors.isEmpty() ? java.util.List.of("WHITE") : colors;
    }

    // ---- Leveling ----
    public boolean isLevelingEnabled() { return getConfig().getBoolean("leveling.enable-leveling", true); }
    public int getBaseXpRequired() { return getConfig().getInt("leveling.base-xp-required", 1000); }
    public double getXpMultiplier() { return getConfig().getDouble("leveling.xp-multiplier", 1.35); }

    // ---- Disband ----
    public boolean isDisbandConfirmationRequired() { return getConfig().getBoolean("disband.require-confirmation", true); }
    public boolean isTransferConfirmationRequired() { return getConfig().getBoolean("transfer.require-confirmation", true); }
    public int getDisbandCooldownSeconds() { return getConfig().getInt("disband.cooldown-after-disband-seconds", 600); }
    public int getRefundPercentOnDisband() { return getConfig().getInt("disband.refund-percent-on-disband", 50); }

    // ---- Storage ----
    public String getStorageType() { return getConfig().getString("storage.type", "YAML"); }

    // ---- GUI ----
    public boolean isGuiEnabled() { return guiConfig.getBoolean("gui.enabled", true); }
    // These are modern Minecraft sound-event keys (e.g. "ui.button.click"),
    // resolved via the Registry-based SoundUtil - not old Sound enum names.
    public String getGuiOpenSound() { return guiConfig.getString("gui.open-sound", "ui.button.click"); }
    public String getGuiSuccessSound() { return guiConfig.getString("gui.success-sound", "entity.player.levelup"); }
    public String getGuiErrorSound() { return guiConfig.getString("gui.error-error", "entity.villager.no"); }
}