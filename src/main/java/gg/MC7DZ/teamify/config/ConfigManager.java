package gg.MC7DZ.teamify.config;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.gui.GuiHolder;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigManager {
   private final Teamify plugin;
   private YamlConfiguration langConfig;
   private YamlConfiguration guiConfig;
   private String loadedLanguage;
   private final MiniMessage miniMessage = MiniMessage.miniMessage();

   public ConfigManager(Teamify plugin) {
      this.plugin = plugin;
      this.loadLanguage();
      this.loadGuiConfig();
   }

   public FileConfiguration getConfig() {
      return this.plugin.getConfig();
   }

   public void reload() {
      this.plugin.reloadConfig();
      this.loadLanguage();
      this.loadGuiConfig();
      this.plugin.reloadGuiConfig();

      for (GuiHolder gui : GuiHolder.getActiveGuis()) {
         gui.rebuild();
      }
   }

   public Component getPrefix() {
      return this.miniMessage.deserialize(this.getConfig().getString("general.prefix", "<#55FFFF><bold>TEAMIFY</bold> <dark_gray>»</dark_gray> <reset>"));
   }

   private void loadLanguage() {
      String language = this.getConfig().getString("general.language", "en");
      File languagesFolder = new File(this.plugin.getDataFolder(), "languages");
      if (!languagesFolder.exists()) {
         languagesFolder.mkdirs();
      }

      for (String lang : new String[]{"en", "ar"}) {
         File langFile = new File(languagesFolder, lang + ".yml");
         if (!langFile.exists()) {
            try (InputStream in = this.plugin.getResource("languages/" + lang + ".yml")) {
               if (in != null) {
                  this.plugin.saveResource("languages/" + lang + ".yml", false);
               }
            } catch (Exception ex) {
               this.plugin.getLogger().warning("Failed to save default language file " + lang + ".yml: " + ex.getMessage());
            }
         }
      }

      File selected = new File(languagesFolder, language + ".yml");
      if (!selected.exists()) {
         this.plugin.getLogger().warning("Language '" + language + "' not found in /languages, defaulting to 'en'.");
         language = "en";
         selected = new File(languagesFolder, "en.yml");
      }

      if (selected.exists()) {
         this.langConfig = YamlConfiguration.loadConfiguration(selected);

         try (InputStream defStream = this.plugin.getResource("languages/" + language + ".yml")) {
            if (defStream != null) {
               YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
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
      File guiFile = new File(this.plugin.getDataFolder(), "gui.yml");
      if (!guiFile.exists()) {
         this.plugin.saveResource("gui.yml", false);
      }

      this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);

      try (InputStream defStream = this.plugin.getResource("gui.yml")) {
         if (defStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            this.guiConfig.setDefaults(defaults);
         }
      } catch (Exception ignored) {
         this.plugin.getLogger().warning("Failed to load default gui.yml: " + ignored.getMessage());
      }
   }

   public String getLoadedLanguage() {
      return this.loadedLanguage;
   }

   public Component getMessage(String path) {
      String raw = null;
      if (this.langConfig != null) {
         raw = this.langConfig.getString("messages." + path);
      }

      if (raw == null) {
         raw = "<red>Missing message: " + path;
      }

      return this.getPrefix().append(this.color(raw));
   }

   public Component getMessage(String path, String... replacements) {
      String msg = null;
      if (this.langConfig != null) {
         msg = this.langConfig.getString("messages." + path);
      }

      if (msg == null) {
         msg = "<red>Missing message: " + path;
      }

      for (int i = 0; i + 1 < replacements.length; i += 2) {
         msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
      }

      return this.getPrefix().append(this.color(msg));
   }

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
         default -> null;
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
      };
   }

   private String legacyToMiniMessage(String input) {
      StringBuilder sb = new StringBuilder(input.length() + 16);

      for (int i = 0; i < input.length(); i++) {
         char c = input.charAt(i);
         if ((c == '&' || c == 167) && i + 1 < input.length()) {
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
      if (s == null) {
         return Component.empty();
      }

      if (this.plugin.getConfigManager().isDebug()) {
         this.plugin.getLogger().info("Attempting to color string: '" + s + "'");
      }

      try {
         Component result = this.miniMessage.deserialize(s);
         if (this.plugin.getConfigManager().isDebug()) {
            this.plugin.getLogger().info("MiniMessage direct deserialize result: '" + LegacyComponentSerializer.legacyAmpersand().serialize(result) + "'");
         }

         return result;
      } catch (Exception ex) {
         this.plugin.getLogger().warning("Failed to parse MiniMessage directly for '" + s + "': " + ex.getMessage());
         String normalized = this.legacyToMiniMessage(s);
         if (this.plugin.getConfigManager().isDebug()) {
            this.plugin.getLogger().info("After legacyToMiniMessage conversion: '" + normalized + "'");
         }

         try {
            Component result = this.miniMessage.deserialize(normalized);
            if (this.plugin.getConfigManager().isDebug()) {
               this.plugin
                  .getLogger()
                  .info("MiniMessage after legacy conversion result: '" + LegacyComponentSerializer.legacyAmpersand().serialize(result) + "'");
            }

            return result;
         } catch (Exception exx) {
            this.plugin
               .getLogger()
               .warning("Failed to parse colored text after legacy conversion '" + s + "', falling back to legacy parsing: " + exx.getMessage());
            Component result = LegacyComponentSerializer.legacyAmpersand().deserialize(normalized.replace('§', '&'));
            if (this.plugin.getConfigManager().isDebug()) {
               this.plugin.getLogger().info("Legacy fallback result: '" + LegacyComponentSerializer.legacyAmpersand().serialize(result) + "'");
            }

            return result;
         }
      }
   }

   public boolean isListCommandEnabled() {
      return this.getConfig().getBoolean("general.enable-list-command", true);
   }

   public boolean isPlayersListEnabled() {
      return this.getConfig().getBoolean("general.enable-players-list", false);
   }

   public boolean isPlayerSettingsEnabled() {
      return this.getConfig().getBoolean("general.enable-player-settings", true);
   }

   public boolean isDebug() {
      return this.getConfig().getBoolean("general.debug", false);
   }

   public boolean isUpdateCheckEnabled() {
      return this.getConfig().getBoolean("update-checker.enabled", true);
   }

   public boolean isUpdateCheckNotifyOps() {
      return this.getConfig().getBoolean("update-checker.notify-ops-on-join", true);
   }

   public String getUpdateCheckModrinthId() {
      return this.getConfig().getString("update-checker.modrinth-id", "");
   }

   public boolean isColoredNamesEnabled() {
      return this.getConfig().getBoolean("general.colored-names", true);
   }

   public ChatColor getTeammateColor() {
      return this.parseChatColor(this.getConfig().getString("general.teammate-color", "<green>"), ChatColor.GREEN);
   }

   public ChatColor getAlliesColor() {
      return this.parseChatColor(this.getConfig().getString("general.allies-color", "<blue>"), ChatColor.BLUE);
   }

   public ChatColor getEnemysColor() {
      return this.parseChatColor(this.getConfig().getString("general.enemys-color", "<white>"), ChatColor.WHITE);
   }

   public EnumSet<ConfigManager.ColorShow> getColorShows() {
      List<String> raw = this.getConfig().getStringList("general.color-shows");
      if (raw.isEmpty()) {
         raw = List.of("NAMETAG");
      }

      EnumSet<ConfigManager.ColorShow> shows = EnumSet.noneOf(ConfigManager.ColorShow.class);

      for (String entry : raw) {
         try {
            shows.add(ConfigManager.ColorShow.valueOf(entry.trim().toUpperCase()));
         } catch (IllegalArgumentException ignored) {
         }
      }

      return shows;
   }

   public boolean isColorShown(ConfigManager.ColorShow show) {
      return this.getColorShows().contains(show);
   }

   private ChatColor parseChatColor(String name, ChatColor fallback) {
      if (name == null) {
         return fallback;
      }

      try {
         return ChatColor.valueOf(name.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
         Component component = this.miniMessage.deserialize(name);
         TextColor textColor = component.color();
         if (textColor != null && textColor instanceof NamedTextColor namedTextColor) {
            if (namedTextColor.equals(NamedTextColor.BLACK)) {
               return ChatColor.BLACK;
            }

            if (namedTextColor.equals(NamedTextColor.DARK_BLUE)) {
               return ChatColor.DARK_BLUE;
            }

            if (namedTextColor.equals(NamedTextColor.DARK_GREEN)) {
               return ChatColor.DARK_GREEN;
            }

            if (namedTextColor.equals(NamedTextColor.DARK_AQUA)) {
               return ChatColor.DARK_AQUA;
            }

            if (namedTextColor.equals(NamedTextColor.DARK_RED)) {
               return ChatColor.DARK_RED;
            }

            if (namedTextColor.equals(NamedTextColor.DARK_PURPLE)) {
               return ChatColor.DARK_PURPLE;
            }

            if (namedTextColor.equals(NamedTextColor.GOLD)) {
               return ChatColor.GOLD;
            }

            if (namedTextColor.equals(NamedTextColor.GRAY)) {
               return ChatColor.GRAY;
            }

            if (namedTextColor.equals(NamedTextColor.DARK_GRAY)) {
               return ChatColor.DARK_GRAY;
            }

            if (namedTextColor.equals(NamedTextColor.BLUE)) {
               return ChatColor.BLUE;
            }

            if (namedTextColor.equals(NamedTextColor.GREEN)) {
               return ChatColor.GREEN;
            }

            if (namedTextColor.equals(NamedTextColor.AQUA)) {
               return ChatColor.AQUA;
            }

            if (namedTextColor.equals(NamedTextColor.RED)) {
               return ChatColor.RED;
            }

            if (namedTextColor.equals(NamedTextColor.LIGHT_PURPLE)) {
               return ChatColor.LIGHT_PURPLE;
            }

            if (namedTextColor.equals(NamedTextColor.YELLOW)) {
               return ChatColor.YELLOW;
            }

            if (namedTextColor.equals(NamedTextColor.WHITE)) {
               return ChatColor.WHITE;
            }
         }

         return fallback;
      }
   }

   public int getMinNameLength() {
      return this.getConfig().getInt("team-creation.min-name-length", 3);
   }

   public int getMaxNameLength() {
      return this.getConfig().getInt("team-creation.max-name-length", 16);
   }

   public int getMinTagLength() {
      return this.getConfig().getInt("team-creation.min-tag-length", 2);
   }

   public int getMaxTagLength() {
      return this.getConfig().getInt("team-creation.max-tag-length", 5);
   }

   public boolean isAllowColorCodes() {
      return this.getConfig().getBoolean("team-creation.allow-color-codes", true);
   }

   public String getNameRegex() {
      return this.getConfig().getString("team-creation.name-regex", "^[a-zA-Z0-9_]+$");
   }

   public int getCreationCooldownSeconds() {
      return this.getConfig().getInt("team-creation.creation-cooldown-seconds", 300);
   }

   public boolean isBlockDuplicateNames() {
      return this.getConfig().getBoolean("team-creation.block-duplicate-names", true);
   }

   public double getCreationCost() {
      return this.getConfig().getDouble("team-creation.creation-cost", 0.0);
   }

   public int getMaxMembers() {
      return this.getConfig().getInt("team-limits.max-members", 20);
   }

   public int getMaxHomes() {
      return this.getConfig().getInt("team-limits.max-homes", 1);
   }

   public int getMaxAllies() {
      return this.getConfig().getInt("team-limits.max-allies", 5);
   }

   public int getMaxEnemies() {
      return this.getConfig().getInt("team-limits.max-enemies", 10);
   }

   public int getMaxDescriptionLength() {
      return this.getConfig().getInt("team-limits.max-description-length", 100);
   }

   public boolean isHomeCommandEnabled() {
      return this.getConfig().getBoolean("home.enable-home", false);
   }

   public boolean isSetHomeCommandEnabled() {
      return this.getConfig().getBoolean("home.enable-sethome", false);
   }

   public int getHomeTeleportDelay() {
      return this.getConfig().getInt("home.teleport-delay-seconds", 3);
   }

   public boolean isCancelOnMove() {
      return this.getConfig().getBoolean("home.cancel-on-move", true);
   }

   public int getHomeCooldownSeconds() {
      return this.getConfig().getInt("home.cooldown-seconds", 30);
   }

   public double getTeleportCost() {
      return this.getConfig().getDouble("home.teleport-cost", 0.0);
   }

   public boolean isTeamChatEnabled() {
      return this.getConfig().getBoolean("chat.enable-team-chat", true);
   }

   public String getTeamChatFormat() {
      return this.getConfig()
         .getString("chat.team-chat-format", "<dark_gray>[<#55FFFF>Team<dark_gray>] <gray>{role} <white>{player}<dark_gray>: <white>{message}");
   }

   public boolean isSendJoinRequestEnabled() {
      return this.getConfig().getBoolean("join-requests.send-join-request", true);
   }

   public boolean isAlliesEnabled() {
      return this.getConfig().getBoolean("relations.enable-allies", true);
   }

   public boolean isEnemiesEnabled() {
      return this.getConfig().getBoolean("relations.enable-enemies", true);
   }

   public boolean isMutualAllianceRequired() {
      return this.getConfig().getBoolean("relations.mutual-alliance-required", true);
   }

   public boolean isFriendlyFireWithinTeam() {
      return this.getConfig().getBoolean("relations.friendly-fire.within-team", true);
   }

   public boolean isAllyChatEnabled() {
      return this.getConfig().getBoolean("relations.enable-ally-chat", true);
   }

   public int getAllyInviteExpireSeconds() {
      return this.getConfig().getInt("relations.ally-invite-expire-seconds", 60);
   }

   public String getAllyChatFormat() {
      return this.getConfig()
         .getString("relations.ally-chat-format", "<dark_gray>[<green>Ally<dark_gray>] <gray>{team} <gray>{role} <white>{player}<dark_gray>: <white>{message}");
   }

   public boolean isSeeMembersWhenInvis() {
      return this.getConfig().getBoolean("visibility.see-members-when-invis.default", false);
   }

   public boolean isHideNamesWhenInvisible() {
      return this.getConfig().getBoolean("visibility.hide-names-when-invisible", false);
   }

   public boolean isBankEnabled() {
      return this.getConfig().getBoolean("bank.enable-team-bank", true);
   }

   public boolean isEchestEnabled() {
      return this.getConfig().getBoolean("echest.enable-echest", false);
   }

   public int getEchestSlots() {
      int slots = this.getConfig().getInt("echest.slots", 54);
      return Math.max(1, Math.min(54, slots));
   }

   public String getEchestFillerItem() {
      return this.getConfig().getString("echest.filler-item", "GRAY_STAINED_GLASS_PANE");
   }

   public double getStartingBalance() {
      return this.getConfig().getDouble("bank.starting-balance", 0.0);
   }

   public double getMaxBalance() {
      return this.getConfig().getDouble("bank.max-balance", 1000000.0);
   }

   public boolean isPlaceholderApiEnabled() {
      return this.getConfig().getBoolean("integrations.placeholderapi.enabled", true);
   }

   public boolean isCountTeamKillsEnabled() {
      return this.getConfig().getBoolean("kills.count-team-kills", false);
   }

   public boolean isTeamColorEnabled() {
      return this.getConfig().getBoolean("team-customization.enable-color", true);
   }

   public boolean isTeamItemEnabled() {
      return this.getConfig().getBoolean("team-customization.enable-custom-item", true);
   }

   public boolean isTeamDescriptionEnabled() {
      return this.getConfig().getBoolean("team-customization.enable-description", true);
   }

   public boolean isShowDescriptionInList() {
      return this.getConfig().getBoolean("team-customization.show-description-in-list", true);
   }

   public String getDefaultTeamColorName() {
      return this.getConfig().getString("team-customization.default-color", "WHITE");
   }

   public List<String> getAvailableTeamColors() {
      List<String> colors = this.getConfig().getStringList("team-customization.available-colors");
      return colors.isEmpty() ? List.of("WHITE") : colors;
   }

   public boolean isLevelingEnabled() {
      return this.getConfig().getBoolean("leveling.enable-leveling", true);
   }

   public int getBaseXpRequired() {
      return this.getConfig().getInt("leveling.base-xp-required", 1000);
   }

   public double getXpMultiplier() {
      return this.getConfig().getDouble("leveling.xp-multiplier", 1.35);
   }

   public boolean isDisbandConfirmationRequired() {
      return this.getConfig().getBoolean("disband.require-confirmation", true);
   }

   public boolean isTransferConfirmationRequired() {
      return this.getConfig().getBoolean("transfer.require-confirmation", true);
   }

   public int getDisbandCooldownSeconds() {
      return this.getConfig().getInt("disband.cooldown-after-disband-seconds", 600);
   }

   public int getRefundPercentOnDisband() {
      return this.getConfig().getInt("disband.refund-percent-on-disband", 50);
   }

   public String getStorageType() {
      return this.getConfig().getString("storage.type", "YAML");
   }

   public boolean isGuiEnabled() {
      return this.guiConfig.getBoolean("gui.enabled", true);
   }

   public String getGuiOpenSound() {
      return this.guiConfig.getString("gui.open-sound", "ui.button.click");
   }

   public String getGuiSuccessSound() {
      return this.guiConfig.getString("gui.success-sound", "entity.player.levelup");
   }

   public String getGuiErrorSound() {
      return this.guiConfig.getString("gui.error-error", "entity.villager.no");
   }

   public enum ColorShow {
      CHAT,
      TAB,
      NAMETAG;
   }
}
