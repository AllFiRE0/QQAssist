package ru.allfire.qqassist.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.allfire.qqassist.QQAssist;

import java.io.File;

public class ResponsesConfig {

    private final QQAssist plugin;
    private FileConfiguration responsesConfig;
    private File responsesFile;

    public ResponsesConfig(QQAssist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        responsesFile = new File(plugin.getDataFolder(), "responses.yml");
        if (!responsesFile.exists()) {
            plugin.saveResource("responses.yml", false);
        }
        responsesConfig = YamlConfiguration.loadConfiguration(responsesFile);
    }

    public void reload() {
        responsesConfig = YamlConfiguration.loadConfiguration(responsesFile);
    }

    public FileConfiguration get() {
        return responsesConfig;
    }
}
