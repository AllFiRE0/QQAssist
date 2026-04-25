package ru.allfire.qqassist.miniapp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;
import ru.allfire.qqassist.database.models.Report;
import ru.allfire.qqassist.utils.TimeUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class MiniAppServer {

    private final QQAssist plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private HttpServer server;
    private int port;

    public MiniAppServer(QQAssist plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var config = plugin.getConfigManager().getMainConfig();
        port = config.getInt("miniapp.http_server.port", 45679);
        String host = config.getString("miniapp.http_server.host", "0.0.0.0");

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);

            server.createContext("/miniapp", this::serveMiniApp);
            server.createContext("/miniapp/style.css", this::serveCSS);
            server.createContext("/miniapp/script.js", this::serveJS);
            server.createContext("/miniapp/images/", this::serveImage);

            server.createContext("/api/qqassist/config", this::handleConfig);
            server.createContext("/api/qqassist/profile", this::handleProfile);
            server.createContext("/api/qqassist/link", this::handleLink);
            server.createContext("/api/qqassist/checkin", this::handleCheckin);
            server.createContext("/api/qqassist/shop", this::handleShop);
            server.createContext("/api/qqassist/buy", this::handleBuy);
            server.createContext("/api/qqassist/top", this::handleTop);
            server.createContext("/api/qqassist/online", this::handleOnline);
            server.createContext("/api/qqassist/report", this::handleReport);
            server.createContext("/api/qqassist/gift", this::handleGift);

            server.createContext("/api/qqassist/admin/reports", this::handleAdminReports);
            server.createContext("/api/qqassist/admin/reports/take", this::handleAdminTakeReport);
            server.createContext("/api/qqassist/admin/reports/close", this::handleAdminCloseReport);
            server.createContext("/api/qqassist/admin/reports/reward", this::handleAdminRewardReport);
            server.createContext("/api/qqassist/admin/punish", this::handleAdminPunish);

            server.setExecutor(null);
            server.start();

            plugin.getLogger().info("MiniApp HTTP server started on port " + port);

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start MiniApp server: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ========== СТРАНИЦЫ ==========

    private void serveMiniApp(HttpExchange exchange) throws IOException {
        var config = plugin.getConfigManager().getMainConfig();
        String baseUrl = config.getString("miniapp.http_server.external_url",
                "http://localhost:" + port);
        var theme = config.getConfigurationSection("miniapp.theme");
        var display = config.getConfigurationSection("miniapp.display");
        String title = display != null ? display.getString("title", "QQAssist") : "QQAssist";

        String html = generateHTML(title, baseUrl, theme);
        sendResponse(exchange, 200, "text/html", html);
    }

    private void serveCSS(HttpExchange exchange) throws IOException {
        var config = plugin.getConfigManager().getMainConfig();
        var theme = config.getConfigurationSection("miniapp.theme");
        String css = generateCSS(theme);
        sendResponse(exchange, 200, "text/css", css);
    }

    private void serveJS(HttpExchange exchange) throws IOException {
        String js = generateJS();
        sendResponse(exchange, 200, "application/javascript", js);
    }

    private void serveImage(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String fileName = path.replace("/miniapp/images/", "");

        File imageFile = new File(plugin.getDataFolder(), "miniapp/images/" + fileName);
        if (!imageFile.exists()) {
            sendResponse(exchange, 404, "text/plain", "Image not found");
            return;
        }

        String contentType = getContentType(fileName);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        byte[] bytes = Files.readAllBytes(imageFile.toPath());
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // ========== API: КОНФИГ ==========

    private void handleConfig(HttpExchange exchange) throws IOException {
        var config = plugin.getConfigManager().getMainConfig();
        var miniapp = config.getConfigurationSection("miniapp");
        Map<String, Object> response = new LinkedHashMap<>();

        if (miniapp != null) {
            response.put("theme", miniapp.get("theme"));
            response.put("display", miniapp.get("display"));
            response.put("screens", miniapp.get("screens"));
            response.put("bottom_nav", miniapp.get("bottom_nav"));
            response.put("lists", miniapp.get("lists"));
            response.put("images", miniapp.get("images"));
        }

        File shopFile = new File(plugin.getDataFolder(), "shop.yml");
        if (shopFile.exists()) {
            var shopConfig = YamlConfiguration.loadConfiguration(shopFile);
            response.put("shop", shopConfig.get("shop"));
        }

        File reportsFile = new File(plugin.getDataFolder(), "reports.yml");
        if (reportsFile.exists()) {
            var reportsConfig = YamlConfiguration.loadConfiguration(reportsFile);
            response.put("report_categories", reportsConfig.get("categories"));
        }

        response.put("punishments", config.get("reports.punishments"));

        sendJson(exchange, 200, gson.toJson(response));
    }

    // ========== API: ПРОФИЛЬ ==========

    private void handleProfile(HttpExchange exchange) throws IOException {
        String tgId = getQueryParam(exchange, "tg_id");
        if (tgId == null) {
            sendJson(exchange, 400, "{\"error\":\"tg_id required\"}");
            return;
        }

        PlayerProfile profile = plugin.getDatabaseManager().getProfileByTelegramId(tgId);
        Map<String, Object> response = new LinkedHashMap<>();

        if (profile != null && profile.isLinked()) {
            response.put("linked", true);
            response.put("minecraft_name", profile.getMinecraftName());
            response.put("telegram_name", profile.getTelegramName());
            response.put("points", profile.getCheckinPoints());
            response.put("streak", profile.getCheckinStreak());
            response.put("total_checkins", profile.getTotalCheckins());
            response.put("gifted_points", profile.getGiftedPoints());
            response.put("received_points", profile.getReceivedPoints());

            boolean canCheckin = System.currentTimeMillis() - profile.getLastCheckin() >= 86400000;
            response.put("checkin_available", canCheckin);
            if (!canCheckin) {
                long remaining = 86400000 - (System.currentTimeMillis() - profile.getLastCheckin());
                response.put("checkin_cooldown", TimeUtil.formatCooldown(remaining));
            }

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(profile.getMinecraftUuid());
            if (offlinePlayer.getName() != null) {
                long playtime = offlinePlayer.getLastSeen() - offlinePlayer.getFirstPlayed();
                response.put("playtime", playtime > 0 ? TimeUtil.formatTime(playtime) : "0");
            }

            if (Bukkit.getPluginManager().isPluginEnabled("CMI")) {
                try {
                    var cmi = Bukkit.getPlayer(profile.getMinecraftUuid());
                    if (cmi != null) {
                        response.put("balance", "?");
                        response.put("ping", cmi.getPing());
                    }
                } catch (Exception ignored) {}
            }
        } else {
            response.put("linked", false);
            response.put("points", 0);
            response.put("streak", 0);
        }

        var activities = plugin.getDatabaseManager().getRecentActivity(
            profile != null ? profile.getMinecraftUuid() : null, 5);
        List<Map<String, Object>> actList = new ArrayList<>();
        if (activities != null) {
            for (var act : activities) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("action", act.get("action"));
                m.put("details", act.get("details"));
                m.put("timestamp", act.get("timestamp"));
                actList.add(m);
            }
        }
        response.put("recent_activity", actList);

        sendJson(exchange, 200, gson.toJson(response));
    }

    // ========== API: ПРИВЯЗКА ==========

    private void handleLink(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);

        String tgId = String.valueOf(data.get("tg_id"));
        String tgName = String.valueOf(data.getOrDefault("tg_name", ""));
        String mcName = String.valueOf(data.get("minecraft_name"));

        var existing = plugin.getDatabaseManager().getProfileByName(mcName);
        if (existing != null && existing.isLinked()) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Аккаунт уже привязан\"}");
            return;
        }

        String code = plugin.getDatabaseManager().createLinkCode(tgId, tgName, mcName);
        if (code == null) {
            sendJson(exchange, 500, "{\"error\":\"Failed to create code\"}");
            return;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("code", code);
        response.put("message", "Код: " + code + ". Введите в игре: /qqlink " + code);

        sendJson(exchange, 200, gson.toJson(response));
    }

    // ========== API: CHECK-IN ==========

    private void handleCheckin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);
        String tgId = String.valueOf(data.get("tg_id"));

        var profile = plugin.getDatabaseManager().getProfileByTelegramId(tgId);
        if (profile == null || !profile.isLinked()) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Привяжите аккаунт\"}");
            return;
        }

        long cooldownMs = 86400000;
        if (System.currentTimeMillis() - profile.getLastCheckin() < cooldownMs) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Кулдаун\"}");
            return;
        }

        var config = plugin.getConfigManager().getMainConfig();
        int min = config.getInt("report_rewards.min", 1);
        int max = config.getInt("report_rewards.max", 30);
        int points = new Random().nextInt(max - min + 1) + min;

        profile.addCheckinPoints(points);
        profile.setLastCheckin(System.currentTimeMillis());
        profile.incrementStreak();
        plugin.getDatabaseManager().saveProfile(profile);
        plugin.getDatabaseManager().logActivity(profile.getMinecraftUuid(), profile.getMinecraftName(),
            "checkin", "+" + points + " points");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("points", points);
        response.put("new_balance", profile.getCheckinPoints());
        response.put("new_streak", profile.getCheckinStreak());

        sendJson(exchange, 200, gson.toJson(response));
    }

    // ========== API: МАГАЗИН ==========

    private void handleShop(HttpExchange exchange) throws IOException {
        File shopFile = new File(plugin.getDataFolder(), "shop.yml");
        if (!shopFile.exists()) {
            sendJson(exchange, 404, "{\"error\":\"Shop not configured\"}");
            return;
        }
        var shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        var shop = shopConfig.get("shop");
        sendJson(exchange, 200, gson.toJson(shop != null ? shop : Map.of()));
    }

    private void handleBuy(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);
        String tgId = String.valueOf(data.get("tg_id"));
        String itemId = String.valueOf(data.get("item_id"));
        String categoryId = String.valueOf(data.getOrDefault("category_id", ""));

        var profile = plugin.getDatabaseManager().getProfileByTelegramId(tgId);
        if (profile == null || !profile.isLinked()) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Привяжите аккаунт\"}");
            return;
        }

        File shopFile = new File(plugin.getDataFolder(), "shop.yml");
        var shopConfig = YamlConfiguration.loadConfiguration(shopFile);

        ConfigurationSection itemSection = null;
        String itemName = itemId;
        int cost = 0;
        List<String> commands = new ArrayList<>();

        var categories = shopConfig.getConfigurationSection("shop.categories");
        if (categories != null) {
            for (String catKey : categories.getKeys(false)) {
                if (!categoryId.isEmpty() && !catKey.equals(categoryId)) continue;
                var items = categories.getConfigurationSection(catKey + ".items");
                if (items != null && items.contains(itemId)) {
                    itemSection = items.getConfigurationSection(itemId);
                    break;
                }
            }
        }

        if (itemSection == null) {
            sendJson(exchange, 404, "{\"error\":\"Товар не найден\"}");
            return;
        }

        cost = itemSection.getInt("cost", 0);
        itemName = itemSection.getString("name", itemId);
        commands = itemSection.getStringList("commands");

        if (profile.getCheckinPoints() < cost) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Недостаточно очков\"}");
            return;
        }

        profile.removeCheckinPoints(cost);
        plugin.getDatabaseManager().saveProfile(profile);
        plugin.getDatabaseManager().logActivity(profile.getMinecraftUuid(), profile.getMinecraftName(),
            "purchase", itemName + " for " + cost + " points");

        for (String cmd : commands) {
            String processed = cmd.replace("%player_name%", profile.getMinecraftName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                processed.replace("asConsole! ", "").replace("message! ", ""));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("item_name", itemName);
        response.put("cost", cost);
        response.put("new_balance", profile.getCheckinPoints());

        sendJson(exchange, 200, gson.toJson(response));
    }

    // ========== API: ТОП ==========

    private void handleTop(HttpExchange exchange) throws IOException {
        int limit = 20;
        String limitParam = getQueryParam(exchange, "limit");
        if (limitParam != null) limit = Integer.parseInt(limitParam);

        var top = plugin.getDatabaseManager().getTopCheckinPlayers(limit);
        sendJson(exchange, 200, gson.toJson(Map.of("top", top)));
    }

    // ========== API: ОНЛАЙН ==========

    private void handleOnline(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", p.getName());
            map.put("ping", p.getPing());
            map.put("world", p.getWorld().getName());

            var profile = plugin.getDatabaseManager().getProfileByUUID(p.getUniqueId());
            map.put("points", profile != null ? profile.getCheckinPoints() : 0);
            map.put("linked", profile != null && profile.isLinked());
            map.put("telegram_name", profile != null && profile.isLinked() ? profile.getTelegramName() : "");

            players.add(map);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("online", players.size());
        response.put("players", players);

        sendJson(exchange, 200, gson.toJson(response));
    }

    // ========== API: ЖАЛОБА ==========

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);
        String tgId = String.valueOf(data.get("tg_id"));
        String targetName = String.valueOf(data.get("target"));
        String category = String.valueOf(data.get("category"));
        String comment = String.valueOf(data.getOrDefault("comment", ""));

        var profile = plugin.getDatabaseManager().getProfileByTelegramId(tgId);
        if (profile == null || !profile.isLinked()) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Привяжите аккаунт\"}");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore()) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Игрок не найден\"}");
            return;
        }

        Player onlinePlayer = Bukkit.getPlayer(target.getUniqueId());
        long reportId = plugin.getDatabaseManager().saveReport(
            profile.getMinecraftUuid(),
            profile.getMinecraftName(),
            target.getUniqueId(),
            targetName,
            category,
            comment,
            onlinePlayer != null ? onlinePlayer.getWorld().getName() : "unknown",
            onlinePlayer != null ? onlinePlayer.getLocation().getX() : 0,
            onlinePlayer != null ? onlinePlayer.getLocation().getY() : 0,
            onlinePlayer != null ? onlinePlayer.getLocation().getZ() : 0
        );

        plugin.getTelegramManager().sendToTelegram("reports",
            "🚩 Новая жалоба #" + reportId + "\n" +
            "👤 От: " + profile.getMinecraftName() + "\n" +
            "👤 На: " + targetName + "\n" +
            "📋 " + category + "\n" +
            "💬 " + (comment.isEmpty() ? "Без комментария" : comment));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("report_id", reportId);

        sendJson(exchange, 200, gson.toJson(response));
    }

    // ========== API: ПОДАРИТЬ ОЧКИ ==========

    private void handleGift(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);
        String tgId = String.valueOf(data.get("tg_id"));
        String targetName = String.valueOf(data.get("target"));
        int amount = Integer.parseInt(String.valueOf(data.get("amount")));

        var senderProfile = plugin.getDatabaseManager().getProfileByTelegramId(tgId);
        if (senderProfile == null || !senderProfile.isLinked()) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Привяжите аккаунт\"}");
            return;
        }

        if (senderProfile.getCheckinPoints() < amount) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Недостаточно очков\"}");
            return;
        }

        var targetProfile = plugin.getDatabaseManager().getProfileByName(targetName);
        if (targetProfile == null) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Игрок не найден\"}");
            return;
        }

        if (senderProfile.getMinecraftUuid().equals(targetProfile.getMinecraftUuid())) {
            sendJson(exchange, 200, "{\"success\":false,\"error\":\"Нельзя подарить самому себе\"}");
            return;
        }

        senderProfile.removeCheckinPoints(amount);
        senderProfile.addGiftedPoints(amount);
        targetProfile.addCheckinPoints(amount);
        targetProfile.addReceivedPoints(amount);

        plugin.getDatabaseManager().saveProfile(senderProfile);
        plugin.getDatabaseManager().saveProfile(targetProfile);

        Player targetPlayer = Bukkit.getPlayer(targetProfile.getMinecraftUuid());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(plugin.getLang().get("points_gift_received",
                "sender", senderProfile.getMinecraftName(),
                "amount", String.valueOf(amount)));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("new_balance", senderProfile.getCheckinPoints());

        sendJson(exchange, 200, gson.toJson(response));
    }

    // ========== API АДМИНА: ЖАЛОБЫ ==========

    private void handleAdminReports(HttpExchange exchange) throws IOException {
        String tgId = getQueryParam(exchange, "tg_id");
        String filter = getQueryParam(exchange, "filter");

        if (!hasAdminPermission(tgId)) {
            sendJson(exchange, 403, "{\"error\":\"Нет доступа\"}");
            return;
        }

        List<Report> reports = plugin.getDatabaseManager().getReports(filter != null ? filter : "pending");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Report r : reports) {
            list.add(reportToMap(r));
        }

        sendJson(exchange, 200, gson.toJson(Map.of("reports", list)));
    }

    private void handleAdminTakeReport(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);
        String tgId = String.valueOf(data.get("tg_id"));
        long reportId = Long.parseLong(String.valueOf(data.get("report_id")));

        if (!hasAdminPermission(tgId)) {
            sendJson(exchange, 403, "{\"error\":\"Нет доступа\"}");
            return;
        }

        var profile = plugin.getDatabaseManager().getProfileByTelegramId(tgId);
        plugin.getDatabaseManager().assignReport(reportId, profile.getMinecraftUuid(), profile.getMinecraftName());

        sendJson(exchange, 200, "{\"success\":true}");
    }

    private void handleAdminCloseReport(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);
        String tgId = String.valueOf(data.get("tg_id"));
        long reportId = Long.parseLong(String.valueOf(data.get("report_id")));

        if (!hasAdminPermission(tgId)) {
            sendJson(exchange, 403, "{\"error\":\"Нет доступа\"}");
            return;
        }

        plugin.getDatabaseManager().closeReport(reportId);
        sendJson(exchange, 200, "{\"success\":true}");
    }

    private void handleAdminRewardReport(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);
        String tgId = String.valueOf(data.get("tg_id"));
        long reportId = Long.parseLong(String.valueOf(data.get("report_id")));
        int amount = Integer.parseInt(String.valueOf(data.get("amount")));

        if (!hasAdminPermission(tgId)) {
            sendJson(exchange, 403, "{\"error\":\"Нет доступа\"}");
            return;
        }

        Report report = plugin.getDatabaseManager().getReport(reportId);
        if (report == null) {
            sendJson(exchange, 404, "{\"error\":\"Жалоба не найдена\"}");
            return;
        }

        var reporterProfile = plugin.getDatabaseManager().getProfileByUUID(report.getReporterUuid());
        if (reporterProfile != null) {
            reporterProfile.addCheckinPoints(amount);
            reporterProfile.addReceivedPoints(amount);
            plugin.getDatabaseManager().saveProfile(reporterProfile);
        }

        plugin.getDatabaseManager().rewardReport(reportId, amount);

        sendJson(exchange, 200, "{\"success\":true}");
    }

    // ========== API АДМИНА: НАКАЗАНИЕ ==========

    private void handleAdminPunish(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());
        Map<String, Object> data = gson.fromJson(body, Map.class);
        String tgId = String.valueOf(data.get("tg_id"));
        String type = String.valueOf(data.get("type"));
        String playerName = String.valueOf(data.get("target"));
        String time = String.valueOf(data.getOrDefault("time", "1d"));
        String reason = String.valueOf(data.getOrDefault("reason", "Нарушение"));

        if (!hasAdminPermission(tgId)) {
            sendJson(exchange, 403, "{\"error\":\"Нет доступа\"}");
            return;
        }

        var punishments = plugin.getConfigManager().getMainConfig()
            .getConfigurationSection("reports.punishments." + type);
        if (punishments == null) {
            sendJson(exchange, 400, "{\"error\":\"Неизвестный тип наказания\"}");
            return;
        }

        String cmd = punishments.getString("command", "")
            .replace("%player%", playerName)
            .replace("%time%", time)
            .replace("%reason%", reason);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        sendJson(exchange, 200, "{\"success\":true}");
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private boolean hasAdminPermission(String tgId) {
        if (tgId == null) return false;
        var profile = plugin.getDatabaseManager().getProfileByTelegramId(tgId);
        if (profile == null || !profile.isLinked()) return false;
        Player player = Bukkit.getPlayer(profile.getMinecraftUuid());
        return player != null && player.hasPermission("qqassist.admin.reports");
    }

    private Map<String, Object> reportToMap(Report r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("reporter", r.getReporterName());
        map.put("target", r.getTargetName());
        map.put("category", r.getCategory());
        map.put("comment", r.getComment());
        map.put("status", r.getStatus());
        map.put("assignee", r.getAssigneeName());
        map.put("created_at", r.getCreatedAt());
        map.put("updated_at", r.getUpdatedAt());
        map.put("closed_at", r.getClosedAt());
        map.put("reward_amount", r.getRewardAmount());
        map.put("world", r.getWorld());
        map.put("x", r.getX());
        map.put("y", r.getY());
        map.put("z", r.getZ());
        return map;
    }

    private String getQueryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2 && parts[0].equals(key)) {
                return parts[1];
            }
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        sendResponse(exchange, code, "application/json", json);
    }

    private String getContentType(String fileName) {
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    // ========== HTML ГЕНЕРАТОР ==========

    private String generateHTML(String title, String baseUrl, ConfigurationSection theme) {
        boolean dark = theme != null && theme.getBoolean("respect_telegram_theme", true);
        String colorScheme = dark ? "" : "<meta name=\"color-scheme\" content=\"light dark\">";

        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                %s
                <title>%s</title>
                <link rel="stylesheet" href="/miniapp/style.css">
                <script src="https://telegram.org/js/telegram-web-app.js"></script>
            </head>
            <body>
                <div id="app">
                    <div id="header">
                        <span id="header-title">%s</span>
                    </div>
                    <div id="tab-bar"></div>
                    <div id="content">
                        <div id="loading">Загрузка...</div>
                    </div>
                    <div id="bottom-nav"></div>
                </div>
                <script src="/miniapp/script.js"></script>
                <script>
                    QQAssist.init('%s');
                </script>
            </body>
            </html>
            """.formatted(colorScheme, title, title, baseUrl);
    }

    // ========== CSS ГЕНЕРАТОР ==========

    private String generateCSS(ConfigurationSection theme) {
        StringBuilder css = new StringBuilder();

        css.append(":root {\n");

        String mode = "light";
        var colors = theme != null ? theme.getConfigurationSection(mode) : null;
        if (colors != null) {
            css.append("  --bg-color: ").append(colors.getString("background_color", "#ffffff")).append(";\n");
            css.append("  --card-bg: ").append(colors.getString("card_background", "#f0f0f0")).append(";\n");
            css.append("  --primary: ").append(colors.getString("primary_color", "#007aff")).append(";\n");
            css.append("  --secondary: ").append(colors.getString("secondary_color", "#5856d6")).append(";\n");
            css.append("  --success: ").append(colors.getString("success_color", "#34c759")).append(";\n");
            css.append("  --danger: ").append(colors.getString("danger_color", "#ff3b30")).append(";\n");
            css.append("  --warning: ").append(colors.getString("warning_color", "#ff9500")).append(";\n");
            css.append("  --text: ").append(colors.getString("text_color", "#1c1c1e")).append(";\n");
            css.append("  --text-secondary: ").append(colors.getString("text_secondary", "#8e8e93")).append(";\n");
            css.append("  --button-bg: ").append(colors.getString("button_color", "#007aff")).append(";\n");
            css.append("  --button-text: ").append(colors.getString("button_text", "#ffffff")).append(";\n");
            css.append("  --input-bg: ").append(colors.getString("input_background", "#f0f0f0")).append(";\n");
            css.append("  --input-border: ").append(colors.getString("input_border", "#c7c7cc")).append(";\n");
            css.append("  --divider: ").append(colors.getString("divider_color", "#e5e5ea")).append(";\n");
            css.append("  --border-radius: ").append(colors.getString("border_radius", "12px")).append(";\n");
            css.append("  --card-shadow: ").append(colors.getString("card_shadow", "none")).append(";\n");

            var fonts = colors.getConfigurationSection("fonts");
            if (fonts != null) {
                css.append("  --font-family: ").append(fonts.getString("family", "sans-serif")).append(";\n");
                css.append("  --font-small: ").append(fonts.getString("size_small", "12px")).append(";\n");
                css.append("  --font-normal: ").append(fonts.getString("size_normal", "15px")).append(";\n");
                css.append("  --font-large: ").append(fonts.getString("size_large", "18px")).append(";\n");
                css.append("  --font-title: ").append(fonts.getString("size_title", "22px")).append(";\n");
                css.append("  --font-points: ").append(fonts.getString("size_points", "36px")).append(";\n");
            }
        }

        css.append("}\n\n");

        if (theme != null && theme.getBoolean("respect_telegram_theme", true)) {
            css.append("@media (prefers-color-scheme: dark) {\n");
            css.append("  :root {\n");
            var darkColors = theme.getConfigurationSection("dark");
            if (darkColors != null) {
                css.append("    --bg-color: ").append(darkColors.getString("background_color", "#1a1a2e")).append(";\n");
                css.append("    --card-bg: ").append(darkColors.getString("card_background", "#16213e")).append(";\n");
                css.append("    --primary: ").append(darkColors.getString("primary_color", "#e94560")).append(";\n");
                css.append("    --text: ").append(darkColors.getString("text_color", "#eeeeee")).append(";\n");
                css.append("    --text-secondary: ").append(darkColors.getString("text_secondary", "#aaaaaa")).append(";\n");
                css.append("    --button-bg: ").append(darkColors.getString("button_color", "#0f3460")).append(";\n");
                css.append("    --button-text: ").append(darkColors.getString("button_text", "#ffffff")).append(";\n");
                css.append("    --input-bg: ").append(darkColors.getString("input_background", "#16213e")).append(";\n");
                css.append("    --input-border: ").append(darkColors.getString("input_border", "#0f3460")).append(";\n");
                css.append("    --divider: ").append(darkColors.getString("divider_color", "#0f3460")).append(";\n");
            }
            css.append("  }\n");
            css.append("}\n\n");
        }

        css.append("""
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                background: var(--bg-color);
                color: var(--text);
                font-family: var(--font-family, sans-serif);
                font-size: var(--font-normal);
                padding: 8px;
                min-height: 100vh;
            }
            #header {
                text-align: center;
                padding: 12px 0;
                font-size: var(--font-title);
                font-weight: bold;
                color: var(--primary);
            }
            #tab-bar {
                display: flex;
                background: var(--card-bg);
                border-radius: var(--border-radius);
                padding: 4px;
                margin-bottom: 8px;
                overflow-x: auto;
            }
            .tab {
                flex: 1;
                text-align: center;
                padding: 8px 4px;
                cursor: pointer;
                border-radius: calc(var(--border-radius) - 4px);
                font-size: var(--font-small);
                white-space: nowrap;
                color: var(--text-secondary);
            }
            .tab.active {
                background: var(--button-bg);
                color: var(--button-text);
            }
            #bottom-nav {
                display: flex;
                position: fixed;
                bottom: 0;
                left: 0;
                right: 0;
                background: var(--card-bg);
                padding: 8px;
                border-top: 1px solid var(--divider);
            }
            .nav-item {
                flex: 1;
                text-align: center;
                cursor: pointer;
                font-size: var(--font-small);
                color: var(--text-secondary);
            }
            .nav-item.active {
                color: var(--primary);
            }
            .card {
                background: var(--card-bg);
                border-radius: var(--border-radius);
                padding: 16px;
                margin: 8px 0;
                box-shadow: var(--card-shadow);
            }
            .btn {
                background: var(--button-bg);
                color: var(--button-text);
                border: none;
                padding: 12px;
                border-radius: var(--border-radius);
                width: 100%;
                font-size: var(--font-normal);
                cursor: pointer;
                margin: 4px 0;
            }
            .btn:active { opacity: 0.8; }
            .btn.danger { background: var(--danger); }
            .btn.success { background: var(--success); }
            .btn.warning { background: var(--warning); }
            .btn.small { padding: 8px; font-size: var(--font-small); width: auto; }
            .points {
                font-size: var(--font-points);
                color: var(--primary);
                font-weight: bold;
            }
            .shop-item {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 12px 0;
                border-bottom: 1px solid var(--divider);
            }
            input, textarea {
                width: 100%;
                padding: 10px;
                margin: 8px 0;
                border-radius: var(--border-radius);
                border: 1px solid var(--input-border);
                background: var(--input-bg);
                color: var(--text);
                font-size: var(--font-normal);
            }
            #loading {
                text-align: center;
                padding: 40px;
                color: var(--text-secondary);
            }
            .spacer { height: 80px; }
            """);

        return css.toString();
    }

    // ========== JS ГЕНЕРАТОР ==========

    private String generateJS() {
        return """
            const QQAssist = {
                config: null,
                profile: null,
                currentTab: 'home',
                baseUrl: '',

                async init(baseUrl) {
                    this.baseUrl = baseUrl;
                    this.config = await this.fetchAPI('/api/qqassist/config');
                    this.applyTheme();
                    this.renderBottomNav();
                    this.switchTab(this.config.display?.default_tab || 'home');
                },

                async fetchAPI(path, method = 'GET', body = null) {
                    try {
                        const options = { method, headers: { 'Content-Type': 'application/json' } };
                        if (body) options.body = JSON.stringify(body);
                        const res = await fetch(this.baseUrl + path, options);
                        return await res.json();
                    } catch (e) {
                        return { error: 'Connection failed' };
                    }
                },

                applyTheme() {
                    const tg = window.Telegram?.WebApp;
                    if (tg) {
                        tg.ready();
                        tg.expand();
                    }
                },

                switchTab(tab) {
                    this.currentTab = tab;
                    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
                    const tabEl = document.getElementById('tab-' + tab);
                    if (tabEl) tabEl.classList.add('active');
                    const navEl = document.getElementById('nav-' + tab);
                    if (navEl) navEl.classList.add('active');

                    switch(tab) {
                        case 'home': this.renderHome(); break;
                        case 'shop': this.renderShop(); break;
                        case 'online': this.renderOnline(); break;
                        case 'top': this.renderTop(); break;
                        case 'profile': this.renderMyProfile(); break;
                        case 'checkin': this.renderCheckin(); break;
                        case 'reports': this.renderReports(); break;
                    }
                },

                async renderHome() {
                    const tg = window.Telegram?.WebApp;
                    if (!tg) { this.setContent('<div class="card">Откройте через Telegram</div>'); return; }
                    this.profile = await this.fetchAPI('/api/qqassist/profile?tg_id=' + tg.initDataUnsafe.user.id);
                    let html = '';
                    if (this.profile.linked) {
                        html += '<div class="card">';
                        html += '<h3>👤 ' + (this.profile.minecraft_name || '') + '</h3>';
                        html += '<div class="points">' + (this.profile.points || 0) + ' ⭐</div>';
                        html += '<div>📅 Серия: ' + (this.profile.streak || 0) + ' дн.</div>';
                        html += '</div>';

                        html += '<div class="card">';
                        html += '<h3>🎁 Ежедневный Check-in</h3>';
                        if (this.profile.checkin_available) {
                            html += '<button class="btn success" onclick="QQAssist.doCheckin()">🎁 ОТМЕТИТЬСЯ (+1-30)</button>';
                        } else {
                            html += '<button class="btn" disabled>⏳ Доступно через ' + (this.profile.checkin_cooldown || '?') + '</button>';
                        }
                        html += '</div>';
                    } else {
                        html += '<div class="card"><h3>Аккаунт не привязан</h3>';
                        html += '<button class="btn" onclick="QQAssist.renderLink()">🔗 Привязать</button></div>';
                    }

                    html += '<div class="card"><h3>Быстрые действия</h3>';
                    html += '<button class="btn small" onclick="QQAssist.switchTab(\'shop\')">🛒 Магазин</button> ';
                    html += '<button class="btn small" onclick="QQAssist.switchTab(\'online\')">🟢 Онлайн</button> ';
                    html += '<button class="btn small" onclick="QQAssist.switchTab(\'top\')">🏆 Топ</button>';
                    html += '</div>';
                    html += '<div class="spacer"></div>';
                    this.setContent(html);
                },

                async doCheckin() {
                    const tg = window.Telegram?.WebApp;
                    const res = await this.fetchAPI('/api/qqassist/checkin', 'POST', { tg_id: tg.initDataUnsafe.user.id });
                    if (res.success) {
                        tg.showPopup({ title: '🎁 Check-in!', message: 'Получено ' + res.points + ' очков! Баланс: ' + res.new_balance });
                        this.renderHome();
                    } else {
                        tg.showPopup({ title: '❌', message: res.error });
                    }
                },

                async renderShop() {
                    const data = await this.fetchAPI('/api/qqassist/shop');
                    const shop = data || {};
                    let html = '<div class="card"><h3>' + (shop.title || '🛒 Магазин') + '</h3></div>';
                    if (shop.categories) {
                        for (const [catId, cat] of Object.entries(shop.categories)) {
                            html += '<div class="card"><h4>' + (cat.icon || '') + ' ' + (cat.name || catId) + '</h4>';
                            if (cat.items) {
                                for (const [itemId, item] of Object.entries(cat.items)) {
                                    html += '<div class="shop-item">';
                                    html += '<div><strong>' + (item.name || itemId) + '</strong></div>';
                                    html += '<div>' + (item.cost || 0) + ' ' + (shop.currency_icon || '⭐') + '</div>';
                                    html += '<button class="btn small" onclick="QQAssist.buyItem(\'' + catId + '\',\'' + itemId + '\')">Купить</button>';
                                    html += '</div>';
                                }
                            }
                            html += '</div>';
                        }
                    }
                    html += '<div class="spacer"></div>';
                    this.setContent(html);
                },

                async buyItem(catId, itemId) {
                    const tg = window.Telegram?.WebApp;
                    const res = await this.fetchAPI('/api/qqassist/buy', 'POST', {
                        tg_id: tg.initDataUnsafe.user.id,
                        category_id: catId,
                        item_id: itemId
                    });
                    if (res.success) {
                        tg.showPopup({ title: '✅', message: 'Куплено: ' + res.item_name + ' за ' + res.cost + ' ⭐' });
                    } else {
                        tg.showPopup({ title: '❌', message: res.error });
                    }
                },

                async renderOnline() {
                    const data = await this.fetchAPI('/api/qqassist/online');
                    let html = '<div class="card"><h3>🟢 Игроки онлайн (' + (data.online || 0) + ')</h3>';
                    html += '<input type="text" placeholder="🔍 Поиск..." oninput="QQAssist.filterOnline(this.value)">';
                    html += '<div id="online-list">';
                    if (data.players) {
                        data.players.forEach(p => {
                            html += '<div class="card online-player">';
                            html += '<strong>' + p.name + '</strong> 📶' + (p.ping || 0) + 'ms';
                            html += ' ⭐' + (p.points || 0);
                            html += ' ' + (p.linked ? '✅' : '❌');
                            html += '<br><button class="btn small" onclick="QQAssist.viewPlayerProfile(\'' + p.name + '\')">Профиль</button>';
                            html += '</div>';
                        });
                    }
                    html += '</div><div class="spacer"></div>';
                    this.setContent(html);
                },

                filterOnline(query) {
                    document.querySelectorAll('.online-player').forEach(el => {
                        el.style.display = el.textContent.toLowerCase().includes(query.toLowerCase()) ? '' : 'none';
                    });
                },

                async viewPlayerProfile(name) {
                    this.setContent('<div class="card"><h3>👤 ' + name + '</h3><p>Загрузка...</p></div>');
                    const tg = window.Telegram?.WebApp;
                    const data = await this.fetchAPI('/api/qqassist/profile?tg_id=' + tg.initDataUnsafe.user.id + '&target=' + name);
                    let html = '<div class="card"><h3>👤 ' + name + '</h3>';
                    html += '<p>⭐ Очков QQ: ' + (data.points || 0) + '</p>';
                    html += '<p>📶 Пинг: ' + (data.ping || '?') + 'ms</p>';
                    html += '<p>🕐 Наиграно: ' + (data.playtime || '?') + '</p>';
                    html += '<button class="btn success" onclick="QQAssist.showGiftDialog(\'' + name + '\')">🎁 Подарить очки</button>';
                    html += '<button class="btn danger" onclick="QQAssist.showReportDialog(\'' + name + '\')">🚩 Пожаловаться</button>';
                    html += '<button class="btn small" onclick="QQAssist.switchTab(\'online\')">← К списку</button>';
                    html += '</div>';
                    this.setContent(html);
                },

                showGiftDialog(target) {
                    const tg = window.Telegram?.WebApp;
                    tg.showPopup({
                        title: '🎁 Подарить очки',
                        message: 'Введите количество очков для ' + target,
                        buttons: [{ type: 'default', text: 'Отмена' }, { type: 'ok', text: 'Подарить' }]
                    }, async (btn) => {
                        if (btn === 'ok') {
                            const res = await this.fetchAPI('/api/qqassist/gift', 'POST', {
                                tg_id: tg.initDataUnsafe.user.id,
                                target: target,
                                amount: 10
                            });
                            tg.showPopup({ title: res.success ? '✅' : '❌', message: res.success ? 'Подарено!' : res.error });
                        }
                    });
                },

                showReportDialog(target) {
                    const tg = window.Telegram?.WebApp;
                    const categories = ['cheating', 'offensive', 'griefing', 'interference', 'bug_abuse', 'other'];
                    const labels = { cheating: 'Читы', offensive: 'Оскорбления', griefing: 'Гриферство', interference: 'Помеха', bug_abuse: 'Баги', other: 'Другое' };
                    let msg = 'Выберите причину жалобы на ' + target + ':\n';
                    categories.forEach(c => msg += '\n' + labels[c]);
                    tg.showPopup({ title: '🚩 Жалоба', message: msg });
                },

                async renderTop() {
                    const data = await this.fetchAPI('/api/qqassist/top?limit=20');
                    let html = '<div class="card"><h3>🏆 Топ игроков</h3>';
                    if (data.top) {
                        data.top.forEach((p, i) => {
                            html += '<div>' + (i + 1) + '. ' + p.name + ' — ' + p.points + ' ⭐</div>';
                        });
                    }
                    html += '</div><div class="spacer"></div>';
                    this.setContent(html);
                },

                async renderMyProfile() {
                    const tg = window.Telegram?.WebApp;
                    if (!tg || !this.profile) { this.renderHome(); return; }
                    let html = '<div class="card"><h3>👤 Профиль</h3>';
                    if (this.profile.linked) {
                        html += '<p>Ник: ' + this.profile.minecraft_name + '</p>';
                        html += '<p>⭐ Очков: ' + this.profile.points + '</p>';
                        html += '<p>📅 Серия: ' + this.profile.streak + ' дн.</p>';
                        html += '<p>📱 Telegram: ✅ ' + (this.profile.telegram_name || '') + '</p>';
                        html += '<button class="btn danger" onclick="QQAssist.unlink()">Отвязать</button>';
                    } else {
                        html += '<p>Не привязан</p>';
                        html += '<button class="btn" onclick="QQAssist.renderLink()">🔗 Привязать</button>';
                    }
                    html += '</div><div class="spacer"></div>';
                    this.setContent(html);
                },

                async renderCheckin() {
                    await this.renderHome();
                },

                async renderLink() {
                    let html = '<div class="card"><h3>🔗 Привязка аккаунта</h3>';
                    html += '<input type="text" id="link-nickname" placeholder="Ваш ник в игре">';
                    html += '<button class="btn" onclick="QQAssist.doLink()">🔗 Привязать</button>';
                    html += '<div id="link-result"></div>';
                    html += '</div>';
                    this.setContent(html);
                },

                async doLink() {
                    const tg = window.Telegram?.WebApp;
                    const nickname = document.getElementById('link-nickname').value;
                    const res = await this.fetchAPI('/api/qqassist/link', 'POST', {
                        tg_id: tg.initDataUnsafe.user.id,
                        tg_name: tg.initDataUnsafe.user.username || tg.initDataUnsafe.user.first_name,
                        minecraft_name: nickname
                    });
                    if (res.success) {
                        document.getElementById('link-result').innerHTML = '<p>✅ Код: <b>' + res.code + '</b></p><p>Введите в игре: /qqlink ' + res.code + '</p>';
                    } else {
                        document.getElementById('link-result').innerHTML = '<p>❌ ' + (res.error || 'Ошибка') + '</p>';
                    }
                },

                async unlink() {
                    const tg = window.Telegram?.WebApp;
                    tg.showPopup({ title: 'Отвязать?', message: 'Вы уверены?', buttons: [{ type: 'cancel' }, { type: 'ok' }] }, async (btn) => {
                        if (btn === 'ok') {
                            tg.showPopup({ title: '✅', message: 'Введите /qqunlink в игре' });
                        }
                    });
                },

                async renderReports() {
                    const tg = window.Telegram?.WebApp;
                    const data = await this.fetchAPI('/api/qqassist/admin/reports?tg_id=' + tg.initDataUnsafe.user.id + '&filter=pending');
                    let html = '<div class="card"><h3>📋 Жалобы</h3>';
                    html += '<button class="btn small" onclick="QQAssist.renderReportsFilter(\'pending\')">Новые</button> ';
                    html += '<button class="btn small" onclick="QQAssist.renderReportsFilter(\'processing\')">В работе</button> ';
                    html += '<button class="btn small" onclick="QQAssist.renderReportsFilter(\'closed\')">Закрыто</button>';
                    html += '<div id="reports-list"></div></div>';
                    this.setContent(html);
                    this.renderReportsList(data.reports || []);
                },

                async renderReportsFilter(filter) {
                    const tg = window.Telegram?.WebApp;
                    const data = await this.fetchAPI('/api/qqassist/admin/reports?tg_id=' + tg.initDataUnsafe.user.id + '&filter=' + filter);
                    this.renderReportsList(data.reports || []);
                },

                renderReportsList(reports) {
                    let html = '';
                    reports.forEach(r => {
                        const icon = r.status === 'pending' ? '🔴' : r.status === 'processing' ? '🟡' : '🟢';
                        html += '<div class="card">';
                        html += icon + ' #' + r.id + ' ' + r.reporter + ' → ' + r.target + ' [' + r.category + ']';
                        html += '<br>';
                        if (r.status === 'pending') {
                            html += '<button class="btn small" onclick="QQAssist.takeReport(' + r.id + ')">👀 Взять</button> ';
                        }
                        if (r.status !== 'closed') {
                            html += '<button class="btn small" onclick="QQAssist.closeReport(' + r.id + ')">✖ Закрыть</button>';
                        }
                        html += '</div>';
                    });
                    document.getElementById('reports-list').innerHTML = html || '<p>Нет жалоб</p>';
                },

                async takeReport(id) {
                    const tg = window.Telegram?.WebApp;
                    await this.fetchAPI('/api/qqassist/admin/reports/take', 'POST', { tg_id: tg.initDataUnsafe.user.id, report_id: id });
                    this.renderReports();
                },

                async closeReport(id) {
                    const tg = window.Telegram?.WebApp;
                    await this.fetchAPI('/api/qqassist/admin/reports/close', 'POST', { tg_id: tg.initDataUnsafe.user.id, report_id: id });
                    this.renderReports();
                },

                renderBottomNav() {
                    const nav = this.config?.bottom_nav;
                    if (!nav || !nav.show) return;
                    let html = '';
                    if (nav.items) {
                        nav.items.forEach(item => {
                            html += '<div class="nav-item" id="nav-' + item.id + '" onclick="QQAssist.switchTab(\'' + item.id + '\')">';
                            html += item.icon + ' ' + item.label;
                            html += '</div>';
                        });
                    }
                    document.getElementById('bottom-nav').innerHTML = html;
                },

                setContent(html) {
                    document.getElementById('content').innerHTML = html;
                }
            };
            """;
    }
}
