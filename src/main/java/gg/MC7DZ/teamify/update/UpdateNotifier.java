package gg.MC7DZ.teamify.update;

import gg.MC7DZ.teamify.Teamify;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Update notifier: fetches a plain JSON file (URL hardcoded in {@link #JSON_URL}, hosted
 * anywhere over HTTPS - GitHub raw, a Gist, your own site) that looks like:
 *
 * <pre>{@code
 * {
 *   "version": "1.2.0",
 *   "url": "https://yoursite.com/downloads/teamify-1.2.0.jar"
 * }
 * }</pre>
 *
 * and, if "version" is newer than the "current-version" recorded in update.yml, notifies OPs
 * and players with the "teamify.admin" permission with a clickable chat message that opens
 * "url" in their browser.
 *
 * The JSON source URL itself lives in code (see {@link #JSON_URL}), not in update.yml -
 * update.yml only controls on/off behavior (notify-ops, auto-check-updates).
 */
public final class UpdateNotifier {
   // The raw JSON URL this notifier checks for update info. Hardcoded on purpose (not in
   // update.yml) so server owners can't accidentally point it somewhere else - only edit
   // this if you're the one maintaining the plugin's update feed.
   private static final String JSON_URL = "https://raw.githubusercontent.com/yourname/yourrepo/main/update.json";

   private static final Pattern VERSION_PATTERN = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
   private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

   private final Teamify plugin;
   private volatile String latestVersion;
   private volatile String downloadUrl;
   private volatile boolean updateAvailable;
   private volatile boolean lastCheckFailed;

   public UpdateNotifier(Teamify plugin) {
      this.plugin = plugin;
   }

   /** Kicks off an async check against {@link #JSON_URL}, if auto-check-updates is enabled in update.yml. */
   public void check() {
      FileConfiguration cfg = this.plugin.getUpdateConfig();
      if (cfg.getBoolean("auto-check-updates", true)) {
         if (JSON_URL != null && !JSON_URL.isBlank()) {
            this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> this.runCheck(JSON_URL));
         } else {
            this.plugin
               .getLogger()
               .warning("Update notifier: auto-check-updates is true in update.yml but JSON_URL is empty in UpdateNotifier.java - skipping the check.");
         }
      }
   }

   private void runCheck(String jsonUrl) {
      try {
         String body = this.fetch(jsonUrl);
         Matcher versionMatcher = VERSION_PATTERN.matcher(body);
         if (!versionMatcher.find()) {
            throw new IOException("No \"version\" field found in the JSON at " + jsonUrl);
         }

         Matcher urlMatcher = URL_PATTERN.matcher(body);
         String remoteVersion = versionMatcher.group(1);
         String remoteUrl = urlMatcher.find() ? urlMatcher.group(1) : null;
         String currentVersion = this.getCurrentVersion();

         this.latestVersion = remoteVersion;
         this.downloadUrl = remoteUrl;
         this.updateAvailable = this.isNewer(remoteVersion, currentVersion);
         this.lastCheckFailed = false;

         if (this.updateAvailable) {
            this.plugin
               .getLogger()
               .warning(
                  "A new Teamify update is available: "
                     + remoteVersion
                     + " (you're running "
                     + currentVersion
                     + ")."
                     + (remoteUrl != null ? " Download: " + remoteUrl : "")
               );
         } else if (this.plugin.getConfigManager().isDebug()) {
            this.plugin.getLogger().info("Teamify update notifier: up to date (running " + currentVersion + ").");
         }
      } catch (Exception ex) {
         this.lastCheckFailed = true;
         this.plugin.getLogger().warning("Update notifier: could not check for updates: " + ex.getMessage());
      }
   }

   private String getCurrentVersion() {
      String stored = this.plugin.getUpdateConfig().getString("current-version");
      return stored != null && !stored.isBlank() ? stored : this.plugin.getDescription().getVersion();
   }

   private String fetch(String jsonUrl) throws IOException {
      URL url = URI.create(jsonUrl).toURL();
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("User-Agent", "MC7DZ/Teamify update-notifier");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      try {
         int status = connection.getResponseCode();
         if (status != 200) {
            throw new IOException("JSON_URL returned HTTP " + status);
         }

         return this.readBody(connection);
      } finally {
         connection.disconnect();
      }
   }

   private String readBody(HttpURLConnection connection) throws IOException {
      StringBuilder sb = new StringBuilder();

      String line;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
         while ((line = reader.readLine()) != null) {
            sb.append(line);
         }
      }

      return sb.toString();
   }

   private boolean isNewer(String remote, String current) {
      if (remote == null || remote.isBlank() || remote.equals(current)) {
         return false;
      }

      String[] remoteParts = remote.split("\\.");
      String[] currentParts = current.split("\\.");
      int length = Math.max(remoteParts.length, currentParts.length);

      try {
         for (int i = 0; i < length; i++) {
            int remotePart = i < remoteParts.length ? Integer.parseInt(remoteParts[i].replaceAll("\\D", "")) : 0;
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i].replaceAll("\\D", "")) : 0;
            if (remotePart != currentPart) {
               return remotePart > currentPart;
            }
         }

         return false;
      } catch (NumberFormatException ex) {
         return true;
      }
   }

   /**
    * Sends the player a chat message about the available update, with a clickable
    * "Click here to download" button that opens the download link in their browser.
    * Does nothing if no update is currently known to be available.
    */
   public void sendClickableNotice(Player player) {
      if (!this.updateAvailable || this.latestVersion == null) {
         return;
      }

      Component message = this.plugin
         .getConfigManager()
         .getPrefix()
         .append(
            this.plugin
               .getConfigManager()
               .color(
                  "<yellow>A new Teamify update is available: <white>"
                     + this.latestVersion
                     + " <yellow>(running <white>"
                     + this.getCurrentVersion()
                     + "<yellow>)."
               )
         );

      if (this.downloadUrl != null && !this.downloadUrl.isBlank()) {
         Component link = Component.text("[Click here to download]", NamedTextColor.GREEN)
            .clickEvent(ClickEvent.openUrl(this.downloadUrl))
            .hoverEvent(HoverEvent.showText(Component.text("Opens " + this.downloadUrl + " in your browser")));
         message = message.append(Component.space()).append(link);
      }

      player.sendMessage(message);
   }

   public boolean isUpdateAvailable() {
      return this.updateAvailable;
   }

   public String getLatestVersion() {
      return this.latestVersion;
   }

   public String getDownloadUrl() {
      return this.downloadUrl;
   }

   public boolean didLastCheckFail() {
      return this.lastCheckFailed;
   }
}
