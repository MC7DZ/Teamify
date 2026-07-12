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

/**
 * Checks Modrinth for a newer release of Teamify when the server starts.
 * Runs entirely off the main thread and only ever reads data - it never
 * downloads or installs anything, it just logs a console message (and
 * optionally pings ops on join, handled by PlayerListener) when a newer
 * version is published.
 *
 * Controlled by the "update-checker" section in config.yml:
 *   - enabled: master on/off switch
 *   - modrinth-id: the project's Modrinth slug or ID (empty = not configured)
 *   - notify-ops-on-join: whether to also ping ops/admins in chat
 */
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

    /** Kicks off the async check. Safe to call even when disabled - it just no-ops. */
    public void check() {
        if (!plugin.getConfigManager().isUpdateCheckEnabled()) {
            return;
        }

        String projectId = plugin.getConfigManager().getUpdateCheckModrinthId();
        if (projectId == null || projectId.isBlank()) {
            plugin.getLogger().warning("Update checker is enabled but update-checker.modrinth-id " +
                    "is not set in config.yml - skipping the check.");
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> runCheck(projectId));
    }

    private void runCheck(String projectId) {
        try {
            String remoteVersion = fetchLatestVersion(projectId);
            String currentVersion = plugin.getDescription().getVersion();

            this.latestVersion = remoteVersion;
            this.updateAvailable = isNewer(remoteVersion, currentVersion);
            this.lastCheckFailed = false;

            if (updateAvailable) {
                plugin.getLogger().warning("A new version of Teamify is available: " + remoteVersion +
                        " (you're running " + currentVersion + "). Download it from " +
                        "https://modrinth.com/plugin/" + projectId + "/versions");
            } else if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Teamify is up to date (running " + currentVersion + ").");
            }
        } catch (Exception ex) {
            this.lastCheckFailed = true;
            // Network hiccups / API downtime shouldn't spam the console with a full
            // stack trace - a single warning line is enough.
            plugin.getLogger().warning("Could not check for Teamify updates on Modrinth: " + ex.getMessage());
        }
    }

    private String fetchLatestVersion(String projectId) throws IOException {
        URL url = URI.create(String.format(VERSIONS_ENDPOINT, projectId)).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "MC7DZ/Teamify update-checker");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("Modrinth API returned HTTP " + status);
            }

            String body = readBody(connection);
            // Modrinth returns versions newest-first, so the first
            // "version_number" match in the JSON array is the latest release.
            Matcher matcher = VERSION_NUMBER_PATTERN.matcher(body);
            if (!matcher.find()) {
                throw new IOException("No versions found for Modrinth project '" + projectId + "'");
            }
            return matcher.group(1);
        } finally {
            connection.disconnect();
        }
    }

    private String readBody(HttpURLConnection connection) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * Small best-effort dotted-version comparison (e.g. "1.10.2" > "1.9.0").
     * Falls back to "an update exists" if either string doesn't parse as a
     * numeric dotted version, so odd version schemes still get flagged
     * rather than silently ignored.
     */
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

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public boolean didLastCheckFail() {
        return lastCheckFailed;
    }
}
