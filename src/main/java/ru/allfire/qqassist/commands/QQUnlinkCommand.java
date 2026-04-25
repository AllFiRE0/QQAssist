package ru.allfire.qqassist.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;

public class QQUnlinkCommand implements CommandExecutor {

    private final QQAssist plugin;

    public QQUnlinkCommand(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cТолько игроки могут отвязывать аккаунт");
            return true;
        }

        PlayerProfile profile = plugin.getDatabaseManager().getProfileByUUID(player.getUniqueId());

        if (profile == null || !profile.isLinked()) {
            sender.sendMessage("§cАккаунт не привязан");
            return true;
        }

        plugin.getDatabaseManager().logActivity(player.getUniqueId(), player.getName(),
            "unlink", "Unlinked from Telegram: " + profile.getTelegramName());

        profile.setLinked(false);
        profile.setTelegramId(null);
        profile.setTelegramName(null);
        plugin.getDatabaseManager().saveProfile(profile);

        sender.sendMessage(plugin.getLang().get("unlink_success"));

        return true;
    }
}
