package ru.allfire.qqassist.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.allfire.qqassist.QQAssist;

public class CommandExecutor {

    public static void execute(QQAssist plugin, Player player, String command) {
        if (command == null || command.isEmpty()) return;

        command = ColorUtil.colorize(command);

        if (command.startsWith("message! ")) {
            String msg = command.substring(9);
            player.sendMessage(msg);
            return;
        }

        if (command.startsWith("broadcast! ")) {
            String msg = command.substring(11);
            Bukkit.broadcastMessage(msg);
            return;
        }

        if (command.startsWith("sound! ")) {
            String[] parts = command.substring(7).split(" ");
            if (parts.length >= 3) {
                try {
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(parts[0].toUpperCase());
                    float volume = Float.parseFloat(parts[1]);
                    float pitch = Float.parseFloat(parts[2]);
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception ignored) {}
            }
            return;
        }

        if (command.startsWith("title! ")) {
            String[] parts = command.substring(7).split("&7", 2);
            String title = parts[0];
            String subtitle = parts.length > 1 ? parts[1] : "";
            player.sendTitle(title, subtitle, 10, 70, 20);
            return;
        }

        if (command.startsWith("asConsole! ")) {
            String cmd = command.substring(12);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }

        if (command.startsWith("asPlayer! ")) {
            String cmd = command.substring(10);
            Bukkit.dispatchCommand(player, cmd);
            return;
        }

        if (command.startsWith("[cmd]")) {
            String cmd = command.replace("[cmd]", "").replace("[/cmd]", "").trim();
            Bukkit.dispatchCommand(player, cmd);
            return;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
