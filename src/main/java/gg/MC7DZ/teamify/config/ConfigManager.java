package gg.MC7DZ.teamify.config;

import gg.MC7DZ.teamify.Teamify;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Thin wrapper around the plugin's config.yml giving convenient
 * typed accessors for the most commonly used settings.
 */
public class ConfigManager {

    private final Teamify plugin;

    public ConfigManager(Teamify plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public String getPrefix() {
        return color(getConfig().getString("general.prefix", "&8[&bTeams&8] &r"));
    }

    public String getMessage(String path) {
        String raw = getConfig().getString("messages." + path, "&cMissing message: " + path);
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

    // ---- Bank ----
    public boolean isBankEnabled() { return getConfig().getBoolean("bank.enable-team-bank", true); }
    public double getStartingBalance() { return getConfig().getDouble("bank.starting-balance", 0.0); }
    public double getMaxBalance() { return getConfig().getDouble("bank.max-balance", 1000000.0); }

    // ---- Leveling ----
    public boolean isLevelingEnabled() { return getConfig().getBoolean("leveling.enable-leveling", true); }
    public int getBaseXpRequired() { return getConfig().getInt("leveling.base-xp-required", 1000); }
    public double getXpMultiplier() { return getConfig().getDouble("leveling.xp-multiplier", 1.35); }

    // ---- Disband ----
    public boolean isDisbandConfirmationRequired() { return getConfig().getBoolean("disband.require-confirmation", true); }
    public int getDisbandCooldownSeconds() { return getConfig().getInt("disband.cooldown-after-disband-seconds", 600); }

    // ---- Storage ----
    public String getStorageType() { return getConfig().getString("storage.type", "YAML"); }

    // ---- GUI ----
    public boolean isGuiEnabled() { return getConfig().getBoolean("gui.enabled", true); }
    public String getGuiOpenSound() { return getConfig().getString("gui.open-sound", "UI_BUTTON_CLICK"); }
    public String getGuiSuccessSound() { return getConfig().getString("gui.success-sound", "ENTITY_PLAYER_LEVELUP"); }
    public String getGuiErrorSound() { return getConfig().getString("gui.error-sound", "ENTITY_VILLAGER_NO"); }
}
