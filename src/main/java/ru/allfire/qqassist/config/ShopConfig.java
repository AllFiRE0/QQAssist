package ru.allfire.qqassist.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.allfire.qqassist.QQAssist;

import java.io.File;

public class ShopConfig {

    private final QQAssist plugin;
    private FileConfiguration shopConfig;
    private File shopFile;

    public ShopConfig(QQAssist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        shopFile = new File(plugin.getDataFolder(), "shop.yml");
        if (!shopFile.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
    }

    public void reload() {
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
    }

    public FileConfiguration get() {
        return shopConfig;
    }

    public boolean isEnabled() {
        return shopConfig.getBoolean("shop.enabled", true);
    }

    public boolean requireLinked() {
        return shopConfig.getBoolean("shop.require_linked", true);
    }

    public String getCurrencyName() {
        return shopConfig.getString("shop.currency_name", "очков");
    }

    public String getCurrencyIcon() {
        return shopConfig.getString("shop.currency_icon", "⭐");
    }

    public String getMessage(String path) {
        return shopConfig.getString("shop.messages." + path, "");
    }
}
