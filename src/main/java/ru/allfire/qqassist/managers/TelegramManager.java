package ru.allfire.qqassist.managers;

import com.google.gson.Gson;
import ru.allfire.qqassist.QQAssist;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TelegramManager {

    private final QQAssist plugin;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private boolean enabled;
    private String token;
    private int port;
    private Map<String, String> chats;

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
            var chatsSection = tg.getConfigurationSection("chats");
            if (chatsSection != null) {
                chats = new java.util.HashMap<>();
                for (String key : chatsSection.getKeys(false)) {
                    chats.put(key, chatsSection.getString(key));
                }
            }
        }
    }

    public void sendToTelegram(String chatName, String message) {
        if (!enabled || token.isEmpty()) return;

        String chatId = chats != null ? chats.get(chatName) : null;
        if (chatId == null || chatId.isEmpty()) return;

        String baseUrl = "http://localhost:" + port;

        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("token", token);
        payload.put("chat_id", chatId);
        payload.put("message", message);

        String json = gson.toJson(payload);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/send_telegram"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Telegram message: " + e.getMessage());
            }
        });
    }

    public boolean isEnabled() { return enabled; }
}
