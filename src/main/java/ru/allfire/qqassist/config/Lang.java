package ru.allfire.qqassist.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.utils.ColorUtil;

import java.io.File;

public class Lang {

    private final QQAssist plugin;
    private FileConfiguration langConfig;
    private String prefix;

    public Lang(QQAssist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(file);
        prefix = colorize(langConfig.getString("prefix", "&8[&a&lQQ&8] &7»"));
    }

    public String get(String path) {
        String msg = langConfig.getString("messages." + path, "");
        if (msg == null || msg.isEmpty()) return "";
        return colorize(msg.replace("%prefix%", prefix));
    }

    public String get(String path, String... replacements) {
        String msg = get(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                msg = msg.replace("%" + replacements[i] + "%", replacements[i + 1]);
            }
        }
        return msg;
    }

    public String getPrefix() {
        return prefix;
    }

    public static String colorize(String text) {
        if (text == null) return "";
        return ColorUtil.colorize(text);
    }
}
