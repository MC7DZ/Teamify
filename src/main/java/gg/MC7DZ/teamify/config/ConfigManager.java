package gg.MC7DZ.teamify.config;

import gg.MC7DZ.teamify.Teamify;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper around the plugin's config.yml giving convenient
 * typed accessors for the most commonly used settings.
 */
public class ConfigManager {

    private final Teamify plugin;
    private YamlConfiguration langConfig;
    private String loadedLanguage;

    public ConfigManager(Teamify plugin) {
        this.plugin = plugin;
        loadLanguage();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        loadLanguage();
    }

    public String getPrefix() {
        return color(getConfig().getString("general.prefix", "&8[&bTeams&8] &r"));
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

    public String getLoadedLanguage() {
        return loadedLanguage;
    }

    public String getMessage(String path) {
        String raw = null;
        if (langConfig != null) {
            raw = langConfig.getString("messages." + path);
        }
        if (raw == null) {
            // Fall back to config.yml's own messages section for backwards compatibility.
            raw = getConfig().getString("messages." + path);
        }
        if (raw == null) {
            raw = "&cMissing message: " + path;
        }
        return getPrefix() + color(raw);
    }

    public String getMessage(String path, String... replacements) {
        String msg = getMessage(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }

    public String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    // ---- Team creation ----
    public int getMinNameLength() { return getConfig().getInt("team-creation.min-name-length", 3); }
    public int getMaxNameLength() { return getConfig().getInt("team-creation.max-name-length", 16); }
    public String getNameRegex() { return getConfig().getString("team-creation.name-regex", "^[a-zA-Z0-9_]+$"); }
    public int getCreationCooldownSeconds() { return getConfig().getInt("team-creation.creation-cooldown-seconds", 300); }
    public boolean isBlockDuplicateNames() { return getConfig().getBoolean("team-creation.block-duplicate-names", true); }

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

    // ---- Chat ----
    public boolean isTeamChatEnabled() { return getConfig().getBoolean("chat.enable-team-chat", true); }
    public String getTeamChatFormat() { return getConfig().getString("chat.team-chat-format", "&8[&bTeam&8] &7{role} &f{player}&8: &f{message}"); }

    // ---- Relations ----
    public boolean isAlliesEnabled() { return getConfig().getBoolean("relations.enable-allies", true); }
    public boolean isEnemiesEnabled() { return getConfig().getBoolean("relations.enable-enemies", true); }
    public boolean isMutualAllianceRequired() { return getConfig().getBoolean("relations.mutual-alliance-required", true); }
    public boolean isFriendlyFireWithinTeam() { return getConfig().getBoolean("relations.friendly-fire.within-team", true); }
    public boolean isAllyChatEnabled() { return getConfig().getBoolean("relations.enable-ally-chat", true); }
    public int getAllyInviteExpireSeconds() { return getConfig().getInt("relations.ally-invite-expire-seconds", 60); }
    public String getAllyChatFormat() { return getConfig().getString("relations.ally-chat-format", "&8[&aAlly&8] &7{team} &7{role} &f{player}&8: &f{message}"); }

    // ---- Visibility (invisible members/allies) ----
    // These are global, server-enforced settings (per-team toggling is not supported).
    public boolean isSeeMembersWhenInvis() { return getConfig().getBoolean("visibility.see-members-when-invis.default", true); }
    public boolean isSeeAlliesWhenInvis() { return getConfig().getBoolean("visibility.see-allies-when-invis.default", true); }

    // ---- Bank ----
    public boolean isBankEnabled() { return getConfig().getBoolean("bank.enable-team-bank", true); }
    public double getStartingBalance() { return getConfig().getDouble("bank.starting-balance", 0.0); }
    public double getMaxBalance() { return getConfig().getDouble("bank.max-balance", 1000000.0); }

    // ---- Team customization (color & custom item) ----
    public boolean isTeamColorEnabled() { return getConfig().getBoolean("team-customization.enable-color", true); }
    public boolean isTeamItemEnabled() { return getConfig().getBoolean("team-customization.enable-custom-item", true); }
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

    // ---- Storage ----
    public String getStorageType() { return getConfig().getString("storage.type", "YAML"); }

    // ---- GUI ----
    public boolean isGuiEnabled() { return getConfig().getBoolean("gui.enabled", true); }
    // These are modern Minecraft sound-event keys (e.g. "ui.button.click"),
    // resolved via the Registry-based SoundUtil - not old Sound enum names.
    public String getGuiOpenSound() { return getConfig().getString("gui.open-sound", "ui.button.click"); }
    public String getGuiSuccessSound() { return getConfig().getString("gui.success-sound", "entity.player.levelup"); }
    public String getGuiErrorSound() { return getConfig().getString("gui.error-sound", "entity.villager.no"); }
}
