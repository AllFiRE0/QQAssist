package ru.allfire.qqassist;

import org.bukkit.plugin.java.JavaPlugin;
import ru.allfire.qqassist.commands.*;
import ru.allfire.qqassist.config.ConfigManager;
import ru.allfire.qqassist.config.Lang;
import ru.allfire.qqassist.config.ShopConfig;
import ru.allfire.qqassist.config.ResponsesConfig;
import ru.allfire.qqassist.database.DatabaseManager;
import ru.allfire.qqassist.managers.ChatBotManager;
import ru.allfire.qqassist.managers.TelegramManager;
import ru.allfire.qqassist.miniapp.MiniAppServer;
import ru.allfire.qqassist.placeholder.QQExpansion;

public final class QQAssist extends JavaPlugin {
    
    private static QQAssist instance;
    
    private ConfigManager configManager;
    private Lang lang;
    private ShopConfig shopConfig;
    private ResponsesConfig responsesConfig;
    private DatabaseManager databaseManager;
    private ChatBotManager chatBotManager;
    private TelegramManager telegramManager;
    private MiniAppServer miniAppServer;
    
    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();
        
        getLogger().info("§8[§aQQAssist§8] §7Starting...");
        
        // Конфиги
        configManager = new ConfigManager(this);
        configManager.loadAll();
        
        lang = new Lang(this);
        lang.load();
        
        shopConfig = new ShopConfig(this);
        shopConfig.load();
        
        responsesConfig = new ResponsesConfig(this);
        responsesConfig.load();
        
        // База данных
        databaseManager = new DatabaseManager(this);
        databaseManager.init();
        
        // Менеджеры
        chatBotManager = new ChatBotManager(this);
        chatBotManager.loadRules();
        getServer().getPluginManager().registerEvents(chatBotManager, this);
        
        telegramManager = new TelegramManager(this);
        
        // Mini App HTTP сервер
        if (configManager.getMainConfig().getBoolean("miniapp.http_server.enabled", true)) {
            miniAppServer = new MiniAppServer(this);
            miniAppServer.start();
        }
        
        // Команды
        registerCommands();
        
        // PlaceholderAPI
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new QQExpansion(this).register();
            getLogger().info("§8[§aQQAssist§8] §aPlaceholderAPI expansion registered");
        }
        
        getLogger().info("§8[§aQQAssist§8] §aEnabled in §f" + (System.currentTimeMillis() - start) + "ms");
        getLogger().info("§8[§aQQAssist§8] §7Author: §fAllF1RE");
    }
    
    @Override
    public void onDisable() {
        if (miniAppServer != null) {
            miniAppServer.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("§8[§aQQAssist§8] §cDisabled");
    }
    
    private void registerCommands() {
        var qq = getCommand("qqassist");
        if (qq != null) {
            var cmd = new QQCommand(this);
            qq.setExecutor(cmd);
            qq.setTabCompleter(cmd);
        }
        
        var report = getCommand("report");
        if (report != null) report.setExecutor(new ReportCommand(this));
        
        var checkin = getCommand("checkin");
        if (checkin != null) checkin.setExecutor(new CheckinCommand(this));
        
        var qqpoints = getCommand("qqpoints");
        if (qqpoints != null) qqpoints.setExecutor(new QQPointsCommand(this));
        
        var qqshop = getCommand("qqshop");
        if (qqshop != null) qqshop.setExecutor(new QQShopCommand(this));
        
        var qqlink = getCommand("qqlink");
        if (qqlink != null) qqlink.setExecutor(new QQLinkCommand(this));
        
        var qqunlink = getCommand("qqunlink");
        if (qqunlink != null) qqunlink.setExecutor(new QQUnlinkCommand(this));
        
        var qqadmin = getCommand("qqadmin");
        if (qqadmin != null) {
            var cmd = new QQAdminCommand(this);
            qqadmin.setExecutor(cmd);
            qqadmin.setTabCompleter(cmd);
        }
    }
    
    public void reload() {
        configManager.reloadAll();
        lang.load();
        shopConfig.load();
        responsesConfig.load();
        chatBotManager.loadRules();
        getLogger().info("§8[§aQQAssist§8] §aConfiguration reloaded");
    }
    
    public static QQAssist getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public Lang getLang() { return lang; }
    public ShopConfig getShopConfig() { return shopConfig; }
    public ResponsesConfig getResponsesConfig() { return responsesConfig; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ChatBotManager getChatBotManager() { return chatBotManager; }
    public TelegramManager getTelegramManager() { return telegramManager; }
    public MiniAppServer getMiniAppServer() { return miniAppServer; }
}
