package ru.allfire.qqassist.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import ru.allfire.qqassist.QQAssist;

public class QQShopCommand implements CommandExecutor {

    private final QQAssist plugin;

    public QQShopCommand(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        var shop = plugin.getShopConfig().get().getConfigurationSection("shop");
        if (shop == null) {
            sender.sendMessage("§cМагазин не настроен");
            return true;
        }

        sender.sendMessage("§8══════ §a§lМАГАЗИН §8══════");
        sender.sendMessage("§7" + shop.getString("description", ""));

        var categories = shop.getConfigurationSection("categories");
        if (categories != null) {
            for (String catKey : categories.getKeys(false)) {
                var cat = categories.getConfigurationSection(catKey);
                if (cat == null) continue;
                sender.sendMessage("§e" + cat.getString("icon", "") + " " + cat.getString("name", catKey));

                var items = cat.getConfigurationSection("items");
                if (items != null) {
                    for (String itemKey : items.getKeys(false)) {
                        var item = items.getConfigurationSection(itemKey);
                        if (item == null) continue;
                        int cost = item.getInt("cost", 0);
                        String name = item.getString("name", itemKey);
                        sender.sendMessage("  §f" + name + " §7- §a" + cost + " ⭐");
                    }
                }
            }
        }

        sender.sendMessage("§7Покупайте товары через Telegram Mini App!");
        sender.sendMessage("§8═══════════════════════");

        return true;
    }
}
