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

public final class ModrinthUpdateChecker {
   private static final String VERSIONS_ENDPOINT = "https://api.modrinth.com/v2/project/%s/version";
   private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
   private final Teamify plugin;
   private volatile String latestVersion;
   private volatile boolean updateAvailable;
   private volatile boolean lastCheckFailed;

   public ModrinthUpdateChecker(Teamify plugin) {
      this.plugin = plugin;
   }

   public void check() {
      if (this.plugin.getConfigManager().isUpdateCheckEnabled()) {
         String projectId = this.plugin.getConfigManager().getUpdateCheckModrinthId();
         if (projectId != null && !projectId.isBlank()) {
            this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> this.runCheck(projectId));
         } else {
            this.plugin.getLogger().warning("Update checker is enabled but update-checker.modrinth-id is not set in config.yml - skipping the check.");
         }
      }
   }

   private void runCheck(String projectId) {
      try {
         String remoteVersion = this.fetchLatestVersion(projectId);
         String currentVersion = this.plugin.getDescription().getVersion();
         this.latestVersion = remoteVersion;
         this.updateAvailable = this.isNewer(remoteVersion, currentVersion);
         this.lastCheckFailed = false;
         if (this.updateAvailable) {
            this.plugin
               .getLogger()
               .warning(
                  "A new version of Teamify is available: "
                     + remoteVersion
                     + " (you're running "
                     + currentVersion
                     + "). Download it from https://modrinth.com/plugin/"
                     + projectId
                     + "/versions"
               );
         } else if (this.plugin.getConfigManager().isDebug()) {
            this.plugin.getLogger().info("Teamify is up to date (running " + currentVersion + ").");
         }
      } catch (Exception ex) {
         this.lastCheckFailed = true;
         this.plugin.getLogger().warning("Could not check for Teamify updates on Modrinth: " + ex.getMessage());
      }
   }

   private String fetchLatestVersion(String projectId) throws IOException {
      URL url = URI.create(String.format("https://api.modrinth.com/v2/project/%s/version", projectId)).toURL();
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("User-Agent", "MC7DZ/Teamify update-checker");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      try {
         int status = connection.getResponseCode();
         if (status != 200) {
            throw new IOException("Modrinth API returned HTTP " + status);
         } else {
            String body = this.readBody(connection);
            Matcher matcher = VERSION_NUMBER_PATTERN.matcher(body);
            if (!matcher.find()) {
               throw new IOException("No versions found for Modrinth project '" + projectId + "'");
            } else {
               return matcher.group(1);
            }
         }
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
      if (remote != null && !remote.isBlank() && !remote.equals(current)) {
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
      } else {
         return false;
      }
   }

   public boolean isUpdateAvailable() {
      return this.updateAvailable;
   }

   public String getLatestVersion() {
      return this.latestVersion;
   }

   public boolean didLastCheckFail() {
      return this.lastCheckFailed;
   }
}
