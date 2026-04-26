package ru.allfire.qqassist.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;
import ru.allfire.qqassist.utils.TimeUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class TelegramBotListener implements HttpHandler {

    private final QQAssist plugin;
    private final Gson gson = new Gson();
    private final Random random = new Random();

    // Кулдауны
    private final Map<Long, Long> userCooldowns = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Long>> ruleCooldowns = new ConcurrentHashMap<>();

    // Конфиг telegram_responses.yml
    private FileConfiguration tgConfig;
    private int globalCooldownSeconds;
    private int globalAutoDeleteSeconds;

    // Скомпилированные паттерны для правил
    private static class CompiledRule {
        String id;
        int priority;
        long cooldownTicks;
        int autoDeleteSeconds;
        List<String> exactPhrases;
        List<Pattern> regexPatterns;
        List<String> containsWords;
        List<String> randomAnswers;
    }
    private List<CompiledRule> compiledRules = new ArrayList<>();

    public TelegramBotListener(QQAssist plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "telegram_responses.yml");
        if (!file.exists()) {
            plugin.saveResource("telegram_responses.yml", false);
        }
        tgConfig = YamlConfiguration.loadConfiguration(file);

        var settings = tgConfig.getConfigurationSection("settings");
        if (settings != null) {
            globalCooldownSeconds = settings.getInt("global_cooldown_seconds", 5);
            globalAutoDeleteSeconds = settings.getInt("auto_delete_seconds", 0);
        }

        // Компилируем правила
        compiledRules.clear();
        var rulesSection = tgConfig.getConfigurationSection("rules");
        if (rulesSection != null) {
            for (String ruleId : rulesSection.getKeys(false)) {
                var rs = rulesSection.getConfigurationSection(ruleId);
                if (rs == null) continue;

                CompiledRule rule = new CompiledRule();
                rule.id = ruleId;
                rule.priority = rs.getInt("priority", 50);
                rule.cooldownTicks = rs.getLong("cooldown_ticks", 100);
                rule.autoDeleteSeconds = rs.getInt("auto_delete_seconds", 0);

                var qs = rs.getConfigurationSection("questions");
                if (qs != null) {
                    rule.exactPhrases = qs.getStringList("exact");
                    rule.containsWords = qs.getStringList("contains");
                    rule.regexPatterns = new ArrayList<>();
                    for (String regex : qs.getStringList("regex")) {
                        rule.regexPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                    }
                }
                rule.randomAnswers = rs.getStringList("random_answers");
                compiledRules.add(rule);
            }
            compiledRules.sort((a, b) -> Integer.compare(b.priority, a.priority));
        }

        plugin.getLogger().info("[QQAssist] Telegram bot config loaded: " 
            + compiledRules.size() + " rules loaded");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[QQAssist] Telegram webhook: " + body);
        }

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            // Проверяем, что это сообщение от TgBridge
            if (!json.has("message") || !json.has("from")) {
                sendResponse(exchange, 200, "OK");
                return;
            }

            JsonObject from = json.getAsJsonObject("from");
            long userId = from.has("id") ? from.get("id").getAsLong() : 0;
            String userName = from.has("username") ? from.get("username").getAsString() : 
                              from.has("first_name") ? from.get("first_name").getAsString() : "Unknown";

            JsonObject messageObj = json.getAsJsonObject("message");
            String text = messageObj.has("text") ? messageObj.get("text").getAsString() : "";

            // Получаем chat_id и topic_id
            JsonObject chat = messageObj.has("chat") ? messageObj.getAsJsonObject("chat") : null;
            String chatId = chat != null && chat.has("id") ? String.valueOf(chat.get("id").getAsLong()) : "";
            int topicId = 0;
            if (messageObj.has("message_thread_id")) {
                topicId = messageObj.get("message_thread_id").getAsInt();
            }

            boolean isTopic = messageObj.has("is_topic_message") && 
                              messageObj.get("is_topic_message").getAsBoolean();

            // Логирование
            if (tgConfig.getBoolean("settings.log_messages", true)) {
                plugin.getLogger().info("[QQAssist] TG Message from " + userName 
                    + " (ID:" + userId + "): " + text);
            }

            // Обрабатываем сообщение
            handleMessage(userId, userName, chatId, topicId, isTopic, text.trim());

        } catch (Exception e) {
            plugin.getLogger().warning("[QQAssist] Error processing Telegram webhook: " + e.getMessage());
        }

        sendResponse(exchange, 200, "OK");
    }

    private void handleMessage(long userId, String userName, String chatId, 
                               int topicId, boolean isTopic, String text) {
        if (text.isEmpty()) return;

        // Проверяем глобальный кулдаун
        if (isOnGlobalCooldown(userId)) return;

        // Сначала проверяем команды
        if (handleCommand(userId, userName, chatId, topicId, text)) {
            userCooldowns.put(userId, System.currentTimeMillis());
            return;
        }

        // Затем проверяем автоответы
        if (handleAutoResponse(userId, userName, chatId, topicId, text)) {
            userCooldowns.put(userId, System.currentTimeMillis());
            return;
        }
    }

    private boolean isOnGlobalCooldown(long userId) {
        Long last = userCooldowns.get(userId);
        if (last != null) {
            long elapsed = (System.currentTimeMillis() - last) / 1000;
            if (elapsed < globalCooldownSeconds) {
                return true;
            }
        }
        return false;
    }

    // ========== ОБРАБОТКА КОМАНД ==========

    private boolean handleCommand(long userId, String userName, String chatId, 
                                  int topicId, String text) {
        var commandsSection = tgConfig.getConfigurationSection("commands");
        if (commandsSection == null) return false;

        for (String cmdKey : commandsSection.getKeys(false)) {
            var cmd = commandsSection.getConfigurationSection(cmdKey);
            if (cmd == null) continue;

            List<String> aliases = cmd.getStringList("aliases");
            String lowerText = text.toLowerCase().trim();

            boolean matched = false;
            for (String alias : aliases) {
                if (lowerText.equals(alias.toLowerCase()) || lowerText.startsWith(alias.toLowerCase() + " ")) {
                    matched = true;
                    break;
                }
            }

            if (!matched) continue;

            int autoDelete = cmd.getInt("auto_delete_seconds", 0);
            if (autoDelete == 0) autoDelete = globalAutoDeleteSeconds;

            switch (cmdKey) {
                case "checkin" -> handleCheckinCommand(userId, userName, chatId, topicId, cmd, autoDelete);
                case "points" -> handlePointsCommand(userId, userName, chatId, topicId, cmd, autoDelete);
                case "profile" -> handleProfileCommand(userId, userName, chatId, topicId, cmd, autoDelete);
                case "help" -> handleHelpCommand(chatId, topicId, cmd, autoDelete);
                case "top" -> handleTopCommand(chatId, topicId, cmd, autoDelete);
                case "online" -> handleOnlineCommand(chatId, topicId, cmd, autoDelete);
                case "link" -> handleLinkCommand(userId, userName, chatId, topicId, text, cmd, autoDelete);
                case "unlink" -> handleUnlinkCommand(userId, chatId, topicId, cmd, autoDelete);
            }
            return true;
        }
        return false;
    }

    private void handleCheckinCommand(long userId, String userName, String chatId, 
                                      int topicId, ConfigurationSection cmd, int autoDelete) {
        PlayerProfile profile = plugin.getDatabaseManager().getProfileByTelegramId(String.valueOf(userId));

        if (profile == null || !profile.isLinked()) {
            sendMessage(chatId, topicId, cmd.getString("messages.not_linked", "❌ Привяжите аккаунт"), autoDelete);
            return;
        }

        int cooldownHours = cmd.getInt("cooldown_hours", 24);
        long cooldownMs = (long) cooldownHours * 3600000;
        long timeSinceLast = System.currentTimeMillis() - profile.getLastCheckin();

        if (timeSinceLast < cooldownMs) {
            String timeLeft = TimeUtil.formatCooldown(cooldownMs - timeSinceLast);
            String msg = cmd.getString("messages.cooldown", "⏳ Следующий check-in через %time%")
                .replace("%time%", timeLeft);
            sendMessage(chatId, topicId, msg, autoDelete);
            return;
        }

        int min = cmd.getInt("rewards.min", 1);
        int max = cmd.getInt("rewards.max", 30);
        int points = random.nextInt(max - min + 1) + min;

        profile.addCheckinPoints(points);
        profile.setLastCheckin(System.currentTimeMillis());
        profile.incrementStreak();
        plugin.getDatabaseManager().saveProfile(profile);
        plugin.getDatabaseManager().logActivity(profile.getMinecraftUuid(), profile.getMinecraftName(),
            "checkin", "+" + points + " points from Telegram");

        String msg = cmd.getString("messages.success", "🎁 Получено %points% очков!")
            .replace("%points%", String.valueOf(points))
            .replace("%balance%", String.valueOf(profile.getCheckinPoints()));
        sendMessage(chatId, topicId, msg, autoDelete);
    }

    private void handlePointsCommand(long userId, String userName, String chatId, 
                                     int topicId, ConfigurationSection cmd, int autoDelete) {
        PlayerProfile profile = plugin.getDatabaseManager().getProfileByTelegramId(String.valueOf(userId));

        if (profile == null || !profile.isLinked()) {
            sendMessage(chatId, topicId, cmd.getString("messages.not_linked", "❌ Привяжите аккаунт"), autoDelete);
            return;
        }

        String msg = cmd.getString("messages.balance", "⭐ Баланс: %points% очков")
            .replace("%points%", String.valueOf(profile.getCheckinPoints()))
            .replace("%streak%", String.valueOf(profile.getCheckinStreak()))
            .replace("%gifted%", String.valueOf(profile.getGiftedPoints()))
            .replace("%received%", String.valueOf(profile.getReceivedPoints()));
        sendMessage(chatId, topicId, msg, autoDelete);
    }

    private void handleProfileCommand(long userId, String userName, String chatId, 
                                      int topicId, ConfigurationSection cmd, int autoDelete) {
        PlayerProfile profile = plugin.getDatabaseManager().getProfileByTelegramId(String.valueOf(userId));

        if (profile == null || !profile.isLinked()) {
            sendMessage(chatId, topicId, cmd.getString("messages.not_linked", "❌ Привяжите аккаунт"), autoDelete);
            return;
        }

        String linked = profile.isLinked() ? "✅ @" + (profile.getTelegramName() != null ? profile.getTelegramName() : "") : "❌";

        String msg = cmd.getString("messages.info", "👤 Профиль")
            .replace("%player_name%", profile.getMinecraftName())
            .replace("%points%", String.valueOf(profile.getCheckinPoints()))
            .replace("%streak%", String.valueOf(profile.getCheckinStreak()))
            .replace("%gifted%", String.valueOf(profile.getGiftedPoints()))
            .replace("%received%", String.valueOf(profile.getReceivedPoints()))
            .replace("%total%", String.valueOf(profile.getTotalCheckins()))
            .replace("%linked%", linked);
        sendMessage(chatId, topicId, msg, autoDelete);
    }

    private void handleHelpCommand(String chatId, int topicId, ConfigurationSection cmd, int autoDelete) {
        String msg = cmd.getString("messages.help", "📋 Список команд");
        sendMessage(chatId, topicId, msg, autoDelete);
    }

    private void handleTopCommand(String chatId, int topicId, ConfigurationSection cmd, int autoDelete) {
        var top = plugin.getDatabaseManager().getTopCheckinPlayers(10);
        if (top.isEmpty()) {
            sendMessage(chatId, topicId, cmd.getString("messages.empty", "Нет данных"), autoDelete);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(cmd.getString("messages.header", "🏆 Топ игроков:")).append("\n");

        String format = cmd.getString("messages.format", "#%pos% %name% — %points% ⭐");
        for (int i = 0; i < top.size(); i++) {
            var player = top.get(i);
            sb.append(format
                .replace("%pos%", String.valueOf(i + 1))
                .replace("%name%", String.valueOf(player.get("name")))
                .replace("%points%", String.valueOf(player.get("points"))))
                .append("\n");
        }

        sendMessage(chatId, topicId, sb.toString(), autoDelete);
    }

    private void handleOnlineCommand(String chatId, int topicId, ConfigurationSection cmd, int autoDelete) {
        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            sendMessage(chatId, topicId, cmd.getString("messages.empty", "Нет игроков"), autoDelete);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(cmd.getString("messages.header", "🟢 Онлайн:").replace("%count%", String.valueOf(players.size()))).append("\n");

        String format = cmd.getString("messages.format", "👤 %name%");
        for (Player p : players) {
            var profile = plugin.getDatabaseManager().getProfileByUUID(p.getUniqueId());
            int points = profile != null ? profile.getCheckinPoints() : 0;
            sb.append(format
                .replace("%name%", p.getName())
                .replace("%ping%", String.valueOf(p.getPing()))
                .replace("%points%", String.valueOf(points)))
                .append("\n");
        }

        sendMessage(chatId, topicId, sb.toString(), autoDelete);
    }

    private void handleLinkCommand(long userId, String userName, String chatId, 
                                   int topicId, String text, ConfigurationSection cmd, int autoDelete) {
        String[] parts = text.split("\\s+", 2);
        if (parts.length < 2) {
            sendMessage(chatId, topicId, cmd.getString("messages.usage", "Использование: /link <ник>"), autoDelete);
            return;
        }

        String mcName = parts[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(mcName);
        if (!target.hasPlayedBefore()) {
            sendMessage(chatId, topicId, cmd.getString("messages.player_not_found", "❌ Игрок не найден"), autoDelete);
            return;
        }

        var existing = plugin.getDatabaseManager().getProfileByName(mcName);
        if (existing != null && existing.isLinked()) {
            sendMessage(chatId, topicId, cmd.getString("messages.already_linked", "❌ Уже привязан"), autoDelete);
            return;
        }

        String code = plugin.getDatabaseManager().createLinkCode(
            String.valueOf(userId), userName, mcName);

        if (code == null) {
            sendMessage(chatId, topicId, "❌ Ошибка создания кода", autoDelete);
            return;
        }

        String msg = cmd.getString("messages.success", "✅ Код: %code%")
            .replace("%code%", code);
        sendMessage(chatId, topicId, msg, autoDelete);
    }

    private void handleUnlinkCommand(long userId, String chatId, int topicId, 
                                     ConfigurationSection cmd, int autoDelete) {
        var profile = plugin.getDatabaseManager().getProfileByTelegramId(String.valueOf(userId));
        if (profile == null || !profile.isLinked()) {
            sendMessage(chatId, topicId, cmd.getString("messages.not_linked", "❌ Не привязан"), autoDelete);
            return;
        }
        sendMessage(chatId, topicId, cmd.getString("messages.confirm_in_game", "ℹ️ Введите /qqunlink в игре"), autoDelete);
    }

    // ========== ОБРАБОТКА АВТООТВЕТОВ ==========

    private boolean handleAutoResponse(long userId, String userName, String chatId, 
                                       int topicId, String text) {
        String lowerText = text.toLowerCase();

        for (CompiledRule rule : compiledRules) {
            if (!matchesRule(rule, lowerText)) continue;

            // Проверяем кулдаун для правила
            if (isOnRuleCooldown(userId, rule)) continue;

            int autoDelete = rule.autoDeleteSeconds;
            if (autoDelete == 0) autoDelete = globalAutoDeleteSeconds;

            if (!rule.randomAnswers.isEmpty()) {
                String answer = rule.randomAnswers.get(random.nextInt(rule.randomAnswers.size()));
                answer = answer
                    .replace("%player_name%", userName)
                    .replace("%user_name%", userName);
                sendMessage(chatId, topicId, answer, autoDelete);

                // Запоминаем кулдаун
                ruleCooldowns.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .put(rule.id, System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    private boolean matchesRule(CompiledRule rule, String message) {
        if (rule.exactPhrases != null) {
            for (String exact : rule.exactPhrases) {
                if (message.equals(exact.toLowerCase())) return true;
            }
        }
        if (rule.regexPatterns != null) {
            for (Pattern pattern : rule.regexPatterns) {
                if (pattern.matcher(message).find()) return true;
            }
        }
        if (rule.containsWords != null) {
            for (String word : rule.containsWords) {
                if (message.contains(word.toLowerCase())) return true;
            }
        }
        return false;
    }

    private boolean isOnRuleCooldown(long userId, CompiledRule rule) {
        Map<String, Long> playerCooldowns = ruleCooldowns.get(userId);
        if (playerCooldowns == null) return false;

        Long last = playerCooldowns.get(rule.id);
        if (last == null) return false;

        long ticksPassed = (System.currentTimeMillis() - last) / 50;
        return ticksPassed < rule.cooldownTicks;
    }

    // ========== ОТПРАВКА СООБЩЕНИЙ ==========

    private void sendMessage(String chatId, int topicId, String message, int autoDeleteSeconds) {
        if (chatId == null || chatId.isEmpty()) return;

        var config = plugin.getConfigManager().getMainConfig();
        String token = config.getString("telegram.tgbridge.token", "");
        int port = config.getInt("tgbridge.port", 45678);

        if (token.isEmpty()) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("chat_id", chatId);
        payload.put("message", message.replace("\\n", "\n"));

        // Добавляем топик если есть
        if (topicId > 0) {
            payload.put("message_thread_id", topicId);
        }

        String json = gson.toJson(payload);
        String baseUrl = "http://localhost:" + port;

        CompletableFuture.runAsync(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/send_message"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .build();

                java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());

                // Автоудаление
                if (autoDeleteSeconds > 0) {
                    try {
                        var responseJson = gson.fromJson(response.body(), Map.class);
                        Object messageIdObj = responseJson.get("message_id");
                        if (messageIdObj != null) {
                            long messageId = ((Number) messageIdObj).longValue();
                            scheduleDelete(chatId, messageId, autoDeleteSeconds, token, port);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send TG message: " + e.getMessage());
            }
        });
    }

    private void scheduleDelete(String chatId, long messageId, int delaySeconds, String token, int port) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("token", token);
                payload.put("chat_id", chatId);
                payload.put("message_id", messageId);

                String json = gson.toJson(payload);
                String baseUrl = "http://localhost:" + port;

                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/delete_message"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .build();

                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        });
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
