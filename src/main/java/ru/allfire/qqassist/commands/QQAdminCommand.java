package ru.allfire.qqassist.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;
import ru.allfire.qqassist.database.models.Report;
import ru.allfire.qqassist.utils.TimeUtil;

import java.util.ArrayList;
import java.util.List;

public class QQAdminCommand implements CommandExecutor, TabCompleter {

    private final QQAssist plugin;

    public QQAdminCommand(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("qqassist.admin")) {
            sender.sendMessage(plugin.getLang().get("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ========== POINTS ==========
            case "points" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /qqadmin points <reset|set|add|take> <игрок> [количество]");
                    return true;
                }
                if (!sender.hasPermission("qqassist.admin.points")) {
                    sender.sendMessage(plugin.getLang().get("no_permission"));
                    return true;
                }

                String action = args[1].toLowerCase();
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                if (!target.hasPlayedBefore()) {
                    sender.sendMessage(plugin.getLang().get("player_not_found"));
                    return true;
                }

                PlayerProfile profile = plugin.getDatabaseManager().getProfileByUUID(target.getUniqueId());
                if (profile == null) {
                    profile = new PlayerProfile(0, target.getUniqueId(), null, target.getName(), null,
                        false, 0, 0, 0, 0, 0, 0, 0);
                }

                switch (action) {
                    case "reset" -> {
                        profile.setCheckinPoints(0);
                        plugin.getDatabaseManager().saveProfile(profile);
                        sender.sendMessage(plugin.getLang().get("admin_points_reset",
                            "player", target.getName()));
                    }
                    case "set" -> {
                        if (args.length < 4) {
                            sender.sendMessage("§cУкажите количество: /qqadmin points set <игрок> <количество>");
                            return true;
                        }
                        int amount = Integer.parseInt(args[3]);
                        profile.setCheckinPoints(amount);
                        plugin.getDatabaseManager().saveProfile(profile);
                        sender.sendMessage(plugin.getLang().get("admin_points_set",
                            "points", String.valueOf(amount),
                            "player", target.getName()));
                    }
                    case "add" -> {
                        if (args.length < 4) {
                            sender.sendMessage("§cУкажите количество: /qqadmin points add <игрок> <количество>");
                            return true;
                        }
                        int amount = Integer.parseInt(args[3]);
                        profile.addCheckinPoints(amount);
                        plugin.getDatabaseManager().saveProfile(profile);
                        sender.sendMessage(plugin.getLang().get("admin_points_add",
                            "points", String.valueOf(amount),
                            "player", target.getName()));
                    }
                    case "take" -> {
                        if (args.length < 4) {
                            sender.sendMessage("§cУкажите количество: /qqadmin points take <игрок> <количество>");
                            return true;
                        }
                        int amount = Integer.parseInt(args[3]);
                        profile.removeCheckinPoints(amount);
                        plugin.getDatabaseManager().saveProfile(profile);
                        sender.sendMessage(plugin.getLang().get("admin_points_take",
                            "points", String.valueOf(amount),
                            "player", target.getName()));
                    }
                    default -> sender.sendMessage("§cДействие: reset, set, add, take");
                }
            }

            // ========== REPORTS ==========
            case "reports" -> {
                if (!sender.hasPermission("qqassist.admin.reports")) {
                    sender.sendMessage(plugin.getLang().get("no_permission"));
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("view")) {
                    if (args.length < 3) {
                        sender.sendMessage("§c/qqadmin reports view <id>");
                        return true;
                    }
                    long id = Long.parseLong(args[2]);
                    Report report = plugin.getDatabaseManager().getReport(id);
                    if (report == null) {
                        sender.sendMessage("§cЖалоба не найдена");
                        return true;
                    }
                    sendReportDetail(sender, report);
                    return true;
                }

                if (args.length >= 3 && args[1].equalsIgnoreCase("take")) {
                    long id = Long.parseLong(args[2]);
                    if (sender instanceof Player admin) {
                        plugin.getDatabaseManager().assignReport(id, admin.getUniqueId(), admin.getName());
                        sender.sendMessage("§aЖалоба #" + id + " взята в обработку");
                    }
                    return true;
                }

                if (args.length >= 3 && args[1].equalsIgnoreCase("close")) {
                    long id = Long.parseLong(args[2]);
                    plugin.getDatabaseManager().closeReport(id);
                    sender.sendMessage("§aЖалоба #" + id + " закрыта");
                    return true;
                }

                String filter = args.length >= 2 ? args[1] : "pending";
                List<Report> reports = plugin.getDatabaseManager().getReports(filter);

                sender.sendMessage("§8══════ §a§lЖАЛОБЫ §8(" + filter + ") §8══════");
                for (Report r : reports) {
                    String statusIcon = switch (r.getStatus()) {
                        case "pending" -> "§c●";
                        case "processing" -> "§e●";
                        case "closed" -> "§a●";
                        default -> "§7●";
                    };
                    sender.sendMessage(statusIcon + " §f#" + r.getId() + " §7" +
                        r.getReporterName() + " → " + r.getTargetName() +
                        " §8[" + r.getCategory() + "]");
                }
                sender.sendMessage("§7/qqadmin reports view <id> — детали");
                sender.sendMessage("§8══════════════════════════");
            }

            // ========== PUNISH ==========
            case "punish" -> {
                if (!sender.hasPermission("qqassist.admin.punish")) {
                    sender.sendMessage(plugin.getLang().get("no_permission"));
                    return true;
                }
                if (args.length < 5) {
                    sender.sendMessage("§c/qqadmin punish <тип> <игрок> <время> <причина>");
                    sender.sendMessage("§7Типы: warn, mute, ban, jail");
                    return true;
                }
                String type = args[1].toLowerCase();
                String playerName = args[2];
                String time = args[3];
                String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));

