package ru.allfire.qqassist.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;

public class QQPointsCommand implements CommandExecutor {

    private final QQAssist plugin;

    public QQPointsCommand(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        Player target;

        if (args.length > 0 && sender.hasPermission("qqassist.admin.points")) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.getLang().get("player_not_found"));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cИспользование: /qqpoints <игрок>");
                return true;
            }
            target = player;
        }

        PlayerProfile profile = plugin.getDatabaseManager().getProfileByUUID(target.getUniqueId());

        int points = profile != null ? profile.getCheckinPoints() : 0;
        int streak = profile != null ? profile.getCheckinStreak() : 0;
        boolean linked = profile != null && profile.isLinked();

        if (target.equals(sender)) {
            sender.sendMessage(plugin.getLang().get("points_balance",
                "points", String.valueOf(points)));
            sender.sendMessage("§7Серия check-in: §f" + streak + " §7дней");
        } else {
            sender.sendMessage("§8[§aQQ§8] §7Очки игрока §f" + target.getName() + "§7: §f" + points);
        }

        return true;
    }
}
