package ru.allfire.qqassist.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.allfire.qqassist.QQAssist;
import ru.allfire.qqassist.database.models.PlayerProfile;

public class QQExpansion extends PlaceholderExpansion {

    private final QQAssist plugin;

    public QQExpansion(QQAssist plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "qqassist";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AllF1RE";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return "";

        PlayerProfile profile = plugin.getDatabaseManager().getProfileByUUID(offlinePlayer.getUniqueId());

        return switch (params.toLowerCase()) {
            case "linked" -> {
                if (profile == null || !profile.isLinked()) yield "no";
                yield "yes";
            }
            case "points" -> profile != null ? String.valueOf(profile.getCheckinPoints()) : "0";
            case "streak" -> profile != null ? String.valueOf(profile.getCheckinStreak()) : "0";
            case "total_checkins" -> profile != null ? String.valueOf(profile.getTotalCheckins()) : "0";
            case "telegram_name" -> {
                if (profile == null || !profile.isLinked()) yield "";
                yield profile.getTelegramName() != null ? profile.getTelegramName() : "";
            }
            case "gifted_points" -> profile != null ? String.valueOf(profile.getGiftedPoints()) : "0";
            case "received_points" -> profile != null ? String.valueOf(profile.getReceivedPoints()) : "0";
            default -> null;
        };
    }
}
