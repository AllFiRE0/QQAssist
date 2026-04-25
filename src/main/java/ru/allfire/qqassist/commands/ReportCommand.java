package ru.allfire.qqassist.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ReportCommand implements CommandExecutor {

    private final QQAssist plugin;
    private final Map<Player, Long> lastReportTime = new HashMap<>();

    public ReportCommand(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько игроки могут отправлять жалобы");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLang().get("report_usage"));
            sender.sendMessage(plugin.getLang().get("report_categories"));
            return true;
        }

        Long last = lastReportTime.get(player);
        long cooldown = plugin.getConfigManager().getMainConfig().getLong("reports.cooldown_seconds", 60) * 1000L;

        if (last != null && System.currentTimeMillis() - last < cooldown) {
            sender.sendMessage(plugin.getLang().get("report_cooldown",
                "time", String.valueOf((cooldown - (System.currentTimeMillis() - last)) / 1000) + "с"));
            return true;
        }

        String targetName = args[0];

        if (targetName.equalsIgnoreCase(player.getName())) {
            sender.sendMessage(plugin.getLang().get("report_cant_self"));
            return true;
        }

        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        if (!offlineTarget.hasPlayedBefore()) {
            sender.sendMessage(plugin.getLang().get("player_not_found"));
            return true;
        }

        String category = args[1].toLowerCase();
        String comment = args.length > 2 ?
            String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";

        long reportId = plugin.getDatabaseManager().saveReport(
            player.getUniqueId(),
            player.getName(),
            offlineTarget.getUniqueId(),
            targetName,
            category,
            comment,
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getY(),
            player.getLocation().getZ()
        );

        lastReportTime.put(player, System.currentTimeMillis());

        player.sendMessage(plugin.getLang().get("report_success", "id", String.valueOf(reportId)));

        plugin.getTelegramManager().sendToTelegram("reports",
            "🚩 Новая жалоба #" + reportId + "\n" +
            "👤 От: " + player.getName() + "\n" +
            "👤 На: " + targetName + "\n" +
            "📋 " + category + "\n" +
            "💬 " + (comment.isEmpty() ? "Без комментария" : comment));

        return true;
    }
}
