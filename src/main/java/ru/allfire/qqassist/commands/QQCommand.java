package ru.allfire.qqassist.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import ru.allfire.qqassist.QQAssist;

import java.util.ArrayList;
import java.util.List;

public class QQCommand implements CommandExecutor, TabCompleter {

    private final QQAssist plugin;

    public QQCommand(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("qqassist.admin")) {
                    sender.sendMessage(plugin.getLang().get("no_permission"));
                    return true;
                }
                long start = System.currentTimeMillis();
                plugin.reload();
                sender.sendMessage(plugin.getLang().get("reload_success",
                    "time", String.valueOf(System.currentTimeMillis() - start)));
            }

            case "help" -> sendHelp(sender);

            case "info" -> {
                sender.sendMessage("§8[§aQQAssist§8] §fVersion: §a1.0.0");
                sender.sendMessage("§8[§aQQAssist§8] §fAuthor: §aAllF1RE");
                sender.sendMessage("§8[§aQQAssist§8] §fRules loaded: §a" + plugin.getChatBotManager().getRuleCount());
                sender.sendMessage("§8[§aQQAssist§8] §fCommands: §a/qqassist help");
            }

            default -> sender.sendMessage(plugin.getLang().get("unknown_command"));
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8══════ §a§lQQAssist §8══════");
        sender.sendMessage("§a/qqassist reload §7- Перезагрузить конфигурацию");
        sender.sendMessage("§a/qqassist info §7- Информация о плагине");
        sender.sendMessage("§a/checkin §7- Ежедневная отметка");
        sender.sendMessage("§a/qqpoints §7- Проверить очки");
        sender.sendMessage("§a/qqlink <код> §7- Привязать Telegram");
        sender.sendMessage("§a/qqunlink §7- Отвязать Telegram");
        sender.sendMessage("§a/qqshop §7- Информация о магазине");
        sender.sendMessage("§a/report <игрок> <категория> §7- Пожаловаться");
        if (sender.hasPermission("qqassist.admin")) {
            sender.sendMessage("§c/qqadmin §7- Админ-команды");
        }
        sender.sendMessage("§8═══════════════════════");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "info"));
            if (sender.hasPermission("qqassist.admin")) {
                options.add("reload");
            }
            for (String s : options) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        }
        return completions;
    }
}
