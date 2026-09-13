package gg.MC7DZ.teamify.storage;

import gg.MC7DZ.teamify.Teamify;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the raw JDBC connection + schema for the SQLITE and MYSQL storage backends.
 * Teams and players are stored as one row each: the row keeps a few flat columns for
 * quick lookups (id/uuid, name) plus a "data" column holding the full record serialized
 * as YAML text (the same format the YAML backend already writes to disk). This avoids
 * hand-mapping every complex field (locations, item stacks, per-member maps, etc.) to
 * SQL columns while still giving real MySQL/SQLite persistence.
 */
public class DatabaseManager {
   private final Teamify plugin;
   private final StorageType type;
   private final String teamsTable;
   private final String playersTable;
   private Connection connection;

   public DatabaseManager(Teamify plugin, StorageType type) {
      this.plugin = plugin;
      this.type = type;
      String prefix = type == StorageType.MYSQL ? plugin.getConfigManager().getMysqlTablePrefix() : "teams_";
      this.teamsTable = prefix + "teams";
      this.playersTable = prefix + "players";
   }

   public void connect() throws SQLException {
      if (this.type == StorageType.SQLITE) {
         try {
            Class.forName("org.sqlite.JDBC");
         } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found on classpath", e);
         }

         File dbFile = new File(this.plugin.getDataFolder(), this.plugin.getConfigManager().getSqliteFileName());
         if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
         }

         this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
      } else {
         try {
            Class.forName("com.mysql.cj.jdbc.Driver");
         } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found on classpath", e);
         }

         String host = this.plugin.getConfigManager().getMysqlHost();
         int port = this.plugin.getConfigManager().getMysqlPort();
         String database = this.plugin.getConfigManager().getMysqlDatabase();
         boolean useSsl = this.plugin.getConfigManager().isMysqlUseSsl();
         String url = "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useSSL=" + useSsl + "&autoReconnect=true&characterEncoding=utf8";
         this.connection = DriverManager.getConnection(
            url, this.plugin.getConfigManager().getMysqlUsername(), this.plugin.getConfigManager().getMysqlPassword()
         );
      }

      this.createTables();
   }

   /**
    * Returns a live connection, transparently reconnecting if the underlying
    * connection has died (e.g. MySQL closed an idle connection).
    */
   private Connection connection() throws SQLException {
      if (this.connection == null || this.connection.isClosed() || !this.connection.isValid(2)) {
         this.connect();
      }

      return this.connection;
   }

   private void createTables() throws SQLException {
      String teamsDdl;
      String playersDdl;
      if (this.type == StorageType.SQLITE) {
         teamsDdl = "CREATE TABLE IF NOT EXISTS " + this.teamsTable
            + " (id TEXT PRIMARY KEY, name TEXT, data TEXT NOT NULL)";
         playersDdl = "CREATE TABLE IF NOT EXISTS " + this.playersTable
            + " (uuid TEXT PRIMARY KEY, name TEXT, hidden INTEGER NOT NULL DEFAULT 0)";
      } else {
         teamsDdl = "CREATE TABLE IF NOT EXISTS " + this.teamsTable
            + " (id VARCHAR(36) PRIMARY KEY, name VARCHAR(64), data LONGTEXT NOT NULL)";
         playersDdl = "CREATE TABLE IF NOT EXISTS " + this.playersTable
            + " (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(64), hidden TINYINT(1) NOT NULL DEFAULT 0)";
      }

      try (Statement st = this.connection.createStatement()) {
         st.executeUpdate(teamsDdl);
         st.executeUpdate(playersDdl);
      }
   }

   public synchronized void upsertTeam(UUID id, String name, String yamlData) {
      String sql = this.type == StorageType.SQLITE
         ? "INSERT INTO " + this.teamsTable + " (id, name, data) VALUES (?, ?, ?) "
            + "ON CONFLICT(id) DO UPDATE SET name = excluded.name, data = excluded.data"
         : "INSERT INTO " + this.teamsTable + " (id, name, data) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE name = VALUES(name), data = VALUES(data)";

      try (PreparedStatement ps = this.connection().prepareStatement(sql)) {
         ps.setString(1, id.toString());
         ps.setString(2, name);
         ps.setString(3, yamlData);
         ps.executeUpdate();
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Failed to save team " + name + " to database: " + e.getMessage());
      }
   }

   public synchronized void deleteTeam(UUID id) {
      try (PreparedStatement ps = this.connection().prepareStatement("DELETE FROM " + this.teamsTable + " WHERE id = ?")) {
         ps.setString(1, id.toString());
         ps.executeUpdate();
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Failed to delete team " + id + " from database: " + e.getMessage());
      }
   }

   /** Returns id -> serialized YAML data for every stored team. */
   public synchronized Map<UUID, String> loadAllTeams() {
      Map<UUID, String> result = new LinkedHashMap<>();

      try (Statement st = this.connection().createStatement();
         ResultSet rs = st.executeQuery("SELECT id, data FROM " + this.teamsTable)) {
         while (rs.next()) {
            try {
               result.put(UUID.fromString(rs.getString("id")), rs.getString("data"));
            } catch (IllegalArgumentException ignored) {
            }
         }
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Failed to load teams from database: " + e.getMessage());
      }

      return result;
   }

   public synchronized void upsertPlayer(UUID uuid, String name, boolean hidden) {
      String sql = this.type == StorageType.SQLITE
         ? "INSERT INTO " + this.playersTable + " (uuid, name, hidden) VALUES (?, ?, ?) "
            + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, hidden = excluded.hidden"
         : "INSERT INTO " + this.playersTable + " (uuid, name, hidden) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE name = VALUES(name), hidden = VALUES(hidden)";

      try (PreparedStatement ps = this.connection().prepareStatement(sql)) {
         ps.setString(1, uuid.toString());
         ps.setString(2, name);
         ps.setInt(3, hidden ? 1 : 0);
         ps.executeUpdate();
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Failed to save player " + uuid + " to database: " + e.getMessage());
      }
   }

   /** Returns uuid -> {name, hidden}. */
   public synchronized Map<UUID, Object[]> loadAllPlayers() {
      Map<UUID, Object[]> result = new HashMap<>();

      try (Statement st = this.connection().createStatement();
         ResultSet rs = st.executeQuery("SELECT uuid, name, hidden FROM " + this.playersTable)) {
         while (rs.next()) {
            try {
               UUID uuid = UUID.fromString(rs.getString("uuid"));
               result.put(uuid, new Object[]{rs.getString("name"), rs.getInt("hidden") != 0});
            } catch (IllegalArgumentException ignored) {
            }
         }
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Failed to load players from database: " + e.getMessage());
      }

      return result;
   }

   public synchronized void close() {
      if (this.connection != null) {
         try {
            this.connection.close();
         } catch (SQLException e) {
            this.plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
         }
      }
   }
}