                var punishments = plugin.getConfigManager().getMainConfig()
                    .getConfigurationSection("reports.punishments." + type);
                if (punishments == null) {
                    sender.sendMessage("§cНеизвестный тип наказания: " + type);
                    return true;
                }

                String cmd = punishments.getString("command", "")
                    .replace("%player%", playerName)
                    .replace("%time%", time)
                    .replace("%reason%", reason);

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

                sender.sendMessage(plugin.getLang().get("admin_punish_success",
                    "player", playerName, "type", type, "time", time));
            }

            default -> sendAdminHelp(sender);
        }

        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§8══════ §c§lQQADMIN §8══════");
        sender.sendMessage("§c/qqadmin points <reset|set|add|take> <игрок> [кол-во]");
        sender.sendMessage("§c/qqadmin reports [pending|processing|closed|all]");
        sender.sendMessage("§c/qqadmin reports view <id>");
        sender.sendMessage("§c/qqadmin reports take <id>");
        sender.sendMessage("§c/qqadmin reports close <id>");
        sender.sendMessage("§c/qqadmin punish <тип> <игрок> <время> <причина>");
        sender.sendMessage("§8═══════════════════════");
    }

    private void sendReportDetail(CommandSender sender, Report report) {
        sender.sendMessage("§8══════ §aЖАЛОБА #" + report.getId() + " §8══════");
        sender.sendMessage("§7Статус: " + getStatusText(report.getStatus()));
        sender.sendMessage("§7От: §f" + report.getReporterName());
        sender.sendMessage("§7На: §f" + report.getTargetName());
        sender.sendMessage("§7Категория: §f" + report.getCategory());
        sender.sendMessage("§7Мир: §f" + report.getWorld() +
            " §7X:§f" + String.format("%.0f", report.getX()) +
            " §7Y:§f" + String.format("%.0f", report.getY()) +
            " §7Z:§f" + String.format("%.0f", report.getZ()));
        if (report.getComment() != null && !report.getComment().isEmpty()) {
            sender.sendMessage("§7Комментарий: §f" + report.getComment());
        }
        sender.sendMessage("§7Создана: §f" + TimeUtil.formatTime(System.currentTimeMillis() - report.getCreatedAt()) + " назад");
        if (report.getAssigneeName() != null) {
            sender.sendMessage("§7Обрабатывает: §f" + report.getAssigneeName());
        }
        sender.sendMessage("§8═══════════════════════════");
    }

    private String getStatusText(String status) {
        return switch (status) {
            case "pending" -> "§cНовая";
            case "processing" -> "§eВ работе";
            case "closed" -> "§aЗакрыта";
            default -> "§7" + status;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("qqassist.admin.points")) options.add("points");
            if (sender.hasPermission("qqassist.admin.reports")) options.add("reports");
            if (sender.hasPermission("qqassist.admin.punish")) options.add("punish");
            for (String s : options) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "points" -> {
                    for (String s : List.of("reset", "set", "add", "take")) {
                        if (s.startsWith(args[1].toLowerCase())) completions.add(s);
                    }
                }
                case "reports" -> {
                    for (String s : List.of("view", "take", "close", "pending", "processing", "closed", "all")) {
                        if (s.startsWith(args[1].toLowerCase())) completions.add(s);
                    }
                }
                case "punish" -> {
                    for (String s : List.of("warn", "mute", "ban", "jail")) {
                        if (s.startsWith(args[1].toLowerCase())) completions.add(s);
                    }
                }
            }
        }

        return completions;
    }
}
