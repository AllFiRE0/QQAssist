package ru.allfire.qqassist.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;
import ru.allfire.qqassist.utils.TimeUtil;

import java.util.Random;

public class CheckinCommand implements CommandExecutor {

    private final QQAssist plugin;
    private final Random random = new Random();

    public CheckinCommand(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько игроки могут выполнять check-in");
            return true;
        }

        PlayerProfile profile = plugin.getDatabaseManager().getProfileByUUID(player.getUniqueId());

        if (profile == null || !profile.isLinked()) {
            sender.sendMessage(plugin.getLang().get("checkin_not_linked"));
            return true;
        }

        long cooldownMs = 86400000; // 24 часа
        if (player.hasPermission("qqassist.bypass.checkin_cooldown")) {
            cooldownMs = 0;
        }

        long timeSinceLast = System.currentTimeMillis() - profile.getLastCheckin();
        if (timeSinceLast < cooldownMs) {
            sender.sendMessage(plugin.getLang().get("checkin_cooldown",
                "time", TimeUtil.formatCooldown(cooldownMs - timeSinceLast)));
            return true;
        }

        var config = plugin.getConfigManager().getMainConfig();
        int min = config.getInt("report_rewards.min", 1);
        int max = config.getInt("report_rewards.max", 30);
        int points = random.nextInt(max - min + 1) + min;

        profile.addCheckinPoints(points);
        profile.setLastCheckin(System.currentTimeMillis());
        profile.incrementStreak();

        plugin.getDatabaseManager().saveProfile(profile);
        plugin.getDatabaseManager().logActivity(player.getUniqueId(), player.getName(),
            "checkin", "+" + points + " points");

        sender.sendMessage(plugin.getLang().get("checkin_success",
            "points", String.valueOf(points),
            "balance", String.valueOf(profile.getCheckinPoints())));

        return true;
    }
}
