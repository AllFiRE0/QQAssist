package ru.allfire.qqassist.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;
import ru.allfire.qqassist.database.models.Report;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final QQAssist plugin;
    private HikariDataSource dataSource;
    private boolean mysql;

    public DatabaseManager(QQAssist plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        var config = plugin.getConfigManager().getMainConfig();
        mysql = config.getString("database.type", "sqlite").equalsIgnoreCase("mysql");

        if (mysql) {
            return initMySQL();
        } else {
            return initSQLite();
        }
    }

    private boolean initSQLite() {
        try {
            File file = new File(plugin.getDataFolder(), "database.db");

            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hc.setMaximumPoolSize(1);
            hc.setConnectionTimeout(30000);
            hc.setPoolName("QQAssist-SQLite");

            dataSource = new HikariDataSource(hc);
            createTables();
            plugin.getLogger().info("SQLite database connected");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to init SQLite: " + e.getMessage());
            return false;
        }
    }

    private boolean initMySQL() {
        var cfg = plugin.getConfigManager().getMainConfig().getConfigurationSection("database.mysql");
        if (cfg == null) return initSQLite();

        try {
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:mysql://" + cfg.getString("host") + ":" + cfg.getInt("port") + "/"
                    + cfg.getString("database") + "?useSSL=false&allowPublicKeyRetrieval=true");
            hc.setUsername(cfg.getString("username"));
            hc.setPassword(cfg.getString("password"));
            hc.setMaximumPoolSize(10);
            hc.setPoolName("QQAssist-MySQL");

            dataSource = new HikariDataSource(hc);
            createTables();
            plugin.getLogger().info("MySQL database connected");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to init MySQL: " + e.getMessage());
            return initSQLite();
        }
    }

    private void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS qq_profiles (" +
                "id INTEGER PRIMARY KEY " + (mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", " +
                "minecraft_uuid VARCHAR(36) UNIQUE, " +
                "telegram_id VARCHAR(64) UNIQUE, " +
                "minecraft_name VARCHAR(32), " +
                "telegram_name VARCHAR(64), " +
                "is_linked BOOLEAN DEFAULT 0, " +
                "checkin_points INTEGER DEFAULT 0, " +
                "last_checkin BIGINT DEFAULT 0, " +
                "checkin_streak INTEGER DEFAULT 0, " +
                "total_checkins INTEGER DEFAULT 0, " +
                "gifted_points INTEGER DEFAULT 0, " +
                "received_points INTEGER DEFAULT 0, " +
                "linked_at BIGINT DEFAULT 0" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS qq_reports (" +
                "id INTEGER PRIMARY KEY " + (mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", " +
                "reporter_uuid VARCHAR(36), " +
                "reporter_name VARCHAR(32), " +
                "target_uuid VARCHAR(36), " +
                "target_name VARCHAR(32), " +
                "category VARCHAR(32), " +
                "comment TEXT, " +
                "world VARCHAR(64), " +
                "x DOUBLE, y DOUBLE, z DOUBLE, " +
                "status VARCHAR(16) DEFAULT 'pending', " +
                "assignee_uuid VARCHAR(36), " +
                "assignee_name VARCHAR(32), " +
                "created_at BIGINT, " +
                "updated_at BIGINT, " +
                "closed_at BIGINT, " +
                "reward_amount INTEGER DEFAULT 0" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS qq_activity_log (" +
                "id INTEGER PRIMARY KEY " + (mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", " +
                "player_uuid VARCHAR(36), " +
                "player_name VARCHAR(32), " +
                "action VARCHAR(32), " +
                "details TEXT, " +
                "timestamp BIGINT" +
                ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS qq_link_codes (" +
                "id INTEGER PRIMARY KEY " + (mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", " +
                "code VARCHAR(10) UNIQUE, " +
                "telegram_id VARCHAR(64), " +
                "telegram_name VARCHAR(64), " +
                "minecraft_name VARCHAR(32), " +
                "created_at BIGINT, " +
                "expires_at BIGINT, " +
                "used BOOLEAN DEFAULT 0" +
                ")");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) dataSource.close();
    }

    // ========== ПРОФИЛИ ==========

    public PlayerProfile getProfileByUUID(UUID uuid) {
        String sql = "SELECT * FROM qq_profiles WHERE minecraft_uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return fromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get profile: " + e.getMessage());
        }
        return null;
    }

    public PlayerProfile getProfileByTelegramId(String tgId) {
        String sql = "SELECT * FROM qq_profiles WHERE telegram_id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tgId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return fromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get profile: " + e.getMessage());
        }
        return null;
    }

    public PlayerProfile getProfileByName(String name) {
        String sql = "SELECT * FROM qq_profiles WHERE LOWER(minecraft_name) = LOWER(?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return fromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get profile: " + e.getMessage());
        }
        return null;
    }

    public void saveProfile(PlayerProfile profile) {
        String sql;
        if (mysql) {
            sql = "INSERT INTO qq_profiles (minecraft_uuid, telegram_id, minecraft_name, telegram_name, " +
                "is_linked, checkin_points, last_checkin, checkin_streak, total_checkins, " +
                "gifted_points, received_points, linked_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE telegram_id=VALUES(telegram_id), " +
                "minecraft_name=VALUES(minecraft_name), telegram_name=VALUES(telegram_name), " +
                "is_linked=VALUES(is_linked), checkin_points=VALUES(checkin_points), " +
                "last_checkin=VALUES(last_checkin), checkin_streak=VALUES(checkin_streak), " +
                "total_checkins=VALUES(total_checkins), gifted_points=VALUES(gifted_points), " +
                "received_points=VALUES(received_points), linked_at=VALUES(linked_at)";
        } else {
            sql = "INSERT OR REPLACE INTO qq_profiles (minecraft_uuid, telegram_id, minecraft_name, " +
                "telegram_name, is_linked, checkin_points, last_checkin, checkin_streak, " +
                "total_checkins, gifted_points, received_points, linked_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        }

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profile.getMinecraftUuid() != null ? profile.getMinecraftUuid().toString() : null);
            ps.setString(2, profile.getTelegramId());
            ps.setString(3, profile.getMinecraftName());
            ps.setString(4, profile.getTelegramName());
            ps.setBoolean(5, profile.isLinked());
            ps.setInt(6, profile.getCheckinPoints());
            ps.setLong(7, profile.getLastCheckin());
            ps.setInt(8, profile.getCheckinStreak());
            ps.setInt(9, profile.getTotalCheckins());
            ps.setInt(10, profile.getGiftedPoints());
            ps.setInt(11, profile.getReceivedPoints());
            ps.setLong(12, profile.getLinkedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save profile: " + e.getMessage());
        }
    }

    private PlayerProfile fromResultSet(ResultSet rs) throws SQLException {
        return new PlayerProfile(
            rs.getLong("id"),
            rs.getString("minecraft_uuid") != null ? UUID.fromString(rs.getString("minecraft_uuid")) : null,
            rs.getString("telegram_id"),
            rs.getString("minecraft_name"),
            rs.getString("telegram_name"),
            rs.getBoolean("is_linked"),
            rs.getInt("checkin_points"),
            rs.getLong("last_checkin"),
            rs.getInt("checkin_streak"),
            rs.getInt("total_checkins"),
            rs.getInt("gifted_points"),
            rs.getInt("received_points"),
            rs.getLong("linked_at")
        );
    }

    // ========== ЖАЛОБЫ ==========

    public long saveReport(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
                           String category, String comment, String world, double x, double y, double z) {
        String sql = "INSERT INTO qq_reports (reporter_uuid, reporter_name, target_uuid, target_name, " +
            "category, comment, world, x, y, z, status, created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,'pending',?,?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            long now = System.currentTimeMillis();
            ps.setString(1, reporterUuid.toString());
            ps.setString(2, reporterName);
            ps.setString(3, targetUuid.toString());
            ps.setString(4, targetName);
            ps.setString(5, category);
            ps.setString(6, comment);
            ps.setString(7, world);
            ps.setDouble(8, x);
            ps.setDouble(9, y);
            ps.setDouble(10, z);
            ps.setLong(11, now);
            ps.setLong(12, now);

            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save report: " + e.getMessage());
        }
        return -1;
    }

    public Report getReport(long id) {
        String sql = "SELECT * FROM qq_reports WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return reportFromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get report: " + e.getMessage());
        }
        return null;
    }

    public List<Report> getReports(String status) {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM qq_reports";
        if (status != null && !status.equals("all")) {
            sql += " WHERE status = ?";
        }
        sql += " ORDER BY created_at DESC LIMIT 100";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (status != null && !status.equals("all")) {
                ps.setString(1, status);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                reports.add(reportFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get reports: " + e.getMessage());
        }
        return reports;
    }

    public void assignReport(long id, UUID assigneeUuid, String assigneeName) {
        String sql = "UPDATE qq_reports SET assignee_uuid = ?, assignee_name = ?, status = 'processing', " +
            "updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assigneeUuid.toString());
            ps.setString(2, assigneeName);
            ps.setLong(3, System.currentTimeMillis());
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to assign report: " + e.getMessage());
        }
    }

    public void closeReport(long id) {
        String sql = "UPDATE qq_reports SET status = 'closed', closed_at = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            ps.setLong(1, now);
            ps.setLong(2, now);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close report: " + e.getMessage());
        }
    }

    public void rewardReport(long id, int amount) {
        String sql = "UPDATE qq_reports SET reward_amount = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setLong(2, System.currentTimeMillis());
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to reward report: " + e.getMessage());
        }
    }

    private Report reportFromResultSet(ResultSet rs) throws SQLException {
        Report report = new Report(
            rs.getLong("id"),
            rs.getString("reporter_uuid") != null ? UUID.fromString(rs.getString("reporter_uuid")) : null,
            rs.getString("reporter_name"),
            rs.getString("target_uuid") != null ? UUID.fromString(rs.getString("target_uuid")) : null,
            rs.getString("target_name"),
            rs.getString("category"),
            rs.getString("comment"),
            rs.getString("world"),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getString("status"),
            rs.getLong("created_at")
        );
        report.setUpdatedAt(rs.getLong("updated_at"));
        report.setClosedAt(rs.getLong("closed_at"));
        report.setRewardAmount(rs.getInt("reward_amount"));
        if (rs.getString("assignee_uuid") != null) {
            report.setAssignee(UUID.fromString(rs.getString("assignee_uuid")), rs.getString("assignee_name"));
        }
        return report;
    }

    // ========== ТОП ==========

    public List<Map<String, Object>> getTopCheckinPlayers(int limit) {
        List<Map<String, Object>> top = new ArrayList<>();
        String sql = "SELECT minecraft_name, checkin_points, checkin_streak FROM qq_profiles " +
            "WHERE is_linked = 1 ORDER BY checkin_points DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", rs.getString("minecraft_name"));
                map.put("points", rs.getInt("checkin_points"));
                map.put("streak", rs.getInt("checkin_streak"));
                top.add(map);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get top players: " + e.getMessage());
        }
        return top;
    }

    // ========== КОДЫ ПРИВЯЗКИ ==========

    public String createLinkCode(String tgId, String tgName, String mcName) {
        String code = String.valueOf(10000 + new Random().nextInt(90000));
        long now = System.currentTimeMillis();

        String sql = "INSERT INTO qq_link_codes (code, telegram_id, telegram_name, minecraft_name, " +
            "created_at, expires_at) VALUES (?,?,?,?,?,?)";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, tgId);
            ps.setString(3, tgName);
            ps.setString(4, mcName);
            ps.setLong(5, now);
            ps.setLong(6, now + 300000);
            ps.executeUpdate();
            return code;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create link code: " + e.getMessage());
        }
        return null;
    }

    public String verifyLinkCode(String code) {
        String sql = "SELECT * FROM qq_link_codes WHERE code = ? AND used = 0 AND expires_at > ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setLong(2, System.currentTimeMillis());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String updateSql = "UPDATE qq_link_codes SET used = 1 WHERE code = ?";
                try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                    ups.setString(1, code);
                    ups.executeUpdate();
                }
                return rs.getString("telegram_id") + ":" + rs.getString("telegram_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to verify code: " + e.getMessage());
        }
        return null;
    }

    // ========== АКТИВНОСТЬ ==========

    public void logActivity(UUID playerUuid, String playerName, String action, String details) {
        String sql = "INSERT INTO qq_activity_log (player_uuid, player_name, action, details, timestamp) " +
            "VALUES (?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, action);
            ps.setString(4, details);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log activity: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getRecentActivity(UUID playerUuid, int limit) {
        List<Map<String, Object>> activities = new ArrayList<>();
        String sql = "SELECT * FROM qq_activity_log WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("action", rs.getString("action"));
                map.put("details", rs.getString("details"));
                map.put("timestamp", rs.getLong("timestamp"));
                activities.add(map);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get activity: " + e.getMessage());
        }
        return activities;
    }
}