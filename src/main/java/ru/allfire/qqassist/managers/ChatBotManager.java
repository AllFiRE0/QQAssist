package ru.allfire.qqassist.managers;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.utils.CommandExecutor;
import ru.allfire.qqassist.utils.ConditionParser;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatBotManager implements Listener {

    private final QQAssist plugin;
    private final List<Rule> rules = new ArrayList<>();
    private final Map<UUID, Long> lastTriggerTime = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> ruleCooldowns = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private boolean enabled;
    private boolean debug;
    private String globalPermission;
    private long globalCooldownTicks;
    private boolean ignoreSelf;
    private boolean logTriggers;
    private int maxMessageLength;
    private List<String> excludedPermissions;
    private List<String> excludedPlayers;

    public ChatBotManager(QQAssist plugin) {
        this.plugin = plugin;
    }

    public void loadRules() {
        rules.clear();

        var config = plugin.getConfigManager().getMainConfig().getConfigurationSection("chatbot");
        if (config == null) return;

        enabled = config.getBoolean("enabled", true);
        debug = plugin.getConfigManager().isDebug();
        globalPermission = config.getString("permission_usage", "");
        globalCooldownTicks = config.getLong("global_cooldown_ticks", 40);
        ignoreSelf = config.getBoolean("ignore_self", true);
        logTriggers = config.getBoolean("log_triggers", true);
        maxMessageLength = config.getInt("max_message_length", 256);
        excludedPermissions = config.getStringList("exclusions.permissions");
        excludedPlayers = config.getStringList("exclusions.players");

        var responsesConfig = plugin.getResponsesConfig().get();
        ConfigurationSection rulesSection = responsesConfig.getConfigurationSection("rules");
        if (rulesSection == null) return;

        for (String ruleId : rulesSection.getKeys(false)) {
            ConfigurationSection rs = rulesSection.getConfigurationSection(ruleId);
            if (rs == null) continue;

            Rule rule = new Rule(ruleId);
            rule.delayTicks = rs.getLong("delay_ticks", 0);
            rule.permission = rs.getString("permission", "");
            rule.symbol = rs.getString("symbol", "");
            rule.condition = rs.getString("condition", "");
            rule.priority = rs.getInt("priority", 50);
            rule.chance = rs.getInt("chance", 100);
            rule.cooldownTicks = rs.getLong("cooldown_ticks", 0);
            rule.tgChat = rs.getString("tg_chat", "");

            ConfigurationSection qs = rs.getConfigurationSection("questions");
            if (qs != null) {
                rule.exactPhrases = qs.getStringList("exact");
                rule.regexPatterns = qs.getStringList("regex");
                rule.containsWords = qs.getStringList("contains");
            }

            rule.answers = rs.getStringList("answers");
            rule.randomAnswers = rs.getStringList("random_answers");

            for (String regex : rule.regexPatterns) {
                rule.compiledPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }

            rules.add(rule);
        }

        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));

        if (debug) {
            plugin.getLogger().info("Loaded " + rules.size() + " rules");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();

        if (isExcluded(player)) return;
        if (!globalPermission.isEmpty() && !player.hasPermission(globalPermission)) return;

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (message.length() > maxMessageLength) return;

        if (debug) {
            plugin.getLogger().info("[QQAssist] " + player.getName() + ": " + message);
        }

        Long lastGlobal = lastTriggerTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        if (lastGlobal != null) {
            long ticksPassed = (now - lastGlobal) / 50;
            if (ticksPassed < globalCooldownTicks) return;
        }

        String lowerMessage = message.toLowerCase();

        for (Rule rule : rules) {
            if (!rule.symbol.isEmpty()) {
                if (!message.startsWith(rule.symbol)) continue;
                lowerMessage = message.substring(rule.symbol.length()).toLowerCase();
            }

            if (!matches(rule, lowerMessage)) continue;

            if (!rule.permission.isEmpty() && !player.hasPermission(rule.permission)) continue;

            if (!rule.condition.isEmpty()) {
                if (!ConditionParser.evaluate(player, rule.condition)) continue;
            }

            Map<String, Long> playerCooldowns = ruleCooldowns.computeIfAbsent(
                player.getUniqueId(), k -> new ConcurrentHashMap<>()
            );

            Long lastRule = playerCooldowns.get(rule.id);
            if (lastRule != null && rule.cooldownTicks > 0) {
                long ticksPassed = (now - lastRule) / 50;
                if (ticksPassed < rule.cooldownTicks) continue;
            }

            if (rule.chance < 100 && random.nextInt(100) >= rule.chance) continue;

            if (logTriggers) {
                plugin.getLogger().info("[QQAssist] " + player.getName() + " → " + rule.id);
            }

            executeCommands(player, message, rule);

            lastTriggerTime.put(player.getUniqueId(), now);
            playerCooldowns.put(rule.id, now);

            break;
        }
    }

    private boolean matches(Rule rule, String message) {
        for (String exact : rule.exactPhrases) {
            if (message.equals(exact.toLowerCase())) return true;
        }

        for (Pattern pattern : rule.compiledPatterns) {
            if (pattern.matcher(message).find()) return true;
        }

        for (String word : rule.containsWords) {
            if (message.contains(word.toLowerCase())) return true;
        }

        return false;
    }

    private void executeCommands(Player player, String message, Rule rule) {
        for (String cmd : rule.answers) {
            executeCommand(player, message, cmd, rule.delayTicks);
        }

        if (!rule.randomAnswers.isEmpty()) {
            String randomCmd = rule.randomAnswers.get(random.nextInt(rule.randomAnswers.size()));
            executeCommand(player, message, randomCmd, rule.delayTicks);
        }

        if (!rule.tgChat.isEmpty()) {
            plugin.getTelegramManager().sendToTelegram(rule.tgChat, "Ответ бота на: " + message);
        }
    }

    private void executeCommand(Player player, String message, String cmd, long delayTicks) {
        String processed = cmd
            .replace("%player_name%", player.getName())
            .replace("%player_uuid%", player.getUniqueId().toString())
            .replace("%player_world%", player.getWorld().getName())
            .replace("%message%", message);

        Runnable task = () -> CommandExecutor.execute(plugin, player, processed);

        if (delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        } else {
            task.run();
        }
    }

    private boolean isExcluded(Player player) {
        for (String perm : excludedPermissions) {
            if (player.hasPermission(perm)) return true;
        }
        return excludedPlayers.contains(player.getName());
    }

    public int getRuleCount() { return rules.size(); }

    private static class Rule {
        final String id;
        long delayTicks;
        String permission;
        String symbol;
        String condition;
        int priority;
        int chance;
        long cooldownTicks;
        String tgChat;
        List<String> exactPhrases = new ArrayList<>();
        List<String> regexPatterns = new ArrayList<>();
        List<String> containsWords = new ArrayList<>();
        List<Pattern> compiledPatterns = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        List<String> randomAnswers = new ArrayList<>();

        Rule(String id) { this.id = id; }
    }
}
