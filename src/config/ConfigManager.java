package ru.allfire.qqassist.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.allfire.qqassist.QQAssist;

import java.io.File;

public class ConfigManager {
    
    private final QQAssist plugin;
    private FileConfiguration mainConfig;
    private File mainConfigFile;
    
    public ConfigManager(QQAssist plugin) {
        this.plugin = plugin;
    }
    
    public void loadAll() {
        mainConfigFile = new File(plugin.getDataFolder(), "config.yml");
        if (!mainConfigFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
    }
    
    public void reloadAll() {
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
    }
    
    public FileConfiguration getMainConfig() {
        return mainConfig;
    }
    
    public boolean isDebug() {
        return mainConfig.getBoolean("debug", false);
    }
}
