package ru.allfire.qqassist.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;

import java.util.UUID;

public class QQLinkCommand implements CommandExecutor {

    private final QQAssist plugin;

    public QQLinkCommand(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько игроки могут привязывать аккаунт");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getLang().get("link_usage"));
            return true;
        }

        String code = args[0];

        PlayerProfile existing = plugin.getDatabaseManager().getProfileByUUID(player.getUniqueId());
        if (existing != null && existing.isLinked()) {
            sender.sendMessage(plugin.getLang().get("link_already"));
            return true;
        }

        String result = plugin.getDatabaseManager().verifyLinkCode(code);
        if (result == null) {
            sender.sendMessage(plugin.getLang().get("link_invalid_code"));
            return true;
        }

        String[] parts = result.split(":");
        String tgId = parts[0];
        String tgName = parts.length > 1 ? parts[1] : "";

        PlayerProfile profile = existing;
        if (profile == null) {
            profile = new PlayerProfile(0, player.getUniqueId(), tgId, player.getName(), tgName,
                true, 0, 0, 0, 0, 0, 0, System.currentTimeMillis());
        } else {
            profile.setTelegramId(tgId);
            profile.setTelegramName(tgName);
            profile.setMinecraftName(player.getName());
            profile.setLinked(true);
            profile.setLinkedAt(System.currentTimeMillis());
        }

        plugin.getDatabaseManager().saveProfile(profile);
        plugin.getDatabaseManager().logActivity(player.getUniqueId(), player.getName(),
            "link", "Linked to Telegram: " + tgName);

        sender.sendMessage(plugin.getLang().get("link_success"));

        return true;
    }
}
