package gg.MC7DZ.teamify.config;

import gg.MC7DZ.teamify.Teamify;
import gg.MC7DZ.teamify.gui.GuiHolder;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

   private static final Pattern HEX_AMP_PATTERN = Pattern.compile("(?i)[&§]#([0-9A-F]{6})");
   private static final Pattern HEX_LEGACY_PATTERN = Pattern.compile("(?i)§x(§[0-9A-F]){6}");

   private static final Map<NamedTextColor, ChatColor> NAMED_TO_CHAT_COLOR = Map.ofEntries(
      Map.entry(NamedTextColor.BLACK, ChatColor.BLACK),
      Map.entry(NamedTextColor.DARK_BLUE, ChatColor.DARK_BLUE),
      Map.entry(NamedTextColor.DARK_GREEN, ChatColor.DARK_GREEN),
      Map.entry(NamedTextColor.DARK_AQUA, ChatColor.DARK_AQUA),
      Map.entry(NamedTextColor.DARK_RED, ChatColor.DARK_RED),
      Map.entry(NamedTextColor.DARK_PURPLE, ChatColor.DARK_PURPLE),
      Map.entry(NamedTextColor.GOLD, ChatColor.GOLD),
      Map.entry(NamedTextColor.GRAY, ChatColor.GRAY),
      Map.entry(NamedTextColor.DARK_GRAY, ChatColor.DARK_GRAY),
      Map.entry(NamedTextColor.BLUE, ChatColor.BLUE),
      Map.entry(NamedTextColor.GREEN, ChatColor.GREEN),
      Map.entry(NamedTextColor.AQUA, ChatColor.AQUA),
      Map.entry(NamedTextColor.RED, ChatColor.RED),
      Map.entry(NamedTextColor.LIGHT_PURPLE, ChatColor.LIGHT_PURPLE),
      Map.entry(NamedTextColor.YELLOW, ChatColor.YELLOW),
      Map.entry(NamedTextColor.WHITE, ChatColor.WHITE)
   );

   /** MiniMessage tag names players are allowed to use for a custom team color (kept to color/gradient/rainbow - no click/hover/insertion tags). */
   private static final Set<String> ALLOWED_TEAM_COLOR_TAGS = Set.of(
      "black",
      "dark_blue",
      "dark_green",
      "dark_aqua",
      "dark_red",
      "dark_purple",
      "gold",
      "gray",
      "dark_gray",
      "blue",
      "green",
      "aqua",
      "red",
      "light_purple",
      "yellow",
      "white",
      "gradient",
      "rainbow",
      "color",
      "colour"
   );

   private static final Pattern TEAM_COLOR_TAG_PATTERN = Pattern.compile("<(/?)([a-zA-Z_#][a-zA-Z0-9_:#.\\-]*)>");

   /**
    * Converts legacy formatting codes (both '&' and section-sign '§' variants), including
    * legacy hex formats ("&#RRGGBB", "§#RRGGBB" and the vanilla "§x§R§R§G§G§B§B" sequence),
    * into their MiniMessage tag equivalents. Any existing MiniMessage tags in the input are
    * left untouched, so mixed strings (e.g. from older configs being migrated) work correctly.
    */
   private String legacyToMiniMessage(String input) {
      if (input.indexOf('&') < 0 && input.indexOf(167) < 0) {
         return input;
      }

      // Vanilla "§x§R§R§G§G§B§B" hex sequences (used by some plugins/exports).
      Matcher hexLegacy = HEX_LEGACY_PATTERN.matcher(input);
      StringBuilder withHexLegacyResolved = new StringBuilder();
      while (hexLegacy.find()) {
         String match = hexLegacy.group();
         StringBuilder hex = new StringBuilder(6);
         for (int i = 2; i < match.length(); i += 2) {
            hex.append(match.charAt(i + 1));
         }

         hexLegacy.appendReplacement(withHexLegacyResolved, "<#" + hex + ">");
      }

      hexLegacy.appendTail(withHexLegacyResolved);
      String afterVanillaHex = withHexLegacyResolved.toString();

      // "&#RRGGBB" / "§#RRGGBB" hex shorthand (used by plugins such as EssentialsX).
      String afterHex = HEX_AMP_PATTERN.matcher(afterVanillaHex).replaceAll(matchResult -> "<#" + matchResult.group(1) + ">");

      StringBuilder sb = new StringBuilder(afterHex.length() + 16);
      for (int i = 0; i < afterHex.length(); i++) {
         char c = afterHex.charAt(i);
         if ((c == '&' || c == 167) && i + 1 < afterHex.length()) {
            String tag = legacyCharToTag(afterHex.charAt(i + 1));
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

      String normalized = this.legacyToMiniMessage(s);
      if (this.plugin.getConfigManager().isDebug() && !normalized.equals(s)) {
         this.plugin.getLogger().info("Normalized '" + s + "' to MiniMessage: '" + normalized + "'");
      }

      try {
         Component result = this.miniMessage.deserialize(normalized);
         if (this.plugin.getConfigManager().isDebug()) {
            this.plugin.getLogger().info("Color result for '" + s + "': '" + LegacyComponentSerializer.legacyAmpersand().serialize(result) + "'");
         }

         return result;
      } catch (Exception ex) {
         // Should only happen for genuinely malformed MiniMessage tags in a config value.
         this.plugin.getLogger().warning("Failed to parse colored text '" + s + "', falling back to legacy parsing: " + ex.getMessage());
         return LegacyComponentSerializer.legacyAmpersand().deserialize(normalized.replace('§', '&'));
      }
   }

   /** Matches a single MiniMessage-recognized preset color name (the plain color tags, not gradient/rainbow/color). */
   private static final Set<String> PRESET_COLOR_NAMES = Set.of(
      "black",
      "dark_blue",
      "dark_green",
      "dark_aqua",
      "dark_red",
      "dark_purple",
      "gold",
      "gray",
      "dark_gray",
      "blue",
      "green",
      "aqua",
      "red",
      "light_purple",
      "yellow",
      "white"
   );

   /** True if a single gradient/color-list argument is a valid stop: a preset name, a hex code, or (for gradient only) an interpolation-speed float. */
   private static boolean isValidColorStop(String stop) {
      if (stop.isEmpty()) {
         return false;
      }

      if (stop.startsWith("#")) {
         return stop.matches("(?i)#[0-9A-F]{6}");
      }

      if (PRESET_COLOR_NAMES.contains(stop.toLowerCase())) {
         return true;
      }

      // gradient supports a trailing interpolation-phase float, e.g. <gradient:red:blue:0.5>
      return stop.matches("-?\\d*\\.?\\d+");
   }

   /**
    * Checks that a player-supplied custom team color is a safe, well-formed MiniMessage color, e.g.
    * {@code <red>}, {@code <#ff8800>}, {@code <gradient:red:blue>}, {@code <gradient:#ff0000:#00ff00>}
    * or {@code <rainbow>}. Only color/gradient/rainbow tags are allowed - no click/hover/insertion
    * tags can sneak in - and every color/gradient stop (hex or preset, freely mixed) is validated.
    */
   public boolean isValidTeamColorFormat(String input) {
      if (input == null) {
         return false;
      }

      String trimmed = input.trim();
      if (trimmed.isEmpty() || trimmed.length() > 64) {
         return false;
      }

      Matcher matcher = TEAM_COLOR_TAG_PATTERN.matcher(trimmed);
      boolean foundAny = false;

      while (matcher.find()) {
         foundAny = true;
         boolean closingTag = !matcher.group(1).isEmpty();
         String tagBody = matcher.group(2);
         String[] parts = tagBody.split(":");
         String tagName = parts[0];

         if (tagName.startsWith("#")) {
            if (!tagName.matches("(?i)#[0-9A-F]{6}")) {
               return false;
            }
         } else if (!ALLOWED_TEAM_COLOR_TAGS.contains(tagName.toLowerCase())) {
            return false;
         } else if (!closingTag && (tagName.equalsIgnoreCase("gradient") || tagName.equalsIgnoreCase("color") || tagName.equalsIgnoreCase("colour"))) {
            // Validate every color stop after the tag name, e.g. "red", "blue", "0.5" in
            // <gradient:red:blue:0.5>, or "#ff0000", "#00ff00" in <gradient:#ff0000:#00ff00> - hex
            // and preset names can be freely mixed within the same gradient.
            for (int i = 1; i < parts.length; i++) {
               if (!isValidColorStop(parts[i])) {
                  return false;
               }
            }
         }
      }

      if (!foundAny) {
         return false;
      }

      try {
         Component test = this.miniMessage.deserialize(trimmed + "TeamifyColorTest");
         String plain = PlainTextComponentSerializer.plainText().serialize(test);
         return plain.equals("TeamifyColorTest");
      } catch (Exception ex) {
         return false;
      }
   }

   /**
    * Renders team text (its name or tag) with either the team's custom MiniMessage color format
    * (hex, preset color, gradient, rainbow, ...) if set, or its legacy scoreboard color otherwise.
    */
   public Component colorTeamText(String colorFormat, ChatColor legacyFallback, String plainText) {
      String prefix = colorFormat != null && !colorFormat.isBlank() ? colorFormat : legacyFallback == null ? "" : legacyFallback.toString();
      return this.color(prefix + plainText);
   }

   /**
    * Same as {@link #colorTeamText}, but serialized down to a legacy '&'/hex-aware string - useful
    * for PlaceholderAPI output, since consuming plugins generally expect legacy formatting codes
    * rather than raw MiniMessage tags.
    */
   public String colorTeamTextLegacy(String colorFormat, ChatColor legacyFallback, String plainText) {
      return LegacyComponentSerializer.legacySection().serialize(this.colorTeamText(colorFormat, legacyFallback, plainText));
   }

   private TextColor firstColor(Component component) {
      if (component.color() != null) {
         return component.color();
      }

      for (Component child : component.children()) {
         TextColor found = this.firstColor(child);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   /**
    * Finds the closest matching legacy scoreboard-safe color for an arbitrary (possibly hex) color.
    * Used to keep the scoreboard nametag color roughly in sync when a team picks a custom hex/gradient
    * color, since Bukkit scoreboard teams can only use the 16 legacy colors.
    */
   public ChatColor nearestChatColor(TextColor color) {
      if (color == null) {
         return ChatColor.WHITE;
      }

      NamedTextColor named = color instanceof NamedTextColor namedTextColor ? namedTextColor : NamedTextColor.nearestTo(color);
      return NAMED_TO_CHAT_COLOR.getOrDefault(named, ChatColor.WHITE);
   }

   /** Best-effort nearest legacy {@link ChatColor} for a rendered MiniMessage color/gradient string. */
   public ChatColor nearestChatColorFor(String colorFormat, ChatColor fallback) {
      if (colorFormat == null || colorFormat.isBlank()) {
         return fallback;
      }

      try {
         String normalized = this.legacyToMiniMessage(colorFormat);
         Component sample = this.miniMessage.deserialize(normalized + "A");
         TextColor found = this.firstColor(sample);
         return found != null ? this.nearestChatColor(found) : fallback;
      } catch (Exception ex) {
         return fallback;
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

   public boolean isColoredNamesEnabled() {
      return this.getConfig().getBoolean("general.colored-names", true);
   }

   /**
    * Raw MiniMessage color format for a teammate's name (preset, hex, or gradient), used wherever the
    * name is rendered as a normal Adventure {@link Component} (chat, tab list) - unlike
    * {@link #getTeammateColor()}, this is NOT limited to the 16 legacy colors.
    */
   public String getTeammateColorFormat() {
      return this.getConfig().getString("general.teammate-color", "<green>");
   }

   /** Same as {@link #getTeammateColorFormat()}, for the allies relation. */
   public String getAlliesColorFormat() {
      return this.getConfig().getString("general.allies-color", "<blue>");
   }

   /** Same as {@link #getTeammateColorFormat()}, for the enemies relation. */
   public String getEnemysColorFormat() {
      return this.getConfig().getString("general.enemys-color", "<white>");
   }

   /**
    * Nearest legacy {@link ChatColor} for the teammate relation color - used only where the color has
    * to go through a Bukkit scoreboard Team (which can't render hex/gradient). Prefer
    * {@link #getTeammateColorFormat()} for anything rendered as a normal Component.
    */
   public ChatColor getTeammateColor() {
      return this.parseChatColor(this.getTeammateColorFormat(), ChatColor.GREEN);
   }

   /** Same as {@link #getTeammateColor()}, for the allies relation. */
   public ChatColor getAlliesColor() {
      return this.parseChatColor(this.getAlliesColorFormat(), ChatColor.BLUE);
   }

   /** Same as {@link #getTeammateColor()}, for the enemies relation. */
   public ChatColor getEnemysColor() {
      return this.parseChatColor(this.getEnemysColorFormat(), ChatColor.WHITE);
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

   /**
    * Resolves a config color value (preset name, MiniMessage hex/gradient tag, or legacy code) down to
    * the nearest of the 16 legacy {@link ChatColor} values, for contexts (scoreboard teams) that can't
    * render true hex/gradient colors.
    */
   private ChatColor parseChatColor(String name, ChatColor fallback) {
      if (name == null) {
         return fallback;
      }

      try {
         return ChatColor.valueOf(name.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
         return this.nearestChatColorFor(name, fallback);
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
