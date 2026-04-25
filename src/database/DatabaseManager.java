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
            hc.setJdbcUrl("jdbc:mysql://" + cfg.getString("host") + ":" + cfg.getInt("port") + "/" + cfg.getString("database") + "?useSSL=false");
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
                "link_code VARCHAR(10), " +
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
                "server VARCHAR(32), " +
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
    
    public void saveProfile(PlayerProfile profile) {
        String sql = mysql ?
            "INSERT INTO qq_profiles (minecraft_uuid, telegram_id, minecraft_name, telegram_name, is_linked, checkin_points, last_checkin, checkin_streak, total_checkins, gifted_points, received_points, linked_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE telegram_id=VALUES(telegram_id), minecraft_name=VALUES(minecraft_name), telegram_name=VALUES(telegram_name), is_linked=VALUES(is_linked), checkin_points=VALUES(checkin_points), last_checkin=VALUES(last_checkin), checkin_streak=VALUES(checkin_streak), total_checkins=VALUES(total_checkins), gifted_points=VALUES(gifted_points), received_points=VALUES(received_points), linked_at=VALUES(linked_at)" :
            "INSERT OR REPLACE INTO qq_profiles (minecraft_uuid, telegram_id, minecraft_name, telegram_name, is_linked, checkin_points, last_checkin, checkin_streak, total_checkins, gifted_points, received_points, linked_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profile.getMinecraftUuid().toString());
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
        String sql = "INSERT INTO qq_reports (reporter_uuid, reporter_name, target_uuid, target_name, category, comment, server, world, x, y, z, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,'pending',?,?)";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            long now = System.currentTimeMillis();
            ps.setString(1, reporterUuid.toString());
            ps.setString(2, reporterName);
            ps.setString(3, targetUuid.toString());
            ps.setString(4, targetName);
            ps.setString(5, category);
            ps.setString(6, comment);
            ps.setString(7, Bukkit.getServer().getName());
            ps.setString(8, world);
            ps.setDouble(9, x);
            ps.setDouble(10, y);
            ps.setDouble(11, z);
            ps.setLong(12, now);
            ps.setLong(13, now);
            
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save report: " + e.getMessage());
        }
        return -1;
    }
    
    // ========== КОДЫ ПРИВЯЗКИ ==========
    
    public String createLinkCode(String tgId, String tgName, String mcName) {
        String code = String.valueOf(10000 + new Random().nextInt(90000));
        long now = System.currentTimeMillis();
        
        String sql = "INSERT INTO qq_link_codes (code, telegram_id, telegram_name, minecraft_name, created_at, expires_at) VALUES (?,?,?,?,?,?)";
        
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, tgId);
            ps.setString(3, tgName);
            ps.setString(4, mcName);
            ps.setLong(5, now);
            ps.setLong(6, now + 300000); // 5 минут
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
                // Помечаем как использованный
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
}
