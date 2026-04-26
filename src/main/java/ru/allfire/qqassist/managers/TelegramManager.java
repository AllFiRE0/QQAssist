package ru.allfire.qqassist.managers;

import com.google.gson.Gson;
import ru.allfire.qqassist.QQAssist;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TelegramManager {

    private final QQAssist plugin;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private boolean enabled;
    private String token;
    private int port;
    private int defaultAutoDeleteSeconds;
    private Map<String, ChatConfig> chats;

    public TelegramManager(QQAssist plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        var config = plugin.getConfigManager().getMainConfig();
        var tg = config.getConfigurationSection("telegram");
        enabled = tg != null && tg.getBoolean("enabled", false);
        
        if (enabled) {
            token = tg.getString("tgbridge.token", "");
            port = tg.getInt("tgbridge.port", 45678);
            defaultAutoDeleteSeconds = tg.getInt("settings.auto_delete_seconds", 0);

            chats = new HashMap<>();
            var chatsSection = tg.getConfigurationSection("chats");
            if (chatsSection != null) {
                for (String key : chatsSection.getKeys(false)) {
                    var chatSection = chatsSection.getConfigurationSection(key);
                    if (chatSection != null) {
                        String chatId = chatSection.getString("chat_id", "");
                        int topicId = chatSection.getInt("topic_id", 0);
                        if (!chatId.isEmpty()) {
                            chats.put(key, new ChatConfig(chatId, topicId));
                        }
                    }
                }
            }
        }
    }

    public void sendToTelegram(String chatName, String message, int autoDeleteSeconds) {
        if (!enabled || token.isEmpty()) return;

        ChatConfig chatConfig = chats.get(chatName);
        if (chatConfig == null || chatConfig.chatId.isEmpty()) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("chat_id", chatConfig.chatId);
        payload.put("message", message);

        if (chatConfig.topicId > 0) {
            payload.put("message_thread_id", chatConfig.topicId);
        }

        String json = gson.toJson(payload);
        String baseUrl = "http://localhost:" + port;

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/send_message"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (autoDeleteSeconds > 0) {
                    try {
                        var responseJson = gson.fromJson(response.body(), Map.class);
                        Object messageId = responseJson.get("message_id");
                        if (messageId != null) {
                            long msgId = ((Number) messageId).longValue();
                            scheduleDelete(chatConfig.chatId, msgId, autoDeleteSeconds);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Telegram message: " + e.getMessage());
            }
        });
    }

    public void sendToTelegram(String chatName, String message) {
        sendToTelegram(chatName, message, defaultAutoDeleteSeconds);
    }

    private void scheduleDelete(String chatId, long messageId, int delaySeconds) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);

                Map<String, Object> payload = new HashMap<>();
                payload.put("token", token);
                payload.put("chat_id", chatId);
                payload.put("message_id", messageId);

                String json = gson.toJson(payload);
                String baseUrl = "http://localhost:" + port;

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/delete_message"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        });
    }

    public boolean isEnabled() { return enabled; }

    private static class ChatConfig {
        final String chatId;
        final int topicId;

        ChatConfig(String chatId, int topicId) {
            this.chatId = chatId;
            this.topicId = topicId;
        }
    }
}
